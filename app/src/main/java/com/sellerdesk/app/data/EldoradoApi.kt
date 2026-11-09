package com.sellerdesk.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * ALL Eldorado URLs live here, in one place.
 *
 * All paths below come from Eldorado's seller Swagger file (https://www.eldorado.gg/swagger/seller).
 * Token life is 15 minutes; the app refreshes it at 12.
 */
object Endpoints {
    const val BASE = "https://www.eldorado.gg"
    const val TOKEN = "/api/authentication/seller/token"

    // GET. displayFilter is required by Eldorado; we only want orders where we are the seller.
    const val ORDERS =
        "/api/v1/orders/me/seller/orders?displayFilter=DisplaySellingOrders&orderGroup=Regular" +
            "&isAscendingDateOrder=false&pageSize=50"
    const val DELIVER = "/api/v1/orders/me/{id}/deliver"   // PUT
    const val CANCEL = "/api/v1/orders/me/{id}/cancel"     // POST, body {"reason": "..."}
    const val EXTEND = "/api/v1/orders/me/{id}/extend-delivery-time"            // POST {"time","reason"}
    const val CORRECTION = "/api/v1/orders/me/{id}/delivery-details/correction" // POST {"message"}
    const val INVOICE = "/api/v1/orders/me/invoice/generate"                    // GET fromDate, untilDate

    const val ONLINE = "/api/offerUser/me/switchOnline"    // PUT
    const val OFFLINE = "/api/offerUser/me/switchOffline"  // PUT

    const val NOTIFICATIONS = "/api/notifications/me?pageSize=30"
    const val UNREAD = "/api/notifications/me/unread-count"
    const val READ_ONE = "/api/notifications/me/{id}/markAsRead"   // PUT
    const val READ_ALL = "/api/notifications/me/markAllAsRead"     // PUT

    const val BOOST_REQUESTS =
        "/api/boostingOffers/me/boostingRequests/received?filter=ActiveRequests&pageSize=20"
    const val BOOST_SUBS = "/api/boostingOffers/me/boostingSubscriptions"

    const val AUTH_PREFIX = "Bearer "
}

class ApiException(val code: Int, message: String) : Exception(message)

/** A raw file reply (used for invoices, whose format Eldorado doesn't document). */
class Download(val code: Int, val contentType: String, val bytes: ByteArray)

class EldoradoApi(private val store: CredentialStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()
    private val lock = Mutex()

    /** Logging in with "demo" / "demo" opens a fake store (for Google Play review). */
    private val isDemo: Boolean get() = store.clientId == "demo"

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    private fun send(req: Request): Pair<Int, String> =
        http.newCall(req).execute().use { it.code to (it.body?.string().orEmpty()) }

    /** Trade Client ID + secret for a short-lived access token (valid 15 min; we refresh at 12). */
    private fun fetchToken(id: String, secret: String) {
        fun post(body: RequestBody) = send(
            Request.Builder().url(Endpoints.BASE + Endpoints.TOKEN).post(body).build()
        )

        val jsonBody = JSONObject().put("clientId", id).put("clientSecret", secret)
            .toString().toRequestBody(json)
        var (code, text) = post(jsonBody)

        if (code == 400 || code == 415) {
            val form = FormBody.Builder()
                .add("client_id", id).add("client_secret", secret)
                .add("grant_type", "client_credentials").build()
            val retry = post(form)
            code = retry.first
            text = retry.second
        }
        store.lastDiag = "POST ${Endpoints.TOKEN} -> $code" +
            if (code !in 200..299) "\n" + text.take(300) else ""

        if (code !in 200..299) throw ApiException(code, "Login failed (HTTP $code). Check Client ID and secret.")

        val j = JSONObject(text)
        val t = listOf("access_token", "accessToken", "token", "jwt")
            .map { j.optString(it) }.firstOrNull { it.isNotEmpty() }
            ?: throw ApiException(code, "Login worked but no token was found in the response.")
        token = t
        tokenAt = System.currentTimeMillis()
    }

    private fun ensureToken() {
        val fresh = token != null && System.currentTimeMillis() - tokenAt < 12 * 60_000
        if (!fresh) fetchToken(store.clientId, store.clientSecret)
    }

    private fun exec(method: String, path: String, body: String?): Pair<Int, String> {
        val rb = when {
            body != null -> body.toRequestBody(json)
            method == "GET" || method == "DELETE" -> null
            else -> "{}".toRequestBody(json)
        }
        val req = Request.Builder()
            .url(Endpoints.BASE + path)
            .header("Authorization", Endpoints.AUTH_PREFIX + token)
            .header("Accept", "application/json")
            .method(method, rb)
            .build()
        return send(req)
    }

    private suspend fun call(method: String, path: String, body: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                ensureToken()
                var r = exec(method, path, body)
                if (r.first == 401) {
                    token = null
                    ensureToken()
                    r = exec(method, path, body)
                }
                // Routine polling that works shouldn't overwrite the Diagnostics of the last real action.
                val polling = path == Endpoints.ORDERS || path == Endpoints.UNREAD ||
                    path == Endpoints.NOTIFICATIONS
                if (!(polling && r.first in 200..299)) {
                    store.lastDiag = "$method $path -> ${r.first}\n" + r.second.take(400)
                }
                r
            }
        }

    private fun ok(code: Int, what: String) {
        if (code !in 200..299) throw ApiException(code, "$what (HTTP $code)")
    }

    suspend fun login(id: String, secret: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (id.equals("demo", true) && secret.equals("demo", true)) {
                store.saveCredentials("demo", "demo")
                return@runCatching
            }
            lock.withLock { fetchToken(id, secret) }
            store.saveCredentials(id, secret)
        }
    }

    fun logout() {
        token = null
        tokenAt = 0
    }

    // ───────── orders ─────────

    suspend fun orders(): Result<List<Order>> = runCatching {
        if (isDemo) return@runCatching Demo.orders
        val (code, text) = call("GET", Endpoints.ORDERS)
        ok(code, "Could not load orders")
        Order.parseList(text)
    }

    suspend fun markDelivered(id: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.setStatus(id, "Delivered")
        val (code, _) = call("PUT", Endpoints.DELIVER.replace("{id}", id))
        ok(code, "Could not mark delivered")
    }

    suspend fun cancel(id: String, reason: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.setStatus(id, "Canceled")
        val body = JSONObject().put("reason", reason).toString()
        val (code, _) = call("POST", Endpoints.CANCEL.replace("{id}", id), body)
        ok(code, "Could not cancel")
    }

    suspend fun extendDelivery(id: String, time: String, reason: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching
        val body = JSONObject().put("time", time).put("reason", reason).toString()
        val (code, _) = call("POST", Endpoints.EXTEND.replace("{id}", id), body)
        ok(code, "Could not extend delivery time")
    }

    suspend fun requestCorrection(id: String, message: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching
        val body = JSONObject().put("message", message).toString()
        val (code, _) = call("POST", Endpoints.CORRECTION.replace("{id}", id), body)
        ok(code, "Could not send the request")
    }

    /** Invoice for the last [days] days. Eldorado doesn't document the file format, so we return raw bytes. */
    suspend fun invoice(days: Int): Result<Download> = runCatching {
        if (isDemo) return@runCatching Download(200, "text/csv", "order,amount\ndemo,0.00\n".toByteArray())
        val until = Instant.now()
        val from = until.minus(days.toLong(), ChronoUnit.DAYS)
        val path = Endpoints.INVOICE +
            "?fromDate=${Uri.encode(from.toString())}&untilDate=${Uri.encode(until.toString())}"
        withContext(Dispatchers.IO) {
            lock.withLock {
                ensureToken()
                val req = Request.Builder()
                    .url(Endpoints.BASE + path)
                    .header("Authorization", Endpoints.AUTH_PREFIX + token)
                    .build()
                http.newCall(req).execute().use { r ->
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    val type = r.header("Content-Type").orEmpty()
                    store.lastDiag = "GET ${Endpoints.INVOICE} -> ${r.code}\n$type, ${bytes.size} bytes"
                    Download(r.code, type, bytes)
                }
            }
        }
    }

    // ───────── listings ─────────

    /** Loads the first 50 listings of each offer type. A type that fails is reported, not fatal. */
    suspend fun listings(): Pair<List<Listing>, List<String>> {
        if (isDemo) return Demo.listings to emptyList()
        val all = mutableListOf<Listing>()
        val errors = mutableListOf<String>()
        for (kind in ListingKind.values()) {
            runCatching {
                val (code, text) = call("GET", kind.listPath)
                if (code !in 200..299) throw ApiException(code, "HTTP $code")
                all += Listing.parse(text, kind)
            }.onFailure { errors += "${kind.label}: ${it.message}" }
        }
        return all to errors
    }

    suspend fun setPaused(listing: Listing, pause: Boolean): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.setListing(listing.id) { it.copy(state = if (pause) "Paused" else "Active") }
        val template = if (pause) listing.kind.pausePath else listing.kind.resumePath
        val (code, _) = call(listing.kind.method, template.replace("{id}", listing.id))
        ok(code, "Could not ${if (pause) "pause" else "resume"}")
    }

    suspend fun setPrice(listing: Listing, amount: Double, currency: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.setListing(listing.id) { it.copy(amountValue = amount, price = "$" + "%.2f".format(amount)) }
        val body = JSONObject().put("amount", amount).put("currency", currency).toString()
        val (code, _) = call("PUT", listing.kind.pricePath.replace("{id}", listing.id), body)
        ok(code, "Could not change the price")
    }

    suspend fun setQuantity(listing: Listing, quantity: Int): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.setListing(listing.id) { it.copy(quantity = quantity.toString()) }
        val path = listing.kind.quantityPath
            ?: throw ApiException(0, "Stock can't be edited for this type of listing")
        val (code, _) = call("PUT", path.replace("{id}", listing.id), quantity.toString())
        ok(code, "Could not change the stock")
    }

    suspend fun deleteListing(listing: Listing): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.delete(listing.id)
        val (code, _) = call("DELETE", listing.kind.deletePath.replace("{id}", listing.id))
        ok(code, "Could not delete the listing")
    }

    // ───────── store status ─────────

    suspend fun setStoreOnline(online: Boolean): Result<Unit> = runCatching {
        if (isDemo) return@runCatching
        val (code, _) = call("PUT", if (online) Endpoints.ONLINE else Endpoints.OFFLINE)
        ok(code, "Could not switch ${if (online) "online" else "offline"}")
    }

    // ───────── notifications ─────────

    suspend fun notifications(): Result<List<AppNotification>> = runCatching {
        if (isDemo) return@runCatching Demo.notifications
        val (code, text) = call("GET", Endpoints.NOTIFICATIONS)
        ok(code, "Could not load notifications")
        AppNotification.parse(text)
    }

    suspend fun unreadCount(): Result<Int> = runCatching {
        if (isDemo) return@runCatching Demo.notifications.count { it.unread }
        val (code, text) = call("GET", Endpoints.UNREAD)
        ok(code, "Could not load the unread count")
        JSONObject(text).optInt("unread", 0)
    }

    suspend fun markRead(id: String): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.readOne(id)
        val (code, _) = call("PUT", Endpoints.READ_ONE.replace("{id}", id))
        ok(code, "Could not mark as read")
    }

    suspend fun markAllRead(): Result<Unit> = runCatching {
        if (isDemo) return@runCatching Demo.readAll()
        val (code, _) = call("PUT", Endpoints.READ_ALL)
        ok(code, "Could not mark all as read")
    }

    // ───────── boosting ─────────

    suspend fun boostingRequests(): Result<List<BoostRequest>> = runCatching {
        if (isDemo) return@runCatching emptyList<BoostRequest>()
        val (code, text) = call("GET", Endpoints.BOOST_REQUESTS)
        ok(code, "Could not load boosting requests")
        Boosting.parseRequests(text)
    }

    suspend fun boostingSubs(): Result<List<BoostSub>> = runCatching {
        if (isDemo) return@runCatching emptyList<BoostSub>()
        val (code, text) = call("GET", Endpoints.BOOST_SUBS)
        ok(code, "Could not load boosting subscriptions")
        Boosting.parseSubs(text)
    }
}

package com.sellerdesk.app.data

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
import java.util.concurrent.TimeUnit

/**
 * ALL Eldorado URLs live here, in one place.
 *
 * CONFIRMED by Eldorado's help center: the token URL, 15-minute token lifetime.
 * NOT CONFIRMED (the public help pages don't list them): the order paths and the
 * Authorization prefix. Open the Seller API reference linked from
 * https://www.eldorado.gg/dashboard/seller-api, compare, and edit the lines marked VERIFY.
 */
object Endpoints {
    const val BASE = "https://eldorado.gg"
    const val TOKEN = "/api/authentication/seller/token"

    const val ORDERS = "/api/seller/orders"                      // VERIFY
    const val DELIVER = "/api/seller/orders/{id}/deliver"        // VERIFY
    const val CANCEL = "/api/seller/orders/{id}/cancel"          // VERIFY
    const val AUTH_PREFIX = "Bearer "                            // VERIFY
}

class ApiException(val code: Int, message: String) : Exception(message)

class EldoradoApi(private val store: CredentialStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()
    private val lock = Mutex()

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
            method == "GET" -> null
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
                store.lastDiag = "$method $path -> ${r.first}\n" + r.second.take(400)
                r
            }
        }

    suspend fun login(id: String, secret: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            lock.withLock { fetchToken(id, secret) }
            store.saveCredentials(id, secret)
        }
    }

    fun logout() {
        token = null
        tokenAt = 0
    }

    suspend fun orders(): Result<List<Order>> = runCatching {
        val (code, text) = call("GET", Endpoints.ORDERS)
        if (code !in 200..299) throw ApiException(code, "Could not load orders (HTTP $code)")
        Order.parseList(text)
    }

    suspend fun markDelivered(id: String): Result<Unit> = runCatching {
        val (code, _) = call("POST", Endpoints.DELIVER.replace("{id}", id))
        if (code !in 200..299) throw ApiException(code, "Could not mark delivered (HTTP $code)")
    }

    suspend fun cancel(id: String): Result<Unit> = runCatching {
        val (code, _) = call("POST", Endpoints.CANCEL.replace("{id}", id))
        if (code !in 200..299) throw ApiException(code, "Could not cancel (HTTP $code)")
    }
}

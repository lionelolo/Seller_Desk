package com.sellerdesk.app.data

import org.json.JSONArray
import org.json.JSONObject

/** "2026-10-31T11:04:22Z" -> "2026-10-31 11:04" */
fun prettyDate(s: String): String = if (s.isEmpty()) "-" else s.replace('T', ' ').take(16)

/**
 * One Eldorado order. Field names come from Eldorado's seller Swagger file.
 * Order states: Paid, Delivered, Disputed, Received, Completed, Canceled, PendingReview.
 */
data class Order(
    val id: String,
    val title: String,
    val buyer: String,
    val amount: String,
    val status: String,
    val createdAt: String,
    val review: String = "",          // "Positive", "Negative" or "" (no review yet)
    val amountValue: Double = 0.0,
    val currency: String = "",
    val quantity: String = "",
    val reviewMessage: String = "",
    val dispute: String = "",
    val cancelation: String = "",
    val history: List<Pair<String, String>> = emptyList(),   // (state, date)
) {
    /** Eldorado state "Paid" = the buyer paid and the seller still has to deliver. */
    val needsAction: Boolean
        get() = status.equals("Paid", ignoreCase = true)

    companion object {
        fun fromJson(o: JSONObject): Order {
            val offer = o.optJSONObject("orderOfferDetails")
            val price = o.optJSONObject("totalPrice")
            val amountText = price?.let {
                val a = it.str("amount").removeSuffix(".0")
                val c = it.str("currency")
                if (a.isEmpty()) "" else if (c.isEmpty()) a else "$a $c"
            }.orEmpty()
            val logs = o.optJSONArray("stateLogs")
            val history = (0 until (logs?.length() ?: 0))
                .mapNotNull { logs?.optJSONObject(it) }
                .map { it.str("state") to it.str("createdDate") }
                .filter { it.first.isNotEmpty() }
            return Order(
                id = o.str("id"),
                title = offer?.str("offerTitle").orEmpty().ifEmpty { "Order" },
                buyer = o.str("buyerUsername").ifEmpty { "Buyer" },
                amount = amountText.ifEmpty { "-" },
                // "state" is an object: {"state": "Paid", "createdDate": "..."}
                status = o.optJSONObject("state")?.str("state").orEmpty(),
                createdAt = o.str("createdDate"),
                review = o.optJSONObject("review")?.str("feedbackRating").orEmpty(),
                amountValue = price?.optDouble("amount", 0.0) ?: 0.0,
                currency = price?.str("currency").orEmpty(),
                quantity = o.str("purchaseQuantity"),
                reviewMessage = o.optJSONObject("review")?.str("reviewMessage").orEmpty(),
                dispute = reasonText(o.optJSONObject("latestDispute")),
                cancelation = reasonText(o.optJSONObject("cancelation")),
                history = history,
            )
        }

        fun parseList(text: String): List<Order> {
            val t = text.trim()
            if (t.isEmpty()) return emptyList()
            val arr: JSONArray = if (t.startsWith("[")) {
                JSONArray(t)
            } else {
                val o = JSONObject(t)
                val keys = listOf("orders", "items", "results", "data", "content")
                keys.firstNotNullOfOrNull { o.optJSONArray(it) }
                    ?: keys.firstNotNullOfOrNull { k ->
                        o.optJSONObject(k)?.let { inner ->
                            keys.firstNotNullOfOrNull { inner.optJSONArray(it) }
                        }
                    }
                    ?: JSONArray()
            }
            return (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it) }
                .map { fromJson(it) }
                .filter { it.id.isNotEmpty() }
        }
    }
}

/** Numbers for the Store screen, computed from the orders the app has loaded (latest 50). */
data class Stats(
    val toDeliver: Int,
    val delivered: Int,
    val completed: Int,
    val problems: Int,       // disputed + canceled
    val positive: Int,
    val negative: Int,
    val revenue: Double,     // sum of completed/received orders
    val currency: String,
    val total: Int,
) {
    val reviews: Int get() = positive + negative
    val positivePercent: Int get() = if (reviews == 0) 0 else Math.round(positive * 100f / reviews)

    companion object {
        fun from(orders: List<Order>): Stats {
            fun count(vararg s: String) = orders.count { o -> s.any { o.status.equals(it, true) } }
            val done = orders.filter { it.status.equals("Completed", true) || it.status.equals("Received", true) }
            return Stats(
                toDeliver = count("Paid"),
                delivered = count("Delivered"),
                completed = done.size,
                problems = count("Disputed", "Canceled"),
                positive = orders.count { it.review.equals("Positive", true) },
                negative = orders.count { it.review.equals("Negative", true) },
                revenue = done.sumOf { it.amountValue },
                currency = orders.firstOrNull { it.currency.isNotEmpty() }?.currency.orEmpty(),
                total = orders.size,
            )
        }
    }
}

/** The kinds of offers Eldorado manages separately. Paths come from the seller Swagger file. */
enum class ListingKind(
    val label: String,
    val listPath: String,
    val method: String,           // HTTP method used for pause and resume
    val pausePath: String,
    val resumePath: String,
    val pricePath: String,        // PUT, body {"amount": 1.5, "currency": "USD"}
    val quantityPath: String?,    // PUT, body is a bare integer; null = stock can't be edited
    val deletePath: String,       // DELETE
) {
    ACCOUNT(
        "Account listings",
        "/api/flexibleOffers/me/search?pageIndex=1&pageSize=50",
        "PUT",
        "/api/flexibleOffersUser/me/{id}/pause",
        "/api/flexibleOffersUser/me/{id}/resume",
        "/api/flexibleOffersUser/me/{id}/changePrice",
        null,
        "/api/flexibleOffersUser/me/{id}",
    ),
    ITEM(
        "Item listings",
        "/api/v1/item-management/me/offers/me/search?pageIndex=1&pageSize=50",
        "POST",
        "/api/v1/item-management/me/offers/{id}/pause",
        "/api/v1/item-management/me/offers/{id}/resume",
        "/api/v1/item-management/me/offers/{id}/price",
        "/api/v1/item-management/me/offers/{id}/quantity",
        "/api/v1/item-management/me/offers/{id}",
    ),
    TOPUP(
        "Top-up listings",
        "/api/predefinedOffers/me?pageIndex=1&pageSize=50",
        "PUT",
        "/api/predefinedOffersUser/me/{id}/pause",
        "/api/predefinedOffersUser/me/{id}/resume",
        "/api/predefinedOffersUser/me/{id}/changePrice",
        "/api/predefinedOffersUser/me/{id}/changeQuantity",
        "/api/predefinedOffersUser/me/{id}",
    ),
}

data class Listing(
    val id: String,
    val title: String,
    val game: String,
    val price: String,
    val amountValue: Double,
    val currency: String,
    val quantity: String,
    val state: String,
    val kind: ListingKind,
) {
    val isActive: Boolean get() = state.equals("Active", true)
    val isPaused: Boolean get() = state.equals("Paused", true)

    companion object {
        fun parse(text: String, kind: ListingKind): List<Listing> {
            val t = text.trim()
            if (t.isEmpty()) return emptyList()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("results") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { o ->
                val id = o.str("id")
                if (id.isEmpty()) return@mapNotNull null
                val game = o.str("gameCategoryTitle")
                val money = o.optJSONObject("pricePerUnit")
                val amount = money?.str("amount").orEmpty().removeSuffix(".0")
                val cur = money?.str("currency").orEmpty()
                Listing(
                    id = id,
                    title = o.str("offerTitle").ifEmpty { game }.ifEmpty { "Offer" },
                    game = game,
                    price = if (amount.isEmpty()) "-" else "$amount $cur".trim(),
                    amountValue = money?.optDouble("amount", 0.0) ?: 0.0,
                    currency = cur,
                    quantity = o.str("quantity"),
                    state = o.str("offerState"),
                    kind = kind,
                )
            }
        }
    }
}

/** One entry of the Eldorado notification inbox. */
data class AppNotification(
    val id: String,
    val title: String,
    val text: String,
    val date: String,
    val unread: Boolean,
) {
    companion object {
        fun parse(text: String): List<AppNotification> {
            val t = text.trim()
            if (t.isEmpty()) return emptyList()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("results") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { o ->
                val custom = o.optJSONObject("customNotification")
                val base = o.optJSONObject("notification")
                val id = base?.str("id").orEmpty().ifEmpty { custom?.str("id").orEmpty() }
                if (id.isEmpty()) return@mapNotNull null
                val details = base?.optJSONObject("details")
                val event = base?.str("event").orEmpty().replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ')
                val status = base?.str("notificationReadStatus").orEmpty()
                    .ifEmpty { custom?.str("readStatus").orEmpty() }
                AppNotification(
                    id = id,
                    title = custom?.str("subject").orEmpty().ifEmpty { event }.ifEmpty { "Notification" },
                    text = custom?.str("text").orEmpty().ifEmpty {
                        listOf(details?.str("title").orEmpty(), details?.str("buyerUsername").orEmpty())
                            .filter { it.isNotEmpty() }.joinToString(" - ")
                    },
                    date = base?.str("notificationDate").orEmpty().ifEmpty { custom?.str("notificationDate").orEmpty() },
                    unread = status.equals("IsUnread", true),
                )
            }
        }
    }
}

data class BoostRequest(val id: String, val category: String, val buyer: String, val date: String)
data class BoostSub(val name: String, val game: String, val subscribed: Boolean)

object Boosting {
    fun parseRequests(text: String): List<BoostRequest> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("results") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            BoostRequest(
                id = it.str("id"),
                category = it.str("boostingCategoryTitle").ifEmpty { "Boosting request" },
                buyer = it.str("buyerUsername").ifEmpty { "Buyer" },
                date = it.str("createdDate"),
            )
        }
    }

    fun parseSubs(text: String): List<BoostSub> {
        val t = text.trim()
        if (!t.startsWith("[")) return emptyList()
        val arr = JSONArray(t)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            BoostSub(
                name = it.str("boostingCategoryName"),
                game = it.str("gameName").ifEmpty { it.str("gameCategoryTitle") },
                subscribed = it.optBoolean("isSubscribed", false),
            )
        }
    }
}

private fun reasonText(o: JSONObject?): String {
    if (o == null) return ""
    return listOf(o.str("reason").replace('_', ' '), o.str("message"))
        .filter { it.isNotBlank() }.joinToString(" - ")
}

private fun JSONObject.str(vararg keys: String): String {
    for (k in keys) {
        val v = opt(k)
        if (v != null && v != JSONObject.NULL && v !is JSONObject && v !is JSONArray) {
            val s = v.toString()
            if (s.isNotBlank()) return s
        }
    }
    return ""
}

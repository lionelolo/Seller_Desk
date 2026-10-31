package com.sellerdesk.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One Eldorado order, parsed defensively: Eldorado's exact field names are not
 * in the public help pages, so we try several likely keys. If a field shows up
 * empty in the app, check Settings > Diagnostics and add the real key name here.
 */
data class Order(
    val id: String,
    val title: String,
    val buyer: String,
    val amount: String,
    val status: String,
    val createdAt: String,
) {
    /** True while the seller still has something to do (deliver). */
    val needsAction: Boolean
        get() {
            val s = status.lowercase()
            val done = listOf("deliver", "complete", "cancel", "refund", "dispute", "closed", "expired")
            val open = listOf("paid", "pending", "await", "process", "new", "active", "wait", "ready")
            if (s.isBlank()) return true
            if (done.any { s.contains(it) } && !s.contains("undeliver") && !s.contains("not delivered")) return false
            return open.any { s.contains(it) } || s == "1" || s == "0"
        }

    companion object {
        fun fromJson(o: JSONObject): Order {
            val offer = o.optJSONObject("offer")
            val buyerObj = o.optJSONObject("buyer") ?: o.optJSONObject("user")
            return Order(
                id = o.str("id", "orderId", "order_id", "uuid"),
                title = o.str("title", "offerTitle", "offerName", "itemName", "name")
                    .ifEmpty { offer?.str("title", "name", "offerTitle").orEmpty() }
                    .ifEmpty { "Order" },
                buyer = o.str("buyerName", "buyerUsername", "buyer_name", "username")
                    .ifEmpty { buyerObj?.str("username", "name", "displayName").orEmpty() }
                    .ifEmpty { o.str("buyer") }
                    .ifEmpty { "Buyer" },
                amount = o.str("totalPrice", "total", "price", "amount", "sellerEarnings")
                    .ifEmpty { o.optJSONObject("totalPrice")?.str("amount", "value").orEmpty() }
                    .ifEmpty { "-" },
                status = o.str("state", "status", "orderState", "orderStatus"),
                createdAt = o.str("createdDate", "createdAt", "created", "dateCreated", "date"),
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

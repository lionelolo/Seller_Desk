package com.sellerdesk.app.data

import org.json.JSONArray
import org.json.JSONObject

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
) {
    /** Eldorado state "Paid" = the buyer paid and the seller still has to deliver. */
    val needsAction: Boolean
        get() = status.equals("Paid", ignoreCase = true)

    companion object {
        fun fromJson(o: JSONObject): Order {
            // Field names from Eldorado's swagger: OrderPrivateViewModel.
            val offer = o.optJSONObject("orderOfferDetails")
            val price = o.optJSONObject("totalPrice")
            val amountText = price?.let {
                val a = it.str("amount").removeSuffix(".0")
                val c = it.str("currency")
                if (a.isEmpty()) "" else if (c.isEmpty()) a else "$a $c"
            }.orEmpty()
            return Order(
                id = o.str("id"),
                title = offer?.str("offerTitle").orEmpty().ifEmpty { "Order" },
                buyer = o.str("buyerUsername").ifEmpty { "Buyer" },
                amount = amountText.ifEmpty { "-" },
                // "state" is an object: {"state": "Paid", "createdDate": "..."}
                status = o.optJSONObject("state")?.str("state").orEmpty(),
                createdAt = o.str("createdDate"),
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

package com.sellerdesk.app.data

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Fake store used when someone logs in with "demo" / "demo" (for Google Play review and screenshots). */
object Demo {
    private fun ago(hours: Long) = Instant.now().minus(hours, ChronoUnit.HOURS).toString()

    private fun order(
        id: String, title: String, buyer: String, price: Double, status: String, hours: Long,
        review: String = "", msg: String = "", conversation: String = "",
    ) = Order(
        id = id, title = title, buyer = buyer, amount = "$" + "%.2f".format(price), status = status,
        createdAt = ago(hours), review = review, amountValue = price, currency = "USD",
        quantity = "1", reviewMessage = msg, conversation = conversation,
    )

    @Volatile var orders: List<Order> = listOf(
        order("d1", "Sample Game Account - Level 60", "demo_buyer_1", 24.99, "Paid", 1,
            conversation = "Hi! Is this still available?"),
        order("d2", "1000 Sample Coins", "demo_buyer_2", 9.50, "Paid", 3),
        order("d3", "Sample Gift Card 25 USD", "demo_buyer_3", 21.00, "Delivered", 8),
        order("d4", "Starter Pack Account", "demo_buyer_4", 14.00, "Completed", 30, "Positive", "Fast and smooth!"),
        order("d5", "500 Sample Gems", "demo_buyer_5", 6.75, "Completed", 52, "Positive", "Thanks"),
        order("d6", "Rare Item Bundle", "demo_buyer_6", 18.20, "Canceled", 70),
    )

    @Volatile var listings: List<Listing> = listOf(
        Listing("l1", "Sample Game Account - Level 60", "Sample Game", "$24.99", 24.99, "USD", "1", "Active", ListingKind.ACCOUNT),
        Listing("l2", "1000 Sample Coins", "Sample Game", "$9.50", 9.50, "USD", "250", "Active", ListingKind.ITEM),
        Listing("l3", "500 Sample Gems", "Sample Game", "$6.75", 6.75, "USD", "80", "Paused", ListingKind.ITEM),
        Listing("l4", "Sample Gift Card 25 USD", "Sample Store", "$21.00", 21.0, "USD", "40", "Active", ListingKind.TOPUP),
    )

    @Volatile var notifications: List<AppNotification> = listOf(
        AppNotification("n1", "New order", "demo_buyer_1 bought Sample Game Account", ago(1), true, "OrderCreated"),
        AppNotification("n2", "Buyer contacted you", "demo_buyer_1: Is this still available?", ago(1), true, "BuyerContactedSeller"),
        AppNotification("n3", "Order completed", "demo_buyer_4 left a positive review", ago(30), false, "OrderCompleted"),
    )

    fun setStatus(id: String, status: String) {
        orders = orders.map { if (it.id == id) it.copy(status = status) else it }
    }

    fun setListing(id: String, f: (Listing) -> Listing) {
        listings = listings.map { if (it.id == id) f(it) else it }
    }

    fun delete(id: String) {
        listings = listings.filter { it.id != id }
    }

    fun readOne(id: String) {
        notifications = notifications.map { if (it.id == id) it.copy(unread = false) else it }
    }

    fun readAll() {
        notifications = notifications.map { it.copy(unread = false) }
    }
}

package com.sellerdesk.app

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sellerdesk.app.alert.AlarmPlayer
import com.sellerdesk.app.alert.AlertCenter
import com.sellerdesk.app.data.AppNotification
import com.sellerdesk.app.data.BoostRequest
import com.sellerdesk.app.data.BoostSub
import com.sellerdesk.app.data.Download
import com.sellerdesk.app.data.Listing
import com.sellerdesk.app.data.Order
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Application = app
    private val store = Graph.store
    private val api = Graph.api

    var loggedIn by mutableStateOf(store.isLoggedIn)
        private set
    var orders by mutableStateOf<List<Order>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var busyId by mutableStateOf<String?>(null)
        private set
    var alarmActive by mutableStateOf(false)
        private set

    // listings
    var listings by mutableStateOf<List<Listing>>(emptyList())
        private set
    var listingsLoading by mutableStateOf(false)
        private set
    var listingsErrors by mutableStateOf<List<String>>(emptyList())
        private set
    var listingBusyId by mutableStateOf<String?>(null)
        private set
    var bulkBusy by mutableStateOf(false)
        private set

    // inbox
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var notifLoading by mutableStateOf(false)
        private set
    var notifError by mutableStateOf<String?>(null)
        private set
    var unread by mutableStateOf(0)
        private set

    // store
    var onlineChoice by mutableStateOf(store.onlineChoice)
        private set
    var storeBusy by mutableStateOf(false)
        private set
    var invoiceBusy by mutableStateOf(false)
        private set
    var boostRequests by mutableStateOf<List<BoostRequest>>(emptyList())
        private set
    var boostSubs by mutableStateOf<List<BoostSub>>(emptyList())
        private set

    private fun toast(text: String) =
        Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()

    // ───────── orders ─────────

    private suspend fun load() {
        loading = true
        api.orders()
            .onSuccess { orders = it; error = null }
            .onFailure { error = it.message ?: "Network error" }
        alarmActive = store.pending || AlarmPlayer.isPlaying
        loading = false
    }

    fun refresh() {
        if (!loggedIn || loading) return
        viewModelScope.launch { load() }
    }

    fun login(id: String, secret: String) {
        if (id.isBlank() || secret.isBlank()) {
            loginError = "Enter both the Client ID and the Client secret."
            return
        }
        viewModelScope.launch {
            loginBusy = true
            loginError = null
            api.login(id.trim(), secret.trim())
                .onSuccess {
                    store.baselineDone = false
                    store.seen = emptySet()
                    store.alertsEnabled = true
                    loggedIn = true
                    OrderMonitorService.start(appCtx)
                    load()
                }
                .onFailure { loginError = it.message ?: "Could not connect." }
            loginBusy = false
        }
    }

    fun markDelivered(order: Order, onDone: () -> Unit) =
        act(order, onDone) { api.markDelivered(order.id) to "Marked as delivered" }

    fun cancel(order: Order, reason: String, onDone: () -> Unit) =
        act(order, onDone) { api.cancel(order.id, reason) to "Order cancelled" }

    fun extendDelivery(order: Order, time: String, reason: String, onDone: () -> Unit) =
        act(order, onDone) { api.extendDelivery(order.id, time, reason) to "Delivery time extended" }

    fun requestCorrection(order: Order, message: String, onDone: () -> Unit) =
        act(order, onDone) { api.requestCorrection(order.id, message) to "Request sent to the buyer" }

    private fun act(
        order: Order,
        onDone: () -> Unit,
        block: suspend () -> Pair<Result<Unit>, String>,
    ) {
        viewModelScope.launch {
            busyId = order.id
            val (result, okText) = block()
            result
                .onSuccess { toast(okText); onDone() }
                .onFailure { toast(it.message ?: "Something went wrong") }
            busyId = null
            load()
        }
    }

    fun stopAlarm() {
        AlertCenter.acknowledge(appCtx)
        alarmActive = false
    }

    // ───────── listings ─────────

    private suspend fun fetchListings() {
        listingsLoading = true
        val (list, errors) = api.listings()
        // If every type failed, keep what we had instead of blanking the screen.
        if (list.isNotEmpty() || errors.isEmpty()) listings = list
        listingsErrors = errors
        listingsLoading = false
    }

    fun loadListings() {
        if (!loggedIn || listingsLoading) return
        viewModelScope.launch { fetchListings() }
    }

    fun togglePause(listing: Listing) {
        viewModelScope.launch {
            listingBusyId = listing.id
            val pause = listing.isActive
            api.setPaused(listing, pause)
                .onSuccess { toast(if (pause) "Listing paused" else "Listing resumed") }
                .onFailure { toast(it.message ?: "Something went wrong") }
            listingBusyId = null
            fetchListings()
        }
    }

    /** Pauses every active listing, or resumes every paused one, one by one. */
    fun bulkPause(pause: Boolean) {
        if (bulkBusy) return
        viewModelScope.launch {
            bulkBusy = true
            val targets = listings.filter { if (pause) it.isActive else it.isPaused }
            var done = 0
            for (l in targets) {
                if (api.setPaused(l, pause).isSuccess) done++
            }
            toast("${if (pause) "Paused" else "Resumed"} $done of ${targets.size} listings")
            bulkBusy = false
            fetchListings()
        }
    }

    /** [price] / [quantity] are null when unchanged. */
    fun editListing(listing: Listing, price: Double?, quantity: Int?, onDone: () -> Unit) {
        viewModelScope.launch {
            listingBusyId = listing.id
            var failure: String? = null
            if (price != null) {
                api.setPrice(listing, price, listing.currency.ifEmpty { "USD" })
                    .onFailure { failure = it.message ?: "Could not change the price" }
            }
            if (failure == null && quantity != null) {
                api.setQuantity(listing, quantity)
                    .onFailure { failure = it.message ?: "Could not change the stock" }
            }
            val f = failure
            if (f == null) {
                toast("Listing updated")
                onDone()
            } else {
                toast(f)
            }
            listingBusyId = null
            fetchListings()
        }
    }

    fun deleteListing(listing: Listing) {
        viewModelScope.launch {
            listingBusyId = listing.id
            api.deleteListing(listing)
                .onSuccess { toast("Listing deleted") }
                .onFailure { toast(it.message ?: "Something went wrong") }
            listingBusyId = null
            fetchListings()
        }
    }

    // ───────── inbox ─────────

    fun refreshUnread() {
        if (!loggedIn) return
        viewModelScope.launch { api.unreadCount().onSuccess { unread = it } }
    }

    fun loadNotifications() {
        if (!loggedIn || notifLoading) return
        viewModelScope.launch {
            notifLoading = true
            api.notifications()
                .onSuccess { notifications = it; notifError = null }
                .onFailure { notifError = it.message ?: "Network error" }
            api.unreadCount().onSuccess { unread = it }
            notifLoading = false
        }
    }

    fun markRead(n: AppNotification) {
        if (!n.unread) return
        notifications = notifications.map { if (it.id == n.id) it.copy(unread = false) else it }
        unread = (unread - 1).coerceAtLeast(0)
        viewModelScope.launch { api.markRead(n.id) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            api.markAllRead()
                .onSuccess {
                    notifications = notifications.map { it.copy(unread = false) }
                    unread = 0
                }
                .onFailure { toast(it.message ?: "Something went wrong") }
        }
    }

    // ───────── store ─────────

    fun setOnline(online: Boolean) {
        if (storeBusy) return
        viewModelScope.launch {
            storeBusy = true
            api.setStoreOnline(online)
                .onSuccess {
                    onlineChoice = if (online) "online" else "offline"
                    store.onlineChoice = onlineChoice
                    toast(if (online) "You're online" else "You're offline")
                }
                .onFailure { toast(it.message ?: "Something went wrong") }
            storeBusy = false
        }
    }

    fun loadBoosting() {
        if (!loggedIn) return
        viewModelScope.launch {
            // A store that doesn't sell boosting may get errors here; that's fine, the card just stays hidden.
            api.boostingRequests().onSuccess { boostRequests = it }
            api.boostingSubs().onSuccess { boostSubs = it }
        }
    }

    fun invoice(days: Int) {
        if (invoiceBusy) return
        viewModelScope.launch {
            invoiceBusy = true
            api.invoice(days)
                .onSuccess { d ->
                    if (d.code in 200..299 && d.bytes.isNotEmpty()) shareFile(d)
                    else toast("Invoice failed (HTTP ${d.code}). Settings > Diagnostics has details.")
                }
                .onFailure { toast(it.message ?: "Invoice failed") }
            invoiceBusy = false
        }
    }

    private fun shareFile(d: Download) {
        runCatching {
            val type = d.contentType.lowercase()
            val ext = when {
                "pdf" in type -> "pdf"
                "spreadsheet" in type || "excel" in type -> "xlsx"
                "csv" in type -> "csv"
                "json" in type -> "json"
                "zip" in type -> "zip"
                "html" in type -> "html"
                else -> "bin"
            }
            val mime = type.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
            val dir = File(appCtx.cacheDir, "invoices").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "invoice-${System.currentTimeMillis()}.$ext")
            file.writeBytes(d.bytes)
            val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            appCtx.startActivity(
                Intent.createChooser(send, "Invoice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast("Could not open the invoice file") }
    }

    fun logout() {
        OrderMonitorService.stop(appCtx)
        AlertCenter.acknowledge(appCtx)
        api.logout()
        store.clearAll()
        orders = emptyList()
        listings = emptyList()
        listingsErrors = emptyList()
        notifications = emptyList()
        unread = 0
        boostRequests = emptyList()
        boostSubs = emptyList()
        onlineChoice = ""
        loggedIn = false
    }
}

package com.sellerdesk.app

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sellerdesk.app.alert.AlarmPlayer
import com.sellerdesk.app.alert.AlertCenter
import com.sellerdesk.app.data.Order
import kotlinx.coroutines.launch

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

    private fun toast(text: String) =
        Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()

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

    fun markDelivered(order: Order, onDone: () -> Unit) = act(order, onDone) { api.markDelivered(order.id) to "Marked as delivered" }

    fun cancel(order: Order, onDone: () -> Unit) = act(order, onDone) { api.cancel(order.id) to "Order cancelled" }

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

    fun logout() {
        OrderMonitorService.stop(appCtx)
        AlertCenter.acknowledge(appCtx)
        api.logout()
        store.clearAll()
        orders = emptyList()
        loggedIn = false
    }
}

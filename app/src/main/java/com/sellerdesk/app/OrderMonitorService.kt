package com.sellerdesk.app

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sellerdesk.app.alert.AlarmPlayer
import com.sellerdesk.app.alert.AlertCenter
import com.sellerdesk.app.data.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls Eldorado every ~20 s (Eldorado documents no webhooks
 * or push for the Seller API, so polling is the only option) and raises the alarm
 * when a new order that needs action appears.
 */
class OrderMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        AlertCenter.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = Graph.store
        if (!store.isLoggedIn || !store.alertsEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            AlertCenter.ID_MONITOR,
            AlertCenter.monitorNotification(this, "Starting..."),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun status(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AlertCenter.ID_MONITOR, AlertCenter.monitorNotification(this, text))
    }

    private suspend fun loop() {
        val store = Graph.store
        var fails = 0
        while (currentCoroutineContext().isActive) {
            Graph.api.orders()
                .onSuccess { list ->
                    fails = 0
                    process(list)
                    status("${list.count { it.needsAction }} order(s) need action")
                }
                .onFailure {
                    fails++
                    if (fails >= 3) status("Can't reach Eldorado - retrying")
                }

            if (store.alertMessages) pollMessages()

            // Nobody tapped "Stop" yet? Ring again every 2 minutes.
            if (store.pending && store.loud && !AlarmPlayer.isPlaying &&
                System.currentTimeMillis() - store.lastAlertAt > 120_000
            ) {
                AlertCenter.retrigger(this)
            }
            delay(store.pollSeconds * 1000L)
        }
    }

    /** Rings when Eldorado reports that a buyer contacted you. Only unread notifications count. */
    private suspend fun pollMessages() {
        val store = Graph.store
        Graph.api.notifications().onSuccess { list ->
            val chat = list.filter { it.unread && it.event in CHAT_EVENTS }
            if (!store.notifBaseline) {
                // First run: remember what's already there so old messages don't ring.
                store.seenNotifs = chat.map { it.id }.toSet()
                store.notifBaseline = true
                return
            }
            val seen = store.seenNotifs
            val fresh = chat.filter { it.id !in seen }
            if (fresh.isNotEmpty()) {
                store.seenNotifs = seen + fresh.map { it.id }
                val text = if (fresh.size == 1) {
                    listOf(fresh[0].title, fresh[0].text).filter { it.isNotEmpty() }.joinToString(" - ")
                } else {
                    "${fresh.size} new buyer messages"
                }
                AlertCenter.triggerMessage(this, text)
            }
        }
    }

    private fun process(list: List<Order>) {
        val store = Graph.store
        if (!store.baselineDone) {
            // First run after login: remember what already exists so we don't ring for old orders.
            store.seen = list.map { it.id }.toSet()
            store.baselineDone = true
            return
        }
        val seen = store.seen
        val fresh = list.filter { it.needsAction && it.id !in seen }
        if (fresh.isNotEmpty()) {
            store.seen = seen + fresh.map { it.id }
            AlertCenter.trigger(this, fresh)
        }
    }

    companion object {
        /** Eldorado notification events that mean "a buyer is talking to you". */
        private val CHAT_EVENTS = setOf("BuyerContactedSeller", "ConversationCreated", "TalkJsNudge")

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OrderMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrderMonitorService::class.java))
        }
    }
}

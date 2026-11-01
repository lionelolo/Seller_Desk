package com.sellerdesk.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restart the order watcher after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Graph.init(context)
        if (Graph.store.isLoggedIn && Graph.store.alertsEnabled) {
            OrderMonitorService.start(context)
        }
    }
}

package com.sellerdesk.app.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sellerdesk.app.AlertActivity
import com.sellerdesk.app.Graph
import com.sellerdesk.app.MainActivity
import com.sellerdesk.app.R
import com.sellerdesk.app.data.Order

object AlertCenter {
    const val CH_MONITOR = "monitor"
    const val CH_ORDERS = "orders_v1"
    const val ID_MONITOR = 1
    const val ID_ALERT = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Order watcher", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that SellerDesk is watching for new orders"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ORDERS, "New order alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud alert when a new order arrives"
                // Sound comes from AlarmPlayer (alarm stream), so the channel itself stays silent.
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
    }

    fun monitorNotification(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("SellerDesk is watching your store")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun trigger(context: Context, fresh: List<Order>) {
        val text = if (fresh.size == 1) {
            "${fresh[0].title} - ${fresh[0].amount}"
        } else {
            "${fresh.size} new orders waiting"
        }
        Graph.store.pendingText = text
        raise(context, text)
    }

    /** Called by the service if nobody has acknowledged the alert for a while. */
    fun retrigger(context: Context) {
        raise(context, Graph.store.pendingText.ifEmpty { "Order waiting" })
    }

    private fun raise(context: Context, text: String) {
        val store = Graph.store
        store.pending = true
        store.lastAlertAt = System.currentTimeMillis()
        if (store.loud) AlarmPlayer.start(context)

        val screen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_ORDERS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("New order!")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ID_ALERT, n)
    }

    fun acknowledge(context: Context) {
        AlarmPlayer.stop()
        Graph.store.pending = false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ID_ALERT)
    }

    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }
}

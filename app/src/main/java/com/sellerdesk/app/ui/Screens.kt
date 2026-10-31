package com.sellerdesk.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sellerdesk.app.Graph
import com.sellerdesk.app.MainViewModel
import com.sellerdesk.app.OrderMonitorService
import com.sellerdesk.app.alert.AlertCenter
import com.sellerdesk.app.data.Order
import kotlinx.coroutines.delay

@Composable
fun Root(vm: MainViewModel, requestNotifications: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (vm.loggedIn) Home(vm, requestNotifications) else LoginScreen(vm)
    }
}

// ───────────────────────── Login ─────────────────────────

@Composable
fun LoginScreen(vm: MainViewModel) {
    var id by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("SellerDesk", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your Eldorado orders, with an alarm you can't sleep through.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = id, onValueChange = { id = it },
            label = { Text("Client ID") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = secret, onValueChange = { secret = it },
            label = { Text("Client secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.loginError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { vm.login(id, secret) },
            enabled = !vm.loginBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (vm.loginBusy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect store")
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "The store owner creates these in Eldorado: Dashboard > Seller API > Generate credentials. " +
                "They're stored encrypted on this phone and sent only to eldorado.gg.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────── Home / orders ─────────────────────────

@Composable
fun Home(vm: MainViewModel, requestNotifications: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var onlyOpen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { requestNotifications() }

    // Auto-refresh every 15 s, only while the app is on screen.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(15_000)
            }
        }
    }

    val detail = vm.orders.firstOrNull { it.id == detailId }
    BackHandler(enabled = showSettings || detailId != null) {
        if (detailId != null) detailId = null else showSettings = false
    }

    when {
        showSettings -> SettingsScreen(vm, onBack = { showSettings = false })
        detail != null -> DetailScreen(vm, detail, onBack = { detailId = null })
        else -> {
            val shown = if (onlyOpen) vm.orders.filter { it.needsAction } else vm.orders
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Orders", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    if (vm.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showSettings = true }) { Text("Settings") }
                }

                if (vm.alarmActive) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "New order waiting",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.stopAlarm() }) { Text("Stop alarm") }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyOpen, onClick = { onlyOpen = true }, label = { Text("Needs action") })
                    FilterChip(selected = !onlyOpen, onClick = { onlyOpen = false }, label = { Text("All") })
                }

                vm.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Settings > Diagnostics shows the raw reply from Eldorado.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                if (shown.isEmpty() && vm.error == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (onlyOpen) "Nothing to deliver.\nQuiet is good." else "No orders yet.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown, key = { it.id }) { o -> OrderCard(o) { detailId = o.id } }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    order.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(order.amount, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.buyer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(order)
            }
        }
    }
}

@Composable
private fun StatusChip(order: Order) {
    val open = order.needsAction
    Surface(
        shape = RoundedCornerShape(50),
        color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            order.status.ifEmpty { "-" },
            style = MaterialTheme.typography.labelMedium,
            color = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ───────────────────────── Detail ─────────────────────────

@Composable
fun DetailScreen(vm: MainViewModel, order: Order, onBack: () -> Unit) {
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDeliver by remember { mutableStateOf(false) }
    val busy = vm.busyId == order.id

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("< Back") }
        Spacer(Modifier.height(8.dp))
        Text(order.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        StatusChip(order)
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("Buyer", order.buyer)
                Field("Amount", order.amount)
                Field("Created", order.createdAt.ifEmpty { "-" })
                Field("Order ID", order.id, mono = true)
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { confirmDeliver = true },
            enabled = !busy && order.needsAction,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Mark as delivered")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { confirmCancel = true },
            enabled = !busy && order.needsAction,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Cancel order") }
    }

    if (confirmDeliver) {
        AlertDialog(
            onDismissRequest = { confirmDeliver = false },
            title = { Text("Mark as delivered?") },
            text = { Text("Only do this after you've actually delivered the goods. The buyer is notified on Eldorado.") },
            confirmButton = {
                TextButton(onClick = { confirmDeliver = false; vm.markDelivered(order, onBack) }) { Text("Yes, delivered") }
            },
            dismissButton = { TextButton(onClick = { confirmDeliver = false }) { Text("Not yet") } },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this order?") },
            text = { Text("This is live on Eldorado and can't be undone from here.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancel(order, onBack) }) {
                    Text("Cancel order", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (mono) FontFamily.Monospace else null,
            fontSize = if (mono) 13.sp else 16.sp,
        )
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    var alerts by remember { mutableStateOf(store.alertsEnabled) }
    var loud by remember { mutableStateOf(store.loud) }
    var diag by remember { mutableStateOf(store.lastDiag) }
    var confirmLogout by remember { mutableStateOf(false) }

    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val fullOk = remember(tick) { AlertCenter.canUseFullScreen(ctx) }
    val batteryOk = remember(tick) {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        SectionTitle("Alert readiness")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                ReadyRow("Notifications allowed", notifOk) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
                ReadyRow("Show alert over lock screen", fullOk) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }
                ReadyRow("Battery: unrestricted", batteryOk) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }
        }
        Text(
            "Also set Do Not Disturb to allow alarms, and on Xiaomi/Oppo/Vivo/Samsung phones " +
                "lock the app in Recents and allow Autostart.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        SectionTitle("Alerts")
        ToggleRow("Watch for new orders in background", alerts) {
            alerts = it
            store.alertsEnabled = it
            if (it) OrderMonitorService.start(ctx) else OrderMonitorService.stop(ctx)
        }
        ToggleRow("Loud alarm (repeats until you tap Stop)", loud) {
            loud = it
            store.loud = it
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                AlertCenter.trigger(ctx, listOf(Order("test", "Test order", "Tester", "$1.00", "paid", "")))
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Test the alarm") }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Diagnostics")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                diag,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(14.dp),
            )
        }
        TextButton(onClick = { diag = store.lastDiag }) { Text("Refresh") }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { confirmLogout = true },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Log out and forget credentials") }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("Alerts stop and the saved Client ID and secret are erased from this phone.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ReadyRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (ok) Color(0xFF3D8B5A) else MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text("Fix") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

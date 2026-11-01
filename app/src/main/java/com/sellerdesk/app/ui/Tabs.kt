package com.sellerdesk.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sellerdesk.app.MainViewModel
import com.sellerdesk.app.data.AppNotification
import com.sellerdesk.app.data.Listing
import com.sellerdesk.app.data.Stats
import com.sellerdesk.app.data.prettyDate

// ───────────────────────── Shared bits ─────────────────────────

@Composable
fun BottomTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            labels.forEachIndexed { i, name ->
                TextButton(onClick = { onSelect(i) }, modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabHeader(title: String, loading: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else {
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        TextButton(onClick = onSettings) { Text("Settings") }
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

// ───────────────────────── Listings ─────────────────────────

@Composable
fun ListingsTab(vm: MainViewModel, onSettings: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadListings() }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 active, 2 paused
    var editing by remember { mutableStateOf<Listing?>(null) }
    var deleting by remember { mutableStateOf<Listing?>(null) }
    var bulk by remember { mutableIntStateOf(0) }   // 0 none, 1 confirm pause all, 2 confirm resume all

    val active = vm.listings.count { it.isActive }
    val paused = vm.listings.count { it.isPaused }
    val shown = when (filter) {
        1 -> vm.listings.filter { it.isActive }
        2 -> vm.listings.filter { it.isPaused }
        else -> vm.listings
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        TabHeader("Listings", vm.listingsLoading, { vm.loadListings() }, onSettings)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("All ${vm.listings.size}") })
            FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Active $active") })
            FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Paused $paused") })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { bulk = 1 }, enabled = active > 0 && !vm.bulkBusy) { Text("Pause all") }
            TextButton(onClick = { bulk = 2 }, enabled = paused > 0 && !vm.bulkBusy) { Text("Resume all") }
            if (vm.bulkBusy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Hint("Working...")
            }
        }

        if (vm.listingsErrors.isNotEmpty()) {
            Hint(vm.listingsErrors.joinToString("\n") + "\nSettings > Diagnostics shows the last reply.")
            Spacer(Modifier.height(8.dp))
        }

        if (shown.isEmpty() && !vm.listingsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.listings.isEmpty()) "No listings found." else "Nothing in this filter.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.kind.name + it.id }) { l ->
                    ListingCard(l, vm, onEdit = { editing = l }, onDelete = { deleting = l })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { l ->
        EditListingDialog(
            l,
            onDismiss = { editing = null },
            onSave = { price, qty ->
                editing = null
                vm.editListing(l, price, qty) {}
            },
        )
    }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this listing?") },
            text = { Text("\"${l.title}\" will be removed from Eldorado. This can't be undone from here. To hide it for a while, use Pause instead.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.deleteListing(l) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } },
        )
    }
    if (bulk != 0) {
        val pause = bulk == 1
        val n = if (pause) active else paused
        AlertDialog(
            onDismissRequest = { bulk = 0 },
            title = { Text(if (pause) "Pause all active listings?" else "Resume all paused listings?") },
            text = {
                Text(
                    if (pause) "$n active listings will stop selling until you resume them."
                    else "$n paused listings will go live again, including any you paused on purpose."
                )
            },
            confirmButton = {
                TextButton(onClick = { bulk = 0; vm.bulkPause(pause) }) { Text(if (pause) "Pause all" else "Resume all") }
            },
            dismissButton = { TextButton(onClick = { bulk = 0 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ListingCard(l: Listing, vm: MainViewModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    val busy = vm.listingBusyId == l.id
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(l.price, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            }
            if (l.game.isNotEmpty() && l.game != l.title) {
                Spacer(Modifier.height(4.dp))
                Text(l.game, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(l.state.ifEmpty { "-" }, highlight = l.isActive)
                Spacer(Modifier.width(8.dp))
                if (l.quantity.isNotEmpty()) {
                    Text("Stock ${l.quantity}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                } else {
                    if (l.isActive || l.isPaused) {
                        TextButton(onClick = { vm.togglePause(l) }) { Text(if (l.isActive) "Pause" else "Resume") }
                    }
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun EditListingDialog(l: Listing, onDismiss: () -> Unit, onSave: (Double?, Int?) -> Unit) {
    var price by remember { mutableStateOf(trimNum(l.amountValue)) }
    var qty by remember { mutableStateOf(l.quantity) }
    val canQty = l.kind.quantityPath != null

    val newPrice = price.replace(',', '.').toDoubleOrNull()
    val newQty = qty.trim().toIntOrNull()
    val priceOk = newPrice != null && newPrice > 0
    val qtyOk = !canQty || (newQty != null && newQty >= 0)
    val priceChanged = newPrice != null && newPrice != l.amountValue
    val qtyChanged = canQty && newQty != null && newQty.toString() != l.quantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit listing") },
        text = {
            Column {
                Text(l.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price per unit (${l.currency.ifEmpty { "USD" }})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (canQty) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Stock") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                    )
                } else {
                    Hint("Stock for account listings is managed on Eldorado.")
                }
                Spacer(Modifier.height(8.dp))
                Hint("Changes go live on Eldorado immediately.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = priceOk && qtyOk && (priceChanged || qtyChanged),
                onClick = {
                    onSave(
                        if (priceChanged) newPrice else null,
                        if (qtyChanged) newQty else null,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── Inbox ─────────────────────────

@Composable
fun InboxTab(vm: MainViewModel, onSettings: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadNotifications() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        TabHeader("Inbox", vm.notifLoading, { vm.loadNotifications() }, onSettings)

        if (vm.unread > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${vm.unread} unread",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.markAllRead() }) { Text("Mark all read") }
            }
        }

        vm.notifError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Hint("Settings > Diagnostics shows the raw reply from Eldorado.")
        }

        Spacer(Modifier.height(8.dp))
        if (vm.notifications.isEmpty() && !vm.notifLoading && vm.notifError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No notifications.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.notifications, key = { it.id }) { n -> NotificationCard(n) { vm.markRead(n) } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (n.unread) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape)
                    .background(if (n.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    n.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (n.unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                )
                if (n.text.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                }
                Spacer(Modifier.height(4.dp))
                Hint(prettyDate(n.date))
            }
        }
    }
}

// ───────────────────────── Store ─────────────────────────

@Composable
fun StoreTab(vm: MainViewModel, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.loadListings()
        vm.loadBoosting()
    }
    val s = Stats.from(vm.orders)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        TabHeader("Store", vm.loading, { vm.refresh(); vm.loadBoosting() }, onSettings)
        Spacer(Modifier.height(12.dp))

        // Online / offline
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Store status")
                Spacer(Modifier.height(6.dp))
                Text(
                    when (vm.onlineChoice) {
                        "online" -> "Online"
                        "offline" -> "Offline"
                        else -> "Not set from this app yet"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(6.dp))
                Hint("Eldorado doesn't tell the app your current status, so this shows what you last set here.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.setOnline(true) },
                        enabled = !vm.storeBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Go online") }
                    OutlinedButton(
                        onClick = { vm.setOnline(false) },
                        enabled = !vm.storeBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Go offline") }
                }
            }
        }

        // Rating
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Your rating")
                Spacer(Modifier.height(6.dp))
                if (s.reviews == 0) {
                    Text("No reviews yet", style = MaterialTheme.typography.headlineMedium)
                } else {
                    Text("${s.positivePercent}% positive", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("${s.positive} positive, ${s.negative} negative", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(6.dp))
                Hint("Counted from your latest ${s.total} orders. Your full public rating is on Eldorado.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("To deliver", s.toDeliver.toString(), highlight = s.toDeliver > 0, modifier = Modifier.weight(1f))
            StatTile("Delivered", s.delivered.toString(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Completed", s.completed.toString(), modifier = Modifier.weight(1f))
            StatTile("Disputed / canceled", s.problems.toString(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Completed sales")
                Spacer(Modifier.height(6.dp))
                val money = if (s.revenue % 1.0 == 0.0) s.revenue.toLong().toString() else "%.2f".format(s.revenue)
                Text("$money ${s.currency}".trim(), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Hint(
                    "Total price of completed orders among your latest ${s.total}. " +
                        "This is not your wallet balance and doesn't subtract Eldorado's fees."
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Listings")
                Spacer(Modifier.height(6.dp))
                Text(
                    "${vm.listings.count { it.isActive }} active, ${vm.listings.count { it.isPaused }} paused",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        // Boosting (only if the store has any)
        if (vm.boostRequests.isNotEmpty() || vm.boostSubs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Label("Boosting")
                    Spacer(Modifier.height(6.dp))
                    Text("${vm.boostRequests.size} active requests", style = MaterialTheme.typography.titleLarge)
                    vm.boostRequests.take(8).forEach { r ->
                        Spacer(Modifier.height(10.dp))
                        Text("${r.buyer} - ${r.category}", style = MaterialTheme.typography.bodyLarge)
                        Hint(prettyDate(r.date))
                    }
                    val subs = vm.boostSubs.filter { it.subscribed }
                    if (subs.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Label("Subscribed categories")
                        Text(
                            subs.joinToString(", ") { listOf(it.game, it.name).filter { p -> p.isNotEmpty() }.joinToString(" ") },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Invoice
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Invoice")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create an invoice for a date range. The file opens in your share sheet so you can save or send it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(7, 30, 90).forEach { d ->
                        OutlinedButton(
                            onClick = { vm.invoice(d) },
                            enabled = !vm.invoiceBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("$d days") }
                    }
                }
                if (vm.invoiceBusy) {
                    Spacer(Modifier.height(8.dp))
                    Hint("Preparing the invoice...")
                }
            }
        }

        // Wallet
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Label("Wallet and withdrawals")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Eldorado's seller API doesn't expose your balance or withdrawals, so they can't be shown here. " +
                        "Open Eldorado in your browser to see them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.eldorado.gg/dashboard")))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Open Eldorado dashboard") }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ───────────────────────── Order dialogs (used by the order detail screen) ─────────────────────────

private val EXTEND_TIMES = listOf(
    "Minute5" to "5 minutes",
    "Minute15" to "15 minutes",
    "Minute30" to "30 minutes",
    "Hour1" to "1 hour",
    "Hour3" to "3 hours",
    "Hour6" to "6 hours",
)

private val EXTEND_REASONS = listOf(
    "WaitingForBuyer" to "Waiting for the buyer",
    "InGameCoordination" to "In-game coordination",
    "PreparationDelay" to "Preparing the order",
    "TechnicalIssue" to "Technical issue",
)

@Composable
fun ExtendDialog(onDismiss: () -> Unit, onConfirm: (time: String, reason: String) -> Unit) {
    var time by remember { mutableStateOf(EXTEND_TIMES[1].first) }
    var reason by remember { mutableStateOf(EXTEND_REASONS.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extend delivery time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label("Extra time")
                EXTEND_TIMES.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { time = value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = time == value, onClick = { time = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Label("Reason (the buyer sees this)")
                EXTEND_REASONS.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { reason = value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == value, onClick = { reason = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(time, reason) }) { Text("Extend") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CorrectionDialog(onDismiss: () -> Unit, onConfirm: (message: String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask buyer to fix details") },
        text = {
            Column {
                Text(
                    "The buyer is asked to correct the delivery details they gave you (for example a wrong username).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("What's wrong?") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = message.isNotBlank(), onClick = { onConfirm(message.trim()) }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

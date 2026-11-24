package com.group4.liveroo.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.bluetooth.BluetoothController
import com.group4.liveroo.bluetooth.BtPermissions
import com.group4.liveroo.bluetooth.FoundDevice
import com.group4.liveroo.sync.InviteOffer
import com.group4.liveroo.sync.InviteOutcome
import com.group4.liveroo.sync.SyncOutcome
import com.group4.liveroo.sync.TransferIntent
import com.group4.liveroo.sync.TransferOffer
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.IconCircle
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.NotYetSynced
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.SecondaryButton
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.ErrorTerracotta
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.FreshGreen
import com.group4.liveroo.ui.theme.Ink
import com.group4.liveroo.ui.theme.SecondaryText
import com.group4.liveroo.ui.theme.SoftPeach
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===================== Shared Bluetooth helpers =====================

/** Shows the permission-missing (P19) or Bluetooth-off state, otherwise [content]. */
@Composable
fun ColumnScope.BluetoothGate(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val granted = remember(tick) { BtPermissions.granted(context) }
    val enabled = remember(tick) { c.bluetooth.isEnabled }

    when {
        !c.bluetooth.isSupported -> Banner("This phone doesn't support Bluetooth.", BannerKind.ERROR)
        !granted -> CenteredState(Icons.Filled.Bluetooth, "Allow Nearby devices to sync with groupmates",
            "Your groups and tasks work without it. Inviting, joining and syncing need it.") {
            PrimaryButton("Allow", { permLauncher.launch(BtPermissions.required) })
            SecondaryButton("Open settings", {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            })
        }
        !enabled -> CenteredState(Icons.Filled.BluetoothDisabled, "Bluetooth is off",
            "Turn it on to invite groupmates and share updates.") {
            PrimaryButton("Turn on", { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) })
        }
        else -> content()
    }
}

@Composable
private fun ColumnScope.CenteredState(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconCircle(icon)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.weight(1f))
    actions()
    Spacer(Modifier.size(16.dp))
}

/** Launches Android's "make visible for 120 seconds" pop-up (T5). */
@Composable
fun rememberVisibilityRequest(onResult: (visible: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult(it.resultCode != Activity.RESULT_CANCELED)
    }
    return {
        launcher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BluetoothController.VISIBLE_SECONDS)
        )
    }
}

@Composable
private fun VisibilityCountdown(since: Long) {
    var left by remember(since) { mutableLongStateOf(BluetoothController.VISIBLE_SECONDS.toLong()) }
    LaunchedEffect(since) {
        while (left > 0) {
            left = BluetoothController.VISIBLE_SECONDS - (System.currentTimeMillis() - since) / 1000
            delay(1000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Visibility, null, Modifier.size(16.dp), tint = SecondaryText)
        Text(
            if (left > 0) "Visible to nearby phones · ${left / 60}:${(left % 60).toString().padStart(2, '0')}" else "No longer visible",
            style = MaterialTheme.typography.bodySmall, color = SecondaryText
        )
    }
}

/** Connecting -> Exchanging updates -> Saving (T14: steps, not a percentage). */
@Composable
private fun SyncSteps(step: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Connecting", "Exchanging updates", "Saving").forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    i < step -> Icon(Icons.Filled.Check, null, Modifier.size(24.dp), tint = ForestGreen)
                    i == step -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = ForestGreen)
                    else -> Icon(Icons.Filled.Sync, null, Modifier.size(24.dp), tint = SecondaryText)
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, color = if (i <= step) Ink else SecondaryText)
            }
        }
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, enabled: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
    LiverooCard(onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(title, color = if (highlighted) FreshGreen else SoftPeach)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Ink else SecondaryText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
        }
    }
}

// ===================== S15 Sync (and transfer mode) =====================

private enum class SyncPhase { IDLE, SCANNING, WORKING }

@Composable
fun SyncScreen(nav: NavController, transferGroupId: String?, newLeaderId: String?) {
    val c = LocalContainer.current
    val transfer = if (transferGroupId != null && newLeaderId != null) TransferIntent(transferGroupId, newLeaderId) else null
    val unsynced by c.repository.unsyncedCount.collectAsStateWithLifecycle(0)
    val groups by c.repository.groups.collectAsStateWithLifecycle(emptyList())
    val allMembers by c.repository.allMembers.collectAsStateWithLifecycle(emptyList())
    val allTasks by c.repository.allTasks.collectAsStateWithLifecycle(emptyList())
    val known by c.repository.knownDevices.collectAsStateWithLifecycle(emptyList())
    val found by c.bluetooth.found.collectAsStateWithLifecycle()
    val scanning by c.bluetooth.scanning.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf(SyncPhase.IDLE) }
    var step by remember { mutableIntStateOf(0) }
    var visibleSince by remember { mutableStateOf<Long?>(null) }
    var otherOpen by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    var incomingTransfer by remember { mutableStateOf<Pair<TransferOffer, CompletableDeferred<Boolean>>?>(null) }
    val jobs = remember { mutableStateListOf<Job>() }

    val newLeaderName = transfer?.let { t -> allMembers.firstOrNull { it.groupId == t.groupId && it.userId == t.newLeaderId }?.displayName } ?: ""

    fun stopAll() {
        c.bluetooth.stopDiscovery(); c.bluetooth.cancelListening()
        jobs.forEach { it.cancel() }; jobs.clear()
    }
    DisposableEffect(Unit) { onDispose { stopAll() } }

    fun finish(outcome: SyncOutcome) {
        c.lastSyncResult.value = outcome
        phase = SyncPhase.IDLE
        nav.navigate(Routes.SYNC_RESULT)
    }

    val requestVisibility = rememberVisibilityRequest { visible ->
        visibleSince = if (visible) System.currentTimeMillis() else null
        phase = SyncPhase.SCANNING
        c.bluetooth.startDiscovery()
        if (visible) jobs += c.appScope.launch {
            // Wait for a groupmate who picks this phone.
            val socket = runCatching { c.bluetooth.acceptOne() }.getOrNull() ?: return@launch
            c.bluetooth.stopDiscovery()
            phase = SyncPhase.WORKING; step = 1
            val result = c.sync.respond(
                socket,
                allowSync = { name ->
                    val answer = CompletableDeferred<Boolean>()
                    incoming = name to answer
                    answer.await().also { incoming = null }
                },
                allowInvite = { false },  // invites are accepted on the Join screen only
                allowTransfer = { offer ->
                    // D6: becoming leader needs an explicit yes.
                    val answer = CompletableDeferred<Boolean>()
                    incomingTransfer = offer to answer
                    answer.await().also { incomingTransfer = null }
                }
            )
            finish(result as? SyncOutcome ?: SyncOutcome.Failed("Unexpected request"))
        }
    }

    fun pick(device: FoundDevice) {
        stopAll()
        phase = SyncPhase.WORKING; step = 0
        jobs += c.appScope.launch {
            val socket = try {
                c.bluetooth.connect(device.address)
            } catch (e: Exception) {
                finish(SyncOutcome.Failed("Couldn't reach that phone. Stay within about 10 m and keep Liveroo open on both phones.", transfer != null))
                return@launch
            }
            step = 1
            val outcome = c.sync.initiateSync(socket, transfer)
            step = 3
            finish(outcome)
        }
    }

    LiverooScaffold(
        title = if (transfer != null) "Transfer to $newLeaderName" else "Sync with a groupmate",
        nav = nav, showBack = transfer != null, tab = Tab.SYNC
    ) {
        BluetoothGate {
            when (phase) {
                SyncPhase.IDLE -> {
                    if (transfer != null) {
                        Banner("Ask $newLeaderName to open Sync and tap Start Sync. Leadership moves only when this sync succeeds.")
                    } else {
                        LiverooCard {
                            Text(
                                if (unsynced > 0) "$unsynced changes not yet synced" else "Everything on this phone is synced",
                                style = MaterialTheme.typography.titleSmall
                            )
                            groups.forEach { g ->
                                val n = allTasks.count { it.groupId == g.id && it.dirty } + allMembers.count { it.groupId == g.id && it.dirty } + (if (g.dirty) 1 else 0)
                                if (n > 0 || g.leftByMe) {
                                    HorizontalDivider()
                                    Row {
                                        Text(g.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(if (g.leftByMe) "You left" else "$n ${if (n == 1) "change" else "changes"}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Stand within about 10 m of a groupmate. Both of you tap Start Sync.",
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryButton("Start Sync", { requestVisibility() }, icon = Icons.Filled.Sync)
                    if (transfer != null) SecondaryButton("Cancel transfer", { nav.popBackStack() })
                    Spacer(Modifier.size(8.dp))
                }

                SyncPhase.SCANNING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ForestGreen)
                        Text(if (scanning) "Looking for groupmates…" else "Search finished", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    val since = visibleSince
                    if (since != null) VisibilityCountdown(since)
                    else Banner("You can still find others, but they can't find you.", BannerKind.WARNING)

                    // T4: known devices show Liveroo names; everything else by Bluetooth name.
                    val myGroupIds = groups.map { it.id }.toSet()
                    val knownByAddress = known.associateBy { it.address }
                    val groupmates = found.filter { d -> knownByAddress[d.address]?.let { k -> allMembers.any { it.userId == k.userId && it.groupId in myGroupIds } } == true }
                    val others = found - groupmates.toSet()

                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item { Text("Groupmates", style = MaterialTheme.typography.titleLarge) }
                        if (groupmates.isEmpty()) item {
                            Text("No groupmates found yet. Check that they're within about 10 m and have tapped Start Sync.",
                                style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                        }
                        items(groupmates, key = { it.address }) { d ->
                            val k = knownByAddress.getValue(d.address)
                            val shared = groups.filter { g -> allMembers.any { it.groupId == g.id && it.userId == k.userId } }.joinToString { it.name }
                            val allowed = transfer == null || k.userId == transfer.newLeaderId
                            DeviceRow(k.displayName, if (allowed) shared else "Only $newLeaderName can be selected", enabled = allowed, highlighted = transfer != null && allowed) { pick(d) }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().clickable { otherOpen = !otherOpen }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Other nearby devices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text("${others.size}", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                                Icon(if (otherOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                            }
                            Text("A groupmate you haven't synced with yet shows here by their phone's name.",
                                style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        }
                        if (otherOpen) items(others, key = { it.address }) { d ->
                            DeviceRow(d.bluetoothName, "Phone or device nearby") { pick(d) }
                        }
                    }
                    if (!scanning) PrimaryButton("Scan again", { c.bluetooth.startDiscovery() })
                    SecondaryButton("Cancel", { stopAll(); phase = SyncPhase.IDLE })
                }

                SyncPhase.WORKING -> {
                    Spacer(Modifier.size(16.dp))
                    SyncSteps(step)
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("Cancel", { stopAll(); phase = SyncPhase.IDLE })
                }
            }
        }
    }

    incoming?.let { (name, answer) ->
        AlertDialog(
            onDismissRequest = { answer.complete(false) },
            title = { Text("$name wants to sync", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Allow?") },
            confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text("Sync") } },
            dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text("Decline", color = Ink) } }
        )
    }

    incomingTransfer?.let { (offer, answer) ->
        AlertDialog(
            onDismissRequest = { answer.complete(false) },
            icon = { Icon(Icons.Filled.WorkspacePremium, null) },
            title = {
                Text("${offer.fromName} wants to make you the leader of ${offer.groupName}", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "You'll be able to invite members and create, edit and delete tasks. " +
                        "${offer.fromName} becomes a regular member. This only happens if the sync finishes."
                )
            },
            confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text("Become leader") } },
            dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text("Decline", color = Ink) } }
        )
    }
}

// ===================== S16 Sync result =====================

@Composable
fun SyncResultScreen(nav: NavController) {
    val c = LocalContainer.current
    val outcome by c.lastSyncResult.collectAsStateWithLifecycle()
    fun done() = nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } }

    LiverooScaffold("Sync result", nav, tab = Tab.SYNC) {
        when (val o = outcome) {
            is SyncOutcome.Success -> {
                LiverooCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconCircle(if (o.transferDone != null) Icons.Filled.WorkspacePremium else Icons.Filled.Check, bg = FreshGreen, tint = Ink, size = 64)
                        Text("Synced with ${o.peerName}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text("Sent ${o.sent} updates · Received ${o.received} updates", style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (o.notes.isNotEmpty()) LiverooCard {
                    Text("CHANGES", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    o.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Done", { done() })
                SecondaryButton("Sync with someone else", { nav.popBackStack() })
            }
            is SyncOutcome.Failed -> {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconCircle(Icons.Filled.Close, bg = SoftPeach, tint = ErrorTerracotta)
                    if (o.wasTransfer) {
                        // C20: a failed transfer changes nothing.
                        Text("Transfer didn't finish", style = MaterialTheme.typography.headlineMedium)
                        Text(o.reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Text("Nothing changed on either phone.", style = MaterialTheme.typography.bodyLarge, color = SecondaryText, textAlign = TextAlign.Center)
                    } else {
                        Text("Sync didn't finish", style = MaterialTheme.typography.headlineMedium)
                        Text("${o.reason} Nothing was changed on this phone.", style = MaterialTheme.typography.bodyLarge, color = SecondaryText, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(if (o.wasTransfer) "Retry" else "Try again", { nav.popBackStack() }, icon = Icons.Filled.Sync)
                SecondaryButton("Cancel", { done() })
            }
            SyncOutcome.Declined -> {
                Banner("Your groupmate declined the sync. Nothing was changed.", BannerKind.WARNING)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Back to Sync", { nav.popBackStack() })
            }
            SyncOutcome.TransferDeclined -> {
                Banner("They didn't agree to become the leader. Nothing was changed on either phone.", BannerKind.WARNING)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Back", { nav.popBackStack() })
                SecondaryButton("Done", { done() })
            }
            null -> Text("No sync yet.", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.size(8.dp))
    }
}

// ===================== S09 Invite members (leader only) =====================

private sealed interface InviteState {
    data object Scanning : InviteState
    data class Waiting(val deviceName: String) : InviteState
    data class Done(val name: String) : InviteState
    data object Declined : InviteState
    data class Failed(val reason: String) : InviteState
}

@Composable
fun InviteScreen(nav: NavController, groupId: String) {
    val c = LocalContainer.current
    val found by c.bluetooth.found.collectAsStateWithLifecycle()
    val scanning by c.bluetooth.scanning.collectAsStateWithLifecycle()
    val members by c.repository.members(groupId).collectAsStateWithLifecycle(emptyList())
    val known by c.repository.knownDevices.collectAsStateWithLifecycle(emptyList())
    var state by remember { mutableStateOf<InviteState>(InviteState.Scanning) }
    val jobs = remember { mutableStateListOf<Job>() }
    DisposableEffect(Unit) { onDispose { c.bluetooth.stopDiscovery(); jobs.forEach { it.cancel() } } }

    LiverooScaffold("Invite members", nav) {
        BluetoothGate {
            LaunchedEffect(Unit) { c.bluetooth.startDiscovery() }
            when (val s = state) {
                InviteState.Scanning -> {
                    Banner("Ask them to open Liveroo, tap + Group → Join a group, then tap Allow when Android asks to make their phone visible.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ForestGreen)
                        Text(if (scanning) "Looking for nearby phones…" else "Search finished", style = MaterialTheme.typography.titleSmall)
                    }
                    val memberIds = members.map { it.userId }.toSet()
                    val knownByAddress = known.associateBy { it.address }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(found, key = { it.address }) { d ->
                            val k = knownByAddress[d.address]
                            val inGroup = k != null && k.userId in memberIds
                            DeviceRow(k?.displayName ?: d.bluetoothName, if (inGroup) "Already in group" else "Nearby", enabled = !inGroup) {
                                c.bluetooth.stopDiscovery()
                                state = InviteState.Waiting(d.bluetoothName)
                                jobs += c.appScope.launch {
                                    state = try {
                                        val socket = c.bluetooth.connect(d.address)
                                        when (val r = c.sync.initiateInvite(socket, groupId)) {
                                            is InviteOutcome.Joined -> InviteState.Done(r.name)
                                            InviteOutcome.Declined -> InviteState.Declined
                                            is InviteOutcome.Failed -> InviteState.Failed(r.reason)
                                        }
                                    } catch (e: Exception) {
                                        InviteState.Failed("Couldn't reach that phone. Stay within about 10 m and keep Liveroo open on both phones.")
                                    }
                                }
                            }
                        }
                    }
                    Text("The invitee's Join screen shows the name their phone appears as.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    if (!scanning) PrimaryButton("Scan again", { c.bluetooth.startDiscovery() })
                    Spacer(Modifier.size(8.dp))
                }
                is InviteState.Waiting -> CenteredState(Icons.Filled.PhoneAndroid, "Waiting for ${s.deviceName} to accept…",
                    "Keep Liveroo open and stay within about 10 m. If Android shows a pairing code, confirm it on both phones.") {
                    SecondaryButton("Cancel", { jobs.forEach { it.cancel() }; state = InviteState.Scanning; c.bluetooth.startDiscovery() })
                }
                is InviteState.Done -> CenteredState(Icons.Filled.Check, "${s.name} joined", "Group data sent.") {
                    PrimaryButton("Back to members", { nav.popBackStack() })
                }
                InviteState.Declined -> CenteredState(Icons.Filled.Close, "The invite was declined", "Nothing was changed.") {
                    PrimaryButton("Invite someone else", { state = InviteState.Scanning; c.bluetooth.startDiscovery() })
                }
                is InviteState.Failed -> CenteredState(Icons.Filled.Close, "Invite didn't finish", s.reason) {
                    PrimaryButton("Try again", { state = InviteState.Scanning; c.bluetooth.startDiscovery() })
                }
            }
        }
    }
}

// ===================== S10 Join a group =====================

private sealed interface JoinState {
    data object Asking : JoinState
    data class Waiting(val since: Long) : JoinState
    data object Denied : JoinState
    data object TimedOut : JoinState
    data object Receiving : JoinState
    data class Done(val groupName: String) : JoinState
    data class Failed(val reason: String) : JoinState
}

@Composable
fun JoinScreen(nav: NavController) {
    val c = LocalContainer.current
    var state by remember { mutableStateOf<JoinState>(JoinState.Asking) }
    var offer by remember { mutableStateOf<Pair<InviteOffer, CompletableDeferred<Boolean>>?>(null) }
    val jobs = remember { mutableStateListOf<Job>() }
    DisposableEffect(Unit) { onDispose { c.bluetooth.cancelListening(); jobs.forEach { it.cancel() } } }

    val requestVisibility = rememberVisibilityRequest { visible ->
        if (!visible) { state = JoinState.Denied; return@rememberVisibilityRequest }
        state = JoinState.Waiting(System.currentTimeMillis())
        jobs += c.appScope.launch {
            val socket = runCatching { c.bluetooth.acceptOne() }.getOrNull()
            if (socket == null) { state = JoinState.TimedOut; return@launch }
            val result = c.sync.respond(
                socket,
                allowSync = { false },
                allowInvite = { o ->
                    val answer = CompletableDeferred<Boolean>()
                    offer = o to answer
                    answer.await().also { yes -> offer = null; if (yes) state = JoinState.Receiving }
                }
            )
            state = when (result) {
                is InviteOutcome.Joined -> JoinState.Done(result.name)
                InviteOutcome.Declined -> JoinState.Waiting(System.currentTimeMillis())
                is InviteOutcome.Failed -> JoinState.Failed(result.reason)
                else -> JoinState.Failed("Connection lost before the group data arrived. Nothing was saved. Ask the leader to invite you again.")
            }
        }
    }

    LiverooScaffold("Join a group", nav) {
        BluetoothGate {
            LaunchedEffect(Unit) { if (state == JoinState.Asking) requestVisibility() }
            when (val s = state) {
                JoinState.Asking -> CenteredState(Icons.Filled.Visibility, "Make your phone visible", "Android will ask to make your phone visible for 120 seconds.")
                is JoinState.Waiting -> {
                    CenteredState(Icons.Filled.Bluetooth, "Ready to join", "Ask your group leader to invite you. Keep this screen open.") {
                        LiverooCard {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Your phone appears as", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                                Text(c.bluetooth.myBluetoothName, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        VisibilityCountdown(s.since)
                    }
                }
                JoinState.Denied -> CenteredState(Icons.Filled.Visibility, "Your leader can't find your phone", "Allow visibility so their phone can see yours.") {
                    PrimaryButton("Try again", { requestVisibility() })
                }
                JoinState.TimedOut -> CenteredState(Icons.Filled.Visibility, "No invite yet", "Your phone is no longer visible.") {
                    PrimaryButton("Make visible again", { requestVisibility() })
                }
                JoinState.Receiving -> {
                    Text("Joining…", style = MaterialTheme.typography.headlineMedium)
                    SyncSteps(1)
                }
                is JoinState.Done -> CenteredState(Icons.Filled.Check, "You joined", "${s.groupName} is now on this phone.") {
                    PrimaryButton("Open your groups", { nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } } })
                }
                is JoinState.Failed -> CenteredState(Icons.Filled.Close, "Join didn't finish", s.reason) {
                    PrimaryButton("Try again", { requestVisibility() })
                }
            }
        }
    }

    offer?.let { (o, answer) ->
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Filled.Groups, null) },
            title = { Text("You're invited", style = MaterialTheme.typography.titleLarge) },
            text = { Text("${o.leaderName} invited you to ${o.groupName}.") },
            confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text("Join") } },
            dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text("Decline", color = Ink) } }
        )
    }
}

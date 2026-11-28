package com.group4.liveroo.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import com.group4.liveroo.ui.components.GroupedCard
import com.group4.liveroo.ui.components.IconTile
import com.group4.liveroo.ui.components.ListRow
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooDialog
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.RowDivider
import com.group4.liveroo.ui.components.SecondaryButton
import com.group4.liveroo.ui.components.SectionHeader
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.components.VisibilityTimerChip
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooRadius
import com.group4.liveroo.ui.theme.LiverooTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.group4.liveroo.ui.components.CenteredState as StateBlock
import com.group4.liveroo.ui.components.SyncSteps as StepsCard

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
        !granted -> CenteredState(Icons.Rounded.Bluetooth, "Allow Nearby devices to sync with groupmates",
            "Your groups and tasks work without it. Inviting, joining and syncing need it.") {
            PrimaryButton("Allow", { permLauncher.launch(BtPermissions.required) })
            SecondaryButton("Open settings", {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            })
        }
        !enabled -> CenteredState(Icons.Rounded.BluetoothDisabled, "Bluetooth is off",
            "Turn it on to invite groupmates and share updates.") {
            PrimaryButton("Turn on", { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) })
        }
        else -> content()
    }
}

/** Centered message in the middle of the screen with the actions pinned at the bottom. */
@Composable
private fun ColumnScope.CenteredState(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String,
    iconBg: Color = Color.Unspecified, iconTint: Color = Color.Unspecified,
    titleStyle: TextStyle? = null, titleColor: Color = Color.Unspecified,
    extra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Spacer(Modifier.weight(1f))
    StateBlock(icon, title, body, iconBg = iconBg, iconTint = iconTint, titleStyle = titleStyle, titleColor = titleColor, actions = extra)
    Spacer(Modifier.weight(1f))
    actions()
    Spacer(Modifier.size(LiverooDimens.space24))
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
    VisibilityTimerChip(
        if (left > 0) "Visible to nearby phones · ${left / 60}:${(left % 60).toString().padStart(2, '0')}" else "No longer visible",
        visible = left > 0
    )
}

/** Connecting -> Exchanging updates -> Saving (T14: steps, not a percentage). */
@Composable
private fun SyncSteps(step: Int) {
    StepsCard(listOf("Connecting", "Exchanging updates", "Saving"), step)
}

/** One device row, for use inside a [GroupedCard]. */
@Composable
private fun DeviceRow(title: String, subtitle: String, enabled: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
    ListRow(
        title = title, subtitle = subtitle,
        leading = { Avatar(title, color = if (highlighted) LiverooTheme.colors.statusDone else Color.Unspecified) },
        chevron = enabled, enabled = enabled, onClick = onClick
    )
}

/** Device rows in one grouped card with dividers between them. */
@Composable
private fun <T> DeviceList(items: List<T>, row: @Composable (T) -> Unit) {
    GroupedCard {
        items.forEachIndexed { i, d ->
            if (i > 0) RowDivider(LiverooDimens.rowDividerInsetLeading)
            row(d)
        }
    }
}

/** Round Done-fill circle with a check or crown. */
@Composable
private fun DoneCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 64) {
    val c = LiverooTheme.colors
    Box(Modifier.size(size.dp).background(c.statusDone, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.5f).dp), tint = c.onStatusDone)
    }
}

/** Card with the larger hero radius. */
@Composable
private fun HeroCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(LiverooRadius.large).background(LiverooTheme.colors.surface, LiverooRadius.large).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

// ===================== S15 Sync (and transfer mode) =====================

private enum class SyncPhase { IDLE, SCANNING, WORKING }

@Composable
fun SyncScreen(nav: NavController, transferGroupId: String?, newLeaderId: String?) {
    val c = LocalContainer.current
    val colors = LiverooTheme.colors
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
    var otherOpen by remember { mutableStateOf<Boolean?>(null) } // null = default (open when no groupmates found)
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
                        Banner(
                            "Ask $newLeaderName to open Sync and tap Start Sync. Leadership moves only when this sync succeeds.",
                            icon = Icons.Rounded.WorkspacePremium
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clip(LiverooRadius.large).background(colors.surface, LiverooRadius.large).padding(LiverooDimens.space16),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconTile(
                                if (unsynced > 0) Icons.Rounded.Schedule else Icons.Rounded.Check,
                                size = LiverooDimens.iconTileHero,
                                bg = if (unsynced > 0) colors.pendingSurface else colors.accentContainer,
                                tint = if (unsynced > 0) colors.pendingText else colors.accent
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (unsynced > 0) "$unsynced changes not yet synced" else "Everything on this phone is synced",
                                    style = LiverooTheme.type.cardTitle, color = colors.textPrimary
                                )
                                Text(
                                    if (unsynced > 0) "Saved on this phone. Sync with a groupmate to share them."
                                    else "You can still sync to get your groupmates' changes.",
                                    style = LiverooTheme.type.body, color = colors.textSecondary
                                )
                            }
                        }
                        val waiting = groups.map { g ->
                            g to (allTasks.count { it.groupId == g.id && it.dirty } + allMembers.count { it.groupId == g.id && it.dirty } + (if (g.dirty) 1 else 0))
                        }.filter { (g, n) -> n > 0 || g.leftByMe }
                        if (waiting.isNotEmpty()) {
                            GroupedCard(header = "Waiting to sync", count = waiting.size) {
                                waiting.forEachIndexed { i, (g, n) ->
                                    if (i > 0) RowDivider(LiverooDimens.rowDividerInsetLeading)
                                    ListRow(
                                        title = g.name,
                                        leading = { IconTile(Icons.Rounded.Groups, bg = colors.pendingSurface, tint = colors.pendingText) },
                                        trailing = {
                                            Text(
                                                if (g.leftByMe) "You left" else "$n ${if (n == 1) "change" else "changes"}",
                                                style = LiverooTheme.type.chip, color = colors.pendingText
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.size(20.dp), tint = colors.textSecondary)
                        Text(
                            "Stand within about 10 m of a groupmate. Both of you tap Start Sync.",
                            style = LiverooTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    PrimaryButton("Start Sync", { requestVisibility() }, icon = Icons.Rounded.Sync)
                    if (transfer != null) SecondaryButton("Cancel transfer", { nav.popBackStack() })
                    Spacer(Modifier.size(8.dp))
                }

                SyncPhase.SCANNING -> {
                    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                        Text(
                            if (scanning) "Looking for groupmates…" else "Search finished",
                            style = LiverooTheme.type.itemTitle, color = colors.textPrimary, modifier = Modifier.weight(1f)
                        )
                    }
                    val since = visibleSince
                    if (since != null) VisibilityCountdown(since)
                    else Banner("You can still find others, but they can't find you.", BannerKind.WARNING)

                    // T4: known devices show Liveroo names; everything else by Bluetooth name.
                    val myGroupIds = groups.map { it.id }.toSet()
                    val knownByAddress = known.associateBy { it.address }
                    val groupmates = found.filter { d -> knownByAddress[d.address]?.let { k -> allMembers.any { it.userId == k.userId && it.groupId in myGroupIds } } == true }
                    val others = found - groupmates.toSet()
                    val othersOpen = otherOpen ?: groupmates.isEmpty()

                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item { Text("Groupmates", style = LiverooTheme.type.heading, color = colors.textPrimary, modifier = Modifier.semantics { heading() }) }
                        if (groupmates.isEmpty()) item {
                            Text("No groupmates found yet. Check that they're within about 10 m and have tapped Start Sync.",
                                style = LiverooTheme.type.body, color = colors.textSecondary)
                        } else item {
                            DeviceList(groupmates) { d ->
                                val k = knownByAddress.getValue(d.address)
                                val shared = groups.filter { g -> allMembers.any { it.groupId == g.id && it.userId == k.userId } }.joinToString { it.name }
                                val allowed = transfer == null || k.userId == transfer.newLeaderId
                                DeviceRow(k.displayName, if (allowed) shared else "Only $newLeaderName can be selected", enabled = allowed, highlighted = transfer != null && allowed) { pick(d) }
                            }
                        }
                        item {
                            Column {
                                SectionHeader("Other nearby devices", others.size, expanded = othersOpen, onClick = { otherOpen = !othersOpen })
                                Text("A groupmate you haven't synced with yet shows here by their phone's name.",
                                    style = LiverooTheme.type.caption, color = colors.textSecondary)
                            }
                        }
                        if (othersOpen && others.isNotEmpty()) item {
                            DeviceList(others) { d -> DeviceRow(d.bluetoothName, "Phone or device nearby") { pick(d) } }
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
        LiverooDialog(
            onDismissRequest = { answer.complete(false) },
            title = "$name wants to sync",
            body = "Allow?",
            confirmText = "Sync",
            onConfirm = { answer.complete(true) },
            dismissText = "Decline",
            onDismiss = { answer.complete(false) }
        )
    }

    incomingTransfer?.let { (offer, answer) ->
        LiverooDialog(
            onDismissRequest = { answer.complete(false) },
            icon = Icons.Rounded.WorkspacePremium,
            title = "${offer.fromName} wants to make you the leader of ${offer.groupName}",
            body = "You'll be able to invite members and create, edit and delete tasks. " +
                "${offer.fromName} becomes a regular member. This only happens if the sync finishes.",
            confirmText = "Become leader",
            onConfirm = { answer.complete(true) },
            dismissText = "Decline",
            onDismiss = { answer.complete(false) }
        )
    }
}

// ===================== S16 Sync result =====================

@Composable
fun SyncResultScreen(nav: NavController) {
    val c = LocalContainer.current
    val colors = LiverooTheme.colors
    val outcome by c.lastSyncResult.collectAsStateWithLifecycle()
    fun done() = nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } }

    LiverooScaffold("Sync result", nav, tab = Tab.SYNC) {
        when (val o = outcome) {
            is SyncOutcome.Success -> {
                HeroCard {
                    DoneCircle(if (o.transferDone != null) Icons.Rounded.WorkspacePremium else Icons.Rounded.Check)
                    Text("Synced with ${o.peerName}", style = LiverooTheme.type.brandHeadline, color = colors.accent, textAlign = TextAlign.Center)
                    Text("Sent ${o.sent} updates · Received ${o.received} updates", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary, textAlign = TextAlign.Center)
                }
                if (o.notes.isNotEmpty()) GroupedCard(header = "Changes") {
                    o.notes.forEachIndexed { i, note ->
                        if (i > 0) RowDivider()
                        Text(note, style = LiverooTheme.type.body, color = colors.textPrimary, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Done", { done() })
                SecondaryButton("Sync with someone else", { nav.popBackStack() })
            }
            is SyncOutcome.Failed -> {
                if (o.wasTransfer) {
                    // C20: a failed transfer changes nothing.
                    CenteredState(Icons.Rounded.Close, "Transfer didn't finish", o.reason, iconBg = colors.errorSurface, iconTint = colors.error,
                        extra = { Text("Nothing changed on either phone.", style = LiverooTheme.type.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center) }
                    ) {
                        PrimaryButton("Retry", { nav.popBackStack() }, icon = Icons.Rounded.Sync)
                        SecondaryButton("Cancel", { done() })
                    }
                } else {
                    CenteredState(Icons.Rounded.Close, "Sync didn't finish", "${o.reason} Nothing was changed on this phone.", iconBg = colors.errorSurface, iconTint = colors.error) {
                        PrimaryButton("Try again", { nav.popBackStack() }, icon = Icons.Rounded.Sync)
                        SecondaryButton("Cancel", { done() })
                    }
                }
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
            null -> Text("No sync yet.", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary)
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
    val colors = LiverooTheme.colors
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
                    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                        Text(if (scanning) "Looking for nearby phones…" else "Search finished", style = LiverooTheme.type.itemTitle, color = colors.textPrimary)
                    }
                    val memberIds = members.map { it.userId }.toSet()
                    val knownByAddress = known.associateBy { it.address }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (found.isNotEmpty()) item {
                            DeviceList(found) { d ->
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
                    }
                    Text("The invitee's Join screen shows the name their phone appears as.", style = LiverooTheme.type.caption, color = colors.textSecondary)
                    if (!scanning) PrimaryButton("Scan again", { c.bluetooth.startDiscovery() })
                    Spacer(Modifier.size(8.dp))
                }
                is InviteState.Waiting -> CenteredState(Icons.Rounded.PhoneAndroid, "Waiting for ${s.deviceName} to accept…",
                    "Keep Liveroo open and stay within about 10 m. If Android shows a pairing code, confirm it on both phones.") {
                    SecondaryButton("Cancel", { jobs.forEach { it.cancel() }; state = InviteState.Scanning; c.bluetooth.startDiscovery() })
                }
                is InviteState.Done -> CenteredState(Icons.Rounded.Check, "${s.name} joined", "Group data sent.", iconBg = colors.statusDone, iconTint = colors.onStatusDone) {
                    PrimaryButton("Back to members", { nav.popBackStack() })
                }
                InviteState.Declined -> CenteredState(Icons.Rounded.Close, "The invite was declined", "Nothing was changed.", iconBg = colors.errorSurface, iconTint = colors.error) {
                    PrimaryButton("Invite someone else", { state = InviteState.Scanning; c.bluetooth.startDiscovery() })
                }
                is InviteState.Failed -> CenteredState(Icons.Rounded.Close, "Invite didn't finish", s.reason, iconBg = colors.errorSurface, iconTint = colors.error) {
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
    val colors = LiverooTheme.colors
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
                JoinState.Asking -> CenteredState(Icons.Rounded.Visibility, "Make your phone visible", "Android will ask to make your phone visible for 120 seconds.")
                is JoinState.Waiting -> {
                    CenteredState(
                        Icons.Rounded.Bluetooth, "Ready to join", "Ask your group leader to invite you. Keep this screen open.",
                        extra = {
                            LiverooCard {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Your phone appears as", style = LiverooTheme.type.body, color = colors.textSecondary)
                                    Text(c.bluetooth.myBluetoothName, style = LiverooTheme.type.titleMedium, color = colors.textPrimary)
                                }
                            }
                            VisibilityCountdown(s.since)
                        }
                    )
                }
                JoinState.Denied -> CenteredState(Icons.Rounded.Visibility, "Your leader can't find your phone", "Allow visibility so their phone can see yours.",
                    iconBg = colors.pendingSurface, iconTint = colors.pendingText) {
                    PrimaryButton("Try again", { requestVisibility() })
                }
                JoinState.TimedOut -> CenteredState(Icons.Rounded.Visibility, "No invite yet", "Your phone is no longer visible.",
                    iconBg = colors.pendingSurface, iconTint = colors.pendingText) {
                    PrimaryButton("Make visible again", { requestVisibility() })
                }
                JoinState.Receiving -> {
                    Text("Joining…", style = LiverooTheme.type.titleMedium, color = colors.textPrimary)
                    SyncSteps(1)
                }
                is JoinState.Done -> CenteredState(
                    Icons.Rounded.Check, "You joined", "${s.groupName} is now on this phone.",
                    iconBg = colors.statusDone, iconTint = colors.onStatusDone,
                    titleStyle = LiverooTheme.type.brandHeadline, titleColor = colors.accent
                ) {
                    PrimaryButton("Open your groups", { nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } } })
                }
                is JoinState.Failed -> CenteredState(Icons.Rounded.Close, "Join didn't finish", s.reason, iconBg = colors.errorSurface, iconTint = colors.error) {
                    PrimaryButton("Try again", { requestVisibility() })
                }
            }
        }
    }

    offer?.let { (o, answer) ->
        LiverooDialog(
            onDismissRequest = { },
            icon = Icons.Rounded.Groups,
            title = "You're invited",
            body = "${o.leaderName} invited you to ${o.groupName}.",
            confirmText = "Join",
            onConfirm = { answer.complete(true) },
            dismissText = "Decline",
            onDismiss = { answer.complete(false) }
        )
    }
}

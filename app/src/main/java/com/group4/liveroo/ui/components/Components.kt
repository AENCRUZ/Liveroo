package com.group4.liveroo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.ui.theme.AvatarInk
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooRadius
import com.group4.liveroo.ui.theme.LiverooTheme
import com.group4.liveroo.ui.theme.avatarColor

enum class Tab { GROUPS, MY_TASKS, SYNC }

// ---------------------------------------------------------------------------------------------
// Screen frame
// ---------------------------------------------------------------------------------------------

/**
 * Standard screen frame. Same parameters as before. Tab screens (showBack = false) get a large
 * left-aligned title; detail screens get a 48 dp back button and a title that wraps to 2 lines.
 * When [tab] is set, the screen shows the floating nav, which is drawn once by LiverooNavHost (addendum 01, B2);
 * the content keeps 120 dp clear at the bottom and the FAB and snackbar sit above it.
 * [snackbarHost] is optional.
 */
@Composable
fun LiverooScaffold(
    title: String,
    nav: NavController,
    showBack: Boolean = true,
    tab: Tab? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LiverooTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
            topBar = { LiverooTopBar(title, nav, showBack, actions) },
            snackbarHost = {
                if (snackbarHost != null) {
                    SnackbarHost(
                        snackbarHost,
                        modifier = Modifier.padding(bottom = if (tab != null) LiverooDimens.fabBottomOffset - 16.dp else 0.dp)
                    ) { LiverooSnackbar(it) }
                }
            },
            floatingActionButton = {
                Box(Modifier.padding(bottom = if (tab != null) LiverooDimens.fabBottomOffset - 16.dp else 0.dp)) {
                    floatingActionButton()
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = LiverooDimens.space16)
                    .padding(bottom = if (tab != null) LiverooDimens.listBottomPaddingWithNav else 0.dp),
                verticalArrangement = Arrangement.spacedBy(LiverooDimens.space12),
                content = content
            )
        }
    }
}

@Composable
private fun LiverooTopBar(title: String, nav: NavController, showBack: Boolean, actions: @Composable RowScope.() -> Unit) {
    val type = LiverooTheme.type
    val colors = LiverooTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = if (showBack) 4.dp else LiverooDimens.space16)
            .padding(top = if (showBack) 4.dp else 20.dp, bottom = if (showBack) 4.dp else 8.dp)
            .heightIn(min = LiverooDimens.touchMin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = { nav.popBackStack() }, Modifier.size(LiverooDimens.touchMin)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
        }
        if (title.isNotBlank()) Text(
            title,
            style = if (showBack) type.titleMedium else type.titleLarge,
            color = colors.textPrimary,
            maxLines = 2,
            modifier = Modifier.weight(1f).semantics { heading() }
        ) else Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ---------------------------------------------------------------------------------------------
// Cards and rows
// ---------------------------------------------------------------------------------------------

/** Single flat card (surface, radius 20, no border). Existing signature kept. */
@Composable
fun LiverooCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = LiverooRadius.medium
    var m = modifier.fillMaxWidth().clip(shape).background(LiverooTheme.colors.surface, shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
    Column(m.padding(LiverooDimens.space16), verticalArrangement = Arrangement.spacedBy(LiverooDimens.space8), content = content)
}

/** Surface card whose children are rows separated by [RowDivider]. Optional header (label + count pill). */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    header: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LiverooTheme.colors
    Column(modifier.fillMaxWidth().clip(LiverooRadius.medium).background(colors.surface, LiverooRadius.medium)) {
        if (header != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(header, style = LiverooTheme.type.heading, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                if (count != null) {
                    Text(
                        "$count", style = LiverooTheme.type.chip, color = colors.pendingText,
                        modifier = Modifier.background(colors.pendingSurface, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun RowDivider(inset: Dp = LiverooDimens.rowDividerInset) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(LiverooTheme.colors.divider))
}

/** Rounded icon tile (40 dp, radius 12 by default). */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = LiverooDimens.iconTile,
    bg: Color = Color.Unspecified,
    tint: Color = Color.Unspecified
) {
    val c = LiverooTheme.colors
    Box(
        modifier.size(size).background(bg.orElse(c.surfaceTile), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = tint.orElse(c.textPrimary)) }
}

private fun Color.orElse(other: Color) = if (this == Color.Unspecified) other else this

/** One list row: leading, title, subtitle, trailing, optional chevron. Whole row is one TalkBack node. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val c = LiverooTheme.colors
    val minH = when {
        subtitle != null -> LiverooDimens.rowMinTwoLine
        leading != null -> LiverooDimens.rowMinWithLeading
        else -> LiverooDimens.rowMin
    }
    var m = modifier.fillMaxWidth().heightIn(min = minH)
    if (onClick != null && enabled) m = m.clickable(role = Role.Button, onClick = onClick)
    Row(
        m.alpha(if (enabled) 1f else 0.55f).semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LiverooTheme.type.itemTitle, color = if (destructive) c.error else c.textPrimary)
            if (subtitle != null) Text(subtitle, style = LiverooTheme.type.body, color = c.textSecondary)
        }
        trailing?.invoke()
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}

// ---------------------------------------------------------------------------------------------
// Status, tags, chips
// ---------------------------------------------------------------------------------------------

fun statusLabel(s: TaskStatus) = when (s) {
    TaskStatus.TODO -> "To Do"; TaskStatus.IN_PROGRESS -> "In Progress"; TaskStatus.DONE -> "Done"
}

@Composable
fun statusIcon(status: TaskStatus): Pair<ImageVector, Color> {
    val c = LiverooTheme.colors
    return when (status) {
        TaskStatus.TODO -> Icons.Rounded.RadioButtonUnchecked to c.textPrimary
        TaskStatus.IN_PROGRESS -> Icons.Rounded.Contrast to c.statusProgress
        TaskStatus.DONE -> Icons.Rounded.CheckCircle to c.statusDoneIcon
    }
}

/** Icon + label, no border or fill. [prefix] is kept for the count-before-label use. */
@Composable
fun StatusIndicator(status: TaskStatus, modifier: Modifier = Modifier, prefix: String = "") {
    val (icon, tint) = statusIcon(status)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Text(prefix + statusLabel(status), style = LiverooTheme.type.chip, color = LiverooTheme.colors.textPrimary)
    }
}

/** Old name, kept so screens compile. */
@Composable
fun StatusChip(status: TaskStatus, prefix: String = "") = StatusIndicator(status, prefix = prefix)

/** Read-only counts: icon + number for each status, then the legend. */
@Composable
fun StatusSummary(todo: Int, inProgress: Int, done: Int, modifier: Modifier = Modifier) {
    val c = LiverooTheme.colors
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(TaskStatus.TODO to todo, TaskStatus.IN_PROGRESS to inProgress, TaskStatus.DONE to done).forEach { (s, n) ->
                val (icon, tint) = statusIcon(s)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icon, null, Modifier.size(18.dp), tint = tint)
                    Text("$n", style = LiverooTheme.type.itemTitle, color = c.textPrimary)
                }
            }
        }
        Text("To Do · In progress · Done", style = LiverooTheme.type.caption, color = c.textSecondary)
    }
}

/** Pill: pending (amber, clock) or shared (green, check). */
@Composable
fun SyncStateChip(text: String, modifier: Modifier = Modifier, shared: Boolean = false) {
    val c = LiverooTheme.colors
    val bg = if (shared) c.accentContainer else c.pendingSurface
    val fg = if (shared) c.onAccentContainer else c.pendingText
    Row(
        modifier.background(bg, CircleShape).padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(if (shared) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = if (shared) c.accent else fg)
        Text(text, style = LiverooTheme.type.micro, color = fg)
    }
}

@Composable
fun PendingChip(text: String = "Not yet synced", modifier: Modifier = Modifier) = SyncStateChip(text, modifier)

/** Old name, kept so screens compile. */
@Composable
fun NotYetSynced(text: String = "Not yet synced") = PendingChip(text)

@Composable
fun RoleTag(isLeader: Boolean, left: Boolean = false) {
    val c = LiverooTheme.colors
    val shape = CircleShape
    when {
        left -> Text(
            "Left", style = LiverooTheme.type.micro, color = c.textSecondary,
            modifier = Modifier.background(c.divider, shape).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        isLeader -> Row(
            Modifier.background(c.accentContainer, shape).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(14.dp), tint = c.accent)
            Text("Leader", style = LiverooTheme.type.micro, color = c.onAccentContainer)
        }
        else -> Text(
            "Member", style = LiverooTheme.type.micro, color = c.textSecondary,
            modifier = Modifier.border(1.5.dp, c.textSecondary, shape).padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SmallTag(text: String) {
    val c = LiverooTheme.colors
    Text(
        text, style = LiverooTheme.type.micro, color = c.textPrimary,
        modifier = Modifier.background(c.surfaceTile, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Avatars
// ---------------------------------------------------------------------------------------------

/** Circle with initials. Colour follows the name unless [color] is given. Initials are always AvatarInk. */
@Composable
fun Avatar(name: String, size: Int = 40, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size.dp).background(color.orElse(if (name.isBlank() || name == "?") LiverooTheme.colors.surfaceTile else avatarColor(name)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = TextStyle(fontFamily = com.group4.liveroo.ui.theme.Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.32f).coerceAtLeast(10f).sp),
            color = AvatarInk
        )
    }
}

/** Overlapping avatars, max 3 plus "+N". One TalkBack node: "N members". */
@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier, size: Int = 28, ringColor: Color = Color.Unspecified) {
    val ring = ringColor.orElse(LiverooTheme.colors.surface)
    val shown = names.take(3)
    val extra = names.size - shown.size
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "${names.size} members" }, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, n ->
            Avatar(n, size, modifier = Modifier.offset(x = (-8).dp * i).border(2.dp, ring, CircleShape))
        }
        if (extra > 0) {
            Box(
                Modifier.offset(x = (-8).dp * shown.size).size(size.dp).background(LiverooTheme.colors.divider, CircleShape).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("+$extra", style = LiverooTheme.type.micro, color = LiverooTheme.colors.textPrimary) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Progress, group card, task row
// ---------------------------------------------------------------------------------------------

/** Decorative ring. Track divider, arc accent, starts at 12 o'clock. No tasks = empty track. */
@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 40.dp, stroke: Dp = 5.dp, centerText: String? = null) {
    val c = LiverooTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(c.divider, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            if (total > 0 && done > 0) {
                drawArc(c.accent, -90f, 360f * done.coerceAtMost(total) / total, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        if (centerText != null) Text(centerText, style = LiverooTheme.type.heading, color = c.textPrimary)
    }
}

/** Group card for Your groups. */
@Composable
fun GroupCard(
    name: String,
    isLeader: Boolean,
    done: Int,
    total: Int,
    todo: Int,
    inProgress: Int,
    memberNames: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    left: Boolean = false,
    syncText: String? = null
) {
    val c = LiverooTheme.colors
    Column(
        modifier.fillMaxWidth().clip(LiverooRadius.medium).background(c.surface, LiverooRadius.medium)
            .clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = LiverooTheme.type.cardTitle, color = c.textPrimary, maxLines = 2, modifier = Modifier.weight(1f))
            RoleTag(isLeader, left)
        }
        if (left) {
            Text("Read-only until your departure syncs", style = LiverooTheme.type.caption, color = c.textSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressRing(done, total)
                Column(Modifier.weight(1f)) {
                    Text("$done of $total tasks done", style = LiverooTheme.type.body, color = c.textPrimary)
                    Text("$todo To Do · $inProgress In progress", style = LiverooTheme.type.caption, color = c.textSecondary)
                }
                AvatarStack(memberNames)
            }
        }
        if (syncText != null) SyncStateChip(syncText)
    }
}

@Composable
private fun TaskRowBody(
    title: String, assigneeName: String?, status: TaskStatus, unsynced: Boolean, groupName: String?,
    modifier: Modifier, onClick: () -> Unit
) {
    val c = LiverooTheme.colors
    val desc = listOfNotNull(title, assigneeName ?: "Unassigned", statusLabel(status), if (unsynced) "not yet synced" else null).joinToString(", ")
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (groupName != null) Text(groupName, style = LiverooTheme.type.caption, color = c.textSecondary)
            Text(title, style = LiverooTheme.type.itemTitle, color = c.textPrimary, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (assigneeName != null) Avatar(assigneeName, 22) else
                    Box(Modifier.size(22.dp).background(c.divider, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, Modifier.size(14.dp), tint = c.textPrimary)
                    }
                Text(assigneeName ?: "Unassigned", style = LiverooTheme.type.body, color = c.textSecondary)
            }
            if (unsynced) PendingChip()
        }
        StatusIndicator(status)
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}

/** Task row in its own card (existing use). */
@Composable
fun TaskRow(title: String, assigneeName: String?, status: TaskStatus, unsynced: Boolean, groupName: String? = null, onClick: () -> Unit) {
    val c = LiverooTheme.colors
    TaskRowBody(
        title, assigneeName, status, unsynced, groupName,
        Modifier.clip(LiverooRadius.medium).background(c.surface, LiverooRadius.medium), onClick
    )
}

/** Task row without its own card, for use inside [GroupedCard] with [RowDivider] between rows. */
@Composable
fun TaskRowItem(title: String, assigneeName: String?, status: TaskStatus, unsynced: Boolean, groupName: String? = null, onClick: () -> Unit) =
    TaskRowBody(title, assigneeName, status, unsynced, groupName, Modifier, onClick)

/** "Saved on this phone" card; show only when [count] > 0. */
@Composable
fun SavedOnPhoneCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LiverooTheme.colors
    Row(
        modifier.fillMaxWidth().clip(LiverooRadius.medium).background(c.surface, LiverooRadius.medium)
            .border(1.dp, c.divider, LiverooRadius.medium)
            .clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(Icons.Rounded.PhoneAndroid, bg = c.pendingSurface, tint = c.pendingText)
        Column(Modifier.weight(1f)) {
            Text("Saved on this phone", style = LiverooTheme.type.itemTitle, color = c.textPrimary)
            Text(if (count == 1) "1 change waiting to sync" else "$count changes waiting to sync", style = LiverooTheme.type.body, color = c.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}

/** Section header. [expanded] != null makes it a full-width collapsible 48 dp button. */
@Composable
fun SectionHeader(label: String, count: Int, expanded: Boolean? = null, onClick: (() -> Unit)? = null) {
    val c = LiverooTheme.colors
    var m = Modifier.fillMaxWidth().padding(top = 8.dp)
    if (expanded != null && onClick != null) {
        m = m.heightIn(min = LiverooDimens.touchMin).clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $label" else "Expand $label", onClick = onClick).semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
    }
    Row(m.semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LiverooTheme.type.heading, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text("$count", style = LiverooTheme.type.caption, color = c.textSecondary)
        if (expanded != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(22.dp).rotate(if (expanded) 0f else -90f), tint = c.textSecondary)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Banner, buttons, FAB
// ---------------------------------------------------------------------------------------------

enum class BannerKind { INFO, WARNING, ERROR }

@Composable
fun Banner(
    text: String,
    kind: BannerKind = BannerKind.INFO,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    icon: ImageVector? = null
) {
    val c = LiverooTheme.colors
    val (bg, fg) = when (kind) {
        BannerKind.INFO -> c.surfaceTile to c.textPrimary
        BannerKind.WARNING -> c.pendingSurface to c.pendingText
        BannerKind.ERROR -> c.errorSurface to c.error
    }
    val ic = icon ?: when (kind) {
        BannerKind.INFO -> Icons.Rounded.Info
        BannerKind.WARNING -> Icons.Rounded.Schedule
        BannerKind.ERROR -> Icons.Rounded.Warning
    }
    Column(
        Modifier.fillMaxWidth().background(bg, LiverooRadius.small).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(ic, null, Modifier.size(20.dp), tint = fg)
            Text(text, style = LiverooTheme.type.body, color = fg)
        }
        if (actionLabel != null) TonalButton(actionLabel, onAction, Modifier.align(Alignment.Start))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = LiverooTheme.colors
    Button(
        onClick = onClick, enabled = enabled, shape = LiverooRadius.full,
        modifier = modifier.fillMaxWidth().heightIn(min = LiverooDimens.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent, contentColor = c.onAccent,
            disabledContainerColor = c.divider, disabledContentColor = c.textSecondary
        )
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = LiverooTheme.type.button)
    }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = LiverooTheme.colors
    Button(
        onClick = onClick, enabled = enabled, shape = LiverooRadius.full,
        modifier = modifier.heightIn(min = LiverooDimens.buttonHeightSmall),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accentContainer, contentColor = c.onAccentContainer,
            disabledContainerColor = c.divider, disabledContentColor = c.textSecondary
        )
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = LiverooTheme.type.button)
    }
}

@Composable
fun TextActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val c = LiverooTheme.colors
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = LiverooDimens.buttonHeightSmall), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text, style = LiverooTheme.type.button, color = if (destructive) c.error else c.textPrimary)
    }
}

/** Old name, kept so screens compile. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = TextActionButton(text, onClick, modifier)

/** Extended FAB: accent pill with plus icon and label. Position is set by [LiverooScaffold]. */
@Composable
fun LiverooFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Add) {
    val c = LiverooTheme.colors
    ExtendedFloatingActionButton(
        onClick = onClick, modifier = modifier.heightIn(min = 56.dp), shape = LiverooRadius.full,
        containerColor = c.accent, contentColor = c.onAccent,
        elevation = FloatingActionButtonDefaults.elevation(6.dp, 6.dp, 6.dp, 6.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = LiverooTheme.type.button)
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog, sheet, snackbar
// ---------------------------------------------------------------------------------------------

/** Wraps AlertDialog. [onDismissRequest] is passed through unchanged by each caller. */
@Composable
fun LiverooDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null
) {
    val c = LiverooTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = LiverooRadius.large,
        containerColor = c.surface,
        icon = icon?.let { { IconTile(it, size = 48.dp, bg = if (destructive) c.errorSurface else c.accentContainer, tint = if (destructive) c.error else c.accent) } },
        title = { Text(title, style = LiverooTheme.type.dialogTitle, color = c.textPrimary) },
        text = if (body != null || content != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (body != null) Text(body, style = LiverooTheme.type.body, color = c.textSecondary)
                    content?.invoke()
                }
            }
        } else null,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.heightIn(min = LiverooDimens.touchMin)) {
                Text(confirmText, style = LiverooTheme.type.button, color = if (!confirmEnabled) c.textSecondary else if (destructive) c.error else c.accent)
            }
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = LiverooDimens.touchMin)) {
                    Text(dismissText, style = LiverooTheme.type.button, color = c.textPrimary)
                }
            }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiverooBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LiverooTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = LiverooRadius.sheet,
        containerColor = c.surface,
        scrimColor = c.scrim
    ) {
        Column(Modifier.padding(bottom = 16.dp), content = content)
    }
}

/** Sheet row with a 40 dp accent tile. */
@Composable
fun SheetAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = LiverooTheme.colors
    ListRow(
        title = text, onClick = onClick,
        leading = { IconTile(icon, bg = c.accentContainer, tint = c.accent) }
    )
}

@Composable
fun LiverooSnackbar(data: SnackbarData) {
    val c = LiverooTheme.colors
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp)).background(c.snackbar, RoundedCornerShape(14.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.onSnackbar)
        Text(data.visuals.message, style = LiverooTheme.type.body, color = c.onSnackbar, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Empty / centered states, sync steps, timer chip
// ---------------------------------------------------------------------------------------------

/** Centered state: icon tile, title, body, actions. */
@Composable
fun CenteredState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconBg: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    titleStyle: TextStyle? = null,
    titleColor: Color = Color.Unspecified,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    val c = LiverooTheme.colors
    Column(
        modifier.fillMaxWidth().padding(LiverooDimens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, size = LiverooDimens.iconTileCentered, bg = iconBg.orElse(c.accentContainer), tint = iconTint.orElse(c.accent))
        Text(title, style = titleStyle ?: LiverooTheme.type.titleMedium, color = titleColor.orElse(c.textPrimary), textAlign = TextAlign.Center)
        Text(body, style = LiverooTheme.type.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
        actions()
    }
}

/** Empty state. Existing signature kept; [icon] is optional. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    val c = LiverooTheme.colors
    Column(
        modifier.fillMaxWidth().padding(LiverooDimens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) IconTile(icon, size = 80.dp, bg = c.accentContainer, tint = c.accent)
        Text(title, style = LiverooTheme.type.dialogTitle, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = LiverooTheme.type.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
        actions()
    }
}

/** Old name, kept so screens compile. Now a rounded tile (radius about 30% of size). */
@Composable
fun IconCircle(icon: ImageVector, bg: Color = Color.Unspecified, tint: Color = Color.Unspecified, size: Int = 96) {
    val c = LiverooTheme.colors
    IconTile(icon, size = size.dp, bg = bg.orElse(c.accentContainer), tint = tint.orElse(c.accent))
}

/** Three-step progress card: done = check, current = spinner, pending = dashed circle. */
@Composable
fun SyncSteps(steps: List<String>, currentIndex: Int, modifier: Modifier = Modifier) {
    val c = LiverooTheme.colors
    Column(
        modifier.fillMaxWidth().clip(LiverooRadius.medium).background(c.surface, LiverooRadius.medium)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(currentIndex.toFloat(), 0f..steps.size.toFloat(), steps.size) }
    ) {
        steps.forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    i < currentIndex -> Box(Modifier.size(28.dp).background(c.statusDone, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.onStatusDone)
                    }
                    i == currentIndex -> Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
                    }
                    else -> Canvas(Modifier.size(28.dp)) {
                        drawCircle(
                            c.outlineControl, radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                        )
                    }
                }
                Text(
                    label, style = if (i == currentIndex) LiverooTheme.type.itemTitle else LiverooTheme.type.bodyLarge,
                    color = if (i > currentIndex) c.textSecondary else c.textPrimary
                )
            }
            if (i < steps.lastIndex) RowDivider()
        }
    }
}

/** "Visible to nearby phones · 1:58". The countdown logic stays with the caller; pass the text. */
@Composable
fun VisibilityTimerChip(text: String, modifier: Modifier = Modifier, visible: Boolean = true) {
    val c = LiverooTheme.colors
    Row(
        modifier.background(if (visible) c.accentContainer else c.divider, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.Visibility, null, Modifier.size(16.dp), tint = if (visible) c.accent else c.textSecondary)
        Text(text, style = LiverooTheme.type.chip, color = if (visible) c.onAccentContainer else c.textSecondary)
    }
}

fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

package com.group4.liveroo.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.Border
import com.group4.liveroo.ui.theme.ErrorTerracotta
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.FreshGreen
import com.group4.liveroo.ui.theme.Ink
import com.group4.liveroo.ui.theme.MutedSage
import com.group4.liveroo.ui.theme.SecondaryText
import com.group4.liveroo.ui.theme.SoftPeach
import com.group4.liveroo.ui.theme.WarningDot
import com.group4.liveroo.ui.theme.WarningSurface
import com.group4.liveroo.ui.theme.WarningText
import com.group4.liveroo.ui.theme.WarmCream
import com.group4.liveroo.ui.theme.White

enum class Tab { GROUPS, MY_TASKS, SYNC }

/** Standard screen frame: Luckiest Guy title, optional back, optional bottom bar and FAB. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiverooScaffold(
    title: String,
    nav: NavController,
    showBack: Boolean = true,
    tab: Tab? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = WarmCream,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        bottomBar = { if (tab != null) LiverooBottomBar(nav, tab) },
        floatingActionButton = floatingActionButton
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun LiverooBottomBar(nav: NavController, current: Tab) {
    val unsynced by LocalContainer.current.repository.unsyncedCount.collectAsStateWithLifecycle(0)
    NavigationBar(containerColor = White) {
        val colors = NavigationBarItemDefaults.colors(indicatorColor = MutedSage, selectedIconColor = Ink, selectedTextColor = Ink)
        fun go(route: String) = nav.navigate(route) {
            popUpTo(Routes.GROUPS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        NavigationBarItem(current == Tab.GROUPS, { go(Routes.GROUPS) }, { Icon(Icons.Filled.Groups, null) }, label = { Text("Groups") }, colors = colors)
        NavigationBarItem(current == Tab.MY_TASKS, { go(Routes.MY_TASKS) }, { Icon(Icons.Filled.TaskAlt, null) }, label = { Text("My Tasks") }, colors = colors)
        NavigationBarItem(
            current == Tab.SYNC, { go(Routes.sync()) },
            {
                BadgedBox(badge = { if (unsynced > 0) Badge(containerColor = ErrorTerracotta) { Text("$unsynced") } }) {
                    Icon(Icons.Filled.Sync, null)
                }
            },
            label = { Text("Sync") }, colors = colors
        )
    }
}

@Composable
fun LiverooCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val m = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, border = BorderStroke(1.dp, Border),
            colors = CardDefaults.cardColors(containerColor = White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Card(modifier = m, shape = shape, border = BorderStroke(1.dp, Border),
            colors = CardDefaults.cardColors(containerColor = White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Status chip (C11): colour + icon + label, never colour alone. */
@Composable
fun StatusChip(status: TaskStatus, prefix: String = "") {
    val (bg, icon, label) = when (status) {
        TaskStatus.TODO -> Triple(White, Icons.Outlined.Circle, "To Do")
        TaskStatus.IN_PROGRESS -> Triple(MutedSage, Icons.Outlined.Contrast, "In Progress")
        TaskStatus.DONE -> Triple(FreshGreen, Icons.Filled.Check, "Done")
    }
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(bg, RoundedCornerShape(16.dp))
            .then(if (status == TaskStatus.TODO) Modifier.border(1.5.dp, Ink, RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Ink)
        Text(prefix + label, style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

fun statusLabel(s: TaskStatus) = when (s) {
    TaskStatus.TODO -> "To Do"; TaskStatus.IN_PROGRESS -> "In Progress"; TaskStatus.DONE -> "Done"
}

@Composable
fun RoleTag(isLeader: Boolean) {
    if (isLeader) {
        Row(
            Modifier.background(ForestGreen, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(14.dp), tint = White)
            Text("Leader", style = MaterialTheme.typography.labelSmall, color = White)
        }
    } else {
        Text(
            "Member", style = MaterialTheme.typography.labelSmall, color = ForestGreen,
            modifier = Modifier.border(1.5.dp, ForestGreen, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SmallTag(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = Ink,
        modifier = Modifier.background(SoftPeach, RoundedCornerShape(13.dp)).border(1.dp, Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** "Not yet synced" marker (P7, C21). */
@Composable
fun NotYetSynced(text: String = "Not yet synced") {
    Row(
        Modifier.background(WarningSurface, RoundedCornerShape(12.dp)).padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(WarningDot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = WarningText)
    }
}

@Composable
fun Avatar(name: String, size: Int = 40, color: Color = MutedSage) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, style = if (size < 36) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = Ink)
    }
}

/** Task row used in Group detail, Members and My Tasks. */
@Composable
fun TaskRow(title: String, assigneeName: String?, status: TaskStatus, unsynced: Boolean, groupName: String? = null, onClick: () -> Unit) {
    LiverooCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (groupName != null) Text(groupName, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (assigneeName != null) Avatar(assigneeName, 28) else
                        Box(Modifier.size(28.dp).background(Border, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = Ink)
                        }
                    Text(assigneeName ?: "Unassigned", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                }
                if (unsynced) NotYetSynced()
            }
            StatusChip(status)
        }
    }
}

@Composable
fun SectionHeader(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
    }
}

enum class BannerKind { INFO, WARNING, ERROR }

@Composable
fun Banner(text: String, kind: BannerKind = BannerKind.INFO, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val (bg, fg) = when (kind) {
        BannerKind.INFO -> SoftPeach to Ink
        BannerKind.WARNING -> WarningSurface to WarningText
        BannerKind.ERROR -> Color(0xFFFBEBE7) to ErrorTerracotta
    }
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (kind != BannerKind.INFO) Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = fg)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
        if (actionLabel != null) OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionLabel) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = White)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

/** Empty state: one line, one optional action (depends on role). */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        actions()
    }
}

@Composable
fun IconCircle(icon: ImageVector, bg: Color = Color(0xFFE8F2E0), tint: Color = ForestGreen, size: Int = 96) {
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.45f).dp), tint = tint)
    }
}

fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

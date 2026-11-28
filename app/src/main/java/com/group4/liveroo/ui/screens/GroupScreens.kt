package com.group4.liveroo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.data.deniedReason
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskEntity
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.AvatarStack
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.CenteredState
import com.group4.liveroo.ui.components.EmptyState
import com.group4.liveroo.ui.components.FilterChipRow
import com.group4.liveroo.ui.components.FilterOption
import com.group4.liveroo.ui.components.GroupCard
import com.group4.liveroo.ui.components.GroupedCard
import com.group4.liveroo.ui.components.IconTile
import com.group4.liveroo.ui.components.ListRow
import com.group4.liveroo.ui.components.LiverooBottomSheet
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooDialog
import com.group4.liveroo.ui.components.LiverooFab
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.LiverooTextField
import com.group4.liveroo.ui.components.PendingChip
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.ProgressRing
import com.group4.liveroo.ui.components.RoleTag
import com.group4.liveroo.ui.components.RowDivider
import com.group4.liveroo.ui.components.SavedOnPhoneCard
import com.group4.liveroo.ui.components.SectionHeader
import com.group4.liveroo.ui.components.SmallTag
import com.group4.liveroo.ui.components.StatusIndicator
import com.group4.liveroo.ui.components.StatusSummary
import com.group4.liveroo.ui.components.SyncStateChip
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.components.TaskRowItem
import com.group4.liveroo.ui.components.TonalButton
import com.group4.liveroo.ui.components.userMessage
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(ms: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))

private fun changesText(n: Int) = if (n == 1) "1 change waiting to sync" else "$n changes waiting"

// ---------- S05 Groups list ----------
@Composable
fun GroupsScreen(nav: NavController) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val groups by c.repository.groups.collectAsStateWithLifecycle(emptyList())
    val tasks by c.repository.allTasks.collectAsStateWithLifecycle(emptyList())
    val members by c.repository.allMembers.collectAsStateWithLifecycle(emptyList())
    var showSheet by remember { mutableStateOf(false) }
    val colors = LiverooTheme.colors

    fun unsyncedOf(g: com.group4.liveroo.data.local.GroupEntity) =
        tasks.count { it.groupId == g.id && it.dirty } + members.count { it.groupId == g.id && it.dirty } + (if (g.dirty) 1 else 0)

    LiverooScaffold(
        title = "Your groups", nav = nav, showBack = false, tab = Tab.GROUPS,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }, Modifier.size(LiverooDimens.touchMin)) {
                Icon(Icons.Rounded.Settings, "Settings", tint = colors.textPrimary)
            }
        },
        floatingActionButton = {
            if (groups.isNotEmpty()) LiverooFab("Group", { showSheet = true })
        }
    ) {
        if (groups.isEmpty()) {
            Spacer(Modifier.weight(1f))
            CenteredState(Icons.Rounded.Groups, "No groups yet", "Create one, or ask your leader to invite you.") {
                PrimaryButton("Create a group", { nav.navigate(Routes.CREATE_GROUP) }, icon = Icons.Rounded.Add)
                TonalButton("Join a group", { nav.navigate(Routes.JOIN) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.weight(1f))
        } else {
            val totalUnsynced = groups.sumOf { unsyncedOf(it) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                if (totalUnsynced > 0) item(key = "saved") {
                    SavedOnPhoneCard(totalUnsynced, { nav.navigate(Routes.sync()) })
                }
                items(groups, key = { it.id }) { g ->
                    val gTasks = tasks.filter { it.groupId == g.id }
                    val unsynced = unsyncedOf(g)
                    GroupCard(
                        name = g.name,
                        isLeader = g.leaderId == me,
                        done = gTasks.count { it.status == TaskStatus.DONE },
                        total = gTasks.size,
                        todo = gTasks.count { it.status == TaskStatus.TODO },
                        inProgress = gTasks.count { it.status == TaskStatus.IN_PROGRESS },
                        memberNames = members.filter { it.groupId == g.id }.map { it.displayName },
                        onClick = { nav.navigate(Routes.group(g.id)) },
                        left = g.leftByMe,
                        syncText = if (g.leftByMe) "Left · not yet synced" else if (unsynced > 0) changesText(unsynced) else null
                    )
                }
            }
        }
    }

    if (showSheet) {
        LiverooBottomSheet(onDismissRequest = { showSheet = false }) {
            ListRow(
                title = "Create a group", subtitle = "You'll be the leader",
                leading = { IconTile(Icons.Rounded.Add, bg = colors.accent, tint = colors.onAccent) },
                onClick = { showSheet = false; nav.navigate(Routes.CREATE_GROUP) }
            )
            ListRow(
                title = "Join a group", subtitle = "Get invited by a nearby leader",
                leading = { IconTile(Icons.Rounded.Bluetooth, bg = colors.accent, tint = colors.onAccent) },
                onClick = { showSheet = false; nav.navigate(Routes.JOIN) }
            )
        }
    }
}

// ---------- S06 Create group ----------
@Composable
fun CreateGroupScreen(nav: NavController) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    LiverooScaffold("Create group", nav) {
        LiverooTextField(
            value = name, onValueChange = { name = it }, label = "Group name",
            maxLength = 40, helper = "Required", placeholder = "e.g. CS312 Final Project"
        )
        Banner("You'll be the group leader. You can invite members next.", icon = Icons.Rounded.WorkspacePremium)
        Spacer(Modifier.weight(1f))
        if (name.isBlank()) {
            Text(
                "Enter a group name to continue", style = LiverooTheme.type.caption, color = LiverooTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        PrimaryButton("Create", {
            scope.launch {
                val id = c.repository.createGroup(name)
                nav.navigate(Routes.group(id)) { popUpTo(Routes.GROUPS) }
            }
        }, enabled = name.isNotBlank(), modifier = Modifier.padding(bottom = LiverooDimens.space24))
    }
}

// ---------- S07 Group detail (role-aware) ----------
@Composable
fun GroupDetailScreen(nav: NavController, groupId: String) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val group by c.repository.group(groupId).collectAsStateWithLifecycle(null)
    val tasks by c.repository.tasks(groupId).collectAsStateWithLifecycle(emptyList())
    val members by c.repository.members(groupId).collectAsStateWithLifecycle(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var doneOpen by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 All, 1 To Do, 2 In progress, 3 Done
    val colors = LiverooTheme.colors

    val g = group ?: run {
        LiverooScaffold("Group", nav) { Text("This group isn't on this phone anymore.", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary) }
        return
    }
    val isLeader = g.leaderId == me
    val readOnly = g.leftByMe
    fun nameOf(id: String?) = members.firstOrNull { it.userId == id }?.let { if (it.userId == me) "${it.displayName} (you)" else it.displayName }
    val leaderName = members.firstOrNull { it.userId == g.leaderId }?.displayName ?: "Unknown"
    val todoN = tasks.count { it.status == TaskStatus.TODO }
    val progN = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val doneN = tasks.count { it.status == TaskStatus.DONE }
    val unsynced = tasks.count { it.dirty } + members.count { it.dirty } + (if (g.dirty) 1 else 0)

    LiverooScaffold(
        title = "", nav = nav, tab = Tab.GROUPS,
        actions = {
            if (!readOnly) Box {
                IconButton(onClick = { menuOpen = true }, Modifier.size(LiverooDimens.touchMin)) {
                    Icon(Icons.Rounded.MoreVert, "More options", tint = colors.textPrimary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface) {
                    DropdownMenuItem(text = { Text("Group settings", style = LiverooTheme.type.body, color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = colors.textPrimary) },
                        onClick = { menuOpen = false; nav.navigate(Routes.groupSettings(groupId)) })
                }
            }
        },
        floatingActionButton = {
            // C5 / P6: only the leader sees "+ Task"
            if (isLeader && !readOnly) LiverooFab("Task", { nav.navigate(Routes.taskForm(groupId)) })
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (readOnly) item {
                Banner(
                    "You left this group. Not yet synced. Sync once with a groupmate to finish.",
                    BannerKind.WARNING, actionLabel = "Go to Sync", onAction = { nav.navigate(Routes.sync()) }
                )
            }
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.name, style = LiverooTheme.type.titleMedium, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleTag(isLeader, left = readOnly)
                        Text("Leader: ${if (isLeader) "$leaderName (you)" else leaderName}", style = LiverooTheme.type.body, color = colors.textSecondary)
                    }
                    if (unsynced > 0) SyncStateChip(changesText(unsynced))
                    else Text("Last changed ${formatTime(g.updatedAt)}", style = LiverooTheme.type.caption, color = colors.textSecondary)
                }
            }
            item(key = "progress") {
                LiverooCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgressRing(doneN, tasks.size, size = 64.dp, stroke = 7.dp, centerText = "$doneN/${tasks.size}")
                        StatusSummary(todoN, progN, doneN)
                    }
                }
            }
            if (!readOnly) item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TonalButton("Members · ${members.size}", { nav.navigate(Routes.members(groupId)) }, Modifier.weight(1f), icon = Icons.Rounded.Groups)
                    if (isLeader) TonalButton("Invite", { nav.navigate(Routes.invite(groupId)) }, Modifier.weight(1f), icon = Icons.Rounded.PersonAdd)
                }
            }
            if (tasks.isNotEmpty()) item(key = "filters") {
                FilterChipRow(
                    listOf(
                        FilterOption("All", tasks.size), FilterOption("To Do", todoN),
                        FilterOption("In progress", progN), FilterOption("Done", doneN)
                    ),
                    selectedIndex = filter, onSelect = { filter = it }
                )
            }
            if (tasks.isEmpty() && !readOnly) item {
                if (isLeader) EmptyState("No tasks yet", "Add the first one and assign it to a member.") {
                    PrimaryButton("Add task", { nav.navigate(Routes.taskForm(groupId)) })
                } else EmptyState("No tasks yet", "Your leader hasn't added any, or you haven't synced recently.") {
                    PrimaryButton("Go to Sync", { nav.navigate(Routes.sync()) })
                }
            }
            for (status in listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE)) {
                val index = when (status) { TaskStatus.TODO -> 1; TaskStatus.IN_PROGRESS -> 2; TaskStatus.DONE -> 3 }
                if (filter != 0 && filter != index) continue
                val list = tasks.filter { it.status == status }
                if (list.isEmpty()) continue
                val label = when (status) { TaskStatus.TODO -> "To Do"; TaskStatus.IN_PROGRESS -> "In progress"; TaskStatus.DONE -> "Done" }
                item(key = "s-$status") {
                    Column {
                        if (status == TaskStatus.DONE) SectionHeader(label, list.size, expanded = doneOpen, onClick = { doneOpen = !doneOpen })
                        else SectionHeader(label, list.size)
                        if (status != TaskStatus.DONE || doneOpen) {
                            Spacer(Modifier.size(8.dp))
                            GroupedCard {
                                list.forEachIndexed { i, t ->
                                    if (i > 0) RowDivider()
                                    TaskRowItem(t.title, nameOf(t.assigneeId), t.status, t.dirty) { nav.navigate(Routes.task(t.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- S08 Members and progress ----------
@Composable
fun MembersScreen(nav: NavController, groupId: String) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val group by c.repository.group(groupId).collectAsStateWithLifecycle(null)
    val members by c.repository.members(groupId).collectAsStateWithLifecycle(emptyList())
    val tasks by c.repository.tasks(groupId).collectAsStateWithLifecycle(emptyList())
    val expanded = remember { mutableStateListOf<String>() }
    val isLeader = group?.leaderId == me
    val sorted = members.sortedWith(compareBy<MemberEntity> { it.userId != group?.leaderId }.thenBy { it.displayName })
    val colors = LiverooTheme.colors

    LiverooScaffold(
        title = "Members", nav = nav, tab = Tab.GROUPS,
        actions = {
            if (isLeader) TonalButton("Invite", { nav.navigate(Routes.invite(groupId)) }, Modifier.padding(end = 8.dp), icon = Icons.Rounded.PersonAdd)
        }
    ) {
        Text("${group?.name.orEmpty()} · ${members.size} members", style = LiverooTheme.type.caption, color = colors.textSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            item(key = "members") {
                GroupedCard {
                    sorted.forEachIndexed { idx, m ->
                        if (idx > 0) RowDivider(LiverooDimens.rowDividerInsetLeading)
                        val mine = tasks.filter { it.assigneeId == m.userId }
                        val open = m.userId in expanded
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = LiverooDimens.rowMinWithLeading)
                                .clickable(role = Role.Button, onClickLabel = if (open) "Hide tasks" else "Show tasks") { if (open) expanded.remove(m.userId) else expanded.add(m.userId) }
                                .semantics(mergeDescendants = true) { stateDescription = if (open) "Expanded" else "Collapsed" }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Avatar(m.displayName)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.displayName, style = LiverooTheme.type.itemTitle, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
                                    if (m.userId == group?.leaderId) RoleTag(true)
                                    if (m.userId == me) SmallTag("You")
                                }
                                Text(countsLine(mine), style = LiverooTheme.type.caption, color = colors.textSecondary)
                                if (m.dirty) PendingChip()
                            }
                            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(22.dp).rotate(if (open) 180f else 0f), tint = colors.textSecondary)
                        }
                        if (open) {
                            if (mine.isEmpty()) Text(
                                "No tasks assigned.", style = LiverooTheme.type.body, color = colors.textSecondary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            mine.forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                                        .background(colors.surfaceTile, com.group4.liveroo.ui.theme.LiverooRadius.small)
                                        .heightIn(min = LiverooDimens.touchMin)
                                        .clickable(role = Role.Button) { nav.navigate(Routes.task(t.id)) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(t.title, style = LiverooTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                    StatusIndicator(t.status)
                                }
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                }
            }
            item(key = "unassigned") {
                val n = tasks.count { it.assigneeId == null }
                GroupedCard {
                    ListRow(title = "Unassigned", subtitle = "$n ${if (n == 1) "task has" else "tasks have"} no assignee")
                }
            }
        }
    }
}

private fun countsLine(tasks: List<TaskEntity>) =
    "${tasks.count { it.status == TaskStatus.TODO }} To Do · ${tasks.count { it.status == TaskStatus.IN_PROGRESS }} In Progress · ${tasks.count { it.status == TaskStatus.DONE }} Done"

// ---------- S12 Group settings ----------
@Composable
fun GroupSettingsScreen(nav: NavController, groupId: String) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val group by c.repository.group(groupId).collectAsStateWithLifecycle(null)
    val members by c.repository.members(groupId).collectAsStateWithLifecycle(emptyList())
    var dialog by remember { mutableStateOf<String?>(null) } // "leader", "member" or "delete"
    var error by remember { mutableStateOf<String?>(null) }
    val isLeader = group?.leaderId == me
    // N5: a leader with no other active member can't transfer leadership, so they may delete the group from this phone.
    val soleMember = isLeader && members.isNotEmpty() && members.all { it.userId == me }
    val colors = LiverooTheme.colors

    LiverooScaffold("Group settings", nav) {
        error?.let { Banner(it, BannerKind.ERROR) }
        GroupedCard {
            ListRow(title = "Group name", subtitle = group?.name.orEmpty())
            RowDivider()
            ListRow(title = "Your role", trailing = { RoleTag(isLeader) })
        }
        GroupedCard {
            if (isLeader) {
                val canTransfer = members.size > 1
                ListRow(
                    title = "Transfer leadership",
                    subtitle = if (!canTransfer) "Invite a member first." else null,
                    leading = { IconTile(Icons.Rounded.WorkspacePremium, bg = colors.accentContainer, tint = colors.accent) },
                    chevron = canTransfer, enabled = canTransfer,
                    onClick = { nav.navigate(Routes.transfer(groupId)) }
                )
                RowDivider(LiverooDimens.rowDividerInsetLeading)
            }
            ListRow(
                title = if (soleMember) "Delete group from this phone" else "Leave group",
                destructive = true,
                leading = { IconTile(Icons.AutoMirrored.Rounded.Logout, bg = colors.errorSurface, tint = colors.error) },
                onClick = {
                    dialog = when {
                        soleMember -> "delete"
                        isLeader -> "leader"
                        else -> "member"
                    }
                }
            )
        }
        if (isLeader && !soleMember) {
            Text(
                "As the leader, you'll be asked to transfer leadership before you can leave.",
                style = LiverooTheme.type.caption, color = colors.textSecondary
            )
        }
    }

    when (dialog) {
        // P13: the leader must transfer first
        "leader" -> LiverooDialog(
            onDismissRequest = { dialog = null },
            title = "Transfer leadership first",
            body = "A group needs a leader before you can leave.",
            icon = Icons.Rounded.WorkspacePremium,
            confirmText = "Transfer leadership",
            confirmEnabled = members.size > 1,
            onConfirm = { dialog = null; nav.navigate(Routes.transfer(groupId)) },
            dismissText = "Cancel"
        )
        // C21 + P12
        "member" -> LiverooDialog(
            onDismissRequest = { dialog = null },
            title = "Leave ${group?.name.orEmpty()}?",
            body = "Your tasks will become unassigned once this syncs. The group stays read-only on this phone until you sync with a groupmate, then it's removed.\n\nAnything you haven't synced will be lost.",
            destructive = true,
            confirmText = "Leave",
            onConfirm = {
                dialog = null
                scope.launch {
                    val denied = c.repository.leaveGroup(groupId).deniedReason()
                    if (denied == null) nav.popBackStack() else error = denied.userMessage()
                }
            },
            dismissText = "Cancel"
        )
        // N5: local only. Other phones that have this group keep their copy.
        "delete" -> LiverooDialog(
            onDismissRequest = { dialog = null },
            title = "Delete \"${group?.name.orEmpty()}\" from this phone?",
            body = "This removes the group, its members and its tasks from this phone only. " +
                "Other phones that have this group are not affected and keep their copy. " +
                "Anything not yet synced is lost.",
            destructive = true,
            confirmText = "Delete from this phone",
            onConfirm = {
                dialog = null
                scope.launch {
                    val denied = c.repository.deleteGroupLocally(groupId).deniedReason()
                    if (denied == null) nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } }
                    else error = denied.userMessage()
                }
            },
            dismissText = "Cancel"
        )
    }
}

// ---------- S11 Transfer leadership ----------
@Composable
fun TransferScreen(nav: NavController, groupId: String) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val group by c.repository.group(groupId).collectAsStateWithLifecycle(null)
    val members by c.repository.members(groupId).collectAsStateWithLifecycle(emptyList())
    val candidates = members.filter { it.userId != me }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.userId == selected }?.displayName.orEmpty()
    val colors = LiverooTheme.colors

    LiverooScaffold("Transfer leadership", nav) {
        Text("Choose who will lead ${group?.name.orEmpty()}.", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary)
        GroupedCard {
            candidates.forEachIndexed { i, m ->
                if (i > 0) RowDivider(LiverooDimens.rowDividerInsetLeading)
                Box(Modifier.selectable(selected = selected == m.userId, role = Role.RadioButton) { selected = m.userId }) {
                    ListRow(
                        title = m.displayName,
                        leading = { Avatar(m.displayName) },
                        trailing = {
                            RadioButton(
                                selected = selected == m.userId, onClick = null, modifier = Modifier.size(22.dp),
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.outlineControl)
                            )
                        }
                    )
                }
            }
        }
        Banner("The new leader takes over when your phones sync. If the sync fails, you stay the leader.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", { confirm = true }, enabled = selected != null, modifier = Modifier.padding(bottom = LiverooDimens.space24))
    }

    if (confirm) LiverooDialog(
        onDismissRequest = { confirm = false },
        title = "Make $selectedName the leader?",
        body = "This happens when your phones sync. You'll become a regular member and won't be able to invite members or create, edit or delete tasks.",
        icon = Icons.Rounded.WorkspacePremium,
        confirmText = "Transfer",
        onConfirm = {
            confirm = false
            selected?.let { nav.navigate(Routes.transferSync(groupId, it)) }
        },
        dismissText = "Cancel"
    )
}

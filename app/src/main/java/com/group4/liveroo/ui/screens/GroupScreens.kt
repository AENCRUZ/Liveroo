package com.group4.liveroo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.data.deniedReason
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskEntity
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.EmptyState
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.NotYetSynced
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.RoleTag
import com.group4.liveroo.ui.components.SectionHeader
import com.group4.liveroo.ui.components.SmallTag
import com.group4.liveroo.ui.components.StatusChip
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.components.TaskRow
import com.group4.liveroo.ui.components.userMessage
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.ErrorTerracotta
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.Ink
import com.group4.liveroo.ui.theme.SecondaryText
import com.group4.liveroo.ui.theme.White
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(ms: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))

// ---------- S05 Groups list ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(nav: NavController) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val groups by c.repository.groups.collectAsStateWithLifecycle(emptyList())
    val tasks by c.repository.allTasks.collectAsStateWithLifecycle(emptyList())
    val members by c.repository.allMembers.collectAsStateWithLifecycle(emptyList())
    var showSheet by remember { mutableStateOf(false) }

    LiverooScaffold(
        title = "Your groups", nav = nav, showBack = false, tab = Tab.GROUPS,
        actions = { IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") } },
        floatingActionButton = {
            if (groups.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { showSheet = true }, containerColor = ForestGreen, contentColor = White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Group") }
            )
        }
    ) {
        if (groups.isEmpty()) {
            Spacer(Modifier.weight(1f))
            EmptyState("No groups yet", "Create one, or ask your leader to invite you.") {
                PrimaryButton("Create a group", { nav.navigate(Routes.CREATE_GROUP) }, icon = Icons.Filled.Add)
                OutlinedButton(onClick = { nav.navigate(Routes.JOIN) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Join a group")
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(groups, key = { it.id }) { g ->
                    val gTasks = tasks.filter { it.groupId == g.id }
                    val unsynced = gTasks.count { it.dirty } + members.count { it.groupId == g.id && it.dirty } + (if (g.dirty) 1 else 0)
                    LiverooCard(onClick = { nav.navigate(Routes.group(g.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // C23: group names on cards in Comfortaa Bold 18sp (titleMedium)
                            Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.leftByMe) SmallTag("Left") else RoleTag(g.leaderId == me)
                        }
                        if (g.leftByMe) {
                            Text("Read-only until your departure syncs", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                            NotYetSynced("Left · Not yet synced")
                        } else {
                            Text("${gTasks.count { it.status == TaskStatus.DONE }} of ${gTasks.size} tasks done", style = MaterialTheme.typography.bodyMedium)
                            if (unsynced > 0) NotYetSynced("Not yet synced ($unsynced)")
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = White) {
            ListItem(
                headlineContent = { Text("Create a group") }, supportingContent = { Text("You'll be the leader") },
                leadingContent = { Icon(Icons.Filled.Add, null) },
                modifier = Modifier.clickable { showSheet = false; nav.navigate(Routes.CREATE_GROUP) },
                colors = ListItemDefaults.colors(containerColor = White)
            )
            ListItem(
                headlineContent = { Text("Join a group") }, supportingContent = { Text("Get invited by a nearby leader") },
                leadingContent = { Icon(Icons.Filled.Bluetooth, null) },
                modifier = Modifier.clickable { showSheet = false; nav.navigate(Routes.JOIN) },
                colors = ListItemDefaults.colors(containerColor = White)
            )
            Spacer(Modifier.height(24.dp))
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
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 40) name = it },
            label = { Text("Group name") }, singleLine = true,
            supportingText = { Text("Required  ${name.length}/40") },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        )
        Banner("You'll be the group leader. You can invite members next.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Create", {
            scope.launch {
                val id = c.repository.createGroup(name)
                nav.navigate(Routes.group(id)) { popUpTo(Routes.GROUPS) }
            }
        }, enabled = name.isNotBlank(), modifier = Modifier.padding(bottom = 16.dp))
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
    var doneOpen by rememberSaveable { mutableStateOf(false) }

    val g = group ?: run {
        LiverooScaffold("Group", nav) { Text("This group isn't on this phone anymore.", style = MaterialTheme.typography.bodyLarge) }
        return
    }
    val isLeader = g.leaderId == me
    val readOnly = g.leftByMe
    fun nameOf(id: String?) = members.firstOrNull { it.userId == id }?.let { if (it.userId == me) "${it.displayName} (you)" else it.displayName }
    val leaderName = members.firstOrNull { it.userId == g.leaderId }?.displayName ?: "Unknown"

    LiverooScaffold(
        title = g.name, nav = nav, tab = Tab.GROUPS,
        actions = {
            if (!readOnly) Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Members") }, leadingIcon = { Icon(Icons.Filled.Groups, null) },
                        onClick = { menuOpen = false; nav.navigate(Routes.members(groupId)) })
                    if (isLeader) DropdownMenuItem(text = { Text("Invite members") }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                        onClick = { menuOpen = false; nav.navigate(Routes.invite(groupId)) })
                    DropdownMenuItem(text = { Text("Group settings") }, leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { menuOpen = false; nav.navigate(Routes.groupSettings(groupId)) })
                }
            }
        },
        floatingActionButton = {
            // C5 / P6: only the leader sees "+ Task"
            if (isLeader && !readOnly) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.taskForm(groupId)) }, containerColor = ForestGreen, contentColor = White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Task") }
            )
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (readOnly) item {
                Banner(
                    "You left this group. Not yet synced. Sync once with a groupmate to finish.",
                    BannerKind.WARNING, actionLabel = "Go to Sync", onAction = { nav.navigate(Routes.sync()) }
                )
            }
            item {
                LiverooCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Leader: ${if (isLeader) "$leaderName (you)" else leaderName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (!readOnly) RoleTag(isLeader)
                    }
                    Text(
                        if (g.dirty || tasks.any { it.dirty }) "Some changes are not yet synced" else "Last changed ${formatTime(g.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall, color = SecondaryText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(TaskStatus.TODO, "${tasks.count { it.status == TaskStatus.TODO }} ")
                        StatusChip(TaskStatus.IN_PROGRESS, "${tasks.count { it.status == TaskStatus.IN_PROGRESS }} ")
                        StatusChip(TaskStatus.DONE, "${tasks.count { it.status == TaskStatus.DONE }} ")
                    }
                }
            }
            if (tasks.isEmpty()) item {
                if (isLeader) EmptyState("No tasks yet", "Add the first one and assign it to a member.") {
                    PrimaryButton("Add task", { nav.navigate(Routes.taskForm(groupId)) })
                } else EmptyState("No tasks yet", "Your leader hasn't added any, or you haven't synced recently.") {
                    PrimaryButton("Go to Sync", { nav.navigate(Routes.sync()) })
                }
            }
            for (status in listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE)) {
                val list = tasks.filter { it.status == status }
                if (list.isEmpty()) continue
                item(key = "h-$status") {
                    if (status == TaskStatus.DONE) {
                        Box(Modifier.clickable { doneOpen = !doneOpen }) {
                            SectionHeader("Done ${if (doneOpen) "▾" else "▸"}", list.size)
                        }
                    } else SectionHeader(if (status == TaskStatus.TODO) "To Do" else "In Progress", list.size)
                }
                if (status != TaskStatus.DONE || doneOpen) {
                    items(list, key = { it.id }) { t ->
                        TaskRow(t.title, nameOf(t.assigneeId), t.status, t.dirty) { nav.navigate(Routes.task(t.id)) }
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

    LiverooScaffold(
        title = "Members", nav = nav, tab = Tab.GROUPS,
        actions = { if (isLeader) TextButton(onClick = { nav.navigate(Routes.invite(groupId)) }) { Icon(Icons.Filled.PersonAdd, null); Text("  Invite", color = Ink) } }
    ) {
        Text("${group?.name.orEmpty()} · ${members.size} members", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(sorted, key = { it.userId }) { m ->
                val mine = tasks.filter { it.assigneeId == m.userId }
                val open = m.userId in expanded
                LiverooCard(onClick = { if (open) expanded.remove(m.userId) else expanded.add(m.userId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(m.displayName)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                                if (m.userId == group?.leaderId) RoleTag(true)
                                if (m.userId == me) SmallTag("You")
                            }
                            Text(countsLine(mine), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            if (m.dirty) NotYetSynced()
                        }
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                    if (open) {
                        HorizontalDivider()
                        if (mine.isEmpty()) Text("No tasks assigned.", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                        mine.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.task(t.id)) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                StatusChip(t.status)
                            }
                        }
                    }
                }
            }
            item {
                val n = tasks.count { it.assigneeId == null }
                LiverooCard {
                    Text("Unassigned", style = MaterialTheme.typography.titleSmall)
                    Text("$n ${if (n == 1) "task has" else "tasks have"} no assignee", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
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

    LiverooScaffold("Group settings", nav) {
        error?.let { Banner(it, BannerKind.ERROR) }
        LiverooCard {
            Text("GROUP NAME", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Text(group?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR ROLE", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.weight(1f))
                RoleTag(isLeader)
            }
        }
        LiverooCard {
            if (isLeader) {
                val canTransfer = members.size > 1
                ListItem(
                    headlineContent = { Text("Transfer leadership") },
                    supportingContent = { if (!canTransfer) Text("Invite a member first.") },
                    leadingContent = { Icon(Icons.Filled.WorkspacePremium, null) },
                    modifier = Modifier.clickable(enabled = canTransfer) { nav.navigate(Routes.transfer(groupId)) },
                    colors = ListItemDefaults.colors(containerColor = White)
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text(if (soleMember) "Delete group from this phone" else "Leave group", color = ErrorTerracotta) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = ErrorTerracotta) },
                modifier = Modifier.clickable {
                    dialog = when {
                        soleMember -> "delete"
                        isLeader -> "leader"
                        else -> "member"
                    }
                },
                colors = ListItemDefaults.colors(containerColor = White)
            )
        }
    }

    when (dialog) {
        // P13: the leader must transfer first
        "leader" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Transfer leadership first", style = MaterialTheme.typography.titleLarge) },
            text = { Text("A group needs a leader before you can leave.") },
            confirmButton = { TextButton(onClick = { dialog = null; nav.navigate(Routes.transfer(groupId)) }, enabled = members.size > 1) { Text("Transfer leadership") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = Ink) } }
        )
        // C21 + P12
        "member" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Leave ${group?.name.orEmpty()}?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Your tasks will become unassigned once this syncs. The group stays read-only on this phone until you sync with a groupmate, then it's removed.\n\nAnything you haven't synced will be lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        val denied = c.repository.leaveGroup(groupId).deniedReason()
                        if (denied == null) nav.popBackStack() else error = denied.userMessage()
                    }
                }) { Text("Leave", color = ErrorTerracotta) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = Ink) } }
        )
        // N5: local only. Other phones that have this group keep their copy.
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete \"${group?.name.orEmpty()}\" from this phone?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "This removes the group, its members and its tasks from this phone only. " +
                        "Other phones that have this group are not affected and keep their copy. " +
                        "Anything not yet synced is lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        val denied = c.repository.deleteGroupLocally(groupId).deniedReason()
                        if (denied == null) nav.navigate(Routes.GROUPS) { popUpTo(Routes.GROUPS) { inclusive = true } }
                        else error = denied.userMessage()
                    }
                }) { Text("Delete from this phone", color = ErrorTerracotta) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = Ink) } }
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

    LiverooScaffold("Transfer leadership", nav) {
        Text("Choose who will lead ${group?.name.orEmpty()}.", style = MaterialTheme.typography.bodyLarge)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            candidates.forEach { m ->
                LiverooCard(modifier = Modifier.selectable(selected = selected == m.userId, role = Role.RadioButton) { selected = m.userId }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(m.displayName)
                        Text(m.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        RadioButton(selected = selected == m.userId, onClick = null)
                    }
                }
            }
        }
        Banner("The new leader takes over when your phones sync. If the sync fails, you stay the leader.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", { confirm = true }, enabled = selected != null, modifier = Modifier.padding(bottom = 16.dp))
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Make $selectedName the leader?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("This happens when your phones sync. You'll become a regular member and won't be able to invite members or create, edit or delete tasks.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                selected?.let { nav.navigate(Routes.transferSync(groupId, it)) }
            }) { Text("Transfer") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Ink) } }
    )
}

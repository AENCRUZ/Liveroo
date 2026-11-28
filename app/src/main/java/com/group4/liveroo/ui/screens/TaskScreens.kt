package com.group4.liveroo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.data.deniedReason
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.EmptyState
import com.group4.liveroo.ui.components.GroupedCard
import com.group4.liveroo.ui.components.LiverooDialog
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.LiverooSelectField
import com.group4.liveroo.ui.components.LiverooTextField
import com.group4.liveroo.ui.components.PendingChip
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.RowDivider
import com.group4.liveroo.ui.components.SectionHeader
import com.group4.liveroo.ui.components.StatusIndicator
import com.group4.liveroo.ui.components.StatusSelector
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.components.TaskRowItem
import com.group4.liveroo.ui.components.statusLabel
import com.group4.liveroo.ui.components.userMessage
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- S13 Task detail (three role variants) ----------
@Composable
fun TaskDetailScreen(nav: NavController, taskId: String) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val task by c.repository.task(taskId).collectAsStateWithLifecycle(null)
    val t = task
    val groupFlow = remember(t?.groupId) { c.repository.group(t?.groupId.orEmpty()) }
    val membersFlow = remember(t?.groupId) { c.repository.members(t?.groupId.orEmpty()) }
    val group by groupFlow.collectAsStateWithLifecycle(null)
    val members by membersFlow.collectAsStateWithLifecycle(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val colors = LiverooTheme.colors

    if (t == null || t.deleted) {
        LiverooScaffold("Task", nav) { Text("This task was deleted.", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary) }
        return
    }
    val isLeader = group?.leaderId == me
    val readOnlyGroup = group?.leftByMe == true
    val isMine = t.assigneeId == me
    val canChangeStatus = !readOnlyGroup && (isLeader || isMine)   // C8, C10
    val assignee = members.firstOrNull { it.userId == t.assigneeId }
    val updater = members.firstOrNull { it.userId == t.updatedBy }?.displayName ?: "someone"

    LiverooScaffold(
        title = "", nav = nav, tab = Tab.GROUPS, snackbarHost = snackbar,
        actions = {
            if (isLeader && !readOnlyGroup) {
                IconButton(onClick = { nav.navigate(Routes.taskForm(t.groupId, t.id)) }, Modifier.size(LiverooDimens.touchMin)) {
                    Icon(Icons.Rounded.Edit, "Edit task", tint = colors.textPrimary)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, Modifier.size(LiverooDimens.touchMin)) {
                        Icon(Icons.Rounded.MoreVert, "More options", tint = colors.textPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface) {
                        DropdownMenuItem(
                            text = { Text("Delete task", style = LiverooTheme.type.body, color = colors.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.error) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(group?.name.orEmpty(), style = LiverooTheme.type.caption, color = colors.textSecondary)
            Text(t.title, style = LiverooTheme.type.titleMedium, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = LiverooTheme.type.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
                if (t.dirty) PendingChip()
            }
            if (canChangeStatus) {
                StatusSelector(t.status, onSelect = { s ->
                    scope.launch {
                        val denied = c.repository.setStatus(t.id, s).deniedReason()
                        snackbar.showSnackbar(denied?.userMessage() ?: "Status updated. Sync to share it.")
                    }
                })
            } else {
                StatusIndicator(t.status)
                Banner(
                    if (assignee != null) "Only ${assignee.displayName} or the leader can change this status."
                    else "Only the leader can change this status.",
                    icon = Icons.Rounded.Lock
                )
            }
            GroupedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Assignee", style = LiverooTheme.type.label, color = colors.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (assignee != null) {
                            Avatar(assignee.displayName)
                            Text(if (isMine) "${assignee.displayName} (you)" else assignee.displayName, style = LiverooTheme.type.itemTitle, color = colors.textPrimary)
                        } else Text("Unassigned", style = LiverooTheme.type.itemTitle, color = colors.textPrimary)
                    }
                }
                RowDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Description", style = LiverooTheme.type.label, color = colors.textSecondary)
                    Text(t.description.ifBlank { "No description." }, style = LiverooTheme.type.bodyLarge, color = colors.textPrimary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = colors.textSecondary)
                Text(
                    "Last updated by ${if (t.updatedBy == me) "you" else updater} · " +
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(t.updatedAt)),
                    style = LiverooTheme.type.caption, color = colors.textSecondary
                )
            }
        }
    }

    if (confirmDelete) LiverooDialog(
        onDismissRequest = { confirmDelete = false },
        title = "Delete this task?",
        body = "“${t.title}” will be removed for everyone after they sync.",
        destructive = true,
        confirmText = "Delete",
        onConfirm = {
            confirmDelete = false
            scope.launch {
                val denied = c.repository.deleteTask(t.id).deniedReason()
                if (denied == null) nav.popBackStack() else snackbar.showSnackbar(denied.userMessage())
            }
        },
        dismissText = "Cancel"
    )
}

// ---------- S14 Create / edit task (leader only) ----------
@Composable
fun TaskFormScreen(nav: NavController, groupId: String, taskId: String?) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    // null until the first load, so a list that is still loading isn't mistaken for "no members" (D3 check below).
    val loadedMembers by c.repository.members(groupId).collectAsStateWithLifecycle(initialValue = null as List<MemberEntity>?)
    val members = loadedMembers.orEmpty()
    var assigneeCleared by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf<DenyReason?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var assigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf(TaskStatus.TODO) }
    var loaded by rememberSaveable { mutableStateOf(taskId == null) }
    var showError by remember { mutableStateOf(false) }
    val colors = LiverooTheme.colors

    // Load the existing task once when editing.
    if (taskId != null) {
        val existing by c.repository.task(taskId).collectAsStateWithLifecycle(null)
        LaunchedEffect(existing) {
            val e = existing
            if (!loaded && e != null) {
                title = e.title; description = e.description; assigneeId = e.assigneeId; status = e.status; loaded = true
            }
        }
    }
    // D3: a saved assignee who is no longer an active member is cleared, so the task is never saved with one.
    LaunchedEffect(loadedMembers, assigneeId, loaded) {
        val list = loadedMembers ?: return@LaunchedEffect
        if (loaded && assigneeId != null && list.none { it.userId == assigneeId }) {
            assigneeId = null
            assigneeCleared = true
        }
    }
    val assigneeMember = members.firstOrNull { it.userId == assigneeId }
    val assigneeLabel = assigneeMember?.let { if (it.userId == me) "${it.displayName} (you)" else it.displayName } ?: "Unassigned"

    LiverooScaffold(if (taskId == null) "New task" else "Edit task", nav) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (assigneeCleared) {
                Banner("The previous assignee is no longer an active member, so this task is now unassigned.", BannerKind.WARNING)
            }
            denied?.let { Banner(it.userMessage(), BannerKind.ERROR) }
            LiverooTextField(
                value = title, onValueChange = { title = it; showError = false }, label = "Title",
                maxLength = 60, helper = "Required", error = if (showError) "Add a title." else null
            )
            LiverooTextField(
                value = description, onValueChange = { description = it }, label = "Description",
                maxLength = 500, helper = "Optional", placeholder = "Optional details", multiline = true
            )
            LiverooSelectField(value = assigneeLabel, label = "Assignee", leadingName = assigneeMember?.displayName) { close ->
                DropdownMenuItem(
                    text = { Text("Unassigned", style = LiverooTheme.type.bodyLarge, color = colors.textPrimary) },
                    onClick = { assigneeId = null; close() }
                )
                members.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(if (m.userId == me) "${m.displayName} (you)" else m.displayName, style = LiverooTheme.type.bodyLarge, color = colors.textPrimary) },
                        leadingIcon = { Avatar(m.displayName, 28) },
                        onClick = { assigneeId = m.userId; close() }
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status", style = LiverooTheme.type.label, color = colors.textSecondary)
                StatusSelector(status, onSelect = { status = it })
            }
        }
        PrimaryButton("Save", {
            if (title.isBlank()) showError = true else scope.launch {
                val reason = c.repository.saveTask(groupId, taskId, title, description, assigneeId, status).deniedReason()
                if (reason == null) nav.popBackStack() else denied = reason
            }
        }, modifier = Modifier.padding(bottom = LiverooDimens.space24))
    }
}

// ---------- S18 My Tasks ----------
@Composable
fun MyTasksScreen(nav: NavController) {
    val c = LocalContainer.current
    val me by c.identity.userId.collectAsStateWithLifecycle("")
    val tasksFlow = remember(me) { c.repository.tasksAssignedTo(me) }
    val tasks by tasksFlow.collectAsStateWithLifecycle(emptyList())
    val groups by c.repository.groups.collectAsStateWithLifecycle(emptyList())
    val names = groups.associate { it.id to it.name }
    val colors = LiverooTheme.colors

    LiverooScaffold("My Tasks", nav, showBack = false, tab = Tab.MY_TASKS) {
        Text("Assigned to you in all your groups", style = LiverooTheme.type.caption, color = colors.textSecondary)
        if (tasks.isEmpty()) {
            Spacer(Modifier.weight(1f))
            EmptyState("Nothing assigned to you yet", "Tasks your leaders assign to you will show here.", icon = Icons.Rounded.TaskAlt)
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                for (s in listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE)) {
                    val list = tasks.filter { it.status == s }
                    if (list.isEmpty()) continue
                    item(key = "s-$s") {
                        Column {
                            SectionHeader(if (s == TaskStatus.IN_PROGRESS) "In progress" else statusLabel(s), list.size)
                            Spacer(Modifier.size(8.dp))
                            GroupedCard {
                                list.forEachIndexed { i, t ->
                                    if (i > 0) RowDivider()
                                    TaskRowItem(t.title, "You", t.status, t.dirty, groupName = names[t.groupId]) { nav.navigate(Routes.task(t.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

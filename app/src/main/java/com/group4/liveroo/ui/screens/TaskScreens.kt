package com.group4.liveroo.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.NotYetSynced
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.SectionHeader
import com.group4.liveroo.ui.components.StatusChip
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.components.TaskRow
import com.group4.liveroo.ui.components.userMessage
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.Border
import com.group4.liveroo.ui.theme.ErrorTerracotta
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.Ink
import com.group4.liveroo.ui.theme.SecondaryText
import com.group4.liveroo.ui.theme.SelectedTint
import com.group4.liveroo.ui.theme.White
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Three-way status selector (C11). */
@Composable
fun StatusSelector(current: TaskStatus, onSelect: (TaskStatus) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(TaskStatus.TODO, "To Do", Icons.Outlined.Circle),
            Triple(TaskStatus.IN_PROGRESS, "In Progress", Icons.Outlined.Contrast),
            Triple(TaskStatus.DONE, "Done", Icons.Filled.Check)
        ).forEach { (s, label, icon) -> StatusOption(label, icon, s == current, Modifier.weight(1f)) { onSelect(s) } }
    }
}

@Composable
private fun StatusOption(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) ForestGreen else Border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SelectedTint else White, contentColor = Ink
        ),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

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

    if (t == null || t.deleted) {
        LiverooScaffold("Task", nav) { Text("This task was deleted.", style = MaterialTheme.typography.bodyLarge) }
        return
    }
    val isLeader = group?.leaderId == me
    val readOnlyGroup = group?.leftByMe == true
    val isMine = t.assigneeId == me
    val canChangeStatus = !readOnlyGroup && (isLeader || isMine)   // C8, C10
    val assignee = members.firstOrNull { it.userId == t.assigneeId }
    val updater = members.firstOrNull { it.userId == t.updatedBy }?.displayName ?: "someone"

    LiverooScaffold(
        title = "Task", nav = nav, tab = Tab.GROUPS,
        actions = {
            if (isLeader && !readOnlyGroup) {
                IconButton(onClick = { nav.navigate(Routes.taskForm(t.groupId, t.id)) }) { Icon(Icons.Filled.Edit, "Edit task") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete task", color = ErrorTerracotta) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = ErrorTerracotta) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(group?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            Text(t.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STATUS", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.weight(1f))
                if (t.dirty) NotYetSynced()
            }
            if (canChangeStatus) {
                StatusSelector(t.status) { s ->
                    scope.launch {
                        val denied = c.repository.setStatus(t.id, s).deniedReason()
                        snackbar.showSnackbar(denied?.userMessage() ?: "Status updated. Sync to share it.")
                    }
                }
            } else {
                StatusChip(t.status)
                Banner(
                    if (assignee != null) "Only ${assignee.displayName} or the leader can change this status."
                    else "Only the leader can change this status."
                )
            }
            LiverooCard {
                Text("ASSIGNEE", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (assignee != null) {
                        Avatar(assignee.displayName)
                        Text(if (isMine) "${assignee.displayName} (you)" else assignee.displayName, style = MaterialTheme.typography.titleSmall)
                    } else Text("Unassigned", style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()
                Text("DESCRIPTION", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Text(t.description.ifBlank { "No description." }, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Last updated by ${if (t.updatedBy == me) "you" else updater} · " +
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(t.updatedAt)),
                style = MaterialTheme.typography.bodySmall, color = SecondaryText
            )
            SnackbarHost(snackbar)
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this task?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("“${t.title}” will be removed for everyone after they sync.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    val denied = c.repository.deleteTask(t.id).deniedReason()
                    if (denied == null) nav.popBackStack() else snackbar.showSnackbar(denied.userMessage())
                }
            }) { Text("Delete", color = ErrorTerracotta) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Ink) } }
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
    var pickerOpen by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

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
    val assigneeLabel = members.firstOrNull { it.userId == assigneeId }?.let { if (it.userId == me) "${it.displayName} (you)" else it.displayName } ?: "Unassigned"

    LiverooScaffold(if (taskId == null) "New task" else "Edit task", nav) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (assigneeCleared) {
                Banner("The previous assignee is no longer an active member, so this task is now unassigned.", BannerKind.WARNING)
            }
            denied?.let { Banner(it.userMessage(), BannerKind.ERROR) }
            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 60) { title = it; showError = false } },
                label = { Text("Title") }, singleLine = true, isError = showError,
                supportingText = { Text(if (showError) "Add a title." else "Required  ${title.length}/60") },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description, onValueChange = { if (it.length <= 500) description = it },
                label = { Text("Description") }, minLines = 4,
                supportingText = { Text("Optional  ${description.length}/500") },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Assignee", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(assigneeLabel, color = Ink, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Ink)
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        DropdownMenuItem(text = { Text("Unassigned") }, onClick = { assigneeId = null; pickerOpen = false })
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(if (m.userId == me) "${m.displayName} (you)" else m.displayName) },
                                onClick = { assigneeId = m.userId; pickerOpen = false }
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status", style = MaterialTheme.typography.labelMedium)
                StatusSelector(status) { status = it }
            }
        }
        PrimaryButton("Save", {
            if (title.isBlank()) showError = true else scope.launch {
                val reason = c.repository.saveTask(groupId, taskId, title, description, assigneeId, status).deniedReason()
                if (reason == null) nav.popBackStack() else denied = reason
            }
        }, modifier = Modifier.padding(bottom = 16.dp))
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

    LiverooScaffold("My Tasks", nav, showBack = false, tab = Tab.MY_TASKS) {
        Text("Assigned to you in all your groups", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        if (tasks.isEmpty()) {
            Spacer(Modifier.weight(1f))
            com.group4.liveroo.ui.components.EmptyState("Nothing assigned to you yet", "Tasks your leaders assign to you will show here.")
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                for (s in listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE)) {
                    val list = tasks.filter { it.status == s }
                    if (list.isEmpty()) continue
                    item(key = "h-$s") { SectionHeader(com.group4.liveroo.ui.components.statusLabel(s), list.size) }
                    items(list, key = { it.id }) { t ->
                        TaskRow(t.title, "You", t.status, t.dirty, groupName = names[t.groupId]) { nav.navigate(Routes.task(t.id)) }
                    }
                }
            }
        }
    }
}

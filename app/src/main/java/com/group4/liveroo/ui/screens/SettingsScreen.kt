package com.group4.liveroo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.bluetooth.BtPermissions
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.theme.ErrorTerracotta
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.Ink
import com.group4.liveroo.ui.theme.SecondaryText
import kotlinx.coroutines.launch

// ---------- S17 Settings ----------
@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name by c.identity.displayName.collectAsStateWithLifecycle("")
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LiverooScaffold("Settings", nav) {
        LiverooCard {
            Text("YOUR NAME", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(name)
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { draft = name; editing = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Edit, null); Text(" Edit", color = Ink)
                }
            }
            // P10: TODO send the new name in the next sync (update this user's MemberEntity rows and mark them dirty).
            Text("A new name reaches groupmates on your next sync.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
        LiverooCard {
            Text("BLUETOOTH", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            StatusLine("Bluetooth", if (c.bluetooth.isEnabled) "On" else "Off", c.bluetooth.isEnabled)
            val granted = BtPermissions.granted(context)
            StatusLine("Nearby devices permission", if (granted) "Allowed" else "Not allowed", granted)
        }
        // P12: permanent data note
        Banner("Liveroo saves everything on this phone only. There is no cloud backup. Sync often so your groupmates have a copy.")
        LiverooCard {
            Text("ABOUT", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Text("Liveroo 1.0", style = MaterialTheme.typography.bodyLarge)
            Text("CS312 Group 4 · BatStateU Alangilan", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
    }

    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Your name", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = draft, onValueChange = { if (it.length <= 30) draft = it },
                singleLine = true, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = draft.isNotBlank(), onClick = {
                editing = false
                scope.launch { c.identity.setName(draft) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel", color = Ink) } }
    )
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(if (ok) Icons.Filled.Check else Icons.Filled.Close, null, tint = if (ok) ForestGreen else ErrorTerracotta)
        Text(" $value", style = MaterialTheme.typography.labelMedium)
    }
}

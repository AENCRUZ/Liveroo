package com.group4.liveroo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.bluetooth.BtPermissions
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.GroupedCard
import com.group4.liveroo.ui.components.IconTile
import com.group4.liveroo.ui.components.ListRow
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.LiverooDialog
import com.group4.liveroo.ui.components.LiverooScaffold
import com.group4.liveroo.ui.components.LiverooTextField
import com.group4.liveroo.ui.components.RowDivider
import com.group4.liveroo.ui.components.TonalButton
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooTheme
import com.group4.liveroo.ui.theme.ThemePreference
import com.group4.liveroo.ui.theme.rememberDarkMode
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
    val colors = LiverooTheme.colors

    LiverooScaffold("Settings", nav) {
        // Profile card
        LiverooCard {
            Text("Your name", style = LiverooTheme.type.label, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(name, size = 64)
                Text(name, style = LiverooTheme.type.itemTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                TonalButton("Edit", { draft = name; editing = true }, icon = Icons.Rounded.Edit)
            }
            // P10: TODO send the new name in the next sync (update this user's MemberEntity rows and mark them dirty).
            Text("A new name reaches groupmates on your next sync.", style = LiverooTheme.type.caption, color = colors.textSecondary)
        }
        // Addendum 01, A5: in-app dark mode switch. The whole row toggles.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Appearance", style = LiverooTheme.type.label, color = colors.textSecondary)
            GroupedCard {
                val dark = rememberDarkMode()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = LiverooDimens.rowMinWithLeading)
                        .toggleable(value = dark, role = Role.Switch, onValueChange = { ThemePreference.setDark(context, it) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconTile(Icons.Rounded.DarkMode, bg = colors.surfaceTile, tint = colors.textPrimary)
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode", style = LiverooTheme.type.itemTitle, color = colors.textPrimary)
                        Text("Use a dark theme in Liveroo", style = LiverooTheme.type.caption, color = colors.textSecondary)
                    }
                    Switch(
                        checked = dark, onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent, checkedBorderColor = colors.accent,
                            uncheckedTrackColor = colors.surfaceTile, uncheckedThumbColor = colors.outlineControl, uncheckedBorderColor = colors.outlineControl
                        )
                    )
                }
            }
        }
        GroupedCard(header = "Bluetooth") {
            StatusLine("Bluetooth", if (c.bluetooth.isEnabled) "On" else "Off", c.bluetooth.isEnabled)
            RowDivider()
            val granted = BtPermissions.granted(context)
            StatusLine("Nearby devices permission", if (granted) "Allowed" else "Not allowed", granted)
        }
        // P12: permanent data note
        Banner(
            "Liveroo saves everything on this phone only. There is no cloud backup. Sync often so your groupmates have a copy.",
            icon = Icons.Rounded.Storage
        )
        GroupedCard(header = "About") {
            ListRow(title = "Liveroo 1.0", subtitle = "CS312 Group 4 · BatStateU Alangilan")
        }
    }

    if (editing) LiverooDialog(
        onDismissRequest = { editing = false },
        title = "Your name",
        confirmText = "Save",
        confirmEnabled = draft.isNotBlank(),
        onConfirm = {
            editing = false
            scope.launch { c.identity.setName(draft) }
        },
        dismissText = "Cancel",
        onDismiss = { editing = false }
    ) {
        LiverooTextField(
            value = draft, onValueChange = { draft = it }, label = "Display name", maxLength = 30
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    val colors = LiverooTheme.colors
    ListRow(
        title = label,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null,
                    tint = if (ok) colors.accent else colors.error
                )
                Text(value, style = LiverooTheme.type.chip, color = colors.textPrimary)
            }
        }
    )
}

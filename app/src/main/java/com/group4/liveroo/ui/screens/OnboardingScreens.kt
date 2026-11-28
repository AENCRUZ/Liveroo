package com.group4.liveroo.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.bluetooth.BtPermissions
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.GroupedCard
import com.group4.liveroo.ui.components.IconTile
import com.group4.liveroo.ui.components.ListRow
import com.group4.liveroo.ui.components.LiverooTextField
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.RowDivider
import com.group4.liveroo.ui.components.TextActionButton
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Full-screen frame for onboarding (no top bar, no nav). 24 dp margins. */
@Composable
private fun OnboardingFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = LiverooTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(LiverooDimens.space24),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) { content() }
    }
}

// ---------- S01 Splash ----------
@Composable
fun SplashScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        c.identity.ensureId()
        delay(900)
        val next = when {
            !c.identity.onboarded.first() && !BtPermissions.granted(context) -> Routes.PERMISSIONS
            !c.identity.onboarded.first() -> Routes.NAME
            else -> Routes.GROUPS
        }
        nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
    }
    Surface(color = LiverooTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Liveroo", style = LiverooTheme.type.brandDisplay, color = LiverooTheme.colors.accent)
            Spacer(Modifier.padding(8.dp))
            Text(
                "Group tasks, shared nearby.",
                style = LiverooTheme.type.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = LiverooTheme.colors.textSecondary
            )
        }
    }
}

// ---------- S02 Permissions ----------
@Composable
fun PermissionsScreen(nav: NavController) {
    val c = LocalContainer.current
    var deniedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            nav.navigate(if (c.bluetooth.isEnabled) Routes.NAME else Routes.BT_OFF)
        } else deniedOnce = true
    }
    val colors = LiverooTheme.colors
    OnboardingFrame {
        Spacer(Modifier.height(16.dp))
        IconTile(Icons.Rounded.Bluetooth, size = 64.dp, bg = colors.accentContainer, tint = colors.accent)
        Text("Let Liveroo find your groupmates", style = LiverooTheme.type.brandHeadline, color = colors.textPrimary)
        GroupedCard {
            Reason(Icons.Rounded.PhoneAndroid, "Find nearby phones", "Discover groupmates within about 10 m.")
            RowDivider(LiverooDimens.rowDividerInsetLeading)
            Reason(Icons.Rounded.Link, "Connect to share task updates", "Send and receive changes over Bluetooth.")
            RowDivider(LiverooDimens.rowDividerInsetLeading)
            Reason(Icons.Rounded.CloudOff, "No internet needed", "Everything works offline, on this phone.")
        }
        if (deniedOnce) Banner(
            "Inviting and syncing won't work without this permission. Your groups and tasks still work.",
            BannerKind.ERROR
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (deniedOnce) "Try again" else "Allow", { launcher.launch(BtPermissions.required) })
        // P19: offline features keep working without Bluetooth permissions.
        TextActionButton("Skip for now", { nav.navigate(Routes.NAME) })
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    val colors = LiverooTheme.colors
    ListRow(
        title = title, subtitle = body,
        leading = { IconTile(icon, bg = colors.accentContainer, tint = colors.accent) }
    )
}

// ---------- S03 Turn on Bluetooth ----------
@Composable
fun BluetoothOffScreen(nav: NavController) {
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        nav.navigate(Routes.NAME)
    }
    val colors = LiverooTheme.colors
    OnboardingFrame {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(Icons.Rounded.BluetoothDisabled, size = 112.dp, bg = colors.pendingSurface, tint = colors.pendingText)
            Text("Bluetooth is off", style = LiverooTheme.type.brandHeadline, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "Turn it on to invite groupmates and share updates. Your tasks still work without it.",
                style = LiverooTheme.type.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Turn on", { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }, icon = Icons.Rounded.Bluetooth)
        TextActionButton("Not now", { nav.navigate(Routes.NAME) })
    }
}

// ---------- S04 Name setup ----------
@Composable
fun NameSetupScreen(nav: NavController) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    OnboardingFrame {
        Text("What should your groupmates call you?", style = LiverooTheme.type.brandHeadline, color = LiverooTheme.colors.textPrimary)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(name.ifBlank { "?" }, size = 72) }
        LiverooTextField(
            value = name,
            onValueChange = { name = it; showError = false },
            label = "Display name",
            maxLength = 30,
            helper = "Shown to groupmates when you invite or sync.",
            error = if (showError) "Enter a name." else null
        )
        Banner(
            "Your groups are saved only on this phone. If you uninstall Liveroo or lose your phone, anything you haven't synced is gone.",
            icon = Icons.Rounded.Storage
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", {
            if (name.isBlank()) showError = true else scope.launch {
                c.identity.ensureId()
                c.identity.setName(name)
                nav.navigate(Routes.GROUPS) { popUpTo(0) { inclusive = true } }
            }
        })
    }
}

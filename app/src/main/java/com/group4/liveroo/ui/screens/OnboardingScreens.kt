package com.group4.liveroo.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.bluetooth.BtPermissions
import com.group4.liveroo.ui.components.Avatar
import com.group4.liveroo.ui.components.Banner
import com.group4.liveroo.ui.components.BannerKind
import com.group4.liveroo.ui.components.IconCircle
import com.group4.liveroo.ui.components.LiverooCard
import com.group4.liveroo.ui.components.PrimaryButton
import com.group4.liveroo.ui.components.SecondaryButton
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.ForestGreen
import com.group4.liveroo.ui.theme.SecondaryText
import com.group4.liveroo.ui.theme.SoftPeach
import com.group4.liveroo.ui.theme.WarmCream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Full-screen frame for onboarding (no top bar). */
@Composable
private fun OnboardingFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = WarmCream, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
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
    Surface(color = WarmCream, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Liveroo", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 56.sp, lineHeight = 60.sp), color = ForestGreen)
            Spacer(Modifier.padding(8.dp))
            Text("Group tasks, shared nearby.", style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
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
    OnboardingFrame {
        IconCircle(Icons.Filled.Bluetooth)
        Text("Let Liveroo find your groupmates", style = MaterialTheme.typography.headlineLarge)
        LiverooCard {
            Reason(Icons.Filled.PhoneAndroid, "Find nearby phones", "Discover groupmates within about 10 m.")
            Reason(Icons.Filled.Link, "Connect to share task updates", "Send and receive changes over Bluetooth.")
            Reason(Icons.Filled.CloudOff, "No internet needed", "Everything works offline, on this phone.")
        }
        if (deniedOnce) Banner(
            "Inviting and syncing won't work without this permission. Your groups and tasks still work.",
            BannerKind.ERROR
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (deniedOnce) "Try again" else "Allow", { launcher.launch(BtPermissions.required) })
        // P19: offline features keep working without Bluetooth permissions.
        SecondaryButton("Skip for now", { nav.navigate(Routes.NAME) })
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconCircle(icon, size = 40)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
    }
}

// ---------- S03 Turn on Bluetooth ----------
@Composable
fun BluetoothOffScreen(nav: NavController) {
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        nav.navigate(Routes.NAME)
    }
    OnboardingFrame {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconCircle(Icons.Filled.BluetoothDisabled, bg = SoftPeach, tint = SecondaryText, size = 120)
            Text("Bluetooth is off", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Turn it on to invite groupmates and share updates. Your tasks still work without it.",
                style = MaterialTheme.typography.bodyLarge, color = SecondaryText, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Turn on", { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) })
        SecondaryButton("Not now", { nav.navigate(Routes.NAME) })
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
        Text("What should your groupmates call you?", style = MaterialTheme.typography.headlineLarge)
        Avatar(name.ifBlank { "?" }, size = 72)
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) { name = it; showError = false } },
            label = { Text("Display name") },
            singleLine = true,
            isError = showError,
            supportingText = { Text(if (showError) "Enter a name." else "Shown to groupmates when you invite or sync.  ${name.length}/30") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Storage, null)
            Text(
                "Your groups are saved only on this phone. If you uninstall Liveroo or lose your phone, anything you haven't synced is gone.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
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

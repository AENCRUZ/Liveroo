package com.group4.liveroo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.group4.liveroo.LocalContainer
import com.group4.liveroo.ui.nav.Routes
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooTheme

/**
 * Floating nav pill (replaces the old bottom bar). Same navigate() calls as before.
 * Drawn as an overlay by [LiverooScaffold], 16 dp from the sides and 16 dp above the navigation-bar inset.
 */
@Composable
fun LiverooFloatingNav(nav: NavController, current: Tab) {
    val unsynced by LocalContainer.current.repository.unsyncedCount.collectAsStateWithLifecycle(0)
    fun go(route: String) = nav.navigate(route) {
        popUpTo(Routes.GROUPS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    LiverooFloatingNavBar(
        current = current,
        badgeCount = unsynced,
        onSelect = { tab ->
            when (tab) {
                Tab.GROUPS -> go(Routes.GROUPS)
                Tab.MY_TASKS -> go(Routes.MY_TASKS)
                Tab.SYNC -> go(Routes.sync())
            }
        }
    )
}

/** Stateless drawing part, so it can be previewed without a NavController. */
@Composable
fun LiverooFloatingNavBar(current: Tab, badgeCount: Int, onSelect: (Tab) -> Unit) {
    val c = LiverooTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
    val tabs = Tab.entries
    // The pill is a fixed 64 dp, so its labels stop growing at 130% font size; the rest of the app scales fully.
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale.coerceAtMost(1.3f))
    ) {
    Box(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = LiverooDimens.navBarInset)
            .padding(bottom = LiverooDimens.navBarInset)
            .fillMaxWidth()
            .height(LiverooDimens.navBarHeight)
            .shadow(6.dp, shape)
            .background(c.navBar, shape)
            .border(1.dp, c.navBorder, shape)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val itemW = maxWidth / tabs.size
            val x by animateDpAsState(itemW * tabs.indexOf(current), tween(200), label = "navIndicator")
            Box(
                Modifier.offset(x = x).width(itemW).fillMaxHeight()
                    .background(c.accent, androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                NavItem(Tab.GROUPS, "Groups", Icons.Rounded.Groups, current, 0, onSelect, Modifier.weight(1f))
                NavItem(Tab.MY_TASKS, "My Tasks", Icons.Rounded.TaskAlt, current, 0, onSelect, Modifier.weight(1f))
                NavItem(Tab.SYNC, "Sync", Icons.Rounded.Sync, current, badgeCount, onSelect, Modifier.weight(1f))
            }
        }
    }
    }
}

@Composable
private fun NavItem(
    tab: Tab, label: String, icon: ImageVector, current: Tab, badge: Int,
    onSelect: (Tab) -> Unit, modifier: Modifier
) {
    val c = LiverooTheme.colors
    val selected = tab == current
    val tint = if (selected) c.onAccent else c.navInactive
    val desc = if (badge > 0) "$label, $badge ${if (badge == 1) "change" else "changes"} waiting" else label
    Box(
        modifier.fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
                if (badge > 0) {
                    Text(
                        if (badge > 99) "99+" else "$badge",
                        style = LiverooTheme.type.micro, color = c.onNavBadge, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp)
                            .background(c.navBadge, androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 5.dp)
                    )
                }
            }
            Text(label, style = LiverooTheme.type.chip, color = tint)
        }
    }
}

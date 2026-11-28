package com.group4.liveroo.ui.nav

import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.group4.liveroo.ui.components.LiverooFloatingNav
import com.group4.liveroo.ui.components.Tab
import com.group4.liveroo.ui.screens.BluetoothOffScreen
import com.group4.liveroo.ui.screens.CreateGroupScreen
import com.group4.liveroo.ui.screens.GroupDetailScreen
import com.group4.liveroo.ui.screens.GroupSettingsScreen
import com.group4.liveroo.ui.screens.GroupsScreen
import com.group4.liveroo.ui.screens.InviteScreen
import com.group4.liveroo.ui.screens.JoinScreen
import com.group4.liveroo.ui.screens.MembersScreen
import com.group4.liveroo.ui.screens.MyTasksScreen
import com.group4.liveroo.ui.screens.NameSetupScreen
import com.group4.liveroo.ui.screens.PermissionsScreen
import com.group4.liveroo.ui.screens.SettingsScreen
import com.group4.liveroo.ui.screens.SplashScreen
import com.group4.liveroo.ui.screens.SyncResultScreen
import com.group4.liveroo.ui.screens.SyncScreen
import com.group4.liveroo.ui.screens.TaskDetailScreen
import com.group4.liveroo.ui.screens.TaskFormScreen
import com.group4.liveroo.ui.screens.TransferScreen
import com.group4.liveroo.ui.theme.LiverooTheme

// ---------------------------------------------------------------------------------------------
// Transitions (addendum 01, part B1)
// ---------------------------------------------------------------------------------------------

private val tabRoots = listOf(Routes.GROUPS, Routes.MY_TASKS, Routes.SYNC) // index = tab order
private val onboardingRoutes = setOf(Routes.SPLASH, Routes.PERMISSIONS, Routes.BT_OFF, Routes.NAME)
private val syncFlow = setOf(Routes.SYNC, Routes.SYNC_RESULT)

private const val SLIDE_MS = 250
private const val FADE_MS = 200
private const val SYNC_FADE_MS = 150
private const val START_SCALE = 0.96f

private enum class Kind { SLIDE, ONBOARDING, SYNC, DEEP }

/** Tab a route belongs to (a group, its members and a task all belong to Groups), or -1 on screens with no tab. */
private fun tabIndexOf(route: String?): Int = tabForRoute(route)?.ordinal ?: -1

private fun kindOf(from: String?, to: String?): Kind = when {
    // Switching to a different tab slides, even from deep inside a group, task or members screen.
    tabIndexOf(from) >= 0 && tabIndexOf(to) >= 0 && tabIndexOf(from) != tabIndexOf(to) -> Kind.SLIDE
    from in onboardingRoutes || to in onboardingRoutes -> Kind.ONBOARDING
    from in syncFlow && to in syncFlow -> Kind.SYNC
    else -> Kind.DEEP
}

/** True when the system "Remove animations" setting (animator scale 0) is on. */
private fun animationsOff(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fromRoute() = initialState.destination.route
private fun AnimatedContentTransitionScope<NavBackStackEntry>.toRoute() = targetState.destination.route

/** Which way a tab-to-tab slide goes: true = new screen comes in from the right (higher tab index). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.goesRight() =
    tabIndexOf(toRoute()) > tabIndexOf(fromRoute())

/**
 * Create group opens the new group with navigate { popUpTo(GROUPS) }. Compose Navigation reports any
 * navigate-with-popUpTo as a "pop", which would play the Back animation (fade only) for what is really
 * opening a screen. Treat that one case as forward.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isOpeningNewGroup() =
    fromRoute() == Routes.CREATE_GROUP && toRoute() == Routes.GROUP

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(off: Boolean, popFlag: Boolean): EnterTransition {
    if (off) return EnterTransition.None
    val pop = popFlag && !isOpeningNewGroup()
    return when (kindOf(fromRoute(), toRoute())) {
        Kind.SLIDE -> slideIntoContainer(
            if (goesRight()) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
            tween(SLIDE_MS, easing = FastOutSlowInEasing)
        )
        Kind.ONBOARDING -> fadeIn(tween(FADE_MS, easing = FastOutSlowInEasing))
        Kind.SYNC -> fadeIn(tween(SYNC_FADE_MS, easing = FastOutSlowInEasing))
        Kind.DEEP ->
            if (pop) fadeIn(tween(FADE_MS, easing = FastOutSlowInEasing))
            else fadeIn(tween(FADE_MS, easing = FastOutSlowInEasing)) + scaleIn(tween(FADE_MS, easing = FastOutSlowInEasing), initialScale = START_SCALE)
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(off: Boolean, popFlag: Boolean): ExitTransition {
    if (off) return ExitTransition.None
    val pop = popFlag && !isOpeningNewGroup()
    return when (kindOf(fromRoute(), toRoute())) {
        Kind.SLIDE -> slideOutOfContainer(
            if (goesRight()) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
            tween(SLIDE_MS, easing = FastOutSlowInEasing)
        )
        Kind.ONBOARDING -> fadeOut(tween(FADE_MS, easing = FastOutSlowInEasing))
        Kind.SYNC -> fadeOut(tween(SYNC_FADE_MS, easing = FastOutSlowInEasing))
        Kind.DEEP ->
            if (pop) fadeOut(tween(FADE_MS, easing = FastOutSlowInEasing)) + scaleOut(tween(FADE_MS, easing = FastOutSlowInEasing), targetScale = START_SCALE)
            else fadeOut(tween(FADE_MS, easing = FastOutSlowInEasing))
    }
}

// ---------------------------------------------------------------------------------------------
// Floating nav (addendum 01, part B2): drawn once, above the NavHost
// ---------------------------------------------------------------------------------------------

/** The tab a route belongs to, or null when the floating nav is hidden on it. Same screens as before. */
private fun tabForRoute(route: String?): Tab? = when (route) {
    Routes.GROUPS, Routes.GROUP, Routes.MEMBERS, Routes.TASK -> Tab.GROUPS
    Routes.MY_TASKS -> Tab.MY_TASKS
    Routes.SYNC, Routes.SYNC_RESULT -> Tab.SYNC
    else -> null
}

@Composable
fun LiverooNavHost() {
    val nav = rememberNavController()
    val groupArg = listOf(navArgument("groupId") { type = NavType.StringType })
    val context = LocalContext.current
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentTab = tabForRoute(backStackEntry?.destination?.route)
    // Keep the last tab while the nav fades out, so it doesn't change label or highlight mid-fade.
    var shownTab by remember { mutableStateOf(Tab.GROUPS) }
    if (currentTab != null) shownTab = currentTab

    // Swipe left/right on a tab root to move to the neighbouring tab. Same navigate() call and the same
    // slide as tapping the floating nav. Not used in transfer mode, where the Sync screen is a fixed step.
    val route = backStackEntry?.destination?.route
    val swipeIndex = tabRoots.indexOf(route)
    val inTransfer = route == Routes.SYNC && backStackEntry?.arguments?.getString("groupId") != null
    val swipeTargets = listOf(Routes.GROUPS, Routes.MY_TASKS, Routes.sync())
    fun goToTab(target: String) = nav.navigate(target) {
        popUpTo(Routes.GROUPS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // The window behind the app is cream; paint the theme background so cross-fades never show it in dark mode.
    Box(
        Modifier.fillMaxSize().background(LiverooTheme.colors.background)
            .pointerInput(swipeIndex, inTransfer) {
                if (swipeIndex < 0 || inTransfer) return@pointerInput
                val threshold = 72.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { total = 0f },
                    onDragEnd = {
                        val next = when {
                            total < -threshold -> swipeIndex + 1   // swipe left: next tab
                            total > threshold -> swipeIndex - 1    // swipe right: previous tab
                            else -> -1
                        }
                        if (next in swipeTargets.indices) goToTab(swipeTargets[next])
                        total = 0f
                    },
                    onHorizontalDrag = { _, dx -> total += dx }
                )
            }
    ) {
        NavHost(
            navController = nav,
            startDestination = Routes.SPLASH,
            enterTransition = { enterFor(animationsOff(context), popFlag = false) },
            exitTransition = { exitFor(animationsOff(context), popFlag = false) },
            popEnterTransition = { enterFor(animationsOff(context), popFlag = true) },
            popExitTransition = { exitFor(animationsOff(context), popFlag = true) }
        ) {
            composable(Routes.SPLASH) { SplashScreen(nav) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(nav) }
            composable(Routes.BT_OFF) { BluetoothOffScreen(nav) }
            composable(Routes.NAME) { NameSetupScreen(nav) }

            composable(Routes.GROUPS) { GroupsScreen(nav) }
            composable(Routes.CREATE_GROUP) { CreateGroupScreen(nav) }
            composable(Routes.GROUP, groupArg) { GroupDetailScreen(nav, it.arguments?.getString("groupId").orEmpty()) }
            composable(Routes.MEMBERS, groupArg) { MembersScreen(nav, it.arguments?.getString("groupId").orEmpty()) }
            composable(Routes.INVITE, groupArg) { InviteScreen(nav, it.arguments?.getString("groupId").orEmpty()) }
            composable(Routes.JOIN) { JoinScreen(nav) }
            composable(Routes.TRANSFER, groupArg) { TransferScreen(nav, it.arguments?.getString("groupId").orEmpty()) }
            composable(Routes.GROUP_SETTINGS, groupArg) { GroupSettingsScreen(nav, it.arguments?.getString("groupId").orEmpty()) }

            composable(Routes.TASK, listOf(navArgument("taskId") { type = NavType.StringType })) {
                TaskDetailScreen(nav, it.arguments?.getString("taskId").orEmpty())
            }
            composable(
                Routes.TASK_FORM,
                listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                TaskFormScreen(nav, it.arguments?.getString("groupId").orEmpty(), it.arguments?.getString("taskId"))
            }
            composable(Routes.MY_TASKS) { MyTasksScreen(nav) }

            composable(
                Routes.SYNC,
                listOf(
                    navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("newLeaderId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                SyncScreen(nav, it.arguments?.getString("groupId"), it.arguments?.getString("newLeaderId"))
            }
            composable(Routes.SYNC_RESULT) { SyncResultScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
        }

        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (animationsOff(context)) EnterTransition.None else fadeIn(tween(SYNC_FADE_MS)),
            exit = if (animationsOff(context)) ExitTransition.None else fadeOut(tween(SYNC_FADE_MS))
        ) {
            LiverooFloatingNav(nav, shownTab)
        }
    }
}

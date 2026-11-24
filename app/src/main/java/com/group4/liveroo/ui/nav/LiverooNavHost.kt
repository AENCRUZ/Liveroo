package com.group4.liveroo.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

@Composable
fun LiverooNavHost() {
    val nav = rememberNavController()
    val groupArg = listOf(navArgument("groupId") { type = NavType.StringType })

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
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
}

package com.group4.liveroo.ui.nav

/** One route per screen in the spec (S01-S18). */
object Routes {
    const val SPLASH = "splash"                 // S01
    const val PERMISSIONS = "permissions"       // S02
    const val BT_OFF = "bt_off"                 // S03
    const val NAME = "name"                     // S04
    const val GROUPS = "groups"                 // S05
    const val CREATE_GROUP = "create_group"     // S06
    const val GROUP = "group/{groupId}"         // S07
    const val MEMBERS = "members/{groupId}"     // S08
    const val INVITE = "invite/{groupId}"       // S09
    const val JOIN = "join"                     // S10
    const val TRANSFER = "transfer/{groupId}"   // S11
    const val GROUP_SETTINGS = "group_settings/{groupId}" // S12
    const val TASK = "task/{taskId}"            // S13
    const val TASK_FORM = "task_form/{groupId}?taskId={taskId}" // S14
    const val SYNC = "sync?groupId={groupId}&newLeaderId={newLeaderId}" // S15 (+ transfer mode)
    const val SYNC_RESULT = "sync_result"       // S16
    const val SETTINGS = "settings"             // S17
    const val MY_TASKS = "my_tasks"             // S18

    fun group(id: String) = "group/$id"
    fun members(id: String) = "members/$id"
    fun invite(id: String) = "invite/$id"
    fun transfer(id: String) = "transfer/$id"
    fun groupSettings(id: String) = "group_settings/$id"
    fun task(id: String) = "task/$id"
    fun taskForm(groupId: String, taskId: String? = null) =
        if (taskId == null) "task_form/$groupId" else "task_form/$groupId?taskId=$taskId"
    fun sync() = "sync"
    fun transferSync(groupId: String, newLeaderId: String) = "sync?groupId=$groupId&newLeaderId=$newLeaderId"
}

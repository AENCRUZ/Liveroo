package com.group4.liveroo.ui.components

import com.group4.liveroo.rules.DenyReason

/** What the person sees when the repository refuses a change. The screens already hide most of these actions. */
fun DenyReason.userMessage(): String = when (this) {
    DenyReason.GROUP_NOT_FOUND -> "This group is no longer on this phone."
    DenyReason.GROUP_NOT_WRITABLE -> "You left this group, so it can't be changed."
    DenyReason.NOT_A_MEMBER -> "You're not an active member of this group."
    DenyReason.NOT_LEADER -> "Only the group leader can do that."
    DenyReason.NOT_ASSIGNEE_OR_LEADER -> "Only the assigned member or the leader can change this status."
    DenyReason.TASK_NOT_FOUND -> "This task is no longer available."
    DenyReason.TASK_DELETED -> "This task was deleted."
    DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER -> "Choose an active member of this group as the assignee."
    DenyReason.LEADER_MUST_TRANSFER -> "Transfer leadership before leaving this group."
    DenyReason.OTHER_MEMBERS_PRESENT -> "This group still has other members, so it can't be deleted from this phone."
}

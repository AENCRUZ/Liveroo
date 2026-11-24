package com.group4.liveroo.rules

enum class DenyReason {
    GROUP_NOT_FOUND,
    GROUP_NOT_WRITABLE,
    NOT_A_MEMBER,
    NOT_LEADER,
    NOT_ASSIGNEE_OR_LEADER,
    TASK_NOT_FOUND,
    TASK_DELETED,
    ASSIGNEE_NOT_ACTIVE_MEMBER,
    /** D1: a leader must transfer leadership before leaving. */
    LEADER_MUST_TRANSFER,
    /** N5: only a sole-member leader may delete a group from this phone. */
    OTHER_MEMBERS_PRESENT
}

sealed interface Auth {
    data object Allowed : Auth
    data class Denied(val reason: DenyReason) : Auth
}

/**
 * Who may do what, judged from this phone's view of the group (D1, D2, D3, N5). The repository calls these before
 * every local write (Step 3). Roles come only from the leadership chain and member rows, never from the screens.
 */
object Authz {
    private val allowed = Auth.Allowed
    private fun denied(r: DenyReason) = Auth.Denied(r)

    private fun leaderGate(group: GroupView, me: String): Auth? = when {
        !group.writable -> denied(DenyReason.GROUP_NOT_WRITABLE)
        !group.isActive(me) -> denied(DenyReason.NOT_A_MEMBER)
        !group.isLeader(me) -> denied(DenyReason.NOT_LEADER)
        else -> null
    }

    /** Create or edit a task (leader only). The assignee, if any, must be an active member (D3). */
    fun saveTask(group: GroupView, me: String, assigneeId: String?): Auth {
        leaderGate(group, me)?.let { return it }
        if (assigneeId != null && !group.isActive(assigneeId)) return denied(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER)
        return allowed
    }

    /** Delete a task (leader only). */
    fun deleteTask(group: GroupView, me: String): Auth = leaderGate(group, me) ?: allowed

    /** Invite or add a member (leader only). */
    fun addMember(group: GroupView, me: String): Auth = leaderGate(group, me) ?: allowed

    /** Change a task's status: the leader on any task, the assignee on their own task, nobody else (D2). */
    fun setStatus(group: GroupView, me: String, task: EffectiveTask?): Auth {
        if (!group.writable) return denied(DenyReason.GROUP_NOT_WRITABLE)
        if (!group.isActive(me)) return denied(DenyReason.NOT_A_MEMBER)
        if (task == null) return denied(DenyReason.TASK_NOT_FOUND)
        if (task.deleted) return denied(DenyReason.TASK_DELETED)
        if (group.isLeader(me) || task.assigneeId == me) return allowed
        return denied(DenyReason.NOT_ASSIGNEE_OR_LEADER)
    }

    /** Leave a group. A leader must transfer leadership first (D1). */
    fun leaveGroup(group: GroupView, me: String): Auth = when {
        !group.writable -> denied(DenyReason.GROUP_NOT_WRITABLE)
        !group.isActive(me) -> denied(DenyReason.NOT_A_MEMBER)
        group.isLeader(me) -> denied(DenyReason.LEADER_MUST_TRANSFER)
        else -> allowed
    }

    /**
     * Delete a group from this phone only (N5): the leader, and only when no other active member exists.
     * It is not propagated: other phones keep their copies.
     */
    fun deleteGroupLocally(group: GroupView, me: String): Auth {
        leaderGate(group, me)?.let { return it }
        val others = group.members.any { it.active && it.userId != me }
        return if (others) denied(DenyReason.OTHER_MEMBERS_PRESENT) else allowed
    }
}

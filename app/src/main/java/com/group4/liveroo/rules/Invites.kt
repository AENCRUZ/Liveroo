package com.group4.liveroo.rules

/** What the invited phone does with an invitation (D9, N2). */
enum class InviteDecision {
    /** I don't have the group: ask the person, then store it. */
    ACCEPT_NEW,

    /**
     * I already have the group, the offer comes from its current leader and I'm already an active member: the
     * invitation only completes a membership the leader hasn't recorded yet (a lost last message). Nothing is asked
     * and nothing is overwritten.
     */
    COMPLETE_EXISTING,

    /** I already have the group in any other way (including one I have left): refuse (D9). */
    REJECT_ALREADY_HAS_GROUP
}

/**
 * Decides, from the invited phone's own view, how to treat an invitation to a group from [inviterId].
 * [group] is this phone's view of the group, or null if it doesn't hold it.
 */
fun classifyInvite(group: GroupView?, me: String, inviterId: String): InviteDecision = when {
    group == null -> InviteDecision.ACCEPT_NEW
    group.writable && group.isActive(me) && group.isLeader(inviterId) -> InviteDecision.COMPLETE_EXISTING
    else -> InviteDecision.REJECT_ALREADY_HAS_GROUP
}

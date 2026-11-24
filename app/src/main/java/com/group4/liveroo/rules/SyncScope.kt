package com.group4.liveroo.rules

/** Shown when the other phone skipped a group because it doesn't recognise me as an active member (N9). */
const val GROUP_SKIPPED_MESSAGE =
    "This group wasn't synced because the other device doesn't recognize you as an active member. " +
        "Sync with the group leader first."

/** One of my groups, as needed to decide how it takes part in a sync with a given peer. */
data class GroupSyncView(
    val id: String,
    val members: List<MemberState>,
    /** I left this group and my departure hasn't been confirmed yet (C21). */
    val leftByMe: Boolean
)

/** How each group takes part in a sync with one peer. Lists are sorted so results are deterministic. */
data class SyncScope(
    /** Both phones hold the group and the peer is an active member in my data: full exchange (D4). */
    val ordinary: List<String>,
    /**
     * Both phones hold the group and my data shows the peer has already left: only a departure confirmation
     * may be exchanged, never group data (N1). The data to exchange is defined in Step 7.
     */
    val departureOnly: List<String>,
    /** Both phones hold the group but I don't recognise the peer as an active member: skipped (D4). */
    val skipped: List<String>
)

/**
 * Decides, from my side, how each group is handled in a sync with [peerUserId], who claims [peerGroupIds].
 * Groups the peer doesn't claim, or that I don't hold, are not shared and appear in none of the lists.
 */
fun classifySyncScope(mine: List<GroupSyncView>, peerUserId: String, peerGroupIds: Collection<String>): SyncScope {
    val peerGroups = peerGroupIds.toSet()
    val ordinary = mutableListOf<String>()
    val departureOnly = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    for (g in mine.filter { it.id in peerGroups }) {
        val peerRow = g.members.firstOrNull { it.userId == peerUserId }
        when {
            peerRow?.active == true -> ordinary += g.id
            peerRow?.leftAt != null -> departureOnly += g.id
            else -> skipped += g.id
        }
    }
    return SyncScope(ordinary.sorted(), departureOnly.sorted(), skipped.sorted())
}

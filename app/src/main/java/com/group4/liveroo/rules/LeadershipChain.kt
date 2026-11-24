package com.group4.liveroo.rules

import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.Ver

/*
 * Pure rules (no Room, no Android) from docs/MERGE_SPEC.md. Nothing here is wired into the repository or the
 * sync code yet; Steps 3 and 4 call these functions. Identities are self-asserted: none of this is authentication.
 */

// ---------- Leadership chain order (spec 2.2) ----------

/**
 * Preference between two chains: > 0 if [a] wins, < 0 if [b] wins, 0 if identical.
 * At the first index where the entries differ the smaller `at` wins; identical `at` falls back to the
 * lexicographically smaller leader id. If one chain is a prefix of the other, the longer wins.
 * This is timestamp-based and deterministic; it does not prove which handover actually completed first.
 */
fun compareChains(a: List<LeaderEntry>, b: List<LeaderEntry>): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val x = a[i]
        val y = b[i]
        if (x == y) continue
        val byAt = y.at.compareTo(x.at)          // smaller `at` => positive => a wins
        if (byAt != 0) return byAt
        return y.id.compareTo(x.id)              // smaller id => positive => a wins
    }
    return a.size.compareTo(b.size)              // longer wins
}

enum class ChainRejection { EMPTY, DIFFERENT_ORIGIN, LEADER_NOT_A_MEMBER }

sealed interface ChainMerge {
    /** The local chain stays (incoming is identical or loses). */
    data class Kept(val chain: List<LeaderEntry>) : ChainMerge

    /** The incoming chain wins and is adopted whole; entries are never mixed between chains. */
    data class Adopted(val chain: List<LeaderEntry>) : ChainMerge

    data class Rejected(val reason: ChainRejection) : ChainMerge
}

/**
 * Merges a chain received from a peer (also used for relayed leadership, spec 2.4, accepted on trust).
 * [memberIds] are the group's member user ids after the members were merged.
 * A [local] chain of null/empty means the group is new on this phone.
 */
fun mergeChains(local: List<LeaderEntry>?, incoming: List<LeaderEntry>, memberIds: Set<String>): ChainMerge {
    if (incoming.isEmpty()) return ChainMerge.Rejected(ChainRejection.EMPTY)
    if (!local.isNullOrEmpty()) {
        if (incoming.first() != local.first()) return ChainMerge.Rejected(ChainRejection.DIFFERENT_ORIGIN)
        if (compareChains(incoming, local) <= 0) return ChainMerge.Kept(local)
    }
    if (incoming.any { it.id !in memberIds }) return ChainMerge.Rejected(ChainRejection.LEADER_NOT_A_MEMBER)
    return ChainMerge.Adopted(incoming)
}

/** True if [ver] was written by the person who led the group in the epoch the version is tagged with. */
fun isLeaderVer(chain: List<LeaderEntry>, ver: Ver): Boolean =
    ver.epoch in chain.indices && chain[ver.epoch].id == ver.by

// ---------- Members ----------

/** A member row as the rules see it. */
data class MemberState(
    val userId: String,
    val displayName: String,
    val joinedAt: Long,
    val leftAt: Long?,
    val updatedAt: Long
) {
    val active: Boolean get() = leftAt == null
}

/** Total order for member rows: updatedAt, then "has left", then name, then the remaining fields. */
private val memberOrder: Comparator<MemberState> = compareBy<MemberState>(
    { it.updatedAt }, { it.leftAt != null }, { it.displayName }, { it.joinedAt }, { it.leftAt ?: Long.MIN_VALUE }
)

/** Merge one member row: the greater row under [memberOrder] wins (identical rows keep the local one). */
fun mergeMember(local: MemberState?, incoming: MemberState): MemberState =
    if (local == null || memberOrder.compare(incoming, local) > 0) incoming else local

// ---------- Group view and transfer validation (spec 2.3, D6, D7) ----------

/** What the rules need to know about one group on one phone. */
data class GroupView(
    val chain: List<LeaderEntry>,
    val members: List<MemberState>,
    /** False once I have left (the group is read-only until the departure syncs). */
    val writable: Boolean
) {
    val leaderId: String get() = chain.last().id
    val epoch: Int get() = chain.size - 1
    fun member(userId: String): MemberState? = members.firstOrNull { it.userId == userId }
    fun isActive(userId: String): Boolean = member(userId)?.active == true
    fun isLeader(userId: String): Boolean = leaderId == userId && isActive(userId)
}

/** The facts carried by a transfer request. Both phones evaluate the same facts and must reach the same verdict. */
data class TransferProposal(val groupId: String, val newLeaderId: String, val at: Long, val expectedEpoch: Int)

enum class TransferError {
    GROUP_NOT_SHARED,
    GROUP_NOT_WRITABLE,
    SENDER_NOT_LEADER,
    STALE_EPOCH,
    NEW_LEADER_IS_SENDER,
    NEW_LEADER_NOT_ACTIVE_MEMBER,
    RESPONDER_NOT_NEW_LEADER,
    NOT_CONSENTED
}

sealed interface TransferVerdict {
    /** [newChain] is the identical chain both phones append to: the old chain plus (newLeaderId, at). */
    data class Valid(val newChain: List<LeaderEntry>) : TransferVerdict

    data class Invalid(val error: TransferError) : TransferVerdict
}

/**
 * Validates a leadership transfer from this phone's view of the group. The initiator and the responder call this
 * with the same arguments, so the verdict is symmetric. [group] is null if this phone doesn't hold the group.
 */
fun validateTransfer(
    group: GroupView?,
    senderId: String,
    proposal: TransferProposal,
    responderId: String,
    responderConsented: Boolean
): TransferVerdict {
    fun invalid(e: TransferError) = TransferVerdict.Invalid(e)
    if (group == null) return invalid(TransferError.GROUP_NOT_SHARED)
    if (!group.writable) return invalid(TransferError.GROUP_NOT_WRITABLE)
    if (group.leaderId != senderId || !group.isActive(senderId)) return invalid(TransferError.SENDER_NOT_LEADER)
    if (proposal.expectedEpoch != group.epoch) return invalid(TransferError.STALE_EPOCH)
    if (proposal.newLeaderId == senderId) return invalid(TransferError.NEW_LEADER_IS_SENDER)
    if (!group.isActive(proposal.newLeaderId)) return invalid(TransferError.NEW_LEADER_NOT_ACTIVE_MEMBER)
    if (responderId != proposal.newLeaderId) return invalid(TransferError.RESPONDER_NOT_NEW_LEADER)
    if (!responderConsented) return invalid(TransferError.NOT_CONSENTED)
    return TransferVerdict.Valid(group.chain + LeaderEntry(proposal.newLeaderId, proposal.at))
}

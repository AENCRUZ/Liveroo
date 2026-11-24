package com.group4.liveroo.rules

import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.AssigneeReg
import com.group4.liveroo.data.model.ContentReg
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.StatusReg
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.TombstoneReg
import com.group4.liveroo.data.model.Ver

// ---------- Task merge (spec 4.1 - 4.3) ----------

/** What a task merge needs to know: the group's chain (after the group step) and its member ids. */
data class TaskContext(val chain: List<LeaderEntry>, val memberIds: Set<String>)

enum class TaskRejection {
    /** A task that doesn't exist locally is only created from valid leader versions by members. */
    INVALID_FIRST_VERSION
}

sealed interface TaskMerge {
    data class Rejected(val reason: TaskRejection) : TaskMerge

    /** [dropped] counts incoming versions that were refused (non-member author, invalid leader version). */
    data class Merged(val registers: TaskRegisters, val changed: Boolean, val dropped: Int) : TaskMerge
}

/**
 * Merges the registers received for one task into the local ones.
 *
 * For a fixed [ctx] this is a maximum under a total order per register, so it is commutative, associative and
 * idempotent over states that were themselves produced by merges and honest local writes. Versions by non-members
 * are never stored. A leader-written version (content, assignee, tombstone) must be valid for its epoch; a valid
 * version always outranks a tainted one (written under a chain that lost a fork), and when the local value is
 * already tainted an incoming tainted value may compete with it so that two phones holding different tainted
 * values still settle on the same one. Status is never refused for validity here: it is only ranked, and validity
 * is applied by [effectiveStatus].
 */
fun mergeTask(local: TaskRegisters?, incoming: TaskRegisters, ctx: TaskContext): TaskMerge {
    fun member(ver: Ver) = ver.by in ctx.memberIds
    fun valid(ver: Ver) = isLeaderVer(ctx.chain, ver)
    var dropped = 0

    if (local == null) {
        val first = incoming.content.ver
        val second = incoming.assignee.ver
        if (!(member(first) && valid(first) && member(second) && valid(second))) {
            return TaskMerge.Rejected(TaskRejection.INVALID_FIRST_VERSION)
        }
        val tombstone = incoming.tombstone?.takeIf { member(it.ver) && valid(it.ver) }
        val status = incoming.status?.takeIf { member(it.key.ver) }
        if (incoming.tombstone != tombstone) dropped++
        if (incoming.status != status) dropped++
        return TaskMerge.Merged(TaskRegisters(incoming.content, incoming.assignee, tombstone, status), changed = true, dropped)
    }

    /** Valid first, then the version, then the payload text as a final deterministic tie-break. */
    fun <T : Any> rankOf(reg: T, ver: Ver): Triple<Boolean, Ver, String> = Triple(valid(ver), ver, reg.toString())

    fun <T : Any> pickLeaderReg(localReg: T, incomingReg: T, verOf: (T) -> Ver): T {
        if (incomingReg == localReg) return localReg
        val inVer = verOf(incomingReg)
        val acceptable = member(inVer) && (valid(inVer) || !valid(verOf(localReg)))
        if (!acceptable) { dropped++; return localReg }
        val a = rankOf(incomingReg, inVer)
        val b = rankOf(localReg, verOf(localReg))
        val order = compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
        return if (order > 0) incomingReg else localReg
    }

    val content = pickLeaderReg<ContentReg>(local.content, incoming.content) { it.ver }
    val assignee = pickLeaderReg<AssigneeReg>(local.assignee, incoming.assignee) { it.ver }

    val tombstone: TombstoneReg? = when {
        incoming.tombstone == null -> local.tombstone
        local.tombstone == null -> {
            val t = incoming.tombstone
            if (member(t.ver) && valid(t.ver)) t else { dropped++; null }
        }
        else -> pickLeaderReg<TombstoneReg>(local.tombstone, incoming.tombstone) { it.ver }
    }

    val status: StatusReg? = when {
        incoming.status == null -> local.status
        !member(incoming.status.key.ver) -> { dropped++; local.status }
        local.status == null -> incoming.status
        else -> {
            val order = compareValuesBy(incoming.status, local.status, { it.key }, { it.value.ordinal })
            if (order > 0) incoming.status else local.status
        }
    }

    val merged = TaskRegisters(content, assignee, tombstone, status)
    return TaskMerge.Merged(merged, changed = merged != local, dropped = dropped)
}

// ---------- Effective view (spec 4.4): pure functions of the merged state ----------

/** What the screens show for a task. */
data class EffectiveTask(
    val title: String,
    val description: String,
    val assigneeId: String?,
    val status: TaskStatus,
    val deleted: Boolean
)

/**
 * The assignee counts only while that person is an active member and the assignment was made during their current
 * membership (joinedAt not later than the assignment). Leaving clears assignments on every phone alike, and
 * re-adding a member does not bring old assignments back.
 */
fun effectiveAssignee(regs: TaskRegisters, member: (String) -> MemberState?): String? {
    val id = regs.assignee.id ?: return null
    val m = member(id) ?: return null
    return if (m.leftAt == null && regs.assignee.ver.ts >= m.joinedAt) id else null
}

/**
 * A status newer than the leader's snapshot counts only if the leader wrote it or its author is the effective
 * assignee. Otherwise the snapshot shows (a forged or unseen-and-reassigned status can only revert to it).
 */
fun effectiveStatus(regs: TaskRegisters, chain: List<LeaderEntry>, effectiveAssignee: String?): TaskStatus {
    val snapshot = regs.assignee.snapshot
    val s = regs.status ?: return snapshot.value
    val valid = isLeaderVer(chain, s.key.ver) || s.key.ver.by == effectiveAssignee || s.key <= snapshot.key
    return if (valid && s.key > snapshot.key) s.value else snapshot.value
}

/** Deleted = a tombstone exists and is valid for its epoch. Sticky: nothing later clears it. */
fun isDeleted(regs: TaskRegisters, chain: List<LeaderEntry>): Boolean =
    regs.tombstone?.let { isLeaderVer(chain, it.ver) } == true

fun effectiveTask(regs: TaskRegisters, chain: List<LeaderEntry>, member: (String) -> MemberState?): EffectiveTask {
    val assignee = effectiveAssignee(regs, member)
    return EffectiveTask(
        title = regs.content.title,
        description = regs.content.description,
        assigneeId = assignee,
        status = effectiveStatus(regs, chain, assignee),
        deleted = isDeleted(regs, chain)
    )
}

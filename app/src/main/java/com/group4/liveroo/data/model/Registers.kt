package com.group4.liveroo.data.model

import com.group4.liveroo.data.local.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * Data model for the merge design in docs/MERGE_SPEC.md (Step 1b: shape and serialization only).
 * The merge, validity and effective-view rules arrive in later steps. Until then [project] is a naive
 * projection with no validity checks, and the existing last-write-wins merge still decides which row wins.
 */

/** Who wrote a value, under which leadership epoch, and when. Total order: epoch, then ts, then by. */
@Serializable
data class Ver(val epoch: Int, val ts: Long, val by: String) : Comparable<Ver> {
    override fun compareTo(other: Ver): Int = compareValuesBy(this, other, Ver::epoch, Ver::ts, Ver::by)
}

/** Status ordering key: the assignment the author acted on (basis) first, then the write itself. */
@Serializable
data class StatusKey(val basis: Ver, val ver: Ver) : Comparable<StatusKey> {
    override fun compareTo(other: StatusKey): Int = compareValuesBy(this, other, StatusKey::basis, StatusKey::ver)
}

/** One entry of a group's leadership chain. Index in the chain = epoch. */
@Serializable
data class LeaderEntry(val id: String, val at: Long)

@Serializable
data class ContentReg(val title: String, val description: String, val ver: Ver)

/** The status the leader had seen when writing the assignment (spec section 4.1). */
@Serializable
data class StatusSnapshot(val value: TaskStatus, val key: StatusKey)

@Serializable
data class AssigneeReg(val id: String?, val ver: Ver, val snapshot: StatusSnapshot)

@Serializable
data class StatusReg(val value: TaskStatus, val key: StatusKey)

@Serializable
data class TombstoneReg(val ver: Ver)

/** The four task registers (spec section 4.1). Stored as JSON in tasks.registers and sent as-is on the wire. */
@Serializable
data class TaskRegisters(
    val content: ContentReg,
    val assignee: AssigneeReg,
    val tombstone: TombstoneReg? = null,
    val status: StatusReg? = null
) {
    /** Newest timestamp across all registers. */
    fun maxTs(): Long = versions().maxOf { it.ts }

    /** The most recent change of any kind (by timestamp, then author). Used for sorting and "last updated by". */
    fun lastChange(): Ver = versions().maxWith(compareBy<Ver>({ it.ts }, { it.by }))

    /** A timestamp for a local write that is strictly newer than everything already recorded. */
    fun nextTs(now: Long): Long = maxOf(now, maxTs() + 1)

    /** Naive status projection: a newer status register beats the snapshot. No validity check yet. */
    fun projectedStatus(): TaskStatus {
        val s = status
        return if (s != null && s.key > assignee.snapshot.key) s.value else assignee.snapshot.value
    }

    // ----- local-write constructors (the writer is the local user; rules and authorization come later) -----

    fun withContent(title: String, description: String, ver: Ver) =
        copy(content = ContentReg(title, description, ver))

    /** A reassignment records the status the writer currently sees as the snapshot. */
    fun withAssignee(id: String?, ver: Ver, currentStatus: TaskStatus) =
        copy(assignee = AssigneeReg(id, ver, StatusSnapshot(currentStatus, latestStatusKey())))

    fun withStatus(value: TaskStatus, ver: Ver) =
        copy(status = StatusReg(value, StatusKey(assignee.ver, ver)))

    /** Deletion is sticky: an existing tombstone is kept. */
    fun withTombstone(ver: Ver) = if (tombstone != null) this else copy(tombstone = TombstoneReg(ver))

    private fun latestStatusKey(): StatusKey = listOfNotNull(status?.key, assignee.snapshot.key).max()

    private fun versions(): List<Ver> =
        listOfNotNull(content.ver, assignee.ver, tombstone?.ver, status?.key?.ver)

    companion object {
        /** A brand-new task. The initial status lives in the snapshot; no status register is written yet. */
        fun create(title: String, description: String, assigneeId: String?, status: TaskStatus, ver: Ver) =
            TaskRegisters(
                content = ContentReg(title, description, ver),
                assignee = AssigneeReg(assigneeId, ver, StatusSnapshot(status, StatusKey(ver, ver)))
            )
    }
}

/** JSON used for the registers and leader-chain database columns. */
object ModelJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val chainSerializer = ListSerializer(LeaderEntry.serializer())

    fun encodeRegisters(r: TaskRegisters): String = json.encodeToString(TaskRegisters.serializer(), r)
    fun decodeRegisters(s: String): TaskRegisters = json.decodeFromString(TaskRegisters.serializer(), s)

    fun encodeChain(c: List<LeaderEntry>): String = json.encodeToString(chainSerializer, c)
    fun decodeChain(s: String): List<LeaderEntry> = json.decodeFromString(chainSerializer, s)
}

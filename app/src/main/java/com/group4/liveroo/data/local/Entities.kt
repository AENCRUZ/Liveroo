package com.group4.liveroo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus { TODO, IN_PROGRESS, DONE }

/**
 * dirty = true means "Not yet synced" (P7). It is set on every local change and
 * cleared after a sync that both phones confirmed.
 *
 * leaderChain is the JSON list of (leaderId, at) described in docs/MERGE_SPEC.md section 2;
 * leaderId is always its last entry (kept as a plain column for queries and screens).
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val leaderId: String,
    val leaderChain: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean = true,
    /** Local only, never synced: I left this group and my departure hasn't synced yet (C21). */
    val leftByMe: Boolean = false
)

@Entity(tableName = "members", primaryKeys = ["groupId", "userId"])
data class MemberEntity(
    val groupId: String,
    val userId: String,
    val displayName: String,
    /** When this person was added (or re-added) to the group. Task assignments older than this don't apply. */
    val joinedAt: Long,
    /** Set when the member leaves (C21). Other phones unassign their tasks when they receive it. */
    val leftAt: Long? = null,
    val updatedAt: Long,
    val dirty: Boolean = true
)

/**
 * The flat columns (title, description, assigneeId, status, deleted, updatedAt, updatedBy) are the
 * materialized current values, always derived from [registers] (JSON of TaskRegisters), which is what syncs.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val description: String,
    val assigneeId: String?,   // null = Unassigned (C12)
    val status: TaskStatus,
    val updatedAt: Long,
    val updatedBy: String,
    /** Soft delete so the deletion can sync to other phones. */
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val registers: String
)

/** Remembers which Bluetooth device belongs to which groupmate (T4), so lists can show Liveroo names. */
@Entity(tableName = "known_devices")
data class KnownDeviceEntity(
    @PrimaryKey val address: String,
    val userId: String,
    val displayName: String
)

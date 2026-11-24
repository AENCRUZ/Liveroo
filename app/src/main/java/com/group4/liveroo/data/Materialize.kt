package com.group4.liveroo.data

import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskEntity
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.rules.GroupView
import com.group4.liveroo.rules.MemberState
import com.group4.liveroo.rules.effectiveTask
import com.group4.liveroo.sync.MemberDto

fun MemberEntity.toState() = MemberState(userId, displayName, joinedAt, leftAt, updatedAt)

fun MemberDto.toState() = MemberState(userId, displayName, joinedAt, leftAt, updatedAt)

/**
 * The stored row for a task: the registers (what syncs) plus the flat columns the screens read, which are the
 * task's effective values under [chain] and the group's members (docs/MERGE_SPEC.md 4.4). Every write, local or
 * received, builds its row here, so the flat columns can never drift from the registers.
 */
fun TaskRegisters.materialize(
    id: String,
    groupId: String,
    dirty: Boolean,
    chain: List<LeaderEntry>,
    member: (String) -> MemberState?
): TaskEntity {
    val effective = effectiveTask(this, chain, member)
    val last = lastChange()
    return TaskEntity(
        id = id,
        groupId = groupId,
        title = effective.title,
        description = effective.description,
        assigneeId = effective.assigneeId,
        status = effective.status,
        updatedAt = last.ts,
        updatedBy = last.by,
        deleted = effective.deleted,
        dirty = dirty,
        registers = ModelJson.encodeRegisters(this)
    )
}

fun TaskRegisters.materialize(id: String, groupId: String, dirty: Boolean, view: GroupView): TaskEntity =
    materialize(id, groupId, dirty, view.chain) { view.member(it) }

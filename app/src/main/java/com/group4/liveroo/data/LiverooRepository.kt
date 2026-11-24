package com.group4.liveroo.data

import androidx.room.withTransaction
import com.group4.liveroo.data.local.GroupEntity
import com.group4.liveroo.data.local.KnownDeviceEntity
import com.group4.liveroo.data.local.LiverooDatabase
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskEntity
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.Ver
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.data.prefs.IdentityStore
import com.group4.liveroo.rules.Auth
import com.group4.liveroo.rules.Authz
import com.group4.liveroo.rules.ChainMerge
import com.group4.liveroo.rules.ChainRejection
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.rules.EffectiveTask
import com.group4.liveroo.rules.GroupSyncView
import com.group4.liveroo.rules.GroupView
import com.group4.liveroo.rules.InviteDecision
import com.group4.liveroo.rules.MemberState
import com.group4.liveroo.rules.TaskContext
import com.group4.liveroo.rules.TaskMerge
import com.group4.liveroo.rules.TransferError
import com.group4.liveroo.rules.TransferProposal
import com.group4.liveroo.rules.TransferVerdict
import com.group4.liveroo.rules.effectiveTask
import com.group4.liveroo.rules.classifyInvite
import com.group4.liveroo.rules.validateTransfer
import com.group4.liveroo.rules.mergeChains
import com.group4.liveroo.rules.mergeMember
import com.group4.liveroo.rules.mergeTask
import com.group4.liveroo.sync.DataMsg
import com.group4.liveroo.sync.GroupDto
import com.group4.liveroo.sync.MemberDto
import com.group4.liveroo.sync.toDto
import com.group4.liveroo.sync.toEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Everything the screens and the sync code read or write goes through here. */
class LiverooRepository(
    private val db: LiverooDatabase,
    private val identity: IdentityStore
) {
    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()

    /** This phone's view of a group for the authorization rules, or null if the group isn't here. */
    private suspend fun groupView(groupId: String): GroupView? {
        val g = db.groups().get(groupId) ?: return null
        val members = db.members().getAll(groupId).map { it.toState() }
        return GroupView(g.chain(), members, writable = !g.leftByMe)
    }

    private fun GroupView.effective(task: TaskEntity): EffectiveTask =
        effectiveTask(task.regs(), chain) { member(it) }

    private fun denied(reason: DenyReason) = WriteResult.Denied(reason)

    private fun Auth.deniedOrNull(): WriteResult.Denied? = (this as? Auth.Denied)?.let { denied(it.reason) }

    /**
     * Rebuilds a group's task rows after its members or leadership changed on this phone. The flat columns are
     * derived (effective assignee, status, deleted), so they must follow. The "Not yet synced" flag is kept.
     */
    private suspend fun rematerialize(groupId: String) {
        val view = groupView(groupId) ?: return
        for (task in db.tasks().getAllForGroup(groupId)) {
            val row = task.regs().materialize(task.id, groupId, task.dirty, view)
            if (row != task) db.tasks().upsert(row)
        }
    }

    /** Removes a group and everything in it from this phone only. Nothing is queued for sync. */
    private suspend fun hardDeleteGroup(groupId: String) {
        db.tasks().deleteForGroup(groupId)
        db.members().deleteForGroup(groupId)
        db.groups().delete(groupId)
    }

    // ---------- Reads (Flows update the UI automatically) ----------
    val groups: Flow<List<GroupEntity>> = db.groups().observeAll()
    val allMembers: Flow<List<MemberEntity>> = db.members().observeAllMembers()
    val allTasks: Flow<List<TaskEntity>> = db.tasks().observeAllTasks()
    val unsyncedCount: Flow<Int> = db.tasks().observeUnsyncedCount()
    val knownDevices: Flow<List<KnownDeviceEntity>> = db.devices().observeAll()

    fun group(id: String): Flow<GroupEntity?> = db.groups().observe(id)
    fun members(groupId: String): Flow<List<MemberEntity>> = db.members().observeActive(groupId)
    fun tasks(groupId: String): Flow<List<TaskEntity>> = db.tasks().observeForGroup(groupId)
    fun task(id: String): Flow<TaskEntity?> = db.tasks().observe(id)
    fun tasksAssignedTo(userId: String): Flow<List<TaskEntity>> = db.tasks().observeAssignedTo(userId)

    // ---------- Local writes (each marks the row "Not yet synced") ----------

    /** C1: anyone can create a group and becomes its leader. */
    suspend fun createGroup(name: String): String {
        val (me, myName) = identity.me()
        val id = newId()
        val t = now()
        db.withTransaction {
            db.groups().upsert(
                GroupEntity(
                    id, name.trim(), leaderId = me,
                    leaderChain = ModelJson.encodeChain(listOf(LeaderEntry(me, t))),
                    createdAt = t, updatedAt = t
                )
            )
            db.members().upsert(MemberEntity(id, me, myName, joinedAt = t, updatedAt = t))
        }
        return id
    }

    /**
     * C5, D2, D3: create or edit a task. Leader only, in a writable group; the assignee must be an active member;
     * a deleted task can't be edited. Refused writes change nothing.
     */
    suspend fun saveTask(
        groupId: String, taskId: String?, title: String, description: String,
        assigneeId: String?, status: TaskStatus
    ): WriteResult<String> {
        val (me, _) = identity.me()
        return db.withTransaction<WriteResult<String>> {
            val view = groupView(groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.saveTask(view, me, assigneeId).deniedOrNull()?.let { return@withTransaction it }
            val id = taskId ?: newId()
            val existing = taskId?.let { db.tasks().get(it) }
            if (existing != null) {
                if (existing.groupId != groupId) return@withTransaction denied(DenyReason.TASK_NOT_FOUND)
                if (view.effective(existing).deleted) return@withTransaction denied(DenyReason.TASK_DELETED)
            }
            val epoch = view.epoch
            val newTitle = title.trim()
            val newDescription = description.trim()
            if (existing == null) {
                val ver = Ver(epoch, now(), me)
                val regs = TaskRegisters.create(newTitle, newDescription, assigneeId, status, ver)
                db.tasks().upsert(regs.materialize(id, groupId, dirty = true, view))
                return@withTransaction WriteResult.Ok(id)
            }
            // Edit: only the registers that actually changed get a new version.
            var regs = existing.regs()
            val ver = Ver(epoch, regs.nextTs(now()), me)
            val before = regs
            if (regs.content.title != newTitle || regs.content.description != newDescription) {
                regs = regs.withContent(newTitle, newDescription, ver)
            }
            if (existing.assigneeId != assigneeId) regs = regs.withAssignee(assigneeId, ver, existing.status)
            if (existing.status != status) regs = regs.withStatus(status, ver)
            if (regs != before) db.tasks().upsert(regs.materialize(id, groupId, dirty = true, view))
            WriteResult.Ok(id)
        }
    }

    /** C8 / C10 / D2: the leader on any task, the assigned member on their own task, nobody else. */
    suspend fun setStatus(taskId: String, status: TaskStatus): WriteResult<Unit> {
        val (me, _) = identity.me()
        return db.withTransaction<WriteResult<Unit>> {
            val task = db.tasks().get(taskId) ?: return@withTransaction denied(DenyReason.TASK_NOT_FOUND)
            val view = groupView(task.groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.setStatus(view, me, view.effective(task)).deniedOrNull()?.let { return@withTransaction it }
            val regs = task.regs()
            val ver = Ver(view.epoch, regs.nextTs(now()), me)
            db.tasks().upsert(regs.withStatus(status, ver).materialize(task.id, task.groupId, dirty = true, view))
            WriteResult.Ok(Unit)
        }
    }

    /** P1: leader only. Soft delete so it syncs. Deleting an already deleted task does nothing. */
    suspend fun deleteTask(taskId: String): WriteResult<Unit> {
        val (me, _) = identity.me()
        return db.withTransaction<WriteResult<Unit>> {
            val task = db.tasks().get(taskId) ?: return@withTransaction denied(DenyReason.TASK_NOT_FOUND)
            val view = groupView(task.groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.deleteTask(view, me).deniedOrNull()?.let { return@withTransaction it }
            val regs = task.regs()
            val ver = Ver(view.epoch, regs.nextTs(now()), me)
            db.tasks().upsert(regs.withTombstone(ver).materialize(task.id, task.groupId, dirty = true, view))
            WriteResult.Ok(Unit)
        }
    }

    /**
     * C21, D1: record the departure locally. The group stays read-only until a sync delivers it.
     * A leader must transfer leadership first.
     */
    suspend fun leaveGroup(groupId: String): WriteResult<Unit> {
        val (me, _) = identity.me()
        val t = now()
        return db.withTransaction<WriteResult<Unit>> {
            val view = groupView(groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.leaveGroup(view, me).deniedOrNull()?.let { return@withTransaction it }
            db.members().get(groupId, me)?.let {
                db.members().upsert(it.copy(leftAt = t, updatedAt = t, dirty = true))
            }
            db.groups().get(groupId)?.let { db.groups().upsert(it.copy(leftByMe = true)) }
            rematerialize(groupId)
            WriteResult.Ok(Unit)
        }
    }

    /**
     * N5: the sole-member leader removes the group from this phone only. Nothing is sent or queued, so other
     * phones that hold the group keep their copy. Anything not yet synced is lost.
     */
    suspend fun deleteGroupLocally(groupId: String): WriteResult<Unit> {
        val (me, _) = identity.me()
        return db.withTransaction<WriteResult<Unit>> {
            val view = groupView(groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.deleteGroupLocally(view, me).deniedOrNull()?.let { return@withTransaction it }
            hardDeleteGroup(groupId)
            WriteResult.Ok(Unit)
        }
    }

    /** Why I may not invite people to this group, or null if I may (leader only, group writable). */
    suspend fun inviteDenial(groupId: String): DenyReason? {
        val (me, _) = identity.me()
        val view = groupView(groupId) ?: return DenyReason.GROUP_NOT_FOUND
        return (Authz.addMember(view, me) as? Auth.Denied)?.reason
    }

    /** Leader adds an invited member (C4, C17). Refused for anyone but the group's leader. */
    suspend fun addMember(groupId: String, userId: String, displayName: String, at: Long? = null): WriteResult<Unit> {
        val (me, _) = identity.me()
        val t = at ?: now()
        return db.withTransaction<WriteResult<Unit>> {
            val view = groupView(groupId) ?: return@withTransaction denied(DenyReason.GROUP_NOT_FOUND)
            Authz.addMember(view, me).deniedOrNull()?.let { return@withTransaction it }
            // Already active: leave the row alone, so their join time (and so their assignments) is kept.
            if (view.isActive(userId)) return@withTransaction WriteResult.Ok(Unit)
            db.members().upsert(MemberEntity(groupId, userId, displayName, joinedAt = t, updatedAt = t))
            rematerialize(groupId)
            WriteResult.Ok(Unit)
        }
    }

    // ---------- Leadership transfer (spec 2.3) ----------

    /** The group's current leadership epoch on this phone, or null if the group isn't here. */
    suspend fun groupEpoch(groupId: String): Int? = groupView(groupId)?.epoch

    /**
     * Judges a handover from this phone's view of the group. The initiator and the responder call this with the same
     * facts, so they reach the same verdict. [consented] is false only when asking whether consent is still missing.
     */
    suspend fun transferVerdict(
        senderId: String, proposal: TransferProposal, responderId: String, consented: Boolean
    ): TransferVerdict = validateTransfer(groupView(proposal.groupId), senderId, proposal, responderId, consented)

    private class TransferRefusedException(val error: TransferError) : RuntimeException()

    /**
     * The one place a finished sync is applied, in a single transaction: merge what arrived, clear "Not yet synced"
     * for the [confirmed] groups, and, for a handover, append the new leader to the chain. The handover is checked
     * again against the merged data; if it no longer holds, everything above is rolled back and nothing changes.
     * Both phones append the identical entry (the proposal's new leader and timestamp).
     */
    suspend fun commitSync(
        incoming: DataMsg, myId: String, allowedGroupIds: Set<String>,
        confirmed: List<String>, snapshotAt: Long, transfer: TransferCommit?
    ): CommitResult = try {
        db.withTransaction<CommitResult> {
            val merge = merge(incoming, myId, allowedGroupIds)
            markClean(confirmed, snapshotAt)
            if (transfer != null) {
                val p = transfer.proposal
                val verdict = validateTransfer(groupView(p.groupId), transfer.senderId, p, transfer.responderId, true)
                if (verdict is TransferVerdict.Invalid) throw TransferRefusedException(verdict.error)
                val chain = (verdict as TransferVerdict.Valid).newChain
                val group = db.groups().get(p.groupId)!!
                db.groups().upsert(
                    group.copy(
                        leaderId = chain.last().id, leaderChain = ModelJson.encodeChain(chain),
                        updatedAt = maxOf(group.updatedAt, p.at), dirty = true     // third phones haven't heard yet
                    )
                )
                rematerialize(p.groupId)
            }
            CommitResult.Done(merge)
        }
    } catch (e: TransferRefusedException) {
        CommitResult.TransferRefused(e.error)
    }

    suspend fun rememberDevice(address: String, userId: String, displayName: String) {
        db.devices().upsert(KnownDeviceEntity(address, userId, displayName))
    }

    // ---------- Sync support ----------

    suspend fun localGroupIds(): List<String> = db.groups().getAll().map { it.id }

    suspend fun groupName(groupId: String): String? = db.groups().get(groupId)?.name

    suspend fun export(groupIds: List<String>): DataMsg {
        val groups = groupIds.mapNotNull { db.groups().get(it) }
        val members = groupIds.flatMap { db.members().getAll(it) }
        val tasks = groupIds.flatMap { db.tasks().getAllForGroup(it) }
        return DataMsg(groups.map { it.toDto() }, members.map { it.toDto() }, tasks.map { it.toDto() })
    }

    /** How each of my groups is seen by the sync scope rules (D4): its members and whether I have left it. */
    suspend fun groupSyncViews(): List<GroupSyncView> = db.groups().getAll().map { g ->
        GroupSyncView(g.id, db.members().getAll(g.id).map { it.toState() }, g.leftByMe)
    }

    /**
     * Merges what a groupmate sent (docs/MERGE_SPEC.md sections 2-4). Only groups in [allowedGroupIds] are looked at;
     * everything else in the message is refused. Per group: members first, then the leadership chain (whole-chain
     * order, never mixed), then each task register by register. The task rows the screens read are then rebuilt
     * from the merged registers, so a member leaving unassigns their tasks on every phone without any write of
     * its own. Nothing is created for a group this phone doesn't already hold.
     * Returns plain-language notes for the Sync result screen.
     */
    suspend fun merge(incoming: DataMsg, myId: String, allowedGroupIds: Set<String>): MergeResult {
        val notes = mutableListOf<String>()
        var changed = 0
        var dropped = 0
        db.withTransaction {
            dropped += incoming.groups.count { it.id !in allowedGroupIds }
            dropped += incoming.members.count { it.groupId !in allowedGroupIds }
            dropped += incoming.tasks.count { it.groupId !in allowedGroupIds }

            for (gid in allowedGroupIds.sorted()) {
                val local = db.groups().get(gid) ?: continue
                val dto = incoming.groups.firstOrNull { it.id == gid }
                val incomingMembers = incoming.members.filter { it.groupId == gid }
                val incomingTasks = incoming.tasks.filter { it.groupId == gid }

                // 1. Members, merged in memory so the chain check below sees everyone.
                val members = db.members().getAll(gid).associateBy { it.userId }.toMutableMap()
                val memberWrites = mutableListOf<Pair<MemberEntity, MemberEntity?>>()   // (new row, previous row)
                for (m in incomingMembers) {
                    val previous = members[m.userId]
                    if (mergeMember(previous?.toState(), m.toState()) != previous?.toState()) {
                        val row = m.toEntity()
                        members[m.userId] = row
                        memberWrites += row to previous
                    }
                }

                // 2. Leadership chain.
                val localChain = local.chain()
                val chainResult =
                    if (dto == null) ChainMerge.Kept(localChain) else mergeChains(localChain, dto.leaderChain, members.keys)
                if (chainResult is ChainMerge.Rejected && chainResult.reason != ChainRejection.LEADER_NOT_A_MEMBER) {
                    dropped += 1 + incomingMembers.size + incomingTasks.size       // not the same group: ignore it all
                    continue
                }
                if (chainResult is ChainMerge.Rejected) dropped++                   // keep my chain, still merge the rest
                val chain = when (chainResult) {
                    is ChainMerge.Adopted -> chainResult.chain
                    is ChainMerge.Kept -> chainResult.chain
                    is ChainMerge.Rejected -> localChain
                }
                var group = local
                if (chainResult is ChainMerge.Adopted) {
                    group = local.copy(
                        leaderId = chain.last().id, leaderChain = ModelJson.encodeChain(chain),
                        updatedAt = maxOf(local.updatedAt, dto!!.updatedAt), dirty = false
                    )
                    changed++
                    if (group.leaderId != local.leaderId) {
                        val leaderName = members[group.leaderId]?.displayName ?: "Someone"
                        notes += if (group.leaderId == myId) "You're now the leader of ${group.name}."
                        else "$leaderName is now the leader of ${group.name}."
                    }
                }

                // The leader took me back after I left: the group is mine to use again.
                if (local.leftByMe && memberWrites.any { it.first.userId == myId && it.first.leftAt == null }) {
                    group = group.copy(leftByMe = false)
                    notes += "You're an active member of ${group.name} again."
                }

                // 3. Tasks, register by register.
                val ctx = TaskContext(chain, members.keys)
                val tasks = db.tasks().getAllForGroup(gid).associateBy { it.id }
                val received = mutableMapOf<String, TaskRegisters>()
                for (t in incomingTasks) {
                    val existing = tasks[t.id]
                    if (existing == null && t.id !in received && db.tasks().get(t.id) != null) {
                        dropped++                                                   // that id belongs to another group
                        continue
                    }
                    when (val r = mergeTask(received[t.id] ?: existing?.regs(), t.registers, ctx)) {
                        is TaskMerge.Rejected -> dropped++
                        is TaskMerge.Merged -> {
                            dropped += r.dropped
                            if (r.changed) received[t.id] = r.registers
                        }
                    }
                }

                // 4. Rebuild every task row from its registers under the final chain and members.
                val lookup: (String) -> MemberState? = { members[it]?.toState() }
                val finalTasks = mutableMapOf<String, TaskEntity>()
                val taskWrites = mutableListOf<TaskEntity>()
                for (id in tasks.keys + received.keys) {
                    val before = tasks[id]
                    val regs = received[id] ?: before!!.regs()
                    val dirty = if (id in received) false else before!!.dirty
                    val row = regs.materialize(id, gid, dirty, chain, lookup)
                    finalTasks[id] = row
                    if (row != before) taskWrites += row
                }
                changed += received.size

                // 5. Notes for the result screen.
                for ((row, previous) in memberWrites) {
                    changed++
                    if (previous == null && row.leftAt == null && row.userId != myId) {
                        notes += "${row.displayName} joined ${group.name}."
                    }
                    if (row.leftAt != null && previous?.leftAt == null && row.userId != myId) {
                        // C21: the departing member's tasks are now unassigned, derived from their departure.
                        val count = tasks.values.count {
                            !it.deleted && it.assigneeId == row.userId && finalTasks[it.id]?.assigneeId != row.userId
                        }
                        notes += "${row.displayName} left ${group.name}. " +
                            "$count ${if (count == 1) "task is" else "tasks are"} now unassigned."
                    }
                }

                if (group != local) db.groups().upsert(group)
                memberWrites.forEach { db.members().upsert(it.first) }
                taskWrites.forEach { db.tasks().upsert(it) }
            }
        }
        return MergeResult(changed, notes, dropped)
    }

    /** After both phones confirmed: clear "Not yet synced" for what was sent. */
    suspend fun markClean(groupIds: List<String>, upTo: Long) {
        if (groupIds.isEmpty()) return
        db.withTransaction {
            db.groups().markClean(groupIds, upTo)
            db.members().markClean(groupIds, upTo)
            db.tasks().markClean(groupIds, upTo)
        }
    }

    /**
     * C21: once my departure has synced, remove the group from this phone. Returns the removed names.
     * Only for a group I really am out of: if the leader has taken me back (my own member row is active again),
     * nothing is removed.
     */
    suspend fun finishDepartures(groupIds: List<String>): List<String> {
        val (me, _) = identity.me()
        val removed = mutableListOf<String>()
        db.withTransaction {
            for (id in groupIds) {
                val g = db.groups().get(id) ?: continue
                val mine = db.members().get(id, me)
                if (g.leftByMe && (mine == null || mine.leftAt != null)) {
                    hardDeleteGroup(id)
                    removed += g.name
                }
            }
        }
        return removed
    }

    /** Names of the groups from [groupIds] that this phone holds, by id (for result messages). */
    suspend fun groupNames(groupIds: List<String>): Map<String, String> =
        groupIds.mapNotNull { id -> db.groups().get(id)?.let { id to it.name } }.toMap()

    private sealed interface InviteCheck {
        data class Problem(val text: String) : InviteCheck
        data class Valid(val group: GroupDto, val tasks: Map<String, TaskRegisters>) : InviteCheck
    }

    /** Checks a received invite payload as a whole: nothing is stored unless all of it makes sense. */
    private fun checkInvite(data: DataMsg, me: String): InviteCheck {
        val group = data.groups.singleOrNull()
            ?: return InviteCheck.Problem("The invitation didn't contain exactly one group.")
        if (data.members.any { it.groupId != group.id } || data.tasks.any { it.groupId != group.id }) {
            return InviteCheck.Problem("The invitation mixed in data from another group.")
        }
        val memberIds = data.members.map { it.userId }.toSet()
        val mine = data.members.firstOrNull { it.userId == me }
        if (mine == null || mine.leftAt != null) {
            return InviteCheck.Problem("You aren't listed as a member of this group.")
        }
        if (mergeChains(null, group.leaderChain, memberIds) !is ChainMerge.Adopted) {
            return InviteCheck.Problem("The group's leadership record is invalid.")
        }
        val ctx = TaskContext(group.leaderChain, memberIds)
        val tasks = mutableMapOf<String, TaskRegisters>()
        for (t in data.tasks) {
            val merged = mergeTask(null, t.registers, ctx) as? TaskMerge.Merged
                ?: return InviteCheck.Problem("A task wasn't written by the group's leader.")
            tasks[t.id] = merged.registers
        }
        return InviteCheck.Valid(group, tasks)
    }

    /** Why a received invite payload can't be trusted, or null if it is fine. Checked before the invitee agrees. */
    suspend fun invitePayloadProblem(data: DataMsg): String? {
        val (me, _) = identity.me()
        return (checkInvite(data, me) as? InviteCheck.Problem)?.text
    }

    /** How this phone treats an invitation to [groupId] from [inviterId] (D9, N2). Decided before the person is asked. */
    suspend fun inviteDecision(groupId: String, inviterId: String): InviteDecision {
        val (me, _) = identity.me()
        return classifyInvite(groupView(groupId), me, inviterId)
    }

    /**
     * The group data for an invitation: the group as I hold it, plus the invitee's member row built in memory. I
     * only write that row myself once the invitee confirms it has stored the group (D8), using the same [joinedAt],
     * so a failed or abandoned invitation leaves no ghost member behind.
     */
    suspend fun invitePayload(groupId: String, inviteeId: String, inviteeName: String, joinedAt: Long): DataMsg {
        val data = export(listOf(groupId))
        if (data.members.any { it.userId == inviteeId && it.leftAt == null }) return data   // already listed as active
        val row = MemberDto(groupId, inviteeId, inviteeName, joinedAt = joinedAt, leftAt = null, updatedAt = joinedAt)
        return data.copy(members = data.members.filter { it.userId != inviteeId } + row)
    }

    /**
     * Invitee side: store the group data received with an accepted invite, after checking it as a whole.
     * Refused if this phone already has the group (D9). Returns why it was refused (nothing stored), or null when
     * it was imported.
     */
    suspend fun importInvite(data: DataMsg): String? {
        val (me, _) = identity.me()
        val check = checkInvite(data, me)
        if (check is InviteCheck.Problem) return check.text
        check as InviteCheck.Valid
        val states = data.members.associate { it.userId to it.toState() }
        return db.withTransaction<String?> {
            if (db.groups().get(check.group.id) != null) return@withTransaction ALREADY_HAS_GROUP_MESSAGE
            db.groups().upsert(check.group.toEntity())
            data.members.forEach { db.members().upsert(it.toEntity()) }
            for ((id, regs) in check.tasks) {
                db.tasks().upsert(regs.materialize(id, check.group.id, false, check.group.leaderChain) { states[it] })
            }
            null
        }
    }
}

/** Shown on the invited phone (and returned by the import) when it already has the group (D9). */
const val ALREADY_HAS_GROUP_MESSAGE = "You already have this group on this phone."

data class MergeResult(val changed: Int, val notes: List<String>, val dropped: Int = 0)

/** A handover to apply together with a sync commit: who sent it, who answered, and what was agreed. */
data class TransferCommit(val proposal: TransferProposal, val senderId: String, val responderId: String)

sealed interface CommitResult {
    data class Done(val merge: MergeResult) : CommitResult

    /** The handover no longer held against the merged data: nothing was changed, not even the merge. */
    data class TransferRefused(val error: TransferError) : CommitResult
}

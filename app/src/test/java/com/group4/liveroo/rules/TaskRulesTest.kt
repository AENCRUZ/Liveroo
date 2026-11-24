package com.group4.liveroo.rules

import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.Ver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {
    private val chain = listOf(LeaderEntry("L", 0))
    private val ids = setOf("L", "A", "B")
    private val ctx = TaskContext(chain, ids)
    private fun m(id: String, joined: Long = 0, left: Long? = null) = MemberState(id, id, joined, left, 0)
    private val members = ids.associateWith { m(it) }
    private val lookup: (String) -> MemberState? = { members[it] }

    /** Task led by L, assigned to A, created at t=10. */
    private val base = TaskRegisters.create("T", "", "A", TaskStatus.TODO, Ver(0, 10, "L"))

    private fun merged(local: TaskRegisters?, incoming: TaskRegisters, c: TaskContext = ctx) =
        (mergeTask(local, incoming, c) as TaskMerge.Merged)

    private fun status(r: TaskRegisters) = effectiveTask(r, chain, lookup).status

    // ----- effective assignee -----

    @Test
    fun assignee_activeMemberCounts_unassignedOrUnknownDoNot() {
        assertEquals("A", effectiveAssignee(base, lookup))
        assertNull(effectiveAssignee(base.withAssignee(null, Ver(0, 11, "L"), TaskStatus.TODO), lookup))
        assertNull(effectiveAssignee(base.withAssignee("ghost", Ver(0, 11, "L"), TaskStatus.TODO), lookup))
    }

    @Test
    fun assignee_whoLeft_isNoLongerAssigned() {
        assertNull(effectiveAssignee(base) { m("A", left = 50) })
    }

    @Test
    fun assignee_reAddedMember_doesNotGetOldAssignmentsBack() {
        // Assigned at t=10; A was re-added at t=100 (leftAt cleared) -> the old assignment doesn't apply.
        assertNull(effectiveAssignee(base) { m("A", joined = 100) })
        // A new assignment after the re-add does apply.
        val again = base.withAssignee("A", Ver(0, 101, "L"), TaskStatus.TODO)
        assertEquals("A", effectiveAssignee(again) { m("A", joined = 100) })
    }

    // ----- effective status (N6 Option A) -----

    @Test
    fun status_withoutRegister_isTheSnapshot() {
        assertEquals(TaskStatus.TODO, status(base))
    }

    @Test
    fun status_assigneeAndLeaderWritesCount() {
        assertEquals(TaskStatus.DONE, status(base.withStatus(TaskStatus.DONE, Ver(0, 11, "A"))))
        assertEquals(TaskStatus.IN_PROGRESS, status(base.withStatus(TaskStatus.IN_PROGRESS, Ver(0, 11, "L"))))
    }

    @Test
    fun status_byAnotherMember_revertsToTheSnapshot() {
        assertEquals(TaskStatus.TODO, status(base.withStatus(TaskStatus.DONE, Ver(0, 11, "B"))))
    }

    @Test
    fun status_writtenWithoutSeeingAReassignment_isDiscarded() {
        val leaderSide = base.withAssignee("B", Ver(0, 11, "L"), TaskStatus.TODO)      // never saw A's status
        val memberSide = base.withStatus(TaskStatus.DONE, Ver(0, 12, "A"))
        val both = merged(leaderSide, memberSide).registers
        assertEquals(TaskStatus.TODO, status(both))                                    // A is no longer the assignee
        assertEquals(both, merged(memberSide, leaderSide).registers)                   // same in the other order
    }

    @Test
    fun status_theLeaderHadAlreadySeen_isKeptAfterReassignment() {
        val seen = base.withStatus(TaskStatus.DONE, Ver(0, 11, "A"))
            .withAssignee("B", Ver(0, 12, "L"), currentStatus = TaskStatus.DONE)
        assertEquals(TaskStatus.DONE, status(seen))
    }

    @Test
    fun status_theNewAssigneesWrite_outranksTheOldAssigneesLateOne() {
        val reassigned = base.withAssignee("B", Ver(0, 12, "L"), TaskStatus.TODO)
        val byNew = reassigned.withStatus(TaskStatus.DONE, Ver(0, 13, "B"))
        val byOldLate = base.withStatus(TaskStatus.IN_PROGRESS, Ver(0, 99, "A"))       // later clock, older assignment
        val all = merged(merged(reassigned, byNew).registers, byOldLate).registers
        assertEquals(TaskStatus.DONE, status(all))
        val reverse = merged(merged(byOldLate, byNew).registers, reassigned).registers
        assertEquals(all, reverse)
    }

    // ----- deleted -----

    @Test
    fun deleted_requiresAValidTombstone() {
        assertTrue(isDeleted(base.withTombstone(Ver(0, 20, "L")), chain))
        assertFalse(isDeleted(base.withTombstone(Ver(0, 20, "B")), chain))             // a member can't delete
        assertFalse(isDeleted(base, chain))
        // Valid only for the chain it was written under (a tainted tombstone does not delete).
        assertFalse(isDeleted(base.withTombstone(Ver(1, 20, "N2")), listOf(LeaderEntry("L", 0), LeaderEntry("N1", 5))))
    }

    // ----- merge: creation and refusals -----

    @Test
    fun newTask_isCreatedOnlyFromValidLeaderVersionsByMembers() {
        assertTrue(mergeTask(null, base, ctx) is TaskMerge.Merged)
        val byMember = TaskRegisters.create("T", "", null, TaskStatus.TODO, Ver(0, 10, "B"))
        assertEquals(TaskMerge.Rejected(TaskRejection.INVALID_FIRST_VERSION), mergeTask(null, byMember, ctx))
        val byStranger = TaskRegisters.create("T", "", null, TaskStatus.TODO, Ver(0, 10, "Z"))
        assertEquals(TaskMerge.Rejected(TaskRejection.INVALID_FIRST_VERSION), mergeTask(null, byStranger, ctx))
    }

    @Test
    fun forgedLeaderVersions_byAMember_areDropped() {
        val forged = base.withContent("Hacked", "", Ver(0, 999, "B"))
            .withAssignee("B", Ver(0, 999, "B"), TaskStatus.DONE)
            .withTombstone(Ver(0, 999, "B"))
        val r = merged(base, forged)
        assertEquals(base, r.registers)
        assertEquals(3, r.dropped)
        assertFalse(r.changed)
    }

    @Test
    fun statusByANonMember_isDropped_butAnyMembersIsStored() {
        val stranger = base.withStatus(TaskStatus.DONE, Ver(0, 11, "Z"))
        assertEquals(1, merged(base, stranger).dropped)
        assertNull(merged(base, stranger).registers.status)
        val member = base.withStatus(TaskStatus.DONE, Ver(0, 11, "B"))
        assertEquals(member.status, merged(base, member).registers.status)             // stored; effective view ignores it
    }

    @Test
    fun merge_isIdempotent() {
        val r = merged(base, base)
        assertFalse(r.changed)
        assertEquals(0, r.dropped)
        assertEquals(base, r.registers)
    }

    // ----- merge: independent registers -----

    @Test
    fun renameAndStatus_bothSurvive_inEitherOrder() {
        val rename = base.withContent("Renamed", "", Ver(0, 20, "L"))
        val progress = base.withStatus(TaskStatus.DONE, Ver(0, 25, "A"))
        val ab = merged(rename, progress).registers
        val ba = merged(progress, rename).registers
        assertEquals(ab, ba)
        assertEquals("Renamed", ab.content.title)
        assertEquals(TaskStatus.DONE, status(ab))
    }

    @Test
    fun deletion_isSticky_andWinsOverALaterEdit() {
        val deleted = base.withTombstone(Ver(0, 20, "L"))
        val laterEdit = base.withContent("Edited", "", Ver(0, 30, "L"))
        val r = merged(deleted, laterEdit).registers
        assertTrue(isDeleted(r, chain))
        assertEquals(r, merged(laterEdit, deleted).registers)
    }

    // ----- forks: tainted versions -----

    private val chainA = listOf(LeaderEntry("L", 0), LeaderEntry("N1", 1))   // wins the fork
    private val forkCtx = TaskContext(chainA, setOf("L", "N1", "N2", "N3"))
    private val origin = TaskRegisters.create("T", "", null, TaskStatus.TODO, Ver(0, 1, "L"))

    @Test
    fun fork_aValidVersionReplacesATaintedOne_onBothPhones() {
        // X wrote under the losing chain (N2), Y under the winning chain (N1); both now know chain A.
        val x = origin.withContent("From N2", "", Ver(1, 5, "N2"))
        val y = origin.withContent("From N1", "", Ver(1, 4, "N1"))
        val xAfter = merged(x, y, forkCtx).registers
        val yAfter = merged(y, x, forkCtx).registers
        assertEquals(xAfter, yAfter)
        assertEquals("From N1", xAfter.content.title)
    }

    @Test
    fun fork_twoTaintedValues_settleOnTheSameOne() {
        val x = origin.withContent("From N2", "", Ver(1, 5, "N2"))
        val y = origin.withContent("From N3", "", Ver(1, 7, "N3"))
        val xAfter = merged(x, y, forkCtx).registers
        val yAfter = merged(y, x, forkCtx).registers
        assertEquals(xAfter, yAfter)
    }

    @Test
    fun fork_aTaintedTombstone_doesNotDelete() {
        val tainted = origin.withTombstone(Ver(1, 5, "N2"))
        assertFalse(isDeleted(tainted, chainA))
    }
}

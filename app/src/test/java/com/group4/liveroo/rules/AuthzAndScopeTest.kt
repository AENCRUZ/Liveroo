package com.group4.liveroo.rules

import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthzAndScopeTest {
    private fun m(id: String, left: Long? = null) = MemberState(id, id, 0, left, 0)
    private val group = GroupView(
        chain = listOf(LeaderEntry("L", 0)),
        members = listOf(m("L"), m("A"), m("B"), m("C", left = 9)),
        writable = true
    )
    private fun task(assignee: String? = "A", deleted: Boolean = false) =
        EffectiveTask("T", "", assignee, TaskStatus.TODO, deleted)

    private val ok = Auth.Allowed
    private fun no(r: DenyReason) = Auth.Denied(r)

    // ----- authorization matrix -----

    @Test
    fun saveTaskAndDeleteTask_areLeaderOnly() {
        assertEquals(ok, Authz.saveTask(group, "L", "A"))
        assertEquals(ok, Authz.saveTask(group, "L", null))
        assertEquals(no(DenyReason.NOT_LEADER), Authz.saveTask(group, "A", "A"))
        assertEquals(no(DenyReason.NOT_A_MEMBER), Authz.saveTask(group, "C", null))        // left
        assertEquals(no(DenyReason.NOT_A_MEMBER), Authz.saveTask(group, "stranger", null))
        assertEquals(ok, Authz.deleteTask(group, "L"))
        assertEquals(no(DenyReason.NOT_LEADER), Authz.deleteTask(group, "B"))
    }

    @Test
    fun assignee_mustBeAnActiveMember() {
        assertEquals(no(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER), Authz.saveTask(group, "L", "C"))
        assertEquals(no(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER), Authz.saveTask(group, "L", "stranger"))
    }

    @Test
    fun setStatus_leaderOnAnyTask_assigneeOnOwn_nobodyElse() {
        assertEquals(ok, Authz.setStatus(group, "L", task("A")))
        assertEquals(ok, Authz.setStatus(group, "L", task(null)))
        assertEquals(ok, Authz.setStatus(group, "A", task("A")))
        assertEquals(no(DenyReason.NOT_ASSIGNEE_OR_LEADER), Authz.setStatus(group, "B", task("A")))
        assertEquals(no(DenyReason.NOT_ASSIGNEE_OR_LEADER), Authz.setStatus(group, "A", task(null)))
        assertEquals(no(DenyReason.TASK_DELETED), Authz.setStatus(group, "L", task(deleted = true)))
        assertEquals(no(DenyReason.TASK_NOT_FOUND), Authz.setStatus(group, "L", null))
        assertEquals(no(DenyReason.NOT_A_MEMBER), Authz.setStatus(group, "C", task("C")))
    }

    @Test
    fun readOnlyGroup_deniesEveryWrite() {
        val ro = group.copy(writable = false)
        val denied = no(DenyReason.GROUP_NOT_WRITABLE)
        assertEquals(denied, Authz.saveTask(ro, "L", null))
        assertEquals(denied, Authz.deleteTask(ro, "L"))
        assertEquals(denied, Authz.addMember(ro, "L"))
        assertEquals(denied, Authz.setStatus(ro, "L", task()))
        assertEquals(denied, Authz.leaveGroup(ro, "A"))
        assertEquals(denied, Authz.deleteGroupLocally(ro, "L"))
    }

    @Test
    fun addMember_isLeaderOnly() {
        assertEquals(ok, Authz.addMember(group, "L"))
        assertEquals(no(DenyReason.NOT_LEADER), Authz.addMember(group, "A"))
    }

    @Test
    fun leaveGroup_membersMay_leaderMustTransferFirst() {
        assertEquals(ok, Authz.leaveGroup(group, "A"))
        assertEquals(no(DenyReason.LEADER_MUST_TRANSFER), Authz.leaveGroup(group, "L"))
        assertEquals(no(DenyReason.NOT_A_MEMBER), Authz.leaveGroup(group, "C"))
    }

    @Test
    fun deleteGroupLocally_onlyASoleMemberLeader() {
        val sole = group.copy(members = listOf(m("L"), m("C", left = 9)))
        assertEquals(ok, Authz.deleteGroupLocally(sole, "L"))
        assertEquals(no(DenyReason.OTHER_MEMBERS_PRESENT), Authz.deleteGroupLocally(group, "L"))
        assertEquals(no(DenyReason.NOT_LEADER), Authz.deleteGroupLocally(sole.copy(members = listOf(m("L"), m("A"))), "A"))
    }

    // ----- sync scope (D4, N1, N9) -----

    private fun view(id: String, vararg members: MemberState, leftByMe: Boolean = false) =
        GroupSyncView(id, members.toList(), leftByMe)

    @Test
    fun scope_activePeer_isOrdinary() {
        val s = classifySyncScope(listOf(view("g1", m("me"), m("peer"))), "peer", listOf("g1"))
        assertEquals(listOf("g1"), s.ordinary)
        assertTrue(s.departureOnly.isEmpty() && s.skipped.isEmpty())
    }

    @Test
    fun scope_peerNotInMyData_isSkipped() {
        val s = classifySyncScope(listOf(view("g1", m("me"))), "peer", listOf("g1"))
        assertEquals(listOf("g1"), s.skipped)
        assertTrue(s.ordinary.isEmpty())
    }

    @Test
    fun scope_peerWhoHasLeftInMyData_isDepartureOnly() {
        val s = classifySyncScope(listOf(view("g1", m("me"), m("peer", left = 7))), "peer", listOf("g1"))
        assertEquals(listOf("g1"), s.departureOnly)
        assertTrue(s.ordinary.isEmpty() && s.skipped.isEmpty())
    }

    @Test
    fun scope_groupsNotHeldByBothAreNotShared() {
        val s = classifySyncScope(listOf(view("g1", m("me"), m("peer")), view("g2", m("me"), m("peer"))), "peer", listOf("g2", "g9"))
        assertEquals(listOf("g2"), s.ordinary)
        assertTrue(s.skipped.isEmpty() && s.departureOnly.isEmpty())
    }

    @Test
    fun scope_resultsAreSorted_andMixedGroupsClassifyIndependently() {
        val mine = listOf(
            view("c", m("me"), m("peer")),
            view("a", m("me")),
            view("b", m("me"), m("peer", left = 1)),
            view("d", m("me"), m("peer"))
        )
        val s = classifySyncScope(mine, "peer", listOf("d", "c", "b", "a"))
        assertEquals(listOf("c", "d"), s.ordinary)
        assertEquals(listOf("b"), s.departureOnly)
        assertEquals(listOf("a"), s.skipped)
    }

    @Test
    fun skippedGroupMessage_isTheApprovedWording() {
        assertEquals(
            "This group wasn't synced because the other device doesn't recognize you as an active member. " +
                "Sync with the group leader first.",
            GROUP_SKIPPED_MESSAGE
        )
    }
}

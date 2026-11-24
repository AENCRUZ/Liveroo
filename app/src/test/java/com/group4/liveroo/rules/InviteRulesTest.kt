package com.group4.liveroo.rules

import com.group4.liveroo.data.model.LeaderEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** D9 and N2 as a truth table. */
class InviteRulesTest {
    private fun m(id: String, left: Long? = null) = MemberState(id, id, 0, left, 0)
    private fun group(
        chain: List<LeaderEntry> = listOf(LeaderEntry("lead", 0)),
        members: List<MemberState> = listOf(m("lead"), m("me")),
        writable: Boolean = true
    ) = GroupView(chain, members, writable)

    @Test
    fun aGroupIDontHave_isAcceptedAfterAsking() {
        assertEquals(InviteDecision.ACCEPT_NEW, classifyInvite(null, "me", "lead"))
    }

    @Test
    fun anInvitationFromTheCurrentLeader_toAnActiveMember_justCompletesIt() {
        assertEquals(InviteDecision.COMPLETE_EXISTING, classifyInvite(group(), "me", "lead"))
    }

    @Test
    fun everyOtherWayOfAlreadyHavingTheGroup_isRefused() {
        val refused = InviteDecision.REJECT_ALREADY_HAS_GROUP
        // from someone who isn't the group's current leader on this phone
        assertEquals(refused, classifyInvite(group(chain = listOf(LeaderEntry("lead", 0), LeaderEntry("ann", 5))), "me", "lead"))
        assertEquals(refused, classifyInvite(group(), "me", "stranger"))
        // I'm not an active member in my own data (missing, or left)
        assertEquals(refused, classifyInvite(group(members = listOf(m("lead"))), "me", "lead"))
        assertEquals(refused, classifyInvite(group(members = listOf(m("lead"), m("me", left = 9))), "me", "lead"))
        // I have left it and the departure hasn't synced yet
        assertEquals(refused, classifyInvite(group(writable = false), "me", "lead"))
        // the sender is the leader of record but isn't an active member in my data
        assertEquals(refused, classifyInvite(group(members = listOf(m("lead", left = 3), m("me"))), "me", "lead"))
    }
}

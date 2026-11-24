package com.group4.liveroo.rules

import com.group4.liveroo.data.model.LeaderEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Random

class TransferAndMemberRulesTest {
    private fun m(id: String, joined: Long = 0, left: Long? = null, updated: Long = 0, name: String = id) =
        MemberState(id, name, joined, left, updated)

    private val chain = listOf(LeaderEntry("L", 0))
    private val group = GroupView(chain, listOf(m("L"), m("N"), m("X", left = 5)), writable = true)
    private val proposal = TransferProposal("g", newLeaderId = "N", at = 100, expectedEpoch = 0)

    private fun verdict(
        g: GroupView? = group, sender: String = "L", p: TransferProposal = proposal,
        responder: String = "N", consented: Boolean = true
    ) = validateTransfer(g, sender, p, responder, consented)

    private fun error(v: TransferVerdict) = (v as TransferVerdict.Invalid).error

    // ----- transfer validation -----

    @Test
    fun validTransfer_appendsTheSharedEntry() {
        val v = verdict() as TransferVerdict.Valid
        assertEquals(chain + LeaderEntry("N", 100), v.newChain)
    }

    @Test
    fun initiatorAndResponder_reachTheSameVerdict() {
        // Both evaluate identical facts from identical group data.
        assertEquals(verdict(), verdict())
        val stale = verdict(p = proposal.copy(expectedEpoch = 3))
        assertEquals(stale, verdict(p = proposal.copy(expectedEpoch = 3)))
    }

    @Test
    fun eachRuleRejectsWithItsOwnReason() {
        assertEquals(TransferError.GROUP_NOT_SHARED, error(verdict(g = null)))
        assertEquals(TransferError.GROUP_NOT_WRITABLE, error(verdict(g = group.copy(writable = false))))
        assertEquals(TransferError.SENDER_NOT_LEADER, error(verdict(sender = "N")))
        assertEquals(TransferError.STALE_EPOCH, error(verdict(p = proposal.copy(expectedEpoch = 1))))
        assertEquals(TransferError.NEW_LEADER_IS_SENDER, error(verdict(p = proposal.copy(newLeaderId = "L"), responder = "L")))
        assertEquals(TransferError.NEW_LEADER_NOT_ACTIVE_MEMBER, error(verdict(p = proposal.copy(newLeaderId = "X"), responder = "X")))
        assertEquals(TransferError.NEW_LEADER_NOT_ACTIVE_MEMBER, error(verdict(p = proposal.copy(newLeaderId = "ghost"), responder = "ghost")))
        assertEquals(TransferError.RESPONDER_NOT_NEW_LEADER, error(verdict(responder = "L")))
        assertEquals(TransferError.NOT_CONSENTED, error(verdict(consented = false)))
    }

    @Test
    fun senderWhoHasLeft_isNotALeader() {
        val g = group.copy(members = listOf(m("L", left = 9), m("N")))
        assertEquals(TransferError.SENDER_NOT_LEADER, error(verdict(g = g)))
    }

    // ----- member rows -----

    @Test
    fun member_greaterUpdatedAtWins() {
        val old = m("a", updated = 1, name = "Old")
        val new = m("a", updated = 2, name = "New")
        assertEquals(new, mergeMember(old, new))
        assertEquals(new, mergeMember(new, old))
        assertEquals(new, mergeMember(null, new))
    }

    @Test
    fun member_tieBreaksOnLeftThenName() {
        val active = m("a", updated = 5, name = "Zed")
        val left = m("a", updated = 5, left = 4, name = "Amy")
        assertEquals(left, mergeMember(active, left))          // "has left" outranks "active"
        assertEquals(left, mergeMember(left, active))
        val amy = m("a", updated = 5, name = "Amy")
        val zed = m("a", updated = 5, name = "Zed")
        assertEquals(zed, mergeMember(amy, zed))
        assertEquals(zed, mergeMember(zed, amy))
    }

    @Test
    fun member_mergeIsCommutativeAssociativeAndIdempotent() {
        val rng = Random(3)
        fun random() = m(
            "a", joined = rng.nextInt(3).toLong(), left = if (rng.nextBoolean()) rng.nextInt(4).toLong() else null,
            updated = rng.nextInt(4).toLong(), name = listOf("A", "B")[rng.nextInt(2)]
        )
        repeat(3_000) {
            val a = random(); val b = random(); val c = random()
            assertEquals(mergeMember(a, b), mergeMember(b, a))
            assertEquals(mergeMember(mergeMember(a, b), c), mergeMember(a, mergeMember(b, c)))
            assertEquals(a, mergeMember(a, a))
        }
    }

    @Test
    fun member_identicalRowKeepsTheLocalInstance() {
        val a = m("a", updated = 1)
        assertSame(a, mergeMember(a, a.copy()))
    }
}

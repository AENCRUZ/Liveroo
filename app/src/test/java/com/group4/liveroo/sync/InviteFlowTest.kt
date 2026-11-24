package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.ALREADY_HAS_GROUP_MESSAGE
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.testsupport.SpyChannel
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.isWireMessage
import com.group4.liveroo.testsupport.joinFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Step 6 (D8, D9, N2): the invitee stores the group before it confirms, the leader writes the new member only after
 * that confirmation, an invitation to a group the invitee already has is refused, and the one exception completes a
 * membership the leader never recorded. Raw peers on the other end let the tests see exactly who has committed what
 * at each moment.
 */
@RunWith(RobolectricTestRunner::class)
class InviteFlowTest {
    private lateinit var lead: TestPhone
    private lateinit var eve: TestPhone
    private lateinit var gid: String

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            lead = TestPhone(context, "lead-id", "Lena")
            eve = TestPhone(context, "eve-id", "Eve")
            gid = lead.repo.createGroup("Study")
            lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).assertOk()
        }
    }

    @After
    fun tearDown() {
        lead.close(); eve.close()
    }

    private class Raw(val channel: SyncChannel) {
        val input = DataInputStream(channel.input)
        val out = DataOutputStream(channel.output)
    }

    /** The real leader invites; the other end is a raw invitee that answers as told and then runs [afterData]. */
    private suspend fun leaderInvitesRawInvitee(
        answer: InviteAnswer = InviteAnswer(true),
        afterData: suspend (Raw, DataMsg) -> Unit = { raw, _ -> Wire.send(raw.out, Ack(true)) }
    ): InviteOutcome = coroutineScope {
        val channels = connectedChannels()
        val invitee = async(Dispatchers.IO) {
            val raw = Raw(channels.responderSide)
            Wire.receive(raw.input) as Hello
            Wire.send(raw.out, newHello("eve-id", "Eve", emptyList(), "invite"))
            Wire.receive(raw.input) as InviteOffer
            Wire.send(raw.out, answer)
            if (answer.accept) afterData(raw, Wire.receive(raw.input) as DataMsg)
        }
        val outcome = lead.sync.initiateInvite(channels.initiatorSide, gid)
        invitee.await()
        outcome
    }

    /** The real invitee answers; the other end is a raw leader that offers [offerGroup] as [inviterId]. */
    private suspend fun rawLeaderInvitesEve(
        inviterId: String = "lead-id",
        offerGroup: String = gid,
        eveChannel: (SyncChannel) -> SyncChannel = { it },
        allowInvite: suspend (InviteOffer) -> Boolean = { error("the person must not be asked") },
        afterAnswer: suspend (Raw, InviteAnswer) -> Unit = { _, _ -> }
    ): Pair<InviteAnswer, Any> = coroutineScope {
        val channels = connectedChannels()
        val leader = async(Dispatchers.IO) {
            val raw = Raw(channels.initiatorSide)
            Wire.send(raw.out, newHello(inviterId, "Lena", emptyList(), "invite"))
            Wire.receive(raw.input) as Hello
            Wire.send(raw.out, InviteOffer(offerGroup, "Study", "Lena"))
            val answer = Wire.receive(raw.input) as InviteAnswer
            afterAnswer(raw, answer)
            answer
        }
        val outcome = eve.sync.respond(eveChannel(channels.responderSide), allowSync = { false }, allowInvite = allowInvite)
        leader.await() to outcome
    }

    /** Sends the invitation payload the way the real leader would, then reads Eve's acknowledgement. */
    private suspend fun sendPayloadAndReadAck(raw: Raw): Ack {
        Wire.send(raw.out, lead.repo.invitePayload(gid, "eve-id", "Eve", System.currentTimeMillis()))
        return Wire.receive(raw.input) as Ack
    }

    private suspend fun eveHoldsTheGroup() {
        lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
        assertNull(eve.joinFrom(lead, gid))
    }

    // ----- D8: the leader commits only after the invitee has -----

    @Test
    fun theLeader_writesTheMember_onlyAfterTheInviteeConfirms() = runBlocking {
        var payload: DataMsg? = null
        var memberWhileWaiting: MemberEntity? = null
        var observed = false

        val outcome = leaderInvitesRawInvitee { raw, data ->
            payload = data
            memberWhileWaiting = lead.db.members().get(gid, "eve-id")        // the leader is waiting for my confirmation now
            observed = true
            Wire.send(raw.out, Ack(true))
        }

        assertTrue(observed)
        assertNull("no ghost member before the confirmation", memberWhileWaiting)
        val sentRow = payload!!.members.single { it.userId == "eve-id" }     // the group data already lists Eve...
        assertNull(sentRow.leftAt)
        assertEquals(sentRow.joinedAt, sentRow.updatedAt)
        assertTrue("leader: $outcome", outcome is InviteOutcome.Joined)
        val stored = lead.db.members().get(gid, "eve-id")!!                  // ...and the leader wrote the same row after
        assertEquals(sentRow.joinedAt, stored.joinedAt)
        assertEquals(sentRow.updatedAt, stored.updatedAt)
        assertNull(stored.leftAt)
    }

    @Test
    fun anInviteeWhoNeverConfirms_leavesNoGhostMember() = runBlocking {
        val outcome = leaderInvitesRawInvitee { raw, _ -> raw.channel.close() }      // takes the data, vanishes
        assertTrue("leader: $outcome", outcome is InviteOutcome.Failed)
        assertNull(lead.db.members().get(gid, "eve-id"))
    }

    @Test
    fun anInviteeWhoRefusesTheData_leavesNoGhostMember() = runBlocking {
        val outcome = leaderInvitesRawInvitee { raw, _ -> Wire.send(raw.out, Ack(false, 0, "no")) }
        assertEquals(InviteOutcome.Failed("That phone couldn't accept the group data."), outcome)
        assertNull(lead.db.members().get(gid, "eve-id"))
    }

    @Test
    fun theInvitee_storesTheGroup_beforeItConfirms() = runBlocking {
        var ack: Ack? = null
        var storedAtTheMomentOfSending: Boolean? = null

        // Looks at Eve's database at the exact moment her confirmation is about to leave her phone.
        val spy = { channel: SyncChannel ->
            SpyChannel(channel) { bytes ->
                if (bytes.isWireMessage("ack")) storedAtTheMomentOfSending = runBlocking { eve.db.groups().get(gid) != null }
            }
        }
        val (answer, outcome) = rawLeaderInvitesEve(eveChannel = spy, allowInvite = { true }) { raw, a ->
            if (a.accept) ack = sendPayloadAndReadAck(raw)
        }

        assertTrue(answer.accept)
        assertEquals(true, ack?.ok)
        assertEquals("the group must already be stored when the confirmation is sent", true, storedAtTheMomentOfSending)
        assertTrue("invitee: $outcome", outcome is InviteOutcome.Joined)
    }

    // ----- N2: completing an invitation whose last message was lost -----

    @Test
    fun aLostLastMessage_isRecoveredByInvitingAgain_withoutAskingOrOverwriting() = runBlocking {
        // First invitation: Eve stores the group and confirms, but the leader never hears it (raw leader, no commit).
        val (_, first) = rawLeaderInvitesEve(allowInvite = { true }) { raw, a -> if (a.accept) sendPayloadAndReadAck(raw) }
        assertTrue("$first", first is InviteOutcome.Joined)
        assertNull("the leader never committed", lead.db.members().get(gid, "eve-id"))
        assertNotNull(eve.db.groups().get(gid))

        // Eve has a change of her own that must survive.
        eve.db.members().upsert(eve.db.members().get(gid, "eve-id")!!.copy(dirty = true))
        val groupBefore = eve.db.groups().get(gid)
        val tasksBefore = eve.db.tasks().getAllForGroup(gid)

        // The leader invites again. Eve already has the group, the offer comes from its leader, she is listed active.
        val outcome = coroutineScope {
            val channels = connectedChannels()
            val invitee = async(Dispatchers.IO) {
                eve.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { error("she must not be asked again") })
            }
            val leader = lead.sync.initiateInvite(channels.initiatorSide, gid)
            leader to invitee.await()
        }

        assertTrue("leader: ${outcome.first}", outcome.first is InviteOutcome.Joined)
        assertTrue("invitee: ${outcome.second}", outcome.second is InviteOutcome.Joined)
        assertNull(lead.db.members().get(gid, "eve-id")!!.leftAt)                    // now recorded
        assertEquals(groupBefore, eve.db.groups().get(gid))                          // nothing overwritten
        assertEquals(tasksBefore, eve.db.tasks().getAllForGroup(gid))
        assertTrue(eve.db.members().get(gid, "eve-id")!!.dirty)
    }

    // ----- D9: refused when the invitee already has the group -----

    private suspend fun assertRefusedAlreadyHasGroup() {
        val (answer, outcome) = rawLeaderInvitesEve()                                // asking the person would throw
        assertFalse(answer.accept)
        assertEquals(INVITE_REASON_ALREADY_HAS_GROUP, answer.reason)
        assertEquals(InviteOutcome.Failed(ALREADY_HAS_GROUP_MESSAGE), outcome)
    }

    @Test
    fun aGroupLedBySomeoneElseOnThisPhone_isRefused() = runBlocking {
        eveHoldsTheGroup()
        val g = eve.db.groups().get(gid)!!
        eve.db.groups().upsert(
            g.copy(leaderId = "ann-id", leaderChain = ModelJson.encodeChain(g.chain() + LeaderEntry("ann-id", g.createdAt + 5)))
        )
        assertRefusedAlreadyHasGroup()                                               // "lead-id" is no longer the leader here
    }

    @Test
    fun aGroupIHaveLeftButNotYetSynced_isRefused() = runBlocking {
        eveHoldsTheGroup()
        eve.repo.leaveGroup(gid).assertOk()
        assertRefusedAlreadyHasGroup()
    }

    @Test
    fun aGroupWhereIAmNotListedActive_isRefused() = runBlocking {
        eveHoldsTheGroup()
        val row = eve.db.members().get(gid, "eve-id")!!
        eve.db.members().upsert(row.copy(leftAt = row.updatedAt + 1))
        assertRefusedAlreadyHasGroup()
    }

    @Test
    fun theLeader_seesARefusal_asAFailure_notAsADecline() = runBlocking {
        val refused = leaderInvitesRawInvitee(InviteAnswer(false, INVITE_REASON_ALREADY_HAS_GROUP))
        assertEquals(InviteOutcome.Failed("Eve already has this group."), refused)
        assertEquals(InviteOutcome.Declined, leaderInvitesRawInvitee(InviteAnswer(false)))
        assertNull(lead.db.members().get(gid, "eve-id"))
    }

    @Test
    fun importInvite_itself_refusesAGroupAlreadyHere() = runBlocking {
        eveHoldsTheGroup()
        val before = eve.db.groups().get(gid)
        assertEquals(ALREADY_HAS_GROUP_MESSAGE, eve.repo.importInvite(lead.repo.export(listOf(gid))))
        assertEquals(before, eve.db.groups().get(gid))
    }

    // ----- the payload and the deferred member write -----

    @Test
    fun invitePayload_addsTheInviteeInMemoryOnly() = runBlocking {
        val payload = lead.repo.invitePayload(gid, "eve-id", "Eve", joinedAt = 7_000)
        val row = payload.members.single { it.userId == "eve-id" }
        assertEquals(7_000L, row.joinedAt)
        assertEquals(7_000L, row.updatedAt)
        assertNull(row.leftAt)
        assertNull(lead.db.members().get(gid, "eve-id"))                             // nothing was written
    }

    @Test
    fun invitePayload_forAnActiveMember_isTheUnchangedExport() = runBlocking {
        lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
        assertEquals(lead.repo.export(listOf(gid)), lead.repo.invitePayload(gid, "eve-id", "Eve", joinedAt = 9_999))
    }

    @Test
    fun invitePayload_forAFormerMember_replacesTheOldRow() = runBlocking {
        lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
        val row = lead.db.members().get(gid, "eve-id")!!
        lead.db.members().upsert(row.copy(leftAt = row.updatedAt + 1))
        val payload = lead.repo.invitePayload(gid, "eve-id", "Eve", joinedAt = row.updatedAt + 50)
        val eves = payload.members.filter { it.userId == "eve-id" }
        assertEquals(1, eves.size)
        assertNull(eves.single().leftAt)
        assertEquals(row.updatedAt + 50, eves.single().joinedAt)
    }

    @Test
    fun addMember_canUseTheAgreedTimestamp() = runBlocking {
        lead.repo.addMember(gid, "eve-id", "Eve", at = 4_242).assertOk()
        val row = lead.db.members().get(gid, "eve-id")!!
        assertEquals(4_242L, row.joinedAt)
        assertEquals(4_242L, row.updatedAt)
    }
}

package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.CommitResult
import com.group4.liveroo.data.TransferCommit
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.rules.TransferError
import com.group4.liveroo.rules.TransferProposal
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.denial
import com.group4.liveroo.testsupport.id
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
import java.io.EOFException

/**
 * Step 5: leadership handovers over the real handshake (D6, D7). The request travels in the first message, the new
 * leader is asked before any data moves, both phones judge it the same way, and both end with the identical entry.
 */
@RunWith(RobolectricTestRunner::class)
class TransferHandshakeTest {
    private lateinit var lead: TestPhone
    private lateinit var ann: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var gid: String

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            lead = TestPhone(context, "lead-id", "Lena")
            ann = TestPhone(context, "ann-id", "Ann")
            bob = TestPhone(context, "bob-id", "Bob")
            gid = lead.repo.createGroup("Study")
            lead.repo.addMember(gid, "ann-id", "Ann").assertOk()
            lead.repo.addMember(gid, "bob-id", "Bob").assertOk()
            assertNull(ann.joinFrom(lead, gid))
            assertNull(bob.joinFrom(lead, gid))
        }
    }

    @After
    fun tearDown() {
        lead.close(); ann.close(); bob.close()
    }

    /** Runs one handover; [asked] collects every question the responder was shown. */
    private suspend fun handover(
        initiator: TestPhone, intent: TransferIntent, responder: TestPhone,
        agree: Boolean, asked: MutableList<TransferOffer> = mutableListOf()
    ): Pair<SyncOutcome, Any> = coroutineScope {
        val channels = connectedChannels()
        val resp = async(Dispatchers.IO) {
            responder.sync.respond(
                channels.responderSide, allowSync = { true }, allowInvite = { false },
                allowTransfer = { asked += it; agree }
            )
        }
        val init = initiator.sync.initiateSync(channels.initiatorSide, intent)
        init to resp.await()
    }

    private suspend fun assertNothingChanged(phone: TestPhone) {
        val group = phone.db.groups().get(gid)!!
        assertEquals(listOf("lead-id"), group.chain().map { it.id })
        assertEquals("lead-id", group.leaderId)
    }

    // ----- the happy path -----

    @Test
    fun anAgreedHandover_givesBothPhonesTheIdenticalEntry_andMovesLeadership() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        val asked = mutableListOf<TransferOffer>()

        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = true, asked = asked)

        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertEquals(listOf(TransferOffer("Lena", gid, "Study")), asked)                  // D6: asked exactly once, by name
        val leadGroup = lead.db.groups().get(gid)!!
        val annGroup = ann.db.groups().get(gid)!!
        assertEquals("ann-id", leadGroup.leaderId)
        assertEquals("ann-id", annGroup.leaderId)
        assertEquals(leadGroup.chain(), annGroup.chain())                                  // same entries, same timestamp
        assertEquals(2, leadGroup.chain().size)
        assertEquals("ann-id", leadGroup.chain().last().id)
        assertEquals((init as SyncOutcome.Success).transferDone!!.at, leadGroup.chain().last().at)
        assertEquals((init).transferDone!!.expectedEpoch, 0)

        assertEquals("Ann is now the leader of Study.", init.notes.first())
        assertEquals("You're now the leader of Study.", (resp as SyncOutcome.Success).notes.first())

        assertEquals("Read", ann.db.tasks().get(tid)!!.title)                             // ordinary data travelled too
        assertTrue(leadGroup.dirty && annGroup.dirty)                                      // Bob hasn't heard yet
    }

    @Test
    fun afterTheHandover_theNewLeaderLeads_theOldOneIsAMember_andMayLeave() = runBlocking {
        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = true)
        assertTrue("$init / $resp", init is SyncOutcome.Success)

        ann.repo.saveTask(gid, null, "From Ann", "", null, TaskStatus.TODO).assertOk()
        assertEquals(DenyReason.NOT_LEADER, lead.repo.saveTask(gid, null, "Nope", "", null, TaskStatus.TODO).denial())
        assertEquals(DenyReason.NOT_LEADER, lead.repo.addMember(gid, "eve-id", "Eve").denial())
        lead.repo.leaveGroup(gid).assertOk()                                               // D1 is satisfied now
    }

    // ----- consent (D6) and the intended person (D7) -----

    @Test
    fun aDeclinedHandover_changesNothing_onEitherPhone_andMovesNoData() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        val asked = mutableListOf<TransferOffer>()

        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = false, asked = asked)

        assertEquals(SyncOutcome.TransferDeclined, init)
        assertEquals(SyncOutcome.TransferDeclined, resp)
        assertEquals(1, asked.size)
        assertNothingChanged(lead); assertNothingChanged(ann)
        assertNull(ann.db.tasks().get(tid))                                                // no data moved before consent
        assertTrue(lead.db.tasks().get(tid)!!.dirty)
    }

    @Test
    fun theWrongPhone_isRefusedBeforeItIsEverAsked() = runBlocking {
        val asked = mutableListOf<TransferOffer>()

        // Lena meant Ann, but Bob's phone answered.
        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), bob, agree = true, asked = asked)

        assertTrue("nobody should be asked: $asked", asked.isEmpty())
        assertEquals(SyncOutcome.Failed(TransferError.RESPONDER_NOT_NEW_LEADER.message(false), wasTransfer = true), init)
        assertEquals(SyncOutcome.Failed(TransferError.RESPONDER_NOT_NEW_LEADER.message(true), wasTransfer = true), resp)
        assertNothingChanged(lead); assertNothingChanged(bob)
    }

    // ----- both phones judge the same facts -----

    @Test
    fun aStaleView_isRefusedByTheResponder_beforeAnyQuestion() = runBlocking {
        // Ann's copy is already at epoch 2 (with Lena leading again), Lena's still at epoch 0.
        val g = ann.db.groups().get(gid)!!
        val longer = listOf(LeaderEntry("lead-id", g.createdAt), LeaderEntry("bob-id", g.createdAt + 5), LeaderEntry("lead-id", g.createdAt + 9))
        ann.db.groups().upsert(g.copy(leaderChain = ModelJson.encodeChain(longer)))
        val asked = mutableListOf<TransferOffer>()

        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = true, asked = asked)

        assertTrue(asked.isEmpty())
        assertEquals(SyncOutcome.Failed(TransferError.STALE_EPOCH.message(false), true), init)
        assertTrue(resp is SyncOutcome.Failed)
        assertNothingChanged(lead)
        assertEquals(3, ann.db.groups().get(gid)!!.chain().size)
    }

    @Test
    fun aResponderWhoDoesNotHoldTheGroup_refuses() = runBlocking {
        ann.db.groups().delete(gid)
        val asked = mutableListOf<TransferOffer>()
        val (init, resp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = true, asked = asked)
        assertTrue(asked.isEmpty())
        assertEquals(SyncOutcome.Failed(TransferError.GROUP_NOT_SHARED.message(false), true), init)
        assertEquals(SyncOutcome.Failed(TransferError.GROUP_NOT_SHARED.message(true), true), resp)
        assertNothingChanged(lead)
    }

    @Test
    fun aNonLeader_cannotStartAHandover_andNothingIsSent() = runBlocking {
        val channels = connectedChannels()
        val peer = async(Dispatchers.IO) { runCatching { Wire.receive(DataInputStream(channels.responderSide.input)) } }
        val outcome = ann.sync.initiateSync(channels.initiatorSide, TransferIntent(gid, "bob-id"))
        assertEquals(SyncOutcome.Failed(TransferError.SENDER_NOT_LEADER.message(false), true), outcome)
        assertTrue(peer.await().exceptionOrNull() is EOFException)
    }

    @Test
    fun aMemberWhoLeft_cannotBeChosen_andNothingIsSent() = runBlocking {
        val row = lead.db.members().get(gid, "ann-id")!!
        lead.db.members().upsert(row.copy(leftAt = row.updatedAt + 1))
        val channels = connectedChannels()
        val peer = async(Dispatchers.IO) { runCatching { Wire.receive(DataInputStream(channels.responderSide.input)) } }
        val outcome = lead.sync.initiateSync(channels.initiatorSide, TransferIntent(gid, "ann-id"))
        assertEquals(SyncOutcome.Failed(TransferError.NEW_LEADER_NOT_ACTIVE_MEMBER.message(false), true), outcome)
        assertTrue(peer.await().exceptionOrNull() is EOFException)
    }

    @Test
    fun handingOverToYourself_orAnUnknownGroup_isRefusedLocally() = runBlocking {
        val self = lead.sync.initiateSync(connectedChannels().initiatorSide, TransferIntent(gid, "lead-id"))
        assertEquals(SyncOutcome.Failed(TransferError.NEW_LEADER_IS_SENDER.message(false), true), self)
        val unknown = lead.sync.initiateSync(connectedChannels().initiatorSide, TransferIntent("nope", "ann-id"))
        assertEquals(SyncOutcome.Failed(TransferError.GROUP_NOT_SHARED.message(false), true), unknown)
    }

    // ----- the commit is all-or-nothing -----

    @Test
    fun commitSync_rollsBackTheWholeSync_whenTheHandoverNoLongerHolds() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        val incoming = lead.repo.export(listOf(gid))
        val stale = TransferProposal(gid, "ann-id", at = 5_000, expectedEpoch = 7)

        val result = ann.repo.commitSync(
            incoming, "ann-id", setOf(gid), listOf(gid), Long.MAX_VALUE,
            TransferCommit(stale, senderId = "lead-id", responderId = "ann-id")
        )

        assertEquals(CommitResult.TransferRefused(TransferError.STALE_EPOCH), result)
        assertNull(ann.db.tasks().get(tid))                                                // the merge was rolled back too
        assertNothingChanged(ann)
    }

    @Test
    fun commitSync_appliesAValidHandoverTogetherWithTheMerge() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        val incoming = lead.repo.export(listOf(gid))
        val agreed = TransferProposal(gid, "ann-id", at = 5_000, expectedEpoch = 0)

        val result = ann.repo.commitSync(
            incoming, "ann-id", setOf(gid), listOf(gid), Long.MAX_VALUE,
            TransferCommit(agreed, senderId = "lead-id", responderId = "ann-id")
        )

        assertTrue(result is CommitResult.Done)
        assertNotNull(ann.db.tasks().get(tid))
        val group = ann.db.groups().get(gid)!!
        assertEquals(LeaderEntry("ann-id", 5_000), group.chain().last())
        assertEquals("ann-id", group.leaderId)
        assertTrue(group.dirty)                                                            // not cleared by the same commit
        assertFalse(ann.db.tasks().get(tid)!!.dirty)
    }

    // ----- competing handovers (spec 2.5): a lost final message, then a second handover from the stale view -----

    @Test
    fun competingHandovers_settleOnOneChain_onEveryPhone() = runBlocking {
        // Lena hands over to Ann; the handover completes on Ann's phone.
        val (first, firstResp) = handover(lead, TransferIntent(gid, "ann-id"), ann, agree = true)
        assertTrue("$first / $firstResp", first is SyncOutcome.Success && firstResp is SyncOutcome.Success)
        // ...but the last message never reached Lena's phone, so she still believes she leads (epoch 0).
        val g = lead.db.groups().get(gid)!!
        lead.db.groups().upsert(g.copy(leaderId = "lead-id", leaderChain = ModelJson.encodeChain(g.chain().take(1))))
        // From that stale view she hands over to Bob as well.
        val (second, secondResp) = handover(lead, TransferIntent(gid, "bob-id"), bob, agree = true)
        assertTrue("$second / $secondResp", second is SyncOutcome.Success && secondResp is SyncOutcome.Success)

        val annsChain = ann.db.groups().get(gid)!!.chain()
        val bobsChain = bob.db.groups().get(gid)!!.chain()
        assertEquals(listOf("lead-id", "ann-id"), annsChain.map { it.id })
        assertEquals(listOf("lead-id", "bob-id"), bobsChain.map { it.id })                // a fork: same epoch, two leaders

        // Everyone syncs with everyone: the smaller timestamp wins (then the smaller id), the same way everywhere.
        val phones = listOf(lead, ann, bob)
        repeat(3) { for (i in phones.indices) for (j in i + 1 until phones.size) com.group4.liveroo.testsupport.exchangeState(phones[i], phones[j]) }

        val expected = if (compareChainsOf(annsChain, bobsChain) > 0) annsChain else bobsChain
        for (p in phones) {
            assertEquals(expected, p.db.groups().get(gid)!!.chain())
            assertEquals(expected.last().id, p.db.groups().get(gid)!!.leaderId)
        }
        // Whoever lost can no longer lead; whoever won can.
        val winner = if (expected.last().id == "ann-id") ann else bob
        val loser = if (winner === ann) bob else ann
        winner.repo.saveTask(gid, null, "Valid", "", null, TaskStatus.TODO).assertOk()
        assertEquals(DenyReason.NOT_LEADER, loser.repo.saveTask(gid, null, "Invalid", "", null, TaskStatus.TODO).denial())
    }

    private fun compareChainsOf(a: List<LeaderEntry>, b: List<LeaderEntry>) = com.group4.liveroo.rules.compareChains(a, b)
}

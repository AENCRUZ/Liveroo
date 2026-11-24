package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.rules.GROUP_SKIPPED_MESSAGE
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.denial
import com.group4.liveroo.testsupport.exchangeState
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

/**
 * Step 7: a departure is finished only when the other phone merged it or had already recorded it (N1), with no
 * group data exchanged in the second case, and a phone that skips a group tells the other one, which then shows the
 * approved message (N9).
 */
@RunWith(RobolectricTestRunner::class)
class DepartureAndSkipTest {
    private lateinit var lead: TestPhone
    private lateinit var ann: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var gid: String

    private val departureNote = "Your departure from Study has synced. The group was removed from this phone."
    private val skippedNote = "Study: $GROUP_SKIPPED_MESSAGE"

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
            lead.repo.saveTask(gid, null, "Read", "", "ann-id", TaskStatus.TODO).assertOk()
            assertNull(ann.joinFrom(lead, gid))
            assertNull(bob.joinFrom(lead, gid))
        }
    }

    @After
    fun tearDown() {
        lead.close(); ann.close(); bob.close()
    }

    private suspend fun handshake(initiator: TestPhone, responder: TestPhone): Pair<SyncOutcome, Any> = coroutineScope {
        val channels = connectedChannels()
        val resp = async(Dispatchers.IO) {
            responder.sync.respond(channels.responderSide, allowSync = { true }, allowInvite = { false })
        }
        val init = initiator.sync.initiateSync(channels.initiatorSide)
        init to resp.await()
    }

    private suspend fun snapshot(p: TestPhone): String =
        p.db.groups().getAll().toString() + p.db.members().getAll(gid) + p.db.tasks().getAllForGroup(gid)

    private fun success(o: Any) = o as SyncOutcome.Success

    // ----- N1: finishing a departure -----

    @Test
    fun aPeerWhoHasNotHeard_receivesTheDeparture_andTheGroupIsRemoved() = runBlocking {
        ann.repo.leaveGroup(gid).assertOk()

        val (init, resp) = handshake(ann, lead)

        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertNotNull(lead.db.members().get(gid, "ann-id")!!.leftAt)                    // Lena now knows
        assertNull(ann.db.groups().get(gid))                                            // removed from Ann's phone
        assertTrue(success(init).notes.toString(), success(init).notes.contains(departureNote))
    }

    @Test
    fun aPeerWhoAlreadyRecordedTheDeparture_confirmsItWithoutAnyGroupData() = runBlocking {
        ann.repo.leaveGroup(gid).assertOk()
        exchangeState(ann, bob)                                                          // Bob records it; Ann keeps the group
        assertNotNull(bob.db.members().get(gid, "ann-id")!!.leftAt)
        assertNotNull(ann.db.groups().get(gid))
        val bobBefore = snapshot(bob)

        val (init, resp) = handshake(ann, bob)

        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertNull("departure is complete", ann.db.groups().get(gid))
        assertTrue(success(init).notes.toString(), success(init).notes.contains(departureNote))
        assertEquals("no group data came back", 0, success(init).received)
        assertEquals("Bob's copy was not touched by Ann's data", bobBefore, snapshot(bob))
    }

    @Test
    fun aPeerWhoDoesNotRecogniseTheLeaver_doesNotCompleteTheDeparture() = runBlocking {
        val others = bob.db.members().getAll(gid).filter { it.userId != "ann-id" }
        bob.db.members().deleteForGroup(gid)
        others.forEach { bob.db.members().upsert(it) }                                   // Bob has never heard of Ann
        ann.repo.leaveGroup(gid).assertOk()

        val (init, _) = handshake(ann, bob)

        assertTrue(init is SyncOutcome.Success)
        assertNotNull("the departure was not delivered, so the group stays", ann.db.groups().get(gid))
        assertTrue(ann.db.groups().get(gid)!!.leftByMe)
        assertTrue(success(init).notes.toString(), success(init).notes.contains(skippedNote))
        assertNull(bob.db.members().get(gid, "ann-id"))
    }

    // ----- N9: telling the person why a group wasn't synced -----

    @Test
    fun aGroupTheOtherPhoneSkipped_isExplainedToTheSkippedPerson_withTheApprovedWording() = runBlocking {
        val others = lead.db.members().getAll(gid).filter { it.userId != "ann-id" }
        lead.db.members().deleteForGroup(gid)
        others.forEach { lead.db.members().upsert(it) }                                  // Lena doesn't list Ann

        val (init, resp) = handshake(ann, lead)

        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertEquals(
            "Study: This group wasn't synced because the other device doesn't recognize you as an active member. " +
                "Sync with the group leader first.",
            success(init).notes.single { it.startsWith("Study:") }
        )
        assertTrue("the skipping phone shows nothing", success(resp).notes.none { it.contains("recognize") })
        assertNotNull(ann.db.groups().get(gid))
    }

    @Test
    fun aNormalSync_showsNoSkippedMessage() = runBlocking {
        val (init, resp) = handshake(ann, lead)
        assertTrue(success(init).notes.none { it.contains("recognize") })
        assertTrue(success(resp).notes.none { it.contains("recognize") })
    }

    @Test
    fun ackCarriesTheDepartureAndSkippedLists() {
        val ack = Ack(true, 1, null, accepted = listOf("a"), departureConfirmed = listOf("b"), skipped = listOf("c"))
        val buffer = java.io.ByteArrayOutputStream()
        Wire.send(java.io.DataOutputStream(buffer), ack)
        assertEquals(ack, Wire.receive(java.io.DataInputStream(java.io.ByteArrayInputStream(buffer.toByteArray()))))
        assertTrue(Ack(true).departureConfirmed.isEmpty() && Ack(true).skipped.isEmpty())
    }

    // ----- a member the leader takes back -----

    @Test
    fun aMemberTheLeaderTakesBack_canUseTheGroupAgain() = runBlocking {
        ann.repo.leaveGroup(gid).assertOk()
        assertEquals(DenyReason.GROUP_NOT_WRITABLE, ann.repo.saveTask(gid, null, "x", "", null, TaskStatus.TODO).denial())
        exchangeState(lead, ann)                                                         // Lena hears Ann left
        lead.repo.addMember(gid, "ann-id", "Ann").assertOk()                              // ...and takes her back

        val (_, annResult) = exchangeState(lead, ann)

        assertTrue(annResult.notes.toString(), annResult.notes.contains("You're an active member of Study again."))
        assertFalse(ann.db.groups().get(gid)!!.leftByMe)
        // No longer read-only: the refusal is now about leadership, not about having left.
        assertEquals(DenyReason.NOT_LEADER, ann.repo.saveTask(gid, null, "x", "", null, TaskStatus.TODO).denial())
    }

    @Test
    fun aDepartureIsNeverFinished_forSomeoneWhoIsActiveAgain() = runBlocking {
        // A stale "left" flag with an active member row must not cost Ann her copy of the group.
        val g = ann.db.groups().get(gid)!!
        ann.db.groups().upsert(g.copy(leftByMe = true))
        assertTrue(ann.repo.finishDepartures(listOf(gid)).isEmpty())
        assertNotNull(ann.db.groups().get(gid))
    }

    @Test
    fun aStalePeerConfirmingTheOldDeparture_doesNotRemoveAReAddedMembersGroup() = runBlocking {
        ann.repo.leaveGroup(gid).assertOk()
        exchangeState(ann, bob)                                                          // Bob knows Ann left
        exchangeState(lead, ann)
        lead.repo.addMember(gid, "ann-id", "Ann").assertOk()
        exchangeState(lead, ann)                                                         // Ann is back; Bob hasn't heard
        assertFalse(ann.db.groups().get(gid)!!.leftByMe)

        handshake(ann, bob)                                                              // Bob "confirms" the old departure

        assertNotNull("Ann keeps her group", ann.db.groups().get(gid))
        assertNull(ann.db.members().get(gid, "ann-id")!!.leftAt)
    }
}

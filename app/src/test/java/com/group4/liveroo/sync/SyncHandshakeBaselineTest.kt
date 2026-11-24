package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Baseline: the full handshake runs end to end over a loopback connection between two simulated phones.
 * These pin the happy paths that must keep working while Phase 1 changes the rules and the protocol.
 */
@RunWith(RobolectricTestRunner::class)
class SyncHandshakeBaselineTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        alice = TestPhone(context, "alice-id", "Alice")
        bob = TestPhone(context, "bob-id", "Bob")
    }

    @After
    fun tearDown() {
        alice.close(); bob.close()
    }

    /** Alice leads a group that Bob already holds a copy of (as if he had joined earlier). */
    private suspend fun sharedGroup(): String {
        val gid = alice.repo.createGroup("Study")
        alice.repo.addMember(gid, "bob-id", "Bob")
        bob.repo.importInvite(alice.repo.export(listOf(gid)))
        return gid
    }

    @Test
    fun sync_deliversTask_andClearsNotYetSynced() = runBlocking {
        val gid = sharedGroup()
        val tid = alice.repo.saveTask(gid, null, "Read ch. 1", "", "bob-id", TaskStatus.TODO).id()
        assertTrue(alice.db.tasks().get(tid)!!.dirty)

        val channels = connectedChannels()
        val responder = async(Dispatchers.IO) {
            bob.sync.respond(channels.responderSide, allowSync = { true }, allowInvite = { false })
        }
        val initiated = alice.sync.initiateSync(channels.initiatorSide)
        val responded = responder.await()

        assertTrue("initiator: $initiated", initiated is SyncOutcome.Success)
        assertTrue("responder: $responded", responded is SyncOutcome.Success)
        assertEquals("Bob", (initiated as SyncOutcome.Success).peerName)
        assertEquals("Read ch. 1", bob.db.tasks().get(tid)!!.title)
        assertFalse(alice.db.tasks().get(tid)!!.dirty)
        // Each phone remembers the other's device (T4).
        assertEquals("bob-id", alice.db.devices().observeAll().first().single().userId)
    }

    @Test
    fun sync_declinedByResponder_changesNothing() = runBlocking {
        val gid = sharedGroup()
        val tid = alice.repo.saveTask(gid, null, "Read ch. 1", "", "bob-id", TaskStatus.TODO).id()

        val channels = connectedChannels()
        val responder = async(Dispatchers.IO) {
            bob.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { false })
        }
        val initiated = alice.sync.initiateSync(channels.initiatorSide)
        val responded = responder.await()

        assertEquals(SyncOutcome.Declined, initiated)
        assertEquals(SyncOutcome.Declined, responded)
        assertEquals(null, bob.db.tasks().get(tid))
        assertTrue(alice.db.tasks().get(tid)!!.dirty)
    }

    @Test
    fun invite_happyPath_givesInviteeTheGroup_andLeaderTheMember() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        alice.repo.saveTask(gid, null, "Read ch. 1", "", null, TaskStatus.TODO)

        val channels = connectedChannels()
        val invitee = async(Dispatchers.IO) {
            bob.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { true })
        }
        val invited = alice.sync.initiateInvite(channels.initiatorSide, gid)
        val joined = invitee.await()

        assertTrue("leader: $invited", invited is InviteOutcome.Joined)
        assertTrue("invitee: $joined", joined is InviteOutcome.Joined)
        assertNotNull(bob.db.groups().get(gid))
        assertEquals("alice-id", bob.db.groups().get(gid)!!.leaderId)
        assertEquals(1, bob.db.tasks().getAllForGroup(gid).size)
        assertNotNull(alice.db.members().get(gid, "bob-id"))
        assertNotNull(bob.db.members().get(gid, "bob-id"))
    }

    @Test
    fun invite_declinedByInvitee_givesNothing() = runBlocking {
        val gid = alice.repo.createGroup("Study")

        val channels = connectedChannels()
        val invitee = async(Dispatchers.IO) {
            bob.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { false })
        }
        val invited = alice.sync.initiateInvite(channels.initiatorSide, gid)
        val answered = invitee.await()

        assertEquals(InviteOutcome.Declined, invited)
        assertEquals(InviteOutcome.Declined, answered)
        assertEquals(null, bob.db.groups().get(gid))
    }

    // ----- Step 1b: different app versions never exchange data -----

    private class RawPeer(channel: SyncChannel) {
        val input = DataInputStream(channel.input)
        val out = DataOutputStream(channel.output)
    }

    /** A pre-v2 Hello: no version field (decodes as v = 0). */
    private fun oldHello(purpose: String, groupIds: List<String> = emptyList()) =
        Hello("bob-id", "Bob", groupIds, purpose)

    @Test
    fun sync_initiator_stopsBeforeSendingData_whenPeerIsAnOlderBuild() = runBlocking {
        val gid = sharedGroup()
        alice.repo.saveTask(gid, null, "Read ch. 1", "", "bob-id", TaskStatus.TODO)
        val channels = connectedChannels()
        val oldPeer = async(Dispatchers.IO) {
            val raw = RawPeer(channels.responderSide)
            val first = Wire.receive(raw.input) as Hello
            Wire.send(raw.out, oldHello("sync", listOf(gid)))
            first to runCatching { Wire.receive(raw.input) }   // must be EOF: the initiator sends no data
        }
        val outcome = alice.sync.initiateSync(channels.initiatorSide)
        val (first, next) = oldPeer.await()

        assertEquals(PROTOCOL_VERSION, first.v)
        assertEquals(SyncOutcome.Failed(VERSION_MISMATCH_MESSAGE, wasTransfer = false), outcome)
        assertTrue(next.exceptionOrNull() is EOFException)
        assertEquals(0, alice.db.devices().observeAll().first().size)
    }

    @Test
    fun sync_responder_refusesBeforePrompting_whenInitiatorIsAnOlderBuild() = runBlocking {
        val channels = connectedChannels()
        val oldInitiator = async(Dispatchers.IO) {
            val raw = RawPeer(channels.initiatorSide)
            Wire.send(raw.out, oldHello("sync"))
            (Wire.receive(raw.input) as Hello) to runCatching { Wire.receive(raw.input) }
        }
        val outcome = bob.sync.respond(
            channels.responderSide,
            allowSync = { error("the user must not be prompted") },
            allowInvite = { error("the user must not be prompted") }
        )
        val (reply, next) = oldInitiator.await()

        assertEquals(SyncOutcome.Failed(VERSION_MISMATCH_MESSAGE), outcome)
        assertEquals(PURPOSE_VERSION_MISMATCH, reply.purpose)
        assertTrue(next.exceptionOrNull() is EOFException)
        assertEquals(0, bob.db.devices().observeAll().first().size)
    }

    @Test
    fun invite_responder_reportsInviteFailure_whenInitiatorIsAnOlderBuild() = runBlocking {
        val channels = connectedChannels()
        val oldInitiator = async(Dispatchers.IO) {
            val raw = RawPeer(channels.initiatorSide)
            Wire.send(raw.out, oldHello("invite"))
            Wire.receive(raw.input) as Hello
        }
        val outcome = bob.sync.respond(
            channels.responderSide,
            allowSync = { error("unexpected") },
            allowInvite = { error("the user must not be prompted") }
        )
        oldInitiator.await()
        assertEquals(InviteOutcome.Failed(VERSION_MISMATCH_MESSAGE), outcome)
        assertEquals(null, bob.db.groups().getAll().firstOrNull())
    }

    @Test
    fun invite_initiator_stopsBeforeOffering_whenPeerIsAnOlderBuild() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val channels = connectedChannels()
        val oldPeer = async(Dispatchers.IO) {
            val raw = RawPeer(channels.responderSide)
            Wire.receive(raw.input) as Hello
            Wire.send(raw.out, oldHello("invite"))
            runCatching { Wire.receive(raw.input) }
        }
        val outcome = alice.sync.initiateInvite(channels.initiatorSide, gid)
        val next = oldPeer.await()

        assertEquals(InviteOutcome.Failed(VERSION_MISMATCH_MESSAGE), outcome)
        assertTrue(next.exceptionOrNull() is EOFException)       // no offer, no group data
        assertEquals(null, alice.db.members().get(gid, "bob-id"))
    }
}

package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.id
import com.group4.liveroo.testsupport.joinFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Step 4 over the real handshake: a group only takes part when the peer is an active member in my own data, and
 * "Not yet synced" is cleared only for groups the other phone also merged.
 */
@RunWith(RobolectricTestRunner::class)
class SyncScopeHandshakeTest {
    private lateinit var lead: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var gid: String

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            lead = TestPhone(context, "lead-id", "Lena")
            bob = TestPhone(context, "bob-id", "Bob")
            gid = lead.repo.createGroup("Study")
            lead.repo.addMember(gid, "bob-id", "Bob").assertOk()
            assertNull(bob.joinFrom(lead, gid))
        }
    }

    @After
    fun tearDown() {
        lead.close(); bob.close()
    }

    private suspend fun sync(initiator: TestPhone, responder: TestPhone): Pair<SyncOutcome, Any> = coroutineScope {
        val channels = connectedChannels()
        val resp = async(Dispatchers.IO) {
            responder.sync.respond(channels.responderSide, allowSync = { true }, allowInvite = { false })
        }
        val init = initiator.sync.initiateSync(channels.initiatorSide)
        init to resp.await()
    }

    @Test
    fun ackListsTheGroupsAPhoneMerged() {
        val ack = Ack(true, 2, null, accepted = listOf("g1", "g2"))
        val buffer = java.io.ByteArrayOutputStream()
        Wire.send(java.io.DataOutputStream(buffer), ack)
        val back = Wire.receive(java.io.DataInputStream(java.io.ByteArrayInputStream(buffer.toByteArray())))
        assertEquals(ack, back)
        assertTrue(Ack(true).accepted.isEmpty())
    }

    @Test
    fun aPeerWhoIsNotRecognised_isSkipped_andNothingIsMarkedSynced() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        assertNull(bob.joinFrom(lead, gid))
        lead.repo.saveTask(gid, tid, "Read twice", "", null, TaskStatus.TODO).assertOk()    // Lena's unsynced change

        // Lena doesn't list Bob (as if she never heard of him), while Bob still holds the group.
        val leadRow = lead.db.members().get(gid, "lead-id")!!
        lead.db.members().deleteForGroup(gid)
        lead.db.members().upsert(leadRow)
        // Bob has a pending change of his own.
        bob.db.members().upsert(bob.db.members().get(gid, "bob-id")!!.copy(dirty = true))

        val (init, resp) = sync(lead, bob)

        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        // Lena skipped the group: she merged nothing from Bob and nothing of hers counts as synced.
        assertEquals("Read twice", lead.db.tasks().get(tid)!!.title)
        assertTrue(lead.db.tasks().get(tid)!!.dirty)
        assertNull(lead.db.members().get(gid, "bob-id"))
        // Bob received nothing for the group, and his pending change is not marked synced.
        assertEquals("Read", bob.db.tasks().get(tid)!!.title)
        assertTrue(bob.db.members().get(gid, "bob-id")!!.dirty)
    }

    @Test
    fun bothRecognised_clearsNotYetSynced_forTheConfirmedGroup() = runBlocking {
        val tid = lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        assertTrue(lead.db.tasks().get(tid)!!.dirty)
        val (init, resp) = sync(lead, bob)
        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertFalse(lead.db.tasks().get(tid)!!.dirty)
        assertEquals("Read", bob.db.tasks().get(tid)!!.title)
    }
}

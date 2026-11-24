package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.connectedChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.DataInputStream
import java.io.EOFException

/** Step 3: only the group's leader can invite, and a refused invite sends nothing to the other phone. */
@RunWith(RobolectricTestRunner::class)
class InviteAuthorizationTest {
    private lateinit var lead: TestPhone
    private lateinit var ann: TestPhone     // a plain member of the leader's group
    private lateinit var eve: TestPhone     // someone ann tries to invite

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        lead = TestPhone(context, "lead-id", "Lena")
        ann = TestPhone(context, "ann-id", "Ann")
        eve = TestPhone(context, "eve-id", "Eve")
    }

    @After
    fun tearDown() {
        lead.close(); ann.close(); eve.close()
    }

    @Test
    fun aMember_cannotInvite_andNothingIsSentToTheOtherPhone() = runBlocking {
        val gid = lead.repo.createGroup("Study")
        lead.repo.addMember(gid, "ann-id", "Ann")
        ann.repo.importInvite(lead.repo.export(listOf(gid)))

        val channels = connectedChannels()
        val peer = async(Dispatchers.IO) {
            runCatching { Wire.receive(DataInputStream(channels.responderSide.input)) }   // must see EOF, no Hello
        }
        val outcome = ann.sync.initiateInvite(channels.initiatorSide, gid)
        val received = peer.await()

        assertEquals(InviteOutcome.Failed("Only the group leader can invite members."), outcome)
        assertTrue(received.exceptionOrNull() is EOFException)
        assertNull(ann.db.members().get(gid, "eve-id"))
    }

    @Test
    fun aGroupThatIsNotOnThisPhone_cannotBeInvitedTo() = runBlocking {
        val channels = connectedChannels()
        val outcome = lead.sync.initiateInvite(channels.initiatorSide, "nope")
        assertEquals(InviteOutcome.Failed("Group not found"), outcome)
    }

    @Test
    fun theLeader_canStillInvite() = runBlocking {
        val gid = lead.repo.createGroup("Study")
        val channels = connectedChannels()
        val invitee = async(Dispatchers.IO) {
            eve.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { true })
        }
        val outcome = lead.sync.initiateInvite(channels.initiatorSide, gid)
        invitee.await()
        assertTrue("leader: $outcome", outcome is InviteOutcome.Joined)
        assertTrue(eve.db.groups().get(gid) != null)
    }
}

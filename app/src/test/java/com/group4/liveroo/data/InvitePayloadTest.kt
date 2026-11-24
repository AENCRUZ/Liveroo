package com.group4.liveroo.data

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.Ver
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.sync.Ack
import com.group4.liveroo.sync.DataMsg
import com.group4.liveroo.sync.Hello
import com.group4.liveroo.sync.INVITE_DATA_REFUSED_MESSAGE
import com.group4.liveroo.sync.InviteAnswer
import com.group4.liveroo.sync.InviteOffer
import com.group4.liveroo.sync.InviteOutcome
import com.group4.liveroo.sync.TaskDto
import com.group4.liveroo.sync.Wire
import com.group4.liveroo.sync.newHello
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.DataInputStream
import java.io.DataOutputStream

/** Step 4: an invitation's group data is checked as a whole, and nothing is stored unless all of it makes sense. */
@RunWith(RobolectricTestRunner::class)
class InvitePayloadTest {
    private lateinit var lead: TestPhone
    private lateinit var eve: TestPhone
    private lateinit var gid: String
    private lateinit var good: DataMsg

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            lead = TestPhone(context, "lead-id", "Lena")
            eve = TestPhone(context, "eve-id", "Eve")
            gid = lead.repo.createGroup("Study")
            lead.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).assertOk()
            lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
            good = lead.repo.export(listOf(gid))
        }
    }

    @After
    fun tearDown() {
        lead.close(); eve.close()
    }

    private suspend fun assertRefused(data: DataMsg) {
        assertNotNull(eve.repo.invitePayloadProblem(data))
        assertNotNull(eve.repo.importInvite(data))
        assertNull(eve.db.groups().get(gid))                                                // nothing was stored
        assertTrue(eve.db.members().getAll(gid).isEmpty())
        assertTrue(eve.db.tasks().getAllForGroup(gid).isEmpty())
    }

    @Test
    fun aGoodPayload_isImported_withRowsMaterialized() = runBlocking {
        assertNull(eve.repo.invitePayloadProblem(good))
        assertNull(eve.repo.importInvite(good))
        assertEquals("lead-id", eve.db.groups().get(gid)!!.leaderId)
        assertEquals(1, eve.db.tasks().getAllForGroup(gid).size)
        assertEquals("eve-id", eve.db.members().get(gid, "eve-id")!!.userId)
    }

    @Test
    fun morethanOneGroup_isRefused() = runBlocking {
        val other = lead.repo.createGroup("Other")
        val twoGroups = good.copy(groups = good.groups + lead.repo.export(listOf(other)).groups)
        assertRefused(twoGroups)
    }

    @Test
    fun dataFromAnotherGroup_isRefused() = runBlocking {
        val other = lead.repo.createGroup("Other")
        val mixed = good.copy(members = good.members + lead.repo.export(listOf(other)).members)
        assertRefused(mixed)
    }

    @Test
    fun aPayloadThatDoesNotListMe_orListsMeAsLeft_isRefused() = runBlocking {
        assertRefused(good.copy(members = good.members.filter { it.userId != "eve-id" }))
        assertRefused(good.copy(members = good.members.map { if (it.userId == "eve-id") it.copy(leftAt = 5) else it }))
    }

    @Test
    fun aLeadershipChainWithAnUnknownLeader_isRefused() = runBlocking {
        val g = good.groups.single()
        assertRefused(good.copy(groups = listOf(g.copy(leaderChain = g.leaderChain + LeaderEntry("ghost-id", 9)))))
    }

    @Test
    fun aTaskNotWrittenByTheLeader_isRefused() = runBlocking {
        val t = good.tasks.single()
        val forged = t.registers.withContent("Hacked", "", Ver(0, t.registers.nextTs(System.currentTimeMillis()), "eve-id"))
        assertRefused(good.copy(tasks = listOf(TaskDto(t.id, t.groupId, forged))))
    }

    // ----- over the real handshake: the invitee refuses before agreeing, and stores nothing -----

    @Test
    fun theInvitee_refusesTamperedGroupData_andSendsANegativeAck() = runBlocking {
        val tampered = good.copy(members = good.members.filter { it.userId != "eve-id" })
        val channels = connectedChannels()
        val rawLeader = async(Dispatchers.IO) {
            val input = DataInputStream(channels.initiatorSide.input)
            val out = DataOutputStream(channels.initiatorSide.output)
            Wire.send(out, newHello("lead-id", "Lena", emptyList(), "invite"))
            Wire.receive(input) as Hello
            Wire.send(out, InviteOffer(gid, "Study", "Lena"))
            val answer = Wire.receive(input) as InviteAnswer
            Wire.send(out, tampered)
            answer to (Wire.receive(input) as Ack)
        }
        val outcome = eve.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { true })
        val (answer, ack) = rawLeader.await()

        assertTrue(answer.accept)
        assertEquals(false, ack.ok)
        assertEquals(InviteOutcome.Failed(INVITE_DATA_REFUSED_MESSAGE), outcome)
        assertNull(eve.db.groups().get(gid))
    }

    @Test
    fun theInvitee_refusesGroupDataThatDoesNotMatchTheInvitation() = runBlocking {
        val channels = connectedChannels()
        val rawLeader = async(Dispatchers.IO) {
            val input = DataInputStream(channels.initiatorSide.input)
            val out = DataOutputStream(channels.initiatorSide.output)
            Wire.send(out, newHello("lead-id", "Lena", emptyList(), "invite"))
            Wire.receive(input) as Hello
            Wire.send(out, InviteOffer("some-other-group", "Study", "Lena"))        // offer names a different group
            Wire.receive(input) as InviteAnswer
            Wire.send(out, good)
            Wire.receive(input) as Ack
        }
        val outcome = eve.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { true })
        assertEquals(false, rawLeader.await().ok)
        assertEquals(InviteOutcome.Failed(INVITE_DATA_REFUSED_MESSAGE), outcome)
        assertNull(eve.db.groups().get(gid))
    }
}

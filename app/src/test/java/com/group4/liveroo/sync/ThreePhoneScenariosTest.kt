package com.group4.liveroo.sync

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.rules.GROUP_SKIPPED_MESSAGE
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.completeTransfer
import com.group4.liveroo.testsupport.connectedChannels
import com.group4.liveroo.testsupport.denial
import com.group4.liveroo.testsupport.id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/**
 * Step 8: whole stories between three simulated phones, driven only by the real flows (invitations, syncs,
 * handovers, leaving). Each phone has its own database; every step goes over the real handshake.
 */
@RunWith(RobolectricTestRunner::class)
class ThreePhoneScenariosTest {
    private lateinit var lena: TestPhone
    private lateinit var ann: TestPhone
    private lateinit var bob: TestPhone

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        lena = TestPhone(context, "lena-id", "Lena")
        ann = TestPhone(context, "ann-id", "Ann")
        bob = TestPhone(context, "bob-id", "Bob")
    }

    @After
    fun tearDown() {
        lena.close(); ann.close(); bob.close()
    }

    // ----- the real flows, as helpers -----

    private suspend fun invite(leader: TestPhone, invitee: TestPhone, groupId: String) = coroutineScope {
        val channels = connectedChannels()
        val answering = async(Dispatchers.IO) {
            invitee.sync.respond(channels.responderSide, allowSync = { false }, allowInvite = { true })
        }
        val leaderSide = leader.sync.initiateInvite(channels.initiatorSide, groupId)
        val inviteeSide = answering.await()
        assertTrue("leader: $leaderSide", leaderSide is InviteOutcome.Joined)
        assertTrue("invitee: $inviteeSide", inviteeSide is InviteOutcome.Joined)
    }

    /** One real sync; both sides must succeed. Returns (initiator's result, responder's result). */
    private suspend fun sync(initiator: TestPhone, responder: TestPhone): Pair<SyncOutcome.Success, SyncOutcome.Success> =
        coroutineScope {
            val channels = connectedChannels()
            val answering = async(Dispatchers.IO) {
                responder.sync.respond(channels.responderSide, allowSync = { true }, allowInvite = { false })
            }
            val first = initiator.sync.initiateSync(channels.initiatorSide)
            val second = answering.await()
            assertTrue("${initiator.name}: $first / ${responder.name}: $second", first is SyncOutcome.Success && second is SyncOutcome.Success)
            (first as SyncOutcome.Success) to (second as SyncOutcome.Success)
        }

    private suspend fun handover(from: TestPhone, to: TestPhone, groupId: String) {
        val (first, second) = completeTransfer(from, to, groupId)
        assertTrue("$first / $second", first is SyncOutcome.Success && second is SyncOutcome.Success)
    }

    private suspend fun snapshot(p: TestPhone, gid: String): String {
        val g = p.db.groups().get(gid)!!
        val members = p.db.members().getAll(gid).sortedBy { it.userId }
            .map { "${it.userId}|${it.displayName}|${it.joinedAt}|${it.leftAt}|${it.updatedAt}" }
        val tasks = p.db.tasks().getAllForGroup(gid).sortedBy { it.id }.map { it.copy(dirty = false).toString() }
        return "${g.name}|${g.leaderId}|${g.leaderChain}\n${members.joinToString("\n")}\n${tasks.joinToString("\n")}"
    }

    private suspend fun titles(p: TestPhone, gid: String) = p.db.tasks().getAllForGroup(gid).map { it.title }.sorted()

    // ----- a whole project from start to finish -----

    @Test
    fun aProjectFromInvitationsToTheLeadersDeparture() = runBlocking {
        // Lena starts the group and invites Ann and Bob.
        val gid = lena.repo.createGroup("Project")
        invite(lena, ann, gid)
        invite(lena, bob, gid)

        // She assigns work and everyone gets it.
        val slides = lena.repo.saveTask(gid, null, "Slides", "", "ann-id", TaskStatus.TODO).id()
        val report = lena.repo.saveTask(gid, null, "Report", "", "bob-id", TaskStatus.TODO).id()
        sync(lena, ann)
        sync(lena, bob)
        assertEquals(listOf("Report", "Slides"), titles(ann, gid))
        assertEquals(listOf("Report", "Slides"), titles(bob, gid))

        // Meanwhile, apart: Ann and Bob progress their tasks, Lena renames one.
        ann.repo.setStatus(slides, TaskStatus.IN_PROGRESS).assertOk()
        bob.repo.setStatus(report, TaskStatus.DONE).assertOk()
        lena.repo.saveTask(gid, slides, "Slides v2", "", "ann-id", TaskStatus.TODO).assertOk()
        sync(ann, lena)
        sync(bob, lena)
        sync(ann, bob)
        for (p in listOf(lena, ann, bob)) {
            assertEquals("Slides v2", p.db.tasks().get(slides)!!.title)                 // the rename and the progress both survive
            assertEquals(TaskStatus.IN_PROGRESS, p.db.tasks().get(slides)!!.status)
            assertEquals(TaskStatus.DONE, p.db.tasks().get(report)!!.status)
        }

        // Lena hands the group to Ann, who has to agree; Bob finds out from Ann.
        handover(lena, ann, gid)
        val (bobSide, _) = sync(bob, ann)
        assertTrue(bobSide.notes.toString(), bobSide.notes.contains("Ann is now the leader of Project."))
        assertEquals(DenyReason.NOT_LEADER, lena.repo.saveTask(gid, null, "No", "", null, TaskStatus.TODO).denial())

        // The new leader leads: she adds a task for Bob, who works on it.
        val demo = ann.repo.saveTask(gid, null, "Demo", "", "bob-id", TaskStatus.TODO).id()
        sync(ann, bob)
        bob.repo.setStatus(demo, TaskStatus.IN_PROGRESS).assertOk()
        sync(bob, ann)
        assertEquals(TaskStatus.IN_PROGRESS, ann.db.tasks().get(demo)!!.status)

        // Lena, now an ordinary member, leaves; Bob is the first to hear, Ann hears from Bob.
        lena.repo.leaveGroup(gid).assertOk()
        val (lenaSide, _) = sync(lena, bob)
        assertTrue(lenaSide.notes.toString(), lenaSide.notes.contains("Your departure from Project has synced. The group was removed from this phone."))
        assertNull("the group is gone from Lena's phone", lena.db.groups().get(gid))
        sync(bob, ann)

        // Ann and Bob end with exactly the same group, and Lena's departure is on record.
        assertEquals(snapshot(ann, gid), snapshot(bob, gid))
        assertNotNull(ann.db.members().get(gid, "lena-id")!!.leftAt)
        assertEquals("ann-id", ann.db.groups().get(gid)!!.leaderId)
        assertEquals(listOf("Demo", "Report", "Slides v2"), titles(ann, gid))
    }

    // ----- a lost final message makes two leaders; every phone settles on one -----

    @Test
    fun competingHandovers_leaveOneLeader_andThePretendersWorkStaysOnHisPhone() = runBlocking {
        val gid = lena.repo.createGroup("Project")
        invite(lena, ann, gid)
        invite(lena, bob, gid)
        sync(lena, ann); sync(lena, bob); sync(ann, bob)

        // Lena hands over to Ann; the last message never reaches Lena's phone, so she still thinks she leads.
        handover(lena, ann, gid)
        val g = lena.db.groups().get(gid)!!
        lena.db.groups().upsert(g.copy(leaderId = "lena-id", leaderChain = ModelJson.encodeChain(g.chain().take(1))))
        // From that stale view she hands over to Bob as well. Bob now believes he leads and starts working.
        handover(lena, bob, gid)
        bob.repo.saveTask(gid, null, "Orphan", "", null, TaskStatus.TODO).assertOk()

        repeat(3) { sync(lena, ann); sync(lena, bob); sync(ann, bob) }

        // One chain, one leader, on every phone: the earlier timestamp wins (Ann).
        val chains = listOf(lena, ann, bob).map { it.db.groups().get(gid)!!.chain() }
        assertEquals(1, chains.distinct().size)
        assertEquals(listOf("lena-id", "ann-id"), chains.first().map { it.id })
        assertEquals(DenyReason.NOT_LEADER, bob.repo.saveTask(gid, null, "No", "", null, TaskStatus.TODO).denial())
        ann.repo.saveTask(gid, null, "Real", "", null, TaskStatus.TODO).assertOk()
        repeat(2) { sync(ann, lena); sync(ann, bob); sync(lena, bob) }
        assertEquals(snapshot(lena, gid), snapshot(ann, gid))
        assertTrue(titles(ann, gid).contains("Real") && titles(bob, gid).contains("Real"))

        // Documented limit: what the losing leader created while he believed he led never reaches anyone else.
        assertNull(titles(lena, gid).firstOrNull { it == "Orphan" })
        assertNull(titles(ann, gid).firstOrNull { it == "Orphan" })
        assertNotNull("it stays on his own phone", titles(bob, gid).firstOrNull { it == "Orphan" })
    }

    // ----- being skipped, and what to do about it -----

    @Test
    fun aNewMemberSkippedByAGroupmate_isTold_andSyncsOnceTheGroupmateHasHeardOfThem() = runBlocking {
        val gid = lena.repo.createGroup("Project")
        invite(lena, ann, gid)
        val task = lena.repo.saveTask(gid, null, "Slides", "", "ann-id", TaskStatus.TODO).id()
        sync(lena, ann)

        // Lena invites Bob. Ann hasn't heard of Bob yet.
        invite(lena, bob, gid)
        val (bobSide, annSide) = sync(bob, ann)
        assertTrue(bobSide.notes.toString(), bobSide.notes.contains("Project: $GROUP_SKIPPED_MESSAGE"))
        assertTrue(annSide.notes.none { it.contains("recognize") })
        assertNull("Ann still doesn't know Bob", ann.db.members().get(gid, "bob-id"))

        // Bob does as the message says: sync with the leader. Then Ann hears of Bob from the leader.
        sync(bob, lena)
        val (_, annAfterLena) = sync(lena, ann)
        assertTrue(annAfterLena.notes.toString(), annAfterLena.notes.contains("Bob joined Project."))

        // Now Bob and Ann sync directly: no message, and Ann's progress reaches Bob.
        ann.repo.setStatus(task, TaskStatus.DONE).assertOk()
        val (bobAgain, _) = sync(bob, ann)
        assertTrue(bobAgain.notes.toString(), bobAgain.notes.none { it.contains("recognize") })
        assertEquals(TaskStatus.DONE, bob.db.tasks().get(task)!!.status)
    }
}

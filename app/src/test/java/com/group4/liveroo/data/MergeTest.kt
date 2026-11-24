package com.group4.liveroo.data

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.Ver
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.sync.DataMsg
import com.group4.liveroo.sync.GroupDto
import com.group4.liveroo.sync.SyncOutcome
import com.group4.liveroo.sync.TaskDto
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.completeTransfer
import com.group4.liveroo.testsupport.exchangeState
import com.group4.liveroo.testsupport.id
import com.group4.liveroo.testsupport.joinFrom
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

/** Step 4: the real merge, run between simulated phones with their own databases. */
@RunWith(RobolectricTestRunner::class)
class MergeTest {
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

    private suspend fun newTask(assignee: String? = "ann-id"): String {
        val id = lead.repo.saveTask(gid, null, "Read", "ch. 1", assignee, TaskStatus.TODO).id()
        assertNull(ann.joinFrom(lead, gid)); assertNull(bob.joinFrom(lead, gid))
        return id
    }

    // ----- allowed groups (D4) -----

    @Test
    fun dataAboutGroupsOutsideTheAllowedSet_isRefused() = runBlocking {
        val other = lead.repo.createGroup("Other")
        lead.repo.saveTask(other, null, "Secret", "", null, TaskStatus.TODO).assertOk()
        val message = lead.repo.export(listOf(gid, other))

        val result = bob.repo.merge(message, "bob-id", setOf(gid))
        assertNull(bob.db.groups().get(other))
        assertTrue(bob.db.members().getAll(other).isEmpty())
        assertTrue(bob.db.tasks().getAllForGroup(other).isEmpty())
        assertTrue("dropped=${result.dropped}", result.dropped >= 3)             // group, member and task of "other"

        val nothing = bob.repo.merge(message, "bob-id", emptySet())
        assertEquals(0, nothing.changed)
        assertEquals(message.groups.size + message.members.size + message.tasks.size, nothing.dropped)
    }

    @Test
    fun aGroupThisPhoneDoesNotHold_isNeverCreatedByMerge() = runBlocking {
        val other = lead.repo.createGroup("Other")
        val result = bob.repo.merge(lead.repo.export(listOf(other)), "bob-id", setOf(other))
        assertNull(bob.db.groups().get(other))
        assertEquals(0, result.changed)
    }

    // ----- per-field task merge (D10, D12) -----

    @Test
    fun renameAndStatusChange_madeApart_bothSurviveASync() = runBlocking {
        val tid = newTask("ann-id")
        lead.repo.saveTask(gid, tid, "Renamed", "ch. 1", "ann-id", TaskStatus.TODO).assertOk()
        ann.repo.setStatus(tid, TaskStatus.DONE).assertOk()

        exchangeState(lead, ann)

        for (phone in listOf(lead, ann)) {
            val t = phone.db.tasks().get(tid)!!
            assertEquals("Renamed", t.title)
            assertEquals(TaskStatus.DONE, t.status)
        }
        assertEquals(lead.db.tasks().get(tid)!!.copy(dirty = false), ann.db.tasks().get(tid)!!.copy(dirty = false))
    }

    @Test
    fun deletion_winsOverAConcurrentStatusChange_whicheverIsNewer() = runBlocking {
        val tid = newTask("ann-id")
        ann.repo.setStatus(tid, TaskStatus.DONE).assertOk()                       // made after the leader saw it, or before
        lead.repo.deleteTask(tid).assertOk()
        exchangeState(lead, ann)
        assertTrue(lead.db.tasks().get(tid)!!.deleted)
        assertTrue(ann.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun aStatusWrittenWithoutSeeingAReassignment_isDiscarded_onEveryPhone() = runBlocking {
        val tid = newTask("ann-id")
        lead.repo.saveTask(gid, tid, "Read", "ch. 1", "bob-id", TaskStatus.TODO).assertOk()   // reassigned to Bob
        ann.repo.setStatus(tid, TaskStatus.DONE).assertOk()                                  // Ann hadn't heard

        exchangeState(lead, ann)
        exchangeState(lead, bob)
        exchangeState(ann, bob)

        for (phone in listOf(lead, ann, bob)) {
            val t = phone.db.tasks().get(tid)!!
            assertEquals("bob-id", t.assigneeId)
            assertEquals(TaskStatus.TODO, t.status)                                         // the leader's snapshot shows
        }
        bob.repo.setStatus(tid, TaskStatus.DONE).assertOk()                                 // the new assignee's write counts
        exchangeState(lead, bob)
        assertEquals(TaskStatus.DONE, lead.db.tasks().get(tid)!!.status)
    }

    @Test
    fun forgedLeaderOnlyEdits_byAMember_areDropped() = runBlocking {
        val tid = newTask(null)
        val honest = lead.db.tasks().get(tid)!!.regs()
        val forged = honest.withContent("Hacked", "", Ver(0, honest.nextTs(System.currentTimeMillis()) + 100, "ann-id"))
            .withTombstone(Ver(0, honest.nextTs(System.currentTimeMillis()) + 100, "ann-id"))
        val message = DataMsg(emptyList(), emptyList(), listOf(TaskDto(tid, gid, forged)))

        val before = bob.db.tasks().get(tid)
        val result = bob.repo.merge(message, "bob-id", setOf(gid))
        assertEquals(before, bob.db.tasks().get(tid))
        assertEquals(2, result.dropped)
        assertFalse(bob.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun aTaskIdBelongingToAnotherGroup_isNeverOverwritten() = runBlocking {
        val other = lead.repo.createGroup("Other")
        val tid = lead.repo.saveTask(other, null, "Mine", "", null, TaskStatus.TODO).id()
        val regs = lead.db.tasks().get(tid)!!.regs().withContent("Taken over", "", Ver(0, Long.MAX_VALUE / 2, "lead-id"))
        val message = DataMsg(emptyList(), emptyList(), listOf(TaskDto(tid, gid, regs)))     // claims it is in group gid
        val before = lead.db.tasks().get(tid)

        val result = lead.repo.merge(message, "lead-id", setOf(gid))
        assertEquals(before, lead.db.tasks().get(tid))
        assertEquals(1, result.dropped)
    }

    // ----- derived unassign (spec 4.4) -----

    @Test
    fun aMemberWhoLeft_hasTheirTasksUnassignedEverywhere_andRejoiningDoesNotRestoreThem() = runBlocking {
        val tid = newTask("ann-id")
        ann.repo.leaveGroup(gid).assertOk()
        assertNull(ann.db.tasks().get(tid)!!.assigneeId)                                    // derived locally at once

        val (leadResult, _) = exchangeState(lead, ann)
        assertTrue(leadResult.notes.toString(), leadResult.notes.contains("Ann left Study. 1 task is now unassigned."))
        assertNull(lead.db.tasks().get(tid)!!.assigneeId)
        assertEquals("ann-id", lead.db.tasks().get(tid)!!.regs().assignee.id)               // derived, not rewritten

        exchangeState(lead, bob)
        assertNull(bob.db.tasks().get(tid)!!.assigneeId)

        lead.repo.addMember(gid, "ann-id", "Ann").assertOk()                                // re-added
        assertNull(lead.db.tasks().get(tid)!!.assigneeId)                                   // old assignment doesn't return
        lead.repo.saveTask(gid, tid, "Read", "ch. 1", "ann-id", TaskStatus.TODO).assertOk()  // a new one does
        assertEquals("ann-id", lead.db.tasks().get(tid)!!.assigneeId)
        exchangeState(lead, ann)
        assertEquals("ann-id", ann.db.tasks().get(tid)!!.assigneeId)
    }

    @Test
    fun addingAnAlreadyActiveMember_keepsTheirJoinTimeAndAssignments() = runBlocking {
        val tid = newTask("ann-id")
        val joined = lead.db.members().get(gid, "ann-id")!!.joinedAt
        lead.repo.addMember(gid, "ann-id", "Ann").assertOk()
        assertEquals(joined, lead.db.members().get(gid, "ann-id")!!.joinedAt)
        assertEquals("ann-id", lead.db.tasks().get(tid)!!.assigneeId)
    }

    @Test
    fun aNewMember_isAnnouncedOnTheOtherPhone() = runBlocking {
        lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
        val (_, annResult) = exchangeState(lead, ann)
        assertTrue(annResult.notes.toString(), annResult.notes.contains("Eve joined Study."))
        assertNotNull(ann.db.members().get(gid, "eve-id"))
    }

    // ----- leadership chain -----

    @Test
    fun aLeadershipChange_isRelayedToAPhoneThatWasNotThere() = runBlocking {
        val (init, resp) = completeTransfer(lead, ann, gid)                                 // Lena hands over to Ann; Bob isn't there
        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        assertEquals("lead-id", bob.db.groups().get(gid)!!.leaderId)

        val (_, bobResult) = exchangeState(lead, bob)                                       // Bob learns it second-hand
        assertEquals("ann-id", bob.db.groups().get(gid)!!.leaderId)
        assertEquals(ann.db.groups().get(gid)!!.chain(), bob.db.groups().get(gid)!!.chain())
        assertTrue(bobResult.notes.toString(), bobResult.notes.contains("Ann is now the leader of Study."))

        // The new leader can lead: her task is valid on every phone.
        val tid = ann.repo.saveTask(gid, null, "From Ann", "", null, TaskStatus.TODO).id()
        exchangeState(ann, lead)
        exchangeState(ann, bob)
        assertEquals("From Ann", lead.db.tasks().get(tid)!!.title)
        assertEquals("From Ann", bob.db.tasks().get(tid)!!.title)
    }

    @Test
    fun aChainWithAnotherOrigin_isIgnoredWithEverythingElseInTheGroup() = runBlocking {
        val tid = newTask(null)
        val fake = DataMsg(
            groups = listOf(GroupDto(gid, "Study", 1, 2, listOf(LeaderEntry("evil-id", 0)))),
            members = emptyList(),
            tasks = listOf(TaskDto(tid, gid, lead.db.tasks().get(tid)!!.regs().withContent("Hacked", "", Ver(0, Long.MAX_VALUE / 2, "evil-id"))))
        )
        val before = bob.db.groups().get(gid)
        val result = bob.repo.merge(fake, "bob-id", setOf(gid))
        assertEquals(before, bob.db.groups().get(gid))
        assertEquals("Read", bob.db.tasks().get(tid)!!.title)
        assertEquals(2, result.dropped)
    }

    @Test
    fun aLongerChainWhoseLeaderIsNotAMember_isNotAdopted() = runBlocking {
        val local = bob.db.groups().get(gid)!!
        val fake = DataMsg(
            groups = listOf(GroupDto(gid, "Study", local.createdAt, local.updatedAt + 1, local.chain() + LeaderEntry("ghost-id", 99))),
            members = emptyList(), tasks = emptyList()
        )
        bob.repo.merge(fake, "bob-id", setOf(gid))
        assertEquals("lead-id", bob.db.groups().get(gid)!!.leaderId)
        assertEquals(1, bob.db.groups().get(gid)!!.chain().size)
    }

    // ----- what the screens read -----

    @Test
    fun mergedRows_areMaterializedFromTheRegisters_andClean() = runBlocking {
        val tid = newTask("ann-id")
        lead.repo.saveTask(gid, tid, "Renamed", "ch. 1", "ann-id", TaskStatus.TODO).assertOk()
        exchangeState(lead, ann)
        val row = ann.db.tasks().get(tid)!!
        assertFalse(row.dirty)                                                              // what arrived is "synced"
        assertEquals(row.regs().content.title, row.title)
        assertEquals(row.regs().lastChange().ts, row.updatedAt)
    }
}

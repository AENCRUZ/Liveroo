package com.group4.liveroo.data

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.data.model.epoch
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.id
import kotlinx.coroutines.flow.first
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
 * Baseline characterization of behavior that must survive Phase 1 unchanged
 * (no authorization is tested here: that is added in Step 3).
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryBaselineTest {
    private lateinit var alice: TestPhone

    @Before
    fun setUp() {
        alice = TestPhone(ApplicationProvider.getApplicationContext(), "alice-id", "Alice")
    }

    @After
    fun tearDown() = alice.close()

    @Test
    fun createGroup_makesCreatorLeaderAndActiveMember_marksUnsynced() = runBlocking {
        val gid = alice.repo.createGroup("  Study  ")
        val group = alice.db.groups().get(gid)
        assertNotNull(group)
        assertEquals("Study", group!!.name)
        assertEquals("alice-id", group.leaderId)
        assertTrue(group.dirty)
        val members = alice.repo.members(gid).first()
        assertEquals(listOf("alice-id"), members.map { it.userId })
    }

    @Test
    fun saveTask_createsDirtyTask_and_setStatus_updatesIt() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val tid = alice.repo.saveTask(gid, null, " Read ", " ch. 1 ", "alice-id", TaskStatus.TODO).id()
        val created = alice.db.tasks().get(tid)!!
        assertEquals("Read", created.title)
        assertEquals("ch. 1", created.description)
        assertTrue(created.dirty)

        alice.repo.setStatus(tid, TaskStatus.DONE)
        assertEquals(TaskStatus.DONE, alice.db.tasks().get(tid)!!.status)
    }

    @Test
    fun deleteTask_isSoftDelete_hiddenFromLists_butRowKept() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val tid = alice.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        alice.repo.deleteTask(tid)
        assertTrue(alice.repo.tasks(gid).first().isEmpty())
        assertTrue(alice.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun leaveGroup_recordsDeparture_andKeepsGroupReadOnlyUntilSync() = runBlocking {
        // A leader must transfer first (D1), so the member who leaves is Bob, who holds a copy of Alice's group.
        val bob = TestPhone(ApplicationProvider.getApplicationContext(), "bob-id", "Bob")
        try {
            val gid = alice.repo.createGroup("Study")
            alice.repo.addMember(gid, "bob-id", "Bob")
            bob.repo.importInvite(alice.repo.export(listOf(gid)))

            bob.repo.leaveGroup(gid).assertOk()
            assertTrue(bob.db.groups().get(gid)!!.leftByMe)
            assertNotNull(bob.db.members().get(gid, "bob-id")!!.leftAt)
            assertTrue(bob.repo.members(gid).first().none { it.userId == "bob-id" })
        } finally {
            bob.close()
        }
    }

    @Test
    fun groupLookup_forUnknownId_isNull() = runBlocking {
        assertNull(alice.db.groups().get("missing"))
    }

    // ----- Step 1b: new columns stay consistent with the flat values -----

    @Test
    fun createGroup_startsLeadershipChain_andRecordsJoinTime() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val group = alice.db.groups().get(gid)!!
        assertEquals(listOf("alice-id"), group.chain().map { it.id })
        assertEquals(group.createdAt, group.chain().single().at)
        assertEquals(0, group.epoch())
        assertEquals(group.createdAt, alice.db.members().get(gid, "alice-id")!!.joinedAt)
    }

    @Test
    fun taskRegisters_matchFlatColumns_afterEveryLocalWrite() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val tid = alice.repo.saveTask(gid, null, "Read", "ch. 1", null, TaskStatus.TODO).id()
        suspend fun check() {
            val t = alice.db.tasks().get(tid)!!
            val r = t.regs()
            assertEquals(r.content.title, t.title)
            assertEquals(r.content.description, t.description)
            assertEquals(r.assignee.id, t.assigneeId)
            assertEquals(r.projectedStatus(), t.status)
            assertEquals(r.tombstone != null, t.deleted)
            assertEquals(r.lastChange().ts, t.updatedAt)
        }
        check()
        alice.repo.saveTask(gid, tid, "Read twice", "ch. 1-2", "alice-id", TaskStatus.IN_PROGRESS)
        check()
        assertEquals("alice-id", alice.db.tasks().get(tid)!!.assigneeId)
        assertEquals(TaskStatus.IN_PROGRESS, alice.db.tasks().get(tid)!!.status)
        alice.repo.setStatus(tid, TaskStatus.DONE)
        check()
        alice.repo.deleteTask(tid)
        check()
        assertTrue(alice.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun saveTask_withNoChanges_writesNothing() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val tid = alice.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        alice.db.tasks().markClean(listOf(gid), Long.MAX_VALUE)
        alice.repo.saveTask(gid, tid, "Read", "", null, TaskStatus.TODO)
        assertTrue(!alice.db.tasks().get(tid)!!.dirty)
    }

    @Test
    fun deleteTask_isSticky_secondDeleteKeepsFirstVersion() = runBlocking {
        val gid = alice.repo.createGroup("Study")
        val tid = alice.repo.saveTask(gid, null, "Read", "", null, TaskStatus.TODO).id()
        alice.repo.deleteTask(tid)
        val first = alice.db.tasks().get(tid)!!.regs().tombstone!!.ver
        alice.repo.deleteTask(tid)
        assertEquals(first, alice.db.tasks().get(tid)!!.regs().tombstone!!.ver)
    }
}

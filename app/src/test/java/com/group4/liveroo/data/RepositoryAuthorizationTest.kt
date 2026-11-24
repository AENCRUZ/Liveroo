package com.group4.liveroo.data

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.regs
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.sync.SyncOutcome
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.completeTransfer
import com.group4.liveroo.testsupport.denial
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

/**
 * Step 3: the repository enforces D1, D2, D3 and N5 itself, from its own data (the screens may hide actions but are
 * not relied on). A refused write must leave the database exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryAuthorizationTest {
    private lateinit var lead: TestPhone
    private lateinit var ann: TestPhone     // member, gets assigned work
    private lateinit var bob: TestPhone     // member, never assigned
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
            assertNull(pull(ann))
            assertNull(pull(bob))
        }
    }

    @After
    fun tearDown() {
        lead.close(); ann.close(); bob.close()
    }

    /** Give [phone] the leader's current copy of the group (as a completed sync would). */
    private suspend fun pull(phone: TestPhone) = phone.joinFrom(lead, gid)

    private suspend fun task(assignee: String? = "ann-id"): String =
        lead.repo.saveTask(gid, null, "Read", "ch. 1", assignee, TaskStatus.TODO).id()
            .also { assertNull(pull(ann)); assertNull(pull(bob)) }

    // ----- D2: tasks and status -----

    @Test
    fun saveTask_isLeaderOnly_andARefusedWriteChangesNothing() = runBlocking {
        val before = ann.db.tasks().getAllForGroup(gid)
        assertEquals(DenyReason.NOT_LEADER, ann.repo.saveTask(gid, null, "Sneaky", "", null, TaskStatus.TODO).denial())
        assertEquals(before, ann.db.tasks().getAllForGroup(gid))
        lead.repo.saveTask(gid, null, "Real", "", null, TaskStatus.TODO).assertOk()           // the leader can
        assertEquals(1, lead.db.tasks().getAllForGroup(gid).size)
    }

    @Test
    fun editTask_byAMember_isRefused_andTheTaskIsUntouched() = runBlocking {
        val tid = task()
        val before = ann.db.tasks().get(tid)
        assertEquals(DenyReason.NOT_LEADER, ann.repo.saveTask(gid, tid, "Renamed", "", "ann-id", TaskStatus.TODO).denial())
        assertEquals(before, ann.db.tasks().get(tid))
    }

    @Test
    fun deleteTask_isLeaderOnly() = runBlocking {
        val tid = task()
        val before = ann.db.tasks().get(tid)
        assertEquals(DenyReason.NOT_LEADER, ann.repo.deleteTask(tid).denial())
        assertEquals(before, ann.db.tasks().get(tid))
        lead.repo.deleteTask(tid).assertOk()
        assertTrue(lead.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun deleteTask_twice_keepsTheFirstVersion() = runBlocking {
        val tid = task()
        lead.repo.deleteTask(tid).assertOk()
        val first = lead.db.tasks().get(tid)
        lead.repo.deleteTask(tid).assertOk()
        assertEquals(first!!.regs().tombstone, lead.db.tasks().get(tid)!!.regs().tombstone)
    }

    @Test
    fun setStatus_leaderOnAnyTask_assigneeOnOwn_nobodyElse() = runBlocking {
        val mine = task("ann-id")
        val unassigned = task(null)

        ann.repo.setStatus(mine, TaskStatus.DONE).assertOk()                                    // assignee, own task
        assertEquals(TaskStatus.DONE, ann.db.tasks().get(mine)!!.status)

        lead.repo.setStatus(unassigned, TaskStatus.IN_PROGRESS).assertOk()                      // leader, any task
        lead.repo.setStatus(mine, TaskStatus.IN_PROGRESS).assertOk()

        val bobBefore = bob.db.tasks().get(mine)
        assertEquals(DenyReason.NOT_ASSIGNEE_OR_LEADER, bob.repo.setStatus(mine, TaskStatus.DONE).denial())
        assertEquals(bobBefore, bob.db.tasks().get(mine))                                       // refused: unchanged

        assertEquals(DenyReason.NOT_ASSIGNEE_OR_LEADER, ann.repo.setStatus(unassigned, TaskStatus.DONE).denial())
    }

    @Test
    fun setStatus_onDeletedOrMissingTask_isRefused() = runBlocking {
        val tid = task()
        lead.repo.deleteTask(tid).assertOk()
        assertEquals(DenyReason.TASK_DELETED, lead.repo.setStatus(tid, TaskStatus.DONE).denial())
        assertEquals(DenyReason.TASK_NOT_FOUND, lead.repo.setStatus("missing", TaskStatus.DONE).denial())
        assertEquals(DenyReason.TASK_NOT_FOUND, lead.repo.deleteTask("missing").denial())
    }

    @Test
    fun editingADeletedTask_isRefused_soDeletionStaysSticky() = runBlocking {
        val tid = task()
        lead.repo.deleteTask(tid).assertOk()
        assertEquals(DenyReason.TASK_DELETED, lead.repo.saveTask(gid, tid, "Revived", "", null, TaskStatus.TODO).denial())
        assertTrue(lead.db.tasks().get(tid)!!.deleted)
    }

    @Test
    fun saveTask_forAnUnknownGroup_orAnotherGroupsTask_isRefused() = runBlocking {
        assertEquals(DenyReason.GROUP_NOT_FOUND, lead.repo.saveTask("nope", null, "T", "", null, TaskStatus.TODO).denial())
        val tid = task()
        val other = lead.repo.createGroup("Other")
        assertEquals(DenyReason.TASK_NOT_FOUND, lead.repo.saveTask(other, tid, "T", "", null, TaskStatus.TODO).denial())
    }

    // ----- D3: assignees -----

    @Test
    fun assignee_mustBeAnActiveMember() = runBlocking {
        assertEquals(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER, lead.repo.saveTask(gid, null, "T", "", "stranger", TaskStatus.TODO).denial())
        val gone = lead.db.members().get(gid, "bob-id")!!
        lead.db.members().upsert(gone.copy(leftAt = gone.updatedAt + 1))
        assertEquals(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER, lead.repo.saveTask(gid, null, "T", "", "bob-id", TaskStatus.TODO).denial())
        assertEquals(0, lead.db.tasks().getAllForGroup(gid).size)                               // nothing was created
        lead.repo.saveTask(gid, null, "T", "", null, TaskStatus.TODO).assertOk()                // unassigned is fine
    }

    @Test
    fun reassigningToAFormerMember_isRefused() = runBlocking {
        val tid = task("ann-id")
        val gone = lead.db.members().get(gid, "bob-id")!!
        lead.db.members().upsert(gone.copy(leftAt = gone.updatedAt + 1))
        val before = lead.db.tasks().get(tid)
        assertEquals(DenyReason.ASSIGNEE_NOT_ACTIVE_MEMBER, lead.repo.saveTask(gid, tid, "Read", "ch. 1", "bob-id", TaskStatus.TODO).denial())
        assertEquals(before, lead.db.tasks().get(tid))
    }

    // ----- members -----

    @Test
    fun addMember_isLeaderOnly() = runBlocking {
        assertEquals(DenyReason.NOT_LEADER, ann.repo.addMember(gid, "eve-id", "Eve").denial())
        assertNull(ann.db.members().get(gid, "eve-id"))
        lead.repo.addMember(gid, "eve-id", "Eve").assertOk()
        assertNotNull(lead.db.members().get(gid, "eve-id"))
        assertEquals(DenyReason.GROUP_NOT_FOUND, lead.repo.addMember("nope", "eve-id", "Eve").denial())
    }

    @Test
    fun inviteDenial_reportsWhoMayInvite() = runBlocking {
        assertNull(lead.repo.inviteDenial(gid))
        assertEquals(DenyReason.NOT_LEADER, ann.repo.inviteDenial(gid))
        assertEquals(DenyReason.GROUP_NOT_FOUND, lead.repo.inviteDenial("nope"))
    }

    // ----- D1: leaving -----

    @Test
    fun aMember_mayLeave_thenTheGroupIsReadOnly() = runBlocking {
        val tid = task("ann-id")
        ann.repo.leaveGroup(gid).assertOk()
        assertTrue(ann.db.groups().get(gid)!!.leftByMe)
        assertEquals(DenyReason.GROUP_NOT_WRITABLE, ann.repo.setStatus(tid, TaskStatus.DONE).denial())
        assertEquals(DenyReason.GROUP_NOT_WRITABLE, ann.repo.leaveGroup(gid).denial())
    }

    @Test
    fun aLeader_mustTransferLeadershipBeforeLeaving() = runBlocking {
        val before = lead.db.groups().get(gid)
        assertEquals(DenyReason.LEADER_MUST_TRANSFER, lead.repo.leaveGroup(gid).denial())
        assertEquals(before, lead.db.groups().get(gid))
        assertNull(lead.db.members().get(gid, "lead-id")!!.leftAt)

        val (init, resp) = completeTransfer(lead, ann, gid)                                     // a real handover to Ann
        assertTrue("$init / $resp", init is SyncOutcome.Success && resp is SyncOutcome.Success)
        lead.repo.leaveGroup(gid).assertOk()
        assertTrue(lead.db.groups().get(gid)!!.leftByMe)
    }

    @Test
    fun leaveGroup_unknownGroup_isRefused() = runBlocking {
        assertEquals(DenyReason.GROUP_NOT_FOUND, lead.repo.leaveGroup("nope").denial())
    }

    // ----- N5: deleting a group from this phone only -----

    @Test
    fun deleteGroupLocally_soleMemberLeader_removesEverythingHere_andOnlyHere() = runBlocking {
        val solo = lead.repo.createGroup("Solo")
        val live = lead.repo.saveTask(solo, null, "A", "", null, TaskStatus.TODO).id()
        val gone = lead.repo.saveTask(solo, null, "B", "", null, TaskStatus.TODO).id()
        lead.repo.deleteTask(gone).assertOk()                                                   // a soft-deleted row too

        lead.repo.deleteGroupLocally(solo).assertOk()

        assertNull(lead.db.groups().get(solo))
        assertTrue(lead.db.members().getAll(solo).isEmpty())
        assertNull(lead.db.tasks().get(live))
        assertNull(lead.db.tasks().get(gone))
        assertFalse(solo in lead.repo.localGroupIds())                                          // never advertised to anyone again
        assertNotNull(lead.db.groups().get(gid))                                                // other groups untouched
    }

    @Test
    fun deleteGroupLocally_doesNotTouchOtherPhonesCopies() = runBlocking {
        val tid = task()
        // Everyone else left, so the leader is the only active member and may delete locally.
        for (id in listOf("ann-id", "bob-id")) {
            val m = lead.db.members().get(gid, id)!!
            lead.db.members().upsert(m.copy(leftAt = m.updatedAt + 1))
        }
        lead.repo.deleteGroupLocally(gid).assertOk()
        assertNull(lead.db.groups().get(gid))
        assertNotNull(ann.db.groups().get(gid))                                                 // no message, no deletion elsewhere
        assertNotNull(ann.db.tasks().get(tid))
        assertNotNull(bob.db.groups().get(gid))
    }

    @Test
    fun deleteGroupLocally_isRefused_withOtherMembers_forNonLeaders_andUnknownGroups() = runBlocking {
        val before = lead.db.tasks().getAllForGroup(gid)
        assertEquals(DenyReason.OTHER_MEMBERS_PRESENT, lead.repo.deleteGroupLocally(gid).denial())
        assertEquals(DenyReason.NOT_LEADER, ann.repo.deleteGroupLocally(gid).denial())
        assertEquals(DenyReason.GROUP_NOT_FOUND, lead.repo.deleteGroupLocally("nope").denial())
        assertNotNull(lead.db.groups().get(gid))
        assertEquals(before, lead.db.tasks().getAllForGroup(gid))
        assertEquals(3, lead.db.members().getAll(gid).size)
    }
}

package com.group4.liveroo.data.model

import com.group4.liveroo.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests of the Step 1b data model: ordering, local-write constructors and JSON. */
class RegistersTest {
    private val a = Ver(0, 10, "a")

    @Test
    fun ver_ordersByEpochThenTimestampThenAuthor() {
        assertTrue(Ver(1, 1, "a") > Ver(0, 999, "z"))      // epoch dominates clocks
        assertTrue(Ver(0, 5, "a") > Ver(0, 4, "z"))
        assertTrue(Ver(0, 5, "b") > Ver(0, 5, "a"))        // deterministic tie-break
        assertEquals(0, Ver(0, 5, "a").compareTo(Ver(0, 5, "a")))
    }

    @Test
    fun statusKey_ordersByBasisFirst() {
        val oldBasis = StatusKey(basis = Ver(0, 1, "l"), ver = Ver(0, 100, "a"))
        val newBasis = StatusKey(basis = Ver(0, 2, "l"), ver = Ver(0, 5, "b"))
        assertTrue(newBasis > oldBasis)                    // a write on the newer assignment outranks a later clock
    }

    @Test
    fun create_projectsGivenValues_withNoStatusRegisterYet() {
        val r = TaskRegisters.create("Read", "ch. 1", "u2", TaskStatus.IN_PROGRESS, a)
        assertEquals(TaskStatus.IN_PROGRESS, r.projectedStatus())
        assertNull(r.status)
        assertNull(r.tombstone)
        assertEquals(a, r.lastChange())
    }

    @Test
    fun status_newerRegisterBeatsSnapshot() {
        val r = TaskRegisters.create("T", "", "u2", TaskStatus.TODO, a)
            .withStatus(TaskStatus.DONE, Ver(0, 11, "u2"))
        assertEquals(TaskStatus.DONE, r.projectedStatus())
        assertEquals(Ver(0, 11, "u2"), r.lastChange())
    }

    @Test
    fun reassignment_recordsSnapshot_ofLatestStatusSeen() {
        val r = TaskRegisters.create("T", "", "u2", TaskStatus.TODO, a)
            .withStatus(TaskStatus.DONE, Ver(0, 11, "u2"))
            .withAssignee("u3", Ver(0, 12, "a"), currentStatus = TaskStatus.DONE)
        assertEquals("u3", r.assignee.id)
        assertEquals(TaskStatus.DONE, r.assignee.snapshot.value)
        // The seen status (basis = the old assignment) is now the snapshot, so it stays effective.
        assertEquals(TaskStatus.DONE, r.projectedStatus())
        // A status written on the new assignment outranks it.
        val later = r.withStatus(TaskStatus.IN_PROGRESS, Ver(0, 13, "u3"))
        assertEquals(TaskStatus.IN_PROGRESS, later.projectedStatus())
    }

    @Test
    fun tombstone_isSticky() {
        val first = Ver(0, 20, "a")
        val r = TaskRegisters.create("T", "", null, TaskStatus.TODO, a)
            .withTombstone(first)
            .withTombstone(Ver(0, 30, "a"))
        assertEquals(first, r.tombstone!!.ver)
    }

    @Test
    fun nextTs_isStrictlyNewer_evenIfTheClockWentBackwards() {
        val r = TaskRegisters.create("T", "", null, TaskStatus.TODO, Ver(0, 1_000, "a"))
        assertEquals(1_001L, r.nextTs(now = 500))
        assertEquals(2_000L, r.nextTs(now = 2_000))
    }

    @Test
    fun registersAndChain_roundTripThroughJson() {
        val r = TaskRegisters.create("T", "d", "u2", TaskStatus.TODO, a)
            .withStatus(TaskStatus.DONE, Ver(0, 11, "u2"))
            .withTombstone(Ver(0, 12, "a"))
        assertEquals(r, ModelJson.decodeRegisters(ModelJson.encodeRegisters(r)))
        val chain = listOf(LeaderEntry("u1", 1), LeaderEntry("u2", 5))
        assertEquals(chain, ModelJson.decodeChain(ModelJson.encodeChain(chain)))
        assertNotNull(ModelJson.encodeRegisters(r))
    }
}

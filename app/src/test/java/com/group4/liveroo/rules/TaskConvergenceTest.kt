package com.group4.liveroo.rules

import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.Ver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Random

/**
 * Randomized multi-device runs of the task merge (spec section 7). Each device holds one copy of a task; devices
 * write locally (with their own skewed clocks) and exchange full state pairwise in random order, then everyone gossips
 * to a fixpoint. With honest writers the raw registers must be identical everywhere; with rogue writers (members
 * forging leader-only fields) the effective task must still be identical everywhere.
 */
class TaskConvergenceTest {
    private val ids = listOf("L", "A", "B", "C")
    private val chain = listOf(LeaderEntry("L", 0))
    private val ctx = TaskContext(chain, ids.toSet())
    private val members = ids.associateWith { MemberState(it, it, joinedAt = 0, leftAt = null, updatedAt = 0) }
    private val lookup: (String) -> MemberState? = { members[it] }
    private val statuses = TaskStatus.values()

    private class Device(val me: String, val skew: Long) {
        var regs: TaskRegisters? = null
    }

    private class Sim(val devices: List<Device>, val beforeGossip: List<TaskRegisters>)

    private fun merge(local: TaskRegisters?, incoming: TaskRegisters?): TaskRegisters? {
        if (incoming == null) return local
        return when (val r = mergeTask(local, incoming, ctx)) {
            is TaskMerge.Rejected -> local
            is TaskMerge.Merged -> r.registers
        }
    }

    private fun exchange(a: Device, b: Device) {
        val x = a.regs
        val y = b.regs
        a.regs = merge(x, y)
        b.regs = merge(y, x)
    }

    private fun effective(r: TaskRegisters) = effectiveTask(r, chain, lookup)

    private fun localOp(d: Device, rng: Random, tick: Long, rogue: Boolean) {
        val regs = d.regs ?: return
        val ver = Ver(0, regs.nextTs(tick + d.skew), d.me)
        d.regs = if (d.me == "L") {
            when (rng.nextInt(4)) {
                0 -> regs.withContent("T$tick", "", ver)
                1 -> regs.withAssignee((ids + listOf(null))[rng.nextInt(ids.size + 1)], ver, effective(regs).status)
                2 -> if (rng.nextInt(6) == 0) regs.withTombstone(ver) else regs
                else -> regs.withStatus(statuses[rng.nextInt(statuses.size)], ver)
            }
        } else if (rogue && rng.nextInt(4) == 0) {
            when (rng.nextInt(3)) {                      // a member forging a leader-only field on their own phone
                0 -> regs.withContent("forged$tick", "", ver)
                1 -> regs.withAssignee(d.me, ver, TaskStatus.DONE)
                else -> regs.withTombstone(ver)
            }
        } else {
            regs.withStatus(statuses[rng.nextInt(statuses.size)], ver)   // any member may try, assignee or not
        }
    }

    private fun simulate(seed: Long, count: Int, rogue: Boolean): Sim {
        val rng = Random(seed)
        val devices = ids.take(count).map { Device(it, skew = rng.nextInt(101) - 50L) }
        devices[0].regs = TaskRegisters.create("T0", "", "A", TaskStatus.TODO, Ver(0, 100, "L"))
        var tick = 100L
        repeat(60) {
            tick += 10
            val d = devices[rng.nextInt(devices.size)]
            if (rng.nextInt(4) < 2) localOp(d, rng, tick, rogue)
            else {
                val o = devices[rng.nextInt(devices.size)]
                if (o !== d) exchange(d, o)
            }
        }
        val before = devices.mapNotNull { it.regs }
        repeat(6) {
            for (i in devices.indices) for (j in i + 1 until devices.size) exchange(devices[i], devices[j])
        }
        return Sim(devices, before)
    }

    @Test
    fun honestWriters_endWithIdenticalRawState_onThreeAndFourDevices() {
        for (count in 3..4) for (seed in 1L..300L) {
            val sim = simulate(seed, count, rogue = false)
            sim.devices.forEach { assertNotNull("seed $seed: a device never got the task", it.regs) }
            assertEquals("seed $seed, $count devices", 1, sim.devices.map { it.regs }.distinct().size)
        }
    }

    @Test
    fun rogueWriters_stillEndWithIdenticalEffectiveTask() {
        for (count in 3..4) for (seed in 1L..300L) {
            val sim = simulate(seed, count, rogue = true)
            sim.devices.forEach { assertNotNull("seed $seed: a device never got the task", it.regs) }
            val views = sim.devices.map { effective(it.regs!!) }.distinct()
            assertEquals("seed $seed, $count devices: $views", 1, views.size)
        }
    }

    @Test
    fun rogueContentAndAssignee_neverOverrideTheLeadersOnAnyDevice() {
        for (seed in 1L..300L) {
            val sim = simulate(seed, 4, rogue = true)
            val leader = sim.devices[0].regs!!
            // Whatever members forged, every phone ends up showing the leader's own content and assignee.
            sim.devices.forEach {
                assertEquals(leader.content.title, it.regs!!.content.title)
                assertEquals(leader.assignee.id, it.regs!!.assignee.id)
            }
        }
    }

    @Test
    fun honestStates_mergeCommutesAssociatesAndIsIdempotent() {
        for (seed in 1L..300L) {
            val s = simulate(seed, 4, rogue = false).beforeGossip
            if (s.size < 3) continue
            for (a in s) {
                assertEquals(a, merge(a, a))
                for (b in s) {
                    assertEquals("seed $seed", merge(a, b), merge(b, a))
                    for (c in s) assertEquals("seed $seed", merge(merge(a, b), c), merge(a, merge(b, c)))
                }
            }
        }
    }
}

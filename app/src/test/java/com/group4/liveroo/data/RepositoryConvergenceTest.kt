package com.group4.liveroo.data

import androidx.test.core.app.ApplicationProvider
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.sync.SyncOutcome
import com.group4.liveroo.testsupport.TestPhone
import com.group4.liveroo.testsupport.assertOk
import com.group4.liveroo.testsupport.completeTransfer
import com.group4.liveroo.testsupport.exchangeState
import com.group4.liveroo.testsupport.joinFrom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Random

/**
 * End to end: 3 and 4 simulated phones, each with its own database, run random leader writes, consented leadership
 * handovers, member status changes, departures and re-adds, and random pairwise syncs through the real repository,
 * handshake and merge. After everyone
 * has exchanged with everyone (a fixpoint), every phone must hold exactly the same group, members and tasks.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryConvergenceTest {
    private val people = listOf("lead" to "Lena", "ann" to "Ann", "bob" to "Bob", "cat" to "Cat")
    private val statuses = TaskStatus.values()

    /** Successful handovers performed during a test, so the runs can't pass without exercising them. */
    private var handovers = 0

    private suspend fun gossip(phones: List<TestPhone>, rounds: Int) {
        repeat(rounds) {
            for (i in phones.indices) for (j in i + 1 until phones.size) exchangeState(phones[i], phones[j])
        }
    }

    /** The phone that currently believes it leads the group (a completed handover moves it on both phones). */
    private suspend fun currentLeader(phones: List<TestPhone>, gid: String): TestPhone =
        phones.first { it.db.groups().get(gid)?.leaderId == it.userId }

    private suspend fun leaderStep(lead: TestPhone, phones: List<TestPhone>, gid: String, rng: Random) {
        val active = lead.db.members().getAll(gid).filter { it.leftAt == null }.map { it.userId }
        val assignees = active + listOf<String?>(null)
        val tasks = lead.db.tasks().getAllForGroup(gid).filter { !it.deleted }
        val pick = if (tasks.isEmpty()) null else tasks[rng.nextInt(tasks.size)]
        when (rng.nextInt(8)) {
            0 -> if (tasks.size < 4) {
                lead.repo.saveTask(gid, null, "T${rng.nextInt(1000)}", "", assignees[rng.nextInt(assignees.size)], statuses[rng.nextInt(3)])
            }
            1 -> pick?.let { lead.repo.saveTask(gid, it.id, "R${rng.nextInt(1000)}", it.description, it.assigneeId, it.status) }
            2 -> pick?.let { lead.repo.saveTask(gid, it.id, it.title, it.description, assignees[rng.nextInt(assignees.size)], it.status) }
            3 -> pick?.let { lead.repo.setStatus(it.id, statuses[rng.nextInt(3)]) }
            4 -> if (rng.nextInt(3) == 0) pick?.let { lead.repo.deleteTask(it.id) }
            5 -> lead.repo.addMember(gid, active.random(rng), "x")                      // no-op for an active member
            else -> {                                                                   // a real, consented handover
                val candidates = phones.filter { it !== lead && it.userId in active }
                if (candidates.isNotEmpty()) {
                    val (outcome, _) = completeTransfer(lead, candidates[rng.nextInt(candidates.size)], gid)
                    if (outcome is SyncOutcome.Success) handovers++
                }
            }
        }
    }

    private suspend fun memberStep(p: TestPhone, gid: String, rng: Random, left: MutableSet<String>) {
        if (p.userId !in left && rng.nextInt(14) == 0) {
            if (p.repo.leaveGroup(gid).let { it is WriteResult.Ok }) left += p.userId
            return
        }
        val tasks = p.db.tasks().getAllForGroup(gid).filter { !it.deleted }
        if (tasks.isNotEmpty()) p.repo.setStatus(tasks[rng.nextInt(tasks.size)].id, statuses[rng.nextInt(3)])
    }

    private fun <T> List<T>.random(rng: Random): T = this[rng.nextInt(size)]

    private suspend fun simulate(seed: Long, count: Int): List<String> {
        val rng = Random(seed)
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val phones = people.take(count).map { (id, name) -> TestPhone(context, "$id-id", name) }
        try {
            val lead = phones[0]
            val gid = lead.repo.createGroup("Study")
            for (p in phones.drop(1)) lead.repo.addMember(gid, p.userId, p.name).assertOk()
            for (p in phones.drop(1)) assertNull(p.joinFrom(lead, gid))

            val left = mutableSetOf<String>()
            repeat(45) {
                val p = phones[rng.nextInt(count)]
                if (rng.nextInt(10) < 4) {
                    val leader = currentLeader(phones, gid)
                    if (p === leader) leaderStep(leader, phones, gid, rng) else memberStep(p, gid, rng, left)
                } else {
                    val q = phones[rng.nextInt(count)]
                    if (q !== p) exchangeState(p, q)
                }
            }
            gossip(phones, 5)
            // Whoever left is added back by the leader (once she knows), then everyone catches up again.
            val finalLeader = currentLeader(phones, gid)
            for (p in phones) if (p !== finalLeader) finalLeader.repo.addMember(gid, p.userId, p.name)
            gossip(phones, 5)

            return phones.map { snapshot(it, gid) }
        } finally {
            phones.forEach { it.close() }
        }
    }

    private suspend fun snapshot(p: TestPhone, gid: String): String {
        val g = p.db.groups().get(gid)!!
        val members = p.db.members().getAll(gid).sortedBy { it.userId }
            .map { "${it.userId}|${it.displayName}|${it.joinedAt}|${it.leftAt}|${it.updatedAt}" }
        val tasks = p.db.tasks().getAllForGroup(gid).sortedBy { it.id }.map { it.copy(dirty = false).toString() }
        return "${g.name}|${g.leaderId}|${g.leaderChain}\n${members.joinToString("\n")}\n${tasks.joinToString("\n")}"
    }

    @Test
    fun threePhones_endWithIdenticalState() = runBlocking {
        for (seed in 1L..20L) {
            val snaps = simulate(seed, 3)
            assertEquals("seed $seed: phones differ\n${snaps.joinToString("\n----\n")}", 1, snaps.distinct().size)
        }
        assertTrue("only $handovers handovers happened", handovers >= 10)
    }

    @Test
    fun fourPhones_endWithIdenticalState() = runBlocking {
        for (seed in 21L..40L) {
            val snaps = simulate(seed, 4)
            assertEquals("seed $seed: phones differ\n${snaps.joinToString("\n----\n")}", 1, snaps.distinct().size)
        }
        assertTrue("only $handovers handovers happened", handovers >= 10)
    }
}

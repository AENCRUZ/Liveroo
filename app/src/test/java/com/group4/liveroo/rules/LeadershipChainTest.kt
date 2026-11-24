package com.group4.liveroo.rules

import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.Ver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LeadershipChainTest {
    private fun e(id: String, at: Long) = LeaderEntry(id, at)
    private val everyone = setOf("O", "a", "b", "c", "d", "N1", "N2")

    // ----- order (spec 2.2) -----

    @Test
    fun longerChain_winsWhenOneIsAPrefix() {
        val short = listOf(e("O", 0))
        val long = listOf(e("O", 0), e("a", 5))
        assertTrue(compareChains(long, short) > 0)
        assertTrue(compareChains(short, long) < 0)
    }

    @Test
    fun atFork_smallerTimestampWins() {
        val first = listOf(e("O", 0), e("N1", 5))
        val second = listOf(e("O", 0), e("N2", 9))
        assertTrue(compareChains(first, second) > 0)
        assertTrue(compareChains(second, first) < 0)
    }

    @Test
    fun atFork_withIdenticalTimestamp_lexicographicallySmallerLeaderIdWins() {
        val n1 = listOf(e("O", 0), e("N1", 5))
        val n2 = listOf(e("O", 0), e("N2", 5))
        assertTrue(compareChains(n1, n2) > 0)
        assertTrue(compareChains(n2, n1) < 0)
        // plain string comparison: "B" < "a"
        assertTrue(compareChains(listOf(e("O", 0), e("B", 5)), listOf(e("O", 0), e("a", 5))) > 0)
    }

    @Test
    fun forkDecidesBeforeLength_soAShorterEarlierBranchBeatsALongerLaterOne() {
        val shortEarly = listOf(e("O", 0), e("N1", 5))
        val longLate = listOf(e("O", 0), e("N2", 9), e("N1", 12))
        assertTrue(compareChains(shortEarly, longLate) > 0)
    }

    @Test
    fun identicalChains_compareEqual() {
        val c = listOf(e("O", 0), e("a", 3))
        assertEquals(0, compareChains(c, c.toList()))
    }

    private fun randomChain(rng: Random): List<LeaderEntry> {
        val ids = listOf("a", "b", "c", "d")
        return listOf(e("O", 0)) + List(rng.nextInt(4)) { e(ids[rng.nextInt(ids.size)], 1L + rng.nextInt(5)) }
    }

    @Test
    fun order_isTotal_antisymmetricAndTransitive() {
        val rng = Random(1)
        repeat(2_000) {
            val a = randomChain(rng); val b = randomChain(rng); val c = randomChain(rng)
            assertEquals(Integer.signum(compareChains(a, b)), -Integer.signum(compareChains(b, a)))
            if (compareChains(a, b) >= 0 && compareChains(b, c) >= 0) assertTrue(compareChains(a, c) >= 0)
        }
    }

    // ----- merge (spec 2.2, 2.4) -----

    private fun join(x: List<LeaderEntry>, y: List<LeaderEntry>): List<LeaderEntry> =
        when (val r = mergeChains(x, y, everyone)) {
            is ChainMerge.Kept -> r.chain
            is ChainMerge.Adopted -> r.chain
            is ChainMerge.Rejected -> error("unexpected $r")
        }

    @Test
    fun merge_isCommutativeAssociativeAndIdempotent() {
        val rng = Random(2)
        repeat(2_000) {
            val a = randomChain(rng); val b = randomChain(rng); val c = randomChain(rng)
            assertEquals(join(a, b), join(b, a))
            assertEquals(join(join(a, b), c), join(a, join(b, c)))
            assertEquals(a, join(a, a))
        }
    }

    @Test
    fun merge_adoptsTheWinnerWhole_neverMixingEntries() {
        val local = listOf(e("O", 0), e("N1", 5), e("a", 7))
        val incoming = listOf(e("O", 0), e("N2", 3))           // forks earlier and wins, though shorter
        val r = mergeChains(local, incoming, everyone) as ChainMerge.Adopted
        assertEquals(incoming, r.chain)
    }

    @Test
    fun merge_keepsLocalWhenIncomingLosesOrIsIdentical() {
        val local = listOf(e("O", 0), e("N1", 5))
        assertEquals(ChainMerge.Kept(local), mergeChains(local, listOf(e("O", 0), e("N2", 9)), everyone))
        assertEquals(ChainMerge.Kept(local), mergeChains(local, local.toList(), everyone))
        assertEquals(ChainMerge.Kept(local), mergeChains(local, listOf(e("O", 0)), everyone))
    }

    @Test
    fun merge_rejectsDifferentOrigin_empty_andLeadersWhoAreNotMembers() {
        val local = listOf(e("O", 0))
        assertEquals(ChainMerge.Rejected(ChainRejection.DIFFERENT_ORIGIN), mergeChains(local, listOf(e("X", 0), e("a", 1)), everyone))
        assertEquals(ChainMerge.Rejected(ChainRejection.EMPTY), mergeChains(local, emptyList(), everyone))
        assertEquals(ChainMerge.Rejected(ChainRejection.LEADER_NOT_A_MEMBER), mergeChains(local, listOf(e("O", 0), e("ghost", 1)), everyone))
    }

    @Test
    fun merge_ofANewGroup_adoptsWhenAllLeadersAreMembers() {
        val incoming = listOf(e("O", 0), e("a", 2))
        assertEquals(ChainMerge.Adopted(incoming), mergeChains(null, incoming, everyone))
        assertEquals(ChainMerge.Rejected(ChainRejection.LEADER_NOT_A_MEMBER), mergeChains(null, incoming, setOf("O")))
    }

    // ----- leader versions -----

    @Test
    fun isLeaderVer_checksTheLeaderOfTheVersionsOwnEpoch() {
        val chain = listOf(e("O", 0), e("N1", 5))
        assertTrue(isLeaderVer(chain, Ver(0, 1, "O")))
        assertTrue(isLeaderVer(chain, Ver(1, 9, "N1")))
        assertFalse(isLeaderVer(chain, Ver(1, 9, "O")))      // the old leader after the handover
        assertFalse(isLeaderVer(chain, Ver(2, 9, "N1")))     // epoch not in the chain
        assertFalse(isLeaderVer(chain, Ver(-1, 9, "O")))
    }
}

package dev.mobium.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private var clock = 0L
    private fun match(seed: Long = 42, vararg bugs: String) = Match(seed, bugs.toSet()) { clock }

    /** Where a species' two cards are on a dealt board. */
    private fun Match.pair(id: String) = deck.indices.filter { deck[it].id == id }

    private fun Match.solve() {
        for (s in SPECIES) {
            val (a, b) = pair(s.id)
            flip(a); flip(b); settle()
        }
    }

    @Test fun aSeedDealsTheSameBoard() {
        assertEquals(match(7).deck, match(7).deck)
        assertNotEquals(match(7).deck, match(8).deck)
        assertEquals(16, match().deck.size)
        SPECIES.forEach { s -> assertEquals(2, match().deck.count { it == s }) }
    }

    @Test fun aPairMatchesAndCountsOneMove() {
        val m = match()
        val (a, b) = m.pair("monarch")
        assertTrue(m.flip(a)); assertTrue(m.flip(b))
        assertEquals(setOf(a, b), m.matched)
        assertEquals(1, m.moves)
        assertFalse(m.busy)
    }

    @Test fun aMismatchWaitsToBeTurnedBack() {
        val m = match()
        val a = m.pair("monarch")[0]
        val b = m.pair("atlas")[0]
        m.flip(a); m.flip(b)
        assertTrue(m.busy)
        assertFalse("no third card while two are up", m.flip(m.pair("owl")[0]))
        m.settle()
        assertTrue(m.faceUp.isEmpty())
        assertTrue(m.matched.isEmpty())
    }

    @Test fun theTimerStopsAtTheLastPair() {
        val m = match()
        clock = 1_000; m.flip(m.pair("morpho")[0])
        clock = 31_000; m.flip(m.pair("morpho")[1]); m.solve()
        clock = 90_000
        assertTrue(m.won)
        assertEquals(30, m.seconds())
        assertEquals(8, m.moves)
    }

    @Test fun eachBugIsCaught() {
        assertEquals(16, match(42, "score-double").apply { solve() }.moves)
        assertFalse(match(42, "pair-never").apply { solve() }.won)

        val sort = Sort(42, setOf("sort-three"))
        val order = sort.cards.sortedBy { it.wingspanCm }
        // The first three in order, the smallest last: wrong, and taken as solved.
        sort.drop(order[1], 0); sort.drop(order[2], 1); sort.drop(order[3], 2); sort.drop(order[0], 3)
        assertTrue("sort-three takes three in order as solved", sort.solved)
        val honest = Sort(42)
        honest.drop(order[1], 0); honest.drop(order[2], 1); honest.drop(order[3], 2); honest.drop(order[0], 3)
        assertFalse(honest.solved)
    }

    @Test fun sortIsSolvedSmallestToLargest() {
        val s = Sort(42)
        val order = s.cards.sortedBy { it.wingspanCm }
        assertEquals(4, order.map { it.wingspanCm }.distinct().size)
        order.reversed().forEachIndexed { i, c -> s.drop(c, i) }
        assertFalse(s.solved)
        order.forEachIndexed { i, c -> s.drop(c, i) }
        assertTrue(s.solved)
        assertTrue(s.inTray().isEmpty())
    }

    /** tests/match.test.json plays seed 42 from this deal; a change here breaks it, so it is pinned. */
    @Test fun seed42IsTheDealTheUiTestsPlay() {
        assertEquals("emerald,owl,morpho,monarch,emerald,swallowtail,atlas,monarch,swallowtail,peacock,morpho," +
            "peacock,owl,atlas,glasswing,glasswing", Match(42) { 0 }.deck.joinToString(",") { it.id })
        assertEquals("swallowtail,atlas,owl,glasswing", Sort(42).cards.joinToString(",") { it.id })
    }
}

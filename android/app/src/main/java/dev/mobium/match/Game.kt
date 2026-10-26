package dev.mobium.match

import java.util.Random

/**
 * A butterfly on a card: its name, the color its card is drawn in, and its
 * wingspan, which the sort level orders by.
 */
data class Species(val id: String, val name: String, val color: Long, val wingspanCm: Int)

val SPECIES = listOf(
    Species("morpho", "Blue Morpho", 0xFF1E5BFF, 15),
    Species("monarch", "Monarch", 0xFFF57C00, 10),
    Species("swallowtail", "Swallowtail", 0xFFFBC02D, 9),
    Species("glasswing", "Glasswing", 0xFF80CBC4, 6),
    Species("owl", "Owl", 0xFF6D4C41, 14),
    Species("peacock", "Peacock", 0xFFC62828, 6),
    Species("atlas", "Atlas", 0xFF8D6E63, 25),
    Species("emerald", "Emerald", 0xFF2E7D32, 8),
)

/** The deliberate bugs a link switches on, each something a test should catch. */
data class Bug(val id: String, val what: String)

val BUGS = listOf(
    Bug("score-double", "A pair turned over counts as two moves"),
    Bug("pair-never", "The Blue Morpho pair never matches"),
    Bug("best-lost", "A better score is not kept as the best"),
    Bug("sort-three", "The sort level is solved once three slots are in order"),
)

val BUG_IDS = BUGS.map { it.id }.toSet()

/**
 * One game of pairs: 16 cards, eight species twice, dealt from a seed so a
 * test knows where every card is. Nothing here touches Android; the clock is
 * handed in.
 */
class Match(seed: Long, private val bugs: Set<String> = emptySet(), private val now: () -> Long) {
    val deck: List<Species> = (SPECIES + SPECIES).shuffled(Random(seed))

    var faceUp: List<Int> = emptyList()
        private set
    val matched = mutableSetOf<Int>()
    var moves = 0
        private set
    private var startedAt: Long? = null
    private var finishedAt: Long? = null

    /** Two cards that did not match are up until [settle] turns them back. */
    val busy get() = faceUp.size == 2
    val won get() = matched.size == deck.size

    /** Turns card [i] over. Returns false when the turn is not allowed. */
    fun flip(i: Int): Boolean {
        if (i !in deck.indices || busy || i in matched || i in faceUp) return false
        if (startedAt == null) startedAt = now()
        faceUp = faceUp + i
        if (faceUp.size == 2) {
            moves += if ("score-double" in bugs) 2 else 1
            val (a, b) = faceUp
            val same = deck[a] == deck[b] && !(deck[a].id == "morpho" && "pair-never" in bugs)
            if (same) {
                matched += faceUp
                faceUp = emptyList()
                if (won) finishedAt = now()
            }
        }
        return true
    }

    /** Turns a pair that did not match face down again. */
    fun settle() {
        if (busy) faceUp = emptyList()
    }

    /** Whole seconds since the first card, stopped at the last pair. */
    fun seconds(): Long {
        val start = startedAt ?: return 0
        return ((finishedAt ?: now()) - start) / 1000
    }
}

/**
 * The sort level: four butterflies dealt from a seed, dragged into four
 * slots from the smallest wingspan to the largest.
 */
class Sort(seed: Long, private val bugs: Set<String> = emptySet()) {
    val cards: List<Species> = SPECIES.sortedBy { it.wingspanCm }.distinctBy { it.wingspanCm }
        .shuffled(Random(seed)).take(4)
    val slots = arrayOfNulls<Species>(4)

    /** Drops [s] into slot [i]; whatever was there goes back to the tray. */
    fun drop(s: Species, i: Int) {
        if (i !in slots.indices) return
        for (j in slots.indices) if (slots[j] == s) slots[j] = null
        slots[i] = s
    }

    fun inTray(): List<Species> = cards.filter { it !in slots }

    val solved: Boolean
        get() {
            val placed = if ("sort-three" in bugs) slots.take(3) else slots.toList()
            if (placed.any { it == null }) return false
            val spans = placed.map { it!!.wingspanCm }
            return spans == spans.sorted()
        }
}

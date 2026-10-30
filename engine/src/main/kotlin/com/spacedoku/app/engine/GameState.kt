package com.spacedoku.app.engine

/** Mutable play state for a puzzle: tracks user-entered values and exposes conflicts. */
class GameState(val topo: CubeTopology, val puzzle: Puzzle) {

    private val values: IntArray = puzzle.givens.copyOf()

    fun valueAt(i: Int): Int = values[i]
    fun isGiven(i: Int): Boolean = puzzle.givens[i] != 0
    fun snapshot(): IntArray = values.copyOf()

    fun set(i: Int, v: Int) {
        require(v in 0..topo.cfg.symbolCount)
        if (!isGiven(i)) values[i] = v
    }

    fun clear(i: Int) = set(i, 0)

    /** Fill every non-given cell with the solution (for inspecting the completed board). */
    fun revealSolution() {
        for (i in values.indices) if (!isGiven(i)) values[i] = puzzle.solution[i]
    }

    /** Indices currently involved in a conflict (an equal value within any all-different group). */
    fun conflicts(): Set<Int> {
        val bad = HashSet<Int>()
        for (g in topo.groups) {
            val firstSeen = HashMap<Int, Int>()
            for (i in g) {
                val v = values[i]
                if (v == 0) continue
                val prev = firstSeen[v]
                if (prev != null) {
                    bad.add(i); bad.add(prev)
                } else {
                    firstSeen[v] = i
                }
            }
        }
        return bad
    }

    /** Bands that contain at least one conflicting cell (drives the cross-face "red thread" visual). */
    fun conflictedBands(conflicts: Set<Int> = conflicts()): List<Int> =
        topo.bands.indices.filter { bi -> topo.bands[bi].any { it in conflicts } }

    fun isSolved(): Boolean = values.none { it == 0 } && conflicts().isEmpty()
}

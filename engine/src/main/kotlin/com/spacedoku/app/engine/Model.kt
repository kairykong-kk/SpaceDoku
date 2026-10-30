package com.spacedoku.app.engine

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The six faces of the cube surface. */
enum class Face { F, B, L, R, U, D }

/** A single cell on the cube surface, addressed in its face-local grid. */
data class CellId(val face: Face, val row: Int, val col: Int)

/**
 * Ruleset for a SpaceDoku board.
 *
 * Validated feasibility (see project plan / feasibility gate):
 *  - n=4, seamWindow=2, useBoxes=true  -> solvable, unique, good variety (v1 default)
 *  - seamWindow == n is OVER-CONSTRAINED for n=4 (no solution); becomes feasible at n=9.
 *
 * @param n          face grid size (4 for v1; 9 for the full game). Boxes need n to be a perfect square.
 * @param seamWindow size of the cross-seam "no repeat" window along a band (2..n). Higher = stronger
 *                   cross-face coupling. Intra-face rows/cols are always full Latin (window == n inside a face).
 * @param useBoxes   whether each face also enforces sqrt(n) x sqrt(n) sub-boxes (classic sudoku).
 */
/** Constraint scheme. */
enum class Scheme {
    /** Per-face rows/cols (+boxes) Latin + cross-seam windows; symbols 1..n. */
    CLASSIC,
    /** Each of the 6 faces (n*n cells) and each of the 12 rings is a full 1..(n*n) set (n=4). */
    FULLSET,
}

data class RuleConfig(
    val n: Int = 4,
    val seamWindow: Int = 2,
    val useBoxes: Boolean = true,
    val scheme: Scheme = Scheme.CLASSIC,
) {
    val boxSize: Int get() = sqrt(n.toDouble()).roundToInt()
    val hasBoxes: Boolean get() = useBoxes && boxSize * boxSize == n

    /** Symbol domain size. CLASSIC -> 1..n; FULLSET -> 1..(n*n). */
    val symbolCount: Int get() = if (scheme == Scheme.FULLSET) n * n else n

    init {
        require(n >= 2) { "n must be >= 2" }
        require(seamWindow in 2..n) { "seamWindow must be in 2..n" }
        if (scheme == Scheme.FULLSET) require(n == 4) { "FULLSET is defined for n=4 (96 cells, 1..16)" }
    }
}

/** A generated puzzle: the full solution plus the givens board (0 = empty). */
class Puzzle(
    val cfg: RuleConfig,
    val solution: IntArray,
    val givens: IntArray,
)

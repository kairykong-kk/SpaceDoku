package com.spacedoku.app.engine

import kotlin.random.Random

/** Generates unique-solution puzzles: random full solution, then dig holes while uniqueness holds. */
class Generator(private val topo: CubeTopology) {

    private val solver = Solver(topo)

    /**
     * @param targetGivens stop digging once this many givens remain (higher = easier). 0 = dig as
     *   far as uniqueness allows (hardest/minimal). The result always has exactly one solution.
     */
    fun generate(seed: Long, targetGivens: Int = 0): Puzzle {
        val rng = Random(seed)
        val solution = solver.fullSolution(rng)
            ?: error("No full solution exists for ${topo.cfg} (over-constrained). Lower seamWindow or disable boxes.")
        val givens = solution.copyOf()
        var count = topo.cellCount
        val order = (0 until topo.cellCount).toMutableList().also { it.shuffle(rng) }
        for (i in order) {
            if (targetGivens > 0 && count <= targetGivens) break
            val saved = givens[i]
            givens[i] = 0
            if (solver.countSolutions(givens, limit = 2) != 1) givens[i] = saved else count--
        }
        return Puzzle(topo.cfg, solution, givens)
    }
}

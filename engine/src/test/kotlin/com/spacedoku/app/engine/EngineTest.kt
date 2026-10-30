package com.spacedoku.app.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    private val cfg = RuleConfig(n = 4, seamWindow = 2, useBoxes = true)
    private val topo = CubeTopology(cfg)

    @Test
    fun cellCountIsSixFaces() {
        assertEquals(6 * cfg.n * cfg.n, topo.cellCount)
    }

    @Test
    fun bandTopologyInvariants() {
        assertEquals("there must be 3n bands", 3 * cfg.n, topo.bands.size)
        assertTrue("each band has length 4n", topo.bands.all { it.size == 4 * cfg.n })
        val membership = IntArray(topo.cellCount)
        for (b in topo.bands) for (c in b) membership[c]++
        assertTrue("every cell belongs to exactly 2 bands", membership.all { it == 2 })
    }

    @Test
    fun fullSolutionSatisfiesAllGroups() {
        val sol = Solver(topo).fullSolution(Random(1))!!
        assertTrue("all symbols in 1..n", sol.all { it in 1..cfg.n })
        for (g in topo.groups) {
            val seen = HashSet<Int>()
            for (i in g) assertTrue("duplicate value within a constraint group", seen.add(sol[i]))
        }
    }

    @Test
    fun generatedPuzzleIsUnique() {
        val p = Generator(topo).generate(seed = 7)
        assertEquals("puzzle must have exactly one solution", 1, Solver(topo).countSolutions(p.givens, limit = 2))
        val givens = p.givens.count { it != 0 }
        assertTrue("givens count sane (was $givens)", givens in 6..30)
    }

    @Test
    fun generationIsDeterministicPerSeed() {
        val a = Generator(topo).generate(seed = 42).givens
        val b = Generator(topo).generate(seed = 42).givens
        assertArrayEquals(a, b)
    }

    @Test
    fun seamWindowEqualToNIsOverConstrainedAtN4() {
        // Feasibility-gate finding: the full n-window over-constrains n=4 (no solution exists).
        val tight = CubeTopology(RuleConfig(n = 4, seamWindow = 4, useBoxes = true))
        assertNull(Solver(tight).fullSolution(Random(1)))
    }

    @Test
    fun conflictDetectionFlagsDuplicateInFace() {
        val p = Generator(topo).generate(seed = 7)
        val gs = GameState(topo, p)
        // find an empty cell and force a guaranteed conflict with one of its peers
        val empty = (0 until topo.cellCount).first { !gs.isGiven(it) }
        val peerVal = topo.peers[empty].map { gs.valueAt(it) }.firstOrNull { it != 0 } ?: 1
        gs.set(empty, peerVal)
        assertTrue("conflict must be detected", gs.conflicts().contains(empty))
    }
}

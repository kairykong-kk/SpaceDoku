package com.spacedoku.app.engine

import kotlin.random.Random

/**
 * Sudoku-style solver over the topology's peer graph (all constraints are all-different groups,
 * so a value is legal iff no peer already uses it). MRV heuristic + backtracking.
 */
class Solver(private val topo: CubeTopology) {

    private val n = topo.cfg.symbolCount // domain 1..symbolCount (CLASSIC: n; FULLSET: n*n)
    private val N = topo.cellCount
    private val peers = topo.peers

    /** Number of completions of [given] (0 = empty), counted up to [limit]. Use limit=2 for uniqueness. */
    fun countSolutions(given: IntArray, limit: Int = 2): Int {
        require(given.size == N)
        return solveCount(given.copyOf(), 0, limit)
    }

    /** A random complete solution, or null if the ruleset is over-constrained (no solution). */
    fun fullSolution(rng: Random): IntArray? {
        val grid = IntArray(N)
        return if (solveFill(grid, rng)) grid else null
    }

    private fun options(grid: IntArray, i: Int): MutableList<Int> {
        val used = BooleanArray(n + 1)
        for (p in peers[i]) {
            val v = grid[p]
            if (v in 1..n) used[v] = true
        }
        val opts = ArrayList<Int>(n)
        for (v in 1..n) if (!used[v]) opts.add(v)
        return opts
    }

    private fun solveCount(grid: IntArray, count: Int, limit: Int): Int {
        var best = -1
        var bestOpts: MutableList<Int>? = null
        for (i in 0 until N) {
            if (grid[i] != 0) continue
            val opts = options(grid, i)
            if (opts.isEmpty()) return count // dead end
            if (bestOpts == null || opts.size < bestOpts.size) {
                best = i; bestOpts = opts
                if (opts.size == 1) break
            }
        }
        if (best == -1) return count + 1 // fully assigned -> one solution
        var c = count
        for (v in bestOpts!!) {
            grid[best] = v
            c = solveCount(grid, c, limit)
            grid[best] = 0
            if (c >= limit) return c
        }
        return c
    }

    private fun solveFill(grid: IntArray, rng: Random): Boolean {
        var best = -1
        var bestOpts: MutableList<Int>? = null
        for (i in 0 until N) {
            if (grid[i] != 0) continue
            val opts = options(grid, i)
            if (opts.isEmpty()) return false
            if (bestOpts == null || opts.size < bestOpts.size) {
                best = i; bestOpts = opts
            }
        }
        if (best == -1) return true
        bestOpts!!.shuffle(rng)
        for (v in bestOpts) {
            grid[best] = v
            if (solveFill(grid, rng)) return true
            grid[best] = 0
        }
        return false
    }
}

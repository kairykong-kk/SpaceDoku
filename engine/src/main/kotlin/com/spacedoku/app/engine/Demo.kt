package com.spacedoku.app.engine

/**
 * Runnable sanity demo (no Android/SDK needed):  ./gradlew :engine:run
 * Prints topology invariants and a generated unique puzzle.
 */
fun main() {
    val cfg = RuleConfig(n = 4, seamWindow = 2, useBoxes = true)
    val topo = CubeTopology(cfg)
    val n = cfg.n

    check(topo.bands.size == 3 * n) { "bands=${topo.bands.size} expected ${3 * n}" }
    check(topo.bands.all { it.size == 4 * n }) { "every band must have length ${4 * n}" }
    val memb = IntArray(topo.cellCount)
    for (b in topo.bands) for (c in b) memb[c]++
    check(memb.all { it == 2 }) { "every cell must belong to exactly 2 bands" }
    println("Topology OK: cells=${topo.cellCount}, bands=${topo.bands.size} (len ${4 * n}), groups=${topo.groups.size}")

    for (target in intArrayOf(0, 64)) {
        val puzzle = Generator(topo).generate(seed = 7, targetGivens = target)
        val givens = puzzle.givens.count { it != 0 }
        val unique = Solver(topo).countSolutions(puzzle.givens, limit = 2) == 1
        val label = if (target == 0) "hard(minimal)" else "easy(target=$target)"
        println("Puzzle[$label]: givens=$givens/${topo.cellCount} -> ${if (unique) "UNIQUE ✔" else "NOT UNIQUE ✗"}")
    }
    // FULLSET (new 4x4x4 / symbols 1..16 scheme) verification
    val ftopo = CubeTopology(RuleConfig(n = 4, scheme = Scheme.FULLSET))
    println("FULLSET: symbols=${ftopo.cfg.symbolCount}, groups=${ftopo.groups.size} (6 faces + 12 rings)")
    val fp = Generator(ftopo).generate(seed = 7, targetGivens = 60)
    val fg = fp.givens.count { it != 0 }
    val funi = Solver(ftopo).countSolutions(fp.givens, limit = 2) == 1
    println("FULLSET puzzle: givens=$fg/96 -> ${if (funi) "UNIQUE ✔" else "NOT UNIQUE"}")

    val puzzle = Generator(topo).generate(seed = 7, targetGivens = 64)
    for (f in Face.entries) printFace(topo, puzzle.givens, f)
}

private fun printFace(topo: CubeTopology, board: IntArray, face: Face) {
    println("Face $face:")
    for (r in 0 until topo.n) {
        val sb = StringBuilder("  ")
        for (c in 0 until topo.n) {
            val v = board[topo.idx(CellId(face, r, c))]
            sb.append(if (v == 0) ". " else "$v ")
        }
        println(sb)
    }
}

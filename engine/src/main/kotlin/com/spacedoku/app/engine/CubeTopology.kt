package com.spacedoku.app.engine

import kotlin.math.atan2
import kotlin.math.roundToLong

/**
 * Single source of truth for the cube-surface geometry, the wrap-around bands,
 * and all "all-different" constraint groups.
 *
 * Band construction (great circles): every surface cell has a 3D center on the unit cube.
 * A band-family wraps AROUND one axis; its 4 faces are those whose normal is perpendicular
 * to that axis; a band groups cells sharing the same coordinate along that axis, ordered by
 * azimuth. This yields exactly 3n bands of length 4n, with every cell in exactly 2 bands.
 *
 * Constraint groups (all reduce to "all values distinct"):
 *  - intra-face: each row and each column of each face (full Latin), plus optional boxes.
 *  - cross-face: every cyclic window of [RuleConfig.seamWindow] cells along a band that
 *    spans >= 2 faces (the seam coupling).
 */
class CubeTopology(val cfg: RuleConfig) {

    val n: Int = cfg.n
    val cells: List<CellId>
    val cellCount: Int
    val bands: List<List<Int>>
    val groups: List<IntArray>
    val peers: Array<IntArray>

    private val indexMap: HashMap<CellId, Int>
    private val centers: Array<DoubleArray>

    private val faces = listOf(Face.F, Face.B, Face.L, Face.R, Face.U, Face.D)

    init {
        val cs = ArrayList<CellId>(6 * n * n)
        for (f in faces) for (r in 0 until n) for (c in 0 until n) cs.add(CellId(f, r, c))
        cells = cs
        cellCount = cs.size
        indexMap = HashMap(cellCount * 2)
        for (i in cs.indices) indexMap[cs[i]] = i
        centers = Array(cellCount) { center(cells[it]) }

        bands = buildBands()
        groups = buildGroups()
        peers = buildPeers()
    }

    fun idx(cell: CellId): Int = indexMap.getValue(cell)

    /** 3D center of cell [i] on the unit cube ([-1,1]^3). Reused by the 3D scene builder. */
    fun centerOf(i: Int): DoubleArray = centers[i]

    // --- geometry ---------------------------------------------------------

    private fun s(i: Int): Double = (2.0 * i + 1.0) / n - 1.0

    private fun center(cell: CellId): DoubleArray = when (cell.face) {
        Face.F -> doubleArrayOf(s(cell.col), -s(cell.row), 1.0)
        Face.B -> doubleArrayOf(-s(cell.col), -s(cell.row), -1.0)
        Face.R -> doubleArrayOf(1.0, -s(cell.row), -s(cell.col))
        Face.L -> doubleArrayOf(-1.0, -s(cell.row), s(cell.col))
        Face.U -> doubleArrayOf(s(cell.col), 1.0, s(cell.row))
        Face.D -> doubleArrayOf(s(cell.col), -1.0, -s(cell.row))
    }

    private fun key(v: Double): Long = (v * 1_000_000.0).roundToLong()

    private fun buildBands(): List<List<Int>> {
        val out = ArrayList<List<Int>>(3 * n)
        // axis indices: x=0, y=1, z=2
        out += family(setOf(Face.F, Face.B, Face.L, Face.R), constAxis = 1, a1 = 2, a2 = 0) // around Y
        out += family(setOf(Face.F, Face.B, Face.U, Face.D), constAxis = 0, a1 = 2, a2 = 1) // around X
        out += family(setOf(Face.U, Face.D, Face.L, Face.R), constAxis = 2, a1 = 1, a2 = 0) // around Z
        return out
    }

    /** Bands of one family: group the 4 faces' cells by [constAxis] coordinate, order by azimuth. */
    private fun family(faceSet: Set<Face>, constAxis: Int, a1: Int, a2: Int): List<List<Int>> {
        val byCoord = LinkedHashMap<Long, MutableList<Int>>()
        for (i in 0 until cellCount) {
            if (cells[i].face !in faceSet) continue
            byCoord.getOrPut(key(centers[i][constAxis])) { ArrayList() }.add(i)
        }
        return byCoord.entries.sortedBy { it.key }.map { e ->
            e.value.sortedBy { atan2(centers[it][a1], centers[it][a2]) }
        }
    }

    // --- constraints ------------------------------------------------------

    private fun buildGroups(): List<IntArray> {
        val grp = ArrayList<IntArray>()
        if (cfg.scheme == Scheme.FULLSET) {
            // 6 faces (each n*n cells) + 12 rings (the great-circle bands): every group is a full set.
            for (f in faces) grp.add(IntArray(n * n) { k -> idx(CellId(f, k / n, k % n)) })
            for (b in bands) grp.add(b.toIntArray())
            return grp
        }
        // CLASSIC: intra-face rows + columns (full Latin)
        for (f in faces) {
            for (r in 0 until n) grp.add(IntArray(n) { c -> idx(CellId(f, r, c)) })
            for (c in 0 until n) grp.add(IntArray(n) { r -> idx(CellId(f, r, c)) })
        }
        // optional boxes
        if (cfg.hasBoxes) {
            val bs = cfg.boxSize
            for (f in faces) {
                var br = 0
                while (br < n) {
                    var bc = 0
                    while (bc < n) {
                        val g = IntArray(n)
                        var k = 0
                        for (dr in 0 until bs) for (dc in 0 until bs) g[k++] = idx(CellId(f, br + dr, bc + dc))
                        grp.add(g)
                        bc += bs
                    }
                    br += bs
                }
            }
        }
        // cross-face seam windows
        val w = cfg.seamWindow
        for (b in bands) {
            val L = b.size
            for (i in 0 until L) {
                val win = IntArray(w) { j -> b[(i + j) % L] }
                if (win.map { cells[it].face }.toSet().size >= 2) grp.add(win)
            }
        }
        return grp
    }

    private fun buildPeers(): Array<IntArray> {
        val sets = Array(cellCount) { HashSet<Int>() }
        for (g in groups) for (a in g) for (b in g) if (a != b) sets[a].add(b)
        return Array(cellCount) { sets[it].toIntArray() }
    }
}

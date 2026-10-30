package com.spacedoku.app.content.cube

import com.spacedoku.app.engine.CellId
import com.spacedoku.app.engine.CubeTopology
import com.spacedoku.app.engine.Face
import com.spacedoku.app.engine.GameState
import com.spacedoku.app.engine.Scheme
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import com.pico.spatial.ui.foundation.content.toColor4
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Builds the cube. COLOR encodes STATE, not value. Each cell is TWO solid boxes — a full-size
 * "border backing" and a slightly smaller "fill" in front of it, so the backing's exposed rim is the
 * border. Both are plain materials, so every STATE change is just a `setBaseColor` (instant, no
 * texture work, can't overflow the GPU texture limit — that was the cause of the lag and crashes).
 *
 * The only textures are the DIGIT glyphs (white, tinted by the digit material's baseColor), created
 * lazily the first time a cell holds a value — empty cells upload nothing at build time.
 *
 * States: selected (green fill+border) > band (dark fill, BLUE border) > peer (lifted fill) > normal.
 */
class CubeSceneBuilder(
    val topo: CubeTopology,
    val state: GameState,
    private val cubeSizeMeters: Float = 0.9f,
) {
    val gridRoot: Entity = Entity()
    val entityToCell: IdentityHashMap<Entity, Int> = IdentityHashMap()

    private val fillMaterials = arrayOfNulls<UnlitMaterial>(topo.cellCount)
    private val borderMaterials = arrayOfNulls<UnlitMaterial>(topo.cellCount)
    private val digitMaterials = arrayOfNulls<UnlitMaterial>(topo.cellCount)
    private val digitEntities = arrayOfNulls<Entity>(topo.cellCount)
    private val glyphTex = arrayOfNulls<TextureResource>(topo.cellCount) // one per cell, closed on value change
    private val lastFill = IntArray(topo.cellCount) { 1 } // last applied colors (1 = unset sentinel)
    private val lastBorder = IntArray(topo.cellCount) { 1 }
    private val lastFg = IntArray(topo.cellCount) { 1 }
    private val lastValue = IntArray(topo.cellCount) { -1 }
    private val digits = DigitTextures()

    private val half = cubeSizeMeters / 2f
    private val pitch = cubeSizeMeters / topo.n
    private val thickness = pitch * 0.02f
    private val gap = 0f                 // cells touch -> borders form continuous grid lines (no gaps)
    private val borderW = pitch * 0.03f // border thickness (rim of the backing showing around the fill)
    private val outer = half - thickness / 2f
    private val fillOuter = outer + 0.0015f // fill sits just in front of the border backing
    private val digitOuter = half + 0.004f  // digit overlay floats just in front of the fill
    private val faceFillMesh = HashMap<Face, MeshResource>()
    private val faceBorderMesh = HashMap<Face, MeshResource>()
    private val toBuild = ArrayDeque<Int>().apply { for (i in 0 until topo.cellCount) add(i) }

    private var conflicts: Set<Int> = emptySet()
    private var selected: Int? = null
    private var bandCells: Set<Int> = emptySet()
    private var facePeers: Set<Int> = emptySet()

    /** True once all cells exist (construction is spread across frames so the loader keeps animating). */
    val isBuilt: Boolean get() = toBuild.isEmpty()

    fun buildBatch(max: Int) {
        var k = 0
        while (k < max && toBuild.isNotEmpty()) { createCell(toBuild.removeFirst()); k++ }
    }

    fun dispose() {
        for (t in glyphTex) runCatching { t?.close() }
    }

    /** [selectedCell] = the editable cell just tapped (null when none). Drives all state highlights. */
    fun update(conflicts: Set<Int>, selectedCell: Int?) {
        this.conflicts = conflicts
        this.selected = selectedCell
        if (selectedCell == null) {
            bandCells = emptySet(); facePeers = emptySet()
        } else {
            bandCells = topo.bands.filter { selectedCell in it }.flatten().toHashSet()
            facePeers = weakPeers(selectedCell).also { it.removeAll(bandCells); it.remove(selectedCell) }
        }
        for (i in 0 until topo.cellCount) applyState(i)
    }

    /** "Weak" related cells: the whole face (FULLSET) or the cell's box (CLASSIC). */
    private fun weakPeers(sel: Int): HashSet<Int> {
        val cell = topo.cells[sel]
        val out = HashSet<Int>()
        if (topo.cfg.scheme == Scheme.FULLSET) {
            for (r in 0 until topo.n) for (c in 0 until topo.n) out.add(topo.idx(CellId(cell.face, r, c)))
        } else {
            val bs = topo.cfg.boxSize
            val br = (cell.row / bs) * bs
            val bc = (cell.col / bs) * bs
            for (dr in 0 until bs) for (dc in 0 until bs) out.add(topo.idx(CellId(cell.face, br + dr, bc + dc)))
        }
        return out
    }

    private fun boxFor(face: Face, s: Float): MeshResource = MeshResource.createBox(
        when (face) {
            Face.F, Face.B -> Vector3(s, s, thickness)
            Face.R, Face.L -> Vector3(thickness, s, s)
            Face.U, Face.D -> Vector3(s, thickness, s)
        }
    )

    private fun fillMesh(face: Face) = faceFillMesh.getOrPut(face) { boxFor(face, pitch - gap - 2f * borderW) }
    private fun borderMesh(face: Face) = faceBorderMesh.getOrPut(face) { boxFor(face, pitch - gap) }

    private fun createCell(i: Int) {
        val c = topo.centerOf(i)
        val face = topo.cells[i].face

        // Border backing: full-size solid box at the face; its rim shows around the smaller fill.
        val borderMat = UnlitMaterial.create()
        borderMaterials[i] = borderMat
        ModelEntity(borderMesh(face), borderMat).also { b ->
            b.components[TransformComponent::class.java]?.setPosition(
                Vector3(axisPos(c[0], outer), axisPos(c[1], outer), axisPos(c[2], outer))
            )
            gridRoot.addChild(b)
        }

        // Fill: slightly smaller solid box just in front. Carries collision + hover (hover dims this).
        val fillMat = UnlitMaterial.create()
        fillMaterials[i] = fillMat
        val fill = ModelEntity(fillMesh(face), fillMat)
        fill.components[TransformComponent::class.java]?.setPosition(
            Vector3(axisPos(c[0], fillOuter), axisPos(c[1], fillOuter), axisPos(c[2], fillOuter))
        )
        fill.components.set(
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(pitch, pitch, pitch))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
        fill.components.set(InteractableComponent())
        fill.components.set(HoverEffectComponent())
        fill.setName("cell_$i")
        gridRoot.addChild(fill)
        entityToCell[fill] = i

        // Digit overlay: white glyph tinted by baseColor; hidden until the cell has a value.
        val digitMat = UnlitMaterial.create(BlendingMode.TRANSPARENT)
        digitMaterials[i] = digitMat
        val digit = ModelEntity(fillMesh(face), digitMat)
        digit.components[TransformComponent::class.java]?.let {
            it.setPosition(Vector3(axisPos(c[0], digitOuter), axisPos(c[1], digitOuter), axisPos(c[2], digitOuter)))
            it.setScaleVector(Vector3(0f, 0f, 0f)) // hidden until it has a value
        }
        gridRoot.addChild(digit)
        digitEntities[i] = digit

        applyState(i)
    }

    private fun applyState(i: Int) {
        val v = state.valueAt(i)
        val given = state.isGiven(i)

        val fillColor = when {
            i == selected && !given -> GREEN
            i in facePeers -> WEAK_BG
            else -> FACE_BG
        }
        val borderColor = when {
            i == selected && !given -> GREEN
            i in bandCells -> BAND_BORDER
            else -> DEF_BORDER
        }
        if (fillColor != lastFill[i]) { fillMaterials[i]!!.setBaseColor(Color(fillColor).toColor4()); lastFill[i] = fillColor }
        if (borderColor != lastBorder[i]) { borderMaterials[i]!!.setBaseColor(Color(borderColor).toColor4()); lastBorder[i] = borderColor }

        // Digit glyph texture only changes when the VALUE changes (rare).
        if (v != lastValue[i]) {
            val tc = digitEntities[i]!!.components[TransformComponent::class.java]
            if (v == 0) {
                tc?.setScaleVector(Vector3(0f, 0f, 0f))
                glyphTex[i]?.close(); glyphTex[i] = null
            } else {
                val t = TextureResource(digits.glyphBitmap(v))
                digitMaterials[i]!!.setBaseColorTexture(t)
                glyphTex[i]?.close(); glyphTex[i] = t
                tc?.setScaleVector(Vector3(1f, 1f, 1f))
                lastFg[i] = 1 // force a re-tint below for the freshly shown digit
            }
            lastValue[i] = v
        }

        // Digit color (conflict red > selected dark > given dim > normal). Pure baseColor tint.
        if (v != 0) {
            val fg = when {
                i in conflicts -> ERR_FG
                i == selected && !given -> ON_GREEN
                given -> DIM
                else -> PRIMARY
            }
            if (fg != lastFg[i]) { digitMaterials[i]!!.setBaseColor(Color(fg).toColor4()); lastFg[i] = fg }
        }
    }

    private fun axisPos(v: Double, outer: Float): Float =
        if (abs(v) > 0.999) (if (v > 0) outer else -outer) else (v.toFloat() * half)

    private companion object {
        const val FACE_BG = 0xFF12171A.toInt()     // normal / given / empty fill
        const val WEAK_BG = 0xFF1B2532.toInt()     // same-face/box peer fill
        const val GREEN = 0xFFB8FF3D.toInt()       // selected fill + border
        const val DEF_BORDER = 0xFF2A3340.toInt()  // normal grid border
        const val BAND_BORDER = 0xFF70D9FF.toInt() // the 2 cross-face bands
        const val ON_GREEN = 0xFF0A0E14.toInt()    // digit on the green selected cell
        const val PRIMARY = 0xFFF3F5F8.toInt()     // normal digit
        const val DIM = 0xFF8D94A5.toInt()         // given digit
        const val ERR_FG = 0xFFFF8A98.toInt()      // conflicting digit
    }
}

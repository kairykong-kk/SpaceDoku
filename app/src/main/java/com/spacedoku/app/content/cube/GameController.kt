package com.spacedoku.app.content.cube

import com.spacedoku.app.engine.CubeTopology
import com.spacedoku.app.engine.GameState
import com.spacedoku.app.engine.Generator
import com.spacedoku.app.engine.Scheme
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.geometry.Offset3D
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.sin

/**
 * 3D state machine inside one SpatialView: MENU -> PLAYING -> SOLVED -> MENU.
 *
 * Input flow (PLAYING): tap a cell to SELECT it (highlight), then tap a number on the bottom
 * number bar (1..symbolCount) to FILL it; CLEAR empties it. Single-hand drag rotates the cube.
 */
class GameController(
    private val content: SpatialViewContent,
    private val rootEntity: Entity,
    private val feedback: Feedback,
) {
    private enum class State { MENU, PLAYING, SOLVED }
    private var state = State.MENU

    private var builder: CubeSceneBuilder? = null
    private var interaction: CubeInteraction? = null
    private var completion: Completion? = null

    private val menuButtons = IdentityHashMap<Entity, Difficulty>()
    private var againButton: Entity? = null
    private val transient = ArrayList<Entity>() // menu / result entities

    // number bar
    private val numberButtons = IdentityHashMap<Entity, Int>()
    private var clearButton: Entity? = null
    private var barRoot: Entity? = null // number bar + CLEAR as one tilted group
    private var guidePanel: Entity? = null // left-side "玩法" rules card (classic modes)
    private var backButton: Entity? = null // "返回主页" under the guide panel (classic modes)

    private var selectedCell: Int? = null
    private var conflicts: Set<Int> = emptySet()
    private var solvedNotified = false

    // Async generation: built on a background thread, handed to the main thread to build entities.
    private val pendingGame = AtomicReference<Pair<CubeTopology, GameState>?>(null)
    private var loadingLabel: Entity? = null // ring anchor (head-centered)
    private var loadingRing: Entity? = null  // the glowing ring (scale-breathed)
    private var loadingShownMs = 0L
    private var buildingActive = false // cube being constructed incrementally (loader still animating)

    private var startMs = 0L
    private var placedAtHead = false
    private var cubeY = 1.5f
    private var menuRoot: Entity? = null      // all menu items, centered on the head's eye height
    private var menuPlacedAtHead = false

    private var pendingDiff: Difficulty? = null
    private var pendingAgain = false
    private var pendingBack = false
    private var pressMs = 0L

    fun begin() {
        content.addEntity(Skybox.create())
        content.addEntity(rootEntity)
        showMenu()
    }

    // --- menu --------------------------------------------------------------

    private fun showMenu() {
        state = State.MENU
        menuPlacedAtHead = false
        // The menu is one group centered on the head (FOV center); items use offsets around 0.
        val root = Entity().also {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.5f, 0f)) // default until head known
            content.addEntity(it)
        }
        menuRoot = root
        transient.add(root)
        fun item(e: Entity, localY: Float) {
            e.components[TransformComponent::class.java]?.setPosition(Vector3(0f, localY, MENU_Z))
            root.addChild(e)
        }
        item(TextPanel.label("SPACEDOKU", 1.3f, 0.24f, TEXT_PRIMARY, sizeFactor = 0.62f, spacing = 0.18f, extrude = 12), 0.595f)
        item(TextPanel.label("SELECT DIFFICULTY", 1.35f, 0.1f, TEXT_SECONDARY, sizeFactor = 0.55f, spacing = 0.05f), 0.205f)
        var ly = 0.005f
        for (d in Difficulty.entries) {
            val border = if (d == Difficulty.TEST) SPATIAL_BLUE else ACCENT_GREEN
            val btn = TextPanel.button(d.label, 0.5f, 0.16f, PANEL_GLASS, border, TEXT_PRIMARY)
            item(btn, ly)
            menuButtons[btn] = d
            ly -= 0.2f
        }
    }

    private fun clearTransient() {
        for (e in transient) runCatching { e.destroy() }
        transient.clear()
        menuButtons.clear()
        menuRoot = null
        againButton = null
    }

    // --- play --------------------------------------------------------------

    /** Generate the puzzle off the main thread (16-symbol gen is heavy); build entities when ready. */
    private fun requestGame(d: Difficulty) {
        clearTransient()
        val handle = LoadingRing.create()
        place(handle.anchor, 0f, 1.5f, MENU_Z)
        content.addEntity(handle.anchor)
        loadingLabel = handle.anchor
        loadingRing = handle.ring
        loadingShownMs = System.currentTimeMillis()
        Thread {
            runCatching {
                val topo = CubeTopology(d.config)
                val gs = GameState(topo, Generator(topo).generate(System.currentTimeMillis(), d.targetGivens))
                pendingGame.set(topo to gs)
            }
        }.start()
    }

    /** Start building the cube. Cells are created over several frames (in [updateFrame]) so the
     *  loader keeps animating instead of the main thread freezing on one big build. */
    private fun startBuild(topo: CubeTopology, gs: GameState) {
        builder = CubeSceneBuilder(topo, gs) // detached; gridRoot added to the scene only in finishBuild
        buildingActive = true
    }

    /** Cube fully built: reveal it, wire interaction + UI, and tear down the loader. */
    private fun finishBuild() {
        val b = builder ?: return
        buildingActive = false
        place(b.gridRoot, 0f, 1.5f, -2.3f)
        rootEntity.addChild(b.gridRoot)
        completion = Completion(b.gridRoot, rootEntity)
        interaction = CubeInteraction(b)
        selectedCell = null
        conflicts = emptySet()
        solvedNotified = false
        val fullset = b.topo.cfg.scheme == Scheme.FULLSET
        if (fullset) buildNumberBar(b.topo.cfg.symbolCount) // TEST: bottom number bar
        else CubeCross.add(b.gridRoot, 0.9f)                // CLASSIC: 2x2 box cross lines
        // Left-side rules card + back button for BOTH modes (scheme-specific rules text).
        val panel = GuidePanel.create(fullset).also {
            place(it, GUIDE_X, 1.45f, GUIDE_Z) // left of the cube (y re-aligned to the cube center on placement)
            it.components[TransformComponent::class.java]?.setQuaternion(Quat(Vector3(0f, 1f, 0f), 0.5f)) // angle toward user
            content.addEntity(it)
        }
        guidePanel = panel
        val backY = -(GuidePanel.panelHeightM(fullset) / 2f + 0.07f) // just below the panel (taller for SPECIAL)
        backButton = TextPanel.button("返回主页", 0.34f, 0.11f, PANEL_GLASS, SPATIAL_BLUE, TEXT_PRIMARY).also {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, backY, 0f)) // local: below the panel
            panel.addChild(it)
        }
        loadingLabel?.let { runCatching { it.destroy() } } // tear down loader only now (no blank gap)
        loadingLabel = null
        loadingRing = null
        startMs = System.currentTimeMillis()
        placedAtHead = false
        state = State.PLAYING
    }

    /** Number bar (1..symbols + CLEAR) as ONE group, tilted around its X axis to face the head. */
    private fun buildNumberBar(symbols: Int) {
        val cols = if (symbols <= 4) symbols else 8
        val gx = 0.135f
        val gy = 0.11f
        val root = Entity()
        place(root, 0f, BAR_Y, BAR_Z)
        root.components[TransformComponent::class.java]?.setQuaternion(Quat(Vector3(1f, 0f, 0f), BAR_TILT))
        content.addEntity(root)
        barRoot = root
        for (v in 1..symbols) {
            val r = (v - 1) / cols
            val c = (v - 1) % cols
            val x = (c - (cols - 1) / 2f) * gx
            val btn = TextPanel.button(v.toString(), 0.12f, 0.085f, PANEL_GLASS, SPATIAL_BLUE, TEXT_PRIMARY)
            btn.components[TransformComponent::class.java]?.setPosition(Vector3(x, -r * gy, 0f)) // local to bar
            root.addChild(btn)
            numberButtons[btn] = v
        }
        val rows = (symbols + cols - 1) / cols
        val clr = TextPanel.button("CLEAR", 0.26f, 0.085f, PANEL_GLASS, ERR_BORDER, TEXT_PRIMARY)
        clr.components[TransformComponent::class.java]?.setPosition(Vector3(0f, -rows * gy, 0f))
        root.addChild(clr)
        clearButton = clr
    }

    private fun clearBar() {
        barRoot?.let { runCatching { it.destroy() } }
        barRoot = null
        numberButtons.clear()
        clearButton = null
    }

    private fun selectCell(i: Int) {
        selectedCell = i
        feedback.place()
        builder?.update(conflicts, i)
    }

    private fun fill(v: Int) {
        val b = builder ?: return
        val sel = selectedCell ?: return
        if (b.state.isGiven(sel)) return
        b.state.set(sel, v)
        conflicts = b.state.conflicts()
        feedback.place()
        builder?.update(conflicts, sel)
        if (!solvedNotified && b.state.isSolved()) {
            solvedNotified = true
            onSolved()
        }
    }

    private fun onSolved() {
        val elapsed = System.currentTimeMillis() - startMs
        completion?.trigger(System.currentTimeMillis())
        feedback.win()
        clearBar()
        builder?.update(emptySet(), null) // clean solved cube (no selection/band highlight)
        addTransientAt(TextPanel.label("SOLVED!", 1.0f, 0.22f, ACCENT_GREEN, sizeFactor = 0.62f, spacing = 0.12f, extrude = 11), 0f, cubeY + 0.6f, CARD_Z)
        addTransientAt(TextPanel.label(fmt(elapsed), 0.6f, 0.16f, SPATIAL_BLUE, sizeFactor = 0.7f, spacing = 0.08f, extrude = 6), 0f, cubeY + 0.4f, CARD_Z)
        againButton = TextPanel.button("PLAY AGAIN", 0.44f, 0.15f, PANEL_GLASS, ACCENT_GREEN, TEXT_PRIMARY).also {
            addTransientAt(it, 0f, cubeY - 0.55f, CARD_Z)
        }
        state = State.SOLVED
    }

    private fun addTransientAt(e: Entity, x: Float, y: Float, z: Float) {
        place(e, x, y, z)
        content.addEntity(e)
        transient.add(e)
    }

    private fun clearGame() {
        clearBar()
        guidePanel?.let { runCatching { it.destroy() } } // destroys its child back button too
        guidePanel = null
        backButton = null
        runCatching { builder?.dispose() }
        runCatching { builder?.gridRoot?.destroy() }
        completion?.dispose()
        builder = null
        interaction = null
        completion = null
    }

    // --- input routing -----------------------------------------------------

    fun onTap(entity: Entity?) {
        entity ?: return
        when (state) {
            State.MENU -> menuButtons[entity]?.let { d ->
                feedback.place(); pressVisual(entity); pendingDiff = d; pressMs = System.currentTimeMillis()
            }
            State.PLAYING -> handlePlay(entity)
            State.SOLVED -> when {
                entity === againButton -> { feedback.place(); pressVisual(entity); pendingAgain = true; pressMs = System.currentTimeMillis() }
                entity === backButton -> { feedback.place(); pressVisual(entity); pendingBack = true; pressMs = System.currentTimeMillis() }
            }
        }
    }

    private fun handlePlay(entity: Entity) {
        if (interaction?.suppressTap() == true) return // mid/just-after a rotate drag
        val b = builder ?: return
        if (entity === backButton) {
            feedback.place(); pressVisual(entity); pendingBack = true; pressMs = System.currentTimeMillis(); return
        }
        val cell = b.entityToCell[entity]
        if (cell != null) {
            if (b.topo.cfg.scheme == Scheme.FULLSET) selectCell(cell) else cycleCell(cell)
            return
        }
        numberButtons[entity]?.let { fill(it); return }
        if (entity === clearButton) fill(0)
    }

    /** CLASSIC input: tap a cell to cycle its value 0..symbolCount (given cells just highlight). */
    private fun cycleCell(i: Int) {
        val b = builder ?: return
        selectedCell = i
        if (!b.state.isGiven(i)) {
            val sym = b.topo.cfg.symbolCount
            b.state.set(i, (b.state.valueAt(i) + 1) % (sym + 1))
            conflicts = b.state.conflicts()
        }
        feedback.place()
        b.update(conflicts, i)
        if (!solvedNotified && b.state.isSolved()) { solvedNotified = true; onSolved() }
    }

    private fun pressVisual(e: Entity) {
        e.components[TransformComponent::class.java]?.setScaleVector(Vector3(0.9f, 0.9f, 0.9f))
    }

    fun onDragStart() { if (state == State.PLAYING) interaction?.onDragStart() }
    fun onDrag(d: Offset3D, metersPerPx: Float) { if (state == State.PLAYING) interaction?.onDrag(d, metersPerPx) }
    fun onDragEnd() { if (state == State.PLAYING) interaction?.onDragEnd() }

    fun updateFrame(nowMs: Long, headPos: Vector3?) {
        if (pendingDiff != null && nowMs - pressMs >= PRESS_MS) {
            val d = pendingDiff!!; pendingDiff = null; requestGame(d)
        }
        // Start building as soon as the puzzle is ready (concurrent with the loader) so the work is
        // spread over the most frames possible. Reveal only once fully built AND the loader has shown
        // long enough — keeps each build frame light so the breathing stays smooth throughout.
        pendingGame.getAndSet(null)?.let { ready -> startBuild(ready.first, ready.second) }
        if (buildingActive) builder?.let { b ->
            b.buildBatch(CELL_BATCH)
            if (b.isBuilt && nowMs - loadingShownMs >= MIN_LOADING_MS) finishBuild()
        }
        loadingLabel?.let { anchor ->
            if (headPos != null) anchor.components[TransformComponent::class.java]?.setPosition(Vector3(headPos.x, headPos.y, headPos.z - 2.0f)) // FOV center, depth kept
            val s = 1f + 0.18f * sin(nowMs * 0.005027).toFloat() // glowing ring breathing in/out (~1.25s/cycle)
            loadingRing?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(s, s, s))
        }
        if ((pendingAgain || pendingBack) && nowMs - pressMs >= PRESS_MS) {
            pendingAgain = false; pendingBack = false
            feedback.resetWin(); clearTransient(); clearGame(); showMenu()
        }
        if (state == State.MENU && !menuPlacedAtHead && headPos != null) {
            menuRoot?.components?.get(TransformComponent::class.java)?.setPosition(Vector3(headPos.x, headPos.y, headPos.z))
            menuPlacedAtHead = true
        }
        if (state == State.PLAYING && !placedAtHead && headPos != null) {
            builder?.gridRoot?.components?.get(TransformComponent::class.java)
                ?.setPosition(Vector3(headPos.x, headPos.y, headPos.z - 2.3f))
            cubeY = headPos.y
            // guide panel: cube center + 6 cm
            guidePanel?.components?.get(TransformComponent::class.java)?.setPosition(Vector3(GUIDE_X, cubeY + 0.06f, GUIDE_Z))
            // number bar: horizontally centered with the cube, tilted to face the head
            barRoot?.components?.get(TransformComponent::class.java)?.setPosition(Vector3(headPos.x, BAR_Y, BAR_Z))
            val dy = headPos.y - BAR_Y
            val dz = headPos.z - BAR_Z
            val faceTilt = -atan2(dy, dz).toFloat()
            barRoot?.components?.get(TransformComponent::class.java)?.setQuaternion(Quat(Vector3(1f, 0f, 0f), faceTilt))
            placedAtHead = true
        }
        completion?.update(nowMs)
        feedback.updateAmbient(nowMs)
    }

    private fun place(e: Entity, x: Float, y: Float, z: Float) {
        e.components[TransformComponent::class.java]?.setPosition(Vector3(x, y, z))
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private companion object {
        const val MENU_Z = -2.0f // start page pushed back 20 cm
        const val CARD_Z = -1.65f
        const val BAR_Z = -1.45f   // pushed 5 cm further
        const val BAR_Y = 0.67f    // lowered so the number bar clears the cube's lower face
        const val GUIDE_X = -0.95f // guide panel: left of the cube
        const val GUIDE_Z = -1.9f
        const val BAR_TILT = -1.0236f // ~-58.6°: tilt around X (radians) so the bar faces the head; raised 30° total to shift the panel up
        const val PRESS_MS = 110L
        const val MIN_LOADING_MS = 1300L // keep the loader on screen long enough to show ≥1 full breathing loop
        const val CELL_BATCH = 4         // cells built per frame (keeps each frame light so the loader animates)
        const val PANEL_GLASS = 0xA8121621.toInt()
        const val ACCENT_GREEN = 0xFFB8FF3D.toInt()
        const val SPATIAL_BLUE = 0xFF70D9FF.toInt()
        const val ERR_BORDER = 0xFFFF6B7D.toInt()
        const val TEXT_PRIMARY = 0xFFF3F5F8.toInt()
        const val TEXT_SECONDARY = 0xFFB1B7C4.toInt()
    }
}

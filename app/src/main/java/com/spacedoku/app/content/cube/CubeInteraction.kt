package com.spacedoku.app.content.cube

import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.geometry.Offset3D

private const val ROTATE_RAD_PER_METER = 3.0f // rotation radians per METER of real hand travel (tune 2..6)
private const val TAP_SUPPRESS_MS = 150L       // ignore taps during / just after a drag

/**
 * Single-hand drag rotation of the cube. Selection + number entry live in [GameController]; this
 * only rotates [CubeSceneBuilder.gridRoot] and reports whether a tap should be suppressed (so a
 * rotate-drag never registers as a cell/number tap).
 */
class CubeInteraction(private val builder: CubeSceneBuilder) {

    private var dragging = false
    private var lastDragEndMs = 0L

    fun suppressTap(): Boolean = dragging || System.currentTimeMillis() - lastDragEndMs < TAP_SUPPRESS_MS

    fun onDragStart() {
        dragging = true
    }

    /**
     * [delta] is the per-frame drag delta in PX; [metersPerPx] converts it to real hand travel in
     * meters. Rotation is then meters * [ROTATE_RAD_PER_METER], accumulated onto current orientation.
     */
    fun onDrag(delta: Offset3D, metersPerPx: Float) {
        val tc = builder.gridRoot.components[TransformComponent::class.java] ?: return
        val yawRad = delta.x * metersPerPx * ROTATE_RAD_PER_METER
        val pitchRad = delta.y * metersPerPx * ROTATE_RAD_PER_METER
        val inc = Quat(Vector3(0f, 1f, 0f), yawRad) * Quat(Vector3(1f, 0f, 0f), pitchRad)
        tc.setQuaternion(inc * tc.quaternion)
    }

    fun onDragEnd() {
        dragging = false
        lastDragEndMs = System.currentTimeMillis()
    }
}

package com.spacedoku.app.content.cube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture

/**
 * SpaceDoku stage (Full Space). Thin shell: it wires HMD tracking, a per-frame loop, and the two
 * spatial gestures (drag = rotate, tap = select), delegating all game logic to [GameController]
 * (MENU -> PLAYING -> SOLVED -> MENU).
 */
@Composable
fun CubeStage() {
    val context = LocalContext.current
    // dragAmount from detectSpatialDragGesture is in PX; convert px -> meters so rotation sensitivity
    // is expressed per meter of real hand travel (device/density independent), not per raw pixel.
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val metersPerPx = remember(density, converter) {
        with(density) { converter.dpToLength(1f.toDp(), LengthUnit.Meters) }
    }
    val hmd = remember { HMDTrackingProvider() }
    DisposableEffect(hmd) {
        hmd.start()
        onDispose { hmd.stop() }
    }

    val feedback = remember { Feedback(context) }
    DisposableEffect(feedback) {
        feedback.startAmbient() // continuous calm ambient drone
        onDispose { feedback.release() }
    }

    val rootEntity = remember { Entity() }
    var controller by remember { mutableStateOf<GameController?>(null) }

    // Real per-frame loop (SpatialView.update is only a recomposition hook, not a frame loop).
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val c = controller ?: return@withFrameNanos
                val p = hmd.latestData.hmdPose.position
                val head = if (p.x != 0f || p.y != 0f || p.z != 0f) rootEntity.convertPositionFrom(p, null) else null
                c.updateFrame(System.currentTimeMillis(), head)
            }
        }
    }

    SpatialView(
        modifier = Modifier
            // Drag first so it gets events before tap; a pure tap (no movement) falls through to tap.
            .pointerInput(Unit) {
                detectSpatialDragGesture(
                    context,
                    TargetEntity.any(),
                    onDragStart = { controller?.onDragStart() },
                    onDragEnd = { controller?.onDragEnd() },
                    onDragCancel = { controller?.onDragEnd() },
                ) { value -> controller?.onDrag(value.dragAmount, metersPerPx) }
            }
            .pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                    controller?.onTap(value.targetEntity)
                }
            },
        initial = { content, _ ->
            GameController(content, rootEntity, feedback).also {
                it.begin()
                controller = it
            }
        }
    )
}

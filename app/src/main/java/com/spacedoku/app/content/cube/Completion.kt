package com.spacedoku.app.content.cube

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3

/**
 * Completion ("you solved it") sequence, driven from the per-frame loop:
 *  1. shrink the cube to 50% while slerping it so its body diagonal stands VERTICAL (one vertex up,
 *     the opposite vertex down — the two green points aligned on a vertical axis);
 *  2. then spin slowly about that vertical diagonal (world Y);
 *  3. looping fireworks far in the background.
 *
 * The whole-cube gold tint is applied separately by [CubeSceneBuilder.celebrate].
 */
class Completion(private val gridRoot: Entity, fireworksParent: Entity) {

    private val fireworks = Fireworks(fireworksParent)

    // Stand the cube on a corner: align body diagonal (1,1,1) with world up.
    // yaw 45 deg, then tilt ~54.74 deg about Z so the (1,1,1) vertex points straight up.
    private val targetQuat = Quat(Vector3(0f, 0f, 1f), 0.9553f) * Quat(Vector3(0f, 1f, 0f), 0.7854f)

    private var active = false
    private var startMs = 0L
    private var lastMs = 0L
    private var startQuat = Quat.identity()
    private var spinAngle = 0f

    fun dispose() = fireworks.dispose()

    fun trigger(nowMs: Long) {
        active = true
        startMs = -1L // start the clock on the first real frame, not on the heavy onSolved frame (no jump)
        startQuat = gridRoot.components[TransformComponent::class.java]?.quaternion ?: Quat.identity()
        fireworks.start()
    }

    fun update(nowMs: Long) {
        if (!active) return
        if (startMs < 0L) { startMs = nowMs; lastMs = nowMs } // first frame after trigger: anchor timing here
        val dt = (nowMs - lastMs).coerceAtLeast(0L).toFloat() / 1000f
        lastMs = nowMs
        val tc = gridRoot.components[TransformComponent::class.java] ?: return

        val elapsed = nowMs - startMs
        if (elapsed < SHRINK_MS) {
            val t = elapsed.toFloat() / SHRINK_MS
            val s = 1f + (TARGET_SCALE - 1f) * t
            tc.setScaleVector(Vector3(s, s, s))
            tc.setQuaternion(Quat.slerp(startQuat, targetQuat, t))
        } else {
            tc.setScaleVector(Vector3(TARGET_SCALE, TARGET_SCALE, TARGET_SCALE))
            spinAngle += dt * SPIN_SPEED
            // spin about world Y == about the now-vertical body diagonal
            tc.setQuaternion(Quat(Vector3(0f, 1f, 0f), spinAngle) * targetQuat)
        }
        fireworks.update(dt)
    }

    private companion object {
        const val SHRINK_MS = 1100L
        const val TARGET_SCALE = 0.5f
        const val SPIN_SPEED = 0.3f // rad/s, gentle
    }
}

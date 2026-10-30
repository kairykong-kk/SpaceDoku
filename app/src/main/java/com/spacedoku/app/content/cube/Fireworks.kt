package com.spacedoku.app.content.cube

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.toColor4
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Looping particle fireworks placed far in the background (so Stage's distance-based render priority
 * keeps them behind the cube). A burst of [count] additive glowing points explodes outward, arcs
 * under gravity, and fades (scale -> 0); when it dies a new burst launches at a random far point.
 */
class Fireworks(parent: Entity, private val count: Int = 60) {

    private val rng = Random(7)
    private val mesh = MeshResource.createSphere(0.07f)
    private val mats = listOf(0xFF22D3EE, 0xFFFF8A4C, 0xFF34E89B, 0xFF8B7BFF, 0xFFFFE7A8)
        .map { UnlitMaterial.create(BlendingMode.ADD).apply { setBaseColor(Color(it).toColor4()) } }

    private val particles = Array(count) { i ->
        ModelEntity(mesh, mats[i % mats.size]).also {
            it.components[TransformComponent::class.java]?.setScaleVector(Vector3(0f, 0f, 0f))
            parent.addChild(it)
        }
    }
    private val px = FloatArray(count); private val py = FloatArray(count); private val pz = FloatArray(count)
    private val vx = FloatArray(count); private val vy = FloatArray(count); private val vz = FloatArray(count)

    private var age = 0f
    private val life = 1.7f
    private var running = false

    fun start() {
        running = true
        launch()
    }

    private fun launch() {
        age = 0f
        val cx = (rng.nextFloat() - 0.5f) * 16f
        val cy = 1.5f + rng.nextFloat() * 4f
        val cz = -(10f + rng.nextFloat() * 8f) // far in front
        for (i in 0 until count) {
            val z = rng.nextFloat() * 2f - 1f
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val r = sqrt(1f - z * z)
            val sp = 2.5f + rng.nextFloat() * 2.8f
            vx[i] = r * cos(a) * sp; vy[i] = r * sin(a) * sp; vz[i] = z * sp
            px[i] = cx; py[i] = cy; pz[i] = cz
        }
    }

    fun dispose() {
        for (p in particles) runCatching { p.destroy() }
    }

    /** Advance one frame by [dt] seconds. */
    fun update(dt: Float) {
        if (!running) return
        age += dt
        if (age >= life) { launch(); return }
        val k = 1f - age / life // fade by shrinking
        for (i in 0 until count) {
            vy[i] -= 3.0f * dt // gravity
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
            val tc = particles[i].components[TransformComponent::class.java] ?: continue
            tc.setPosition(Vector3(px[i], py[i], pz[i]))
            tc.setScaleVector(Vector3(k, k, k))
        }
    }
}

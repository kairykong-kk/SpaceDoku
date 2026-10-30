package com.spacedoku.app.content.cube

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.toColor4
import kotlin.random.Random

/** A large inside-out sphere textured with a layered procedural nebula + starfield (cosmic backdrop). */
object Skybox {

    fun create(radius: Float = 25f): Entity {
        val tex = TextureResource(nebulaBitmap(1536))
        val mat = UnlitMaterial.create().apply {
            setBaseColor(Color.White.toColor4())
            setBaseColorTexture(tex)
            setCullingMode(MaterialCullingMode.NONE) // viewed from the inside
        }
        return ModelEntity(MeshResource.createSphere(radius), mat).also { e ->
            e.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0f, 0f))
        }
    }

    private fun nebulaBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng = Random(20260617L)
        val s = size.toFloat()

        // 1) deep blue-gray gradient base + a soft central glow (style spec)
        Paint().apply {
            shader = LinearGradient(0f, 0f, s, s, 0xFF0D1019.toInt(), 0xFF06070B.toInt(), Shader.TileMode.CLAMP)
        }.also { canvas.drawRect(0f, 0f, s, s, it) }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(s * 0.5f, s * 0.4f, s * 0.34f, 0x333A4965, 0x00000000, Shader.TileMode.CLAMP)
        }.also { canvas.drawRect(0f, 0f, s, s, it) }

        // 2) a couple of very subtle blue-gray nebula clouds (restrained)
        val hues = intArrayOf(0x24283C5A, 0x1C1E5A6E)
        for (h in hues) {
            repeat(2) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = h
                    maskFilter = BlurMaskFilter(s * 0.07f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(rng.nextFloat() * s, rng.nextFloat() * s, s * (0.10f + rng.nextFloat() * 0.22f), p)
            }
        }

        // 3) dim star field
        val star = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(1400) {
            star.color = AColor.argb((rng.nextFloat() * 150 + 40).toInt(), 255, 255, 255)
            canvas.drawCircle(rng.nextFloat() * s, rng.nextFloat() * s, rng.nextFloat() * 1.4f + 0.3f, star)
        }

        // 5) bright glow stars (radial halo + white core), tinted blue/white/warm
        val tints = intArrayOf(0xFFFFFFFF.toInt(), 0xFF9FD8FF.toInt(), 0xFFFFE3C0.toInt())
        repeat(40) {
            val x = rng.nextFloat() * s
            val y = rng.nextFloat() * s
            val r = rng.nextFloat() * 6f + 3f
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r, tints[rng.nextInt(tints.size)], 0x00000000, Shader.TileMode.CLAMP)
            }.also { canvas.drawCircle(x, y, r, it) }
            star.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, r * 0.3f, star)
        }
        return bmp
    }
}

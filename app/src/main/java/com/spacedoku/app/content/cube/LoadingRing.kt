package com.spacedoku.app.content.cube

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.toColor4

/**
 * Loading indicator: a glowing green ring (#B8FF3D) with a static GENERATING label below.
 * [Handle.ring] is scale-"breathed" every frame by the caller until the puzzle is ready.
 */
object LoadingRing {
    class Handle(val anchor: Entity, val ring: Entity)

    private const val GREEN = 0xFFB8FF3D.toInt()

    fun create(): Handle {
        val anchor = Entity()
        val mat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color.White.toColor4())
            setBaseColorTexture(TextureResource(renderRing(256)))
            setCullingMode(MaterialCullingMode.NONE)
        }
        val ring = ModelEntity(MeshResource.createPlane(0.176f, 0.176f, 0f), mat).also { // +10%
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0.05f, 0f))
            anchor.addChild(it)
        }
        TextPanel.label("GENERATING", 1.0f, 0.14f, 0xFFB1B7C4.toInt(), sizeFactor = 0.55f, spacing = 0.06f).also {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, -0.13f, 0f))
            anchor.addChild(it)
        }
        return Handle(anchor, ring)
    }

    private fun renderRing(s: Int): Bitmap {
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val r = s * 0.34f
        // Outer glow (blurred stroke), then a crisp ring on top.
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.06f; color = GREEN
            maskFilter = BlurMaskFilter(s * 0.06f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.035f; color = GREEN
        })
        return bmp
    }
}

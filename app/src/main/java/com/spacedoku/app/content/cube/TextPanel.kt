package com.spacedoku.app.content.cube

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.toColor4

/**
 * Flat (plane-based) text panels with TRANSPARENT blending — dark-glass tech style.
 *  - button(): semi-transparent rounded glass + accent border + text, tappable.
 *  - label():  text only (optional painted "extrude" for a 3D look), non-interactive.
 */
object TextPanel {

    fun button(text: String, widthM: Float, heightM: Float, glassArgb: Int, borderArgb: Int, fgArgb: Int): Entity =
        plane(renderButton(text, px(widthM), px(heightM), glassArgb, borderArgb, fgArgb), widthM, heightM, interactive = true)

    fun label(
        text: String,
        widthM: Float,
        heightM: Float,
        fgArgb: Int,
        sizeFactor: Float = 0.6f,
        spacing: Float = 0.18f,
        extrude: Int = 0,
    ): Entity = plane(renderLabel(text, px(widthM), px(heightM), fgArgb, sizeFactor, spacing, extrude), widthM, heightM, interactive = false)

    private fun px(m: Float): Int = (m * 900f).toInt().coerceIn(64, 1536)

    private fun plane(bmp: Bitmap, widthM: Float, heightM: Float, interactive: Boolean): Entity {
        val mat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color.White.toColor4())
            setBaseColorTexture(TextureResource(bmp))
            setCullingMode(MaterialCullingMode.NONE)
        }
        val e = ModelEntity(MeshResource.createPlane(widthM, heightM, 0f), mat)
        if (interactive) {
            e.components.set(
                CollisionComponent(
                    collisionShape = listOf(ShapeResource.createBox(Vector3(widthM, heightM, 0.06f))),
                    physicsMaterial = PhysicsMaterialResource(),
                )
            )
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
        }
        return e
    }

    private fun renderButton(text: String, w: Int, h: Int, glassArgb: Int, borderArgb: Int, fgArgb: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = h * 0.18f // medium, restrained radius
        val inset = h * 0.04f
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glassArgb })
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = h * 0.018f
            color = borderArgb
        })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgArgb
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            textSize = h * 0.4f
        }
        canvas.drawText(text, w / 2f, h / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return bmp
    }

    private fun renderLabel(text: String, w: Int, h: Int, fgArgb: Int, sizeFactor: Float, spacing: Float, extrude: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lines = text.split("\n")
        val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgArgb
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = spacing
            textSize = h * sizeFactor / (if (lines.size > 1) 1.5f else 1f)
        }
        val lineH = front.descent() - front.ascent()
        val firstY = h / 2f - (lineH * lines.size) / 2f - front.ascent()
        val cx = w / 2f
        if (extrude > 0) {
            val shadow = Paint(front).apply { color = darken(fgArgb, 0.32f) }
            for (k in extrude downTo 1) {
                val off = k * 1.3f
                lines.forEachIndexed { i, ln -> canvas.drawText(ln, cx + off, firstY + i * lineH + off, shadow) }
            }
        }
        lines.forEachIndexed { i, ln -> canvas.drawText(ln, cx, firstY + i * lineH, front) }
        return bmp
    }

    private fun darken(argb: Int, f: Float): Int {
        val r = ((argb shr 16 and 0xFF) * f).toInt()
        val g = ((argb shr 8 and 0xFF) * f).toInt()
        val b = ((argb and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

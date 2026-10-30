package com.spacedoku.app.content.cube

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.ui.foundation.content.toColor4

/**
 * The "玩法" rules card placed to the LEFT of the cube. Static info panel; one textured glass plane.
 * The FULLSET (SPECIAL) variant also draws a small cube diagram under each rule: rule 1 = one full
 * green face; rule 2 = a green ring wrapping the faces.
 */
object GuidePanel {
    private const val GREEN = 0xFFB8FF3D.toInt()
    private const val WHITE = 0xFFF3F5F8.toInt()
    private const val SEC = 0xFF9CA6B6.toInt()
    private const val BLUE = 0xFF70D9FF.toInt()
    private const val LINE = 0xFF6E7E92.toInt() // cube grid lines

    private const val W = 560
    private const val MODE_FACE = 0
    private const val MODE_H = 1   // ring around the vertical axis (horizontal wrap)
    private const val MODE_V = 2   // ring around the left-right axis (over the top)
    private const val MODE_D = 3   // ring around the front-back axis (over the top-right)
    private const val MODE_ROW = 4 // single face: one horizontal row
    private const val MODE_COL = 5 // single face: one vertical column

    private fun heightPx(fullset: Boolean) = if (fullset) 860 else 800

    fun panelHeightM(fullset: Boolean, widthM: Float = 0.56f): Float = widthM * heightPx(fullset) / W

    fun create(fullset: Boolean, widthM: Float = 0.56f): Entity {
        val bmp = render(W, heightPx(fullset), fullset)
        val mat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color.White.toColor4())
            setBaseColorTexture(TextureResource(bmp))
            setCullingMode(MaterialCullingMode.NONE)
        }
        return ModelEntity(MeshResource.createPlane(widthM, panelHeightM(fullset, widthM), 0f), mat)
    }

    private fun render(w: Int, h: Int, fullset: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF00E1320.toInt() })

        val padL = 56f
        val bodyX = 124f
        fun text(s: String, x: Float, y: Float, size: Float, col: Int, bold: Boolean = false) {
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = col; textSize = size
                typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            })
        }
        fun divider(y: Float) = c.drawLine(padL, y, w - padL, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF273140.toInt(); strokeWidth = 1.5f
        })

        text("玩法", padL, 72f, 26f, GREEN, true)
        if (fullset) {
            text("六个面，", padL, 150f, 58f, WHITE, true)
            text("十二条环。", padL, 214f, 58f, WHITE, true)

            divider(250f)
            text("01", padL, 306f, 30f, GREEN, true)
            text("每个面（4×4 共 16 格）都是", bodyX, 300f, 27f, SEC)
            text("1–16 的完整集合。", bodyX, 336f, 27f, SEC)
            drawCube(c, bodyX, 360f, 60f, MODE_FACE) // one full green face (left-aligned with the text)

            divider(472f)
            text("02", padL, 526f, 30f, GREEN, true)
            text("3 个方向各 4 条、共 12 条环线，", bodyX, 520f, 26f, SEC)
            text("每条绕 4 个面贯穿 16 格，", bodyX, 556f, 26f, SEC)
            text("也是 1–16 的完整集合。", bodyX, 592f, 26f, SEC)
            // three cubes = the three orthogonal ring directions (left-aligned with the body text)
            drawCube(c, bodyX, 628f, 58f, MODE_H)
            drawCube(c, bodyX + 144f, 628f, 58f, MODE_V)
            drawCube(c, bodyX + 288f, 628f, 58f, MODE_D)

            divider(742f)
            text("↔", padL, 800f, 38f, BLUE, true)
            text("拖动立方体查看各面", bodyX, 786f, 24f, SEC)
            text("点格子后从底部数字条选 1–16", bodyX, 822f, 24f, SEC)
        } else {
            text("六个面，", padL, 150f, 58f, WHITE, true)
            text("同一道题。", padL, 214f, 58f, WHITE, true)

            divider(250f)
            text("01", padL, 306f, 30f, GREEN, true)
            text("每个面中，每行、每列和每个 2×2", bodyX, 300f, 27f, SEC)
            text("宫都包含 1–4。", bodyX, 336f, 27f, SEC)
            // face / row / column (left-aligned with the body text)
            drawCube(c, bodyX, 360f, 58f, MODE_FACE)
            drawCube(c, bodyX + 144f, 360f, 58f, MODE_ROW)
            drawCube(c, bodyX + 288f, 360f, 58f, MODE_COL)

            divider(478f)
            text("02", padL, 532f, 30f, GREEN, true)
            text("跨过接缝后，相邻的对应格不能", bodyX, 526f, 27f, SEC)
            text("出现相同数字。", bodyX, 564f, 27f, SEC)

            divider(606f)
            text("↔", padL, 666f, 38f, BLUE, true)
            text("拖动立方体查看各面", bodyX, 652f, 25f, SEC)
            text("点击格子后输入数字", bodyX, 690f, 25f, SEC)
        }
        return bmp
    }

    /**
     * A small 3-face cube (front + top + right), each face a full 4×4 grid. [mode] selects the green
     * highlight: a whole face (rule 1) or a ring wrapping in one of the three orthogonal directions.
     */
    private fun drawCube(c: Canvas, fx: Float, fy0: Float, s: Float, mode: Int) {
        val d = s * 0.42f
        val fy = fy0 + d // leave room above for the top face
        // face parameterizations: u=across, w=down, v=depth (all 0..1)
        fun front(u: Float, w: Float) = floatArrayOf(fx + u * s, fy + w * s)
        fun top(u: Float, v: Float) = floatArrayOf(fx + u * s + v * d, fy - v * d)
        fun right(v: Float, w: Float) = floatArrayOf(fx + s + v * d, fy + w * s - v * d)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LINE; strokeWidth = 1.8f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
        }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; style = Paint.Style.FILL }
        fun quad(p: Paint, vararg pts: FloatArray) {
            val path = Path()
            path.moveTo(pts[0][0], pts[0][1])
            for (i in 1 until pts.size) path.lineTo(pts[i][0], pts[i][1])
            path.close(); c.drawPath(path, p)
        }
        fun seg(a: FloatArray, b: FloatArray) = c.drawLine(a[0], a[1], b[0], b[1], line)
        val q = 0.25f

        // green highlight (drawn under the grid)
        when (mode) {
            MODE_FACE -> quad(green, front(0f, 0f), front(1f, 0f), front(1f, 1f), front(0f, 1f))
            MODE_ROW -> quad(green, front(0f, q), front(1f, q), front(1f, 2 * q), front(0f, 2 * q)) // one row, this face
            MODE_COL -> quad(green, front(q, 0f), front(2 * q, 0f), front(2 * q, 1f), front(q, 1f)) // one column, this face
            MODE_H -> { // a horizontal row wrapping front -> right
                quad(green, front(0f, q), front(1f, q), front(1f, 2 * q), front(0f, 2 * q))
                quad(green, right(0f, q), right(1f, q), right(1f, 2 * q), right(0f, 2 * q))
            }
            MODE_V -> { // a vertical column wrapping front -> over the top
                quad(green, front(q, 0f), front(2 * q, 0f), front(2 * q, 1f), front(q, 1f))
                quad(green, top(q, 0f), top(2 * q, 0f), top(2 * q, 1f), top(q, 1f))
            }
            MODE_D -> { // a band wrapping over the top -> down the right (front-back axis)
                quad(green, top(0f, q), top(1f, q), top(1f, 2 * q), top(0f, 2 * q))
                quad(green, right(q, 0f), right(q, 1f), right(2 * q, 1f), right(2 * q, 0f))
            }
        }

        // full 4x4 grid on all three faces
        for (k in 0..4) {
            val f = k / 4f
            seg(front(f, 0f), front(f, 1f)); seg(front(0f, f), front(1f, f))
            seg(top(f, 0f), top(f, 1f)); seg(top(0f, f), top(1f, f))
            seg(right(f, 0f), right(f, 1f)); seg(right(0f, f), right(1f, f))
        }
    }
}

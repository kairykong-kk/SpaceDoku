package com.spacedoku.app.content.cube

import androidx.compose.ui.graphics.Color
import com.spacedoku.app.engine.Face
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.toColor4

/**
 * Adds a "+" line on each of the 6 faces — children of the cube's gridRoot, so they rotate with it
 * and are destroyed with it. The cross sits on the 2x2 box boundary, visually splitting each 4x4
 * face into four boxes (CLASSIC modes only).
 */
object CubeCross {
    fun add(gridRoot: Entity, cubeSizeMeters: Float) {
        val half = cubeSizeMeters / 2f
        val len = cubeSizeMeters * 0.96f
        val thin = 0.004f
        val out = half - thin / 2f + 0.0005f // flush with the cell surface (0.5 mm proud to avoid z-fighting)
        val mat = UnlitMaterial.create().apply { setBaseColor(Color(0xFFF3F5F8).toColor4()) } // white

        for (face in Face.entries) {
            val na: Int
            val sign: Float
            when (face) {
                Face.F -> { na = 2; sign = 1f }
                Face.B -> { na = 2; sign = -1f }
                Face.R -> { na = 0; sign = 1f }
                Face.L -> { na = 0; sign = -1f }
                Face.U -> { na = 1; sign = 1f }
                Face.D -> { na = 1; sign = -1f }
            }
            for (longAxis in (0..2).filter { it != na }) {
                val ext = floatArrayOf(thin, thin, thin); ext[longAxis] = len
                val pos = floatArrayOf(0f, 0f, 0f); pos[na] = sign * out
                val e = ModelEntity(MeshResource.createBox(Vector3(ext[0], ext[1], ext[2])), mat)
                e.components[TransformComponent::class.java]?.setPosition(Vector3(pos[0], pos[1], pos[2]))
                gridRoot.addChild(e)
            }
        }
    }
}

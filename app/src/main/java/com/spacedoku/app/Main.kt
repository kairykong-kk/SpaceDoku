package com.spacedoku.app

import com.spacedoku.app.content.cube.CubeStage
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) = with(scope) { DefaultStage { PicoTheme { CubeStage() } } }




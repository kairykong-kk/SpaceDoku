package com.spacedoku.app.content.cube

import com.spacedoku.app.engine.RuleConfig
import com.spacedoku.app.engine.Scheme

/** A start-menu choice: its ruleset + how many givens (difficulty). */
enum class Difficulty(val label: String, val targetGivens: Int, val config: RuleConfig) {
    EASY("EASY", 80, RuleConfig(n = 4, seamWindow = 2, useBoxes = true)),
    MEDIUM("MEDIUM", 44, RuleConfig(n = 4, seamWindow = 2, useBoxes = true)),
    // 0 = dig as far as uniqueness allows -> the hardest the classic ruleset offers (minimal givens).
    HARD("HARD", 0, RuleConfig(n = 4, seamWindow = 2, useBoxes = true)),

    /** New 4x4x4 / symbols 1..16 scheme (6 faces + 12 rings as full sets). Easy givens + candidate aid. */
    TEST("SPECIAL", 75, RuleConfig(n = 4, scheme = Scheme.FULLSET)),
}

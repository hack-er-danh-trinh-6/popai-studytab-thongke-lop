package com.health.pop.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusStreakRulesTest {

    @Test
    fun earnsStreak_onlyFromSixtyMinutes() {
        assertFalse(FocusStreakRules.earnsStreak(0))
        assertFalse(FocusStreakRules.earnsStreak(59))
        assertTrue(FocusStreakRules.earnsStreak(60))
        assertTrue(FocusStreakRules.earnsStreak(120))
    }

    @Test
    fun clampTarget_roundsToStepAndStaysInRange() {
        assertEquals(5, FocusStreakRules.clampTarget(1))
        assertEquals(25, FocusStreakRules.clampTarget(25))
        assertEquals(25, FocusStreakRules.clampTarget(26))
        assertEquals(30, FocusStreakRules.clampTarget(28))
        assertEquals(60, FocusStreakRules.clampTarget(60))
        assertEquals(180, FocusStreakRules.clampTarget(999))
    }

    @Test
    fun presets_areValidTargets() {
        FocusStreakRules.PRESET_MINUTES.forEach { assertEquals(it, FocusStreakRules.clampTarget(it)) }
        assertTrue(FocusStreakRules.PRESET_MINUTES.contains(FocusStreakRules.STREAK_MINUTES))
    }
}

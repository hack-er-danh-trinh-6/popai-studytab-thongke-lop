package com.health.pop.core.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRulesTest {

    @Test
    fun noLimitMeansNoStage() {
        assertEquals(UsageRules.STAGE_NONE, UsageRules.stageFor(500, 0))
    }

    @Test
    fun stagesFollowUsage() {
        assertEquals(UsageRules.STAGE_NONE, UsageRules.stageFor(47, 60))
        assertEquals(UsageRules.STAGE_WARNING, UsageRules.stageFor(48, 60))
        assertEquals(UsageRules.STAGE_OVER, UsageRules.stageFor(60, 60))
        assertEquals(UsageRules.STAGE_OVER, UsageRules.stageFor(95, 60))
    }

    @Test
    fun veryShortLimitSkipsWarning() {
        assertEquals(UsageRules.STAGE_NONE, UsageRules.stageFor(3, 4))
        assertEquals(UsageRules.STAGE_OVER, UsageRules.stageFor(4, 4))
    }

    @Test
    fun notifiesOnceWhenStageIncreases() {
        assertTrue(UsageRules.shouldNotify(UsageRules.STAGE_WARNING, 0, false, 0, 0, 15))
        assertFalse(UsageRules.shouldNotify(UsageRules.STAGE_WARNING, UsageRules.STAGE_WARNING, true, 0, 0, 15))
        assertTrue(UsageRules.shouldNotify(UsageRules.STAGE_OVER, UsageRules.STAGE_WARNING, false, 0, 0, 15))
    }

    @Test
    fun repeatsOverReminderOnlyWhileStillUsing() {
        val now = 100 * 60_000L
        val recent = now - 5 * 60_000L
        val old = now - 20 * 60_000L
        assertFalse(UsageRules.shouldNotify(UsageRules.STAGE_OVER, UsageRules.STAGE_OVER, true, now, recent, 15))
        assertTrue(UsageRules.shouldNotify(UsageRules.STAGE_OVER, UsageRules.STAGE_OVER, true, now, old, 15))
        assertFalse(UsageRules.shouldNotify(UsageRules.STAGE_OVER, UsageRules.STAGE_OVER, false, now, old, 15))
        assertFalse(UsageRules.shouldNotify(UsageRules.STAGE_OVER, UsageRules.STAGE_OVER, true, now, old, 0))
    }
}

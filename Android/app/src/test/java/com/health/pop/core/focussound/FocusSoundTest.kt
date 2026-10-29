package com.health.pop.core.focussound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundTest {

    @Test
    fun volumeIsThirtyPercent() {
        assertEquals(0.30f, FocusMusic.VOLUME, 0.0001f)
        assertTrue(FocusMusic.DUCKED_VOLUME < FocusMusic.VOLUME)
    }

    @Test
    fun urlJoinsBaseAndPathWithSingleSlash() {
        assertEquals("https://api.example.com/media/focus-music.mp3", FocusMusic.url("https://api.example.com/"))
        assertEquals("https://api.example.com/media/focus-music.mp3", FocusMusic.url("https://api.example.com"))
    }
}

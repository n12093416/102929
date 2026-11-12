package com.example

import com.example.audio.AudioAnalyzer
import com.example.audio.AudioTimeSlice
import com.example.audio.Difficulty
import com.example.model.BasicObstacle
import com.example.model.CharacterForm
import com.example.model.ClearingMotion
import com.example.model.CompositeObstacle
import com.example.model.VibriStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testVibriEvolutionProgress() {
        var status = VibriStatus(form = CharacterForm.SLUG)

        // 6 clears in slug evolves to frog
        for (i in 1..6) {
            status = status.onClear(CompositeObstacle.BLOCK_ONLY, i * 500L)
        }
        assertEquals(CharacterForm.FROG, status.form)

        // 8 clears in frog evolves to rabbit
        for (i in 1..8) {
            status = status.onClear(CompositeObstacle.LOOP_ONLY, (6 + i) * 500L)
        }
        assertEquals(CharacterForm.RABBIT, status.form)

        // 16 clears in rabbit evolves to queen
        for (i in 1..16) {
            status = status.onClear(CompositeObstacle.WAVE_ONLY, (14 + i) * 500L)
        }
        assertEquals(CharacterForm.QUEEN, status.form)
    }

    @Test
    fun testClearingMotions() {
        var status = VibriStatus(form = CharacterForm.RABBIT)

        // Vault jump on block
        status = status.onClear(CompositeObstacle.BLOCK_ONLY, 1000L)
        assertEquals(ClearingMotion.VAULT_JUMP, status.currentMotion)

        // Dive loop on loop
        status = status.onClear(CompositeObstacle.LOOP_ONLY, 2000L)
        assertEquals(ClearingMotion.DIVE_LOOP, status.currentMotion)

        // Wave surf on wave
        status = status.onClear(CompositeObstacle.WAVE_ONLY, 3000L)
        assertEquals(ClearingMotion.WAVE_SURF, status.currentMotion)

        // Pit hurdle on pit
        status = status.onClear(CompositeObstacle.PIT_ONLY, 4000L)
        assertEquals(ClearingMotion.PIT_HURDLE, status.currentMotion)

        // Corkscrew on composite
        status = status.onClear(CompositeObstacle.BLOCK_LOOP, 5000L)
        assertEquals(ClearingMotion.COMPOSITE_CORKSCREW, status.currentMotion)
    }

    @Test
    fun testVibriDamageDegradation() {
        var status = VibriStatus(form = CharacterForm.QUEEN)

        // Queen -> Rabbit
        var (nextStatus, isDead) = status.onHit(1000L)
        assertEquals(CharacterForm.RABBIT, nextStatus.form)
        assertEquals(ClearingMotion.STUMBLE_HIT, nextStatus.currentMotion)
        assertFalse(isDead)

        // Rabbit -> Frog
        val hit2 = nextStatus.onHit(2000L)
        nextStatus = hit2.first
        assertEquals(CharacterForm.FROG, nextStatus.form)
        assertFalse(hit2.second)

        // Frog -> Slug
        val hit3 = nextStatus.onHit(3000L)
        nextStatus = hit3.first
        assertEquals(CharacterForm.SLUG, nextStatus.form)
        assertFalse(hit3.second)

        // Slug + Hit -> Game Over
        val hit4 = nextStatus.onHit(4000L)
        assertTrue(hit4.second)
    }

    @Test
    fun testCompositeObstacleBasics() {
        val blockLoop = CompositeObstacle.BLOCK_LOOP
        assertTrue(blockLoop.isComposite)
        assertTrue(blockLoop.basics.contains(BasicObstacle.BLOCK))
        assertTrue(blockLoop.basics.contains(BasicObstacle.LOOP))
        assertFalse(blockLoop.basics.contains(BasicObstacle.PIT))

        val singleBlock = CompositeObstacle.BLOCK_ONLY
        assertFalse(singleBlock.isComposite)
        assertEquals(setOf(BasicObstacle.BLOCK), singleBlock.basics)
    }

    @Test
    fun testAudioWaveformAnalysis() {
        val slices = mutableListOf<AudioTimeSlice>()
        for (t in 0..10000 step 40) {
            val isBassPeak = (t % 1000 == 0)
            val isHighPeak = (t % 1000 == 500)
            slices.add(
                AudioTimeSlice(
                    timeMs = t.toLong(),
                    rms = if (isBassPeak || isHighPeak) 0.8f else 0.2f,
                    bass = if (isBassPeak) 0.9f else 0.1f,
                    mid = 0.2f,
                    high = if (isHighPeak) 0.85f else 0.1f
                )
            )
        }

        val result = AudioAnalyzer.analyzeWaveform(slices, Difficulty.NORMAL)
        assertTrue(result.obstacles.isNotEmpty())
        assertTrue(result.averageBpm > 0)
    }
}

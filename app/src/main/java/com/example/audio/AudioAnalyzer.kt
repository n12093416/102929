package com.example.audio

import com.example.model.BasicObstacle
import com.example.model.CompositeObstacle
import com.example.model.Obstacle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Difficulty {
    EASY,
    NORMAL,
    VIB_MASTER
}

data class AudioTimeSlice(
    val timeMs: Long,
    val rms: Float,
    val bass: Float,
    val mid: Float,
    val high: Float
)

data class TrackAnalysisResult(
    val obstacles: List<Obstacle>,
    val timeSlices: List<AudioTimeSlice>,
    val averageBpm: Int,
    val totalDurationMs: Long,
    val peakCount: Int,
    val compositeCount: Int
)

object AudioAnalyzer {

    /**
     * Analyzes audio PCM samples or synthesized wave slices to produce
     * the procedural Vib-Ribbon obstacle timeline based on waveform features:
     * - Strong drum bass -> BLOCK (블록)
     * - High pitch transient -> LOOP (고리)
     * - Mid wave rhythm -> WAVE (물결)
     * - Sudden drop -> PIT (구덩이)
     * - Dual peaks -> Composite obstacles (합성 장애물)
     */
    fun analyzeWaveform(
        timeSlices: List<AudioTimeSlice>,
        difficulty: Difficulty = Difficulty.NORMAL
    ): TrackAnalysisResult {
        if (timeSlices.isEmpty()) {
            return TrackAnalysisResult(emptyList(), emptyList(), 120, 0, 0, 0)
        }

        val totalDuration = timeSlices.last().timeMs
        val minIntervalMs = when (difficulty) {
            Difficulty.EASY -> 650L
            Difficulty.NORMAL -> 450L
            Difficulty.VIB_MASTER -> 300L
        }

        // Calculate baseline averages
        val avgBass = timeSlices.map { it.bass }.average().toFloat().coerceAtLeast(0.05f)
        val avgHigh = timeSlices.map { it.high }.average().toFloat().coerceAtLeast(0.05f)
        val avgMid = timeSlices.map { it.mid }.average().toFloat().coerceAtLeast(0.05f)
        val avgRms = timeSlices.map { it.rms }.average().toFloat().coerceAtLeast(0.05f)

        val obstacles = mutableListOf<Obstacle>()
        var lastObstacleTime = 1500L // Give player 1.5s lead-in time
        var idCounter = 1L

        for (i in 2 until timeSlices.size - 2) {
            val curr = timeSlices[i]
            val prev = timeSlices[i - 1]
            val prev2 = timeSlices[i - 2]
            val next = timeSlices[i + 1]

            if (curr.timeMs < lastObstacleTime + minIntervalMs) continue
            if (curr.timeMs > totalDuration - 2000L) break // Grace period before song ends

            // Peak detection (local maximums in frequency bands)
            val isBassPeak = curr.bass > avgBass * 1.35f && curr.bass > prev.bass && curr.bass >= next.bass
            val isHighPeak = curr.high > avgHigh * 1.45f && curr.high > prev.high && curr.high >= next.high
            val isMidActive = curr.mid > avgMid * 1.3f && (curr.mid - prev.mid) > 0.08f
            val isDrop = prev2.rms > avgRms * 1.2f && curr.rms < avgRms * 0.45f

            var matchedType: CompositeObstacle? = null

            // In Easy mode, do not spawn composite obstacles
            val allowComposite = difficulty != Difficulty.EASY

            if (allowComposite && isBassPeak && isHighPeak) {
                matchedType = CompositeObstacle.BLOCK_LOOP
            } else if (allowComposite && isBassPeak && isMidActive) {
                matchedType = CompositeObstacle.BLOCK_WAVE
            } else if (allowComposite && isBassPeak && isDrop) {
                matchedType = CompositeObstacle.BLOCK_PIT
            } else if (allowComposite && isHighPeak && isMidActive) {
                matchedType = CompositeObstacle.LOOP_WAVE
            } else if (allowComposite && isHighPeak && isDrop) {
                matchedType = CompositeObstacle.LOOP_PIT
            } else if (allowComposite && isMidActive && isDrop) {
                matchedType = CompositeObstacle.WAVE_PIT
            } else if (isBassPeak) {
                matchedType = CompositeObstacle.BLOCK_ONLY
            } else if (isHighPeak) {
                matchedType = CompositeObstacle.LOOP_ONLY
            } else if (isMidActive) {
                matchedType = CompositeObstacle.WAVE_ONLY
            } else if (isDrop) {
                matchedType = CompositeObstacle.PIT_ONLY
            }

            matchedType?.let { type ->
                obstacles.add(
                    Obstacle(
                        id = idCounter++,
                        timestampMs = curr.timeMs,
                        type = type
                    )
                )
                lastObstacleTime = curr.timeMs
            }
        }

        // If obstacles are too few (e.g. ambient track), add rhythmic beats
        if (obstacles.size < 10 && totalDuration > 10000L) {
            val step = when (difficulty) {
                Difficulty.EASY -> 1200L
                Difficulty.NORMAL -> 800L
                Difficulty.VIB_MASTER -> 500L
            }
            var t = 2000L
            val types = listOf(
                CompositeObstacle.BLOCK_ONLY,
                CompositeObstacle.LOOP_ONLY,
                CompositeObstacle.WAVE_ONLY,
                CompositeObstacle.PIT_ONLY
            )
            var idx = 0
            while (t < totalDuration - 2000L) {
                obstacles.add(
                    Obstacle(
                        id = idCounter++,
                        timestampMs = t,
                        type = types[idx % types.size]
                    )
                )
                idx++
                t += step
            }
        }

        val composites = obstacles.count { it.type.isComposite }
        val estBpm = if (obstacles.size > 2) {
            val avgDiff = (obstacles.last().timestampMs - obstacles.first().timestampMs) / obstacles.size.toFloat()
            (60000f / avgDiff).toInt().coerceIn(80, 180)
        } else {
            120
        }

        return TrackAnalysisResult(
            obstacles = obstacles,
            timeSlices = timeSlices,
            averageBpm = estBpm,
            totalDurationMs = totalDuration,
            peakCount = obstacles.size,
            compositeCount = composites
        )
    }

    /**
     * Extracts time slices from raw PCM 16-bit 44.1kHz audio samples.
     */
    fun processPcmSamples(
        pcmShorts: ShortArray,
        sampleRate: Int = 44100,
        windowSizeMs: Int = 40
    ): List<AudioTimeSlice> {
        val samplesPerWindow = (sampleRate * (windowSizeMs / 1000.0)).toInt().coerceAtLeast(128)
        val slices = mutableListOf<AudioTimeSlice>()

        var index = 0
        var currentTimeMs = 0L

        while (index + samplesPerWindow < pcmShorts.size) {
            var sumSquare = 0.0
            var lowBandEnergy = 0.0
            var midBandEnergy = 0.0
            var highBandEnergy = 0.0

            for (i in 0 until samplesPerWindow) {
                val sample = pcmShorts[index + i] / 32768.0f
                sumSquare += sample * sample

                // Approximate simple spectral band filter via Goertzel or phase difference
                val phase = (i.toDouble() / samplesPerWindow) * 2.0 * Math.PI
                lowBandEnergy += abs(sample * sin(phase * 2.0))
                midBandEnergy += abs(sample * sin(phase * 8.0))
                highBandEnergy += abs(sample * sin(phase * 24.0))
            }

            val rms = sqrt(sumSquare / samplesPerWindow).toFloat().coerceIn(0f, 1f)
            val bass = (lowBandEnergy / samplesPerWindow).toFloat() * 2.5f
            val mid = (midBandEnergy / samplesPerWindow).toFloat() * 2.5f
            val high = (highBandEnergy / samplesPerWindow).toFloat() * 3.0f

            slices.add(
                AudioTimeSlice(
                    timeMs = currentTimeMs,
                    rms = rms,
                    bass = bass.coerceIn(0f, 1f),
                    mid = mid.coerceIn(0f, 1f),
                    high = high.coerceIn(0f, 1f)
                )
            )

            index += samplesPerWindow
            currentTimeMs += windowSizeMs
        }

        return slices
    }
}

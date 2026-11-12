package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.audio.AudioTimeSlice
import com.example.model.CharacterForm
import com.example.model.ClearingMotion
import com.example.model.CompositeObstacle
import com.example.model.Obstacle
import com.example.model.VibriStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VibRibbonCanvas(
    currentPlaybackMs: Long,
    vibriStatus: VibriStatus,
    obstacles: List<Obstacle>,
    currentSlice: AudioTimeSlice,
    bpm: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ribbon_motion")

    // Walk cadence linked to tempo
    val walkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((60000 / bpm.coerceAtLeast(60)).coerceIn(240, 600), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "walk_cycle"
    )

    // Ambient 3D rotation for obstacles
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "obstacle_spin"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF010103))
    ) {
        val width = size.width
        val height = size.height

        val baseRibbonY = height * 0.58f
        val vibriX = width * 0.28f

        // 1. CRT Vector Phosphor Horizon Grid with bass bounce
        val bassPulse = currentSlice.bass * 14f
        drawVectorPerspectiveGrid(width, height, baseRibbonY, bassPulse)

        // 2. Target Hitzone Reticle
        drawTargetReticle(vibriX, baseRibbonY)

        // 3. Audio-Reactive Oscilloscope Vector Ribbon
        drawOscilloscopeRibbon(
            width = width,
            baseY = baseRibbonY,
            currentPlaybackMs = currentPlaybackMs,
            vibriX = vibriX,
            obstacles = obstacles,
            currentSlice = currentSlice,
            walkPhase = walkPhase,
            rotateAngle = rotateAngle
        )

        // 4. Vibri Acrobatics Character
        val motionProgress = if (vibriStatus.currentMotion != ClearingMotion.NONE) {
            val elapsed = currentPlaybackMs - vibriStatus.motionStartTimeMs
            (elapsed.toFloat() / vibriStatus.motionDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        drawAcrobaticVibri(
            vibriX = vibriX,
            baseY = baseRibbonY,
            vibriStatus = vibriStatus,
            motionProgress = motionProgress,
            walkPhase = walkPhase,
            currentSlice = currentSlice
        )
    }
}

/**
 * High-end vector horizon grid with authentic arcade phosphor lines.
 */
private fun DrawScope.drawVectorPerspectiveGrid(
    width: Float,
    height: Float,
    horizonY: Float,
    bassPulse: Float
) {
    val vanishingX = width * 0.5f
    val gridAlpha = 0.12f
    val lineColor = Color(0x2800FFCC)

    // Perspective floor rays
    for (i in -8..8) {
        val bottomX = vanishingX + (i * (width / 8f))
        drawLine(
            color = lineColor,
            start = Offset(vanishingX, horizonY - 10f),
            end = Offset(bottomX, height),
            strokeWidth = 1.2f
        )
    }

    // Transverse scanline rows
    for (step in 1..6) {
        val fraction = step / 6f
        val y = horizonY + (fraction * fraction * (height - horizonY)) + (bassPulse * fraction)
        drawLine(
            color = Color(0xFF00FFCC).copy(alpha = gridAlpha * fraction),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
    }
}

/**
 * Target timing reticle where obstacles align with Vibri.
 */
private fun DrawScope.drawTargetReticle(vibriX: Float, baseY: Float) {
    // Glowing laser alignment beam
    drawLine(
        color = Color(0x3300FFCC),
        start = Offset(vibriX, baseY - 90f),
        end = Offset(vibriX, baseY + 80f),
        strokeWidth = 4f
    )
    drawLine(
        color = Color(0x99FFFFFF),
        start = Offset(vibriX, baseY - 80f),
        end = Offset(vibriX, baseY + 70f),
        strokeWidth = 1.5f
    )

    // Floating target diamond reticles
    val diamondPath = Path().apply {
        moveTo(vibriX, baseY - 65f)
        lineTo(vibriX + 9f, baseY - 55f)
        lineTo(vibriX, baseY - 45f)
        lineTo(vibriX - 9f, baseY - 55f)
        close()
    }
    drawPath(diamondPath, color = Color(0xFF00FFCC), style = Stroke(width = 2f))
    drawCircle(color = Color.White, radius = 3f, center = Offset(vibriX, baseY - 55f))
}

/**
 * Multi-layer vector ribbon that ripples with frequency spectrum.
 */
private fun DrawScope.drawOscilloscopeRibbon(
    width: Float,
    baseY: Float,
    currentPlaybackMs: Long,
    vibriX: Float,
    obstacles: List<Obstacle>,
    currentSlice: AudioTimeSlice,
    walkPhase: Float,
    rotateAngle: Float
) {
    val travelTimeMs = 1800f
    val speedPxPerMs = (width - vibriX) / travelTimeMs

    // Audio-reactive amplitude impulse
    val audioAmp = (currentSlice.rms * 22f + currentSlice.bass * 20f).coerceIn(3f, 36f)

    val ribbonPath = Path()
    val steps = 90
    val stepPx = width / steps

    for (i in 0..steps) {
        val px = i * stepPx
        // Harmonic waveform modulation
        val wave1 = sin((px * 0.025f) - (walkPhase * 2.2f)) * audioAmp
        val wave2 = cos((px * 0.06f) - (walkPhase * 4f)) * (audioAmp * 0.35f)
        val py = baseY + wave1 + wave2

        if (i == 0) {
            ribbonPath.moveTo(px, py)
        } else {
            ribbonPath.lineTo(px, py)
        }
    }

    // Outer cyan phosphor halo (glowing CRT aura)
    drawPath(
        path = ribbonPath,
        color = Color(0x3500FFCC),
        style = Stroke(width = 8f, cap = StrokeCap.Round)
    )

    // Brilliant white razor-sharp core
    drawPath(
        path = ribbonPath,
        color = Color.White,
        style = Stroke(width = 3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Render upcoming obstacles along the ribbon
    val minTime = currentPlaybackMs - 350L
    val maxTime = currentPlaybackMs + 2200L

    val visible = obstacles.filter { it.timestampMs in minTime..maxTime }

    for (obs in visible) {
        val diffMs = obs.timestampMs - currentPlaybackMs
        val obsX = vibriX + (diffMs * speedPxPerMs)
        val waveAtObs = sin((obsX * 0.025f) - (walkPhase * 2.2f)) * audioAmp
        val obsY = baseY + waveAtObs

        drawWireframeObstacle(obsX, obsY, obs.type, obs.cleared, rotateAngle)
    }
}

/**
 * Draws 3D rotating isometric wireframe obstacles with CRT phosphor glow.
 */
private fun DrawScope.drawWireframeObstacle(
    cx: Float,
    cy: Float,
    type: CompositeObstacle,
    cleared: Boolean,
    rotateAngle: Float
) {
    val alpha = if (cleared) 0.25f else 1.0f
    val mainColor = Color.White.copy(alpha = alpha)
    val neonGlow = Color(0xFF00FFCC).copy(alpha = alpha * 0.6f)
    val stroke = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val glowStroke = Stroke(width = 7.5f, cap = StrokeCap.Round)

    when (type) {
        CompositeObstacle.BLOCK_ONLY -> drawBlock3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, rotateAngle)
        CompositeObstacle.LOOP_ONLY -> drawLoop3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, rotateAngle)
        CompositeObstacle.WAVE_ONLY -> drawWave3D(cx, cy, mainColor, neonGlow, stroke, glowStroke)
        CompositeObstacle.PIT_ONLY -> drawPit3D(cx, cy, mainColor, neonGlow, stroke, glowStroke)

        // 6 Composite Combinations
        CompositeObstacle.BLOCK_LOOP -> {
            drawBlock3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, rotateAngle, size = 40f)
            drawLoop3D(cx, cy - 24f, mainColor, neonGlow, stroke, glowStroke, rotateAngle, radius = 25f)
        }
        CompositeObstacle.BLOCK_WAVE -> {
            drawWave3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, width = 64f)
            drawBlock3D(cx, cy - 36f, mainColor, neonGlow, stroke, glowStroke, rotateAngle, size = 32f)
        }
        CompositeObstacle.BLOCK_PIT -> {
            drawPit3D(cx, cy, mainColor, neonGlow, stroke, glowStroke)
            drawBlock3D(cx, cy - 44f, mainColor, neonGlow, stroke, glowStroke, rotateAngle, size = 30f)
        }
        CompositeObstacle.LOOP_WAVE -> {
            drawWave3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, width = 64f)
            drawLoop3D(cx, cy - 36f, mainColor, neonGlow, stroke, glowStroke, rotateAngle, radius = 26f)
        }
        CompositeObstacle.LOOP_PIT -> {
            drawPit3D(cx, cy, mainColor, neonGlow, stroke, glowStroke)
            drawLoop3D(cx, cy - 40f, mainColor, neonGlow, stroke, glowStroke, rotateAngle, radius = 24f)
        }
        CompositeObstacle.WAVE_PIT -> {
            drawPit3D(cx, cy, mainColor, neonGlow, stroke, glowStroke, depth = 65f)
            drawWave3D(cx - 30f, cy, mainColor, neonGlow, stroke, glowStroke, width = 50f)
        }
    }
}

// 1. BLOCK: 3D Rotating Isometric Wireframe Cube
private fun DrawScope.drawBlock3D(
    cx: Float,
    cy: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    rotateAngle: Float,
    size: Float = 44f
) {
    val half = size / 2f
    val topY = cy - size - 2f
    val depth = size * 0.45f

    val path = Path().apply {
        // Front square
        moveTo(cx - half, topY)
        lineTo(cx + half, topY)
        lineTo(cx + half, topY + size)
        lineTo(cx - half, topY + size)
        close()

        // 3D Top face
        moveTo(cx - half, topY)
        lineTo(cx - half + depth, topY - depth)
        lineTo(cx + half + depth, topY - depth)
        lineTo(cx + half, topY)

        // 3D Right side face
        moveTo(cx + half, topY + size)
        lineTo(cx + half + depth, topY + size - depth)
        lineTo(cx + half + depth, topY - depth)

        // Internal crosshair facet
        moveTo(cx - half, topY)
        lineTo(cx + half, topY + size)
    }

    drawPath(path, color = glow, style = glowStroke)
    drawPath(path, color = color, style = stroke)

    // Rotating internal core diamond
    val coreSize = 10f
    withTransform({
        rotate(rotateAngle, pivot = Offset(cx, topY + half))
    }) {
        val diamond = Path().apply {
            moveTo(cx, topY + half - coreSize)
            lineTo(cx + coreSize, topY + half)
            lineTo(cx, topY + half + coreSize)
            lineTo(cx - coreSize, topY + half)
            close()
        }
        drawPath(diamond, color = Color(0xFF00FFCC).copy(alpha = color.alpha), style = Stroke(width = 2f))
    }
}

// 2. LOOP: Multi-ring 3D Wireframe Loop
private fun DrawScope.drawLoop3D(
    cx: Float,
    cy: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    rotateAngle: Float,
    radius: Float = 34f
) {
    val loopCenterY = cy - radius - 10f
    val center = Offset(cx, loopCenterY)

    // Outer & inner rings
    drawCircle(color = glow, radius = radius, center = center, style = glowStroke)
    drawCircle(color = color, radius = radius, center = center, style = stroke)
    drawCircle(color = color, radius = radius * 0.6f, center = center, style = stroke)

    // Rotating cross spokes
    withTransform({
        rotate(rotateAngle * 1.5f, pivot = center)
    }) {
        drawLine(color = color, start = Offset(cx - radius, loopCenterY), end = Offset(cx + radius, loopCenterY), strokeWidth = 1.5f)
        drawLine(color = color, start = Offset(cx, loopCenterY - radius), end = Offset(cx, loopCenterY + radius), strokeWidth = 1.5f)
    }

    // Connectors to ribbon
    drawLine(color = color, start = Offset(cx - radius * 0.35f, loopCenterY + radius), end = Offset(cx - radius * 0.35f, cy), strokeWidth = stroke.width)
    drawLine(color = color, start = Offset(cx + radius * 0.35f, loopCenterY + radius), end = Offset(cx + radius * 0.35f, cy), strokeWidth = stroke.width)
}

// 3. WAVE: Multi-frequency Harmonic Waveform Peak
private fun DrawScope.drawWave3D(
    cx: Float,
    cy: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    width: Float = 72f
) {
    val halfW = width / 2f
    val amplitude = 48f

    val path = Path().apply {
        moveTo(cx - halfW, cy)
        cubicTo(
            cx - halfW * 0.5f, cy - amplitude * 1.35f,
            cx, cy + amplitude * 0.85f,
            cx + halfW * 0.5f, cy - amplitude * 1.35f
        )
        lineTo(cx + halfW, cy)
    }

    drawPath(path, color = glow, style = glowStroke)
    drawPath(path, color = color, style = stroke)

    // Vertical resonant rib lines
    for (i in -3..3) {
        val rx = cx + (i * (halfW / 4f))
        val ry = cy - (amplitude * cos(i * 0.5f)).coerceAtLeast(0f)
        drawLine(
            color = Color(0xFF00FFCC).copy(alpha = color.alpha * 0.7f),
            start = Offset(rx, cy),
            end = Offset(rx, ry),
            strokeWidth = 1.8f
        )
    }
}

// 4. PIT: Deep 3D Wireframe Chasm
private fun DrawScope.drawPit3D(
    cx: Float,
    cy: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    depth: Float = 68f,
    width: Float = 58f
) {
    val halfW = width / 2f
    val path = Path().apply {
        moveTo(cx - halfW, cy)
        lineTo(cx, cy + depth)
        lineTo(cx + halfW, cy)
    }

    drawPath(path, color = glow, style = glowStroke)
    drawPath(path, color = color, style = stroke)

    // Perspective depth lines down into abyss
    for (step in listOf(0.35f, 0.7f)) {
        val y = cy + (depth * step)
        val w = halfW * (1f - step)
        drawLine(
            color = Color(0xFF00FFCC).copy(alpha = color.alpha * 0.8f),
            start = Offset(cx - w, y),
            end = Offset(cx + w, y),
            strokeWidth = 1.6f
        )
    }
    // Vertical center spine into the pit
    drawLine(color = color.copy(alpha = 0.5f), start = Offset(cx, cy), end = Offset(cx, cy + depth), strokeWidth = 1.5f)
}

/**
 * Draws Vibri with dedicated acrobatics motions for each obstacle:
 * - VAULT_JUMP: 360° forward somersault over Block
 * - DIVE_LOOP: Torpedo horizontal dive spin through Loop
 * - WAVE_SURF: Sinuous surfboard carve riding Wave
 * - PIT_HURDLE: Grand athletic hurdle leap across Pit
 * - COMPOSITE_CORKSCREW: High vertical twister spin with vector trails
 * - STUMBLE_HIT: Flailing stumble with chromatic glitch jitter
 */
private fun DrawScope.drawAcrobaticVibri(
    vibriX: Float,
    baseY: Float,
    vibriStatus: VibriStatus,
    motionProgress: Float,
    walkPhase: Float,
    currentSlice: AudioTimeSlice
) {
    val stroke = Stroke(width = 3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val glowStroke = Stroke(width = 7.5f, cap = StrokeCap.Round)

    val isGlitching = vibriStatus.isGlitching
    val color = if (isGlitching) Color(1f, 0.35f, 0.35f) else Color.White
    val glowColor = if (isGlitching) Color(0x66FF3333) else Color(0x4400FFCC)

    val motion = vibriStatus.currentMotion

    if (motion != ClearingMotion.NONE && motionProgress in 0.01f..0.99f) {
        when (motion) {
            ClearingMotion.VAULT_JUMP -> {
                drawVaultJumpMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.DIVE_LOOP -> {
                drawDiveLoopMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.WAVE_SURF -> {
                drawWaveSurfMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.PIT_HURDLE -> {
                drawPitHurdleMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.COMPOSITE_CORKSCREW -> {
                drawCompositeCorkscrewMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.STUMBLE_HIT -> {
                drawStumbleHitMotion(vibriX, baseY, motionProgress, color, glowColor, stroke, glowStroke, vibriStatus.form)
                return
            }
            ClearingMotion.NONE -> {}
        }
    }

    // Default regular running cycle
    when (vibriStatus.form) {
        CharacterForm.RABBIT -> drawRunningRabbit(vibriX, baseY, walkPhase, color, glowColor, stroke, glowStroke)
        CharacterForm.QUEEN -> drawSuperQueen(vibriX, baseY, walkPhase, color, glowColor, stroke, glowStroke)
        CharacterForm.FROG -> drawHoppingFrog(vibriX, baseY, walkPhase, color, glowColor, stroke, glowStroke)
        CharacterForm.SLUG -> drawCreepingSlug(vibriX, baseY, walkPhase, color, glowColor, stroke, glowStroke)
    }
}

// ==================== DEDICATED ACROBATIC MOTIONS ====================

// 1. VAULT JUMP: 360° Forward Somersault Vault over Block
private fun DrawScope.drawVaultJumpMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // Parabolic trajectory (peak height 75px)
    val jumpY = -sin(p * PI.toFloat()) * 75f
    val jumpX = cx + (sin(p * PI.toFloat()) * 18f)
    val flipAngle = p * 360f

    val center = Offset(jumpX, baseY + jumpY - 32f)

    withTransform({
        rotate(flipAngle, pivot = center)
    }) {
        // Tucked acrobat pose: Head, curved spine, tucked knees, trailing ears
        val headY = center.y - 18f
        drawCircle(color = glow, radius = 12f, center = Offset(center.x, headY), style = glowStroke)
        drawCircle(color = color, radius = 12f, center = Offset(center.x, headY), style = stroke)

        // Trailing aerodynamic ears
        val earPath = Path().apply {
            moveTo(center.x - 3f, headY - 10f)
            lineTo(center.x - 24f, headY - 26f)
            lineTo(center.x - 6f, headY - 8f)

            moveTo(center.x + 3f, headY - 10f)
            lineTo(center.x - 20f, headY - 32f)
            lineTo(center.x + 6f, headY - 8f)
        }
        drawPath(earPath, color = color, style = stroke)

        // Tucked torso and knees
        val bodyPath = Path().apply {
            moveTo(center.x, headY + 12f)
            lineTo(center.x + 6f, center.y + 6f)
            lineTo(center.x - 10f, center.y + 14f) // Tucked knees
            lineTo(center.x, headY + 12f)
        }
        drawPath(bodyPath, color = color, style = stroke)
    }

    // Jump burst trail particles
    if (p in 0.1f..0.8f) {
        val trailAlpha = 1f - p
        drawLine(
            color = Color(0xFF00FFCC).copy(alpha = trailAlpha),
            start = Offset(cx - 20f, baseY - 10f),
            end = Offset(center.x, center.y + 10f),
            strokeWidth = 2f
        )
    }
}

// 2. DIVE LOOP: Torpedo Horizontal Dive Roll through Center of Ring
private fun DrawScope.drawDiveLoopMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // Torpedo glide through loop center (mid-altitude ~42px)
    val diveY = -sin(p * PI.toFloat()) * 48f
    val diveX = cx + (p * 30f)
    val center = Offset(diveX, baseY + diveY - 20f)

    // Horizontal streamline orientation
    withTransform({
        rotate(15f * sin(p * PI.toFloat()), pivot = center)
    }) {
        // Streamlined horizontal pose: Arms thrust forward, ears pinned back
        val headX = center.x + 22f
        val headY = center.y

        drawCircle(color = glow, radius = 10f, center = Offset(headX, headY), style = glowStroke)
        drawCircle(color = color, radius = 10f, center = Offset(headX, headY), style = stroke)

        // Hands outstretched like a diver
        drawLine(color = color, start = Offset(headX, headY), end = Offset(headX + 22f, headY), strokeWidth = stroke.width)

        // Ears streamlined backwards
        val earPath = Path().apply {
            moveTo(headX - 6f, headY - 4f)
            lineTo(headX - 32f, headY - 10f)
            lineTo(headX - 6f, headY + 2f)
        }
        drawPath(earPath, color = color, style = stroke)

        // Horizontal streamlined body & pointed feet
        drawLine(color = color, start = Offset(headX - 10f, headY), end = Offset(center.x - 20f, headY), strokeWidth = stroke.width)
        drawLine(color = color, start = Offset(center.x - 20f, headY), end = Offset(center.x - 38f, headY + 2f), strokeWidth = stroke.width)
    }
}

// 3. WAVE SURF: Sinuous Body-Wave Glide Riding Wave Crest
private fun DrawScope.drawWaveSurfMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // Riding up and down the wave crest
    val waveY = -sin(p * PI.toFloat()) * 52f
    val center = Offset(cx, baseY + waveY - 26f)

    // Surfing lean angle
    val leanAngle = cos(p * PI.toFloat()) * -25f

    withTransform({
        rotate(leanAngle, pivot = center)
    }) {
        val headY = center.y - 28f
        drawCircle(color = glow, radius = 11f, center = Offset(center.x, headY), style = glowStroke)
        drawCircle(color = color, radius = 11f, center = Offset(center.x, headY), style = stroke)

        // Floppy ears caught in the sea-breeze
        val earPath = Path().apply {
            moveTo(center.x - 4f, headY - 10f)
            lineTo(center.x - 18f, headY - 32f)
            lineTo(center.x + 2f, headY - 10f)
        }
        drawPath(earPath, color = color, style = stroke)

        // Torso
        drawLine(color = color, start = Offset(center.x, headY + 11f), end = Offset(center.x - 4f, center.y), strokeWidth = stroke.width)

        // Surfer arms spread for balance
        drawLine(color = color, start = Offset(center.x, headY + 16f), end = Offset(center.x + 24f, headY + 10f), strokeWidth = stroke.width)
        drawLine(color = color, start = Offset(center.x, headY + 16f), end = Offset(center.x - 24f, headY + 22f), strokeWidth = stroke.width)

        // Surfing feet stance (one forward, one back)
        drawLine(color = color, start = Offset(center.x - 4f, center.y), end = Offset(center.x + 16f, center.y + 24f), strokeWidth = stroke.width)
        drawLine(color = color, start = Offset(center.x - 4f, center.y), end = Offset(center.x - 18f, center.y + 24f), strokeWidth = stroke.width)
    }

    // Glowing surf wake board line
    drawLine(
        color = Color(0xFF00FFCC),
        start = Offset(center.x - 24f, center.y + 26f),
        end = Offset(center.x + 24f, center.y + 26f),
        strokeWidth = 3f
    )
}

// 4. PIT HURDLE: Grand Athletic Split Hurdle Leap Across Chasm
private fun DrawScope.drawPitHurdleMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // Grand soaring leap spanning the pit
    val leapY = -sin(p * PI.toFloat()) * 60f
    val leapX = cx + (sin(p * PI.toFloat()) * 22f)
    val center = Offset(leapX, baseY + leapY - 28f)

    val headY = center.y - 26f
    drawCircle(color = glow, radius = 11f, center = Offset(center.x, headY), style = glowStroke)
    drawCircle(color = color, radius = 11f, center = Offset(center.x, headY), style = stroke)

    // Confident forward-swept ears
    val earPath = Path().apply {
        moveTo(center.x - 3f, headY - 11f)
        lineTo(center.x - 16f, headY - 35f)
        lineTo(center.x + 2f, headY - 11f)

        moveTo(center.x + 3f, headY - 11f)
        lineTo(center.x + 16f, headY - 35f)
        lineTo(center.x + 8f, headY - 11f)
    }
    drawPath(earPath, color = color, style = stroke)

    // Torso
    drawLine(color = color, start = Offset(center.x, headY + 11f), end = Offset(center.x, center.y), strokeWidth = stroke.width)

    // Athletic split legs: front leg kicked straight forward, back leg kicked back
    val splitLegLeading = center.x + 28f
    val splitLegTrailing = center.x - 28f

    // Leading leg
    drawLine(color = color, start = Offset(center.x, center.y), end = Offset(splitLegLeading, center.y + 6f), strokeWidth = stroke.width)
    // Trailing leg
    drawLine(color = color, start = Offset(center.x, center.y), end = Offset(splitLegTrailing, center.y + 14f), strokeWidth = stroke.width)

    // Energetic hurdle arm pump
    drawLine(color = color, start = Offset(center.x, headY + 16f), end = Offset(center.x + 20f, headY + 6f), strokeWidth = stroke.width)
    drawLine(color = color, start = Offset(center.x, headY + 16f), end = Offset(center.x - 18f, headY + 24f), strokeWidth = stroke.width)
}

// 5. COMPOSITE CORKSCREW: High Altitude 720° Twist & Sparkle Spin
private fun DrawScope.drawCompositeCorkscrewMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // Spectacular high-altitude twister flip
    val jumpY = -sin(p * PI.toFloat()) * 90f
    val center = Offset(cx, baseY + jumpY - 35f)
    val twistAngle = p * 720f

    withTransform({
        rotate(twistAngle, pivot = center)
    }) {
        val headY = center.y - 20f
        drawCircle(color = Color(0xFFFFD166), radius = 12f, center = Offset(center.x, headY), style = glowStroke)
        drawCircle(color = Color.White, radius = 12f, center = Offset(center.x, headY), style = stroke)

        // Radiant starburst ears
        val earPath = Path().apply {
            moveTo(center.x, headY - 12f)
            lineTo(center.x, headY - 40f)
            moveTo(center.x, headY + 12f)
            lineTo(center.x, headY + 40f)
        }
        drawPath(earPath, color = Color(0xFF00FFCC), style = stroke)

        // Arms and legs spread in acrobatic star
        drawLine(color = Color.White, start = Offset(center.x - 25f, center.y), end = Offset(center.x + 25f, center.y), strokeWidth = stroke.width)
        drawLine(color = Color.White, start = Offset(center.x - 18f, center.y + 22f), end = Offset(center.x + 18f, center.y - 22f), strokeWidth = stroke.width)
    }

    // Expanding shockwave ring on takeoff and landing
    val ringRadius = (p * 55f)
    drawCircle(color = Color(0x66FFD166), radius = ringRadius, center = center, style = Stroke(width = 2f))
}

// 6. STUMBLE HIT: Backward Stumble & Vertex Glitch Jitter on Mistake
private fun DrawScope.drawStumbleHitMotion(
    cx: Float,
    baseY: Float,
    p: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke,
    form: CharacterForm
) {
    // High-frequency jitter
    val jitterX = sin(p * 40f) * 14f
    val jitterY = cos(p * 50f) * 10f
    val center = Offset(cx - (p * 20f) + jitterX, baseY - 30f + jitterY)

    val headY = center.y - 22f
    // Glitching red outline
    drawCircle(color = Color.Red, radius = 13f, center = Offset(center.x, headY), style = glowStroke)
    drawCircle(color = Color.White, radius = 11f, center = Offset(center.x, headY), style = stroke)

    // Flailing backwards ears
    val earPath = Path().apply {
        moveTo(center.x, headY - 10f)
        lineTo(center.x - 28f + jitterX, headY - 32f)
        moveTo(center.x, headY - 10f)
        lineTo(center.x - 20f - jitterX, headY - 36f)
    }
    drawPath(earPath, color = Color.Red, style = stroke)

    // Stumbling backwards flailing limbs
    drawLine(color = Color.White, start = Offset(center.x, headY + 12f), end = Offset(center.x - 15f, center.y + 10f), strokeWidth = stroke.width)
    drawLine(color = Color.White, start = Offset(center.x - 15f, center.y + 10f), end = Offset(center.x - 30f, center.y + 30f), strokeWidth = stroke.width)
}

// ==================== REGULAR WALKING CYCLES ====================

// Standard Vibri (Polygonal Rabbit)
private fun DrawScope.drawRunningRabbit(
    cx: Float,
    cy: Float,
    walkPhase: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke
) {
    val bounceY = abs(sin(walkPhase)) * 8f
    val headCenterY = cy - 60f - bounceY
    val torsoY = cy - 32f - bounceY

    // 1. Head with vector glow
    drawCircle(color = glow, radius = 12f, center = Offset(cx, headCenterY), style = glowStroke)
    drawCircle(color = color, radius = 12f, center = Offset(cx, headCenterY), style = stroke)

    // Cute geometric eye
    drawCircle(color = color, radius = 2.2f, center = Offset(cx + 4.5f, headCenterY - 2.5f))

    // 2. Animated Long Ears (Dynamic drag inertia)
    val earWaggle = sin(walkPhase) * 14f
    val earPath = Path().apply {
        moveTo(cx - 3f, headCenterY - 12f)
        lineTo(cx - 18f + earWaggle, headCenterY - 44f)
        lineTo(cx - 7f, headCenterY - 12f)

        moveTo(cx + 3f, headCenterY - 12f)
        lineTo(cx + 16f - earWaggle, headCenterY - 48f)
        lineTo(cx + 8f, headCenterY - 12f)
    }
    drawPath(earPath, color = glow, style = glowStroke)
    drawPath(earPath, color = color, style = stroke)

    // 3. Torso
    drawLine(color = color, start = Offset(cx, headCenterY + 12f), end = Offset(cx, torsoY), strokeWidth = stroke.width)

    // 4. Arms (In-tempo jogging swing)
    val armSwing = sin(walkPhase) * 16f
    drawLine(color = color, start = Offset(cx, headCenterY + 18f), end = Offset(cx + 16f + armSwing, torsoY - 2f), strokeWidth = stroke.width)
    drawLine(color = color, start = Offset(cx, headCenterY + 18f), end = Offset(cx - 14f - armSwing, torsoY - 2f), strokeWidth = stroke.width)

    // 5. Running Legs (Articulated knees and ankles)
    val legPhase = sin(walkPhase)
    val leftFootX = cx - 18f * legPhase
    val leftFootY = cy - (abs(sin(walkPhase)) * 6f).coerceAtLeast(0f)
    val rightFootX = cx + 18f * legPhase
    val rightFootY = cy - (abs(cos(walkPhase)) * 6f).coerceAtLeast(0f)

    drawLine(color = color, start = Offset(cx, torsoY), end = Offset(cx - 6f * legPhase, torsoY + 14f), strokeWidth = stroke.width)
    drawLine(color = color, start = Offset(cx - 6f * legPhase, torsoY + 14f), end = Offset(leftFootX, leftFootY), strokeWidth = stroke.width)

    drawLine(color = color, start = Offset(cx, torsoY), end = Offset(cx + 6f * legPhase, torsoY + 14f), strokeWidth = stroke.width)
    drawLine(color = color, start = Offset(cx + 6f * legPhase, torsoY + 14f), end = Offset(rightFootX, rightFootY), strokeWidth = stroke.width)
}

// Super Vibri (Fairy Queen)
private fun DrawScope.drawSuperQueen(
    cx: Float,
    cy: Float,
    walkPhase: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke
) {
    val floatY = sin(walkPhase) * 14f
    val headCenterY = cy - 68f + floatY
    val torsoY = cy - 36f + floatY

    // Tiara / Crown
    val crownPath = Path().apply {
        moveTo(cx - 10f, headCenterY - 14f)
        lineTo(cx - 14f, headCenterY - 30f)
        lineTo(cx - 5f, headCenterY - 22f)
        lineTo(cx, headCenterY - 34f)
        lineTo(cx + 5f, headCenterY - 22f)
        lineTo(cx + 14f, headCenterY - 30f)
        lineTo(cx + 10f, headCenterY - 14f)
        close()
    }
    drawPath(crownPath, color = Color(0xFFFFE066), style = stroke)

    // Fluttering fairy wings
    val wingFlap = cos(walkPhase * 3f) * 20f
    val wingPath = Path().apply {
        moveTo(cx - 5f, torsoY - 8f)
        lineTo(cx - 40f, torsoY - 38f + wingFlap)
        lineTo(cx - 30f, torsoY - 10f)
        close()

        moveTo(cx + 5f, torsoY - 8f)
        lineTo(cx + 40f, torsoY - 38f - wingFlap)
        lineTo(cx + 30f, torsoY - 10f)
        close()
    }
    drawPath(wingPath, color = Color(0xFF00FFCC), style = stroke)

    // Head & Torso
    drawCircle(color = glow, radius = 12f, center = Offset(cx, headCenterY), style = glowStroke)
    drawCircle(color = color, radius = 12f, center = Offset(cx, headCenterY), style = stroke)
    drawLine(color = color, start = Offset(cx, headCenterY + 12f), end = Offset(cx, torsoY), strokeWidth = stroke.width)

    // Elegant gown
    val gownPath = Path().apply {
        moveTo(cx - 15f, cy - 8f + floatY)
        lineTo(cx, torsoY)
        lineTo(cx + 15f, cy - 8f + floatY)
        close()
    }
    drawPath(gownPath, color = color, style = stroke)
}

// Frog Vibri
private fun DrawScope.drawHoppingFrog(
    cx: Float,
    cy: Float,
    walkPhase: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke
) {
    val hopY = abs(sin(walkPhase)) * 22f
    val bodyY = cy - 24f - hopY

    drawOval(color = glow, topLeft = Offset(cx - 22f, bodyY - 12f), size = Size(44f, 24f), style = glowStroke)
    drawOval(color = color, topLeft = Offset(cx - 22f, bodyY - 12f), size = Size(44f, 24f), style = stroke)

    drawCircle(color = color, radius = 6f, center = Offset(cx - 12f, bodyY - 16f), style = stroke)
    drawCircle(color = color, radius = 6f, center = Offset(cx + 12f, bodyY - 16f), style = stroke)

    val legSpread = 24f + (if (hopY > 10f) 8f else -8f)
    val legPath = Path().apply {
        moveTo(cx - 18f, bodyY)
        lineTo(cx - legSpread, cy - 4f)
        lineTo(cx - legSpread - 8f, cy)

        moveTo(cx + 18f, bodyY)
        lineTo(cx + legSpread, cy - 4f)
        lineTo(cx + legSpread + 8f, cy)
    }
    drawPath(legPath, color = color, style = stroke)
}

// Slug Vibri
private fun DrawScope.drawCreepingSlug(
    cx: Float,
    cy: Float,
    walkPhase: Float,
    color: Color,
    glow: Color,
    stroke: Stroke,
    glowStroke: Stroke
) {
    val squish = sin(walkPhase * 2f) * 6f

    for (i in 0..4) {
        val segX = cx - (i * 12f) + (squish * (i / 4f))
        val segY = cy - 9f + (abs(sin(walkPhase + i)) * 4f)
        drawCircle(color = glow, radius = 8f - (i * 0.8f), center = Offset(segX, segY), style = glowStroke)
        drawCircle(color = color, radius = 8f - (i * 0.8f), center = Offset(segX, segY), style = stroke)
    }

    val headX = cx + 6f
    val headY = cy - 12f
    drawLine(color = color, start = Offset(headX, headY), end = Offset(headX + 8f, headY - 10f), strokeWidth = 2f)
    drawCircle(color = color, radius = 2f, center = Offset(headX + 9f, headY - 11f))
}

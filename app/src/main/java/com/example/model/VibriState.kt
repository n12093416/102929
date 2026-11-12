package com.example.model

/**
 * Vibri's 4 evolution forms:
 * QUEEN (슈퍼 비브리 / 여왕 요정) <-> RABBIT (토끼) <-> FROG (개구리) <-> SLUG (굼벵이)
 */
enum class CharacterForm(val titleKorean: String, val scoreMultiplier: Float) {
    QUEEN("여왕 요정 (Super Vibri)", 2.0f),
    RABBIT("토끼 (Vibri)", 1.0f),
    FROG("개구리 (Frog)", 0.8f),
    SLUG("굼벵이 (Slug)", 0.5f)
}

/**
 * Dedicated obstacle-clearing acrobatics for Vibri:
 * - VAULT_JUMP: 360° forward tuck flip vaulting over Block (□)
 * - DIVE_LOOP: Torpedo dive roll straight through the center of Loop (◯)
 * - WAVE_SURF: Sinuous body-wave glide riding atop Wave (〜)
 * - PIT_HURDLE: Grand athletic hurdle leap spanning Pit (▽)
 * - COMPOSITE_CORKSCREW: High-altitude corkscrew spin with trailing spark lines for composite obstacles
 * - STUMBLE_HIT: Backward flail & vertex glitch distortion on mistake
 */
enum class ClearingMotion {
    NONE,
    VAULT_JUMP,
    DIVE_LOOP,
    WAVE_SURF,
    PIT_HURDLE,
    COMPOSITE_CORKSCREW,
    STUMBLE_HIT
}

data class VibriStatus(
    val form: CharacterForm = CharacterForm.RABBIT,
    val streakInForm: Int = 0,
    val isGlitching: Boolean = false,
    val currentMotion: ClearingMotion = ClearingMotion.NONE,
    val motionStartTimeMs: Long = 0L,
    val motionDurationMs: Long = 420L,
    val livesLeftInSlug: Int = 1
) {
    fun onHit(nowMs: Long): Pair<VibriStatus, Boolean> {
        val nextStatus = copy(
            currentMotion = ClearingMotion.STUMBLE_HIT,
            motionStartTimeMs = nowMs,
            motionDurationMs = 500L,
            isGlitching = true,
            streakInForm = 0
        )
        return when (form) {
            CharacterForm.QUEEN -> Pair(nextStatus.copy(form = CharacterForm.RABBIT), false)
            CharacterForm.RABBIT -> Pair(nextStatus.copy(form = CharacterForm.FROG), false)
            CharacterForm.FROG -> Pair(nextStatus.copy(form = CharacterForm.SLUG), false)
            CharacterForm.SLUG -> Pair(nextStatus, true) // Game Over!
        }
    }

    fun onClear(obstacleType: CompositeObstacle, nowMs: Long): VibriStatus {
        val newStreak = streakInForm + 1
        val motion = when {
            obstacleType.isComposite -> ClearingMotion.COMPOSITE_CORKSCREW
            obstacleType.basics.contains(BasicObstacle.BLOCK) -> ClearingMotion.VAULT_JUMP
            obstacleType.basics.contains(BasicObstacle.LOOP) -> ClearingMotion.DIVE_LOOP
            obstacleType.basics.contains(BasicObstacle.WAVE) -> ClearingMotion.WAVE_SURF
            obstacleType.basics.contains(BasicObstacle.PIT) -> ClearingMotion.PIT_HURDLE
            else -> ClearingMotion.VAULT_JUMP
        }

        val baseUpdated = copy(
            currentMotion = motion,
            motionStartTimeMs = nowMs,
            motionDurationMs = 420L,
            isGlitching = false
        )

        return when (form) {
            CharacterForm.SLUG -> {
                if (newStreak >= 6) {
                    baseUpdated.copy(form = CharacterForm.FROG, streakInForm = 0)
                } else {
                    baseUpdated.copy(streakInForm = newStreak)
                }
            }
            CharacterForm.FROG -> {
                if (newStreak >= 8) {
                    baseUpdated.copy(form = CharacterForm.RABBIT, streakInForm = 0)
                } else {
                    baseUpdated.copy(streakInForm = newStreak)
                }
            }
            CharacterForm.RABBIT -> {
                if (newStreak >= 16) {
                    baseUpdated.copy(form = CharacterForm.QUEEN, streakInForm = 0)
                } else {
                    baseUpdated.copy(streakInForm = newStreak)
                }
            }
            CharacterForm.QUEEN -> {
                baseUpdated.copy(streakInForm = newStreak)
            }
        }
    }
}

data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val durationMs: Long,
    val isCustom: Boolean = true,
    val customUriString: String? = null,
    val localFilePath: String? = null,
    val fileSizeFormatted: String = ""
)

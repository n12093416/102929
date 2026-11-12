package com.example.model

/**
 * The 4 fundamental obstacle types in Vib-Ribbon:
 * 1. BLOCK (블록): Spawns on strong drum beats / bass kicks. Button: Block (□)
 * 2. LOOP (고리): Spawns on high-pitch melody spikes / sharp transients. Button: Loop (◯)
 * 3. WAVE (물결): Spawns on mid-range sustained rhythms / waveform wiggles. Button: Wave (〜)
 * 4. PIT (구덩이): Spawns on sudden volume drops / bass dips / off-beats. Button: Pit (▽)
 */
enum class BasicObstacle(val symbol: String, val koreanName: String) {
    BLOCK("□", "블록"),
    LOOP("◯", "고리"),
    WAVE("〜", "물결"),
    PIT("▽", "구덩이")
}

/**
 * Composite Obstacles (합성 장애물):
 * When audio frequencies or rhythms collide, 2 basic obstacles are combined,
 * requiring the player to press BOTH corresponding buttons simultaneously.
 */
enum class CompositeObstacle(
    val title: String,
    val basics: Set<BasicObstacle>,
    val description: String
) {
    // Single obstacles
    BLOCK_ONLY("블록", setOf(BasicObstacle.BLOCK), "강한 드럼 비트"),
    LOOP_ONLY("고리", setOf(BasicObstacle.LOOP), "고음 멜로디 피크"),
    WAVE_ONLY("물결", setOf(BasicObstacle.WAVE), "중음역대 리듬"),
    PIT_ONLY("구덩이", setOf(BasicObstacle.PIT), "비트 드롭 / 음량 급감"),

    // 6 Composite obstacle pairs
    BLOCK_LOOP("블록 + 고리", setOf(BasicObstacle.BLOCK, BasicObstacle.LOOP), "드럼 비트 + 고음 피크 동시"),
    BLOCK_WAVE("블록 + 물결", setOf(BasicObstacle.BLOCK, BasicObstacle.WAVE), "드럼 비트 + 롤링 물결 리듬"),
    BLOCK_PIT("블록 + 구덩이", setOf(BasicObstacle.BLOCK, BasicObstacle.PIT), "드럼 비트 + 급격한 드롭"),
    LOOP_WAVE("고리 + 물결", setOf(BasicObstacle.LOOP, BasicObstacle.WAVE), "고음 멜로디 + 물결 리듬"),
    LOOP_PIT("고리 + 구덩이", setOf(BasicObstacle.LOOP, BasicObstacle.PIT), "고음 피크 + 구덩이 드롭"),
    WAVE_PIT("물결 + 구덩이", setOf(BasicObstacle.WAVE, BasicObstacle.PIT), "물결 리듬 + 구덩이 드롭");

    val isComposite: Boolean get() = basics.size > 1
}

enum class HitJudgement(val score: Int, val label: String, val comboAdd: Int) {
    PERFECT(300, "PERFECT!", 1),
    GOOD(180, "GOOD", 1),
    OK(100, "OK", 1),
    MISS(0, "OUCH!", 0)
}

data class Obstacle(
    val id: Long,
    val timestampMs: Long,
    val type: CompositeObstacle,
    var judgement: HitJudgement? = null,
    var cleared: Boolean = false
)

package com.example.game

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthPlayer
import com.example.audio.AudioTimeSlice
import com.example.audio.Difficulty
import com.example.audio.TrackAnalysisResult
import com.example.model.BasicObstacle
import com.example.model.CharacterForm
import com.example.model.HitJudgement
import com.example.model.TrackInfo
import com.example.model.VibriStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

enum class ScreenState {
    TITLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    GAME_OVER,
    STAGE_CLEAR,
    TUTORIAL
}

data class FloatingEffect(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val isGlitch: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class GameUiState(
    val screen: ScreenState = ScreenState.TITLE,
    val tracks: List<TrackInfo> = emptyList(),
    val selectedTrack: TrackInfo? = null,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val isImportingAudio: Boolean = false,
    val bufferingProgress: Float = 0f,
    val bufferingStatusText: String = "",
    val analysisResult: TrackAnalysisResult? = null,

    // Playback & Game loop
    val currentPlaybackMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val score: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val vibriStatus: VibriStatus = VibriStatus(),
    val highestFormReached: CharacterForm = CharacterForm.RABBIT,

    // Stats
    val perfectCount: Int = 0,
    val goodCount: Int = 0,
    val okCount: Int = 0,
    val missCount: Int = 0,

    // Active button presses
    val activeButtons: Set<BasicObstacle> = emptySet(),

    // Visual feedback
    val floatingEffects: List<FloatingEffect> = emptyList(),
    val currentSlice: AudioTimeSlice = AudioTimeSlice(0L, 0.1f, 0.1f, 0.1f, 0.1f),

    // Dialogs
    val showExplanationDialog: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val audioPlayer = AudioSynthPlayer(application.applicationContext)
    private val prefs = application.getSharedPreferences("vibribbon_tracks_v2", Context.MODE_PRIVATE)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var gameLoopJob: Job? = null
    private var effectIdCounter = 0L

    init {
        loadSavedTracks()
    }

    private fun loadSavedTracks() {
        val jsonStr = prefs.getString("user_tracks", null)
        val loaded = mutableListOf<TrackInfo>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val localPath = obj.optString("localFilePath")
                    // Only keep if cached file still exists
                    if (localPath.isNotBlank() && File(localPath).exists()) {
                        loaded.add(
                            TrackInfo(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                artist = obj.getString("artist"),
                                bpm = obj.optInt("bpm", 128),
                                durationMs = obj.optLong("durationMs", 60000L),
                                isCustom = true,
                                customUriString = obj.optString("customUriString").ifBlank { null },
                                localFilePath = localPath,
                                fileSizeFormatted = obj.optString("fileSizeFormatted", "")
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        _uiState.update {
            it.copy(
                tracks = loaded,
                selectedTrack = loaded.firstOrNull()
            )
        }
    }

    private fun persistTracks(tracks: List<TrackInfo>) {
        try {
            val array = JSONArray()
            for (t in tracks) {
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("bpm", t.bpm)
                obj.put("durationMs", t.durationMs)
                obj.put("customUriString", t.customUriString ?: "")
                obj.put("localFilePath", t.localFilePath ?: "")
                obj.put("fileSizeFormatted", t.fileSizeFormatted)
                array.put(obj)
            }
            prefs.edit().putString("user_tracks", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun importUserAudio(uri: Uri, rawFileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingAudio = true) }
            try {
                val newTrack = audioPlayer.importAudioFile(uri, rawFileName)
                val updated = listOf(newTrack) + _uiState.value.tracks.filter { it.id != newTrack.id }
                persistTracks(updated)
                _uiState.update {
                    it.copy(
                        tracks = updated,
                        selectedTrack = newTrack,
                        isImportingAudio = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImportingAudio = false) }
            }
        }
    }

    fun createDemoTestDisc() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingAudio = true) }
            val demoTrack = audioPlayer.createDemoTestTrack()
            val updated = listOf(demoTrack) + _uiState.value.tracks
            persistTracks(updated)
            _uiState.update {
                it.copy(
                    tracks = updated,
                    selectedTrack = demoTrack,
                    isImportingAudio = false
                )
            }
        }
    }

    fun deleteTrack(trackId: String) {
        val current = _uiState.value.tracks
        val trackToDelete = current.firstOrNull { it.id == trackId }
        trackToDelete?.localFilePath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        val updated = current.filter { it.id != trackId }
        persistTracks(updated)
        _uiState.update {
            it.copy(
                tracks = updated,
                selectedTrack = if (it.selectedTrack?.id == trackId) updated.firstOrNull() else it.selectedTrack
            )
        }
    }

    fun selectTrack(track: TrackInfo) {
        _uiState.update { it.copy(selectedTrack = track) }
    }

    fun setDifficulty(diff: Difficulty) {
        _uiState.update { it.copy(difficulty = diff) }
    }

    fun openExplanationDialog(show: Boolean) {
        _uiState.update { it.copy(showExplanationDialog = show) }
    }

    fun setScreen(screen: ScreenState) {
        if (screen == ScreenState.TITLE) {
            audioPlayer.stopPlayback()
            gameLoopJob?.cancel()
        }
        _uiState.update { it.copy(screen = screen) }
    }

    fun startTrackBufferingAndPlay(trackToPlay: TrackInfo? = null) {
        val targetTrack = trackToPlay ?: _uiState.value.selectedTrack ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    screen = ScreenState.BUFFERING,
                    selectedTrack = targetTrack,
                    bufferingProgress = 0f,
                    bufferingStatusText = "오디오 데이터 선 읽기(버퍼링) 시작..."
                )
            }

            val analysis = audioPlayer.prepareTrack(
                track = targetTrack,
                difficulty = _uiState.value.difficulty
            ) { progress, status ->
                _uiState.update {
                    it.copy(
                        bufferingProgress = progress,
                        bufferingStatusText = status
                    )
                }
            }

            delay(250)

            _uiState.update {
                it.copy(
                    screen = ScreenState.PLAYING,
                    analysisResult = analysis,
                    currentPlaybackMs = 0L,
                    totalDurationMs = analysis.totalDurationMs,
                    score = 0,
                    combo = 0,
                    maxCombo = 0,
                    perfectCount = 0,
                    goodCount = 0,
                    okCount = 0,
                    missCount = 0,
                    vibriStatus = VibriStatus(form = CharacterForm.RABBIT),
                    highestFormReached = CharacterForm.RABBIT,
                    floatingEffects = emptyList(),
                    activeButtons = emptySet()
                )
            }

            audioPlayer.startPlayback(targetTrack) {
                triggerStageClear()
            }

            startGameLoop()
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            val windowTargetMs = 240L
            while (isActive && _uiState.value.screen == ScreenState.PLAYING) {
                val currentMs = audioPlayer.getCurrentPositionMs()
                val slice = audioPlayer.getCurrentSlice(currentMs)

                val analysis = _uiState.value.analysisResult
                var updatedVibri = _uiState.value.vibriStatus
                var newCombo = _uiState.value.combo
                var isGameOver = false
                var missedSomething = false

                analysis?.obstacles?.forEach { obs ->
                    if (!obs.cleared && obs.timestampMs < currentMs - windowTargetMs) {
                        obs.cleared = true
                        obs.judgement = HitJudgement.MISS
                        newCombo = 0
                        missedSomething = true
                        val (nextVibri, dead) = updatedVibri.onHit(currentMs)
                        updatedVibri = nextVibri
                        if (dead) {
                            isGameOver = true
                        }
                        spawnFloatingEffect("OUCH!", true)
                        vibrate(90, 220)
                    }
                }

                val cleanEffects = _uiState.value.floatingEffects.filter {
                    System.currentTimeMillis() - it.timestampMs < 1200
                }

                _uiState.update {
                    it.copy(
                        currentPlaybackMs = currentMs,
                        currentSlice = slice,
                        vibriStatus = updatedVibri,
                        combo = newCombo,
                        missCount = if (missedSomething) it.missCount + 1 else it.missCount,
                        floatingEffects = cleanEffects
                    )
                }

                if (isGameOver) {
                    triggerGameOver()
                    break
                }

                delay(16)
            }
        }
    }

    fun onButtonDown(button: BasicObstacle) {
        val newButtons = _uiState.value.activeButtons + button
        _uiState.update { it.copy(activeButtons = newButtons) }
        checkHit(newButtons)
    }

    fun onButtonUp(button: BasicObstacle) {
        _uiState.update { it.copy(activeButtons = it.activeButtons - button) }
    }

    private fun checkHit(currentButtons: Set<BasicObstacle>) {
        if (_uiState.value.screen != ScreenState.PLAYING) return
        val currentMs = _uiState.value.currentPlaybackMs
        val obstacles = _uiState.value.analysisResult?.obstacles ?: return

        val candidate = obstacles.firstOrNull { obs ->
            !obs.cleared && abs(obs.timestampMs - currentMs) <= 240L
        } ?: return

        val required = candidate.type.basics
        val diffMs = abs(candidate.timestampMs - currentMs)

        if (currentButtons.containsAll(required)) {
            candidate.cleared = true
            val judgement = when {
                diffMs <= 85L -> HitJudgement.PERFECT
                diffMs <= 160L -> HitJudgement.GOOD
                else -> HitJudgement.OK
            }
            candidate.judgement = judgement

            val formMultiplier = _uiState.value.vibriStatus.form.scoreMultiplier
            val addedScore = (judgement.score * formMultiplier).toInt()
            val newCombo = _uiState.value.combo + 1
            val newMaxCombo = maxOf(_uiState.value.maxCombo, newCombo)

            // Trigger specific clearing acrobatics motion!
            val nextVibri = _uiState.value.vibriStatus.onClear(candidate.type, currentMs)
            val newHighest = if (nextVibri.form == CharacterForm.QUEEN) {
                CharacterForm.QUEEN
            } else {
                _uiState.value.highestFormReached
            }

            spawnFloatingEffect(judgement.label, false)
            vibrate(35, 120)

            _uiState.update {
                it.copy(
                    score = it.score + addedScore,
                    combo = newCombo,
                    maxCombo = newMaxCombo,
                    vibriStatus = nextVibri,
                    highestFormReached = newHighest,
                    perfectCount = if (judgement == HitJudgement.PERFECT) it.perfectCount + 1 else it.perfectCount,
                    goodCount = if (judgement == HitJudgement.GOOD) it.goodCount + 1 else it.goodCount,
                    okCount = if (judgement == HitJudgement.OK) it.okCount + 1 else it.okCount
                )
            }
        }
    }

    private fun spawnFloatingEffect(text: String, isGlitch: Boolean) {
        val effect = FloatingEffect(
            id = effectIdCounter++,
            text = text,
            x = 0.5f,
            y = 0.4f,
            isGlitch = isGlitch
        )
        _uiState.update {
            it.copy(floatingEffects = it.floatingEffects + effect)
        }
    }

    fun pauseGame() {
        audioPlayer.pausePlayback()
        _uiState.update { it.copy(screen = ScreenState.PAUSED) }
    }

    fun resumeGame() {
        audioPlayer.resumePlayback()
        _uiState.update { it.copy(screen = ScreenState.PLAYING) }
        startGameLoop()
    }

    private fun triggerGameOver() {
        audioPlayer.stopPlayback()
        gameLoopJob?.cancel()
        vibrate(300, 255)
        _uiState.update { it.copy(screen = ScreenState.GAME_OVER) }
    }

    private fun triggerStageClear() {
        audioPlayer.stopPlayback()
        gameLoopJob?.cancel()
        vibrate(150, 180)
        _uiState.update { it.copy(screen = ScreenState.STAGE_CLEAR) }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        amplitude.coerceIn(1, 255)
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopPlayback()
        gameLoopJob?.cancel()
    }
}

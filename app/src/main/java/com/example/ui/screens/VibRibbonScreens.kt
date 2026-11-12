package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.Difficulty
import com.example.game.GameUiState
import com.example.game.GameViewModel
import com.example.game.ScreenState
import com.example.model.BasicObstacle
import com.example.model.CharacterForm
import com.example.model.TrackInfo
import com.example.ui.components.VibRibbonCanvas
import com.example.ui.components.VibRibbonController

@Composable
fun VibRibbonMainScreen(viewModel: GameViewModel, state: GameUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020206))
    ) {
        when (state.screen) {
            ScreenState.TITLE -> TitleScreen(viewModel, state)
            ScreenState.BUFFERING -> BufferingScreen(state)
            ScreenState.PLAYING -> PlayingScreen(viewModel, state)
            ScreenState.PAUSED -> PausedOverlay(viewModel, state)
            ScreenState.GAME_OVER -> GameOverScreen(viewModel, state)
            ScreenState.STAGE_CLEAR -> StageClearScreen(viewModel, state)
            ScreenState.TUTORIAL -> TutorialScreen(viewModel)
        }

        if (state.showExplanationDialog) {
            WaveformExplanationDialog(onDismiss = { viewModel.openExplanationDialog(false) })
        }
    }
}

@Composable
fun TitleScreen(viewModel: GameViewModel, state: GameUiState) {
    val context = LocalContext.current
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var fileName = "내 오디오 트랙.mp3"
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
            viewModel.importUserAudio(it, fileName)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Retro Vector Logo Header
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚡ VIB-RIBBON ⚡",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
        Text(
            text = "실시간 오디오 파형 분석 리듬 머신",
            color = Color(0xFF00FFCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Large Interactive CD Drive Slot (Upload Centerpiece)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0F1B)),
            border = BorderStroke(1.5.dp, Color(0xFF00FFCC)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { audioPickerLauncher.launch("audio/*") }
                .testTag("btn_upload_cd")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spinning Holographic CD Disc Graphic
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(2.dp, Color(0xFF00FFCC)), CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00FFCC),
                                    Color(0xFF7928CA),
                                    Color(0xFFFF0080),
                                    Color(0xFF00FFCC)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0F1B))
                            .border(BorderStroke(1.5.dp, Color.White), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "음악 CD 파일 넣기 / 업로드",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "MP3, WAV, FLAC, M4A 파일 지원 (터치하여 선택)",
                        color = Color(0xBB00FFCC),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "업로드",
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Mode Options (Waveform Explanation & Tutorial)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.openExplanationDialog(true) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("파형 분석 원리", color = Color.White, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { viewModel.setScreen(ScreenState.TUTORIAL) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("장애물 모션 연습", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Difficulty Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("난이도: ", color = Color(0x88FFFFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Difficulty.entries.forEach { diff ->
                val selected = state.difficulty == diff
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setDifficulty(diff) },
                    label = {
                        Text(
                            text = when (diff) {
                                Difficulty.EASY -> "EASY"
                                Difficulty.NORMAL -> "NORMAL"
                                Difficulty.VIB_MASTER -> "VIB-MASTER"
                            },
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00FFCC),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF10121C),
                        labelColor = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Uploaded Disc Library List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "내 음악 CD 보관함 (${state.tracks.size}곡)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            // Demo generator button for users without local audio files
            Text(
                text = "+ 샘플 데모 CD 추가",
                color = Color(0xFFFFD166),
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { viewModel.createDemoTestDisc() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (state.isImportingAudio) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color(0xFF00FFCC), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("오디오 파일 로드 및 캐시 준비 중...", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (state.tracks.isEmpty() && !state.isImportingAudio) {
            // Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0C14)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Icon(imageVector = Icons.Default.Album, contentDescription = null, tint = Color(0x44FFFFFF), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "CD 트레이가 비어 있습니다",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "상단의 [음악 CD 파일 넣기]를 눌러\n스마트폰의 음악을 넣어주세요!",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tracks) { track ->
                    val isSelected = track.id == state.selectedTrack?.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF162032) else Color(0xFF0E101A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) Color(0xFF00FFCC) else Color(0x22FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectTrack(track) }
                            .testTag("track_${track.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF00FFCC) else Color(0xFF1D2233)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${track.artist} • ${track.fileSizeFormatted}",
                                    color = Color(0x88FFFFFF),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteTrack(track.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = Color(0x66FFFFFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Start Playback Button
        val hasTrack = state.selectedTrack != null
        Button(
            onClick = { viewModel.startTrackBufferingAndPlay() },
            enabled = hasTrack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                disabledContainerColor = Color(0x33FFFFFF)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_start_game")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (hasTrack) Color.Black else Color(0x66000000),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasTrack) "START VIBRI RUN (곡 분석 및 시작)" else "음악 파일을 먼저 선택하세요",
                color = if (hasTrack) Color.Black else Color(0x66000000),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BufferingScreen(state: GameUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, Color(0xFF00FFCC)), CircleShape)
                .background(Color(0xFF0C101C)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF00FFCC),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "선 읽기(버퍼링) & 파형 분석 중",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.bufferingStatusText,
            color = Color(0xCCFFFFFF),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        LinearProgressIndicator(
            progress = { state.bufferingProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF00FFCC),
            trackColor = Color(0xFF1A2234)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${(state.bufferingProgress * 100).toInt()}%",
            color = Color(0xFF00FFCC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PlayingScreen(viewModel: GameViewModel, state: GameUiState) {
    val track = state.selectedTrack ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP HUD: Song Info, Evolution Form Badge, Score, Combo, Pause
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                val formColor = when (state.vibriStatus.form) {
                    CharacterForm.QUEEN -> Color(0xFFFFE066)
                    CharacterForm.RABBIT -> Color(0xFF00FFCC)
                    CharacterForm.FROG -> Color(0xFF66FF66)
                    CharacterForm.SLUG -> Color(0xFFFF6666)
                }
                Text(
                    text = "진화: ${state.vibriStatus.form.titleKorean}",
                    color = formColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SCORE: ${state.score}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (state.combo > 1) "COMBO: ${state.combo}x" else "COMBO: 0",
                    color = if (state.combo >= 10) Color(0xFFFFE066) else Color(0xAAFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = { viewModel.pauseGame() },
                modifier = Modifier.testTag("btn_pause")
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "일시정지",
                    tint = Color.White
                )
            }
        }

        // Live Audio Lookahead Buffer & Progress Bar
        val progressFraction = if (state.totalDurationMs > 0) {
            (state.currentPlaybackMs.toFloat() / state.totalDurationMs).coerceIn(0f, 1f)
        } else 0f
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Color(0xFF00FFCC),
            trackColor = Color(0x33FFFFFF)
        )

        // Main Visual Canvas with real-time vector ribbon and acrobatic Vibri
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            VibRibbonCanvas(
                currentPlaybackMs = state.currentPlaybackMs,
                vibriStatus = state.vibriStatus,
                obstacles = state.analysisResult?.obstacles ?: emptyList(),
                currentSlice = state.currentSlice,
                bpm = state.analysisResult?.averageBpm ?: track.bpm
            )

            // Floating Judgement Text Overlay
            state.floatingEffects.forEach { effect ->
                Text(
                    text = effect.text,
                    color = if (effect.isGlitch) Color(0xFFFF4D4D) else Color(0xFF00FFCC),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Bottom Multi-touch Controller
        VibRibbonController(
            activeButtons = state.activeButtons,
            onButtonDown = { viewModel.onButtonDown(it) },
            onButtonUp = { viewModel.onButtonUp(it) }
        )
    }
}

@Composable
fun PausedOverlay(viewModel: GameViewModel, state: GameUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE020206)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "PAUSED",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.resumeGame() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .testTag("btn_resume")
            ) {
                Text("RESUME (계속하기)", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.startTrackBufferingAndPlay() },
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .testTag("btn_retry_pause")
            ) {
                Text("RETRY (재도전)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.setScreen(ScreenState.TITLE) },
                border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .testTag("btn_title_pause")
            ) {
                Text("TITLE (메인 메뉴)", color = Color(0xAAFFFFFF))
            }
        }
    }
}

@Composable
fun GameOverScreen(viewModel: GameViewModel, state: GameUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚡ GAME OVER ⚡",
            color = Color(0xFFFF4D4D),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "굼벵이 상태에서 장애물에 충돌했습니다!",
            color = Color(0xAAFFFFFF),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("최종 점수: ${state.score}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("최대 콤보: ${state.maxCombo}x", color = Color(0xFFFFD166), fontSize = 14.sp)
                Text("PERFECT: ${state.perfectCount} | GOOD: ${state.goodCount} | MISS: ${state.missCount}", color = Color(0x88FFFFFF), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { viewModel.startTrackBufferingAndPlay() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_retry_gameover")
        ) {
            Text("다시 도전하기 (RETRY)", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = { viewModel.setScreen(ScreenState.TITLE) },
            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("곡 목록으로 돌아가기", color = Color.White)
        }
    }
}

@Composable
fun StageClearScreen(viewModel: GameViewModel, state: GameUiState) {
    val totalHits = state.perfectCount + state.goodCount + state.okCount + state.missCount
    val accuracy = if (totalHits > 0) {
        ((state.perfectCount * 1.0 + state.goodCount * 0.8 + state.okCount * 0.5) / totalHits * 100).toInt()
    } else 100

    val rank = when {
        accuracy >= 92 && state.missCount == 0 -> "S"
        accuracy >= 80 -> "A"
        accuracy >= 65 -> "B"
        else -> "C"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "★ STAGE CLEAR! ★",
            color = Color(0xFF00FFCC),
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(BorderStroke(3.dp, Color(0xFFFFD166)), CircleShape)
                .background(Color(0xFF141724)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank,
                color = Color(0xFFFFD166),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("곡명: ${state.selectedTrack?.title}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("최종 점수: ${state.score}", color = Color(0xFF00FFCC), fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("정확도: $accuracy% | 최대 콤보: ${state.maxCombo}x", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                Text("최고 진화 상태: ${state.highestFormReached.titleKorean}", color = Color(0xFFFFE066), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("PERFECT: ${state.perfectCount} | GOOD: ${state.goodCount} | OK: ${state.okCount} | MISS: ${state.missCount}", color = Color(0x88FFFFFF), fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.setScreen(ScreenState.TITLE) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_clear_next")
        ) {
            Text("다른 곡 선택하기", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TutorialScreen(viewModel: GameViewModel) {
    var activeSimulatorButtons by remember { mutableStateOf<Set<BasicObstacle>>(emptySet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { viewModel.setScreen(ScreenState.TITLE) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로", tint = Color.White)
            }
            Text(
                text = "장애물 넘기 아크로바틱 모션",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "◆ 장애물별 전용 아크로바틱 모션",
                    color = Color(0xFF00FFCC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(
                listOf(
                    Triple("□ 블록 넘기", "[360° 공중제비 볼트 점프]", "무릎을 턱으로 당기며 전방 360도 공중제비로 상자를 뛰어넘습니다."),
                    Triple("◯ 고리 통과", "[어뢰 회전 다이빙 롤]", "양팔을 앞으로 뻗고 링 중심부를 수평으로 꿰뚫으며 회전 다이빙합니다."),
                    Triple("〜 물결 타기", "[네온 서프보드 바디 서핑]", "몸을 뒤로 젖히고 파도의 정상을 서핑하듯 미끄러지며 유려하게 활강합니다."),
                    Triple("▽ 구덩이 넘기", "[대형 스플릿 허들 도약]", "다리를 앞뒤로 길게 찢는 역동적인 허들 점프로 깊은 크레바스를 도약합니다."),
                    Triple("⚡ 합성 장애물", "[720° 코크스크루 트위스터]", "수직으로 솟구쳐 720도 회전 스핀과 스파크 충격파를 발산하며 돌파합니다.")
                )
            ) { (name, motion, desc) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101320)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(motion, color = Color(0xFFFFD166), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, color = Color(0x88FFFFFF), fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "▼ 입력 테스트 시뮬레이터 (동시 터치 지원)",
            color = Color(0xAAFFFFFF),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        VibRibbonController(
            activeButtons = activeSimulatorButtons,
            onButtonDown = { activeSimulatorButtons = activeSimulatorButtons + it },
            onButtonUp = { activeSimulatorButtons = activeSimulatorButtons - it }
        )
    }
}

@Composable
fun WaveformExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "실시간 오디오 파형(Waveform) 분석",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "1. 선 읽기(버퍼링) 방식\n음악 CD를 넣고 곡을 선택하면, 게임은 음악을 스피커로 출력하기 직전에 오디오 데이터를 먼저 읽어 들여 분석합니다.",
                    color = Color(0xDDFFFFFF),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = "2. 음향 데이터와 장애물 매칭\n곡의 템포(BPM), 비트의 강약, 주파수 대역(저음과 고음의 변화) 등을 알고리즘으로 계산합니다. 강한 드럼 비트 구간에는 '블록', 고음 피크에는 '고리', 중음 지속음에는 '물결', 급격한 음량 드롭에는 '구덩이'를 자동 배치합니다.",
                    color = Color(0xDDFFFFFF),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = "3. 합성 장애물 생성\n비트가 겹치거나 변칙적인 리듬이 나오는 구간에서는 기본 4가지 장애물을 두 개씩 조합한 고난도 복합 장애물을 만들어내어 곡의 분위기를 난이도에 반영합니다.",
                    color = Color(0xDDFFFFFF),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
            ) {
                Text("확인", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF141420),
        shape = RoundedCornerShape(14.dp)
    )
}

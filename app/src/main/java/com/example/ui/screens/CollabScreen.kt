package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.GeminiToolType
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CollabMessage
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun CollabScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val collabAgents = remember {
        GeminiToolType.entries.filter {
            it != GeminiToolType.VISION_ANALYZER && it != GeminiToolType.FUNCTION_TOKEN_LAB
        }
    }
    var showSetupPanel by remember { mutableStateOf(true) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 상호 소통 회의실", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        text = "선택한 Gemini AI들이 서로의 응답을 읽고 토론·검증·보완합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (state.collabMessages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.saveCollabTranscriptToHistory() },
                        modifier = Modifier.testTag("collab_save_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "회의록 저장", tint = EmeraldTertiary)
                    }
                }
            }
        }

        // Saved Banner
        state.collabStatusBanner?.let { banner ->
            item {
                Surface(
                    color = EmeraldTertiary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldTertiary)
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldTertiary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Collapsible Setup Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSetupPanel = !showSetupPanel },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "참여 AI (${state.collabParticipants.size}개) & 라운드 (${state.collabRounds}R)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showSetupPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "접기/펼치기",
                            tint = TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = showSetupPanel) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("참여할 AI 선택 (최소 2개)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                collabAgents.forEach { agent ->
                                    val selected = state.collabParticipants.contains(agent)
                                    val (_, accent) = toolVisualMeta(agent)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.toggleCollabParticipant(agent) },
                                        label = { Text("${String.format("%02d", agent.apiNumber)} ${agent.title}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.22f),
                                            selectedLabelColor = accent
                                        ),
                                        modifier = Modifier.testTag("collab_agent_${agent.name.lowercase()}")
                                    )
                                }
                            }

                            // Rounds selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("상호 소통 라운드:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                listOf(1, 2, 3).forEach { round ->
                                    FilterChip(
                                        selected = state.collabRounds == round,
                                        onClick = { viewModel.updateCollabRounds(round) },
                                        label = { Text("${round}R") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanPrimary.copy(alpha = 0.22f),
                                            selectedLabelColor = CyanPrimary
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = state.collabTopic,
                                onValueChange = { viewModel.updateCollabTopic(it) },
                                label = { Text("AI들에게 던질 공동 주제 / 안건") },
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedContainerColor = StudioSurfaceVariant,
                                    unfocusedContainerColor = StudioSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("collab_topic_input")
                            )

                            val isRunning = state.collabRunningSpeaker != null
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.startCollabDiscussion() },
                                    enabled = !isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF04131E)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("collab_start_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI 회의 시작 (릴레이 소통)", fontWeight = FontWeight.Bold)
                                }

                                if (isRunning) {
                                    OutlinedButton(
                                        onClick = { viewModel.stopCollabDiscussion() },
                                        border = BorderStroke(1.dp, CoralError),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "중지", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Speaking Status Indicator
        state.collabRunningSpeaker?.let { speaker ->
            val (_, accent) = toolVisualMeta(speaker)
            item {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${speaker.title}가 이전 AI들의 발언과 지시문을 읽고 답변 중입니다...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                }
            }
        }

        // Error Banner
        state.collabError?.let { err ->
            item {
                Surface(
                    color = CoralError.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoralError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoralError,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Conversation Messages
        if (state.collabMessages.isEmpty() && state.collabRunningSpeaker == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("어떻게 AI끼리 소통하나요?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. 각 AI는 [공통 지시문]과 [자기만의 개별 지시문]을 독립적으로 부여받습니다.\n" +
                                "2. 회의가 시작되면 앞선 AI의 실제 응답을 다음 AI에게 컨텍스트로 전달해 서로 반박, 수치 검증(Python), 웹 검색 보완, JSON 구조화를 이어갑니다.\n" +
                                "3. 회의 중 언제든 사회자로 개입하거나 특정 AI를 지목해 추가 발언을 시킬 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(state.collabMessages, key = { it.id }) { msg ->
                CollabMessageCard(msg = msg)
            }
        }

        // Moderator Interjection & Directed AI Follow-up Bar
        if (state.collabMessages.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("사회자 개입 & 특정 AI 지목 이어말하기", style = MaterialTheme.typography.labelMedium, color = AmberSecondary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.collabModeratorInput,
                                onValueChange = { viewModel.updateCollabModeratorInput(it) },
                                placeholder = { Text("추가 질문이나 반론 지시를 입력하세요...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberSecondary,
                                    unfocusedBorderColor = StudioBorder
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val firstParticipant = state.collabParticipants.firstOrNull()
                                    viewModel.sendModeratorInterjectionAndTriggerNext(firstParticipant)
                                },
                                enabled = state.collabRunningSpeaker == null && state.collabModeratorInput.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AmberSecondary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "개입 전송", tint = Color(0xFF1E1403))
                            }
                        }

                        // Quick mention pills to ask a specific AI to reply to the thread
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            collabAgents.forEach { agent ->
                                val (_, accent) = toolVisualMeta(agent)
                                OutlinedButton(
                                    onClick = { viewModel.triggerSingleCollabAgentTurn(agent) },
                                    enabled = state.collabRunningSpeaker == null,
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+ ${agent.title} 발언",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun CollabMessageCard(msg: CollabMessage) {
    val uriHandler = LocalUriHandler.current
    val tool = msg.speakerTool
    val accent = if (tool != null) toolVisualMeta(tool).second else AmberSecondary
    var showThoughts by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (tool == null) AmberSecondary.copy(alpha = 0.08f) else StudioSurface
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(msg.speakerLabel, style = MaterialTheme.typography.titleMedium, color = accent)
                }

                if (tool != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (msg.appliedCommon) {
                            Surface(color = CyanPrimary.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                                Text("공통", style = MaterialTheme.typography.labelSmall, color = CyanPrimary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        if (msg.appliedIndividual) {
                            Surface(color = EmeraldTertiary.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                                Text("개별", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            if (msg.thoughts.isNotBlank()) {
                Surface(
                    color = StudioSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThoughts = !showThoughts }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = RoseAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showThoughts) "사고 과정 접기" else "사고 과정 보기",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoseAccent
                            )
                        }
                        AnimatedVisibility(visible = showThoughts) {
                            Text(
                                text = msg.thoughts,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            Text(msg.content, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)

            msg.codeBlocks.forEach { block ->
                Surface(
                    color = Color(0xFF0A0F18),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Python 검증 실행", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFont),
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "결과: ${block.output}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTertiary
                        )
                    }
                }
            }

            if (msg.groundingSources.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    msg.groundingSources.forEach { source ->
                        Surface(
                            color = StudioSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                try { uriHandler.openUri(source.uri) } catch (_: Exception) {}
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(source.title, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

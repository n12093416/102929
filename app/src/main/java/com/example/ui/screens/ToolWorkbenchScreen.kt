package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.GeminiApiResponse
import com.example.data.remote.GeminiToolType
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.ToolExecutionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWorkbenchScreen(
    tool: GeminiToolType,
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    BackHandler { viewModel.closeTool() }

    val (_, accent) = toolVisualMeta(tool)
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.selectVisionImage(uri)
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeTool() },
                        modifier = Modifier.testTag("workbench_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "${String.format("%02d", tool.apiNumber)}. ${tool.title}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tool.endpointTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    val canSave = state.executionState is ToolExecutionState.Success ||
                        (state.executionState is ToolExecutionState.Streaming && state.executionState.partialText.isNotBlank())
                    IconButton(
                        onClick = { viewModel.saveCurrentResultToHistory() },
                        enabled = canSave,
                        modifier = Modifier.testTag("workbench_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "보관함에 저장",
                            tint = if (canSave) EmeraldTertiary else TextMuted
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.statusBannerMessage?.let { msg ->
                Surface(
                    color = EmeraldTertiary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldTertiary),
                    modifier = Modifier.clickable { viewModel.clearStatusBanner() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = EmeraldTertiary)
                    }
                }
            }

            // Independent Common + Individual Directives Panel for all 8 APIs
            IndependentDirectivesPanel(
                tool = tool,
                state = state,
                viewModel = viewModel
            )

            when (tool) {
                GeminiToolType.MULTI_TURN_CHAT,
                GeminiToolType.STREAMING_WRITER -> {
                    TemperatureControlCard(
                        temperature = state.temperature,
                        accent = accent,
                        onTemperatureChange = { viewModel.updateTemperature(it) }
                    )
                }
                GeminiToolType.VISION_ANALYZER -> {
                    VisionImagePickerCard(
                        selectedImageUri = state.selectedImageUri,
                        onPickImage = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                GeminiToolType.STRUCTURED_JSON -> {
                    JsonSchemaSelectorCard(
                        selectedMode = state.jsonSchemaMode,
                        onSelectMode = { viewModel.updateJsonSchemaMode(it) }
                    )
                }
                GeminiToolType.THINKING_REASONER -> {
                    ThinkingBudgetSelectorCard(
                        budget = state.thinkingBudget,
                        onSelectBudget = { viewModel.updateThinkingBudget(it) }
                    )
                }
                else -> {}
            }

            if (tool == GeminiToolType.MULTI_TURN_CHAT && state.chatHistory.isNotEmpty()) {
                ChatThreadSection(
                    state = state,
                    onClearChat = { viewModel.clearChatHistory() }
                )
            }

            OutlinedTextField(
                value = state.promptInput,
                onValueChange = { viewModel.updatePromptInput(it) },
                label = {
                    Text(
                        if (tool == GeminiToolType.FUNCTION_TOKEN_LAB) "텍스트 A (기준 문장)"
                        else "프롬프트 입력"
                    )
                },
                minLines = 3,
                maxLines = 7,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = StudioBorder,
                    focusedContainerColor = StudioSurface,
                    unfocusedContainerColor = StudioSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workbench_prompt_input")
            )

            if (tool == GeminiToolType.FUNCTION_TOKEN_LAB) {
                OutlinedTextField(
                    value = state.secondaryInput,
                    onValueChange = { viewModel.updateSecondaryInput(it) },
                    label = { Text("텍스트 B (유사도·함수 비교 문장)") },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberSecondary,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioSurface,
                        unfocusedContainerColor = StudioSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workbench_secondary_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.checkPromptTokens() },
                    enabled = !state.isCountingTokens && state.promptInput.isNotBlank(),
                    border = BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("workbench_token_button")
                ) {
                    if (state.isCountingTokens) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Token, contentDescription = "토큰 수 계산", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.liveTokenCount?.let { "$it tok" } ?: "토큰 확인",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = { viewModel.executeActiveTool() },
                    enabled = state.executionState !is ToolExecutionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF06111B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("workbench_run_button")
                ) {
                    Icon(
                        imageVector = if (tool == GeminiToolType.MULTI_TURN_CHAT) Icons.AutoMirrored.Filled.Send else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tool == GeminiToolType.MULTI_TURN_CHAT) "전송하기" else "${tool.badgeLabel} 실행",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ExecutionOutputPanel(
                tool = tool,
                executionState = state.executionState,
                accent = accent,
                onCopyText = { text ->
                    clipboard.setText(AnnotatedString(text))
                }
            )
        }
    }
}

@Composable
private fun IndependentDirectivesPanel(
    tool: GeminiToolType,
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val indText = state.individualDirectives[tool].orEmpty()
    val indEnabled = state.individualEnabledMap[tool] ?: true

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "지시문 독립 설정 · 공통(${if (state.useCommonDirective) "ON" else "OFF"}) / 개별(${if (indEnabled) "ON" else "OFF"})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "펼치기",
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Common Directive block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. 모든 AI 공통 지시문", style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
                        Switch(
                            checked = state.useCommonDirective,
                            onCheckedChange = { viewModel.toggleCommonDirective(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CyanPrimary)
                        )
                    }
                    OutlinedTextField(
                        value = state.commonDirective,
                        onValueChange = { viewModel.updateCommonDirective(it) },
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = StudioBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Individual Directive block (independent!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. '${tool.title}' 전용 개별 지시문", style = MaterialTheme.typography.labelMedium, color = EmeraldTertiary)
                        Switch(
                            checked = indEnabled,
                            onCheckedChange = { viewModel.toggleIndividualDirective(tool, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = EmeraldTertiary)
                        )
                    }
                    OutlinedTextField(
                        value = indText,
                        onValueChange = { viewModel.updateIndividualDirective(tool, it) },
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldTertiary,
                            unfocusedBorderColor = StudioBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureControlCard(temperature: Float, accent: Color, onTemperatureChange: (Float) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("창의성 Temperature", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(String.format("%.2f", temperature), style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }
            Slider(
                value = temperature,
                onValueChange = onTemperatureChange,
                valueRange = 0f..1.5f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
        }
    }
}

@Composable
private fun VisionImagePickerCard(selectedImageUri: android.net.Uri?, onPickImage: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "선택된 이미지",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onPickImage,
                border = BorderStroke(1.dp, EmeraldTertiary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_pick_image_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = EmeraldTertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedImageUri == null) "사진 선택하기 (Android Photo Picker)" else "다른 이미지 선택하기",
                    color = EmeraldTertiary
                )
            }
        }
    }
}

@Composable
private fun JsonSchemaSelectorCard(selectedMode: String, onSelectMode: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedMode == "ACTION_PLAN",
            onClick = { onSelectMode("ACTION_PLAN") },
            label = { Text("실행 계획 스키마") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VioletAccent.copy(alpha = 0.25f),
                selectedLabelColor = VioletAccent
            )
        )
        FilterChip(
            selected = selectedMode == "INSIGHT_SUMMARY",
            onClick = { onSelectMode("INSIGHT_SUMMARY") },
            label = { Text("핵심 요약 + 감정 점수 스키마") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VioletAccent.copy(alpha = 0.25f),
                selectedLabelColor = VioletAccent
            )
        )
    }
}

@Composable
private fun ThinkingBudgetSelectorCard(budget: Int, onSelectBudget: (Int) -> Unit) {
    Column {
        Text("Thinking Budget (추론 토큰 한도)", style = MaterialTheme.typography.labelMedium, color = RoseAccent)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1024 to "빠른 추론 (1K)", 4096 to "표준 추론 (4K)", 8192 to "심층 추론 (8K)").forEach { (value, label) ->
                FilterChip(
                    selected = budget == value,
                    onClick = { onSelectBudget(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoseAccent.copy(alpha = 0.22f),
                        selectedLabelColor = RoseAccent
                    )
                )
            }
        }
    }
}

@Composable
private fun ChatThreadSection(state: StudioUiState, onClearChat: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("대화 히스토리 (${state.chatHistory.size}턴)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            IconButton(onClick = onClearChat, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "대화 초기화", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        state.chatHistory.forEach { turn ->
            val isUser = turn.role == "user"
            Surface(
                color = if (isUser) CyanPrimary.copy(alpha = 0.14f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isUser) CyanPrimary.copy(alpha = 0.4f) else StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "USER" else "GEMINI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) CyanPrimary else EmeraldTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(turn.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ExecutionOutputPanel(
    tool: GeminiToolType,
    executionState: ToolExecutionState,
    accent: Color,
    onCopyText: (String) -> Unit
) {
    when (executionState) {
        ToolExecutionState.Idle -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "실행 버튼을 누르면 ${tool.endpointTag} 엔드포인트로 실제 요청을 전송합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        ToolExecutionState.Loading -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Gemini API 응답을 처리하는 중입니다...", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
        is ToolExecutionState.Streaming -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, AmberSecondary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = AmberSecondary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Firebase AI 실시간 스트리밍 수신 중...", style = MaterialTheme.typography.labelMedium, color = AmberSecondary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = executionState.partialText.ifEmpty { "..." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
        is ToolExecutionState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CoralError.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, CoralError),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = executionState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralError,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        is ToolExecutionState.Success -> {
            SuccessResponseCards(
                response = executionState.response,
                accent = accent,
                onCopyText = onCopyText
            )
        }
    }
}

@Composable
private fun SuccessResponseCards(
    response: GeminiApiResponse,
    accent: Color,
    onCopyText: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showThoughts by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        response.functionAnalysisResult?.let { fn ->
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, AmberSecondary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Function Calling & 토큰 실측 분석 (${fn.functionCalled})", style = MaterialTheme.typography.labelMedium, color = AmberSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    val pct = (fn.jaccardSimilarity * 100.0).coerceIn(0.0, 100.0)
                    Text(
                        text = String.format("%.1f%% 어휘 겹침", pct),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { fn.jaccardSimilarity.toFloat().coerceIn(0f, 1f) },
                        color = AmberSecondary,
                        trackColor = StudioSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "공통 키워드 ${fn.sharedKeywordsCount}개 · 텍스트 A(${fn.textATokens} tok) vs 텍스트 B(${fn.textBTokens} tok)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (response.thoughts.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
                border = BorderStroke(1.dp, RoseAccent.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThoughts = !showThoughts },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = RoseAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("모델 사고 과정 (Thinking Trace)", style = MaterialTheme.typography.labelMedium, color = RoseAccent)
                        }
                        Icon(
                            imageVector = if (showThoughts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "접기/펼치기",
                            tint = TextSecondary
                        )
                    }
                    AnimatedVisibility(visible = showThoughts) {
                        Text(
                            text = response.thoughts,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }

        val mainText = response.jsonFormatted.ifBlank { response.text }
        if (mainText.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (response.jsonFormatted.isNotBlank()) "구조화 JSON 응답" else "Gemini 응답",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        IconButton(onClick = { onCopyText(mainText) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "복사", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mainText,
                        style = if (response.jsonFormatted.isNotBlank()) {
                            MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFont)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = TextPrimary
                    )
                }
            }
        }

        response.codeBlocks.forEachIndexed { idx, block ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F18)),
                border = BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("실행 코드 #${idx + 1} (${block.language})", style = MaterialTheme.typography.labelMedium, color = EmeraldTertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFont),
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = StudioSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("실행 결과 (stdout)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = block.output,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFont),
                                color = EmeraldTertiary
                            )
                        }
                    }
                }
            }
        }

        if (response.groundingSources.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google 검색 검증 출처 (${response.groundingSources.size})", style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
                    response.groundingSources.forEach { source ->
                        Surface(
                            color = StudioSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try { uriHandler.openUri(source.uri) } catch (_: Exception) {}
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(source.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(source.uri, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (response.totalTokenCount > 0) {
            Text(
                text = "사용 토큰 · Prompt: ${response.promptTokenCount} | Output: ${response.candidateTokenCount} | Total: ${response.totalTokenCount}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

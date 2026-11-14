package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.GeminiToolType
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.MainTab
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun StudioHomeScreen(
    state: StudioUiState,
    recordCount: Int,
    viewModel: StudioViewModel,
    onToolClick: (GeminiToolType) -> Unit,
    onNavigateTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(columns) }) {
                StudioHeroBanner(
                    state = state,
                    recordCount = recordCount,
                    onAccountClick = { onNavigateTab(MainTab.ACCOUNT) },
                    onCollabClick = { onNavigateTab(MainTab.COLLAB) },
                    onHistoryClick = { onNavigateTab(MainTab.HISTORY) }
                )
            }

            item(span = { GridItemSpan(columns) }) {
                DirectivesConsoleCard(
                    state = state,
                    viewModel = viewModel
                )
            }

            item(span = { GridItemSpan(columns) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "8대 Gemini API 작업 도구",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Surface(
                        color = StudioSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Text(
                            text = "Firebase AI Logic · App Check",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(GeminiToolType.entries, key = { it.name }) { tool ->
                val hasCommon = state.useCommonDirective && state.commonDirective.isNotBlank()
                val hasIndividual = state.individualEnabledMap[tool] == true &&
                    !state.individualDirectives[tool].isNullOrBlank()
                GeminiToolCard(
                    tool = tool,
                    hasCommonDirective = hasCommon,
                    hasIndividualDirective = hasIndividual,
                    onClick = { onToolClick(tool) }
                )
            }

            item(span = { GridItemSpan(columns) }) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DirectivesConsoleCard(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var activeDirectiveTab by remember { mutableIntStateOf(0) } // 0 = Common, 1 = Per-AI Individual
    val activeTool = state.selectedDirectiveEditorTool
    val individualText = state.individualDirectives[activeTool].orEmpty()
    val individualEnabled = state.individualEnabledMap[activeTool] ?: true

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("directives_console_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("AI 지시문 콘솔 (공통 / 개별 분리)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "공통 ${if (state.useCommonDirective) "ON" else "OFF"} · 개별 ${state.individualEnabledMap.count { it.value }}/${GeminiToolType.entries.size}개 활성",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "지시문 콘솔 펼치기",
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabRow(
                        selectedTabIndex = activeDirectiveTab,
                        containerColor = StudioSurfaceVariant,
                        contentColor = CyanPrimary
                    ) {
                        Tab(
                            selected = activeDirectiveTab == 0,
                            onClick = { activeDirectiveTab = 0 },
                            text = { Text("공통 지시문 (전체 AI)", maxLines = 1) },
                            modifier = Modifier.testTag("directive_tab_common")
                        )
                        Tab(
                            selected = activeDirectiveTab == 1,
                            onClick = { activeDirectiveTab = 1 },
                            text = { Text("개별 지시문 (AI별 독립)", maxLines = 1) },
                            modifier = Modifier.testTag("directive_tab_individual")
                        )
                    }

                    if (activeDirectiveTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("모든 AI 호출 시 공통으로 함께 적용", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Switch(
                                checked = state.useCommonDirective,
                                onCheckedChange = { viewModel.toggleCommonDirective(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CyanPrimary),
                                modifier = Modifier.testTag("common_directive_switch")
                            )
                        }
                        OutlinedTextField(
                            value = state.commonDirective,
                            onValueChange = { viewModel.updateCommonDirective(it) },
                            label = { Text("공통 지시문 입력 (개별 지시문과 별개로 적용됨)") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = StudioBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("common_directive_input")
                        )
                    } else {
                        Text("개별 지시문을 설정할 AI 선택", style = MaterialTheme.typography.labelMedium, color = EmeraldTertiary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GeminiToolType.entries.forEach { tool ->
                                FilterChip(
                                    selected = activeTool == tool,
                                    onClick = { viewModel.selectDirectiveEditorTool(tool) },
                                    label = { Text("${String.format("%02d", tool.apiNumber)} ${tool.title}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldTertiary.copy(alpha = 0.22f),
                                        selectedLabelColor = EmeraldTertiary
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("'${activeTool.title}' 전용 지시문 사용", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Switch(
                                checked = individualEnabled,
                                onCheckedChange = { viewModel.toggleIndividualDirective(activeTool, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = EmeraldTertiary),
                                modifier = Modifier.testTag("individual_directive_switch")
                            )
                        }

                        OutlinedTextField(
                            value = individualText,
                            onValueChange = { viewModel.updateIndividualDirective(activeTool, it) },
                            label = { Text("${activeTool.title} 개별 지시문") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldTertiary,
                                unfocusedBorderColor = StudioBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("individual_directive_input")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioHeroBanner(
    state: StudioUiState,
    recordCount: Int,
    onAccountClick: () -> Unit,
    onCollabClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF102033), Color(0xFF141923), Color(0xFF1E1A2E))
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudioBorder),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini Studio",
                            tint = CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GEMINI AI 작업실",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "8개 API · 독립 지시문 · AI 상호 소통",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (state.currentUser != null) EmeraldTertiary else StudioBorder),
                    modifier = Modifier
                        .clickable(onClick = onAccountClick)
                        .testTag("hero_account_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.currentUser?.profilePictureUri != null) {
                            AsyncImage(
                                model = state.currentUser.profilePictureUri,
                                contentDescription = "프로필",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google 계정",
                                tint = if (state.currentUser != null) EmeraldTertiary else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = state.currentUser?.displayName ?: "로그인",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusStatChip(
                    label = "AI 상호 소통",
                    value = "${state.collabParticipants.size}개 AI 회의",
                    accentColor = CyanPrimary,
                    onClick = onCollabClick,
                    modifier = Modifier.weight(1f)
                )
                StatusStatChip(
                    label = "AI 엔진",
                    value = "App Check 활성",
                    accentColor = EmeraldTertiary,
                    onClick = onAccountClick,
                    modifier = Modifier.weight(1f)
                )
                StatusStatChip(
                    label = "클라우드 보관함",
                    value = "${recordCount}건",
                    accentColor = VioletAccent,
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusStatChip(
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioSurface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GeminiToolCard(
    tool: GeminiToolType,
    hasCommonDirective: Boolean,
    hasIndividualDirective: Boolean,
    onClick: () -> Unit
) {
    val (icon, accent) = toolVisualMeta(tool)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tool.title,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = String.format("%02d", tool.apiNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = tool.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (hasCommonDirective) {
                        Surface(color = CyanPrimary.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                            Text("공통", style = MaterialTheme.typography.labelSmall, color = CyanPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    if (hasIndividualDirective) {
                        Surface(color = EmeraldTertiary.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                            Text("개별", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.endpointTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "열기",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun toolVisualMeta(tool: GeminiToolType): Pair<ImageVector, Color> = when (tool) {
    GeminiToolType.MULTI_TURN_CHAT -> Icons.Default.ChatBubbleOutline to CyanPrimary
    GeminiToolType.STREAMING_WRITER -> Icons.Default.Bolt to AmberSecondary
    GeminiToolType.VISION_ANALYZER -> Icons.Default.ImageSearch to EmeraldTertiary
    GeminiToolType.STRUCTURED_JSON -> Icons.Default.DataObject to VioletAccent
    GeminiToolType.SEARCH_GROUNDING -> Icons.Default.TravelExplore to CyanPrimary
    GeminiToolType.CODE_EXECUTION -> Icons.Default.Code to EmeraldTertiary
    GeminiToolType.THINKING_REASONER -> Icons.Default.Psychology to RoseAccent
    GeminiToolType.FUNCTION_TOKEN_LAB -> Icons.Default.Hub to AmberSecondary
}

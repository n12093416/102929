package com.example.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleUserProfile
import com.example.data.remote.ChatTurn
import com.example.data.remote.CodeExecutionBlock
import com.example.data.remote.FirebaseAiService
import com.example.data.remote.GeminiApiResponse
import com.example.data.remote.GeminiToolType
import com.example.data.remote.GroundingSource
import com.example.data.repository.FirestoreWorkspaceRepository
import com.example.data.repository.WorkspaceRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab(val label: String) {
    STUDIO("작업실"),
    COLLAB("AI 소통"),
    HISTORY("보관함"),
    ACCOUNT("계정")
}

sealed interface ToolExecutionState {
    data object Idle : ToolExecutionState
    data object Loading : ToolExecutionState
    data class Streaming(val partialText: String) : ToolExecutionState
    data class Success(val response: GeminiApiResponse) : ToolExecutionState
    data class Error(val message: String) : ToolExecutionState
}

data class CollabMessage(
    val id: Long,
    val speakerTool: GeminiToolType?, // null means human moderator
    val speakerLabel: String,
    val content: String,
    val thoughts: String = "",
    val groundingSources: List<GroundingSource> = emptyList(),
    val codeBlocks: List<CodeExecutionBlock> = emptyList(),
    val appliedCommon: Boolean = false,
    val appliedIndividual: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class StudioUiState(
    val selectedTab: MainTab = MainTab.STUDIO,
    val activeTool: GeminiToolType? = null,
    val currentUser: GoogleUserProfile,
    val isAuthLoading: Boolean = false,
    val authFeedback: String? = null,

    // Independent Directives: Common (Global) vs Per-AI Individual
    val commonDirective: String = "핵심을 명확하고 구조적으로 정리하여 한국어로 답변하세요.",
    val useCommonDirective: Boolean = true,
    val individualDirectives: Map<GeminiToolType, String> = defaultIndividualDirectives(),
    val individualEnabledMap: Map<GeminiToolType, Boolean> = GeminiToolType.entries.associateWith { true },
    val selectedDirectiveEditorTool: GeminiToolType = GeminiToolType.MULTI_TURN_CHAT,
    val isDirectiveSyncing: Boolean = false,

    // Shared Tool Prompt & Execution State
    val promptInput: String = "",
    val secondaryInput: String = "",
    val temperature: Float = 0.7f,
    val thinkingBudget: Int = 4096,
    val jsonSchemaMode: String = "ACTION_PLAN",
    val selectedImageUri: Uri? = null,
    val chatHistory: List<ChatTurn> = emptyList(),
    val executionState: ToolExecutionState = ToolExecutionState.Idle,
    val liveTokenCount: Int? = null,
    val isCountingTokens: Boolean = false,
    val statusBannerMessage: String? = null,
    val historyFilterToolId: String? = null,

    // Inter-AI Communication (AI끼리 서로 소통) State
    val collabTopic: String = "모바일 AI 생산성 앱을 4주 안에 출시하기 위한 기술 검증과 실행 전략을 서로 토론해줘.",
    val collabParticipants: List<GeminiToolType> = listOf(
        GeminiToolType.SEARCH_GROUNDING,
        GeminiToolType.THINKING_REASONER,
        GeminiToolType.CODE_EXECUTION,
        GeminiToolType.STRUCTURED_JSON
    ),
    val collabRounds: Int = 1,
    val collabMessages: List<CollabMessage> = emptyList(),
    val collabRunningSpeaker: GeminiToolType? = null,
    val collabModeratorInput: String = "",
    val collabError: String? = null,
    val collabStatusBanner: String? = null
)

private fun defaultIndividualDirectives(): Map<GeminiToolType, String> = mapOf(
    GeminiToolType.MULTI_TURN_CHAT to "친근하고 명쾌한 시니어 엔지니어처럼 대화하며 다음 질문을 제안하세요.",
    GeminiToolType.STREAMING_WRITER to "감각적이고 흡입력 있는 카피라이팅 톤으로 즉시 읽히는 문장을 작성하세요.",
    GeminiToolType.VISION_ANALYZER to "시각적 디테일, 색감, 구성 요소와 텍스트 단서를 빠짐없이 묘사하세요.",
    GeminiToolType.STRUCTURED_JSON to "누락된 필드 없이 스키마 규격에 완벽히 부합하는 간결한 데이터만 추출하세요.",
    GeminiToolType.SEARCH_GROUNDING to "최신 웹 검색 근거를 우선 반영하고 사실과 출처 중심으로 설명하세요.",
    GeminiToolType.CODE_EXECUTION to "반드시 실행 가능한 Python 코드로 직접 계산해 수치를 검증한 뒤 설명하세요.",
    GeminiToolType.THINKING_REASONER to "논리적 비약 없이 단계별 가정과 반례를 검토한 후 결론을 도출하세요.",
    GeminiToolType.FUNCTION_TOKEN_LAB to "호출된 함수가 반환한 실측 토큰·어휘 데이터를 기반으로 명확히 분석하세요."
)

class StudioViewModel(
    private val repository: FirestoreWorkspaceRepository,
    private val aiService: FirebaseAiService,
    private val authManager: GoogleAuthManager,
    private val currentUserId: String,
    currentUserProfile: GoogleUserProfile,
    private val decodeBitmap: suspend (Uri) -> Bitmap
) : ViewModel() {

    companion object {
        private const val TAG = "StudioViewModel"

        fun factory(
            repository: FirestoreWorkspaceRepository,
            aiService: FirebaseAiService,
            authManager: GoogleAuthManager,
            currentUserId: String,
            currentUserProfile: GoogleUserProfile,
            decodeBitmap: suspend (Uri) -> Bitmap
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudioViewModel(
                    repository = repository,
                    aiService = aiService,
                    authManager = authManager,
                    currentUserId = currentUserId,
                    currentUserProfile = currentUserProfile,
                    decodeBitmap = decodeBitmap
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(
        StudioUiState(currentUser = currentUserProfile)
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val savedRecords: StateFlow<List<WorkspaceRecord>> = repository
        .observeRecords(currentUserId)
        .catch { e ->
            Log.e(TAG, "Failed to observe Firestore workspaceRecords", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var activeJob: Job? = null
    private var collabJob: Job? = null
    private var directiveSaveJob: Job? = null
    private var hasPendingLocalDirectiveEdit = false
    private var collabMsgIdCounter = 1L

    init {
        observeRemoteDirectives()
    }

    private fun observeRemoteDirectives() {
        viewModelScope.launch {
            repository.observeDirectiveConfig(currentUserId)
                .catch { e ->
                    Log.e(TAG, "Failed to observe Firestore directiveConfig", e)
                }
                .collect { config ->
                    if (config == null || hasPendingLocalDirectiveEdit) return@collect
                    val mergedIndividual = defaultIndividualDirectives().toMutableMap()
                    GeminiToolType.entries.forEach { tool ->
                        config.individualDirectives[tool.name]?.let { value ->
                            mergedIndividual[tool] = value
                        }
                    }
                    val mergedEnabled = GeminiToolType.entries.associateWith { true }.toMutableMap()
                    GeminiToolType.entries.forEach { tool ->
                        config.individualEnabledMap[tool.name]?.let { enabled ->
                            mergedEnabled[tool] = enabled
                        }
                    }
                    _uiState.update {
                        it.copy(
                            commonDirective = config.commonDirective,
                            useCommonDirective = config.useCommonDirective,
                            individualDirectives = mergedIndividual,
                            individualEnabledMap = mergedEnabled
                        )
                    }
                }
        }
    }

    private fun scheduleDirectivePersist(state: StudioUiState, immediate: Boolean) {
        hasPendingLocalDirectiveEdit = true
        directiveSaveJob?.cancel()
        directiveSaveJob = viewModelScope.launch {
            if (!immediate) delay(500L)
            _uiState.update { it.copy(isDirectiveSyncing = true) }
            val result = repository.saveDirectiveConfig(
                userId = currentUserId,
                commonDirective = state.commonDirective,
                useCommonDirective = state.useCommonDirective,
                individualDirectives = state.individualDirectives.mapKeys { it.key.name },
                individualEnabledMap = state.individualEnabledMap.mapKeys { it.key.name }
            )
            hasPendingLocalDirectiveEdit = false
            _uiState.update { it.copy(isDirectiveSyncing = false) }
            result.onFailure { err ->
                Log.e(TAG, "Failed to save directive config", err)
                _uiState.update {
                    it.copy(statusBannerMessage = "지시문 클라우드 저장 실패: ${err.localizedMessage ?: "오류"}")
                }
            }
        }
    }

    // ==================== INDEPENDENT DIRECTIVES CONTROL ====================

    fun updateCommonDirective(text: String) {
        _uiState.update { state ->
            state.copy(commonDirective = text).also { scheduleDirectivePersist(it, immediate = false) }
        }
    }

    fun toggleCommonDirective(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(useCommonDirective = enabled).also { scheduleDirectivePersist(it, immediate = true) }
        }
    }

    fun selectDirectiveEditorTool(tool: GeminiToolType) {
        _uiState.update { it.copy(selectedDirectiveEditorTool = tool) }
    }

    fun updateIndividualDirective(tool: GeminiToolType, text: String) {
        _uiState.update { state ->
            val updatedMap = state.individualDirectives.toMutableMap().apply { put(tool, text) }
            state.copy(individualDirectives = updatedMap).also { scheduleDirectivePersist(it, immediate = false) }
        }
    }

    fun toggleIndividualDirective(tool: GeminiToolType, enabled: Boolean) {
        _uiState.update { state ->
            val updatedMap = state.individualEnabledMap.toMutableMap().apply { put(tool, enabled) }
            state.copy(individualEnabledMap = updatedMap).also { scheduleDirectivePersist(it, immediate = true) }
        }
    }

    fun buildEffectiveSystemInstruction(
        tool: GeminiToolType,
        extraInstruction: String = ""
    ): String {
        val state = _uiState.value
        val common = if (state.useCommonDirective) state.commonDirective else ""
        val individual = if (state.individualEnabledMap[tool] == true) {
            state.individualDirectives[tool].orEmpty()
        } else ""
        return aiService.composeSystemInstruction(
            commonDirective = common,
            individualDirective = individual,
            extraInstruction = extraInstruction
        )
    }

    // ==================== NAVIGATION & INPUTS ====================

    fun selectTab(tab: MainTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                activeTool = null,
                statusBannerMessage = null
            )
        }
    }

    fun openTool(tool: GeminiToolType) {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                activeTool = tool,
                promptInput = tool.samplePrompt,
                secondaryInput = if (tool == GeminiToolType.FUNCTION_TOKEN_LAB) {
                    "모바일 AI 도구는 개발자가 더 빠르게 앱을 만들 수 있도록 도와줍니다."
                } else "",
                executionState = ToolExecutionState.Idle,
                liveTokenCount = null,
                statusBannerMessage = null
            )
        }
    }

    fun closeTool() {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                activeTool = null,
                executionState = ToolExecutionState.Idle,
                statusBannerMessage = null
            )
        }
    }

    fun updatePromptInput(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun updateSecondaryInput(text: String) {
        _uiState.update { it.copy(secondaryInput = text) }
    }

    fun updateTemperature(value: Float) {
        _uiState.update { it.copy(temperature = value) }
    }

    fun updateThinkingBudget(budget: Int) {
        _uiState.update { it.copy(thinkingBudget = budget) }
    }

    fun updateJsonSchemaMode(mode: String) {
        _uiState.update { it.copy(jsonSchemaMode = mode) }
    }

    fun selectVisionImage(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun clearChatHistory() {
        _uiState.update {
            it.copy(
                chatHistory = emptyList(),
                executionState = ToolExecutionState.Idle
            )
        }
    }

    fun setHistoryFilter(toolId: String?) {
        _uiState.update { it.copy(historyFilterToolId = toolId) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true) }
            authManager.signOut()
            _uiState.update { it.copy(isAuthLoading = false) }
        }
    }

    // ==================== 8 GEMINI AI LOGIC EXECUTION ====================

    fun checkPromptTokens() {
        val text = _uiState.value.promptInput
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCountingTokens = true) }
            try {
                val count = aiService.countTokens(text)
                _uiState.update { it.copy(liveTokenCount = count, isCountingTokens = false) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Token count timed out", e)
                _uiState.update {
                    it.copy(
                        isCountingTokens = false,
                        statusBannerMessage = "토큰 수 계산 시간이 초과되었습니다."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Token count failed", e)
                _uiState.update {
                    it.copy(
                        isCountingTokens = false,
                        statusBannerMessage = e.localizedMessage ?: "토큰 계산 실패"
                    )
                }
            }
        }
    }

    fun executeActiveTool() {
        val tool = _uiState.value.activeTool ?: return
        val prompt = _uiState.value.promptInput.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(executionState = ToolExecutionState.Error("프롬프트를 입력해주세요.")) }
            return
        }

        val combinedDirective = buildEffectiveSystemInstruction(tool)

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            when (tool) {
                GeminiToolType.STREAMING_WRITER -> runStreamingWriter(prompt, combinedDirective)
                GeminiToolType.MULTI_TURN_CHAT -> runMultiTurnChat(prompt, combinedDirective)
                GeminiToolType.VISION_ANALYZER -> runVisionAnalysis(prompt, combinedDirective)
                GeminiToolType.STRUCTURED_JSON -> runStructuredJson(prompt, combinedDirective)
                GeminiToolType.SEARCH_GROUNDING -> runSearchGrounding(prompt, combinedDirective)
                GeminiToolType.CODE_EXECUTION -> runCodeExecution(prompt, combinedDirective)
                GeminiToolType.THINKING_REASONER -> runThinkingReasoner(prompt, combinedDirective)
                GeminiToolType.FUNCTION_TOKEN_LAB -> runFunctionTokenLab(prompt, combinedDirective)
            }
        }
    }

    private suspend fun runMultiTurnChat(prompt: String, systemInstruction: String) {
        val updatedHistory = _uiState.value.chatHistory + ChatTurn("user", prompt)
        _uiState.update {
            it.copy(
                chatHistory = updatedHistory,
                promptInput = "",
                executionState = ToolExecutionState.Loading
            )
        }
        try {
            val response = aiService.generateMultiTurnChat(
                systemPrompt = systemInstruction,
                history = updatedHistory,
                temperature = _uiState.value.temperature
            )
            val modelTurn = ChatTurn("model", response.text)
            _uiState.update {
                it.copy(
                    chatHistory = updatedHistory + modelTurn,
                    executionState = ToolExecutionState.Success(response)
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Multi-turn chat timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("요청 시간이 초과되었습니다(60초). 다시 시도해주세요."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multi-turn chat failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "채팅 요청 실패"))
            }
        }
    }

    private suspend fun runStreamingWriter(prompt: String, systemInstruction: String) {
        val accumulated = StringBuilder()
        _uiState.update { it.copy(executionState = ToolExecutionState.Streaming("")) }
        aiService.streamGenerateText(prompt, _uiState.value.temperature, systemInstruction)
            .catch { e ->
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                Log.e(TAG, "Streaming failed", e)
                val msg = if (e is TimeoutCancellationException) {
                    "스트리밍 응답 시간이 초과되었습니다(60초)."
                } else {
                    e.localizedMessage ?: "스트리밍 오류"
                }
                _uiState.update { it.copy(executionState = ToolExecutionState.Error(msg)) }
            }
            .onCompletion { cause ->
                if (cause == null && accumulated.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            executionState = ToolExecutionState.Success(
                                GeminiApiResponse(text = accumulated.toString())
                            )
                        )
                    }
                }
            }
            .collect { chunk ->
                accumulated.append(chunk)
                _uiState.update {
                    it.copy(executionState = ToolExecutionState.Streaming(accumulated.toString()))
                }
            }
    }

    private suspend fun runVisionAnalysis(prompt: String, systemInstruction: String) {
        val imageUri = _uiState.value.selectedImageUri
        if (imageUri == null) {
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("먼저 이미지 선택 버튼을 눌러 사진을 첨부해주세요."))
            }
            return
        }
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val bitmap = decodeBitmap(imageUri)
            val response = aiService.analyzeVisionImage(bitmap, prompt, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision analysis timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("이미지 분석 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "비전 분석 실패"))
            }
        }
    }

    private suspend fun runStructuredJson(prompt: String, systemInstruction: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val response = aiService.generateStructuredJson(prompt, _uiState.value.jsonSchemaMode, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured JSON timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("JSON 추출 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured JSON failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "JSON 추출 실패"))
            }
        }
    }

    private suspend fun runSearchGrounding(prompt: String, systemInstruction: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val response = aiService.searchWithGrounding(prompt, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Search grounding timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("웹 검색 그라운딩 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search grounding failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "웹 검색 그라운딩 실패"))
            }
        }
    }

    private suspend fun runCodeExecution(prompt: String, systemInstruction: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val response = aiService.executeCodeWithGemini(prompt, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Code execution timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("코드 실행 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Code execution failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "코드 실행 API 오류"))
            }
        }
    }

    private suspend fun runThinkingReasoner(prompt: String, systemInstruction: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val response = aiService.thinkAndReason(prompt, _uiState.value.thinkingBudget, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Thinking timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("심층 추론 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Thinking failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "심층 추론 API 오류"))
            }
        }
    }

    private suspend fun runFunctionTokenLab(textA: String, systemInstruction: String) {
        val textB = _uiState.value.secondaryInput.trim()
        if (textB.isBlank()) {
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("비교할 두 번째 문장(텍스트 B)도 입력해주세요."))
            }
            return
        }
        _uiState.update { it.copy(executionState = ToolExecutionState.Loading) }
        try {
            val response = aiService.analyzeWithFunctionCallingAndTokens(textA, textB, systemInstruction)
            _uiState.update { it.copy(executionState = ToolExecutionState.Success(response)) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Function calling timed out", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error("함수 호출 & 토큰 분석 시간이 초과되었습니다(60초)."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Function calling failed", e)
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Error(e.localizedMessage ?: "함수 호출 API 오류"))
            }
        }
    }

    // ==================== MULTI-AI COLLABORATION (AI끼리 서로 소통) ====================

    fun updateCollabTopic(topic: String) {
        _uiState.update { it.copy(collabTopic = topic) }
    }

    fun updateCollabRounds(rounds: Int) {
        _uiState.update { it.copy(collabRounds = rounds.coerceIn(1, 3)) }
    }

    fun updateCollabModeratorInput(text: String) {
        _uiState.update { it.copy(collabModeratorInput = text) }
    }

    fun toggleCollabParticipant(tool: GeminiToolType) {
        if (tool == GeminiToolType.VISION_ANALYZER || tool == GeminiToolType.FUNCTION_TOKEN_LAB) return
        _uiState.update { state ->
            val current = state.collabParticipants
            val updated = if (current.contains(tool)) {
                if (current.size > 2) current - tool else current
            } else {
                current + tool
            }
            state.copy(collabParticipants = updated)
        }
    }

    fun startCollabDiscussion() {
        val topic = _uiState.value.collabTopic.trim()
        if (topic.isBlank()) {
            _uiState.update { it.copy(collabError = "AI들이 토론할 주제를 입력해주세요.") }
            return
        }
        val participants = _uiState.value.collabParticipants
        if (participants.size < 2) {
            _uiState.update { it.copy(collabError = "최소 2개 이상의 AI를 선택해주세요.") }
            return
        }

        collabJob?.cancel()
        collabJob = viewModelScope.launch {
            val initialMsg = CollabMessage(
                id = collabMsgIdCounter++,
                speakerTool = null,
                speakerLabel = "사용자 (회의 안건)",
                content = topic
            )
            _uiState.update {
                it.copy(
                    collabMessages = listOf(initialMsg),
                    collabError = null,
                    collabStatusBanner = null
                )
            }

            val rounds = _uiState.value.collabRounds
            for (round in 1..rounds) {
                for (agent in participants) {
                    val ok = invokeSingleCollabAgent(agent, round, rounds)
                    if (!ok) {
                        _uiState.update { it.copy(collabRunningSpeaker = null) }
                        return@launch
                    }
                }
            }
            _uiState.update { it.copy(collabRunningSpeaker = null) }
        }
    }

    fun sendModeratorInterjectionAndTriggerNext(targetAgent: GeminiToolType? = null) {
        val text = _uiState.value.collabModeratorInput.trim()
        if (text.isNotBlank()) {
            val modMsg = CollabMessage(
                id = collabMsgIdCounter++,
                speakerTool = null,
                speakerLabel = "사용자 (개입 질문)",
                content = text
            )
            _uiState.update {
                it.copy(
                    collabMessages = it.collabMessages + modMsg,
                    collabModeratorInput = "",
                    collabError = null
                )
            }
        }
        if (targetAgent != null && _uiState.value.collabRunningSpeaker == null) {
            collabJob?.cancel()
            collabJob = viewModelScope.launch {
                invokeSingleCollabAgent(targetAgent, round = 1, totalRounds = 1)
                _uiState.update { it.copy(collabRunningSpeaker = null) }
            }
        }
    }

    fun triggerSingleCollabAgentTurn(agent: GeminiToolType) {
        if (_uiState.value.collabRunningSpeaker != null) return
        collabJob?.cancel()
        collabJob = viewModelScope.launch {
            invokeSingleCollabAgent(agent, round = 1, totalRounds = 1)
            _uiState.update { it.copy(collabRunningSpeaker = null) }
        }
    }

    fun stopCollabDiscussion() {
        collabJob?.cancel()
        _uiState.update { it.copy(collabRunningSpeaker = null) }
    }

    private suspend fun invokeSingleCollabAgent(
        agent: GeminiToolType,
        round: Int,
        totalRounds: Int
    ): Boolean {
        _uiState.update { it.copy(collabRunningSpeaker = agent, collabError = null) }
        val state = _uiState.value
        val transcript = buildString {
            appendLine("=== 현재까지의 AI 협업 대화 내역 ===")
            state.collabMessages.forEach { msg ->
                appendLine("[${msg.speakerLabel}]:")
                appendLine(msg.content)
                appendLine()
            }
        }

        val roleInstruction = """
            당신은 다중 AI 작업실 회의에 참여한 '${agent.apiNumber}번 ${agent.title} (${agent.badgeLabel})' 입니다.
            현재 라운드: $round / $totalRounds.
            위에 있는 다른 AI들의 이전 발언을 주의 깊게 읽고, 동의·보완·반박·검증·구체화를 진행하세요.
            앞선 AI와 똑같은 말을 반복하지 말고, 당신의 전문 API 역할(${agent.subtitle})에 맞는 새로운 기여를 4~8문장 내외로 명확히 제시하세요.
        """.trimIndent()

        val systemInstruction = buildEffectiveSystemInstruction(agent, roleInstruction)
        val promptForAgent = "$transcript\n이제 '${agent.title}'의 순서입니다. 앞선 AI들의 발언을 인용하거나 반론하며 이어서 답변해주세요."

        return try {
            val response = when (agent) {
                GeminiToolType.SEARCH_GROUNDING -> aiService.searchWithGrounding(promptForAgent, systemInstruction)
                GeminiToolType.CODE_EXECUTION -> aiService.executeCodeWithGemini(promptForAgent, systemInstruction)
                GeminiToolType.THINKING_REASONER -> aiService.thinkAndReason(promptForAgent, state.thinkingBudget, systemInstruction)
                GeminiToolType.STRUCTURED_JSON -> aiService.generateStructuredJson(promptForAgent, state.jsonSchemaMode, systemInstruction)
                else -> aiService.generateMultiTurnChat(
                    systemPrompt = systemInstruction,
                    history = listOf(ChatTurn("user", promptForAgent)),
                    temperature = state.temperature
                )
            }

            val displayText = response.jsonFormatted.ifBlank { response.text }.ifBlank {
                if (response.codeBlocks.isNotEmpty()) "코드 실행 검증을 완료했습니다." else "(응답 없음)"
            }

            val message = CollabMessage(
                id = collabMsgIdCounter++,
                speakerTool = agent,
                speakerLabel = "${String.format("%02d", agent.apiNumber)} ${agent.title}",
                content = displayText,
                thoughts = response.thoughts,
                groundingSources = response.groundingSources,
                codeBlocks = response.codeBlocks,
                appliedCommon = state.useCommonDirective && state.commonDirective.isNotBlank(),
                appliedIndividual = state.individualEnabledMap[agent] == true && !state.individualDirectives[agent].isNullOrBlank()
            )
            _uiState.update { it.copy(collabMessages = it.collabMessages + message) }
            true
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Collab agent timed out: ${agent.name}", e)
            _uiState.update {
                it.copy(collabError = "${agent.title} 응답 시간이 초과되었습니다(60초).")
            }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Collab agent failed: ${agent.name}", e)
            _uiState.update {
                it.copy(collabError = "${agent.title} 응답 오류: ${e.localizedMessage ?: "요청 실패"}")
            }
            false
        }
    }

    fun saveCollabTranscriptToHistory() {
        val messages = _uiState.value.collabMessages
        if (messages.isEmpty()) return
        val transcriptText = messages.joinToString("\n\n") { msg ->
            buildString {
                appendLine("▶ ${msg.speakerLabel}")
                appendLine(msg.content)
                if (msg.codeBlocks.isNotEmpty()) {
                    msg.codeBlocks.forEach { cb ->
                        appendLine("[${cb.language}] ${cb.code}")
                        appendLine("출력: ${cb.output}")
                    }
                }
            }.trimEnd()
        }

        viewModelScope.launch {
            val result = repository.saveRecord(
                userId = currentUserId,
                toolId = "AI_COLLAB_SESSION",
                toolTitle = "AI 상호 소통 회의",
                modelEndpoint = "Firebase.ai Multi-Agent (${_uiState.value.collabParticipants.size} AIs)",
                prompt = _uiState.value.collabTopic,
                responseText = transcriptText,
                metadataSummary = "${messages.size}개 메시지"
            )
            _uiState.update {
                it.copy(
                    collabStatusBanner = if (result.isSuccess) {
                        "AI 협업 회의록이 Cloud Firestore 보관함에 저장되었습니다."
                    } else {
                        "저장 실패: ${result.exceptionOrNull()?.localizedMessage ?: "권한 또는 네트워크 오류"}"
                    }
                )
            }
        }
    }

    // ==================== CLOUD FIRESTORE WORKSPACE PERSISTENCE ====================

    fun saveCurrentResultToHistory() {
        val tool = _uiState.value.activeTool ?: return
        val state = _uiState.value.executionState

        val (responseText, metadata) = when (state) {
            is ToolExecutionState.Success -> {
                val r = state.response
                val body = when {
                    r.jsonFormatted.isNotBlank() -> r.jsonFormatted
                    r.codeBlocks.isNotEmpty() -> buildString {
                        if (r.text.isNotBlank()) appendLine(r.text).appendLine()
                        r.codeBlocks.forEach { block ->
                            appendLine("[${block.language}]")
                            appendLine(block.code)
                            appendLine("출력: ${block.output}")
                        }
                    }
                    else -> r.text
                }
                val meta = if (r.totalTokenCount > 0) "${r.totalTokenCount} tokens" else tool.badgeLabel
                body to meta
            }
            is ToolExecutionState.Streaming -> state.partialText to tool.badgeLabel
            else -> return
        }

        val promptToSave = if (tool == GeminiToolType.MULTI_TURN_CHAT) {
            _uiState.value.chatHistory.lastOrNull { it.role == "user" }?.text ?: "멀티턴 대화"
        } else {
            _uiState.value.promptInput.trim()
        }

        viewModelScope.launch {
            val result = repository.saveRecord(
                userId = currentUserId,
                toolId = tool.name,
                toolTitle = tool.title,
                modelEndpoint = tool.endpointTag,
                prompt = promptToSave,
                responseText = responseText,
                metadataSummary = metadata
            )
            _uiState.update {
                it.copy(
                    statusBannerMessage = if (result.isSuccess) {
                        "작업 결과가 Cloud Firestore 보관함에 저장되었습니다."
                    } else {
                        "저장 실패: ${result.exceptionOrNull()?.localizedMessage ?: "오류"}"
                    }
                )
            }
        }
    }

    fun deleteHistoryRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteRecord(currentUserId, recordId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllRecords(currentUserId)
        }
    }
}

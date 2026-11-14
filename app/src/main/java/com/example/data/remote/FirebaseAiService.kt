package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.CodeExecutionResultPart
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ExecutableCodePart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

class FirebaseAiService {

    companion object {
        private const val TAG = "FirebaseAiService"
        const val MODEL_FLASH = "gemini-3.8-flash"
        const val MODEL_FLASH_LITE = "gemini-3.5-flash-lite"
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    private val defaultTokenCounterModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH
        )
    }

    private fun buildSystemContent(systemInstruction: String): Content? {
        val clean = systemInstruction.trim()
        if (clean.isEmpty()) return null
        return content { text(clean) }
    }

    fun composeSystemInstruction(
        commonDirective: String,
        individualDirective: String,
        extraInstruction: String = ""
    ): String {
        val sections = mutableListOf<String>()
        if (commonDirective.isNotBlank()) {
            sections += "[공통 지시문 - 모든 AI에게 일괄 적용]\n${commonDirective.trim()}"
        }
        if (individualDirective.isNotBlank()) {
            sections += "[개별 지시문 - 현재 AI에게만 독립 적용]\n${individualDirective.trim()}"
        }
        if (extraInstruction.isNotBlank()) {
            sections += "[상황 지시문]\n${extraInstruction.trim()}"
        }
        return sections.joinToString("\n\n")
    }

    fun computeJaccardSimilarity(textA: String, textB: String): Pair<Double, List<String>> {
        val tokenize = { text: String ->
            text.lowercase()
                .split(Regex("[\\s,.!?;:\"'()\\[\\]{}]+"))
                .filter { it.length >= 2 }
                .toSet()
        }
        val wordsA = tokenize(textA)
        val wordsB = tokenize(textB)
        if (wordsA.isEmpty() && wordsB.isEmpty()) return 0.0 to emptyList()
        val intersection = wordsA.intersect(wordsB).sorted()
        val union = wordsA.union(wordsB)
        val score = if (union.isEmpty()) 0.0 else intersection.size.toDouble() / union.size.toDouble()
        return score to intersection
    }

    // API #1: Multi-Turn Conversational Chat
    suspend fun generateMultiTurnChat(
        systemPrompt: String,
        history: List<ChatTurn>,
        temperature: Float
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        require(history.isNotEmpty()) { "대화 내역이 비어 있습니다." }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                this.temperature = temperature
                maxOutputTokens = 2048
            },
            systemInstruction = buildSystemContent(systemPrompt)
        )

        val priorTurns = history.dropLast(1).map { turn ->
            content(role = turn.role) { text(turn.text) }
        }
        val latestUserText = history.last().text
        val chat = model.startChat(history = priorTurns)

        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            chat.sendMessage(latestUserText)
        }
        parseGenerateContentResponse(response)
    }

    // API #2: Real-Time Streaming Generation
    fun streamGenerateText(
        prompt: String,
        temperature: Float,
        systemPrompt: String
    ): Flow<String> = flow {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                this.temperature = temperature
                maxOutputTokens = 2048
            },
            systemInstruction = buildSystemContent(systemPrompt)
        )
        withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.e(TAG, "Streaming generation failed", e)
        throw e
    }.flowOn(Dispatchers.IO)

    // API #3: Multimodal Vision Analysis
    suspend fun analyzeVisionImage(
        bitmap: Bitmap,
        prompt: String,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 2048
            },
            systemInstruction = buildSystemContent(systemPrompt)
        )
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContent(inputContent)
        }
        parseGenerateContentResponse(response)
    }

    // API #4: Structured Output (Schema.obj + responseMimeType = application/json)
    suspend fun generateStructuredJson(
        prompt: String,
        schemaMode: String,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val schema = if (schemaMode == "ACTION_PLAN") {
            Schema.obj(
                properties = mapOf(
                    "projectTitle" to Schema.string("프로젝트 또는 주제의 제목"),
                    "summary" to Schema.string("핵심 요약 1~2문장"),
                    "priority" to Schema.enumeration(
                        values = listOf("HIGH", "MEDIUM", "LOW"),
                        description = "우선순위"
                    ),
                    "tasks" to Schema.array(
                        items = Schema.obj(
                            properties = mapOf(
                                "step" to Schema.string("작업 내용"),
                                "owner" to Schema.string("담당자 또는 역할"),
                                "deadline" to Schema.string("마감 기한")
                            )
                        ),
                        description = "실행 작업 목록"
                    )
                )
            )
        } else {
            Schema.obj(
                properties = mapOf(
                    "headline" to Schema.string("핵심 헤드라인"),
                    "sentiment" to Schema.enumeration(
                        values = listOf("POSITIVE", "NEUTRAL", "NEGATIVE"),
                        description = "전반적 어조"
                    ),
                    "keyPoints" to Schema.array(
                        items = Schema.string(),
                        description = "핵심 인사이트 목록"
                    ),
                    "confidenceScore" to Schema.double("신뢰도 점수 (0.0~1.0)")
                )
            )
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = schema
            },
            systemInstruction = buildSystemContent(systemPrompt)
        )

        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        val rawJson = response.text.orEmpty()
        val prettyJson = try {
            JSONObject(rawJson).toString(2)
        } catch (_: Exception) {
            rawJson
        }
        parseGenerateContentResponse(response).copy(jsonFormatted = prettyJson)
    }

    // API #5: Grounding with Google Search
    suspend fun searchWithGrounding(
        prompt: String,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = buildSystemContent(systemPrompt)
        )
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        parseGenerateContentResponse(response)
    }

    // API #6: Python Code Execution Sandbox
    suspend fun executeCodeWithGemini(
        prompt: String,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            tools = listOf(Tool.codeExecution()),
            systemInstruction = buildSystemContent(systemPrompt)
        )
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        parseGenerateContentResponse(response)
    }

    // API #7: Deep Thinking Reasoner
    suspend fun thinkAndReason(
        prompt: String,
        thinkingBudget: Int,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                temperature = 0.5f
                thinkingConfig = thinkingConfig {
                    this.thinkingBudget = thinkingBudget
                    includeThoughts = true
                }
            },
            systemInstruction = buildSystemContent(systemPrompt)
        )
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        parseGenerateContentResponse(response)
    }

    // API #8: Flash-Lite Function Calling + CountTokens Analysis
    suspend fun analyzeWithFunctionCallingAndTokens(
        textA: String,
        textB: String,
        systemPrompt: String
    ): GeminiApiResponse = withContext(Dispatchers.IO) {
        val textATokens = withTimeout(30_000L) {
            defaultTokenCounterModel.countTokens(textA).totalTokens
        }
        val textBTokens = withTimeout(30_000L) {
            defaultTokenCounterModel.countTokens(textB).totalTokens
        }

        val computeTextMetricsDeclaration = FunctionDeclaration(
            name = "computeTextMetrics",
            description = "두 문장의 길이, 어휘 겹침(Jaccard 유사도), 공통 핵심 단어를 정밀 계산합니다.",
            parameters = mapOf(
                "textA" to Schema.string("첫 번째 기준 문장"),
                "textB" to Schema.string("두 번째 비교 문장")
            )
        )

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH_LITE,
            tools = listOf(Tool.functionDeclarations(listOf(computeTextMetricsDeclaration))),
            systemInstruction = buildSystemContent(systemPrompt)
        )

        val chat = model.startChat()
        val initialPrompt = """
            아래 두 문장의 구조적·어휘적 유사도와 차이점을 분석해줘.
            반드시 `computeTextMetrics` 함수를 먼저 호출해서 실측 데이터를 확인한 후 설명해.
            - 텍스트 A ($textATokens tokens): $textA
            - 텍스트 B ($textBTokens tokens): $textB
        """.trimIndent()

        val firstResponse = withTimeout(REQUEST_TIMEOUT_MS) {
            chat.sendMessage(initialPrompt)
        }

        val functionCall = firstResponse.functionCalls.firstOrNull { it.name == "computeTextMetrics" }
        val (jaccardScore, sharedWords) = if (functionCall != null) {
            val argA = functionCall.args["textA"]?.jsonPrimitive?.content ?: textA
            val argB = functionCall.args["textB"]?.jsonPrimitive?.content ?: textB
            computeJaccardSimilarity(argA, argB)
        } else {
            computeJaccardSimilarity(textA, textB)
        }

        val finalResponse = if (functionCall != null) {
            val functionResultJson = JsonObject(
                mapOf(
                    "jaccardSimilarity" to JsonPrimitive(jaccardScore),
                    "sharedKeywords" to JsonArray(sharedWords.map { JsonPrimitive(it) }),
                    "textATokenCount" to JsonPrimitive(textATokens),
                    "textBTokenCount" to JsonPrimitive(textBTokens),
                    "textALength" to JsonPrimitive(textA.length),
                    "textBLength" to JsonPrimitive(textB.length)
                )
            )
            withTimeout(REQUEST_TIMEOUT_MS) {
                chat.sendMessage(
                    content("user") {
                        part(FunctionResponsePart("computeTextMetrics", functionResultJson))
                    }
                )
            }
        } else {
            firstResponse
        }

        parseGenerateContentResponse(finalResponse).copy(
            functionAnalysisResult = FunctionTokenAnalysisResult(
                jaccardSimilarity = jaccardScore,
                sharedKeywordsCount = sharedWords.size,
                textATokens = textATokens,
                textBTokens = textBTokens,
                functionCalled = functionCall?.name ?: "direct_analysis"
            )
        )
    }

    // Token counter helper
    suspend fun countTokens(text: String): Int = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            defaultTokenCounterModel.countTokens(text).totalTokens
        }
    }

    internal fun parseGenerateContentResponse(response: GenerateContentResponse): GeminiApiResponse {
        val textParts = StringBuilder()
        val codeBlocks = mutableListOf<CodeExecutionBlock>()
        var pendingLang = "PYTHON"
        var pendingCode = ""

        val firstCandidate = response.candidates.firstOrNull()
        firstCandidate?.content?.parts?.forEach { part ->
            when (part) {
                is TextPart -> {
                    if (part.text.isNotBlank()) {
                        if (textParts.isNotEmpty()) textParts.append('\n')
                        textParts.append(part.text)
                    }
                }
                is ExecutableCodePart -> {
                    pendingLang = part.language.toString()
                    pendingCode = part.code
                }
                is CodeExecutionResultPart -> {
                    codeBlocks += CodeExecutionBlock(
                        language = pendingLang,
                        code = pendingCode,
                        output = "${part.outcome}: ${part.output}"
                    )
                    pendingCode = ""
                }
            }
        }

        if (pendingCode.isNotBlank()) {
            codeBlocks += CodeExecutionBlock(
                language = pendingLang,
                code = pendingCode,
                output = "(실행 결과 없음)"
            )
        }

        val groundingSources = firstCandidate?.groundingMetadata?.groundingChunks
            ?.mapNotNull { chunk ->
                val web = chunk.web ?: return@mapNotNull null
                val uri = web.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                GroundingSource(title = title, uri = uri)
            }
            ?.distinctBy { it.uri }
            .orEmpty()

        val usage = response.usageMetadata
        val resolvedText = textParts.toString().ifBlank { response.text.orEmpty() }

        return GeminiApiResponse(
            text = resolvedText,
            thoughts = response.thoughtSummary.orEmpty(),
            groundingSources = groundingSources,
            codeBlocks = codeBlocks,
            promptTokenCount = usage?.promptTokenCount ?: 0,
            candidateTokenCount = usage?.candidatesTokenCount ?: 0,
            totalTokenCount = usage?.totalTokenCount ?: 0
        )
    }
}

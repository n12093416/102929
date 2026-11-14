package com.example.data.remote

enum class GeminiToolType(
    val apiNumber: Int,
    val title: String,
    val subtitle: String,
    val endpointTag: String,
    val badgeLabel: String,
    val samplePrompt: String
) {
    MULTI_TURN_CHAT(
        apiNumber = 1,
        title = "멀티턴 AI 채팅",
        subtitle = "System Instruction & startChat 맥락 유지",
        endpointTag = "Firebase.ai · gemini-3.8-flash (startChat)",
        badgeLabel = "CHAT API",
        samplePrompt = "모바일 앱 아키텍처 설계 시 Clean Architecture와 MVVM의 장단점을 핵심만 비교해줘."
    ),
    STREAMING_WRITER(
        apiNumber = 2,
        title = "실시간 스트리밍",
        subtitle = "토큰 단위 실시간 초고속 생성",
        endpointTag = "Firebase.ai · gemini-3.8-flash (generateContentStream)",
        badgeLabel = "STREAM API",
        samplePrompt = "신규 AI 생산성 앱 런칭을 위한 강렬한 앱스토어 소개 문구 3가지를 작성해줘."
    ),
    VISION_ANALYZER(
        apiNumber = 3,
        title = "비전 이미지 분석",
        subtitle = "멀티모달 이미지 + 텍스트 Q&A",
        endpointTag = "Firebase.ai · gemini-3.8-flash (content image)",
        badgeLabel = "VISION API",
        samplePrompt = "이 이미지의 핵심 요소, 색상 분위기, 그리고 텍스트나 객체 정보를 자세히 분석해줘."
    ),
    STRUCTURED_JSON(
        apiNumber = 4,
        title = "구조화 JSON 추출",
        subtitle = "Schema.obj 기반 엄격한 JSON 변환",
        endpointTag = "Firebase.ai · gemini-3.8-flash (responseSchema)",
        badgeLabel = "SCHEMA API",
        samplePrompt = "다음 회의 메모에서 프로젝트명, 핵심 작업 3가지, 우선순위를 JSON으로 추출해줘: 다음 주 금요일까지 AI 작업실 프로토타입 완성. 디자인 점검은 수요일, 성능 테스트는 목요일에 김민수 담당."
    ),
    SEARCH_GROUNDING(
        apiNumber = 5,
        title = "웹 검색 그라운딩",
        subtitle = "실시간 Google Search 연동 및 출처 인용",
        endpointTag = "Firebase.ai · gemini-3.8-flash (Tool.googleSearch)",
        badgeLabel = "GROUNDING API",
        samplePrompt = "최신 안드로이드 Jetpack Compose 및 Kotlin 멀티플랫폼 주요 업데이트 동향을 요약해줘."
    ),
    CODE_EXECUTION(
        apiNumber = 6,
        title = "코드 생성 & 실행",
        subtitle = "Python 샌드박스 자동 연산 및 검증",
        endpointTag = "Firebase.ai · gemini-3.8-flash (Tool.codeExecution)",
        badgeLabel = "SANDBOX API",
        samplePrompt = "Python 코드를 작성해 실행해서 1부터 1000까지의 소수 개수와 그 합계, 그리고 처음 10개의 피보나치 수를 계산해줘."
    ),
    THINKING_REASONER(
        apiNumber = 7,
        title = "심층 사고 추론",
        subtitle = "Thinking Budget 기반 단계별 논리 추론",
        endpointTag = "Firebase.ai · gemini-3.8-flash (thinkingConfig)",
        badgeLabel = "THINKING API",
        samplePrompt = "3리터 물통과 5리터 물통만 가지고 정확히 4리터의 물을 측정하는 최소 단계 절차를 논리적으로 증명해줘."
    ),
    FUNCTION_TOKEN_LAB(
        apiNumber = 8,
        title = "함수 호출 & 토큰 분석",
        subtitle = "Flash-Lite FunctionDeclaration + countTokens",
        endpointTag = "Firebase.ai · gemini-3.5-flash-lite (FunctionCalling)",
        badgeLabel = "FUNCTION API",
        samplePrompt = "인공지능이 모바일 앱 개발자의 생산성을 혁신하고 있습니다."
    )
}

data class ChatTurn(
    val role: String, // "user" or "model"
    val text: String
)

data class GroundingSource(
    val title: String,
    val uri: String
)

data class CodeExecutionBlock(
    val language: String,
    val code: String,
    val output: String
)

data class FunctionTokenAnalysisResult(
    val jaccardSimilarity: Double,
    val sharedKeywordsCount: Int,
    val textATokens: Int,
    val textBTokens: Int,
    val functionCalled: String
)

data class GeminiApiResponse(
    val text: String,
    val thoughts: String = "",
    val jsonFormatted: String = "",
    val groundingSources: List<GroundingSource> = emptyList(),
    val codeBlocks: List<CodeExecutionBlock> = emptyList(),
    val functionAnalysisResult: FunctionTokenAnalysisResult? = null,
    val promptTokenCount: Int = 0,
    val candidateTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

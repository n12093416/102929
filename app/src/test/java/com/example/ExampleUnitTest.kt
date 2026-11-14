package com.example

import com.example.data.remote.FirebaseAiService
import com.example.data.remote.GeminiToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun eightDistinctGeminiApisAreRegistered() {
        val tools = GeminiToolType.entries
        assertEquals(8, tools.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), tools.map { it.apiNumber })
    }

    @Test
    fun jaccardSimilarityCalculatesCorrectly() {
        val service = FirebaseAiService()
        val (identical, sharedSame) = service.computeJaccardSimilarity(
            "모바일 인공지능 작업실 개발",
            "모바일 인공지능 작업실 개발"
        )
        assertEquals(1.0, identical, 0.0001)
        assertEquals(4, sharedSame.size)

        val (disjoint, sharedNone) = service.computeJaccardSimilarity(
            "alpha beta",
            "gamma delta"
        )
        assertEquals(0.0, disjoint, 0.0001)
        assertTrue(sharedNone.isEmpty())
    }

    @Test
    fun composeSystemInstructionKeepsCommonAndIndividualIndependent() {
        val service = FirebaseAiService()

        val both = service.composeSystemInstruction(
            commonDirective = "Always reply in Korean.",
            individualDirective = "Validate calculations with Python."
        )
        assertTrue(both.contains("Always reply in Korean."))
        assertTrue(both.contains("Validate calculations with Python."))

        val onlyCommon = service.composeSystemInstruction(
            commonDirective = "Always reply in Korean.",
            individualDirective = ""
        )
        assertTrue(onlyCommon.contains("Always reply in Korean."))
        assertFalse(onlyCommon.contains("개별 지시문"))

        val onlyIndividual = service.composeSystemInstruction(
            commonDirective = "",
            individualDirective = "Use strict JSON."
        )
        assertFalse(onlyIndividual.contains("공통 지시문"))
        assertTrue(onlyIndividual.contains("Use strict JSON."))
    }
}

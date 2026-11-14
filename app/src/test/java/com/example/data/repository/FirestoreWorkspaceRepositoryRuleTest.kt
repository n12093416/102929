package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreWorkspaceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveRecord_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreWorkspaceRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveRecord(
                userId = aliceUid,
                toolId = "MULTI_TURN_CHAT",
                toolTitle = "멀티턴 AI 채팅",
                modelEndpoint = "Firebase.ai · gemini-3.8-flash",
                prompt = "Explain Clean Architecture",
                responseText = "Clean Architecture separates domain and UI.",
                metadataSummary = "42 tokens"
            )
        }
        assertTrue("Expected record creation to succeed", createResult.isSuccess)
        val recordId = createResult.getOrThrow()
        assertTrue(recordId.isNotEmpty())

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeRecords(aliceUid).first { list -> list.any { it.id == recordId } }
        }
        assertTrue(emitted.any { it.id == recordId && it.userId == aliceUid })

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getRecordById(aliceUid, recordId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals("Explain Clean Architecture", fetched?.prompt)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteRecord(aliceUid, recordId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun saveAndObserveDirectiveConfig_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreWorkspaceRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDirectiveConfig(
                userId = aliceUid,
                commonDirective = "모든 응답을 한국어로 작성하세요.",
                useCommonDirective = true,
                individualDirectives = mapOf("CODE_EXECUTION" to "Python으로 수치 검증"),
                individualEnabledMap = mapOf("CODE_EXECUTION" to true)
            )
        }
        assertTrue("Expected directive config save to succeed", saveResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDirectiveConfig(aliceUid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals("모든 응답을 한국어로 작성하세요.", observed?.commonDirective)
        assertEquals("Python으로 수치 검증", observed?.individualDirectives?.get("CODE_EXECUTION"))

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDirectiveConfig(
                userId = aliceUid,
                commonDirective = "공통 지시문 업데이트",
                useCommonDirective = false,
                individualDirectives = mapOf("CODE_EXECUTION" to "Python으로 수치 검증"),
                individualEnabledMap = mapOf("CODE_EXECUTION" to true)
            )
        }
        assertTrue("Expected directive config update to succeed", updateResult.isSuccess)
    }

    @Test
    fun getRecordById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreWorkspaceRepository(firestore)
        val recordId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveRecord(
                userId = aliceUid,
                toolId = "THINKING_REASONER",
                toolTitle = "심층 사고 추론",
                modelEndpoint = "Firebase.ai · gemini-3.8-flash",
                prompt = "Alice confidential prompt",
                responseText = "Alice confidential response"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreWorkspaceRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getRecordById(aliceUid, recordId)
        }
        assertTrue("Bob must not read Alice's record", result.isFailure)
        val firestoreException = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeRecords_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FirestoreWorkspaceRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeRecords("unauth_target_uid").first()
            }
            fail("Expected PERMISSION_DENIED when observing records unauthenticated")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun saveDirectiveConfig_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FirestoreWorkspaceRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDirectiveConfig(
                userId = "unauth_target_uid",
                commonDirective = "Unauthorized write",
                useCommonDirective = true,
                individualDirectives = emptyMap(),
                individualEnabledMap = emptyMap()
            )
        }
        assertTrue(result.isFailure)
        val firestoreException = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_studio@test.com"
        const val BOB_EMAIL = "bob_studio@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

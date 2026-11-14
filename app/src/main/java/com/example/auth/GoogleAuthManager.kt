package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleUserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val profilePictureUri: String?
)

sealed class AuthResult {
    data class Success(val profile: GoogleUserProfile) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data object Cancelled : AuthResult()
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun FirebaseUser.toGoogleUserProfile(): GoogleUserProfile = GoogleUserProfile(
    id = uid,
    email = email.orEmpty(),
    displayName = displayName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore('@')
        ?: "Gemini User",
    profilePictureUri = photoUrl?.toString()
)

class GoogleAuthManager(private val appContext: Context) {

    private val credentialManager = CredentialManager.create(appContext)
    private val auth: FirebaseAuth get() = Firebase.auth

    companion object {
        private const val TAG = "Auth"
    }

    fun observeAuthState(): Flow<FirebaseUser?> = auth.authStateFlow()

    fun currentUser(): FirebaseUser? = auth.currentUser

    /**
     * Silent auto sign-in using GetGoogleIdOption in its own single-option request.
     */
    suspend fun attemptAutoSignIn(activityContext: Context): Boolean {
        if (auth.currentUser != null) return true
        val clientId = resolveWebClientId(activityContext) ?: return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val activity = activityContext.findActivity() ?: return false
            val result = credentialManager.getCredential(context = activity, request = request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in unavailable: ${e.message}")
            false
        }
    }

    /**
     * Interactive Sign in with Google via Jetpack Credential Manager (GetSignInWithGoogleOption).
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthResult {
        val clientId = resolveWebClientId(activityContext)
            ?: return AuthResult.Error("Google Sign-In 설정(default_web_client_id)을 찾을 수 없습니다.")

        val activity = activityContext.findActivity()
            ?: return AuthResult.Error("Google 로그인 창을 열기 위한 Activity 컨텍스트를 찾을 수 없습니다.")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                    ?: return AuthResult.Error("Firebase 인증 사용자 정보를 가져오지 못했습니다.")
                AuthResult.Success(user.toGoogleUserProfile())
            } else {
                AuthResult.Error("지원하지 않는 Google 자격 증명 형식입니다.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available on device: ${e.message}", e)
            AuthResult.Error("기기에 사용 가능한 Google 계정이 없습니다. 기기 설정에서 Google 계정을 추가해주세요.")
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Invalid Google ID Token response", e)
            AuthResult.Error("Google ID 토큰 파싱에 실패했습니다.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.type} - ${e.message}", e)
            AuthResult.Error(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google 로그인에 실패했습니다.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Sign-In failed", e)
            AuthResult.Error(e.localizedMessage ?: "로그인 중 오류가 발생했습니다.")
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            try {
                appContext.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

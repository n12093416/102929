package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.StudioUiState

@Composable
fun AccountScreen(
    state: StudioUiState,
    onSignInClick: (android.content.Context) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Google 계정 & Firebase 연동", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(
            text = "Firebase Authentication과 Jetpack Credential Manager로 안전하게 계정을 인증합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        // Google Profile / Sign-In Card
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = BorderStroke(1.dp, if (state.currentUser != null) EmeraldTertiary.copy(alpha = 0.6f) else StudioBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val user = state.currentUser
                if (user != null) {
                    if (user.profilePictureUri != null) {
                        AsyncImage(
                            model = user.profilePictureUri,
                            contentDescription = "Google 프로필 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(48.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.VerifiedUser, contentDescription = "인증됨", tint = EmeraldTertiary, modifier = Modifier.size(18.dp))
                    }
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = StudioSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Firebase UID: ${user.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = onSignOutClick,
                        enabled = !state.isAuthLoading,
                        border = BorderStroke(1.dp, CoralError.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CoralError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("로그아웃 (Credential 상태 초기화)", color = CoralError, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(StudioSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(42.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Google 계정으로 작업실 연결", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "로그인하면 계정 UID별로 Cloud Firestore 보관함과 지시문이 동기화됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { onSignInClick(context) },
                        enabled = !state.isAuthLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF111827)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (state.isAuthLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF111827))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Auth feedback banner
        state.authFeedback?.let { feedback ->
            val isError = feedback.contains("실패") || feedback.contains("오류") || feedback.contains("설정되지") || feedback.contains("없습니다")
            Surface(
                color = (if (isError) CoralError else EmeraldTertiary).copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isError) CoralError else EmeraldTertiary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) CoralError else EmeraldTertiary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Firebase Backend Configuration Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Firebase 백엔드 구성 상태", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }

                ConfigStatusRow(
                    keyName = "Firebase AI Logic + App Check",
                    description = "API 키 노출 없이 gemini-3.8-flash / 3.5-flash-lite 보안 호출",
                    isReady = true
                )

                ConfigStatusRow(
                    keyName = "Cloud Firestore & Firebase Auth",
                    description = "Google 계정별 작업 보관함 및 공통/개별 지시문 실시간 동기화",
                    isReady = true
                )

                Text(
                    text = "※ Firebase App Check와 Firestore 보안 규칙으로 본인 계정의 데이터만 안전하게 보호됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ConfigStatusRow(
    keyName: String,
    description: String,
    isReady: Boolean
) {
    Surface(
        color = StudioSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(keyName, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isReady) EmeraldTertiary else AmberSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isReady) "주입됨" else "설정 필요",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReady) EmeraldTertiary else AmberSecondary
                )
            }
        }
    }
}

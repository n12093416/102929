package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AuthResult
import com.example.auth.GoogleAuthManager
import com.example.auth.toGoogleUserProfile
import com.example.data.remote.FirebaseAiService
import com.example.data.repository.FirestoreWorkspaceRepository
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.CollabScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StudioHomeScreen
import com.example.ui.screens.ToolWorkbenchScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GeminiStudioTheme
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MainTab
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var authManager: GoogleAuthManager
    private lateinit var aiService: FirebaseAiService
    private lateinit var repository: FirestoreWorkspaceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run BEFORE setContent, ViewModels, or any Firebase SDK calls.
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        authManager = GoogleAuthManager(applicationContext)
        aiService = FirebaseAiService()
        repository = FirestoreWorkspaceRepository(applicationContext)

        setContent {
            GeminiStudioTheme {
                val firebaseUser by authManager.observeAuthState()
                    .collectAsStateWithLifecycle(initialValue = authManager.currentUser())
                var isSigningIn by remember { mutableStateOf(false) }
                var signInError by remember { mutableStateOf<String?>(null) }
                var triedAutoSignIn by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(firebaseUser) {
                    if (firebaseUser == null && !triedAutoSignIn) {
                        triedAutoSignIn = true
                        authManager.attemptAutoSignIn(this@MainActivity)
                    } else if (firebaseUser != null) {
                        signInError = null
                        isSigningIn = false
                    }
                }

                val activeUser = firebaseUser
                if (activeUser == null) {
                    SignInScreen(
                        isLoading = isSigningIn,
                        errorMessage = signInError,
                        onSignInClick = { ctx ->
                            if (!isSigningIn) {
                                coroutineScope.launch {
                                    isSigningIn = true
                                    signInError = null
                                    when (val res = authManager.signInWithGoogle(ctx)) {
                                        is AuthResult.Success -> {
                                            isSigningIn = false
                                        }
                                        is AuthResult.Error -> {
                                            isSigningIn = false
                                            signInError = res.message
                                        }
                                        AuthResult.Cancelled -> {
                                            isSigningIn = false
                                            signInError = "Google 로그인이 취소되었습니다."
                                        }
                                    }
                                }
                            }
                        }
                    )
                } else {
                    key(activeUser.uid) {
                        val studioViewModel: StudioViewModel = viewModel(
                            key = activeUser.uid,
                            factory = StudioViewModel.factory(
                                repository = repository,
                                aiService = aiService,
                                authManager = authManager,
                                currentUserId = activeUser.uid,
                                currentUserProfile = activeUser.toGoogleUserProfile(),
                                decodeBitmap = ::decodeScaledBitmap
                            )
                        )
                        AuthenticatedStudioContent(
                            viewModel = studioViewModel,
                            onSignInClick = { ctx ->
                                coroutineScope.launch {
                                    authManager.signInWithGoogle(ctx)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun decodeScaledBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val original = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("선택한 이미지를 읽을 수 없습니다.")

        val maxDim = 1024
        val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        if (scale < 1f) {
            val resized = Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (resized !== original) original.recycle()
            resized
        } else {
            original
        }
    }
}

@Composable
private fun AuthenticatedStudioContent(
    viewModel: StudioViewModel,
    onSignInClick: (android.content.Context) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedRecords by viewModel.savedRecords.collectAsStateWithLifecycle()

    val activeTool = uiState.activeTool
    if (activeTool != null) {
        ToolWorkbenchScreen(
            tool = activeTool,
            state = uiState,
            viewModel = viewModel
        )
    } else {
        BackHandler(enabled = uiState.selectedTab != MainTab.STUDIO) {
            viewModel.selectTab(MainTab.STUDIO)
        }

        AdaptiveStudioShell(
            selectedTab = uiState.selectedTab,
            onSelectTab = { viewModel.selectTab(it) }
        ) { innerModifier ->
            when (uiState.selectedTab) {
                MainTab.STUDIO -> StudioHomeScreen(
                    state = uiState,
                    recordCount = savedRecords.size,
                    viewModel = viewModel,
                    onToolClick = { viewModel.openTool(it) },
                    onNavigateTab = { viewModel.selectTab(it) },
                    modifier = innerModifier
                )
                MainTab.COLLAB -> CollabScreen(
                    state = uiState,
                    viewModel = viewModel,
                    modifier = innerModifier
                )
                MainTab.HISTORY -> HistoryScreen(
                    records = savedRecords,
                    selectedToolFilter = uiState.historyFilterToolId,
                    onSelectFilter = { viewModel.setHistoryFilter(it) },
                    onDeleteRecord = { viewModel.deleteHistoryRecord(it) },
                    onClearAll = { viewModel.clearAllHistory() },
                    modifier = innerModifier
                )
                MainTab.ACCOUNT -> AccountScreen(
                    state = uiState,
                    onSignInClick = onSignInClick,
                    onSignOutClick = { viewModel.signOut() },
                    modifier = innerModifier
                )
            }
        }
    }
}

@Composable
private fun AdaptiveStudioShell(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
    ) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail(
                    containerColor = StudioSurface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectTab(tab) },
                            icon = { TabIcon(tab, selected) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanPrimary,
                                selectedTextColor = CyanPrimary,
                                indicatorColor = StudioSurfaceElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else {
            Scaffold(
                containerColor = StudioBackground,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(containerColor = StudioSurface) {
                        MainTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                icon = { TabIcon(tab, selected) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanPrimary,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = StudioSurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, selected: Boolean) {
    val icon = when (tab) {
        MainTab.STUDIO -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
        MainTab.COLLAB -> if (selected) Icons.Filled.Forum else Icons.Outlined.Forum
        MainTab.HISTORY -> if (selected) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks
        MainTab.ACCOUNT -> if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
    }
    Icon(imageVector = icon, contentDescription = tab.label)
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.game.GameViewModel
import com.example.game.ScreenState
import com.example.ui.screens.VibRibbonMainScreen

class MainActivity : ComponentActivity() {

    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibRibbonAppTheme {
                val state by gameViewModel.uiState.collectAsState()

                // Hardware / System gesture BackHandler
                BackHandler(enabled = state.screen != ScreenState.TITLE) {
                    when (state.screen) {
                        ScreenState.PLAYING -> gameViewModel.pauseGame()
                        ScreenState.PAUSED -> gameViewModel.setScreen(ScreenState.TITLE)
                        ScreenState.TUTORIAL -> gameViewModel.setScreen(ScreenState.TITLE)
                        ScreenState.GAME_OVER -> gameViewModel.setScreen(ScreenState.TITLE)
                        ScreenState.STAGE_CLEAR -> gameViewModel.setScreen(ScreenState.TITLE)
                        ScreenState.BUFFERING -> gameViewModel.setScreen(ScreenState.TITLE)
                        ScreenState.TITLE -> { /* default back */ }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF030307)
                ) {
                    VibRibbonMainScreen(viewModel = gameViewModel, state = state)
                }
            }
        }
    }
}

@Composable
fun VibRibbonAppTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF00FFCC),
        secondary = Color(0xFFFFD166),
        tertiary = Color(0xFFFF4D4D),
        background = Color(0xFF030307),
        surface = Color(0xFF0D0F18),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}

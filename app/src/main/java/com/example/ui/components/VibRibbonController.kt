package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BasicObstacle

@Composable
fun VibRibbonController(
    activeButtons: Set<BasicObstacle>,
    onButtonDown: (BasicObstacle) -> Unit,
    onButtonUp: (BasicObstacle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Control Cluster: BLOCK [□] & PIT [▽]
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ControllerTouchButton(
                obstacle = BasicObstacle.BLOCK,
                symbol = "□",
                label = "BLOCK",
                subLabel = "드럼 비트",
                isPressed = activeButtons.contains(BasicObstacle.BLOCK),
                onDown = { onButtonDown(BasicObstacle.BLOCK) },
                onUp = { onButtonUp(BasicObstacle.BLOCK) },
                tag = "btn_block"
            )

            ControllerTouchButton(
                obstacle = BasicObstacle.PIT,
                symbol = "▽",
                label = "PIT",
                subLabel = "급격한 드롭",
                isPressed = activeButtons.contains(BasicObstacle.PIT),
                onDown = { onButtonDown(BasicObstacle.PIT) },
                onUp = { onButtonUp(BasicObstacle.PIT) },
                tag = "btn_pit"
            )
        }

        // Center visual indicator of current active combinations
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(8.dp))
                .background(Color(0x22111116), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (activeButtons.size > 1) "⚡ COMPOSITE HIT ⚡" else if (activeButtons.size == 1) "INPUT ACTIVE" else "READY",
                    color = if (activeButtons.size > 1) Color(0xFFFFE066) else if (activeButtons.size == 1) Color.White else Color(0x66FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = activeButtons.joinToString(" + ") { it.koreanName }.ifEmpty { "버튼 대기 중" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Right Control Cluster: LOOP [◯] & WAVE [〜]
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ControllerTouchButton(
                obstacle = BasicObstacle.LOOP,
                symbol = "◯",
                label = "LOOP",
                subLabel = "고음 피크",
                isPressed = activeButtons.contains(BasicObstacle.LOOP),
                onDown = { onButtonDown(BasicObstacle.LOOP) },
                onUp = { onButtonUp(BasicObstacle.LOOP) },
                tag = "btn_loop"
            )

            ControllerTouchButton(
                obstacle = BasicObstacle.WAVE,
                symbol = "〜",
                label = "WAVE",
                subLabel = "중음 물결",
                isPressed = activeButtons.contains(BasicObstacle.WAVE),
                onDown = { onButtonDown(BasicObstacle.WAVE) },
                onUp = { onButtonUp(BasicObstacle.WAVE) },
                tag = "btn_wave"
            )
        }
    }
}

@Composable
private fun ControllerTouchButton(
    obstacle: BasicObstacle,
    symbol: String,
    label: String,
    subLabel: String,
    isPressed: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    tag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xDDFFFFFF) else Color(0x18FFFFFF),
        animationSpec = tween(50),
        label = "btn_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) Color.Black else Color.White,
        animationSpec = tween(50),
        label = "btn_fg"
    )
    val borderColor = if (isPressed) Color(0xFF00FFCC) else Color(0x99FFFFFF)

    Box(
        modifier = Modifier
            .size(width = 86.dp, height = 72.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(14.dp))
            .pointerInput(obstacle) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDown()
                    waitForUpOrCancellation()
                    onUp()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = symbol,
                color = contentColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subLabel,
                color = if (isPressed) Color(0xAA000000) else Color(0x77FFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

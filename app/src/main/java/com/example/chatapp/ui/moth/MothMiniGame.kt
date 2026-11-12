package com.example.chatapp.ui.moth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.PixelMoth
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MothMiniGameView() {
    var isPlaying by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(30) }

    var mothY by remember { mutableFloatStateOf(0.5f) }
    var mothVelocity by remember { mutableFloatStateOf(0f) }

    var emberX by remember { mutableFloatStateOf(1.0f) }
    var emberY by remember { mutableFloatStateOf(0.4f) }

    fun startGame() {
        mothY = 0.5f
        mothVelocity = -0.015f
        emberX = 1.0f
        emberY = Random.nextFloat() * 0.6f + 0.2f
        score = 0
        gameOver = false
        isPlaying = true
    }

    fun flap() {
        if (!isPlaying) {
            startGame()
        } else if (!gameOver) {
            mothVelocity = -0.035f // Flap up!
        } else {
            startGame()
        }
    }

    // Game physics loop
    LaunchedEffect(isPlaying, gameOver) {
        while (isPlaying && !gameOver) {
            delay(30)
            mothVelocity += 0.003f // Gravity
            mothY = (mothY + mothVelocity).coerceIn(0.05f, 0.95f)

            emberX -= 0.018f
            if (emberX < -0.1f) {
                emberX = 1.1f
                emberY = Random.nextFloat() * 0.6f + 0.2f
            }

            // Catch ember collision check
            val dx = kotlin.math.abs(0.25f - emberX)
            val dy = kotlin.math.abs(mothY - emberY)
            if (dx < 0.08f && dy < 0.09f) {
                score += 10
                if (score > highScore) highScore = score
                emberX = 1.1f
                emberY = Random.nextFloat() * 0.6f + 0.2f
            }

            // Bottom ground collision check
            if (mothY >= 0.93f) {
                gameOver = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Game Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "moth & ember",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LanternAmber,
                )
                Text(
                    text = "tap screen to flap toward embers",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                )
            }

            SteppedBox(
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                cutSize = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "score: $score",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TheirBubbleText,
                    )
                    Text(
                        text = "best: $highScore",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = LanternAmber,
                    )
                }
            }
        }

        // Playable Pixel Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MothHeaderBg, SteppedCornerShape(6.dp))
                .border(2.dp, MothDivider, SteppedCornerShape(6.dp))
                .clickable { flap() }
                .testTag("moth_game_canvas"),
        ) {
            // Background Stars and Embers
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw background stars
                drawRect(StarYellow, Offset(0.2f * w, 0.15f * h), Size(3.dp.toPx(), 3.dp.toPx()))
                drawRect(MutedText, Offset(0.7f * w, 0.25f * h), Size(3.dp.toPx(), 3.dp.toPx()))
                drawRect(StarYellow, Offset(0.5f * w, 0.8f * h), Size(3.dp.toPx(), 3.dp.toPx()))
                drawRect(MutedText, Offset(0.85f * w, 0.65f * h), Size(3.dp.toPx(), 3.dp.toPx()))

                // Ground fog
                drawRect(
                    color = Color(0xFF16122C),
                    topLeft = Offset(0f, 0.94f * h),
                    size = Size(w, 0.06f * h),
                )

                // Glowing Ember target
                val ex = emberX * w
                val ey = emberY * h
                drawRect(LanternAmber, Offset(ex - 8.dp.toPx(), ey - 8.dp.toPx()), Size(16.dp.toPx(), 16.dp.toPx()))
                drawRect(LanternGlow, Offset(ex - 4.dp.toPx(), ey - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))
            }

            // Player Moth
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 60.dp, y = (mothY * 340).dp),
            ) {
                PixelMoth(isAnimated = isPlaying && !gameOver)
            }

            // Start / Game Over Overlay
            if (!isPlaying || gameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x991A1633)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (gameOver) "the lantern extinguished" else "moth flight",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (gameOver) WaxSealRed else LanternAmber,
                        )
                        if (gameOver) {
                            Text(
                                text = "embers gathered: $score",
                                fontFamily = PixelifySans,
                                fontSize = 14.sp,
                                color = TheirBubbleText,
                            )
                        }
                        SteppedButton(
                            text = if (gameOver) "ignite again" else "take flight",
                            onClick = { startGame() },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            cutSize = 4.dp,
                        )
                    }
                }
            }
        }
    }
}

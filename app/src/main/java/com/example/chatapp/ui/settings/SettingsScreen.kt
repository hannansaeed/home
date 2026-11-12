package com.example.chatapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.theme.*

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
) {
    var soundEnabled by remember { mutableStateOf(true) }
    var sealedMode by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .testTag("settings_screen"),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(width = 2.dp, color = MothDivider)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelLantern(isLit = true)
                    Text(
                        text = "moth hours",
                        fontFamily = PixelifySans,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternAmber,
                        letterSpacing = 1.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).background(SecureGreen))
                    Text(
                        text = "sealed",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = SecureGreen,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile Card
            SteppedBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                cutSize = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PixelLantern(isLit = true)
                    Column {
                        Text(
                            text = "moth_fan",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TheirBubbleText,
                        )
                        Text(
                            text = "lantern lit • private by default",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                    }
                }
            }

            // Security Settings
            SteppedBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "lantern security",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = LanternAmber,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sealedMode = !sealedMode },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "end-to-end sealed cipher",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = TheirBubbleText,
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(if (sealedMode) SecureGreen else MothDivider),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { soundEnabled = !soundEnabled },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ember chime on message",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = TheirBubbleText,
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(if (soundEnabled) LanternAmber else MothDivider),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extinguish Lantern / Sign Out button
            SteppedButton(
                text = "extinguish lantern",
                onClick = onSignOut,
                backgroundColor = WaxSealRed,
                borderColor = WaxSealDarkEdge,
                textColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button"),
            )
        }
    }
}

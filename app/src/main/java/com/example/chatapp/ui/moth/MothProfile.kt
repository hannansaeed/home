package com.example.chatapp.ui.moth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.theme.*

@Composable
fun MothProfileView(
    state: MothState,
    onSignOut: () -> Unit,
) {
    var editUsername by remember { mutableStateOf(state.currentUsername) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val avatarColors = listOf(
        Color(0xFFB8A9E8), // Lavender
        Color(0xFF82D8A7), // Mint
        Color(0xFFF38D71), // Coral
        Color(0xFFFFD152), // Yellow
        Color(0xFF70D6FF), // Sky blue
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column {
            Text(
                text = "operator lantern",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LanternAmber,
            )
            Text(
                text = "private by default • encrypted locally",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
            )
        }

        // Profile Card
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PixelFaceAvatar(color = state.userAvatarColor)

                    Column {
                        Text(
                            text = state.currentUsername,
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TheirBubbleText,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(SecureGreen))
                            Text(
                                text = "lantern lit • sealed",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = SecureGreen,
                            )
                        }
                    }
                }

                // Edit Handle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "change lantern handle",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SteppedBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicTextField(
                                    value = editUsername,
                                    onValueChange = { editUsername = it },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        SteppedButton(
                            text = "save",
                            onClick = {
                                if (editUsername.isNotBlank()) {
                                    state.currentUsername = editUsername.trim()
                                }
                            },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            cutSize = 3.dp,
                        )
                    }
                }

                // Choose Avatar Color
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "lantern hue",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        avatarColors.forEach { color ->
                            val isSelected = state.userAvatarColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, SteppedCornerShape(3.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) LanternAmber else MothBgDark,
                                        shape = SteppedCornerShape(3.dp),
                                    )
                                    .clickable { state.updateAvatarColor(color) },
                            )
                        }
                    }
                }
            }
        }

        // Toggles & Security
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "cipher preferences",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = LanternAmber,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.sealedCipherEnabled = !state.sealedCipherEnabled },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "sealed end-to-end cipher",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = TheirBubbleText,
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(if (state.sealedCipherEnabled) SecureGreen else MothDivider),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.sealedCipherEnabled) {
                            Text(text = "■", fontSize = 10.sp, color = MothBgDark)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.emberChimesEnabled = !state.emberChimesEnabled },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ember chime on new transmission",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = TheirBubbleText,
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(if (state.emberChimesEnabled) LanternAmber else MothDivider),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.emberChimesEnabled) {
                            Text(text = "■", fontSize = 10.sp, color = MyBubbleText)
                        }
                    }
                }
            }
        }

        // Extinguish Lantern / Sign Out button
        if (!showLogoutConfirm) {
            SteppedButton(
                text = "extinguish lantern (log out)",
                onClick = { showLogoutConfirm = true },
                backgroundColor = WaxSealRed,
                borderColor = WaxSealDarkEdge,
                textColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button"),
            )
        } else {
            SteppedBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = TheirBubbleFill,
                borderColor = WaxSealRed,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "extinguish your lantern?",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TheirBubbleText,
                    )
                    Text(
                        text = "your local key will be locked until reignited.",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SteppedButton(
                            text = "yes, extinguish",
                            onClick = onSignOut,
                            backgroundColor = WaxSealRed,
                            borderColor = WaxSealDarkEdge,
                            textColor = Color.White,
                            modifier = Modifier.testTag("confirm_sign_out_button"),
                        )
                        SteppedButton(
                            text = "keep lit",
                            onClick = { showLogoutConfirm = false },
                            backgroundColor = TheirBubbleBorder,
                            borderColor = MothDivider,
                            textColor = TheirBubbleText,
                        )
                    }
                }
            }
        }
    }
}

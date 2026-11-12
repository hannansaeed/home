package com.example.chatapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
) {
    var username by remember { mutableStateOf("moth_fan") }
    var passphrase by remember { mutableStateOf("••••••••") }
    var showPassphrase by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("login_screen"),
    ) {
        // Starfield background
        PixelStarfield()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hanging Lantern Header with flanking moths (matches Screenshot 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Large hanging lantern from top
                LargeHangingLantern(modifier = Modifier.align(Alignment.TopCenter))

                // Left Flapping Moth
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp, top = 20.dp),
                ) {
                    PixelMoth(isAnimated = true)
                }

                // Right Flapping Moth
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp, top = 20.dp),
                ) {
                    PixelMoth(isAnimated = true)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title & Tagline
            Text(
                text = "moth hours",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = LanternAmber,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "private by default",
                fontFamily = PixelifySans,
                fontSize = 15.sp,
                color = MutedText,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Username Field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "username",
                    fontFamily = PixelifySans,
                    fontSize = 14.sp,
                    color = MutedText,
                )

                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                    borderWidth = 2.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = username,
                            onValueChange = { username = it },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 15.sp,
                                color = TheirBubbleText,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Passphrase Field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "passphrase",
                    fontFamily = PixelifySans,
                    fontSize = 14.sp,
                    color = MutedText,
                )

                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                    borderWidth = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                textStyle = TextStyle(
                                    fontFamily = PixelifySans,
                                    fontSize = 15.sp,
                                    color = TheirBubbleText,
                                ),
                                cursorBrush = SolidColor(LanternAmber),
                                singleLine = true,
                                visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                            )
                        }

                        // "show" / "hide" toggle
                        Text(
                            text = if (showPassphrase) "hide" else "show",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = MutedText,
                            modifier = Modifier
                                .clickable { showPassphrase = !showPassphrase }
                                .padding(start = 8.dp)
                                .testTag("toggle_passphrase_button"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Primary Button: "light the lantern" (matches Screenshot 3)
            SteppedButton(
                text = "light the lantern",
                onClick = onLoginSuccess,
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                textColor = MyBubbleText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("login_submit_button"),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Divider: line "or" line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(MothDivider),
                )
                Text(
                    text = "or",
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    color = MutedText,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(MothDivider),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Secondary Outlined Button: "make a new lantern"
            SteppedButton(
                text = "make a new lantern",
                onClick = onLoginSuccess,
                backgroundColor = MothBgDark,
                borderColor = TheirBubbleBorder,
                textColor = TheirBubbleText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("register_button"),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Footer in green: "■ no phone number needed"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(SecureGreen),
                )
                Text(
                    text = "no phone number needed",
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    color = SecureGreen,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

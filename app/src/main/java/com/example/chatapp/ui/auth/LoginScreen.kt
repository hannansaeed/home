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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.theme.*

enum class AuthScreenMode {
    LOGIN,
    SIGNUP
}

@Composable
fun LoginScreen(
    state: MothState,
    onLoginSuccess: () -> Unit,
) {
    var authMode by remember { mutableStateOf(AuthScreenMode.LOGIN) }

    // Login state
    var loginUsername by remember { mutableStateOf(state.currentUsername) }
    var loginPassphrase by remember { mutableStateOf("") }
    var showLoginPassphrase by remember { mutableStateOf(false) }

    // Signup state
    var signupUsername by remember { mutableStateOf("") }
    var signupPassphrase by remember { mutableStateOf("") }
    var signupConfirmPassphrase by remember { mutableStateOf("") }
    var showSignupPassphrase by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lanternSparkCount by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()

    fun performLogin() {
        if (loginUsername.isBlank()) {
            errorMessage = "please enter your lantern handle"
        } else if (loginPassphrase.isBlank()) {
            errorMessage = "please enter your passphrase"
        } else {
            errorMessage = null
            state.currentUsername = loginUsername.trim()
            onLoginSuccess()
        }
    }

    fun performSignup() {
        if (signupUsername.isBlank()) {
            errorMessage = "please choose a lantern handle"
        } else if (signupPassphrase.isBlank()) {
            errorMessage = "please set a passphrase"
        } else if (signupPassphrase.length < 3) {
            errorMessage = "passphrase must be at least 3 characters"
        } else if (signupPassphrase != signupConfirmPassphrase) {
            errorMessage = "passphrases do not match"
        } else {
            errorMessage = null
            state.currentUsername = signupUsername.trim()
            onLoginSuccess()
        }
    }

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
                // Large hanging lantern from top (interactive: tap to spark embers!)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clickable { lanternSparkCount++ },
                ) {
                    LargeHangingLantern()
                }

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
                text = if (authMode == AuthScreenMode.LOGIN) "private by default" else "craft your nocturnal identity",
                fontFamily = PixelifySans,
                fontSize = 15.sp,
                color = MutedText,
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Error notice banner
            if (errorMessage != null) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    backgroundColor = TheirBubbleFill,
                    borderColor = WaxSealRed,
                    cutSize = 3.dp,
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = WaxSealRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (authMode == AuthScreenMode.LOGIN) {
                // ==================== LOGIN FORM ====================

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
                            if (loginUsername.isEmpty()) {
                                Text(
                                    text = "e.g. moth_fan",
                                    fontFamily = PixelifySans,
                                    fontSize = 15.sp,
                                    color = PlaceholderText,
                                )
                            }
                            BasicTextField(
                                value = loginUsername,
                                onValueChange = {
                                    loginUsername = it
                                    errorMessage = null
                                },
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

                // Passphrase Field with WORKING Show/Hide toggle
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
                                if (loginPassphrase.isEmpty()) {
                                    Text(
                                        text = "enter secret passphrase",
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = loginPassphrase,
                                    onValueChange = {
                                        loginPassphrase = it
                                        errorMessage = null
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 15.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    visualTransformation = if (showLoginPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        keyboardType = KeyboardType.Password,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("password_input"),
                                )
                            }

                            // WORKING Show / Hide button
                            Box(
                                modifier = Modifier
                                    .clickable { showLoginPassphrase = !showLoginPassphrase }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                    .testTag("toggle_passphrase_button"),
                            ) {
                                Text(
                                    text = if (showLoginPassphrase) "hide" else "show",
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = if (showLoginPassphrase) LanternAmber else MutedText,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Primary Button: "light the lantern" (WORKS AS LOGIN)
                SteppedButton(
                    text = "light the lantern",
                    onClick = { performLogin() },
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

                // Secondary Outlined Button: "make a new lantern" (OPENS SIGNUP PAGE)
                SteppedButton(
                    text = "make a new lantern",
                    onClick = {
                        authMode = AuthScreenMode.SIGNUP
                        errorMessage = null
                    },
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("register_button"),
                )
            } else {
                // ==================== SIGNUP / MAKE A NEW LANTERN FORM ====================

                // Choose Username
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "choose lantern handle",
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
                            if (signupUsername.isEmpty()) {
                                Text(
                                    text = "e.g. night_flier, tide_watcher",
                                    fontFamily = PixelifySans,
                                    fontSize = 14.sp,
                                    color = PlaceholderText,
                                )
                            }
                            BasicTextField(
                                value = signupUsername,
                                onValueChange = {
                                    signupUsername = it
                                    errorMessage = null
                                },
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
                                    .testTag("signup_username_input"),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Set Passphrase
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "set secret passphrase",
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
                                if (signupPassphrase.isEmpty()) {
                                    Text(
                                        text = "create secret passphrase",
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = signupPassphrase,
                                    onValueChange = {
                                        signupPassphrase = it
                                        errorMessage = null
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 15.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    visualTransformation = if (showSignupPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        keyboardType = KeyboardType.Password,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_password_input"),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clickable { showSignupPassphrase = !showSignupPassphrase }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = if (showSignupPassphrase) "hide" else "show",
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = if (showSignupPassphrase) LanternAmber else MutedText,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm Passphrase
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "confirm passphrase",
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
                            if (signupConfirmPassphrase.isEmpty()) {
                                Text(
                                    text = "re-type passphrase",
                                    fontFamily = PixelifySans,
                                    fontSize = 14.sp,
                                    color = PlaceholderText,
                                )
                            }
                            BasicTextField(
                                value = signupConfirmPassphrase,
                                onValueChange = {
                                    signupConfirmPassphrase = it
                                    errorMessage = null
                                },
                                textStyle = TextStyle(
                                    fontFamily = PixelifySans,
                                    fontSize = 15.sp,
                                    color = TheirBubbleText,
                                ),
                                cursorBrush = SolidColor(LanternAmber),
                                singleLine = true,
                                visualTransformation = if (showSignupPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Password,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_confirm_password_input"),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Primary Button: "ignite new lantern" (CREATES NEW ACCOUNT / SIGNUP)
                SteppedButton(
                    text = "ignite new lantern",
                    onClick = { performSignup() },
                    backgroundColor = MyBubbleFill,
                    borderColor = MyBubbleBorder,
                    textColor = MyBubbleText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("signup_submit_button"),
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

                // Return to Login Button
                SteppedButton(
                    text = "already have a lantern? light it",
                    onClick = {
                        authMode = AuthScreenMode.LOGIN
                        errorMessage = null
                    },
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("back_to_login_button"),
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

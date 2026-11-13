package com.example.chatapp.ui.moth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

@Composable
fun MothProfileView(
    state: MothState,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editUsername by remember { mutableStateOf(state.currentUsername) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var isUpdatingAvatar by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUpdatingAvatar = true
            avatarError = null
            scope.launch {
                val res = state.updateCustomAvatar(uri)
                res.onFailure { err ->
                    avatarError = err.localizedMessage ?: "failed to set avatar photo"
                }
                isUpdatingAvatar = false
            }
        }
    }

    val avatarColors = listOf(
        Color(0xFFB8A9E8), // Lavender
        Color(0xFF82D8A7), // Mint
        Color(0xFFF38D71), // Coral
        Color(0xFFFFD152), // Yellow
        Color(0xFF70D6FF), // Sky blue
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                    text = "stored locally on your device",
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { showAvatarOptions = true }
                                .testTag("profile_avatar_button"),
                        ) {
                            UserAvatar(
                                avatarPath = state.userAvatarPath,
                                color = state.userAvatarColor,
                                size = 54.dp,
                            )
                            Text(
                                text = if (isUpdatingAvatar) "saving..." else "tap photo",
                                fontFamily = PixelifySans,
                                fontSize = 10.sp,
                                color = LanternAmber,
                            )
                        }

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
                                    text = "lantern lit • active",
                                    fontFamily = PixelifySans,
                                    fontSize = 12.sp,
                                    color = SecureGreen,
                                )
                            }
                        }
                    }

                    if (avatarError != null) {
                        Text(
                            text = avatarError ?: "",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = WaxSealRed,
                        )
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
                        text = "privacy & screen",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )

                    PrivacyToggleRow(
                        title = "block screenshots",
                        subtitle = "hide screen in recents & block captures",
                        checked = state.screenshotBlockingEnabled,
                        testTag = "toggle_screenshot_block",
                        onToggle = { state.setScreenshotBlocking(!state.screenshotBlockingEnabled) },
                    )

                    PrivacyToggleRow(
                        title = "hide typing indicator",
                        subtitle = "others won't see when you're typing",
                        checked = state.hideTypingIndicators,
                        testTag = "toggle_hide_typing",
                        onToggle = { state.setHideTyping(!state.hideTypingIndicators) },
                    )

                    PrivacyToggleRow(
                        title = "disable read receipts",
                        subtitle = "don't send 'seen' confirmations",
                        checked = state.disableReadReceipts,
                        testTag = "toggle_disable_receipts",
                        onToggle = { state.updateDisableReadReceipts(!state.disableReadReceipts) },
                    )

                    // App Lock PIN
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (state.appLockEnabled) {
                                    state.disableAppLock()
                                } else {
                                    pinInput = ""
                                    pinError = null
                                    showPinSetupDialog = true
                                }
                            }
                            .padding(vertical = 4.dp)
                            .testTag("toggle_app_lock"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "app lock (4-digit pin)",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TheirBubbleText,
                            )
                            Text(
                                text = if (state.appLockEnabled) "locked when leaving app • tap to remove" else "require pin when opening app",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = MutedText,
                            )
                        }
                        SteppedBox(
                            backgroundColor = if (state.appLockEnabled) SecureGreen else MothBgDark,
                            borderColor = if (state.appLockEnabled) SecureGreen else MothDivider,
                            cutSize = 2.dp,
                            borderWidth = 1.dp,
                        ) {
                            Text(
                                text = if (state.appLockEnabled) "ON" else "OFF",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (state.appLockEnabled) MothBgDark else MutedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Message Font Size
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "message font size",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val sizeOptions = listOf(
                                "small" to 0.85f,
                                "medium" to 1.0f,
                                "large" to 1.25f,
                            )
                            sizeOptions.forEach { (label, scaleVal) ->
                                val selected = kotlin.math.abs(state.messageFontScale - scaleVal) < 0.05f
                                SteppedButton(
                                    text = label,
                                    onClick = { state.setFontScale(scaleVal) },
                                    backgroundColor = if (selected) MyBubbleFill else MothBgDark,
                                    borderColor = if (selected) MyBubbleBorder else MothDivider,
                                    textColor = if (selected) MyBubbleText else TheirBubbleText,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("font_size_$label"),
                                    cutSize = 3.dp,
                                )
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
                            text = "your local history stays saved on this device.",
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

        if (showPinSetupDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x991A1633))
                    .clickable { showPinSetupDialog = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                        .testTag("pin_setup_dialog"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "set 4-digit app lock pin",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LanternAmber,
                        )
                        SteppedBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
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
                                if (pinInput.isEmpty()) {
                                    Text(
                                        text = "enter 4 digits (e.g. 1234)",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = pinInput,
                                    onValueChange = { next ->
                                        if (next.length <= 4 && next.all { it.isDigit() }) {
                                            pinInput = next
                                            pinError = null
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 16.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (pinError != null) {
                            Text(
                                text = pinError ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = WaxSealRed,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SteppedButton(
                                text = "cancel",
                                onClick = { showPinSetupDialog = false },
                                backgroundColor = TheirBubbleFill,
                                borderColor = MothDivider,
                                textColor = TheirBubbleText,
                                modifier = Modifier.weight(1f),
                                cutSize = 3.dp,
                            )
                            SteppedButton(
                                text = "enable pin",
                                onClick = {
                                    if (state.enableAppLock(pinInput)) {
                                        showPinSetupDialog = false
                                    } else {
                                        pinError = "enter exactly 4 digits"
                                    }
                                },
                                backgroundColor = MyBubbleFill,
                                borderColor = MyBubbleBorder,
                                textColor = MyBubbleText,
                                modifier = Modifier.weight(1f),
                                cutSize = 3.dp,
                            )
                        }
                    }
                }
            }
        }

        // Custom Avatar Photo Options Dialog
        if (showAvatarOptions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x991A1633))
                    .clickable { showAvatarOptions = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "avatar photo",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = LanternAmber,
                            )
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                color = MutedText,
                                modifier = Modifier.clickable { showAvatarOptions = false },
                            )
                        }

                        SteppedButton(
                            text = "choose photo from gallery",
                            onClick = {
                                showAvatarOptions = false
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choose_avatar_photo_button"),
                            cutSize = 3.dp,
                        )

                        if (state.userAvatarPath != null) {
                            SteppedButton(
                                text = "use default pixel face",
                                onClick = {
                                    state.clearCustomAvatar()
                                    showAvatarOptions = false
                                },
                                backgroundColor = TheirBubbleFill,
                                borderColor = WaxSealRed,
                                textColor = WaxSealRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_avatar_button"),
                                cutSize = 3.dp,
                            )
                        }

                        SteppedButton(
                            text = "cancel",
                            onClick = { showAvatarOptions = false },
                            backgroundColor = TheirBubbleFill,
                            borderColor = MothDivider,
                            textColor = TheirBubbleText,
                            modifier = Modifier.fillMaxWidth(),
                            cutSize = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TheirBubbleText,
            )
            Text(
                text = subtitle,
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = MutedText,
            )
        }
        SteppedBox(
            backgroundColor = if (checked) SecureGreen else MothBgDark,
            borderColor = if (checked) SecureGreen else MothDivider,
            cutSize = 2.dp,
            borderWidth = 1.dp,
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (checked) MothBgDark else MutedText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

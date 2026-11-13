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

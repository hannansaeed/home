package com.example.chatapp.ui.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatScreen(
    state: MothState,
    chatTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeConv = state.getActiveConversation()
    val activeChatId = activeConv?.id
        ?: "${state.currentUsername.lowercase()}::${chatTitle.trim().lowercase()}"
    val messages = state.activeMessages

    var draft by remember { mutableStateOf("") }
    var selectedSendType by remember { mutableStateOf(MessageType.REGULAR) }
    var showCipherInfo by remember { mutableStateOf(false) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var isPreparingAttachment by remember { mutableStateOf(false) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (fullScreenPhotoPath != null) {
            fullScreenPhotoPath = null
        } else {
            onBack()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isPreparingAttachment = true
            attachmentError = null
            showAttachMenu = false
            coroutineScope.launch {
                val res = state.preparePhotoAttachment(uri)
                res.onSuccess {
                    pendingAttachment = it
                }.onFailure { err ->
                    attachmentError = err.localizedMessage ?: "failed to load photo"
                }
                isPreparingAttachment = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isPreparingAttachment = true
            attachmentError = null
            showAttachMenu = false
            coroutineScope.launch {
                val res = state.prepareFileAttachment(uri)
                res.onSuccess {
                    pendingAttachment = it
                }.onFailure { err ->
                    attachmentError = err.localizedMessage ?: "failed to load file"
                }
                isPreparingAttachment = false
            }
        }
    }

    fun openLocalFile(path: String, mimeType: String?) {
        try {
            val file = File(path)
            if (!file.exists()) {
                attachmentError = "file not found on device"
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "open attachment"))
        } catch (_: Exception) {
            attachmentError = "no app found to open this file"
        }
    }

    val listState = rememberLazyListState()

    // Scroll to bottom when messages or typing indicator changes
    LaunchedEffect(messages.size, state.isContactTyping) {
        val totalCount = messages.size + (if (state.isContactTyping) 1 else 0)
        if (totalCount > 0) {
            listState.scrollToItem(totalCount - 1)
            delay(40)
            listState.scrollToItem(totalCount - 1)
        }
    }

    // Disappearing message embers timer ticker
    LaunchedEffect(activeChatId, messages) {
        val disappearingMsgs = messages.filter { it.type == MessageType.DISAPPEARING && it.embersRemaining > 0 }
        if (disappearingMsgs.isNotEmpty()) {
            delay(3500)
            disappearingMsgs.forEach { msg ->
                state.tickEmbers(activeChatId, msg.id)
            }
        }
    }

    fun handleSend() {
        if ((draft.isNotBlank() || pendingAttachment != null) && !isPreparingAttachment) {
            val textToSend = draft.trim()
            val typeToSend = selectedSendType
            val attachmentToSend = pendingAttachment

            draft = ""
            selectedSendType = MessageType.REGULAR
            pendingAttachment = null
            showAttachMenu = false
            attachmentError = null

            state.sendMessage(
                chatId = activeChatId,
                text = textToSend,
                type = typeToSend,
                attachment = attachmentToSend,
            )
            coroutineScope.launch {
                val countNow = state.activeMessages.size + (if (state.isContactTyping) 1 else 0)
                if (countNow > 0) {
                    listState.scrollToItem(countNow - 1)
                }
                delay(60)
                val countAfter = state.activeMessages.size + (if (state.isContactTyping) 1 else 0)
                if (countAfter > 0) {
                    listState.scrollToItem(countAfter - 1)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MothBgDark)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("chat_screen"),
        ) {
            // 1. Header
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
                    // Left: Avatar + Chat Title (clickable to go back)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .testTag("chat_back_button"),
                    ) {
                        PixelFaceAvatar(color = activeConv?.avatarColor ?: LanternAmber)
                        Text(
                            text = chatTitle,
                            fontFamily = PixelifySans,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanternAmber,
                            letterSpacing = 1.sp,
                        )
                    }

                    // Right: "■ sealed" green indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { showCipherInfo = !showCipherInfo },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (state.sealedCipherEnabled) SecureGreen else MutedText),
                        )
                        Text(
                            text = if (state.sealedCipherEnabled) "sealed" else "unsealed",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = if (state.sealedCipherEnabled) SecureGreen else MutedText,
                        )
                    }
                }
            }

            // Cipher Information Toast
            AnimatedVisibility(visible = showCipherInfo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .border(1.dp, SecureGreen)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "channel: $chatTitle • stored locally on device",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = SecureGreen,
                        )
                        Text(
                            text = "✕",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { showCipherInfo = false },
                        )
                    }
                }
            }

            // 2. Chat Area with Starfield & Moon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // Night Starfield
                PixelStarfield()

                // Crescent Moon on bottom right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 56.dp, bottom = 48.dp),
                ) {
                    PixelCrescentMoon()
                }

                // Message List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.type == MessageType.DISAPPEARING) {
                            DisappearingMessageBubble(
                                message = msg,
                                litEmbers = msg.embersRemaining,
                                onToggleReaction = { state.toggleReaction(activeChatId, msg.id) },
                                onOpenPhoto = { fullScreenPhotoPath = it },
                                onOpenFile = { path, mime -> openLocalFile(path, mime) },
                            )
                        } else if (msg.type == MessageType.VIEW_ONCE) {
                            ViewOnceCard(
                                message = msg,
                                onOpen = { state.revealViewOnce(activeChatId, msg.id) },
                                onOpenPhoto = { fullScreenPhotoPath = it },
                                onOpenFile = { path, mime -> openLocalFile(path, mime) },
                            )
                        } else {
                            StandardMessageBubble(
                                message = msg,
                                onToggleReaction = { state.toggleReaction(activeChatId, msg.id) },
                                onOpenPhoto = { fullScreenPhotoPath = it },
                                onOpenFile = { path, mime -> openLocalFile(path, mime) },
                            )
                        }
                    }

                    // Typing indicator row (flapping moth + pulsing 3 dots)
                    if (state.isContactTyping) {
                        item {
                            TypingIndicatorRow()
                        }
                    }
                }
            }

            // Attachment Error Banner
            if (attachmentError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .border(1.dp, WaxSealRed)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = attachmentError ?: "",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = WaxSealRed,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "✕",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                            modifier = Modifier
                                .clickable { attachmentError = null }
                                .padding(start = 8.dp),
                        )
                    }
                }
            }

            // Preparing Attachment Banner
            if (isPreparingAttachment) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "preparing attachment...",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = LanternAmber,
                    )
                }
            }

            // Pending Attachment Preview Strip
            pendingAttachment?.let { att ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (att.type == AttachmentType.PHOTO) {
                            AsyncImage(
                                model = File(att.localPath),
                                contentDescription = att.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(SteppedCornerShape(3.dp))
                                    .border(1.dp, LanternAmber, SteppedCornerShape(3.dp)),
                            )
                        } else {
                            SteppedBox(
                                modifier = Modifier.size(44.dp),
                                backgroundColor = MothBgDark,
                                borderColor = LanternAmber,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = att.name.substringAfterLast('.', "FILE").take(4).uppercase(),
                                        fontFamily = PixelifySans,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LanternAmber,
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.name,
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TheirBubbleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatFileSize(att.sizeBytes),
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = MutedText,
                            )
                        }

                        SteppedBox(
                            modifier = Modifier
                                .clickable { pendingAttachment = null }
                                .testTag("clear_attachment_button"),
                            backgroundColor = MothBgDark,
                            borderColor = WaxSealRed,
                            cutSize = 3.dp,
                            borderWidth = 1.dp,
                        ) {
                            Text(
                                text = "✕",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = WaxSealRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Attachment Picker Menu (Photo / File)
            AnimatedVisibility(visible = showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SteppedButton(
                        text = "🖼 photo",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_photo_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "📎 file",
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_file_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                    )
                }
            }

            // Send mode selector indicator (if user chose Disappearing or View Once)
            if (selectedSendType != MessageType.REGULAR) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (selectedSendType == MessageType.DISAPPEARING) "🔥 sending as burning message (10 embers)"
                            else "📜 sending as view-once sealed card",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = "cancel",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { selectedSendType = MessageType.REGULAR },
                        )
                    }
                }
            }

            // 3. Bottom Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothHeaderBg)
                    .border(width = 2.dp, color = MothDivider)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Attach toggle button (+)
                    SteppedBox(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { showAttachMenu = !showAttachMenu }
                            .testTag("attach_button"),
                        backgroundColor = MothBgDark,
                        borderColor = if (showAttachMenu || pendingAttachment != null) LanternAmber else TheirBubbleBorder,
                        cutSize = 4.dp,
                        borderWidth = 2.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (showAttachMenu) "✕" else "+",
                                fontFamily = PixelifySans,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showAttachMenu || pendingAttachment != null) LanternAmber else TheirBubbleText,
                            )
                        }
                    }

                    // Stepped-corner text field "say something"
                    SteppedBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        cutSize = 4.dp,
                        borderWidth = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = if (pendingAttachment != null) "add caption..." else "say something",
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send,
                                    ),
                                    keyboardActions = KeyboardActions(onSend = { handleSend() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("message_input_field"),
                                )
                            }

                            // Message Type Quick Toggles (disappearing ember or view once seal)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedSendType = if (selectedSendType == MessageType.DISAPPEARING) MessageType.REGULAR else MessageType.DISAPPEARING
                                        }
                                        .testTag("toggle_disappearing_button"),
                                )
                                Text(
                                    text = "📜",
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedSendType = if (selectedSendType == MessageType.VIEW_ONCE) MessageType.REGULAR else MessageType.VIEW_ONCE
                                        }
                                        .testTag("toggle_view_once_button"),
                                )
                            }
                        }
                    }

                    // Pixel wax-seal send button with pixel arrow
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = { handleSend() })
                            .testTag("send_message_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelWaxSeal(hasArrow = true)
                    }
                }
            }
        }

        // Fullscreen Photo Viewer Overlay
        fullScreenPhotoPath?.let { photoPath ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothBgDark.copy(alpha = 0.96f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable { fullScreenPhotoPath = null }
                    .padding(16.dp),
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Full screen photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                )
                SteppedButton(
                    text = "✕ close",
                    onClick = { fullScreenPhotoPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("close_photo_viewer_button"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    textColor = LanternAmber,
                    cutSize = 3.dp,
                )
            }
        }
    }
}

/**
 * Standard Message Bubble (Theirs on left in purple, Mine on right in amber).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandardMessageBubble(
    message: MothMessage,
    onToggleReaction: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        val bg = if (message.isMine) MyBubbleFill else TheirBubbleFill
        val border = if (message.isMine) MyBubbleBorder else TheirBubbleBorder
        val textColor = if (message.isMine) MyBubbleText else TheirBubbleText

        SteppedBox(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {
                        if (message.attachmentType == null) {
                            onToggleReaction()
                        } else if (message.attachmentType == AttachmentType.PHOTO && !message.attachmentLocalPath.isNullOrBlank()) {
                            onOpenPhoto(message.attachmentLocalPath)
                        } else if (message.attachmentType == AttachmentType.FILE && !message.attachmentLocalPath.isNullOrBlank()) {
                            onOpenFile(message.attachmentLocalPath, message.attachmentMimeType)
                        }
                    },
                    onLongClick = { onToggleReaction() },
                )
                .testTag("message_bubble_${message.id}"),
            backgroundColor = bg,
            borderColor = border,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            BubbleBodyContent(
                message = message,
                textColor = textColor,
                borderColor = border,
                onOpenPhoto = onOpenPhoto,
                onOpenFile = onOpenFile,
                onLongClick = onToggleReaction,
            )
        }

        ReactionChip(message = message, onToggleReaction = onToggleReaction)
    }
}

/**
 * Disappearing Message with row of 10 burning ember squares.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DisappearingMessageBubble(
    message: MothMessage,
    litEmbers: Int,
    onToggleReaction: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        val bg = if (message.isMine) MyBubbleFill else TheirBubbleFill
        val border = if (message.isMine) MyBubbleBorder else TheirBubbleBorder
        val textColor = if (message.isMine) MyBubbleText else TheirBubbleText

        SteppedBox(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {
                        if (message.attachmentType == null) {
                            onToggleReaction()
                        } else if (message.attachmentType == AttachmentType.PHOTO && !message.attachmentLocalPath.isNullOrBlank()) {
                            onOpenPhoto(message.attachmentLocalPath)
                        } else if (message.attachmentType == AttachmentType.FILE && !message.attachmentLocalPath.isNullOrBlank()) {
                            onOpenFile(message.attachmentLocalPath, message.attachmentMimeType)
                        }
                    },
                    onLongClick = { onToggleReaction() },
                )
                .testTag("message_bubble_${message.id}"),
            backgroundColor = bg,
            borderColor = border,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            BubbleBodyContent(
                message = message,
                textColor = textColor,
                borderColor = border,
                onOpenPhoto = onOpenPhoto,
                onOpenFile = onOpenFile,
                onLongClick = onToggleReaction,
            )
        }

        ReactionChip(message = message, onToggleReaction = onToggleReaction)

        Spacer(modifier = Modifier.height(6.dp))

        // Row of 10 ember squares + "burning down"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            DisappearingEmbers(total = 10, litCount = litEmbers)
            Text(
                text = "burning down",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
            )
        }
    }
}

/**
 * View-Once Card with pixel wax seal.
 */
@Composable
private fun ViewOnceCard(
    message: MothMessage,
    onOpen: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        SteppedBox(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { onOpen() }
                .testTag("view_once_card"),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelWaxSeal(hasArrow = false)

                    Column {
                        val title = when (message.viewOnceStatus) {
                            ViewOnceStatus.UNOPENED -> "view once"
                            ViewOnceStatus.REVEALED -> message.text.ifBlank {
                                if (message.attachmentType == AttachmentType.PHOTO) "photo revealed"
                                else message.attachmentName ?: "file revealed"
                            }
                            ViewOnceStatus.BURNED -> "message burned"
                        }
                        val subtitle = when (message.viewOnceStatus) {
                            ViewOnceStatus.UNOPENED -> "tap to open"
                            ViewOnceStatus.REVEALED -> "tap to seal & burn"
                            ViewOnceStatus.BURNED -> "sealed in ash"
                        }

                        Text(
                            text = title,
                            fontFamily = PixelifySans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (message.viewOnceStatus == ViewOnceStatus.BURNED) MutedText else TheirBubbleText,
                        )
                        Text(
                            text = subtitle,
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = if (message.viewOnceStatus == ViewOnceStatus.BURNED) WaxSealRed else MutedText,
                        )
                    }
                }

                if (message.viewOnceStatus == ViewOnceStatus.REVEALED && message.attachmentType != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AttachmentSection(
                        message = message,
                        textColor = TheirBubbleText,
                        borderColor = TheirBubbleBorder,
                        onOpenPhoto = onOpenPhoto,
                        onOpenFile = onOpenFile,
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleBodyContent(
    message: MothMessage,
    textColor: Color,
    borderColor: Color,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = if (message.attachmentType != null) 10.dp else 16.dp,
            vertical = 10.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.attachmentType != null) {
            AttachmentSection(
                message = message,
                textColor = textColor,
                borderColor = borderColor,
                onOpenPhoto = onOpenPhoto,
                onOpenFile = onOpenFile,
                onLongClick = onLongClick,
            )
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = textColor,
                modifier = if (message.attachmentType != null) Modifier.padding(horizontal = 4.dp) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentSection(
    message: MothMessage,
    textColor: Color,
    borderColor: Color,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onLongClick: () -> Unit,
) {
    val path = message.attachmentLocalPath
    val fileExists = !path.isNullOrBlank() && File(path).exists()

    if (message.attachmentType == AttachmentType.PHOTO) {
        if (fileExists && path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = message.attachmentName ?: "Photo attachment",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 220.dp)
                    .heightIn(min = 100.dp, max = 180.dp)
                    .clip(SteppedCornerShape(3.dp))
                    .border(1.dp, borderColor, SteppedCornerShape(3.dp))
                    .combinedClickable(
                        onClick = { onOpenPhoto(path) },
                        onLongClick = onLongClick,
                    )
                    .testTag("photo_attachment_${message.id}"),
            )
        } else {
            Text(
                text = "[photo unavailable]",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    } else if (message.attachmentType == AttachmentType.FILE) {
        val fileName = message.attachmentName ?: "attachment"
        val ext = fileName.substringAfterLast('.', "FILE").take(4).uppercase()
        SteppedBox(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 230.dp)
                .combinedClickable(
                    onClick = {
                        if (path != null) {
                            onOpenFile(path, message.attachmentMimeType)
                        }
                    },
                    onLongClick = onLongClick,
                )
                .testTag("file_attachment_${message.id}"),
            backgroundColor = MothBgDark.copy(alpha = 0.35f),
            borderColor = borderColor,
            cutSize = 3.dp,
            borderWidth = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SteppedBox(
                    modifier = Modifier.size(34.dp),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ext,
                            fontFamily = PixelifySans,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanternAmber,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatFileSize(message.attachmentSizeBytes)} • tap to open",
                        fontFamily = PixelifySans,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(
    message: MothMessage,
    onToggleReaction: () -> Unit,
) {
    if (message.reactionCount > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        SteppedBox(
            modifier = Modifier
                .clickable { onToggleReaction() }
                .padding(start = if (message.isMine) 0.dp else 4.dp, end = if (message.isMine) 4.dp else 0.dp)
                .testTag("reaction_chip_${message.id}"),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 3.dp,
            borderWidth = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PixelFirefly()
                Text(
                    text = "${message.reactionCount}",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = TheirBubbleText,
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    return if (kb >= 1.0) "${kb.toInt()} KB" else "$bytes B"
}

/**
 * Typing Indicator with flapping moth sprite and pulsing 3 dots.
 */
@Composable
private fun TypingIndicatorRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        // Flapping pixel moth
        PixelMoth(isAnimated = true)

        // Stepped bubble with 3 dots
        SteppedBox(
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 3.dp,
            borderWidth = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(delayMillis = 0)
                PulsingDot(delayMillis = 200)
                PulsingDot(delayMillis = 400)
            }
        }
    }
}

@Composable
private fun PulsingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )

    Box(
        modifier = Modifier
            .size(4.dp)
            .background(MutedText.copy(alpha = alpha)),
    )
}


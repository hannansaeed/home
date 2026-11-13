package com.example.chatapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val isGroup = activeConv?.isGroup == true
    val messages = state.activeMessages

    var draft by remember { mutableStateOf("") }
    var selectedSendType by remember { mutableStateOf(MessageType.REGULAR) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showStickerTray by remember { mutableStateOf(false) }
    var showPixelStickerMaker by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var isPreparingAttachment by remember { mutableStateOf(false) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    // Reply, Edit, Forward, Actions, Info, Search, Pin, Group modals
    var replyingToMessage by remember { mutableStateOf<MothMessage?>(null) }
    var editingMessage by remember { mutableStateOf<MothMessage?>(null) }
    var actionModalMessage by remember { mutableStateOf<MothMessage?>(null) }
    var infoModalMessage by remember { mutableStateOf<MothMessage?>(null) }
    var forwardModalMessage by remember { mutableStateOf<MothMessage?>(null) }
    var tappedReceiptMsgId by remember { mutableStateOf<String?>(null) }
    var showGroupInfoModal by remember { mutableStateOf(false) }

    // In-chat Search
    var showInChatSearch by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    var searchCursorIdx by remember { mutableIntStateOf(0) }

    // Camera capture URI holder
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVoiceCall by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val pinnedMessages = remember(messages) {
        messages.filter { it.isPinned && !it.isDeleted }
    }
    var pinnedBannerIdx by remember { mutableIntStateOf(0) }

    val searchMatches = remember(messages, inChatSearchQuery) {
        val q = inChatSearchQuery.trim()
        if (q.isBlank()) emptyList()
        else messages.mapIndexedNotNull { idx, msg ->
            if (!msg.isDeleted && msg.text.contains(q, ignoreCase = true)) idx else null
        }
    }

    LaunchedEffect(searchMatches.size) {
        searchCursorIdx = if (searchMatches.isNotEmpty()) searchMatches.lastIndex else 0
    }

    LaunchedEffect(searchCursorIdx, searchMatches) {
        if (searchMatches.isNotEmpty() && searchCursorIdx in searchMatches.indices) {
            listState.animateScrollToItem(searchMatches[searchCursorIdx])
        }
    }

    BackHandler {
        when {
            fullScreenPhotoPath != null -> fullScreenPhotoPath = null
            showPixelStickerMaker -> showPixelStickerMaker = false
            actionModalMessage != null -> actionModalMessage = null
            infoModalMessage != null -> infoModalMessage = null
            forwardModalMessage != null -> forwardModalMessage = null
            showGroupInfoModal -> showGroupInfoModal = false
            showInChatSearch -> {
                showInChatSearch = false
                inChatSearchQuery = ""
            }
            else -> onBack()
        }
    }

    // Broadcast real-time typing status
    LaunchedEffect(draft, activeChatId) {
        if (draft.isNotEmpty() && editingMessage == null) {
            state.updateMyTypingStatus(activeChatId, true)
            delay(3000L)
            state.updateMyTypingStatus(activeChatId, false)
        } else {
            state.updateMyTypingStatus(activeChatId, false)
        }
    }

    DisposableEffect(activeChatId) {
        onDispose {
            state.updateMyTypingStatus(activeChatId, false)
            state.voiceRecorder.cancelRecording()
            state.voicePlayer.stop()
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
                res.onSuccess { pendingAttachment = it }
                    .onFailure { attachmentError = it.localizedMessage ?: "failed to load photo" }
                isPreparingAttachment = false
            }
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            isPreparingAttachment = true
            attachmentError = null
            showAttachMenu = false
            coroutineScope.launch {
                val res = state.preparePhotoAttachment(uri)
                res.onSuccess { pendingAttachment = it }
                    .onFailure { attachmentError = it.localizedMessage ?: "failed to capture photo" }
                isPreparingAttachment = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val outUri = state.createTempCameraPhotoUri()
            pendingCameraUri = outUri
            cameraCaptureLauncher.launch(outUri)
        } else {
            attachmentError = "camera permission denied"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!state.voiceRecorder.startRecording()) {
                attachmentError = "unable to start microphone"
            }
        } else {
            attachmentError = "microphone permission denied"
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldCall = pendingVoiceCall
        pendingVoiceCall = false
        if (shouldCall && granted) {
            state.startCall(chatTitle, CallType.VOICE) { err -> attachmentError = err }
        } else if (shouldCall) {
            attachmentError = "microphone permission required for calling"
        }
    }

    fun launchCallFromChat() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            state.startCall(chatTitle, CallType.VOICE) { err -> attachmentError = err }
        } else {
            pendingVoiceCall = true
            callPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stickerPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isPreparingAttachment = true
            attachmentError = null
            coroutineScope.launch {
                val res = state.createPhotoSticker(uri)
                res.onFailure { err -> attachmentError = err.localizedMessage ?: "failed to create sticker" }
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
                res.onSuccess { pendingAttachment = it }
                    .onFailure { attachmentError = it.localizedMessage ?: "failed to load file" }
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
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "open attachment"))
        } catch (_: Exception) {
            attachmentError = "no app found to open this file"
        }
    }

    // Auto-scroll to bottom when messages or typing changes (unless user is navigating search results)
    LaunchedEffect(messages.size, state.isContactTyping) {
        if (!showInChatSearch) {
            val totalCount = messages.size + (if (state.isContactTyping) 1 else 0)
            if (totalCount > 0) {
                listState.scrollToItem(totalCount - 1)
                delay(40)
                listState.scrollToItem(totalCount - 1)
            }
        }
    }

    fun handleSend() {
        val editing = editingMessage
        if (editing != null) {
            val updatedText = draft.trim()
            if (updatedText.isNotBlank()) {
                state.editMessage(activeChatId, editing.id, updatedText)
            }
            editingMessage = null
            draft = ""
            return
        }

        if ((draft.isNotBlank() || pendingAttachment != null) && !isPreparingAttachment) {
            val textToSend = draft.trim()
            val typeToSend = selectedSendType
            val attachmentToSend = pendingAttachment
            val replyTarget = replyingToMessage

            draft = ""
            selectedSendType = MessageType.REGULAR
            pendingAttachment = null
            replyingToMessage = null
            showAttachMenu = false
            showStickerTray = false
            attachmentError = null

            state.sendMessage(
                chatId = activeChatId,
                text = textToSend,
                type = typeToSend,
                attachment = attachmentToSend,
                replyTo = replyTarget,
            )
            coroutineScope.launch {
                val countNow = state.activeMessages.size + (if (state.isContactTyping) 1 else 0)
                if (countNow > 0) listState.scrollToItem(countNow - 1)
                delay(60)
                val countAfter = state.activeMessages.size + (if (state.isContactTyping) 1 else 0)
                if (countAfter > 0) listState.scrollToItem(countAfter - 1)
            }
        }
    }

    // Group @mention autocomplete candidates
    val mentionQuery = remember(draft, isGroup) {
        if (!isGroup) null
        else {
            val lastToken = draft.substringAfterLast(' ')
            if (lastToken.startsWith("@")) lastToken.drop(1) else null
        }
    }
    val mentionCandidates = remember(mentionQuery, activeConv?.members) {
        if (mentionQuery == null || activeConv == null) emptyList()
        else activeConv.members.filter {
            !it.equals(state.currentUsername, ignoreCase = true) &&
                it.contains(mentionQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MothBgDark)
                .testTag("chat_screen"),
        ) {
            // 1. Header merged seamlessly with status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothHeaderBg)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left: Back + Avatar + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(onClick = onBack)
                                .testTag("chat_back_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelBackArrowIcon(
                                tint = LanternAmber,
                                modifier = Modifier.size(26.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable {
                                if (isGroup) showGroupInfoModal = true else onBack()
                            },
                        ) {
                            if (isGroup && activeConv != null) {
                                SteppedBox(
                                    modifier = Modifier.size(36.dp),
                                    backgroundColor = activeConv.avatarColor,
                                    borderColor = LanternAmber,
                                    cutSize = 3.dp,
                                    borderWidth = 1.dp,
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = activeConv.groupIcon, fontSize = 16.sp)
                                    }
                                }
                            } else {
                                UserAvatar(
                                    avatarPath = activeConv?.avatarLocalPath,
                                    color = activeConv?.avatarColor ?: LanternAmber,
                                    size = 36.dp,
                                )
                            }

                            Column {
                                Text(
                                    text = activeConv?.name ?: chatTitle,
                                    fontFamily = PixelifySans,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanternAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val presence = state.activeContactPresence
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.testTag("contact_presence_status"),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(if (presence.isOnline) SecureGreen else MutedText),
                                    )
                                    Text(
                                        text = if (isGroup) "tap for members • ${presence.label}" else presence.label,
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = if (presence.isOnline) SecureGreen else MutedText,
                                    )
                                }
                            }
                        }
                    }

                    // Right Actions: Voice Call (1:1) + In-Chat Search
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!isGroup && !chatTitle.equals("support", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { launchCallFromChat() }
                                    .testTag("chat_voice_call_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelPhoneIcon(
                                    tint = LanternAmber,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    showInChatSearch = !showInChatSearch
                                    if (!showInChatSearch) inChatSearchQuery = ""
                                }
                                .testTag("in_chat_search_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelSearchIcon(
                                tint = if (showInChatSearch) TheirBubbleText else LanternAmber,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

            // In-Chat Keyword Search Bar
            AnimatedVisibility(visible = showInChatSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedBox(
                        modifier = Modifier.weight(1f).height(34.dp),
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        cutSize = 3.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (inChatSearchQuery.isEmpty()) {
                                Text(
                                    text = "search messages...",
                                    fontFamily = PixelifySans,
                                    fontSize = 12.sp,
                                    color = PlaceholderText,
                                )
                            }
                            BasicTextField(
                                value = inChatSearchQuery,
                                onValueChange = { inChatSearchQuery = it },
                                textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                                cursorBrush = SolidColor(LanternAmber),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("in_chat_search_input"),
                            )
                        }
                    }
                    if (inChatSearchQuery.isNotBlank()) {
                        val counterText = if (searchMatches.isEmpty()) "0/0" else "${searchCursorIdx + 1}/${searchMatches.size}"
                        Text(text = counterText, fontFamily = PixelifySans, fontSize = 11.sp, color = LanternAmber)
                        Text(
                            text = "↑",
                            fontSize = 16.sp,
                            color = LanternAmber,
                            modifier = Modifier
                                .clickable {
                                    if (searchMatches.isNotEmpty()) {
                                        searchCursorIdx = if (searchCursorIdx > 0) searchCursorIdx - 1 else searchMatches.lastIndex
                                    }
                                }
                                .padding(horizontal = 4.dp),
                        )
                        Text(
                            text = "↓",
                            fontSize = 16.sp,
                            color = LanternAmber,
                            modifier = Modifier
                                .clickable {
                                    if (searchMatches.isNotEmpty()) {
                                        searchCursorIdx = (searchCursorIdx + 1) % searchMatches.size
                                    }
                                }
                                .padding(horizontal = 4.dp),
                        )
                    }
                    Text(
                        text = "✕",
                        fontSize = 13.sp,
                        color = MutedText,
                        modifier = Modifier
                            .clickable {
                                showInChatSearch = false
                                inChatSearchQuery = ""
                            }
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            // Pinned Message Top Banner
            if (pinnedMessages.isNotEmpty()) {
                val safeIdx = pinnedBannerIdx.coerceIn(0, pinnedMessages.lastIndex)
                val pinned = pinnedMessages[safeIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .border(1.dp, LanternAmber)
                        .clickable {
                            val idxInList = messages.indexOfFirst { it.id == pinned.id }
                            if (idxInList >= 0) {
                                coroutineScope.launch { listState.animateScrollToItem(idxInList) }
                            }
                            if (pinnedMessages.size > 1) {
                                pinnedBannerIdx = (safeIdx + 1) % pinnedMessages.size
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("pinned_message_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "📌", fontSize = 12.sp)
                        Text(
                            text = pinned.text.ifBlank { pinned.attachmentName ?: "pinned message" },
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = LanternAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "unpin",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                        modifier = Modifier
                            .clickable { state.togglePinMessage(activeChatId, pinned.id) }
                            .padding(start = 8.dp),
                    )
                }
            }

            // 2. Chat Area with Starfield & Moon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PixelStarfield()

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 56.dp, bottom = 48.dp),
                ) {
                    PixelCrescentMoon()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isSearchMatch = inChatSearchQuery.isNotBlank() &&
                            !msg.isDeleted && msg.text.contains(inChatSearchQuery.trim(), ignoreCase = true)

                        SwipeToReplyContainer(
                            enabled = !msg.isDeleted,
                            onSwipedToReply = { replyingToMessage = msg },
                        ) {
                            when (msg.type) {
                                MessageType.DISAPPEARING -> {
                                    DisappearingMessageBubble(
                                        message = msg,
                                        litEmbers = msg.embersRemaining,
                                        fontScale = state.messageFontScale,
                                        isGroup = isGroup,
                                        isReceiptExpanded = tappedReceiptMsgId == msg.id,
                                        highlightQuery = if (isSearchMatch) inChatSearchQuery.trim() else "",
                                        onTapBubble = {
                                            tappedReceiptMsgId = if (tappedReceiptMsgId == msg.id) null else msg.id
                                        },
                                        onLongPress = { actionModalMessage = msg },
                                        onToggleEmojiReaction = { emoji -> state.reactToMessage(activeChatId, msg.id, emoji) },
                                        onOpenPhoto = { fullScreenPhotoPath = it },
                                        onOpenFile = { path, mime -> openLocalFile(path, mime) },
                                        voicePlayer = state.voicePlayer,
                                    )
                                }
                                MessageType.VIEW_ONCE -> {
                                    ViewOnceCard(
                                        message = msg,
                                        onOpen = { state.revealViewOnce(activeChatId, msg.id) },
                                        onLongPress = { actionModalMessage = msg },
                                        onOpenPhoto = { fullScreenPhotoPath = it },
                                        onOpenFile = { path, mime -> openLocalFile(path, mime) },
                                    )
                                }
                                MessageType.BURNED_TRACE -> {
                                    BurnedTraceBubble(message = msg, fontScale = state.messageFontScale)
                                }
                                MessageType.REGULAR -> {
                                    StandardMessageBubble(
                                        message = msg,
                                        fontScale = state.messageFontScale,
                                        isGroup = isGroup,
                                        isReceiptExpanded = tappedReceiptMsgId == msg.id,
                                        highlightQuery = if (isSearchMatch) inChatSearchQuery.trim() else "",
                                        onTapBubble = {
                                            tappedReceiptMsgId = if (tappedReceiptMsgId == msg.id) null else msg.id
                                        },
                                        onLongPress = { actionModalMessage = msg },
                                        onToggleEmojiReaction = { emoji -> state.reactToMessage(activeChatId, msg.id, emoji) },
                                        onOpenPhoto = { fullScreenPhotoPath = it },
                                        onOpenFile = { path, mime -> openLocalFile(path, mime) },
                                        onJumpToReply = { replyId ->
                                            val idx = messages.indexOfFirst { it.id == replyId }
                                            if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
                                        },
                                        voicePlayer = state.voicePlayer,
                                    )
                                }
                            }
                        }
                    }

                    if (state.isContactTyping) {
                        item { TypingIndicatorRow() }
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
                            modifier = Modifier.clickable { attachmentError = null }.padding(start = 8.dp),
                        )
                    }
                }
            }

            if (isPreparingAttachment) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TheirBubbleFill)
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    Text(text = "preparing...", fontFamily = PixelifySans, fontSize = 11.sp, color = LanternAmber)
                }
            }

            // @Mention Autocomplete Strip (Groups)
            if (mentionCandidates.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(mentionCandidates) { member ->
                        SteppedBox(
                            modifier = Modifier.clickable {
                                val prefixBeforeAt = draft.substringBeforeLast("@")
                                draft = "$prefixBeforeAt@$member "
                            },
                            backgroundColor = MothBgDark,
                            borderColor = LanternAmber,
                            cutSize = 2.dp,
                            borderWidth = 1.dp,
                        ) {
                            Text(
                                text = "@$member",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = LanternAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Edit or Reply Banner above Input
            if (editingMessage != null) {
                val target = editingMessage!!
                InputModeBanner(
                    label = "✏ editing message",
                    preview = target.text,
                    onCancel = {
                        editingMessage = null
                        draft = ""
                    },
                )
            } else if (replyingToMessage != null) {
                val target = replyingToMessage!!
                val who = if (target.isMine) "you" else target.senderUsername
                InputModeBanner(
                    label = "↩ replying to @$who",
                    preview = target.text.ifBlank { target.attachmentName ?: "attachment" },
                    onCancel = { replyingToMessage = null },
                )
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
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (att.type == AttachmentType.VOICE) "MIC" else att.name.substringAfterLast('.', "FILE").take(4).uppercase(),
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

            // Custom Stickers Tray
            AnimatedVisibility(visible = showStickerTray) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "my stickers",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = LanternAmber,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteppedButton(
                                text = "+ draw",
                                onClick = { showPixelStickerMaker = true },
                                backgroundColor = TheirBubbleFill,
                                borderColor = LanternAmber,
                                textColor = LanternAmber,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                                modifier = Modifier.testTag("draw_sticker_button"),
                            )
                            SteppedButton(
                                text = "+ photo",
                                onClick = {
                                    stickerPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                backgroundColor = TheirBubbleFill,
                                borderColor = TheirBubbleBorder,
                                textColor = TheirBubbleText,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                                modifier = Modifier.testTag("add_photo_sticker_button"),
                            )
                        }
                    }

                    if (state.customStickers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "no stickers yet — tap + draw or + photo to create one",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = MutedText,
                            )
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.customStickers, key = { it.id }) { sticker ->
                                StickerTrayItem(
                                    sticker = sticker,
                                    onSend = {
                                        state.sendSticker(
                                            chatId = activeChatId,
                                            sticker = sticker,
                                            asViewOnce = selectedSendType == MessageType.VIEW_ONCE,
                                        )
                                        selectedSendType = MessageType.REGULAR
                                        showStickerTray = false
                                    },
                                    onDelete = { state.deleteSticker(sticker.id) },
                                )
                            }
                        }
                        Text(
                            text = "tap to send • hold to delete",
                            fontFamily = PixelifySans,
                            fontSize = 10.sp,
                            color = PlaceholderText,
                        )
                    }
                }
            }

            // + (More) Menu: Camera, Photo, File, View Once
            AnimatedVisibility(visible = showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SteppedButton(
                        text = "cam",
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val outUri = state.createTempCameraPhotoUri()
                                pendingCameraUri = outUri
                                cameraCaptureLauncher.launch(outUri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("camera_capture_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                        leadingIcon = { PixelCameraIcon(tint = TheirBubbleText) },
                    )
                    SteppedButton(
                        text = "photo",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("pick_photo_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                        leadingIcon = { PixelPhotoIcon(tint = TheirBubbleText) },
                    )
                    SteppedButton(
                        text = "file",
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).testTag("pick_file_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                        leadingIcon = { PixelFileIcon(tint = TheirBubbleText) },
                    )
                    SteppedButton(
                        text = "once",
                        onClick = {
                            selectedSendType = if (selectedSendType == MessageType.VIEW_ONCE) {
                                MessageType.REGULAR
                            } else {
                                MessageType.VIEW_ONCE
                            }
                            showAttachMenu = false
                        },
                        modifier = Modifier.weight(1f).testTag("toggle_view_once_button"),
                        backgroundColor = if (selectedSendType == MessageType.VIEW_ONCE) MyBubbleFill else TheirBubbleFill,
                        borderColor = if (selectedSendType == MessageType.VIEW_ONCE) MyBubbleBorder else TheirBubbleBorder,
                        textColor = if (selectedSendType == MessageType.VIEW_ONCE) MyBubbleText else TheirBubbleText,
                        cutSize = 3.dp,
                        leadingIcon = {
                            PixelSealIcon(
                                tint = if (selectedSendType == MessageType.VIEW_ONCE) MyBubbleText else TheirBubbleText
                            )
                        },
                    )
                }
            }

            if (selectedSendType == MessageType.VIEW_ONCE) {
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
                            text = "📜 sending as view-once sealed card",
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

            // 3. Bottom Input Bar (with Voice Note Recording mode & Sticker button)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothHeaderBg)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (state.voiceRecorder.isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(MothBgDark, SteppedCornerShape(4.dp))
                            .border(2.dp, WaxSealRed, SteppedCornerShape(4.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(WaxSealRed))
                            val sec = state.voiceRecorder.elapsedSec
                            Text(
                                text = "recording ${sec / 60}:${"%02d".format(sec % 60)}",
                                fontFamily = PixelifySans,
                                fontSize = 13.sp,
                                color = LanternAmber,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SteppedButton(
                                text = "✕",
                                onClick = { state.voiceRecorder.cancelRecording() },
                                backgroundColor = TheirBubbleFill,
                                borderColor = WaxSealRed,
                                textColor = WaxSealRed,
                                cutSize = 2.dp,
                            )
                            SteppedButton(
                                text = "✓ send",
                                onClick = {
                                    val result = state.voiceRecorder.stopRecording()
                                    if (result != null) {
                                        val (file, dur) = result
                                        coroutineScope.launch {
                                            val attRes = state.prepareVoiceAttachment(file, dur)
                                            attRes.onSuccess { att ->
                                                state.sendMessage(
                                                    chatId = activeChatId,
                                                    text = "",
                                                    attachment = att,
                                                    replyTo = replyingToMessage,
                                                )
                                                replyingToMessage = null
                                            }.onFailure { err ->
                                                attachmentError = err.localizedMessage ?: "voice note failed"
                                            }
                                        }
                                    }
                                },
                                backgroundColor = MyBubbleFill,
                                borderColor = MyBubbleBorder,
                                textColor = MyBubbleText,
                                cutSize = 2.dp,
                                modifier = Modifier.testTag("send_voice_note_button"),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // + (More) toggle button
                        SteppedBox(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    showAttachMenu = !showAttachMenu
                                    if (showAttachMenu) showStickerTray = false
                                }
                                .testTag("attach_button"),
                            backgroundColor = MothBgDark,
                            borderColor = if (showAttachMenu || pendingAttachment != null || selectedSendType == MessageType.VIEW_ONCE) LanternAmber else TheirBubbleBorder,
                            cutSize = 4.dp,
                            borderWidth = 2.dp,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (showAttachMenu) "✕" else "+",
                                    fontFamily = PixelifySans,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showAttachMenu || pendingAttachment != null || selectedSendType == MessageType.VIEW_ONCE) LanternAmber else TheirBubbleText,
                                )
                            }
                        }

                        // Text field + Sticker button + Voice Note button
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
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

                                // Sticker Picker Button
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable {
                                            showStickerTray = !showStickerTray
                                            if (showStickerTray) showAttachMenu = false
                                        }
                                        .testTag("sticker_picker_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelStickerIcon(tint = if (showStickerTray) LanternAmber else MutedText)
                                }

                                // Voice Note Button beside Stickers (outlined pixel mic)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable {
                                            showStickerTray = false
                                            showAttachMenu = false
                                            val granted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                if (!state.voiceRecorder.startRecording()) {
                                                    attachmentError = "unable to start microphone"
                                                }
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        .testTag("voice_note_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelMicIcon(
                                        tint = MutedText,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        // Pixel wax-seal send button
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
        }

        // Long-press Emoji Reaction & Message Action Modal
        actionModalMessage?.let { msg ->
            MessageReactionAndActionModal(
                message = msg,
                currentUsername = state.currentUsername,
                onDismiss = { actionModalMessage = null },
                onReact = { emoji ->
                    state.reactToMessage(activeChatId, msg.id, emoji)
                    actionModalMessage = null
                },
                onReply = {
                    replyingToMessage = msg
                    actionModalMessage = null
                },
                onForward = {
                    forwardModalMessage = msg
                    actionModalMessage = null
                },
                onEdit = {
                    editingMessage = msg
                    draft = msg.text
                    replyingToMessage = null
                    actionModalMessage = null
                },
                onTogglePin = {
                    state.togglePinMessage(activeChatId, msg.id)
                    actionModalMessage = null
                },
                onInfo = {
                    infoModalMessage = msg
                    actionModalMessage = null
                },
                onDeleteForMe = {
                    state.deleteMessageForMe(activeChatId, msg.id)
                    actionModalMessage = null
                },
                onDeleteForEveryone = {
                    state.deleteMessageForEveryone(activeChatId, msg.id)
                    actionModalMessage = null
                },
            )
        }

        // Message Info (WhatsApp-style timestamps) Modal
        infoModalMessage?.let { msg ->
            MessageInfoModal(message = msg, onDismiss = { infoModalMessage = null })
        }

        // Forward Target Picker Modal
        forwardModalMessage?.let { msg ->
            ForwardTargetModal(
                conversations = state.conversations,
                onSelect = { target ->
                    state.forwardMessage(msg, target)
                    forwardModalMessage = null
                },
                onDismiss = { forwardModalMessage = null },
            )
        }

        // Group Info & Admin Modal
        if (showGroupInfoModal && activeConv != null && activeConv.isGroup) {
            GroupInfoModal(
                state = state,
                conversation = activeConv,
                onDismiss = { showGroupInfoModal = false },
            )
        }

        // Pixel Sticker Maker Modal
        if (showPixelStickerMaker) {
            PixelStickerMakerDialog(
                onDismiss = { showPixelStickerMaker = false },
                onSave = { label, grid ->
                    coroutineScope.launch {
                        val res = state.createPixelSticker(label, grid, 10)
                        res.onSuccess { showPixelStickerMaker = false }
                            .onFailure { attachmentError = it.localizedMessage ?: "failed to save sticker" }
                    }
                },
            )
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
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                )
                SteppedButton(
                    text = "✕ close",
                    onClick = { fullScreenPhotoPath = null },
                    modifier = Modifier.align(Alignment.TopEnd).testTag("close_photo_viewer_button"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    textColor = LanternAmber,
                    cutSize = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun SwipeToReplyContainer(
    enabled: Boolean,
    onSwipedToReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                dragX = 0f
                                triggered = false
                            },
                            onDragCancel = {
                                dragX = 0f
                                triggered = false
                            },
                            onHorizontalDrag = { _, delta ->
                                dragX = (dragX + delta).coerceIn(-120f, 120f)
                                if (!triggered && kotlin.math.abs(dragX) > 75f) {
                                    triggered = true
                                    onSwipedToReply()
                                }
                            },
                        )
                    }
                } else Modifier
            )
            .offset { IntOffset(dragX.roundToInt(), 0) },
    ) {
        content()
    }
}

@Composable
private fun InputModeBanner(
    label: String,
    preview: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TheirBubbleFill)
            .border(1.dp, LanternAmber)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = LanternAmber)
            Text(text = preview, fontFamily = PixelifySans, fontSize = 12.sp, color = TheirBubbleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = "✕",
            fontFamily = PixelifySans,
            fontSize = 13.sp,
            color = MutedText,
            modifier = Modifier.clickable(onClick = onCancel).padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTrayItem(
    sticker: MothSticker,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    SteppedBox(
        modifier = Modifier
            .width(68.dp)
            .combinedClickable(onClick = onSend, onLongClick = onDelete)
            .testTag("sticker_item_${sticker.id}"),
        backgroundColor = MothBgDark,
        borderColor = TheirBubbleBorder,
        cutSize = 3.dp,
        borderWidth = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AsyncImage(
                model = File(sticker.imageLocalPath),
                contentDescription = sticker.label,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = sticker.label,
                fontFamily = PixelifySans,
                fontSize = 9.sp,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PixelStickerMakerDialog(
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> Unit,
) {
    val gridSize = 10
    val cells = remember { mutableStateListOf<Int>().apply { repeat(gridSize * gridSize) { add(0) } } }
    var selectedColor by remember { mutableIntStateOf(LanternAmber.toArgb()) }
    var label by remember { mutableStateOf("") }

    val palette = listOf(
        LanternAmber, LanternGlow, WaxSealRed, SecureGreen,
        TheirBubbleText, TheirBubbleBorder, Color(0xFF70D6FF), Color(0xFF1A1633),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1A1633))
            .clickable { onDismiss() }
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}.testTag("pixel_sticker_maker"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("draw pixel sticker", fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LanternAmber)
                    Text("✕", fontSize = 14.sp, color = MutedText, modifier = Modifier.clickable { onDismiss() })
                }

                SteppedBox(
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (label.isEmpty()) {
                            Text("sticker name (e.g. happy moth)", fontFamily = PixelifySans, fontSize = 12.sp, color = PlaceholderText)
                        }
                        BasicTextField(
                            value = label,
                            onValueChange = { label = it },
                            textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.background(MothBgDark).border(2.dp, MothDivider).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (r in 0 until gridSize) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (c in 0 until gridSize) {
                                val idx = r * gridSize + c
                                val cellArgb = cells[idx]
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(if (cellArgb == 0) Color(0xFF231E4A) else Color(cellArgb))
                                        .clickable { cells[idx] = if (cells[idx] == selectedColor) 0 else selectedColor },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        palette.forEach { color ->
                            val argb = color.toArgb()
                            val isSelected = selectedColor == argb
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color, SteppedCornerShape(2.dp))
                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else MothDivider, SteppedCornerShape(2.dp))
                                    .clickable { selectedColor = argb },
                            )
                        }
                    }
                    SteppedButton(
                        text = "erase",
                        onClick = { selectedColor = 0 },
                        backgroundColor = if (selectedColor == 0) WaxSealRed else MothBgDark,
                        borderColor = if (selectedColor == 0) WaxSealRed else MothDivider,
                        textColor = if (selectedColor == 0) Color.White else MutedText,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SteppedButton(
                        text = "clear",
                        onClick = { for (i in cells.indices) cells[i] = 0 },
                        backgroundColor = TheirBubbleFill,
                        borderColor = MothDivider,
                        textColor = TheirBubbleText,
                        modifier = Modifier.weight(1f),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "save sticker",
                        onClick = { if (cells.any { it != 0 }) onSave(label, cells.toList()) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandardMessageBubble(
    message: MothMessage,
    fontScale: Float,
    isGroup: Boolean,
    isReceiptExpanded: Boolean,
    highlightQuery: String,
    onTapBubble: () -> Unit,
    onLongPress: () -> Unit,
    onToggleEmojiReaction: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onJumpToReply: (String) -> Unit,
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        if (!message.isDeleted && message.attachmentType == AttachmentType.STICKER && message.text.isBlank()) {
            val path = message.attachmentLocalPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = message.attachmentName ?: "Sticker",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .size(124.dp)
                        .combinedClickable(onClick = onTapBubble, onLongClick = onLongPress)
                        .testTag("sticker_bubble_${message.id}"),
                )
            } else {
                Text("[sticker]", fontFamily = PixelifySans, fontSize = 12.sp, color = MutedText)
            }
        } else {
            val bg = if (message.isDeleted) MothHeaderBg else if (message.isMine) MyBubbleFill else TheirBubbleFill
            val border = when {
                highlightQuery.isNotBlank() -> LanternAmber
                message.isDeleted -> MothDivider
                message.isMine -> MyBubbleBorder
                else -> TheirBubbleBorder
            }
            val textColor = if (message.isDeleted) MutedText else if (message.isMine) MyBubbleText else TheirBubbleText

            SteppedBox(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {
                            if (message.isDeleted) return@combinedClickable
                            when (message.attachmentType) {
                                AttachmentType.PHOTO -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenPhoto(message.attachmentLocalPath) else onTapBubble()
                                AttachmentType.FILE -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenFile(message.attachmentLocalPath, message.attachmentMimeType) else onTapBubble()
                                AttachmentType.VOICE -> if (!message.attachmentLocalPath.isNullOrBlank()) voicePlayer.togglePlay(message.id, message.attachmentLocalPath) else onTapBubble()
                                else -> onTapBubble()
                            }
                        },
                        onLongClick = onLongPress,
                    )
                    .testTag("message_bubble_${message.id}"),
                backgroundColor = bg,
                borderColor = border,
                cutSize = 4.dp,
                borderWidth = 2.dp,
            ) {
                BubbleBodyContent(
                    message = message,
                    fontScale = fontScale,
                    isGroup = isGroup,
                    textColor = textColor,
                    borderColor = border,
                    highlightQuery = highlightQuery,
                    onOpenPhoto = onOpenPhoto,
                    onOpenFile = onOpenFile,
                    onLongPress = onLongPress,
                    onJumpToReply = onJumpToReply,
                    voicePlayer = voicePlayer,
                )
            }
        }

        ReactionsChipRow(message = message, onToggleEmojiReaction = onToggleEmojiReaction)

        if (isReceiptExpanded) {
            InlineReceiptLine(message = message)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DisappearingMessageBubble(
    message: MothMessage,
    litEmbers: Int,
    fontScale: Float,
    isGroup: Boolean,
    isReceiptExpanded: Boolean,
    highlightQuery: String,
    onTapBubble: () -> Unit,
    onLongPress: () -> Unit,
    onToggleEmojiReaction: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        val bg = if (message.isMine) MyBubbleFill else TheirBubbleFill
        val border = if (highlightQuery.isNotBlank()) LanternAmber else if (message.isMine) MyBubbleBorder else TheirBubbleBorder
        val textColor = if (message.isMine) MyBubbleText else TheirBubbleText

        SteppedBox(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {
                        when (message.attachmentType) {
                            AttachmentType.PHOTO -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenPhoto(message.attachmentLocalPath) else onTapBubble()
                            AttachmentType.FILE -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenFile(message.attachmentLocalPath, message.attachmentMimeType) else onTapBubble()
                            AttachmentType.VOICE -> if (!message.attachmentLocalPath.isNullOrBlank()) voicePlayer.togglePlay(message.id, message.attachmentLocalPath) else onTapBubble()
                            else -> onTapBubble()
                        }
                    },
                    onLongClick = onLongPress,
                )
                .testTag("message_bubble_${message.id}"),
            backgroundColor = bg,
            borderColor = border,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            BubbleBodyContent(
                message = message,
                fontScale = fontScale,
                isGroup = isGroup,
                textColor = textColor,
                borderColor = border,
                highlightQuery = highlightQuery,
                onOpenPhoto = onOpenPhoto,
                onOpenFile = onOpenFile,
                onLongPress = onLongPress,
                onJumpToReply = {},
                voicePlayer = voicePlayer,
            )
        }

        ReactionsChipRow(message = message, onToggleEmojiReaction = onToggleEmojiReaction)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            DisappearingEmbers(total = 10, litCount = litEmbers)
            Text(
                text = if (message.leaveTrace) "burning • leaves ash" else "burning down",
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = MutedText,
            )
        }

        if (isReceiptExpanded) {
            InlineReceiptLine(message = message)
        }
    }
}

@Composable
private fun InlineReceiptLine(message: MothMessage) {
    val label = if (!message.isMine) {
        "received • ${message.time}"
    } else {
        val statusWord = when (message.status) {
            MessageStatus.SENDING -> "sending..."
            MessageStatus.SENT -> "sent"
            MessageStatus.DELIVERED -> "delivered"
            MessageStatus.READ -> "seen"
        }
        val tsMs = when (message.status) {
            MessageStatus.READ -> message.readAt.takeIf { it > 0L } ?: message.sentAt
            MessageStatus.DELIVERED -> message.deliveredAt.takeIf { it > 0L } ?: message.sentAt
            else -> message.sentAt
        }
        val tsLabel = if (tsMs > 0L) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(tsMs)).lowercase()
        } else message.time
        if (message.status == MessageStatus.SENDING) "sending..." else "$statusWord • $tsLabel"
    }
    Text(
        text = label,
        fontFamily = PixelifySans,
        fontSize = 10.sp,
        color = if (message.status == MessageStatus.READ) SecureGreen else MutedText,
        modifier = Modifier
            .padding(top = 3.dp, start = 4.dp, end = 4.dp)
            .testTag("inline_receipt_${message.id}"),
    )
}

@Composable
private fun BurnedTraceBubble(message: MothMessage, fontScale: Float) {
    var isRevealed by remember(message.id) { mutableStateOf(false) }
    val canPeek = message.isMine && message.text.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        if (isRevealed && canPeek) {
            SteppedBox(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .alpha(0.45f)
                    .clickable { isRevealed = false }
                    .testTag("burned_trace_revealed_${message.id}"),
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                cutSize = 4.dp,
                borderWidth = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = message.text,
                        fontFamily = PixelifySans,
                        fontSize = (15f * fontScale).sp,
                        lineHeight = (20f * fontScale).sp,
                        color = MyBubbleText,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = "✦", fontSize = 10.sp, color = WaxSealRed)
                        Text(
                            text = "ash memory • tap to hide",
                            fontFamily = PixelifySans,
                            fontSize = 10.sp,
                            color = MyBubbleText.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        } else {
            SteppedBox(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .then(if (canPeek) Modifier.defaultMinSize(minHeight = 48.dp).clickable { isRevealed = true } else Modifier)
                    .testTag("burned_trace_${message.id}"),
                backgroundColor = MothHeaderBg,
                borderColor = MothDivider,
                cutSize = 4.dp,
                borderWidth = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "✦", fontSize = 12.sp, color = WaxSealRed)
                    Column {
                        Text(text = "a burning message turned to ash", fontFamily = PixelifySans, fontSize = 12.sp, color = MutedText)
                        if (canPeek) {
                            Text(text = "tap trace to peek", fontFamily = PixelifySans, fontSize = 10.sp, color = PlaceholderText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewOnceCard(
    message: MothMessage,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
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
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
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
                                when (message.attachmentType) {
                                    AttachmentType.PHOTO -> "photo revealed"
                                    AttachmentType.STICKER -> "sticker revealed"
                                    AttachmentType.VOICE -> "voice note revealed"
                                    else -> message.attachmentName ?: "file revealed"
                                }
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
                        onLongPress = onLongPress,
                        voicePlayer = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleBodyContent(
    message: MothMessage,
    fontScale: Float,
    isGroup: Boolean,
    textColor: Color,
    borderColor: Color,
    highlightQuery: String,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onLongPress: () -> Unit,
    onJumpToReply: (String) -> Unit,
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = if (message.attachmentType != null && !message.isDeleted) 10.dp else 14.dp,
            vertical = 9.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (message.isDeleted) {
            Text(
                text = "🚫 this message was deleted",
                fontFamily = PixelifySans,
                fontStyle = FontStyle.Italic,
                fontSize = (13f * fontScale).sp,
                color = textColor,
            )
            return@Column
        }

        if (message.isForwarded) {
            Text(
                text = "↪ forwarded",
                fontFamily = PixelifySans,
                fontStyle = FontStyle.Italic,
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.7f),
            )
        }

        if (isGroup && !message.isMine) {
            Text(
                text = "@${message.senderUsername}",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = LanternAmber,
            )
        }

        if (!message.replyToId.isNullOrBlank() && !message.replyToPreview.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothBgDark.copy(alpha = 0.25f))
                    .border(1.dp, borderColor.copy(alpha = 0.6f))
                    .clickable { onJumpToReply(message.replyToId) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Column {
                    Text(
                        text = "@${message.replyToSender ?: "lantern"}",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (message.isMine) MyBubbleText.copy(alpha = 0.85f) else LanternAmber,
                    )
                    Text(
                        text = message.replyToPreview,
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (message.attachmentType != null) {
            AttachmentSection(
                message = message,
                textColor = textColor,
                borderColor = borderColor,
                onOpenPhoto = onOpenPhoto,
                onOpenFile = onOpenFile,
                onLongPress = onLongPress,
                voicePlayer = voicePlayer,
            )
        }

        if (message.text.isNotBlank()) {
            Text(
                text = styledMessageText(message.text, message.isMine, highlightQuery),
                fontFamily = PixelifySans,
                fontSize = (14f * fontScale).sp,
                lineHeight = (19f * fontScale).sp,
                color = textColor,
                modifier = if (message.attachmentType != null) Modifier.padding(horizontal = 4.dp) else Modifier,
            )
        }

        if (message.isPinned || message.isEdited) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.isPinned) {
                    Text(text = "📌", fontSize = 10.sp)
                }
                if (message.isEdited) {
                    Text(
                        text = "edited",
                        fontFamily = PixelifySans,
                        fontStyle = FontStyle.Italic,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private fun styledMessageText(text: String, isMine: Boolean, highlightQuery: String): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        // Highlight @mentions
        val mentionRegex = Regex("@[a-zA-Z0-9_]+")
        mentionRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = if (isMine) MothBgDark else LanternAmber,
                ),
                start = match.range.first,
                end = match.range.last + 1,
            )
        }
        // Highlight active search query
        if (highlightQuery.isNotBlank()) {
            var startIdx = text.indexOf(highlightQuery, ignoreCase = true)
            while (startIdx >= 0) {
                addStyle(
                    SpanStyle(
                        background = LanternGlow,
                        color = MothBgDark,
                        fontWeight = FontWeight.Bold,
                    ),
                    start = startIdx,
                    end = (startIdx + highlightQuery.length).coerceAtMost(text.length),
                )
                startIdx = text.indexOf(highlightQuery, startIdx + highlightQuery.length, ignoreCase = true)
            }
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
    onLongPress: () -> Unit,
    voicePlayer: VoiceNotePlayer?,
) {
    val path = message.attachmentLocalPath
    val fileExists = !path.isNullOrBlank() && File(path).exists()

    when (message.attachmentType) {
        AttachmentType.PHOTO, AttachmentType.STICKER -> {
            if (fileExists && path != null) {
                AsyncImage(
                    model = File(path),
                    contentDescription = message.attachmentName ?: "Image attachment",
                    contentScale = if (message.attachmentType == AttachmentType.STICKER) ContentScale.Fit else ContentScale.Crop,
                    filterQuality = if (message.attachmentType == AttachmentType.STICKER) FilterQuality.None else FilterQuality.Low,
                    modifier = Modifier
                        .widthIn(min = 110.dp, max = 220.dp)
                        .heightIn(min = 100.dp, max = 180.dp)
                        .clip(SteppedCornerShape(3.dp))
                        .border(1.dp, borderColor, SteppedCornerShape(3.dp))
                        .combinedClickable(onClick = { onOpenPhoto(path) }, onLongClick = onLongPress)
                        .testTag("photo_attachment_${message.id}"),
                )
            } else {
                Text("[image unavailable]", fontFamily = PixelifySans, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
            }
        }
        AttachmentType.VOICE -> {
            val isPlaying = voicePlayer?.playingMessageId == message.id
            val dur = message.audioDurationSec
            val durLabel = "${dur / 60}:${"%02d".format(dur % 60)}"
            SteppedBox(
                modifier = Modifier
                    .widthIn(min = 175.dp, max = 220.dp)
                    .combinedClickable(
                        onClick = { if (fileExists && path != null) voicePlayer?.togglePlay(message.id, path) },
                        onLongClick = onLongPress,
                    )
                    .testTag("voice_attachment_${message.id}"),
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
                        modifier = Modifier.size(30.dp),
                        backgroundColor = LanternAmber,
                        borderColor = MyBubbleBorder,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                fontSize = 12.sp,
                                color = MothBgDark,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(MothDivider),
                        ) {
                            val frac = if (isPlaying) (voicePlayer?.progress ?: 0f) else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(LanternAmber),
                            )
                        }
                        Text(
                            text = if (isPlaying) "playing • $durLabel" else "voice • $durLabel",
                            fontFamily = PixelifySans,
                            fontSize = 10.sp,
                            color = textColor,
                        )
                    }
                }
            }
        }
        AttachmentType.FILE -> {
            val fileName = message.attachmentName ?: "attachment"
            val ext = fileName.substringAfterLast('.', "FILE").take(4).uppercase()
            SteppedBox(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 230.dp)
                    .combinedClickable(
                        onClick = { if (path != null) onOpenFile(path, message.attachmentMimeType) },
                        onLongClick = onLongPress,
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(ext, fontFamily = PixelifySans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LanternAmber)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fileName, fontFamily = PixelifySans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatFileSize(message.attachmentSizeBytes)} • tap to open", fontFamily = PixelifySans, fontSize = 10.sp, color = textColor.copy(alpha = 0.75f))
                    }
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun ReactionsChipRow(
    message: MothMessage,
    onToggleEmojiReaction: (String) -> Unit,
) {
    val entries = message.reactions.entries.filter { it.value.isNotEmpty() }
    if (entries.isEmpty() && message.reactionCount <= 0) return

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = if (message.isMine) 0.dp else 4.dp, end = if (message.isMine) 4.dp else 0.dp),
    ) {
        if (entries.isNotEmpty()) {
            entries.forEach { (emoji, users) ->
                SteppedBox(
                    modifier = Modifier
                        .clickable { onToggleEmojiReaction(emoji) }
                        .testTag("reaction_chip_${message.id}_$emoji"),
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = emoji, fontSize = 12.sp)
                        Text(text = "${users.size}", fontFamily = PixelifySans, fontSize = 11.sp, color = TheirBubbleText)
                    }
                }
            }
        } else {
            SteppedBox(
                modifier = Modifier
                    .clickable { onToggleEmojiReaction("🔥") }
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
                    Text(text = "${message.reactionCount}", fontFamily = PixelifySans, fontSize = 12.sp, color = TheirBubbleText)
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    return if (kb >= 1.0) "${kb.toInt()} KB" else "$bytes B"
}

@Composable
private fun TypingIndicatorRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        PixelMoth(isAnimated = true)
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
    Box(modifier = Modifier.size(4.dp).background(MutedText.copy(alpha = alpha)))
}

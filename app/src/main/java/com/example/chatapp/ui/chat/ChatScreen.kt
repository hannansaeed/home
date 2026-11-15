package com.example.chatapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.viewinterop.AndroidView
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
    var pendingViewOnce by remember { mutableStateOf(false) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showStickerTray by remember { mutableStateOf(false) }
    var showPixelStickerMaker by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var isPreparingAttachment by remember { mutableStateOf(false) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoPath by remember { mutableStateOf<String?>(null) }
    var viewingViewOnceMessage by remember { mutableStateOf<MothMessage?>(null) }
    var pendingStickerCropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingStickerCropLabel by remember { mutableStateOf("") }
    var pendingSafSavePath by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    // Reply, Edit, Forward, Actions, Info, Delete, Search, Profile modals
    var replyingToMessage by remember { mutableStateOf<MothMessage?>(null) }
    var editingMessage by remember { mutableStateOf<MothMessage?>(null) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEditReactionsDialog by remember { mutableStateOf(false) }
    var infoModalMessage by remember { mutableStateOf<MothMessage?>(null) }
    var forwardModalMessages by remember { mutableStateOf<List<MothMessage>>(emptyList()) }
    var deleteTargetMessages by remember { mutableStateOf<List<MothMessage>>(emptyList()) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var tappedReceiptMsgId by remember { mutableStateOf<String?>(null) }
    var showGroupInfoModal by remember { mutableStateOf(false) }
    var showContactProfileOverlay by remember { mutableStateOf(false) }

    val selectedMessages = remember(messages, selectedMessageIds) {
        messages.filter { it.id in selectedMessageIds }
    }
    LaunchedEffect(messages) {
        val validIds = messages.map { it.id }.toSet()
        val pruned = selectedMessageIds.intersect(validIds)
        if (pruned != selectedMessageIds) {
            selectedMessageIds = pruned
        }
    }

    val hasDisappearingMessages = remember(messages) {
        messages.any { it.type == MessageType.DISAPPEARING }
    }
    LaunchedEffect(activeChatId, hasDisappearingMessages) {
        if (hasDisappearingMessages) {
            while (true) {
                state.tickBurningMessagesNow()
                delay(500L)
            }
        }
    }

    LaunchedEffect(actionNotice) {
        if (actionNotice != null) {
            delay(2200L)
            actionNotice = null
        }
    }

    // In-chat Search
    var showInChatSearch by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    var searchCursorIdx by remember { mutableIntStateOf(0) }

    // Camera capture URI holder
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVoiceCall by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(1450L)
            highlightedMessageId = null
        }
    }

    fun jumpToMessage(targetId: String) {
        val idx = messages.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        coroutineScope.launch {
            highlightedMessageId = null
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
            if (visible != null) {
                val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
                val itemCenter = visible.offset + visible.size / 2
                val delta = (itemCenter - viewportCenter).toFloat()
                if (kotlin.math.abs(delta) > 6f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    )
                }
            } else {
                val anchorIdx = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: idx
                val step = kotlin.math.abs(idx - anchorIdx)
                if (step > 25) {
                    val preIdx = if (idx < anchorIdx) idx + 15 else (idx - 15).coerceAtLeast(0)
                    listState.scrollToItem(preIdx)
                }
                listState.animateScrollToItem(idx)
                val postLayout = listState.layoutInfo
                val targetVisible = postLayout.visibleItemsInfo.firstOrNull { it.index == idx }
                if (targetVisible != null && postLayout.viewportSize.height > 0) {
                    val viewportCenter = postLayout.viewportStartOffset + postLayout.viewportSize.height / 2
                    val itemCenter = targetVisible.offset + targetVisible.size / 2
                    val delta = (itemCenter - viewportCenter).toFloat()
                    if (kotlin.math.abs(delta) > 6f) {
                        listState.animateScrollBy(
                            value = delta,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }
            highlightedMessageId = targetId
        }
    }

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
            viewingViewOnceMessage != null -> {
                viewingViewOnceMessage?.let { vo ->
                    state.markViewOnceOpenedAndBurn(activeChatId, vo.id)
                }
                viewingViewOnceMessage = null
            }
            pendingStickerCropUri != null -> pendingStickerCropUri = null
            fullScreenPhotoPath != null -> fullScreenPhotoPath = null
            fullScreenVideoPath != null -> fullScreenVideoPath = null
            showPixelStickerMaker -> showPixelStickerMaker = false
            showEditReactionsDialog -> showEditReactionsDialog = false
            deleteTargetMessages.isNotEmpty() -> deleteTargetMessages = emptyList()
            selectedMessageIds.isNotEmpty() -> selectedMessageIds = emptySet()
            infoModalMessage != null -> infoModalMessage = null
            forwardModalMessages.isNotEmpty() -> forwardModalMessages = emptyList()
            showGroupInfoModal -> showGroupInfoModal = false
            showContactProfileOverlay -> showContactProfileOverlay = false
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
                val res = state.prepareMediaAttachment(uri)
                res.onSuccess { pendingAttachment = it }
                    .onFailure { attachmentError = it.localizedMessage ?: "failed to load media" }
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
            attachmentError = null
            pendingStickerCropLabel = state.resolveUriDisplayName(uri)
                .substringBeforeLast('.')
                .take(24)
                .ifBlank { "sticker" }
            pendingStickerCropUri = uri
        }
    }

    val safSaveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val srcPath = pendingSafSavePath
        pendingSafSavePath = null
        if (destUri != null && srcPath != null) {
            coroutineScope.launch {
                val res = state.writeLocalFileToUri(srcPath, destUri)
                res.onSuccess { actionNotice = "saved to device storage" }
                    .onFailure { attachmentError = it.localizedMessage ?: "failed to save media" }
            }
        }
    }

    fun saveMediaFromChat(
        localPath: String,
        attachmentType: AttachmentType?,
        mimeType: String?,
        fileName: String?,
    ) {
        coroutineScope.launch {
            val res = state.saveMediaToDevice(localPath, attachmentType, mimeType, fileName)
            res.onSuccess { msg ->
                actionNotice = msg
            }.onFailure { err ->
                if (err is SafExportRequiredException) {
                    pendingSafSavePath = localPath
                    safSaveDocumentLauncher.launch(err.fileName)
                } else {
                    attachmentError = err.localizedMessage ?: "failed to save media"
                }
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
            val attachmentToSend = pendingAttachment
            val typeToSend = if (pendingViewOnce && attachmentToSend != null) MessageType.VIEW_ONCE else MessageType.REGULAR
            val replyTarget = replyingToMessage

            draft = ""
            pendingViewOnce = false
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
            // 1. Header (Normal Contact Bar OR Contextual WhatsApp-style Action Bar when message selected)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothHeaderBg)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (selectedMessages.isNotEmpty()) {
                    val singleSelected = selectedMessages.singleOrNull()
                    val nonDeletedSelected = selectedMessages.filter { !it.isDeleted }
                    val allStarred = nonDeletedSelected.isNotEmpty() && nonDeletedSelected.all { it.isStarred }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contextual_action_bar"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { selectedMessageIds = emptySet() }
                                    .testTag("contextual_close_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelBackArrowIcon(tint = LanternAmber, modifier = Modifier.size(24.dp))
                            }
                            Text(
                                text = "${selectedMessages.size}",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = LanternAmber,
                                modifier = Modifier.testTag("contextual_selected_count"),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (nonDeletedSelected.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable {
                                            state.setMessagesStarred(
                                                activeChatId,
                                                nonDeletedSelected.map { it.id },
                                                !allStarred,
                                            )
                                            selectedMessageIds = emptySet()
                                        }
                                        .testTag("action_star_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelStarIcon(tint = LanternAmber, filled = allStarred, modifier = Modifier.size(18.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable {
                                            forwardModalMessages = nonDeletedSelected
                                            selectedMessageIds = emptySet()
                                        }
                                        .testTag("action_forward_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelForwardIcon(tint = TheirBubbleText, modifier = Modifier.size(18.dp))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        deleteTargetMessages = selectedMessages
                                        selectedMessageIds = emptySet()
                                    }
                                    .testTag("action_delete_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelTrashIcon(tint = WaxSealRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left: Back + Avatar + Title (Avatar/Name opens Contact Profile or Group Info)
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
                                modifier = Modifier
                                    .clickable {
                                        if (isGroup) showGroupInfoModal = true else showContactProfileOverlay = true
                                    }
                                    .testTag("chat_header_profile_button"),
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    if (searchMatches.isNotEmpty()) {
                                        searchCursorIdx = if (searchCursorIdx > 0) searchCursorIdx - 1 else searchMatches.lastIndex
                                    }
                                }
                                .testTag("in_chat_search_prev"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelArrowUpIcon(tint = LanternAmber, modifier = Modifier.size(18.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    if (searchMatches.isNotEmpty()) {
                                        searchCursorIdx = (searchCursorIdx + 1) % searchMatches.size
                                    }
                                }
                                .testTag("in_chat_search_next"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelArrowDownIcon(tint = LanternAmber, modifier = Modifier.size(18.dp))
                        }
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
                            jumpToMessage(pinned.id)
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
                        PixelPinIcon(tint = LanternAmber, modifier = Modifier.size(14.dp))
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
                        val isSelected = msg.id in selectedMessageIds
                        var tapCount by remember(msg.id) { mutableIntStateOf(0) }
                        var lastTapAt by remember(msg.id) { mutableLongStateOf(0L) }

                        val toggleSelect = {
                            selectedMessageIds = if (msg.id in selectedMessageIds) {
                                selectedMessageIds - msg.id
                            } else {
                                selectedMessageIds + msg.id
                            }
                        }
                        val onTapBubbleAction = {
                            if (selectedMessageIds.isNotEmpty()) {
                                toggleSelect()
                            } else if (!msg.isDeleted) {
                                val now = System.currentTimeMillis()
                                val nextCount = if (now - lastTapAt <= 750L) tapCount + 1 else 1
                                lastTapAt = now
                                if (nextCount >= 3) {
                                    tapCount = 0
                                    lastTapAt = 0L
                                    state.toggleStarMessage(activeChatId, msg.id)
                                } else {
                                    tapCount = nextCount
                                    tappedReceiptMsgId = if (tappedReceiptMsgId == msg.id) null else msg.id
                                }
                            }
                        }

                        val isJumpHighlighted = highlightedMessageId == msg.id
                        val rowHighlightColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> MyBubbleFill.copy(alpha = 0.22f)
                                isJumpHighlighted -> LanternAmber.copy(alpha = 0.24f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 300),
                            label = "msg_row_highlight",
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowHighlightColor)
                                .then(
                                    if (selectedMessageIds.isNotEmpty()) {
                                        Modifier.clickable { toggleSelect() }
                                    } else {
                                        Modifier
                                    }
                                ),
                            horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start,
                        ) {
                            SwipeToReplyContainer(
                                enabled = !msg.isDeleted && selectedMessageIds.isEmpty(),
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
                                            isJumpHighlighted = isJumpHighlighted,
                                            onTapBubble = onTapBubbleAction,
                                            onLongPress = toggleSelect,
                                            onToggleEmojiReaction = { emoji -> state.reactToMessage(activeChatId, msg.id, emoji) },
                                            onOpenPhoto = { path ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else fullScreenPhotoPath = path
                                            },
                                            onOpenVideo = { path ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else fullScreenVideoPath = path
                                            },
                                            onOpenFile = { path, mime ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else openLocalFile(path, mime)
                                            },
                                            onJumpToReply = { replyId ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else jumpToMessage(replyId)
                                            },
                                            onVoicePlayStarted = { startedMsg ->
                                                state.markVoiceNoteListened(activeChatId, startedMsg)
                                            },
                                            voicePlayer = state.voicePlayer,
                                        )
                                    }
                                    MessageType.VIEW_ONCE -> {
                                        ViewOnceCard(
                                            message = msg,
                                            isReceiptExpanded = tappedReceiptMsgId == msg.id,
                                            isJumpHighlighted = isJumpHighlighted,
                                            onTapCard = {
                                                if (selectedMessageIds.isNotEmpty()) {
                                                    toggleSelect()
                                                } else if (!msg.isMine && msg.viewOnceStatus == ViewOnceStatus.UNOPENED) {
                                                    viewingViewOnceMessage = msg
                                                } else {
                                                    onTapBubbleAction()
                                                }
                                            },
                                            onLongPress = toggleSelect,
                                        )
                                    }
                                    MessageType.BURNED_TRACE -> {
                                        BurnedTraceBubble(
                                            message = msg,
                                            fontScale = state.messageFontScale,
                                            inSelectionMode = selectedMessageIds.isNotEmpty(),
                                            onToggleSelect = toggleSelect,
                                        )
                                    }
                                    MessageType.REGULAR -> {
                                        StandardMessageBubble(
                                            message = msg,
                                            fontScale = state.messageFontScale,
                                            isGroup = isGroup,
                                            isReceiptExpanded = tappedReceiptMsgId == msg.id,
                                            highlightQuery = if (isSearchMatch) inChatSearchQuery.trim() else "",
                                            isJumpHighlighted = isJumpHighlighted,
                                            onTapBubble = onTapBubbleAction,
                                            onLongPress = toggleSelect,
                                            onToggleEmojiReaction = { emoji -> state.reactToMessage(activeChatId, msg.id, emoji) },
                                            onOpenPhoto = { path ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else fullScreenPhotoPath = path
                                            },
                                            onOpenVideo = { path ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else fullScreenVideoPath = path
                                            },
                                            onOpenFile = { path, mime ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else openLocalFile(path, mime)
                                            },
                                            onJumpToReply = { replyId ->
                                                if (selectedMessageIds.isNotEmpty()) toggleSelect() else jumpToMessage(replyId)
                                            },
                                            onVoicePlayStarted = { startedMsg ->
                                                state.markVoiceNoteListened(activeChatId, startedMsg)
                                            },
                                            onFavoriteSticker = { stickerMsg ->
                                                coroutineScope.launch {
                                                    state.favoriteStickerFromMessage(stickerMsg)
                                                        .onSuccess { actionNotice = "added to my stickers" }
                                                        .onFailure { attachmentError = it.localizedMessage ?: "failed to save sticker" }
                                                }
                                            },
                                            voicePlayer = state.voicePlayer,
                                        )
                                    }
                                }
                            }

                            // When exactly 1 message is selected, render reaction line and actions next to the message.
                            // When 2 or more messages are selected, this floating bar disappears completely.
                            if (selectedMessages.size == 1 &&
                                selectedMessages.first().id == msg.id &&
                                !msg.isDeleted &&
                                msg.type != MessageType.BURNED_TRACE
                            ) {
                                val canSaveMedia = msg.type != MessageType.VIEW_ONCE &&
                                    msg.attachmentType != null &&
                                    !msg.attachmentLocalPath.isNullOrBlank() &&
                                    File(msg.attachmentLocalPath).exists()
                                val canFavSticker = msg.type != MessageType.VIEW_ONCE &&
                                    msg.attachmentType == AttachmentType.STICKER &&
                                    !msg.attachmentLocalPath.isNullOrBlank() &&
                                    File(msg.attachmentLocalPath).exists()
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectedMessageToolbar(
                                    message = msg,
                                    currentUsername = state.currentUsername,
                                    customReactionEmojis = state.customReactionEmojis,
                                    onReact = { emoji ->
                                        state.reactToMessage(activeChatId, msg.id, emoji)
                                        selectedMessageIds = emptySet()
                                    },
                                    onOpenEditReactions = { showEditReactionsDialog = true },
                                    onReply = {
                                        replyingToMessage = msg
                                        selectedMessageIds = emptySet()
                                    },
                                    onEdit = if (msg.isMine && msg.type == MessageType.REGULAR && msg.attachmentType == null) {
                                        {
                                            editingMessage = msg
                                            draft = msg.text
                                            replyingToMessage = null
                                            selectedMessageIds = emptySet()
                                        }
                                    } else null,
                                    onPin = {
                                        state.togglePinMessage(activeChatId, msg.id)
                                        selectedMessageIds = emptySet()
                                    },
                                    onInfo = {
                                        infoModalMessage = msg
                                        selectedMessageIds = emptySet()
                                    },
                                    onStar = {
                                        state.toggleStarMessage(activeChatId, msg.id)
                                        selectedMessageIds = emptySet()
                                    },
                                    onFavoriteSticker = if (canFavSticker) {
                                        {
                                            coroutineScope.launch {
                                                state.favoriteStickerFromMessage(msg)
                                                    .onSuccess { actionNotice = "added to my stickers" }
                                                    .onFailure { attachmentError = it.localizedMessage ?: "failed to save sticker" }
                                            }
                                            selectedMessageIds = emptySet()
                                        }
                                    } else null,
                                    onSaveMedia = if (canSaveMedia) {
                                        {
                                            msg.attachmentLocalPath?.let { path ->
                                                saveMediaFromChat(
                                                    localPath = path,
                                                    attachmentType = msg.attachmentType,
                                                    mimeType = msg.attachmentMimeType,
                                                    fileName = msg.attachmentName,
                                                )
                                            }
                                            selectedMessageIds = emptySet()
                                        }
                                    } else null,
                                    onDelete = {
                                        deleteTargetMessages = listOf(msg)
                                        selectedMessageIds = emptySet()
                                    },
                                )
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
                    label = "editing message",
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
                    label = "replying to @$who",
                    preview = target.text.ifBlank { target.attachmentName ?: "attachment" },
                    onCancel = { replyingToMessage = null },
                )
            }

            // Pending Attachment Preview Strip with WhatsApp-style View-Once Toggle
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
                        } else if (att.type == AttachmentType.VIDEO) {
                            SteppedBox(
                                modifier = Modifier.size(44.dp),
                                backgroundColor = MothBgDark,
                                borderColor = LanternAmber,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PixelVideoIcon(tint = LanternAmber, modifier = Modifier.size(20.dp))
                                }
                            }
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
                                text = if (pendingViewOnce) "${formatFileSize(att.sizeBytes)} • view once" else formatFileSize(att.sizeBytes),
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = if (pendingViewOnce) LanternAmber else MutedText,
                            )
                        }

                        // WhatsApp-style View-Once toggle on selected attachment
                        SteppedBox(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { pendingViewOnce = !pendingViewOnce }
                                .testTag("pending_view_once_toggle"),
                            backgroundColor = if (pendingViewOnce) MyBubbleFill else MothBgDark,
                            borderColor = if (pendingViewOnce) LanternAmber else TheirBubbleBorder,
                            cutSize = 3.dp,
                            borderWidth = 1.dp,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PixelViewOnceIcon(
                                    tint = if (pendingViewOnce) MyBubbleText else MutedText,
                                    active = pendingViewOnce,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        SteppedBox(
                            modifier = Modifier
                                .clickable {
                                    pendingAttachment = null
                                    pendingViewOnce = false
                                }
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
                                            asViewOnce = false,
                                        )
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

            // + (More) Menu: Camera, Photo/Video Media, File
            AnimatedVisibility(visible = showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MothHeaderBg)
                        .border(1.dp, MothDivider)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        text = "media",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
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
                            borderColor = if (showAttachMenu || pendingAttachment != null || pendingViewOnce) LanternAmber else TheirBubbleBorder,
                            cutSize = 4.dp,
                            borderWidth = 2.dp,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (showAttachMenu) "✕" else "+",
                                    fontFamily = PixelifySans,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showAttachMenu || pendingAttachment != null || pendingViewOnce) LanternAmber else TheirBubbleText,
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

        // Edit Quick Reactions Dialog
        if (showEditReactionsDialog) {
            EditReactionsDialog(
                currentEmojis = state.customReactionEmojis,
                onSave = { updated -> state.updateCustomReactionEmojis(updated) },
                onReset = { state.resetCustomReactionEmojis() },
                onDismiss = { showEditReactionsDialog = false },
            )
        }

        // Delete Choice Dialog ("Delete for me / Delete for everyone")
        if (deleteTargetMessages.isNotEmpty()) {
            val targets = deleteTargetMessages
            DeleteChoiceDialog(
                count = targets.size,
                canDeleteForEveryone = targets.any { it.isMine && !it.isDeleted },
                onDeleteForMe = {
                    targets.forEach { m -> state.deleteMessageForMe(activeChatId, m.id) }
                    deleteTargetMessages = emptyList()
                },
                onDeleteForEveryone = {
                    targets.forEach { m ->
                        if (m.isMine && !m.isDeleted) {
                            state.deleteMessageForEveryone(activeChatId, m.id)
                        } else {
                            state.deleteMessageForMe(activeChatId, m.id)
                        }
                    }
                    deleteTargetMessages = emptyList()
                },
                onDismiss = { deleteTargetMessages = emptyList() },
            )
        }

        // Message Info (WhatsApp-style timestamps) Modal
        infoModalMessage?.let { msg ->
            MessageInfoModal(message = msg, onDismiss = { infoModalMessage = null })
        }

        // Forward Target Picker Modal
        if (forwardModalMessages.isNotEmpty()) {
            val toForward = forwardModalMessages
            ForwardTargetModal(
                count = toForward.size,
                conversations = state.conversations,
                onSelect = { target ->
                    toForward.forEach { msg -> state.forwardMessage(msg, target) }
                    forwardModalMessages = emptyList()
                },
                onDismiss = { forwardModalMessages = emptyList() },
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

        // Extended Contact Profile Overlay (with Shared Media/Files/Voice)
        if (showContactProfileOverlay && !isGroup) {
            ContactProfileOverlay(
                state = state,
                contactUsername = activeConv?.name ?: chatTitle,
                conversation = activeConv,
                messages = messages,
                onDismiss = { showContactProfileOverlay = false },
                onCallVoice = { launchCallFromChat() },
                onOpenSearch = { showInChatSearch = true },
                onOpenPhoto = { fullScreenPhotoPath = it },
                onOpenVideo = { fullScreenVideoPath = it },
                onOpenFile = { path, mime -> openLocalFile(path, mime) },
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

        // View-Once Protected Media Overlay (enforces FLAG_SECURE & burns on close)
        viewingViewOnceMessage?.let { voMsg ->
            ViewOnceMediaOverlay(
                state = state,
                message = voMsg,
                onCloseAndBurn = {
                    state.markViewOnceOpenedAndBurn(activeChatId, voMsg.id)
                    viewingViewOnceMessage = null
                },
            )
        }

        // Fullscreen Photo Viewer Overlay (with Save to Gallery)
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
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "save",
                        onClick = {
                            saveMediaFromChat(
                                localPath = photoPath,
                                attachmentType = AttachmentType.PHOTO,
                                mimeType = "image/jpeg",
                                fileName = File(photoPath).name,
                            )
                        },
                        modifier = Modifier.testTag("save_photo_button"),
                        backgroundColor = MothHeaderBg,
                        borderColor = SecureGreen,
                        textColor = SecureGreen,
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "close",
                        onClick = { fullScreenPhotoPath = null },
                        modifier = Modifier.testTag("close_photo_viewer_button"),
                        backgroundColor = MothHeaderBg,
                        borderColor = LanternAmber,
                        textColor = LanternAmber,
                        cutSize = 3.dp,
                    )
                }
            }
        }

        // Fullscreen Video Player Overlay (with Save to Movies)
        fullScreenVideoPath?.let { videoPath ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothBgDark.copy(alpha = 0.96f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(File(videoPath)))
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 520.dp)
                        .align(Alignment.Center),
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "save",
                        onClick = {
                            saveMediaFromChat(
                                localPath = videoPath,
                                attachmentType = AttachmentType.VIDEO,
                                mimeType = "video/mp4",
                                fileName = File(videoPath).name,
                            )
                        },
                        modifier = Modifier.testTag("save_video_button"),
                        backgroundColor = MothHeaderBg,
                        borderColor = SecureGreen,
                        textColor = SecureGreen,
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "close",
                        onClick = { fullScreenVideoPath = null },
                        modifier = Modifier.testTag("close_video_viewer_button"),
                        backgroundColor = MothHeaderBg,
                        borderColor = LanternAmber,
                        textColor = LanternAmber,
                        cutSize = 3.dp,
                    )
                }
            }
        }

        // Photo Sticker Interactive Cropper
        pendingStickerCropUri?.let { cropUri ->
            ImageCropDialog(
                imageUri = cropUri,
                title = "crop sticker",
                outputSizePx = 320,
                showLabelInput = true,
                initialLabel = pendingStickerCropLabel,
                onConfirm = { croppedBitmap, customLabel ->
                    pendingStickerCropUri = null
                    isPreparingAttachment = true
                    attachmentError = null
                    coroutineScope.launch {
                        val res = state.createPhotoStickerBitmap(croppedBitmap, customLabel)
                        res.onSuccess { actionNotice = "sticker saved to my stickers" }
                            .onFailure { err -> attachmentError = err.localizedMessage ?: "failed to create sticker" }
                        if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                        isPreparingAttachment = false
                    }
                },
                onDismiss = { pendingStickerCropUri = null },
            )
        }

        // Brief floating notice toast for save / favourite sticker confirmations
        actionNotice?.let { noticeText ->
            SteppedBox(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                    .testTag("chat_action_notice"),
                backgroundColor = MothHeaderBg,
                borderColor = SecureGreen,
                cutSize = 3.dp,
                borderWidth = 1.dp,
            ) {
                Text(
                    text = noticeText,
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = SecureGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
    isJumpHighlighted: Boolean = false,
    onTapBubble: () -> Unit,
    onLongPress: () -> Unit,
    onToggleEmojiReaction: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onJumpToReply: (String) -> Unit,
    onVoicePlayStarted: (MothMessage) -> Unit = {},
    onFavoriteSticker: (MothMessage) -> Unit = {},
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        if (!message.isDeleted && message.attachmentType == AttachmentType.STICKER && message.text.isBlank()) {
            val path = message.attachmentLocalPath
            Box(modifier = Modifier.combinedClickable(onClick = onTapBubble, onLongClick = onLongPress)) {
                if (!path.isNullOrBlank() && File(path).exists()) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = message.attachmentName ?: "Sticker",
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(124.dp)
                            .testTag("sticker_bubble_${message.id}"),
                    )
                } else {
                    Text("[sticker]", fontFamily = PixelifySans, fontSize = 12.sp, color = MutedText)
                }
                if (message.isStarred || message.isPinned) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MothHeaderBg.copy(alpha = 0.85f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (message.isPinned) {
                            PixelPinIcon(tint = LanternAmber, modifier = Modifier.size(11.dp))
                        }
                        if (message.isStarred) {
                            PixelStarIcon(tint = LanternAmber, filled = true, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        } else {
            val bg = if (message.isDeleted) MothHeaderBg else if (message.isMine) MyBubbleFill else TheirBubbleFill
            val border = when {
                isJumpHighlighted -> LanternAmber
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
                                AttachmentType.VIDEO -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenVideo(message.attachmentLocalPath) else onTapBubble()
                                AttachmentType.FILE -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenFile(message.attachmentLocalPath, message.attachmentMimeType) else onTapBubble()
                                AttachmentType.VOICE, AttachmentType.STICKER, null -> onTapBubble()
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
                    onTapBubble = onTapBubble,
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    onOpenFile = onOpenFile,
                    onLongPress = onLongPress,
                    onJumpToReply = onJumpToReply,
                    onVoicePlayStarted = onVoicePlayStarted,
                    voicePlayer = voicePlayer,
                )
            }
        }

        ReactionsChipRow(message = message, onToggleEmojiReaction = onToggleEmojiReaction)

        if (isReceiptExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InlineReceiptLine(message = message)
                if (!message.isDeleted &&
                    message.attachmentType == AttachmentType.STICKER &&
                    !message.attachmentLocalPath.isNullOrBlank() &&
                    File(message.attachmentLocalPath).exists()
                ) {
                    Text(
                        text = "+ save sticker",
                        fontFamily = PixelifySans,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternAmber,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clickable { onFavoriteSticker(message) }
                            .testTag("inline_fav_sticker_${message.id}"),
                    )
                }
            }
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
    isJumpHighlighted: Boolean = false,
    onTapBubble: () -> Unit,
    onLongPress: () -> Unit,
    onToggleEmojiReaction: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onJumpToReply: (String) -> Unit = {},
    onVoicePlayStarted: (MothMessage) -> Unit = {},
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        val bg = if (message.isMine) MyBubbleFill else TheirBubbleFill
        val border = if (isJumpHighlighted || highlightQuery.isNotBlank()) LanternAmber else if (message.isMine) MyBubbleBorder else TheirBubbleBorder
        val textColor = if (message.isMine) MyBubbleText else TheirBubbleText

        SteppedBox(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {
                        when (message.attachmentType) {
                            AttachmentType.PHOTO -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenPhoto(message.attachmentLocalPath) else onTapBubble()
                            AttachmentType.VIDEO -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenVideo(message.attachmentLocalPath) else onTapBubble()
                            AttachmentType.FILE -> if (!message.attachmentLocalPath.isNullOrBlank()) onOpenFile(message.attachmentLocalPath, message.attachmentMimeType) else onTapBubble()
                            AttachmentType.VOICE, AttachmentType.STICKER, null -> onTapBubble()
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
                onTapBubble = onTapBubble,
                onOpenPhoto = onOpenPhoto,
                onOpenVideo = onOpenVideo,
                onOpenFile = onOpenFile,
                onLongPress = onLongPress,
                onJumpToReply = onJumpToReply,
                onVoicePlayStarted = onVoicePlayStarted,
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
    fun fmtTs(ms: Long): String {
        return if (ms > 0L) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms)).lowercase()
        } else {
            message.time
        }
    }

    val isVoiceListened = message.attachmentType == AttachmentType.VOICE && message.listenedAt > 0L
    val label = if (isVoiceListened) {
        "listened • ${fmtTs(message.listenedAt)}"
    } else if (!message.isMine) {
        if (message.status == MessageStatus.READ && message.readAt > 0L) {
            "seen • ${fmtTs(message.readAt)}"
        } else {
            val recTs = message.deliveredAt.takeIf { it > 0L } ?: message.sentAt
            "received • ${fmtTs(recTs)}"
        }
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
        if (message.status == MessageStatus.SENDING) "sending..." else "$statusWord • ${fmtTs(tsMs)}"
    }
    Text(
        text = label,
        fontFamily = PixelifySans,
        fontSize = 10.sp,
        color = if (isVoiceListened || message.status == MessageStatus.READ) SecureGreen else MutedText,
        modifier = Modifier
            .padding(top = 3.dp, start = 4.dp, end = 4.dp)
            .testTag("inline_receipt_${message.id}"),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BurnedTraceBubble(
    message: MothMessage,
    fontScale: Float,
    inSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
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
                    .combinedClickable(
                        onClick = { if (inSelectionMode) onToggleSelect() else isRevealed = false },
                        onLongClick = onToggleSelect,
                    )
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
                    Text(
                        text = "ash memory • tap to hide",
                        fontFamily = PixelifySans,
                        fontSize = 10.sp,
                        color = MyBubbleText.copy(alpha = 0.75f),
                    )
                }
            }
        } else {
            SteppedBox(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .combinedClickable(
                        onClick = {
                            if (inSelectionMode) {
                                onToggleSelect()
                            } else if (canPeek) {
                                isRevealed = true
                            }
                        },
                        onLongClick = onToggleSelect,
                    )
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
                    Box(modifier = Modifier.size(6.dp).background(WaxSealRed))
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
    isReceiptExpanded: Boolean,
    isJumpHighlighted: Boolean = false,
    onTapCard: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isBurned = message.viewOnceStatus != ViewOnceStatus.UNOPENED
    val mediaWord = when (message.attachmentType) {
        AttachmentType.VIDEO -> "video"
        AttachmentType.PHOTO -> "photo"
        AttachmentType.VOICE -> "voice note"
        else -> "media"
    }
    val title = when {
        isBurned -> "opened"
        message.isMine -> "view once $mediaWord"
        else -> "view once $mediaWord"
    }
    val subtitle = when {
        isBurned -> "sealed in ash"
        message.isMine -> "sent • view once"
        else -> "tap to view"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        SteppedBox(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .combinedClickable(onClick = onTapCard, onLongClick = onLongPress)
                .testTag("view_once_card"),
            backgroundColor = if (message.isMine) MyBubbleFill else TheirBubbleFill,
            borderColor = when {
                isJumpHighlighted -> LanternAmber
                isBurned -> MothDivider
                message.isMine -> MyBubbleBorder
                else -> TheirBubbleBorder
            },
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelViewOnceIcon(
                    tint = if (isBurned) MutedText else if (message.isMine) MyBubbleText else LanternAmber,
                    active = !isBurned,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = title,
                            fontFamily = PixelifySans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBurned) MutedText else if (message.isMine) MyBubbleText else TheirBubbleText,
                        )
                        if (message.isStarred && !message.isDeleted) {
                            PixelStarIcon(tint = LanternAmber, filled = true, modifier = Modifier.size(11.dp))
                        }
                    }
                    Text(
                        text = subtitle,
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = if (isBurned) MutedText else if (message.isMine) MyBubbleText.copy(alpha = 0.78f) else LanternAmber,
                    )
                }
            }
        }

        if (isReceiptExpanded) {
            InlineReceiptLine(message = message)
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
    onTapBubble: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onLongPress: () -> Unit,
    onJumpToReply: (String) -> Unit,
    onVoicePlayStarted: (MothMessage) -> Unit = {},
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
                text = "this message was deleted",
                fontFamily = PixelifySans,
                fontStyle = FontStyle.Italic,
                fontSize = (13f * fontScale).sp,
                color = textColor,
            )
            return@Column
        }

        if (message.isForwarded) {
            Text(
                text = "forwarded",
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
                onTapBubble = onTapBubble,
                onOpenPhoto = onOpenPhoto,
                onOpenVideo = onOpenVideo,
                onOpenFile = onOpenFile,
                onLongPress = onLongPress,
                onVoicePlayStarted = onVoicePlayStarted,
                voicePlayer = voicePlayer,
            )
        }

        if (message.text.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = if (message.attachmentType != null) Modifier.padding(horizontal = 4.dp) else Modifier,
            ) {
                Text(
                    text = styledMessageText(message.text, message.isMine, highlightQuery),
                    fontFamily = PixelifySans,
                    fontSize = (14f * fontScale).sp,
                    lineHeight = (19f * fontScale).sp,
                    color = textColor,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (message.isStarred || message.isPinned || message.isEdited) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        if (message.isPinned) {
                            PixelPinIcon(
                                tint = if (message.isMine) MyBubbleText else LanternAmber,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                        if (message.isStarred) {
                            PixelStarIcon(
                                tint = LanternAmber,
                                filled = true,
                                modifier = Modifier.size(11.dp),
                            )
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
        } else if (message.isStarred || message.isPinned || message.isEdited) {
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.isPinned) {
                    PixelPinIcon(
                        tint = if (message.isMine) MyBubbleText else LanternAmber,
                        modifier = Modifier.size(11.dp),
                    )
                }
                if (message.isStarred) {
                    PixelStarIcon(
                        tint = LanternAmber,
                        filled = true,
                        modifier = Modifier.size(11.dp),
                    )
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
    onTapBubble: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onLongPress: () -> Unit,
    onVoicePlayStarted: (MothMessage) -> Unit = {},
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
        AttachmentType.VIDEO -> {
            if (fileExists && path != null) {
                SteppedBox(
                    modifier = Modifier
                        .widthIn(min = 175.dp, max = 230.dp)
                        .combinedClickable(
                            onClick = { onOpenVideo(path) },
                            onLongClick = onLongPress,
                        )
                        .testTag("video_attachment_${message.id}"),
                    backgroundColor = MothBgDark.copy(alpha = 0.45f),
                    borderColor = borderColor,
                    cutSize = 3.dp,
                    borderWidth = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SteppedBox(
                            modifier = Modifier.size(34.dp),
                            backgroundColor = LanternAmber,
                            borderColor = MyBubbleBorder,
                            cutSize = 2.dp,
                            borderWidth = 1.dp,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PixelPlayIcon(tint = MothBgDark, modifier = Modifier.size(14.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.attachmentName ?: "video",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "video • ${formatFileSize(message.attachmentSizeBytes)}",
                                fontFamily = PixelifySans,
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            } else {
                Text("[video unavailable]", fontFamily = PixelifySans, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
            }
        }
        AttachmentType.VOICE -> {
            val isPlaying = voicePlayer?.isPlaying == true && voicePlayer.activeMessageId == message.id
            val frac = (voicePlayer?.progressFor(message.id) ?: 0f).coerceIn(0f, 1f)
            val totalDurSec = if (voicePlayer?.activeMessageId == message.id && (voicePlayer.durationMs > 0)) {
                (voicePlayer.durationMs / 1000).coerceAtLeast(1)
            } else {
                message.audioDurationSec.coerceAtLeast(1)
            }
            val posSec = ((voicePlayer?.positionMsFor(message.id, totalDurSec) ?: 0) / 1000).coerceIn(0, totalDurSec)
            val posLabel = "${posSec / 60}:${"%02d".format(posSec % 60)}"
            val durLabel = "${totalDurSec / 60}:${"%02d".format(totalDurSec % 60)}"

            // Outer box tap shows sent/seen status; ONLY play button plays/pauses; seek bar scrubs forward/backward!
            SteppedBox(
                modifier = Modifier
                    .widthIn(min = 195.dp, max = 235.dp)
                    .combinedClickable(
                        onClick = onTapBubble,
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
                    // Dedicated Play / Pause Button
                    SteppedBox(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                if (fileExists && path != null && voicePlayer != null) {
                                    val started = voicePlayer.togglePlay(message.id, path)
                                    if (started) {
                                        onVoicePlayStarted(message)
                                    }
                                }
                            }
                            .testTag("voice_play_button_${message.id}"),
                        backgroundColor = LanternAmber,
                        borderColor = MyBubbleBorder,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isPlaying) {
                                PixelPauseIcon(tint = MothBgDark, modifier = Modifier.size(13.dp))
                            } else {
                                PixelPlayIcon(tint = MothBgDark, modifier = Modifier.size(13.dp))
                            }
                        }
                    }

                    // Scrubbable Seek Progress Bar + Timer
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .then(
                                    if (fileExists && path != null) {
                                        Modifier
                                            .pointerInput(message.id, path) {
                                                detectTapGestures { offset ->
                                                    val nextFrac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                    voicePlayer?.seekTo(message.id, path, nextFrac)
                                                }
                                            }
                                            .pointerInput(message.id, path) {
                                                detectHorizontalDragGestures { change, _ ->
                                                    change.consume()
                                                    val nextFrac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                    voicePlayer?.seekTo(message.id, path, nextFrac)
                                                }
                                            }
                                    } else Modifier
                                )
                                .testTag("voice_seekbar_${message.id}"),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(MothDivider),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .fillMaxHeight()
                                        .background(LanternAmber),
                                )
                            }
                            // Pixel thumb indicator
                            val thumbOffsetDp = (maxWidth - 8.dp) * frac
                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffsetDp)
                                    .size(8.dp, 12.dp)
                                    .background(TheirBubbleText),
                            )
                        }
                        Text(
                            text = if (isPlaying || frac > 0f) "$posLabel / $durLabel" else "voice • $durLabel",
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

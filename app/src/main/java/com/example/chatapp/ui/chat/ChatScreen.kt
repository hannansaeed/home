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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    state: MothState,
    chatTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val cleanChatTitle = remember(chatTitle) { chatTitle.trim().removePrefix("@") }
    val resolvedConv = state.getActiveConversation()
        ?: state.conversations.firstOrNull {
            it.name.equals(cleanChatTitle, ignoreCase = true) ||
                it.id.equals("${state.currentUsername.lowercase()}::${cleanChatTitle.lowercase()}", ignoreCase = true)
        }
    var cachedConv by remember(chatTitle) { mutableStateOf(resolvedConv) }
    if (resolvedConv != null) {
        cachedConv = resolvedConv
    }
    val activeConv = resolvedConv ?: cachedConv
    var cachedPresence by remember(chatTitle) { mutableStateOf(state.activeContactPresence) }
    if (state.activeChatId != null) {
        cachedPresence = state.activeContactPresence
    }

    val activeChatId = activeConv?.id
        ?: "${state.currentUsername.lowercase()}::${cleanChatTitle.lowercase()}"
    val isGroup = activeConv?.isGroup == true
    val messages = state.activeMessages

    var draft by remember { mutableStateOf("") }
    var pendingViewOnce by remember { mutableStateOf(false) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showStickerTray by remember { mutableStateOf(false) }
    var showVoiceFilterMenu by remember { mutableStateOf(false) }
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
    var holdStickerTarget by remember { mutableStateOf<MothSticker?>(null) }
    var unpinConfirmMessage by remember { mutableStateOf<MothMessage?>(null) }

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

    LaunchedEffect(selectedMessageIds) {
        if (selectedMessageIds.size == 1) {
            val targetId = selectedMessageIds.first()
            val idx = messages.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                delay(40L)
                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                if (visible == null) {
                    listState.animateScrollToItem(idx)
                } else {
                    val vpStart = layoutInfo.viewportStartOffset
                    val vpEnd = layoutInfo.viewportEndOffset
                    val itemTop = visible.offset
                    val itemBottom = visible.offset + visible.size
                    if (itemTop < vpStart) {
                        listState.animateScrollBy((itemTop - vpStart - 24).toFloat())
                    } else if (itemBottom > vpEnd) {
                        listState.animateScrollBy((itemBottom - vpEnd + 24).toFloat())
                    }
                }
            }
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
            holdStickerTarget != null -> holdStickerTarget = null
            unpinConfirmMessage != null -> unpinConfirmMessage = null
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

    DisposableEffect(activeChatId, cleanChatTitle) {
        state.markChatScreenVisible(activeChatId, cleanChatTitle)
        onDispose {
            state.clearChatScreenVisible(activeChatId)
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
        val peerTarget = activeConv?.name ?: cleanChatTitle
        if (shouldCall && granted) {
            state.startCall(peerTarget, CallType.VOICE) { err -> attachmentError = err }
        } else if (shouldCall) {
            attachmentError = "microphone permission required for calling"
        }
    }

    fun launchCallFromChat() {
        val peerTarget = activeConv?.name ?: cleanChatTitle
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            state.startCall(peerTarget, CallType.VOICE) { err -> attachmentError = err }
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
            ChatScreenHeader(
                selectedMessages = selectedMessages,
                activeConv = activeConv,
                cleanChatTitle = cleanChatTitle,
                isGroup = isGroup,
                cachedPresence = cachedPresence,
                showInChatSearch = showInChatSearch,
                onClearSelection = { selectedMessageIds = emptySet() },
                onCopySelected = {
                    val copyableTexts = selectedMessages
                        .filter { !it.isDeleted }
                        .mapNotNull { m -> m.text.takeIf { it.isNotBlank() } }
                    if (copyableTexts.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(copyableTexts.joinToString("\n")))
                        selectedMessageIds = emptySet()
                        actionNotice = "copied"
                    }
                },
                onStarSelected = { star ->
                    val nonDeletedIds = selectedMessages.filter { !it.isDeleted }.map { it.id }
                    state.setMessagesStarred(activeChatId, nonDeletedIds, star)
                    selectedMessageIds = emptySet()
                },
                onForwardSelected = { msgs ->
                    forwardModalMessages = msgs
                    selectedMessageIds = emptySet()
                },
                onDeleteSelected = { msgs ->
                    deleteTargetMessages = msgs
                    selectedMessageIds = emptySet()
                },
                onBack = onBack,
                onOpenProfile = {
                    if (isGroup) showGroupInfoModal = true else showContactProfileOverlay = true
                },
                onCallVoice = { launchCallFromChat() },
                onToggleSearch = {
                    showInChatSearch = !showInChatSearch
                    if (!showInChatSearch) inChatSearchQuery = ""
                },
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

            ChatInChatSearchBar(
                visible = showInChatSearch,
                query = inChatSearchQuery,
                matchCount = searchMatches.size,
                cursorIdx = searchCursorIdx,
                onQueryChange = { inChatSearchQuery = it },
                onPrevMatch = {
                    if (searchMatches.isNotEmpty()) {
                        searchCursorIdx = if (searchCursorIdx > 0) searchCursorIdx - 1 else searchMatches.lastIndex
                    }
                },
                onNextMatch = {
                    if (searchMatches.isNotEmpty()) {
                        searchCursorIdx = (searchCursorIdx + 1) % searchMatches.size
                    }
                },
                onClose = {
                    showInChatSearch = false
                    inChatSearchQuery = ""
                },
            )

            ChatPinnedMessageBanner(
                pinnedMessages = pinnedMessages,
                pinnedBannerIdx = pinnedBannerIdx,
                onTapPinned = { pinned, nextIdx ->
                    jumpToMessage(pinned.id)
                    pinnedBannerIdx = nextIdx
                },
                onRequestUnpin = { pinned -> unpinConfirmMessage = pinned },
            )

            state.activeChatTodSession?.let { tod ->
                ChatTodBotCard(
                    tod = tod,
                    onPlayTurn = { mode -> state.playTodTurn(activeChatId, mode) },
                    onAdvanceTurn = { state.advanceTodTurn(activeChatId) },
                    onRemove = { state.removeTodBotFromChat(activeChatId) },
                )
            }

            ChatMessageFeed(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                listState = listState,
                messages = messages,
                inChatSearchQuery = inChatSearchQuery,
                selectedMessageIds = selectedMessageIds,
                selectedMessagesCount = selectedMessages.size,
                singleSelectedMessageId = selectedMessages.singleOrNull()?.id,
                highlightedMessageId = highlightedMessageId,
                tappedReceiptMsgId = tappedReceiptMsgId,
                fontScale = state.messageFontScale,
                isGroup = isGroup,
                isContactTyping = state.isContactTyping,
                currentUsername = state.currentUsername,
                customReactionEmojis = state.customReactionEmojis,
                voicePlayer = state.voicePlayer,
                onToggleSelect = { id ->
                    selectedMessageIds = if (id in selectedMessageIds) {
                        selectedMessageIds - id
                    } else {
                        selectedMessageIds + id
                    }
                },
                onToggleReceipt = { id ->
                    tappedReceiptMsgId = if (tappedReceiptMsgId == id) null else id
                },
                onSwipeToReply = { msg -> replyingToMessage = msg },
                onToggleEmojiReaction = { msg, emoji -> state.reactToMessage(activeChatId, msg.id, emoji) },
                onOpenPhoto = { path -> fullScreenPhotoPath = path },
                onOpenVideo = { path -> fullScreenVideoPath = path },
                onOpenFile = { path, mime -> openLocalFile(path, mime) },
                onJumpToReply = { replyId -> jumpToMessage(replyId) },
                onVoicePlayStarted = { startedMsg -> state.markVoiceNoteListened(activeChatId, startedMsg) },
                onViewOnceTap = { msg ->
                    if (selectedMessageIds.isNotEmpty()) {
                        selectedMessageIds = if (msg.id in selectedMessageIds) {
                            selectedMessageIds - msg.id
                        } else {
                            selectedMessageIds + msg.id
                        }
                    } else if (!msg.isMine && msg.viewOnceStatus == ViewOnceStatus.UNOPENED) {
                        viewingViewOnceMessage = msg
                    } else if (!msg.isDeleted) {
                        tappedReceiptMsgId = if (tappedReceiptMsgId == msg.id) null else msg.id
                    }
                },
                onFavoriteSticker = { stickerMsg ->
                    coroutineScope.launch {
                        state.favoriteStickerFromMessage(stickerMsg)
                            .onSuccess { actionNotice = "added to my stickers" }
                            .onFailure { attachmentError = it.localizedMessage ?: "failed to save sticker" }
                    }
                },
                onCallBack = { launchCallFromChat() },
                onToolbarReact = { msg, emoji ->
                    state.reactToMessage(activeChatId, msg.id, emoji)
                    selectedMessageIds = emptySet()
                },
                onOpenEditReactions = { showEditReactionsDialog = true },
                onToolbarReply = { msg ->
                    replyingToMessage = msg
                    selectedMessageIds = emptySet()
                },
                onToolbarCopy = { msg ->
                    clipboardManager.setText(AnnotatedString(msg.text))
                    selectedMessageIds = emptySet()
                    actionNotice = "copied"
                },
                onToolbarEdit = { msg ->
                    editingMessage = msg
                    draft = msg.text
                    replyingToMessage = null
                    selectedMessageIds = emptySet()
                },
                onToolbarPin = { msg ->
                    if (msg.isPinned) {
                        unpinConfirmMessage = msg
                    } else {
                        state.togglePinMessage(activeChatId, msg.id)
                    }
                    selectedMessageIds = emptySet()
                },
                onToolbarInfo = { msg ->
                    infoModalMessage = msg
                    selectedMessageIds = emptySet()
                },
                onToolbarStar = { msg ->
                    state.toggleStarMessage(activeChatId, msg.id)
                    selectedMessageIds = emptySet()
                },
                onToolbarFavoriteSticker = { msg ->
                    coroutineScope.launch {
                        state.favoriteStickerFromMessage(msg)
                            .onSuccess { actionNotice = "added to my stickers" }
                            .onFailure { attachmentError = it.localizedMessage ?: "failed to save sticker" }
                    }
                    selectedMessageIds = emptySet()
                },
                onToolbarSaveMedia = { msg ->
                    msg.attachmentLocalPath?.let { path ->
                        saveMediaFromChat(
                            localPath = path,
                            attachmentType = msg.attachmentType,
                            mimeType = msg.attachmentMimeType,
                            fileName = msg.attachmentName,
                        )
                    }
                    selectedMessageIds = emptySet()
                },
                onToolbarDelete = { msg ->
                    deleteTargetMessages = listOf(msg)
                    selectedMessageIds = emptySet()
                },
            )

            ChatAttachmentErrorBanner(
                attachmentError = attachmentError,
                onDismiss = { attachmentError = null },
            )

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

            ChatMentionStrip(
                mentionCandidates = mentionCandidates,
                onPickMember = { member ->
                    val prefixBeforeAt = draft.substringBeforeLast("@")
                    draft = "$prefixBeforeAt@$member "
                },
            )

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

            pendingAttachment?.let { att ->
                ChatPendingAttachmentBanner(
                    att = att,
                    pendingViewOnce = pendingViewOnce,
                    onToggleViewOnce = { pendingViewOnce = !pendingViewOnce },
                    onClearAttachment = {
                        pendingAttachment = null
                        pendingViewOnce = false
                    },
                )
            }

            ChatStickersTray(
                visible = showStickerTray,
                stickers = state.customStickers,
                onOpenDraw = { showPixelStickerMaker = true },
                onOpenPhotoPicker = {
                    stickerPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSendSticker = { sticker ->
                    state.sendSticker(chatId = activeChatId, sticker = sticker, asViewOnce = false)
                    showStickerTray = false
                },
                onHoldSticker = { sticker -> holdStickerTarget = sticker },
            )

            ChatAttachMenuRow(
                visible = showAttachMenu,
                onCameraClick = {
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
                onMediaClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            )

            ChatVoiceFilterPickerBar(
                visible = showVoiceFilterMenu,
                activeFilter = state.voiceRecorder.selectedFilter,
                onSelectFilter = { filter ->
                    state.voiceRecorder.selectedFilter = filter
                    showVoiceFilterMenu = false
                },
            )

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

            ChatInputBar(
                voiceRecorder = state.voiceRecorder,
                draft = draft,
                hasPendingAttachment = pendingAttachment != null,
                pendingViewOnce = pendingViewOnce,
                showAttachMenu = showAttachMenu,
                showStickerTray = showStickerTray,
                showVoiceFilterMenu = showVoiceFilterMenu,
                onDraftChange = { draft = it },
                onToggleAttachMenu = {
                    showAttachMenu = !showAttachMenu
                    if (showAttachMenu) showStickerTray = false
                },
                onToggleStickerTray = {
                    showStickerTray = !showStickerTray
                    if (showStickerTray) {
                        showAttachMenu = false
                        showVoiceFilterMenu = false
                    }
                },
                onVoiceNoteClick = {
                    showStickerTray = false
                    showAttachMenu = false
                    showVoiceFilterMenu = false
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
                },
                onVoiceNoteLongClick = {
                    showStickerTray = false
                    showAttachMenu = false
                    showVoiceFilterMenu = !showVoiceFilterMenu
                },
                onSendTextClick = { handleSend() },
                onSendVoiceNoteClick = {
                    coroutineScope.launch {
                        val result = state.voiceRecorder.stopRecordingAndProcess()
                        if (result != null) {
                            val (file, dur) = result
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
            )
        }

        if (showEditReactionsDialog) {
            EditReactionsDialog(
                currentEmojis = state.customReactionEmojis,
                onSave = { updated -> state.updateCustomReactionEmojis(updated) },
                onReset = { state.resetCustomReactionEmojis() },
                onDismiss = { showEditReactionsDialog = false },
            )
        }

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

        infoModalMessage?.let { msg ->
            MessageInfoModal(message = msg, onDismiss = { infoModalMessage = null })
        }

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

        if (showGroupInfoModal && activeConv != null && activeConv.isGroup) {
            GroupInfoModal(
                state = state,
                conversation = activeConv,
                onDismiss = { showGroupInfoModal = false },
            )
        }

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

        fullScreenPhotoPath?.let { photoPath ->
            ChatFullScreenPhotoOverlay(
                photoPath = photoPath,
                onSave = {
                    saveMediaFromChat(
                        localPath = photoPath,
                        attachmentType = AttachmentType.PHOTO,
                        mimeType = "image/jpeg",
                        fileName = File(photoPath).name,
                    )
                },
                onClose = { fullScreenPhotoPath = null },
            )
        }

        fullScreenVideoPath?.let { videoPath ->
            ChatFullScreenVideoOverlay(
                videoPath = videoPath,
                onSave = {
                    saveMediaFromChat(
                        localPath = videoPath,
                        attachmentType = AttachmentType.VIDEO,
                        mimeType = "video/mp4",
                        fileName = File(videoPath).name,
                    )
                },
                onClose = { fullScreenVideoPath = null },
            )
        }

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

        unpinConfirmMessage?.let { targetMsg ->
            ChatUnpinConfirmDialog(
                targetMsg = targetMsg,
                onCancel = { unpinConfirmMessage = null },
                onConfirmUnpin = {
                    state.togglePinMessage(activeChatId, targetMsg.id)
                    unpinConfirmMessage = null
                },
            )
        }

        holdStickerTarget?.let { sticker ->
            ChatStickerHoldOptionsDialog(
                sticker = sticker,
                onUnfavourite = {
                    state.deleteSticker(sticker.id)
                    actionNotice = "removed from favourites"
                    holdStickerTarget = null
                },
                onDelete = {
                    state.deleteSticker(sticker.id)
                    actionNotice = "sticker deleted"
                    holdStickerTarget = null
                },
                onCancel = { holdStickerTarget = null },
            )
        }

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
internal fun SwipeToReplyContainer(
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
internal fun InputModeBanner(
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
internal fun StickerTrayItem(
    sticker: MothSticker,
    onSend: () -> Unit,
    onHold: () -> Unit,
) {
    SteppedBox(
        modifier = Modifier
            .width(68.dp)
            .combinedClickable(onClick = onSend, onLongClick = onHold)
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
internal fun PixelStickerMakerDialog(
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> Unit,
) {
    val gridSize = 10
    val cells = remember { mutableStateListOf<Int>().apply { repeat(gridSize * gridSize) { add(0) } } }
    var selectedColor by remember { mutableIntStateOf(LanternAmber.toArgb()) }
    var label by remember { mutableStateOf("") }

    val palette = listOf(
        LanternAmber, LanternGlow, WaxSealRed, SecureGreen,
        TheirBubbleText, TheirBubbleBorder, Color(0xFF70D6FF), MothInkDark,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothScrimStrong)
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
                            Text(if (isBloomTheme) "sticker name (e.g. happy bee)" else "sticker name (e.g. happy moth)", fontFamily = PixelifySans, fontSize = 12.sp, color = PlaceholderText)
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
                                        .background(if (cellArgb == 0) MothHeaderBg else Color(cellArgb))
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
internal fun StandardMessageBubble(
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
    onCallBack: () -> Unit = {},
    voicePlayer: VoiceNotePlayer,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        if (!message.isDeleted && message.attachmentType == AttachmentType.CALL) {
            val dir = runCatching {
                CallDirection.valueOf(message.attachmentName ?: CallDirection.MISSED.name)
            }.getOrDefault(CallDirection.MISSED)
            val isMissed = dir == CallDirection.MISSED
            val dur = message.audioDurationSec
            val iconColor = when {
                isMissed -> WaxSealRed
                dur > 0 -> SecureGreen
                else -> LanternAmber
            }
            val callTitle = when (dir) {
                CallDirection.MISSED -> "missed voice call"
                CallDirection.OUTGOING -> "outgoing voice call"
                CallDirection.INCOMING -> "incoming voice call"
            }
            val callSub = when {
                dur > 0 -> "${dur / 60}:${"%02d".format(dur % 60)} • tap to call back"
                isMissed -> "tap to call back"
                else -> "no answer • tap to call back"
            }
            SteppedBox(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 270.dp)
                    .combinedClickable(
                        onClick = onCallBack,
                        onLongClick = onLongPress,
                    )
                    .testTag("call_log_bubble_${message.id}"),
                backgroundColor = if (message.isMine) MyBubbleFill.copy(alpha = 0.22f) else MothHeaderBg,
                borderColor = if (isJumpHighlighted) LanternAmber else if (isMissed) WaxSealRed else TheirBubbleBorder,
                cutSize = 4.dp,
                borderWidth = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SteppedBox(
                        modifier = Modifier.size(30.dp),
                        backgroundColor = MothBgDark,
                        borderColor = iconColor,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelPhoneIcon(tint = iconColor, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = callTitle,
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isMissed) WaxSealRed else TheirBubbleText,
                        )
                        Text(
                            text = callSub,
                            fontFamily = PixelifySans,
                            fontSize = 10.sp,
                            color = MutedText,
                        )
                    }
                    Text(
                        text = message.time,
                        fontFamily = PixelifySans,
                        fontSize = 9.sp,
                        color = PlaceholderText,
                    )
                }
            }
        } else if (!message.isDeleted && message.attachmentType == AttachmentType.STICKER && message.text.isBlank()) {
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
                                AttachmentType.VOICE, AttachmentType.STICKER, AttachmentType.CALL, null -> onTapBubble()
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
internal fun DisappearingMessageBubble(
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
                            AttachmentType.VOICE, AttachmentType.STICKER, AttachmentType.CALL, null -> onTapBubble()
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
                text = if (isBloomTheme) {
                    if (message.leaveTrace) "petals falling • leaves seed" else "petals falling"
                } else {
                    if (message.leaveTrace) "burning • leaves ash" else "burning down"
                },
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
internal fun BurnedTraceBubble(
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
                        text = if (isBloomTheme) "seed memory • tap to hide" else "ash memory • tap to hide",
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
                        Text(
                            text = if (isBloomTheme) "flower withered • petals fell" else "a burning message turned to ash",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        if (canPeek) {
                            Text(text = if (isBloomTheme) "tap seed to peek" else "tap trace to peek", fontFamily = PixelifySans, fontSize = 10.sp, color = PlaceholderText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ViewOnceCard(
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
        isBurned -> if (isBloomTheme) "flower withered" else "sealed in ash"
        message.isMine -> "sent • view once"
        else -> if (isBloomTheme) "tap to bloom" else "tap to view"
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
                            PixelStarIcon(
                                tint = if (message.isMine) MyBubbleText else LanternAmber,
                                filled = true,
                                modifier = Modifier.size(11.dp),
                            )
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
                        text = "@${message.replyToSender ?: if (isBloomTheme) "bloom" else "lantern"}",
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
                if (message.isStarred || message.isPinned) {
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
                                tint = if (message.isMine) MyBubbleText else LanternAmber,
                                filled = true,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        } else if (message.isStarred || message.isPinned) {
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
                        tint = if (message.isMine) MyBubbleText else LanternAmber,
                        filled = true,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        if (message.isEdited) {
            Text(
                text = "edited",
                fontFamily = PixelifySans,
                fontStyle = FontStyle.Italic,
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.End),
            )
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
        AttachmentType.CALL, null -> Unit
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

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    return if (kb >= 1.0) "${kb.toInt()} KB" else "$bytes B"
}

@Composable
internal fun TypingIndicatorRow() {
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

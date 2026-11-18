package com.example.chatapp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import java.io.File

@Composable
internal fun ChatScreenHeader(
    selectedMessages: List<MothMessage>,
    activeConv: MothConversation?,
    cleanChatTitle: String,
    isGroup: Boolean,
    cachedPresence: ContactPresence,
    showInChatSearch: Boolean,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onStarSelected: (Boolean) -> Unit,
    onForwardSelected: (List<MothMessage>) -> Unit,
    onDeleteSelected: (List<MothMessage>) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onCallVoice: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MothHeaderBg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (selectedMessages.isNotEmpty()) {
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
                            .clickable(onClick = onClearSelection)
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
                        if (nonDeletedSelected.any { it.text.isNotBlank() }) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable(onClick = onCopySelected)
                                    .testTag("action_copy_top_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelCopyIcon(tint = TheirBubbleText, modifier = Modifier.size(18.dp))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onStarSelected(!allStarred) }
                                .testTag("action_star_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelStarIcon(tint = LanternAmber, filled = allStarred, modifier = Modifier.size(18.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onForwardSelected(nonDeletedSelected) }
                                .testTag("action_forward_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelForwardIcon(tint = TheirBubbleText, modifier = Modifier.size(18.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onDeleteSelected(selectedMessages) }
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
                            .clickable(onClick = onOpenProfile)
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
                                text = activeConv?.name ?: cleanChatTitle,
                                fontFamily = PixelifySans,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanternAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val displayPresenceLabel =
                                if (isBloomTheme && cachedPresence.label == "active") "rooted" else cachedPresence.label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("contact_presence_status"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(if (cachedPresence.isOnline) SecureGreen else MutedText),
                                )
                                Text(
                                    text = if (isGroup) "tap for members • $displayPresenceLabel" else displayPresenceLabel,
                                    fontFamily = PixelifySans,
                                    fontSize = 11.sp,
                                    color = if (cachedPresence.isOnline) SecureGreen else MutedText,
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!isGroup && !cleanChatTitle.equals("support", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(onClick = onCallVoice)
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
                            .clickable(onClick = onToggleSearch)
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
}

@Composable
internal fun ChatInChatSearchBar(
    visible: Boolean,
    query: String,
    matchCount: Int,
    cursorIdx: Int,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
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
                    if (query.isEmpty()) {
                        Text(
                            text = "search messages...",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = PlaceholderText,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                        cursorBrush = SolidColor(LanternAmber),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("in_chat_search_input"),
                    )
                }
            }
            if (query.isNotBlank()) {
                val counterText = if (matchCount == 0) "0/0" else "${cursorIdx + 1}/$matchCount"
                Text(text = counterText, fontFamily = PixelifySans, fontSize = 11.sp, color = LanternAmber)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onPrevMatch)
                        .testTag("in_chat_search_prev"),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelArrowUpIcon(tint = LanternAmber, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onNextMatch)
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
                    .clickable(onClick = onClose)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
internal fun ChatPinnedMessageBanner(
    pinnedMessages: List<MothMessage>,
    pinnedBannerIdx: Int,
    onTapPinned: (MothMessage, Int) -> Unit,
    onRequestUnpin: (MothMessage) -> Unit,
) {
    if (pinnedMessages.isEmpty()) return
    val safeIdx = pinnedBannerIdx.coerceIn(0, pinnedMessages.lastIndex)
    val pinned = pinnedMessages[safeIdx]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TheirBubbleFill)
            .border(1.dp, LanternAmber)
            .clickable {
                val nextIdx = if (pinnedMessages.size > 1) (safeIdx + 1) % pinnedMessages.size else safeIdx
                onTapPinned(pinned, nextIdx)
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
                .clickable { onRequestUnpin(pinned) }
                .padding(start = 8.dp)
                .testTag("unpin_banner_button"),
        )
    }
}

@Composable
internal fun ChatTodBotCard(
    tod: TodSessionState,
    currentUsername: String,
    onPlayTurn: (String) -> Unit,
    onSkipPrompt: () -> Unit,
    onAdvanceTurn: () -> Unit,
    onRemove: () -> Unit,
) {
    val isMyTurn = tod.turnUsername.equals(currentUsername, ignoreCase = true)
    SteppedBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("in_chat_tod_bot_card"),
        backgroundColor = MothHeaderBg,
        borderColor = if (tod.promptType == "DARE") WaxSealRed else LanternAmber,
        cutSize = 4.dp,
        borderWidth = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .background(SecureGreen)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "BOT",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MothBgDark,
                        )
                    }
                    Text(
                        text = if (isMyTurn) "TOD • r${tod.roundNumber} • your turn" else "TOD • r${tod.roundNumber} • @${tod.turnUsername}",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isMyTurn) SecureGreen else LanternAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "✕ remove",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = WaxSealRed,
                    modifier = Modifier
                        .clickable(onClick = onRemove)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("remove_tod_bot_button"),
                )
            }

            val controlsAlpha = if (isMyTurn) 1f else 0.42f

            if (tod.promptText.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(if (tod.promptType == "DARE") WaxSealRed else TheirBubbleFill)
                                .border(1.dp, if (tod.promptType == "DARE") WaxSealDarkEdge else TheirBubbleBorder)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = tod.promptType,
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White,
                            )
                        }
                        if (tod.selectedMode.isNotBlank()) {
                            Text(
                                text = "(${tod.selectedMode.lowercase()})",
                                fontFamily = PixelifySans,
                                fontSize = 10.sp,
                                color = MutedText,
                            )
                        }
                    }
                    Text(
                        text = tod.promptText,
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = TheirBubbleText,
                        lineHeight = 18.sp,
                    )
                }

                if (!isMyTurn) {
                    Text(
                        text = "waiting for @${tod.turnUsername}...",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(controlsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "skip",
                        onClick = onSkipPrompt,
                        enabled = isMyTurn,
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tod_skip_button"),
                        cutSize = 2.dp,
                    )
                    SteppedButton(
                        text = "next",
                        onClick = onAdvanceTurn,
                        enabled = isMyTurn,
                        backgroundColor = SecureGreen,
                        borderColor = Color(0xFF4CA877),
                        textColor = MothBgDark,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tod_next_button"),
                        cutSize = 2.dp,
                    )
                }
            } else {
                Text(
                    text = if (isMyTurn) "your turn! pick truth, dare, or random" else "waiting for @${tod.turnUsername} to pick...",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(controlsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SteppedButton(
                        text = "truth",
                        onClick = { onPlayTurn("TRUTH") },
                        enabled = isMyTurn,
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tod_pick_truth_button"),
                        cutSize = 2.dp,
                    )
                    SteppedButton(
                        text = "dare",
                        onClick = { onPlayTurn("DARE") },
                        enabled = isMyTurn,
                        backgroundColor = WaxSealRed,
                        borderColor = WaxSealDarkEdge,
                        textColor = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tod_pick_dare_button"),
                        cutSize = 2.dp,
                    )
                    SteppedButton(
                        text = "random",
                        onClick = { onPlayTurn("RANDOM") },
                        enabled = isMyTurn,
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tod_pick_random_button"),
                        cutSize = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatMessageFeed(
    modifier: Modifier,
    listState: LazyListState,
    messages: List<MothMessage>,
    inChatSearchQuery: String,
    selectedMessageIds: Set<String>,
    selectedMessagesCount: Int,
    singleSelectedMessageId: String?,
    highlightedMessageId: String?,
    tappedReceiptMsgId: String?,
    fontScale: Float,
    isGroup: Boolean,
    isContactTyping: Boolean,
    currentUsername: String,
    customReactionEmojis: List<String>,
    voicePlayer: VoiceNotePlayer,
    onToggleSelect: (String) -> Unit,
    onToggleReceipt: (String) -> Unit,
    onSwipeToReply: (MothMessage) -> Unit,
    onToggleEmojiReaction: (MothMessage, String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onJumpToReply: (String) -> Unit,
    onVoicePlayStarted: (MothMessage) -> Unit,
    onViewOnceTap: (MothMessage) -> Unit,
    onFavoriteSticker: (MothMessage) -> Unit,
    onCallBack: () -> Unit,
    onToolbarReact: (MothMessage, String) -> Unit,
    onOpenEditReactions: () -> Unit,
    onToolbarReply: (MothMessage) -> Unit,
    onToolbarCopy: (MothMessage) -> Unit,
    onToolbarEdit: (MothMessage) -> Unit,
    onToolbarPin: (MothMessage) -> Unit,
    onToolbarInfo: (MothMessage) -> Unit,
    onToolbarStar: (MothMessage) -> Unit,
    onToolbarFavoriteSticker: (MothMessage) -> Unit,
    onToolbarSaveMedia: (MothMessage) -> Unit,
    onToolbarDelete: (MothMessage) -> Unit,
) {
    Box(modifier = modifier) {
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
            itemsIndexed(messages, key = { _, msg -> msg.id }) { idx, msg ->
                ChatMessageItemRow(
                    msg = msg,
                    isSearchMatch = inChatSearchQuery.isNotBlank() &&
                        !msg.isDeleted && msg.text.contains(inChatSearchQuery.trim(), ignoreCase = true),
                    inChatSearchQuery = inChatSearchQuery,
                    isSelected = msg.id in selectedMessageIds,
                    inSelectionMode = selectedMessageIds.isNotEmpty(),
                    showSingleToolbar = selectedMessagesCount == 1 &&
                        singleSelectedMessageId == msg.id &&
                        !msg.isDeleted &&
                        msg.type != MessageType.BURNED_TRACE &&
                        msg.attachmentType != AttachmentType.CALL,
                    placeToolbarAbove = idx >= (messages.size - 2).coerceAtLeast(0),
                    isJumpHighlighted = highlightedMessageId == msg.id,
                    isReceiptExpanded = tappedReceiptMsgId == msg.id,
                    fontScale = fontScale,
                    isGroup = isGroup,
                    currentUsername = currentUsername,
                    customReactionEmojis = customReactionEmojis,
                    voicePlayer = voicePlayer,
                    onToggleSelect = { onToggleSelect(msg.id) },
                    onTapBubbleAction = {
                        if (selectedMessageIds.isNotEmpty()) {
                            onToggleSelect(msg.id)
                        } else if (!msg.isDeleted) {
                            onToggleReceipt(msg.id)
                        }
                    },
                    onSwipeToReply = { onSwipeToReply(msg) },
                    onToggleEmojiReaction = { emoji -> onToggleEmojiReaction(msg, emoji) },
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    onOpenFile = onOpenFile,
                    onJumpToReply = onJumpToReply,
                    onVoicePlayStarted = onVoicePlayStarted,
                    onViewOnceTap = { onViewOnceTap(msg) },
                    onFavoriteSticker = { onFavoriteSticker(msg) },
                    onCallBack = onCallBack,
                    onToolbarReact = { emoji -> onToolbarReact(msg, emoji) },
                    onOpenEditReactions = onOpenEditReactions,
                    onToolbarReply = { onToolbarReply(msg) },
                    onToolbarCopy = { onToolbarCopy(msg) },
                    onToolbarEdit = { onToolbarEdit(msg) },
                    onToolbarPin = { onToolbarPin(msg) },
                    onToolbarInfo = { onToolbarInfo(msg) },
                    onToolbarStar = { onToolbarStar(msg) },
                    onToolbarFavoriteSticker = { onToolbarFavoriteSticker(msg) },
                    onToolbarSaveMedia = { onToolbarSaveMedia(msg) },
                    onToolbarDelete = { onToolbarDelete(msg) },
                )
            }

            if (isContactTyping) {
                item { TypingIndicatorRow() }
            }
        }
    }
}

@Composable
internal fun ChatMessageItemRow(
    msg: MothMessage,
    isSearchMatch: Boolean,
    inChatSearchQuery: String,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    showSingleToolbar: Boolean,
    placeToolbarAbove: Boolean,
    isJumpHighlighted: Boolean,
    isReceiptExpanded: Boolean,
    fontScale: Float,
    isGroup: Boolean,
    currentUsername: String,
    customReactionEmojis: List<String>,
    voicePlayer: VoiceNotePlayer,
    onToggleSelect: () -> Unit,
    onTapBubbleAction: () -> Unit,
    onSwipeToReply: () -> Unit,
    onToggleEmojiReaction: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onJumpToReply: (String) -> Unit,
    onVoicePlayStarted: (MothMessage) -> Unit,
    onViewOnceTap: () -> Unit,
    onFavoriteSticker: () -> Unit,
    onCallBack: () -> Unit,
    onToolbarReact: (String) -> Unit,
    onOpenEditReactions: () -> Unit,
    onToolbarReply: () -> Unit,
    onToolbarCopy: () -> Unit,
    onToolbarEdit: () -> Unit,
    onToolbarPin: () -> Unit,
    onToolbarInfo: () -> Unit,
    onToolbarStar: () -> Unit,
    onToolbarFavoriteSticker: () -> Unit,
    onToolbarSaveMedia: () -> Unit,
    onToolbarDelete: () -> Unit,
) {
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
            .then(if (inSelectionMode) Modifier.clickable(onClick = onToggleSelect) else Modifier),
        horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start,
    ) {
        if (showSingleToolbar && placeToolbarAbove) {
            SingleMessageToolbarItem(
                msg = msg,
                currentUsername = currentUsername,
                customReactionEmojis = customReactionEmojis,
                onReact = onToolbarReact,
                onOpenEditReactions = onOpenEditReactions,
                onReply = onToolbarReply,
                onCopy = onToolbarCopy,
                onEdit = onToolbarEdit,
                onPin = onToolbarPin,
                onInfo = onToolbarInfo,
                onStar = onToolbarStar,
                onFavoriteSticker = onToolbarFavoriteSticker,
                onSaveMedia = onToolbarSaveMedia,
                onDelete = onToolbarDelete,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        SwipeToReplyContainer(
            enabled = !msg.isDeleted && !inSelectionMode,
            onSwipedToReply = onSwipeToReply,
        ) {
            when (msg.type) {
                MessageType.DISAPPEARING -> {
                    DisappearingMessageBubble(
                        message = msg,
                        litEmbers = msg.embersRemaining,
                        fontScale = fontScale,
                        isGroup = isGroup,
                        isReceiptExpanded = isReceiptExpanded,
                        highlightQuery = if (isSearchMatch) inChatSearchQuery.trim() else "",
                        isJumpHighlighted = isJumpHighlighted,
                        onTapBubble = onTapBubbleAction,
                        onLongPress = onToggleSelect,
                        onToggleEmojiReaction = onToggleEmojiReaction,
                        onOpenPhoto = { path -> if (inSelectionMode) onToggleSelect() else onOpenPhoto(path) },
                        onOpenVideo = { path -> if (inSelectionMode) onToggleSelect() else onOpenVideo(path) },
                        onOpenFile = { path, mime -> if (inSelectionMode) onToggleSelect() else onOpenFile(path, mime) },
                        onJumpToReply = { replyId -> if (inSelectionMode) onToggleSelect() else onJumpToReply(replyId) },
                        onVoicePlayStarted = onVoicePlayStarted,
                        voicePlayer = voicePlayer,
                    )
                }
                MessageType.VIEW_ONCE -> {
                    ViewOnceCard(
                        message = msg,
                        isReceiptExpanded = isReceiptExpanded,
                        isJumpHighlighted = isJumpHighlighted,
                        onTapCard = onViewOnceTap,
                        onLongPress = onToggleSelect,
                    )
                }
                MessageType.BURNED_TRACE -> {
                    BurnedTraceBubble(
                        message = msg,
                        fontScale = fontScale,
                        inSelectionMode = inSelectionMode,
                        onToggleSelect = onToggleSelect,
                    )
                }
                MessageType.REGULAR -> {
                    StandardMessageBubble(
                        message = msg,
                        fontScale = fontScale,
                        isGroup = isGroup,
                        isReceiptExpanded = isReceiptExpanded,
                        highlightQuery = if (isSearchMatch) inChatSearchQuery.trim() else "",
                        isJumpHighlighted = isJumpHighlighted,
                        onTapBubble = onTapBubbleAction,
                        onLongPress = onToggleSelect,
                        onToggleEmojiReaction = onToggleEmojiReaction,
                        onOpenPhoto = { path -> if (inSelectionMode) onToggleSelect() else onOpenPhoto(path) },
                        onOpenVideo = { path -> if (inSelectionMode) onToggleSelect() else onOpenVideo(path) },
                        onOpenFile = { path, mime -> if (inSelectionMode) onToggleSelect() else onOpenFile(path, mime) },
                        onJumpToReply = { replyId -> if (inSelectionMode) onToggleSelect() else onJumpToReply(replyId) },
                        onVoicePlayStarted = onVoicePlayStarted,
                        onFavoriteSticker = { onFavoriteSticker() },
                        onCallBack = { if (inSelectionMode) onToggleSelect() else onCallBack() },
                        voicePlayer = voicePlayer,
                    )
                }
            }
        }

        if (showSingleToolbar && !placeToolbarAbove) {
            Spacer(modifier = Modifier.height(6.dp))
            SingleMessageToolbarItem(
                msg = msg,
                currentUsername = currentUsername,
                customReactionEmojis = customReactionEmojis,
                onReact = onToolbarReact,
                onOpenEditReactions = onOpenEditReactions,
                onReply = onToolbarReply,
                onCopy = onToolbarCopy,
                onEdit = onToolbarEdit,
                onPin = onToolbarPin,
                onInfo = onToolbarInfo,
                onStar = onToolbarStar,
                onFavoriteSticker = onToolbarFavoriteSticker,
                onSaveMedia = onToolbarSaveMedia,
                onDelete = onToolbarDelete,
            )
        }
    }
}

@Composable
internal fun SingleMessageToolbarItem(
    msg: MothMessage,
    currentUsername: String,
    customReactionEmojis: List<String>,
    onReact: (String) -> Unit,
    onOpenEditReactions: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onStar: () -> Unit,
    onFavoriteSticker: () -> Unit,
    onSaveMedia: () -> Unit,
    onDelete: () -> Unit,
) {
    val canSaveMedia = msg.type != MessageType.VIEW_ONCE &&
        msg.attachmentType != null &&
        msg.attachmentType != AttachmentType.CALL &&
        !msg.attachmentLocalPath.isNullOrBlank() &&
        File(msg.attachmentLocalPath).exists()
    val canFavSticker = msg.type != MessageType.VIEW_ONCE &&
        msg.attachmentType == AttachmentType.STICKER &&
        !msg.attachmentLocalPath.isNullOrBlank() &&
        File(msg.attachmentLocalPath).exists()

    SelectedMessageToolbar(
        message = msg,
        currentUsername = currentUsername,
        customReactionEmojis = customReactionEmojis,
        onReact = onReact,
        onOpenEditReactions = onOpenEditReactions,
        onReply = onReply,
        onCopy = if (msg.text.isNotBlank()) onCopy else null,
        onEdit = if (msg.isMine && msg.type == MessageType.REGULAR && msg.attachmentType == null) onEdit else null,
        onPin = onPin,
        onInfo = onInfo,
        onStar = onStar,
        onFavoriteSticker = if (canFavSticker) onFavoriteSticker else null,
        onSaveMedia = if (canSaveMedia) onSaveMedia else null,
        onDelete = onDelete,
    )
}

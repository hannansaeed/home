package com.example.chatapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    state: MothState,
    chatTitle: String,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val activeConv = state.getActiveConversation()
    val activeChatId = activeConv?.id ?: "1"
    val messages = activeConv?.messages ?: emptyList()

    var draft by remember { mutableStateOf("") }
    var selectedSendType by remember { mutableStateOf(MessageType.REGULAR) }
    var showCipherInfo by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size, state.isContactTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
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
        if (draft.isNotBlank()) {
            state.sendMessage(
                chatId = activeChatId,
                text = draft.trim(),
                type = selectedSendType,
            )
            draft = ""
            selectedSendType = MessageType.REGULAR
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("chat_screen"),
    ) {
        // 1. Header (matches Screenshot 2)
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
                // Left: Lantern icon + title "moth hours" (clickable to go back)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .testTag("chat_back_button"),
                ) {
                    PixelLantern(isLit = true)
                    Text(
                        text = "moth hours",
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
                        text = "channel: $chatTitle • 256-bit sealed pixel cipher",
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

            // Crescent Moon on bottom right (matches Screenshot 2)
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.type == MessageType.DISAPPEARING) {
                        DisappearingMessageBubble(
                            message = msg,
                            litEmbers = msg.embersRemaining,
                            onToggleReaction = { state.toggleReaction(activeChatId, msg.id) },
                        )
                    } else if (msg.type == MessageType.VIEW_ONCE) {
                        ViewOnceCard(
                            message = msg,
                            onOpen = { state.revealViewOnce(activeChatId, msg.id) },
                        )
                    } else {
                        StandardMessageBubble(
                            message = msg,
                            onToggleReaction = { state.toggleReaction(activeChatId, msg.id) },
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

        // 3. Bottom Input Bar (matches Screenshot 2)
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
                                    text = "say something",
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

                // Pixel wax-seal send button with pixel arrow (matches Screenshot 2)
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

/**
 * Standard Message Bubble (Theirs on left in purple, Mine on right in amber).
 */
@Composable
private fun StandardMessageBubble(
    message: MothMessage,
    onToggleReaction: () -> Unit,
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
                .clickable { onToggleReaction() }
                .testTag("message_bubble_${message.id}"),
            backgroundColor = bg,
            borderColor = border,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Text(
                text = message.text,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Reaction Chip under bubble (e.g. firefly + 2)
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
}

/**
 * Disappearing Message with row of 10 burning ember squares (matches Screenshot 2).
 */
@Composable
private fun DisappearingMessageBubble(
    message: MothMessage,
    litEmbers: Int,
    onToggleReaction: () -> Unit,
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
                .clickable { onToggleReaction() }
                .testTag("message_bubble_${message.id}"),
            backgroundColor = bg,
            borderColor = border,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Text(
                text = message.text,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

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
 * View-Once Card with pixel wax seal (matches Screenshot 2).
 */
@Composable
private fun ViewOnceCard(
    message: MothMessage,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        SteppedBox(
            modifier = Modifier
                .clickable { onOpen() }
                .testTag("view_once_card"),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelWaxSeal(hasArrow = false)

                Column {
                    val title = when (message.viewOnceStatus) {
                        ViewOnceStatus.UNOPENED -> "view once"
                        ViewOnceStatus.REVEALED -> message.text
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
        }
    }
}

/**
 * Typing Indicator with flapping moth sprite and pulsing 3 dots (matches Screenshot 2).
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

package com.example.chatapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay

enum class MessageType {
    REGULAR,
    DISAPPEARING,
    VIEW_ONCE
}

data class MothMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val type: MessageType = MessageType.REGULAR,
    var reactionCount: Int = 0,
    var viewOnceOpened: Boolean = false,
    var viewOnceRevealed: Boolean = false,
)

@Composable
fun ChatScreen(
    chatTitle: String = "mika",
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    // Initial mock messages matching Screenshot 2
    var messages by remember {
        mutableStateOf(
            listOf(
                MothMessage(
                    id = "1",
                    text = "you coming tonight?",
                    isMine = false,
                    type = MessageType.REGULAR,
                    reactionCount = 2,
                ),
                MothMessage(
                    id = "2",
                    text = "bringing the lanterns",
                    isMine = true,
                    type = MessageType.REGULAR,
                ),
                MothMessage(
                    id = "3",
                    text = "this one burns out soon",
                    isMine = false,
                    type = MessageType.DISAPPEARING,
                ),
                MothMessage(
                    id = "4",
                    text = "see you at the pier",
                    isMine = true,
                    type = MessageType.REGULAR,
                ),
                MothMessage(
                    id = "5",
                    text = "secret pier map: 3rd post by the tide",
                    isMine = false,
                    type = MessageType.VIEW_ONCE,
                ),
            )
        )
    }

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Disappearing message ember countdown (10 embers)
    var litEmbers by remember { mutableIntStateOf(6) }
    var disappearingBurnedOut by remember { mutableStateOf(false) }

    LaunchedEffect(litEmbers) {
        if (litEmbers > 0 && !disappearingBurnedOut) {
            delay(3500)
            litEmbers -= 1
            if (litEmbers == 0) {
                delay(1000)
                disappearingBurnedOut = true
            }
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.size)
    }

    fun sendMessage() {
        if (draft.isNotBlank()) {
            val newMsg = MothMessage(
                id = System.currentTimeMillis().toString(),
                text = draft.trim(),
                isMine = true,
                type = MessageType.REGULAR,
            )
            messages = messages + newMsg
            draft = ""
        }
    }

    fun toggleReaction(msgId: String) {
        messages = messages.map { msg ->
            if (msg.id == msgId) {
                val newCount = if (msg.reactionCount > 0) msg.reactionCount - 1 else 1
                msg.copy(reactionCount = newCount)
            } else msg
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
                // Left: Lantern icon + title "moth hours"
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
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(SecureGreen),
                    )
                    Text(
                        text = "sealed",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = SecureGreen,
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
                        if (!disappearingBurnedOut) {
                            DisappearingMessageBubble(
                                message = msg,
                                litEmbers = litEmbers,
                                onToggleReaction = { toggleReaction(msg.id) },
                            )
                        }
                    } else if (msg.type == MessageType.VIEW_ONCE) {
                        ViewOnceCard(
                            message = msg,
                            onOpen = {
                                messages = messages.map {
                                    if (it.id == msg.id) it.copy(viewOnceRevealed = !it.viewOnceRevealed)
                                    else it
                                }
                            },
                        )
                    } else {
                        StandardMessageBubble(
                            message = msg,
                            onToggleReaction = { toggleReaction(msg.id) },
                        )
                    }
                }

                // Typing indicator row (flapping moth + pulsing 3 dots)
                item {
                    TypingIndicatorRow()
                }
            }
        }

        // 3. Bottom Input Bar (matches Screenshot 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(width = 2.dp, color = MothDivider)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
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
                            keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("message_input_field"),
                        )
                    }
                }

                // Pixel wax-seal send button with pixel arrow (matches Screenshot 2)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(onClick = { sendMessage() })
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
        horizontalAlignment = Alignment.Start,
    ) {
        SteppedBox(
            modifier = Modifier
                .clickable { onToggleReaction() }
                .testTag("message_bubble_${message.id}"),
            backgroundColor = TheirBubbleFill,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Text(
                text = message.text,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = TheirBubbleText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row of 10 ember squares + "burning down"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp),
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
        horizontalAlignment = Alignment.Start,
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
                    Text(
                        text = if (message.viewOnceRevealed) message.text else "view once",
                        fontFamily = PixelifySans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TheirBubbleText,
                    )
                    Text(
                        text = if (message.viewOnceRevealed) "burned upon closing" else "tap to open",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
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

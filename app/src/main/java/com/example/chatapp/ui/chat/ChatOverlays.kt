package com.example.chatapp.ui.chat

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.AttachmentType
import com.example.chatapp.ui.moth.MessageStatus
import com.example.chatapp.ui.moth.MothConversation
import com.example.chatapp.ui.moth.MothMessage
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.moth.UserProfileDetails
import com.example.chatapp.ui.theme.*
import java.io.File
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FloatingReactionBar(
    emojis: List<String>,
    userReactions: Set<String>,
    onReact: (String) -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SteppedBox(
        modifier = modifier.testTag("floating_reaction_bar"),
        backgroundColor = MothHeaderBg,
        borderColor = LanternAmber,
        cutSize = 3.dp,
        borderWidth = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emojis.forEach { emoji ->
                val hasReacted = emoji in userReactions
                SteppedBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onReact(emoji) }
                        .testTag("reaction_pick_$emoji"),
                    backgroundColor = if (hasReacted) MyBubbleFill else MothBgDark,
                    borderColor = if (hasReacted) LanternAmber else MothDivider,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 17.sp)
                    }
                }
            }
            SteppedBox(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onOpenEdit)
                    .testTag("reaction_edit_button"),
                backgroundColor = MothBgDark,
                borderColor = TheirBubbleBorder,
                cutSize = 2.dp,
                borderWidth = 1.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = LanternAmber,
                    )
                }
            }
        }
    }
}

/**
 * Floating bar appearing right next to the selected message when exactly 1 message is selected.
 * Contains both the quick reaction line (with + to edit emojis) and the action buttons:
 * reply, edit (if mine), pin, info, star, delete.
 * Disappears automatically when 2 or more messages are selected.
 */
@Composable
fun SelectedMessageToolbar(
    message: MothMessage,
    currentUsername: String,
    customReactionEmojis: List<String>,
    onReact: (String) -> Unit,
    onOpenEditReactions: () -> Unit,
    onReply: () -> Unit,
    onEdit: (() -> Unit)?,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onStar: () -> Unit,
    onFavoriteSticker: (() -> Unit)? = null,
    onSaveMedia: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userReactions = message.reactions.filterValues { users ->
        users.any { it.equals(currentUsername, ignoreCase = true) }
    }.keys

    SteppedBox(
        modifier = modifier.testTag("selected_message_toolbar_${message.id}"),
        backgroundColor = MothHeaderBg,
        borderColor = LanternAmber,
        cutSize = 4.dp,
        borderWidth = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            // Line 1: Quick Reactions + '+' to customize emojis
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                customReactionEmojis.forEach { emoji ->
                    val hasReacted = emoji in userReactions
                    SteppedBox(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onReact(emoji) }
                            .testTag("reaction_pick_$emoji"),
                        backgroundColor = if (hasReacted) MyBubbleFill else MothBgDark,
                        borderColor = if (hasReacted) LanternAmber else MothDivider,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onOpenEditReactions)
                        .testTag("reaction_edit_button"),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = LanternAmber,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MothDivider)
            )

            // Line 2: Action buttons next to the selected message
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Reply Button
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onReply)
                        .testTag("action_reply_button"),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelReplyIcon(tint = TheirBubbleText, modifier = Modifier.size(16.dp))
                    }
                }

                // Edit Button (if regular text message authored by me)
                if (onEdit != null) {
                    SteppedBox(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(onClick = onEdit)
                            .testTag("action_edit_button"),
                        backgroundColor = MothBgDark,
                        borderColor = LanternAmber,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelEditIcon(tint = LanternAmber, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // Pin / Unpin Button
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onPin)
                        .testTag("action_pin_button"),
                    backgroundColor = if (message.isPinned) MyBubbleFill else MothBgDark,
                    borderColor = if (message.isPinned) LanternAmber else TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelPinIcon(
                            tint = if (message.isPinned) MyBubbleText else LanternAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Info Button
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onInfo)
                        .testTag("action_info_button"),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelInfoIcon(tint = SecureGreen, modifier = Modifier.size(15.dp))
                    }
                }

                // Star / Unstar Button
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onStar)
                        .testTag("action_star_button"),
                    backgroundColor = if (message.isStarred) MyBubbleFill else MothBgDark,
                    borderColor = if (message.isStarred) LanternAmber else TheirBubbleBorder,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelStarIcon(
                            tint = LanternAmber,
                            filled = message.isStarred,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Favourite sticker to My Stickers
                if (onFavoriteSticker != null) {
                    SteppedBox(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(onClick = onFavoriteSticker)
                            .testTag("action_favorite_sticker_button"),
                        backgroundColor = MothBgDark,
                        borderColor = LanternAmber,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelStickerIcon(tint = LanternAmber, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // Save media to device storage
                if (onSaveMedia != null) {
                    SteppedBox(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(onClick = onSaveMedia)
                            .testTag("action_save_media_button"),
                        backgroundColor = MothBgDark,
                        borderColor = SecureGreen,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelSaveIcon(tint = SecureGreen, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // Delete Button
                SteppedBox(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onDelete)
                        .testTag("action_delete_button"),
                    backgroundColor = MothBgDark,
                    borderColor = WaxSealRed.copy(alpha = 0.7f),
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelTrashIcon(tint = WaxSealRed, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

private data class EmojiChoice(
    val emoji: String,
    val category: String,
    val keywords: String,
)

private fun extractGraphemes(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val result = mutableListOf<String>()
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        val seg = text.substring(start, end)
        if (seg.isNotEmpty()) result.add(seg)
        start = end
        end = it.next()
    }
    return result
}

private fun isEmojiGrapheme(grapheme: String): Boolean {
    if (grapheme.isBlank()) return false
    return grapheme.any { ch -> Character.isSurrogate(ch) || ch.code >= 0x203C }
}

private val REACTION_CATALOG = listOf(
    // faces
    EmojiChoice("😂", "faces", "laugh lol joy tears funny"),
    EmojiChoice("🥹", "faces", "holding back tears emotional cute"),
    EmojiChoice("😍", "faces", "heart eyes love crush"),
    EmojiChoice("🥰", "faces", "smiling hearts love warm"),
    EmojiChoice("😘", "faces", "kiss love"),
    EmojiChoice("😁", "faces", "grin happy smile"),
    EmojiChoice("😅", "faces", "sweat nervous awkward haha"),
    EmojiChoice("🤣", "faces", "rofl laugh hard"),
    EmojiChoice("😊", "faces", "blush smile warm"),
    EmojiChoice("😌", "faces", "relieved calm chill"),
    EmojiChoice("😮", "faces", "wow surprised shocked gasp"),
    EmojiChoice("🤯", "faces", "mind blown shocked"),
    EmojiChoice("😳", "faces", "flushed shy embarrassed"),
    EmojiChoice("🥺", "faces", "pleading puppy eyes"),
    EmojiChoice("😢", "faces", "cry sad tear"),
    EmojiChoice("😭", "faces", "sob crying loud"),
    EmojiChoice("😡", "faces", "angry mad rage"),
    EmojiChoice("🤬", "faces", "swearing furious rage"),
    EmojiChoice("🥳", "faces", "party celebrate birthday"),
    EmojiChoice("😎", "faces", "cool sunglasses"),
    EmojiChoice("🤩", "faces", "star struck amazed"),
    EmojiChoice("🤔", "faces", "think hmm wonder"),
    EmojiChoice("🤭", "faces", "giggle oops"),
    EmojiChoice("🤫", "faces", "shh quiet secret"),
    EmojiChoice("😴", "faces", "sleep tired zzz"),
    EmojiChoice("🫠", "faces", "melting shy"),
    EmojiChoice("🫡", "faces", "salute respect"),
    EmojiChoice("😬", "faces", "awkward cringe"),
    EmojiChoice("🙄", "faces", "eyeroll whatever"),
    EmojiChoice("🤨", "faces", "sus skeptical eyebrow"),
    EmojiChoice("😏", "faces", "smirk sly"),
    EmojiChoice("😒", "faces", "unamused sideeye"),
    EmojiChoice("🤪", "faces", "crazy silly goofy"),
    EmojiChoice("🤪", "faces", "zany wild"),
    EmojiChoice("🤢", "faces", "sick gross"),
    EmojiChoice("🥶", "faces", "cold freezing"),
    EmojiChoice("🥵", "faces", "hot sweating"),
    EmojiChoice("😈", "faces", "devil smirk mischievous"),
    EmojiChoice("🤡", "faces", "clown joke"),
    // hearts
    EmojiChoice("❤️", "hearts", "red heart love"),
    EmojiChoice("🧡", "hearts", "orange heart"),
    EmojiChoice("💛", "hearts", "yellow heart"),
    EmojiChoice("💚", "hearts", "green heart"),
    EmojiChoice("🩵", "hearts", "light blue heart"),
    EmojiChoice("💙", "hearts", "blue heart"),
    EmojiChoice("💜", "hearts", "purple heart"),
    EmojiChoice("🖤", "hearts", "black heart dark"),
    EmojiChoice("🩶", "hearts", "grey heart"),
    EmojiChoice("🤍", "hearts", "white heart"),
    EmojiChoice("🩷", "hearts", "pink heart"),
    EmojiChoice("💖", "hearts", "sparkling heart"),
    EmojiChoice("💗", "hearts", "growing heart"),
    EmojiChoice("💓", "hearts", "beating heart"),
    EmojiChoice("💘", "hearts", "cupid arrow heart"),
    EmojiChoice("💝", "hearts", "ribbon gift heart"),
    EmojiChoice("💔", "hearts", "broken heart"),
    EmojiChoice("❤️‍🔥", "hearts", "heart on fire passion"),
    EmojiChoice("❤️‍🩹", "hearts", "healing mending heart"),
    EmojiChoice("🫶", "hearts", "heart hands love"),
    // hands
    EmojiChoice("👍", "hands", "thumbs up yes good like"),
    EmojiChoice("👎", "hands", "thumbs down no dislike"),
    EmojiChoice("👏", "hands", "clap applause"),
    EmojiChoice("🙌", "hands", "celebrate praise hands"),
    EmojiChoice("🙏", "hands", "pray please thank"),
    EmojiChoice("🤝", "hands", "handshake deal"),
    EmojiChoice("✌️", "hands", "peace victory"),
    EmojiChoice("🤞", "hands", "fingers crossed luck"),
    EmojiChoice("👌", "hands", "ok perfect"),
    EmojiChoice("🤌", "hands", "pinched chef kiss"),
    EmojiChoice("🤙", "hands", "call me shaka"),
    EmojiChoice("🤘", "hands", "rock on metal"),
    EmojiChoice("🤟", "hands", "love you"),
    EmojiChoice("💪", "hands", "strong muscle flex"),
    EmojiChoice("👋", "hands", "wave hello bye"),
    EmojiChoice("🫶", "hands", "heart hands"),
    EmojiChoice("🫵", "hands", "point you"),
    EmojiChoice("🫰", "hands", "finger heart snap"),
    EmojiChoice("🫂", "hands", "hug comfort"),
    // vibes & night
    EmojiChoice("🔥", "vibes", "fire lit flame hot"),
    EmojiChoice("✨", "vibes", "sparkles magic shine"),
    EmojiChoice("🦋", "vibes", "butterfly moth"),
    EmojiChoice("🏮", "vibes", "lantern lamp"),
    EmojiChoice("🌙", "vibes", "moon night crescent"),
    EmojiChoice("⭐", "vibes", "star favorite"),
    EmojiChoice("🌟", "vibes", "glowing star"),
    EmojiChoice("⚡", "vibes", "lightning bolt fast"),
    EmojiChoice("💫", "vibes", "dizzy shooting star"),
    EmojiChoice("💯", "vibes", "100 percent perfect"),
    EmojiChoice("🎉", "vibes", "confetti party"),
    EmojiChoice("🎊", "vibes", "party ball"),
    EmojiChoice("🏆", "vibes", "trophy win champion"),
    EmojiChoice("👑", "vibes", "crown king queen"),
    EmojiChoice("💀", "vibes", "skull dead funny"),
    EmojiChoice("☠️", "vibes", "skull crossbones"),
    EmojiChoice("👻", "vibes", "ghost spooky"),
    EmojiChoice("👀", "vibes", "eyes look"),
    EmojiChoice("🧠", "vibes", "brain smart"),
    EmojiChoice("🎵", "vibes", "music note song"),
    EmojiChoice("🎶", "vibes", "music notes"),
    EmojiChoice("🎧", "vibes", "headphones music"),
    EmojiChoice("☕", "vibes", "coffee tea"),
    EmojiChoice("🍕", "vibes", "pizza food"),
    EmojiChoice("🍿", "vibes", "popcorn drama movie"),
    EmojiChoice("🌹", "vibes", "rose flower"),
    EmojiChoice("🌸", "vibes", "cherry blossom flower"),
    EmojiChoice("🌈", "vibes", "rainbow"),
    EmojiChoice("🌊", "vibes", "ocean wave"),
    EmojiChoice("❄️", "vibes", "snow cold"),
    EmojiChoice("💥", "vibes", "boom bang impact"),
    EmojiChoice("💬", "vibes", "speech bubble"),
    EmojiChoice("🚩", "vibes", "red flag"),
    EmojiChoice("✅", "vibes", "check yes done"),
    EmojiChoice("❌", "vibes", "no cross wrong"),
    EmojiChoice("❓", "vibes", "question what"),
    EmojiChoice("‼️", "vibes", "exclamation shock"),
)

@Composable
fun EditReactionsDialog(
    currentEmojis: List<String>,
    onSave: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var emojisList by remember(currentEmojis) {
        mutableStateOf(currentEmojis.ifEmpty { listOf("❤️", "🔥", "😂", "😮", "😢", "👍", "✨") })
    }
    var selectedSlotIdx by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("all") }
    val keyboardInputFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeSelectedIdx = selectedSlotIdx.coerceIn(0, (emojisList.size - 1).coerceAtLeast(0))

    fun applyEmojiToSelectedSlot(emoji: String) {
        val current = emojisList.toMutableList()
        if (current.isEmpty()) return
        val existingIdx = current.indexOf(emoji)
        if (existingIdx >= 0 && existingIdx != safeSelectedIdx) {
            val prev = current[safeSelectedIdx]
            current[safeSelectedIdx] = emoji
            current[existingIdx] = prev
        } else {
            current[safeSelectedIdx] = emoji
        }
        emojisList = current
        selectedSlotIdx = (safeSelectedIdx + 1) % current.size
    }

    fun handleEmojiInputChange(raw: String) {
        val graphemes = extractGraphemes(raw)
        val picked = graphemes.filter { isEmojiGrapheme(it) }
        if (picked.isNotEmpty()) {
            picked.forEach { applyEmojiToSelectedSlot(it) }
            searchQuery = graphemes.filterNot { isEmojiGrapheme(it) }.joinToString("")
        } else {
            searchQuery = raw
        }
    }

    val filteredCatalog = remember(searchQuery, activeCategory) {
        val q = searchQuery.trim().lowercase()
        REACTION_CATALOG.filter { item ->
            val catOk = activeCategory == "all" || item.category == activeCategory
            val queryOk = q.isBlank() ||
                item.emoji.contains(q) ||
                item.keywords.contains(q) ||
                item.category.contains(q)
            catOk && queryOk
        }.distinctBy { it.emoji }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A1633))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(enabled = false) {}
                .testTag("edit_reactions_dialog"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "customize reactions",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "✕",
                        fontSize = 15.sp,
                        color = MutedText,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }

                // Instagram-style top quick reaction bar with selectable active slot
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                    borderWidth = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            emojisList.forEachIndexed { index, emoji ->
                                val isSlotSelected = index == safeSelectedIdx
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    SteppedBox(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable { selectedSlotIdx = index }
                                            .testTag("reaction_slot_$index"),
                                        backgroundColor = if (isSlotSelected) MyBubbleFill else MothHeaderBg,
                                        borderColor = if (isSlotSelected) LanternAmber else MothDivider,
                                        cutSize = 3.dp,
                                        borderWidth = if (isSlotSelected) 2.dp else 1.dp,
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(text = emoji, fontSize = 19.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(3.dp)
                                            .background(if (isSlotSelected) LanternAmber else Color.Transparent),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "tap a slot above, then pick from list or keyboard",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = MutedText,
                        )
                    }
                }

                // Search OR type/pick directly from soft emoji keyboard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SteppedBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        cutSize = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PixelSearchIcon(tint = MutedText, modifier = Modifier.size(14.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "search or tap emoji key...",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { handleEmojiInputChange(it) },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(keyboardInputFocus)
                                        .testTag("custom_reaction_emoji_input"),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "✕",
                                    fontSize = 12.sp,
                                    color = MutedText,
                                    modifier = Modifier.clickable { searchQuery = "" },
                                )
                            }
                        }
                    }

                    SteppedButton(
                        text = "keyboard",
                        onClick = {
                            keyboardInputFocus.requestFocus()
                            keyboardController?.show()
                        },
                        backgroundColor = MothBgDark,
                        borderColor = LanternAmber,
                        textColor = LanternAmber,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("open_emoji_keyboard_button"),
                    )
                }

                // Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("all", "faces", "hearts", "hands", "vibes").forEach { cat ->
                        val active = activeCategory == cat
                        SteppedBox(
                            modifier = Modifier
                                .clickable { activeCategory = cat }
                                .testTag("reaction_cat_$cat"),
                            backgroundColor = if (active) MyBubbleFill else MothBgDark,
                            borderColor = if (active) LanternAmber else MothDivider,
                            cutSize = 2.dp,
                            borderWidth = 1.dp,
                        ) {
                            Text(
                                text = cat,
                                fontFamily = PixelifySans,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                color = if (active) MyBubbleText else MutedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // Scrollable Emoji Picker Grid (plus keyboard emoji prompt in "all")
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    backgroundColor = MothBgDark,
                    borderColor = MothDivider,
                    cutSize = 3.dp,
                    borderWidth = 1.dp,
                ) {
                    if (filteredCatalog.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "no matching emojis — use keyboard button",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = MutedText,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (activeCategory == "all" && searchQuery.isBlank()) {
                                SteppedBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            keyboardInputFocus.requestFocus()
                                            keyboardController?.show()
                                        }
                                        .testTag("all_emojis_keyboard_banner"),
                                    backgroundColor = MothHeaderBg,
                                    borderColor = LanternAmber.copy(alpha = 0.7f),
                                    cutSize = 2.dp,
                                    borderWidth = 1.dp,
                                ) {
                                    Text(
                                        text = "tap here to open full phone emoji keyboard",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = LanternAmber,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    )
                                }
                            }
                            filteredCatalog.chunked(7).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowItems.forEach { choice ->
                                        val isCurrentSlotValue = emojisList.getOrNull(safeSelectedIdx) == choice.emoji
                                        val isInBar = choice.emoji in emojisList
                                        SteppedBox(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { applyEmojiToSelectedSlot(choice.emoji) }
                                                .testTag("reaction_preset_${choice.emoji}"),
                                            backgroundColor = when {
                                                isCurrentSlotValue -> MyBubbleFill
                                                isInBar -> MothHeaderBg
                                                else -> Color.Transparent
                                            },
                                            borderColor = when {
                                                isCurrentSlotValue -> LanternAmber
                                                isInBar -> TheirBubbleBorder
                                                else -> Color.Transparent
                                            },
                                            cutSize = 2.dp,
                                            borderWidth = 1.dp,
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(text = choice.emoji, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                    repeat(7 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Reset / Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "reset",
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        backgroundColor = MothBgDark,
                        borderColor = MothDivider,
                        textColor = MutedText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_reactions_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "done",
                        onClick = {
                            if (emojisList.isNotEmpty()) {
                                onSave(emojisList)
                            }
                            onDismiss()
                        },
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_reactions_button"),
                        cutSize = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteChoiceDialog(
    count: Int = 1,
    canDeleteForEveryone: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A1633))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .testTag("delete_choice_dialog"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (count > 1) "delete $count messages?" else "delete message?",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LanternAmber,
                )
                if (canDeleteForEveryone) {
                    SteppedButton(
                        text = "delete for everyone",
                        onClick = onDeleteForEveryone,
                        backgroundColor = WaxSealRed,
                        borderColor = WaxSealDarkEdge,
                        textColor = Color.White,
                        modifier = Modifier.fillMaxWidth().testTag("action_delete_everyone"),
                        cutSize = 3.dp,
                    )
                }
                SteppedButton(
                    text = "delete for me",
                    onClick = onDeleteForMe,
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    modifier = Modifier.fillMaxWidth().testTag("action_delete_me"),
                    cutSize = 3.dp,
                )
                SteppedButton(
                    text = "cancel",
                    onClick = onDismiss,
                    backgroundColor = MothBgDark,
                    borderColor = MothDivider,
                    textColor = MutedText,
                    modifier = Modifier.fillMaxWidth(),
                    cutSize = 3.dp,
                )
            }
        }
    }
}

@Composable
fun MessageInfoModal(
    message: MothMessage,
    onDismiss: () -> Unit,
) {
    fun fmt(ms: Long): String {
        if (ms <= 0L) return "pending"
        return SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(ms)).lowercase()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A1633))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .testTag("message_info_modal"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "message info",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        color = MutedText,
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                }

                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MothBgDark,
                    borderColor = MothDivider,
                    cutSize = 3.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val isVoice = message.attachmentType == AttachmentType.VOICE
                        val statusLabel = if (isVoice && message.listenedAt > 0L) {
                            "listened"
                        } else if (message.isMine) {
                            when (message.status) {
                                MessageStatus.SENDING -> "sending..."
                                MessageStatus.SENT -> "sent"
                                MessageStatus.DELIVERED -> "delivered"
                                MessageStatus.READ -> "seen"
                            }
                        } else {
                            when (message.status) {
                                MessageStatus.READ -> "seen"
                                else -> "received"
                            }
                        }
                        InfoRow("status", statusLabel, SecureGreen)
                        InfoRow("sent at", fmt(message.sentAt))
                        InfoRow(if (message.isMine) "delivered at" else "received at", fmt(message.deliveredAt))
                        InfoRow("seen at", fmt(message.readAt))
                        if (isVoice) {
                            InfoRow(
                                label = "listened at",
                                value = fmt(message.listenedAt),
                                valueColor = if (message.listenedAt > 0L) SecureGreen else TheirBubbleText,
                            )
                        }
                        InfoRow("encryption", "ECDH P-256 + AES-GCM", LanternAmber)
                    }
                }

                SteppedButton(
                    text = "close",
                    onClick = onDismiss,
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

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = TheirBubbleText) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontFamily = PixelifySans, fontSize = 12.sp, color = MutedText)
        Text(text = value, fontFamily = PixelifySans, fontSize = 12.sp, color = valueColor)
    }
}

@Composable
fun ViewOnceMediaOverlay(
    state: MothState,
    message: MothMessage,
    onCloseAndBurn: () -> Unit,
) {
    DisposableEffect(message.id) {
        state.isViewingViewOnce = true
        onDispose {
            state.isViewingViewOnce = false
        }
    }

    BackHandler {
        onCloseAndBurn()
    }

    val path = message.attachmentLocalPath
    val fileExists = !path.isNullOrBlank() && File(path).exists()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B1A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("view_once_viewer_overlay"),
    ) {
        // Protected Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 72.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                message.attachmentType == AttachmentType.VIDEO && fileExists && path != null -> {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.fromFile(File(path)))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 480.dp),
                    )
                }
                (message.attachmentType == AttachmentType.PHOTO || message.attachmentType == AttachmentType.STICKER) && fileExists && path != null -> {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "View once media",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    SteppedBox(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MothHeaderBg,
                        borderColor = LanternAmber,
                        cutSize = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PixelViewOnceIcon(tint = LanternAmber, active = true, modifier = Modifier.size(28.dp))
                            Text(
                                text = message.text.ifBlank { message.attachmentName ?: "view once secret" },
                                fontFamily = PixelifySans,
                                fontSize = 17.sp,
                                color = TheirBubbleText,
                            )
                        }
                    }
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xCC1A1633))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelViewOnceIcon(tint = LanternAmber, active = true, modifier = Modifier.size(18.dp))
                Column {
                    Text(
                        text = "@${message.senderUsername} • view once",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "screen capture blocked • burns on close",
                        fontFamily = PixelifySans,
                        fontSize = 10.sp,
                        color = MutedText,
                    )
                }
            }

            SteppedButton(
                text = "✕ close",
                onClick = onCloseAndBurn,
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                textColor = LanternAmber,
                cutSize = 3.dp,
                modifier = Modifier.testTag("close_view_once_button"),
            )
        }

        // Caption Bar if present
        if (message.text.isNotBlank() && message.attachmentType != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC1A1633))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message.text,
                    fontFamily = PixelifySans,
                    fontSize = 14.sp,
                    color = TheirBubbleText,
                )
            }
        }
    }
}

@Composable
fun ContactProfileOverlay(
    state: MothState,
    contactUsername: String,
    conversation: MothConversation?,
    messages: List<MothMessage>,
    onDismiss: () -> Unit,
    onCallVoice: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String, String?) -> Unit,
) {
    BackHandler { onDismiss() }

    var profileDetails by remember(contactUsername) { mutableStateOf<UserProfileDetails?>(null) }
    var isLoading by remember(contactUsername) { mutableStateOf(true) }
    var mediaTab by remember { mutableStateOf("media") } // media | files | voice | starred

    LaunchedEffect(contactUsername) {
        isLoading = true
        profileDetails = state.fetchUserProfile(contactUsername)
        isLoading = false
    }

    val visibleMessages = remember(messages) {
        messages.filter {
            !it.isDeleted &&
                it.type != com.example.chatapp.ui.moth.MessageType.VIEW_ONCE &&
                it.type != com.example.chatapp.ui.moth.MessageType.BURNED_TRACE &&
                !it.attachmentLocalPath.isNullOrBlank() &&
                File(it.attachmentLocalPath).exists()
        }
    }
    val sharedMedia = remember(visibleMessages) {
        visibleMessages.filter {
            it.attachmentType == AttachmentType.PHOTO || it.attachmentType == AttachmentType.VIDEO
        }.reversed()
    }
    val sharedFiles = remember(visibleMessages) {
        visibleMessages.filter { it.attachmentType == AttachmentType.FILE }.reversed()
    }
    val sharedVoice = remember(visibleMessages) {
        visibleMessages.filter { it.attachmentType == AttachmentType.VOICE }.reversed()
    }
    val starredMessages = remember(messages) {
        messages.filter { it.isStarred && !it.isDeleted }.reversed()
    }

    val isSupport = contactUsername.equals("support", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .testTag("contact_profile_overlay"),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onDismiss)
                        .testTag("contact_profile_back"),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelBackArrowIcon(tint = LanternAmber, modifier = Modifier.size(26.dp))
                }
                Text(
                    text = "contact profile",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = LanternAmber,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Identity Card
            item {
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MothHeaderBg,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UserAvatar(
                            avatarPath = profileDetails?.avatarPath ?: conversation?.avatarLocalPath,
                            color = profileDetails?.avatarColor ?: conversation?.avatarColor ?: LanternAmber,
                            size = 76.dp,
                        )
                        Text(
                            text = "@${profileDetails?.username ?: contactUsername}",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = TheirBubbleText,
                        )
                        val isOnline = profileDetails?.isOnline ?: state.activeContactPresence.isOnline
                        val statusText = profileDetails?.statusLabel ?: state.activeContactPresence.label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isOnline) SecureGreen else MutedText),
                            )
                            Text(
                                text = if (isLoading && profileDetails == null) "checking lantern..." else statusText,
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = if (isOnline) SecureGreen else MutedText,
                            )
                        }

                        val bioText = profileDetails?.bio.orEmpty()
                        if (bioText.isNotBlank()) {
                            Text(
                                text = bioText,
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = TheirBubbleText.copy(alpha = 0.85f),
                            )
                        }

                        if (!isSupport) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SteppedButton(
                                    text = "call",
                                    onClick = {
                                        onDismiss()
                                        onCallVoice()
                                    },
                                    backgroundColor = MothBgDark,
                                    borderColor = LanternAmber,
                                    textColor = LanternAmber,
                                    leadingIcon = { PixelPhoneIcon(tint = LanternAmber, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f),
                                    cutSize = 3.dp,
                                )
                                SteppedButton(
                                    text = "search",
                                    onClick = {
                                        onDismiss()
                                        onOpenSearch()
                                    },
                                    backgroundColor = MothBgDark,
                                    borderColor = TheirBubbleBorder,
                                    textColor = TheirBubbleText,
                                    leadingIcon = { PixelSearchIcon(tint = TheirBubbleText, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f),
                                    cutSize = 3.dp,
                                )
                            }
                        }
                    }
                }
            }

            // Details & Encryption Card
            item {
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoRow("joined", profileDetails?.joinedLabel ?: if (isSupport) "system lantern" else "loading...")
                        InfoRow("messages", "${profileDetails?.localMessageCount ?: messages.size}", LanternAmber)
                        InfoRow("cipher", "ECDH P-256 + AES-GCM", SecureGreen)
                        InfoRow("key id", state.getPeerKeyFingerprint(contactUsername), MutedText)
                    }
                }
            }

            // Shared Media, Files, Voice & Starred Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "shared media & starred",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LanternAmber,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val tabs = listOf(
                            "media" to "media (${sharedMedia.size})",
                            "files" to "files (${sharedFiles.size})",
                            "voice" to "voice (${sharedVoice.size})",
                            "starred" to "star (${starredMessages.size})",
                        )
                        tabs.forEach { (key, label) ->
                            val selected = mediaTab == key
                            SteppedBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { mediaTab = key }
                                    .testTag("profile_tab_$key"),
                                backgroundColor = if (selected) MyBubbleFill else MothHeaderBg,
                                borderColor = if (selected) MyBubbleBorder else MothDivider,
                                cutSize = 2.dp,
                                borderWidth = 1.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = PixelifySans,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        color = if (selected) MyBubbleText else MutedText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (mediaTab) {
                "media" -> {
                    if (sharedMedia.isEmpty()) {
                        item {
                            EmptySharedCard("no shared photos or videos yet")
                        }
                    } else {
                        items(sharedMedia.chunked(3)) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { msg ->
                                    val p = msg.attachmentLocalPath!!
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(SteppedCornerShape(3.dp))
                                            .background(MothHeaderBg)
                                            .border(1.dp, TheirBubbleBorder, SteppedCornerShape(3.dp))
                                            .clickable {
                                                if (msg.attachmentType == AttachmentType.VIDEO) onOpenVideo(p) else onOpenPhoto(p)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (msg.attachmentType == AttachmentType.VIDEO) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                PixelPlayIcon(tint = LanternAmber, modifier = Modifier.size(22.dp))
                                                Text(
                                                    text = "video",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 10.sp,
                                                    color = TheirBubbleText,
                                                )
                                            }
                                        } else {
                                            AsyncImage(
                                                model = File(p),
                                                contentDescription = msg.attachmentName ?: "Shared photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                "files" -> {
                    if (sharedFiles.isEmpty()) {
                        item { EmptySharedCard("no shared files yet") }
                    } else {
                        items(sharedFiles, key = { it.id }) { msg ->
                            val path = msg.attachmentLocalPath!!
                            SteppedBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(path, msg.attachmentMimeType) },
                                backgroundColor = MothHeaderBg,
                                borderColor = MothDivider,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PixelFileIcon(tint = LanternAmber)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = msg.attachmentName ?: "attachment",
                                            fontFamily = PixelifySans,
                                            fontSize = 12.sp,
                                            color = TheirBubbleText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${msg.time} • tap to open",
                                            fontFamily = PixelifySans,
                                            fontSize = 10.sp,
                                            color = MutedText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "starred" -> {
                    if (starredMessages.isEmpty()) {
                        item { EmptySharedCard("triple-tap any message to star it") }
                    } else {
                        items(starredMessages, key = { it.id }) { msg ->
                            val preview = msg.text.ifBlank {
                                msg.attachmentName ?: (msg.attachmentType?.name?.lowercase() ?: "message")
                            }
                            SteppedBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_starred_${msg.id}"),
                                backgroundColor = MothHeaderBg,
                                borderColor = TheirBubbleBorder,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PixelStarIcon(tint = LanternAmber, filled = true, modifier = Modifier.size(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${msg.senderUsername} • ${msg.time}",
                                            fontFamily = PixelifySans,
                                            fontSize = 10.sp,
                                            color = LanternAmber,
                                        )
                                        Text(
                                            text = preview,
                                            fontFamily = PixelifySans,
                                            fontSize = 12.sp,
                                            color = TheirBubbleText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val chatId = conversation?.id
                                        ?: "${state.currentUsername.lowercase()}::${contactUsername.trim().lowercase()}"
                                    Text(
                                        text = "unstar",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                        modifier = Modifier
                                            .clickable { state.toggleStarMessage(chatId, msg.id) }
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (sharedVoice.isEmpty()) {
                        item { EmptySharedCard("no shared voice notes yet") }
                    } else {
                        items(sharedVoice, key = { it.id }) { msg ->
                            val dur = msg.audioDurationSec
                            val durLabel = "${dur / 60}:${"%02d".format(dur % 60)}"
                            SteppedBox(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = MothHeaderBg,
                                borderColor = MothDivider,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PixelMicIcon(tint = LanternAmber, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "@${msg.senderUsername} • voice ($durLabel)",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = TheirBubbleText,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = msg.time,
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySharedCard(label: String) {
    SteppedBox(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MothHeaderBg,
        borderColor = MothDivider,
        cutSize = 3.dp,
        borderWidth = 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
            )
        }
    }
}

@Composable
fun ForwardTargetModal(
    count: Int = 1,
    conversations: List<MothConversation>,
    onSelect: (MothConversation) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A1633))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clickable(enabled = false) {}
                .testTag("forward_target_modal"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (count > 1) "forward $count messages to..." else "forward message to...",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        color = MutedText,
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MothBgDark)
                                .border(1.dp, MothDivider)
                                .clickable { onSelect(conv) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (conv.isGroup) "${conv.groupIcon} ${conv.name}" else "@${conv.name}",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TheirBubbleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "send",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = LanternAmber,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupInfoModal(
    state: MothState,
    conversation: MothConversation,
    onDismiss: () -> Unit,
) {
    val isAdmin = conversation.admins.any { it.equals(state.currentUsername, ignoreCase = true) }
    var newMemberHandle by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A1633))
            .clickable(onClick = onDismiss)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .clickable(enabled = false) {}
                .testTag("group_info_modal"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = conversation.groupIcon, fontSize = 20.sp)
                        Column {
                            Text(
                                text = conversation.name,
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = LanternAmber,
                            )
                            Text(
                                text = "${conversation.members.size} members",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = MutedText,
                            )
                        }
                    }
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        color = MutedText,
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                }

                if (isAdmin) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SteppedBox(
                            modifier = Modifier.weight(1f).height(38.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newMemberHandle.isEmpty()) {
                                    Text(
                                        text = "add member handle...",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = newMemberHandle,
                                    onValueChange = { newMemberHandle = it; errorMsg = null },
                                    textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("add_group_member_input"),
                                )
                            }
                        }
                        SteppedButton(
                            text = "+ add",
                            onClick = {
                                if (newMemberHandle.isNotBlank()) {
                                    state.addGroupMember(conversation.id, newMemberHandle) { err ->
                                        if (err != null) errorMsg = err else newMemberHandle = ""
                                    }
                                }
                            },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            cutSize = 3.dp,
                            modifier = Modifier.testTag("add_group_member_button"),
                        )
                    }
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg.orEmpty(),
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = WaxSealRed,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(conversation.members, key = { it }) { member ->
                        val memberIsAdmin = conversation.admins.any { it.equals(member, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MothBgDark)
                                .border(1.dp, MothDivider)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "@$member",
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                )
                                if (memberIsAdmin) {
                                    Text(
                                        text = "ADMIN",
                                        fontFamily = PixelifySans,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LanternAmber,
                                        modifier = Modifier
                                            .border(1.dp, LanternAmber)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            if (isAdmin && !member.equals(state.currentUsername, ignoreCase = true)) {
                                Text(
                                    text = "remove",
                                    fontFamily = PixelifySans,
                                    fontSize = 11.sp,
                                    color = WaxSealRed,
                                    modifier = Modifier
                                        .clickable { state.removeGroupMember(conversation.id, member) }
                                        .padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

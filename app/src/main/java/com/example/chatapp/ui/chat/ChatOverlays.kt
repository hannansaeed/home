package com.example.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.MothConversation
import com.example.chatapp.ui.moth.MothMessage
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.moth.MessageStatus
import com.example.chatapp.ui.moth.MessageType
import com.example.chatapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK_REACTIONS = listOf("❤️", "🔥", "😂", "😮", "😢", "👍", "✨", "🦋")

@Composable
fun MessageReactionAndActionModal(
    message: MothMessage,
    currentUsername: String,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onInfo: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
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
                .clickable(enabled = false) {}
                .testTag("message_action_modal"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Emoji Reaction Picker Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val hasReacted = message.reactions[emoji]?.any {
                            it.equals(currentUsername, ignoreCase = true)
                        } == true
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
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                if (!message.isDeleted) {
                    ActionSheetRow("reply", TheirBubbleText, "action_reply", onReply) {
                        PixelReplyIcon(tint = TheirBubbleText)
                    }
                    ActionSheetRow("forward", TheirBubbleText, "action_forward", onForward) {
                        PixelForwardIcon(tint = TheirBubbleText)
                    }
                    if (message.type == MessageType.REGULAR) {
                        ActionSheetRow(
                            if (message.isPinned) "unpin message" else "pin message",
                            LanternAmber,
                            "action_pin",
                            onTogglePin,
                        ) {
                            PixelPinIcon(tint = LanternAmber)
                        }
                    }
                    ActionSheetRow("message info", SecureGreen, "action_info", onInfo) {
                        PixelInfoIcon(tint = SecureGreen)
                    }
                    if (message.isMine && message.type == MessageType.REGULAR && message.attachmentType == null) {
                        ActionSheetRow("edit", LanternAmber, "action_edit", onEdit) {
                            PixelEditIcon(tint = LanternAmber)
                        }
                    }
                }

                ActionSheetRow("delete for me", MutedText, "action_delete_me", onDeleteForMe) {
                    PixelTrashIcon(tint = MutedText)
                }
                if (message.isMine && !message.isDeleted) {
                    ActionSheetRow("delete for everyone", WaxSealRed, "action_delete_everyone", onDeleteForEveryone) {
                        PixelBlockIcon(tint = WaxSealRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    label: String,
    color: Color,
    tag: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MothBgDark)
            .border(1.dp, MothDivider)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(
            text = label,
            fontFamily = PixelifySans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
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
                        InfoRow("status", when (message.status) {
                            MessageStatus.SENDING -> "sending..."
                            MessageStatus.SENT -> "sent"
                            MessageStatus.DELIVERED -> "delivered"
                            MessageStatus.READ -> "seen / read"
                        }, SecureGreen)
                        InfoRow("sent at", fmt(message.sentAt))
                        InfoRow("delivered at", fmt(message.deliveredAt))
                        InfoRow("read at", fmt(message.readAt))
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
fun ForwardTargetModal(
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
                        text = "forward message to...",
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
                                text = "send →",
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

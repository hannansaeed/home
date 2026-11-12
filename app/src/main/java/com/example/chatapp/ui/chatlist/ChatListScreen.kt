package com.example.chatapp.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.theme.*

data class MothConversation(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val avatarColor: Color,
    val unreadCount: Int = 0,
    val isDisappearing: Boolean = false,
    val isViewOnce: Boolean = false,
)

@Composable
fun ChatListScreen(
    onOpenChat: (title: String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf("chats") }

    val conversations = remember {
        listOf(
            MothConversation(
                id = "1",
                name = "mika",
                preview = "bringing the lanterns",
                time = "9:41",
                avatarColor = Color(0xFFB8A9E8), // Lavender
                unreadCount = 2,
            ),
            MothConversation(
                id = "2",
                name = "pier crew",
                preview = "juno: who has the keys",
                time = "8:15",
                avatarColor = Color(0xFF82D8A7), // Mint green
                unreadCount = 5,
            ),
            MothConversation(
                id = "3",
                name = "sam",
                preview = "disappearing",
                time = "mon",
                avatarColor = Color(0xFFF38D71), // Coral
                isDisappearing = true,
            ),
            MothConversation(
                id = "4",
                name = "ro",
                preview = "view once, unopened",
                time = "sun",
                avatarColor = Color(0xFFFFD152), // Yellow
                isViewOnce = true,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("chat_list_screen"),
    ) {
        // 1. Header (matches Screenshot 1)
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
                // Left: Lantern + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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

                // Right: Search icon + Plus icon (in amber)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "⌕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternAmber,
                        modifier = Modifier.clickable { },
                    )
                    Text(
                        text = "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternAmber,
                        modifier = Modifier.clickable { onOpenChat("new lantern") },
                    )
                }
            }
        }

        // 2. "lit now" Section (matches Screenshot 1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "lit now",
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    color = MutedText,
                )
                PixelMoth(isAnimated = true)
            }

            // 5 Lantern Avatars Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LitNowItem(name = "mika", isLit = true, onClick = { onOpenChat("mika") })
                LitNowItem(name = "juno", isLit = true, onClick = { onOpenChat("juno") })
                LitNowItem(name = "sam", isLit = false, onClick = { onOpenChat("sam") })
                LitNowItem(name = "ro", isLit = true, onClick = { onOpenChat("ro") })
                LitNowItem(name = "kit", isLit = true, onClick = { onOpenChat("kit") })
            }
        }

        // Section Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MothDivider),
        )

        // 3. Conversation List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(conversations, key = { it.id }) { conv ->
                ConversationRow(
                    conversation = conv,
                    onClick = { onOpenChat(conv.name) },
                )
                // Divider between rows
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MothDivider),
                )
            }
        }

        // 4. Bottom Tab Bar (matches Screenshot 1: chats, games, stickers, me)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(width = 2.dp, color = MothDivider)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(
                    label = "chats",
                    iconSymbol = "💬",
                    isSelected = activeTab == "chats",
                    onClick = { activeTab = "chats" },
                )
                TabItem(
                    label = "games",
                    iconSymbol = "⚄",
                    isSelected = activeTab == "games",
                    onClick = { activeTab = "games" },
                )
                TabItem(
                    label = "stickers",
                    iconSymbol = "✦",
                    isSelected = activeTab == "stickers",
                    onClick = { activeTab = "stickers" },
                )
                TabItem(
                    label = "me",
                    iconSymbol = "▲",
                    isSelected = activeTab == "me",
                    onClick = {
                        activeTab = "me"
                        onNavigateToSettings()
                    },
                )
            }
        }
    }
}

@Composable
private fun LitNowItem(
    name: String,
    isLit: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        PixelLantern(isLit = isLit)
        Text(
            text = name,
            fontFamily = PixelifySans,
            fontSize = 12.sp,
            color = if (isLit) TheirBubbleText else PlaceholderText,
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: MothConversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 40px Stepped-Corner Face Avatar (simple eyes + mouth)
        PixelFaceAvatar(color = conversation.avatarColor)

        // Middle column: Name and Preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.name,
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TheirBubbleText,
                )
                Text(
                    text = conversation.time,
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Message Preview Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (conversation.isDisappearing) {
                    // Small ember squares preview: 3 lit, 1 burned
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) {
                            Box(modifier = Modifier.size(5.dp).background(LanternAmber))
                        }
                        Box(modifier = Modifier.size(5.dp).background(MothDivider))
                    }
                    Text(
                        text = conversation.preview,
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = MutedText,
                    )
                } else if (conversation.isViewOnce) {
                    // Red wax seal square icon
                    Box(modifier = Modifier.size(6.dp).background(WaxSealRed))
                    Text(
                        text = conversation.preview,
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = MutedText,
                    )
                } else {
                    Text(
                        text = conversation.preview,
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = MutedText,
                        maxLines = 1,
                    )
                }
            }
        }

        // Unread Badge (if any): Amber stepped square
        if (conversation.unreadCount > 0) {
            SteppedBox(
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                cutSize = 2.dp,
                borderWidth = 1.dp,
            ) {
                Text(
                    text = "${conversation.unreadCount}",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MyBubbleText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    iconSymbol: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (isSelected) LanternAmber else PlaceholderText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = iconSymbol,
            fontSize = 16.sp,
            color = color,
        )
        Text(
            text = label,
            fontFamily = PixelifySans,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

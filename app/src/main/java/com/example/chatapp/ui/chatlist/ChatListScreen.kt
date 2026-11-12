package com.example.chatapp.ui.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*

@Composable
fun ChatListScreen(
    state: MothState,
    onOpenChat: (title: String) -> Unit,
    onSignOut: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf("chats") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    var newContactName by remember { mutableStateOf("") }
    var newContactInitialMsg by remember { mutableStateOf("") }

    val filteredConversations = remember(state.conversations, searchQuery) {
        if (searchQuery.isBlank()) state.conversations
        else state.conversations.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.preview.contains(searchQuery, ignoreCase = true)
        }
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
                        text = "Lanterns",
                        fontFamily = PixelifySans,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternAmber,
                        letterSpacing = 1.sp,
                    )
                }

                // Right: Search icon + Options menu button (containing + button inside)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "⌕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showSearch) TheirBubbleText else LanternAmber,
                        modifier = Modifier
                            .clickable {
                                showSearch = !showSearch
                                if (!showSearch) searchQuery = ""
                            }
                            .testTag("search_icon"),
                    )

                    // Options button (three vertical dots ⋮) with dropdown containing + new lantern action
                    Box {
                        Text(
                            text = "⋮",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanternAmber,
                            modifier = Modifier
                                .clickable { showOptionsMenu = !showOptionsMenu }
                                .padding(horizontal = 4.dp)
                                .testTag("options_button"),
                        )

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(MothHeaderBg)
                                .border(2.dp, TheirBubbleBorder),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "+",
                                            fontFamily = PixelifySans,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LanternAmber,
                                        )
                                        Text(
                                            text = "new lantern / chat",
                                            fontFamily = PixelifySans,
                                            fontSize = 13.sp,
                                            color = TheirBubbleText,
                                        )
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showNewChatDialog = true
                                },
                                modifier = Modifier.testTag("menu_new_chat_button"),
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "✓",
                                            fontFamily = PixelifySans,
                                            fontSize = 13.sp,
                                            color = SecureGreen,
                                        )
                                        Text(
                                            text = "mark all as read",
                                            fontFamily = PixelifySans,
                                            fontSize = 13.sp,
                                            color = TheirBubbleText,
                                        )
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    state.conversations = state.conversations.map { it.copy(unreadCount = 0) }
                                },
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "▲",
                                            fontFamily = PixelifySans,
                                            fontSize = 12.sp,
                                            color = MutedText,
                                        )
                                        Text(
                                            text = "operator profile",
                                            fontFamily = PixelifySans,
                                            fontSize = 13.sp,
                                            color = TheirBubbleText,
                                        )
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    activeTab = "me"
                                },
                            )
                        }
                    }
                }
            }
        }

        // Live Search Bar (stepped style)
        AnimatedVisibility(visible = showSearch && activeTab == "chats") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MothHeaderBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "filter lanterns...",
                                fontFamily = PixelifySans,
                                fontSize = 13.sp,
                                color = PlaceholderText,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 13.sp,
                                color = TheirBubbleText,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input"),
                        )
                    }
                }
            }
        }

        // Main Tab Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (activeTab) {
                "chats" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
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

                            // Lantern Avatars Row (active contacts)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                state.conversations.forEach { conv ->
                                    LitNowItem(
                                        name = conv.name,
                                        isLit = conv.isLit,
                                        onClick = {
                                            state.openConversation(conv.id)
                                            onOpenChat(conv.name)
                                        }
                                    )
                                }
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
                        if (filteredConversations.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "no lanterns matching query",
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = MutedText,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                items(filteredConversations, key = { it.id }) { conv ->
                                    ConversationRow(
                                        conversation = conv,
                                        onClick = {
                                            state.openConversation(conv.id)
                                            onOpenChat(conv.name)
                                        },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MothDivider),
                                    )
                                }
                            }
                        }
                    }
                }
                "games" -> {
                    MothMiniGameView()
                }
                "empty" -> {
                    EmptyPageView()
                }
                "me" -> {
                    MothProfileView(
                        state = state,
                        onSignOut = onSignOut,
                    )
                }
            }
        }

        // 4. Bottom Tab Bar (matches Screenshot 1: chats, games, empty, me)
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
                    label = "empty",
                    iconSymbol = "✦",
                    isSelected = activeTab == "empty",
                    onClick = { activeTab = "empty" },
                )
                TabItem(
                    label = "me",
                    iconSymbol = "▲",
                    isSelected = activeTab == "me",
                    onClick = { activeTab = "me" },
                )
            }
        }
    }

    // New Conversation Dialog (matches Moth hours pixel style)
    if (showNewChatDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x991A1633))
                .clickable { showNewChatDialog = false }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}, // Prevent dismiss on inner click
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "light a new lantern",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { showNewChatDialog = false },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "contact handle",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        SteppedBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newContactName.isEmpty()) {
                                    Text(
                                        text = "e.g. aster, tide_watcher",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = newContactName,
                                    onValueChange = { newContactName = it },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "initial whisper",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        SteppedBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newContactInitialMsg.isEmpty()) {
                                    Text(
                                        text = "lantern lit across the bay",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = newContactInitialMsg,
                                    onValueChange = { newContactInitialMsg = it },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 14.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SteppedButton(
                            text = "cancel",
                            onClick = { showNewChatDialog = false },
                            backgroundColor = TheirBubbleFill,
                            borderColor = MothDivider,
                            textColor = TheirBubbleText,
                            modifier = Modifier.weight(1f),
                            cutSize = 3.dp,
                        )
                        SteppedButton(
                            text = "ignite",
                            onClick = {
                                if (newContactName.isNotBlank()) {
                                    state.createNewChat(
                                        name = newContactName.trim(),
                                        initialMessage = newContactInitialMsg.trim(),
                                    )
                                    showNewChatDialog = false
                                    onOpenChat(newContactName.trim())
                                    newContactName = ""
                                    newContactInitialMsg = ""
                                }
                            },
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

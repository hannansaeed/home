package com.example.chatapp.ui.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.calls.CallHistoryEmptyPage
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(
    state: MothState,
    onOpenChat: (title: String) -> Unit,
    onSignOut: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf("chats") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showStarredModal by remember { mutableStateOf(false) }
    var holdChatTarget by remember { mutableStateOf<MothConversation?>(null) }

    var newContactName by remember { mutableStateOf("") }
    var newContactInitialMsg by remember { mutableStateOf("") }
    var newChatError by remember { mutableStateOf<String?>(null) }

    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupIcon by remember { mutableStateOf("🏮") }
    var newGroupMembers by remember { mutableStateOf("") }
    var newGroupError by remember { mutableStateOf<String?>(null) }

    var selectedProfileUsername by remember { mutableStateOf<String?>(null) }
    var profileDetails by remember { mutableStateOf<UserProfileDetails?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProfileUsername) {
        val target = selectedProfileUsername
        if (target != null) {
            isLoadingProfile = true
            profileDetails = null
            profileDetails = state.fetchUserProfile(target)
            isLoadingProfile = false
        }
    }

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
            .testTag("chat_list_screen"),
    ) {
        // 1. Header merged seamlessly with the top status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .statusBarsPadding()
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

                // Right: Search icon + Options menu (ONLY on Chats and Calls tabs)
                if (activeTab == "chats" || activeTab == "calls") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    showSearch = !showSearch
                                    if (!showSearch) searchQuery = ""
                                }
                                .testTag("search_icon"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelSearchIcon(
                                tint = if (showSearch) TheirBubbleText else LanternAmber,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Options button (three vertical dots ⋮)
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
                                if (activeTab == "calls") {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                PixelTrashIcon(
                                                    tint = WaxSealRed,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = "clear all logs",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 13.sp,
                                                    color = WaxSealRed,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            coroutineScope.launch { state.clearAllCallLogs() }
                                        },
                                        modifier = Modifier.testTag("menu_clear_all_logs"),
                                    )
                                } else {
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
                                                PixelLantern(
                                                    isLit = true,
                                                    modifier = Modifier.size(16.dp, 19.dp),
                                                )
                                                Text(
                                                    text = "new group lantern",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 13.sp,
                                                    color = TheirBubbleText,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showNewGroupDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_new_group_button"),
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                PixelStarIcon(
                                                    tint = LanternAmber,
                                                    filled = true,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                                Text(
                                                    text = "starred messages",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 13.sp,
                                                    color = TheirBubbleText,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showStarredModal = true
                                        },
                                        modifier = Modifier.testTag("menu_starred_messages_button"),
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
                                            state.markAllAsRead()
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                PixelPersonIcon(
                                                    tint = MutedText,
                                                    modifier = Modifier.size(16.dp),
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
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MothDivider),
        )

        // Live Search Bar (stepped style, shared for Chats & Calls)
        AnimatedVisibility(visible = showSearch && (activeTab == "chats" || activeTab == "calls")) {
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
                                text = if (activeTab == "calls") "filter calls..." else "filter lanterns...",
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

                            // Lantern Avatars Row (lantern is only lit when the user is active)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                state.conversations.forEach { conv ->
                                    LitNowItem(
                                        name = conv.name,
                                        isLit = if (conv.isGroup) true else state.isContactOnline(conv.name),
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
                                        onLongClick = {
                                            holdChatTarget = conv
                                        },
                                        onAvatarClick = {
                                            if (conv.isGroup) {
                                                state.openConversation(conv.id)
                                                onOpenChat(conv.name)
                                            } else {
                                                selectedProfileUsername = conv.name
                                            }
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
                "fun" -> {
                    MothMiniGameView(
                        state = state,
                        onOpenChat = { target ->
                            state.openConversation(target)
                            onOpenChat(target)
                        },
                    )
                }
                "calls" -> {
                    CallHistoryEmptyPage(state = state, searchQuery = searchQuery)
                }
                "me" -> {
                    MothProfileView(
                        state = state,
                        onSignOut = onSignOut,
                        onOpenChat = { target ->
                            state.openConversation(target)
                            onOpenChat(target)
                        },
                    )
                }
            }
        }

        // 4. Bottom Tab Bar merged seamlessly behind system navigation bar / gesture pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MothDivider),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(
                    label = "chats",
                    iconVector = Icons.Outlined.ChatBubbleOutline,
                    isSelected = activeTab == "chats",
                    onClick = {
                        activeTab = "chats"
                        showOptionsMenu = false
                        showSearch = false
                        searchQuery = ""
                    },
                )
                TabItem(
                    label = "fun",
                    isSelected = activeTab == "fun",
                    onClick = {
                        activeTab = "fun"
                        showOptionsMenu = false
                        showSearch = false
                        searchQuery = ""
                    },
                    customIcon = { tint ->
                        PixelGameIcon(tint = tint, modifier = Modifier.size(19.dp))
                    },
                )
                TabItem(
                    label = "calls",
                    isSelected = activeTab == "calls",
                    onClick = {
                        activeTab = "calls"
                        showOptionsMenu = false
                        showSearch = false
                        searchQuery = ""
                    },
                    customIcon = { tint ->
                        PixelPhoneIcon(tint = tint, modifier = Modifier.size(19.dp))
                    },
                )
                TabItem(
                    label = "me",
                    isSelected = activeTab == "me",
                    onClick = {
                        activeTab = "me"
                        showOptionsMenu = false
                        showSearch = false
                        searchQuery = ""
                    },
                    customIcon = { tint ->
                        PixelPersonIcon(tint = tint, modifier = Modifier.size(19.dp))
                    },
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

                    if (newChatError != null) {
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = WaxSealRed,
                            cutSize = 3.dp,
                        ) {
                            Text(
                                text = newChatError.orEmpty(),
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = WaxSealRed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SteppedButton(
                            text = "cancel",
                            onClick = {
                                showNewChatDialog = false
                                newChatError = null
                            },
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
                                        onError = { newChatError = it },
                                        onSuccess = {
                                            showNewChatDialog = false
                                            onOpenChat(newContactName.trim().lowercase())
                                            newContactName = ""
                                            newContactInitialMsg = ""
                                            newChatError = null
                                        }
                                    )
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

    // User Profile Details Modal (when clicking on a user's avatar in chat list)
    selectedProfileUsername?.let { targetName ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x991A1633))
                .clickable { selectedProfileUsername = null }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .testTag("user_profile_modal"),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "lantern profile",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { selectedProfileUsername = null },
                        )
                    }

                    if (isLoadingProfile && profileDetails == null) {
                        Text(
                            text = "loading details...",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = MutedText,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        val convFallback = state.conversations.firstOrNull {
                            it.name.equals(targetName, ignoreCase = true)
                        }
                        val details = profileDetails
                        UserAvatar(
                            avatarPath = details?.avatarPath ?: convFallback?.avatarLocalPath,
                            color = details?.avatarColor ?: convFallback?.avatarColor ?: LanternAmber,
                            size = 68.dp,
                        )

                        Text(
                            text = "@${details?.username ?: targetName}",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TheirBubbleText,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val isOnline = details?.isOnline == true
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isOnline) SecureGreen else MutedText)
                            )
                            Text(
                                text = details?.statusLabel ?: "offline",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = if (isOnline) SecureGreen else MutedText,
                            )
                        }

                        if (!details?.bio.isNullOrBlank()) {
                            Text(
                                text = details?.bio.orEmpty(),
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = TheirBubbleText.copy(alpha = 0.85f),
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "joined",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = MutedText,
                                    )
                                    Text(
                                        text = details?.joinedLabel ?: "unknown",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = TheirBubbleText,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "local messages",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = MutedText,
                                    )
                                    Text(
                                        text = "${details?.localMessageCount ?: 0}",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = LanternAmber,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SteppedButton(
                                text = "close",
                                onClick = { selectedProfileUsername = null },
                                backgroundColor = TheirBubbleFill,
                                borderColor = MothDivider,
                                textColor = TheirBubbleText,
                                modifier = Modifier.weight(1f),
                                cutSize = 3.dp,
                            )
                            SteppedButton(
                                text = "open chat",
                                onClick = {
                                    selectedProfileUsername = null
                                    state.openConversation(targetName)
                                    onOpenChat(targetName)
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
    // New Group Dialog
    if (showNewGroupDialog) {
        val groupIcons = listOf("🏮", "🦋", "🌙", "🔥", "✦", "🎲", "🌿", "⚡")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x991A1633))
                .clickable { showNewGroupDialog = false }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .testTag("new_group_dialog"),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "new group lantern",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { showNewGroupDialog = false },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "group name",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newGroupName.isEmpty()) {
                                    Text(
                                        text = "e.g. night watch",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = newGroupName,
                                    onValueChange = { newGroupName = it; newGroupError = null },
                                    textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 14.sp, color = TheirBubbleText),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("group_name_input"),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "group icon",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            groupIcons.forEach { ic ->
                                val selected = newGroupIcon == ic
                                SteppedBox(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable { newGroupIcon = ic },
                                    backgroundColor = if (selected) MyBubbleFill else MothBgDark,
                                    borderColor = if (selected) LanternAmber else MothDivider,
                                    cutSize = 2.dp,
                                    borderWidth = 1.dp,
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = ic, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "member handles (comma separated)",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                        )
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            backgroundColor = MothBgDark,
                            borderColor = TheirBubbleBorder,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newGroupMembers.isEmpty()) {
                                    Text(
                                        text = "e.g. aster, tide_watcher",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = newGroupMembers,
                                    onValueChange = { newGroupMembers = it; newGroupError = null },
                                    textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 14.sp, color = TheirBubbleText),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("group_members_input"),
                                )
                            }
                        }
                    }

                    if (newGroupError != null) {
                        Text(
                            text = newGroupError.orEmpty(),
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = WaxSealRed,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SteppedButton(
                            text = "cancel",
                            onClick = { showNewGroupDialog = false; newGroupError = null },
                            backgroundColor = TheirBubbleFill,
                            borderColor = MothDivider,
                            textColor = TheirBubbleText,
                            modifier = Modifier.weight(1f),
                            cutSize = 3.dp,
                        )
                        SteppedButton(
                            text = "create group",
                            onClick = {
                                val handles = newGroupMembers.split(",", " ").map { it.trim() }.filter { it.isNotBlank() }
                                state.createGroupChat(
                                    groupName = newGroupName,
                                    groupIcon = newGroupIcon,
                                    memberHandles = handles,
                                    onError = { newGroupError = it },
                                    onSuccess = {
                                        val title = newGroupName.trim()
                                        showNewGroupDialog = false
                                        newGroupName = ""
                                        newGroupMembers = ""
                                        newGroupError = null
                                        onOpenChat(title)
                                    }
                                )
                            },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            modifier = Modifier.weight(1f).testTag("create_group_confirm_button"),
                            cutSize = 3.dp,
                        )
                    }
                }
            }
        }
    }

    // Long-press Conversation Options Dialog (Pin, Mute, Delete)
    holdChatTarget?.let { targetChat ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x991A1633))
                .clickable { holdChatTarget = null }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .testTag("hold_chat_dialog"),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (targetChat.isGroup) targetChat.name else "@${targetChat.name}",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    SteppedButton(
                        text = if (targetChat.isPinned) "unpin chat" else "pin chat",
                        onClick = {
                            state.togglePinChat(targetChat.id)
                            holdChatTarget = null
                        },
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        modifier = Modifier.fillMaxWidth().testTag("hold_chat_pin_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = if (targetChat.isMuted) "unmute notifications" else "mute notifications",
                        onClick = {
                            state.toggleMuteChat(targetChat.id)
                            holdChatTarget = null
                        },
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        modifier = Modifier.fillMaxWidth().testTag("hold_chat_mute_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "delete chat",
                        onClick = {
                            state.deleteChatConversation(targetChat.id)
                            holdChatTarget = null
                        },
                        backgroundColor = WaxSealRed,
                        borderColor = WaxSealDarkEdge,
                        textColor = Color.White,
                        modifier = Modifier.fillMaxWidth().testTag("hold_chat_delete_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "cancel",
                        onClick = { holdChatTarget = null },
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

    // All Starred Messages Across All Chats Modal
    if (showStarredModal) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x991A1633))
                .clickable { showStarredModal = false }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .clickable(enabled = false) {}
                    .testTag("all_starred_messages_modal"),
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
                            PixelStarIcon(tint = LanternAmber, filled = true, modifier = Modifier.size(16.dp))
                            Text(
                                text = "starred messages",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = LanternAmber,
                            )
                        }
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = MutedText,
                            modifier = Modifier.clickable { showStarredModal = false },
                        )
                    }

                    if (state.allStarredMessages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "hold a message and tap ★ to star it",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = MutedText,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.allStarredMessages, key = { "${it.chatId}_${it.message.id}" }) { entry ->
                                val preview = entry.message.text.ifBlank {
                                    entry.message.attachmentName
                                        ?: (entry.message.attachmentType?.name?.lowercase() ?: "message")
                                }
                                SteppedBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showStarredModal = false
                                            state.openConversation(entry.chatId)
                                            onOpenChat(entry.chatName)
                                        }
                                        .testTag("starred_entry_${entry.message.id}"),
                                    backgroundColor = MothBgDark,
                                    borderColor = TheirBubbleBorder,
                                    cutSize = 3.dp,
                                    borderWidth = 1.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${entry.chatName} • @${entry.message.senderUsername}",
                                                fontFamily = PixelifySans,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
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
                                            Text(
                                                text = entry.message.time,
                                                fontFamily = PixelifySans,
                                                fontSize = 10.sp,
                                                color = MutedText,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clickable { state.toggleStarMessage(entry.chatId, entry.message.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            PixelStarIcon(tint = LanternAmber, filled = true, modifier = Modifier.size(14.dp))
                                        }
                                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: MothConversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (conversation.isGroup) {
            SteppedBox(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onAvatarClick)
                    .testTag("chat_avatar_${conversation.name}"),
                backgroundColor = conversation.avatarColor,
                borderColor = LanternAmber,
                cutSize = 4.dp,
                borderWidth = 2.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = conversation.groupIcon, fontSize = 18.sp)
                }
            }
        } else {
            // 40px Stepped-Corner Avatar (tap to view user profile details)
            UserAvatar(
                avatarPath = conversation.avatarLocalPath,
                color = conversation.avatarColor,
                modifier = Modifier
                    .clickable(onClick = onAvatarClick)
                    .testTag("chat_avatar_${conversation.name}"),
            )
        }

        // Middle column: Name and Preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (conversation.isGroup) "${conversation.name} (${conversation.members.size})" else conversation.name,
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TheirBubbleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (conversation.isPinned) {
                        PixelPinIcon(tint = LanternAmber, modifier = Modifier.size(12.dp))
                    }
                    if (conversation.isMuted) {
                        PixelMuteBellIcon(tint = MutedText, modifier = Modifier.size(12.dp))
                    }
                }
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
    iconVector: ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    customIcon: (@Composable (Color) -> Unit)? = null,
) {
    val color = if (isSelected) LanternAmber else PlaceholderText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("tab_$label"),
    ) {
        Box(
            modifier = Modifier.height(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(19.dp),
                )
            } else if (customIcon != null) {
                customIcon(color)
            }
        }
        Text(
            text = label,
            fontFamily = PixelifySans,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

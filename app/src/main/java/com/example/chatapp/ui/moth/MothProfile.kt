package com.example.chatapp.ui.moth

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
) {
    PROFILE("profile", "username, passphrase, bio & avatar"),
    APPEARANCE("appearance", "theme, font & size"),
    PRIVACY("privacy", "screenshots, typing, read receipts & pin"),
    NOTIFICATIONS("notifications", "message alerts, calls & previews"),
    DATA_STORAGE("data and storage", "media usage, cache & call logs"),
    CONTACTS("friends / contacts", "your saved handles & active status"),
    ;

    fun localizedSubtitle(): String = subtitle
}

@Composable
fun MothProfileView(
    state: MothState,
    onSignOut: () -> Unit,
    onOpenChat: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    var editUsername by remember(state.currentUsername) { mutableStateOf(state.currentUsername) }
    var editBio by remember(state.userBio) { mutableStateOf(state.userBio) }
    var currentPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var profileNotice by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var pendingAvatarCropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var isUpdatingAvatar by remember { mutableStateOf(false) }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var storageStats by remember { mutableStateOf(StorageStats()) }
    var storageActionNotice by remember { mutableStateOf<String?>(null) }

    var newFriendHandle by remember { mutableStateOf("") }
    var friendError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    BackHandler(enabled = activeCategory != null || showPinSetupDialog || showAvatarOptions || pendingAvatarCropUri != null) {
        when {
            pendingAvatarCropUri != null -> pendingAvatarCropUri = null
            showPinSetupDialog -> showPinSetupDialog = false
            showAvatarOptions -> showAvatarOptions = false
            activeCategory != null -> activeCategory = null
        }
    }

    LaunchedEffect(activeCategory) {
        if (activeCategory == SettingsCategory.DATA_STORAGE) {
            storageStats = state.computeStorageStats()
            storageActionNotice = null
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarError = null
            pendingAvatarCropUri = uri
        }
    }

    val avatarColors = listOf(
        Color(0xFFB8A9E8), // Lavender
        Color(0xFF82D8A7), // Mint
        Color(0xFFF38D71), // Coral
        Color(0xFFFFD152), // Yellow
        Color(0xFF70D6FF), // Sky blue
    )

    val friendsList = remember(state.conversations) {
        state.conversations.filter { !it.isGroup && !it.name.equals("support", ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MothBgDark)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val category = activeCategory
            if (category == null) {
                // Root Settings & Operator Profile Header
                Column {
                    Text(
                        text = "settings",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = if (isBloomTheme) "garden identity, privacy & storage" else "lantern identity, privacy & storage",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                    )
                }

                // Operator Summary Card
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { showAvatarOptions = true }
                                    .testTag("profile_avatar_button"),
                            ) {
                                UserAvatar(
                                    avatarPath = state.userAvatarPath,
                                    color = state.userAvatarColor,
                                    size = 54.dp,
                                )
                                Text(
                                    text = if (isUpdatingAvatar) "saving..." else "tap photo",
                                    fontFamily = PixelifySans,
                                    fontSize = 10.sp,
                                    color = LanternAmber,
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.currentUsername,
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TheirBubbleText,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(modifier = Modifier.size(6.dp).background(SecureGreen))
                                    Text(
                                        text = if (isBloomTheme) "flower blooming • rooted" else "lantern lit • active",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = SecureGreen,
                                    )
                                }
                                if (state.userBio.isNotBlank()) {
                                    Text(
                                        text = state.userBio,
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        if (avatarError != null) {
                            Text(
                                text = avatarError ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = WaxSealRed,
                            )
                        }
                    }
                }

                // Category Navigation Rows (WhatsApp-style)
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsCategoryRow(
                            title = SettingsCategory.PROFILE.title,
                            subtitle = SettingsCategory.PROFILE.subtitle,
                            testTag = "settings_category_profile",
                            icon = { PixelPersonIcon(tint = LanternAmber, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                profileNotice = null
                                profileError = null
                                activeCategory = SettingsCategory.PROFILE
                            },
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
                        SettingsCategoryRow(
                            title = SettingsCategory.APPEARANCE.title,
                            subtitle = SettingsCategory.APPEARANCE.localizedSubtitle(),
                            testTag = "settings_category_appearance",
                            icon = { PixelPaletteIcon(tint = LanternAmber, modifier = Modifier.size(18.dp)) },
                            onClick = { activeCategory = SettingsCategory.APPEARANCE },
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
                        SettingsCategoryRow(
                            title = SettingsCategory.PRIVACY.title,
                            subtitle = SettingsCategory.PRIVACY.subtitle,
                            testTag = "settings_category_privacy",
                            icon = { PixelShieldIcon(tint = SecureGreen, modifier = Modifier.size(18.dp)) },
                            onClick = { activeCategory = SettingsCategory.PRIVACY },
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
                        SettingsCategoryRow(
                            title = SettingsCategory.NOTIFICATIONS.title,
                            subtitle = SettingsCategory.NOTIFICATIONS.subtitle,
                            testTag = "settings_category_notifications",
                            icon = { PixelBellIcon(tint = LanternAmber, modifier = Modifier.size(18.dp)) },
                            onClick = { activeCategory = SettingsCategory.NOTIFICATIONS },
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
                        SettingsCategoryRow(
                            title = SettingsCategory.DATA_STORAGE.title,
                            subtitle = SettingsCategory.DATA_STORAGE.subtitle,
                            testTag = "settings_category_storage",
                            icon = { PixelDatabaseIcon(tint = TheirBubbleText, modifier = Modifier.size(18.dp)) },
                            onClick = { activeCategory = SettingsCategory.DATA_STORAGE },
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))
                        SettingsCategoryRow(
                            title = SettingsCategory.CONTACTS.title,
                            subtitle = if (isBloomTheme) "${friendsList.size} saved garden contacts" else "${friendsList.size} saved lantern contacts",
                            testTag = "settings_category_contacts",
                            icon = { PixelUsersIcon(tint = Color(0xFF70D6FF), modifier = Modifier.size(18.dp)) },
                            onClick = { activeCategory = SettingsCategory.CONTACTS },
                        )
                    }
                }

                // Extinguish Lantern / Wither Flower / Sign Out
                if (!showLogoutConfirm) {
                    SteppedButton(
                        text = if (isBloomTheme) "flower withering (log out)" else "extinguish lantern (log out)",
                        onClick = { showLogoutConfirm = true },
                        backgroundColor = WaxSealRed,
                        borderColor = WaxSealDarkEdge,
                        textColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button"),
                    )
                } else {
                    SteppedBox(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = TheirBubbleFill,
                        borderColor = WaxSealRed,
                        cutSize = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = if (isBloomTheme) "let your flower wither?" else "extinguish your lantern?",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TheirBubbleText,
                            )
                            Text(
                                text = "your local history stays saved on this device.",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = MutedText,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SteppedButton(
                                    text = if (isBloomTheme) "yes, wither" else "yes, extinguish",
                                    onClick = onSignOut,
                                    backgroundColor = WaxSealRed,
                                    borderColor = WaxSealDarkEdge,
                                    textColor = Color.White,
                                    modifier = Modifier.testTag("confirm_sign_out_button"),
                                )
                                SteppedButton(
                                    text = if (isBloomTheme) "keep blooming" else "keep lit",
                                    onClick = { showLogoutConfirm = false },
                                    backgroundColor = TheirBubbleBorder,
                                    borderColor = MothDivider,
                                    textColor = TheirBubbleText,
                                )
                            }
                        }
                    }
                }
            } else {
                // Category Subpage Header with Back Arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SteppedBox(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { activeCategory = null }
                            .testTag("settings_back_button"),
                        backgroundColor = MothHeaderBg,
                        borderColor = LanternAmber,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelBackArrowIcon(tint = LanternAmber, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column {
                        Text(
                            text = category.title,
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = category.localizedSubtitle(),
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = MutedText,
                        )
                    }
                }

                when (category) {
                    SettingsCategory.PROFILE -> {
                        if (profileNotice != null) {
                            Text(
                                text = profileNotice ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = SecureGreen,
                            )
                        }
                        if (profileError != null) {
                            Text(
                                text = profileError ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = WaxSealRed,
                            )
                        }

                        // Avatar Photo & Default Avatar Color
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    UserAvatar(
                                        avatarPath = state.userAvatarPath,
                                        color = state.userAvatarColor,
                                        size = 48.dp,
                                    )
                                    SteppedButton(
                                        text = if (isUpdatingAvatar) "saving..." else "change avatar photo",
                                        onClick = { showAvatarOptions = true },
                                        backgroundColor = MothBgDark,
                                        borderColor = LanternAmber,
                                        textColor = LanternAmber,
                                        cutSize = 3.dp,
                                        modifier = Modifier.testTag("profile_change_avatar_button"),
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                                Text(
                                    text = "default avatar color",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    avatarColors.forEach { color ->
                                        val isSelected = state.userAvatarColor == color
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(color, SteppedCornerShape(3.dp))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) LanternAmber else MothBgDark,
                                                    shape = SteppedCornerShape(3.dp),
                                                )
                                                .clickable { state.updateAvatarColor(color) },
                                        )
                                    }
                                }
                            }
                        }

                        // Username & Bio Card
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "username",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SteppedBox(
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        backgroundColor = MothBgDark,
                                        borderColor = MothDivider,
                                        cutSize = 3.dp,
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            BasicTextField(
                                                value = editUsername,
                                                onValueChange = { editUsername = it; profileError = null; profileNotice = null },
                                                textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 14.sp, color = TheirBubbleText),
                                                cursorBrush = SolidColor(LanternAmber),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("profile_username_input"),
                                            )
                                        }
                                    }
                                    SteppedButton(
                                        text = if (isSavingProfile) "..." else "save",
                                        onClick = {
                                            if (!isSavingProfile) {
                                                isSavingProfile = true
                                                profileError = null
                                                profileNotice = null
                                                state.changeUsername(editUsername) { err ->
                                                    isSavingProfile = false
                                                    if (err != null) profileError = err else profileNotice = "username saved"
                                                }
                                            }
                                        },
                                        backgroundColor = MyBubbleFill,
                                        borderColor = MyBubbleBorder,
                                        textColor = MyBubbleText,
                                        cutSize = 3.dp,
                                        modifier = Modifier.testTag("profile_save_username_button"),
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                                Text(
                                    text = "bio",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SteppedBox(
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        backgroundColor = MothBgDark,
                                        borderColor = MothDivider,
                                        cutSize = 3.dp,
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (editBio.isEmpty()) {
                                                Text(
                                                    text = if (isBloomTheme) "say something about your flower..." else "say something about your lantern...",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 12.sp,
                                                    color = PlaceholderText,
                                                )
                                            }
                                            BasicTextField(
                                                value = editBio,
                                                onValueChange = { editBio = it.take(120); profileNotice = null },
                                                textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                                                cursorBrush = SolidColor(LanternAmber),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
                                            )
                                        }
                                    }
                                    SteppedButton(
                                        text = "save",
                                        onClick = {
                                            profileError = null
                                            state.updateBio(editBio) {
                                                profileNotice = "bio saved"
                                            }
                                        },
                                        backgroundColor = MyBubbleFill,
                                        borderColor = MyBubbleBorder,
                                        textColor = MyBubbleText,
                                        cutSize = 3.dp,
                                        modifier = Modifier.testTag("profile_save_bio_button"),
                                    )
                                }
                            }
                        }

                        // Passphrase Change Card
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "change passphrase",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                )
                                SteppedBox(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    backgroundColor = MothBgDark,
                                    borderColor = MothDivider,
                                    cutSize = 3.dp,
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (currentPassInput.isEmpty()) {
                                            Text(
                                                text = "current passphrase",
                                                fontFamily = PixelifySans,
                                                fontSize = 12.sp,
                                                color = PlaceholderText,
                                            )
                                        }
                                        BasicTextField(
                                            value = currentPassInput,
                                            onValueChange = { currentPassInput = it; profileError = null; profileNotice = null },
                                            textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                                            cursorBrush = SolidColor(LanternAmber),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("profile_current_pass_input"),
                                        )
                                    }
                                }
                                SteppedBox(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    backgroundColor = MothBgDark,
                                    borderColor = MothDivider,
                                    cutSize = 3.dp,
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (newPassInput.isEmpty()) {
                                            Text(
                                                text = "new passphrase (min 3 chars)",
                                                fontFamily = PixelifySans,
                                                fontSize = 12.sp,
                                                color = PlaceholderText,
                                            )
                                        }
                                        BasicTextField(
                                            value = newPassInput,
                                            onValueChange = { newPassInput = it; profileError = null; profileNotice = null },
                                            textStyle = TextStyle(fontFamily = PixelifySans, fontSize = 13.sp, color = TheirBubbleText),
                                            cursorBrush = SolidColor(LanternAmber),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("profile_new_pass_input"),
                                        )
                                    }
                                }
                                SteppedButton(
                                    text = "update passphrase",
                                    onClick = {
                                        profileError = null
                                        profileNotice = null
                                        state.updatePassphrase(currentPassInput, newPassInput) { err ->
                                            if (err != null) {
                                                profileError = err
                                            } else {
                                                profileNotice = "passphrase updated"
                                                currentPassInput = ""
                                                newPassInput = ""
                                            }
                                        }
                                    },
                                    backgroundColor = MothBgDark,
                                    borderColor = LanternAmber,
                                    textColor = LanternAmber,
                                    modifier = Modifier.fillMaxWidth().testTag("profile_update_pass_button"),
                                    cutSize = 3.dp,
                                )
                            }
                        }
                    }

                    SettingsCategory.APPEARANCE -> {
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "theme",
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TheirBubbleText,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        AppThemeOption.entries.forEach { themeOption ->
                                            val selected = state.selectedThemeId == themeOption.id
                                            val swatchPalette = if (themeOption == AppThemeOption.FLOWER_BLOOM) {
                                                FlowerBloomPalette
                                            } else {
                                                LanternNightPalette
                                            }
                                            val chipShape = SteppedCornerShape(3.dp)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(
                                                        if (selected) MyBubbleFill else MothBgDark,
                                                        chipShape,
                                                    )
                                                    .border(
                                                        width = 2.dp,
                                                        color = if (selected) MyBubbleBorder else MothDivider,
                                                        shape = chipShape,
                                                    )
                                                    .clickable { state.setAppTheme(themeOption) }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                                    .testTag("app_theme_${themeOption.id}"),
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(swatchPalette.bgDark)
                                                                .border(1.dp, swatchPalette.divider),
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(swatchPalette.theirBubbleFill)
                                                                .border(1.dp, swatchPalette.theirBubbleBorder),
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(swatchPalette.myBubbleFill)
                                                                .border(1.dp, swatchPalette.myBubbleBorder),
                                                        )
                                                    }
                                                    Text(
                                                        text = themeOption.label,
                                                        fontFamily = PixelifySans,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = if (selected) MyBubbleText else TheirBubbleText,
                                                        maxLines = 1,
                                                    )
                                                    Text(
                                                        text = themeOption.subtitle,
                                                        fontFamily = PixelifySans,
                                                        fontSize = 10.sp,
                                                        color = if (selected) MyBubbleText.copy(alpha = 0.78f) else MutedText,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "message font size",
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TheirBubbleText,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val sizeOptions = listOf(
                                            "small" to 0.85f,
                                            "medium" to 1.0f,
                                            "large" to 1.25f,
                                        )
                                        sizeOptions.forEach { (label, scaleVal) ->
                                            val selected = kotlin.math.abs(state.messageFontScale - scaleVal) < 0.05f
                                            SteppedButton(
                                                text = label,
                                                onClick = { state.setFontScale(scaleVal) },
                                                backgroundColor = if (selected) MyBubbleFill else MothBgDark,
                                                borderColor = if (selected) MyBubbleBorder else MothDivider,
                                                textColor = if (selected) MyBubbleText else TheirBubbleText,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("font_size_$label"),
                                                cutSize = 3.dp,
                                            )
                                        }
                                    }

                                    // Live preview bubble
                                    SteppedBox(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        backgroundColor = MyBubbleFill,
                                        borderColor = MyBubbleBorder,
                                        cutSize = 4.dp,
                                    ) {
                                        Text(
                                            text = if (isBloomTheme) {
                                                "preview: spring petals bloom in warm sun."
                                            } else {
                                                "preview: moths gather around warm lanterns."
                                            },
                                            fontFamily = PixelifySans,
                                            fontSize = (14f * state.messageFontScale).sp,
                                            lineHeight = (19f * state.messageFontScale).sp,
                                            color = MyBubbleText,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "app font",
                                            fontFamily = PixelifySans,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TheirBubbleText,
                                        )
                                        Text(
                                            text = "pixel & retro",
                                            fontFamily = PixelifySans,
                                            fontSize = 11.sp,
                                            color = MutedText,
                                        )
                                        val pixelFonts = AppFontOption.entries.filter { it.isPixelStyle }
                                        pixelFonts.chunked(2).forEach { rowFonts ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                rowFonts.forEach { option ->
                                                    val selected = state.selectedFontId == option.id
                                                    val chipShape = SteppedCornerShape(3.dp)
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 42.dp)
                                                            .background(
                                                                if (selected) MyBubbleFill else MothBgDark,
                                                                chipShape,
                                                            )
                                                            .border(
                                                                width = 2.dp,
                                                                color = if (selected) MyBubbleBorder else MothDivider,
                                                                shape = chipShape,
                                                            )
                                                            .clickable { state.setAppFont(option) }
                                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                                            .testTag("app_font_${option.id}"),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = option.label,
                                                            fontFamily = option.fontFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = if (selected) MyBubbleText else TheirBubbleText,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                                if (rowFonts.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }

                                        Text(
                                            text = "normal",
                                            fontFamily = PixelifySans,
                                            fontSize = 11.sp,
                                            color = MutedText,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        val normalFonts = AppFontOption.entries.filter { !it.isPixelStyle }
                                        normalFonts.chunked(2).forEach { rowFonts ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                rowFonts.forEach { option ->
                                                    val selected = state.selectedFontId == option.id
                                                    val chipShape = SteppedCornerShape(3.dp)
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 42.dp)
                                                            .background(
                                                                if (selected) MyBubbleFill else MothBgDark,
                                                                chipShape,
                                                            )
                                                            .border(
                                                                width = 2.dp,
                                                                color = if (selected) MyBubbleBorder else MothDivider,
                                                                shape = chipShape,
                                                            )
                                                            .clickable { state.setAppFont(option) }
                                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                                            .testTag("app_font_${option.id}"),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = option.label,
                                                            fontFamily = option.fontFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = if (selected) MyBubbleText else TheirBubbleText,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                                if (rowFonts.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MothDivider))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "ui font size",
                                                fontFamily = PixelifySans,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TheirBubbleText,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (kotlin.math.abs(state.uiFontScale - 1.0f) >= 0.02f) {
                                                    Text(
                                                        text = "reset",
                                                        fontFamily = PixelifySans,
                                                        fontSize = 11.sp,
                                                        color = LanternAmber,
                                                        modifier = Modifier
                                                            .clickable { state.updateUiFontScale(1.0f) }
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            .testTag("ui_font_size_reset"),
                                                    )
                                                }
                                                Text(
                                                    text = "${(state.uiFontScale * 100).roundToInt()}%",
                                                    fontFamily = PixelifySans,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = LanternAmber,
                                                )
                                            }
                                        }

                                        Slider(
                                            value = state.uiFontScale,
                                            onValueChange = { next ->
                                                val snapped = ((next * 20f).roundToInt() / 20f).coerceIn(0.75f, 1.35f)
                                                state.updateUiFontScale(snapped)
                                            },
                                            valueRange = 0.75f..1.35f,
                                            steps = 11,
                                            colors = SliderDefaults.colors(
                                                thumbColor = LanternAmber,
                                                activeTrackColor = LanternAmber,
                                                inactiveTrackColor = MothDivider,
                                                activeTickColor = MothBgDark,
                                                inactiveTickColor = MutedText,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ui_font_size_slider"),
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = "smaller",
                                                fontFamily = PixelifySans,
                                                fontSize = 10.sp,
                                                color = MutedText,
                                            )
                                            Text(
                                                text = "default",
                                                fontFamily = PixelifySans,
                                                fontSize = 10.sp,
                                                color = MutedText,
                                            )
                                            Text(
                                                text = "larger",
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

                    SettingsCategory.PRIVACY -> {
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PrivacyToggleRow(
                                    title = "block screenshots",
                                    subtitle = "hide screen in recents & block captures",
                                    checked = state.screenshotBlockingEnabled,
                                    testTag = "toggle_screenshot_block",
                                    onToggle = { state.setScreenshotBlocking(!state.screenshotBlockingEnabled) },
                                )
                                PrivacyToggleRow(
                                    title = "hide typing indicator",
                                    subtitle = "others won't see when you're typing",
                                    checked = state.hideTypingIndicators,
                                    testTag = "toggle_hide_typing",
                                    onToggle = { state.setHideTyping(!state.hideTypingIndicators) },
                                )
                                PrivacyToggleRow(
                                    title = "disable read receipts",
                                    subtitle = "don't send 'seen' confirmations",
                                    checked = state.disableReadReceipts,
                                    testTag = "toggle_disable_receipts",
                                    onToggle = { state.updateDisableReadReceipts(!state.disableReadReceipts) },
                                )
                                PrivacyToggleRow(
                                    title = "app lock (4-digit pin)",
                                    subtitle = if (state.appLockEnabled) "locked when leaving app • tap to remove" else "require pin when opening app",
                                    checked = state.appLockEnabled,
                                    testTag = "toggle_app_lock",
                                    onToggle = {
                                        if (state.appLockEnabled) {
                                            state.disableAppLock()
                                        } else {
                                            pinInput = ""
                                            pinError = null
                                            showPinSetupDialog = true
                                        }
                                    },
                                )
                            }
                        }
                    }

                    SettingsCategory.NOTIFICATIONS -> {
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PrivacyToggleRow(
                                    title = "message notifications",
                                    subtitle = "alert when new encrypted messages arrive",
                                    checked = state.messageNotificationsEnabled,
                                    testTag = "toggle_msg_notifications",
                                    onToggle = { state.setMessageNotifications(!state.messageNotificationsEnabled) },
                                )
                                PrivacyToggleRow(
                                    title = "call notifications",
                                    subtitle = "alert for incoming voice calls",
                                    checked = state.callNotificationsEnabled,
                                    testTag = "toggle_call_notifications",
                                    onToggle = { state.setCallNotifications(!state.callNotificationsEnabled) },
                                )
                                PrivacyToggleRow(
                                    title = "show message previews",
                                    subtitle = "display sender & snippet in notifications",
                                    checked = state.notificationPreviewsEnabled,
                                    testTag = "toggle_notif_previews",
                                    onToggle = { state.setNotificationPreviews(!state.notificationPreviewsEnabled) },
                                )
                            }
                        }
                    }

                    SettingsCategory.DATA_STORAGE -> {
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "device storage breakdown",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = LanternAmber,
                                )
                                StorageMetricRow("cached media & stickers", formatStorageBytes(storageStats.mediaBytes))
                                StorageMetricRow("stored messages", "${storageStats.messageCount}")
                                StorageMetricRow("conversations", "${storageStats.chatCount}")
                                StorageMetricRow("call history logs", "${storageStats.callLogCount}")
                            }
                        }

                        if (storageActionNotice != null) {
                            Text(
                                text = storageActionNotice ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = SecureGreen,
                            )
                        }

                        SteppedButton(
                            text = "clear cached media",
                            onClick = {
                                scope.launch {
                                    state.clearCachedMedia()
                                    storageStats = state.computeStorageStats()
                                    storageActionNotice = "cached media cleared"
                                }
                            },
                            backgroundColor = MothHeaderBg,
                            borderColor = LanternAmber,
                            textColor = LanternAmber,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_cached_media_button"),
                            cutSize = 3.dp,
                        )

                        SteppedButton(
                            text = "clear call history",
                            onClick = {
                                scope.launch {
                                    state.clearAllCallLogs()
                                    storageStats = state.computeStorageStats()
                                    storageActionNotice = "call history cleared"
                                }
                            },
                            backgroundColor = MothHeaderBg,
                            borderColor = WaxSealRed,
                            textColor = WaxSealRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_call_logs_button"),
                            cutSize = 3.dp,
                        )
                    }

                    SettingsCategory.CONTACTS -> {
                        // Add friend by handle
                        SteppedBox(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MothHeaderBg,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "add friend by handle",
                                    fontFamily = PixelifySans,
                                    fontSize = 12.sp,
                                    color = MutedText,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SteppedBox(
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        backgroundColor = MothBgDark,
                                        borderColor = MothDivider,
                                        cutSize = 3.dp,
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (newFriendHandle.isEmpty()) {
                                                Text(
                                                    text = "e.g. aster",
                                                    fontFamily = PixelifySans,
                                                    fontSize = 12.sp,
                                                    color = PlaceholderText,
                                                )
                                            }
                                            BasicTextField(
                                                value = newFriendHandle,
                                                onValueChange = {
                                                    newFriendHandle = it
                                                    friendError = null
                                                },
                                                textStyle = TextStyle(
                                                    fontFamily = PixelifySans,
                                                    fontSize = 13.sp,
                                                    color = TheirBubbleText,
                                                ),
                                                cursorBrush = SolidColor(LanternAmber),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("add_friend_input"),
                                            )
                                        }
                                    }
                                    SteppedButton(
                                        text = "+ add",
                                        onClick = {
                                            val handle = newFriendHandle.trim()
                                            if (handle.isNotBlank()) {
                                                state.createNewChat(
                                                    name = handle,
                                                    initialMessage = "",
                                                    onError = { friendError = it },
                                                    onSuccess = {
                                                        newFriendHandle = ""
                                                        friendError = null
                                                    },
                                                )
                                            }
                                        },
                                        backgroundColor = MyBubbleFill,
                                        borderColor = MyBubbleBorder,
                                        textColor = MyBubbleText,
                                        cutSize = 3.dp,
                                    )
                                }
                                if (friendError != null) {
                                    Text(
                                        text = friendError ?: "",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = WaxSealRed,
                                    )
                                }
                            }
                        }

                        if (friendsList.isEmpty()) {
                            SteppedBox(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = TheirBubbleFill,
                                borderColor = MothDivider,
                                cutSize = 4.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "no friends added yet",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = MutedText,
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                friendsList.forEach { friend ->
                                    val isOnline = state.isContactOnline(friend.name)
                                    SteppedBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenChat(friend.name) }
                                            .testTag("friend_row_${friend.name}"),
                                        backgroundColor = TheirBubbleFill,
                                        borderColor = TheirBubbleBorder,
                                        cutSize = 3.dp,
                                        borderWidth = 1.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            UserAvatar(
                                                avatarPath = friend.avatarLocalPath,
                                                color = friend.avatarColor,
                                                size = 36.dp,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${friend.name}",
                                                    fontFamily = PixelifySans,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = TheirBubbleText,
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(if (isOnline) SecureGreen else MutedText),
                                                    )
                                                    Text(
                                                        text = if (isOnline) (if (isBloomTheme) "rooted" else "active") else "offline",
                                                        fontFamily = PixelifySans,
                                                        fontSize = 11.sp,
                                                        color = if (isOnline) SecureGreen else MutedText,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "chat",
                                                fontFamily = PixelifySans,
                                                fontWeight = FontWeight.Bold,
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
            }
        }

        if (showPinSetupDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothScrimSoft)
                    .clickable { showPinSetupDialog = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                        .testTag("pin_setup_dialog"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "set 4-digit app lock pin",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LanternAmber,
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
                                if (pinInput.isEmpty()) {
                                    Text(
                                        text = "enter 4 digits (e.g. 1234)",
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = pinInput,
                                    onValueChange = { next ->
                                        if (next.length <= 4 && next.all { it.isDigit() }) {
                                            pinInput = next
                                            pinError = null
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 16.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (pinError != null) {
                            Text(
                                text = pinError ?: "",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = WaxSealRed,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SteppedButton(
                                text = "cancel",
                                onClick = { showPinSetupDialog = false },
                                backgroundColor = TheirBubbleFill,
                                borderColor = MothDivider,
                                textColor = TheirBubbleText,
                                modifier = Modifier.weight(1f),
                                cutSize = 3.dp,
                            )
                            SteppedButton(
                                text = "enable pin",
                                onClick = {
                                    if (state.enableAppLock(pinInput)) {
                                        showPinSetupDialog = false
                                    } else {
                                        pinError = "enter exactly 4 digits"
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

        // Custom Avatar Photo Options Dialog
        if (showAvatarOptions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothScrimSoft)
                    .clickable { showAvatarOptions = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
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
                                text = "avatar photo",
                                fontFamily = PixelifySans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = LanternAmber,
                            )
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                color = MutedText,
                                modifier = Modifier.clickable { showAvatarOptions = false },
                            )
                        }

                        SteppedButton(
                            text = "choose photo from gallery",
                            onClick = {
                                showAvatarOptions = false
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            backgroundColor = MyBubbleFill,
                            borderColor = MyBubbleBorder,
                            textColor = MyBubbleText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choose_avatar_photo_button"),
                            cutSize = 3.dp,
                        )

                        if (state.userAvatarPath != null) {
                            SteppedButton(
                                text = "use default pixel face",
                                onClick = {
                                    state.clearCustomAvatar()
                                    showAvatarOptions = false
                                },
                                backgroundColor = TheirBubbleFill,
                                borderColor = WaxSealRed,
                                textColor = WaxSealRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_avatar_button"),
                                cutSize = 3.dp,
                            )
                        }

                        SteppedButton(
                            text = "cancel",
                            onClick = { showAvatarOptions = false },
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

        // Interactive Avatar Cropper Dialog
        pendingAvatarCropUri?.let { cropUri ->
            ImageCropDialog(
                imageUri = cropUri,
                title = "crop avatar",
                outputSizePx = 300,
                showLabelInput = false,
                onConfirm = { croppedBitmap, _ ->
                    pendingAvatarCropUri = null
                    isUpdatingAvatar = true
                    avatarError = null
                    scope.launch {
                        val res = state.updateCustomAvatarBitmap(croppedBitmap)
                        res.onFailure { err ->
                            avatarError = err.localizedMessage ?: "failed to set avatar photo"
                        }
                        if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                        isUpdatingAvatar = false
                    }
                },
                onDismiss = { pendingAvatarCropUri = null },
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    subtitle: String,
    testTag: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SteppedBox(
            modifier = Modifier.size(34.dp),
            backgroundColor = MothBgDark,
            borderColor = MothDivider,
            cutSize = 2.dp,
            borderWidth = 1.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TheirBubbleText,
            )
            Text(
                text = subtitle,
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = MutedText,
            )
        }
        PixelChevronRightIcon(tint = MutedText, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = PixelifySans,
            fontSize = 12.sp,
            color = MutedText,
        )
        Text(
            text = value,
            fontFamily = PixelifySans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = TheirBubbleText,
        )
    }
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.2f MB".format(mb)
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TheirBubbleText,
            )
            Text(
                text = subtitle,
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = MutedText,
            )
        }
        SteppedBox(
            backgroundColor = if (checked) SecureGreen else MothBgDark,
            borderColor = if (checked) SecureGreen else MothDivider,
            cutSize = 2.dp,
            borderWidth = 1.dp,
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (checked) MothBgDark else MutedText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

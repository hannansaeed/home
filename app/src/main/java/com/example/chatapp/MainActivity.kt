package com.example.chatapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chatapp.ui.auth.LoginScreen
import com.example.chatapp.ui.calls.ActiveCallOverlay
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.chatlist.ChatListScreen
import com.example.chatapp.ui.components.PixelLantern
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

sealed interface MothScreen {
    data object Login : MothScreen
    data object Home : MothScreen
    data class Chat(val title: String) : MothScreen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingChatTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        pendingChatTarget = intent?.getStringExtra("open_chat_user")

        setContent {
            ChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MothBgDark,
                ) {
                    MothNavigationRoot(
                        pendingChatTarget = pendingChatTarget,
                        onConsumedPendingChat = { pendingChatTarget = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra("open_chat_user")
        if (!target.isNullOrBlank()) {
            pendingChatTarget = target
        }
    }
}

@Composable
fun MothNavigationRoot(
    pendingChatTarget: String? = null,
    onConsumedPendingChat: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mothState = remember { MothState(context, scope) }
    var currentScreen by remember {
        mutableStateOf<MothScreen>(if (mothState.isLoggedIn) MothScreen.Home else MothScreen.Login)
    }

    // Apply FLAG_SECURE dynamically based on user privacy preference
    LaunchedEffect(mothState.screenshotBlockingEnabled) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val hasFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (mothState.screenshotBlockingEnabled && !hasFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (!mothState.screenshotBlockingEnabled && hasFlag) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Request POST_NOTIFICATIONS runtime permission on Android 13+ once logged in
    LaunchedEffect(mothState.isLoggedIn) {
        if (mothState.isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Track app foreground/background for live active status & notifications
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mothState.setAppForeground(true)
                Lifecycle.Event.ON_PAUSE -> mothState.setAppForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Open target chat when launched from a notification
    LaunchedEffect(pendingChatTarget, mothState.isLoggedIn) {
        if (!pendingChatTarget.isNullOrBlank() && mothState.isLoggedIn) {
            mothState.openConversation(pendingChatTarget)
            currentScreen = MothScreen.Chat(pendingChatTarget)
            onConsumedPendingChat()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "MothScreenTransition",
        ) { screen ->
            when (screen) {
                MothScreen.Login -> LoginScreen(
                    state = mothState,
                    onLoginSuccess = { currentScreen = MothScreen.Home },
                )
                MothScreen.Home -> ChatListScreen(
                    state = mothState,
                    onOpenChat = { title ->
                        if (mothState.activeChatId == null) {
                            mothState.openConversation(title)
                        }
                        currentScreen = MothScreen.Chat(title)
                    },
                    onSignOut = {
                        mothState.signOut()
                        currentScreen = MothScreen.Login
                    },
                )
                is MothScreen.Chat -> ChatScreen(
                    state = mothState,
                    chatTitle = screen.title,
                    onBack = {
                        mothState.closeActiveConversation()
                        currentScreen = MothScreen.Home
                    },
                )
            }
        }

        // Incoming / Active WebRTC Voice & Video Call Overlay
        if (mothState.activeCall != null) {
            ActiveCallOverlay(state = mothState)
        }

        // App Lock 4-Digit PIN Overlay
        if (mothState.isLoggedIn && mothState.isAppLocked) {
            AppLockOverlay(
                onUnlock = { pin -> mothState.unlockApp(pin) },
            )
        }
    }
}

@Composable
private fun AppLockOverlay(onUnlock: (String) -> Boolean) {
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .padding(32.dp)
            .testTag("app_lock_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelLantern(isLit = false)
                Text(
                    text = "lantern locked",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LanternAmber,
                )
                Text(
                    text = "enter your 4-digit pin",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                )

                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (pinInput.isEmpty()) {
                            Text(
                                text = "••••",
                                fontFamily = PixelifySans,
                                fontSize = 18.sp,
                                color = PlaceholderText,
                            )
                        }
                        BasicTextField(
                            value = pinInput,
                            onValueChange = { next ->
                                if (next.length <= 4 && next.all { it.isDigit() }) {
                                    pinInput = next
                                    errorText = null
                                    if (next.length == 4) {
                                        if (!onUnlock(next)) {
                                            errorText = "incorrect pin"
                                            pinInput = ""
                                        }
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 18.sp,
                                color = LanternAmber,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_pin_input"),
                        )
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = WaxSealRed,
                    )
                }

                SteppedButton(
                    text = "unlock",
                    onClick = {
                        if (!onUnlock(pinInput)) {
                            errorText = "incorrect pin"
                            pinInput = ""
                        }
                    },
                    backgroundColor = MyBubbleFill,
                    borderColor = MyBubbleBorder,
                    textColor = MyBubbleText,
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_unlock_button"),
                    cutSize = 3.dp,
                )
            }
        }
    }
}


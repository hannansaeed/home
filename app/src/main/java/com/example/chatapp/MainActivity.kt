package com.example.chatapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chatapp.ui.auth.LoginScreen
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.chatlist.ChatListScreen
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.theme.ChatTheme
import com.example.chatapp.ui.theme.MothBgDark
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
}

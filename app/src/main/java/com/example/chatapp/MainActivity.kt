package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MothBgDark,
                ) {
                    MothNavigationRoot()
                }
            }
        }
    }
}

@Composable
fun MothNavigationRoot() {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mothState = remember { MothState(context, scope) }
    var currentScreen by remember {
        mutableStateOf<MothScreen>(if (mothState.isLoggedIn) MothScreen.Home else MothScreen.Login)
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
                    mothState.openConversation(title)
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
                onBack = { currentScreen = MothScreen.Home },
            )
        }
    }
}

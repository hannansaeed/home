package com.example.chatapp

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import com.example.chatapp.ui.auth.LoginScreen
import com.example.chatapp.ui.calls.ActiveCallOverlay
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.chatlist.ChatListScreen
import com.example.chatapp.ui.components.LargeHangingLantern
import com.example.chatapp.ui.components.PixelLantern
import com.example.chatapp.ui.components.PixelMoth
import com.example.chatapp.ui.components.PixelStarfield
import com.example.chatapp.ui.moth.MothBackgroundSync
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
        val bootPrefs = getSharedPreferences("moth_session_prefs", Context.MODE_PRIVATE)
        val savedFontId = bootPrefs.getString("pref_app_font", AppFontOption.PIXELIFY_SANS.id)
        val savedThemeId = bootPrefs.getString("pref_app_theme", AppThemeOption.LANTERN_NIGHT.id)
        PixelifySans = AppFontOption.fromId(savedFontId).fontFamily
        activeAppTheme = AppThemeOption.fromId(savedThemeId)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = if (isBloomTheme) {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (isBloomTheme) {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        pendingChatTarget = intent?.getStringExtra("open_chat_user")

        setContent {
            val lightBars = isBloomTheme
            LaunchedEffect(lightBars) {
                enableEdgeToEdge(
                    statusBarStyle = if (lightBars) {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (lightBars) {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    },
                )
            }
            ChatTheme(appFontFamily = PixelifySans, themeOption = activeAppTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MothBgDark,
                ) {
                    MothApp(
                        pendingChatTarget = pendingChatTarget,
                        onConsumedPendingChat = { pendingChatTarget = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val appCtx = applicationContext
        Thread { syncDayNightAppIcon(appCtx) }.start()
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

private fun syncDayNightAppIcon(context: Context) {
    try {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isDay = hour in 6..17
        val pm = context.packageManager
        val dayComponent = ComponentName(context.packageName, "com.example.chatapp.LauncherDay")
        val nightComponent = ComponentName(context.packageName, "com.example.chatapp.LauncherNight")

        val dayState = pm.getComponentEnabledSetting(dayComponent)
        val nightState = pm.getComponentEnabledSetting(nightComponent)
        val isDayActive = (dayState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) ||
            (dayState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                nightState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        if (isDay == isDayActive) return

        val toEnable = if (isDay) dayComponent else nightComponent
        val toDisable = if (isDay) nightComponent else dayComponent
        pm.setComponentEnabledSetting(
            toEnable,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            toDisable,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    } catch (_: Exception) {
    }
}

@Composable
fun MothApp(
    pendingChatTarget: String? = null,
    onConsumedPendingChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOpeningSplash by remember { mutableStateOf(true) }
    var mothState by remember { mutableStateOf<MothState?>(null) }

    // Concurrently initialize MothState off the main thread so Frame 0 draws the lantern splash immediately
    LaunchedEffect(Unit) {
        val state = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MothState(context, scope)
        }
        mothState = state
    }

    // Preserve smooth lantern animation transition over the window splash
    LaunchedEffect(Unit) {
        delay(950L)
        while (mothState == null) {
            delay(50L)
        }
        showOpeningSplash = false
    }

    DisposableEffect(mothState) {
        val current = mothState
        onDispose {
            current?.dispose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val activeState = mothState
        if (activeState != null) {
            MothNavigationRoot(
                mothState = activeState,
                pendingChatTarget = pendingChatTarget,
                onConsumedPendingChat = onConsumedPendingChat,
            )
        }

        // Animated Opening Splash displays immediately on cold start and sits on top until finished
        AnimatedVisibility(
            visible = showOpeningSplash,
            exit = fadeOut(animationSpec = tween(350)),
        ) {
            MothOpeningSplash()
        }
    }
}

@Composable
fun MothNavigationRoot(
    mothState: MothState,
    pendingChatTarget: String? = null,
    onConsumedPendingChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentScreen by remember {
        mutableStateOf<MothScreen>(if (mothState.isLoggedIn) MothScreen.Home else MothScreen.Login)
    }

    // Apply FLAG_SECURE dynamically based on privacy preference or active View-Once playback
    LaunchedEffect(mothState.screenshotBlockingEnabled, mothState.isViewingViewOnce) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val secureRequired = mothState.screenshotBlockingEnabled || mothState.isViewingViewOnce
        val hasFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (secureRequired && !hasFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (!secureRequired && hasFlag) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && mothState.isLoggedIn) {
            MothBackgroundSync.syncState(context)
        }
    }

    // Request POST_NOTIFICATIONS runtime permission on Android 13+ once logged in,
    // and ensure background messaging listener / periodic fallback worker are active.
    LaunchedEffect(mothState.isLoggedIn) {
        if (mothState.isLoggedIn) {
            MothBackgroundSync.syncState(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Track app foreground/background for live active status & notifications
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mothState.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> mothState.setAppForeground(false)
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
                        if (!mothState.activeChatId.equals("${mothState.currentUsername.lowercase()}::${title.trim().removePrefix("@").lowercase()}", ignoreCase = true)) {
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
private fun MothOpeningSplash() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val lanternScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.92f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "lanternScale",
    )
    val pulseTransition = rememberInfiniteTransition(label = "splashPulse")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .testTag("opening_splash"),
        contentAlignment = Alignment.Center,
    ) {
        PixelStarfield()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PixelMoth(isAnimated = true)
                Box(
                    modifier = Modifier
                        .scale(lanternScale)
                        .alpha(glowAlpha),
                ) {
                    LargeHangingLantern()
                }
                PixelMoth(isAnimated = true)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "farlight",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = LanternAmber,
                letterSpacing = 1.5.sp,
            )

            Text(
                text = "private by default",
                fontFamily = PixelifySans,
                fontSize = 13.sp,
                color = MutedText,
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
                    text = if (isBloomTheme) "garden locked" else "lantern locked",
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


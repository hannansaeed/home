package com.example.chatapp.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chatapp.ui.components.PixelLantern
import com.example.chatapp.ui.components.PixelMicIcon
import com.example.chatapp.ui.components.PixelPhoneIcon
import com.example.chatapp.ui.components.PixelSearchIcon
import com.example.chatapp.ui.components.PixelSpeakerIcon
import com.example.chatapp.ui.components.PixelStarfield
import com.example.chatapp.ui.components.UserAvatar
import com.example.chatapp.ui.moth.CallDirection
import com.example.chatapp.ui.moth.CallType
import com.example.chatapp.ui.moth.MothCallLog
import com.example.chatapp.ui.moth.MothState
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CallHistoryEmptyPage(state: MothState) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // all | missed | incoming | outgoing
    var dialHandle by remember { mutableStateOf("") }
    var callError by remember { mutableStateOf<String?>(null) }

    var pendingDialPeer by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val peer = pendingDialPeer
        pendingDialPeer = null
        if (peer != null && granted) {
            state.startCall(peer, CallType.VOICE) { err -> callError = err }
        } else if (peer != null) {
            callError = "mic permission required to call"
        }
    }

    fun initiateVoiceCall(peer: String) {
        callError = null
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            state.startCall(peer, CallType.VOICE) { err -> callError = err }
        } else {
            pendingDialPeer = peer
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val filteredLogs = remember(state.callLogs, searchQuery, filterMode) {
        state.callLogs.filter { log ->
            val matchesFilter = when (filterMode) {
                "missed" -> log.direction == CallDirection.MISSED
                "incoming" -> log.direction == CallDirection.INCOMING
                "outgoing" -> log.direction == CallDirection.OUTGOING
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                log.peerUsername.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .testTag("empty_page_view"),
    ) {
        PixelStarfield()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "lantern calls",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "encrypted voice calls",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                    )
                }
                PixelLantern(isLit = true)
            }

            // Quick Call Dialer Card
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
                            borderColor = MothDivider,
                            cutSize = 3.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (dialHandle.isEmpty()) {
                                    Text(
                                        text = "enter handle to call...",
                                        fontFamily = PixelifySans,
                                        fontSize = 12.sp,
                                        color = PlaceholderText,
                                    )
                                }
                                BasicTextField(
                                    value = dialHandle,
                                    onValueChange = { dialHandle = it; callError = null },
                                    textStyle = TextStyle(
                                        fontFamily = PixelifySans,
                                        fontSize = 13.sp,
                                        color = TheirBubbleText,
                                    ),
                                    cursorBrush = SolidColor(LanternAmber),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("call_dial_input"),
                                )
                            }
                        }

                        SteppedButton(
                            text = "call",
                            onClick = {
                                if (dialHandle.isNotBlank()) initiateVoiceCall(dialHandle.trim())
                            },
                            backgroundColor = MothBgDark,
                            borderColor = LanternAmber,
                            textColor = LanternAmber,
                            cutSize = 3.dp,
                            leadingIcon = {
                                PixelPhoneIcon(tint = LanternAmber, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("dial_voice_button"),
                        )
                    }

                    if (callError != null) {
                        Text(
                            text = callError ?: "",
                            fontFamily = PixelifySans,
                            fontSize = 11.sp,
                            color = WaxSealRed,
                        )
                    }
                }
            }

            // Search Call History
            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                backgroundColor = MothHeaderBg,
                borderColor = MothDivider,
                cutSize = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelSearchIcon(tint = MutedText, modifier = Modifier.size(18.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "search call history...",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
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
                                .testTag("call_history_search_input"),
                        )
                    }
                }
            }

            // Filter Pills: All, Missed, Incoming, Outgoing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("all", "missed", "incoming", "outgoing").forEach { tab ->
                    val selected = filterMode == tab
                    SteppedBox(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { filterMode = tab }
                            .testTag("call_filter_$tab"),
                        backgroundColor = if (selected) MyBubbleFill else MothHeaderBg,
                        borderColor = if (selected) MyBubbleBorder else MothDivider,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab,
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MyBubbleText else MutedText,
                            )
                        }
                    }
                }
            }

            // Call History List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.callLogs.isEmpty()) "no calls recorded yet" else "no matching calls",
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        CallLogRow(
                            log = log,
                            onCallVoice = { initiateVoiceCall(log.peerUsername) },
                            onDelete = { state.deleteCallLog(log.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(
    log: MothCallLog,
    onCallVoice: () -> Unit,
    onDelete: () -> Unit,
) {
    val dirArrow = when (log.direction) {
        CallDirection.INCOMING -> "↙"
        CallDirection.OUTGOING -> "↗"
        CallDirection.MISSED -> "✕"
    }
    val dirColor = when (log.direction) {
        CallDirection.INCOMING -> SecureGreen
        CallDirection.OUTGOING -> LanternAmber
        CallDirection.MISSED -> WaxSealRed
    }
    val durationLabel = if (log.durationSec > 0) {
        val m = log.durationSec / 60
        val s = log.durationSec % 60
        if (m > 0) "${m}m ${s}s" else "${s}s"
    } else log.direction.name.lowercase()

    SteppedBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_log_${log.id}"),
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
            UserAvatar(avatarPath = null, color = LanternAmber, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${log.peerUsername}",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TheirBubbleText,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = dirArrow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = dirColor,
                    )
                    Text(
                        text = "voice • $durationLabel • ${log.timeLabel}",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = if (log.direction == CallDirection.MISSED) WaxSealRed else MutedText,
                    )
                }
            }
            SteppedBox(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onCallVoice),
                backgroundColor = MothBgDark,
                borderColor = LanternAmber,
                cutSize = 2.dp,
                borderWidth = 1.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PixelPhoneIcon(tint = LanternAmber, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = "✕",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
fun ActiveCallOverlay(state: MothState) {
    val call = state.activeCall ?: return
    val context = LocalContext.current

    var elapsedSec by remember(call.callId, call.status) { mutableIntStateOf(0) }
    LaunchedEffect(call.callId, call.status, call.startedAtMs) {
        if (call.status == "ACTIVE" && call.startedAtMs > 0L) {
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - call.startedAtMs) / 1000L).toInt().coerceAtLeast(0)
                delay(1000L)
            }
        }
    }

    val answerPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            state.acceptIncomingCall()
        } else {
            state.declineOrEndCall()
        }
    }

    fun acceptWithPermissions() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) state.acceptIncomingCall() else answerPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("active_call_overlay"),
    ) {
        PixelStarfield()

        // Top / Center Peer Header
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(avatarPath = null, color = LanternAmber, size = 84.dp)
            Text(
                text = "@${call.peerUsername}",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = LanternAmber,
            )
            val statusLine = when {
                call.status == "RINGING" && call.isCaller -> "ringing..."
                call.status == "RINGING" && !call.isCaller -> "incoming voice call"
                else -> {
                    val m = elapsedSec / 60
                    val s = elapsedSec % 60
                    val timerStr = "%02d:%02d".format(m, s)
                    "${state.webRtcManager.connectionState} • $timerStr"
                }
            }
            Text(
                text = statusLine,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = if (call.status == "ACTIVE") SecureGreen else MutedText,
            )
        }

        // Bottom Controls (outlined pixel icons)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!call.isCaller && call.status == "RINGING") {
                SteppedButton(
                    text = "✕ decline",
                    onClick = { state.declineOrEndCall() },
                    backgroundColor = WaxSealRed,
                    borderColor = WaxSealDarkEdge,
                    textColor = Color.White,
                    modifier = Modifier.testTag("decline_call_button"),
                )
                SteppedButton(
                    text = "✓ accept",
                    onClick = { acceptWithPermissions() },
                    backgroundColor = SecureGreen,
                    borderColor = TheirBubbleBorder,
                    textColor = MothBgDark,
                    modifier = Modifier.testTag("accept_call_button"),
                )
            } else {
                val muted = state.webRtcManager.isMuted
                SteppedButton(
                    text = if (muted) "unmute" else "mute",
                    onClick = { state.webRtcManager.toggleMute() },
                    backgroundColor = if (muted) MyBubbleFill else MothHeaderBg,
                    borderColor = TheirBubbleBorder,
                    textColor = if (muted) MyBubbleText else TheirBubbleText,
                    leadingIcon = {
                        PixelMicIcon(
                            tint = if (muted) MyBubbleText else TheirBubbleText,
                            muted = muted,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("call_mute_button"),
                )
                val speakerOn = state.webRtcManager.isSpeakerOn
                SteppedButton(
                    text = if (speakerOn) "spkr" else "ear",
                    onClick = { state.webRtcManager.toggleSpeaker() },
                    backgroundColor = if (speakerOn) MyBubbleFill else MothHeaderBg,
                    borderColor = TheirBubbleBorder,
                    textColor = if (speakerOn) MyBubbleText else TheirBubbleText,
                    leadingIcon = {
                        PixelSpeakerIcon(
                            tint = if (speakerOn) MyBubbleText else TheirBubbleText,
                            speakerOn = speakerOn,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("call_speaker_button"),
                )
                SteppedButton(
                    text = "✕ end",
                    onClick = { state.declineOrEndCall() },
                    backgroundColor = WaxSealRed,
                    borderColor = WaxSealDarkEdge,
                    textColor = Color.White,
                    modifier = Modifier.testTag("end_call_button"),
                )
            }
        }
    }
}

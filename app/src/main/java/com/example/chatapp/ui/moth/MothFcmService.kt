package com.example.chatapp.ui.moth

import android.app.NotificationManager
import android.content.Context
import com.example.chatapp.R
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class MothFcmService : FirebaseMessagingService() {

    private val db: FirebaseFirestore by lazy {
        try {
            FirebaseApp.initializeApp(applicationContext)
        } catch (_: Exception) {}
        FirebaseFirestore.getInstance(
            applicationContext.getString(R.string.firestore_database_id)
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return
        runBlocking(Dispatchers.IO) {
            MothState.persistFcmToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val cfg = MothBackgroundSync.readConfig(applicationContext)
        if (cfg.username.isBlank()) return

        runBlocking(Dispatchers.IO) {
            val data = remoteMessage.data
            val relayKind = data["relayKind"]?.trim()?.uppercase().orEmpty()

            if (relayKind == "CALL" || !data["callId"].isNullOrBlank()) {
                handleCallAlert(cfg, data)
                return@runBlocking
            }

            // Sync pending encrypted relay messages from Firestore so Room history,
            // DELIVERED receipts, and grouped notifications update identically to live streams.
            var processedRelay = false
            try {
                val relaySnap = db.collection("relay_messages")
                    .whereEqualTo("recipientUsername", cfg.username)
                    .get()
                    .await()
                if (relaySnap.documents.isNotEmpty()) {
                    MothState.processIncomingRelayBatchAwait(applicationContext, relaySnap.documents)
                    processedRelay = true
                }
            } catch (_: Exception) {}

            if (!processedRelay && cfg.msgNotifEnabled) {
                postFallbackFcmMessageAlert(cfg.username, remoteMessage)
            }
        }
    }

    private suspend fun handleCallAlert(
        cfg: MothBackgroundSync.BgConfig,
        data: Map<String, String>,
    ) {
        if (!cfg.callNotifEnabled || MothState.hasActiveUiInstanceFor(cfg.username)) return

        try {
            val callSnap = db.collection("call_sessions")
                .whereEqualTo("callee", cfg.username)
                .get()
                .await()
            val nowLocalMs = System.currentTimeMillis()
            val ringingDoc = callSnap.documents
                .sortedByDescending { it.getLong("createdAt") ?: 0L }
                .firstOrNull { d ->
                    val status = d.getString("status") ?: ""
                    if (status != "RINGING") return@firstOrNull false
                    val serverTs = d.getTimestamp("serverCreatedAt")?.toDate()?.time
                    if (serverTs != null) {
                        (nowLocalMs - serverTs) in -60_000L..120_000L
                    } else {
                        val created = d.getLong("createdAt") ?: nowLocalMs
                        kotlin.math.abs(nowLocalMs - created) < 120_000L
                    }
                }

            if (ringingDoc != null) {
                val caller = ringingDoc.getString("caller")?.removePrefix("@")?.lowercase().orEmpty()
                val callTypeStr = ringingDoc.getString("callType") ?: CallType.VOICE.name
                val callType = runCatching { CallType.valueOf(callTypeStr) }.getOrDefault(CallType.VOICE)
                val offerSdp = ringingDoc.getString("offerSdp")
                if (caller.isNotBlank() &&
                    !offerSdp.isNullOrBlank() &&
                    !MothBackgroundSync.wasCallNotifiedRecently(applicationContext, ringingDoc.id)
                ) {
                    MothBackgroundSync.markCallNotified(applicationContext, ringingDoc.id)
                    MothState.postIncomingCallNotification(applicationContext, caller, callType)
                }
                return
            }
        } catch (_: Exception) {}

        // Fallback if payload directly specifies caller
        val callId = data["callId"].orEmpty()
        val caller = (data["caller"] ?: data["senderUsername"] ?: "").trim().removePrefix("@").lowercase()
        val callType = runCatching {
            CallType.valueOf(data["callType"] ?: CallType.VOICE.name)
        }.getOrDefault(CallType.VOICE)
        if (caller.isNotBlank() && (callId.isBlank() || !MothBackgroundSync.wasCallNotifiedRecently(applicationContext, callId))) {
            if (callId.isNotBlank()) MothBackgroundSync.markCallNotified(applicationContext, callId)
            MothState.postIncomingCallNotification(applicationContext, caller, callType)
        }
    }

    private suspend fun postFallbackFcmMessageAlert(
        ownerUsername: String,
        remoteMessage: RemoteMessage,
    ) {
        val data = remoteMessage.data
        val rawSender = (data["senderUsername"] ?: data["sender"] ?: remoteMessage.notification?.title ?: "")
            .trim()
            .removePrefix("@")
            .lowercase()
        val groupId = data["groupId"]?.trim().orEmpty()
        val rawChatId = data["chatId"]?.trim().orEmpty()
        val openTarget = when {
            groupId.isNotBlank() -> groupId
            rawSender.isNotBlank() -> rawSender
            else -> ""
        }
        val chatId = when {
            rawChatId.isNotBlank() -> rawChatId
            openTarget.isNotBlank() -> "${ownerUsername.lowercase()}::$openTarget"
            else -> ""
        }

        if (openTarget.isBlank() && remoteMessage.notification?.body.isNullOrBlank() && data["body"].isNullOrBlank()) {
            return
        }

        if (MothState.isChatCurrentlyOpenProcessWide(chatId, rawSender, openTarget)) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            listOf(chatId, openTarget, rawSender).filter { it.isNotBlank() }.forEach {
                nm?.cancel(it.hashCode())
            }
            return
        }

        val previewsEnabled = applicationContext
            .getSharedPreferences("moth_session_prefs", Context.MODE_PRIVATE)
            .getBoolean("pref_notif_previews", true)
        val rawBody = data["body"] ?: data["text"] ?: remoteMessage.notification?.body ?: "new encrypted message"
        val displayBody = if (previewsEnabled) rawBody else "new message"
        val senderTitle = when {
            !data["senderTitle"].isNullOrBlank() -> data["senderTitle"]!!
            rawSender.isNotBlank() -> "@$rawSender"
            !remoteMessage.notification?.title.isNullOrBlank() -> remoteMessage.notification?.title!!
            else -> "farlight"
        }

        MothState.postConversationNotification(
            context = applicationContext,
            ownerUsername = ownerUsername,
            chatId = chatId,
            openTarget = openTarget.ifBlank { ownerUsername },
            senderTitle = senderTitle,
            senderHandle = rawSender,
            fallbackBody = displayBody,
            alertOnce = false,
        )
    }
}

package com.example.chatapp.ui.moth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isBlank()) return

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val openTarget = intent.getStringExtra(EXTRA_OPEN_TARGET).orEmpty()
        val senderTitle = intent.getStringExtra(EXTRA_SENDER_TITLE).orEmpty().ifBlank { openTarget }
        val senderHandle = intent.getStringExtra(EXTRA_SENDER_HANDLE).orEmpty().ifBlank { openTarget }
        val incomingBody = intent.getStringExtra(EXTRA_INCOMING_BODY).orEmpty()
        if (chatId.isBlank()) return

        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val owner = MothState.sendDirectReplyAwait(appCtx, chatId, replyText)
                MothState.postConversationNotification(
                    context = appCtx,
                    ownerUsername = owner,
                    chatId = chatId,
                    openTarget = openTarget,
                    senderTitle = senderTitle,
                    senderHandle = senderHandle,
                    fallbackBody = incomingBody.ifBlank { replyText },
                    alertOnce = true,
                )
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.example.chatapp.ACTION_NOTIFICATION_REPLY"
        const val KEY_TEXT_REPLY = "key_moth_direct_reply"
        const val EXTRA_CHAT_ID = "extra_reply_chat_id"
        const val EXTRA_OPEN_TARGET = "extra_reply_open_target"
        const val EXTRA_NOTIF_ID = "extra_reply_notif_id"
        const val EXTRA_SENDER_TITLE = "extra_reply_sender_title"
        const val EXTRA_SENDER_HANDLE = "extra_reply_sender_handle"
        const val EXTRA_INCOMING_BODY = "extra_reply_incoming_body"
    }
}


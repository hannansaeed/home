package com.example.chatapp.ui.moth

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.chatapp.MainActivity
import com.example.chatapp.R
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
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, openTarget.hashCode())
        val senderTitle = intent.getStringExtra(EXTRA_SENDER_TITLE).orEmpty().ifBlank { openTarget }
        val incomingBody = intent.getStringExtra(EXTRA_INCOMING_BODY).orEmpty()
        if (chatId.isBlank()) return

        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MothState.sendDirectReplyAwait(appCtx, chatId, replyText)

                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        appCtx,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (nm != null && canNotify) {
                    val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_chat_user", openTarget)
                    }
                    val openPending = PendingIntent.getActivity(
                        appCtx,
                        notifId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val mePerson = Person.Builder().setName("you").build()
                    val peerPerson = Person.Builder().setName(senderTitle).build()
                    val style = NotificationCompat.MessagingStyle(mePerson)
                    if (incomingBody.isNotBlank()) {
                        style.addMessage(incomingBody, System.currentTimeMillis() - 1000L, peerPerson)
                    }
                    style.addMessage(replyText, System.currentTimeMillis(), mePerson)

                    val updated = NotificationCompat.Builder(appCtx, MothState.NOTIF_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_moth_notification)
                        .setStyle(style)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .setContentIntent(openPending)
                        .build()
                    nm.notify(notifId, updated)
                }
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
        const val EXTRA_INCOMING_BODY = "extra_reply_incoming_body"
    }
}

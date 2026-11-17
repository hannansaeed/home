package com.example.chatapp.ui.moth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MothBackgroundSync {
    private const val PREFS_NAME = "moth_session_prefs"
    private const val KEY_LOGGED_IN_USER = "logged_in_username"
    private const val KEY_MSG_NOTIF = "pref_msg_notifications"
    private const val KEY_CALL_NOTIF = "pref_call_notifications"
    private const val KEY_LAST_NOTIFIED_CALL_ID = "bg_last_notified_call_id"
    private const val KEY_LAST_NOTIFIED_CALL_AT = "bg_last_notified_call_at"
    private const val UNIQUE_PERIODIC_WORK = "farlight_background_sync_periodic"

    internal const val ACTION_SYNC = "com.example.chatapp.ACTION_BG_SYNC"
    internal const val ACTION_UI_ATTACHED = "com.example.chatapp.ACTION_BG_UI_ATTACHED"
    internal const val ACTION_UI_DETACHED = "com.example.chatapp.ACTION_BG_UI_DETACHED"

    internal const val BG_CHANNEL_ID = "farlight_background_v1"
    internal const val BG_NOTIF_ID = 44019

    internal data class BgConfig(
        val username: String,
        val msgNotifEnabled: Boolean,
        val callNotifEnabled: Boolean,
    ) {
        val shouldRun: Boolean
            get() = username.isNotBlank() && (msgNotifEnabled || callNotifEnabled)
    }

    internal fun readConfig(context: Context): BgConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BgConfig(
            username = prefs.getString(KEY_LOGGED_IN_USER, "")?.trim()?.lowercase().orEmpty(),
            msgNotifEnabled = prefs.getBoolean(KEY_MSG_NOTIF, true),
            callNotifEnabled = prefs.getBoolean(KEY_CALL_NOTIF, true),
        )
    }

    fun syncState(context: Context) {
        val appCtx = context.applicationContext
        val cfg = readConfig(appCtx)
        if (!cfg.shouldRun) {
            cancelWorker(appCtx)
            stopService(appCtx)
            return
        }
        schedulePeriodicWorker(appCtx)
        tryStartForegroundService(appCtx, ACTION_SYNC)
    }

    fun onUiAttached(context: Context) {
        val appCtx = context.applicationContext
        val cfg = readConfig(appCtx)
        if (!cfg.shouldRun) {
            cancelWorker(appCtx)
            stopService(appCtx)
            return
        }
        schedulePeriodicWorker(appCtx)
        tryStartForegroundService(appCtx, ACTION_UI_ATTACHED)
    }

    fun onUiDetached(context: Context) {
        val appCtx = context.applicationContext
        val cfg = readConfig(appCtx)
        if (!cfg.shouldRun) {
            cancelWorker(appCtx)
            stopService(appCtx)
            return
        }
        schedulePeriodicWorker(appCtx)
        tryStartForegroundService(appCtx, ACTION_UI_DETACHED)
    }

    fun scheduleWorkerIfNeeded(context: Context) {
        val appCtx = context.applicationContext
        if (readConfig(appCtx).shouldRun) {
            schedulePeriodicWorker(appCtx)
        }
    }

    fun onBootOrPackageReplaced(context: Context) {
        val appCtx = context.applicationContext
        if (!readConfig(appCtx).shouldRun) return
        schedulePeriodicWorker(appCtx)
        tryStartForegroundService(appCtx, ACTION_SYNC)
    }

    internal fun tryStartForegroundService(context: Context, action: String) {
        val intent = Intent(context.applicationContext, MothMessagingService::class.java).apply {
            this.action = action
        }
        try {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        } catch (_: Exception) {
            // OS may restrict background FGS launch on some API levels/OEMs; WorkManager remains scheduled.
        }
    }

    private fun stopService(context: Context) {
        try {
            context.applicationContext.stopService(
                Intent(context.applicationContext, MothMessagingService::class.java)
            )
        } catch (_: Exception) {}
    }

    internal fun schedulePeriodicWorker(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MothSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (_: Exception) {}
    }

    private fun cancelWorker(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        } catch (_: Exception) {}
    }

    internal fun wasCallNotifiedRecently(context: Context, callId: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastId = prefs.getString(KEY_LAST_NOTIFIED_CALL_ID, "") ?: ""
        val lastAt = prefs.getLong(KEY_LAST_NOTIFIED_CALL_AT, 0L)
        return lastId == callId && (System.currentTimeMillis() - lastAt) < 180_000L
    }

    internal fun markCallNotified(context: Context, callId: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFIED_CALL_ID, callId)
            .putLong(KEY_LAST_NOTIFIED_CALL_AT, System.currentTimeMillis())
            .apply()
    }
}

class MothMessagingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var relayListener: ListenerRegistration? = null
    private var callListener: ListenerRegistration? = null
    private var listeningUsername: String = ""
    private val activeRingingCalls = ConcurrentHashMap<String, String>() // callId -> caller

    private val db: FirebaseFirestore by lazy {
        try {
            FirebaseApp.initializeApp(applicationContext)
        } catch (_: Exception) {}
        FirebaseFirestore.getInstance(applicationContext.getString(R.string.firestore_database_id))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForegroundImmediately()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundImmediately()

        val cfg = MothBackgroundSync.readConfig(applicationContext)
        if (!cfg.shouldRun) {
            detachListeners()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val action = intent?.action
        val uiActive = action != MothBackgroundSync.ACTION_UI_DETACHED &&
            MothState.hasActiveUiInstanceFor(cfg.username)

        if (uiActive) {
            detachListeners()
        } else {
            attachListeners(cfg.username)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val cfg = MothBackgroundSync.readConfig(applicationContext)
        if (!cfg.shouldRun) return

        // If Activity's task was swiped away before Compose dispose finished, tear down UI streams
        // cleanly and immediately take over Firestore listeners inside this service.
        MothState.disposeActiveUiIfPresent()
        MothBackgroundSync.schedulePeriodicWorker(applicationContext)
        attachListeners(cfg.username)
    }

    override fun onDestroy() {
        detachListeners()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForegroundImmediately() {
        val notification = buildOngoingNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    MothBackgroundSync.BG_NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
                )
            } else {
                startForeground(MothBackgroundSync.BG_NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            // Fallback without type on older platforms if needed
            try {
                startForeground(MothBackgroundSync.BG_NOTIF_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun buildOngoingNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(MothBackgroundSync.BG_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    MothBackgroundSync.BG_CHANNEL_ID,
                    "Farlight Background Connection",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Keeps Farlight connected to receive encrypted messages and calls"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            MothBackgroundSync.BG_NOTIF_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, MothBackgroundSync.BG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moth_notification)
            .setColor(0xFFFFB43B.toInt())
            .setContentTitle("farlight")
            .setContentText("listening for messages & calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pending)
            .build()
    }

    private fun attachListeners(username: String) {
        if (username.isBlank()) return
        if (listeningUsername == username && relayListener != null && callListener != null) {
            return
        }
        detachListeners()
        listeningUsername = username

        relayListener = db.collection("relay_messages")
            .whereEqualTo("recipientUsername", username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (MothState.hasActiveUiInstanceFor(username)) return@addSnapshotListener
                if (snapshot.documents.isEmpty()) return@addSnapshotListener
                val docs = snapshot.documents
                serviceScope.launch {
                    MothState.processIncomingRelayBatchAwait(applicationContext, docs)
                }
            }

        callListener = db.collection("call_sessions")
            .whereEqualTo("callee", username)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                if (MothState.hasActiveUiInstanceFor(username)) return@addSnapshotListener
                val cfg = MothBackgroundSync.readConfig(applicationContext)
                if (!cfg.callNotifEnabled) return@addSnapshotListener

                val nowLocalMs = System.currentTimeMillis()
                val doc = snap.documents
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

                if (doc != null) {
                    val callId = doc.id
                    val caller = doc.getString("caller")?.removePrefix("@")?.lowercase().orEmpty()
                    val callTypeStr = doc.getString("callType") ?: CallType.VOICE.name
                    val callType = try { CallType.valueOf(callTypeStr) } catch (_: Exception) { CallType.VOICE }
                    val offerSdp = doc.getString("offerSdp")
                    if (caller.isNotBlank() && !offerSdp.isNullOrBlank()) {
                        if (!activeRingingCalls.containsKey(callId) &&
                            !MothBackgroundSync.wasCallNotifiedRecently(applicationContext, callId)
                        ) {
                            activeRingingCalls[callId] = caller
                            MothBackgroundSync.markCallNotified(applicationContext, callId)
                            MothState.postIncomingCallNotification(applicationContext, caller, callType)
                        }
                    }
                } else if (activeRingingCalls.isNotEmpty()) {
                    val endedCallers = activeRingingCalls.values.distinct()
                    activeRingingCalls.clear()
                    endedCallers.forEach { caller ->
                        MothState.cancelNotificationForHandle(applicationContext, caller)
                    }
                }
            }
    }

    private fun detachListeners() {
        relayListener?.remove()
        relayListener = null
        callListener?.remove()
        callListener = null
        listeningUsername = ""
        activeRingingCalls.clear()
    }
}

class MothSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cfg = MothBackgroundSync.readConfig(applicationContext)
        if (!cfg.shouldRun) return Result.success()
        if (MothState.hasActiveUiInstanceFor(cfg.username)) return Result.success()

        return try {
            try {
                FirebaseApp.initializeApp(applicationContext)
            } catch (_: Exception) {}
            val db = FirebaseFirestore.getInstance(
                applicationContext.getString(R.string.firestore_database_id)
            )

            val relaySnap = db.collection("relay_messages")
                .whereEqualTo("recipientUsername", cfg.username)
                .get()
                .await()
            if (relaySnap.documents.isNotEmpty()) {
                MothState.processIncomingRelayBatchAwait(applicationContext, relaySnap.documents)
            }

            if (cfg.callNotifEnabled && !MothState.hasActiveUiInstanceFor(cfg.username)) {
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
                    val callType = try { CallType.valueOf(callTypeStr) } catch (_: Exception) { CallType.VOICE }
                    val offerSdp = ringingDoc.getString("offerSdp")
                    if (caller.isNotBlank() &&
                        !offerSdp.isNullOrBlank() &&
                        !MothBackgroundSync.wasCallNotifiedRecently(applicationContext, ringingDoc.id)
                    ) {
                        MothBackgroundSync.markCallNotified(applicationContext, ringingDoc.id)
                        MothState.postIncomingCallNotification(applicationContext, caller, callType)
                    }
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class MothBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            MothBackgroundSync.onBootOrPackageReplaced(context.applicationContext)
        }
    }
}

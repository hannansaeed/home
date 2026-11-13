package com.example.chatapp.ui.moth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.crypto.EcdhAesGcmCrypto
import com.example.chatapp.data.local.ChatDatabaseProvider
import com.example.chatapp.data.local.MothCallLogEntity
import com.example.chatapp.data.local.MothChatEntity
import com.example.chatapp.data.local.MothMessageEntity
import com.example.chatapp.data.local.MothStickerEntity
import com.example.chatapp.ui.calls.WebRtcCallManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MothState(private val context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mothDao = ChatDatabaseProvider.get(appContext).mothDao()
    val mediaDir: File = File(appContext.filesDir, "moth_media").apply { mkdirs() }

    private val db: FirebaseFirestore by lazy {
        try {
            FirebaseApp.initializeApp(appContext)
        } catch (_: Exception) {}
        FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
    }

    val crypto = EcdhAesGcmCrypto(appContext)
    val webRtcManager by lazy { WebRtcCallManager(appContext, db) }
    val voiceRecorder by lazy { VoiceNoteRecorder(appContext, mediaDir, scope) }
    val voicePlayer by lazy { VoiceNotePlayer(scope) }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val burnMutex = Mutex()
    private val groupKeysCache = ConcurrentHashMap<String, ByteArray>()

    var currentUsername by mutableStateOf("")
    var userAvatarColor by mutableStateOf(Color(0xFFB8A9E8))
    var userAvatarPath by mutableStateOf<String?>(null)
    var isAppInForeground by mutableStateOf(true)

    val isLoggedIn: Boolean
        get() = currentUsername.isNotBlank()

    // Privacy & Screen Controls
    var screenshotBlockingEnabled by mutableStateOf(prefs.getBoolean(KEY_SCREENSHOT_BLOCK, false))
        private set
    var hideTypingIndicators by mutableStateOf(prefs.getBoolean(KEY_HIDE_TYPING, false))
        private set
    var disableReadReceipts by mutableStateOf(prefs.getBoolean(KEY_DISABLE_READ_RECEIPTS, false))
        private set
    var messageFontScale by mutableStateOf(prefs.getFloat(KEY_FONT_SCALE, 1.0f))
        private set
    var appLockEnabled by mutableStateOf(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
        private set
    var isAppLocked by mutableStateOf(false)
        private set

    var conversations by mutableStateOf<List<MothConversation>>(emptyList())
    var activeChatId by mutableStateOf<String?>(null)
    var activeMessages by mutableStateOf<List<MothMessage>>(emptyList())
    var isContactTyping by mutableStateOf(false)
    var activeContactPresence by mutableStateOf(ContactPresence())
    var customStickers by mutableStateOf<List<MothSticker>>(emptyList())
    var callLogs by mutableStateOf<List<MothCallLog>>(emptyList())
    var activeCall by mutableStateOf<ActiveCallState?>(null)
    val contactOnlineMap = mutableStateMapOf<String, Boolean>()

    private var localChatsJob: Job? = null
    private var localMessagesJob: Job? = null
    private var localStickersJob: Job? = null
    private var localCallLogsJob: Job? = null
    private var burnMonitorJob: Job? = null
    private var presenceHeartbeatJob: Job? = null
    private var typingTimeoutJob: Job? = null
    private var callTimerJob: Job? = null

    private var relayListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    private var cloudChatsListener: ListenerRegistration? = null
    private var cloudStickersListener: ListenerRegistration? = null
    private var myProfileListener: ListenerRegistration? = null
    private var incomingCallListener: ListenerRegistration? = null
    private var groupMetaListener: ListenerRegistration? = null

    private val listPresenceListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val contactPresenceSnapshots = ConcurrentHashMap<String, DocumentSnapshot>()
    private val processingRelayIds = ConcurrentHashMap.newKeySet<String>()
    private val syncedAvatarUsers = ConcurrentHashMap.newKeySet<String>()
    private var lastTypingSentAt: Long = 0L
    private var lastTypingSentState: Boolean = false
    private var lastPausedAtMs: Long = 0L
    @Volatile private var serverClockOffsetMs: Long = 0L

    private fun serverNow(): Long = System.currentTimeMillis() + serverClockOffsetMs

    fun isContactOnline(username: String): Boolean {
        val clean = username.trim().lowercase()
        if (clean == "support") return true
        return contactOnlineMap[clean] == true
    }

    init {
        ensureNotificationChannel()

        val savedUser = prefs.getString(KEY_LOGGED_IN_USER, "")?.trim()?.lowercase().orEmpty()
        val savedColorArgb = prefs.getInt(KEY_AVATAR_COLOR, Color(0xFFB8A9E8).toArgb())
        userAvatarColor = Color(savedColorArgb)

        if (savedUser.isNotBlank()) {
            currentUsername = savedUser
            if (appLockEnabled && !prefs.getString(KEY_APP_LOCK_PIN_HASH, null).isNullOrBlank()) {
                isAppLocked = true
            }
            loadSavedAvatarForUser(savedUser)
            startSessionStreams(savedUser)
        }

        // Ensure default support user exists in Firestore directory
        ioScope.launch {
            try {
                val supportRef = db.collection("users").document("support")
                val snap = supportRef.get().await()
                if (!snap.exists()) {
                    supportRef.set(
                        mapOf(
                            "username" to "support",
                            "passphrase" to "support123",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // Privacy & Screen Settings
    // -------------------------------------------------------------------------

    fun setScreenshotBlocking(enabled: Boolean) {
        screenshotBlockingEnabled = enabled
        prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCK, enabled).apply()
    }

    fun setHideTyping(enabled: Boolean) {
        hideTypingIndicators = enabled
        prefs.edit().putBoolean(KEY_HIDE_TYPING, enabled).apply()
        if (enabled) {
            activeChatId?.let { updateMyTypingStatus(it, false) }
        }
    }

    fun updateDisableReadReceipts(enabled: Boolean) {
        disableReadReceipts = enabled
        prefs.edit().putBoolean(KEY_DISABLE_READ_RECEIPTS, enabled).apply()
    }

    fun setFontScale(scale: Float) {
        messageFontScale = scale
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    fun enableAppLock(pin: String): Boolean {
        val clean = pin.trim()
        if (clean.length != 4 || !clean.all { it.isDigit() }) return false
        prefs.edit()
            .putString(KEY_APP_LOCK_PIN_HASH, sha256(clean))
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
        appLockEnabled = true
        isAppLocked = false
        return true
    }

    fun disableAppLock() {
        prefs.edit()
            .remove(KEY_APP_LOCK_PIN_HASH)
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .apply()
        appLockEnabled = false
        isAppLocked = false
    }

    fun unlockApp(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_APP_LOCK_PIN_HASH, null) ?: return true
        return if (sha256(pin.trim()) == storedHash) {
            isAppLocked = false
            true
        } else {
            false
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Notifications & Foreground Presence
    // -------------------------------------------------------------------------

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Moth Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming Moth hours messages and calls"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun loadSavedAvatarForUser(username: String) {
        val path = prefs.getString("${KEY_AVATAR_PATH}_$username", null)
        userAvatarPath = if (!path.isNullOrBlank() && File(path).exists()) path else null
    }

    private fun deleteOldUserAvatarFiles(username: String, keepPath: String? = null) {
        try {
            val prefix = "my_avatar_${username}"
            mediaDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix) && file.absolutePath != keepPath) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun setAppForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        val user = currentUsername
        if (!inForeground) {
            lastPausedAtMs = System.currentTimeMillis()
        } else if (user.isNotBlank() && appLockEnabled && lastPausedAtMs > 0L) {
            if (System.currentTimeMillis() - lastPausedAtMs > 2500L) {
                isAppLocked = true
            }
        }
        if (user.isNotBlank()) {
            publishMyPresence(user, inForeground)
        }
    }

    private fun publishMyPresence(username: String, online: Boolean) {
        ioScope.launch {
            try {
                val docRef = db.collection("presence").document(username)
                docRef.set(
                    mapOf(
                        "isOnline" to online,
                        "lastSeen" to System.currentTimeMillis(),
                        "serverSeenAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                if (online) {
                    val snap = docRef.get().await()
                    val serverMs = snap.getTimestamp("serverSeenAt")?.toDate()?.time
                    if (serverMs != null) {
                        serverClockOffsetMs = serverMs - System.currentTimeMillis()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // Profile & Avatars
    // -------------------------------------------------------------------------

    fun updateAvatarColor(color: Color) {
        userAvatarColor = color
        prefs.edit().putInt(KEY_AVATAR_COLOR, color.toArgb()).apply()
        val user = currentUsername
        if (user.isNotBlank()) {
            ioScope.launch {
                try {
                    db.collection("users").document(user).set(
                        mapOf("avatarColorArgb" to color.toArgb().toLong()),
                        SetOptions.merge()
                    ).await()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun updateCustomAvatar(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val user = currentUsername
        if (user.isBlank()) return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read photo"))

            val side = minOf(bitmap.width, bitmap.height)
            val xOff = (bitmap.width - side) / 2
            val yOff = (bitmap.height - side) / 2
            val cropped = Bitmap.createBitmap(bitmap, xOff, yOff, side, side)
            val scaled = if (side > 300) {
                Bitmap.createScaledBitmap(cropped, 300, 300, true)
            } else cropped

            val outStream = ByteArrayOutputStream()
            var quality = 80
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            while (outStream.size() > MAX_AVATAR_BYTES && quality > 30) {
                outStream.reset()
                quality -= 15
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            }

            if (scaled !== cropped) scaled.recycle()
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()

            val bytes = outStream.toByteArray()
            val avatarFile = File(mediaDir, "my_avatar_${user}_${System.currentTimeMillis()}.jpg")
            avatarFile.writeBytes(bytes)
            deleteOldUserAvatarFiles(user, keepPath = avatarFile.absolutePath)

            userAvatarPath = avatarFile.absolutePath
            prefs.edit().putString("${KEY_AVATAR_PATH}_$user", avatarFile.absolutePath).apply()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            try {
                db.collection("users").document(user).set(
                    mapOf(
                        "avatarBase64" to base64,
                        "avatarColorArgb" to userAvatarColor.toArgb().toLong(),
                    ),
                    SetOptions.merge()
                ).await()
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCustomAvatar() {
        val user = currentUsername
        if (user.isBlank()) return
        userAvatarPath = null
        prefs.edit().remove("${KEY_AVATAR_PATH}_$user").apply()
        ioScope.launch {
            deleteOldUserAvatarFiles(user, keepPath = null)
            try {
                db.collection("users").document(user)
                    .update("avatarBase64", FieldValue.delete()).await()
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // Authentication & Session Streams
    // -------------------------------------------------------------------------

    fun registerUser(
        username: String,
        passphrase: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = passphrase.trim()

        if (cleanUser.length < 3) {
            onError("Username must be at least 3 characters")
            return
        }
        if (cleanPass.length < 3) {
            onError("Passphrase must be at least 3 characters")
            return
        }

        scope.launch {
            try {
                val userRef = db.collection("users").document(cleanUser)
                val doc = withContext(Dispatchers.IO) { userRef.get().await() }
                if (doc.exists()) {
                    onError("Lantern handle '$cleanUser' is already taken")
                    return@launch
                }

                val myPubKey = crypto.initializeForUser(cleanUser)
                val userPayload = mapOf(
                    "username" to cleanUser,
                    "passphrase" to cleanPass,
                    "avatarColorArgb" to userAvatarColor.toArgb().toLong(),
                    "e2eePublicKey" to myPubKey,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                withContext(Dispatchers.IO) { userRef.set(userPayload).await() }

                persistSession(cleanUser)
                loadSavedAvatarForUser(cleanUser)
                startSessionStreams(cleanUser)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Registration failed")
            }
        }
    }

    fun loginUser(
        username: String,
        passphrase: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = passphrase.trim()

        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            onError("Please enter username and passphrase")
            return
        }

        scope.launch {
            try {
                val userRef = db.collection("users").document(cleanUser)
                val doc = withContext(Dispatchers.IO) { userRef.get().await() }
                if (!doc.exists()) {
                    onError("No lantern found for '$cleanUser'")
                    return@launch
                }

                val storedPass = doc.getString("passphrase") ?: ""
                if (storedPass != cleanPass) {
                    onError("Incorrect passphrase")
                    return@launch
                }

                val remoteColor = doc.getLong("avatarColorArgb")
                if (remoteColor != null) {
                    userAvatarColor = Color(remoteColor.toInt())
                    prefs.edit().putInt(KEY_AVATAR_COLOR, remoteColor.toInt()).apply()
                }
                val remoteAvatarBase64 = doc.getString("avatarBase64")
                if (!remoteAvatarBase64.isNullOrBlank()) {
                    val dest = File(mediaDir, "my_avatar_${cleanUser}.jpg")
                    try {
                        dest.writeBytes(Base64.decode(remoteAvatarBase64, Base64.NO_WRAP))
                        deleteOldUserAvatarFiles(cleanUser, keepPath = dest.absolutePath)
                        prefs.edit().putString("${KEY_AVATAR_PATH}_$cleanUser", dest.absolutePath).apply()
                    } catch (_: Exception) {}
                } else {
                    deleteOldUserAvatarFiles(cleanUser, keepPath = null)
                    prefs.edit().remove("${KEY_AVATAR_PATH}_$cleanUser").apply()
                }

                persistSession(cleanUser)
                loadSavedAvatarForUser(cleanUser)
                startSessionStreams(cleanUser)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Login failed")
            }
        }
    }

    private fun persistSession(username: String) {
        currentUsername = username
        prefs.edit().putString(KEY_LOGGED_IN_USER, username).apply()
    }

    fun signOut() {
        val oldUser = currentUsername
        if (oldUser.isNotBlank()) {
            publishMyPresence(oldUser, false)
            activeChatId?.let { updateMyTypingStatus(it, false) }
        }
        voiceRecorder.cancelRecording()
        voicePlayer.stop()
        if (activeCall != null) {
            declineOrEndCall()
        }

        prefs.edit().remove(KEY_LOGGED_IN_USER).apply()
        localChatsJob?.cancel(); localChatsJob = null
        localMessagesJob?.cancel(); localMessagesJob = null
        localStickersJob?.cancel(); localStickersJob = null
        localCallLogsJob?.cancel(); localCallLogsJob = null
        burnMonitorJob?.cancel(); burnMonitorJob = null
        presenceHeartbeatJob?.cancel(); presenceHeartbeatJob = null
        typingTimeoutJob?.cancel(); typingTimeoutJob = null
        callTimerJob?.cancel(); callTimerJob = null

        relayListener?.remove(); relayListener = null
        typingListener?.remove(); typingListener = null
        presenceListener?.remove(); presenceListener = null
        cloudChatsListener?.remove(); cloudChatsListener = null
        cloudStickersListener?.remove(); cloudStickersListener = null
        myProfileListener?.remove(); myProfileListener = null
        incomingCallListener?.remove(); incomingCallListener = null
        groupMetaListener?.remove(); groupMetaListener = null
        listPresenceListeners.values.forEach { it.remove() }
        listPresenceListeners.clear()
        contactPresenceSnapshots.clear()
        contactOnlineMap.clear()

        processingRelayIds.clear()
        syncedAvatarUsers.clear()
        groupKeysCache.clear()
        currentUsername = ""
        userAvatarPath = null
        activeChatId = null
        isAppLocked = false
        conversations = emptyList()
        activeMessages = emptyList()
        customStickers = emptyList()
        callLogs = emptyList()
        isContactTyping = false
        activeContactPresence = ContactPresence()
    }

    private fun startSessionStreams(username: String) {
        if (username.isBlank()) return

        // Initialize E2EE keypair & publish public key to Firestore
        val myPubKey = crypto.initializeForUser(username)
        if (myPubKey.isNotBlank()) {
            ioScope.launch {
                try {
                    db.collection("users").document(username).set(
                        mapOf("e2eePublicKey" to myPubKey),
                        SetOptions.merge()
                    ).await()
                } catch (_: Exception) {}
            }
        }

        publishMyPresence(username, isAppInForeground)

        ioScope.launch {
            ensureLocalSupportChatExists(username)
            processBurningMessages(username)
            cleanupStaleTypingDocs(username)
            cleanupOrphanedLocalFiles(username)
        }

        // Own profile sync
        myProfileListener?.remove()
        myProfileListener = db.collection("users").document(username)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists() || currentUsername != username) return@addSnapshotListener
                ioScope.launch {
                    try {
                        val remoteColor = doc.getLong("avatarColorArgb")
                        if (remoteColor != null) {
                            val colorObj = Color(remoteColor.toInt())
                            if (userAvatarColor != colorObj) {
                                withContext(Dispatchers.Main) { userAvatarColor = colorObj }
                                prefs.edit().putInt(KEY_AVATAR_COLOR, remoteColor.toInt()).apply()
                            }
                        }
                        val remoteBase64 = doc.getString("avatarBase64")
                        if (!remoteBase64.isNullOrBlank()) {
                            val decoded = Base64.decode(remoteBase64, Base64.NO_WRAP)
                            val currentFile = userAvatarPath?.let { File(it) }
                            val alreadyMatches = currentFile != null && currentFile.exists() &&
                                currentFile.length().toInt() == decoded.size
                            if (!alreadyMatches) {
                                val dest = File(mediaDir, "my_avatar_${username}_${System.currentTimeMillis()}.jpg")
                                dest.writeBytes(decoded)
                                deleteOldUserAvatarFiles(username, keepPath = dest.absolutePath)
                                prefs.edit().putString("${KEY_AVATAR_PATH}_$username", dest.absolutePath).apply()
                                withContext(Dispatchers.Main) { userAvatarPath = dest.absolutePath }
                            }
                        } else if (!doc.metadata.hasPendingWrites()) {
                            deleteOldUserAvatarFiles(username, keepPath = null)
                            prefs.edit().remove("${KEY_AVATAR_PATH}_$username").apply()
                            if (userAvatarPath != null) {
                                withContext(Dispatchers.Main) { userAvatarPath = null }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

        // Cloud chats list sync (1:1 + groups)
        cloudChatsListener?.remove()
        cloudChatsListener = db.collection("users").document(username).collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || currentUsername != username) return@addSnapshotListener
                ioScope.launch {
                    try {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val isGroup = doc.getBoolean("isGroup") ?: false
                            val cloudGroupId = doc.getString("groupId")?.takeIf { it.isNotBlank() }
                            val other = doc.getString("otherUsername")?.trim()
                                ?.let { if (isGroup) it else it.lowercase() }
                                ?: doc.id.trim().lowercase()
                            if (other.isBlank()) continue
                            val chatId = doc.getString("id")
                                ?: chatIdFor(username, if (isGroup) (cloudGroupId ?: doc.id) else other)
                            if (change.type == DocumentChange.Type.REMOVED) {
                                mothDao.deleteChat(chatId)
                                continue
                            }
                            val cloudUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val local = mothDao.getChat(chatId)
                            if (local == null || cloudUpdatedAt > local.updatedAt) {
                                val isGroup = doc.getBoolean("isGroup") ?: (local?.isGroup ?: false)
                                val entity = MothChatEntity(
                                    id = chatId,
                                    ownerUsername = username,
                                    otherUsername = other,
                                    preview = doc.getString("preview") ?: (local?.preview ?: "lantern lit"),
                                    time = doc.getString("time") ?: (local?.time ?: currentTimeLabel()),
                                    avatarColorArgb = doc.getLong("avatarColorArgb")
                                        ?: (local?.avatarColorArgb ?: avatarColorForUsername(other).toArgb().toLong()),
                                    avatarLocalPath = local?.avatarLocalPath,
                                    unreadCount = (doc.getLong("unreadCount") ?: (local?.unreadCount?.toLong() ?: 0L)).toInt(),
                                    isLit = doc.getBoolean("isLit") ?: (local?.isLit ?: false),
                                    isDisappearing = doc.getBoolean("isDisappearing") ?: false,
                                    isViewOnce = doc.getBoolean("isViewOnce") ?: false,
                                    updatedAt = maxOf(cloudUpdatedAt, local?.updatedAt ?: 0L),
                                    isGroup = isGroup,
                                    groupIcon = doc.getString("groupIcon") ?: (local?.groupIcon ?: "🏮"),
                                    adminsJson = doc.getString("adminsJson") ?: (local?.adminsJson ?: "[]"),
                                    membersJson = doc.getString("membersJson") ?: (local?.membersJson ?: "[]"),
                                )
                                mothDao.upsertChat(entity)
                                if (!isGroup && other != "support") {
                                    syncContactAvatarInBackground(username, other, chatId)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

        // Cloud stickers sync
        cloudStickersListener?.remove()
        var initialStickerPushDone = false
        cloudStickersListener = db.collection("users").document(username).collection("stickers")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || currentUsername != username) return@addSnapshotListener
                ioScope.launch {
                    try {
                        val cloudIds = snapshot.documents.map { it.id }.toSet()
                        if (!initialStickerPushDone) {
                            initialStickerPushDone = true
                            val localStickers = mothDao.getStickersOnce(username)
                            for (local in localStickers) {
                                if (local.id !in cloudIds) {
                                    val f = File(local.imageLocalPath)
                                    if (f.exists()) syncStickerToCloud(local, f.readBytes())
                                }
                            }
                        }

                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val stkId = doc.getString("id") ?: doc.id
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val existingFile = File(mediaDir, "${stkId}.png")
                                if (existingFile.exists()) existingFile.delete()
                                mothDao.deleteSticker(stkId)
                            } else {
                                val label = doc.getString("label") ?: "sticker"
                                val base64 = doc.getString("imageBase64") ?: continue
                                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                val dest = File(mediaDir, "${stkId}.png")
                                if (!dest.exists()) {
                                    dest.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
                                }
                                mothDao.upsertSticker(
                                    MothStickerEntity(
                                        id = stkId,
                                        ownerUsername = username,
                                        label = label,
                                        imageLocalPath = dest.absolutePath,
                                        createdAt = createdAt,
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

        // Observe local Room chats
        localChatsJob?.cancel()
        localChatsJob = scope.launch {
            mothDao.observeChats(username).collect { entities ->
                syncListPresenceListeners(entities.filter { !it.isGroup }.map { it.otherUsername })
                conversations = entities.map { entity ->
                    if (!entity.isGroup && entity.otherUsername != "support" && syncedAvatarUsers.add(entity.otherUsername)) {
                        syncContactAvatarInBackground(username, entity.otherUsername, entity.id)
                    }
                    MothConversation(
                        id = entity.id,
                        name = entity.otherUsername,
                        preview = entity.preview,
                        time = entity.time,
                        avatarColor = Color(entity.avatarColorArgb.toInt()),
                        avatarLocalPath = entity.avatarLocalPath?.takeIf { File(it).exists() },
                        unreadCount = entity.unreadCount,
                        isLit = if (entity.isGroup) true else isContactOnline(entity.otherUsername),
                        isDisappearing = entity.isDisappearing,
                        isViewOnce = entity.isViewOnce,
                        isGroup = entity.isGroup,
                        groupIcon = entity.groupIcon,
                        admins = jsonArrayToList(entity.adminsJson),
                        members = jsonArrayToList(entity.membersJson),
                    )
                }
            }
        }

        // Observe custom stickers
        localStickersJob?.cancel()
        localStickersJob = scope.launch {
            mothDao.observeStickers(username).collect { entities ->
                customStickers = entities.mapNotNull { entity ->
                    if (File(entity.imageLocalPath).exists()) {
                        MothSticker(id = entity.id, label = entity.label, imageLocalPath = entity.imageLocalPath)
                    } else null
                }
            }
        }

        // Observe call logs
        localCallLogsJob?.cancel()
        localCallLogsJob = scope.launch {
            mothDao.observeCallLogs(username).collect { entities ->
                callLogs = entities.map { e ->
                    MothCallLog(
                        id = e.id,
                        peerUsername = e.peerUsername,
                        callType = try { CallType.valueOf(e.callType) } catch (_: Exception) { CallType.VOICE },
                        direction = try { CallDirection.valueOf(e.direction) } catch (_: Exception) { CallDirection.OUTGOING },
                        durationSec = e.durationSec,
                        timestamp = e.timestamp,
                        timeLabel = e.timeLabel,
                    )
                }
            }
        }

        // Background burning ticker
        burnMonitorJob?.cancel()
        burnMonitorJob = ioScope.launch {
            while (isActive && currentUsername == username) {
                try {
                    processBurningMessages(username)
                } catch (_: Throwable) {}
                delay(500L)
            }
        }

        // Presence heartbeat
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = ioScope.launch {
            while (isActive && currentUsername == username) {
                delay(15_000L)
                if (currentUsername != username) break
                if (isAppInForeground) {
                    publishMyPresence(username, true)
                }
                refreshPresenceStates()
            }
        }

        // Incoming WebRTC call listener
        incomingCallListener?.remove()
        incomingCallListener = db.collection("call_sessions")
            .whereEqualTo("callee", username)
            .whereEqualTo("status", "RINGING")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || currentUsername != username) return@addSnapshotListener
                val doc = snap.documents.firstOrNull()
                if (doc != null && activeCall == null) {
                    val callId = doc.id
                    val caller = doc.getString("caller")?.lowercase().orEmpty()
                    val callTypeStr = doc.getString("callType") ?: CallType.VOICE.name
                    val callType = try { CallType.valueOf(callTypeStr) } catch (_: Exception) { CallType.VOICE }
                    val offerSdp = doc.getString("offerSdp")
                    if (caller.isNotBlank() && !offerSdp.isNullOrBlank()) {
                        activeCall = ActiveCallState(
                            callId = callId,
                            peerUsername = caller,
                            callType = callType,
                            isCaller = false,
                            status = "RINGING",
                            offerSdp = offerSdp,
                        )
                        if (!isAppInForeground) {
                            showIncomingCallNotification(caller, callType)
                        }
                    }
                } else if (doc == null && activeCall?.isCaller == false && activeCall?.status == "RINGING") {
                    val missedCall = activeCall
                    activeCall = null
                    if (missedCall != null) {
                        recordCallLog(missedCall.peerUsername, missedCall.callType, CallDirection.MISSED, 0)
                    }
                }
            }

        // Incoming relay messages & control events (MESSAGE, REACTION, EDIT, REVOKE, PIN, RECEIPT)
        relayListener?.remove()
        relayListener = db.collection("relay_messages")
            .whereEqualTo("recipientUsername", username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val docId = doc.id
                    if (!processingRelayIds.add(docId)) continue
                    ioScope.launch {
                        try {
                            handleIncomingRelayDoc(username, doc)
                            doc.reference.delete().await()
                        } catch (_: Exception) {
                        } finally {
                            processingRelayIds.remove(docId)
                        }
                    }
                }
            }
    }

    private suspend fun handleIncomingRelayDoc(username: String, doc: DocumentSnapshot) {
        val recipient = doc.getString("recipientUsername")?.trim()?.lowercase().orEmpty()
        val sender = doc.getString("senderUsername")?.trim()?.lowercase().orEmpty()
        if (recipient != username || sender.isBlank()) return

        // Cache sender public key if included
        val senderPubKey = doc.getString("senderPublicKey")
        if (!senderPubKey.isNullOrBlank()) {
            crypto.cachePeerPublicKey(sender, senderPubKey)
        } else {
            ensurePeerPublicKey(sender)
        }

        val relayKind = doc.getString("relayKind") ?: "MESSAGE"
        val groupId = doc.getString("groupId")?.takeIf { it.isNotBlank() }

        when (relayKind) {
            "REACTION" -> {
                val targetMsgId = doc.getString("targetMsgId") ?: return
                val emoji = doc.getString("emoji") ?: return
                val add = doc.getBoolean("add") ?: true
                val localId = localMsgIdFor(username, targetMsgId)
                val msg = mothDao.getMessage(localId) ?: return
                val updatedMap = parseReactionsJson(msg.reactionsJson).toMutableMap()
                val users = updatedMap[emoji]?.toMutableList() ?: mutableListOf()
                if (add) {
                    if (!users.contains(sender)) users.add(sender)
                    updatedMap[emoji] = users
                } else {
                    users.remove(sender)
                    if (users.isEmpty()) updatedMap.remove(emoji) else updatedMap[emoji] = users
                }
                val (count, hasMine) = computeReactionSummary(updatedMap, username)
                mothDao.updateReactions(localId, count, hasMine, serializeReactionsJson(updatedMap))
                return
            }

            "EDIT" -> {
                val targetMsgId = doc.getString("targetMsgId") ?: return
                val newText = decryptRelayText(doc, sender, groupId)
                val localId = localMsgIdFor(username, targetMsgId)
                mothDao.updateMessageEdited(localId, newText)
                return
            }

            "REVOKE" -> {
                val targetMsgId = doc.getString("targetMsgId") ?: return
                val localId = localMsgIdFor(username, targetMsgId)
                val existing = mothDao.getMessage(localId)
                if (!existing?.attachmentLocalPath.isNullOrBlank()) {
                    try {
                        val f = File(existing!!.attachmentLocalPath!!)
                        if (f.exists() && f.absolutePath.startsWith(mediaDir.absolutePath)) f.delete()
                    } catch (_: Exception) {}
                }
                mothDao.markMessageDeleted(localId)
                return
            }

            "PIN" -> {
                val targetMsgId = doc.getString("targetMsgId") ?: return
                val pinned = doc.getBoolean("pinned") ?: true
                mothDao.updateMessagePinned(localMsgIdFor(username, targetMsgId), pinned)
                return
            }

            "RECEIPT" -> {
                val statusStr = doc.getString("receiptStatus") ?: return
                val receiptAt = doc.getLong("receiptAt") ?: System.currentTimeMillis()
                val ids = (doc.get("targetMsgIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val newStatus = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { return }
                for (targetId in ids) {
                    val localId = localMsgIdFor(username, targetId)
                    val existing = mothDao.getMessage(localId) ?: continue
                    val currentRank = statusRank(existing.status)
                    val targetRank = statusRank(newStatus.name)
                    if (targetRank > currentRank) {
                        mothDao.updateMessageStatus(
                            localId = localId,
                            status = newStatus.name,
                            deliveredAt = if (newStatus == MessageStatus.DELIVERED) receiptAt else 0L,
                            readAt = if (newStatus == MessageStatus.READ) receiptAt else 0L,
                        )
                    }
                }
                return
            }
        }

        // Standard MESSAGE delivery
        val msgId = doc.getString("id") ?: doc.id
        val text = decryptRelayText(doc, sender, groupId)
        val typeStr = doc.getString("type") ?: MessageType.REGULAR.name
        val msgType = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.REGULAR }
        val leaveTrace = doc.getBoolean("leaveTrace") ?: false
        val timeStr = doc.getString("time") ?: currentTimeLabel()
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val receivedAt = System.currentTimeMillis()
        val attachmentType = doc.getString("attachmentType")
        val attachmentName = doc.getString("attachmentName")
        val attachmentCipherB64 = doc.getString("attachmentCiphertext")
        val attachmentPlainB64 = doc.getString("attachmentBase64")
        val attachmentMime = doc.getString("attachmentMimeType")
        val attachmentSize = doc.getLong("attachmentSizeBytes") ?: 0L
        val audioDurationSec = (doc.getLong("audioDurationSec") ?: 0L).toInt()
        val replyToId = doc.getString("replyToId")
        val replyToSender = doc.getString("replyToSender")
        val replyToPreview = doc.getString("replyToPreview")
        val isForwarded = doc.getBoolean("isForwarded") ?: false

        var savedLocalPath: String? = null
        val decodedAttBytes: ByteArray? = when {
            !attachmentCipherB64.isNullOrBlank() -> decryptRelayBytes(attachmentCipherB64, sender, groupId)
            !attachmentPlainB64.isNullOrBlank() -> Base64.decode(attachmentPlainB64, Base64.NO_WRAP)
            else -> null
        }
        if (decodedAttBytes != null) {
            val safeName = sanitizeFilename(attachmentName ?: "attachment")
            val targetFile = File(mediaDir, "${msgId}_$safeName")
            targetFile.writeBytes(decodedAttBytes)
            savedLocalPath = targetFile.absolutePath
        }

        val chatId = if (groupId != null) chatIdFor(username, groupId) else chatIdFor(username, sender)
        val localMsgId = localMsgIdFor(username, msgId)

        if (groupId != null) {
            ensureLocalGroupChatFromCloud(username, groupId)
        }

        val existingMsg = mothDao.getMessage(localMsgId)
        if (existingMsg == null) {
            val skew = receivedAt - timestamp
            val burnStart = if (msgType == MessageType.DISAPPEARING) {
                if (skew in 0L..8_000L) timestamp else receivedAt
            } else 0L

            mothDao.upsertMessage(
                MothMessageEntity(
                    localId = localMsgId,
                    id = msgId,
                    ownerUsername = username,
                    chatId = chatId,
                    senderUsername = sender,
                    text = text,
                    type = msgType.name,
                    viewOnceStatus = ViewOnceStatus.UNOPENED.name,
                    embersRemaining = 10,
                    leaveTrace = leaveTrace,
                    burnStartAt = burnStart,
                    time = timeStr,
                    timestamp = timestamp,
                    attachmentType = attachmentType,
                    attachmentName = attachmentName,
                    attachmentLocalPath = savedLocalPath,
                    attachmentMimeType = attachmentMime,
                    attachmentSizeBytes = attachmentSize,
                    audioDurationSec = audioDurationSec,
                    replyToId = replyToId,
                    replyToSender = replyToSender,
                    replyToPreview = replyToPreview,
                    isForwarded = isForwarded,
                    status = MessageStatus.DELIVERED.name,
                    sentAt = timestamp,
                    deliveredAt = receivedAt,
                )
            )

            val existingChat = mothDao.getChat(chatId)
            val isCurrentlyOpen = activeChatId == chatId && isAppInForeground
            val preview = buildPreviewText(
                text = text,
                type = msgType,
                attachmentType = attachmentType,
                attachmentName = attachmentName,
                senderPrefix = if (groupId != null) "$sender: " else "",
            )
            val updatedChat = (existingChat ?: MothChatEntity(
                id = chatId,
                ownerUsername = username,
                otherUsername = groupId ?: sender,
                preview = preview,
                time = timeStr,
                avatarColorArgb = avatarColorForUsername(groupId ?: sender).toArgb().toLong(),
                isGroup = groupId != null,
                updatedAt = timestamp,
            )).copy(
                preview = preview,
                time = timeStr,
                unreadCount = if (isCurrentlyOpen) 0 else (existingChat?.unreadCount ?: 0) + 1,
                isLit = true,
                isDisappearing = msgType == MessageType.DISAPPEARING,
                isViewOnce = msgType == MessageType.VIEW_ONCE,
                updatedAt = timestamp,
            )
            mothDao.upsertChat(updatedChat)
            syncChatToCloud(updatedChat)

            if (groupId == null && existingChat?.avatarLocalPath == null) {
                syncContactAvatarInBackground(username, sender, chatId)
            }

            // Send DELIVERED receipt (or READ if currently open & read receipts enabled)
            if (groupId == null && sender != "support") {
                val receiptStatus = if (isCurrentlyOpen && !disableReadReceipts) {
                    MessageStatus.READ
                } else {
                    MessageStatus.DELIVERED
                }
                sendReceiptRelay(username, sender, listOf(msgId), receiptStatus)
            }

            if (!isCurrentlyOpen) {
                val notifTitle = if (groupId != null) "${existingChat?.otherUsername ?: "group"} • $sender" else sender
                val notifText = buildNotificationBody(text, msgType, attachmentType, attachmentName)
                showIncomingMessageNotification(notifTitle, notifText, groupId ?: sender)
            }
        }
    }

    // -------------------------------------------------------------------------
    // E2EE Helpers
    // -------------------------------------------------------------------------

    private suspend fun ensurePeerPublicKey(peerUsername: String): Boolean {
        val clean = peerUsername.trim().lowercase()
        if (clean.isBlank() || clean == "support") return false
        if (crypto.hasPeerPublicKey(clean)) return true
        return try {
            val doc = db.collection("users").document(clean).get().await()
            val pub = doc.getString("e2eePublicKey")
            if (!pub.isNullOrBlank()) {
                crypto.cachePeerPublicKey(clean, pub)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getOrFetchGroupKey(groupId: String, memberList: List<String>): ByteArray? {
        groupKeysCache[groupId]?.let { return it }
        val me = currentUsername
        if (me.isBlank()) return null
        return try {
            val doc = db.collection("groups").document(groupId).get().await()
            val keyMap = doc.get("memberKeys") as? Map<*, *>
            val keyEntry = keyMap?.get(me) as? Map<*, *>
            val cipherB64 = keyEntry?.get("cipher") as? String
            val keySender = (keyEntry?.get("from") as? String)?.lowercase()
            if (!cipherB64.isNullOrBlank() && !keySender.isNullOrBlank()) {
                if (keySender == me || ensurePeerPublicKey(keySender)) {
                    val raw = crypto.decrypt(keySender, Base64.decode(cipherB64, Base64.NO_WRAP))
                    groupKeysCache[groupId] = raw
                    return raw
                }
            }
            // Generate new group key if absent
            val newKey = crypto.generateGroupKey()
            groupKeysCache[groupId] = newKey
            distributeGroupKey(groupId, newKey, memberList)
            newKey
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun distributeGroupKey(groupId: String, groupKey: ByteArray, members: List<String>) {
        val me = currentUsername
        if (me.isBlank()) return
        val wrapped = mutableMapOf<String, Map<String, String>>()
        for (member in members.distinct()) {
            if (member == me || ensurePeerPublicKey(member)) {
                try {
                    val c = crypto.encrypt(member, groupKey)
                    wrapped[member] = mapOf(
                        "from" to me,
                        "cipher" to Base64.encodeToString(c, Base64.NO_WRAP),
                    )
                } catch (_: Exception) {}
            }
        }
        if (wrapped.isNotEmpty()) {
            try {
                db.collection("groups").document(groupId)
                    .set(mapOf("memberKeys" to wrapped), SetOptions.merge()).await()
            } catch (_: Exception) {}
        }
    }

    private suspend fun decryptRelayText(doc: DocumentSnapshot, sender: String, groupId: String?): String {
        val isE2ee = doc.getBoolean("e2ee") == true
        val cipherB64 = doc.getString("ciphertext")
        if (isE2ee && !cipherB64.isNullOrBlank()) {
            val decoded = decryptRelayBytes(cipherB64, sender, groupId)
            if (decoded != null) return String(decoded, Charsets.UTF_8)
        }
        return doc.getString("text") ?: ""
    }

    private suspend fun decryptRelayBytes(cipherB64: String, sender: String, groupId: String?): ByteArray? {
        return try {
            val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
            if (groupId != null) {
                val gk = getOrFetchGroupKey(groupId, emptyList()) ?: return null
                crypto.decryptWithGroupKey(gk, cipherBytes)
            } else {
                ensurePeerPublicKey(sender)
                crypto.decrypt(sender, cipherBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Contact Avatars, Notifications & Burning Ticker
    // -------------------------------------------------------------------------

    private fun syncContactAvatarInBackground(ownerUsername: String, otherUsername: String, chatId: String) {
        ioScope.launch {
            try {
                val userDoc = db.collection("users").document(otherUsername).get().await()
                if (!userDoc.exists()) return@launch
                userDoc.getString("e2eePublicKey")?.let { crypto.cachePeerPublicKey(otherUsername, it) }
                val base64 = userDoc.getString("avatarBase64")
                val colorLong = userDoc.getLong("avatarColorArgb")
                val existingChat = mothDao.getChat(chatId) ?: return@launch

                val contactFile = File(mediaDir, "contact_avatar_${otherUsername}.jpg")
                val newAvatarPath = if (!base64.isNullOrBlank()) {
                    contactFile.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
                    contactFile.absolutePath
                } else {
                    if (contactFile.exists()) contactFile.delete()
                    null
                }

                val newColor = colorLong ?: existingChat.avatarColorArgb
                if (newAvatarPath != existingChat.avatarLocalPath || newColor != existingChat.avatarColorArgb) {
                    mothDao.upsertChat(
                        existingChat.copy(avatarLocalPath = newAvatarPath, avatarColorArgb = newColor)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun showIncomingMessageNotification(title: String, body: String, openTarget: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureNotificationChannel()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_user", openTarget)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            openTarget.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moth_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(openTarget.hashCode(), notification)
    }

    private fun showIncomingCallNotification(caller: String, callType: CallType) {
        showIncomingMessageNotification("@$caller", "incoming voice call", caller)
    }

    private suspend fun processBurningMessages(ownerUsername: String) = burnMutex.withLock {
        val disappearing = mothDao.getDisappearingMessages(ownerUsername)
        if (disappearing.isEmpty()) return@withLock
        val now = System.currentTimeMillis()

        for (msg in disappearing) {
            val startAt = if (msg.burnStartAt > 0L) msg.burnStartAt else msg.timestamp
            val elapsed = (now - startAt).coerceAtLeast(0L)

            if (elapsed >= BURN_DURATION_MS) {
                if (!msg.attachmentLocalPath.isNullOrBlank()) {
                    try {
                        val file = File(msg.attachmentLocalPath)
                        if (file.exists() && file.absolutePath.startsWith(mediaDir.absolutePath)) {
                            file.delete()
                        }
                    } catch (_: Exception) {}
                }

                if (msg.leaveTrace) {
                    val isSender = msg.senderUsername.equals(ownerUsername, ignoreCase = true)
                    mothDao.upsertMessage(
                        msg.copy(
                            type = MessageType.BURNED_TRACE.name,
                            text = if (isSender) msg.text else "",
                            embersRemaining = 0,
                            attachmentType = null,
                            attachmentName = null,
                            attachmentLocalPath = null,
                            attachmentMimeType = null,
                            attachmentSizeBytes = 0L,
                        )
                    )
                    val chat = mothDao.getChat(msg.chatId)
                    val latest = mothDao.getLatestMessage(ownerUsername, msg.chatId)
                    if (chat != null && latest?.localId == msg.localId) {
                        val updatedChat = chat.copy(preview = "✦ message burned", isDisappearing = false)
                        mothDao.upsertChat(updatedChat)
                        ioScope.launch { syncChatToCloud(updatedChat) }
                    }
                } else {
                    mothDao.deleteMessage(msg.localId)
                    val chat = mothDao.getChat(msg.chatId)
                    if (chat != null) {
                        val latest = mothDao.getLatestMessage(ownerUsername, msg.chatId)
                        val updatedChat = if (latest == null) {
                            chat.copy(preview = "lantern lit", isDisappearing = false, isViewOnce = false)
                        } else {
                            val latestType = try { MessageType.valueOf(latest.type) } catch (_: Exception) { MessageType.REGULAR }
                            chat.copy(
                                preview = buildPreviewText(latest.text, latestType, latest.attachmentType, latest.attachmentName),
                                time = latest.time,
                                isDisappearing = latestType == MessageType.DISAPPEARING,
                                isViewOnce = latestType == MessageType.VIEW_ONCE,
                            )
                        }
                        mothDao.upsertChat(updatedChat)
                        ioScope.launch { syncChatToCloud(updatedChat) }
                    }
                }
            } else {
                val newEmbers = (10 - (elapsed / BURN_STEP_MS).toInt()).coerceIn(1, 10)
                if (newEmbers != msg.embersRemaining) {
                    mothDao.updateEmbers(msg.localId, newEmbers)
                }
            }
        }
    }

    private suspend fun syncChatToCloud(chat: MothChatEntity) {
        try {
            if (chat.ownerUsername.isBlank() || chat.otherUsername.isBlank()) return
            val docKey = if (chat.isGroup) chat.id.substringAfter("::") else chat.otherUsername
            val payload = hashMapOf<String, Any>(
                "id" to chat.id,
                "ownerUsername" to chat.ownerUsername,
                "otherUsername" to chat.otherUsername,
                "preview" to chat.preview,
                "time" to chat.time,
                "avatarColorArgb" to chat.avatarColorArgb,
                "unreadCount" to chat.unreadCount,
                "isLit" to chat.isLit,
                "isDisappearing" to chat.isDisappearing,
                "isViewOnce" to chat.isViewOnce,
                "updatedAt" to chat.updatedAt,
                "isGroup" to chat.isGroup,
                "groupIcon" to chat.groupIcon,
                "adminsJson" to chat.adminsJson,
                "membersJson" to chat.membersJson,
            )
            if (chat.isGroup) payload["groupId"] = docKey
            db.collection("users")
                .document(chat.ownerUsername)
                .collection("chats")
                .document(docKey)
                .set(payload, SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    private suspend fun ensureLocalSupportChatExists(username: String) {
        val chatId = chatIdFor(username, "support")
        val existing = mothDao.getChat(chatId)
        if (existing == null) {
            val now = System.currentTimeMillis()
            val timeStr = currentTimeLabel()
            val supportChat = MothChatEntity(
                id = chatId,
                ownerUsername = username,
                otherUsername = "support",
                preview = "how can we help your lantern tonight?",
                time = timeStr,
                avatarColorArgb = Color(0xFFFFB43B).toArgb().toLong(),
                unreadCount = 1,
                isLit = true,
                updatedAt = now,
            )
            mothDao.upsertChat(supportChat)
            syncChatToCloud(supportChat)
            mothDao.upsertMessage(
                MothMessageEntity(
                    localId = localMsgIdFor(username, "sup_1"),
                    id = "sup_1",
                    ownerUsername = username,
                    chatId = chatId,
                    senderUsername = "support",
                    text = "welcome to moth hours. type !!!text!!! for no-trace burn or !!!!text!!!! to leave ash.",
                    type = MessageType.REGULAR.name,
                    time = timeStr,
                    timestamp = now - 2000L,
                    status = MessageStatus.READ.name,
                )
            )
            mothDao.upsertMessage(
                MothMessageEntity(
                    localId = localMsgIdFor(username, "sup_2"),
                    id = "sup_2",
                    ownerUsername = username,
                    chatId = chatId,
                    senderUsername = "support",
                    text = "how can we help your lantern tonight?",
                    type = MessageType.REGULAR.name,
                    time = timeStr,
                    timestamp = now - 1000L,
                    status = MessageStatus.READ.name,
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Conversation Opening, Presence & Typing
    // -------------------------------------------------------------------------

    fun getActiveConversation(): MothConversation? {
        return conversations.firstOrNull { it.id == activeChatId }
    }

    fun openConversation(idOrName: String) {
        val owner = currentUsername
        if (owner.isBlank()) return

        val target = conversations.firstOrNull {
            it.id == idOrName || it.name.equals(idOrName, ignoreCase = true)
        }
        val otherUser = target?.name?.lowercase()
            ?: idOrName.substringAfter("::", idOrName).trim().lowercase()
        val chatId = target?.id ?: chatIdFor(owner, otherUser)

        activeChatId = chatId
        isContactTyping = false
        lastTypingSentAt = 0L
        lastTypingSentState = false

        startObservingLocalMessages(owner, chatId)

        if (target?.isGroup == true || otherUser.startsWith("grp_")) {
            activeContactPresence = ContactPresence(isOnline = true, label = "${target?.members?.size ?: 1} members")
            typingListener?.remove()
            presenceListener?.remove()
            startObservingGroupMetadata(owner, otherUser, chatId)
        } else {
            groupMetaListener?.remove()
            startObservingContactTypingAndPresence(owner, otherUser)
            if (otherUser != "support") {
                syncContactAvatarInBackground(owner, otherUser, chatId)
            }
        }

        ioScope.launch {
            processBurningMessages(owner)
            mothDao.markChatRead(chatId)
            mothDao.getChat(chatId)?.let { syncChatToCloud(it.copy(unreadCount = 0)) }
            markIncomingMessagesAsRead(owner, chatId, otherUser, target?.isGroup == true)
        }
    }

    private suspend fun markIncomingMessagesAsRead(owner: String, chatId: String, otherUser: String, isGroup: Boolean) {
        if (isGroup || otherUser == "support" || disableReadReceipts) return
        val msgs = mothDao.getMessagesByChatOnce(owner, chatId)
        val unreadIncoming = msgs.filter {
            !it.senderUsername.equals(owner, ignoreCase = true) && it.status != MessageStatus.READ.name
        }
        if (unreadIncoming.isEmpty()) return
        val now = System.currentTimeMillis()
        unreadIncoming.forEach { m ->
            mothDao.updateMessageStatus(m.localId, MessageStatus.READ.name, readAt = now)
        }
        sendReceiptRelay(owner, otherUser, unreadIncoming.map { it.id }, MessageStatus.READ)
    }

    private suspend fun sendReceiptRelay(sender: String, recipient: String, targetMsgIds: List<String>, status: MessageStatus) {
        if (targetMsgIds.isEmpty() || recipient == "support") return
        try {
            val docId = "rcpt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
            db.collection("relay_messages").document(docId).set(
                mapOf(
                    "relayKind" to "RECEIPT",
                    "senderUsername" to sender,
                    "recipientUsername" to recipient,
                    "targetMsgIds" to targetMsgIds,
                    "receiptStatus" to status.name,
                    "receiptAt" to System.currentTimeMillis(),
                )
            ).await()
        } catch (_: Exception) {}
    }

    fun closeActiveConversation() {
        val oldChatId = activeChatId
        if (oldChatId != null) {
            updateMyTypingStatus(oldChatId, false)
        }
        voiceRecorder.cancelRecording()
        voicePlayer.stop()
        activeChatId = null
        localMessagesJob?.cancel(); localMessagesJob = null
        typingTimeoutJob?.cancel(); typingTimeoutJob = null
        typingListener?.remove(); typingListener = null
        presenceListener?.remove(); presenceListener = null
        groupMetaListener?.remove(); groupMetaListener = null
        isContactTyping = false
        activeContactPresence = ContactPresence()
    }

    private fun evaluatePresence(snap: DocumentSnapshot?): ContactPresence {
        if (snap == null || !snap.exists()) {
            return ContactPresence(isOnline = false, lastSeen = 0L, label = "offline")
        }
        val online = snap.getBoolean("isOnline") == true
        val serverSeenMs = snap.getTimestamp("serverSeenAt")?.toDate()?.time
        val clientLastSeen = snap.getLong("lastSeen") ?: 0L
        val referenceMs = serverSeenMs ?: clientLastSeen
        val nowRef = if (serverSeenMs != null) serverNow() else System.currentTimeMillis()
        val fresh = online && (referenceMs == 0L || (nowRef - referenceMs) < 75_000L)
        val displaySeen = serverSeenMs ?: clientLastSeen
        val label = when {
            fresh -> "active"
            displaySeen > 0L -> "last seen ${formatLastSeen(displaySeen)}"
            else -> "offline"
        }
        return ContactPresence(isOnline = fresh, lastSeen = displaySeen, label = label)
    }

    private fun syncListPresenceListeners(otherUsers: List<String>) {
        val activeSet = otherUsers.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != "support" }
            .toSet()

        val toRemove = listPresenceListeners.keys.filter { it !in activeSet }
        for (user in toRemove) {
            listPresenceListeners.remove(user)?.remove()
            contactPresenceSnapshots.remove(user)
            contactOnlineMap.remove(user)
        }

        for (user in activeSet) {
            if (!listPresenceListeners.containsKey(user)) {
                val reg = db.collection("presence").document(user)
                    .addSnapshotListener { snap, error ->
                        if (error != null) return@addSnapshotListener
                        if (snap != null) contactPresenceSnapshots[user] = snap else contactPresenceSnapshots.remove(user)
                        contactOnlineMap[user] = evaluatePresence(snap).isOnline
                    }
                listPresenceListeners[user] = reg
            }
        }
    }

    private fun refreshPresenceStates() {
        for ((user, snap) in contactPresenceSnapshots) {
            val evaluated = evaluatePresence(snap)
            if (contactOnlineMap[user] != evaluated.isOnline) {
                contactOnlineMap[user] = evaluated.isOnline
            }
        }
        val activeId = activeChatId ?: return
        val conv = conversations.firstOrNull { it.id == activeId }
        if (conv?.isGroup == true) return
        val other = conv?.name?.lowercase() ?: activeId.substringAfter("::", "").lowercase()
        if (other.isNotBlank() && other != "support") {
            contactPresenceSnapshots[other]?.let { snap ->
                activeContactPresence = evaluatePresence(snap)
            }
        }
    }

    private fun startObservingContactTypingAndPresence(owner: String, otherUser: String) {
        typingListener?.remove()
        presenceListener?.remove()
        typingTimeoutJob?.cancel()

        if (otherUser == "support") {
            activeContactPresence = ContactPresence(isOnline = true, lastSeen = System.currentTimeMillis(), label = "active")
            return
        }

        activeContactPresence = contactPresenceSnapshots[otherUser]?.let { evaluatePresence(it) }
            ?: ContactPresence(label = "offline")

        val typingDocId = "${otherUser}_to_${owner}"
        typingListener = db.collection("typing_status").document(typingDocId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) {
                    isContactTyping = false
                    return@addSnapshotListener
                }
                val isTyping = snap.getBoolean("isTyping") == true
                val updatedAt = snap.getLong("updatedAt") ?: 0L
                val ageMs = System.currentTimeMillis() - updatedAt
                if (isTyping && ageMs < 9_000L) {
                    isContactTyping = true
                    typingTimeoutJob?.cancel()
                    typingTimeoutJob = scope.launch {
                        delay(9_000L - ageMs.coerceAtLeast(0L))
                        isContactTyping = false
                    }
                } else {
                    typingTimeoutJob?.cancel()
                    isContactTyping = false
                }
            }

        presenceListener = db.collection("presence").document(otherUser)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                if (snap != null) contactPresenceSnapshots[otherUser] = snap
                val evaluated = evaluatePresence(snap)
                activeContactPresence = evaluated
                contactOnlineMap[otherUser] = evaluated.isOnline
            }
    }

    fun updateMyTypingStatus(chatId: String, isTyping: Boolean) {
        val owner = currentUsername
        if (owner.isBlank()) return
        val conv = conversations.firstOrNull { it.id == chatId }
        if (conv?.isGroup == true) return
        val otherUser = conv?.name?.lowercase() ?: chatId.substringAfter("::", "").lowercase()
        if (otherUser.isBlank() || otherUser == "support") return

        val effectiveTyping = if (hideTypingIndicators) false else isTyping
        val now = System.currentTimeMillis()
        if (effectiveTyping && lastTypingSentState && (now - lastTypingSentAt) < 1800L) return
        if (!effectiveTyping && !lastTypingSentState) return
        lastTypingSentState = effectiveTyping
        lastTypingSentAt = now

        val docId = "${owner}_to_${otherUser}"
        ioScope.launch {
            try {
                val ref = db.collection("typing_status").document(docId)
                if (effectiveTyping) {
                    ref.set(
                        mapOf(
                            "senderUsername" to owner,
                            "isTyping" to true,
                            "updatedAt" to now,
                        )
                    ).await()
                } else {
                    ref.delete().await()
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun cleanupStaleTypingDocs(username: String) {
        try {
            val snap = db.collection("typing_status")
                .whereEqualTo("senderUsername", username)
                .get()
                .await()
            for (doc in snap.documents) doc.reference.delete().await()
        } catch (_: Exception) {}
    }

    private suspend fun cleanupOrphanedLocalFiles(username: String) {
        try {
            val referenced = buildSet {
                addAll(mothDao.getAllAttachmentPaths())
                addAll(mothDao.getAllChatAvatarPaths())
                addAll(mothDao.getAllStickerPaths())
                userAvatarPath?.let { add(it) }
            }
            mediaDir.listFiles()?.forEach { file ->
                val name = file.name
                val belongs = name.startsWith("sent_") ||
                    name.startsWith("msg_") ||
                    name.startsWith("voice_") ||
                    name.startsWith("cam_") ||
                    name.startsWith("my_avatar_${username}")
                if (belongs && file.absolutePath !in referenced) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun fetchUserProfile(username: String): UserProfileDetails = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase()
        val owner = currentUsername
        val chatId = chatIdFor(owner, clean)
        val localChat = mothDao.getChat(chatId)
        val msgCount = mothDao.getChatMessageCount(owner, chatId)

        if (clean == "support") {
            return@withContext UserProfileDetails(
                username = "support",
                avatarPath = null,
                avatarColor = Color(0xFFFFB43B),
                isOnline = true,
                statusLabel = "active",
                joinedLabel = "system lantern",
                localMessageCount = msgCount,
            )
        }

        var avatarPath = localChat?.avatarLocalPath?.takeIf { File(it).exists() }
        var avatarColor = Color((localChat?.avatarColorArgb ?: avatarColorForUsername(clean).toArgb().toLong()).toInt())
        var joinedLabel = "unknown"
        var isOnline = false
        var statusLabel = "offline"

        try {
            val userDoc = db.collection("users").document(clean).get().await()
            if (userDoc.exists()) {
                userDoc.getLong("avatarColorArgb")?.let { avatarColor = Color(it.toInt()) }
                val base64 = userDoc.getString("avatarBase64")
                val file = File(mediaDir, "contact_avatar_${clean}.jpg")
                if (!base64.isNullOrBlank()) {
                    file.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
                    avatarPath = file.absolutePath
                    mothDao.updateChatAvatarPath(chatId, avatarPath)
                } else {
                    if (file.exists()) file.delete()
                    avatarPath = null
                    mothDao.updateChatAvatarPath(chatId, null)
                }
                userDoc.getTimestamp("createdAt")?.toDate()?.let { created ->
                    joinedLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(created).lowercase()
                }
            }

            val presenceDoc = db.collection("presence").document(clean).get().await()
            val evaluated = evaluatePresence(presenceDoc)
            isOnline = evaluated.isOnline
            statusLabel = evaluated.label
            contactOnlineMap[clean] = evaluated.isOnline
        } catch (_: Exception) {}

        UserProfileDetails(
            username = clean,
            avatarPath = avatarPath,
            avatarColor = avatarColor,
            isOnline = isOnline,
            statusLabel = statusLabel,
            joinedLabel = joinedLabel,
            localMessageCount = msgCount,
        )
    }

    fun markAllAsRead() {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            mothDao.markAllChatsRead(owner)
            conversations.forEach { conv ->
                mothDao.getChat(conv.id)?.let { syncChatToCloud(it.copy(unreadCount = 0)) }
            }
        }
    }

    private fun startObservingLocalMessages(ownerUsername: String, chatId: String) {
        localMessagesJob?.cancel()
        localMessagesJob = scope.launch {
            mothDao.observeMessages(ownerUsername, chatId).collect { entities ->
                activeMessages = entities.map { entity ->
                    val type = try { MessageType.valueOf(entity.type) } catch (_: Exception) { MessageType.REGULAR }
                    val voStatus = try { ViewOnceStatus.valueOf(entity.viewOnceStatus) } catch (_: Exception) { ViewOnceStatus.UNOPENED }
                    val attType = entity.attachmentType?.let {
                        try { AttachmentType.valueOf(it) } catch (_: Exception) { null }
                    }
                    val statusEnum = try { MessageStatus.valueOf(entity.status) } catch (_: Exception) { MessageStatus.SENT }
                    val reactionsMap = parseReactionsJson(entity.reactionsJson)
                    val (count, hasMine) = if (reactionsMap.isNotEmpty()) {
                        computeReactionSummary(reactionsMap, ownerUsername)
                    } else {
                        entity.reactionCount to entity.hasReacted
                    }

                    MothMessage(
                        id = entity.id,
                        senderUsername = entity.senderUsername,
                        text = entity.text,
                        isMine = entity.senderUsername.equals(ownerUsername, ignoreCase = true),
                        type = type,
                        reactionCount = count,
                        hasReacted = hasMine,
                        reactions = reactionsMap,
                        viewOnceStatus = voStatus,
                        embersRemaining = entity.embersRemaining,
                        leaveTrace = entity.leaveTrace,
                        burnStartAt = entity.burnStartAt,
                        time = entity.time,
                        attachmentType = attType,
                        attachmentName = entity.attachmentName,
                        attachmentLocalPath = entity.attachmentLocalPath,
                        attachmentMimeType = entity.attachmentMimeType,
                        attachmentSizeBytes = entity.attachmentSizeBytes,
                        audioDurationSec = entity.audioDurationSec,
                        replyToId = entity.replyToId,
                        replyToSender = entity.replyToSender,
                        replyToPreview = entity.replyToPreview,
                        isForwarded = entity.isForwarded,
                        isEdited = entity.isEdited,
                        isDeleted = entity.isDeleted,
                        isPinned = entity.isPinned,
                        status = statusEnum,
                        sentAt = entity.sentAt,
                        deliveredAt = entity.deliveredAt,
                        readAt = entity.readAt,
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Media, Camera, Voice Note & Sticker Preparation
    // -------------------------------------------------------------------------

    fun createTempCameraPhotoUri(): Uri {
        val file = File(mediaDir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    suspend fun prepareVoiceAttachment(audioFile: File, durationSec: Int): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists()) return@withContext Result.failure(IllegalArgumentException("Audio file missing"))
            val bytes = audioFile.readBytes()
            if (bytes.size > MAX_RELAY_ATTACHMENT_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Voice note too large for relay"))
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success(
                PendingAttachment(
                    type = AttachmentType.VOICE,
                    name = audioFile.name,
                    localPath = audioFile.absolutePath,
                    mimeType = "audio/mp4",
                    sizeBytes = bytes.size.toLong(),
                    base64Data = base64,
                    audioDurationSec = durationSec,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun preparePhotoAttachment(uri: Uri): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        try {
            val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            val rawName = queryDisplayName(uri) ?: "photo_${System.currentTimeMillis()}.jpg"
            val fileName = sanitizeFilename(
                if (rawName.endsWith(".jpg", true) || rawName.endsWith(".jpeg", true) || rawName.endsWith(".png", true))
                    rawName else "$rawName.jpg"
            )

            val originalBitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read image"))

            val maxDim = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDim || height > maxDim) {
                minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
            } else 1f

            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else originalBitmap

            val outStream = ByteArrayOutputStream()
            var quality = 75
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            while (outStream.size() > MAX_RELAY_ATTACHMENT_BYTES && quality > 30) {
                outStream.reset()
                quality -= 15
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            }

            if (scaledBitmap !== originalBitmap) scaledBitmap.recycle()
            originalBitmap.recycle()

            val bytes = outStream.toByteArray()
            if (bytes.size > MAX_RELAY_ATTACHMENT_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Image exceeds 600 KB relay limit"))
            }

            val destFile = File(mediaDir, "sent_${System.currentTimeMillis()}_$fileName")
            destFile.writeBytes(bytes)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            Result.success(
                PendingAttachment(
                    type = AttachmentType.PHOTO,
                    name = fileName,
                    localPath = destFile.absolutePath,
                    mimeType = if (mime.startsWith("image/")) "image/jpeg" else mime,
                    sizeBytes = bytes.size.toLong(),
                    base64Data = base64,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prepareFileAttachment(uri: Uri): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        try {
            val mime = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
            val rawName = queryDisplayName(uri) ?: "attachment_${System.currentTimeMillis()}"
            val fileName = sanitizeFilename(rawName)

            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to read file"))

            if (bytes.size > MAX_RELAY_ATTACHMENT_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("File too large (${bytes.size / 1024} KB). Max 600 KB for relay.")
                )
            }

            val destFile = File(mediaDir, "sent_${System.currentTimeMillis()}_$fileName")
            destFile.writeBytes(bytes)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            Result.success(
                PendingAttachment(
                    type = AttachmentType.FILE,
                    name = fileName,
                    localPath = destFile.absolutePath,
                    mimeType = mime,
                    sizeBytes = bytes.size.toLong(),
                    base64Data = base64,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPixelSticker(
        label: String,
        pixelColors: List<Int>,
        gridSize: Int = 10,
    ): Result<MothSticker> = withContext(Dispatchers.IO) {
        val owner = currentUsername
        if (owner.isBlank()) return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val sizePx = 240
            val cellPx = sizePx / gridSize
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val idx = row * gridSize + col
                    val argb = pixelColors.getOrElse(idx) { 0 }
                    if (argb != 0) {
                        paint.color = argb
                        canvas.drawRect(
                            (col * cellPx).toFloat(),
                            (row * cellPx).toFloat(),
                            ((col + 1) * cellPx).toFloat(),
                            ((row + 1) * cellPx).toFloat(),
                            paint,
                        )
                    }
                }
            }

            val id = "stk_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            val cleanLabel = label.trim().ifBlank { "pixel sticker" }.take(24)
            val file = File(mediaDir, "${id}.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()

            val entity = MothStickerEntity(
                id = id,
                ownerUsername = owner,
                label = cleanLabel,
                imageLocalPath = file.absolutePath,
            )
            mothDao.upsertSticker(entity)
            val bytes = file.readBytes()
            ioScope.launch { syncStickerToCloud(entity, bytes) }
            Result.success(MothSticker(id = id, label = cleanLabel, imageLocalPath = file.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPhotoSticker(uri: Uri): Result<MothSticker> = withContext(Dispatchers.IO) {
        val owner = currentUsername
        if (owner.isBlank()) return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val original = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read image"))

            val side = minOf(original.width, original.height)
            val xOff = (original.width - side) / 2
            val yOff = (original.height - side) / 2
            val cropped = Bitmap.createBitmap(original, xOff, yOff, side, side)
            val scaled = if (side > 320) Bitmap.createScaledBitmap(cropped, 320, 320, true) else cropped

            val outStream = ByteArrayOutputStream()
            var quality = 85
            scaled.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            if (outStream.size() > 240 * 1024) {
                outStream.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                while (outStream.size() > 240 * 1024 && quality > 35) {
                    outStream.reset()
                    quality -= 15
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                }
            }

            if (scaled !== cropped) scaled.recycle()
            if (cropped !== original) cropped.recycle()
            original.recycle()

            val rawName = queryDisplayName(uri) ?: "sticker"
            val cleanLabel = rawName.substringBeforeLast('.').take(24).ifBlank { "sticker" }
            val id = "stk_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            val file = File(mediaDir, "${id}.png")
            val bytes = outStream.toByteArray()
            file.writeBytes(bytes)

            val entity = MothStickerEntity(
                id = id,
                ownerUsername = owner,
                label = cleanLabel,
                imageLocalPath = file.absolutePath,
            )
            mothDao.upsertSticker(entity)
            ioScope.launch { syncStickerToCloud(entity, bytes) }
            Result.success(MothSticker(id = id, label = cleanLabel, imageLocalPath = file.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncStickerToCloud(sticker: MothStickerEntity, bytes: ByteArray) {
        try {
            if (sticker.ownerUsername.isBlank()) return
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            db.collection("users")
                .document(sticker.ownerUsername)
                .collection("stickers")
                .document(sticker.id)
                .set(
                    mapOf(
                        "id" to sticker.id,
                        "label" to sticker.label,
                        "imageBase64" to base64,
                        "createdAt" to sticker.createdAt,
                    )
                )
                .await()
        } catch (_: Exception) {}
    }

    fun deleteSticker(id: String) {
        val owner = currentUsername
        ioScope.launch {
            val file = File(mediaDir, "${id}.png")
            if (file.exists()) file.delete()
            mothDao.deleteSticker(id)
            if (owner.isNotBlank()) {
                try {
                    db.collection("users")
                        .document(owner)
                        .collection("stickers")
                        .document(id)
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
        }
    }

    fun sendSticker(chatId: String, sticker: MothSticker, asViewOnce: Boolean = false) {
        ioScope.launch {
            val file = File(sticker.imageLocalPath)
            if (!file.exists()) return@launch
            val bytes = file.readBytes()
            if (bytes.size > MAX_RELAY_ATTACHMENT_BYTES) return@launch
            val copyFile = File(mediaDir, "sent_stk_${System.currentTimeMillis()}.png")
            copyFile.writeBytes(bytes)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val attachment = PendingAttachment(
                type = AttachmentType.STICKER,
                name = "${sanitizeFilename(sticker.label)}.png",
                localPath = copyFile.absolutePath,
                mimeType = "image/png",
                sizeBytes = bytes.size.toLong(),
                base64Data = base64,
            )
            sendMessage(
                chatId = chatId,
                text = "",
                type = if (asViewOnce) MessageType.VIEW_ONCE else MessageType.REGULAR,
                attachment = attachment,
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Messaging: Send, Reply, Forward, Edit, Delete, Pin, React
    // -------------------------------------------------------------------------

    fun sendMessage(
        chatId: String,
        text: String,
        type: MessageType = MessageType.REGULAR,
        attachment: PendingAttachment? = null,
        replyTo: MothMessage? = null,
        isForwarded: Boolean = false,
    ) {
        val rawTrimmed = text.trim()
        if (rawTrimmed.isBlank() && attachment == null) return
        val sender = currentUsername
        if (sender.isBlank()) return

        var effectiveText = rawTrimmed
        var effectiveType = type
        var leaveTrace = false

        if (effectiveType != MessageType.VIEW_ONCE && rawTrimmed.isNotEmpty()) {
            when {
                rawTrimmed.startsWith("!!!!") && rawTrimmed.endsWith("!!!!") && rawTrimmed.length > 8 -> {
                    val inner = rawTrimmed.substring(4, rawTrimmed.length - 4).trim()
                    if (inner.isNotEmpty()) {
                        effectiveText = inner
                        effectiveType = MessageType.DISAPPEARING
                        leaveTrace = true
                    }
                }
                rawTrimmed.startsWith("!!!") && rawTrimmed.endsWith("!!!") && rawTrimmed.length > 6 -> {
                    val inner = rawTrimmed.substring(3, rawTrimmed.length - 3).trim()
                    if (inner.isNotEmpty()) {
                        effectiveText = inner
                        effectiveType = MessageType.DISAPPEARING
                        leaveTrace = false
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val timeStr = currentTimeLabel()
        val msgId = "msg_${now}_${UUID.randomUUID().toString().replace("-", "").take(6)}"

        updateMyTypingStatus(chatId, false)

        val replyPreview = replyTo?.let {
            if (it.text.isNotBlank()) it.text.take(60)
            else when (it.attachmentType) {
                AttachmentType.PHOTO -> "📷 photo"
                AttachmentType.VOICE -> "🎤 voice note"
                AttachmentType.STICKER -> "✦ sticker"
                AttachmentType.FILE -> "📎 ${it.attachmentName ?: "file"}"
                null -> "message"
            }
        }

        ioScope.launch {
            try {
                val chatEntity = mothDao.getChat(chatId)
                val otherUser = chatEntity?.otherUsername
                    ?: conversations.firstOrNull { it.id == chatId }?.name?.lowercase()
                    ?: chatId.substringAfter("::", "")
                if (otherUser.isBlank()) return@launch
                val isGroup = chatEntity?.isGroup == true

                val msgEntity = MothMessageEntity(
                    localId = localMsgIdFor(sender, msgId),
                    id = msgId,
                    ownerUsername = sender,
                    chatId = chatId,
                    senderUsername = sender,
                    text = effectiveText,
                    type = effectiveType.name,
                    viewOnceStatus = ViewOnceStatus.UNOPENED.name,
                    embersRemaining = 10,
                    leaveTrace = leaveTrace,
                    burnStartAt = if (effectiveType == MessageType.DISAPPEARING) now else 0L,
                    time = timeStr,
                    timestamp = now,
                    attachmentType = attachment?.type?.name,
                    attachmentName = attachment?.name,
                    attachmentLocalPath = attachment?.localPath,
                    attachmentMimeType = attachment?.mimeType,
                    attachmentSizeBytes = attachment?.sizeBytes ?: 0L,
                    audioDurationSec = attachment?.audioDurationSec ?: 0,
                    replyToId = replyTo?.id,
                    replyToSender = replyTo?.senderUsername?.ifBlank { if (replyTo.isMine) sender else otherUser },
                    replyToPreview = replyPreview,
                    isForwarded = isForwarded,
                    status = if (otherUser == "support") MessageStatus.READ.name else MessageStatus.SENDING.name,
                    sentAt = now,
                )
                mothDao.upsertMessage(msgEntity)

                val preview = buildPreviewText(
                    text = effectiveText,
                    type = effectiveType,
                    attachmentType = attachment?.type?.name,
                    attachmentName = attachment?.name,
                )
                val updatedChat = (chatEntity ?: MothChatEntity(
                    id = chatId,
                    ownerUsername = sender,
                    otherUsername = otherUser,
                    preview = preview,
                    time = timeStr,
                    avatarColorArgb = avatarColorForUsername(otherUser).toArgb().toLong(),
                    unreadCount = 0,
                    isLit = true,
                    updatedAt = now,
                )).copy(
                    preview = preview,
                    time = timeStr,
                    isDisappearing = effectiveType == MessageType.DISAPPEARING,
                    isViewOnce = effectiveType == MessageType.VIEW_ONCE,
                    updatedAt = now,
                )
                mothDao.upsertChat(updatedChat)
                syncChatToCloud(updatedChat)

                if (otherUser == "support") {
                    triggerSupportReply(sender, chatId, attachment != null)
                } else if (isGroup) {
                    val groupId = chatId.substringAfter("::")
                    val members = jsonArrayToList(chatEntity?.membersJson ?: "[]").filter { it != sender }
                    val groupKey = getOrFetchGroupKey(groupId, members + sender)
                    for (member in members) {
                        val relayDocId = "${msgId}_${member}"
                        val payload = buildRelayPayload(
                            msgId = msgId,
                            sender = sender,
                            recipient = member,
                            groupId = groupId,
                            text = effectiveText,
                            type = effectiveType,
                            leaveTrace = leaveTrace,
                            timeStr = timeStr,
                            timestamp = now,
                            attachment = attachment,
                            replyToId = msgEntity.replyToId,
                            replyToSender = msgEntity.replyToSender,
                            replyToPreview = msgEntity.replyToPreview,
                            isForwarded = isForwarded,
                            groupKey = groupKey,
                        )
                        db.collection("relay_messages").document(relayDocId).set(payload).await()
                    }
                    mothDao.updateMessageStatus(msgEntity.localId, MessageStatus.SENT.name, sentAt = System.currentTimeMillis())
                } else {
                    val payload = buildRelayPayload(
                        msgId = msgId,
                        sender = sender,
                        recipient = otherUser,
                        groupId = null,
                        text = effectiveText,
                        type = effectiveType,
                        leaveTrace = leaveTrace,
                        timeStr = timeStr,
                        timestamp = now,
                        attachment = attachment,
                        replyToId = msgEntity.replyToId,
                        replyToSender = msgEntity.replyToSender,
                        replyToPreview = msgEntity.replyToPreview,
                        isForwarded = isForwarded,
                        groupKey = null,
                    )
                    db.collection("relay_messages").document(msgId).set(payload).await()
                    mothDao.updateMessageStatus(msgEntity.localId, MessageStatus.SENT.name, sentAt = System.currentTimeMillis())
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun buildRelayPayload(
        msgId: String,
        sender: String,
        recipient: String,
        groupId: String?,
        text: String,
        type: MessageType,
        leaveTrace: Boolean,
        timeStr: String,
        timestamp: Long,
        attachment: PendingAttachment?,
        replyToId: String?,
        replyToSender: String?,
        replyToPreview: String?,
        isForwarded: Boolean,
        groupKey: ByteArray?,
    ): HashMap<String, Any> {
        val payload = hashMapOf<String, Any>(
            "relayKind" to "MESSAGE",
            "id" to msgId,
            "senderUsername" to sender,
            "recipientUsername" to recipient,
            "senderPublicKey" to crypto.getPublicKeyBase64(),
            "type" to type.name,
            "leaveTrace" to leaveTrace,
            "time" to timeStr,
            "timestamp" to timestamp,
            "isForwarded" to isForwarded,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (groupId != null) payload["groupId"] = groupId
        if (!replyToId.isNullOrBlank()) payload["replyToId"] = replyToId
        if (!replyToSender.isNullOrBlank()) payload["replyToSender"] = replyToSender
        if (!replyToPreview.isNullOrBlank()) payload["replyToPreview"] = replyToPreview

        val canEncrypt1to1 = groupId == null && ensurePeerPublicKey(recipient)
        if (groupKey != null) {
            payload["e2ee"] = true
            val c = crypto.encryptWithGroupKey(groupKey, text.toByteArray(Charsets.UTF_8))
            payload["ciphertext"] = Base64.encodeToString(c, Base64.NO_WRAP)
        } else if (canEncrypt1to1) {
            payload["e2ee"] = true
            val c = crypto.encrypt(recipient, text.toByteArray(Charsets.UTF_8))
            payload["ciphertext"] = Base64.encodeToString(c, Base64.NO_WRAP)
        } else {
            payload["e2ee"] = false
            payload["text"] = text
        }

        if (attachment != null) {
            payload["attachmentType"] = attachment.type.name
            payload["attachmentName"] = attachment.name
            payload["attachmentMimeType"] = attachment.mimeType
            payload["attachmentSizeBytes"] = attachment.sizeBytes
            payload["audioDurationSec"] = attachment.audioDurationSec
            val b64 = attachment.base64Data
            if (!b64.isNullOrBlank()) {
                val raw = Base64.decode(b64, Base64.NO_WRAP)
                if (groupKey != null) {
                    val enc = crypto.encryptWithGroupKey(groupKey, raw)
                    payload["attachmentCiphertext"] = Base64.encodeToString(enc, Base64.NO_WRAP)
                } else if (canEncrypt1to1) {
                    val enc = crypto.encrypt(recipient, raw)
                    payload["attachmentCiphertext"] = Base64.encodeToString(enc, Base64.NO_WRAP)
                } else {
                    payload["attachmentBase64"] = b64
                }
            }
        }
        return payload
    }

    fun forwardMessage(message: MothMessage, targetConversation: MothConversation) {
        ioScope.launch {
            var pending: PendingAttachment? = null
            if (message.attachmentType != null && !message.attachmentLocalPath.isNullOrBlank()) {
                val srcFile = File(message.attachmentLocalPath)
                if (srcFile.exists()) {
                    val bytes = srcFile.readBytes()
                    if (bytes.size <= MAX_RELAY_ATTACHMENT_BYTES) {
                        val copyFile = File(mediaDir, "sent_fwd_${System.currentTimeMillis()}_${sanitizeFilename(message.attachmentName ?: "att")}")
                        copyFile.writeBytes(bytes)
                        pending = PendingAttachment(
                            type = message.attachmentType,
                            name = message.attachmentName ?: "attachment",
                            localPath = copyFile.absolutePath,
                            mimeType = message.attachmentMimeType ?: "*/*",
                            sizeBytes = bytes.size.toLong(),
                            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                            audioDurationSec = message.audioDurationSec,
                        )
                    }
                }
            }
            sendMessage(
                chatId = targetConversation.id,
                text = message.text,
                type = MessageType.REGULAR,
                attachment = pending,
                isForwarded = true,
            )
        }
    }

    fun editMessage(chatId: String, messageId: String, newText: String) {
        val clean = newText.trim()
        val owner = currentUsername
        if (clean.isBlank() || owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            if (!msg.senderUsername.equals(owner, ignoreCase = true) || msg.isDeleted) return@launch

            mothDao.updateMessageEdited(localId, clean)
            val chat = mothDao.getChat(chatId) ?: return@launch
            val recipients = if (chat.isGroup) {
                jsonArrayToList(chat.membersJson).filter { it != owner }
            } else if (chat.otherUsername != "support") {
                listOf(chat.otherUsername)
            } else emptyList()

            val groupId = if (chat.isGroup) chat.id.substringAfter("::") else null
            val groupKey = if (groupId != null) getOrFetchGroupKey(groupId, recipients + owner) else null
            for (recipient in recipients) {
                val docId = "edit_${messageId}_${recipient}_${System.currentTimeMillis()}"
                val payload = hashMapOf<String, Any>(
                    "relayKind" to "EDIT",
                    "targetMsgId" to messageId,
                    "senderUsername" to owner,
                    "recipientUsername" to recipient,
                    "senderPublicKey" to crypto.getPublicKeyBase64(),
                )
                if (groupId != null) payload["groupId"] = groupId
                if (groupKey != null) {
                    payload["e2ee"] = true
                    payload["ciphertext"] = Base64.encodeToString(crypto.encryptWithGroupKey(groupKey, clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
                } else if (ensurePeerPublicKey(recipient)) {
                    payload["e2ee"] = true
                    payload["ciphertext"] = Base64.encodeToString(crypto.encrypt(recipient, clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
                } else {
                    payload["e2ee"] = false
                    payload["text"] = clean
                }
                try { db.collection("relay_messages").document(docId).set(payload).await() } catch (_: Exception) {}
            }
        }
    }

    fun deleteMessageForMe(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId)
            if (!msg?.attachmentLocalPath.isNullOrBlank()) {
                try {
                    val f = File(msg!!.attachmentLocalPath!!)
                    if (f.exists() && f.absolutePath.startsWith(mediaDir.absolutePath)) f.delete()
                } catch (_: Exception) {}
            }
            mothDao.deleteMessage(localId)
        }
    }

    fun deleteMessageForEveryone(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            if (!msg.senderUsername.equals(owner, ignoreCase = true)) return@launch

            if (!msg.attachmentLocalPath.isNullOrBlank()) {
                try {
                    val f = File(msg.attachmentLocalPath)
                    if (f.exists() && f.absolutePath.startsWith(mediaDir.absolutePath)) f.delete()
                } catch (_: Exception) {}
            }
            mothDao.markMessageDeleted(localId)

            val chat = mothDao.getChat(chatId) ?: return@launch
            val groupId = if (chat.isGroup) chat.id.substringAfter("::") else null
            val recipients = if (chat.isGroup) {
                jsonArrayToList(chat.membersJson).filter { it != owner }
            } else if (chat.otherUsername != "support") {
                listOf(chat.otherUsername)
            } else emptyList()

            for (recipient in recipients) {
                val docId = "rev_${messageId}_${recipient}_${System.currentTimeMillis()}"
                val payload = hashMapOf<String, Any>(
                    "relayKind" to "REVOKE",
                    "targetMsgId" to messageId,
                    "senderUsername" to owner,
                    "recipientUsername" to recipient,
                )
                if (groupId != null) payload["groupId"] = groupId
                try { db.collection("relay_messages").document(docId).set(payload).await() } catch (_: Exception) {}
            }
        }
    }

    fun togglePinMessage(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            if (msg.isDeleted) return@launch
            val newPinned = !msg.isPinned
            mothDao.updateMessagePinned(localId, newPinned)

            val chat = mothDao.getChat(chatId) ?: return@launch
            val groupId = if (chat.isGroup) chat.id.substringAfter("::") else null
            val recipients = if (chat.isGroup) {
                jsonArrayToList(chat.membersJson).filter { it != owner }
            } else if (chat.otherUsername != "support") {
                listOf(chat.otherUsername)
            } else emptyList()

            for (recipient in recipients) {
                val docId = "pin_${messageId}_${recipient}_${System.currentTimeMillis()}"
                val payload = hashMapOf<String, Any>(
                    "relayKind" to "PIN",
                    "targetMsgId" to messageId,
                    "senderUsername" to owner,
                    "recipientUsername" to recipient,
                    "pinned" to newPinned,
                )
                if (groupId != null) payload["groupId"] = groupId
                try { db.collection("relay_messages").document(docId).set(payload).await() } catch (_: Exception) {}
            }
        }
    }

    fun reactToMessage(chatId: String, messageId: String, emoji: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            val map = parseReactionsJson(msg.reactionsJson).toMutableMap()
            val list = map[emoji]?.toMutableList() ?: mutableListOf()
            val added: Boolean
            if (list.contains(owner)) {
                list.remove(owner)
                added = false
                if (list.isEmpty()) map.remove(emoji) else map[emoji] = list
            } else {
                list.add(owner)
                added = true
                map[emoji] = list
            }
            val (count, hasMine) = computeReactionSummary(map, owner)
            mothDao.updateReactions(localId, count, hasMine, serializeReactionsJson(map))

            val chat = mothDao.getChat(chatId) ?: return@launch
            val groupId = if (chat.isGroup) chat.id.substringAfter("::") else null
            val recipients = if (chat.isGroup) {
                jsonArrayToList(chat.membersJson).filter { it != owner }
            } else if (chat.otherUsername != "support") {
                listOf(chat.otherUsername)
            } else emptyList()

            for (recipient in recipients) {
                val docId = "rx_${messageId}_${recipient}_${System.currentTimeMillis()}"
                val payload = hashMapOf<String, Any>(
                    "relayKind" to "REACTION",
                    "targetMsgId" to messageId,
                    "senderUsername" to owner,
                    "recipientUsername" to recipient,
                    "emoji" to emoji,
                    "add" to added,
                )
                if (groupId != null) payload["groupId"] = groupId
                try { db.collection("relay_messages").document(docId).set(payload).await() } catch (_: Exception) {}
            }
        }
    }

    fun toggleReaction(chatId: String, messageId: String) {
        reactToMessage(chatId, messageId, "🔥")
    }

    private fun triggerSupportReply(ownerUsername: String, chatId: String, hadAttachment: Boolean) {
        scope.launch {
            delay(850)
            isContactTyping = true
            delay(1300)
            isContactTyping = false

            val replyText = if (hadAttachment) {
                "attachment received and sealed locally on your lantern."
            } else {
                listOf(
                    "your transmission is received and stored locally.",
                    "lantern signal is stable across the network.",
                    "all channels are E2EE sealed with on-device history.",
                    "let us know if you need assistance with your wicks or cipher."
                ).random()
            }

            val now = System.currentTimeMillis()
            val timeStr = currentTimeLabel()
            val replyId = "reply_${now}_${UUID.randomUUID().toString().replace("-", "").take(6)}"

            withContext(Dispatchers.IO) {
                mothDao.upsertMessage(
                    MothMessageEntity(
                        localId = localMsgIdFor(ownerUsername, replyId),
                        id = replyId,
                        ownerUsername = ownerUsername,
                        chatId = chatId,
                        senderUsername = "support",
                        text = replyText,
                        type = MessageType.REGULAR.name,
                        time = timeStr,
                        timestamp = now,
                        status = MessageStatus.READ.name,
                        sentAt = now,
                        deliveredAt = now,
                        readAt = now,
                    )
                )
                mothDao.getChat(chatId)?.let { existingChat ->
                    mothDao.upsertChat(existingChat.copy(preview = replyText, time = timeStr, updatedAt = now))
                }
            }
        }
    }

    fun revealViewOnce(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        ioScope.launch {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            val current = try { ViewOnceStatus.valueOf(msg.viewOnceStatus) } catch (_: Exception) { ViewOnceStatus.UNOPENED }
            when (current) {
                ViewOnceStatus.UNOPENED -> mothDao.updateViewOnceStatus(localId, ViewOnceStatus.REVEALED.name)
                ViewOnceStatus.REVEALED -> {
                    if (!msg.attachmentLocalPath.isNullOrBlank()) {
                        try {
                            val file = File(msg.attachmentLocalPath)
                            if (file.exists() && file.absolutePath.startsWith(mediaDir.absolutePath)) file.delete()
                        } catch (_: Exception) {}
                    }
                    mothDao.burnViewOnceMessage(localId)
                }
                ViewOnceStatus.BURNED -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Direct Chats & Group Conversations
    // -------------------------------------------------------------------------

    fun createNewChat(
        name: String,
        initialMessage: String,
        onError: (String) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        val owner = currentUsername
        val cleanName = name.trim().lowercase()
        if (owner.isBlank()) {
            onError("Not logged in"); return
        }
        if (cleanName.isBlank()) {
            onError("Please enter a username"); return
        }
        if (cleanName == owner) {
            onError("You cannot chat with yourself"); return
        }

        scope.launch {
            try {
                val userDoc = withContext(Dispatchers.IO) {
                    db.collection("users").document(cleanName).get().await()
                }
                if (!userDoc.exists()) {
                    onError("User '$cleanName' does not exist in the database")
                    return@launch
                }

                userDoc.getString("e2eePublicKey")?.let { crypto.cachePeerPublicKey(cleanName, it) }
                val remoteColor = userDoc.getLong("avatarColorArgb")
                    ?: avatarColorForUsername(cleanName).toArgb().toLong()
                val remoteAvatarBase64 = userDoc.getString("avatarBase64")
                var savedAvatarPath: String? = null
                if (!remoteAvatarBase64.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val file = File(mediaDir, "contact_avatar_${cleanName}.jpg")
                            file.writeBytes(Base64.decode(remoteAvatarBase64, Base64.NO_WRAP))
                            savedAvatarPath = file.absolutePath
                        } catch (_: Exception) {}
                    }
                }

                val chatId = chatIdFor(owner, cleanName)
                val now = System.currentTimeMillis()
                val timeStr = currentTimeLabel()

                withContext(Dispatchers.IO) {
                    val existing = mothDao.getChat(chatId)
                    if (existing == null) {
                        val newChat = MothChatEntity(
                            id = chatId,
                            ownerUsername = owner,
                            otherUsername = cleanName,
                            preview = initialMessage.trim().ifBlank { "lantern lit" },
                            time = timeStr,
                            avatarColorArgb = remoteColor,
                            avatarLocalPath = savedAvatarPath,
                            unreadCount = 0,
                            isLit = true,
                            updatedAt = now,
                        )
                        mothDao.upsertChat(newChat)
                        syncChatToCloud(newChat)
                    }
                }

                openConversation(chatId)
                if (initialMessage.isNotBlank()) {
                    sendMessage(chatId, initialMessage.trim())
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to create chat")
            }
        }
    }

    fun createGroupChat(
        groupName: String,
        groupIcon: String,
        memberHandles: List<String>,
        onError: (String) -> Unit,
        onSuccess: (String) -> Unit = {},
    ) {
        val owner = currentUsername
        val cleanTitle = groupName.trim()
        if (owner.isBlank()) { onError("Not logged in"); return }
        if (cleanTitle.length < 2) { onError("Group name must be at least 2 characters"); return }

        scope.launch {
            try {
                val requested = memberHandles.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && it != owner && it != "support" }
                    .distinct()
                val verifiedMembers = mutableListOf(owner)
                for (handle in requested) {
                    val doc = withContext(Dispatchers.IO) { db.collection("users").document(handle).get().await() }
                    if (!doc.exists()) {
                        onError("User '$handle' does not exist")
                        return@launch
                    }
                    doc.getString("e2eePublicKey")?.let { crypto.cachePeerPublicKey(handle, it) }
                    verifiedMembers.add(handle)
                }

                val groupId = "grp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                val now = System.currentTimeMillis()
                val timeStr = currentTimeLabel()
                val colorArgb = avatarColorForUsername(cleanTitle).toArgb().toLong()
                val admins = listOf(owner)
                val adminsJson = listToJsonArray(admins)
                val membersJson = listToJsonArray(verifiedMembers)
                val icon = groupIcon.ifBlank { "🏮" }

                withContext(Dispatchers.IO) {
                    db.collection("groups").document(groupId).set(
                        mapOf(
                            "id" to groupId,
                            "name" to cleanTitle,
                            "icon" to icon,
                            "colorArgb" to colorArgb,
                            "admins" to admins,
                            "members" to verifiedMembers,
                            "updatedAt" to now,
                        )
                    ).await()

                    val gk = crypto.generateGroupKey()
                    groupKeysCache[groupId] = gk
                    distributeGroupKey(groupId, gk, verifiedMembers)

                    for (member in verifiedMembers) {
                        val memberChat = MothChatEntity(
                            id = chatIdFor(member, groupId),
                            ownerUsername = member,
                            otherUsername = cleanTitle,
                            preview = "group lantern lit",
                            time = timeStr,
                            avatarColorArgb = colorArgb,
                            isLit = true,
                            updatedAt = now,
                            isGroup = true,
                            groupIcon = icon,
                            adminsJson = adminsJson,
                            membersJson = membersJson,
                        )
                        if (member == owner) {
                            mothDao.upsertChat(memberChat)
                        }
                        // Store under doc key groupId in cloud chats subcollection
                        db.collection("users").document(member).collection("chats").document(groupId)
                            .set(
                                mapOf(
                                    "id" to chatIdFor(member, groupId),
                                    "ownerUsername" to member,
                                    "otherUsername" to cleanTitle,
                                    "groupId" to groupId,
                                    "preview" to "group lantern lit",
                                    "time" to timeStr,
                                    "avatarColorArgb" to colorArgb,
                                    "unreadCount" to if (member == owner) 0 else 1,
                                    "isLit" to true,
                                    "isGroup" to true,
                                    "groupIcon" to icon,
                                    "adminsJson" to adminsJson,
                                    "membersJson" to membersJson,
                                    "updatedAt" to now,
                                )
                            ).await()
                    }
                }

                // Patch local chat row to preserve groupId cleanly inside id (`owner::grp_...`) while display name uses otherUsername
                val myChatId = chatIdFor(owner, groupId)
                // Re-read carefully: `chatId` ends with `::grp_xxx`, while `otherUsername` stores `cleanTitle`!
                // Note: In `sendMessage`/`relay`, extract `groupId` from `chatId.substringAfter("::")`!
                openConversation(myChatId)
                onSuccess(myChatId)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to create group")
            }
        }
    }

    private suspend fun ensureLocalGroupChatFromCloud(owner: String, groupId: String) {
        val chatId = chatIdFor(owner, groupId)
        if (mothDao.getChat(chatId) != null) return
        try {
            val doc = db.collection("groups").document(groupId).get().await()
            if (!doc.exists()) return
            val name = doc.getString("name") ?: "group"
            val icon = doc.getString("icon") ?: "🏮"
            val colorArgb = doc.getLong("colorArgb") ?: avatarColorForUsername(name).toArgb().toLong()
            val admins = (doc.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            mothDao.upsertChat(
                MothChatEntity(
                    id = chatId,
                    ownerUsername = owner,
                    otherUsername = name,
                    preview = "group lantern lit",
                    time = currentTimeLabel(),
                    avatarColorArgb = colorArgb,
                    isGroup = true,
                    groupIcon = icon,
                    adminsJson = listToJsonArray(admins),
                    membersJson = listToJsonArray(members),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } catch (_: Exception) {}
    }

    private fun startObservingGroupMetadata(owner: String, groupIdOrName: String, chatId: String) {
        val groupId = chatId.substringAfter("::")
        if (!groupId.startsWith("grp_")) return
        groupMetaListener?.remove()
        groupMetaListener = db.collection("groups").document(groupId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                ioScope.launch {
                    val local = mothDao.getChat(chatId) ?: return@launch
                    val name = doc.getString("name") ?: local.otherUsername
                    val icon = doc.getString("icon") ?: local.groupIcon
                    val admins = (doc.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val updated = local.copy(
                        otherUsername = name,
                        groupIcon = icon,
                        adminsJson = listToJsonArray(admins),
                        membersJson = listToJsonArray(members),
                    )
                    mothDao.upsertChat(updated)
                    withContext(Dispatchers.Main) {
                        activeContactPresence = ContactPresence(isOnline = true, label = "${members.size} members")
                    }
                }
            }
    }

    fun addGroupMember(chatId: String, handle: String, onResult: (String?) -> Unit) {
        val clean = handle.trim().lowercase()
        val owner = currentUsername
        val groupId = chatId.substringAfter("::")
        if (clean.isBlank() || !groupId.startsWith("grp_")) {
            onResult("Invalid group or handle")
            return
        }
        scope.launch {
            try {
                val userDoc = withContext(Dispatchers.IO) { db.collection("users").document(clean).get().await() }
                if (!userDoc.exists()) {
                    onResult("User '$clean' not found")
                    return@launch
                }
                userDoc.getString("e2eePublicKey")?.let { crypto.cachePeerPublicKey(clean, it) }

                withContext(Dispatchers.IO) {
                    val grpRef = db.collection("groups").document(groupId)
                    val grpSnap = grpRef.get().await()
                    val members = ((grpSnap.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty() + clean).distinct()
                    val admins = (grpSnap.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    if (!admins.contains(owner)) {
                        withContext(Dispatchers.Main) { onResult("Only admins can add members") }
                        return@withContext
                    }
                    val now = System.currentTimeMillis()
                    grpRef.update(mapOf("members" to members, "updatedAt" to now)).await()
                    val gk = getOrFetchGroupKey(groupId, members)
                    if (gk != null) distributeGroupKey(groupId, gk, members)

                    val membersJson = listToJsonArray(members)
                    val adminsJson = listToJsonArray(admins)
                    val name = grpSnap.getString("name") ?: "group"
                    val icon = grpSnap.getString("icon") ?: "🏮"
                    val color = grpSnap.getLong("colorArgb") ?: avatarColorForUsername(name).toArgb().toLong()

                    for (m in members) {
                        db.collection("users").document(m).collection("chats").document(groupId)
                            .set(
                                mapOf(
                                    "id" to chatIdFor(m, groupId),
                                    "ownerUsername" to m,
                                    "otherUsername" to name,
                                    "groupId" to groupId,
                                    "preview" to "$clean joined",
                                    "time" to currentTimeLabel(),
                                    "avatarColorArgb" to color,
                                    "isGroup" to true,
                                    "groupIcon" to icon,
                                    "adminsJson" to adminsJson,
                                    "membersJson" to membersJson,
                                    "updatedAt" to now,
                                ),
                                SetOptions.merge()
                            ).await()
                    }
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(e.localizedMessage ?: "Failed to add member")
            }
        }
    }

    fun removeGroupMember(chatId: String, handle: String) {
        val clean = handle.trim().lowercase()
        val owner = currentUsername
        val groupId = chatId.substringAfter("::")
        if (!groupId.startsWith("grp_") || clean == owner) return
        ioScope.launch {
            try {
                val grpRef = db.collection("groups").document(groupId)
                val grpSnap = grpRef.get().await()
                val admins = (grpSnap.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (!admins.contains(owner)) return@launch
                val members = (grpSnap.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty().filter { it != clean }
                val newAdmins = admins.filter { it != clean }
                val now = System.currentTimeMillis()
                grpRef.update(mapOf("members" to members, "admins" to newAdmins, "updatedAt" to now)).await()
                db.collection("users").document(clean).collection("chats").document(groupId).delete().await()

                val membersJson = listToJsonArray(members)
                val adminsJson = listToJsonArray(newAdmins)
                for (m in members) {
                    db.collection("users").document(m).collection("chats").document(groupId)
                        .update(mapOf("membersJson" to membersJson, "adminsJson" to adminsJson, "updatedAt" to now))
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // WebRTC Voice Calling + Call History Log
    // -------------------------------------------------------------------------

    fun startCall(peerUsername: String, callType: CallType = CallType.VOICE, onError: (String) -> Unit = {}) {
        val me = currentUsername
        val cleanPeer = peerUsername.trim().lowercase()
        if (me.isBlank() || cleanPeer.isBlank() || cleanPeer == me || cleanPeer == "support") {
            onError("Cannot call '$cleanPeer'")
            return
        }
        if (activeCall != null) {
            onError("Already in a call")
            return
        }

        scope.launch {
            try {
                val userDoc = withContext(Dispatchers.IO) { db.collection("users").document(cleanPeer).get().await() }
                if (!userDoc.exists()) {
                    onError("User '$cleanPeer' not found")
                    return@launch
                }

                val callId = "call_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
                val callState = ActiveCallState(
                    callId = callId,
                    peerUsername = cleanPeer,
                    callType = callType,
                    isCaller = true,
                    status = "RINGING",
                    startedAtMs = 0L,
                )
                activeCall = callState

                withContext(Dispatchers.IO) {
                    db.collection("call_sessions").document(callId).set(
                        mapOf(
                            "caller" to me,
                            "callee" to cleanPeer,
                            "callType" to callType.name,
                            "status" to "RINGING",
                            "createdAt" to System.currentTimeMillis(),
                        )
                    ).await()
                }

                webRtcManager.startCallerSession(
                    callId = callId,
                    onRemoteHangup = { scope.launch(Dispatchers.Main) { finishCallLocally(wasAnswered = webRtcManager.connectionState == "connected") } }
                )

                // Monitor when callee answers so timer starts
                callTimerJob?.cancel()
                callTimerJob = scope.launch {
                    while (isActive && activeCall?.callId == callId) {
                        if (webRtcManager.connectionState == "connected" && activeCall?.status != "ACTIVE") {
                            activeCall = activeCall?.copy(status = "ACTIVE", startedAtMs = System.currentTimeMillis())
                        }
                        delay(500L)
                    }
                }
            } catch (e: Exception) {
                activeCall = null
                onError(e.localizedMessage ?: "Failed to start call")
            }
        }
    }

    fun acceptIncomingCall() {
        val call = activeCall ?: return
        if (call.isCaller || call.offerSdp.isNullOrBlank()) return
        activeCall = call.copy(status = "ACTIVE", startedAtMs = System.currentTimeMillis())
        webRtcManager.acceptCalleeSession(
            callId = call.callId,
            offerSdp = call.offerSdp,
            onRemoteHangup = { scope.launch(Dispatchers.Main) { finishCallLocally(wasAnswered = true) } }
        )
    }

    fun declineOrEndCall() {
        val call = activeCall ?: return
        val wasAnswered = call.status == "ACTIVE" || webRtcManager.connectionState == "connected"
        val newStatus = if (!call.isCaller && call.status == "RINGING") "DECLINED" else "ENDED"
        finishCallLocally(wasAnswered = wasAnswered)

        ioScope.launch {
            try {
                val docRef = db.collection("call_sessions").document(call.callId)
                docRef.update("status", newStatus).await()
                delay(1500L)
                cleanupCallSessionDoc(call.callId)
            } catch (_: Exception) {}
        }
    }

    private fun finishCallLocally(wasAnswered: Boolean) {
        val call = activeCall ?: return
        activeCall = null
        callTimerJob?.cancel()
        callTimerJob = null
        webRtcManager.endSession()

        val durationSec = if (wasAnswered && call.startedAtMs > 0L) {
            ((System.currentTimeMillis() - call.startedAtMs) / 1000L).toInt().coerceAtLeast(1)
        } else 0
        val direction = when {
            call.isCaller -> CallDirection.OUTGOING
            wasAnswered -> CallDirection.INCOMING
            else -> CallDirection.MISSED
        }
        recordCallLog(call.peerUsername, call.callType, direction, durationSec)
    }

    private suspend fun cleanupCallSessionDoc(callId: String) {
        try {
            val ref = db.collection("call_sessions").document(callId)
            ref.collection("callerCandidates").get().await().documents.forEach { it.reference.delete().await() }
            ref.collection("calleeCandidates").get().await().documents.forEach { it.reference.delete().await() }
            ref.delete().await()
        } catch (_: Exception) {}
    }

    private fun recordCallLog(peer: String, callType: CallType, direction: CallDirection, durationSec: Int) {
        val owner = currentUsername
        if (owner.isBlank()) return
        val now = System.currentTimeMillis()
        ioScope.launch {
            mothDao.upsertCallLog(
                MothCallLogEntity(
                    id = "clog_${now}_${UUID.randomUUID().toString().take(4)}",
                    ownerUsername = owner,
                    peerUsername = peer,
                    callType = callType.name,
                    direction = direction.name,
                    durationSec = durationSec,
                    timestamp = now,
                    timeLabel = currentTimeLabel(),
                )
            )
        }
    }

    fun deleteCallLog(id: String) {
        ioScope.launch { mothDao.deleteCallLog(id) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun statusRank(status: String): Int = when (status) {
        MessageStatus.SENDING.name -> 0
        MessageStatus.SENT.name -> 1
        MessageStatus.DELIVERED.name -> 2
        MessageStatus.READ.name -> 3
        else -> 1
    }

    private fun parseReactionsJson(jsonStr: String): Map<String, List<String>> {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val result = linkedMapOf<String, List<String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val emoji = keys.next()
                val arr = obj.optJSONArray(emoji) ?: continue
                val users = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i)
                    if (u.isNotBlank()) users.add(u)
                }
                if (users.isNotEmpty()) result[emoji] = users
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeReactionsJson(map: Map<String, List<String>>): String {
        val obj = JSONObject()
        map.forEach { (emoji, users) ->
            if (users.isNotEmpty()) obj.put(emoji, JSONArray(users))
        }
        return obj.toString()
    }

    private fun computeReactionSummary(map: Map<String, List<String>>, username: String): Pair<Int, Boolean> {
        var count = 0
        var hasMine = false
        map.values.forEach { list ->
            count += list.size
            if (list.any { it.equals(username, ignoreCase = true) }) hasMine = true
        }
        return count to hasMine
    }

    private fun jsonArrayToList(jsonStr: String): List<String> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun listToJsonArray(items: List<String>): String = JSONArray(items).toString()

    private fun buildPreviewText(
        text: String,
        type: MessageType,
        attachmentType: String?,
        attachmentName: String?,
        senderPrefix: String = "",
    ): String {
        val core = when {
            type == MessageType.VIEW_ONCE -> "view once"
            type == MessageType.DISAPPEARING -> "🔥 burning message"
            type == MessageType.BURNED_TRACE -> "✦ message burned"
            attachmentType == AttachmentType.STICKER.name -> "✦ sticker"
            attachmentType == AttachmentType.VOICE.name -> "🎤 voice note"
            attachmentType == AttachmentType.PHOTO.name -> if (text.isNotBlank()) "📷 $text" else "📷 photo"
            attachmentType == AttachmentType.FILE.name -> if (text.isNotBlank()) "📎 $text" else "📎 ${attachmentName ?: "file"}"
            else -> text
        }
        return "$senderPrefix$core"
    }

    private fun buildNotificationBody(
        text: String,
        type: MessageType,
        attachmentType: String?,
        attachmentName: String?,
    ): String {
        if (type == MessageType.VIEW_ONCE) return "sent a view-once message"
        if (type == MessageType.DISAPPEARING) return "sent a burning message"
        if (attachmentType == AttachmentType.STICKER.name) return "sent a sticker"
        if (attachmentType == AttachmentType.VOICE.name) return "sent a voice note"
        if (attachmentType == AttachmentType.PHOTO.name) return if (text.isNotBlank()) "📷 $text" else "sent a photo"
        if (attachmentType == AttachmentType.FILE.name) return if (text.isNotBlank()) "📎 $text" else "sent a file: ${attachmentName ?: "attachment"}"
        return text
    }

    private fun formatLastSeen(lastSeenMs: Long): String {
        val diff = System.currentTimeMillis() - lastSeenMs
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${(diff / 60_000L)}m ago"
            diff < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSeenMs)).lowercase()
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastSeenMs)).lowercase()
        }
    }

    private fun chatIdFor(ownerUsername: String, otherUsername: String): String {
        return "${ownerUsername.lowercase()}::${otherUsername.lowercase()}"
    }

    private fun localMsgIdFor(ownerUsername: String, msgId: String): String {
        return "${ownerUsername.lowercase()}::$msgId"
    }

    private fun currentTimeLabel(): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()).lowercase()
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64).ifBlank { "file" }
    }

    private fun avatarColorForUsername(username: String): Color {
        if (username.equals("support", ignoreCase = true)) return Color(0xFFFFB43B)
        val palette = listOf(
            Color(0xFFB8A9E8),
            Color(0xFF82D8A7),
            Color(0xFFF38D71),
            Color(0xFFFFD152),
            Color(0xFF70D6FF),
        )
        val idx = (username.hashCode() and 0x7FFFFFFF) % palette.size
        return palette[idx]
    }

    companion object {
        private const val PREFS_NAME = "moth_session_prefs"
        private const val KEY_LOGGED_IN_USER = "logged_in_username"
        private const val KEY_AVATAR_COLOR = "user_avatar_color"
        private const val KEY_AVATAR_PATH = "user_avatar_path"
        private const val KEY_SCREENSHOT_BLOCK = "pref_screenshot_blocking"
        private const val KEY_HIDE_TYPING = "pref_hide_typing"
        private const val KEY_DISABLE_READ_RECEIPTS = "pref_disable_read_receipts"
        private const val KEY_FONT_SCALE = "pref_font_scale"
        private const val KEY_APP_LOCK_ENABLED = "pref_app_lock_enabled"
        private const val KEY_APP_LOCK_PIN_HASH = "pref_app_lock_pin_hash"
        private const val NOTIF_CHANNEL_ID = "moth_messages"
        private const val BURN_STEP_MS = 1500L
        private const val BURN_DURATION_MS = 10 * BURN_STEP_MS
        private const val MAX_RELAY_ATTACHMENT_BYTES = 600 * 1024
        private const val MAX_AVATAR_BYTES = 150 * 1024
    }
}

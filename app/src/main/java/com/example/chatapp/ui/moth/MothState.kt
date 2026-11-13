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
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.data.local.ChatDatabaseProvider
import com.example.chatapp.data.local.MothChatEntity
import com.example.chatapp.data.local.MothMessageEntity
import com.example.chatapp.data.local.MothStickerEntity
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MessageType {
    REGULAR,
    DISAPPEARING,
    VIEW_ONCE,
    BURNED_TRACE
}

enum class ViewOnceStatus {
    UNOPENED,
    REVEALED,
    BURNED
}

enum class AttachmentType {
    PHOTO,
    FILE,
    STICKER
}

data class PendingAttachment(
    val type: AttachmentType,
    val name: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?,
)

data class MothSticker(
    val id: String,
    val label: String,
    val imageLocalPath: String,
)

data class ContactPresence(
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val label: String = "offline",
)

data class UserProfileDetails(
    val username: String,
    val avatarPath: String?,
    val avatarColor: Color,
    val isOnline: Boolean,
    val statusLabel: String,
    val joinedLabel: String,
    val localMessageCount: Int,
)

data class MothMessage(
    val id: String,
    val senderUsername: String = "",
    val text: String,
    val isMine: Boolean,
    val type: MessageType = MessageType.REGULAR,
    val reactionCount: Int = 0,
    val hasReacted: Boolean = false,
    val viewOnceStatus: ViewOnceStatus = ViewOnceStatus.UNOPENED,
    val embersRemaining: Int = 10,
    val leaveTrace: Boolean = false,
    val burnStartAt: Long = 0L,
    val time: String = "now",
    val attachmentType: AttachmentType? = null,
    val attachmentName: String? = null,
    val attachmentLocalPath: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentSizeBytes: Long = 0L,
)

data class MothConversation(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val avatarColor: Color,
    val avatarLocalPath: String? = null,
    val unreadCount: Int = 0,
    val isLit: Boolean = true,
    val isDisappearing: Boolean = false,
    val isViewOnce: Boolean = false,
    val messages: List<MothMessage> = emptyList(),
)

class MothState(private val context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mothDao = ChatDatabaseProvider.get(appContext).mothDao()
    private val mediaDir: File = File(appContext.filesDir, "moth_media").apply { mkdirs() }

    private val db: FirebaseFirestore by lazy {
        try {
            FirebaseApp.initializeApp(appContext)
        } catch (_: Exception) {}
        FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val burnMutex = Mutex()

    var currentUsername by mutableStateOf("")
    var userAvatarColor by mutableStateOf(Color(0xFFB8A9E8))
    var userAvatarPath by mutableStateOf<String?>(null)
    var isAppInForeground by mutableStateOf(true)

    val isLoggedIn: Boolean
        get() = currentUsername.isNotBlank()

    var conversations by mutableStateOf<List<MothConversation>>(emptyList())
    var activeChatId by mutableStateOf<String?>(null)
    var activeMessages by mutableStateOf<List<MothMessage>>(emptyList())
    var isContactTyping by mutableStateOf(false)
    var activeContactPresence by mutableStateOf(ContactPresence())
    var customStickers by mutableStateOf<List<MothSticker>>(emptyList())
    val contactOnlineMap = mutableStateMapOf<String, Boolean>()

    private var localChatsJob: Job? = null
    private var localMessagesJob: Job? = null
    private var localStickersJob: Job? = null
    private var burnMonitorJob: Job? = null
    private var presenceHeartbeatJob: Job? = null
    private var typingTimeoutJob: Job? = null

    private var relayListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    private var cloudChatsListener: ListenerRegistration? = null
    private var cloudStickersListener: ListenerRegistration? = null
    private var myProfileListener: ListenerRegistration? = null

    private val listPresenceListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val contactPresenceSnapshots = ConcurrentHashMap<String, DocumentSnapshot>()
    private val processingRelayIds = ConcurrentHashMap.newKeySet<String>()
    private val syncedAvatarUsers = ConcurrentHashMap.newKeySet<String>()
    private var lastTypingSentAt: Long = 0L
    private var lastTypingSentState: Boolean = false
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
            loadSavedAvatarForUser(savedUser)
            startSessionStreams(savedUser)
        }

        // Ensure default support user exists in Firestore directory
        scope.launch(Dispatchers.IO) {
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Moth Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming Moth hours messages"
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

    fun updateAvatarColor(color: Color) {
        userAvatarColor = color
        prefs.edit().putInt(KEY_AVATAR_COLOR, color.toArgb()).apply()
        val user = currentUsername
        if (user.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
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

                val userPayload = mapOf(
                    "username" to cleanUser,
                    "passphrase" to cleanPass,
                    "avatarColorArgb" to userAvatarColor.toArgb().toLong(),
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

                // Restore avatar color/photo from Firestore if none set locally
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
        prefs.edit().remove(KEY_LOGGED_IN_USER).apply()
        localChatsJob?.cancel()
        localChatsJob = null
        localMessagesJob?.cancel()
        localMessagesJob = null
        localStickersJob?.cancel()
        localStickersJob = null
        burnMonitorJob?.cancel()
        burnMonitorJob = null
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null

        relayListener?.remove()
        relayListener = null
        typingListener?.remove()
        typingListener = null
        presenceListener?.remove()
        presenceListener = null
        cloudChatsListener?.remove()
        cloudChatsListener = null
        cloudStickersListener?.remove()
        cloudStickersListener = null
        myProfileListener?.remove()
        myProfileListener = null
        listPresenceListeners.values.forEach { it.remove() }
        listPresenceListeners.clear()
        contactPresenceSnapshots.clear()
        contactOnlineMap.clear()

        processingRelayIds.clear()
        syncedAvatarUsers.clear()
        currentUsername = ""
        userAvatarPath = null
        activeChatId = null
        conversations = emptyList()
        activeMessages = emptyList()
        customStickers = emptyList()
        isContactTyping = false
        activeContactPresence = ContactPresence()
    }

    private fun startSessionStreams(username: String) {
        if (username.isBlank()) return

        publishMyPresence(username, isAppInForeground)

        // 1. Ensure local support chat exists on this device & clean up stale logs/files
        ioScope.launch {
            ensureLocalSupportChatExists(username)
            processBurningMessages(username)
            cleanupStaleTypingDocs(username)
            cleanupOrphanedLocalFiles(username)
        }

        // 1b. Sync own profile (avatar & hue) from cloud users/{username}
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
                                withContext(Dispatchers.Main) {
                                    userAvatarColor = colorObj
                                }
                                prefs.edit().putInt(KEY_AVATAR_COLOR, remoteColor.toInt()).apply()
                            }
                        }
                        val remoteBase64 = doc.getString("avatarBase64")
                        if (!remoteBase64.isNullOrBlank()) {
                            val decoded = Base64.decode(remoteBase64, Base64.NO_WRAP)
                            val currentPath = userAvatarPath
                            val currentFile = currentPath?.let { File(it) }
                            val alreadyMatches = currentFile != null && currentFile.exists() &&
                                currentFile.length().toInt() == decoded.size
                            if (!alreadyMatches) {
                                val dest = File(mediaDir, "my_avatar_${username}_${System.currentTimeMillis()}.jpg")
                                dest.writeBytes(decoded)
                                deleteOldUserAvatarFiles(username, keepPath = dest.absolutePath)
                                prefs.edit().putString("${KEY_AVATAR_PATH}_$username", dest.absolutePath).apply()
                                withContext(Dispatchers.Main) {
                                    userAvatarPath = dest.absolutePath
                                }
                            }
                        } else if (!doc.metadata.hasPendingWrites()) {
                            deleteOldUserAvatarFiles(username, keepPath = null)
                            prefs.edit().remove("${KEY_AVATAR_PATH}_$username").apply()
                            if (userAvatarPath != null) {
                                withContext(Dispatchers.Main) {
                                    userAvatarPath = null
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

        // 1c. Sync user's chats list from cloud (users/{username}/chats) into local Room
        cloudChatsListener?.remove()
        cloudChatsListener = db.collection("users").document(username).collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || currentUsername != username) return@addSnapshotListener
                ioScope.launch {
                    try {
                        for (doc in snapshot.documents) {
                            val other = doc.getString("otherUsername")?.trim()?.lowercase()
                                ?: doc.id.trim().lowercase()
                            if (other.isBlank()) continue
                            val chatId = doc.getString("id") ?: chatIdFor(username, other)
                            val cloudUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val local = mothDao.getChat(chatId)
                            if (local == null || cloudUpdatedAt > local.updatedAt) {
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
                                )
                                mothDao.upsertChat(entity)
                                if (other != "support") {
                                    syncContactAvatarInBackground(username, other, chatId)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

        // 1d. Sync custom stickers bidirectionally with cloud (users/{username}/stickers)
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
                                    if (f.exists()) {
                                        syncStickerToCloud(local, f.readBytes())
                                    }
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

        // 2. Observe local Room chats for this user + attach live presence listeners per contact
        localChatsJob?.cancel()
        localChatsJob = scope.launch {
            mothDao.observeChats(username).collect { entities ->
                syncListPresenceListeners(entities.map { it.otherUsername })
                conversations = entities.map { entity ->
                    if (entity.otherUsername != "support" && syncedAvatarUsers.add(entity.otherUsername)) {
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
                        isLit = isContactOnline(entity.otherUsername),
                        isDisappearing = entity.isDisappearing,
                        isViewOnce = entity.isViewOnce,
                    )
                }
            }
        }

        // 3. Observe custom stickers for this user
        localStickersJob?.cancel()
        localStickersJob = scope.launch {
            mothDao.observeStickers(username).collect { entities ->
                customStickers = entities.mapNotNull { entity ->
                    if (File(entity.imageLocalPath).exists()) {
                        MothSticker(
                            id = entity.id,
                            label = entity.label,
                            imageLocalPath = entity.imageLocalPath,
                        )
                    } else null
                }
            }
        }

        // 4. Global non-blocking background burning message ticker (updates live in open chat too!)
        burnMonitorJob?.cancel()
        burnMonitorJob = ioScope.launch {
            while (isActive && currentUsername == username) {
                try {
                    processBurningMessages(username)
                } catch (_: Throwable) {}
                delay(500L)
            }
        }

        // 5. Presence heartbeat every 15s + re-evaluate cached presence snapshots
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

        // 6. Listen for incoming undelivered relay messages in Firestore, save locally, notify, then delete from Firestore
        relayListener?.remove()
        relayListener = db.collection("relay_messages")
            .whereEqualTo("recipientUsername", username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val docId = doc.id
                    if (!processingRelayIds.add(docId)) continue
                    scope.launch(Dispatchers.IO) {
                        try {
                            val recipient = doc.getString("recipientUsername")?.trim()?.lowercase().orEmpty()
                            val sender = doc.getString("senderUsername")?.trim()?.lowercase().orEmpty()
                            if (recipient != username || sender.isBlank()) {
                                doc.reference.delete().await()
                                return@launch
                            }

                            val msgId = doc.getString("id") ?: docId
                            val text = doc.getString("text") ?: ""
                            val typeStr = doc.getString("type") ?: MessageType.REGULAR.name
                            val msgType = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.REGULAR }
                            val leaveTrace = doc.getBoolean("leaveTrace") ?: false
                            val timeStr = doc.getString("time") ?: currentTimeLabel()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val receivedAt = System.currentTimeMillis()
                            val attachmentType = doc.getString("attachmentType")
                            val attachmentName = doc.getString("attachmentName")
                            val attachmentBase64 = doc.getString("attachmentBase64")
                            val attachmentMime = doc.getString("attachmentMimeType")
                            val attachmentSize = doc.getLong("attachmentSizeBytes") ?: 0L

                            var savedLocalPath: String? = null
                            if (!attachmentBase64.isNullOrBlank()) {
                                val safeName = sanitizeFilename(attachmentName ?: "attachment")
                                val targetFile = File(mediaDir, "${msgId}_$safeName")
                                val decodedBytes = Base64.decode(attachmentBase64, Base64.NO_WRAP)
                                targetFile.writeBytes(decodedBytes)
                                savedLocalPath = targetFile.absolutePath
                            }

                            val chatId = chatIdFor(username, sender)
                            val localMsgId = localMsgIdFor(username, msgId)

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
                                        reactionCount = 0,
                                        hasReacted = false,
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
                                    )
                                )

                                val existingChat = mothDao.getChat(chatId)
                                val isCurrentlyOpen = activeChatId == chatId && isAppInForeground
                                val preview = buildPreviewText(
                                    text = text,
                                    type = msgType,
                                    attachmentType = attachmentType,
                                    attachmentName = attachmentName,
                                )
                                val updatedChat = MothChatEntity(
                                    id = chatId,
                                    ownerUsername = username,
                                    otherUsername = sender,
                                    preview = preview,
                                    time = timeStr,
                                    avatarColorArgb = existingChat?.avatarColorArgb
                                        ?: avatarColorForUsername(sender).toArgb().toLong(),
                                    avatarLocalPath = existingChat?.avatarLocalPath,
                                    unreadCount = if (isCurrentlyOpen) 0 else (existingChat?.unreadCount ?: 0) + 1,
                                    isLit = true,
                                    isDisappearing = msgType == MessageType.DISAPPEARING,
                                    isViewOnce = msgType == MessageType.VIEW_ONCE,
                                    updatedAt = timestamp,
                                )
                                mothDao.upsertChat(updatedChat)
                                syncChatToCloud(updatedChat)

                                if (existingChat?.avatarLocalPath == null) {
                                    syncContactAvatarInBackground(username, sender, chatId)
                                }

                                // Fire Android Notification if user isn't currently viewing this exact chat in foreground
                                if (!isCurrentlyOpen) {
                                    val notifText = buildNotificationBody(
                                        text = text,
                                        type = msgType,
                                        attachmentType = attachmentType,
                                        attachmentName = attachmentName,
                                    )
                                    showIncomingMessageNotification(sender, notifText)
                                }
                            }

                            // Delete from Firestore relay once stored locally on device
                            doc.reference.delete().await()
                        } catch (_: Exception) {
                        } finally {
                            processingRelayIds.remove(docId)
                        }
                    }
                }
            }
    }

    private fun syncContactAvatarInBackground(ownerUsername: String, otherUsername: String, chatId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val userDoc = db.collection("users").document(otherUsername).get().await()
                if (!userDoc.exists()) return@launch
                val base64 = userDoc.getString("avatarBase64")
                val colorLong = userDoc.getLong("avatarColorArgb")
                val existingChat = mothDao.getChat(chatId) ?: return@launch

                var newAvatarPath = existingChat.avatarLocalPath
                val contactFile = File(mediaDir, "contact_avatar_${otherUsername}.jpg")
                if (!base64.isNullOrBlank()) {
                    contactFile.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
                    newAvatarPath = contactFile.absolutePath
                } else {
                    if (contactFile.exists()) contactFile.delete()
                    newAvatarPath = null
                }

                val newColor = colorLong ?: existingChat.avatarColorArgb
                if (newAvatarPath != existingChat.avatarLocalPath || newColor != existingChat.avatarColorArgb) {
                    mothDao.upsertChat(
                        existingChat.copy(
                            avatarLocalPath = newAvatarPath,
                            avatarColorArgb = newColor,
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun showIncomingMessageNotification(sender: String, body: String) {
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
            putExtra("open_chat_user", sender)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moth_notification)
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(sender.hashCode(), notification)
    }

    private suspend fun processBurningMessages(ownerUsername: String) = burnMutex.withLock {
        val disappearing = mothDao.getDisappearingMessages(ownerUsername)
        if (disappearing.isEmpty()) return@withLock
        val now = System.currentTimeMillis()

        for (msg in disappearing) {
            val startAt = if (msg.burnStartAt > 0L) msg.burnStartAt else msg.timestamp
            val elapsed = (now - startAt).coerceAtLeast(0L)

            if (elapsed >= BURN_DURATION_MS) {
                // Clean up local attachment file if one was attached
                if (!msg.attachmentLocalPath.isNullOrBlank()) {
                    try {
                        val file = File(msg.attachmentLocalPath)
                        if (file.exists() && file.absolutePath.startsWith(mediaDir.absolutePath)) {
                            file.delete()
                        }
                    } catch (_: Exception) {}
                }

                if (msg.leaveTrace) {
                    // 4 '!' marks -> Leave an ash trace.
                    // Keep original text ONLY for the sender so sender can peek translucently; wipe it for receiver.
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
                        val updatedChat = chat.copy(
                            preview = "✦ message burned",
                            isDisappearing = false,
                        )
                        mothDao.upsertChat(updatedChat)
                        ioScope.launch { syncChatToCloud(updatedChat) }
                    }
                } else {
                    // 3 '!' marks -> Vanish completely with NO trace
                    mothDao.deleteMessage(msg.localId)
                    val chat = mothDao.getChat(msg.chatId)
                    if (chat != null) {
                        val latest = mothDao.getLatestMessage(ownerUsername, msg.chatId)
                        val updatedChat = if (latest == null) {
                            chat.copy(
                                preview = "lantern lit",
                                isDisappearing = false,
                                isViewOnce = false,
                            )
                        } else {
                            val latestType = try { MessageType.valueOf(latest.type) } catch (_: Exception) { MessageType.REGULAR }
                            chat.copy(
                                preview = buildPreviewText(
                                    text = latest.text,
                                    type = latestType,
                                    attachmentType = latest.attachmentType,
                                    attachmentName = latest.attachmentName,
                                ),
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
            db.collection("users")
                .document(chat.ownerUsername)
                .collection("chats")
                .document(chat.otherUsername)
                .set(
                    mapOf(
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
                    ),
                    SetOptions.merge()
                )
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
                )
            )
        }
    }

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
        startObservingContactTypingAndPresence(owner, otherUser)

        if (otherUser != "support") {
            syncContactAvatarInBackground(owner, otherUser, chatId)
        }

        scope.launch(Dispatchers.IO) {
            processBurningMessages(owner)
            mothDao.markChatRead(chatId)
            mothDao.getChat(chatId)?.let { syncChatToCloud(it.copy(unreadCount = 0)) }
        }
    }

    fun closeActiveConversation() {
        val oldChatId = activeChatId
        if (oldChatId != null) {
            updateMyTypingStatus(oldChatId, false)
        }
        activeChatId = null
        localMessagesJob?.cancel()
        localMessagesJob = null
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        typingListener?.remove()
        typingListener = null
        presenceListener?.remove()
        presenceListener = null
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
        return ContactPresence(
            isOnline = fresh,
            lastSeen = displaySeen,
            label = label,
        )
    }

    private fun syncListPresenceListeners(otherUsers: List<String>) {
        val activeSet = otherUsers.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != "support" }
            .toSet()

        // Remove listeners for users no longer in chat list
        val toRemove = listPresenceListeners.keys.filter { it !in activeSet }
        for (user in toRemove) {
            listPresenceListeners.remove(user)?.remove()
            contactPresenceSnapshots.remove(user)
            contactOnlineMap.remove(user)
        }

        // Attach listeners for new contacts
        for (user in activeSet) {
            if (!listPresenceListeners.containsKey(user)) {
                val reg = db.collection("presence").document(user)
                    .addSnapshotListener { snap, error ->
                        if (error != null) return@addSnapshotListener
                        if (snap != null) {
                            contactPresenceSnapshots[user] = snap
                        } else {
                            contactPresenceSnapshots.remove(user)
                        }
                        val evaluated = evaluatePresence(snap)
                        contactOnlineMap[user] = evaluated.isOnline
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
        val other = conversations.firstOrNull { it.id == activeId }?.name?.lowercase()
            ?: activeId.substringAfter("::", "").lowercase()
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
            activeContactPresence = ContactPresence(
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                label = "active",
            )
            return
        }

        activeContactPresence = contactPresenceSnapshots[otherUser]?.let { evaluatePresence(it) }
            ?: ContactPresence(label = "offline")

        // 1. Typing listener
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

        // 2. Presence listener (active / last seen / offline)
        presenceListener = db.collection("presence").document(otherUser)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                if (snap != null) {
                    contactPresenceSnapshots[otherUser] = snap
                }
                val evaluated = evaluatePresence(snap)
                activeContactPresence = evaluated
                contactOnlineMap[otherUser] = evaluated.isOnline
            }
    }

    fun updateMyTypingStatus(chatId: String, isTyping: Boolean) {
        val owner = currentUsername
        if (owner.isBlank()) return
        val otherUser = conversations.firstOrNull { it.id == chatId }?.name?.lowercase()
            ?: chatId.substringAfter("::", "").lowercase()
        if (otherUser.isBlank() || otherUser == "support") return

        val now = System.currentTimeMillis()
        if (isTyping && lastTypingSentState && (now - lastTypingSentAt) < 1800L) {
            return
        }
        if (!isTyping && !lastTypingSentState) {
            return
        }
        lastTypingSentState = isTyping
        lastTypingSentAt = now

        val docId = "${owner}_to_${otherUser}"
        ioScope.launch {
            try {
                val ref = db.collection("typing_status").document(docId)
                if (isTyping) {
                    ref.set(
                        mapOf(
                            "senderUsername" to owner,
                            "isTyping" to true,
                            "updatedAt" to now,
                        )
                    ).await()
                } else {
                    // Delete typing doc cleanly when done so no stale typing timestamps/logs remain
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
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
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
                val belongsToUserOrMedia = name.startsWith("sent_") ||
                    name.startsWith("msg_") ||
                    name.startsWith("my_avatar_${username}")
                if (belongsToUserOrMedia && file.absolutePath !in referenced) {
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
                val colorLong = userDoc.getLong("avatarColorArgb")
                if (colorLong != null) {
                    avatarColor = Color(colorLong.toInt())
                }
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
                val created = userDoc.getTimestamp("createdAt")?.toDate()
                if (created != null) {
                    joinedLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        .format(created).lowercase()
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
        scope.launch(Dispatchers.IO) {
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
                    MothMessage(
                        id = entity.id,
                        senderUsername = entity.senderUsername,
                        text = entity.text,
                        isMine = entity.senderUsername.equals(ownerUsername, ignoreCase = true),
                        type = type,
                        reactionCount = entity.reactionCount,
                        hasReacted = entity.hasReacted,
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
                    )
                }
            }
        }
    }

    suspend fun preparePhotoAttachment(uri: Uri): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        try {
            val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            val rawName = queryDisplayName(uri) ?: "photo_${System.currentTimeMillis()}.jpg"
            val fileName = sanitizeFilename(
                if (rawName.endsWith(".jpg", ignoreCase = true) ||
                    rawName.endsWith(".jpeg", ignoreCase = true) ||
                    rawName.endsWith(".png", ignoreCase = true)
                ) rawName else "$rawName.jpg"
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
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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
        scope.launch(Dispatchers.IO) {
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

    fun sendMessage(
        chatId: String,
        text: String,
        type: MessageType = MessageType.REGULAR,
        attachment: PendingAttachment? = null,
    ) {
        val rawTrimmed = text.trim()
        if (rawTrimmed.isBlank() && attachment == null) return
        val sender = currentUsername
        if (sender.isBlank()) return

        // Parse burning format:
        // !!!!text!!!! -> burning message that LEAVES A TRACE
        // !!!text!!!   -> burning message that leaves NO TRACE
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

        scope.launch(Dispatchers.IO) {
            try {
                val chatEntity = mothDao.getChat(chatId)
                val otherUser = chatEntity?.otherUsername
                    ?: conversations.firstOrNull { it.id == chatId }?.name?.lowercase()
                    ?: chatId.substringAfter("::", "")
                if (otherUser.isBlank()) return@launch

                // 1. Save immediately in sender's local Room DB
                val msgEntity = MothMessageEntity(
                    localId = localMsgIdFor(sender, msgId),
                    id = msgId,
                    ownerUsername = sender,
                    chatId = chatId,
                    senderUsername = sender,
                    text = effectiveText,
                    type = effectiveType.name,
                    reactionCount = 0,
                    hasReacted = false,
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

                // 2. Deliver either to simulated support bot or via Firestore temporary relay
                if (otherUser == "support") {
                    triggerSupportReply(sender, chatId, attachment != null)
                } else {
                    val relayPayload = hashMapOf<String, Any>(
                        "id" to msgId,
                        "senderUsername" to sender,
                        "recipientUsername" to otherUser,
                        "text" to effectiveText,
                        "type" to effectiveType.name,
                        "leaveTrace" to leaveTrace,
                        "time" to timeStr,
                        "timestamp" to now,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                    if (attachment != null) {
                        relayPayload["attachmentType"] = attachment.type.name
                        relayPayload["attachmentName"] = attachment.name
                        relayPayload["attachmentMimeType"] = attachment.mimeType
                        relayPayload["attachmentSizeBytes"] = attachment.sizeBytes
                        if (!attachment.base64Data.isNullOrBlank()) {
                            relayPayload["attachmentBase64"] = attachment.base64Data
                        }
                    }
                    db.collection("relay_messages").document(msgId).set(relayPayload).await()
                }
            } catch (_: Exception) {}
        }
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
                    "all channels are sealed with on-device history.",
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
                    )
                )
                val existingChat = mothDao.getChat(chatId)
                if (existingChat != null) {
                    mothDao.upsertChat(
                        existingChat.copy(
                            preview = replyText,
                            time = timeStr,
                            updatedAt = now,
                        )
                    )
                }
            }
        }
    }

    fun toggleReaction(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            val newReacted = !msg.hasReacted
            val newCount = if (newReacted) msg.reactionCount + 1 else (msg.reactionCount - 1).coerceAtLeast(0)
            mothDao.updateReaction(localId, newCount, newReacted)
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
                            if (file.exists() && file.absolutePath.startsWith(mediaDir.absolutePath)) {
                                file.delete()
                            }
                        } catch (_: Exception) {}
                    }
                    mothDao.burnViewOnceMessage(localId)
                }
                ViewOnceStatus.BURNED -> Unit
            }
        }
    }

    fun createNewChat(
        name: String,
        initialMessage: String,
        onError: (String) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        val owner = currentUsername
        val cleanName = name.trim().lowercase()
        if (owner.isBlank()) {
            onError("Not logged in")
            return
        }
        if (cleanName.isBlank()) {
            onError("Please enter a username")
            return
        }
        if (cleanName == owner) {
            onError("You cannot chat with yourself")
            return
        }

        scope.launch {
            try {
                // Verify target user actually exists in Firestore users database
                val userDoc = withContext(Dispatchers.IO) {
                    db.collection("users").document(cleanName).get().await()
                }
                if (!userDoc.exists()) {
                    onError("User '$cleanName' does not exist in the database")
                    return@launch
                }

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

    private fun buildPreviewText(
        text: String,
        type: MessageType,
        attachmentType: String?,
        attachmentName: String?,
    ): String {
        if (type == MessageType.VIEW_ONCE) return "view once"
        if (type == MessageType.DISAPPEARING) return "🔥 burning message"
        if (type == MessageType.BURNED_TRACE) return "✦ message burned"
        if (attachmentType == AttachmentType.STICKER.name) return "✦ sticker"
        if (attachmentType == AttachmentType.PHOTO.name) {
            return if (text.isNotBlank()) "📷 $text" else "📷 photo"
        }
        if (attachmentType == AttachmentType.FILE.name) {
            return if (text.isNotBlank()) "📎 $text" else "📎 ${attachmentName ?: "file"}"
        }
        return text
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
        if (attachmentType == AttachmentType.PHOTO.name) {
            return if (text.isNotBlank()) "📷 $text" else "sent a photo"
        }
        if (attachmentType == AttachmentType.FILE.name) {
            return if (text.isNotBlank()) "📎 $text" else "sent a file: ${attachmentName ?: "attachment"}"
        }
        return text
    }

    private fun formatLastSeen(lastSeenMs: Long): String {
        val diff = System.currentTimeMillis() - lastSeenMs
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${(diff / 60_000L)}m ago"
            diff < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(Date(lastSeenMs)).lowercase()
            else -> SimpleDateFormat("MMM d", Locale.getDefault())
                .format(Date(lastSeenMs)).lowercase()
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
        private const val NOTIF_CHANNEL_ID = "moth_messages"
        private const val BURN_STEP_MS = 1500L
        private const val BURN_DURATION_MS = 10 * BURN_STEP_MS // 15s total across 10 embers
        private const val MAX_RELAY_ATTACHMENT_BYTES = 600 * 1024 // 600 KB safe limit for Firestore 1 MiB relay docs
        private const val MAX_AVATAR_BYTES = 150 * 1024
    }
}

package com.example.chatapp.ui.moth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.chatapp.R
import com.example.chatapp.data.local.ChatDatabaseProvider
import com.example.chatapp.data.local.MothChatEntity
import com.example.chatapp.data.local.MothMessageEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    VIEW_ONCE
}

enum class ViewOnceStatus {
    UNOPENED,
    REVEALED,
    BURNED
}

enum class AttachmentType {
    PHOTO,
    FILE
}

data class PendingAttachment(
    val type: AttachmentType,
    val name: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?,
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

    var currentUsername by mutableStateOf("")
    var userAvatarColor by mutableStateOf(Color(0xFFB8A9E8))
    var sealedCipherEnabled by mutableStateOf(true)
    var emberChimesEnabled by mutableStateOf(true)

    val isLoggedIn: Boolean
        get() = currentUsername.isNotBlank()

    var conversations by mutableStateOf<List<MothConversation>>(emptyList())
    var activeChatId by mutableStateOf<String?>(null)
    var activeMessages by mutableStateOf<List<MothMessage>>(emptyList())
    var isContactTyping by mutableStateOf(false)

    private var localChatsJob: Job? = null
    private var localMessagesJob: Job? = null
    private var relayListener: ListenerRegistration? = null
    private val processingRelayIds = ConcurrentHashMap.newKeySet<String>()

    init {
        // Restore persistent login session if present
        val savedUser = prefs.getString(KEY_LOGGED_IN_USER, "")?.trim()?.lowercase().orEmpty()
        val savedColorArgb = prefs.getInt(KEY_AVATAR_COLOR, Color(0xFFB8A9E8).toArgb())
        userAvatarColor = Color(savedColorArgb)

        if (savedUser.isNotBlank()) {
            currentUsername = savedUser
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

    fun updateAvatarColor(color: Color) {
        userAvatarColor = color
        prefs.edit().putInt(KEY_AVATAR_COLOR, color.toArgb()).apply()
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
                    "createdAt" to FieldValue.serverTimestamp()
                )
                withContext(Dispatchers.IO) { userRef.set(userPayload).await() }

                persistSession(cleanUser)
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

                persistSession(cleanUser)
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
        prefs.edit().remove(KEY_LOGGED_IN_USER).apply()
        localChatsJob?.cancel()
        localChatsJob = null
        localMessagesJob?.cancel()
        localMessagesJob = null
        relayListener?.remove()
        relayListener = null
        processingRelayIds.clear()
        currentUsername = ""
        activeChatId = null
        conversations = emptyList()
        activeMessages = emptyList()
        isContactTyping = false
    }

    private fun startSessionStreams(username: String) {
        if (username.isBlank()) return

        // 1. Ensure local support chat exists on this device for this user
        scope.launch(Dispatchers.IO) {
            ensureLocalSupportChatExists(username)
        }

        // 2. Observe local Room chats for this user
        localChatsJob?.cancel()
        localChatsJob = scope.launch {
            mothDao.observeChats(username).collect { entities ->
                conversations = entities.map { entity ->
                    MothConversation(
                        id = entity.id,
                        name = entity.otherUsername,
                        preview = entity.preview,
                        time = entity.time,
                        avatarColor = Color(entity.avatarColorArgb.toInt()),
                        unreadCount = entity.unreadCount,
                        isLit = entity.isLit,
                        isDisappearing = entity.isDisappearing,
                        isViewOnce = entity.isViewOnce,
                    )
                }
            }
        }

        // 3. Listen for incoming undelivered relay messages in Firestore, save locally, then delete from Firestore
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
                            val timeStr = doc.getString("time") ?: currentTimeLabel()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
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
                                val isCurrentlyOpen = activeChatId == chatId
                                val preview = buildPreviewText(
                                    text = text,
                                    type = msgType,
                                    attachmentType = attachmentType,
                                    attachmentName = attachmentName,
                                )
                                mothDao.upsertChat(
                                    MothChatEntity(
                                        id = chatId,
                                        ownerUsername = username,
                                        otherUsername = sender,
                                        preview = preview,
                                        time = timeStr,
                                        avatarColorArgb = existingChat?.avatarColorArgb
                                            ?: avatarColorForUsername(sender).toArgb().toLong(),
                                        unreadCount = if (isCurrentlyOpen) 0 else (existingChat?.unreadCount ?: 0) + 1,
                                        isLit = true,
                                        isDisappearing = msgType == MessageType.DISAPPEARING,
                                        isViewOnce = msgType == MessageType.VIEW_ONCE,
                                        updatedAt = timestamp,
                                    )
                                )
                            }

                            // Delete from Firestore relay once stored locally on device (WhatsApp style)
                            doc.reference.delete().await()
                        } catch (_: Exception) {
                        } finally {
                            processingRelayIds.remove(docId)
                        }
                    }
                }
            }
    }

    private suspend fun ensureLocalSupportChatExists(username: String) {
        val chatId = chatIdFor(username, "support")
        val existing = mothDao.getChat(chatId)
        if (existing == null) {
            val now = System.currentTimeMillis()
            val timeStr = currentTimeLabel()
            mothDao.upsertChat(
                MothChatEntity(
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
            )
            mothDao.upsertMessage(
                MothMessageEntity(
                    localId = localMsgIdFor(username, "sup_1"),
                    id = "sup_1",
                    ownerUsername = username,
                    chatId = chatId,
                    senderUsername = "support",
                    text = "welcome to moth hours. your channel is sealed and stored locally.",
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
        val chatId = target?.id ?: chatIdFor(owner, idOrName.trim().lowercase())
        activeChatId = chatId
        startObservingLocalMessages(owner, chatId)

        scope.launch(Dispatchers.IO) {
            mothDao.markChatRead(chatId)
        }
    }

    fun markAllAsRead() {
        val owner = currentUsername
        if (owner.isBlank()) return
        scope.launch(Dispatchers.IO) {
            mothDao.markAllChatsRead(owner)
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
                        // Dynamic per-viewer calculation: fixes both users seeing messages on the right!
                        isMine = entity.senderUsername.equals(ownerUsername, ignoreCase = true),
                        type = type,
                        reactionCount = entity.reactionCount,
                        hasReacted = entity.hasReacted,
                        viewOnceStatus = voStatus,
                        embersRemaining = entity.embersRemaining,
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

            // Decode and scale down to max 800px so it stays well within Firestore 1 MiB relay limit
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
        val cleanText = text.trim()
        if (cleanText.isBlank() && attachment == null) return
        val sender = currentUsername
        if (sender.isBlank()) return

        val now = System.currentTimeMillis()
        val timeStr = currentTimeLabel()
        val msgId = "msg_${now}_${UUID.randomUUID().toString().replace("-", "").take(6)}"

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
                    text = cleanText,
                    type = type.name,
                    reactionCount = 0,
                    hasReacted = false,
                    viewOnceStatus = ViewOnceStatus.UNOPENED.name,
                    embersRemaining = 10,
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
                    text = cleanText,
                    type = type,
                    attachmentType = attachment?.type?.name,
                    attachmentName = attachment?.name,
                )
                mothDao.upsertChat(
                    (chatEntity ?: MothChatEntity(
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
                        isDisappearing = type == MessageType.DISAPPEARING,
                        isViewOnce = type == MessageType.VIEW_ONCE,
                        updatedAt = now,
                    )
                )

                // 2. Deliver either to simulated support bot or via Firestore temporary relay
                if (otherUser == "support") {
                    triggerSupportReply(sender, chatId, attachment != null)
                } else {
                    val relayPayload = hashMapOf<String, Any>(
                        "id" to msgId,
                        "senderUsername" to sender,
                        "recipientUsername" to otherUser,
                        "text" to cleanText,
                        "type" to type.name,
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
        scope.launch(Dispatchers.IO) {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            val current = try { ViewOnceStatus.valueOf(msg.viewOnceStatus) } catch (_: Exception) { ViewOnceStatus.UNOPENED }
            val next = when (current) {
                ViewOnceStatus.UNOPENED -> ViewOnceStatus.REVEALED
                ViewOnceStatus.REVEALED -> ViewOnceStatus.BURNED
                ViewOnceStatus.BURNED -> ViewOnceStatus.BURNED
            }
            mothDao.updateViewOnceStatus(localId, next.name)
        }
    }

    fun tickEmbers(chatId: String, messageId: String) {
        val owner = currentUsername
        if (owner.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val localId = localMsgIdFor(owner, messageId)
            val msg = mothDao.getMessage(localId) ?: return@launch
            if (msg.embersRemaining <= 1) {
                mothDao.deleteMessage(localId)
            } else {
                mothDao.updateEmbers(localId, msg.embersRemaining - 1)
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

                val chatId = chatIdFor(owner, cleanName)
                val now = System.currentTimeMillis()
                val timeStr = currentTimeLabel()

                withContext(Dispatchers.IO) {
                    val existing = mothDao.getChat(chatId)
                    if (existing == null) {
                        mothDao.upsertChat(
                            MothChatEntity(
                                id = chatId,
                                ownerUsername = owner,
                                otherUsername = cleanName,
                                preview = initialMessage.trim().ifBlank { "lantern lit" },
                                time = timeStr,
                                avatarColorArgb = avatarColorForUsername(cleanName).toArgb().toLong(),
                                unreadCount = 0,
                                isLit = true,
                                updatedAt = now,
                            )
                        )
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
        if (attachmentType == AttachmentType.PHOTO.name) {
            return if (text.isNotBlank()) "📷 $text" else "📷 photo"
        }
        if (attachmentType == AttachmentType.FILE.name) {
            return if (text.isNotBlank()) "📎 $text" else "📎 ${attachmentName ?: "file"}"
        }
        return text
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
        private const val MAX_RELAY_ATTACHMENT_BYTES = 600 * 1024 // 600 KB safe limit for Firestore 1 MiB relay docs
    }
}

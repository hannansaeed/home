package com.example.chatapp.ui.moth

import androidx.compose.ui.graphics.Color

enum class MessageType {
    REGULAR,
    DISAPPEARING,
    VIEW_ONCE,
    BURNED_TRACE
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

enum class ViewOnceStatus {
    UNOPENED,
    REVEALED,
    BURNED
}

enum class AttachmentType {
    PHOTO,
    FILE,
    STICKER,
    VOICE
}

enum class CallType {
    VOICE
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED
}

data class PendingAttachment(
    val type: AttachmentType,
    val name: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?,
    val audioDurationSec: Int = 0,
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
    val reactions: Map<String, List<String>> = emptyMap(),
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
    val audioDurationSec: Int = 0,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToPreview: String? = null,
    val isForwarded: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val status: MessageStatus = MessageStatus.SENT,
    val sentAt: Long = 0L,
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
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
    val isGroup: Boolean = false,
    val groupIcon: String = "🏮",
    val admins: List<String> = emptyList(),
    val members: List<String> = emptyList(),
)

data class MothCallLog(
    val id: String,
    val peerUsername: String,
    val callType: CallType,
    val direction: CallDirection,
    val durationSec: Int,
    val timestamp: Long,
    val timeLabel: String,
)

data class ActiveCallState(
    val callId: String,
    val peerUsername: String,
    val callType: CallType,
    val isCaller: Boolean,
    val status: String, // RINGING | CONNECTING | ACTIVE
    val startedAtMs: Long = 0L,
    val offerSdp: String? = null,
)

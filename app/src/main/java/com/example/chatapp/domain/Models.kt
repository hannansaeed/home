package com.example.chatapp.domain

import kotlinx.coroutines.flow.Flow

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val createdAt: Long = 0L,
)

data class ChatSummary(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val updatedAt: Long = 0L,
) {
    fun getOtherParticipantName(currentUserId: String): String {
        return participantNames.entries
            .firstOrNull { it.key != currentUserId }
            ?.value
            ?.ifBlank { "User" }
            ?: "Chat"
    }

    fun getOtherParticipantId(currentUserId: String): String {
        return participantIds.firstOrNull { it != currentUserId } ?: ""
    }
}

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val isMine: Boolean = false,
)

interface ChatRepository {
    val currentUserId: String?
    val currentUserName: String?
    fun observeUserChats(): Flow<List<ChatSummary>>
    fun observeMessages(chatId: String): Flow<List<Message>>
    fun observeUsers(): Flow<List<UserProfile>>
    suspend fun getOrCreateChatWithUser(otherUserId: String, otherUserName: String): String
    suspend fun sendMessage(chatId: String, text: String)
    suspend fun syncCurrentUserProfile()
}

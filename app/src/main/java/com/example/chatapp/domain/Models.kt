package com.example.chatapp.domain

import kotlinx.coroutines.flow.Flow

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> userIds
    val expiresAt: Long? = null,                            // disappearing messages
    val isMine: Boolean = false,
)

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

interface ChatRepository {
    fun messages(chatId: String): Flow<List<Message>>
    val connectionState: Flow<ConnectionState>
    suspend fun send(chatId: String, text: String)
    suspend fun react(messageId: String, emoji: String)
    fun connect()
    fun disconnect()
}

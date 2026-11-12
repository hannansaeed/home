package com.example.chatapp.data

import com.example.chatapp.crypto.MessageCrypto
import com.example.chatapp.data.local.MessageDao
import com.example.chatapp.data.local.MessageEntity
import com.example.chatapp.data.remote.ChatSocket
import com.example.chatapp.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: MessageDao,
    private val socket: ChatSocket,
    private val crypto: MessageCrypto,
) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val me = "me" // TODO: real user id from auth

    override val connectionState: Flow<ConnectionState> = socket.state

    override fun messages(chatId: String): Flow<List<Message>> =
        dao.observe(chatId).map { list -> list.map { it.toDomain() } }

    override fun connect() {
        // TODO: real server URL + auth token
        socket.connect("wss://example.com/ws", token = "dev-token")
        scope.launch {
            socket.incoming.collect { raw ->
                // TODO: parse envelope, decrypt via crypto.decrypt(), upsert into dao
            }
        }
    }

    override fun disconnect() = socket.disconnect()

    override suspend fun send(chatId: String, text: String) {
        val entity = MessageEntity(UUID.randomUUID().toString(), chatId, me, text, System.currentTimeMillis())
        dao.upsert(entity) // optimistic local write
        val cipher = crypto.encrypt(recipientId = chatId, plaintext = text.toByteArray())
        socket.send(android.util.Base64.encodeToString(cipher, android.util.Base64.NO_WRAP))
    }

    override suspend fun react(messageId: String, emoji: String) {
        val current = dao.reactions(messageId)?.let { Json.decodeFromString<Map<String, List<String>>>(it) } ?: emptyMap()
        val users = current[emoji].orEmpty()
        val updated = current + (emoji to if (me in users) users - me else users + me)
        dao.setReactions(messageId, Json.encodeToString(updated))
    }

    private fun MessageEntity.toDomain() = Message(
        id = id, chatId = chatId, senderId = senderId, text = text, timestamp = timestamp,
        reactions = Json.decodeFromString(reactionsJson), expiresAt = expiresAt, isMine = senderId == me,
    )
}

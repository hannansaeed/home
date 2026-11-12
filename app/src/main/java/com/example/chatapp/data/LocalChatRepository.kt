package com.example.chatapp.data

import com.example.chatapp.data.local.LocalAccountDao
import com.example.chatapp.data.local.LocalChatDao
import com.example.chatapp.data.local.LocalChatEntity
import com.example.chatapp.data.local.MessageDao
import com.example.chatapp.data.local.MessageEntity
import com.example.chatapp.domain.ChatRepository
import com.example.chatapp.domain.ChatSummary
import com.example.chatapp.domain.Message
import com.example.chatapp.domain.SessionManager
import com.example.chatapp.domain.UserProfile
import com.example.chatapp.domain.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalChatRepository @Inject constructor(
    private val chatDao: LocalChatDao,
    private val messageDao: MessageDao,
    private val accountDao: LocalAccountDao,
    private val sessionManager: SessionManager,
) : ChatRepository {

    override val currentUserId: String?
        get() = (sessionManager.session.value as? UserSession.Local)?.username

    override val currentUserName: String?
        get() = (sessionManager.session.value as? UserSession.Local)?.username ?: "You"

    override fun observeUserChats(): Flow<List<ChatSummary>> {
        val myName = currentUserName ?: return flowOf(emptyList())
        return chatDao.observeChats().map { entities ->
            entities.map { entity ->
                ChatSummary(
                    id = entity.id,
                    participantIds = listOf(myName, entity.otherUserName),
                    participantNames = mapOf(myName to myName, entity.otherUserName to entity.otherUserName),
                    lastMessage = entity.lastMessage,
                    lastMessageSenderId = "",
                    lastMessageSenderName = entity.otherUserName,
                    updatedAt = entity.updatedAt,
                )
            }
        }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> {
        val myName = currentUserName ?: return flowOf(emptyList())
        return messageDao.observe(chatId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    chatId = entity.chatId,
                    senderId = entity.senderId,
                    senderName = entity.senderId,
                    text = entity.text,
                    timestamp = entity.timestamp,
                    isMine = entity.senderId.equals(myName, ignoreCase = true),
                )
            }
        }
    }

    override fun observeUsers(): Flow<List<UserProfile>> {
        val myName = currentUserName ?: return flowOf(emptyList())
        return accountDao.observeOtherUsernames(myName).map { usernames ->
            val list = mutableListOf<UserProfile>()
            // Add other local registered users
            usernames.forEach { u ->
                list.add(UserProfile(userId = u, displayName = u, email = "$u@local"))
            }
            // Always include a helpful local assistant/support bot
            if (usernames.none { it.equals("Support", ignoreCase = true) }) {
                list.add(UserProfile(userId = "Support", displayName = "Support Team", email = "support@chatapp.local"))
            }
            list
        }
    }

    override suspend fun getOrCreateChatWithUser(otherUserId: String, otherUserName: String): String {
        val existing = chatDao.getChatByOtherUser(otherUserName)
        if (existing != null) {
            return existing.id
        }

        val newId = "local_chat_${UUID.randomUUID().toString().take(8)}"
        val chatEntity = LocalChatEntity(
            id = newId,
            otherUserName = otherUserName,
            lastMessage = "Started a conversation",
            updatedAt = System.currentTimeMillis(),
        )
        chatDao.upsertChat(chatEntity)
        return newId
    }

    override suspend fun sendMessage(chatId: String, text: String) {
        val myName = currentUserName ?: "You"
        val messageId = "msg_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        val msg = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = myName,
            text = text.trim(),
            timestamp = now,
        )
        messageDao.upsert(msg)

        val chat = chatDao.getChat(chatId)
        val otherName = chat?.otherUserName ?: "Chat"
        chatDao.upsertChat(
            LocalChatEntity(
                id = chatId,
                otherUserName = otherName,
                lastMessage = text.trim(),
                updatedAt = now,
            )
        )
    }

    override suspend fun syncCurrentUserProfile() {
        val myName = currentUserName ?: return
        // Ensure welcome chat exists if there are no chats yet
        val existingChats = chatDao.observeChats()
        val welcomeChatId = "welcome_support"
        if (chatDao.getChat(welcomeChatId) == null) {
            chatDao.upsertChat(
                LocalChatEntity(
                    id = welcomeChatId,
                    otherUserName = "Support Team",
                    lastMessage = "Welcome to ChatApp! Send us a message anytime.",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            messageDao.upsert(
                MessageEntity(
                    id = "welcome_msg",
                    chatId = welcomeChatId,
                    senderId = "Support Team",
                    text = "Welcome to ChatApp, $myName! Your local messages are encrypted on-device with SQLCipher. Start chatting or create new conversations from the + button.",
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }
}

package com.example.chatapp.data

import com.example.chatapp.domain.ChatRepository
import com.example.chatapp.domain.ChatSummary
import com.example.chatapp.domain.Message
import com.example.chatapp.domain.SessionManager
import com.example.chatapp.domain.UserProfile
import com.example.chatapp.domain.UserSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeChatRepository @Inject constructor(
    private val firestoreRepo: FirestoreChatRepository,
    private val localRepo: LocalChatRepository,
    private val sessionManager: SessionManager,
) : ChatRepository {

    private val activeRepo: ChatRepository
        get() = if (sessionManager.session.value is UserSession.Local) localRepo else firestoreRepo

    override val currentUserId: String?
        get() = activeRepo.currentUserId

    override val currentUserName: String?
        get() = activeRepo.currentUserName

    override fun observeUserChats(): Flow<List<ChatSummary>> =
        activeRepo.observeUserChats()

    override fun observeMessages(chatId: String): Flow<List<Message>> =
        activeRepo.observeMessages(chatId)

    override fun observeUsers(): Flow<List<UserProfile>> =
        activeRepo.observeUsers()

    override suspend fun getOrCreateChatWithUser(otherUserId: String, otherUserName: String): String =
        activeRepo.getOrCreateChatWithUser(otherUserId, otherUserName)

    override suspend fun sendMessage(chatId: String, text: String) =
        activeRepo.sendMessage(chatId, text)

    override suspend fun syncCurrentUserProfile() =
        activeRepo.syncCurrentUserProfile()
}

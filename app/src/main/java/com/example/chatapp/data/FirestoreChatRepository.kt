package com.example.chatapp.data

import com.example.chatapp.domain.ChatRepository
import com.example.chatapp.domain.ChatSummary
import com.example.chatapp.domain.Message
import com.example.chatapp.domain.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChatRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ChatRepository {

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUserName: String?
        get() = auth.currentUser?.displayName?.ifBlank { "You" } ?: "You"

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google")
    }

    override suspend fun syncCurrentUserProfile() {
        val user = auth.currentUser ?: return
        val docRef = db.collection("users").document(user.uid)
        val existing = docRef.get().await()
        if (!existing.exists()) {
            val payload = hashMapOf<String, Any>(
                "userId" to user.uid,
                "displayName" to (user.displayName?.ifBlank { "User" } ?: "User"),
                "email" to (user.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            user.photoUrl?.toString()?.let { payload["photoUrl"] = it }
            docRef.set(payload).await()
        }
    }

    override fun observeUserChats(): Flow<List<ChatSummary>> {
        val uid = requireUserId()
        return db.collection("chats")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val participants = doc.get("participantIds") as? List<*>
                    val strParticipants = participants?.filterIsInstance<String>() ?: emptyList()
                    val namesMap = (doc.get("participantNames") as? Map<*, *>)
                        ?.filterKeys { it is String }
                        ?.mapKeys { it.key as String }
                        ?.mapValues { it.value?.toString() ?: "" }
                        ?: emptyMap()
                    val lastMsg = doc.getString("lastMessage") ?: ""
                    val senderId = doc.getString("lastMessageSenderId") ?: ""
                    val senderName = doc.getString("lastMessageSenderName") ?: ""
                    val ts = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L

                    ChatSummary(
                        id = id,
                        participantIds = strParticipants,
                        participantNames = namesMap,
                        lastMessage = lastMsg,
                        lastMessageSenderId = senderId,
                        lastMessageSenderName = senderName,
                        updatedAt = ts,
                    )
                }.sortedByDescending { it.updatedAt }
            }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> {
        val uid = requireUserId()
        return db.collection("chats").document(chatId)
            .collection("messages")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val senderId = doc.getString("senderId") ?: ""
                    val senderName = doc.getString("senderName") ?: ""
                    val text = doc.getString("text") ?: ""
                    val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L

                    Message(
                        id = id,
                        chatId = chatId,
                        senderId = senderId,
                        senderName = senderName,
                        text = text,
                        timestamp = ts,
                        isMine = senderId == uid,
                    )
                }.sortedBy { it.timestamp }
            }
    }

    override fun observeUsers(): Flow<List<UserProfile>> {
        val uid = requireUserId()
        return db.collection("users")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val userId = doc.getString("userId") ?: doc.id
                    if (userId == uid) return@mapNotNull null
                    val name = doc.getString("displayName") ?: "User"
                    val email = doc.getString("email") ?: ""
                    val photo = doc.getString("photoUrl")
                    val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    UserProfile(
                        userId = userId,
                        displayName = name,
                        email = email,
                        photoUrl = photo,
                        createdAt = ts,
                    )
                }
            }
    }

    override suspend fun getOrCreateChatWithUser(otherUserId: String, otherUserName: String): String {
        val uid = requireUserId()
        val query = db.collection("chats")
            .whereArrayContains("participantIds", uid)
            .get()
            .await()

        val existing = query.documents.firstOrNull { doc ->
            val p = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            p.contains(otherUserId) && p.size == 2
        }

        if (existing != null) {
            return existing.getString("id") ?: existing.id
        }

        val newChatId = "chat_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val myName = currentUserName ?: "User"
        val payload = hashMapOf<String, Any>(
            "id" to newChatId,
            "participantIds" to listOf(uid, otherUserId),
            "participantNames" to mapOf(uid to myName, otherUserId to otherUserName),
            "lastMessage" to "Started a conversation",
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to myName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db.collection("chats").document(newChatId).set(payload).await()
        return newChatId
    }

    override suspend fun sendMessage(chatId: String, text: String) {
        val uid = requireUserId()
        val myName = currentUserName ?: "User"
        val chatDoc = db.collection("chats").document(chatId).get().await()
        val participants = (chatDoc.get("participantIds") as? List<*>)?.filterIsInstance<String>()
            ?: listOf(uid)

        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val messagePayload = hashMapOf<String, Any>(
            "id" to messageId,
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to myName,
            "text" to text.trim(),
            "participantIds" to participants,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .set(messagePayload).await()

        db.collection("chats").document(chatId).update(
            mapOf(
                "lastMessage" to text.trim(),
                "lastMessageSenderId" to uid,
                "lastMessageSenderName" to myName,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }
}

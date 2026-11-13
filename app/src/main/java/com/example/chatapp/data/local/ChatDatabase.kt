package com.example.chatapp.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val reactionsJson: String = "{}",
    val expiresAt: Long? = null,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observe(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET reactionsJson = :json WHERE id = :id")
    suspend fun setReactions(id: String, json: String)

    @Query("SELECT reactionsJson FROM messages WHERE id = :id")
    suspend fun reactions(id: String): String?

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun purgeExpired(now: Long)
}

@Entity(tableName = "local_accounts")
data class LocalAccountEntity(
    @PrimaryKey val username: String,
    val passwordHash: String,
    val securityQuestion: String,
    val securityAnswerHash: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface LocalAccountDao {
    @Query("SELECT * FROM local_accounts WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getAccount(username: String): LocalAccountEntity?

    @Query("SELECT username FROM local_accounts WHERE username != :excludeUsername COLLATE NOCASE")
    fun observeOtherUsernames(excludeUsername: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: LocalAccountEntity)

    @Query("UPDATE local_accounts SET passwordHash = :newPasswordHash WHERE username = :username COLLATE NOCASE")
    suspend fun updatePassword(username: String, newPasswordHash: String)
}

@Entity(tableName = "local_chats")
data class LocalChatEntity(
    @PrimaryKey val id: String,
    val otherUserName: String,
    val lastMessage: String,
    val updatedAt: Long,
)

@Dao
interface LocalChatDao {
    @Query("SELECT * FROM local_chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<LocalChatEntity>>

    @Query("SELECT * FROM local_chats WHERE id = :id LIMIT 1")
    suspend fun getChat(id: String): LocalChatEntity?

    @Query("SELECT * FROM local_chats WHERE otherUserName = :otherUserName COLLATE NOCASE LIMIT 1")
    suspend fun getChatByOtherUser(otherUserName: String): LocalChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: LocalChatEntity)
}

// =========================================================================
// Moth Hours Local-First Storage (WhatsApp-style per-user on-device Room DB)
// =========================================================================

@Entity(tableName = "moth_chats")
data class MothChatEntity(
    @PrimaryKey val id: String, // "${ownerUsername}::${otherUsername}"
    val ownerUsername: String,
    val otherUsername: String,
    val preview: String,
    val time: String,
    val avatarColorArgb: Long,
    val avatarLocalPath: String? = null,
    val unreadCount: Int = 0,
    val isLit: Boolean = true,
    val isDisappearing: Boolean = false,
    val isViewOnce: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "moth_messages")
data class MothMessageEntity(
    @PrimaryKey val localId: String, // "${ownerUsername}::${id}"
    val id: String,
    val ownerUsername: String,
    val chatId: String,
    val senderUsername: String,
    val text: String,
    val type: String = "REGULAR", // REGULAR | DISAPPEARING | VIEW_ONCE | BURNED_TRACE
    val reactionCount: Int = 0,
    val hasReacted: Boolean = false,
    val viewOnceStatus: String = "UNOPENED",
    val embersRemaining: Int = 10,
    val leaveTrace: Boolean = false,
    val burnStartAt: Long = 0L,
    val time: String = "now",
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentType: String? = null,      // "PHOTO" | "FILE" | "STICKER" | null
    val attachmentName: String? = null,
    val attachmentLocalPath: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentSizeBytes: Long = 0L,
)

@Entity(tableName = "moth_stickers")
data class MothStickerEntity(
    @PrimaryKey val id: String,
    val ownerUsername: String,
    val label: String,
    val imageLocalPath: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface MothDao {
    @Query("SELECT * FROM moth_chats WHERE ownerUsername = :ownerUsername ORDER BY updatedAt DESC")
    fun observeChats(ownerUsername: String): Flow<List<MothChatEntity>>

    @Query("SELECT * FROM moth_chats WHERE id = :id LIMIT 1")
    suspend fun getChat(id: String): MothChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: MothChatEntity)

    @Query("UPDATE moth_chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatRead(chatId: String)

    @Query("UPDATE moth_chats SET unreadCount = 0 WHERE ownerUsername = :ownerUsername")
    suspend fun markAllChatsRead(ownerUsername: String)

    @Query("UPDATE moth_chats SET avatarLocalPath = :avatarPath WHERE id = :chatId")
    suspend fun updateChatAvatarPath(chatId: String, avatarPath: String?)

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(ownerUsername: String, chatId: String): Flow<List<MothMessageEntity>>

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND type = 'DISAPPEARING'")
    suspend fun getDisappearingMessages(ownerUsername: String): List<MothMessageEntity>

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(ownerUsername: String, chatId: String): MothMessageEntity?

    @Query("SELECT COUNT(*) FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId")
    suspend fun getChatMessageCount(ownerUsername: String, chatId: String): Int

    @Query("SELECT * FROM moth_messages WHERE localId = :localId LIMIT 1")
    suspend fun getMessage(localId: String): MothMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MothMessageEntity)

    @Query("DELETE FROM moth_messages WHERE localId = :localId")
    suspend fun deleteMessage(localId: String)

    @Query("UPDATE moth_messages SET embersRemaining = :embers WHERE localId = :localId")
    suspend fun updateEmbers(localId: String, embers: Int)

    @Query("UPDATE moth_messages SET viewOnceStatus = :status WHERE localId = :localId")
    suspend fun updateViewOnceStatus(localId: String, status: String)

    @Query("UPDATE moth_messages SET viewOnceStatus = 'BURNED', text = '', attachmentType = NULL, attachmentName = NULL, attachmentLocalPath = NULL, attachmentMimeType = NULL, attachmentSizeBytes = 0 WHERE localId = :localId")
    suspend fun burnViewOnceMessage(localId: String)

    @Query("UPDATE moth_messages SET reactionCount = :count, hasReacted = :hasReacted WHERE localId = :localId")
    suspend fun updateReaction(localId: String, count: Int, hasReacted: Boolean)

    @Query("SELECT * FROM moth_stickers WHERE ownerUsername = :ownerUsername ORDER BY createdAt DESC")
    fun observeStickers(ownerUsername: String): Flow<List<MothStickerEntity>>

    @Query("SELECT * FROM moth_stickers WHERE ownerUsername = :ownerUsername")
    suspend fun getStickersOnce(ownerUsername: String): List<MothStickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSticker(sticker: MothStickerEntity)

    @Query("DELETE FROM moth_stickers WHERE id = :id")
    suspend fun deleteSticker(id: String)

    @Query("SELECT attachmentLocalPath FROM moth_messages WHERE attachmentLocalPath IS NOT NULL")
    suspend fun getAllAttachmentPaths(): List<String>

    @Query("SELECT avatarLocalPath FROM moth_chats WHERE avatarLocalPath IS NOT NULL")
    suspend fun getAllChatAvatarPaths(): List<String>

    @Query("SELECT imageLocalPath FROM moth_stickers")
    suspend fun getAllStickerPaths(): List<String>
}

@Database(
    entities = [
        MessageEntity::class,
        LocalAccountEntity::class,
        LocalChatEntity::class,
        MothChatEntity::class,
        MothMessageEntity::class,
        MothStickerEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun accountDao(): LocalAccountDao
    abstract fun chatDao(): LocalChatDao
    abstract fun mothDao(): MothDao
}

object ChatDatabaseProvider {
    @Volatile
    private var INSTANCE: ChatDatabase? = null

    fun get(context: Context): ChatDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                try {
                    context.applicationContext.deleteDatabase("chat.db")
                } catch (_: Exception) {}
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chatapp_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

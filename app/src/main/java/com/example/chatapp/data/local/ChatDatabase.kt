package com.example.chatapp.data.local

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

@Database(
    entities = [MessageEntity::class, LocalAccountEntity::class, LocalChatEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun accountDao(): LocalAccountDao
    abstract fun chatDao(): LocalChatDao
}

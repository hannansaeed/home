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
    @PrimaryKey val id: String, // "${ownerUsername}::${otherUsername}" or "${ownerUsername}::grp_${groupId}"
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
    val isGroup: Boolean = false,
    val groupIcon: String = "🏮",
    val adminsJson: String = "[]",
    val membersJson: String = "[]",
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
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
    val reactionsJson: String = "{}", // {"❤️":["alice","bob"]}
    val viewOnceStatus: String = "UNOPENED",
    val embersRemaining: Int = 10,
    val leaveTrace: Boolean = false,
    val burnStartAt: Long = 0L,
    val time: String = "now",
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentType: String? = null,      // "PHOTO" | "FILE" | "STICKER" | "VOICE" | null
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
    val isStarred: Boolean = false,
    val status: String = "SENT", // SENDING | SENT | DELIVERED | READ
    val sentAt: Long = 0L,
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
    val listenedAt: Long = 0L,
)

@Entity(tableName = "moth_stickers")
data class MothStickerEntity(
    @PrimaryKey val id: String,
    val ownerUsername: String,
    val label: String,
    val imageLocalPath: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "moth_call_logs")
data class MothCallLogEntity(
    @PrimaryKey val id: String,
    val ownerUsername: String,
    val peerUsername: String,
    val callType: String, // VOICE | VIDEO
    val direction: String, // INCOMING | OUTGOING | MISSED
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val timeLabel: String = "now",
)

@Dao
interface MothDao {
    @Query("SELECT * FROM moth_chats WHERE ownerUsername = :ownerUsername ORDER BY isPinned DESC, updatedAt DESC")
    fun observeChats(ownerUsername: String): Flow<List<MothChatEntity>>

    @Query("SELECT * FROM moth_chats WHERE ownerUsername = :ownerUsername")
    suspend fun getChatsOnce(ownerUsername: String): List<MothChatEntity>

    @Query("SELECT * FROM moth_chats WHERE id = :id LIMIT 1")
    suspend fun getChat(id: String): MothChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: MothChatEntity)

    @Query("DELETE FROM moth_chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("UPDATE moth_chats SET isPinned = :pinned WHERE id = :chatId")
    suspend fun updateChatPinned(chatId: String, pinned: Boolean)

    @Query("UPDATE moth_chats SET isMuted = :muted WHERE id = :chatId")
    suspend fun updateChatMuted(chatId: String, muted: Boolean)

    @Query("UPDATE moth_chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatRead(chatId: String)

    @Query("UPDATE moth_chats SET unreadCount = 0 WHERE ownerUsername = :ownerUsername")
    suspend fun markAllChatsRead(ownerUsername: String)

    @Query("UPDATE moth_chats SET avatarLocalPath = :avatarPath WHERE id = :chatId")
    suspend fun updateChatAvatarPath(chatId: String, avatarPath: String?)

    @Query("UPDATE moth_chats SET ownerUsername = :newOwner WHERE ownerUsername = :oldOwner")
    suspend fun migrateChatsOwner(oldOwner: String, newOwner: String)

    @Query("UPDATE moth_chats SET id = :newChatId WHERE id = :oldChatId")
    suspend fun updateChatId(oldChatId: String, newChatId: String)

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(ownerUsername: String, chatId: String): Flow<List<MothMessageEntity>>

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND isStarred = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeStarredMessages(ownerUsername: String): Flow<List<MothMessageEntity>>

    @Query("SELECT * FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatOnce(ownerUsername: String, chatId: String): List<MothMessageEntity>

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

    @Query("DELETE FROM moth_messages WHERE ownerUsername = :ownerUsername AND chatId = :chatId")
    suspend fun deleteMessagesByChat(ownerUsername: String, chatId: String)

    @Query("UPDATE moth_messages SET ownerUsername = :newOwner WHERE ownerUsername = :oldOwner")
    suspend fun migrateMessagesOwner(oldOwner: String, newOwner: String)

    @Query("UPDATE moth_messages SET chatId = :newChatId WHERE chatId = :oldChatId")
    suspend fun updateMessagesChatId(oldChatId: String, newChatId: String)

    @Query("UPDATE moth_messages SET embersRemaining = :embers WHERE localId = :localId")
    suspend fun updateEmbers(localId: String, embers: Int)

    @Query("UPDATE moth_messages SET viewOnceStatus = :status WHERE localId = :localId")
    suspend fun updateViewOnceStatus(localId: String, status: String)

    @Query("UPDATE moth_messages SET viewOnceStatus = 'BURNED', text = '', attachmentType = NULL, attachmentName = NULL, attachmentLocalPath = NULL, attachmentMimeType = NULL, attachmentSizeBytes = 0 WHERE localId = :localId")
    suspend fun burnViewOnceMessage(localId: String)

    @Query("UPDATE moth_messages SET reactionCount = :count, hasReacted = :hasReacted, reactionsJson = :reactionsJson WHERE localId = :localId")
    suspend fun updateReactions(localId: String, count: Int, hasReacted: Boolean, reactionsJson: String)

    @Query("UPDATE moth_messages SET text = :newText, isEdited = 1 WHERE localId = :localId")
    suspend fun updateMessageEdited(localId: String, newText: String)

    @Query("UPDATE moth_messages SET isDeleted = 1, isPinned = 0, isStarred = 0, text = '', attachmentType = NULL, attachmentName = NULL, attachmentLocalPath = NULL, attachmentMimeType = NULL, attachmentSizeBytes = 0 WHERE localId = :localId")
    suspend fun markMessageDeleted(localId: String)

    @Query("UPDATE moth_messages SET isPinned = :pinned WHERE localId = :localId")
    suspend fun updateMessagePinned(localId: String, pinned: Boolean)

    @Query("UPDATE moth_messages SET isStarred = :starred WHERE localId = :localId")
    suspend fun updateMessageStarred(localId: String, starred: Boolean)

    @Query("UPDATE moth_messages SET status = :status, sentAt = CASE WHEN :sentAt > 0 THEN :sentAt ELSE sentAt END, deliveredAt = CASE WHEN :deliveredAt > 0 THEN :deliveredAt ELSE deliveredAt END, readAt = CASE WHEN :readAt > 0 THEN :readAt ELSE readAt END WHERE localId = :localId")
    suspend fun updateMessageStatus(localId: String, status: String, sentAt: Long = 0L, deliveredAt: Long = 0L, readAt: Long = 0L)

    @Query("UPDATE moth_messages SET listenedAt = CASE WHEN :listenedAt > 0 THEN :listenedAt ELSE listenedAt END, status = 'READ', readAt = CASE WHEN readAt <= 0 AND :listenedAt > 0 THEN :listenedAt ELSE readAt END WHERE localId = :localId")
    suspend fun updateMessageListened(localId: String, listenedAt: Long)

    @Query("SELECT * FROM moth_stickers WHERE ownerUsername = :ownerUsername ORDER BY createdAt DESC")
    fun observeStickers(ownerUsername: String): Flow<List<MothStickerEntity>>

    @Query("SELECT * FROM moth_stickers WHERE ownerUsername = :ownerUsername")
    suspend fun getStickersOnce(ownerUsername: String): List<MothStickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSticker(sticker: MothStickerEntity)

    @Query("DELETE FROM moth_stickers WHERE id = :id")
    suspend fun deleteSticker(id: String)

    @Query("UPDATE moth_stickers SET ownerUsername = :newOwner WHERE ownerUsername = :oldOwner")
    suspend fun migrateStickersOwner(oldOwner: String, newOwner: String)

    @Query("SELECT * FROM moth_call_logs WHERE ownerUsername = :ownerUsername ORDER BY timestamp DESC")
    fun observeCallLogs(ownerUsername: String): Flow<List<MothCallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallLog(log: MothCallLogEntity)

    @Query("DELETE FROM moth_call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: String)

    @Query("UPDATE moth_call_logs SET ownerUsername = :newOwner WHERE ownerUsername = :oldOwner")
    suspend fun migrateCallLogsOwner(oldOwner: String, newOwner: String)

    @Query("SELECT COUNT(*) FROM moth_messages WHERE ownerUsername = :ownerUsername")
    suspend fun getTotalMessageCount(ownerUsername: String): Int

    @Query("SELECT COUNT(*) FROM moth_chats WHERE ownerUsername = :ownerUsername")
    suspend fun getTotalChatCount(ownerUsername: String): Int

    @Query("SELECT COUNT(*) FROM moth_call_logs WHERE ownerUsername = :ownerUsername")
    suspend fun getTotalCallLogCount(ownerUsername: String): Int

    @Query("DELETE FROM moth_call_logs WHERE ownerUsername = :ownerUsername")
    suspend fun clearAllCallLogs(ownerUsername: String)

    @Query("UPDATE moth_messages SET attachmentLocalPath = NULL WHERE ownerUsername = :ownerUsername AND attachmentLocalPath IS NOT NULL")
    suspend fun clearAllAttachmentPaths(ownerUsername: String)

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
        MothCallLogEntity::class,
    ],
    version = 7,
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

    private fun getExistingColumns(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Set<String> {
        val cols = mutableSetOf<String>()
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) {
                    cols.add(cursor.getString(nameIdx).lowercase())
                }
            }
        }
        return cols
    }

    private fun addMissingColumns(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        columnDefinitions: List<String>,
    ) {
        val existingCols = getExistingColumns(db, tableName)
        if (existingCols.isEmpty()) return
        columnDefinitions.forEach { colDef ->
            val colName = colDef.substringBefore(' ').trim().lowercase()
            if (colName !in existingCols) {
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $colDef")
            }
        }
    }

    private fun runSafeMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val colsChats = listOf(
            "isPinned INTEGER NOT NULL DEFAULT 0",
            "isMuted INTEGER NOT NULL DEFAULT 0",
            "isGroup INTEGER NOT NULL DEFAULT 0",
            "groupIcon TEXT NOT NULL DEFAULT '🏮'",
            "adminsJson TEXT NOT NULL DEFAULT '[]'",
            "membersJson TEXT NOT NULL DEFAULT '[]'",
            "isDisappearing INTEGER NOT NULL DEFAULT 0",
            "isViewOnce INTEGER NOT NULL DEFAULT 0"
        )
        addMissingColumns(db, "moth_chats", colsChats)

        val colsMsgs = listOf(
            "isPinned INTEGER NOT NULL DEFAULT 0",
            "isStarred INTEGER NOT NULL DEFAULT 0",
            "isEdited INTEGER NOT NULL DEFAULT 0",
            "isDeleted INTEGER NOT NULL DEFAULT 0",
            "isForwarded INTEGER NOT NULL DEFAULT 0",
            "replyToId TEXT",
            "replyToSender TEXT",
            "replyToPreview TEXT",
            "attachmentType TEXT",
            "attachmentName TEXT",
            "attachmentLocalPath TEXT",
            "attachmentMimeType TEXT",
            "attachmentSizeBytes INTEGER NOT NULL DEFAULT 0",
            "audioDurationSec INTEGER NOT NULL DEFAULT 0",
            "viewOnceStatus TEXT NOT NULL DEFAULT 'UNOPENED'",
            "embersRemaining INTEGER NOT NULL DEFAULT 10",
            "leaveTrace INTEGER NOT NULL DEFAULT 0",
            "burnStartAt INTEGER NOT NULL DEFAULT 0",
            "reactionsJson TEXT NOT NULL DEFAULT '{}'",
            "reactionCount INTEGER NOT NULL DEFAULT 0",
            "hasReacted INTEGER NOT NULL DEFAULT 0",
            "status TEXT NOT NULL DEFAULT 'SENT'",
            "sentAt INTEGER NOT NULL DEFAULT 0",
            "deliveredAt INTEGER NOT NULL DEFAULT 0",
            "readAt INTEGER NOT NULL DEFAULT 0",
            "listenedAt INTEGER NOT NULL DEFAULT 0"
        )
        addMissingColumns(db, "moth_messages", colsMsgs)
    }

    private val MIGRATION_1_7 = object : androidx.room.migration.Migration(1, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }
    private val MIGRATION_2_7 = object : androidx.room.migration.Migration(2, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }
    private val MIGRATION_3_7 = object : androidx.room.migration.Migration(3, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }
    private val MIGRATION_4_7 = object : androidx.room.migration.Migration(4, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }
    private val MIGRATION_5_7 = object : androidx.room.migration.Migration(5, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }
    private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { runSafeMigration(db) }
    }

    fun get(context: Context): ChatDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chatapp_local.db"
                )
                    .addMigrations(
                        MIGRATION_1_7,
                        MIGRATION_2_7,
                        MIGRATION_3_7,
                        MIGRATION_4_7,
                        MIGRATION_5_7,
                        MIGRATION_6_7,
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

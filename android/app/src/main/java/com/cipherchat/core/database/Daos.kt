package com.cipherchat.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun statusToString(s: MessageStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): MessageStatus = MessageStatus.valueOf(s)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY COALESCE(lastMessageAt, createdAt) DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Upsert suspend fun upsertAll(items: List<ConversationEntity>)
    @Upsert suspend fun upsert(item: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<Long>)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :id")
    suspend fun incrementUnread(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {
    /** Server-ordered messages first (by server id), then still-pending local ones in creation order. */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY CASE WHEN serverId IS NULL THEN 1 ELSE 0 END, serverId, createdAt",
    )
    fun observe(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE clientMsgId = :clientMsgId")
    suspend fun get(clientMsgId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): MessageEntity?

    @Upsert suspend fun upsert(item: MessageEntity)
    @Upsert suspend fun upsertAll(items: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY createdAt")
    suspend fun pending(): List<MessageEntity>

    @Query("SELECT MIN(serverId) FROM messages WHERE conversationId = :conversationId AND serverId IS NOT NULL")
    suspend fun oldestServerId(conversationId: Long): Long?

    @Query("SELECT MAX(serverId) FROM messages WHERE conversationId = :conversationId")
    suspend fun newestServerId(conversationId: Long): Long?

    @Query("UPDATE messages SET status = :status, failureReason = :reason WHERE clientMsgId = :clientMsgId")
    suspend fun setStatus(clientMsgId: String, status: MessageStatus, reason: String?)

    @Query("UPDATE messages SET deliveredCount = deliveredCount + 1 WHERE serverId = :serverId")
    suspend fun incrementDelivered(serverId: Long)

    @Query("UPDATE messages SET reactionsJson = :json WHERE serverId = :serverId")
    suspend fun setReactions(serverId: Long, json: String)

    @Query("SELECT serverId FROM messages WHERE conversationId = :conversationId AND serverId IS NOT NULL AND serverId >= :minId")
    suspend fun serverIdsFrom(conversationId: Long, minId: Long): List<Long>

    @Query("DELETE FROM messages WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM messages WHERE clientMsgId = :clientMsgId")
    suspend fun delete(clientMsgId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    /** Disappearing messages: remove locally once their server-assigned expiry has passed. */
    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}

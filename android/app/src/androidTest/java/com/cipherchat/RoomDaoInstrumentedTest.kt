package com.cipherchat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cipherchat.core.database.AppDatabase
import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite via Room: verifies the SQL the JVM fakes can only imitate. */
@RunWith(AndroidJUnit4::class)
class RoomDaoInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun msg(
        id: String, serverId: Long?, conv: Long = 1, createdAt: Long = 0, status: MessageStatus = MessageStatus.SENT,
        expires: Long? = null, text: String = "t",
    ) = MessageEntity(id, serverId, conv, 1, "plain", text, null, null, createdAt, expires, status, 0, "[]")

    private fun conv(id: Long, last: Long?, unread: Int = 0) = ConversationEntity(
        id, "direct", null, "[]", null, unread, null, null, null, null, last, createdAt = 1,
    )

    @Test
    fun serverMessagesOrderedByServerIdThenPendingByCreationTime() = runBlocking {
        val dao = db.messageDao()
        dao.upsertAll(
            listOf(
                msg("pending-b", null, createdAt = 30, status = MessageStatus.PENDING),
                msg("srv-3", 3, createdAt = 5),
                msg("pending-a", null, createdAt = 20, status = MessageStatus.PENDING),
                msg("srv-1", 1, createdAt = 100),
                msg("srv-2", 2, createdAt = 1),
            ),
        )
        assertEquals(listOf("srv-1", "srv-2", "srv-3", "pending-a", "pending-b"), dao.observe(1).first().map { it.clientMsgId })
    }

    @Test
    fun upsertByClientIdCollapsesOptimisticRowAndServerEcho() = runBlocking {
        val dao = db.messageDao()
        dao.upsert(msg("c-1", null, status = MessageStatus.PENDING, text = "draft"))
        dao.upsert(msg("c-1", 42, status = MessageStatus.SENT, text = "draft"))
        dao.upsert(msg("c-1", 42, status = MessageStatus.SENT, text = "draft")) // duplicate broadcast
        val rows = dao.observe(1).first()
        assertEquals(1, rows.size)
        assertEquals(42L, rows.single().serverId)
        assertEquals(MessageStatus.SENT, rows.single().status)
        assertTrue(dao.pending().isEmpty())
    }

    @Test
    fun pendingQueryReturnsOnlyPendingOldestFirst() = runBlocking {
        val dao = db.messageDao()
        dao.upsertAll(
            listOf(
                msg("p2", null, createdAt = 2, status = MessageStatus.PENDING),
                msg("f1", null, createdAt = 1, status = MessageStatus.FAILED),
                msg("p1", null, createdAt = 1, status = MessageStatus.PENDING),
                msg("s1", 9, createdAt = 0),
            ),
        )
        assertEquals(listOf("p1", "p2"), dao.pending().map { it.clientMsgId })
    }

    @Test
    fun purgeExpiredRemovesOnlyElapsedMessages() = runBlocking {
        val dao = db.messageDao()
        dao.upsertAll(listOf(msg("gone", 1, expires = 100), msg("edge", 2, expires = 1_000), msg("later", 3, expires = 5_000), msg("never", 4)))
        assertEquals(2, dao.purgeExpired(1_000))
        assertEquals(setOf("later", "never"), dao.observe(1).first().map { it.clientMsgId }.toSet())
    }

    @Test
    fun cursorQueriesAndReconciliation() = runBlocking {
        val dao = db.messageDao()
        dao.upsertAll(listOf(msg("a", 10), msg("b", 20), msg("c", 30), msg("local", null, status = MessageStatus.PENDING)))
        assertEquals(10L, dao.oldestServerId(1))
        assertEquals(30L, dao.newestServerId(1))
        assertEquals(listOf(20L, 30L), dao.serverIdsFrom(1, 20).sorted())
        dao.deleteByServerId(20)
        assertNull(dao.getByServerId(20))
        dao.incrementDelivered(30)
        assertEquals(1, dao.get("c")!!.deliveredCount)
    }

    @Test
    fun conversationsSortedByLastActivityAndUnreadCounters() = runBlocking {
        val dao = db.conversationDao()
        dao.upsertAll(listOf(conv(1, last = 100), conv(2, last = 300, unread = 2), conv(3, last = null)))
        assertEquals(listOf(2L, 1L, 3L).take(2), dao.observeAll().first().map { it.id }.take(2))
        dao.incrementUnread(2)
        assertEquals(3, dao.get(2)!!.unreadCount)
        dao.clearUnread(2)
        assertEquals(0, dao.get(2)!!.unreadCount)
        dao.deleteAllExcept(listOf(1))
        assertEquals(listOf(1L), dao.observeAll().first().map { it.id })
    }

    @Test
    fun pendingMessagesSurviveReopeningTheDatabase() = runBlocking {
        val name = "reopen-test.db"
        context.deleteDatabase(name)
        var file = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        file.messageDao().upsert(msg("durable", null, status = MessageStatus.PENDING, text = "queued offline"))
        file.close() // simulates process death

        file = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val pending = file.messageDao().pending()
        assertEquals(listOf("durable"), pending.map { it.clientMsgId })
        assertEquals("queued offline", pending.single().payload)
        file.close()
        context.deleteDatabase(name)
        Unit
    }

    @Test
    fun clearAllTablesWipesLocalCacheOnLogout() = runBlocking {
        db.messageDao().upsert(msg("x", 1))
        db.conversationDao().upsert(conv(1, 1))
        db.clearAllTables()
        assertTrue(db.messageDao().observe(1).first().isEmpty())
        assertTrue(db.conversationDao().observeAll().first().isEmpty())
    }
}

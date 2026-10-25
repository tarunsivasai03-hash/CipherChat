package com.cipherchat.data

import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.MessageDto
import com.cipherchat.core.network.NetworkJson
import com.cipherchat.core.network.SocketAck
import com.cipherchat.core.network.SocketEvent
import com.cipherchat.testing.FakeConnectionConfig
import com.cipherchat.testing.FakeConversationDao
import com.cipherchat.testing.FakeMessageDao
import com.cipherchat.testing.FakeRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val convDao = FakeConversationDao()
    private val msgDao = FakeMessageDao()
    private val realtime = FakeRealtime()
    private lateinit var repo: ChatRepository
    private var clock = 1_000L
    private var idCounter = 0

    /** Behaviour of the fake REST backend, swappable per test. */
    private var restSend: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(500) }
    private val seenCalls = mutableListOf<String>()

    private val ME = 1L
    private val PEER = 2L
    private val CONV = 10L

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.method == "POST" && request.path!!.endsWith("/messages") -> restSend(request)
                    request.method == "POST" && request.path!!.endsWith("/seen") -> { seenCalls += request.path!!; MockResponse().setResponseCode(204) }
                    request.method == "GET" && request.path!!.startsWith("/api/v1/conversations/$CONV/messages") -> pageResponse(request)
                    request.method == "GET" && request.path == "/api/v1/conversations" -> MockResponse().setBody("""{"conversations":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val api = ApiClient(FakeConnectionConfig(server.url("/").toString()))
        repo = ChatRepository(api, convDao, msgDao, realtime, myId = { ME }, scope = scope, now = { clock }, newId = { "client-${++idCounter}".padEnd(10, '0') })
        runBlockingTest { convDao.upsert(conversation()) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun runBlockingTest(block: suspend () -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private fun conversation(unread: Int = 0) = ConversationEntity(
        id = CONV, type = "direct", name = null, membersJson = "[]", disappearSeconds = null, unreadCount = unread,
        lastMessageClientId = null, lastMessageSenderId = null, lastMessageKind = null, lastMessagePayload = null, lastMessageAt = null, createdAt = 0,
    )

    private fun dto(id: Long, clientId: String, sender: Long = ME, text: String = "hi", delivered: List<Long> = emptyList()) = MessageDto(
        id = id, conversationId = CONV, senderId = sender, kind = "plain", payload = text, clientMsgId = clientId,
        createdAt = "2026-01-01T00:00:%02dZ".format(id.toInt() % 60), deliveredTo = delivered,
    )

    private fun created(m: MessageDto) = MockResponse().setResponseCode(201).setBody("""{"message":${NetworkJson.encodeToString(m)}}""")

    private var pageMessages: List<MessageDto> = emptyList()
    private var pageHasMore = false
    private fun pageResponse(r: RecordedRequest) =
        MockResponse().setBody("""{"messages":${NetworkJson.encodeToString(pageMessages)},"has_more":$pageHasMore,"next_before":null}""")

    private suspend fun awaitMessage(predicate: (List<MessageEntity>) -> Boolean): List<MessageEntity> =
        withTimeout(10_000) { repo.messages(CONV).first(predicate) }

    // ---- sending -----------------------------------------------------------------------------------

    @Test
    fun socketAckMakesMessageSentAndSkipsRest() = runBlockingTest {
        realtime.ackProvider = { req -> SocketAck.Accepted(dto(100, req.clientMsgId, text = req.payload)) }
        val id = repo.send(CONV, "hello", "plain", null, null)
        val list = awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        assertEquals(id, list.single().clientMsgId)
        assertEquals(100L, list.single().serverId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pendingRowIsWrittenBeforeAnyNetworkAndRestFallbackConfirmsIt() = runBlockingTest {
        restSend = { req ->
            // At this moment the message must already be durable locally as PENDING.
            val local = runBlocking { msgDao.rows.value.values.single() }
            assertEquals(MessageStatus.PENDING, local.status)
            val clientId = NetworkJson.parseToJsonElement(req.body.readUtf8()).let { (it as kotlinx.serialization.json.JsonObject)["client_msg_id"]!!.toString().trim('"') }
            created(dto(101, clientId))
        }
        repo.send(CONV, "offline-first", "plain", null, null)
        val list = awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        assertEquals(101L, list.single().serverId)
        val req = server.takeRequest()
        assertEquals("Bearer test-token", req.getHeader("Authorization"))
    }

    @Test
    fun networkFailureLeavesMessagePendingThenRetryDeliversExactlyOnce() = runBlockingTest {
        restSend = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) }
        repo.send(CONV, "queued", "plain", null, null)
        // Wait for the failed attempt to finish: the row must stay PENDING, never SENT or FAILED.
        withTimeout(10_000) { while (server.requestCount < 1) kotlinx.coroutines.delay(10) }
        kotlinx.coroutines.delay(100)
        val row = msgDao.rows.value.values.single()
        assertEquals("reason=${row.failureReason}", MessageStatus.PENDING, row.status)

        // Connectivity returns.
        restSend = { req ->
            val body = req.body.readUtf8()
            val clientId = (NetworkJson.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject)["client_msg_id"]!!.toString().trim('"')
            created(dto(102, clientId, text = "queued"))
        }
        repo.retryPending()
        val list = awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        assertEquals(1, list.size)
        assertEquals(102L, list.single().serverId)
        assertTrue(repo.messages(CONV).first().none { it.status == MessageStatus.PENDING })
    }

    @Test
    fun clientErrorMarksFailedWithReasonAndRetryCanRecover() = runBlockingTest {
        restSend = { MockResponse().setResponseCode(403).setBody("""{"error":{"code":"cannot_message","message":"You cannot message this user."}}""") }
        val id = repo.send(CONV, "blocked", "plain", null, null)
        val failed = awaitMessage { it.singleOrNull()?.status == MessageStatus.FAILED }
        assertEquals("You cannot message this user.", failed.single().failureReason)

        restSend = { created(dto(103, id, text = "blocked")) }
        repo.retry(id)
        val ok = awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        assertEquals(103L, ok.single().serverId)
    }

    @Test
    fun socketRejectionIsNotReportedAsSuccess() = runBlockingTest {
        realtime.ackProvider = { SocketAck.Rejected("cannot_message") }
        repo.send(CONV, "x", "plain", null, null)
        val list = awaitMessage { it.singleOrNull()?.status == MessageStatus.FAILED }
        assertNull(list.single().serverId)
    }

    @Test
    fun rateLimitedStaysPendingForLaterRetry() = runBlockingTest {
        realtime.ackProvider = { SocketAck.Rejected("rate_limited") }
        repo.send(CONV, "slow down", "plain", null, null)
        kotlinx.coroutines.delay(100)
        assertEquals(MessageStatus.PENDING, msgDao.rows.value.values.single().status)
    }

    @Test
    fun cipherMessagesKeepKindAndNameButNeverTheKey() = runBlockingTest {
        realtime.ackProvider = { req -> SocketAck.Accepted(dto(104, req.clientMsgId).copy(kind = req.kind, meta = req.meta, payload = req.payload)) }
        repo.send(CONV, "Khoor", "cipher", "caesar", null)
        awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        val req = realtime.sent.single()
        assertEquals("cipher", req.kind)
        assertEquals("caesar", req.meta)
        assertFalse(NetworkJson.encodeToString(req).contains("key"))
    }

    // ---- receiving / dedupe / receipts -----------------------------------------------------------------

    @Test
    fun broadcastOfOwnAckedMessageDoesNotDuplicateOrBumpUnread() = runBlockingTest {
        realtime.ackProvider = { req -> SocketAck.Accepted(dto(105, req.clientMsgId, text = req.payload)) }
        val id = repo.send(CONV, "mine", "plain", null, null)
        awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        repo.start()
        realtime.emit(SocketEvent.NewMessage(dto(105, id, text = "mine")))
        realtime.emit(SocketEvent.NewMessage(dto(105, id, text = "mine")))
        kotlinx.coroutines.delay(150)
        assertEquals(1, msgDao.rows.value.size)
        assertEquals(0, convDao.rows.value.getValue(CONV).unreadCount)
        assertTrue(realtime.delivered.isEmpty()) // never ack our own message
    }

    @Test
    fun incomingMessageIsStoredOnceCountsUnreadOnceAndAcknowledged() = runBlockingTest {
        repo.start()
        val m = dto(200, "peer-msg-0001", sender = PEER, text = "yo")
        realtime.emit(SocketEvent.NewMessage(m))
        realtime.emit(SocketEvent.NewMessage(m)) // duplicate delivery after a reconnect
        withTimeout(5_000) { while (msgDao.rows.value.isEmpty()) kotlinx.coroutines.delay(10) }
        kotlinx.coroutines.delay(150)
        assertEquals(1, msgDao.rows.value.size)
        assertEquals(1, convDao.rows.value.getValue(CONV).unreadCount)
        assertEquals(listOf(200L), realtime.delivered)
        assertEquals("yo", convDao.rows.value.getValue(CONV).lastMessagePayload)
    }

    @Test
    fun incomingMessageInOpenConversationIsMarkedSeenNotUnread() = runBlockingTest {
        repo.start()
        repo.setActiveConversation(CONV)
        assertEquals(listOf(CONV), realtime.joined)
        realtime.emit(SocketEvent.NewMessage(dto(201, "peer-msg-0002", sender = PEER)))
        withTimeout(5_000) { while (seenCalls.isEmpty()) kotlinx.coroutines.delay(10) }
        assertEquals(0, convDao.rows.value.getValue(CONV).unreadCount)
        repo.setActiveConversation(null)
        assertEquals(listOf(CONV), realtime.left)
    }

    @Test
    fun deliveryReceiptsCountOnlyRecipientsAndMarkDelivered() = runBlockingTest {
        realtime.ackProvider = { req -> SocketAck.Accepted(dto(106, req.clientMsgId)) }
        repo.send(CONV, "x", "plain", null, null)
        awaitMessage { it.singleOrNull()?.status == MessageStatus.SENT }
        repo.start()
        realtime.emit(SocketEvent.DeliveryReceipt(CONV, 106, ME)) // own device echo: ignored
        realtime.emit(SocketEvent.DeliveryReceipt(CONV, 106, PEER))
        kotlinx.coroutines.delay(150)
        assertEquals(1, msgDao.rows.value.values.single().deliveredCount)
    }

    @Test
    fun deletionAndReactionEventsUpdateLocalRows() = runBlockingTest {
        msgDao.upsert(dto(300, "keep-me-001").toEntity())
        msgDao.upsert(dto(301, "delete-me-1").toEntity())
        repo.start()
        realtime.emit(SocketEvent.MessageDeleted(CONV, 301))
        realtime.emit(SocketEvent.ReactionChanged(CONV, 300, listOf(com.cipherchat.core.network.ReactionDto(PEER, "👍"))))
        kotlinx.coroutines.delay(150)
        assertEquals(setOf("keep-me-001"), msgDao.rows.value.keys)
        assertTrue(msgDao.rows.value.getValue("keep-me-001").reactionsJson.contains("👍"))
    }

    // ---- history / expiry -----------------------------------------------------------------------------

    @Test
    fun loadLatestReconcilesMessagesDeletedWhileOffline() = runBlockingTest {
        msgDao.upsertAll(listOf(dto(400, "old-aaaaaa1").toEntity(), dto(401, "old-aaaaaa2").toEntity(), dto(402, "old-aaaaaa3").toEntity()))
        pageMessages = listOf(dto(400, "old-aaaaaa1"), dto(402, "old-aaaaaa3")) // 401 was deleted remotely
        pageHasMore = false
        val r = repo.loadLatest(CONV)
        assertTrue(r is com.cipherchat.core.common.AppResult.Success)
        assertEquals(setOf("old-aaaaaa1", "old-aaaaaa3"), msgDao.rows.value.keys)
    }

    @Test
    fun loadOlderUsesOldestKnownServerIdAsCursor() = runBlockingTest {
        msgDao.upsertAll(listOf(dto(500, "page-aaaaaa1").toEntity(), dto(501, "page-aaaaaa2").toEntity()))
        pageMessages = listOf(dto(498, "page-aaaaaa0"), dto(499, "page-aaaaaaz"))
        pageHasMore = true
        val r = repo.loadOlder(CONV)
        assertEquals(true, (r as com.cipherchat.core.common.AppResult.Success).data)
        assertEquals(4, msgDao.rows.value.size)
    }

    @Test
    fun expiredMessagesArePurgedLocally() = runBlockingTest {
        msgDao.upsert(dto(600, "expire-0001").toEntity().copy(expiresAt = 500))
        msgDao.upsert(dto(601, "forever-001").toEntity().copy(expiresAt = null))
        msgDao.upsert(dto(602, "later-00001").toEntity().copy(expiresAt = 9_999_999))
        assertEquals(1, msgDao.purgeExpired(clock))
        assertEquals(setOf("forever-001", "later-00001"), msgDao.rows.value.keys)
    }

    @Test
    fun pendingMessagesSortAfterServerMessages() = runBlockingTest {
        msgDao.upsert(dto(700, "server-0001").toEntity().copy(createdAt = 50))
        repo.send(CONV, "local", "plain", null, null) // stays pending (REST 500)
        msgDao.upsert(dto(701, "server-0002").toEntity().copy(createdAt = 5_000))
        val order = repo.messages(CONV).first().map { it.clientMsgId }
        assertEquals("client-100", order.last())
        assertNotNull(order.firstOrNull { it == "server-0001" })
    }
}

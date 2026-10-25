package com.cipherchat.feature

import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.MemberDto
import com.cipherchat.core.network.UserDto
import com.cipherchat.data.Authenticator
import com.cipherchat.domain.EncryptionIndicator
import com.cipherchat.domain.EncryptionPolicy
import com.cipherchat.domain.cipher.CaesarCipher
import com.cipherchat.domain.cipher.MonoalphabeticCipher
import com.cipherchat.feature.auth.AuthValidation
import com.cipherchat.feature.auth.LoginViewModel
import com.cipherchat.feature.auth.RegisterViewModel
import com.cipherchat.feature.chat.DeliveryLabel
import com.cipherchat.feature.chat.ChatRow
import com.cipherchat.feature.chat.ChatRows
import com.cipherchat.feature.chat.MessageUi
import com.cipherchat.feature.chat.MessageUiMapper
import com.cipherchat.feature.cipher.CipherViewModel
import com.cipherchat.feature.settings.ServerUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuthenticator : Authenticator {
    var loginResult: AppResult<UserDto> = AppResult.Success(user())
    var registerResult: AppResult<UserDto> = AppResult.Success(user())
    val logins = mutableListOf<Pair<String, String>>()
    val registers = mutableListOf<Triple<String, String, String?>>()
    override suspend fun login(username: String, password: String): AppResult<UserDto> { logins += username to password; return loginResult }
    override suspend fun register(username: String, password: String, displayName: String?): AppResult<UserDto> {
        registers += Triple(username, password, displayName); return registerResult
    }
}

private fun user(id: Long = 1, name: String = "alice") = UserDto(id, name, name.replaceFirstChar { it.uppercase() })

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loginRequiresBothFieldsAndDoesNotCallServerOtherwise() = runTest(dispatcher) {
        val auth = FakeAuthenticator()
        val vm = LoginViewModel(auth)
        vm.submit()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertTrue(auth.logins.isEmpty())
    }

    @Test
    fun loginSuccessClearsPasswordAndStopsLoading() = runTest(dispatcher) {
        val auth = FakeAuthenticator()
        val vm = LoginViewModel(auth)
        vm.onUsername("alice"); vm.onPassword("correct-horse")
        vm.submit()
        assertTrue(vm.state.value.loading)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertEquals("", vm.state.value.password)
        assertNull(vm.state.value.error)
        assertEquals(listOf("alice" to "correct-horse"), auth.logins)
    }

    @Test
    fun loginFailureShowsServerMessageAndKeepsPasswordForRetry() = runTest(dispatcher) {
        val auth = FakeAuthenticator().apply { loginResult = AppResult.Failure(AppError(AppError.Kind.UNAUTHORIZED, "Invalid username or password.")) }
        val vm = LoginViewModel(auth)
        vm.onUsername("alice"); vm.onPassword("wrong-password")
        vm.submit(); advanceUntilIdle()
        assertEquals("Invalid username or password.", vm.state.value.error)
        assertEquals("wrong-password", vm.state.value.password)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun doubleSubmitWhileLoadingIsIgnored() = runTest(dispatcher) {
        val auth = FakeAuthenticator()
        val vm = LoginViewModel(auth)
        vm.onUsername("alice"); vm.onPassword("correct-horse")
        vm.submit(); vm.submit()
        advanceUntilIdle()
        assertEquals(1, auth.logins.size)
    }

    @Test
    fun registerValidatesBeforeCallingServer() = runTest(dispatcher) {
        val auth = FakeAuthenticator()
        val vm = RegisterViewModel(auth)
        vm.onUsername("a!"); vm.onPassword("short"); vm.onConfirm("different")
        vm.submit(); advanceUntilIdle()
        val s = vm.state.value
        assertNotNull(s.usernameError); assertNotNull(s.passwordError); assertNotNull(s.confirmError)
        assertTrue(auth.registers.isEmpty())
    }

    @Test
    fun registerSendsTrimmedValidInput() = runTest(dispatcher) {
        val auth = FakeAuthenticator()
        val vm = RegisterViewModel(auth)
        vm.onUsername("new_user"); vm.onDisplayName("New User"); vm.onPassword("a-long-password"); vm.onConfirm("a-long-password")
        vm.submit(); advanceUntilIdle()
        assertEquals(listOf(Triple("new_user", "a-long-password", "New User")), auth.registers)
        assertNull(vm.state.value.error)
    }

    @Test
    fun registerSurfacesUsernameTaken() = runTest(dispatcher) {
        val auth = FakeAuthenticator().apply { registerResult = AppResult.Failure(AppError(AppError.Kind.CONFLICT, "That username or email is not available.")) }
        val vm = RegisterViewModel(auth)
        vm.onUsername("taken_name"); vm.onPassword("a-long-password"); vm.onConfirm("a-long-password")
        vm.submit(); advanceUntilIdle()
        assertEquals("That username or email is not available.", vm.state.value.error)
    }

    @Test
    fun validationRules() {
        assertNotNull(AuthValidation.username(""))
        assertNotNull(AuthValidation.username("ab"))
        assertNotNull(AuthValidation.username("has space"))
        assertNull(AuthValidation.username("ok_name.1"))
        assertNotNull(AuthValidation.password("123456789", "u"))
        assertNotNull(AuthValidation.password("SameAsUser1", "sameasuser1"))
        assertNull(AuthValidation.password("long-enough-pw", "user"))
    }
}

class CipherViewModelTest {
    @Test
    fun encryptDecryptAndSwapFlow() {
        val vm = CipherViewModel()
        vm.onInput("Hello, World!")
        vm.encrypt()
        assertEquals("Khoor, Zruog!", vm.state.value.output)
        vm.swap()
        assertEquals("Khoor, Zruog!", vm.state.value.input)
        assertEquals("", vm.state.value.output)
        vm.decrypt()
        assertEquals("Hello, World!", vm.state.value.output)
        vm.clear()
        assertEquals("", vm.state.value.input + vm.state.value.output)
    }

    @Test
    fun invalidKeyShowsLiveProblemAndBlocksOutput() {
        val vm = CipherViewModel()
        vm.onInput("hi")
        vm.onKey("not-a-number")
        assertNotNull(vm.state.value.keyProblem)
        vm.encrypt()
        assertEquals("", vm.state.value.output)
        assertNotNull(vm.state.value.error)
        vm.onKey("5")
        assertNull(vm.state.value.keyProblem)
    }

    @Test
    fun switchingCipherResetsKeyToItsExampleAndClearsOutput() {
        val vm = CipherViewModel()
        vm.onInput("abc"); vm.encrypt()
        vm.selectCipher(MonoalphabeticCipher)
        assertEquals(MonoalphabeticCipher.exampleKey, vm.state.value.key)
        assertEquals("", vm.state.value.output)
        assertEquals(MonoalphabeticCipher, vm.state.value.cipher)
        vm.selectCipher(CaesarCipher)
        assertEquals("3", vm.state.value.key)
    }

    @Test
    fun emptyInputIsValid() {
        val vm = CipherViewModel()
        vm.encrypt()
        assertEquals("", vm.state.value.output)
        assertNull(vm.state.value.error)
    }
}

class MessageUiMapperTest {
    private fun entity(
        id: String, sender: Long, serverId: Long? = null, status: MessageStatus = MessageStatus.SENT, delivered: Int = 0,
        kind: String = "plain", reply: Long? = null, text: String = "t",
    ) = MessageEntity(id, serverId, 1, sender, kind, text, null, reply, 0, null, status, delivered, "[]")

    private val members = listOf(MemberDto(user(1, "me"), "member"), MemberDto(user(2, "bob"), "member"))

    @Test
    fun deliveryLabelsReflectOnlyEvidence() {
        assertEquals(DeliveryLabel.SENDING, MessageUiMapper.labelFor(entity("a", 1, status = MessageStatus.PENDING)))
        assertEquals(DeliveryLabel.FAILED, MessageUiMapper.labelFor(entity("a", 1, status = MessageStatus.FAILED)))
        assertEquals(DeliveryLabel.SENT, MessageUiMapper.labelFor(entity("a", 1, 5)))
        assertEquals(DeliveryLabel.DELIVERED, MessageUiMapper.labelFor(entity("a", 1, 5, delivered = 1)))
        assertTrue(DeliveryLabel.entries.none { it.name.contains("READ") })
    }

    @Test
    fun mapsOwnershipNamesAndLabelsOnlyForOwnMessages() {
        val ui = MessageUiMapper.map(listOf(entity("m1", 1, 1), entity("m2", 2, 2), entity("m3", 99, 3)), members, myId = 1)
        assertTrue(ui[0].mine && ui[0].label != null && ui[0].senderName == "You")
        assertTrue(!ui[1].mine && ui[1].label == null && ui[1].senderName == "Bob")
        assertEquals("Former member", ui[2].senderName)
    }

    @Test
    fun replyPreviewResolvesLocallyOrFallsBack() {
        val ui = MessageUiMapper.map(
            listOf(entity("m1", 2, 1, text = "original question"), entity("m2", 1, 2, reply = 1), entity("m3", 1, 3, reply = 999)),
            members, 1,
        )
        assertEquals("original question", ui[1].replyPreview)
        assertEquals("Original message", ui[2].replyPreview)
        assertNull(ui[0].replyPreview)
    }

    @Test
    fun encryptionIndicatorNeverClaimsE2e() {
        assertEquals(EncryptionIndicator.E2E_UNAVAILABLE, EncryptionPolicy.forConversation(serverE2eEnabled = false))
        // Even if a server claimed E2E support, this build has no protocol, so it must not show a verified state.
        assertEquals(EncryptionIndicator.E2E_UNAVAILABLE, EncryptionPolicy.forConversation(serverE2eEnabled = true))
        assertEquals(EncryptionIndicator.EDUCATIONAL_CIPHER, EncryptionPolicy.forMessageKind("cipher"))
        assertEquals(EncryptionIndicator.E2E_UNAVAILABLE, EncryptionPolicy.forMessageKind("plain"))
    }
}

class ServerUrlValidatorTest {
    @Test
    fun releaseRulesRequireHttps() {
        assertNull(ServerUrlValidator.validate("https://chat.example.com", allowCleartext = false))
        assertNotNull(ServerUrlValidator.validate("http://chat.example.com", allowCleartext = false))
        assertNotNull(ServerUrlValidator.validate("ftp://x", allowCleartext = false))
        assertNotNull(ServerUrlValidator.validate("", allowCleartext = false))
        assertNotNull(ServerUrlValidator.validate("https://", allowCleartext = false))
        assertNotNull(ServerUrlValidator.validate("chat.example.com", allowCleartext = false))
    }

    @Test
    fun debugAllowsEmulatorHost() {
        assertNull(ServerUrlValidator.validate("http://10.0.2.2:5000/", allowCleartext = true))
        assertNull(ServerUrlValidator.validate("http://192.168.1.20:5000", allowCleartext = true))
    }
}

class ChatRowsTest {
    private val zone = java.time.ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        java.time.LocalDateTime.of(2026, 3, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun msg(id: String, mine: Boolean, t: Long, sender: String = if (mine) "You" else "Bob") = MessageUi(
        id, 1, mine, sender, "plain", null, id, t, null, 0, null, emptyList(), null, null,
    )

    private fun build(vararg m: MessageUi) = ChatRows.build(m.toList(), { "day" }, zone)

    @Test
    fun insertsOneSeparatorPerCalendarDay() {
        val rows = build(msg("a", true, at(1, 10)), msg("b", true, at(1, 11)), msg("c", true, at(2, 9)))
        assertEquals(listOf("Day", "Msg", "Msg", "Day", "Msg"), rows.map { it::class.simpleName })
    }

    @Test
    fun consecutiveMessagesFromOneSenderFormOneRun() {
        val rows = build(msg("a", true, at(1, 10, 0)), msg("b", true, at(1, 10, 2)), msg("c", true, at(1, 10, 4))).filterIsInstance<ChatRow.Msg>()
        assertEquals(listOf(true, false, false), rows.map { it.firstInRun })
        assertEquals(listOf(false, false, true), rows.map { it.lastInRun })
    }

    @Test
    fun senderChangeOrLongPauseStartsANewRun() {
        val rows = build(
            msg("a", true, at(1, 10, 0)), msg("b", false, at(1, 10, 1)), // other sender
            msg("c", false, at(1, 10, 30)), // same sender but a 29-minute gap
        ).filterIsInstance<ChatRow.Msg>()
        assertEquals(listOf(true, true, true), rows.map { it.firstInRun })
        assertEquals(listOf(true, true, true), rows.map { it.lastInRun })
    }

    @Test
    fun keysAreStableAndUnique() {
        val rows = build(msg("a", true, at(1, 10)), msg("b", true, at(2, 10)))
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        assertTrue(ChatRows.build(emptyList(), { "d" }, zone).isEmpty())
    }
}

package com.cipherchat.feature.cipher

import androidx.lifecycle.ViewModel
import com.cipherchat.domain.cipher.CaesarCipher
import com.cipherchat.domain.cipher.Cipher
import com.cipherchat.domain.cipher.CipherOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CipherUiState(
    val cipher: Cipher = CaesarCipher,
    val input: String = "",
    val key: String = CaesarCipher.exampleKey,
    val output: String = "",
    val error: String? = null,
    /** Live key problem, shown before the user presses a button. Null when the key is fine. */
    val keyProblem: String? = null,
)

val ExampleMessages = listOf(
    "Meet me at the library at noon.",
    "The quick brown fox jumps over the lazy dog.",
    "Hello, World! 123",
    "Café déjà vu — naïve 😀",
)

class CipherViewModel : ViewModel() {
    private val _state = MutableStateFlow(CipherUiState())
    val state: StateFlow<CipherUiState> = _state.asStateFlow()

    fun selectCipher(c: Cipher) = _state.update { it.copy(cipher = c, key = c.exampleKey, output = "", error = null, keyProblem = c.validateKey(c.exampleKey)) }
    fun onInput(v: String) = _state.update { it.copy(input = v, error = null) }
    fun onKey(v: String) = _state.update { it.copy(key = v, error = null, keyProblem = it.cipher.validateKey(v)) }
    fun useExample(text: String) = _state.update { it.copy(input = text, output = "", error = null) }

    fun encrypt() = run(encrypt = true)
    fun decrypt() = run(encrypt = false)

    private fun run(encrypt: Boolean) = _state.update { s ->
        val out = if (encrypt) s.cipher.encrypt(s.input, s.key) else s.cipher.decrypt(s.input, s.key)
        when (out) {
            is CipherOutcome.Ok -> s.copy(output = out.text, error = null)
            is CipherOutcome.Error -> s.copy(output = "", error = out.message)
        }
    }

    /** Moves the output into the input so it can be decoded/encoded again. */
    fun swap() = _state.update { if (it.output.isEmpty()) it else it.copy(input = it.output, output = "", error = null) }

    fun clear() = _state.update { it.copy(input = "", output = "", error = null) }
}

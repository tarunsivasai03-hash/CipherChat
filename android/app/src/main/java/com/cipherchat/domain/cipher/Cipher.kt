package com.cipherchat.domain.cipher

/**
 * EDUCATIONAL ciphers only. None of these provide real confidentiality: they are trivially breakable
 * and must never be used as the security mechanism for private chat.
 *
 * Shared Unicode rules (apply to every cipher unless stated otherwise):
 *  - Text is processed by Unicode code point, so surrogate pairs (emoji, rare CJK) are never split.
 *  - Only the characters a cipher explicitly maps are transformed. Everything else (digits, spaces,
 *    punctuation, accented letters, other scripts, emoji) passes through unchanged.
 *  - The empty message is valid and maps to the empty message.
 *  - Latin letters keep their case (A-Z and a-z only; "é" or "ß" are not Latin letters here).
 */
sealed interface CipherOutcome {
    data class Ok(val text: String) : CipherOutcome
    data class Error(val message: String) : CipherOutcome
}

interface Cipher {
    /** Stable identifier, also sent as non-secret message metadata. */
    val id: String
    val displayName: String
    val description: String
    val keyHint: String
    val exampleKey: String
    val requiresKey: Boolean get() = true

    /** Returns a human-readable problem, or null when the key is valid. */
    fun validateKey(key: String): String?
    fun encrypt(text: String, key: String): CipherOutcome
    fun decrypt(text: String, key: String): CipherOutcome
}

internal inline fun Cipher.withValidKey(key: String, block: () -> CipherOutcome): CipherOutcome =
    validateKey(key)?.let { CipherOutcome.Error(it) } ?: block()

internal fun String.codePointList(): List<Int> = codePoints().toArray().toList()

internal fun List<Int>.codePointString(): String {
    val sb = StringBuilder()
    forEach { sb.appendCodePoint(it) }
    return sb.toString()
}

internal fun isLatinUpper(cp: Int) = cp in 'A'.code..'Z'.code
internal fun isLatinLower(cp: Int) = cp in 'a'.code..'z'.code

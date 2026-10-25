package com.cipherchat.domain.cipher

/** Shifts every Latin letter by [shift] positions (mod 26). Key: an integer; negative shifts allowed. */
object CaesarCipher : Cipher {
    override val id = "caesar"
    override val displayName = "Caesar"
    override val description =
        "Shifts each letter a fixed number of places in the alphabet. Only 25 meaningful keys exist, so it is broken instantly."
    override val keyHint = "Whole number, e.g. 3 (negative allowed)"
    override val exampleKey = "3"
    private const val MAX_ABS_SHIFT = 1_000_000

    override fun validateKey(key: String): String? {
        val n = key.trim().toIntOrNull() ?: return "Key must be a whole number."
        return if (kotlin.math.abs(n) > MAX_ABS_SHIFT) "Key is too large (max $MAX_ABS_SHIFT)." else null
    }

    private fun shift(text: String, by: Int): String {
        val s = ((by % 26) + 26) % 26
        val out = StringBuilder()
        for (cp in text.codePointList()) {
            when {
                isLatinUpper(cp) -> out.appendCodePoint('A'.code + (cp - 'A'.code + s) % 26)
                isLatinLower(cp) -> out.appendCodePoint('a'.code + (cp - 'a'.code + s) % 26)
                else -> out.appendCodePoint(cp)
            }
        }
        return out.toString()
    }

    override fun encrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(shift(text, key.trim().toInt())) }
    override fun decrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(shift(text, -key.trim().toInt())) }
}

/** Caesar with a fixed shift of 13. Self-inverse; the key is ignored. */
object Rot13Cipher : Cipher {
    override val id = "rot13"
    override val displayName = "ROT13"
    override val description = "Caesar with a fixed shift of 13. Applying it twice returns the original, and there is no key at all."
    override val keyHint = "No key needed"
    override val exampleKey = ""
    override val requiresKey = false

    override fun validateKey(key: String): String? = null
    override fun encrypt(text: String, key: String) = CaesarCipher.encrypt(text, "13")
    override fun decrypt(text: String, key: String) = CaesarCipher.decrypt(text, "13")
}

/**
 * Vigenere: a repeating-key Caesar. Key: one or more Latin letters. The key position advances only on
 * Latin letters, so spaces/punctuation/other scripts never consume key characters (classic behaviour).
 */
object VigenereCipher : Cipher {
    override val id = "vigenere"
    override val displayName = "Vigenère"
    override val description =
        "Uses a keyword to apply a different Caesar shift to each letter. Stronger than Caesar, but still broken by classical frequency analysis."
    override val keyHint = "Letters A–Z only, e.g. LEMON"
    override val exampleKey = "LEMON"

    override fun validateKey(key: String): String? = when {
        key.isEmpty() -> "Key must not be empty."
        !key.all { it in 'A'..'Z' || it in 'a'..'z' } -> "Key may contain only the letters A–Z."
        else -> null
    }

    private fun apply(text: String, key: String, sign: Int): String {
        val shifts = key.map { it.lowercaseChar() - 'a' }
        var k = 0
        val out = StringBuilder()
        for (cp in text.codePointList()) {
            val base = when {
                isLatinUpper(cp) -> 'A'.code
                isLatinLower(cp) -> 'a'.code
                else -> -1
            }
            if (base < 0) {
                out.appendCodePoint(cp)
            } else {
                val s = sign * shifts[k % shifts.size]
                out.appendCodePoint(base + (((cp - base + s) % 26) + 26) % 26)
                k++
            }
        }
        return out.toString()
    }

    override fun encrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, +1)) }
    override fun decrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, -1)) }
}

package com.cipherchat.domain.cipher

/**
 * Monoalphabetic substitution over the Latin alphabet. Key: a permutation of the 26 letters, given as
 * the cipher alphabet for A..Z in order (case-insensitive, each letter exactly once). Case is preserved.
 */
object MonoalphabeticCipher : Cipher {
    override val id = "substitution"
    override val displayName = "Substitution"
    override val description =
        "Replaces each letter with another according to a 26-letter key. Many keys exist, but letter-frequency analysis breaks it."
    override val keyHint = "All 26 letters once each, e.g. QWERTYUIOPASDFGHJKLZXCVBNM"
    override val exampleKey = "QWERTYUIOPASDFGHJKLZXCVBNM"

    override fun validateKey(key: String): String? {
        val k = key.trim()
        if (k.length != 26) return "Key must be exactly 26 letters (got ${k.length})."
        if (!k.all { it in 'A'..'Z' || it in 'a'..'z' }) return "Key may contain only the letters A–Z."
        if (k.lowercase().toSet().size != 26) return "Every letter must appear exactly once."
        return null
    }

    private fun apply(text: String, key: String, decrypt: Boolean): String {
        val cipherLower = key.trim().lowercase()
        val forward = cipherLower.mapIndexed { i, c -> ('a' + i) to c }.toMap()
        val map = if (decrypt) forward.entries.associate { (p, c) -> c to p } else forward
        val out = StringBuilder()
        for (cp in text.codePointList()) {
            when {
                isLatinLower(cp) -> out.append(map.getValue(cp.toChar()))
                isLatinUpper(cp) -> out.append(map.getValue(cp.toChar().lowercaseChar()).uppercaseChar())
                else -> out.appendCodePoint(cp)
            }
        }
        return out.toString()
    }

    override fun encrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, false)) }
    override fun decrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, true)) }
}

/**
 * Substitution over a user-chosen alphabet (any Unicode characters). Key: "PLAIN|CIPHER" where CIPHER is a
 * permutation of PLAIN's characters (same set, no duplicates). Matching is exact (case-sensitive, no folding);
 * characters outside PLAIN pass through unchanged. Requiring a permutation keeps encrypt/decrypt a bijection.
 */
object CustomAlphabetCipher : Cipher {
    override val id = "custom_alphabet"
    override val displayName = "Custom alphabet"
    override val description =
        "Like Substitution, but you choose the alphabet (any characters). The cipher alphabet must be a rearrangement of the plain alphabet."
    override val keyHint = "PLAIN|CIPHER, e.g. abcde|ecabd"
    override val exampleKey = "abcdefghijklmnopqrstuvwxyz|zyxwvutsrqponmlkjihgfedcba"

    private fun parse(key: String): Pair<List<Int>, List<Int>>? {
        val parts = key.split("|")
        if (parts.size != 2) return null
        return parts[0].codePointList() to parts[1].codePointList()
    }

    override fun validateKey(key: String): String? {
        val (plain, cipher) = parse(key) ?: return "Key must look like PLAIN|CIPHER (exactly one '|')."
        if (plain.isEmpty()) return "Alphabet must not be empty."
        if (plain.size != cipher.size) return "Both alphabets must have the same length (${plain.size} vs ${cipher.size})."
        if (plain.toSet().size != plain.size) return "The plain alphabet contains a repeated character."
        if (cipher.toSet().size != cipher.size) return "The cipher alphabet contains a repeated character."
        if (plain.toSet() != cipher.toSet()) return "The cipher alphabet must use exactly the same characters as the plain alphabet."
        return null
    }

    private fun apply(text: String, key: String, decrypt: Boolean): String {
        val (plain, cipher) = parse(key)!!
        val map = if (decrypt) cipher.zip(plain).toMap() else plain.zip(cipher).toMap()
        return text.codePointList().map { map[it] ?: it }.codePointString()
    }

    override fun encrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, false)) }
    override fun decrypt(text: String, key: String) = withValidKey(key) { CipherOutcome.Ok(apply(text, key, true)) }
}

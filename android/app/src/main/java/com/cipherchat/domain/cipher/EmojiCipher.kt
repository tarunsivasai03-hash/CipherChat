package com.cipherchat.domain.cipher

/** A versioned letter/digit -> emoji table. Changing a published table requires a new version number. */
data class EmojiDictionary(val version: Int, val entries: Map<Int, Int>) {
    companion object {
        /**
         * v1 maps a-z, A-Z, 0-9 (62 symbols, case-sensitive) onto the 62 contiguous animal/nature emoji
         * U+1F400..U+1F43D, in the order digits, upper-case, lower-case. Never edit v1; add v2 instead.
         */
        val V1: EmojiDictionary by lazy {
            val symbols = ('0'..'9') + ('A'..'Z') + ('a'..'z')
            EmojiDictionary(1, symbols.mapIndexed { i, c -> c.code to (0x1F400 + i) }.toMap())
        }
        val BUILT_IN: Map<String, EmojiDictionary> get() = mapOf("v1" to V1)
    }
}

/**
 * Replaces characters with emoji. Key is either a built-in dictionary name ("v1", also used when empty) or a
 * user-defined table of "char=emoji" pairs separated by spaces or commas, e.g. `a=🐶 b=🐱`.
 *
 * Rules: each side is exactly one code point; characters must be unique, emoji must be unique, and an emoji may
 * not also be a mapped character. Unmapped characters pass through. To keep decoding unambiguous, encrypting text
 * that already contains one of the table's emoji is rejected.
 */
object EmojiCipher : Cipher {
    override val id = "emoji"
    override val displayName = "Emoji code"
    override val description =
        "Swaps letters and digits for emoji using a versioned dictionary (v1) or your own table. Fun, but it is just a lookup table."
    override val keyHint = "v1, or your own pairs like a=🐶 b=🐱"
    override val exampleKey = "v1"
    override val requiresKey = false

    fun parseTable(key: String): Result<Map<Int, Int>> {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return Result.success(EmojiDictionary.V1.entries)
        EmojiDictionary.BUILT_IN[trimmed.lowercase()]?.let { return Result.success(it.entries) }
        val table = LinkedHashMap<Int, Int>()
        for (pair in trimmed.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }) {
            val sides = pair.split("=")
            if (sides.size != 2) return Result.failure(IllegalArgumentException("Bad pair \"$pair\"; use char=emoji."))
            val (from, to) = sides[0].codePointList() to sides[1].codePointList()
            if (from.size != 1 || to.size != 1) {
                return Result.failure(IllegalArgumentException("Each side of \"$pair\" must be a single character."))
            }
            if (table.containsKey(from[0])) return Result.failure(IllegalArgumentException("Character repeated in table."))
            table[from[0]] = to[0]
        }
        if (table.values.toSet().size != table.size) return Result.failure(IllegalArgumentException("Two characters share the same emoji."))
        if (table.values.any { table.containsKey(it) }) {
            return Result.failure(IllegalArgumentException("An emoji in the table is also a mapped character."))
        }
        return Result.success(table)
    }

    override fun validateKey(key: String): String? = parseTable(key).exceptionOrNull()?.message

    override fun encrypt(text: String, key: String): CipherOutcome {
        val table = parseTable(key).getOrElse { return CipherOutcome.Error(it.message ?: "Invalid key.") }
        val codePoints = text.codePointList()
        val emoji = table.values.toSet()
        if (codePoints.any { it in emoji }) {
            return CipherOutcome.Error("The text already contains emoji used by this table, which would make decoding ambiguous.")
        }
        return CipherOutcome.Ok(codePoints.map { table[it] ?: it }.codePointString())
    }

    override fun decrypt(text: String, key: String): CipherOutcome {
        val table = parseTable(key).getOrElse { return CipherOutcome.Error(it.message ?: "Invalid key.") }
        val reverse = table.entries.associate { (k, v) -> v to k }
        return CipherOutcome.Ok(text.codePointList().map { reverse[it] ?: it }.codePointString())
    }
}

object CipherRegistry {
    val all: List<Cipher> = listOf(CaesarCipher, VigenereCipher, Rot13Cipher, MonoalphabeticCipher, CustomAlphabetCipher, EmojiCipher)
    fun byId(id: String): Cipher? = all.firstOrNull { it.id == id }
}

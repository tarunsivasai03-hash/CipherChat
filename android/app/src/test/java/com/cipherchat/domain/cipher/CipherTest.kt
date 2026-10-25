package com.cipherchat.domain.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CipherTest {
    private val samples = listOf(
        "",
        "Hello, World!",
        "The quick brown fox jumps over the lazy dog 0123456789",
        "ÀÉÎõü ß çñ — naïve café",
        "日本語のテキスト and Кириллица",
        "emoji 😀🎉 survive 👨‍👩‍👧 sequences",
        "   spaces\tand\nnewlines  ",
        "ZzAa",
    )

    private fun ok(o: CipherOutcome): String = (o as CipherOutcome.Ok).text

    @Test
    fun everyCipherRoundTripsForEverySample() {
        val keys = mapOf(
            "caesar" to listOf("3", "0", "-7", "26", "1000", "-1000000"),
            "vigenere" to listOf("LEMON", "a", "Key"),
            "rot13" to listOf(""),
            "substitution" to listOf("QWERTYUIOPASDFGHJKLZXCVBNM", "zyxwvutsrqponmlkjihgfedcba"),
            "custom_alphabet" to listOf("abc|cab", "éàü日|日üàé", "abcdefghijklmnopqrstuvwxyz|zyxwvutsrqponmlkjihgfedcba"),
            "emoji" to listOf("", "v1", "a=🐶 b=🐱 é=🦊"),
        )
        for (cipher in CipherRegistry.all) {
            for (key in keys.getValue(cipher.id)) {
                assertNull("${cipher.id} key '$key'", cipher.validateKey(key))
                for (s in samples) {
                    val enc = cipher.encrypt(s, key)
                    assertTrue("${cipher.id} encrypt '$s' -> $enc", enc is CipherOutcome.Ok)
                    assertEquals("${cipher.id}/$key/'$s'", s, ok(cipher.decrypt(ok(enc), key)))
                }
            }
        }
    }

    @Test
    fun caesarKnownVectors() {
        assertEquals("Khoor, Zruog!", ok(CaesarCipher.encrypt("Hello, World!", "3")))
        assertEquals("Hello, World!", ok(CaesarCipher.decrypt("Khoor, Zruog!", "3")))
        assertEquals("Ebiil", ok(CaesarCipher.encrypt("Hello", "-3")))
        assertEquals("Hello", ok(CaesarCipher.encrypt("Hello", "26")))
        assertEquals("Héllo 1!", ok(CaesarCipher.encrypt("Héllo 1!", "0"))) // non-ASCII/digits untouched
    }

    @Test
    fun caesarKeyValidation() {
        assertNotNull(CaesarCipher.validateKey(""))
        assertNotNull(CaesarCipher.validateKey("abc"))
        assertNotNull(CaesarCipher.validateKey("1.5"))
        assertNotNull(CaesarCipher.validateKey("99999999999"))
        assertNull(CaesarCipher.validateKey(" 5 "))
        assertTrue(CaesarCipher.encrypt("x", "abc") is CipherOutcome.Error)
    }

    @Test
    fun rot13() {
        assertEquals("Uryyb, Jbeyq!", ok(Rot13Cipher.encrypt("Hello, World!", "ignored")))
        val s = "Round trip ÄÖ 😀"
        assertEquals(s, ok(Rot13Cipher.encrypt(ok(Rot13Cipher.encrypt(s, "")), "")))
    }

    @Test
    fun vigenereKnownVectorAndKeyAdvanceRules() {
        assertEquals("LXFOPVEFRNHR", ok(VigenereCipher.encrypt("ATTACKATDAWN", "LEMON")))
        assertEquals("ATTACKATDAWN", ok(VigenereCipher.decrypt("LXFOPVEFRNHR", "lemon")))
        // key does not advance on spaces/punctuation: same letters, same key positions as the unspaced text
        assertEquals("LXFOP VEFRN HR", ok(VigenereCipher.encrypt("ATTAC KATDA WN", "LEMON")))
    }

    @Test
    fun vigenereKeyValidation() {
        assertNotNull(VigenereCipher.validateKey(""))
        assertNotNull(VigenereCipher.validateKey("ab1"))
        assertNotNull(VigenereCipher.validateKey("a b"))
        assertNotNull(VigenereCipher.validateKey("é"))
        assertTrue(VigenereCipher.encrypt("hi", "") is CipherOutcome.Error)
    }

    @Test
    fun substitution() {
        val key = "QWERTYUIOPASDFGHJKLZXCVBNM"
        assertEquals("itssg", ok(MonoalphabeticCipher.encrypt("hello", key)))
        assertEquals("Itssg, Vgksr!", ok(MonoalphabeticCipher.encrypt("Hello, World!", key)))
        assertNotNull(MonoalphabeticCipher.validateKey("ABC"))
        assertNotNull(MonoalphabeticCipher.validateKey("QWERTYUIOPASDFGHJKLZXCVBNN")) // duplicate N, missing M
        assertNotNull(MonoalphabeticCipher.validateKey("QWERTYUIOPASDFGHJKLZXCVBN1"))
        assertNotNull(MonoalphabeticCipher.validateKey("qwertyuiopasdfghjklzxcvbnmQ")) // 27 chars
    }

    @Test
    fun customAlphabet() {
        assertEquals("cab", ok(CustomAlphabetCipher.encrypt("abc", "abc|cab")))
        assertEquals("ABC cab", ok(CustomAlphabetCipher.encrypt("ABC abc", "abc|cab"))) // case-sensitive, others pass through
        assertEquals("日", ok(CustomAlphabetCipher.encrypt("é", "éà日|日éà")))
        listOf("abc", "abc|ab", "abc|abd", "aab|abc", "abc|aab", "a|b|c", "|", "abc|abcd").forEach {
            assertNotNull("'$it' should be invalid", CustomAlphabetCipher.validateKey(it))
        }
    }

    @Test
    fun emojiV1IsStableAndVersioned() {
        assertEquals(1, EmojiDictionary.V1.version)
        assertEquals(62, EmojiDictionary.V1.entries.size)
        assertEquals(62, EmojiDictionary.V1.entries.values.toSet().size)
        // Pin the published table: any edit to v1 breaks old messages, so this must fail loudly.
        assertEquals(0x1F400, EmojiDictionary.V1.entries.getValue('0'.code))
        assertEquals(0x1F40A, EmojiDictionary.V1.entries.getValue('A'.code))
        assertEquals(0x1F424, EmojiDictionary.V1.entries.getValue('a'.code))
        assertEquals(0x1F43D, EmojiDictionary.V1.entries.getValue('z'.code))
        val enc = ok(EmojiCipher.encrypt("Hi 5!", "v1"))
        assertEquals(String(Character.toChars(0x1F40A + 'H'.code - 'A'.code)) + String(Character.toChars(0x1F424 + 8)) + " " + String(Character.toChars(0x1F405)) + "!", enc)
    }

    @Test
    fun emojiCustomTableAndRejections() {
        assertEquals("🐶🐱c", ok(EmojiCipher.encrypt("abc", "a=🐶 b=🐱")))
        assertEquals("ab", ok(EmojiCipher.decrypt("🐶🐱", "a=🐶,b=🐱")))
        assertNotNull(EmojiCipher.validateKey("a=🐶 a=🐱")) // repeated char
        assertNotNull(EmojiCipher.validateKey("a=🐶 b=🐶")) // shared emoji
        assertNotNull(EmojiCipher.validateKey("a=bc")) // multi-char value
        assertNotNull(EmojiCipher.validateKey("a=b b=c")) // emoji that is also a key
        assertNotNull(EmojiCipher.validateKey("nonsense"))
        // ambiguous input is refused instead of silently corrupting the round trip
        assertTrue(EmojiCipher.encrypt("hi 🐶", "a=🐶") is CipherOutcome.Error)
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        val s = "a😀b𝔘c"
        for (c in listOf(CaesarCipher to "5", VigenereCipher to "KEY", MonoalphabeticCipher to "QWERTYUIOPASDFGHJKLZXCVBNM")) {
            val enc = ok(c.first.encrypt(s, c.second))
            assertTrue(enc.contains("😀") && enc.contains("𝔘"))
        }
    }

    @Test
    fun registryHasAllSixCiphers() {
        assertEquals(setOf("caesar", "vigenere", "rot13", "substitution", "custom_alphabet", "emoji"), CipherRegistry.all.map { it.id }.toSet())
        assertEquals(CaesarCipher, CipherRegistry.byId("caesar"))
        assertNull(CipherRegistry.byId("aes"))
    }
}

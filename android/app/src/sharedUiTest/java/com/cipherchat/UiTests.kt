package com.cipherchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cipherchat.core.designsystem.CipherChatTheme
import com.cipherchat.core.designsystem.EncryptionBanner
import com.cipherchat.core.designsystem.Palette
import com.cipherchat.data.ThemeMode
import com.cipherchat.domain.EncryptionIndicator
import com.cipherchat.feature.cipher.CipherTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CipherPlaygroundUiTest {
    @get:Rule val rule = createComposeRule()

    private fun awaitText(text: String) = rule.waitUntil(5_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun caesarEncryptThenDecryptThroughTheUi() {
        rule.setContent { CipherChatTheme { CipherTab() } }
        rule.onNodeWithText("Your text").performTextInput("Hello, World!")
        rule.onNodeWithText("Encrypt").performClick()
        awaitText("Khoor, Zruog!") // the output "decodes" in with a short scramble animation
        rule.onNodeWithText("Khoor, Zruog!").assertIsDisplayed()
        rule.onNodeWithText("Use as input").performClick()
        rule.onNodeWithText("Decrypt").performClick()
        awaitText("Hello, World!")
        rule.onNodeWithText("Hello, World!").assertIsDisplayed()
    }

    @Test
    fun invalidKeyShowsInlineProblem() {
        rule.setContent { CipherChatTheme { CipherTab() } }
        rule.onNodeWithText("Key").performTextClearance()
        rule.onNodeWithText("Key").performTextInput("abc")
        rule.onNodeWithText("Key must be a whole number.").assertIsDisplayed()
        rule.onNodeWithText("Your text").performTextInput("hi")
        rule.onNodeWithText("Encrypt").performClick()
        // Shown both under the key field and under the buttons, and no output is produced.
        rule.onAllNodes(hasText("Key must be a whole number.")).assertCountEquals(2)
        rule.onNodeWithText("Result appears here").assertIsDisplayed()
    }

    @Test
    fun selectingCipherShowsItsExplanationAndEducationalWarning() {
        rule.setContent { CipherChatTheme { CipherTab() } }
        rule.onNodeWithText("Vigenère").performClick()
        rule.onNode(hasText("keyword", substring = true)).assertIsDisplayed()
        rule.onNode(hasText("Educational only", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Educational cipher").assertIsDisplayed()
    }

    @Test
    fun rot13HasNoKeyField() {
        rule.setContent { CipherChatTheme { CipherTab() } }
        rule.onNodeWithText("ROT13").performClick()
        rule.onNodeWithText("This cipher has no key.").assertIsDisplayed()
    }

    @Test
    fun copyAndSwapAreDisabledUntilThereIsOutput() {
        rule.setContent { CipherChatTheme { CipherTab() } }
        rule.onNodeWithText("Copy output").assertIsNotEnabled()
        rule.onNodeWithText("Use as input").assertIsNotEnabled()
        rule.onNodeWithText("Your text").performTextInput("abc")
        rule.onNodeWithText("Encrypt").performClick()
        rule.onNodeWithText("Copy output").assertIsEnabled()
    }
}

@RunWith(AndroidJUnit4::class)
class ThemeAndSecurityIndicatorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun darkAndLightThemesUseTheirOwnBackgrounds() {
        var dark = Color.Unspecified
        var light = Color.Unspecified
        rule.setContent {
            CipherChatTheme(ThemeMode.DARK) { dark = MaterialTheme.colorScheme.background }
            CipherChatTheme(ThemeMode.LIGHT) { light = MaterialTheme.colorScheme.background }
        }
        rule.waitForIdle()
        assertEquals(Palette.Charcoal, dark)
        assertEquals(Palette.Parchment, light)
    }

    @Test
    fun unavailableE2eIsLabelledHonestlyAndNeverAsEncrypted() {
        rule.setContent { CipherChatTheme { EncryptionBanner(EncryptionIndicator.E2E_UNAVAILABLE) } }
        rule.onNodeWithText("Not end-to-end encrypted").assertIsDisplayed()
        rule.onNode(hasText("server can read", substring = true)).assertIsDisplayed()
    }
}

/** Drives the real app (real Application + container) through the first-run flow. No network needed. */
@RunWith(AndroidJUnit4::class)
class FirstRunFlowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun reachAuthScreen() {
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("Create your account")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("Welcome back")).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Skip").performClick()
    }

    @Test
    fun onboardingThenRegisterValidatesLocallyWithoutCallingServer() {
        reachAuthScreen()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("Create your account")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("Welcome back")).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodes(hasText("Welcome back")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("New here? Create an account").performClick()
        }
        rule.onNodeWithText("Create your account").assertIsDisplayed()
        rule.onNodeWithText("Username").performTextInput("x!")
        rule.onNodeWithText("Password").performTextInput("short")
        rule.onNodeWithText("Confirm password").performTextInput("different")
        rule.onNodeWithText("Create account").performClick()
        rule.onNodeWithText("3–32 characters: letters, digits, _ or .").assertIsDisplayed()
        rule.onNodeWithText("Use at least 10 characters.").assertIsDisplayed()
        rule.onNodeWithText("Passwords don't match.").assertIsDisplayed()
    }

    @Test
    fun loginRequiresCredentials() {
        reachAuthScreen()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("Welcome back")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("Already have an account? Sign in")).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodes(hasText("Already have an account? Sign in")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Already have an account? Sign in").performClick()
        }
        rule.onNodeWithText("Sign in").performClick()
        rule.onNodeWithText("Enter your username and password.").assertIsDisplayed()
    }

    @Test
    fun serverSettingsRejectsInsecureAddressesWhereNotAllowedAndBadSchemes() {
        reachAuthScreen()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Server settings")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Server settings").performClick()
        rule.onNodeWithText("Server address").performTextClearance()
        rule.onNodeWithText("Server address").performTextInput("ftp://nope")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Address must start with https://").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertExists()
    }
}

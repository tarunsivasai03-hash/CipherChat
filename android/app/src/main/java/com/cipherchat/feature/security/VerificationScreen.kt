package com.cipherchat.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cipherchat.core.designsystem.EncryptionBanner
import com.cipherchat.core.designsystem.Eyebrow
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.SubScreen
import com.cipherchat.core.designsystem.appear
import com.cipherchat.core.designsystem.glass
import com.cipherchat.domain.EncryptionIndicator

/**
 * Identity verification screen.
 *
 * Safety numbers / fingerprints only mean something when there are real public identity keys to compare.
 * This build has no E2E protocol and therefore no keys, so showing a fingerprint here would be a fabrication.
 * The screen says so plainly instead of displaying a fake code.
 */
@Composable
fun VerificationScreen(onBack: () -> Unit) {
    SubScreen("Security & verification", onBack) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = padding.calculateTopPadding(), bottom = Spacing.xl)) {
            EncryptionBanner(EncryptionIndicator.E2E_UNAVAILABLE)
            Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Contact verification is unavailable",
                    Modifier.padding(top = Spacing.sm).appear(0),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp)),
                )
                Text(
                    "Verifying a contact means comparing a safety number derived from both people's encryption keys. " +
                        "CipherChat has no end-to-end encryption protocol yet, so there are no keys to compare, and we won't show a made-up code.",
                    Modifier.appear(1),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InfoCard("What protects your messages today", "• HTTPS in transit between your phone and the server.\n• Your password is hashed on the server (Argon2id); your session token is encrypted on this device with the Android Keystore.\n• Only members of a conversation can read it.", 2)
                InfoCard("What does not protect them", "• The server operator can read plain and cipher-mode messages.\n• Educational ciphers (Caesar, Vigenère, …) hide text from casual glances only; anyone can break them.\n• Disappearing messages cannot erase screenshots or copies.", 3)
                InfoCard("What the server can see", "Usernames, who is in which conversation, message timing and size, and the content of non-E2E messages.", 4)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, index: Int) {
    Column(Modifier.fillMaxWidth().appear(index).glass(MaterialTheme.shapes.large, GlassLevel.Regular).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Eyebrow(title)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

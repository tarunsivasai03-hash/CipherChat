package com.cipherchat.domain

/**
 * Every security state the UI is allowed to show. Only [EDUCATIONAL_CIPHER] and [E2E_UNAVAILABLE] are
 * reachable in this build, because no end-to-end protocol has been integrated (docs/E2E_STATUS.md).
 * The remaining states exist so the UI contract is fixed, but nothing produces them: a green
 * "verified" badge must never appear without a real verified session behind it.
 */
enum class EncryptionIndicator(val title: String, val explanation: String) {
    EDUCATIONAL_CIPHER(
        "Educational cipher",
        "Text was scrambled with a classroom cipher. This is for fun and learning, not security. The server can read it.",
    ),
    E2E_VERIFIED("End-to-end encrypted · verified", "Reserved. Not produced by this build."),
    E2E_UNVERIFIED("End-to-end encrypted · not verified", "Reserved. Not produced by this build."),
    E2E_PENDING("Setting up encryption…", "Reserved. Not produced by this build."),
    E2E_UNAVAILABLE(
        "Not end-to-end encrypted",
        "Private end-to-end chat isn't available yet. Messages are protected in transit by HTTPS but the server can read them.",
    ),
    VERIFICATION_FAILED("Security verification failed", "Reserved. Not produced by this build."),
}

object EncryptionPolicy {
    /** Indicator for a whole conversation. [serverE2eEnabled] comes from /health and is false in this build. */
    fun forConversation(@Suppress("UNUSED_PARAMETER") serverE2eEnabled: Boolean): EncryptionIndicator =
        EncryptionIndicator.E2E_UNAVAILABLE

    fun forMessageKind(kind: String): EncryptionIndicator = when (kind) {
        "cipher" -> EncryptionIndicator.EDUCATIONAL_CIPHER
        else -> EncryptionIndicator.E2E_UNAVAILABLE
    }
}

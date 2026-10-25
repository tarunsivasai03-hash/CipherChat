# End-to-end encryption: status and decision record

**Status: NOT implemented. Private (E2E) chat is disabled in both the app and the server.**

This build does not claim, display, or simulate end-to-end encryption anywhere. The UI shows
"Not end-to-end encrypted" on every conversation, the server rejects `kind: "e2e"` messages with
`422 e2e_unavailable`, and `/api/v1/health` reports `e2e_enabled: false`. The educational ciphers are a
separate, clearly labelled feature and are **not** used as a security mechanism.

## Protocol evaluated

**Signal Protocol (PQXDH key agreement + Double Ratchet) via the official `libsignal` library**, because for
one-to-one messaging it is the most widely reviewed option and provides: authenticated encryption, forward
secrecy, post-compromise security, replay/tamper protection and safety numbers for identity verification.

Facts checked against primary sources (2026-10-25):

| Question | Finding | Source |
|---|---|---|
| License | **GNU AGPLv3** | github.com/signalapp/libsignal |
| Third-party use | README: "Use outside of Signal is unsupported." APIs "subject to change without notice" (versioned best-effort) | same |
| Android artifact | `org.signal:libsignal-android` (JVM tests: `org.signal:libsignal-client`), served from Signal's own Maven repo; Maven Central copy last updated Nov 2025 (0.86.5) | repo.maven.apache.org metadata |
| Contributions | Require a CLA | same |

## Why it was not integrated

1. **Licensing needs your decision.** AGPLv3 is a strong copyleft licence. Shipping `libsignal` in the app (and
   arguably linking server-side) means the corresponding source for the combined work must be offered under
   AGPLv3. That may be perfectly fine for an open-source project, but it is not a call an implementer should make
   silently, and it must be reviewed by whoever owns the licensing of this project.
2. **No supported-use guarantee.** Signal states external use is unsupported and APIs may change without notice,
   which makes long-term maintenance a real cost to plan for.
3. **Scope.** A correct integration is a subsystem of its own, not a library call:
   * server: per-device identity key, signed pre-key and one-time pre-key bundles, with upload/fetch/replenish
     endpoints, bundle-fetch rate limiting, and one-time-key exhaustion handling;
   * client: persistent `IdentityKeyStore`, `SessionStore`, `PreKeyStore`, `SignedPreKeyStore` with the
     session state encrypted at rest (Android Keystore wraps a database key; the Keystore does **not**
     replace protocol key storage);
   * per-device fan-out (one ciphertext per recipient device), delivery of pre-key messages, out-of-order and
     duplicate handling, session reset, device add/revoke;
   * identity-change UX (block-and-warn on changed keys) and safety-number verification;
   * sealed groups need a separate group protocol (sender keys or MLS); encrypting each message per member is
     **not** automatically a secure group design.
   Shipping any part of this unverified would be worse than not shipping it.
4. **Verification.** The brief requires the properties to be tested, not assumed. That needs two-party session
   tests against the real library plus tampering/replay tests, none of which can be honestly claimed without
   the integration existing.

## Alternatives considered and rejected

* **Google Tink (Apache-2.0)**: excellent primitives (AEAD, HPKE) but it is a crypto toolkit, not a messaging
  protocol. Building a key-agreement + ratchet on top would be "inventing a protocol", which the brief forbids.
* **MLS (RFC 9420)**: the right long-term answer for secure groups, but no mature, maintained Android/Kotlin
  implementation was evaluated or verified here.
* **Self-made scheme (RSA/AES hybrid, etc.)**: rejected outright.

## What is in place to make a later integration safe

* `Message.kind` already reserves `"e2e"` (DB check constraint) and the server refuses it today.
* `Device.identity_public_key` exists (always `NULL` now); no column anywhere can hold a private key
  (asserted by `tests/security/test_authorization.py::test_no_private_key_material_columns`).
* The client has a fixed set of six security indicator states (`EncryptionIndicator`); only
  *Educational cipher* and *Not end-to-end encrypted* are reachable. `EncryptionPolicy.forConversation`
  returns *unavailable* even if a server claims `e2e_enabled`, so a lying or newer server cannot make this
  build show a verified badge.
* The verification screen explains that no safety number exists instead of showing a made-up one.

## To enable E2E later (decision checklist)

1. Decide the project licence (AGPLv3-compatible, or negotiate/choose another protocol).
2. Add pre-key endpoints + per-device ciphertext envelope to the API (new `payload_version`).
3. Implement the client stores on Room + Keystore-wrapped keys.
4. Add two-party tests (session setup, ratchet advance, replay rejection, tamper rejection, identity change).
5. Only then flip the capability on, per conversation, with a real verified-state signal.

## What the server can see today

Usernames, display names, contact graph, conversation membership and group metadata, message timing and size,
and the **plaintext of all `plain` and `cipher` messages** (educational ciphers do not hide content from the server).

# CipherChat architecture

```
CipherChat/
├── android/    Kotlin, Compose, Material 3 (single :app module)
├── backend/    Flask, Flask-SocketIO, SQLAlchemy, Flask-Migrate
└── docs/
```

## Two messaging modes

| | Secret Cipher | Private Chat |
|---|---|---|
| Purpose | Entertainment / learning | Confidential messaging |
| Implementation | Pure Kotlin ciphers (`domain/cipher`) | **Not available** (see `E2E_STATUS.md`) |
| Where it runs | On the device, key never leaves it | n/a |
| Server visibility | Server stores and can read the (coded) text | n/a |
| Labelled as | "Educational cipher" | "Not end-to-end encrypted" |

Cipher output can be sent as a normal message with `kind="cipher"` and `meta=<cipher id>` (never the key).
Receivers decode locally by entering the key.

## Backend

* Application factory (`app/__init__.py`), blueprints under `app/routes`, business logic in `app/services`,
  models in `app/models`, auth/password code in `app/security`, Socket.IO in `app/sockets`.
* **Authentication:** opaque server-side sessions. 256-bit random bearer token; only its SHA-256 is stored
  (`auth_sessions`). Idle TTL 14 days (sliding) and absolute TTL 90 days (configurable); revocable per device;
  logout / device revoke / account delete immediately invalidate HTTP *and* disconnect live sockets. Bearer
  tokens in a header mean no cookies, so CSRF does not apply. There is no refresh endpoint because there is no
  refresh token.
* **Passwords:** Argon2id (`argon2-cffi` defaults). Unknown-user logins burn an equivalent verification so
  response time doesn't reveal account existence; error is the same generic `invalid_credentials`.
* **Authorization:** every conversation-scoped operation goes through `conversation_service.require_member`
  (returns 404 for non-members, so ids can't be probed). Sender id always comes from the session, never the body.
  The same function guards the socket handlers.
* **Realtime:** sockets authenticate in the Socket.IO `connect` handshake (`auth: {token}`), re-validate the
  session on *every* event, join `user:{id}` rooms server-side, and only allow `conv:{id}` room joins after a
  membership check. New messages and receipts are fanned out to per-user rooms; typing uses conversation rooms.
  Per-user sliding-window rate limits on sends and typing.
* **Persistence before ack:** a message is committed to the DB before the sender gets `status: "accepted"` or
  anyone receives it. `accepted` means *durably stored by the server*; **delivered** is only reported after a
  recipient client sends `message_delivered` (stored in `message_receipts`). There are **no read receipts**.
  `last_seen_message_id` is a private per-member unread marker that is never shared.
* **Idempotency:** `UNIQUE(conversation_id, sender_id, client_msg_id)`; retries return the original row.
* **Disappearing messages:** `expires_at` set from the conversation timer at send time. Expired rows are never
  returned, are deleted opportunistically on reads/list calls, and `flask purge-expired` deletes the rest (run it
  from cron).
* **Account deletion:** hard delete of the user and everything it owns (see `auth_service.delete_account`).
* **Single-process realtime:** socket registry and rate windows are in-process, so run one worker (see
  `DEPLOYMENT.md`). Scaling out needs a Socket.IO message queue and shared registry.

### Data model

`users`, `devices`, `auth_sessions`, `contact_requests`, `contacts` (stored in both directions), `blocks`,
`reports`, `conversations` (`direct` | `group`; `direct_key` unique per pair), `conversation_members`
(role, status, joined_at), `messages`, `message_receipts`, `message_reactions`. Foreign keys are enforced
(`PRAGMA foreign_keys=ON`), with unique constraints, check constraints and indexes
(`messages(conversation_id, id)`, `messages(expires_at)`, …). The schema is plain SQLAlchemy and portable to
PostgreSQL. Migrations live in `backend/migrations` and are tested against the models.

Group members only see messages created after they (re)joined.

### What the server can observe

Usernames and display names; optional email (never verified; see below); contact graph; conversation
membership; message timing, size and sender; reactions; delivery receipts; IP addresses in access logs; and
message **content** for all current message kinds.

Email: stored if provided but **no verification flow exists**, `email_verified` is always false and nothing is
sent. The Android UI doesn't collect email.

## Android

* Kotlin, Jetpack Compose, Material 3, MVVM, Repository pattern, Flow/StateFlow, Navigation Compose with typed
  `Dest` routes, Room, DataStore, Retrofit/OkHttp (kotlinx.serialization), `io.socket:socket.io-client`,
  AndroidX Biometric. Hand-wired DI in `AppContainer` (Hilt was not used: the graph is ~10 objects, and it avoids
  a second annotation processor).
* Packages: `core.common` (result/error model), `core.network`, `core.database`, `core.security`,
  `core.designsystem`, `data` (repositories), `domain` (ciphers, encryption policy), `feature.*`, `navigation`.
  Composables contain no networking, crypto or business rules; ViewModels hold immutable `StateFlow` state and
  survive rotation.
* **Single source of truth:** Room. UI observes Room; REST/socket only write to it.
* **Sending:** write `PENDING` row (durable) → try socket ack, fall back to REST (idempotent via
  `client_msg_id`) → on server acceptance the row becomes `SENT`. Network trouble / rate limits / unreadable
  responses keep it `PENDING` (retried on reconnect); only a definitive 4xx rejection marks `FAILED`.
  The room primary key is `clientMsgId`, so the optimistic row, the ack and the broadcast collapse into one row.
* **Reconnect:** Socket.IO auto-reconnect with backoff; on connect the app re-joins open rooms, refreshes
  conversations and retries pending sends. A 401 on an authenticated REST call signs the user out locally (token wiped, cache cleared);
  a refused socket handshake is re-verified over REST first.
* **One subscription:** `ChatRepository.start()` is idempotent and tied to the signed-in session, so navigating
  between screens never creates duplicate socket listeners.
* **Pagination:** newest page on open, older pages via `before=<oldest server id>`; deletions made while
  offline are reconciled on the newest page.

### Local data policy

| Data | Where | Protection |
|---|---|---|
| Session token | DataStore | AES-256-GCM, key in Android Keystore (non-exportable). No plaintext fallback; if Keystore fails, sign-in is refused. |
| Cached user, settings | DataStore | Not secret. |
| Conversation summaries, message text (plain/cipher kinds), pending sends | Room (`cipherchat.db`) | App-private storage only. **Not encrypted at rest** (no SQLCipher). Cleared on logout, session expiry and account deletion. Excluded from cloud backup and device transfer (`allowBackup=false` + extraction rules). Remote content is already server-readable, so this does not add a new exposure class, but a rooted device or forensic image can read it. |
| Cipher keys typed by the user | Memory only | Never persisted, never sent. |
| Private keys | none exist | n/a (no E2E) |

Nothing sensitive is written to logs: no body/header logging is enabled in OkHttp, the server doesn't log
bodies, and SQLAlchemy `hide_parameters` is on.

### Privacy features

* **App lock:** AndroidX BiometricPrompt (strong biometric or device credential), re-engaged whenever the app
  leaves the foreground. A cold-start flash of content before the lock appears is possible (the preference
  is read asynchronously).
* **Block screenshots / hide in recents:** `FLAG_SECURE`.
* **Notifications:** none. No push provider is integrated, so no message content can reach one.
* **Attachments, encrypted backups:** not implemented.

## Not implemented (honest list)

E2E encryption and everything that depends on it (device keys, verification, secure groups, E2E attachments),
push notifications, attachments, encrypted backups, QR contact discovery, email verification, password reset,
password change, report UI (API exists), and refresh tokens (not needed with server-side sessions).

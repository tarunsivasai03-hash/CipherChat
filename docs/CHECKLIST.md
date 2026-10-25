# Implementation checklist (final status)

Legend: ✅ done and verified by tests/commands · 🟡 implemented but only partly verified · ❌ not implemented

## Acceptance criteria

| # | Criterion | Status | Evidence / note |
|---|---|---|---|
| 1 | Android app builds | ✅ | `assembleDebug` and `assembleRelease` (unsigned) succeed |
| 2 | Backend starts | ✅ | dev server started; live HTTP + Socket.IO smoke (register→contact→chat→send→relay; bad token refused) |
| 3 | Register/login/logout | ✅ backend · 🟡 app | backend integration tests; app screens render on emulator and are UI-tested on JVM; a full on-device sign-in against the live server was **not** completed (emulator too slow/flaky on this 7.4 GB host) |
| 4 | Passwords hashed securely | ✅ | Argon2id, tested |
| 5 | Contact management | ✅ backend · 🟡 app | API tests; app logic compiled, not exercised on device |
| 6 | Persistent realtime messages | ✅ backend · ✅ repository logic (fakes) · 🟡 on-device | |
| 7 | Unauthorized access rejected | ✅ | HTTP and socket tests |
| 8 | Educational ciphers + tests | ✅ | 12 cipher tests + 5 UI tests |
| 9 | E2E only after real integration | ✅ (by omission) | E2E **disabled**; see E2E_STATUS.md |
| 10 | Plaintext/private keys don't reach backend | ✅ | no private keys exist; cipher keys never sent (asserted in repository test) — but the server **does** receive plaintext/cipher text by design in this version |
| 11 | Dark/light themes | ✅ | UI test + theme switch in Settings |
| 12 | Local persistence & reconnection | ✅ Room on real SQLite (8 instrumented tests) · ✅ pending/retry logic (JVM) · 🟡 live reconnection not observed on device |
| 13 | Migrations | ✅ | migration test equals models |
| 14 | Automated tests executed | ✅ | see below |
| 15 | Setup instructions accurate | ✅ commands were run; gunicorn/nginx not run |
| 16 | Limitations documented | ✅ | SECURITY.md, E2E_STATUS.md |
| 17 | No placeholder falsely reports success | ✅ | review; verification screen refuses to invent a safety number |

## Test results (as last run)

* Backend: `python -m pytest` → **78 passed** (unit, integration, security, rate-limit, migration, socket tests).
* Android JVM: `./gradlew testDebugUnitTest` → **69 passed** (ciphers, API client w/ MockWebServer, repository behaviour with fakes, ViewModels, mappers, validators, 10 Compose UI tests on Robolectric).
* Android instrumented: `RoomDaoInstrumentedTest` **8/8 passed on an Android 17 emulator**. The same Compose UI tests also exist as instrumented tests (shared source set) but **could not be run on the emulator** (system ANRs from host memory exhaustion); they ran under Robolectric instead. `connectedDebugAndroidTest` via Gradle failed with a UTP result-channel error on API 37; running with `adb shell am instrument` worked for Room.

## Feature status

| Area | Status |
|---|---|
| Screens: splash, onboarding, register, login, home/recents, search, conversation, contacts, requests, groups, cipher playground, verification (honest "unavailable"), profile, privacy/security, devices/sessions, delete account, server settings | ✅ implemented |
| Chat: replies, reactions, delivery states (sending/sent/delivered/failed; no read), connection banner, pagination, typing, cipher composer + local decode | ✅ implemented |
| Disappearing messages (10 s/1 m/1 h/24 h/off) | ✅ server + client purge tested |
| Biometric lock, FLAG_SECURE | 🟡 implemented; not exercised on device |
| E2E, device keys, safety numbers, secure groups, E2E attachments | ❌ (blocked on licence/protocol decision) |
| Push notifications, attachments, encrypted backups, QR contacts | ❌ |
| Email verification, password reset/change, report UI | ❌ |
| Hilt DI | ❌ intentionally manual DI |
| Gradle-run instrumented tests, gunicorn/PostgreSQL verification, dependency audit, R8 release runtime test | ❌ not done |

## Known issues found and fixed during build (for the record)

* Handlers imported after `socketio.init_app` attached to only the first app instance → import order fixed (test-visible).
* A successful-but-unreadable REST response was classified as a permanent failure → now keeps the message pending (idempotent retry).
* "Sign in" link on Register popped the root of the back stack after onboarding → now navigates explicitly.
* JVM trust-store/PyPI TLS interception handled without disabling verification.

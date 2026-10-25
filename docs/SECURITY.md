# Security controls and known gaps

> Automated tests are not a professional security audit. Nothing here has been independently reviewed.

## Implemented and tested (backend: `backend/tests/security`, `integration`)

| Area | Control | Test |
|---|---|---|
| Passwords | Argon2id; never stored/returned/logged in plaintext | `test_security_primitives`, `test_auth::test_password_stored_hashed_only` |
| Sessions | 256-bit random tokens, stored only as SHA-256; idle (sliding) + absolute expiry; revoke on logout/device revoke/account delete | `test_auth` |
| Login | Generic `invalid_credentials`; dummy-verify for unknown users; rate limited per IP **and** per username | `test_auth::test_login_errors_are_generic`, `test_rate_limits` |
| Register/search/contact-request | Rate limits; contact-request responses identical for existing and non-existing users; search needs ≥3 chars, escapes `%`/`_`, honours `discoverable` and blocks | `test_contacts` |
| Authorization | Every conversation/message resource checks active membership; non-members get 404 (cannot probe ids); sender id taken from the session, forged ids ignored; reply targets and reports are scoped to visible messages | `test_authorization`, `test_conversations` |
| Sockets | Token required at handshake; session re-validated on every event; room joins need membership; removed members are evicted from rooms; revocation disconnects; per-user rate limits; payload validation | `test_sockets` |
| Messages | Persist before ack; idempotent `client_msg_id`; size limits; `kind:"e2e"` refused and never stored | `test_conversations`, `test_sockets` |
| Plaintext/key leakage | No column can hold a private key; message payloads and tokens not in logs; SQLAlchemy `hide_parameters`; 500s return a generic body | `test_authorization` |
| Transport | `REQUIRE_HTTPS` enforcement + HSTS; security headers (`nosniff`, `DENY`, `no-referrer`, `no-store`, CSP `default-src 'none'`) | `test_authorization` |
| Config | Production refuses weak/missing `SECRET_KEY`, wildcard CORS, missing DB URL | `test_authorization::test_production_config_validation` |
| Data deletion | Account deletion removes user, sessions, devices, contacts, requests, blocks, messages, direct threads; groups continue and promote an admin | `test_account_deletion` |
| Injection | SQLAlchemy parameterised queries only; no raw SQL with user input | review |
| Migrations | Migration chain builds the same schema as the models | `test_migrations` |

## Implemented on Android

* Session token encrypted with an Android Keystore AES-256-GCM key; sign-in refused if the Keystore fails.
* Release builds are HTTPS-only (no cleartext config); debug builds allow cleartext for dev servers only.
  TLS verification is never disabled.
* `allowBackup=false` and empty backup/transfer rules.
* Optional biometric/device-credential app lock and `FLAG_SECURE`.
* Local cache wiped on logout, session expiry and account deletion.
* Only `INTERNET` and `USE_BIOMETRIC` permissions.
* Honest UI: security indicators come from a fixed enum; verified/encrypted states are unreachable.

## Known gaps and risks

1. **No end-to-end encryption** (see `E2E_STATUS.md`). The server operator and anyone with DB or backup access can read every message.
2. **Local Room DB is not encrypted at rest.** A rooted device/forensic image can read cached messages.
3. **Rate limits are per process and in memory**; the socket registry is per process. One worker only.
4. **No account lockout / CAPTCHA / email verification / password reset / password change.** Brute force is slowed by rate limits only. Users with a forgotten password cannot recover the account.
5. **No refresh-token-style rotation**; a stolen bearer token works until revoked/expired (14 days idle). Revoke via *Devices & sessions*.
6. **Contact enumeration is reduced, not eliminated**: username search (rate limited, opt-out via *Discoverable*) reveals existing usernames; registration reveals taken usernames.
7. **Metadata** (who talks to whom, when, how much) is visible to the server regardless of content protection.
8. **Disappearing messages** rely on server timestamps and the device clock for local purge; they can't remove screenshots, copies or content already read, and a device that is offline keeps its local copy until it next runs the purge.
9. **Cold-start lock flash**: the app-lock preference loads asynchronously, so a brief view of the UI before the lock screen is possible.
10. **Dependencies were not audited** (`pip-audit` / OSV scan not run). Versions pinned by range in `requirements.txt`.
11. The `Origin` check on sockets only matters for browsers; native clients send none. Authentication is by token.
12. Production deployment steps (gunicorn/nginx) were not executed in this environment.
13. Reporting exists in the API but has no moderation tooling and no Android UI.
14. Logs: access logs contain IPs and paths (including user-search queries).

# REST + Socket.IO API (v1)

Base path `/api/v1`. JSON in/out. Authenticated endpoints need `Authorization: Bearer <token>`.
Errors: `{"error": {"code": "<machine_code>", "message": "<human text>"}}`.
Timestamps are UTC ISO-8601 (`2026-01-01T12:00:00Z`). IDs are integers.

Common statuses: `400` validation, `401` missing/invalid/expired/revoked token, `403` forbidden,
`404` not found *or not a member* (deliberately indistinguishable), `409` conflict, `422` unsupported
(`e2e_unavailable`), `429` rate limited (`Retry-After` when available).

## Health

| | |
|---|---|
| `GET /health` | `{status, e2e_enabled:false, api_version}`. 200, or 503 if the DB is unreachable. No auth. |

## Auth

| Endpoint | Body → Response |
|---|---|
| `POST /auth/register` | `{username, password, display_name?, email?, device_name?, platform?}` → 201 `{token, device_id, user}`. Username 3–32 of `[A-Za-z0-9_.]`, case-insensitive unique (409 `username_taken`); password 10–128 chars. 5/min/IP. |
| `POST /auth/login` | `{username, password, device_name?, platform?}` → `{token, device_id, user}`. Generic 401 `invalid_credentials`. 10/min per IP **and** per username. Each login creates a new device/session. |
| `POST /auth/logout` | 204. Revokes this session and disconnects its sockets. |
| `GET /auth/me` | `{user, device_id}` |
| `PATCH /auth/me` | `{display_name?, email?}` → `{user}` |

`/auth/refresh` does not exist: sessions are server-side and slide on use, so there is no refresh token.

## Users and contacts

| Endpoint | Notes |
|---|---|
| `GET /users/search?q=` | ≥3 chars, prefix match on username, max 20, excludes self, non-discoverable users, and users who blocked you. 30/min. |
| `GET /contacts` · `DELETE /contacts/{user_id}` | |
| `GET /contact-requests` | `{incoming:[…], outgoing:[…]}` (pending only) |
| `POST /contact-requests` | `{user_id}` or `{username}` → **always** `202 {"status":"requested"}` whether or not the target exists / is reachable (anti-enumeration). Errors only for self (400), already contacts (409), you blocked them (409). A mutual pending request auto-accepts. 20/hour. |
| `POST /contact-requests/{id}/accept` · `/reject` | Recipient only. |
| `GET /blocks` · `POST /blocks {user_id}` · `DELETE /blocks/{id}` | Blocking removes the contact and pending requests and prevents messaging both ways. `{id}` is the *block* id. |
| `POST /reports` | `{user_id? , message_id?, reason}`; a message can only be reported by someone who can see it. 10/hour. |

## Conversations and messages

| Endpoint | Notes |
|---|---|
| `POST /conversations` | `{type:"direct", user_id}` (must be a contact; idempotent, 201/200) or `{type:"group", name, member_ids}` (members must be your contacts, ≤50). |
| `GET /conversations` | Each item: members, `last_message`, private `unread_count`, `encryption: {e2e:false, state:"unavailable"}`. |
| `GET /conversations/{id}` | Members only. |
| `PATCH /conversations/{id}` | `{name?}` (group admin) and/or `{disappear_after_seconds: 10|60|3600|86400|null}` (direct: either member; group: admin). |
| `POST /conversations/{id}/members` | `{member_ids}` group admin, contacts only. |
| `DELETE /conversations/{id}/members/{user_id}` | Admin removes anyone; any member may remove themselves. Last admin leaving promotes the longest-standing member. |
| `GET /conversations/{id}/messages?limit=&before=` | limit ≤100 (default 50), returns `{messages, has_more, next_before}` oldest→newest; `before` is a message id cursor. Members only see messages from after they joined. |
| `POST /conversations/{id}/messages` | `{kind:"plain"|"cipher", payload, client_msg_id, meta?, reply_to_id?}` → 201 (200 if `client_msg_id` already stored). Payload 1–4000 chars. `cipher` requires `meta` (cipher id; never the key). `kind:"e2e"` → 422. 60/min. |
| `POST /conversations/{id}/seen` | `{message_id}` – advances **your private** unread marker; not a read receipt. |
| `DELETE /messages/{id}` | Sender only; removes for everyone. |
| `PUT/DELETE /messages/{id}/reaction` | `{emoji}` / none. One reaction per user per message. |

Message object: `id, conversation_id, sender_id, kind, payload_version, payload, meta, client_msg_id, reply_to_id, created_at, expires_at, delivered_to:[user_id], reactions:[{user_id, emoji}]`.

## Devices, settings, account

| Endpoint | Notes |
|---|---|
| `GET /devices` | Your devices (one per login) with `current` flag. |
| `POST /devices/{id}/revoke` | Revokes its sessions, disconnects its sockets. Your own devices only. |
| `GET/PATCH /settings` | `{discoverable: bool}` |
| `DELETE /account` | `{password}` → 204. Permanently deletes everything (see ARCHITECTURE.md). |

Not implemented (and therefore not exposed): `POST /devices` (devices are created by login),
`GET /conversations/{id}/verification` (needs E2E keys).

## Socket.IO

Connect with `auth: {"token": "<bearer token>"}`. Invalid/expired/revoked → connection refused (`unauthorized`).
`Origin` is checked against `SOCKETIO_CORS_ORIGINS` when a browser sends one (native clients send none).

Client → server (all acknowledge with `{ok:true,…}` or `{ok:false,error:<code>}`):

| Event | Payload |
|---|---|
| `join_conversation` / `leave_conversation` | `{conversation_id}`; join requires membership. |
| `send_message` | `{conversation_id, kind, payload, client_msg_id, meta?, reply_to_id?}` → `{ok, status:"accepted", created, message}`. "accepted" = durably stored. 60 per minute per user. |
| `message_delivered` | `{message_id}` – recipient reports receipt (not "read"). |
| `typing` | `{conversation_id, is_typing}` → relayed to joined members. |

Server → client: `new_message`, `delivery_receipt {conversation_id, message_id, user_id, delivered_at}`,
`message_deleted`, `reaction_changed`, `typing`, `conversation_updated`, `contacts_changed`, `contact_request`.
The session is re-validated on every client event; revocation disconnects the socket.

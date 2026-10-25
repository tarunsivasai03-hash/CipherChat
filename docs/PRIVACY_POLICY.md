# CipherChat Privacy Policy — DRAFT

> **Draft for the server operator to complete and have reviewed by a lawyer.** It describes what the software in
> this repository actually does. Replace the bracketed placeholders. If you change the software, update this text.

**Operator:** [Your name / organisation] · **Contact:** [privacy contact email] · **Last updated:** [date]

## 1. Important: messages are not end-to-end encrypted

CipherChat's "Secret Cipher" tools (Caesar, Vigenère, ROT13, substitution, emoji codes) are educational and give no real
protection. End-to-end encrypted private chat is **not available** in this version. **The operator's server can
read the content of every message**, including cipher-coded messages. Messages are protected in transit by HTTPS.

## 2. What we collect and why

| Data | Why | Notes |
|---|---|---|
| Username, display name, password | Create and secure your account | Password is stored only as an Argon2id hash. |
| Email (optional, if your client sends it) | Reserved for future account features | Not verified, not used to send mail in this version. The Android app does not ask for it. |
| Contacts, friend requests, blocks | Let you connect with people | |
| Conversations (members, names of groups), messages, reactions, reply links | Provide messaging | Message text (including cipher-coded text) is stored on the server in readable form. |
| Delivery receipts | Show "Delivered" to the sender | There are no read receipts. |
| Private "last seen message" marker | Compute your unread count | Never shared with other users. |
| Device name and session records (created/last-used times) | Let you see and revoke sessions | |
| IP address and request metadata | Security, abuse prevention, rate limiting | In server/proxy access logs; retained per the operator's log policy [define: e.g. 30 days]. |
| Reports you file | Moderation | Reporter, reported user/message, reason. |

No advertising, analytics or third-party tracking SDKs are included. No data is sold. No push-notification
provider is used in this version, so no message content is sent to one.

## 3. On your device

The app stores your session token (encrypted with the Android Keystore), settings, and a local cache of recent
conversations and messages in app-private storage. The cache is **not** encrypted at rest beyond Android's own
device protections. Signing out, an expired session, or deleting your account erases it. The app opts out of
Android cloud backup and device transfer.

## 4. Sharing

Your username and display name are visible to other users who search for you (unless you turn off *Discoverable*)
and to your contacts and conversation members. Members of a conversation see its messages. We disclose data to
third parties only where legally required [operator to confirm], or to hosting providers who run the server under
contract [operator to list].

## 5. Retention and deletion

* **Messages** stay until deleted by the sender, until a disappearing-message timer expires, or until the account or
  conversation is deleted.
* **Disappearing messages** (10 s, 1 min, 1 h, 24 h) are no longer served after the timer and are physically removed
  by the server's cleanup. They cannot remove screenshots, copies, or content already seen on other devices.
* **Account deletion** (Settings → Delete account) permanently deletes your profile, sessions, contacts, requests,
  blocks, and every message you sent; one-to-one chats involving you are deleted for the other person as well; you
  leave groups. Server **backups** made earlier may retain data until they expire [operator: state backup retention].
* **Logs** are kept for [period].

## 6. Your choices and rights

You can edit your display name, hide from search, block users, revoke devices, and delete your account in the app.
Depending on where you live you may have rights to access, correct, export or erase your data or to object to
processing; contact the operator [address] to exercise them. [Operator: add legal bases, controller details, data
transfer information and supervisory-authority contact as required in your jurisdiction.]

## 7. Children

CipherChat is not directed at children under [13/16 — operator to set per jurisdiction].

## 8. Security

We use HTTPS, Argon2id password hashing, revocable sessions, rate limiting and access controls. No system is
perfectly secure and this software has not had an independent security audit.

## 9. Changes

We will update this policy and the "last updated" date when the software or practices change; material changes will
be announced in the app.

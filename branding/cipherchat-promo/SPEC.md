# SPEC — "Meet at 7" (storyline A)

GOAL:    a stranger understands in 20 s that CipherChat lets friends send each other messages in a fun secret code, and remembers the Twin Bubbles mark.
OUTPUT:  HyperFrames project in `./film`, 1080 x 1920, 30 fps, portrait.
TIME:    20 s x 30 = 600 frames. Music 100 BPM, 18 frames per beat (beat n at n*0.6 s).
SAFE:    no text above y 168 or below y 1330 (platform UI). Text >= 28 px.

LOOK:    the app's own themes, rebuilt from its code (`android/.../core/designsystem/Theme.kt`).
  Dark (Maya, Cipher tab, end card): ground #111513 with slow radial colour fields (#14513B @55%, #2C3530 @90%, #2ECC8F @16%);
    text #E4EAE6, muted #B4BFB8, accent #2ECC8F (on accent: #06281C), outgoing bubble #14513B / text #BDF3DC,
    glass = white 7-12% + 1 px border white 24% -> 5%, hairline #3A453F.
  Light (Sam): ground #FAF9F5, blobs #E3DACC / #EADFCB / #2ECC8F @16%; ink #141413, muted #5E5D59, hairline #D1CFC5,
    outgoing bubble ink #141413 / text #FAF9F5, glass white 55-74%, accent for the one highlighted word #0B7A55.
  Type: Roboto (bundled, = Android sans) for UI and titles; serif for message text via @font-face local('Georgia')
    (stand-in for Android's Noto Serif); JetBrains Mono (bundled) for cipher text. Titles 76-84 px Roboto 700, left-aligned at x 72, y 196.
  Radii (px): chips 16, fields 24, bubbles 44 with a 10 px tail corner, panels/sheets 48-56.
  Logo: `branding/logo/masters/cipherchat-symbol.svg` path data (two bubble subpaths, evenodd) and
    `cipherchat-wordmark.svg` (viewBox 1254.7 x 240) recoloured #2ECC8F. Never redrawn by eye.

OBJECTS: chat header (back arrow, square avatar with initials, name, "Not end-to-end encrypted" chip), message bubbles
  (cipher bubbles carry the "Educational cipher" chip + mono text), composer with cipher chips (Caesar, Vigenère, ROT13),
  "Key (never sent)" field, cap toggle and send button; long-press sheet (reactions 👍 ❤️ 😂 😮 🙏, Reply, Copy, Decode…);
  "Decode message" dialog (chips, Key field, Cancel / Decode); Cipher tab (chips, "Your text", Encrypt/Decrypt, OUTPUT panel).
  A fingertip: 76 px translucent circle with a white ring (touch, not a cursor).

FACTS (cipher outputs, from the app's real cipher code):
  Caesar 3: "Meet at 7" -> "Phhw dw 7".  ROT13: "Zrrg ng 7".  Vigenère LEMON: "Xiqh ne 7".
  Emoji code v1: "🐖🐨🐨🐷 🐤🐷 🐇" (M=U+1F416, e=U+1F428, t=U+1F437, a=U+1F424, 7=U+1F407).
  Never say secure, private, protected or encrypted. The header chip honestly says "Not end-to-end encrypted".

MOTION:  arrivals expo.out / power4.out; camera and floods power2.inOut; zoom-through and exits power3.in.
  Letter rolls: slot-machine per letter, one letter 0.1 s after the next, each step 0.09 s (Caesar steps through the real shifts).
  Springs only on position/scale (back.out(1.4)). Exits 40-50% shorter than entrances. Landed titles stay still.
  Title entrances vary: per-word blur 6->0 in 0.13 s (T1), per-word grey->ink (T2), whole-line blur 8->0 (T3), slot roll (T4).

SHOTS (absolute seconds):
  1 maya  0.00-5.35  Dark chat. Frame 0: composer filling the lower band (y 900-1320) with "Meet at 7", Caesar chip selected, key 3, cap toggle on.
           T1 "Say it in code" ("code" in emerald) 0.15-2.6. Letters roll to "Phhw dw 7" 0.4-1.6.
           Finger enters 1.8, taps send 2.2-2.35; the coded text flies up into a new green bubble (2.3-2.9) while the list scrolls up 230 px;
           status "Sending…" 2.8 -> "Sent" 3.3 -> "Delivered" 3.9. Whip out left with blur 4.95-5.35 (power3.in).
  2 sam   5.00-13.60 Light chat (Sam's phone), whip in from right 5.0-5.35. Coded bubble arrives 5.45-5.85.
           T2 "Sam gets a puzzle" ("puzzle" #0B7A55) 5.5-7.4. Finger to bubble 7.2-7.6, long-press 7.65-8.25 (bubble scale 0.985).
           Scrim + sheet rise 8.2-8.6 (sheet content y 790-1300). Tap "Decode…" 9.0-9.15; sheet down 9.15-9.4; dialog in 9.35-9.7.
           T3 "Key 3 cracks it" 9.4-11.9. Tap key field 10.05; "3" types 10.3; tap Decode 10.8-10.92; dialog out 10.95-11.2.
           Decoded panel reveals (clip-path) 11.0-11.35; letters roll "Phhw dw 7" -> "Meet at 7" 11.2-12.0 = PAYOFF on beat 20 (12.0 s).
           Hold 12.0-13.05, then zoom through the decoded text (scale 1 -> 3.2, blur up, power3.in) 13.05-13.6.
  3 cipher 13.40-17.00 Dark Cipher tab. "Meet at 7" lands in "Your text" from scale 1.6 + blur (matches the zoom), UI settles 0.25 s.
           Output starts as "Phhw dw 7". T4 "Six ciphers to play with" 13.7-16.4. Finger taps ROT13 13.95, Encrypt 14.25,
           output scrambles to "Zrrg ng 7" 14.4-14.8; montage cut 14.95: Vigenère + "Xiqh ne 7"; cut 15.6: Emoji code (chip row slides left) + emoji output.
           16.3-16.8: everything but the OUTPUT panel blurs out; the panel shrinks into one emerald bubble at the mark's left-bubble position.
  4 end   16.80-20.00 Dark. Mark centred at (540, 760), size 300 px (left bubble centre 507.2,722.5, r 65.6).
           Second bubble swings 0 -> 180 deg about the mark centre 16.8-17.4 (lands on beat 29), path recomputed per frame so the overlap hole opens.
           Wordmark (560 px wide, y 900) wipes in left->right 17.4-17.8. Tagline "Chat in code. Just for fun." ("code" emerald) 54 px, y 1060,
           per word 17.7-18.0, held to 20.0 (2.0 s).

SOUND:   music synthesised in code (pure Python `wave`, no new deps) to `film/assets/music.wav`: soft pad + plucked minor arpeggio from 0 s,
  kick + bass enter at the payoff (12.0 s), riser 15.8-17.4, impact at 17.4. Effects only on visible actions: soft ticks on letter landings
  (0.4-1.6, 11.2-12.0) and the typed "3" (10.3), whoosh on send (2.3) and a second take on the whip (4.95), pop on bubble arrival (5.48),
  light clicks on taps (2.25, 9.05, 10.05, 10.85, 13.95, 14.25). Mix to about -14 LUFS, peaks <= -1 dBTP (`hyperframes normalize-audio`, `audio --json`).

BANNED:  the defaults in `spec.md`, plus: invented UI or labels the app does not have; any security claim; centred-everything title layouts;
  the logo before the end card; a phone frame around the app.

QA (snapshot --at): 0.0 (thumbnail: composer + rolling letters), 1.6, 2.6, 3.9, 5.2 (whip), 6.0, 8.4 (sheet), 10.3 (dialog with 3),
  12.0 (payoff), 13.4 (zoom seam), 14.6, 15.8, 16.8 (handoff seam), 17.4 (mark lands), 19.9 (end card held).

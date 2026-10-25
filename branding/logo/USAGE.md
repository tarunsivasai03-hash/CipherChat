# CipherChat logo: usage guide

Source of truth: `tools/build_logo.py` generates every master as plain filled SVG paths (no fonts, no strokes, no live text).

## The mark
Two speech bubbles. Each is a circle with one sharp corner (the tail); the second is the first rotated 180° about one point, which is the ROT13 idea (apply it twice, get yourself back). Where they overlap there is a real hole. The wordmark's `i` dot is the same bubble.

## Colour
| Name | HEX | RGB | CMYK (approx.) | Use |
|---|---|---|---|---|
| Emerald | `#2ECC8F` | 46, 204, 143 | 77/0/30/20 | Mark and wordmark on dark backgrounds; app icon foreground |
| Deep Emerald | `#0B7A55` | 11, 122, 85 | 91/0/30/52 | Mark and wordmark on light backgrounds |
| Charcoal | `#111513` | 17, 21, 19 | 19/0/10/92 | Dark backgrounds, app-icon tile, one-colour black alternative |
| Mist | `#F5F8F6` | 245, 248, 246 | 1/0/1/3 | Light backgrounds |

CMYK values are mathematical conversions for reference only. For print, proof against a physical swatch and pick the nearest spot colour yourself; no Pantone match has been verified.

Contrast (WCAG ratio): Emerald on Charcoal **8.9:1**, Deep Emerald on white **5.3:1**, Deep Emerald on Mist **5.0:1**. **Bright Emerald on white is only 2.1:1: never use it for the mark on light backgrounds.**

Rule of thumb: bright Emerald on dark, Deep Emerald on light, or pure black/white when one colour is mandated.

## Files
| Folder | What |
|---|---|
| `masters/` | `cipherchat-symbol.svg`, `-symbol-small.svg` (small-size cut), `-symbol-reversed.svg` (reversed cut), `-wordmark.svg`, `-horizontal.svg`, `-stacked.svg` (black masters) |
| `symbol/` | black, white, Deep Emerald, Emerald, square, favicon, app-icon SVGs + 512 px PNGs; `…-reversed-mono-2ecc8f.svg` (use on dark) |
| `lockups/` | horizontal and stacked in black, white, Deep Emerald, Emerald (SVG + PNG) |
| `web/` | `favicon.ico`, `favicon.svg`, 16/32/48 PNG, `apple-touch-icon.png`, `icon-192/512.png`, `maskable-512.png`, `site.webmanifest`, `head-snippet.html` |
| `presentation/` | `board.html` + `slides/*.png` |

## Which cut to use
* **Symbol, normal use (32 px and up):** `cipherchat-symbol`.
* **Below 32 px (favicon, notification, tiny UI):** `cipherchat-symbol-small`: bubbles sit closer, so the overlap hole stays open at 16 px.
* **On dark backgrounds:** `cipherchat-symbol-reversed` family. Light shapes look heavier, so this cut opens the hole slightly.
* **Lockups:** horizontal for headers and documents; stacked for store listings, social avatars with a name, splash screens.

## Clear space and minimum size
* Let **x = the radius of one bubble** (about 32% of the symbol's height). Keep at least **x** free on every side of any logo version.
* Symbol: 16 px minimum (small cut), 32 px for the normal cut. Horizontal lockup: 24 px tall minimum. Stacked lockup: 64 px wide minimum.

## Do
* Use only the supplied files. Keep proportions locked.
* Use one colour at a time (mark and wordmark share a colour).
* Place the mark on solid backgrounds with sufficient contrast.

## Don't
* Recolour the mark with bright Emerald on white or light grey.
* Stretch, rotate, outline, add shadows, gradients or glow.
* Re-typeset the name in a font; use the wordmark file.
* Separate the two bubbles, change the overlap, or flip a single bubble.
* Put the mark on busy photos without a solid plate.
* Add a padlock, shield or "encrypted" claim to the logo: the product is honest about not offering end-to-end encryption yet, and the identity must not imply it.

## Honest limits
* The wordmark is a custom geometric construction made for this project; it is not a licensed typeface and has had only a first optical pass (no kerning review by a type designer).
* No trademark or domain clearance has been done: have a professional run searches before launch, including reverse image search for the symbol.
* Print production (CMYK proofing, embroidery, die-cutting) is untested; no PDF/EPS/AI files are supplied (SVG masters only).
* Sample names, e-mail and URLs inside `presentation/` mockups are placeholders.

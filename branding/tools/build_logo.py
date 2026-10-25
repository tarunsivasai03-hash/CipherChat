"""Generates the CipherChat logo masters as plain filled SVG paths (no <text>, no strokes, no fonts).

Run:  python tools/build_logo.py     (writes into ../logo/masters)

Geometry
  Symbol : two speech bubbles. Each is a circle (r=56) with one sharp corner (the "tail"); the second is the first
           rotated 180 degrees about the canvas centre (128,128), the ROT13 idea: apply it twice, get yourself.
           The overlap is a real hole (fill-rule evenodd).
  Wordmark: geometric monoline sans built from rects, annuli and annular sectors. Stroke 26, x-height 120,
           ascender 176, cap height 168. The dot of the i is the bubble from the symbol.
"""
import math
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "logo", "masters")
os.makedirs(OUT, exist_ok=True)


def f(n):
    s = f"{n:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ----------------------------------------------------------------------------------------------- symbol
def bubble(cx, cy, r, rot180=False, pivot=(128, 128)):
    """Circle with a sharp bottom-left corner. All drawn clockwise. rot180 rotates about pivot."""
    def P(x, y):
        return (2 * pivot[0] - x, 2 * pivot[1] - y) if rot180 else (x, y)

    bl, l, t, rr, b = P(cx - r, cy + r), P(cx - r, cy), P(cx, cy - r), P(cx + r, cy), P(cx, cy + r)
    a = lambda p: f"{f(p[0])},{f(p[1])}"
    return (f"M{a(bl)} L{a(l)} A{r},{r} 0 0 1 {a(t)} A{r},{r} 0 0 1 {a(rr)} A{r},{r} 0 0 1 {a(b)} Z")


def symbol_path(d=85.0, r=56, dx=0.0, dy=0.0, scale=1.0):
    """Two bubbles whose centres are `d` apart on the (56,64) diagonal, centred on (128+dx,128+dy)."""
    d, r = d * scale, r * scale
    ux, uy = 56 / math.hypot(56, 64), 64 / math.hypot(56, 64)
    c1 = (128 - ux * d / 2 + dx, 128 - uy * d / 2 + dy)
    p = bubble(c1[0], c1[1], r) + " " + bubble(c1[0], c1[1], r, rot180=True, pivot=(128 + dx, 128 + dy))
    return p


def svg(viewbox, body, title, label=None):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" role="img" aria-label="{label or title}">\n'
            f"  <title>{title}</title>\n{body}\n</svg>\n")


def write(name, content):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as fh:
        fh.write(content)


# ----------------------------------------------------------------------------------------------- wordmark
W, XH, ASC, DESC, CAP = 26, 120, 176, 56, 168
RO, RI = XH / 2, XH / 2 - W
CRO, CRI = CAP / 2, CAP / 2 - W


class Pen:
    """Accumulates clockwise subpaths in absolute coordinates (with scale + offset)."""

    def __init__(self, ox, oy, s):
        self.ox, self.oy, self.s, self.d = ox, oy, s, []

    def p(self, x, y):
        return f"{f(self.ox + x * self.s)},{f(self.oy + y * self.s)}"

    def r(self, v):
        return f(v * self.s)

    def rect(self, x, y, w, h):
        self.d.append(f"M{self.p(x, y)} L{self.p(x + w, y)} L{self.p(x + w, y + h)} L{self.p(x, y + h)} Z")

    def sector(self, cx, cy, ro, ri, a0, a1):
        pt = lambda r, a: (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
        large = 1 if (a1 - a0) > 180 else 0
        self.d.append(
            f"M{self.p(*pt(ro, a0))} A{self.r(ro)},{self.r(ro)} 0 {large} 1 {self.p(*pt(ro, a1))} "
            f"L{self.p(*pt(ri, a1))} A{self.r(ri)},{self.r(ri)} 0 {large} 0 {self.p(*pt(ri, a0))} Z")

    def ring(self, cx, cy, ro, ri):
        self.d.append(f"M{self.p(cx + ro, cy)} A{self.r(ro)},{self.r(ro)} 0 1 1 {self.p(cx - ro, cy)} "
                      f"A{self.r(ro)},{self.r(ro)} 0 1 1 {self.p(cx + ro, cy)} Z "
                      f"M{self.p(cx + ri, cy)} A{self.r(ri)},{self.r(ri)} 0 1 0 {self.p(cx - ri, cy)} "
                      f"A{self.r(ri)},{self.r(ri)} 0 1 0 {self.p(cx + ri, cy)} Z")

    def bubble(self, x, y, size):
        r = size / 2
        self.d.append(f"M{self.p(x, y + size)} L{self.p(x, y + r)} A{self.r(r)},{self.r(r)} 0 0 1 {self.p(x + r, y)} "
                      f"A{self.r(r)},{self.r(r)} 0 0 1 {self.p(x + size, y + r)} "
                      f"A{self.r(r)},{self.r(r)} 0 0 1 {self.p(x + r, y + size)} Z")


# Each glyph draws at local x=0 on baseline y=0 (up is negative) and returns (advance, left_overhang).
def g_C(p, x):
    p.sector(x + CRO, -CRO, CRO, CRI, 40, 320)
    return CRO + CRO * math.cos(math.radians(40))


def g_i(p, x):
    p.rect(x, -XH, W, XH)
    p.bubble(x + W / 2 - 18, -XH - 18 - 36, 36)  # the dot is the logo's bubble
    return W


def g_p(p, x):
    p.ring(x + RO, -RO, RO, RI)
    p.rect(x, -XH, W, XH + DESC)
    return XH


def g_h(p, x):
    p.rect(x, -ASC, W, ASC)
    p.sector(x + RO, -RO, RO, RI, 180, 360)
    p.rect(x + XH - W, -RO, W, RO)
    return XH


def g_e(p, x):
    cx, cy = x + RO, -RO
    p.sector(cx, cy, RO, RI, 45, 360)
    xo = math.sqrt(RO ** 2 - (W / 2) ** 2)  # crossbar ends flush with the outer circle (no nub)
    p.d.append(f"M{p.p(cx - RI - 1, cy - W / 2)} L{p.p(cx + xo, cy - W / 2)} "
               f"A{p.r(RO)},{p.r(RO)} 0 0 1 {p.p(cx + xo, cy + W / 2)} L{p.p(cx - RI - 1, cy + W / 2)} Z")
    return XH


def g_r(p, x):
    p.rect(x, -XH, W, XH)
    p.sector(x + RO, -RO, RO, RI, 180, 270)
    return RO


def g_a(p, x):
    p.ring(x + RO, -RO, RO, RI)
    p.rect(x + XH - W, -XH, W, XH)
    return XH


def g_t(p, x):
    fo, fi = 48, 22
    p.rect(x, -150, W, 150 - fo)
    p.sector(x + fo, -fo, fo, fi, 90, 180)
    p.rect(x - 18, -XH, 18 + 58, W)
    return fo


WORD = [  # (glyph, gap before the next glyph)
    (g_C, 22), (g_i, 20), (g_p, 14), (g_h, 14), (g_e, 12), (g_r, 30), (g_C, 14), (g_h, 14), (g_a, 34), (g_t, 0),
]


def wordmark(ox, oy, s):
    """Returns (path d, width in canvas units). Baseline at oy; ink starts at ox."""
    p, x = Pen(ox, oy, s), 0.0
    for glyph, gap in WORD:
        adv = glyph(p, x)
        x += adv + gap
    return " ".join(p.d), x * s


def build():
    small_d = 70
    write("cipherchat-symbol.svg", svg("0 0 256 256", f'  <path fill="#000" fill-rule="evenodd" d="{symbol_path()}"/>', "CipherChat symbol", "CipherChat: two speech bubbles rotated 180 degrees about one point"))
    # Small-size cut: bubbles closer (bigger lens that survives 16 px), mark fills more of a tight canvas.
    sm = symbol_path(d=small_d)
    write("cipherchat-symbol-small.svg", svg("32 32 192 192", f'  <path fill="#000" fill-rule="evenodd" d="{sm}"/>', "CipherChat symbol, small-size cut"))
    # Reversed (for dark backgrounds): light shapes look heavier, so open the lens a little.
    write("cipherchat-symbol-reversed.svg", svg("0 0 256 256", f'  <path fill="#fff" fill-rule="evenodd" d="{symbol_path(d=90)}"/>', "CipherChat symbol, reversed cut"))

    wd, ww = wordmark(0, 0, 1.0)
    pad = 10
    # Wordmark alone: tight box with a little padding (ink spans y=-ASC-.. to DESC).
    top, bot = -(ASC + 4), DESC + 4
    wm_body, _ = wordmark(pad, -top, 1.0)
    write("cipherchat-wordmark.svg", svg(f"0 0 {f(ww + 2 * pad + 30)} {f(bot - top)}", f'  <path fill="#000" d="{wm_body}"/>', "CipherChat wordmark"))

    # Horizontal lockup, height 256: symbol on the left, wordmark scaled so the x-height band is centred on the symbol.
    s = 0.88
    sym_dx = -24  # symbol ink spans x 44..212 -> 20..188
    base = 128 + XH * s / 2
    wm, wwid = wordmark(188 + 40, base, s)
    total = 188 + 40 + wwid + 20
    sym = symbol_path(dx=sym_dx)
    write("cipherchat-horizontal.svg", svg(f"0 0 {f(total)} 256", f'  <path fill="#000" fill-rule="evenodd" d="{sym}"/>\n  <path fill="#000" d="{wm}"/>', "CipherChat horizontal logo"))

    # Stacked lockup: symbol (x1.5) centred above, wordmark (x0.62) centred below.
    ws = 0.62
    _, w2 = wordmark(0, 0, ws)
    pad2 = 60
    canvas_w = w2 + 2 * pad2
    sc = 1.5
    sym_h = 176 * sc
    sym_cy = 40 + sym_h / 2
    base2 = 40 + sym_h + 60 + ASC * ws
    wm2, _ = wordmark(pad2, base2, ws)
    sym2 = symbol_path(dx=canvas_w / 2 - 128, dy=sym_cy - 128, scale=sc)
    write("cipherchat-stacked.svg", svg(f"0 0 {f(canvas_w)} {f(base2 + DESC * ws + 40)}", f'  <path fill="#000" fill-rule="evenodd" d="{sym2}"/>\n  <path fill="#000" d="{wm2}"/>', "CipherChat stacked logo"))
    print("wordmark width", round(ww), "| horizontal canvas", round(total), "x 256")


if __name__ == "__main__":
    build()

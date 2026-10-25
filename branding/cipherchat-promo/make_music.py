"""Synthesises the score and effects for the "Meet at 7" film into film/assets/music.wav.

100 BPM (a beat every 0.6 s), A minor. Everything is generated here; no samples.
Run: python make_music.py   then normalise with `hyperframes normalize-audio`.
"""
import os
import wave

import numpy as np

SR = 48000
DUR = 20.0
N = int(SR * DUR)
BEAT = 0.6
BAR = BEAT * 4
rng = np.random.default_rng(7)

L = np.zeros(N)
R = np.zeros(N)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def add(sig, start, gain=1.0, pan=0.0):
    i = int(start * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    lg = gain * np.cos((pan + 1) * np.pi / 4)
    rg = gain * np.sin((pan + 1) * np.pi / 4)
    L[i : i + len(sig)] += sig * lg
    R[i : i + len(sig)] += sig * rg


def tvec(d):
    return np.arange(int(d * SR)) / SR


def lowpass(x, width):
    k = np.ones(width) / width
    return np.convolve(x, k, mode="same")


# Chords per bar: Am F C G | Am F G Am (the last lands on the logo at 16.8 s).
CHORDS = [
    [57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62],
    [57, 60, 64], [53, 57, 60], [55, 59, 62], [57, 60, 64, 71],
]
ROOTS = [45, 41, 36, 43, 45, 41, 43, 45]

# Pad: soft sine stacks, detuned pairs, slow swells that overlap between bars.
for b, chord in enumerate(CHORDS):
    start = b * BAR
    d = BAR + 0.8 if b < 7 else DUR - start
    t = tvec(d)
    env = np.minimum(1, t / 0.6) * np.minimum(1, np.maximum(0, (d - t) / 0.8))
    sig = np.zeros_like(t)
    for n in chord:
        f = midi(n)
        for det in (-0.0018, 0.0018):
            ff = f * (1 + det)
            sig += np.sin(2 * np.pi * ff * t) + 0.25 * np.sin(4 * np.pi * ff * t) + 0.08 * np.sin(6 * np.pi * ff * t)
    sig *= env / (len(chord) * 2)
    add(sig, start, 0.16, -0.25)
    add(sig, start + 0.012, 0.16, 0.25)

# Pluck: eighth-note arpeggio of the chord, an octave up. Quieter under the decode, back for the montage.
for b, chord in enumerate(CHORDS[:7]):
    tones = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[1] + 24]
    for s in range(8):
        at = b * BAR + s * BEAT / 2
        f = midi(tones[s % 4])
        t = tvec(0.6)
        sig = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) + 0.12 * np.sin(6 * np.pi * f * t)) * np.exp(-t / 0.16)
        sig *= np.minimum(1, t / 0.003)
        add(sig, at, 0.09 if s % 2 == 0 else 0.065, -0.35 if s % 2 else 0.35)

# Kick and bass enter on the payoff (10.2 s) and run until the riser takes over.
PAYOFF = 10.2
beat_t = PAYOFF
while beat_t < 15.6 - 1e-6:
    t = tvec(0.45)
    f = 45 + 75 * np.exp(-t / 0.04)
    phase = 2 * np.pi * np.cumsum(f) / SR
    kick = np.sin(phase) * np.exp(-t / 0.16) * np.minimum(1, t / 0.002)
    add(kick, beat_t, 0.55)
    beat_t += BEAT

eighth = PAYOFF
while eighth < 16.2 - 1e-6:
    bar = int(eighth // BAR)
    f = midi(ROOTS[bar])
    t = tvec(0.28)
    env = np.minimum(1, t / 0.005) * np.exp(-t / 0.2)
    sig = np.tanh(2.2 * np.sin(2 * np.pi * f * t)) * env
    add(sig, eighth, 0.16)
    eighth += BEAT / 2

# A bright chord on the decode.
t = tvec(2.2)
bell = np.zeros_like(t)
for n in (69, 72, 76, 81):
    f = midi(n)
    bell += (np.sin(2 * np.pi * f * t) + 0.2 * np.sin(2 * np.pi * 2.76 * f * t) * np.exp(-t / 0.15)) * np.exp(-t / 0.9)
add(bell / 4, PAYOFF, 0.22, 0.1)

# Riser into the logo, 15.6 -> 16.8.
d = 1.2
t = tvec(d)
u = t / d
noise = rng.standard_normal(len(t))
dark = lowpass(noise, 40)
dark /= np.max(np.abs(dark)) + 1e-9
riser = (dark * (1 - u) + noise * 0.35 * u) * u ** 2
sweep = np.sin(2 * np.pi * np.cumsum(220 + 900 * u ** 2) / SR) * u ** 2 * 0.5
add(riser + sweep, 15.6, 0.28)

# Impact on the landing (16.8).
t = tvec(2.4)
boom = np.sin(2 * np.pi * np.cumsum(40 + 50 * np.exp(-t / 0.08)) / SR) * np.exp(-t / 0.7)
burst = lowpass(rng.standard_normal(len(t)), 6) * np.exp(-t / 0.12)
add(boom, 16.8, 0.6)
add(burst, 16.8, 0.25)


# --- Effects, only on things you see happen ---
def whoosh(at, d=0.38, pan_from=-0.6, pan_to=0.6, gain=0.35):
    t = tvec(d)
    u = t / d
    env = np.sin(np.pi * u) ** 2
    n = lowpass(rng.standard_normal(len(t)), 18)
    n /= np.max(np.abs(n)) + 1e-9
    sig = n * env
    half = len(sig) // 2
    add(sig[:half], at, gain, pan_from)
    add(sig[half:], at + half / SR, gain, pan_to)


def click(at, gain=0.22):
    t = tvec(0.03)
    sig = np.sin(2 * np.pi * 2200 * t) * np.exp(-t / 0.004) + 0.3 * rng.standard_normal(len(t)) * np.exp(-t / 0.0015)
    add(sig, at, gain)


def tick(at, gain=0.08, f=3400):
    t = tvec(0.02)
    sig = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.003)
    add(sig, at, gain, 0.2)


def pop(at, gain=0.3):
    t = tvec(0.12)
    f = 300 + 500 * np.exp(-t / 0.02)
    sig = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.03) * np.minimum(1, t / 0.002)
    add(sig, at, gain)


whoosh(0.95)                      # coded text flies into the bubble
whoosh(3.6, 0.36, 0.7, -0.7)      # whip to Sam's phone
pop(4.05)                         # coded bubble arrives
for at in (0.8, 7.0, 8.05, 8.8, 12.15, 12.5, 13.35, 13.65, 14.7, 14.95):
    click(at)
click(8.3, 0.14)                  # the "3" is typed

# Letter landings: the letters of "Meet at 7" (the space and 7 do not move).
for i in (0, 1, 2, 3, 5, 6):
    tick(1.27 + i * 0.07)
    tick(9.67 + i * 0.07, 0.1, 2900)
# Cipher tab outputs settling, softer.
for start, count in ((12.65, 9), (13.8, 9), (15.05, 13)):
    for i in range(count):
        tick(start + 0.16 + i * 0.03, 0.035, 4200)

# Gentle fade at the very end, then write 16-bit stereo.
fade = np.ones(N)
fl = int(0.6 * SR)
fade[-fl:] = np.linspace(1, 0, fl) ** 2
L *= fade
R *= fade
peak = max(np.max(np.abs(L)), np.max(np.abs(R)))
# Make-up gain to about -14 LUFS (measured with ffmpeg loudnorm), with a soft-knee limiter so
# only the few loud transients (the impact) are rounded off, keeping peaks under -1.5 dBFS.
GAIN_DB = 4.2
scale = 0.89 / peak * 10 ** (GAIN_DB / 20)


def limit(x, knee=0.62, ceil=0.84):
    a = np.abs(x)
    over = a > knee
    y = a.copy()
    y[over] = knee + (ceil - knee) * np.tanh((a[over] - knee) / (ceil - knee))
    return np.sign(x) * y


data = np.empty(N * 2, dtype=np.int16)
data[0::2] = np.clip(limit(L * scale) * 32767, -32768, 32767).astype(np.int16)
data[1::2] = np.clip(limit(R * scale) * 32767, -32768, 32767).astype(np.int16)

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "film", "assets", "music.wav")
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(data.tobytes())
print("wrote", out, "peak scale", round(scale, 3))

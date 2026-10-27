"""Synthesised sounds of "Kota yang Hidup" (DESIGN 42.12.6, module 42V-F): the street instruments, the festival sounds and two
new crowd beds, all made from nothing but maths (no recorded sample, no VOICEVOX), plus the tunes they play.

Usage (needs numpy + soundfile with libsndfile's Vorbis encoder, the venv of tools/gen_voices.py):
  python -I tools/gen_kota_hidup_suara.py <repo> [--merge-sounds-json] [--preview <dir>] [--only <name,...>] [--timpa]

Writes
  src/main/resources/assets/kultivator/sounds/kota42/<name>.ogg      mono 22.05 kHz, one file per note / variant
  src/main/resources/assets/kultivator/sounds/ambient/kota/malam_meriah.ogg   stereo 44.1 kHz seamless loop (night district)
  src/main/resources/assets/kultivator/sounds/ambient/kota/upacara.ogg        stereo 44.1 kHz seamless loop (distant suona)
  src/main/resources/data/kultivator/kota/melodi/<id>.json            the tunes of client/dunia/kota42/fx/MelodiJalan
  docs/progres/m2-42V-F-suara.json                                    the sounds.json entries (kept for the finish agent)
With --merge-sounds-json the entries are also merged into sounds.json (every other entry is kept); only the finish agent
(F-END) does that. Re-running rewrites the files; an old file is renamed to <name>.<k>.deactivation first, never deleted.

The notes: pentatonic gong-shang-jue-zhi-yu on D (D E F# A B). Every melodic instrument has 12 notes named by their degree
0..11 (two octaves and two degrees), so a tune is the same list of degrees for every instrument and the client plays
`kultivator:kota42.<instrument>.<degree>`:
  erhu  D4..E6   suona D4..E6   pipa D3..E5   dizi D5..E7
The unpitched ones: gong 0 big / 1 medium / 2 small, kentongan 0-2 (low, middle, high), genderang_kecil (2 variants), balok_kayu
(2), petasan (6 strings of crackers, 0.5-1.4 s), merpati_peluit (the hum of the whistles tied to a pigeon's tail).
The beds are made from the existing ambient/kota loops (read only) plus these instruments, so the town's own crowd stays the
same and only the music and the festival dressing are new.
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

sys.stdout.reconfigure(encoding='utf-8')

SR = 22050
SR2 = 44100
TWO_PI = 2.0 * np.pi
NAMES_DEG = ('gong', 'shang', 'jue', 'zhi', 'yu')
# degree d (0..11) -> semitones above D: pentatonic D E F# A B = 0 2 4 7 9
SEMI = (0, 2, 4, 7, 9)


def semitone(d):
    return SEMI[d % 5] + 12 * (d // 5)


# lowest D of each instrument (Hz): D4 = 293.665
D4 = 293.6648
BASE = {'erhu': D4, 'suona': D4, 'pipa': D4 / 2.0, 'dizi': D4 * 2.0}
NOTE_COUNT = 12


def hz(inst, d):
    return BASE[inst] * 2.0 ** (semitone(d) / 12.0)


# ---------------------------------------------------------------------------------------------------- small DSP kit

rng = np.random.default_rng(4242)
# --timpa: overwrite the files of an earlier run of this script in place (no .deactivation copy); for iterating only
TIMPA = '--timpa' in sys.argv


def tt(sec, sr=SR):
    return np.arange(int(sec * sr)) / sr


def white(n):
    return rng.standard_normal(n)


def spectrum_filter(x, shape, sr=SR):
    """Multiplies the spectrum by shape(freq array): one FFT filter for everything (low/high/band/formants)."""
    n = len(x)
    X = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(n, 1.0 / sr)
    g = shape(f)
    return np.fft.irfft(X * (g[:, None] if x.ndim > 1 else g), n, axis=0)


def lowpass(x, fc, sr=SR, order=2):
    return spectrum_filter(x, lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order)), sr)


def highpass(x, fc, sr=SR, order=2):
    return spectrum_filter(x, lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1.0)) ** (2 * order)), sr)


def bandpass(x, fc, bw_oct=1.0, sr=SR):
    return spectrum_filter(x, lambda f: np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / fc) / (bw_oct / 2.355)) ** 2), sr)


def formants(x, bumps, sr=SR):
    """bumps: [(centre Hz, gain dB, width in octaves)] added on top of a flat response (a body / reed resonance)."""
    def shape(f):
        g = np.ones_like(f)
        lf = np.log2(np.maximum(f, 1.0))
        for fc, db, w in bumps:
            amp = 10 ** (db / 20.0) - 1.0
            g += amp * np.exp(-0.5 * ((lf - np.log2(fc)) / (w / 2.355)) ** 2)
        return g
    return spectrum_filter(x, shape, sr)


def adsr(n, a, d, s, r, sr=SR):
    """Envelope of n samples: attack a s (curved), decay d s to level s, release r s at the end."""
    t = np.arange(n) / sr
    dur = n / sr
    env = np.ones(n)
    at = np.clip(t / max(a, 1e-4), 0.0, 1.0)
    env = at ** 1.6
    dec = s + (1.0 - s) * np.exp(-np.maximum(t - a, 0.0) / max(d, 1e-4))
    env = env * dec
    rel = np.clip((dur - t) / max(r, 1e-4), 0.0, 1.0)
    return env * rel ** 1.5


def norm(x, peak=0.85):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 1e-9 else x


def fade_edges(x, ms_in=2.0, ms_out=10.0, sr=SR):
    x = x.copy()
    a = max(1, int(sr * ms_in / 1000))
    b = max(1, int(sr * ms_out / 1000))
    x[:a] *= np.linspace(0, 1, a)
    x[-b:] *= np.linspace(1, 0, b)
    return x


def echo(x, taps, sr=SR):
    """Slapback off the walls of a street: taps [(seconds, gain, lowpass Hz)]."""
    out = np.concatenate([x, np.zeros(int(sr * max(t for t, _, _ in taps)) + 8)])
    for sec, g, lp in taps:
        d = lowpass(x, lp, sr)
        i = int(sec * sr)
        out[i:i + len(x)] += d * g
    return out


def room_ir(sec, rt60, tone, seed, sr=SR):
    r = np.random.default_rng(seed)
    n = int(sec * sr)
    t = np.arange(n) / sr
    ir = lowpass(r.standard_normal(n) * np.exp(-6.91 * t / rt60), tone, sr)
    ir[:int(0.003 * sr)] *= np.linspace(0, 1, int(0.003 * sr))
    return ir / np.sqrt(np.sum(ir ** 2))


def convolve(x, h):
    n = len(x) + len(h) - 1
    m = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(h, m), m)[:n]


def partials(f0, ratios, amps, taus, dur, sr=SR, bloom=None, beat=0.0, glide=None):
    """Additive modal synthesis: partial k = f0*ratio, amplitude amp, exponential decay tau, optional bloom time (a gong's
    slow swell), two detuned copies for the beating shimmer, optional pitch glide (start multiplier, seconds)."""
    t = tt(dur, sr)
    out = np.zeros(len(t))
    for k, (r, a, tau) in enumerate(zip(ratios, amps, taus)):
        f = f0 * r
        if f * (1 + beat * 0.01) > sr * 0.45:
            continue
        env = a * np.exp(-t / tau)
        if bloom:
            env = env * (1.0 - np.exp(-t / (bloom * (0.6 + 0.25 * k))))
        fm = np.full(len(t), f)
        if glide:
            fm = fm * (1.0 + (glide[0] - 1.0) * np.exp(-t / glide[1]))
        ph = TWO_PI * np.cumsum(fm) / sr
        ph0 = rng.uniform(0, TWO_PI)
        sig = np.sin(ph + ph0)
        if beat:
            ph2 = TWO_PI * np.cumsum(fm * (1.0 + beat * 0.001 * (1 + 0.37 * k))) / sr
            sig = 0.6 * sig + 0.4 * np.sin(ph2 + rng.uniform(0, TWO_PI))
        out += env * sig
    return out


# ---------------------------------------------------------------------------------------------------- instruments

def erhu(f, dur=1.55):
    """Bowed spike fiddle: a bright sawtooth with the bow's rosin noise, a slow vibrato that arrives after the attack, a slide up
    into the note, the snakeskin's nasal peak and the small body."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib_depth = np.clip((t - 0.28) / 0.35, 0, 1) * 0.0105  # about 18 cents
    vib = vib_depth * np.sin(TWO_PI * 5.5 * t + 0.4)
    slide = -0.032 * np.exp(-t / 0.055)  # starts a little flat
    fi = f * (1.0 + vib + slide)
    ph = TWO_PI * np.cumsum(fi) / SR
    nh = max(2, int(min(9000.0, SR * 0.45) / f))
    sig = np.zeros(n)
    for k in range(1, nh + 1):
        amp = 1.0 / k ** 0.82
        if k % 7 == 0:
            amp *= 0.5
        sig += amp * np.sin(k * ph + 0.9 * k)
    sig *= 0.26
    bow = highpass(white(n), 1400) * 0.035
    scratch = np.exp(-t / 0.05) * highpass(white(n), 2500) * 0.16
    pressure = 1.0 + 0.07 * np.sin(TWO_PI * 2.7 * t + 1.0) + 0.04 * np.sin(TWO_PI * 7.9 * t)
    sig = (sig + bow * pressure + scratch)
    sig = formants(sig, [(430, 5, 0.9), (1150, 7, 0.7), (2250, 6, 0.55), (3600, 2, 0.8)])
    sig = lowpass(sig, 7600)
    return norm(sig * adsr(n, 0.085, 0.8, 0.88, 0.2) * pressure)


def suona(f, dur=1.25):
    """Double-reed horn: a narrow pulse (all the harmonics), two reeds slightly apart, nasal formants, a little breath and
    the loud flared bell."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = np.clip((t - 0.22) / 0.3, 0, 1) * 0.0045 * np.sin(TWO_PI * 5.2 * t)
    out = np.zeros(n)
    for cents, g in ((-2.8, 0.55), (2.8, 0.45)):
        fi = f * 2 ** (cents / 1200.0) * (1.0 + vib + 0.02 * np.exp(-t / 0.03))
        ph = TWO_PI * np.cumsum(fi) / SR
        nh = max(2, int(min(9000.0, SR * 0.45) / f))
        k = np.arange(1, nh + 1)
        amp = np.abs(np.sin(np.pi * k * 0.2)) / k
        s = np.zeros(n)
        for kk, a in zip(k, amp):
            s += a * np.sin(kk * ph)
        out += g * s
    out *= 0.9
    out += bandpass(white(n), f * 3, 1.6) * 0.07 * (0.4 + np.exp(-t / 0.2))
    out = np.tanh(1.6 * out)
    out = formants(out, [(760, 5, 0.8), (1300, 9, 0.6), (2400, 8, 0.5), (3500, 5, 0.6)])
    out = lowpass(out, 7400)
    return norm(out * adsr(n, 0.022, 0.9, 0.95, 0.09))


def pipa(f, dur=2.1):
    """Plucked lute: modal strings (slightly stretched partials, the highs die first), the plucking point one fifth along the
    string, the nail's click and the wooden belly's thump."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    B = 0.00006
    pluck = 0.19
    tau1 = 1.55 * (293.66 / max(f, 120.0)) ** 0.35
    k = 1
    while True:
        fk = k * f * np.sqrt(1.0 + B * k * k)
        if fk > SR * 0.45 or k > 28:
            break
        amp = abs(np.sin(np.pi * k * pluck)) / (k ** 1.05)
        tau = tau1 / (1.0 + 0.62 * (k - 1)) ** 0.9
        out += amp * np.exp(-t / tau) * np.sin(TWO_PI * fk * t + rng.uniform(0, TWO_PI))
        k += 1
    click = highpass(white(n), 3200) * np.exp(-t / 0.006) * 0.35
    thump = np.sin(TWO_PI * (170 + 0.0 * t) * t) * np.exp(-t / 0.05) * 0.22
    out = out * 0.45 + click + thump
    out = formants(out, [(200, 4, 0.6), (420, 5, 0.6), (1300, 2, 1.0)])
    out = lowpass(out, 6500)
    return norm(fade_edges(out, 0.5, 80.0))


def dizi(f, dur=1.4):
    """Bamboo flute with the membrane: a nearly pure tone with the breath around the third partial, the buzzing shimmer of
    the dimo, a light vibrato after the attack and a soft chiff on the start."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = np.clip((t - 0.3) / 0.4, 0, 1) * 0.0068 * np.sin(TWO_PI * 5.0 * t)
    fi = f * (1.0 + vib + 0.012 * np.exp(-t / 0.04))
    ph = TWO_PI * np.cumsum(fi) / SR
    sig = (np.sin(ph) + 0.34 * np.sin(2 * ph + 0.3) + 0.16 * np.sin(3 * ph + 1.1) + 0.07 * np.sin(4 * ph + 2.0))
    env = adsr(n, 0.055, 0.5, 0.92, 0.14)
    breath = bandpass(white(n), min(f * 3.2, 9000.0), 1.4) * 0.055 * (0.5 + 1.4 * np.exp(-t / 0.09))
    chiff = bandpass(white(n), f * 2.0, 1.0) * np.exp(-t / 0.04) * 0.3
    mem = highpass(white(n), 3300) * lowpass(np.abs(sig), 60) * 0.5
    out = sig * 0.5 + (breath + chiff) * 0.8 + mem * 0.05
    out = formants(out, [(f, 3, 0.4), (f * 2, 2, 0.5)])
    out = lowpass(out, 9500)
    return norm(out * env)


GONG_R = (1.0, 1.19, 1.56, 2.09, 2.37, 2.99, 3.62, 4.38, 5.31, 6.14, 7.2)
GONG_A = (1.0, 0.62, 0.55, 0.5, 0.42, 0.34, 0.26, 0.2, 0.16, 0.11, 0.08)
GONG_T = (3.4, 2.9, 2.5, 2.1, 1.7, 1.35, 1.05, 0.8, 0.6, 0.45, 0.35)


def gong(f0, scale, dur=4.0):
    """A bronze gong: inharmonic partials that swell after the strike (the bloom), each as two slightly detuned copies for the
    shimmering beat, a downward glide at the start and the mallet's thump. scale < 1 shortens the ring (small gong)."""
    taus = [x * scale for x in GONG_T]
    body = partials(f0, GONG_R, GONG_A, taus, dur, bloom=0.045, beat=1.4, glide=(1.035, 0.25))
    t = tt(dur)
    thump = lowpass(white(len(t)), 700) * np.exp(-t / 0.03) * 0.5
    ring = bandpass(white(len(t)), f0 * 4.4, 1.2) * np.exp(-t / (0.35 * scale)) * 0.1
    out = body * 0.5 + thump + ring
    out = lowpass(out, 9500)
    return norm(fade_edges(out, 1.0, 120.0), 0.9)


def kentongan(f, dur=0.55):
    """The wooden slit drum of the night watch: hollow wood, a short 'tok' with two overtones, the knock of the stick."""
    t = tt(dur)
    body = partials(f, (1.0, 2.13, 3.4), (1.0, 0.45, 0.2), (0.16, 0.09, 0.05), dur, glide=(1.03, 0.02))
    knock = lowpass(white(len(t)), 1100) * np.exp(-t / 0.008) * 0.8
    click = highpass(white(len(t)), 2800) * np.exp(-t / 0.003) * 0.25
    out = formants(body * 0.6 + knock + click, [(f * 1.6, 5, 0.7)])
    return norm(fade_edges(out, 0.3, 60.0), 0.88)


def genderang(f0, snap, dur=0.5):
    """The small waist drum: a membrane's inharmonic modes dropping in pitch as it is struck, a snap of the skin."""
    t = tt(dur)
    body = partials(f0, (1.0, 1.59, 2.14, 2.30, 2.65), (1.0, 0.6, 0.4, 0.3, 0.2), (0.13, 0.09, 0.07, 0.06, 0.05), dur, glide=(1.32, 0.03))
    sn = bandpass(white(len(t)), 2600, 1.4) * np.exp(-t / 0.012) * snap
    thud = np.sin(TWO_PI * f0 * 0.5 * t) * np.exp(-t / 0.05) * 0.3
    return norm(fade_edges(body * 0.5 + sn + thud, 0.3, 60.0), 0.9)


def balok(f, dur=0.3):
    """A wooden block: two short bright modes and the click."""
    t = tt(dur)
    body = partials(f, (1.0, 1.52, 2.6), (1.0, 0.55, 0.2), (0.05, 0.034, 0.02), dur)
    click = highpass(white(len(t)), 2500) * np.exp(-t / 0.002) * 0.4
    return norm(fade_edges(body * 0.6 + click, 0.2, 40.0), 0.85)


def pop(strength, sr=SR):
    """One firecracker: a crack (high noise), a body (mid noise), a thump, each with its own very short decay."""
    n = int(0.22 * sr)
    t = np.arange(n) / sr
    crack = highpass(white(n), 1800, sr) * np.exp(-t / 0.0035)
    body = bandpass(white(n), 900, 2.0, sr) * np.exp(-t / 0.012)
    thump = np.sin(TWO_PI * (110 - 55 * np.minimum(t / 0.05, 1.0)) * t) * np.exp(-t / 0.035) * 0.7
    return (crack * 1.0 + body * 0.9 + thump) * strength


def petasan(variant):
    """A string of crackers. variant 0-5 differ in count and rhythm (a big bang and a tail, a fast rattle, an accelerating string,
    slow single pops, a double bang, a long fizzle of tiny pops); a slapback off the house walls closes each one."""
    plans = {
        0: [(0.0, 1.0)] + [(0.28 + 0.07 * i + rng.uniform(0, 0.03), 0.28 + 0.1 * rng.random()) for i in range(7)],
        1: [(0.045 * i + rng.uniform(0, 0.012), 0.5 + 0.4 * rng.random()) for i in range(14)],
        2: [(0.5 * (1 - np.exp(-i * 0.19)) * 1.6 + rng.uniform(0, 0.01), 0.45 + 0.45 * rng.random()) for i in range(16)],
        3: [(0.17 * i + rng.uniform(0, 0.03), 0.6 + 0.35 * rng.random()) for i in range(6)],
        4: [(0.0, 1.0), (0.11, 0.9)] + [(0.34 + 0.06 * i, 0.25 + 0.15 * rng.random()) for i in range(5)],
        5: [(0.03 * i + rng.uniform(0, 0.02), 0.18 + 0.3 * rng.random()) for i in range(26)],
    }[variant]
    total = max(t for t, _ in plans) + 0.3
    out = np.zeros(int(total * SR) + 100)
    for at, s in plans:
        p = pop(s)
        i = int(at * SR)
        out[i:i + len(p)] += p[:len(out) - i]
    out = echo(out, [(0.115, 0.34, 3800), (0.24, 0.18, 2600), (0.38, 0.08, 1900)])
    return norm(fade_edges(out, 0.1, 80.0), 0.92)


def merpati_peluit(dur=2.3):
    """The hum of whistles tied to a pigeon's tail as the flock circles: three reeds slightly apart (the beating hum), the whole
    thing swelling and dipping with the Doppler of the turn, and a thin breath of air."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    dop = 1.0 + 0.045 * np.sin(TWO_PI * 0.55 * t + 1.0)
    for f, g, d in ((1015.0, 1.0, 0.0), (1331.0, 0.7, 7.0), (1782.0, 0.45, 13.0)):
        ph = TWO_PI * np.cumsum((f * dop + d * np.sin(TWO_PI * 0.9 * t)) ) / SR
        out += g * (np.sin(ph) + 0.25 * np.sin(2 * ph + 0.6))
    swell = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.4 * (0.75 + 0.25 * np.sin(TWO_PI * 1.1 * t + 0.5))
    air = bandpass(white(n), 2600, 1.6) * 0.08 * swell
    out = (out * 0.3 + air) * swell
    out = lowpass(highpass(out, 500), 5200)
    return norm(fade_edges(out, 30.0, 150.0), 0.8)


# ---------------------------------------------------------------------------------------------------- tunes

# A tune is a list of voices; a melodic voice is "degree:beats" tokens, "-" a rest. A rhythm voice is a pattern of characters,
# one per `unit` beats: x a hit, X a strong hit, . a rest. Written out by write_tunes() into data/kultivator/kota/melodi/*.json.
def phrase(text):
    out = []
    for tok in text.split():
        if tok.startswith('-'):
            out.append([-1, float(tok[2:]) if ':' in tok else 1.0])
            continue
        d, b = tok.split(':')
        out.append([int(d), float(b)])
    return out


TUNES = [
    {
        'id': 'jembatan', 'nama': 'Lagu di Bawah Jembatan', 'tempo': 66, 'instrumen': 'erhu', 'nuansa': ['jalan', 'sungai', 'pengamen'],
        'cerita': 'Lagu pengamen Ah Qin; tiga kali naik, sekali turun, selalu pulang ke nada gong.',
        'lapis': [{'jenis': 'melodi', 'instrumen': 'erhu', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
            '5:1.5 6:.5 7:1 8:1 9:2 8:1 7:1 8:1.5 7:.5 6:1 5:1 6:3 -:1 '
            '3:1 5:1 6:1 5:1 7:1.5 6:.5 5:1 3:1 4:1 3:1 2:1 0:1 1:2 0:2 '
            '5:1 7:1 8:1.5 9:.5 10:2 9:1 8:1 7:1.5 8:.5 7:1 6:1 5:4 -:2')}],
    },
    {
        'id': 'bulan_sabit', 'nama': 'Bulan Sabit di Atas Genteng', 'tempo': 72, 'instrumen': 'erhu', 'nuansa': ['malam', 'jalan'],
        'cerita': 'Lagu pelan untuk bulan yang baru naik; nadanya meluncur seperti kucing di atap.',
        'lapis': [{'jenis': 'melodi', 'instrumen': 'erhu', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
            '8:2 7:1 6:1 7:1.5 5:.5 6:2 3:1 5:1 6:1 7:1 8:4 '
            '9:1 8:.5 9:.5 10:2 9:1 8:1 7:2 8:1 7:1 6:1 5:1 3:4 -:2 '
            '6:1.5 7:.5 8:2 7:1 6:1 5:2 6:1 5:1 3:2 5:2 6:4 -:2')}],
    },
    {
        'id': 'perahu_pulang', 'nama': 'Perahu Pulang', 'tempo': 90, 'instrumen': 'erhu', 'nuansa': ['sungai', 'pelabuhan'],
        'cerita': 'Tiga ketukan, seperti dayung: lagu nelayan yang pulang membawa ikan perak.',
        'lapis': [{'jenis': 'melodi', 'instrumen': 'erhu', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
            '3:1 5:.5 6:.5 5:1 6:1 7:.5 8:.5 7:1 8:1.5 9:.5 8:1 7:1 6:1 5:1 6:1.5 5:.5 3:1 '
            '5:1 6:.5 7:.5 8:1 9:1 10:.5 9:.5 8:1 7:1.5 6:.5 5:1 6:1 5:.5 3:.5 0:1 3:3 -:3')}],
    },
    {
        'id': 'arak_pengantin', 'nama': 'Arak-arakan Pengantin', 'tempo': 124, 'instrumen': 'suona', 'nuansa': ['nikah', 'upacara'],
        'cerita': 'Suona, genderang kecil dan gong menjemput mempelai; riang, keras, dan tidak peduli tetangga.',
        'lapis': [
            {'jenis': 'melodi', 'instrumen': 'suona', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
                '8:1 8:.5 9:.5 10:1.5 9:.5 8:1 7:.5 8:.5 5:2 7:1 7:.5 8:.5 9:1.5 8:.5 7:1 6:.5 7:.5 8:2 '
                '10:1 10:.5 11:.5 10:1 9:1 8:1 9:.5 8:.5 7:1.5 6:.5 5:1 6:1 7:1 8:1 10:2 10:2 -:1 '
                '8:1 8:.5 9:.5 10:1.5 9:.5 8:1 7:.5 8:.5 5:2 6:1 7:1 8:1 9:1 10:4 -:2')},
            {'jenis': 'ritme', 'instrumen': 'genderang_kecil', 'unit': 0.5, 'vol': 0.8, 'pola': 'x.xx.xxx'},
            {'jenis': 'ritme', 'instrumen': 'gong', 'ukuran': 1, 'unit': 1.0, 'vol': 0.7, 'pola': 'X...'},
            {'jenis': 'ritme', 'instrumen': 'balok_kayu', 'unit': 0.5, 'vol': 0.5, 'pola': '.x.x.x.x'},
        ],
    },
    {
        'id': 'ratap_pagi', 'nama': 'Ratapan Pagi', 'tempo': 50, 'instrumen': 'suona', 'nuansa': ['makam', 'upacara'],
        'cerita': 'Pengiring pemakaman: satu nada ditahan, satu gong yang lambat, erhu yang menahan napas.',
        'lapis': [
            {'jenis': 'melodi', 'instrumen': 'suona', 'oktaf': 0, 'vol': 0.9, 'catatan': phrase(
                '9:2 8:1 7:1 8:3 6:1 7:2 6:1 5:1 4:4 5:2 7:1 8:1 7:2 6:1 5:1 6:2 5:1 4:1 4:4 -:4 '
                '9:2 8:1 7:1 8:2 9:1 10:1 9:3 7:1 8:2 7:1 6:1 5:1 4:1 4:2 4:6 -:4')},
            {'jenis': 'melodi', 'instrumen': 'erhu', 'oktaf': 0, 'vol': 0.55, 'catatan': phrase(
                '4:4 3:4 2:4 4:4 -:4 4:4 3:4 2:4 0:4 4:6 -:2 -:4 0:4 2:4 4:8')},
            {'jenis': 'ritme', 'instrumen': 'gong', 'ukuran': 0, 'unit': 4.0, 'vol': 0.75, 'pola': 'X'},
        ],
    },
    {
        'id': 'pesta_lentera', 'nama': 'Pesta Lentera', 'tempo': 112, 'instrumen': 'dizi', 'nuansa': ['pesta', 'malam', 'festival'],
        'cerita': 'Dizi memimpin, pipa mengisi; lagu untuk malam ketika semua lentera dinyalakan.',
        'lapis': [
            {'jenis': 'melodi', 'instrumen': 'dizi', 'oktaf': 0, 'vol': 0.9, 'catatan': phrase(
                '5:.5 6:.5 7:1 8:1 7:1 5:.5 6:.5 7:1 9:2 8:.5 9:.5 10:1 9:1 8:1 7:1 6:1 5:2 '
                '8:.5 8:.5 9:1 10:1 9:1 7:.5 8:.5 9:1 11:2 10:1 9:1 8:1 7:1 6:.5 7:.5 5:4 -:2')},
            {'jenis': 'melodi', 'instrumen': 'pipa', 'oktaf': 0, 'vol': 0.8, 'catatan': phrase(
                '0:1 5:1 0:1 5:1 0:1 5:1 2:1 7:1 1:1 6:1 1:1 6:1 3:1 8:1 4:1 9:1 '
                '0:1 5:1 0:1 5:1 2:1 7:1 2:1 7:1 1:1 6:1 3:1 8:1 4:2 0:4 -:2')},
        ],
    },
    {
        'id': 'malam_teh', 'nama': 'Teh Malam', 'tempo': 76, 'instrumen': 'pipa', 'nuansa': ['kedai', 'malam'],
        'cerita': 'Pipa sendirian di kedai teh; nada-nada jatuh pelan seperti uap dari cangkir.',
        'lapis': [{'jenis': 'melodi', 'instrumen': 'pipa', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
            '5:.5 7:.5 8:1 7:.5 5:.5 3:1 5:1 6:.5 7:.5 8:2 -:1 '
            '8:.5 9:.5 10:1 9:.5 8:.5 7:1 6:.5 7:.5 5:1 3:2 -:1 '
            '3:.5 5:.5 6:1 7:1 8:.5 7:.5 5:1 6:.5 5:.5 3:.5 0:.5 3:3 -:2')}],
    },
    {
        'id': 'dizi_sungai', 'nama': 'Seruling di Tepi Sungai', 'tempo': 82, 'instrumen': 'dizi', 'nuansa': ['sungai', 'pagi'],
        'cerita': 'Seruling bambu yang mengikuti arus; lari-larian kecil lalu diam sebentar.',
        'lapis': [{'jenis': 'melodi', 'instrumen': 'dizi', 'oktaf': 0, 'vol': 1.0, 'catatan': phrase(
            '5:.5 6:.5 7:.5 8:.5 9:2 8:.5 7:.5 6:.5 5:.5 6:2 7:.5 8:.5 9:.5 10:.5 9:1 8:1 7:2 -:1 '
            '3:1 5:.5 6:.5 7:1 6:.5 5:.5 3:2 5:.5 6:.5 7:.5 8:.5 9:.5 8:.5 7:1 5:4 -:2')}],
    },
    {
        'id': 'gembira_pasar', 'nama': 'Gembira di Pasar Malam', 'tempo': 120, 'instrumen': 'pipa', 'nuansa': ['malam_meriah', 'pasar'],
        'cerita': 'Pipa dan erhu bersahutan di distrik malam; cepat, riuh, dan kadang sengaja sumbang.',
        'lapis': [
            {'jenis': 'melodi', 'instrumen': 'erhu', 'oktaf': 0, 'vol': 0.8, 'catatan': phrase(
                '8:1 9:.5 8:.5 7:1 5:1 6:1 7:.5 6:.5 5:2 8:1 9:.5 10:.5 9:1 8:1 7:.5 8:.5 7:.5 6:.5 5:2 '
                '10:1 10:.5 9:.5 8:1 7:1 8:.5 9:.5 8:.5 7:.5 5:2 6:1 7:1 8:1 9:1 10:4 -:2')},
            {'jenis': 'melodi', 'instrumen': 'pipa', 'oktaf': 0, 'vol': 0.9, 'catatan': phrase(
                '0:.5 5:.5 0:.5 5:.5 2:.5 7:.5 2:.5 7:.5 1:.5 6:.5 1:.5 6:.5 3:.5 8:.5 3:.5 8:.5 '
                '0:.5 5:.5 0:.5 5:.5 2:.5 7:.5 2:.5 7:.5 4:.5 9:.5 4:.5 9:.5 0:2 -:2')},
            {'jenis': 'ritme', 'instrumen': 'balok_kayu', 'unit': 0.5, 'vol': 0.45, 'pola': 'x.x.x.xx'},
        ],
    },
    {
        'id': 'barongsai', 'nama': 'Tabuh Singa', 'tempo': 132, 'instrumen': 'genderang_kecil', 'nuansa': ['barongsai', 'festival'],
        'cerita': 'Tabuhan barongsai: genderang memburu, gong menjawab, balok kayu menghitung langkah singa.',
        'lapis': [
            {'jenis': 'ritme', 'instrumen': 'genderang_kecil', 'unit': 0.25, 'vol': 1.0, 'pola': 'x.xxx.xxx.x.xxxx'},
            {'jenis': 'ritme', 'instrumen': 'gong', 'ukuran': 2, 'unit': 1.0, 'vol': 0.8, 'pola': 'X..x'},
            {'jenis': 'ritme', 'instrumen': 'balok_kayu', 'unit': 0.5, 'vol': 0.55, 'pola': 'x.xx.x.x'},
        ],
    },
    {
        'id': 'ronda', 'nama': 'Ketukan Ronda', 'tempo': 96, 'instrumen': 'kentongan', 'nuansa': ['ronda', 'malam'],
        'cerita': 'Kentongan penjaga malam: dua, satu, tiga; lalu sunyi sampai geng berikutnya.',
        'lapis': [{'jenis': 'ritme', 'instrumen': 'kentongan', 'unit': 0.5, 'vol': 1.0, 'pola': 'xx.x.xxx........'}],
    },
]


def write_tunes(repo):
    d = os.path.join(repo, 'src/main/resources/data/kultivator/kota/melodi')
    os.makedirs(d, exist_ok=True)
    for tune in TUNES:
        data = {
            'id': tune['id'],
            'nama': 'kultivator.kota42.melodi.' + tune['id'],
            'tempo': tune['tempo'],
            'tangga': 'pentatonik_d',
            'instrumen': tune['instrumen'],
            'nuansa': tune['nuansa'],
            'lapis': tune['lapis'],
        }
        # the beats of a melodic voice must add up (a bar of the tune is never short): checked here once
        for lap in tune['lapis']:
            if lap['jenis'] == 'melodi':
                total = sum(b for _, b in lap['catatan'])
                assert abs(total * 2 - round(total * 2)) < 1e-9, (tune['id'], lap['instrumen'], total)
        path = os.path.join(d, tune['id'] + '.json')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
            f.write('\n')
    print('tunes', len(TUNES))


# ---------------------------------------------------------------------------------------------------- the two new beds

def render_voice(inst, degrees_beats, tempo, total_sec, sr, oktaf=0, vol=1.0, cache=None):
    """Mixes one melodic voice into a mono buffer of total_sec seconds (notes keep ringing over the next ones)."""
    out = np.zeros(int(total_sec * sr))
    t = 0.0
    spb = 60.0 / tempo
    fn = {'erhu': erhu, 'suona': suona, 'pipa': pipa, 'dizi': dizi}[inst]
    for deg, beats in degrees_beats:
        if deg >= 0:
            key = (inst, deg)
            if cache is not None and key in cache:
                s = cache[key]
            else:
                s = fn(hz(inst, deg))
                if cache is not None:
                    cache[key] = s
            if sr != SR:
                s = np.interp(np.arange(int(len(s) * sr / SR)) / sr * SR, np.arange(len(s)), s)
            i = int(t * sr)
            if i < len(out):
                seg = s[:len(out) - i]
                out[i:i + len(seg)] += seg * vol
        t += beats * spb
    return out


def beat_slice(catatan, b0, b1):
    """The notes of a melodic voice that start inside [b0, b1) beats, as a fresh list starting at beat b0."""
    out = []
    t = 0.0
    for d, b in catatan:
        if t >= b0 and t < b1:
            out.append([d, b])
        t += b
    return out


def tune(tid):
    return next(x for x in TUNES if x['id'] == tid)


def seamless(bed, loop, xf):
    n = int(SR2 * loop)
    x = int(SR2 * xf)
    fade = np.linspace(0, 1, x)[:, None]
    out = bed[:n].copy()
    out[:x] = bed[:x] * np.sqrt(fade) + bed[n:n + x] * np.sqrt(1 - fade)
    return out


def level(bed, rms_db, peak=0.7):
    rms = np.sqrt(np.mean(bed ** 2))
    bed = bed * (10 ** (rms_db / 20) / max(1e-9, rms))
    m = np.max(np.abs(bed))
    return bed * (peak / m) if m > peak else bed


def load_loop(repo, name):
    data, sr = sf.read(os.path.join(repo, 'src/main/resources/assets/kultivator/sounds/ambient/kota', name + '.ogg'), dtype='float64')
    assert sr == SR2, sr
    return data


def place_stereo(bed, mono, at_sec, gain, pan, total):
    x = mono * gain
    left, right = np.cos(pan * np.pi / 2), np.sin(pan * np.pi / 2)
    i = int(at_sec * SR2)
    n = len(bed)
    idx = (i + np.arange(len(x))) % n
    np.add.at(bed[:, 0], idx, x * left)
    np.add.at(bed[:, 1], idx, x * right)


def stereo_room(bed, rt, wet, tone, seed):
    out = bed.copy()
    for ch in range(2):
        h = room_ir(rt * 1.3, rt, tone, seed + ch, SR2)
        w = convolve(bed[:, ch], h)[:len(bed)]
        out[:, ch] += w * wet * 4
    return out


def build_malam_meriah(repo, cache):
    """The night district: the tavern and the street crowd under pipa, erhu and dizi that answer each other from open windows,
    a few coin clinks, a firecracker far off. 36 s loop, the music phrases are placed so they never end on the seam."""
    loop, xf = 36.0, 3.0
    total = loop + xf
    n = int(SR2 * total)
    kedai = load_loop(repo, 'kedai')
    jalan = load_loop(repo, 'jalan')
    bed = np.zeros((n, 2))
    bed += kedai[:n] * 0.85 if len(kedai) >= n else np.tile(kedai, (2, 1))[:n] * 0.85
    bed += np.roll(jalan, int(SR2 * 11), axis=0)[:n] * 0.55
    # music: three voices from windows at different distances and pans, each far enough to lose its highs
    spec = [('pipa', 'gembira_pasar', 1, 1.0, 0.0, 0.32, 0.9), ('erhu', 'gembira_pasar', 0, 0.9, 5.5, 0.7, 0.7),
            ('dizi', 'pesta_lentera', 0, 1.0, 14.0, 0.55, 0.6)]
    for inst, tid, lap_i, vol, at, pan, gain in spec:
        tu = tune(tid)
        laps = [l for l in tu['lapis'] if l['jenis'] == 'melodi' and l['instrumen'] == inst]
        if not laps:
            continue
        voice = render_voice(inst, laps[0]['catatan'], tu['tempo'], 22.0, SR, vol=vol, cache=cache)
        voice = np.interp(np.arange(int(len(voice) * SR2 / SR)) / SR2 * SR, np.arange(len(voice)), voice)
        voice = lowpass(voice, 3600, SR2)
        place_stereo(bed, voice, at, gain * 0.5, pan, n)
    # a firecracker string far off, coins, a cup set down
    pet = petasan(3)
    far = lowpass(np.interp(np.arange(int(len(pet) * SR2 / SR)) / SR2 * SR, np.arange(len(pet)), pet), 1500, SR2)
    place_stereo(bed, far, 27.0, 0.12, 0.2, n)
    for at, g, p in ((8.2, 0.08, 0.3), (19.5, 0.07, 0.6), (30.4, 0.08, 0.4)):
        place_stereo(bed, balok(2200.0, 0.2) * 0.4, at, g, p, n)
    bed = stereo_room(bed, 0.6, 0.04, 3800, 301)
    return level(seamless(bed, loop, xf), -23.5)


def build_upacara(repo, cache):
    """A procession heard from far away: suona phrases and a slow gong over a faint street, wide and dull as if over rooftops.
    40 s loop."""
    loop, xf = 40.0, 3.0
    total = loop + xf
    n = int(SR2 * total)
    jalan = load_loop(repo, 'jalan')
    bed = np.zeros((n, 2))
    bed += lowpass(np.roll(jalan, int(SR2 * 5), axis=0)[:n], 1400, SR2) * 0.25
    tu = tune('arak_pengantin')
    lap = next(l for l in tu['lapis'] if l['jenis'] == 'melodi')
    spb_b = 60.0 / tu['tempo']
    for b0, b1, at, pan in ((0.0, 34.0, 1.0, 0.36), (26.0, 62.0, 22.0, 0.44)):
        seg = beat_slice(lap['catatan'], b0, b1)
        voice = render_voice('suona', seg, tu['tempo'], (b1 - b0) * spb_b + 2.5, SR, vol=1.0, cache=cache)
        voice = np.interp(np.arange(int(len(voice) * SR2 / SR)) / SR2 * SR, np.arange(len(voice)), voice)
        place_stereo(bed, lowpass(voice, 2300, SR2), at, 0.55, pan, n)
    g = gong(150.0, 1.0)
    g = np.interp(np.arange(int(len(g) * SR2 / SR)) / SR2 * SR, np.arange(len(g)), g)
    for at in (0.5, 6.5, 12.5, 18.5, 24.5, 30.5, 36.5):
        place_stereo(bed, lowpass(g, 1700, SR2), at, 0.35, 0.5, n)
    d = genderang(185.0, 0.3)
    d = np.interp(np.arange(int(len(d) * SR2 / SR)) / SR2 * SR, np.arange(len(d)), d)
    spb = 60.0 / tu['tempo']
    t = 2.0
    pat = 'x.xx.xxx'
    k = 0
    while t < total - 1:
        if pat[k % len(pat)] == 'x':
            place_stereo(bed, lowpass(d, 1500, SR2), t, 0.15, 0.42, n)
        t += spb * 0.5
        k += 1
    bed = stereo_room(bed, 2.0, 0.16, 2600, 311)
    return level(seamless(bed, loop, xf), -26.5)


# ---------------------------------------------------------------------------------------------------- writing

def write_ogg(path, audio, sr, channels):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fresh = path + '.new'
    data = np.clip(audio, -1, 1).astype(np.float32)
    # libsndfile's Vorbis encoder overflows the stack on one big write: feed it in blocks
    with sf.SoundFile(fresh, 'w', sr, channels, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(data), 8192):
            f.write(data[i:i + 8192])
    if os.path.exists(path):
        if TIMPA:
            os.replace(fresh, path)
            return
        k = 0
        while os.path.exists('%s.%d.deactivation' % (path, k)):
            k += 1
        os.rename(path, '%s.%d.deactivation' % (path, k))
    os.rename(fresh, path)


def all_clips():
    """name -> (callable returning the audio) for every clip under sounds/kota42."""
    clips = {}
    for inst, fn in (('erhu', erhu), ('suona', suona), ('pipa', pipa), ('dizi', dizi)):
        for d in range(NOTE_COUNT):
            clips['%s_%d' % (inst, d)] = (lambda fn=fn, inst=inst, d=d: fn(hz(inst, d)))
    for i, (f, sc) in enumerate(((118.0, 1.0), (176.0, 0.9), (262.0, 0.55))):
        clips['gong_%d' % i] = (lambda f=f, sc=sc: gong(f, sc))
    for i, f in enumerate((236.0, 296.0, 372.0)):
        clips['kentongan_%d' % i] = (lambda f=f: kentongan(f))
    clips['genderang_kecil_0'] = lambda: genderang(190.0, 0.5)
    clips['genderang_kecil_1'] = lambda: genderang(262.0, 0.85)
    clips['balok_kayu_0'] = lambda: balok(930.0)
    clips['balok_kayu_1'] = lambda: balok(1240.0)
    for v in range(6):
        clips['petasan_%d' % v] = (lambda v=v: petasan(v))
    clips['merpati_peluit'] = merpati_peluit
    return clips


SUB_ID = {
    'erhu': 'Erhu mengalun', 'suona': 'Suona melengking', 'pipa': 'Pipa dipetik', 'dizi': 'Dizi ditiup',
    'gong': 'Gong berdengung', 'kentongan': 'Kentongan berbunyi', 'genderang_kecil': 'Genderang kecil ditabuh',
    'balok_kayu': 'Balok kayu diketuk', 'petasan': 'Petasan meletus', 'merpati_peluit': 'Peluit merpati berdengung',
}
SUB_EN = {
    'erhu': 'Erhu plays', 'suona': 'Suona blares', 'pipa': 'Pipa plucked', 'dizi': 'Dizi plays',
    'gong': 'Gong resonates', 'kentongan': 'Slit drum knocks', 'genderang_kecil': 'Small drum beaten',
    'balok_kayu': 'Wood block knocks', 'petasan': 'Firecrackers pop', 'merpati_peluit': 'Pigeon whistles hum',
}


def sounds_entries():
    """The sounds.json entries of everything this script writes (event -> entry)."""
    out = {}
    for inst in ('erhu', 'suona', 'pipa', 'dizi'):
        for d in range(NOTE_COUNT):
            out['kota42.%s.%d' % (inst, d)] = {'subtitle': 'subtitles.kultivator.kota42.' + inst,
                                              'sounds': [{'name': 'kultivator:kota42/%s_%d' % (inst, d)}]}
    for i in range(3):
        out['kota42.gong.%d' % i] = {'subtitle': 'subtitles.kultivator.kota42.gong', 'sounds': [{'name': 'kultivator:kota42/gong_%d' % i}]}
        out['kota42.kentongan.%d' % i] = {'subtitle': 'subtitles.kultivator.kota42.kentongan',
                                         'sounds': [{'name': 'kultivator:kota42/kentongan_%d' % i}]}
    out['kota42.genderang_kecil'] = {'subtitle': 'subtitles.kultivator.kota42.genderang_kecil',
                                     'sounds': [{'name': 'kultivator:kota42/genderang_kecil_%d' % i} for i in range(2)]}
    out['kota42.balok_kayu'] = {'subtitle': 'subtitles.kultivator.kota42.balok_kayu',
                                'sounds': [{'name': 'kultivator:kota42/balok_kayu_%d' % i} for i in range(2)]}
    out['kota42.petasan'] = {'subtitle': 'subtitles.kultivator.kota42.petasan',
                             'sounds': [{'name': 'kultivator:kota42/petasan_%d' % i} for i in range(6)]}
    out['kota42.merpati_peluit'] = {'subtitle': 'subtitles.kultivator.kota42.merpati_peluit',
                                    'sounds': [{'name': 'kultivator:kota42/merpati_peluit'}]}
    for bed in ('malam_meriah', 'upacara'):
        out['ambient.kota.' + bed] = {'subtitle': 'subtitles.kultivator.ambient.kota.' + bed,
                                      'sounds': [{'name': 'kultivator:ambient/kota/' + bed, 'stream': True}]}
    return out


def lang_fragments(repo):
    base = os.path.join(repo, 'lang-src')
    for suffix, table in (('id_id', SUB_ID), ('en_us', SUB_EN)):
        data = {}
        for k, v in table.items():
            data['subtitles.kultivator.kota42.' + k] = v
        data['subtitles.kultivator.ambient.kota.malam_meriah'] = 'Malam kota yang meriah' if suffix == 'id_id' else 'A lively night in town'
        data['subtitles.kultivator.ambient.kota.upacara'] = 'Suona dan gong dari kejauhan' if suffix == 'id_id' else 'Suona and gong in the distance'
        # tune names (the music "now playing" line of the client scheduler)
        for tu in TUNES:
            data['kultivator.kota42.melodi.' + tu['id']] = tu['nama'] if suffix == 'id_id' else EN_TUNE[tu['id']]
        path = os.path.join(base, 'kota_hidup_subtitle.%s.json' % suffix)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print('lang kota_hidup_subtitle written')


EN_TUNE = {
    'jembatan': 'Song Under the Bridge', 'bulan_sabit': 'Crescent Moon over the Roof Tiles', 'perahu_pulang': 'The Boat Comes Home',
    'arak_pengantin': 'The Wedding Procession', 'ratap_pagi': 'Morning Lament', 'pesta_lentera': 'Lantern Feast',
    'malam_teh': 'Night Tea', 'dizi_sungai': 'Flute on the Riverbank', 'gembira_pasar': 'Merry Night Market',
    'barongsai': 'Lion Drums', 'ronda': 'The Night Watch Knocks',
}


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    repo = args[0]
    merge = '--merge-sounds-json' in args
    only = None
    prev = None
    if '--only' in args:
        only = set(args[args.index('--only') + 1].split(','))
    if '--preview' in args:
        prev = args[args.index('--preview') + 1]
    out_dir = os.path.join(repo, 'src/main/resources/assets/kultivator/sounds/kota42')
    clips = all_clips()
    for i, (name, make) in enumerate(clips.items()):
        if only and name not in only and name.split('_')[0] not in only:
            continue
        audio = make()
        assert np.all(np.isfinite(audio)), name
        write_ogg(os.path.join(out_dir, name + '.ogg'), audio, SR, 1)
        if prev:
            os.makedirs(prev, exist_ok=True)
            sf.write(os.path.join(prev, name + '.wav'), audio.astype(np.float32), SR)
        if i % 10 == 0:
            print('..', name, round(len(audio) / SR, 2), 's')
    write_tunes(repo)
    cache = {}
    if not only or 'ambient' in only:
        for name, build in (('malam_meriah', build_malam_meriah), ('upacara', build_upacara)):
            audio = build(repo, cache)
            write_ogg(os.path.join(repo, 'src/main/resources/assets/kultivator/sounds/ambient/kota', name + '.ogg'), audio, SR2, 2)
            if prev:
                sf.write(os.path.join(prev, name + '.wav'), audio.astype(np.float32), SR2)
            print('bed', name, round(len(audio) / SR2, 1), 's')
    entries = sounds_entries()
    snippet = os.path.join(repo, 'docs/progres/m2-42V-F-suara.json')
    with open(snippet, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(dict(sorted(entries.items())), f, ensure_ascii=False, indent=2)
        f.write('\n')
    lang_fragments(repo)
    if merge:
        sj = os.path.join(repo, 'src/main/resources/assets/kultivator/sounds.json')
        old = json.load(open(sj, encoding='utf-8'))
        for k in list(old):
            if k.startswith('kota42.') or k in ('ambient.kota.malam_meriah', 'ambient.kota.upacara'):
                del old[k]
        old.update(entries)
        with open(sj, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(dict(sorted(old.items())), f, ensure_ascii=False, indent=2)
            f.write('\n')
        print('sounds.json merged', len(entries), 'entries')
    print('events', len(entries), 'clips', len(clips))


if __name__ == '__main__':
    main()

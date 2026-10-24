"""Builds the crowd ambience loops of "Kota yang Hidup" (docs/progres/suara-kota.md): one seamless stereo loop per kind of
place, mixed from VOICEVOX walla (many quiet overlapping Japanese phrases, made unintelligible by distance, pan and room),
CC0 foley and a few synthesised sounds (temple bell, wooden fish, surf bed).

Usage (the venv of tools/gen_voices.py: VOICEVOX CORE 0.17 + soundfile + numpy):
  python -I tools/gen_riuh_kota.py <repo> <voicevox_dir> <kenney_rpg_dir> <kenney_impact_dir> <gulls_dir> <waves_dir>
    <kenney_rpg_dir>    Kenney "RPG Audio" (CC0), the folder holding Audio/*.ogg
    <kenney_impact_dir> Kenney "Impact Sounds" (CC0), the folder holding Audio/*.ogg
    <gulls_dir>         "Solo Seagull Sound Effects" by Rango Mango (CC0, OpenGameArt): Seagull Ambient *.wav
    <waves_dir>         "water waves" by transitking (CC0, OpenGameArt): wave_0*.flac
Writes src/main/resources/assets/kultivator/sounds/ambient/kota/<kind>.ogg and the ambient.kota.* entries of
sounds.json (every other entry is kept). Sources and licences: docs/CREDITS.md.
"""
import glob
import io
import json
import os
import sys

import numpy as np
import soundfile as sf

sys.stdout.reconfigure(encoding='utf-8')
SR = 44100
REPO, VVX, RPG, IMPACT, GULLS, WAVES = sys.argv[1:7]
OUT = os.path.join(REPO, 'src/main/resources/assets/kultivator/sounds/ambient/kota')
LOOP = 48.0  # seconds; long enough that the scattered details do not feel like a loop
XF = 3.0  # crossfade folded from the tail into the head, for a seamless loop
N = int(SR * LOOP)
TOTAL = int(SR * (LOOP + XF))
rng = np.random.default_rng(3838)

# ---------------------------------------------------------------- DSP (same filters as gen_voices.py)


def lowpass(x, hz):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / np.sqrt(1 + (f / hz) ** 4)
    return np.fft.irfft(X, len(x))


def highpass(x, hz):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / np.sqrt(1 + (hz / np.maximum(f, 1)) ** 4)
    return np.fft.irfft(X, len(x))


def ir(seconds, rt60, predelay_ms, tone_hz, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    t = np.arange(n) / SR
    noise = lowpass(r.standard_normal(n) * np.exp(-6.91 * t / rt60), tone_hz)
    noise[:int(SR * 0.004)] *= np.linspace(0, 1, int(SR * 0.004))
    return np.concatenate([np.zeros(int(SR * predelay_ms / 1000)), noise / np.sqrt(np.sum(noise ** 2))])


def convolve(x, h):
    n = len(x) + len(h) - 1
    m = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(h, m), m)[:n]


def resample(x, n_out):
    X = np.fft.rfft(x)
    m = n_out // 2 + 1
    Y = np.zeros(m, dtype=complex)
    k = min(m, len(X))
    Y[:k] = X[:k]
    return np.fft.irfft(Y, n_out) * (n_out / len(x))


def mono(data, sr):
    if data.ndim > 1:
        data = data.mean(axis=1)
    return resample(data, int(round(len(data) * SR / sr))) if sr != SR else data


def load(path):
    data, sr = sf.read(path, dtype='float64')
    x = mono(data, sr)
    return x / max(1e-9, np.max(np.abs(x)))


def place(bed, x, at, gain, pan):
    """Adds mono x into the stereo bed at sample `at` (wrapping past the loop end, so details may straddle the seam)."""
    x = x * gain
    left, right = np.cos(pan * np.pi / 2), np.sin(pan * np.pi / 2)
    idx = (at + np.arange(len(x))) % TOTAL
    np.add.at(bed[:, 0], idx, x * left)
    np.add.at(bed[:, 1], idx, x * right)


def room(bed, rt, wet, tone, seed):
    out = bed.copy()
    for ch in range(2):
        w = convolve(bed[:, ch], ir(rt * 1.3, rt, 15 + 9 * ch, tone, seed + ch))[:TOTAL]
        out[:, ch] += w * wet * 4
    return out


def seamless(bed):
    """Folds the XF tail onto the head with an equal-power crossfade and cuts the loop to LOOP seconds."""
    x = int(SR * XF)
    fade = np.linspace(0, 1, x)[:, None]
    out = bed[:N].copy()
    out[:x] = bed[:x] * np.sqrt(fade) + bed[N:N + x] * np.sqrt(1 - fade)
    return out


def level(bed, rms_db, peak=0.7):
    rms = np.sqrt(np.mean(bed ** 2))
    bed = bed * (10 ** (rms_db / 20) / max(1e-9, rms))
    m = np.max(np.abs(bed))
    return bed * (peak / m) if m > peak else bed


# ---------------------------------------------------------------- VOICEVOX walla

from voicevox_core.blocking import Onnxruntime, OpenJtalk, Synthesizer, VoiceModelFile  # noqa: E402

ort = Onnxruntime.load_once(filename=os.path.join(VVX, 'ort/voicevox_onnxruntime-win-x64-1.17.3/lib/voicevox_onnxruntime.dll'))
syn = Synthesizer(ort, OpenJtalk(os.path.join(VVX, 'dict/open_jtalk_dic_utf_8-1.11')))
for n in (0, 4, 9, 10, 12, 15):
    with VoiceModelFile.open(os.path.join(VVX, f'vvm/{n}.vvm')) as m:
        syn.load_voice_model(m)


def tts(text, style, speed=1.0, pitch=0.0, intonation=1.0):
    q = syn.create_audio_query(text, style)
    q.speed_scale = speed
    q.pitch_scale = pitch
    q.intonation_scale = intonation
    q.pre_phoneme_length = 0.02
    q.post_phoneme_length = 0.05
    data, sr = sf.read(io.BytesIO(syn.synthesis(q, style)), dtype='float64')
    x = mono(data, sr)
    return x / max(1e-9, np.max(np.abs(x)))


# Everyday fragments; at walla distance only the rhythm and the tone of voices survive.
WALLA = ['そうそう、それでね', 'ええ、本当に', 'いくらですか', 'あれはどうだ', 'まあまあだね', 'なるほどなあ', 'うんうん',
         'ちょっと見せて', 'それは困ったね', 'いやいや、違うよ', 'へえ、そうなんだ', '明日また来るよ', 'これ下さい',
         'ほら、あそこ', 'そりゃあいい', 'だから言っただろう', 'ふうん', 'あのね、昨日ね', '安くしとくよ', 'さあさあ']
STYLES = [12, 11, 52, 13, 2, 8, 10, 20, 46, 42, 3]
LAUGHS = ['あはは', 'はっはっは', 'うふふ', 'わはは', 'えへへ']
pool = []
for i, text in enumerate(WALLA * 2):
    st = STYLES[(i * 7) % len(STYLES)]
    pool.append(tts(text, st, speed=1.0 + 0.15 * rng.random(), pitch=0.03 * rng.standard_normal()))
laughs = [tts(t, s, speed=1.05, intonation=1.5) for t in LAUGHS for s in (12, 2, 11, 8)]
print('walla pool', len(pool), 'laughs', len(laughs))


def walla(bed, voices_per_sec, gain_db, lp=(900, 2400)):
    """Many overlapping far voices: random start, distance gain, dulled tone, spread over the stereo field."""
    count = int(voices_per_sec * LOOP)
    for _ in range(count):
        x = pool[rng.integers(len(pool))]
        dist = rng.random()
        x = lowpass(x, lp[0] + (lp[1] - lp[0]) * (1 - dist))
        g = 10 ** ((gain_db - 14 * dist) / 20)
        place(bed, x, int(rng.integers(TOTAL)), g, rng.uniform(0.1, 0.9))


def scatter(bed, clips, every_s, gain_db, jitter=0.6, lp=None, spread=(0.15, 0.85)):
    t = rng.random() * every_s
    while t < LOOP + XF:
        x = clips[rng.integers(len(clips))]
        if lp:
            x = lowpass(x, lp)
        g = 10 ** ((gain_db - 8 * rng.random()) / 20)
        place(bed, x, int(t * SR), g, rng.uniform(*spread))
        t += every_s * (1 - jitter / 2 + jitter * rng.random())


# ---------------------------------------------------------------- foley and synthesis

def kenney(folder, pattern):
    files = sorted(glob.glob(os.path.join(folder, '**', pattern), recursive=True))
    if not files:
        raise SystemExit(f'no {pattern} under {folder}')
    return [load(f) for f in files]


coins = kenney(RPG, 'handleCoins*.ogg')
steps = kenney(RPG, 'footstep0*.ogg') + kenney(IMPACT, 'footstep_concrete_*.ogg')
wood_steps = kenney(IMPACT, 'footstep_wood_*.ogg')
creaks = kenney(RPG, 'creak*.ogg')
cloth = kenney(RPG, 'cloth*.ogg')
pots = kenney(RPG, 'metalPot*.ogg')
cups = kenney(IMPACT, 'impactGlass_light_*.ogg') + kenney(IMPACT, 'impactPlate_light_*.ogg')
planks = kenney(IMPACT, 'impactPlank_medium_*.ogg') + kenney(IMPACT, 'impactWood_light_*.ogg')
crates = kenney(IMPACT, 'impactWood_heavy_*.ogg')
small_bells = kenney(IMPACT, 'impactBell_heavy_*.ogg')
gulls = [load(f) for f in sorted(glob.glob(os.path.join(GULLS, 'Seagull*Ambient*.wav')))]
waves = [load(f) for f in sorted(glob.glob(os.path.join(WAVES, 'wave_0*.flac')))]
if not gulls or not waves:
    raise SystemExit('gull or wave clips missing')


def cart(length_s=3.5):
    """A hand cart rolling past: a stream of soft wooden knocks and a creak, rising and falling."""
    n = int(SR * length_s)
    x = np.zeros(n)
    t = 0.0
    while t < length_s - 0.3:
        k = planks[rng.integers(len(planks))][:int(SR * 0.25)] * 0.35
        i = int(t * SR)
        x[i:i + len(k)] += k[:max(0, n - i)]
        t += 0.11 + 0.07 * rng.random()
    c = creaks[rng.integers(len(creaks))]
    x[:min(n, len(c))] += 0.5 * c[:n]
    env = np.sin(np.linspace(0, np.pi, n)) ** 1.5
    return lowpass(x * env, 3000)


def temple_bell():
    """A big bronze bell (bonsho): inharmonic partials, slow beating and a long hum."""
    n = int(SR * 9)
    t = np.arange(n) / SR
    f0 = 98.0
    x = np.zeros(n)
    for ratio, amp, decay in ((0.5, 0.5, 7.0), (1.0, 1.0, 5.5), (1.183, 0.6, 4.0), (1.506, 0.45, 3.2), (2.0, 0.4, 2.6),
                              (2.74, 0.25, 1.8), (3.37, 0.15, 1.2), (4.2, 0.1, 0.8)):
        f = f0 * ratio
        x += amp * np.exp(-t / decay) * (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * (f + 0.7) * t))
    hit = lowpass(rng.standard_normal(int(SR * 0.04)), 1800) * np.linspace(1, 0, int(SR * 0.04))
    x[:len(hit)] += 0.8 * hit
    x[:int(SR * 0.005)] *= np.linspace(0, 1, int(SR * 0.005))
    return x / np.max(np.abs(x))


def mokugyo():
    """The wooden fish: a hollow knock."""
    n = int(SR * 0.25)
    t = np.arange(n) / SR
    x = np.exp(-t / 0.035) * (np.sin(2 * np.pi * 620 * t) + 0.4 * np.sin(2 * np.pi * 1310 * t))
    x[:int(SR * 0.012)] += lowpass(rng.standard_normal(int(SR * 0.012)), 3000) * 0.6
    return x / np.max(np.abs(x))


def surf():
    """A soft surf bed: brownish noise swelling every 6-9 s."""
    noise = lowpass(np.cumsum(rng.standard_normal(TOTAL)) * 0.02 + rng.standard_normal(TOTAL) * 0.3, 900)
    noise = highpass(noise, 60)
    t = np.arange(TOTAL) / SR
    swell = np.zeros(TOTAL)
    s = 0.0
    while s < LOOP + XF:
        period = 6 + 3 * rng.random()
        mask = (t >= s) & (t < s + period)
        swell[mask] = np.sin(np.pi * (t[mask] - s) / period) ** 2
        s += period
    return noise / np.max(np.abs(noise)) * (0.35 + 0.65 * swell)


# ---------------------------------------------------------------- the five kinds

def build_pasar():
    bed = np.zeros((TOTAL, 2))
    walla(bed, 7.5, -16)
    calls = [tts(t, s, speed=1.0, intonation=1.4) for t, s in (('いらっしゃい！', 12), ('安いよ、安いよ！', 11), ('さあ、見ていって！', 2),
                                                               ('採れたてだよ！', 52), ('一つどうだい！', 8))]
    scatter(bed, calls, 7.0, -14, lp=2800)
    scatter(bed, coins, 3.2, -15)
    scatter(bed, steps, 0.45, -27, jitter=0.9)
    scatter(bed, cloth, 5.0, -24)
    scatter(bed, [cart() for _ in range(4)], 11.0, -15)
    scatter(bed, pots, 9.0, -24)
    return level(seamless(room(bed, 0.6, 0.05, 4000, 101)), -24)


def build_kedai():
    bed = np.zeros((TOTAL, 2))
    walla(bed, 5.0, -15, lp=(1200, 2800))
    scatter(bed, laughs, 3.6, -17, lp=3200)
    scatter(bed, cups, 1.6, -18, jitter=0.9)
    scatter(bed, pots, 6.0, -22)
    scatter(bed, wood_steps, 1.2, -27, jitter=0.9)
    scatter(bed, creaks, 7.0, -24)
    return level(seamless(room(bed, 0.9, 0.12, 3500, 111)), -23)


def build_pelabuhan():
    bed = np.zeros((TOTAL, 2))
    s = surf()
    bed[:, 0] += 0.25 * s
    bed[:, 1] += 0.25 * np.roll(s, int(SR * 0.4))
    scatter(bed, [lowpass(w, 3500) for w in waves], 4.0, -12, spread=(0.2, 0.8))
    scatter(bed, gulls, 5.5, -16, spread=(0.05, 0.95))
    walla(bed, 3.0, -19)
    shouts = [tts(t, s, speed=1.0, intonation=1.5) for t, s in (('おーい！', 11), ('綱を引けー！', 12), ('荷を降ろせ！', 52),
                                                                ('船が着いたぞー！', 13), ('こっちだ、こっち！', 11))]
    scatter(bed, shouts, 8.0, -17, lp=2400)
    scatter(bed, creaks, 4.5, -21)
    scatter(bed, crates, 6.0, -22)
    scatter(bed, wood_steps, 1.0, -28, jitter=0.9)
    return level(seamless(room(bed, 0.4, 0.03, 4500, 121)), -23)


def build_jalan():
    bed = np.zeros((TOTAL, 2))
    walla(bed, 3.8, -18)
    scatter(bed, steps, 0.32, -24, jitter=0.9)
    scatter(bed, cloth, 3.5, -25)
    scatter(bed, [cart() for _ in range(3)], 14.0, -16)
    scatter(bed, laughs, 10.0, -22, lp=2400)
    scatter(bed, coins, 12.0, -22)
    return level(seamless(room(bed, 0.5, 0.04, 4000, 131)), -25)


def build_kuil():
    bed = np.zeros((TOTAL, 2))
    # chanting: deep voices on one note, layered and slowed, under a wide hall
    chant = []
    for st, p in ((53, -0.05), (42, -0.08), (11, -0.1)):
        chant.append(tts('なーむー、あーみー、だーぶーつー', st, speed=0.62, pitch=p, intonation=0.0))
    for i, c in enumerate(chant):
        t = 0.0
        while t < LOOP + XF:
            place(bed, lowpass(c, 1800), int((t + 0.07 * i) * SR), 10 ** (-17 / 20), 0.35 + 0.15 * i)
            t += len(c) / SR + 0.4
    knock = mokugyo()
    t = 0.0
    while t < LOOP + XF:
        place(bed, knock, int(t * SR), 10 ** (-17 / 20), 0.55)
        t += 0.62
    bell = temple_bell()
    for at in (2.0, 18.0, 34.0):
        place(bed, bell, int(at * SR), 10 ** (-9 / 20), 0.45)
    scatter(bed, small_bells, 13.0, -24, lp=5000)
    walla(bed, 0.8, -26, lp=(700, 1400))
    scatter(bed, wood_steps, 2.5, -30, jitter=0.9)
    return level(seamless(room(bed, 2.4, 0.18, 3000, 141)), -25)


KINDS = {'pasar': build_pasar, 'kedai': build_kedai, 'pelabuhan': build_pelabuhan, 'jalan': build_jalan, 'kuil': build_kuil}
SUB_EN = {'pasar': 'Market crowd', 'kedai': 'Tavern chatter', 'pelabuhan': 'Harbour bustle', 'jalan': 'Street crowd', 'kuil': 'Temple chanting'}

os.makedirs(OUT, exist_ok=True)
sounds = {}
for kind, build in KINDS.items():
    audio = build()
    path = os.path.join(OUT, kind + '.ogg')
    fresh = os.path.join(OUT, kind + '.new')
    # libsndfile's Vorbis encoder overflows the stack on one big write: feed it in blocks
    with sf.SoundFile(fresh, 'w', SR, 2, format='OGG', subtype='VORBIS') as f:
        data = np.clip(audio, -1, 1).astype(np.float32)
        for i in range(0, len(data), 8192):
            f.write(data[i:i + 8192])
    if os.path.exists(path):
        # never overwrite in place: keep the old loop beside it (rename, never delete)
        k = 0
        while os.path.exists(f'{path}.{k}.deactivation'):
            k += 1
        os.rename(path, f'{path}.{k}.deactivation')
    os.rename(fresh, path)
    sounds['ambient.kota.' + kind] = {'subtitle': 'subtitles.kultivator.ambient.kota.' + kind,
                                      'sounds': [{'name': 'kultivator:ambient/kota/' + kind, 'stream': True}]}
    print(kind, round(len(audio) / SR, 1), 's', os.path.getsize(path) // 1024, 'KB', SUB_EN[kind])

sj = os.path.join(REPO, 'src/main/resources/assets/kultivator/sounds.json')
old = json.load(open(sj, encoding='utf-8'))
old = {k: v for k, v in old.items() if not k.startswith('ambient.kota.')}
old.update(sounds)
with open(sj, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(dict(sorted(old.items())), f, ensure_ascii=False, indent=2)
    f.write('\n')
print('sounds.json ambient.kota entries', len(sounds))

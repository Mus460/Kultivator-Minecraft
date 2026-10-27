"""Generates the Japanese voice lines (VOICEVOX) and the processed vocal clips.

Usage (VOICEVOX CORE 0.17 + soundfile + numpy in a venv):
  python -I tools/gen_voices.py <repo> <voicevox_dir> <voice_dl_dir>
    <voicevox_dir> holds ort/voicevox_onnxruntime-win-x64-1.17.3/lib/voicevox_onnxruntime.dll,
                   dict/open_jtalk_dic_utf_8-1.11 and vvm/{0,4,9,10,12,15}.vvm
    <voice_dl_dir> holds the CC0 / CC-BY packs listed in docs/CREDITS.md (non-verbal clips only)
Writes src/main/resources/assets/kultivator/sounds/voice/**.ogg, src/main/resources/assets/kultivator/sounds.json
(voice entries; other entries are kept), lang-src/voice.en_us.json, lang-src/voice.id_id.json and
tools/voice_manifest.json. Credits: docs/CREDITS.md.
"""
import io
import json
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
REPO, VVX, DL = sys.argv[1], sys.argv[2], sys.argv[3]
OUT = os.path.join(REPO, 'src/main/resources/assets/kultivator/sounds/voice')

# ---------------------------------------------------------------- DSP


def resample(x, n_out):
    if len(x) == n_out:
        return x
    X = np.fft.rfft(x)
    m = n_out // 2 + 1
    Y = np.zeros(m, dtype=complex)
    k = min(m, len(X))
    Y[:k] = X[:k]
    return np.fft.irfft(Y, n_out) * (n_out / len(x))


def to_sr(x, sr):
    return resample(x, int(round(len(x) * SR / sr))) if sr != SR else x


def stft(x, n=2048, hop=512):
    w = np.hanning(n)
    x = np.concatenate([np.zeros(n), x, np.zeros(n)])
    frames = 1 + (len(x) - n) // hop
    return np.array([np.fft.rfft(w * x[i * hop:i * hop + n]) for i in range(frames)])


def istft(S, n=2048, hop=512):
    w = np.hanning(n)
    out = np.zeros(n + hop * (len(S) - 1))
    norm = np.zeros_like(out)
    for i, f in enumerate(S):
        out[i * hop:i * hop + n] += w * np.fft.irfft(f, n)
        norm[i * hop:i * hop + n] += w * w
    norm[norm < 1e-6] = 1
    return (out / norm)[n:]


def stretch(x, rate, n=2048, hop=512):
    S = stft(x, n, hop)
    steps = np.arange(0, len(S) - 1, rate)
    phase = np.angle(S[0])
    expected = 2 * np.pi * hop * np.arange(S.shape[1]) / n
    out = []
    for t in steps:
        i = int(t)
        a = t - i
        mag = (1 - a) * np.abs(S[i]) + a * np.abs(S[i + 1])
        out.append(mag * np.exp(1j * phase))
        d = np.angle(S[i + 1]) - np.angle(S[i]) - expected
        d -= 2 * np.pi * np.round(d / (2 * np.pi))
        phase = phase + expected + d
    return istft(np.array(out), n, hop)


def pitch(x, semis):
    if semis == 0:
        return x
    r = 2 ** (semis / 12)
    y = stretch(x, 1 / r)
    return resample(y, len(x))


def delay(x, ms, total):
    d = int(SR * ms / 1000)
    y = np.zeros(total)
    y[d:d + len(x)] += x[:max(0, min(len(x), total - d))]
    return y


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
    rng = np.random.default_rng(seed)
    n = int(SR * seconds)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    noise = lowpass(noise, tone_hz)
    noise[:int(SR * 0.004)] *= np.linspace(0, 1, int(SR * 0.004))
    return np.concatenate([np.zeros(int(SR * predelay_ms / 1000)), noise / np.sqrt(np.sum(noise ** 2))])


def convolve(x, h):
    n = len(x) + len(h) - 1
    m = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(h, m), m)[:n]


def trim(x, db=-48):
    thr = np.max(np.abs(x)) * 10 ** (db / 20)
    idx = np.where(np.abs(x) > thr)[0]
    if len(idx) == 0:
        return x
    a = max(0, idx[0] - int(SR * 0.01))
    b = min(len(x), idx[-1] + int(SR * 0.04))
    return x[a:b]


def fade_out(x, ms):
    n = min(len(x), int(SR * ms / 1000))
    if x.ndim == 1:
        x[-n:] *= np.linspace(1, 0, n)
    else:
        x[-n:, :] *= np.linspace(1, 0, n)[:, None]
    return x


def norm(x, peak=0.89):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def fx_heaven(x):
    """Voice from the sky: choir layers, a huge hall reverb and long echoes ("bergema")."""
    tail = int(SR * 3.4)
    total = len(x) + tail
    dry = np.zeros(total)
    dry[:len(x)] += x
    sub = pitch(x, -12)
    fifth = pitch(x, 7)
    L = dry + 0.42 * delay(sub, 9, total) + 0.30 * delay(pitch(x, 0.13), 13, total) + 0.10 * delay(fifth, 31, total)
    R = dry + 0.42 * delay(sub, 15, total) + 0.30 * delay(pitch(x, -0.13), 21, total) + 0.10 * delay(fifth, 37, total)
    for ch, (a, b) in ((0, (380, 760)), (1, (430, 860))):
        src = L if ch == 0 else R
        echo = 0.38 * delay(lowpass(src, 4200), a, total) + 0.2 * delay(lowpass(src, 2600), b, total) \
            + 0.09 * delay(lowpass(src, 1800), a + b, total)
        if ch == 0:
            L = src + echo
        else:
            R = src + echo
    wetL = convolve(L, ir(4.6, 4.2, 70, 5200, 11))[:total]
    wetR = convolve(R, ir(4.6, 4.2, 83, 5200, 12))[:total]
    t = np.arange(total) / SR
    env = np.clip(np.minimum(t / 0.6, (total / SR - t) / 1.8), 0, 1)
    drone = 0.035 * env * (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.4 * t) + 0.3 * np.sin(2 * np.pi * 110 * t))
    out = np.stack([0.78 * L + 1.05 * wetL * np.max(np.abs(L)) * 6 + drone,
                    0.78 * R + 1.05 * wetR * np.max(np.abs(R)) * 6 + drone], axis=1)
    return fade_out(norm(out), 900)


def fx_demon(x):
    """Heart demon: lower, a whisper shadow, a growl ring and a dark close reverb."""
    tail = int(SR * 2.2)
    total = len(x) + tail
    low = np.zeros(total)
    low[:len(x)] = pitch(x, -4)
    t = np.arange(total) / SR
    growl = low * (0.75 + 0.25 * np.sin(2 * np.pi * 31 * t))
    whisper = highpass(np.concatenate([x, np.zeros(tail)]), 2200) * 0.55
    rng = np.random.default_rng(7)
    breath = lowpass(rng.standard_normal(total), 5000) * np.abs(np.concatenate([x, np.zeros(tail)])) * 0.6
    mono = np.tanh(1.6 * (growl + 0.25 * delay(whisper, 23, total) + 0.25 * breath)) / 1.6
    wetL = convolve(mono, ir(2.6, 2.1, 25, 1900, 21))[:total]
    wetR = convolve(mono, ir(2.6, 2.1, 41, 1900, 22))[:total]
    m = np.max(np.abs(mono)) * 5
    L = mono + 0.18 * delay(whisper, 0, total) + wetL * m
    R = 0.92 * mono + 0.18 * delay(whisper, 47, total) + wetR * m
    return fade_out(norm(np.stack([L, R], axis=1)), 700)


def fx_boss(x):
    """Ancient beings: very deep, with a stone-hall echo."""
    tail = int(SR * 2.8)
    total = len(x) + tail
    a = np.zeros(total)
    a[:len(x)] = pitch(x, -3)
    sub = delay(pitch(x, -15), 6, total) * 0.4
    mono = a + sub
    mono = mono + 0.3 * delay(lowpass(mono, 2500), 290, total) + 0.14 * delay(lowpass(mono, 1600), 580, total)
    wetL = convolve(mono, ir(3.4, 3.0, 50, 3000, 31))[:total]
    wetR = convolve(mono, ir(3.4, 3.0, 64, 3000, 32))[:total]
    m = np.max(np.abs(mono)) * 5.5
    return fade_out(norm(np.stack([mono + wetL * m, mono + wetR * m], axis=1)), 800)


def fx_room(x, rt=0.55, wet=0.16, seed=41):
    """Natural speech in the open sect courtyard: a short soft room."""
    tail = int(SR * rt * 0.9)
    total = len(x) + tail
    d = np.zeros(total)
    d[:len(x)] = highpass(x, 90)
    w = convolve(d, ir(rt * 1.2, rt, 12, 6000, seed))[:total]
    return fade_out(norm(d + w * np.max(np.abs(d)) * 4 * wet), 120)


def fx_elder(x):
    return fx_room(x, 0.9, 0.22, 51)


FX = {'heaven': fx_heaven, 'demon': fx_demon, 'boss': fx_boss, 'room': fx_room, 'elder': fx_elder}

# ---------------------------------------------------------------- VOICEVOX

from voicevox_core.blocking import Onnxruntime, OpenJtalk, Synthesizer, VoiceModelFile  # noqa: E402

ort = Onnxruntime.load_once(filename=os.path.join(VVX, 'ort/voicevox_onnxruntime-win-x64-1.17.3/lib/voicevox_onnxruntime.dll'))
syn = Synthesizer(ort, OpenJtalk(os.path.join(VVX, 'dict/open_jtalk_dic_utf_8-1.11')))
for n in (0, 4, 9, 10, 12, 15):
    with VoiceModelFile.open(os.path.join(VVX, f'vvm/{n}.vvm')) as m:
        syn.load_voice_model(m)

EMO = {  # emotion -> (pitch, intonation, speed, volume) used when a voice has no matching style
    'normal': (0.0, 1.0, 1.0, 1.0), 'joy': (0.04, 1.35, 1.08, 1.05), 'angry': (0.0, 1.45, 1.15, 1.2),
    'fear': (0.06, 1.2, 1.2, 0.95), 'sad': (-0.03, 0.8, 0.9, 0.9), 'calm': (-0.02, 0.9, 0.92, 1.0),
}


# readings Open JTalk gets wrong (captions keep the kanji)
READ = {'宗主': 'そうしゅ', '擂台': 'らいたい', '師匠': 'ししょう', '師尊': 'しそん', '天劫': 'てんごう', '仙帝': 'せんてい', 'お主': 'おぬし', '理': 'ことわり'}


def tts(text, style, emo='normal', speed=1.0, pitch_scale=None, intonation=None, pre=0.05, post=0.12):
    for k, v in READ.items():
        text = text.replace(k, v)
    q = syn.create_audio_query(text, style)
    p, i, s, v = EMO[emo]
    q.pitch_scale = p if pitch_scale is None else pitch_scale
    q.intonation_scale = i if intonation is None else intonation
    q.speed_scale = s * speed
    q.volume_scale = v
    q.pre_phoneme_length = pre
    q.post_phoneme_length = post
    data, sr = sf.read(io.BytesIO(syn.synthesis(q, style)), dtype='float64')
    if data.ndim > 1:
        data = data.mean(axis=1)
    return trim(to_sr(data, sr))


def load(path):
    data, sr = sf.read(path, dtype='float64')
    if data.ndim > 1:
        data = data.mean(axis=1)
    return trim(to_sr(data, sr))


MADE = []


def write(rel, make):
    """Writes the clip only when it is missing: existing clips are never re-encoded or replaced (delete one to remake it)."""
    path = os.path.join(OUT, rel + '.ogg')
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sf.write(path, np.clip(make(), -1, 1).astype(np.float32), SR, format='OGG', subtype='VORBIS')
        MADE.append(rel)
    return 'kultivator:voice/' + rel


# ---------------------------------------------------------------- content
# event id, japanese line, english caption, indonesian caption, style, emotion, fx, speed
HEAVEN = 13
HEAVEN_CALM = 84
HEAVEN_FIERCE = 81
HEART_DEMON = 21
ANCIENT = 53
ELDER = 42
ROGUE = 51

SPEAKER_EN = {'heaven': 'Heaven', 'demon': 'Heart Demon', 'boss': 'Ancient One', 'elder': 'Old Hermit',
              'rogue': 'Rogue Cultivator', 'disciple': 'Disciple'}
SPEAKER_ID = {'heaven': 'Langit', 'demon': 'Iblis Hati', 'boss': 'Makhluk Purba', 'elder': 'Pertapa Tua',
              'rogue': 'Kultivator Jahat', 'disciple': 'Murid'}

LINES = [
    # --- voice of Heaven (stereo, non-positional)
    ('heaven', 'breakthrough', '道が、共鳴している。', 'The Dao resonates.', 'Dao beresonansi.', HEAVEN_CALM, 'normal', 0.9),
    ('heaven', 'realm', '新たなる境地。天が、震えている。', 'A new realm. The heavens tremble.', 'Ranah baru. Langit bergetar.', HEAVEN, 'normal', 0.88),
    ('heaven', 'tribulation_begin', '天劫、降臨。耐え抜くか、滅びるか。', 'The Heavenly Tribulation descends. Endure, or perish.', 'Kesengsaraan Langit turun. Bertahanlah, atau binasa.', HEAVEN_FIERCE, 'normal', 0.86),
    ('heaven', 'tribulation_pass', '天劫を越えし者よ。天は、汝を認めた。', 'You who crossed the tribulation: Heaven acknowledges you.', 'Wahai yang melewati kesengsaraan: Langit mengakuimu.', HEAVEN, 'normal', 0.86),
    ('heaven', 'tribulation_fail', '汝の道は、まだ足りぬ。', 'Your Dao is not yet enough.', 'Dao-mu belum cukup.', HEAVEN_CALM, 'normal', 0.86),
    ('heaven', 'immortal_ascend', '天地よ、証人となれ。新たなる仙人が、今ここに誕生した。', 'Heaven and Earth, bear witness. A new Immortal is born this day.', 'Langit dan Bumi, jadilah saksi. Seorang Abadi baru lahir hari ini.', HEAVEN, 'normal', 0.84),
    ('heaven', 'true_immortal', '万界よ、ひれ伏せ。不滅の仙帝が、目覚めた。', 'All realms, bow down. The undying Immortal Emperor has awakened.', 'Wahai seluruh alam, bersujudlah. Kaisar Abadi yang tak binasa telah bangkit.', HEAVEN_FIERCE, 'normal', 0.84),
    ('heaven', 'dao_insight', '大道の理、ここに悟れり。', 'The principle of the Great Dao is understood.', 'Prinsip Dao Agung telah dipahami.', HEAVEN_CALM, 'normal', 0.88),
    ('heaven', 'sect_founded', '一つの宗門が生まれた。その道よ、永遠なれ。', 'A sect is born. May its Dao endure forever.', 'Sebuah sekte lahir. Semoga Dao-nya abadi selamanya.', HEAVEN, 'normal', 0.88),
    ('heaven', 'witness', '刮目せよ。', 'Behold.', 'Saksikanlah.', HEAVEN_FIERCE, 'normal', 0.8),
    ('heaven', 'decree', '天の勅命を、ここに下す！', 'Heaven\'s decree is hereby handed down!', 'Titah Langit dengan ini diturunkan!', HEAVEN_FIERCE, 'normal', 0.88),
    ('heaven', 'decree_sword', '天剣よ、落ちよ！', 'Heavenly Sword, fall!', 'Pedang Langit, jatuhlah!', HEAVEN_FIERCE, 'normal', 0.85),
    ('heaven', 'decree_spear', '天の槍よ、大地を貫け！', 'Spear of Heaven, pierce the earth!', 'Tombak Langit, tembuslah bumi!', HEAVEN_FIERCE, 'normal', 0.85),
    ('heaven', 'decree_stars', '北斗七星よ、陣を成せ！', 'Seven Stars of the Northern Dipper, form the array!', 'Tujuh Bintang Biduk Utara, bentuklah formasi!', HEAVEN_FIERCE, 'normal', 0.85),
    ('heaven', 'decree_lift', '天地よ、浮かび上がれ！', 'Heaven and Earth, rise up!', 'Langit dan Bumi, terangkatlah!', HEAVEN_FIERCE, 'normal', 0.85),
    ('heaven', 'wrath', '愚かなる者よ。天を怒らせたな。', 'Foolish one. You have angered Heaven.', 'Wahai makhluk bodoh. Kau telah membuat Langit murka.', HEAVEN_FIERCE, 'normal', 0.84),
    ('heaven', 'calamity', '災厄が、近づいている。', 'A calamity draws near.', 'Malapetaka mendekat.', HEAVEN_CALM, 'normal', 0.86),
    ('heaven', 'secret_realm', '秘境の門が、開かれた。', 'The gate of the secret realm has opened.', 'Gerbang alam rahasia telah terbuka.', HEAVEN_CALM, 'normal', 0.88),
    # --- heart demon (stereo, inside the head)
    ('demon', 'waver', '心が、揺らいでいるな……', 'Your heart is wavering...', 'Hatimu sedang goyah...', HEART_DEMON, 'calm', 0.85),
    ('demon', 'tempt', '力が欲しいか？ならば、我を受け入れよ。', 'Do you want power? Then accept me.', 'Kau ingin kekuatan? Maka terimalah aku.', HEART_DEMON, 'calm', 0.85),
    ('demon', 'win', 'お前の心は、もう我のものだ。', 'Your heart already belongs to me.', 'Hatimu sudah menjadi milikku.', HEART_DEMON, 'normal', 0.85),
    ('demon', 'lose', 'くっ……この程度で、我を退けたつもりか……', 'Tch... you think this is enough to drive me away...', 'Cih... kau kira ini cukup untuk mengusirku...', HEART_DEMON, 'angry', 0.9),
    ('demon', 'whisper', '弱き者よ……', 'Weakling...', 'Wahai yang lemah...', HEART_DEMON, 'calm', 0.8),
    # --- ancient beings (purba bosses, celestial beasts)
    ('boss', 'awaken', '我が眠りを妨げるは、何者だ。', 'Who dares disturb my slumber?', 'Siapa yang berani mengusik tidurku?', ANCIENT, 'calm', 0.85),
    ('boss', 'taunt', '蟻が、天に挑むか。', 'An ant challenges the heavens?', 'Seekor semut menantang langit?', ANCIENT, 'normal', 0.85),
    ('boss', 'enraged', 'よかろう……本気を見せてやる！', 'Very well... I will show you my true power!', 'Baiklah... akan kutunjukkan kekuatan sejatiku!', ANCIENT, 'angry', 0.9),
    ('boss', 'defeat', '見事……汝こそ、真の強者……', 'Splendid... you are truly the strong one...', 'Luar biasa... kaulah yang benar-benar kuat...', ANCIENT, 'sad', 0.85),
    # --- old hermit (villages, cave abodes, inheritance)
    ('elder', 'welcome', 'ほっほっ、よく来たのう、若き修行者よ。', 'Ho ho, welcome, young cultivator.', 'Ho ho, selamat datang, kultivator muda.', ELDER, 'normal', 0.95),
    ('elder', 'advice', '焦るでない。道は、一歩ずつ歩むものじゃ。', 'Do not rush. The Dao is walked one step at a time.', 'Jangan terburu-buru. Dao ditempuh selangkah demi selangkah.', ELDER, 'calm', 0.95),
    ('elder', 'inheritance', 'この伝承、お主に託そう。', 'I entrust this inheritance to you.', 'Kuwariskan warisan ini kepadamu.', ELDER, 'calm', 0.92),
    # --- rogue cultivators
    ('rogue', 'ambush', 'ここで会ったが百年目！その宝、置いていけ！', 'At last we meet! Leave your treasures behind!', 'Akhirnya kita bertemu! Tinggalkan hartamu!', ROGUE, 'angry', 1.0),
    ('rogue', 'flee', 'くっ……覚えていろ！', 'Tch... you will remember this!', 'Cih... ingat saja ini!', ROGUE, 'angry', 1.05),
    ('rogue', 'defeat', 'ば、馬鹿な……', 'I-impossible...', 'T-tidak mungkin...', ROGUE, 'sad', 0.95),
    ('elder', 'leitai_buka', '宗門大会、開幕！弟子たちよ、擂台に上がれ！', 'The sect tournament begins! Disciples, take the stage!', 'Turnamen sekte dibuka! Murid-murid, naiklah ke panggung!', ELDER, 'normal', 0.95),
    ('elder', 'banner', '宗門の旗が山門に掲げられました。', 'The sect banner has been raised at the gate.', 'Bendera sekte telah dikibarkan di gerbang.', ELDER, 'calm', 0.95),
    ('elder', 'envoy', '我が宗門の使者、和をもって参りました。', "Our sect's envoy comes in peace.", 'Utusan sekte kami datang dengan damai.', ELDER, 'calm', 0.95),
    ('boss', 'tamu_tantang', '我が宗門、貴殿らの剣を試しに参った。', 'Our sect has come to test your swords.', 'Sekte kami datang untuk menguji pedang kalian.', ANCIENT, 'normal', 0.9),
    ('boss', 'sect_master', '貴様の宗門が我らに挑むか。', 'Your sect dares challenge us?', 'Sektemu berani menantang kami?', ANCIENT, 'angry', 0.9),
    ('boss', 'sect_master_defeat', '今日より…我が宗門は従おう。', 'From today... our sect submits.', 'Hari ini... sekte kami tunduk.', ANCIENT, 'sad', 0.9),
    # --- DESIGN 39.F.7 (handed to 39E): the elder who breaks up a feud and pays for the damage
    ('elder', 'seteru_lerai', 'もうよい！互いの宗門の恥を晒すな。', 'Enough! You shame both your sects.', 'Cukup! Kalian mempermalukan sekte masing-masing.', ELDER, 'angry', 0.95),
    ('elder', 'seteru_ganti_rugi', '我が宗門が償いをいたそう。', 'Our sect will pay for the damage.', 'Sekte kami akan membayar ganti rugi.', ELDER, 'calm', 0.95),
    # --- DESIGN 37 (docs/progres/langit-voices.md, added by 39E): the sect rises above the clouds
    ('heaven', 'langit_naik', '山は雲を越え、宗門は天に昇る。', 'The mountain passes the clouds; the sect ascends.', 'Gunung melampaui awan; sekte naik ke langit.', HEAVEN, 'normal', 0.86),
]

# Disciples: every disciple keeps one of six voices (picked from its UUID in code).
# voice id -> (style per emotion, japanese pitch tweak)
VOICES = {
    'm1': {'normal': 12, 'joy': 32, 'fear': 33, 'angry': 34, 'sad': 12, 'calm': 12},  # 白上虎太郎
    'm2': {'normal': 11, 'joy': 39, 'angry': 40, 'sad': 41, 'fear': 11, 'calm': 11},  # 玄野武宏
    'm3': {'normal': 52, 'joy': 52, 'angry': 52, 'sad': 52, 'fear': 52, 'calm': 52},  # 雀松朱司
    'f1': {'normal': 2, 'joy': 0, 'angry': 6, 'sad': 2, 'fear': 2, 'calm': 2},  # 四国めたん
    'f2': {'normal': 8, 'joy': 8, 'angry': 8, 'sad': 8, 'fear': 8, 'calm': 8},  # 春日部つむぎ
    'f3': {'normal': 46, 'joy': 46, 'angry': 46, 'sad': 46, 'fear': 46, 'calm': 46},  # 小夜/SAYO
}
VOICE_CREDIT = {'m1': '白上虎太郎', 'm2': '玄野武宏', 'm3': '雀松朱司', 'f1': '四国めたん', 'f2': '春日部つむぎ', 'f3': '小夜/SAYO'}
NATIVE_EMO = {'m1': {'joy', 'fear', 'angry'}, 'm2': {'joy', 'angry', 'sad'}, 'f1': {'joy', 'angry'}}

DISCIPLE = [
    ('greet', '師尊！お帰りなさいませ！', 'Master! Welcome back!', 'Guru! Selamat datang kembali!', 'joy'),
    ('greet_alt', '師尊、ご機嫌麗しゅう。', 'Master, I hope you are well.', 'Guru, semoga Guru sehat selalu.', 'normal'),
    ('thanks', 'ご指導、感謝いたします！', 'Thank you for your guidance!', 'Terima kasih atas bimbingannya!', 'normal'),
    ('breakthrough', 'やった！弟子、突破いたしました！', 'I did it! This disciple has broken through!', 'Berhasil! Murid ini telah menerobos!', 'joy'),
    ('cheer', 'さすが師尊！', 'As expected of Master!', 'Memang hebat, Guru!', 'joy'),
    ('careful', '師尊、お気をつけて！', 'Master, be careful!', 'Guru, hati-hati!', 'fear'),
    ('protect', '宗門を守れ！', 'Protect the sect!', 'Lindungi sekte!', 'angry'),
    ('attack', 'くらえ！', 'Take this!', 'Rasakan ini!', 'angry'),
    ('attack_alt', 'はぁっ！', 'Haah!', 'Haah!', 'angry'),
    ('technique', '奥義、発動！', 'Secret art, unleash!', 'Jurus rahasia, lepaskan!', 'angry'),
    ('victory', '勝ちました、師尊！', 'We won, Master!', 'Kita menang, Guru!', 'joy'),
    ('fear', 'ひ、ひぃっ……強すぎる……', 'Eek... it is too strong...', 'Hiii... terlalu kuat...', 'fear'),
    ('tired', '少し、休ませてください……', 'Please let me rest a little...', 'Izinkan saya istirahat sebentar...', 'sad'),
    ('cultivate', '修行あるのみ！', 'Nothing but cultivation!', 'Hanya ada latihan!', 'normal'),
    ('loyal', 'この命、師尊に捧げます！', 'I offer my life to you, Master!', 'Nyawa ini kupersembahkan untuk Guru!', 'normal'),
    ('formation', '陣法、準備完了です！', 'The formation is ready!', 'Formasi sudah siap!', 'normal'),
    ('injured', 'うっ……まだ、戦えます……', 'Ugh... I can still fight...', 'Ugh... saya masih bisa bertarung...', 'sad'),
    ('farewell', 'いってらっしゃいませ、師尊。', 'Have a safe journey, Master.', 'Selamat jalan, Guru.', 'calm'),
    ('sulk', 'やりたくありません。', "I don't want to.", 'Aku tidak mau.', 'sad'),
    ('rebel', '師匠、今日こそ挑ませてもらう！', 'Master, today I challenge you!', 'Guru, hari ini aku menantangmu!', 'angry'),
    ('submit', '負けました…ここに残ります。', 'I lost... I will stay here.', 'Aku kalah... aku akan tetap di sini.', 'sad'),
    ('leave_night', '師匠、お許しください。', 'Forgive me, Master.', 'Maafkan aku, Guru.', 'sad'),
    ('oath', '義兄弟の契りを結ぼう！', 'Let us swear to be siblings!', 'Mari bersumpah menjadi saudara!', 'joy'),
    ('tea', 'お茶をどうぞ。', 'Please, have some tea.', 'Silakan, minum tehnya.', 'calm'),
    ('grudge', 'お前だけは許さない。', 'I will never forgive you.', 'Aku tidak akan memaafkanmu.', 'angry'),
    ('beloved', 'あなたと一緒なら。', 'As long as I am with you.', 'Asal bersamamu.', 'calm'),
    ('leitai_salam', 'お手合わせ願います！', 'Please spar with me!', 'Mohon petunjuknya!', 'normal'),
    ('leitai_juara', '師匠、この栄冠は宗門のために！', 'Master, this title is for our sect!', 'Guru, gelar ini untuk sekte kita!', 'joy'),
    ('leitai_kalah', 'まだ力が足りない…次の大会こそ！', 'Not strong enough yet... next season!', 'Aku belum cukup kuat... musim depan!', 'sad'),
    ('climb_peak', '頂に着いた！', 'I reached the peak!', 'Aku sampai di puncak!', 'joy'),
    ('climb_illusion', 'これは幻だ…退かぬ！', 'This is only an illusion... I will not retreat!', 'Ini hanya ilusi… aku tidak akan mundur!', 'angry'),
    ('mourn', '安らかに眠れ。', 'Rest in peace.', 'Beristirahatlah dengan tenang.', 'sad'),
    ('study', 'この書、少しずつ分かってきた。', 'This scripture... I am starting to understand.', 'Kitab ini... aku mulai mengerti.', 'calm'),
    ('qingming', 'ご先祖様、我らの礼をお受けください。', 'Ancestors, accept our respects.', 'Para leluhur, terimalah hormat kami.', 'calm'),
    ('report', '宗主、今朝のご報告です。', "Sect Master, this morning's report.", 'Ketua, laporan pagi ini.', 'normal'),
    ('depart', '行ってまいります、師匠。', 'We are setting off, Master.', 'Murid berangkat, Guru.', 'normal'),
    ('mission_report', '師匠、ただいま戻りました。ご報告いたします。', 'Master, I have returned to report.', 'Guru, murid kembali dan melapor.', 'normal'),
    ('abandoned', '師匠……私を見捨てたのですか。', 'Master... you abandoned me?', 'Guru... meninggalkanku?', 'sad'),
    ('robe', '宗門の紋の新しい衣…ありがとうございます、師匠。', 'A new robe with the sect emblem... thank you, Master.', 'Jubah baru dengan lambang sekte... terima kasih, Guru.', 'joy'),
    ('envoy_return', '師匠、使者の務めを果たしてまいりました。', 'Master, the envoy duty is done.', 'Guru, tugas utusan telah selesai.', 'normal'),
    ('harvest', '師匠、良い収穫です！', 'A good harvest, Master!', 'Panennya bagus, Guru!', 'joy'),
    ('ore_found', '師匠、岩の中で何かが光っています！', 'Master, something is shining inside the rock!', 'Guru, ada yang bersinar di dalam batu!', 'joy'),
    # --- DESIGN 39.8.4 (39E, 24 lines) and 39.F.7 (39F, 16 lines): autonomous disciples and inter-sect feuds
    ('pamit', '師匠、少し町へ行ってまいります。', 'Master, I am off to town for a while.', 'Guru, murid pamit ke kota sebentar.', 'normal'),
    ('pulang_belanja', '師匠、ただいま戻りました。お土産です。', 'Master, I am back. I brought you something.', 'Guru, murid pulang. Ini oleh-olehnya.', 'joy'),
    ('tawar', '高すぎる！少しまけてくれ。', 'Too expensive! Knock a little off.', 'Terlalu mahal! Kurangi sedikit.', 'angry'),
    ('tawar_sepakat', 'よし、それで手を打とう。', 'All right, it is a deal.', 'Baiklah, sepakat.', 'joy'),
    ('lihat_lapak', 'ふむ…この丹薬、なかなか良い。', 'Hmm... this pill is not bad.', 'Hmm... pil ini lumayan.', 'calm'),
    ('copet', '待て、泥棒！', 'Stop, thief!', 'Berhenti, pencuri!', 'angry'),
    ('makan', 'わあ、美味しい！', 'Wow, delicious!', 'Wah, enak sekali!', 'joy'),
    ('latih', 'もう一太刀！', 'One more stroke!', 'Satu jurus lagi!', 'angry'),
    ('salam_pagi', 'おはようございます、先輩！', 'Good morning, Senior!', 'Selamat pagi, Kakak Senior!', 'joy'),
    ('tutup_masuk', 'これより閉関いたします。お邪魔なさらぬよう。', 'I will now enter seclusion. Please do not disturb me.', 'Murid akan menutup diri. Mohon jangan diganggu.', 'calm'),
    ('tutup_keluar', '閉関、終わりました…気が澄んでいる。', 'Seclusion is over... my qi feels clear.', 'Pengasingan selesai... qi-ku lebih jernih.', 'calm'),
    ('hufa_minta', '先輩、突破の護法をお願いできますか。', 'Senior, would you guard me while I break through?', 'Senior, maukah menjagaku saat menembus?', 'normal'),
    ('hufa_jaga', '安心して、私が護ります。', 'Be at ease, I will guard you.', 'Tenang, aku menjagamu.', 'calm'),
    ('tembus_sendiri', '今日、己の力で突破する！', 'Today I break through by my own strength!', 'Hari ini aku menembus dengan kekuatanku sendiri!', 'angry'),
    ('hati_lulus', '心魔など…私は揺るがない。', 'A heart demon... cannot shake me.', 'Iblis hati... tidak bisa menggoyahkanku.', 'calm'),
    ('dao_pilih', '我が道は、この道なり。', 'This Dao is my path.', 'Jalanku adalah Dao ini.', 'calm'),
    ('belajar_sendiri', '自分で悟った！', 'I understood it on my own!', 'Aku memahaminya sendiri!', 'joy'),
    ('serap_batu', 'この霊石…霊気が澄んでいる。', 'This spirit stone... its qi is pure.', 'Batu roh ini... qi-nya murni.', 'calm'),
    ('hadiah', 'これ、あなたに。受け取ってね。', 'This is for you. Please accept it.', 'Ini untukmu. Jangan ditolak, ya.', 'joy'),
    ('tertawan', '師匠…助けて…', 'Master... help me...', 'Guru... tolong aku...', 'fear'),
    ('diselamatkan', '師匠が来てくださった！信じていました。', 'Master came! I knew you would.', 'Guru datang! Aku tahu Guru pasti datang.', 'joy'),
    ('iblis_bisik', '力が…もっと力が欲しい…', 'Power... I need more power...', 'Kekuatan... aku butuh lebih banyak kekuatan...', 'sad'),
    ('ditebus', 'ありがとうございます、師匠…私は戻りました。', 'Thank you, Master... I have come back.', 'Terima kasih, Guru... aku kembali.', 'sad'),
    ('jalan_ganti', '自分の道を見つけた。', 'I have found my own path.', 'Aku sudah menemukan jalanku.', 'calm'),
    ('seteru_ejek', '小さな宗門の弟子が、ここに座るとはな。', 'A disciple of a petty sect dares sit here?', 'Murid sekte kecil berani duduk di sini?', 'normal'),
    ('seteru_ejek_guru', '師匠に礼儀を教わらなかったのか？', 'Did your master never teach you manners?', 'Gurumu tidak mengajarimu sopan santun?', 'angry'),
    ('seteru_balas', '口を慎め！', 'Watch your mouth!', 'Jaga mulutmu!', 'angry'),
    ('seteru_tantang', '勇気があるなら、擂台に上がれ！', 'If you dare, step onto the stage!', 'Kalau berani, naik ke panggung!', 'angry'),
    ('seteru_terima', 'その挑戦、受けて立つ。', 'Challenge accepted.', 'Tantangan diterima.', 'normal'),
    ('seteru_menang', '今日のことを覚えておけ。', 'Remember this day.', 'Ingat hari ini.', 'normal'),
    ('seteru_kalah', 'これで終わりだと思うな！', 'This is not over!', 'Ini belum selesai!', 'angry'),
    ('seteru_keroyok', '皆、やってしまえ！', 'Brothers, get them!', 'Saudara-saudara, hajar mereka!', 'angry'),
    ('seteru_sergap', 'この道は我が宗門のものだ。', 'This road belongs to our sect.', 'Jalan ini milik sekte kami.', 'normal'),
    ('seteru_lindungi', 'その村人を放せ！', 'Let that villager go!', 'Lepaskan warga itu!', 'angry'),
    ('seteru_curi', 'その巻物を返せ、盗人め！', 'Give back that scroll, thief!', 'Kembalikan gulungan itu, pencuri!', 'angry'),
    ('seteru_maaf', 'あの件は…申し訳なかった。', 'About that matter... I am sorry.', 'Aku... minta maaf atas kejadian itu.', 'sad'),
    ('seteru_sumpah', 'この恨み、必ず晴らすと誓う！', 'I swear I will repay this grudge!', 'Aku bersumpah akan membalas ini!', 'angry'),
    ('seteru_damai', '今日から、もう敵ではない。', 'From today on, we are enemies no more.', 'Mulai hari ini, kita bukan musuh lagi.', 'calm'),
    ('seteru_saingan', '次の大会は、私が勝つ。', 'Next season, I will win.', 'Musim depan, aku yang menang.', 'normal'),
    ('seteru_kekasih', '宗門が敵同士でも…私はあなたを選ぶ。', 'Even if our sects are enemies... I still choose you.', 'Walau sekte kita bermusuhan... aku tetap memilihmu.', 'calm'),
]
# readings Open JTalk gets wrong in the 39 lines (captions keep the kanji)
READ.update({'閉関': 'へいかん', '護法': 'ごほう', '心魔': 'しんま', '一太刀': 'ひとたち', '己の力': 'おのれのちから', '我が道': 'わがみち',
             '盗人': 'ぬすびと', '霊気': 'れいき'})

manifest = {'lines': [], 'disciple_voices': VOICE_CREDIT}
sounds = {}
en = {}
id_ = {}


def event(eid, names, sub_en, sub_id, stream=False):
    sounds[eid] = {'subtitle': 'subtitles.kultivator.' + eid,
                   'sounds': [{'name': n, 'stream': stream} for n in names]}
    en['subtitles.kultivator.' + eid] = sub_en
    id_['subtitles.kultivator.' + eid] = sub_id


for group, key, ja, cap_en, cap_id, style, emo, speed in LINES:
    fxname = {'heaven': 'heaven', 'demon': 'demon', 'boss': 'boss', 'elder': 'elder', 'rogue': 'room'}[group]
    name = write(f'{group}/{key}', lambda: FX[fxname](tts(ja, style, emo, speed=speed, pitch_scale=-0.06 if group == 'heaven' else None)))
    eid = f'voice.{group}.{key}'
    event(eid, [name], f'{SPEAKER_EN[group]} speaks', f'{SPEAKER_ID[group]} berbicara')
    en[f'kultivator.voice.{group}.{key}'] = cap_en
    id_[f'kultivator.voice.{group}.{key}'] = cap_id
    manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': group, 'style': style})

for key, ja, cap_en, cap_id, emo in DISCIPLE:
    en[f'kultivator.voice.disciple.{key}'] = cap_en
    id_[f'kultivator.voice.disciple.{key}'] = cap_id
    for vid, styles in VOICES.items():
        style = styles[emo]
        native = emo in NATIVE_EMO.get(vid, set())
        name = write(f'disciple/{vid}/{key}', lambda: fx_room(tts(ja, style, 'normal' if native else emo, speed=1.0),
                                                              seed=60 + len(manifest['lines'])))
        eid = f'voice.disciple.{vid}.{key}'
        event(eid, [name], 'Disciple speaks', 'Murid berbicara')
        manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': 'disciple', 'voice': vid, 'style': style})

# --- Dunia Kultivasi yang Luas (DESIGN 34.8): event voice.<key>, caption kultivator.voice.<key>
READ.update({'凡塵': 'ぼんじん', '霊舟': 'れいしゅう', '玉帝': 'ぎょくてい', '言伝': 'ことづて', '帳': 'ちょう',
             '空賊': 'くうぞく', '我が宮': 'わがみや', '水晶宮': 'すいしょうきゅう', '亡者': 'もうじゃ'})
WORLD_SPEAKER = {  # speaker -> (style, fx, speaker group for subtitles)
    'm1': (12, 'room', 'disciple'), 'm2': (11, 'room', 'disciple'), 'm3': (52, 'room', 'disciple'),
    'f1': (2, 'room', 'disciple'), 'f2': (8, 'room', 'disciple'), 'f3': (46, 'room', 'disciple'),
    'elder': (ELDER, 'elder', 'elder'), 'boss': (ANCIENT, 'boss', 'boss'), 'demon': (HEART_DEMON, 'demon', 'demon'),
    'heaven': (HEAVEN, 'heaven', 'heaven'), 'heaven_fierce': (HEAVEN_FIERCE, 'heaven', 'heaven'),
}
WORLD = [
    ('kota.auction_open', 'm2', '今宵の競売を始めます！', 'Tonight\'s auction begins!', 'Lelang malam ini dimulai!', 'joy'),
    ('kota.auction_sold', 'm2', '落札！', 'Sold!', 'Terjual!', 'joy'),
    ('kota.tea_welcome', 'f1', 'どうぞ、お茶が入りました。', 'Please sit, the tea is freshly brewed.', 'Silakan duduk, tehnya baru diseduh.', 'calm'),
    ('kota.tea_news', 'f1', 'もうお聞きになりましたか？', 'Have you heard the news?', 'Sudah dengar kabarnya?', 'normal'),
    ('kota.black_whisper', 'm3', '良い品だ…口の堅い者にだけな。', 'Fine goods... for those who know how to keep quiet.', 'Barang bagus… untuk orang yang tahu diam.', 'calm'),
    ('kota.guard_halt', 'm1', '止まれ！この町は宗門の庇護下にある。', 'Halt! This town is under a sect\'s protection.', 'Berhenti! Kota ini dilindungi sekte.', 'angry'),
    ('kota.wanderer_dao', 'elder', '道は違えど、語り合おうではないか。', 'Our paths differ. Let us exchange views.', 'Jalan kita berbeda. Mari bertukar pandangan.', 'calm'),
    ('kota.rival_claim', 'm3', 'その宝は我らのものだ！', 'That treasure is ours!', 'Harta itu milik kami!', 'angry'),
    ('kota.immortal_scorn', 'f3', '凡塵の者がここまで来たか。', 'Mortal dust made it all the way here?', 'Debu fana sampai juga ke sini?', 'calm'),
    ('kota.lecture', 'elder', '聞け。満ちた杯に道は流れぬ。', 'Listen. The Dao does not flow into a full cup.', 'Dengarkan. Dao tidak mengalir ke cangkir yang penuh.', 'calm'),
    ('kota.envoy_pleased', 'boss', '玉帝はお喜びである。', 'The Jade Emperor is pleased.', 'Kaisar Giok berkenan.', 'calm'),
    ('kota.peach_keeper', 'f2', 'この桃は、己の時に熟すもの。', 'These peaches ripen in their own time.', 'Persik ini matang menurut waktunya sendiri.', 'calm'),
    ('penjara.free_at_last', 'demon', 'ついに…自由だ！', 'At last... free!', 'Akhirnya… bebas!', 'angry'),
    ('penjara.resealed', 'heaven', '封印、ここに成る。', 'The seal is restored.', 'Segel telah pulih.', 'normal'),
    ('menara.next_floor', 'heaven', '登れ。次の階が待っている。', 'Climb. The next floor awaits.', 'Naiklah, lantai berikutnya menanti.', 'normal'),
    ('menara.shadow_self', 'm3', '我は汝なり。超えられるものなら超えてみよ。', 'I am you. Surpass me if you can.', 'Akulah dirimu. Kalahkan aku jika kau mampu.', 'calm'),
    ('gua.ancestor_trial', 'elder', '我が陣は汝の力ではなく、心を量る。', 'My formation weighs your heart, not your strength.', 'Formasiku menimbang hatimu, bukan kekuatanmu.', 'calm'),
    ('heaven.langit.meteor_shower', 'heaven', '星が雨のごとく降り注ぐ。', 'The stars fall like rain.', 'Bintang-bintang jatuh seperti hujan.', 'normal'),
    ('heaven.langit.twin_moons', 'heaven', '二つの月が夜を照らす。', 'Two moons light the night.', 'Dua bulan menerangi malam.', 'normal'),
    ('heaven.langit.treasure_born', 'heaven_fierce', '天地の宝、ここに生まる。', 'A treasure of Heaven and Earth is born.', 'Harta langit dan bumi telah lahir.', 'normal'),
    ('heaven.langit.blood_eclipse', 'heaven_fierce', '血の月…天が怒っている。', 'A blood moon... Heaven is enraged.', 'Bulan berdarah… langit murka.', 'normal'),
    ('naga.subdued', 'heaven', '龍、ここに伏す。', 'The dragon submits.', 'Naga telah tunduk.', 'normal'),
    ('naga.tribulation', 'heaven_fierce', '天が汝の霊獣を試す。', 'Heaven tests your spirit beast.', 'Langit menguji binatang rohmu.', 'normal'),
    ('naga.evolved', 'heaven_fierce', '古の血が目覚め、霊獣が進化する！', 'Ancient blood awakens, the spirit beast evolves!', 'Darah purba bangkit, binatang roh berevolusi!', 'normal'),
    ('naga.trib_failed', 'heaven', '時はまだ満ちぬ…再び立て。', 'The time is not yet ripe... rise again.', 'Belum waktunya… bangkitlah lagi.', 'normal'),
    ('naga.jiao_roar', 'boss', '我が空を乱すは何者ぞ！', 'Who dares disturb my sky?', 'Siapa berani mengusik langitku?', 'angry'),
    ('bawah.meng_po_offer', 'f3', '飲みなさい。悲しみを忘れるのです。', 'Drink, and forget your sorrow.', 'Minumlah, lupakan dukamu.', 'calm'),
    ('bawah.meng_po_refuse', 'f3', 'ならば、すべての記憶を抱いて裁きへ行きなさい。', 'Then go to judgement carrying all your memories.', 'Kalau begitu, pergilah ke pengadilan dengan semua ingatanmu.', 'calm'),
    ('bawah.yama_judgement', 'boss', '鏡の前で己の業を量れ！', 'Weigh your deeds before the mirror!', 'Timbang perbuatanmu di depan cermin!', 'angry'),
    ('bawah.yama_book', 'boss', '汝の名、この帳に記されておる。', 'Your name is written in this book.', 'Namamu tertulis di buku ini.', 'calm'),
    ('bawah.ox_head', 'm1', '亡者よ、ついて来い！', 'Soul, come with us!', 'Arwah, ikut kami!', 'angry'),
    ('bawah.ghost_plea', 'f2', 'どうか…言伝を。', 'Please... deliver my message.', 'Tolong… sampaikan pesanku.', 'sad'),
    ('laut.dragon_king', 'boss', '我が宮に参るは何者ぞ。', 'Who dares come to my palace?', 'Siapa yang berani datang ke istanaku?', 'calm'),
    ('laut.gate_guard', 'm1', '水晶宮へようこそ。', 'Welcome to the Crystal Palace.', 'Selamat datang di Istana Kristal.', 'normal'),
    ('laut.penglai_trial', 'elder', '汝の悟りを示せ。', 'Show me your insight.', 'Tunjukkan wawasanmu.', 'calm'),
    ('kapal.launch', 'm1', '霊舟、出航！', 'Spirit ship, set sail!', 'Kapal roh, berangkat!', 'joy'),
    ('kapal.cloud_leap', 'heaven_fierce', '雲海を越えよ！', 'Pierce the sea of clouds!', 'Menembus lautan awan!', 'normal'),
    ('kapal.arrive', 'm1', '到着いたしました。', 'We have arrived.', 'Kita telah tiba.', 'normal'),
    ('kapal.sky_pirates', 'm3', '空賊だ！積み荷を置いていけ！', 'Sky pirates! Leave your cargo!', 'Bajak langit! Tinggalkan muatanmu!', 'angry'),
    ('kapal.hull_broken', 'm1', '船体損傷、降下する！', 'Hull damaged, we are going down!', 'Lambung rusak, kita turun!', 'fear'),
    ('peta.site_found', 'heaven', '山海、汝の名を記す。', 'The mountains and seas record your name.', 'Gunung dan laut mencatat namamu.', 'normal'),
    # --- DESIGN 38.B.8 (38ST): the capital, group kotabesar.* (VoiceEvents.KOTABESAR)
    ('kotabesar.gate_welcome', 'm1', 'ようこそ、都へ。城内での私闘は禁じられている。', 'Welcome to the capital. Private fights are forbidden within the walls.', 'Selamat datang di ibu kota. Bertarung sendiri di dalam tembok dilarang.', 'normal'),
    ('kotabesar.curfew', 'm1', '夜鼓が鳴った。門を閉めるぞ！', 'The night drum has sounded. Closing the gates!', 'Genderang malam sudah berbunyi. Gerbang ditutup!', 'angry'),
    ('kotabesar.suspect', 'm1', 'おい、そこの者。妙な真似はするなよ。', 'You there. Don\'t try anything strange.', 'Hei, kau. Jangan macam-macam.', 'angry'),
    ('kotabesar.wanted', 'm1', '手配書の顔だ！捕らえろ！', 'That\'s the face on the wanted poster! Seize them!', 'Itu wajah di surat buronan! Tangkap!', 'angry'),
    ('kotabesar.fine_paid', 'm2', '罰金は納められた。行ってよし。', 'The fine is paid. You may go.', 'Denda sudah dibayar. Silakan pergi.', 'calm'),
    ('kotabesar.jail', 'm1', '牢で頭を冷やすがいい。', 'Cool your head in the cell.', 'Dinginkan kepalamu di sel.', 'calm'),
    ('kotabesar.bank_welcome', 'm2', '銭荘へようこそ。霊石はしかとお預かりいたします。', 'Welcome to the money house. Your spirit stones are safe with us.', 'Selamat datang di rumah uang. Batu rohmu aman bersama kami.', 'calm'),
    ('kotabesar.bank_note', 'm2', 'この為替手形は、どの都の銭荘でも換金できます。', 'This note can be cashed at the money house of any capital.', 'Surat wesel ini bisa ditukar di rumah uang ibu kota mana pun.', 'normal'),
    ('kotabesar.gamble_cut', 'm3', '切るぞ…さあ、天か地か！', 'Cutting... now, heaven or earth!', 'Kupotong… nah, langit atau bumi!', 'joy'),
    ('kotabesar.gamble_win', 'm3', '出た！霊光だ！', 'It\'s out! Spirit light!', 'Keluar! Cahaya roh!', 'joy'),
    ('kotabesar.gamble_lose', 'm3', 'ただの石…これもまた天命よ。', 'Just stone... that too is fate.', 'Cuma batu… itu pun takdir.', 'calm'),
    ('kotabesar.grand_auction', 'm2', '大競売会、ただいま開幕でございます！', 'The Grand Auction now begins!', 'Lelang Agung kini dimulai!', 'joy'),
    ('kotabesar.alchemy_start', 'elder', '丹会を始める。炉に火を入れよ！', 'The Pill Gathering begins. Light your furnaces!', 'Pertemuan Pil dimulai. Nyalakan tungkumu!', 'normal'),
    ('kotabesar.alchemy_winner', 'elder', '見事な丹だ。今回の首席はそなたである。', 'A splendid pill. You stand first this time.', 'Pil yang indah. Kali ini kaulah yang pertama.', 'joy'),
    ('kotabesar.smith_master', 'm1', '良い鉄は、良い火を待つものだ。', 'Good iron waits for good fire.', 'Besi yang baik menunggu api yang baik.', 'calm'),
    ('kotabesar.guild_bounty', 'f2', '新しい依頼が出ています。腕に覚えは？', 'New commissions are posted. Confident in your skill?', 'Ada misi baru. Yakin dengan kemampuanmu?', 'normal'),
    ('kotabesar.guild_rank', 'f2', 'おめでとうございます、位が一つ上がりました。', 'Congratulations, your rank has risen.', 'Selamat, pangkatmu naik satu.', 'joy'),
    ('kotabesar.recruit_call', 'm1', '我が宗門に入りたい者は、前へ！', 'Those who wish to join our sect, step forward!', 'Yang ingin masuk sekte kami, maju!', 'joy'),
    ('kotabesar.beast_market', 'm3', 'この子は満月の夜に生まれた霊狼だよ。', 'This one is a spirit wolf born under a full moon.', 'Yang ini serigala roh yang lahir di malam purnama.', 'normal'),
    ('kotabesar.inn_welcome', 'f1', 'いらっしゃいませ。お泊まりですか、お食事ですか？', 'Welcome! Staying the night, or here to eat?', 'Selamat datang! Mau menginap atau makan?', 'joy'),
    ('kotabesar.library_hush', 'elder', '書閣ではお静かに。', 'Quiet in the library, please.', 'Harap tenang di perpustakaan.', 'calm'),
    ('kotabesar.temple_bell', 'elder', '鐘の音に心を澄ませよ。', 'Let the bell clear your heart.', 'Biarkan lonceng menjernihkan hatimu.', 'calm'),
    ('kotabesar.teleport_ready', 'f3', '陣が開きます。目を閉じてください。', 'The array is opening. Please close your eyes.', 'Formasi terbuka. Silakan pejamkan mata.', 'calm'),
    ('kotabesar.dock_depart', 'm2', '出航！雲の上で会おう！', 'Setting sail! See you above the clouds!', 'Berangkat! Sampai jumpa di atas awan!', 'joy'),
    ('kotabesar.palace_audience', 'boss', '面を上げよ。', 'Raise your head.', 'Angkat kepalamu.', 'calm'),
    ('kotabesar.lantern_festival', 'f1', '灯籠祭りが始まりました！謎かけはいかが？', 'The Lantern Festival has begun! Care for a riddle?', 'Festival Lentera sudah dimulai! Mau teka-teki?', 'joy'),
    ('kotabesar.riddle_right', 'f1', '正解です！お見事！', 'Correct! Well done!', 'Benar! Hebat!', 'joy'),
    ('kotabesar.beast_tide', 'm1', '獣潮だ！城壁へ急げ！', 'A beast tide! To the walls, quickly!', 'Gelombang binatang! Cepat ke tembok!', 'angry'),
    ('kotabesar.defense_won', 'm1', '都は守られた！', 'The capital is saved!', 'Ibu kota selamat!', 'joy'),
    ('kotabesar.tournament_open', 'boss', '天下の英傑よ、この擂台で力を示せ！', 'Heroes of the realm, prove your strength upon this stage!', 'Para pahlawan dunia, tunjukkan kekuatan kalian di panggung ini!', 'normal'),
    # --- DESIGN 38.S.6 (38ST): villages, compounds, caves, roads, repairs, group tempat.* and the capital arc cerita.*
    ('tempat.village_elder', 'elder', '旅のお方、村で少し休んでいきなされ。', 'Traveller, rest a while in our village.', 'Pengembara, beristirahatlah sebentar di desa kami.', 'calm'),
    ('tempat.village_fear', 'elder', 'ど、どうかお許しを…しゅうりは我らがいたします。', 'P-please forgive us... we will repair it ourselves.', 'M-mohon ampun... kami sendiri yang akan memperbaikinya.', 'fear'),
    ('tempat.bill_request', 'm1', '恐れながら…壊れた分の弁償をお願いしたい。', 'With respect... we must ask you to pay for the damage.', 'Maaf... kami harus meminta ganti rugi atas kerusakannya.', 'normal'),
    ('tempat.bill_paid', 'm1', 'ありがたい。すぐに職人を集めます。', 'Thank you. I\'ll gather the craftsmen at once.', 'Terima kasih. Akan segera kukumpulkan para tukang.', 'joy'),
    ('tempat.bill_refused', 'm1', '…この恨み、町は忘れませんぞ。', '...The town will not forget this.', '...Kota ini tidak akan melupakannya.', 'angry'),
    ('tempat.repair_work', 'm2', 'よいしょ！もう一枚！', 'Heave! One more plank!', 'Hup! Satu papan lagi!', 'joy'),
    ('tempat.clan_patriarch', 'boss', '我が一族の地に、何用か。', 'What business brings you to my clan\'s land?', 'Ada urusan apa di tanah klanku?', 'calm'),
    ('tempat.young_master', 'm3', '身の程を知れ。この地で俺に逆らうとはな！', 'Know your place. Defying me on my own land!', 'Tahu dirilah. Berani menentangku di tanahku sendiri!', 'angry'),
    ('tempat.hermit', 'elder', '縁ある者よ、座るがよい。', 'You who share a bond with me, sit.', 'Engkau yang berjodoh denganku, duduklah.', 'calm'),
    ('tempat.hermit_koan', 'elder', '空の器にこそ、道は満ちる。', 'Only an empty vessel can be filled with the Dao.', 'Hanya wadah yang kosong dapat diisi Dao.', 'calm'),
    ('tempat.inheritance', 'heaven', '我が遺産を継ぐ資格、示してみよ。', 'Show me you are worthy of my legacy.', 'Tunjukkan bahwa kau layak mewarisi peninggalanku.', 'normal'),
    ('tempat.inheritance_pass', 'heaven', 'よかろう。我が道、汝に託す。', 'Very well. I entrust my path to you.', 'Baiklah. Jalanku kuserahkan padamu.', 'calm'),
    ('tempat.demon_hideout', 'demon', '生きて帰れると思うな！', 'Don\'t think you\'ll leave alive!', 'Jangan kira kau bisa pulang hidup-hidup!', 'angry'),
    ('tempat.captive_saved', 'f1', '助かった…本当にありがとうございます！', 'We\'re saved... thank you, truly!', 'Kami selamat... terima kasih, sungguh!', 'joy'),
    ('tempat.waystation', 'm2', '駅で一休みしていきな。水も飯もあるぞ。', 'Rest a bit at the post. There\'s water and food.', 'Mampirlah istirahat di pos. Ada air dan makanan.', 'normal'),
    ('tempat.peddler', 'm2', '珍しい品だよ、遠い町から運んできたんだ！', 'Rare goods, carried all the way from distant towns!', 'Barang langka, kubawa dari kota yang jauh!', 'joy'),
    ('tempat.bandit', 'm3', '通行料を置いていけ！', 'Leave a toll if you want to pass!', 'Tinggalkan upeti kalau mau lewat!', 'angry'),
    ('tempat.tribulation_peak', 'heaven', 'この峰は、幾多の劫を見届けてきた。', 'This peak has witnessed countless tribulations.', 'Puncak ini telah menyaksikan tak terhitung kesengsaraan.', 'calm'),
    ('tempat.sect_visit', 'm1', '本宗に何のご用でしょうか。', 'What brings you to our sect?', 'Ada keperluan apa ke sekte kami?', 'normal'),
    ('tempat.child_play', 'f1', '鬼さんこちら！', 'Catch me if you can!', 'Ayo tangkap aku!', 'joy'),
    ('cerita.ruyi_thanks', 'f2', 'このご恩は、決して忘れません。', 'I will never forget this kindness.', 'Budi ini takkan pernah kulupakan.', 'calm'),
    ('cerita.moying_reveal', 'm3', '気づくのが遅かったな…！', 'You noticed too late...!', 'Kau terlambat menyadarinya...!', 'angry'),
    ('cerita.bao_verdict', 'elder', '証拠は揃った。判決を下す！', 'The evidence is complete. I pronounce the verdict!', 'Bukti sudah lengkap. Putusan dijatuhkan!', 'angry'),
    ('cerita.coronation', 'boss', '今日より、この都を汝に託す。', 'From this day, I entrust this capital to you.', 'Mulai hari ini, kota ini kuserahkan padamu.', 'calm'),
    # --- DESIGN 38.T.12 (38ST): the five themed cities and trade, group tema.<tema>.* (VoiceEvents.TEMA_*)
    ('tema.pelabuhan.welcome', 'f2', '港へようこそ。荷を下ろすなら、まず税関へ。', 'Welcome to the harbour. If you\'re unloading, customs first.', 'Selamat datang di pelabuhan. Kalau mau bongkar muatan, ke bea cukai dulu.', 'normal'),
    ('tema.pelabuhan.fish_call', 'f1', '獲れたてだよ！朝の霊魚、今だけだよ！', 'Fresh from the sea! Morning spirit fish, only for now!', 'Baru ditangkap! Ikan roh pagi, cuma sekarang!', 'joy'),
    ('tema.pelabuhan.ship_arrive', 'm2', '南の船が入ったぞ！荷揚げを急げ！', 'A southern ship is in! Hurry with the unloading!', 'Kapal selatan masuk! Cepat bongkar muatan!', 'joy'),
    ('tema.pelabuhan.ship_depart', 'm2', '錨を上げろ！帆を張れ！', 'Weigh anchor! Hoist the sails!', 'Angkat jangkar! Kembangkan layar!', 'joy'),
    ('tema.pelabuhan.lighthouse', 'elder', 'この灯が消えたら、海は人を帰さぬ。', 'If this light goes out, the sea won\'t send anyone home.', 'Kalau pelita ini padam, laut tak akan memulangkan siapa pun.', 'calm'),
    ('tema.pelabuhan.pirates', 'm1', '海賊だ！桟橋を守れ！', 'Pirates! Defend the piers!', 'Bajak laut! Pertahankan dermaga!', 'angry'),
    ('tema.pelabuhan.black_shark', 'm3', 'この港の灯は、俺がもらう。', 'This harbour\'s light is mine to take.', 'Pelita pelabuhan ini akan kuambil.', 'angry'),
    ('tema.pelabuhan.sea_crossing', 'm2', '霧の向こうは果てなき海だ。しっかり掴まってな。', 'Beyond the fog lies the Boundless Sea. Hold on tight.', 'Di balik kabut ada Laut Tanpa Batas. Pegangan yang kuat.', 'normal'),
    ('tema.pelabuhan.goddess_day', 'elder', '海の女神に祈りを。今年も無事の航海を。', 'Pray to the Sea Goddess, for another year of safe voyages.', 'Berdoalah pada Dewi Laut, demi pelayaran selamat setahun lagi.', 'calm'),
    ('tema.pelabuhan.shipwright', 'elder', '良い船は、竜骨から嘘をつかん。', 'A good ship never lies, starting from its keel.', 'Kapal yang baik tak pernah berbohong, mulai dari lunasnya.', 'calm'),
    ('tema.pelabuhan.lamp_lit', 'elder', '灯がともった…おかえり、みんな。', 'The light is lit... welcome home, everyone.', 'Pelita menyala... selamat pulang, semuanya.', 'joy'),
    ('tema.oasis.welcome', 'm1', '玉門を越えた先は砂の海だ。水は持ったか？', 'Beyond the Jade Gate lies a sea of sand. Got your water?', 'Di balik Gerbang Giok ada lautan pasir. Sudah bawa air?', 'normal'),
    ('tema.oasis.bazaar', 'm3', 'さあさあ、西の香辛料に絨毯だ！値段は相談次第！', 'Come, come! Western spices and carpets! The price is open to talk!', 'Ayo, ayo! Rempah Barat dan permadani! Harga bisa dirunding!', 'joy'),
    ('tema.oasis.haggle_no', 'm3', 'それじゃ商売にならんよ、旦那。', 'That won\'t make me a living, sir.', 'Kalau segitu, aku tak bisa makan, Tuan.', 'angry'),
    ('tema.oasis.haggle_yes', 'm3', 'ええい、持ってけ！今日だけだぞ！', 'Fine, take it! Only today!', 'Ah, ambillah! Hanya hari ini!', 'joy'),
    ('tema.oasis.caravan', 'm1', '隊商が着いたぞ！駱駝に水をやれ！', 'The caravan has arrived! Water the camels!', 'Kafilah sudah tiba! Beri minum unta-unta!', 'joy'),
    ('tema.oasis.sandstorm', 'm1', '砂嵐だ！駱駝を伏せさせろ、中へ入れ！', 'Sandstorm! Make the camels kneel, get inside!', 'Badai pasir! Suruh unta berlutut, masuk ke dalam!', 'angry'),
    ('tema.oasis.water_priestess', 'f3', '水は命。一滴たりとも無駄にしてはなりません。', 'Water is life. Not one drop must be wasted.', 'Air adalah kehidupan. Tak satu tetes pun boleh terbuang.', 'calm'),
    ('tema.oasis.lord', 'boss', 'この町が砂に呑まれる日は来させぬ。', 'I will not let the day come when sand swallows this town.', 'Takkan kubiarkan datang hari pasir menelan kota ini.', 'calm'),
    ('tema.oasis.scorpion', 'demon', '水は我が蠍のもの。砂に還れ！', 'The water belongs to my scorpion. Return to the sand!', 'Air ini milik kalajengkingku. Kembalilah ke pasir!', 'angry'),
    ('tema.oasis.dance', 'f1', '今宵は満月、旋舞をご覧あれ！', 'Tonight is the full moon. Behold the whirling dance!', 'Malam ini purnama, saksikanlah tari putar!', 'joy'),
    ('tema.air.welcome', 'f1', '舟に乗っていくかい？町を一回りしてあげるよ。', 'Want a boat ride? I\'ll take you round the town.', 'Mau naik perahu? Kuantar keliling kota.', 'joy'),
    ('tema.air.boat_song', 'f1', '水は流れて、橋はめぐる…', 'The water flows, the bridges pass by...', 'Air mengalir, jembatan silih berganti...', 'calm'),
    ('tema.air.silk', 'f3', '蘇家の絹は、五代にわたり嘘をついたことがありません。', 'The silk of House Su has not lied in five generations.', 'Sutra keluarga Su tak pernah berbohong selama lima generasi.', 'calm'),
    ('tema.air.tea', 'm1', '一煎目は香りを、二煎目は心を味わうのです。', 'The first steep is for the fragrance, the second for the heart.', 'Seduhan pertama untuk harumnya, seduhan kedua untuk hatinya.', 'calm'),
    ('tema.air.thief', 'm2', '泥棒だ！舟で逃げたぞ！', 'Thief! He\'s escaping by boat!', 'Pencuri! Dia kabur naik perahu!', 'angry'),
    ('tema.air.lantern_night', 'f1', '灯籠を流しましょう。迷った魂が帰れるように。', 'Let\'s float the lanterns, so the lost souls can find their way home.', 'Mari melepas lentera, agar arwah yang tersesat bisa pulang.', 'calm'),
    ('tema.air.ghost', 'f2', 'わたしの布は…まだ織りかけなの。', 'My cloth... is still unfinished.', 'Kainku... masih belum selesai ditenun.', 'calm'),
    ('tema.air.ghost_rest', 'f2', 'ありがとう…これで、やっと眠れる。', 'Thank you... now, at last, I can sleep.', 'Terima kasih... akhirnya aku bisa tidur.', 'calm'),
    ('tema.air.dragon_boat', 'm1', '太鼓を合わせろ！竜舟、進め！', 'Match the drum! Dragon boat, forward!', 'Ikuti genderang! Perahu naga, maju!', 'joy'),
    ('tema.air.poet', 'elder', '雨を聴く亭で、詩を一つ詠まぬか。', 'Will you not compose a verse in the Pavilion for Listening to Rain?', 'Maukah kau menggubah satu bait di Paviliun Menyimak Hujan?', 'calm'),
    ('tema.gunung.welcome', 'm1', '千段の石段の始まりだ。足元に気をつけて。', 'This is the start of the thousand stone steps. Watch your feet.', 'Inilah awal seribu anak tangga. Hati-hati langkahmu.', 'normal'),
    ('tema.gunung.porter', 'm2', '一歩ずつ、一歩ずつ。山は逃げやせん。', 'One step at a time. The mountain isn\'t going anywhere.', 'Selangkah demi selangkah. Gunung tak akan lari.', 'calm'),
    ('tema.gunung.lift', 'm1', '乗った乗った！雲の上まで一息だ！', 'All aboard! Up above the clouds in one breath!', 'Ayo naik! Ke atas awan dalam sekali napas!', 'joy'),
    ('tema.gunung.lift_stuck', 'm1', 'くそっ、綱が…！動くな、今助ける！', 'Damn, the rope...! Don\'t move, I\'m coming!', 'Sial, talinya...! Jangan bergerak, aku datang!', 'angry'),
    ('tema.gunung.monkey', 'f1', 'あっ、猿に饅頭を取られたね。果物を投げてごらん。', 'Oh, the monkey took your bun. Try throwing it some fruit.', 'Wah, bakpaomu direbut kera. Coba lempar buah.', 'joy'),
    ('tema.gunung.rockfall', 'm1', '落石だ！道から離れろ！', 'Falling rocks! Get off the path!', 'Batu runtuh! Menjauh dari jalan!', 'angry'),
    ('tema.gunung.abbot', 'elder', '山頂に至る道は千あれど、心に至る道は一つ。', 'A thousand paths reach the summit, but only one reaches the heart.', 'Seribu jalan menuju puncak, tetapi hanya satu menuju hati.', 'calm'),
    ('tema.gunung.pilgrim', 'm2', '三歩一拝、願いよ届け。', 'Three steps, one bow. Let my wish be heard.', 'Tiga langkah satu sujud, semoga permohonanku sampai.', 'calm'),
    ('tema.gunung.buddha_light', 'f3', '見なさい、雲の上の光の輪を。仏光です。', 'Look, the ring of light upon the clouds. The Buddha\'s light.', 'Lihatlah lingkaran cahaya di atas awan. Cahaya Buddha.', 'joy'),
    ('tema.gunung.cloud_bell', 'elder', '雲の鐘が鳴る。山はあなたを覚えました。', 'The cloud bell rings. The mountain remembers you.', 'Lonceng Awan berbunyi. Gunung kini mengingatmu.', 'calm'),
    ('tema.iblis.gate_toll', 'm1', '通るなら貢ぎ物を置いていけ。命よりは安いぞ。', 'Leave a tribute if you\'d pass. It\'s cheaper than your life.', 'Kalau mau lewat, tinggalkan upeti. Lebih murah dari nyawamu.', 'normal'),
    ('tema.iblis.gate_weak', 'm1', 'こんな小物、餌にもならん。帰れ。', 'Small fry like you isn\'t even good bait. Go home.', 'Ikan teri sepertimu bahkan tak pantas jadi umpan. Pulang sana.', 'normal'),
    ('tema.iblis.pact', 'm3', '市場の中で血を流すな。それがこの町唯一の掟だ。', 'Spill no blood inside the market. That is this town\'s only law.', 'Jangan tumpahkan darah di dalam pasar. Itulah satu-satunya hukum kota ini.', 'calm'),
    ('tema.iblis.hunt_hour', 'demon', '夜道の通行料だ。金か、血か？', 'A toll for the night road. Gold, or blood?', 'Upeti jalan malam. Uang, atau darah?', 'angry'),
    ('tema.iblis.arena_open', 'm1', '血の階段へようこそ！生きて降りられるかな？', 'Welcome to the Blood Ladder! Think you\'ll walk down alive?', 'Selamat datang di Tangga Darah! Yakin bisa turun hidup-hidup?', 'joy'),
    ('tema.iblis.arena_win', 'm1', '見たか！新しい名が血に刻まれたぞ！', 'Did you see?! A new name is carved in blood!', 'Kalian lihat?! Nama baru terukir dengan darah!', 'joy'),
    ('tema.iblis.broker', 'f3', '欲しいものは何？ここでは、値段のないものはないのよ。', 'What do you desire? Here, nothing is without a price.', 'Apa yang kau inginkan? Di sini, tak ada yang tak berharga.', 'calm'),
    ('tema.iblis.forbidden', 'elder', 'この書は、読む者の何かを喰らう。それでも開くか？', 'This book devours something of whoever reads it. Will you still open it?', 'Kitab ini memakan sesuatu dari pembacanya. Masih mau membukanya?', 'calm'),
    ('tema.iblis.captive', 'f1', 'おねがい…村に帰りたいの。', 'Please... I want to go home to my village.', 'Tolong... aku ingin pulang ke desaku.', 'calm'),
    ('tema.iblis.lord', 'boss', '正道の者が、わざわざ地獄の門を叩くとはね。', 'How rare, a righteous one knocking at hell\'s gate of their own accord.', 'Jarang sekali, orang jalan lurus mengetuk gerbang neraka atas kemauan sendiri.', 'calm'),
    ('tema.iblis.hunter', 'f2', '正道の名において、その首、貰い受ける！', 'In the name of the righteous path, your head is forfeit!', 'Atas nama jalan lurus, kepalamu kuambil!', 'angry'),
    ('tema.iblis.raid', 'm1', '正道の連中が門に来た！迎え撃て！', 'The righteous are at the gate! Meet them head on!', 'Orang-orang jalan lurus di gerbang! Hadapi mereka!', 'angry'),
    ('tema.iblis.dawn', 'm3', 'この町にも、朝は来るんだな。', 'So even this town gets a morning.', 'Ternyata kota ini pun bisa didatangi pagi.', 'calm'),
    ('tema.dagang.sold', 'm2', '良い取引でした。また来てくださいよ。', 'A fine deal. Do come again.', 'Transaksi yang bagus. Datang lagi, ya.', 'joy'),
    ('tema.dagang.saturated', 'm2', 'その品はもう溢れててね…安くしか買えんよ。', 'We\'re already flooded with that... I can only buy it cheap.', 'Barang itu sudah membanjir... aku cuma bisa beli murah.', 'normal'),
]
# readings Open JTalk gets wrong in the 38 lines (captions keep the kanji)
READ.update({'夜鼓': 'やこ', '銭荘': 'せんそう', '為替手形': 'かわせてがた', '霊光': 'れいこう', '丹会': 'たんかい', '首席': 'しゅせき',
             '霊狼': 'れいろう', '書閣': 'しょかく', '獣潮': 'じゅうちょう', '英傑': 'えいけつ', '幾多の劫': 'いくたのごう', '本宗': 'ほんしゅう',
             '縁ある': 'えんある', '汝': 'なんじ', '霊魚': 'れいぎょ', '竜骨': 'りゅうこつ', '玉門': 'ぎょくもん', '旋舞': 'せんぶ', '蘇家': 'そけ',
             '一煎目': 'いっせんめ', '二煎目': 'にせんめ', '竜舟': 'りゅうしゅう', '詠まぬ': 'よまぬ', '三歩一拝': 'さんぽいっぱい', '仏光': 'ぶっこう',
             '正道': 'せいどう', '貢ぎ物': 'みつぎもの', '喰らう': 'くらう', '蠍': 'さそり', '果てなき': 'はてなき'})
for key, spk, ja, cap_en, cap_id, emo in WORLD:
    style, fxname, group = WORLD_SPEAKER[spk]
    native = spk.startswith(('m', 'f')) and emo in NATIVE_EMO.get(spk, set())
    if spk.startswith(('m', 'f')) and native:
        style = VOICES[spk][emo]
    def make():
        raw = tts(ja, style, 'normal' if native else emo, speed=0.88 if group == 'heaven' else 0.95,
                  pitch_scale=-0.06 if group == 'heaven' else None)
        return fx_room(raw, seed=90) if fxname == 'room' else FX[fxname](raw)
    name = write('world/' + key.replace('.', '/'), make)
    eid = f'voice.{key}'
    event(eid, [name], f'{SPEAKER_EN[group]} speaks' if group != 'disciple' else 'Someone speaks',
          f'{SPEAKER_ID[group]} berbicara' if group != 'disciple' else 'Seseorang berbicara')
    en[f'kultivator.voice.{key}'] = cap_en
    id_[f'kultivator.voice.{key}'] = cap_id
    manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': spk, 'style': style})

# --- Gabung Sekte (41) + Kota yang Hidup (42): 133 named-speaker lines, source docs/progres/m2-voices.md (added by F-END).
# Clips under sounds/voice/world/gabung/** and world/kota42/**, events voice.<key>, captions kultivator.voice.<key>,
# speaker captions subtitles.kultivator.voice.<key>. VoiceEvents.GABUNG / KOTA42 mirror these keys.
READ.update({'二更': 'にこう', '四更': 'しこう', '五更': 'ごこう', '王手': 'おうて', '膏薬': 'こうやく', '宗門': 'しゅうもん',
             '庇護': 'ひご', '礎': 'いしずえ', '貴宗': 'きしゅう', '新郎新婦': 'しんろうしんぷ', '故人': 'こじん', '偲び': 'しのび',
             '判決': 'はんけつ', '拍子木': 'ひょうしぎ', '師匠': 'ししょう', '旦那': 'だんな', '影絵芝居': 'かげえしばい',
             '一座': 'いちざ', '妙技': 'みょうぎ', 'お立ち会い': 'おたちあい', '学堂': 'がくどう', '守護宗門': 'しゅごしゅうもん'})
KV41_42 = [  # (key, speaker, ja, en, id, emotion, speaker_en, speaker_id) from docs/progres/m2-voices.md
    ('gabung.gerbang_panggil', 'm1', '入門試験を受ける者は、前へ出よ！', 'Those taking the entrance exam, step forward!', 'Yang ikut ujian masuk, maju!', 'angry', 'Sect Gate Guard', 'Penjaga Gerbang Sekte'),
    ('gabung.tangga_tekanan', 'elder', '心を静めよ。山はお前を試している。', 'Still your heart. The mountain is testing you.', 'Tenangkan hatimu. Gunung sedang mengujimu.', 'calm', 'Admissions Elder', 'Tetua Penerimaan'),
    ('gabung.ujian_mulai', 'm2', '試験、始め！', 'The test, begin!', 'Ujian, mulai!', 'normal', 'Proctor Disciple', 'Murid Pengawas'),
    ('gabung.ujian_lulus', 'elder', 'ふむ…悪くない。', 'Hmm... not bad.', 'Hmm... tidak buruk.', 'calm', 'Admissions Elder', 'Tetua Penerimaan'),
    ('gabung.ujian_gagal', 'elder', '縁がなかったようだな。三日後にまた来い。', 'It seems fate is not with you yet. Come back in three days.', 'Tampaknya belum berjodoh. Datanglah tiga hari lagi.', 'calm', 'Admissions Elder', 'Tetua Penerimaan'),
    ('gabung.diterima', 'elder', '今日より、お前は我が宗門の弟子だ。', 'From this day on, you are a disciple of our sect.', 'Mulai hari ini, kau murid sekte kami.', 'joy', 'Admissions Elder', 'Tetua Penerimaan'),
    ('gabung.istimewa', 'elder', 'これほどの才…誰が弟子に取るか、揉めそうだな。', 'Talent like this... the elders will fight over who takes you.', 'Bakat sebesar ini... para tetua akan berebut.', 'joy', 'Admissions Elder', 'Tetua Penerimaan'),
    ('gabung.sumpah_aturan_1', 'elder', '同門を害するなかれ。', 'Never harm a fellow disciple.', 'Jangan mencelakai saudara seperguruan.', 'calm', 'Law Elder', 'Tetua Hukum'),
    ('gabung.sumpah_aturan_2', 'elder', '宗門の秘伝を漏らすなかれ。', "Never betray the sect's secret teachings.", 'Jangan membocorkan warisan rahasia sekte.', 'calm', 'Law Elder', 'Tetua Hukum'),
    ('gabung.sumpah_aturan_3', 'elder', '宗門の危機には、必ず駆けつけよ。', 'When the sect is in danger, come without fail.', 'Saat sekte dalam bahaya, datanglah tanpa ragu.', 'calm', 'Law Elder', 'Tetua Hukum'),
    ('gabung.shifu_pilih', 'elder', 'わしの弟子にならぬか？', 'Will you become my disciple?', 'Maukah kau menjadi muridku?', 'joy', 'Master', 'Shifu'),
    ('gabung.shifu_lindungi', 'elder', 'わしの弟子に手を出すとは、いい度胸だ。', 'You have nerve, laying a hand on my disciple.', 'Berani sekali menyentuh muridku.', 'angry', 'Master', 'Shifu'),
    ('gabung.shifu_lampaui', 'elder', 'はっはっは！弟子が師を超えたか！', 'Hahaha! The disciple has surpassed the master!', 'Hahaha! Murid sudah melampaui guru!', 'joy', 'Master', 'Shifu'),
    ('gabung.sapa_adik', 'f1', '師弟、おはよう！', 'Good morning, little martial brother!', 'Adik seperguruan, selamat pagi!', 'joy', 'Senior Sister', 'Kakak Seperguruan'),
    ('gabung.sapa_kakak', 'm3', '師兄、お疲れ様です。', 'Senior brother, thank you for your hard work.', 'Kakak senior, terima kasih atas kerja kerasnya.', 'normal', 'Junior Brother', 'Adik Seperguruan'),
    ('gabung.saingan_ejek', 'm3', '雑用係がここで何をしている？', 'What is an errand boy doing here?', 'Pesuruh sedang apa di sini?', 'angry', 'Rival Disciple', 'Saingan Seperguruan'),
    ('gabung.panggilan_perang', 'm1', '敵が山門に迫っている！弟子たちよ、集え！', 'The enemy nears the mountain gate! Disciples, assemble!', 'Musuh mendekati gerbang! Para murid, berkumpul!', 'angry', 'Sect Gate Guard', 'Penjaga Gerbang Sekte'),
    ('gabung.hukum_tegur', 'elder', '門規を忘れたか。', "Have you forgotten the sect's rules?", 'Sudah lupa aturan sekte?', 'angry', 'Law Elder', 'Tetua Hukum'),
    ('gabung.hukum_kurung', 'elder', '思過崖で己を省みよ。', 'Reflect on yourself at the Cliff of Contemplation.', 'Renungkan dirimu di Tebing Renungan.', 'calm', 'Law Elder', 'Tetua Hukum'),
    ('gabung.usir', 'elder', '今日をもって、お前を破門とする。二度と我が名を口にするな。', 'As of today you are expelled. Never speak my name again.', 'Mulai hari ini kau diusir. Jangan pernah sebut namaku lagi.', 'angry', 'Sect Master', 'Ketua Sekte'),
    ('gabung.pamit', 'elder', '行くがよい。道は一つではない。', 'Go. There is more than one road.', 'Pergilah. Jalan tidak hanya satu.', 'calm', 'Master', 'Shifu'),
    ('gabung.pemburu', 'm1', '裏切り者め、宗門の名において捕らえる！', 'Traitor, in the name of the sect, you are under arrest!', 'Pengkhianat, atas nama sekte kau kutangkap!', 'angry', 'Sect Hunter', 'Pemburu Sekte'),
    ('gabung.ketua_serah', 'boss', 'この衣と鉢を、お前に託そう。', 'This robe and this bowl, I entrust to you.', 'Jubah dan mangkuk ini, kuserahkan padamu.', 'calm', 'Old Sect Master', 'Ketua Lama'),
    ('gabung.ketua_baru', 'm2', '新宗主に、拝礼！', 'Pay respects to the new Sect Master!', 'Hormat kepada Ketua baru!', 'joy', 'Proctor Disciple', 'Murid Pengawas'),
    ('gabung.tamu_sambut', 'm2', '客卿長老、ようこそお越しくださいました。', 'Welcome, Guest Elder.', 'Selamat datang, Tetua Tamu.', 'normal', 'Proctor Disciple', 'Murid Pengawas'),
    ('gabung.naik_kelas', 'heaven', '一宗、天に名を連ねる。', 'A sect writes its name among the heavens.', 'Satu sekte mencatatkan namanya di langit.', 'normal', 'Heaven', 'Langit'),
    ('gabung.turnamen_juara', 'elder', '見事だ。今日より、お前が門内一の弟子だ。', 'Splendid. From today you are the first disciple of this school.', 'Luar biasa. Mulai hari ini, kaulah murid terbaik perguruan.', 'joy', 'Sect Elder', 'Tetua Sekte di lei tai'),
    ('gabung.pelajaran_mulai', 'elder', '座れ。今日は一つ、大事なことを教えよう。', 'Sit. Today I will teach you one thing that matters.', 'Duduklah. Hari ini akan kuajarkan satu hal penting.', 'calm', 'Master', 'Shifu'),
    ('kota42.sapa_pagi', 'f1', 'おはようございます！今日もいい天気ですね。', 'Good morning! Lovely weather today.', 'Selamat pagi! Cuacanya bagus hari ini.', 'joy', 'Townswoman', 'Warga Perempuan'),
    ('kota42.sapa_kenal', 'm2', 'おや、旦那！また来てくださったんですね。', 'Why, sir! Back again, I see.', 'Wah, Tuan! Datang lagi rupanya.', 'joy', 'Regular Merchant', 'Pedagang Langganan'),
    ('kota42.sapa_asing', 'm3', '見かけない顔だな。旅のお方かい？', "Don't know your face. A traveller, eh?", 'Wajahmu asing. Pengembara, ya?', 'normal', 'Wary Townsman', 'Warga Curiga'),
    ('kota42.sapa_akrab', 'f2', 'あら、いらっしゃい！お茶でも飲んでいって。', 'Oh, come in! Have some tea first.', 'Wah, mampir! Minum teh dulu.', 'joy', 'Neighbour Auntie', 'Bibi Tetangga'),
    ('kota42.terima_kasih', 'f1', '本当にありがとうございます。このご恩は忘れません。', "Thank you, truly. I won't forget this kindness.", 'Terima kasih sungguh. Budi ini takkan kulupakan.', 'joy', 'Grateful Townswoman', 'Warga yang Ditolong'),
    ('kota42.diselamatkan', 'm2', 'あの火事のとき、あなたがいなければ…！', "That fire... if it weren't for you, sir...!", 'Waktu kebakaran itu, kalau bukan karena Tuan...!', 'sad', 'Rescued Townsman', 'Warga yang Diselamatkan'),
    ('kota42.anak_kagum', 'f1', 'すごい！お兄さん、空を飛べるの？', 'Wow! Can you really fly?', 'Hebat! Kakak bisa terbang?', 'joy', 'Town Child', 'Anak Kota'),
    ('kota42.tanya_jalan', 'm1', 'ああ、それなら東へまっすぐ、橋を渡った先ですよ。', "Oh, that's straight east, just across the bridge.", 'Oh, itu lurus ke timur, di seberang jembatan.', 'normal', 'Helpful Townsman', 'Warga Ramah'),
    ('kota42.tidak_tahu', 'f3', 'ごめんなさい、私も来たばかりで…井戸の奥さんたちに聞いてみて。', "Sorry, I've only just arrived too... ask the women at the well.", 'Maaf, aku juga baru datang... tanyakan ibu-ibu di sumur.', 'normal', 'Newcomer', 'Pendatang Baru'),
    ('kota42.gosip_bisik', 'f2', 'ねえ、聞いた？張さんの息子と李さんの娘がね…', 'Psst, heard the news? The Zhang boy and the Li girl...', 'Hei, sudah dengar? Putra keluarga Zhang dan putri keluarga Li...', 'normal', 'Well Gossip', 'Ibu-Ibu Sumur'),
    ('kota42.gosip_tawa', 'f1', 'まあ、大げさなんだから！', 'Oh, she always blows things up!', 'Ah, dia memang suka melebih-lebihkan!', 'joy', 'Well Gossip', 'Ibu-Ibu Sumur'),
    ('kota42.duka', 'm2', '父が…昨夜、亡くなりました。', 'Father... passed away last night.', 'Ayah... wafat semalam.', 'sad', 'Grieving Son', 'Anak yang Berduka'),
    ('kota42.bayi_lahir', 'm1', '生まれた！男の子だ！みんな、赤い卵だよ！', "It's born! A boy! Everyone, red eggs!", 'Sudah lahir! Laki-laki! Semua, ini telur merah!', 'joy', 'New Father', 'Ayah Baru'),
    ('kota42.nikah_undang', 'f2', '明日、娘が嫁ぐんです。ぜひいらしてください。', 'My daughter marries tomorrow. Please come.', 'Besok putriku menikah. Datanglah.', 'joy', "Bride's Mother", 'Ibu Pengantin'),
    ('kota42.takut_serangan', 'f3', '早く中へ！獣が城壁の外まで来てる！', 'Get inside, quick! The beasts are right outside the walls!', 'Cepat masuk! Binatang sudah sampai di luar tembok!', 'fear', 'Frightened Townswoman', 'Warga Ketakutan'),
    ('kota42.marah', 'm3', 'なんだと！もう一度言ってみろ！', 'What did you say! Say that again!', 'Apa katamu! Ulangi sekali lagi!', 'angry', 'Angry Townsman', 'Warga Pemarah'),
    ('kota42.mabuk', 'm1', 'ひっく…月が…二つ…いや、一つか…', 'Hic... two... moons... no, one...', 'Hik... bulannya... dua... bukan, satu...', 'normal', 'Drunkard', 'Pemabuk'),
    ('kota42.catur_skak', 'elder', '王手！どうだ、参ったか！', 'Check! Well, do you give up?', 'Skak! Bagaimana, menyerah?', 'joy', 'Old Chess Player', 'Kakek Pecatur'),
    ('kota42.catur_kalah', 'elder', 'むう…今日は風が悪い。', "Hmm... the wind's against me today.", 'Hmm... anginnya sedang buruk hari ini.', 'sad', 'Old Chess Player', 'Kakek Pecatur'),
    ('kota42.pedagang_seru', 'm2', '安いよ安いよ！今朝採れたての野菜だよ！', 'Cheap, cheap! Vegetables picked this morning!', 'Murah, murah! Sayur baru dipetik pagi ini!', 'joy', 'Greengrocer', 'Pedagang Sayur'),
    ('kota42.harga_naik', 'f2', 'お米がまた上がったのよ。困ったわねえ。', 'Rice went up again. What a bother.', 'Beras naik lagi. Repot sekali.', 'sad', 'Housewife', 'Ibu Rumah Tangga'),
    ('kota42.ronda_geng1', 'elder', 'てんかわき、ものかわく。ひのようじん！', 'Dry skies, dry things, mind the fire!', 'Udara kering, hati-hati api dan lilin!', 'normal', 'Lao Geng, Night Watchman', 'Lao Geng, Penjaga Malam'),
    ('kota42.ronda_geng2', 'elder', '二更、平安無事でござーい！', 'Second watch, and all is well!', 'Geng kedua, semua aman!', 'calm', 'Lao Geng, Night Watchman', 'Lao Geng, Penjaga Malam'),
    ('kota42.ronda_geng3', 'elder', '戸締まり用心、泥棒に気をつけよ！', 'Lock your doors, beware of thieves!', 'Kunci pintu, waspada pencuri!', 'normal', 'Lao Geng, Night Watchman', 'Lao Geng, Penjaga Malam'),
    ('kota42.ronda_geng4', 'm3', '四更でございます。安らかにお休みなされ。', 'The fourth watch. Sleep in peace.', 'Geng keempat. Tidurlah dengan tenang.', 'calm', 'Xiao Bang, Watch Apprentice', 'Xiao Bang, Murid Ronda'),
    ('kota42.ronda_geng5', 'elder', '五更！夜明けは近いぞ。', 'Fifth watch! Dawn is near.', 'Geng kelima! Fajar sudah dekat.', 'calm', 'Lao Geng, Night Watchman', 'Lao Geng, Penjaga Malam'),
    ('kota42.copet_teriak', 'f1', 'スリだ！誰か捕まえて！', 'Pickpocket! Somebody stop him!', 'Copet! Tolong tangkap!', 'fear', 'Pickpocket Victim', 'Korban Copet'),
    ('kota42.copet_tabrak', 'm3', 'おっと、失礼！', 'Oops, pardon me!', 'Ups, maaf!', 'normal', 'Pickpocket', 'Pencopet'),
    ('kota42.copet_tertangkap', 'm3', 'ま、参った！返します、返しますから！', "M-mercy! I'll give it back, I'll give it back!", 'A-ampun! Kukembalikan, kukembalikan!', 'fear', 'Pickpocket', 'Pencopet'),
    ('kota42.kebakaran', 'm1', '火事だ！水を持ってこい！', 'Fire! Bring water!', 'Kebakaran! Bawa air!', 'fear', 'Panicked Townsman', 'Warga Panik'),
    ('kota42.api_padam', 'm2', '消えた…助かった…ありがとうございます！', "It's out... we're saved... thank you!", 'Padam... selamat... terima kasih!', 'joy', 'Homeowner', 'Pemilik Rumah'),
    ('kota42.gerobak', 'm2', 'ああ、荷が！誰か手を貸してくれ！', 'Oh no, my load! Someone lend a hand!', 'Aduh, muatanku! Tolong bantu!', 'sad', 'Carter', 'Kusir Gerobak'),
    ('kota42.lerai', 'm3', 'やめろ！二人とも、やめるんだ！', 'Stop it! Both of you, stop!', 'Hentikan! Kalian berdua, hentikan!', 'angry', 'Peacemaker', 'Warga Pelerai'),
    ('kota42.lao_shuo_buka', 'elder', 'さてさて、今日お話しするのは…', "Now then, now then, today's tale is...", 'Nah, nah, yang akan kuceritakan hari ini...', 'calm', 'Lao Shuo, Storyteller', 'Lao Shuo, Pendongeng'),
    ('kota42.lao_shuo_tutup', 'elder', '続きは、また明日のお楽しみ！', 'For the rest, come back tomorrow!', 'Lanjutannya, nantikan besok!', 'joy', 'Lao Shuo, Storyteller', 'Lao Shuo, Pendongeng'),
    ('kota42.akrobat_buka', 'f1', 'さあさあ、お立ち会い！三日月一座の妙技、とくとご覧あれ！', 'Roll up, roll up, behold the wonders of the Crescent Moon Troupe!', 'Ayo, ayo, saksikan kehebatan Rombongan Bulan Sabit!', 'joy', 'Madam Hong Ling', 'Nyonya Hong Ling'),
    ('kota42.penjual_obat', 'm2', 'この膏薬ひと塗りで、腰の痛みもたちまち消える！', 'One dab of this plaster and your back pain is gone!', 'Sekali oles plester ini, sakit pinggang langsung hilang!', 'joy', 'Medicine Hawker', 'Penjual Obat'),
    ('kota42.sekolah_hafal', 'f1', 'じんしのしょ、せいほんぜん。せいあいちかく、ならいあいとおし。', 'Men at their birth are good by nature; nature is close, habit sets apart.', 'Manusia pada awalnya berwatak baik; wataknya dekat, kebiasaannya menjauhkan.', 'normal', 'Schoolchild', 'Murid Sekolah'),
    ('kota42.guru_panggil', 'm2', 'さあ、みんな並んで。学堂へ行くよ。', "Line up, everyone. We're off to school.", 'Ayo semua berbaris. Kita ke sekolah.', 'normal', 'Schoolteacher', 'Guru Sekolah'),
    ('kota42.taichi', 'elder', 'ゆっくり息を吸って…吐いて…', 'Breathe in slowly... and out...', 'Tarik napas pelan... hembuskan...', 'calm', 'Tai Chi Elder', 'Kakek Taichi'),
    ('kota42.comblang', 'f2', 'いいご縁がありますよ。任せてちょうだい！', "There's a fine match for you. Leave it to me!", 'Ada jodoh yang bagus. Serahkan padaku!', 'joy', 'Auntie Mei, Matchmaker', 'Bibi Mei, Mak Comblang'),
    ('kota42.nikah_jemput', 'm1', '花嫁のお迎えだ！道を開けてくれ！', "Here comes the bride's escort! Make way!", 'Penjemputan pengantin! Beri jalan!', 'joy', 'Bridal Escort', 'Pengiring Pengantin'),
    ('kota42.nikah_sembah', 'elder', 'いっぱい、てんち！にはい、こうどう！ふうふ、たいはい！', 'Bow to Heaven and Earth! Bow to the parents! Bow to each other!', 'Sembah langit dan bumi! Sembah orang tua! Saling bersembah!', 'joy', 'Master of Ceremonies', 'Pemandu Upacara'),
    ('kota42.bersulang', 'm2', '新郎新婦に、乾杯！', 'To the bride and groom, cheers!', 'Untuk kedua mempelai, bersulang!', 'joy', 'Wedding Guest', 'Tamu Pesta'),
    ('kota42.makam_pimpin', 'elder', '故人を偲び、皆で送りましょう。', 'Let us remember the departed and see them off together.', 'Mari kita kenang almarhum dan mengantarnya bersama.', 'sad', 'Funeral Master', 'Pengurus Duka'),
    ('kota42.makam_ratap', 'f2', 'どうして…どうして先に逝ってしまったの…', 'Why... why did you go first...', 'Kenapa... kenapa pergi lebih dulu...', 'sad', 'Grieving Widow', 'Janda yang Berduka'),
    ('kota42.zhuazhou_kuas', 'f1', '見て！筆を掴んだわ！きっと学者になるわね！', "Look! He grabbed the brush! He'll be a scholar for sure!", 'Lihat! Dia meraih kuas! Pasti jadi sarjana!', 'joy', "Baby's Mother", 'Ibu Bayi'),
    ('kota42.zhuazhou_pedang', 'm1', '剣を掴んだぞ！この子は仙人になるかもしれん！', 'He grabbed the sword! This child might become a cultivator!', 'Dia meraih pedang! Anak ini mungkin jadi kultivator!', 'joy', "Baby's Father", 'Ayah Bayi'),
    ('kota42.rekrut_terima', 'm2', 'おめでとう。今日から君は我が宗門の候補だ。', 'Congratulations. From today you are a candidate of our sect.', 'Selamat. Mulai hari ini kau calon murid sekte kami.', 'joy', 'Recruitment Officer', 'Petugas Kantor Rekrutmen'),
    ('kota42.rekrut_pamit', 'f1', 'お父さん、お母さん…行ってきます！', "Father, Mother... I'm off!", 'Ayah, Ibu... aku berangkat!', 'sad', 'Young Recruit', 'Calon Murid'),
    ('kota42.restu_tolak', 'm3', '息子は店を継ぐんだ。仙人なんぞにはやらん！', "My son inherits the shop. I won't hand him to the immortals!", 'Putraku akan mewarisi toko. Takkan kuserahkan jadi kultivator!', 'angry', 'Shopkeeper Father', 'Ayah Pemilik Toko'),
    ('kota42.pelindung_sapa', 'm1', '守護宗門の方だ！ありがたい！', "It's someone from the protecting sect! Thank heavens!", 'Itu orang dari sekte pelindung! Syukurlah!', 'joy', 'Townsman', 'Warga'),
    ('kota42.bela_panggil', 'm2', '獣の群れが町に迫っています！どうかお助けを！', 'A beast horde is closing in on the town! Please, help us!', 'Kawanan binatang mendekati kota! Tolonglah kami!', 'fear', 'Town Messenger', 'Utusan Kota'),
    ('kota42.bela_menang', 'm2', '町は守られた！宗門万歳！', 'The town is saved! Long live the sect!', 'Kota terselamatkan! Hidup sekte!', 'joy', 'Town Messenger', 'Utusan Kota'),
    ('kota42.panji_naik', 'elder', '今日よりこの町は、貴宗の庇護を受けます。', "From this day, this town lies under your sect's protection.", 'Mulai hari ini kota ini di bawah naungan sekte Tuan.', 'calm', 'Mayor', 'Wali Kota'),
    ('kota42.mandor', 'elder', '礎がしっかりしてこそ、家は百年もつ。', 'Only on firm foundations does a house stand a hundred years.', 'Hanya bila pondasinya kokoh, rumah bertahan seratus tahun.', 'calm', 'Master Builder', 'Mandor Tukang'),
    ('kota42.kavling_selesai', 'm1', '新しい家が建ったぞ！祝いだ！', "The new house is up! Let's celebrate!", 'Rumah baru sudah berdiri! Mari rayakan!', 'joy', 'Builder', 'Tukang'),
    ('kota42.bangun_kembali', 'm2', '壊れても、また建てればいい。みんなでな。', 'If it falls, we build it again. Together.', 'Kalau hancur, kita bangun lagi. Bersama-sama.', 'calm', 'Builder', 'Tukang'),
    ('kota42.sandi_malam', 'demon', '月のない夜、石の下で水が眠る…', 'On a moonless night, water sleeps beneath the stone...', 'Malam tanpa bulan, air tidur di bawah batu...', 'calm', 'Ghost Market Keeper', 'Penjaga Pasar Hantu'),
    ('kota42.lentera_hijau', 'demon', '顔は見ない、名は聞かない。それがここの決まりだ。', "We don't look at faces, we don't ask names. That's the rule here.", 'Tak melihat wajah, tak bertanya nama. Itu aturan di sini.', 'calm', 'Ghost Market Keeper', 'Penjaga Pasar Hantu'),
    ('kota42.razia', 'm1', '役人だ！逃げろ！', 'Officials! Run!', 'Petugas! Lari!', 'fear', 'Black Market Trader', 'Pedagang Gelap'),
    ('kota42.dadu_besar', 'm3', '大だ！大が来たぞ！', 'Big! Big came up!', 'Besar! Keluar besar!', 'joy', 'Gambler', 'Penjudi'),
    ('kota42.dadu_kecil', 'm3', 'くそっ、また小か…', 'Damn it, small again...', 'Sial, kecil lagi...', 'sad', 'Gambler', 'Penjudi'),
    ('kota42.huaquan', 'm1', 'ごかいしゅ！ろくろくじゅん！', 'Five champions! Six-six smooth sailing!', 'Lima juara! Enam-enam lancar!', 'joy', 'Tavern Drinker', 'Peminum Kedai'),
    ('kota42.wayang_buka', 'm2', '影絵芝居の始まり始まり！', 'The shadow play begins, it begins!', 'Pertunjukan wayang bayang dimulai!', 'joy', 'Shadow Puppeteer', 'Dalang Wayang Bayang'),
    ('kota42.sup_nenek', 'f2', '寒かったろう。熱いスープをお飲み。', "Cold out, wasn't it? Drink some hot soup.", 'Dingin, kan? Minumlah sup panas ini.', 'calm', 'Granny Tang', 'Nenek Tang'),
    ('kota42.laba', 'elder', 'ろうはちの粥だ。誰でも並びなされ。', 'Here is Laba porridge. Everyone, line up.', 'Ini bubur Laba. Siapa saja silakan antre.', 'calm', 'Temple Abbot', 'Biksu Kuil'),
    ('kota42.tahun_baru', 'f1', '新年おめでとう！お年玉ちょうだい！', 'Happy New Year! Red envelope, please!', 'Selamat tahun baru! Minta angpao!', 'joy', 'Town Child', 'Anak Kota'),
    ('kota42.pindah', 'm2', '水の都に仕事があるそうで…家族で引っ越します。', "They say there's work in the Water City... the whole family is moving.", 'Katanya ada kerja di Kota Air... sekeluarga kami pindah.', 'normal', 'Departing Townsman', 'Warga yang Pindah'),
    ('kota42.pejabat_sapa', 'm1', 'これは、お役人様！', 'Oh, Lord Official!', 'Wah, Tuan Pejabat!', 'normal', 'Townsman', 'Warga'),
    ('kota42.putusan', 'elder', '判決を下す！', 'The verdict is passed!', 'Putusan dijatuhkan!', 'angry', 'Town Magistrate', 'Hakim Kota'),
    ('kota42.suap', 'm3', 'まあまあ、これはほんの気持ちで…', 'Now, now, this is just a small token of thanks...', 'Sudahlah, ini sekadar tanda terima kasih...', 'normal', 'Briber', 'Penyuap'),
    ('kota42.pajak_keluh', 'm3', 'また税が上がるのか…', 'Taxes up again?...', 'Pajak naik lagi?...', 'sad', 'Merchant', 'Pedagang'),
    ('kota42.cucu_cerita', 'elder', 'おじいちゃんの若い頃にな、空飛ぶ剣士が町を救ってくれたんじゃ。', 'When Grandpa was young, a flying swordsman saved this town.', 'Waktu Kakek masih muda, seorang pendekar terbang menyelamatkan kota ini.', 'calm', 'Storytelling Grandfather', 'Kakek Pencerita'),
    ('kota42.ah_qin', 'f2', 'この曲は、亡き師匠の曲です。', "This tune was my late master's.", 'Lagu ini lagu almarhum guruku.', 'sad', 'Ah Qin, Street Musician', 'Ah Qin, Pengamen'),
    ('kota42.lao_geng_pamit', 'elder', 'わしの拍子木も、今夜で最後か…', 'My clapper too... tonight is its last...', 'Kentonganku pun, malam ini yang terakhir...', 'sad', 'Lao Geng, Night Watchman', 'Lao Geng, Penjaga Malam'),
    ('kota42.xiao_bang', 'm3', '師匠、見ててください。僕が町の夜を守ります！', "Master, watch me. I'll guard the town's nights!", 'Guru, lihatlah. Aku yang akan menjaga malam kota!', 'joy', 'Xiao Bang, Watch Apprentice', 'Xiao Bang, Murid Ronda'),
    ('kota42.xiao_yu', 'f1', '葉っぱがね、私の手の上で浮かぶの！', 'The leaf floats above my hand!', 'Daunnya melayang di atas tanganku!', 'joy', 'Xiao Yu', 'Xiao Yu'),
    ('kota42.seru.ya_l', 'm2', 'はい。', 'Yes.', 'Ya.', 'normal', 'Townsman', 'Warga'),
    ('kota42.seru.ya_p', 'f1', 'はい。', 'Yes.', 'Ya.', 'normal', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.eh_l', 'm3', 'ええっ？', 'Huh?', 'Hah?', 'fear', 'Townsman', 'Warga'),
    ('kota42.seru.eh_p', 'f2', 'ええっ？', 'Huh?', 'Hah?', 'fear', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.hmm_l', 'm2', 'ふむ…', 'Hmm...', 'Hmm...', 'calm', 'Townsman', 'Warga'),
    ('kota42.seru.hmm_p', 'f2', 'うーん…', 'Hmm...', 'Hmm...', 'calm', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.oh_l', 'm1', 'へえ！', 'Oh, really!', 'Oh ya!', 'joy', 'Townsman', 'Warga'),
    ('kota42.seru.oh_p', 'f1', 'へえ！', 'Oh, really!', 'Oh ya!', 'joy', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.tawa_l', 'm1', 'あははっ！', 'Hahaha!', 'Hahaha!', 'joy', 'Townsman', 'Warga'),
    ('kota42.seru.tawa_p', 'f1', 'あははっ！', 'Hahaha!', 'Hahaha!', 'joy', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.cih', 'm3', 'ちっ。', 'Tch.', 'Cih.', 'angry', 'Townsman', 'Warga'),
    ('kota42.seru.aduh_l', 'm2', 'やれやれ…', 'Oh dear...', 'Aduh...', 'sad', 'Townsman', 'Warga'),
    ('kota42.seru.aduh_p', 'f2', 'やれやれ…', 'Oh dear...', 'Aduh...', 'sad', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.terima_kasih_l', 'm2', 'どうも！', 'Thanks!', 'Terima kasih!', 'joy', 'Townsman', 'Warga'),
    ('kota42.seru.terima_kasih_p', 'f1', 'どうも！', 'Thanks!', 'Terima kasih!', 'joy', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.maaf_l', 'm3', 'すみません…', 'Sorry...', 'Maaf...', 'sad', 'Townsman', 'Warga'),
    ('kota42.seru.maaf_p', 'f3', 'すみません…', 'Sorry...', 'Maaf...', 'sad', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.wah_anak', 'f1', 'わあっ！', 'Wow!', 'Wah!', 'joy', 'Child', 'Anak'),
    ('kota42.seru.tetua_hmm', 'elder', 'ほっほっ。', 'Ho ho.', 'Ho ho.', 'calm', 'Elder', 'Tetua'),
    ('kota42.seru.tetua_marah', 'elder', '馬鹿者！', 'You fool!', 'Dasar bodoh!', 'angry', 'Elder', 'Tetua'),
    ('kota42.seru.mabuk', 'm1', 'ひっく。', 'Hic.', 'Hik.', 'normal', 'Townsman', 'Warga'),
    ('kota42.seru.bisik_p', 'f2', 'しーっ。', 'Shh.', 'Sst.', 'calm', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.takut_p', 'f3', 'ひっ…！', 'Eek...!', 'Hii...!', 'fear', 'Townswoman', 'Warga Perempuan'),
    ('kota42.seru.setuju_l', 'm1', 'そうだそうだ！', 'Right, right!', 'Betul, betul!', 'joy', 'Townsman', 'Warga'),
    ('kota42.nikah_selamat', 'f1', 'ご結婚おめでとうございます！末永くお幸せに！', 'Congratulations on your wedding! May you be happy forever!', 'Selamat atas pernikahan kalian! Semoga bahagia selamanya!', 'joy', 'Wedding Guest', 'Tamu Pesta'),
    ('kota42.makam_sujud', 'm3', 'どうか、安らかに眠ってください…。', 'May you rest in peace...', 'Semoga beristirahat dengan tenang...', 'sad', 'Bereaved Family', 'Keluarga Almarhum'),
    ('kota42.sebulan_selamat', 'elder', '満月おめでとう。丈夫に育つんだよ。', 'Happy one month! Grow up healthy, little one.', 'Selamat sebulan! Tumbuhlah sehat, ya.', 'joy', 'Elderly Neighbour', 'Tetangga Tua'),
]
for key, spk, ja, cap_en, cap_id, emo, sub_en, sub_id in KV41_42:
    style, fxname, group = WORLD_SPEAKER[spk]
    native = spk.startswith(('m', 'f')) and emo in NATIVE_EMO.get(spk, set())
    if spk.startswith(('m', 'f')) and native:
        style = VOICES[spk][emo]
    def make():
        raw = tts(ja, style, 'normal' if native else emo, speed=0.88 if group == 'heaven' else 0.95,
                  pitch_scale=-0.06 if group == 'heaven' else None)
        return fx_room(raw, seed=90) if fxname == 'room' else FX[fxname](raw)
    name = write('world/' + key.replace('.', '/'), make)
    eid = f'voice.{key}'
    event(eid, [name], f'{sub_en} speaks', f'{sub_id} berbicara')
    en[f'kultivator.voice.{key}'] = cap_en
    id_[f'kultivator.voice.{key}'] = cap_id
    manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': sub_en, 'style': style})

# --- Sekte Langit (DESIGN 37, docs/progres/langit-voices.md; added by 39E): named speakers with their own subtitles
LANGIT_37 = [
    ('sekte_langit.bajak_datang', ROGUE, 'room', '倉の中身を差し出せ！さもなくばこの島を焼き払う！', 'Hand over your storehouse, or we burn this island!',
     'Serahkan isi gudang kalian, atau pulau ini kami bakar!', 'angry', 'Cloud Pirate Captain', 'Kapten Bajak Awan'),
    ('sekte_langit.longbo_datang', ANCIENT, 'boss', '祖先は山を支える亀を六匹釣った。お前の亀が七匹目だ！',
     'My ancestor fished up six mountain-bearing turtles. Yours will be the seventh!',
     'Leluhurku memancing enam kura-kura penyangga gunung. Kura-kuramu yang ketujuh!', 'normal', 'Longbo Giant', 'Raksasa Longbo'),
    ('sekte_langit.longbo_kalah', ANCIENT, 'boss', '馬鹿な…亀一匹さえ釣れぬとは…', 'Impossible... not even one turtle...',
     'Mustahil... seekor kura-kura pun tak terpancing...', 'sad', 'Longbo Giant', 'Raksasa Longbo'),
    ('sekte_langit.tamu_salam', ELDER, 'elder', '雲の上の同道よ、お招きに感謝する。', 'Fellow daoist above the clouds, thank you for the welcome.',
     'Sahabat di atas awan, terima kasih atas sambutanmu.', 'calm', 'Guest Elder', 'Tetua Tamu'),
    ('sekte_langit.utusan_salam', VOICES['f3']['calm'], 'room', '天宮はそなたの島を見ていた。天命を受けよ。',
     'The Heavenly Court has seen your island. Receive its command.', 'Istana Langit telah melihat pulaumu. Terimalah perintahnya.', 'calm',
     'Heavenly Court Envoy', 'Utusan Istana Langit'),
    ('sekte_langit.utusan_tantang', HEAVEN_FIERCE, 'heaven', '天は聞いた。黄昏に備えよ。', 'Heaven has heard. Prepare yourself at dusk.',
     'Langit telah mendengar. Bersiaplah saat senja.', 'normal', 'Heaven', 'Langit'),
]
READ.update({'天宮': 'てんきゅう', '天命': 'てんめい', '同道': 'どうどう', '黄昏': 'たそがれ'})
for key, style, fxname, ja, cap_en, cap_id, emo, sub_en, sub_id in LANGIT_37:
    def make():
        raw = tts(ja, style, emo, speed=0.88 if fxname == 'heaven' else 0.95, pitch_scale=-0.06 if fxname == 'heaven' else None)
        return fx_room(raw, seed=93) if fxname == 'room' else FX[fxname](raw)
    name = write('world/' + key.replace('.', '/'), make)
    eid = f'voice.{key}'
    event(eid, [name], f'{sub_en} speaks', f'{sub_id} berbicara')
    en[f'kultivator.voice.{key}'] = cap_en
    id_[f'kultivator.voice.{key}'] = cap_id
    manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': sub_en, 'style': style})

# --- non-verbal clips from the free packs (see docs/CREDITS.md)
ADV = os.path.join(DL, 'male_adv/x/RPG Male Adventurer')
FEM = os.path.join(DL, 'female_rpg')
DEM = os.path.join(DL, 'demon/x')


def pack(eid, rel_dir, files, fx, sub_en, sub_id):
    """A missing pack file is skipped only when its processed clip already exists (the packs are not kept)."""
    names = []
    for i, f in enumerate(files):
        rel = f'{rel_dir}/{i}'
        if not os.path.exists(f) and not os.path.exists(os.path.join(OUT, rel + '.ogg')):
            raise SystemExit(f'missing pack file {f} and no clip {rel}.ogg: download the packs of docs/CREDITS.md into {DL}')
        names.append(write(rel, lambda: fx(load(f))))
    event(eid, names, sub_en, sub_id)


def room_soft(x):
    return fx_room(x, 0.4, 0.1, 71)


pack('voice.grunt.male.attack', 'grunt/male/attack', [f'{ADV}/attack{i}.wav' for i in range(9)] + [f'{ADV}/attackbig{i}.wav' for i in range(6)],
     room_soft, 'Disciple shouts', 'Murid berteriak')
pack('voice.grunt.male.hurt', 'grunt/male/hurt', [f'{ADV}/hurt{i}.wav' for i in range(10)], room_soft, 'Disciple hurt', 'Murid terluka')
pack('voice.grunt.male.death', 'grunt/male/death', [f'{ADV}/death{i}.wav' for i in range(2)], room_soft, 'Disciple falls', 'Murid tumbang')
pack('voice.grunt.female.hurt', 'grunt/female/hurt', [f'{FEM}/oww.ogg', f'{FEM}/gah.ogg'], room_soft, 'Disciple hurt', 'Murid terluka')
for vid in ('f1', 'f2', 'f3'):
    for k, ja in (('kiai0', 'はっ！'), ('kiai1', 'やぁっ！'), ('kiai2', 'えいっ！')):
        write(f'grunt/female/{vid}_{k}', lambda: room_soft(tts(ja, VOICES[vid]['angry'], 'angry' if vid != 'f1' else 'normal', speed=1.1, post=0.02)))
event('voice.grunt.female.attack', [f'kultivator:voice/grunt/female/{v}_{k}' for v in ('f1', 'f2', 'f3') for k in ('kiai0', 'kiai1', 'kiai2')],
      'Disciple shouts', 'Murid berteriak')
pack('voice.demon.laugh', 'demon/laugh', [f'{DEM}/DL DEMON LAUGHS.wav', f'{DEM}/DL DEMON MOOHAAA LAUGH.wav', f'{DEM}/DL DEMON SNIGGER 1.wav',
                                          f'{DEM}/DL DEMON SNIGGER 2.wav'], fx_demon, 'Demonic laughter', 'Tawa iblis')
pack('voice.demon.growl', 'demon/growl', [f'{DEM}/DL DEMON GROWL {i}.wav' for i in range(1, 5)] + [f'{DEM}/DL DEMON SNARL {i}.wav' for i in range(1, 4)],
     fx_demon, 'Demonic growl', 'Geraman iblis')
pack('voice.demon.death', 'demon/death', [f'{DEM}/DL DEMON DIES.wav'], fx_demon, 'Demon shrieks', 'Iblis menjerit')
pack('voice.boss.roar', 'boss/roar', [f'{DEM}/DL DEMON FIGHT GRUNT {i}.wav' for i in range(1, 4)], fx_boss, 'Ancient roar', 'Raungan purba')

# --- Kota yang Hidup (docs/progres/suara-kota.md): murmurs under the gestured talks of resident NPCs (38.N.4 butir 8).
# Event voice.gumam.<tipe>.<n>, caption kultivator.voice.gumam.<tipe>.<n>; the client picks the voice type from the
# speaker's Sosok (man, woman, old man, old woman, child) and a random line. Mono, quiet and dull ("far murmur") so they
# sit under the crowd ambience instead of reading as a line spoken to the player.
GUMAM_SPEAKER = {  # tipe -> [(style, pitch, speed)], rotated over the lines for variety
    'pria': [(12, 0.0, 1.05), (11, 0.0, 1.05), (52, 0.0, 1.05), (13, 0.0, 1.0)],
    'wanita': [(2, 0.0, 1.05), (8, 0.0, 1.05), (10, 0.0, 1.05), (20, 0.0, 1.05), (46, 0.0, 1.05)],
    'kakek': [(42, 0.0, 0.92), (53, 0.0, 0.95)],
    'nenek': [(80, -0.07, 0.86), (20, -0.09, 0.86)],
    'anak': [(3, 0.05, 1.1), (1, 0.06, 1.1), (10, 0.12, 1.1)],
}
GUMAM_EN = {'pria': 'man', 'wanita': 'woman', 'kakek': 'old man', 'nenek': 'old woman', 'anak': 'child'}
GUMAM_ID = {'pria': 'pria', 'wanita': 'wanita', 'kakek': 'kakek', 'nenek': 'nenek', 'anak': 'anak'}
GUMAM = {
    'pria': [
        ('いらっしゃい、いらっしゃい！', 'Come in, come in!', 'Mari, mari, silakan!'),
        ('今日はいい天気だな。', 'Fine weather today.', 'Cuaca hari ini bagus, ya.'),
        ('それ、いくらだ？', 'How much is that?', 'Itu berapa harganya?'),
        ('高いなあ、もう少し安くならんか。', "Pricey... can't you go a bit lower?", 'Mahal... tidak bisa kurang sedikit?'),
        ('聞いたか、あの噂。', 'Did you hear that rumour?', 'Sudah dengar kabar itu?'),
        ('はっはっは、そりゃいい！', "Ha ha ha, that's a good one!", 'Ha ha ha, bagus sekali!'),
        ('おう、久しぶりだな。', 'Hey, long time no see.', 'Wah, lama tidak jumpa.'),
        ('雨が降りそうだな。', 'Looks like rain.', 'Sepertinya mau hujan.'),
        ('腹が減ったな、飯にしよう。', "I'm starving, let's eat.", 'Lapar nih, ayo makan.'),
        ('まあまあ、落ち着けよ。', 'Easy now, calm down.', 'Sudah, sudah, tenang dulu.'),
        ('昨日の市は賑やかだったな。', "Yesterday's market was lively.", 'Pasar kemarin ramai sekali.'),
        ('へえ、本当かい？', 'Huh, really?', 'Oh ya, sungguh?'),
        ('霊石三つでどうだ。', 'How about three spirit stones?', 'Bagaimana kalau tiga batu roh?'),
        ('じゃあ、またな。', 'Well, see you.', 'Ya sudah, sampai jumpa.'),
    ],
    'wanita': [
        ('まあ、いらっしゃい。', 'Oh, welcome.', 'Wah, silakan masuk.'),
        ('このお野菜、新鮮ですよ。', 'These vegetables are fresh.', 'Sayuran ini masih segar, lho.'),
        ('あら、お久しぶりね。', "Oh, it's been a while.", 'Eh, lama tidak bertemu.'),
        ('ねえ、聞いた？隣の娘さんがね…', 'Did you hear? The girl next door...', 'Eh, sudah dengar? Anak gadis tetangga itu...'),
        ('うふふ、そうなの。', 'Hee hee, is that so.', 'Hihi, begitu ya.'),
        ('今日は風が気持ちいいわね。', 'What a pleasant breeze today.', 'Anginnya segar hari ini.'),
        ('ちょっと高くない？', "Isn't that a bit pricey?", 'Agak mahal, tidak?'),
        ('じゃあ、二つちょうだい。', "Then I'll take two.", 'Kalau begitu, aku ambil dua.'),
        ('本当に？信じられない！', 'Really? Unbelievable!', 'Sungguh? Tidak bisa dipercaya!'),
        ('洗濯物、乾くかしら。', 'Will the washing dry, I wonder.', 'Jemuranku kering tidak, ya.'),
        ('あはは、おかしい！', 'Ah ha ha, how funny!', 'Ahaha, lucu sekali!'),
        ('お茶でも飲んでいく？', 'Will you stay for some tea?', 'Mampir minum teh dulu?'),
        ('気をつけて帰ってね。', 'Take care on the way home.', 'Hati-hati pulangnya, ya.'),
        ('また明日ね。', 'See you tomorrow.', 'Sampai besok, ya.'),
    ],
    'kakek': [
        ('ほっほっほ、若いのう。', 'Ho ho ho, so young.', 'Ho ho ho, masih muda, ya.'),
        ('わしの若い頃はのう…', 'Back when I was young...', 'Waktu aku masih muda dulu...'),
        ('今年の米は出来がよい。', 'The rice came in well this year.', 'Padi tahun ini bagus hasilnya.'),
        ('腰が痛うてかなわん。', 'My back is killing me.', 'Pinggangku sakit sekali.'),
        ('明日は雨じゃな、膝が言うておる。', 'Rain tomorrow; my knees say so.', 'Besok hujan; lututku bilang begitu.'),
        ('ふむ、それは良い話じゃ。', 'Hmm, that is good news.', 'Hmm, itu kabar baik.'),
        ('茶を一杯もらおうかの。', "I'll have a cup of tea.", 'Aku minta secangkir teh.'),
        ('近頃の若い者は…', 'Young folk these days...', 'Anak muda zaman sekarang...'),
        ('まあ、焦らずともよい。', 'Now, no need to rush.', 'Sudahlah, tidak perlu terburu-buru.'),
        ('昔、この町には仙人がおったそうな。', 'They say an immortal once lived in this town.', 'Konon dulu ada seorang abadi tinggal di kota ini.'),
        ('囲碁でも打つかの。', 'Shall we play a game of Go?', 'Bagaimana kalau main catur Go?'),
        ('ほう、そうかそうか。', 'Oh, I see, I see.', 'Oh, begitu, begitu.'),
        ('うむ、達者でな。', 'Mm, take care of yourself.', 'Hm, jaga dirimu baik-baik.'),
    ],
    'nenek': [
        ('あらあら、まあまあ。', 'Oh my, oh my.', 'Aduh, aduh.'),
        ('孫がね、大きくなってねえ。', 'My grandchild has grown so big.', 'Cucuku sudah besar, lho.'),
        ('寒くなってきたねえ。', "It's getting cold, isn't it.", 'Mulai dingin, ya.'),
        ('ちゃんと食べてるかい？', 'Are you eating properly?', 'Makanmu cukup, Nak?'),
        ('昔はねえ、この辺は田んぼだったのよ。', 'Long ago, all this was rice fields.', 'Dulu, di sini semua sawah.'),
        ('ほほほ、ありがとうねえ。', 'Ho ho, thank you, dear.', 'Hohoho, terima kasih, ya.'),
        ('お饅頭、おひとつどうぞ。', 'Have a steamed bun.', 'Silakan, ambil satu bakpao.'),
        ('まあ、怖い話だねえ。', 'My, what a frightening tale.', 'Aduh, cerita yang menakutkan.'),
        ('今日は市が立つ日だよ。', 'Today is market day.', 'Hari ini hari pasar.'),
        ('よっこらしょ。', 'Up we go.', 'Aduh, encok...'),
        ('そうかい、そうかい。', 'Is that so, is that so.', 'Begitu, ya, begitu.'),
        ('気をつけてお行きなさいね。', 'Go carefully now.', 'Hati-hati di jalan, ya.'),
    ],
    'anak': [
        ('わーい、待ってー！', 'Yay, wait for me!', 'Hore, tunggu aku!'),
        ('お母さん、あれ買って！', 'Mum, buy me that!', 'Bu, belikan yang itu!'),
        ('鬼ごっこしようよ！', "Let's play tag!", 'Ayo main kejar-kejaran!'),
        ('見て見て、蝶々！', 'Look, look, a butterfly!', 'Lihat, lihat, kupu-kupu!'),
        ('お腹すいたー。', "I'm hungry...", 'Laparrr...'),
        ('へへへ、つかまえた！', 'Hee hee, got you!', 'Hehe, kena kau!'),
        ('すごーい！', 'Wooow!', 'Hebaaat!'),
        ('僕、仙人になるんだ！', "I'm going to be an immortal!", 'Aku mau jadi abadi!'),
        ('ねえねえ、知ってる？', 'Hey, hey, you know what?', 'Eh, eh, tahu tidak?'),
        ('飴玉ちょうだい！', 'Give me a sweet!', 'Minta permennya!'),
        ('あははは！', 'Ah ha ha ha!', 'Ahahaha!'),
        ('こっちだよー！', 'Over here!', 'Sebelah sini!'),
        ('またあしたね！', 'See you tomorrow!', 'Sampai besok!'),
    ],
}
READ.update({'仙人': 'せんにん', '霊石三つ': 'れいせきみっつ', '痛うて': 'いとうて', '言うておる': 'いうておる', '囲碁': 'いご',
             '市が立つ': 'いちがたつ', '昨日の市': 'きのうのいち', '飴玉': 'あめだま', '蝶々': 'ちょうちょう'})


def fx_far(x):
    """A murmur a few steps away: dull top, thin bottom, a little room, gently levelled and kept quiet."""
    tail = int(SR * 0.35)
    d = np.zeros(len(x) + tail)
    d[:len(x)] = lowpass(highpass(x, 160), 2600)
    d = np.tanh(2.2 * d / max(1e-9, np.max(np.abs(d)))) / np.tanh(2.2)
    w = convolve(d, ir(0.5, 0.42, 9, 3500, 77))[:len(d)]
    return fade_out(norm(d + w * 2.5, 0.55), 90)


for tipe, lines in GUMAM.items():
    for n, (ja, cap_en, cap_id) in enumerate(lines):
        style, p, spd = GUMAM_SPEAKER[tipe][n % len(GUMAM_SPEAKER[tipe])]
        name = write(f'gumam/{tipe}/{n}', lambda: fx_far(tts(ja, style, 'normal', speed=spd, pitch_scale=p, intonation=1.15)))
        eid = f'voice.gumam.{tipe}.{n}'
        event(eid, [name], f'Townsfolk chatter ({GUMAM_EN[tipe]})', f'Warga mengobrol ({GUMAM_ID[tipe]})')
        en[f'kultivator.voice.gumam.{tipe}.{n}'] = cap_en
        id_[f'kultivator.voice.gumam.{tipe}.{n}'] = cap_id
        manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': 'gumam_' + tipe, 'style': style})

# ---------------------------------------------------------------- outputs
sj = os.path.join(REPO, 'src/main/resources/assets/kultivator/sounds.json')
old = json.load(open(sj, encoding='utf-8')) if os.path.exists(sj) else {}
old = {k: v for k, v in old.items() if not k.startswith('voice.')}
old.update(sounds)
with open(sj, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(dict(sorted(old.items())), f, ensure_ascii=False, indent=2)
    f.write('\n')
os.makedirs(os.path.join(REPO, 'lang-src'), exist_ok=True)
for lang, d in (('en_us', en), ('id_id', id_)):
    with open(os.path.join(REPO, f'lang-src/voice.{lang}.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=2)
        f.write('\n')
with open(os.path.join(REPO, 'tools/voice_manifest.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump(manifest, f, ensure_ascii=False, indent=1)
    f.write('\n')
print('events', len(sounds), 'lines', len(manifest['lines']), 'new clips', len(MADE))

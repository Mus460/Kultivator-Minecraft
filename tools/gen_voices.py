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


def write(rel, audio):
    path = os.path.join(OUT, rel + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, np.clip(audio, -1, 1).astype(np.float32), SR, format='OGG', subtype='VORBIS')
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
]

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
    raw = tts(ja, style, emo, speed=speed, pitch_scale=-0.06 if group == 'heaven' else None)
    fxname = {'heaven': 'heaven', 'demon': 'demon', 'boss': 'boss', 'elder': 'elder', 'rogue': 'room'}[group]
    name = write(f'{group}/{key}', FX[fxname](raw))
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
        raw = tts(ja, style, 'normal' if native else emo, speed=1.0)
        name = write(f'disciple/{vid}/{key}', fx_room(raw, seed=60 + len(manifest['lines'])))
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
]
for key, spk, ja, cap_en, cap_id, emo in WORLD:
    style, fxname, group = WORLD_SPEAKER[spk]
    native = spk.startswith(('m', 'f')) and emo in NATIVE_EMO.get(spk, set())
    if spk.startswith(('m', 'f')) and native:
        style = VOICES[spk][emo]
    raw = tts(ja, style, 'normal' if native else emo, speed=0.88 if group == 'heaven' else 0.95,
              pitch_scale=-0.06 if group == 'heaven' else None)
    name = write('world/' + key.replace('.', '/'), fx_room(raw, seed=90) if fxname == 'room' else FX[fxname](raw))
    eid = f'voice.{key}'
    event(eid, [name], f'{SPEAKER_EN[group]} speaks' if group != 'disciple' else 'Someone speaks',
          f'{SPEAKER_ID[group]} berbicara' if group != 'disciple' else 'Seseorang berbicara')
    en[f'kultivator.voice.{key}'] = cap_en
    id_[f'kultivator.voice.{key}'] = cap_id
    manifest['lines'].append({'event': eid, 'ja': ja, 'en': cap_en, 'id': cap_id, 'speaker': spk, 'style': style})

# --- non-verbal clips from the free packs (see docs/CREDITS.md)
ADV = os.path.join(DL, 'male_adv/x/RPG Male Adventurer')
FEM = os.path.join(DL, 'female_rpg')
DEM = os.path.join(DL, 'demon/x')


def pack(eid, rel_dir, files, fx, sub_en, sub_id):
    names = []
    for i, f in enumerate(files):
        names.append(write(f'{rel_dir}/{i}', fx(load(f))))
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
        write(f'grunt/female/{vid}_{k}', room_soft(tts(ja, VOICES[vid]['angry'], 'angry' if vid != 'f1' else 'normal', speed=1.1, post=0.02)))
event('voice.grunt.female.attack', [f'kultivator:voice/grunt/female/{v}_{k}' for v in ('f1', 'f2', 'f3') for k in ('kiai0', 'kiai1', 'kiai2')],
      'Disciple shouts', 'Murid berteriak')
pack('voice.demon.laugh', 'demon/laugh', [f'{DEM}/DL DEMON LAUGHS.wav', f'{DEM}/DL DEMON MOOHAAA LAUGH.wav', f'{DEM}/DL DEMON SNIGGER 1.wav',
                                          f'{DEM}/DL DEMON SNIGGER 2.wav'], fx_demon, 'Demonic laughter', 'Tawa iblis')
pack('voice.demon.growl', 'demon/growl', [f'{DEM}/DL DEMON GROWL {i}.wav' for i in range(1, 5)] + [f'{DEM}/DL DEMON SNARL {i}.wav' for i in range(1, 4)],
     fx_demon, 'Demonic growl', 'Geraman iblis')
pack('voice.demon.death', 'demon/death', [f'{DEM}/DL DEMON DIES.wav'], fx_demon, 'Demon shrieks', 'Iblis menjerit')
pack('voice.boss.roar', 'boss/roar', [f'{DEM}/DL DEMON FIGHT GRUNT {i}.wav' for i in range(1, 4)], fx_boss, 'Ancient roar', 'Raungan purba')

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
print('events', len(sounds), 'lines', len(manifest['lines']))

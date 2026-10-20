"""Film Spektakuler assets (DESIGN 30.2.11): the Kenney CC0 pipeline and our own lens and cosmic textures.

Usage (from the project root; the Kenney folder is only read):
    python -I tools/gen_film_assets.py <kenney_dir>

<kenney_dir> holds the extracted packs kenney_particle-pack, kenney_impact-sounds and kenney_sci-fi-sounds (CC0 1.0,
https://www.kenney.nl). The script
  * decodes the selected particle sprites (a small pure-Python PNG decoder: zlib, 8-bit, colour types 0/2/3/4/6,
    filters 0-4), box-downscales them, turns them into white RGB with alpha = source alpha x luminance (so the client
    tints them cleanly) and writes assets/kultivator/textures/particle/film_<name>_<n>.png plus
    assets/kultivator/particles/film_<name>.json;
  * writes the HUD and world textures under assets/kultivator/textures/misc/film/ (Kenney smear, scratch, scorch,
    window leak; our own grain, anamorphic streak, light leaks, heat haze, star river, nebulae, palace strip, sun corona
    and moon disc);
  * copies the selected .ogg files byte for byte to assets/kultivator/sounds/film/<id>_<n>.ogg (no conversion tool is
    available; they are already 44.1 kHz Vorbis) and merges one "film.<id>" event per cue into
    assets/kultivator/sounds.json, keeping every other entry (the voice.* events of module V);
  * prints the channel count of every copied file (stereo files are protagonist-only cues, never positioned).
"""
import json
import math
import os
import random
import shutil
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "kultivator")
PARTICLE_TEX = os.path.join(ASSETS, "textures", "particle")
PARTICLE_JSON = os.path.join(ASSETS, "particles")
MISC = os.path.join(ASSETS, "textures", "misc", "film")
SOUNDS = os.path.join(ASSETS, "sounds", "film")

# (type, [Kenney sources in "PNG (Transparent)"], size)
PARTICLES = [
    ("film_spark", ["spark_01", "spark_02", "spark_04", "spark_05"], 64),
    ("film_flare", ["flare_01", "light_01", "light_02", "light_03"], 64),
    ("film_star", ["star_01", "star_04", "star_06", "star_08"], 64),
    ("film_smoke", ["smoke_01", "smoke_04", "smoke_07", "smoke_10"], 64),
    ("film_flame", ["flame_01", "flame_03", "flame_05", "fire_01"], 64),
    ("film_magic", ["magic_01", "magic_02", "magic_03", "magic_04"], 64),
    ("film_slash", ["slash_01", "slash_02", "slash_03", "slash_04"], 64),
    ("film_trace", ["trace_01", "trace_03", "trace_05"], 64),
    ("film_twirl", ["twirl_01", "twirl_02", "twirl_03"], 64),
    ("film_circle", ["circle_01", "circle_03", "circle_05"], 64),
    ("film_muzzle", ["muzzle_01", "muzzle_03", "muzzle_05"], 64),
    ("film_dirt", ["dirt_01", "dirt_02", "dirt_03"], 32),
    ("film_symbol", ["symbol_01", "symbol_02"], 64),
]

# (event id, [(pack, file)])
SOUND_CUES = [
    ("riser_long", [("sci", "spaceEngineLarge_000"), ("sci", "spaceEngineLarge_001")]),
    ("riser_short", [("sci", "forceField_000"), ("sci", "forceField_001"), ("sci", "forceField_002")]),
    ("hum_energy", [("sci", "spaceEngineLow_000")]),
    ("hum_vortex", [("sci", "engineCircular_000")]),
    ("whoosh", [("sci", "thrusterFire_000"), ("sci", "thrusterFire_001")]),
    ("zap", [("sci", "laserLarge_000"), ("sci", "laserLarge_001"), ("sci", "laserLarge_002")]),
    ("twinkle", [("sci", "laserRetro_000")]),
    ("crunch", [("sci", "explosionCrunch_000"), ("sci", "explosionCrunch_001"), ("sci", "explosionCrunch_002")]),
    ("clang", [("sci", "impactMetal_000"), ("sci", "impactMetal_001"), ("sci", "impactMetal_002")]),
    ("sub_boom", [("sci", "lowFrequency_explosion_000"), ("sci", "lowFrequency_explosion_001")]),
    ("bell", [("imp", "impactBell_heavy_000"), ("imp", "impactBell_heavy_001"), ("imp", "impactBell_heavy_002")]),
    ("glass", [("imp", "impactGlass_heavy_000"), ("imp", "impactGlass_heavy_001"), ("imp", "impactGlass_heavy_002")]),
    ("punch", [("imp", "impactPunch_heavy_000"), ("imp", "impactPunch_heavy_001"), ("imp", "impactPunch_heavy_002")]),
    ("gong", [("imp", "impactPlate_heavy_000"), ("imp", "impactPlate_heavy_001")]),
    ("thud", [("imp", "impactSoft_heavy_000"), ("imp", "impactSoft_heavy_001")]),
    ("rock", [("imp", "impactMining_000")]),
]


# ------------------------------------------------------------------ PNG decoding

def decode_png(path):
    """Returns (width, height, rows of RGBA tuples) for an 8-bit PNG (colour types 0, 2, 3, 4, 6; no interlace)."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG: " + path)
    pos = 8
    width = height = depth = ctype = interlace = None
    palette = []
    trns = b""
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break
    if depth != 8 or interlace != 0:
        raise ValueError("unsupported PNG (depth %s, interlace %s): %s" % (depth, interlace, path))
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = width * channels
    raw = zlib.decompress(bytes(idat))
    rows = []
    prev = bytearray(stride)
    i = 0
    for _ in range(height):
        filt = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if filt == 1:
                line[x] = (line[x] + a) & 0xFF
            elif filt == 2:
                line[x] = (line[x] + b) & 0xFF
            elif filt == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 0xFF
        prev = line
        out = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if ctype == 0:
                out.append((px[0], px[0], px[0], 255))
            elif ctype == 2:
                out.append((px[0], px[1], px[2], 255))
            elif ctype == 3:
                r, g, bb = palette[px[0]]
                alpha = trns[px[0]] if px[0] < len(trns) else 255
                out.append((r, g, bb, alpha))
            elif ctype == 4:
                out.append((px[0], px[0], px[0], px[1]))
            else:
                out.append((px[0], px[1], px[2], px[3]))
        rows.append(out)
    return width, height, rows


def to_white(rows, width, height, size_w, size_h, gamma=1.0):
    """Box-downscales to size_w x size_h; white RGB, alpha = alpha x luminance (premultiplied average)."""
    img = Image(size_w, size_h)
    fx = width / float(size_w)
    fy = height / float(size_h)
    for y in range(size_h):
        y0 = int(y * fy)
        y1 = max(y0 + 1, int((y + 1) * fy))
        for x in range(size_w):
            x0 = int(x * fx)
            x1 = max(x0 + 1, int((x + 1) * fx))
            total = 0.0
            n = 0
            for yy in range(y0, min(y1, height)):
                row = rows[yy]
                for xx in range(x0, min(x1, width)):
                    r, g, b, a = row[xx]
                    lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                    total += (a / 255.0) * lum
                    n += 1
            value = total / max(1, n)
            if gamma != 1.0:
                value = value ** gamma
            img.set(x, y, (255, 255, 255, int(round(value * 255))))
    return img


# ------------------------------------------------------------------ procedural helpers

def value_noise(seed, cells):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
    for i in range(cells + 1):
        grid[i][cells] = grid[i][0]
        grid[cells][i] = grid[0][i]

    def sample(u, v):
        x = (u % 1.0) * cells
        y = (v % 1.0) * cells
        x0 = int(x)
        y0 = int(y)
        tx = x - x0
        ty = y - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * tx
        b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * tx
        return a + (b - a) * ty

    return sample


def fbm(seed, octaves=5):
    layers = [value_noise(seed * 31 + i, 4 << i) for i in range(octaves)]

    def sample(u, v):
        total = 0.0
        amp = 0.5
        norm = 0.0
        for layer in layers:
            total += layer(u, v) * amp
            norm += amp
            amp *= 0.5
        return total / norm

    return sample


def clamp01(v):
    return max(0.0, min(1.0, v))


def grain(n):
    img = Image(64, 64)
    rnd = random.Random(9000 + n)
    for y in range(64):
        for x in range(64):
            v = rnd.random()
            # bright and dark specks in one white texture: alpha carries the speck strength
            a = abs(v - 0.5) * 2.0
            a = a ** 2.2
            img.set(x, y, (255, 255, 255, int(40 + a * 215)))
    return img


def anamorphic():
    img = Image(256, 16)
    for y in range(16):
        dy = abs(y - 7.5) / 7.5
        for x in range(256):
            dx = abs(x - 127.5) / 127.5
            core = math.exp(-(dy * 3.2) ** 2) * (1.0 - dx ** 1.6)
            img.set(x, y, (255, 255, 255, int(clamp01(core) * 255)))
    return img


def leak(n):
    img = Image(128, 128)
    noise = fbm(700 + n, 4)
    cx, cy = [(0.0, 0.5), (0.0, 0.15), (0.0, 0.85)][n]
    for y in range(128):
        for x in range(128):
            u = x / 127.0
            v = y / 127.0
            d = math.hypot(u - cx, (v - cy) * 0.8)
            base = clamp01(1.0 - d / 1.05) ** 1.8
            wobble = 0.65 + 0.35 * noise(u * 0.8, v * 0.8)
            a = clamp01(base * wobble)
            # warm white core fading to a softer edge (tinted by the client)
            img.set(x, y, (255, int(235 + 20 * (1 - d)), int(220 + 35 * (1 - d)), int(a * 255)))
    return img


def haze():
    img = Image(64, 64)
    noise = fbm(4242, 4)
    for y in range(64):
        for x in range(64):
            u = x / 64.0
            v = y / 64.0
            edge = math.sin(math.pi * u) * (0.35 + 0.65 * math.sin(math.pi * v))
            wave = 0.5 + 0.5 * math.sin((u * 6.0 + noise(u, v) * 3.0) * math.pi * 2.0)
            a = clamp01(edge * (0.35 + 0.65 * wave))
            img.set(x, y, (255, 255, 255, int(26 + a * 229) if a > 0.02 else 0))
    return img


def star_river():
    img = Image(256, 64)
    noise = fbm(1717, 5)
    rnd = random.Random(1718)
    for y in range(64):
        for x in range(256):
            u = x / 256.0
            v = y / 63.0
            band = math.exp(-((v - 0.5) / 0.26) ** 2)
            cloud = noise(u * 2.0, v * 0.5)
            a = clamp01(band * (0.25 + 0.75 * cloud) * 0.85)
            img.set(x, y, (int(200 + 55 * cloud), int(210 + 45 * band), 255, int(a * 255)))
    for _ in range(900):
        x = rnd.randrange(256)
        v = rnd.gauss(0.5, 0.2)
        y = int(clamp01(v) * 63)
        bright = rnd.random() ** 3
        img.blend(x, y, (255, 255, 255, int(120 + bright * 135)))
        if bright > 0.6:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img.blend((x + dx) % 256, y + dy, (255, 255, 255, int(80 * bright)))
    return img


def nebula(n):
    img = Image(128, 128)
    noise = fbm(3300 + n * 7, 5)
    warp = fbm(3400 + n * 7, 3)
    for y in range(128):
        for x in range(128):
            u = x / 127.0
            v = y / 127.0
            d = math.hypot(u - 0.5, v - 0.5) / 0.5
            fall = clamp01(1.0 - d) ** 1.4
            w = warp(u, v)
            c = noise(u + w * 0.35, v - w * 0.35)
            a = clamp01((c - 0.32) * 1.9) * fall
            img.set(x, y, (255, int(225 + 30 * c), int(235 + 20 * c), int(a * 255)))
    return img


def palace_strip():
    """Heavenly palace silhouettes on a cloud bank: tiered pagodas and halls with upswept eaves and gold roof rims."""
    img = Image(256, 64)
    rnd = random.Random(5150)
    dark = (22, 20, 38)
    wall = (34, 30, 56)
    gold = (255, 213, 74)

    def roof(cx, y, half):
        # a curved roof: flat ridge, sloping sides, eave tips curling up two pixels
        for i in range(-half - 3, half + 4):
            edge = abs(i) - half
            curl = 0 if edge <= 0 else min(2, edge)
            top = y - 3 + (0 if abs(i) < half - 1 else 1) - curl
            for yy in range(top, y + 1):
                img.set(cx + i, yy, dark + (255,))
            img.set(cx + i, top - 1, gold + (235,))
        img.set(cx, y - 5, gold + (255,))
        img.set(cx, y - 6, gold + (200,))

    def storey(cx, base, half, h):
        for yy in range(base - h, base):
            for xx in range(cx - half, cx + half + 1):
                pillar = (xx - cx) % 4 == 0
                img.set(xx, yy, (dark if pillar else wall) + (255,))
        roof(cx, base - h, half + 1)

    x = 8
    while x < 248:
        kind = rnd.random()
        base = 47
        if kind < 0.35:
            # pagoda: four or five shrinking storeys
            half = rnd.randint(5, 7)
            tiers = rnd.randint(4, 5)
            y = base
            for t in range(tiers):
                h = 4 if t else 5
                storey(x + half, y, max(2, half - t), h)
                y -= h + 3
            x += half * 2 + rnd.randint(8, 14)
        else:
            # hall: a wide storey on a terrace, sometimes a second roof above
            half = rnd.randint(10, 16)
            for yy in range(base - 2, base):
                for xx in range(x, x + half * 2 + 3):
                    img.set(xx, yy, dark + (255,))
            storey(x + half + 1, base - 2, half, 6)
            if rnd.random() < 0.6:
                storey(x + half + 1, base - 11, max(4, half - 5), 4)
            x += half * 2 + rnd.randint(6, 12)
    # the cloud bank in front of the bases
    cloud = fbm(5151, 4)
    for y in range(40, 64):
        for x in range(256):
            v = (y - 40) / 23.0
            c = cloud(x / 256.0 * 3.0, v)
            a = clamp01((v * 1.4 - 0.1) * (0.55 + 0.6 * c))
            img.blend(x, y, (236, 240, 255, int(a * 235)))
    return img


def sun_corona():
    img = Image(64, 64)
    for y in range(64):
        for x in range(64):
            dx = (x - 31.5) / 31.5
            dy = (y - 31.5) / 31.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.cos(ang * 12.0)
            core = clamp01(1.0 - d / 0.32)
            glow = clamp01(1.0 - d) ** 2.2
            a = clamp01(core + glow * (0.55 + 0.45 * rays))
            img.set(x, y, (255, 255, 255, int(a * 255)))
    return img


def moon_disc():
    img = Image(64, 64)
    noise = fbm(6060, 4)
    for y in range(64):
        for x in range(64):
            dx = (x - 31.5) / 30.0
            dy = (y - 31.5) / 30.0
            d = math.hypot(dx, dy)
            if d > 1.0:
                halo = clamp01(1.0 - (d - 1.0) / 0.06)
                if halo > 0:
                    img.set(x, y, (230, 236, 255, int(halo * 120)))
                continue
            c = noise(x / 64.0, y / 64.0)
            shade = 0.78 + 0.22 * c - 0.18 * clamp01(dx * 0.6 + dy * 0.4)
            g = int(255 * clamp01(shade))
            img.set(x, y, (g, g, min(255, g + 12), 255))
    return img


# ------------------------------------------------------------------ Vorbis channel count

def ogg_channels(path):
    with open(path, "rb") as f:
        head = f.read(256)
    marker = head.find(b"\x01vorbis")
    if marker < 0:
        return -1
    return head[marker + 11]


# ------------------------------------------------------------------ main

def main():
    if len(sys.argv) < 2:
        print("usage: python -I tools/gen_film_assets.py <kenney_dir>")
        sys.exit(1)
    kenney = os.path.abspath(sys.argv[1])
    sprites = os.path.join(kenney, "kenney_particle-pack", "PNG (Transparent)")
    for d in (PARTICLE_TEX, PARTICLE_JSON, MISC, SOUNDS):
        os.makedirs(d, exist_ok=True)

    # particle sprites
    for name, sources, size in PARTICLES:
        textures = []
        for i, src in enumerate(sources):
            w, h, rows = decode_png(os.path.join(sprites, src + ".png"))
            img = to_white(rows, w, h, size, size)
            out = "%s_%d" % (name, i)
            img.save(os.path.join(PARTICLE_TEX, out + ".png"))
            textures.append("kultivator:" + out)
        with open(os.path.join(PARTICLE_JSON, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": textures}, f, indent=2)
            f.write("\n")
        print("particle", name, len(textures))

    # Kenney HUD and world textures
    w, h, rows = decode_png(os.path.join(sprites, "Rotated", "trace_02_rotated.png"))
    to_white(rows, w, h, 128, 32).save(os.path.join(MISC, "smear.png"))
    w, h, rows = decode_png(os.path.join(sprites, "scratch_01.png"))
    to_white(rows, w, h, 128, 128).save(os.path.join(MISC, "scratch.png"))
    for i in range(3):
        w, h, rows = decode_png(os.path.join(sprites, "scorch_%02d.png" % (i + 1)))
        to_white(rows, w, h, 64, 64).save(os.path.join(MISC, "scorch_%d.png" % i))
    w, h, rows = decode_png(os.path.join(sprites, "window_02.png"))
    to_white(rows, w, h, 64, 64).save(os.path.join(MISC, "leak_window.png"))
    w, h, rows = decode_png(os.path.join(sprites, "flare_01.png"))
    to_white(rows, w, h, 64, 64).save(os.path.join(MISC, "flare_core.png"))
    w, h, rows = decode_png(os.path.join(sprites, "circle_05.png"))
    to_white(rows, w, h, 64, 64).save(os.path.join(MISC, "flare_ghost.png"))
    w, h, rows = decode_png(os.path.join(sprites, "light_02.png"))
    to_white(rows, w, h, 64, 64).save(os.path.join(MISC, "flare_light.png"))
    w, h, rows = decode_png(os.path.join(sprites, "star_06.png"))
    to_white(rows, w, h, 32, 32).save(os.path.join(MISC, "cosmic_star.png"))

    # our own procedural textures
    for i in range(4):
        grain(i).save(os.path.join(MISC, "grain_%d.png" % i))
    anamorphic().save(os.path.join(MISC, "anamorphic.png"))
    for i in range(3):
        leak(i).save(os.path.join(MISC, "leak_%d.png" % i))
    haze().save(os.path.join(MISC, "haze.png"))
    star_river().save(os.path.join(MISC, "star_river.png"))
    for i in range(3):
        nebula(i).save(os.path.join(MISC, "nebula_%d.png" % i))
    palace_strip().save(os.path.join(MISC, "palace_strip.png"))
    sun_corona().save(os.path.join(MISC, "sun_corona.png"))
    moon_disc().save(os.path.join(MISC, "moon_disc.png"))
    print("misc textures written")

    # sounds: copy byte for byte, then merge sounds.json
    packs = {"sci": os.path.join(kenney, "kenney_sci-fi-sounds", "Audio"),
             "imp": os.path.join(kenney, "kenney_impact-sounds", "Audio")}
    sounds_json = os.path.join(ASSETS, "sounds.json")
    table = {}
    if os.path.exists(sounds_json):
        with open(sounds_json, "r", encoding="utf-8") as f:
            table = json.load(f)
    total = 0
    for cue, files in SOUND_CUES:
        names = []
        channels = set()
        for i, (pack, base) in enumerate(files):
            src = os.path.join(packs[pack], base + ".ogg")
            dst_name = "%s_%d" % (cue, i)
            dst = os.path.join(SOUNDS, dst_name + ".ogg")
            shutil.copyfile(src, dst)
            total += os.path.getsize(dst)
            channels.add(ogg_channels(dst))
            names.append({"name": "kultivator:film/" + dst_name})
        table["film." + cue] = {"subtitle": "subtitles.kultivator.film." + cue, "sounds": names}
        print("sound", cue, len(names), "channels", sorted(channels))
    with open(sounds_json, "w", encoding="utf-8", newline="\n") as f:
        json.dump(table, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("sounds.json events:", len(table), "film bytes:", total)


if __name__ == "__main__":
    main()

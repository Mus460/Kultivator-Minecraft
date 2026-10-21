"""Generates the textures of the Film Kosmik 3D Orbit stage (DESIGN 32.2.2, module O).

Equirectangular maps (2:1, u = (lon + 180) / 360 with lon measured from +Z towards +X (east), v = (90 - lat) / 180;
the 3D point of a pixel is (cos lat sin lon, sin lat, cos lat cos lon), the planet's home meridian is lon 0 = the
middle column). Noise is 3D value-noise fbm sampled on the unit sphere, so every map is seamless in longitude and not
pinched at the poles. Every map is generated at its largest size and box-downsampled.

textures/misc/kosmik/orbit/
  planet_day_256/512/1024.png   the default albedo (oceans, beaches, plains, forest, taiga, savanna, desert belts at
                                15-30 deg, jungle near the equator, ridges with slope darkening, ice caps above 70 deg);
                                a continent along the home meridian (lat 0-70 N, lon +-25) holds every climate band
  planet_class_256/512/1024.png R = class * 16 (0 deep ocean .. 10 snow/ice), G = detail luminance, B = home weight (1 on
                                the home meridian arc lat 0-70 N, 0 at 35 deg of arc), A = 255
  planet_night_256/512.png      warm city-light speckles (additive: premultiplied RGB, alpha = intensity)
  clouds_256/512/1024.png       white with alpha: ~45 % cover, cyclone swirls, thin polar bands
  space_256/512/1024.png        the dome: black, soft stars, the Milky Way band tilted 60 deg, faint nebula tints
  moon_128/256.png              grey regolith, maria, craters with rims and rays
  sun_core.png 128, sun_corona.png 256, limb_glow.png 256, star_spike.png 128 (additive, premultiplied)
  atmo.png 32                   flat white (the shells' colour and alpha are per vertex)
  plasma.png 64x128             the re-entry sheath: u around the cone (tiles), v from the leading point (white) to the
                                rim (orange -> violet -> black)
  trail.png 128x32              the burning trail: u along (tiles), v across
  beam.png 32x128               a star beam: u (32 px) along (tiles), v (128 px) across
  star_line.png 64x8            a Dipper line: u along (tiles), v across
  rune_ring.png 256             a gold annulus with twelve of our own seal glyphs (abstract strokes)
  patch_land_256/512.png        tileable local land detail (fields, hedges, woods, rivers), near-grey for tinting
  patch_cloud_256/512.png       tileable cumulus deck, white with alpha
  cell_top.png 32, cell_side.png 32x64, cell_bottom.png 32   Gunung's lifted cells (nearest filtering, blocky)

Additive textures (energySwirl, blend ONE/ONE, texture alpha cut below 0.1): RGB is premultiplied by the intensity and
alpha = min(1, 4 * intensity), so the cut edge is invisible and black adds nothing. Every smooth texture gets a
.png.mcmeta with "blur" (linear filtering); glows also "clamp".

Run with: python -I tools/gen_kosmik_orbit.py   (stdlib + tools/png.py; fixed seed; about two minutes)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator",
                   "textures", "misc", "kosmik", "orbit")
SEED = 3202610
MAX_TOTAL_BYTES = 8 * 1024 * 1024

# ---------------------------------------------------------------------------------------------------------------- noise

_rng = random.Random(SEED)
_PERM = list(range(256))
_rng.shuffle(_PERM)
_PERM += _PERM
_VAL = [_rng.random() for _ in range(256)]


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smooth(t):
    t = clamp(t)
    return t * t * (3.0 - 2.0 * t)


def sstep(a, b, x):
    if a == b:
        return 1.0 if x >= b else 0.0
    return smooth((x - a) / (b - a))


def lerp(a, b, t):
    return a + (b - a) * t


def noise3(x, y, z):
    """3D value noise in [0, 1]."""
    xi = math.floor(x)
    yi = math.floor(y)
    zi = math.floor(z)
    fx = x - xi
    fy = y - yi
    fz = z - zi
    xi &= 255
    yi &= 255
    zi &= 255
    ux = fx * fx * (3 - 2 * fx)
    uy = fy * fy * (3 - 2 * fy)
    uz = fz * fz * (3 - 2 * fz)
    p = _PERM
    v = _VAL
    a = p[xi] + yi
    aa = p[a] + zi
    ab = p[a + 1] + zi
    b = p[xi + 1] + yi
    ba = p[b] + zi
    bb = p[b + 1] + zi
    x1 = v[p[aa]] + (v[p[ba]] - v[p[aa]]) * ux
    x2 = v[p[ab]] + (v[p[bb]] - v[p[ab]]) * ux
    y1 = x1 + (x2 - x1) * uy
    x3 = v[p[aa + 1]] + (v[p[ba + 1]] - v[p[aa + 1]]) * ux
    x4 = v[p[ab + 1]] + (v[p[bb + 1]] - v[p[ab + 1]]) * ux
    y2 = x3 + (x4 - x3) * uy
    return y1 + (y2 - y1) * uz


def fbm3(x, y, z, octaves, lac=2.0, gain=0.5):
    total = 0.0
    amp = 0.5
    norm = 0.0
    for _ in range(octaves):
        total += amp * noise3(x, y, z)
        norm += amp
        x *= lac
        y *= lac
        z *= lac
        x += 17.31
        y += 9.17
        z += 3.71
        amp *= gain
    return total / norm


def ridge3(x, y, z, octaves):
    total = 0.0
    amp = 0.5
    norm = 0.0
    for _ in range(octaves):
        n = 1.0 - abs(2.0 * noise3(x, y, z) - 1.0)
        total += amp * n * n
        norm += amp
        x = x * 2.0 + 5.1
        y = y * 2.0 + 1.3
        z = z * 2.0 + 7.7
        amp *= 0.5
    return total / norm


def noise2p(x, y, period):
    """2D value noise in [0, 1], periodic with an integer lattice period."""
    xi = math.floor(x)
    yi = math.floor(y)
    fx = x - xi
    fy = y - yi
    x0 = xi % period
    y0 = yi % period
    x1 = (x0 + 1) % period
    y1 = (y0 + 1) % period
    p = _PERM
    v = _VAL
    ux = fx * fx * (3 - 2 * fx)
    uy = fy * fy * (3 - 2 * fy)
    a = v[p[p[x0] + y0]]
    b = v[p[p[x1] + y0]]
    c = v[p[p[x0] + y1]]
    d = v[p[p[x1] + y1]]
    return lerp(lerp(a, b, ux), lerp(c, d, ux), uy)


def fbm2p(u, v, base_period, octaves, offset=0.0):
    """Tileable 2D fbm on the unit square (u, v in [0, 1))."""
    total = 0.0
    amp = 0.5
    norm = 0.0
    period = base_period
    for o in range(octaves):
        total += amp * noise2p(u * period + offset * (o + 1), v * period + offset * 0.5 * (o + 1), period)
        norm += amp
        period *= 2
        amp *= 0.5
    return total / norm


def sphere_point(x, y, w, h):
    lon = (x + 0.5) / w * 360.0 - 180.0
    lat = 90.0 - (y + 0.5) / h * 180.0
    rl = math.radians(lat)
    ro = math.radians(lon)
    cl = math.cos(rl)
    return lat, lon, cl * math.sin(ro), math.sin(rl), cl * math.cos(ro)


def arc_deg(lat1, lon1, lat2, lon2):
    a1 = math.radians(lat1)
    a2 = math.radians(lat2)
    d = math.sin(a1) * math.sin(a2) + math.cos(a1) * math.cos(a2) * math.cos(math.radians(lon1 - lon2))
    return math.degrees(math.acos(clamp(d, -1.0, 1.0)))


# ------------------------------------------------------------------------------------------------------------- helpers


def to_byte(v):
    return int(clamp(v) * 255.0 + 0.5)


def add_pixel(img, x, y, rgb_intensity):
    """Writes an additive (premultiplied) pixel from linear rgb in [0, 1]."""
    r, g, b = (clamp(c) for c in rgb_intensity)
    i = max(r, g, b)
    img.set(x, y, (to_byte(r), to_byte(g), to_byte(b), to_byte(min(1.0, 4.0 * i))))


def downsample(img, keep_class=False):
    """Box 2x2 downsample (the class map keeps the top-left class and averages G and B)."""
    w = img.width // 2
    h = img.height // 2
    out = Image(w, h)
    for y in range(h):
        for x in range(w):
            p0 = img.get(2 * x, 2 * y)
            p1 = img.get(2 * x + 1, 2 * y)
            p2 = img.get(2 * x, 2 * y + 1)
            p3 = img.get(2 * x + 1, 2 * y + 1)
            avg = [(p0[c] + p1[c] + p2[c] + p3[c] + 2) // 4 for c in range(4)]
            if keep_class:
                avg[0] = p0[0]
                avg[3] = 255
            out.set(x, y, avg)
    return out


WRITTEN = []


def save(img, name, blur=True, clamp_edges=False):
    path = os.path.join(OUT, name)
    img.save(path)
    WRITTEN.append((name, img))
    meta_path = path + ".mcmeta"
    if blur or clamp_edges:
        meta = {"texture": {}}
        if blur:
            meta["texture"]["blur"] = True
        if clamp_edges:
            meta["texture"]["clamp"] = True
        with open(meta_path, "w", newline="\n") as f:
            f.write(json.dumps(meta) + "\n")
    elif os.path.exists(meta_path):
        os.remove(meta_path)


def save_chain(img, base, sizes, keep_class=False):
    """Saves img (the largest size) and its box-downsampled smaller sizes as base_<width>.png."""
    current = img
    for size in sorted(sizes, reverse=True):
        while current.width > size:
            current = downsample(current, keep_class)
        save(current, "%s_%d.png" % (base, size), blur=not keep_class)


# ------------------------------------------------------------------------------------------------------- planet maps

DEEP, SHALLOW, BEACH, PLAINS, FOREST, TAIGA, SAVANNA, DESERT, JUNGLE, ROCK, SNOW = range(11)

DAY = {
    DEEP: (10, 30, 84),
    SHALLOW: (26, 86, 146),
    BEACH: (212, 198, 148),
    PLAINS: (110, 152, 64),
    FOREST: (46, 102, 42),
    TAIGA: (54, 88, 66),
    SAVANNA: (156, 150, 78),
    DESERT: (218, 188, 126),
    JUNGLE: (30, 116, 34),
    ROCK: (118, 110, 102),
    SNOW: (236, 242, 250),
}


def home_mask(lat, lon):
    """1 on the home continent (lat -6..76, lon +-27), soft 8 deg edges."""
    a = sstep(-14.0, -4.0, lat) * (1.0 - sstep(70.0, 80.0, lat))
    b = 1.0 - sstep(18.0, 30.0, abs(lon))
    return a * b


def planet_maps(w, h):
    print("planet: fields %dx%d" % (w, h))
    elev = [0.0] * (w * h)
    moist = [0.0] * (w * h)
    jitter = [0.0] * (w * h)
    detail = [0.0] * (w * h)
    hm = [0.0] * (w * h)
    rid = [0.0] * (w * h)
    lats = [0.0] * h
    for y in range(h):
        for x in range(w):
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            lats[y] = lat
            i = y * w + x
            e = fbm3(px * 1.6 + 11.0, py * 1.6 + 3.0, pz * 1.6 - 7.0, 7)
            m = home_mask(lat, lon)
            core = (1.0 - sstep(8.0, 16.0, abs(lon))) * sstep(-4.0, 2.0, lat) * (1.0 - sstep(72.0, 78.0, lat))
            e += 0.20 * m + 0.12 * core
            # the antipode of home stays open ocean (keeps the texture seam calm)
            back = 1.0 - sstep(0.0, 28.0, 180.0 - abs(lon))
            e -= 0.22 * back
            r = ridge3(px * 3.1 + 2.0, py * 3.1 - 4.0, pz * 3.1 + 1.0, 4)
            e += 0.10 * r * r * r
            rid[y * w + x] = r
            elev[i] = e
            hm[i] = m
            moist[i] = fbm3(px * 2.3 - 31.0, py * 2.3 + 12.0, pz * 2.3 + 5.0, 4)
            jitter[i] = fbm3(px * 4.0 + 50.0, py * 4.0, pz * 4.0 - 20.0, 3)
            detail[i] = fbm3(px * 24.0 + 3.0, py * 24.0 - 8.0, pz * 24.0 + 1.0, 3)
    srt = sorted(elev)
    sea = srt[int(len(srt) * 0.70)]
    # ridges (not the continent bias) make the mountains: the top 9 % of the ridge field on land, peaks the top 3 %
    land = sorted(rid[i] for i in range(w * h) if elev[i] > sea)
    mountain = land[int(len(land) * 0.91)] if land else 1.0
    peak = land[int(len(land) * 0.975)] if land else 1.0
    print("planet: sea %.3f mountain %.3f peak %.3f" % (sea, mountain, peak))

    day = Image(w, h)
    cls = Image(w, h)
    classes = [0] * (w * h)
    for y in range(h):
        for x in range(w):
            i = y * w + x
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            e = elev[i]
            alat = abs(lat) + (jitter[i] - 0.5) * 8.0
            near = hm[i]
            if e <= sea:
                depth = sea - e
                if alat > 76.0:
                    c = SNOW
                else:
                    c = DEEP if depth > 0.045 else SHALLOW
            else:
                hgt = e - sea
                m = lerp(moist[i], 0.5, near * 0.8)
                if alat > 71.0:
                    c = SNOW
                elif rid[i] > mountain and hgt > 0.02:
                    c = SNOW if (rid[i] > peak and alat > 28.0) else ROCK
                elif hgt < 0.006:
                    c = BEACH
                elif alat < 11.0:
                    c = JUNGLE if (m > 0.40 or near > 0.5) else SAVANNA
                elif alat < 16.0:
                    c = SAVANNA
                elif alat < 31.0:
                    c = DESERT if (m < 0.60 or near > 0.5) else SAVANNA
                elif alat < 50.0:
                    c = FOREST if m > 0.52 else PLAINS
                elif alat < 64.0:
                    c = TAIGA
                else:
                    c = SNOW if m > 0.45 else TAIGA
            classes[i] = c
    # slope darkening (no baked sun) and the colours
    for y in range(h):
        for x in range(w):
            i = y * w + x
            c = classes[i]
            d = detail[i]
            e = elev[i]
            xl = (x - 1) % w
            xr = (x + 1) % w
            yu = max(0, y - 1)
            yd = min(h - 1, y + 1)
            lat = lats[y]
            sx = (elev[y * w + xr] - elev[y * w + xl]) / max(0.15, math.cos(math.radians(lat)))
            sy = elev[yd * w + x] - elev[yu * w + x]
            slope = math.sqrt(sx * sx + sy * sy) * w / 1024.0
            base = DAY[c]
            if c in (DEEP, SHALLOW):
                depth = clamp((sea - e) / 0.14)
                col = [lerp(DAY[SHALLOW][k], DAY[DEEP][k] * 0.8, smooth(depth)) for k in range(3)]
                shade = 0.94 + 0.12 * d
            else:
                col = list(base)
                shade = (0.82 + 0.36 * d) * (1.0 - clamp(slope * 9.0) * 0.45)
                if c == DESERT:
                    shade *= 0.92 + 0.16 * noise3(x * 0.15, y * 0.4, 3.0)
            day.set(x, y, (col[0] * shade, col[1] * shade, col[2] * shade, 255))
            lat, lon, _, _, _ = sphere_point(x, y, w, h)
            dl = arc_deg(lat, lon, clamp(lat, 0.0, 70.0), 0.0)
            hw = 1.0 - sstep(0.0, 35.0, dl)
            cls.set(x, y, (c * 16, to_byte(clamp(0.5 + (d - 0.5) * 1.6)), to_byte(hw), 255))
    return day, cls, classes, elev, sea


def night_map(w, h, classes, elev, sea, src_w):
    """City lights at w x h (sampling the classes of the src_w map)."""
    print("planet: night %dx%d" % (w, h))
    rng = random.Random(SEED + 7)
    img = Image(w, h, (0, 0, 0, 0))
    step = src_w // w
    lum = [0.0] * (w * h)
    for y in range(h):
        for x in range(w):
            si = (y * step) * src_w + x * step
            c = classes[si]
            if c in (DEEP, SHALLOW, SNOW, ROCK):
                continue
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            hgt = elev[si] - sea
            coast = 1.0 - sstep(0.0, 0.035, hgt)
            river = 1.0 - sstep(0.0, 0.03, abs(noise3(px * 7.0 + 9.0, py * 7.0, pz * 7.0 - 4.0) - 0.5))
            city = sstep(0.62, 0.8, fbm3(px * 14.0 - 2.0, py * 14.0 + 6.0, pz * 14.0 + 3.0, 3))
            p = 0.015 + 0.30 * coast + 0.22 * river + 0.45 * city
            if c == DESERT:
                p *= 0.25
            if abs(lat) > 62.0:
                p *= 0.3
            if rng.random() < p:
                lum[y * w + x] = 0.35 + 0.65 * rng.random() * (0.6 + 0.4 * city)
    for y in range(h):
        for x in range(w):
            v = lum[y * w + x]
            halo = 0.0
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                halo += lum[((y + dy) % h) * w + (x + dx) % w]
            i = clamp(v + 0.18 * halo)
            if i <= 0.0:
                continue
            add_pixel(img, x, y, (i * 1.0, i * 0.78, i * 0.46))
    return img


def clouds_map(w, h):
    print("clouds %dx%d" % (w, h))
    rng = random.Random(SEED + 11)
    cyclones = []
    for k in range(4):
        lat = (rng.uniform(12.0, 45.0)) * (1 if k % 2 == 0 else -1)
        lon = rng.uniform(-170.0, 170.0)
        rl = math.radians(lat)
        ro = math.radians(lon)
        axis = (math.cos(rl) * math.sin(ro), math.sin(rl), math.cos(rl) * math.cos(ro))
        spin = rng.uniform(2.5, 4.0) * (1 if lat > 0 else -1)
        cyclones.append((axis, spin, rng.uniform(0.16, 0.24)))
    cover = [0.0] * (w * h)
    for y in range(h):
        for x in range(w):
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            qx, qy, qz = px, py, pz
            eye = 0.0
            for (ax, ay, az), spin, rad in cyclones:
                d = math.acos(clamp(qx * ax + qy * ay + qz * az, -1.0, 1.0))
                f = math.exp(-(d / rad) ** 2)
                ang = spin * f
                if ang != 0.0:
                    c = math.cos(ang)
                    s = math.sin(ang)
                    dot = qx * ax + qy * ay + qz * az
                    cx = ay * qz - az * qy
                    cy = az * qx - ax * qz
                    cz = ax * qy - ay * qx
                    qx, qy, qz = (qx * c + cx * s + ax * dot * (1 - c),
                                  qy * c + cy * s + ay * dot * (1 - c),
                                  qz * c + cz * s + az * dot * (1 - c))
                eye = max(eye, math.exp(-(d / (rad * 0.12)) ** 2))
            n = fbm3(qx * 3.2 + 40.0, qy * 3.2 - 12.0, qz * 3.2 + 8.0, 6)
            # thin polar bands and an equatorial streak
            band = math.exp(-((abs(lat) - 58.0) / 4.0) ** 2) * 0.10 * noise3(lon * 0.35, lat * 0.2, 5.0)
            itcz = math.exp(-(lat / 5.0) ** 2) * 0.05
            cover[y * w + x] = n + band + itcz - eye * 0.3
    srt = sorted(cover)
    t = srt[int(len(srt) * 0.55)]
    img = Image(w, h, (255, 255, 255, 0))
    for y in range(h):
        for x in range(w):
            v = cover[y * w + x]
            a = sstep(t - 0.05, t + 0.10, v)
            a = a * (0.75 + 0.25 * sstep(t, t + 0.2, v))
            g = 236 + int(19 * sstep(t, t + 0.25, v))
            img.set(x, y, (g, g, min(255, g + 4), to_byte(a)))
    return img


def space_map(w, h):
    print("space %dx%d" % (w, h))
    rng = random.Random(SEED + 23)
    ng = (0.0, math.cos(math.radians(60.0)), math.sin(math.radians(60.0)))
    gc = (1.0, 0.0, 0.0)
    rgb = [None] * (w * h)
    for y in range(h):
        for x in range(w):
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            dn = px * ng[0] + py * ng[1] + pz * ng[2]
            core = math.exp(-(math.acos(clamp(px * gc[0] + py * gc[1] + pz * gc[2], -1, 1)) ** 2) / 0.35)
            band = math.exp(-(dn * dn) / 0.018) + 0.35 * math.exp(-(dn * dn) / 0.09)
            struct = fbm3(px * 6.0 + 1.0, py * 6.0 + 2.0, pz * 6.0 + 3.0, 5)
            lanes = sstep(0.52, 0.70, fbm3(px * 9.0 - 5.0, py * 9.0 + 7.0, pz * 9.0 - 1.0, 4)) * math.exp(-(dn * dn) / 0.006)
            mw = band * (0.35 + 0.9 * struct) * (0.55 + 1.3 * core) * (1.0 - 0.75 * lanes)
            mw = clamp(mw * 0.30, 0.0, 0.55)
            warm = clamp(core * 1.2)
            r = mw * lerp(0.74, 1.0, warm)
            g = mw * lerp(0.80, 0.92, warm)
            b = mw * lerp(1.0, 0.80, warm)
            n1 = fbm3(px * 2.0 + 9.0, py * 2.0, pz * 2.0 - 9.0, 3)
            n2 = fbm3(px * 2.4 - 9.0, py * 2.4 + 4.0, pz * 2.4, 3)
            v = sstep(0.55, 0.8, n1) * 0.07
            t = sstep(0.58, 0.82, n2) * 0.05
            r += v * 0.55 + t * 0.10 + 0.006
            g += v * 0.20 + t * 0.42 + 0.006
            b += v * 0.75 + t * 0.48 + 0.014
            rgb[y * w + x] = [r, g, b]
    # stars: about one per 60 px, as soft dots kept round on the sphere
    count = w * h // 60
    for _ in range(count):
        zz = rng.uniform(-1.0, 1.0)
        th = rng.uniform(-math.pi, math.pi)
        rr = math.sqrt(1.0 - zz * zz)
        px, py, pz = rr * math.sin(th), zz, rr * math.cos(th)
        lat = math.degrees(math.asin(py))
        lon = math.degrees(math.atan2(px, pz))
        dn = px * ng[0] + py * ng[1] + pz * ng[2]
        if rng.random() > 0.55 + 0.45 * math.exp(-(dn * dn) / 0.03):
            continue
        cx = (lon + 180.0) / 360.0 * w
        cy = (90.0 - lat) / 180.0 * h
        mag = rng.random() ** 3.2
        bright = 0.18 + 0.95 * mag
        rad = (0.55 + 0.9 * mag) * w / 1024.0
        temp = rng.random()
        col = (lerp(0.72, 1.0, temp), lerp(0.82, 0.92, temp), lerp(1.0, 0.70, temp)) if temp > 0.15 else (1.0, 0.78, 0.55)
        sx = rad / max(0.08, math.cos(math.radians(lat)))
        x0 = int(math.floor(cx - sx * 2.5))
        x1 = int(math.ceil(cx + sx * 2.5))
        y0 = int(math.floor(cy - rad * 2.5))
        y1 = int(math.ceil(cy + rad * 2.5))
        for yy in range(max(0, y0), min(h, y1 + 1)):
            for xx in range(x0, x1 + 1):
                dx = (xx + 0.5 - cx) / sx
                dy = (yy + 0.5 - cy) / rad
                f = math.exp(-(dx * dx + dy * dy)) * bright
                if f < 0.004:
                    continue
                px_ = rgb[yy * w + (xx % w)]
                px_[0] += col[0] * f
                px_[1] += col[1] * f
                px_[2] += col[2] * f
    img = Image(w, h)
    for y in range(h):
        for x in range(w):
            r, g, b = rgb[y * w + x]
            img.set(x, y, (to_byte(r), to_byte(g), to_byte(b), 255))
    return img


def moon_map(w, h):
    print("moon %dx%d" % (w, h))
    rng = random.Random(SEED + 31)
    craters = []
    for k in range(90):
        zz = rng.uniform(-1.0, 1.0)
        th = rng.uniform(-math.pi, math.pi)
        rr = math.sqrt(1.0 - zz * zz)
        size = 0.02 + 0.16 * rng.random() ** 2.5
        craters.append(((rr * math.sin(th), zz, rr * math.cos(th)), size, k < 6))
    img = Image(w, h)
    for y in range(h):
        for x in range(w):
            lat, lon, px, py, pz = sphere_point(x, y, w, h)
            base = 0.58 + 0.22 * (fbm3(px * 5.0, py * 5.0, pz * 5.0, 5) - 0.5)
            mare = sstep(0.55, 0.66, fbm3(px * 1.6 + 4.0, py * 1.6 - 2.0, pz * 1.6 + 6.0, 3))
            v = base - 0.20 * mare
            for (cx, cy, cz), size, rays in craters:
                dot = px * cx + py * cy + pz * cz
                if dot < math.cos(size * 3.2):
                    continue
                d = math.acos(clamp(dot, -1.0, 1.0)) / size
                if d < 0.85:
                    v -= 0.12 * (1.0 - d / 0.85) ** 0.5
                elif d < 1.15:
                    v += 0.12 * (1.0 - abs(d - 1.0) / 0.15)
                if rays and d > 1.1:
                    ang = math.atan2(px - cx, pz - cz) * 7.0
                    v += 0.06 * max(0.0, math.sin(ang)) ** 6 * max(0.0, 1.0 - (d - 1.1) / 2.0)
            v += 0.05 * (noise3(px * 40.0, py * 40.0, pz * 40.0) - 0.5)
            g = clamp(v)
            img.set(x, y, (to_byte(g * 1.0), to_byte(g * 0.98), to_byte(g * 0.95), 255))
    return img


# ------------------------------------------------------------------------------------------------------------ glows


def radial(size, fn, color_fn=None):
    img = Image(size, size, (0, 0, 0, 0))
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dx = (x - c) / c
            dy = (y - c) / c
            r = math.sqrt(dx * dx + dy * dy)
            i = fn(r, math.atan2(dy, dx), x, y) if r < 1.0 else 0.0
            i = clamp(i) * (1.0 - sstep(0.9, 1.0, r))
            col = color_fn(r, i) if color_fn else (1.0, 1.0, 1.0)
            add_pixel(img, x, y, (col[0] * i, col[1] * i, col[2] * i))
    return img


def sun_core():
    return radial(128, lambda r, a, x, y: math.exp(-(r / 0.10) ** 2) + 0.55 * math.exp(-(r / 0.30) ** 2) + 0.18 * (1.0 - r) ** 2,
                  lambda r, i: (1.0, lerp(1.0, 0.86, sstep(0.05, 0.5, r)), lerp(1.0, 0.62, sstep(0.05, 0.6, r))))


def sun_corona():
    def f(r, a, x, y):
        streak = 0.55 + 0.45 * noise3(math.cos(a) * 3.0 + 3.0, math.sin(a) * 3.0, r * 1.5)
        streak = streak ** 3 * 1.6
        return (0.9 * math.exp(-r / 0.20) + 0.9 * streak * math.exp(-r / 0.42)) * (1.0 - r) ** 1.2
    return radial(256, f, lambda r, i: (1.0, 0.86, 0.62))


def limb_glow():
    def f(r, a, x, y):
        peak = 0.87
        if r < peak:
            return sstep(0.78, peak, r) ** 1.5
        return (1.0 - sstep(peak, 1.0, r)) ** 1.6
    return radial(256, f)


def star_spike():
    def f(r, a, x, y):
        c = 63.5
        dx = abs(x - c) / c
        dy = abs(y - c) / c
        spike = math.exp(-(dy / 0.025) ** 2) * (1.0 - dx) ** 2 + math.exp(-(dx / 0.025) ** 2) * (1.0 - dy) ** 2
        return math.exp(-(r / 0.07) ** 2) + 0.25 * math.exp(-(r / 0.25) ** 2) + 0.8 * spike
    return radial(128, f)


def flat_white():
    return Image(32, 32, (255, 255, 255, 255))


def plasma():
    w, h = 64, 128
    img = Image(w, h)
    for y in range(h):
        v = y / (h - 1)
        for x in range(w):
            u = x / w
            streak = fbm2p(u, v * 0.5, 4, 3, 2.0)
            i = (1.0 - v) ** 1.2 * (0.55 + 0.75 * streak) * (1.0 - sstep(0.8, 1.0, v))
            col = (lerp(1.0, 0.62, sstep(0.2, 0.85, v)), lerp(1.0, 0.36, sstep(0.05, 0.5, v)) * lerp(1.0, 0.7, sstep(0.6, 1.0, v)),
                   lerp(1.0, 0.2, sstep(0.05, 0.4, v)) + 0.85 * sstep(0.55, 0.95, v))
            add_pixel(img, x, y, (col[0] * i, col[1] * i, min(1.0, col[2]) * i))
    return img


def trail():
    w, h = 128, 32
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h
        across = math.exp(-((v - 0.5) / 0.22) ** 2) * (1.0 - sstep(0.42, 0.5, abs(v - 0.5)))
        for x in range(w):
            u = x / w
            streak = fbm2p(u, v * 0.25, 8, 3, 5.0)
            i = across * (0.45 + 0.75 * streak) + 0.5 * math.exp(-((v - 0.5) / 0.06) ** 2)
            add_pixel(img, x, y, (i, i * 0.95, i * 0.85))
    return img


def beam():
    """u (x, 32 px) along the beam (tiles, a slow flicker), v (y, 128 px) across (soft profile, bright core)."""
    w, h = 32, 128
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h
        across = math.exp(-((v - 0.5) / 0.16) ** 2) * (1.0 - sstep(0.42, 0.5, abs(v - 0.5)))
        core = math.exp(-((v - 0.5) / 0.04) ** 2)
        for x in range(w):
            u = x / w
            flick = 0.75 + 0.25 * fbm2p(u, v * 0.25, 4, 3, 7.0)
            i = across * 0.7 * flick + core
            add_pixel(img, x, y, (i, i, i))
    return img


def star_line():
    w, h = 64, 8
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h
        across = math.exp(-((v - 0.5) / 0.2) ** 2) * (1.0 - sstep(0.4, 0.5, abs(v - 0.5)))
        for x in range(w):
            u = x / w
            dots = 0.75 + 0.25 * math.cos(u * math.pi * 2.0 * 4.0)
            i = across * dots
            add_pixel(img, x, y, (i, i, i))
    return img


def rune_ring():
    size = 256
    img = Image(size, size)
    c = (size - 1) / 2.0
    rng = random.Random(SEED + 41)
    # twelve glyphs: each a few strokes in a small box (our own abstract seal marks, not real characters)
    glyphs = []
    for g in range(12):
        strokes = []
        for s in range(rng.randint(3, 5)):
            if rng.random() < 0.5:
                a = rng.uniform(-0.4, 0.4)
                strokes.append(((-0.5, a), (0.5, a + rng.uniform(-0.15, 0.15))))
            else:
                a = rng.uniform(-0.4, 0.4)
                strokes.append(((a, -0.5), (a + rng.uniform(-0.2, 0.2), 0.5)))
        if g % 3 == 0:
            strokes.append(((-0.45, -0.45), (0.45, 0.45)))
        glyphs.append(strokes)
    for y in range(size):
        for x in range(size):
            dx = (x - c) / c
            dy = (y - c) / c
            r = math.sqrt(dx * dx + dy * dy)
            if r >= 1.0:
                img.set(x, y, (0, 0, 0, 0))
                continue
            a = math.atan2(dy, dx)
            line = math.exp(-((r - 0.93) / 0.012) ** 2) + math.exp(-((r - 0.62) / 0.010) ** 2) * 0.9
            ticks = math.exp(-((r - 0.97) / 0.015) ** 2) * (max(0.0, math.cos(a * 48.0)) ** 8) * 0.8
            i = line + ticks
            band = (r - 0.66) / (0.89 - 0.66)
            if 0.0 <= band <= 1.0:
                seg = (a / (2.0 * math.pi) + 0.5) * 12.0
                k = int(seg) % 12
                fu = (seg - math.floor(seg)) * 2.0 - 1.0
                fv = band * 2.0 - 1.0
                if abs(fu) < 0.62 and abs(fv) < 0.75:
                    gx = fu / 0.62 * 0.5
                    gy = fv / 0.75 * 0.5
                    best = 1.0
                    for (x0, y0), (x1, y1) in glyphs[k]:
                        vx = x1 - x0
                        vy = y1 - y0
                        t = clamp(((gx - x0) * vx + (gy - y0) * vy) / (vx * vx + vy * vy))
                        ex = gx - (x0 + vx * t)
                        ey = gy - (y0 + vy * t)
                        best = min(best, math.sqrt(ex * ex + ey * ey))
                    i += math.exp(-(best / 0.07) ** 2)
            i *= 1.0 - sstep(0.985, 1.0, r)
            i = clamp(i)
            add_pixel(img, x, y, (i, i * 0.80, i * 0.36))
    return img


# ---------------------------------------------------------------------------------------------------- local patch


def patch_land(size):
    print("patch_land %d" % size)
    img = Image(size, size)
    cells = 12
    rng = random.Random(SEED + 51)
    tone = [[0.78 + 0.22 * rng.random() for _ in range(cells)] for _ in range(cells)]
    stripe = [[rng.random() < 0.5 for _ in range(cells)] for _ in range(cells)]
    for y in range(size):
        v = y / size
        for x in range(size):
            u = x / size
            wu = u + 0.025 * (fbm2p(u, v, 4, 2, 1.0) - 0.5)
            wv = v + 0.025 * (fbm2p(u, v, 4, 2, 9.0) - 0.5)
            gx = (wu * cells) % cells
            gy = (wv * cells) % cells
            ix = int(gx)
            iy = int(gy)
            fx = gx - ix
            fy = gy - iy
            hedge = min(fx, 1.0 - fx, fy, 1.0 - fy)
            lum = tone[iy % cells][ix % cells]
            ph = fx if stripe[iy % cells][ix % cells] else fy
            lum *= 0.96 + 0.04 * math.cos(ph * math.pi * 2.0 * 6.0)
            if hedge < 0.03:
                lum *= 0.86
            wood = fbm2p(u, v, 6, 4, 3.0)
            if wood > 0.58:
                lum = lum * 0.55 * (0.85 + 0.3 * fbm2p(u, v, 48, 1, 4.0))
            river_y = 0.35 + 0.08 * math.sin(u * 2.0 * math.pi * 2.0) + 0.05 * (fbm2p(u, 0.0, 8, 2, 2.0) - 0.5)
            dv = abs(((v - river_y + 0.5) % 1.0) - 0.5)
            river_x = 0.7 + 0.06 * math.sin(v * 2.0 * math.pi * 3.0)
            du = abs(((u - river_x + 0.5) % 1.0) - 0.5)
            r = g = b = lum
            if dv < 0.008 or du < 0.006:
                r, g, b = 0.42, 0.50, 0.58
            img.set(x, y, (to_byte(r), to_byte(g), to_byte(b), 255))
    return img


def patch_cloud(size):
    print("patch_cloud %d" % size)
    img = Image(size, size)
    vals = []
    for y in range(size):
        for x in range(size):
            vals.append(fbm2p(x / size, y / size, 4, 6, 13.0))
    srt = sorted(vals)
    t = srt[int(len(srt) * 0.55)]
    for y in range(size):
        for x in range(size):
            n = vals[y * size + x]
            a = sstep(t - 0.04, t + 0.12, n)
            g = 228 + int(27 * sstep(t, t + 0.2, n))
            img.set(x, y, (g, g, min(255, g + 3), to_byte(a)))
    return img


def cells():
    rng = random.Random(SEED + 61)
    top = Image(32, 32)
    for y in range(32):
        for x in range(32):
            v = 0.80 + 0.2 * rng.random()
            if rng.random() < 0.06:
                v *= 0.82
            top.set(x, y, (to_byte(v), to_byte(v), to_byte(v), 255))
    side = Image(32, 64)
    for y in range(64):
        for x in range(32):
            n = rng.random()
            fringe = 4 + (1 if (x * 7) % 5 == 0 else 0) + (1 if (x * 3) % 7 == 0 else 0)
            if y < fringe:
                col = (98 + 30 * n, 150 + 30 * n, 62 + 18 * n)
            elif y < 30:
                col = (122 + 24 * n, 86 + 18 * n, 56 + 14 * n)
                if rng.random() < 0.04:
                    col = (150, 140, 130)
            else:
                col = (112 + 26 * n, 112 + 26 * n, 114 + 26 * n)
                if (y - 30) % 9 == 0 and n < 0.6:
                    col = (84, 84, 88)
            side.set(x, y, (col[0], col[1], col[2], 255))
    bottom = Image(32, 32)
    for y in range(32):
        for x in range(32):
            n = rng.random()
            col = (70 + 20 * n, 54 + 14 * n, 40 + 10 * n)
            bottom.set(x, y, (col[0], col[1], col[2], 255))
    for k in range(9):
        x0 = rng.randrange(32)
        y0 = rng.randrange(32)
        for s in range(12):
            bottom.set((x0 + s) % 32, (y0 + (s * (k % 3 - 1))) % 32, (150, 120, 84, 255))
    return top, side, bottom


# --------------------------------------------------------------------------------------------------------- validate

EQUIRECT = ("planet_day", "planet_class", "planet_night", "clouds", "space", "moon")
GLOWS = ("sun_core", "sun_corona", "limb_glow", "star_spike", "rune_ring")
EXPECTED = {
    "planet_day_256.png": (256, 128), "planet_day_512.png": (512, 256), "planet_day_1024.png": (1024, 512),
    "planet_class_256.png": (256, 128), "planet_class_512.png": (512, 256), "planet_class_1024.png": (1024, 512),
    "planet_night_256.png": (256, 128), "planet_night_512.png": (512, 256),
    "clouds_256.png": (256, 128), "clouds_512.png": (512, 256), "clouds_1024.png": (1024, 512),
    "space_256.png": (256, 128), "space_512.png": (512, 256), "space_1024.png": (1024, 512),
    "moon_128.png": (128, 64), "moon_256.png": (256, 128),
    "sun_core.png": (128, 128), "sun_corona.png": (256, 256), "limb_glow.png": (256, 256), "star_spike.png": (128, 128),
    "atmo.png": (32, 32), "plasma.png": (64, 128), "trail.png": (128, 32), "beam.png": (32, 128),
    "star_line.png": (64, 8), "rune_ring.png": (256, 256),
    "patch_land_256.png": (256, 256), "patch_land_512.png": (512, 512),
    "patch_cloud_256.png": (256, 256), "patch_cloud_512.png": (512, 512),
    "cell_top.png": (32, 32), "cell_side.png": (32, 64), "cell_bottom.png": (32, 32),
}


def validate():
    problems = []
    names = {n for n, _ in WRITTEN}
    for name, size in EXPECTED.items():
        if name not in names:
            problems.append("missing " + name)
    total = 0
    for name, img in WRITTEN:
        path = os.path.join(OUT, name)
        total += os.path.getsize(path)
        if os.path.exists(path + ".mcmeta"):
            total += os.path.getsize(path + ".mcmeta")
        if name in EXPECTED and (img.width, img.height) != EXPECTED[name]:
            problems.append("%s is %dx%d" % (name, img.width, img.height))
        magenta = 0
        for p in img.px:
            if p[3] > 0 and p[0] > 200 and p[2] > 200 and p[1] < 60:
                magenta += 1
        if magenta:
            problems.append("%s has %d magenta pixels" % (name, magenta))
        base = name.rsplit("_", 1)[0]
        if base in EQUIRECT:
            # seamless: the first and last columns differ no more than neighbouring columns do elsewhere
            w = img.width
            diff = [0.0, 0.0, 0.0, 0.0]
            typical = [0.0, 0.0, 0.0, 0.0]
            for y in range(img.height):
                a = img.get(0, y)
                b = img.get(w - 1, y)
                m0 = img.get(w // 2 - 1, y)
                m1 = img.get(w // 2, y)
                q0 = img.get(w // 4 - 1, y)
                q1 = img.get(w // 4, y)
                for c in range(4):
                    diff[c] += abs(a[c] - b[c])
                    typical[c] += 0.5 * (abs(m0[c] - m1[c]) + abs(q0[c] - q1[c]))
            diff = [d / img.height for d in diff]
            typical = [t / img.height for t in typical]
            if base != "planet_class" and any(diff[c] > max(2.0, 1.6 * typical[c]) for c in range(4)):
                problems.append("%s seam differs by %s (typical %s)" % (name, ["%.2f" % d for d in diff], ["%.2f" % t for t in typical]))
            if base == "planet_class":
                if diff[0] > 4.0:
                    problems.append("%s seam class differs by %.2f" % (name, diff[0]))
                for p in img.px:
                    if p[0] % 16 != 0 or p[0] > 160:
                        problems.append("%s has a blended class %d" % (name, p[0]))
                        break
        if name.rsplit(".", 1)[0] in GLOWS:
            worst = 0
            border = [(x, 0) for x in range(img.width)] + [(x, img.height - 1) for x in range(img.width)]
            border += [(0, y) for y in range(img.height)] + [(img.width - 1, y) for y in range(img.height)]
            for x, y in border:
                p = img.get(x, y)
                worst = max(worst, max(p[0], p[1], p[2]) * p[3] // 255)
            if worst > 2:
                problems.append("%s border glow %d" % (name, worst))
    print("total %.2f MB in %d files" % (total / 1024.0 / 1024.0, len(WRITTEN)))
    if total > MAX_TOTAL_BYTES:
        problems.append("total %d bytes is over the 8 MB budget" % total)
    for p in problems:
        print("PROBLEM:", p)
    return not problems


def main():
    os.makedirs(OUT, exist_ok=True)
    day, cls, classes, elev, sea = planet_maps(1024, 512)
    save_chain(day, "planet_day", (1024, 512, 256))
    save_chain(cls, "planet_class", (1024, 512, 256), keep_class=True)
    night = night_map(512, 256, classes, elev, sea, 1024)
    save_chain(night, "planet_night", (512, 256))
    save_chain(clouds_map(1024, 512), "clouds", (1024, 512, 256))
    save_chain(space_map(1024, 512), "space", (1024, 512, 256))
    save_chain(moon_map(256, 128), "moon", (256, 128))
    save(sun_core(), "sun_core.png", clamp_edges=True)
    save(sun_corona(), "sun_corona.png", clamp_edges=True)
    save(limb_glow(), "limb_glow.png", clamp_edges=True)
    save(star_spike(), "star_spike.png", clamp_edges=True)
    save(rune_ring(), "rune_ring.png", clamp_edges=True)
    save(flat_white(), "atmo.png")
    save(plasma(), "plasma.png")
    save(trail(), "trail.png")
    save(beam(), "beam.png")
    save(star_line(), "star_line.png")
    save_chain(patch_land(512), "patch_land", (512, 256))
    save_chain(patch_cloud(512), "patch_cloud", (512, 256))
    top, side, bottom = cells()
    save(top, "cell_top.png", blur=False)
    save(side, "cell_side.png", blur=False)
    save(bottom, "cell_bottom.png", blur=False)
    ok = validate()
    print("OK" if ok else "FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

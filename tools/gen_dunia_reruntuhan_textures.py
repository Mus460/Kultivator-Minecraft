"""Module R of DESIGN 34: the 3D creature parts, item icons and skins (everything ours: cuboid item models and procedural
textures, nothing downloaded).

Writes (module R's own folders only):
  assets/kultivator/models/item/dunia/reruntuhan/<part>.json   one cuboid model per creature part (demon kings, floor
                                                                guardians); drawn by client.dunia.reruntuhan.RigRenderer
  assets/kultivator/items/dunia/reruntuhan/<part>.json         their item definitions (kultivator:dunia/reruntuhan/<part>)
  assets/kultivator/textures/item/dunia/reruntuhan/*.png       part textures (32 x 32, the items atlas)
  assets/kultivator/textures/entity/dunia/reruntuhan/*.png     the lesser demons' player skins (64 x 64)
  assets/kultivator/textures/item/<id>.png + models/item/<id>.json + items/<id>.json   the three item icons
  assets/kultivator/textures/dunia/reruntuhan/*.png             the world FX sprites (telegraph ring, crack, sigil, glow,
                                                                cloud, gate curtain, talisman paper)

Model rules (the same as tools/gen_fire_ultimate_models.py, checked by validate()): the anchor (8, 8, 8) is the part's
joint (hip, shoulder, neck root, body centre); the front faces north (-z), up is +y; element coordinates within -16..32,
uv within 0..16, an element rotation is one axis by -45 / -22.5 / 22.5 / 45; at most 40 elements per model.

Run: python tools/gen_dunia_reruntuhan_textures.py [preview_dir]   (preview_dir: 8x previews of every texture)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator")
TEX_DIR = os.path.join(ROOT, "textures", "item", "dunia", "reruntuhan")
SKIN_DIR = os.path.join(ROOT, "textures", "entity", "dunia", "reruntuhan")
MODEL_DIR = os.path.join(ROOT, "models", "item", "dunia", "reruntuhan")
ITEM_DIR = os.path.join(ROOT, "items", "dunia", "reruntuhan")
NS = "kultivator"
TEX_PREFIX = "kultivator:item/dunia/reruntuhan/"

TEXTURES = {}
MODELS = {}
SIDES = ("north", "south", "east", "west", "up", "down")


# ================================================================================================ colour and noise

def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def clamp01(v):
    return max(0.0, min(1.0, v))


class Noise:
    def __init__(self, seed):
        r = random.Random(seed)
        self.p = list(range(256))
        r.shuffle(self.p)
        self.p += self.p
        self.g = [r.random() for _ in range(256)]

    def lattice(self, x, y):
        return self.g[self.p[(self.p[x & 255] + y) & 255]]

    def smooth(self, x, y):
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.lattice(x0, y0) + (self.lattice(x0 + 1, y0) - self.lattice(x0, y0)) * sx
        b = self.lattice(x0, y0 + 1) + (self.lattice(x0 + 1, y0 + 1) - self.lattice(x0, y0 + 1)) * sx
        return a + (b - a) * sy

    def fbm(self, x, y, octaves=3):
        total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            total += self.smooth(x * f, y * f) * amp
            norm += amp
            amp *= 0.5
            f *= 2
        return total / norm


def tex(name, fn, size=32):
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            c = fn((x + 0.5) / size, (y + 0.5) / size, x, y)
            img.set(x, y, (c[0], c[1], c[2], int(max(0, min(255, round(c[3] if len(c) > 3 else 255))))))
    TEXTURES[name] = img
    return name


# ================================================================================================ texture kinds

def fur(name, base, dark, light, seed, stripes=0, stripe_col=None, flow=1.0):
    n = Noise(seed)
    b, d, l = rgb(base), rgb(dark), rgb(light)
    s = rgb(stripe_col) if stripe_col else d

    def fn(u, v, x, y):
        strand = n.fbm(u * 10.0, v * 3.0 * flow, 3)
        c = mix(d, b, 0.4 + 0.8 * strand)
        c = mix(c, l, max(0.0, n.fbm(u * 4.0 + 7, v * 4.0) - 0.55) * 1.6)
        if stripes:
            w = math.sin((u * stripes + n.fbm(u * 3, v * 3) * 1.5) * math.pi * 2)
            if w > 0.55:
                c = mix(c, s, 0.85)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def scales(name, base, edge, glint, seed, size=6):
    n = Noise(seed)
    b, e, g = rgb(base), rgb(edge), rgb(glint)

    def fn(u, v, x, y):
        row = int(v * size)
        off = 0.5 if row % 2 else 0.0
        cu = (u * size + off) % 1.0
        cv = (v * size) % 1.0
        d = math.hypot(cu - 0.5, cv * 1.2 - 0.2)
        c = mix(b, e, clamp01((d - 0.35) * 4.0))
        c = mix(c, g, clamp01(0.35 - d) * 1.2 * n.fbm(u * 8, v * 8))
        c = mix(c, (0, 0, 0), 0.12 * (n.fbm(u * 6, v * 6) - 0.4))
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def stone(name, base, dark, seed, moss=None, cracks=True):
    n = Noise(seed)
    b, d = rgb(base), rgb(dark)
    m = rgb(moss) if moss else None

    def fn(u, v, x, y):
        k = n.fbm(u * 5, v * 5, 4)
        c = mix(d, b, 0.3 + 0.9 * k)
        if cracks:
            crack = abs(n.fbm(u * 3 + 11, v * 3) - 0.5)
            if crack < 0.025:
                c = mix(c, (20, 18, 16), 0.7)
        if m and n.fbm(u * 4 + 3, v * 4 + 5) > 0.62:
            c = mix(c, m, 0.75)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def bronze(name, seed, base="8A5A2A", patina="4A9A7A"):
    n = Noise(seed)
    b, p = rgb(base), rgb(patina)

    def fn(u, v, x, y):
        k = n.fbm(u * 6, v * 6, 4)
        c = mix(b, rgb("C08A4A"), clamp01(k * 1.3 - 0.2))
        if n.fbm(u * 3 + 4, v * 3 + 9) > 0.58:
            c = mix(c, p, 0.8)
        # engraved taotie-like ridges
        if (x + y) % 8 == 0 or (x - y) % 8 == 0:
            c = mix(c, (40, 26, 14), 0.35)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def metal(name, base, light, seed, rivets=True):
    n = Noise(seed)
    b, l = rgb(base), rgb(light)

    def fn(u, v, x, y):
        k = n.fbm(u * 2, v * 8, 3)
        c = mix(b, l, clamp01(k * 1.4 - 0.3 + (1.0 - v) * 0.25))
        if rivets and (x % 8 in (3, 4)) and (y % 8 in (3, 4)):
            c = mix(c, (230, 230, 240), 0.6)
        if y % 16 == 0:
            c = mix(c, (30, 30, 36), 0.5)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def glow(name, core, rim):
    c0, c1 = rgb(core), rgb(rim)

    def fn(u, v, x, y):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        c = mix(c0, c1, clamp01(d))
        return (c[0], c[1], c[2], 255)
    return tex(name, fn, 16)


def solid(name, colour, grain=0.1, seed=1):
    n = Noise(seed)
    c0 = rgb(colour)

    def fn(u, v, x, y):
        c = mix(c0, (255, 255, 255), grain * (n.fbm(u * 6, v * 6) - 0.5))
        return (c[0], c[1], c[2], 255)
    return tex(name, fn, 16)


def horn(name, base, tip, seed):
    n = Noise(seed)
    b, t = rgb(base), rgb(tip)

    def fn(u, v, x, y):
        c = mix(b, t, v)
        if (y + int(n.fbm(u * 4, v * 4) * 3)) % 5 == 0:
            c = mix(c, (60, 50, 40), 0.25)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn, 16)


def feathers(name, base, tip, seed, tip_frac=0.25):
    n = Noise(seed)
    b, t = rgb(base), rgb(tip)

    def fn(u, v, x, y):
        barb = 0.5 + 0.5 * math.sin((u * 10.0 + v * 4.0) * math.pi)
        c = mix(b, mix(b, (255, 255, 255), 0.2), barb * 0.5)
        c = mix(c, (0, 0, 0), 0.1 * (n.fbm(u * 6, v * 6) - 0.4))
        if v > 1.0 - tip_frac:
            c = mix(c, t, (v - (1.0 - tip_frac)) / tip_frac)
        if int(u * 8) != int((u + 0.03) * 8):
            c = mix(c, (0, 0, 0), 0.2)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def flame(name, hot, mid, cool, seed, alpha=230):
    n = Noise(seed)
    a, b, c_ = rgb(hot), rgb(mid), rgb(cool)

    def fn(u, v, x, y):
        t = 1.0 - v
        k = n.fbm(u * 4, v * 3 - 0.0, 3)
        heat = clamp01(1.1 - t - abs(u - 0.5) * 0.8 + (k - 0.5) * 0.6)
        col = mix(c_, b, clamp01(heat * 1.6)) if heat < 0.6 else mix(b, a, clamp01((heat - 0.6) * 2.5))
        return (col[0], col[1], col[2], alpha * clamp01(heat * 2.2 + 0.25))
    return tex(name, fn)


def jade(name, base, light, seed):
    n = Noise(seed)
    b, l = rgb(base), rgb(light)

    def fn(u, v, x, y):
        k = n.fbm(u * 3, v * 3, 4)
        vein = abs(n.fbm(u * 2 + 5, v * 6) - 0.5)
        c = mix(b, l, clamp01(k * 1.2 - 0.1))
        if vein < 0.04:
            c = mix(c, (240, 255, 240), 0.5)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def shadow(name, seed, base="1A1028", vein="8A5AE0"):
    n = Noise(seed)
    b, vcol = rgb(base), rgb(vein)

    def fn(u, v, x, y):
        k = n.fbm(u * 4, v * 4, 4)
        c = mix(b, mix(b, (0, 0, 0), 0.5), k)
        vein = abs(n.fbm(u * 3 + 2, v * 3 + 7) - 0.5)
        if vein < 0.03:
            c = mix(c, vcol, 0.9)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


def cloth(name, base, trim, seed):
    n = Noise(seed)
    b, t = rgb(base), rgb(trim)

    def fn(u, v, x, y):
        c = mix(b, (0, 0, 0), 0.15 * (n.fbm(u * 8, v * 2) - 0.3))
        if v > 0.82 or v < 0.08:
            c = t
        if 0.84 < v < 0.9 and (x % 4 == 0):
            c = mix(t, (255, 255, 255), 0.4)
        return (c[0], c[1], c[2], 255)
    return tex(name, fn)


# ================================================================================================ the model DSL

class Model:
    def __init__(self, mid):
        self.id = mid
        self.els = []
        self.tex = {}
        MODELS[mid] = self

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return self.tex[name]

    def box(self, f, t, texname, rot=None, glow=0, faces=SIDES, uv=None):
        f2 = [round(min(f[i], t[i]), 4) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
        e = {"from": f2, "to": t2, "faces": {}}
        for side in faces:
            e["faces"][side] = {"uv": list(uv or (0, 0, 16, 16)), "texture": "#" + self.key(texname)}
        if rot:
            axis, angle, origin = rot
            if angle:
                e["rotation"] = {"origin": [round(x, 4) for x in origin], "axis": axis, "angle": angle}
        if glow:
            e["light_emission"] = int(glow)
        self.els.append(e)
        return e

    def cube(self, c, size, texname, rot=None, glow=0):
        f = [c[i] - size[i] / 2.0 for i in range(3)]
        t = [c[i] + size[i] / 2.0 for i in range(3)]
        return self.box(f, t, texname, rot=rot, glow=glow)


def validate(m):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements (max 40)" % len(m.els))
    for i, e in enumerate(m.els):
        for k in ("from", "to"):
            for v in e[k]:
                if v < -16.0 or v > 32.0:
                    problems.append("element %d %s %s out of -16..32" % (i, k, e[k]))
        for face in e["faces"].values():
            if any(x < 0 or x > 16 for x in face["uv"]):
                problems.append("element %d uv %s" % (i, face["uv"]))
        rot = e.get("rotation")
        if rot and rot["angle"] not in (-45.0, -22.5, 0.0, 22.5, 45.0, -45, 45):
            problems.append("element %d angle %s" % (i, rot["angle"]))
    for name in m.tex:
        if name not in TEXTURES:
            problems.append("texture %s missing" % name)
    return problems


# ================================================================================================ textures

def build_textures():
    # demon kings
    scales("xl_scale", "2E5A2A", "16301A", "9AE070", 701)
    scales("xl_belly", "8A9A50", "4A5A2A", "D8E8A0", 702, size=4)
    glow("xl_eye", "FFF6A0", "E0A020")
    solid("xl_fang", "F0E8D0", 0.06, 703)
    solid("xl_mouth", "6A1018", 0.1, 704)
    fur("bt_hide", "3A2418", "1A0E08", "6A4A32", 711)
    horn("bt_horn", "D8CCB0", "403830", 712)
    metal("bt_iron", "3A3A44", "8A8A98", 713)
    cloth("bt_cloth", "7A1010", "D8A020", 714)
    glow("bt_eye", "FF8040", "C02000")
    solid("bt_skin", "5A3020", 0.12, 715)
    bronze("cy_bronze", 721)
    metal("cy_iron", "2A2A30", "6A6A78", 722, rivets=False)
    cloth("cy_cloth", "3A2A18", "A08030", 723)
    fur("cy_skin", "6A3A22", "3A1A0A", "8A5A3A", 724)
    glow("cy_eye", "FF4020", "801000")
    horn("cy_horn", "C0B090", "2A2018", 725)
    cloth("cy_banner", "8A1A10", "1A1A1A", 726)
    # floor guardians
    metal("lb_iron", "4A4E58", "9AA0B0", 731)
    horn("lb_horn", "B8B0A0", "3A3430", 732)
    glow("lb_eye", "FFD040", "C08000")
    solid("lb_ring", "D8A020", 0.1, 733)
    # the guardians' temple regalia (detail pass): saddle cloths, a ribbon collar and the bronze bells
    cloth("gd_saddle_red", "8A1A1A", "E8B030", 735)
    cloth("gd_saddle_violet", "3A1A5A", "C0A0FF", 736)
    cloth("gd_ribbon", "E890C0", "FFFFFF", 738)
    bronze("gd_bell", 737)
    jade("ug_jade", "2A8A5A", "9AF0C0", 741)
    jade("ug_belly", "C8E8B0", "F0FFF0", 742)
    glow("ug_eye", "FFF080", "40C060")
    feathers("ba_feather", "E04A1A", "FFD040", 751)
    flame("ba_flame", "FFF6C0", "FF8A2A", "C0200A", 752)
    horn("ba_beak", "FFD060", "8A4A10", 753)
    solid("ba_leg", "3A2A20", 0.1, 754)
    stone("kb_shell", "7A6A50", "3A3020", 761, moss="4A6A2A")
    stone("kb_skin", "8A8070", "4A4438", 762, cracks=False)
    glow("kb_eye", "B0FFB0", "208020")
    fur("hp_fur", "D8B040", "6A4A10", "FFF0B0", 771, stripes=4, stripe_col="2A1A30")
    glow("hp_eye", "C0E0FF", "4060FF")
    solid("hp_spark", "B57CFF", 0.2, 772)
    fur("ri_fur", "F8E8F8", "B8A0C8", "FFFFFF", 781)
    fur("ri_tail", "FFD0F0", "E890D0", "FFFFFF", 782, flow=0.5)
    glow("ri_eye", "FF80D0", "8020A0")
    scales("kl_scale", "D8A020", "8A5A10", "FFF0A0", 791)
    fur("kl_mane", "FF7A2A", "A02A0A", "FFD060", 792, flow=0.6)
    horn("kl_horn", "FFF0C0", "C08020", 793)
    glow("kl_eye", "FFFFFF", "FFB020")
    feathers("bb_feather", "F4F8FF", "1A1A22", 801, tip_frac=0.18)
    solid("bb_crown", "D81A1A", 0.08, 802)
    horn("bb_beak", "E8D8A0", "6A5A30", 803)
    solid("bb_leg", "2A2A2A", 0.1, 804)
    shadow("nb_scale", 811)
    shadow("nb_belly", 812, base="2A1838", vein="C08AFF")
    glow("nb_eye", "E0C0FF", "6A20C0")
    horn("nb_horn", "4A3A5A", "0A0810", 813)
    solid("nb_claw", "D8D0E8", 0.1, 814)


# ================================================================================================ demon kings

def xiangliu():
    """Xiangliu: the coiled serpent body (anchor = the coil's centre at ground + 8), one neck segment and one head that
    the rig repeats nine times."""
    m = Model("xiangliu_coil")
    # three stacked rings of body segments, eight around, shrinking upward
    for ring, (r, y, s) in enumerate(((14.0, 0.0, 9.0), (10.0, 7.0, 8.0), (6.0, 13.0, 7.0))):
        for k in range(8):
            a = k * math.pi / 4.0 + ring * math.pi / 8.0
            cx = 8 + r * math.cos(a)
            cz = 8 + r * math.sin(a)
            rot = ("y", 45.0 if k % 2 else 0.0, [cx, y, cz]) if k % 2 else None
            m.cube((cx, y - 4 + s / 2, cz), (s, s, s), "xl_scale" if (k + ring) % 3 else "xl_belly", rot=rot)
    m.cube((8, 16, 8), (8, 8, 8), "xl_scale")
    m.cube((8, 8, 8), (12, 4, 12), "xl_belly")
    n = Model("xiangliu_neck")
    # a neck rising from the anchor (the root) to y + 24, thinner toward the top, belly scales in front
    for i, (y0, w) in enumerate(((8, 6.0), (14, 5.4), (20, 4.8), (26, 4.2))):
        n.box([8 - w / 2, y0, 8 - w / 2], [8 + w / 2, y0 + 6.0, 8 + w / 2], "xl_scale")
        n.box([8 - w / 2 + 0.6, y0 + 0.4, 8 - w / 2 - 0.3], [8 + w / 2 - 0.6, y0 + 5.8, 8 - w / 2], "xl_belly", faces=("north",))
    h = Model("xiangliu_head")
    # a viper head, the anchor at the back of the skull; jaws open forward (-z)
    h.box([4, 5, -6], [12, 11, 9], "xl_scale")
    h.box([5, 4, -10], [11, 8, -5], "xl_scale")
    h.box([5, 2.5, -10], [11, 4, 2], "xl_belly")
    h.box([5.2, 3.6, -9.6], [10.8, 4.4, 1.0], "xl_mouth")
    for x in (5.6, 10.4):
        h.cube((x, 3.2, -9.0), (0.6, 1.6, 0.6), "xl_fang")
        h.cube((x, 9.6, -3.5), (1.4, 1.2, 1.4), "xl_eye", glow=15)
    h.box([6.5, 11, -4], [9.5, 13, 6], "xl_scale", rot=("x", 22.5, [8, 11, 1]))
    h.box([3, 7, 0], [4, 10, 6], "xl_scale", rot=("y", -22.5, [4, 8, 3]))
    h.box([12, 7, 0], [13, 10, 6], "xl_scale", rot=("y", 22.5, [12, 8, 3]))


def banteng():
    """Raja Banteng Iblis (the Bull Demon King): a bull-headed giant in iron armour with an iron club."""
    t = Model("banteng_torso")
    # anchor = the waist (hips); the torso rises to y + 24 (the shoulders)
    t.box([0, 8, 2], [16, 22, 14], "bt_skin")
    t.box([-1, 18, 1], [17, 28, 15], "bt_iron")
    t.box([2, 8, 1], [14, 18, 2], "bt_iron")
    t.box([3, 4, 3], [13, 10, 13], "bt_cloth")
    t.box([2, -4, 1], [14, 6, 3], "bt_cloth", rot=("x", -22.5, [8, 6, 2]))
    t.box([6, 22, 0], [10, 26, 1], "lb_ring")
    for x in (-3, 15):
        t.box([x, 24, 2], [x + 4, 30, 14], "bt_iron")
        t.box([x - 0.5, 29, 4], [x + 4.5, 31, 12], "bt_iron", rot=("z", -22.5 if x < 0 else 22.5, [x + 2, 30, 8]))
    t.box([4, 26, 13], [12, 30, 17], "bt_cloth")
    hd = Model("banteng_head")
    # anchor = the neck; the bull head looks forward (-z)
    hd.box([3, 6, 0], [13, 18, 12], "bt_hide")
    hd.box([4, 6, -6], [12, 13, 1], "bt_hide")
    hd.box([4.5, 6.5, -7], [11.5, 10, -5.5], "bt_skin")
    hd.cube((8, 8.2, -7.3), (3, 3, 0.6), "lb_ring")
    for x in (4.2, 11.8):
        hd.cube((x, 14.5, -0.4), (1.2, 1.2, 0.6), "bt_eye", glow=15)
    for side in (-1, 1):
        x0 = 13 if side > 0 else 3
        hd.box([x0 - (0 if side > 0 else 6), 14, 3], [x0 + (6 if side > 0 else 0), 17, 6], "bt_horn")
        tip_x = x0 + 6 * side
        hd.box([tip_x - 1.5, 15, 2], [tip_x + 1.5, 26, 5], "bt_horn", rot=("z", -22.5 * side, [tip_x, 15, 3.5]))
    hd.box([4, 17, 2], [12, 20, 10], "bt_hide")
    a = Model("banteng_arm")
    # anchor = the shoulder; the arm hangs down to y - 18 with a spiked iron bracer and a fist
    a.box([3, -6, 3], [13, 10, 13], "bt_skin")
    a.box([2, 6, 2], [14, 12, 14], "bt_iron")
    a.box([3.5, -14, 3.5], [12.5, -4, 12.5], "bt_iron")
    a.box([3, -16, 3], [13, -12, 13], "bt_skin")
    for k in range(4):
        ang = k * math.pi / 2
        a.cube((8 + 5.2 * math.cos(ang), -9, 8 + 5.2 * math.sin(ang)), (1.4, 1.4, 1.4), "lb_ring")
    lg = Model("banteng_leg")
    # anchor = the hip; a thick leg down to y - 24 ending in a cloven hoof
    lg.box([3, -6, 3], [13, 8, 13], "bt_cloth")
    lg.box([3.5, -14, 3.5], [12.5, -5, 12.5], "bt_hide")
    lg.box([3, -11, 2.5], [13, -7, 4], "bt_iron")
    lg.box([3, -16, 2], [7.6, -13, 13], "bt_horn")
    lg.box([8.4, -16, 2], [13, -13, 13], "bt_horn")
    c = Model("banteng_club")
    # anchor = the grip; the club points down (-y) to a spiked head
    c.box([7, -4, 7], [9, 14, 9], "bt_iron")
    c.box([5, -16, 5], [11, -4, 11], "bt_iron")
    c.box([4.5, -15, 4.5], [11.5, -13, 11.5], "lb_ring")
    for k in range(6):
        y = -14 + (k // 3) * 6
        ang = k * math.tau / 3
        c.cube((8 + 3.6 * math.cos(ang), y, 8 + 3.6 * math.sin(ang)), (1.6, 1.6, 1.6), "bt_horn", rot=("y", 45.0, [8, y, 8]))
    c.cube((8, 14.5, 8), (3, 1.5, 3), "lb_ring")


def chiyou():
    """Chiyou: the bronze war god of the Shan Hai Jing's battle with the Yellow Emperor: a bronze-masked horned head,
    four arms with ge halberds, a war banner on the back."""
    t = Model("chiyou_torso")
    t.box([1, 6, 3], [15, 24, 13], "cy_skin")
    t.box([0, 12, 2], [16, 26, 14], "cy_bronze")
    t.box([2, 4, 2], [14, 10, 14], "cy_cloth")
    t.box([1, -6, 1], [15, 6, 3], "cy_cloth", rot=("x", -22.5, [8, 6, 2]))
    t.box([1, -6, 13], [15, 6, 15], "cy_cloth", rot=("x", 22.5, [8, 6, 14]))
    for x in (-2, 14):
        t.box([x, 22, 2], [x + 4, 28, 14], "cy_bronze")
    t.cube((8, 18, 1.6), (6, 6, 0.8), "cy_iron")
    t.cube((8, 18, 1.0), (2, 2, 0.6), "cy_eye", glow=15)
    hd = Model("chiyou_head")
    hd.box([3, 0, 2], [13, 12, 13], "cy_bronze")
    hd.box([2.5, 2, 1], [13.5, 10, 2.5], "cy_iron")
    for x in (5, 11):
        hd.cube((x, 7, 0.8), (2, 1.2, 0.6), "cy_eye", glow=15)
    hd.box([6, 2.5, 0.4], [10, 4.5, 1.2], "cy_iron")
    for side in (-1, 1):
        x = 8 + 5.5 * side
        hd.box([x - 1.2, 10, 5], [x + 1.2, 20, 8], "cy_horn", rot=("z", -22.5 * side, [x, 10, 6.5]))
        hd.box([x + 1.5 * side - 1, 17, 5.5], [x + 1.5 * side + 1, 24, 7.5], "cy_horn", rot=("z", -45.0 * side, [x, 18, 6.5]))
    hd.box([4, 12, 4], [12, 14, 12], "cy_bronze")
    hd.box([7, 14, 5], [9, 19, 11], "cy_skin")
    a = Model("chiyou_arm")
    a.box([4, -4, 4], [12, 10, 12], "cy_skin")
    a.box([3, 4, 3], [13, 11, 13], "cy_bronze")
    a.box([4.5, -13, 4.5], [11.5, -3, 11.5], "cy_iron")
    a.box([4, -16, 4], [12, -12, 12], "cy_skin")
    w = Model("chiyou_ge")
    # the ge (dagger-axe): a long shaft along -y (anchor = the grip), a bronze blade crossing at the top
    w.box([7.2, -16, 7.2], [8.8, 30, 8.8], "cy_iron")
    w.box([8, 24, 6.5], [20, 28, 9.5], "cy_bronze")
    w.box([18, 22, 7], [22, 29, 9], "cy_bronze", rot=("z", -22.5, [20, 25, 8]))
    w.box([4, 26, 7.4], [8, 27.5, 8.6], "cy_bronze")
    w.cube((8, -14.5, 8), (2.4, 1.5, 2.4), "cy_bronze")
    lg = Model("chiyou_leg")
    lg.box([3.5, -6, 3.5], [12.5, 8, 12.5], "cy_cloth")
    lg.box([4, -14, 4], [12, -5, 12], "cy_skin")
    lg.box([3.5, -11, 3], [12.5, -7, 4.5], "cy_bronze")
    lg.box([3, -16, 1], [13, -13, 13], "cy_iron")
    b = Model("chiyou_banner")
    b.box([7.4, -8, 7.4], [8.6, 32, 8.6], "cy_iron")
    b.box([8.6, 10, 7.8], [24, 30, 8.2], "cy_banner")
    b.box([5, 30, 5], [11, 32, 11], "cy_bronze")


# ================================================================================================ floor guardians

def quadruped(prefix, body_tex, belly_tex, head_fn, leg_tex, hoof_tex, tail_fn, body=(12, 12, 26), plates=None,
              saddle=None, collar=None, bell=None, cuffs=None):
    """A four-legged guardian: the body (anchor = its centre), a leg (anchor = the hip, down to y - 18), a head and a
    tail made by the given functions.

    Detail pass: the temple regalia that tells the guardians apart: a saddle cloth over the back with drapes down both
    flanks (`saddle`), a collar round the chest (`collar`) with a bronze bell under the throat (`bell`) and metal cuffs
    above the hooves (`cuffs`)."""
    m = Model(prefix + "_body")
    w, h, l = body
    m.box([8 - w / 2, 8 - h / 2, 8 - l / 2], [8 + w / 2, 8 + h / 2, 8 + l / 2], body_tex)
    m.box([8 - w / 2 + 1, 8 - h / 2 - 1, 8 - l / 2 + 3], [8 + w / 2 - 1, 8 - h / 2, 8 + l / 2 - 3], belly_tex)
    m.box([8 - w / 2 - 0.5, 8 - h / 2 + 2, 8 - l / 2 - 1], [8 + w / 2 + 0.5, 8 + h / 2 + 1.5, 8 - l / 2 + 8], body_tex)
    if plates:
        for k in range(4):
            z = 8 - l / 2 + 3 + k * (l - 6) / 4
            m.box([8 - w / 2 - 0.6, 8 + h / 2 - 1, z], [8 + w / 2 + 0.6, 8 + h / 2 + 0.8, z + 3], plates)
    if saddle:
        top = 8 + h / 2 + (0.8 if plates else 0.0)
        m.box([8 - w / 2 - 0.4, top, 8 - l / 4], [8 + w / 2 + 0.4, top + 0.6, 8 + l / 4], saddle)
        for side in (-1, 1):
            x = 8 + side * (w / 2 + 0.4)
            m.box([x - 0.3, top - 5.0, 8 - l / 4 + 1], [x + 0.3, top, 8 + l / 4 - 1], saddle)
            m.cube((x, top - 5.6, 8), (0.8, 1.2, 0.8), bell or saddle)
    if collar:
        m.box([8 - w / 2 - 0.9, 8 - h / 2 + 1.5, 8 - l / 2 + 0.5], [8 + w / 2 + 0.9, 8 + h / 2 + 2.1, 8 - l / 2 + 2.5], collar)
    if bell:
        m.cube((8, 8 - h / 2 + 0.6, 8 - l / 2 - 0.4), (2.4, 2.6, 2.4), bell)
        m.cube((8, 8 - h / 2 - 0.9, 8 - l / 2 - 0.4), (0.8, 0.8, 0.8), "lb_ring")
    lg = Model(prefix + "_leg")
    lg.box([5, -6, 5], [11, 8, 11], leg_tex)
    lg.box([5.5, -14, 5.5], [10.5, -6, 10.5], leg_tex)
    lg.box([4.5, -16, 4], [11.5, -13, 11.5], hoof_tex)
    if cuffs:
        lg.box([5.1, -12.6, 5.1], [10.9, -11.2, 10.9], cuffs)
    hd = Model(prefix + "_head")
    head_fn(hd)
    tl = Model(prefix + "_tail")
    tail_fn(tl)


def lembu_besi():
    def head(h):
        h.box([3, 2, -6], [13, 14, 6], "lb_iron")
        h.box([4, 2, -10], [12, 9, -5], "lb_iron")
        h.cube((8, 5, -10.4), (3, 3, 0.8), "lb_ring")
        for x in (4, 12):
            h.cube((x, 11, -5.5), (1.2, 1.2, 0.6), "lb_eye", glow=15)
        for s in (-1, 1):
            x = 8 + 6 * s
            h.box([x - 1.5, 11, -2], [x + 1.5, 13, 2], "lb_horn")
            h.box([x + 1.5 * s - 1.2, 12, -2], [x + 1.5 * s + 1.2, 22, 1], "lb_horn", rot=("z", -22.5 * s, [x, 12, 0]))
            h.box([x - 2, 4, -1], [x + 2, 10, 1], "lb_iron", rot=("y", 22.5 * s, [x, 7, 0]))
    def tail(t):
        t.box([7, 2, 8], [9, 9, 10], "lb_iron", rot=("x", 22.5, [8, 8, 8]))
        t.box([6.5, -4, 9], [9.5, 2, 12], "lb_horn")
    quadruped("lembu_besi", "lb_iron", "lb_iron", head, "lb_iron", "lb_horn", tail, body=(16, 14, 28), plates="lb_ring",
              collar="lb_ring", bell="gd_bell", cuffs="lb_ring")


def kura_batu():
    def head(h):
        h.box([4, 3, -8], [12, 11, 4], "kb_skin")
        h.box([5, 3, -11], [11, 8, -7], "kb_skin")
        for x in (4.6, 11.4):
            h.cube((x, 8.5, -6), (1.0, 1.0, 1.0), "kb_eye", glow=15)
    def tail(t):
        t.box([6.5, 4, 8], [9.5, 7, 16], "kb_skin", rot=("x", -22.5, [8, 6, 8]))
    quadruped("kura_batu", "kb_skin", "kb_skin", head, "kb_skin", "kb_shell", tail, body=(18, 8, 22), bell="gd_bell")
    s = Model("kura_batu_shell")
    # the great domed shell over the body (anchor = the body centre)
    s.box([-4, 10, -6], [20, 14, 22], "kb_shell")
    s.box([-2, 14, -4], [18, 18, 20], "kb_shell")
    s.box([1, 18, -1], [15, 21, 17], "kb_shell")
    s.box([4, 21, 2], [12, 23, 14], "kb_shell")
    s.box([-5, 7, -7], [21, 10, 23], "kb_skin")
    for k in range(6):
        a = k * math.tau / 6
        s.cube((8 + 7 * math.cos(a), 19.5, 8 + 7 * math.sin(a)), (3, 3, 3), "kb_shell", rot=("y", 45.0, [8 + 7 * math.cos(a), 19.5, 8 + 7 * math.sin(a)]))


def harimau_petir():
    def head(h):
        h.box([3, 3, -5], [13, 13, 6], "hp_fur")
        h.box([4.5, 3, -9], [11.5, 9, -4], "hp_fur")
        h.box([5.5, 2, -9], [10.5, 3.5, -4.5], "xl_fang")
        for x in (4.6, 11.4):
            h.cube((x, 10, -5.2), (1.4, 1.0, 0.6), "hp_eye", glow=15)
        for x in (4, 12):
            h.box([x - 1.5, 12, 0], [x + 1.5, 15, 2], "hp_fur")
        h.box([2, 6, -2], [3, 12, 4], "hp_fur")
        h.box([13, 6, -2], [14, 12, 4], "hp_fur")
        h.cube((8, 14, -1), (2, 2, 2), "hp_spark", glow=12, rot=("y", 45.0, [8, 14, -1]))
    def tail(t):
        t.box([7, 7, 8], [9, 9, 20], "hp_fur", rot=("x", 22.5, [8, 8, 8]))
        t.box([6.5, 10, 18], [9.5, 13, 24], "hp_spark", glow=12, rot=("x", 45.0, [8, 11, 20]))
    quadruped("harimau_petir", "hp_fur", "ri_fur", head, "hp_fur", "hp_fur", tail, body=(12, 11, 26), saddle="gd_saddle_violet",
              collar="gd_saddle_violet", cuffs="gd_bell")


def rubah_ilusi():
    def head(h):
        h.box([4, 3, -4], [12, 11, 5], "ri_fur")
        h.box([5.5, 3, -9], [10.5, 7, -3], "ri_fur")
        h.cube((8, 5.5, -9.4), (1.6, 1.2, 0.8), "nb_claw")
        for x in (5, 11):
            h.cube((x, 8.8, -4.3), (1.4, 0.8, 0.6), "ri_eye", glow=15)
            h.box([x - 1.6, 11, -1], [x + 1.6, 17, 2], "ri_tail", rot=("z", 22.5 if x < 8 else -22.5, [x, 11, 0]))
    def tail(t):
        # one great tail (the rig fans three of them)
        t.box([6, 6, 8], [10, 10, 16], "ri_tail", rot=("x", 22.5, [8, 8, 8]))
        t.box([5, 9, 13], [11, 15, 22], "ri_tail", rot=("x", 45.0, [8, 12, 16]))
        t.box([6, 14, 18], [10, 18, 24], "ri_fur", rot=("x", 45.0, [8, 16, 21]))
    quadruped("rubah_ilusi", "ri_fur", "ri_fur", head, "ri_fur", "ri_tail", tail, body=(9, 8, 20), collar="gd_ribbon", bell="gd_bell")


def kilin_emas():
    def head(h):
        h.box([4, 3, -5], [12, 13, 6], "kl_scale")
        h.box([5, 3, -10], [11, 9, -4], "kl_scale")
        h.box([5, 1.5, -9], [11, 3, -4], "kl_mane")
        for x in (4.6, 11.4):
            h.cube((x, 10.5, -5.3), (1.4, 1.0, 0.6), "kl_eye", glow=15)
        h.box([7, 13, -2], [9, 24, 0], "kl_horn", rot=("x", -22.5, [8, 13, -1]))
        h.box([3, 6, 3], [13, 16, 9], "kl_mane")
        for s in (-1, 1):
            h.box([8 + 5 * s - 1, 8, -8], [8 + 5 * s + 1, 9, -2], "kl_mane", rot=("y", 22.5 * s, [8 + 5 * s, 8, -5]))
    def tail(t):
        t.box([6.5, 6, 8], [9.5, 9, 16], "kl_scale", rot=("x", 22.5, [8, 8, 8]))
        t.box([5, 7, 14], [11, 14, 22], "kl_mane", rot=("x", 45.0, [8, 10, 18]))
    quadruped("kilin_emas", "kl_scale", "kl_mane", head, "kl_scale", "kl_horn", tail, body=(12, 12, 24), plates="kl_mane",
              saddle="gd_saddle_red", collar="lb_ring", bell="gd_bell", cuffs="lb_ring")


def crane(prefix, feather, accent, beak, leg, flame_tex=None):
    """A crane guardian: body (anchor = its centre), neck+head (anchor = the base of the neck), one wing (anchor = the
    shoulder, spreading +x; the rig turns it 180 degrees for the other side), one leg (anchor = the hip), the tail."""
    b = Model(prefix + "_body")
    b.box([3, 3, -2], [13, 13, 16], feather)
    b.box([4, 2, 0], [12, 3, 14], feather)
    b.box([4, 10, -4], [12, 15, 4], feather)
    if flame_tex:
        b.box([2, 12, 2], [14, 18, 14], flame_tex, glow=12)
    n = Model(prefix + "_neck")
    n.box([6.5, 8, 6.5], [9.5, 22, 9.5], feather, rot=("x", -22.5, [8, 8, 8]))
    n.box([5.5, 21, -1], [10.5, 26, 6], feather)
    n.box([7, 22, -12], [9, 24, -1], beak)
    n.box([6, 25, 0], [10, 27, 4], accent, glow=8 if flame_tex else 0)
    for x in (5.4, 10.6):
        n.cube((x, 24.5, 1), (0.6, 1.0, 1.0), "kl_eye", glow=15)
    w = Model(prefix + "_wing")
    w.box([8, 6, 2], [24, 9, 14], feather)
    w.box([22, 6.5, 4], [32, 8.5, 16], feather, rot=("y", -22.5, [22, 7.5, 9]))
    w.box([8, 5, 12], [26, 7, 18], accent)
    if flame_tex:
        w.box([10, 8.6, 3], [30, 9.4, 15], flame_tex, glow=12)
    lg = Model(prefix + "_leg")
    lg.box([7.4, -14, 7.4], [8.6, 8, 8.6], leg)
    lg.box([6, -16, 5], [10, -14, 11], leg)
    t = Model(prefix + "_tail")
    t.box([5, 4, 8], [11, 8, 20], feather, rot=("x", -22.5, [8, 6, 8]))
    t.box([6, 6, 16], [10, 9, 26], accent, rot=("x", -45.0, [8, 7, 18]))
    if flame_tex:
        t.box([4, 6, 12], [12, 14, 26], flame_tex, glow=12, rot=("x", -22.5, [8, 10, 14]))


def ular_giok():
    h = Model("ular_giok_head")
    h.box([4, 4, -8], [12, 11, 6], "ug_jade")
    h.box([5, 3, -12], [11, 8, -7], "ug_jade")
    h.box([5, 2.5, -12], [11, 4, 4], "ug_belly")
    for x in (4.4, 11.6):
        h.cube((x, 9, -5), (1.0, 1.4, 1.4), "ug_eye", glow=15)
    h.box([7, 11, -6], [9, 13, 6], "ug_belly", rot=("x", -22.5, [8, 11, 0]))
    s = Model("ular_giok_seg")
    s.box([3, 3, 0], [13, 13, 16], "ug_jade")
    s.box([4, 2, 1], [12, 3, 15], "ug_belly")
    s.box([7, 13, 2], [9, 14.5, 14], "ug_belly")
    t = Model("ular_giok_tail")
    t.box([5, 5, 0], [11, 11, 12], "ug_jade")
    t.box([6.5, 6.5, 11], [9.5, 9.5, 22], "ug_jade")
    t.box([7.5, 7.5, 21], [8.5, 8.5, 28], "ug_eye", glow=10)


def naga_bayangan():
    h = Model("naga_bayangan_head")
    h.box([3, 3, -6], [13, 13, 8], "nb_scale")
    h.box([4, 2, -14], [12, 9, -5], "nb_scale")
    h.box([4.5, 0.5, -13], [11.5, 2, -4], "nb_belly")
    for x in (4.6, 11.4):
        h.cube((x, 11, -4), (1.4, 1.2, 1.2), "nb_eye", glow=15)
        h.box([x - 1, 12, 0], [x + 1, 24, 2], "nb_horn", rot=("x", -45.0, [x, 12, 1]))
    for s in (-1, 1):
        h.box([8 + 5 * s - 1, 4, -14], [8 + 5 * s + 1, 5, -2], "nb_horn", rot=("y", 22.5 * s, [8 + 5 * s, 4, -8]))
    h.box([7, 13, -2], [9, 15, 8], "nb_belly")
    s = Model("naga_bayangan_seg")
    s.box([2, 3, 0], [14, 13, 16], "nb_scale")
    s.box([3, 2, 1], [13, 3, 15], "nb_belly")
    s.box([7, 13, 2], [9, 17, 6], "nb_horn")
    s.box([7, 13, 9], [9, 16, 13], "nb_horn")
    c = Model("naga_bayangan_claw")
    c.box([6, -8, 6], [10, 8, 10], "nb_scale")
    for k in range(3):
        c.box([5 + k * 2.5, -11, 4], [6 + k * 2.5, -8, 7], "nb_claw")
    t = Model("naga_bayangan_tail")
    t.box([4, 4, 0], [12, 12, 14], "nb_scale")
    t.box([5.5, 5.5, 13], [10.5, 10.5, 24], "nb_scale")
    t.box([6, 7.5, 22], [10, 8.5, 30], "nb_eye", glow=10)


# ================================================================================================ item icons and skins

def icons():
    def icon(name, fn):
        img = Image(16, 16)
        for y in range(16):
            for x in range(16):
                c = fn(x, y)
                if c:
                    img.set(x, y, c)
        path = os.path.join(ROOT, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        write_json(os.path.join(ROOT, "models", "item", name + ".json"), {"parent": "minecraft:item/generated",
                                                                         "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        write_json(os.path.join(ROOT, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        ICONS.append((name, img))

    n = Noise(9001)

    def sobekan(x, y):
        # a torn yellow seal talisman with red cinnabar strokes and a ragged lower edge
        if not (4 <= x <= 11 and 1 <= y <= 14):
            return None
        ragged = 11 + int(n.fbm(x * 0.7, 3) * 4)
        if y > ragged:
            return None
        c = mix(rgb("E8D070"), rgb("C8A040"), n.fbm(x * 0.4, y * 0.4))
        if x in (4, 11) or y == 1:
            c = mix(c, rgb("8A6A20"), 0.6)
        if (x == 7 or x == 8) and 3 <= y <= ragged - 1:
            c = rgb("C0201A")
        if y in (4, 8) and 5 <= x <= 10:
            c = rgb("C0201A")
        if y == ragged:
            c = mix(c, rgb("6A4A10"), 0.5)
        return (int(c[0]), int(c[1]), int(c[2]), 255)

    def inti(x, y):
        # a black-red demon core with a burning heart and a rim of violet sparks
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 6.6:
            return None
        k = clamp01(1.0 - d / 6.6)
        c = mix(rgb("1A0408"), rgb("C0201A"), k ** 1.6)
        c = mix(c, rgb("FFC080"), clamp01(k - 0.75) * 3.0)
        if 5.6 < d <= 6.6:
            c = mix(rgb("6A2A8A"), rgb("2A0A18"), d - 5.6)
        if (x + y) % 5 == 0 and d < 5.0 and d > 3.0:
            c = mix(c, rgb("FF6A3A"), 0.5)
        return (int(c[0]), int(c[1]), int(c[2]), 255)

    def kitab(x, y):
        # an old formation book: indigo cover, gold bagua on the front, stitched spine
        if not (2 <= x <= 13 and 2 <= y <= 14):
            return None
        c = mix(rgb("2A2A5A"), rgb("1A1A3A"), n.fbm(x * 0.3, y * 0.3))
        if x <= 3:
            c = rgb("6A4A2A") if y % 3 else rgb("D8C090")
        if y in (2, 14) or x == 13:
            c = rgb("E8E0C8")
        d = math.hypot(x - 8.5, y - 8.0)
        if 2.6 <= d <= 3.6:
            c = rgb("FFD54A")
        if d < 1.2:
            c = rgb("FFFFFF") if x >= 8.5 else rgb("101010")
        return (int(c[0]), int(c[1]), int(c[2]), 255)

    icon("sobekan_segel", sobekan)
    icon("inti_iblis_kuno", inti)
    icon("kitab_formasi_kuno", kitab)


ICONS = []
SKINS = []


def demon_skin(index, body, dark, horn_c, eye):
    """A 64 x 64 player-format skin of a lesser demon: dark skin, a loincloth, claws, glowing eyes, ridged horns on the
    hat layer (front and top), bone bracers."""
    n = Noise(9100 + index)
    img = Image(64, 64)
    b, d, h, e = rgb(body), rgb(dark), rgb(horn_c), rgb(eye)

    def put(x, y, c, a=255):
        img.set(x, y, (int(c[0]), int(c[1]), int(c[2]), a))

    # base: fill the base layer areas (head 0..32 x 0..16, body/arms/legs 0..64 x 16..32, and the second-layer-free
    # lower left arm/leg areas 16..48 x 48..64)
    regions = [(0, 0, 32, 16), (0, 16, 56, 32), (16, 48, 48, 64)]
    for x0, y0, x1, y1 in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = n.fbm(x * 0.25, y * 0.25)
                put(x, y, mix(d, b, 0.35 + 0.8 * k))
    # face (front of head = 8..16 x 8..16): eyes, a fanged mouth, brow ridge
    for x in range(8, 16):
        put(x, 10, mix(d, (0, 0, 0), 0.3))
    for x in (9, 10, 13, 14):
        put(x, 11, e)
    for x in range(10, 14):
        put(x, 14, (30, 8, 8))
    put(10, 15, (240, 232, 210))
    put(13, 15, (240, 232, 210))
    # loincloth on the body front (20..28 x 26..32) and legs top
    for y in range(26, 32):
        for x in range(20, 28):
            put(x, y, mix(rgb("3A1A10"), rgb("1A0A08"), n.fbm(x * 0.5, y * 0.5)))
    # bone bracers on the right arm (44..48 x 26..28) and claws at the hands (44..48 x 31)
    for x in range(40, 56):
        put(x, 27, rgb("D8D0B8"))
        put(x, 31, rgb("F0E8D8"))
    for x in range(32, 40):
        put(x, 59, rgb("D8D0B8"))
    # horns on the hat layer: front face of the hat (40..48 x 8..16) top corners, and the hat top (40..48 x 0..8)
    for (x, y) in ((40, 8), (41, 8), (40, 9), (46, 8), (47, 8), (47, 9), (41, 9), (46, 9)):
        put(x, y, h)
    for (x, y) in ((41, 1), (42, 2), (41, 2), (46, 1), (45, 2), (46, 2)):
        put(x, y, h)
    name = "iblis_kecil_%d" % index
    SKINS.append((name, img))


# ================================================================================================ world FX textures

FX_DIR = os.path.join(ROOT, "textures", "dunia", "reruntuhan")
FX = []


def fx_textures():
    """White (vertex-tinted) emissive sprites for client.dunia.reruntuhan.RFxClient / RSink: the telegraph ring, the
    floor crack decal, the seal sigil, the soft glow, the cloud puff of the pagoda's crown, the portal curtain of the
    band gates, and the talisman paper (drawn cutout, in its own colours)."""
    def save(name, img):
        os.makedirs(FX_DIR, exist_ok=True)
        img.save(os.path.join(FX_DIR, name + ".png"))
        FX.append((name, img))

    # telegraph ring: a bright rim, a second thin ring and a faint hatched fill (white, tinted per skill)
    s = 64
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            u = (x + 0.5) / s * 2 - 1
            v = (y + 0.5) / s * 2 - 1
            d = math.hypot(u, v)
            if d > 1.0:
                continue
            a = clamp01(1.0 - abs(d - 0.93) / 0.06)
            a = max(a, 0.55 * clamp01(1.0 - abs(d - 0.80) / 0.025))
            hatch = 1.0 if ((x + y) // 3) % 2 == 0 else 0.0
            a = max(a, 0.16 * hatch * clamp01(d / 0.9))
            a = max(a, 0.08)
            img.set(x, y, (255, 255, 255, int(a * 255)))
    save("cincin_telegraf", img)

    # crack decal: veins radiating from the centre with a glowing core (alpha only, tinted by the caller)
    s = 64
    img = Image(s, s)
    rnd = random.Random(7062)
    n = Noise(7063)
    veins = []
    for k in range(9):
        a = k * math.tau / 9 + rnd.uniform(-0.25, 0.25)
        pts = [(32.0, 32.0)]
        x, y = 32.0, 32.0
        for _ in range(12):
            a += rnd.uniform(-0.45, 0.45)
            x += math.cos(a) * 2.6
            y += math.sin(a) * 2.6
            pts.append((x, y))
        veins.append(pts)
    for y in range(s):
        for x in range(s):
            best = 99.0
            for pts in veins:
                for i in range(len(pts) - 1):
                    ax, ay = pts[i]
                    bx, by = pts[i + 1]
                    dx, dy = bx - ax, by - ay
                    t = clamp01(((x - ax) * dx + (y - ay) * dy) / max(1e-6, dx * dx + dy * dy))
                    px, py = ax + dx * t, ay + dy * t
                    best = min(best, math.hypot(x - px, y - py) + i * 0.08)
            r = math.hypot(x - 32, y - 32) / 32.0
            a = clamp01(1.0 - best / 1.6) * clamp01(1.2 - r)
            a = max(a, clamp01(1.0 - r * 3.2) * 0.9)
            a *= 0.75 + 0.25 * n.fbm(x * 0.2, y * 0.2)
            if a > 0.01:
                img.set(x, y, (255, 255, 255, int(a * 255)))
    save("retak", img)

    # seal sigil: two rims, the eight trigrams in the Early Heaven order, tick marks and a square seal in the middle
    s = 128
    img = Image(s, s)
    tri = ("111", "011", "101", "001", "110", "010", "100", "000")  # bottom line first: Qian Dui Li Zhen Xun Kan Gen Kun
    for y in range(s):
        for x in range(s):
            u = (x + 0.5) / s * 2 - 1
            v = (y + 0.5) / s * 2 - 1
            d = math.hypot(u, v)
            if d > 1.0:
                continue
            ang = math.atan2(v, u)
            a = clamp01(1.0 - abs(d - 0.95) / 0.025)
            a = max(a, clamp01(1.0 - abs(d - 0.62) / 0.02))
            a = max(a, 0.7 * clamp01(1.0 - abs(d - 0.40) / 0.015))
            tick = (ang / math.tau * 48.0) % 1.0
            if 0.87 < d < 0.92 and (tick < 0.12 or tick > 0.88):
                a = max(a, 0.9)
            sector = (ang + math.pi / 8) / (math.tau / 8)
            k = int(math.floor(sector)) % 8
            local = sector - math.floor(sector)
            if 0.66 <= d <= 0.84 and 0.22 <= local <= 0.78:
                line = int((d - 0.66) / 0.06)
                frac = (d - 0.66) / 0.06 - line
                if 0 <= line < 3 and 0.18 < frac < 0.82:
                    solid = tri[k][line] == "1"
                    if solid or not (0.44 < local < 0.56):
                        a = max(a, 0.95)
            m = max(abs(u), abs(v))
            if 0.18 < m < 0.22:
                a = max(a, 0.9)
            if m < 0.14 and (abs(u) < 0.025 or abs(v) < 0.025 or abs(abs(u) - abs(v)) < 0.02):
                a = max(a, 0.85)
            if a > 0.01:
                img.set(x, y, (255, 255, 255, int(a * 255)))
    save("segel_lingkar", img)

    # soft glow dot
    s = 32
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            d = math.hypot((x + 0.5) / s * 2 - 1, (y + 0.5) / s * 2 - 1)
            a = clamp01(1.0 - d) ** 2.2
            if a > 0.0:
                img.set(x, y, (255, 255, 255, int(a * 255)))
    save("cahaya", img)

    # cloud puff: billowy lobes, bright top, soft edge (tinted pale by the caller)
    s = 64
    img = Image(s, s)
    n = Noise(7064)
    lobes = [(32, 36, 20), (18, 38, 13), (46, 38, 14), (26, 26, 12), (40, 26, 13), (32, 20, 10)]
    for y in range(s):
        for x in range(s):
            a = 0.0
            for cx, cy, r in lobes:
                a = max(a, clamp01(1.0 - math.hypot(x - cx, y - cy) / r))
            a = clamp01(a * 1.6) * (0.7 + 0.3 * n.fbm(x * 0.12, y * 0.12, 4))
            shade = 255 - int(40 * clamp01((y - 20) / 30.0))
            if a > 0.01:
                img.set(x, y, (shade, shade, min(255, shade + 6), int(a * 220)))
    save("awan", img)

    # portal curtain: vertical streams, brightest at the bottom, fading up and to the sides
    w, h = 32, 64
    img = Image(w, h)
    n = Noise(7065)
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w
            v = (y + 0.5) / h
            side = clamp01(1.0 - abs(u - 0.5) * 2.2)
            stream = 0.55 + 0.45 * math.sin(u * 23.0 + n.fbm(u * 3.0, v * 6.0) * 5.0)
            a = side ** 0.7 * (0.35 + 0.65 * v) * stream
            if a > 0.01:
                img.set(x, y, (255, 255, 255, int(clamp01(a) * 255)))
    save("tirai_gerbang", img)

    # talisman paper (cutout, its own colours): yellow paper, red border, a cinnabar seal script and the seal box
    w, h = 16, 32
    img = Image(w, h)
    n = Noise(7066)
    for y in range(h):
        for x in range(w):
            c = mix(rgb("F0D878"), rgb("D8B850"), n.fbm(x * 0.3, y * 0.15))
            if x in (0, w - 1) or y in (0, h - 1):
                c = rgb("A01A14")
            elif x in (1, w - 2) or y in (1, h - 2):
                c = mix(c, rgb("C02A1A"), 0.35)
            if 6 <= x <= 9 and 7 <= y <= 27:
                k = (y * 7 + x * 3) % 11
                if k in (0, 1, 2, 5, 8) or (x in (7, 8) and y % 4 != 3):
                    c = rgb("B81E16")
            if 4 <= x <= 11 and 2 <= y <= 5 and (x in (4, 11) or y in (2, 5) or x in (7, 8)):
                c = rgb("9A120E")
            img.set(x, y, (int(c[0]), int(c[1]), int(c[2]), 255))
    save("jimat", img)


def eye_textures():
    """The emissive eye overlays (player-skin layout, only the eye pixels; drawn with the vanilla "eyes" render type):
    the lesser demons' painted eyes (row 11, their eye colour) and the mirror shadow's violet glare (rows 11-13)."""
    for i, eye in enumerate(("FFD040", "FF4020", "C0FF60")):
        img = Image(64, 64)
        e = rgb(eye)
        for x in (9, 10, 13, 14):
            img.set(x, 11, (e[0], e[1], e[2], 255))
            img.set(x, 10, (e[0], e[1], e[2], 70))
            img.set(x, 12, (e[0], e[1], e[2], 90))
        img.save(os.path.join(SKIN_DIR, "mata_iblis_%d.png" % i))
        FX.append(("mata_iblis_%d" % i, img))
    img = Image(64, 64)
    for x in (9, 10, 13, 14):
        img.set(x, 11, (200, 150, 255, 110))
        img.set(x, 12, (230, 200, 255, 255) if x in (10, 13) else (150, 90, 230, 255))
        img.set(x, 13, (120, 60, 200, 60))
    img.save(os.path.join(SKIN_DIR, "mata_bayangan.png"))
    FX.append(("mata_bayangan", img))


# ================================================================================================ output

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def model_json(m):
    textures = {key: TEX_PREFIX + name for name, key in m.tex.items()}
    textures["particle"] = TEX_PREFIX + next(iter(m.tex))
    return {"textures": textures, "elements": m.els}


def main():
    build_textures()
    xiangliu()
    banteng()
    chiyou()
    lembu_besi()
    kura_batu()
    harimau_petir()
    rubah_ilusi()
    kilin_emas()
    crane("bangau_api", "ba_feather", "ba_flame", "ba_beak", "ba_leg", flame_tex="ba_flame")
    crane("bangau_abadi", "bb_feather", "bb_crown", "bb_beak", "bb_leg")
    ular_giok()
    naga_bayangan()
    icons()
    fx_textures()
    os.makedirs(SKIN_DIR, exist_ok=True)
    eye_textures()
    for i, (body, dark, horn_c, eye) in enumerate((("8A2A20", "3A0A08", "2A2020", "FFD040"), ("5A5A60", "1A1A20", "D8D0C0", "FF4020"),
                                                    ("2A6A4A", "0A2A1A", "C8C0A0", "C0FF60"))):
        demon_skin(i, body, dark, horn_c, eye)
    errors = 0
    used = set()
    for mid, m in sorted(MODELS.items()):
        problems = validate(m)
        if problems:
            errors += 1
            print("MODEL %s:" % mid)
            for p in problems:
                print("   ", p)
        used.update(m.tex)
        write_json(os.path.join(MODEL_DIR, mid + ".json"), model_json(m))
        write_json(os.path.join(ITEM_DIR, mid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/dunia/reruntuhan/%s" % (NS, mid)}})
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    os.makedirs(SKIN_DIR, exist_ok=True)
    for name, img in SKINS:
        img.save(os.path.join(SKIN_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet_items = [(n, TEXTURES[n]) for n in sorted(used)] + SKINS + ICONS + FX
        cols = 8
        cell = 72
        rows = (len(sheet_items) + cols - 1) // cols
        sheet = Image(cols * cell, rows * cell, (30, 30, 34, 255))
        for i, (n, img) in enumerate(sheet_items):
            ox, oy = (i % cols) * cell + 4, (i // cols) * cell + 4
            scale = max(1, 64 // max(img.width, img.height))
            for y in range(img.height * scale):
                for x in range(img.width * scale):
                    c = img.get(x // scale, y // scale)
                    if c[3] > 0:
                        sheet.blend(ox + x, oy + y, c)
        sheet.save(os.path.join(preview, "reruntuhan_textures.png"))
    print("module R models: %d, textures: %d, skins: %d, icons: %d, fx: %d, errors: %d" % (len(MODELS), len(used), len(SKINS), len(ICONS),
                                                                                         len(FX), errors))
    for mid, m in sorted(MODELS.items()):
        print("  %-26s %2d elements" % (mid, len(m.els)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

"""Models and textures of the town animals and the lion dance of DESIGN 42 "Kota yang Hidup" (42.7.5, 42.12.2, 42.12.4;
module 42V-H). Everything is ours: cuboid meshes and procedural fur, feather, lacquer and cloth painters; nothing is
downloaded.

The mesh machinery is the one of 42V-M (tools/gen_kota_hidup_assets.py, imported read-only: Box/Part/Mesh, box-UV packing,
painting, the textured 3/4 preview with the exact ModelPart.Cube uv layout and the Java emitter). One source of truth per
animal: a mesh written here once as named parts (pivot + rotation, mob model space: y down, 24 = the ground, front = -z)
whose boxes carry a material; every look (coat, breed, colour) is a palette {material: painter} over the same uv layout.

Writes:
  src/main/resources/assets/kultivator/textures/entity/kota42/hewan/<mesh>_<look>.png
      kucing_{oranye,hitam,putih,belang_abu,hitam_putih,kaliko}, anjing_{kuning,hitam,belang,penjaga},
      ayam_{jago,betina,anak}, merpati_{abu,putih,coklat}, kuda_{coklat,hitam,abu_belang,putih},
      keledai_{abu,coklat}, unta_{pasir,coklat_tua}, barongsai_{merah_emas,hijau,hitam}
  src/client/java/dev/kultivator/client/dunia/kota42/model/hewan/HewanMesh.java   (GENERATED; never hand-edit)
  previews (optional) <preview_dir>/h_<mesh>_<look>_<pose>.png and t_<mesh>_<look>.png (read them with Read)

Run: python -I tools/gen_kota_hidup_hewan.py [preview_dir]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_kota_hidup_assets as A  # noqa: E402  (42V-M's mesh machinery, read-only)
from gen_tempat_models import rgb, mix, shade, stable  # noqa: E402

REPO = A.REPO
TEX_DIR = os.path.join(A.ENT_TEX, "hewan")
JAVA_MESH = os.path.join(REPO, "src", "client", "java", "dev", "kultivator", "client", "dunia", "kota42", "model", "hewan", "HewanMesh.java")

Mesh = A.Mesh
deg = A.deg
nz = A.nz
along = A.along

OURS = []          # meshes of this script, in emission order
WRITTEN = []
ERRORS = A.ERRORS


def mesh(name, width, looks, doc, glow=()):
    m = Mesh(name, width, looks, glow=glow, doc=doc)
    del A.MESHES[name]  # keep 42V-M's registry untouched; we emit our own file
    OURS.append(m)
    return m


# ================================================================================================ painters
# p(fx, fy, fw, fh, face, rng) -> (r, g, b[, a]) or None (transparent). (fx, fy) = the texel's top-left inside the face
# in model pixels; side faces run fy downwards; "top" runs fy from the back (+z) to the front; "right" is the -x face.

SISI = ("left", "right")
FACE_OFS = {"top": 0, "bottom": 31, "right": 57, "front": 83, "left": 109, "back": 135}


def _ofs(face, fw, fh):
    return FACE_OFS[face] + int(fw * 7 + fh * 13)


def bulu(base, seed, perut=None, punggung=None, helai=0.11, pola=None, kasar=False):
    """Fur: fine strands along the face's height (or along the long side on top), a paler belly on the underside and
    the lower quarter of the sides, a darker spine on top; pola(fx, fy, fw, fh, face) may return a marking colour."""
    b = rgb(base)
    pc = rgb(perut) if perut else None
    bc = rgb(punggung) if punggung else None

    def p(fx, fy, fw, fh, face, rng):
        o = _ofs(face, fw, fh)
        col = b
        if pola is not None:
            m = pola(fx, fy, fw, fh, face)
            if m is not None:
                col = m
        if pc is not None:
            if face == "bottom":
                col = mix(col, pc, 0.85)
            elif face != "top" and fh >= 2.0 and fy > fh * 0.68:
                col = mix(col, pc, min(1.0, (fy / fh - 0.68) / 0.32) * 0.75)
        if bc is not None and face == "top":
            col = mix(col, bc, 0.45)
        if face in ("top", "bottom"):
            strand = nz(seed, int(fx * 2) + o, int(fy * 4), 2)
        else:
            strand = nz(seed, int(fx * 4) + o, int(fy * 2), 2)
        col = shade(col, 1.0 - helai + 2 * helai * strand)
        if kasar and rng.random() < 0.08:
            col = shade(col, 0.78)
        return col
    return p


def tabby(stripe, period, seed, thr=0.45, base=None):
    """Tabby stripes across the long axis of every face (rings on legs and tail, bars on the flanks)."""
    s = rgb(stripe)
    bb = rgb(base) if base else None

    def pola(fx, fy, fw, fh, face):
        a, c = along(fx, fy, fw, fh)
        o = _ofs(face, fw, fh)
        v = math.sin(a * 2 * math.pi / period + 1.6 * nz(seed, int(c * 2) + o, int(a), 3) + o)
        if v > thr:
            return mix(bb or s, s, min(1.0, (v - thr) * 3.0))
        return bb
    return pola


def bercak(colours, seed, cell=3):
    """Patches (calico, piebald): colours = [(threshold, hex), ...] over a value noise, first match wins."""
    cs = [(t, rgb(c)) for t, c in colours]

    def pola(fx, fy, fw, fh, face):
        o = _ofs(face, fw, fh)
        n = nz(seed, int(fx * 2) + o, int(fy * 2) + o // 3, cell)
        for t, c in cs:
            if n < t:
                return c
        return None
    return pola


def belang_bundar(light, seed):
    """Dapple grey: pale rings with darker rims (horse coats)."""
    lc = rgb(light)

    def pola(fx, fy, fw, fh, face):
        o = _ofs(face, fw, fh)
        cx = math.floor(fx / 3.0)
        cy = math.floor(fy / 3.0)
        jx = nz(seed, int(cx) * 5 + o, int(cy) * 7, 1) * 1.4
        jy = nz(seed + 1, int(cx) * 5 + o, int(cy) * 7, 1) * 1.4
        dx = fx - (cx * 3 + 0.8 + jx)
        dy = fy - (cy * 3 + 0.8 + jy)
        d = math.sqrt(dx * dx + dy * dy)
        if d < 0.9:
            return lc
        return None
    return pola


def kombinasi(*polas):
    def pola(fx, fy, fw, fh, face):
        for q in polas:
            if q is None:
                continue
            c = q(fx, fy, fw, fh, face)
            if c is not None:
                return c
        return None
    return pola


def satu(c, var=0.0, seed=3):
    col = rgb(c)

    def p(fx, fy, fw, fh, face, rng):
        if var:
            return shade(col, 1.0 - var + 2 * var * nz(seed, int(fx * 4), int(fy * 4), 2))
        return col
    return p


def mata(iris, k, pupil="bulat", tepi="1A1410", kilau=True, muka=("front",)):
    """An eye on the box's front face: iris with a dark rim, a round or slit pupil and a catch light; the other faces are
    the rim colour. k = the box's texel density (to sample texel centres); muka = the faces that show the eye (side
    eyes of horses, birds and camels use SISI)."""
    ic, rc = rgb(iris), rgb(tepi)
    h = 0.5 / k

    def p(fx, fy, fw, fh, face, rng):
        if face not in muka:
            return rc
        x, y = fx + h, fy + h
        nx, ny = (x - fw / 2.0) / (fw / 2.0), (y - fh / 2.0) / (fh / 2.0)
        r = math.sqrt(nx * nx + ny * ny)
        if kilau and nx < -0.25 and ny < -0.25 and r < 0.85:
            return (250, 250, 240)
        if pupil == "celah" and abs(nx) < 0.3:
            return (16, 12, 10)
        if pupil == "bulat" and r < 0.5:
            return (16, 12, 10)
        if r > 0.92:
            return rc
        return mix(shade(ic, 1.15), shade(ic, 0.7), max(0.0, ny * 0.5 + 0.5))
    return p


def kulit(base, seed=41, var=0.10):
    """Bare skin or leather: smooth with soft mottling."""
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        return shade(b, 1.0 - var + 2 * var * nz(seed, int(fx * 3), int(fy * 3), 3))
    return p


def tanduk_kuku(base, seed=43):
    """Hoof or horn: vertical growth lines, darker towards the ground."""
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.85 + 0.2 * nz(seed, int(fx * 4), 0, 1))
        if face not in ("top", "bottom"):
            col = shade(col, 1.08 - 0.25 * fy / max(fh, 0.5))
        return col
    return p


def bulu_burung(base, seed, garis=None, sisik=True, tepi=None):
    """Feathers: overlapping scales (rows of rounded tips) with a darker shaft line; garis = colour of wing bars."""
    b = rgb(base)
    gc = rgb(garis) if garis else None
    tc = rgb(tepi) if tepi else None

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.92 + 0.12 * nz(seed, int(fx * 4), int(fy * 4), 2))
        if sisik:
            row = math.floor(fy / 0.75)
            off = 0.5 if row % 2 else 0.0
            u = (fx / 0.75 + off) % 1.0
            v = (fy / 0.75) % 1.0
            if v > 0.55 + 0.35 * abs(u - 0.5) * 2:
                col = shade(col, 0.82)
        if gc is not None and face not in ("top", "bottom") and fw > 2 and (0.35 < fx / fw < 0.45 or 0.6 < fx / fw < 0.7):
            col = gc
        if tc is not None and fy > fh - 0.5 and face not in ("top", "bottom"):
            col = tc
        return col
    return p


def sayap_burung(base, seed, bar=None, tip=None):
    """A spread wing card (its big faces are top and bottom): feather rows across the span, two dark bars over the
    secondaries near the trailing edge, darker primary tips along the trailing edge; the underside paler."""
    b = rgb(base)
    bc = rgb(bar) if bar else None
    tc = rgb(tip) if tip else None

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.92 + 0.1 * nz(seed, int(fx * 4), int(fy * 4), 2))
        if face not in ("top", "bottom"):
            return shade(col, 0.85)
        # top: fy = 0 at the trailing (back) edge; bottom: fy = 0 at the leading edge
        trail = fy if face == "top" else fh - fy
        if (fx * 4) % 3 < 0.6:
            col = shade(col, 0.86)  # feather shafts
        if bc is not None and fh > 2 and (abs(trail - 1.0) < 0.22 or abs(trail - 1.6) < 0.22):
            col = bc
        if tc is not None and trail < 0.45:
            col = tc
        if face == "bottom":
            col = mix(col, (240, 240, 236), 0.3)
        return col
    return p


def bulu_bergradasi(top, bottom, seed, helai=0.08):
    """Feathers or fur fading from top to bottom of a face (hackles, pigeon necks)."""
    t, bo = rgb(top), rgb(bottom)

    def p(fx, fy, fw, fh, face, rng):
        if face == "top":
            col = t
        elif face == "bottom":
            col = bo
        else:
            col = mix(t, bo, fy / max(fh, 0.5))
        return shade(col, 1.0 - helai + 2 * helai * nz(seed, int(fx * 4), int(fy * 4), 2))
    return p


def kilau_leher(seed):
    """The iridescent pigeon neck: green on one side of each feather, purple on the other."""
    def p(fx, fy, fw, fh, face, rng):
        v = math.sin(fx * 3.1 + fy * 1.7 + nz(seed, int(fx * 4), int(fy * 4), 2) * 3)
        col = mix(rgb("4E8A6A"), rgb("8A4E8E"), v * 0.5 + 0.5)
        return shade(col, 0.85 + 0.25 * nz(seed + 3, int(fx * 4), int(fy * 4), 2))
    return p


def rambut(base, seed, ujung=None, rontok=0.0, gelap=0.0):
    """Hair of a mane or tail card: long strands along the height, frayed transparent tips at the bottom (rontok =
    share of the lowest 15 % left empty), lighter tips (ujung)."""
    b = rgb(base)
    uc = rgb(ujung) if ujung else None

    def p(fx, fy, fw, fh, face, rng):
        s = nz(seed, int(fx * 4) + _ofs(face, fw, fh), int(fy * 0.5), 1)
        col = shade(b, 0.72 + 0.4 * s)
        if uc is not None and face not in ("top", "bottom"):
            col = mix(col, uc, max(0.0, (fy / max(fh, 0.5) - 0.6) / 0.4) * 0.7)
        if rontok and face not in ("top", "bottom") and fy > fh * 0.85 and nz(seed + 9, int(fx * 4), 0, 1) < rontok:
            return None
        if gelap:
            col = shade(col, 1.0 - gelap)
        return col
    return p


def tali(base="7A3A1E", seed=45, stitch="C8A060"):
    """Leather strap with stitch dots along the long side."""
    b, sc = rgb(base), rgb(stitch)

    def p(fx, fy, fw, fh, face, rng):
        a, c = along(fx, fy, fw, fh)
        col = shade(b, 0.88 + 0.16 * nz(seed, int(a * 2), int(c * 2), 2))
        lo = min(fw, fh)
        if lo >= 0.9 and (int(a * 2) % 2 == 0) and abs(c - lo / 2.0) < 0.2:
            col = sc
        return col
    return p


def kain_tenun(colours, seed, lebar=1.5, emas=None):
    """Striped woven saddle cloth (kilim-like): bands along the face height with a small diamond motif on wide bands."""
    cs = [rgb(c) for c in colours]
    gc = rgb(emas) if emas else None

    def p(fx, fy, fw, fh, face, rng):
        band = int(fy / lebar)
        col = cs[band % len(cs)]
        if gc is not None and band % len(cs) == 0:
            u = (fx % 2.0) - 1.0
            v = (fy % lebar) / lebar - 0.5
            if abs(u) + abs(v) * 2 < 0.55:
                col = gc
        if (int(fx * 2) + int(fy * 2)) % 2 == 0:
            col = shade(col, 0.93)
        return shade(col, 0.92 + 0.12 * nz(seed, int(fx * 2), int(fy * 2), 2))
    return p


def karung(base="B89A68", seed=47):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.86 + 0.16 * nz(seed, int(fx * 2), int(fy * 2), 3))
        if (int(fx * 2) + int(fy * 2)) % 2 == 0:
            col = shade(col, 0.92)
        if face not in ("top", "bottom") and abs(fy - fh * 0.25) < 0.3:
            col = rgb("6E4A2A")  # the tie rope
        return col
    return p


def rumbai(base, seed, tip=None, frayed=0.45):
    """Fringe or fur card: dense strands, a ragged transparent lower edge."""
    b = rgb(base)
    tc = rgb(tip) if tip else None

    def p(fx, fy, fw, fh, face, rng):
        s = nz(seed, int(fx * 4) + _ofs(face, fw, fh), 0, 1)
        col = shade(b, 0.78 + 0.32 * s)
        if face in ("top", "bottom"):
            return col
        if tc is not None and fy > fh * 0.55:
            col = mix(col, tc, 0.6)
        if fy > fh * (0.55 + 0.45 * s) and fy > fh - 1.2 and rng.random() < frayed:
            return None
        return col
    return p


def sisik_kain(base, rim, inner, seed, period=4.4):
    """Lion dance body cloth: rows of overlapping fish scales, each with a pale inner arc and a gold rim, over a woven
    satin sheen."""
    b, r, i = rgb(base), rgb(rim), rgb(inner)

    def p(fx, fy, fw, fh, face, rng):
        row = math.floor(fy / (period * 0.6))
        off = 0.5 if row % 2 else 0.0
        u = (fx / period + off) % 1.0 - 0.5
        v = (fy / (period * 0.6)) % 1.0
        d = math.sqrt((u * 1.0) ** 2 + ((v - 0.15) * 0.75) ** 2)
        if d > 0.52:
            col = shade(b, 0.72)
        elif d > 0.43:
            col = r
        elif d > 0.30:
            col = mix(b, i, 0.55)
        else:
            col = b
        sheen = max(0.0, math.sin((fx + fy) * 0.7)) ** 8
        col = mix(col, (255, 240, 220), sheen * 0.18)
        return shade(col, 0.95 + 0.06 * nz(seed, int(fx * 2), int(fy * 2), 2))
    return p


def topeng(base, gold, accent, seed, garis="1A1010"):
    """Lion head papier-mache: lacquer base, gold cloud scrolls, accent dots, a gold edge band, black outlines."""
    b, g, ac, gl = rgb(base), rgb(gold), rgb(accent), rgb(garis)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.9 + 0.14 * nz(seed, int(fx * 2), int(fy * 2), 3))
        v = math.sin(fx * 0.55 + 1.5 * math.sin(fy * 0.35 + seed)) * math.cos(fy * 0.5 - 1.1 * math.sin(fx * 0.3))
        if abs(v) < 0.05:
            col = gl
        elif abs(v) < 0.15:
            col = g
        elif v > 0.88:
            col = mix(col, ac, 0.7)
        if fw >= 3 and fh >= 3 and (fx < 0.5 or fy < 0.5 or fx >= fw - 0.5 or fy >= fh - 0.5):
            col = g
        sheen = max(0.0, math.sin((fx + fy * 0.6) * 0.5)) ** 6
        return mix(col, (255, 236, 214), sheen * 0.25)
    return p


def emas(seed=51):
    return A.emas(seed)


def cermin():
    """Forehead mirror: silver disc with a sky reflection and a red rim (front face); the rest silver."""
    def p(fx, fy, fw, fh, face, rng):
        if face != "front":
            return rgb("B8BCC4")
        nx, ny = (fx + 0.25 - fw / 2.0) / (fw / 2.0), (fy + 0.25 - fh / 2.0) / (fh / 2.0)
        r = math.sqrt(nx * nx + ny * ny)
        if r > 0.95:
            return rgb("C8281E")
        if r > 0.82:
            return rgb("E8C058")
        if nx < -0.1 and ny < -0.1 and nx + ny > -1.0:
            return (250, 252, 255)
        return mix(rgb("9AB0C8"), rgb("E0E8F0"), 0.5 - ny * 0.5)
    return p


def daun_selada(seed=53):
    def p(fx, fy, fw, fh, face, rng):
        col = mix(rgb("5E9A2E"), rgb("B8E070"), nz(seed, int(fx * 4), int(fy * 4), 2))
        if abs((fx * 1.3 + fy) % 1.6 - 0.8) < 0.12:
            col = mix(col, rgb("E8F8C0"), 0.6)  # veins
        return col
    return p


def angpao(seed=55):
    def p(fx, fy, fw, fh, face, rng):
        col = shade(rgb("C8201A"), 0.9 + 0.1 * nz(seed, int(fx * 4), int(fy * 4), 2))
        if face in ("front", "back") and abs(fx - fw / 2) < fw * 0.22 and abs(fy - fh / 2) < fh * 0.22:
            return rgb("F0C850")
        return col
    return p


def gigi():
    def p(fx, fy, fw, fh, face, rng):
        col = rgb("F4EEDC")
        if face not in ("top", "bottom") and int(fx * 2) % 3 == 0:
            col = rgb("B8B0A0")  # tooth gaps
        return col
    return p


# ================================================================================================ cat

KUCING_LOOKS = {}


def _kucing_look(name, badan, kepala, kaki, ekor, dada, moncong, telinga, iris, kelopak, cakar=None):
    KUCING_LOOKS[name] = {
        "badan": badan, "kepala": kepala, "kaki": kaki, "ekor": ekor, "dada": dada, "moncong": moncong,
        "telinga": telinga, "telinga_dalam": kulit("E8A0A0", 61), "hidung": satu("D87A80"), "lidah": satu("E86A78", 0.05),
        "kumis": satu("F4F0E8"), "mata": mata(iris, 4, "celah"), "kelopak": kelopak, "cakar": cakar or kaki,
    }


def kucing_looks():
    # orange tabby: ginger with darker bars, cream belly and chin
    jahe = bulu("D8862E", 101, perut="F0D8A8", pola=tabby("A0561A", 1.8, 102, 0.35))
    _kucing_look("oranye", jahe, bulu("D8862E", 103, pola=tabby("A0561A", 1.4, 104, 0.5)), bulu("D8862E", 105, pola=tabby("A0561A", 1.2, 106, 0.4)),
                 bulu("D8862E", 107, pola=tabby("9A5018", 1.3, 108, 0.2)), bulu("F2DCB0", 109), bulu("F4E0BC", 110), bulu("C8762A", 111),
                 "C8A030", bulu("D8862E", 112))
    hitam = bulu("26221F", 121, perut="34302C", helai=0.16)
    _kucing_look("hitam", hitam, bulu("26221F", 122, helai=0.16), bulu("221E1C", 123, helai=0.14), bulu("221E1C", 124, helai=0.16),
                 bulu("2C2824", 125), bulu("2E2A26", 126), bulu("1E1A18", 127), "D8B030", bulu("26221F", 128))
    putih = bulu("ECE8E0", 131, perut="F8F6F2", helai=0.06)
    _kucing_look("putih", putih, bulu("F0ECE4", 132, helai=0.06), bulu("ECE8E0", 133, helai=0.06), bulu("E8E4DC", 134, helai=0.07),
                 bulu("F8F6F0", 135), bulu("F8F6F2", 136), bulu("E4DED6", 137), "6A9AD0", bulu("ECE8E0", 138))
    abu = bulu("8E8A84", 141, perut="C8C2B8", pola=tabby("3E3A36", 1.6, 142, 0.3))
    _kucing_look("belang_abu", abu, bulu("8E8A84", 143, pola=tabby("3E3A36", 1.2, 144, 0.45)), bulu("8E8A84", 145, pola=tabby("3E3A36", 1.1, 146, 0.35)),
                 bulu("8E8A84", 147, pola=tabby("34302C", 1.2, 148, 0.15)), bulu("D2CCC2", 149), bulu("D8D2C8", 150), bulu("7A7672", 151),
                 "8AB040", bulu("8E8A84", 152))

    # tuxedo: black coat, white chest, belly, muzzle and paws
    def kaos_kaki(fx, fy, fw, fh, face):
        if face == "bottom" or (face != "top" and fy > fh * 0.55):
            return rgb("F2EEE6")
        return None
    _kucing_look("hitam_putih", bulu("24201E", 161, perut="F2EEE6", helai=0.15), bulu("24201E", 162, helai=0.15),
                 bulu("24201E", 163, pola=kaos_kaki, helai=0.12), bulu("24201E", 164, helai=0.15), bulu("F4F0E8", 165), bulu("F4F0E8", 166),
                 bulu("1E1A18", 167), "A8B838", bulu("24201E", 168), cakar=bulu("F2EEE6", 169))
    # calico: white base with orange and black patches
    pola = bercak([(0.34, "D8862E"), (0.46, "F2EEE6"), (0.62, "2A2622")], 171)
    pola2 = bercak([(0.30, "2A2622"), (0.52, "F2EEE6"), (0.70, "D8862E")], 172)
    _kucing_look("kaliko", bulu("F2EEE6", 173, perut="F8F6F2", pola=pola), bulu("F2EEE6", 174, pola=pola2),
                 bulu("F2EEE6", 175, pola=bercak([(0.25, "D8862E")], 176)), bulu("F2EEE6", 177, pola=bercak([(0.45, "2A2622"), (0.7, "D8862E")], 178)),
                 bulu("F6F2EA", 179), bulu("F6F2EA", 180), bulu("D8862E", 181), "C8A030", bulu("F2EEE6", 182, pola=pola2))
    return dict(KUCING_LOOKS)


def mesh_kucing():
    m = mesh("kucing", 64, kucing_looks(),
             "town cat (42.7.5): body 4 x 4 x 9, head with ears, cheeks, muzzle, slit-pupil eyes, eyelids, whiskers, tongue, "
             "two-segment tail, four legs with haunches and paws; looks oranye/hitam/putih/belang_abu/hitam_putih/kaliko.")
    badan = m.part("badan", (0, 18, 0))
    badan.c(0, 0, 0, 4, 4, 9, "badan", k=2)
    badan.c(0, 0.6, -4.3, 3.4, 2.8, 0.8, "dada", k=2)
    badan.c(0, -0.2, 4.2, 3.6, 3.4, 0.8, "badan", k=2)   # rump rounding
    kepala = badan.part("kepala", (0, -1.8, -4.2))
    kepala.c(0, -1.2, -1.6, 4, 3.6, 3.6, "kepala", k=2)
    kepala.c(-2.05, -0.3, -2.3, 0.6, 1.6, 1.8, "kepala", k=2)  # cheek ruffs
    kepala.c(2.05, -0.3, -2.3, 0.6, 1.6, 1.8, "kepala", k=2)
    kepala.c(0, 0.0, -3.75, 2.0, 1.1, 0.9, "moncong", k=2)
    kepala.c(0, -0.62, -4.25, 0.8, 0.5, 0.2, "hidung", k=4)
    kepala.c(-0.95, -1.55, -3.45, 1.0, 0.9, 0.1, "mata", k=4)
    kepala.c(0.95, -1.55, -3.45, 1.0, 0.9, 0.1, "mata", k=4)
    kel = kepala.part("kelopak")
    kel.c(-0.95, -1.55, -3.53, 1.1, 1.0, 0.1, "kelopak", k=4)
    kel.c(0.95, -1.55, -3.53, 1.1, 1.0, 0.1, "kelopak", k=4)
    for s, nm in ((-1, "telinga_k"), (1, "telinga_kn")):
        t = kepala.part(nm, (s * 1.3, -3.0, -1.6), (0, 0, deg(12 * s)))
        t.c(0, -0.8, 0, 1.3, 1.6, 0.6, "telinga", k=4)
        t.c(0, -0.65, -0.33, 0.8, 1.1, 0.1, "telinga_dalam", k=4)
        w = kepala.part("kumis_" + ("k" if s < 0 else "kn"), (s * 0.9, 0.0, -4.0), (0, deg(25 * s), 0))
        for i, y in enumerate((-0.25, 0.1, 0.45)):
            w.b(0 if s > 0 else -2.6, y, 0, 2.6, 0.12, 0.12, "kumis", k=4, share="kumis")
    lidah = kepala.part("lidah", (0, 0.45, -4.0))
    lidah.c(0, 0, -0.4, 0.8, 0.3, 1.0, "lidah", k=4)
    e1 = badan.part("ekor_1", (0, -1.3, 4.3), (deg(-55), 0, 0))
    e1.b(-0.5, -0.5, 0, 1, 1, 5, "ekor", k=2)
    e2 = e1.part("ekor_2", (0, 0, 4.8), (deg(70), 0, 0))
    e2.b(-0.5, -0.5, 0, 1, 1, 5, "ekor", k=2)
    for nm, x, z, hind in (("kaki_dk", -1.15, -3.0, False), ("kaki_dkn", 1.15, -3.0, False), ("kaki_bk", -1.15, 3.2, True), ("kaki_bkn", 1.15, 3.2, True)):
        k_ = m.part(nm, (x, 20, z))
        k_.b(-0.5, 0, -0.5, 1, 3.5, 1, "kaki", k=2)
        k_.b(-0.62, 3.4, -0.85, 1.24, 0.6, 1.35, "cakar", k=2)
        if hind:
            k_.b(-0.78, -1.6, -1.1, 1.56, 2.8, 2.1, "badan", k=2)
        else:
            k_.b(-0.62, -1.0, -0.7, 1.24, 1.6, 1.4, "badan", k=2)
    return m


# ================================================================================================ dog

def anjing_looks():
    looks = {}

    def look(name, badan, kepala, kaki, ekor, moncong, telinga, alis, lebat, iris="6A4020", rahang=None):
        looks[name] = {"badan": badan, "kepala": kepala, "kaki": kaki, "ekor": ekor, "moncong": moncong, "telinga": telinga,
                       "telinga_dalam": kulit("C88A80", 63), "hidung": kulit("1E1A1A", 64, 0.2), "lidah": satu("E06A78", 0.05),
                       "gigi": satu("F2ECDC"), "mata": mata(iris, 4, "bulat"), "kelopak": kepala, "alis": alis,
                       "kalung": satu("B82A20", 0.1), "lonceng": emas(65), "bulu_lebat": lebat, "cakar": kaki,
                       "rahang": rahang or moncong}
    # village yellow dog (tugou): sandy coat, cream muzzle and belly
    look("kuning", bulu("C89650", 201, perut="F0DCB0", punggung="9A6E38"), bulu("C89650", 202), bulu("D0A060", 203, perut="F0DCB0"),
         bulu("C89650", 204, perut="F0DCB0"), bulu("EED8B0", 205), bulu("B8864A", 206), bulu("C89650", 207), bulu("C89650", 208, kasar=True))
    # black "four-eyed" dog: black with tan brow dots, cheeks and legs
    look("hitam", bulu("231F1D", 211, perut="8A5A30", helai=0.15), bulu("231F1D", 212, helai=0.15),
         bulu("231F1D", 213, pola=lambda fx, fy, fw, fh, face: rgb("A06A38") if (face != "top" and fy > fh * 0.6) else None),
         bulu("231F1D", 214), bulu("8A5A30", 215), bulu("1E1A18", 216), bulu("B07A40", 217), bulu("231F1D", 218, kasar=True))
    # brindle/piebald: white with brown patches
    pb = bercak([(0.38, "7A4E2A"), (0.48, "3A2A1E")], 221)
    look("belang", bulu("ECE6DA", 222, pola=pb), bulu("ECE6DA", 223, pola=bercak([(0.42, "7A4E2A")], 224)), bulu("ECE6DA", 225),
         bulu("ECE6DA", 226, pola=bercak([(0.5, "7A4E2A")], 227)), bulu("F4F0E8", 228), bulu("7A4E2A", 229), bulu("ECE6DA", 230),
         bulu("ECE6DA", 231, pola=pb, kasar=True))
    # the big shaggy guard dog: dark grey-brown with a heavy coat, tan legs (shown with the fur layer)
    look("penjaga", bulu("4A3E34", 241, perut="9A7A58", helai=0.2), bulu("4A3E34", 242, helai=0.2),
         bulu("4A3E34", 243, pola=lambda fx, fy, fw, fh, face: rgb("A07A50") if (face != "top" and fy > fh * 0.45) else None, helai=0.18),
         bulu("4A3E34", 244, helai=0.22), bulu("6A5848", 245), bulu("3A3028", 246), bulu("A07A50", 247),
         rumbai("4A3E34", 248, tip="6A5644", frayed=0.25), iris="8A5A20")
    return looks


def mesh_anjing():
    m = mesh("anjing", 128, anjing_looks(),
             "town dog (42.7.5): body 6 x 6 x 11, head 6 x 6 x 5 with a 3 x 3 x 3 muzzle and hinged jaw, upright ears, brow dots, "
             "red rope collar with a bell, curled three-segment tail, four legs; guard fur layer 7 x 7 x 12 with neck ruff; looks "
             "kuning/hitam/belang/penjaga.")
    badan = m.part("badan", (0, 16, 0))
    badan.c(0, 0, 0, 6, 6, 11, "badan", k=2)
    badan.c(0, 0.4, -5.6, 5.2, 4.8, 0.6, "badan", k=2)  # chest
    lebat = badan.part("bulu_lebat")
    lebat.c(0, 0.3, 0.2, 7, 7, 12, "bulu_lebat", k=1)
    lebat.c(0, -1.0, -5.4, 7.6, 7.2, 3, "bulu_lebat", k=1)
    kepala = badan.part("kepala", (0, -2.6, -5.6))
    kepala.c(0, -1.6, -2.0, 6, 5.6, 5, "kepala", k=2)
    kepala.c(0, -0.6, -5.4, 3, 2, 3, "moncong", k=2)
    kepala.c(0, -1.3, -6.95, 1.4, 0.8, 0.3, "hidung", k=4)
    kepala.c(0, 0.7, -0.4, 6.4, 1.0, 1.2, "kalung", k=2)      # red rope collar behind the jaw
    kepala.c(0, 1.7, -1.1, 1.0, 1.0, 1.0, "lonceng", k=4)
    kepala.c(-1.5, -2.5, -4.55, 1.2, 1.0, 0.1, "mata", k=4)
    kepala.c(1.5, -2.5, -4.55, 1.2, 1.0, 0.1, "mata", k=4)
    kepala.c(-1.5, -3.55, -4.55, 0.9, 0.5, 0.1, "alis", k=4)
    kepala.c(1.5, -3.55, -4.55, 0.9, 0.5, 0.1, "alis", k=4)
    kel = kepala.part("kelopak")
    kel.c(-1.5, -2.5, -4.62, 1.3, 1.1, 0.1, "kelopak", k=4)
    kel.c(1.5, -2.5, -4.62, 1.3, 1.1, 0.1, "kelopak", k=4)
    rahang = kepala.part("rahang", (0, 0.4, -3.9))
    rahang.b(-1.4, 0, -3.0, 2.8, 0.9, 3.0, "rahang", k=2)
    rahang.b(-0.8, -0.15, -2.8, 1.6, 0.25, 2.3, "lidah", k=4)
    rahang.b(-1.3, -0.35, -2.95, 2.6, 0.35, 0.3, "gigi", k=4)
    for s, nm in ((-1, "telinga_k"), (1, "telinga_kn")):
        t = kepala.part(nm, (s * 2.0, -4.3, -1.6), (deg(-8), 0, deg(10 * s)))
        t.c(0, -1.2, 0, 1.8, 2.4, 0.8, "telinga", k=2)
        t.c(0, -1.0, -0.42, 1.1, 1.6, 0.1, "telinga_dalam", k=4)
    e1 = badan.part("ekor_1", (0, -2.4, 5.4), (deg(55), 0, 0))
    e1.b(-0.75, -0.75, 0, 1.5, 1.5, 3.2, "ekor", k=2)
    e2 = e1.part("ekor_2", (0, 0, 3.0), (deg(55), 0, 0))
    e2.b(-0.8, -0.8, 0, 1.6, 1.6, 2.8, "ekor", k=2)
    e3 = e2.part("ekor_3", (0, 0, 2.6), (deg(50), 0, 0))
    e3.b(-0.85, -0.85, 0, 1.7, 1.7, 2.2, "ekor", k=2)
    for nm, x, z, hind in (("kaki_dk", -1.8, -4.0, False), ("kaki_dkn", 1.8, -4.0, False), ("kaki_bk", -1.8, 4.0, True), ("kaki_bkn", 1.8, 4.0, True)):
        k_ = m.part(nm, (x, 19, z))
        k_.b(-0.95, 0, -0.95, 1.9, 4.4, 1.9, "kaki", k=2)
        k_.b(-1.1, 4.2, -1.5, 2.2, 0.8, 2.5, "cakar", k=2)
        if hind:
            k_.b(-1.2, -2.0, -1.5, 2.4, 3.4, 3.0, "badan", k=2)
    return m


# ================================================================================================ chicken

def ayam_looks():
    looks = {}

    def look(name, badan, sayap, dada, leher, ekor, ekor_b, jengger, kaki, paruh, iris="C87A1E"):
        looks[name] = {"badan": badan, "sayap": sayap, "dada": dada, "leher": leher, "ekor": ekor, "ekor_b": ekor_b, "jengger": jengger,
                       "pial": jengger, "kaki": kaki, "paruh": paruh, "mata": mata(iris, 4, "bulat", kilau=False, muka=SISI), "kepala": leher}
    # rooster: chestnut back, gold hackles, black-green breast and sickle tail
    look("jago", bulu_burung("A8441E", 301), bulu_burung("8A3418", 302, garis="2A3A30"), bulu_burung("2A2A24", 303),
         bulu_bergradasi("F0A030", "C86020", 304), rambut("1E2E26", 305, ujung="3E6A4E"), rambut("1E2E26", 306),
         kulit("D8261E", 307, 0.12), satu("D8B048", 0.08), satu("D8B048"))
    # hen: speckled brown
    look("betina", bulu_burung("9A6A3E", 311), bulu_burung("7A5230", 312), bulu_burung("B88A5A", 313), bulu_bergradasi("B8844E", "9A6A3E", 314),
         rambut("5A3E26", 315), rambut("6A4A2E", 316), kulit("D03A2A", 317, 0.12), satu("D8B048", 0.08), satu("D8B048"))
    # chick: yellow down
    down = bulu("F0D048", 321, perut="F8E888", helai=0.12)
    look("anak", down, bulu("E8C440", 322), bulu("F8E070", 323), bulu("F0D048", 324), down, down, kulit("F0D048", 325), satu("E8A040"),
         satu("E89A40"), iris="1A1410")
    return looks


def mesh_ayam():
    m = mesh("ayam", 64, ayam_looks(),
             "yard chicken (42.7.5): body 4 x 5 x 6 with breast, neck-head 2 x 4 x 2, comb, wattle, beak, eyes, hackles, wings "
             "1 x 3 x 4, sickle tail (rooster) or short tail (hen), thin legs with toes; looks jago/betina/anak (the chick is "
             "drawn at half size with a bigger head and no comb, wattle or tail).")
    badan = m.part("badan", (0, 18.5, 0.4))
    badan.c(0, 0, 0, 4, 5, 6, "badan", k=2)
    badan.c(0, 0.6, -3.2, 3.4, 3.6, 0.8, "dada", k=2)
    badan.c(0, 1.4, 1.0, 3.6, 1.4, 4.4, "dada", k=2)  # fluffy underside
    for s, nm in ((-1, "sayap_k"), (1, "sayap_kn")):
        w = badan.part(nm, (s * 2.0, -1.9, -1.9))
        w.b(-1 if s < 0 else 0, 0, 0, 1, 3, 4, "sayap", k=2)
        w.b(-0.9 if s < 0 else -0.1, 2.6, 2.5, 1, 0.8, 2.0, "sayap", k=2)  # primary tips
    ej = badan.part("ekor_jago", (0, -1.8, 2.6), (deg(-35), 0, 0))
    ej.b(-0.35, -6.0, -0.4, 0.7, 6.5, 2.4, "ekor", k=2)
    ej.b(-0.3, -5.0, 1.6, 0.6, 5.5, 1.8, "ekor", k=2)
    sab = ej.part("ekor_sabit", (0, -6.0, 0.8), (deg(-60), 0, 0))
    sab.b(-0.25, -0.4, 0, 0.5, 0.8, 4.2, "ekor", k=2)
    eb = badan.part("ekor_betina", (0, -1.5, 2.8), (deg(-40), 0, 0))
    eb.b(-1.4, -2.6, -0.6, 2.8, 2.8, 1.6, "ekor_b", k=2)
    kepala = badan.part("kepala", (0, -1.9, -2.2))
    kepala.b(-1, -4, -1, 2, 4, 2, "leher", k=2)
    hk = kepala.part("bulu_leher")
    hk.c(0, -1.6, 0.15, 2.6, 2.8, 2.6, "leher", k=2)
    kepala.c(0, -3.05, -1.55, 0.8, 0.6, 1.1, "paruh", k=4)
    kepala.c(-1.02, -3.3, -0.45, 0.1, 0.55, 0.55, "mata", k=4)
    kepala.c(1.02, -3.3, -0.45, 0.1, 0.55, 0.55, "mata", k=4)
    jg = kepala.part("jengger")
    jg.b(-0.5, -6.3, -1.3, 1.0, 2.4, 2.6, "jengger", k=4)
    jg.b(-0.4, -6.9, -0.9, 0.8, 0.7, 0.6, "jengger", k=4)
    jg.b(-0.4, -6.8, 0.1, 0.8, 0.6, 0.6, "jengger", k=4)
    jk = kepala.part("jengger_kecil")
    jk.b(-0.4, -5.1, -1.1, 0.8, 1.2, 1.6, "jengger", k=4)
    pial = kepala.part("pial")
    pial.c(0, -2.0, -1.25, 0.8, 1.3, 0.4, "pial", k=4)
    for nm, x in (("kaki_k", -0.9), ("kaki_kn", 0.9)):
        k_ = m.part(nm, (x, 21, 0.5))
        k_.b(-0.4, -0.4, -0.5, 0.8, 1.2, 1.0, "badan", k=2)   # thigh feathers
        k_.b(-0.25, 0.6, -0.25, 0.5, 2.4, 0.5, "kaki", k=4)
        k_.b(-1.0, 2.9, -1.5, 2.0, 0.12, 2.0, "kaki", k=4)
    return m


# ================================================================================================ pigeon (one bird; the flock draws it N times)

def merpati_looks():
    looks = {}

    def look(name, badan, sayap, leher, ekor, kepala, iris="E07A20"):
        looks[name] = {"badan": badan, "sayap": sayap, "leher": leher, "ekor": ekor, "kepala": kepala, "paruh": satu("3A3430"),
                       "cere": satu("ECE8E0"), "kaki": satu("C86A70"), "mata": mata(iris, 4, "bulat", kilau=False, muka=SISI)}
    look("abu", bulu_burung("8A9098", 401), sayap_burung("9AA0A8", 402, "2E3036", "3A3C42"), kilau_leher(403),
         bulu_burung("6E747C", 404, tepi="2A2C30"), bulu_burung("6E747C", 405, sisik=False))
    look("putih", bulu_burung("ECEAE6", 411), sayap_burung("F2F0EC", 412, None, "D8D6D0"), bulu_bergradasi("F4F2EE", "E4E2DE", 413),
         bulu_burung("E8E6E2", 414), bulu_burung("F4F2EE", 415, sisik=False), iris="3A2418")
    look("coklat", bulu_burung("8A5E40", 421), sayap_burung("A0704A", 422, "4A2E1E", "F0E8DC"), kilau_leher(423),
         bulu_burung("6E4A32", 424, tepi="EDE5D8"), bulu_burung("7A5238", 425, sisik=False))
    return looks


def mesh_merpati():
    m = mesh("merpati", 64, merpati_looks(),
             "one pigeon of a flock (42.7.5): body 2 x 2 x 4, head 1.5 cube with beak and cere, iridescent neck, wings "
             "3 x 3.2 hinged at the shoulder (spread flat, folded by the renderer), fan tail, legs; looks abu/putih/coklat.")
    badan = m.part("badan", (0, 22.3, 0))
    badan.c(0, 0, 0, 2, 2, 4, "badan", k=4)
    badan.c(0, -0.4, -1.75, 1.75, 1.7, 1.1, "leher", k=4)
    kepala = badan.part("kepala", (0, -0.9, -1.9))
    kepala.c(0, -0.65, -0.3, 1.3, 1.3, 1.4, "kepala", k=4)
    kepala.c(0, -0.45, -1.25, 0.36, 0.28, 0.55, "paruh", k=4)
    kepala.c(0, -0.68, -1.05, 0.42, 0.2, 0.25, "cere", k=4)
    kepala.c(-0.67, -0.85, -0.5, 0.1, 0.36, 0.36, "mata", k=4)
    kepala.c(0.67, -0.85, -0.5, 0.1, 0.36, 0.36, "mata", k=4)
    badan.c(0, 0.35, -1.95, 1.8, 1.4, 0.4, "leher", k=4)       # breast
    for s, nm in ((-1, "sayap_k"), (1, "sayap_kn")):
        w = badan.part(nm, (s * 0.95, -0.85, -1.2))
        w.b(-3.0 if s < 0 else 0, 0, 0, 3.0, 0.25, 3.2, "sayap", k=4)
        w.b(-2.6 if s < 0 else 0.4, 0, 3.2, 2.2, 0.2, 0.8, "sayap", k=4)  # trailing primaries
    ekor = badan.part("ekor", (0, -0.3, 1.9), (deg(-8), 0, 0))
    ekor.b(-1.0, -0.25, 0, 2.0, 0.5, 2.2, "ekor", k=4)
    for nm, x in (("kaki_k", -0.45), ("kaki_kn", 0.45)):
        k_ = m.part(nm, (x, 23.3, 0.2))
        k_.b(-0.15, 0, -0.15, 0.3, 0.6, 0.3, "kaki", k=4)
        k_.b(-0.35, 0.6, -0.6, 0.7, 0.1, 0.8, "kaki", k=4)
    return m


# ================================================================================================ horse and donkey (one builder)

def kuda_looks():
    looks = {}

    def look(name, kulit_, surai, kaki_bawah, moncong, belang_dahi=None):
        looks[name] = {"badan": kulit_, "leher": kulit_, "kepala": kulit_, "surai": surai, "ekor": surai, "kaki": kulit_, "kaki_bawah": kaki_bawah,
                       "kuku": tanduk_kuku("3A3430"), "moncong": moncong, "mata": mata("3A2418", 4, "bulat", muka=SISI), "kelopak": kulit_,
                       "telinga": kulit_, "kekang": tali("5A2E1A", 501), "logam": A.logam("B8B4A8", 502, karat=False),
                       "pelana": tali("7A4A26", 503, "D8B070"), "alas_pelana": kain_tenun(["2E4A7A", "C8A050", "8A2A20"], 504),
                       "pita": satu("D8241C", 0.06), "bunga": A.kain("E8302A", 505), "emas": emas(506),
                       "dahi": belang_dahi or kulit_, "hidung": kulit("2A2220", 507, 0.15), "beban": karung("B89A68", 508),
                       "tali_beban": satu("6E4A2A")}
    look("coklat", bulu("8A4E28", 511, perut="A0683E", helai=0.07), rambut("1E1612", 512), bulu("231C18", 513, helai=0.06),
         bulu("5A3420", 514), bulu("ECE6DA", 515))
    look("hitam", bulu("201C1A", 521, helai=0.08), rambut("141210", 522, ujung="2A2420"), bulu("1A1716", 523, helai=0.06),
         bulu("2A2422", 524))
    look("abu_belang", bulu("8E8E8A", 531, perut="C8C8C2", pola=belang_bundar("C2C2BC", 532), helai=0.06), rambut("E0DED8", 533, ujung="8A8A84"),
         bulu("5A5A58", 534), bulu("4A4A48", 535))
    look("putih", bulu("ECE8DE", 541, helai=0.05), rambut("F4F0E6", 542, ujung="D8D2C4"), bulu("E0DBD0", 543), bulu("C8B8B0", 544))
    return looks


def keledai_looks():
    looks = {}

    def look(name, base, perut, stripe):
        def silang(fx, fy, fw, fh, face):
            # the donkey's dorsal stripe and shoulder cross
            if face == "top" and abs(fx - fw / 2.0) < 0.6:
                return rgb(stripe)
            return None
        k_ = bulu(base, 601 + len(looks), perut=perut, pola=silang, helai=0.10, kasar=True)
        looks[name] = {"badan": k_, "leher": bulu(base, 611 + len(looks), pola=silang, helai=0.1), "kepala": bulu(base, 621 + len(looks), helai=0.1),
                       "surai": rambut(stripe, 631 + len(looks)), "ekor": rambut(stripe, 641 + len(looks)),
                       "kaki": bulu(base, 651 + len(looks), helai=0.1), "kaki_bawah": bulu(perut, 661 + len(looks), helai=0.08),
                       "kuku": tanduk_kuku("4A4038"), "moncong": bulu("F0EAE0", 671), "mata": mata("2A1A10", 4, "bulat", muka=SISI),
                       "kelopak": bulu(base, 681 + len(looks)), "telinga": bulu(base, 691 + len(looks)),
                       "kekang": tali("6E4A2A", 701, "B89A68"), "logam": A.logam("8A8478", 702), "pelana": tali("6E4A2A", 703, "B89A68"),
                       "alas_pelana": kain_tenun(["6A5A44", "8A7A5A"], 704), "pita": satu("D8241C"), "bunga": A.kain("E8302A", 705),
                       "emas": emas(706), "dahi": bulu(base, 707), "hidung": kulit("3A3030", 708, 0.15), "beban": karung("B89A68", 709),
                       "tali_beban": satu("5A3A20")}
    look("abu", "8A847C", "D8D2C8", "3A342E")
    look("coklat", "7A5E46", "D8C8B0", "3A2A1E")
    return looks


def _kuda(m, keledai=False):
    """Horse (or donkey) mesh at the horse's design sizes (the donkey is drawn at 0.72 by its renderer)."""
    badan = m.part("badan", (0, 7, 0))
    badan.c(0, 0, 0, 10, 10, 22, "badan")
    badan.c(0, 0.5, -11.4, 8.6, 8.4, 1.0, "badan")   # chest
    badan.c(0, -0.2, 11.3, 9.0, 8.6, 1.0, "badan")   # rump
    # saddle: tethered riding horse (donkeys carry the pack saddle instead)
    if not keledai:
        pel = badan.part("pelana")
        pel.c(0, -5.4, -1.0, 7.0, 1.2, 8.0, "pelana")
        pel.c(0, -6.2, -4.6, 5.0, 1.2, 1.2, "pelana")    # pommel
        pel.c(0, -6.0, 2.8, 6.0, 1.0, 1.0, "pelana")     # cantle
        pel.b(-5.25, -5.0, -6.0, 0.25, 7.0, 10.0, "alas_pelana")
        pel.b(5.0, -5.0, -6.0, 0.25, 7.0, 10.0, "alas_pelana")
        pel.b(-5.5, -4.6, -1.4, 0.3, 9.6, 0.4, "kekang")   # stirrup leathers
        pel.b(5.2, -4.6, -1.4, 0.3, 9.6, 0.4, "kekang")
        pel.c(-5.6, 5.4, -1.2, 0.5, 1.2, 1.6, "logam")      # stirrups
        pel.c(5.6, 5.4, -1.2, 0.5, 1.2, 1.6, "logam")
        pel.b(-5.1, 1.6, -0.6, 10.2, 0.6, 1.0, "kekang")    # girth
    else:
        bb = badan.part("beban")
        bb.c(0, -5.4, -1.0, 8.0, 1.4, 9.0, "pelana")
        bb.c(0, -6.6, -1.0, 6.0, 1.2, 1.2, "pelana")
        bb.c(-6.2, -1.6, -1.0, 2.6, 7.0, 8.0, "beban")
        bb.c(6.2, -1.6, -1.0, 2.6, 7.0, 8.0, "beban")
        bb.c(0, -6.3, -1.0, 11.0, 0.4, 0.8, "tali_beban")
        bb.b(-5.1, 1.6, -1.4, 10.2, 0.6, 1.0, "kekang")
    # bridal finery (horse only, hias): red saddle cloth with gold border, ribbons on the crupper
    if not keledai:
        hias = badan.part("hias")
        hias.b(-5.65, -5.3, -6.6, 0.3, 8.0, 11.2, "pita")
        hias.b(5.35, -5.3, -6.6, 0.3, 8.0, 11.2, "pita")
        hias.b(-5.75, 2.3, -6.7, 0.45, 0.6, 11.4, "emas")
        hias.b(5.3, 2.3, -6.7, 0.45, 0.6, 11.4, "emas")
        hias.c(0, -5.2, 9.0, 6.0, 0.6, 0.8, "pita")
        for i, z in enumerate((5.0, 8.0)):
            hias.b(-5.3, -4.6, z, 0.3, 4.5, 0.8, "pita")
            hias.b(5.0, -4.6, z, 0.3, 4.5, 0.8, "pita")
    # neck 4 x 10 x 6 raked 30 degrees forward
    leher = badan.part("leher", (0, -2.5, -9.0), (deg(30), 0, 0))
    leher.b(-2.25, -12.0, -3.6, 4.5, 12.0, 6.0, "leher")
    leher.b(-3.0, -5.0, -4.0, 6.0, 7.0, 7.4, "leher")   # thick base of the neck
    if keledai:
        sk = leher.part("surai")
        sk.b(-0.6, -11.5, 1.6, 1.2, 11.0, 1.4, "surai")   # short stiff upright brush
    else:
        sk = leher.part("surai")
        for i in range(8):
            y0 = -11.5 + i * 1.35
            sk.b(-0.7, y0 - 0.2, 2.2, 1.4, 1.6, 1.0, "surai")   # crest
            x = 2.0 if i % 3 else -2.25                          # the mane falls mostly to the right
            sk.b(x, y0, 0.6 + (i % 3) * 0.2, 0.25, 3.6, 2.0, "surai")
    kepala = leher.part("kepala", (0, -11.4, -1.0), (deg(8) if not keledai else deg(12), 0, 0))
    kepala.b(-2.5, -2.6, -9.6, 5.0, 5.0, 10.0, "kepala")
    kepala.b(-2.1, -2.0, -11.4, 4.2, 4.2, 2.0, "moncong")
    kepala.c(0, 1.4, -7.0, 4.0, 1.2, 6.0, "kepala")    # jaw line
    kepala.c(-1.2, -0.9, -11.45, 0.9, 0.7, 0.1, "hidung", k=4)
    kepala.c(1.2, -0.9, -11.45, 0.9, 0.7, 0.1, "hidung", k=4)
    kepala.c(0, -2.7, -6.5, 1.6, 0.1, 5.0, "dahi", k=2)   # blaze / star
    kepala.c(-2.55, -1.2, -5.2, 0.1, 1.2, 1.3, "mata", k=4)
    kepala.c(2.55, -1.2, -5.2, 0.1, 1.2, 1.3, "mata", k=4)
    kel = kepala.part("kelopak")
    kel.c(-2.62, -1.2, -5.2, 0.1, 1.3, 1.4, "kelopak", k=4)
    kel.c(2.62, -1.2, -5.2, 0.1, 1.3, 1.4, "kelopak", k=4)
    rahang = kepala.part("rahang", (0, 2.0, -2.0))
    rahang.b(-1.9, 0, -9.2, 3.8, 1.2, 8.0, "kepala")
    rahang.b(-1.8, 0.1, -9.6, 3.6, 0.9, 0.6, "moncong")
    el, eh = (1.5, 5.4) if keledai else (1.3, 3.0)
    for s, nm in ((-1, "telinga_k"), (1, "telinga_kn")):
        t = kepala.part(nm, (s * 1.5, -2.4, -0.6), (deg(-10), 0, deg(12 * s)))
        t.c(0, -eh / 2.0, 0, el, eh, 1.0, "telinga")
    if not keledai:
        kepala.b(-1.4, -3.4, -1.2, 2.8, 1.2, 1.4, "surai")   # forelock root
        jumbai = kepala.part("jumbai", (0, -3.0, -1.6), (deg(-25), 0, 0))
        jumbai.b(-1.2, 0, -0.1, 2.4, 3.4, 0.15, "surai")
    # bridle: noseband, cheek straps, brow band, bit rings, rein to the neck
    kek = kepala.part("kekang")
    kek.b(-2.6, -2.2, -9.0, 5.2, 0.6, 0.6, "kekang")
    kek.b(-2.65, -2.8, -8.8, 0.3, 4.4, 0.5, "kekang")
    kek.b(2.35, -2.8, -8.8, 0.3, 4.4, 0.5, "kekang")
    kek.b(-2.6, -2.8, -0.9, 5.2, 0.5, 0.5, "kekang")
    kek.b(-2.65, -2.8, -8.8, 0.3, 0.5, 8.2, "kekang")
    kek.b(2.35, -2.8, -8.8, 0.3, 0.5, 8.2, "kekang")
    kek.c(-2.75, 0.9, -9.4, 0.3, 0.9, 0.9, "logam", k=2)
    kek.c(2.75, 0.9, -9.4, 0.3, 0.9, 0.9, "logam", k=2)
    if not keledai:
        bunga = kepala.part("bunga", (0, -3.2, -3.0))
        bunga.c(0, -1.4, 0, 2.6, 2.6, 2.6, "bunga", k=2)
        bunga.c(0, -1.4, 0, 3.2, 1.2, 1.2, "bunga", k=2)
        bunga.c(0, -1.4, 0, 1.2, 3.2, 1.2, "bunga", k=2)
        bunga.b(-1.6, -0.2, 0.4, 0.4, 4.0, 0.3, "pita", k=2)
        bunga.b(1.2, -0.2, 0.4, 0.4, 4.0, 0.3, "pita", k=2)
    # tail: dock plus two crossed hair cards (a donkey's rope tail with a tuft)
    ekor = badan.part("ekor", (0, -3.6, 11.4), (deg(28), 0, 0))
    ekor.b(-1.0, -0.8, -0.4, 2.0, 2.0, 3.2, "ekor")
    if keledai:
        ek2 = ekor.part("ekor_rambut", (0, 0.4, 2.6), (deg(-50), 0, 0))
        ek2.b(-0.5, 0, -0.5, 1.0, 8.0, 1.0, "ekor")
        ek2.b(-1.1, 7.0, -1.1, 2.2, 4.0, 2.2, "surai")
    else:
        ek2 = ekor.part("ekor_rambut", (0, 0.6, 2.6), (deg(-48), 0, 0))
        ek2.b(-1.8, 0, -0.08, 3.6, 13.0, 0.16, "ekor")
        ek2.b(-0.08, 0, -1.8, 0.16, 13.0, 3.6, "ekor")
        ek2.b(-1.1, -0.2, -1.1, 2.2, 5.0, 2.2, "ekor")
    # legs: forearm/gaskin, cannon with fetlock, hoof
    for nm, x, z, hind in (("kaki_dk", -3.2, -8.5, False), ("kaki_dkn", 3.2, -8.5, False), ("kaki_bk", -3.2, 8.6, True), ("kaki_bkn", 3.2, 8.6, True)):
        k_ = m.part(nm, (x, 11, z))
        if hind:
            k_.b(-2.0, -3.0, -2.6, 4.0, 7.0, 5.0, "kaki")
        else:
            k_.b(-1.8, -2.0, -2.0, 3.6, 7.0, 4.0, "kaki")
        b2 = k_.part("betis" + nm[4:], (0, 5.0, 0))
        b2.b(-1.5, 0, -1.5, 3.0, 6.0, 3.0, "kaki_bawah")
        b2.b(-1.65, 4.4, -1.7, 3.3, 1.2, 3.4, "kaki_bawah")   # fetlock
        b2.b(-1.7, 6.0, -1.9, 3.4, 2.0, 3.6, "kuku")
    return m


def mesh_kuda():
    m = mesh("kuda", 128, kuda_looks(),
             "tethered horse (42.7.5): body 10 x 10 x 22 with chest and rump, neck 4 x 11 x 6 raked 30 degrees, head 5 x 5 x 10 "
             "with muzzle, nostrils, blaze, eyes, eyelids, hinged jaw, ears, forelock, eight-card mane, bridle with bit rings, "
             "saddle with blanket, stirrups and girth, bridal finery (red cloth, gold border, ribbons, silk flower), crossed "
             "hair-card tail, hoofed legs in two segments; looks coklat/hitam/abu_belang/putih.")
    return _kuda(m)


def mesh_keledai():
    m = mesh("keledai", 128, keledai_looks(),
             "town donkey (42.7.5): the horse build with long ears, a short upright mane, a dorsal stripe and shoulder cross, "
             "a pale muzzle and belly, a rope tail with a tuft and a pack saddle with two sacks (shown when loaded); drawn at "
             "0.72; looks abu/coklat.")
    return _kuda(m, keledai=True)


# ================================================================================================ camel

def unta_looks():
    looks = {}

    def look(name, base, perut, lebat):
        looks[name] = {"badan": bulu(base, 801 + len(looks), perut=perut, helai=0.1), "punuk": bulu(base, 811 + len(looks), helai=0.12),
                       "jumbai": rumbai(lebat, 821 + len(looks), frayed=0.3), "leher": bulu(base, 831 + len(looks), helai=0.1),
                       "kepala": bulu(base, 841 + len(looks), helai=0.08), "bibir": kulit(perut, 851 + len(looks), 0.12),
                       "kaki": bulu(base, 861 + len(looks), helai=0.09), "lutut": kulit("5A4A3A", 871), "telapak": kulit("4A3E34", 872),
                       "mata": mata("2A1A10", 4, "bulat", muka=SISI), "kelopak": bulu(base, 881 + len(looks)), "telinga": bulu(base, 891 + len(looks)),
                       "hidung": kulit("3A2E26", 901, 0.15), "tali": tali("8A6A40", 902, "C8A870"), "lonceng": emas(903),
                       "kain": kain_tenun(["A8281E", "2A3A6A", "D8A848", "1E2A1E"], 904, 1.2, emas="F0D070"),
                       "rumbai": rumbai("D8A848", 905, tip="A8281E", frayed=0.35)}
    look("pasir", "C8A470", "E0C8A0", "A07A4A")
    look("coklat_tua", "7A5434", "A07A58", "4E3420")
    return looks


def mesh_unta():
    m = mesh("unta", 128, unta_looks(),
             "tethered camel (42.7.5): body 10 x 12 x 22, two humps 6 x 6 x 6 with shaggy crowns, S-curved two-segment neck "
             "with throat mane, head with split lip, nostrils, side-chewing jaw, eyes with lashes, ears, rope halter with a bell, "
             "kilim saddle cloth with tassels between the humps, long legs of thigh, shin with calloused knee pad and wide "
             "foot pad; looks pasir/coklat_tua.")
    badan = m.part("badan", (0, 3.0, 0))
    badan.c(0, 0, 0, 10, 12, 22, "badan")
    badan.c(0, 1.0, -11.4, 8.4, 9.0, 1.0, "badan")
    badan.c(0, 0.6, 11.4, 8.6, 9.6, 1.0, "badan")
    for i, z in enumerate((-6.0, 6.0)):
        pk = badan.part("punuk_%d" % i, (0, -6.0, z))
        pk.g(0, 0, 0, 6, 6, 6, "punuk")
        pk.g(0, -6.0, 0, 4.0, 1.6, 4.0, "punuk")
        pk.g(0, -6.6, 0, 4.6, 1.4, 4.6, "jumbai")
    kain = badan.part("kain")
    kain.c(0, -6.2, 0, 8.0, 0.4, 5.4, "kain", k=2)
    kain.b(-5.3, -6.0, -2.7, 0.3, 8.0, 5.4, "kain", k=2)
    kain.b(5.0, -6.0, -2.7, 0.3, 8.0, 5.4, "kain", k=2)
    kain.b(-5.4, 2.0, -2.7, 0.2, 1.6, 5.4, "rumbai", k=2)
    kain.b(5.2, 2.0, -2.7, 0.2, 1.6, 5.4, "rumbai", k=2)
    kain.b(-5.2, 3.2, -0.6, 10.4, 0.6, 1.2, "tali")
    # S neck: the lower segment reaches forward and down from the chest, the upper rises to the head
    l1 = badan.part("leher_1", (0, -1.0, -11.0), (deg(112), 0, 0))
    l1.b(-2.2, -9.0, -2.2, 4.4, 9.0, 4.4, "leher")
    l1.b(-2.4, -8.0, 1.6, 4.8, 7.0, 1.6, "jumbai")
    l2 = l1.part("leher_2", (0, -8.4, 0), (deg(-100), 0, 0))
    l2.b(-1.9, -13.0, -1.9, 3.8, 13.0, 3.8, "leher")
    l2.b(-2.1, -12.0, -2.6, 4.2, 8.0, 1.0, "jumbai")
    kepala = l2.part("kepala", (0, -12.6, 0), (deg(-12), 0, 0))
    kepala.b(-2.4, -2.6, -8.0, 4.8, 4.8, 9.0, "kepala")
    kepala.b(-1.8, -1.6, -10.0, 3.6, 3.0, 2.2, "bibir")
    kepala.c(0, -1.0, -10.05, 0.4, 2.0, 0.1, "hidung", k=4)
    kepala.c(-1.0, -1.2, -10.05, 0.7, 0.4, 0.1, "hidung", k=4)
    kepala.c(1.0, -1.2, -10.05, 0.7, 0.4, 0.1, "hidung", k=4)
    kepala.c(-2.45, -1.2, -4.6, 0.1, 1.2, 1.4, "mata", k=4)
    kepala.c(2.45, -1.2, -4.6, 0.1, 1.2, 1.4, "mata", k=4)
    kel = kepala.part("kelopak")
    kel.c(-2.52, -1.25, -4.6, 0.1, 1.3, 1.5, "kelopak", k=4)
    kel.c(2.52, -1.25, -4.6, 0.1, 1.3, 1.5, "kelopak", k=4)
    kepala.b(-2.6, -2.9, -6.0, 5.2, 0.6, 5.0, "jumbai")   # brow tuft
    rahang = kepala.part("rahang", (0, 2.0, -1.0))
    rahang.b(-1.6, 0, -8.0, 3.2, 1.4, 8.0, "bibir")
    for s, nm in ((-1, "telinga_k"), (1, "telinga_kn")):
        t = kepala.part(nm, (s * 1.9, -2.4, -0.4), (deg(-20), 0, deg(35 * s)))
        t.c(0, -1.2, 0, 1.4, 2.4, 0.9, "telinga", k=2)
    hal = kepala.part("tali_kepala")
    hal.b(-2.7, -1.0, -7.4, 5.4, 0.7, 0.7, "tali")
    hal.b(-2.7, -2.9, -1.6, 5.4, 0.7, 0.7, "tali")
    hal.b(-2.75, -2.9, -7.4, 0.35, 0.6, 6.4, "tali")
    hal.b(2.4, -2.9, -7.4, 0.35, 0.6, 6.4, "tali")
    hal.c(0, 3.4, -4.0, 1.6, 1.6, 1.6, "lonceng", k=2)
    ekor = badan.part("ekor", (0, -4.0, 11.6), (deg(15), 0, 0))
    ekor.b(-0.9, 0, -0.6, 1.8, 9.0, 1.2, "badan")
    ekor.b(-1.2, 7.5, -0.9, 2.4, 3.5, 1.8, "jumbai")
    for nm, x, z in (("kaki_dk", -3.2, -8.4), ("kaki_dkn", 3.2, -8.4), ("kaki_bk", -3.2, 8.4), ("kaki_bkn", 3.2, 8.4)):
        k_ = m.part(nm, (x, 8.0, z))
        k_.b(-2.0, -2.0, -2.0, 4.0, 9.0, 4.0, "kaki")
        b2 = k_.part("betis" + nm[4:], (0, 7.0, 0))
        b2.b(-1.5, 0, -1.5, 3.0, 8.0, 3.0, "kaki")
        b2.c(0, 0.2, -1.3, 3.2, 2.4, 1.2, "lutut")
        b2.b(-2.0, 7.6, -2.6, 4.0, 1.4, 4.6, "telapak")
        b2.b(-2.1, 8.4, -2.9, 1.6, 0.6, 1.2, "telapak")
        b2.b(0.5, 8.4, -2.9, 1.6, 0.6, 1.2, "telapak")
    return m


# ================================================================================================ lion dance (barongsai)

def barongsai_looks():
    looks = {}

    def look(name, base, gold, accent, fur, fur_tip, celana, alis):
        looks[name] = {
            "topeng": topeng(base, gold, accent, 1000 + len(looks)), "emas": emas(1010 + len(looks)),
            "bulu": rumbai(fur, 1020 + len(looks), tip=fur_tip, frayed=0.35), "bulu_padat": bulu(fur, 1030 + len(looks), helai=0.16),
            "alis": rumbai(alis, 1040 + len(looks), frayed=0.25), "janggut": rumbai(fur, 1050 + len(looks), tip=fur_tip, frayed=0.5),
            "mata": mata("1A1A1A", 2, "bulat", tepi="F0C850"), "putih_mata": satu("F4F0E8"),
            "kelopak": topeng(base, gold, accent, 1060 + len(looks)), "bulu_mata": rumbai("1A1414", 1065, frayed=0.2),
            "cermin": cermin(), "tanduk": topeng(gold, base, accent, 1070 + len(looks)),
            "hidung": topeng(gold, base, accent, 1080 + len(looks)), "mulut": satu("6A0E10", 0.08), "lidah": satu("D84A5A", 0.06),
            "gigi": gigi(), "sisik": sisik_kain(base, gold, accent, 1090 + len(looks)),
            "rumbai": rumbai(fur, 1100 + len(looks), tip=fur_tip, frayed=0.5),
            "celana": A.kain(celana, 1110 + len(looks)), "celana_bulu": rumbai(fur, 1120 + len(looks), frayed=0.4),
            "sepatu": kulit("1E1A18", 1130, 0.12), "cakar": satu("F2ECDC"),
            "selada": daun_selada(1140), "angpao": angpao(1141),
        }
    # Liu Bei's gold-and-red lion with white fur
    look("merah_emas", "C8261C", "F0C850", "2E7A3A", "F6F2EA", "E8D8B0", "C8261C", "F6F2EA")
    # Zhao Yun's green lion with cream fur
    look("hijau", "2E7A3A", "F0C850", "C8261C", "F2E6C0", "D8C890", "2E6A34", "F2E6C0")
    # Zhang Fei's black lion with white fur and red accents
    look("hitam", "1E1C1E", "D8D8D0", "C8261C", "F4F2EE", "B8B8B0", "1E1C1E", "F4F2EE")
    return looks


def mesh_barongsai():
    m = mesh("barongsai", 128, barongsai_looks(),
             "lion dance costume (42.12.4): head 12 x 10 x 14 (lacquer with gold cloud scrolls, hinged lower jaw with teeth, "
             "tongue and beard, round eyes with two-frame eyelids and lashes, fur brows, forehead mirror, horn, nose, ears, "
             "fur trim quads), five-segment scaled cloth body 10 x 8 x 24 with fur fringes and a spine ridge, fur tail, four "
             "trousered legs of the two dancers with fur cuffs and clawed boots, lettuce with a red packet (cai qing) and four "
             "spat leaves; looks merah_emas/hijau/hitam.")
    # head (held by the front dancer): pivot at his hands, the head reaches forward
    kp = m.part("kepala", (0, 8.0, -12.0))
    kp.b(-6.0, -6.0, -9.0, 12.0, 6.0, 12.0, "topeng", k=2)       # skull
    kp.b(-5.0, -8.0, -7.0, 10.0, 2.0, 8.0, "topeng", k=2)        # dome
    kp.b(-5.5, 0.0, -8.0, 11.0, 2.0, 10.0, "topeng", k=2)        # upper jaw
    kp.b(-5.0, 0.3, -7.6, 10.0, 1.2, 9.0, "mulut", k=2)          # mouth inside
    kp.b(-4.8, 1.8, -8.1, 9.6, 0.8, 0.6, "gigi", k=4)            # upper teeth
    kp.b(-6.6, -2.5, -9.5, 1.4, 4.0, 5.0, "emas", k=2)           # gold cheeks
    kp.b(5.2, -2.5, -9.5, 1.4, 4.0, 5.0, "emas", k=2)
    kp.b(-2.2, -3.4, -11.0, 4.4, 3.4, 2.4, "hidung", k=2)        # big nose
    kp.c(0, -6.4, -9.15, 3.2, 3.2, 0.3, "cermin", k=4)           # forehead mirror
    tk = kp.part("tanduk", (0, -8.0, -5.0), (deg(-25), 0, 0))
    tk.g(0, 0, 0, 2.0, 3.6, 2.0, "tanduk", k=2)
    tk.g(0, -3.6, 0, 1.2, 1.2, 1.2, "emas", k=4)
    for s in (-1, 1):
        sx = "k" if s < 0 else "kn"
        kp.c(s * 3.0, -3.6, -9.05, 3.2, 3.0, 0.2, "putih_mata", k=4)
        kp.c(s * 3.0, -3.6, -9.2, 2.6, 2.6, 0.2, "mata", k=4)
        kl = kp.part("kelopak_" + sx, (s * 3.0, -5.2, -9.3))
        kl.b(-1.7, 0, -0.3, 3.4, 3.4, 0.3, "kelopak", k=4)
        kl.b(-1.8, 3.1, -0.5, 3.6, 0.6, 0.2, "bulu_mata", k=4)
        al = kp.part("alis_" + sx, (s * 3.0, -5.6, -9.4), (deg(-10), 0, deg(8 * s)))
        al.b(-2.4, -1.6, -0.3, 4.8, 1.8, 1.0, "alis", k=2)
        al.b(-2.2, -0.2, -0.5, 4.4, 1.0, 0.2, "alis", k=2)
        te = kp.part("telinga_" + sx, (s * 5.2, -5.6, -1.0), (0, 0, deg(20 * s)))
        te.c(s * 1.2, -1.0, 0, 2.6, 2.4, 0.6, "topeng", k=2)
        te.c(s * 1.4, -2.4, 0, 2.8, 1.2, 0.8, "bulu", k=2)
    # fur trim around the head: crown, sides, back rim (quads of fur)
    trim = kp.part("bulu_kepala")
    trim.b(-6.2, -8.6, -2.4, 12.4, 2.4, 0.4, "bulu", k=2)
    trim.b(-6.4, -6.4, -1.0, 0.4, 7.0, 4.0, "bulu", k=2)
    trim.b(6.0, -6.4, -1.0, 0.4, 7.0, 4.0, "bulu", k=2)
    trim.b(-6.0, -6.4, 2.8, 12.0, 8.0, 0.4, "bulu_padat", k=1)
    trim.b(-6.6, -1.2, -9.6, 1.0, 3.6, 0.3, "bulu", k=2)
    trim.b(5.6, -1.2, -9.6, 1.0, 3.6, 0.3, "bulu", k=2)
    rh = kp.part("rahang", (0, 2.0, 1.0))
    rh.b(-5.0, 0, -9.0, 10.0, 2.0, 9.0, "topeng", k=2)
    rh.b(-4.6, -0.3, -8.6, 9.2, 0.4, 8.0, "mulut", k=2)
    rh.b(-4.4, -0.9, -8.9, 8.8, 0.7, 0.5, "gigi", k=4)
    rh.b(-2.0, -0.6, -7.6, 4.0, 0.4, 5.0, "lidah", k=2)
    jg = rh.part("janggut", (0, 2.0, -8.0), (deg(10), 0, 0))
    jg.b(-4.5, 0, -0.3, 9.0, 4.0, 0.3, "janggut", k=2)
    jg.b(-3.5, 0, 0.6, 7.0, 3.0, 0.3, "janggut", k=2)
    sel = kp.part("selada", (0, 1.4, -10.4))
    sel.c(0, 0, 0, 3.4, 2.6, 2.6, "selada", k=2)
    sel.c(0, -0.8, -0.8, 4.2, 1.2, 1.6, "selada", k=2)
    sel.c(0, 1.6, 0.2, 1.8, 2.2, 0.3, "angpao", k=4)
    for i in range(4):
        d = kp.part("daun_%d" % i, (0, 1.0, -10.0))
        d.c(0, 0, 0, 1.4, 0.2, 1.2, "selada", k=4, share="daun")
    # body: five chained cloth segments from the back of the head to the tail dancer
    prev = m.root
    piv = (0, 4.2, -5.6)
    seg_len = 4.8
    for i in range(5):
        kn = prev.part("kain_%d" % i, piv)
        w = 10.4 - (0.4 if i in (0, 4) else 0.0)
        kn.b(-w / 2, 0, 0, w, 7.0, seg_len, "sisik", k=2, share="kain")
        kn.b(-w / 2 - 0.1, 7.0, 0, 0.1, 2.2, seg_len, "rumbai", k=2, share="rumbai_k")
        kn.b(w / 2, 7.0, 0, 0.1, 2.2, seg_len, "rumbai", k=2, share="rumbai_kn")
        kn.b(-0.6, -1.2, 0, 1.2, 1.4, seg_len, "bulu_padat", k=2, share="duri")
        prev = kn
        piv = (0, 0, seg_len)
    ekor = prev.part("ekor", (0, 0.5, seg_len), (deg(-35), 0, 0))
    ekor.b(-1.8, -1.8, 0, 3.6, 3.6, 4.0, "bulu_padat", k=2)
    ekor.b(-2.4, -2.4, 3.6, 4.8, 4.8, 2.2, "bulu", k=2)
    # the dancers' legs: front pair under the head, back pair under the last segment
    for nm, x, z in (("kaki_dk", -2.6, -8.0), ("kaki_dkn", 2.6, -8.0), ("kaki_bk", -2.6, 11.0), ("kaki_bkn", 2.6, 11.0)):
        k_ = m.part(nm, (x, 12.0, z))
        k_.b(-2.2, 0, -2.2, 4.4, 6.2, 4.4, "celana", k=2)
        b2 = k_.part("betis" + nm[4:], (0, 6.0, 0))
        b2.b(-2.0, 0, -2.0, 4.0, 4.6, 4.0, "celana", k=2)
        b2.b(-2.5, 3.0, -2.5, 5.0, 1.8, 5.0, "celana_bulu", k=2)
        b2.b(-2.2, 4.4, -3.0, 4.4, 1.6, 5.2, "sepatu", k=2)
        b2.b(-2.0, 5.2, -3.6, 1.0, 0.6, 0.8, "cakar", k=4)
        b2.b(-0.5, 5.2, -3.6, 1.0, 0.6, 0.8, "cakar", k=4)
        b2.b(1.0, 5.2, -3.6, 1.0, 0.6, 0.8, "cakar", k=4)
    return m


# ================================================================================================ java

def write_java():
    body = "\n\n".join(A.java_mesh_method(m) for m in OURS)
    names = ", ".join("{@link #%s()}" % m.java for m in OURS)
    src = """package dev.kultivator.client.dunia.kota42.model.hewan;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by {@code tools/gen_kota_hidup_hewan.py} (42V-H, DESIGN 42.12.4): do not edit, change the mesh in the
 * generator and run it again (it also repaints the matching atlases under {@code textures/entity/kota42/hewan/}, whose
 * box-UV regions are packed from these very boxes). Mob model space: y down, 24 = the ground, front = -z. The renderers of
 * {@code client/dunia/kota42/render/hewan} look parts up by name ({@code ModelPart.createPartLookup}) and animate them.
 * <p>
 * Meshes: %s.
 */
public final class HewanMesh {

%s

	private HewanMesh() {
	}
}
""" % (names, body)
    os.makedirs(os.path.dirname(JAVA_MESH), exist_ok=True)
    with open(JAVA_MESH, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
    WRITTEN.append(os.path.relpath(JAVA_MESH, REPO).replace("\\", "/"))


# ================================================================================================ main

R = deg
PREVIEW = {
    "kucing": [("berdiri", set(), {}),
               ("tidur", {"lidah"}, {"kaki_dk": {"rot": (R(-80), 0, 0)}, "kaki_dkn": {"rot": (R(-80), 0, 0)}, "kaki_bk": {"rot": (R(80), 0, 0)},
                                     "kaki_bkn": {"rot": (R(80), 0, 0)}, "badan": {"pivot": (0, 21.5, 0)},
                                     "kepala": {"pivot": (0, -0.6, -4.2), "rot": (R(20), R(60), 0)},
                                     "ekor_1": {"pivot": (0, 0.8, 4.3), "rot": (R(-5), R(-80), 0)}, "ekor_2": {"pivot": (0, 0, 4.8), "rot": (0, R(-70), 0)}})],
    "anjing": [("berdiri", {"bulu_lebat"}, {}), ("gonggong", {"bulu_lebat"}, {"rahang": {"pivot": (0, 0.4, -3.9), "rot": (R(20), 0, 0)}})],
    "ayam": [("berdiri", {"jengger_kecil", "ekor_betina"}, {}), ("betina", {"jengger", "ekor_jago", "bulu_leher", "pial"}, {})],
    "merpati": [("terbang", set(), {}), ("hinggap", set(), {"sayap_k": {"pivot": (-0.95, -0.85, -1.2), "rot": (0, 0, R(-84))},
                                                           "sayap_kn": {"pivot": (0.95, -0.85, -1.2), "rot": (0, 0, R(84))}})],
    "kuda": [("berpelana", {"hias", "bunga"}, {}), ("pengantin", set(), {})],
    "keledai": [("beban", set(), {})],
    "unta": [("berdiri", set(), {})],
    "barongsai": [("berdiri", {"selada"} | {"daun_%d" % i for i in range(4)} | {"kelopak_k", "kelopak_kn"}, {}),
                  ("buka_mulut", {"daun_%d" % i for i in range(4)}, {"rahang": {"pivot": (0, 2.0, 1.0), "rot": (R(28), 0, 0)}})],
}
# the parts a look hides in its own preview (the renderers toggle the same parts per variant)
LOOK_HIDE = {
    ("kucing", None): {"kelopak", "lidah"},
    ("anjing", None): {"kelopak", "bulu_lebat"},
    ("anjing", "penjaga"): {"kelopak"},
    ("ayam", "betina"): {"jengger", "ekor_jago", "bulu_leher", "pial"},
    ("ayam", "anak"): {"jengger", "jengger_kecil", "ekor_jago", "ekor_betina", "bulu_leher", "pial"},
    ("ayam", None): {"jengger_kecil", "ekor_betina"},
    ("kuda", None): {"kelopak", "hias", "bunga"},
    ("keledai", None): {"kelopak"},
    ("unta", None): {"kelopak"},
}


def look_hide(mesh_name, look):
    return LOOK_HIDE.get((mesh_name, look), LOOK_HIDE.get((mesh_name, None), set()))


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    for b in (mesh_kucing, mesh_anjing, mesh_ayam, mesh_merpati, mesh_kuda, mesh_keledai, mesh_unta, mesh_barongsai):
        b()
    for m in OURS:
        A.pack(m)
    tex = {}
    for m in OURS:
        looks = list(m.looks)
        for look in looks:
            img = A.paint(m, look)
            path = os.path.join(TEX_DIR, "%s_%s.png" % (m.name, look))
            os.makedirs(TEX_DIR, exist_ok=True)
            img.save(path)
            WRITTEN.append(os.path.relpath(path, REPO).replace("\\", "/"))
            tex["%s_%s" % (m.name, look)] = img
            if preview_dir:
                poses = PREVIEW.get(m.name, [("semua", set(), {})])
                for tag, hidden, over in (poses if look == looks[0] else poses[:1]):
                    hidden = hidden | look_hide(m.name, look)
                    if tag == "tidur":
                        hidden = hidden - {"kelopak"}
                    A.preview(m, look, os.path.join(preview_dir, "h_%s_%s_%s.png" % (m.name, look, tag)), hidden=hidden, overrides=over)
                A.big(img, 4 if m.width <= 64 else 3).save(os.path.join(preview_dir, "t_%s_%s.png" % (m.name, look)))
    write_java()
    boxes = sum(len(m.boxes()) for m in OURS)
    print("hewan meshes %d (%d boxes, %d parts), textures %d, written %d, errors %d"
          % (len(OURS), boxes, sum(len(m.parts) for m in OURS), len(tex), len(WRITTEN), len(ERRORS)))
    for m in OURS:
        print("  %-10s %4d x %-4d boxes %4d parts %3d looks %s" % (m.name, m.width, m.height, len(m.boxes()), len(m.parts), ",".join(m.looks)))
    for e in ERRORS:
        print("ERROR:", e)
    if ERRORS:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

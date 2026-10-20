"""Generates the far silhouettes of the giant decree weapons (DESIGN 29.16.10, Tingkat Titah module C). Everything here is ours.

Writes (64 x 16, drawn along the strip: u = 0 at the weapon's lowest point, u = 1 at its top; v across it):
  textures/fx/titah/sword_far.png    the Heaven-Splitting Sword point-first: a white-gold blade with a bright edge line,
                                     the gold guard, grip and pommel, soft glowing edges
  textures/fx/titah/pillar_far.png   the Sea-Stabilising Pillar: an ember-gold column with gold hoops at both ends

They are drawn as additive quads by FarRelic (LOD 3: Minimal, shader packs, the frame guard), so the colours are bright
and the edges fade to transparent.

Run with: python tools/gen_titah_far_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures", "fx", "titah")
W, H = 64, 16


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def put(img, x, y, colour, alpha):
    if 0 <= x < W and 0 <= y < H and alpha > 0:
        a = int(round(max(0.0, min(1.0, alpha)) * 255))
        old = img.get(x, y)
        if a > old[3]:
            img.set(x, y, tuple(colour) + (a,))


def band(img, x, half, core, edge, glow=1.6):
    """One column of the silhouette: solid inside |dy| <= half, fading out over `glow` px beyond it."""
    centre = (H - 1) / 2.0
    for y in range(H):
        d = abs(y - centre)
        if d <= half:
            t = d / max(0.5, half)
            put(img, x, y, mix(core, edge, t * t), 1.0)
        elif d <= half + glow:
            put(img, x, y, edge, 0.55 * (1.0 - (d - half) / glow))


def sword():
    img = Image(W, H)
    white, gold, gold_dark, edge = rgb("FFF8E6"), rgb("FFD54A"), rgb("B8862A"), rgb("FFE9A8")
    for x in range(W):
        if x < 44:
            # the blade: from the point (x = 0) widening to 2.6 px half-width, a bright line down its middle
            half = 0.4 + 2.2 * math.sqrt(x / 44.0)
            band(img, x, half, white, edge)
        elif x < 47:
            # the guard: wide gold wings
            band(img, x, 6.2, gold, gold_dark, 1.2)
        elif x < 58:
            # the grip, wrapped (darker every other pixel)
            band(img, x, 1.4, gold if x % 2 == 0 else gold_dark, gold_dark, 1.0)
        else:
            # the pommel: a round gold knob
            r = 2.8 - 0.5 * abs(x - 60.5)
            band(img, x, max(0.8, r), gold, gold_dark, 1.2)
    return img


def pillar():
    img = Image(W, H)
    ember, gold, hot, dark = rgb("FF8A2A"), rgb("FFD54A"), rgb("FFF0C0"), rgb("A8401A")
    for x in range(W):
        hoop = x < 6 or x >= 58 or 28 <= x < 31
        if hoop:
            band(img, x, 5.6, gold, rgb("C89A2A"), 1.4)
        else:
            # the column: a hot core line, ember sides
            band(img, x, 4.6, hot if 20 <= x % 24 <= 21 else mix(hot, ember, 0.35), dark, 1.6)
    return img


def save(img, name):
    os.makedirs(ROOT, exist_ok=True)
    img.save(os.path.join(ROOT, name + ".png"))


def main():
    save(sword(), "sword_far")
    save(pillar(), "pillar_far")
    print("titah far textures: 2")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Generates the HUD textures of the sword duel (DESIGN 35.9 module U) under assets/kultivator/textures/gui/duel/.

  posture_frame.png  128x12  the lacquered frame of a Posture bar: dark lacquer, a thin gold inner line, a diamond
                             notch at the centre (the bar grows from the middle outward, as in Sekiro) and pointed caps
  posture_fill.png   128x16  two fills, drawn from the centre outward: rows 0-7 the amber of a calm stance (a brushed
                             streak texture, light on top), rows 8-15 the crimson of a stance about to break
  bind_meter.png     128x32  the Blade Lock meter: rows 0-9 the track (vermilion on the left, indigo on the right, a gold
                             rim), at (0,12) a 20x20 taiji disc that slides with the pressure, at (24,12) a 20x20 glow ring
  seal_marks.png     64x32   two blank vermilion seal stamps, 32x32 each (square at u=0, round at u=32) with worn ink
                             edges; the HUD writes the kanji (danger / decide) on them with the game font

Run with: python tools/gen_duel_ui_textures.py [preview_dir]
Deterministic (seeded noise); no dependencies besides tools/png.py.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures", "gui", "duel")


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def posture_frame():
    w, h = 128, 12
    img = Image(w, h)
    lacquer = rgb(0x1C0F0A, 235)
    lacquer_hi = rgb(0x3A2014, 235)
    gold = rgb(0xD4A637)
    gold_dark = rgb(0x8A671B)
    inside = rgb(0x0A0604, 150)
    for x in range(w):
        # the pointed caps: the frame narrows over the last 6 px of each end
        edge = min(x, w - 1 - x)
        inset = max(0, 6 - edge) // 2 if edge < 6 else 0
        for y in range(inset, h - inset):
            top = y == inset
            bottom = y == h - 1 - inset
            if top or bottom or edge <= inset:
                img.set(x, y, mix(lacquer_hi, lacquer, 0.0 if top else 1.0))
            elif y == inset + 1 or y == h - 2 - inset:
                img.set(x, y, gold if (x + y) % 9 else gold_dark)
            else:
                img.set(x, y, inside)
    # the centre notch: a small gold diamond over the frame
    cx, cy = w // 2, h // 2
    for dy in range(-4, 5):
        half = 4 - abs(dy)
        for dx in range(-half, half + 1):
            ring = abs(dx) == half
            img.set(cx + dx, cy + dy, gold if ring else rgb(0x6E160F, 255))
    # quarter ticks
    for q in (32, 96):
        for y in (2, h - 3):
            img.set(q, y, gold_dark)
    return img


def posture_fill():
    w, h = 128, 16
    img = Image(w, h)
    rnd = random.Random(35)
    streak = [rnd.random() for _ in range(w)]
    calm_top, calm_bottom = rgb(0xFFD27A), rgb(0xC9711B)
    hot_top, hot_bottom = rgb(0xFF6A4A), rgb(0x8E120C)
    for band, (top, bottom) in enumerate(((calm_top, calm_bottom), (hot_top, hot_bottom))):
        for y in range(8):
            t = y / 7.0
            base = mix(top, bottom, t)
            for x in range(w):
                # brushed streaks along the bar, a bright core line, darker edges
                s = (streak[x] - 0.5) * 0.18 + (streak[(x * 7 + y * 3) % w] - 0.5) * 0.08
                c = mix(base, rgb(0xFFFFFF) if s > 0 else rgb(0x000000), abs(s))
                if y == 1:
                    c = mix(c, rgb(0xFFF4D0), 0.45)
                if y == 7:
                    c = mix(c, rgb(0x000000), 0.35)
                img.set(x, band * 8 + y, c)
    return img


def bind_meter():
    img = Image(128, 32)
    gold = rgb(0xD4A637)
    vermilion = rgb(0xC0302A, 230)
    indigo = rgb(0x2A3A8A, 230)
    for x in range(128):
        t = x / 127.0
        c = mix(vermilion, indigo, (t - 0.35) / 0.3)
        for y in range(10):
            if y == 0 or y == 9:
                img.set(x, y, gold)
            elif y == 1 or y == 8:
                img.set(x, y, mix(c, rgb(0x000000), 0.4))
            else:
                img.set(x, y, mix(c, rgb(0xFFFFFF), 0.15 if y == 2 else 0.0))
    # centre line
    for y in range(10):
        img.set(63, y, gold)
        img.set(64, y, gold)
    # the taiji disc at (0, 12), 20 x 20
    r = 9.5
    cx, cy = 10.0, 22.0
    for y in range(12, 32):
        for x in range(0, 20):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            if d > r - 1.2:
                img.set(x, y, gold)
                continue
            # the classic S: white on the left half, black on the right, with the two small heads
            upper = math.hypot(px, py + r / 2)
            lower = math.hypot(px, py - r / 2)
            white = px < 0
            if upper < r / 2:
                white = True
            if lower < r / 2:
                white = False
            if upper < r / 7:
                white = False
            if lower < r / 7:
                white = True
            img.set(x, y, rgb(0xF4ECD8) if white else rgb(0x14100C))
    # the glow ring at (24, 12), 20 x 20 (white; tinted when drawn)
    cx = 34.0
    for y in range(12, 32):
        for x in range(24, 44):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            a = math.exp(-((d - 7.5) ** 2) / 3.0)
            if a > 0.02:
                img.set(x, y, (255, 255, 255, int(255 * a)))
    return img


def seal_marks():
    img = Image(64, 32)
    rnd = random.Random(1950)
    red = rgb(0xB3281D)
    dark = rgb(0x6E160F)
    for kind in range(2):
        ox = kind * 32
        for y in range(32):
            for x in range(32):
                px, py = x + 0.5 - 16, y + 0.5 - 16
                if kind == 0:
                    inside = max(abs(px), abs(py)) <= 14.0
                    rim = max(abs(px), abs(py)) >= 12.0
                else:
                    d = math.hypot(px, py)
                    inside = d <= 14.5
                    rim = d >= 12.5
                if not inside:
                    continue
                worn = rnd.random()
                if rim:
                    # the rim of the stamp, with gaps where the ink did not take
                    if worn < 0.12:
                        continue
                    img.set(ox + x, y, mix(red, dark, worn * 0.4))
                else:
                    # the field: lighter ink with speckles, leaving room for the white kanji
                    a = 210 if worn > 0.08 else 120
                    c = mix(red, rgb(0xE04A30), (worn - 0.5) * 0.3)
                    img.set(ox + x, y, (c[0], c[1], c[2], a))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    images = {
        "posture_frame.png": posture_frame(),
        "posture_fill.png": posture_fill(),
        "bind_meter.png": bind_meter(),
        "seal_marks.png": seal_marks(),
    }
    for name, img in images.items():
        img.save(os.path.join(OUT, name))
        if preview:
            os.makedirs(preview, exist_ok=True)
            big = Image(img.width * 6, img.height * 6, (40, 40, 40, 255))
            for y in range(big.height):
                for x in range(big.width):
                    big.blend(x, y, img.get(x // 6, y // 6))
            big.save(os.path.join(preview, name))
    print("wrote", ", ".join(images))


if __name__ == "__main__":
    main()

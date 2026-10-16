"""Generates the mod icon and the advancement tab background."""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")


def icon():
    size = 128
    img = Image(size, size)
    c = size / 2 - 0.5
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d < 60:
                t = d / 60
                # deep ink-blue orb with a jade glow in the middle
                r = 10 + 40 * (1 - t) ** 2
                g = 24 + 150 * (1 - t) ** 3
                b = 52 + 110 * (1 - t) ** 2
                img.set(x, y, (r, g, b, 255))
            if 54 <= d < 60:
                k = 1 - abs(d - 57) / 3
                img.blend(x, y, (255, 205, 80, int(255 * max(0, k))))
            if 58 <= d < 62:
                img.blend(x, y, (120, 80, 20, int(160 * max(0, 1 - abs(d - 60) / 2))))
    # a sword standing in the orb
    for y in range(22, 102):
        half = 2.5 if y < 80 else 1.5
        if y < 30:
            half = (y - 22) / 8 * 2.5
        for x in range(int(c - 4), int(c + 5)):
            dx = abs(x - c)
            if dx <= half:
                shade = 235 - dx * 25
                img.set(x, y, (shade, shade + 10, 255, 255))
    for y in range(80, 85):
        for x in range(int(c - 14), int(c + 15)):
            img.set(x, y, (230, 180, 60, 255))
    for y in range(85, 102):
        for x in range(int(c - 2), int(c + 3)):
            img.set(x, y, (120, 60, 30, 255))
    for y in range(102, 107):
        for x in range(int(c - 4), int(c + 5)):
            img.set(x, y, (230, 180, 60, 255))
    # qi motes
    rnd = random.Random(7)
    for _ in range(40):
        a = rnd.random() * math.tau
        r = 20 + rnd.random() * 30
        x = int(c + math.cos(a) * r)
        y = int(c + math.sin(a) * r)
        img.blend(x, y, (180, 255, 230, 200))
    img.save(os.path.join(ROOT, "icon.png"))


def background():
    img = Image(16, 16)
    rnd = random.Random(3)
    for y in range(16):
        for x in range(16):
            n = rnd.random() * 10
            img.set(x, y, (18 + n, 46 + n * 2, 52 + n, 255))
    # faint cloud swirls
    for x in range(16):
        y = int(8 + 3 * math.sin(x / 16 * math.tau))
        img.blend(x, y, (90, 170, 150, 90))
        img.blend(x, (y + 8) % 16, (90, 170, 150, 50))
    os.makedirs(os.path.join(ROOT, "textures", "gui", "advancements", "backgrounds"), exist_ok=True)
    img.save(os.path.join(ROOT, "textures", "gui", "advancements", "backgrounds", "jade.png"))


if __name__ == "__main__":
    icon()
    background()
    print("ok")

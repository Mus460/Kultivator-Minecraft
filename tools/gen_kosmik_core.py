"""Generates the shared textures of Film Kosmik 3D's core (DESIGN 32.1.2, module F). Everything here is ours.

Writes (16 x 16, opaque):
  textures/misc/kosmik/black.png   the fallback dome of a stage without its own dome texture (pure space black, a
                                   faint blue lift so a shader pack's tone mapping never crushes it to a flat hole)
  textures/misc/kosmik/white.png   plain white for vertex-coloured stage geometry

Run with: python -I tools/gen_kosmik_core.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator", "textures", "misc",
                    "kosmik")
SIZE = 16


def solid(rgb):
    img = Image(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            img.set(x, y, (rgb[0], rgb[1], rgb[2], 255))
    return img


def validate(paths):
    for path in paths:
        if not os.path.isfile(path):
            raise SystemExit("missing " + path)
        with open(path, "rb") as f:
            head = f.read(24)
        if head[:8] != b"\x89PNG\r\n\x1a\n":
            raise SystemExit("not a png " + path)
        w = int.from_bytes(head[16:20], "big")
        h = int.from_bytes(head[20:24], "big")
        if (w, h) != (SIZE, SIZE):
            raise SystemExit("bad size %s %dx%d" % (path, w, h))
    total = sum(os.path.getsize(p) for p in paths)
    print("ok: %d files, %d bytes" % (len(paths), total))


def main():
    os.makedirs(ROOT, exist_ok=True)
    black = os.path.join(ROOT, "black.png")
    white = os.path.join(ROOT, "white.png")
    solid((2, 3, 8)).save(black)
    solid((255, 255, 255)).save(white)
    validate([black, white])


if __name__ == "__main__":
    main()

"""Generates the heart demon textures (trial module).

heart_demon_eyes.png: a 64x64 overlay in the player-skin UV layout, fully transparent except the glowing red eyes on
the front face of the head (u 8..15, v 8..15, eyes on row 12 like the default skins). It is drawn with the emissive
"eyes" render type over the darkened copy of the owner's skin, so the eyes glow in the dark.

Run with: python tools/gen_trial_textures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")

EYE_CORE = (255, 236, 220, 255)
EYE_HOT = (255, 72, 56, 255)
EYE_RED = (214, 18, 30, 255)
GLOW = (255, 30, 40, 110)
GLOW_FAINT = (200, 10, 30, 55)

# Front face of the head in the player skin layout.
FACE_X, FACE_Y = 8, 8
EYE_ROW = FACE_Y + 4


def heart_demon_eyes():
    img = Image(64, 64)
    # Left eye (viewer's left) at face columns 1-2, right eye at columns 5-6: the standard eye pixels.
    for start in (FACE_X + 1, FACE_X + 5):
        inner = start + 1 if start == FACE_X + 1 else start
        outer = start if start == FACE_X + 1 else start + 1
        # The burning pupil next to the nose, the red iris outside it.
        img.set(inner, EYE_ROW, EYE_HOT)
        img.set(outer, EYE_ROW, EYE_RED)
        # A soft glow bleeding above and below the eye.
        for x in (start, start + 1):
            img.blend(x, EYE_ROW - 1, GLOW)
            img.blend(x, EYE_ROW + 1, GLOW_FAINT)
        # A thin trail of red light running down from the outer corner, like a tear.
        img.blend(outer, EYE_ROW + 2, GLOW_FAINT)
    # A white-hot spark in the middle of each pupil row keeps the eyes readable from far away.
    img.blend(FACE_X + 2, EYE_ROW, (EYE_CORE[0], EYE_CORE[1], EYE_CORE[2], 150))
    img.blend(FACE_X + 5, EYE_ROW, (EYE_CORE[0], EYE_CORE[1], EYE_CORE[2], 150))
    return img


def main():
    out_dir = os.path.join(ROOT, "textures", "entity")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "heart_demon_eyes.png")
    heart_demon_eyes().save(path)
    print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()

"""Tiny dependency-free PNG writer for generating the mod's textures (python tools/<script>.py).

Usage:
    from png import Image
    img = Image(16, 16)            # transparent
    img.set(x, y, (r, g, b, a))    # 0..255
    img.blend(x, y, (r, g, b, a))  # alpha-blend over what is there
    img.save("path/to/file.png")
"""
import struct
import zlib


class Image:
    def __init__(self, width, height, fill=(0, 0, 0, 0)):
        self.width = width
        self.height = height
        self.px = [list(fill) for _ in range(width * height)]

    def get(self, x, y):
        return tuple(self.px[y * self.width + x])

    def set(self, x, y, rgba):
        if 0 <= x < self.width and 0 <= y < self.height:
            r, g, b = rgba[0], rgba[1], rgba[2]
            a = rgba[3] if len(rgba) > 3 else 255
            self.px[y * self.width + x] = [int(max(0, min(255, c))) for c in (r, g, b, a)]

    def blend(self, x, y, rgba):
        if not (0 <= x < self.width and 0 <= y < self.height):
            return
        sr, sg, sb = rgba[0], rgba[1], rgba[2]
        sa = (rgba[3] if len(rgba) > 3 else 255) / 255.0
        dr, dg, db, da = self.px[y * self.width + x]
        da /= 255.0
        oa = sa + da * (1 - sa)
        if oa <= 0:
            self.px[y * self.width + x] = [0, 0, 0, 0]
            return
        out = [(sr * sa + dr * da * (1 - sa)) / oa, (sg * sa + dg * da * (1 - sa)) / oa, (sb * sa + db * da * (1 - sa)) / oa, oa * 255]
        self.px[y * self.width + x] = [int(max(0, min(255, round(c)))) for c in out]

    def save(self, path):
        raw = bytearray()
        for y in range(self.height):
            raw.append(0)
            for x in range(self.width):
                raw.extend(bytes(self.px[y * self.width + x]))

        def chunk(kind, data):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        header = struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n")
            f.write(chunk(b"IHDR", header))
            f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
            f.write(chunk(b"IEND", b""))

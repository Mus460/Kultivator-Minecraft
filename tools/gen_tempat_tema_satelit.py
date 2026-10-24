"""The two satellites of the themed cities (DESIGN 38.T.2.6; owner 38TK inti). Deterministic: same run, same bytes.

  kota_tema/reruntuhan_gurun  jigsaw (size 4, max 60): a town swallowed by the sand. Start = the half-buried gate with
                              its two broken towers; east the toppled watchtower (two variants); north the sunken court
                              whose stair drops 14 blocks to the buried hall of three rooms: the pillar hall (two rune
                              pillars, murals 0-3), the mural corridor (murals 4-7) and the nest of the Raja Kalajengking
                              (two more pillars, murals 8-11). Murals are terracotta mosaics on the walls (dancers, camels,
                              the crescent spring, the scorpion) plus anchors for the client billboard. Chests use the
                              tier dispatcher kultivator:tema/reruntuhan (Pemurnian Qi inside 2600, Fondasi inside 5200,
                              Inti Emas beyond, by the chest's own position; tables tema/reruntuhan_1|2|3).
  kota_tema/pos_pemburu       tata_letak (rencana kota_tema_pos_pemburu, district pos, 2 x 2 lots): the righteous
                              hunters' camp of Han Qingshuang: command tent with the war table (NW), the Bounty Board of
                              demonic fugitives with two guard tents (NE), the smithy (SW), the horse lines and the drill
                              ground (SE); four PEMBURU_LURUS (the leader in the command tent).

Anchors: kota_tema/reruntuhan (site) + the oasis agent's tema_oasis/pintu (ruang 1-3), tema_oasis/pilar (no 1-4),
tema_oasis/sarang (tier 0 = by ring), and inti's kota_tema/mural_gua (kisah 0-11), kota_tema/temuan_reruntuhan
(jenis koin|mural|peta); camp: kota_tema/pos (site), kota_tema/pemburu (peran PEMBURU_LURUS, ketua), kota_tema/papan_buronan.
No block ever changes at runtime: sand doors, pillars and the scorpion are entities of the oasis agent.

Writes: structure/kota_tema/{reruntuhan_gurun,pos_pemburu}/*.nbt, worldgen/{structure,template_pool,processor_list}/
kota_tema/{reruntuhan_gurun,pos_pemburu}*, loot_table/tema/{reruntuhan,reruntuhan_1,reruntuhan_2,reruntuhan_3,
pos_pemburu}.json. Sets, tags and the pack are gen_tempat_tema_data.py's.

    python tools/gen_tempat_tema_satelit.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402

NS = "kultivator"
RG = "kota_tema/reruntuhan_gurun"
PP = "kota_tema/pos_pemburu"
LOOT_RG = NS + ":tema/reruntuhan"
LOOT_PP = NS + ":tema/pos_pemburu"
PROC_RG = NS + ":" + RG + "/lapuk"
PROC_PP = NS + ":" + PP + "/utuh"

PASIR = {
    "floor": "minecraft:smooth_sandstone", "floor_alt": "minecraft:cut_sandstone", "wall": "minecraft:sandstone",
    "wall_alt": "minecraft:cut_sandstone", "pillar": "minecraft:cut_sandstone", "trim": "minecraft:chiseled_sandstone",
    "roof": "minecraft:sandstone_stairs", "roof_slab": "minecraft:sandstone_slab", "roof_full": "minecraft:smooth_sandstone",
    "accent": "minecraft:orange_terracotta", "jade": "minecraft:cyan_terracotta", "glow": "minecraft:lantern",
    "path": "minecraft:smooth_sandstone", "path_alt": "minecraft:sand", "fence": "minecraft:sandstone_wall",
    "window": "minecraft:air", "carpet": "minecraft:orange_carpet", "plaster": "minecraft:smooth_sandstone",
    "found": "minecraft:sandstone",
}
KEMAH = {
    "floor": "minecraft:dirt_path", "floor_alt": "minecraft:coarse_dirt", "wall": "minecraft:spruce_planks",
    "wall_alt": "minecraft:stripped_spruce_log", "pillar": "minecraft:spruce_log", "trim": "minecraft:stripped_spruce_log",
    "roof": "minecraft:spruce_stairs", "roof_slab": "minecraft:spruce_slab", "roof_full": "minecraft:spruce_planks",
    "accent": "minecraft:white_wool", "jade": "minecraft:light_blue_wool", "glow": "minecraft:lantern",
    "path": "minecraft:dirt_path", "path_alt": "minecraft:gravel", "fence": "minecraft:spruce_fence",
    "window": "minecraft:air", "carpet": "minecraft:white_carpet", "plaster": "minecraft:white_terracotta",
    "found": "minecraft:dirt",
}

# ------------------------------------------------------------------------------------------------ mural mosaics
WARNA = {"k": "minecraft:brown_terracotta", "o": "minecraft:orange_terracotta", "r": "minecraft:red_terracotta",
         "y": "minecraft:yellow_terracotta", "w": "minecraft:white_terracotta", "b": "minecraft:cyan_terracotta",
         "u": "minecraft:light_blue_terracotta", "d": "minecraft:black_terracotta", "g": "minecraft:green_terracotta",
         "p": "minecraft:pink_terracotta", "m": "minecraft:magenta_terracotta"}
MOTIF = {
    "penari": ["r..y..r", ".r.y.r.", "..rmr..", ".rrrrr.", "rrrprrr", "..y.y.."],
    "unta": ["......kk.", "..kk..kkk", ".kkkkkkk.", "kkkkkkkk.", ".k.k..k.k", ".k.k..k.k"],
    "kafilah": ["...o.....kk", "..ooo.kk.kk", "...o.kkkkk.", "..o.okkkkk.", "..o.o.k.k..", "..o.o.k.k.."],
    "matahari": [".yyy.", "yyyyy", "yyoyy", "yyyyy", ".yyy."],
    "palma": ["gggg.g", ".gggg.", "g.kk.g", "..k...", "..k...", "..k..."],
    "mata_air": ["...bb..", ".bbuub.", "bbu..b.", "bu.....", "bbu..b.", ".bbuub."],
    "kalajengking": ["d.......d", ".d.ddd.d.", "..ddddd..", ".d.ddd.d.", "d...r...d"],
    "bulan": ["..www", ".ww..", "ww...", ".ww..", "..www"],
}
# 12 stories: what each panel shows (left motif, right motif, accent sky)
KISAH = [("penari", "matahari"), ("kafilah", "palma"), ("mata_air", "bulan"), ("penari", "bulan"),
         ("unta", "matahari"), ("kafilah", "mata_air"), ("palma", "penari"), ("unta", "bulan"),
         ("kalajengking", "mata_air"), ("penari", "kalajengking"), ("kalajengking", "matahari"), ("mata_air", "palma")]


def mural(p, x0, y_atas, z0, kisah, arah):
    """A 13 x 8 mosaic panel framed in cut sandstone on a wall plane. arah "x": the panel runs +x at fixed z0 (seen from
    the side the room is on); "z": runs +z at fixed x0. Returns the panel centre (for the billboard anchor)."""
    w, h = 13, 8
    def at(u, v, block):
        x, z = (x0 + u, z0) if arah == "x" else (x0, z0 + u)
        p.put(x, y_atas - v, z, block)
    for u in range(w):
        for v in range(h):
            edge = u in (0, w - 1) or v in (0, h - 1)
            at(u, v, "minecraft:cut_sandstone" if edge else "minecraft:smooth_sandstone")
    left, right = KISAH[kisah % len(KISAH)]
    for motif, u0 in ((left, 1), (right, 7)):
        rows = MOTIF[motif]
        v0 = h - 1 - len(rows)
        for v, row in enumerate(rows):
            for u, c in enumerate(row):
                if c != "." and u0 + u < w - 1:
                    at(u0 + u, v0 + v, WARNA[c])
    # a sand band of the dune line at the bottom of every panel
    for u in range(1, w - 1):
        if p.rng.random() < 0.6:
            at(u, h - 2, "minecraft:yellow_terracotta" if u % 3 else "minecraft:orange_terracotta")
    return (x0 + w // 2, y_atas - h // 2, z0) if arah == "x" else (x0, y_atas - h // 2, z0 + w // 2)


# ------------------------------------------------------------------------------------------------ shared helpers
def lot(folder, nama, sx, sy, sz, palet):
    return tl.Lot(folder, nama, tinggi=sy, lebar=sx, dalam=sz, palet=palet)


def gundukan(p, cx, cz, r, puncak, dasar, rng_cut=0.25):
    """A sand dune mound: height falls off from `puncak` at the centre to `dasar` at radius r (sand on sandstone)."""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
            if d > r:
                continue
            top = int(round(dasar + (puncak - dasar) * (1 - (d / r) ** 1.6)))
            if p.rng.random() < rng_cut:
                top -= 1
            for y in range(dasar, top + 1):
                if p.inside(x, y, z) and p.get(x, y, z) is None:
                    p.put(x, y, z, "minecraft:sand")


def tembok_rusak(p, x0, z0, x1, z1, y0, tinggi, tebal=1, rusak=0.35):
    """A broken wall: per column the height drops at random, the top often a slab or a stair."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            h = tinggi if p.rng.random() > rusak else max(1, int(tinggi * p.rng.uniform(0.25, 0.8)))
            for y in range(y0, y0 + h):
                r = p.rng.random()
                p.put(x, y, z, "minecraft:cut_sandstone" if r < 0.18 else "minecraft:chiseled_sandstone" if r < 0.22 else "minecraft:sandstone")
            if p.rng.random() < 0.4:
                p.put(x, y0 + h, z, "minecraft:sandstone_slab", {"type": "bottom"})


def tiang(p, x, z, y0, y1, patah=None):
    """A column of cut sandstone with chiseled base and capital; patah: broken at that height."""
    top = y1 if patah is None else min(y1, patah)
    for y in range(y0, top + 1):
        p.put(x, y, z, "minecraft:chiseled_sandstone" if y in (y0, y1) else "minecraft:cut_sandstone")


def tiang_rune(p, x, z, y0, tinggi=6):
    """A rune pillar body: chiseled sandstone with cyan glyph bands (the PilarRuneEntity stands on its top)."""
    for y in range(y0, y0 + tinggi):
        band = (y - y0) % 3 == 1
        p.put(x, y, z, "minecraft:cyan_terracotta" if band else "minecraft:chiseled_sandstone")
    p.put(x, y0 + tinggi, z, "minecraft:smooth_sandstone_slab", {"type": "bottom"})
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p.put(x + dx, y0, z + dz, "minecraft:sandstone_stairs", {"facing": {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]})


def peti(p, x, y, z, hadap, tabel=LOOT_RG):
    p.put(x, y, z, "minecraft:chest", {"facing": hadap, "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": tabel})


def tulang(p, x, y, z):
    r = p.rng.random()
    if r < 0.5:
        p.put(x, y, z, "minecraft:bone_block", {"axis": p.rng.choice(("x", "z", "y"))})
    elif r < 0.75:
        p.put(x, y, z, "minecraft:skeleton_skull", {"rotation": str(p.rng.randrange(16))})
    else:
        p.put(x, y, z, "minecraft:dead_bush")


def pot(p, x, y, z):
    p.put(x, y, z, "minecraft:decorated_pot", {"facing": p.rng.choice(("north", "east", "south", "west")), "cracked": "true",
                                               "waterlogged": "false"})


def hiasan_gurun(p, x0, z0, x1, z1, y, n):
    """Scatter of dead bushes, bones and cracked pots on the floor y (only on free cells over a solid block)."""
    for _ in range(n):
        x = p.rng.randint(x0, x1)
        z = p.rng.randint(z0, z1)
        if p.get(x, y, z) is None and p.get(x, y - 1, z) is not None:
            r = p.rng.random()
            if r < 0.45:
                p.put(x, y, z, "minecraft:dead_bush")
            elif r < 0.75:
                tulang(p, x, y, z)
            else:
                pot(p, x, y, z)


# ================================================================================================ reruntuhan gurun
G = 4  # ground level of the surface pieces (y 0..3 foundation)


def gerbang():
    """Start piece: the half-buried city gate between two broken towers, the courtyard north of it."""
    W, H, D = 25, 23, 23
    p = lot(RG, "gerbang", W, H, D, PASIR)
    p.fill(0, 0, 0, W - 1, G - 1, D - 1, "minecraft:sandstone")
    # courtyard paving north of the wall (z 0..11), cracked and sand-drifted
    for x in range(W):
        for z in range(0, 12):
            r = p.rng.random()
            p.put(x, G - 1, z, "minecraft:sand" if r < 0.3 else "minecraft:smooth_sandstone" if r < 0.8 else "minecraft:cut_sandstone")
    p.air(0, G, 0, W - 1, H - 1, 11)
    # the city wall z 12..14 with the gate opening x 10..14
    for x in range(W):
        for z in range(12, 15):
            if 10 <= x <= 14:
                continue
            top = 13 if x in range(3, 8) or x in range(17, 22) else p.rng.choice((9, 10, 11, 11, 12))
            for y in range(G, G + top):
                r = p.rng.random()
                p.put(x, y, z, "minecraft:cut_sandstone" if (y - G) % 5 == 0 else "minecraft:chiseled_sandstone" if r < 0.04 else "minecraft:sandstone")
    # gate arch: jambs, lintel of chiseled sandstone, a stepped arch
    for y in range(G, G + 9):
        for z in range(12, 15):
            p.put(9, y, z, "minecraft:chiseled_sandstone" if y % 3 == 0 else "minecraft:cut_sandstone")
            p.put(15, y, z, "minecraft:chiseled_sandstone" if y % 3 == 0 else "minecraft:cut_sandstone")
    for x in range(9, 16):
        for z in range(12, 15):
            p.put(x, G + 9, z, "minecraft:chiseled_sandstone")
            p.put(x, G + 10, z, "minecraft:smooth_sandstone")
    for z in range(12, 15):
        p.put(10, G + 8, z, "minecraft:sandstone_stairs", {"facing": "east", "half": "top"})
        p.put(14, G + 8, z, "minecraft:sandstone_stairs", {"facing": "west", "half": "top"})
    p.air(10, G, 12, 14, G + 7, 14)
    # half buried: sand fills the opening up to G+2, a dune slopes up to it from the south
    p.fill(10, G, 12, 14, G + 2, 14, "minecraft:sand")
    gundukan(p, 12, 19, 9, G + 4, G)
    for x in range(10, 15):
        for z in range(15, 23):
            if p.get(x, G + 3, z) is not None:
                p.air(x, G + 4, z, x, G + 7, z)
    # two flanking towers (x 2..7 and 17..22, z 10..16); the west one stands, the east one broke at G+7
    for tx, utuh in ((2, True), (17, False)):
        top = G + 16 if utuh else G + 7
        for x in range(tx, tx + 6):
            for z in range(10, 17):
                edge = x in (tx, tx + 5) or z in (10, 16)
                for y in range(G, top):
                    if edge:
                        p.put(x, y, z, "minecraft:cut_sandstone" if (y - G) % 4 == 3 else "minecraft:sandstone")
                    else:
                        p.put(x, y, z, "minecraft:air")
        if utuh:
            for x in range(tx - 1, tx + 7):
                for z in range(9, 18):
                    if x in (tx - 1, tx + 6) or z in (9, 17):
                        p.put(x, top, z, "minecraft:sandstone_stairs", {"facing": "north" if z == 9 else "south" if z == 17 else "west" if x == tx - 1 else "east", "half": "top"})
            for x in range(tx, tx + 6):
                for z in range(10, 17):
                    if (x + z) % 2 == 0 and (x in (tx, tx + 5) or z in (10, 16)):
                        p.put(x, top + 1, z, "minecraft:sandstone_wall")
            # narrow windows
            for y in (G + 6, G + 11):
                p.air(tx, y, 13, tx, y + 1, 13)
                p.air(tx + 5, y, 13, tx + 5, y + 1, 13)
            p.air(tx + 2, G, 10, tx + 3, G + 2, 10)
        else:
            # toppled stones of the fallen top on the dune
            for _ in range(18):
                x = p.rng.randint(16, 24)
                z = p.rng.randint(15, 22)
                y = G + 1
                while y < H - 1 and p.get(x, y, z) is not None:
                    y += 1
                p.put(x, y, z, p.rng.choice(("minecraft:cut_sandstone", "minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:sandstone_slab")))
    # inside the courtyard: a fallen guardian statue (lion head of chiseled sandstone on its side) and a dry fountain
    for x in range(4, 9):
        p.put(x, G, 4, "minecraft:cut_sandstone")
        p.put(x, G + 1, 4, "minecraft:smooth_sandstone_slab", {"type": "bottom"})
    p.put(3, G, 4, "minecraft:chiseled_sandstone")
    p.put(3, G + 1, 4, "minecraft:chiseled_sandstone")
    p.put(2, G, 4, "minecraft:sandstone_stairs", {"facing": "east"})
    for x in range(15, 22):
        for z in range(2, 9):
            d = ((x - 18) ** 2 + (z - 5) ** 2) ** 0.5
            if d <= 3.5:
                p.put(x, G - 1, z, "minecraft:cut_sandstone")
                if d > 2.5:
                    p.put(x, G, z, "minecraft:smooth_sandstone_slab", {"type": "bottom"})
                elif p.rng.random() < 0.5:
                    p.put(x, G - 1, z, "minecraft:sand")
    tiang(p, 18, 5, G, G + 4, patah=G + 2)
    hiasan_gurun(p, 0, 0, 24, 10, G, 26)
    # torn awning remains on the inner wall face
    for x in (5, 6, 18, 19):
        p.put(x, G + 6, 11, "minecraft:orange_wool")
        if p.rng.random() < 0.6:
            p.put(x, G + 5, 11, "minecraft:orange_carpet")
    # connectors: north to the sunken court, east to the watchtower
    p.jigsaw_side(12, G, 0, "north", NS + ":rg/gerbang_utara", NS + ":rg/aula_selatan", NS + ":" + RG + "/aula_tangga",
                  final_state="minecraft:smooth_sandstone")
    p.jigsaw_side(W - 1, G, 5, "east", NS + ":rg/gerbang_timur", NS + ":rg/menara_barat", NS + ":" + RG + "/menara",
                  final_state="minecraft:smooth_sandstone")
    p.jangkar(12, G + 1, 6, "kota_tema/reruntuhan")
    p.jangkar(4, G + 1, 5, "kota_tema/temuan_reruntuhan", jenis="peta")
    p.titik("pandang", 12, G + 8, 22, "north")
    return p.simpan(), p


def menara(varian):
    """The toppled watchtower east of the gate: a stump with a stair inside and its top lying in the sand."""
    W, H, D = 17, 18, 15
    nama = "menara_roboh" if varian == 0 else "menara_roboh_b"
    p = lot(RG, nama, W, H, D, PASIR)
    p.fill(0, 0, 0, W - 1, G - 1, D - 1, "minecraft:sandstone")
    for x in range(W):
        for z in range(D):
            p.put(x, G - 1, z, "minecraft:sand" if p.rng.random() < 0.55 else "minecraft:smooth_sandstone")
    cx, cz, r = 6, 7, 4
    stump = G + (9 if varian == 0 else 6)
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
            if d <= r + 0.3:
                ring = d >= r - 0.7
                top = stump - (0 if p.rng.random() > 0.3 else p.rng.randint(1, 3))
                for y in range(G, top if ring else G):
                    p.put(x, y, z, "minecraft:cut_sandstone" if (y - G) % 4 == 0 else "minecraft:sandstone")
                if not ring:
                    p.air(x, G, z, x, H - 1, z)
    # spiral stair inside the stump
    steps = [(cx - 2, cz - 2), (cx - 1, cz - 3), (cx, cz - 3), (cx + 1, cz - 3), (cx + 2, cz - 2), (cx + 3, cz - 1),
             (cx + 3, cz), (cx + 3, cz + 1), (cx + 2, cz + 2)]
    for i, (x, z) in enumerate(steps):
        y = G + i
        if y < stump:
            p.put(x, y, z, "minecraft:smooth_sandstone_slab", {"type": "top"})
    p.air(cx - r, G, cz, cx - r, G + 2, cz)  # doorway west
    # the fallen top lies east, a broken cylinder on its side with a crenellation row
    for i in range(7):
        x = 11 + (i % 2)
        for j in range(-3, 4):
            z = cz + j
            yy = G + (abs(j) <= 1) + (1 if abs(j) <= 2 else 0)
            if p.rng.random() < 0.8:
                p.put(x + i // 2, yy, z, p.rng.choice(("minecraft:sandstone", "minecraft:cut_sandstone")))
    for j in range(-3, 4, 2):
        p.put(15, G + 1, cz + j, "minecraft:sandstone_wall")
    gundukan(p, 13, 3, 5, G + 3, G)
    gundukan(p, 2, 12, 4, G + 2, G)
    peti(p, cx, G, cz + 2, "north")
    p.put(cx + 1, G, cz + 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    hiasan_gurun(p, 0, 0, W - 1, D - 1, G, 14)
    p.jigsaw_side(0, G, cz, "west", NS + ":rg/menara_barat", "minecraft:empty", "minecraft:empty",
                  final_state="minecraft:sand")
    p.jangkar(cx, G + 1, cz, "kota_tema/temuan_reruntuhan", jenis="koin")
    return p.simpan(), p


B = 1  # floor of the buried rooms (y 0 foundation)
TINGGI_RUANG = 9


def aula_tangga():
    """The sunken court: ruined arcades around a stair well that drops 14 blocks to the buried hall's door."""
    W, H, D = 15, 24, 21
    S = 16  # court floor (aligned with the gate's ground: connector at S on the south edge)
    p = lot(RG, "aula_tangga", W, H, D, PASIR)
    # court floor at S, low broken arcade walls
    for x in range(W):
        for z in range(D):
            p.put(x, S - 1, z, "minecraft:sandstone")
            p.put(x, S - 1 + 0, z, "minecraft:smooth_sandstone" if p.rng.random() > 0.25 else "minecraft:sand")
    p.air(0, S, 0, W - 1, H - 1, D - 1)
    tembok_rusak(p, 0, 0, 0, D - 1, S, 5)
    tembok_rusak(p, W - 1, 0, W - 1, D - 1, S, 5)
    tembok_rusak(p, 0, 0, W - 1, 0, S, 4)
    for z in (4, 10, 16):
        tiang(p, 2, z, S, S + 6, patah=S + p.rng.randint(2, 6))
        tiang(p, W - 3, z, S, S + 6, patah=S + p.rng.randint(2, 6))
    # the stair well: 5 wide (x 5..9) from z 16 (top) down north to z 3 at the hall floor B
    for z in range(2, 18):
        for x in range(4, 11):
            for y in range(B, S):
                p.put(x, y, z, "minecraft:sandstone" if x in (4, 10) else "minecraft:air")
    for i in range(S - B):
        z = 16 - i
        y = S - 1 - i
        if z < 3:
            break
        for x in range(5, 10):
            p.put(x, y, z, "minecraft:sandstone_stairs", {"facing": "south", "half": "bottom"})
            p.put(x, y - 1, z, "minecraft:sandstone")
        if i % 4 == 2:
            p.put(4, y + 2, z, "minecraft:chiseled_sandstone")
            p.put(10, y + 2, z, "minecraft:chiseled_sandstone")
    # bottom landing and the hall door frame (sand door entity of ruang 1 stands in it)
    for x in range(4, 11):
        for z in range(0, 3):
            p.put(x, B - 1, z, "minecraft:cut_sandstone")
            for y in range(B, B + 6):
                p.put(x, y, z, "minecraft:sandstone" if x in (4, 10) or y == B + 5 else "minecraft:air")
    for x in range(5, 10):
        p.put(x, B + 4, 0, "minecraft:chiseled_sandstone")
    p.put(7, B + 5, 1, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # well rim on the court: stairs as a parapet, a pair of guardian lions (chiseled + stairs)
    for z in range(2, 18):
        p.put(4, S, z, "minecraft:sandstone_wall")
        p.put(10, S, z, "minecraft:sandstone_wall")
    for x in (5, 9):
        p.put(x, S, 18, "minecraft:chiseled_sandstone")
        p.put(x, S + 1, 18, "minecraft:sandstone_stairs", {"facing": "south"})
    gundukan(p, 12, 3, 4, S + 2, S)
    hiasan_gurun(p, 0, 0, W - 1, D - 1, S, 12)
    p.jigsaw_side(7, S, D - 1, "south", NS + ":rg/aula_selatan", "minecraft:empty", "minecraft:empty",
                  final_state="minecraft:smooth_sandstone")
    p.jigsaw_side(7, B, 0, "north", NS + ":rg/aula_utara", NS + ":rg/ruang1_selatan", NS + ":" + RG + "/ruang_tiang",
                  final_state="minecraft:cut_sandstone")
    p.jangkar(7, B + 1, 1, "tema_oasis/pintu", ruang=1)
    return p.simpan(), p


def ruang_dasar(p, W, D, H):
    """Shell of a buried room: sandstone box with a cut-sandstone floor and an air interior."""
    for x in range(W):
        for z in range(D):
            p.put(x, B - 1, z, "minecraft:cut_sandstone")
            p.put(x, H - 1, z, "minecraft:sandstone")
            for y in range(B, H - 1):
                edge = x in (0, W - 1) or z in (0, D - 1)
                p.put(x, y, z, "minecraft:sandstone" if edge else "minecraft:air")
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            if p.rng.random() < 0.08:
                p.put(x, B, z, "minecraft:sand")


def ruang_tiang():
    """Room 1: the pillar hall. Two rows of columns, two rune pillars, four murals, a chest by the far wall."""
    W, D, H = 21, 21, TINGGI_RUANG + 2
    p = lot(RG, "ruang_tiang", W, H, D, PASIR)
    ruang_dasar(p, W, D, H)
    for x in (5, 15):
        for z in (5, 10, 15):
            tiang(p, x, z, B, H - 2)
    # ceiling beams of cut sandstone between the columns
    for z in (5, 10, 15):
        for x in range(5, 16):
            p.put(x, H - 2, z, "minecraft:cut_sandstone")
    tiang_rune(p, 10, 6, B)
    tiang_rune(p, 10, 14, B)
    # murals 0-3 on the side walls (inner faces)
    centres = [mural(p, 0, B + 8, 3, 0, "z"), mural(p, W - 1, B + 8, 3, 1, "z")]
    # far (north) wall: two more panels left and right of the door to the corridor
    centres.append(mural(p, 1, B + 8, 0, 2, "x"))
    # door east to the corridor (x W-1, z 15..17)
    for z in range(14, 18):
        for y in range(B, B + 4):
            p.put(W - 1, y, z, "minecraft:air")
    p.put(W - 1, B + 4, 14, "minecraft:chiseled_sandstone")
    p.put(W - 1, B + 4, 17, "minecraft:chiseled_sandstone")
    centres.append(mural(p, 7, B + 8, 0, 3, "x"))
    peti(p, 16, B, 2, "south")
    pot(p, 17, B, 2)
    pot(p, 3, B, 18)
    for x, z in ((3, 3), (17, 18), (12, 18)):
        tulang(p, x, B, z)
    for x in (4, 16):
        p.put(x, B + 6, 10, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
        p.put(x, B + 7, 10, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.air(9, B, D - 1, 11, B + 3, D - 1)
    p.jigsaw_side(10, B, D - 1, "south", NS + ":rg/ruang1_selatan", "minecraft:empty", "minecraft:empty",
                  final_state="minecraft:cut_sandstone")
    p.jigsaw_side(W - 1, B, 16, "east", NS + ":rg/ruang1_timur", NS + ":rg/lorong_barat", NS + ":" + RG + "/lorong_mural",
                  final_state="minecraft:cut_sandstone")
    p.jangkar(10, B + 7, 6, "tema_oasis/pilar", no=1)
    p.jangkar(10, B + 7, 14, "tema_oasis/pilar", no=2)
    for i, c in enumerate(centres):
        p.jangkar(c[0] + (1 if c[0] == 0 else -1 if c[0] == W - 1 else 0), c[1], c[2] + (1 if c[2] == 0 else 0), "kota_tema/mural_gua", kisah=i)
    return p.simpan(), p


def lorong_mural():
    """Room 2: the mural corridor running north, murals 4-7 facing each other, the second sand door at its end."""
    W, D, H = 11, 27, TINGGI_RUANG + 2
    p = lot(RG, "lorong_mural", W, H, D, PASIR)
    ruang_dasar(p, W, D, H)
    # vaulted ceiling: stairs on both sides
    for z in range(1, D - 1):
        p.put(1, H - 2, z, "minecraft:sandstone_stairs", {"facing": "east", "half": "top"})
        p.put(W - 2, H - 2, z, "minecraft:sandstone_stairs", {"facing": "west", "half": "top"})
    centres = [mural(p, 0, B + 8, 1, 4, "z"), mural(p, W - 1, B + 8, 1, 5, "z"), mural(p, 0, B + 8, 13, 6, "z"),
               mural(p, W - 1, B + 8, 13, 7, "z")]
    for z in (7, 19):
        p.put(5, B + 6, z, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
        p.put(5, B + 7, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    # an offering ledge with cracked pots and a chest halfway
    for z in range(11, 14):
        p.put(W - 2, B, z, "minecraft:smooth_sandstone_slab", {"type": "bottom"})
    pot(p, W - 2, B + 1, 11)
    pot(p, W - 2, B + 1, 13)
    peti(p, 1, B, 12, "east")
    # the corridor enters from the west at z 22..25 (connector) and ends north at the nest door
    for z in range(21, 25):
        for y in range(B, B + 4):
            p.put(0, y, z, "minecraft:air")
    p.jigsaw_side(0, B, 23, "west", NS + ":rg/lorong_barat", "minecraft:empty", "minecraft:empty",
                  final_state="minecraft:cut_sandstone")
    for x in range(4, 7):
        for y in range(B, B + 4):
            p.put(x, y, 0, "minecraft:air")
    p.jigsaw_side(5, B, 0, "north", NS + ":rg/lorong_utara", NS + ":rg/sarang_selatan", NS + ":" + RG + "/sarang",
                  final_state="minecraft:cut_sandstone")
    p.jangkar(5, B + 1, 1, "tema_oasis/pintu", ruang=2)
    for i, c in enumerate(centres):
        p.jangkar(c[0] + (1 if c[0] == 0 else -1), c[1], c[2], "kota_tema/mural_gua", kisah=4 + i)
    hiasan_gurun(p, 1, 1, W - 2, D - 2, B, 8)
    return p.simpan(), p


def sarang():
    """Room 3: the nest of the Raja Kalajengking: a round sand pit under a cracked dome, two rune pillars, murals 8-11,
    bones of the ones who came before, the reward chest behind the pit."""
    W, D, H = 25, 25, 13
    p = lot(RG, "sarang", W, H, D, PASIR)
    ruang_dasar(p, W, D, H)
    c = 12
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if d <= 7:
                p.put(x, B - 1, z, "minecraft:sand")
                p.put(x, B, z, "minecraft:air")
            elif d <= 8:
                p.put(x, B, z, "minecraft:smooth_sandstone_slab", {"type": "bottom"})
            # dome: the ceiling rises toward the middle
            for y in range(B + 7, H - 1):
                if d < (H - 1 - y) * 3.2:
                    p.put(x, y, z, "minecraft:air")
    for (x, z) in ((4, 4), (20, 4), (4, 20), (20, 20)):
        tiang(p, x, z, B, H - 4)
    tiang_rune(p, 4, 12, B)
    tiang_rune(p, 20, 12, B)
    centres = [mural(p, 6, B + 8, 0, 8, "x"), mural(p, 0, B + 8, 6, 9, "z"), mural(p, W - 1, B + 8, 6, 10, "z"),
               mural(p, 6, B + 8, D - 1, 11, "x")]
    for _ in range(26):
        x = p.rng.randint(2, W - 3)
        z = p.rng.randint(2, D - 3)
        if p.get(x, B, z) in (None, "minecraft:air") and p.get(x, B - 1, z) not in (None, "minecraft:air"):
            tulang(p, x, B, z)
    for x, z in ((3, 7), (21, 17), (8, 21)):
        p.put(x, B, z, "minecraft:cobweb")
    peti(p, c, B + 0, 2, "south")
    p.put(c - 1, B, 2, "minecraft:gold_block")
    p.put(c + 1, B, 2, "minecraft:skeleton_skull", {"rotation": "8"})
    for x in range(11, 14):
        for y in range(B, B + 4):
            p.put(x, y, D - 1, "minecraft:air")
    p.jigsaw_side(c, B, D - 1, "south", NS + ":rg/sarang_selatan", "minecraft:empty", "minecraft:empty",
                  final_state="minecraft:cut_sandstone")
    p.jangkar(c, B + 1, c, "tema_oasis/sarang", tier=0)
    p.jangkar(c, B + 1, D - 2, "tema_oasis/pintu", ruang=3)
    p.jangkar(4, B + 7, 12, "tema_oasis/pilar", no=3)
    p.jangkar(20, B + 7, 12, "tema_oasis/pilar", no=4)
    p.jangkar(c, B + 1, 4, "kota_tema/temuan_reruntuhan", jenis="mural")
    p.titik("pandang", c, B + 5, D - 3, "north")
    return p.simpan(), p, centres


# ================================================================================================ pos pemburu
L = tl.LANTAI_Y
LOT = tl.LOT


def lot_kemah(nama, sisi):
    p = lot(PP, nama, LOT, 24, LOT, KEMAH)
    p.fill(0, 0, 0, LOT - 1, L - 1, LOT - 1, "minecraft:dirt")
    for x in range(LOT):
        for z in range(LOT):
            r = p.rng.random()
            p.put(x, L, z, "minecraft:grass_block" if r < 0.62 else "minecraft:coarse_dirt" if r < 0.82 else "minecraft:dirt_path")
    p.sisi(*sisi)
    return p


def jalan_kemah(p, sisi):
    """3-wide camp lanes of dirt path along the given sides (two lots make the 6-wide camp street)."""
    for c in sisi:
        if c == "n":
            box = (0, 0, LOT - 1, 2)
        elif c == "s":
            box = (0, LOT - 3, LOT - 1, LOT - 1)
        elif c == "w":
            box = (0, 0, 2, LOT - 1)
        else:
            box = (LOT - 3, 0, LOT - 1, LOT - 1)
        for x in range(box[0], box[2] + 1):
            for z in range(box[1], box[3] + 1):
                p.put(x, L, z, "minecraft:dirt_path" if p.rng.random() < 0.8 else "minecraft:gravel")


def tenda(p, x0, z0, panjang, lebar, tinggi, kain="minecraft:white_wool", tepi="minecraft:light_gray_wool", pintu="south"):
    """A ridge tent: spruce poles at the corners and the ridge, a wool roof falling to both long sides, open gable on
    `pintu` (south/north). Returns the interior box (x0+1, z0+1, x1-1, z1-1)."""
    x1, z1 = x0 + lebar - 1, z0 + panjang - 1
    mid = (x0 + x1) / 2.0
    p.air(x0 + 1, L + 1, z0 + 1, x1 - 1, L + tinggi - 1, z1 - 1)
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            h = int(round(tinggi - abs(x - mid) * (tinggi - 1) / max(1.0, mid - x0)))
            p.put(x, L + h, z, kain if abs(x - mid) > 0.6 or z in (z0, z1) else tepi)
            if x in (x0, x1):
                for y in range(L + 1, L + h):
                    p.put(x, y, z, kain)
    for z in (z0, z1):
        for y in range(L + 1, L + tinggi + 1):
            p.put(int(mid), y, z, "minecraft:spruce_fence")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        p.put(x, L + 1, z, "minecraft:spruce_fence")
    # closed back gable, open front with tied flaps
    back = z0 if pintu == "south" else z1
    front = z1 if pintu == "south" else z0
    for x in range(x0 + 1, x1):
        h = int(round(tinggi - abs(x - mid) * (tinggi - 1) / max(1.0, mid - x0)))
        for y in range(L + 1, L + h):
            p.put(x, y, back, kain)
    p.put(x0 + 1, L + 1, front, tepi)
    p.put(x1 - 1, L + 1, front, tepi)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            p.put(x, L, z, "minecraft:spruce_planks" if (x + z) % 5 else "minecraft:stripped_spruce_wood", {"axis": "y"} if (x + z) % 5 == 0 else None)
    return (x0 + 1, z0 + 1, x1 - 1, z1 - 1)


def panji(p, x, z, tinggi=7):
    """A sect banner pole: spruce fence pole, a white banner on top (the righteous sect's colour)."""
    for y in range(L + 1, L + tinggi):
        p.put(x, y, z, "minecraft:spruce_fence")
    p.put(x, L + tinggi, z, "minecraft:white_banner", {"rotation": "0"})


def api_unggun(p, x, z):
    """A cold campfire ring (unlit: cek_tempat forbids lit campfires outside kitchens) with log benches."""
    p.put(x, L + 1, z, "minecraft:campfire", {"lit": "false", "facing": "north", "signal_fire": "false", "waterlogged": "false"})
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        p.put(x + dx, L, z + dz, "minecraft:cobblestone")
    p.put(x - 3, L + 1, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    p.put(x + 3, L + 1, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    p.put(x, L + 1, z + 3, "minecraft:stripped_spruce_log", {"axis": "x"})


def pagar_kemah(p, sisi_luar):
    """Palisade of sharpened spruce posts one block inside the outer (free) sides, a lantern post every 8 blocks; the
    end of each side toward the camp street (the lot corner on the inner side) stays open as a gate."""
    for c in sisi_luar:
        for i in range(LOT):
            if c in "ns":
                x, z = i, (1 if c == "n" else LOT - 2)
                gate = (i >= LOT - 7) if "e" not in sisi_luar else (i <= 6)
            else:
                x, z = (1 if c == "w" else LOT - 2), i
                gate = (i >= LOT - 7) if "s" not in sisi_luar else (i <= 6)
            if gate:
                continue
            h = 3 + (i % 2)
            for y in range(L + 1, L + h):
                p.put(x, y, z, "minecraft:spruce_log", {"axis": "y"})
            p.put(x, L + h, z, "minecraft:spruce_fence")
            if i % 8 == 4:
                p.put(x, L + h + 1, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def tumpukan(p, x, z, n=5):
    """A supply stack: barrels and crates (spruce planks, chests without loot are props) two high."""
    for i in range(n):
        xx, zz = x + i % 3, z + i // 3
        p.put(xx, L + 1, zz, "minecraft:barrel" if (i % 2) == 0 else "minecraft:spruce_planks", {"facing": "up", "open": "false"} if i % 2 == 0 else None)
        if p.rng.random() < 0.5:
            p.put(xx, L + 2, zz, "minecraft:barrel", {"facing": "north", "open": "false"})


def kayu_bakar(p, x, z, panjang=4):
    for i in range(panjang):
        p.put(x + i, L + 1, z, "minecraft:stripped_spruce_log", {"axis": "z"})
        if i % 2 == 0:
            p.put(x + i, L + 2, z, "minecraft:spruce_log", {"axis": "z"})


def tiang_lampu(p, x, z):
    for y in range(L + 1, L + 4):
        p.put(x, y, z, "minecraft:spruce_fence")
    p.put(x, L + 4, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def gerobak(p, x, z):
    """A covered supply wagon: plank bed, dark oak wheels, a white canvas hoop."""
    for i in range(5):
        for j in range(3):
            p.put(x + i, L + 2, z + j, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for i in (0, 4):
        for j in (-1, 3):
            p.put(x + i, L + 1, z + j, "minecraft:dark_oak_trapdoor", {"facing": "north" if j < 0 else "south", "half": "bottom",
                                                                        "open": "true", "powered": "false", "waterlogged": "false"})
    for i in range(5):
        for j in range(3):
            p.put(x + i, L + 4, z + j, "minecraft:white_wool")
        p.put(x + i, L + 3, z - 0, "minecraft:white_wool" if i in (0, 4) else "minecraft:air")
        p.put(x + i, L + 3, z + 2, "minecraft:white_wool" if i in (0, 4) else "minecraft:air")
    p.put(x + 1, L + 3, z + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(x + 3, L + 3, z + 1, "minecraft:hay_block", {"axis": "x"})
    p.put(x - 1, L + 1, z + 1, "minecraft:spruce_fence")


def pos_komando():
    """NW lot: the command tent of Han Qingshuang with the war table, the sect banners and the leader's bedroll."""
    p = lot_kemah("komando", ("B", "J", "J", "B"))
    jalan_kemah(p, "es")
    ix0, iz0, ix1, iz1 = tenda(p, 10, 8, 18, 15, 7, pintu="south")
    # war table: map on a cartography table bench with candles and a lectern of the bounty ledger
    for x in range(15, 20):
        p.put(x, L + 1, 15, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
        p.put(x, L + 1, 16, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
    p.put(15, L + 1, 15, "minecraft:cartography_table")
    p.put(19, L + 2, 16, "minecraft:candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
    p.put(17, L + 2, 15, "minecraft:white_carpet")
    p.put(16, L + 2, 16, "minecraft:red_carpet")
    p.put(13, L + 1, 12, "minecraft:lectern", {"facing": "east", "has_book": "false", "powered": "false"})
    for x in (14, 20):
        p.put(x, L + 1, 13, "minecraft:spruce_stairs", {"facing": "south" if x == 14 else "south"})
    p.put(21, L + 1, 10, "minecraft:white_bed", {"facing": "south", "part": "head", "occupied": "false"})
    p.put(21, L + 1, 11, "minecraft:white_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    peti(p, 22, L + 1, 13, "west", tabel=LOOT_PP)
    p.put(12, L + 1, 21, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(12, L + 2, 21, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    p.put(22, L + 1, 21, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    for x in (8, 27):
        panji(p, x, 26)
    api_unggun(p, 18, 33)
    pagar_kemah(p, "nw")
    for zt in (30, 38):
        tenda(p, 4, zt, 6, 5, 3, kain="minecraft:light_gray_wool", tepi="minecraft:white_wool", pintu="south")
        p.put(6, L + 1, zt + 2, "minecraft:white_carpet")
    for x, z in ((9, 27), (27, 27), (30, 12), (30, 20)):
        tiang_lampu(p, x, z)
    tumpukan(p, 30, 36, 6)
    kayu_bakar(p, 24, 40, 5)
    p.titik("kasur", 6, L + 1, 32, "south")
    p.titik("kasur", 6, L + 1, 40, "south")
    p.kotak("bangunan", 10, L, 8, 24, L + 7, 25, "south", nama="tenda_komando")
    p.titik("kasur", 21, L + 1, 10, "south")
    p.titik("meja_tulis", 16, L + 1, 14, "south")
    p.titik("obrol", 17, L + 1, 18, "north")
    p.titik("jaga", 17, L + 1, 27, "south")
    p.titik("kursi", 15, L + 1, 33, "east")
    p.titik("kursi", 21, L + 1, 33, "west")
    p.titik("peti_milik", 22, L + 1, 13, "west")
    p.jangkar(17, L + 1, 12, "kota_tema/pos")
    p.jangkar(17, L + 1, 17, "kota_tema/pemburu", peran="PEMBURU_LURUS", ketua="true")
    return p


def pos_papan():
    """NE lot: the Bounty Board of demonic fugitives under a little roof, two guard tents and the lookout platform."""
    p = lot_kemah("papan", ("B", "B", "J", "J"))
    jalan_kemah(p, "sw")
    # the board: dark oak frame 9 wide, paper sheets of white/light gray terracotta, red seal wool, a shingled roof
    bx0, bz = 10, 18
    for x in range(bx0, bx0 + 11):
        for y in range(L + 1, L + 6):
            edge = x in (bx0, bx0 + 10) or y in (L + 1, L + 5)
            if edge:
                p.put(x, y, bz, "minecraft:dark_oak_planks" if y != L + 1 else "minecraft:stripped_dark_oak_log", {"axis": "x"} if y == L + 1 else None)
            else:
                r = p.rng.random()
                p.put(x, y, bz, "minecraft:white_terracotta" if r < 0.45 else "minecraft:light_gray_terracotta" if r < 0.8 else "minecraft:birch_planks")
        p.put(x, L + 6, bz - 1, "minecraft:dark_oak_stairs", {"facing": "north"})
        p.put(x, L + 6, bz, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
        p.put(x, L + 6, bz + 1, "minecraft:dark_oak_stairs", {"facing": "south"})
    for x in (bx0 + 2, bx0 + 5, bx0 + 8):
        p.put(x, L + 4, bz, "minecraft:red_wool")
    for x in (bx0, bx0 + 10):
        p.put(x, L, bz, "minecraft:stripped_dark_oak_log", {"axis": "y"})
    for x in (bx0 + 1, bx0 + 9):
        p.put(x, L + 1, bz + 1, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    # two guard tents
    tenda(p, 6, 26, 9, 7, 4, kain="minecraft:light_gray_wool", tepi="minecraft:white_wool", pintu="north")
    tenda(p, 24, 26, 9, 7, 4, kain="minecraft:light_gray_wool", tepi="minecraft:white_wool", pintu="north")
    for x0 in (7, 25):
        p.put(x0 + 1, L + 1, 28, "minecraft:white_carpet")
        p.put(x0 + 3, L + 1, 28, "minecraft:white_carpet")
    # lookout platform on four logs with a ladder
    for x, z in ((34, 6), (39, 6), (34, 11), (39, 11)):
        for y in range(L + 1, L + 8):
            p.put(x, y, z, "minecraft:spruce_log", {"axis": "y"})
    for x in range(34, 40):
        for z in range(6, 12):
            p.put(x, L + 8, z, "minecraft:spruce_planks")
            if x in (34, 39) or z in (6, 11):
                p.put(x, L + 9, z, "minecraft:spruce_fence")
    for y in range(L + 1, L + 9):
        p.put(36, y, 12, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    p.put(36, L + 9, 12, "minecraft:air")
    panji(p, 39, 6, tinggi=12)
    api_unggun(p, 18, 34)
    pagar_kemah(p, "ne")
    for x, z in ((4, 16), (28, 18), (12, 40), (30, 40)):
        tiang_lampu(p, x, z)
    kayu_bakar(p, 20, 40, 4)
    tumpukan(p, 40, 30, 4)
    p.kotak("bangunan", 6, L, 26, 12, L + 4, 34, "north", nama="tenda_jaga_barat")
    p.kotak("bangunan", 24, L, 26, 30, L + 4, 34, "north", nama="tenda_jaga_timur")
    p.titik("jaga", 36, L + 9, 8, "north")
    p.titik("jaga", 15, L + 1, 21, "north")
    p.titik("kasur", 8, L + 1, 30, "north")
    p.titik("kasur", 26, L + 1, 30, "north")
    p.titik("obrol", 18, L + 1, 30, "north")
    p.titik("kursi", 15, L + 1, 34, "east")
    p.jangkar(15, L + 1, bz + 2, "kota_tema/papan_buronan")
    return p


def pos_pandai():
    """SW lot: the smithy: an open forge shed with anvil, grindstone, smithing table, a cold blast furnace and tool racks."""
    p = lot_kemah("pandai", ("J", "J", "B", "B"))
    jalan_kemah(p, "ne")
    x0, z0, x1, z1 = 10, 12, 26, 24
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, L, z, "minecraft:stone_bricks" if (x + z) % 4 else "minecraft:cobblestone")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
        for y in range(L + 1, L + 6):
            p.put(x, y, z, "minecraft:stripped_spruce_log", {"axis": "y"})
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            edge = z in (z0 - 1, z1 + 1)
            p.put(x, L + 6, z, "minecraft:spruce_stairs" if edge else "minecraft:spruce_slab",
                  {"facing": "north" if z == z0 - 1 else "south"} if edge else {"type": "bottom", "waterlogged": "false"})
    # back wall of stone with the forge
    for x in range(x0, x1 + 1):
        for y in range(L + 1, L + 4):
            p.put(x, y, z0, "minecraft:cobblestone" if (x + y) % 3 else "minecraft:stone_bricks")
    p.put(14, L + 1, z0 + 1, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    p.put(15, L + 1, z0 + 1, "minecraft:smoker", {"facing": "south", "lit": "false"})
    p.put(14, L + 2, z0 + 1, "minecraft:stone_brick_wall", {"up": "true", "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": "false"})
    p.put(18, L + 1, 17, "minecraft:anvil", {"facing": "east"})
    p.put(20, L + 1, 17, "minecraft:grindstone", {"facing": "north", "face": "floor"})
    p.put(22, L + 1, z0 + 1, "minecraft:smithing_table")
    p.put(23, L + 1, z0 + 1, "minecraft:crafting_table")
    p.put(24, L + 1, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    for x in range(11, 13):
        p.put(x, L + 1, 22, "minecraft:water_cauldron", {"level": "3"})
    for x in (17, 19, 21):
        p.put(x, L + 3, z0 + 1, "minecraft:spruce_trapdoor", {"facing": "south", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    for x in range(30, 42, 3):
        p.put(x, L + 1, 30, "minecraft:hay_block", {"axis": "y"})
    p.put(33, L + 1, 34, "minecraft:target")
    p.put(36, L + 1, 34, "minecraft:target")
    pagar_kemah(p, "sw")
    gerobak(p, 8, 32)
    gerobak(p, 18, 38)
    tumpukan(p, 28, 14, 6)
    kayu_bakar(p, 28, 22, 5)
    for x, z in ((6, 10), (30, 28), (8, 28)):
        tiang_lampu(p, x, z)
    p.kotak("bangunan", x0, L, z0, x1, L + 6, z1, "south", nama="pandai")
    p.titik("kerja:palu", 18, L + 1, 18, "north")
    p.titik("kerja:tungku", 14, L + 1, z0 + 2, "north")
    p.titik("latihan", 33, L + 1, 32, "south")
    p.titik("latihan", 36, L + 1, 32, "south")
    p.titik("meja_makan", 22, L + 1, 21, "west")
    p.jangkar(19, L + 1, 20, "kota_tema/pemburu", peran="PEMBURU_LURUS")
    return p


def pos_kuda():
    """SE lot: horse lines (fenced paddock, hay, a water trough) and the drill ground with straw dummies."""
    p = lot_kemah("kuda", ("J", "B", "B", "J"))
    jalan_kemah(p, "nw")
    x0, z0, x1, z1 = 8, 8, 30, 22
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                p.put(x, L + 1, z, "minecraft:spruce_fence")
            elif p.rng.random() < 0.35:
                p.put(x, L, z, "minecraft:coarse_dirt")
    p.put((x0 + x1) // 2, L + 1, z1, "minecraft:spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "false", "powered": "false"})
    # covered horse line along the north fence
    for x in range(x0 + 1, x1):
        p.put(x, L + 4, z0 + 1, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
        p.put(x, L + 4, z0 + 2, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
        if x % 4 == 1:
            for y in range(L + 1, L + 4):
                p.put(x, y, z0 + 2, "minecraft:spruce_fence")
    for x in range(x0 + 2, x1 - 1, 4):
        p.put(x, L + 1, z0 + 1, "minecraft:hay_block", {"axis": "x"})
    for x in range(x0 + 3, x0 + 9):
        p.put(x, L + 1, z1 - 2, "minecraft:water_cauldron", {"level": "3"})
    # drill ground: straw dummies on fence posts, a weapon rack
    for x in range(10, 38, 6):
        p.put(x, L + 1, 34, "minecraft:spruce_fence")
        p.put(x, L + 2, 34, "minecraft:hay_block", {"axis": "y"})
        p.put(x, L + 3, 34, "minecraft:carved_pumpkin", {"facing": "north"})
    for x in range(14, 20):
        p.put(x, L + 1, 40, "minecraft:spruce_fence")
        p.put(x, L + 2, 40, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    pagar_kemah(p, "se")
    # fodder shed with saddles (leather-brown wool) and hay
    for x in range(34, 42):
        for z in range(8, 14):
            p.put(x, L + 4, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for x, z in ((34, 8), (41, 8), (34, 13), (41, 13)):
        for y in range(L + 1, L + 4):
            p.put(x, y, z, "minecraft:spruce_fence")
    for x in range(35, 41):
        p.put(x, L + 1, 9, "minecraft:hay_block", {"axis": "x"})
        if x % 2:
            p.put(x, L + 2, 9, "minecraft:hay_block", {"axis": "x"})
    for x in (36, 38, 40):
        p.put(x, L + 1, 12, "minecraft:brown_wool")
        p.put(x, L + 2, 12, "minecraft:spruce_fence")
    for x, z in ((6, 26), (32, 24), (40, 28)):
        tiang_lampu(p, x, z)
    p.kotak("bangunan", x0, L, z0, x1, L + 4, z1, "south", nama="kandang_kuda")
    p.titik("jaga", 20, L + 1, 24, "south")
    p.titik("latihan", 16, L + 1, 32, "south")
    p.titik("latihan", 22, L + 1, 32, "south")
    p.titik("kerja:sapu", 12, L + 1, 12, "north")
    p.jangkar(19, L + 1, 25, "kota_tema/pemburu", peran="PEMBURU_LURUS")
    p.jangkar(25, L + 1, 30, "kota_tema/pemburu", peran="PEMBURU_LURUS")
    return p


# ================================================================================================ loot tables
def _item(name, w, lo=1, hi=1, umur=None):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    mods = []
    if hi > 1 or lo != 1:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if umur:
        mods.append({"type": "minecraft:set_components", "components": {"kultivator:herb_age": umur}})
    if mods:
        e["modifier"] = mods if len(mods) > 1 else mods[0]
    return e


def _pool(lo, hi, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": entries}


def _tabel(nama, pools, jenis="minecraft:chest"):
    path = os.path.join(structgen.DATA, "loot_table", "tema", nama + ".json")
    structgen.write_json(path, {"type": jenis, "pools": pools, "random_sequence": NS + ":tema/" + nama})


def _kotak(r):
    return {"type": "minecraft:location_check",
            "predicate": {"position": {"x": {"min": -r, "max": r}, "z": {"min": -r, "max": r}}}}


def loot_tables():
    T = NS + ":tema/"
    _tabel("reruntuhan_1", [
        _pool(2, 4, [_item(NS + ":spirit_stone_low", 30, 2, 5), _item(T + "koin_kuno", 25, 2, 6), _item(T + "pecahan_mural", 12, 1, 2),
                     _item("minecraft:gold_nugget", 18, 3, 9), _item(NS + ":spirit_grass", 10, 1, 2, 60), _item(NS + ":blank_talisman", 6)]),
        _pool(0, 1, [_item(T + "lempeng_peta", 3), _item(NS + ":dagang/kurma_emas", 6, 2, 4), {"type": "minecraft:empty", "weight": 10}]),
    ])
    _tabel("reruntuhan_2", [
        _pool(2, 4, [_item(NS + ":spirit_stone_mid", 26, 1, 3), _item(T + "koin_kuno", 24, 4, 10), _item(T + "pecahan_mural", 14, 1, 3),
                     _item("minecraft:gold_ingot", 14, 1, 3), _item(NS + ":purple_flame_flower", 9, 1, 2, 200), _item(NS + ":beast_core_mid", 10)]),
        _pool(0, 1, [_item(T + "lempeng_peta", 4), _item(NS + ":dagang/kaca_warna", 6, 1, 3), _item(NS + ":dagang/giok_barat", 2),
                     {"type": "minecraft:empty", "weight": 8}]),
    ])
    _tabel("reruntuhan_3", [
        _pool(2, 5, [_item(NS + ":spirit_stone_high", 22, 1, 2), _item(T + "koin_kuno", 22, 8, 16), _item(T + "pecahan_mural", 14, 2, 3),
                     _item(NS + ":beast_core_high", 12), _item(NS + ":gold_lightning_bamboo", 8, 1, 1, 400),
                     _item(NS + ":ninecurl_ginseng", 6, 1, 1, 300), _item("minecraft:diamond", 8, 1, 2)]),
        _pool(1, 1, [_item(T + "lempeng_peta", 3), _item(NS + ":dagang/giok_barat", 5, 1, 2), _item(NS + ":dagang/permadani_gurun", 4),
                     {"type": "minecraft:empty", "weight": 6}]),
    ])
    # the dispatcher: the chest's own position picks the tier (ring 1 / 2 / beyond; square approximation of 3000 / 6000)
    _tabel("reruntuhan", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:loot_table", "value": T + "reruntuhan_1", "condition": _kotak(2600)},
        {"type": "minecraft:loot_table", "value": T + "reruntuhan_2", "condition": _kotak(5200)},
        {"type": "minecraft:loot_table", "value": T + "reruntuhan_3"}]}]}])
    _tabel("pos_pemburu", [
        _pool(3, 6, [_item("minecraft:bread", 20, 2, 5), _item("minecraft:cooked_beef", 14, 1, 3), _item("minecraft:arrow", 16, 4, 12),
                     _item("minecraft:paper", 12, 2, 6), _item("minecraft:iron_ingot", 10, 1, 3), _item(NS + ":spirit_stone_low", 12, 2, 6),
                     _item(NS + ":qi_gathering_pill", 8, 1, 2), _item("minecraft:leather", 8, 1, 3)]),
    ])


# ================================================================================================ worldgen JSON
def processors():
    rules = []
    for src, dst, pr in (("minecraft:sandstone", "minecraft:sand", 0.06), ("minecraft:cut_sandstone", "minecraft:sandstone", 0.10),
                         ("minecraft:smooth_sandstone", "minecraft:sand", 0.12), ("minecraft:chiseled_sandstone", "minecraft:cut_sandstone", 0.15),
                         ("minecraft:smooth_sandstone", "minecraft:sandstone", 0.08)):
        rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src, "probability": pr},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": dst}})
    tl.tulis_processor(RG + "/lapuk", {"processors": [{"processor_type": "minecraft:rule", "rules": rules},
                                                      {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}]})
    tl.tulis_processor(PP + "/utuh", tl.processor_utuh())


def worldgen(ids):
    processors()
    tl.tulis_pool(RG + "/gerbang", [(tl.elemen(ids["gerbang"], PROC_RG), 1)])
    tl.tulis_pool(RG + "/menara", [(tl.elemen(ids["menara_roboh"], PROC_RG), 3), (tl.elemen(ids["menara_roboh_b"], PROC_RG), 2)])
    tl.tulis_pool(RG + "/aula_tangga", [(tl.elemen(ids["aula_tangga"], PROC_RG), 1)])
    tl.tulis_pool(RG + "/ruang_tiang", [(tl.elemen(ids["ruang_tiang"], PROC_RG), 1)])
    tl.tulis_pool(RG + "/lorong_mural", [(tl.elemen(ids["lorong_mural"], PROC_RG), 1)])
    tl.tulis_pool(RG + "/sarang", [(tl.elemen(ids["sarang"], PROC_RG), 1)])
    rg = {"type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/kota_tema/reruntuhan_gurun",
          "max_distance_from_center": {"horizontal": 60, "vertical": 48}, "size": 4, "spawn_overrides": {},
          "start_height": {"absolute": -1}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
          "start_pool": NS + ":" + RG + "/gerbang", "step": "surface_structures", "terrain_adaptation": "beard_box",
          "use_expansion_hack": False}
    tl.tulis_wg("structure", RG, rg)
    lots = []
    for nama, (lx, lz) in (("komando", (0, 0)), ("papan", (1, 0)), ("pandai", (0, 1)), ("kuda", (1, 1))):
        tl.tulis_pool(PP + "/" + nama, [(tl.elemen(ids["pp_" + nama], PROC_PP), 1)])
        lots.append({"dx": -LOT + LOT * lx, "dz": -LOT + LOT * lz, "pool": NS + ":" + PP + "/" + nama, "putar": 0})
    pp = tl.struktur_tata_letak("kota_tema_pos_pemburu", "pos", lots, "#minecraft:is_overworld", adaptasi="beard_box")
    tl.tulis_wg("structure", PP, pp)


def main():
    ids = {}
    path, p = gerbang()
    ids["gerbang"] = p.id
    for v in (0, 1):
        path, p = menara(v)
        ids[p.nama] = p.id
    path, p = aula_tangga()
    ids["aula_tangga"] = p.id
    path, p = ruang_tiang()
    ids["ruang_tiang"] = p.id
    path, p = lorong_mural()
    ids["lorong_mural"] = p.id
    path, p, _c = sarang()
    ids["sarang"] = p.id
    for builder in (pos_komando, pos_papan, pos_pandai, pos_kuda):
        p = builder()
        p.simpan()
        ids["pp_" + p.nama] = p.id
    worldgen(ids)
    loot_tables()
    tl.cek_semua(strict=True)
    print("satelit: %d templates" % len(ids))


if __name__ == "__main__":
    main()

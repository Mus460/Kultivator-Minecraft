"""Interior dressing styles for the desert oasis and the demon city (D-TK2 detail pass; DESIGN 38.T.4 and 38.T.7).

Builds on tools/hias_tema.py (D-TK1's module, imported, never edited): importing this module registers extra room
styles into hias_tema.GAYA / hias_tema.RUG, so `hias_tema.ruang(p, x0, z0, x1, z1, f, "<style>")` dresses walls with
them. `tengah(...)` adds what hias_tema does not do: furniture islands in the open middle of large halls (low tables
with floor cushions, tavern tables with stools, shelving rows, crate stacks, brass displays, weapon racks, gambling
tables), on a grid that keeps two-block aisles, every doorway front, every life point (titik) and the cell it faces free.

Same contract as hias_tema: only empty cells (missing or air) on a solid floor are filled, nothing placed by a builder
is overwritten, an own seeded random stream is used (deterministic, the rest of a generator's output does not change).
Every block is a vanilla 26.3 block (checked by structgen against tools/data/vanilla_blocks_26_3.json); no fire, lava,
lightning rods or lit campfires (cek_tempat).
"""
import random

import hias_tema as H
import kota_besar_kit as K

_OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
_ARAH = (("north", 0, -1), ("south", 0, 1), ("west", -1, 0), ("east", 1, 0))
_FENCE = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
_BARS = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
_TD = {"half": "top", "open": "false", "powered": "false", "waterlogged": "false"}

taruh = H.taruh


def _lilin(warna, n):
    return "minecraft:%s_candle" % warna, {"candles": str(n), "lit": "true", "waterlogged": "false"}


# ================================================================================================ desert pieces
_POT_GURUN = ("minecraft:potted_cactus", "minecraft:potted_dead_bush", "minecraft:flower_pot", "minecraft:potted_acacia_sapling",
              "minecraft:potted_orange_tulip")
_BANTAL = ("minecraft:red_carpet", "minecraft:orange_carpet", "minecraft:yellow_carpet", "minecraft:magenta_carpet",
           "minecraft:cyan_carpet")
_WOL = ("minecraft:red_wool", "minecraft:orange_wool", "minecraft:yellow_wool", "minecraft:cyan_wool", "minecraft:purple_wool",
        "minecraft:magenta_wool")


def _bantal(p, x, y, z, hadap, r):
    """A floor cushion against the wall (a carpet of the bazaar colours)."""
    taruh(p, x, y, z, r.choice(_BANTAL))


def _meja_rendah(p, x, y, z, hadap, r):
    """A low acacia table with a brass lamp, a water jug or a small pot on it."""
    if taruh(p, x, y, z, "minecraft:acacia_trapdoor", dict(_TD, facing=hadap)):
        roll = r.random()
        if roll < 0.35:
            taruh(p, x, y + 1, z, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
        elif roll < 0.7:
            taruh(p, x, y + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": hadap, "waterlogged": "false"})
        else:
            taruh(p, x, y + 1, z, r.choice(_POT_GURUN))


def _kendi(p, x, y, z, hadap, r):
    """A clay water jar, sometimes two (a big one with a cracked one beside it)."""
    taruh(p, x, y, z, "minecraft:decorated_pot", {"cracked": "true" if r.random() < 0.25 else "false", "facing": hadap,
                                                  "waterlogged": "false"})


def _tempayan(p, x, y, z, hadap, r):
    """A filled water basin (the oasis lives on its water)."""
    taruh(p, x, y, z, "minecraft:water_cauldron", {"level": str(r.choice((2, 3, 3)))})


def _karung(p, x, y, z, hadap, r):
    """Spice and grain sacks: full composters, a second one stacked or a lantern on top."""
    taruh(p, x, y, z, "minecraft:composter", {"level": str(r.choice((6, 7, 8)))})
    if r.random() < 0.35:
        taruh(p, x, y + 1, z, "minecraft:composter", {"level": str(r.choice((5, 7)))})


def _lampu_kuningan(p, x, y, z, hadap, r):
    """A brass lamp on a barrel stand."""
    if taruh(p, x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"}):
        taruh(p, x, y + 1, z, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})


def _gulung_permadani(p, x, y, z, hadap, r):
    """A rolled carpet standing on end: wool with a carpet of another colour on top."""
    a = r.choice(_WOL)
    if taruh(p, x, y, z, a):
        taruh(p, x, y + 1, z, r.choice(_BANTAL))


def _rak_tombak(p, x, y, z, hadap, r):
    """A spear rack: two acacia posts with an iron 'spearhead'."""
    if taruh(p, x, y, z, "minecraft:acacia_fence", _FENCE):
        if taruh(p, x, y + 1, z, "minecraft:acacia_fence", _FENCE):
            taruh(p, x, y + 2, z, "minecraft:iron_bars", _BARS)


def _peti_kafilah(p, x, y, z, hadap, r):
    """A caravan chest or a lying cask, a saddle blanket on top now and then."""
    if r.random() < 0.5:
        taruh(p, x, y, z, "minecraft:chest", {"facing": hadap, "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest"})
    else:
        ax = "east" if hadap in ("north", "south") else "south"
        if taruh(p, x, y, z, "minecraft:barrel", {"facing": ax, "open": "false"}) and r.random() < 0.5:
            taruh(p, x, y + 1, z, r.choice(_BANTAL))


def _jerami(p, x, y, z, hadap, r):
    """Camel fodder: a hay bale, sometimes two."""
    taruh(p, x, y, z, "minecraft:hay_block", {"axis": "y"})
    if r.random() < 0.4:
        taruh(p, x, y + 1, z, "minecraft:hay_block", {"axis": "x" if hadap in ("north", "south") else "z"})


def _tungku_tanah(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:smoker", {"facing": hadap, "lit": "false"})


def _kuningan_pajang_fix(p, x, y, z, hadap, r):
    if taruh(p, x, y, z, r.choice(("minecraft:waxed_cut_copper", "minecraft:waxed_copper_block", "minecraft:waxed_exposed_cut_copper"))):
        if r.random() < 0.6:
            taruh(p, x, y + 1, z, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
        else:
            taruh(p, x, y + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": hadap, "waterlogged": "false"})


def _tenun(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:loom", {"facing": hadap})


def _pewarna(p, x, y, z, hadap, r):
    """A dye vat (water cauldron) with dyed wool drying beside it."""
    taruh(p, x, y, z, "minecraft:water_cauldron", {"level": "3"})


def _kitab_air(p, x, y, z, hadap, r):
    """The priests' water records: a lectern with its ledger or a bookcase."""
    if r.random() < 0.4:
        taruh(p, x, y, z, "minecraft:lectern", {"facing": hadap, "has_book": "true", "powered": "false"})
    else:
        H._rak(p, x, y, z, hadap, r)


def _lilin_putih(p, x, y, z, hadap, r):
    b, props = _lilin(r.choice(("white", "light_blue", "white")), r.choice((2, 3, 4)))
    taruh(p, x, y, z, b, props)


def _pot_gurun(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(_POT_GURUN))


def _peti(p, x, y, z, hadap, r):
    H._peti(p, x, y, z, hadap, r)


# ================================================================================================ demon city pieces
_POT_IBLIS = ("minecraft:potted_crimson_roots", "minecraft:potted_wither_rose", "minecraft:potted_crimson_fungus",
              "minecraft:potted_warped_fungus", "minecraft:potted_dead_bush")


def _lilin_merah(p, x, y, z, hadap, r):
    """Red and black candles on a crimson board."""
    if taruh(p, x, y, z, "minecraft:crimson_trapdoor", dict(_TD, facing=hadap)):
        b, props = _lilin(r.choice(("red", "red", "black")), r.choice((1, 2, 3)))
        taruh(p, x, y + 1, z, b, props)


def _lilin_lantai_merah(p, x, y, z, hadap, r):
    b, props = _lilin(r.choice(("red", "black", "red")), r.choice((2, 3, 4)))
    taruh(p, x, y, z, b, props)


def _tengkorak_fix(p, x, y, z, hadap, r):
    plinth = r.choice(("minecraft:bone_block", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone"))
    if taruh(p, x, y, z, plinth, {"axis": "y"} if plinth == "minecraft:bone_block" else None):
        taruh(p, x, y + 1, z, r.choice(("minecraft:skeleton_skull", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull")),
              {"rotation": str({"south": 0, "west": 4, "north": 8, "east": 12}[hadap]), "powered": "false"})


def _rantai(p, x, y, z, hadap, r):
    """Chains hanging from a hook: two or three links high."""
    taruh(p, x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    taruh(p, x, y + 1, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    if r.random() < 0.5:
        taruh(p, x, y + 2, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def _lentera_jiwa(p, x, y, z, hadap, r):
    """A soul lantern on a blackstone post."""
    if taruh(p, x, y, z, "minecraft:polished_blackstone_wall", {"east": "none", "north": "none", "south": "none", "west": "none",
                                                                "up": "true", "waterlogged": "false"}):
        taruh(p, x, y + 1, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})


def _rak_senjata(p, x, y, z, hadap, r):
    """A dark weapon rack: a dark oak post with iron 'blades' and a chain."""
    if taruh(p, x, y, z, "minecraft:dark_oak_fence", _FENCE):
        taruh(p, x, y + 1, z, "minecraft:iron_bars", _BARS)
        if r.random() < 0.5:
            taruh(p, x, y + 2, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def _sangkar(p, x, y, z, hadap, r):
    """A small cage (iron bars box) with a skull or a bone inside implied by a bone block base."""
    if taruh(p, x, y, z, "minecraft:iron_bars", _BARS):
        taruh(p, x, y + 1, z, "minecraft:iron_bars", _BARS)


def _tong_racun(p, x, y, z, hadap, r):
    """A poison cask: a lying barrel with a green or lime candle."""
    ax = "east" if hadap in ("north", "south") else "south"
    if taruh(p, x, y, z, "minecraft:barrel", {"facing": ax, "open": "false"}) and r.random() < 0.55:
        b, props = _lilin(r.choice(("green", "lime")), 1)
        taruh(p, x, y + 1, z, b, props)


def _guci_gelap(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:decorated_pot", {"cracked": "true" if r.random() < 0.4 else "false", "facing": hadap,
                                                  "waterlogged": "false"})


def _kitab_gelap(p, x, y, z, hadap, r):
    """Chained scrolls: a chiseled bookshelf with a chain above, or a lectern."""
    if r.random() < 0.3:
        taruh(p, x, y, z, "minecraft:lectern", {"facing": hadap, "has_book": "true", "powered": "false"})
        return
    props = {"facing": hadap}
    for i in range(6):
        props["slot_%d_occupied" % i] = "true" if r.random() < 0.6 else "false"
    if taruh(p, x, y, z, "minecraft:chiseled_bookshelf", props):
        taruh(p, x, y + 1, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def _pot_iblis(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(_POT_IBLIS))


def _bangku_gelap(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(("minecraft:dark_oak_stairs", "minecraft:crimson_stairs")),
          {"facing": _OPP[hadap], "half": "bottom", "shape": "straight", "waterlogged": "false"})


def _brankas(p, x, y, z, hadap, r):
    """A strongbox: a chest under a blackstone slab, or a barrel bound in chain."""
    if r.random() < 0.6:
        taruh(p, x, y, z, "minecraft:chest", {"facing": hadap, "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest"})
    elif taruh(p, x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"}):
        taruh(p, x, y + 1, z, "minecraft:iron_chain", {"axis": "x" if hadap in ("north", "south") else "z", "waterlogged": "false"})


def _tulang(p, x, y, z, hadap, r):
    """A pile of beast bones (bone block, sometimes a lying one on top)."""
    taruh(p, x, y, z, "minecraft:bone_block", {"axis": r.choice(("x", "y", "z"))})


def _topeng(p, x, y, z, hadap, r):
    """A mask stand: a dark oak post with a skull 'mask' on it (the mask stall of the Thousand Shadows)."""
    if taruh(p, x, y, z, "minecraft:dark_oak_fence", _FENCE):
        taruh(p, x, y + 1, z, r.choice(("minecraft:skeleton_skull", "minecraft:wither_skeleton_skull")),
              {"rotation": str({"south": 0, "west": 4, "north": 8, "east": 12}[hadap]), "powered": "false"})


# style -> [(piece, weight)]
GAYA_BARU = {
    # oasis
    "gurun_inap": [(_bantal, 3), (_meja_rendah, 2), (_kendi, 2), (_peti, 1), (_pot_gurun, 1), (_lampu_kuningan, 1),
                   (_tempayan, 1)],
    "gurun_teh": [(_bantal, 4), (_meja_rendah, 3), (_kendi, 2), (_karung, 1), (_pot_gurun, 1), (_lampu_kuningan, 1)],
    "gurun_dapur": [(_tungku_tanah, 3), (_tempayan, 2), (_karung, 3), (_kendi, 2), (H._tong_rebah, 1), (H._kuali, 1)],
    "gurun_gudang": [(_karung, 4), (_peti_kafilah, 3), (_jerami, 2), (H._tong_rebah, 2), (_kendi, 1), (H._tali, 1)],
    "kuningan": [(_kuningan_pajang_fix, 4), (H._landasan, 1), (H._gerinda, 1), (H._meja_kerja, 1), (_lampu_kuningan, 2),
                 (_peti, 1)],
    "tenun": [(_gulung_permadani, 4), (_pewarna, 2), (_tenun, 1), (_karung, 1), (_bantal, 1)],
    "barak_gurun": [(_rak_tombak, 4), (_peti, 2), (H._tong_rebah, 1), (_kendi, 1), (H._gerinda, 1)],
    "kuil_air": [(_lilin_putih, 3), (_kendi, 2), (_kitab_air, 2), (_tempayan, 2), (_pot_gurun, 1)],
    "kantor_gurun": [(H._rak, 3), (H._mimbar, 1), (_peti, 2), (_lampu_kuningan, 1), (_kendi, 1), (H._meja_kerja, 1)],
    # demon city
    "iblis_kedai": [(H._tong_rebah, 3), (_bangku_gelap, 3), (_lilin_merah, 2), (_guci_gelap, 1), (_tong_racun, 2),
                    (_tengkorak_fix, 1)],
    "iblis_inap": [(H._lemari, 2), (_lilin_merah, 2), (_peti, 1), (_rantai, 1), (_pot_iblis, 1), (_guci_gelap, 1)],
    "iblis_gudang": [(H._peti_kayu, 3), (_tong_racun, 2), (H._tong_rebah, 2), (_rantai, 2), (_brankas, 1)],
    "penjara": [(_rantai, 3), (_rak_senjata, 2), (_brankas, 1), (H._tong_rebah, 1), (_tengkorak_fix, 1), (_lentera_jiwa, 1)],
    "barak_iblis": [(_rak_senjata, 3), (_brankas, 2), (_rantai, 2), (H._gerinda, 1), (H._tong_rebah, 1), (_lentera_jiwa, 1)],
    "kontrak": [(_kitab_gelap, 3), (_lilin_merah, 2), (_brankas, 1), (H._mimbar, 1), (_tengkorak_fix, 1), (_rantai, 1)],
    "lelang": [(_guci_gelap, 2), (_lilin_lantai_merah, 2), (_bangku_gelap, 2), (_lentera_jiwa, 1), (_topeng, 1),
               (_brankas, 1)],
    "istana_darah": [(_lilin_lantai_merah, 2), (_guci_gelap, 2), (_tengkorak_fix, 1), (_lentera_jiwa, 2), (_pot_iblis, 1),
                     (_kitab_gelap, 1)],
    "teh_asap": [(H._rak_teh, 2), (_guci_gelap, 2), (_lilin_merah, 1), (_bangku_gelap, 2), (_pot_iblis, 1)],
    "rumah_iblis": [(H._lemari, 2), (_lilin_merah, 2), (_brankas, 1), (_kitab_gelap, 1), (_pot_iblis, 1), (_tulang, 1)],
}

RUG_BARU = {
    "gurun_inap": ("minecraft:orange_carpet", "minecraft:red_carpet"),
    "gurun_teh": ("minecraft:red_carpet", "minecraft:yellow_carpet"),
    "kuil_air": ("minecraft:light_blue_carpet", "minecraft:white_carpet"),
    "kantor_gurun": ("minecraft:red_carpet", "minecraft:yellow_carpet"),
    "tenun": ("minecraft:magenta_carpet", "minecraft:orange_carpet"),
    "iblis_kedai": ("minecraft:red_carpet", "minecraft:black_carpet"),
    "iblis_inap": ("minecraft:black_carpet", "minecraft:red_carpet"),
    "kontrak": ("minecraft:black_carpet", "minecraft:gray_carpet"),
    "lelang": ("minecraft:red_carpet", "minecraft:black_carpet"),
    "istana_darah": ("minecraft:red_carpet", "minecraft:black_carpet"),
    "teh_asap": ("minecraft:gray_carpet", "minecraft:black_carpet"),
    "rumah_iblis": ("minecraft:black_carpet", "minecraft:red_carpet"),
}

H.GAYA.update(GAYA_BARU)
H.RUG.update(RUG_BARU)


# ================================================================================================ middle islands
def _meja_bantal(p, x, y, z, r, bebas):
    """A low acacia table with four floor cushions (desert dining and tea)."""
    if not taruh(p, x, y, z, "minecraft:acacia_trapdoor", dict(_TD, facing="north")):
        return False
    roll = r.random()
    if roll < 0.4:
        taruh(p, x, y + 1, z, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
    elif roll < 0.75:
        taruh(p, x, y + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
    warna = r.choice(_BANTAL)
    for _a, dx, dz in _ARAH:
        if (x + dx, y, z + dz) not in bebas:
            taruh(p, x + dx, y, z + dz, warna)
    return True


def _meja_kursi(p, x, y, z, r, bebas, kayu="dark_oak", lilin=("red", "black")):
    """A table (closed top trapdoor) with a candle and stools on two or four sides."""
    if not taruh(p, x, y, z, "minecraft:%s_trapdoor" % kayu, dict(_TD, facing="north")):
        return False
    b, props = _lilin(r.choice(lilin), r.choice((1, 2, 3)))
    taruh(p, x, y + 1, z, b, props)
    sisi = _ARAH if r.random() < 0.5 else (_ARAH[0], _ARAH[1])
    for a, dx, dz in sisi:
        if (x + dx, y, z + dz) not in bebas:
            # the stool's back faces away from the table, so the sitter looks at it
            taruh(p, x + dx, y, z + dz, "minecraft:%s_stairs" % kayu,
                  {"facing": a, "half": "bottom", "shape": "straight", "waterlogged": "false"})
    return True


def _meja_judi(p, x, y, z, r, bebas):
    """A dice table: two red-felt boards side by side, candles and two stools on each long side."""
    if not (H._kosong(p, x + 1, y, z) and (x + 1, y, z) not in bebas):
        return False
    for xx in (x, x + 1):
        taruh(p, xx, y, z, "minecraft:crimson_trapdoor", dict(_TD, facing="north"))
        taruh(p, xx, y + 1, z, "minecraft:red_carpet")
    for xx in (x, x + 1):
        for a, dz in (("north", -1), ("south", 1)):
            if (xx, y, z + dz) not in bebas:
                taruh(p, xx, y, z + dz, "minecraft:crimson_stairs", {"facing": a, "half": "bottom", "shape": "straight",
                                                                      "waterlogged": "false"})
    b, props = _lilin("red", 2)
    taruh(p, x - 1, y, z, b, props) if (x - 1, y, z) not in bebas else None
    return True


def _rak_tengah(p, x, y, z, r, bebas, dua=True):
    """A free-standing shelving pair: two bookcases (or chained chiseled shelves), two high."""
    blok = "minecraft:bookshelf" if r.random() < 0.6 else "minecraft:chiseled_bookshelf"
    ok = False
    for dx in (0, 1) if dua else (0,):
        if (x + dx, y, z) in bebas:
            continue
        props = None
        if blok.endswith("chiseled_bookshelf"):
            props = {"facing": "south"}
            for i in range(6):
                props["slot_%d_occupied" % i] = "true" if r.random() < 0.6 else "false"
        if taruh(p, x + dx, y, z, blok, props):
            ok = True
            taruh(p, x + dx, y + 1, z, blok, props)
    return ok


def _tumpukan(p, x, y, z, r, bebas):
    """A 2 x 2 stack of crates, sacks and hay, one or two high."""
    ok = False
    for dx in (0, 1):
        for dz in (0, 1):
            if (x + dx, y, z + dz) in bebas:
                continue
            roll = r.random()
            if roll < 0.45:
                b, props = "minecraft:barrel", {"facing": "up", "open": "false"}
            elif roll < 0.7:
                b, props = "minecraft:composter", {"level": "8"}
            else:
                b, props = "minecraft:hay_block", {"axis": "y"}
            if taruh(p, x + dx, y, z + dz, b, props):
                ok = True
                if r.random() < 0.5:
                    taruh(p, x + dx, y + 1, z + dz, "minecraft:barrel", {"facing": "up", "open": "false"})
    return ok


def _tumpukan_gelap(p, x, y, z, r, bebas):
    """A 2 x 2 stack of oil casks and chests bound with chain (demon storerooms)."""
    ok = False
    for dx in (0, 1):
        for dz in (0, 1):
            if (x + dx, y, z + dz) in bebas:
                continue
            if r.random() < 0.7:
                done = taruh(p, x + dx, y, z + dz, "minecraft:barrel", {"facing": r.choice(("up", "east", "south")), "open": "false"})
            else:
                done = taruh(p, x + dx, y, z + dz, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
                             {"id": "minecraft:chest"})
            if done:
                ok = True
                if r.random() < 0.35:
                    taruh(p, x + dx, y + 1, z + dz, "minecraft:barrel", {"facing": "up", "open": "false"})
    return ok


def _pajang_kuningan(p, x, y, z, r, bebas):
    """A brass display table: two copper plinths with lamps and pots."""
    ok = False
    for dx in (0, 1):
        if (x + dx, y, z) in bebas:
            continue
        if taruh(p, x + dx, y, z, "minecraft:waxed_cut_copper" if dx == 0 else "minecraft:waxed_exposed_cut_copper"):
            ok = True
            if r.random() < 0.55:
                taruh(p, x + dx, y + 1, z, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
            else:
                taruh(p, x + dx, y + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
    return ok


def _tenun_tengah(p, x, y, z, r, bebas):
    """A loom with a finished carpet roll and a cushion for the weaver."""
    if not taruh(p, x, y, z, "minecraft:loom", {"facing": "south"}):
        return False
    if (x + 1, y, z) not in bebas:
        _gulung_permadani(p, x + 1, y, z, "south", r)
    if (x, y, z + 1) not in bebas:
        taruh(p, x, y, z + 1, r.choice(_BANTAL))
    return True


def _rak_senjata_tengah(p, x, y, z, r, bebas, kayu="dark_oak"):
    """A double weapon rack (posts with iron blades) and a grindstone."""
    ok = False
    for dx in (0, 1):
        if (x + dx, y, z) in bebas:
            continue
        if taruh(p, x + dx, y, z, "minecraft:%s_fence" % kayu, _FENCE):
            ok = True
            taruh(p, x + dx, y + 1, z, "minecraft:iron_bars", _BARS)
    if ok and (x, y, z + 1) not in bebas and r.random() < 0.5:
        taruh(p, x, y, z + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    return ok


def _rak_tombak_tengah(p, x, y, z, r, bebas):
    return _rak_senjata_tengah(p, x, y, z, r, bebas, "acacia")


def _altar_lilin(p, x, y, z, r, bebas):
    """A low offering table with candles and a water jar (shrines)."""
    if not taruh(p, x, y, z, "minecraft:smooth_sandstone_slab", {"type": "bottom", "waterlogged": "false"}):
        return False
    for _a, dx, dz in _ARAH:
        if (x + dx, y, z + dz) not in bebas and r.random() < 0.6:
            b, props = _lilin(r.choice(("white", "light_blue")), r.choice((1, 2, 3)))
            taruh(p, x + dx, y, z + dz, b, props)
    return True


def _meja_ranjang(p, x, y, z, r, bebas):
    """Barracks middle: a footlocker pair and a stool."""
    ok = False
    for dx in (0, 1):
        if (x + dx, y, z) not in bebas and taruh(p, x + dx, y, z, "minecraft:chest", {"facing": "south", "type": "single",
                                                                                       "waterlogged": "false"},
                                                  {"id": "minecraft:chest"}):
            ok = True
    return ok


PULAU = {
    "meja_bantal": _meja_bantal,
    "meja_kursi": _meja_kursi,
    "meja_kursi_akasia": lambda p, x, y, z, r, b: _meja_kursi(p, x, y, z, r, b, "acacia", ("white", "yellow")),
    "meja_judi": _meja_judi,
    "rak": _rak_tengah,
    "tumpukan": _tumpukan,
    "tumpukan_gelap": _tumpukan_gelap,
    "kuningan": _pajang_kuningan,
    "tenun": _tenun_tengah,
    "senjata": _rak_senjata_tengah,
    "tombak": _rak_tombak_tengah,
    "altar": _altar_lilin,
    "peti": _meja_ranjang,
}


def _pintu_depan(p, x0, z0, x1, z1, y):
    """Inner cells within 3 of an opening in the room's boundary ring (doorways), plus cells near stairs/ladders."""
    out = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for _a, dx, dz in _ARAH:
                nx, nz = x + dx, z + dz
                if nx in (x0 - 1, x1 + 1) or nz in (z0 - 1, z1 + 1):
                    if not H._padat(p, nx, y, nz) and not H._padat(p, nx, y + 1, nz):
                        for ex in range(-3, 4):
                            for ez in range(-3, 4):
                                if abs(ex) + abs(ez) <= 4:
                                    out.add((x + ex, z + ez))
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for yy in (y - 1, y, y + 1):
                b = p.get(x, yy, z) if p.inside(x, yy, z) else None
                if b and ("stairs" in b or "ladder" in b or "scaffolding" in b or "_door" in b):
                    for ex in range(-2, 3):
                        for ez in range(-2, 3):
                            out.add((x + ex, z + ez))
                    break
    return out


def tengah(p, x0, z0, x1, z1, f, jenis, jarak=5, tepi=3, seed=0, isi=0.85):
    """Furniture islands of kind `jenis` (key of PULAU, or a tuple of keys used in turn) in the open middle of the room
    (x0..x1, z0..z1) on floor f: a grid of step `jarak` inset `tepi` from the walls; an island stands only where its
    3 x 3 neighbourhood is empty floor with headroom and away from doorways and life points. Returns islands placed."""
    x0, x1 = min(x0, x1), max(x0, x1)
    z0, z1 = min(z0, z1), max(z0, z1)
    y = f + 1
    kinds = (jenis,) if isinstance(jenis, str) else tuple(jenis)
    r = random.Random((x0 * 92821) ^ (z0 * 68917) ^ (f * 3571) ^ (x1 * 131) ^ (z1 * 7) ^ seed ^ 0x51ED)
    bebas = H._bebas(p)
    pintu = _pintu_depan(p, x0, z0, x1, z1, y)
    n = 0
    i = 0
    for gx in range(x0 + tepi, x1 - tepi + 1, jarak):
        for gz in range(z0 + tepi, z1 - tepi + 1, jarak):
            if r.random() > isi:
                continue
            ok = True
            for ex in range(-1, 3):
                for ez in range(-1, 2):
                    cx, cz = gx + ex, gz + ez
                    if (cx, cz) in pintu or (cx, y, cz) in bebas or not H._kosong(p, cx, y, cz) or not H._kosong(p, cx, y + 1, cz) \
                            or not H._padat(p, cx, f, cz):
                        ok = False
                        break
                if not ok:
                    break
            if not ok:
                continue
            fn = PULAU[kinds[i % len(kinds)]]
            if fn(p, gx, y, gz, r, bebas):
                n += 1
                i += 1
    return n


def kamar(p, x0, z0, x1, z1, f, gaya, pulau=None, jarak=5, isi=0.55, rug=True, seed=0):
    """Walls in style `gaya` (hias_tema.ruang), then islands `pulau` in the middle when the room is large enough.
    Islands go first so the wall pieces and the rug never take their space; returns pieces + islands."""
    n = 0
    if pulau and (x1 - x0) >= 8 and (z1 - z0) >= 8:
        n += tengah(p, x0, z0, x1, z1, f, pulau, jarak=jarak, seed=seed)
        rug = False
    n += H.ruang(p, x0, z0, x1, z1, f, gaya, isi=isi, rug=rug, seed=seed)
    return n

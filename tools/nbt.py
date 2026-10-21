"""Tiny dependency-free NBT reader/writer and structure-template builder (DESIGN 34.3).

Every worldgen module (K, R, L, B, D, T of DESIGN 34) writes its structure templates through this file, so the format
is fixed in ONE place. Verified against the 26.3 sources (reference/mcsrc):
  - StructureTemplate.save/load: root compound {DataVersion, size:[I x3], palette:[state], blocks:[{pos:[I x3],
    state:I, nbt?}], entities:[{pos:[D x3], blockPos:[I x3], nbt}]}, gzip compressed (TemplateSource.readStructure ->
    NbtIo.readCompressed), file data/<ns>/structure/<path>.nbt (StructureTemplateManager RESOURCE_STRUCTURE_LISTER).
  - NbtUtils.writeBlockState: a palette entry is {id:"minecraft:stone", properties:{facing:"north"}} (lower case keys
    in 26.3; "properties" is left out for a single-state block).
  - SharedConstants.WORLD_VERSION = 5023: templates carry DataVersion 5023, so the DataFixer leaves them untouched.
  - StructureTemplate.buildInfoList: blocks are stored full blocks first, then other blocks, then block entities, each
    sorted by y, x, z. We follow the same order (the "full block" test is a name heuristic, see _is_full).
  - Block entity nbt carries its "id" (BlockEntity.saveWithId); entity nbt must carry "id" (EntityType.create).

Usage:
    from nbt import Template, Byte, Short, Long, Float
    t = Template(9, 6, 9)
    t.set(0, 0, 0, "minecraft:stone_bricks")
    t.set(4, 1, 4, "minecraft:lantern", {"hanging": "false"})
    t.jigsaw(4, 0, 8, "south_up", name="kultivator:kota/street", target="kultivator:kota/house",
             pool="kultivator:kota/houses")
    t.entity(4.5, 1.0, 4.5, {"id": "kultivator:dunia_anchor", "kind": "kota/lelang"})
    t.save("src/main/resources/data/kultivator/structure/kota/center_1.nbt")

    tag = read_file("some.nbt")          # -> (root_name, python value) with typed wrappers kept

Plain Python values map to tags: dict -> Compound, list -> List (element type from the first element), str -> String,
bool -> Byte, int -> Int, float -> Double. Use the wrappers Byte/Short/Long/Float/IntArray/LongArray/ByteArray for the
other types. The output is deterministic (gzip mtime 0, insertion-ordered keys), so regenerating gives the same bytes.
"""
import gzip
import io
import os
import struct

DATA_VERSION = 5023  # net.minecraft.SharedConstants.WORLD_VERSION in 26.3

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


# ----------------------------------------------------------------------------------------------------------- wrappers
class _Num:
    __slots__ = ("v",)

    def __init__(self, v):
        self.v = v

    def __eq__(self, other):
        return type(self) is type(other) and self.v == other.v

    def __hash__(self):
        return hash((type(self).__name__, self.v))

    def __repr__(self):
        return "%s(%r)" % (type(self).__name__, self.v)


class Byte(_Num):
    pass


class Short(_Num):
    pass


class Int(_Num):
    pass


class Long(_Num):
    pass


class Float(_Num):
    pass


class Double(_Num):
    pass


class ByteArray(_Num):
    pass


class IntArray(_Num):
    pass


class LongArray(_Num):
    pass


def _type_of(value):
    if isinstance(value, Byte) or isinstance(value, bool):
        return TAG_BYTE
    if isinstance(value, Short):
        return TAG_SHORT
    if isinstance(value, Int) or isinstance(value, int):
        return TAG_INT
    if isinstance(value, Long):
        return TAG_LONG
    if isinstance(value, Float):
        return TAG_FLOAT
    if isinstance(value, Double) or isinstance(value, float):
        return TAG_DOUBLE
    if isinstance(value, ByteArray):
        return TAG_BYTE_ARRAY
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, (list, tuple)):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    if isinstance(value, IntArray):
        return TAG_INT_ARRAY
    if isinstance(value, LongArray):
        return TAG_LONG_ARRAY
    raise TypeError("no NBT type for %r" % (value,))


def _raw(value):
    return value.v if isinstance(value, _Num) else value


# ------------------------------------------------------------------------------------------------------------- writer
def _write_str(out, s):
    # Java "modified UTF-8" equals UTF-8 for text without NUL and outside the BMP; templates only use plain ids.
    data = s.encode("utf-8")
    if len(data) > 0xFFFF:
        raise ValueError("string too long for NBT")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out, tag_type, value):
    v = _raw(value)
    if tag_type == TAG_BYTE:
        out.write(struct.pack(">b", int(v)))
    elif tag_type == TAG_SHORT:
        out.write(struct.pack(">h", v))
    elif tag_type == TAG_INT:
        out.write(struct.pack(">i", v))
    elif tag_type == TAG_LONG:
        out.write(struct.pack(">q", v))
    elif tag_type == TAG_FLOAT:
        out.write(struct.pack(">f", v))
    elif tag_type == TAG_DOUBLE:
        out.write(struct.pack(">d", v))
    elif tag_type == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(bytes((b & 0xFF) for b in v))
    elif tag_type == TAG_STRING:
        _write_str(out, v)
    elif tag_type == TAG_LIST:
        if len(v) == 0:
            out.write(struct.pack(">bi", TAG_END, 0))
            return
        elem = _type_of(v[0])
        for item in v:
            if _type_of(item) != elem:
                raise TypeError("NBT list elements must share one type: %r" % (v,))
        out.write(struct.pack(">bi", elem, len(v)))
        for item in v:
            _write_payload(out, elem, item)
    elif tag_type == TAG_COMPOUND:
        for key, item in v.items():
            t = _type_of(item)
            out.write(struct.pack(">b", t))
            _write_str(out, key)
            _write_payload(out, t, item)
        out.write(struct.pack(">b", TAG_END))
    elif tag_type == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for i in v:
            out.write(struct.pack(">i", i))
    elif tag_type == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for i in v:
            out.write(struct.pack(">q", i))
    else:
        raise TypeError("bad tag type %d" % tag_type)


def to_bytes(root, name=""):
    """Uncompressed NBT of a root compound (a named tag, as NbtIo.write)."""
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND))
    _write_str(out, name)
    _write_payload(out, TAG_COMPOUND, root)
    return out.getvalue()


def write_file(path, root, name="", compressed=True):
    data = to_bytes(root, name)
    d = os.path.dirname(os.path.abspath(path))
    os.makedirs(d, exist_ok=True)
    if compressed:
        buf = io.BytesIO()
        with gzip.GzipFile(filename="", mode="wb", fileobj=buf, mtime=0) as gz:
            gz.write(data)
        data = buf.getvalue()
    with open(path, "wb") as f:
        f.write(data)


# ------------------------------------------------------------------------------------------------------------- reader
class _Reader:
    def __init__(self, data):
        self.data = data
        self.i = 0

    def take(self, n):
        b = self.data[self.i:self.i + n]
        if len(b) != n:
            raise ValueError("truncated NBT")
        self.i += n
        return b

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))

    def string(self):
        (n,) = self.unpack(">H")
        return self.take(n).decode("utf-8", errors="replace")

    def payload(self, t):
        if t == TAG_BYTE:
            return Byte(self.unpack(">b")[0])
        if t == TAG_SHORT:
            return Short(self.unpack(">h")[0])
        if t == TAG_INT:
            return self.unpack(">i")[0]
        if t == TAG_LONG:
            return Long(self.unpack(">q")[0])
        if t == TAG_FLOAT:
            return Float(self.unpack(">f")[0])
        if t == TAG_DOUBLE:
            return self.unpack(">d")[0]
        if t == TAG_BYTE_ARRAY:
            (n,) = self.unpack(">i")
            return ByteArray(list(struct.unpack(">%db" % n, self.take(n))))
        if t == TAG_STRING:
            return self.string()
        if t == TAG_LIST:
            elem, n = self.unpack(">bi")
            return [self.payload(elem) for _ in range(n)]
        if t == TAG_COMPOUND:
            out = {}
            while True:
                (ct,) = self.unpack(">b")
                if ct == TAG_END:
                    return out
                key = self.string()
                out[key] = self.payload(ct)
        if t == TAG_INT_ARRAY:
            (n,) = self.unpack(">i")
            return IntArray(list(struct.unpack(">%di" % n, self.take(4 * n))))
        if t == TAG_LONG_ARRAY:
            (n,) = self.unpack(">i")
            return LongArray(list(struct.unpack(">%dq" % n, self.take(8 * n))))
        raise ValueError("bad tag type %d at %d" % (t, self.i))


def from_bytes(data):
    """(root name, root compound) from uncompressed or gzip NBT bytes."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    (t,) = r.unpack(">b")
    if t != TAG_COMPOUND:
        raise ValueError("root is not a compound")
    name = r.string()
    return name, r.payload(TAG_COMPOUND)


def read_file(path):
    with open(path, "rb") as f:
        return from_bytes(f.read())


# ----------------------------------------------------------------------------------------------- structure templates
# Names that are NOT a full collision cube (placed after the full blocks, like vanilla's "other blocks" list). Only the
# order of the stored list depends on it (StructureTemplate.placeInWorld places in list order), so a wrong guess is
# harmless; it only keeps torches, lanterns and doors after the blocks they hang on.
_PARTIAL_SUFFIXES = (
    "_slab", "_stairs", "_fence", "_fence_gate", "_wall", "_pane", "_bars", "_door", "_trapdoor", "torch", "lantern",
    "_chain", "chain", "_button", "_pressure_plate", "_carpet", "_sign", "_hanging_sign", "_banner", "_flower", "_sapling",
    "_vine", "vine", "vines", "ladder", "rail", "candle", "_candle", "flower_pot", "_head", "_skull", "_bed", "_rod",
    "lever", "snow", "_petals", "_leaves", "campfire", "bell", "anvil", "chest", "scaffolding", "lily_pad", "kelp",
    "seagrass", "_coral", "_coral_fan", "dripleaf", "azalea", "cobweb", "sea_pickle", "lichen", "_roots", "_mushroom",
    "water", "lava", "air", "cave_air", "void_air", "jigsaw", "light", "barrier", "brewing_stand", "cauldron", "lectern",
    "grindstone", "stonecutter", "composter", "hopper", "enchanting_table", "dirt_path", "farmland", "cake",
    "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush", "poppy", "dandelion", "cornflower",
    "lily_of_the_valley", "allium", "azure_bluet", "oxeye_daisy", "_tulip", "peony", "rose_bush", "lilac", "sunflower",
    "bamboo", "sugar_cane", "decorated_pot", "end_rod", "conduit", "pointed_dripstone", "amethyst_cluster", "_bud",
)


_FULL_NAMES = ("sea_lantern", "snow_block", "bamboo_block", "bamboo_planks", "bamboo_mosaic", "stripped_bamboo_block")


def _is_full(block_id, props):
    name = block_id.split(":", 1)[-1]
    if props and props.get("waterlogged") == "true":
        return False
    if name.startswith("potted_"):
        return False
    if name in _FULL_NAMES:
        return True
    return not any(name == s or name.endswith(s) for s in _PARTIAL_SUFFIXES)


def _state_key(block_id, props):
    if ":" not in block_id:
        block_id = "minecraft:" + block_id
    items = tuple(sorted((str(k), str(v)) for k, v in (props or {}).items()))
    return block_id, items


class Template:
    """A structure template under construction. Unset cells are structure_void (left untouched in the world).

    set(x, y, z, block, props=None, nbt=None) places one state; "minecraft:air" is stored explicitly (clears terrain).
    Positions outside the size raise, so a piece never silently loses a block.
    """

    def __init__(self, sx, sy, sz):
        if min(sx, sy, sz) < 1 or max(sx, sz) > 48 or sy > 384:
            # 48: StructureBlockEntity.MAX_SIZE_PER_AXIS; jigsaw pieces larger than that are legal but slow and hard to
            # edit in game, so the shared library refuses them (split a big building into pieces instead).
            raise ValueError("template size %dx%dx%d outside 1..48 (height 1..384)" % (sx, sy, sz))
        self.size = (sx, sy, sz)
        self.cells = {}      # (x, y, z) -> (state_key, nbt or None)
        self.entities = []   # (x, y, z, nbt)

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def set(self, x, y, z, block, props=None, nbt=None):
        if not self.inside(x, y, z):
            raise IndexError("(%d,%d,%d) outside template %r" % (x, y, z, self.size))
        key = _state_key(block, props)
        if key[0] == "minecraft:structure_void":
            self.cells.pop((x, y, z), None)
            return
        self.cells[(x, y, z)] = (key, dict(nbt) if nbt else None)

    def get(self, x, y, z):
        cell = self.cells.get((x, y, z))
        return None if cell is None else cell[0][0]

    def clear(self, x, y, z):
        self.cells.pop((x, y, z), None)

    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state="minecraft:air", joint=None,
               placement_priority=0, selection_priority=0):
        """A jigsaw connector (JigsawBlockEntity: name, target, pool, final_state, joint, placement/selection priority).

        orientation is a FrontAndTop name: "north_up" ... for side connectors, "up_north" / "down_south" ... for
        vertical ones. joint defaults like StructureTemplate.getDefaultJointType: rollable when the front is vertical.
        """
        front = orientation.split("_")[0]
        if joint is None:
            joint = "rollable" if front in ("up", "down") else "aligned"
        nbt = {
            "id": "minecraft:jigsaw",
            "name": name,
            "target": target,
            "pool": pool,
            "final_state": final_state,
            "joint": joint,
            "placement_priority": int(placement_priority),
            "selection_priority": int(selection_priority),
        }
        self.set(x, y, z, "minecraft:jigsaw", {"orientation": orientation}, nbt)

    def entity(self, x, y, z, nbt):
        """An entity placed at generation (SinglePoolElement sets ignoreEntities false). nbt must contain "id"."""
        if "id" not in nbt:
            raise ValueError("entity nbt needs an id")
        bx, by, bz = int(x // 1), int(y // 1), int(z // 1)
        if not self.inside(bx, by, bz):
            raise IndexError("entity (%r,%r,%r) outside template %r" % (x, y, z, self.size))
        self.entities.append((float(x), float(y), float(z), dict(nbt)))

    def to_tag(self):
        palette = []
        index = {}
        full, other, with_nbt = [], [], []
        for pos in sorted(self.cells, key=lambda p: (p[1], p[0], p[2])):
            key, nbt = self.cells[pos]
            if key not in index:
                index[key] = len(palette)
                entry = {"id": key[0]}
                if key[1]:
                    entry["properties"] = {k: v for k, v in key[1]}
                palette.append(entry)
            block = {"pos": [pos[0], pos[1], pos[2]], "state": index[key]}
            if nbt:
                block["nbt"] = nbt
                with_nbt.append(block)
            elif _is_full(key[0], dict(key[1])):
                full.append(block)
            else:
                other.append(block)
        entities = []
        for x, y, z, nbt in self.entities:
            entities.append({"pos": [x, y, z], "blockPos": [int(x // 1), int(y // 1), int(z // 1)], "nbt": nbt})
        return {
            "size": list(self.size),
            "entities": entities,
            "blocks": full + other + with_nbt,
            "palette": palette,
            "DataVersion": DATA_VERSION,
        }

    def save(self, path):
        write_file(path, self.to_tag())
        return path


def describe(root):
    """A short human summary of a template root compound (used by the self test and by module generators)."""
    size = root.get("size")
    palette = root.get("palette") or [p for p in (root.get("palettes") or [[]])[0]]
    blocks = root.get("blocks", [])
    counts = {}
    for b in blocks:
        state = palette[b["state"]]
        name = state.get("id") or state.get("Name")
        counts[name] = counts.get(name, 0) + 1
    top = sorted(counts.items(), key=lambda kv: -kv[1])[:6]
    return "size=%s palette=%d blocks=%d entities=%d DataVersion=%s top=%s" % (
        size, len(palette), len(blocks), len(root.get("entities", [])), root.get("DataVersion"), top)


if __name__ == "__main__":
    import sys

    for p in sys.argv[1:]:
        _, r = read_file(p)
        print(p)
        print("  " + describe(r))
        pal = r.get("palette") or (r.get("palettes") or [[]])[0]
        print("  first palette entries:", pal[:3])

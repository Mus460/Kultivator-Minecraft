"""Models and textures of DESIGN 42 "Kota yang Hidup" (42.12.3-42.12.4, module 42V-M: everything except the animals and
the lion dance, which are 42V-H's). Everything is ours: cuboid meshes, hand-placed pixel sprites and glyphs, procedural
materials; nothing is downloaded.

One source of truth per entity: a mesh is written here once as named parts (pivot + rotation, mob model space: y down,
24 = the ground, front = -z) with boxes that carry a material (and optional per-face decals). From it the script
  * packs every box's box-UV region into an atlas and paints it (one PNG per look, plus a glow PNG when a material glows),
  * writes the Java meshes  src/client/java/dev/kultivator/client/dunia/kota42/model/KotaMesh.java  (generated; the
    renderers in client/dunia/kota42/render look the parts up by name and animate them),
  * renders a textured 3/4 preview of every mesh with the exact box-UV mapping of ModelPart.Cube (read it with Read).

Writes (assets/kultivator/...):
  textures/entity/kota42/<mesh>[_<look>][_pancar].png   entity atlases (panggung, tenda, lapak_hantu, api_kecil, ...)
  textures/entity/kota42/wayang_<naskah>.png            4 shadow-puppet atlases (64 x 64, 6 figures, arm joints)
  textures/entity/kota42/lentera.png (+ _pancar)        city lanterns (5 kinds; paper painted pale, tinted in code)
  textures/entity/kota42/tandu_pengantin.png            the red bridal look of the 38 sedan chair (recoloured)
  textures/kota42/glif/<id>.png                         19 hand-drawn glyphs 16 x 16 (42.12.4) for 42V-F
  textures/kota42/panji/<keadaan>.png                   banners 32 x 64 per town state (42.9.3)
  textures/kota42/kuplet_<kiri|kanan>.png               New Year couplets 8 x 48
  textures/item/kota42/*.png, models/item/kota42/*.json, items/<id>.json      the 22 items of 42.12.3
  textures/block/kota42/*.png, models/block/kota42/*.json, blockstates/<id>.json, models/item/kota42/<block>.json

Run: python -I tools/gen_kota_hidup_assets.py [preview_dir]
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from png import Image  # noqa: E402
from gen_tempat_models import rgb, mix, shade, Noise, stable, write_json, sheet  # noqa: E402
from sosok_lib import read_png  # noqa: E402

REPO = os.path.normpath(os.path.join(HERE, ".."))
ROOT = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator")
ENT_TEX = os.path.join(ROOT, "textures", "entity", "kota42")
GLYPH_TEX = os.path.join(ROOT, "textures", "kota42", "glif")
PANJI_TEX = os.path.join(ROOT, "textures", "kota42", "panji")
KOTA_TEX = os.path.join(ROOT, "textures", "kota42")
ITEM_TEX = os.path.join(ROOT, "textures", "item", "kota42")
ITEM_MODEL = os.path.join(ROOT, "models", "item", "kota42")
ITEM_DEF = os.path.join(ROOT, "items")
BLOCK_TEX = os.path.join(ROOT, "textures", "block", "kota42")
BLOCK_MODEL = os.path.join(ROOT, "models", "block", "kota42")
BLOCKSTATE = os.path.join(ROOT, "blockstates")
JAVA_MESH = os.path.join(REPO, "src", "client", "java", "dev", "kultivator", "client", "dunia", "kota42", "model", "KotaMesh.java")
TANDU_SRC = os.path.join(ROOT, "textures", "entity", "tempat", "tema", "tandu_pasir.png")

WRITTEN = []
ERRORS = []


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    WRITTEN.append(os.path.relpath(path, REPO).replace("\\", "/"))


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def rgba(c, a=255):
    return (clamp(c[0]), clamp(c[1]), clamp(c[2]), a if len(c) < 4 else c[3])


# ================================================================================================ mesh spec

FACES = ("top", "bottom", "right", "front", "left", "back")


class Box:
    """One ModelPart cube: min corner (x, y, z), size (w, h, d) in model pixels, part-local. mat = material name of the
    mesh's palette; decal = {face: painter(fx, fy, fw, fh) -> rgba or None}; share = boxes with the same key and size
    reuse one uv region; k = texel density (1, 2 or 4; Java texScale 1/k)."""

    def __init__(self, part, x, y, z, w, h, d, mat, decal=None, share=None, k=1, grow=0.0):
        self.part = part
        # entityCutout does not cull: a flat card gets 0.1 px of depth so its two faces never z-fight
        if w == 0:
            x, w = x - 0.05, 0.1
        if h == 0:
            y, h = y - 0.05, 0.1
        if d == 0:
            z, d = z - 0.05, 0.1
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = w, h, d
        self.mat = mat
        self.decal = decal or {}
        self.share = share
        self.k = k
        self.grow = grow
        self.u = self.v = None

    def region(self):
        k = self.k
        return int(math.ceil(k * (2 * self.d + 2 * self.w) - 1e-6)), int(math.ceil(k * (self.d + self.h) - 1e-6))

    def face_rects(self):
        """face -> (u0, v0, u1, v1) in atlas texels (floats), the ModelPart.Cube layout (region of the world top at
        (d, 0), bottom at (d + w, 0); row 2: right(-x), front(-z), left(+x), back(+z))."""
        k, u, v = self.k, self.u, self.v
        w, h, d = self.w * k, self.h * k, self.d * k
        return {
            "top": (u + d, v, u + d + w, v + d),
            "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h),
            "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
            "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        }


class Part:
    def __init__(self, mesh, name, parent, pivot, rot):
        self.mesh = mesh
        self.name = name
        self.parent = parent
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.boxes = []
        self.children = []

    def part(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        return self.mesh.add_part(name, self, pivot, rot)

    def b(self, x, y, z, w, h, d, mat, **kw):
        """A box by its min corner (part-local)."""
        self.boxes.append(Box(self, x, y, z, w, h, d, mat, **kw))
        return self

    def c(self, cx, cy, cz, w, h, d, mat, **kw):
        """A box by its centre (part-local)."""
        return self.b(cx - w / 2.0, cy - h / 2.0, cz - d / 2.0, w, h, d, mat, **kw)

    def g(self, cx, y0, cz, w, h, d, mat, **kw):
        """A box standing on y0 (its bottom at y0, model y down), centred in x and z."""
        return self.b(cx - w / 2.0, y0 - h, cz - d / 2.0, w, h, d, mat, **kw)


def deg(a):
    return a * math.pi / 180.0


class Mesh:
    """A named entity mesh. looks = {look: {material: painter}}; the first look is the default texture."""

    def __init__(self, name, width, looks, glow=(), java=None, doc=""):
        self.name = name
        self.width = width
        self.looks = looks
        self.glow = set(glow)
        self.java = java or name
        self.doc = doc
        self.parts = {}
        self.root = Part(self, "root", None, (0, 0, 0), (0, 0, 0))
        self.height = 0
        MESHES[name] = self

    def add_part(self, name, parent, pivot, rot):
        if name in self.parts or name == "root":
            raise ValueError("%s: duplicate part %s" % (self.name, name))
        p = Part(self, name, parent, pivot, rot)
        parent.children.append(p)
        self.parts[name] = p
        return p

    def part(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        return self.add_part(name, self.root, pivot, rot)

    def all_parts(self):
        out = []

        def walk(p):
            out.append(p)
            for ch in p.children:
                walk(ch)
        walk(self.root)
        return out

    def boxes(self):
        return [b for p in self.all_parts() for b in p.boxes]


MESHES = {}


# ================================================================================================ uv packing + painting

def pack(mesh):
    """Shelf packing of every box region (k-aligned), tallest first; shared regions once."""
    shared = {}
    todo = []
    for b in mesh.boxes():
        if b.share is not None:
            key = (b.share, b.w, b.h, b.d, b.k)
            if key in shared:
                continue
            shared[key] = b
        todo.append(b)
    todo.sort(key=lambda b: (-b.region()[1], -b.region()[0]))
    W = mesh.width
    x = y = shelf = 0
    for b in todo:
        rw, rh = b.region()
        k = b.k
        if rw > W:
            ERRORS.append("%s: box %sx%sx%s region %d wider than %d" % (mesh.name, b.w, b.h, b.d, rw, W))
            rw = W
        x = (x + k - 1) // k * k
        if x + rw > W:
            x = 0
            y += shelf
            shelf = 0
        y = (y + k - 1) // k * k if shelf == 0 else y
        b.u, b.v = x, y
        x += rw
        shelf = max(shelf, rh)
    for b in mesh.boxes():
        if b.share is not None:
            src = shared[(b.share, b.w, b.h, b.d, b.k)]
            b.u, b.v = src.u, src.v
    h = y + shelf
    mesh.height = max(16, (h + 15) // 16 * 16)


def paint(mesh, look, glow_only=False):
    img = Image(mesh.width, mesh.height)
    mats = mesh.looks[look]
    done = set()
    for b in mesh.boxes():
        key = (b.u, b.v, b.w, b.h, b.d)
        if key in done:
            continue
        done.add(key)
        if glow_only and b.mat not in mesh.glow:
            continue
        painter = mats.get(b.mat)
        if painter is None:
            ERRORS.append("%s/%s: material %s missing" % (mesh.name, look, b.mat))
            continue
        rng = random.Random(stable(mesh.name, look, b.u, b.v))
        for face, (u0, v0, u1, v1) in b.face_rects().items():
            fw, fh = u1 - u0, v1 - v0
            if fw <= 0 or fh <= 0:
                continue
            dec = b.decal.get(face) or b.decal.get("all")
            for ty in range(int(math.floor(v0)), int(math.ceil(v1))):
                for tx in range(int(math.floor(u0)), int(math.ceil(u1))):
                    if not (u0 <= tx + 0.5 < u1 + 1e-6 and v0 <= ty + 0.5 < v1 + 1e-6):
                        continue
                    fx, fy = (tx - u0) / b.k, (ty - v0) / b.k
                    c = None
                    if dec is not None:
                        c = dec(fx, fy, fw / b.k, fh / b.k)
                    if c is None:
                        c = painter(fx, fy, fw / b.k, fh / b.k, face, rng)
                    if c is None:
                        continue
                    img.set(tx, ty, rgba(c))
    return img


# ================================================================================================ java emission

def jf(v):
    s = ("%.4f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    if s == "-0.0":
        s = "0.0"
    return s + "F"


def java_part_pose(p):
    x, y, z = p.pivot
    rx, ry, rz = p.rot
    if rx == 0 and ry == 0 and rz == 0:
        if x == 0 and y == 0 and z == 0:
            return "PartPose.ZERO"
        return "PartPose.offset(%s, %s, %s)" % (jf(x), jf(y), jf(z))
    return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (jf(x), jf(y), jf(z), jf(rx), jf(ry), jf(rz))


def java_cubes(p):
    if not p.boxes:
        return "CubeListBuilder.create()"
    out = ["CubeListBuilder.create()"]
    for b in p.boxes:
        k = b.k
        dims = "%s, %s, %s, %s, %s, %s" % (jf(b.x), jf(b.y), jf(b.z), jf(b.w), jf(b.h), jf(b.d))
        out.append(".texOffs(%d, %d)" % (b.u // k, b.v // k))
        if k == 1 and b.grow == 0:
            out.append(".addBox(%s)" % dims)
        else:
            out.append(".addBox(%s, new CubeDeformation(%s), %s, %s)" % (dims, jf(b.grow), jf(1.0 / k), jf(1.0 / k)))
    return "\n\t\t\t\t".join(out)


def java_mesh_method(mesh):
    lines = ["\t/** {@code %s}: %s Texture %d x %d. */" % (mesh.name, mesh.doc, mesh.width, mesh.height),
             "\tpublic static LayerDefinition %s() {" % mesh.java,
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    var = {mesh.root: "root"}
    n = 0
    for p in mesh.all_parts()[1:]:
        n += 1
        v = "p%d" % n
        var[p] = v
        needs_var = bool(p.children)
        lhs = "PartDefinition %s = " % v if needs_var else ""
        lines.append("\t\t%s%s.addOrReplaceChild(\"%s\", %s,\n\t\t\t\t%s);" % (lhs, var[p.parent], p.name, java_cubes(p), java_part_pose(p)))
    lines.append("\t\treturn LayerDefinition.create(mesh, %d, %d);" % (mesh.width, mesh.height))
    lines.append("\t}")
    return "\n".join(lines)


def write_java():
    body = "\n\n".join(java_mesh_method(m) for m in MESHES.values())
    names = ", ".join("{@link #%s()}" % m.java for m in MESHES.values())
    src = """package dev.kultivator.client.dunia.kota42.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by {@code tools/gen_kota_hidup_assets.py} (42V-M, DESIGN 42.12.4): do not edit, change the mesh in the
 * generator and run it again (it also repaints the matching atlases under {@code textures/entity/kota42/}, whose box-UV
 * regions are packed from these very boxes). Mob model space: y down, 24 = the ground, front = -z. The renderers of
 * {@code client/dunia/kota42/render} look parts up by name ({@code ModelPart.createPartLookup}) and animate them.
 * <p>
 * Meshes: %s.
 */
public final class KotaMesh {

%s

	private KotaMesh() {
	}
}
""" % (names, body)
    os.makedirs(os.path.dirname(JAVA_MESH), exist_ok=True)
    with open(JAVA_MESH, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
    WRITTEN.append(os.path.relpath(JAVA_MESH, REPO).replace("\\", "/"))


# ================================================================================================ textured preview

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_rot(rx, ry, rz):
    """Quaternionf.rotationZYX(rz, ry, rx) as a matrix: Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    Ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    Rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(Rz, mat_mul(Ry, Rx))


def mat_tr(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def face_quads(b):
    """face -> (4 corners part-local, matching uv corners), exactly the vertex/uv order of ModelPart.Cube/Polygon."""
    x0, y0, z0 = b.x - b.grow, b.y - b.grow, b.z - b.grow
    x1, y1, z1 = b.x + b.w + b.grow, b.y + b.h + b.grow, b.z + b.d + b.grow
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    r = b.face_rects()
    out = {}

    def poly(verts, rect, flipv=False):
        u0, v0, u1, v1 = rect
        if flipv:
            v0, v1 = v1, v0
        # Polygon.remap: v[0] -> (u1, v0), v[1] -> (u0, v0), v[2] -> (u0, v1), v[3] -> (u1, v1)
        return verts, [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]
    out["top"] = poly([l1, l0, t0, t1], r["top"])          # DOWN polygon (model -y = world top)
    out["bottom"] = poly([t2, t3, l3, l2], r["bottom"], True)  # UP polygon (u2, v1, u22, v0)
    out["right"] = poly([t0, l0, l3, t3], r["right"])
    out["front"] = poly([t1, t0, t3, t2], r["front"])
    out["left"] = poly([l1, t1, t2, l2], r["left"])
    out["back"] = poly([l0, l1, l2, l3], r["back"])
    return out


def preview(mesh, look, path, hidden=(), size=420, yaw=-35.0, pitch=28.0, overrides=None):
    """Textured 3/4 view: world = (mx, 24 - my, -mz), camera at the front-right above. hidden = part names skipped with
    their children; overrides = {part: {"pivot": .., "rot": ..}} to pose a part."""
    tex = paint(mesh, look)
    overrides = overrides or {}
    quads = []

    def walk(p, m, vis):
        pose_pivot = overrides.get(p.name, {}).get("pivot", p.pivot)
        pose_rot = overrides.get(p.name, {}).get("rot", p.rot)
        m2 = mat_mul(m, mat_mul(mat_tr(*pose_pivot), mat_rot(*pose_rot)))
        v = vis and p.name not in hidden
        if v:
            for b in p.boxes:
                for face, (verts, uvs) in face_quads(b).items():
                    pts = [apply(m2, q) for q in verts]
                    quads.append((pts, uvs, face, b.mat in mesh.glow))
        for ch in p.children:
            walk(ch, m2, v)
    walk(mesh.root, [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], True)
    if not quads:
        return
    ya, pa = math.radians(yaw), math.radians(pitch)

    def proj(p):
        x, y, z = p[0], 24.0 - p[1], -p[2]
        x1 = x * math.cos(ya) + z * math.sin(ya)
        z1 = -x * math.sin(ya) + z * math.cos(ya)
        y1 = y * math.cos(pa) - z1 * math.sin(pa)
        z2 = y * math.sin(pa) + z1 * math.cos(pa)
        return x1, y1, z2
    allp = [proj(q) for qd in quads for q in qd[0]]
    lo = [min(p[i] for p in allp) for i in range(2)]
    hi = [max(p[i] for p in allp) for i in range(2)]
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1.0)
    s = (size - 20) / span
    img = Image(size, size, (52, 58, 66, 255))
    light = {"top": 1.0, "bottom": 0.45, "front": 0.86, "back": 0.6, "left": 0.72, "right": 0.78}
    tris = []
    for pts, uvs, face, lit in quads:
        pp = [proj(p) for p in pts]
        # back-face culling (entity cutout culls): screen-space winding
        (ax, ay, _), (bx, by, _), (cx, cy, _) = pp[0], pp[1], pp[2]
        area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        sp = [((p[0] - lo[0]) * s + 10, size - ((p[1] - lo[1]) * s + 10), p[2]) for p in pp]
        tris.append((area, sp, uvs, face, lit))
    # the camera looks along -z2: larger z2 is nearer, so texels are drawn in ascending depth
    texels = []
    for area, sp, uvs, face, lit in tris:
        if area <= 1e-9:
            continue
        (u_a, v_a), (u_b, v_b), (u_c, v_c), (u_d, v_d) = uvs
        umin, umax = min(u_a, u_b, u_c, u_d), max(u_a, u_b, u_c, u_d)
        vmin, vmax = min(v_a, v_b, v_c, v_d), max(v_a, v_b, v_c, v_d)
        nu, nv = max(1, int(round(umax - umin))), max(1, int(round(vmax - vmin)))
        # bilinear map: corners 0..3 with uv; param (a along v0->v1 edge order) -> use corners directly
        P = sp
        for j in range(nv):
            for i in range(nu):
                a0, a1, b0, b1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv

                def at(a, bb):
                    # corner order: 1 (u0,v0), 0 (u1,v0), 3 (u1,v1), 2 (u0,v1)
                    top = (P[1][0] + (P[0][0] - P[1][0]) * a, P[1][1] + (P[0][1] - P[1][1]) * a, P[1][2] + (P[0][2] - P[1][2]) * a)
                    bot = (P[2][0] + (P[3][0] - P[2][0]) * a, P[2][1] + (P[3][1] - P[2][1]) * a, P[2][2] + (P[3][2] - P[2][2]) * a)
                    return (top[0] + (bot[0] - top[0]) * bb, top[1] + (bot[1] - top[1]) * bb, top[2] + (bot[2] - top[2]) * bb)
                corners = [at(a0, b0), at(a1, b0), at(a1, b1), at(a0, b1)]
                # uv of this texel: u from uv[1].u to uv[0].u, v from uv[1].v to uv[2].v
                uu = uvs[1][0] + (uvs[0][0] - uvs[1][0]) * (i + 0.5) / nu
                vv = uvs[1][1] + (uvs[2][1] - uvs[1][1]) * (j + 0.5) / nv
                tx, ty = int(math.floor(uu)) % tex.width, int(math.floor(vv))
                if not (0 <= ty < tex.height):
                    continue
                c = tex.get(tx, ty)
                if c[3] < 128:
                    continue
                f = 1.12 if lit else light[face]
                col = (c[0] * f, c[1] * f, c[2] * f, 255)
                depth = sum(q[2] for q in corners) / 4
                texels.append((depth, [(q[0], q[1]) for q in corners], col))
    texels.sort(key=lambda t: t[0])
    for _, pts, col in texels:
        fillpoly(img, pts, col)
    save_preview(img, path)


def fillpoly(img, pts, col):
    ys = [p[1] for p in pts]
    for y in range(max(0, int(min(ys))), min(img.height, int(max(ys)) + 2)):
        xs = []
        n = len(pts)
        for i in range(n):
            (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
            if (y0 <= y + 0.5 < y1) or (y1 <= y + 0.5 < y0):
                xs.append(x0 + (y + 0.5 - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for k in range(0, len(xs) - 1, 2):
            for x in range(max(0, int(math.floor(xs[k] + 0.5))), min(img.width, int(math.floor(xs[k + 1] + 0.5)) + 1)):
                img.set(x, y, col)


PREVIEW_DIR = None


def save_preview(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def big(img, factor=4, bg=True):
    out = Image(img.width * factor, img.height * factor)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.get(x, y)
            base = 40 if ((x + y) % 2) else 60
            k = a / 255.0
            c = (r * k + base * (1 - k), g * k + base * (1 - k), b * k + base * (1 - k), 255)
            for yy in range(factor):
                for xx in range(factor):
                    out.set(x * factor + xx, y * factor + yy, c)
    return out


# ================================================================================================ materials
# A painter is p(fx, fy, fw, fh, face, rng) -> (r, g, b[, a]) or None (transparent); (fx, fy) is the texel inside the face
# (face-local, model pixels), fw x fh the face size. Faces are seen from outside in their natural orientation.

_NOISE = {}


def nz(seed, x, y, cell=4):
    n = _NOISE.get(seed)
    if n is None:
        n = _NOISE[seed] = Noise(seed, 64)
    return n.at(x % 64, y % 64, cell)


def along(fx, fy, fw, fh):
    """(a, b): a runs along the long side of the face, b across it."""
    return (fx, fy) if fw >= fh else (fy, fx)


def kayu(base, seed=1, seam=0.0, dark=0.0):
    """Wood: grain along the long side, rings and pores; seam > 0 cuts plank joints every `seam` pixels across."""
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        a, c = along(fx, fy, fw, fh)
        g = math.sin(c * 1.7 + math.sin(a * 0.35 + seed) * 1.6 + seed)
        col = shade(b, 0.84 + 0.12 * g + 0.12 * nz(seed, int(a), int(c), 4))
        if face in ("top", "bottom") and fw < 3 and fh < 3:
            col = shade(col, 0.8)  # end grain
        if seam and int(c) % int(seam) == 0 and fw > 2 and fh > 2:
            col = shade(col, 0.62)
        if rng.random() < 0.02:
            col = shade(col, 0.72)
        if dark:
            col = shade(col, 1.0 - dark)
        return col
    return p


def bambu(seed=3, base="B8B060", node=10):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        a, c = along(fx, fy, fw, fh)
        if face in ("top", "bottom") and max(fw, fh) <= 2.5:
            return rgb("8A7A40")
        col = shade(b, 0.88 + 0.16 * math.sin(c * 2.1) + 0.06 * nz(seed, int(a), int(c), 2))
        if int(a + seed) % node == 0:
            col = shade(rgb("7C703A"), 1.05)
        elif int(a + seed) % node == 1:
            col = shade(col, 1.12)
        return col
    return p


def polos(base, var=0.08, seed=5, cell=3):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        return shade(b, 1.0 - var + 2 * var * nz(seed, int(fx * 3 + fw), int(fy * 3 + fh), cell))
    return p


def lak(base, seed=7, tepi=None, gloss=0.3):
    """Lacquer: deep colour with a soft sheen; tepi = colour of a 1 px edge line on faces of at least 4 x 4."""
    b = rgb(base)
    t = rgb(tepi) if tepi else None

    def p(fx, fy, fw, fh, face, rng):
        sheen = max(0.0, math.sin((fx + fy * 0.7) * 0.45)) ** 6
        col = mix(shade(b, 0.88 + 0.18 * nz(seed, int(fx), int(fy), 4)), (255, 236, 214), sheen * gloss)
        if t and fw >= 4 and fh >= 4 and (fx < 1 or fy < 1 or fx >= fw - 1 or fy >= fh - 1):
            col = shade(t, 0.9 + 0.2 * rng.random())
        return col
    return p


def emas(seed=9):
    def p(fx, fy, fw, fh, face, rng):
        g = 0.5 + 0.5 * math.sin((fx - fy) * 0.9 + seed)
        col = mix(rgb("A87A22"), rgb("F2D27A"), g * 0.8 + 0.2 * nz(seed, int(fx), int(fy), 2))
        if rng.random() < 0.04:
            col = rgb("FFF2C0")
        return col
    return p


def logam(base="6E7276", seed=11, karat=True):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.8 + 0.3 * nz(seed, int(fx), int(fy), 2))
        if karat and rng.random() < 0.05:
            col = shade(rgb("8A5A3A"), 0.9)  # rust fleck
        return col
    return p


def kain(base, seed=13, weave=True, alt=None):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.88 + 0.14 * nz(seed, int(fx), int(fy), 4))
        if weave and (int(fx) + int(fy)) % 2 == 0:
            col = shade(col, 0.94)
        if alt and int(fy) % 4 == 0:
            col = rgb(alt)
        return col
    return p


def jerami(seed=15, base="B89A58", dark=0.0):
    def p(fx, fy, fw, fh, face, rng):
        weave = ((int(fx) // 2) + (int(fy) // 2)) % 2
        col = mix(rgb(base), rgb("E0C47E"), 0.35 + 0.3 * math.sin(fx * 1.3 + (fy if weave else -fy) * 0.4))
        if weave:
            col = shade(col, 0.86)
        if int(fy) % 6 == 0:
            col = shade(col, 0.7)
        if rng.random() < 0.03:
            col = shade(col, 0.6)
        return shade(col, 1.0 - dark)
    return p


def kertas(base="F2EAD4", rib="B8A880", seed=17, step=3):
    b, r = rgb(base), rgb(rib)

    def p(fx, fy, fw, fh, face, rng):
        if face in ("top", "bottom"):
            return shade(b, 0.92)
        col = shade(b, 0.92 + 0.1 * nz(seed, int(fx), int(fy), 2))
        if int(fy) % step == 0:
            col = mix(col, r, 0.55)
        return col
    return p


def batu(base="8E8A80", seed=19):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.78 + 0.34 * nz(seed, int(fx * 2), int(fy * 2), 3))
        if rng.random() < 0.06:
            col = shade(col, 0.72)
        return col
    return p


def keramik(base, seed=21, glaze=0.25, crackle=False):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.9 + 0.12 * nz(seed, int(fx), int(fy), 2))
        if face not in ("top", "bottom") and fy < 1:
            col = mix(col, (255, 255, 255), glaze)
        if crackle and (int(fx * 3 + fy * 5) % 7 == 0):
            col = shade(col, 0.82)
        return col
    return p


def bara(seed=23):
    def p(fx, fy, fw, fh, face, rng):
        t = nz(seed, int(fx * 2), int(fy * 2), 2)
        return mix(rgb("8A1A08"), rgb("FFC040"), t)
    return p


def satu(c):
    col = rgb(c) if isinstance(c, str) else c

    def p(fx, fy, fw, fh, face, rng):
        return col
    return p


def tali(base="A8885A"):
    def p(fx, fy, fw, fh, face, rng):
        a, c = along(fx, fy, fw, fh)
        return shade(rgb(base), 0.8 + 0.25 * (int(a + c) % 2))
    return p


def buah(base, seed=27, kilap=True, bintik=None):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.86 + 0.16 * nz(seed, int(fx * 2), int(fy * 2), 2))
        if kilap and face == "top" and fx < fw / 2 and fy < fh / 2:
            col = mix(col, (255, 255, 255), 0.25)
        if bintik and rng.random() < 0.12:
            col = rgb(bintik)
        return col
    return p


def daun(base="4E8A32", seed=29):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        col = shade(b, 0.8 + 0.3 * nz(seed, int(fx * 2), int(fy * 2), 2))
        if int(fx) == int(fw / 2):
            col = mix(col, (220, 240, 190), 0.4)  # the midrib
        return col
    return p


def tembus(base, a=150):
    b = rgb(base)

    def p(fx, fy, fw, fh, face, rng):
        return b + (a,)
    return p


# ------------------------------------------------------------------ decal helpers

def decal_glyph(gid, fg, bg=None, pad=0.0, flip=False):
    """A face decal drawing glyph `gid` fitted into the face (nearest), fg colour; bg colour or None (= the material
    shows through). flip = upside down (the New Year fortune glyph on doors)."""
    rows = GLYPH[gid]
    fgc, bgc = rgb(fg), (rgb(bg) if bg else None)

    def d(fx, fy, fw, fh):
        s = min((fw - 2 * pad) / 16.0, (fh - 2 * pad) / 16.0)
        ox, oy = (fw - 16 * s) / 2.0, (fh - 16 * s) / 2.0
        gx, gy = int((fx - ox) / s), int((fy - oy) / s)
        if flip:
            gx, gy = 15 - gx, 15 - gy
        if 0 <= gx < 16 and 0 <= gy < 16 and GLYPH_ROWS(rows, gx, gy):
            return fgc
        return bgc
    return d


def GLYPH_ROWS(rows, gx, gy):
    return rows[gy][gx] == "#"


def decal_rows(rows, pal, flipx=False):
    """A face decal from pixel rows (one char per face texel)."""
    def d(fx, fy, fw, fh):
        y, x = int(fy), int(fx)
        if 0 <= y < len(rows):
            row = rows[y]
            if flipx:
                x = len(row) - 1 - x
            if 0 <= x < len(row):
                ch = row[x]
                if ch in pal:
                    return pal[ch]
        return None
    return d


def decal_fn(fn):
    """A face decal from a function of the face-local texel."""
    return fn


# ================================================================================================ glyphs (hand drawn)
# Pixel matrices; '#' = stroke; auto-centred into 16 x 16 by center_glyph. The 19 town glyphs of 42.12.4, the five
# element glyphs of the root-testing stone and the dice-table signs.

GLYPH_SRC = {
    "fu": [  # good fortune
        "..#.....#######",
        "...#...........",
        "#####..#######.",
        "....#..#.....#.",
        "...#...#######.",
        "..##.#.........",
        ".#.#..#########",
        "#..#...#...#..#",
        "...#...#...#..#",
        "...#...#######.",
        "...#...#...#..#",
        "...#...#...#..#",
        "...#...#######.",
    ],
    "shou": [  # longevity
        ".......#.......",
        "..###########..",
        ".......#.......",
        "...#########...",
        ".......#.......",
        "###############",
        "......#........",
        ".....#.....#...",
        "....###########",
        "...#.#.....#...",
        "..#..#.#...#...",
        ".....#..#..#...",
        "....#......#...",
        "...#.....#.#...",
        "..#.......##...",
    ],
    "chun": [  # spring
        ".......#.......",
        "..###########..",
        ".......#.......",
        "...#########...",
        "......#.#......",
        "###############",
        ".....#...#.....",
        "....#.....#....",
        "..###########..",
        ".#..#.....#..#.",
        "#...#######...#",
        "....#.....#....",
        "....#######....",
        "....#.....#....",
        "....#######....",
    ],
    "xi": [  # double happiness
        "...#.....#.....",
        "###############",
        "...#.....#.....",
        ".#####.#####...",
        ".......#.......",
        ".#####.#####...",
        ".#...#.#...#...",
        ".#####.#####...",
        "###############",
        ".#...#.#...#...",
        ".#...#.#...#...",
        "###############",
        "...............",
        ".#####.#####...",
        ".#...#.#...#...",
        ".#####.#####...",
    ],
    "dian": [  # offering (funerals)
        "...#.......#...",
        "....#.....#....",
        "..###########..",
        "....#..#..#....",
        "...#########...",
        "...#..#.#..#...",
        "...#.#...#.#...",
        "...#########...",
        "...#.......#...",
        "...#########...",
        ".......#.......",
        "###############",
        "......#.#......",
        ".....#...#.....",
        "...##.....##...",
    ],
    "jiu": [  # wine (酒): three drops and the wine jar
        "........#...#..",
        "#.....#########",
        ".#.....#.....#.",
        ".......#.....#.",
        ".......#######.",
        "#......#..#..#.",
        ".#.....#..#..#.",
        ".......#######.",
        ".......#.....#.",
        "..#....#.....#.",
        ".#....#########",
    ],
    "cha": [  # tea
        "...#.....#.....",
        "###############",
        "...#.....#.....",
        ".......#.......",
        "......#.#......",
        ".....#...#.....",
        "...##.....##...",
        ".##.#######.##.",
        ".......#.......",
        "..###########..",
        ".......#.......",
        ".....#.#.#.....",
        "....#..#..#....",
        "...#...#...#...",
        ".......#.......",
    ],
    "yao": [  # medicine
        "...#.....#.....",
        "###############",
        "...#.....#.....",
        "...#...#.......",
        "..#...#######..",
        ".#..#.#.....#..",
        "#####.#.#...#..",
        "...#..#..#..#..",
        "..#...#.....#..",
        ".#..#.#.....#..",
        "#####.#.....#..",
        "......#.....#..",
        "......#....##..",
    ],
    "bu": [  # cloth
        "......#........",
        "###############",
        "....#..........",
        "...#...#.......",
        "..#..#######...",
        ".##..#..#..#...",
        "#.#..#..#..#...",
        "..#..#..#..#...",
        "..#..#..#.##...",
        "..#.....#......",
        "..#.....#......",
    ],
    "tie": [  # iron
        "..#.......#....",
        ".####...#.#....",
        "#.......#######",
        ".####..#..#....",
        "..#.......#....",
        ".####.#########",
        "..#.......#....",
        "..#......#.#...",
        "..#.##..#...#..",
        "..##...#.....##",
    ],
    "bing": [  # cakes
        "..#....#....#..",
        ".####...#..#...",
        "#...#.#########",
        "...#....#...#..",
        ".##.....#...#..",
        "..#...#########",
        "..#.....#...#..",
        "..#.....#...#..",
        "..#.#..#....#..",
        "..##..#.....#..",
    ],
    "shu": [  # books
        "..........##...",
        "..#########.#..",
        "..........#....",
        "..........#....",
        "..#########....",
        "..#.......#....",
        "###############",
        "..........#....",
        "..........#....",
        "..........#....",
        "........###....",
    ],
    "fu2": [  # talismans
        "..#.....#......",
        ".####..#####...",
        "#..#..#..#.....",
        "...#.......#...",
        "..#..##########",
        ".##........#...",
        "#.#...#....#...",
        "..#....#...#...",
        "..#........#...",
        "..#.......##...",
    ],
    "dang": [  # pawnshop
        ".#.....#.....#.",
        "..#....#....#..",
        "...#...#...#...",
        "...............",
        "..###########..",
        "............#..",
        "..###########..",
        "............#..",
        "..###########..",
    ],
    "ke": [  # guest
        "......#........",
        ".#############.",
        ".#...........#.",
        "....#..........",
        "...#######.....",
        "..#.#....#.....",
        "......#.#......",
        ".....##.##.....",
        "...##.....##...",
        ".#.#######.#...",
        "...#.....#.....",
        "...#######.....",
    ],
    "zhan": [  # inn
        "..#......#.#...",
        "..#..#####..#..",
        "#####....#.....",
        "..#...#######..",
        ".###.....#..#..",
        "#.#.#.#####....",
        "..#......#.#...",
        "..#.......#..#.",
        "..#.....##.#.#.",
        "..#...##....##.",
    ],
    "feng": [  # harvest
        "......#........",
        "..###########..",
        "......#........",
        ".#############.",
        "......#........",
        "###############",
        "......#........",
        "......#........",
        "......#........",
    ],
    "jie": [  # beware (戒): the halberd stroke on the right, the two-handed base on the left
        ".............#.",
        "###############",
        "......#.......#",
        ".....#........#",
        "....#.........#",
        "..##########..#",
        "....#...#.....#",
        "....#...#....#.",
        "....#...#....#.",
        "....#...#...#..",
        "...#....#..#...",
        "...#....##.#...",
        "..#.....#.#....",
        ".#......###....",
    ],
    "mian": [  # noodles
        "###############",
        "......#........",
        ".....#.........",
        ".#############.",
        ".#...#...#...#.",
        ".#...#####...#.",
        ".#...#...#...#.",
        ".#...#####...#.",
        ".#...#...#...#.",
        ".#...#####...#.",
        ".#...........#.",
        ".#############.",
    ],
    # the five elements of the root-testing stone (BatuUjiEntity bit order: metal, wood, water, fire, earth)
    "jin": [  # metal
        ".......#.......",
        "......#.#......",
        ".....#...#.....",
        "...##.....##...",
        "..###########..",
        ".......#.......",
        "..#########....",
        ".......#.......",
        "..#....#...#...",
        "...#...#..#....",
        "###############",
    ],
    "mu": [  # wood
        ".......#.......",
        ".......#.......",
        "###############",
        "......###......",
        ".....#.#.#.....",
        "....#..#..#....",
        "...#...#...#...",
        "..#....#....#..",
        ".#.....#.....#.",
        ".......#.......",
        ".......#.......",
    ],
    "shui": [  # water
        ".......#.......",
        ".......#....#..",
        "######.#...#...",
        ".....#.##.#....",
        "....#..#.#.....",
        "...#...#..#....",
        "..#....#...#...",
        ".#.....#....##.",
        "......##.......",
    ],
    "huo": [  # fire
        ".......#.......",
        ".#.....#.....#.",
        "..#....#....#..",
        "...#...#...#...",
        ".......#.......",
        "......#.#......",
        ".....#...#.....",
        "....#.....#....",
        "..##.......##..",
        ".#...........#.",
    ],
    "tu": [  # earth
        ".......#.......",
        ".......#.......",
        "..###########..",
        ".......#.......",
        ".......#.......",
        ".......#.......",
        "###############",
    ],
    "da": [  # big
        ".......#.......",
        ".......#.......",
        "###############",
        ".......#.......",
        "......#.#......",
        ".....#...#.....",
        "....#.....#....",
        "..##.......##..",
        "##...........##",
    ],
    "xiao": [  # small
        ".......#.......",
        ".......#.......",
        "...#...#...#...",
        "..#....#....#..",
        ".#.....#.....#.",
        "#......#......#",
        ".......#.......",
        "......##.......",
    ],
}

# the 19 glyph textures of 42.12.4 for 42V-F (shop signs, banners, door papers), by file name, in design order
GLYPH_TOWN = ["fu", "shou", "chun", "xi", "dian", "jiu", "cha", "yao", "bu", "tie", "bing", "shu", "fu2", "dang", "ke", "zhan",
              "feng", "jie", "mian"]


def center_glyph(rows):
    w = max(len(r) for r in rows)
    rows = [r.ljust(w, ".") for r in rows]
    ys = [y for y, r in enumerate(rows) if "#" in r]
    xs = [x for r in rows for x, ch in enumerate(r) if ch == "#"]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    gw, gh = x1 - x0 + 1, y1 - y0 + 1
    if gw > 16 or gh > 16:
        raise ValueError("glyph too big %dx%d" % (gw, gh))
    ox, oy = (16 - gw) // 2, (16 - gh) // 2
    out = [["."] * 16 for _ in range(16)]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if rows[y][x] == "#":
                out[oy + y - y0][ox + x - x0] = "#"
    return ["".join(r) for r in out]


GLYPH = {k: center_glyph(v) for k, v in GLYPH_SRC.items()}


# ================================================================================================ shared mesh pieces

def round2(p, cx, y0, cz, w, h, mat, inset=0.3, **kw):
    """An octagon-ish round body: two crossed boxes standing on y0."""
    p.g(cx, y0, cz, w, h, w * (1 - inset), mat, **kw)
    p.g(cx, y0, cz, w * (1 - inset), h, w, mat, **kw)
    return p


def jar(p, cx, y0, cz, s, mat, lid=None, neck=True):
    """A pot-bellied jar of width s standing on y0: foot, belly (round2), shoulder, neck, optional lid."""
    p.g(cx, y0, cz, s * 0.7, s * 0.2, s * 0.7, mat)
    round2(p, cx, y0 - s * 0.2, cz, s, s * 0.75, mat)
    round2(p, cx, y0 - s * 0.95, cz, s * 0.75, s * 0.2, mat)
    if neck:
        p.g(cx, y0 - s * 1.15, cz, s * 0.45, s * 0.22, s * 0.45, mat)
    if lid:
        p.g(cx, y0 - s * 1.37, cz, s * 0.55, s * 0.12, s * 0.55, lid)
    return p


def bowl(p, cx, y0, cz, w, h, mat, isi=None):
    """An open bowl on y0 (four walls + foot) and optional contents level with the rim."""
    t = max(0.3, w * 0.12)
    p.g(cx, y0, cz, w * 0.6, h * 0.25, w * 0.6, mat)
    y = y0 - h * 0.25
    p.g(cx, y, cz - w / 2 + t / 2, w, h * 0.75, t, mat)
    p.g(cx, y, cz + w / 2 - t / 2, w, h * 0.75, t, mat)
    p.g(cx - w / 2 + t / 2, y, cz, t, h * 0.75, w - 2 * t, mat)
    p.g(cx + w / 2 - t / 2, y, cz, t, h * 0.75, w - 2 * t, mat)
    p.g(cx, y, cz, w - 2 * t, h * 0.25, w - 2 * t, mat)
    if isi:
        p.g(cx, y - h * 0.2, cz, w - 2 * t, h * 0.4, w - 2 * t, isi)
    return p


def lantern(parent, name, pivot, s=1.0, paper="lentera_kertas", cap="emas", tassel="rumbai_merah", string="tali", shape="bulat"):
    """A hanging paper lantern part (pivot = the hook): string, cap, round or long paper body, cap, tassel. The part is
    returned so the renderer can sway it."""
    L = parent.part(name, pivot)
    L.b(-0.15 * s, 0, -0.15 * s, 0.3 * s, 2.0 * s, 0.3 * s, string)
    L.b(-1.0 * s, 2.0 * s, -1.0 * s, 2.0 * s, 0.6 * s, 2.0 * s, cap)
    if shape == "panjang":
        L.b(-1.4 * s, 2.6 * s, -1.4 * s, 2.8 * s, 5.0 * s, 2.8 * s, paper)
        L.b(-1.6 * s, 3.4 * s, -1.6 * s, 3.2 * s, 3.4 * s, 3.2 * s, paper)
        y = 7.6 * s
    else:
        L.b(-1.7 * s, 2.6 * s, -1.7 * s, 3.4 * s, 3.6 * s, 3.4 * s, paper)
        L.b(-2.1 * s, 3.1 * s, -1.3 * s, 4.2 * s, 2.6 * s, 2.6 * s, paper)
        L.b(-1.3 * s, 3.1 * s, -2.1 * s, 2.6 * s, 2.6 * s, 4.2 * s, paper)
        y = 6.2 * s
    L.b(-1.0 * s, y, -1.0 * s, 2.0 * s, 0.6 * s, 2.0 * s, cap)
    L.b(-0.3 * s, y + 0.6 * s, -0.3 * s, 0.6 * s, 2.4 * s, 0.6 * s, tassel)
    return L


def paper_cut(gid, red="C8281E"):
    """A red paper-cut sheet with glyph `gid` cut out (transparent strokes) and a cut border."""
    rows = GLYPH[gid]
    col = rgb(red)

    def d(fx, fy, fw, fh):
        if fx < 0.5 or fy < 0.5 or fx > fw - 0.5 or fy > fh - 0.5:
            return col
        s = min((fw - 1) / 16.0, (fh - 1) / 16.0)
        ox, oy = (fw - 16 * s) / 2.0, (fh - 16 * s) / 2.0
        gx, gy = int((fx - ox) / s), int((fy - oy) / s)
        if 0 <= gx < 16 and 0 <= gy < 16 and rows[gy][gx] == "#":
            return (0, 0, 0, 0)
        return shade(col, 0.92 + 0.12 * ((int(fx * 2) + int(fy * 2)) % 2))
    return d


def scallop(period=4.25, solid=1.8):
    """A valance edge: solid band, then rounded scallops; cut-outs transparent."""
    def d(fx, fy, fw, fh):
        if fy < solid:
            return None
        t = (fx % period) / period - 0.5
        depth = solid + (fh - solid) * math.sqrt(max(0.0, 1 - (2 * t) ** 2))
        return None if fy < depth else (0, 0, 0, 0)
    return d


def triangle_cut(apex_x=None):
    """Transparent outside an isosceles triangle with its apex at the top centre (or at apex_x)."""
    def d(fx, fy, fw, fh):
        ax = fw / 2.0 if apex_x is None else apex_x * fw
        half_l = ax * (fy + 0.5) / fh
        half_r = (fw - ax) * (fy + 0.5) / fh
        if fx + 0.5 < ax - half_l or fx + 0.5 > ax + half_r:
            return (0, 0, 0, 0)
        return None
    return d


TENDA_MATS = {
    "bambu": bambu(3), "bambu_tua": bambu(5, "9A8A4A"), "kain_dasar": kain("E8DCC0", 31), "kain_warna": kain("F4F0EA", 33),
    "rumbai_warna": kain("F0ECE4", 35), "kayu": kayu("8A6038", 37), "kayu_tua": kayu("5A3A22", 39, seam=4),
    "papan_meja": kayu("9C7448", 41, seam=0), "kain_meja": kain("8E2A22", 43, alt="C9A24A"), "tikar": jerami(45),
    "keranjang": jerami(47, "A08048"), "tali": tali(), "emas": emas(), "lentera_kertas": kertas("D8382A", "8A1A12", 49),
    "rumbai_merah": kain("B01E18", 51, weave=False), "kukusan": jerami(53, "C8A868"), "keramik_putih": keramik("E8ECF0", 55),
    "keramik_biru": keramik("5A7AB0", 57), "pangsit": polos("F2E8D8", 0.06, 59), "haw": buah("C81E1E", 61), "kue_bulan": polos("C88A3A", 0.1, 63),
    "jerami": jerami(65, "D0B068"), "kertas_merah": polos("C8281E", 0.06, 67), "kincir_a": polos("E04040", 0.05, 69),
    "kincir_b": polos("F0C030", 0.05, 71), "kincir_c": polos("3A8AE0", 0.05, 73), "genderang": lak("B02A20", 75, tepi="E8D8B0"),
    "tanah_liat": polos("B0704A", 0.1, 77), "baju_a": polos("3A6AB0", 0.08, 79), "baju_b": polos("C04A3A", 0.08, 81),
    "wajah": polos("F0C8A0", 0.04, 83), "vas_a": keramik("4A8A7A", 85), "vas_b": keramik("C8A040", 87), "vas_c": keramik("8A3A6A", 89),
    "kubis": buah("A8D07A", 91, kilap=False), "kubis_luar": daun("6AA040", 93), "lobak": polos("F2F0E8", 0.05, 95),
    "daun": daun("4E8A32", 97), "cabai": buah("D02A1A", 99), "jeruk": buah("F08A20", 101), "persik": buah("F4A0A0", 103, bintik="E86A6A"),
    "semangka": kain("2E6A2A", 105, weave=False, alt="A8D070"), "semangka_isi": buah("E04A4A", 107, kilap=False, bintik="2A1A10"),
    "guci_coklat": keramik("6A3A1E", 109, glaze=0.3), "guci_seladon": keramik("8AB8A0", 111, crackle=True), "teko": keramik("A04A30", 113),
    "herba": polos("6A7A3A", 0.18, 115), "lemari_obat": kayu("7A4A28", 117), "kuningan": emas(119), "labu": polos("C8A050", 0.1, 121),
    "lumpang": batu("8A8478", 123), "kain_a": kain("C03A3A", 125), "kain_b": kain("3A5A9A", 127), "kain_c": kain("E0C060", 129),
    "kain_d": kain("4A8A5A", 131), "kain_e": kain("E8E0D0", 133), "boneka": polos("D8B080", 0.06, 135),
}


def lemari_obat_decal():
    """A medicine cabinet front: 3 x 3 drawers with brass pulls and paper labels."""
    def d(fx, fy, fw, fh):
        cw, ch = fw / 3.0, fh / 3.0
        cx, cy = fx % cw, fy % ch
        if cx < 0.4 or cy < 0.4:
            return rgb("3A2010")
        if abs(cx - cw / 2) < 0.5 and abs(cy - ch * 0.62) < 0.5:
            return rgb("D8B050")
        if abs(cx - cw / 2) < 0.8 and abs(cy - ch * 0.3) < 0.4:
            return rgb("E8E0C8")
        return None
    return d


def build_atap_tenda(m, dyed):
    """The gable canopy of the market tents: 8 strips a panel, dyed strips in the tinted mesh, undyed in the base one;
    scalloped valances (dyed) at both eaves. Same part names in both meshes (one animation)."""
    atap = m.part("atap", (0, -15, 0))
    ang = deg(20.6)
    L = 17.1
    for side, sgn in (("d", 1), ("b", -1)):
        panel = atap.part("atap_" + side, (0, 0, 0), (ang * sgn, 0, 0))
        for i in range(8):
            if (i % 2 == 0) != dyed:
                continue
            x0 = -17 + 4.25 * i
            z0 = -L if sgn > 0 else 0.0
            panel.b(x0, 0, z0, 4.25, 0.8, L, "kain_warna" if dyed else "kain_dasar", share="strip")
        if dyed:
            fr = panel.part("rumbai_" + side, (0, 0.8, -L * sgn), (-ang * sgn, 0, 0))
            fr.b(-17, 0, 0, 34, 3, 0, "rumbai_warna", decal={"all": scallop()}, share="rumbai")
    if not dyed:
        atap.b(-18, -0.6, -0.6, 36, 1.2, 1.2, "bambu")
    return atap


def mesh_tenda():
    m = Mesh("tenda", 256, {"dasar": TENDA_MATS}, glow=("lentera_kertas",), java="tenda",
             doc="the market tent of tenda_pasar and tenda_lapak (42.12.4): bamboo poles, gable canopy (undyed strips; "
                 "the dyed ones are tendaWarna), counter, crate, baskets, the lapak mat and ten goods sets.")
    tiang = m.part("tiang", (0, 24, 0))
    for x in (-14.5, 14.5):
        for z in (-14.5, 14.5):
            tiang.b(x - 0.75, -34, z - 0.75, 1.5, 34, 1.5, "bambu", share="tiang")
    for z in (-14.5, 14.5):
        tiang.b(-15.5, -34, z - 0.5, 31, 1, 1, "bambu_tua", share="palang_x")
    build_atap_tenda(m, False)
    meja = m.part("meja")
    meja.b(-14, 8, -14, 28, 1.5, 9, "papan_meja")
    for x in (-13, 11.5):
        for z in (-13.5, -6.5):
            meja.b(x, 9.5, z, 1.5, 14.5, 1.5, "kayu", share="kaki_meja")
    meja.b(-14, 9.5, -14.05, 28, 6, 0, "kain_meja")
    meja.b(-12.5, 18.5, -12.5, 25, 1, 1, "kayu")
    m.part("tikar").b(-12, 23, -15, 24, 1, 16, "tikar")
    peti = m.part("peti")
    peti.b(4, 18, 6, 8, 6, 6, "kayu_tua")
    peti.b(3.8, 17.4, 5.8, 8.4, 0.8, 6.4, "kayu")
    kr = m.part("keranjang")
    round2(kr, -8, 24, 8, 6, 4, "keranjang")
    round2(kr, -3, 24, 11, 4.5, 3, "keranjang")
    kr.g(-3, 21, 11, 3.6, 0.6, 3.6, "jeruk")

    # tenda_pasar goods (VARIAN_LENTERA .. VARIAN_PERMAINAN)
    g = m.part("pasar_0")
    for i, x in enumerate((-10.5, -3.5, 3.5, 10.5)):
        lantern(g, "lentera_%d" % i, (x, -9, -14.5))
    g.g(-6, 8, -10, 4, 1, 3, "kertas_merah")
    g.g(-6, 7, -10, 3.6, 1, 2.6, "kertas_merah")
    g.g(5, 8, -9.5, 3, 3, 3, "bambu_tua")
    g = m.part("pasar_1")
    for t in range(3):
        round2(g, -8, 8 - 2 * t, -9.5, 6, 2, "kukusan")
    round2(g, -8, 2, -9.5, 6.4, 1, "kukusan")
    g.g(-8, 1, -9.5, 1, 0.6, 1, "bambu_tua")
    bowl(g, -1, 8, -10, 4, 1.6, "keramik_biru", isi="pangsit")
    g.g(6, 8, -10, 6, 1, 2, "kayu")
    for i in range(5):
        x = 3.6 + i * 1.2
        g.g(x, 7, -10, 0.4, 6, 0.4, "bambu")
        for j in range(3):
            g.g(x, 3 - j * 1.2, -10, 1.1, 1.1, 1.1, "haw", share="haw")
    g.g(9.5, 8, -7, 5, 0.5, 4, "kayu")
    for i in range(4):
        g.g(8.3 + (i % 2) * 2.4, 7.5, -7.9 + (i // 2) * 1.8, 1.8, 0.8, 1.6, "kue_bulan", share="kue")
    g = m.part("pasar_2")
    g.g(11, 8, -9.5, 2, 9, 2, "jerami")
    for i in range(5):
        a = i * 72.0
        k = g.part("kincir_%d" % i, (11 + 1.6 * math.cos(deg(a)), -0.5 - (i % 3) * 2, -9.5 + 1.6 * math.sin(deg(a))), (0, deg(-a), 0))
        k.b(0, -0.2, -0.2, 2.5, 0.4, 0.4, "bambu")
        k.b(2.5, -1.5, -1.5, 0, 3, 3, ("kincir_a", "kincir_b", "kincir_c")[i % 3])
    for i, x in enumerate((-9, -5.5)):
        g.g(x, 8, -10, 0.4, 4, 0.4, "bambu")
        g.g(x, 4.5, -10, 2.2, 2, 1.2, "genderang")
    for i, x in enumerate((-1.5, 1, 3.5)):
        g.g(x, 8, -10.5, 1.5, 2, 1.2, ("baju_a", "baju_b", "tanah_liat")[i])
        g.g(x, 6, -10.5, 1.2, 1.2, 1.2, "wajah")
    g = m.part("pasar_3")
    g.b(-15, -6, -15.2, 30, 0.2, 0.2, "tali")
    for i, (x, gid) in enumerate(((-11.5, "fu"), (-5.75, "xi"), (0, "chun"), (5.75, "fu"), (11.5, "shou"))):
        g.b(x - 2.5, -5.8, -15.1, 5, 6, 0, "kertas_merah", decal={"all": paper_cut(gid)}, k=2)
    for i in range(3):
        g.b(-11 + i * 1.3, 6.8, -12, 1, 1, 8, "kertas_merah")
    g.g(6, 8, -10, 6, 0.6, 4, "kertas_merah")
    g = m.part("pasar_4")
    for i in range(6):
        x, z = -9 + (i % 3) * 3.4, -11.5 + (i // 3) * 3
        round2(g, x, 8, z, 1.6, 2.4, ("vas_a", "vas_b", "vas_c")[i % 3])
        g.g(x, 5.6, z, 1, 0.8, 1, ("vas_a", "vas_b", "vas_c")[i % 3])
    for i in range(3):
        rx = 3 + i * 3.4
        g.g(rx, 8, -12.5, 3, 0.4, 0.4, "bambu_tua")
        g.g(rx, 8, -9.9, 3, 0.4, 0.4, "bambu_tua")
        g.g(rx - 1.3, 8, -11.2, 0.4, 0.4, 2.2, "bambu_tua")
        g.g(rx + 1.3, 8, -11.2, 0.4, 0.4, 2.2, "bambu_tua")
    g.b(-5, 18, 8.5, 10, 6, 3, "kayu_tua")
    for i in range(3):
        g.g(-3 + i * 3, 18, 10, 1.5, 2.4, 1.4, ("baju_a", "baju_b", "boneka")[i])
        g.g(-3 + i * 3, 15.6, 10, 1.2, 1.2, 1.2, "wajah")

    # tenda_lapak goods (VARIAN_SAYUR .. VARIAN_KAIN): no counter (the renderer hides meja), the mat (top y 23) in front
    # and a low bench along the back of the mat (bangku_lapak, top y 19, z -3.5 .. 1)
    bl = m.part("bangku_lapak")
    bl.b(-11, 18, -3.5, 22, 1.2, 4.5, "papan_meja")
    for x in (-10.5, 9):
        bl.b(x, 19.2, -3, 1.5, 3.8, 3.5, "kayu", share="kaki_bangku_lapak")
    B, Z = 18.0, -1.2
    g = m.part("lapak_0")
    for (x, z) in ((-8, -9), (-4.6, -11), (-9.4, -12.6)):
        g.g(x, 23, z, 4.4, 2.2, 4.4, "kubis_luar")
        g.g(x, 21.6, z, 3.4, 2.4, 3.4, "kubis")
    for i in range(3):
        g.b(2, 21.8 - i * 1.0, -12 + i * 0.6, 4, 1, 1, "lobak")
        g.b(6, 21.6 - i * 1.0, -12.2 + i * 0.6, 2, 1.4, 1.4, "daun")
    g.b(-2, 21.5, -8, 2, 1.5, 5, "daun")
    g.b(0.5, 21.5, -8, 2, 1.5, 5, "daun")
    round2(g, 4, B, Z, 5, 2, "keranjang")
    g.g(4, B - 1.8, Z, 4.2, 0.5, 3.2, "cabai")
    round2(g, -5, B, Z, 5, 2, "keranjang")
    g.g(-5, B - 1.8, Z, 4.2, 0.5, 3.2, "daun")
    g = m.part("lapak_1")
    for layer, (n, s) in enumerate(((3, 0), (2, 1), (1, 2))):
        for i in range(n):
            for j in range(2 if n == 3 else n):
                g.g(-8 + i * 2 + s, B - layer * 2, -2.2 + j * 2 + s * 0.5, 2, 2, 2, "jeruk", share="jeruk")
    round2(g, 6, 23, -10, 6, 2, "keranjang")
    for i in range(5):
        g.g(4.4 + (i % 3) * 1.6, 21 - (i // 3) * 1.2, -10.6 + (i % 2) * 1.2, 1.7, 1.7, 1.7, "persik", share="persik")
    g.g(-6, 23, -9, 5, 4.5, 6, "semangka")
    g.g(1, 23, -9.5, 5, 2.2, 6, "semangka")
    g.g(1, 20.8, -9.5, 4.4, 0.2, 5.4, "semangka_isi")
    g = m.part("lapak_2")
    jar(g, -8, 23, -10, 5, "guci_coklat", lid="kayu")
    jar(g, -2.5, 23, -12, 3.6, "guci_seladon")
    jar(g, 3, 23, -10.5, 2.8, "guci_coklat")
    for i in range(4):
        bowl(g, -6 + 0.2 * i, B - i * 1.0, Z, 4, 1.2, "keramik_putih")
    for x in (2.5, 6.5):
        g.g(x, B, Z, 2.6, 2.2, 2.6, "teko")
        g.g(x, B - 2.2, Z, 1.4, 0.5, 1.4, "teko")
        g.b(x - 2.3, B - 1.6, Z - 0.3, 1.2, 0.5, 0.6, "teko")
        g.b(x + 1.3, B - 2.0, Z - 0.25, 0.5, 1.6, 0.5, "teko")
    g = m.part("lapak_3")
    for i, x in enumerate((-11, -7, 7, 11)):
        h = g.part("herba_%d" % i, (x, -9, -14.5))
        h.b(-0.15, 0, -0.15, 0.3, 1.5, 0.3, "tali")
        h.b(-0.75, 1.5, -0.75, 1.5, 5, 1.5, "herba")
        h.b(-0.5, 6.5, -0.5, 1, 1.2, 1, "herba")
    g.b(-4, B - 7, Z - 2, 8, 7, 4, "lemari_obat", decal={"front": lemari_obat_decal()})
    for x in (6, 9):
        g.g(x, B, Z, 2.2, 2.2, 2.2, "labu")
        g.g(x, B - 2.2, Z, 1.4, 1.4, 1.4, "labu")
        g.g(x, B - 3.6, Z, 0.5, 0.8, 0.5, "kayu")
    g.g(-8, 23, -10, 2.4, 1.5, 2.4, "lumpang")
    g.b(-8.2, 18.8, -10.2, 0.5, 3.4, 0.5, "kayu")
    for i in range(4):
        g.g(-2 + i * 2.2, 23, -11, 1.8, 0.8, 3, "herba", share="herba_tikar")
    g = m.part("lapak_4")
    for i in range(4):
        g.g(-7, B - i * 1.5, Z, 6, 1.5, 4, ("kain_a", "kain_b", "kain_c", "kain_d")[i])
    for i in range(3):
        g.g(1, B - i * 1.5, Z, 6, 1.5, 4, ("kain_e", "kain_d", "kain_a")[i])
    g.b(8, -9, -14.6, 5, 12, 0.4, "kain_b")
    g.b(-10, 21, -11, 2, 2, 8, "kain_c")
    g.b(-7.6, 21, -11, 2, 2, 8, "kain_a")
    g.g(3, 23, -9, 6, 1.5, 4, "kain_e")
    return m


def mesh_tenda_warna():
    m = Mesh("tenda_warna", 128, {"dasar": TENDA_MATS}, java="tendaWarna",
             doc="the dyed canopy strips and valances of the market tents, painted pale and tinted with the synced warna().")
    build_atap_tenda(m, True)
    return m


HANTU_MATS = {
    "tikar": jerami(201, "6A5838", 0.15), "lak_tua": lak("5A1A14", 203, tepi="2A2420", gloss=0.12), "kayu_tua": kayu("4A3020", 205),
    "besi": logam("4A4C50", 207), "bambu": bambu(209, "7A7A4A"), "tali": tali("6A5A40"), "kayu_gelap": kayu("3A2418", 211),
    "lentera_hijau": kertas("5AD07A", "1E6A3A", 213), "rumbai_hijau": kain("2A7A4A", 215, weave=False), "perunggu": emas(217),
    "dupa": polos("8A3A2A", 0.1, 219), "bara": bara(221), "lilin_hijau": polos("6AB07A", 0.06, 223), "nyala_hijau": satu("B8FFC8"),
    "jimat": polos("E8D070", 0.05, 225), "giok": keramik("5FA88C", 227), "benang_merah": polos("B02A20", 0.05, 229),
    "guci_seladon": keramik("8AB8A0", 231, crackle=True), "guci_hitam": keramik("2A2A30", 233, glaze=0.35), "guci_merah": keramik("9A2A20", 235),
    "kertas": kertas("E8DCC0", "C8B898", 237, step=2), "gagang_gulung": lak("3A1A10", 239), "sarung": lak("1E1A18", 241, tepi="C9A24A"),
    "cermin": emas(243), "emas_koin": emas(245),
}


def jimat_decal():
    def d(fx, fy, fw, fh):
        if abs(fx - fw / 2) < 0.35 and 0.4 < fy < fh - 0.4 and int(fy * 2) % 3 != 2:
            return rgb("B01E18")
        return None
    return d


def gulung_decal():
    def d(fx, fy, fw, fh):
        if int(fy) % 2 == 1 and 0.6 < fx < fw - 0.6 and int(fx * 3 + fy) % 4 != 0:
            return rgb("2A2420")
        return None
    return d


def mesh_lapak_hantu():
    m = Mesh("lapak_hantu", 128, {"dasar": HANTU_MATS}, glow=("lentera_hijau", "nyala_hijau", "bara"), java="lapakHantu",
             doc="one Ghost Market stall (42.12.4): worn straw mat, two old chests (one open), a bent bamboo pole with the "
                 "green lantern (part lentera sways), incense, a green candle and four goods sets barang_0..3.")
    m.root.b(-12, 23, -10, 24, 1, 16, "tikar")
    m.root.b(-12.5, 23.4, -10, 0.5, 0.6, 16, "tali")
    m.root.b(12, 23.4, -10, 0.5, 0.6, 16, "tali")
    pa = m.part("peti_a")
    pa.b(-11, 18.5, 0, 6, 4.5, 6, "lak_tua")
    pa.b(-11.3, 17, -0.3, 6.6, 1.5, 6.6, "lak_tua")
    for z in (1.2, 4.2):
        pa.b(-11.15, 17, z, 6.3, 6, 0.6, "besi", share="pita_besi")
    pa.b(-8.5, 19, -0.15, 1, 1.2, 0.2, "besi")
    pb = m.part("peti_b")
    pb.b(5, 18.5, 1, 6, 4.5, 6, "lak_tua")
    pb.g(8, 19, 4, 5, 0.6, 5, "emas_koin")
    tutup = pb.part("tutup_b", (8, 18.5, 7), (deg(-60), 0, 0))
    tutup.b(-3.3, -1.5, -6.6, 6.6, 1.5, 6.6, "lak_tua")
    for z in (-5.4, -2.4):
        tutup.b(-3.4, -1.6, z, 6.8, 1.7, 0.6, "besi", share="pita_tutup")
    tiang = m.part("tiang", (13, 24, 5))
    tiang.b(-0.5, -30, -0.5, 1, 30, 1, "bambu")
    tiang.b(-1.2, -1.2, -1.2, 2.4, 1.2, 2.4, "kayu_tua")
    lengan = tiang.part("lengan", (0, -29.5, 0), (deg(10), 0, 0))
    lengan.b(-0.4, -0.4, -9.5, 0.8, 0.8, 10, "bambu")
    lengan.b(-0.3, -0.3, -10.2, 0.6, 0.6, 0.8, "tali")
    lantern(lengan, "lentera", (0, 0.4, -9.4), s=1.15, paper="lentera_hijau", cap="kayu_gelap", tassel="rumbai_hijau")
    m.root.g(-1, 23, 5, 2.5, 2, 2.5, "perunggu")
    for i, (x, z, r) in enumerate(((-1.5, 4.6, 0.2), (-1, 5.2, -0.15), (-0.5, 4.8, 0.05))):
        st = m.part("dupa_%d" % i, (x, 21, z), (r, 0, r * 0.7))
        st.b(-0.12, -3, -0.12, 0.25, 3, 0.25, "dupa")
        st.b(-0.15, -3.4, -0.15, 0.3, 0.4, 0.3, "bara")
    m.root.g(8, 23, -6, 1, 2.5, 1, "lilin_hijau")
    m.root.g(8, 20.5, -6, 0.6, 1, 0.6, "nyala_hijau")
    m.root.g(-2, 23, 8.5, 4, 0.8, 3, "kayu_gelap")
    m.root.b(-3.6, 22.2, 7.2, 0.6, 1.8, 2.6, "kayu_gelap")
    m.root.b(-0.6, 22.2, 7.2, 0.6, 1.8, 2.6, "kayu_gelap")
    g = m.part("barang_0")
    g.g(0, 23, -6, 9, 0.5, 4.5, "kayu_tua")
    for i in range(6):
        s = g.part("jimat_%d" % i, (-3 + i * 1.2, 22.4, -6), (0, deg(-20 + i * 8), 0))
        s.b(-0.5, -0.1, -2, 1, 0.1, 4, "jimat", decal={"top": jimat_decal()}, share="jimat", k=2)
    for i in range(3):
        g.g(-8 + i * 2, 23, -3, 1.5, 0.4, 1.5, "giok", share="giok")
    g.g(6, 23, -3, 2, 0.6, 1.2, "benang_merah")
    g = m.part("barang_1")
    jar(g, -5, 23, -5, 2.6, "guci_seladon", lid="kayu_tua")
    jar(g, 0, 23, -6, 3.2, "guci_hitam")
    jar(g, 4.5, 23, -4.5, 2.2, "guci_merah", lid="kayu_tua")
    g = m.part("barang_2")
    for i, (x, y) in enumerate(((-4, 23), (-2.8, 23), (-1.6, 23), (-3.4, 21.8), (-2.2, 21.8))):
        g.b(x - 0.6, y - 1.2, -8, 1.2, 1.2, 6, "kertas", share="gulung")
    g.b(1, 22.6, -8, 6, 0.2, 4, "kertas", decal={"top": gulung_decal()}, k=2)
    g.b(0.6, 22.2, -8.3, 0.8, 0.8, 4.6, "gagang_gulung", share="gagang")
    g.b(6.6, 22.2, -8.3, 0.8, 0.8, 4.6, "gagang_gulung", share="gagang")
    g = m.part("barang_3")
    g.g(-6, 23, -6, 0.8, 3, 0.8, "kayu_gelap")
    g.g(-6, 23, -2, 0.8, 3, 0.8, "kayu_gelap")
    g.b(-6.5, 19.6, -9, 1, 0.8, 10, "sarung")
    g.b(-6.6, 19.4, -1.6, 1.2, 1.2, 1.4, "perunggu")
    g.g(2, 23, -4, 1, 2, 1, "kayu_gelap")
    g.b(0, 16.8, -4.25, 4, 4, 0.5, "cermin")
    g.g(6, 23, -6, 2.6, 2, 2.6, "perunggu")
    for dx, dz in ((-0.9, -0.9), (0.9, -0.9), (0, 0.9)):
        g.g(6 + dx, 23, -6 + dz, 0.4, 0.6, 0.4, "perunggu")
    return m


# ------------------------------------------------------------------ the travelling stage

def rok_decal():
    """Stage skirt: a gold band with a key fret, red pleated cloth, scalloped hem."""
    def d(fx, fy, fw, fh):
        if fy < 1.5:
            return rgb("D8B050") if (int(fx) % 4 != 3 or fy < 0.5) else rgb("8A5A10")
        if fy > fh - 1.2:
            t = (fx % 5) / 5.0 - 0.5
            if fy > fh - 1.2 + 1.2 * (1 - math.sqrt(max(0.0, 1 - (2 * t) ** 2))):
                return (0, 0, 0, 0)
        c = rgb("A82018") if int(fx) % 3 else rgb("8A1812")
        return shade(c, 0.92 + 0.12 * nz(301, int(fx), int(fy), 2))
    return d


def papan_hias_decal():
    """The carved front valance: gold frame, red field with gold cloud scrolls."""
    def d(fx, fy, fw, fh):
        if fy < 0.6 or fy > fh - 0.6 or fx < 0.6 or fx > fw - 0.6:
            return rgb("E0B850")
        x = int(fx) % 8
        y = int(fy)
        cloud = {(1, 1), (2, 1), (3, 2), (2, 2), (5, 1), (6, 2), (5, 2), (4, 1)}
        if (x, y) in cloud:
            return rgb("D8A840")
        return rgb("9A1A14")
    return d


def latar_decal(v):
    """Four painted backdrops: 0 opera (sun over blue mountains, cranes), 1 acrobats (red-gold rays), 2 musicians
    (plum blossom branch on cream silk), 3 storyteller (a coiling dragon in clouds)."""
    def d(fx, fy, fw, fh):
        X, Y = fx / fw, fy / fh
        if v == 0:
            sky = mix(rgb("E8D8B0"), rgb("F0C890"), Y)
            ridge = 0.55 + 0.12 * math.sin(X * 9.0) + 0.06 * math.sin(X * 23.0)
            if (X - 0.72) ** 2 + ((Y - 0.28) * fh / fw) ** 2 < 0.0045:
                return rgb("D8402A")
            if Y > ridge:
                return mix(rgb("4A6A8A"), rgb("2A3A5A"), (Y - ridge) * 2.2)
            if Y > ridge - 0.12 and int(fx * 0.7 + fy) % 5 == 0:
                return rgb("8AA0B0")
            if abs(Y - 0.2 - 0.03 * math.sin(X * 30)) < 0.012 and 0.2 < X < 0.45:
                return rgb("2A2420")
            return sky
        if v == 1:
            ang = math.atan2(Y - 1.0, X - 0.5)
            ray = int((ang + 4) * 9) % 2
            c = rgb("C8281E") if ray else rgb("E8B040")
            if Y > 0.85:
                c = rgb("6A1A12")
            return shade(c, 0.94 + 0.1 * nz(303, int(fx), int(fy), 3))
        if v == 2:
            c = rgb("EDE2C8")
            bx = 0.15 + X * 0.8
            by = 0.35 + 0.25 * math.sin(X * 4.5)
            if abs(Y - by) < 0.025 and X < 0.85:
                return rgb("3A2418")
            for k in range(9):
                px, py = 0.1 + k * 0.085, 0.35 + 0.25 * math.sin((0.1 + k * 0.085) * 4.5) + (0.07 if k % 2 else -0.07)
                if (X - px) ** 2 * 4 + (Y - py) ** 2 < 0.0018:
                    return rgb("E06A8A") if k % 3 else rgb("F4F0F0")
            if fy < 1 or fy > fh - 1:
                return rgb("8A6A3A")
            return shade(c, 0.95 + 0.08 * nz(305, int(fx), int(fy), 4))
        c = mix(rgb("2A3A4A"), rgb("1A2430"), Y)
        t = X * 6.28 * 1.5
        body = 0.5 + 0.22 * math.sin(t)
        if abs(Y - body) < 0.06:
            return rgb("D8A030") if int(fx) % 3 else rgb("A86A18")
        if abs(Y - body) < 0.08:
            return rgb("6A3A10")
        if ((X * 7 + Y * 3) % 1.0) < 0.08 and int(fy) % 4 == 0:
            return rgb("8A9AB0")
        return c
    return d


def panji_seg_decal(top, bottom, j, segw, W, H, base="B01E18", fg="F0D070"):
    """One flutter segment of a vertical pennant: the banner (W x H) carries two glyphs stacked, a gold border and a
    swallow-tail; segment j shows columns j*segw .. (j+1)*segw."""
    g1, g2 = GLYPH[top], GLYPH[bottom]
    b, f = rgb(base), rgb(fg)

    def d(fx, fy, fw, fh):
        X = fx + j * segw
        if fy > H - 2.5 and abs(X - W / 2.0) < (fy - (H - 2.5)) * 1.4:
            return (0, 0, 0, 0)
        if X < 0.5 or X > W - 0.5 or fy < 0.5:
            return f
        s = (W - 2.0) / 16.0
        for k, rows in enumerate((g1, g2)):
            oy = 1.0 + k * (16 * s + 1.0)
            gx, gy = int((X - 1.0) / s), int((fy - oy) / s)
            if 0 <= gx < 16 and 0 <= gy < 16 and rows[gy][gx] == "#":
                return f
        return shade(b, 0.92 + 0.1 * nz(307, int(X * 2), int(fy * 2), 2))
    return d


PANJI_PASANGAN = [("fu", "shou"), ("chun", "feng"), ("xi", "fu"), ("shou", "chun")]

PANGGUNG_MATS = {
    "papan": kayu("A87A48", 311, seam=0), "papan_b": kayu("94683C", 313), "kayu": kayu("6A4628", 315), "lak": lak("9A1E18", 317),
    "rok": kain("A82018", 319), "papan_hias": lak("9A1A14", 321), "latar": kain("E0D0B0", 323), "tirai": kain("B42A1E", 325, alt="D8B050"),
    "tali_emas": emas(327), "kain_atap": kain("A81E16", 329, alt="D8A840"), "rumbai_atap": kain("C8A040", 331, weave=False),
    "emas": emas(333), "bambu": bambu(335), "panji": kain("B01E18", 337), "lentera_kertas": kertas("D8382A", "8A1A12", 339),
    "rumbai_merah": kain("B01E18", 341, weave=False), "tali": tali(), "taplak": kain("B02018", 343, alt="E0B850"),
    "kursi": lak("6A2A18", 345), "bangku": kayu("9A7048", 347), "lingkar": bambu(349, "C8A040"), "piring": keramik("E8ECF0", 351),
    "genderang": lak("B02A20", 353, tepi="E8D8B0"), "kulit": polos("E8D8B0", 0.06, 355), "gong": emas(357), "teko": keramik("4A6A5A", 359),
    "kipas": polos("E8DCC0", 0.05, 361), "balok": kayu("3A2418", 363),
}


def chair(p, name, cx, cz, y0, rot=0.0, mat="kursi"):
    """A small lacquered chair facing the front (-z), its back at +z; legs stand on y0."""
    c = p.part(name, (cx, y0, cz), (0, rot, 0))
    for dx in (-1.5, 1.5):
        for dz in (-1.5, 1.5):
            c.g(dx, 0, dz, 0.6, 3.4, 0.6, mat, share="kaki_kursi")
    c.g(0, -3.4, 0, 3.8, 0.6, 3.8, mat)
    c.g(0, -4.0, 1.6, 3.8, 4.4, 0.6, mat)
    return c


def mesh_panggung():
    m = Mesh("panggung", 256, {"dasar": PANGGUNG_MATS}, glow=("lentera_kertas",), java="panggung",
             doc="the travelling troupe's stage (42.12.4): plank floor on trestles, skirt, steps (lantai), four red poles with "
                 "beams and carved valance (tiang), four backdrops latar_0..3, side curtains, cloth roof with valance (atap), "
                 "two pennant poles with four glyph pairs each (panji_k/panji_kn, 3 flutter segments), two lanterns and the "
                 "troupe props alat_0..3.")
    lantai = m.part("lantai", (0, 24, 0))
    for i in range(6):
        lantai.b(-40, -12, -24 + 8 * i, 80, 4, 8, "papan" if i % 2 == 0 else "papan_b", share="papan_%d" % (i % 2))
    lantai.b(-41, -12.6, -24.6, 82, 1.6, 1.2, "lak")
    lantai.b(-41, -12.6, 23.4, 82, 1.6, 1.2, "lak")
    for x in (-37, -12.5, 12.5, 37):
        for z in (-21, 21):
            lantai.b(x - 1, -8, z - 1, 2, 8, 2, "kayu", share="kaki")
        lantai.b(x - 1, -8, -22, 2, 1.5, 44, "kayu", share="palang")
    lantai.b(-40, -8, -24.3, 80, 8, 0, "rok", decal={"all": rok_decal()})
    lantai.b(-40.3, -8, -24, 0, 8, 48, "rok", decal={"all": rok_decal()}, share="rok_sisi")
    lantai.b(40.3, -8, -24, 0, 8, 48, "rok", decal={"all": rok_decal()}, share="rok_sisi")
    lantai.b(40.4, -4, -22, 7, 4, 8, "papan_b")
    lantai.b(40.4, -8, -22, 4.7, 4, 8, "papan")
    lantai.b(40.4, -12, -22, 2.4, 4, 8, "papan_b")
    tiang = m.part("tiang", (0, 12, 0))
    for x in (-38, 38):
        for z in (-22, 22):
            tiang.b(x - 1, -40, z - 1, 2, 40, 2, "lak", share="tiang")
    tiang.b(-40, -42, -23, 80, 2, 2, "lak", share="balok_x")
    tiang.b(-40, -42, 21, 80, 2, 2, "lak", share="balok_x")
    tiang.b(-39, -42, -21, 2, 2, 42, "lak", share="balok_z")
    tiang.b(37, -42, -21, 2, 2, 42, "lak", share="balok_z")
    tiang.b(-37, -40, -23.8, 74, 4, 0.8, "papan_hias", decal={"front": papan_hias_decal(), "back": papan_hias_decal()})
    for v in range(4):
        tiang.part("latar_%d" % v).b(-37, -38, 21.4, 74, 38, 0, "latar", decal={"all": latar_decal(v)})
    for x, nm in ((-38.6, "tirai_k"), (36.0, "tirai_kn")):
        t = tiang.part(nm, (x, -38, -21))
        for k in range(3):
            t.b(0.2 * k, 0, k * 1.1, 2.6 - 0.4 * k, 38, 1.0, "tirai", share="tirai_%d" % k)
        t.b(-0.2, 22, -0.2, 3.0, 0.8, 3.6, "tali_emas")
    atap = m.part("atap", (0, -30, 0))
    ang = deg(15.5)
    for side, sgn in (("d", 1), ("b", -1)):
        pnl = atap.part("atap_" + side, (0, -7.5, 0), (ang * sgn, 0, 0))
        pnl.b(-42, 0, -27 if sgn > 0 else 0, 84, 0.8, 27, "kain_atap", share="atap")
        fr = pnl.part("rumbai_atap_" + side, (0, 0.8, -27 * sgn), (-ang * sgn, 0, 0))
        fr.b(-42, 0, 0, 84, 3, 0, "rumbai_atap", decal={"all": scallop(5.25, 1.4)}, share="rumbai_atap")
        for i in range(9):
            fr.b(-40 + i * 10 - 0.4, 2.6, -0.4, 0.8, 3, 0.8, "emas", share="rumbai_emas")
    atap.b(-2, -10.5, -1.2, 4, 3, 2.4, "emas")
    atap.b(-1, -12.5, -0.6, 2, 2, 1.2, "emas")
    for x in (-43, 41):
        atap.b(x, -9.5, -1, 2, 1.5, 2, "emas", share="ujung_bubung")
    for side, x0, dirn in (("k", -42.0, 1), ("kn", 42.0, -1)):
        pole = m.part("panji_" + side, (x0, 12, -24))
        pole.b(-0.5, -50, -0.5, 1, 50, 1, "bambu", share="tiang_panji")
        pole.b(-0.9, -51.2, -0.9, 1.8, 1.2, 1.8, "emas", share="kenop")
        pole.b(-0.4 if dirn > 0 else -9.6, -49, -0.4, 10, 0.8, 0.8, "bambu", share="lengan_panji")
        for v, (g1, g2) in enumerate(PANJI_PASANGAN):
            prev = pole.part("panji_%s_%d" % (side, v), (0.5 * dirn, -48, 0))
            for j in range(3):
                if j == 0:
                    seg = prev
                else:
                    seg = prev.part("panji_%s_%d_%d" % (side, v, j), (3.0 * dirn, 0, 0))
                x = 0 if dirn > 0 else -3.0
                jj = j if dirn > 0 else 2 - j
                seg.b(x, 0, -0.1, 3, 22, 0.2, "panji", decal={"front": panji_seg_decal(g1, g2, jj, 3, 9, 22),
                                                               "back": panji_seg_decal(g1, g2, 2 - j if dirn > 0 else j, 3, 9, 22)},
                      k=2)
                prev = seg
    for x, nm in ((-34, "lentera_k"), (34, "lentera_kn")):
        lantern(m.root, nm, (x, -28, -23), s=1.6)
    # props on the floor (top y 12)
    a = m.part("alat_0")
    a.g(0, 12, 12, 9, 5, 5, "taplak")
    a.g(0, 7, 12, 9.6, 0.6, 5.6, "kursi")
    chair(a, "kursi_0", -7.5, 12.5, 12)
    chair(a, "kursi_1", 7.5, 12.5, 12)
    a = m.part("alat_1")
    for i in range(3):
        a.g(-12, 12 - i * 3, 10, 9, 1, 2.4, "bangku", share="bangku")
        for dx in (-3.6, 3.6):
            a.g(-12 + dx, 11 - i * 3, 10, 0.8, 2, 2, "bangku", share="kaki_bangku")
    for x in (10, 16):
        a.g(x, 12, 12, 0.8, 9, 0.8, "kayu", share="tiang_simpai")
    hoop = a.part("simpai", (13, -0.5, 12))
    for (x, y, w, h) in ((-3, -3, 6, 0.6), (-3, 2.4, 6, 0.6), (-3, -3, 0.6, 6), (2.4, -3, 0.6, 6)):
        hoop.b(x, y, -0.3, w, h, 0.6, "lingkar")
    a.g(0, 12, 14, 0.5, 12, 0.5, "bambu")
    a.g(0, 0, 14, 3, 0.4, 3, "piring")
    a = m.part("alat_2")
    for dx in (-2, 2):
        for dz in (-2, 2):
            a.g(-8 + dx, 12, 10 + dz, 0.6, 4, 0.6, "kayu", share="kaki_gendang")
    round2(a, -8, 8, 10, 5.4, 4, "genderang")
    a.g(-8, 4, 10, 4.4, 0.2, 4.4, "kulit")
    for x in (5, 13):
        a.g(x, 12, 12, 0.8, 11, 0.8, "kayu", share="tiang_gong")
    a.g(9, 1, 12, 9, 0.8, 0.8, "kayu")
    gong = a.part("gong", (9, 1.8, 12))
    gong.b(-0.15, 0, -0.15, 0.3, 1.2, 0.3, "tali")
    gong.b(-2.6, 1.2, -0.25, 5.2, 5.2, 0.5, "gong")
    gong.b(-1, 2.8, -0.45, 2, 2, 0.4, "gong")
    a = m.part("alat_3")
    a.g(0, 12, 10, 6, 5, 4, "taplak")
    a.g(0, 7, 10, 6.6, 0.6, 4.6, "kursi")
    a.g(-1.5, 6.4, 9.6, 1.6, 1.4, 1.6, "teko")
    a.g(0.4, 6.4, 9.2, 0.8, 0.7, 0.8, "piring")
    a.g(1.8, 6.4, 10.2, 2.2, 0.3, 1.0, "kipas")
    a.g(1.4, 6.4, 8.8, 1.4, 0.7, 0.8, "balok")
    chair(a, "kursi_3", 0, 15, 12)
    return m


# ------------------------------------------------------------------ small fire

API_MATS = {
    "jerami": jerami(401, "C8A060"), "jerami_hangus": jerami(403, "6A5030", 0.2), "guci": keramik("6A4A2A", 405), "minyak": lak("201410", 407, gloss=0.6),
    "kayu_bakar": kayu("5A3A22", 409), "arang": polos("2A2220", 0.15, 411), "bara": bara(413),
}


def flame_frame(i, small=False):
    """An 8-frame flame (16 x 32 texels at k = 2): three or four licking tongues of their own height and sway over a
    glowing base, white-yellow core, orange body, red edge. Frame i of 8; small = the narrower second layer."""
    rnd = random.Random(1000 + (50 if small else 0))
    n = 3 if small else 4
    tongues = [(0.22 + 0.56 * k / (n - 1) + rnd.uniform(-0.05, 0.05), rnd.uniform(0.55, 1.0) if k not in (1, 2) else rnd.uniform(0.85, 1.0),
                rnd.uniform(0.16, 0.24), rnd.uniform(0, 6.28)) for k in range(n)]
    ph = i / 8.0 * 2 * math.pi

    def d(fx, fy, fw, fh):
        X, Y = fx / fw, 1.0 - fy / fh  # Y up 0..1
        v = 0.0
        for cx, h, w, p in tongues:
            hh = h * (0.82 + 0.18 * math.sin(ph + p))
            if Y > hh:
                continue
            t = Y / hh
            sway = 0.07 * math.sin(ph + p + t * 4.0) * t
            width = w * (1 - t) ** 0.75 * min(1.0, (t + 0.2) * 2.5)
            dist = abs(X - cx - sway) / max(0.01, width)
            v = max(v, (1 - dist) * (1 - 0.35 * t))
        if Y < 0.14:
            v = max(v, (1 - abs(X - 0.5) / 0.46) * (1 - Y / 0.14) * 0.9)
        if v <= 0.0:
            return (0, 0, 0, 0)
        v -= 0.22 * (nz(415 + i, int(fx * 2), int(fy * 2 + i * 3), 2) - 0.5)
        if v <= 0.06:
            return (0, 0, 0, 0)
        if v > 0.62:
            return (255, 246, 200, 255)
        if v > 0.4:
            return (255, 200, 70, 245)
        if v > 0.2:
            return (240, 120, 30, 230)
        return (190, 50, 20, 200)
    return d


def hangus_decal():
    """A charred log end: black rings around a glowing heart."""
    def d(fx, fy, fw, fh):
        r = math.hypot(fx - fw / 2, fy - fh / 2)
        return rgb("C8501A") if r < 0.45 else (rgb("2A2220") if int(r * 2) % 2 == 0 else rgb("4A3A30"))
    return d


def mesh_api():
    m = Mesh("api_bahan", 128, {"dasar": API_MATS}, glow=("bara",), java="apiBahan",
             doc="what burns under a small fire (42.12.4): straw heap (bahan_0), an overturned oil jar and its pool "
                 "(bahan_1), crossed logs (bahan_2), three glowing embers (bara).")
    g = m.part("bahan_0")
    g.b(-5, 21, -5, 10, 3, 10, "jerami_hangus")
    g.b(-3.5, 19.5, -3, 7, 1.5, 6, "jerami")
    for i in range(4):
        s = g.part("helai_%d" % i, (0, 23.4, 0), (0, deg(25 + i * 47), deg(4)))
        s.b(-0.2, -0.2, -6.5, 0.4, 0.4, 6.5, "jerami", share="helai")
    g = m.part("bahan_1")
    g.b(-5, 23.9, -4, 9, 0.1, 8, "minyak")
    jg = g.part("guci", (2, 21.6, 1), (0, deg(30), deg(90)))
    jar(jg, 0, 2.2, 0, 4, "guci")
    g = m.part("bahan_2")
    for i in range(3):
        lg = g.part("kayu_%d" % i, (0, 22.2, 0), (0, deg(i * 60), deg(6 * (i - 1))))
        lg.b(-6, -1.25, -1.25, 12, 2.5, 2.5, "kayu_bakar", share="kayu",
             decal={"right": hangus_decal(), "left": hangus_decal()})
    bara = m.part("bara")
    for (x, z, s) in ((-1.5, -1, 2), (1.6, 0.5, 1.5), (0, 1.8, 1.2)):
        bara.g(x, 24, z, s, s * 0.7, s, "bara")
    return m


def mesh_api_nyala():
    mats = {"api": satu("FFFFFF")}
    m = Mesh("api_nyala", 256, {"dasar": mats}, glow=("api",), java="apiNyala",
             doc="the flame tongues of the small fire: eight frame groups nyala_0..7 of six crossed quads (three large, three "
                 "small), drawn emissive only; the renderer shows one frame (two per tick) and scales the group with nilai().")
    for i in range(8):
        f = m.part("nyala_%d" % i, (0, 22.5, 0))
        for q in range(3):
            big_q = f.part("nyala_%d_b%d" % (i, q), (0, 0, 0), (0, deg(q * 60), 0))
            big_q.b(-4, -16, 0, 8, 16, 0, "api", decal={"all": flame_frame(i)}, share="besar_%d" % i, k=2)
            small_q = f.part("nyala_%d_k%d" % (i, q), (math.cos(deg(q * 120 + 30)) * 2.2, 0, math.sin(deg(q * 120 + 30)) * 2.2),
                             (0, deg(q * 60 + 30), 0))
            small_q.b(-3, -11, 0, 6, 11, 0, "api", decal={"all": flame_frame((i + 3) % 8, True)}, share="kecil_%d" % i, k=2)
    return m


# ------------------------------------------------------------------ spilled goods

BARANG_MATS = {
    "kubis": buah("A8D07A", 501, kilap=False), "kubis_luar": daun("6AA040", 503), "bawang": daun("7AB04A", 505), "guci": keramik("8A5A3A", 507),
    "tutup": kayu("6A4628", 509), "beras": polos("F0ECE0", 0.06, 511), "karung": kain("B8A078", 513), "tali": tali(), "kacang": polos("8A5A30", 0.15, 515),
    "jeruk": buah("F08A20", 517), "persik": buah("F4A0A0", 519, bintik="E86A6A"), "pir": buah("D8D070", 521), "kain": kain("3A5A9A", 523, alt="C8A040"),
}


def mesh_barang():
    m = Mesh("barang", 64, {"dasar": BARANG_MATS}, java="barang",
             doc="goods spilled from an overturned cart (42.12.4): cabbage and leek (jenis_0), a rolled jar with spilled "
                 "rice (jenis_1), a sack and beans (jenis_2), fruit (jenis_3), a cloth bolt half unrolled (jenis_4).")
    g = m.part("jenis_0")
    g.g(0, 24, 0, 4.6, 2, 4.6, "kubis_luar")
    g.g(0, 22.6, 0, 3.6, 2.4, 3.6, "kubis")
    lk = g.part("bawang", (2.6, 23.4, 1), (0, deg(35), 0))
    lk.b(-0.5, -0.5, -3, 1, 1, 6, "bawang")
    g = m.part("jenis_1")
    g.b(-3, 23.9, -2, 6, 0.1, 5, "beras")
    jg = g.part("guci", (0, 21.8, 0), (0, deg(20), deg(90)))
    jar(jg, 0, 2.2, 0, 4.2, "guci")
    g.g(3.6, 24, -2.4, 2.4, 0.6, 2.4, "tutup")
    g = m.part("jenis_2")
    g.g(0, 24, 0, 6, 4, 5, "karung")
    g.g(3.6, 22.6, 0, 1.6, 1.6, 1.8, "karung")
    g.g(3.0, 22.8, 0, 0.6, 2.0, 2.2, "tali")
    g.b(4.6, 23.9, -2, 3, 0.1, 4, "kacang")
    g = m.part("jenis_3")
    for (x, z, mat, s) in ((-1.5, -1, "jeruk", 2), (1.2, 0.4, "jeruk", 2), (-0.2, 2, "persik", 1.8), (2.8, -1.8, "pir", 1.7), (-3, 1.6, "jeruk", 2)):
        g.g(x, 24, z, s, s, s, mat)
    g = m.part("jenis_4")
    g.b(-1.25, 21.5, -3.5, 2.5, 2.5, 7, "kain")
    g.b(1.25, 23.8, -3, 5, 0.2, 6, "kain")
    return m


# ------------------------------------------------------------------ coffin bier

def peti_ujung_decal(gid):
    def d(fx, fy, fw, fh):
        if fx < 0.6 or fx > fw - 0.6 or fy < 0.6 or fy > fh - 0.6:
            return rgb("D8B048")
        r = min(fw, fh) * 0.42
        dx, dy = fx - fw / 2, fy - fh / 2
        if dx * dx + dy * dy < r * r:
            s = 2 * r / 16.0
            gx, gy = int((dx + r) / s), int((dy + r) / s)
            if 0 <= gx < 16 and 0 <= gy < 16 and GLYPH[gid][gy][gx] == "#":
                return rgb("F2D27A")
            if dx * dx + dy * dy > (r - 0.5) ** 2:
                return rgb("C8A040")
        return None
    return d


def peti_sisi_decal():
    """Gold cloud bands along the top and bottom of the coffin sides."""
    def d(fx, fy, fw, fh):
        if fy < 1.2 or fy > fh - 1.2:
            x = int(fx * 2) % 6
            if x in (0, 1, 3) or int(fy * 2) % 2 == 0:
                return rgb("D8B048")
        return None
    return d


def peti_looks():
    base = {"tali": tali(), "emas": emas(601), "galah": kayu("5A3A22", 603), "bunga_merah": kain("C81E1E", 605, weave=False),
            "pita_merah": kain("B01818", 607), "bunga_putih": polos("F4F2EC", 0.05, 609), "kertas": kertas("F0EEE6", "D0CCC0", 611),
            "bangku": kayu("7A5634", 613)}
    hitam = dict(base, lak_peti=lak("16120F", 615, gloss=0.12))
    merah = dict(base, lak_peti=lak("9A1612", 617, gloss=0.15))
    return {"hitam": hitam, "merah": merah}


def mesh_usungan():
    m = Mesh("usungan", 128, peti_looks(), java="usungan",
             doc="the coffin bier (42.12.4): a lacquered coffin, higher at the head, gold cloud bands and a 寿 roundel on "
                 "the head (part peti), two 56 px poles with gold caps and cross bars (galah), the red silk flower ball "
                 "(bunga_merah) or white paper flowers (bunga_putih), and two trestles (kuda_kuda) for KEADAAN_DIAM. Looks "
                 "hitam (VARIAN_HITAM) and merah (VARIAN_MERAH).")
    peti = m.part("peti", (0, 0, 0))
    peti.b(-5, -1, -14, 10, 1, 28, "lak_peti")
    peti.b(-5.5, -11, -14, 11, 10, 14, "lak_peti", decal={"front": peti_ujung_decal("shou"), "right": peti_sisi_decal(),
                                                          "left": peti_sisi_decal()})
    peti.b(-5, -9.5, 0, 10, 8.5, 14, "lak_peti", decal={"back": peti_ujung_decal("fu"), "right": peti_sisi_decal(),
                                                        "left": peti_sisi_decal()})
    peti.b(-6, -12.5, -14.6, 12, 1.5, 15, "lak_peti")
    peti.b(-5.6, -11, 0.4, 11.2, 1.5, 14.2, "lak_peti")
    peti.b(-4, -13.2, -13.6, 8, 0.8, 27, "lak_peti")
    peti.b(-6.2, -12.7, -14.8, 12.4, 0.4, 0.4, "emas")
    for z in (-10, 0, 10):
        peti.b(-7.5, 0, z - 0.5, 15, 1, 1, "galah", share="palang")
    peti.b(-5.8, -11.6, -0.3, 11.6, 11.7, 0.6, "tali")
    for side, x in (("k", -6.5), ("kn", 6.5)):
        gl = m.part("galah_" + side, (x, 1.5, 0), (0, deg(90), 0))
        gl.b(-28, -0.5, -0.5, 56, 1, 1, "galah", share="galah")
        gl.b(-28.8, -0.7, -0.7, 1.4, 1.4, 1.4, "emas", share="tutup_galah")
        gl.b(27.4, -0.7, -0.7, 1.4, 1.4, 1.4, "emas", share="tutup_galah")
    bm = m.part("bunga_merah", (0, -13.2, -6))
    bm.g(0, 0, 0, 3, 3, 3, "bunga_merah")
    bm.g(0, -0.4, 0, 3.6, 2.2, 2.2, "bunga_merah")
    bm.g(0, -0.4, 0, 2.2, 2.2, 3.6, "bunga_merah")
    bm.b(-6, -0.2, -0.3, 12, 0.2, 0.6, "pita_merah")
    bp = m.part("bunga_putih", (0, -13.2, -6))
    for (x, z) in ((-2, -2), (2, -1), (0, 2), (-1.5, 6), (1.5, 10)):
        bp.g(x, 0, z, 1.8, 1.4, 1.8, "bunga_putih", share="bunga")
    bp.b(-0.4, -0.2, -3, 0.8, 0.2, 14, "kertas")
    kk = m.part("kuda_kuda")
    for z in (-9, 9):
        kk.b(-8, 2, z - 1.5, 16, 1, 3, "bangku", share="bangku")
        for x in (-7, 6):
            kk.b(x, 3, z - 1, 1, 7, 2, "bangku", share="kaki_bangku")
    return m


# ------------------------------------------------------------------ banquet table

def ngon(p, prefix, cx, y0, cz, R, h, mat, sides=16, share=None):
    """A regular 16-gon (or 8-gon) slab standing on y0: sides/2 strips rotated in equal steps (an exact polygon)."""
    n = sides // 2
    a = R * math.cos(math.pi / sides)
    s = 2 * R * math.sin(math.pi / sides)
    for i in range(n // 2):
        q = p.part("%s_%d" % (prefix, i), (cx, y0, cz), (0, deg(180.0 / n * i), 0))
        q.b(-a, -h, -s / 2, 2 * a, h, s, mat, share=share)
        q.b(-s / 2, -h, -a, s, h, 2 * a, mat, share=(share + "_t") if share else None)
    return p


def jamuan_looks():
    base = {"kayu": kayu("6A3A20", 701), "keramik_putih": keramik("EEF0F2", 703), "bangku": keramik("D8E0E8", 705),
            "bangku_pola": keramik("4A6AA8", 707), "putar": lak("4A2414", 709), "ikan": polos("D89A50", 0.1, 711), "ayam": polos("E0B070", 0.1, 713),
            "pangsit": polos("F2E8D8", 0.06, 715), "sayur": daun("3E8A2A", 717), "daging": polos("8A3A1E", 0.12, 719), "mi": polos("F0E0B0", 0.08, 721),
            "sup": polos("C8902A", 0.1, 723), "cawan": keramik("E8ECF0", 725), "sumpit": polos("2A1A12", 0.05, 727), "tulang": polos("F0E8D8", 0.08, 729),
            "tureen": keramik("C8A040", 731)}

    def kain_meja(base_col, gid, glyph_col):
        k = kain(base_col, 733)
        g = decal_glyph(gid, glyph_col, pad=0.4) if gid else None

        def p(fx, fy, fw, fh, face, rng):
            if g and face in ("front", "back", "left", "right") and fw > 4:
                c = g(fx, fy, fw, fh)
                if c is not None:
                    return c
            return k(fx, fy, fw, fh, face, rng)
        return p
    merah = dict(base, kain=kain("B81E18", 735), tirai=kain_meja("A81810", "xi", "F2D27A"))
    putih = dict(base, kain=kain("ECEAE4", 737), tirai=kain_meja("E4E2DA", "dian", "2A2420"))
    polos_ = dict(base, kain=kain("C8A878", 739), tirai=kain_meja("B89868", "fu", "8A1A12"))
    return {"merah": merah, "putih": putih, "polos": polos_}


HIDANGAN = ["ikan", "ayam", "pangsit", "sayur", "daging", "mi"]


def mesh_jamuan():
    m = Mesh("jamuan", 128, jamuan_looks(), java="jamuan",
             doc="the round banquet table (42.12.4): a 16-gon top under a cloth (four rotated parts of two strips), four "
                 "cloth flaps with 囍 / 奠 / 福 by look, pedestal and foot, eight porcelain stools (bangku_0..7), six plates "
                 "with dishes (hidangan_0..5, by the synced mask), a turntable with a soup tureen, cups (cawan_0..7) and bones "
                 "(tulang) for the meal progress. Looks merah (wedding), putih (funeral), polos (feast).")
    ngon(m.root, "daun", 0, 13, 0, 12.0, 1.0, "kayu", share="daun")
    ngon(m.root, "kain", 0, 12.2, 0, 12.6, 0.6, "kain", share="kain")
    for i in range(4):
        f = m.part("tirai_%d" % i, (0, 12.2, 0), (0, deg(90 * i), 0))
        f.b(-3, 0, -12.45, 6, 6, 0, "tirai", share="tirai", k=4)
    m.root.g(0, 23, 0, 3, 10, 3, "kayu")
    m.root.g(0, 24, 0, 10, 1, 2, "kayu")
    m.root.g(0, 24, 0, 2, 1, 10, "kayu")
    for i in range(8):
        a = deg(22.5 + 45 * i)
        x, z = 17 * math.cos(a), 17 * math.sin(a)
        st = m.part("bangku_%d" % i, (x, 24, z))
        st.g(0, 0, 0, 3.2, 0.6, 3.2, "bangku_pola", share="kaki_bangku")
        round2(st, 0, -0.6, 0, 3.4, 4.6, "bangku", share=None)
        round2(st, 0, -5.2, 0, 4.0, 0.8, "bangku")
    for i in range(6):
        a = deg(60 * i)
        x, z = 7.6 * math.cos(a), 7.6 * math.sin(a)
        m.root.g(x, 11.6, z, 3.8, 0.4, 3.8, "keramik_putih", share="piring")
        h = m.part("hidangan_%d" % i, (x, 11.2, z), (0, -a, 0))
        kind = HIDANGAN[i]
        if kind == "ikan":
            h.g(0, 0, 0, 1.4, 1.0, 3.0, "ikan")
            h.g(0, 0, 1.9, 1.6, 0.6, 0.9, "ikan")
        elif kind == "ayam":
            h.g(0, 0, 0, 2.2, 1.5, 2.6, "ayam")
            h.g(0.9, 0, -1.4, 0.6, 0.6, 1.0, "ayam")
            h.g(-0.9, 0, -1.4, 0.6, 0.6, 1.0, "ayam")
        elif kind == "pangsit":
            for (dx, dz) in ((-0.7, -0.7), (0.7, -0.6), (-0.6, 0.7), (0.7, 0.7), (0, 0)):
                h.g(dx, 0 if (dx or dz) else -0.7, dz, 1.0, 0.7, 1.0, "pangsit", share="pangsit")
        elif kind == "sayur":
            h.g(0, 0, 0, 2.8, 0.8, 2.6, "sayur")
            h.g(0.4, -0.8, -0.2, 1.6, 0.5, 1.4, "sayur")
        elif kind == "daging":
            for (dx, dz) in ((-0.6, -0.6), (0.6, -0.6), (-0.6, 0.6), (0.6, 0.6)):
                h.g(dx, 0, dz, 1.0, 1.0, 1.0, "daging", share="daging")
        else:
            h.g(0, 0, 0, 2.8, 0.6, 2.8, "mi")
            h.g(0, -0.6, 0, 1.6, 0.4, 1.6, "mi")
    ngon(m.root, "putar", 0, 11.6, 0, 4.6, 0.4, "putar", sides=8, share="putar")
    bowl(m.root, 0, 11.2, 0, 3.8, 2.2, "tureen", isi="sup")
    m.root.g(0, 9.2, 0, 1.0, 0.6, 1.0, "tureen")
    for i in range(8):
        a = deg(22.5 + 45 * i)
        x, z = 10.6 * math.cos(a), 10.6 * math.sin(a)
        c = m.part("cawan_%d" % i, (x, 11.6, z))
        c.g(0, 0, 0, 0.9, 0.9, 0.9, "cawan", share="cawan")
        sp = m.part("sumpit_%d" % i, (x * 0.9, 11.55, z * 0.9), (0, -a + deg(80), 0))
        sp.b(-1.6, -0.2, -0.3, 3.2, 0.2, 0.2, "sumpit", share="sumpit")
        sp.b(-1.6, -0.2, 0.1, 3.2, 0.2, 0.2, "sumpit", share="sumpit")
    t = m.part("tulang")
    for (x, z) in ((5, 7), (-6, 6), (8, -4), (-7, -6), (2, -9)):
        t.g(x, 11.6, z, 1.2, 0.4, 0.5, "tulang", share="tulang")
    return m


# ------------------------------------------------------------------ dice table

PIPS = {1: [(1, 1)], 2: [(0, 0), (2, 2)], 3: [(0, 0), (1, 1), (2, 2)], 4: [(0, 0), (2, 0), (0, 2), (2, 2)],
        5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], 6: [(0, 0), (0, 1), (0, 2), (2, 0), (2, 1), (2, 2)]}
# face -> value (MejaDaduEntity faces 1-6); the renderer turns the wanted face up
DADU_MUKA = {"top": 1, "bottom": 6, "front": 2, "back": 5, "right": 3, "left": 4}


def dadu_decal(face):
    v = DADU_MUKA[face]

    def d(fx, fy, fw, fh):
        cx, cy = int(fx / fw * 3), int(fy / fh * 3)
        sub_x, sub_y = (fx / fw * 3) % 1.0, (fy / fh * 3) % 1.0
        if (cx, cy) in PIPS[v] and 0.15 < sub_x < 0.85 and 0.15 < sub_y < 0.85:
            return rgb("C81E1E") if v in (1, 4) else rgb("1A1A1E")
        return rgb("F2EEE4") if (0.2 < fx < fw - 0.2 and 0.2 < fy < fh - 0.2) else rgb("D8D2C4")
    return d


def laken_decal():
    """The green felt: a cream border, a centre line, 大 (big) on the left field, 小 (small) on the right."""
    gd, gx = decal_glyph("da", "F2E8C8"), decal_glyph("xiao", "F2E8C8")

    def d(fx, fy, fw, fh):
        if fx < 0.5 or fy < 0.5 or fx > fw - 0.5 or fy > fh - 0.5:
            return rgb("E8DCB0")
        if abs(fx - fw / 2) < 0.25:
            return rgb("E8DCB0")
        if 1.5 < fx < 7.5 and 2 < fy < fh - 2:
            c = gd(fx - 1.5, fy - 2, 6, fh - 4)
            if c is not None:
                return c
        if fw - 7.5 < fx < fw - 1.5 and 2 < fy < fh - 2:
            c = gx(fx - (fw - 7.5), fy - 2, 6, fh - 4)
            if c is not None:
                return c
        return shade(rgb("2A6A3A"), 0.9 + 0.12 * nz(801, int(fx * 2), int(fy * 2), 2))
    return d


DADU_MATS = {
    "papan_meja": kayu("6A3A1E", 803), "kayu": kayu("5A3420", 805), "laken": kain("2A6A3A", 807), "bambu": bambu(809, "C8B070"),
    "rotan": tali("8A6A3A"), "piring": keramik("E8E4DA", 811), "dadu": satu("F2EEE4"), "koin": emas(813),
    "tiang": kayu("4A2A18", 815), "lentera_kertas": kertas("D8382A", "8A1A12", 817), "emas": emas(819), "tali": tali(),
    "rumbai_merah": kain("B01E18", 821, weave=False), "chip_a": polos("C81E1E", 0.05, 823), "chip_b": polos("2A5AA8", 0.05, 825),
    "chip_c": polos("E8E0D0", 0.05, 827), "papan_tanda": lak("1E1A18", 829, tepi="C9A24A"), "tambalan": kain("6A5A3A", 831),
    "lilin": polos("E8E0C8", 0.06, 833), "nyala": satu("FFE0A0"), "cawan_arak": keramik("C8C0B0", 835),
}


def mesh_dadu():
    m = Mesh("dadu", 256, {"dasar": DADU_MATS}, glow=("lentera_kertas", "nyala"), java="dadu",
             doc="the dice table (42.12.4): wooden table with green felt (大 / 小 fields), legs and aprons, a dish, the bamboo "
                 "cup (cawan, lifted and shaken by the renderer), three dice (dadu_0..2 with a child dadu_n_k: top 1, bottom 6, "
                 "front 2, back 5, right 3, left 4; scaled 0.4), and the variant extras jenis_0 (tavern: coins, wine cup), "
                 "jenis_1 (licensed den: lantern pole, chips, sign), jenis_2 (Ghost Market: patch, candle stub).")
    m.root.b(-10, 11, -7, 20, 1, 14, "papan_meja")
    m.root.b(-9, 10.8, -6, 18, 0.2, 12, "laken", decal={"top": laken_decal()}, k=4)
    for x in (-9.5, 7.5):
        for z in (-6.5, 4.5):
            m.root.b(x, 12, z, 2, 12, 2, "kayu", share="kaki")
    m.root.b(-8, 12, -6.3, 16, 2, 0.8, "kayu", share="apron_x")
    m.root.b(-8, 12, 5.5, 16, 2, 0.8, "kayu", share="apron_x")
    m.root.b(-9.3, 12, -4.5, 0.8, 2, 9, "kayu", share="apron_z")
    m.root.b(8.5, 12, -4.5, 0.8, 2, 9, "kayu", share="apron_z")
    m.root.b(-8, 20, -6, 16, 0.8, 0.8, "kayu", share="palang")
    m.root.b(-8, 20, 5.2, 16, 0.8, 0.8, "kayu", share="palang")
    m.root.g(0, 10.8, 0, 6, 0.3, 6, "piring")
    cw = m.part("cawan", (0, 10.5, 0))
    cw.g(0, 0, 0, 4, 5, 3, "bambu")
    cw.g(0, 0, 0, 3, 5, 4, "bambu")
    cw.g(0, 0, 0, 4.3, 0.6, 3.3, "rotan")
    cw.g(0, -2.4, 0, 4.3, 0.6, 3.3, "rotan")
    cw.g(0, -5, 0, 2.6, 0.4, 2.6, "bambu")
    for i, (x, z) in enumerate(((-1.0, -0.4), (0.8, 0.7), (0.4, -1.1))):
        d = m.part("dadu_%d" % i, (x, 9.9, z))
        k = d.part("dadu_%d_k" % i)
        k.b(-1.5, -1.5, -1.5, 3, 3, 3, "dadu", decal={f: dadu_decal(f) for f in FACES}, share="dadu", k=2)
    g = m.part("jenis_0")
    for i in range(4):
        g.g(-6 + (i % 2) * 1.4, 10.8 - (i // 2) * 0.8, 3.8, 1.1, 0.8, 1.1, "koin", share="koin")
    g.g(6, 10.8, 3.8, 1.2, 1.2, 1.2, "cawan_arak")
    g = m.part("jenis_1")
    g.b(10, -10, -7, 1, 34, 1, "tiang")
    g.b(5.5, -10, -6.9, 5, 0.8, 0.8, "tiang")
    lantern(g, "lentera", (6, -9.4, -6.5), s=1.0)
    for i, mat in enumerate(("chip_a", "chip_b", "chip_c")):
        for j in range(3 - i):
            g.g(-7 + i * 1.6, 10.8 - j * 0.4, -4.5, 1.2, 0.4, 1.2, mat, share="chip_" + mat)
    g.b(-3, 6.8, 5.6, 6, 4, 0.6, "papan_tanda", decal={"front": decal_glyph("dang", "E0B850", pad=0.3)}, k=4)
    g = m.part("jenis_2")
    g.b(2, 10.75, 1, 4, 0.1, 3, "tambalan")
    g.g(-7, 10.8, 4, 0.8, 1.2, 0.8, "lilin")
    g.g(-7, 9.6, 4, 0.4, 0.8, 0.4, "nyala")
    return m


# ------------------------------------------------------------------ shadow screen

def puncak_decal():
    def d(fx, fy, fw, fh):
        t = abs(fx - fw / 2) / (fw / 2)
        top = fh * (0.2 + 0.8 * t * t)
        if fy < top - 0.5:
            return (0, 0, 0, 0)
        if fy < top + 0.4:
            return rgb("E0B850")
        if int(fx) % 4 == 0 and fy > top + 0.8:
            return rgb("D8A840")
        return None
    return d


LAYAR_MATS = {
    "lak": lak("8A1A14", 901, tepi="D8B050"), "kayu": kayu("4A2A18", 903), "kain_layar": kertas("F4E2B8", "E8D2A0", 905, step=6),
    "emas": emas(907), "puncak": lak("9A1E16", 909), "lentera_kertas": kertas("D8382A", "8A1A12", 911), "tali": tali(),
    "rumbai_merah": kain("B01E18", 913, weave=False), "meja": kayu("5A3420", 915), "pelita": keramik("8A5A2A", 917), "nyala": satu("FFE8A8"),
}


def mesh_layar():
    m = Mesh("layar", 128, {"dasar": LAYAR_MATS}, glow=("kain_layar", "lentera_kertas", "nyala"), java="layar",
             doc="the shadow-puppet screen (42.12.4): lacquered frame 32 x 24 x 2 with feet and a carved gold crest, the "
                 "30 x 22 paper cloth (glows from behind with the lamp), two small corner lanterns, and the puppeteer's table "
                 "with its oil lamp behind the screen. The silhouettes are drawn by the renderer on the cloth plane.")
    m.root.b(-17, -8, -1, 34, 2, 2, "lak", share="bilah")
    m.root.b(-17, 16, -1, 34, 2, 2, "lak", share="bilah")
    m.root.b(-17, -6, -1, 2, 30, 2, "lak", share="tiang")
    m.root.b(15, -6, -1, 2, 30, 2, "lak", share="tiang")
    m.root.b(-18.5, 23, -3, 5, 1, 6, "kayu", share="kaki")
    m.root.b(13.5, 23, -3, 5, 1, 6, "kayu", share="kaki")
    m.root.b(-12, -11, -0.5, 24, 3, 1, "puncak", decal={"front": puncak_decal(), "back": puncak_decal()})
    m.root.b(-18, -9, -1.5, 3, 1.5, 3, "emas", share="ujung")
    m.root.b(15, -9, -1.5, 3, 1.5, 3, "emas", share="ujung")
    m.root.b(-15, -6, 0, 30, 22, 0, "kain_layar")
    lantern(m.root, "lentera_k", (-16, -7.5, -1.6), s=0.8)
    lantern(m.root, "lentera_kn", (16, -7.5, -1.6), s=0.8)
    mj = m.part("meja")
    mj.b(-8, 14, 4, 16, 1, 6, "meja")
    for x in (-7.5, 6.5):
        for z in (4.5, 8.5):
            mj.b(x, 15, z, 1, 9, 1, "meja", share="kaki_meja")
    mj.g(0, 14, 7, 2.2, 1.6, 2.2, "pelita")
    mj.g(0, 12.4, 7, 0.6, 1.4, 0.6, "nyala")
    return m


# ------------------------------------------------------------------ root-testing stone

UNSUR_GLIF = ["jin", "mu", "shui", "huo", "tu"]
# glyph faces of the stone, in BatuUjiEntity bit order (metal, wood, water, fire, earth)
UNSUR_MUKA = ["front", "right", "back", "left", "top"]


def ukiran_decal(gid):
    """A glyph carved into stone: dark engraved strokes with a pale lower edge, a carved frame."""
    rows = GLYPH[gid]

    def d(fx, fy, fw, fh):
        side = min(fw, fh)
        oy = 1.0 if fh > fw else 0.0
        s = side / 16.0
        gx, gy = int(fx / s), int((fy - oy) / s)
        if 0 <= gx < 16 and 0 <= gy < 16:
            if rows[gy][gx] == "#":
                return rgb("3A3832")
            if gy > 0 and rows[gy - 1][gx] == "#":
                return rgb("B8B2A2")
        if fx < 0.25 or fx > fw - 0.25 or (fh > fw and (fy > fh - 0.5)):
            return rgb("5A564C")
        return None
    return d


def glif_decal(gid):
    """The lit glyph only (white; tinted with the root colour), the rest transparent."""
    rows = GLYPH[gid]

    def d(fx, fy, fw, fh):
        side = min(fw, fh)
        oy = 1.0 if fh > fw else 0.0
        s = side / 16.0
        gx, gy = int(fx / s), int((fy - oy) / s)
        if 0 <= gx < 16 and 0 <= gy < 16 and rows[gy][gx] == "#":
            return (255, 255, 255, 255)
        if 0 <= gx < 16 and 0 <= gy < 16:
            for ox, oy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                x2, y2 = gx + ox, gy + oy2
                if 0 <= x2 < 16 and 0 <= y2 < 16 and rows[y2][x2] == "#":
                    return (255, 255, 255, 110)
        return (0, 0, 0, 0)
    return d


def cincin_decal():
    def d(fx, fy, fw, fh):
        r = math.hypot(fx - fw / 2, fy - fh / 2)
        if 4.6 < r < 5.6 and int(math.atan2(fy - fh / 2, fx - fw / 2) * 6) % 3 != 0:
            return (255, 255, 255, 220)
        return (0, 0, 0, 0)
    return d


def mesh_batu_uji():
    mats = {"batu_uji": batu("8E8A7E", 1001), "alas": batu("6A665C", 1003), "telapak": batu("7A766A", 1005)}
    m = Mesh("batu_uji", 64, {"dasar": mats}, java="batuUji",
             doc="the root-testing stone (42.12.4): an 8 x 12 x 8 stone carved with 金 木 水 火 (front, right, back, left) and 土 "
                 "on top, on a plinth.")
    m.root.b(-4, 10, -4, 8, 12, 8, "batu_uji", decal={f: ukiran_decal(g) for f, g in zip(UNSUR_MUKA, UNSUR_GLIF)}, k=2)
    m.root.b(-6, 22, -6, 12, 2, 12, "alas")
    m.root.b(-5, 21.5, -5, 10, 0.5, 10, "alas")
    return m


def mesh_batu_uji_glif():
    mats = {"glif": satu("FFFFFF")}
    m = Mesh("batu_uji_glif", 128, {"dasar": mats}, glow=("glif",), java="batuUjiGlif",
             doc="the light of the root-testing stone: five glyph plates glif_0..4 (unsur bits) a hair outside the faces and "
                 "a rune ring on the plinth (cincin), white, drawn emissive and tinted with the root colour.")
    off = 0.06
    plates = [(-4, 10, -4 - off, 8, 12, 0, "front"), (-4 - off, 10, -4, 0, 12, 8, "right"), (-4, 10, 4 + off, 8, 12, 0, "back"),
              (4 + off, 10, -4, 0, 12, 8, "left"), (-4, 10 - off, -4, 8, 0, 8, "top")]
    for i, (x, y, z, w, h, d, face) in enumerate(plates):
        dec = glif_decal(UNSUR_GLIF[i])
        m.part("glif_%d" % i).b(x, y, z, w, h, d, "glif", decal={"all": dec}, k=2)
    m.part("cincin").b(-6, 21.94, -6, 12, 0, 12, "glif", decal={"all": cincin_decal()}, k=2)
    return m


# ------------------------------------------------------------------ refugee tent

def kanvas_looks():
    def kanvas(base, patch, seed):
        k = kain(base, seed)
        pk = kain(patch, seed + 1)

        def p(fx, fy, fw, fh, face, rng):
            if fw > 10 and fh > 10:
                for (px, py, pw, ph) in ((4, 6, 5, 4), (fw - 9, fh - 8, 4, 5), (fw * 0.45, fh * 0.3, 3, 3)):
                    if px <= fx < px + pw and py <= fy < py + ph:
                        if fx - px < 0.5 or fy - py < 0.5 or fx > px + pw - 0.5 or fy > py + ph - 0.5:
                            return rgb("3A3028")
                        return pk(fx, fy, fw, fh, face, rng)
            c = k(fx, fy, fw, fh, face, rng)
            if int(fy) % 9 == 0:
                c = shade(c, 0.86)
            return c
        return p
    base = {"tiang": kayu("6A4A2E", 1101), "tali": tali(), "patok": kayu("8A6A44", 1103), "alas_tidur": kain("8A6A4A", 1105, alt="6A4A2A"),
            "selimut": kain("6A4A6A", 1107), "panci": logam("3A3A3C", 1109, karat=False), "batu": batu("7A766C", 1111), "buntelan": kain("4A6A9A", 1113),
            "lentera_kertas": kertas("F2E6C0", "B8A070", 1115), "kayu_gelap": kayu("3A2418", 1117), "rumbai_merah": kain("B01E18", 1119, weave=False)}
    abu = dict(base, kanvas=kanvas("A8A498", "7A7A6E", 1121))
    coklat = dict(base, kanvas=kanvas("8A6A44", "6A5030", 1123))
    biru = dict(base, kanvas=kanvas("3A4A6A", "5A6A8A", 1125))
    return {"abu": abu, "coklat": coklat, "biru": biru}


def mesh_pengungsi():
    m = Mesh("pengungsi", 128, kanvas_looks(), glow=("lentera_kertas",), java="pengungsi",
             doc="the refugee tent (42.12.4): an A-frame of patched canvas 24 x 22 x 28 (sisi_k/sisi_kn), ridge pole, crossed "
                 "poles front and back, a triangular back wall, two front flaps (pintu_k/pintu_kn, tied open by day), guy ropes "
                 "and pegs, a bedroll, a pot on three stones, a bundle, and a small lantern at the entrance. Looks abu, coklat, biru.")
    ang = math.atan2(12, 22)
    L = math.hypot(12, 22)
    m.part("sisi_k", (0, 2, 0), (0, 0, ang)).b(0, 0, -14, 0.6, L, 28, "kanvas")
    m.part("sisi_kn", (0, 2, 0), (0, 0, -ang)).b(-0.6, 0, -14, 0.6, L, 28, "kanvas")
    m.part("bubung", (0, 1.4, 0), (0, deg(90), 0)).b(-16, -0.6, -0.6, 32, 1.2, 1.2, "tiang")
    for side, z in (("d", -14.6), ("b", 14.6)):
        for nm, sgn in (("k", 1), ("kn", -1)):
            m.part("tiang_%s_%s" % (side, nm), (0, 1.0, z), (0, 0, ang * sgn)).b(-0.5, -1.5, -0.5, 1, L + 1.6, 1, "tiang", share="tiang")
    m.root.b(-12, 2, 14, 24, 22, 0, "kanvas", decal={"all": triangle_cut()})
    # the flaps hinge on the slanted front edges: tilt (pintu_*) -> swing (engsel_*, the renderer's yRot) -> untilt
    for side, sgn in (("k", 1), ("kn", -1)):
        tilt = m.part("pintu_" + side, (0, 2, -14.05), (0, 0, ang * sgn))
        hinge = tilt.part("engsel_" + side)
        cloth = hinge.part("kain_pintu_" + side, (0, 0, 0), (0, 0, -ang * sgn))
        cloth.b(-12 if sgn > 0 else 0, 0, 0, 12, 22, 0, "kanvas", decal={"all": triangle_cut(1.0 if sgn > 0 else 0.0)}, share="pintu_" + side)
        cloth.b(-7 if sgn > 0 else 6, 11, -0.3, 1, 0.6, 0.6, "tali")
    for side, z0, sgn in (("d", -16, -1), ("b", 16, 1)):
        a = math.atan2(6, 22)
        rp = m.part("tali_" + side, (0, 2, z0), (a * sgn, 0, 0))
        rp.b(-0.15, 0, -0.15, 0.3, math.hypot(6, 22), 0.3, "tali", share="tali_tenda")
        m.root.g(0, 24.6, z0 + 6 * sgn, 0.8, 2.4, 0.8, "patok", share="patok")
    m.root.b(-9, 22.5, -7, 6, 1.5, 15, "alas_tidur")
    m.root.b(-8.8, 21.7, -2, 5.6, 0.8, 9, "selimut")
    m.root.b(-9.2, 21.0, 6, 6.4, 2.5, 2.2, "alas_tidur")
    m.root.g(7, 24, 6, 4, 3, 4, "buntelan")
    m.root.g(7, 21, 6, 1.6, 1.0, 1.6, "buntelan")
    for (x, z) in ((5, -9), (8, -9), (6.5, -6.6)):
        m.root.g(x, 24, z, 1.6, 1.4, 1.6, "batu", share="batu")
    m.root.g(6.5, 22.6, -8.2, 3, 2.2, 3, "panci")
    lantern(m.root, "lentera", (0, 3.6, -15.2), s=0.75, paper="lentera_kertas", cap="kayu_gelap")
    return m


# ------------------------------------------------------------------ city lanterns (42.9.2, drawn by 42V-F)

def kertas_lentera():
    """Lantern paper painted pale (tinted in code): bamboo ribs every 2 px, faint vertical seams, a soft top and bottom
    shadow so the tint reads round."""
    def p(fx, fy, fw, fh, face, rng):
        if face in ("top", "bottom"):
            return (200, 200, 200)
        v = 0.94 + 0.06 * nz(1201, int(fx * 2), int(fy * 2), 2)
        if int(fy * 2) % 4 == 0:
            v *= 0.68
        if int(fx * 2) % 6 == 0:
            v *= 0.9
        edge = min(fy, fh - fy) / max(0.5, fh)
        v *= 0.78 + 0.22 * min(1.0, edge * 4)
        c = 255 * v
        return (c, c, c)
    return p


LENTERA_MATS = {"kertas": kertas_lentera(), "kerangka": kayu("3A2418", 1203), "emas": emas(1205),
                "rumbai": kain("B01E18", 1207, weave=False), "rumbai_emas": kain("D8A840", 1209, weave=False), "tali": tali(),
                "mata": satu("C81E1E"), "telinga_dalam": satu("F0B0B0")}


def mesh_lentera(paper_only):
    name = "lentera_kertas" if paper_only else "lentera"
    m = Mesh(name, 64, {"dasar": LENTERA_MATS}, glow=("kertas",) if paper_only else (), java="lenteraKertas" if paper_only else "lentera",
             doc=("the paper of the five city lanterns (white with ribs; tinted with the lantern colour, emissive when lit)"
                  if paper_only else
                  "the frames of the five city lanterns (42.12.4; hook at the origin): jenis_0 round, jenis_1 long, jenis_2 gourd "
                  "(Ghost Market), jenis_3 rabbit (Mid-Autumn, on a stick), jenis_4 hexagonal silk; caps, tassels, hooks."))
    P = paper_only

    def paper(part, *box, **kw):
        if P:
            part.b(*box, "kertas", **kw)

    def frame(part, *box, mat="kerangka", **kw):
        if not P:
            part.b(*box, mat, **kw)
    j = m.part("jenis_0")
    frame(j, -0.3, 0, -0.3, 0.6, 1.4, 0.6, mat="tali")
    frame(j, -1.6, 1.4, -1.6, 3.2, 0.8, 3.2, mat="emas")
    paper(j, -2.5, 2.2, -2.5, 5, 7, 5)
    paper(j, -3, 3.2, -2, 6, 5, 4)
    paper(j, -2, 3.2, -3, 4, 5, 6)
    paper(j, -3.3, 4.2, -1.4, 6.6, 3, 2.8)
    paper(j, -1.4, 4.2, -3.3, 2.8, 3, 6.6)
    frame(j, -1.6, 9.2, -1.6, 3.2, 0.8, 3.2, mat="emas")
    frame(j, -0.4, 10, -0.4, 0.8, 4, 0.8, mat="rumbai")
    j = m.part("jenis_1")
    frame(j, -0.3, 0, -0.3, 0.6, 1.4, 0.6, mat="tali")
    frame(j, -1.4, 1.4, -1.4, 2.8, 0.8, 2.8)
    paper(j, -2, 2.2, -2, 4, 10, 4)
    paper(j, -2.3, 4.2, -2.3, 4.6, 6, 4.6)
    frame(j, -1.4, 12.2, -1.4, 2.8, 0.8, 2.8)
    frame(j, -0.4, 13, -0.4, 0.8, 3, 0.8, mat="rumbai_emas")
    j = m.part("jenis_2")
    frame(j, -0.3, 0, -0.3, 0.6, 1.2, 0.6, mat="tali")
    frame(j, -1.0, 1.2, -1.0, 2.0, 0.6, 2.0)
    paper(j, -1.5, 1.8, -1.5, 3, 2.4, 3)
    paper(j, -2.5, 4.2, -2.5, 5, 4.6, 5)
    paper(j, -2.9, 5.0, -1.8, 5.8, 3, 3.6)
    paper(j, -1.8, 5.0, -2.9, 3.6, 3, 5.8)
    frame(j, -1.0, 8.8, -1.0, 2.0, 0.6, 2.0)
    frame(j, -0.3, 9.4, -0.3, 0.6, 3.4, 0.6, mat="rumbai")
    j = m.part("jenis_3")
    frame(j, -0.25, -4, -0.25, 0.5, 6, 0.5)
    frame(j, -0.25, 1.6, -3.5, 0.5, 0.5, 7)
    paper(j, -2.5, 3, -3, 5, 4, 6)
    paper(j, -1.75, 2.4, -5.2, 3.5, 3, 3, decal={"front": decal_rows(["....", ".m..m", "....", "...."], {"m": rgb("C81E1E")})})
    paper(j, -1.5, -0.6, -4.6, 0.8, 3, 1.6, decal={"front": decal_rows([".", "t", "t"], {"t": rgb("F0B0B0")})})
    paper(j, 0.7, -0.6, -4.6, 0.8, 3, 1.6, decal={"front": decal_rows([".", "t", "t"], {"t": rgb("F0B0B0")})})
    paper(j, -0.75, 3.6, 3, 1.5, 1.5, 1.5)
    frame(j, -2.2, 7, -2.6, 0.6, 0.6, 5.2, mat="emas")
    frame(j, 1.6, 7, -2.6, 0.6, 0.6, 5.2, mat="emas")
    j = m.part("jenis_4")
    frame(j, -0.3, 0, -0.3, 0.6, 1.2, 0.6, mat="tali")
    R = 3.5
    ap, sd = R * math.cos(math.pi / 6), R
    for k in range(3):
        q = j.part("heks_%d" % k, (0, 0, 0), (0, deg(60 * k), 0))
        paper(q, -sd / 2, 2, -ap, sd, 8, 2 * ap, share="heks")
        frame(q, -sd / 2 - 0.2, 1.4, -ap - 0.2, sd + 0.4, 0.6, 2 * ap + 0.4, share="rim")
        frame(q, -sd / 2 - 0.2, 10, -ap - 0.2, sd + 0.4, 0.6, 2 * ap + 0.4, share="rim")
    for k in range(6):
        a = deg(60 * k + 30)
        frame(j, R * math.cos(a) - 0.25, 10.6, R * math.sin(a) - 0.25, 0.5, 2.6, 0.5, mat="rumbai", share="rumbai_heks")
    return m


# ================================================================================================ item sprites (16 x 16)

def sprite(rows, palette):
    img = Image(16, 16)
    assert len(rows) == 16, (len(rows), rows[0])
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = palette[ch]
            img.set(x, y, c if len(c) == 4 else c + (255,))
    return img


def P(**kw):
    return {k: rgb(v) for k, v in kw.items()}


SPRITE_SRC = {
    "akta_rumah": ([
        "................",
        ".oooooooooooooo.",
        ".oPPPPPPPPPPPPo.",
        ".oPkkPkkkPkkPPo.",
        ".oPPPPPPPPPPPPo.",
        ".oPPPPPkkPPPPPo.",
        ".oPPPPkPPkPPPPo.",
        ".oPPPkkkkkkPPPo.",
        ".oPPPPkPPkPPPPo.",
        ".oPPPPkPdkPPPPo.",
        ".oPPPPPPPPPPPPo.",
        ".oPkkkkPPPRRRPo.",
        ".oPPPPPPPRrrRPo.",
        ".oPkkkPPPPRRRPo.",
        ".oqqqqqqqqqqqqo.",
        ".oooooooooooooo.",
    ], P(o="5A4630", P="EEE2C2", q="CDBE98", k="2A2420", d="8A5A30", R="C8281E", r="F0604A")),
    "kunci_rumah": ([
        "................",
        "...oooo.........",
        "..oGggGo........",
        ".oGo..oGo.......",
        ".oGo..oGoRR.....",
        "..oGggGo.rRR....",
        "...oGGo...rR....",
        "...oGGo...RRr...",
        "...oGGo...rRR...",
        "...oGGoo...r....",
        "...oGGGGo.......",
        "...oGGoo........",
        "...oGGGo........",
        "...oGGoo........",
        "...oGGGo........",
        "....ooo.........",
    ], P(o="4A3010", G="C8962E", g="F2D27A", R="C8281E", r="8A1610")),
    "topeng_pasar_hantu": ([
        "................",
        "....oooooooo....",
        "...oWWWWWWWWo...",
        "..oWWRWWWWRWWo..",
        "..oWRWWWWWWRWo..",
        "..oWWWWWWWWWWo..",
        "..oWkkWWWWkkWo..",
        "..oWkkWsWWkkWo..",
        "..oWWWWsWWWWWo..",
        "..oWRWWWWWWRWo..",
        "..oWWRWRRWRWWo..",
        "...oWWWWWWWWo...",
        "...oWRRRRRRWo...",
        "....oWWWWWWo....",
        ".....oooooo.....",
        "................",
    ], P(o="6A6060", W="F4F0E8", s="D8D0C0", R="C81E1E", k="1A1418")),
    "lencana_pejabat": ([
        "......RRRR......",
        ".....R.rr.R.....",
        "......RRRR......",
        "...oooooooooo...",
        "...oGgggggggGo..",
        "...oGbbbbbbbGo..",
        "...oGbbWWbbbGo..",
        "...oGbWWWWbbGo..",
        "...oGbbWkWWbGo..",
        "...oGbbbWWbbGo..",
        "...oGbbWbbWbGo..",
        "...oGbbbbbbbGo..",
        "...oGGGGGGGGGo..",
        "....ooooooooo...",
        ".....RR..RR.....",
        "......R..R......",
    ], P(o="5A3A08", G="C8962E", g="F2D27A", b="2A3A6A", W="F2F0E8", k="C81E1E", R="C8281E", r="8A1610")),
    "surat_kontrak": ([
        "................",
        "..ww........ww..",
        ".wPPwwwwwwwwPPw.",
        ".wPPPPPPPPPPPPw.",
        "..wPkkPkkPkkPw..",
        "..wPPPPPPPPPPw..",
        "..wPkkkPkkkPPw..",
        "..wPPPPPPPPPPw..",
        "..RRRRRRRRRRRR..",
        "..wPkkPkPPkkPw..",
        "..wPPPPPPPrrPw..",
        "..wPkkkkPPrRPw..",
        ".wPPPPPPPPPPPPw.",
        ".wPPwwwwwwwwPPw.",
        "..ww........ww..",
        "................",
    ], P(w="8A6A40", P="EEE2C2", k="2A2420", R="C8281E", r="E05A40")),
    "telur_merah": ([
        "................",
        "................",
        "......oooo......",
        ".....oRRRRo.....",
        "....oRrRRRRo....",
        "....oRrRRRRo....",
        "...oRRRRRRRRo...",
        "...oRRRRRRRRo...",
        "...oRRRRRRRdo...",
        "...oRRRRRRRdo...",
        "...oRRRRRRddo...",
        "....oRRRRddo....",
        "....oRdddddo....",
        ".....oooooo.....",
        "................",
        "................",
    ], P(o="5A0A0A", R="D02A22", r="F8A090", d="9A1612")),
    "kue_pengantin": ([
        "................",
        "................",
        ".....oooooo.....",
        "...ooYYYYYYoo...",
        "..oYYYYYYYYYYo..",
        ".oYYYRYYRYYYYYo.",
        ".oYYRRRRRRRYYYo.",
        ".oYYYRYYRYYYYYo.",
        ".oYYRRRRRRRYYYo.",
        ".oYYRYRRYRYYYYo.",
        ".oyYYYYYYYYYYyo.",
        ".obyyyyyyyyyybo.",
        "..obbbbbbbbbbo..",
        "...oooooooooo...",
        "................",
        "................",
    ], P(o="5A3A10", Y="F2D890", y="D8B060", b="A8783A", R="C81E1E")),
    "sup_hangat": ([
        ".....s...s......",
        "......s...s.....",
        ".....s...s......",
        "......s...s.....",
        "................",
        ".oooooooooooooo.",
        ".oSSSgSSSSoSSSo.",
        ".oSSSSSSgSSSSSo.",
        "..oWWWWWWWWWWo..",
        "..oWbWWbWWbWWo..",
        "...oWWWWWWWWo...",
        "...owWWWWWWwo...",
        "....owwwwwwo....",
        ".....oWWWWo.....",
        ".....oooooo.....",
        "................",
    ], P(o="3A3A44", s="E8E8F0", S="D89A40", g="5A9A3A", W="F0F2F4", b="4A6AB0", w="C8CCD8")),
    "bubur_laba": ([
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        ".oCCrCCnCCCrCCo.",
        ".oCnCCCCrCnCCCo.",
        ".oCCCrCCCCCCnCo.",
        "..oBBBBBBBBBBo..",
        "..oBbBBBbBBBbo..",
        "...oBBBBBBBBo...",
        "...oBBBBBBBBo...",
        "....obbbbbbo....",
        ".....oBBBBo.....",
        ".....oooooo.....",
        "................",
        "................",
    ], P(o="3A2A1A", C="E8D8B0", r="9A2A20", n="8A5A30", B="8A4A2A", b="6A3418")),
    "kue_bulan": ([
        "................",
        "................",
        ".....oooooo.....",
        "...ooKKKKKKoo...",
        "..oKKkKKKKkKKo..",
        "..oKkKKkkKKkKo..",
        ".oKKKkKKKKkKKKo.",
        ".oKkKKkkkkKKkKo.",
        ".oKKKkKKKKkKKKo.",
        ".oKkKKkkkkKKkKo.",
        "..oKKkKKKKkKKo..",
        "..oKkKKkkKKkKo..",
        "...ooKKKKKKoo...",
        ".....oooooo.....",
        "................",
        "................",
    ], P(o="5A3010", K="D89A48", k="A86A28")),
    "kue_keranjang": ([
        "................",
        "................",
        "................",
        "......RR........",
        ".....oRRo.......",
        "...ooooooooo....",
        "..oNNNNNNNNNo...",
        ".oNnNNNNNNNnNo..",
        ".oNNNNNNNNNNNo..",
        ".oNNNNNNNNNNNo..",
        ".onNNNNNNNNNno..",
        "..oNNNNNNNNNo...",
        ".LLooooooooooLL.",
        "LLlLLLLLLLLLLlLL",
        ".LLLLLLLLLLLLLL.",
        "................",
    ], P(o="3A1A08", N="8A4A20", n="B87038", R="C81E1E", L="4A8A3A", l="7AB060")),
    "air_plum": ([
        "................",
        "................",
        "...oooooooooo...",
        "...oUUUUiUUUo...",
        "...ouUUiiUUuo...",
        "...oUUUUUUUUo...",
        "...oUUUUUiUUo...",
        "...oUuUUiiUUo...",
        "....oUUUUUUo....",
        "....oUUUUuUo....",
        "....oUUUUUUo....",
        ".....oUUUUo.....",
        ".....ooccoo.....",
        "......occo......",
        "....oooooooo....",
        "................",
    ], P(o="5A5A66", U="5A1A3A", u="8A3A5A", i="D8E8F0", c="C8C8D0")),
    "permen_gula": ([
        "......aaa.......",
        ".....a...a......",
        "....a.aa..a.....",
        "....a.a.a.a.....",
        ".....a..aa......",
        "...aa.a....aa...",
        "..a....aaaa..a..",
        "..a.aa......a...",
        "...a..aaaaaa....",
        "....aa..s.......",
        "........s.......",
        "........s.......",
        "........s.......",
        "........s.......",
        "........s.......",
        "........s.......",
    ], P(a="E8A030", s="D8C090")),
    "tanghulu": ([
        "..........oo....",
        ".........oRRo...",
        "........oRrRo...",
        "........oRRRo...",
        ".......oRRoo....",
        "......oRrRo.....",
        "......oRRRo.....",
        ".....oRRoo......",
        "....oRrRo.......",
        "....oRRRo.......",
        "...oRRoo........",
        "..oRrRo.........",
        "..oRRRo.........",
        "..ssoo..........",
        ".ss.............",
        "s...............",
    ], P(o="6A0A0A", R="D8241C", r="FFC0A8", s="D8C090")),
    "kentongan_tua": ([
        "................",
        "...oooo.........",
        "..oBBBBo........",
        "..oBbBBo........",
        "..oBkkBo........",
        "..oBkkBo........",
        "..oBkkBo....tt..",
        "..oBkkBo...t..t.",
        "..oBkkBo...t..t.",
        "..oBbBBo....tt..",
        "..oBBBBo...mm...",
        "..oBbBBo..mm....",
        "..oBBBBo.mm.....",
        "...oooo.mm......",
        "........m.......",
        "................",
    ], P(o="3A3010", B="A8963E", b="7C703A", k="2A2010", t="A8885A", m="6A4628")),
    "kwitansi_sumur": ([
        "................",
        "..oooooooooooo..",
        "..oPPPPPPPPPPo..",
        "..oPkkPkkkPPPo..",
        "..oPPPPPPPPPPo..",
        "..oPbbbbbbbbPo..",
        "..oPPPPPPPPPPo..",
        "..oPkPkPkkPPPo..",
        "..oPPPPPPPPPPo..",
        "..oPkkkPPRRRPo..",
        "..oPPPPPRrrRPo..",
        "..oPkkPPPRRRPo..",
        "..oPPPPPPPPPPo..",
        "..ovPvPvPvPvPo..",
        "..o.o.o.o.o.oo..",
        "................",
    ], P(o="6A5A40", P="F0E6CC", k="2A2420", b="4A6AA8", R="C8281E", r="F0704A", v="D8CCA8")),
    "surat_utang": ([
        "................",
        "...oooooooooo...",
        "...oPPPPPPPPo...",
        "...oPkkkkkPPo...",
        "...oPPPPPPPPo...",
        "...oPkkPkkkPo...",
        "...oPPPPPPPPo...",
        "...oPkkkPkkPo...",
        "...oPPPPPPPPo...",
        "...oPkkPPPPPo...",
        "...oPPPPRRRPo...",
        "...oPPPRrRrRo...",
        "...oPPPRRrRRo...",
        "...oPPPPRRRPo...",
        "...oooooooooo...",
        "................",
    ], P(o="6A5A40", P="E8DCC0", k="2A2420", R="B81E18", r="E05040")),
    "resep_sup": ([
        "................",
        "..oooooooooooo..",
        "..oBBBBBBBBBBo..",
        "..otBBBBBBBBBo..",
        "..oBBpppppBBBo..",
        "..otBpkkkpBBBo..",
        "..oBBpppppBBBo..",
        "..otBBBBBBBBBo..",
        "..oBBBBBBBBBBo..",
        "..otBBBSBBBBBo..",
        "..oBBBSSSBBBBo..",
        "..otBBBSBBBBBo..",
        "..oBBBBBBBBBBo..",
        "..oPPPPPPPPPPo..",
        "..oooooooooooo..",
        "................",
    ], P(o="2A1A10", B="5A7A4A", t="E8DCC0", p="F0E6CC", k="2A2420", S="D89A40", P="E8E0CC")),
    "wayang_kulit_kera_sakti": ([
        ".s.........GG...",
        ".s........GYYG..",
        ".s.......GYkYYG.",
        ".s.......GYYYG..",
        ".s........GRRG..",
        ".sGG....GRRRRRG.",
        ".sYYGGGGRRYYRRRG",
        ".s..GGRRRRYYRRG.",
        ".s......GRRRRG..",
        ".s......GYYYYG..",
        ".s.....GRR.RRG..",
        ".s.....GR...RG..",
        ".s....GRR...RRG.",
        ".s....GY.....YG.",
        ".s.......s......",
        ".........s......",
    ], P(s="D8C090", G="4A2A10", Y="E8B030", k="2A1A10", R="C8281E")),
    "wayang_kulit_ular_putih": ([
        "........kk......",
        ".......kWWk.....",
        "......kWWWWk....",
        "......kWkWkk....",
        ".......kWWk.....",
        "......kWWWWk....",
        ".....kWGGGGWk...",
        "....kWWGWWGWWk..",
        "...kW.kWGGWk.Wk.",
        "......kWWWWk....",
        ".....kWGWWGWk...",
        ".....kWWGGWWk...",
        "....kWWWWWWWWk..",
        "....kGGGGGGGGk..",
        ".........s......",
        ".........s......",
    ], P(k="3A4A4A", W="F0F4F0", G="6AC08A", s="D8C090")),
    "wayang_kulit_hua_mulan": ([
        ".......kkk....R.",
        "......kRRRk..R..",
        "......kWkWk.R...",
        ".......kWk.R....",
        "......kAAAkR....",
        ".....kAYAYAk....",
        "....kAAAAAAAk...",
        "...kW.kAYAk.Wk..",
        "......kAAAk.....",
        "......kRRRk.....",
        ".....kRRRRRk....",
        ".....kRR.RRk....",
        "....kAA...AAk...",
        "....kk.....kk...",
        "........s.......",
        "........s.......",
    ], P(k="2A1A10", R="C8281E", W="F0D8B0", A="8A8A90", Y="E0B040", s="D8C090")),
    "wayang_kulit_hakim_bao": ([
        "...kk.kkkk.kk...",
        "..kKKkKKKKkKKk..",
        "...kk.kKKk.kk...",
        "......kNYNk.....",
        "......kNNNk.....",
        "......kNNNk.....",
        "....kkkBBBkkk...",
        "...kBBBBYBBBBk..",
        "..kB.kBBBBBk.Bk.",
        ".....kBBYBBk....",
        ".....kBBBBBk....",
        ".....kBBBBBk....",
        "....kBBBBBBBk...",
        "....kYYYYYYYk...",
        "........s.......",
        "........s.......",
    ], P(k="1A1418", K="2A2A30", N="2A2420", Y="E0B040", B="2A2A3A", s="D8C090")),
}

ITEM_IDS = ["akta_rumah", "kunci_rumah", "topeng_pasar_hantu", "lencana_pejabat", "surat_kontrak", "telur_merah", "kue_pengantin",
            "sup_hangat", "bubur_laba", "kue_bulan", "kue_keranjang", "air_plum", "permen_gula", "tanghulu", "kentongan_tua",
            "kwitansi_sumur", "surat_utang", "wayang_kulit_kera_sakti", "wayang_kulit_ular_putih", "wayang_kulit_hua_mulan",
            "wayang_kulit_hakim_bao", "resep_sup"]
HANDHELD = {"kunci_rumah", "permen_gula", "tanghulu", "wayang_kulit_kera_sakti", "wayang_kulit_ular_putih", "wayang_kulit_hua_mulan",
            "wayang_kulit_hakim_bao", "kentongan_tua"}


def topeng_head_model():
    """The paper mask worn on the head (display context head): a curved face plate 8 x 8 a hair in front of the face,
    two cheek wings and a forehead band, eye holes cut, red stripes (item texture kota42/topeng_wajah)."""
    els = []

    def box(f, t, uv_front, faces=("north", "south", "east", "west", "up", "down")):
        e = {"from": f, "to": t, "faces": {}}
        for fc in faces:
            e["faces"][fc] = {"uv": uv_front if fc in ("north", "south") else [0, 0, 1, 1], "texture": "#wajah"}
        els.append(e)
    box([4, 4, 3.4], [12, 12, 4], [4, 4, 12, 12])
    box([3, 5, 3.8], [4, 11, 6], [3, 5, 4, 11])
    box([12, 5, 3.8], [13, 11, 6], [12, 5, 13, 11])
    box([4, 12, 3.6], [12, 13, 5], [4, 3, 12, 4])
    return {"textures": {"wajah": "kultivator:item/kota42/topeng_wajah", "particle": "kultivator:item/kota42/topeng_wajah"},
            "elements": els,
            "display": {"head": {"rotation": [0, 0, 0], "translation": [0, -3.2, -4.2], "scale": [1.02, 1.02, 1.02]}}}


def topeng_wajah():
    rows = [
        "................",
        "................",
        "................",
        "...WWWWWWWWWW...",
        "...WWRWWWWRWW...",
        "...WRWWWWWWRW...",
        "...WWWWWWWWWW...",
        "...W..WWWW..W...",
        "...W..WsWW..W...",
        "...WWWWsWWWWW...",
        "...WRWWWWWWRW...",
        "...WWRWRRWRWW...",
        "...WWWWWWWWWW...",
        "...WRRRRRRRRW...",
        "................",
        "................",
    ]
    img = sprite(rows, P(W="F4F0E8", s="D8D0C0", R="C81E1E"))
    for y in range(16):
        for x in range(16):
            if img.get(x, y)[3] == 0:
                img.set(x, y, (244, 240, 232, 255))
    for (x, y) in ((4, 7), (5, 7), (4, 8), (5, 8), (10, 7), (11, 7), (10, 8), (11, 8)):
        img.set(x, y, (0, 0, 0, 0))
    return img


def item_assets():
    sprites = {}
    for name in ITEM_IDS:
        rows, pal = SPRITE_SRC[name]
        sprites[name] = sprite(rows, pal)
        save(sprites[name], os.path.join(ITEM_TEX, name + ".png"))
        write_json(os.path.join(ITEM_MODEL, name + ".json"),
                   {"parent": "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated",
                    "textures": {"layer0": "kultivator:item/kota42/" + name}})
        model = {"type": "minecraft:model", "model": "kultivator:item/kota42/" + name}
        if name == "topeng_pasar_hantu":
            model = {"type": "minecraft:select", "property": "minecraft:display_context",
                     "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": "kultivator:item/kota42/topeng_pasar_hantu_kepala"}}],
                     "fallback": model}
        write_json(os.path.join(ITEM_DEF, name + ".json"), {"model": model})
    save(topeng_wajah(), os.path.join(ITEM_TEX, "topeng_wajah.png"))
    sprites["topeng_wajah"] = topeng_wajah()
    write_json(os.path.join(ITEM_MODEL, "topeng_pasar_hantu_kepala.json"), topeng_head_model())
    return sprites


# ================================================================================================ blocks (42.12.3)

def btex(name, painter, size=32):
    img = Image(size, size)
    rng = random.Random(stable(name))
    for y in range(size):
        for x in range(size):
            c = painter(x / 2.0, y / 2.0, size / 2.0, size / 2.0, "front", rng)
            if c is not None:
                img.set(x, y, rgba(c))
    BLOCK_TEXTURES[name] = img
    return name


BLOCK_TEXTURES = {}


def sempoa_painter():
    def p(fx, fy, fw, fh, face, rng):
        if fx < 1 or fx > fw - 1 or fy < 1 or fy > fh - 1:
            return rgb("4A2A14")
        if abs(fy - fh * 0.32) < 0.4:
            return rgb("4A2A14")
        col = int(fx - 1) % 2
        if col == 1:
            return rgb("3A2010")
        row = fy - 1
        if int(row) % 2 == 0:
            return rgb("8A3A1E") if int(fx) % 4 else rgb("A8502A")
        return rgb("C8B898")
    return p


def build_block_textures():
    btex("kayu_rak", kayu("8A5E36", 1301))
    btex("kayu_rak_gelap", kayu("5A3A22", 1303, seam=4))
    btex("lak_merah", lak("9A1E18", 1305, tepi="D8B050"))
    btex("lak_merah_polos", lak("9A1E18", 1307))
    btex("lak_hitam", lak("1E1A18", 1309, tepi="C9A24A"))
    btex("emas", emas(1311))
    btex("perunggu", emas(1313))
    btex("sempoa", sempoa_painter())
    btex("buku", kertas("EEE2C2", "8A7A5A", 1315, step=2))
    btex("rantai", logam("5A5E64", 1317, karat=False))
    btex("kayu_tiang", kayu("4A2E1A", 1319))
    btex("genteng", polos("3A3E44", 0.12, 1321))
    btex("kasir_depan", lak("9A1E18", 1323, tepi="D8B050"))
    # the counter front: a gold 福 roundel
    img = BLOCK_TEXTURES["kasir_depan"]
    g = GLYPH["fu"]
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy)
            if 9.5 < r < 11:
                img.set(x, y, (216, 176, 80, 255))
            elif r <= 9.5:
                gx, gy = int((dx + 8)), int((dy + 8))
                if 0 <= gx < 16 and 0 <= gy < 16 and g[gy][gx] == "#":
                    img.set(x, y, (242, 210, 122, 255))


class BModel:
    """A block JSON model of cuboid elements on whole small textures (uv = the face rectangle in block pixels)."""

    def __init__(self, name, particle):
        self.name = name
        self.els = []
        self.tex = {}
        self.particle = particle

    def key(self, t):
        if t not in self.tex:
            self.tex[t] = "t%d" % len(self.tex)
        return self.tex[t]

    def box(self, f, t, tex, faces=None, rot=None):
        e = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": {}}
        dims = {"north": (f[0], 16 - t[1], t[0], 16 - f[1]), "south": (16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]),
                "east": (16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]), "west": (f[2], 16 - t[1], t[2], 16 - f[1]),
                "up": (f[0], f[2], t[0], t[2]), "down": (f[0], 16 - t[2], t[0], 16 - f[2])}
        for side, uv in dims.items():
            tt = (faces or {}).get(side, tex)
            if tt is None:
                continue
            e["faces"][side] = {"uv": [round(max(0, min(16, v)), 3) for v in uv], "texture": "#" + self.key(tt)}
        if rot:
            e["rotation"] = rot
        self.els.append(e)
        return e

    def json(self):
        tx = {k: "kultivator:block/kota42/" + t for t, k in self.tex.items()}
        tx["particle"] = "kultivator:block/kota42/" + self.particle
        return {"parent": "minecraft:block/block", "textures": tx, "elements": self.els}


def block_models():
    """Models in the north orientation (front = the customer side = -z, FACING north); blockstates rotate by y."""
    out = {}
    # rak_dagang: three shelves against the back half, open to the front
    m = BModel("rak_dagang", "kayu_rak")
    m.box([1, 0, 15], [15, 16, 16], "kayu_rak_gelap")
    m.box([0, 0, 8], [1, 16, 16], "kayu_rak")
    m.box([15, 0, 8], [16, 16, 16], "kayu_rak")
    for y in (0, 5, 10):
        m.box([1, y, 8], [15, y + 1, 15], "kayu_rak")
        m.box([1, y + 1, 8], [15, y + 1.8, 8.8], "lak_merah_polos")
    m.box([0, 15, 7.6], [16, 16, 16], "kayu_rak_gelap")
    m.box([0.4, 16, 7.6], [15.6, 16.6, 8.4], "lak_merah_polos")
    out["rak_dagang"] = m
    # meja_kasir: a red counter 12 high with a gold-framed front, top board, abacus, bell and ledger
    m = BModel("meja_kasir", "lak_merah")
    m.box([0.5, 0, 0.5], [15.5, 1.2, 15.5], "kayu_rak_gelap")
    m.box([1, 1.2, 1], [15, 11, 15], "lak_merah", faces={"north": "kasir_depan"})
    m.box([0, 11, 0], [16, 12, 16], "kayu_rak_gelap")
    m.box([0, 10.4, 0], [16, 11, 0.6], "emas")
    m.box([2, 12, 3], [10, 12.6, 7], "kayu_tiang")
    m.box([2, 12.6, 3], [10, 15.2, 3.6], "kayu_tiang")
    m.box([2, 12.6, 6.4], [10, 15.2, 7], "kayu_tiang")
    m.box([2.4, 12.6, 3.6], [9.6, 15.2, 6.4], "sempoa", faces={"up": None, "down": None, "east": None, "west": None})
    m.box([1.6, 12.6, 3], [2.2, 15.2, 7], "kayu_tiang")
    m.box([9.8, 12.6, 3], [10.4, 15.2, 7], "kayu_tiang")
    m.box([11.5, 12, 3.5], [13.5, 13.8, 5.5], "perunggu")
    m.box([12.2, 13.8, 4.2], [12.8, 15, 4.8], "kayu_tiang")
    m.box([4, 12, 9], [11, 12.8, 13.5], "buku")
    m.box([4, 12.8, 9], [11, 13, 13.5], "lak_hitam")
    out["meja_kasir"] = m
    # papan_nama_toko: a lacquered board 14 x 8 x 1 hanging on two chains from the block above
    m = BModel("papan_nama_toko", "lak_hitam")
    m.box([1, 2, 7.5], [15, 10, 8.5], "lak_hitam")
    m.box([0.6, 9.6, 7.3], [15.4, 10.4, 8.7], "emas")
    m.box([0.6, 1.6, 7.3], [15.4, 2.4, 8.7], "emas")
    for x in (3, 12.4):
        m.box([x, 10, 8], [x + 0.6, 16, 8], "rantai", faces={"east": None, "west": None, "up": None, "down": None})
        m.box([x + 0.3, 10, 7.7], [x + 0.3, 16, 8.3], "rantai", faces={"north": None, "south": None, "up": None, "down": None})
    out["papan_nama_toko"] = m
    # papan_cabang: a dark board between two posts under a small tiled roof, on feet
    m = BModel("papan_cabang", "lak_hitam")
    for x in (0.5, 13.5):
        m.box([x, 0, 7], [x + 2, 16, 9], "kayu_tiang")
        m.box([x - 0.5, 0, 5.5], [x + 2.5, 1, 10.5], "kayu_tiang")
    m.box([2.5, 3, 7.5], [13.5, 14, 8.5], "lak_hitam")
    m.box([2.5, 13.4, 7.3], [13.5, 14, 8.7], "emas")
    m.box([2.5, 3, 7.3], [13.5, 3.6, 8.7], "emas")
    m.box([-0.5, 15, 5], [16.5, 16, 11], "genteng")
    m.box([1, 16, 6.5], [15, 16.6, 9.5], "genteng")
    out["papan_cabang"] = m
    return out


def block_assets():
    build_block_textures()
    models = block_models()
    used = set()
    for name, m in models.items():
        write_json(os.path.join(BLOCK_MODEL, name + ".json"), m.json())
        used.update(m.tex)
        used.add(m.particle)
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": "kultivator:block/kota42/" + name}
            if y:
                v["y"] = y
            variants["facing=" + facing] = v
        write_json(os.path.join(BLOCKSTATE, name + ".json"), {"variants": variants})
        write_json(os.path.join(ITEM_DEF, name + ".json"), {"model": {"type": "minecraft:model", "model": "kultivator:block/kota42/" + name}})
        for e in m.els:
            for v in e["from"] + e["to"]:
                if v < -16 or v > 32:
                    ERRORS.append("block %s element out of range" % name)
    for t in sorted(used):
        save(BLOCK_TEXTURES[t], os.path.join(BLOCK_TEX, t + ".png"))
    return models


# ================================================================================================ glyphs, banners, couplets

def glyph_textures():
    out = {}
    for gid in GLYPH_TOWN:
        rows = GLYPH[gid]
        img = Image(16, 16)
        for y in range(16):
            for x in range(16):
                if rows[y][x] == "#":
                    img.set(x, y, (255, 255, 255, 255))
                elif any(0 <= x + dx < 16 and 0 <= y + dy < 16 and rows[y + dy][x + dx] == "#" for dx, dy in ((-1, 0), (0, -1), (-1, -1))):
                    img.set(x, y, (60, 50, 40, 110))
        save(img, os.path.join(GLYPH_TEX, gid + ".png"))
        out[gid] = img
    return out


PANJI_KEADAAN = {
    # name: (cloth, border, glyph or None, glyph colour, swallow tail)
    "biasa": ("F2F0EA", "B8B4AC", None, None, True),
    "festival": ("C8281E", "E8B848", "fu", "F2D27A", True),
    "festival_shou": ("C8281E", "E8B848", "shou", "F2D27A", True),
    "tahun_baru": ("D02A22", "F2D27A", "chun", "F8E090", True),
    "panen": ("D8B058", "8A5A20", "feng", "6A3A10", True),
    "perang": ("1E1A1C", "B01E18", "jie", "D83A2A", True),
    "sekte": ("F2F0EA", "B8B4AC", None, None, False),
    "arak": ("F0EAD8", "B01E18", "jiu", "1E1A18", False),
}


def panji_textures():
    out = {}
    for name, (cloth, border, gid, gcol, tail) in PANJI_KEADAAN.items():
        img = Image(32, 64)
        cb, bb = rgb(cloth), rgb(border)
        for y in range(64):
            for x in range(32):
                if name == "arak" and x > 27 and (y % 6) < 3:
                    continue  # the toothed free edge of the wine flag
                if tail and y > 56 and abs(x - 15.5) < (y - 56) * 2.0:
                    continue
                c = shade(cb, 0.9 + 0.12 * nz(1401, x, y, 4))
                if (x + y) % 2 == 0:
                    c = shade(c, 0.96)
                if x < 2 or x > 29 or y < 3 or (name != "arak" and y > 60 and not tail):
                    c = bb
                if name == "arak" and (x > 25 or y > 59):
                    c = bb
                if 3 <= y <= 4 and x % 3 == 0:
                    c = shade(bb, 0.7)
                img.set(x, y, rgba(c))
        if gid:
            rows = GLYPH[gid]
            gc = rgb(gcol)
            # the glyph (a 16 x 16 matrix) scaled to fit the cloth inside the borders (x 2..29): 24 px, the wine flag
            # (free toothed edge from x 26) 22 px; nearest sampling at the pixel centres keeps every stroke
            size = 24 if name != "arak" else 22
            ox = 2 + ((28 if name != "arak" else 24) - size) // 2
            oy = 14 if name != "arak" else 16
            for y in range(size):
                for x in range(size):
                    if rows[int((y + 0.5) * 16 / size)][int((x + 0.5) * 16 / size)] == "#":
                        px, py = x + ox, y + oy
                        if 2 <= px <= 29 and img.get(px, py)[3] > 0:
                            img.set(px, py, rgba(gc))
        save(img, os.path.join(PANJI_TEX, name + ".png"))
        out[name] = img
    return out


KUPLET = {"kiri": ["chun", "fu", "shou", "feng"], "kanan": ["xi", "fu", "chun", "shou"]}


def kuplet_textures():
    out = {}
    for side, gids in KUPLET.items():
        img = Image(8, 48)
        for y in range(48):
            for x in range(8):
                c = shade(rgb("C8241C"), 0.9 + 0.14 * nz(1501, x, y, 3))
                if (x * 5 + y * 3) % 23 == 0:
                    c = rgb("E8C060")
                img.set(x, y, rgba(c))
        for i, gid in enumerate(gids):
            rows = GLYPH[gid]
            for y in range(8):
                for x in range(8):
                    hit = sum(rows[min(15, y * 2 + dy)][min(15, x * 2 + dx)] == "#" for dy in (0, 1) for dx in (0, 1))
                    if hit >= 1 and 0 < x < 7:
                        img.set(x, 4 + i * 11 + y - 2, (24, 18, 16, 255))
        save(img, os.path.join(KOTA_TEX, "kuplet_%s.png" % side))
        out[side] = img
    return out


# ================================================================================================ shadow puppets

def wayang_atlas(naskah, figures):
    """64 x 64: 6 cells of 21 x 32 (3 x 2). Each cell: the body silhouette in rows 0..23 (feet at row 23), the arm strip
    in rows 25..30 (shoulder at x 0, hand to the right). Coloured translucent leather with punched dots."""
    img = Image(64, 64)
    for i, fig in enumerate(figures):
        ox, oy = (i % 3) * 21, (i // 3) * 32
        body, arm = fig
        for y, row in enumerate(body):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                c = WAYANG_PAL[ch]
                a = 235 if ch not in "o" else 255
                if ch not in "ok" and (x * 3 + y * 5) % 11 == 0:
                    a = 60  # punched pattern
                img.set(ox + x, oy + y, c + (a,))
        for y, row in enumerate(arm):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                img.set(ox + x, oy + 25 + y, WAYANG_PAL[ch] + (240,))
    save(img, os.path.join(ENT_TEX, "wayang_%s.png" % naskah))
    return img


WAYANG_PAL = {k: rgb(v) for k, v in {"o": "1A1410", "k": "2A1A10", "R": "B8301E", "Y": "D8A030", "G": "4A9A5A", "W": "E8E0C8",
                                         "B": "2A3A6A", "N": "2A2420", "A": "8A8A90", "P": "C86A8A", "C": "6A4A2A", "S": "8AB8D8",
                                         "H": "3A6A3A", "D": "6A1A1A", "L": "D8D0B8"}.items()}


def fig(head, body_c, accent, kind="robe", hat=None, extra=None):
    """A 21 x 24 silhouette built from rows: kind robe (long gown), armour, monkey, beast, object."""
    rows = [["."] * 21 for _ in range(24)]

    def put(x, y, ch):
        if 0 <= x < 21 and 0 <= y < 24:
            rows[y][x] = ch
    cx = 9
    # head (profile facing right), outline o
    for y in range(2, 7):
        for x in range(cx - 2, cx + 3):
            put(x, y, head)
    put(cx + 3, 4, head)
    put(cx + 2, 3, "o")
    if hat == "sayap":  # Judge Bao's winged hat
        for x in range(cx - 7, cx + 8):
            put(x, 1, "N")
        for x in range(cx - 2, cx + 3):
            put(x, 0, "N")
    elif hat == "mahkota":
        for x in range(cx - 2, cx + 3):
            put(x, 1, accent)
        put(cx, 0, accent)
    elif hat == "sanggul":
        for x in range(cx - 3, cx + 1):
            put(x, 1, "N")
        put(cx - 3, 2, "N")
        put(cx - 4, 2, accent)
    elif hat == "helm":
        for x in range(cx - 3, cx + 3):
            put(x, 1, "A")
        put(cx - 1, 0, "R")
    elif hat == "gundul":
        pass
    elif hat == "telinga":
        put(cx - 2, 1, head)
        put(cx + 2, 1, head)
    put(cx, 7, head)
    if kind in ("robe", "armour", "monkey"):
        for y in range(8, 23):
            half = 2 + (y - 8) * (5 if kind == "robe" else 3) // 14
            for x in range(cx - half, cx + half + 1):
                c = body_c
                if kind == "armour" and (x + y) % 3 == 0:
                    c = "A"
                if y in (12, 13) and abs(x - cx) <= half:
                    c = accent
                if abs(x - cx) == half:
                    c = "o"
                put(x, y, c)
        if kind != "robe":
            for y in range(19, 24):
                put(cx - 2, y, body_c)
                put(cx + 2, y, body_c)
        else:
            put(cx - 4, 23, "o")
            put(cx + 4, 23, "o")
        if kind == "monkey":
            for y in range(14, 22):
                put(cx - 5 - (y - 14) // 3, y, "Y")
    elif kind == "beast":
        for y in range(9, 18):
            for x in range(1, 20):
                if abs(y - 13) <= 4 - abs(x - 10) // 4:
                    put(x, y, body_c)
        for x in (3, 6, 14, 17):
            for y in range(18, 24):
                put(x, y, body_c)
        for y in range(5, 10):
            put(18, y, body_c)
            put(19, y, body_c)
        put(20, 6, accent)
    if extra == "tongkat":
        for y in range(0, 24):
            put(17, y, "Y")
    elif extra == "pedang":
        for y in range(6, 20):
            put(16, y, "L")
        put(15, 14, "Y")
        put(17, 14, "Y")
    elif extra == "mangkuk":
        for x in range(13, 18):
            put(x, 13, "Y")
        for x in range(14, 17):
            put(x, 14, "Y")
    elif extra == "payung":
        for x in range(10, 21):
            put(x, 3 + abs(x - 15) // 3, "P")
        for y in range(4, 16):
            put(15, y, "C")
    elif extra == "alat_tenun":
        for y in range(10, 24):
            put(1, y, "C")
            put(7, y, "C")
        for x in range(1, 8):
            put(x, 10, "C")
            put(x, 16, "W")
    return ["".join(r) for r in rows]


def arm(col, item=None):
    rows = [["."] * 21 for _ in range(6)]
    for x in range(0, 10):
        rows[2][x] = col
        rows[3][x] = col
    rows[1][9] = rows[4][9] = "o"
    rows[2][10] = rows[3][10] = "W"
    if item == "tongkat":
        for x in range(8, 21):
            rows[1][x] = "Y"
    elif item == "pedang":
        for x in range(11, 21):
            rows[2][x] = "L"
    elif item == "kipas":
        for y in range(0, 6):
            rows[y][12] = "W"
            rows[y][13] = "W"
    return ["".join(r) for r in rows]


def wayang_textures():
    sets = {
        "kera_sakti": [(fig("Y", "R", "Y", "monkey", "mahkota", "tongkat"), arm("R", "tongkat")),
                       (fig("W", "Y", "R", "robe", "gundul", "mangkuk"), arm("Y")),
                       (fig("P", "B", "Y", "armour", "telinga"), arm("B")),
                       (fig("C", "H", "Y", "robe", "gundul"), arm("H")),
                       (fig("G", "D", "Y", "armour", "helm", "pedang"), arm("D", "pedang")),
                       (fig("W", "W", "S", "beast", None), arm("W"))],
        "ular_putih": [(fig("W", "W", "G", "robe", "sanggul", "payung"), arm("W")),
                       (fig("W", "G", "W", "robe", "sanggul"), arm("G", "pedang")),
                       (fig("W", "B", "W", "robe", "mahkota"), arm("B", "kipas")),
                       (fig("W", "Y", "R", "robe", "gundul", "mangkuk"), arm("Y")),
                       (fig("S", "S", "W", "beast", None), arm("S")),
                       (fig("C", "C", "Y", "beast", None), arm("C"))],
        "hua_mulan": [(fig("W", "R", "A", "armour", "helm", "pedang"), arm("R", "pedang")),
                      (fig("W", "C", "Y", "robe", "mahkota"), arm("C")),
                      (fig("C", "N", "R", "beast", None), arm("N")),
                      (fig("W", "B", "Y", "armour", "helm"), arm("B", "pedang")),
                      (fig("D", "N", "R", "armour", "helm", "pedang"), arm("N", "pedang")),
                      (fig("W", "P", "W", "robe", "sanggul", "alat_tenun"), arm("P"))],
        "hakim_bao": [(fig("N", "B", "Y", "robe", "sayap"), arm("B", "kipas")),
                      (fig("W", "R", "Y", "armour", "helm", "pedang"), arm("R", "pedang")),
                      (fig("W", "P", "Y", "robe", "mahkota"), arm("P")),
                      (fig("W", "W", "B", "robe", "sanggul"), arm("W")),
                      (fig("N", "Y", "R", "robe", "sayap"), arm("Y")),
                      (fig("C", "C", "Y", "beast", None), arm("C"))],
    }
    return {n: wayang_atlas(n, f) for n, f in sets.items()}


# ================================================================================================ bridal sedan chair look

def tandu_pengantin():
    """The red bridal look of the 38 sedan chair: tandu_pasir.png recoloured, ochre and sand to vermilion and crimson,
    the dark red to deep red, gold kept (same band layout, so TanduRenderer draws it as a fourth texture)."""
    w, h, px = read_png(TANDU_SRC)
    img = Image(w, h)
    for i, (r, g, b, a) in enumerate(px):
        x, y = i % w, i // w
        if a == 0:
            continue
        mx, mn = max(r, g, b), min(r, g, b)
        lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
        sat = (mx - mn) / max(1, mx)
        gold = r > 170 and g > 130 and b < 110 and (r - g) < 70
        if gold:
            img.set(x, y, (r, g, b, a))
            continue
        if sat < 0.15:
            img.set(x, y, (r, g, b, a))
            continue
        nr, ng, nb = mix(rgb("5A0808"), rgb("E8382A"), min(1.0, lum * 1.5))
        img.set(x, y, (clamp(nr), clamp(ng), clamp(nb), a))
    save(img, os.path.join(ENT_TEX, "tandu_pengantin.png"))
    return img


# ================================================================================================ main

# per mesh: the preview poses (hidden part groups) so each variant can be read
PREVIEWS = {
    "tenda": [("pasar_0", {"tikar", "bangku_lapak", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_0", "lapak_1", "lapak_2", "lapak_3", "lapak_4"}),
              ("pasar_1_3", {"tikar", "bangku_lapak", "pasar_0", "pasar_2", "pasar_4", "lapak_0", "lapak_1", "lapak_2", "lapak_3", "lapak_4", "atap"}),
              ("pasar_2_4", {"tikar", "bangku_lapak", "pasar_0", "pasar_1", "pasar_3", "lapak_0", "lapak_1", "lapak_2", "lapak_3", "lapak_4", "atap"}),
              ("lapak_0", {"meja", "pasar_0", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_1", "lapak_2", "lapak_3", "lapak_4", "atap"}),
              ("lapak_1", {"meja", "pasar_0", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_0", "lapak_2", "lapak_3", "lapak_4", "atap"}),
              ("lapak_2", {"meja", "pasar_0", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_0", "lapak_1", "lapak_3", "lapak_4", "atap"}),
              ("lapak_3", {"meja", "pasar_0", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_0", "lapak_1", "lapak_2", "lapak_4", "atap"}),
              ("lapak_4", {"meja", "pasar_0", "pasar_1", "pasar_2", "pasar_3", "pasar_4", "lapak_0", "lapak_1", "lapak_2", "lapak_3", "atap"})],
    "panggung": [("opera", {"alat_1", "alat_2", "alat_3", "latar_1", "latar_2", "latar_3"} | {"panji_%s_%d" % (s, v) for s in ("k", "kn") for v in (1, 2, 3)}),
                 ("musik_tanpa_atap", {"atap", "alat_0", "alat_1", "alat_3", "latar_0", "latar_1", "latar_3"} | {"panji_%s_%d" % (s, v) for s in ("k", "kn") for v in (0, 2, 3)}),
                 ("akrobat_tanpa_atap", {"atap", "alat_0", "alat_2", "alat_3", "latar_0", "latar_2", "latar_3"} | {"panji_%s_%d" % (s, v) for s in ("k", "kn") for v in (0, 1, 3)}),
                 ("dongeng_tanpa_atap", {"atap", "alat_0", "alat_1", "alat_2", "latar_0", "latar_1", "latar_2"} | {"panji_%s_%d" % (s, v) for s in ("k", "kn") for v in (0, 1, 2)})],
    "lapak_hantu": [("jimat", {"barang_1", "barang_2", "barang_3"}), ("guci", {"barang_0", "barang_2", "barang_3"}),
                    ("gulungan", {"barang_0", "barang_1", "barang_3"}), ("pusaka", {"barang_0", "barang_1", "barang_2"})],
    "api_bahan": [("jerami", {"bahan_1", "bahan_2"}), ("minyak", {"bahan_0", "bahan_2"}), ("kayu", {"bahan_0", "bahan_1"})],
    "api_nyala": [("f0", {"nyala_%d" % i for i in range(1, 8)}), ("f4", {"nyala_%d" % i for i in range(8) if i != 4})],
    "barang": [("j%d" % i, {"jenis_%d" % k for k in range(5) if k != i}) for i in range(5)],
    "usungan": [("diusung", {"kuda_kuda", "bunga_putih"}), ("diam_putih", {"bunga_merah"})],
    "jamuan": [("semua", set())],
    "dadu": [("kedai", {"jenis_1", "jenis_2"}), ("guanpu_buka", {"jenis_0", "jenis_2", "cawan"}), ("gelap", {"jenis_0", "jenis_1"})],
    "layar": [("depan", set())],
    "batu_uji": [("depan", set())],
    "batu_uji_glif": [("semua", set())],
    "pengungsi": [("siang", set())],
    "lentera": [("j%d" % i, {"jenis_%d" % k for k in range(5) if k != i}) for i in range(5)],
    "lentera_kertas": [("j%d" % i, {"jenis_%d" % k for k in range(5) if k != i}) for i in range(5)],
    "tenda_warna": [("semua", set())],
}
PREVIEW_POSE = {
    "pengungsi": {"engsel_k": {"rot": (0, deg(-150), 0)}, "engsel_kn": {"rot": (0, deg(150), 0)}},
    "dadu": {"cawan": {"pivot": (5.5, 10.5, 2), "rot": (0, 0, deg(-100))}},
}


def main():
    global PREVIEW_DIR
    PREVIEW_DIR = sys.argv[1] if len(sys.argv) > 1 else None
    builders = [mesh_tenda, mesh_tenda_warna, mesh_lapak_hantu, mesh_panggung, mesh_api, mesh_api_nyala, mesh_barang, mesh_usungan,
                mesh_jamuan, mesh_dadu, mesh_layar, mesh_batu_uji, mesh_batu_uji_glif, mesh_pengungsi,
                lambda: mesh_lentera(False), lambda: mesh_lentera(True)]
    for b in builders:
        b()
    for m in MESHES.values():
        pack(m)
    tex_count = 0
    for m in MESHES.values():
        looks = list(m.looks)
        for look in looks:
            img = paint(m, look)
            suffix = "" if len(looks) == 1 else "_" + look
            save(img, os.path.join(ENT_TEX, m.name + suffix + ".png"))
            tex_count += 1
            if m.glow:
                g = paint(m, look, glow_only=True)
                save(g, os.path.join(ENT_TEX, m.name + suffix + "_pancar.png"))
                tex_count += 1
            if PREVIEW_DIR:
                poses = PREVIEWS.get(m.name, [("semua", set())])
                for tag, hidden in (poses if look == looks[0] else poses[:1]):
                    preview(m, look, os.path.join(PREVIEW_DIR, "m_%s_%s_%s.png" % (m.name, look, tag)), hidden=hidden,
                            overrides=PREVIEW_POSE.get(m.name))
            if PREVIEW_DIR and look == looks[0]:
                big(img, 3).save(os.path.join(PREVIEW_DIR, "t_%s.png" % m.name))
    write_java()
    sprites = item_assets()
    block_assets()
    glyphs = glyph_textures()
    panji = panji_textures()
    kuplet = kuplet_textures()
    wayang = wayang_textures()
    tandu_pengantin()
    boxes = sum(len(m.boxes()) for m in MESHES.values())
    print("meshes %d (%d boxes, %d parts), entity textures %d, items %d, blocks 4, glyphs %d, banners %d, written %d, errors %d"
          % (len(MESHES), boxes, sum(len(m.parts) for m in MESHES.values()), tex_count, len(ITEM_IDS), len(glyphs), len(panji),
             len(WRITTEN), len(ERRORS)))
    for m in MESHES.values():
        print("  %-16s %4d x %-4d boxes %4d parts %3d" % (m.name, m.width, m.height, len(m.boxes()), len(m.parts)))
    for e in ERRORS:
        print("ERROR:", e)
    if PREVIEW_DIR:
        sheet(sprites, os.path.join(PREVIEW_DIR, "items.png"), factor=6, cols=8)
        sheet(glyphs, os.path.join(PREVIEW_DIR, "glyphs.png"), factor=6, cols=10)
        sheet({k: v for k, v in panji.items()}, os.path.join(PREVIEW_DIR, "panji.png"), factor=4, cols=8)
        sheet(kuplet, os.path.join(PREVIEW_DIR, "kuplet.png"), factor=6, cols=2)
        sheet(wayang, os.path.join(PREVIEW_DIR, "wayang.png"), factor=4, cols=4)
        sheet(BLOCK_TEXTURES, os.path.join(PREVIEW_DIR, "block_tex.png"), factor=4, cols=7)
        block_preview(os.path.join(PREVIEW_DIR, "blocks.png"))
    if ERRORS:
        raise SystemExit(1)


def block_preview(path):
    """Block models through the entity preview: each element becomes a box on its own texture."""
    for name, bm in block_models().items():
        looks = {}
        mats = {}
        m = Mesh("blok_" + name, 256, {"dasar": mats})
        del MESHES["blok_" + name]
        for i, e in enumerate(bm.els):
            f, t = e["from"], e["to"]
            tname = next(iter(e["faces"].values()))["texture"][1:]
            tex_name = next(k for k, v in bm.tex.items() if v == tname)
            src = BLOCK_TEXTURES[tex_name]

            def painter(fx, fy, fw, fh, face, rng, src=src):
                return src.get(int(fx * 2) % 32, int(fy * 2) % 32)
            mats["e%d" % i] = painter
            # block (x, y up, z) -> model (x - 8, 24 - y, z - 8)
            m.root.b(f[0] - 8, 24 - t[1], f[2] - 8, t[0] - f[0], t[1] - f[1], t[2] - f[2], "e%d" % i)
        pack(m)
        preview(m, "dasar", path.replace(".png", "_%s.png" % name), size=260)


if __name__ == "__main__":
    main()

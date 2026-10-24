"""Tiny software renderer for Sosok previews (DESIGN 40.3.11 (e), module 40K): the player-shaped body with its outer
layers plus every visible hair/garment cube of a resolved look, textured from the composed atlas, drawn in a 3/4 view
with a z-buffer. Cube placement follows the same rules as ``SosokBusanaModel`` (parents, pivots, pairs, rotations), so
the preview is the in-game model at rest.

UV mapping per face is exactly ``ModelPart.Cube`` / ``ModelPart.Polygon`` of 26.3 (front texture left = the wearer's
right; side strips run back->front on the right side and front->back on the left side; mirror flips horizontally).
"""
import math

from sosok_lib import UV_RAMBUT, UV_BUSANA, RAMBUT_KUBUS, BUSANA_KUBUS, blend
from sosok_rupa import V_KEPANG_DEPAN, V_BAHU_KIRI, V_ROK_LEBAR, V_SANGGUL_KECIL, V_JAMBUL_SATU


class Part:
    def __init__(self, name, pivot, rot=(0, 0, 0), boxes=(), scale=(1, 1, 1)):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.boxes = list(boxes)   # (x0, y0, z0, w, h, d, u, v, grow, mirror)
        self.children = []
        self.scale = scale

    def add(self, child):
        self.children.append(child)
        return child


def player(slim):
    """The PlayerModel at rest with base + outer boxes (grow 0.5 hat, 0.25 others)."""
    aw = 3 if slim else 4
    ay = 2.0  # 26.3: slim and wide arms both pivot at y 2
    head = Part("kepala", (0, 0, 0), boxes=[(-4, -8, -4, 8, 8, 8, 0, 0, 0, False), (-4, -8, -4, 8, 8, 8, 32, 0, 0.5, False)])
    body = Part("badan", (0, 0, 0), boxes=[(-4, 0, -2, 8, 12, 4, 16, 16, 0, False), (-4, 0, -2, 8, 12, 4, 16, 32, 0.25, False)])
    rarm = Part("lengan_kanan", (-5, ay, 0), boxes=[(-aw + 1, -2, -2, aw, 12, 4, 40, 16, 0, False),
                                                   (-aw + 1, -2, -2, aw, 12, 4, 40, 32, 0.25, False)])
    larm = Part("lengan_kiri", (5, ay, 0), boxes=[(-1, -2, -2, aw, 12, 4, 32, 48, 0, False),
                                                 (-1, -2, -2, aw, 12, 4, 48, 48, 0.25, False)])
    rleg = Part("kaki_kanan", (-1.9, 12, 0), boxes=[(-2, 0, -2, 4, 12, 4, 0, 16, 0, False), (-2, 0, -2, 4, 12, 4, 0, 32, 0.25, False)])
    lleg = Part("kaki_kiri", (1.9, 12, 0), boxes=[(-2, 0, -2, 4, 12, 4, 16, 48, 0, False), (-2, 0, -2, 4, 12, 4, 0, 48, 0.25, False)])
    return {"kepala": head, "badan": body, "lengan_kanan": rarm, "lengan_kiri": larm, "kaki_kanan": rleg, "kaki_kiri": lleg}


def _cube_part(name, entry, pivot, mirror=False, slim=False, rot=None, scale=(1, 1, 1)):
    box = entry[2]
    if slim and "ramping" in entry[5]:
        box = entry[5]["ramping"]
    x0, y0, z0, w, h, d = box
    if mirror:
        x0 = -x0 - w
    r = rot if rot is not None else entry[5].get("rot", (0, 0, 0))
    if mirror:
        r = (r[0], -r[1], -r[2])
    return Part(name, pivot, r, [(x0, y0, z0, w, h, d, entry[3][0], entry[3][1], 0, mirror)], scale)


def attach_cubes(parts, look, slim):
    """Adds the look's visible cubes to the skeleton, following SosokBusanaModel's placement rules."""
    hair = {c for i, c in enumerate(RAMBUT_KUBUS) if look["kubus_rambut"] >> i & 1}
    body = {c for i, c in enumerate(BUSANA_KUBUS) if look["kubus_busana"] >> i & 1}
    var = look["varian"]
    made = {}

    def parent_of(name, default):
        return made.get(name) or parts.get(default)

    def place(table, cid, parent_name):
        entry = table[cid]
        par = entry[0]
        pair = entry[5].get("pasangan", False)
        px, py, pz = entry[1]
        sc = (1, 1, 1)
        if cid == "sanggul" and var & V_SANGGUL_KECIL:
            sc = (0.85, 0.85, 0.85)
        geser = 0.5 if slim else 0.0   # the slim arm is centred 0.5 px further in (BusanaBagian)
        if par == "lengan":
            p1 = _cube_part(cid, entry, (px + geser, py, pz), False, slim, scale=sc)
            if not (cid == "bahu_zirah" and var & V_BAHU_KIRI):
                parts["lengan_kanan"].add(p1)
            made[cid] = p1
            if pair:
                p2 = _cube_part(cid + "_kiri", entry, (-(px + geser), py, pz), True, slim, scale=sc)
                parts["lengan_kiri"].add(p2)
                made[cid + "_kiri"] = p2
            return
        if cid == "lengan_lebar_kantong":
            mx, my, mz = UV_BUSANA["lengan_lebar_manset"][1]
            parts["lengan_kanan"].add(_cube_part(cid, entry, (mx + geser + px, my + py, mz + pz)))
            parts["lengan_kiri"].add(_cube_part(cid + "_kiri", entry, (-(mx + geser) - px, my + py, mz + pz), True))
            return
        if par == "sabuk":
            # belt hangings hang from the body at the belt's height (also without a belt cube); the ruqun's ribbons
            # hang from the chest when there is no belt (BusanaBagian)
            y = 6.5 + py if (cid != "pita_sabuk" or "sabuk" in body) else 3.0
            parts["badan"].add(_cube_part(cid, entry, (px, y, pz)))
            if pair:
                parts["badan"].add(_cube_part(cid + "_kiri", entry, (-px, y, pz), True))
            return
        host = made.get(par) or parts.get(par)
        if host is None:
            return
        if cid == "rok_zirah":
            host.add(_cube_part(cid, entry, (px, py, -2.6)))
            back = _cube_part(cid + "_b", entry, (px, py, 2.6))
            host.add(back)
            return
        if cid == "rumbai_iblis":
            for i, x in enumerate((-3, 0, 3)):
                host.add(_cube_part("%s_%d" % (cid, i), entry, (x, py, -2.2 if i != 1 else 2.2)))
            return
        if cid == "manik_buyao":
            for i in range(3):
                host.add(_cube_part("%s_%d" % (cid, i), entry, (px + 0.8 * (i - 1), py, pz)))
            return
        if cid in ("kepang_1",) and var & V_KEPANG_DEPAN:
            for sgn in (1, -1):
                p = _cube_part(cid + ("" if sgn > 0 else "_kiri"), entry, (2.6 * sgn, 0.5, -2.2), sgn < 0)
                host.add(p)
                made[cid + ("" if sgn > 0 else "_kiri")] = p
            return
        if cid == "jambul_anak" and var & V_JAMBUL_SATU:
            host.add(_cube_part(cid, entry, (0, -8, -2.5)))
            return
        p = _cube_part(cid, entry, (px, py, pz), scale=sc)
        if cid in ("rok_panjang_depan", "rok_panjang_belakang", "rok_pendek_depan", "rok_pendek_belakang") and var & V_ROK_LEBAR:
            p.scale = (1.06, 1, 1)
        host.add(p)
        made[cid] = p
        if pair:
            host2 = made.get(par + "_kiri", host)
            p2 = _cube_part(cid + "_kiri", entry, (px if host2 is not host else -px, py, pz), True, scale=sc)
            host2.add(p2)
            made[cid + "_kiri"] = p2

    def host_needed(cid):
        """The part this hair cube hangs on in this look (alternate parents for ribbons, pins and braids)."""
        if cid == "pita_rambut" and "sanggul" not in hair and "cepol" not in hair and "ekor_kuda_1" in hair:
            return "ekor_kuda_1"
        if cid == "pita_rambut" and "cepol" in hair and "sanggul" not in hair:
            return "cepol_kiri"
        if cid == "tusuk_guan" and "guan_rendah" not in hair and "sanggul_rendah" in hair:
            return "sanggul_rendah"
        if cid == "kepang_2" and var & V_KEPANG_DEPAN:
            return "kepang_1_kiri"
        par = UV_RAMBUT[cid][0]
        return None if par in parts else par

    def place_hair(cid):
        e = UV_RAMBUT[cid]
        if cid == "pita_rambut" and "sanggul" not in hair and "cepol" not in hair and "ekor_kuda_1" in hair:
            made["ekor_kuda_1"].add(_cube_part(cid, e, (0.6, 0.6, 0.9)))
        elif cid == "pita_rambut" and "cepol" in hair and "sanggul" not in hair:
            made["cepol"].add(_cube_part(cid, e, (0, -0.5, 1.5)))
            made["cepol_kiri"].add(_cube_part(cid + "_kiri", e, (0, -0.5, 1.5), True))
        elif cid == "tusuk_guan" and "guan_rendah" not in hair and "sanggul_rendah" in hair:
            made["sanggul_rendah"].add(_cube_part(cid, e, (0, -1.4, 1.5)))
        elif cid == "kepang_2" and var & V_KEPANG_DEPAN:
            made["kepang_1"].add(_cube_part(cid, e, (0, 6, 0)))
            made["kepang_1_kiri"].add(_cube_part(cid + "_kiri", e, (0, 6, 0), True))
        else:
            place(UV_RAMBUT, cid, None)

    pending = [c for c in RAMBUT_KUBUS if c in hair]
    while pending:
        rest = []
        for cid in pending:
            need = host_needed(cid)
            if need is None or need in made:
                place_hair(cid)
            elif need in hair or need.replace("_kiri", "") in hair:
                rest.append(cid)
        if len(rest) == len(pending):
            break
        pending = rest
    for cid in BUSANA_KUBUS:
        if cid in body:
            place(UV_BUSANA, cid, None)


# ---------------------------------------------------------------------- maths


def _rot_matrix(rx, ry, rz):
    """rotationZYX(z, y, x) as used by ModelPart.translateAndRotate (degrees in, 3x3 out)."""
    x, y, z = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return _mm(_mm(Rz, Ry), Rx)


def _mm(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def _mv(m, v):
    return (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2])


def _faces(x0, y0, z0, w, h, d, u, v, grow, mirror):
    """The 6 polygons of a cube: list of (4 vertices (x, y, z), uv rect (u0, v0, u1, v1), shade)."""
    X0, Y0, Z0 = x0 - grow, y0 - grow, z0 - grow
    X1, Y1, Z1 = x0 + w + grow, y0 + h + grow, z0 + d + grow
    if mirror:
        X0, X1 = X1, X0
    t0, t1, t2, t3 = (X0, Y0, Z0), (X1, Y0, Z0), (X1, Y1, Z0), (X0, Y1, Z0)
    l0, l1, l2, l3 = (X0, Y0, Z1), (X1, Y0, Z1), (X1, Y1, Z1), (X0, Y1, Z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [((l1, l0, t0, t1), (u1, v0, u2, v1), 1.0),
             ((t2, t3, l3, l2), (u2, v1, u22, v0), 0.55),
             ((t0, l0, l3, t3), (u0, v1, u1, v2), 0.78),
             ((t1, t0, t3, t2), (u1, v1, u2, v2), 0.92),
             ((l1, t1, t2, l2), (u2, v1, u3, v2), 0.78),
             ((l0, l1, l2, l3), (u3, v1, u4, v2), 0.7)]
    out = []
    for verts, (a0, b0, a1, b1), shade in polys:
        # Polygon.remap: v[0]=(u1,v0) v[1]=(u0,v0) v[2]=(u0,v1) v[3]=(u1,v1)
        uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        if mirror:
            verts = verts[::-1]
            uvs = uvs[::-1]
        out.append((verts, uvs, shade))
    return out


def render(atlas, parts, yaw=-28, pitch=14, px=6, tex=128, bg=None):
    """Renders the skeleton (dict of root parts) to (w, h, ARGB list). px = screen pixels per model pixel."""
    W, H = 30 * px + 8, 47 * px + 8
    img = [0] * (W * H) if bg is None else [bg] * (W * H)
    zbuf = [1e9] * (W * H)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (W / 2 + x * px, 13 * px + 4 + y * px, z)  # head top at y -8; buns, guan and caping reach -13

    def draw(part, M, T):
        m = _mm(M, _rot_matrix(*part.rot))
        t = tuple(T[i] + _mv(M, part.pivot)[i] for i in range(3))
        ms = tuple(tuple(m[i][j] * part.scale[j] for j in range(3)) for i in range(3))
        for box in part.boxes:
            for verts, uvs, shade in _faces(*box):
                wv = [tuple(t[i] + _mv(ms, vtx)[i] for i in range(3)) for vtx in verts]
                _poly(wv, uvs, shade)
        for c in part.children:
            draw(c, m, t)

    def _poly(wv, uvs, shade):
        us = [q[0] for q in uvs]
        vs = [q[1] for q in uvs]
        umin, umax, vmin, vmax = min(us), max(us), min(vs), max(vs)
        if umax - umin < 1e-6 or vmax - vmin < 1e-6:
            return
        # corners in (a, b) space: find which vertex sits at (umin, vmin) etc. -> bilinear
        def at(a, b):
            uu = umin + a * (umax - umin)
            vv = vmin + b * (vmax - vmin)
            # weights from the rectangle corners
            res = [0.0, 0.0, 0.0]
            for (cu, cv), p in zip(uvs, wv):
                wa = (1 - abs(uu - cu) / (umax - umin))
                wb = (1 - abs(vv - cv) / (vmax - vmin))
                for i in range(3):
                    res[i] += p[i] * wa * wb
            return res
        for ty in range(int(math.floor(vmin)), int(math.ceil(vmax))):
            for tx in range(int(math.floor(umin)), int(math.ceil(umax))):
                if not (0 <= tx < tex and 0 <= ty < tex):
                    continue
                c = atlas[ty * tex + tx]
                if (c >> 24) & 255 < 128:
                    continue
                a0 = max(0.0, (tx - umin) / (umax - umin))
                a1 = min(1.0, (tx + 1 - umin) / (umax - umin))
                b0 = max(0.0, (ty - vmin) / (vmax - vmin))
                b1 = min(1.0, (ty + 1 - vmin) / (vmax - vmin))
                q = [view(at(a0, b0)), view(at(a1, b0)), view(at(a1, b1)), view(at(a0, b1))]
                col = (int(((c >> 16) & 255) * shade) << 16) | (int(((c >> 8) & 255) * shade) << 8) | int((c & 255) * shade)
                _fill(q, col)

    def _fill(q, col):
        xs = [p[0] for p in q]
        ys = [p[1] for p in q]
        z = sum(p[2] for p in q) / 4
        x0, x1 = max(0, int(math.floor(min(xs)))), min(W - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(H - 1, int(math.ceil(max(ys))))
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                if _inside(q, xx + 0.5, yy + 0.5):
                    i = yy * W + xx
                    if z < zbuf[i]:
                        zbuf[i] = z
                        img[i] = 0xFF000000 | col

    I3 = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    for part in parts.values():
        draw(part, I3, (0, 0, 0))
    return W, H, img


def _inside(q, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = q[i][0], q[i][1]
        x2, y2 = q[(i + 1) % 4][0], q[(i + 1) % 4][1]
        c = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(c) < 1e-9:
            continue
        s = 1 if c > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return True


def render_look(atlas, look, yaw=-28, pitch=14, px=6):
    parts = player(look["ramping"])
    attach_cubes(parts, look, look["ramping"])
    return render(atlas, parts, yaw, pitch, px)

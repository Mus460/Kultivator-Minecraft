#!/usr/bin/env python3
"""DESIGN 38.S (module 38ST): story content generator.

One source for the data and the texts so they never drift:
  data/kultivator/tempat/dialog/<id>.json   dialogs (DialogNode format of dev.kultivator.dunia.tempat.cerita)
  data/kultivator/tempat/quest/<id>.json    quest chains
  data/kultivator/tempat/kabar/<file>.json  rumour templates
  lang-src/tempat_cerita.{id_id,en_us}.json  engine strings, characters, dialogs, quests, items, arc, rumours
  lang-src/tempat_lore.{id_id,en_us}.json    place lore (38.S.7) and the Sumber lines
  lang-src/tempat_kalimat.{id_id,en_us}.json speech-bubble lines (38.S.6)
  lang-src/tempat_panduan.{id_id,en_us}.json guide chapters 18 and 19 (38.D.11)
  src/main/java/dev/kultivator/dunia/tempat/cerita/KalimatIndeks.java (lines per group.situation)
  src/main/java/dev/kultivator/dunia/tempat/cerita/LoreIndeks.java (lore entries: paragraphs, table, Sumber, filler)

Content lives in tools/cerita_isi_*.py; this file holds the small DSL, the writers and the validation (every node
target exists, every condition / effect / goal id is a built-in one, items, clips, props and voice keys exist, id and en
have the same keys and the same number of %s). Indonesian is primary. Run: python tools/gen_tempat_cerita.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)

DATA = os.path.join(REPO, 'src/main/resources/data/kultivator/tempat')
JAVA = os.path.join(REPO, 'src/main/java/dev/kultivator')
LANG = os.path.join(REPO, 'lang-src')

# ------------------------------------------------------------------------------------------------ lang fragments
FRAG = {name: ({}, {}) for name in ('tempat_cerita', 'tempat_lore', 'tempat_kalimat', 'tempat_panduan')}
ERRORS = []


def tx(frag, key, pair):
    """Adds one text (id, en) under key to a fragment."""
    if not isinstance(pair, (tuple, list)) or len(pair) != 2:
        ERRORS.append(f'{key}: text must be (id, en), got {pair!r}')
        return key
    ids, ens = FRAG[frag]
    if key in ids:
        ERRORS.append(f'duplicate lang key {key}')
    ids[key] = pair[0]
    ens[key] = pair[1]
    return key


# ------------------------------------------------------------------------------------------------ DSL
class N:
    """Dialog node: N(id, (id_text, en_text), gestur=, prop=, suara=, akibat=[], pilihan=[], isian=[], kabar=False)."""

    def __init__(self, id, t=None, gestur='', prop='', suara='', akibat=(), pilihan=(), isian=(), kabar=False):
        self.id, self.t, self.gestur, self.prop, self.suara = id, t, gestur, prop, suara
        self.akibat, self.pilihan, self.isian, self.kabar = list(akibat), list(pilihan), list(isian), kabar


class P:
    """Choice: P((id_text, en_text), ke=None, syarat=[], akibat=[], tampil=False, alasan=None)."""

    def __init__(self, t, ke=None, syarat=(), akibat=(), tampil=False, alasan=None):
        self.t, self.ke, self.syarat, self.akibat, self.tampil, self.alasan = t, ke, list(syarat), list(akibat), tampil, alasan


DIALOGS = {}
QUESTS = {}
KABAR = {}
ITEMS = {}


def D(id, pembicara, simpul, prioritas=0, syarat=(), sekali=False):
    if id in DIALOGS:
        ERRORS.append(f'duplicate dialog {id}')
    DIALOGS[id] = dict(pembicara=list(pembicara) if isinstance(pembicara, (list, tuple)) else [pembicara], simpul=simpul,
                       prioritas=prioritas, syarat=list(syarat), sekali=sekali)


class T:
    """Quest stage: T(id, (id_text, en_text), tujuan=[], akibat=[], akibat_masuk=[], cabang=[(syarat[], ke)], ke=None,
    batas_hari=0, gagal_ke=None, arah='', hasil=(id, en) ending text when the quest ends after this stage)."""

    def __init__(self, id, t, tujuan=(), akibat=(), akibat_masuk=(), cabang=(), ke=None, batas_hari=0, gagal_ke=None, arah='',
                 hasil=None):
        self.id, self.t, self.tujuan, self.akibat, self.akibat_masuk = id, t, list(tujuan), list(akibat), list(akibat_masuk)
        self.cabang, self.ke, self.batas_hari, self.gagal_ke, self.arah, self.hasil = list(cabang), ke, batas_hari, gagal_ke, arah, hasil


def Q(id, pemberi, tempat, nama, ringkas, tahap, ulang=False, alur=False, hasil_gagal=None):
    if id in QUESTS:
        ERRORS.append(f'duplicate quest {id}')
    QUESTS[id] = dict(pemberi=pemberi, tempat=tempat, nama=nama, ringkas=ringkas, tahap=tahap, ulang=ulang, alur=alur,
                      hasil_gagal=hasil_gagal)


LORE = {}
# Entries whose texts live in other fragments (theme agents' tempat_tema_<tema>.*): index rows only, no lang written here.
LORE_LUAR = {
    'kota_tema.iblis': dict(paragraf=3, tabel=0, sumber=True, isian=''),
    'kota_tema.oasis': dict(paragraf=3, tabel=0, sumber=True, isian=''),
}
LORE_ISIAN = {'', 'sekte_situs', 'sekte_terdekat', 'sekte_iblis', 'sekte_lurus'}


def L(id, judul, paragraf, tabel=(), sumber=None, isian=''):
    """Place lore (38.S.7): title, 2-4 paragraphs, an optional table (one row chosen per site, shown before the paragraphs),
    an optional Sumber line and one filler for every %s (LoreTempat: sekte_situs, sekte_terdekat, sekte_iblis, sekte_lurus)."""
    if id in LORE:
        ERRORS.append(f'duplicate lore {id}')
    if isian not in LORE_ISIAN:
        ERRORS.append(f'lore {id}: unknown filler {isian}')
    if not 1 <= len(paragraf) <= 4:
        ERRORS.append(f'lore {id}: needs 1-4 paragraphs, has {len(paragraf)}')
    base = f'kultivator.lore.{id}'
    tx('tempat_lore', base + '.judul', judul)
    texts = []
    for n, p in enumerate(paragraf, 1):
        tx('tempat_lore', f'{base}.{n}', p)
        texts.append(p)
    for n, p in enumerate(tabel):
        tx('tempat_lore', f'{base}.tabel.{n}', p)
        texts.append(p)
    if sumber is not None:
        tx('tempat_lore', base + '.sumber', sumber)
        if not sumber[0].startswith('Sumber:') or not sumber[1].startswith('Source:'):
            ERRORS.append(f'lore {id}: Sumber line must start with "Sumber:" / "Source:"')
    for p in texts:
        n = p[0].count('%s') if isinstance(p, (tuple, list)) else 0
        if n > 1 or (n == 1 and not isian):
            ERRORS.append(f'lore {id}: {n} %s but filler "{isian}"')
    LORE[id] = dict(paragraf=len(paragraf), tabel=len(tabel), sumber=sumber is not None, isian=isian)


def K(file, id, pemicu, t, bobot=10, umur=3, penyebar=(), isian=()):
    if id in KABAR:
        ERRORS.append(f'duplicate rumour {id}')
    KABAR[id] = dict(file=file, pemicu=pemicu, t=t, bobot=bobot, umur=umur, penyebar=list(penyebar), isian=list(isian))


# ------------------------------------------------------------------------------------------------ known ids
SYARAT = {'nama_baik', 'pangkat', 'ranah', 'pita', 'bendera', 'punya', 'nb', 'nb_pita', 'hari', 'quest', 'jam', 'sikap_sekte',
          'aliran_terdekat', 'takut', 'buronan', 'acak', 'watak', 'situs_dikenal', 'alur', 'alur_dukung', 'alur_bab5', 'alur_penguasa',
          'alur_dou', 'bukti', 'makam'}
AKIBAT = {'bendera', 'nama_baik', 'beri', 'ambil', 'bayar', 'bayar_pita', 'beri_nb', 'beri_nb_pita', 'donasi', 'quest_mulai', 'quest_maju',
          'quest_gagal', 'peristiwa', 'buka_layanan', 'mulai_duel', 'klip', 'suara', 'adegan', 'kabar', 'alur', 'sikap_sekte', 'panggil',
          'kawal_mulai', 'jadi_tokoh', 'npc_pergi', 'gelar', 'pesan', 'efek', 'tutup', 'panggil_peran', 'lore'}
TUJUAN = {'pergi', 'bicara', 'kalahkan', 'antar', 'kawal', 'duel', 'racik', 'tempa', 'donasi', 'bebaskan', 'tunggu', 'tanda',
          'misi_gilda', 'ujian'}
# goal kinds other modules register (38TK themes; kept in the validation so the data may use them)
TUJUAN_LAIN = set()
# effects other modules register through Akibat.daftar (38DS: clan and sect bouts)
AKIBAT |= {'tanding_klan', 'tanding_sekte'}
BARANG = set()
TOKOH = set()
GELAR = {}


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def java_list(src, pattern):
    return set(re.findall(pattern, src))


def load_known():
    cerita = os.path.join(JAVA, 'dunia/tempat/cerita')
    global BARANG, TOKOH
    BARANG = java_list(read(os.path.join(cerita, 'BarangCerita.java')), r'(?:biasa|khusus)\("(\w+)"') | {'pedang_tanpa_nama'}
    TOKOH = java_list(read(os.path.join(cerita, 'Tokoh.java')), r'add\("(\w+)", NpcRole')
    gerak = java_list(read(os.path.join(JAVA, 'sosok/GerakId.java')), r'put\(\w+, "(\w+)"')
    prop = java_list(read(os.path.join(JAVA, 'sosok/PropId.java')), r'put\(\w+, "(\w+)"')
    roles = {r.lower() for r in re.findall(r'^\s+([A-Z_]+)\(', read(os.path.join(JAVA, 'dunia/kota/NpcRole.java')), re.M)}
    voices = set()
    gv = read(os.path.join(HERE, 'gen_voices.py'))
    for k in re.findall(r"^\s+\('([a-z_.]+)', '(?:m1|m2|m3|f1|f2|f3|elder|boss|demon|heaven|heaven_fierce)'", gv, re.M):
        voices.add(k)
    for line in ('greet', 'greet_alt', 'thanks', 'cheer', 'careful', 'farewell'):
        for v in ('m1', 'm2', 'm3', 'f1', 'f2', 'f3'):
            voices.add(f'disciple.{v}.{line}')
    voices |= {'elder.welcome', 'elder.advice', 'elder.inheritance', 'boss.taunt', 'demon.laugh', 'rogue.ambush'}
    return gerak, prop, roles, voices


# ------------------------------------------------------------------------------------------------ writers
def words(line):
    line = line.strip()
    if line.startswith('!'):
        line = line[1:].strip()
    return line.split()


def check_lines(where, lines, known, kind):
    for line in lines:
        w = words(line)
        if not w or w[0] not in known:
            ERRORS.append(f'{where}: unknown {kind} "{line}"')
            continue
        check_args(where, w)


def check_args(where, w):
    head = w[0]
    if head in ('beri', 'ambil', 'punya') and len(w) > 1 and w[1].startswith('cerita:') and w[1][7:] not in BARANG:
        ERRORS.append(f'{where}: unknown story item {w[1]}')
    if head in ('quest_mulai', 'quest_maju', 'quest_gagal', 'quest') and len(w) > 1 and w[1] not in QUESTS:
        ERRORS.append(f'{where}: unknown quest {w[1]}')
    if head in ('panggil', 'jadi_tokoh') and len(w) > 1 and w[1] not in TOKOH:
        ERRORS.append(f'{where}: unknown character {w[1]}')
    if head == 'gelar' and len(w) > 1 and w[1] not in GELAR:
        ERRORS.append(f'{where}: title {w[1]} has no text (add it to GELAR)')
    if head == 'lore' and len(w) > 1 and w[1] not in LORE:
        ERRORS.append(f'{where}: unknown lore {w[1]}')
    if head == 'kabar' and len(w) > 1 and w[1] not in KABAR:
        ERRORS.append(f'{where}: unknown rumour template {w[1]}')


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def build_dialogs(gerak, prop, roles, voices):
    out = {}
    for did, d in DIALOGS.items():
        for who in d['pembicara']:
            if who.startswith('tokoh:') and who[6:] not in TOKOH:
                ERRORS.append(f'dialog {did}: unknown speaker {who}')
            elif who.startswith('peran:') and who[6:] not in roles:
                ERRORS.append(f'dialog {did}: unknown role {who}')
            elif not who.startswith(('tokoh:', 'peran:')):
                ERRORS.append(f'dialog {did}: bad speaker {who}')
        check_lines(f'dialog {did}', d['syarat'], SYARAT, 'condition')
        ids = {n.id for n in d['simpul']}
        nodes = []
        for n in d['simpul']:
            node = {'id': n.id}
            if n.kabar:
                node['teks'] = 'kabar'
            else:
                tx('tempat_cerita', f'kultivator.cerita.{did}.{n.id}', n.t)
            if n.isian:
                node['isian'] = n.isian
            if n.gestur:
                if n.gestur not in gerak:
                    ERRORS.append(f'dialog {did}.{n.id}: unknown clip {n.gestur}')
                node['gestur'] = n.gestur
            if n.prop:
                if n.prop not in prop:
                    ERRORS.append(f'dialog {did}.{n.id}: unknown prop {n.prop}')
                node['prop'] = n.prop
            if n.suara:
                if n.suara not in voices:
                    ERRORS.append(f'dialog {did}.{n.id}: unknown voice {n.suara}')
                node['suara'] = n.suara
            if n.akibat:
                check_lines(f'dialog {did}.{n.id}', n.akibat, AKIBAT, 'effect')
                node['akibat'] = n.akibat
            choices = []
            for i, c in enumerate(n.pilihan, 1):
                tx('tempat_cerita', f'kultivator.cerita.{did}.{n.id}.{i}', c.t)
                ch = {}
                if c.syarat:
                    check_lines(f'dialog {did}.{n.id}.{i}', c.syarat, SYARAT, 'condition')
                    ch['syarat'] = c.syarat
                if c.akibat:
                    check_lines(f'dialog {did}.{n.id}.{i}', c.akibat, AKIBAT, 'effect')
                    ch['akibat'] = c.akibat
                if c.ke is not None:
                    if c.ke not in ids:
                        ERRORS.append(f'dialog {did}.{n.id}.{i}: unknown node {c.ke}')
                    ch['ke'] = c.ke
                if c.tampil:
                    ch['tampil'] = True
                if c.alasan:
                    key = f'kultivator.cerita.{did}.{n.id}.{i}.alasan'
                    tx('tempat_cerita', key, c.alasan)
                    ch['alasan'] = key
                choices.append(ch)
            if choices:
                node['pilihan'] = choices
            nodes.append(node)
        obj = {'pembicara': d['pembicara']}
        if d['prioritas']:
            obj['prioritas'] = d['prioritas']
        if d['syarat']:
            obj['syarat'] = d['syarat']
        if d['sekali']:
            obj['sekali'] = True
        obj['mulai'] = d['simpul'][0].id
        obj['simpul'] = nodes
        out[did] = obj
    return out


def build_quests():
    out = {}
    for qid, q in QUESTS.items():
        tx('tempat_cerita', f'kultivator.cerita.quest.{qid}.nama', q['nama'])
        tx('tempat_cerita', f'kultivator.cerita.quest.{qid}.ringkas', q['ringkas'])
        stage_ids = [t.id for t in q['tahap']]
        stages = []
        ends = set()
        for i, t in enumerate(q['tahap']):
            if t.id in ('nama', 'ringkas', 'hasil', 'selesai', 'gagal'):
                ERRORS.append(f'quest {qid}: stage id {t.id} is reserved')
            tx('tempat_cerita', f'kultivator.cerita.quest.{qid}.{t.id}', t.t)
            s = {'id': t.id}
            for line in t.tujuan:
                w = words(line)
                if w and w[0] not in TUJUAN and w[0] not in SYARAT and w[0] not in TUJUAN_LAIN:
                    ERRORS.append(f'quest {qid}.{t.id}: unknown goal "{line}"')
                elif w:
                    check_args(f'quest {qid}.{t.id}', w)
            if t.tujuan:
                s['tujuan'] = t.tujuan
            for field, lines in (('akibat_masuk', t.akibat_masuk), ('akibat', t.akibat)):
                if lines:
                    check_lines(f'quest {qid}.{t.id}', lines, AKIBAT, 'effect')
                    s[field] = lines
            if t.cabang:
                cab = []
                for syarat, ke in t.cabang:
                    check_lines(f'quest {qid}.{t.id} branch', syarat, SYARAT, 'condition')
                    if ke not in stage_ids and ke not in ('selesai', 'gagal'):
                        ERRORS.append(f'quest {qid}.{t.id}: branch to unknown stage {ke}')
                    if ke in ('selesai', 'gagal'):
                        ends.add(t.id)
                    cab.append({'syarat': syarat, 'ke': ke})
                s['cabang'] = cab
            nxt = t.ke if t.ke is not None else (stage_ids[i + 1] if i + 1 < len(stage_ids) else 'selesai')
            if nxt not in stage_ids and nxt not in ('selesai', 'gagal'):
                ERRORS.append(f'quest {qid}.{t.id}: next unknown stage {nxt}')
            if nxt in ('selesai', 'gagal'):
                ends.add(t.id)
            if t.ke is not None:
                s['ke'] = t.ke
            if t.batas_hari:
                s['batas_hari'] = t.batas_hari
                g = t.gagal_ke or 'gagal'
                if g not in stage_ids and g not in ('selesai', 'gagal'):
                    ERRORS.append(f'quest {qid}.{t.id}: deadline to unknown stage {g}')
                if g in ('selesai', 'gagal'):
                    ends.add(t.id)
                if t.gagal_ke:
                    s['gagal_ke'] = t.gagal_ke
            if t.arah:
                s['arah'] = t.arah
            stages.append(s)
        # every stage after which the quest can end needs an ending text
        for t in q['tahap']:
            if t.id in ends:
                if t.hasil is None:
                    ERRORS.append(f'quest {qid}.{t.id}: can end here but has no hasil text')
                else:
                    tx('tempat_cerita', f'kultivator.cerita.quest.{qid}.hasil.{t.id}', t.hasil)
            elif t.hasil is not None:
                tx('tempat_cerita', f'kultivator.cerita.quest.{qid}.hasil.{t.id}', t.hasil)
        obj = {'pemberi': q['pemberi'], 'tempat': q['tempat']}
        if q['ulang']:
            obj['ulang'] = True
        if q['alur']:
            obj['alur'] = True
        obj['tahap'] = stages
        out[qid] = obj
    return out


def build_kabar(roles=None):
    files = {}
    for kid, k in KABAR.items():
        for r in k['penyebar']:
            if roles is not None and r not in roles:
                ERRORS.append(f'rumour {kid}: unknown spreader role {r}')
        tx('tempat_cerita', f'kultivator.cerita.kabar.{kid}', k['t'])
        t = {'id': kid, 'pemicu': k['pemicu'], 'bobot': k['bobot'], 'umur_hari': k['umur']}
        if k['penyebar']:
            t['peran_penyebar'] = k['penyebar']
        if k['isian']:
            t['isian'] = k['isian']
        files.setdefault(k['file'], []).append(t)
    return files


def check_parity():
    for name, (ids, ens) in FRAG.items():
        if set(ids) != set(ens):
            ERRORS.append(f'{name}: id/en keys differ')
        for key in ids:
            a = len(re.findall(r'%(?:\d+\$)?s', ids[key]))
            b = len(re.findall(r'%(?:\d+\$)?s', ens[key]))
            if a != b:
                ERRORS.append(f'{name} {key}: %s count id {a} en {b}')
            for txt in (ids[key], ens[key]):
                if not txt.strip():
                    ERRORS.append(f'{name} {key}: empty text')
                if re.search(r'%(?![\ds%])', txt.replace('%%', '').replace('%s', '')):
                    ERRORS.append(f'{name} {key}: stray % in "{txt[:40]}"')


def check_other_fragments():
    """No key of ours may live in another fragment (mergeLang fails on duplicates)."""
    ours = set()
    for ids, _ in FRAG.values():
        ours |= set(ids)
    for fn in os.listdir(LANG):
        if not fn.endswith('.id_id.json') or fn.split('.')[0] in FRAG:
            continue
        other = json.load(open(os.path.join(LANG, fn), encoding='utf-8'))
        dup = ours & set(other)
        if dup:
            ERRORS.append(f'{fn} already has {sorted(dup)[:5]}')


def write_kalimat_index(index):
    lines = ['package dev.kultivator.dunia.tempat.cerita;', '', 'import java.util.Map;', '', '/**',
             ' * Lines per {@code <grup>.<situasi>} of {@link Kalimat} (fragment {@code tempat_kalimat}). Generated by',
             ' * {@code tools/gen_tempat_cerita.py} together with the lang fragment; do not edit by hand.', ' */',
             'final class KalimatIndeks {', '', '\tstatic final Map<String, Integer> JUMLAH = Map.ofEntries(']
    entries = [f'\t\t\tMap.entry("{k}", {v})' for k, v in sorted(index.items())]
    lines.append(',\n'.join(entries) + ');')
    lines += ['', '\tprivate KalimatIndeks() {', '\t}', '}', '']
    with open(os.path.join(JAVA, 'dunia/tempat/cerita/KalimatIndeks.java'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines))


def write_lore_index():
    lines = ['package dev.kultivator.dunia.tempat.cerita;', '', 'import java.util.Map;', '', '/**',
             ' * Place lore entries of {@link LoreTempat} (fragment {@code tempat_lore}, keys',
             ' * {@code kultivator.lore.<id>.judul|<n>|tabel.<n>|sumber}). Generated by {@code tools/gen_tempat_cerita.py}',
             ' * together with the lang fragment; do not edit by hand.', ' */', 'final class LoreIndeks {', '',
             '\t/** One entry: paragraph count (1-4), table rows (0 = none), whether it has a Sumber line, the %s filler ("" none). */',
             '\trecord Entri(int paragraf, int tabel, boolean sumber, String isian) {', '\t}', '',
             '\tstatic final Map<String, Entri> ENTRI = Map.ofEntries(']
    entries = [f'\t\t\tMap.entry("{k}", new Entri({v["paragraf"]}, {v["tabel"]}, {str(v["sumber"]).lower()}, "{v["isian"]}"))'
               for k, v in sorted({**LORE, **LORE_LUAR}.items())]
    lines.append(',\n'.join(entries) + ');')
    lines += ['', '\tprivate LoreIndeks() {', '\t}', '}', '']
    with open(os.path.join(JAVA, 'dunia/tempat/cerita/LoreIndeks.java'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines))


def main():
    gerak, prop, roles, voices = load_known()
    import cerita_isi_umum
    import cerita_isi_tokoh
    import cerita_isi_takhta
    import cerita_isi_quest
    import cerita_isi_watak
    import cerita_isi_warisan
    import cerita_isi_kabar
    import cerita_isi_kalimat
    import cerita_isi_lore
    import cerita_isi_panduan
    import cerita_isi_detail
    import cerita_isi_detail_dialog
    cerita_isi_lore.isi(sys.modules[__name__])
    for mod in (cerita_isi_umum, cerita_isi_tokoh, cerita_isi_takhta, cerita_isi_quest, cerita_isi_watak, cerita_isi_warisan,
                cerita_isi_kabar, cerita_isi_detail, cerita_isi_detail_dialog):
        mod.isi(sys.modules[__name__])
    for gid, pair in GELAR.items():
        tx('tempat_cerita', f'kultivator.cerita.gelar.{gid}', pair)
    dialogs = build_dialogs(gerak, prop, roles, voices)
    quests = build_quests()
    kabar = build_kabar(roles)
    index = cerita_isi_kalimat.isi(sys.modules[__name__])
    cerita_isi_panduan.isi(sys.modules[__name__])
    for item in sorted(BARANG):
        if f'kultivator.cerita.barang.{item}' not in FRAG['tempat_cerita'][0]:
            ERRORS.append(f'story item {item} has no name text')
    check_parity()
    check_other_fragments()
    if ERRORS:
        for e in ERRORS:
            print('ERROR', e)
        print(len(ERRORS), 'errors; nothing written')
        sys.exit(1)
    for sub in ('dialog', 'quest', 'kabar'):
        folder = os.path.join(DATA, sub)
        if os.path.isdir(folder):
            for fn in os.listdir(folder):
                # tema_* belong to the 38TK theme generators, mandiri* to 39's gen_mandiri_lang.py: never ours to delete
                if fn.endswith('.json') and not fn.startswith(('tema_', 'mandiri')):
                    os.remove(os.path.join(folder, fn))
    for did, obj in dialogs.items():
        write_json(os.path.join(DATA, 'dialog', did + '.json'), obj)
    for qid, obj in quests.items():
        write_json(os.path.join(DATA, 'quest', qid + '.json'), obj)
    for fn, templates in kabar.items():
        write_json(os.path.join(DATA, 'kabar', fn + '.json'), {'templat': templates})
    for name, (ids, ens) in FRAG.items():
        write_json(os.path.join(LANG, f'{name}.id_id.json'), dict(sorted(ids.items())))
        write_json(os.path.join(LANG, f'{name}.en_us.json'), dict(sorted(ens.items())))
    write_kalimat_index(index)
    write_lore_index()
    print(f'dialogs {len(dialogs)}, quests {len(quests)}, rumours {len(kabar_count(kabar))}, kalimat {sum(index.values())}, '
          f'lore {len(LORE)}, '
          + ', '.join(f'{n} {len(ids)}' for n, (ids, _) in FRAG.items()))


def kabar_count(files):
    return [t for ts in files.values() for t in ts]


if __name__ == '__main__':
    main()

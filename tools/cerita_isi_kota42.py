#!/usr/bin/env python3
"""DESIGN 42.10 + the data of 42.5 (module 42B-C): the named characters, their dialogs and story arcs of Kota yang Hidup,
the twelve tales of Lao Shuo, the personal requests ("Tolong"), the gift tastes, the court-case templates, the shadow-play
scripts, the rumours and the direction words of "Tanya Jalan". One source for the data and the texts so they never drift:

  data/kultivator/tempat/tokoh/kota42.json        named characters (read by 42B TokohKota; registered into 38 Tokoh)
  data/kultivator/tempat/dialog/kota42_*.json     dialogs (38 DialogNode format, dev.kultivator.dunia.tempat.cerita)
  data/kultivator/tempat/quest/kota42_*.json      quest chains (38 Quest format; 42B goal kinds listed in TUJUAN_42)
  data/kultivator/tempat/kabar/kota42.json        rumour templates (38 KabarTempat format)
  data/kultivator/tempat/tolong/<jenis>.json      personal requests of residents (42.5.6, read by 42B PermintaanWarga)
  data/kultivator/tempat/selera.json              gift tastes (42.5.7, read by 42B Selera)
  data/kultivator/tempat/perkara/<id>.json        court cases (42.6.6, format of 42C-2's Perkara; the three of 42C-2 stay)
  data/kultivator/tempat/wayang/<naskah>.json     shadow-play scripts (42.9.6; acts = LayarBayangRenderer.BABAK_NASKAH)
  data/kultivator/tempat/bicara/kisah_lao_shuo.json   index of the twelve tales (dialog node keys)
  data/kultivator/tempat/bicara/jalan.json        words of "Tanya Jalan" (42.5.5): compass, distance bands, landmarks
  lang-src/kota_hidup_cerita.{id_id,en_us}.json   every text above (identical keys; Indonesian primary)

Validation (nothing is written on any error): every dialog/quest target exists, every condition / effect / goal head is a
38 built-in or a 42B kind of TUJUAN_42 / AKIBAT_42, roles, clips (GerakId names or k:<KotaGerakId name>), props, voice keys
(gen_voices.py or the kota42 list of docs/progres/m2-voices.md), item ids, habits, temperaments, %s parity id/en, no key
of ours in another fragment. Run: python -I tools/cerita_isi_kota42.py [repo]   (deterministic; LF output)
"""
import json
import os
import re
import sys

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, 'src/main/resources/data/kultivator/tempat')
JAVA = os.path.join(REPO, 'src/main/java/dev/kultivator')
LANG = os.path.join(REPO, 'lang-src')
FRAG = 'kota_hidup_cerita'
L = {}
ERRORS = []


def t(key, pair):
    """Adds one text (id, en) under key; returns the key."""
    if not isinstance(pair, (tuple, list)) or len(pair) != 2:
        ERRORS.append(f'{key}: text must be (id, en), got {pair!r}')
        return key
    if key in L and L[key] != tuple(pair):
        ERRORS.append(f'duplicate lang key {key}')
    L[key] = tuple(pair)
    return key


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


# ================================================================================================ known ids (code is truth)
def _known():
    roles = {r.lower() for r in re.findall(r'^\s+([A-Z_]+)\(', read(os.path.join(JAVA, 'dunia/kota/NpcRole.java')), re.M)}
    gerak = set(re.findall(r'put\(\w+, "(\w+)"', read(os.path.join(JAVA, 'sosok/GerakId.java'))))
    prop = set(re.findall(r'put\(\w+, "(\w+)"', read(os.path.join(JAVA, 'sosok/PropId.java'))))
    kg = read(os.path.join(JAVA, 'dunia/kota42/api/KotaGerakId.java'))
    klip42, prop42 = {}, {}
    for name, num in re.findall(r'public static final int ([A-Z_0-9]+) = (\d+);', kg):
        n = int(num)
        if name.startswith('PROP_') and 170 <= n <= 239:
            prop42[name[5:].lower()] = n
        elif 350 <= n <= 459 and not name.startswith(('KLIP_', 'V_')):
            klip42[name.lower()] = n
    voices = set()
    gv = read(os.path.join(REPO, 'tools/gen_voices.py'))
    voices |= set(re.findall(r"^\s+\('([a-z_.0-9]+)', '(?:m1|m2|m3|f1|f2|f3|elder|boss|demon|heaven|heaven_fierce)'", gv, re.M))
    voices |= set(re.findall(r'^\| (kota42\.[a-z_.0-9]+) \|', read(os.path.join(REPO, 'docs/progres/m2-voices.md')), re.M))
    items = set()
    for root, _, files in os.walk(JAVA):
        for fn in files:
            if fn.endswith('.java'):
                src = read(os.path.join(root, fn))
                items |= {'kultivator:' + x for x in re.findall(r'ModItems\.register\("([a-z0-9_/]+)"', src)}
                if fn == 'KotaAcaraItems.java':
                    items |= {'kultivator:wayang_kulit_' + x for x in re.findall(r'wayang\("(\w+)"\)', src)}
    cerita = set(re.findall(r'(?:biasa|khusus)\("(\w+)"', read(os.path.join(JAVA, 'dunia/tempat/cerita/BarangCerita.java'))))
    items |= {'cerita:' + x for x in cerita}
    tokoh38 = set(re.findall(r'add\("(\w+)", NpcRole', read(os.path.join(JAVA, 'dunia/tempat/cerita/Tokoh.java'))))
    habits = {fn[:-5] for fn in os.listdir(os.path.join(DATA, 'kebiasaan')) if fn.endswith('.json')}
    return roles, gerak, prop, klip42, prop42, voices, items, tokoh38, habits


ROLES, GERAK, PROP, KLIP42, PROP42, VOICES, ITEMS, TOKOH38, HABITS = _known()
WATAK = ['ramah', 'curiga', 'serakah', 'bijak', 'penakut', 'sombong', 'jujur', 'licik']
KELOMPOK = ['pekerja', 'pedagang', 'penjaga', 'anak', 'tetua', 'kultivator', 'cendekia', 'rahib', 'pejabat', 'khusus']
TAHAP = ['bayi', 'anak', 'remaja', 'dewasa_muda', 'dewasa', 'tua', 'pensiun']
KENANGAN = ['DIBERI_HADIAH', 'DIBANTU', 'DISELAMATKAN', 'HADIR_NIKAH', 'HADIR_MAKAM', 'ANAK_DIREKRUT', 'DIPUKUL', 'DICURI',
            'RUMAH_RUSAK', 'UTANG_LUNAS', 'UTANG_MANGKIR', 'CERITA_ORANG_TUA']
MUSIM = ['semi', 'panas', 'gugur', 'dingin']
VANILLA_OK = re.compile(r'^#?minecraft:[a-z0-9_/]+$')

# 38 built-ins (SyaratDasar / AkibatDasar / QuestTracker.daftarDasar) and the kinds 42B registers (DESIGN 42.10 "tujuan baru")
SYARAT = {'nama_baik', 'pangkat', 'ranah', 'pita', 'bendera', 'punya', 'nb', 'nb_pita', 'hari', 'quest', 'jam', 'sikap_sekte',
          'aliran_terdekat', 'takut', 'buronan', 'acak', 'watak', 'situs_dikenal', 'alur', 'alur_dukung', 'alur_bab5', 'alur_penguasa',
          'alur_dou', 'bukti', 'makam'}
AKIBAT = {'bendera', 'nama_baik', 'beri', 'ambil', 'bayar', 'bayar_pita', 'beri_nb', 'beri_nb_pita', 'donasi', 'quest_mulai', 'quest_maju',
          'quest_gagal', 'peristiwa', 'buka_layanan', 'mulai_duel', 'klip', 'suara', 'adegan', 'kabar', 'alur', 'sikap_sekte', 'panggil',
          'kawal_mulai', 'jadi_tokoh', 'npc_pergi', 'gelar', 'pesan', 'efek', 'tutup', 'panggil_peran', 'lore'}
TUJUAN = {'pergi', 'bicara', 'kalahkan', 'antar', 'kawal', 'duel', 'racik', 'tempa', 'donasi', 'bebaskan', 'tunggu', 'tanda',
          'misi_gilda', 'ujian'}
# 42B (TujuanKota) registers these goal kinds; event ids are documented in docs/progres/m2-42B-C.md
TUJUAN_42 = {'bicara_warga', 'gosip_telusur', 'hadir_upacara', 'bayar_utang', 'perkara', 'kavling_tahap', 'kejar_copet', 'ronda_geng',
             'tolong', 'beri_warga'}
# 42B registers these effects (Akibat.daftar) for the arcs; documented in m2-42B-C.md
AKIBAT_42 = {'kota42.kesan', 'kota42.gosip', 'kota42.kebiasaan', 'kota42.nikah', 'kota42.pindah', 'kota42.calon', 'kota42.saham',
             'kota42.nama_jalan', 'kota42.harga', 'kota42.resep', 'kota42.pensiun', 'kota42.warisi', 'kota42.pakta',
             'kota42.jabatan', 'kota42.restu'}
AKIBAT |= AKIBAT_42 | {'infami'}   # infami N [sebab]: TemaCerita (38.T)
SYARAT |= {'infami', 'kota_wafat'}   # kota_wafat <tokoh>: that named character has died (hand-off to 42B)

BARANG_KOTA42 = ['kultivator:kentongan_tua', 'kultivator:kwitansi_sumur', 'kultivator:surat_utang', 'kultivator:resep_sup',
                 'kultivator:kue_pengantin', 'kultivator:sup_hangat', 'kultivator:bubur_laba', 'kultivator:telur_merah',
                 'kultivator:wayang_kulit_kera_sakti', 'kultivator:wayang_kulit_ular_putih', 'kultivator:wayang_kulit_hua_mulan',
                 'kultivator:wayang_kulit_hakim_bao', 'kultivator:topeng_pasar_hantu', 'kultivator:surat_kontrak']


def item_ok(where, iid):
    if iid.startswith('#'):
        if not VANILLA_OK.match(iid) and not re.match(r'^#kultivator:[a-z0-9_/]+$', iid):
            ERRORS.append(f'{where}: bad item tag {iid}')
        return
    if iid.startswith('minecraft:'):
        if not VANILLA_OK.match(iid):
            ERRORS.append(f'{where}: bad vanilla id {iid}')
        return
    if iid not in ITEMS:
        ERRORS.append(f'{where}: unknown item {iid}')


def klip(name):
    """Clip name for a node: a GerakId name stays, k:<name> becomes the KotaGerakId number."""
    if not name:
        return ''
    if name.startswith('k:'):
        if name[2:] not in KLIP42:
            ERRORS.append(f'unknown kota42 clip {name}')
            return ''
        return str(KLIP42[name[2:]])
    if name not in GERAK and 'sikap_' + name not in GERAK:
        ERRORS.append(f'unknown clip {name}')
    return name


def prop(name):
    if not name:
        return ''
    if name.startswith('k:'):
        if name[2:] not in PROP42:
            ERRORS.append(f'unknown kota42 prop {name}')
            return ''
        return str(PROP42[name[2:]])
    if name not in PROP:
        ERRORS.append(f'unknown prop {name}')
    return name


# ================================================================================================ DSL: dialogs, quests, rumours
class N:
    """Dialog node: N(id, (id, en), gestur='', prop='', suara='', akibat=[], pilihan=[], isian=[], kabar=False)."""

    def __init__(self, id, t=None, gestur='', prop='', suara='', akibat=(), pilihan=(), isian=(), kabar=False):
        self.id, self.t, self.gestur, self.prop, self.suara = id, t, gestur, prop, suara
        self.akibat, self.pilihan, self.isian, self.kabar = list(akibat), list(pilihan), list(isian), kabar


class P:
    """Choice: P((id, en), ke=None, syarat=[], akibat=[], alasan=None (shown locked with that reason))."""

    def __init__(self, t, ke=None, syarat=(), akibat=(), alasan=None):
        self.t, self.ke, self.syarat, self.akibat, self.alasan = t, ke, list(syarat), list(akibat), alasan


DIALOGS = {}
QUESTS = {}
KABAR = {}
TOKOH = {}


def D(id, pembicara, simpul, prioritas=0, syarat=(), sekali=False):
    if not id.startswith('kota42_'):
        ERRORS.append(f'dialog {id}: ids start with kota42_')
    if id in DIALOGS:
        ERRORS.append(f'duplicate dialog {id}')
    DIALOGS[id] = dict(pembicara=list(pembicara) if isinstance(pembicara, (list, tuple)) else [pembicara], simpul=simpul,
                       prioritas=prioritas, syarat=list(syarat), sekali=sekali)


class T:
    """Quest stage: T(id, (id, en), tujuan=[], akibat=[], akibat_masuk=[], cabang=[(syarat[], ke)], ke=None, batas_hari=0,
    gagal_ke=None, arah='', hasil=(id, en) ending text when the quest ends after this stage)."""

    def __init__(self, id, t, tujuan=(), akibat=(), akibat_masuk=(), cabang=(), ke=None, batas_hari=0, gagal_ke=None, arah='',
                 hasil=None):
        self.id, self.t, self.tujuan, self.akibat, self.akibat_masuk = id, t, list(tujuan), list(akibat), list(akibat_masuk)
        self.cabang, self.ke, self.batas_hari, self.gagal_ke, self.arah, self.hasil = list(cabang), ke, batas_hari, gagal_ke, arah, hasil


def Q(id, pemberi, tempat, nama, ringkas, tahap, ulang=False, alur=True):
    if not id.startswith('kota42_'):
        ERRORS.append(f'quest {id}: ids start with kota42_')
    if id in QUESTS:
        ERRORS.append(f'duplicate quest {id}')
    QUESTS[id] = dict(pemberi=pemberi, tempat=tempat, nama=nama, ringkas=ringkas, tahap=tahap, ulang=ulang, alur=alur)


def K(id, pemicu, teks, bobot=10, umur=4, penyebar=(), isian=()):
    if not id.startswith('kota42_'):
        ERRORS.append(f'rumour {id}: ids start with kota42_')
    if id in KABAR:
        ERRORS.append(f'duplicate rumour {id}')
    KABAR[id] = dict(pemicu=pemicu, t=teks, bobot=bobot, umur=umur, penyebar=list(penyebar), isian=list(isian))


def words(line):
    line = line.strip()
    if line.startswith('!'):
        line = line[1:].strip()
    return line.split()


def ref(where, r):
    if r.startswith('tokoh:'):
        if r[6:] not in TOKOH and r[6:] not in TOKOH38:
            ERRORS.append(f'{where}: unknown character {r}')
    elif r.startswith('peran:'):
        if r[6:] not in ROLES:
            ERRORS.append(f'{where}: unknown role {r}')
    elif r.startswith('tahap:'):
        if r[6:] not in ('anak', 'remaja', 'dewasa', 'tua'):
            ERRORS.append(f'{where}: unknown life stage {r}')
    elif not r.startswith(('kebiasaan:', '*')):
        ERRORS.append(f'{where}: bad reference {r}')


def check_args(where, w):
    head = w[0]
    if head in ('beri', 'ambil', 'punya') and len(w) > 1:
        item_ok(where, w[1] if ':' in w[1] else 'kultivator:' + w[1])
    if head in ('quest_mulai', 'quest_maju', 'quest_gagal', 'quest') and len(w) > 1 and w[1] not in QUESTS:
        ERRORS.append(f'{where}: unknown quest {w[1]}')
    if head == 'kabar' and len(w) > 1 and w[1] not in KABAR:
        ERRORS.append(f'{where}: unknown rumour template {w[1]}')
    if head == 'antar' and len(w) > 2:
        item_ok(where, w[1])
        ref(where, w[2])
    if head in ('bicara', 'duel', 'bicara_warga') and len(w) > 1:
        ref(where, w[1])
    if head == 'panggil_peran' and len(w) > 1 and w[1] not in ROLES:
        ERRORS.append(f'{where}: unknown role {w[1]}')
    if head == 'panggil' and len(w) > 1 and w[1] not in TOKOH and w[1] not in TOKOH38:
        ERRORS.append(f'{where}: unknown character {w[1]}')
    if head == 'klip' and len(w) > 1:
        klip(w[1])
    if head == 'suara' and len(w) > 1 and w[1] not in VOICES:
        ERRORS.append(f'{where}: unknown voice {w[1]}')
    if head == 'kota42.kebiasaan' and len(w) > 2 and w[2] not in HABITS:
        ERRORS.append(f'{where}: unknown habit {w[2]}')
    if head in ('kota42.kesan', 'kota42.kebiasaan', 'kota42.pindah', 'kota42.pensiun', 'kota42.warisi', 'kota42.ikut') and len(w) > 1:
        ref(where, w[1])
    if head == 'kota42.nikah' and len(w) > 2:
        ref(where, w[1])
        ref(where, w[2])


def check_lines(where, lines, known, kind):
    for line in lines:
        w = words(line)
        if not w or w[0] not in known:
            ERRORS.append(f'{where}: unknown {kind} "{line}"')
            continue
        check_args(where, w)


def build_dialogs():
    out = {}
    for did, d in DIALOGS.items():
        for who in d['pembicara']:
            ref(f'dialog {did}', who)
        check_lines(f'dialog {did}', d['syarat'], SYARAT, 'condition')
        ids = [n.id for n in d['simpul']]
        if len(set(ids)) != len(ids):
            ERRORS.append(f'dialog {did}: duplicate node ids')
        reached = {ids[0]}
        nodes = []
        for n in d['simpul']:
            node = {'id': n.id}
            if n.kabar:
                node['teks'] = 'kabar'
            else:
                t(f'kultivator.cerita.{did}.{n.id}', n.t)
            if n.isian:
                node['isian'] = n.isian
            g = klip(n.gestur)
            if g:
                node['gestur'] = g
            pr = prop(n.prop)
            if pr:
                node['prop'] = pr
            if n.suara:
                if n.suara not in VOICES:
                    ERRORS.append(f'dialog {did}.{n.id}: unknown voice {n.suara}')
                node['suara'] = n.suara
            if n.akibat:
                check_lines(f'dialog {did}.{n.id}', n.akibat, AKIBAT, 'effect')
                node['akibat'] = n.akibat
            choices = []
            for i, c in enumerate(n.pilihan, 1):
                t(f'kultivator.cerita.{did}.{n.id}.{i}', c.t)
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
                    reached.add(c.ke)
                    ch['ke'] = c.ke
                if c.alasan:
                    ch['tampil'] = True
                    ch['alasan'] = t(f'kultivator.cerita.{did}.{n.id}.{i}.alasan', c.alasan)
                choices.append(ch)
            if choices:
                node['pilihan'] = choices
            nodes.append(node)
        for n in ids:
            if n not in reached:
                ERRORS.append(f'dialog {did}: node {n} is never reached')
        obj = {'pembicara': d['pembicara']}
        if d['prioritas']:
            obj['prioritas'] = d['prioritas']
        if d['syarat']:
            obj['syarat'] = d['syarat']
        if d['sekali']:
            obj['sekali'] = True
        obj['mulai'] = ids[0]
        obj['simpul'] = nodes
        out[did] = obj
    return out


def build_quests():
    out = {}
    for qid, q in QUESTS.items():
        ref(f'quest {qid}', q['pemberi'])
        t(f'kultivator.cerita.quest.{qid}.nama', q['nama'])
        t(f'kultivator.cerita.quest.{qid}.ringkas', q['ringkas'])
        stage_ids = [s.id for s in q['tahap']]
        if len(set(stage_ids)) != len(stage_ids):
            ERRORS.append(f'quest {qid}: duplicate stage ids')
        stages, ends = [], set()
        for i, s in enumerate(q['tahap']):
            if s.id in ('nama', 'ringkas', 'hasil', 'selesai', 'gagal'):
                ERRORS.append(f'quest {qid}: stage id {s.id} is reserved')
            t(f'kultivator.cerita.quest.{qid}.{s.id}', s.t)
            o = {'id': s.id}
            for line in s.tujuan:
                w = words(line)
                if w and w[0] not in TUJUAN and w[0] not in SYARAT and w[0] not in TUJUAN_42:
                    ERRORS.append(f'quest {qid}.{s.id}: unknown goal "{line}"')
                elif w:
                    check_args(f'quest {qid}.{s.id}', w)
            if s.tujuan:
                o['tujuan'] = s.tujuan
            for field, lines in (('akibat_masuk', s.akibat_masuk), ('akibat', s.akibat)):
                if lines:
                    check_lines(f'quest {qid}.{s.id}', lines, AKIBAT, 'effect')
                    o[field] = lines
            if s.cabang:
                cab = []
                for syarat, ke in s.cabang:
                    check_lines(f'quest {qid}.{s.id} branch', syarat, SYARAT, 'condition')
                    if ke not in stage_ids and ke not in ('selesai', 'gagal'):
                        ERRORS.append(f'quest {qid}.{s.id}: branch to unknown stage {ke}')
                    if ke in ('selesai', 'gagal'):
                        ends.add(s.id)
                    cab.append({'syarat': syarat, 'ke': ke})
                o['cabang'] = cab
            nxt = s.ke if s.ke is not None else (stage_ids[i + 1] if i + 1 < len(stage_ids) else 'selesai')
            if nxt not in stage_ids and nxt not in ('selesai', 'gagal'):
                ERRORS.append(f'quest {qid}.{s.id}: next unknown stage {nxt}')
            if nxt in ('selesai', 'gagal'):
                ends.add(s.id)
            if s.ke is not None:
                o['ke'] = s.ke
            if s.batas_hari:
                o['batas_hari'] = s.batas_hari
                g = s.gagal_ke or 'gagal'
                if g not in stage_ids and g not in ('selesai', 'gagal'):
                    ERRORS.append(f'quest {qid}.{s.id}: deadline to unknown stage {g}')
                if g in ('selesai', 'gagal'):
                    ends.add(s.id)
                if s.gagal_ke:
                    o['gagal_ke'] = s.gagal_ke
            if s.arah:
                o['arah'] = s.arah
            stages.append(o)
        for s in q['tahap']:
            if s.id in ends and s.hasil is None:
                ERRORS.append(f'quest {qid}.{s.id}: can end here but has no hasil text')
            if s.hasil is not None:
                t(f'kultivator.cerita.quest.{qid}.hasil.{s.id}', s.hasil)
        obj = {'pemberi': q['pemberi'], 'tempat': q['tempat']}
        if q['ulang']:
            obj['ulang'] = True
        if q['alur']:
            obj['alur'] = True
        obj['tahap'] = stages
        out[qid] = obj
    return out


def build_kabar():
    out = []
    for kid, k in KABAR.items():
        for r in k['penyebar']:
            if r not in ROLES:
                ERRORS.append(f'rumour {kid}: unknown spreader role {r}')
        t(f'kultivator.cerita.kabar.{kid}', k['t'])
        o = {'id': kid, 'pemicu': k['pemicu'], 'bobot': k['bobot'], 'umur_hari': k['umur']}
        if k['penyebar']:
            o['peran_penyebar'] = k['penyebar']
        if k['isian']:
            o['isian'] = k['isian']
        out.append(o)
    return out


# ================================================================================================ named characters (42.10)
SUARA_TUBUH = {'m1': 0, 'm2': 1, 'm3': 2, 'f1': 3, 'f2': 4, 'f3': 5, 'elder': 1, 'demon': 2, 'kucing': 0}


def tokoh(id, han, peran, perempuan, umur, watak, suara, tempat, lokasi, nama, gelar, riwayat, abadi=True, kebiasaan=(),
          keluarga=(), jadwal='', hewan=False, rupa=''):
    """One named character of 42.10. nama/gelar/riwayat are (id, en). tempat: ibukota | keliling | kota34 | tema_air.
    lokasi: service lots (38 layanan) or 42 titik kinds, in order of preference, where TokohKota places the body.
    keluarga: [(hubungan, tokoh id)] the family links TokohKota writes into the Warga of the first capital."""
    if id in TOKOH or id in TOKOH38:
        ERRORS.append(f'duplicate character {id}')
    if not hewan and peran not in ROLES:
        ERRORS.append(f'character {id}: unknown role {peran}')
    if watak and watak not in WATAK:
        ERRORS.append(f'character {id}: unknown temperament {watak}')
    if suara not in SUARA_TUBUH:
        ERRORS.append(f'character {id}: unknown voice {suara}')
    for h in kebiasaan:
        if h not in HABITS:
            ERRORS.append(f'character {id}: unknown habit {h}')
    t(f'kultivator.cerita.tokoh.{id}', nama)
    t(f'kultivator.cerita.tokoh.{id}.gelar', gelar)
    t(f'kultivator.kota42.tokoh.{id}.riwayat', riwayat)
    TOKOH[id] = dict(id=id, nama_han=han, peran=peran, perempuan=perempuan, umur=umur, watak=watak, suara=suara,
                     suara_tubuh=SUARA_TUBUH[suara], abadi=abadi, tempat=tempat, lokasi=list(lokasi), kebiasaan=list(kebiasaan),
                     keluarga=[{'hubungan': h, 'tokoh': k} for h, k in keluarga], jadwal=jadwal, hewan=hewan, rupa=rupa)


def isi_tokoh():
    tokoh('lao_shuo', '老说', 'pendongeng', False, 70, 'bijak', 'elder', 'keliling', ['teh', 'panggung'],
          ('Lao Shuo', 'Lao Shuo'), ('Pendongeng Rombongan Bulan Sabit', 'Storyteller of the Crescent Moon Troupe'),
          ('Pendongeng tua yang konon sudah bercerita di seratus kota. Kipasnya robek di tiga tempat, suaranya tidak pernah. '
           'Ia tahu letak tempat-tempat yang tidak ada di peta, tapi hanya menyebutnya di tengah sebuah kisah.',
           'An old storyteller said to have told tales in a hundred towns. His fan is torn in three places, his voice never. '
           'He knows where places missing from every map lie, but only mentions them in the middle of a tale.'),
          kebiasaan=['pencerita_anak', 'pecinta_teh'], jadwal='rombongan')
    tokoh('ah_qin', '阿琴', 'pengamen', True, 45, 'jujur', 'f2', 'keliling', ['jembatan', 'pasar_pagi_1', 'teh'],
          ('Ah Qin', 'Ah Qin'), ('Pemain Erhu Buta', 'The Blind Erhu Player'),
          ('Kehilangan penglihatan karena demam semasa kecil, Ah Qin belajar erhu dari guru pengamen Shi Banqiao. Gurunya wafat '
           'sebelum sempat mengajarkan lagu terakhirnya, "Bulan di Bawah Jembatan". Ia berkeliling tiga kota mencari orang yang '
           'masih ingat nadanya.',
           'Blinded by a childhood fever, Ah Qin learned the erhu from the street musician Shi Banqiao. Her master died before '
           'teaching her his last song, "Moon Under the Bridge". She wanders three towns looking for anyone who still remembers '
           'its tune.'),
          abadi=False, kebiasaan=['pemain_erhu'], jadwal='pengamen')
    tokoh('hong_ling', '红绫', 'akrobat', True, 50, 'ramah', 'f1', 'keliling', ['panggung', 'teh'],
          ('Nyonya Hong Ling', 'Madam Hong Ling'), ('Ketua Rombongan Bulan Sabit', 'Head of the Crescent Moon Troupe'),
          ('Dulu penari tali paling berani di selatan; kini memimpin dua puluh pemain dengan suara yang bisa membelah keramaian '
           'pasar. Ia menerima pesanan barongsai untuk peresmian bangunan baru dan menawar harga seperti saudagar garam.',
           'Once the boldest rope dancer of the south; now she leads twenty players with a voice that can split a market crowd. '
           'She takes lion-dance bookings for the opening of new buildings and haggles like a salt merchant.'),
          keluarga=[('murid', 'yan_feiyan'), ('murid', 'yan_feihu'), ('rekan', 'lao_shuo')], jadwal='rombongan')
    tokoh('yan_feiyan', '燕飞燕', 'akrobat', True, 19, 'ramah', 'f3', 'keliling', ['panggung'],
          ('Yan Feiyan', 'Yan Feiyan'), ('Akrobat Rombongan, Sang Kakak', 'Troupe Acrobat, the Elder Sister'),
          ('Bisa berdiri di atas tiga piring yang berputar sambil menuang teh. Ia menjaga adiknya sejak orang tua mereka '
           'hilang dalam banjir, dan diam-diam menabung untuk membuka sekolah akrobat sendiri.',
           'She can stand on three spinning plates while pouring tea. She has looked after her brother since their parents '
           'vanished in a flood, and quietly saves to open an acrobat school of her own.'),
          abadi=False, keluarga=[('adik', 'yan_feihu')], jadwal='rombongan')
    tokoh('yan_feihu', '燕飞虎', 'akrobat', False, 17, 'sombong', 'm3', 'keliling', ['panggung'],
          ('Yan Feihu', 'Yan Feihu'), ('Akrobat Rombongan, Sang Adik', 'Troupe Acrobat, the Younger Brother'),
          ('Puncak piramida manusia dan sumber setengah kekacauan rombongan. Ia bersumpah tidak akan pernah menetap di satu '
           'kota, sampai ia melihat seorang gadis penjual bunga di pasar pagi.',
           'The top of the human pyramid and the source of half the troupe\'s trouble. He swore he would never settle in one '
           'town, until he saw a flower girl at the morning market.'),
          abadi=False, keluarga=[('kakak', 'yan_feiyan')], jadwal='rombongan')
    tokoh('hu_lu', '胡卢', 'penjual_obat', False, 40, 'licik', 'm2', 'keliling', ['pasar_pagi_2', 'teh'],
          ('Hu Lu', 'Hu Lu'), ('Tabib Keliling', 'Travelling Physician'),
          ('Labu obatnya berisi "Pil Seribu Khasiat" yang menyembuhkan segala hal kecuali dompet kosong. Separuh dagangannya '
           'tepung beras berwarna, separuhnya lagi, anehnya, benar-benar manjur. Hanya Hu Lu yang tahu mana yang mana.',
           'His gourd holds "Thousand-Virtue Pills" that cure everything except an empty purse. Half his stock is coloured rice '
           'flour, the other half, strangely, really works. Only Hu Lu knows which is which.'),
          kebiasaan=['penjudi'], jadwal='rombongan')
    tokoh('ma_fu', '马福', 'kafilah_bashi', False, 55, 'serakah', 'm2', 'keliling', ['kafilah', 'gudang_kafilah'],
          ('Saudagar Ma Fu', 'Merchant Ma Fu'), ('Pemimpin Kafilah Keliling', 'Leader of a Travelling Caravan'),
          ('Kafilahnya membawa barang yang tidak dijual di toko mana pun: sutra Kota Air, garam gunung, batu roh dari tambang '
           'yang sudah tutup. Ia menghitung untung dengan sempoa perak dan tidak pernah lupa siapa berutang padanya.',
           'His caravan carries goods no shop sells: Water City silk, mountain salt, spirit stones from closed mines. He counts '
           'profit on a silver abacus and never forgets who owes him.'),
          kebiasaan=['penabung'], jadwal='kafilah')
    tokoh('lao_geng', '老更', 'penjaga_malam', False, 72, 'jujur', 'elder', 'ibukota', ['ronda_1', 'gerbang'],
          ('Lao Geng', 'Lao Geng'), ('Penjaga Malam Ibu Kota', 'Night Watchman of the Capital'),
          ('Lima puluh tahun memukul kentongan di jalan-jalan ibu kota, dari jam malam pertama sampai jam kelima. Tangannya '
           'gemetar sekarang, tapi pukulannya tidak pernah meleset satu ketukan pun. Ia mencari pewaris sebelum lentera terakhirnya padam.',
           'Fifty years he has struck the watch rattle through the capital\'s streets, from the first night watch to the fifth. '
           'His hands shake now, but his beat has never missed once. He is looking for an heir before his last lantern goes out.'),
          abadi=False, kebiasaan=['pecinta_teh', 'tidur_siang'], keluarga=[('murid', 'xiao_bang')], jadwal='ronda')
    tokoh('xiao_bang', '小梆', 'penjaga_malam', False, 16, 'penakut', 'm3', 'ibukota', ['ronda_1', 'gerbang'],
          ('Xiao Bang', 'Xiao Bang'), ('Murid Penjaga Malam', 'Night Watchman\'s Apprentice'),
          ('Yatim dari Kumuh yang dipungut Lao Geng. Takut gelap, takut anjing, takut kucing hitam, tapi tidak pernah absen satu '
           'malam pun. Ia menghafal pola pukulan gurunya dengan mengetukkan jari di mangkuk nasi.',
           'An orphan from the Slums taken in by Lao Geng. Afraid of the dark, of dogs, of black cats, yet he has never missed a '
           'single night. He memorises his master\'s beat by tapping his fingers on his rice bowl.'),
          abadi=False, keluarga=[('guru', 'lao_geng')], jadwal='ronda')
    tokoh('bibi_mei', '梅婆', 'mak_comblang', True, 58, 'licik', 'f2', 'ibukota', ['sumur', 'teh'],
          ('Bibi Mei', 'Auntie Mei'), ('Mak Comblang Hunian Barat', 'Matchmaker of the West Residences'),
          ('Sudah menjodohkan dua ratus pasangan dan memisahkan, katanya, hanya tiga. Ia tahu mas kawin setiap keluarga di '
           'Hunian Barat dan menyimpan almanak merah yang lebih tebal dari kitab hukum.',
           'She has matched two hundred couples and parted, she says, only three. She knows every West Residences family\'s '
           'bride price and keeps a red almanac thicker than the law book.'),
          kebiasaan=['gosip_sumur', 'pesolek'], jadwal='sumur')
    tokoh('bibi_zhang', '张婶', 'warga', True, 46, 'ramah', 'f1', 'ibukota', ['sumur'],
          ('Bibi Zhang', 'Auntie Zhang'), ('Bibi Sumur, yang Ramah', 'Well Auntie, the Kind One'),
          ('Selalu membawa bakpao lebih untuk siapa saja yang lewat. Kalau ia menyebar kabar, kabarnya selalu tentang orang '
           'yang perlu ditolong. Ia bibi dari Zhang Shan.',
           'Always carries extra buns for whoever passes. When she spreads news, it is always about someone who needs help. '
           'She is Zhang Shan\'s aunt.'),
          kebiasaan=['gosip_sumur', 'dermawan'], keluarga=[('keponakan', 'zhang_shan')], jadwal='sumur')
    tokoh('bibi_li', '李婶', 'warga', True, 50, 'curiga', 'f2', 'ibukota', ['sumur'],
          ('Bibi Li', 'Auntie Li'), ('Bibi Sumur, yang Curiga', 'Well Auntie, the Suspicious One'),
          ('Menghitung ember orang lain dan tahu siapa yang menimba lebih dari jatahnya. Tidak ada gosip yang lewat sumur '
           'tanpa ia tanyakan asalnya, dan tidak ada orang asing yang lewat tanpa ia tatap.',
           'Counts other people\'s buckets and knows who draws more than their share. No gossip passes the well without her '
           'asking where it came from, and no stranger passes without her stare.'),
          kebiasaan=['gosip_sumur', 'pengintip'], jadwal='sumur')
    tokoh('bibi_wang', '王婶', 'warga', True, 42, 'penakut', 'f3', 'ibukota', ['sumur'],
          ('Bibi Wang', 'Auntie Wang'), ('Bibi Sumur, yang Penakut', 'Well Auntie, the Timid One'),
          ('Tahu semua hal tentang semua orang, dan sedikit melebihkan semuanya. Dua copet menjadi lima, seekor anjing liar '
           'menjadi serigala. Tetapi kalau ada anak hilang, Bibi Wang orang pertama yang tahu ke mana ia lari.',
           'Knows everything about everyone, and exaggerates all of it a little. Two pickpockets become five, a stray dog becomes '
           'a wolf. But when a child goes missing, Auntie Wang is the first to know where it ran.'),
          kebiasaan=['gosip_sumur', 'takhayul'], jadwal='sumur')
    tokoh('kakek_xiang', '象爷', 'warga', False, 74, 'bijak', 'elder', 'ibukota', ['catur'],
          ('Kakek Xiang', 'Grandpa Xiang'), ('Pecatur di Bawah Pohon Huai', 'Chess Player Under the Pagoda Tree'),
          ('Bermain catur gajah dengan Kakek Che setiap pagi selama empat puluh tahun. Ia selalu menang di babak pertama dan '
           'selalu sengaja kalah di babak kedua, supaya sahabatnya mau datang lagi besok.',
           'Has played elephant chess with Grandpa Che every morning for forty years. He always wins the first game and always '
           'loses the second on purpose, so his friend will come back tomorrow.'),
          abadi=False, kebiasaan=['kakek_catur', 'tai_chi'], keluarga=[('sahabat', 'kakek_che')], jadwal='catur')
    tokoh('kakek_che', '车爷', 'warga', False, 77, 'sombong', 'elder', 'ibukota', ['catur'],
          ('Kakek Che', 'Grandpa Che'), ('Pecatur yang Tak Mau Kalah', 'The Chess Player Who Will Not Lose'),
          ('Mengaku pernah mengalahkan seorang tetua sekte dalam dua belas langkah. Membanting bidak kalau kalah, lalu '
           'menyusunnya lagi dengan sangat hati-hati. Belum pernah sadar bahwa Kakek Xiang mengalah setiap hari.',
           'Claims he once beat a sect elder in twelve moves. Slams the pieces when he loses, then sets them up again very '
           'carefully. Has never realised that Grandpa Xiang lets him win every day.'),
          abadi=False, kebiasaan=['kakek_catur', 'pemarah'], keluarga=[('sahabat', 'kakek_xiang')], jadwal='catur')
    tokoh('li_zuixian', '李醉仙', 'sastrawan', False, 38, 'sombong', 'm1', 'ibukota', ['teh', 'rumah_makan'],
          ('Li Zuixian', 'Li Zuixian'), ('Penyair Pemabuk dari Kedai Bulan Mabuk', 'Drunken Poet of the Drunken Moon Tavern'),
          ('Gagal ujian negara tiga kali karena terlalu jujur menulis tentang para penguji. Kini menulis pantun festival untuk '
           'segelas arak dan bersumpah bisa mengalahkan siapa saja dalam adu bait. Konon ia menyimpan giok teknik di balik jubahnya.',
           'Failed the state examination three times for writing too honestly about the examiners. Now writes festival couplets '
           'for a cup of wine and swears he can beat anyone at verse-capping. They say he keeps a technique jade inside his robe.'),
          kebiasaan=['pemabuk', 'penyair'], jadwal='kedai_malam')
    tokoh('shi_xiaoqian', '千手鼠', 'pencopet', False, 26, 'licik', 'm3', 'ibukota', ['kumuh', 'pasar_hantu_1'],
          ('Shi Xiaoqian', 'Shi Xiaoqian'), ('"Tikus Seribu Jari", Bos Copet Kumuh', '"Thousand-Finger Rat", Pickpocket Boss of the Slums'),
          ('Ia sendiri dulu anak jalanan yang dipukul karena mencuri sepotong kue. Kini belasan anak Kumuh mencopet untuknya, '
           'dan ia memberi mereka makan lebih teratur daripada siapa pun di kota ini. Jarinya cukup cepat untuk melepas kancing '
           'jubah seorang kultivator.',
           'He was once a street child beaten for stealing a cake. Now a dozen Slum children pick pockets for him, and he feeds '
           'them more regularly than anyone else in town. His fingers are quick enough to undo a cultivator\'s robe buttons.'),
          kebiasaan=['penjudi'], jadwal='kumuh')
    tokoh('lentera_hijau', '绿灯先生', 'makelar_gelap', False, 0, 'licik', 'demon', 'ibukota', ['pasar_hantu_1', 'pasar_hantu_2'],
          ('Tuan Lentera Hijau', 'Mister Green Lantern'), ('Tuan Rumah Pasar Hantu', 'Host of the Ghost Market'),
          ('Tidak ada yang tahu umurnya, wajahnya, atau di mana ia tidur. Ia muncul di balik lentera hijau setiap malam Pasar '
           'Hantu, berbisik lewat topeng kertas, dan hanya menjawab orang yang tahu sandi malam itu.',
           'Nobody knows his age, his face, or where he sleeps. He appears behind a green lantern every Ghost Market night, '
           'whispers through a paper mask, and only answers those who know that night\'s password.'),
          jadwal='pasar_hantu', rupa='topeng_kertas')
    tokoh('gong_shu', '公输', 'tukang', False, 60, 'jujur', 'elder', 'ibukota', ['kavling', 'perbaikan'],
          ('Mandor Gong Shu', 'Foreman Gong Shu'), ('Mandor Pembangunan Ibu Kota', 'Master Builder of the Capital'),
          ('Keturunan keluarga tukang yang konon membangun tangga awan pertama. Ia membawa salinan lusuh "Yingzao Fashi" ke '
           'mana-mana dan bisa menghitung genteng atap dengan sekali pandang. Baginya kota adalah makhluk hidup yang harus tumbuh lurus.',
           'Descended from a builders\' family said to have raised the first cloud ladder. He carries a worn copy of the "Yingzao '
           'Fashi" everywhere and can count a roof\'s tiles at a glance. To him a city is a living thing that must grow straight.'),
          kebiasaan=['pekerja_keras'], jadwal='mandor')
    tokoh('shen_wan', '沈万', 'pialang', False, 52, 'serakah', 'm2', 'ibukota', ['bank', 'pegadaian'],
          ('Juragan Shen Wan', 'Master Shen Wan'), ('Pialang Rumah Uang', 'Broker of the Counting House'),
          ('Memiliki seperempat gudang ibu kota dan surat utang separuh warganya. Ia menyumbang lentera untuk kuil setiap '
           'festival, dan menagih bunga dari kuil yang sama keesokan harinya.',
           'Owns a quarter of the capital\'s warehouses and the IOUs of half its people. He donates lanterns to the temple every '
           'festival, and collects interest from the same temple the next day.'),
          kebiasaan=['penabung', 'pelit'], keluarga=[('keponakan', 'shen_ziyu')], jadwal='rumah_uang')
    tokoh('shen_ziyu', '沈子玉', 'tuan_muda', False, 23, 'sombong', 'm1', 'ibukota', ['bank', 'teh'],
          ('Shen Ziyu', 'Shen Ziyu'), ('Keponakan Juragan Shen', 'Nephew of Master Shen'),
          ('Pewaris yang lebih pandai memilih kipas daripada membaca buku kas. Pamannya sedang mencarikan istri dari keluarga '
           'yang punya kedai, supaya keponakannya belajar berdagang.',
           'An heir better at choosing fans than reading ledgers. His uncle is looking for a wife from a family with a shop, so '
           'the nephew might learn trade.'),
          abadi=False, kebiasaan=['pesolek'], keluarga=[('paman', 'shen_wan')], jadwal='tuan_muda')
    tokoh('qiu_panitera', '秋书吏', 'menteri', False, 44, 'jujur', 'm1', 'ibukota', ['yamen', 'cukai'],
          ('Panitera Qiu', 'Clerk Qiu'), ('Juru Pajak Yamen', 'Tax Clerk of the Yamen'),
          ('Menulis setiap keping pajak dengan kuas yang sama selama dua puluh tahun. Tidak pernah menerima amplop, dan karena '
           'itu tidak pernah naik pangkat. Ia mencari orang yang mau naik jabatan dengan cara yang sama.',
           'Has written down every coin of tax with the same brush for twenty years. Never took an envelope, and therefore never '
           'rose in rank. He is looking for someone willing to rise the same way.'),
          kebiasaan=['kutu_buku'], jadwal='yamen')
    tokoh('nenek_tang', '汤婆婆', 'koki', True, 81, 'ramah', 'f2', 'ibukota', ['warung', 'dapur_umum'],
          ('Nenek Tang', 'Granny Tang'), ('Pemilik Warung Sup Musim Dingin', 'Keeper of the Winter Soup Stall'),
          ('Warung supnya hanya buka di musim dingin, dan setiap orang yang kedinginan boleh makan meski tak punya uang. Resepnya '
           'diturunkan dari neneknya, yang mendapatkannya dari seorang biksu pada musim dingin paling panjang yang diingat kota ini.',
           'Her soup stall opens only in winter, and anyone who is cold may eat even without money. The recipe came from her '
           'grandmother, who had it from a monk in the longest winter this city remembers.'),
          abadi=False, kebiasaan=['dermawan', 'pendoa_kuil'], keluarga=[('cucu', 'tang_xiaomi')], jadwal='warung_sup')
    tokoh('tang_xiaomi', '汤小米', 'pelayan', True, 18, 'jujur', 'f1', 'ibukota', ['warung', 'dapur_umum'],
          ('Tang Xiaomi', 'Tang Xiaomi'), ('Cucu Nenek Tang', 'Granny Tang\'s Granddaughter'),
          ('Mengiris lobak lebih cepat daripada neneknya dan menghafal seratus nama pelanggan tetap. Belum berani memasak sup '
           'itu sendiri; ia selalu lupa satu bahan yang tidak pernah disebutkan neneknya.',
           'Slices radish faster than her grandmother and knows a hundred regulars by name. She does not yet dare cook the soup '
           'alone; she always forgets one ingredient her grandmother never names.'),
          abadi=False, keluarga=[('nenek', 'nenek_tang')], jadwal='warung_sup')
    tokoh('paman_bai', '白叔', 'pengurus_duka', False, 63, 'bijak', 'elder', 'ibukota', ['kuil', 'kuil_dewa_kota'],
          ('Paman Bai', 'Uncle Bai'), ('Pengurus Upacara Duka', 'Master of Funeral Rites'),
          ('Sudah memimpin seribu pemakaman dan masih menangis di setiap pemakaman anak. Ia tahu tata cara duka setiap marga '
           'dan percaya bahwa orang mati baru benar-benar pergi kalau tidak ada lagi yang menyebut namanya.',
           'Has led a thousand funerals and still weeps at every child\'s. He knows every clan\'s mourning rites and believes the '
           'dead only truly leave when no one says their name any more.'),
          kebiasaan=['pendoa_kuil'], jadwal='kuil')
    tokoh('guru_zhou', '周书', 'guru_sekolah', False, 48, 'bijak', 'm2', 'ibukota', ['sekolah', 'perpustakaan'],
          ('Tuan Guru Zhou Shu', 'Master Zhou Shu'), ('Guru Sekolah Ibu Kota', 'Schoolmaster of the Capital'),
          ('Mengajar anak tukang dan anak pejabat di bangku yang sama, dan memukul tangan keduanya dengan penggaris yang sama. '
           'Ia yang pertama melihat daun melayang di sekitar seorang gadis kecil dari Kumuh.',
           'Teaches craftsmen\'s and officials\' children on the same bench, and raps both their hands with the same ruler. He '
           'was the first to see leaves floating around a little girl from the Slums.'),
          kebiasaan=['kutu_buku', 'tai_chi'], jadwal='sekolah')
    tokoh('fang_gudang', '方娘子', 'penjaga_gudang', True, 46, 'curiga', 'f1', 'ibukota', ['gudang_kafilah', 'kontrak'],
          ('Nyonya Fang', 'Madam Fang'), ('Kepala Gudang Ibu Kota', 'Head of the Capital Warehouses'),
          ('Menimbang setiap karung dua kali dan setiap saudagar tiga kali. Tikus pun harus mencatat namanya di buku masuk '
           'gudangnya. Ia bersumpah suatu hari akan membuktikan Juragan Shen menimbun beras.',
           'Weighs every sack twice and every merchant three times. Even rats must sign her warehouse book. She swears that one '
           'day she will prove Master Shen hoards rice.'),
          kebiasaan=['penabung'], jadwal='gudang')
    tokoh('gao_senior', '高师兄', 'murid_sekte_dalam', False, 24, 'sombong', 'm1', 'ibukota', ['teh', 'gerbang'],
          ('Kakak Senior Gao', 'Senior Brother Gao'), ('Perekrut Sekte Lurus', 'Recruiter of a Righteous Sect'),
          ('Utusan sekte Lurus terkuat di wilayah ini, turun gunung setiap musim untuk mencari anak berbakat. Pedangnya bagus, '
           'jubahnya lebih bagus, dan sopan santunnya tertinggal di gunung.',
           'Envoy of the strongest Righteous sect in the region, down from the mountain every season to find gifted children. '
           'His sword is fine, his robe finer, and his manners stayed on the mountain.'),
          kebiasaan=['pesolek'], jadwal='perekrut')
    tokoh('xiao_yu', '小雨', 'anak', True, 9, 'ramah', 'f1', 'ibukota', ['kumuh_anak', 'sekolah'],
          ('Xiao Yu', 'Xiao Yu'), ('Anak Kumuh', 'Child of the Slums'),
          ('Daun-daun berputar mengelilinginya kalau ia tertawa, dan ia mengira semua orang juga begitu. Ia membantu ibunya '
           'meniup gula menjadi burung dan diam-diam mengintip kelas Guru Zhou dari jendela.',
           'Leaves circle around her when she laughs, and she thinks everyone is like that. She helps her mother blow sugar into '
           'birds and secretly watches Master Zhou\'s class through the window.'),
          abadi=False, kebiasaan=['pemuja_kultivator'], keluarga=[('ayah', 'yu_laosan'), ('ibu', 'yu_xiuniang')], jadwal='anak')
    tokoh('yu_laosan', '于老三', 'kuli', False, 36, 'licik', 'm3', 'ibukota', ['kumuh', 'judi_batu'],
          ('Yu Laosan', 'Yu Laosan'), ('Ayah Xiao Yu, Kuli dan Penjudi', 'Xiao Yu\'s Father, Porter and Gambler'),
          ('Kuli pelabuhan yang kuat dan pemain dadu yang lemah. Utangnya di meja dadu sudah lebih besar dari upah setahun, dan '
           'ia mulai mendengarkan tawaran orang-orang berjubah sekte.',
           'A strong dock porter and a weak dice player. His gambling debt is larger than a year\'s wages, and he has begun '
           'listening to offers from men in sect robes.'),
          abadi=False, kebiasaan=['penjudi'], keluarga=[('istri', 'yu_xiuniang'), ('anak', 'xiao_yu')], jadwal='kuli')
    tokoh('yu_xiuniang', '于秀娘', 'warga', True, 33, 'ramah', 'f2', 'ibukota', ['kumuh', 'pasar_pagi_1'],
          ('Yu Xiuniang', 'Yu Xiuniang'), ('Ibu Xiao Yu, Pelukis Gula', 'Xiao Yu\'s Mother, Sugar Painter'),
          ('Melukis naga dan bangau dari gula cair di atas papan batu. Ia tahu putrinya istimewa sejak bayinya tertawa dan semua '
           'kelopak bunga di jendela berbalik ke arahnya.',
           'Paints dragons and cranes in molten sugar on a stone slab. She has known her daughter was special since the baby '
           'laughed and every petal at the window turned toward her.'),
          abadi=False, kebiasaan=['pelukis_gula'], keluarga=[('suami', 'yu_laosan'), ('anak', 'xiao_yu')], jadwal='pasar_pagi')
    tokoh('lao_hua', '老花', 'warga', False, 6, '', 'kucing', 'ibukota', ['kucing_tidur', 'penjual_ikan'],
          ('Lao Hua', 'Lao Hua'), ('Kucing Pasar Ikan', 'Cat of the Fish Market'),
          ('Kucing belang tiga yang gemuk dan dihormati seluruh pasar ikan. Ia memilih sendiri siapa yang boleh mengelusnya dan '
           'pernah hilang tiga hari, membuat setengah pasar berhenti berjualan untuk mencarinya.',
           'A fat calico cat respected by the whole fish market. She chooses for herself who may stroke her, and once vanished '
           'for three days, making half the market stop trading to look for her.'),
          abadi=False, hewan=True, jadwal='kucing')
    tokoh('zhang_shan', '张山', 'tukang', False, 20, 'jujur', 'm3', 'ibukota', ['sumur', 'warung'],
          ('Zhang Shan', 'Zhang Shan'), ('Putra Keluarga Tahu', 'Son of the Tofu Family'),
          ('Bangun sebelum subuh untuk menggiling kedelai dan pandai mengukir burung dari kayu sisa. Setiap pagi ia menimba air '
           'di sumur pada waktu yang sama dengan Li Ying, dan setiap pagi pura-pura itu kebetulan.',
           'Rises before dawn to grind soybeans and carves birds from scrap wood. Every morning he draws water at the well at '
           'the same time as Li Ying, and every morning pretends it is chance.'),
          abadi=False, kebiasaan=['pekerja_keras'], keluarga=[('ayah', 'zhang_laotou'), ('bibi', 'bibi_zhang')], jadwal='keluarga_tahu')
    tokoh('li_ying', '李莺', 'pelayan', True, 19, 'ramah', 'f1', 'ibukota', ['sumur', 'rumah_makan'],
          ('Li Ying', 'Li Ying'), ('Putri Keluarga Arak', 'Daughter of the Wine Family'),
          ('Menyanyi sambil menyaring arak dan bisa mengenali umur sebuah guci dari baunya. Ia menyimpan seekor burung kayu '
           'kecil di bawah bantalnya dan tidak pernah bilang dari siapa.',
           'Sings while straining wine and can tell a jar\'s age by its smell. She keeps a small wooden bird under her pillow and '
           'never says who gave it.'),
          abadi=False, kebiasaan=['penyulam'], keluarga=[('ayah', 'li_laoban')], jadwal='keluarga_arak')
    tokoh('zhang_laotou', '张老头', 'koki', False, 52, 'jujur', 'm2', 'ibukota', ['warung'],
          ('Pak Tua Zhang', 'Old Zhang'), ('Pembuat Tahu Jalan Sumur', 'Tofu Maker of Well Street'),
          ('Tahunya paling lembut di Hunian Barat, lidahnya paling keras. Ia tidak pernah lupa bahwa kakek Li menggeser patok '
           'batas sumur satu langkah ke halaman keluarga Zhang, empat puluh tahun lalu.',
           'His tofu is the softest in the West Residences, his tongue the hardest. He never forgets that Li\'s grandfather moved '
           'the well boundary stone one step into the Zhang yard, forty years ago.'),
          abadi=False, kebiasaan=['pemarah', 'pekerja_keras'], keluarga=[('anak', 'zhang_shan')], jadwal='keluarga_tahu')
    tokoh('li_laoban', '李老板', 'pemilik_teh', False, 55, 'sombong', 'm1', 'ibukota', ['rumah_makan', 'teh'],
          ('Juragan Li', 'Boss Li'), ('Pemilik Kedai Arak Jalan Sumur', 'Owner of the Well Street Wine Shop'),
          ('Kedai araknya berdiri sejak zaman kakeknya, dan begitu pula dendamnya. Ia bilang keluarga Zhang berutang sepuluh '
           'tahil perak yang tidak pernah dibayar, meski ia sendiri tidak pernah melihat surat utangnya.',
           'His wine shop has stood since his grandfather\'s day, and so has his grudge. He says the Zhang family owes ten taels '
           'of silver never paid, though he himself has never seen the IOU.'),
          abadi=False, kebiasaan=['pemabuk'], keluarga=[('anak', 'li_ying')], jadwal='keluarga_arak')
    tokoh('wu_dalang', '吴大郎', 'koki', False, 41, 'jujur', 'm2', 'ibukota', ['rumah_makan', 'warung'],
          ('Wu Dalang', 'Wu Dalang'), ('Penjual Bakmi Tiga Generasi', 'Third-Generation Noodle Seller'),
          ('Menarik mi dengan tangan sejak umur sepuluh. Kakeknya meminjam lima tahil dari keluarga Shen untuk membuka warung; '
           'setelah tiga generasi membayar bunga, utangnya kini dua ratus tahil.',
           'Has pulled noodles by hand since he was ten. His grandfather borrowed five taels from the Shen family to open the '
           'stall; after three generations of paying interest, the debt is now two hundred.'),
          abadi=False, kebiasaan=['pekerja_keras'], keluarga=[('istri', 'wu_niangzi')], jadwal='warung')
    tokoh('wu_niangzi', '吴娘子', 'pelayan', True, 38, 'penakut', 'f2', 'ibukota', ['rumah_makan', 'warung'],
          ('Nyonya Wu', 'Madam Wu'), ('Istri Penjual Bakmi', 'The Noodle Seller\'s Wife'),
          ('Menyimpan setiap kwitansi bunga dalam kotak teh berkarat di bawah lantai. Ia yang akhirnya berani meminta tolong.',
           'Keeps every interest receipt in a rusty tea tin under the floor. She is the one who finally dares to ask for help.'),
          abadi=False, kebiasaan=['penabung'], keluarga=[('suami', 'wu_dalang')], jadwal='warung')
    tokoh('dalang_su', '苏师傅', 'pemusik', False, 57, 'bijak', 'm2', 'ibukota', ['panggung'],
          ('Dalang Su', 'Puppeteer Su'), ('Dalang Teater Bayang', 'Master of the Shadow Theatre'),
          ('Menggerakkan enam wayang dengan dua tangan dan menyuarakan semuanya dengan satu tenggorokan. Wayang kulit '
           'tertuanya dibuat oleh gurunya dari kulit keledai yang disamak seratus hari.',
           'Moves six puppets with two hands and voices them all with one throat. His oldest leather puppets were made by his '
           'master from donkey hide tanned for a hundred days.'),
          abadi=True, kebiasaan=['penggemar_opera'], jadwal='teater')
    tokoh('tetua_he', '何老', 'tetua_desa', False, 68, 'jujur', 'elder', 'kota34', ['gerbang', 'kuil'],
          ('Tetua He', 'Elder He'), ('Tetua Kota yang Ditinggalkan Pelindungnya', 'Elder of a Town Its Protector Forgot'),
          ('Sudah tiga musim menulis surat ke sekte pelindungnya tanpa jawaban, sementara begal mengambil jagung dari ladang '
           'dan anak gadis dari jalan. Ia akan berlutut pada siapa pun yang mau mengangkat panji di gerbangnya.',
           'For three seasons he has written to his protector sect without an answer, while bandits take corn from the fields '
           'and girls from the road. He will kneel to anyone willing to raise a banner over his gate.'),
          abadi=True, jadwal='tetua')


# ================================================================================================ dialog helpers
LANJUT = ('Lalu?', 'And then?')
KEMBALI = ('Aku mau tanya hal lain.', 'I want to ask something else.')
PAMIT = ('Sampai jumpa.', 'Goodbye.')


def sapa(did, who, buka, topik, prioritas=5, syarat=(), gestur='bicara_tangan', prop='', suara='', awal=(), pamit=PAMIT,
         sekali=False):
    """A character's talk: an opening line and a list of topics. topik: (node, choice (id,en), reply (id,en), opts) with opts
    gestur/prop/suara/akibat/isian/kabar (a live rumour instead of the reply)/syarat (choice conditions)/alasan/lanjut [(choice, reply, gestur)] for a follow-up chain.
    awal: extra P choices put before the topics on the opening node (quest starts, stage actions)."""
    nodes = []
    choices = list(awal)
    for node, pilih, jawab, opts in topik:
        choices.append(P(pilih, ke=node, syarat=opts.get('syarat', ()), akibat=opts.get('akibat_pilih', ()), alasan=opts.get('alasan')))
    choices.append(P(pamit))
    nodes.append(N('mulai', buka, gestur=gestur, prop=prop, suara=suara, pilihan=choices))
    for node, pilih, jawab, opts in topik:
        chain = opts.get('lanjut', [])
        nxt = [P(chain[0][0], ke=f'{node}_2')] if chain else [P(KEMBALI, ke='mulai'), P(pamit)]
        nodes.append(N(node, jawab, gestur=opts.get('gestur', ''), prop=opts.get('prop', ''), suara=opts.get('suara', ''),
                       akibat=opts.get('akibat', ()), isian=opts.get('isian', ()), pilihan=nxt, kabar=opts.get('kabar', False)))
        for i, link in enumerate(chain):
            last = i == len(chain) - 1
            nxt = [P(KEMBALI, ke='mulai'), P(pamit)] if last else [P(chain[i + 1][0], ke=f'{node}_{i + 3}')]
            nodes.append(N(f'{node}_{i + 2}', link[1], gestur=link[2] if len(link) > 2 else '', pilihan=nxt))
    D(did, who, nodes, prioritas=prioritas, syarat=syarat, sekali=sekali)


# ================================================================================================ the twelve tales of Lao Shuo
# Own short retellings (6-10 lines each), never a copied translation. Sumber lines name the classic each tale comes from.
KISAH = [
    ('kera_sakti', ('Kera Batu Mengacau Istana Langit', 'The Stone Monkey Wrecks the Heavenly Palace'),
     ('Sumber: Wu Cheng\'en, Perjalanan ke Barat (Xiyou Ji), bab 1-7', 'Source: Wu Cheng\'en, Journey to the West (Xiyou Ji), ch. 1-7'), [
         ('Di puncak Gunung Bunga dan Buah ada sebutir batu yang dihangatkan matahari dan bulan selama ribuan tahun.',
          'On the peak of Flower Fruit Mountain lay a stone warmed by sun and moon for thousands of years.'),
         ('Suatu pagi batu itu pecah, dan keluarlah seekor kera yang matanya memancarkan cahaya sampai ke langit.',
          'One morning it cracked, and out came a monkey whose eyes shot light all the way to heaven.'),
         ('Kera itu berguru, belajar tujuh puluh dua perubahan dan jungkir balik awan yang melesat seratus delapan ribu li.',
          'The monkey found a master and learned seventy-two transformations and a cloud somersault of a hundred and eight thousand li.'),
         ('Istana Langit memberinya jabatan penjaga kuda. Ketika tahu jabatan itu jabatan terendah, ia mengamuk.',
          'The Heavenly Palace made him keeper of the horses. When he learned it was the lowest post of all, he rioted.'),
         ('Ia mencuri persik keabadian, menghabiskan pil Lao Jun, dan mengalahkan seratus ribu prajurit langit.',
          'He stole the peaches of immortality, ate up Lao Jun\'s pills and beat a hundred thousand heavenly soldiers.'),
         ('Lalu Buddha datang dan bertaruh: kalau kau bisa melompat keluar dari telapak tanganku, takhta langit milikmu.',
          'Then the Buddha came with a wager: leap out of my palm, and heaven\'s throne is yours.'),
         ('Kera itu melompat ke ujung dunia, menulis namanya di lima tiang, dan ternyata tiang itu jari-jari Buddha.',
          'The monkey leapt to the end of the world and wrote his name on five pillars, which turned out to be the Buddha\'s fingers.'),
         ('Lima ratus tahun ia terhimpit gunung. Ingat itu, anak muda, kalau kalian merasa lebih kuat dari langit.',
          'Five hundred years he lay under a mountain. Remember that, young ones, whenever you feel stronger than heaven.')]),
    ('ular_putih', ('Ular Putih di Danau Barat', 'The White Snake of West Lake'),
     ('Sumber: legenda rakyat Ular Putih (Baishe Zhuan), versi Feng Menglong', 'Source: the folk legend of the White Snake (Baishe Zhuan), Feng Menglong\'s version'), [
         ('Seekor ular putih bertapa seribu tahun di gunung Emei sampai ia bisa mengambil wujud seorang wanita.',
          'A white snake meditated a thousand years on Mount Emei until she could take a woman\'s shape.'),
         ('Di Danau Barat, saat hujan musim semi, ia meminjam payung dari seorang pemuda tabib bernama Xu Xian.',
          'At West Lake, in a spring rain, she borrowed an umbrella from a young herbalist named Xu Xian.'),
         ('Mereka menikah, membuka toko obat, dan ramuan sang istri menyembuhkan wabah yang tidak bisa disembuhkan tabib lain.',
          'They married and opened an apothecary, and the wife\'s remedies cured a plague no other healer could.'),
         ('Tetapi biksu Fahai melihat sisik di bawah kulitnya dan menyuruh Xu Xian memberinya arak realgar di hari Duanwu.',
          'But the monk Fahai saw scales beneath her skin and told Xu Xian to give her realgar wine on the Duanwu festival.'),
         ('Sang istri berubah menjadi ular di depan suaminya, dan Xu Xian mati ketakutan.',
          'The wife turned into a snake before her husband\'s eyes, and Xu Xian died of fright.'),
         ('Ia terbang ke gunung dewa, mencuri rumput penghidup, dan menghidupkan suaminya lagi dengan tangan berdarah.',
          'She flew to the mountain of the immortals, stole the herb of life and brought her husband back with bleeding hands.'),
         ('Fahai mengurungnya di bawah Pagoda Leifeng. Kata orang, pagoda itu baru runtuh ketika danau mengering.',
          'Fahai imprisoned her under the Leifeng Pagoda. People say it would only fall when the lake ran dry.'),
         ('Siapa yang lebih kejam, ular yang mencintai atau manusia yang takut? Itu kalian yang menjawab.',
          'Who was crueller, the snake who loved or the man who feared? That you must answer yourselves.')]),
    ('penggembala', ('Penggembala dan Penenun', 'The Cowherd and the Weaver Girl'),
     ('Sumber: legenda Niulang dan Zhinü, asal Festival Qixi', 'Source: the legend of Niulang and Zhinü, origin of the Qixi festival'), [
         ('Penenun langit, putri Ratu Ibu Barat, menenun awan senja dengan benang cahaya.',
          'The weaver of heaven, daughter of the Queen Mother of the West, wove the sunset clouds from threads of light.'),
         ('Suatu hari ia turun mandi di sungai, dan seorang penggembala miskin menyembunyikan jubah terbangnya atas saran kerbaunya.',
          'One day she came down to bathe in a river, and a poor cowherd hid her flying robe, as his ox had advised.'),
         ('Ia tidak bisa pulang, lalu ia jatuh cinta. Mereka menikah dan punya dua anak.',
          'She could not go home, and then she fell in love. They married and had two children.'),
         ('Ratu Ibu murka dan menyeret putrinya kembali. Si penggembala mengejarnya dengan anak-anak di dalam keranjang pikulan.',
          'The Queen Mother raged and dragged her daughter back. The cowherd chased her with the children in his carrying baskets.'),
         ('Sang Ratu menggores langit dengan tusuk kondenya, dan terbentanglah Sungai Perak yang tak terseberangi.',
          'The Queen drew her hairpin across the sky, and the uncrossable Silver River spread out.'),
         ('Hanya sekali setahun, malam ketujuh bulan ketujuh, burung murai membentuk jembatan agar mereka bisa bertemu.',
          'Only once a year, on the seventh night of the seventh month, magpies form a bridge so they can meet.'),
         ('Kalau malam itu hujan, katanya itu air mata mereka. Jadi jangan menggerutu kalau festival Qixi basah.',
          'If it rains that night, they say it is their tears. So do not grumble when Qixi is wet.')]),
    ('change', ('Chang\'e Terbang ke Bulan', 'Chang\'e Flies to the Moon'),
     ('Sumber: legenda Hou Yi dan Chang\'e (Huainanzi dan cerita rakyat Pertengahan Musim Gugur)', 'Source: the legend of Hou Yi and Chang\'e (Huainanzi and Mid-Autumn folk tales)'), [
         ('Dahulu ada sepuluh matahari, dan bumi terbakar sampai sungai-sungai mendidih.',
          'Once there were ten suns, and the earth burned until the rivers boiled.'),
         ('Pemanah Hou Yi memanah sembilan di antaranya, dan meninggalkan satu supaya padi tetap tumbuh.',
          'The archer Hou Yi shot down nine of them, and left one so the rice would still grow.'),
         ('Sebagai hadiah, Ratu Ibu Barat memberinya satu pil keabadian, cukup untuk satu orang saja.',
          'As a reward the Queen Mother of the West gave him one elixir of immortality, enough for a single person.'),
         ('Hou Yi menyimpannya, karena ia tidak mau hidup selamanya tanpa istrinya, Chang\'e.',
          'Hou Yi put it away, for he did not want to live forever without his wife, Chang\'e.'),
         ('Seorang murid serakah mendobrak rumah mereka untuk merebut pil itu. Chang\'e menelannya agar tidak jatuh ke tangannya.',
          'A greedy disciple broke into their house to seize the elixir. Chang\'e swallowed it so it would not fall into his hands.'),
         ('Tubuhnya menjadi ringan dan terus naik, sampai ia berhenti di bulan, tempat terdekat dari suaminya yang masih di langit.',
          'Her body grew light and kept rising until she stopped on the moon, the closest place to her husband still in the sky.'),
         ('Hou Yi meletakkan kue dan buah di halaman setiap purnama. Itulah sebabnya kita makan kue bulan.',
          'Hou Yi set out cakes and fruit in the yard every full moon. That is why we eat mooncakes.')]),
    ('mulan', ('Mulan Menggantikan Ayahnya', 'Mulan Takes Her Father\'s Place'),
     ('Sumber: Balada Mulan (Mulan Ci), syair rakyat Dinasti Utara', 'Source: the Ballad of Mulan (Mulan Ci), a folk poem of the Northern Dynasties'), [
         ('Suara alat tenun berhenti, dan Mulan menghela napas di depan jendela.',
          'The loom fell silent, and Mulan sighed before the window.'),
         ('Daftar wajib militer datang, dua belas gulungan, dan di setiap gulungan ada nama ayahnya yang sudah tua.',
          'The conscription lists had come, twelve scrolls, and every scroll bore her old father\'s name.'),
         ('Ia membeli kuda di pasar timur, pelana di pasar barat, tali kekang di pasar selatan dan cambuk di pasar utara.',
          'She bought a horse in the east market, a saddle in the west, a bridle in the south and a whip in the north.'),
         ('Dua belas tahun ia berperang di perbatasan, dan tidak seorang pun tahu ia perempuan.',
          'Twelve years she fought on the frontier, and not one soul knew she was a woman.'),
         ('Kaisar menawarinya jabatan menteri. Ia hanya meminta seekor unta cepat untuk pulang.',
          'The emperor offered her a minister\'s seat. She asked only for a swift camel to ride home.'),
         ('Di rumah ia berganti pakaian lama dan menyisir rambut di depan cermin. Teman-teman seperangnya terbelalak di pintu.',
          'At home she put on her old clothes and combed her hair before the mirror. Her comrades stood gaping at the door.'),
         ('Kelinci jantan berlari, kelinci betina berlari. Kalau keduanya berlari berdampingan, siapa bisa membedakan?',
          'The buck hare runs, the doe hare runs. When the two run side by side, who can tell them apart?')]),
    ('hakim_bao', ('Hakim Bao dan Tiga Pisau Pemenggal', 'Judge Bao and the Three Guillotines'),
     ('Sumber: kisah-kisah Hakim Bao Zheng (Longtu Gong\'an dan lakon Sanxia Wuyi)', 'Source: the Judge Bao Zheng stories (Longtu Gong\'an and the Sanxia Wuyi plays)'), [
         ('Hakim Bao berwajah hitam dan berbulan sabit di dahi. Konon siang hari ia mengadili manusia, malam hari mengadili hantu.',
          'Judge Bao had a black face and a crescent moon on his brow. They say he judged men by day and ghosts by night.'),
         ('Di ruang sidangnya ada tiga pisau pemenggal: kepala naga untuk bangsawan, kepala harimau untuk pejabat, kepala anjing untuk rakyat.',
          'In his court stood three guillotines: the dragon head for nobles, the tiger head for officials, the dog head for commoners.'),
         ('Suatu hari seorang wanita desa datang menggendong anak. Suaminya, Chen Shimei, lulus ujian dan menikahi putri kaisar.',
          'One day a village woman came carrying a child. Her husband, Chen Shimei, had passed the exam and married the emperor\'s daughter.'),
         ('Untuk menutupi masa lalunya, Chen menyuruh orang membunuh istri dan anaknya sendiri.',
          'To hide his past, Chen sent men to kill his own wife and child.'),
         ('Sang putri dan permaisuri datang ke ruang sidang memohon ampun. Hakim Bao melepas topi jabatannya dan meletakkannya di meja.',
          'The princess and the empress dowager came to the court to beg for mercy. Judge Bao took off his official hat and laid it on the table.'),
         ('"Kalau hukum tunduk pada keluarga istana, topi ini tidak ada artinya." Lalu ia memerintahkan pisau kepala naga dibuka.',
          '"If the law bows to the palace, this hat means nothing." Then he ordered the dragon-head blade unveiled.'),
         ('Karena itu di setiap Yamen orang menggantung papan "Cermin Terang di Atas Tinggi". Hakim yang baik tidak bisa dibeli.',
          'That is why every Yamen hangs the board "A Clear Mirror Hung on High". A good judge cannot be bought.')]),
    ('delapan_dewa', ('Delapan Dewa Menyeberangi Laut', 'The Eight Immortals Cross the Sea'),
     ('Sumber: legenda Delapan Dewa (Baxian Guohai), lakon Dinasti Ming', 'Source: the legend of the Eight Immortals (Baxian Guohai), a Ming-dynasty play'), [
         ('Delapan dewa pulang dari pesta Ratu Ibu Barat dengan sedikit mabuk, dan tiba di tepi Laut Timur.',
          'Eight immortals came home from the Queen Mother of the West\'s banquet a little drunk, and reached the shore of the Eastern Sea.'),
         ('Lü Dongbin berkata: jangan naik awan, mari tiap orang menyeberang dengan pusakanya sendiri.',
          'Lü Dongbin said: no clouds today, let each of us cross on our own treasure.'),
         ('Si Pincang Li melempar labunya, Zhongli melempar kipasnya, He Xiangu berdiri di atas bunga teratai.',
          'Iron-Crutch Li tossed his gourd, Zhongli his fan, and He Xiangu stood upon a lotus flower.'),
         ('Lan Caihe menyeberang dengan papan kastanyetnya, dan Raja Naga Laut Timur menginginkannya.',
          'Lan Caihe crossed on his clappers, and the Dragon King of the Eastern Sea coveted them.'),
         ('Putra naga merampas kastanyet itu dan menyeret Lan Caihe ke dasar laut.',
          'The dragon prince snatched the clappers and dragged Lan Caihe to the bottom of the sea.'),
         ('Tujuh dewa lainnya mengamuk; laut dibakar, gunung dilempar, dan istana naga berguncang.',
          'The other seven raged; the sea was set ablaze, mountains were hurled and the dragon palace shook.'),
         ('Sejak itu orang bilang: delapan dewa menyeberang laut, masing-masing menunjukkan kesaktiannya. Setiap orang punya caranya sendiri.',
          'Ever since, people say: the eight immortals cross the sea, each showing their own power. Everyone has their own way.')]),
    ('yu_gong', ('Yu Gong Memindahkan Gunung', 'The Foolish Old Man Moves the Mountains'),
     ('Sumber: Liezi, bab Tang Wen', 'Source: the Liezi, chapter Tang Wen'), [
         ('Yu Gong berumur sembilan puluh tahun, dan rumahnya terhalang dua gunung, Taihang dan Wangwu.',
          'Yu Gong was ninety years old, and his house was blocked by two mountains, Taihang and Wangwu.'),
         ('Ia mengumpulkan keluarganya dan berkata: mari kita ratakan gunung ini supaya jalan ke selatan terbuka.',
          'He gathered his family and said: let us level these mountains so the road south is open.'),
         ('Mereka memikul tanah dan batu ke laut Bohai. Satu perjalanan pulang-pergi makan waktu setahun.',
          'They carried earth and stone to the Bohai Sea. One round trip took a whole year.'),
         ('Orang pintar dari tikungan sungai menertawakannya: kau sudah tua, sehelai rumput gunung pun tak bisa kau cabut.',
          'A clever man from the river bend laughed at him: you are old, you cannot even pull up a blade of mountain grass.'),
         ('Yu Gong menjawab: aku mati, anakku meneruskan; anakku punya cucu, cucuku punya anak. Gunung tidak bertambah tinggi.',
          'Yu Gong replied: when I die my son goes on; my son has grandsons, my grandsons have sons. The mountains grow no taller.'),
         ('Dewa ular yang memegang gunung mendengarnya dan ketakutan, lalu melapor ke Kaisar Langit.',
          'The snake god who held the mountains heard him and took fright, and reported to the Emperor of Heaven.'),
         ('Kaisar Langit tersentuh, dan mengutus dua raksasa memikul gunung itu pergi. Ketekunan memindahkan langit, bukan hanya gunung.',
          'The Emperor of Heaven was moved, and sent two giants to carry the mountains away. Perseverance moves heaven, not only mountains.')]),
    ('sarjana_rubah', ('Sarjana dan Rubah', 'The Scholar and the Fox'),
     ('Sumber: Pu Songling, Kisah Aneh dari Studio Liao (Liaozhai Zhiyi), kisah Yingning', 'Source: Pu Songling, Strange Tales from a Chinese Studio (Liaozhai Zhiyi), the tale of Yingning'), [
         ('Seorang sarjana muda melihat gadis yang memegang setangkai bunga plum dan tertawa tanpa henti di festival lentera.',
          'A young scholar saw a girl holding a sprig of plum blossom and laughing without end at the lantern festival.'),
         ('Ia jatuh sakit karena rindu, lalu mencarinya ke lembah di pegunungan barat, dan menemukan rumah penuh bunga.',
          'He fell ill with longing, then searched for her in a valley in the western hills, and found a house full of flowers.'),
         ('Gadis itu, Yingning, tertawa setiap kali ia bicara, dan neneknya yang sangat tua hanya tersenyum.',
          'The girl, Yingning, laughed whenever he spoke, and her very old grandmother only smiled.'),
         ('Ia membawanya pulang dan menikahinya. Tawanya menyembuhkan pertengkaran seisi rumah.',
          'He brought her home and married her. Her laughter healed every quarrel in the household.'),
         ('Suatu hari Yingning berhenti tertawa dan bercerita: ibunya seekor rubah, neneknya arwah yang membesarkannya.',
          'One day Yingning stopped laughing and confessed: her mother was a fox, and her grandmother the ghost who raised her.'),
         ('Ia meminta suaminya memindahkan makam neneknya yang terlantar ke samping makam keluarga.',
          'She asked her husband to move her grandmother\'s neglected grave beside the family tombs.'),
         ('Sejak itu Yingning tidak pernah tertawa lagi di depan orang, tetapi ia tetap baik hati. Yang aneh belum tentu jahat.',
          'After that Yingning never laughed in front of others again, but she stayed kind. What is strange is not always wicked.')]),
    ('kupu_kupu', ('Sepasang Kupu-kupu', 'The Butterfly Lovers'),
     ('Sumber: legenda Liang Shanbo dan Zhu Yingtai', 'Source: the legend of Liang Shanbo and Zhu Yingtai'), [
         ('Zhu Yingtai menyamar sebagai pemuda supaya bisa belajar di akademi Hangzhou.',
          'Zhu Yingtai disguised herself as a young man so she could study at the Hangzhou academy.'),
         ('Tiga tahun ia sekamar dengan Liang Shanbo, belajar, berdebat dan tertawa, dan Liang tidak pernah curiga.',
          'For three years she shared a room with Liang Shanbo, studying, arguing and laughing, and Liang never suspected.'),
         ('Saat berpisah ia memberi seribu isyarat: sepasang angsa, sepasang ikan. Liang yang lugu tidak mengerti satu pun.',
          'At parting she gave a thousand hints: a pair of geese, a pair of fish. Innocent Liang understood none of them.'),
         ('Ketika akhirnya ia sadar dan datang melamar, Yingtai sudah dijodohkan dengan putra keluarga Ma yang kaya.',
          'When he finally understood and came to propose, Yingtai had already been promised to the wealthy Ma family\'s son.'),
         ('Liang pulang dan meninggal karena patah hati.',
          'Liang went home and died of a broken heart.'),
         ('Pada hari pernikahan, tandu Yingtai lewat di depan makam Liang. Badai turun, makam terbelah, dan ia melompat masuk.',
          'On her wedding day, Yingtai\'s palanquin passed Liang\'s grave. A storm broke, the grave split open, and she leapt inside.'),
         ('Dari makam itu terbang sepasang kupu-kupu yang tidak pernah berpisah lagi. Jangan jodohkan orang dengan dompet, Bibi Mei.',
          'From the grave flew a pair of butterflies that never parted again. Do not match people by their purses, Auntie Mei.')]),
    ('penjara_iblis', ('Penjara Iblis di Bawah Gunung', 'The Demon Prison Beneath the Mountain'),
     ('Sumber: lore dunia ini (segel-segel kuno dan sarang iblis, DESIGN 34)', 'Source: the lore of this world (the ancient seals and demon nests, DESIGN 34)'), [
         ('Sebelum ada kota, para leluhur kultivator berperang dengan iblis selama sembilan musim dingin.',
          'Before there were towns, the ancestor cultivators fought the demons for nine winters.'),
         ('Mereka tidak bisa membunuh raja iblis, jadi mereka menguburnya, menumpuk gunung di atasnya dan menulis segel di batu.',
          'They could not kill the demon king, so they buried him, heaped a mountain over him and wrote seals in the stone.'),
         ('Setiap segel dijaga satu keluarga. Keluarga itu sudah lama lupa apa yang mereka jaga, tapi masih menyalakan dupa setiap bulan baru.',
          'Each seal was kept by one family. Those families have long forgotten what they guard, but still burn incense every new moon.'),
         ('Kalau kalian melihat tanah yang hangat di musim salju, atau burung yang tidak mau hinggap, jangan menggali.',
          'If you see ground that stays warm in the snow, or birds that will not land, do not dig.'),
         ('Kata guruku, segel itu retak setiap seratus tahun, dan yang keluar lebih dulu bukan raja, melainkan anak-anaknya.',
          'My master said the seals crack every hundred years, and what comes out first is not the king but his children.'),
         ('Gua-gua yang baru muncul di peta para pencari harta... ah, mungkin hanya kebetulan.',
          'The caves that newly appear on treasure hunters\' maps... ah, perhaps it is only chance.'),
         ('Tidurlah yang nyenyak. Yang menjaga segel bukan batu, tapi orang-orang yang masih ingat kenapa batu itu di sana.',
          'Sleep soundly. What guards a seal is not the stone, but the people who still remember why the stone is there.')]),
    ('sekte_awan', ('Sekte yang Turun dari Awan', 'The Sect That Came Down from the Clouds'),
     ('Sumber: lore dunia ini (istana langit dan sekte di atas awan, DESIGN 37)', 'Source: the lore of this world (the sky palaces and the sects above the clouds, DESIGN 37)'), [
         ('Di atas awan ada istana-istana yang tidak pernah menyentuh tanah. Para muridnya tidak pernah merasakan lumpur.',
          'Above the clouds stand palaces that never touch the ground. Their disciples have never felt mud.'),
         ('Suatu musim kemarau, sebuah sekte langit melihat ladang di bawahnya retak dan anak-anak mengemis air.',
          'One dry season, a sky sect saw the fields below cracking and children begging for water.'),
         ('Para tetua berkata: urusan fana bukan urusan kita. Tapi murid termuda melompat turun membawa kendi air roh.',
          'The elders said: mortal matters are not our matters. But the youngest disciple leapt down carrying a jar of spirit water.'),
         ('Ia menuang air itu ke sumur kering, dan sumur itu mengalir lagi tiga tahun tanpa henti.',
          'She poured the water into a dry well, and the well flowed again for three years without stopping.'),
         ('Sektenya mengusirnya karena melanggar aturan. Ia tinggal di desa itu, mengajar anak-anak bernapas dengan benar.',
          'Her sect cast her out for breaking the rules. She stayed in that village, teaching children to breathe properly.'),
         ('Desa itu menjadi kota, murid-muridnya menjadi sekte, dan sekte itu tidak pernah membangun istana di atas awan.',
          'The village became a town, her pupils became a sect, and that sect never built a palace above the clouds.'),
         ('Kalau kalian lihat gerbang sekte yang pintunya selalu terbuka untuk orang kota, mungkin itu sekte miliknya.',
          'If you see a sect gate whose doors are always open to townsfolk, perhaps it is hers.')]),
]


def isi_kisah():
    """Dialog kota42_lao_shuo: greeting, two menu pages (at most 9 choices per node), each tale as a chain of nodes of two
    lines, a Sumber line at the end. The index file bicara/kisah_lao_shuo.json lists the tales with their node keys."""
    did = 'kota42_lao_shuo'
    nodes = []
    page1, page2 = KISAH[:6], KISAH[6:]
    nodes.append(N('mulai', ('Duduk, duduk! Teh sudah dituang, kipas sudah terbuka. Mau dengar kisah apa malam ini?',
                             'Sit, sit! The tea is poured, the fan is open. Which tale will you hear tonight?'),
                   gestur='k:dongeng', prop='kipas_lipat', suara='kota42.lao_shuo_buka',
                   pilihan=[P(('Kisah-kisah lama, Kek.', 'The old tales, Grandpa.'), ke='menu1'),
                            P(('Kisah lain lagi.', 'Other tales.'), ke='menu2'),
                            P(('Kakek tahu tempat-tempat rahasia?', 'Do you know any secret places?'), ke='petunjuk'),
                            P(('Siapa Kakek sebenarnya?', 'Who are you really, Grandpa?'), ke='diri'),
                            P(PAMIT)]))
    nodes.append(N('menu1', ('Pilih saja. Semuanya benar, dan semuanya sedikit bohong.', 'Choose. They are all true, and all a little lies.'),
                   gestur='k:dongeng', pilihan=[P(k[1], ke=f'{k[0]}_1') for k in page1] + [P(('Yang lain.', 'Others.'), ke='menu2'), P(PAMIT)]))
    nodes.append(N('menu2', ('Ini kisah yang jarang kuceritakan. Dengarkan baik-baik.', 'These are tales I seldom tell. Listen well.'),
                   gestur='k:dongeng', pilihan=[P(k[1], ke=f'{k[0]}_1') for k in page2] + [P(('Yang tadi.', 'The earlier ones.'), ke='menu1'), P(PAMIT)]))
    index = []
    for kid, judul, sumber, lines in KISAH:
        if not 6 <= len(lines) <= 10:
            ERRORS.append(f'tale {kid}: needs 6-10 lines, has {len(lines)}')
        t(f'kultivator.kota42.kisah.{kid}.judul', judul)
        t(f'kultivator.kota42.kisah.{kid}.sumber', sumber)
        if not sumber[0].startswith('Sumber:') or not sumber[1].startswith('Source:'):
            ERRORS.append(f'tale {kid}: Sumber line must start with Sumber:/Source:')
        groups = [lines[i:i + 2] for i in range(0, len(lines), 2)]
        keys = []
        for gi, g in enumerate(groups, 1):
            text = (' '.join(x[0] for x in g), ' '.join(x[1] for x in g))
            last = gi == len(groups)
            nxt = [P(LANJUT, ke=f'{kid}_{gi + 1}')] if not last else [
                P(('Kisah lain, Kek!', 'Another tale, Grandpa!'), ke='menu1'),
                P(('Terima kasih, Kek. (beri dua keping)', 'Thank you, Grandpa. (give two coins)'), syarat=['nb 2'],
                  akibat=['bayar 2', 'nama_baik 1', 'klip k:beri_koin'], ke='terima_kasih'),
                P(PAMIT)]
            nodes.append(N(f'{kid}_{gi}', text, gestur='k:dongeng' if gi % 2 else 'k:deklamasi', prop='kipas_lipat', pilihan=nxt))
            keys.append(f'kultivator.cerita.{did}.{kid}_{gi}')
        index.append({'id': kid, 'judul': f'kultivator.kota42.kisah.{kid}.judul', 'sumber': f'kultivator.kota42.kisah.{kid}.sumber',
                      'baris': len(lines), 'simpul': keys})
    nodes.append(N('terima_kasih', ('Hoho, tehku jadi lebih manis malam ini. Datanglah lagi besok, kisahnya belum habis.',
                                    'Hoho, my tea is sweeter tonight. Come again tomorrow, the tales are not finished.'),
                   gestur='hormat', suara='kota42.lao_shuo_tutup', pilihan=[P(('Kisah satu lagi!', 'One more tale!'), ke='menu2'), P(PAMIT)]))
    nodes.append(N('petunjuk', ('Tempat rahasia? Hmm. Dengarkan kisah Penjara Iblis baik-baik, dan perhatikan gua mana yang tanahnya hangat '
                                'di musim salju. Lalu tanyakan pada pemburu di kedai teh, mereka lebih tahu dari peta.',
                                'Secret places? Hmm. Listen to the tale of the Demon Prison closely, and mark which caves keep warm ground '
                                'in the snow. Then ask the hunters in the teahouse, they know more than maps.'),
                   gestur='bisik', pilihan=[P(('Ceritakan kisah itu.', 'Tell me that tale.'), ke='penjara_iblis_1'), P(KEMBALI, ke='mulai')]))
    nodes.append(N('diri', ('Aku? Aku orang yang terlalu banyak mendengar. Dulu aku pencatat istana, lalu aku sadar kisah rakyat '
                            'lebih jujur daripada catatan istana. Sejak itu aku ikut Nyonya Hong Ling ke mana pun roda kereta berputar.',
                            'Me? I am a man who has heard too much. I was once a palace scribe, then I learned the people\'s tales '
                            'are more honest than the palace records. Since then I follow Madam Hong Ling wherever the cart wheels turn.'),
                   gestur='minum_teh', prop='cangkir', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]))
    D(did, 'tokoh:lao_shuo', nodes, prioritas=10)
    return index


# ================================================================================================ arcs 1-4 (42.10)
def alur_dua_keluarga():
    q = 'kota42_dua_keluarga'
    Q(q, 'tokoh:bibi_zhang', 'kota42', ('Dua Keluarga di Jalan Sumur', 'Two Families on Well Street'),
      ('Putra pembuat tahu dan putri pemilik kedai arak saling mencintai, tetapi dua keluarga mereka bermusuhan sejak kakek '
       'mereka berebut patok batas sumur.',
       'The tofu maker\'s son and the wine seller\'s daughter love each other, but their families have feuded since their '
       'grandfathers fought over the well\'s boundary stone.'), [
        T('gosip', ('Bibi Zhang berbisik tentang pertemuan rahasia di sumur saat subuh. Temui Li Ying, putri kedai arak.',
                    'Auntie Zhang whispers of secret meetings at the well at dawn. Find Li Ying, the wine shop\'s daughter.'),
          tujuan=['bicara tokoh:li_ying']),
        T('surat', ('Li Ying menitipkan sepucuk surat. Antarkan kepada Zhang Shan tanpa terlihat ayahnya.',
                    'Li Ying entrusted you with a letter. Deliver it to Zhang Shan without his father seeing.'),
          tujuan=['bendera kota42.dua.surat', 'bicara tokoh:zhang_shan']),
        T('larangan', ('Pasangan itu memohon restu. Bicaralah dengan Pak Tua Zhang, Juragan Li dan Bibi Mei sang mak comblang.',
                       'The couple begs for a blessing. Speak with Old Zhang, Boss Li and Auntie Mei the matchmaker.'),
          tujuan=['bendera kota42.dua.dengar_zhang', 'bendera kota42.dua.dengar_li', 'bendera kota42.dua.dengar_mei'],
          akibat_masuk=['kota42.gosip kota42_pasangan_sumur tokoh:zhang_shan tokoh:li_ying']),
        T('telusur', ('Asal permusuhan itu tidak jelas. Telusuri gosip tentang patok batas sumur sampai ke sumbernya, dan tanyakan '
                      'orang paling tua di Hunian Barat.',
                      'Nobody is sure how the feud began. Trace the gossip about the well\'s boundary stone back to its source, '
                      'and ask the oldest man in the West Residences.'),
          tujuan=['tanda kota42.telusur.kota42_batas_sumur', 'bicara tokoh:kakek_xiang'],
          akibat_masuk=['kota42.gosip kota42_batas_sumur tokoh:zhang_laotou tokoh:li_laoban']),
        T('putusan', ('Kau tahu kebenarannya: utang lama itu sudah dilunasi, kwitansinya jatuh ke dasar sumur. Tentukan jalannya: '
                      'damaikan keduanya, bantu mereka kawin lari ke Kota Air, atau biarkan saja.',
                      'You know the truth: the old debt was paid, and the receipt fell to the bottom of the well. Choose the way: '
                      'reconcile the families, help the lovers elope to the Water City, or leave it be.'),
          cabang=[(['bendera kota42.dua.damai'], 'damai'), (['bendera kota42.dua.lari'], 'lari')],
          batas_hari=10, gagal_ke='dibiarkan'),
        T('damai', ('Bawa kwitansi dari dasar sumur kepada Juragan Li.', 'Bring the receipt from the bottom of the well to Boss Li.'),
          tujuan=['antar kultivator:kwitansi_sumur tokoh:li_laoban'], ke='nikah'),
        T('nikah', ('Kedua keluarga berdamai. Hadiri pernikahan Zhang Shan dan Li Ying.',
                    'The families are reconciled. Attend the wedding of Zhang Shan and Li Ying.'),
          tujuan=['hadir_upacara nikah tokoh:zhang_shan'],
          akibat_masuk=['kota42.nikah tokoh:zhang_shan tokoh:li_ying'],
          akibat=['kota42.kesan tokoh:zhang_laotou 40', 'kota42.kesan tokoh:li_laoban 40', 'kota42.kesan tokoh:zhang_shan 40',
                  'kota42.kesan tokoh:li_ying 40', 'beri kultivator:kue_pengantin 3', 'nama_baik 4', 'kabar kota42_dua_damai pemain'],
          batas_hari=12, gagal_ke='nikah_lewat', ke='selesai',
          hasil=('Pernikahan dua keluarga Jalan Sumur dirayakan tiga hari tiga malam. Anak pertama mereka kelak diberi nama '
                 'kehormatan sesuai namamu.',
                 'The wedding of Well Street\'s two families was celebrated for three days and nights. Their first child will '
                 'one day bear an honorary name after yours.')),
        T('nikah_lewat', ('Pernikahan berlangsung tanpamu, tetapi kedua keluarga tetap berterima kasih.',
                          'The wedding took place without you, but both families are still grateful.'),
          tujuan=['hari 0'], akibat=['kota42.kesan tokoh:zhang_laotou 25', 'kota42.kesan tokoh:li_laoban 25',
                                     'beri kultivator:kue_pengantin 1', 'kabar kota42_dua_damai pemain'], ke='selesai',
          hasil=('Kau melewatkan pesta, tetapi sepotong kue pengantin disisihkan untukmu.',
                 'You missed the feast, but a piece of wedding cake was set aside for you.')),
        T('lari', ('Kawal Zhang Shan dan Li Ying ke Kota Air. Begal suka mengintai pasangan pelarian di jalan.',
                   'Escort Zhang Shan and Li Ying to the Water City. Bandits like to waylay eloping couples on the road.'),
          tujuan=['kawal tokoh:zhang_shan kota_tema/air'],
          batas_hari=6, gagal_ke='dibiarkan', arah='kota_tema/air'),
        T('lari_tiba', ('Mereka tiba di Kota Air dan menikah di bawah jembatan berlentera.',
                        'They reached the Water City and married under a lantern-lit bridge.'),
          tujuan=['hari 0'], akibat=['kota42.nikah tokoh:zhang_shan tokoh:li_ying', 'kota42.pindah tokoh:zhang_shan kota_tema/air',
                                     'kota42.pindah tokoh:li_ying kota_tema/air', 'kota42.kesan tokoh:zhang_shan 40',
                                     'kota42.kesan tokoh:li_ying 40', 'kota42.kesan tokoh:zhang_laotou -15',
                                     'kota42.kesan tokoh:li_laoban -15', 'kabar kota42_dua_lari pemain', 'quest_mulai kota42_zhuazhou_damai'],
          ke='selesai',
          hasil=('Pasangan itu hidup bahagia di Kota Air. Dua ayah di Jalan Sumur tidak saling bicara, tetapi diam-diam sama-sama '
                 'menanyakan kabar cucu yang belum lahir.',
                 'The couple live happily in the Water City. The two fathers on Well Street do not speak to each other, yet both '
                 'quietly ask about a grandchild not yet born.')),
        T('dibiarkan', ('Sepuluh hari berlalu. Li Ying dijodohkan dengan keponakan Juragan Shen, dan Zhang Shan mulai minum.',
                        'Ten days passed. Li Ying was promised to Master Shen\'s nephew, and Zhang Shan started drinking.'),
          tujuan=['hari 0'], akibat=['kota42.nikah tokoh:shen_ziyu tokoh:li_ying', 'kota42.kebiasaan tokoh:zhang_shan pemabuk',
                                     'kabar kota42_dua_pisah'], ke='tebus'),
        T('tebus', ('Zhang Shan menghabiskan malam di kedai. Mungkin secangkir teh penenang hati dan seorang teman bisa menariknya kembali.',
                    'Zhang Shan spends his nights at the tavern. Perhaps a cup of heart-calming tea and a friend can pull him back.'),
          tujuan=['antar kultivator:teh_penenang_hati tokoh:zhang_shan'],
          akibat=['kota42.kebiasaan tokoh:zhang_shan pemabuk hapus', 'kota42.kesan tokoh:zhang_shan 30', 'kota42.kesan tokoh:bibi_zhang 20'],
          ke='selesai',
          hasil=('Zhang Shan berhenti minum dan kembali menggiling kedelai sebelum subuh. Ia tidak lagi menimba air saat Li Ying lewat, '
                 'tetapi ia tersenyum lagi.',
                 'Zhang Shan stopped drinking and is back grinding soybeans before dawn. He no longer draws water when Li Ying '
                 'passes, but he smiles again.')),
    ])
    Q('kota42_zhuazhou_damai', 'tokoh:bibi_zhang', 'kota42', ('Cucu di Atas Tikar', 'A Grandchild on the Mat'),
      ('Pasangan Jalan Sumur punya bayi di Kota Air. Pada upacara zhuazhou, kedua kakek bisa berdamai bila ada yang membawa '
       'mereka ke sana.',
       'The Well Street couple have a baby in the Water City. At the zhuazhou ceremony the two grandfathers could make peace, '
       'if someone brings them there.'), [
        T('kakek_zhang', ('Bujuk Pak Tua Zhang untuk melihat cucunya.', 'Persuade Old Zhang to see his grandchild.'),
          tujuan=['bendera kota42.zhuazhou.zhang']),
        T('kakek_li', ('Bujuk Juragan Li untuk melihat cucunya.', 'Persuade Boss Li to see his grandchild.'),
          tujuan=['bendera kota42.zhuazhou.li']),
        T('upacara', ('Hadiri zhuazhou bayi itu di Kota Air bersama kedua kakek.',
                      'Attend the baby\'s zhuazhou in the Water City with both grandfathers.'),
          tujuan=['hadir_upacara lahir tokoh:zhang_shan'], arah='kota_tema/air',
          akibat=['kota42.kesan tokoh:zhang_laotou 30', 'kota42.kesan tokoh:li_laoban 30', 'beri kultivator:telur_merah 4',
                  'kabar kota42_zhuazhou_damai pemain'],
          hasil=('Bayi itu meraih kuas dan sebutir dadu sekaligus. Kedua kakek tertawa bersama untuk pertama kalinya dalam empat '
                 'puluh tahun, dan saling menuduh siapa yang mewariskan dadu itu.',
                 'The baby grabbed a brush and a die at once. The two grandfathers laughed together for the first time in forty '
                 'years, and accused each other of passing on the die.')),
    ])
    # ---- dialogs
    D('kota42_dua_bibi_zhang', 'tokoh:bibi_zhang', [
        N('mulai', ('Sst, sini sebentar. Kau orang baik, kan? Keponakanku Zhang Shan setiap subuh menimba air tepat saat putri '
                    'kedai arak lewat. Dua ember, satu sumur, dua hati. Dan kalau ayah mereka tahu, sumur ini bisa jadi medan perang.',
                    'Psst, come here a moment. You are a good sort, aren\'t you? My nephew Zhang Shan draws water every dawn just as '
                    'the wine shop girl passes. Two buckets, one well, two hearts. And if their fathers find out, this well becomes a battlefield.'),
          gestur='bisik', suara='kota42.gosip_bisik',
          pilihan=[P(('Aku akan bicara dengan gadis itu.', 'I will speak with the girl.'), akibat=['quest_mulai kota42_dua_keluarga'], ke='terima'),
                   P(('Kenapa ayah mereka bermusuhan?', 'Why do their fathers feud?'), ke='sebab'),
                   P(('Itu bukan urusanku.', 'That is not my business.'))]),
        N('sebab', ('Patok batas sumur, katanya. Kakek Li menggeser patok itu satu langkah, atau kakek Zhang tidak membayar utang, '
                    'tergantung siapa yang kau tanya. Empat puluh tahun dua keluarga lewat sumur yang sama tanpa saling menyapa.',
                    'The well\'s boundary stone, they say. Grandfather Li moved it one step, or grandfather Zhang never paid a debt, '
                    'depending on whom you ask. Forty years the two families have passed the same well without a greeting.'),
          gestur='bicara_tangan', pilihan=[P(('Aku akan bantu.', 'I will help.'), akibat=['quest_mulai kota42_dua_keluarga'], ke='terima'),
                                           P(PAMIT)]),
        N('terima', ('Li Ying biasanya di belakang kedai arak, menyaring arak sambil bernyanyi. Jangan bilang aku yang menyuruh, ya!',
                     'Li Ying is usually behind the wine shop, straining wine and singing. Don\'t say I sent you!'),
          gestur='k:gosok_dinding'),
    ], prioritas=40, syarat=['quest kota42_dua_keluarga baru', 'hari 2'], sekali=True)
    D('kota42_dua_li_ying', 'tokoh:li_ying', [
        N('mulai', ('Bibi Zhang yang menyuruhmu, kan? Wajahnya selalu merah kalau menyimpan rahasia. Ya... aku dan Zhang Shan. '
                    'Ayahku akan menghancurkan semua guci arak kalau tahu.',
                    'Auntie Zhang sent you, didn\'t she? Her face goes red whenever she keeps a secret. Yes... Zhang Shan and I. '
                    'My father would smash every wine jar if he knew.'),
          gestur='k:menepuk_jidat',
          pilihan=[P(('Aku bisa membantu.', 'I can help.'), ke='surat'), P(('Kenapa tidak bicara langsung dengan ayahmu?', 'Why not tell your father yourself?'), ke='ayah')]),
        N('ayah', ('Ayahku hanya mendengar dua hal: suara guci yang penuh dan kata "Zhang" yang ia ludahkan. Aku sudah mencoba.',
                   'My father hears only two things: the sound of a full jar and the word "Zhang", which he spits. I have tried.'),
          gestur='menangis', pilihan=[P(('Biar aku yang bantu.', 'Let me help.'), ke='surat')]),
        N('surat', ('Tolong berikan ini pada Zhang Shan. Jangan dibaca! Dan... katakan burung kayunya masih kusimpan.',
                    'Please give this to Zhang Shan. Don\'t read it! And... tell him I still keep his wooden bird.'),
          gestur='k:sodor_amplop', akibat=['bendera kota42.dua.surat']),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga tahap surat', '!bendera kota42.dua.surat'])
    D('kota42_dua_zhang_shan', 'tokoh:zhang_shan', [
        N('mulai', ('Surat? Dari... dari dia? (Ia membacanya tiga kali, lalu menyimpannya di dalam bajunya.) Ia ingin kami minta '
                    'restu. Ayahku akan melempar gilingan kedelai ke kepalaku.',
                    'A letter? From... from her? (He reads it three times, then tucks it inside his shirt.) She wants us to ask for '
                    'a blessing. My father will throw the soy grinder at my head.'),
          gestur='membaca',
          pilihan=[P(('Aku akan bicara dengan kedua ayah kalian.', 'I will speak with both your fathers.'), ke='restu'),
                   P(('Bibi Mei mungkin bisa membantu.', 'Auntie Mei might help.'), ke='restu')]),
        N('restu', ('Terima kasih. Kalau ada yang bisa melunakkan mereka, itu orang luar yang tidak punya dendam. Bibi Mei menjodohkan '
                    'setengah Hunian Barat, tapi ia mau dibayar mahal.',
                    'Thank you. If anyone can soften them, it is an outsider with no grudge. Auntie Mei matched half the West '
                    'Residences, but she likes to be paid well.'),
          gestur='hormat', akibat=['bendera kota42.dua.baca']),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga aktif', 'bendera kota42.dua.surat', '!bendera kota42.dua.baca'])
    D('kota42_dua_zhang_laotou', 'tokoh:zhang_laotou', [
        N('mulai', ('Anak keluarga Li?! Selama aku masih bisa menggiling kedelai, tidak ada keluarga Li yang menginjak halamanku. '
                    'Kakek mereka menggeser patok batas sumur satu langkah ke tanah kami!',
                    'A Li girl?! As long as I can grind soybeans, no Li sets foot in my yard. Their grandfather moved the well\'s '
                    'boundary stone one step into our land!'),
          gestur='marah_tunjuk', suara='kota42.marah', akibat=['bendera kota42.dua.dengar_zhang'],
          pilihan=[P(('Anda yakin itu yang terjadi?', 'Are you sure that is what happened?'), ke='yakin'), P(PAMIT)]),
        N('yakin', ('Ayahku yang bilang! ...Ayahku juga bilang ada utang, sepuluh tahil, entah siapa berutang pada siapa. Pokoknya keluarga Li salah.',
                    'My father said so! ...My father also spoke of a debt, ten taels, I am not sure who owed whom. Anyway the Lis are wrong.'),
          gestur='berdebat'),
    ], prioritas=55, syarat=['quest kota42_dua_keluarga aktif', 'bendera kota42.dua.surat', '!bendera kota42.dua.dengar_zhang'])
    D('kota42_dua_li_laoban', 'tokoh:li_laoban', [
        N('mulai', ('Putriku dan anak tukang tahu itu? Hah! Keluarga Zhang masih berutang sepuluh tahil perak kepada kakekku. Bayar dulu, '
                    'baru bicara soal menantu!',
                    'My daughter and that tofu boy? Hah! The Zhangs still owe my grandfather ten taels of silver. Pay that first, then '
                    'we talk of sons-in-law!'),
          gestur='marah_tunjuk', akibat=['bendera kota42.dua.dengar_li'],
          pilihan=[P(('Anda pernah melihat surat utangnya?', 'Have you ever seen the IOU?'), ke='surat'), P(PAMIT)]),
        N('surat', ('Surat? ...Tidak. Tapi kakekku tidak pernah bohong. Yah, kecuali soal umur araknya.',
                    'The IOU? ...No. But my grandfather never lied. Well, except about the age of his wine.'),
          gestur='k:garuk_kepala'),
    ], prioritas=55, syarat=['quest kota42_dua_keluarga aktif', 'bendera kota42.dua.surat', '!bendera kota42.dua.dengar_li'])
    D('kota42_dua_bibi_mei', 'tokoh:bibi_mei', [
        N('mulai', ('Zhang dan Li? Aduh, aduh. Almanakku bilang shio mereka cocok, tapi almanak tidak bisa mengalahkan dua ayah keras kepala. '
                    'Aku tidak mau namaku tercoreng pernikahan yang dibatalkan.',
                    'Zhang and Li? Oh my, oh my. My almanac says their signs match, but an almanac cannot beat two stubborn fathers. '
                    'I won\'t have my name stained by a cancelled wedding.'),
          gestur='k:bercermin', prop='k:almanak_merah', akibat=['bendera kota42.dua.dengar_mei'],
          pilihan=[P(('Apa yang membuat Bibi mau membantu?', 'What would make you help?'), ke='syarat'), P(PAMIT)]),
        N('syarat', ('Bukti, Nak. Kalau kau bisa membuktikan dendam itu tidak berdasar, aku akan menjodohkan mereka dengan pesta terbesar '
                     'tahun ini. Tanyakan Kakek Xiang di bawah pohon huai. Ia sudah hidup sebelum patok itu ditanam.',
                     'Proof, child. If you can prove the grudge is baseless, I will match them with the biggest feast of the year. Ask '
                     'Grandpa Xiang under the pagoda tree. He was alive before that stone was planted.'),
          gestur='tunjuk', suara='kota42.comblang'),
    ], prioritas=55, syarat=['quest kota42_dua_keluarga aktif', 'bendera kota42.dua.surat', '!bendera kota42.dua.dengar_mei'])
    D('kota42_dua_bibi_li', 'tokoh:bibi_li', [
        N('mulai', ('Patok batas sumur? Ibuku dulu bilang kakek Zhang meminjam sepuluh tahil dari kakek Li untuk membeli batu gilingan. '
                    'Tapi siapa yang bilang ke ibuku... hmm, Kakek Xiang, waktu ia masih muda dan banyak omong.',
                    'The boundary stone? My mother used to say grandfather Zhang borrowed ten taels from grandfather Li to buy a '
                    'millstone. But who told my mother... hmm, Grandpa Xiang, when he was young and talkative.'),
          gestur='k:intip', akibat=['peristiwa tanda kota42.telusur.kota42_batas_sumur'],
          pilihan=[P(('Lalu apa utang itu dibayar?', 'Was the debt ever paid?'), ke='bayar')]),
        N('bayar', ('Itu yang tidak pernah diceritakan siapa pun. Aneh, ya? Gosip yang bagus selalu kehilangan ujungnya.',
                    'That is what nobody ever tells. Strange, isn\'t it? Good gossip always loses its ending.'),
          gestur='bisik', suara='kota42.gosip_bisik'),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga tahap telusur'])
    D('kota42_dua_kakek_xiang', 'tokoh:kakek_xiang', [
        N('mulai', ('Patok batas itu? Aku ingat hari itu. Kakek Zhang membayar sepuluh tahil itu, lunas, di tepi sumur. Kakek Li menulis '
                    'kwitansi, lalu mereka minum terlalu banyak dan bertengkar soal siapa yang lebih kuat menimba.',
                    'That boundary stone? I remember the day. Grandfather Zhang paid the ten taels, in full, at the edge of the well. '
                    'Grandfather Li wrote a receipt, then they drank too much and quarrelled over who could draw water faster.'),
          gestur='catur', prop='papan_catur',
          pilihan=[P(('Di mana kwitansinya sekarang?', 'Where is the receipt now?'), ke='kwitansi')]),
        N('kwitansi', ('Jatuh ke sumur waktu mereka bergulat! Dimasukkan ke kotak timah, kalau tidak salah. Kalau sumurnya pernah dikuras, '
                       'tanya Bibi Wang. Suaminya tukang kuras sumur, dan ia menyimpan semua barang aneh yang ditemukan suaminya.',
                       'It fell into the well while they wrestled! In a tin box, if I remember. If the well was ever dredged, ask '
                       'Auntie Wang. Her husband dredges wells, and she keeps every odd thing he finds.'),
          gestur='tunjuk', suara='kota42.catur_skak'),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga tahap telusur'])
    D('kota42_dua_putusan', 'tokoh:bibi_wang', [
        N('mulai', ('Kotak timah dari dasar sumur? Aduh, aku kira itu kotak jimat hantu! Aku simpan di bawah ranjang sepuluh tahun, '
                    'takut membukanya. Isinya kertas tua bercap jempol. Mau kau apakan?',
                    'A tin box from the bottom of the well? Oh, I thought it was a ghost charm box! I kept it under my bed ten years, '
                    'too scared to open it. There is an old paper inside with a thumbprint. What will you do with it?'),
          gestur='takut_sembunyi',
          pilihan=[P(('(Damaikan) Aku akan menunjukkan kwitansi ini kepada Juragan Li.', '(Reconcile) I will show this receipt to Boss Li.'),
                     akibat=['beri kultivator:kwitansi_sumur', 'bendera kota42.dua.damai', 'quest_maju kota42_dua_keluarga'], ke='damai'),
                   P(('(Kawin lari) Lebih baik mereka pergi saja ke Kota Air.', '(Elope) Better they simply leave for the Water City.'),
                     akibat=['bendera kota42.dua.lari', 'quest_maju kota42_dua_keluarga'], ke='lari'),
                   P(('Aku perlu memikirkannya.', 'I need to think about it.'))]),
        N('damai', ('Bawalah ke Juragan Li. Kalau ia masih membantah, tunjukkan cap jempol kakeknya sendiri!',
                    'Take it to Boss Li. If he still argues, show him his own grandfather\'s thumbprint!'), gestur='k:kagum'),
        N('lari', ('Kawin lari? Seperti di kisah-kisah Lao Shuo! Aduh, jalannya penuh begal... hati-hati, ya!',
                   'Elope? Like in Lao Shuo\'s tales! Oh, the road is full of bandits... do be careful!'), gestur='kaget'),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga tahap putusan'])
    D('kota42_dua_lari_zhang_shan', 'tokoh:zhang_shan', [
        N('mulai', ('Li Ying sudah menunggu di gerbang dengan buntelannya. Kami siap. Tolong jangan biarkan begal menyentuhnya.',
                    'Li Ying is waiting at the gate with her bundle. We are ready. Please don\'t let bandits touch her.'),
          gestur='hormat', prop='k:buntelan', akibat=['kawal_mulai 60']),
    ], prioritas=60, syarat=['quest kota42_dua_keluarga tahap lari'])
    D('kota42_dua_li_laoban_damai', 'tokoh:li_laoban', [
        N('mulai', ('Kwitansi apa itu? ...Ini tulisan kakekku. Dan cap jempolnya. "Sepuluh tahil, lunas." Empat puluh tahun aku '
                    'membenci keluarga yang tidak berutang apa-apa padaku.',
                    'What receipt is that? ...This is my grandfather\'s hand. And his thumbprint. "Ten taels, paid in full." Forty '
                    'years I hated a family that owed me nothing.'),
          gestur='membaca', pilihan=[P(('Masih ada waktu untuk memperbaikinya.', 'There is still time to mend it.'), ke='maaf')]),
        N('maaf', ('Panggil Bibi Mei. Panggil tukang tahu keras kepala itu. Aku akan membuka guci arak dua puluh tahun, dan kalau ia '
                   'menolak minum, biar aku yang menangis duluan.',
                   'Call Auntie Mei. Call that stubborn tofu maker. I will open a twenty-year jar, and if he refuses to drink, I will '
                   'cry first.'),
          gestur='k:bersulang', akibat=['bendera kota42.dua.maaf']),
    ], prioritas=60, syarat=['quest kota42_dua_keluarga tahap nikah', '!bendera kota42.dua.maaf'])
    D('kota42_dua_tebus', 'tokoh:zhang_shan', [
        N('mulai', ('Hik... kau lagi. Mau mengejekku? Ia memakai sutra merah sekarang. Sutra Juragan Shen.',
                    'Hic... you again. Come to mock me? She wears red silk now. Master Shen\'s silk.'),
          gestur='k:jalan_mabuk', suara='kota42.mabuk'),
    ], prioritas=50, syarat=['quest kota42_dua_keluarga tahap tebus', '!punya kultivator:teh_penenang_hati'])
    for kakek, flag, line in (
            ('zhang_laotou', 'zhang', ('Cucu? Di Kota Air? ...Anak itu bodoh, kabur tanpa pamit. Tapi cucu tetap cucu. Baiklah, aku ikut, '
                                       'asal si tukang arak tidak duduk di sebelahku.',
                                       'A grandchild? In the Water City? ...The boy was a fool to run off. But a grandchild is a '
                                       'grandchild. Fine, I will come, as long as the wine seller doesn\'t sit next to me.')),
            ('li_laoban', 'li', ('Putriku melahirkan? Tanpa aku di sana? ...Ambilkan guci terbaikku. Aku tidak akan membiarkan tukang tahu '
                                 'itu memberi hadiah lebih besar dariku.',
                                 'My daughter gave birth? Without me there? ...Fetch my finest jar. I will not let that tofu maker '
                                 'give a bigger gift than mine.'))):
        D(f'kota42_zhuazhou_{kakek}', f'tokoh:{kakek}', [
            N('mulai', line, gestur='angguk_dengar', akibat=[f'bendera kota42.zhuazhou.{flag}']),
        ], prioritas=50, syarat=['quest kota42_zhuazhou_damai aktif', f'!bendera kota42.zhuazhou.{flag}'])


def alur_lao_geng():
    q = 'kota42_lentera_lao_geng'
    Q(q, 'tokoh:lao_geng', 'kota42', ('Lentera Terakhir Lao Geng', 'Lao Geng\'s Last Lantern'),
      ('Penjaga malam tertua ibu kota ingin menurunkan kentongannya kepada murid yang penakut sebelum lenteranya padam.',
       'The capital\'s oldest night watchman wants to pass his rattle to his timid apprentice before his lantern goes out.'), [
        T('ronda', ('Ikut Lao Geng meronda tiga malam, dari jam malam pertama sampai jam kelima.',
                    'Walk the night watch with Lao Geng for three nights, from the first watch to the fifth.'),
          tujuan=['ronda_geng tokoh:lao_geng x3']),
        T('ajari', ('Xiao Bang masih salah ketukan di jam ketiga. Ajari ia pola pukulan lima ketukan.',
                    'Xiao Bang still misses the beat at the third watch. Teach him the five-beat pattern.'),
          tujuan=['tanda kota42.irama_ronda', 'bicara tokoh:xiao_bang']),
        T('pensiun', ('Lao Geng memutuskan pensiun. Temui dia di pos jaga gerbang.',
                      'Lao Geng has decided to retire. Meet him at the gate watch post.'),
          tujuan=['bendera kota42.geng.pamit']),
        T('makam', ('Lao Geng wafat dalam tidurnya, kentongan di sisinya. Hadiri pemakamannya.',
                    'Lao Geng died in his sleep, his rattle beside him. Attend his funeral.'),
          akibat_masuk=['kota42.pensiun tokoh:lao_geng'], tujuan=['hadir_upacara makam tokoh:lao_geng'],
          batas_hari=25, gagal_ke='ziarah'),
        T('pewaris', ('Malam ini Xiao Bang meronda sendirian dengan gong gurunya. Temani dia di ronda pertamanya.',
                      'Tonight Xiao Bang walks the watch alone with his master\'s gong. Walk with him on his first round.'),
          akibat_masuk=['kota42.warisi tokoh:xiao_bang tokoh:lao_geng'], tujuan=['ronda_geng tokoh:xiao_bang'],
          akibat=['beri kultivator:kentongan_tua', 'nama_baik 5', 'kota42.kesan tokoh:xiao_bang 30', 'kabar kota42_ronda_baru pemain'],
          ke='selesai',
          hasil=('Suara Xiao Bang pecah di jam pertama dan mantap di jam kelima. Kentongan tua Lao Geng kini tergantung di pinggangmu, '
                 'dan setiap kali dibunyikan, penjaga malam di mana pun menoleh.',
                 'Xiao Bang\'s voice cracked at the first watch and was steady by the fifth. Lao Geng\'s old rattle now hangs at '
                 'your belt, and whenever it sounds, night watchmen everywhere turn their heads.')),
        T('ziarah', ('Kau melewatkan pemakamannya. Xiao Bang menunggumu di makam gurunya.',
                     'You missed the funeral. Xiao Bang waits for you at his master\'s grave.'),
          tujuan=['bicara tokoh:xiao_bang'], ke='pewaris'),
    ])
    D('kota42_geng_sapa', 'tokoh:lao_geng', [
        N('mulai', ('Hm? Kau berjalan seperti orang yang tidak takut gelap. Bagus. Kota ini tidur nyenyak karena ada yang tidak tidur. '
                    'Lima puluh tahun aku memukul kentongan ini.',
                    'Hm? You walk like someone unafraid of the dark. Good. This city sleeps soundly because someone stays awake. '
                    'Fifty years I have struck this rattle.'),
          gestur='k:pukul_kentongan', prop='k:kentongan', suara='kota42.ronda_geng1',
          pilihan=[P(('Boleh aku ikut meronda?', 'May I walk the watch with you?'), syarat=['quest kota42_lentera_lao_geng baru'],
                     akibat=['quest_mulai kota42_lentera_lao_geng'], ke='ikut'),
                   P(('Apa arti seruan jaga itu?', 'What do the watch calls mean?'), ke='seruan'),
                   P(('Siapa anak yang selalu di belakang Kakek?', 'Who is the boy always behind you?'), ke='murid'),
                   P(PAMIT)]),
        N('ikut', ('Bawa lentera. Jangan bicara saat aku berseru. Dan kalau melihat api, teriak lebih keras dariku.',
                   'Bring a lantern. Do not talk while I call. And if you see fire, shout louder than me.'), gestur='k:seru_jaga',
          suara='kota42.ronda_geng2'),
        N('seruan', ('"Langit kering, hati-hati api!" di jam pertama. "Tutup pintu, jaga pencuri!" di jam ketiga. Di jam kelima kami '
                     'berseru "Fajar datang, semua selamat". Kalau suatu malam aku tidak berseru, berarti aku sudah pergi.',
                     '"Dry sky, beware of fire!" at the first watch. "Shut your doors, mind the thieves!" at the third. At the fifth '
                     'we call "Dawn is coming, all is well". If one night I do not call, it means I have gone.'),
          gestur='k:seru_jaga', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('murid', ('Xiao Bang. Takut pada bayangannya sendiri, tapi tidak pernah absen. Keberanian bukan tidak takut, Nak. Keberanian '
                    'itu tetap berjalan sambil gemetar.',
                    'Xiao Bang. Afraid of his own shadow, yet never absent. Courage is not the absence of fear, child. Courage is '
                    'walking on while you shake.'),
          gestur='tepuk_bahu', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=10)
    D('kota42_geng_ajari', 'tokoh:xiao_bang', [
        N('mulai', ('Dok, dok... dok! Salah lagi! Guru bilang jam ketiga itu dua cepat satu lambat, tapi tanganku selalu ikut jantungku.',
                    'Tok, tok... tok! Wrong again! Master says the third watch is two quick, one slow, but my hands always follow my heart.'),
          gestur='k:pukul_kentongan', prop='k:kentongan', suara='kota42.ronda_geng4',
          pilihan=[P(('Ikuti napasmu, bukan jantungmu. Tarik, tarik, hembus.', 'Follow your breath, not your heart. In, in, out.'), ke='coba')]),
        N('coba', ('Tarik, tarik... hembus. Dok, dok... dok. Dok, dok... dok! Aku bisa! Jangan bilang Guru aku menangis tadi.',
                   'In, in... out. Tok, tok... tok. Tok, tok... tok! I can do it! Don\'t tell Master I cried earlier.'),
          gestur='tertawa', suara='kota42.xiao_bang', akibat=['peristiwa tanda kota42.irama_ronda']),
    ], prioritas=50, syarat=['quest kota42_lentera_lao_geng tahap ajari'])
    D('kota42_geng_pamit', 'tokoh:lao_geng', [
        N('mulai', ('Tanganku sudah tidak mau diam, dan mataku melihat dua lentera di tempat yang hanya ada satu. Sudah waktunya. '
                    'Xiao Bang akan memimpin ronda mulai bulan depan.',
                    'My hands will not keep still any more, and my eyes see two lanterns where there is one. It is time. Xiao Bang '
                    'will lead the watch from next month.'),
          gestur='k:pamit_lambai', suara='kota42.lao_geng_pamit',
          pilihan=[P(('Kakek sudah menjaga kota ini dengan baik.', 'You have guarded this city well, Grandpa.'), ke='terima')]),
        N('terima', ('Kota yang menjaga aku, Nak. Lima puluh tahun aku tidak pernah sendirian di jalan gelap. Kalau aku pergi nanti, '
                     'pukul kentongan ini sekali untukku.',
                     'It was the city that kept me, child. Fifty years I was never alone on a dark street. When I go, strike this '
                     'rattle once for me.'),
          gestur='hormat_dalam', akibat=['bendera kota42.geng.pamit']),
    ], prioritas=50, syarat=['quest kota42_lentera_lao_geng tahap pensiun'])
    D('kota42_geng_ziarah', 'tokoh:xiao_bang', [
        N('mulai', ('Kau datang. Guru minta kentongannya diberikan kepada orang yang menemaniku waktu aku masih salah ketukan. '
                    'Malam ini aku meronda sendiri. Maukah kau berjalan di sampingku, sekali saja?',
                    'You came. Master asked that his rattle go to the one who walked with me when I still missed the beat. Tonight '
                    'I walk the watch alone. Will you walk beside me, just once?'),
          gestur='k:sapu_makam'),
    ], prioritas=50, syarat=['quest kota42_lentera_lao_geng tahap ziarah'])


def alur_utang():
    q = 'kota42_utang_tiga_generasi'
    Q(q, 'tokoh:wu_niangzi', 'kota42', ('Utang Tiga Generasi', 'A Debt of Three Generations'),
      ('Kakek keluarga Wu meminjam lima tahil dari keluarga Shen. Tiga generasi kemudian, bunganya sudah menjadi dua ratus.',
       'The Wu family\'s grandfather borrowed five taels from the Shen family. Three generations later, the interest has grown to two hundred.'), [
        T('minta', ('Nyonya Wu meminta tolong. Dengarkan cerita suaminya, Wu Dalang, di warung bakmi.',
                    'Madam Wu asks for help. Hear her husband Wu Dalang\'s story at the noodle stall.'),
          tujuan=['bicara tokoh:wu_dalang']),
        T('surat', ('Bawa surat utang kakek Wu kepada Panitera Qiu di Yamen untuk dihitung ulang.',
                    'Take grandfather Wu\'s IOU to Clerk Qiu at the Yamen to have it recalculated.'),
          tujuan=['antar kultivator:surat_utang tokoh:qiu_panitera'], arah=''),
        T('pilih', ('Bunganya melanggar batas hukum. Lunasi sendiri, bawa perkara ke Hakim Bao, atau tanam modal di warung bakmi.',
                    'The interest breaks the legal limit. Pay it yourself, take the case to Judge Bao, or invest in the noodle stall.'),
          cabang=[(['bendera kota42.utang.lunas'], 'lunas'), (['bendera kota42.utang.sidang'], 'sidang'), (['bendera kota42.utang.modal'], 'modal')]),
        T('lunas', ('Bayarkan utang keluarga Wu kepada Juragan Shen di Rumah Uang.', 'Pay the Wu family\'s debt to Master Shen at the Counting House.'),
          tujuan=['tanda kota42.utang.bayar'], ke='lunas_akhir'),
        T('lunas_akhir', ('Utang lunas.', 'The debt is paid.'), tujuan=['hari 0'],
          akibat=['kota42.kesan tokoh:wu_dalang 35', 'kota42.kesan tokoh:wu_niangzi 35', 'kota42.kesan tokoh:shen_wan 5',
                  'beri kultivator:hidangan/mi_panjang_umur 3', 'kabar kota42_utang_lunas pemain'], ke='selesai',
          hasil=('Keluarga Wu membakar surat utang itu di tungku warung. Setiap kali kau datang, mangkukmu berisi mi dua kali lebih panjang.',
                 'The Wu family burned the IOU in the stall\'s stove. Whenever you come, your bowl holds noodles twice as long.')),
        T('sidang', ('Bawa surat utang yang sudah dihitung ulang kepada Hakim Bao.', 'Take the recalculated IOU to Judge Bao.'),
          tujuan=['antar kultivator:surat_utang tokoh:bao_qing'], ke='sidang_akhir'),
        T('sidang_akhir', ('Hakim Bao memutus perkara.', 'Judge Bao rules on the case.'), tujuan=['hari 0'],
          akibat=['kota42.kesan tokoh:wu_dalang 40', 'kota42.kesan tokoh:wu_niangzi 40', 'kota42.kesan tokoh:shen_wan -50',
                  'nama_baik 3', 'kabar kota42_utang_sidang pemain'], ke='selesai',
          hasil=('Hakim Bao menghapus bunga haram dan mendenda Juragan Shen dua kali lipat. Shen Wan tidak lagi tersenyum padamu, '
                 'tetapi separuh pasar mengangguk hormat saat kau lewat.',
                 'Judge Bao struck out the unlawful interest and fined Master Shen double. Shen Wan no longer smiles at you, but '
                 'half the market nods respectfully as you pass.')),
        T('modal', ('Tanam modal di warung bakmi Wu dan tunggu labanya melunasi utang.',
                    'Invest in the Wu noodle stall and wait for the profit to clear the debt.'),
          tujuan=['bendera kota42.utang.modal_bayar', 'tunggu 7'], ke='modal_akhir'),
        T('modal_akhir', ('Laba warung melunasi utang.', 'The stall\'s profit clears the debt.'), tujuan=['hari 0'],
          akibat=['kota42.saham tokoh:wu_dalang 40', 'kota42.kesan tokoh:wu_dalang 30', 'kota42.kesan tokoh:wu_niangzi 30',
                  'kabar kota42_utang_modal pemain'], ke='selesai',
          hasil=('Dengan kuali baru dan papan nama baru, warung bakmi Wu ramai sampai larut. Utangnya lunas dalam seminggu, dan empat '
                 'dari sepuluh keping laba kini milikmu.',
                 'With a new wok and a new signboard, the Wu noodle stall is busy until late. The debt was cleared in a week, and '
                 'four coins in ten of its profit are now yours.')),
    ])
    D('kota42_utang_minta', 'tokoh:wu_niangzi', [
        N('mulai', ('Tuan... maaf mengganggu. Setiap bulan anak buah Juragan Shen datang menagih bunga, dan setiap bulan utang kami '
                    'malah bertambah. Suamiku terlalu malu untuk bicara. Aku tidak.',
                    'Excuse me... sorry to bother you. Every month Master Shen\'s men come to collect interest, and every month our '
                    'debt grows. My husband is too ashamed to speak. I am not.'),
          gestur='hormat', pilihan=[P(('Ceritakan dari awal.', 'Tell me from the beginning.'), akibat=['quest_mulai kota42_utang_tiga_generasi'], ke='awal'),
                                    P(('Aku tidak bisa membantu sekarang.', 'I cannot help right now.'))]),
        N('awal', ('Kakek suamiku meminjam lima tahil untuk membuka warung ini. Lima tahil! Sekarang Juragan Shen bilang dua ratus. '
                   'Tanyakan suamiku, ia menyimpan surat utangnya di bawah papan kuali.',
                   'My husband\'s grandfather borrowed five taels to open this stall. Five taels! Now Master Shen says two hundred. '
                   'Ask my husband, he keeps the IOU under the wok board.'),
          gestur='bicara_tangan'),
    ], prioritas=40, syarat=['quest kota42_utang_tiga_generasi baru', 'hari 3'], sekali=True)
    D('kota42_utang_wu_dalang', 'tokoh:wu_dalang', [
        N('mulai', ('Istriku cerita padamu? (Ia menarik napas, mengelap tangan yang bertepung.) Ini suratnya. Kakek menulisnya waktu '
                    'aku belum lahir: "bunga tiga per sepuluh setiap tahun, ditambahkan pada pokok". Ayahku membayar, aku membayar, '
                    'dan anakku nanti juga akan membayar.',
                    'My wife told you? (He sighs and wipes his floury hands.) Here is the paper. Grandfather wrote it before I was born: '
                    '"interest three in ten each year, added to the principal". My father paid, I pay, and my son will pay too.'),
          gestur='k:menimbang', prop='k:surat_utang', akibat=['beri kultivator:surat_utang'],
          pilihan=[P(('Biar Panitera Yamen menghitungnya ulang.', 'Let the Yamen clerk recalculate it.'), ke='yamen')]),
        N('yamen', ('Panitera Qiu? Katanya ia satu-satunya pejabat yang tidak bisa dibeli. Semoga itu benar.',
                    'Clerk Qiu? They say he is the only official who cannot be bought. I hope that is true.'), gestur='hormat'),
    ], prioritas=50, syarat=['quest kota42_utang_tiga_generasi tahap surat', '!punya kultivator:surat_utang', '!bendera kota42.utang.dihitung'])
    D('kota42_utang_qiu', 'tokoh:qiu_panitera', [
        N('mulai', ('(Ia membaca, mencelup kuas, menghitung di sempoa.) Bunga berbunga, tiga per sepuluh setahun. Hukum kota membatasi '
                    'bunga tiga puluh enam per seratus dan melarang bunga ditambahkan pada pokok. Keluarga Wu sudah membayar empat kali '
                    'lipat utang aslinya.',
                    '(He reads, dips his brush, works the abacus.) Interest on interest, three in ten a year. City law caps interest at '
                    'thirty-six per hundred and forbids adding it to the principal. The Wu family has already paid four times the '
                    'original loan.'),
          gestur='hitung_sempoa', prop='sempoa', akibat=['bendera kota42.utang.dihitung', 'beri kultivator:surat_utang'],
          pilihan=[P(('Jadi apa yang bisa dilakukan?', 'So what can be done?'), ke='pilihan')]),
        N('pilihan', ('Tiga jalan. Lunasi sisanya supaya urusan selesai tanpa ribut. Bawa ke Hakim Bao, dan Juragan Shen akan kehilangan '
                      'muka dan uang. Atau buat warung itu cukup untung untuk membayar sendiri, kalau kau punya modal dan sabar.',
                      'Three roads. Pay the rest so it ends without fuss. Take it to Judge Bao, and Master Shen loses face and money. '
                      'Or make the stall profitable enough to pay for itself, if you have capital and patience.'),
          gestur='bicara_tangan',
          pilihan=[P(('(Lunasi) Aku akan membayar sisanya.', '(Pay) I will pay the rest.'), akibat=['bendera kota42.utang.lunas', 'quest_maju kota42_utang_tiga_generasi']),
                   P(('(Sidang) Bawa ke Hakim Bao.', '(Court) Take it to Judge Bao.'), akibat=['bendera kota42.utang.sidang', 'quest_maju kota42_utang_tiga_generasi']),
                   P(('(Modal) Aku akan menanam modal di warung Wu.', '(Invest) I will invest in the Wu stall.'), akibat=['bendera kota42.utang.modal', 'quest_maju kota42_utang_tiga_generasi'])]),
    ], prioritas=50, syarat=['quest kota42_utang_tiga_generasi tahap pilih'])
    D('kota42_utang_modal', 'tokoh:wu_dalang', [
        N('mulai', ('Modal? Dengan dua belas keping kali pitamu kami bisa membeli kuali besi baru dan papan nama. Kuali baru, kuah lebih '
                    'cepat mendidih, pelanggan tidak perlu menunggu.',
                    'Capital? With twelve coins times your band we could buy a new iron wok and a signboard. New wok, faster broth, '
                    'no waiting customers.'),
          gestur='bicara_tangan',
          pilihan=[P(('(Bayar) Ini modalnya.', '(Pay) Here is the capital.'), syarat=['nb_pita 12'],
                     akibat=['bayar_pita 12', 'bendera kota42.utang.modal_bayar'], ke='terima', alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('Nanti saja.', 'Later.'))]),
        N('terima', ('Besok papan namanya sudah tergantung. Tunggu saja seminggu, Tuan akan lihat antreannya!',
                     'Tomorrow the sign will be up. Just wait a week, you will see the queue!'), gestur='k:beri_koin'),
    ], prioritas=50, syarat=['quest kota42_utang_tiga_generasi tahap modal', '!bendera kota42.utang.modal_bayar'])
    D('kota42_utang_shen', 'tokoh:shen_wan', [
        N('mulai', ('Utang keluarga Wu? Seratus delapan puluh enam tahil, empat qian. Tapi untuk tamu terhormat seperti Tuan, '
                    'cukup dua puluh kali pita. Aku orang yang murah hati, kan?',
                    'The Wu family\'s debt? One hundred eighty-six taels, four qian. But for an honoured guest like you, twenty times '
                    'your band will do. I am a generous man, am I not?'),
          gestur='k:hitung_koin',
          pilihan=[P(('(Bayar) Ini.', '(Pay) Here.'), syarat=['nb_pita 20'], akibat=['bayar_pita 20', 'peristiwa tanda kota42.utang.bayar'],
                     ke='lunas', alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('Nanti.', 'Later.'))]),
        N('lunas', ('Senang berbisnis dengan Tuan. Surat utangnya boleh dibakar. Jangan lupa, Rumah Uang selalu terbuka untuk Tuan.',
                    'A pleasure doing business. You may burn the IOU. Remember, the Counting House is always open to you.'),
          gestur='hormat'),
    ], prioritas=50, syarat=['quest kota42_utang_tiga_generasi tahap lunas'])


def alur_seribu_jari():
    q = 'kota42_seribu_jari'
    Q(q, 'tokoh:bibi_wang', 'kota42', ('Pasar Hantu dan Seribu Jari', 'The Ghost Market and the Thousand Fingers'),
      ('Copet-copet Kumuh bekerja untuk satu orang. Jejaknya mengarah ke Pasar Hantu dan Tuan Lentera Hijau.',
       'The Slum pickpockets work for one man. The trail leads to the Ghost Market and Mister Green Lantern.'), [
        T('korban', ('Dompet Bibi Wang dicopet di pasar. Tangkap seorang copet.', 'Auntie Wang\'s purse was picked at the market. Catch a pickpocket.'),
          tujuan=['kejar_copet *']),
        T('telusur', ('Copet itu menyebut "Seribu Jari". Telusuri gosip tentangnya sampai ke sumbernya.',
                      'The pickpocket mentioned "Thousand Fingers". Trace the gossip about him to its source.'),
          akibat_masuk=['kota42.gosip kota42_seribu_jari'], tujuan=['tanda kota42.telusur.kota42_seribu_jari']),
        T('sandi', ('Semua jejak berujung di Pasar Hantu. Cari sandi malam ini, lalu temui Tuan Lentera Hijau saat pasar buka.',
                    'Every trail ends at the Ghost Market. Find tonight\'s password, then meet Mister Green Lantern when the market opens.'),
          tujuan=['bendera kota42.jari.sandi', 'bicara tokoh:lentera_hijau']),
        T('sarang', ('Lentera Hijau menunjuk sebuah gudang tua di Kumuh. Hadapi Shi Xiaoqian, si Tikus Seribu Jari.',
                     'Green Lantern pointed to an old warehouse in the Slums. Confront Shi Xiaoqian, the Thousand-Finger Rat.'),
          cabang=[(['bendera kota42.jari.serah'], 'serah'), (['bendera kota42.jari.informan'], 'informan'), (['bendera kota42.jari.insaf'], 'insaf')]),
        T('serah', ('Serahkan Shi Xiaoqian kepada Hakim Bao.', 'Hand Shi Xiaoqian over to Judge Bao.'),
          tujuan=['bicara tokoh:bao_qing'],
          akibat=['nama_baik 4', 'kota42.calon peran:anak kantor', 'beri kultivator:topeng_pasar_hantu', 'kabar kota42_jari_serah pemain'],
          ke='selesai',
          hasil=('Shi Xiaoqian dicambuk dan dibuang ke tambang garam. Anak-anaknya dibawa ke kuil, dan seorang di antaranya, yang '
                 'jarinya bisa membuat koin melayang, muncul di Kantor Rekrutmen.',
                 'Shi Xiaoqian was flogged and sent to the salt mines. His children were taken to the temple, and one of them, whose '
                 'fingers make coins float, turned up at the Recruitment Office.')),
        T('informan', ('Shi Xiaoqian kini mata dan telingamu.', 'Shi Xiaoqian is now your eyes and ears.'), tujuan=['hari 0'],
          akibat=['bendera kota42.informan_seribu_jari', 'beri kultivator:topeng_pasar_hantu', 'kabar kota42_jari_informan'], ke='selesai',
          hasil=('Copet kota ini tidak lagi menyentuh sakumu, dan setiap senja seorang anak menyelipkan secarik kertas: di mana Pasar '
                 'Hantu buka malam itu.',
                 'This city\'s pickpockets no longer touch your pockets, and every dusk a child slips you a scrap of paper: where the '
                 'Ghost Market opens that night.')),
        T('insaf', ('Modali Shi Xiaoqian membuka toko kunci.', 'Fund Shi Xiaoqian to open a locksmith\'s shop.'),
          tujuan=['bendera kota42.jari.modal'],
          akibat=['kota42.saham tokoh:shi_xiaoqian 30', 'kota42.kesan tokoh:shi_xiaoqian 40', 'beri kultivator:topeng_pasar_hantu',
                  'nama_baik 2', 'kabar kota42_jari_insaf pemain'], ke='selesai',
          hasil=('Toko kunci "Seribu Jari" buka di tepi Kumuh. Tangan yang dulu membuka kantong orang kini membuka gembok yang kuncinya '
                 'hilang, dan anak-anak di belakangnya belajar mengikir besi.',
                 'The "Thousand Fingers" lock shop opened at the edge of the Slums. Hands that once opened other people\'s purses now '
                 'open padlocks whose keys are lost, and the children behind him learn to file iron.')),
    ])
    D('kota42_jari_mulai', 'tokoh:bibi_wang', [
        N('mulai', ('Copet! Copet! Aduh, dompetku! Lima... tidak, sepuluh anak menabrakku sekaligus! Uang belanja sebulan!',
                    'Thief! Thief! Oh, my purse! Five... no, ten children bumped me at once! A whole month\'s shopping money!'),
          gestur='kaget', suara='kota42.copet_teriak',
          pilihan=[P(('Aku akan menangkap mereka.', 'I will catch them.'), akibat=['quest_mulai kota42_seribu_jari'], ke='kejar'),
                   P(('Tenang, Bibi. Ke arah mana mereka lari?', 'Calm down, Auntie. Which way did they run?'), akibat=['quest_mulai kota42_seribu_jari'], ke='kejar')]),
        N('kejar', ('Ke gang ikan! Semua copet kota ini lari ke gang ikan, lalu menghilang seperti asap. Orang bilang mereka kerja untuk '
                    'satu orang. "Seribu Jari", begitu namanya dibisikkan.',
                    'Down fish alley! Every pickpocket in this city runs down fish alley, then vanishes like smoke. People say they '
                    'work for one man. "Thousand Fingers", that is the name they whisper.'), gestur='tunjuk'),
    ], prioritas=40, syarat=['quest kota42_seribu_jari baru', 'hari 4', 'acak 50'], sekali=True)
    D('kota42_jari_telusur', 'tokoh:bibi_li', [
        N('mulai', ('Seribu Jari? Aku dengar dari tukang ikan, yang dengar dari tukang kuras sumur, yang... sudahlah. Pokoknya semua '
                    'barang curian dijual lagi di Pasar Hantu. Kalau mau tahu lebih, tanya Tabib Hu Lu. Ia langganan di sana.',
                    'Thousand Fingers? I heard it from the fishmonger, who heard it from the well-dredger, who... never mind. Anyway, '
                    'all stolen goods are sold again at the Ghost Market. If you want more, ask Hu Lu the travelling physician. He is a regular there.'),
          gestur='k:intip', akibat=['peristiwa tanda kota42.telusur.kota42_seribu_jari']),
    ], prioritas=50, syarat=['quest kota42_seribu_jari tahap telusur'])
    D('kota42_jari_hu_lu', 'tokoh:hu_lu', [
        N('mulai', ('Sandi Pasar Hantu? Ssst! Murah, murah: dua keping kali pitamu, dan aku lupa siapa yang bertanya.',
                    'The Ghost Market password? Shh! Cheap, cheap: two coins times your band, and I forget who asked.'),
          gestur='bisik',
          pilihan=[P(('(Bayar) Baiklah.', '(Pay) Fine.'), syarat=['nb_pita 2'], akibat=['bayar_pita 2', 'bendera kota42.jari.sandi'], ke='sandi',
                     alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('(Ancam) Atau aku bilang ke semua orang pilmu tepung beras.', '(Threaten) Or I tell everyone your pills are rice flour.'),
                     syarat=['nama_baik 30'], akibat=['bendera kota42.jari.sandi'], ke='sandi', alasan=('Ia tidak takut padamu.', 'He is not afraid of you.')),
                   P(PAMIT)]),
        N('sandi', ('Malam ini: "Bulan tidak punya bayangan." Jawabannya: "Karena ia sendiri bayangan matahari." Jangan salah ucap, '
                    'Lentera Hijau tidak suka orang bodoh.',
                    'Tonight: "The moon has no shadow." The answer: "Because it is itself the sun\'s shadow." Don\'t get it wrong, Green '
                    'Lantern does not like fools.'),
          gestur='bisik', suara='kota42.sandi_malam'),
    ], prioritas=50, syarat=['quest kota42_seribu_jari tahap sandi', '!bendera kota42.jari.sandi'])
    D('kota42_jari_lentera', 'tokoh:lentera_hijau', [
        N('mulai', ('(Suara berbisik dari balik topeng kertas.) Bulan tidak punya bayangan...',
                    '(A whisper from behind a paper mask.) The moon has no shadow...'),
          gestur='k:intip', suara='kota42.lentera_hijau',
          pilihan=[P(('Karena ia sendiri bayangan matahari.', 'Because it is itself the sun\'s shadow.'), syarat=['bendera kota42.jari.sandi'], ke='jawab',
                     alasan=('Kau tidak tahu sandinya.', 'You do not know the password.')),
                   P(('Aku mencari Seribu Jari.', 'I am looking for Thousand Fingers.'), ke='usir')]),
        N('usir', ('Pasar ini tidak mengenal orang yang tidak mengenal malam. Pergilah sebelum lenteraku padam.',
                   'This market does not know those who do not know the night. Leave before my lantern goes out.'), gestur='k:intip'),
        N('jawab', ('Tikus selalu bersarang di gudang yang paling sepi. Gudang garam tua di ujung Kumuh. Dan tamu... yang kau cari '
                    'tidak sejahat yang kau kira. Ia hanya lebih lapar.',
                    'Rats always nest in the emptiest warehouse. The old salt store at the end of the Slums. And guest... the one you seek '
                    'is not as wicked as you think. Only hungrier.'),
          gestur='tunjuk'),
    ], prioritas=50, syarat=['quest kota42_seribu_jari tahap sandi'])
    D('kota42_jari_sarang', 'tokoh:shi_xiaoqian', [
        N('mulai', ('Hebat, hebat. Lentera Hijau menjualku, ya? (Belasan anak bersembunyi di balik karung garam.) Mereka makan dua kali '
                    'sehari karena aku. Kota ini memberi mereka apa? Cambuk.',
                    'Bravo, bravo. Green Lantern sold me, eh? (A dozen children hide behind the salt sacks.) They eat twice a day because '
                    'of me. What does this city give them? The whip.'),
          gestur='k:hitung_koin',
          pilihan=[P(('(Serahkan) Hukum tetap hukum. Kau ikut ke Yamen.', '(Hand over) The law is the law. You are coming to the Yamen.'),
                     akibat=['bendera kota42.jari.serah', 'kawal_mulai 30', 'quest_maju kota42_seribu_jari'], ke='serah'),
                   P(('(Informan) Bekerjalah untukku. Copetmu berhenti mengincarku, dan kau jadi telingaku.',
                      '(Informant) Work for me. Your pickpockets leave me alone, and you become my ears.'),
                     akibat=['bendera kota42.jari.informan', 'quest_maju kota42_seribu_jari'], ke='informan'),
                   P(('(Insaf) Tanganmu terlalu berharga untuk mencopet. Pernah terpikir membuka toko kunci?',
                      '(Reform) Your hands are too valuable for picking pockets. Ever thought of opening a lock shop?'),
                     akibat=['bendera kota42.jari.insaf', 'quest_maju kota42_seribu_jari'], ke='insaf')]),
        N('serah', ('Hah. Setidaknya beri mereka makan sebelum kau pergi. Itu saja.', 'Hah. At least feed them before you go. That is all.'),
          gestur='berlutut'),
        N('informan', ('Kau lebih licik dari kelihatannya. Baiklah. Mulai hari ini, kantongmu tidak akan pernah lebih ringan di kota ini.',
                       'You are slier than you look. Very well. From today, your purse will never be lighter in this city.'), gestur='tertawa'),
        N('insaf', ('Toko kunci? (Ia tertawa, lalu berhenti.) Ayahku tukang kunci, sebelum ia mati di penjara utang. Kalau kau serius... '
                    'aku perlu modal.',
                    'A lock shop? (He laughs, then stops.) My father was a locksmith, before he died in debtors\' prison. If you are serious... '
                    'I need capital.'), gestur='k:garuk_kepala'),
    ], prioritas=50, syarat=['quest kota42_seribu_jari tahap sarang'])
    D('kota42_jari_modal', 'tokoh:shi_xiaoqian', [
        N('mulai', ('Lima belas keping kali pitamu untuk kikir, landasan kecil dan sewa sebulan. Aku tidak akan kabur. Aku juga tidak '
                    'akan berterima kasih terlalu keras.',
                    'Fifteen coins times your band for files, a small anvil and a month\'s rent. I will not run. I will not thank you '
                    'too loudly either.'),
          gestur='bicara_tangan',
          pilihan=[P(('(Bayar)', '(Pay)'), syarat=['nb_pita 15'], akibat=['bayar_pita 15', 'bendera kota42.jari.modal'], ke='terima',
                     alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('Nanti.', 'Later.'))]),
        N('terima', ('(Ia menggenggam kantong itu lama sekali.) ...Toko pertama di Kumuh yang tidak menjual barang curian. Bayangkan itu.',
                     '(He grips the purse for a long time.) ...The first shop in the Slums that sells nothing stolen. Imagine that.'),
          gestur='hormat'),
    ], prioritas=55, syarat=['quest kota42_seribu_jari tahap insaf', '!bendera kota42.jari.modal'])
    D('kota42_jari_bao', 'tokoh:bao_qing', [
        N('mulai', ('Shi Xiaoqian, "Tikus Seribu Jari". Tiga puluh laporan copet bertanda jarinya. Hukum menghukum tangan yang mencuri. '
                    'Tetapi anak-anak itu tidak dihukum karena lapar. Mereka akan dibawa ke kuil.',
                    'Shi Xiaoqian, the "Thousand-Finger Rat". Thirty pickpocket reports bearing his touch. The law punishes the hand '
                    'that steals. But those children will not be punished for hunger. They will be taken to the temple.'),
          gestur='k:palu_putusan', suara='kota42.putusan'),
    ], prioritas=60, syarat=['quest kota42_seribu_jari tahap serah'])


# ================================================================================================ arcs 5-10 (42.10)
def alur_akar_langit():
    q = 'kota42_akar_langit'
    Q(q, 'tokoh:guru_zhou', 'kota42', ('Anak Akar Langit', 'The Child of Heavenly Roots'),
      ('Seorang gadis kecil dari Kumuh membuat daun melayang saat tertawa. Seorang perekrut sekte sudah menawar ayahnya.',
       'A little girl from the Slums makes leaves float when she laughs. A sect recruiter has already made her father an offer.'), [
        T('daun', ('Guru Zhou bercerita tentang daun yang melayang. Temui Xiao Yu di Kumuh.',
                   'Master Zhou spoke of floating leaves. Find Xiao Yu in the Slums.'),
          tujuan=['bicara tokoh:xiao_yu']),
        T('tawar', ('Kakak Senior Gao menawarkan uang kepada ayah Xiao Yu. Bicaralah dengan Yu Laosan dan dengan Gao sendiri.',
                    'Senior Brother Gao has offered money to Xiao Yu\'s father. Speak with Yu Laosan and with Gao himself.'),
          tujuan=['bendera kota42.akar.dengar_ayah', 'bendera kota42.akar.dengar_gao']),
        T('cara', ('Ada tiga jalan untuk menahan tawaran Gao: lunasi utang judi ayahnya, bujuk ibunya, atau kalahkan Gao dalam tanding ramah.',
                   'There are three ways to hold off Gao\'s offer: pay the father\'s gambling debt, win over the mother, or beat Gao in a friendly bout.'),
          cabang=[(['bendera kota42.akar.tanding'], 'tanding')], ke='kenangan'),
        T('tanding', ('Kalahkan Kakak Senior Gao dalam tanding ramah (berhenti saat satu pihak tinggal sepersepuluh).',
                      'Beat Senior Brother Gao in a friendly bout (it stops when one side is down to a tenth).'),
          tujuan=['duel tokoh:gao_senior'], batas_hari=3, gagal_ke='kenangan'),
        T('kenangan', ('Ibu Xiao Yu ingin bercerita tentang hari zhuazhou putrinya. Dengarkan dia, lalu tentukan masa depan anak itu bersama keluarganya.',
                       'Xiao Yu\'s mother wants to tell you about her daughter\'s zhuazhou day. Listen to her, then decide the child\'s future with her family.'),
          cabang=[(['bendera kota42.akar.pemain'], 'pemain'), (['bendera kota42.akar.gao'], 'gao'), (['bendera kota42.akar.tinggal'], 'tinggal')]),
        T('pemain', ('Xiao Yu bergabung dengan sektemu.', 'Xiao Yu joins your sect.'), tujuan=['hari 0'],
          akibat=['kota42.calon tokoh:xiao_yu pemain', 'kota42.kesan tokoh:yu_xiuniang 30', 'kabar kota42_akar_pemain pemain'], ke='selesai',
          hasil=('Xiao Yu mengikat buntelannya dengan pita merah ibunya. Di aula sektemu, daun-daun di halaman berputar setiap kali ia '
                 'tertawa, dan para murid lain mulai ikut tertawa.',
                 'Xiao Yu tied her bundle with her mother\'s red ribbon. In your sect\'s hall the courtyard leaves whirl whenever she '
                 'laughs, and the other disciples have started laughing too.')),
        T('gao', ('Xiao Yu mengikuti Kakak Senior Gao.', 'Xiao Yu follows Senior Brother Gao.'), tujuan=['hari 0'],
          akibat=['kota42.calon tokoh:xiao_yu gao', 'kota42.kesan tokoh:yu_laosan 20', 'kabar kota42_akar_gao'], ke='selesai',
          hasil=('Xiao Yu naik ke gunung sekte Lurus. Kelak, di Turnamen Agung, seorang gadis berjubah putih dengan daun berputar di '
                 'sekelilingnya akan menyebut namamu sebelum menghunus pedang.',
                 'Xiao Yu went up to the Righteous sect\'s mountain. One day, at the Grand Tournament, a girl in white with leaves '
                 'circling her will say your name before drawing her sword.')),
        T('tinggal', ('Xiao Yu tetap di kota.', 'Xiao Yu stays in the city.'), tujuan=['hari 0'],
          akibat=['kota42.calon tokoh:xiao_yu tinggal', 'kota42.kesan tokoh:yu_xiuniang 40', 'kota42.kesan tokoh:guru_zhou 20',
                  'kabar kota42_akar_tinggal'], ke='selesai',
          hasil=('Xiao Yu tetap belajar di kelas Guru Zhou. Daun yang melayang di sekitarnya kini ia pakai untuk menebak tanaman obat; '
                 'kelak ia akan menjadi tabib kota yang paling dicintai.',
                 'Xiao Yu stays in Master Zhou\'s class. She now uses the leaves floating around her to tell medicinal herbs apart; one '
                 'day she will be the city\'s most beloved physician.')),
    ])
    D('kota42_akar_guru', 'tokoh:guru_zhou', [
        N('mulai', ('Murid yang paling rajin di kelasku tidak pernah duduk di bangku. Ia mengintip dari jendela, dan setiap kali ia '
                    'menjawab pertanyaanku dengan berbisik, daun di halaman ikut berputar. Anak Kumuh, putri tukang gula.',
                    'The most diligent pupil in my class has never sat on a bench. She watches through the window, and whenever she '
                    'whispers the answer to my question, the leaves in the yard whirl. A Slum child, the sugar painter\'s daughter.'),
          gestur='mengajar', prop='kitab', suara='kota42.guru_panggil',
          pilihan=[P(('Daun berputar? Aku akan menemuinya.', 'Whirling leaves? I will meet her.'), akibat=['quest_mulai kota42_akar_langit'], ke='pesan'),
                   P(('Kenapa Guru menceritakan ini padaku?', 'Why tell me this, Master?'), ke='kenapa')]),
        N('kenapa', ('Karena kemarin seorang pemuda berjubah sekte datang ke Kumuh membawa sekantong perak. Anak berakar langit tidak '
                     'muncul seratus tahun sekali, dan orang yang membeli anak dengan perak jarang memperlakukannya seperti anak.',
                     'Because yesterday a young man in a sect robe came to the Slums with a bag of silver. A child with heavenly roots '
                     'appears not once in a hundred years, and those who buy children with silver seldom treat them as children.'),
          gestur='bicara_tangan', pilihan=[P(('Aku akan menemuinya.', 'I will meet her.'), akibat=['quest_mulai kota42_akar_langit'], ke='pesan'), P(PAMIT)]),
        N('pesan', ('Namanya Xiao Yu. Bawakan ia sesuatu yang manis. Anak yang lapar tidak bisa memikirkan masa depan.',
                    'Her name is Xiao Yu. Bring her something sweet. A hungry child cannot think about the future.'), gestur='hormat'),
    ], prioritas=40, syarat=['quest kota42_akar_langit baru', 'hari 5'], sekali=True)
    D('kota42_akar_xiao_yu', 'tokoh:xiao_yu', [
        N('mulai', ('Kakak mau lihat? (Ia tertawa, dan tiga lembar daun kering terangkat dari tanah, berputar di sekitar kepalanya seperti '
                    'burung kecil.) Daunnya suka padaku! Kata Ayah jangan tunjukkan ke orang asing. Tapi Kakak bukan orang asing, kan?',
                    'Want to see? (She laughs, and three dry leaves lift off the ground and circle her head like little birds.) The leaves '
                    'like me! Father says not to show strangers. But you are not a stranger, are you?'),
          gestur='k:kagum', suara='kota42.xiao_yu',
          pilihan=[P(('Bukan. Siapa lagi yang pernah melihatnya?', 'No. Who else has seen this?'), ke='siapa')]),
        N('siapa', ('Kakak berjubah putih yang pedangnya berkilau. Ia memberi Ayah kantong yang berat. Sejak itu Ayah tidak mau melihat '
                    'mataku. Ibu menangis di dapur.',
                    'A big brother in a white robe with a shiny sword. He gave Father a heavy purse. Since then Father won\'t look me in '
                    'the eye. Mother cries in the kitchen.'),
          gestur='menangis'),
    ], prioritas=50, syarat=['quest kota42_akar_langit tahap tawar', '!bendera kota42.akar.dengar_ayah'])
    D('kota42_akar_ayah', 'tokoh:yu_laosan', [
        N('mulai', ('Apa? Kau juga mau membeli anakku? (Ia menghitung sesuatu di jarinya.) Utangku di meja dadu sepuluh kali upahku. '
                    'Orang sekte itu menawarkan cukup untuk melunasinya dua kali. Anakku akan makan nasi putih di gunung. Itu salah?',
                    'What? You want to buy my child too? (He counts something on his fingers.) My dice debt is ten times my wages. That '
                    'sect man offered enough to pay it twice. My girl would eat white rice on a mountain. Is that wrong?'),
          gestur='berdebat', akibat=['bendera kota42.akar.dengar_ayah'],
          pilihan=[P(('(Bayar utangnya) Kalau utangmu lunas, kau tidak perlu menjualnya.', '(Pay his debt) If your debt is paid, you need not sell her.'),
                     syarat=['nb_pita 10', 'quest kota42_akar_langit tahap cara'], akibat=['bayar_pita 10', 'kota42.kebiasaan tokoh:yu_laosan penjudi hapus',
                                                                                         'quest_maju kota42_akar_langit'], ke='lunas',
                     alasan=('Butuh 10 keping kali pita, dan pembicaraan dengan Gao lebih dulu.', 'Needs 10 coins times your band, and a talk with Gao first.')),
                   P(('Ia anakmu, bukan dagangan.', 'She is your daughter, not merchandise.'), ke='malu')]),
        N('lunas', ('(Lama ia diam.) ...Aku akan bilang pada orang sekte itu untuk mengambil peraknya kembali. Dan aku tidak akan menyentuh '
                    'dadu lagi. Sumpah demi... demi daun-daun konyol itu.',
                    '(He is silent a long time.) ...I will tell that sect man to take his silver back. And I will never touch dice again. '
                    'I swear on... on those silly leaves.'), gestur='berlutut'),
        N('malu', ('Gampang bicara kalau perutmu kenyang. Tapi... tanyakan ibunya. Ia yang paling tahu apa yang baik untuk anak itu.',
                   'Easy to say on a full stomach. But... ask her mother. She knows best what is good for the girl.'), gestur='k:garuk_kepala'),
    ], prioritas=50, syarat=['quest kota42_akar_langit aktif', '!quest kota42_akar_langit tahap daun', '!bendera kota42.akar.pilih'])
    D('kota42_akar_gao', 'tokoh:gao_senior', [
        N('mulai', ('Anak Kumuh itu? Akar langit, murni, tidak bercampur. Sekteku menawarkan nasi, kitab dan guru terbaik. Kau menawarkan '
                    'apa, pengembara? Tanah becek dan daun kering?',
                    'The Slum child? Heavenly roots, pure, unmixed. My sect offers rice, scriptures and the best masters. What do you '
                    'offer, wanderer? Mud and dry leaves?'),
          gestur='sikap_angkuh', akibat=['bendera kota42.akar.dengar_gao'],
          pilihan=[P(('(Tantang) Kita selesaikan dengan pedang. Kalau aku menang, kau mundur.', '(Challenge) Let us settle it with swords. If I win, you withdraw.'),
                     syarat=['quest kota42_akar_langit tahap cara'], akibat=['bendera kota42.akar.tanding', 'quest_maju kota42_akar_langit', 'mulai_duel'],
                     alasan=('Dengarkan dulu keluarga anak itu.', 'Hear the child\'s family first.')),
                   P(('Biar keluarganya yang memutuskan.', 'Let her family decide.'), ke='putus')]),
        N('putus', ('Keluarga? Ayahnya sudah memutuskan, dengan harga yang pantas. Tapi silakan, bicaralah dengan tikus-tikus Kumuh itu.',
                    'Family? Her father already decided, for a fair price. But please, go talk to those Slum rats.'), gestur='sikap_angkuh'),
    ], prioritas=50, syarat=['quest kota42_akar_langit aktif', '!quest kota42_akar_langit tahap daun', '!quest kota42_akar_langit tahap tanding',
                             '!bendera kota42.akar.pilih'])
    D('kota42_akar_ibu', 'tokoh:yu_xiuniang', [
        N('mulai', ('Waktu Xiao Yu berumur setahun, kami menggelar tikar zhuazhou. Ada kuas, sempoa, gunting, pedang kayu. Ia tidak '
                    'meraih apa pun. Ia tertawa, dan semua benda itu melayang ke arahnya. Aku tahu sejak hari itu, ia bukan milik kami '
                    'selamanya.',
                    'When Xiao Yu turned one, we laid out the zhuazhou mat. A brush, an abacus, scissors, a wooden sword. She reached for '
                    'nothing. She laughed, and every one of them floated toward her. I knew from that day she would not be ours forever.'),
          gestur='k:lukis_gula', prop='k:sendok_gula', suara='kota42.zhuazhou_kuas',
          pilihan=[P(('Lalu apa yang Ibu inginkan untuknya?', 'Then what do you want for her?'), ke='ingin')]),
        N('ingin', ('Aku ingin ia pergi ke tempat yang membiarkannya tetap tertawa. Hari Ujian Terbuka sebentar lagi. Bantu kami memilih.',
                    'I want her to go where she can keep laughing. The Open Examination Day is close. Help us choose.'),
          gestur='bicara_tangan',
          pilihan=[P(('(Sektemu) Bawa ia ke sektemu.', '(Your sect) Bring her to your sect.'), akibat=['bendera kota42.akar.pemain', 'bendera kota42.akar.pilih', 'quest_maju kota42_akar_langit'], ke='pilih_pemain'),
                   P(('(Sekte Gao) Sekte Lurus itu kuat. Biarkan ia ikut Gao.', '(Gao\'s sect) That Righteous sect is strong. Let her go with Gao.'),
                     akibat=['bendera kota42.akar.gao', 'bendera kota42.akar.pilih', 'quest_maju kota42_akar_langit'], ke='pilih_gao'),
                   P(('(Tinggal) Ia masih sembilan tahun. Biarkan ia tumbuh di sini.', '(Stay) She is only nine. Let her grow up here.'),
                     akibat=['bendera kota42.akar.tinggal', 'bendera kota42.akar.pilih', 'quest_maju kota42_akar_langit'], ke='pilih_tinggal')]),
        N('pilih_pemain', ('Kalau kau yang menjadi gurunya, aku bisa tidur nyenyak. Jaga dia. Biarkan ia pulang setiap Tahun Baru.',
                           'If you are her master, I can sleep soundly. Look after her. Let her come home every New Year.'), gestur='k:peluk'),
        N('pilih_gao', ('Gunung itu jauh... tapi nasinya putih. Semoga mereka tidak memotong sayapnya.',
                        'That mountain is far... but its rice is white. May they not clip her wings.'), gestur='menangis'),
        N('pilih_tinggal', ('Terima kasih. Guru Zhou bilang ia boleh duduk di bangku mulai besok. Bangku sungguhan!',
                            'Thank you. Master Zhou says she may sit on a bench from tomorrow. A real bench!'), gestur='tertawa'),
    ], prioritas=50, syarat=['quest kota42_akar_langit tahap kenangan'])
    D('kota42_akar_ibu_cara', 'tokoh:yu_xiuniang', [
        N('mulai', ('Suamiku sudah setengah menjual putri kami. Kalau kau mau membantu, jangan bayar orang itu. Bantu aku bicara padanya '
                    'sebagai ibu, bukan sebagai penjudi.',
                    'My husband has half sold our daughter. If you want to help, don\'t pay that man. Help me speak to him as a mother, '
                    'not as a gambler.'),
          gestur='k:lukis_gula', prop='k:sendok_gula',
          pilihan=[P(('(Bujuk) Kita bicara bersama dengannya.', '(Persuade) We talk to him together.'),
                     akibat=['kota42.kesan tokoh:yu_xiuniang 15', 'quest_maju kota42_akar_langit'], ke='bujuk'),
                   P(PAMIT)]),
        N('bujuk', ('(Malam itu suara mereka terdengar sampai ke gang. Pagi harinya Yu Laosan mengembalikan kantong perak itu kepada Gao.)',
                    '(That night their voices carried to the alley. In the morning Yu Laosan returned the silver purse to Gao.)'), gestur='hormat'),
    ], prioritas=55, syarat=['quest kota42_akar_langit tahap cara'])


def alur_kota_tumbuh():
    q = 'kota42_kota_tumbuh'
    Q(q, 'tokoh:gong_shu', 'kota42', ('Kota yang Tumbuh', 'The Growing City'),
      ('Mandor Gong Shu ingin membangun kavling pertama di tepi ibu kota, tetapi kayu dan genteng tidak datang sendiri.',
       'Foreman Gong Shu wants to raise the first new plot at the capital\'s edge, but timber and tiles do not arrive by themselves.'), [
        T('kurang', ('Fondasi sudah dipatok, tapi kayu tidak ada. Tanyakan Saudagar Ma Fu, kafilahnya membawa kayu jati dari selatan.',
                     'The foundation is staked, but there is no timber. Ask Merchant Ma Fu, his caravan brings teak from the south.'),
          tujuan=['bicara tokoh:ma_fu']),
        T('kawal', ('Kafilah kayu Ma Fu diincar begal. Kalahkan para begal di jalan, lalu kawal gerobaknya ke kota.',
                    'Ma Fu\'s timber caravan is stalked by bandits. Beat the bandits on the road, then escort his wagon to the city.'),
          tujuan=['kalahkan peran:pembunuh_bayaran x3'], akibat_masuk=['panggil_peran pembunuh_bayaran musuh 3']),
        T('kirim', ('Begal sudah kabur. Laporkan kepada Ma Fu supaya gerobak kayunya berangkat.',
                    'The bandits have fled. Report to Ma Fu so his timber wagons can set off.'),
          tujuan=['bicara tokoh:ma_fu']),
        T('genteng', ('Kayu sudah datang. Kumpulkan enam puluh empat genteng (bata tanah liat) untuk atapnya.',
                      'The timber has arrived. Gather sixty-four roof tiles (clay bricks) for the roof.'),
          tujuan=['antar minecraft:brick tokoh:gong_shu x64']),
        T('resmi', ('Tukang bekerja siang malam. Tunggu sampai kavling itu berdiri, lalu hadiri peresmiannya.',
                    'The builders work day and night. Wait until the plot stands, then attend its opening.'),
          tujuan=['kavling_tahap 1', 'bendera kota42.tumbuh.potong_pita'],
          akibat=['kota42.nama_jalan pemain', 'nama_baik 10', 'kota42.kesan tokoh:gong_shu 30', 'kabar kota42_tumbuh_resmi pemain'],
          hasil=('Nyonya Hong Ling mendatangkan barongsai, Mandor Gong Shu memotong pita merah, dan di ujung jalan baru itu kini '
                 'tergantung papan nama dengan namamu.',
                 'Madam Hong Ling brought a lion dance, Foreman Gong Shu cut the red ribbon, and at the end of the new street now '
                 'hangs a signboard with your name.')),
    ])
    D('kota42_tumbuh_mulai', 'tokoh:gong_shu', [
        N('mulai', ('Lihat patok-patok itu? Di situ akan berdiri sebuah rumah, lalu sebuah jalan, lalu sebuah lingkungan. Kota yang '
                    'berhenti tumbuh mulai mati, kata kakek buyutku. Masalahnya, kota ini tumbuh lebih cepat daripada kayunya.',
                    'See those stakes? A house will stand there, then a street, then a whole quarter. A city that stops growing starts '
                    'dying, my great-grandfather said. The trouble is, this city grows faster than its timber.'),
          gestur='k:pasang_patok', prop='k:papan_patok', suara='kota42.mandor',
          pilihan=[P(('Aku bisa membantu.', 'I can help.'), syarat=['quest kota42_kota_tumbuh baru'], akibat=['quest_mulai kota42_kota_tumbuh'], ke='bantu'),
                   P(('Apa itu Yingzao Fashi?', 'What is the Yingzao Fashi?'), ke='kitab'),
                   P(PAMIT)]),
        N('bantu', ('Ma Fu punya kayu jati, tapi ia tidak mau mengirim tanpa pengawal. Bicaralah dengannya, ia biasanya di gudang kafilah.',
                    'Ma Fu has teak, but he won\'t send it without guards. Talk to him, he is usually at the caravan warehouse.'), gestur='tunjuk'),
        N('kitab', ('"Tata Cara Membangun", ditulis Li Jie untuk istana Song. Ukuran balok, kemiringan atap, semuanya dihitung dengan '
                    'satuan cai. Rumah yang dibangun menurut kitab ini tidak roboh oleh gempa kecil. Aku membacanya seperti orang lain membaca puisi.',
                    '"Building Standards", written by Li Jie for the Song court. Beam sizes, roof pitch, all counted in cai units. A house '
                    'built by this book does not fall in a small quake. I read it the way others read poetry.'),
          gestur='membaca', prop='kitab', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=30, syarat=['hari 6'])
    D('kota42_tumbuh_ma_fu', 'tokoh:ma_fu', [
        N('mulai', ('Kayu jati untuk Gong Shu? Ada, ada. Tiga gerobak di gudang selatan. Tapi jalannya penuh begal, dan begal tidak membayar '
                    'kayu. Kalau kau mau membersihkan jalannya, harga temanku turun separuh.',
                    'Teak for Gong Shu? Yes, yes. Three wagons in the south warehouse. But the road is full of bandits, and bandits do '
                    'not pay for timber. If you clear the road, my price for a friend drops by half.'),
          gestur='hitung_sempoa', prop='sempoa',
          pilihan=[P(('Aku akan mengawalnya.', 'I will escort it.'), ke='kawal')]),
        N('kawal', ('Bagus! Dan tepat waktu: lihat orang-orang bertopeng di balik tumpukan karung itu? Mereka mengikuti gerobakku sejak '
                    'hutan bambu. Usir mereka, dan kayunya kukirim ke mandor.',
                    'Good! And just in time: see the masked men behind those sacks? They have followed my wagons since the bamboo '
                    'grove. Drive them off, and I send the timber to the foreman.'), gestur='tunjuk'),
    ], prioritas=50, syarat=['quest kota42_kota_tumbuh tahap kawal'])
    D('kota42_tumbuh_kayu', 'tokoh:ma_fu', [
        N('mulai', ('Begalnya kabur? Hebat! Gerobak kayu berangkat sekarang. Tanda terima ini untuk Gong Shu. Separuh harga, seperti janji.',
                    'The bandits fled? Splendid! The timber wagons leave now. This receipt is for Gong Shu. Half price, as promised.'),
          gestur='k:beri_koin', akibat=['bendera kota42.tumbuh.kayu', 'kota42.kesan tokoh:ma_fu 15']),
    ], prioritas=55, syarat=['quest kota42_kota_tumbuh tahap genteng', '!bendera kota42.tumbuh.kayu'])
    D('kota42_tumbuh_resmi', 'tokoh:gong_shu', [
        N('mulai', ('Atapnya sudah naik, dindingnya sudah dikapur. Barongsai Nyonya Hong Ling menunggu di ujung jalan. Mau memotong '
                    'pitanya? Kau yang paling pantas.',
                    'The roof is up, the walls are limewashed. Madam Hong Ling\'s lion dance waits at the end of the street. Will you '
                    'cut the ribbon? You deserve it most.'),
          gestur='k:potong_pita', prop='k:gunting_besar',
          pilihan=[P(('(Potong pita)', '(Cut the ribbon)'), akibat=['bendera kota42.tumbuh.potong_pita', 'klip k:potong_pita k:gunting_besar'],
                     ke='potong')]),
        N('potong', ('Selamat! Rumah pertama di jalan baru. Mulai hari ini jalan ini punya nama, dan nama itu namamu.',
                     'Congratulations! The first house on the new street. From today this street has a name, and that name is yours.'),
          gestur='tepuk_tangan', suara='kota42.kavling_selesai'),
    ], prioritas=50, syarat=['quest kota42_kota_tumbuh tahap resmi', '!bendera kota42.tumbuh.potong_pita'])


def alur_sup_hangat():
    q = 'kota42_sup_hangat'
    Q(q, 'tokoh:nenek_tang', 'kota42', ('Musim Dingin Sup Hangat', 'The Winter of Warm Soup'),
      ('Di hari Laba, kuali sup Nenek Tang kosong sebelum separuh antrean terlayani.',
       'On Laba day, Granny Tang\'s soup pot ran dry before half the queue was served.'), [
        T('bahan', ('Kumpulkan delapan bahan sup untuk Nenek Tang: dua bit, dua wortel, jamur cokelat, jamur merah, rumput laut kering, '
                    'dan semangkuk sup ginseng roh dari rumah makan.',
                    'Gather eight soup ingredients for Granny Tang: two beetroots, two carrots, a brown mushroom, a red mushroom, dried '
                    'kelp, and a bowl of spirit ginseng soup from the eating house.'),
          tujuan=['antar minecraft:beetroot tokoh:nenek_tang x2', 'antar minecraft:carrot tokoh:nenek_tang x2',
                  'antar minecraft:brown_mushroom tokoh:nenek_tang', 'antar minecraft:red_mushroom tokoh:nenek_tang',
                  'antar minecraft:dried_kelp tokoh:nenek_tang', 'antar kultivator:hidangan/sup_ginseng_roh tokoh:nenek_tang']),
        T('bagi', ('Sup sudah mendidih. Bagikan bubur di kuil bersama Paman Bai.', 'The soup is boiling. Share out the porridge at the temple with Uncle Bai.'),
          tujuan=['bendera kota42.sup.bagi'],
          akibat=['beri kultivator:resep_sup', 'kota42.resep kultivator:sup_hangat', 'kota42.kesan tokoh:nenek_tang 30',
                  'nama_baik 3', 'kabar kota42_sup_bagi pemain']),
        T('musim_depan', ('Nenek Tang sudah sangat tua. Datanglah menjenguknya di musim dingin berikutnya.',
                          'Granny Tang is very old. Come and visit her next winter.'),
          akibat_masuk=['kota42.pensiun tokoh:nenek_tang'], tujuan=['hadir_upacara makam tokoh:nenek_tang'], batas_hari=60, gagal_ke='cucu'),
        T('cucu', ('Tang Xiaomi meneruskan warung neneknya, tetapi supnya belum terasa sama. Mungkin kau ingat sesuatu yang ia lupakan.',
                   'Tang Xiaomi carries on her grandmother\'s stall, but the soup does not taste the same. Perhaps you remember something she forgot.'),
          tujuan=['bendera kota42.sup.rahasia'],
          akibat=['kota42.kesan tokoh:tang_xiaomi 40', 'kabar kota42_sup_cucu'],
          hasil=('Bahan rahasia Nenek Tang ternyata bukan bahan: ia selalu melayani orang yang paling kedinginan lebih dulu. Warung '
                 'itu tetap buka setiap musim dingin, dan mangkuk pertama selalu untuk orang yang menggigil.',
                 'Granny Tang\'s secret ingredient was not an ingredient: she always served the coldest person first. The stall still '
                 'opens every winter, and the first bowl always goes to whoever is shivering.')),
    ])
    D('kota42_sup_mulai', 'tokoh:nenek_tang', [
        N('mulai', ('Aduh, aduh. Kualinya kosong, padahal antrean masih sampai gerbang. Tahun ini musim dinginnya galak sekali. Kalau '
                    'tidak ada yang membantu, anak-anak Kumuh tidur dengan perut dingin.',
                    'Oh dear, oh dear. The pot is empty, and the queue still reaches the gate. This winter is so fierce. If nobody helps, '
                    'the Slum children will sleep with cold bellies.'),
          gestur='k:batuk', prop='k:kendi_sup', suara='kota42.sup_nenek',
          pilihan=[P(('Apa yang Nenek butuhkan?', 'What do you need, Granny?'), akibat=['quest_mulai kota42_sup_hangat'], ke='butuh'),
                   P(('Semangkuk saja, Nek.', 'Just one bowl, Granny.'), ke='mangkuk')]),
        N('butuh', ('Bit dan wortel dari ladang, dua jamur dari hutan, rumput laut kering dari pedagang ikan, dan semangkuk sup ginseng dari '
                    'rumah makan untuk kaldunya. Nenek terlalu tua untuk berjalan sejauh itu.',
                    'Beetroot and carrots from the fields, two mushrooms from the woods, dried kelp from the fish seller, and a bowl of '
                    'ginseng soup from the eating house for the stock. Granny is too old to walk that far.'), gestur='bicara_tangan'),
        N('mangkuk', ('Habis, Nak, habis. Besok pagi Nenek masak lagi, kalau bahannya ada.', 'All gone, child, all gone. Granny will cook again tomorrow, if there are ingredients.'),
          gestur='menangis', pilihan=[P(('Aku carikan bahannya.', 'I will find the ingredients.'), akibat=['quest_mulai kota42_sup_hangat'], ke='butuh'), P(PAMIT)]),
    ], prioritas=40, syarat=['quest kota42_sup_hangat baru'], sekali=True)
    D('kota42_sup_bagi', 'tokoh:paman_bai', [
        N('mulai', ('Nenek Tang mengirim kuali sebesar ini? Di depan kuil sudah antre seratus orang. Ambil sendok besar itu, kita bagikan '
                    'bersama. Satu mangkuk, satu senyum.',
                    'Granny Tang sent a pot this big? A hundred people are queueing at the temple. Take that big ladle, we share it out '
                    'together. One bowl, one smile.'),
          gestur='k:bagi_bubur', prop='k:mangkuk_bubur', suara='kota42.laba',
          pilihan=[P(('(Bagikan bubur)', '(Share out the porridge)'), akibat=['bendera kota42.sup.bagi', 'klip k:bagi_bubur k:mangkuk_bubur'], ke='selesai')]),
        N('selesai', ('Lihat wajah-wajah itu. Bubur Laba bukan soal kenyang, Nak. Soal ingat bahwa ada yang peduli.',
                      'Look at those faces. Laba porridge is not about a full belly, child. It is about remembering someone cares.'), gestur='hormat'),
    ], prioritas=50, syarat=['quest kota42_sup_hangat tahap bagi'])
    D('kota42_sup_cucu', 'tokoh:tang_xiaomi', [
        N('mulai', ('Aku sudah memakai semua bahan Nenek, tapi supnya tidak sama. Pelanggan tetap bilang enak, tapi mereka bohong supaya '
                    'aku tidak menangis. Kau pernah memasak bersama Nenek. Apa yang kulupakan?',
                    'I use all Granny\'s ingredients, but the soup is not the same. The regulars say it is good, but they lie so I won\'t '
                    'cry. You cooked with Granny once. What did I forget?'),
          gestur='k:seka_keringat', prop='k:kendi_sup',
          pilihan=[P(('Nenek selalu melayani orang yang paling kedinginan lebih dulu.', 'Granny always served the coldest person first.'),
                     akibat=['bendera kota42.sup.rahasia'], ke='ingat'),
                   P(('Mungkin garamnya kurang.', 'Maybe it needs more salt.'), ke='garam')]),
        N('garam', ('Sudah kucoba. Dan jahe, dan daun bawang, dan... bukan itu.', 'I tried. And ginger, and scallion, and... it is not that.'),
          gestur='k:garuk_kepala', pilihan=[P(('Nenek selalu melayani orang yang paling kedinginan lebih dulu.', 'Granny always served the coldest person first.'),
                                             akibat=['bendera kota42.sup.rahasia'], ke='ingat')]),
        N('ingat', ('(Ia menatap antrean, lalu menyendok mangkuk pertama untuk pengemis tua di ujung barisan.) ...Ya. Itu dia. Rasanya sama.',
                    '(She looks at the queue, then ladles the first bowl for the old beggar at the end of the line.) ...Yes. That is it. '
                    'It tastes the same.'), gestur='menangis'),
    ], prioritas=50, syarat=['quest kota42_sup_hangat tahap cucu'])


def alur_pelindung():
    q = 'kota42_pelindung_baru'
    Q(q, 'tokoh:tetua_he', 'kota42_kota34', ('Pelindung Baru', 'A New Protector'),
      ('Sekte pelindung sebuah kota sudah tiga musim tidak datang, dan begal mulai menguasai jalan-jalannya.',
       'A town\'s protector sect has not come for three seasons, and bandits have begun to rule its roads.'), [
        T('keluh', ('Dengarkan keluhan Tetua He.', 'Hear Elder He\'s complaint.'), tujuan=['bicara tokoh:tetua_he']),
        T('kemah', ('Bersihkan kemah begal terdekat.', 'Clear out the nearest bandit camp.'),
          tujuan=['pergi pos/kemah_begal', 'kalahkan peran:pembunuh_bayaran x4'], arah='pos/kemah_begal'),
        T('tantang', ('Seorang utusan sekte lama datang menantangmu karena "merebut" kota mereka. Kalahkan dia dalam tanding.',
                      'An envoy of the old sect comes to challenge you for "stealing" their town. Beat him in a bout.'),
          akibat_masuk=['panggil_peran murid_sekte_dalam musuh 1'], tujuan=['duel peran:murid_sekte_dalam']),
        T('serbu', ('Binatang buas mengepung kota. Pertahankan kota dalam serbuan pertamanya di bawah perlindunganmu.',
                    'Beasts besiege the town. Defend it in its first raid under your protection.'),
          tujuan=['tanda kota42.bela_menang'], batas_hari=12, gagal_ke='panji'),
        T('panji', ('Rakyat menunggu di gerbang. Naikkan panji sektemu.', 'The people wait at the gate. Raise your sect\'s banner.'),
          tujuan=['bendera kota42.pelindung.panji'],
          akibat=['kota42.pakta', 'nama_baik 6', 'kabar kota42_pelindung_panji pemain'],
          hasil=('Panji sektemu berkibar di atas gerbang. Anak-anak berani bermain sampai sore, dan Tetua He menyalakan dupa untuk '
                 'leluhurmu meski ia tidak tahu siapa mereka.',
                 'Your sect\'s banner flies over the gate. Children dare to play until dusk, and Elder He burns incense for your '
                 'ancestors though he does not know who they are.')),
    ])
    D('kota42_pelindung_he', 'tokoh:tetua_he', [
        N('mulai', ('Tuan kultivator... tolong. Tiga musim aku menulis surat ke gunung pelindung kami. Tidak ada jawaban. Kemarin begal '
                    'mengambil seluruh panen jagung Desa Timur dan dua gadis dari jalan sumur.',
                    'Honoured cultivator... please. For three seasons I have written to our protector\'s mountain. No answer. Yesterday '
                    'bandits took Eastern Village\'s whole corn harvest and two girls from the well road.'),
          gestur='berlutut',
          pilihan=[P(('Aku akan mengurus begal itu.', 'I will deal with those bandits.'), syarat=['quest kota42_pelindung_baru baru'],
                     akibat=['quest_mulai kota42_pelindung_baru', 'quest_maju kota42_pelindung_baru'], ke='terima'),
                   P(('Kenapa sekte pelindungmu tidak datang?', 'Why does your protector sect not come?'), ke='kenapa'),
                   P(PAMIT)]),
        N('kenapa', ('Kota kami kecil, pajak kami sedikit. Sekte besar punya kota yang lebih gemuk untuk dijaga. Kami hanya sebaris nama '
                     'di buku mereka.',
                     'Our town is small, our tribute meagre. Great sects have fatter towns to guard. We are only a line in their ledger.'),
          gestur='menangis', pilihan=[P(('Aku akan membantu.', 'I will help.'), syarat=['quest kota42_pelindung_baru baru'],
                                        akibat=['quest_mulai kota42_pelindung_baru', 'quest_maju kota42_pelindung_baru'], ke='terima'), P(PAMIT)]),
        N('terima', ('Kemah mereka di pos tua di jalan utara. Semoga langit melindungimu, karena sekte kami tidak.',
                     'Their camp is at the old post on the north road. May heaven protect you, since our sect does not.'), gestur='hormat_dalam'),
    ], prioritas=40, syarat=['ranah 2'])
    D('kota42_pelindung_panji', 'tokoh:tetua_he', [
        N('mulai', ('Seluruh kota berkumpul di gerbang. Tiang panji sudah dicat merah. Tuan, kota kecil ini meminta perlindungan sektemu. '
                    'Kami tidak kaya, tapi kami tidak lupa.',
                    'The whole town has gathered at the gate. The banner pole is painted red. Honoured one, this small town asks for your '
                    'sect\'s protection. We are not rich, but we do not forget.'),
          gestur='hormat_dalam',
          pilihan=[P(('(Naikkan panji)', '(Raise the banner)'), akibat=['bendera kota42.pelindung.panji', 'suara kota42.panji_naik'], ke='naik'),
                   P(('Belum saatnya.', 'Not yet.'))]),
        N('naik', ('(Panji itu menangkap angin dan berkibar. Seseorang memukul gong, lalu seluruh kota bersorak.) Mulai hari ini kami '
                   'punya pelindung lagi.',
                   '(The banner catches the wind and unfurls. Someone strikes a gong, then the whole town cheers.) From today we have a '
                   'protector again.'), gestur='tepuk_tangan', suara='kota42.pelindung_sapa'),
    ], prioritas=50, syarat=['quest kota42_pelindung_baru tahap panji'])


def alur_wayang():
    q = 'kota42_malam_wayang'
    Q(q, 'tokoh:dalang_su', 'kota42', ('Malam Wayang', 'The Night of the Shadow Play'),
      ('Wayang kulit Kera Sakti tertua milik Dalang Su dicuri tiga hari sebelum pertunjukan perdana.',
       'Puppeteer Su\'s oldest Monkey King puppet was stolen three days before the premiere.'), [
        T('dicuri', ('Dengarkan Dalang Su tentang wayang yang hilang.', 'Hear Puppeteer Su about the missing puppet.'), tujuan=['bicara tokoh:dalang_su']),
        T('telusur', ('Telusuri gosip tentang wayang curian. Siapa yang pertama melihatnya?', 'Trace the gossip about the stolen puppet. Who saw it first?'),
          akibat_masuk=['kota42.gosip kota42_wayang_hilang'], tujuan=['tanda kota42.telusur.kota42_wayang_hilang']),
        T('tebus', ('Wayang itu dijual di Pasar Hantu. Tebus atau rebut dari tangan Lentera Hijau.',
                    'The puppet is for sale at the Ghost Market. Ransom it or seize it from Green Lantern\'s hands.'),
          tujuan=['punya kultivator:wayang_kulit_kera_sakti']),
        T('kembali', ('Kembalikan wayang itu kepada Dalang Su.', 'Return the puppet to Puppeteer Su.'),
          tujuan=['antar kultivator:wayang_kulit_kera_sakti tokoh:dalang_su']),
        T('perdana', ('Malam perdana! Dalang Su memintamu menabuh gong mengikuti irama pertunjukan.',
                      'Premiere night! Puppeteer Su asks you to strike the gong to the show\'s rhythm.'),
          tujuan=['tanda kota42.irama_wayang'],
          akibat=['bendera kota42.tiket_wayang', 'kota42.kesan tokoh:dalang_su 30', 'kabar kota42_wayang_perdana pemain'],
          hasil=('Kera Sakti melompat di layar, gongmu berbunyi tepat saat pedang bertemu tongkat, dan penonton berdiri bersorak. Mulai '
                 'malam ini kau tidak perlu membayar tiket teater bayang lagi.',
                 'The Monkey King leapt across the screen, your gong rang just as sword met staff, and the audience rose cheering. From '
                 'tonight you never need to pay for a shadow-theatre ticket again.')),
    ])
    D('kota42_wayang_su', 'tokoh:dalang_su', [
        N('mulai', ('Enam wayang, enam nyawa. Sekarang tinggal lima. Kera Sakti buatan guruku, kulit keledai yang disamak seratus hari, '
                    'hilang dari kotaknya. Tanpa dia, lakon Perjalanan ke Barat tidak bisa dimainkan.',
                    'Six puppets, six lives. Now only five. The Monkey King my master made, donkey hide tanned a hundred days, vanished '
                    'from its box. Without him, the Journey to the West cannot be played.'),
          gestur='k:dalang', prop='k:wayang_tongkat',
          pilihan=[P(('Aku akan mencarinya.', 'I will find it.'), syarat=['quest kota42_malam_wayang baru'], akibat=['quest_mulai kota42_malam_wayang'], ke='cari'),
                   P(('Bagaimana wayang bayang dimainkan?', 'How is a shadow play performed?'), ke='cara'),
                   P(PAMIT)]),
        N('cari', ('Malam itu Bibi Wang bilang melihat bayangan melompat dari atap teater. Bibi Wang selalu melihat bayangan, tapi '
                   'kali ini mungkin benar.',
                   'That night Auntie Wang said she saw a shadow leap from the theatre roof. Auntie Wang always sees shadows, but this '
                   'time she may be right.'), gestur='tunjuk'),
        N('cara', ('Lampu minyak di belakang, kain di depan, wayang di antaranya. Penonton melihat bayangan, aku melihat wayang. Yang '
                   'sulit bukan tangannya, tapi suaranya: enam suara, satu tenggorokan, dan gong harus jatuh tepat saat pedang bertemu.',
                   'An oil lamp behind, the cloth in front, the puppets between. The audience sees shadows, I see puppets. The hard part '
                   'is not the hands but the voice: six voices, one throat, and the gong must fall just as swords meet.'),
          gestur='k:dalang', prop='k:wayang_tongkat', suara='kota42.wayang_buka', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=30)
    D('kota42_wayang_telusur', 'tokoh:bibi_wang', [
        N('mulai', ('Bayangan itu! Ya! Kecil, cepat, seperti kera sungguhan! Lalu pagi harinya anak tukang ikan bilang ada wayang dijual '
                    'di Pasar Hantu. Aduh, jangan-jangan wayangnya hidup dan kabur sendiri!',
                    'That shadow! Yes! Small, quick, like a real monkey! Then next morning the fishmonger\'s boy said a puppet was for '
                    'sale at the Ghost Market. Oh, what if the puppet came alive and ran off by itself!'),
          gestur='takut_sembunyi', akibat=['peristiwa tanda kota42.telusur.kota42_wayang_hilang']),
    ], prioritas=50, syarat=['quest kota42_malam_wayang tahap telusur'])
    D('kota42_wayang_lentera', 'tokoh:lentera_hijau', [
        N('mulai', ('Kulit keledai seratus hari... barang bagus. Enam keping kali pitamu, dan wayang ini pulang ke dalangnya. Atau kau '
                    'mau mencoba mengambilnya? Anak-anakku senang bermain.',
                    'Donkey hide, a hundred days... fine goods. Six coins times your band, and this puppet goes home to its master. Or '
                    'would you rather try taking it? My children love to play.'),
          gestur='k:intip', prop='k:wayang_tongkat', suara='kota42.lentera_hijau',
          pilihan=[P(('(Tebus)', '(Ransom)'), syarat=['nb_pita 6'], akibat=['bayar_pita 6', 'beri kultivator:wayang_kulit_kera_sakti'], ke='tebus',
                     alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('(Rebut) Serahkan, atau lenteramu padam.', '(Seize) Hand it over, or your lantern goes out.'),
                     akibat=['panggil_peran pencopet musuh 3', 'bendera kota42.wayang.rebut'], ke='rebut'),
                   P(PAMIT)]),
        N('tebus', ('Pasar ini selalu menepati janji. Itulah sebabnya pasar ini masih ada.', 'This market always keeps its word. That is why it still exists.'),
          gestur='hormat'),
        N('rebut', ('Hehe. Anak-anak, tamu kita mau bermain.', 'Heh heh. Children, our guest wants to play.'), gestur='k:intip'),
    ], prioritas=50, syarat=['quest kota42_malam_wayang tahap tebus', '!bendera kota42.wayang.rebut'])
    D('kota42_wayang_rebut', 'tokoh:lentera_hijau', [
        N('mulai', ('Cukup, cukup! Anak-anakku sudah kenyang bermain. Ambil wayangmu. Tapi ingat, Pasar Hantu punya ingatan panjang.',
                    'Enough, enough! My children have played their fill. Take your puppet. But remember, the Ghost Market has a long memory.'),
          gestur='k:sodor_amplop', akibat=['beri kultivator:wayang_kulit_kera_sakti', 'kota42.kesan tokoh:lentera_hijau -20']),
    ], prioritas=55, syarat=['quest kota42_malam_wayang tahap tebus', 'bendera kota42.wayang.rebut', '!punya kultivator:wayang_kulit_kera_sakti'])
    D('kota42_wayang_perdana', 'tokoh:dalang_su', [
        N('mulai', ('Penonton sudah duduk, lampu sudah menyala. Kau yang menabuh gong malam ini. Lihat bayangan pedang dan tongkat. Saat '
                    'keduanya bertemu, pukul!',
                    'The audience is seated, the lamp is lit. You strike the gong tonight. Watch the shadows of sword and staff. When '
                    'they meet, strike!'),
          gestur='k:dalang', prop='k:wayang_tongkat',
          pilihan=[P(('(Tabuh gong tepat waktu)', '(Strike the gong on the beat)'), akibat=['klip k:pukul_gong k:gong_tangan', 'peristiwa tanda kota42.irama_wayang'], ke='tepat'),
                   P(('Sebentar, aku belum siap.', 'Wait, I am not ready.'))]),
        N('tepat', ('GONG! Tepat sekali! Dengar sorakan itu? Guruku pasti tersenyum di atas sana.',
                    'GONG! Perfect! Hear that cheer? My master must be smiling up there.'), gestur='tepuk_tangan'),
    ], prioritas=50, syarat=['quest kota42_malam_wayang tahap perdana', 'jam 19 23'])


def alur_pejabat():
    q = 'kota42_pejabat_jujur'
    Q(q, 'tokoh:qiu_panitera', 'kota42', ('Pejabat Jujur', 'An Honest Official'),
      ('Panitera Qiu mencari seseorang yang mau naik jabatan tanpa amplop. Lalu paceklik datang, dan Juragan Shen menimbun beras.',
       'Clerk Qiu is looking for someone willing to rise without envelopes. Then famine comes, and Master Shen hoards rice.'), [
        T('sidang', ('Sebagai pejabat kota, putuskan tiga perkara dengan adil.', 'As a city official, decide three court cases fairly.'),
          tujuan=['perkara * x3']),
        T('paceklik', ('Paceklik! Harga pangan naik setengah lagi. Nyonya Fang curiga Juragan Shen menimbun beras. Kumpulkan bukti.',
                       'Famine! Food prices rise by half again. Madam Fang suspects Master Shen of hoarding rice. Gather proof.'),
          akibat_masuk=['kota42.harga pangan 150 10'], tujuan=['bendera kota42.pejabat.bukti']),
        T('besar', ('Perkara besar di Yamen: sita timbunan Juragan Shen untuk lumbung kota, atau terima amplopnya.',
                    'The great case at the Yamen: seize Master Shen\'s hoard for the city granary, or accept his envelope.'),
          cabang=[(['bendera kota42.pejabat.sita'], 'sita'), (['bendera kota42.pejabat.suap'], 'suap')]),
        T('sita', ('Timbunan disita ke lumbung.', 'The hoard is seized for the granary.'), tujuan=['hari 0'],
          akibat=['kota42.harga pangan 85 7', 'kota42.jabatan 5', 'kota42.kesan tokoh:shen_wan -40', 'kota42.kesan tokoh:qiu_panitera 40',
                  'nama_baik 8', 'kabar kota42_pejabat_sita pemain'], ke='selesai',
          hasil=('Tiga ratus karung beras keluar dari gudang Juragan Shen ke lumbung kota. Harga turun dalam seminggu, dan Panitera Qiu '
                 'menulis namamu di daftar calon Wali Kota dengan kuas yang sama yang ia pakai dua puluh tahun.',
                 'Three hundred sacks of rice left Master Shen\'s warehouse for the city granary. Prices fell within a week, and Clerk '
                 'Qiu wrote your name on the list for Magistrate with the same brush he has used for twenty years.')),
        T('suap', ('Amplop diterima.', 'The envelope is accepted.'), tujuan=['hari 0'],
          akibat=['beri_nb_pita 30', 'infami 10 kota42', 'kota42.kesan tokoh:shen_wan 30', 'kota42.kesan tokoh:qiu_panitera -60',
                  'kabar kota42_pejabat_suap'], ke='selesai',
          hasil=('Amplop itu tebal dan wangi cendana. Harga pangan tetap tinggi sepanjang musim, dan Panitera Qiu tidak lagi menatap '
                 'matamu. Auditor dari ibu kota mulai membuka buku-buku lama.',
                 'The envelope was thick and smelled of sandalwood. Food prices stayed high all season, and Clerk Qiu no longer meets '
                 'your eyes. Auditors from the capital have begun opening old ledgers.')),
    ])
    D('kota42_pejabat_qiu', 'tokoh:qiu_panitera', [
        N('mulai', ('Dua puluh tahun aku menulis pajak, dan dua puluh tahun aku melihat pejabat naik dengan amplop dan jatuh dengan '
                    'amplop. Yamen butuh orang yang bisa memutus perkara tanpa melihat isi dompet penggugat.',
                    'Twenty years I have written taxes, and twenty years I have watched officials rise by envelopes and fall by envelopes. '
                    'The Yamen needs someone who can judge a case without looking at the plaintiff\'s purse.'),
          gestur='menulis', prop='kuas',
          pilihan=[P(('Aku mau mencoba.', 'I will try.'), syarat=['quest kota42_pejabat_jujur baru'], akibat=['quest_mulai kota42_pejabat_jujur'], ke='terima'),
                   P(('Bagaimana cara masuk Yamen?', 'How does one join the Yamen?'), ke='cara'),
                   P(PAMIT)]),
        N('cara', ('Mulai dari juru tulis, naik ke pengawas pasar, lalu hakim pembantu. Setiap jenjang butuh Nama Baik dan ranah. Yang tidak '
                   'tertulis: setiap jenjang juga menawarkan amplop. Menolaknya lebih sulit daripada naik ranah.',
                   'Start as a scribe, rise to market overseer, then assistant judge. Every rank needs Good Name and realm. What is not '
                   'written: every rank also offers envelopes. Refusing them is harder than rising a realm.'),
          gestur='bicara_tangan', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('terima', ('Putuskan tiga perkara. Dengarkan saksi, telusuri bukti, dan jangan pernah membuka amplop di meja sidang.',
                     'Decide three cases. Hear the witnesses, trace the evidence, and never open an envelope at the bench.'), gestur='hormat'),
    ], prioritas=30, syarat=['nama_baik 30'])
    D('kota42_pejabat_fang', 'tokoh:fang_gudang', [
        N('mulai', ('Bukti? Ini buku masuk gudangku. Tiga ratus karung beras masuk ke gudang Shen di bulan panen, nol karung keluar. Dan '
                    'lihat stempelnya: "gandum pakan kuda". Kuda siapa yang makan tiga ratus karung beras?',
                    'Proof? Here is my warehouse ledger. Three hundred sacks of rice went into Shen\'s storehouse at harvest, zero came '
                    'out. And look at the stamp: "horse fodder grain". Whose horses eat three hundred sacks of rice?'),
          gestur='k:menimbang', prop='k:dacing', akibat=['bendera kota42.pejabat.bukti']),
    ], prioritas=50, syarat=['quest kota42_pejabat_jujur tahap paceklik', '!bendera kota42.pejabat.bukti'])
    D('kota42_pejabat_shen', 'tokoh:shen_wan', [
        N('mulai', ('Ah, Tuan pejabat kita. Paceklik ini menyedihkan, menyedihkan. Aku dengar Tuan menyelidiki gudangku. Kenapa repot? '
                    '(Sebuah amplop merah tebal tergelincir ke atas meja.) Untuk teh Tuan.',
                    'Ah, our honourable official. This famine is sad, so sad. I hear you are looking into my warehouse. Why trouble '
                    'yourself? (A thick red envelope slides onto the table.) For your tea.'),
          gestur='k:sodor_amplop', prop='k:angpao', suara='kota42.suap',
          pilihan=[P(('(Tolak) Simpan amplopmu. Kita bertemu di Yamen.', '(Refuse) Keep your envelope. We meet at the Yamen.'),
                     syarat=['bendera kota42.pejabat.bukti'], akibat=['bendera kota42.pejabat.sita', 'quest_maju kota42_pejabat_jujur'], ke='tolak',
                     alasan=('Kau belum punya bukti.', 'You have no proof yet.')),
                   P(('(Terima) Teh yang mahal.', '(Accept) Expensive tea.'), akibat=['bendera kota42.pejabat.suap', 'quest_maju kota42_pejabat_jujur'],
                     ke='terima')]),
        N('tolak', ('(Senyumnya membeku.) Tuan akan menyesal. Kota ini berdiri di atas uangku.', '(His smile freezes.) You will regret this. This city stands on my money.'),
          gestur='marah_tunjuk'),
        N('terima', ('Tuan bijaksana. Paceklik akan berlalu, dan teman tetap teman.', 'You are wise. Famine passes, and friends remain friends.'), gestur='hormat'),
    ], prioritas=50, syarat=['quest kota42_pejabat_jujur tahap besar'])


# ================================================================================================ side stories (42.10 "Kait")
def alur_samping():
    # ---- Ah Qin: Lagu di Bawah Jembatan
    q = 'kota42_lagu_jembatan'
    Q(q, 'tokoh:ah_qin', 'kota42', ('Lagu di Bawah Jembatan', 'The Song Under the Bridge'),
      ('Ah Qin mencari nada lagu terakhir almarhum gurunya, "Bulan di Bawah Jembatan", di tiga kota.',
       'Ah Qin seeks the tune of her late master\'s last song, "Moon Under the Bridge", across three towns.'), [
        T('bait_satu', ('Lao Shuo pernah mendengar Shi Banqiao bermain. Tanyakan bait pertamanya.',
                        'Lao Shuo once heard Shi Banqiao play. Ask him for the first phrase.'),
          tujuan=['bendera kota42.lagu.satu']),
        T('bait_dua', ('Seorang penggemar opera di kota lain konon menyenandungkan lagu itu. Cari dia.',
                       'An opera lover in another town is said to hum that song. Find them.'),
          tujuan=['bicara_warga kebiasaan:penggemar_opera'], arah='kota/'),
        T('bait_tiga', ('Bait terakhir hanya diingat pemain erhu di Kota Air, tempat Shi Banqiao lahir.',
                        'The last phrase is remembered only by an erhu player in the Water City, where Shi Banqiao was born.'),
          tujuan=['pergi kota_tema/air', 'bicara_warga kebiasaan:pemain_erhu'], arah='kota_tema/air'),
        T('mainkan', ('Bawa ketiga bait itu kepada Ah Qin.', 'Bring all three phrases to Ah Qin.'),
          tujuan=['bendera kota42.lagu.main'],
          akibat=['kota42.kesan tokoh:ah_qin 40', 'efek minecraft:regeneration 60', 'kabar kota42_lagu_jembatan pemain'],
          hasil=('Di bawah jembatan batu, Ah Qin memainkan lagu gurunya dari awal sampai akhir untuk pertama kalinya. Pedagang berhenti '
                 'menawar, anak-anak berhenti berlari, dan untuk sejenak seluruh pasar mendengarkan bulan.',
                 'Under the stone bridge, Ah Qin played her master\'s song from beginning to end for the first time. Traders stopped '
                 'haggling, children stopped running, and for a moment the whole market listened to the moon.')),
    ])
    D('kota42_lagu_ah_qin', 'tokoh:ah_qin', [
        N('mulai', ('Langkahmu baru di jembatan ini. (Jarinya menggeser senar, nada panjang yang bergetar.) Lagu ini belum selesai. '
                    'Guruku mati sebelum mengajarkan akhirnya. Aku memainkan dua pertiga lagu setiap hari, dan setiap hari aku berhenti di tempat yang sama.',
                    'Your steps are new on this bridge. (Her fingers slide along the string, a long trembling note.) This song is not '
                    'finished. My master died before teaching me its end. I play two-thirds of it every day, and every day I stop at the same place.'),
          gestur='k:main_erhu', prop='k:erhu', suara='kota42.ah_qin',
          pilihan=[P(('Aku akan mencari sisa lagunya.', 'I will look for the rest of the song.'), syarat=['quest kota42_lagu_jembatan baru'],
                     akibat=['quest_mulai kota42_lagu_jembatan'], ke='terima'),
                   P(('Ceritakan tentang gurumu.', 'Tell me about your master.'), ke='guru'),
                   P(('(Beri dua keping)', '(Give two coins)'), syarat=['nb 2'], akibat=['bayar 2', 'kota42.kesan tokoh:ah_qin 3'], ke='koin'),
                   P(PAMIT)]),
        N('guru', ('Shi Banqiao. Ia bermain erhu di bawah jembatan selama empat puluh tahun dan tidak pernah meminta uang. Katanya, lagu '
                   'yang dibayar berhenti menjadi lagu. Ia meninggal pada malam bulan purnama, dengan busur masih di tangannya.',
                   'Shi Banqiao. He played the erhu under bridges for forty years and never asked for money. He said a song that is paid '
                   'for stops being a song. He died on a full-moon night, the bow still in his hand.'),
          gestur='k:main_erhu', prop='k:erhu', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('terima', ('Lao Shuo pernah duduk di samping guruku. Mulailah darinya. Dan... terima kasih. Sudah lama tidak ada yang bertanya.',
                     'Lao Shuo once sat beside my master. Start with him. And... thank you. It has been long since anyone asked.'),
          gestur='hormat'),
        N('koin', ('Terima kasih. Kau dengar? Senarnya ikut tersenyum.', 'Thank you. Do you hear? Even the strings are smiling.'),
          gestur='k:main_erhu', prop='k:erhu'),
    ], prioritas=10)
    D('kota42_lagu_lao_shuo', 'tokoh:lao_shuo', [
        N('mulai', ('Shi Banqiao! Tentu aku ingat. Lagu itu dimulai seperti air yang menetes dari dayung: tiga nada turun, satu nada '
                    'naik yang menggantung, seperti orang yang menunggu jawaban. (Ia menyenandungkannya pelan.)',
                    'Shi Banqiao! Of course I remember. That song begins like water dripping from an oar: three notes falling, one '
                    'rising note left hanging, like someone awaiting an answer. (He hums it softly.)'),
          gestur='k:dongeng', prop='kipas_lipat', akibat=['bendera kota42.lagu.satu']),
    ], prioritas=50, syarat=['quest kota42_lagu_jembatan tahap bait_satu'])
    D('kota42_lagu_main', 'tokoh:ah_qin', [
        N('mulai', ('Kau kembali. Langkahmu lebih berat, seperti membawa sesuatu. ...Kau menemukannya?',
                    'You are back. Your steps are heavier, as if you carry something. ...Did you find it?'),
          gestur='k:main_erhu', prop='k:erhu',
          pilihan=[P(('(Senandungkan ketiga bait)', '(Hum the three phrases)'), akibat=['bendera kota42.lagu.main'], ke='main')]),
        N('main', ('(Ia mendengarkan dengan kepala miring, bibirnya bergerak tanpa suara. Lalu busurnya menyentuh senar.) ...Jadi begini '
                   'akhirnya. Bulan tidak tenggelam. Bulan turun ke sungai dan tinggal di sana.',
                   '(She listens with her head tilted, lips moving without sound. Then her bow touches the strings.) ...So this is how '
                   'it ends. The moon does not set. The moon comes down to the river and stays there.'),
          gestur='k:main_erhu', prop='k:erhu', suara='kota42.ah_qin'),
    ], prioritas=50, syarat=['quest kota42_lagu_jembatan tahap mainkan'])

    # ---- Li Zuixian: tujuh malam minum
    q = 'kota42_tujuh_malam'
    Q(q, 'tokoh:li_zuixian', 'kota42', ('Tujuh Malam Bersama Penyair', 'Seven Nights with the Poet'),
      ('Li Zuixian bersumpah akan memberikan giok tekniknya kepada siapa pun yang menemaninya minum tujuh malam dan tidak tertidur '
       'sebelum bait terakhir.',
       'Li Zuixian swears he will give his technique jade to whoever drinks with him for seven nights and does not fall asleep '
       'before the last verse.'), [
        T('minum', ('Temani Li Zuixian minum arak bunga persik tujuh malam di kedainya.',
                    'Drink peach-blossom wine with Li Zuixian for seven nights at his tavern.'),
          tujuan=['tanda kota42.minum_li_zuixian x7']),
        T('giok', ('Malam ketujuh. Dengarkan bait terakhirnya.', 'The seventh night. Hear his last verse.'),
          tujuan=['bendera kota42.zuixian.giok'], akibat=['beri kultivator:jade_slip', 'kota42.kesan tokoh:li_zuixian 40'],
          hasil=('Di bawah bulan ketujuh, Li Zuixian menyelipkan sekeping giok ke tanganmu. "Jurus Pedang Mabuk Delapan Dewa," katanya. '
                 '"Guruku memberikannya padaku karena aku tidak pernah bisa berdiri tegak. Kau berdiri terlalu tegak. Belajarlah goyah."',
                 'Under the seventh moon, Li Zuixian slipped a jade into your hand. "The Drunken Eight Immortals Sword," he said. "My '
                 'master gave it to me because I could never stand straight. You stand too straight. Learn to sway."')),
    ])
    D('kota42_zuixian_sapa', 'tokoh:li_zuixian', [
        N('mulai', ('Ah, wajah baru! Duduk, duduk. Aku sedang menulis bait tentang bulan yang jatuh ke dalam cawan, tapi cawannya terus '
                    'kosong sebelum baitnya selesai. Kau tahu kenapa? Karena aku meminumnya!',
                    'Ah, a new face! Sit, sit. I am writing a verse about the moon falling into a cup, but the cup keeps emptying before '
                    'the verse is done. Do you know why? Because I drink it!'),
          gestur='k:nyanyi_mabuk', prop='k:cawan_arak', suara='kota42.mabuk',
          pilihan=[P(('Kudengar Tuan menyimpan giok teknik.', 'I hear you keep a technique jade.'), syarat=['quest kota42_tujuh_malam baru'],
                     akibat=['quest_mulai kota42_tujuh_malam'], ke='giok'),
                   P(('(Minum bersama: secawan arak bunga persik)', '(Drink together: a cup of peach-blossom wine)'),
                     syarat=['quest kota42_tujuh_malam tahap minum', 'jam 19 24', 'punya kultivator:hidangan/arak_bunga_persik'],
                     akibat=['ambil kultivator:hidangan/arak_bunga_persik', 'peristiwa tanda kota42.minum_li_zuixian', 'klip k:bersulang k:cawan_arak'],
                     ke='minum', alasan=('Bawa arak bunga persik, dan datanglah malam hari.', 'Bring peach-blossom wine, and come at night.')),
                   P(('Ujian negara? Ceritakan.', 'The state exam? Tell me.'), ke='ujian'),
                   P(('Tuan sebaiknya berhenti minum.', 'You should stop drinking.'), ke='berhenti'),
                   P(PAMIT)]),
        N('giok', ('Hah! Benar. Tujuh malam, tujuh cawan, tujuh bait. Kalau kau masih bangun di bait ketujuh, gioknya milikmu. '
                   'Bawa arakmu sendiri, aku penyair, bukan saudagar.',
                   'Hah! True. Seven nights, seven cups, seven verses. If you are still awake at the seventh verse, the jade is yours. '
                   'Bring your own wine, I am a poet, not a merchant.'),
          gestur='k:deklamasi', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('minum', ('"Bulan di dalam cawan, cawan di dalam tangan, tangan di dalam bulan..." Hik. Bagus, bagus! Kau masih bangun. '
                    'Datang lagi besok malam!',
                    '"The moon in the cup, the cup in the hand, the hand in the moon..." Hic. Good, good! You are still awake. Come again '
                    'tomorrow night!'),
          gestur='k:huaquan_a', suara='kota42.huaquan'),
        N('ujian', ('Tiga kali! Kali pertama aku menulis bahwa penguji itu lebih bodoh dari kerbaunya. Kali kedua aku menulis bahwa kerbaunya '
                    'tersinggung. Kali ketiga... aku tidak ingat, aku mabuk. Tapi bait-baitku masih dinyanyikan di setiap festival lentera.',
                    'Three times! The first time I wrote that the examiner was dumber than his ox. The second, that the ox was offended. '
                    'The third... I do not remember, I was drunk. But my verses are still sung at every lantern festival.'),
          gestur='tertawa', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('berhenti', ('Berhenti? Kalau aku berhenti minum, aku harus berhenti menulis. Kalau aku berhenti menulis, aku harus jadi pejabat. '
                       'Mana yang lebih buruk untuk kota ini?',
                       'Stop? If I stop drinking, I must stop writing. If I stop writing, I must become an official. Which is worse for '
                       'this city?'), gestur='berdebat', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=10)
    D('kota42_zuixian_giok', 'tokoh:li_zuixian', [
        N('mulai', ('Malam ketujuh, dan kau masih bangun! "Arak habis, bulan masih penuh; teman pergi, bait masih tinggal." ...Itu bait '
                    'ketujuh. Sekarang ulurkan tanganmu.',
                    'The seventh night, and you are still awake! "The wine is gone, the moon still full; the friend departs, the verse '
                    'remains." ...That is the seventh verse. Now hold out your hand.'),
          gestur='k:deklamasi', akibat=['bendera kota42.zuixian.giok']),
    ], prioritas=50, syarat=['quest kota42_tujuh_malam tahap giok'])

    # ---- Hu Lu: dibongkar atau bermitra
    q = 'kota42_tabib_keliling'
    Q(q, 'peran:tabib', 'kota42', ('Pil Seribu Khasiat', 'The Thousand-Virtue Pills'),
      ('Tabib kota curiga separuh pil Tabib Keliling Hu Lu hanyalah tepung beras berwarna.',
       'The town physician suspects half of travelling physician Hu Lu\'s pills are nothing but coloured rice flour.'), [
        T('beli', ('Beli sebutir "Pil Seribu Khasiat" dari Hu Lu.', 'Buy one "Thousand-Virtue Pill" from Hu Lu.'), tujuan=['bendera kota42.hulu.beli']),
        T('uji', ('Bawa pil itu kepada tabib kota untuk diperiksa.', 'Take the pill to the town physician to be examined.'),
          tujuan=['bendera kota42.hulu.uji']),
        T('hadapi', ('Pil itu tepung beras. Hadapi Hu Lu: bongkar dia di depan pasar, atau jadikan dia mitra yang jujur.',
                     'The pill is rice flour. Confront Hu Lu: expose him before the market, or make him an honest partner.'),
          cabang=[(['bendera kota42.hulu.bongkar'], 'bongkar'), (['bendera kota42.hulu.mitra'], 'mitra')]),
        T('bongkar', ('Hu Lu dibongkar.', 'Hu Lu is exposed.'), tujuan=['hari 0'],
          akibat=['nama_baik 3', 'kota42.kesan tokoh:hu_lu -40', 'kabar kota42_hulu_bongkar pemain'], ke='selesai',
          hasil=('Pasar mengusir Hu Lu dengan kubis busuk. Rombongan Bulan Sabit masih membawanya ke kota berikutnya, tetapi labu '
                 'obatnya kini hanya berisi pil yang manjur, dan harganya tiga kali lipat.',
                 'The market drove Hu Lu out with rotten cabbages. The Crescent Moon Troupe still takes him to the next town, but his '
                 'gourd now holds only pills that work, at three times the price.')),
        T('mitra', ('Hu Lu menjadi mitramu.', 'Hu Lu becomes your partner.'), tujuan=['hari 0'],
          akibat=['kota42.saham tokoh:hu_lu 20', 'kota42.kesan tokoh:hu_lu 30', 'kabar kota42_hulu_mitra'], ke='selesai',
          hasil=('Hu Lu membuang tepung berasnya ke sungai dan menjual separuh pil yang manjur dengan harga jujur. Seperlima labanya '
                 'kini sampai ke kantongmu, dan anehnya ia tampak lebih bahagia.',
                 'Hu Lu tipped his rice flour into the river and sells the genuine half at an honest price. A fifth of his profit now '
                 'reaches your purse, and strangely he looks happier.')),
    ])
    D('kota42_hulu_tabib', 'peran:tabib', [
        N('mulai', ('Tiga pasien datang padaku minggu ini, semuanya minum "Pil Seribu Khasiat" dan semuanya makin sakit. Aku tidak bisa '
                    'menuduh tanpa bukti. Belikan aku sebutir pil itu.',
                    'Three patients came to me this week, all of them took "Thousand-Virtue Pills" and all got worse. I cannot accuse '
                    'without proof. Buy me one of those pills.'),
          gestur='periksa_nadi', pilihan=[P(('Baik.', 'Very well.'), akibat=['quest_mulai kota42_tabib_keliling'])]),
    ], prioritas=35, syarat=['quest kota42_tabib_keliling baru', 'hari 8', 'acak 40'], sekali=True)
    D('kota42_hulu_sapa', 'tokoh:hu_lu', [
        N('mulai', ('Mampir, mampir! Pil Seribu Khasiat! Sakit kepala, sakit hati, sakit kantong: satu butir, seribu khasiat! Untuk '
                    'Tuan, harga teman.',
                    'Step up, step up! Thousand-Virtue Pills! Headaches, heartaches, purse-aches: one pill, a thousand virtues! For you, '
                    'a friend\'s price.'),
          gestur='k:beri_koin', prop='k:kotak_obat_keliling', suara='kota42.penjual_obat',
          pilihan=[P(('(Beli satu, 3 keping)', '(Buy one, 3 coins)'), syarat=['nb 3', 'quest kota42_tabib_keliling tahap beli'],
                     akibat=['bayar 3', 'bendera kota42.hulu.beli'], ke='beli', alasan=('Uangmu kurang.', 'You do not have enough money.')),
                   P(('Pil itu benar-benar manjur?', 'Do those pills really work?'), ke='manjur'),
                   P(('Kau penipu, Hu Lu.', 'You are a fraud, Hu Lu.'), syarat=['quest kota42_tabib_keliling tahap hadapi'], ke='hadapi'),
                   P(PAMIT)]),
        N('beli', ('Satu butir untuk Tuan! Telan dengan teh hangat, jangan dengan arak, jangan saat bulan mati, jangan sambil marah. '
                   'Kalau tidak manjur, pasti karena salah satu itu.',
                   'One pill for you! Swallow with warm tea, not wine, not on a moonless night, not while angry. If it fails, it must be '
                   'one of those.'), gestur='tertawa'),
        N('manjur', ('Separuh... ehm, sepenuhnya manjur! Lihat, aku sehat, kan? Itu karena aku meminumnya setiap hari. Yah, yang warna hijau.',
                     'Half... ahem, fully effective! Look, I am healthy, aren\'t I? That is because I take them every day. Well, the green ones.'),
          gestur='k:garuk_kepala', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        N('hadapi', ('Ssst! Jangan keras-keras! ...Baiklah, baiklah. Separuhnya tepung. Tapi separuhnya lagi resep nenekku, dan itu sungguh '
                     'manjur. Aku cuma tidak punya cukup herba untuk semuanya.',
                     'Shh! Not so loud! ...All right, all right. Half is flour. But the other half is my grandmother\'s recipe, and that '
                     'truly works. I just never have enough herbs for all of it.'),
          gestur='bisik',
          pilihan=[P(('(Bongkar) Semua orang di pasar harus tahu.', '(Expose) Everyone at the market must know.'),
                     akibat=['bendera kota42.hulu.bongkar', 'quest_maju kota42_tabib_keliling'], ke='bongkar'),
                   P(('(Bermitra) Jual yang manjur saja. Aku yang bayar herbanya, kita bagi laba.',
                      '(Partner) Sell only the real ones. I pay for the herbs, we share the profit.'),
                     akibat=['bendera kota42.hulu.mitra', 'quest_maju kota42_tabib_keliling'], ke='mitra')]),
        N('bongkar', ('Tidak, tidak! Kubis! Bukan kubis! (Ia berlari sambil memeluk labunya.)', 'No, no! Cabbages! Not cabbages! (He runs off hugging his gourd.)'),
          gestur='lari_takut'),
        N('mitra', ('Jujur? Aku? ...Nenekku akan tertawa di kuburnya. Baiklah, Tuan. Mitra.', 'Honest? Me? ...My grandmother will laugh in her grave. Very well. Partners.'),
          gestur='hormat'),
    ], prioritas=10)
    D('kota42_hulu_uji', 'peran:tabib', [
        N('mulai', ('(Ia menghancurkan pil itu di lumpang, mencium, mencicipi dengan ujung lidah.) Tepung beras, gula aren, dan pewarna '
                    'kunyit. Tidak ada satu helai herba pun. Tapi... yang ini lain. (Ia mengangkat butir kedua.) Ini sungguhan.',
                    '(He crushes the pill in a mortar, sniffs, tastes it with the tip of his tongue.) Rice flour, palm sugar and turmeric '
                    'dye. Not a single herb. But... this one is different. (He holds up a second pill.) This one is real.'),
          gestur='tumbuk_herba', prop='alu', akibat=['bendera kota42.hulu.uji']),
    ], prioritas=50, syarat=['quest kota42_tabib_keliling tahap uji'])

    # ---- Yan Feihu jatuh cinta
    q = 'kota42_hati_feihu'
    Q(q, 'tokoh:yan_feiyan', 'kota42', ('Puncak Piramida yang Jatuh Cinta', 'The Top of the Pyramid Falls in Love'),
      ('Yan Feihu, akrobat termuda Rombongan Bulan Sabit, jatuh cinta pada gadis penjual bunga dan ingin meninggalkan rombongan.',
       'Yan Feihu, the youngest acrobat of the Crescent Moon Troupe, has fallen for a flower girl and wants to leave the troupe.'), [
        T('dengar', ('Bicaralah dengan Yan Feihu.', 'Speak with Yan Feihu.'), tujuan=['bicara tokoh:yan_feihu']),
        T('comblang', ('Bibi Mei tahu setiap gadis di kota ini. Tanyakan tentang penjual bunga itu.',
                       'Auntie Mei knows every girl in this city. Ask her about the flower girl.'),
          tujuan=['bendera kota42.feihu.comblang']),
        T('putus', ('Rombongan berangkat besok. Bantu Feihu memilih: tinggal di kota demi cintanya, atau pergi bersama kakaknya.',
                    'The troupe leaves tomorrow. Help Feihu choose: stay in the city for love, or leave with his sister.'),
          cabang=[(['bendera kota42.feihu.tinggal'], 'tinggal'), (['bendera kota42.feihu.pergi'], 'pergi')]),
        T('tinggal', ('Feihu tinggal.', 'Feihu stays.'), tujuan=['hari 0'],
          akibat=['kota42.pindah tokoh:yan_feihu kota/', 'kota42.kesan tokoh:yan_feihu 40', 'kota42.kesan tokoh:yan_feiyan 10',
                  'kabar kota42_feihu_tinggal'], ke='selesai',
          hasil=('Feihu menjual sepatu akrobatnya dan membuka kios di sebelah kios bunga. Ia masih berjungkir balik setiap pagi, hanya '
                 'untuk membuat seorang gadis tertawa. Feiyan menangis di gerbang, lalu tersenyum.',
                 'Feihu sold his acrobat shoes and opened a stall next to the flower stall. He still somersaults every morning, just '
                 'to make one girl laugh. Feiyan cried at the gate, then smiled.')),
        T('pergi', ('Feihu pergi bersama rombongan.', 'Feihu leaves with the troupe.'), tujuan=['hari 0'],
          akibat=['kota42.kesan tokoh:yan_feiyan 40', 'kota42.kesan tokoh:hong_ling 20', 'kabar kota42_feihu_pergi'], ke='selesai',
          hasil=('Feihu naik ke puncak piramida sekali lagi di pertunjukan perpisahan dan melempar setangkai bunga ke kerumunan. Gadis '
                 'itu menangkapnya. Rombongan berjanji kembali musim semi depan.',
                 'Feihu climbed to the top of the pyramid once more at the farewell show and threw a flower into the crowd. The girl '
                 'caught it. The troupe promised to return next spring.')),
    ])
    D('kota42_feihu_mulai', 'tokoh:yan_feiyan', [
        N('mulai', ('Adikku jatuh dari puncak piramida kemarin. Tidak pernah sekali pun dalam sepuluh tahun! Matanya terus melihat ke kios '
                    'bunga di sudut pasar. Kalau ia berhenti fokus, ia bisa patah leher. Tolong bicara dengannya.',
                    'My brother fell from the top of the pyramid yesterday. Not once in ten years! His eyes keep drifting to the flower '
                    'stall at the corner of the market. If he stops focusing, he could break his neck. Please talk to him.'),
          gestur='k:akrobat_piring', prop='k:piring_akrobat',
          pilihan=[P(('Aku akan bicara dengannya.', 'I will talk to him.'), akibat=['quest_mulai kota42_hati_feihu']), P(PAMIT)]),
    ], prioritas=35, syarat=['quest kota42_hati_feihu baru', 'hari 3'], sekali=True)
    D('kota42_feihu_dengar', 'tokoh:yan_feihu', [
        N('mulai', ('Kakak menyuruhmu, kan? Aku tidak jatuh karena lengah! Aku jatuh karena... karena ia tertawa. Gadis bunga itu. '
                    'Tawanya seperti lonceng angin. Aku bahkan tidak tahu namanya.',
                    'My sister sent you, didn\'t she? I did not fall from carelessness! I fell because... because she laughed. The flower '
                    'girl. Her laugh is like wind chimes. I do not even know her name.'),
          gestur='k:garuk_kepala'),
    ], prioritas=50, syarat=['quest kota42_hati_feihu tahap comblang', '!bendera kota42.feihu.comblang'])
    D('kota42_feihu_comblang', 'tokoh:bibi_mei', [
        N('mulai', ('Gadis bunga di sudut pasar? Ah, itu cucu tukang kebun kuil. Rajin, manis, dan sudah menolak tiga lamaran pedagang. '
                    'Ia bilang ia mau menikah dengan orang yang bisa membuatnya terkejut. Hm! Akrobat, katamu?',
                    'The flower girl at the market corner? Ah, that is the temple gardener\'s granddaughter. Diligent, sweet, and she has '
                    'refused three merchants\' proposals. She says she will marry someone who can surprise her. Hm! An acrobat, you say?'),
          gestur='k:bercermin', prop='k:almanak_merah', suara='kota42.comblang', akibat=['bendera kota42.feihu.comblang']),
    ], prioritas=50, syarat=['quest kota42_hati_feihu tahap comblang', '!bendera kota42.feihu.comblang'])
    D('kota42_feihu_putus', 'tokoh:yan_feihu', [
        N('mulai', ('Ia bilang ia suka orang yang bisa membuatnya terkejut. Aku bisa jungkir balik tiga kali di udara! Tapi rombongan berangkat '
                    'besok, dan Kakak... Kakak tidak punya siapa-siapa lagi selain aku.',
                    'She says she likes someone who can surprise her. I can somersault three times in the air! But the troupe leaves '
                    'tomorrow, and my sister... my sister has no one but me.'),
          gestur='bicara_tangan',
          pilihan=[P(('(Tinggal) Kakakmu kuat. Ikuti hatimu.', '(Stay) Your sister is strong. Follow your heart.'),
                     akibat=['bendera kota42.feihu.tinggal', 'quest_maju kota42_hati_feihu'], ke='tinggal'),
                   P(('(Pergi) Rombongan keluargamu. Kembalilah musim semi depan.', '(Leave) The troupe is your family. Come back next spring.'),
                     akibat=['bendera kota42.feihu.pergi', 'quest_maju kota42_hati_feihu'], ke='pergi')]),
        N('tinggal', ('Aku akan bilang pada Kakak malam ini. Lalu aku akan jungkir balik di depan kios bunga, empat kali!',
                      'I will tell my sister tonight. Then I will somersault in front of the flower stall, four times!'), gestur='k:egrang_jalan'),
        N('pergi', ('...Kau benar. Tapi aku akan melempar bunga dari puncak piramida besok. Kalau ia menangkapnya, aku pasti kembali.',
                    '...You are right. But tomorrow I will throw a flower from the top of the pyramid. If she catches it, I will surely come back.'),
          gestur='k:piramid_atas'),
    ], prioritas=50, syarat=['quest kota42_hati_feihu tahap putus'])


# ================================================================================================ greetings of every named character
def mulai_lagi(q, teks):
    """A choice on a greeting that (re)starts quest q when it has not started yet (the one-time opening dialog was declined)."""
    return P(teks, syarat=[f'quest {q} baru'], akibat=[f'quest_mulai {q}'])


def isi_sapa():
    sapa('kota42_sapa_hong_ling', 'tokoh:hong_ling',
         ('Rombongan Bulan Sabit! Akrobat, barongsai, dongeng dan lagu! Mau menonton atau mau memesan? Barongsai untuk peresmian '
          'rumah baru, harga musim ini masih bersahabat.',
          'The Crescent Moon Troupe! Acrobats, lion dance, tales and songs! Here to watch or to book? Lion dance for a new house '
          'opening, this season\'s price is still friendly.'),
         [('rombongan', ('Ceritakan tentang rombonganmu.', 'Tell me about your troupe.'),
           ('Dua puluh orang, tiga gerobak, satu keledai yang lebih keras kepala dari Lao Shuo. Kami singgah sepekan di setiap kota, '
            'lalu jalan lagi. Rumah kami adalah jalan antara dua kota.',
            'Twenty people, three carts, one donkey more stubborn than Lao Shuo. We stay a week in every town, then move on. Our home '
            'is the road between two towns.'), dict(gestur='k:pimpin')),
          ('tali', ('Benarkah Nyonya dulu penari tali?', 'Were you really a rope dancer?'),
           ('Tali sepanjang dua puluh langkah di atas sungai Qiantang, tanpa jaring. Lututku sekarang berbunyi seperti kayu bakar, '
            'jadi aku pindah ke pekerjaan yang lebih berbahaya: memimpin dua puluh seniman.',
            'A rope twenty paces long over the Qiantang river, no net. My knees creak like firewood now, so I moved to a more dangerous '
            'trade: leading twenty artists.'), dict(gestur='tertawa')),
          ('barongsai', ('Kenapa barongsai untuk rumah baru?', 'Why a lion dance for a new house?'),
           ('Singa mengusir roh jahat yang suka bersembunyi di sudut yang masih bau kapur. Dan tetangga jadi tahu ada orang baru yang '
            'cukup kaya untuk menyewa singa.',
            'The lion drives off evil spirits that like hiding in corners still smelling of lime. And the neighbours learn someone new '
            'is rich enough to hire a lion.'), dict(gestur='k:barongsai_kepala'))],
         gestur='k:pimpin', suara='kota42.akrobat_buka')
    sapa('kota42_sapa_yan_feiyan', 'tokoh:yan_feiyan',
         ('Hati-hati, piringnya masih berputar! ...Nah. Selamat datang. Pertunjukan berikutnya setelah lonceng sore.',
          'Careful, the plates are still spinning! ...There. Welcome. The next show is after the evening bell.'),
         [('latihan', ('Bagaimana bisa berdiri di atas piring?', 'How can you stand on plates?'),
           ('Sepuluh ribu kali jatuh. Yang kesepuluh ribu satu, kau berdiri. Begitu saja. Adikku butuh lima ribu, dan ia tidak pernah '
            'berhenti membanggakannya.',
            'Ten thousand falls. On the ten thousand and first, you stand. That is all. My brother needed five thousand, and he never '
            'stops bragging about it.'), dict(gestur='k:akrobat_piring', prop='k:piring_akrobat')),
          ('sekolah', ('Apa cita-citamu?', 'What is your dream?'),
           ('Sekolah akrobat untuk anak-anak yatim banjir, seperti kami dulu. Aku sudah menabung delapan puluh keping. Butuh seribu.',
            'An acrobat school for flood orphans, like we once were. I have saved eighty coins. I need a thousand.'), dict(gestur='bicara_tangan'))],
         gestur='k:akrobat_piring', prop='k:piring_akrobat',
         awal=[mulai_lagi('kota42_hati_feihu', ('Kau tampak khawatir. Soal adikmu?', 'You look worried. About your brother?'))])
    sapa('kota42_sapa_yan_feihu', 'tokoh:yan_feihu',
         ('Lihat ini! (Ia berjungkir balik dua kali dan mendarat di satu tangan.) Hah! Kakakku butuh dua kali lebih lama untuk '
          'belajar itu.',
          'Watch this! (He flips twice and lands on one hand.) Hah! My sister took twice as long to learn that.'),
         [('puncak', ('Bagaimana rasanya di puncak piramida?', 'What is it like at the top of the pyramid?'),
           ('Seperti jadi raja. Semua orang melihat ke atas, dan tidak ada yang tahu kaki di bawahku gemetar. Kaki Kakak, maksudku.',
            'Like being a king. Everyone looks up, and nobody knows the legs beneath me are shaking. My sister\'s legs, I mean.'),
           dict(gestur='k:piramid_atas')),
          ('menetap', ('Pernah ingin menetap di satu kota?', 'Ever want to settle in one town?'),
           ('Menetap? Itu untuk orang tua dan pohon. ...Kenapa kau tanya? Ada yang bilang sesuatu tentang kios bunga?',
            'Settle? That is for old people and trees. ...Why do you ask? Did someone say something about the flower stall?'),
           dict(gestur='k:garuk_kepala'))],
         gestur='k:egrang_jalan')
    sapa('kota42_sapa_ma_fu', 'tokoh:ma_fu',
         ('Saudagar Ma Fu, kafilah dari tujuh kota! Sutra, garam, batu roh dari tambang yang sudah tutup. Kalau tidak ada di kafilahku, '
          'berarti belum ditemukan.',
          'Merchant Ma Fu, caravans from seven towns! Silk, salt, spirit stones from closed mines. If my caravan does not have it, it '
          'has not been found yet.'),
         [('langka', ('Barang langka apa yang Tuan bawa?', 'What rare goods do you carry?'),
           ('Minggu ini: teh awan ungu dari tebing yang hanya dipetik monyet, dan kristal garam merah dari danau mati. Harganya? '
            'Tergantung seberapa kau menginginkannya.',
            'This week: purple cloud tea from cliffs only monkeys can pick, and red salt crystals from a dead lake. The price? '
            'Depends how much you want it.'), dict(gestur='tawar')),
          ('begal', ('Jalan kafilah aman?', 'Are the caravan roads safe?'),
           ('Aman untuk yang membayar pengawal. Begal selatan makin berani sejak sekte pelindung di sana malas turun gunung.',
            'Safe for those who pay guards. The southern bandits grow bolder since the protector sect there grew too lazy to come down.'),
           dict(gestur='hitung_sempoa', prop='sempoa')),
          ('utang', ('Kudengar Tuan tidak pernah lupa utang.', 'I hear you never forget a debt.'),
           ('Utang itu seperti unta: kalau tidak diberi makan, ia tetap hidup dan tetap meminta air. Aku hanya mengingatkan unta-unta itu.',
            'A debt is like a camel: unfed, it still lives and still asks for water. I only remind the camels.'), dict(gestur='tertawa'))],
         gestur='seru_dagang')
    sapa('kota42_sapa_xiao_bang', 'tokoh:xiao_bang',
         ('Eh! Kaget! Maaf, aku pikir bayanganmu anjing hitam. Selamat... selamat siang? Aku baru bangun. Penjaga malam tidur siang hari.',
          'Eh! You startled me! Sorry, I thought your shadow was a black dog. Good... good afternoon? I just woke up. Night watchmen sleep by day.'),
         [('takut', ('Kau penjaga malam yang takut gelap?', 'A night watchman afraid of the dark?'),
           ('Guru bilang justru itu gunanya. Orang yang tidak takut gelap tidak akan memperhatikan apa yang bersembunyi di dalamnya.',
            'Master says that is the point. Someone unafraid of the dark never notices what hides in it.'), dict(gestur='takut_sembunyi')),
          ('guru', ('Bagaimana Lao Geng memungutmu?', 'How did Lao Geng take you in?'),
           ('Aku mencuri bakpao dari gerobak waktu umurku tujuh tahun. Guru menangkapku, membeli dua bakpao lagi, dan menyuruhku memegang '
            'lenteranya sampai pagi. Aku tidak pernah pulang ke Kumuh lagi.',
            'I stole a bun from a cart when I was seven. Master caught me, bought two more buns, and told me to hold his lantern until '
            'morning. I never went back to the Slums.'), dict(gestur='k:pukul_kentongan', prop='k:kentongan'))],
         gestur='kaget')
    sapa('kota42_sapa_bibi_mei', 'tokoh:bibi_mei',
         ('Aduh, wajah yang beruntung! Alis tebal, telinga panjang. Sudah menikah? Belum? Aku punya almanak yang bisa mengubah itu.',
          'Oh, a lucky face! Thick brows, long ears. Married yet? No? I have an almanac that can change that.'),
         [('jodoh', ('Bagaimana Bibi menjodohkan orang?', 'How do you match people?'),
           ('Delapan karakter kelahiran, mas kawin, gosip sumur dan sedikit dupa. Sisanya intuisi. Dua ratus pasangan, dan hanya tiga '
            'yang bercerai. Yah, empat, tapi yang keempat itu salah almanaknya.',
            'Eight birth characters, the bride price, well gossip and a little incense. The rest is intuition. Two hundred couples, and '
            'only three divorced. Well, four, but the fourth was the almanac\'s fault.'), dict(gestur='k:bercermin', prop='k:almanak_merah')),
          ('pemain', ('Aku tidak mencari jodoh, Bi.', 'I am not looking for a match, Auntie.'),
           ('Semua orang bilang begitu sampai mereka melihat orang yang tepat di festival lentera. Datanglah ke festival, pakai baju '
            'yang bagus. Bibi tidak memaksa. Belum.',
            'Everyone says that until they see the right person at the lantern festival. Come to the festival, wear something nice. '
            'Auntie is not pushing. Yet.'), dict(gestur='tertawa')),
          ('kabar', ('Ada kabar apa di Hunian Barat?', 'Any news in the West Residences?'), None, dict(gestur='bisik', kabar=True))],
         gestur='k:bercermin', prop='k:almanak_merah', suara='kota42.comblang')
    for bibi, buka, topik in (
            ('bibi_zhang', ('Mampir, mampir! Masih hangat bakpaonya, ambil satu. Jangan bilang Bibi Li, ia akan menghitung berapa yang kubagi.',
                            'Come by, come by! The buns are still warm, take one. Don\'t tell Auntie Li, she will count how many I give away.'),
             [('keluarga', ('Bagaimana keponakan Bibi?', 'How is your nephew?'),
               ('Zhang Shan? Anak baik, kerja keras, terlalu pendiam. Ia mengukir burung kayu sampai larut malam. Untuk siapa, coba? '
                'Bibi tahu, tapi Bibi tidak bilang.',
                'Zhang Shan? A good boy, hardworking, too quiet. He carves wooden birds late into the night. For whom, I wonder? Auntie '
                'knows, but Auntie won\'t say.'), dict(gestur='bisik'))]),
            ('bibi_li', ('Kau lagi. Tiga kali lewat sumur ini hari ini. Mau menimba atau mau mengintip?',
                         'You again. Three times past this well today. Here to draw water or to snoop?'),
             [('curiga', ('Bibi selalu curiga pada semua orang?', 'Are you suspicious of everyone, Auntie?'),
               ('Bukan curiga. Teliti. Waktu semua orang percaya pedagang garam itu jujur, aku yang melihat timbangannya miring. Sekarang '
                'ia menjual garam di penjara.',
                'Not suspicious. Careful. When everyone trusted that salt seller, I was the one who saw his scale tilt. Now he sells salt '
                'in prison.'), dict(gestur='k:intip'))]),
            ('bibi_wang', ('Hati-hati di jalan! Kemarin ada serigala di gang ikan! ...Yah, anjing besar. Tapi galak sekali!',
                           'Careful on the road! Yesterday there was a wolf in fish alley! ...Well, a big dog. But very fierce!'),
             [('takhayul', ('Bibi percaya hantu?', 'Do you believe in ghosts, Auntie?'),
               ('Percaya? Aku melihatnya! Setiap malam bulan mati ada lentera hijau melayang di ujung Kumuh. Orang bilang itu pasar hantu. '
                'Aku bilang itu hantu pasar. Beda!',
                'Believe? I have seen them! Every moonless night a green lantern floats at the end of the Slums. People call it a ghost '
                'market. I call it a market ghost. Different!'), dict(gestur='takut_sembunyi'))])):
        topik = list(topik) + [('kabar', ('Ada gosip apa hari ini?', 'Any gossip today?'), None, dict(gestur='bisik', kabar=True))]
        awal = []
        if bibi == 'bibi_zhang':
            awal = [mulai_lagi('kota42_dua_keluarga', ('Soal Zhang Shan dan putri kedai arak...', 'About Zhang Shan and the wine shop girl...'))]
        if bibi == 'bibi_wang':
            awal = [mulai_lagi('kota42_seribu_jari', ('Dompet Bibi sudah ketemu?', 'Did you find your purse, Auntie?'))]
        sapa(f'kota42_sapa_{bibi}', f'tokoh:{bibi}', buka, topik, gestur='timba_sumur',
             suara='kota42.gosip_tawa' if bibi == 'bibi_zhang' else '', awal=awal)
    sapa('kota42_sapa_kakek_xiang', 'tokoh:kakek_xiang',
         ('Duduk, anak muda. Jangan bicara saat Che sedang berpikir. Ia sudah berpikir sejak subuh.',
          'Sit, young one. Don\'t talk while Che is thinking. He has been thinking since dawn.'),
         [('catur', ('Siapa yang biasanya menang?', 'Who usually wins?'),
           ('Aku menang yang pertama, ia menang yang kedua. Empat puluh tahun seperti itu. Jangan bilang padanya bahwa yang kedua itu '
            'hadiahku. Orang tua butuh alasan untuk bangun pagi.',
            'I win the first, he wins the second. Forty years like that. Don\'t tell him the second is my gift. Old men need a reason to '
            'get up in the morning.'), dict(gestur='catur', prop='papan_catur')),
          ('kota', ('Kakek ingat kota ini waktu masih muda?', 'Do you remember this city when you were young?'),
           ('Dulu gerbang barat itu masih kayu, dan pohon huai ini masih setinggi pinggangku. Kakekmu mungkin pernah lewat di jalan ini. '
            'Kota berganti wajah, tapi sumur tetap di tempatnya.',
            'The west gate was still wood then, and this pagoda tree only waist-high. Your grandfather may have walked this road. A city '
            'changes its face, but the well stays where it is.'), dict(gestur='memandang'))],
         gestur='catur', prop='papan_catur')
    sapa('kota42_sapa_kakek_che', 'tokoh:kakek_che',
         ('Ssst! Lihat langkah ini. Kuda ke sini, meriam ke sana... SKAK! Hah! Xiang, kau kalah lagi!',
          'Shh! Watch this move. Horse here, cannon there... CHECK! Hah! Xiang, you lose again!'),
         [('tetua', ('Benarkah Kakek pernah mengalahkan tetua sekte?', 'Did you really beat a sect elder?'),
           ('Dua belas langkah! Ia datang dengan jubah awan dan pergi dengan wajah merah. Namanya? Ah, aku lupa. Tapi langkahnya aku ingat '
            'semua! Kuda, meriam, kereta...',
            'Twelve moves! He came in a cloud robe and left with a red face. His name? Ah, I forget. But every move I remember! Horse, '
            'cannon, chariot...'), dict(gestur='catur', prop='papan_catur', suara='kota42.catur_skak')),
          ('tantang', ('Mau main denganku, Kek?', 'Will you play with me, Grandpa?'),
           ('Denganmu? Hmph. Kembalilah setelah sepuluh tahun latihan. Atau besok pagi, kalau Xiang terlambat.',
            'With you? Hmph. Come back after ten years of practice. Or tomorrow morning, if Xiang is late.'), dict(gestur='sikap_angkuh'))],
         gestur='catur', prop='papan_catur')
    sapa('kota42_sapa_shi_xiaoqian', 'tokoh:shi_xiaoqian',
         ('Wah, kantongmu tebal. Jangan khawatir, aku sedang libur. (Ia memutar sekeping koin di antara jari-jarinya, koin itu menghilang.)',
          'My, your purse is fat. Don\'t worry, I am on holiday. (He rolls a coin across his knuckles, and it vanishes.)'),
         [('jari', ('Bagaimana kau melakukannya?', 'How do you do that?'),
           ('Sepuluh tahun lapar mengajarkan jari-jari lebih cepat daripada guru mana pun. Mau tahu di mana koinnya? Periksa sakumu.',
            'Ten years of hunger teach fingers faster than any master. Want to know where the coin is? Check your pocket.'),
           dict(gestur='k:hitung_koin')),
          ('anak', ('Anak-anak itu bekerja untukmu?', 'Do those children work for you?'),
           ('Mereka makan karena aku. Kalau kota ini memberi mereka nasi, mereka tidak perlu jari secepat ini.',
            'They eat because of me. If this city gave them rice, they would not need fingers this quick.'), dict(gestur='marah_tunjuk'))],
         gestur='k:hitung_koin')
    sapa('kota42_sapa_lentera_hijau', 'tokoh:lentera_hijau',
         ('(Bisikan dari balik topeng.) Malam ini pasar menjual apa saja yang hilang dari siang. Kau mencari apa yang hilang, atau '
          'apa yang belum pernah ada?',
          '(A whisper from behind the mask.) Tonight the market sells whatever the day has lost. Do you seek what was lost, or what '
          'never existed?'),
         [('siapa', ('Siapa kau sebenarnya?', 'Who are you really?'),
           ('Wajah adalah barang dagangan. Aku menjual wajahku setiap malam dan membeli yang baru setiap pagi.',
            'A face is merchandise. I sell mine every night and buy a new one every morning.'), dict(gestur='k:intip')),
          ('aturan', ('Apa aturan pasar ini?', 'What are the rules of this market?'),
           ('Tiga. Jangan tanya dari mana barang datang. Jangan sebut nama asli. Dan pergilah sebelum ayam berkokok, karena saat itu '
            'pasar ini tidak pernah ada.',
            'Three. Don\'t ask where goods come from. Don\'t say real names. And leave before the cock crows, because then this market '
            'never existed.'), dict(gestur='bisik'))],
         gestur='k:intip', suara='kota42.lentera_hijau')
    sapa('kota42_sapa_shen_wan', 'tokoh:shen_wan',
         ('Selamat datang di Rumah Uang! Menabung, meminjam, menanam modal: Shen Wan melayani dengan senyum. Bunganya pun tersenyum.',
          'Welcome to the Counting House! Saving, borrowing, investing: Shen Wan serves with a smile. Even the interest smiles.'),
         [('pinjam', ('Bagaimana cara meminjam?', 'How does borrowing work?'),
           ('Mudah! Cap jempol di sini, di sini, dan di sini. Bunga lima per seratus setiap tujuh hari, jatuh tempo tujuh hari. Kalau '
            'terlambat... yah, Yamen punya pendapat sendiri.',
            'Easy! Thumbprint here, here and here. Five per hundred every seven days, due in seven days. If you are late... well, the '
            'Yamen has its own opinion.'), dict(gestur='k:hitung_koin')),
          ('dermawan', ('Kudengar Tuan menyumbang lentera untuk kuil.', 'I hear you donate lanterns to the temple.'),
           ('Seratus lentera setiap festival! Dewa-dewa harus tahu siapa yang membayar cahayanya. Dan para biksu juga harus tahu, '
            'karena atap kuil mereka masih kugadaikan.',
            'A hundred lanterns every festival! The gods must know who pays for their light. And the monks too, since their temple '
            'roof is still in pawn to me.'), dict(gestur='tertawa'))],
         gestur='hormat')
    sapa('kota42_sapa_shen_ziyu', 'tokoh:shen_ziyu',
         ('Kau menyukai kipasku? Sutra Kota Air, lukisan bangau oleh pelukis istana. Tiga puluh tahil. Pamanku bilang itu pemborosan.',
          'You like my fan? Water City silk, cranes painted by a court artist. Thirty taels. My uncle says it is a waste.'),
         [('paman', ('Pamanmu orang yang keras?', 'Is your uncle a hard man?'),
           ('Ia menghitung butir nasi di mangkukku. Katanya kalau aku bisa menghitung nasi, aku bisa menghitung perak. Aku lebih suka '
            'menghitung bintang.',
            'He counts the grains in my rice bowl. He says if I can count rice, I can count silver. I prefer counting stars.'),
           dict(gestur='k:menepuk_jidat')),
          ('jodoh', ('Kudengar kau akan dijodohkan.', 'I hear you are to be matched.'),
           ('Dengan gadis dari keluarga yang punya kedai, supaya aku "belajar berdagang". Kasihan gadis itu. Aku bahkan tidak bisa '
            'menawar harga kipas.',
            'With a girl from a family with a shop, so that I "learn trade". Poor girl. I cannot even haggle over a fan.'),
           dict(gestur='kipas', prop='kipas_lipat'))],
         gestur='kipas', prop='kipas_lipat')
    sapa('kota42_sapa_nenek_tang', 'tokoh:nenek_tang',
         ('Dingin, ya? Duduk dekat kuali. Nenek tidak tanya punya uang atau tidak, Nenek tanya sudah makan atau belum.',
          'Cold, isn\'t it? Sit by the pot. Granny does not ask if you have money, Granny asks if you have eaten.'),
         [('sup', ('Apa rahasia sup Nenek?', 'What is the secret of your soup, Granny?'),
           ('Rahasia? Tulang yang direbus sejak subuh, jahe tua, dan kesabaran. Dan satu lagi, tapi kalau Nenek bilang, namanya bukan '
            'rahasia lagi.',
            'A secret? Bones simmered since dawn, old ginger, and patience. And one more thing, but if Granny tells, it is no longer a secret.'),
           dict(gestur='masak', prop='sendok_sayur')),
          ('cucu', ('Siapa gadis yang membantu Nenek?', 'Who is the girl helping you?'),
           ('Xiaomi, cucuku. Tangannya cepat, hatinya baik, tapi ia belum sabar. Sup yang terburu-buru rasanya seperti air mata.',
            'Xiaomi, my granddaughter. Quick hands, a good heart, but not yet patient. Hurried soup tastes like tears.'),
           dict(gestur='tepuk_bahu'))],
         gestur='masak', prop='sendok_sayur', suara='kota42.sup_nenek',
         awal=[mulai_lagi('kota42_sup_hangat', ('Nenek butuh bahan sup?', 'Do you need soup ingredients, Granny?'))])
    sapa('kota42_sapa_tang_xiaomi', 'tokoh:tang_xiaomi',
         ('Mangkuk atau dua? Kuahnya baru, lobaknya kuiris sendiri. Nenek bilang irisanku terlalu tipis, tapi pelanggan suka.',
          'One bowl or two? Fresh broth, radish sliced by me. Granny says my slices are too thin, but customers like them.'),
         [('nenek', ('Bagaimana kabar nenekmu?', 'How is your grandmother?'),
           ('Ia batuk lebih sering musim ini, tapi menolak berhenti memasak. Katanya kalau ia berhenti, ia akan berhenti semuanya.',
            'She coughs more this season, but refuses to stop cooking. She says if she stops, she will stop everything.'),
           dict(gestur='menangis')),
          ('mimpi', ('Apa cita-citamu?', 'What is your dream?'),
           ('Membuat sup yang sama dengan sup Nenek. Itu saja. Kedengarannya kecil, ya? Tapi itu sup yang membuat orang kembali hidup.',
            'To make the same soup as Granny\'s. That is all. Sounds small, doesn\'t it? But that soup brings people back to life.'),
           dict(gestur='bicara_tangan'))],
         gestur='k:seka_keringat', prop='k:kendi_sup')
    sapa('kota42_sapa_paman_bai', 'tokoh:paman_bai',
         ('Berjalanlah pelan di sini, Nak. Arwah suka ketenangan.', 'Walk softly here, child. Spirits like quiet.'),
         [('duka', ('Bagaimana Paman bisa bertahan dengan pekerjaan ini?', 'How do you bear this work, Uncle?'),
           ('Setiap pemakaman adalah cerita terakhir seseorang. Aku hanya memastikan cerita itu diceritakan dengan baik. Yang sulit '
            'bukan orang mati, tapi orang yang ditinggalkan.',
            'Every funeral is someone\'s last story. I only make sure it is told well. The hard part is not the dead, but those left behind.'),
           dict(gestur='sujud_dupa', prop='dupa')),
          ('nama', ('Kenapa nama orang mati harus disebut?', 'Why must the names of the dead be spoken?'),
           ('Selama namanya disebut, ia masih punya rumah di dunia ini. Karena itu kita membakar uang kertas dan menyebut nama di hari '
            'Qingming. Bukan untuk mereka saja. Untuk kita juga.',
            'As long as their name is spoken, they still have a home in this world. That is why we burn paper money and say names on '
            'Qingming. Not only for them. For us too.'), dict(gestur='k:bakar_uang_kertas'))],
         gestur='sikap_doa', suara='kota42.makam_pimpin')
    sapa('kota42_sapa_guru_zhou', 'tokoh:guru_zhou',
         ('"Belajar tanpa berpikir itu sia-sia, berpikir tanpa belajar itu berbahaya." Selamat datang. Anak-anak sedang menghafal, '
          'jangan ganggu yang di barisan belakang, mereka sedang pura-pura menghafal.',
          '"Learning without thought is labour lost; thought without learning is perilous." Welcome. The children are reciting, '
          'don\'t disturb the back row, they are pretending to recite.'),
         [('anak', ('Murid Guru dari mana saja?', 'Where do your pupils come from?'),
           ('Anak tukang, anak pedagang, anak pejabat. Di bangkuku mereka semua sama: tangan yang belum bisa menulis lurus.',
            'Craftsmen\'s children, merchants\', officials\'. On my benches they are all the same: hands that cannot yet write straight.'),
           dict(gestur='mengajar', prop='kitab', suara='kota42.sekolah_hafal')),
          ('bakat', ('Guru bisa melihat bakat kultivasi?', 'Can you see cultivation talent?'),
           ('Aku bukan kultivator. Tapi aku guru dua puluh tahun. Anak yang berbakat selalu bertanya "kenapa" satu kali lebih banyak. '
            'Dan kadang, daun-daun di sekitarnya ikut bertanya.',
            'I am no cultivator. But I have taught twenty years. A gifted child always asks "why" once more than the rest. And '
            'sometimes the leaves around them ask too.'), dict(gestur='bicara_tangan'))],
         gestur='mengajar', prop='kitab',
         awal=[mulai_lagi('kota42_akar_langit', ('Guru tampak memikirkan sesuatu.', 'You seem to be thinking about something, Master.'))])
    sapa('kota42_sapa_fang_gudang', 'tokoh:fang_gudang',
         ('Nama? Barang? Berat? Jangan sentuh karung itu, belum ditimbang. Kalau mau menyewa ruang gudang, tulis di sini, dengan '
          'tulisan yang bisa dibaca.',
          'Name? Goods? Weight? Don\'t touch that sack, it is not weighed yet. If you want warehouse space, write here, legibly.'),
         [('timbang', ('Kenapa semua ditimbang dua kali?', 'Why weigh everything twice?'),
           ('Karena timbangan pertama untuk saudagar, yang kedua untuk kebenaran. Dan yang ketiga, yang tidak kubilang, untuk tikus.',
            'Because the first weighing is for the merchant, the second for the truth. And the third, which I don\'t mention, for the rats.'),
           dict(gestur='k:menimbang', prop='k:dacing')),
          ('shen', ('Nyonya tidak suka Juragan Shen?', 'You dislike Master Shen?'),
           ('Aku tidak suka angka yang tidak cocok. Gudang Shen menerima seratus karung dan mengeluarkan sembilan puluh. Sepuluh karung '
            'itu ke mana? Tikus Shen gemuk sekali.',
            'I dislike numbers that do not match. Shen\'s warehouse takes a hundred sacks and sends out ninety. Where do the ten go? '
            'Shen\'s rats must be very fat.'), dict(gestur='marah_tunjuk'))],
         gestur='k:menimbang', prop='k:dacing')
    sapa('kota42_sapa_gao_senior', 'tokoh:gao_senior',
         ('Kau menghalangi jalan. ...Oh, kultivator? Ranahmu biasa saja. Sekteku menerima murid baru musim ini, tapi hanya yang berakar murni.',
          'You are in the way. ...Oh, a cultivator? Your realm is ordinary. My sect takes new disciples this season, but only pure roots.'),
         [('sekte', ('Sekte apa yang kau wakili?', 'Which sect do you represent?'),
           ('Sekte Lurus terkuat di wilayah ini. Namanya tidak perlu kusebut, kau pasti sudah mendengarnya. Kalau belum, itu masalahmu.',
            'The strongest Righteous sect in this region. I need not say its name, you must have heard it. If not, that is your problem.'),
           dict(gestur='sikap_angkuh')),
          ('cari', ('Apa yang kau cari di kota ini?', 'What are you looking for in this city?'),
           ('Permata di dalam lumpur. Anak berbakat lahir di mana saja, bahkan di Kumuh. Sayang kalau mereka membusuk jadi kuli.',
            'Jewels in the mud. Gifted children are born anywhere, even in the Slums. A pity if they rot into porters.'),
           dict(gestur='memandang'))],
         gestur='sikap_angkuh')
    sapa('kota42_sapa_xiao_yu', 'tokoh:xiao_yu',
         ('Kakak! Kakak bisa terbang? Sekali saja, tunjukkan pedangnya! Aku janji tidak bilang Ibu!',
          'Big sister! Big brother! Can you fly? Just once, show me your sword! I promise I won\'t tell Mother!'),
         [('gula', ('Itu burung gula buatanmu?', 'Is that sugar bird yours?'),
           ('Ibu yang membuat, aku yang meniup sayapnya! Mau? Rasanya seperti matahari sore.',
            'Mother makes them, I blow the wings! Want one? It tastes like the evening sun.'), dict(gestur='k:makan_tanghulu', prop='k:permen_gula')),
          ('sekolah', ('Kau suka belajar?', 'Do you like learning?'),
           ('Aku mengintip kelas Guru Zhou dari jendela! Aku sudah hafal seratus karakter. Yang paling kusuka karakter "terbang".',
            'I peek into Master Zhou\'s class through the window! I already know a hundred characters. My favourite is "fly".'),
           dict(gestur='k:intip'))],
         gestur='k:kagum', suara='kota42.anak_kagum')
    sapa('kota42_sapa_yu_laosan', 'tokoh:yu_laosan',
         ('Butuh kuli? Seratus jin, dua keping. Lebih dari itu, tambah satu. Jangan tanya tentang meja dadu.',
          'Need a porter? A hundred jin, two coins. More than that, one more. Don\'t ask about the dice table.'),
         [('dadu', ('Kenapa tidak boleh tanya tentang dadu?', 'Why not ask about dice?'),
           ('Karena aku akan bilang besok pasti menang. Dan kau akan tahu aku bohong. Dan aku juga tahu.',
            'Because I would say tomorrow I am sure to win. And you would know I am lying. And so would I.'), dict(gestur='k:menepuk_jidat')),
          ('anak', ('Bagaimana putrimu?', 'How is your daughter?'),
           ('Pintar. Terlalu pintar untuk anak kuli. Kadang aku takut padanya, kadang aku takut untuknya.',
            'Clever. Too clever for a porter\'s child. Sometimes I fear her, sometimes I fear for her.'), dict(gestur='memandang'))],
         gestur='angkat_bawa')
    sapa('kota42_sapa_yu_xiuniang', 'tokoh:yu_xiuniang',
         ('Naga atau bangau? Satu keping. Untuk anak-anak, setengah. Untuk yang kelihatan sedih, gratis.',
          'Dragon or crane? One coin. For children, half. For those who look sad, free.'),
         [('gula', ('Bagaimana melukis dengan gula?', 'How do you paint with sugar?'),
           ('Gula aren dipanaskan sampai berwarna seperti madu tua, lalu dituang sambil bernapas pelan. Kalau tanganmu ragu, naganya '
            'jadi cacing. Kalau hatimu ragu, gulanya retak.',
            'Palm sugar heated to the colour of old honey, then poured while breathing slowly. If your hand hesitates, the dragon '
            'becomes a worm. If your heart hesitates, the sugar cracks.'), dict(gestur='k:lukis_gula', prop='k:sendok_gula')),
          ('putri', ('Putrimu istimewa, ya?', 'Your daughter is special, isn\'t she?'),
           ('Semua ibu bilang begitu. Tapi tidak semua ibu melihat kelopak bunga berbalik ke arah tawa anaknya.',
            'Every mother says so. But not every mother sees petals turn toward her child\'s laughter.'), dict(gestur='bicara_tangan'))],
         gestur='k:lukis_gula', prop='k:sendok_gula')
    sapa('kota42_sapa_zhang_shan', 'tokoh:zhang_shan',
         ('(Ia berhenti mengukir dan menyembunyikan sesuatu di balik punggungnya.) Oh! Mau tahu? Tahu segar, sepuluh kotak sehari, '
          'ayahku yang membuat. Aku yang menggiling.',
          '(He stops carving and hides something behind his back.) Oh! Want tofu? Fresh tofu, ten trays a day, my father makes it. '
          'I do the grinding.'),
         [('ukir', ('Apa yang kau sembunyikan?', 'What are you hiding?'),
           ('Burung. Burung kayu. Untuk... untuk adik tetangga. Tetangga yang mana? Ehm. Tahunya mau berapa kotak?',
            'A bird. A wooden bird. For... for the neighbour\'s little sister. Which neighbour? Ahem. How many trays of tofu?'),
           dict(gestur='k:garuk_kepala')),
          ('ayah', ('Ayahmu galak?', 'Is your father fierce?'),
           ('Ia baik, tapi lidahnya tajam. Dan ia tidak pernah lupa apa pun yang terjadi empat puluh tahun lalu. Yang kemarin ia lupa.',
            'He is kind, but his tongue is sharp. And he never forgets anything from forty years ago. Yesterday he forgets.'),
           dict(gestur='bicara_tangan'))],
         gestur='palu_landasan')
    sapa('kota42_sapa_li_ying', 'tokoh:li_ying',
         ('Arak bunga persik, arak beras, arak plum. Mau cium? Yang ini dua tahun, yang itu lima. Bisa kutebak umurnya dengan mata tertutup.',
          'Peach-blossom wine, rice wine, plum wine. Want a sniff? This one is two years, that one five. I can tell their age with my eyes closed.'),
         [('arak', ('Bagaimana membedakan umur arak?', 'How do you tell a wine\'s age?'),
           ('Arak muda berbau seperti pagi, arak tua berbau seperti sore. Yang sepuluh tahun berbau seperti kenangan. Ayahku menyimpan '
            'satu guci dua puluh tahun, katanya untuk pernikahanku.',
            'Young wine smells of morning, old wine of evening. Ten-year wine smells of memories. My father keeps one twenty-year jar, '
            'he says for my wedding.'), dict(gestur='tuang_teh', prop='kendi')),
          ('nyanyi', ('Kau suka menyanyi?', 'Do you like singing?'),
           ('Menyaring arak itu lama dan sepi. Kalau aku menyanyi, waktu berjalan lebih cepat. Dan kadang ada yang mendengarkan dari '
            'balik pagar sumur.',
            'Straining wine is slow and lonely. If I sing, time passes faster. And sometimes someone listens from behind the well fence.'),
           dict(gestur='tertawa'))],
         gestur='tuang_teh', prop='kendi')
    sapa('kota42_sapa_zhang_laotou', 'tokoh:zhang_laotou',
         ('Tahu! Tahu lembut, tahu kering, tahu bau! Yang bau paling enak, jangan dengarkan hidungmu.',
          'Tofu! Soft tofu, dried tofu, stinky tofu! The stinky one is the best, don\'t listen to your nose.'),
         [('tahu', ('Apa rahasia tahu Bapak?', 'What is the secret of your tofu?'),
           ('Air sumur Jalan Sumur. Air paling manis di Hunian Barat. Itulah kenapa sumur itu pantas diperebutkan.',
            'Well Street\'s well water. The sweetest water in the West Residences. That is why that well is worth fighting over.'),
           dict(gestur='tumbuk_herba', prop='alu')),
          ('li', ('Bagaimana dengan tetangga Bapak, keluarga Li?', 'What about your neighbours, the Lis?'),
           ('Jangan sebut nama itu di depan tahuku. Tahunya bisa asam.', 'Don\'t say that name in front of my tofu. It might sour.'),
           dict(gestur='marah_tunjuk'))],
         gestur='seru_dagang')
    sapa('kota42_sapa_li_laoban', 'tokoh:li_laoban',
         ('Kedai Arak Keluarga Li, berdiri sejak zaman kakekku! Duduk, minum, dan jangan pernah menyebut tukang tahu di seberang jalan.',
          'The Li Family Wine Shop, standing since my grandfather\'s day! Sit, drink, and never mention the tofu maker across the street.'),
         [('kakek', ('Ceritakan tentang kakek Bapak.', 'Tell me about your grandfather.'),
           ('Kakekku membuat arak terbaik di ibu kota dan minum separuhnya sendiri. Ia jujur dalam segala hal, kecuali umur arak dan '
            'jumlah cawan yang ia minum.',
            'My grandfather made the best wine in the capital and drank half of it himself. He was honest in everything, except the age '
            'of his wine and the number of cups he drank.'), dict(gestur='minum_teh', prop='cangkir')),
          ('putri', ('Putri Bapak menyanyi dengan merdu.', 'Your daughter sings beautifully.'),
           ('Seperti ibunya. Aku akan mencarikan suami terbaik untuknya. Yang punya kedai, yang punya nama, yang bukan... yang bukan dari seberang jalan.',
            'Like her mother. I will find her the best husband. One with a shop, with a name, one who is not... not from across the street.'),
           dict(gestur='bicara_tangan'))],
         gestur='seru_dagang')
    sapa('kota42_sapa_wu_dalang', 'tokoh:wu_dalang',
         ('Mi tarik tangan! Satu helai dari ujung ke ujung mangkuk. Panjang umur untuk yang memakannya, pegal lengan untuk yang membuatnya.',
          'Hand-pulled noodles! One strand from rim to rim. Long life for whoever eats it, aching arms for whoever makes it.'),
         [('mi', ('Bagaimana menarik mi sepanjang itu?', 'How do you pull noodles that long?'),
           ('Adonan diistirahatkan semalam, lalu dibanting, ditarik, dilipat. Tiga belas lipatan, delapan ribu helai. Kakekku bisa empat '
            'belas lipatan. Aku belum.',
            'The dough rests overnight, then is slammed, pulled, folded. Thirteen folds, eight thousand strands. My grandfather could do '
            'fourteen. I cannot yet.'), dict(gestur='palu_landasan')),
          ('warung', ('Warungnya ramai?', 'Is the stall busy?'),
           ('Ramai, tapi uangnya tidak tinggal lama. Setiap bulan ada yang datang menjemputnya. Sudahlah, mau pedas atau tidak?',
            'Busy, but the money does not stay long. Every month someone comes to fetch it. Never mind, spicy or not?'),
           dict(gestur='k:seka_keringat'))],
         gestur='palu_landasan')
    sapa('kota42_sapa_wu_niangzi', 'tokoh:wu_niangzi',
         ('Silakan duduk, Tuan. Teh gratis, mi tidak. Maaf, tehnya juga tidak terlalu bagus.',
          'Please sit, sir. Tea is free, noodles are not. Sorry, the tea is not very good either.'),
         [('kotak', ('Kotak teh apa yang Nyonya pegang?', 'What tea tin are you holding?'),
           ('Kwitansi. Setiap keping bunga yang kami bayar kepada keluarga Shen sejak suamiku masih kecil. Kotaknya sudah penuh dua kali.',
            'Receipts. Every coin of interest we have paid the Shen family since my husband was a boy. The tin has filled up twice.'),
           dict(gestur='k:menimbang'))],
         gestur='hormat',
         awal=[mulai_lagi('kota42_utang_tiga_generasi', ('Nyonya tampak gelisah.', 'You seem troubled, madam.'))])
    sapa('kota42_sapa_gong_shu', 'tokoh:gong_shu',
         ('Hati-hati, balok itu belum dipasak. Kalau mau lewat, lewat sisi utara. Kalau mau membantu, ambil palu.',
          'Careful, that beam is not pegged yet. If you are passing, take the north side. If you want to help, take a hammer.'),
         [('balok', ('Kenapa balok tidak dipaku?', 'Why are beams not nailed?'),
           ('Paku berkarat, pasak tidak. Rumah dengan pasak bisa bergoyang saat gempa dan berdiri lagi. Rumah dengan paku patah.',
            'Nails rust, pegs do not. A pegged house can sway in a quake and stand again. A nailed house breaks.'),
           dict(gestur='k:pikul_balok', prop='k:balok_pikul')),
          ('kota', ('Kota ini akan tumbuh ke mana?', 'Where will this city grow?'),
           ('Ke arah air dan jalan dagang. Kota tumbuh seperti pohon, ke arah cahaya dan air. Tugasku memastikan cabangnya tidak menyumbat jalan.',
            'Toward water and the trade roads. A city grows like a tree, toward light and water. My job is to see its branches do not '
            'choke the roads.'), dict(gestur='tunjuk'))],
         gestur='k:pasang_patok', prop='k:papan_patok')
    sapa('kota42_sapa_qiu', 'tokoh:qiu_panitera',
         ('Pajak lapak bulan ini sudah dibayar? Kalau belum, antre di sana. Kalau sudah, simpan kwitansinya baik-baik.',
          'Has this month\'s stall tax been paid? If not, queue over there. If so, keep the receipt safe.'),
         [('pajak', ('Untuk apa pajak dipakai?', 'What is the tax used for?'),
           ('Tembok, jalan, lumbung, gaji penjaga, dan lentera di jalan malam. Kalau kau ingin tahu persisnya, buku besarnya terbuka '
            'di sini. Aku menulisnya untuk dibaca, bukan untuk disembunyikan.',
            'Walls, roads, the granary, the guards\' pay, and the lanterns on night streets. If you want the exact figures, the ledger '
            'is open here. I write it to be read, not hidden.'), dict(gestur='menulis', prop='kuas'))],
         gestur='menulis', prop='kuas')
    sapa('kota42_sapa_tetua_he', 'tokoh:tetua_he',
         ('Selamat datang di kota kami yang kecil. Jalan utara tidak aman, lewatlah jalan sungai.',
          'Welcome to our small town. The north road is unsafe, take the river road.'),
         [('kota', ('Kota ini sudah lama berdiri?', 'Has this town stood long?'),
           ('Tiga ratus tahun. Leluhur kami pelarian banjir yang menemukan tanah tinggi di sini. Kami tidak pernah kaya, tapi tidak '
            'pernah menyerah juga.',
            'Three hundred years. Our ancestors were flood refugees who found high ground here. We were never rich, but never gave up either.'),
           dict(gestur='memandang'))],
         gestur='hormat')
    sapa('kota42_sapa_dalang_bao', 'tokoh:bao_qing',
         ('Cermin terang tergantung tinggi. Perkara apa yang membawamu ke Yamen?', 'A clear mirror hangs on high. What case brings you to the Yamen?'),
         [('perkara_kota', ('Bagaimana perkara rakyat kecil diadili?', 'How are the small folk\'s cases judged?'),
           ('Sama dengan perkara bangsawan: saksi, bukti, hukum. Hakim pembantu memutus sengketa kebun dan ayam. Yang berat naik ke '
            'mejaku. Tidak ada perkara yang terlalu kecil untuk keadilan.',
            'The same as a noble\'s: witnesses, evidence, law. Assistant judges decide disputes over gardens and chickens. The heavy ones '
            'come to my bench. No case is too small for justice.'), dict(gestur='k:palu_putusan'))],
         prioritas=-1, gestur='sikap_tangan_belakang', syarat=['quest kota42_pejabat_jujur aktif'])


# ================================================================================================ quests other modules name
# 42C-2 Jabatan.QUEST_CATATAN / QUEST_BANJIR (rank 1 and 4), 42E KantorRekrut "kota42_restu_ayah" (started by code).
def alur_jabatan():
    # ---- Catatan Kota (rank 1, Juru Tulis Kehormatan)
    q = 'kota42_catatan_kota'
    Q(q, 'tokoh:qiu_panitera', 'kota42', ('Catatan Kota', 'The Town Record'),
      ('Panitera Qiu menyusun buku catatan kota yang baru dan butuh orang yang mau berjalan, bertanya dan menulis dengan jujur.',
       'Clerk Qiu is compiling the town\'s new record book and needs someone who will walk, ask and write honestly.'), [
        T('warga', ('Buku catatan dimulai dari orangnya. Bicaralah dengan lima warga, siapa saja, dan catat bagaimana kabar mereka.',
                    'A record starts with its people. Talk with five residents, anyone, and note how they fare.'),
          tujuan=['bicara_warga * x5']),
        T('tetua', ('Sejarah kota ada di kepala orang tua. Dengarkan seorang tetua warga, catat apa yang ia ingat.',
                    'The town\'s history lives in its elders\' heads. Hear an elderly resident and note what they remember.'),
          tujuan=['bicara_warga tahap:tua']),
        T('anak', ('Dan masa depan kota ada di jalanan bersama anak-anak. Tanyakan permainan apa yang sedang digemari di gang.',
                   'And the town\'s future is out in the alleys with the children. Ask which games are the fashion in the lanes.'),
          tujuan=['bicara_warga tahap:anak']),
        T('lapor', ('Bawa catatanmu kepada Panitera Qiu.', 'Bring your notes to Clerk Qiu.'),
          tujuan=['bicara tokoh:qiu_panitera'],
          akibat=['bendera kota42.catatan_kota', 'nama_baik 4', 'beri_nb_pita 25', 'kota42.kesan tokoh:qiu_panitera 20'],
          hasil=('Panitera Qiu membaca catatanmu dua kali, mencoret satu kata sifat yang terlalu berbunga, lalu membubuhkan cap. '
                 '"Tulisan jujur. Kau pantas jadi Juru Tulis Kehormatan."',
                 'Clerk Qiu reads your notes twice, strikes out one overly flowery adjective and stamps the page. '
                 '"Honest writing. You are fit to be an Honorary Scribe."')),
    ])
    D('kota42_catatan_mulai', 'tokoh:qiu_panitera', [
        N('mulai', ('Buku catatan kota yang lama sudah dimakan rayap. Aku butuh buku baru, ditulis orang yang berjalan di jalanan, bukan '
                    'orang yang menebak dari balik meja. Sanggup?',
                    'The town\'s old record book was eaten by termites. I need a new one, written by someone who walks the streets, not '
                    'guessed at from behind a desk. Are you up to it?'),
          gestur='menulis', prop='kuas', suara='kota42.pejabat_sapa',
          pilihan=[P(('Aku sanggup.', 'I am up to it.'), syarat=['quest kota42_catatan_kota baru'], akibat=['quest_mulai kota42_catatan_kota'], ke='mulai_ya'),
                   P(('Apa gunanya buku catatan?', 'What use is a record book?'), ke='guna'),
                   P(PAMIT)]),
        N('mulai_ya', ('Bagus. Lima warga, seorang tetua, seorang anak. Tidak perlu bicara tentang cuaca. Tanyakan hal yang orang '
                       'biasanya tidak ditanya: apa yang mereka khawatirkan, apa yang mereka syukuri.',
                       'Good. Five residents, one elder, one child. No need to talk about the weather. Ask what people are not usually asked: '
                       'what they worry about, what they are thankful for.'), gestur='tunjuk'),
        N('guna', ('Pajak dihitung dari catatan, utang dibuktikan dengan catatan, bahkan nama jalan dipertahankan oleh catatan. Kota tanpa '
                   'catatan hanya punya ingatan, dan ingatan itu licin seperti belut.',
                   'Taxes are reckoned from records, debts are proven by records, even street names are kept alive by records. A town without '
                   'records has only memory, and memory is slippery as an eel.'),
          gestur='menulis', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=20, syarat=['hari 3'])
    D('kota42_catatan_lapor', 'tokoh:qiu_panitera', [
        N('mulai', ('Sudah? Bagus, mari kulihat. Lima warga, seorang tetua, seorang anak. Kau tidak menyalin dari tembok pengumuman, aku harap.',
                    'Done? Good, let me see. Five residents, an elder, a child. You did not copy from the notice wall, I hope.'),
          gestur='menulis', prop='k:gulungan_perkara', suara='kota42.pejabat_sapa',
          pilihan=[P(('(Serahkan catatan)', '(Hand over the notes)'), ke='cap')]),
        N('cap', ('Hm. Tulisanmu seperti orang yang mendengarkan. Terima kasih. Kelak, kalau kau ingin ikut mengurus kota ini, '
                  'datang lagi ke mejaku.',
                  'Hm. You write like someone who listens. Thank you. One day, if you wish to help run this town, come to my desk again.'),
          gestur='k:palu_putusan', prop='k:cap_pejabat'),
    ], prioritas=50, syarat=['quest kota42_catatan_kota tahap lapor'])

    # ---- Banjir Musim Semi (rank 4, Wakil Wali Kota)
    q = 'kota42_banjir_musim_semi'
    Q(q, 'tokoh:gong_shu', 'kota42', ('Banjir Musim Semi', 'The Spring Flood'),
      ('Salju gunung mencair dan sungai naik. Mandor Gong Shu butuh tanggul darurat sebelum air masuk ke kampung bawah.',
       'The mountain snow is melting and the river is rising. Foreman Gong Shu needs an emergency embankment before water reaches the lower quarter.'), [
        T('pasir', ('Tanggul darurat butuh karung pasir. Antar enam puluh empat balok pasir kepada Mandor Gong Shu.',
                    'An emergency embankment needs sandbags. Bring sixty-four blocks of sand to Foreman Gong Shu.'),
          tujuan=['antar minecraft:sand tokoh:gong_shu x64']),
        T('balok', ('Pasir saja tidak cukup, tanggul butuh tiang penahan. Antar tiga puluh dua kayu gelondong.',
                    'Sand alone is not enough, the bank needs retaining posts. Bring thirty-two logs.'),
          tujuan=['antar minecraft:oak_log tokoh:gong_shu x32']),
        T('tolong', ('Keluarga di tepi sungai tidak mau pergi. Bantulah dua warga menyelesaikan urusan mereka agar bisa mengungsi.',
                     'Families by the river refuse to leave. Help two residents settle their affairs so they can evacuate.'),
          tujuan=['tolong * x2']),
        T('jaga', ('Air paling tinggi pada malam hari. Ikut ronda dan awasi tanggul semalam suntuk.',
                   'The water peaks at night. Join the night watch and keep an eye on the embankment through the dark.'),
          tujuan=['ronda_geng * x1']),
        T('lapor', ('Tanggul bertahan. Lapor kepada Mandor Gong Shu.', 'The embankment held. Report to Foreman Gong Shu.'),
          tujuan=['bicara tokoh:gong_shu'],
          akibat=['bendera kota42.banjir_semi', 'nama_baik 8', 'beri_nb_pita 60', 'kota42.kesan tokoh:gong_shu 30'],
          hasil=('Air naik sampai pinggang tanggul dan berhenti. Pagi harinya warga kampung bawah mengantarkan bakpao ke pos jagamu, '
                 'dan Mandor Gong Shu hanya berkata, "Pasak yang baik tidak pernah memamerkan dirinya."',
                 'The water rose to the embankment\'s waist and stopped. At dawn the lower quarter\'s people brought buns to your watch post, '
                 'and Foreman Gong Shu only said, "A good peg never shows off."')),
    ])
    D('kota42_banjir_mulai', 'tokoh:gong_shu', [
        N('mulai', ('Dengar suara itu? Itu sungai, dan ia sedang lapar. Salju dari gunung turun semuanya sekaligus tahun ini. Kalau kampung '
                    'bawah banjir, kota kehilangan seratus rumah dan satu generasi ingatan. Aku butuh tangan.',
                    'Hear that sound? That is the river, and it is hungry. All the mountain snow is coming down at once this year. If the lower '
                    'quarter floods, the town loses a hundred houses and a generation of memory. I need hands.'),
          gestur='k:pasang_patok', prop='k:papan_patok', suara='kota42.mandor',
          pilihan=[P(('Katakan apa yang harus dilakukan.', 'Tell me what must be done.'), syarat=['quest kota42_banjir_musim_semi baru'],
                     akibat=['quest_mulai kota42_banjir_musim_semi'], ke='bagi'),
                   P(('Kenapa harus aku?', 'Why me?'), ke='kenapa'),
                   P(PAMIT)]),
        N('bagi', ('Empat pekerjaan. Pasir, kayu, memindahkan orang yang keras kepala, dan satu malam tanpa tidur di atas tanggul. Mulai dari '
                   'pasir, itu yang paling cepat habis.',
                   'Four jobs. Sand, timber, moving the stubborn, and one sleepless night on the embankment. Start with sand, it runs out fastest.'),
          gestur='tunjuk'),
        N('kenapa', ('Karena orang yang ikut mengangkat karung akan peduli pada kota ini jauh lebih lama daripada orang yang hanya membayar. '
                     'Dan karena aku melihatmu berjalan di jalan-jalan ini setiap hari.',
                     'Because someone who lifts sandbags cares for this town far longer than someone who only pays. And because I see you '
                     'walking these streets every day.'),
          gestur='bicara_tangan', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
    ], prioritas=20, syarat=['hari 10', 'nama_baik 20'])
    D('kota42_banjir_lapor', 'tokoh:gong_shu', [
        N('mulai', ('Sungai mundur. Aku melihatnya dengan mataku sendiri: sampai di ujung tali ukurku, lalu berhenti. Kau tidak tidur '
                    'semalaman, kan? Matamu seperti kelinci.',
                    'The river withdrew. I saw it myself: right up to the end of my measuring cord, then it stopped. You did not sleep all night, '
                    'did you? Your eyes look like a rabbit\'s.'),
          gestur='k:potong_pita', suara='kota42.mandor',
          pilihan=[P(('Tanggulnya kuat.', 'The embankment was strong.'), ke='kuat')]),
        N('kuat', ('Karena disusun menurut kitab, dan kitab disusun oleh orang yang pernah kebanjiran. Terima kasih. Kota ini berutang padamu '
                   'semalam tanpa tidur.',
                   'Because it was laid by the book, and the book was written by people who had been flooded. Thank you. This town owes you '
                   'one sleepless night.'),
          gestur='tepuk_tangan'),
    ], prioritas=50, syarat=['quest kota42_banjir_musim_semi tahap lapor'])

    # ---- Restu Ayah (started by code: KantorRekrut when a family refuses to let the gifted child go)
    q = 'kota42_restu_ayah'
    Q(q, 'peran:warga', 'kota42', ('Restu Ayah', 'The Father\'s Blessing'),
      ('Seorang ayah menolak melepas anaknya yang berbakat ke sekte karena ingin ia meneruskan usaha keluarga. Restunya harus diraih '
       'dengan kerja, bukan uang.',
       'A father refuses to let his gifted child go to a sect because he wants the child to carry on the family trade. His blessing must be '
       'earned by deeds, not coin.'), [
        T('dengar', ('Datangi keluarga anak itu. Dengarkan keberatan sang ayah sampai selesai tanpa memotong.',
                     'Visit the child\'s family. Hear the father\'s objections to the end without interrupting.'),
          tujuan=['bicara_warga tahap:dewasa']),
        T('bukti', ('Sang ayah ingin tahu orang macam apa yang akan membawa anaknya pergi. Buktikan dirimu berguna: selesaikan satu '
                    'permintaan warga kota.',
                    'The father wants to know what kind of person would take his child away. Prove yourself useful: finish one resident\'s '
                    'request.'),
          tujuan=['tolong *']),
        T('hadiah', ('Bawalah buah tangan ke rumahnya. Sepotong kue, sekantong teh, apa saja, asalkan dari hati.',
                     'Bring a small gift to his house. A cake, a pouch of tea, anything, as long as it is from the heart.'),
          tujuan=['beri_warga *']),
        T('restu', ('Bicaralah sekali lagi dengan sang ayah. Mungkin hatinya sudah lebih lunak.',
                    'Speak with the father once more. Perhaps his heart has softened.'),
          tujuan=['bicara_warga tahap:dewasa'],
          akibat=['kota42.restu', 'bendera kota42.restu_ayah', 'nama_baik 3'],
          hasil=('Sang ayah menghela napas panjang. "Aku tidak suka kau, tapi aku percaya tanganmu. Bawalah dia. Dan suruh dia pulang '
                 'waktu Tahun Baru."',
                 'The father lets out a long sigh. "I do not like you, but I trust your hands. Take her. And tell her to come home at New '
                 'Year."')),
    ], ulang=True, alur=False)


def isi_layanan():
    """The service screens of 42U reached from the greeting of the character who runs them (38 buka_layanan)."""
    for did, nama, layar in (('kota42_sapa_gong_shu', ('Lihat papan kavling kota.', 'See the town\'s plot board.'), 'kavling'),
                             ('kota42_sapa_qiu', ('Buka buku jabatan dan perkara.', 'Open the book of offices and cases.'), 'jabatan'),
                             ('kota42_sapa_shen_wan', ('Aku ingin menanam modal.', 'I would like to invest.'), 'investasi'),
                             ('kota42_sapa_fang_gudang', ('Lihat gudang dan kontrak dagang.', 'See the warehouse and trade contracts.'), 'gudang')):
        if did not in DIALOGS:
            ERRORS.append(f'isi_layanan: no dialog {did}')
            continue
        DIALOGS[did]['simpul'][0].pilihan.insert(0, P(nama, akibat=[f'buka_layanan {layar}']))


def isi_duka():
    """The survivor of the two old chess players (42.10): the other one has died. Needs the condition kota_wafat <tokoh> (see hand-offs)."""
    for sisa, pergi, nama_pergi, buah, umur in (('kakek_che', 'kakek_xiang', ('Kakek Xiang', 'Grandfather Xiang'), ('gajahnya', 'his elephant'), ('tujuh puluh empat', 'seventy-four')),
                                          ('kakek_xiang', 'kakek_che', ('Kakek Che', 'Grandfather Che'), ('bentengnya', 'his chariot'), ('tujuh puluh tujuh', 'seventy-seven'))):
        gelar = nama_pergi[0].split()[1]
        D(f'kota42_duka_{sisa}', f'tokoh:{sisa}', [
            N('mulai', (f'Papan ini terlalu besar sekarang. Tiga puluh tahun aku menggerakkan buah-buahku sambil mendengar {gelar} mengeluh '
                        f'tentang {buah[0]}. Hari ini {buah[0]} diam di tempatnya, dan aku tidak berani menyentuhnya.',
                        f'This board is too big now. For thirty years I moved my pieces while hearing {gelar} grumble about {buah[1]}. '
                        f'Today {buah[1]} stays where it is, and I dare not touch it.'),
              gestur='k:tonton_catur', prop='papan_catur', suara='kota42.catur_kalah',
              pilihan=[P((f'Aku turut berduka atas kepergian {nama_pergi[0]}.', f'I am sorry for the loss of {nama_pergi[1]}.'), ke='duka'),
                       P(('(Duduk dan mainkan satu babak.)', '(Sit down and play a game.)'), ke='main', akibat=[f'kota42.kesan tokoh:{sisa} 10']),
                       P(PAMIT)]),
            N('duka', (f'Terima kasih. Orang bilang {umur[0]} tahun itu usia yang cukup. Tidak ada usia yang cukup untuk kehilangan '
                       'lawan main. Lawan main itu lebih langka daripada sahabat.',
                       f'Thank you. People say {umur[1]} years is age enough. No age is enough to lose an opponent. An opponent is rarer than a friend.'),
              gestur='bicara_tangan', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
            N('main', (f'Hm. Kau memainkan benteng seperti orang yang tidak takut kalah. Itu cara yang baik. {gelar} dulu begitu, dan ia kalah '
                       f'lebih sering daripada yang ia akui. Mainlah lagi besok, kalau kau lewat.',
                       f'Hm. You play the chariot like someone unafraid of losing. That is a good way. {gelar} was like that, and lost more often than he '
                       f'admitted. Play again tomorrow, if you pass by.'),
              gestur='k:tonton_catur', suara='kota42.catur_skak', pilihan=[P(KEMBALI, ke='mulai'), P(PAMIT)]),
        ], prioritas=40, syarat=[f'kota_wafat {pergi}'])


# ================================================================================================ rumours (38 KabarTempat format)
# Quest outcomes spread with "kabar <id> pemain": %1$s is the player's name. Background rumours ("umum") carry %1$s = the town.
def isi_kabar():
    def hasil(id, umur, idn, en, bobot=10, penyebar=()):
        K(id, id, (idn, en), bobot=bobot, umur=umur, penyebar=penyebar)

    def umum(id, idn, en, penyebar=(), umur=3):
        K(id, 'umum', (idn, en), bobot=10, umur=umur, penyebar=penyebar, isian=['kota'])

    # ---- outcomes of the arcs
    hasil('kota42_dua_damai', 14, 'Keluarga Zhang dan keluarga Li akhirnya duduk semeja! %1$s yang membuktikan utang sumur itu sudah lunas, '
          'dan kini seluruh jalan memakan kue pengantin.',
          'The Zhangs and the Lis finally sat at one table! %1$s proved the well debt was settled long ago, and now the whole street is '
          'eating wedding cake.', penyebar=['pemilik_teh', 'penjual_ikan'])
    hasil('kota42_dua_lari', 10, 'Katanya Zhang Shan dan Li Ying kawin lari ke Kota Air, dikawal %1$s. Dua ayah mengunci pintu dan tidak '
          'berbicara. Tiga Bibi Sumur berbicara untuk mereka semua.',
          'They say Zhang Shan and Li Ying eloped to the Water City, escorted by %1$s. Two fathers locked their doors and said nothing. '
          'The Three Aunts of the Well spoke for all of them.', penyebar=['mak_comblang', 'pemilik_teh'])
    hasil('kota42_dua_pisah', 10, 'Li Ying akan menikah dengan keponakan Juragan Shen. Zhang Shan tidak keluar rumah, dan kedai arak '
          'melihat ia lebih sering daripada ibunya.',
          'Li Ying is to marry Master Shen\'s nephew. Zhang Shan does not leave his house, and the wine shop sees him more often than his mother does.',
          penyebar=['pelayan', 'pemilik_teh'])
    hasil('kota42_zhuazhou_damai', 12, 'Di pesta zhuazhou cucu mereka, dua kakek yang tidak bertegur sapa selama dua puluh tahun duduk '
          'berdampingan. %1$s yang membawa mereka. Bayi itu meraih kuas, dan kedua kakek menangis.',
          'At their grandchild\'s zhuazhou feast, two grandfathers who had not spoken in twenty years sat side by side. %1$s brought them. '
          'The baby grabbed the brush, and both grandfathers wept.', penyebar=['mak_comblang', 'tetua_desa'])
    hasil('kota42_ronda_baru', 10, 'Gong ronda berbunyi lagi tadi malam, kali ini dari tangan Xiao Bang yang gemetar tapi tepat ketukannya. '
          '%1$s duduk di sebelahnya sampai pukul lima.',
          'The watch gong sounded again last night, this time from Xiao Bang\'s trembling but exact hands. %1$s sat beside him till the '
          'fifth watch.', penyebar=['penjaga_malam', 'penjaga'])
    hasil('kota42_utang_lunas', 8, 'Utang tiga generasi keluarga Wu lunas! %1$s menyerahkan peraknya kepada Juragan Shen Wan, yang menghitungnya '
          'dua kali dan, katanya, hampir menangis.',
          'The Wu family\'s three-generation debt is paid! %1$s handed the silver to Master Shen Wan, who counted it twice and, they say, '
          'almost wept.', penyebar=['pialang', 'bankir'])
    hasil('kota42_utang_sidang', 10, 'Juragan Shen Wan didenda di depan Hakim Bao karena bunga yang melampaui hukum. %1$s yang membawa '
          'suratnya ke meja hakim. Para peminjam kecil mengirim telur merah ke rumah keluarga Wu.',
          'Master Shen Wan was fined before Judge Bao for interest beyond the law. %1$s brought the document to the judge\'s desk. Small '
          'borrowers sent red eggs to the Wu house.', penyebar=['pialang', 'hakim'])
    hasil('kota42_utang_modal', 8, 'Bakmi keluarga Wu kini punya pemilik baru berlengan empat puluh persen: %1$s. Antrean di kedai '
          'sudah sampai ke jembatan, dan lunasnya utang tinggal soal musim.',
          'The Wu noodle shop now has a new forty-percent partner: %1$s. The queue already reaches the bridge, and the debt is just a matter '
          'of seasons.', penyebar=['pelayan', 'koki'])
    hasil('kota42_jari_serah', 10, 'Seribu jari Shi Xiaoqian akhirnya digelandang ke Yamen. Anak-anak jalanannya diantar %1$s ke kuil, '
          'dan seorang di antara mereka konon dibawa ke Kantor Rekrutmen.',
          'Shi Xiaoqian\'s thousand fingers were finally marched to the Yamen. %1$s took his street children to the temple, and one of them '
          'is said to have been taken to the Recruitment Office.', penyebar=['penjaga', 'pemilik_teh'])
    hasil('kota42_jari_informan', 8, 'Dompet di Kumuh tiba-tiba aman. Katanya Si Tikus Seribu Jari punya "pelanggan khusus" sekarang. '
          'Siapa yang bilang begitu? Aku tidak pernah bilang.',
          'Purses in the Slums are suddenly safe. They say the Thousand-Finger Rat has a "special customer" now. Who says so? I never said so.',
          penyebar=['pengemis', 'pencopet'])
    hasil('kota42_jari_insaf', 10, 'Toko kunci baru dibuka di lorong Kumuh, pemiliknya bekas pencopet, modalnya dari %1$s. Pelanggan '
          'pertamanya dompet-dompet yang pernah ia curi.',
          'A new locksmith shop opened in a Slums alley, its owner a former pickpocket, its capital from %1$s. Its first customers are the '
          'purses he once stole.', penyebar=['pedagang_keliling', 'pengemis'])
    hasil('kota42_akar_pemain', 12, 'Xiao Yu, anak pelukis gula dari Kumuh, resmi menjadi murid sekte %1$s. Ibunya melukis naga gula '
          'sebesar tangan dan membagikannya gratis seharian.',
          'Xiao Yu, the sugar painter\'s child from the Slums, has formally become a disciple of %1$s\'s sect. Her mother drew a sugar '
          'dragon big as a hand and gave them away free all day.', penyebar=['penjual_ikan', 'pemilik_teh'])
    hasil('kota42_akar_gao', 12, 'Anak berakar langit dari Kumuh dibawa Kakak Senior Gao ke gunung sektenya. Ayahnya melunasi utang '
          'judi dengan uang mukanya dan tidak berani menatap istrinya.',
          'The child with heavenly roots from the Slums was taken by Senior Gao to his sect\'s mountain. Her father paid his gambling debt '
          'with the advance and did not dare look at his wife.', penyebar=['penjual_ikan', 'pengemis'])
    hasil('kota42_akar_tinggal', 12, 'Xiao Yu menolak pergi dan belajar meramu obat dari tabib kota. Katanya, "Langit boleh menunggu, '
          'ibuku tidak." Ada yang menangis di Kumuh, ada yang tertawa.',
          'Xiao Yu refused to leave and is learning herb-craft from the town healer. She said, "Heaven can wait, my mother cannot." Some '
          'wept in the Slums, some laughed.', penyebar=['penjual_ikan', 'tabib'])
    hasil('kota42_tumbuh_resmi', 12, 'Jalan baru di tepi kota diberi nama %1$s! Barongsai Nyonya Hong Ling menari di ujungnya, Mandor Gong '
          'Shu memotong pita, dan tiga anak kecil sudah menulis nama itu di tembok dengan arang.',
          'The new street at the town\'s edge is named for %1$s! Madam Hong Ling\'s lion dance performed at its end, Foreman Gong Shu cut '
          'the ribbon, and three small children have already chalked the name on a wall.', penyebar=['tukang', 'kuli'])
    hasil('kota42_sup_bagi', 9, 'Nenek Tang menanak bubur laba sampai seratus mangkuk, dan %1$s yang membawakan bahan-bahannya dari empat '
          'toko dan dua kota. Kuil tidak pernah sehangat itu di bulan kedua belas.',
          'Granny Tang cooked laba porridge by the hundred bowls, and %1$s fetched its ingredients from four shops and two towns. The temple '
          'was never so warm in the twelfth month.', penyebar=['koki', 'rahib'])
    hasil('kota42_sup_cucu', 10, 'Warung sup Nenek Tang buka lagi pagi ini. Cucunya yang mengaduk, dengan resep yang sama persis. '
          'Orang-orang mengantre tanpa bicara.',
          'Granny Tang\'s soup stall opened again this morning. Her granddaughter stirs, with the very same recipe. People queued without a word.',
          penyebar=['koki', 'pelayan'])
    hasil('kota42_pelindung_panji', 12, 'Panji sekte %1$s dinaikkan di gerbang kota, dan tetua kota menyalakan petasan sampai larut. '
          'Begal-begal di jalan utara konon sudah mulai mencari pekerjaan lain.',
          'The banner of %1$s\'s sect was raised at the town gate, and the elder set off firecrackers late into the night. The bandits on '
          'the north road are said to be looking for other work.', penyebar=['penjaga', 'petani'])
    hasil('kota42_wayang_perdana', 10, 'Pertunjukan wayang perdana di Teater Bayang penuh sampai ke pintu. %1$s yang menabuh gong, dan '
          'Dalang Su menangis setelah layar padam. Tiket gratis seumur hidup untuk sang penabuh.',
          'The premiere at the Shadow Theatre was full to the door. %1$s struck the gong, and Puppeteer Su wept after the screen went dark. '
          'A free ticket for life for the drummer.', penyebar=['pelayan', 'pemusik'])
    hasil('kota42_pejabat_sita', 10, 'Gudang timbunan Juragan Shen Wan disita ke Lumbung atas perintah %1$s, dan harga beras turun lagi '
          'sebelum matahari terbenam. Ada yang bersorak, ada yang mencatat siapa yang bersorak.',
          'Master Shen Wan\'s hoard was seized for the Granary on the order of %1$s, and the price of rice fell again before sunset. Some '
          'cheered, some noted who cheered.', penyebar=['pedagang_keliling', 'hakim'])
    hasil('kota42_pejabat_suap', 8, 'Kabarnya ada pejabat baru yang gemar amplop merah. Para saudagar tersenyum lebar, para petani '
          'tersenyum sempit. Panitera Qiu mencatat segalanya dan tidak mengatakan apa-apa.',
          'Word is a new official likes red envelopes. Merchants smile wide, farmers smile narrow. Clerk Qiu notes everything and says nothing.',
          penyebar=['pialang', 'pemilik_teh'])
    hasil('kota42_lagu_jembatan', 10, 'Dari bawah jembatan terdengar lagu yang tidak pernah kudengar, dimainkan Ah Qin dengan tiga bait '
          'yang dikumpulkan %1$s dari tiga kota. Seorang tua berhenti, menunduk, dan berkata: "Itu lagu Shi Banqiao."',
          'From under the bridge came a song I had never heard, played by Ah Qin with three phrases %1$s gathered from three towns. An old '
          'man stopped, bowed his head and said: "That is Shi Banqiao\'s song."', penyebar=['pengamen', 'pemusik'])
    hasil('kota42_hulu_bongkar', 8, 'Tabib Keliling Hu Lu dibongkar kedoknya di tengah pasar oleh %1$s: pil seribu khasiatnya ternyata '
          'tepung dan kapur. Ia lari tanpa gerobak, dan gerobaknya dibagikan.',
          'Wandering Healer Hu Lu was unmasked in the middle of the market by %1$s: his thousand-virtue pills were flour and chalk. He '
          'fled without his cart, and the cart was shared out.', penyebar=['pedagang_keliling', 'pemilik_teh'])
    hasil('kota42_hulu_mitra', 8, 'Hu Lu si tabib keliling kini punya kios tetap, dan pilnya sungguhan: ramuan dari kebun obat yang ia sewa. '
          'Orang bilang aneh melihat penipu bekerja jujur, tapi laba tetaplah laba.',
          'Hu Lu the wandering healer now has a fixed stall, and his pills are real: brewed from the herb garden he rents. People say it is '
          'strange to see a swindler work honestly, but profit is profit.', penyebar=['tabib', 'pedagang_keliling'])
    hasil('kota42_feihu_tinggal', 8, 'Yan Feihu tidak ikut rombongan! Ia melempar bunga dari puncak piramida dan kali ini ditangkap, '
          'dengan dua tangan. Yan Feiyan memukul punggung adiknya sampai ia batuk, lalu menangis.',
          'Yan Feihu did not leave with the troupe! He threw a flower from the top of the pyramid and this time it was caught, with both hands. '
          'Yan Feiyan thumped her brother\'s back until he coughed, then wept.', penyebar=['akrobat', 'penjual_ikan'])
    hasil('kota42_feihu_pergi', 8, 'Rombongan Bulan Sabit berangkat pagi ini. Seikat bunga tergeletak di jalan, tidak ditangkap siapa-siapa. '
          'Gadis penjual bunga menyimpannya di jambangan di jendela.',
          'The Crescent Moon Troupe left this morning. A bunch of flowers lay in the road, caught by no one. The flower girl keeps it in a '
          'vase at her window.', penyebar=['akrobat', 'penjual_ikan'])
    # ---- background talk of the town ("umum"; %1$s = the town)
    umum('kota42_umum_sumur', 'Tiga Bibi di sumur bilang air %1$s makin manis sejak musim lalu. Entah soal airnya, entah soal gosipnya.',
         'The Three Aunts at the well say the water of %1$s has grown sweeter since last season. Whether it is the water or the gossip, who knows.',
         penyebar=['warga'])
    umum('kota42_umum_catur', 'Dua kakek yang bermain catur di bawah pohon huai sudah berdebat sejak pagi tentang satu langkah gajah yang '
         'dilakukan tiga tahun lalu.',
         'The two grandfathers playing chess under the locust tree have been arguing since morning about one elephant move made three years ago.',
         penyebar=['warga'])
    umum('kota42_umum_pasar', 'Harga sayur di pasar %1$s naik lagi. Ada yang menyalahkan hujan, ada yang menyalahkan pedagang, ada yang '
         'menyalahkan bulan.',
         'Vegetable prices at the market of %1$s rose again. Some blame the rain, some the merchants, some the moon.',
         penyebar=['petani', 'pedagang_keliling'])
    umum('kota42_umum_dongeng', 'Pendongeng tua di kedai teh baru menamatkan kisah Kera Batu, dan semua anak kecil di %1$s sekarang '
         'berjungkir balik dari tangga.',
         'The old storyteller at the teahouse just finished the Stone Monkey tale, and every small child in %1$s is now somersaulting off the steps.',
         penyebar=['pemilik_teh', 'pelayan'])
    umum('kota42_umum_ronda', 'Penjaga malam %1$s berkata gongnya berbunyi sendiri semalam. Kalau bukan hantu, katanya, pasti tikus.',
         'The night watchman of %1$s says his gong rang by itself last night. If not a ghost, he says, then surely a rat.',
         penyebar=['penjaga_malam', 'penjaga'])
    umum('kota42_umum_nikah', 'Mak Comblang katanya sedang sibuk. Pertanda baik: dua keluarga di %1$s sedang saling mengirim kue.',
         'The Matchmaker is said to be busy. A good sign: two families in %1$s are sending each other cakes.',
         penyebar=['mak_comblang', 'warga'])
    umum('kota42_umum_bayi', 'Telur merah digantung di pintu rumah di ujung lorong: ada bayi baru lahir di %1$s. Semua tetangga '
         'diam-diam menghitung bulan.',
         'A red egg hangs on a doorway at the end of the lane: a baby is born in %1$s. All the neighbours quietly count the months.',
         penyebar=['warga', 'penjual_ikan'])
    umum('kota42_umum_arak', 'Penyair pemabuk di kedai arak %1$s membuat pantun baru, katanya tentang bulan. Kedai itu menutup lebih '
         'awal karena stok arak habis, bukan karena bulan.',
         'The drunken poet at the wine house of %1$s composed a new verse, about the moon, he says. The shop closed early because the wine '
         'ran out, not because of the moon.', penyebar=['pelayan', 'pemilik_teh'])
    umum('kota42_umum_kucing', 'Kucing belang tua di pasar ikan %1$s menolak ikan mentah hari ini. Para pedagang berunding: apakah ia sakit, '
         'atau sedang merajuk?',
         'The old tabby at the fish market of %1$s refused raw fish today. The merchants confer: is it ill, or merely sulking?',
         penyebar=['penjual_ikan'])
    umum('kota42_umum_layang', 'Layang-layang naga di atas atap %1$s terlepas dan jatuh di halaman Tabib. Anak-anak mengantre meminta '
         'maaf, dan Tabib membuat mereka menumbuk obat sebagai hukuman.',
         'A dragon kite over the roofs of %1$s broke loose and fell into the Healer\'s yard. The children queued to apologise, and the Healer '
         'made them grind herbs as punishment.', penyebar=['tabib', 'warga'])
    umum('kota42_umum_copet', 'Hati-hati di dekat Pasar Timur %1$s: ada yang bilang jari-jari tertentu bekerja sangat cepat belakangan ini.',
         'Careful near the East Market of %1$s: some say certain fingers have been working very fast lately.',
         penyebar=['penjaga', 'pedagang_keliling'])
    umum('kota42_umum_sekte', 'Orang berjubah sekte lewat di gerbang %1$s kemarin dan menatap anak-anak lebih lama dari biasanya. Orang '
         'tua mulai mengunci pintu dengan lebih rapat.',
         'A sect-robed person passed through the gate of %1$s yesterday and looked at the children longer than usual. Parents are starting '
         'to bar their doors more firmly.', penyebar=['petani', 'penjaga'])
    umum('kota42_umum_musim', 'Burung walet sudah kembali ke %1$s. Para tetua bilang, itu berarti sarang baru, rumah baru, dan hutang '
         'baru.',
         'The swallows are back in %1$s. The elders say that means new nests, new houses and new debts.',
         penyebar=['tetua_desa', 'petani'])
    umum('kota42_umum_tahun', 'Petasan sudah dijual di mana-mana di %1$s. Pedagangnya berkata ia tidak pernah menaikkan harga, hanya '
         'menyesuaikan dengan kegembiraan.',
         'Firecrackers are on sale everywhere in %1$s. The seller says he never raises prices, only adjusts them to the cheer.',
         penyebar=['pedagang_keliling'])


# ================================================================================================ "Tolong": personal requests (42.5.6)
# Format read by PermintaanWarga: {nama, teks[] (one per resident), imbalan, batas_hari, peluang}. Lines get %1$s = the resident the
# request is about (sick relative, lost child, lover, enemy, lender, match, student) and %2$s = a number (logs for kayu_bakar, NB for
# rentenir and pinjam_uang). imbalan gets %1$s = NB (only the kinds that pay NB use it).
TOLONG_JENIS = ['obat_ortu', 'kayu_bakar', 'kucing_hilang', 'anak_hilang', 'surat_kekasih', 'damaikan', 'rentenir', 'preman',
                'barang_dicopet', 'bantu_panen', 'bahan_rumah', 'ajari_anak', 'cari_jodoh', 'kabar_anak_sekte', 'pinjam_uang',
                'undangan']


def isi_tolong():
    out = {}

    def jenis(id, nama, teks, imbalan, batas, peluang=1.0):
        assert id in TOLONG_JENIS, id
        d = {'nama': t(f'kultivator.kota42.tolong.{id}.nama', nama), 'teks': [], 'imbalan': t(f'kultivator.kota42.tolong.{id}.imbalan', imbalan),
             'batas_hari': batas, 'peluang': peluang}
        for i, tx in enumerate(teks):
            d['teks'].append(t(f'kultivator.kota42.tolong.{id}.teks.{i}', tx))
        out[id] = d

    jenis('obat_ortu', ('Obat untuk yang Sakit', 'Medicine for the Sick'), [
        ('%1$s demam sejak tiga malam. Tabib bilang ada pil yang bisa menolong, tapi uangku belum cukup. Tuan, tolonglah.',
         '%1$s has had a fever three nights now. The healer says a pill would help, but I cannot afford it. Sir, please help.'),
        ('Bapak %1$s batuk darah semalam. Aku sudah menjual dua ayam untuk bayar tabib, tapi obatnya habis. Bisakah Tuan membantu?',
         'Old %1$s coughed blood last night. I have sold two hens to pay the healer, but the medicine is gone. Could you help?'),
        ('Jangan bilang siapa-siapa, tapi %1$s sakit dan kami malu minta tolong. Kalau Tuan punya pil penyembuh, kami akan berutang budi seumur hidup.',
         'Tell no one, but %1$s is ill and we are too ashamed to ask. If you have a healing pill, we will owe you for life.')],
        ('Terima kasih. Ambillah %1$s NB, hanya ini yang bisa kami kumpulkan, dan semangkuk bubur dari dapur kami.',
         'Thank you. Take %1$s NB, it is all we could gather, and a bowl of porridge from our kitchen.'), 5)
    jenis('kayu_bakar', ('Kayu Bakar untuk Musim Dingin', 'Firewood for the Winter'), [
        ('Dapurku sudah dingin tiga hari. Kalau Tuan bisa mengantar %2$s kayu gelondong, kami bisa memasak sup dan menghangatkan anak-anak.',
         'My hearth has been cold three days. If you could bring %2$s logs, we could cook soup and warm the children.'),
        ('Salju turun lebih awal dan persediaan kayu kami habis. %2$s gelondong saja cukup untuk melewati minggu ini.',
         'Snow came early and our wood is gone. Just %2$s logs would see us through this week.'),
        ('Nenekku menggigil di bawah tiga selimut. Kami butuh %2$s batang kayu untuk tungku. Aku akan membalas dengan sup.',
         'My grandmother shivers under three blankets. We need %2$s logs for the stove. I will repay you with soup.'),
        ('Pemotong kayu kampung sakit, dan harga kayu naik dua kali lipat. %2$s gelondong, itu saja yang kuminta.',
         'The village woodcutter is ill, and wood has doubled in price. %2$s logs, that is all I ask.')],
        ('Bawalah dua mangkuk sup hangat ini. Rasanya tidak seindah rasa terima kasih kami.',
         'Take these two bowls of warming soup. They taste less fine than our thanks.'), 4, 0.8)
    jenis('kucing_hilang', ('Kucing yang Hilang', 'The Lost Cat'), [
        ('Si Belang tidak pulang sejak kemarin. Biasanya ia tidur di atap, tapi atapnya kosong. Mungkin Tuan melihatnya di gang?',
         'Stripey has not come home since yesterday. She sleeps on the roof, but the roof is empty. Perhaps you saw her in the lanes?'),
        ('Kucingku pergi mengejar tikus dan tidak kembali. Ia suka menyelinap ke gang belakang. Tolong carikan, ia satu-satunya temanku.',
         'My cat went chasing a mouse and did not return. She likes slipping into the back lanes. Please find her, she is my only friend.'),
        ('Aku sudah memanggil dengan ikan asin, dengan lonceng, dengan nyanyian. Kucing itu tetap tidak muncul. Bisa Tuan melihat di sekitar?',
         'I have called with dried fish, with a bell, with singing. The cat still does not appear. Could you look around?')],
        ('Ia pulang bersamamu! Kau pasti punya cara dengan kucing. Ia mungkin ikut menemanimu seharian.',
         'She came home with you! You must have a way with cats. She might follow you around all day.'), 3)
    jenis('anak_hilang', ('Anak yang Tersesat', 'The Lost Child'), [
        ('Anakku bermain di luar sejak siang dan belum pulang! Sebentar lagi gelap. Tolong, cari di dekat tepi kota!',
         'My child went out to play at noon and has not come home! It will be dark soon. Please, look near the town\'s edge!'),
        ('%1$s tidak ada di rumah kawan-kawannya, tidak di sumur, tidak di pasar. Aku takut ia keluar tembok. Tuan, tolong!',
         '%1$s is not at the friends\' houses, nor at the well, nor at the market. I fear the child went past the wall. Sir, please!'),
        ('Aku sudah berteriak sampai serak. Anakku suka mengejar kupu-kupu dan lupa jalan pulang. Bisakah Tuan mencari dan mengantarnya?',
         'I have shouted myself hoarse. My child chases butterflies and forgets the way home. Could you find and bring the child back?')],
        ('Terima kasih, Tuan, terima kasih! Ini upah dari kas kampung, %1$s NB. Dan kabar baik ini akan kuceritakan ke seluruh jalan.',
         'Thank you, sir, thank you! Here is a reward from the neighbourhood fund, %1$s NB. And I shall tell this good news to the whole street.'),
        2, 0.6)
    jenis('surat_kekasih', ('Surat untuk Kekasih', 'A Letter for a Lover'), [
        ('Ini surat untuk %1$s. Aku tidak berani mengantarnya sendiri, ayahku bisa marah. Tuan, bisakah Tuan mengantarkannya diam-diam?',
         'This letter is for %1$s. I dare not take it myself, my father would be furious. Sir, could you deliver it quietly?'),
        ('Tuan sering berjalan jauh, kan? Tolong sampaikan surat ini kepada %1$s. Jangan dibaca, ya. Isinya hanya puisi yang jelek.',
         'You travel far, do you not? Please bring this letter to %1$s. Do not read it, will you. It is only bad poetry.'),
        ('Sudah tiga bulan aku menulis surat ini dan tiga bulan aku menyobeknya. Sekarang akhirnya kutulis sampai habis. Antarkan ke %1$s?',
         'For three months I wrote this letter and for three months tore it up. Now at last I wrote it to the end. Will you take it to %1$s?')],
        ('Ia membalas?! Ah, jantungku. Terima kasih, Tuan. Entah apa jadinya aku tanpa Tuan.',
         'She replied?! Oh, my heart. Thank you, sir. I know not what I would do without you.'), 7)
    jenis('damaikan', ('Damaikan Dua Sahabat', 'Reconcile Two Old Friends'), [
        ('Aku dan %1$s dulu sahabat, sekarang tidak saling menyapa karena soal kecil yang membesar. Bisakah Tuan menengahi?',
         '%1$s and I were once friends, now we do not even greet each other over a small matter that grew. Could you mediate?'),
        ('Tetangga sebelah, %1$s, dan aku bertengkar karena pagar. Seluruh gang jadi tegang. Tuan tampak orang yang sabar. Tolonglah.',
         'My neighbour %1$s and I quarrelled over a fence. The whole lane is tense. You look a patient person. Please help.'),
        ('Aku ingin berdamai dengan %1$s, tapi harga diriku lebih keras dari batu. Kalau Tuan yang bicara, mungkin ia mau mendengar.',
         'I want peace with %1$s, but my pride is harder than stone. If you speak, perhaps they will listen.')],
        ('Kami sudah minum teh bersama tadi. Rasanya aneh, dan menyenangkan. Terima kasih sudah menengahi.',
         'We drank tea together just now. It felt odd, and pleasant. Thank you for mediating.'), 6, 0.8)
    jenis('rentenir', ('Utang kepada Lintah Darat', 'In Debt to the Loan Shark'), [
        ('Aku berutang %2$s NB kepada %1$s, dan bunganya menumpuk lebih cepat dari uangku. Kalau Tuan bisa membantu melunasi, aku bersumpah akan bekerja untuk Tuan.',
         'I owe %2$s NB to %1$s, and the interest piles faster than my coin. If you can help me settle it, I swear I will work for you.'),
        ('%1$s datang ke rumahku tiga kali minggu ini, membawa dua orang berbadan besar. Utangku %2$s NB. Aku tidak bisa tidur.',
         '%1$s came to my house three times this week with two large men. My debt is %2$s NB. I cannot sleep.'),
        ('Dulu kupinjam sedikit saja dari %1$s. Sekarang jadi %2$s NB. Bunganya beranak-pinak seperti kelinci. Tolong, Tuan.',
         'I borrowed only a little from %1$s once. Now it is %2$s NB. The interest breeds like rabbits. Please, sir.')],
        ('Utangku lunas, dan rumahku kembali tenang. Ini upah dari kas kota, %1$s NB. Semoga Tuan tidak bermusuhan dengan lintah itu karenaku.',
         'My debt is paid and my house is calm again. Here is a reward from the town fund, %1$s NB. May you not make an enemy of that shark on my account.'), 5)
    jenis('preman', ('Preman di Gang', 'Thugs in the Lane'), [
        ('Beberapa preman memalak setiap pedagang di gang ini. Penjaga tidak sempat. Kalau Tuan bisa mengusir mereka, tanpa ada yang tewas...',
         'Some thugs shake down every vendor in this lane. The guards have no time. If you could drive them off, without anyone dying...'),
        ('Tuan kelihatan bisa berkelahi. Dua orang kasar menghalangi pintu warungku tiap sore. Bisakah mereka diajari sopan santun?',
         'You look like you can fight. Two rough men block my stall door every evening. Could they be taught manners?'),
        ('Anak-anak muda berandal itu bukan jahat, hanya salah arah. Tolong beri mereka pelajaran, jangan lukai mereka terlalu dalam.',
         'Those young ruffians are not wicked, only misdirected. Please teach them a lesson, but do not hurt them too deep.')],
        ('Mereka sudah menyingkir! Gang ini tenang untuk pertama kalinya sejak musim gugur. Ambil %1$s NB dari kas pedagang, dan terima kasih.',
         'They have gone! The lane is quiet for the first time since autumn. Take %1$s NB from the vendors\' purse, and thank you.'), 4, 0.8)
    jenis('barang_dicopet', ('Barang yang Dicopet', 'The Stolen Belongings'), [
        ('Dompetku dicopet di pasar tadi pagi, isinya bekal sebulan! Mungkin Tuan bisa mengejar pencopetnya, atau mencari barang itu di Pasar Hantu?',
         'My purse was lifted at the market this morning, a month of savings in it! Perhaps you could catch the thief, or look for it at the Ghost Market?'),
        ('Seseorang menyelinap dan mengambil kantong obatku. Tidak ada gunanya bagi pencuri, tapi bagiku itu nyawa. Tolong, Tuan.',
         'Someone slipped in and took my medicine pouch. Of no use to a thief, but life itself to me. Please, sir.'),
        ('Aku melihat jari-jari cepat itu, tetapi terlambat. Cincin peninggalan ibuku hilang. Aku tidak bisa mengejar, kakiku sakit.',
         'I saw those quick fingers, but too late. My late mother\'s ring is gone. I cannot give chase, my legs ache.')],
        ('Barangku kembali! Tak kusangka masih ada orang yang mau repot. Ambil %1$s NB ini, dan doaku.',
         'My belongings are back! I never thought anyone would still take the trouble. Take these %1$s NB, and my blessing.'), 4)
    jenis('bantu_panen', ('Bantu Panen', 'Help with the Harvest'), [
        ('Gandum matang lebih cepat dari perkiraan dan hujan akan turun. Satu orang lagi saja, satu menit di ladang, sangat berarti.',
         'The grain ripened faster than expected and rain is coming. One more pair of hands, just a minute in the field, means a lot.'),
        ('Suamiku terkilir kakinya dan ladang belum terpanen. Bisakah Tuan menemani dan membantu menyabit?',
         'My husband sprained his foot and the field is not yet harvested. Could you stay and help with the sickle?'),
        ('Burung-burung sudah mulai menyerbu padi. Kalau Tuan berdiri di ladang bersamaku semenit saja, mereka akan takut dan pergi.',
         'The birds have started raiding the rice. If you stand in the field with me for a minute, they will be afraid and go.')],
        ('Panen selesai sebelum hujan! Ini sekeranjang hasilnya. Tidak banyak, tapi segar.',
         'The harvest is done before the rain! Here is a basket of it. Not much, but fresh.'), 3, 0.8)
    jenis('bahan_rumah', ('Bahan untuk Perbaikan Rumah', 'Materials for House Repair'), [
        ('Atap rumahku bocor sejak badai kemarin. Mandor sudah datang, tapi kami kekurangan bahan. Bisakah Tuan mengantarkan ke mandor?',
         'My roof has leaked since yesterday\'s storm. The foreman came, but we are short of materials. Could you bring some to him?'),
        ('Dindingku retak. Aku sudah memanggil tukang, tapi bahannya belum datang. Kalau Tuan berkenan membawa bahan ke mandor, rumah ini selamat.',
         'My wall cracked. I called the builder, but the materials have not come. If you could bring materials to the foreman, this house is saved.')],
        ('Rumahku sudah ditambal! Mandor bilang kau membawa bahan tepat waktu. Rezeki baik untukmu.',
         'My house is patched! The foreman says you brought the materials just in time. Good fortune to you.'), 5, 0.8)
    jenis('ajari_anak', ('Mengajari Anak', 'Teaching a Child'), [
        ('Anakku, %1$s, selalu bertanya tentang dunia luar dan aku tidak punya jawabannya. Mau berdiri bersamanya sebentar dan mengajarinya?',
         'My child %1$s keeps asking about the world outside and I have no answers. Would you stand with the child a while and teach?'),
        ('Guru sekolah sedang sakit, dan %1$s tertinggal pelajarannya. Bisakah Tuan meluangkan semenit untuk mengajarinya satu atau dua hal?',
         'The schoolmaster is ill, and %1$s is falling behind. Could you spare a minute to teach one or two things?'),
        ('%1$s melihat Tuan berjalan dan bilang ingin seperti Tuan. Bicaralah dengan dia, ajari yang Tuan bisa. Anak itu butuh teladan.',
         '%1$s saw you walk by and said they want to be like you. Talk with the child, teach what you can. They need an example.')],
        ('Ia belajar dengan sungguh-sungguh dan sekarang menirumu di halaman. Terima kasih sudah mau mengajar.',
         'The child studied earnestly and is now imitating you in the yard. Thank you for teaching.'), 6, 0.7)
    jenis('cari_jodoh', ('Mencari Jodoh', 'Finding a Match'), [
        ('Usiaku sudah lewat dua puluh lima dan semua kawan sudah menikah. Tuan banyak kenal orang, siapa tahu ada yang cocok? Atau bawa saja ke Mak Comblang.',
         'I am past twenty-five and all my friends are married. You know many people, perhaps someone suits? Or just take me to the Matchmaker.'),
        ('Ibuku menangis tiap malam karena aku belum menikah. Aku sudah bosan. Tolong kenalkan aku dengan %1$s, katanya orangnya baik.',
         'My mother weeps every night that I am not wed. I am tired of it. Please introduce me to %1$s, they say the person is kind.'),
        ('Kalau Tuan melihat orang yang baik hati dan sendirian, kenalkan kami. Aku tidak minta rupa, hanya kehangatan.',
         'If you see someone kind-hearted and alone, introduce us. I do not ask for looks, only warmth.')],
        ('Kami sudah bertemu dua kali. Ia tertawa pada leluconku yang paling garing. Mungkin ini pertanda baik.',
         'We have met twice. They laughed at my driest joke. Perhaps that is a good sign.'), 10, 0.7)
    jenis('kabar_anak_sekte', ('Kabar dari Anak di Sekte', 'News of a Child in the Sect'), [
        ('Anakku %1$s masuk sekte tahun lalu dan tidak pernah mengirim kabar. Apakah ia sehat? Bisakah Tuan menyampaikan surat ini kepadanya?',
         'My child %1$s joined a sect last year and has never sent word. Are they well? Could you carry this letter to them?'),
        ('Kami bangga, tapi rumah sepi tanpa %1$s. Tolong antarkan surat ini ke aula sektenya dan, kalau boleh, bawa balasannya.',
         'We are proud, but the house is quiet without %1$s. Please take this letter to the sect hall and, if allowed, bring back a reply.')],
        ('Ia membalas! Tulisannya masih jelek seperti dulu. Terima kasih, Tuan, kami akan membacanya sampai kertasnya lusuh.',
         'They replied! The handwriting is as poor as ever. Thank you, sir, we will read it until the paper wears out.'), 8, 0.6)
    jenis('pinjam_uang', ('Pinjam Uang', 'A Loan'), [
        ('Musim ini buruk. Kalau Tuan mau meminjamiku %2$s NB, aku berjanji mengembalikannya dengan bunga setelah panen.',
         'This season is bad. If you will lend me %2$s NB, I promise to return it with interest after the harvest.'),
        ('Aku butuh %2$s NB untuk modal dagang kecil. Aku tidak punya jaminan selain kata-kataku. Tuan percaya?',
         'I need %2$s NB for small trading capital. I have no collateral but my word. Do you trust it?'),
        ('Anakku harus bayar guru dan aku kekurangan %2$s NB. Jangan khawatir, aku orang yang menepati janji.',
         'My child must pay the teacher and I am short %2$s NB. Do not worry, I am a person who keeps promises.')],
        ('Terima kasih sudah percaya. Kau akan menerima uangmu kembali, dengan bunga.',
         'Thank you for trusting me. You shall have your money back, with interest.'), 5, 0.8)
    jenis('undangan', ('Undangan Pesta Keluarga', 'A Family Feast Invitation'), [
        ('Keluarga kami mengadakan pesta, dan kami ingin Tuan hadir. Datanglah ke upacaranya, makan bersama kami, dan beri berkah.',
         'Our family is holding a feast, and we would like you to attend. Come to the ceremony, eat with us, and bless the day.'),
        ('Tuan sudah sering menolong kami. Mari hadir di pesta kami hari ini sebagai tamu kehormatan. Kursi untuk Tuan sudah disiapkan.',
         'You have helped us often. Come to our feast today as a guest of honour. A seat is set for you.')],
        ('Pestanya meriah berkat kehadiran Tuan. Bawalah kue ini pulang, dan sampaikan salam kami kepada sahabat-sahabat Tuan.',
         'The feast was merry thanks to your presence. Take this cake home, and give our regards to your friends.'), 3)
    assert set(out) == set(TOLONG_JENIS), set(TOLONG_JENIS) ^ set(out)
    return out


# ================================================================================================ gift tastes (42.5.7)
# Extends the built-in table of Selera (arak teh kitab manis kain bunga ikan obat herba jimat senjata mewah daging biji): three new tastes
# (names in lang as kultivator.kota42.bicara.rasa.<id>, the key family of 42B) and rules for the habits / roles / temperaments of 42.4.12
# the built-in table does not cover. A hate always beats a like (Selera.nilai).
def isi_selera():
    rasa = {
        'pangsit': dict(id=['minecraft:bread', 'minecraft:baked_potato', 'minecraft:mushroom_stew', 'minecraft:rabbit_stew',
                            'minecraft:beetroot_soup', 'minecraft:cooked_cod', 'minecraft:cooked_salmon', 'minecraft:pumpkin_pie'],
                        item=['bakpao', 'pangsit', 'dumpling', 'mi_', 'nasi', 'bubur', 'sup_', 'sup', 'mantou']),
        'kertas': dict(id=['minecraft:paper', 'minecraft:ink_sac', 'minecraft:glow_ink_sac', 'minecraft:feather', 'minecraft:string',
                           'minecraft:map', 'minecraft:filled_map'], item=['kuas', 'tinta', 'kertas', 'layang']),
        'buah': dict(id=['minecraft:apple', 'minecraft:melon_slice', 'minecraft:sweet_berries', 'minecraft:glow_berries',
                         'minecraft:carrot', 'minecraft:chorus_fruit', 'minecraft:dried_kelp'], item=['persik', 'buah', 'jeruk']),
    }
    names = {'pangsit': ('Makanan Hangat', 'Hearty Food'), 'kertas': ('Kertas dan Tinta', 'Paper and Ink'), 'buah': ('Buah Segar', 'Fresh Fruit')}
    for k, v in names.items():
        t(f'kultivator.kota42.bicara.rasa.{k}', v)
    aturan = []

    def a(suka=(), benci=(), **cond):
        aturan.append(dict(cond, suka=list(suka), benci=list(benci)))

    # habits of 42.4.12 the built-in table skips
    a(kebiasaan='kakek_catur', suka=['teh', 'buah'])
    a(kebiasaan='pemain_erhu', suka=['teh', 'kertas'])
    a(kebiasaan='penggemar_opera', suka=['kain', 'arak'])
    a(kebiasaan='adu_jangkrik', suka=['buah', 'biji'])
    a(kebiasaan='layang_layang', suka=['kertas'])
    a(kebiasaan='pencerita_anak', suka=['teh', 'manis'])
    a(kebiasaan='tai_chi', suka=['teh', 'buah'], benci=['arak'])
    a(kebiasaan='penabung', suka=['biji', 'pangsit'], benci=['mewah'])
    a(kebiasaan='pemalas', suka=['manis', 'daging'], benci=['senjata'])
    a(kebiasaan='gosip_sumur', suka=['teh', 'manis'])
    a(kebiasaan='penjudi', suka=['mewah', 'arak'])
    a(kebiasaan='pemarah', suka=['arak', 'daging'], benci=['teh'])
    a(kebiasaan='pekerja_keras', suka=['pangsit', 'daging'])
    a(kebiasaan='penyapu_jalan', suka=['teh', 'pangsit'])
    a(kebiasaan='dermawan', suka=['pangsit'], benci=['mewah'])
    a(kebiasaan='pengintip', suka=['arak', 'manis'])
    a(kebiasaan='tidur_siang', suka=['teh', 'pangsit'])
    # roles by name of 42.10 and the trades
    a(peran='hakim', suka=['kitab', 'teh'], benci=['mewah'])
    a(peran='pustakawan', suka=['kitab', 'kertas'])
    a(peran='guru_sekolah', suka=['kertas', 'kitab', 'buah'])
    a(peran='pemusik', suka=['teh', 'kain'])
    a(peran='pengamen', suka=['pangsit', 'teh'])
    a(peran='pendongeng', suka=['teh', 'manis', 'kitab'])
    a(peran='akrobat', suka=['pangsit', 'daging', 'buah'], benci=['arak'])
    a(peran='mak_comblang', suka=['kain', 'manis', 'teh'])
    a(peran='penjual_obat', suka=['mewah', 'arak'])
    a(peran='pengurus_duka', suka=['teh', 'pangsit'], benci=['bunga'])
    a(peran='pialang', suka=['mewah'], benci=['biji'])
    a(peran='bankir', suka=['mewah', 'teh'])
    a(peran='penjaga_malam', suka=['teh', 'pangsit'], benci=['arak'])
    a(peran='penjaga', suka=['daging', 'pangsit'])
    a(peran='penjaga_gudang', suka=['pangsit', 'teh'])
    a(peran='petani', suka=['biji', 'pangsit'])
    a(peran='nelayan', suka=['arak', 'pangsit'])
    a(peran='penjual_ikan', suka=['arak', 'teh'])
    a(peran='pencopet', suka=['pangsit', 'manis'], benci=['senjata'])
    a(peran='pengemis', suka=['pangsit', 'daging'])
    a(peran='tukang', suka=['arak', 'pangsit'])
    a(peran='pemilik_teh', suka=['teh', 'kitab'])
    # temperaments
    a(watak='ramah', suka=['manis', 'pangsit'])
    a(watak='curiga', benci=['mewah'])
    a(watak='licik', suka=['mewah', 'arak'])
    a(watak='jujur', suka=['pangsit', 'teh'], benci=['mewah'])
    a(watak='penakut', suka=['buah'])
    # life stages
    a(tahap='remaja', suka=['manis', 'pangsit', 'kertas'])
    a(tahap='anak', suka=['buah'])
    a(tahap='tua', suka=['buah'], benci=['senjata'])
    # a sick resident wants food as much as medicine
    a(sakit=True, suka=['pangsit'])
    return {'ganti': False, 'rasa': rasa, 'aturan': aturan}


# ================================================================================================ court cases (42.6.6)
# Format of 42C-2's Perkara engine (ekonomi/Perkara.java javadoc). Args: %1$s plaintiff, %2$s defendant, %3$s witness (or the first
# spreader of a gossip). The three of 42C-2 (ayam_hilang, timbangan_curang, sengketa_kebun) stay; these add 24 more.
PERKARA = {}


def PK(id, nama, teks, a_peran, b_peran, klaim, sa, sb, dusta, bukti, putusan, benar='acak', suap=0.3, saksi=2):
    """klaim: ((id,en),(id,en)) plaintiff and defendant claims; sa / sb: two witness lines each when that side is right;
    bukti: [(jenis, barang or '', (a_id,a_en), (b_id,b_en))]; putusan: three ((text), (consequence)) for penggugat, tergugat, damai."""
    base = f'kultivator.kota42.perkara.t.{id}'
    for pr in list(a_peran) + list(b_peran):
        if pr not in ROLES:
            ERRORS.append(f'perkara {id}: unknown role {pr}')
    if len(putusan) != 3:
        ERRORS.append(f'perkara {id}: needs three verdicts')
    o = {'nama': t(f'{base}.nama', nama), 'teks': t(f'{base}.teks', teks)}
    pg = {'klaim': t(f'{base}.klaim_a', klaim[0])}
    tg = {'klaim': t(f'{base}.klaim_b', klaim[1])}
    if a_peran:
        pg['peran'] = list(a_peran)
    if b_peran:
        tg['peran'] = list(b_peran)
    o['penggugat'], o['tergugat'] = pg, tg
    o['benar'] = benar
    o['saksi'] = saksi
    o['kesaksian'] = {'penggugat': [t(f'{base}.saksi_a{i + 1}', x) for i, x in enumerate(sa)],
                      'tergugat': [t(f'{base}.saksi_b{i + 1}', x) for i, x in enumerate(sb)]}
    o['dusta'] = t(f'{base}.dusta', dusta)
    bl = []
    for i, (jenis, barang, ta, tb) in enumerate(bukti, 1):
        b = {'jenis': jenis, 'teks': {'penggugat': t(f'{base}.bukti{i}_a', ta), 'tergugat': t(f'{base}.bukti{i}_b', tb)}}
        if barang:
            item_ok(f'perkara {id}', barang)
            b['barang'] = barang
        bl.append(b)
    o['bukti'] = bl
    o['putusan'] = [{'untuk': u, 'teks': t(f'{base}.putusan{i}', p[0]), 'akibat': t(f'{base}.putusan{i}_akibat', p[1])}
                    for i, (u, p) in enumerate(zip(('penggugat', 'tergugat', 'damai'), putusan), 1)]
    o['suap'] = suap
    if id in PERKARA:
        ERRORS.append(f'duplicate perkara {id}')
    PERKARA[id] = o


def isi_perkara_a():
    PK('utang_sewa', ('Sewa kamar %1$s yang tertunggak', 'The unpaid room rent of %1$s'),
       ('%1$s menuntut %2$s melunasi sewa kamar enam bulan yang tak pernah dibayar.',
        '%1$s demands that %2$s pay six months of room rent that was never paid.'), ['pemilik_penginapan', 'pedagang_keliling'], [],
       (('“Enam bulan! Ia makan di dapurku dan tidur di kamarku, tapi sepeser pun tak keluar dari kantongnya.”',
         '“Six months! He ate in my kitchen and slept in my room, but not a coin came out of his pocket.”'),
        ('“Aku sudah membayar uang muka di depan. Tulisannya ada di buku sewa, tapi %1$s menyimpan bukunya sendiri.”',
         '“I paid a deposit up front. It is in the rent book, but %1$s keeps the book himself.”')),
       [('“Aku mendengar %1$s menagih tiap pagi, dan %2$s selalu berjanji besok.”', '“I heard %1$s ask each morning, and %2$s always promised tomorrow.”'),
        ('“Kamar itu tidak pernah kosong sejak musim gugur. Aku yang membersihkannya.”', '“That room has not been empty since autumn. I cleaned it.”')],
       [('“Ada uang muka, kulihat ia menyerahkan kantung kecil pada %1$s di musim semi.”', '“There was a deposit, I saw him hand %1$s a small pouch in spring.”'),
        ('“%1$s sering lupa mencatat. Buku sewanya penuh coretan.”', '“%1$s often forgets to note things. The rent book is full of scribbles.”')],
       ('“Aku tidak tahu apa-apa. Aku hanya mengantar teh ke kamarnya.”', '“I know nothing. I only brought tea to his room.”'),
       [('catatan', '', ('Buku sewa: tiga halaman kosong untuk nama %2$s, halaman yang lain penuh.', 'Rent book: three blank pages for %2$s\'s name, the rest are full.'),
         ('Buku sewa: satu baris dicoret untuk uang muka, tintanya lebih baru dari sisanya.', 'Rent book: one line scratched out for a deposit, the ink newer than the rest.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s membeli arak mahal minggu lalu, padahal katanya tak punya uang.', 'Word from %3$s: %2$s bought costly wine last week, though claiming to be penniless.'),
         ('Kabar dari %3$s: %1$s pernah menuntut penyewa lain dua kali untuk utang yang sama.', 'Word from %3$s: %1$s once sued another lodger twice for the same debt.'))],
       [(('%2$s melunasi seluruh sewa dan membayar denda keterlambatan.', '%2$s pays the whole rent and a late fine.'),
         ('%1$s puas; %2$s kehilangan muka di depan para penyewa lain.', '%1$s is satisfied; %2$s loses face before the other lodgers.')),
        (('Tuntutan ditolak; uang muka diakui dan sisanya dihapus.', 'The claim is dismissed; the deposit is recognised and the rest cancelled.'),
         ('%2$s lega; %1$s harus belajar mencatat dengan rapi.', '%2$s is relieved; %1$s must learn to keep tidy records.')),
        (('Separuh dibayar sekarang, separuh setelah panen; buku sewa ditulis ulang.', 'Half is paid now, half after harvest; the rent book is rewritten.'),
         ('Tak ada yang benar-benar puas, tetapi penginapan tetap buka.', 'No one is truly satisfied, but the inn stays open.'))])

    PK('mas_kawin', ('Mas kawin putri %1$s', 'The bride price for %1$s\'s daughter'),
       ('%1$s menuntut %2$s menepati mas kawin yang dijanjikan untuk pertunangan anak mereka.',
        '%1$s demands that %2$s keep the bride price promised for their children\'s betrothal.'), ['warga', 'petani'], ['warga', 'pedagang_keliling'],
       (('“Sepuluh tahil perak dan dua kain sutra, dijanjikan di depan Mak Comblang! Sekarang ia bilang tidak ingat.”',
         '“Ten taels of silver and two bolts of silk, promised before the Matchmaker! Now he says he does not remember.”'),
        ('“Yang kujanjikan satu kain sutra dan lima tahil. %1$s menambahnya setiap kali menceritakannya.”',
         '“What I promised was one bolt of silk and five taels. %1$s adds to it every time he tells it.”')),
       [('“Aku hadir saat mereka berjabat tangan. Kata ‘sepuluh’ terdengar jelas.”', '“I was there when they clasped hands. The word ‘ten’ was clear.”'),
        ('“Mak Comblang mencatat angkanya di kertas merah. Ia tidak pernah salah.”', '“The Matchmaker wrote the figure on red paper. She is never wrong.”')],
       [('“Aku di belakang mereka, dan yang kudengar ‘lima’, bukan ‘sepuluh’.”', '“I stood behind them, and what I heard was ‘five’, not ‘ten’.”'),
        ('“Dua hari kemudian %1$s mengeluh bahwa mas kawinnya terlalu murah.”', '“Two days later %1$s complained that the bride price was too cheap.”')],
       ('“Suasananya ramai, aku tak mendengar angkanya. Jangan tanya aku.”', '“It was noisy, I did not hear the figure. Do not ask me.”'),
       [('barang', 'minecraft:red_dye', ('Kertas merah Mak Comblang: angka “sepuluh” ditulis dengan tinta kuas.', 'The Matchmaker\'s red paper: the figure “ten” written in brush ink.'),
         ('Kertas merah Mak Comblang: angka “lima”, dan di bawahnya bekas coretan lama.', 'The Matchmaker\'s red paper: the figure “five”, and below it a trace of old scratching.')),
        ('gosip', '', ('Kabar dari %3$s: keluarga %2$s membeli tanah baru setelah pertunangan itu.', 'Word from %3$s: %2$s\'s family bought new land after the betrothal.'),
         ('Kabar dari %3$s: keluarga %2$s gagal panen dua kali berturut-turut.', 'Word from %3$s: %2$s\'s family had two failed harvests in a row.'))],
       [(('%2$s membayar mas kawin sepuluh tahil seperti kata Mak Comblang.', '%2$s pays the ten-tael bride price as the Matchmaker wrote.'),
         ('%1$s puas; %2$s menanggung kerugian.', '%1$s is satisfied; %2$s bears the loss.')),
        (('Mas kawin tetap lima tahil; pertunangan dilanjutkan.', 'The bride price stays five taels; the betrothal goes on.'),
         ('%2$s lega; %1$s menggerutu tapi tetap hadir di pesta.', '%2$s is relieved; %1$s grumbles but still attends the feast.')),
        (('Tujuh tahil setengah, dan kedua keluarga makan bersama.', 'Seven and a half taels, and the two families eat together.'),
         ('Kedua pihak menahan diri demi anak-anak mereka.', 'Both sides hold back for their children\'s sake.'))])

    PK('warisan_rumah', ('Warisan rumah almarhum', 'The late man\'s house'),
       ('%1$s dan %2$s berebut rumah peninggalan paman mereka yang wafat tanpa surat wasiat.',
        '%1$s and %2$s dispute the house of their late uncle, who died without a will.'), ['warga', 'tukang'], ['warga', 'petani'],
       (('“Akulah yang merawatnya sepuluh tahun terakhir. Rumah itu hakku!”', '“I nursed him for his last ten years. The house is mine by right!”'),
        ('“Aku keponakan tertua. Adat menyatakan rumah jatuh pada yang tertua.”', '“I am the eldest nephew. Custom says the house goes to the eldest.”')),
       [('“Setiap pagi %1$s membawa bubur ke rumah itu sampai paman wafat.”', '“Every morning %1$s brought porridge to that house until the uncle died.”'),
        ('“Paman pernah berkata di depanku: rumah ini untuk yang menutup matanya. Dan %1$s yang ada di sana.”',
         '“The uncle once told me: this house is for the one who closes my eyes. And %1$s was there.”')],
       [('“%1$s jarang datang kecuali untuk meminjam uang. Paman sering mengeluh.”', '“%1$s rarely came except to borrow money. The uncle often complained.”'),
        ('“Adat tidak bisa dilanggar. Yang tertua mewarisi, dan ia harus mengurus yang lain.”', '“Custom cannot be broken. The eldest inherits, and must look after the rest.”')],
       ('“Aku hanya tetangga. Rumah itu tak ada hubungannya denganku.”', '“I am only a neighbour. That house has nothing to do with me.”'),
       [('catatan', '', ('Catatan balai: iuran pemakaman dibayar penuh oleh %1$s.', 'Hall record: the funeral contribution was paid in full by %1$s.'),
         ('Catatan balai: %2$s tercatat sebagai ahli waris tertua dalam daftar marga.', 'Hall record: %2$s is listed as eldest heir in the clan register.')),
        ('barang', 'minecraft:book', ('Buku harian paman: ‘Hanya %1$s yang menemaniku di malam-malam sakit.’', 'The uncle\'s diary: ‘Only %1$s kept me company on the sick nights.’'),
         ('Buku harian paman: ‘Rumah ini akan jatuh pada ahli waris sesuai kebiasaan keluarga kita.’', 'The uncle\'s diary: ‘This house shall pass to the heir by our family\'s custom.’'))],
       [(('Rumah diberikan kepada %1$s; %2$s menerima bagian dari hasil kebun.', 'The house goes to %1$s; %2$s receives part of the garden yield.'),
         ('%1$s terharu; %2$s sakit hati.', '%1$s is moved; %2$s is hurt.')),
        (('Rumah diberikan kepada %2$s sebagai yang tertua; %1$s boleh tinggal di kamar belakang.', 'The house goes to %2$s as the eldest; %1$s may stay in the back room.'),
         ('%2$s menang menurut adat; %1$s merasa tidak dihargai.', '%2$s wins by custom; %1$s feels unvalued.')),
        (('Rumah dijual dan hasilnya dibagi dua setelah dikurangi biaya makam.', 'The house is sold and the proceeds divided in two after funeral costs.'),
         ('Tak ada yang menang, tetapi keluarga tak terpecah.', 'No one wins, but the family is not split.'))])

    PK('perkelahian_kedai', ('Perkelahian di kedai arak', 'The brawl at the wine shop'),
       ('%1$s menuntut %2$s mengganti dua meja dan satu guci arak yang pecah dalam perkelahian semalam.',
        '%1$s demands that %2$s pay for two tables and a wine jar broken in last night\'s brawl.'), ['pemilik_teh', 'pelayan'], ['kuli', 'nelayan', 'tukang'],
       (('“Ia yang melempar kursi pertama! Aku melihatnya dengan mataku sendiri!”', '“He threw the first chair! I saw it with my own eyes!”'),
        ('“Aku hanya membela diri. %1$s dan tamunya yang mulai menghina.”', '“I only defended myself. %1$s and his guests started the insults.”')),
       [('“Kudengar bunyi kursi patah, lalu %2$s berteriak sambil mengangkat meja.”', '“I heard a chair snap, then %2$s shouted while lifting a table.”'),
        ('“Ia sudah mabuk sebelum tengah malam dan menolak pergi.”', '“He was drunk before midnight and refused to leave.”')],
       [('“Pelayan menumpahkan arak ke pakaian %2$s lalu tertawa. Itu pemicunya.”', '“The waiter spilled wine on %2$s\'s clothes and laughed. That was the spark.”'),
        ('“%1$s sendiri yang mendorong lebih dulu, lalu menyalahkan yang lain.”', '“%1$s was the first to push, then blamed the other.”')],
       ('“Aku sudah tidur di pojok. Aku tidak melihat apa pun.”', '“I was asleep in the corner. I saw nothing.”'),
       [('barang', 'minecraft:oak_planks', ('Serpihan kursi dengan bekas jari dan sisa arak di kayunya.', 'A chair splinter with finger marks and wine dregs on the wood.'),
         ('Serpihan meja menancap di dinding, tinggi sekali: bukan tempat orang membela diri.', 'A table splinter stuck high in the wall: not where one defends oneself.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s sudah tiga kali dilarang masuk kedai arak lain.', 'Word from %3$s: %2$s was banned from three other wine shops.'),
         ('Kabar dari %3$s: kedai %1$s terkenal menyajikan arak yang dicampur air.', 'Word from %3$s: %1$s\'s shop is known for watered-down wine.'))],
       [(('%2$s mengganti semua kerusakan dan bekerja tiga hari di kedai itu.', '%2$s pays for all damage and works three days at the shop.'),
         ('%1$s puas; %2$s dijauhi di kedai-kedai lain.', '%1$s is satisfied; %2$s is shunned at other shops.')),
        (('%1$s menanggung kerusakannya sendiri dan diperingatkan soal arak campur air.', '%1$s bears the damage himself and is warned about watered wine.'),
         ('%2$s lega; %1$s kehilangan muka di depan pelanggannya.', '%2$s is relieved; %1$s loses face before his customers.')),
        (('Biaya dibagi dua dan keduanya berjabat tangan di depan penjaga.', 'Costs are split and both shake hands before the guard.'),
         ('Kedai kembali ramai, perkelahian terlupakan.', 'The shop is busy again, the brawl forgotten.'))])

    PK('gosip_fitnah', ('Fitnah tentang %1$s', 'Slander about %1$s'),
       ('%1$s menuntut %2$s mencabut gosip bahwa ia mencampur kapur ke dalam tepung dagangannya.',
        '%1$s demands that %2$s retract the gossip that he mixes chalk into his flour.'), ['pedagang_keliling', 'koki', 'pemilik_teh'], ['warga', 'penjual_ikan'],
       (('“Gosip itu menyebar sampai ke ujung kota dan pelangganku pergi satu per satu!”', '“That gossip spread to the end of town and my customers left one by one!”'),
        ('“Aku hanya menceritakan yang kulihat. Roti %1$s memang putih sekali, lebih putih dari tepung biasa.”', '“I only told what I saw. %1$s\'s bread really is white, whiter than ordinary flour.”')),
       [('“Aku membeli tepung %1$s tiap minggu selama tiga tahun. Tidak ada kapurnya.”', '“I have bought %1$s\'s flour every week for three years. There is no chalk in it.”'),
        ('“Gosip itu mulai dari mulut %2$s, aku sendiri mendengarnya di sumur.”', '“The gossip started from %2$s\'s mouth, I heard it myself at the well.”')],
       [('“Tepungnya memang terlalu putih. Aku curiga sejak lama.”', '“The flour really is too white. I have been suspicious for a long time.”'),
        ('“%1$s menolak membiarkan siapa pun melihat gudang tepungnya.”', '“%1$s refuses to let anyone see his flour store.”')],
       ('“Aku hanya mendengar dari orang lain, siapa tadi... lupa.”', '“I only heard it from someone else, who was it... forgotten.”'),
       [('gosip', '', ('Rantai gosip: dimulai dari %3$s, yang mendengarnya dari %2$s di sumur.', 'Gossip chain: it begins with %3$s, who heard it from %2$s at the well.'),
         ('Rantai gosip: %3$s hanya mengulang ucapan %2$s tanpa melihat sendiri.', 'Gossip chain: %3$s merely repeated %2$s without seeing anything.')),
        ('barang', 'minecraft:wheat', ('Contoh tepung dari gudang %1$s: gandum murni, tanpa campuran.', 'A flour sample from %1$s\'s store: pure grain, no admixture.'),
         ('Contoh tepung dari gudang %1$s: sangat halus dan putih, sulit diperiksa penggilingannya.', 'A flour sample from %1$s\'s store: very fine and white, the milling hard to check.'))],
       [(('%2$s mencabut gosipnya di depan sumur dan membayar denda.', '%2$s retracts his gossip at the well and pays a fine.'),
         ('%1$s dipulihkan namanya; %2$s malu.', '%1$s has his name restored; %2$s is shamed.')),
        (('Tuduhan ditolak karena gosip itu hanya pendapat, bukan fitnah.', 'The claim is dismissed because the gossip was only opinion, not slander.'),
         ('%2$s lega; %1$s harus membuktikan tepungnya sendiri.', '%2$s is relieved; %1$s must prove his flour himself.')),
        (('%1$s membuka gudangnya untuk diperiksa, dan %2$s meminta maaf.', '%1$s opens his store for inspection, and %2$s apologises.'),
         ('Kepercayaan pulih pelan-pelan.', 'Trust recovers slowly.'))], suap=0.2)

    PK('anak_tertahan', ('Anak %1$s di rumah tetangga', '%1$s\'s child at the neighbour\'s'),
       ('%1$s menuduh %2$s menahan anaknya di rumah dan menyuruhnya bekerja tanpa upah selama sebulan.',
        '%1$s accuses %2$s of keeping his child at the house and making the child work unpaid for a month.'), ['warga', 'petani'], ['pedagang_keliling', 'tukang'],
       (('“Anakku tidak pulang sebulan! Ia dipakai mengangkat karung dan mencuci piring!”', '“My child has not come home for a month! He is used to lift sacks and wash dishes!”'),
        ('“Anak itu datang sendiri minta makan dan kerja. Aku memberinya tempat tidur dan sepiring nasi.”', '“The child came by himself asking for food and work. I gave him a bed and a plate of rice.”')),
       [('“Aku melihat anak itu membawa dua karung beras di punggungnya, kecil sekali ia.”', '“I saw the child carry two sacks of rice on his back, so small he is.”'),
        ('“Ia kurus dan tangannya merah. %2$s tidak membiarkannya keluar.”', '“He is thin and his hands are red. %2$s does not let him out.”')],
       [('“Anak itu sendiri yang bilang tidak mau pulang, katanya ayahnya terlalu keras.”', '“The child himself said he did not want to go home, saying his father is too strict.”'),
        ('“%2$s memberinya makan di meja yang sama dengan anak-anaknya.”', '“%2$s fed him at the same table as his own children.”')],
       ('“Anak? Aku tidak melihat anak siapa pun di sana.”', '“A child? I saw no one\'s child there.”'),
       [('catatan', '', ('Catatan toko: anak itu tercatat mengangkut barang tiap hari, tanpa catatan upah.', 'Shop ledger: the child is noted carrying goods daily, with no wage entry.'),
         ('Catatan toko: sebulan makan dan penginapan dicatat atas nama anak itu.', 'Shop ledger: a month of food and lodging is noted under the child\'s name.')),
        ('gosip', '', ('Kabar dari %3$s: ayah anak itu pernah memukulnya di pasar.', 'Word from %3$s: the child\'s father once struck him in the market.'),
         ('Kabar dari %3$s: ayah anak itu sering mabuk dan menghilang berhari-hari.', 'Word from %3$s: the child\'s father often gets drunk and vanishes for days.'))],
       [(('Anak diserahkan kembali, dan %2$s membayar upah sebulan.', 'The child is returned, and %2$s pays a month\'s wage.'), ('%1$s lega; %2$s tersinggung.', '%1$s is relieved; %2$s is offended.')),
        (('Tuduhan ditolak; anak tinggal sementara dan %1$s diberi peringatan.', 'The claim is dismissed; the child stays for now and %1$s is warned.'), ('%2$s lega; %1$s marah dan malu.', '%2$s is relieved; %1$s is angry and ashamed.')),
        (('Anak pulang dengan syarat, dan balai menunjuk seorang tetua sebagai pengawas.', 'The child returns on conditions, and the hall appoints an elder to oversee.'), ('Kedua pihak enggan, tetapi anak itu tenang.', 'Both sides are reluctant, but the child is calm.'))], suap=0.15)

    PK('kebakaran_lalai', ('Kebakaran di dapur %1$s', 'The fire in %1$s\'s kitchen'),
       ('%1$s menuntut %2$s mengganti dapur dan atap yang terbakar akibat tungku yang ditinggalkan menyala.',
        '%1$s demands that %2$s pay for the kitchen and roof burnt through a stove left alight.'), ['pemilik_teh', 'koki', 'tukang'], ['pelayan', 'kuli'],
       (('“Ia meninggalkan tungku menyala dan pergi ke pasar! Hampir seluruh lorong terbakar!”', '“He left the stove burning and went to the market! The whole lane nearly burnt!”'),
        ('“Tungku itu sudah kumatikan. Api muncul dari atap, dari bara yang terbang dari rumah lain.”', '“I put that stove out. The fire began on the roof, from an ember blown from another house.”')),
       [('“Aku melihat %2$s meninggalkan dapur dengan wajan masih panas.”', '“I saw %2$s leave the kitchen with the wok still hot.”'),
        ('“Asap pertama keluar dari jendela dapur, bukan atap.”', '“The first smoke came from the kitchen window, not the roof.”')],
       [('“Malam itu angin kencang, bara beterbangan dari tungku pembakaran sebelah.”', '“That night a strong wind blew embers from the neighbouring kiln.”'),
        ('“Atap jerami %1$s memang sudah kering seperti ranting.”', '“%1$s\'s thatch was as dry as twigs.”')],
       ('“Aku tertidur. Baru bangun saat orang berteriak api.”', '“I was asleep. I woke only when people shouted fire.”'),
       [('barang', 'minecraft:charcoal', ('Arang menumpuk di dapur dan tungku masih berisi bara tebal.', 'Charcoal piled in the kitchen, the stove still holding thick embers.'),
         ('Arang di tungku sudah dingin, tetapi ada bara hangus di atap jerami.', 'The stove charcoal is cold, but there are scorched embers in the thatch.')),
        ('catatan', '', ('Catatan ronda: sebelum api, penjaga melihat dapur %1$s terang sampai larut.', 'Watch record: before the fire, the guard saw %1$s\'s kitchen bright till late.'),
         ('Catatan ronda: angin barat kencang sejak tengah malam, dua rumah lain hampir terbakar.', 'Watch record: a strong west wind since midnight, two other houses nearly burnt.'))],
       [(('%2$s membayar perbaikan dapur dan atap.', '%2$s pays for the repair of kitchen and roof.'), ('%1$s puas; %2$s berutang pada banyak orang.', '%1$s is satisfied; %2$s owes many people now.')),
        (('Tuduhan ditolak; api dianggap musibah angin.', 'The claim is dismissed; the fire is judged an accident of the wind.'), ('%2$s lega; %1$s menanggung kerugiannya sendiri.', '%2$s is relieved; %1$s bears his own loss.')),
        (('Biaya ditanggung bersama dan semua atap diganti genteng.', 'Costs are shared and all roofs are replaced with tile.'), ('Lorong lebih aman dari api di masa depan.', 'The lane is safer from fire in future.'))])

    PK('kerbau_masuk_sawah', ('Kerbau di sawah %1$s', 'The buffalo in %1$s\'s paddy'),
       ('%1$s menuntut %2$s mengganti padi yang diinjak kerbau yang lepas dari kandangnya.',
        '%1$s demands that %2$s pay for rice trampled by his buffalo, loose from its pen.'), ['petani'], ['petani'],
       (('“Kerbaunya menginjak seperempat sawahku! Panenku hilang!”', '“His buffalo trampled a quarter of my paddy! My harvest is gone!”'),
        ('“Kandangku dikunci. Seseorang sengaja membuka pagarnya malam itu.”', '“My pen was locked. Someone opened its gate on purpose that night.”')),
       [('“Kulihat kerbau itu berjalan sendirian di pematang saat fajar.”', '“I saw the buffalo walking alone on the dyke at dawn.”'),
        ('“Pagar kandang %2$s sudah lama reyot, ia tidak pernah memperbaikinya.”', '“%2$s\'s pen fence has been rickety for ages, he never mended it.”')],
       [('“Tali kandang itu terpotong rapi, bukan putus.”', '“The pen rope was cut clean, not snapped.”'),
        ('“%1$s berselisih dengan %2$s soal air minggu lalu. Aku mendengarnya.”', '“%1$s quarrelled with %2$s over water last week. I heard it.”')],
       ('“Kerbau? Aku hanya lewat membawa air. Tidak lihat apa-apa.”', '“Buffalo? I was only passing with water. I saw nothing.”'),
       [('barang', 'minecraft:lead', ('Tali kandang: ujungnya kusut dan sobek, tanda ditarik paksa.', 'The pen rope: frayed and torn at the end, pulled by force.'),
         ('Tali kandang: terpotong rata oleh bilah tajam.', 'The pen rope: cut flat by a sharp blade.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s sering membiarkan kerbaunya merumput di tanah orang.', 'Word from %3$s: %2$s often lets his buffalo graze on others\' land.'),
         ('Kabar dari %3$s: %1$s pernah mengancam akan membuat %2$s menyesal soal air.', 'Word from %3$s: %1$s once threatened to make %2$s sorry over the water.'))],
       [(('%2$s mengganti panen yang rusak dan memperbaiki kandangnya.', '%2$s pays for the ruined harvest and mends his pen.'), ('%1$s puas; %2$s menggerutu.', '%1$s is satisfied; %2$s grumbles.')),
        (('Tuduhan ditolak; kerbau dilepas orang lain, dan %1$s diminta mencari pelakunya.', 'The claim is dismissed; the buffalo was freed by someone else, and %1$s is told to find the culprit.'),
         ('%2$s lega; %1$s kehilangan panen dan prasangka.', '%2$s is relieved; %1$s loses harvest and suspicion.')),
        (('Mereka menggarap sisa sawah bersama dan membagi hasilnya.', 'They tend the remaining paddy together and share the yield.'), ('Dendam air mereda, setidaknya sampai musim kering.', 'The water grudge cools, at least until the dry season.'))])

    PK('air_irigasi', ('Giliran air parit %1$s', '%1$s\'s turn at the irrigation ditch'),
       ('%1$s menuduh %2$s membuka pintu air parit di luar giliran sehingga sawahnya kekeringan.',
        '%1$s accuses %2$s of opening the ditch gate out of turn so that his paddy ran dry.'), ['petani'], ['petani'],
       (('“Giliranku malam ini, tapi saat aku datang air sudah habis!”', '“It was my turn tonight, but when I came the water was gone!”'),
        ('“Aku hanya membuka pintu sebentar karena bibit semaiku layu. %1$s melebih-lebihkan.”', '“I only opened the gate briefly because my seedlings were wilting. %1$s exaggerates.”')),
       [('“Aku mendengar bunyi roda air pada jam yang bukan jam %2$s.”', '“I heard the water wheel at an hour that was not %2$s\'s hour.”'),
        ('“Papan giliran di parit sudah dilepas dan digeser.”', '“The turn board at the ditch was removed and shifted.”')],
       [('“Tanah %2$s memang retak, benihnya hampir mati. Wajar kalau ia panik.”', '“%2$s\'s soil really was cracked, the seedlings nearly dead. No wonder he panicked.”'),
        ('“Papan giliran itu lapuk dan jatuh sendiri.”', '“That turn board was rotten and fell by itself.”')],
       ('“Parit itu bukan urusanku. Air datang, air pergi.”', '“That ditch is not my affair. Water comes, water goes.”'),
       [('catatan', '', ('Papan giliran: nama %1$s tertulis untuk malam itu, tintanya masih terbaca.', 'Turn board: %1$s\'s name is written for that night, the ink still legible.'),
         ('Papan giliran: pangkalnya lapuk dan retak, kayunya basah dan lunak.', 'Turn board: its base is rotten and cracked, the wood wet and soft.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s sudah dua kali ditegur tetua soal air.', 'Word from %3$s: %2$s has been warned twice by the elder over water.'),
         ('Kabar dari %3$s: %1$s sering menambah waktu gilirannya sendiri.', 'Word from %3$s: %1$s often extends his own turn.'))],
       [(('%2$s mengembalikan giliran air dan memberi %1$s dua malam ekstra.', '%2$s restores the water turn and grants %1$s two extra nights.'), ('%1$s puas; %2$s dicap curang.', '%1$s is satisfied; %2$s is branded a cheat.')),
        (('Tuduhan ditolak; papan giliran diganti dengan batu bertanda.', 'The claim is dismissed; the turn board is replaced with a marked stone.'), ('%2$s lega; %1$s harus bersabar.', '%2$s is relieved; %1$s must be patient.')),
        (('Giliran diatur ulang oleh tetua, kedua sawah mendapat bagian sama.', 'The elder redraws the turns, both paddies get an equal share.'), ('Tak ada kemenangan, tetapi parit tetap mengalir.', 'No victory, but the ditch keeps flowing.'))])

    PK('sewa_kuda', ('Kuda sewaan %1$s', 'The hired horse of %1$s'),
       ('%1$s menuntut %2$s mengganti kuda yang pincang setelah disewa dua hari ke kota tetangga.',
        '%1$s demands that %2$s pay for a horse lamed after two days hired to the next town.'), ['pedagang_keliling', 'petani'], ['pedagang_keliling', 'pengembara'],
       (('“Kudaku pergi sehat dan pulang pincang! Ia pasti memacunya tanpa ampun.”', '“My horse left sound and came back lame! He surely rode it without mercy.”'),
        ('“Kuda itu sudah pincang sebelum kusewa. %1$s menutupinya dengan kain di kakinya.”', '“That horse was already lame before I hired it. %1$s hid it with a cloth on its leg.”')),
       [('“Aku melihat kuda itu sehat dan gagah saat berangkat.”', '“I saw that horse sound and spirited when it left.”'),
        ('“%2$s mengendarainya melewati jalan berbatu, dalam hujan.”', '“%2$s rode it over the stony road, in the rain.”')],
       [('“Kuda itu memang sering mengangkat kaki kirinya sejak musim lalu.”', '“That horse has often lifted its left foot since last season.”'),
        ('“Kulihat kain di kakinya. %1$s tahu ada yang salah.”', '“I saw the cloth on its leg. %1$s knew something was wrong.”')],
       ('“Kuda? Banyak kuda di jalan itu. Aku tak ingat satu pun.”', '“Horses? Plenty on that road. I recall none.”'),
       [('barang', 'minecraft:saddle', ('Pelana: tapal kaki kiri penuh batu kecil dan lecet baru.', 'Saddle: the left hoof shoe full of small stones and fresh abrasions.'),
         ('Pelana: kain pembalut kaki kiri, kotor oleh tanah lama.', 'Saddle: a cloth wrap on the left foot, stained with old soil.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s dikenal memacu kuda sewaan hingga berbusa.', 'Word from %3$s: %2$s is known for riding hired horses till they foam.'),
         ('Kabar dari %3$s: %1$s sudah dua kali menjual kuda sakit sebagai kuda sehat.', 'Word from %3$s: %1$s twice sold a sick horse as a sound one.'))],
       [(('%2$s mengganti separuh harga kuda dan membayar tabib hewan.', '%2$s pays half the horse\'s price and the animal healer.'), ('%1$s puas; %2$s menyesal.', '%1$s is satisfied; %2$s regrets.')),
        (('Tuduhan ditolak; %1$s membayar perawatan kuda atas penyembunyiannya.', 'The claim is dismissed; %1$s pays for the horse\'s care for concealing it.'), ('%2$s lega; %1$s kehilangan kepercayaan pelanggan.', '%2$s is relieved; %1$s loses customers\' trust.')),
        (('Kedua pihak membayar tabib hewan bersama dan kuda dipinjamkan gratis ke tetua.', 'Both pay the animal healer together and the horse is lent free to the elder.'), ('Kuda sembuh, dendam surut.', 'The horse heals, the grudge subsides.'))])

    PK('atap_bocor', ('Genteng %1$s yang jatuh', 'The falling tiles of %1$s'),
       ('%1$s menuntut %2$s memperbaiki atap yang gentengnya jatuh ke halaman dan merusak jemuran.',
        '%1$s demands that %2$s repair a roof whose tiles fell into his yard and spoiled the washing.'), ['warga', 'koki'], ['warga', 'tukang'],
       (('“Genteng rumahnya jatuh lagi, kali ini nyaris menimpa cucuku!”', '“His roof tiles fell again, this time nearly hitting my grandchild!”'),
        ('“Atapku dipasang tukang bulan lalu. Gentengnya terdorong angin dari sisi %1$s.”', '“My roof was laid by a builder last month. The tiles were pushed by wind from %1$s\'s side.”')),
       [('“Dua genteng pecah di jemuran %1$s. Aku lihat sendiri.”', '“Two tiles broke on %1$s\'s washing line. I saw it myself.”'),
        ('“Atap %2$s memang miring ke arah halaman %1$s.”', '“%2$s\'s roof does slope toward %1$s\'s yard.”')],
       [('“Tukangnya bilang atap itu pantas, tapi angin kemarin memang kencang.”', '“The builder said the roof was sound, but yesterday\'s wind was strong.”'),
        ('“Pohon besar di halaman %1$s mematahkan dahan ke atap itu.”', '“The big tree in %1$s\'s yard snapped a bough onto that roof.”')],
       ('“Aku tidak melihat. Ada suara pecah, lalu aku menutup pintu.”', '“I did not see. There was a crash, and I shut my door.”'),
       [('barang', 'minecraft:bricks', ('Pecahan genteng di halaman: bahannya longgar, tepinya rapuh.', 'A tile shard in the yard: loose material, brittle edges.'),
         ('Pecahan genteng di halaman: bersih dan padat, tanda pasangan baru.', 'A tile shard in the yard: clean and dense, a sign of fresh laying.')),
        ('catatan', '', ('Catatan tukang: atap %2$s dipasang dengan genteng murah campuran.', 'Builder\'s note: %2$s\'s roof was laid with cheap mixed tiles.'),
         ('Catatan tukang: atap %2$s dipasang sesuai standar kota.', 'Builder\'s note: %2$s\'s roof was laid to the town standard.'))],
       [(('%2$s mengganti jemuran dan memasang ulang atapnya dengan genteng baik.', '%2$s replaces the washing and relays his roof with good tiles.'), ('%1$s puas; %2$s mengeluh biaya.', '%1$s is satisfied; %2$s complains of the cost.')),
        (('Tuduhan ditolak; %1$s diminta memangkas dahan pohonnya.', 'The claim is dismissed; %1$s is told to prune his tree.'), ('%2$s lega; %1$s menggerutu soal pohon.', '%2$s is relieved; %1$s grumbles about the tree.')),
        (('Mereka memperbaiki atap dan memangkas pohon bersama, makan siang dari %1$s.', 'They mend the roof and prune the tree together, lunch from %1$s.'), ('Tetangga akur lagi.', 'The neighbours are friends again.'))], suap=0.2)


def isi_perkara_b():
    PK('obat_palsu', ('Obat palsu dari %2$s', 'The fake medicine of %2$s'),
       ('%1$s menuntut %2$s mengembalikan uang untuk ramuan penurun demam yang ternyata hanya air gula.',
        '%1$s demands a refund from %2$s for a fever draught that proved to be only sugar water.'), ['warga', 'petani'], ['penjual_obat', 'tabib'],
       (('“Anakku minum ramuan itu tiga hari dan demamnya tak turun. Rasanya manis saja!”', '“My child drank that draught three days and the fever did not drop. It only tasted sweet!”'),
        ('“Ramuanku asli, dibuat dari akar dan daun. Demam anak itu memang lebih berat dari dugaanku.”', '“My draught is real, made of roots and leaves. The child\'s fever was simply worse than I guessed.”')),
       [('“Aku mencicipi sisa ramuan itu. Yang terasa hanya gula dan sedikit jahe.”', '“I tasted what was left of the draught. It was only sugar and a little ginger.”'),
        ('“Kulihat %2$s mengaduk di baskom dengan gula batu, tidak ada akar sama sekali.”', '“I saw %2$s stirring a basin with rock sugar, no root at all.”')],
       [('“Aku minum ramuan yang sama dan demamku turun dalam semalam.”', '“I drank the same draught and my fever went down overnight.”'),
        ('“%1$s memberi anaknya terlalu sedikit dan berhenti di hari kedua.”', '“%1$s gave the child too little and stopped on the second day.”')],
       ('“Obat itu urusan tabib, bukan urusanku.”', '“Medicine is the healer\'s business, not mine.”'),
       [('barang', 'minecraft:sugar', ('Endapan di botol ramuan: butiran gula bening, tak ada serpihan daun.', 'Residue in the draught bottle: clear sugar grains, no leaf flecks.'),
         ('Endapan di botol ramuan: serpihan akar halus di dasar, tanda ramuan asli.', 'Residue in the draught bottle: fine root flecks at the bottom, a sign of a real brew.')),
        ('gosip', '', ('Kabar dari %3$s: dua pembeli lain mengeluh hal yang sama pada %2$s.', 'Word from %3$s: two other buyers complained of the same to %2$s.'),
         ('Kabar dari %3$s: ramuan %2$s pernah menyembuhkan separuh jalan.', 'Word from %3$s: %2$s\'s draughts once healed half the street.'))],
       [(('%2$s mengembalikan uang, menyerahkan sisa ramuan ke tabib kota, dan dilarang berjualan sebulan.', '%2$s refunds, hands the rest of the draught to the town healer, and is barred from selling for a month.'),
         ('%1$s puas; %2$s kehilangan pelanggan.', '%1$s is satisfied; %2$s loses customers.')),
        (('Tuduhan ditolak; %1$s diberi nasihat agar tidak berhenti di tengah pengobatan.', 'The claim is dismissed; %1$s is advised not to stop a treatment halfway.'),
         ('%2$s lega; %1$s malu dan khawatir.', '%2$s is relieved; %1$s is ashamed and worried.')),
        (('Separuh uang dikembalikan, dan tabib kota memeriksa si anak tanpa biaya.', 'Half the money is refunded, and the town healer examines the child free.'),
         ('Anak pulih, kedua pihak berjabat tangan.', 'The child recovers, both sides shake hands.'))], suap=0.45)

    PK('giok_palsu', ('Giok yang ternyata kaca', 'The jade that was glass'),
       ('%1$s menuntut %2$s mengembalikan harga gelang giok yang ketahuan terbuat dari kaca hijau.',
        '%1$s demands that %2$s refund a jade bracelet found to be made of green glass.'), ['warga', 'pedagang_keliling'], ['pedagang_keliling', 'pandai_pusaka'],
       (('“Ia bersumpah itu giok Hetian! Pandai emas bilang itu kaca.”', '“He swore it was Hetian jade! The goldsmith says it is glass.”'),
        ('“Aku menjual apa yang kubeli. Aku sendiri tertipu pedagang selatan.”', '“I sold what I bought. I was cheated myself by a southern trader.”')),
       [('“Aku mengetuk gelang itu dengan kuku. Bunyinya nyaring seperti gelas.”', '“I tapped the bracelet with my nail. It rang clear like a glass.”'),
        ('“Aku lihat %2$s menjual tiga gelang serupa minggu ini.”', '“I saw %2$s sell three similar bracelets this week.”')],
       [('“%2$s membeli gelang itu dari kafilah dengan sertifikat bertanda.”', '“%2$s bought those bracelets from a caravan with a marked certificate.”'),
        ('“%1$s menjatuhkannya sekali dan menyalahkan penjual saat retak.”', '“%1$s dropped it once and blamed the seller when it cracked.”')],
       ('“Giok, kaca... bagiku semuanya berkilau.”', '“Jade, glass... to me it all sparkles.”'),
       [('barang', 'minecraft:emerald', ('Gelang itu digores di batu asah: tergores, tanda kaca lunak.', 'The bracelet scratched on a whetstone: it scratched, a sign of soft glass.'),
         ('Gelang itu digores di batu asah: tidak tergores, tetapi retak oleh benturan.', 'The bracelet scratched on a whetstone: unscratched, but cracked by an impact.')),
        ('catatan', '', ('Surat jalan kafilah: tidak mencantumkan giok sama sekali, hanya “batu hias”.', 'Caravan waybill: no mention of jade at all, only “ornamental stone”.'),
         ('Surat jalan kafilah: mencantumkan “giok hijau, enam buah” dengan cap pedagang.', 'Caravan waybill: lists “green jade, six pieces” with the trader\'s stamp.'))],
       [(('%2$s mengembalikan harga penuh dan menempel pengumuman di lapaknya.', '%2$s refunds in full and posts a notice at his stall.'), ('%1$s puas; %2$s kehilangan nama di pasar.', '%1$s is satisfied; %2$s loses his name in the market.')),
        (('Tuduhan ditolak; %1$s diminta mengganti ongkos pemeriksaan.', 'The claim is dismissed; %1$s is asked to repay the inspection fee.'), ('%2$s lega; %1$s kecewa.', '%2$s is relieved; %1$s is disappointed.')),
        (('Setengah harga dikembalikan; kafilah itu dilaporkan ke penjaga gerbang.', 'Half the price is refunded; the caravan is reported to the gate guard.'), ('Penipu sebenarnya mungkin sudah jauh.', 'The true cheat may already be far away.'))], suap=0.5)

    PK('kolam_ikan', ('Ikan di kolam %1$s', 'The fish in %1$s\'s pond'),
       ('%1$s menuduh %2$s mengambil ikan mas dari kolamnya setiap subuh.',
        '%1$s accuses %2$s of taking carp from his pond every dawn.'), ['petani', 'nelayan'], ['nelayan', 'warga'],
       (('“Ikan di kolamku berkurang separuh dalam seminggu. Tidak ada yang mengambilnya selain dia!”', '“The fish in my pond are halved in a week. No one but he takes them!”'),
        ('“Aku memancing di sungai di luar pagarnya. Burung bangau yang mengambil ikannya.”', '“I fish in the river outside his fence. It is the heron that takes his fish.”')),
       [('“Subuh tadi aku melihat bayangan mendayung rakit kecil ke kolam itu.”', '“At dawn I saw a shadow pole a small raft into that pond.”'),
        ('“Ember %2$s selalu basah dan penuh pada pagi hari.”', '“%2$s\'s bucket is always wet and full in the morning.”')],
       [('“Bangau putih bertengger di pohon dekat kolam itu setiap pagi.”', '“A white heron perches in the tree by that pond every morning.”'),
        ('“%2$s sudah memberi ikan kepada tetangga sejak dulu, itu dari sungai.”', '“%2$s has shared fish with neighbours for ages, from the river.”')],
       ('“Ikan? Aku tak makan ikan. Aku makan sayur.”', '“Fish? I do not eat fish. I eat vegetables.”'),
       [('barang', 'minecraft:cod', ('Sisik ikan mas emas ditemukan di keranjang %2$s.', 'Gold carp scales found in %2$s\'s basket.'),
         ('Bulu bangau putih tertinggal di tepi kolam, dengan jejak cakar.', 'A white heron feather left by the pond, with claw prints.')),
        ('gosip', '', ('Kabar dari %3$s: ember %2$s tercium bau lumpur kolam.', 'Word from %3$s: %2$s\'s bucket smelled of pond mud.'),
         ('Kabar dari %3$s: bangau itu sudah merampok tiga kolam lain bulan ini.', 'Word from %3$s: that heron has raided three other ponds this month.'))],
       [(('%2$s mengganti ikan dan dilarang mendekati kolam itu.', '%2$s pays for the fish and is barred from the pond.'), ('%1$s puas; %2$s dipermalukan.', '%1$s is satisfied; %2$s is shamed.')),
        (('Tuduhan ditolak; %1$s memasang jaring di atas kolamnya.', 'The claim is dismissed; %1$s hangs a net over his pond.'), ('%2$s lega; %1$s menghela napas.', '%2$s is relieved; %1$s sighs.')),
        (('Kolam diberi jaring, dan %2$s diizinkan memancing di sisi sungai.', 'The pond gets a net, and %2$s may fish on the river side.'), ('Bangau yang kecewa.', 'Only the heron is disappointed.'))], suap=0.2)

    PK('surat_wasiat', ('Surat wasiat yang meragukan', 'The doubtful will'),
       ('%1$s menyatakan surat wasiat gurunya yang meninggal adalah palsu, karena %2$s tiba-tiba mewarisi seluruh kebun.',
        '%1$s declares his late teacher\'s will a forgery, because %2$s suddenly inherits the whole garden.'), ['guru_sekolah', 'sastrawan', 'warga'], ['warga', 'petani'],
       (('“Tulisan gurunya terlalu rapi! Ia gemetar sejak setahun terakhir, tulisannya tak mungkin sebagus itu.”', '“The teacher\'s writing is too neat! He trembled for his last year, his hand could not have been that good.”'),
        ('“Surat itu ditulis di depan dua saksi dan dicap. Siapa yang meragukan, meragukan almarhum.”', '“It was written before two witnesses and stamped. Whoever doubts it, doubts the dead man.”')),
       [('“Aku melihat gurunya menulis sendiri, ia memegang kuas dengan dua tangan.”', '“I saw the teacher write himself, holding the brush with both hands.”'),
        ('“Cap pada surat itu tidak sama dengan cap lama gurunya.”', '“The stamp on that will is not the same as the teacher\'s old seal.”')],
       [('“Aku saksi pertama. Gurunya bicara jelas dan menandatangani sendiri.”', '“I was the first witness. The teacher spoke clearly and signed himself.”'),
        ('“%1$s tak pernah menjenguk gurunya sejak ia sakit.”', '“%1$s never visited his teacher once he fell ill.”')],
       ('“Aku tidak pandai membaca. Surat itu sama saja bagiku.”', '“I read poorly. A will is all the same to me.”'),
       [('catatan', '', ('Perbandingan tulisan: huruf-huruf di surat wasiat jauh lebih mantap daripada tulisan guru setahun terakhir.', 'Handwriting comparison: the letters of the will are far steadier than the teacher\'s last-year hand.'),
         ('Perbandingan tulisan: coretan khas guru terlihat pada tiga huruf surat itu.', 'Handwriting comparison: the teacher\'s trademark flourish is visible in three letters of the will.')),
        ('barang', 'minecraft:paper', ('Kertas surat wasiat: dibuat setahun lalu, sebelum gurunya sakit parah.', 'The will\'s paper: made a year ago, before the teacher fell gravely ill.'),
         ('Kertas surat wasiat: tinta kuas lama, cap merah bertanggal bulan lalu.', 'The will\'s paper: old brush ink, red seal dated last month.'))],
       [(('Surat wasiat dibatalkan dan kebun dibagi menurut adat kepada murid dan keluarga.', 'The will is voided and the garden divided by custom between pupils and family.'), ('%1$s puas; %2$s dicurigai memalsu.', '%1$s is satisfied; %2$s is suspected of forgery.')),
        (('Surat wasiat dinyatakan sah; %1$s diminta menghormati almarhum.', 'The will is declared valid; %1$s is told to honour the dead.'), ('%2$s lega; %1$s kecewa.', '%2$s is relieved; %1$s is disappointed.')),
        (('Kebun dibagi dua; separuh untuk sekolah almarhum.', 'The garden is split; half goes to the late man\'s school.'), ('Nama guru itu dihormati keduanya.', 'Both honour the teacher\'s name.'))], suap=0.35)

    PK('taruhan_jangkrik', ('Taruhan adu jangkrik', 'The cricket-fight wager'),
       ('%1$s menuntut %2$s membayar taruhan lima belas tahil yang kalah dalam adu jangkrik di bawah pohon.',
        '%1$s demands that %2$s pay a lost wager of fifteen taels from the cricket fight under the tree.'), ['pemilik_teh', 'warga'], ['warga', 'pedagang_keliling'],
       (('“Jangkrik %2$s lari dari arena sebelum pertarungan selesai. Itu kalah!”', '“%2$s\'s cricket fled the arena before the fight ended. That is a loss!”'),
        ('“Jangkrikku tidak lari, ia melompat untuk menyerang! %1$s menyalakan lentera di sisi arena dan membuatnya silau.”', '“My cricket did not flee, it leapt to attack! %1$s lit a lantern beside the ring and dazzled it.”')),
       [('“Aku berdiri di sebelah arena. Jangkrik %2$s melompat keluar lewat sisi kiri.”', '“I stood by the arena. %2$s\'s cricket jumped out through the left side.”'),
        ('“Tidak ada lentera. Yang ada hanya matahari sore.”', '“There was no lantern. Only the afternoon sun.”')],
       [('“Aku melihat lentera kecil di dekat tangan %1$s. Jangkrik itu terlihat silau.”', '“I saw a small lantern near %1$s\'s hand. The cricket looked dazzled.”'),
        ('“%1$s sering menang dengan cara yang tidak jelas.”', '“%1$s often wins in unclear ways.”')],
       ('“Taruhan? Aku tidak berjudi. Aku hanya menonton.”', '“Wager? I do not gamble. I only watch.”'),
       [('barang', 'minecraft:torch', ('Lentera kecil ditemukan di balik tempayan, minyaknya masih hangat.', 'A small lantern found behind a jar, its oil still warm.'),
         ('Tidak ada lentera di dekat arena, hanya bekas lilin lama di tempayan.', 'No lantern by the arena, only an old candle stub in a jar.')),
        ('gosip', '', ('Kabar dari %3$s: %1$s pernah dilarang dari arena kota tetangga karena curang.', 'Word from %3$s: %1$s was once banned from the next town\'s ring for cheating.'),
         ('Kabar dari %3$s: jangkrik %2$s memang penakut dan kalah tiga kali berturut-turut.', 'Word from %3$s: %2$s\'s cricket is a coward and lost three times in a row.'))],
       [(('%2$s membayar taruhan dan jangkriknya dipensiunkan.', '%2$s pays the wager and his cricket is retired.'), ('%1$s puas; %2$s menggerutu soal harga diri.', '%1$s is satisfied; %2$s grumbles about pride.')),
        (('Pertarungan diulang tanpa lentera; taruhan ditahan di balai.', 'The fight is repeated without a lantern; the wager is held at the hall.'), ('%2$s lega; %1$s harus berlaga lagi.', '%2$s is relieved; %1$s must fight again.')),
        (('Taruhan dibagi dua dan arena ditandai dengan garis kapur.', 'The wager is split and the arena marked with a chalk line.'), ('Kedua jangkrik kembali ke sangkar.', 'Both crickets go back to their cages.'))], suap=0.4)

    PK('sumur_tercemar', ('Sumur yang berbau', 'The foul-smelling well'),
       ('%1$s menuduh %2$s membuang sisa bahan celup ke dekat sumur bersama sehingga airnya berbau dan berwarna.',
        '%1$s accuses %2$s of dumping dye waste near the shared well so that its water smells and is tinted.'), ['warga', 'koki'], ['tukang', 'pedagang_keliling'],
       (('“Air sumur berwarna biru dan berbau. Anak-anak sakit perut. Ini pasti dari bengkel celupnya!”', '“The well water is blue and smells. The children have stomach aches. This must be from his dye shop!”'),
        ('“Bengkelku membuang limbah ke selokan, bukan ke sumur. Air sumur berbau karena tikus mati di dalamnya.”', '“My shop drains waste to the ditch, not the well. The water smells because a dead rat is in it.”')),
       [('“Aku mencium bau nila yang sama di air sumur dan di selokan %2$s.”', '“I smelled the same indigo at the well and in %2$s\'s ditch.”'),
        ('“Ember celupnya selalu dibilas dekat sumur, aku sering melihatnya.”', '“His dye buckets are always rinsed by the well, I often see it.”')],
       [('“Aku membersihkan sumur itu sendiri tadi pagi, kutemukan tikus mati dekat dasar.”', '“I cleaned that well myself this morning, found a dead rat near the bottom.”'),
        ('“Selokan bengkel mengalir ke arah sungai, bukan sumur.”', '“The shop ditch flows to the river, not the well.”')],
       ('“Air sumur? Aku minum air dari sungai.”', '“Well water? I drink from the river.”'),
       [('barang', 'minecraft:blue_dye', ('Endapan nila di dinding sumur, hanya pada sisi yang menghadap bengkel.', 'Indigo deposit on the well wall, only on the side facing the shop.'),
         ('Bangkai tikus tenggelam dekat dasar sumur, bulunya sudah rontok.', 'A drowned rat near the well bottom, its fur already shedding.')),
        ('gosip', '', ('Kabar dari %3$s: bengkel celup pernah ditegur tetua karena membuang limbah sembarangan.', 'Word from %3$s: the dye shop was warned by the elder for dumping carelessly.'),
         ('Kabar dari %3$s: %1$s sudah lama ingin bengkel itu pindah dari lorongnya.', 'Word from %3$s: %1$s has long wanted that shop moved from his lane.'))],
       [(('%2$s memindahkan pencucian celupnya ke tepi sungai dan membayar pembersihan sumur.', '%2$s moves his dye rinsing to the riverbank and pays for the well\'s cleaning.'), ('%1$s puas; %2$s menanggung biaya.', '%1$s is satisfied; %2$s bears the cost.')),
        (('Tuduhan ditolak; sumur dibersihkan dari tikus dan ditutup papan.', 'The claim is dismissed; the well is cleaned of the rat and covered with a board.'), ('%2$s lega; %1$s menanggung malu.', '%2$s is relieved; %1$s bears the shame.')),
        (('Sumur ditutup papan, dan selokan bengkel disalurkan jauh dari sumur.', 'The well is covered, and the shop ditch rerouted away from it.'), ('Lorong itu bernapas lega.', 'The lane breathes easy.'))])

    PK('pernikahan_batal', ('Pernikahan yang dibatalkan', 'The cancelled wedding'),
       ('%1$s menuntut %2$s mengganti biaya pesta pernikahan yang dibatalkan sepihak seminggu sebelum hari H.',
        '%1$s demands that %2$s repay the cost of a wedding feast cancelled unilaterally a week before the day.'), ['warga', 'koki', 'pemilik_teh'], ['warga', 'petani'],
       (('“Tenda sudah dipesan, makanan sudah dibeli, undangan sudah disebar! Dan ia membatalkan tanpa alasan!”', '“The tent is booked, the food bought, the invitations sent! And he cancels with no reason!”'),
        ('“Aku punya alasan: calon pengantinnya menyembunyikan utang besar. %1$s sendiri tahu dan diam.”', '“I have a reason: the bride-to-be hid a large debt. %1$s knew himself and kept silent.”')),
       [('“Aku memasak dua hari untuk pesta itu. Semua bahan sudah dibeli di depan mataku.”', '“I cooked two days for that feast. All the ingredients were bought before my eyes.”'),
        ('“%2$s hanya berkata ‘sudah berubah pikiran’, tanpa alasan lain.”', '“%2$s only said ‘I changed my mind’, no other reason.”')],
       [('“Aku mendengar pemberi utang menagih di pintu calon pengantin itu.”', '“I heard the lender dunning at the door of that bride-to-be.”'),
        ('“%1$s pernah berkata ‘yang penting menikah dulu, utang urusan nanti’.”', '“%1$s once said ‘marry first, debts can wait’.”')],
       ('“Pernikahan? Aku tidak diundang, jadi aku tidak tahu.”', '“A wedding? I was not invited, so I do not know.”'),
       [('catatan', '', ('Daftar belanja pesta: seluruh bahan sudah dibayar penuh atas nama %1$s.', 'The feast shopping list: all ingredients paid in full under %1$s\'s name.'),
         ('Catatan utang: nama calon pengantin tercatat berutang tiga puluh tahil di balai.', 'Debt record: the bride-to-be is listed owing thirty taels at the hall.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s sudah dua kali membatalkan pertunangan sebelumnya.', 'Word from %3$s: %2$s has cancelled two earlier betrothals.'),
         ('Kabar dari %3$s: %1$s tahu soal utang itu sejak awal dan menutupinya.', 'Word from %3$s: %1$s knew of the debt from the start and covered it up.'))],
       [(('%2$s mengganti separuh biaya pesta dan meminta maaf secara terbuka.', '%2$s repays half the feast cost and apologises publicly.'), ('%1$s puas; %2$s dicap tak bisa dipegang.', '%1$s is satisfied; %2$s is branded unreliable.')),
        (('Tuntutan ditolak; pembatalan sah karena utang disembunyikan.', 'The claim is dismissed; the cancellation is valid because the debt was hidden.'), ('%2$s lega; %1$s kehilangan muka.', '%2$s is relieved; %1$s loses face.')),
        (('Makanan pesta dibagikan ke kuil dan biaya dibagi dua.', 'The feast food is given to the temple and costs are split.'), ('Pesta yang batal tak jadi sia-sia.', 'The cancelled feast is not wasted.'))])

    PK('upah_tak_dibayar', ('Upah yang ditahan', 'The withheld wages'),
       ('%1$s menuntut %2$s membayar upah dua bulan yang ditahan dengan alasan pekerjaannya tidak rapi.',
        '%1$s demands that %2$s pay two months of wages withheld on the grounds that his work was sloppy.'), ['tukang', 'kuli', 'pelayan'], ['pemilik_teh', 'pedagang_keliling', 'pemilik_penginapan'],
       (('“Dua bulan aku bekerja dan tidak sepeser pun kuterima! Dia bilang pekerjaanku tidak rapi.”', '“Two months I worked and not a coin did I get! He says my work was sloppy.”'),
        ('“Ia merusak tiga guci dan menunda pekerjaan. Aku menahan upah sebagai ganti kerugian.”', '“He broke three jars and delayed work. I withheld the wages as compensation.”')),
       [('“Aku melihat pekerjaannya, dindingnya rapi dan lurus.”', '“I saw his work, the wall is neat and straight.”'),
        ('“%2$s tak pernah mencatat guci yang pecah sampai upah ditagih.”', '“%2$s never noted the broken jars until wages were demanded.”')],
       [('“Guci itu memang pecah di tangan %1$s, aku di sana.”', '“Those jars did break in %1$s\'s hands, I was there.”'),
        ('“Ia datang terlambat tiga kali minggu itu.”', '“He came late three times that week.”')],
       ('“Aku hanya tukang sapu. Aku tak tahu urusan upah.”', '“I am only the sweeper. I know nothing of wages.”'),
       [('catatan', '', ('Buku kerja: dua belas hari kerja tercatat dengan paraf %2$s di setiap halaman.', 'Work book: twelve working days noted with %2$s\'s initials on each page.'),
         ('Buku kerja: tiga hari ditandai “terlambat” dan satu baris “guci pecah” di tulisan %2$s.', 'Work book: three days marked “late” and one line “jars broken” in %2$s\'s hand.')),
        ('gosip', '', ('Kabar dari %3$s: %2$s sering menahan upah pekerja lalu mencari alasan.', 'Word from %3$s: %2$s often withholds workers\' wages and then finds a reason.'),
         ('Kabar dari %3$s: %1$s berpindah-pindah majikan karena sering bermasalah.', 'Word from %3$s: %1$s moves between employers because of frequent trouble.'))],
       [(('%2$s membayar seluruh upah dan denda keterlambatan.', '%2$s pays all wages and a late fine.'), ('%1$s puas; %2$s dikenal pelit.', '%1$s is satisfied; %2$s is known as miserly.')),
        (('Tuntutan ditolak sebagian; upah dikurangi harga guci.', 'The claim is partly dismissed; wages are reduced by the jars\' price.'), ('%2$s lega; %1$s menerima sedikit.', '%2$s is relieved; %1$s receives little.')),
        (('Upah dibayar tiga perempat; guci dihapus dari utang.', 'Wages paid three quarters; the jars struck from the debt.'), ('Keduanya kecewa tetapi mampu melanjutkan.', 'Both disappointed but able to go on.'))], suap=0.4)

    PK('cap_dipalsukan', ('Cap dagang yang dipalsukan', 'The forged trade seal'),
       ('%1$s menuduh %2$s memakai cap dagang tiruan pada kain dan menjualnya dengan nama bengkel miliknya.',
        '%1$s accuses %2$s of using a counterfeit trade seal on cloth sold in his workshop\'s name.'), ['pedagang_keliling', 'pandai_pusaka', 'tukang'], ['pedagang_keliling', 'pialang'],
       (('“Capku bergambar bangau dengan tiga bulu ekor. Kain ini bergambar bangau dengan dua bulu. Tiruan!”', '“My seal shows a crane with three tail feathers. This cloth shows a crane with two. A copy!”'),
        ('“Cap itu sah. Aku membeli kain dari bengkel %1$s sendiri, mungkin ia lupa.”', '“That seal is genuine. I bought the cloth from %1$s\'s own workshop, perhaps he forgot.”')),
       [('“Aku pelanggan lama %1$s. Kain ini kasar, tidak sehalus kain bengkelnya.”', '“I am a long-time customer of %1$s. This cloth is coarse, not as fine as his workshop\'s.”'),
        ('“%2$s menjual dengan harga setengah dari harga %1$s. Mustahil kualitas sama.”', '“%2$s sells at half of %1$s\'s price. The same quality is impossible.”')],
       [('“Aku melihat nota pembelian kain itu di bengkel %1$s.”', '“I saw the purchase note of that cloth at %1$s\'s workshop.”'),
        ('“Cap bangau dua bulu juga dipakai bengkel %1$s dua tahun lalu.”', '“The two-feather crane seal was used by %1$s\'s workshop two years ago too.”')],
       ('“Aku hanya membeli. Aku tidak pernah memeriksa cap.”', '“I only buy. I never check seals.”'),
       [('barang', 'minecraft:white_wool', ('Kain yang diperiksa: cap bangau dua bulu, tinta tidak tembus ke sisi belakang.', 'The cloth examined: a two-feather crane seal, the ink not bleeding through the back.'),
         ('Kain yang diperiksa: cap bangau dua bulu persis cap lama bengkel %1$s, tinta tembus.', 'The cloth examined: a two-feather crane seal identical to %1$s\'s old seal, ink bled through.')),
        ('catatan', '', ('Daftar cap balai: cap %1$s tiga bulu, tidak ada cap dua bulu atas namanya.', 'Hall seal register: %1$s\'s seal has three feathers, no two-feather seal is under his name.'),
         ('Daftar cap balai: cap dua bulu tercatat atas bengkel %1$s dua tahun lalu, lalu diganti.', 'Hall seal register: a two-feather seal is recorded for %1$s\'s workshop two years ago, then replaced.'))],
       [(('%2$s menyerahkan cap tiruannya, membayar denda, dan menarik semua kain dari pasar.', '%2$s surrenders the counterfeit seal, pays a fine and withdraws all cloth from the market.'), ('%1$s puas; %2$s dipermalukan.', '%1$s is satisfied; %2$s is shamed.')),
        (('Tuduhan ditolak; cap itu sah, %1$s diminta mencabut tuduhan.', 'The claim is dismissed; the seal is genuine, %1$s is told to withdraw the charge.'), ('%2$s lega; %1$s dipermalukan.', '%2$s is relieved; %1$s is shamed.')),
        (('Kedua cap didaftarkan ulang di balai; kain lama boleh habis terjual.', 'Both seals are re-registered at the hall; the old cloth may sell out.'), ('Masalah selesai dengan birokrasi.', 'The matter ends with bureaucracy.'))], suap=0.45)

    PK('kucing_tertabrak', ('Kucing %1$s dan gerobak', '%1$s\'s cat and the cart'),
       ('%1$s menuntut %2$s mengganti kucing kesayangannya yang tertabrak gerobak di tikungan pasar.',
        '%1$s demands that %2$s compensate for his pet cat hit by a cart at the market corner.'), ['warga', 'penjual_ikan'], ['kuli', 'pedagang_keliling'],
       (('“Gerobaknya melaju tanpa melihat! Kucingku tertabrak dan sekarang pincang!”', '“His cart raced without looking! My cat was hit and is now lame!”'),
        ('“Kucing itu melompat tepat di depan roda. Aku sudah menarik tali kekang sekuat tenaga.”', '“That cat leapt right before the wheel. I pulled the reins with all my strength.”')),
       [('“Gerobak itu melaju kencang di tikungan, kulihat roda kiri naik ke trotoar.”', '“The cart rushed at the corner, I saw its left wheel mount the pavement.”'),
        ('“%2$s menoleh ke penjual bakpao saat itu.”', '“%2$s was looking at the bun seller just then.”')],
       [('“Kucing itu sedang mengejar tikus dan melompat dari bawah tong.”', '“The cat was chasing a mouse and leapt from under a barrel.”'),
        ('“Gerobak itu berjalan pelan karena muatan penuh. Aku di sampingnya.”', '“The cart was slow with its full load. I was beside it.”')],
       ('“Kucing? Aku tak tahu ada kucing. Pasar ini penuh kucing.”', '“A cat? I did not know about a cat. The market is full of cats.”'),
       [('barang', 'minecraft:string', ('Jejak roda memanjang di tanah tikungan, menandakan rem mendadak yang terlambat.', 'A long wheel mark in the corner dirt, a sign of braking too late.'),
         ('Jejak roda pendek dan lurus; tidak ada tanda melaju kencang.', 'A short, straight wheel mark; no sign of speed.')),
        ('gosip', '', ('Kabar dari %3$s: gerobak %2$s sering melaju kencang di pasar pagi.', 'Word from %3$s: %2$s\'s cart often rushes in the morning market.'),
         ('Kabar dari %3$s: kucing %1$s terkenal bandel dan sering menyelinap ke jalan.', 'Word from %3$s: %1$s\'s cat is notorious and often slips into the road.'))],
       [(('%2$s membayar tabib hewan dan memasang lonceng di gerobaknya.', '%2$s pays the animal healer and hangs a bell on his cart.'), ('%1$s puas; %2$s lebih berhati-hati.', '%1$s is satisfied; %2$s is more careful.')),
        (('Tuduhan ditolak; %1$s diminta mengikat kucingnya di dalam rumah.', 'The claim is dismissed; %1$s is told to keep his cat indoors.'), ('%2$s lega; %1$s sedih.', '%2$s is relieved; %1$s is sad.')),
        (('Biaya tabib hewan dibagi dua; kucing diberi kalung lonceng.', 'The animal healer\'s fee is split; the cat is given a bell collar.'), ('Kucing sembuh dan tetap bandel.', 'The cat heals and stays naughty.'))], suap=0.15)

    PK('lapak_pasar', ('Sengketa lapak di pasar', 'The market stall dispute'),
       ('%1$s dan %2$s berebut lapak di sudut pasar yang dekat sumur, yang sama-sama mereka klaim sebagai tempat turun-temurun.',
        '%1$s and %2$s contest the stall at the market corner near the well, which both claim as hereditary.'), ['pedagang_keliling', 'penjual_ikan'], ['pedagang_keliling', 'petani'],
       (('“Kakekku berdagang di sudut itu sejak kota ini punya pasar. Itu lapak keluargaku!”', '“My grandfather has traded at that corner since the town had a market. That is my family\'s stall!”'),
        ('“Ayahku membeli lapak itu dari %1$s tiga puluh tahun lalu. Ada tanda terimanya.”', '“My father bought that stall from %1$s thirty years ago. There is a receipt.”')),
       [('“Dulu aku membeli ikan di sudut itu dari kakek %1$s. Aku ingat bau lautnya.”', '“I used to buy fish at that corner from %1$s\'s grandfather. I remember the smell of the sea.”'),
        ('“Batu alas lapak itu diukir lambang keluarga %1$s.”', '“The stall\'s base stone is carved with %1$s\'s family mark.”')],
       [('“Aku di sana saat pembelian. Uangnya dihitung tiga kali.”', '“I was there at the purchase. The money was counted three times.”'),
        ('“Kakek %1$s memang pernah berdagang di sana, lalu menjualnya.”', '“%1$s\'s grandfather did trade there once, then sold it.”')],
       ('“Lapak itu? Aku selalu lewat di sisi lain pasar.”', '“That stall? I always pass on the other side of the market.”'),
       [('catatan', '', ('Daftar pasar: nama keluarga %1$s tercatat di sudut itu sampai tiga puluh tahun lalu.', 'Market register: the %1$s family name is listed at that corner until thirty years ago.'),
         ('Daftar pasar: sudut itu dialihkan atas nama keluarga %2$s tiga puluh tahun lalu.', 'Market register: that corner was transferred to the %2$s family thirty years ago.')),
        ('barang', 'minecraft:paper', ('Surat izin lama: lambang keluarga %1$s, tak ada catatan penjualan.', 'An old permit: the %1$s family mark, no sale recorded.'),
         ('Tanda terima tua dengan cap merah: lapak dibeli dengan lima belas tahil.', 'An old receipt with a red seal: the stall bought for fifteen taels.'))],
       [(('Lapak dikembalikan kepada %1$s; %2$s diberi sudut lain dengan potongan sewa setahun.', 'The stall returns to %1$s; %2$s receives another corner with a year\'s rent cut.'), ('%1$s puas; %2$s kecewa.', '%1$s is satisfied; %2$s is disappointed.')),
        (('Lapak tetap milik %2$s sesuai tanda terima; %1$s dibantu mencari sudut baru.', 'The stall stays with %2$s per the receipt; %1$s is helped to a new corner.'), ('%2$s lega; %1$s sakit hati.', '%2$s is relieved; %1$s is hurt.')),
        (('Lapak dibagi: pagi untuk %1$s, siang untuk %2$s.', 'The stall is shared: mornings for %1$s, afternoons for %2$s.'), ('Pasar tetap ramai oleh dua suara.', 'The market stays loud with two voices.'))])

    PK('pohon_tumbang', ('Pohon tumbang di pagar %1$s', 'The tree down on %1$s\'s fence'),
       ('%1$s menuntut %2$s mengganti pagar yang rubuh ditimpa pohon huai yang sudah lama dikeluhkan lapuk.',
        '%1$s demands that %2$s pay for a fence crushed by a locust tree long complained of as rotten.'), ['warga', 'petani'], ['warga', 'tukang'],
       (('“Sudah tiga kali kukatakan pohon itu lapuk! Sekarang pagarku hancur!”', '“I told him three times that tree was rotten! Now my fence is smashed!”'),
        ('“Pohon itu sehat. Angin kencang yang menumbangkannya, bukan kelapukannya.”', '“That tree was healthy. The strong wind felled it, not rot.”')),
       [('“Aku mendengar %1$s menegur %2$s soal pohon itu bulan lalu.”', '“I heard %1$s warn %2$s about that tree last month.”'),
        ('“Batang itu berlubang di dalam, aku melihat setelah roboh.”', '“The trunk was hollow inside, I saw after it fell.”')],
       [('“Angin malam itu menumbangkan dua pohon lain di jalan ini.”', '“The wind that night felled two other trees on this road.”'),
        ('“%2$s memangkas dahannya musim lalu. Ia merawat pohon itu.”', '“%2$s pruned its boughs last season. He tended that tree.”')],
       ('“Pohon? Banyak pohon di jalan ini. Entah yang mana.”', '“A tree? There are many on this road. I know not which.”'),
       [('barang', 'minecraft:oak_log', ('Potongan batang: bagian dalam coklat berlubang dan lunak, tanda lapuk lama.', 'A trunk cross-section: brown, hollow and soft inside, a sign of long rot.'),
         ('Potongan batang: bagian dalam keras dan lembap, patah oleh tekanan angin.', 'A trunk cross-section: hard and damp inside, snapped by wind pressure.')),
        ('catatan', '', ('Catatan tukang kayu: dua bulan lalu ia memperingatkan %2$s tentang pohon itu.', 'The carpenter\'s note: two months ago he warned %2$s about that tree.'),
         ('Catatan tukang kayu: pohon itu diperiksa musim semi dan dinyatakan sehat.', 'The carpenter\'s note: that tree was inspected in spring and declared healthy.'))],
       [(('%2$s mengganti pagar dan menebang dua pohon lain yang tampak lapuk.', '%2$s pays for the fence and fells two other trees that look rotten.'), ('%1$s puas; %2$s menyesal.', '%1$s is satisfied; %2$s regrets.')),
        (('Tuduhan ditolak; angin dinyatakan penyebab tunggal.', 'The claim is dismissed; the wind is declared the sole cause.'), ('%2$s lega; %1$s harus membayar pagarnya sendiri.', '%2$s is relieved; %1$s must pay for his own fence.')),
        (('Pagar diganti bersama, pohon yang tersisa diperiksa tukang kayu kota.', 'The fence is replaced together, the remaining trees checked by the town carpenter.'), ('Jalan itu lebih aman.', 'The road is safer.'))], suap=0.2)

    PK('anjing_menggigit', ('Anjing %2$s menggigit', '%2$s\'s dog bit'),
       ('%1$s menuntut %2$s membayar tabib setelah anjingnya menggigit kaki cucunya di lorong.',
        '%1$s demands that %2$s pay the healer after his dog bit the grandchild\'s leg in the lane.'), ['warga', 'tukang'], ['warga', 'petani'],
       (('“Anjing itu menyerang cucuku tanpa sebab! Kakinya berdarah!”', '“That dog attacked my grandchild for no reason! Her leg bled!”'),
        ('“Anjingku tidak menggigit tanpa sebab. Cucunya menarik ekornya.”', '“My dog does not bite without cause. The grandchild pulled its tail.”')),
       [('“Aku melihat anjing itu menerjang tanpa menggonggong lebih dulu.”', '“I saw the dog lunge without barking first.”'),
        ('“Ia tidak diikat padahal lorong penuh anak-anak.”', '“It was not tied though the lane was full of children.”')],
       [('“Anjing itu menggonggong dua kali sebagai peringatan, lalu baru menggigit.”', '“The dog barked twice in warning, and only then bit.”'),
        ('“Cucunya memang suka menggoda hewan di lorong ini.”', '“The grandchild does like to tease animals in this lane.”')],
       ('“Anjing? Aku tidak melihat anjing. Aku sedang menyapu.”', '“A dog? I saw no dog. I was sweeping.”'),
       [('barang', 'minecraft:bone', ('Robekan kain celana kecil dengan bekas gigi dalam, tanda gigitan keras.', 'A torn scrap of a small trouser with deep tooth marks, the sign of a hard bite.'),
         ('Rambut ekor anjing tercabut tersangkut di tangan kecil, tanda ditarik paksa.', 'Dog tail hair torn and caught in a small hand, a sign of a forced pull.')),
        ('gosip', '', ('Kabar dari %3$s: anjing %2$s sudah dua kali menggonggong keras pada anak lain.', 'Word from %3$s: %2$s\'s dog has barked fiercely at other children twice.'),
         ('Kabar dari %3$s: anjing %2$s dikenal jinak dan sering dipeluk anak-anak.', 'Word from %3$s: %2$s\'s dog is known to be gentle and often hugged by children.'))],
       [(('%2$s membayar tabib dan wajib mengikat anjingnya di jam ramai.', '%2$s pays the healer and must tie his dog at busy hours.'), ('%1$s puas; %2$s menunduk malu.', '%1$s is satisfied; %2$s lowers his head in shame.')),
        (('Tuduhan ditolak; anjing tak bersalah dan anak diajari menyayangi hewan.', 'The claim is dismissed; the dog is blameless and the child is taught to be kind to animals.'), ('%2$s lega; %1$s kecewa.', '%2$s is relieved; %1$s is disappointed.')),
        (('Biaya tabib dibagi dua; anjing diberi lonceng dan anak diajari menyapanya.', 'The healer\'s fee is split; the dog gets a bell and the child is taught to greet it.'), ('Lorong itu tahu aturannya sekarang.', 'The lane knows the rules now.'))], suap=0.1)


def isi_perkara():
    isi_perkara_a()
    isi_perkara_b()
    n = len(PERKARA)
    if n < 21:
        ERRORS.append(f'only {n} new court cases, 42C-2 asks for at least 21')
    return PERKARA


# ================================================================================================ shadow plays (42.9.6) and item names
# The acts are read from the client choreography (LayarBayangRenderer.BABAK_NASKAH: figure a, figure b, mode) so data and screen never
# drift; the atlas cell numbers name the six figures of each script in tools/gen_kota_hidup_assets.py (wayang_textures).
def _babak_naskah():
    src = read(os.path.join(REPO, 'src/client/java/dev/kultivator/client/dunia/kota42/render/LayarBayangRenderer.java'))
    body = src[src.index('BABAK_NASKAH = {'):]
    body = body[:body.index('\n\t};')]
    acts = re.findall(r'\{(\d), (\d), (SAPA|TARUNG|CAKAP|JALAN|KEJAR)\}', body)
    if len(acts) != 16:
        ERRORS.append(f'LayarBayangRenderer.BABAK_NASKAH has {len(acts)} acts, expected 16')
        return [[(0, 1, 'sapa')] * 4] * 4
    return [[(int(a), int(b), m.lower()) for a, b, m in acts[i * 4:i * 4 + 4]] for i in range(4)]


NASKAH_ID = ['kera_sakti', 'ular_putih', 'hua_mulan', 'hakim_bao']


def isi_wayang():
    babak = _babak_naskah()
    java = read(os.path.join(JAVA, 'dunia/kota42/acara/LayarBayangEntity.java'))
    if re.search(r'NASKAH_ID = \{"kera_sakti", "ular_putih", "hua_mulan", "hakim_bao"\}', java) is None:
        ERRORS.append('LayarBayangEntity.NASKAH_ID changed: update NASKAH_ID of the generator')
    S = {
        'kera_sakti': dict(
            judul=('Kera Sakti Menuju Barat', 'The Monkey King Journeys West'),
            sumber=('Sumber: Wu Cheng\'en, Perjalanan ke Barat (Xiyou Ji)', 'Source: Wu Cheng\'en, Journey to the West (Xiyou Ji)'),
            tokoh=[('Sun Wukong, si Kera Sakti', 'Sun Wukong, the Monkey King'), ('Biksu Tang Sanzang', 'Tang Sanzang the monk'),
                   ('Zhu Bajie, si Babi', 'Zhu Bajie, the Pig'), ('Sha Wujing, si Pasir', 'Sha Wujing, the Sand Monk'),
                   ('Iblis Banteng yang Perkasa', 'The Mighty Bull Demon'), ('Kuda Putih sang Naga', 'The White Dragon Horse')],
            pembuka=('Dalang Su menaikkan sumbu lampu. Di layar yang bercahaya, keledai yang disamak seratus hari kembali hidup.',
                     'Puppeteer Su raises the lamp wick. On the glowing screen, the hide cured for a hundred days lives again.'),
            babak=[(('Biksu Tang menuntun kuda putih ke barat. Langkah mereka pelan, dan tidak ada yang tahu bahwa sembilan puluh sembilan marabahaya menunggu.',
                     'The monk leads the white horse west. Their pace is slow, and nobody knows that ninety-nine perils lie ahead.'),
                    ('Kita berjalan demi sutra, bukan demi cepat.', 'We walk for the scriptures, not for speed.'),
                    ('Hhh.', 'Hhhrrr.')),
                   (('Sun Wukong menghadang Iblis Banteng. Tongkat emas melawan tanduk baja, dan awan di langit ikut menghindar.',
                     'Sun Wukong faces the Bull Demon. Golden staff against steel horns, and even the clouds in the sky step aside.'),
                    ('Seratus dua puluh jurus, dan aku baru pemanasan!', 'A hundred and twenty bouts, and I am only warming up!'),
                    ('Kera kecil, kau akan jadi sup monyet!', 'Little ape, you will be monkey soup!')),
                   (('Di tepi sungai, Bajie dan Wujing bertengkar tentang siapa yang membawa bekal. Keduanya lupa; bekalnya sudah dimakan Bajie.',
                     'On the riverbank, Bajie and Wujing bicker over who carried the provisions. Both forget; Bajie ate them already.'),
                    ('Bukan aku! Perutku yang makan, bukan aku!', 'It was not me! My belly ate it, not me!'),
                    ('Aku membawa kopor. Kopor itu tidak berisi apa pun.', 'I carried the luggage. The luggage held nothing.')),
                   (('Perjalanan hari itu selesai. Murid dan guru saling memberi hormat di bawah bulan. Kuda putih merumput. Layar meredup.',
                     'The day\'s journey ends. Master and disciple bow to each other beneath the moon. The white horse grazes. The screen dims.'),
                    ('Guru, besok kita sampai di mana?', 'Master, where shall we be tomorrow?'),
                    ('Di mana pun, asalkan kita tetap berjalan.', 'Wherever, as long as we keep walking.'))],
            penutup=('Gong terakhir bergema, dan siluet-siluet itu kembali menjadi kulit. Penonton bertepuk tangan lama.',
                     'The last gong rings, and the silhouettes become hide again. The audience applauds for a long while.'),
            lore=('Tanda terima kasih Dalang Su: kera sakti membuat tujuh puluh dua perubahan, dan sebanyak itu pula cara menontonnya.',
                  'A thank-you from Puppeteer Su: the Monkey King has seventy-two transformations, and as many ways to watch him.')),
        'ular_putih': dict(
            judul=('Ular Putih di Danau Barat', 'The White Snake at West Lake'),
            sumber=('Sumber: legenda Ular Putih (Baishe Zhuan)', 'Source: the White Snake legend (Baishe Zhuan)'),
            tokoh=[('Bai Suzhen, ular putih', 'Bai Suzhen, the white snake'), ('Xiaoqing, ular hijau', 'Xiaoqing, the green snake'),
                   ('Xu Xian, sang tabib', 'Xu Xian, the physician'), ('Biksu Fahai', 'The monk Fahai'),
                   ('Ular Putih Purba', 'The Ancient White Serpent'), ('Ular Hijau Purba', 'The Ancient Green Serpent')],
            pembuka=('Lampu minyak berkedip, dan hujan turun di layar. Sebuah payung merah dibuka di tepi Danau Barat.',
                     'The oil lamp flickers, and rain falls across the screen. A red umbrella opens by West Lake.'),
            babak=[(('Hujan di jembatan Duan. Seorang tabib muda meminjamkan payungnya kepada seorang perempuan berbaju putih, dan keduanya tidak tahu bahwa musim semi baru saja memilih mereka.',
                     'Rain on Broken Bridge. A young physician lends his umbrella to a woman in white, and neither knows that spring has just chosen them.'),
                    ('Terima kasih, Tuan. Pinjamkan aku payungmu sampai di tepi danau.', 'Thank you, sir. Lend me your umbrella as far as the lakeshore.'),
                    ('Dengan senang hati, Nona.', 'Gladly, miss.')),
                   (('Dua saudari berjalan di jalan kota, dan siapa pun yang berpapasan merasa udara menjadi lebih sejuk.',
                     'Two sisters walk the town road, and anyone who passes feels the air grow cooler.'),
                    ('Kak, di sini banyak orang. Jangan menoleh ke langit.', 'Sister, there are crowds here. Do not look at the sky.'),
                    ('Aku tahu. Aku hanya ingin memandang pasarnya.', 'I know. I only want to look at the market.')),
                   (('Biksu Fahai datang dengan tongkat bergelang. Ia berkata, "Perempuan itu ular." Xiaoqing menghunus pedang, dan layar bergetar.',
                     'The monk Fahai arrives with his ringed staff. He says, "That woman is a serpent." Xiaoqing draws her sword, and the screen trembles.'),
                    ('Menyingkirlah, biksu! Kakakku tidak pernah menyakiti siapa pun!', 'Stand aside, monk! My sister never hurt anyone!'),
                    ('Kasih sayang manusia dan ular tidak akan berakhir baik.', 'The love of man and serpent will not end well.')),
                   (('Fahai mengejar Bai Suzhen sampai ke jembatan itu lagi. Air Danau Barat naik di belakang layar, dan seluruh penonton menahan napas.',
                     'Fahai chases Bai Suzhen back to that bridge. The water of West Lake rises behind the screen, and the entire audience holds its breath.'),
                    ('...', '...'),
                    ('Berhenti, siluman!', 'Halt, demon!'))],
            penutup=('Layar menjadi putih seperti kabut danau, lalu padam. Seseorang di barisan belakang menyeka matanya.',
                     'The screen turns white as lake mist, then goes dark. Someone in the back row wipes their eyes.'),
            lore=('Kata Dalang Su: dalam kisah ular putih, payung selalu lebih tua dari pemiliknya, dan hujan selalu lebih setia dari janji.',
                  'Puppeteer Su says: in the white snake tale, the umbrella is always older than its owner, and the rain always more faithful than a promise.')),
        'hua_mulan': dict(
            judul=('Hua Mulan Menggantikan Ayahnya', 'Hua Mulan Takes Her Father\'s Place'),
            sumber=('Sumber: Balada Mulan (Mulan Shi)', 'Source: the Ballad of Mulan (Mulan Shi)'),
            tokoh=[('Hua Mulan, sang prajurit', 'Hua Mulan, the soldier'), ('Ayah Hua Hu', 'Father Hua Hu'),
                   ('Kuda Perang Mulan', 'Mulan\'s war horse'), ('Jenderal Pasukan', 'The army general'),
                   ('Pemimpin Musuh', 'The enemy chief'), ('Mulan di Alat Tenun', 'Mulan at the loom')],
            pembuka=('Lampu menyala. Bunyi alat tenun terdengar dari balik layar: tak-tik-tuk, tak-tik-tuk, lalu berhenti.',
                     'The lamp is lit. The sound of a loom comes from behind the screen: clack-click-clack, clack-click-clack, then stops.'),
            babak=[(('Di alat tenun, Mulan mendengar panggilan wajib militer. Dua belas gulungan, dan nama ayahnya ada di setiap gulungan.',
                     'At the loom, Mulan hears the conscription call. Twelve scrolls, and her father\'s name is on every one.'),
                    ('Ayah sudah tua. Aku akan membeli kuda dan pelana.', 'Father is old. I shall buy a horse and saddle.'),
                    ('Anakku, jangan...', 'My child, do not...')),
                   (('Mulan menunggang keluar dari kampung. Sungai Kuning yang bergemuruh jauh terdengar, bukan suara ibunya.',
                     'Mulan rides out of the village. The distant roar of the Yellow River is heard, not her mother\'s voice.'),
                    ('Pagi aku pamit pada ayah dan ibu, malam aku bermalam di tepi Sungai Kuning.', 'At dawn I bid my parents farewell, at night I camp on the Yellow River bank.'),
                    ('Hhhrrr.', 'Hhhrrr.')),
                   (('Di medan perang, Mulan berhadapan dengan pemimpin musuh. Sepuluh tahun pertempuran, dan tak seorang pun tahu ia seorang perempuan.',
                     'On the battlefield, Mulan faces the enemy chief. Ten years of war, and no one knows she is a woman.'),
                    ('Aku datang bukan untuk pangkat. Aku datang untuk ayahku.', 'I did not come for rank. I came for my father.'),
                    ('Prajurit biasa ini bertarung seperti jenderal!', 'This common soldier fights like a general!')),
                   (('Sang jenderal menawarkan jabatan tinggi. Mulan membungkuk, menolak, dan hanya meminta seekor unta untuk pulang.',
                     'The general offers high office. Mulan bows, declines, and asks only for a camel to ride home.'),
                    ('Mohon izinkan hamba pulang. Ayah dan ibu menunggu.', 'Permit me to go home. My parents are waiting.'),
                    ('Pergilah, prajuritku. Kau telah melakukan lebih dari yang kuminta.', 'Go, my soldier. You have done more than I asked.'))],
            penutup=('Gong menyambut Mulan pulang. Di balik layar, alat tenun itu kembali berbunyi, kali ini pelan dan bahagia.',
                     'The gong greets Mulan\'s return. Behind the screen, the loom sounds again, this time soft and happy.'),
            lore=('Dalang Su berbisik: dalam balada itu, saat teman seperjalanannya menyadari siapa dia, mereka terperangah, karena kelinci jantan dan betina sama-sama sulit dibedakan saat berlari.',
                  'Puppeteer Su whispers: in the ballad, when her comrades learned who she was they stood amazed, for male and female hares are equally hard to tell apart when they run.')),
        'hakim_bao': dict(
            judul=('Hakim Bao dan Qin Xianglian', 'Judge Bao and Qin Xianglian'),
            sumber=('Sumber: kisah Hakim Bao (Bao Gong An)', 'Source: the Judge Bao tales (Bao Gong An)'),
            tokoh=[('Hakim Bao yang Adil', 'Judge Bao the Just'), ('Zhan Zhao, si Kucing Selatan', 'Zhan Zhao, the Southern Cat'),
                   ('Chen Shimei, sang juara', 'Chen Shimei, the top scholar'), ('Qin Xianglian, sang istri', 'Qin Xianglian, the abandoned wife'),
                   ('Pejabat Pendamping', 'The attending official'), ('Anak Qin Xianglian', 'Qin Xianglian\'s child')],
            pembuka=('Dalang Su mengetuk tongkat tiga kali. Sebuah palu keadilan, hitam seperti wajah hakim, naik ke layar.',
                     'Puppeteer Su taps his rod three times. A gavel of justice, black as the judge\'s face, rises on the screen.'),
            babak=[(('Qin Xianglian berlutut di depan meja hakim dengan dua anak kecil di sisinya. Suaminya, sang juara ujian, tidak mengakuinya lagi.',
                     'Qin Xianglian kneels before the judge\'s desk with two small children beside her. Her husband, the top scholar, no longer acknowledges her.'),
                    ('Hamba mohon keadilan, Tuan Hakim.', 'I beg for justice, my lord judge.'),
                    ('Angkat wajahmu, dan ceritakan segalanya.', 'Raise your face, and tell me everything.')),
                   (('Zhan Zhao mengejar Chen Shimei melewati atap-atap kota. Bayangannya cepat dan tak bersuara, seperti kucing di dinding.',
                     'Zhan Zhao pursues Chen Shimei over the town\'s rooftops. His shadow is quick and soundless, like a cat on a wall.'),
                    ('Berhenti, Tuan Chen! Hakim Bao memanggilmu.', 'Halt, Master Chen! Judge Bao summons you.'),
                    ('Biarkan aku lewat!', 'Let me pass!')),
                   (('Di ruang sidang, Hakim Bao berhadapan dengan sang juara. "Kau punya istri di desa, kau punya anak." Chen Shimei menunduk.',
                     'In the courtroom, Judge Bao faces the top scholar. "You have a wife in the village, you have children." Chen Shimei lowers his head.'),
                    ('Seorang pejabat yang mengkhianati keluarganya tidak pantas memakai seragam kerajaan.', 'An official who betrays his family is unfit to wear the imperial robe.'),
                    ('Hamba... hamba tak menyangka...', 'I... I never imagined...')),
                   (('Putusan dibacakan. Dua pejabat pendamping membungkuk dalam-dalam, dan palu hitam itu turun satu kali. Seluruh ruang sidang sunyi.',
                     'The verdict is read. The two attending officials bow deeply, and the black gavel falls once. The whole courtroom is silent.'),
                    ('Perintah ditegakkan, atas nama keadilan.', 'The order is upheld, in the name of justice.'),
                    ('Dengan hormat, Tuan Hakim.', 'With respect, my lord judge.'))],
            penutup=('Palu terakhir berbunyi, layar padam, dan beberapa penonton meninggalkan teater sambil menggumamkan hukum.',
                     'The final gavel sounds, the screen darkens, and some of the audience leave the theatre murmuring the law.'),
            lore=('Kata Dalang Su: wajah hitam Hakim Bao bukan karena arang, melainkan karena ia tak pernah mau memutihkan perkara.',
                  'Puppeteer Su says: Judge Bao\'s black face is not from charcoal, but because he never whitewashed a case.')),
    }
    out = {}
    for i, nid in enumerate(NASKAH_ID):
        s = S[nid]
        base = f'kultivator.kota42.wayang.{nid}'
        if len(s['tokoh']) != 6 or len(s['babak']) != 4:
            ERRORS.append(f'wayang {nid}: needs six figures and four acts')
        o = {'id': nid, 'indeks': i, 'judul': t(f'{base}.judul', s['judul']), 'sumber': t(f'{base}.sumber', s['sumber']),
             'durasi_tick': 1200, 'babak_tick': 300, 'pembuka': t(f'{base}.pembuka', s['pembuka']),
             'tokoh': [{'sel': c, 'nama': t(f'{base}.tokoh.{c}', nm)} for c, nm in enumerate(s['tokoh'])], 'babak': []}
        for k, (narasi, kata_a, kata_b) in enumerate(s['babak']):
            a, b, mode = babak[i][k]
            o['babak'].append({'mulai_tick': k * 300, 'a': a, 'b': b, 'mode': mode, 'narasi': t(f'{base}.babak.{k}.narasi', narasi),
                               'kata_a': t(f'{base}.babak.{k}.a', kata_a), 'kata_b': t(f'{base}.babak.{k}.b', kata_b),
                               'bunyi': {'sapa': 'genderang', 'tarung': 'gong', 'cakap': 'erhu', 'jalan': 'erhu', 'kejar': 'genderang'}[mode]})
        o['penutup'] = t(f'{base}.penutup', s['penutup'])
        o['lore'] = t(f'{base}.lore', s['lore'])
        o['tiket_nb'] = 2
        out[nid] = o
    return out


def isi_jalan():
    return {}   # shortcut: TanyaJalan takes every direction word from its own lang keys and reads no data file; nothing to add here


def isi_pesan():
    """Item names of the story items (42R0 named them as 42B-C's)."""
    for iid, nm in (('kentongan_tua', ('Kentongan Tua', 'Old Watch Drum')), ('kwitansi_sumur', ('Kwitansi Sumur', 'Well Receipt')),
                    ('surat_utang', ('Surat Utang', 'IOU')),
                    ('wayang_kulit_kera_sakti', ('Wayang Kera Sakti', 'Monkey King Puppet')),
                    ('wayang_kulit_ular_putih', ('Wayang Ular Putih', 'White Snake Puppet')),
                    ('wayang_kulit_hua_mulan', ('Wayang Hua Mulan', 'Hua Mulan Puppet')),
                    ('wayang_kulit_hakim_bao', ('Wayang Hakim Bao', 'Judge Bao Puppet')),
                    ('resep_sup', ('Resep Sup Nenek Tang', 'Granny Tang\'s Soup Recipe'))):
        t(f'item.kultivator.{iid}', nm)
    # the place names a quest shows as its home (38 Quest.tempat -> kultivator.cerita.tempat.<tempat>)
    t('kultivator.cerita.tempat.kota42', ('Kota yang Hidup', 'The Living Town'))
    t('kultivator.cerita.tempat.kota42_kota34', ('Kota Perbatasan', 'The Border Town'))
    return {}


# ================================================================================================ checks and writers
def check_lang():
    for key, (idn, en) in L.items():
        a = len(re.findall(r'%(?:\d+\$)?s', idn))
        b = len(re.findall(r'%(?:\d+\$)?s', en))
        if a != b:
            ERRORS.append(f'{key}: %s count id {a} en {b}')
        for txt in (idn, en):
            if not txt.strip():
                ERRORS.append(f'{key}: empty text')
            if re.search(r'%(?![\ds%])', txt.replace('%%', '').replace('%s', '')):
                ERRORS.append(f'{key}: stray % in "{txt[:40]}"')
    for fn in sorted(os.listdir(LANG)):
        if not fn.endswith('.id_id.json') or fn.split('.')[0] == FRAG:
            continue
        other = json.load(open(os.path.join(LANG, fn), encoding='utf-8'))
        dup = set(L) & set(other)
        if dup:
            ERRORS.append(f'{fn} already has {sorted(dup)[:5]}')


def build_tokoh():
    for tid, d in TOKOH.items():
        for k in d['keluarga']:
            if k['tokoh'] not in TOKOH and k['tokoh'] not in TOKOH38:
                ERRORS.append(f'character {tid}: family link to unknown {k["tokoh"]}')
    return {'tokoh': [dict(d, nama=f'kultivator.cerita.tokoh.{tid}', gelar=f'kultivator.cerita.tokoh.{tid}.gelar',
                           riwayat=f'kultivator.kota42.tokoh.{tid}.riwayat',
                           dialog=sorted(did for did, dd in DIALOGS.items() if f'tokoh:{tid}' in dd['pembicara']),
                           alur=sorted(qid for qid, q in QUESTS.items() if q['pemberi'] == f'tokoh:{tid}'))
                      for tid, d in TOKOH.items()]}


def main():
    isi_tokoh()
    kisah = isi_kisah()
    for name in ('alur_dua_keluarga', 'alur_lao_geng', 'alur_utang', 'alur_seribu_jari', 'alur_akar_langit', 'alur_kota_tumbuh',
                 'alur_sup_hangat', 'alur_pelindung', 'alur_wayang', 'alur_pejabat', 'alur_samping', 'alur_jabatan', 'isi_sapa', 'isi_layanan', 'isi_duka', 'isi_kabar'):
        globals()[name]()
    outputs = {}
    for name in ('isi_tolong', 'isi_selera', 'isi_perkara', 'isi_wayang', 'isi_jalan', 'isi_pesan'):
        outputs[name] = globals()[name]()
    dialogs = build_dialogs()
    quests = build_quests()
    kabar = build_kabar()
    tokoh_json = build_tokoh()
    for tid, d in TOKOH.items():
        if not d['hewan'] and not any(f'tokoh:{tid}' in dd['pembicara'] for dd in DIALOGS.values()):
            ERRORS.append(f'character {tid} has no dialog')
    check_lang()
    if ERRORS:
        for e in ERRORS:
            print('ERROR', e)
        print(len(ERRORS), 'errors; nothing written')
        sys.exit(1)
    write_json(os.path.join(DATA, 'tokoh', 'kota42.json'), tokoh_json)
    for did, obj in dialogs.items():
        write_json(os.path.join(DATA, 'dialog', did + '.json'), obj)
    for qid, obj in quests.items():
        write_json(os.path.join(DATA, 'quest', qid + '.json'), obj)
    write_json(os.path.join(DATA, 'kabar', 'kota42.json'), {'templat': kabar})
    write_json(os.path.join(DATA, 'bicara', 'kisah_lao_shuo.json'), {'pendongeng': 'lao_shuo', 'kisah': kisah})
    for jenis, obj in outputs['isi_tolong'].items():
        write_json(os.path.join(DATA, 'tolong', jenis + '.json'), obj)
    write_json(os.path.join(DATA, 'selera.json'), outputs['isi_selera'])
    for pid, obj in outputs['isi_perkara'].items():
        write_json(os.path.join(DATA, 'perkara', pid + '.json'), obj)
    for nid, obj in outputs['isi_wayang'].items():
        write_json(os.path.join(DATA, 'wayang', nid + '.json'), obj)
    ids = {k: v[0] for k, v in sorted(L.items())}
    ens = {k: v[1] for k, v in sorted(L.items())}
    write_json(os.path.join(LANG, f'{FRAG}.id_id.json'), ids)
    write_json(os.path.join(LANG, f'{FRAG}.en_us.json'), ens)
    print(f'tokoh {len(TOKOH)}, dialogs {len(dialogs)}, quests {len(quests)}, rumours {len(kabar)}, tales {len(kisah)}, '
          f'tolong {len(outputs["isi_tolong"])}, perkara {len(outputs["isi_perkara"])}, wayang {len(outputs["isi_wayang"])}, '
          f'lang keys {len(L)}')


if __name__ == '__main__':
    main()

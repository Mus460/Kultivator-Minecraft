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
TUJUAN_42 = {'bicara_warga', 'gosip_telusur', 'hadir_upacara', 'bayar_utang', 'perkara', 'kavling_tahap', 'kejar_copet', 'ronda_geng'}
# 42B registers these effects (Akibat.daftar) for the arcs; documented in m2-42B-C.md
AKIBAT_42 = {'kota42.kesan', 'kota42.gosip', 'kota42.kebiasaan', 'kota42.nikah', 'kota42.pindah', 'kota42.calon', 'kota42.saham',
             'kota42.nama_jalan', 'kota42.harga', 'kota42.resep', 'kota42.pensiun', 'kota42.warisi', 'kota42.pakta',
             'kota42.jabatan'}
AKIBAT |= AKIBAT_42 | {'infami'}   # infami N [sebab]: TemaCerita (38.T)
SYARAT |= {'infami'}

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
    gestur/prop/suara/akibat/isian/syarat (choice conditions)/alasan/lanjut [(choice, reply, gestur)] for a follow-up chain.
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
                       akibat=opts.get('akibat', ()), isian=opts.get('isian', ()), pilihan=nxt))
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
                 'alur_sup_hangat', 'alur_pelindung', 'alur_wayang', 'alur_pejabat', 'alur_samping', 'isi_sapa', 'isi_kabar'):
        globals()[name]()
    outputs = {}
    for name in ('isi_tolong', 'isi_selera', 'isi_perkara', 'isi_wayang', 'isi_jalan', 'isi_pesan'):
        outputs[name] = globals()[name]()
    dialogs = build_dialogs()
    quests = build_quests()
    kabar = build_kabar()
    tokoh_json = build_tokoh()
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
    write_json(os.path.join(DATA, 'bicara', 'jalan.json'), outputs['isi_jalan'])
    ids = {k: v[0] for k, v in sorted(L.items())}
    ens = {k: v[1] for k, v in sorted(L.items())}
    write_json(os.path.join(LANG, f'{FRAG}.id_id.json'), ids)
    write_json(os.path.join(LANG, f'{FRAG}.en_us.json'), ens)
    print(f'tokoh {len(TOKOH)}, dialogs {len(dialogs)}, quests {len(quests)}, rumours {len(kabar)}, tales {len(kisah)}, '
          f'tolong {len(outputs["isi_tolong"])}, perkara {len(outputs["isi_perkara"])}, wayang {len(outputs["isi_wayang"])}, '
          f'lang keys {len(L)}')


if __name__ == '__main__':
    main()

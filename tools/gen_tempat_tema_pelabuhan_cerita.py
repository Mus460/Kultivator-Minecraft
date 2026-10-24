#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DESIGN 38.T.3 (module 38TK-P): the story data and texts of the Kota Pelabuhan, from one table so the id_id and en_us
keys never drift.

Writes (all owned by 38TK-P):
  data/kultivator/tempat/dialog/tema_pelabuhan_*.json        dialogs of the harbour characters
  data/kultivator/tempat/quest/tema_pelabuhan*.json          "Pelita yang Padam" + 4 side quests
  data/kultivator/tempat/kabar/tema_pelabuhan.json           rumours (code-spread and background)
  data/kultivator/tempat/{jadwal,permintaan}/kota_tema_pelabuhan.json
  data/kultivator/loot_table/tema/pelabuhan/bajak_laut.json  spoils of the pirate junk
  data/kultivator/advancement/tempat/tema_pelabuhan_{lunas,lomba,bajak}.json
  lang-src/tempat_tema_pelabuhan.{id_id,en_us}.json          UI lines of tema/pelabuhan/*.java and every story text
Validates clip / prop names against GerakId / PropId, node and stage targets, %s parity and duplicates against the
other lang fragments. Run: python tools/gen_tempat_tema_pelabuhan_cerita.py
"""
import json
import os
import re
import sys


# ========================================================================================== UI strings
P = "kultivator.kota_tema.pelabuhan."

UI = [
    # ---------------------------------------------------------------- shared
    ("kurang_uang", "Nilai Batumu tidak cukup.", "You do not carry enough Nilai Batu."),
    ("pita", "Butuh pita ranah %s ke atas.", "Needs realm band %s or higher."),
    ("habis", "Barang itu sudah habis untuk hari ini.", "That item is sold out for today."),
    ("dibeli", "Dibeli %1$s × %2$s seharga %3$s.", "Bought %1$s × %2$s for %3$s."),
    ("nama_uji", "Pelabuhan Uji", "Test Harbour"),
    ("rute", "Pelabuhan di Pesisir", "A Harbour on the Coast"),
    ("uji.bukan", "Kau tidak berdiri di dalam Kota Pelabuhan.", "You are not standing inside a Harbour City."),
    # ---------------------------------------------------------------- sheets
    ("layar.kapal", "Muatan Kapal Dagang", "Merchant Ship's Cargo"),
    ("layar.lintas", "Loket Kapal Lintas Laut", "Boundless Sea Crossing Office"),
    ("layar.ikan", "Pasar Ikan Pagi", "Morning Fish Market"),
    ("layar.seberang", "Pasar Seberang", "Overseas Market"),
    ("layar.bahan_bakar", "Loket Bahan Bakar Menara Tambat", "Mooring Tower Fuel Desk"),
    ("layar.perahu", "Sewa Perahu Nelayan", "Fishing Boat Hire"),
    ("layar.dokumen", "Catatan Bea Kantor Urusan Laut", "Maritime Office Customs Records"),
    ("layar.galangan", "Galangan Mbah Lunas", "Granny Keel's Shipyard"),
    ("layar.racik", "Tungku Kaca Galangan", "The Shipyard Glass Kiln"),
    ("jasa.kapal", "Lihat muatan kapal", "See the ship's cargo"),
    ("jasa.lintas", "Beli tiket Kapal Lintas Laut", "Buy a Boundless Sea crossing ticket"),
    ("jasa.ikan", "Belanja ikan pagi", "Buy the morning catch"),
    ("jasa.seberang", "Lihat barang seberang", "Browse the overseas goods"),
    ("jasa.bahan_bakar", "Isi bahan bakar kapal terbang", "Refuel a flying ship"),
    ("jasa.perahu", "Sewa perahu nelayan", "Hire a fishing boat"),
    ("jasa.galangan", "Pesan lunas di galangan", "Order a keel at the shipyard"),
    # ---------------------------------------------------------------- ships
    ("kapal.belum", "Belum ada kapal di tambatan ini. Lihat jadwal kedatangan.",
     "No ship lies at this berth yet. See the arrival schedule."),
    ("kapal.cari_gao", "Kapten Gao Fan menjual tiket di Pos Kapal Lintas Laut.",
     "Captain Gao Fan sells tickets at the Boundless Sea Crossing post."),
    ("kapal.cari_nakhoda", "Bicaralah dengan nakhoda di geladak untuk membeli muatan.",
     "Talk to the captain on the deck to buy the cargo."),
    ("asal.selatan", "Negeri Rempah Selatan", "the Southern Spice Lands"),
    ("asal.mutiara", "Kepulauan Mutiara", "the Pearl Isles"),
    ("asal.barat", "Negeri Barat", "the Western Lands"),
    ("asal.kota", "kota pelabuhan lain di pesisir", "another harbour city down the coast"),
    ("kapal.nama.0", "Jung Angin Pertama", "First Wind Junk"),
    ("kapal.nama.1", "Bunga Teratai Laut", "Sea Lotus"),
    ("kapal.nama.2", "Burung Camar Emas", "Golden Gull"),
    ("kapal.nama.3", "Naga Biru Tidur", "Sleeping Blue Dragon"),
    ("kapal.nama.4", "Bulan di Atas Karang", "Moon over the Reef"),
    ("kapal.nama.5", "Seribu Li Tenang", "Calm Thousand Li"),
    ("kapal.nama.6", "Mutiara Selatan", "Southern Pearl"),
    ("kapal.nama.7", "Kura-kura Giok", "Jade Turtle"),
    ("kapal.nama.8", "Layar Merah Fajar", "Red Sail of Dawn"),
    ("kapal.nama.9", "Ombak Rempah", "Spice Wave"),
    ("kapal.nama.10", "Bintang Penunjuk", "Guiding Star"),
    ("kapal.nama.11", "Restu Dewi Laut", "Sea Goddess's Blessing"),
    ("kompas.sudah", "Kau sudah membawa Kompas Pasang.", "You already carry a Tide Compass."),
    ("seberang.tutup", "Pasar Seberang tutup sehari: para pedagang masih membersihkan bekas serbuan bajak laut.",
     "The Overseas Market is shut for a day: the merchants are still clearing up after the pirate raid."),
    ("ikan.tutup", "Pasar Ikan Pagi sudah tutup.", "The Morning Fish Market has closed."),
    ("ikan.jam", "Buka pukul 05.00 sampai 09.00, saat perahu pulang.", "Open from 05:00 to 09:00, when the boats come home."),
    # ---------------------------------------------------------------- crossing
    ("lintas.tutup", "Kapal Lintas Laut tidak berlayar dalam badai. Kapten Gao menunggu laut tenang.",
     "The crossing ship does not sail in a storm. Captain Gao waits for calm water."),
    ("lintas.buronan", "Kantor Urusan Laut tidak menjual tiket kepada buronan.",
     "The Maritime Office sells no tickets to wanted people."),
    ("lintas.sudah", "Kapal sudah berangkat pukul %s. Datanglah lagi besok.",
     "The ship left at %s. Come again tomorrow."),
    ("lintas.sekali", "Kau sudah menyeberang hari ini. Kapal hanya berlayar sekali sehari.",
     "You already crossed today. The ship sails only once a day."),
    ("lintas.tiket", "Tiket dibeli: berangkat hari ini pukul %1$s (%2$s). Tunggulah di dermaga.",
     "Ticket bought: sailing today at %1$s (%2$s). Wait on the quay."),
    ("lintas.tiket_besok", "Tiket dibeli untuk besok pukul %1$s (%2$s).", "Ticket bought for tomorrow at %1$s (%2$s)."),
    ("lintas.gratis", "Kakek Deng sudah bicara dengan kapten: pelayaran ini gratis untukmu.",
     "Grandpa Deng has spoken with the captain: this voyage is free for you."),
    ("lintas.judul", "Menuju Laut Tanpa Batas", "Toward the Boundless Sea"),
    ("lintas.sub", "Kabut menelan haluan; pelita mercusuar mengecil di buritan.",
     "Fog swallows the bow; the lighthouse lamp shrinks astern."),
    ("lintas.gagal", "Kabut menebal dan kapten memutar haluan. Tiketmu berlaku besok.",
     "The fog thickens and the captain turns back. Your ticket holds for tomorrow."),
    ("lintas.murid_tinggal", "Muridmu menunggu di dermaga sampai kau pulang dari laut.",
     "Your disciple waits on the quay until you return from the sea."),
    # ---------------------------------------------------------------- shipyard
    ("galangan.tutup", "Galangan sedang tutup.", "The shipyard is closed."),
    ("galangan.pita", "Mbah Lunas hanya membangun kapal ini untuk pemilik pita ranah %s ke atas.",
     "Granny Keel builds this ship only for owners of realm band %s or higher."),
    ("galangan.sudah", "Pesananmu masih di landasan. Satu kapal dulu, kata Mbah Lunas.",
     "Your order is still on the slipway. One ship at a time, says Granny Keel."),
    ("galangan.dipesan", "Lunas dipesan. Lihat landasan: kapalmu jadi dalam %s hari.",
     "Keel ordered. Watch the slipway: your ship is done in %s days."),
    ("galangan.belum", "Belum jadi. Masih %s hari lagi di landasan.", "Not done yet. %s more days on the slipway."),
    ("galangan.diambil", "Lunas diserahkan. Mbah Lunas menepuk lambungnya sekali, untuk keberuntungan.",
     "The keel is handed over. Granny Keel slaps the hull once, for luck."),
    ("galangan.tidak_ada", "Tidak ada pesanan atas namamu di galangan ini.", "There is no order in your name at this shipyard."),
    ("galangan.gaya_terkunci", "Gaya lambung itu belum bisa dicat di sini.", "That hull style cannot be painted here yet."),
    ("galangan.pegang_lunas", "Pegang lunas kapalmu di tangan agar bisa dicat ulang.", "Hold your ship's keel in hand to repaint it."),
    ("galangan.gaya_baru", "Lambung dicat ulang: %s.", "Hull repainted: %s."),
    ("galangan.kelas.perahu", "Perahu Awan", "Cloud Boat"),
    ("galangan.kelas.sekte", "Kapal Sekte", "Sect Ship"),
    # ---------------------------------------------------------------- boats
    ("perahu.ok", "Perahu disewa untuk %s jam. Jangan jauh dari teluk.", "Boat hired for %s hours. Keep to the bay."),
    ("perahu.lomba_ok", "Perahu lomba disewa untuk %s jam. Tunggu genderang di garis mulai.",
     "Race boat hired for %s hours. Wait for the drum at the start line."),
    ("perahu.sudah", "Kau sudah menyewa sebuah perahu.", "You already hired a boat."),
    ("perahu.lomba_tutup", "Pendaftaran lomba perahu sudah ditutup.", "Entries for the boat race have closed."),
    ("perahu.tidak_ada", "Tidak ada perahu kosong di tambatan sekarang.", "No free boat at the moorings right now."),
    ("perahu.penuh", "Semua perahu sedang disewa.", "Every boat is hired out."),
    ("perahu.bukan", "Ini bukan perahu sewaanmu.", "This is not your hired boat."),
    ("perahu.jauh", "Terlalu jauh dari teluk! Perahu akan ditarik pulang bila kau terus mendayung.",
     "Too far from the bay! The boat will be towed home if you keep rowing."),
    ("perahu.habis", "Waktu sewa habis. Perahu kembali ke tambatan.", "The hire is over. The boat goes back to its mooring."),
    ("perahu.ditarik", "Para nelayan menarik perahumu kembali ke teluk.", "The fishers tow your boat back into the bay."),
    ("perahu.ditinggal", "Perahu yang ditinggal dikembalikan ke tambatan.", "The abandoned boat is returned to its mooring."),
    ("perahu.ikan_roh", "Sisik biru berkilau di kailmu: Ikan Roh!", "Blue scales flash on your line: a Spirit Fish!"),
    # ---------------------------------------------------------------- festival and race
    ("dewi.judul", "Hari Dewi Laut", "Sea Goddess Day"),
    ("dewi.sub", "Tandu sang Dewi turun ke dermaga; genderang memanggil perahu.",
     "The Goddess's palanquin comes down to the quay; drums call the boats."),
    ("lomba.daftar", "Kau terdaftar dalam Lomba Perahu Nelayan. Dua putaran di teluk!",
     "You are entered in the Fishing Boat Race. Two laps of the bay!"),
    ("lomba.sibuk", "Selesaikan dulu urusanmu yang lain.", "Finish your other business first."),
    ("lomba.tak_ada_jalur", "Jalur lomba di teluk ini belum dipasangi pelampung.", "No race buoys are laid in this bay yet."),
    ("lomba.latihan", "A-Lan mendayung ke sampingmu: \"Satu putaran latihan. Yang kalah traktir teh!\"",
     "A-Lan rows up beside you: \"One practice lap. Loser buys the tea!\""),
    ("lomba.mulai", "Dayung!", "Row!"),
    ("lomba.juara", "Juara Lomba Perahu!", "Boat Race Champion!"),
    ("lomba.tiba", "Kau tiba di urutan ke-%s.", "You finish in place %s."),
    # ---------------------------------------------------------------- pirates
    ("bajak.firasat", "Lampu-lampu jung berlayar hitam tampak di cakrawala malam ini...",
     "The lights of a black-sailed junk show on the horizon tonight..."),
    ("bajak.judul", "Serbuan Bajak Laut", "Pirate Raid"),
    ("bajak.sub", "Layar hitam menutup bintang; papan titian berdentum di Dermaga Agung.",
     "Black sails blot out the stars; boarding planks crash onto the Grand Quay."),
    ("bajak.gelombang", "Gelombang bajak laut %1$s / %2$s", "Pirate wave %1$s / %2$s"),
    ("bajak.deng_kenal", "Kakek Deng berbisik dari menara: \"Hei Sha... itu kau, Nak?\"",
     "Grandpa Deng whispers from the tower: \"Hei Sha... is that you, boy?\""),
    ("bajak.menang", "Dermaga Bertahan!", "The Quays Hold!"),
    ("bajak.menang_sub", "Nama Baik +%1$s · %2$s Nilai Batu dari Kas Kota", "Good Name +%1$s · %2$s Nilai Batu from the Town Chest"),
    ("bajak.kalah", "Bajak laut kabur dengan rampasannya. Pasar Seberang tutup sehari.",
     "The pirates got away with their loot. The Overseas Market shuts for a day."),
    # ---------------------------------------------------------------- story bits
    ("heisha.menyerah", "Hei Sha jatuh berlutut dan menancapkan goloknya ke papan: \"Cukup.\"",
     "Hei Sha drops to one knee and drives his dao into the planks: \"Enough.\""),
    ("mutiara.ular", "Dari perut ular laut bergulir sebutir mutiara yang bercahaya seperti fajar.",
     "From the sea serpent's belly rolls a pearl that glows like the dawn."),
    ("mutiara.dibeli", "Pedagang pulau membungkus Mutiara Fajar dengan daun lontar.",
     "The island merchant wraps the Dawn Pearl in palm leaves."),
    ("mutiara.sudah", "Kau sudah membawa Mutiara Fajar.", "You already carry a Dawn Pearl."),
    ("kaca.dibeli", "Ibrahim menyerahkan Kaca Garam Laut, terbungkus kain wol.",
     "Ibrahim hands over the Sea-Salt Glass, wrapped in wool cloth."),
    ("bintang.mulai", "Ibrahim membuka astrolabnya. Diamlah dan pandang bintang Penunjuk...",
     "Ibrahim opens his astrolabe. Be still and watch the Guiding Star..."),
    ("bintang.selesai", "Malam pengamatan selesai (%s). Bintang-bintang tercatat di buku Ibrahim.",
     "The night's watch is done (%s). The stars are written in Ibrahim's book."),
    ("hadiah.dapat", "Kau menerima %s.", "You receive %s."),
    ("hadiah.gigi_hiu", "Gigi Hiu", "Shark Tooth"),
    ("hadiah.gigi_hiu.lore", "Golok Kapten Hiu Hitam, ditempa dari baja kapal karam.",
     "Captain Black Shark's dao, forged from wrecked-ship steel."),
    # ---------------------------------------------------------------- tema_dokumen
    ("dokumen.judul", "Surat Muatan · %s", "Cargo Manifest · %s"),
    ("dokumen.label.kapal", "Kapal", "Ship"),
    ("dokumen.label.muatan", "Muatan", "Cargo"),
    ("dokumen.label.berat", "Berat", "Weight"),
    ("dokumen.label.segel", "Segel bea", "Customs seal"),
    ("dokumen.label.tanggal", "Tanggal", "Date"),
    ("dokumen.muatan.0", "Rempah Selatan, 40 karung", "Southern spice, 40 sacks"),
    ("dokumen.muatan.1", "Porselen hijau, 12 peti jerami", "Green porcelain, 12 straw crates"),
    ("dokumen.muatan.2", "Garam laut, 30 tong", "Sea salt, 30 casks"),
    ("dokumen.muatan.3", "Kaca warna, 8 peti", "Coloured glass, 8 crates"),
    ("dokumen.muatan.4", "Kain katun, 25 gulung", "Cotton cloth, 25 bolts"),
    ("dokumen.muatan.5", "Ikan asin, 50 keranjang", "Salted fish, 50 baskets"),
    ("dokumen.muatan.lampu", "Minyak lampu biasa, 20 tong", "Common lamp oil, 20 casks"),
    ("dokumen.dan", "%s pikul", "%s piculs"),
    ("dokumen.hari", "Hari ke-%s bulan ini", "Day %s of this month"),
    ("dokumen.ruang.0", "palka depan", "fore hold"),
    ("dokumen.ruang.1", "palka tengah", "main hold"),
    ("dokumen.ruang.2", "palka buritan", "aft hold"),
    ("dokumen.catatan_isi", "Segel No. %1$s · %2$s · timbangan dermaga %3$s pikul (termasuk peti 20 pikul)",
     "Seal No. %1$s · %2$s · quay scale %3$s piculs (crates 20 piculs included)"),
    ("dokumen.benar", "Tepat. Segel dan beratnya bohong: \"minyak lampu\" itu Minyak Bayangan.",
     "Right. The seal and the weight lie: that \"lamp oil\" is Shadow Oil."),
    ("dokumen.salah", "Bukan yang itu. Sisa percobaan: %s.", "Not that one. Tries left: %s."),
    ("dokumen.ditunjukkan", "Lin Haichao menghela napas dan menunjuk surat yang palsu: segelnya tak cocok dengan catatan.",
     "Lin Haichao sighs and points at the forged manifest: its seal does not match the record."),
    # ---------------------------------------------------------------- tema_racik
    ("racik.bara_merah", "Bara merah: panas tungku biasa", "Red embers: an ordinary kiln heat"),
    ("racik.bara_putih", "Bara putih: tiup puputan sampai menyilaukan", "White embers: work the bellows until it dazzles"),
    ("racik.bara_biru", "Bara biru: api roh yang tenang", "Blue embers: a calm spirit flame"),
    ("racik.tumbuk_kasar", "Tumbuk pasir kasar-kasar", "Pound the sand coarse"),
    ("racik.tumbuk_halus", "Tumbuk pasir sehalus tepung", "Pound the sand fine as flour"),
    ("racik.sisik_ikan", "Taburkan sisik Ikan Roh ke kaca cair", "Sprinkle Spirit Fish scales into the molten glass"),
    ("racik.poles_pasir", "Poles dengan pasir basah", "Polish with wet sand"),
    ("racik.poles_sutra", "Poles dengan sutra dan air garam", "Polish with silk and brine"),
    ("racik.poles_minyak", "Poles dengan minyak ikan", "Polish with fish oil"),
    ("racik.petunjuk.1", "Mbah Lunas: \"Garam laut cuma mau leleh di api yang putih. Jangan pelit puputan!\"",
     "Granny Keel: \"Sea salt only melts in a white fire. Don't be stingy with the bellows!\""),
    ("racik.petunjuk.2", "Mbah Lunas: \"Kaca biasa retak kena dingin malam. Roh ikan yang membuatnya lentur.\"",
     "Granny Keel: \"Common glass cracks in the night cold. A fish's spirit makes it supple.\""),
    ("racik.petunjuk.3", "Mbah Lunas: \"Lensa itu mata mercusuar. Mata dibersihkan dengan sutra, bukan diamplas!\"",
     "Granny Keel: \"That lens is the lighthouse's eye. You wipe an eye with silk, you don't sand it!\""),
    ("racik.kurang", "Butuh 4 pasir dan 1 Ikan Roh untuk menyalakan tungku kaca.",
     "You need 4 sand and 1 Spirit Fish to light the glass kiln."),
    ("racik.baik", "Kaca berdenting bening. Langkah yang tepat.", "The glass rings clear. The right step."),
    ("racik.buruk", "Kaca berderak keruh... Mbah Lunas mengernyit.", "The glass creaks cloudy... Granny Keel frowns."),
    ("racik.gagal", "Kacanya retak di tungku. Coba lagi dengan bahan baru.", "The glass cracks in the kiln. Try again with fresh materials."),
    ("racik.jadi", "Kaca Garam Laut jadi: %s keping.", "Sea-Salt Glass made: %s pieces."),
    # ---------------------------------------------------------------- boat names (tema_pelabuhan_lunas_pertama)
    ("nama_perahu.camar_putih", "Camar Putih", "White Gull"),
    ("nama_perahu.angin_selatan", "Angin Selatan", "South Wind"),
    ("nama_perahu.pelita_kecil", "Pelita Kecil", "Little Lamp"),
    ("nama_perahu.ombak_giok", "Ombak Giok", "Jade Wave"),
    ("nama_perahu.bintang_fajar", "Bintang Fajar", "Dawn Star"),
    ("nama_perahu.kura_kura_tua", "Kura-kura Tua", "Old Turtle"),
]

TOKOH = [
    # id, name id, name en, title id, title en
    ("lin_haichao", "Lin Haichao", "Lin Haichao", "Syahbandar", "Harbourmaster"),
    ("zhao_wanli", "Zhao Wanli", "Zhao Wanli", "Kepala Dewan Saudagar", "Head of the Merchants' Council"),
    ("deng_ming", "Kakek Deng Ming", "Grandpa Deng Ming", "Penjaga Suar", "Lighthouse Keeper"),
    ("a_lan", "A-Lan", "A-Lan", "Awak Kapal", "Deckhand"),
    ("gong_shumu", "Mbah Lunas", "Granny Keel", "Tukang Kapal", "Shipwright"),
    ("ibrahim", "Ibrahim dari Negeri Barat", "Ibrahim of the Western Lands", "Saudagar Seberang", "Overseas Merchant"),
    ("gao_fan", "Kapten Gao Fan", "Captain Gao Fan", "Nakhoda Kapal Lintas Laut", "Master of the Crossing Ship"),
    ("bibi_yu", "Bibi Yu", "Auntie Yu", "Penjual Ikan", "Fishmonger"),
    ("pendeta_laut", "Pendeta Mei", "Priestess Mei", "Penjaga Kuil Dewi Laut", "Keeper of the Sea Goddess Temple"),
    ("hei_sha", "Kapten Hiu Hitam", "Captain Black Shark", "Bajak Laut", "Pirate"),
]


def ui_lang():
    out = []
    for k, i, e in UI:
        out.append((P + k, i, e))
    for t, ni, ne, gi, ge in TOKOH:
        out.append((P + "tokoh." + t, ni, ne))
        out.append((P + "tokoh." + t + ".gelar", gi, ge))
    return out


# ========================================================================================== dialogs
Q = "tema_pelabuhan_pelita"
QI = "tema_pelabuhan_ikan_roh"
QT = "tema_pelabuhan_tali_putus"
QB = "tema_pelabuhan_bintang"
QL = "tema_pelabuhan_lunas_pertama"


def N(nid, teks, gestur="", prop="", akibat=None, isian=None, pilihan=None):
    return {"id": nid, "teks": teks, "gestur": gestur, "prop": prop, "akibat": akibat or [], "isian": isian or [],
            "pilihan": pilihan or []}


def C(teks, ke=None, syarat=None, akibat=None, tampil=False, alasan=None):
    return {"teks": teks, "ke": ke, "syarat": syarat or [], "akibat": akibat or [], "tampil": tampil, "alasan": alasan}


def D(did, pembicara, syarat, prioritas, simpul, sekali=False):
    return {"id": did, "pembicara": pembicara if isinstance(pembicara, list) else [pembicara], "syarat": syarat,
            "prioritas": prioritas, "simpul": simpul, "sekali": sekali}


def tahap(q, s):
    return "quest %s tahap %s" % (q, s)


DENG, LIN, ZHAO, ALAN, GONG = "tokoh:deng_ming", "tokoh:lin_haichao", "tokoh:zhao_wanli", "tokoh:a_lan", "tokoh:gong_shumu"
IBRA, GAO, YU, MEI, HEI = "tokoh:ibrahim", "tokoh:gao_fan", "tokoh:bibi_yu", "tokoh:pendeta_laut", "tokoh:hei_sha"
BYE = ("Sampai nanti.", "Until later.")

DIALOG = [
    # ================================================================ chain bab 1: the dim lamp
    D("tema_pelabuhan_deng_sapa", DENG, ["quest %s baru" % Q, "pelabuhan_di_kota"], 60, [
        N("mulai", ("Eh? Bicara yang keras, anak muda, telinga tua ini sudah separuh milik laut. ...Kau lihat mercusuar itu? "
                    "Pelitaku redup tiap malam. Dulu sinarnya sampai ke Karang Seribu; sekarang kapal-kapal hilang di kabut.",
                    "Eh? Speak up, young one, these old ears belong half to the sea. ...See that lighthouse? My lamp grows dim "
                    "every night. Its beam once reached the Thousand Reefs; now ships vanish in the fog."),
          "tatap_langit", "lentera_tangan", pilihan=[
              C(("Biar kulihat lampunya, Kek.", "Let me look at the lamp, Grandpa."), "terima", akibat=["quest_mulai " + Q]),
              C(("Lampu apa itu sebenarnya?", "What is that lamp, really?"), "cerita"),
              C(("Lain kali, Kek.", "Another time, Grandpa.")),
          ]),
        N("cerita", ("Pelita Penunjuk Angin. Para pendiri kota membawa sebutir mutiara dari Laut Tanpa Batas dan menyalakan api roh "
                     "di dalamnya. Selama ia menyala, tak satu kapal fana pun tersesat dalam kabut. Tiga puluh tahun aku menuang "
                     "minyaknya... dan baru kali ini ia meredup.",
                     "The Wind-Guiding Lamp. The city's founders brought a pearl from the Boundless Sea and lit a spirit fire "
                     "inside it. While it burns, no mortal ship loses its way in the fog. Thirty years I have poured its oil... "
                     "and only now does it fade."),
          "cerita", pilihan=[
              C(("Aku akan memeriksanya.", "I will look into it."), "terima", akibat=["quest_mulai " + Q]),
              C(BYE),
          ]),
        N("terima", ("Naiklah ke ruang lampu di puncak menara dan lihat lensanya dengan matamu sendiri. Lalu temui Syahbandar Lin "
                     "di Kantor Urusan Laut. Dia tak suka orang sekte, tapi dia mendengar.",
                     "Climb to the lamp room at the top of the tower and see the lens with your own eyes. Then find Harbourmaster "
                     "Lin at the Maritime Office. She has no love for sect folk, but she listens."),
          "tunjuk"),
    ]),
    D("tema_pelabuhan_deng_redup", DENG, [tahap(Q, "redup")], 55, [
        N("mulai", ("Lensanya retak seperti es di awal musim semi. Kalau kau sudah melihatnya, pergilah ke Syahbandar. "
                    "Tangga melingkar itu dua ratus anak tangga; jangan pegang pagar yang berkarat.",
                    "The lens is cracked like ice at the start of spring. Once you have seen it, go to the Harbourmaster. "
                    "That spiral stair has two hundred steps; don't hold the rusty rail."),
          "tunjuk", pilihan=[C(("Baik, Kek.", "All right, Grandpa."))]),
    ]),
    D("tema_pelabuhan_lin_redup", LIN, [tahap(Q, "redup")], 55, [
        N("mulai", ("Orang sekte di kantor saya. Biar saya tebak: mau menyeberang gratis, atau mau memungut \"upeti perlindungan\"? "
                    "Bulan ini tiga kapal hilang di kabut. Saya tidak punya waktu untuk pedang yang sombong.",
                    "A sect cultivator in my office. Let me guess: a free crossing, or a little \"protection tribute\"? Three ships "
                    "were lost in the fog this month. I have no time for proud swords."),
          "sikap_lipat_tangan", pilihan=[
              C(("Kakek Deng mengirimku. Lensa mercusuar retak.", "Grandpa Deng sent me. The lighthouse lens is cracked."), "kapal"),
              C(("Aku hanya lewat.", "I am only passing through.")),
          ]),
        N("kapal", ("...Deng tua. Lensa retak meredupkan cahaya, ya. Tapi tiga kapal dengan nakhoda berpengalaman tidak "
                    "tenggelam hanya karena lampu agak redup. Ada yang lebih busuk di pelabuhan ini. Bila kau sungguh mau membantu, "
                    "kembalilah: catatan bea kapal-kapal itu ada di meja saya.",
                    "...Old Deng. A cracked lens dims the light, yes. But three ships with seasoned masters do not sink because "
                    "a lamp is a little dim. Something rotten sits in this harbour. If you truly want to help, come back: the "
                    "customs records of those ships are on my desk."),
          "menulis", "papan_catatan"),
    ]),
    # ================================================================ bab 2: the forged manifest
    D("tema_pelabuhan_lin_catatan", LIN, [tahap(Q, "catatan")], 55, [
        N("mulai", ("Tiga surat muatan dari kapal yang hilang, dan catatan timbangan dermaga waktu mereka berangkat. Satu surat "
                    "berbohong. Cocokkan segel dan beratnya; ingat, timbangan kami ikut menimbang peti.",
                    "Three manifests from the lost ships, and the quay scale records from the day they sailed. One manifest "
                    "lies. Match the seals and the weights; remember, our scale weighs the crates too."),
          "menulis", "papan_catatan", pilihan=[
              C(("Biar kuperiksa.", "Let me examine them."), akibat=["buka_layanan tema_dokumen"]),
              C(("Nanti saja.", "Later.")),
          ]),
    ]),
    D("tema_pelabuhan_lin_lapor", LIN, [tahap(Q, "lapor")], 55, [
        N("mulai", ("Wajahmu seperti orang yang menemukan ikan busuk di dasar keranjang. Bicaralah.",
                    "You look like someone who found a rotten fish at the bottom of the basket. Speak."),
          "sikap_tangan_belakang", pilihan=[
              C(("Peti \"minyak lampu\" itu berisi Minyak Bayangan. Itulah yang memadamkan pelita.",
                 "Those \"lamp oil\" casks hold Shadow Oil. That is what is choking the lamp."), "tindak",
                akibat=["nama_baik 3", "bendera pelabuhan_lapor", "kabar tema_pelabuhan_minyak pemain",
                        "kabar tema_pelabuhan_minyak_2 pemain", "quest_maju " + Q]),
              C(("Belum ada yang pasti.", "Nothing certain yet.")),
          ]),
        N("tindak", ("Minyak Bayangan... asapnya mencekik api roh, dan kabutnya membutakan nakhoda. Seseorang memasoknya lewat gudang "
                     "kota ini. Saya akan menyita setiap peti dan mencari tangan yang menandatangani segel palsu itu. Tapi lensanya "
                     "tetap retak. Temui Mbah Lunas di galangan; kalau ada yang bisa membuat kaca untuk mata mercusuar, dialah orangnya.",
                     "Shadow Oil... its smoke chokes a spirit flame, and its haze blinds a helmsman. Someone ships it through this "
                     "city's warehouses. I will seize every cask and find the hand that signed that false seal. But the lens is "
                     "still cracked. See Granny Keel at the shipyard; if anyone can make glass for the lighthouse's eye, it is her."),
          "tunjuk"),
    ]),
    D("tema_pelabuhan_zhao_lapor", ZHAO, [tahap(Q, "lapor")], 56, [
        N("mulai", ("Ah, tamu yang teliti! Seluruh dermaga membicarakanmu: orang sekte yang membaca surat muatan seperti juru tulis "
                    "tua. Duduklah, tehnya dari Negeri Rempah Selatan.",
                    "Ah, the careful guest! The whole quay talks of you: a sect cultivator who reads manifests like an old clerk. "
                    "Sit, the tea is from the Southern Spice Lands."),
          "hormat", pilihan=[
              C(("Peti \"minyak lampu\" itu lewat gudangmu, bukan?", "Those \"lamp oil\" casks passed through your warehouse, didn't they?"),
                "suap"),
              C(BYE),
          ]),
        N("suap", ("Dagang itu seperti laut, sahabatku: tenang di permukaan, banyak arus di bawahnya. Peti-peti itu... salah catat "
                   "saja. Terimalah %s ini sebagai tanda persahabatan, dan biarkan Syahbandar sibuk dengan urusan lain.",
                   "Trade is like the sea, my friend: calm on top, many currents beneath. Those casks... a clerical error. Take "
                   "these %s as a token of friendship, and let the Harbourmaster busy herself with other matters."),
          "hitung_sempoa", "kantong_uang", isian=["nb_pita:120"], pilihan=[
              C(("Terima uangnya dan diam.", "Take the money and keep quiet."), "terima",
                akibat=["beri_nb_pita 120", "bendera pelabuhan_suap", "quest_maju " + Q]),
              C(("Simpan uangmu. Aku akan melapor.", "Keep your money. I will report it."), "tolak"),
          ]),
        N("terima", ("Bijak, sangat bijak. Laut menyukai orang yang tahu kapan harus diam. Kalau Syahbandar bertanya, bilang saja "
                     "lensa tua memang sudah waktunya pecah.",
                     "Wise, very wise. The sea loves those who know when to be silent. If the Harbourmaster asks, just say an old "
                     "lens was bound to crack."), "tertawa"),
        N("tolak", ("Sayang sekali. Di pelabuhan, orang jujur cepat basah. Hati-hati di dermaga malam-malam, tamu.",
                    "A pity. In a harbour, honest people get wet quickly. Mind the quays at night, guest."),
          "sikap_lipat_tangan"),
    ]),
    # ================================================================ bab 3: the sea-salt glass
    D("tema_pelabuhan_gong_lensa", GONG, [tahap(Q, "lensa")], 55, [
        N("mulai", ("Lensa baru? Hah! Kaca biasa pecah kena dingin laut malam, macam lensa yang sekarang. Mata mercusuar butuh "
                    "Kaca Garam Laut: pasir pantai, roh ikan, api putih, dan tangan yang sabar. Tanganmu sabar, bocah?",
                    "A new lens? Hah! Common glass cracks in the night chill of the sea, like the one up there now. A lighthouse "
                    "eye needs Sea-Salt Glass: beach sand, a fish's spirit, white fire and patient hands. Are your hands patient, child?"),
          "palu_lunas", "palu_dempul", pilihan=[
              C(("Ajari aku meraciknya di tungkumu.", "Teach me to make it at your kiln."), akibat=["buka_layanan tema_racik"]),
              C(("Aku sudah membawa Kaca Garam Laut.", "I have brought Sea-Salt Glass."), "pasang",
                syarat=["punya kultivator:tema/kaca_garam_laut"]),
              C(("Di mana lagi kaca itu bisa didapat?", "Where else can that glass be had?"), "ibrahim"),
              C(BYE),
          ]),
        N("ibrahim", ("Si Ibrahim dari Negeri Barat punya sebongkah, dibawanya dari pantai-pantai garam di seberang. Mahal, dan dia "
                      "orang aneh: malam-malam menatap bintang sambil memegang piringan kuningan. Atau racik sendiri: empat genggam "
                      "pasir dan seekor Ikan Roh. Sewa perahu di Tambatan Nelayan kalau kau belum punya ikannya.",
                      "Ibrahim of the Western Lands has a lump of it, brought from the salt shores across the sea. Pricey, and an odd "
                      "one: he stares at the stars all night holding a brass disc. Or make it yourself: four handfuls of sand and "
                      "one Spirit Fish. Hire a boat at the Fishers' Moorings if you have no fish yet."),
          "bicara_tangan"),
        N("pasang", ("Coba lihat... hm. Bening, tak ada gelembung. Bagus. Malam ini kupasang sendiri, Deng tua tak boleh naik "
                     "tangga itu lagi. Nanti lihat ke mercusuar, bocah.",
                     "Let me see... hm. Clear, no bubbles. Good. I'll fit it myself tonight; old Deng mustn't climb those stairs "
                     "again. Watch the lighthouse later, child."),
          "angkat_bawa", akibat=["ambil kultivator:tema/kaca_garam_laut 1", "pelabuhan_pelita 1",
                                 "kabar tema_pelabuhan_lensa pemain", "quest_maju " + Q]),
    ]),
    D("tema_pelabuhan_ibrahim_lensa", IBRA, [tahap(Q, "lensa")], 55, [
        N("mulai", ("Damai atasmu, sahabat. Kaca Garam Laut? Ya, saya membawa satu bongkah dari pantai-pantai garam di barat, "
                    "untuk lensa teropong saya. Saya bisa melepasnya... dengan harga 300 Nilai Batu. Atau dengan harga yang lain.",
                    "Peace upon you, friend. Sea-Salt Glass? Yes, I brought a lump from the salt shores of the west, for the "
                    "lens of my spyglass. I could part with it... for 300 Nilai Batu. Or for another price."),
          "hormat_dalam", pilihan=[
              C(("Kubeli seharga 300 Nilai Batu.", "I will buy it for 300 Nilai Batu."), "jual", syarat=["nb 300"],
                akibat=["pelabuhan_kaca_ibrahim"], tampil=True,
                alasan=("Nilai Batumu kurang dari 300.", "You carry less than 300 Nilai Batu.")),
              C(("Harga yang lain itu apa?", "What is the other price?"), "bintang"),
              C(("Malam bintang kita sudah lewat.", "Our night of stars is done."), "hadiah",
                syarat=["bendera pelabuhan_lensa_bintang", "pelabuhan_bintang_malam 1"],
                akibat=["beri kultivator:tema/kaca_garam_laut 1", "bendera pelabuhan_lensa_bintang off"]),
              C(BYE),
          ]),
        N("bintang", ("Temani saya satu malam di puncak Pagoda Penunjuk. Di negeri saya, para pelaut membaca bintang seperti kalian "
                      "membaca papan kabar, tetapi bintang di langit timur ini masih asing bagi buku saya. Satu malam pengamatan, "
                      "dan kacanya milikmu.",
                      "Keep me company for one night at the top of the Guiding Pagoda. In my land sailors read the stars the way "
                      "you read a notice board, but the stars of this eastern sky are still strangers to my book. One night of "
                      "watching, and the glass is yours."),
          "v_tatap_tunjuk", pilihan=[
              C(("Baik, malam ini.", "Very well, tonight."), "janji", akibat=["pelabuhan_bintang", "bendera pelabuhan_lensa_bintang"]),
              C(("Mungkin lain kali.", "Perhaps another time.")),
          ]),
        N("janji", ("Naiklah setelah senja ke puncak pagoda dan tunggulah sampai bintang Penunjuk naik di atas laut. Lalu kembali "
                    "pada saya.", "Climb to the pagoda top after dusk and wait until the Guiding Star rises above the sea. Then "
                    "come back to me."), "tunjuk"),
        N("jual", ("Semoga cahayanya kembali menuntun kapal-kapal pulang.", "May its light guide the ships home again."), "hormat_dalam"),
        N("hadiah", ("Bintang-bintang berpihak pada kita malam itu. Ambillah kacanya, dan terima kasih: buku saya kini lebih "
                     "panjang satu halaman.", "The stars were on our side that night. Take the glass, and thank you: my book "
                     "is one page longer now."), "hormat_dalam"),
    ]),
    # ================================================================ bab 4: the raid
    D("tema_pelabuhan_deng_serbuan", DENG, [tahap(Q, "serbuan")], 55, [
        N("mulai", ("Lampunya terang lagi! Terang sekali... tapi terang juga mengundang mata. Ada yang menunggu cahaya ini di laut, "
                    "aku bisa merasakannya di lututku. Jagalah dermaga malam ini.",
                    "The lamp is bright again! So bright... but brightness invites eyes too. Something out on the sea has been "
                    "waiting for this light, I can feel it in my knees. Guard the quays tonight."),
          "teropong_laut", "teropong", pilihan=[C(("Aku akan berjaga.", "I will keep watch."))]),
    ]),
    D("tema_pelabuhan_lin_serbuan", LIN, [tahap(Q, "serbuan")], 55, [
        N("mulai", ("Penjaga sudah siaga di Dermaga Agung dan gudang. Bila jung berlayar hitam datang, kami butuh setiap pedang, "
                    "termasuk pedang sekte yang sombong.",
                    "The guards stand ready at the Grand Quay and the warehouses. If a black-sailed junk comes, we need every "
                    "sword, even a proud sect sword."),
          "sikap_tangan_belakang", pilihan=[C(("Aku di sini.", "I am here."))]),
    ]),
    D("tema_pelabuhan_zhao_serbuan", ZHAO, [tahap(Q, "serbuan"), "bendera pelabuhan_suap"], 57, [
        N("mulai", ("Ssst! Jangan keras-keras. Mereka... mereka meminta kunci Gudang Bersama malam ini. Aku tak bisa menolak; "
                    "utangku pada Hiu Hitam lebih dalam dari palung. Maafkan aku.",
                    "Shh! Not so loud. They... they asked for the key to the Shared Warehouse tonight. I could not refuse; my "
                    "debt to the Black Shark is deeper than a trench. Forgive me."),
          "bisik", pilihan=[C(("Kau akan menanggung ini, Zhao.", "You will answer for this, Zhao."))]),
    ]),
    # ================================================================ bab 5: the Dawn Pearl
    D("tema_pelabuhan_deng_mutiara", DENG, [tahap(Q, "mutiara")], 55, [
        N("mulai", ("Hei Sha... Dulu dia muridku, bocah kurus yang menghafal setiap karang di teluk ini. Lalu laut memanggilnya "
                    "terlalu keras. Dia mencari Mutiara Pelita, tapi mutiara itu sudah lama habis cahayanya. Ini, pegang. "
                    "Rasakan betapa dinginnya.",
                    "Hei Sha... He was my apprentice once, a skinny boy who knew every reef in this bay by heart. Then the sea "
                    "called him too loudly. He came for the Lamp Pearl, but its light died long ago. Here, hold it. Feel how cold it is."),
          "cerita", pilihan=[
              C(("Di mana mencari mutiara yang baru?", "Where can a new pearl be found?"), "laut"),
              C(BYE),
          ]),
        N("laut", ("Hanya di Laut Tanpa Batas. Kapten Gao Fan berlayar ke sana tiap tengah hari; aku sudah bicara padanya: untukmu, "
                   "sekali ini, gratis. Di sana ada Pulau Pasar, para pedagangnya menjual Mutiara Fajar bagi yang berani bertanya "
                   "(dekati mereka dengan hormat, sambil merunduk). Atau rebut dari perut ular laut. Bawa pulang ke sini.",
                   "Only in the Boundless Sea. Captain Gao Fan sails there every noon; I have spoken to him: for you, this once, "
                   "free. There is an Isle Market, and its merchants sell a Dawn Pearl to those bold enough to ask (approach them "
                   "humbly, crouching low). Or take one from a sea serpent's belly. Bring it home, here."),
          "tunjuk"),
    ]),
    D("tema_pelabuhan_gao_mutiara", GAO, [tahap(Q, "mutiara")], 55, [
        N("mulai", ("Kakek Deng sudah bilang, sudah bilang. Gratis. Hhh... jangan bilang siapa-siapa; kalau semua orang tahu, aku "
                    "bangkrut. Kapal berangkat pukul dua belas. Di darat aku takut pada anjing, tapi di laut, percayalah padaku.",
                    "Grandpa Deng told me, he told me. Free. Hhh... don't tell anyone; if everyone knew, I'd be ruined. We sail at "
                    "twelve. On land I am afraid of dogs, but at sea, trust me."),
          "bicara_tangan", pilihan=[C(("Sampai di dermaga.", "See you on the quay."))]),
    ]),
    D("tema_pelabuhan_deng_titipan", DENG, [tahap(Q, "titipan")], 55, [
        N("mulai", ("Kau lama sekali, jadi Ibrahim menitipkan ini lewat kafilahnya: Mutiara Fajar, dibeli dari pedagang pulau. "
                    "Katanya bintang-bintang menyuruhnya. Simpan baik-baik; Hei Sha pasti tahu mutiara itu sudah ada di kota.",
                    "You took so long that Ibrahim sent this with his caravan: a Dawn Pearl, bought from an island merchant. He says "
                    "the stars told him to. Keep it safe; Hei Sha will surely know the pearl is in the city."),
          "cerita", pilihan=[C(("Terima kasih, Kek.", "Thank you, Grandpa."))]),
    ]),
    # ================================================================ bab 6: the breakwater
    D("tema_pelabuhan_heisha_duel", HEI, [tahap(Q, "duel")], 70, [
        N("mulai", ("Jadi kau yang membuat lampu tua itu menyala lagi. Kau tahu berapa banyak kapal yang kurampok dalam gelap? "
                    "Mutiara itu, serahkan. Atau laut akan menelanmu seperti dia menelan semua orang yang berharap.",
                    "So you are the one who lit the old lamp again. Do you know how many ships I robbed in the dark? The pearl: "
                    "hand it over. Or the sea will swallow you the way it swallows everyone who hopes."),
          "sikap_berkacak", pilihan=[
              C(("Coba ambil sendiri.", "Come and take it."), akibat=["pelabuhan_heisha_musuh", "tutup"]),
              C(("Tunggu. Gurumu masih menyalakan lampu itu untukmu tiap malam.",
                 "Wait. Your master still lights that lamp for you every night."), "kisah", syarat=["nama_baik 30"], tampil=True,
                alasan=("Hei Sha tak mau mendengar orang asing (butuh Nama Baik 30).",
                        "Hei Sha will not listen to a stranger (needs Good Name 30).")),
              C(("Aku akan kembali.", "I will be back.")),
          ]),
        N("kisah", ("...Lelaki tua itu? Dia mengusirku waktu aku menenggelamkan kapal pertama. \"Laut bukan milikmu,\" katanya. "
                    "Lalu dia tetap menuang minyak ke lampu itu, tiap malam, menghadap ke arah aku berlayar? Bohong. ...Buktikan "
                    "dengan pedang, bukan dengan mulut.",
                    "...That old man? He drove me out when I sank my first ship. \"The sea is not yours,\" he said. And still he "
                    "poured oil into that lamp, every night, facing the way I sailed? A lie. ...Prove it with a sword, not with words."),
          "v_tatap_renung", pilihan=[
              C(("Duel Pedang secara terhormat. Bila aku menang, kau dengarkan dia.",
                 "An honourable Duel of Swords. If I win, you listen to him."),
                akibat=["bendera pelabuhan_heisha_dengar", "pelabuhan_heisha_ramah", "mulai_duel"]),
              C(("Kalau begitu, biar golok yang bicara.", "Then let the blades talk."), akibat=["pelabuhan_heisha_musuh", "tutup"]),
          ]),
    ]),
    D("tema_pelabuhan_deng_duel", DENG, [tahap(Q, "duel")], 55, [
        N("mulai", ("Dia menunggu di Pemecah Ombak, aku melihat layarnya dari menara. Kalau dia belum datang, biar kunyalakan "
                    "pelita dua kali; itu tanda kami dulu.",
                    "He waits on the Breakwater, I saw his sail from the tower. If he has not come, let me flash the lamp twice; "
                    "that was our sign, long ago."),
          "teropong_laut", "teropong", pilihan=[
              C(("Nyalakan tandanya, Kek.", "Flash the sign, Grandpa."), akibat=["pelabuhan_heisha"]),
              C(BYE),
          ]),
    ]),
    D("tema_pelabuhan_heisha_nasib", HEI, [tahap(Q, "nasib")], 70, [
        N("mulai", ("Cukup... laut sudah memutuskan. Golokku patah semangatnya sebelum bilahnya. Lakukan apa yang harus kau lakukan.",
                    "Enough... the sea has decided. My dao lost its heart before its edge. Do what you must."),
          "sikap_tunduk", pilihan=[
              C(("Kau akan menghadap Syahbandar Lin.", "You will answer to Harbourmaster Lin."),
                akibat=["nama_baik 3", "pelabuhan_nasib serah", "kabar tema_pelabuhan_heisha_serah_2 pemain", "quest_maju " + Q]),
              C(("Pulanglah pada gurumu. Dia menunggumu di menara.", "Go home to your master. He waits for you in the tower."),
                syarat=["bendera pelabuhan_heisha_dengar"], tampil=True,
                alasan=("Hei Sha belum mau mendengar tentang gurunya.", "Hei Sha has not yet agreed to hear of his master."),
                akibat=["nama_baik 5", "pelabuhan_nasib damai", "kabar tema_pelabuhan_heisha_damai_2 pemain", "quest_maju " + Q]),
              C(("Lautmu berakhir di sini.", "Your sea ends here."), akibat=["nama_baik -5", "pelabuhan_nasib tumpas", "kabar tema_pelabuhan_heisha_tumpas_2 pemain",
                                                                                  "quest_maju " + Q]),
          ]),
    ]),
    D("tema_pelabuhan_lin_nasib", LIN, [tahap(Q, "nasib")], 50, [
        N("mulai", ("Hiu Hitam sudah tak berdaya, kata penjaga? Kalau kau tak sanggup memutuskan, serahkan dia pada hukum "
                    "pelabuhan. Kami akan mengadilinya dengan adil, lebih adil dari laut.",
                    "The Black Shark is beaten, the guards say? If you cannot decide, hand him to the harbour's law. We will judge "
                    "him fairly, more fairly than the sea would."),
          "sikap_tangan_belakang", pilihan=[
              C(("Adililah dia.", "Judge him."), akibat=["nama_baik 3", "pelabuhan_nasib serah", "kabar tema_pelabuhan_heisha_serah_2 pemain", "quest_maju " + Q]),
              C(("Belum.", "Not yet.")),
          ]),
    ]),
    D("tema_pelabuhan_deng_nasib", DENG, [tahap(Q, "nasib"), "bendera pelabuhan_heisha_dengar"], 52, [
        N("mulai", ("Kudengar kau bertarung dengannya secara terhormat. Bawa dia ke sini... tidak, biar aku yang turun. Sudah "
                    "dua puluh tahun aku menunggu bocah itu pulang.",
                    "I hear you fought him with honour. Bring him here... no, let me go down. Twenty years I have waited for that "
                    "boy to come home."),
          "menangis", pilihan=[
              C(("Mari, Kek. Dia di Pemecah Ombak.", "Come, Grandpa. He is on the Breakwater."),
                akibat=["nama_baik 5", "pelabuhan_nasib damai", "kabar tema_pelabuhan_heisha_damai_2 pemain", "quest_maju " + Q]),
              C(("Sebentar lagi.", "In a moment.")),
          ]),
    ]),
    # ================================================================ bab 7: the relit lamp
    D("tema_pelabuhan_deng_upacara", DENG, [tahap(Q, "upacara")], 60, [
        N("mulai", ("Mutiara Fajar... cahayanya seperti pagi pertama di laut, sebelum ada kapal, sebelum ada bajak laut. Mari, kita "
                    "nyalakan Pelita Angin bersama. Seluruh dermaga sudah menunggu.",
                    "The Dawn Pearl... its light is like the first morning on the sea, before there were ships, before there were "
                    "pirates. Come, let us light the Wind Lamp together. The whole quay is waiting."),
          "tatap_langit", pilihan=[
              C(("Nyalakan.", "Light it."), "nyala", syarat=["punya kultivator:tema/mutiara_fajar"], tampil=True,
                alasan=("Kau tidak membawa Mutiara Fajar.", "You do not carry the Dawn Pearl."),
                akibat=["pelabuhan_upacara", "gelar penjaga_pelita", "nama_baik 10", "kabar tema_pelabuhan_pelita_nyala pemain",
                        "kabar tema_pelabuhan_pelita_nyala_2 pemain",
                        "quest_maju " + Q]),
              C(("Sebentar lagi.", "In a moment.")),
          ]),
        N("nyala", ("Lihat... birunya, emasnya! Kapal-kapal akan pulang lagi, dan Hari Dewi Laut berikutnya diadakan atas namamu. "
                    "Mulai malam ini, kau Penjaga Pelita kota ini, sama seperti aku.",
                    "Look... the blue, the gold! The ships will come home again, and the next Sea Goddess Day is held in your "
                    "name. From tonight you are this city's Keeper of the Light, just as I am."),
          "tertawa"),
    ]),
    D("tema_pelabuhan_deng_selesai", DENG, ["quest %s selesai" % Q], 20, [
        N("mulai", ("Penjaga Pelita! Duduklah. Malam ini sinarnya sampai ke Karang Seribu lagi; nakhoda-nakhoda membungkuk ke arah "
                    "menara waktu lewat. Ada kabar dari dermaga?",
                    "Keeper of the Light! Sit down. Tonight the beam reaches the Thousand Reefs again; the captains bow toward the "
                    "tower as they pass. Any news from the quays?"),
          "tertawa", pilihan=[
              C(("Bagaimana kabar Hei Sha, Kek?", "How is Hei Sha, Grandpa?"), "murid", syarat=["bendera pelabuhan_hasil_damai"]),
              C(("Bagaimana kabar Hei Sha, Kek?", "How is Hei Sha, Grandpa?"), "sel", syarat=["bendera pelabuhan_hasil_serah"]),
              C(("Kakek masih memikirkan Hei Sha?", "Do you still think of Hei Sha, Grandpa?"), "makam",
                syarat=["bendera pelabuhan_hasil_tumpas"]),
              C(("Ke mana Hei Sha pergi?", "Where did Hei Sha go?"), "kabut",
                syarat=["!bendera pelabuhan_hasil_damai", "!bendera pelabuhan_hasil_serah", "!bendera pelabuhan_hasil_tumpas"]),
              C(("Kabar apa yang Kakek dengar?", "What news have you heard, Grandpa?"), "kabar"),
              C(BYE),
          ]),
        N("murid", ("Tiap senja dia naik dua ratus anak tangga itu dengan sekeranjang minyak, padahal aku sudah bilang lututku masih "
                    "kuat. Menakhodai kapal lintas kedua, menambal jala anak-anak dermaga, dan belum sekali pun mengangkat golok "
                    "lagi. Kemarin dia bertanya apakah karang di mulut teluk masih di tempat yang sama. Masih, kataku. Sama "
                    "seperti rumahnya.",
                    "Every dusk he climbs those two hundred steps with a basket of oil, though I told him my knees are still "
                    "strong. He captains the second crossing ship, mends the nets of the quay children, and has not lifted a blade "
                    "once. Yesterday he asked whether the reef at the bay's mouth was still where it used to be. Still there, I "
                    "said. Same as his home."),
          "tertawa", pilihan=[
              C(("Sampaikan salamku padanya.", "Give him my greetings."), "salam"),
              C(BYE),
          ]),
        N("salam", ("Akan kusampaikan. Dia akan pura-pura tidak peduli, lalu menggosok lensa itu dua kali lebih lama. Begitulah murid.",
                    "I will. He will pretend not to care, then polish that lens twice as long. That is apprentices for you."),
          "angguk_dengar"),
        N("sel", ("Syahbandar Lin menjatuhkan hukuman yang adil: tiga tahun mengayuh kapal keruk pelabuhan, membersihkan lumpur "
                  "dari alur yang dulu dia jarah. Aku membawakannya sup ikan Bibi Yu tiap hari kedua puluh tiga. Dia tidak bicara... "
                  "tapi mangkuknya selalu kosong.",
                  "Harbourmaster Lin passed a fair sentence: three years pedalling the harbour dredge, clearing mud from the very "
                  "channels he once plundered. I bring him Auntie Yu's fish soup every twenty-third. He does not speak... but the "
                  "bowl always comes back empty."),
          "cerita", pilihan=[
              C(("Tiga tahun tidak lama untuk laut.", "Three years is not long to the sea."), "tiga"),
              C(BYE),
          ]),
        N("tiga", ("Benar. Laut menunggu dengan sabar. Kalau tiga tahun lagi dia mengetuk pintu menara, aku akan membuka pintunya.",
                   "True. The sea waits patiently. If he knocks on the tower door in three years, I will open it."),
          "tatap_langit"),
        N("makam", ("Kau melakukan apa yang laut minta, dan para nakhoda selamat karenanya. Aku tidak menyalahkanmu. Tapi dua puluh "
                    "tahun aku menyalakan lampu ini untuk seorang bocah kurus yang tersesat. Sekarang aku menyalakan satu pelita "
                    "kecil lagi, di ujung Pemecah Ombak, menghadap ke laut. Untuk bocah itu, bukan untuk kaptennya.",
                    "You did what the sea asked, and the captains are safe because of it. I do not blame you. But for twenty years "
                    "I lit this lamp for a skinny lost boy. Now I light one more little lamp, at the end of the Breakwater, facing "
                    "the sea. For the boy, not for the captain."),
          "tatap_langit", "lentera_tangan", pilihan=[
              C(("Boleh aku ikut menyalakannya?", "May I help light it?"), "pelita_kecil", akibat=["nama_baik 1"]),
              C(BYE),
          ]),
        N("pelita_kecil", ("...Ya. Senja nanti. Bawa sumbu baru; yang lama sudah habis dimakan angin.",
                           "...Yes. At dusk. Bring a new wick; the old one is eaten by the wind."),
          "angguk_dengar"),
        N("kabut", ("Hilang di kabut, seperti waktu dia pergi pertama kali. Tapi sekarang pelitanya terang. Kalau suatu malam dia "
                    "ingin pulang, sinar ini akan menemukannya lebih dulu daripada penjaga. Aku tidak akan mengunci pintu menara.",
                    "Gone into the fog, like the first time he left. But now the lamp is bright. If some night he wishes to come "
                    "home, this light will find him before the guards do. I will not lock the tower door."),
          "tatap_langit"),
        N("kabar", "kabar", "cerita"),
    ]),
    D("tema_pelabuhan_lin_selesai", LIN, ["quest %s selesai" % Q, "pelabuhan_di_kota"], 15, [
        N("mulai", ("Penjaga Pelita. Sejak lampu itu menyala, tidak satu kapal pun hilang di kabut. Saya mencatatnya di buku bea, "
                    "di halaman yang biasanya untuk kerugian. Duduk, kalau mau. Tapi jangan menghunus apa pun di kantor saya.",
                    "Keeper of the Light. Since the lamp was lit, not one ship has been lost in the fog. I recorded it in the customs "
                    "book, on the page usually kept for losses. Sit, if you like. But draw nothing in my office."),
          "sikap_tangan_belakang", "papan_catatan", pilihan=[
              C(("Apa jadinya Zhao Wanli?", "What became of Zhao Wanli?"), "zhao_adil", syarat=["bendera pelabuhan_lapor"]),
              C(("Apa jadinya Zhao Wanli?", "What became of Zhao Wanli?"), "zhao_suap", syarat=["bendera pelabuhan_suap"]),
              C(("Apa jadinya Zhao Wanli?", "What became of Zhao Wanli?"), "zhao_lolos",
                syarat=["!bendera pelabuhan_lapor", "!bendera pelabuhan_suap"]),
              C(("Apa putusan untuk Hei Sha?", "What was the verdict on Hei Sha?"), "putusan",
                syarat=["bendera pelabuhan_hasil_serah"]),
              C(("Kau tidak keberatan Hei Sha bebas?", "You don't mind that Hei Sha walks free?"), "bebas",
                syarat=["bendera pelabuhan_hasil_damai"]),
              C(("Kabar apa di pelabuhan?", "What news in the harbour?"), "kabar"),
              C(BYE),
          ]),
        N("zhao_adil", ("Didenda seribu Nilai Batu, dicopot dari kursi Dewan, dan sekarang setiap segel Gudang Bersama punya dua kunci: "
                        "satu di tangannya, satu di tangan saya. Dia membenci saya. Itu tandanya aturannya bekerja.",
                        "Fined a thousand Nilai Batu, stripped of his Council seat, and now every seal on the Shared Warehouse has two "
                        "keys: one in his hand, one in mine. He hates me. That means the rules work."),
          "menulis", "papan_catatan"),
        N("zhao_suap", ("Saya membaca buku besarnya waktu menyita peti-peti itu. Ada satu baris: seratus dua puluh keping per pita, "
                        "\"untuk tamu yang teliti\". Saya tahu siapa tamu itu. Lampu itu tetap menyala karenamu, jadi saya diam. "
                        "Tapi saya mencatatnya juga.",
                        "I read his ledger when I seized those casks. One line: a hundred and twenty pieces per band, \"for the "
                        "careful guest\". I know who that guest was. The lamp burns because of you, so I keep quiet. But I wrote it "
                        "down too."),
          "sikap_lipat_tangan", pilihan=[
              C(("Aku akan mengembalikannya ke kas pelabuhan.", "I will pay it back into the harbour chest."), "kembali",
                syarat=["!bendera pelabuhan_suap_tebus", "nb_pita 120"], tampil=True,
                alasan=("Nilai Batumu tidak cukup untuk menebusnya.", "You do not carry enough Nilai Batu to repay it."),
                akibat=["bayar_pita 120", "nama_baik 6", "bendera pelabuhan_suap_tebus"]),
              C(("Itu sudah lewat.", "That is in the past.")),
          ]),
        N("kembali", ("...Saya tidak menyangka. Uang ini akan membeli tali tambat baru untuk Tambatan Nelayan. Baris di buku itu "
                      "saya coret. Dengan tinta merah, supaya kelihatan dicoret.",
                      "...I did not expect that. This money will buy new mooring ropes for the Fishers' Moorings. I will strike that "
                      "line in the book. In red ink, so it shows it was struck."),
          "angguk_dengar"),
        N("zhao_lolos", ("Peti-peti itu hilang dari gudang semalam sebelum saya datang. Tanpa surat, tanpa segel, tanpa saksi. Zhao "
                         "masih tersenyum di Balai Dewan. Saya bisa menunggu. Pedagang yang serakah selalu menulis sesuatu.",
                         "The casks vanished from the warehouse the night before I came. No papers, no seals, no witnesses. Zhao "
                         "still smiles in the Council Hall. I can wait. A greedy merchant always writes something down."),
          "sikap_lipat_tangan"),
        N("putusan", ("Tiga tahun di kapal keruk. Ada yang bilang terlalu ringan untuk seorang bajak laut. Tapi dia menyerah tanpa "
                      "darah dan menunjukkan sendiri gudang-gudang tempat minyak itu disimpan. Hukum yang hanya bisa menggantung "
                      "orang tidak akan pernah membuat laut lebih aman.",
                      "Three years on the dredge. Some say that is too light for a pirate. But he surrendered without blood and "
                      "showed us himself the warehouses where the oil was hidden. A law that can only hang people will never make "
                      "the sea any safer."),
          "sikap_tangan_belakang"),
        N("bebas", ("Saya keberatan. Saya menulis tiga surat protes ke Dewan. Lalu saya melihatnya menyeret jaring tiga nelayan dari "
                    "pusaran di Karang Seribu, di tengah badai yang tak berani dilayari siapa pun. Surat keempat tidak jadi saya tulis.",
                    "I do mind. I wrote three letters of protest to the Council. Then I watched him drag three fishers' nets out of "
                    "the whirlpool at the Thousand Reefs, in a storm nobody dared sail. I never wrote the fourth letter."),
          "sikap_lipat_tangan"),
        N("kabar", "kabar", "sikap_tangan_belakang"),
    ]),
    D("tema_pelabuhan_zhao_selesai_suap", ZHAO, ["quest %s selesai" % Q, "bendera pelabuhan_suap", "pelabuhan_di_kota"], 22, [
        N("mulai", ("Sahabatku yang pendiam! Ssst, duduklah dekat-dekat. Syahbandar memeriksa bukuku seperti kucing memeriksa "
                    "keranjang ikan. Kau... tidak bilang apa-apa padanya, kan? Tentu saja tidak. Kita orang bijak.",
                    "My quiet friend! Shh, sit close. The Harbourmaster combs my books like a cat combs a fish basket. You... "
                    "haven't told her anything, have you? Of course not. We are wise people."),
          "bisik", "kantong_uang", pilihan=[
              C(("Dia sudah tahu, Zhao.", "She already knows, Zhao."), "pucat"),
              C(("Dengar-dengar apa saja?", "Heard anything lately?"), "kabar"),
              C(BYE),
          ]),
        N("pucat", ("Dia... tahu? Dan dia diam? Itu lebih menakutkan daripada denda. Perempuan itu menyimpan utang seperti nelayan "
                    "menyimpan garam. Aku harus menyumbang untuk kuil. Banyak.",
                    "She... knows? And she says nothing? That is more frightening than a fine. That woman keeps debts the way fishers "
                    "keep salt. I must donate to the temple. A lot."),
          "kaget"),
        N("kabar", "kabar", "hitung_sempoa"),
    ]),
    D("tema_pelabuhan_zhao_selesai_lapor", ZHAO, ["quest %s selesai" % Q, "bendera pelabuhan_lapor", "pelabuhan_di_kota"], 21, [
        N("mulai", ("Oh. Kau. Si penjilat surat muatan. Gara-gara kau aku harus meminta izin perempuan itu untuk membuka gudangku "
                    "sendiri. ...Tapi kuakui, tamu: sejak lampu menyala, kapal-kapal rempah datang dua kali lebih sering. Laba "
                    "tetaplah laba.",
                    "Oh. You. The manifest-licker. Because of you I must ask that woman's leave to open my own warehouse. ...But I "
                    "admit it, guest: since the lamp was lit, the spice ships come twice as often. Profit is still profit."),
          "sikap_lipat_tangan", pilihan=[
              C(("Kau masih bisa jujur, Zhao.", "You can still be honest, Zhao."), "jujur"),
              C(("Dengar-dengar apa saja?", "Heard anything lately?"), "kabar"),
              C(BYE),
          ]),
        N("jujur", ("Jujur itu mahal di pelabuhan. Tapi kudengar orang jujur tidak perlu membayar penjaga malam. Mungkin... aku "
                    "akan menghitung ulang.",
                    "Honesty is expensive in a harbour. But I hear honest men need not pay night watchmen. Perhaps... I will "
                    "recount."),
          "hitung_sempoa", "kantong_uang"),
        N("kabar", "kabar", "hitung_sempoa"),
    ]),
    # ================================================================ side: Bibi Yu's spirit fish soup
    D("tema_pelabuhan_bibi_yu_sapa", YU, ["quest %s baru" % QI, "pelabuhan_di_kota"], 40, [
        N("mulai", ("Ikan! Ikan segar! Eh, kau, yang bawa pedang, mau bantu Bibi? Hari Dewi Laut nanti Bibi masak sup untuk seluruh "
                    "kampung pelaut, dan sup Dewi harus pakai Ikan Roh. Sepuluh ekor! Nelayan sekarang malas melaut jauh.",
                    "Fish! Fresh fish! Eh, you, with the sword, want to help Auntie? On Sea Goddess Day Auntie cooks soup for the whole "
                    "sailors' quarter, and the Goddess's soup needs Spirit Fish. Ten of them! The fishers are too lazy to go far now."),
          "seru_dagang", "ikan_tali", pilihan=[
              C(("Sepuluh Ikan Roh. Akan kucarikan.", "Ten Spirit Fish. I will find them."), "terima", akibat=["quest_mulai " + QI]),
              C(("Di mana ikan itu ditangkap?", "Where are they caught?"), "mana"),
              C(BYE),
          ]),
        N("mana", ("Di teluk, tentu! Sewa perahu di Tambatan Nelayan, dua Nilai Batu sejam, lalu pancing yang sabar. Satu dari "
                   "sepuluh tangkapan biasanya Ikan Roh, sisiknya biru menyala.",
                   "In the bay, of course! Hire a boat at the Fishers' Moorings, two Nilai Batu an hour, then fish with patience. "
                   "About one catch in ten is a Spirit Fish; its scales glow blue."),
          "tunjuk", pilihan=[C(("Akan kucarikan.", "I will find them."), "terima", akibat=["quest_mulai " + QI]), C(BYE)]),
        N("terima", ("Anak baik! Bawa ke lapak Bibi di Pasar Ikan Pagi. Jangan biarkan mereka mati kepanasan!",
                     "Good child! Bring them to Auntie's stall at the Morning Fish Market. Don't let them cook in the sun!"), "tertawa"),
    ]),
    D("tema_pelabuhan_bibi_yu_kumpul", YU, [tahap(QI, "kumpul")], 40, [
        N("mulai", ("Belum sepuluh? Teluk tidak lari ke mana-mana. Ayo, ayo!", "Not ten yet? The bay isn't going anywhere. Go on, go on!"),
          "seru_dagang", pilihan=[C(("Segera, Bi.", "Soon, Auntie."))]),
    ]),
    D("tema_pelabuhan_bibi_yu_serah", YU, [tahap(QI, "serah")], 45, [
        N("mulai", ("Aduh, lihat sisiknya! Biru seperti mata Dewi! Sini, sini, berikan semuanya.",
                    "Oh my, look at those scales! Blue as the Goddess's eyes! Here, here, give them all to me."),
          "kaget", pilihan=[
              C(("Ini sepuluh Ikan Roh.", "Here are ten Spirit Fish."), "terima", syarat=["punya kultivator:tema/ikan_roh 10"],
                tampil=True, alasan=("Kau tidak membawa sepuluh Ikan Roh.", "You do not carry ten Spirit Fish."),
                akibat=["ambil kultivator:tema/ikan_roh 10", "beri kultivator:tema/sup_ikan_pagi 2", "beri_nb 75", "nama_baik 2",
                        "kabar tema_pelabuhan_sup pemain", "quest_maju " + QI]),
              C(("Nanti dulu, Bi.", "Wait a little, Auntie.")),
          ]),
        N("terima", ("Ini upahmu, dan dua mangkuk sup yang pertama, untuk sang pemancing. Kalau kau masih di kota waktu Hari Dewi "
                     "Laut, duduklah di dermaga; Bibi sisakan satu mangkuk lagi.",
                     "Here's your pay, and the first two bowls of soup, for the angler. If you're in town on Sea Goddess Day, sit on "
                     "the quay; Auntie will save you another bowl."), "tertawa"),
    ]),
    # ================================================================ side: A-Lan's frayed rope
    D("tema_pelabuhan_a_lan_sapa", ALAN, ["quest %s baru" % QT, "pelabuhan_di_kota"], 40, [
        N("mulai", ("Kau orang sekte, kan? Cepat, kuat, tidak takut air? Aku mau jadi nakhoda, tapi Syahbandar bilang tali tambatku "
                    "masih \"putus-putus\": aku belum pernah mengalahkan orang lain di teluk. Lomba denganku! Kalau aku kalah dari "
                    "orang sehebat kau, dan tetap berani, itu baru nakhoda.",
                    "You're sect folk, right? Fast, strong, not afraid of water? I want to be a captain, but the Harbourmaster says "
                    "my mooring rope is still \"frayed\": I've never beaten anyone in the bay. Race me! If I lose to someone as good "
                    "as you and keep my nerve, that's a captain."),
          "bicara_tangan", "tali", pilihan=[
              C(("Siapkan perahumu.", "Ready your boat."), "terima", akibat=["quest_mulai " + QT]),
              C(("Aku tak pandai mendayung.", "I'm no good at rowing.")),
          ]),
        N("terima", ("Ha! Sewa perahu di Tambatan Nelayan, lalu panggil aku. Satu putaran jalur lomba. Atau kalahkan aku waktu Lomba "
                     "Perahu di Hari Dewi Laut, kalau kau suka penonton.",
                     "Ha! Hire a boat at the Fishers' Moorings, then call me. One lap of the race course. Or beat me in the Boat Race "
                     "on Sea Goddess Day, if you like a crowd."), "tertawa"),
    ]),
    D("tema_pelabuhan_a_lan_lomba", ALAN, [tahap(QT, "lomba")], 45, [
        N("mulai", ("Perahumu sudah siap? Aku sudah tiga hari mengoles dayungku dengan minyak ikan.",
                    "Is your boat ready? I've been oiling my oar with fish oil for three days."),
          "ikat_tali", "tali", pilihan=[
              C(("Ayo, satu putaran sekarang.", "Come on, one lap now."), akibat=["pelabuhan_lomba_latih", "tutup"]),
              C(("Nanti, waktu Hari Dewi Laut.", "Later, on Sea Goddess Day.")),
          ]),
    ]),
    D("tema_pelabuhan_lin_alan", LIN, ["quest %s selesai" % QT], 40, [
        N("mulai", ("A-Lan bilang kau mengalahkannya di teluk, dan dia tersenyum waktu kalah. Itu yang saya tunggu: nakhoda yang "
                    "tahu rasanya kalah tak akan menantang badai karena gengsi. Saya beri dia restu. Terima kasih, orang sekte.",
                    "A-Lan says you beat her in the bay, and she smiled as she lost. That is what I was waiting for: a captain who "
                    "knows how losing feels will not dare a storm out of pride. I give her my blessing. Thank you, sect cultivator."),
          "sikap_tangan_belakang", pilihan=[C(("Dia akan jadi nakhoda yang baik.", "She will make a fine captain."))]),
    ], sekali=True),
    # ================================================================ side: Ibrahim's three nights
    D("tema_pelabuhan_ibrahim_sapa", IBRA, ["quest %s baru" % QB, "pelabuhan_di_kota"], 40, [
        N("mulai", ("Damai atasmu. Kau melihat piringan kuningan ini? Astrolab, dari negeri saya. Ia menghitung bintang supaya "
                    "pelaut tahu letak utara sejati. Tapi di langit timur saya perlu tiga malam lagi untuk menyalin semua bintang "
                    "Penunjuk. Maukah kau menemani saya?",
                    "Peace upon you. You see this brass disc? An astrolabe, from my land. It reckons the stars so sailors know true "
                    "north. But under the eastern sky I need three more nights to copy all the Guiding Stars. Will you keep me company?"),
          "hormat_dalam", pilihan=[
              C(("Tiga malam di pagoda. Aku ikut.", "Three nights at the pagoda. I'm in."), "terima", akibat=["quest_mulai " + QB]),
              C(BYE),
          ]),
        N("terima", ("Puncak Pagoda Penunjuk, selepas senja, sekali tiap malam. Saya bawa teh, kau bawa kesabaran.",
                     "The top of the Guiding Pagoda, after dusk, once a night. I bring the tea, you bring the patience."), "tunjuk"),
    ]),
    D("tema_pelabuhan_ibrahim_bintang", IBRA, [tahap(QB, "malam")], 45, [
        N("mulai", ("Lihat ke selatan: tujuh bintang yang dinamai pelaut kalian \"Kemudi Dewi\". Di negeri saya mereka disebut lain, "
                    "tapi mereka tetap menunjuk pulang. Kita bertemu lagi di pagoda setelah gelap.",
                    "Look south: seven stars your sailors call \"the Goddess's Rudder\". In my land they have another name, but they "
                    "still point home. We meet again at the pagoda after dark."),
          "v_tatap_tunjuk", pilihan=[C(("Sampai malam.", "Until tonight."))]),
    ]),
    D("tema_pelabuhan_ibrahim_selesai", IBRA, ["quest %s selesai" % QB], 20, [
        N("mulai", ("Sahabat bintangku! Astrolab kecil itu menunjuk utara sejati, di laut mana pun. Dan bukuku kini punya bab "
                    "tentang langit timur, dengan namamu di halaman pertama.",
                    "My star friend! That little astrolabe points to true north, on any sea. And my book now has a chapter on the "
                    "eastern sky, with your name on the first page."),
          "hormat_dalam", pilihan=[C(("Terima kasih, Ibrahim.", "Thank you, Ibrahim."))]),
    ]),
    # ================================================================ side: the first keel
    D("tema_pelabuhan_gong_sapa", GONG, ["quest %s baru" % QL, "pelabuhan_di_kota"], 40, [
        N("mulai", ("Kau memandangi landasanku seperti anak memandang manisan. Mau kapal sendiri? Perahu awan dari galangan ini "
                    "terbang lebih lurus dari burung camar. Pesan lunasnya, lalu kita beri dia nama yang pantas.",
                    "You stare at my slipway like a child at sweets. Want a ship of your own? A cloud boat from this yard flies "
                    "straighter than a gull. Order its keel, and then we give it a proper name."),
          "palu_lunas", "palu_dempul", pilihan=[
              C(("Aku mau kapal pertamaku.", "I want my first ship."), "terima", akibat=["quest_mulai " + QL]),
              C(BYE),
          ]),
        N("terima", ("Bagus! Pesan lewat layanan galangan: perahu awan 900, kapal sekte 4000 Nilai Batu. Lalu kembali padaku waktu "
                     "rangkanya sudah berdiri.", "Good! Order through the shipyard service: cloud boat 900, sect ship 4000 Nilai "
                     "Batu. Then come back to me once the frame stands."), "tertawa"),
    ]),
    D("tema_pelabuhan_gong_namai", GONG, [tahap(QL, "namai")], 45, [
        N("mulai", ("Kapal tanpa nama itu seperti anak tanpa ibu: laut tidak tahu harus memanggilnya apa. Pilih, bocah. Nama itu "
                    "kuukir di lunasnya sebelum kau mengambilnya.",
                    "A ship with no name is like a child with no mother: the sea doesn't know what to call it. Choose, child. I carve "
                    "the name on its keel before you take it."),
          "palu_lunas", "palu_dempul", pilihan=[
              C(("\"Camar Putih\"", "\"White Gull\""), "jadi", akibat=["pelabuhan_namai camar_putih", "quest_maju " + QL]),
              C(("\"Angin Selatan\"", "\"South Wind\""), "jadi", akibat=["pelabuhan_namai angin_selatan", "quest_maju " + QL]),
              C(("\"Pelita Kecil\"", "\"Little Lamp\""), "jadi", akibat=["pelabuhan_namai pelita_kecil", "quest_maju " + QL]),
              C(("\"Ombak Giok\"", "\"Jade Wave\""), "jadi", akibat=["pelabuhan_namai ombak_giok", "quest_maju " + QL]),
              C(("\"Bintang Fajar\"", "\"Dawn Star\""), "jadi", akibat=["pelabuhan_namai bintang_fajar", "quest_maju " + QL]),
              C(("\"Kura-kura Tua\"", "\"Old Turtle\""), "jadi", akibat=["pelabuhan_namai kura_kura_tua", "quest_maju " + QL]),
              C(("Biar kupikir dulu.", "Let me think first.")),
          ]),
        N("jadi", ("Hm! Nama yang baik. Laut akan mengingatnya. Ambil lunasmu kalau sudah jadi, dan jangan tabrakkan ke menara tambat!",
                   "Hm! A good name. The sea will remember it. Take your keel when it's done, and don't ram it into the mooring tower!"),
          "tertawa"),
    ]),
    D("tema_pelabuhan_gong_pesan", GONG, [tahap(QL, "pesan")], 42, [
        N("mulai", ("Belum pesan? Lihat layanan galangan, bocah. Aku tak membangun kapal dari omongan.",
                    "Not ordered yet? Look at the shipyard service, child. I don't build ships out of chatter."),
          "sikap_berkacak", pilihan=[C(("Baik, Mbah.", "All right, Granny."))]),
    ]),
    # ================================================================ flavour
    D("tema_pelabuhan_lin_sapa", LIN, ["pelabuhan_di_kota"], 10, [
        N("mulai", ("Kantor Urusan Laut. Bea sepersekian dari muatan, segel di setiap peti, dan tidak ada pedang yang terhunus di "
                    "dermaga. Itu aturan saya.", "Maritime Office. Customs are a fraction of the cargo, a seal on every crate, and "
                    "no drawn swords on the quays. Those are my rules."),
          "menulis", "papan_catatan", pilihan=[C(("Kabar apa di pelabuhan?", "What news in the harbour?"), "kabar"), C(BYE)]),
        N("kabar", "kabar", "sikap_tangan_belakang"),
    ]),
    D("tema_pelabuhan_zhao_sapa", ZHAO, ["pelabuhan_di_kota"], 10, [
        N("mulai", ("Zhao Wanli, kepala Dewan Saudagar, pelayanmu! Rempah, porselen, kaca dari negeri seberang... apa pun yang "
                    "kaucari, Dewan punya teman yang punya teman.", "Zhao Wanli, head of the Merchants' Council, at your service! "
                    "Spice, porcelain, glass from overseas... whatever you seek, the Council has a friend who has a friend."),
          "hormat", pilihan=[C(("Dengar-dengar apa saja?", "Heard anything lately?"), "kabar"), C(BYE)]),
        N("kabar", "kabar", "hitung_sempoa"),
    ]),
    D("tema_pelabuhan_gao_sapa", GAO, ["pelabuhan_di_kota"], 10, [
        N("mulai", ("Kapal Lintas Laut berangkat tengah hari, sekali sehari, kecuali badai. Kecuali badai! Aku tidak gila.",
                    "The crossing ship leaves at noon, once a day, except in storms. Except in storms! I am not mad."),
          "bicara_tangan", pilihan=[
              C(("Kudengar ada kapal lintas kedua.", "I hear there is a second crossing ship."), "kedua",
                syarat=["bendera pelabuhan_hasil_damai"]),
              C(("Apa kata para pelaut?", "What do the sailors say?"), "kabar"), C(BYE)]),
        N("kedua", ("Hiu Hitam! Menakhodai kapal lintas! Dan dia berlayar menembus kabut yang membuatku pulang ke kasur. Hmph. "
                    "Tapi dia membawakanku teh dari Pulau Pasar, jadi... aku tidak akan menggigitnya. Belum.",
                    "The Black Shark! Captaining a crossing ship! And he sails through fog that sends me home to bed. Hmph. But he "
                    "brings me tea from the Isle Market, so... I won't bite him. Not yet."),
          "bicara_tangan"),
        N("kabar", "kabar", "angguk_dengar"),
    ]),
    D("tema_pelabuhan_mei_sapa", MEI, ["pelabuhan_di_kota"], 30, [
        N("mulai", ("Selamat datang di Kuil Dewi Laut. Bakarlah sebatang dupa untuk mereka yang sedang di laut; asapnya naik, dan "
                    "Dewi melihat dari jauh. Pada hari kedua puluh tiga tiap bulan, beliau turun ke dermaga bersama kami.",
                    "Welcome to the Sea Goddess Temple. Burn a stick of incense for those at sea; the smoke rises and the Goddess "
                    "watches from afar. On the twenty-third day of each month she comes down to the quay with us."),
          "sikap_doa", "dupa", pilihan=[
              C(("Ceritakan tentang Dewi Laut.", "Tell me about the Sea Goddess."), "dewi"),
              C(("Bakarkan dupa untuk Hei Sha.", "Burn incense for Hei Sha."), "arwah", syarat=["bendera pelabuhan_hasil_tumpas"],
                akibat=["nama_baik 1"]),
              C(("Apakah Dewi memaafkan bajak laut?", "Does the Goddess forgive pirates?"), "maaf",
                syarat=["bendera pelabuhan_hasil_damai"]),
              C(("Kabar dari para peziarah?", "News from the pilgrims?"), "kabar"),
              C(BYE),
          ]),
        N("dewi", ("Dahulu beliau putri seorang nelayan yang bermimpi melihat ayah dan saudaranya tenggelam, lalu terbang dalam mimpi "
                   "untuk menarik mereka dari ombak. Dua pengawalnya berdiri di aula: Mata Seribu Li yang melihat setiap kapal, dan "
                   "Telinga Angin yang mendengar setiap doa.",
                   "Once she was a fisherman's daughter who dreamed of her father and brothers drowning, and flew in her dream to "
                   "pull them from the waves. Her two guardians stand in the hall: Thousand-Li Eye, who sees every ship, and Wind "
                   "Ear, who hears every prayer."),
          "sujud_dupa", "dupa"),
        N("arwah", ("Tiga batang, untuk tiga hal: untuk kapal-kapal yang dia tenggelamkan, untuk bocah dermaga yang dulu dia, dan "
                    "untuk tanganmu yang harus memutuskan. Laut menerima semuanya. Asapnya naik lurus hari ini; itu pertanda baik.",
                    "Three sticks, for three things: for the ships he sank, for the quay boy he once was, and for your hand that "
                    "had to decide. The sea accepts them all. The smoke rises straight today; that is a good sign."),
          "sujud_dupa", "dupa"),
        N("maaf", ("Dewi menarik ayah dan saudaranya dari ombak tanpa bertanya apakah mereka pernah berdosa. Dia sudah datang ke "
                   "kuil ini tiap pagi sebelum melaut, membersihkan abu dupa tanpa disuruh. Dewi melihatnya. Kami juga.",
                   "The Goddess pulled her father and brothers from the waves without asking whether they had ever sinned. He comes "
                   "to this temple every morning before he sails, sweeping the incense ash unasked. The Goddess sees him. So do we."),
          "sikap_doa", "dupa"),
        N("kabar", "kabar", "sikap_doa"),
    ]),
    D("tema_pelabuhan_awak", ["peran:pelaut", "peran:nelayan", "peran:penjual_ikan", "peran:tukang_kapal", "peran:kuli"],
      ["pelabuhan_di_kota"], 5, [
          N("mulai", ("Angin barat daya sudah datang, musim kapal rempah. Mau dengar kabar dermaga?",
                      "The south-west wind is here, spice-ship season. Want to hear the quay gossip?"),
            "ikat_tali", pilihan=[C(("Ceritakan.", "Tell me."), "kabar"), C(BYE)]),
          N("kabar", "kabar", "bicara_tangan"),
      ]),
    D("tema_pelabuhan_bea", ["peran:petugas_bea", "peran:syahbandar"], ["pelabuhan_di_kota"], 5, [
        N("mulai", ("Segel, surat jalan, timbangan. Barang dari luar kena bea dua per seratus; selundupan disita dan pemiliknya "
                    "didenda.", "Seal, pass, scales. Foreign goods pay two in a hundred; smuggled goods are seized and their owner fined."),
          "menulis", "papan_catatan", pilihan=[C(("Ada kabar?", "Any news?"), "kabar"), C(BYE)]),
        N("kabar", "kabar", "angguk_dengar"),
    ]),
    D("tema_pelabuhan_seberang", ["peran:pedagang_seberang", "peran:nakhoda_laut"], ["pelabuhan_di_kota"], 5, [
        N("mulai", ("Dari negeri seberang kami datang, ke negeri seberang kami pergi. Di antara keduanya, ada kota yang membeli "
                    "apa saja.", "From lands overseas we come, to lands overseas we go. In between, there is a city that buys anything."),
          "hormat_dalam", pilihan=[C(("Kabar dari laut?", "News from the sea?"), "kabar"), C(BYE)]),
        N("kabar", "kabar", "bicara_tangan"),
    ]),
]


# ========================================================================================== quests and rumours

TEMPAT = "tema_pelabuhan"
TEMPAT_TEKS = ("Kota Pelabuhan", "Harbour City")


def S(sid, baris, **kw):
    """A stage: id, journal line (id, en), JSON fields."""
    d = {"id": sid}
    d.update(kw)
    return (d, baris)


QUESTS = [
    {
        "id": Q, "pemberi": "tokoh:deng_ming",
        "nama": ("Pelita yang Padam", "The Dimming Lamp"),
        "ringkas": ("Pelita Penunjuk Angin di mercusuar meredup dan kapal-kapal hilang di kabut. Kakek Deng Ming, penjaga suar "
                    "yang setengah tuli, meminta tolong.",
                    "The Wind-Guiding Lamp in the lighthouse is dimming and ships vanish in the fog. Grandpa Deng Ming, the "
                    "half-deaf lighthouse keeper, asks for help."),
        "tahap": [
            S("redup", ("Naiki mercusuar dan lihat lensa Pelita Angin dari dekat, lalu bicaralah dengan Syahbandar Lin Haichao "
                        "di Kantor Urusan Laut.",
                        "Climb the lighthouse and look at the Wind Lamp's lens up close, then speak with Harbourmaster Lin "
                        "Haichao at the Maritime Office."),
              tujuan=["pelabuhan_suar lihat", "bicara tokoh:lin_haichao"], akibat=["nama_baik 2"], batas_hari=5,
              gagal_ke="catatan", arah="kota_tema/pelabuhan"),
            S("catatan", ("Periksa catatan bea di meja Lin Haichao: satu dari tiga surat muatan kapal yang hilang itu palsu.",
                          "Examine the customs records on Lin Haichao's desk: one of the three manifests of the lost ships is forged."),
              tujuan=["pelabuhan_dokumen benar"], batas_hari=5, gagal_ke="lapor"),
            S("lapor", ("\"Minyak lampu\" itu Minyak Bayangan. Laporkan pada Lin Haichao, atau dengar dulu apa kata Zhao Wanli "
                        "dari Dewan Saudagar.",
                        "That \"lamp oil\" is Shadow Oil. Report it to Lin Haichao, or first hear what Zhao Wanli of the "
                        "Merchants' Council has to say."),
              batas_hari=5, gagal_ke="lensa"),
            S("lensa", ("Lensa baru butuh Kaca Garam Laut: racik di tungku Mbah Lunas, beli dari Ibrahim, atau temani Ibrahim "
                        "semalam mengamati bintang. Serahkan kacanya pada Mbah Lunas.",
                        "A new lens needs Sea-Salt Glass: make it at Granny Keel's kiln, buy it from Ibrahim, or keep Ibrahim "
                        "company for a night of stargazing. Hand the glass to Granny Keel."),
              batas_hari=5, gagal_ke="serbuan"),
            S("serbuan", ("Pelita menyala terang, dan sesuatu di laut datang mencarinya. Pertahankan dermaga malam ini.",
                          "The lamp burns bright, and something out at sea is coming for it. Defend the quays tonight."),
              akibat_masuk=["pelabuhan_pelita 1", "pelabuhan_serbuan"], tujuan=["pelabuhan_bajak *"],
              akibat=["nama_baik 5", "kabar tema_pelabuhan_bab4 pemain"], batas_hari=5, gagal_ke="mutiara"),
            S("mutiara", ("Mutiara Pelita sudah mati. Naik Kapal Lintas Laut (gratis kali ini) ke Laut Tanpa Batas, dapatkan "
                          "Mutiara Fajar dari pedagang pulau atau dari ular laut, dan bawa pulang ke kota.",
                          "The Lamp Pearl is dead. Take the crossing ship (free this time) to the Boundless Sea, get a Dawn Pearl "
                          "from an island merchant or a sea serpent, and bring it home to the city."),
              akibat_masuk=["bendera pelabuhan_lintas_gratis", "beri kultivator:tema/mutiara_pelita"],
              tujuan=["punya kultivator:tema/mutiara_fajar", "pelabuhan_di_kota"], ke="duel", batas_hari=10, gagal_ke="titipan"),
            S("titipan", ("Ibrahim menitipkan Mutiara Fajar lewat kafilahnya. Kembalilah ke kota pelabuhan.",
                          "Ibrahim sent a Dawn Pearl with his caravan. Return to the harbour city."),
              akibat_masuk=["beri kultivator:tema/mutiara_fajar", "kabar tema_pelabuhan_titipan"], tujuan=["pelabuhan_di_kota"],
              ke="duel"),
            S("duel", ("Kapten Hiu Hitam menunggu di Pemecah Ombak. Kalahkan dia, atau bicaralah dulu dan tantang dia Duel Pedang "
                       "secara terhormat.",
                       "Captain Black Shark waits on the Breakwater. Defeat him, or talk first and challenge him to an honourable "
                       "Duel of Swords."),
              akibat_masuk=["pelabuhan_heisha"], tujuan=["pelabuhan_heisha kalah"], batas_hari=5, gagal_ke="upacara"),
            S("nasib", ("Hei Sha berlutut di papan pemecah ombak. Putuskan nasibnya: serahkan pada Lin Haichao, damaikan dengan "
                        "gurunya, atau akhiri lautnya.",
                        "Hei Sha kneels on the breakwater planks. Decide his fate: hand him to Lin Haichao, reconcile him with his "
                        "master, or end his sea."),
              batas_hari=3, gagal_ke="upacara"),
            S("upacara", ("Bawa Mutiara Fajar kepada Kakek Deng dan nyalakan kembali Pelita Penunjuk Angin.",
                          "Bring the Dawn Pearl to Grandpa Deng and relight the Wind-Guiding Lamp."), ke="selesai"),
        ],
        "hasil": {"upacara": ("Pelita Penunjuk Angin menyala biru-emas dan sinarnya sampai ke Karang Seribu lagi. Kau kini Penjaga "
                              "Pelita: Pasar Seberang memberimu potongan, Kapal Lintas Laut separuh harga, dan Hari Dewi Laut "
                              "berikutnya diadakan atas namamu.",
                              "The Wind-Guiding Lamp burns blue and gold and its beam reaches the Thousand Reefs again. You are now "
                              "a Keeper of the Light: the Overseas Market gives you a discount, the crossing ship half fare, and "
                              "the next Sea Goddess Day is held in your name.")},
    },
    {
        "id": QI, "pemberi": "tokoh:bibi_yu",
        "nama": ("Sup untuk Sang Dewi", "Soup for the Goddess"),
        "ringkas": ("Bibi Yu, penjual ikan di Pasar Ikan Pagi, butuh sepuluh Ikan Roh untuk sup Hari Dewi Laut.",
                    "Auntie Yu of the Morning Fish Market needs ten Spirit Fish for the Sea Goddess Day soup."),
        "tahap": [
            S("kumpul", ("Pancing sepuluh Ikan Roh di teluk (sewa perahu di Tambatan Nelayan).",
                         "Catch ten Spirit Fish in the bay (hire a boat at the Fishers' Moorings)."),
              tujuan=["punya kultivator:tema/ikan_roh 10"]),
            S("serah", ("Bawa sepuluh Ikan Roh ke lapak Bibi Yu di Pasar Ikan Pagi.",
                        "Bring the ten Spirit Fish to Auntie Yu's stall at the Morning Fish Market."), ke="selesai"),
        ],
        "hasil": {"serah": ("Sup Dewi mendidih di dermaga, harum jahe dan laut. Bibi Yu menyebut namamu pada setiap mangkuk.",
                            "The Goddess's soup simmers on the quay, smelling of ginger and sea. Auntie Yu says your name over "
                            "every bowl.")},
    },
    {
        "id": QT, "pemberi": "tokoh:a_lan",
        "nama": ("Tali yang Putus-putus", "The Frayed Rope"),
        "ringkas": ("A-Lan, gadis awak Kedai Jangkar Patah, ingin jadi nakhoda. Syahbandar baru memberi restu bila ia pernah "
                    "bertanding di teluk melawan lawan yang hebat.",
                    "A-Lan, the deckhand of the Broken Anchor Tavern, wants to be a captain. The Harbourmaster will only bless "
                    "her once she has raced a worthy rival in the bay."),
        "tahap": [
            S("lomba", ("Kalahkan A-Lan dalam lomba perahu di teluk: putaran latihan atau Lomba Perahu Hari Dewi Laut.",
                        "Beat A-Lan in a boat race in the bay: a practice lap or the Sea Goddess Day Boat Race."),
              tujuan=["pelabuhan_lomba kalahkan_alan"]),
            S("restu", ("Ceritakan pada Lin Haichao bagaimana A-Lan bertanding.", "Tell Lin Haichao how A-Lan raced."),
              tujuan=["bicara tokoh:lin_haichao"],
              akibat=["nama_baik 3", "bendera pelabuhan_alan_nakhoda", "kabar tema_pelabuhan_alan pemain"], ke="selesai"),
        ],
        "hasil": {"restu": ("A-Lan mendapat restu Syahbandar. Tali tambatnya kini utuh: suatu hari ia akan berdiri di geladak "
                            "kapalnya sendiri.",
                            "A-Lan has the Harbourmaster's blessing. Her mooring rope is whole now: one day she will stand on "
                            "the deck of her own ship.")},
    },
    {
        "id": QB, "pemberi": "tokoh:ibrahim",
        "nama": ("Tiga Malam Bintang", "Three Nights of Stars"),
        "ringkas": ("Ibrahim dari Negeri Barat menyalin bintang-bintang langit timur ke dalam bukunya dan meminta teman jaga.",
                    "Ibrahim of the Western Lands is copying the eastern stars into his book and asks for a companion on watch."),
        "tahap": [
            S("malam", ("Tiga malam berbeda, tunggulah di puncak Pagoda Penunjuk selepas senja sampai pengamatan selesai.",
                        "On three different nights, wait at the top of the Guiding Pagoda after dusk until the watch is done."),
              akibat_masuk=["pelabuhan_bintang"], tujuan=["pelabuhan_bintang malam x3"],
              akibat=["pelabuhan_hadiah astrolab", "nama_baik 2"], ke="selesai"),
        ],
        "hasil": {"malam": ("Ibrahim menghadiahkan Astrolab Kecil yang selalu menunjuk utara sejati. Bukunya kini punya satu bab "
                            "tentang langit timur.",
                            "Ibrahim gives you a Small Astrolabe that always points to true north. His book now has a chapter "
                            "on the eastern sky.")},
    },
    {
        "id": QL, "pemberi": "tokoh:gong_shumu",
        "nama": ("Lunas Pertama", "The First Keel"),
        "ringkas": ("Mbah Lunas mau membangun kapal pertamamu di galangan, dan memberinya nama bersamamu.",
                    "Granny Keel wants to build your first ship at the shipyard, and name it together with you."),
        "tahap": [
            S("pesan", ("Pesan lunas perahu awan atau kapal sekte lewat layanan galangan Mbah Lunas.",
                        "Order a cloud boat or sect ship keel through Granny Keel's shipyard service."),
              tujuan=["pelabuhan_lunas_pesan"]),
            S("namai", ("Pilih nama kapalmu bersama Mbah Lunas sebelum lunasnya diambil.",
                        "Choose your ship's name with Granny Keel before the keel is collected."), batas_hari=4, gagal_ke="ambil"),
            S("ambil", ("Tunggu sampai kapalmu selesai di landasan, lalu ambil lunasnya di galangan.",
                        "Wait until your ship is finished on the slipway, then collect its keel at the shipyard."),
              tujuan=["bendera pelabuhan_lunas_diambil"], akibat=["nama_baik 2", "kabar tema_pelabuhan_lunas pemain"], ke="selesai"),
        ],
        "hasil": {"ambil": ("Lunas pertamamu meninggalkan galangan dengan nama terukir di haluannya. Mbah Lunas pura-pura tidak "
                            "menyeka matanya.",
                            "Your first keel leaves the yard with its name carved on the bow. Granny Keel pretends not to wipe "
                            "her eyes.")},
    },
]

# rumours: id, pemicu ("" = id itself; "umum" = background), weight, days, spreader roles, text (with %s from the spreader)
HARBOUR = ["pelaut", "nelayan", "penjual_ikan", "nakhoda_laut", "pedagang_seberang", "penjaga_suar", "syahbandar",
           "petugas_bea", "tukang_kapal", "pendeta_laut", "kuli"]
KABAR = [
    ("tema_pelabuhan_kapal_tiba_selatan", "", 12, 3, [],
     ("Kapal dagang dari Negeri Rempah Selatan baru buang jangkar di Dermaga Agung. Muatannya dijual di geladak, siapa cepat dia dapat.",
      "A merchant ship from the Southern Spice Lands just dropped anchor at the Grand Quay. Its cargo is sold on deck, first come first served.")),
    ("tema_pelabuhan_kapal_tiba_mutiara", "", 12, 3, [],
     ("Kapal dagang dari Kepulauan Mutiara baru buang jangkar di Dermaga Agung. Muatannya dijual di geladak, siapa cepat dia dapat.",
      "A merchant ship from the Pearl Isles just dropped anchor at the Grand Quay. Its cargo is sold on deck, first come first served.")),
    ("tema_pelabuhan_kapal_tiba_barat", "", 12, 3, [],
     ("Kapal dagang dari Negeri Barat baru buang jangkar di Dermaga Agung. Muatannya dijual di geladak, siapa cepat dia dapat.",
      "A merchant ship from the Western Lands just dropped anchor at the Grand Quay. Its cargo is sold on deck, first come first served.")),
    ("tema_pelabuhan_kapal_tiba_kota", "", 12, 3, [],
     ("Kapal dagang dari kota pelabuhan lain di pesisir baru buang jangkar di Dermaga Agung. Muatannya dijual di geladak, siapa cepat dia dapat.",
      "A merchant ship from another harbour city down the coast just dropped anchor at the Grand Quay. Its cargo is sold on deck, first come first served.")),
    ("tema_pelabuhan_dewi_laut", "", 14, 2, [],
     ("Hari Dewi Laut! Tandu sang Dewi turun ke dermaga, lalu ada lomba perahu nelayan di teluk.",
      "Sea Goddess Day! The Goddess's palanquin comes down to the quay, then there's a fishing boat race in the bay.")),
    ("tema_pelabuhan_bajak_datang", "", 16, 2, [],
     ("Jung berlayar hitam menyerbu Dermaga Agung! Bajak laut Hiu Hitam lagi!",
      "A black-sailed junk is raiding the Grand Quay! Black Shark's pirates again!")),
    ("tema_pelabuhan_bajak_menang", "", 12, 4, [],
     ("Bajak laut dipukul mundur dari dermaga semalam. Para penjaga minum arak sampai pagi.",
      "The pirates were driven off the quays last night. The guards drank wine till morning.")),
    ("tema_pelabuhan_bajak_kalah", "", 12, 4, [],
     ("Bajak laut menjarah gudang semalam. Pasar Seberang tutup, dan satu kapal dagang tak berani datang.",
      "Pirates looted the warehouses last night. The Overseas Market is shut, and one merchant ship dares not come.")),
    ("tema_pelabuhan_lomba_juara", "", 10, 5, [],
     ("%s memenangi Lomba Perahu Nelayan dan membawa pulang Jimat Ombak!",
      "%s won the Fishing Boat Race and took home the Wave Charm!")),
    ("tema_pelabuhan_heisha_serah", "", 14, 8, [],
     ("Kapten Hiu Hitam diserahkan pada Syahbandar oleh %s. Kantor Urusan Laut akan mengadilinya.",
      "Captain Black Shark was handed to the Harbourmaster by %s. The Maritime Office will try him.")),
    ("tema_pelabuhan_heisha_damai", "", 14, 8, [],
     ("Kau dengar? Hiu Hitam pulang ke gurunya, Kakek Deng, setelah berduel dengan %s. Katanya dia akan menakhodai kapal "
      "lintas laut kedua.",
      "Did you hear? The Black Shark went home to his master, Grandpa Deng, after a duel with %s. They say he will captain a "
      "second crossing ship.")),
    ("tema_pelabuhan_heisha_tumpas", "", 14, 8, [],
     ("Hiu Hitam tewas di Pemecah Ombak oleh pedang %s. Para pelaut bersyukur... tapi ada yang bilang laut tak suka darah.",
      "The Black Shark died on the Breakwater by the sword of %s. The sailors are grateful... but some say the sea dislikes blood.")),
    ("tema_pelabuhan_minyak", "", 12, 5, [],
     ("%s membongkar penyelundupan Minyak Bayangan! Syahbandar menyita peti-peti di Gudang Bersama.",
      "%s uncovered the Shadow Oil smuggling! The Harbourmaster is seizing casks in the Shared Warehouse.")),
    ("tema_pelabuhan_lensa", "", 10, 4, [],
     ("Mercusuar terang lagi! Mbah Lunas memasang lensa baru yang dibawa %s.",
      "The lighthouse is bright again! Granny Keel fitted a new lens brought by %s.")),
    ("tema_pelabuhan_bab4", "", 10, 4, [],
     ("%s ikut menjaga dermaga waktu bajak laut datang mencari Mutiara Pelita.",
      "%s stood guard on the quays when the pirates came for the Lamp Pearl.")),
    ("tema_pelabuhan_titipan", "", 8, 3, [],
     ("Kafilah Ibrahim datang membawa bungkusan untuk Kakek Deng. Isinya bercahaya seperti fajar.",
      "Ibrahim's caravan arrived with a parcel for Grandpa Deng. Whatever is inside glows like dawn.")),
    ("tema_pelabuhan_pelita_nyala", "", 18, 10, [],
     ("Pelita Penunjuk Angin menyala biru-emas! %s menyalakannya bersama Kakek Deng. Kapal-kapal pulang lagi.",
      "The Wind-Guiding Lamp burns blue and gold! %s lit it with Grandpa Deng. The ships are coming home again.")),
    ("tema_pelabuhan_alan", "", 8, 5, [],
     ("A-Lan dapat restu jadi nakhoda setelah bertanding perahu dengan %s.",
      "A-Lan got her blessing to become a captain after racing %s.")),
    ("tema_pelabuhan_lunas", "", 8, 4, [],
     ("Mbah Lunas meluncurkan kapal baru milik %s. Namanya terukir di haluan.",
      "Granny Keel launched a new ship belonging to %s. Its name is carved on the bow.")),
    ("tema_pelabuhan_sup", "", 8, 4, [],
     ("Sup Ikan Roh Bibi Yu tahun ini istimewa: %s yang memancing ikannya.",
      "Auntie Yu's Spirit Fish soup is special this year: %s caught the fish.")),
    # second voices for the chain's turning points (fired by id next to the first voice, from the same choice)
    ("tema_pelabuhan_heisha_serah_2", "", 10, 8, HARBOUR,
     ("Hiu Hitam digiring ke Kantor Urusan Laut dengan tangan terikat tali tambatnya sendiri. Dia tidak melawan sekali pun. "
      "%s berjalan di belakangnya sampai ke pintu.",
      "The Black Shark was led to the Maritime Office with his hands tied by his own mooring rope. He did not resist once. "
      "%s walked behind him all the way to the door.")),
    ("tema_pelabuhan_heisha_damai_2", "", 10, 8, HARBOUR,
     ("Hiu Hitam berlutut di kaki mercusuar, dan Kakek Deng memeluknya seperti menarik jala yang berat. Para nelayan pura-pura "
      "tidak menangis. %s yang mempertemukan mereka.",
      "The Black Shark knelt at the foot of the lighthouse, and Grandpa Deng hugged him like hauling in a heavy net. The fishers "
      "pretended not to cry. %s brought them together.")),
    ("tema_pelabuhan_heisha_tumpas_2", "", 10, 8, HARBOUR,
     ("Malam itu Kakek Deng menyalakan lampu terlambat. Lalu dia menaruh satu pelita kecil di ujung Pemecah Ombak, menghadap ke "
      "laut. Tidak ada yang berani bertanya untuk siapa, apalagi pada %s.",
      "That night Grandpa Deng lit the lamp late. Then he set one little lamp at the end of the Breakwater, facing the sea. Nobody "
      "dares ask who it is for, least of all %s.")),
    ("tema_pelabuhan_pelita_nyala_2", "", 12, 10, HARBOUR,
     ("Begitu Pelita menyala, semua kapal di teluk membunyikan lonceng bersama-sama. Bibi Yu menangis di lapaknya, dan %s berdiri "
      "di puncak menara di samping Kakek Deng.",
      "The moment the Lamp was lit, every ship in the bay rang its bell at once. Auntie Yu wept at her stall, and %s stood at the "
      "top of the tower beside Grandpa Deng.")),
    ("tema_pelabuhan_minyak_2", "", 8, 5, ["petugas_bea", "kuli", "syahbandar"],
     ("Peti \"minyak lampu\" di Gudang Bersama ternyata Minyak Bayangan. Asapnya hitam dan berbau karat. %s yang pertama mencium "
      "baunya.",
      "The \"lamp oil\" casks in the Shared Warehouse turned out to be Shadow Oil. Its smoke is black and smells of rust. %s was "
      "the first to smell it.")),
    # background talk of harbour folk
    ("tema_pelabuhan_umum_keruk", "umum", 4, 3, ["kuli", "tukang_kapal", "nelayan"],
     ("Kapal keruk pelabuhan menelan lumpur sehari semalam. Tanpa dia, alur Dermaga Agung dangkal dalam satu musim.",
      "The harbour dredge swallows mud day and night. Without it, the Grand Quay channel would silt up in one season.")),
    ("tema_pelabuhan_umum_camar", "umum", 4, 3, ["nelayan", "penjual_ikan", "pelaut"],
     ("Kalau camar terbang rendah di atas Pasar Ikan Pagi, besok ada angin. Kalau mereka diam di atap gudang, besok ada badai.",
      "When the gulls fly low over the Morning Fish Market, there's wind tomorrow. When they sit still on the warehouse roofs, "
      "a storm.")),
    ("tema_pelabuhan_umum_alan", "umum", 4, 3, ["pelaut", "nelayan", "tukang_kapal"],
     ("A-Lan mengoles dayungnya dengan minyak ikan tiap pagi. Katanya dayung yang licin membelah air lebih cepat. Baunya "
      "sampai ke kuil.",
      "A-Lan oils her oar with fish oil every morning. She says a slick oar cuts water faster. You can smell it at the temple.")),
    ("tema_pelabuhan_umum_laut", "umum", 6, 3, HARBOUR,
     ("Kata Kapten Gao, di Laut Tanpa Batas ada pulau yang pasarnya pindah tiap pasang surut.",
      "Captain Gao says in the Boundless Sea there is an island whose market moves with every tide.")),
    ("tema_pelabuhan_umum_suar", "umum", 6, 3, HARBOUR,
     ("Kakek Deng sudah tiga puluh tahun menjaga suar. Dia bicara pada lampunya seperti pada cucu.",
      "Grandpa Deng has kept the light for thirty years. He talks to his lamp like a grandchild.")),
    ("tema_pelabuhan_umum_hiu", "umum", 5, 3, HARBOUR,
     ("Hiu Hitam dulu bocah dermaga biasa. Katanya dia hafal setiap karang di teluk dengan mata tertutup.",
      "The Black Shark was once an ordinary quay boy. They say he knew every reef in the bay blindfolded.")),
    ("tema_pelabuhan_umum_zhao", "umum", 5, 3, ["pelaut", "kuli", "petugas_bea", "pedagang_seberang"],
     ("Zhao Wanli berutang pada setengah kota, dan setengah kota lagi berutang padanya.",
      "Zhao Wanli owes half the city, and the other half owes him.")),
    ("tema_pelabuhan_umum_lunas", "umum", 5, 3, ["tukang_kapal", "pelaut", "kuli"],
     ("Mbah Lunas memaki semua orang, tapi tak satu pun kapalnya pernah bocor.",
      "Granny Keel curses everyone, but not one of her ships has ever leaked.")),
    ("tema_pelabuhan_umum_ibrahim", "umum", 5, 3, ["pedagang_seberang", "nakhoda_laut", "pelaut"],
     ("Ibrahim dari Negeri Barat membaca bintang lebih cepat dari peta. Nakhoda tua pun bertanya padanya.",
      "Ibrahim of the Western Lands reads the stars faster than a chart. Even old captains ask him.")),
    ("tema_pelabuhan_umum_ikan", "umum", 6, 3, ["nelayan", "penjual_ikan"],
     ("Pasar Ikan Pagi buka sebelum ayam berkokok dan tutup sebelum matahari panas. Datang terlambat, dapat tulang.",
      "The Morning Fish Market opens before the cock crows and shuts before the sun gets hot. Come late, get bones.")),
    ("tema_pelabuhan_umum_badai", "umum", 5, 3, ["nelayan", "pelaut", "nakhoda_laut", "pendeta_laut"],
     ("Kalau langit menggeram, perahu tetap di tambatan. Dewi Laut tak menolong orang yang menantang badai.",
      "When the sky growls, the boats stay moored. The Sea Goddess does not save those who dare a storm.")),
    ("tema_pelabuhan_umum_kompas", "umum", 4, 3, ["nakhoda_laut", "pedagang_seberang"],
     ("Kapal dari Kepulauan Mutiara kadang membawa Kompas Pasang. Jarumnya menunjuk pusaran laut, bukan utara.",
      "Ships from the Pearl Isles sometimes carry a Tide Compass. Its needle points to a sea whirlpool, not north.")),
    ("tema_pelabuhan_umum_dewi", "umum", 5, 3, ["pendeta_laut", "nelayan", "penjual_ikan"],
     ("Hari kedua puluh tiga tiap bulan, Dewi Laut turun ke dermaga. Jangan melaut pagi itu; ikut arak-arakannya saja.",
      "On the twenty-third of each month the Sea Goddess comes down to the quay. Don't fish that morning; join the procession."))
]


# ========================================================================================== writers
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = REPO + "/src/main/resources/data/kultivator"
JAVA = REPO + "/src/main/java/dev/kultivator"
LANG = REPO + "/lang-src"

L = {}
ERR = []


def lang(key, idn, en):
    if key in L:
        ERR.append("duplicate key " + key)
    if idn.count("%") != en.count("%"):
        ERR.append("format count differs " + key)
    L[key] = (idn, en)


def tulis(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(obj, ensure_ascii=False, indent=2) + "\n")


def nama_java(path):
    src = open(path, encoding="utf-8").read()
    return {m.lower() for m in re.findall(r"public static final int ([A-Z0-9_]+) = ", src)}


GERAK = nama_java(JAVA + "/sosok/GerakId.java")
PROP = nama_java(JAVA + "/sosok/PropId.java")


def cek_gerak(nama, where):
    if nama and nama not in GERAK and ("sikap_" + nama) not in GERAK:
        ERR.append("unknown clip %s in %s" % (nama, where))


def cek_prop(nama, where):
    if nama and nama not in PROP:
        ERR.append("unknown prop %s in %s" % (nama, where))


# ====================================================================== dialogs
def dialogs():
    out_dir = RES + "/tempat/dialog"
    for d in DIALOG:
        did = d["id"]
        assert did.startswith("tema_pelabuhan_"), did
        nodes = []
        ids = {n["id"] for n in d["simpul"]}
        for n in d["simpul"]:
            o = {"id": n["id"]}
            if n["teks"] == "kabar":
                o["teks"] = "kabar"
            else:
                lang("kultivator.cerita.%s.%s" % (did, n["id"]), *n["teks"])
            if n["isian"]:
                o["isian"] = n["isian"]
            if n["gestur"]:
                cek_gerak(n["gestur"], did)
                o["gestur"] = n["gestur"]
            if n["prop"]:
                cek_prop(n["prop"], did)
                o["prop"] = n["prop"]
            if n["akibat"]:
                o["akibat"] = n["akibat"]
            pil = []
            for i, c in enumerate(n["pilihan"], 1):
                p = {}
                lang("kultivator.cerita.%s.%s.%d" % (did, n["id"], i), *c["teks"])
                if c["syarat"]:
                    p["syarat"] = c["syarat"]
                if c["akibat"]:
                    p["akibat"] = c["akibat"]
                if c["ke"]:
                    if c["ke"] not in ids:
                        ERR.append("dialog %s: choice to unknown node %s" % (did, c["ke"]))
                    p["ke"] = c["ke"]
                if c["tampil"]:
                    p["tampil"] = True
                    if c["alasan"]:
                        key = "kultivator.cerita.%s.%s.%d.alasan" % (did, n["id"], i)
                        lang(key, *c["alasan"])
                        p["alasan"] = key
                pil.append(p)
            if pil:
                o["pilihan"] = pil
            nodes.append(o)
        obj = {"pembicara": d["pembicara"], "prioritas": d["prioritas"]}
        if d["syarat"]:
            obj["syarat"] = d["syarat"]
        if d["sekali"]:
            obj["sekali"] = True
        obj["mulai"] = d["simpul"][0]["id"]
        obj["simpul"] = nodes
        tulis("%s/%s.json" % (out_dir, did), obj)
    return len(DIALOG)


# ====================================================================== quests
def quests():
    lang("kultivator.cerita.tempat." + TEMPAT, *TEMPAT_TEKS)
    for q in QUESTS:
        qid = q["id"]
        assert qid.startswith("tema_pelabuhan"), qid
        lang("kultivator.cerita.quest.%s.nama" % qid, *q["nama"])
        lang("kultivator.cerita.quest.%s.ringkas" % qid, *q["ringkas"])
        stages = []
        ids = [s[0]["id"] for s in q["tahap"]]
        for st, baris in q["tahap"]:
            lang("kultivator.cerita.quest.%s.%s" % (qid, st["id"]), *baris)
            for k in ("ke", "gagal_ke"):
                if k in st and st[k] not in ids + ["selesai", "gagal"]:
                    ERR.append("quest %s: %s %s unknown" % (qid, k, st[k]))
            stages.append(st)
        for sid, teks in q["hasil"].items():
            lang("kultivator.cerita.quest.%s.hasil.%s" % (qid, sid), *teks)
        tulis("%s/tempat/quest/%s.json" % (RES, qid),
              {"pemberi": q["pemberi"], "tempat": TEMPAT, "tahap": stages})
    return len(QUESTS)


# ====================================================================== rumours
def kabar():
    tpl = []
    for kid, pemicu, bobot, umur, peran, teks in KABAR:
        o = {"id": kid, "pemicu": pemicu or kid, "bobot": bobot, "umur_hari": umur}
        if peran:
            o["peran_penyebar"] = peran
        tpl.append(o)
        lang("kultivator.cerita.kabar." + kid, *teks)
    tulis(RES + "/tempat/kabar/tema_pelabuhan.json", {"templat": tpl})
    return len(tpl)


# ====================================================================== misc story lang
ADEGAN = [
    ("tema_pelabuhan_lintas", "Pelayaran ke Laut Tanpa Batas", "Voyage to the Boundless Sea"),
    ("tema_pelabuhan_dewi", "Sembahyang Dewi Laut", "Prayers to the Sea Goddess"),
    ("tema_pelabuhan_bajak", "Layar Hitam di Teluk", "Black Sails in the Bay"),
    ("tema_pelabuhan_heisha", "Kapten Hiu Hitam", "Captain Black Shark"),
    ("tema_pelabuhan_pelita", "Pelita Angin Menyala Kembali", "The Wind Lamp Burns Again"),
]


def cerita_lain():
    for aid, i, e in ADEGAN:
        lang("kultivator.cerita.adegan." + aid, i, e)
    lang("kultivator.cerita.gelar.penjaga_pelita", "Penjaga Pelita", "Keeper of the Light")


# ====================================================================== advancements (theme extras, granted in code)
ADV = [
    ("tema_pelabuhan_lunas", "minecraft:oak_boat", "task", ("Lunas dari Galangan", "A Keel from the Yard"),
     ("Ambil lunas kapal pesananmu dari galangan Mbah Lunas.", "Collect the keel you ordered from Granny Keel's shipyard.")),
    ("tema_pelabuhan_lomba", "minecraft:prismarine_crystals", "goal", ("Juara Teluk", "Champion of the Bay"),
     ("Menangi Lomba Perahu Nelayan pada Hari Dewi Laut.", "Win the Fishing Boat Race on Sea Goddess Day.")),
    ("tema_pelabuhan_bajak", "minecraft:iron_sword", "goal", ("Dermaga Tak Tertembus", "Unbroken Quays"),
     ("Patahkan Serbuan Bajak Laut di Kota Pelabuhan.", "Break a Pirate Raid on a Harbour City.")),
]


def advancements():
    for aid, icon, frame, title, desc in ADV:
        tulis("%s/advancement/tempat/%s.json" % (RES, aid), {
            "parent": "kultivator:tempat/tema_pelabuhan",
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {
                "description": {"translate": "advancement.kultivator.tempat.%s.description" % aid},
                "icon": {"id": icon},
                "title": {"translate": "advancement.kultivator.tempat.%s.title" % aid},
                "frame": frame,
            },
            "requirements": [["done"]],
        })
        lang("advancement.kultivator.tempat.%s.title" % aid, *title)
        lang("advancement.kultivator.tempat.%s.description" % aid, *desc)
    return len(ADV)


# ====================================================================== loot: the pirate junk's spoils
def item(name, w, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    if hi > 1:
        # 26.3: one "modifier" per entry (LootPoolEntryContainer), as the inti tables write it
        e["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}
    return e


def loot():
    tulis(RES + "/loot_table/tema/pelabuhan/bajak_laut.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                item("kultivator:spirit_stone_low", 30, 2, 6),
                item("kultivator:spirit_stone_mid", 10, 1, 2),
                item("minecraft:gold_nugget", 18, 3, 9),
                item("minecraft:gold_ingot", 8, 1, 3),
                item("kultivator:dagang/rempah_seberang", 12, 1, 3),
                item("kultivator:dagang/porselen_hijau", 8, 1, 2),
                item("kultivator:dagang/kaca_warna", 8, 1, 2),
                item("kultivator:dagang/mutiara_laut", 5),
                item("kultivator:dagang/minyak_bayangan", 6, 1, 2),
                item("kultivator:dagang/ikan_asin_roh", 8, 1, 3),
                item("minecraft:nautilus_shell", 6),
                item("minecraft:prismarine_shard", 8, 2, 5),
                item("kultivator:healing_pill", 6, 1, 2),
            ]},
            {"rolls": 1, "entries": [
                {"type": "minecraft:empty", "weight": 17},
                item("kultivator:tema/kompas_pasang", 2),
                item("minecraft:heart_of_the_sea", 1),
            ]},
        ],
    })


# ====================================================================== schedule data (roles the built-ins leave out)
def e(kegiatan, tujuan, klip="", kanan="", kiri="", punggung="", jalan="", dalam=False):
    for c in (klip, jalan):
        cek_gerak(c, "jadwal")
    for p in (kanan, kiri, punggung):
        cek_prop(p, "jadwal")
    o = {"kegiatan": kegiatan, "tujuan": tujuan}
    for k, v in (("klip", klip), ("kanan", kanan), ("kiri", kiri), ("punggung", punggung), ("jalan", jalan)):
        if v:
            o[k] = v
    if dalam:
        o["dalam"] = True
    return o


def jadwal():
    tidur = e("tidur", "@kasur", "tidur", dalam=True)
    basuh = e("basuh_muka", "@rumah", "basuh_muka")
    makan = e("makan", "meja_makan", "makan", "sumpit", "mangkuk")
    obj = {
        "jenis": "kota_tema/pelabuhan",
        "peran": {
            # Syahbandar Lin's day: dawn spyglass on the office balcony, manifests, the quays in the afternoon, tea
            "syahbandar": {
                "22-4": tidur, "5": basuh,
                "6-7": e("teropong", "@kerja", "teropong_laut", "teropong"),
                "8-11": e("surat_muatan", "meja_tulis", "menulis", "kuas"),
                "12-13": makan,
                "14-17": e("periksa_muatan", "tambatan", "menulis", "papan_catatan"),
                "18-21": e("minum_teh", "kursi", "minum_teh", "cangkir"),
            },
            # customs clerks: the scales in the morning, the ledgers after lunch
            "petugas_bea": {
                "22-4": tidur, "5": basuh,
                "6-11": e("periksa_muatan", "kerja:timbangan", "menulis", "papan_catatan"),
                "12": makan,
                "13-17": e("surat_muatan", "meja_tulis", "menulis", "kuas"),
                "18-21": e("minum_teh", "kursi", "minum_teh", "cangkir"),
            },
            # porters: crates from the berths to the warehouse racks, sea shanties at night
            "kuli": {
                "22-4": tidur, "5": basuh,
                "6-11": e("bongkar_kapal", "tambatan", "angkat_bawa", "peti", jalan="jalan_bawa"),
                "12-13": e("makan_dermaga", "tambatan", "makan", "sumpit", "mangkuk"),
                "14-17": e("angkat_karung", "kerja:rak", "rak_susun", "peti", jalan="jalan_pikul"),
                "18-21": e("nyanyi_pelaut", "kursi", "minum_arak", "cawan_arak"),
            },
        },
    }
    tulis(RES + "/tempat/jadwal/kota_tema_pelabuhan.json", obj)


# ====================================================================== sale demand of the harbour (KafilahSekte)
def permintaan():
    """{item: factor}: the trade goods' harbour column read from DagangItems (so a table here never disagrees with the
    Buku Dagang), contraband 0 for open buyers (fences trade through bersama.Dagang), plus what a shipping town needs."""
    src = open(JAVA + "/dunia/tempat/tema/bersama/DagangItems.java", encoding="utf-8").read()
    obj = {"_catatan": "Kota Pelabuhan: haus kayu kapal, tali, besi dan bekal pelaut; ikan dan barang laut murah di sini.",
           "*": 1.0}
    n = 0
    for m in re.finditer(r'barang\("([a-z_]+)",.*?,\s*"([a-z]*)",\s*([0-9.]+),', src, re.S):
        bid, jalur, col = m.group(1), m.group(2), float(m.group(3))
        obj["kultivator:dagang/" + bid] = 0.0 if jalur == "penadah" else col
        n += 1
    if n < 17:
        ERR.append("DagangItems parse found only %d goods" % n)
    obj.update({
        "minecraft:oak_log": 1.2, "minecraft:spruce_log": 1.25, "minecraft:dark_oak_log": 1.2, "minecraft:oak_planks": 1.15,
        "minecraft:spruce_planks": 1.15, "minecraft:string": 1.25, "minecraft:iron_ingot": 1.2, "minecraft:iron_chain": 1.2,
        "minecraft:white_wool": 1.15, "minecraft:leather": 1.1, "minecraft:bread": 1.15, "minecraft:wheat": 1.1,
        "kultivator:hidangan/bakpao_roh": 1.2, "kultivator:healing_pill": 1.25, "kultivator:qi_gathering_pill": 1.1,
        "minecraft:cod": 0.9, "minecraft:salmon": 0.9, "minecraft:tropical_fish": 0.9, "minecraft:kelp": 0.9,
        "kultivator:tema/ikan_roh": 0.9,
    })
    tulis(RES + "/tempat/permintaan/kota_tema_pelabuhan.json", obj)
    return n


# ====================================================================== lang
def lang_files():
    for k, i, en in ui_lang():
        lang(k, i, en)
    others = {}
    for fn in os.listdir(LANG):
        if fn.startswith("tempat_tema_pelabuhan.") or not fn.endswith(".id_id.json"):
            continue
        for k in json.load(open(os.path.join(LANG, fn), encoding="utf-8")):
            others[k] = fn
    for k in L:
        if k in others:
            ERR.append("key %s already in %s" % (k, others[k]))
    keys = sorted(L)
    tulis(LANG + "/tempat_tema_pelabuhan.id_id.json", {k: L[k][0] for k in keys})
    tulis(LANG + "/tempat_tema_pelabuhan.en_us.json", {k: L[k][1] for k in keys})
    return len(keys)


if __name__ == "__main__":
    n_d = dialogs()
    n_q = quests()
    n_k = kabar()
    cerita_lain()
    n_a = advancements()
    loot()
    jadwal()
    n_p = permintaan()
    n_l = lang_files()
    print("dialogs %d, quests %d, rumours %d, advancements %d, trade goods %d, lang keys %d" % (n_d, n_q, n_k, n_a, n_p, n_l))
    if ERR:
        print("ERRORS:")
        for x in ERR:
            print("  " + x)
        sys.exit(1)
    print("OK")

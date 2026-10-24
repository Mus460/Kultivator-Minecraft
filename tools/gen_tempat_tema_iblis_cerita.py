#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DESIGN 38.T.7 (module 38TK-I): the story data and texts of the Kota Iblis, from one table so the id_id and en_us keys
never drift (pattern: tools/gen_tempat_tema_pelabuhan_cerita.py).

Writes (all owned by 38TK-I):
  data/kultivator/tempat/dialog/tema_iblis_*.json          dialogs of the demon city's characters and roles
  data/kultivator/tempat/quest/tema_iblis*.json            "Rantai yang Putus" (7 chapters, 3 routes) + 4 side quests
  data/kultivator/tempat/kabar/tema_iblis.json             rumours (spread by code and by the calendar)
  data/kultivator/tempat/{jadwal,permintaan}/kota_tema_iblis.json
  data/kultivator/loot_table/tema/iblis/*.json             chests of the toll house, arena, treasury, broker, contracts
  data/kultivator/advancement/tempat/tema_iblis_*.json     the theme's extra advancements (granted in code)
  lang-src/tempat_tema_iblis.{id_id,en_us}.json            every UI line of tema/iblis/*.java and every story text
Validates: clip / prop names (GerakId / PropId), dialog node and quest stage targets, condition / effect / goal words
(38ST built-ins, inti's TemaCerita words, IblisCerita words), every literal key of tema/iblis/*.java present, the
placeholder count of each key against the arguments its Java call passes, id/en parity, duplicates against the other
fragments. Run: python tools/gen_tempat_tema_iblis_cerita.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = REPO + "/src/main/resources/data/kultivator"
JAVA = REPO + "/src/main/java/dev/kultivator"
IBLIS = JAVA + "/dunia/tempat/tema/iblis"
LANG = REPO + "/lang-src"
FRAG = "tempat_tema_iblis"

L = {}
ERR = []


def lang(key, idn, en):
    if key in L:
        ERR.append("duplicate key " + key)
    L[key] = (idn, en)


def tulis(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(obj, ensure_ascii=False, indent=2) + "\n")


# ========================================================================================== UI strings (tema/iblis/*.java)
P = "kultivator.kota_tema.iblis."

UI = [
    # ---------------------------------------------------------------- shared
    ("uang_kurang", "Nilai Batumu tidak cukup: butuh %s.", "You do not carry enough Nilai Batu: %s needed."),
    ("dapat", "Kau mendapat %s.", "You receive %s."),
    ("layar.meter", "Gentar %1$s · Infami %2$s", "Dread %1$s · Infamy %2$s"),
    ("layar.pasar", "Pasar Seribu Bayangan", "Market of a Thousand Shadows"),
    ("layar.kitab", "Menara Kitab Terlarang", "Tower of Forbidden Books"),
    ("layar.kontrak", "Dinding Kontrak Sarang Pembunuh", "Contract Wall of the Assassins' Den"),
    ("layar.buronan", "Papan Buronan Iblis", "Demonic Bounty Board"),
    ("layar.taruhan", "Meja Taruhan Arena Darah", "Blood Arena Betting Table"),
    ("layar.tawanan", "Kandang Tawanan", "Captive Pens"),
    ("jasa.tangga", "Tangga Darah dan taruhan", "The Blood Ladder and betting"),
    ("jasa.tangga.catatan", "Daftar laga, lihat peringkat, pasang taruhan malam ini.", "Enter a bout, see your rank, bet on tonight's card."),
    ("jasa.kitab", "Rak kitab terlarang", "The forbidden shelves"),
    ("jasa.kitab.catatan", "Gulungan dan giok yang memakan sesuatu dari pembacanya.", "Scrolls and slips that devour something of their reader."),
    ("jasa.pasar", "Barang pasar gelap", "Black-market wares"),
    ("jasa.pasar.catatan", "Racun, pil darah, inti binatang, peta sarang, topeng, senjata curian.",
     "Poisons, blood pills, beast cores, lair maps, masks, stolen weapons."),
    ("jasa.kontrak", "Dinding kontrak", "The contract wall"),
    ("jasa.kontrak.catatan", "Hanya binatang iblis, geng iblis saingan, begal dan regu pemburu.",
     "Only demon beasts, rival demonic gangs, bandits and hunter squads."),
    ("jasa.buronan", "Papan buronan iblis", "The demonic bounty board"),
    ("jasa.buronan.catatan", "Kepala-kepala bernama dari Kota Iblis, atas nama sekte lurus.",
     "Named heads from the Demon City, in the name of the righteous sects."),
    ("jasa.tawanan", "Tebus tawanan", "Ransom a captive"),
    ("jasa.tawanan.catatan", "Hanya penebusan atau Tebusan Darah. Tidak ada jalan lain.", "Ransom or a Blood Ransom only. There is no other way."),
    ("jasa.taruhan", "Pasang taruhan", "Place a bet"),
    ("jasa.taruhan.catatan", "Dua belas laga tiap malam, 19.00 sampai 23.00.", "Twelve bouts a night, 19:00 to 23:00."),
    # ---------------------------------------------------------------- the gate
    ("gerbang.petunjuk", "Gerbang Tengkorak tertutup. Bayar upeti %s pada penegak di depan rahang.",
     "The Skull Gate is shut. Pay the %s toll to an enforcer before the jaws."),
    ("gerbang.bayar", "Kau meletakkan %s di meja upeti. Rahang tengkorak terbuka.", "You lay %s on the toll table. The skull's jaws open."),
    ("gerbang.gratis", "Penegak menyingkir tanpa meminta apa pun (%s). Namamu sudah dikenal di sini.",
     "The enforcer steps aside asking nothing (%s). Your name is known here."),
    ("gerbang.lemah", "Mangsa sekecil ini tidak berharga di sini. (Butuh ranah Fondasi.)",
     "Prey this small is worth nothing here. (Foundation realm needed.)"),
    ("gerbang.awas", "KOTA IBLIS", "THE DEMON CITY"),
    ("gerbang.awas.sub", "Ranahmu terlalu rendah untuk kota ini. Inti Emas disarankan.", "Your realm is low for this city. Golden Core is advised."),
    ("penyusup", "PENYUSUP", "INTRUDER"),
    ("penyusup.sub", "Kau masuk tanpa upeti dan terlihat penegak. Mereka memburumu sampai esok.",
     "You came in without the toll and an enforcer saw you. They hunt you until tomorrow."),
    # ---------------------------------------------------------------- the Pact
    ("zp.langgar", "PERJANJIAN PASAR DILANGGAR", "THE MARKET PACT IS BROKEN"),
    ("zp.langgar.sub", "Semua penegak memburumu sehari; Zona Perjanjian tertutup bagimu tiga hari.",
     "Every enforcer hunts you for a day; the Pact Zone is closed to you for three days."),
    ("zp.darah", "Darah tidak boleh tumpah di sini.", "No blood is spilled here."),
    # ---------------------------------------------------------------- Gentar
    ("gentar.naik", "Gentar +%1$s (kini %2$s): %3$s.", "Dread +%1$s (now %2$s): %3$s."),
    ("gentar.turun", "Gentar %1$s (kini %2$s): %3$s.", "Dread %1$s (now %2$s): %3$s."),
    ("gentar.ambang.30", "Penantang mulai menghindarimu, dan pasar gelap memberimu harga lebih baik.",
     "Challengers begin to avoid you, and the black market gives you a better price."),
    ("gentar.ambang.60", "Penguasa Kota bersedia menerimamu, dan Gerbang Tengkorak tak lagi meminta upeti darimu.",
     "The city's ruler will receive you, and the Skull Gate no longer asks you for a toll."),
    ("gentar.ambang.80", "Kau kini Yang Ditakuti di kota ini. Para penegak memberi hormat saat kau lewat.",
     "You are now the Feared One in this city. The enforcers salute as you pass."),
    ("alasan.arena", "menang di Arena Darah", "a win in the Blood Arena"),
    ("alasan.kontrak", "kontrak tuntas", "a contract fulfilled"),
    ("alasan.berburu", "selamat dari Jam Berburu", "surviving the Hunting Hour"),
    ("alasan.bunuh_warga", "membunuh warga di dalam tembok", "killing a citizen inside the walls"),
    ("alasan.bela_kota", "membela gerbang", "defending the gate"),
    ("alasan.turnamen", "juara Turnamen Darah", "Blood Tournament champion"),
    ("alasan.lao", "laga terakhir Lao Kuang", "Lao Kuang's last bout"),
    ("alasan.anak", "melaporkan anak-anak kumuh", "reporting the slum children"),
    # ---------------------------------------------------------------- arena
    ("arena.sudah", "Kau sudah berada dalam satu laga.", "You are already in a bout."),
    ("arena.habis", "Tu Gang menolak: %s laga sehari sudah cukup untuk siapa pun.", "Tu Gang refuses: %s bouts a day is enough for anyone."),
    ("arena.diburu", "Para penegak memburumu; arena tidak menerima buronan kota.", "The enforcers hunt you; the arena takes no fugitive of the city."),
    ("arena.tutup", "Lubang arena tertutup saat ini.", "The arena pit is closed right now."),
    ("arena.mulai.tangga", "TANGGA DARAH · PERINGKAT %s", "BLOOD LADDER · RANK %s"),
    ("arena.mulai.binatang", "LAWAN BINATANG · PERINGKAT %s", "BEAST BOUT · RANK %s"),
    ("arena.mulai.tebusan", "TEBUSAN DARAH", "BLOOD RANSOM"),
    ("arena.mulai.turnamen", "TURNAMEN DARAH · BABAK %s", "BLOOD TOURNAMENT · ROUND %s"),
    ("arena.binatang", "Binatang Iblis Kandang (tingkat %s)", "Caged Demon Beast (tier %s)"),
    ("arena.lawan_tangga", "Gladiator anak tangga %s", "Gladiator of rung %s"),
    ("arena.lawan.binatang", "dari kandang di bawah tribun", "from the cages beneath the stands"),
    ("arena.lawan.gladiator", "petarung bayaran ruang gladiator", "a hired fighter of the gladiators' room"),
    ("arena.menang", "KAU MENANG", "YOU WIN"),
    ("arena.hadiah", "Hadiah %s", "Prize %s"),
    ("arena.naik", "Kau naik ke anak tangga %s.", "You climb to rung %s."),
    ("arena.juara", "%s menaklukkan kesembilan anak Tangga Darah. Kota Iblis meneriakkan namanya.",
     "%s has conquered all nine rungs of the Blood Ladder. The Demon City shouts the name."),
    ("arena.kalah.hp", "Para pengawas menyeretmu keluar sebelum darahmu habis. Biaya laga hilang.",
     "The overseers drag you out before your blood runs out. The fee is lost."),
    ("arena.kalah.lari", "Lawanmu lenyap dari lubang; biaya laga dikembalikan.", "Your foe vanished from the pit; the fee is returned."),
    ("arena.kalah.keluar", "Kau meninggalkan lubang arena. Kerumunan mencemooh; biaya laga hilang.",
     "You left the pit. The crowd jeers; the fee is lost."),
    ("arena.kalah.waktu", "Gong waktu berbunyi. Laga tanpa pemenang dihitung kekalahan di Arena Darah.",
     "The time gong sounds. A bout without a winner counts as a loss in the Blood Arena."),
    ("gladiator.nama", "%1$s %2$s", "%1$s %2$s"),
    ("palu", "Palu Darah Senja", "Dusk Blood Maul"),
    ("palu.lore", "Gada juara Tangga Darah. Kepalanya pernah menghancurkan sembilan perisai dalam satu malam.",
     "The Blood Ladder champion's maul. Its head once broke nine shields in a single night."),
    # ---------------------------------------------------------------- betting
    ("taruhan.tutup", "Meja taruhan tutup; laga malam baru dimulai pukul 19.00.", "The betting table is closed; the night's bouts start at 19:00."),
    ("taruhan.batas", "Taruhan paling besar %s.", "The largest bet is %s."),
    ("taruhan.habis", "Kau sudah memasang %s taruhan malam ini.", "You have placed %s bets tonight."),
    ("taruhan.pasang", "Taruhan %1$s untuk %2$s (peluang ×%3$s).", "Bet of %1$s on %2$s (odds ×%3$s)."),
    ("taruhan.menang", "Taruhanmu menang: %1$s, berkat %2$s.", "Your bet won: %1$s, thanks to %2$s."),
    ("taruhan.menang_kurang", "Taruhanmu menang, tetapi kas kota hanya sanggup membayar %1$s (%2$s).",
     "Your bet won, but the city purse could only pay %1$s (%2$s)."),
    ("taruhan.kalah", "Taruhan %1$s hilang; %2$s menang.", "Bet of %1$s lost; %2$s won."),
    ("taruhan.baris", "%1$s. %2$s (t%3$s ×%4$s) lawan %5$s (t%6$s ×%7$s) [%8$s]",
     "%1$s. %2$s (t%3$s ×%4$s) vs %5$s (t%6$s ×%7$s) [%8$s]"),
    # ---------------------------------------------------------------- captives
    ("tawanan.tutup", "Kandang Tawanan telah ditutup untuk selamanya.", "The Captive Pens are closed for good."),
    ("tawanan.tidak_ada", "Tawanan itu tidak lagi di sini.", "That captive is no longer here."),
    ("tawanan.habis", "Penjaga hanya melepas %s tawanan sehari.", "The jailer releases only %s captives a day."),
    ("tawanan.tebus_ok", "Rantai dilepas. Bawa dia pulang dengan selamat.", "The chains are off. Take them home safely."),
    ("tawanan.bebas", "%1$s bebas. Kawal dia keluar gerbang menuju %2$s.", "%1$s is free. Escort them out of the gate toward %2$s."),
    ("tawanan.bebas_darah", "Darah membayar rantai %1$s. Kawal dia ke %2$s.", "Blood paid for %1$s's chains. Escort them to %2$s."),
    ("tawanan.sendiri", "%s tak sabar menunggu dan berjalan sendiri menuju Pos Pemburu.",
     "%s grew tired of waiting and walks alone toward the Hunters' Post."),
    ("tawanan.tiba", "%s tiba dengan selamat dan menangis di pelukan orang-orang yang mengenalnya.",
     "%s arrives safely and weeps in the arms of people who know them."),
    ("tawanan.diantar", "Pengawal Rumah Tebusan mengantar %s pulang.", "The House of Ransom's escort takes %s home."),
    ("tawanan.kosong", "Kandang kosong untuk saat ini.", "The pens are empty for now."),
    ("tawanan.kosong_serbu", "Kandang dikosongkan sejak penyerbuan sekte lurus.", "The pens stand empty since the righteous siege."),
    ("tawanan.rumah_tebusan", "Rumah Tebusan: setengah harga, diantar pulang oleh pengawal.", "House of Ransom: half price, escorted home."),
    ("tawanan.desa", "dekat %s", "near %s"),
    ("tawanan.papan", "%1$s · dari %2$s", "%1$s · from %2$s"),
    ("tawanan.cerita.0", "Diculik saat memanen di ladang paling ujung.", "Taken while harvesting the farthest field."),
    ("tawanan.cerita.1", "Pedagang garam yang menolak membayar upeti jalan.", "A salt seller who refused the road toll."),
    ("tawanan.cerita.2", "Diserahkan rentenir untuk melunasi utang ayahnya.", "Handed over by a moneylender for his father's debt."),
    ("tawanan.cerita.3", "Menyusul kakaknya ke hutan dan tak pernah kembali.", "Followed an older brother into the forest and never came back."),
    ("tawanan.cerita.4", "Tabib desa yang dibawa untuk merawat gladiator.", "A village healer taken to tend the gladiators."),
    ("tawanan.cerita.5", "Gadis penenun yang dijanjikan kerja di kota.", "A weaver girl promised work in the city."),
    ("tawanan.cerita.6", "Penjaga kuil kecil yang menolak menyerahkan pelita.", "Keeper of a small shrine who would not give up its lamp."),
    ("tawanan.cerita.7", "Penebang kayu yang terlalu dekat dengan sarang iblis.", "A woodcutter who strayed too near a demon lair."),
    ("tawanan.cerita.8", "Nelayan sungai yang perahunya dirampas di tikungan.", "A river fisher whose boat was seized at the bend."),
    ("tawanan.cerita.9", "Anak gembala yang mengikuti seekor kambing ke lembah.", "A shepherd child who followed a goat into the valley."),
    ("tawanan.cerita.10", "Sudah tiga kali mencoba kabur. Tiga kali tertangkap. Masih tersenyum.",
     "Has tried to escape three times. Caught three times. Still smiling."),
    # ---------------------------------------------------------------- black market
    ("pasar.tutup", "Pasar Seribu Bayangan tutup: para makelar masih bersembunyi sejak penyerbuan.",
     "The Market of a Thousand Shadows is shut: the brokers still hide since the siege."),
    ("pasar.penyusup", "Tak satu tirai pun terbuka bagi buruan para penegak.", "Not one curtain opens for the enforcers' quarry."),
    ("pasar.habis", "Barang itu sudah terjual hari ini.", "That piece has already sold today."),
    ("pasar.pita", "Butuh pita ranah %s ke atas.", "Needs realm band %s or higher."),
    ("pasar.batas", "Paling banyak %s sehari.", "At most %s a day."),
    ("pasar.beli", "Kau membeli %1$s seharga %2$s.", "You buy %1$s for %2$s."),
    ("pasar.curian_lore", "Asal gelap: toko kota lurus menolak membelinya.", "Dark origin: honest city shops refuse to buy it."),
    ("pil.minum", "Darah panas mengalir; luka menutup, tetapi tubuhmu lemas.", "Hot blood flows; wounds close, but your body goes limp."),
    ("racun.tanpa_senjata", "Pegang senjata di tangan utama untuk melapisinya.", "Hold a weapon in your main hand to coat it."),
    ("racun.oles", "Senjatamu berkilau hijau: %s serangan berikutnya meracuni.", "Your weapon gleams green: the next %s hits poison."),
    ("peta.tidak", "Tidak ada sarang iblis yang bisa ditunjuk peta ini dari sini.", "There is no demon lair this map can point to from here."),
    ("peta.arah", "Peta Sarang menunjuk sarang iblis %1$s blok jauhnya, di x %2$s, z %3$s.",
     "The Lair Map points to a demon lair %1$s blocks away, at x %2$s, z %3$s."),
    # ---------------------------------------------------------------- black auction
    ("lelang.mulai", "Tirai merah Balai Lelang Gelap terbuka. Lelang Gelap dimulai.", "The red curtain of the Black Auction Hall parts. The Black Auction begins."),
    # ---------------------------------------------------------------- forbidden tower
    ("menara.tolak", "Kitab-kitab lantai %1$s menolakmu (butuh Gentar %2$s). Langkahmu berat.",
     "The books of floor %1$s reject you (Dread %2$s needed). Your steps grow heavy."),
    ("menara.butuh_gentar", "Butuh Gentar %s di kota ini.", "Needs Dread %s in this city."),
    ("menara.diburu", "Si Buta Mo Wen tidak melayani buruan para penegak.", "Blind Mo Wen does not serve the enforcers' quarry."),
    ("menara.segel_alasan", "Segel lantai sembilan hanya terbuka saat Pesta Darah, atau bagi Tamu Darah.",
     "The seal of floor nine opens only during the Blood Feast, or for a Blood Guest."),
    ("menara.kitab_habis", "Kitab Darah Senja sudah diambil seseorang.", "Someone has already taken the Dusk Blood Book."),
    ("menara.noda_lore", "Noda terlarang: mempelajarinya menambah Infami +8.", "Forbidden stain: learning it adds Infamy +8."),
    ("menara.akses", "Mo Wen menandai namamu di buku tamu menara. Rak-rak di atas lebih ramah padamu (Gentar +20 untuk lantai).",
     "Mo Wen marks your name in the tower's guest book. The upper shelves are kinder to you (Dread +20 for the floors)."),
    ("menara.lantai.1", "Lantai 1 · Meja Mo Wen", "Floor 1 · Mo Wen's Desk"),
    ("menara.lantai.2", "Lantai 2 · Jalan Hantu", "Floor 2 · The Ghost Road"),
    ("menara.lantai.3", "Lantai 3 · Bayangan dan Darah", "Floor 3 · Shadow and Blood"),
    ("menara.lantai.4", "Lantai 4 · Rantai Jiwa", "Floor 4 · Soul Chains"),
    ("menara.lantai.5", "Lantai 5 · Kupu-kupu Arwah", "Floor 5 · Wraith Butterflies"),
    ("menara.lantai.6", "Lantai 6 · Topeng Iblis Hati", "Floor 6 · Heart-Demon Masks"),
    ("menara.lantai.7", "Lantai 7 · Mata Iblis Ungu", "Floor 7 · Purple Demon Eye"),
    ("menara.lantai.8", "Lantai 8 · Jari Kematian", "Floor 8 · Finger of Death"),
    ("menara.lantai.9", "Lantai 9 · Kitab Darah Senja", "Floor 9 · The Dusk Blood Book"),
    ("menara.lantai.1.isi", "Dupa, meja, dan buku tamu yang tak pernah ditulis ulang. Mo Wen membaca dengan jari.",
     "Incense, a desk, and a guest book never rewritten. Mo Wen reads with his fingers."),
    ("menara.lantai.2.isi", "Gulungan pembakar darah, tinta hitam berbau besi.", "Blood-burning scrolls, black ink that smells of iron."),
    ("menara.lantai.3.isi", "Giok langkah hantu; rak-raknya bergeser sendiri bila tak dipandang.",
     "Ghost-step slips; the shelves shift by themselves when unwatched."),
    ("menara.lantai.4.isi", "Rantai di setiap rak; di sini Mo Wen menyimpan teka-teki tiga kitab.",
     "Chains on every shelf; here Mo Wen keeps the riddle of three books."),
    ("menara.lantai.5.isi", "Debu berkilau seperti sayap. Jangan bernapas terlalu dalam.", "Dust glitters like wings. Do not breathe too deeply."),
    ("menara.lantai.6.isi", "Topeng-topeng tergantung tanpa wajah di baliknya, menunggu wajah baru.",
     "Masks hang with no face behind them, waiting for a new one."),
    ("menara.lantai.7.isi", "Satu jendela, satu mata ungu di kaca yang selalu menatap ke dalam.",
     "One window, one purple eye in the glass that always looks inward."),
    ("menara.lantai.8.isi", "Dinginnya menusuk tulang. Kitab-kitab di sini ditulis di kulit.", "The cold bites the bone. The books here are written on skin."),
    ("menara.lantai.9.isi", "Kolam merah tanpa air, mimbar obsidian menangis, dan satu kitab yang menunggu tiga ribu tahun.",
     "A red pool with no water, a weeping obsidian lectern, and one book that has waited three thousand years."),
    ("kitab", "Kitab Darah Senja", "Dusk Blood Book"),
    ("kitab.lore.1", "Teknik terakhir Pemimpin Iblis Darah, ditulis sebelum ia jatuh di medan ini.",
     "The last technique of the Blood Demon Lord, written before he fell on this field."),
    ("kitab.lore.2", "Infami +20 bagi yang memilikinya. Kota-kota lurus mengingat.", "Infamy +20 for whoever owns it. Righteous cities remember."),
    ("kitab.dapat", "Kitab Darah Senja kini milikmu. Tiga ribu tahun menunggu, dan ia memilih tanganmu.",
     "The Dusk Blood Book is yours now. Three thousand years of waiting, and it chose your hand."),
    ("kitab.ganti", "Kitab itu sudah hilang dari menara; Xue Yao membayar harganya: %s.",
     "The book is already gone from the tower; Xue Yao pays its price instead: %s."),
    ("segel.retak", "Segel darah retak dan memudar di depanmu.", "The blood seal cracks and fades before you."),
    ("segel.tolak", "Segel darah membakar dingin. Ia tidak mengenalmu.", "The blood seal burns cold. It does not know you."),
    # ---------------------------------------------------------------- forbidden scrolls
    ("gulungan.bakar_darah.efek", "Korbankan 30 %% darah: Kekuatan II 30 detik. Jeda 5 menit.",
     "Sacrifice 30 %% of your blood: Strength II for 30 s. 5 minute cooldown."),
    ("gulungan.langkah_hantu.efek", "Tak Terlihat dan Cepat II 12 detik; biaya 20 %% Qi.", "Invisibility and Speed II for 12 s; costs 20 %% Qi."),
    ("gulungan.rantai_jiwa.efek", "Tarik satu makhluk dalam 12 blok ke depanmu; lawan ranah, ujian dan bos kebal.",
     "Pull one creature within 12 blocks to your front; realm foes, trials and bosses are immune."),
    ("gulungan.topeng_iblis_hati.efek", "Kerusakan +15 %% selama 60 detik; sesudahnya tekanan iblis hati +5.",
     "Damage +15 %% for 60 s; afterwards heart-demon pressure +5."),
    ("gulungan.peringatan", "Dipakai di luar Kota Iblis di depan saksi: Infami +1.", "Used outside the Demon City before witnesses: Infamy +1."),
    ("gulungan.disaksikan", "Ada yang melihat. Kabar gulungan terlarangmu akan sampai ke sekte lurus.",
     "Someone saw. Word of your forbidden scroll will reach the righteous sects."),
    ("gulungan.bakar_darah.lemah", "Darahmu terlalu sedikit untuk dibakar.", "You have too little blood left to burn."),
    ("gulungan.bakar_darah.pakai", "Darahmu menyala. Kekuatan membanjir.", "Your blood ignites. Strength floods in."),
    ("gulungan.langkah_hantu.qi", "Qi-mu tidak cukup untuk Langkah Hantu.", "Your Qi is not enough for the Ghost Step."),
    ("gulungan.langkah_hantu.pakai", "Bayanganmu memudar ke dalam asap.", "Your shadow fades into smoke."),
    ("gulungan.rantai_jiwa.kosong", "Rantai jiwa tidak menemukan sasaran di depanmu.", "The soul chain finds no target before you."),
    ("gulungan.rantai_jiwa.kebal", "Rantai putus: jiwa itu terlalu kuat untuk ditarik.", "The chain snaps: that soul is too strong to pull."),
    ("gulungan.rantai_jiwa.pakai", "Rantai jiwa menyeret %s ke depanmu.", "The soul chain drags %s before you."),
    ("gulungan.topeng_iblis_hati.pakai", "Topeng iblis hati menempel di wajahmu. Kau haus pertarungan.",
     "The heart-demon mask clings to your face. You thirst for battle."),
    ("gulungan.topeng_iblis_hati.habis", "Topeng terlepas. Bisikan iblis hatimu tertinggal.", "The mask falls away. Your heart demon's whisper remains."),
    # ---------------------------------------------------------------- contracts
    ("kontrak.diburu", "Sarang Kontrak tidak menerima buruan para penegak.", "The Contract Den takes no quarry of the enforcers."),
    ("kontrak.penuh", "Paling banyak %s kontrak berjalan bersamaan.", "At most %s contracts may run at once."),
    ("kontrak.ambil", "Kontrak diterima: %1$s di sekitar x %2$s, z %3$s. Tiga hari.",
     "Contract taken: %1$s around x %2$s, z %3$s. Three days."),
    ("kontrak.belum", "Sasaran kontrak itu belum tumbang.", "That contract's target has not fallen yet."),
    ("kontrak.serah", "Juru kontrak menghitung koin: %s.", "The contract clerk counts out the coins: %s."),
    ("kontrak.batal", "Kontrak dibatalkan.", "Contract cancelled."),
    ("kontrak.dekat", "Sasaran kontrakmu ada di dekat sini.", "Your contract's target is near."),
    ("kontrak.gagal", "Waktu kontrakmu habis. Sarang Kontrak mencoret namamu dari dinding.", "Your contract's time ran out. The Den strikes your name from the wall."),
    ("kontrak.tuntas", "KONTRAK TUNTAS", "CONTRACT FULFILLED"),
    ("kontrak.tuntas.sub", "Kembali ke Sarang Kontrak untuk upahnya.", "Return to the Contract Den for the fee."),
    ("kontrak.jenis.binatang", "Kontrak binatang iblis", "Demon beast contract"),
    ("kontrak.jenis.iblis_saingan", "Kontrak geng iblis saingan", "Rival demonic gang contract"),
    ("kontrak.jenis.begal", "Kontrak band begal", "Bandit band contract"),
    ("kontrak.jenis.pemburu", "Kontrak regu pemburu lurus", "Righteous hunter squad contract"),
    ("kontrak.sasaran.binatang.0", "Babi taring besi yang menjaga jalur herba (tingkat %s)", "An iron-tusk boar guarding a herb trail (tier %s)"),
    ("kontrak.sasaran.binatang.1", "Beruang salju bermata merah yang memangsa kafilah (tingkat %s)",
     "A red-eyed snow bear preying on caravans (tier %s)"),
    ("kontrak.sasaran.binatang.2", "Binatang tanduk yang merobohkan pos jalan (tingkat %s)", "A horned beast that wrecked a road post (tier %s)"),
    ("kontrak.sasaran.binatang.3", "Pemangsa malam di lembah kabut (tingkat %s)", "A night predator of the misty valley (tier %s)"),
    ("kontrak.sasaran.binatang.4", "Binatang iblis yang lepas dari kandang arena (tingkat %s)", "A demon beast loose from the arena cages (tier %s)"),
    ("kontrak.sasaran.binatang.5", "Pelahap inti di tepi sungai darah (tingkat %s)", "A core-eater on the bank of the blood river (tier %s)"),
    ("kontrak.sasaran.saingan", "Geng %1$s pimpinan %2$s yang mencuri pil Pasar Seribu Bayangan",
     "The %1$s gang under %2$s, who stole pills from the Thousand Shadows"),
    ("kontrak.sasaran.begal", "Band begal %s yang merampok jalur upeti", "%s's bandit band robbing the tribute road"),
    ("kontrak.sasaran.pemburu", "Regu %s pemburu lurus yang mengintai warga kota", "A squad of %s righteous hunters stalking the citizens"),
    ("kontrak.geng.0", "Kalajengking Merah", "Red Scorpion"),
    ("kontrak.geng.1", "Tulang Gagak", "Crow Bone"),
    ("kontrak.geng.2", "Seribu Taring", "Thousand Fangs"),
    ("kontrak.geng.3", "Lentera Mati", "Dead Lantern"),
    ("kontrak.geng.4", "Ular Bisu", "Mute Snake"),
    ("kontrak.geng.5", "Kabut Ungu", "Purple Mist"),
    ("kontrak.geng.6", "Cakar Senja", "Dusk Claw"),
    ("kontrak.geng.7", "Darah Dingin", "Cold Blood"),
    # ---------------------------------------------------------------- Shadow Oil
    ("minyak.jangan", "Tong-tong ini milik Nyonya Bai She. Merusaknya tanpa alasan hanya mengundang pisau.",
     "These barrels belong to Madam Bai She. Breaking them for nothing only invites knives."),
    ("minyak.lihat", "Tong bersegel lilin hitam. Baunya seperti malam yang terbakar.", "A barrel sealed with black wax. It smells like a burning night."),
    ("minyak.lihat_pecah", "Tong pecah; minyak bayangan meresap ke lantai batu.", "A broken barrel; shadow oil seeps into the stone floor."),
    ("minyak.pecah", "Tong pecah: %1$s dari %2$s.", "Barrels broken: %1$s of %2$s."),
    ("minyak.sabotase", "JARINGAN MINYAK BAYANGAN PUTUS", "THE SHADOW OIL RING IS CUT"),
    ("minyak.sabotase.sub", "Nyonya Bai She akan mengingat ini. Harga pasarnya naik untukmu.",
     "Madam Bai She will remember this. Her market prices rise for you."),
    ("minyak.beli", "Kau membeli bagian jaringan minyak: %1$s, dibayarkan selama %2$s hari.",
     "You buy a share of the oil ring: %1$s, paid out over %2$s days."),
    ("minyak.bagian", "Kurir Hong Pao menyelipkan bagianmu: %1$s (sisa %2$s kiriman).",
     "Hong Pao's courier slips you your share: %1$s (%2$s payments left)."),
    # ---------------------------------------------------------------- hunting hour
    ("berburu.judul", "JAM BERBURU", "THE HUNTING HOUR"),
    ("berburu.sub", "\"Upeti jalan, atau darahmu?\" (%s)", "\"A road toll, or your blood?\" (%s)"),
    ("berburu.pilihan", "Jawab: %1$s atau %2$s", "Answer: %1$s or %2$s"),
    ("berburu.bayar", "[Bayar %s]", "[Pay %s]"),
    ("berburu.lawan", "[Lawan]", "[Fight]"),
    ("berburu.dibayar", "Mereka mengambil %s dan larut ke dalam gelap.", "They take %s and melt into the dark."),
    ("berburu.darah", "Darah, kalau begitu.", "Blood, then."),
    ("berburu.zp", "Para pemburu malam berhenti di batas Zona Perjanjian.", "The night hunters stop at the edge of the Pact Zone."),
    ("berburu.menang", "Jalan malam kini lebih sepi bagimu.", "The night road grows quieter for you."),
    # ---------------------------------------------------------------- righteous hunters
    ("pemburu.datang", "PEMBURU LURUS", "RIGHTEOUS HUNTERS"),
    ("pemburu.datang.sub", "Atas nama %s, mereka datang untukmu.", "In the name of %s, they come for you."),
    ("pemburu.gilda", "Gilda Kultivator", "the Cultivator Guild"),
    ("pemburu.gelar", "Pemburu Lurus", "Righteous Hunter"),
    ("pemburu.tobat", "Kau membayar denda tobat %s. Para pemburu menurunkan pedang.", "You pay the penance fine of %s. The hunters lower their swords."),
    ("pemburu.kejar", "Mereka mengejarmu!", "They give chase!"),
    ("pemburu.lolos", "Kau lolos dari para pemburu.", "You escaped the hunters."),
    ("pemburu.kalah", "Regu pemburu tumbang. Sektenya akan mendengar.", "The hunter squad falls. Their sect will hear of it."),
    ("pemburu.han_mundur", "Han Qingshuang terhuyung, menyarungkan pedangnya, dan mundur dengan luka. \"Ini belum selesai.\"",
     "Han Qingshuang staggers, sheathes her sword and withdraws, wounded. \"This is not over.\""),
    # ---------------------------------------------------------------- bounties
    ("buronan.nama", "%1$s %2$s", "%1$s %2$s"),
    ("buronan.gelar", "Buronan Iblis", "Demonic Fugitive"),
    ("buronan.ambil", "Perburuan diterima: %1$s, berkeliaran di x %2$s, z %3$s hari ini.", "Hunt taken: %1$s, roaming at x %2$s, z %3$s today."),
    ("buronan.belum", "Buronan itu belum tumbang oleh tanganmu.", "That fugitive has not fallen by your hand."),
    ("buronan.klaim", "Hadiah kepala %1$s dibayarkan: %2$s.", "The bounty on %1$s is paid: %2$s."),
    ("buronan.pusaka", "Di antara barang buronan itu kau menemukan %s.", "Among the fugitive's belongings you find %s."),
    ("buronan.ketemu", "%s ada di sini.", "%s is here."),
    ("buronan.tewas", "BURONAN TUMBANG", "FUGITIVE DOWN"),
    ("buronan.di_luar", "di luar kota hari ini", "outside the city today"),
    ("buronan.di_kota", "di dalam kota hari ini (di luar Zona Perjanjian)", "inside the city today (outside the Pact Zone)"),
    ("buronan.papan.kosong", "Tidak ada Kota Iblis yang dikenal di dekat sini.", "No known Demon City is near."),
    ("buronan.papan.baris", "%1$s · hadiah %2$s", "%1$s · bounty %2$s"),
    ("buronan.kontrak_kepala", "Kontrak Kepala: %1$s (%2$s) berkeliaran di x %3$s, z %4$s.", "Head Contract: %1$s (%2$s) roams at x %3$s, z %4$s."),
    ("buronan.jual", "Han Qingshuang membeli Kontrak Kepalamu seharga %s.", "Han Qingshuang buys your Head Contract for %s."),
    ("buronan.tanpa_kontrak", "Kau tidak membawa Kontrak Kepala.", "You carry no Head Contract."),
    ("buronan.kejahatan.0", "membakar %1$s; %2$s rumah jadi abu", "burned %1$s; %2$s houses turned to ash"),
    ("buronan.kejahatan.1", "menculik %2$s warga %1$s", "kidnapped %2$s people of %1$s"),
    ("buronan.kejahatan.2", "meracuni sumur %1$s; %2$s orang sakit", "poisoned the well of %1$s; %2$s fell ill"),
    ("buronan.kejahatan.3", "membantai kafilah di jalan %1$s; %2$s korban", "slaughtered a caravan on the %1$s road; %2$s dead"),
    ("buronan.kejahatan.4", "mencuri pusaka kuil %1$s dan membunuh %2$s penjaga", "stole the relic of %1$s shrine and killed %2$s guards"),
    ("buronan.kejahatan.5", "menjual %2$s anak %1$s ke pasar gelap", "sold %2$s children of %1$s on the black market"),
    ("buronan.kejahatan.6", "menghisap qi %2$s murid sekte di dekat %1$s", "drained the qi of %2$s sect disciples near %1$s"),
    ("buronan.kejahatan.7", "membuka segel makam di %1$s; %2$s mayat bangkit", "broke a tomb seal at %1$s; %2$s corpses rose"),
    ("buronan.kejahatan.8", "memeras %1$s dengan racun; %2$s keluarga bangkrut", "extorted %1$s with poison; %2$s families ruined"),
    ("buronan.kejahatan.9", "membunuh %2$s pemburu lurus di perbatasan %1$s", "killed %2$s righteous hunters at the border of %1$s"),
    ("buronan.kejahatan.10", "membakar lumbung %1$s di musim paceklik; %2$s orang kelaparan", "burned the granary of %1$s in the lean season; %2$s starved"),
    ("buronan.kejahatan.11", "memimpin serbuan malam ke %1$s; %2$s tak kembali", "led a night raid on %1$s; %2$s never came back"),
    ("buronan.dusun.0", "Dusun Batu Hijau", "Green Stone Hamlet"),
    ("buronan.dusun.1", "Desa Tiga Sumur", "Three Wells Village"),
    ("buronan.dusun.2", "Dusun Bambu Kuning", "Yellow Bamboo Hamlet"),
    ("buronan.dusun.3", "Desa Tepi Kabut", "Mist's Edge Village"),
    ("buronan.dusun.4", "Dusun Lumbung Tua", "Old Granary Hamlet"),
    ("buronan.dusun.5", "Desa Jembatan Merah", "Red Bridge Village"),
    ("buronan.dusun.6", "Dusun Kenari", "Walnut Hamlet"),
    ("buronan.dusun.7", "Desa Lembah Garam", "Salt Valley Village"),
    ("buronan.dusun.8", "Dusun Pinus Bisu", "Silent Pine Hamlet"),
    ("buronan.dusun.9", "Desa Teratai Senja", "Dusk Lotus Village"),
    # ---------------------------------------------------------------- siege
    ("serbu.mulai", "PENYERBUAN LURUS", "THE RIGHTEOUS SIEGE"),
    ("serbu.mulai.sub", "Koalisi sekte lurus mengepung Gerbang Tengkorak.", "A coalition of righteous sects besieges the Skull Gate."),
    ("serbu.gelombang", "Gelombang %1$s dari %2$s", "Wave %1$s of %2$s"),
    ("serbu.tidak", "Tidak ada penyerbuan yang sedang berlangsung di dekat sini.", "No siege is under way nearby."),
    ("serbu.pihak.lurus", "Kau berdiri bersama jalan lurus. Para penegak akan memburumu sampai penyerbuan usai.",
     "You stand with the righteous path. The enforcers will hunt you until the siege is over."),
    ("serbu.pihak.kota", "Kau berdiri bersama kota. Para pemburu lurus akan menyerangmu.", "You stand with the city. The righteous hunters will strike at you."),
    ("serbu.menang", "PIHAKMU MENANG", "YOUR SIDE WINS"),
    ("serbu.kalah", "PIHAKMU KALAH", "YOUR SIDE LOSES"),
    ("serbu.hasil.lurus", "Gerbang jebol; kandang dikosongkan, pasar tutup dua hari.", "The gate falls; the pens are emptied, the market shut two days."),
    ("serbu.hasil.kota", "Gerbang bertahan; upeti naik setengah selama tiga hari.", "The gate holds; the toll rises by half for three days."),
    ("tuas.terkunci", "Tuas gerbang terkunci rantai. Bukan saatnya.", "The gate lever is chained. Not now."),
    ("tuas.dijaga", "Masih ada %s penjaga di ruang tuas.", "%s guards still hold the lever room."),
    ("terowongan.runtuhan", "Reruntuhan menutup Terowongan Tua dari luar.", "Rubble closes the Old Tunnel from outside."),
    ("terowongan.buka", "TEROWONGAN TUA TERBUKA", "THE OLD TUNNEL IS OPEN"),
    ("terowongan.buka.sub", "%s tawanan menunggumu di ujung dalam. Bawa mereka keluar!", "%s captives wait at the inner end. Bring them out!"),
    ("terowongan.selamat", "%s tawanan menghirup udara bebas", "%s captives breathe free air"),
    # ---------------------------------------------------------------- feast and tournament
    ("pesta.mulai", "PESTA DARAH", "THE BLOOD FEAST"),
    ("pesta.mulai.sub", "Gerhana darah. Lentera kota berubah merah, dan lantai sembilan terbuka.",
     "A blood eclipse. The city's lanterns turn red, and floor nine opens."),
    ("pesta.akhir", "Gerhana usai. Lentera kembali hitam.", "The eclipse is over. The lanterns turn black again."),
    ("turnamen.tidak", "Turnamen Darah hanya digelar saat Pesta Darah.", "The Blood Tournament is held only during the Blood Feast."),
    ("turnamen.sudah", "Kau sudah terdaftar dalam Turnamen Darah malam ini.", "You are already entered in tonight's Blood Tournament."),
    ("turnamen.daftar", "Kau mendaftar Turnamen Darah (%1$s). Juara membawa pulang %2$s.", "You enter the Blood Tournament (%1$s). The champion takes home %2$s."),
    ("turnamen.gugur", "Kau gugur di babak %s.", "You fall in round %s."),
    ("turnamen.lanjut", "Lolos! Babak %1$s dimulai dalam %2$s detik.", "Through! Round %1$s starts in %2$s seconds."),
    ("turnamen.pergi", "Kau meninggalkan kota; namamu dicoret dari turnamen.", "You left the city; your name is struck from the tournament."),
    ("turnamen.bubar", "Gerhana usai; Turnamen Darah dibubarkan.", "The eclipse is over; the Blood Tournament is dissolved."),
    ("turnamen.juara", "JUARA TURNAMEN DARAH", "BLOOD TOURNAMENT CHAMPION"),
    # ---------------------------------------------------------------- Xue Yao
    ("xue.judul", "XUE YAO, PENGUASA DARAH SENJA", "XUE YAO, LORD OF THE DUSK BLOOD"),
    ("xue.fase.1", "Pedang darah terhunus.", "The blood sword is drawn."),
    ("xue.fase.2", "Xue Yao memanggil gladiatornya!", "Xue Yao calls her gladiators!"),
    ("xue.fase.3", "Kolam darah berdenyut di sekitarnya. Menjauh!", "The blood pool pulses around her. Stay back!"),
    ("xue.kalah", "XUE YAO MENYERAH", "XUE YAO YIELDS"),
    ("xue.kalah.sub", "\"Kota ini milik yang lebih kuat. Malam ini, itu kau.\"", "\"This city belongs to the stronger. Tonight, that is you.\""),
    ("kipas", "Kipas Senja", "Dusk Fan"),
    ("kipas.lore", "Kipas besi Penguasa Darah Senja. Tiap rusuknya pernah menjadi pedang seorang penantang.",
     "The iron fan of the Dusk Blood Lord. Each of its ribs was once a challenger's sword."),
    # ---------------------------------------------------------------- routes and endings
    ("rute.lurus", "Kau memilih jalan Lurus: peta ini milik Han Qingshuang.", "You chose the Righteous road: this map belongs to Han Qingshuang."),
    ("rute.penebusan", "Kau memilih jalan Penebusan: Ye Wuhen akan membawa mereka keluar.", "You chose the road of Redemption: Ye Wuhen will lead them out."),
    ("rute.darah", "Kau memilih jalan Darah: Xue Yao kini berutang padamu.", "You chose the road of Blood: Xue Yao now owes you."),
    ("akhir.lurus", "PEMUTUS RANTAI", "CHAIN BREAKER"),
    ("akhir.penebusan", "PEMBAWA FAJAR", "BRINGER OF DAWN"),
    ("akhir.darah", "TAMU DARAH", "BLOOD GUEST"),
    ("akhir.sub", "Kota Iblis tidak akan pernah sama lagi.", "The Demon City will never be the same."),
    # ---------------------------------------------------------------- side quests
    ("topeng.arah", "Regu pemburu yang mencuri topeng Bai She berkemah dekat x %1$s, z %2$s.",
     "The hunter squad that stole Bai She's mask camps near x %1$s, z %2$s."),
    ("topeng.ketemu", "Kemah regu pemburu. Topeng itu ada di antara mereka.", "The hunter squad's camp. The mask is among them."),
    ("topeng.rebut", "Topeng Tanpa Wajah kembali ke tanganmu.", "The Faceless Mask is back in your hands."),
    # ---------------------------------------------------------------- commands
    ("cmd.tidak_ada", "Tidak ada Kota Iblis dalam jangkauan.", "No Demon City in range."),
    ("cmd.di_kota", "Berdirilah di dalam Kota Iblis.", "Stand inside a Demon City."),
    ("cmd.infami", "Infami kini %s.", "Infamy is now %s."),
    ("cmd.gentar", "Gentar kini %s.", "Dread is now %s."),
    ("cmd.alur", "Rantai yang Putus melompat ke tahap %s.", "The Broken Chain jumps to stage %s."),
    ("cmd.alur_gagal", "Tahap %s tidak dikenal.", "Unknown stage %s."),
    ("cmd.rute", "Rute kini: %s", "Route is now: %s"),
    ("cmd.reset", "Bendera cerita kota ini dihapus.", "This city's story flags are cleared."),
    ("cmd.boleh", "boleh sekarang", "may come now"),
    ("cmd.pemburu", "Infami %1$s (ambang %2$s): %3$s", "Infamy %1$s (threshold %2$s): %3$s"),
    ("cmd.buronan", "#%1$s %2$s · tingkat %3$s · %4$s · x %5$s z %6$s", "#%1$s %2$s · tier %3$s · %4$s · x %5$s z %6$s"),
    ("cmd.kontrak", "#%1$s %2$s %3$s · %4$s NB · x %5$s z %6$s", "#%1$s %2$s %3$s · %4$s NB · x %5$s z %6$s"),
    ("cmd.lantai", "Lantai %1$s: %2$s (Gentar %3$s)", "Floor %1$s: %2$s (Dread %3$s)"),
    ("cmd.segel", "Segel keadaan %1$s, stok kitab %2$s", "Seal state %1$s, book stock %2$s"),
    ("cmd.serbu", "Gelombang %1$s/%2$s · pemburu %3$s · penegak %4$s · tumbang %5$s/%6$s · %7$s",
     "Wave %1$s/%2$s · hunters %3$s · enforcers %4$s · fallen %5$s/%6$s · %7$s"),
    ("cmd.serbu_lagi", "Penyerbuan berikutnya dalam %s hari.", "Next siege in %s days."),
    ("cmd.tawanan", "#%1$s %2$s · %3$s", "#%1$s %2$s · %3$s"),
    ("cmd.tawanan_harga", "Harga tebus: %s NB.", "Ransom price: %s NB."),
    ("cmd.kota", "%1$s · kas %2$s NB · penguasa %3$s · Gentar %4$s · Infami %5$s · rute %6$s · tahap %7$s",
     "%1$s · purse %2$s NB · ruler %3$s · Dread %4$s · Infamy %5$s · route %6$s · stage %7$s"),
    ("cmd.lelang_gagal", "Juru lelang Balai Lelang Gelap tidak ada di dekat sini.", "The Black Auction Hall's auctioneer is not near."),
]

# 16 nicknames of gladiators and fugitives, 6 fighting styles of the betting card
JULUKAN = [
    ("si Taring Patah", "the Broken Fang"), ("si Tangan Besi", "Iron Hand"), ("si Mata Satu", "One-Eye"), ("si Anjing Kubur", "the Grave Dog"),
    ("si Pemakan Abu", "the Ash Eater"), ("si Rantai Merah", "Red Chain"), ("si Gigi Hitam", "Black Tooth"), ("si Lidah Ular", "Snake Tongue"),
    ("si Tulang Retak", "Cracked Bone"), ("si Senyum Pisau", "Knife Smile"), ("si Langkah Sunyi", "Silent Step"), ("si Kepala Batu", "Stonehead"),
    ("si Gagak Malam", "Night Crow"), ("si Darah Dingin", "Cold Blood"), ("si Seribu Luka", "Thousand Scars"), ("si Tawa Kubur", "Grave Laugh"),
]
GAYA = [("ganas", "fierce"), ("licik", "cunning"), ("tenang", "calm"), ("liar", "wild"), ("bertahan", "defensive"), ("nekat", "reckless")]

# the recurring characters: id, name (id, en), title (id, en)
TOKOH = [
    ("xue_yao", ("Xue Yao", "Xue Yao"), ("Penguasa Darah Senja", "Lord of the Dusk Blood")),
    ("tu_gang", ("Tu Gang", "Tu Gang"), ("Si Jagal, Tuan Arena", "the Butcher, Master of the Arena")),
    ("bai_she", ("Nyonya Bai She", "Madam Bai She"), ("Makelar Gelap", "Shadow Broker")),
    ("mo_wen", ("Si Buta Mo Wen", "Blind Mo Wen"), ("Penjaga Kitab Terlarang", "Keeper of the Forbidden Books")),
    ("ye_wuhen", ("Ye Wuhen", "Ye Wuhen"), ("Kultivator Iblis Muda", "Young Demonic Cultivator")),
    ("a_fu", ("A-Fu", "A-Fu"), ("Tawanan Kecil", "Little Captive")),
    ("han_qingshuang", ("Han Qingshuang", "Han Qingshuang"), ("Murid Inti Pemburu Lurus", "Core Disciple, Righteous Hunter")),
    ("hong_pao", ("Hong Pao", "Hong Pao"), ("Si Jubah Merah", "the Red Robe")),
    ("lao_kuang", ("Lao Kuang", "Lao Kuang"), ("Gladiator Tua", "Old Gladiator")),
]


def ui_lang():
    for k, i, e in UI:
        lang(P + k, i, e)
    for n, (i, e) in enumerate(JULUKAN):
        lang(P + "julukan.%d" % n, i, e)
    for n, (i, e) in enumerate(GAYA):
        lang(P + "gaya.%d" % n, i, e)
    for t, nama, gelar in TOKOH:
        lang(P + "tokoh." + t, *nama)
        lang(P + "tokoh." + t + ".gelar", *gelar)
    lang("kultivator.sibuk.tema.berburu", "dihadang Jam Berburu", "stopped by the Hunting Hour")
    lang("kultivator.sibuk.tema.pemburu", "diburu regu Pemburu Lurus", "hunted by a Righteous Hunter squad")
    # place lore (kultivator.lore.kota_tema.iblis.*, the pattern of the oasis theme)
    lang("kultivator.lore.kota_tema.iblis.judul", "Kota Iblis", "The Demon City")
    lang("kultivator.lore.kota_tema.iblis.1",
         "Kota ini berdiri di atas medan perang tempat sekte-sekte lurus mengalahkan Pemimpin Iblis Darah tiga ribu tahun lalu. "
         "Tanahnya masih merah bila digali sedalam lutut, dan tulang-tulang raksasa di Gerbang Tengkorak konon miliknya.",
         "This city stands on the battlefield where the righteous sects defeated the Blood Demon Lord three thousand years ago. "
         "The earth is still red a knee deep down, and the giant bones of the Skull Gate are said to be his.")
    lang("kultivator.lore.kota_tema.iblis.2",
         "Tidak ada hukum selain Perjanjian Pasar: di dalam pasar dan kedai, darah tidak boleh tumpah; di luar itu, yang kuat benar. "
         "Penguasa Kota memungut upeti dari setiap pintu, menyewakan arena, dan menyimpan Menara Kitab Terlarang.",
         "There is no law but the Market Pact: inside the market and the taverns no blood is spilled; outside, the strong are right. "
         "The city's ruler takes a toll from every door, rents out the arena and keeps the Tower of Forbidden Books.")
    lang("kultivator.lore.kota_tema.iblis.3",
         "Sekte-sekte lurus memasang hadiah atas kepala penghuninya, dan setiap beberapa purnama pasukan pemburu mengepung temboknya. "
         "Penghuni kota menyebut pengepungan itu 'musim panen' dan bertaruh berapa lama gerbang akan bertahan.",
         "The righteous sects put bounties on its people's heads, and every few full moons a hunting host besieges its walls. "
         "The citizens call the sieges 'harvest season' and bet on how long the gate will hold.")
    lang("kultivator.lore.kota_tema.iblis.sumber",
         "Sumber: Wilayah Sudut Hitam (Black-Corner Region) dalam Battle Through the Heavens karya Tian Can Tu Dou dan Kota Tanda "
         "Hitam (Black Mark City) dengan biaya masuknya (terjemahan Wuxiaworld, sekitar bab 383-387); kiasan umum xianxia tentang kota "
         "jalan iblis, arena darah dan perburuan hadiah oleh sekte lurus. Tata kota, tokoh, harga dan angkanya rancangan game.",
         "Source: the Black-Corner Region of Battle Through the Heavens by Tian Can Tu Dou and its Black Mark City with the entry fee "
         "(Wuxiaworld translation, around chapters 383-387); common xianxia tropes of demonic-path cities, blood arenas and righteous "
         "bounty hunts. The layout, characters, prices and numbers are the game's design.")


# ========================================================================================== dialogs
Q = "tema_iblis_rantai"
QT = "tema_iblis_topeng"
QL = "tema_iblis_gladiator_tua"
QK = "tema_iblis_kitab"
QA = "tema_iblis_anak_liar"


def N(nid, teks, gestur="", prop="", akibat=None, isian=None, pilihan=None, suara=""):
    return {"id": nid, "teks": teks, "gestur": gestur, "prop": prop, "akibat": akibat or [], "isian": isian or [],
            "pilihan": pilihan or [], "suara": suara}


def C(teks, ke=None, syarat=None, akibat=None, tampil=False, alasan=None):
    return {"teks": teks, "ke": ke, "syarat": syarat or [], "akibat": akibat or [], "tampil": tampil, "alasan": alasan}


def D(did, pembicara, syarat, prioritas, simpul, sekali=False):
    return {"id": did, "pembicara": pembicara if isinstance(pembicara, list) else [pembicara], "syarat": syarat,
            "prioritas": prioritas, "simpul": simpul, "sekali": sekali}


def tahap(q, s):
    return "quest %s tahap %s" % (q, s)


def kenang(butir):
    """Route-aware talk after the chain (D-TK2): butir = [(node, conditions, (question id, en), (answer id, en), gesture)] ->
    (choices for the start node, nodes). A choice whose conditions fail stays hidden, so each player hears only the lines
    of the road they took (IblisCerita "iblis_rute" is per player)."""
    pil = [C(q, nid, syarat=list(sy)) for nid, sy, q, a, g in butir]
    sim = [N(nid, a, g, "", pilihan=[C(("Ada hal lain.", "Something else."), "mulai"), C(("Aku pergi.", "I'll go."))])
           for nid, sy, q, a, g in butir]
    return pil, sim


SELESAI = "quest tema_iblis_rantai selesai"
XUE, TU, BAI, MO, YE = "tokoh:xue_yao", "tokoh:tu_gang", "tokoh:bai_she", "tokoh:mo_wen", "tokoh:ye_wuhen"
AFU, HAN, HONG, LAO = "tokoh:a_fu", "tokoh:han_qingshuang", "tokoh:hong_pao", "tokoh:lao_kuang"
BYE = ("Aku pergi.", "I'll go.")
PETA = "kultivator:tema/peta_gerbang_rahasia"
KOTA = "iblis_di_kota"

YE_PIL, YE_SIM = kenang([
    ("tu_gang", [SELESAI, "iblis_rute lurus"],
     ("Tu Gang memerintah sekarang. Kau tidak keberatan?", "Tu Gang rules now. You don't mind?"),
     ("Si Jagal? Ia memerintah seperti mengurus lubangnya: keras, adil, dan selalu menghitung uang dua kali. Aku lebih suka begitu daripada "
      "senyum Xue Yao. Lagi pula, aku tidak pernah ingin takhta. Aku hanya ingin mendengar pagi.",
      "The Butcher? He rules the way he runs his pit: hard, fair, and always counting the money twice. I prefer that to Xue Yao's smile. "
      "Besides, I never wanted a throne. I only wanted to hear morning."), "sikap_tangan_belakang"),
    ("duel_aula", [SELESAI, "iblis_rute darah", "bendera iblis_jamuan_tolak"],
     ("Kau tidak dendam soal duel di aula?", "No grudge about the duel in the hall?"),
     ("Kau menolak membunuhku di meja Xue Yao. Di kota ini, itu lebih langka dari giok merah. Kita berdiri di sisi yang berbeda, tapi aku "
      "berutang satu nyawa padamu. Jangan habiskan utang itu untuk hal remeh.",
      "You refused to kill me at Xue Yao's table. In this city that is rarer than red jade. We stand on different sides, but I owe you "
      "one life. Don't spend that debt on something small."), "bicara_tangan"),
])
HAN_PIL, HAN_SIM = kenang([
    ("markas", [SELESAI, "iblis_rute lurus"],
     ("Pos Pemburu sekarang markas tetap?", "The Hunters' Post is a garrison now?"),
     ("Atas nama tujuh sekte. Para tetua ingin menyebutnya kemenangan surga; aku menyebutnya pekerjaan yang belum selesai. Kota itu tidak "
      "berubah dalam semalam hanya karena rantainya putus. Tapi kandangnya kosong, dan itu cukup untuk tahun ini.",
      "In the name of seven sects. The elders want to call it heaven's victory; I call it unfinished work. That city does not change "
      "overnight just because its chain broke. But the pens are empty, and that is enough for this year."), "sikap_gagang"),
    ("peta_ye", [SELESAI, "iblis_rute penebusan"],
     ("Kau marah aku memberikan peta itu pada Ye Wuhen?", "Are you angry I gave the map to Ye Wuhen?"),
     ("Marah? Aku ingin pedangku di leher Xue Yao, dan kau memberiku seorang iblis yang membawa tawanan keluar tanpa satu pun mati. Itu "
      "membuatku berpikir terlalu lama di malam hari. Pergilah. Papan buronan tetap terbuka untukmu.",
      "Angry? I wanted my sword at Xue Yao's throat, and you gave me a demon who led the captives out without a single death. It keeps "
      "me thinking too long at night. Go. The bounty board is still open to you."), "sikap_tangan_belakang"),
    ("gerbang", [SELESAI, "iblis_rute darah"],
     ("Kau masih mengingat duel di gerbang?", "Do you still remember the duel at the gate?"),
     ("Setiap kali aku memegang pedang. Kau menang dengan bersih, dan itu yang paling menyakitkan. Aku tidak dendam. Tapi namamu kini "
      "tertulis di buku Pos ini, dan suatu hari seorang murid yang lebih muda dariku akan membacanya.",
      "Every time I hold a sword. You won cleanly, and that hurts the most. I hold no grudge. But your name is written in this Post's "
      "book now, and one day a disciple younger than me will read it."), "sikap_gagang"),
])
TU_PIL, TU_SIM = kenang([
    ("tebus_laga", [SELESAI, "iblis_rute penebusan"],
     ("Bagaimana lubangmu sejak Ye Wuhen memerintah?", "How is your pit since Ye Wuhen took the throne?"),
     ("Ia melarang laga sampai mati. Penonton menggerutu seminggu, lalu kembali. Ternyata mereka datang untuk taruhannya, bukan "
      "darahnya. Aku kehilangan dua jagal dan mendapat tiga penjual kacang. Hah!",
      "He banned bouts to the death. The crowd grumbled for a week, then came back. Turns out they come for the bets, not the blood. I "
      "lost two butchers and gained three peanut sellers. Hah!"), "tertawa"),
    ("tamu_xue", [SELESAI, "iblis_rute darah"],
     ("Kau melihatku membela gerbang?", "Did you see me defend the gate?"),
     ("Kau membela rahang tengkorak itu lebih baik dari separuh penegakku. Tapi jangan kira aku lupa: tamu Xue Yao tidak pernah naik "
      "Tangga Darah dengan lawan murahan. Lubangku punya harga keluarganya sendiri.",
      "You held those skull jaws better than half my enforcers. But don't think I forget: Xue Yao's guest never climbs the Blood Ladder "
      "against cheap opponents. My pit has its own family price."), "sikap_berkacak"),
])
TUP_PIL, TUP_SIM = kenang([
    ("kenapa", ["iblis_rute lurus"],
     ("Kenapa kau mau memerintah?", "Why did you agree to rule?"),
     ("Karena Ketua Han memintaku, dan karena aku satu-satunya orang yang ditakuti kedua pihak. Penegak takut goloku, pemburu takut "
      "bukuku. Bukuku berisi utang semua orang. Itu senjata yang lebih tajam.",
      "Because Leader Han asked, and because I'm the only man both sides fear. The enforcers fear my cleaver, the hunters fear my ledger. "
      "My ledger holds everyone's debts. That's the sharper weapon."), "sikap_berkacak"),
    ("afu", ["iblis_afu_bebas"],
     ("Apa kabar A-Fu?", "Any news of A-Fu?"),
     ("Bocah itu? Pulang ke desanya membawa sekarung kenari dari pasar. Kenari! Empat kali kabur dari kandangku, dan akhirnya keluar lewat "
      "pintu depan. Kalau ia kembali sebagai pendekar suatu hari, aku tidak akan heran.",
      "That brat? Went home to her village with a sack of walnuts from the market. Walnuts! Ran from my pens four times and finally walked "
      "out the front door. If she comes back a swordswoman one day, I won't be surprised."), "tertawa"),
])
BAI_PIL, BAI_SIM = kenang([
    ("sewa_naik", [SELESAI, "iblis_rute lurus"],
     ("Bagaimana pasar sejak Tu Gang memerintah?", "How is the market since Tu Gang rules?"),
     ("Si Jagal menaikkan sewa kios dan melarang rantai di pasar. Ironis, bukan? Sekarang barang yang paling laku adalah kunci. Setiap "
      "orang ingin mengunci sesuatu, sayang, dan aku menjual kuncinya.",
      "The Butcher raised stall rents and banned chains in the market. Ironic, isn't it? Now the best-selling goods are locks. Everyone "
      "wants to lock something away, darling, and I sell the locks."), "kipas"),
    ("pajak_cahaya", [SELESAI, "iblis_rute penebusan"],
     ("Ye Wuhen mengubah pasar?", "Did Ye Wuhen change the market?"),
     ("Pasar Seribu Bayangan kini buka siang hari. Siang! Bayanganku menyusut, pelangganku menyipitkan mata, dan aku harus menjual payung. "
      "Tapi untung tetap untung. Bai She tidak pernah kalah oleh matahari.",
      "The Market of a Thousand Shadows now opens by day. By day! My shadows shrink, my customers squint, and I've had to sell parasols. "
      "But profit is profit. Bai She has never lost to the sun."), "kipas"),
    ("keluarga", [SELESAI, "iblis_rute darah"],
     ("Ada harga khusus untuk Tamu Darah?", "Any special price for the Blood Guest?"),
     ("Harga keluarga, tentu. Dan sedikit nasihat gratis: tamu kesayangan Xue Yao sebelum dirimu duduk di kursi yang sama selama sembilan "
      "tahun. Lalu suatu malam ia tidak datang ke jamuan. Nikmati kursimu, sayang.",
      "The family price, of course. And a little free advice: Xue Yao's favourite guest before you sat in that same chair for nine "
      "years. Then one night he did not come to the banquet. Enjoy your chair, darling."), "bisik"),
])
MO_PIL, MO_SIM = kenang([
    ("rantai_kitab", [SELESAI],
     ("Rantai kota ini putus. Apa kata kitab-kitabmu?", "This city's chain is broken. What do your books say?"),
     ("Kitab tidak peduli siapa yang memerintah. Mereka hanya lapar. Tapi rak di lantai tiga berhenti berbisik sejak malam itu. Mungkin "
      "mereka sedang menunggu, seperti aku, untuk tahu kota macam apa yang akan tumbuh dari tulang yang sama.",
      "Books don't care who rules. They are only hungry. But the shelf on floor three stopped whispering since that night. Perhaps they "
      "are waiting, like me, to learn what kind of city grows from the same bones."), "membaca"),
    ("lantai_sembilan", [SELESAI, "iblis_rute darah"],
     ("Lantai sembilan mengenalku sekarang?", "Does floor nine know me now?"),
     ("Ia mengenal langkahmu sebelum kau naik tangga. Hati-hati: kitab yang mengenal pembacanya juga tahu apa yang paling ingin ia makan "
      "darimu. Aku sudah memberinya mataku. Jangan beri ia sesuatu yang lebih mahal.",
      "It knows your step before you reach the stairs. Careful: a book that knows its reader also knows what it most wants to eat from "
      "you. I gave it my eyes. Don't give it anything dearer."), "bicara_tangan"),
])

DIALOG = [
    # ================================================================ the gate
    D("tema_iblis_upeti", "peran:penegak_iblis", [KOTA, "pita 2"], 20, [
        N("mulai", ("Berhenti. Kalau mau lewat, tinggalkan upeti. Lebih murah dari nyawamu.",
                    "Halt. Leave a tribute if you'd pass. It's cheaper than your life."),
          "berdiri_jaga", "", suara="tema.iblis.gate_toll", pilihan=[
              C(("Ini upetiku.", "Here is my tribute."), "lewat", akibat=["iblis_bayar_upeti"]),
              C(("Apa aturan kota ini?", "What are this city's rules?"), "aturan"),
              C(("Nanti saja.", "Later.")),
          ]),
        N("aturan", ("Satu aturan. Di Pasar Seribu Bayangan dan Kedai Cawan Racun, darah tidak boleh tumpah: itu Perjanjian Pasar. "
                     "Di luar itu, yang kuat benar. Malam hari, jalan-jalan punya pemburunya sendiri. Upeti dibayar sekali sehari.",
                     "One rule. In the Market of a Thousand Shadows and the Poison Cup Tavern no blood is spilled: that is the Market Pact. "
                     "Outside, the strong are right. At night the streets have hunters of their own. The toll is paid once a day."),
          "tunjuk", "", pilihan=[
              C(("Ini upetiku.", "Here is my tribute."), "lewat", akibat=["iblis_bayar_upeti"]),
              C(BYE),
          ]),
        N("lewat", ("Masuklah. Dan ingat: tidak ada yang akan menolongmu di sini.", "Go in. And remember: nobody will help you here."),
          "sikap_lipat_tangan"),
    ]),
    D("tema_iblis_upeti_lemah", "peran:penegak_iblis", [KOTA, "!pita 2"], 25, [
        N("mulai", ("Ikan teri sepertimu bahkan tak pantas jadi umpan. Pulang sana, sebelum ada yang menganggapmu camilan.",
                    "Small fry like you isn't even good bait. Go home, before someone takes you for a snack."),
          "ancam", "", suara="tema.iblis.gate_weak", pilihan=[C(("...Baik.", "...Fine."))]),
    ]),
    D("tema_iblis_penegak_ditakuti", "peran:penegak_iblis", [KOTA, "gentar 80"], 30, [
        N("mulai", ("Yang Ditakuti. Silakan lewat. Tidak ada upeti untukmu.", "Feared One. Please pass. No toll for you."), "hormat"),
    ]),
    # ================================================================ chapter 1: Ye Wuhen's warning
    D("tema_iblis_ye_peringatan", YE, [tahap(Q, "peringatan")], 70, [
        N("mulai", ("Kau baru membayar upeti, ya? Kelihatan dari caramu memandang tiang-tiang itu. Itu Lapangan Peringatan: senjata "
                    "patah dan topeng retak dari mereka yang pernah menantang Penguasa. Dengarkan aku sebentar, %s, dan mungkin "
                    "senjatamu tidak ikut tergantung di sini.",
                    "You just paid the toll, didn't you? I can tell by how you look at those posts. That is the Warning Field: "
                    "broken weapons and cracked masks of everyone who ever challenged the Lord. Listen to me for a moment, %s, and "
                    "maybe your weapon won't end up hanging here too."),
          "sikap_tangan_belakang", "", isian=["pemain"], pilihan=[
              C(("Aku mendengarkan.", "I'm listening."), "aturan"),
              C(("Kenapa kau peduli pada orang asing?", "Why would you care about a stranger?"), "kenapa"),
          ]),
        N("aturan", ("Zona Perjanjian, di pasar dan kedai, satu-satunya tanah aman. Di luar itu, sejak pukul tujuh malam sampai subuh, "
                     "ada Jam Berburu: mereka akan menghadangmu dan meminta upeti jalan. Bayar, atau berdarah. Dan jangan pernah "
                     "membunuh di dalam pasar. Penegak akan memburumu seperti anjing.",
                     "The Pact Zone, the market and the taverns, is the only safe ground. Outside it, from seven at night until dawn, "
                     "comes the Hunting Hour: they will bar your way and ask a road toll. Pay, or bleed. And never kill inside the "
                     "market. The enforcers will hunt you like dogs."),
          "bicara_tangan", "", pilihan=[
              C(("Kenapa kau memberitahuku semua ini?", "Why tell me all this?"), "kenapa"),
              C(("Terima kasih. Ada lagi?", "Thank you. Anything else?"), "kandang"),
          ]),
        N("kenapa", ("Karena dua puluh tahun lalu, aku juga orang asing di sini. Mereka mengambilku dari desa waktu aku enam tahun. "
                     "Aku tumbuh di kandang itu, lalu di ruang gladiator, lalu... aku menjadi salah satu dari mereka. Hampir.",
                     "Because twenty years ago I was a stranger here too. They took me from my village when I was six. I grew up in "
                     "those pens, then in the gladiators' room, then... I became one of them. Almost."),
          "tatap_langit", "", pilihan=[C(("Hampir?", "Almost?"), "kandang")]),
        N("kandang", ("Kalau kau masih punya hati, pergilah lihat Kandang Tawanan di timur laut. Ada seorang anak di sana yang terus "
                      "mencoba kabur. Dia mengingatkanku pada diriku sendiri. Lihat saja dulu. Itu saja yang kuminta.",
                      "If you still have a heart, go and look at the Captive Pens to the north-east. There's a child there who keeps "
                      "trying to escape. She reminds me of myself. Just look. That is all I ask."),
          "tunjuk", "", akibat=["peristiwa tanda iblis_peringatan"], pilihan=[
              C(("Aku akan melihatnya.", "I'll go and see.")),
          ]),
    ]),
    D("tema_iblis_ye_sapa", YE, [KOTA], 10, [
        N("mulai", ("Masih hidup? Bagus. Di kota ini, itu sudah prestasi.", "Still alive? Good. In this city that is an achievement."),
          "sikap_tangan_belakang", "", pilihan=[
              C(("Ceritakan tentang Penguasa Kota.", "Tell me about the city's ruler."), "xue", syarat=["iblis_penguasa xue_yao"]),
              C(("Bagaimana rasanya memimpin kota ini?", "How does it feel to rule this city?"), "penguasa", syarat=["iblis_penguasa ye_wuhen"]),
              *YE_PIL,
              C(BYE),
          ]),
        N("xue", ("Xue Yao. Sopan, tenang, dan sudah membunuh lebih banyak orang daripada yang bisa kau hitung. Dia bilang kota ini satu-satunya "
                  "tempat jujur di dunia: setidaknya di sini tak ada yang berpura-pura suci.",
                  "Xue Yao. Polite, calm, and she has killed more people than you could count. She says this city is the only honest place "
                  "in the world: at least here nobody pretends to be pure."), "bicara_tangan"),
        N("penguasa", ("Berat. Pagi datang setiap hari sekarang, tapi orang-orang di sini belum terbiasa dengan cahaya. Kandang itu kini Rumah "
                       "Tebusan. Kadang aku masih mendengar suara rantai di malam hari.",
                       "Heavy. Morning comes every day now, but the people here are not used to light yet. Those pens are the House of "
                       "Ransom now. Sometimes I still hear chains at night."), "tatap_langit", suara="tema.iblis.dawn"),
        *YE_SIM,
    ]),
    # ================================================================ chapter 2: A-Fu
    D("tema_iblis_afu_mohon", AFU, [tahap(Q, "kandang")], 70, [
        N("mulai", ("Kakak! Kakak bukan orang sini, kan? Bajumu tidak bau darah. Tolong... aku ingin pulang ke desaku. Ibu pasti masih "
                    "menungguku di bawah pohon kenari.",
                    "Hey! You're not from here, are you? Your clothes don't smell of blood. Please... I want to go home to my village. "
                    "Mother must still be waiting for me under the walnut tree."),
          "berlutut_rantai", "", suara="tema.iblis.captive", pilihan=[
              C(("Siapa namamu?", "What's your name?"), "nama"),
              C(("Aku akan mencari cara mengeluarkanmu.", "I'll find a way to get you out."), "janji"),
          ]),
        N("nama", ("A-Fu! Aku sudah tiga kali kabur. Tiga kali ditangkap si Jagal. Dia bilang kalau ada yang mau menebusku, atau bertarung "
                   "di arena untukku, pintuku boleh dibuka. Tapi siapa yang mau?",
                   "A-Fu! I've escaped three times. Caught three times by the Butcher. He says if someone pays for me, or fights in the "
                   "arena for me, my door can open. But who would?"),
          "duduk_lantai", "", pilihan=[C(("Aku mau.", "I would."), "janji")]),
        N("janji", ("Sungguh? Kau janji? Tu Gang ada di arena. Penjaga kandang juga bisa menerima tebusan. Cepat ya, Kak... sebelum aku dijual.",
                    "Really? You promise? Tu Gang is at the arena. The pen jailer can take a ransom too. Hurry, okay... before they sell me."),
          "menangis", "", akibat=["peristiwa tanda iblis_afu_temu"]),
    ]),
    D("tema_iblis_afu_tunggu", AFU, [tahap(Q, "tebus_afu")], 70, [
        N("mulai", ("Kakak kembali! Kau sudah bicara dengan penjaga? Atau si Jagal?", "You came back! Did you talk to the jailer? Or the Butcher?"),
          "berlutut_rantai", "", pilihan=[
              C(("Aku akan menebusmu sekarang.", "I'll ransom you now."), akibat=["buka_layanan tema_tawanan"]),
              C(("Aku akan bertarung untukmu di arena.", "I'll fight for you in the arena."), "darah"),
              C(("Maaf, A-Fu. Aku tidak bisa.", "I'm sorry, A-Fu. I can't."), "abai"),
          ]),
        N("darah", ("Tebusan Darah? Kau gila... tapi terima kasih! Bicaralah dengan Tu Gang, atau pilih Tebusan Darah di daftar kandang.",
                    "A Blood Ransom? You're mad... but thank you! Talk to Tu Gang, or choose Blood Ransom on the pens' list."),
          "sikap_anak", "", pilihan=[C(("Buka daftar kandang.", "Open the pens' list."), akibat=["buka_layanan tema_tawanan"]), C(BYE)]),
        N("abai", ("...Tidak apa-apa. Aku sudah biasa. Aku akan kabur sendiri lagi. Keempat kalinya pasti berhasil.",
                   "...It's alright. I'm used to it. I'll run away on my own again. The fourth time will work, surely."),
          "takut_sembunyi", "", akibat=["iblis_afu abai"]),
    ]),
    D("tema_iblis_tugang_tebusan", TU, [tahap(Q, "tebus_afu")], 65, [
        N("mulai", ("Hah! Kau yang ditanyakan bocah cerewet itu. Aturanku sederhana: bayar penjaga, atau berdarah di lubangku. Tebusan "
                    "Darah, lawannya satu tingkat lebih keras. Kalau kau roboh, anak itu tetap di kandang dan kau diseret keluar.",
                    "Hah! You're the one the chatty brat keeps asking about. My rules are simple: pay the jailer, or bleed in my pit. "
                    "Blood Ransom, the foe is one tier harder. If you drop, the kid stays caged and you get dragged out."),
          "sikap_berkacak", "", pilihan=[
              C(("Aku terima Tebusan Darah.", "I accept the Blood Ransom."), akibat=["buka_layanan tema_tawanan"]),
              C(("Kenapa kau peduli pada aturan?", "Why do you care about rules?"), "aturan"),
              C(BYE),
          ]),
        N("aturan", ("Karena di kota tanpa hukum, aturanku satu-satunya yang tidak bohong. Yang kuat menang, yang menang dibayar. Adil.",
                     "Because in a lawless city my rules are the only ones that don't lie. The strong win, the winner gets paid. Fair."),
          "tertawa"),
    ]),
    # ================================================================ chapter 3: the Shadow Oil
    D("tema_iblis_baishe_minyak", BAI, [tahap(Q, "minyak")], 70, [
        N("mulai", ("Oh, tamu yang membebaskan tikus kecil itu. Aku suka orang yang berani menghabiskan uang untuk hal yang tak berguna. "
                    "Duduklah. Kau pernah mencium Minyak Bayangan? Sekali tetes, lentera menyala tujuh malam tanpa asap. Pelabuhan dan "
                    "kota air sangat menyukainya... dan sekte lurus sangat membencinya.",
                    "Oh, the guest who freed that little mouse. I like people brave enough to spend money on useless things. Sit. Have you "
                    "ever smelled Shadow Oil? One drop and a lantern burns seven nights without smoke. The harbours and the canal cities "
                    "adore it... and the righteous sects loathe it."),
          "kipas", "kipas_sutra", suara="tema.iblis.broker", pilihan=[
              C(("Apa yang kau tawarkan?", "What are you offering?"), "tawar"),
              C(("Dari mana minyak itu?", "Where does the oil come from?"), "asal"),
          ]),
        N("asal", ("Dari tulang-tulang di bawah kota ini, sayang. Darah tiga ribu tahun, diperas pelan-pelan. Gudangnya di Sarang Kontrak; "
                   "Hong Pao yang membawanya ke luar. Kau mungkin pernah melihat jubah merahnya.",
                   "From the bones beneath this city, darling. Three thousand years of blood, pressed out slowly. The store is at the "
                   "Contract Den; Hong Pao carries it out. You may have seen his red robe."),
          "bisik", "", pilihan=[C(("Lalu tawaranmu?", "And your offer?"), "tawar")]),
        N("tawar", ("Sebuah bagian: %s, dibayar dalam lima hari. Yang perlu kau lakukan hanya... tidak melihat apa pun.",
                    "A share: %s, paid over five days. All you need to do is... see nothing."),
          "hitung_koin_gelap", "kantong_uang", isian=["nb_pita:200"], pilihan=[
              C(("Aku ambil bagiannya.", "I'll take the share."), "beli", akibat=["iblis_minyak beli"]),
              C(("Aku akan memikirkannya.", "I'll think about it."), "pikir"),
          ]),
        N("beli", ("Pilihan bijak. Selamat datang di keluarga, sayang. Jangan khawatir soal kabar dari sekte lurus; mereka selalu mengeluh.",
                   "A wise choice. Welcome to the family, darling. Don't mind the talk of the righteous sects; they always complain."),
          "tertawa", "kipas_sutra"),
        N("pikir", ("Tentu. Gudangnya dijaga, tapi pintunya tidak pernah dikunci. Siapa yang berani mencuri dari Bai She?",
                    "Of course. The store is guarded, but its door is never locked. Who would dare steal from Bai She?"),
          "kipas", "kipas_sutra"),
    ]),
    D("tema_iblis_hongpao", HONG, [tahap(Q, "minyak")], 65, [
        N("mulai", ("...", "..."), "sikap_lipat_tangan", "", pilihan=[
            C(("Kau Hong Pao? Kurir Bai She?", "You're Hong Pao? Bai She's courier?"), "kenal", syarat=["iblis_kenal_minyak"]),
            C(("Kau Hong Pao? Kurir Bai She?", "You're Hong Pao? Bai She's courier?"), "asing", syarat=["!iblis_kenal_minyak"]),
        ]),
        N("kenal", ("Kau. Aku ingat wajahmu, dari dermaga dan kanal. Kau terus mengikuti jejak minyakku. Gudangnya di belakangku, kalau "
                    "itu yang kau cari. Enam tong. Jangan bilang aku yang memberitahumu.",
                    "You. I remember your face, from the docks and the canals. You keep following my oil's trail. The store is behind me, "
                    "if that's what you're after. Six barrels. Don't say I told you."), "bisik"),
        N("asing", ("Aku tidak menjual apa-apa. Aku hanya membawa. Kalau kau tidak membawa apa-apa, pergilah.",
                    "I sell nothing. I only carry. If you carry nothing, leave."), "sikap_lipat_tangan"),
    ]),
    # ================================================================ chapter 4: the Secret Gate map
    D("tema_iblis_han_peta", HAN, [tahap(Q, "peta")], 70, [
        N("mulai", ("Kau datang dari kota itu. Bau dupa darahnya masih menempel. Aku Han Qingshuang, murid inti %s. Dengar: penyerbuan "
                    "kami selalu patah di Gerbang Tengkorak. Kalau ada jalan lain ke dalam... konon Si Buta Mo Wen menyimpan Peta Gerbang "
                    "Rahasia di lantai empat menaranya.",
                    "You come from that city. Its blood incense still clings to you. I am Han Qingshuang, core disciple of %s. Listen: our "
                    "sieges always break at the Skull Gate. If there were another way inside... they say Blind Mo Wen keeps the Map of the "
                    "Secret Gate on the fourth floor of his tower."),
          "sikap_gagang", "", isian=["sekte_lurus"], suara="tema.iblis.hunter", pilihan=[
              C(("Kenapa aku harus membantumu?", "Why should I help you?"), "kenapa"),
              C(("Aku akan mencari peta itu.", "I'll look for that map."), "cari"),
          ]),
        N("kenapa", ("Karena di balik tembok itu ada kandang, dan di kandang itu ada orang-orang desa yang tidak pernah memilih jalan iblis. "
                     "Aku tidak memercayaimu. Tapi aku tidak punya pilihan lain.",
                     "Because behind that wall there are pens, and in those pens are village folk who never chose the demonic path. I do "
                     "not trust you. But I have no other choice."),
          "sikap_tangan_belakang", "", pilihan=[C(("Aku akan mencarinya.", "I'll find it."), "cari")]),
        N("cari", ("Mo Wen buta, tapi dia melihat lewat kitab. Jawab teka-tekinya. Dan jangan berikan peta itu pada siapa pun selain aku.",
                   "Mo Wen is blind, but he sees through books. Answer his riddle. And give that map to no one but me."), "tunjuk"),
    ]),
    D("tema_iblis_mowen_teka", MO, [tahap(Q, "peta"), "!punya " + PETA], 70, [
        N("mulai", ("Langkahmu berbau gerbang. Kau mencari peta yang tidak pernah digambar. Lantai empat menyimpan tiga kitab; jawab ketiganya "
                    "dan peta itu milikmu. Salah satu saja, dan kitab-kitab itu akan tertidur lagi untukmu.",
                    "Your steps smell of gates. You seek a map that was never drawn. Floor four keeps three books; answer all three and the map "
                    "is yours. Miss even one, and the books fall asleep for you again."),
          "membaca", "kitab", suara="tema.iblis.forbidden", pilihan=[
              C(("Aku siap.", "I am ready."), "satu", syarat=["gentar 30"], tampil=True,
                alasan=("Lantai empat menolakmu (butuh Gentar 30).", "Floor four rejects you (Dread 30 needed).")),
              C(BYE),
          ]),
        N("satu", ("Kitab pertama: 'Aku punya kota tanpa rumah, sungai tanpa air, dan gunung tanpa batu. Apa aku?'",
                   "The first book: 'I have cities without houses, rivers without water and mountains without stone. What am I?'"),
          "membaca", "kitab", pilihan=[
              C(("Mimpi.", "A dream."), "salah"),
              C(("Peta.", "A map."), "dua"),
              C(("Lukisan.", "A painting."), "salah"),
          ]),
        N("dua", ("Kitab kedua: 'Semakin banyak kau ambil dariku, semakin besar aku. Tiap tawanan yang kabur meninggalkanku di dinding kandang.'",
                  "The second book: 'The more you take from me, the bigger I grow. Every captive who escapes leaves me in the wall of the pen.'"),
          "membaca", "kitab", pilihan=[
              C(("Lubang.", "A hole."), "tiga"),
              C(("Rindu.", "Longing."), "salah"),
              C(("Utang.", "Debt."), "salah"),
          ]),
        N("tiga", ("Kitab ketiga: 'Aku tidak terlihat oleh penegak, tidak tersentuh rantai, dan aku membuka setiap gerbang. Setiap orang yang "
                   "lolos dari sini membawaku pergi.'",
                   "The third book: 'No enforcer can see me, no chain can touch me, and I open every gate. Everyone who escapes this place "
                   "carries me away.'"),
          "membaca", "kitab", pilihan=[
              C(("Uang.", "Money."), "salah"),
              C(("Kematian.", "Death."), "salah"),
              C(("Harapan.", "Hope."), "benar"),
          ]),
        N("benar", ("Harapan. Ya. Peta ini digambar seorang tawanan seratus tahun lalu, dengan kukunya, di dinding terowongan. Bawalah. "
                    "Ke tangan siapa peta itu jatuh, itulah kota ini besok.",
                    "Hope. Yes. This map was drawn by a captive a hundred years ago, with his fingernails, on the tunnel wall. Take it. "
                    "Whoever's hand it falls into, that is this city tomorrow."),
          "membaca", "gulungan", akibat=["iblis_beri peta"]),
        N("salah", ("Kitab itu menutup dirinya. Kembalilah nanti, ketika ia sudah lupa wajahmu.",
                    "The book closes itself. Come back later, when it has forgotten your face."), "sikap_tua"),
    ]),
    D("tema_iblis_pilih_han", HAN, [tahap(Q, "pilih"), "punya " + PETA], 75, [
        N("mulai", ("Itu... petanya? Gerbang rahasia di bawah tembok timur. Berikan padaku, dan penyerbuan berikutnya tidak akan berhenti di "
                    "rahang tengkorak.",
                    "That... is the map? A secret gate under the eastern wall. Give it to me, and the next siege won't stop at the skull's jaws."),
          "sikap_gagang", "", pilihan=[
              C(("Ambillah. Akhiri kandang itu.", "Take it. End those pens."), "serah",
                akibat=["ambil " + PETA + " 1", "iblis_rute lurus", "peristiwa tanda iblis_peta_serah"]),
              C(("Belum.", "Not yet.")),
          ]),
        N("serah", ("Aku salah menilaimu. Saat penyerbuan dimulai, buka gerbang itu dari dalam: tuasnya di rumah bea. Aku akan menunggu di luar.",
                    "I misjudged you. When the siege begins, open the gate from inside: the lever is in the toll house. I'll be waiting outside."),
          "hormat"),
    ]),
    D("tema_iblis_pilih_ye", YE, [tahap(Q, "pilih"), "punya " + PETA], 75, [
        N("mulai", ("Peta Mo Wen? Kalau Han mendapatkannya, kota ini akan terbakar dan para tawanan ikut terbakar bersamanya. Berikan padaku. "
                    "Ada Terowongan Tua dari kandang ke luar tembok. Aku bisa membawa mereka keluar saat semua mata tertuju ke gerbang.",
                    "Mo Wen's map? If Han gets it this city burns, and the captives burn with it. Give it to me. There's an Old Tunnel from the "
                    "pens to beyond the wall. I can lead them out while every eye is on the gate."),
          "bicara_tangan", "", pilihan=[
              C(("Bawa mereka pulang, Ye Wuhen.", "Take them home, Ye Wuhen."), "serah",
                akibat=["ambil " + PETA + " 1", "iblis_rute penebusan", "peristiwa tanda iblis_peta_serah"]),
              C(("Belum.", "Not yet.")),
          ]),
        N("serah", ("Terima kasih. Saat penyerbuan dimulai, temui aku di ujung dalam terowongan. Kita akan butuh seseorang yang bisa bertarung.",
                    "Thank you. When the siege begins, meet me at the inner end of the tunnel. We'll need someone who can fight."),
          "hormat_dalam"),
    ]),
    D("tema_iblis_pilih_xue", XUE, [tahap(Q, "pilih"), "punya " + PETA], 75, [
        N("mulai", ("Kau membawa sesuatu yang tidak seharusnya ada. Peta gerbang yang bahkan aku sendiri tidak tahu letaknya. Menarik. "
                    "Apa yang kau inginkan?",
                    "You carry something that should not exist. A map of a gate even I did not know of. Interesting. What do you want?"),
          "sikap_angkuh", "", suara="tema.iblis.lord", pilihan=[
              C(("Aku melaporkan ini padamu, Penguasa.", "I report this to you, my Lord."), "serah",
                akibat=["ambil " + PETA + " 1", "iblis_rute darah", "peristiwa tanda iblis_peta_serah"]),
              C(("Tidak ada.", "Nothing.")),
          ]),
        N("serah", ("Kesetiaan. Barang paling langka di kotaku. Saat sekte lurus datang lagi, berdirilah di gerbangku. Dan kalahkan murid inti itu "
                    "di depan semua orang.",
                    "Loyalty. The rarest ware in my city. When the righteous come again, stand at my gate. And beat that core disciple in "
                    "front of everyone."),
          "kipas", "kipas_sutra"),
    ]),
    # ================================================================ chapter 5: the siege
    D("tema_iblis_han_serbu", HAN, ["iblis_serbu"], 72, [
        N("mulai", ("Gelombang berikutnya sebentar lagi. Kau di pihak siapa, orang dari kota iblis?",
                    "The next wave is coming. Whose side are you on, you from the demon city?"),
          "sikap_gagang", "", pilihan=[
              C(("Jalan lurus.", "The righteous path."), "lurus", akibat=["iblis_pihak lurus"]),
              C(("Hadapi aku dalam duel, Han Qingshuang.", "Face me in a duel, Han Qingshuang."), "duel", syarat=[tahap(Q, "serbu_darah")]),
              C(BYE),
          ]),
        N("lurus", ("Bagus. Pegang sisi kiri. Jangan biarkan penegak menyentuh para pemanah.", "Good. Hold the left. Don't let the enforcers reach the archers."),
          "tunjuk"),
        N("duel", ("Jadi itu jawabanmu. Baik. Pedang lawan pedang. Kalau aku kalah, aku mundur. Kalau kau kalah... kau pulang dengan rantai.",
                   "So that's your answer. Very well. Blade against blade. If I lose, I withdraw. If you lose... you go home in chains."),
          "hunus", "", akibat=["mulai_duel"]),
    ]),
    D("tema_iblis_tugang_serbu", TU, ["iblis_serbu"], 72, [
        N("mulai", ("Orang-orang lurus di gerbang! Hadapi mereka! Kau berdiri di sisi mana, petarung?",
                    "The righteous are at the gate! Meet them head on! Which side do you stand on, fighter?"),
          "marah_tunjuk", "", suara="tema.iblis.raid", pilihan=[
              C(("Di sisi kota.", "The city's side."), "kota", akibat=["iblis_pihak kota"]),
              C(BYE),
          ]),
        N("kota", ("Ha! Begitu. Ambil tempat di depan rahang. Yang paling banyak menjatuhkan jubah putih minum gratis di Cawan Racun seminggu!",
                   "Ha! That's it. Take a place before the jaws. Whoever drops the most white robes drinks free at the Poison Cup for a week!"),
          "tertawa"),
    ]),
    D("tema_iblis_ye_terowongan", YE, [tahap(Q, "serbu_tebus")], 74, [
        N("mulai", ("Mereka datang. Batu-batu reruntuhan sudah kugeser dari dalam; saat gelombang pertama menghantam gerbang, terowongan terbuka. "
                    "Kau di depan, aku di belakang. Jangan berhenti sampai kita melihat langit.",
                    "They're coming. I've loosened the rubble stones from inside; when the first wave hits the gate, the tunnel opens. You "
                    "lead, I'll cover the rear. Don't stop until we see the sky."),
          "bicara_tangan", "", pilihan=[C(("Ayo.", "Let's go."))]),
    ]),
    # ================================================================ chapter 6
    D("tema_iblis_xue_tantang", XUE, [tahap(Q, "istana")], 80, [
        N("mulai", ("Kau membuka gerbangku, atau mencuri kandangku. Kini kau berdiri di depanku dengan pedang terhunus. Jarang sekali, orang "
                    "jalan lurus mengetuk gerbang neraka atas kemauan sendiri.",
                    "You opened my gate, or stole my pens. Now you stand before me with a drawn blade. How rare, a righteous one knocking at "
                    "hell's gate of their own accord."),
          "sikap_angkuh", "kipas_sutra", suara="tema.iblis.lord", pilihan=[
              C(("Kota ini berakhir malam ini, Xue Yao.", "This city ends tonight, Xue Yao."), "tantang"),
              C(("Kenapa kau membangun semua ini?", "Why did you build all this?"), "kenapa"),
          ]),
        N("kenapa", ("Di luar, sekte-sekte lurus membunuh atas nama surga dan menyebutnya keadilan. Di sini, kami membunuh atas nama untung dan "
                     "menyebutnya perdagangan. Setidaknya kami jujur.",
                     "Outside, the righteous sects kill in heaven's name and call it justice. Here we kill for profit and call it trade. At "
                     "least we are honest."),
          "kipas", "kipas_sutra", pilihan=[C(("Cukup.", "Enough."), "tantang")]),
        N("tantang", ("Kalau begitu, biar kolam darah ini memutuskan siapa yang jujur.", "Then let this blood pool decide who is honest."),
          "hunus", "", akibat=["iblis_xue_bos", "tutup"]),
    ]),
    D("tema_iblis_xue_jamuan", XUE, [tahap(Q, "jamuan"), "!bendera iblis_jamuan_tolak"], 80, [
        N("mulai", ("Duduklah, tamuku. Arak darah senja, tiga ratus tahun. Kau menjaga gerbangku dan memukul mundur murid inti itu. Satu ujian "
                    "lagi. Ye Wuhen menjual peta kotaku pada para pemberontak di kandang. Singkirkan dia, dan kau duduk di sampingku selamanya.",
                    "Sit, my guest. Dusk blood wine, three hundred years old. You held my gate and drove back that core disciple. One more "
                    "test. Ye Wuhen sold my city's map to the rebels in the pens. Remove him, and you sit beside me forever."),
          "minum_arak", "cawan_arak", pilihan=[
              C(("Anggap sudah terjadi.", "Consider it done."), "terima", akibat=["iblis_ye_pergi", "peristiwa iblis_xue jamuan"]),
              C(("Tidak. Aku tidak membunuh untukmu.", "No. I do not kill for you."), "tolak", akibat=["bendera iblis_jamuan_tolak"]),
          ]),
        N("terima", ("Dingin dan cepat. Aku menyukaimu. Ye Wuhen tidak akan terlihat lagi di kotaku.",
                     "Cold and quick. I like you. Ye Wuhen will not be seen in my city again."),
          "tertawa", "cawan_arak"),
        N("tolak", ("Hm. Menolak Xue Yao di mejanya sendiri. Baik. Bertarunglah dengan Ye Wuhen di aula, bukan sampai mati. Kalau kau menang, "
                    "kau membuktikan dirimu lebih tajam dari kesetiaanmu. Itu juga cukup bagiku.",
                    "Hm. Refusing Xue Yao at her own table. Very well. Fight Ye Wuhen in the hall, not to the death. If you win, you prove "
                    "yourself sharper than your loyalty. That is enough for me too."),
          "kipas", "kipas_sutra"),
    ]),
    D("tema_iblis_ye_duel", YE, [tahap(Q, "jamuan"), "bendera iblis_jamuan_tolak"], 80, [
        N("mulai", ("Kau menolaknya. Aku mendengarnya dari balik tirai. Kalau begitu kita bertarung, tapi tidak ada yang mati malam ini. Siap?",
                    "You refused her. I heard it from behind the curtain. Then we fight, but nobody dies tonight. Ready?"),
          "latih_jurus", "", pilihan=[
              C(("Siap.", "Ready."), akibat=["mulai_duel"]),
              C(("Sebentar lagi.", "In a moment.")),
          ]),
    ]),
    # ================================================================ after the chain
    D("tema_iblis_tugang_penguasa", TU, [KOTA, "iblis_penguasa tu_gang"], 30, [
        N("mulai", ("Penguasa? Aku? Ha! Aku cuma jagal yang kebetulan masih berdiri. Kandang sudah ditutup; para pemburu lurus bermarkas di pos "
                    "mereka. Harga pasar naik, tapi tidak ada lagi yang dijual dengan rantai. Aturanku masih sama: yang kuat menang.",
                    "Lord? Me? Ha! I'm just a butcher who happens to still be standing. The pens are closed; the righteous hunters keep their "
                    "post as a garrison. Market prices went up, but nothing is sold in chains anymore. My rules are the same: the strong win."),
          "sikap_berkacak", "", pilihan=[
              C(("Buka meja taruhan.", "Open the betting table."), akibat=["buka_layanan tema_taruhan"]), *TUP_PIL, C(BYE)]),
        *TUP_SIM,
    ]),
    D("tema_iblis_xue_tamu", XUE, [KOTA, "iblis_gelar tamu_darah", "iblis_penguasa xue_yao"], 40, [
        N("mulai", ("Tamu Darahku. Gerbangku terbuka untukmu tanpa upeti, pasarku memberimu harga keluarga, dan lantai sembilan mengenal namamu. "
                    "Sekte-sekte lurus menutup gerbang mereka untukmu, tentu saja. Mereka tidak pernah belajar menghargai kejujuran.",
                    "My Blood Guest. My gate opens for you without a toll, my market gives you the family price, and floor nine knows your "
                    "name. The righteous sects shut their gates to you, of course. They never learned to value honesty."),
          "kipas", "kipas_sutra", suara="tema.iblis.lord"),
    ]),
    D("tema_iblis_xue_sapa", XUE, [KOTA, "iblis_penguasa xue_yao"], 10, [
        N("mulai", ("Kau berdiri di depan Penguasa Darah Senja tanpa diundang. Bicaralah, sebelum aku bosan.",
                    "You stand before the Lord of the Dusk Blood uninvited. Speak, before I grow bored."),
          "sikap_angkuh", "", pilihan=[
              C(("Aku ingin mendengar tentang kota ini.", "I want to hear about this city."), "kota", syarat=["iblis_audiensi"], tampil=True,
                alasan=("Penguasa belum mau menerimamu (butuh Gentar 60).", "The Lord will not receive you yet (Dread 60 needed).")),
              C(BYE),
          ]),
        N("kota", ("Tiga ribu tahun lalu, Pemimpin Iblis Darah jatuh di tanah ini. Sekte lurus menyebutnya kemenangan. Mereka meninggalkan "
                   "tulangnya di sini, dan kami membangun gerbang dari rahangnya. Kota kami berdiri di atas kekalahan mereka yang mengira "
                   "sudah menang.",
                   "Three thousand years ago the Blood Demon Lord fell on this ground. The righteous sects called it victory. They left his "
                   "bones here, and we built a gate from his jaw. Our city stands on the defeat of those who thought they had won."),
          "kipas", "kipas_sutra"),
    ]),
    # ================================================================ the hunters' camp
    D("tema_iblis_han_sapa", HAN, [], 12, [
        N("mulai", ("Pos Pemburu. Kalau kau datang untuk hadiah, papan buronan di sana. Kalau kau datang dari kota itu dengan niat buruk, pedangku "
                    "di sini.",
                    "The Hunters' Post. If you came for bounties, the board is there. If you came from that city with ill intent, my sword "
                    "is here."),
          "sikap_gagang", "", pilihan=[
              C(("Tunjukkan papan buronan.", "Show me the bounty board."), akibat=["buka_layanan tema_kontrak"]),
              C(("Aku membawa Kontrak Kepala.", "I carry a Head Contract."), "kontrak", syarat=["punya kultivator:tema/kontrak_kepala"]),
              *HAN_PIL,
              C(BYE),
          ]),
        N("kontrak", ("Kontrak Kepala dari lelang gelap... Seratus lima puluh Nilai Batu, dan aku tidak bertanya bagaimana kau mendapatkannya.",
                      "A Head Contract from the black auction... A hundred and fifty Nilai Batu, and I won't ask how you got it."),
          "sikap_tangan_belakang", "", pilihan=[C(("Jual.", "Sell."), akibat=["iblis_jual_kontrak"]), C(BYE)]),
        *HAN_SIM,
    ]),
    D("tema_iblis_pemburu_sapa", "peran:pemburu_lurus", [], 8, [
        N("mulai", ("Pos Pemburu sekte lurus. Ketua Han memeriksa setiap laporan sendiri.",
                    "The righteous sects' Hunters' Post. Leader Han checks every report herself."),
          "berdiri_jaga", "", pilihan=[C(("Papan buronan.", "The bounty board."), akibat=["buka_layanan tema_kontrak"]), C(BYE)]),
    ]),
    D("tema_iblis_cegat", ["peran:pemburu_lurus", HAN], ["acak 0"], 0, [
        N("mulai", ("Atas nama jalan lurus, kepalamu kuambil! ...Kecuali kau mau menebus dosamu. Denda tobat %s, dan kau boleh pergi dengan "
                    "kepala utuh.",
                    "In the name of the righteous path, your head is forfeit! ...Unless you would atone. A penance fine of %s, and you may go "
                    "with your head attached."),
          "sikap_gagang", "", isian=["nb_pita:50"], suara="tema.iblis.hunter", pilihan=[
              C(("Aku menyerah dan menebus.", "I yield and atone."), "tobat", akibat=["iblis_pemburu menyerah"]),
              C(("Coba ambil kepalaku.", "Try and take my head."), "lawan", akibat=["iblis_pemburu lawan"]),
              C(("(Lari)", "(Run)"), akibat=["iblis_pemburu lari", "tutup"]),
          ]),
        N("tobat", ("Tobat diterima. Jangan biarkan namamu muncul di papan kami lagi.", "Penance accepted. Don't let your name appear on our board again."),
          "hormat"),
        N("lawan", ("Bersiaplah!", "On guard!"), "hunus"),
    ]),
    # ================================================================ the pens, the market, the den, the arena
    D("tema_iblis_tawanan_kandang", "peran:tawanan", [KOTA], 60, [
        N("mulai", ("Tolong... kau bukan penjaga, kan? Kami hanya ingin pulang.", "Please... you're not a guard, are you? We only want to go home."),
          "berlutut_rantai", "", suara="tema.iblis.captive", pilihan=[
              C(("Aku akan menebus kalian.", "I'll ransom you."), akibat=["iblis_tebus_lagi"]),
              C(("Bertahanlah.", "Hold on.")),
          ]),
    ]),
    D("tema_iblis_makelar_sapa", "peran:makelar_gelap", [KOTA], 6, [
        N("mulai", ("Sst. Racun, pil darah, peta sarang... di sini tak ada yang tak berharga. Lihat-lihat saja dulu.",
                    "Psst. Poisons, blood pills, lair maps... here nothing is without a price. Just have a look."),
          "bisik", "kantong_uang", pilihan=[C(("Tunjukkan barangmu.", "Show me your wares."), akibat=["buka_layanan tema_pasar_gelap"]), C(BYE)]),
    ]),
    D("tema_iblis_pembunuh_sapa", "peran:pembunuh_bayaran", [KOTA], 6, [
        N("mulai", ("Dinding kontrak di belakangku. Hanya binatang, geng saingan, begal dan anjing-anjing sekte lurus. Kami tidak membunuh warga. "
                    "Itu buruk untuk bisnis.",
                    "The contract wall is behind me. Only beasts, rival gangs, bandits and righteous dogs. We don't kill citizens. Bad for "
                    "business."),
          "asah_pedang", "pisau_ganda", pilihan=[C(("Lihat kontrak.", "See the contracts."), akibat=["buka_layanan tema_kontrak"]), C(BYE)]),
    ]),
    D("tema_iblis_tugang_sapa", TU, [KOTA], 10, [
        N("mulai", ("Mau naik Tangga Darah, atau cuma mau melihat orang lain berdarah? Dua-duanya bayar.",
                    "Want to climb the Blood Ladder, or just watch other people bleed? Either way you pay."),
          "sikap_berkacak", "", suara="tema.iblis.arena_open", pilihan=[
              C(("Buka meja Tangga Darah.", "Open the Blood Ladder table."), akibat=["buka_layanan tema_taruhan"]),
              C(("Daftarkan aku di Turnamen Darah.", "Enter me in the Blood Tournament."), akibat=["iblis_turnamen daftar", "tutup"],
                syarat=["iblis_pesta"]),
              *TU_PIL,
              C(BYE),
          ]),
        *TU_SIM,
    ]),
    D("tema_iblis_baishe_sapa", BAI, [KOTA], 10, [
        N("mulai", ("Apa yang kau inginkan, sayang? Di sini, tak ada yang tak berharga.", "What do you desire, darling? Here, nothing is without a price."),
          "kipas", "kipas_sutra", suara="tema.iblis.broker", pilihan=[
              C(("Pasar gelap.", "The black market."), akibat=["buka_layanan tema_pasar_gelap"]),
              C(("Ada pekerjaan?", "Any work?"), "topeng", syarat=["quest %s baru" % QT]),
              *BAI_PIL,
              C(BYE),
          ]),
        N("topeng", ("Sebenarnya ada. Regu pemburu lurus mencuri Topeng Tanpa Wajah dari kios topengku waktu penyerbuan terakhir. Topeng itu "
                     "tidak berguna bagi mereka; mereka mengambilnya untuk menghinaku. Ambil kembali, dan aku akan membayarmu dengan pantas.",
                     "As it happens, yes. A righteous squad stole a Faceless Mask from my mask stall during the last siege. It's useless to "
                     "them; they took it to insult me. Take it back, and I'll pay you properly."),
          "bisik", "", pilihan=[C(("Di mana mereka?", "Where are they?"), akibat=["quest_mulai " + QT, "iblis_topeng_regu"]), C(BYE)]),
        *BAI_SIM,
    ]),
    D("tema_iblis_baishe_topeng", BAI, [tahap(QT, "kembali")], 70, [
        N("mulai", ("Kau membawanya? Ah... baunya masih seperti dupa sekte. Menjijikkan. Simpan saja topeng itu; aku tak mau memakai sesuatu "
                    "yang pernah disentuh tangan pemburu. Ini upahmu.",
                    "You brought it? Ah... it still smells of sect incense. Disgusting. Keep the mask; I won't wear something a hunter's hand "
                    "has touched. Here is your fee."),
          "kipas", "kipas_sutra", akibat=["beri_nb_pita 8", "gentar 5", "peristiwa tanda iblis_topeng_serah"]),
    ]),
    # ================================================================ Mo Wen (shop and the side quest)
    D("tema_iblis_mowen_sapa", MO, [KOTA], 10, [
        N("mulai", ("Kitab ini memakan sesuatu dari pembacanya. Masih mau membukanya?",
                    "This book devours something of whoever reads it. Will you still open it?"),
          "membaca", "kitab", suara="tema.iblis.forbidden", pilihan=[
              C(("Tunjukkan rak terlarang.", "Show me the forbidden shelves."), akibat=["buka_layanan tema_kitab_terlarang"]),
              C(("Ada yang bisa kubantu?", "Can I help with anything?"), "kitab", syarat=["quest %s baru" % QK]),
              *MO_PIL,
              C(BYE),
          ]),
        N("kitab", ("Perpustakaan Agung ibu kota menyimpan halaman-halaman kitab yang hilang. Bawakan aku tiga halaman, dan rak-rak di atas akan "
                    "mengenalmu lebih cepat dari rasa takut mereka.",
                    "The capital's Great Library keeps pages of lost scriptures. Bring me three pages, and the upper shelves will know you "
                    "sooner than their fear would."),
          "membaca", "kitab", pilihan=[C(("Akan kucari.", "I'll find them."), akibat=["quest_mulai " + QK]), C(BYE)]),
        *MO_SIM,
    ]),
    D("tema_iblis_mowen_kitab", MO, [tahap(QK, "serah"), "punya kultivator:lost_scripture_page 3"], 70, [
        N("mulai", ("Tiga halaman. Aku bisa mencium tinta perpustakaan itu, dan debu dari seorang raja yang mati. Terima kasih. Namamu kini "
                    "tertulis di buku tamu menara.",
                    "Three pages. I can smell that library's ink, and the dust of a dead king. Thank you. Your name is now written in the "
                    "tower's guest book."),
          "membaca", "kitab", akibat=["ambil kultivator:lost_scripture_page 3", "iblis_akses_lantai", "peristiwa tanda iblis_kitab_serah"]),
    ]),
    # ================================================================ Lao Kuang (side quest)
    D("tema_iblis_lao_sapa", LAO, [KOTA, "iblis_lao_ada", "quest %s baru" % QL], 40, [
        N("mulai", ("Empat puluh tahun di lubang itu. Empat ratus laga. Lututku berbunyi seperti pintu kandang. Tu Gang bilang aku masih "
                    "menghasilkan uang, jadi aku belum boleh berhenti. Kau tahu apa yang diinginkan gladiator tua, nak?",
                    "Forty years in that pit. Four hundred bouts. My knees creak like the pen doors. Tu Gang says I still make money, so I'm "
                    "not allowed to stop. Do you know what an old gladiator wants, kid?"),
          "asah_pedang", "", pilihan=[C(("Apa?", "What?"), "ingin"), C(BYE)]),
        N("ingin", ("Satu dari dua. Mati dengan pedang di tangan, melawan seseorang yang pantas. Atau pulang ke kebun ubi ibuku, kalau ada yang mau "
                    "membeli kebebasanku dari si Jagal. Pilihlah untukku, karena aku sudah terlalu lelah memilih.",
                    "One of two. To die with a sword in hand, against someone worthy. Or go home to my mother's yam garden, if someone would "
                    "buy my freedom from the Butcher. Choose for me, for I am too tired to choose."),
          "sikap_tua", "", akibat=["quest_mulai " + QL]),
    ]),
    D("tema_iblis_lao_pilih", LAO, [tahap(QL, "pilih"), "!bendera iblis_lao_terhormat"], 70, [
        N("mulai", ("Sudah memutuskan, nak?", "Decided, kid?"), "sikap_tua", "", pilihan=[
            C(("Laga terakhir. Aku lawanmu.", "A last bout. I'll be your foe."), "duel"),
            C(("Kubeli kebebasanmu.", "I'll buy your freedom."), "pensiun", syarat=["nb 120"], tampil=True,
              alasan=("Butuh 120 Nilai Batu.", "Needs 120 Nilai Batu.")),
            C(BYE),
        ]),
        N("duel", ("Ha... ha! Baik. Jangan menahan diri. Kalau kau menahan diri, itu penghinaan.",
                   "Ha... ha! Good. Don't hold back. Holding back would be an insult."), "hunus", "", akibat=["mulai_duel"]),
        N("pensiun", ("Kebun ubi... Ibu pasti sudah lama tiada, tapi kebunnya mungkin masih ada. Terima kasih, nak. Lao Kuang akan menanam lagi.",
                      "The yam garden... Mother must be long gone, but the garden may still be there. Thank you, kid. Lao Kuang will plant "
                      "again."),
          "hormat_dalam", "", akibat=["iblis_lao pensiun"]),
    ]),
    # ================================================================ the slum children (side quest)
    D("tema_iblis_anak_liar", "peran:anak", [KOTA, "quest %s baru" % QA], 40, [
        N("mulai", ("Jangan bilang siapa-siapa! Rotinya cuma tiga... dapur umum tidak akan rugi. Adikku belum makan dua hari.",
                    "Don't tell anyone! It's only three loaves... the common kitchen won't miss them. My little brother hasn't eaten in two "
                    "days."),
          "takut_sembunyi", "", pilihan=[
              C(("Ini, beli roti yang layak.", "Here, buy proper bread."), "bantu", syarat=["nb 20"], tampil=True,
                alasan=("Butuh 20 Nilai Batu.", "Needs 20 Nilai Batu."), akibat=["quest_mulai " + QA, "iblis_anak bantu"]),
              C(("Pencuri dilaporkan ke penegak.", "Thieves get reported to the enforcers."), "lapor", akibat=["quest_mulai " + QA, "iblis_anak lapor"]),
              C(("Pergilah, cepat.", "Go, quickly.")),
          ]),
        N("bantu", ("Kau... memberi kami uang? Di kota ini? Terima kasih! Aku akan ingat wajahmu!",
                    "You... give us money? In this city? Thank you! I'll remember your face!"), "lambai"),
        N("lapor", ("Tidak! Tolong! ...Kau sama saja dengan mereka.", "No! Please! ...You're just like them."), "menangis"),
    ]),
    # ================================================================ townsfolk
    D("tema_iblis_warga_sapa", "peran:warga_iblis", [KOTA], 4, [
        N("mulai", ("Jangan menatapku terlalu lama, orang asing. Di kota ini, tatapan dihitung sebagai tantangan.",
                    "Don't stare at me too long, stranger. In this city a stare counts as a challenge."), "sikap_lipat_tangan"),
    ]),
    D("tema_iblis_warga_malam", "peran:warga_iblis", [KOTA, "jam 18 24"], 5, [
        N("mulai", ("Malam ini ada laga di Arena Darah. Aku bertaruh pada si Gagak Malam. Dia belum pernah kalah... dua kali.",
                    "There's a bout at the Blood Arena tonight. I've bet on the Night Crow. He has never lost... twice."), "bicara_tangan"),
    ]),
    D("tema_iblis_gladiator_sapa", "peran:gladiator_iblis", [KOTA], 5, [
        N("mulai", ("Kau melihat bekas luka ini? Masing-masing satu kemenangan. Yang di punggung... jangan tanya.",
                    "See these scars? Each one a win. The one on my back... don't ask."), "latih_jurus"),
    ]),
    D("tema_iblis_jagal_sapa", "peran:jagal_arena", [KOTA], 5, [
        N("mulai", ("Kapakku tumpul lagi. Laga semalam terlalu banyak tulang.", "My axe is dull again. Too many bones in last night's bouts."), "asah_pisau"),
    ]),
]


# ========================================================================================== quests
TEMPAT = "tema_iblis"
TEMPAT_TEKS = ("Kota Iblis", "Demon City")


def S(sid, baris, **kw):
    """A stage: id, journal line (id, en), JSON fields (tujuan, akibat, akibat_masuk, cabang, ke, batas_hari, gagal_ke, arah)."""
    d = {"id": sid}
    for k, v in kw.items():
        if k == "cabang":
            d["cabang"] = [{"syarat": s, "ke": ke} for s, ke in v]
        else:
            d[k] = v
    return (d, baris)


QUESTS = [
    {
        "id": Q, "pemberi": YE,
        "nama": ("Rantai yang Putus", "The Broken Chain"),
        "ringkas": ("Ye Wuhen, kultivator iblis yang dulu diculik dari sebuah desa, memintamu melihat Kandang Tawanan. Di belakangnya menunggu "
                    "jaringan Minyak Bayangan, sebuah peta yang digambar dengan kuku, penyerbuan sekte lurus, dan Penguasa Darah Senja.",
                    "Ye Wuhen, a demonic cultivator once stolen from a village, asks you to look at the Captive Pens. Behind them wait the "
                    "Shadow Oil ring, a map drawn with fingernails, the righteous siege, and the Lord of the Dusk Blood."),
        "tahap": [
            S("peringatan", ("Dengarkan peringatan Ye Wuhen di Lapangan Peringatan, dekat Gerbang Tengkorak.",
                             "Hear Ye Wuhen's warning on the Warning Field, by the Skull Gate."),
              tujuan=["tanda iblis_peringatan"], akibat=["gentar 2"], batas_hari=6, gagal_ke="kandang", arah="kota_tema/iblis"),
            S("kandang", ("Lihat Kandang Tawanan di timur laut kota dan bicaralah dengan anak yang terus mencoba kabur.",
                          "Look at the Captive Pens in the city's north-east and talk to the child who keeps trying to escape."),
              akibat_masuk=["iblis_afu pastikan"], tujuan=["tanda iblis_afu_temu"], batas_hari=6, gagal_ke="tebus_afu"),
            S("tebus_afu", ("Bebaskan A-Fu: tebus dia di Kandang Tawanan, atau menangkan Tebusan Darah di arena Tu Gang. Atau tinggalkan dia.",
                            "Free A-Fu: ransom her at the Captive Pens, or win a Blood Ransom in Tu Gang's arena. Or leave her."),
              tujuan=["iblis_afu"], cabang=[(["bendera iblis_afu_abai"], "minyak")], batas_hari=6, gagal_ke="minyak"),
            S("antar_afu", ("Kawal A-Fu keluar Gerbang Tengkorak sampai ke desanya, atau kampung mana pun yang aman.",
                            "Escort A-Fu out of the Skull Gate to her village, or any safe village."),
              tujuan=["iblis_pulang a_fu"], batas_hari=6, gagal_ke="minyak", arah="kampung/"),
            S("minyak", ("Nyonya Bai She ingin bicara soal Minyak Bayangan. Gudangnya di Sarang Kontrak: beli bagiannya, atau pecahkan keenam tongnya.",
                         "Madam Bai She wants a word about Shadow Oil. Its store is at the Contract Den: buy a share, or break all six barrels."),
              tujuan=["iblis_minyak"], batas_hari=6, gagal_ke="peta"),
            S("peta", ("Han Qingshuang di Pos Pemburu mencari Peta Gerbang Rahasia. Jawab teka-teki tiga kitab Si Buta Mo Wen di menara.",
                       "Han Qingshuang at the Hunters' Post seeks the Map of the Secret Gate. Answer Blind Mo Wen's riddle of three books in the tower."),
              tujuan=["punya " + PETA], batas_hari=6, gagal_ke="padam", arah="kota_tema/pos_pemburu"),
            S("pilih", ("Putuskan ke tangan siapa peta itu jatuh: Han Qingshuang (Lurus), Ye Wuhen (Penebusan), atau Xue Yao (Darah).",
                        "Decide whose hand the map falls into: Han Qingshuang (Righteous), Ye Wuhen (Redemption) or Xue Yao (Blood)."),
              tujuan=["tanda iblis_peta_serah"], batas_hari=6, gagal_ke="padam",
              cabang=[(["iblis_rute lurus"], "serbu_lurus"), (["iblis_rute penebusan"], "serbu_tebus"), (["iblis_rute darah"], "serbu_darah")]),
            S("serbu_lurus", ("Penyerbuan dimulai. Kalahkan enam penjaga ruang tuas di rumah bea dan tarik tuas Gerbang Tengkorak.",
                              "The siege begins. Defeat the six guards of the lever room in the toll house and pull the Skull Gate's lever."),
              akibat_masuk=["iblis_serbu"], tujuan=["iblis_serbu tuas"], akibat=["infami -15 rantai_lurus"], ke="istana", batas_hari=6, gagal_ke="padam"),
            S("serbu_tebus", ("Penyerbuan dimulai. Temui Ye Wuhen di Terowongan Tua dan kawal para tawanan keluar tembok.",
                              "The siege begins. Meet Ye Wuhen in the Old Tunnel and escort the captives beyond the wall."),
              akibat_masuk=["iblis_serbu"], tujuan=["iblis_serbu terowongan"], akibat=["infami -15 rantai_tebus"], ke="istana", batas_hari=6, gagal_ke="padam"),
            S("serbu_darah", ("Penyerbuan dimulai. Bela Gerbang Tengkorak dan kalahkan Han Qingshuang dalam duel. Ia tidak akan mati; ia akan mundur.",
                              "The siege begins. Defend the Skull Gate and beat Han Qingshuang in a duel. She will not die; she will withdraw."),
              akibat_masuk=["iblis_serbu"], tujuan=["iblis_serbu han_mundur"], akibat=["infami 15 rantai_darah", "gentar 20"], ke="jamuan",
              batas_hari=6, gagal_ke="padam"),
            S("istana", ("Masuki Istana Darah dan hadapi Xue Yao, Penguasa Darah Senja.", "Enter the Blood Palace and face Xue Yao, Lord of the Dusk Blood."),
              tujuan=["iblis_xue kalah"], akibat=["iblis_hadiah kipas"], cabang=[(["iblis_rute lurus"], "akhir_lurus")], ke="akhir_tebus",
              batas_hari=6, gagal_ke="padam"),
            S("jamuan", ("Hadiri jamuan Xue Yao di Istana Darah. Ia menyiapkan satu ujian kesetiaan.",
                         "Attend Xue Yao's banquet in the Blood Palace. She has prepared one test of loyalty."),
              tujuan=["iblis_xue"], akibat=["iblis_hadiah kitab"], ke="akhir_darah", batas_hari=6, gagal_ke="padam"),
            S("akhir_lurus", ("Rantai putus.", "The chain is broken."), tujuan=["hari 0"], akibat=["iblis_akhir lurus"], ke="selesai"),
            S("akhir_tebus", ("Fajar pertama.", "The first dawn."), tujuan=["hari 0"], akibat=["iblis_akhir penebusan"], ke="selesai"),
            S("akhir_darah", ("Tamu di meja darah.", "A guest at the blood table."), tujuan=["hari 0"], akibat=["iblis_akhir darah"], ke="selesai"),
            S("padam", ("Waktu habis.", "Time ran out."), tujuan=["hari 0"], akibat=["kabar tema_iblis_padam"], ke="gagal"),
        ],
        "hasil": {
            "akhir_lurus": ("Tu Gang kini memerintah Kota Iblis. Kandang Tawanan ditutup untuk selamanya dan Pos Pemburu menjadi markas tetap. Di "
                            "Lapangan Peringatan, seseorang menggantungkan rantai yang putus.",
                            "Tu Gang now rules the Demon City. The Captive Pens are closed for good and the Hunters' Post became a garrison. On "
                            "the Warning Field someone hung up a broken chain."),
            "akhir_tebus": ("Ye Wuhen memerintah kota. Siang hari kini aman di setiap jalan, dan kandang menjadi Rumah Tebusan. Para penghuni "
                            "kota masih menyipitkan mata melihat matahari.",
                            "Ye Wuhen rules the city. Daytime is now safe on every street, and the pens became the House of Ransom. The "
                            "citizens still squint at the sun."),
            "akhir_darah": ("Xue Yao tetap memerintah, dan kau duduk di sampingnya sebagai Tamu Darah. Gerbang dan lantai sembilan terbuka "
                            "untukmu; gerbang-gerbang sekte lurus tertutup.",
                            "Xue Yao still rules, and you sit beside her as Blood Guest. The gate and floor nine are open to you; the righteous "
                            "sects' gates are closed."),
            "padam": ("Kesempatan itu berlalu. Xue Yao tetap berkuasa, dan kedai-kedai bercerita tentang penyerbuan yang patah di rahang tengkorak.",
                      "The chance passed. Xue Yao still rules, and the taverns tell of a siege that broke on the skull's jaws."),
        },
    },
    {
        "id": QT, "pemberi": BAI,
        "nama": ("Topeng Tanpa Wajah", "The Faceless Mask"),
        "ringkas": ("Regu pemburu lurus mencuri sebuah topeng dari kios Nyonya Bai She saat penyerbuan. Ambil kembali.",
                    "A righteous hunter squad stole a mask from Madam Bai She's stall during the siege. Take it back."),
        "tahap": [
            S("cari", ("Temukan kemah regu pemburu di luar Gerbang Tengkorak dan rebut Topeng Tanpa Wajah.",
                       "Find the hunter squad's camp outside the Skull Gate and seize the Faceless Mask."),
              tujuan=["iblis_topeng rebut"], batas_hari=4, gagal_ke="hilang"),
            S("kembali", ("Bawa topeng itu kembali ke Nyonya Bai She.", "Bring the mask back to Madam Bai She."),
              tujuan=["tanda iblis_topeng_serah"], ke="selesai"),
            S("hilang", ("Regu itu sudah pergi.", "The squad is gone."), tujuan=["hari 0"], ke="gagal"),
        ],
        "hasil": {
            "kembali": ("Bai She membayarmu dan membiarkanmu menyimpan topeng itu. Di Kota Iblis, wajah yang tak dikenal adalah perisai.",
                        "Bai She paid you and let you keep the mask. In the Demon City an unknown face is a shield."),
            "hilang": ("Regu pemburu pulang ke sektenya, membawa topeng itu sebagai piala.", "The hunters went home to their sect, the mask a trophy."),
        },
    },
    {
        "id": QL, "pemberi": LAO,
        "nama": ("Gladiator Tua", "The Old Gladiator"),
        "ringkas": ("Lao Kuang sudah empat puluh tahun bertarung di Arena Darah. Ia ingin mati terhormat, atau pulang ke kebun ubi ibunya.",
                    "Lao Kuang has fought forty years in the Blood Arena. He wants an honourable death, or to go home to his mother's yam garden."),
        "tahap": [
            S("pilih", ("Beri Lao Kuang laga terakhirnya dalam duel, atau beli kebebasannya dari Tu Gang (120 NB).",
                        "Give Lao Kuang his last bout in a duel, or buy his freedom from Tu Gang (120 NB)."),
              tujuan=["iblis_lao"], cabang=[(["bendera iblis_lao_terhormat"], "terhormat")], ke="pensiun"),
            S("terhormat", ("Laga terakhir.", "The last bout."), tujuan=["hari 0"], ke="selesai"),
            S("pensiun", ("Kebun ubi.", "The yam garden."), tujuan=["hari 0"], ke="selesai"),
        ],
        "hasil": {
            "terhormat": ("Pedang Lao Kuang kini tergantung di Lapangan Peringatan, di antara para penantang yang ia kalahkan.",
                          "Lao Kuang's sword now hangs on the Warning Field, among the challengers he once beat."),
            "pensiun": ("Lao Kuang berjalan keluar gerbang dengan tongkat dan karung bibit. Tu Gang tidak menoleh, tapi ia menghitung uangmu dua kali.",
                        "Lao Kuang walked out of the gate with a stick and a sack of seedlings. Tu Gang didn't look, but he counted your money twice."),
        },
    },
    {
        "id": QK, "pemberi": MO,
        "nama": ("Tiga Halaman yang Hilang", "Three Lost Pages"),
        "ringkas": ("Si Buta Mo Wen menginginkan tiga halaman kitab yang hilang. Sebagai gantinya, rak-rak atas menara akan mengenalmu.",
                    "Blind Mo Wen wants three lost scripture pages. In return, the tower's upper shelves will know you."),
        "tahap": [
            S("kumpul", ("Kumpulkan tiga Halaman Kitab Hilang (dijual di ibu kota dan lelang).", "Gather three Lost Scripture Pages (sold in the capital and at auctions)."),
              tujuan=["punya kultivator:lost_scripture_page 3"], arah="kotabesar"),
            S("serah", ("Serahkan ketiga halaman kepada Mo Wen di lantai satu menara.", "Hand the three pages to Mo Wen on the tower's first floor."),
              tujuan=["tanda iblis_kitab_serah"], ke="selesai"),
        ],
        "hasil": {
            "serah": ("Namamu tertulis di buku tamu Menara Kitab Terlarang. Lantai-lantai atas menyambutmu seolah kau lebih ditakuti dari sebenarnya.",
                      "Your name is written in the guest book of the Tower of Forbidden Books. The upper floors greet you as if you were more "
                      "feared than you are."),
        },
    },
    {
        "id": QA, "pemberi": "peran:anak",
        "nama": ("Roti dari Dapur Umum", "Bread from the Common Kitchen"),
        "ringkas": ("Anak-anak kumuh Kota Iblis mencuri roti dari dapur umum. Kau memergoki mereka.",
                    "The Demon City's slum children steal bread from the common kitchen. You caught them at it."),
        "tahap": [
            S("pilih", ("Bantu anak-anak itu, atau laporkan mereka ke penegak.", "Help the children, or report them to the enforcers."),
              tujuan=["iblis_anak"], cabang=[(["bendera iblis_anak_lapor"], "dilapor")], ke="dibantu"),
            S("dibantu", ("Roti yang layak.", "Proper bread."), tujuan=["hari 0"], ke="selesai"),
            S("dilapor", ("Laporan ke penegak.", "A report to the enforcers."), tujuan=["hari 0"], ke="selesai"),
        ],
        "hasil": {
            "dibantu": ("Di lorong kumuh, anak-anak itu kini bersiul tiap kali kau lewat: tanda bahwa jalan di depan aman.",
                        "In the slum alleys the children now whistle whenever you pass: a sign that the road ahead is safe."),
            "dilapor": ("Penegak mengusir anak-anak itu dari dapur umum. Mereka mengingat wajahmu, tapi bukan dengan baik.",
                        "The enforcers drove the children from the common kitchen. They remember your face, but not kindly."),
        },
    },
]


# ========================================================================================== rumours
SEBAR = ["warga_iblis", "makelar_gelap", "pemilik_teh", "warga"]
KABAR = [
    ("tema_iblis_warga_hilang", None, 12, 6, ["warga", "pemilik_teh"],
     ("%1$s dari %2$s tidak pulang sejak tiga malam lalu. Ada yang melihat orang-orang bertopeng besi di jalan.",
      "%1$s of %2$s has not come home for three nights. Someone saw men in iron masks on the road.")),
    ("tema_iblis_tawanan_pulang", None, 12, 6, ["warga", "pemilik_teh"],
     ("%1$s pulang dari Kota Iblis! Katanya %2$s menebusnya dan mengawalnya sampai ke sini.",
      "%1$s came home from the Demon City! They say %2$s ransomed them and escorted them all the way here.")),
    ("tema_iblis_afu_kabur", None, 8, 4, SEBAR,
     ("Bocah dari kandang itu mencoba kabur lagi. Si Jagal menangkapnya di tembok timur. Keempat kalinya.",
      "That brat from the pens tried to run again. The Butcher caught her at the east wall. The fourth time.")),
    ("tema_iblis_minyak_sabotase", None, 12, 6, SEBAR,
     ("Enam tong Minyak Bayangan pecah di gudang Sarang Kontrak. Bai She tersenyum, artinya ada yang akan mati. Katanya %s pelakunya.",
      "Six barrels of Shadow Oil broke in the Contract Den's store. Bai She is smiling, which means someone will die. They say %s did it.")),
    ("tema_iblis_xue_kalah", None, 14, 10, SEBAR + ["penegak_iblis"],
     ("Xue Yao berlutut! Penguasa Darah Senja meninggalkan istananya setelah dikalahkan %s. Siapa yang akan duduk di takhta itu?",
      "Xue Yao knelt! The Lord of the Dusk Blood left her palace after %s beat her. Who will sit on that throne?")),
    ("tema_iblis_akhir_lurus", None, 14, 12, SEBAR,
     ("Kandang ditutup, rantai digantung di Lapangan Peringatan. Tu Gang memerintah kota, dan semua orang menyebut nama %s.",
      "The pens are shut, the chains hung on the Warning Field. Tu Gang rules the city, and everyone speaks the name of %s.")),
    ("tema_iblis_akhir_penebusan", None, 14, 12, SEBAR,
     ("Pagi datang ke Kota Iblis. Ye Wuhen memerintah, dan katanya %s yang membawa fajar.",
      "Morning came to the Demon City. Ye Wuhen rules, and they say %s brought the dawn.")),
    ("tema_iblis_akhir_darah", None, 14, 12, SEBAR,
     ("Xue Yao punya tamu baru di mejanya. %s, kata orang. Gerbang sekte-sekte lurus tertutup bagi nama itu.",
      "Xue Yao has a new guest at her table. %s, people say. The righteous sects' gates are shut to that name.")),
    ("tema_iblis_lao_pensiun", None, 10, 6, SEBAR,
     ("Lao Kuang pensiun! Gladiator tua itu berjalan keluar gerbang membawa karung bibit ubi. Tu Gang menghitung uang tebusannya dua kali.",
      "Lao Kuang retired! The old gladiator walked out of the gate with a sack of yam seedlings. Tu Gang counted the ransom twice.")),
    ("tema_iblis_lao_terhormat", None, 10, 6, SEBAR,
     ("Laga terakhir Lao Kuang melawan %s. Penonton berdiri semua. Pedangnya kini di Lapangan Peringatan.",
      "Lao Kuang's last bout, against %s. The whole crowd stood. His sword is on the Warning Field now.")),
    ("tema_iblis_anak_bantu", None, 6, 4, ["warga_iblis", "pengemis"],
     ("Ada orang asing yang memberi anak-anak kumuh uang roti. Di kota ini! Mungkin dia sakit.",
      "Some stranger gave the slum children bread money. In this city! Maybe they're ill.")),
    ("tema_iblis_anak_lapor", None, 6, 4, ["warga_iblis", "penegak_iblis"],
     ("Penegak mengusir anak-anak kumuh dari dapur umum. Ada orang asing yang melaporkan mereka.",
      "The enforcers drove the slum children from the common kitchen. A stranger reported them.")),
    ("tema_iblis_buronan_tewas", None, 12, 8, ["warga", "pemilik_teh", "pemburu_lurus"],
     ("%1$s, buronan Kota Iblis, tewas di tangan %2$s. Pos Pemburu membayar hadiahnya.",
      "%1$s, a fugitive of the Demon City, died by the hand of %2$s. The Hunters' Post paid the bounty.")),
    ("tema_iblis_kitab", None, 12, 10, SEBAR,
     ("Kitab Darah Senja meninggalkan lantai sembilan! Mo Wen bilang pemiliknya %s. Sekte lurus pasti sudah mendengar.",
      "The Dusk Blood Book left floor nine! Mo Wen says its owner is %s. The righteous sects must have heard by now.")),
    ("tema_iblis_serbu_mulai", None, 10, 2, SEBAR + ["penegak_iblis"],
     ("Jubah putih di cakrawala! Koalisi sekte lurus mengepung Gerbang Tengkorak. Musim panen datang lagi.",
      "White robes on the horizon! A coalition of righteous sects besieges the Skull Gate. Harvest season again.")),
    ("tema_iblis_serbu_lurus", None, 12, 6, SEBAR,
     ("Gerbang Tengkorak jebol kemarin. Kandang dikosongkan dan para makelar bersembunyi. Pasar baru buka lusa.",
      "The Skull Gate fell yesterday. The pens were emptied and the brokers are hiding. The market opens the day after tomorrow.")),
    ("tema_iblis_serbu_kota", None, 12, 6, SEBAR,
     ("Penyerbuan patah di rahang tengkorak lagi. Upeti gerbang naik setengah tiga hari ini, untuk membayar mayat-mayat itu.",
      "The siege broke on the skull's jaws again. The gate toll is up by half these three days, to pay for the corpses.")),
    ("tema_iblis_pesta", None, 12, 2, SEBAR,
     ("Gerhana darah! Lentera merah di setiap pintu, Turnamen Darah di arena, dan lantai sembilan menara terbuka. Jangan keluar sendirian.",
      "A blood eclipse! Red lanterns at every door, the Blood Tournament in the arena, and floor nine of the tower open. Don't go out alone.")),
    ("tema_iblis_turnamen", None, 12, 6, SEBAR + ["gladiator_iblis"],
     ("%s memenangkan Turnamen Darah. Xue Yao sendiri bertepuk tangan dari Bilik Penguasa.",
      "%s won the Blood Tournament. Xue Yao herself applauded from the Lord's Box.")),
    ("tema_iblis_ye_hilang", None, 10, 8, SEBAR,
     ("Ye Wuhen tidak terlihat sejak jamuan di istana. Rumahnya kosong. Tidak ada yang berani bertanya.",
      "Ye Wuhen has not been seen since the palace banquet. His house is empty. Nobody dares ask.")),
    ("tema_iblis_padam", None, 8, 6, SEBAR,
     ("Katanya ada yang mencoba memutus rantai kota ini. Katanya ia terlambat. Xue Yao masih tersenyum.",
      "They say someone tried to break this city's chain. They say they were too late. Xue Yao is still smiling.")),
    ("tema_iblis_lelang_mulai", "acara_tema_lelang_gelap", 10, 1, SEBAR,
     ("Malam ini tirai merah Balai Lelang Gelap terbuka. Bawa uang, dan jangan tanya asal barangnya.",
      "Tonight the Black Auction Hall's red curtain parts. Bring money, and don't ask where the goods came from.")),
    ("tema_iblis_laga_malam", "acara_tema_malam_pertandingan", 6, 1, ["warga_iblis", "gladiator_iblis", "jagal_arena"],
     ("Dua belas laga malam ini. Si Gagak Malam melawan si Tangan Besi di laga ketujuh. Pasang taruhanmu sebelum gong.",
      "Twelve bouts tonight. Night Crow against Iron Hand in the seventh. Place your bets before the gong.")),
    ("tema_iblis_serbu_acara", "acara_tema_penyerbuan", 8, 1, SEBAR,
     ("Gong gerbang berbunyi tiga kali. Para penegak mengasah golok di depan rahang.", "The gate gong rang three times. The enforcers sharpen their cleavers before the jaws.")),
    ("tema_iblis_pesta_acara", "acara_tema_pesta_darah", 8, 1, SEBAR,
     ("Bulan berdarah. Kota ini tidak tidur malam ini.", "The moon is bleeding. This city does not sleep tonight.")),
    ("tema_iblis_topeng_selesai", "quest_" + QT, 8, 4, ["makelar_gelap", "warga_iblis", "pembunuh_bayaran"],
     ("Topeng Tanpa Wajah pulang ke Pasar Seribu Bayangan, dan Bai She menolak memakainya. Yang merebutnya dari regu pemburu: %s.",
      "The Faceless Mask came back to the Market of a Thousand Shadows, and Bai She refused to wear it. Who took it back from the hunters: %s.")),
    ("tema_iblis_rantai_selesai", "quest_" + Q, 10, 8, ["warga", "pemilik_teh"],
     ("Dari Kota Iblis datang kabar aneh: rantainya putus, dan %s ada di tengah semua itu.",
      "Strange news from the Demon City: its chain is broken, and %s was in the middle of it all.")),
]
# background lines of the demon city (pemicu "umum": only these roles tell them, any day)
IBLIS_UMUM = ["warga_iblis", "makelar_gelap", "penegak_iblis", "pembunuh_bayaran", "gladiator_iblis", "jagal_arena"]
for _i, _t in enumerate([
    ("Jangan pernah menghitung uang di jalan sesudah lentera jiwa menyala. Di kota ini, bunyi koin lebih nyaring dari teriakan.",
     "Never count money in the street after the soul lanterns are lit. In this city the clink of coins is louder than a scream."),
    ("Upeti gerbang dibayar sekali sehari. Yang membayar dua kali bukan dermawan; ia baru saja ditandai penegak sebagai orang bodoh.",
     "The gate toll is paid once a day. Whoever pays twice isn't generous; the enforcers have just marked them as a fool."),
    ("Kedai Cawan Racun punya dua belas meja. Meja ketujuh tak pernah diduduki. Pemiliknya bilang itu meja untuk tamu yang belum mati.",
     "The Poison Cup Tavern has twelve tables. The seventh is never taken. The owner says it's for the guest who hasn't died yet."),
    ("Teh di Kedai Teh Asap diseduh dengan air sumur kumuh. Rasanya pahit, tapi setidaknya kau tahu racunnya bukan dari tehnya.",
     "The tea at the Smoke Teahouse is brewed with slum well water. It's bitter, but at least you know the poison isn't in the tea."),
    ("Dinding kontrak di Sarang Pembunuh tak pernah memuat nama warga kota. Bukan karena belas kasihan. Warga yang mati tak membayar upeti.",
     "The Contract Wall in the Assassins' Den never lists a citizen. Not out of mercy. Dead citizens pay no tribute."),
    ("Para gladiator memoles pedang dengan minyak tulang sebelum laga malam. Baunya menempel di Arena Darah sampai pagi.",
     "The gladiators polish their blades with bone oil before the night bouts. The smell clings to the Blood Arena until morning."),
    ("Si Buta Mo Wen bisa mengenali pengunjung dari langkahnya. Katanya, langkah orang yang berbohong selalu setengah ketukan lebih cepat.",
     "Blind Mo Wen knows visitors by their steps. He says a liar's step is always half a beat too quick."),
    ("Serigala bayangan di kandang gerbang hanya makan sekali sehari, saat gong senja. Kalau mereka melolong sebelum gong, ada penyusup.",
     "The shadow wolves in the gate kennel eat once a day, at the dusk gong. If they howl before the gong, there's an intruder."),
    ("Balai Lelang Gelap tidak menerima tawaran dengan suara. Angkat jarimu, dan jangan sampai bersin; seseorang pernah membeli peti "
     "kosong seharga seribu Nilai Batu karena bersin.",
     "The Black Auction Hall takes no spoken bids. Raise your finger, and don't sneeze; someone once bought an empty chest for a "
     "thousand Nilai Batu with a sneeze."),
    ("Anak-anak kumuh tahu setiap lubang di tembok timur. Bayar mereka sepotong roti, dan mereka menunjukkan jalan yang tak dijaga penegak.",
     "The slum children know every gap in the east wall. Pay them a piece of bread and they'll show you a way the enforcers don't guard."),
]):
    KABAR.append(("tema_iblis_umum_%d" % _i, "umum", 6, 30, IBLIS_UMUM, _t))


# ========================================================================================== advancements (granted in code)
ADV = [
    ("tema_iblis_upeti", "minecraft:skeleton_skull", "task", ("Upeti Pertama", "The First Toll"),
     ("Bayar upeti di Gerbang Tengkorak Kota Iblis.", "Pay the toll at the Demon City's Skull Gate.")),
    ("tema_iblis_ditakuti", "minecraft:wither_skeleton_skull", "goal", ("Yang Ditakuti", "The Feared One"),
     ("Capai Gentar 80 di sebuah Kota Iblis.", "Reach Dread 80 in a Demon City.")),
    ("tema_iblis_berburu", "minecraft:iron_sword", "task", ("Darah, Bukan Upeti", "Blood, Not Tribute"),
     ("Menangi pertarungan Jam Berburu.", "Win a fight of the Hunting Hour.")),
    ("tema_iblis_kontrak", "minecraft:paper", "task", ("Dinding Kontrak", "The Contract Wall"),
     ("Tuntaskan sebuah kontrak Sarang Kontrak.", "Fulfil a contract of the Contract Den.")),
    ("tema_iblis_buronan", "minecraft:crossbow", "goal", ("Pemburu Kepala", "Head Hunter"),
     ("Klaim hadiah seorang buronan iblis di Pos Pemburu.", "Claim the bounty of a demonic fugitive at the Hunters' Post.")),
    ("tema_iblis_turnamen_darah", "minecraft:netherite_sword", "challenge", ("Juara Turnamen Darah", "Blood Tournament Champion"),
     ("Menangi Turnamen Darah saat gerhana darah.", "Win the Blood Tournament during a blood eclipse.")),
    ("tema_iblis_kitab_darah", "minecraft:enchanted_book", "challenge", ("Kitab Darah Senja", "The Dusk Blood Book"),
     ("Miliki Kitab Darah Senja dari lantai sembilan.", "Own the Dusk Blood Book of floor nine.")),
    ("tema_iblis_xue_yao", "minecraft:red_candle", "challenge", ("Penguasa Berlutut", "The Lord Kneels"),
     ("Kalahkan Xue Yao, Penguasa Darah Senja.", "Defeat Xue Yao, Lord of the Dusk Blood.")),
    ("tema_iblis_tamu_darah", "minecraft:redstone", "goal", ("Tamu Darah", "Blood Guest"),
     ("Selesaikan Rantai yang Putus di jalan Darah.", "Finish The Broken Chain on the road of Blood.")),
]


# ========================================================================================== loot tables (chests of the generator)
def item(name, w, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    if hi > 1:
        # 26.3: one "modifier" per entry (LootPoolEntryContainer), as the inti tables write it
        e["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}
    return e


def peti(rolls, entries, extra=None):
    pools = [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}]
    if extra:
        pools.append({"rolls": 1, "entries": extra})
    return {"type": "minecraft:chest", "pools": pools}


LOOT = {
    # the toll house: the day's tribute and weapons confiscated at the gate
    "upeti": peti((3, 6), [
        item("kultivator:spirit_stone_low", 30, 3, 9), item("kultivator:spirit_stone_mid", 8, 1, 3), item("minecraft:gold_nugget", 16, 4, 12),
        item("minecraft:iron_sword", 6), item("minecraft:iron_axe", 5), item("minecraft:iron_chain", 10, 1, 4), item("minecraft:paper", 10, 2, 6),
        item("kultivator:token_pasar_gelap", 4), item("minecraft:bone", 10, 2, 6)],
        [{"type": "minecraft:empty", "weight": 12}, item("kultivator:tema/topeng_tanpa_wajah", 1)]),
    # the gladiators' room: bandages, cheap blades, the night's cups
    "gladiator": peti((2, 5), [
        item("kultivator:healing_pill", 18, 1, 3), item("minecraft:bread", 16, 2, 5), item("minecraft:cooked_beef", 12, 1, 3),
        item("minecraft:iron_sword", 6), item("minecraft:shield", 5), item("minecraft:leather", 10, 1, 4), item("minecraft:string", 8, 2, 5),
        item("kultivator:spirit_stone_low", 14, 1, 5), item("kultivator:tema/pil_darah", 4)]),
    # the arena's prize chest by the betting table
    "arena": peti((3, 5), [
        item("kultivator:spirit_stone_low", 26, 4, 12), item("kultivator:spirit_stone_mid", 12, 1, 4), item("minecraft:gold_ingot", 10, 1, 3),
        item("kultivator:beast_core_low", 12, 1, 2), item("kultivator:beast_core_mid", 5), item("kultivator:healing_pill", 10, 1, 3),
        item("kultivator:tema/racun_lemah", 6, 1, 2), item("minecraft:bone", 10, 3, 8)],
        [{"type": "minecraft:empty", "weight": 9}, item("kultivator:demonic_loot_pouch", 1)]),
    # the broker's exchange: wares of the market of a thousand shadows
    "makelar": peti((3, 6), [
        item("kultivator:spirit_stone_low", 20, 3, 10), item("kultivator:spirit_stone_mid", 10, 1, 3), item("minecraft:emerald", 8, 1, 3),
        item("kultivator:tema/racun_lemah", 10, 1, 3), item("kultivator:tema/racun_sedang", 5, 1, 2), item("kultivator:tema/pil_darah", 8, 1, 2),
        item("kultivator:dagang/racun_ular_hitam", 8, 1, 2), item("kultivator:dagang/minyak_bayangan", 6, 1, 2), item("kultivator:toxin_purging_pill", 8, 1, 2),
        item("kultivator:token_pasar_gelap", 6), item("minecraft:amethyst_shard", 8, 2, 5)],
        [{"type": "minecraft:empty", "weight": 10}, item("kultivator:tema/peta_sarang", 2), item("kultivator:tema/kontrak_kepala", 1)]),
    # the contract den: blades, poisons and the paper of contracts
    "kontrak": peti((2, 5), [
        item("minecraft:paper", 18, 3, 8), item("minecraft:ink_sac", 10, 1, 3), item("minecraft:iron_sword", 6), item("minecraft:arrow", 12, 4, 12),
        item("kultivator:tema/racun_lemah", 10, 1, 2), item("kultivator:tema/racun_sedang", 4), item("kultivator:spirit_stone_low", 16, 2, 8),
        item("kultivator:loot_pouch", 6), item("minecraft:spider_eye", 8, 1, 3)],
        [{"type": "minecraft:empty", "weight": 14}, item("kultivator:tema/gulungan_langkah_hantu", 1)]),
    # the tribute treasury of the Blood Palace (quest target, guarded)
    "perbendaharaan": peti((4, 7), [
        item("kultivator:spirit_stone_mid", 24, 2, 6), item("kultivator:spirit_stone_high", 6, 1, 2), item("minecraft:gold_ingot", 18, 2, 6),
        item("minecraft:gold_block", 3), item("minecraft:emerald", 12, 2, 5), item("minecraft:diamond", 4, 1, 2),
        item("kultivator:dagang/minyak_bayangan", 8, 1, 3), item("kultivator:beast_core_mid", 8, 1, 2), item("kultivator:demonic_banner", 3),
        item("kultivator:token_darah_api", 4)],
        [{"type": "minecraft:empty", "weight": 6}, item("kultivator:beast_core_high", 2), item("kultivator:demonic_loot_pouch", 2)]),
}


# ========================================================================================== schedule data (events of the demon city)
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
    obj = {
        "_catatan": "DESIGN 38.T.7.3 / 38.T.7.10, module 38TK-I: what the built-in JadwalTema leaves to the theme (the keeper of the "
                    "forbidden books, the contract killers, the night events). Hours are clock hours (06:00 = tick 0); the night shift of "
                    "the demonic roles is applied by JadwalTema itself.",
        "jenis": "kota_tema/iblis",
        "peran": {
            "penjaga_kitab_terlarang": {
                "6-11": tidur,
                "12-17": e("baca_kitab", "meja_tulis", "membaca", "kitab", dalam=True),
                "18-23": e("rak_berantai", "@kerja", "rak_susun", "gulungan", dalam=True),
                "0-5": e("dupa_menara", "dupa", "sujud_dupa", "dupa", dalam=True),
            },
            "pembunuh_bayaran": {
                "6-13": tidur,
                "14-17": e("asah_pisau", "kursi", "asah_pisau", "pisau_ganda"),
                "18-21": e("loket_kontrak", "@kerja", "sikap_lipat_tangan", "pisau_ganda"),
                "22-3": e("patroli_atap", "jaga", "berdiri_jaga", jalan="baris_jaga"),
                "4-5": e("minum_racun", "meja_makan", "minum_teh", "cawan_racun"),
            },
        },
        "acara": {
            "tema.malam_pertandingan": {
                "*": e("tonton_arena", "penonton", "sorak_arena"),
                "warga_iblis": e("tonton_arena", "penonton", "sorak_arena"),
                "makelar_gelap": e("taruhan", "penonton", "hitung_koin_gelap", "kantong_uang"),
                "anak": e("intip_arena", "penonton", "sikap_anak"),
            },
            "tema.lelang_gelap": {
                "makelar_gelap": e("lelang_gelap", "kursi", "hitung_koin_gelap", "kantong_uang"),
                "warga_iblis": e("lelang_gelap", "kursi", "duduk_kursi"),
            },
            "tema.penyerbuan": {
                "*": e("sembunyi", "@rumah", "takut_sembunyi", dalam=True),
                "gladiator_iblis": e("bela_gerbang", "jaga", "latih_jurus"),
                "pembunuh_bayaran": e("bela_gerbang", "jaga", "sikap_lipat_tangan", "pisau_ganda"),
                "tawanan": e("berharap", "@tetap", "sikap_doa"),
            },
            "tema.pesta_darah": {
                "*": e("pesta_darah", "meja_makan", "minum_arak", "cawan_arak"),
                "warga_iblis": e("pesta_darah", "penonton", "sorak_arena"),
                "anak": e("lentera_merah", "obrol", "main_lentera", "lentera_tangan"),
                "tawanan": e("takut", "@tetap", "takut_sembunyi"),
            },
        },
    }
    tulis(RES + "/tempat/jadwal/kota_tema_iblis.json", obj)


# ========================================================================================== sale demand of the demon city
def permintaan():
    """{item: factor}: the trade goods' demon-city column read from DagangItems (column 5, PASAR_IBLIS), plus what a city of
    arenas and poisons needs (blades, bones, beast parts, wine) and what it has plenty of."""
    src = open(JAVA + "/dunia/tempat/tema/bersama/DagangItems.java", encoding="utf-8").read()
    obj = {"_catatan": "Kota Iblis: haus senjata, zirah, arak, daging dan bahan racun; tulang, batu hitam dan barang gelap murah di sini.",
           "*": 1.0}
    n = 0
    for m in re.finditer(r'barang\("([a-z_]+)",\s*Tema\.[A-Z]+,[^;]*?((?:[0-9.]+,\s*){5}[0-9.]+)\);', src, re.S):
        cols = [float(x) for x in re.findall(r"[0-9.]+", m.group(2))]
        obj["kultivator:dagang/" + m.group(1)] = cols[5]
        n += 1
    if n < 17:
        ERR.append("DagangItems parse found only %d goods" % n)
    obj.update({
        "minecraft:iron_sword": 1.25, "minecraft:iron_axe": 1.2, "minecraft:iron_ingot": 1.2, "minecraft:iron_chain": 1.25, "minecraft:shield": 1.2,
        "minecraft:leather": 1.15, "minecraft:cooked_beef": 1.2, "minecraft:cooked_porkchop": 1.2, "minecraft:bread": 1.1,
        "kultivator:healing_pill": 1.3, "kultivator:qi_recovery_pill": 1.2, "minecraft:spider_eye": 1.25, "minecraft:fermented_spider_eye": 1.3,
        "minecraft:poisonous_potato": 1.2, "kultivator:beast_core_low": 1.15, "kultivator:beast_core_mid": 1.15,
        "minecraft:bone": 0.5, "minecraft:bone_block": 0.5, "minecraft:blackstone": 0.4, "minecraft:basalt": 0.4, "minecraft:soul_sand": 0.5,
        "kultivator:tema/racun_lemah": 0.8, "kultivator:tema/pil_darah": 0.8,
    })
    tulis(RES + "/tempat/permintaan/kota_tema_iblis.json", obj)
    return n


# ========================================================================================== validation tables
def nama_java(path):
    src = open(path, encoding="utf-8").read()
    return {m.lower() for m in re.findall(r"public static final int ([A-Z0-9_]+) = ", src)}


GERAK = nama_java(JAVA + "/sosok/GerakId.java")
PROP = nama_java(JAVA + "/sosok/PropId.java")
PERAN = {r.lower() for r in re.findall(r"^\s+([A-Z_]+)\(", open(JAVA + "/dunia/kota/NpcRole.java", encoding="utf-8").read(), re.M)}
SUARA = set(re.findall(r"^\s+\('([a-z_.]+)', '(?:m1|m2|m3|f1|f2|f3|elder|boss|demon|heaven|heaven_fierce)'",
                       open(HERE + "/gen_voices.py", encoding="utf-8").read(), re.M))
TOKOH_ID = {t for t, _, _ in TOKOH}
# conditions / effects / goals: 38ST built-ins (gen_tempat_cerita.py), inti's TemaCerita, IblisCerita
SYARAT = {"nama_baik", "pangkat", "ranah", "pita", "bendera", "punya", "nb", "nb_pita", "hari", "quest", "jam", "sikap_sekte",
          "aliran_terdekat", "takut", "buronan", "acak", "watak", "situs_dikenal",
          "infami", "gentar", "pita_dagang", "kota_tema", "kota_tema_dikenal", "acara"}
AKIBAT = {"bendera", "nama_baik", "beri", "ambil", "bayar", "bayar_pita", "beri_nb", "beri_nb_pita", "donasi", "quest_mulai", "quest_maju",
          "quest_gagal", "peristiwa", "buka_layanan", "mulai_duel", "klip", "suara", "adegan", "kabar", "sikap_sekte", "panggil_peran",
          "kawal_mulai", "npc_pergi", "pesan", "efek", "tutup", "infami", "gentar", "acara"}
TUJUAN = {"pergi", "bicara", "kalahkan", "antar", "kawal", "duel", "racik", "tempa", "donasi", "bebaskan", "tunggu", "tanda", "misi_gilda",
          "ujian", "naik_lift", "ziarah_selesai", "lentera_dilepas", "kapal_lintas", "tebus", "dagang_laba", "kontrak_muatan", "masuk_kota_tema"}
LAYAR = {"tema_pasar_gelap", "tema_kitab_terlarang", "tema_kontrak", "tema_taruhan", "tema_tawanan"}


def kata_iblis():
    """The iblis_* words registered by IblisCerita.java (read from the source, so data and code cannot drift)."""
    src = open(IBLIS + "/IblisCerita.java", encoding="utf-8").read()
    syarat = set(re.findall(r'Syarat\.daftar\("([a-z_]+)"', src))
    akibat = set(re.findall(r'Akibat\.daftar\("([a-z_]+)"', src))
    tujuan = set(re.findall(r'public static final String [A-Z_]+ = "(iblis_[a-z_]+)";', src))
    return syarat, akibat, tujuan


IS, IA, IT = kata_iblis()
SYARAT |= IS
AKIBAT |= IA
TUJUAN |= IT


def cek_gerak(nama, where):
    if nama and nama not in GERAK:
        ERR.append("unknown clip %s in %s" % (nama, where))


def cek_prop(nama, where):
    if nama and nama not in PROP:
        ERR.append("unknown prop %s in %s" % (nama, where))


def cek_baris(where, lines, known, kind):
    for line in lines:
        w = line.strip().lstrip("!").split()
        if not w or w[0] not in known:
            ERR.append("%s: unknown %s '%s'" % (where, kind, line))
            continue
        if w[0] in ("quest", "quest_mulai", "quest_maju", "quest_gagal") and len(w) > 1 and w[1] not in QUEST_IDS:
            ERR.append("%s: unknown quest %s" % (where, w[1]))
        if w[0] == "buka_layanan" and len(w) > 1 and w[1] not in LAYAR:
            ERR.append("%s: unknown screen %s" % (where, w[1]))
        if w[0] == "kabar" and len(w) > 1 and w[1] not in KABAR_IDS:
            ERR.append("%s: unknown rumour %s" % (where, w[1]))


QUEST_IDS = {q["id"] for q in QUESTS}
KABAR_IDS = {k[0] for k in KABAR}


def placeholders(text):
    """The argument indices a translation uses (1-based), %% excluded."""
    t = text.replace("%%", "")
    idx = set()
    seq = 0
    for m in re.finditer(r"%(?:(\d+)\$)?s", t):
        if m.group(1):
            idx.add(int(m.group(1)))
        else:
            seq += 1
            idx.add(seq)
    if re.search(r"%(?!(\d+\$)?s)", t):
        ERR.append("unsupported format in '%s'" % text[:50])
    return idx


# ========================================================================================== writers
def dialogs():
    out_dir = RES + "/tempat/dialog"
    for d in DIALOG:
        did = d["id"]
        assert did.startswith("tema_iblis_"), did
        for who in d["pembicara"]:
            if who.startswith("tokoh:") and who[6:] not in TOKOH_ID:
                ERR.append("dialog %s: unknown character %s" % (did, who))
            elif who.startswith("peran:") and who[6:] not in PERAN:
                ERR.append("dialog %s: unknown role %s" % (did, who))
        cek_baris("dialog " + did, d["syarat"], SYARAT, "condition")
        nodes = []
        ids = {n["id"] for n in d["simpul"]}
        for n in d["simpul"]:
            o = {"id": n["id"]}
            lang("kultivator.cerita.%s.%s" % (did, n["id"]), *n["teks"])
            if len(placeholders(n["teks"][0])) != len(n["isian"]) or placeholders(n["teks"][0]) != placeholders(n["teks"][1]):
                ERR.append("dialog %s.%s: %%s count does not match isian" % (did, n["id"]))
            if n["isian"]:
                o["isian"] = n["isian"]
            if n["gestur"]:
                cek_gerak(n["gestur"], did)
                o["gestur"] = n["gestur"]
            if n["prop"]:
                cek_prop(n["prop"], did)
                o["prop"] = n["prop"]
            if n["suara"]:
                if n["suara"] not in SUARA:
                    ERR.append("dialog %s: unknown voice %s" % (did, n["suara"]))
                o["suara"] = n["suara"]
            if n["akibat"]:
                cek_baris("dialog %s.%s" % (did, n["id"]), n["akibat"], AKIBAT, "effect")
                o["akibat"] = n["akibat"]
            pil = []
            for i, c in enumerate(n["pilihan"], 1):
                p = {}
                lang("kultivator.cerita.%s.%s.%d" % (did, n["id"], i), *c["teks"])
                if c["syarat"]:
                    cek_baris("dialog %s.%s.%d" % (did, n["id"], i), c["syarat"], SYARAT, "condition")
                    p["syarat"] = c["syarat"]
                if c["akibat"]:
                    cek_baris("dialog %s.%s.%d" % (did, n["id"], i), c["akibat"], AKIBAT, "effect")
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


def quests():
    lang("kultivator.cerita.tempat." + TEMPAT, *TEMPAT_TEKS)
    for q in QUESTS:
        qid = q["id"]
        assert qid.startswith("tema_iblis"), qid
        lang("kultivator.cerita.quest.%s.nama" % qid, *q["nama"])
        lang("kultivator.cerita.quest.%s.ringkas" % qid, *q["ringkas"])
        ids = [s[0]["id"] for s in q["tahap"]]
        stages = []
        ends = set()
        for i, (st, baris) in enumerate(q["tahap"]):
            if st["id"] in ("nama", "ringkas", "hasil", "selesai", "gagal"):
                ERR.append("quest %s: reserved stage id %s" % (qid, st["id"]))
            lang("kultivator.cerita.quest.%s.%s" % (qid, st["id"]), *baris)
            for line in st.get("tujuan", []):
                w = line.lstrip("!").split()
                if w and w[0] not in TUJUAN and w[0] not in SYARAT:
                    ERR.append("quest %s.%s: unknown goal %s" % (qid, st["id"], line))
            cek_baris("quest %s.%s" % (qid, st["id"]), st.get("akibat", []) + st.get("akibat_masuk", []), AKIBAT, "effect")
            for cab in st.get("cabang", []):
                cek_baris("quest %s.%s branch" % (qid, st["id"]), cab["syarat"], SYARAT, "condition")
                if cab["ke"] not in ids + ["selesai", "gagal"]:
                    ERR.append("quest %s: branch to unknown stage %s" % (qid, cab["ke"]))
            nxt = st.get("ke", ids[i + 1] if i + 1 < len(ids) else "selesai")
            for k in ("ke", "gagal_ke"):
                if k in st and st[k] not in ids + ["selesai", "gagal"]:
                    ERR.append("quest %s: %s %s unknown" % (qid, k, st[k]))
            if nxt in ("selesai", "gagal") or st.get("gagal_ke") in ("selesai", "gagal") or any(c["ke"] in ("selesai", "gagal") for c in st.get("cabang", [])):
                ends.add(st["id"])
            stages.append(st)
        for sid in ends:
            if sid not in q["hasil"]:
                ERR.append("quest %s: stage %s can end the quest but has no hasil text" % (qid, sid))
        for sid, teks in q["hasil"].items():
            if sid not in ids:
                ERR.append("quest %s: hasil for unknown stage %s" % (qid, sid))
            lang("kultivator.cerita.quest.%s.hasil.%s" % (qid, sid), *teks)
        tulis("%s/tempat/quest/%s.json" % (RES, qid), {"pemberi": q["pemberi"], "tempat": TEMPAT, "tahap": stages})
    return len(QUESTS)


# the number of arguments each code-spread rumour gets (tema/iblis/*.java CeritaApi.kabar calls; calendar/quest triggers)
KABAR_ARGS = {"tema_iblis_warga_hilang": 2, "tema_iblis_tawanan_pulang": 2, "tema_iblis_minyak_sabotase": 1, "tema_iblis_xue_kalah": 1,
              "tema_iblis_akhir_lurus": 1, "tema_iblis_akhir_penebusan": 1, "tema_iblis_akhir_darah": 1, "tema_iblis_lao_terhormat": 1,
              "tema_iblis_buronan_tewas": 2, "tema_iblis_kitab": 1, "tema_iblis_turnamen": 1, "tema_iblis_rantai_selesai": 1,
              "tema_iblis_topeng_selesai": 1}


def kabar():
    tpl = []
    for kid, pemicu, bobot, umur, peran, teks in KABAR:
        o = {"id": kid, "pemicu": pemicu or kid, "bobot": bobot, "umur_hari": umur}
        for r in peran:
            if r not in PERAN:
                ERR.append("rumour %s: unknown spreader role %s" % (kid, r))
        if peran:
            o["peran_penyebar"] = peran
        tpl.append(o)
        lang("kultivator.cerita.kabar." + kid, *teks)
        n = len(placeholders(teks[0]))
        if n != KABAR_ARGS.get(kid, 0) or placeholders(teks[0]) != placeholders(teks[1]):
            ERR.append("rumour %s: %d placeholders, code passes %d" % (kid, n, KABAR_ARGS.get(kid, 0)))
    tulis(RES + "/tempat/kabar/tema_iblis.json", {"templat": tpl})
    return len(tpl)


def advancements():
    for aid, icon, frame, title, desc in ADV:
        tulis("%s/advancement/tempat/%s.json" % (RES, aid), {
            "parent": "kultivator:tempat/tema_iblis",
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
    # every advancement the code grants must exist
    granted = set()
    for fn in os.listdir(IBLIS):
        if fn.endswith(".java"):
            granted |= set(re.findall(r'Advancements\.grant\(\w+, "tempat/([a-z_]+)"\)', open(os.path.join(IBLIS, fn), encoding="utf-8").read()))
    for g in granted:
        if not os.path.exists("%s/advancement/tempat/%s.json" % (RES, g)):
            ERR.append("advancement tempat/%s is granted in code but has no file" % g)
    return len(ADV)


def loot():
    for name, table in LOOT.items():
        tulis("%s/loot_table/tema/iblis/%s.json" % (RES, name), table)
    # every loot table the generator's chests name must exist
    gen = open(HERE + "/gen_tempat_tema_iblis.py", encoding="utf-8").read()
    for ref in set(re.findall(r"kultivator:tema/iblis/([a-z_]+)", gen)):
        if ref not in LOOT:
            ERR.append("chest loot table tema/iblis/%s has no definition" % ref)
    return len(LOOT)


def java_keys():
    """Every literal translation key of tema/iblis/*.java and the number of arguments its calls pass (max)."""
    keys = {}
    for fn in sorted(os.listdir(IBLIS)):
        if not fn.endswith(".java"):
            continue
        s = open(os.path.join(IBLIS, fn), encoding="utf-8").read()
        for m in re.finditer(r'"((?:kultivator\.kota_tema\.iblis|kultivator\.sibuk\.tema)\.[a-z0-9_.]*[a-z0-9_])"', s):
            keys.setdefault(m.group(1), set())
        for m in re.finditer(r'Component\.translatable\(\s*"(kultivator\.kota_tema\.iblis\.[a-z0-9_.]*[a-z0-9_])"\s*([,)])', s):
            if m.group(2) == ")":
                keys[m.group(1)].add(0)
                continue
            i = m.end()
            depth, args, instr = 1, 1, False
            while i < len(s) and depth > 0:
                ch = s[i]
                if instr:
                    if ch == chr(92):
                        i += 1
                    elif ch == '"':
                        instr = False
                elif ch == '"':
                    instr = True
                elif ch in "([{":
                    depth += 1
                elif ch in ")]}":
                    depth -= 1
                elif ch == "," and depth == 1:
                    args += 1
                i += 1
            keys[m.group(1)].add(args)
    return keys


def lang_files():
    ui_lang()
    for key, uses in java_keys().items():
        if key not in L:
            ERR.append("key %s is used in tema/iblis/*.java but has no text" % key)
            continue
        if uses:
            n = len(placeholders(L[key][0]))
            if n > max(uses):
                ERR.append("key %s uses %d placeholders but its call passes %d" % (key, n, max(uses)))
    for key, (i, en) in L.items():
        if placeholders(i) != placeholders(en):
            ERR.append("key %s: id/en placeholders differ" % key)
        if not i.strip() or not en.strip():
            ERR.append("key %s: empty text" % key)
    others = {}
    for fn in os.listdir(LANG):
        if fn.startswith(FRAG + ".") or not fn.endswith(".id_id.json"):
            continue
        for k in json.load(open(os.path.join(LANG, fn), encoding="utf-8")):
            others[k] = fn
    for k in L:
        if k in others:
            ERR.append("key %s already in %s" % (k, others[k]))
    keys = sorted(L)
    if not ERR:
        tulis(LANG + "/%s.id_id.json" % FRAG, {k: L[k][0] for k in keys})
        tulis(LANG + "/%s.en_us.json" % FRAG, {k: L[k][1] for k in keys})
    return len(keys)


if __name__ == "__main__":
    n_d = dialogs()
    n_q = quests()
    n_k = kabar()
    n_a = advancements()
    n_t = loot()
    jadwal()
    n_p = permintaan()
    n_l = lang_files()
    print("dialogs %d, quests %d, rumours %d, advancements %d, loot tables %d, trade goods %d, lang keys %d"
          % (n_d, n_q, n_k, n_a, n_t, n_p, n_l))
    if ERR:
        print("ERRORS (%d):" % len(ERR))
        for x in ERR:
            print("  " + x)
        sys.exit(1)
    print("OK")

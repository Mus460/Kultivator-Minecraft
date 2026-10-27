#!/usr/bin/env python3
"""DESIGN 42.5 / 42.10 (module 42B): the lang fragment of the talk code ("kota_hidup_bicara").

Writes lang-src/kota_hidup_bicara.{id_id,en_us}.json (identical keys; Indonesian primary; LF) for every text the code of
dunia/kota42/bicara/** and DuniaMarkers ("kota/tujuan") shows, and checks that no key of this fragment lives in another one.
The taste names rasa.pangsit / rasa.kertas / rasa.buah and the request texts of tempat/tolong/*.json belong to 42B-C's
fragment kota_hidup_cerita; the defaults of the 16 request kinds below are only used when a data file is missing.

  python -I tools/gen_kota_hidup_bicara_lang.py [repo]      (deterministic)
Self-check: every static key and every dynamic family found in bicara/*.java has its variants here (scan at the end).
"""
import glob
import json
import os
import re
import sys

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(REPO, 'lang-src')
FRAG = 'kota_hidup_bicara'
P = 'kultivator.kota42.bicara.'
L = {}


def k(key, idn, en):
    if key in L:
        raise SystemExit('duplicate key ' + key)
    L[key] = (idn, en)


def fam(prefix, variants):
    """variants: {suffix: (id, en)}"""
    for suffix, (idn, en) in variants.items():
        k(prefix + suffix, idn, en)


# ------------------------------------------------------------------------------------------------ talk sheet
k(P + 'terkunci', 'Warga ini belum cukup percaya padamu.', 'This resident does not trust you enough yet.')
k(P + 'kerja.pegawai', 'Pegawai · %s', 'Employee · %s')
k(P + 'kesan_naik', 'Kesannya padamu naik %s.', 'Their opinion of you rose by %s.')
k(P + 'kesan_turun', 'Kesannya padamu turun %s.', 'Their opinion of you fell by %s.')
k(P + 'kartu_tutup', 'Kartu warga tidak bisa dibuka sekarang.', 'The resident card cannot be opened right now.')
fam(P + 'nada_terkunci.', {
    'sopan': ('Nada sopan selalu bisa dipakai.', 'The polite tone is always allowed.'),
    'akrab': ('Belum cukup akrab untuk berbicara seperti kawan.', 'You are not close enough to talk like a friend.'),
    'merayu': ('Merayu hanya pantas bagi yang sudah akrab, dan ia harus lajang.', 'Flirting is only fit for the close, and they must be single.'),
    'mengancam': ('Kau belum cukup ditakuti untuk mengancam.', 'You are not feared enough to threaten anyone.'),
    'menyuap': ('Menyuap perlu uang atau hadiah yang pantas di tangan.', 'A bribe needs money or a worthy gift in hand.'),
    'bercanda': ('Suasana hatinya sedang tidak cocok untuk bercanda.', 'Their mood does not suit a joke right now.'),
})
fam(P + 'aksi.', {
    'kartu': ('Kartu Warga', 'Resident Card'),
    'pinjam': ('Pinjam hingga %s NB', 'Borrow up to %s NB'),
    'lunasi': ('Lunasi utangnya (%s NB)', 'Repay the debt (%s NB)'),
    'tanpa_uang': ('Kau tidak punya cukup uang.', 'You do not have enough money.'),
})

# ------------------------------------------------------------------------------------------------ Kabar (rumours)
fam(P + 'kabar.', {
    'samar': ('Katanya ada kabar, tapi ia lupa rinciannya.', 'He says there was news, but the details escape him.'),
    'entah': ('entah siapa', 'someone'),
    'lupa': ('Aduh, saya lupa dari mana mendengarnya.', 'Hm, I forget where I heard it.'),
    'jejak_saksi': ('Orang yang melihat langsung', 'Someone who saw it first-hand'),
    'jejak_pendekar': ('Seorang pendekar yang lewat', 'A passing swordsman'),
    'lihat_sendiri.ragu': ('Aku melihatnya sendiri... meski sulit dipercaya.', 'I saw it myself... though it is hard to believe.'),
    'lihat_sendiri.bisik': ('(berbisik) Aku melihatnya sendiri. Jangan bilang siapa-siapa.', '(whispering) I saw it myself. Tell no one.'),
    'lihat_sendiri.biasa': ('Aku melihatnya dengan mata kepala sendiri.', 'I saw it with my own eyes.'),
    'dari_pendekar.ragu': ('Katanya dari seorang pendekar lewat, tapi siapa yang bisa memastikan?', 'Said to be from a passing swordsman, but who can tell?'),
    'dari_pendekar.bisik': ('(berbisik) Dari seorang pendekar yang lewat. Jangan keras-keras.', '(whispering) From a passing swordsman. Keep it down.'),
    'dari_pendekar.biasa': ('Kudengar dari seorang pendekar yang lewat.', 'I heard it from a passing swordsman.'),
    'dari.ragu': ('Dari %s, tapi kau tahu sendiri orangnya.', 'From %s, but you know what they are like.'),
    'dari.bisik': ('(berbisik) %s yang cerita. Jangan bilang aku yang membuka mulut.', '(whispering) %s told me. Do not say I opened my mouth.'),
    'dari.biasa': ('Kudengar dari %s.', 'I heard it from %s.'),
})

# ------------------------------------------------------------------------------------------------ Sebar (recruiting word)
fam(P + 'sebar.', {
    'tanpa_aula': ('Kau belum punya aula sekte yang berdiri.', 'You have no standing sect hall yet.'),
    'sudah': ('Hari ini kau sudah menyebarkan kabar di kota ini.', 'You have already spread the word in this town today.'),
    'kurang': ('Perlu %s NB untuk teh dan kabar.', 'It takes %s NB for tea and news.'),
    'ok': ('Kabar tersebar. Lebih banyak pelamar datang selama %s hari.', 'The word is out. More hopefuls will come for %s days.'),
    'teks.0': ('Sekte %s menerima murid? Akan kuceritakan ke semua tetangga!', 'Is the %s sect taking disciples? I will tell every neighbour!'),
    'teks.1': ('Kabar bagus dari sekte %s. Anak-anak berbakat di sini pasti ingin tahu.', 'Good news from the %s sect. The gifted children here will want to hear.'),
    'teks.2': ('Aku akan menyebut nama %s di sumur besok pagi. Para bibi suka kabar begini.', 'I will mention %s at the well tomorrow morning. The aunts love news like this.'),
})

# ------------------------------------------------------------------------------------------------ Beri (gifts)
fam(P + 'beri.', {
    'petunjuk_umum': ('Dia tidak punya kesukaan khusus.', 'They have no special liking.'),
    'petunjuk': ('Sepertinya dia suka %s.', 'They seem to like %s.'),
    'belum_kenal': ('Belum cukup kenal untuk tahu seleranya.', 'You do not know them well enough to know their taste.'),
    'tangan_kosong': ('Tanganmu kosong. Pegang barang dulu.', 'Your hand is empty. Hold an item first.'),
    'tiga_hari': ('Hadiah berikutnya baru dihitung %s hari lagi.', 'The next gift counts in %s days.'),
    'tidak_dihitung': ('Hadiah diterima, tetapi belum dihitung (jeda tiga hari).', 'The gift was accepted but does not count yet (three-day pause).'),
    'pesan.dermawan': ('Hadiah yang murah hati. Kesannya naik banyak.', 'A generous gift. Their opinion rose a lot.'),
    'pesan.suka': ('Dia menyukainya. Kesannya naik.', 'They liked it. Their opinion rose.'),
    'pesan.benci': ('Dia tidak menyukainya. Kesannya turun.', 'They disliked it. Their opinion fell.'),
    'pesan.biasa': ('Dia menerimanya dengan sopan.', 'They accepted it politely.'),
    'reaksi.dermawan.0': ('Ya ampun, %s? Ini terlalu berharga! Aku tidak akan melupakan kebaikanmu.', 'My goodness, %s? This is far too precious! I will not forget your kindness.'),
    'reaksi.dermawan.1': ('%s... untuk kami? Tuan sungguh dermawan. Seluruh jalan akan mendengarnya.', '%s... for us? You are truly generous. The whole street will hear of it.'),
    'reaksi.dermawan.2': ('Aku hampir menangis. %s, dari orang asing sebaik ini?', 'I could almost cry. %s, from a stranger this kind?'),
    'reaksi.suka.0': ('Wah, %s! Persis yang kusuka. Terima kasih!', 'Oh, %s! Just what I like. Thank you!'),
    'reaksi.suka.1': ('%s! Bagaimana Tuan tahu seleraku? Terima kasih banyak.', '%s! How did you know my taste? Thank you so much.'),
    'reaksi.suka.2': ('Ah, %s. Hari ini jadi lebih baik. Terima kasih.', 'Ah, %s. This day just got better. Thank you.'),
    'reaksi.benci.0': ('%s? Hm... terima kasih, kurasa. Aku tidak begitu suka itu.', '%s? Hm... thank you, I suppose. I am not fond of that.'),
    'reaksi.benci.1': ('Eh, %s... Tuan baik hati, tapi itu bukan seleraku.', 'Er, %s... you are kind, but that is not my taste.'),
    'reaksi.benci.2': ('%s. Kuterima saja, supaya tidak menyinggung.', '%s. I will take it, so as not to offend.'),
    'reaksi.biasa.0': ('%s, ya? Terima kasih, Tuan.', '%s, is it? Thank you, sir.'),
    'reaksi.biasa.1': ('Oh, %s. Baik sekali, terima kasih.', 'Oh, %s. How kind, thank you.'),
    'reaksi.biasa.2': ('%s untukku? Terima kasih, akan kupakai.', '%s for me? Thank you, I will make use of it.'),
})
fam(P + 'rasa.', {
    'arak': ('arak', 'wine'), 'teh': ('teh', 'tea'), 'kitab': ('kitab dan buku', 'books and scrolls'),
    'manis': ('yang manis-manis', 'sweets'), 'kain': ('kain halus', 'fine cloth'), 'bunga': ('bunga', 'flowers'),
    'ikan': ('ikan', 'fish'), 'obat': ('obat-obatan', 'medicine'), 'herba': ('ramuan herba', 'herbs'),
    'jimat': ('jimat', 'talismans'), 'senjata': ('senjata', 'weapons'), 'mewah': ('barang mewah', 'luxuries'),
    'daging': ('daging', 'meat'), 'biji': ('biji-bijian', 'seeds and grain'),
})

# ------------------------------------------------------------------------------------------------ Pinjaman (loans)
fam(P + 'pinjam.', {
    'mati': ('Pinjam-meminjam sedang tidak dilayani di kota ini.', 'Lending and borrowing is not offered in this town right now.'),
    'bukan_pemodal': ('Dia bukan orang yang punya uang lebih untuk dipinjamkan.', 'They are not someone with spare money to lend.'),
    'tak_mampu': ('Keuangan rumah tangganya sendiri sedang pas-pasan.', 'Their own household is barely getting by.'),
    'belum_percaya': ('Dia belum cukup percaya padamu (perlu kesan %s).', 'They do not trust you enough yet (needs opinion %s).'),
    'masih_utang': ('Kau masih punya pinjaman yang belum lunas padanya.', 'You still owe them an unpaid loan.'),
    'kas_kosong': ('Kas Kota sedang kosong, tidak ada yang bisa dipinjamkan.', 'The town chest is empty, nothing can be lent.'),
    'beri.0': ('Baiklah, %1$s NB. Kembalikan dalam %2$s hari, bunganya %3$s%% seminggu.', 'Very well, %1$s NB. Pay it back within %2$s days, interest %3$s%% a week.'),
    'beri.1': ('%1$s NB, pinjaman biasa. Jatuh tempo %2$s hari, bunga %3$s%%. Jangan lupa.', '%1$s NB, an ordinary loan. Due in %2$s days, interest %3$s%%. Do not forget.'),
    'beri.2': ('Kupinjamkan %1$s NB dari kas. %2$s hari, bunga %3$s%%. Aku percaya padamu.', 'I will lend %1$s NB from the chest. %2$s days, interest %3$s%%. I trust you.'),
    'ok': ('Kau meminjam %s NB. Jatuh tempo %s hari lagi.', 'You borrowed %s NB. It falls due in %s days.'),
    'tak_ada_utang': ('Kau tidak punya utang maupun piutang dengan warga ini.', 'You have no debt or loan with this resident.'),
    'cicil': ('Terima kasih, %1$s NB diterima. Sisa utangnya %2$s NB.', 'Thank you, %1$s NB received. %2$s NB still owed.'),
    'lunas.0': ('Lunas! Senang berurusan denganmu.', 'Paid in full! A pleasure doing business with you.'),
    'lunas.1': ('Utangku selesai. Terima kasih sudah sabar.', 'My debt is done. Thank you for your patience.'),
    'dibayar': ('Kau membayar %s NB.', 'You paid %s NB.'),
    'kembali.0': ('Uangmu kembali: %1$s NB pokok dan %2$s NB bunga. Terima kasih banyak.', 'Your money is back: %1$s NB principal and %2$s NB interest. Many thanks.'),
    'kembali.1': ('Ini pinjamanmu, %1$s NB, plus bunga %2$s NB. Semoga rezekimu lancar.', 'Here is your loan, %1$s NB, plus %2$s NB interest. May your fortune flow.'),
    'kembali_chat': ('%1$s melunasi pinjamannya: %2$s NB pokok + %3$s NB bunga.', '%1$s repaid the loan: %2$s NB principal + %3$s NB interest.'),
    'warga_mangkir': ('%s tidak sanggup melunasi pinjamannya. Uangmu hilang, dan kabarnya menyebar.', '%s could not repay the loan. Your money is lost, and word of it spreads.'),
    'mangkir': ('Kau mangkir dari utangmu pada %1$s (%2$s NB). Kesannya padamu jatuh.', 'You defaulted on your debt to %1$s (%2$s NB). Their opinion of you has fallen.'),
    'digugat': ('Utangmu yang mangkir digugat ke Yamen: %s NB menjadi denda.', 'Your defaulted debt was taken to the Yamen: %s NB became a fine.'),
})

# ------------------------------------------------------------------------------------------------ Tanya Jalan (asking the way)
fam(P + 'jalan.', {
    'minta_uang': ('Hmm, soal jalan itu... %s NB dulu, baru kuberitahu.', 'Hmm, about that way... %s NB first, then I will tell you.'),
    'kurang_uang': ('Uangmu kurang dari %s NB.', 'You have less than %s NB.'),
    'terkunci': ('Bayar dulu untuk tahu.', 'Pay first to find out.'),
    'anak_tidak_tahu': ('Aku... tidak tahu, Tuan. Tanya bibi-bibi di sumur saja, ya.', 'I... do not know, sir. Ask the aunts at the well.'),
    'baru_umum': ('Aku baru di sini. Tanyakan saja pada bibi-bibi di sumur, mereka tahu segalanya.', 'I am new here. Ask the aunts at the well, they know everything.'),
    'baru_tanya': ('Aku baru di sini. Coba tanya %s, dia tahu banyak.', 'I am new here. Try asking %s, they know a lot.'),
    'hilang': ('Tujuan itu sudah tidak ketemu. Tanya lagi nanti.', 'That place cannot be found any more. Ask again later.'),
    'ditandai': ('%s ditandai di peta selama 5 menit.', '%s is marked on the map for 5 minutes.'),
    'sampai': ('Kau sampai di %s.', 'You have arrived at %s.'),
    'kompas': ('%1$s: %2$s blok ke %3$s', '%1$s: %2$s blocks %3$s'),
    'langkah': ('%s langkah', '%s paces'),
    'li': ('%s li', '%s li'),
    'di_sini': ('%s? Itu di sini juga, lihat sekelilingmu.', '%s? That is right here, look around you.'),
    'di_sini_orang': ('%s ada di dekat sini. Lihat saja sekeliling.', '%s is somewhere near. Just look around.'),
    'orang': ('%1$s sekarang %2$s. Dari sini ke arah %3$s, kira-kira %4$s.', '%1$s is %2$s right now. From here towards the %3$s, about %4$s.'),
    'orang_patokan': ('%1$s sekarang %2$s. Dari sini ke arah %3$s, %4$s, kira-kira %5$s.', '%1$s is %2$s right now. From here towards the %3$s, %4$s, about %5$s.'),
    'arah': ('%1$s? Ke arah %2$s, kira-kira %3$s dari sini.', '%1$s? Towards the %2$s, about %3$s from here.'),
    'arah_patokan': ('%1$s? Ke arah %2$s, %3$s, kira-kira %4$s dari sini.', '%1$s? Towards the %2$s, %3$s, about %4$s from here.'),
    'patokan_distrik': ('di %s', 'in the %s'),
    'patokan_dua': ('di %1$s, %2$s', 'in the %1$s, %2$s'),
    'kegiatan.rumah': ('di rumah', 'at home'),
    'kegiatan.kerja': ('sedang bekerja', 'at work'),
    'kegiatan.kedai': ('di kedai', 'at the tavern'),
    'kegiatan.pasar': ('di pasar', 'at the market'),
    'kegiatan.kuil': ('di kuil', 'at the temple'),
    'kegiatan.jalan': ('sedang berjalan-jalan', 'out for a stroll'),
    'patokan.sumur': ('dekat sumur', 'near the well'),
    'patokan.panggung': ('dekat panggung', 'near the stage'),
    'patokan.catur': ('dekat pohon tempat orang main catur', 'near the chess tree'),
    'patokan.lumbung': ('dekat lumbung', 'near the granary'),
    'patokan.nama_jalan': ('dekat papan nama jalan', 'near the street sign'),
    'patokan.panji': ('dekat panji sekte', 'near the sect banner'),
    'layanan.pasar_hantu': ('Pasar Hantu (%s)', 'Ghost Market (%s)'),
    'layanan.rumahku': ('Rumahmu', 'Your house'),
})
fam(P + 'jalan.layanan.', {
    'rumah_makan': ('Rumah Makan', 'Restaurant'), 'penginapan': ('Penginapan', 'Inn'), 'tabib': ('Tabib', 'Healer'),
    'rumah_uang': ('Rumah Uang', 'Money House'), 'rumah_dagang': ('Rumah Dagang', 'Trading House'), 'yamen': ('Yamen', 'Yamen'),
    'gudang': ('Gudang', 'Warehouse'), 'gilda_alkemis': ('Gilda Alkemis', 'Alchemist Guild'), 'gilda_tempa': ('Gilda Tempa', 'Smiths Guild'),
    'toko_senjata': ('Toko Senjata', 'Weapon Shop'), 'gilda_misi': ('Gilda Misi', 'Mission Guild'), 'perpustakaan': ('Perpustakaan', 'Library'),
    'teleport': ('Formasi Teleportasi', 'Teleport Formation'), 'pasar_binatang': ('Pasar Binatang', 'Beast Market'),
    'judi_batu': ('Rumah Judi Batu', 'Stone Gambling House'), 'lelang': ('Balai Lelang', 'Auction Hall'), 'kedai_teh': ('Kedai Teh', 'Tea House'),
    'toko_pil': ('Toko Pil', 'Pill Shop'), 'pandai': ('Pandai Besi', 'Blacksmith'), 'kuil': ('Kuil', 'Temple'), 'dermaga': ('Dermaga', 'Pier'),
    'pasar_ikan': ('Pasar Ikan', 'Fish Market'), 'bazar': ('Bazar', 'Bazaar'), 'toko_sutra': ('Toko Sutra', 'Silk Shop'),
    'sumur': ('Sumur', 'Well'), 'kantor_rekrut': ('Kantor Rekrutmen', 'Recruitment Office'), 'lumbung': ('Lumbung', 'Granary'),
    'panggung': ('Panggung', 'Stage'), 'sidang': ('Balai Sidang', 'Court Hall'), 'guanpu': ('Meja Guanpu', 'Guanpu Table'),
})
k('kultivator.dunia.marker.kota.tujuan', 'Tujuan', 'Destination')

# ------------------------------------------------------------------------------------------------ Tolong (requests)
fam(P + 'tolong.', {
    'seseorang': ('seseorang', 'someone'),
    'tidak_ada': ('Tidak ada permintaan tolong dari warga ini.', 'This resident has no request for help.'),
    'batas_harian': ('Kau sudah menerima %s permintaan hari ini.', 'You have already accepted %s requests today.'),
    'diterima': ('Permintaan diterima: %s', 'Request accepted: %s'),
    'belum': ('Tugasnya belum selesai.', 'The task is not done yet.'),
    'tanpa_obat': ('Kau tidak punya obat yang diminta. Tabib bisa dibayar %s NB.', 'You have no medicine to give. A healer can be paid %s NB.'),
    'tanpa_barang': ('Kau perlu %1$s × %2$s untuk ini.', 'You need %1$s × %2$s for this.'),
    'kurang_uang': ('Uangmu kurang: perlu %s NB.', 'You are short of money: %s NB needed.'),
    'beres': ('Permintaan selesai: %s.', 'Request done: %s.'),
    'maju': ('Satu langkah selesai. Lanjutkan.', 'One step done. Carry on.'),
    'selesai_chat': ('%1$s berterima kasih. Permintaan "%2$s" selesai.', '%1$s thanks you. The request "%2$s" is done.'),
    'damai_ok': ('%1$s dan %2$s sepakat berdamai, setidaknya untuk sekarang.', '%1$s and %2$s agreed to make peace, for now at least.'),
    'damai_gagal': ('%1$s dan %2$s tetap tidak mau saling bicara.', '%1$s and %2$s still refuse to speak to each other.'),
    'surat_sampai': ('Suratnya sampai ke tujuan.', 'The letter reached its reader.'),
    'kucing_ketemu': ('Kau menemukan kucing yang hilang!', 'You found the missing cat!'),
    'anak_ketemu': ('Kau menemukan anak yang hilang!', 'You found the lost child!'),
    'temani_hitung': ('Menemani... %1$s / %2$s detik', 'Keeping company... %1$s / %2$s seconds'),
    'plat_copet': ('Pencopet', 'Pickpocket'),
    'plat_preman': ('Preman', 'Thug'),
    'copet_seru': ('Mau menangkapku? Coba saja!', 'Want to catch me? Try it!'),
    'preman_kabur': ('Ampun! Aku pergi!', 'Mercy! I am leaving!'),
    'tujuan.serah': ('Serahkan %1$s × %2$s', 'Hand over %1$s × %2$s'),
    'tujuan.bayar.umum': ('Bayar %s NB', 'Pay %s NB'),
    'tujuan.bayar.rentenir': ('Lunasi utang ke rentenir: %s NB', 'Settle the loan shark: %s NB'),
    'tujuan.bayar.pinjam': ('Pinjamkan %s NB', 'Lend %s NB'),
    'tujuan.bicara.lapor': ('Lapor kembali kepada %s', 'Report back to %s'),
    'tujuan.bicara.surat': ('Antarkan surat kepada %s', 'Deliver the letter to %s'),
    'tujuan.bicara.damai': ('Bujuk %s agar berdamai', 'Persuade %s to make peace'),
    'tujuan.bicara.comblang': ('Temui mak comblang %s', 'See the matchmaker %s'),
    'tujuan.bicara.jodoh': ('Temui calon jodoh, %s', 'Meet the prospective match, %s'),
    'tujuan.temukan.kucing': ('Temukan kucing milik %s', "Find %s's cat"),
    'tujuan.temukan.anak': ('Temukan %s, anak yang hilang', 'Find %s, the lost child'),
    'tujuan.temani.panen': ('Temani %1$s memanen selama %2$s detik', 'Help %1$s with the harvest for %2$s seconds'),
    'tujuan.temani.ajar': ('Temani %1$s belajar selama %2$s detik', 'Sit with %1$s studying for %2$s seconds'),
    'tujuan.duel.preman': ('Kalahkan %s preman dalam duel persahabatan', 'Beat %s thugs in a friendly duel'),
    'tujuan.duel.copet': ('Kalahkan pencopet dalam duel persahabatan (%s)', 'Beat the pickpocket in a friendly duel (%s)'),
    'tujuan.pergi.gerbang': ('Bawa kabar ke gerbang sekte tempat %s berguru', 'Take word to the sect gate where %s studies'),
    'tujuan.hadir': ('Hadiri perayaan keluarga %s', "Attend %s's family celebration"),
    'barang.logs': ('kayu gelondong', 'logs'),
    'barang.planks': ('papan kayu', 'planks'),
    'dengar.surat': ('Surat dari dia? Terima kasih... katakan padanya aku menunggu.', 'A letter from them? Thank you... tell them I am waiting.'),
    'dengar.damai': ('Hm. Baiklah, akan kupikirkan baik-baik.', 'Hm. Very well, I will think it over.'),
    'dengar.comblang': ('Oh, ada yang mencari jodoh? Duduklah, kita bicarakan pelan-pelan.', 'Oh, someone is looking for a match? Sit down, we will talk it over.'),
    'dengar.jodoh': ('Dia yang menyuruhmu? Hmm... boleh juga kita bertemu.', 'They sent you? Hmm... we could meet, I suppose.'),
    'dengar.tekan': ('B-baik, baik! Tak perlu ribut, utangnya kuhapus.', 'A-all right! No need for a fuss, I will wipe the debt.'),
    'terima.0': ('Terima kasih sudah mau membantu. Aku benar-benar tertolong.', 'Thank you for helping. You are a real lifesaver.'),
    'terima.1': ('Tuan baik sekali mau mendengarkan. Semoga cepat selesai.', 'You are kind to listen. May it be done soon.'),
    'terima.2': ('Aku tidak tahu harus minta tolong siapa lagi. Terima kasih.', 'I did not know who else to ask. Thank you.'),
    'terima_kasih.0': ('Sudah beres? Terima kasih, Tuan. Aku berutang budi.', 'Done already? Thank you, sir. I owe you.'),
    'terima_kasih.1': ('Tuan menyelamatkan hariku. Terima kasih banyak.', 'You saved my day. Thank you so much.'),
    'terima_kasih.2': ('Sungguh baik hati. Aku akan menceritakannya pada tetangga.', 'Truly kind. I will tell the neighbours.'),
    'anak_lega.0': ('Hu hu... terima kasih, Kak. Aku mau pulang.', 'Sniff... thank you. I want to go home.'),
    'anak_lega.1': ('Aku tadi tersesat. Kakak baik, ya.', 'I got lost. You are kind.'),
    'temani.panen.0': ('Kerjanya cepat sekali!', 'You work fast!'),
    'temani.panen.1': ('Hati-hati sabitnya, ya.', 'Mind the sickle.'),
    'temani.panen.2': ('Sedikit lagi, sebelum hujan.', 'Almost done, before the rain.'),
    'temani.ajar.0': ('Coba baca satu lagi, Nak.', 'Try reading one more, child.'),
    'temani.ajar.1': ('Nah, yang ini artinya apa?', 'Now, what does this one mean?'),
    'temani.ajar.2': ('Pintar. Teruskan.', 'Clever. Go on.'),
    'preman_seru.0': ('Heh, orang luar! Mau cari gara-gara?', 'Hey, outsider! Looking for trouble?'),
    'preman_seru.1': ('Gang ini wilayah kami. Bayar atau pergi!', 'This alley is ours. Pay up or leave!'),
    'preman_seru.2': ('Kau kira bisa lewat begitu saja?', 'You think you can just walk past?'),
})
# defaults of the 16 request kinds (used only when tempat/tolong/<kind>.json is missing; the data files carry the real texts)
JENIS = {
    'obat_ortu': ('Obat untuk yang Sakit', 'Medicine for the Sick', '%1$s sakit dan kami butuh obat. Tolonglah.', '%1$s is ill and we need medicine. Please help.',
                  '%1$s demam tiga malam. Kalau Tuan punya pil penyembuh...', '%1$s has had a fever three nights. If you have a healing pill...',
                  'Terima kasih. Ini %s NB, semua yang kami punya.', 'Thank you. Here is %s NB, all we have.'),
    'kayu_bakar': ('Kayu Bakar', 'Firewood', 'Dapurku dingin. Tolong antar %2$s kayu gelondong.', 'My hearth is cold. Please bring %2$s logs.',
                   'Musim dingin tiba, %2$s kayu saja sudah cukup.', 'Winter is here, just %2$s logs would do.',
                   'Terima kasih. Ada semangkuk sup hangat untukmu.', 'Thank you. There is a warm bowl of soup for you.'),
    'kucing_hilang': ('Kucing yang Hilang', 'The Missing Cat', 'Kucingku hilang sejak kemarin. Mungkin di tepi kota.', 'My cat has been gone since yesterday. Maybe at the edge of town.',
                      'Si belang belum pulang. Bisakah Tuan mencarinya?', 'The tabby has not come home. Could you look for it?',
                      'Terima kasih sudah menemukannya.', 'Thank you for finding it.'),
    'anak_hilang': ('Anak yang Hilang', 'The Lost Child', 'Anakku %1$s belum pulang! Tolong cari dia.', 'My child %1$s has not come home! Please find them.',
                    '%1$s main sampai jauh dan belum kembali. Tolong...', '%1$s wandered off and has not returned. Please...',
                    'Kau menyelamatkannya. Ambillah %s NB sebagai tanda terima kasih.', 'You saved them. Take %s NB as thanks.'),
    'surat_kekasih': ('Surat untuk Kekasih', 'A Letter for a Sweetheart', 'Maukah Tuan mengantar suratku kepada %1$s? Diam-diam.', 'Would you carry my letter to %1$s? Quietly.',
                      'Ada seseorang di hatiku, %1$s. Tolong sampaikan surat ini.', 'Someone is in my heart, %1$s. Please deliver this letter.',
                      'Terima kasih. Semoga ada balasan.', 'Thank you. May there be a reply.'),
    'damaikan': ('Damaikan Dua Orang', 'Make Peace', 'Aku bertengkar dengan %1$s. Bisakah Tuan menengahi?', 'I quarrelled with %1$s. Could you mediate?',
                 'Sudah lama aku dan %1$s tidak bicara. Tolong bujuk dia.', '%1$s and I have not spoken for ages. Please talk to them.',
                 'Terima kasih sudah menengahi.', 'Thank you for mediating.'),
    'rentenir': ('Utang kepada Rentenir', 'Debt to the Loan Shark', 'Aku berutang %2$s NB pada %1$s dan bunganya mencekik. Tolong...', 'I owe %1$s %2$s NB and the interest is choking me. Please...',
                 '%1$s menagih terus. %2$s NB, itu yang harus kulunasi.', '%1$s keeps collecting. %2$s NB is what I must settle.',
                 'Terima kasih. Aku bebas dari jeratnya.', 'Thank you. I am free of his grip.'),
    'preman': ('Preman di Gang', 'Thugs in the Alley', 'Preman memalak kami tiap malam. Bisakah Tuan mengusir mereka?', 'Thugs shake us down every night. Could you drive them off?',
               'Gang kami tidak aman lagi. Usir para preman itu, Tuan.', 'Our alley is no longer safe. Drive those thugs out, sir.',
               'Terima kasih. Ambillah %s NB dari kas kota.', 'Thank you. Take %s NB from the town chest.'),
    'barang_dicopet': ('Barang Dicopet', 'Stolen Goods', 'Dompetku dicopet di pasar! Tangkaplah pencurinya, Tuan.', 'My purse was stolen at the market! Catch the thief, sir.',
                       'Seorang pencopet lari dengan hartaku. Tolong tangkap.', 'A pickpocket ran off with my savings. Please catch them.',
                       'Terima kasih. %s NB dari kas kota untuk Tuan.', 'Thank you. %s NB from the town chest for you.'),
    'bantu_panen': ('Bantu Panen', 'Help with the Harvest', 'Padi sudah menguning dan tenagaku kurang. Temani aku memanen?', 'The rice is golden and I am short of hands. Help me harvest?',
                    'Panen tak bisa menunggu. Bisakah Tuan menemani sebentar?', 'The harvest cannot wait. Could you keep me company a while?',
                    'Terima kasih. Hasil panen tahun ini cukup baik.', 'Thank you. This year is a good harvest.'),
    'bahan_rumah': ('Bahan untuk Rumah', 'Materials for the House', 'Rumahku rusak dan papan habis. Bisa bantu %2$s papan?', 'My house is damaged and the planks are gone. Can you bring %2$s planks?',
                    'Atapku bocor. %2$s papan akan menyelamatkanku.', 'My roof leaks. %2$s planks would save me.',
                    'Terima kasih. Rumahku utuh lagi.', 'Thank you. My house is whole again.'),
    'ajari_anak': ('Ajari Anak', 'Teach the Child', 'Anakku, %1$s, ingin belajar. Maukah Tuan menemaninya?', 'My child %1$s wants to learn. Would you sit with them?',
                   '%1$s suka buku, tapi aku tak bisa mengajarinya. Tolong.', '%1$s loves books, but I cannot teach them. Please.',
                   'Terima kasih. Dia sangat senang.', 'Thank you. They were delighted.'),
    'cari_jodoh': ('Cari Jodoh', 'Finding a Match', 'Umurku sudah cukup. Tolong temuilah %1$s untukku.', 'I am of age. Please go and meet %1$s for me.',
                   'Mak comblang bilang %1$s cocok untukku. Bisakah Tuan menjajaki?', 'The matchmaker says %1$s suits me. Could you find out?',
                   'Terima kasih. Mudah-mudahan jodoh.', 'Thank you. May it be a match.'),
    'kabar_anak_sekte': ('Kabar Anak di Sekte', 'News of the Child in the Sect', 'Anakku %1$s di sekte sudah lama tak berkabar. Tolong lihat.', 'My child %1$s has not written from the sect for ages. Please look in.',
                         'Bisakah Tuan menengok %1$s di gerbang sekte dan membawa kabar?', 'Could you visit %1$s at the sect gate and bring word?',
                         'Terima kasih. Hatiku tenang sekarang.', 'Thank you. My heart is at ease now.'),
    'pinjam_uang': ('Pinjam Uang', 'A Loan', 'Aku butuh %2$s NB sampai panen. Bisakah Tuan meminjamiku?', 'I need %2$s NB until harvest. Could you lend it to me?',
                    'Hidup sedang sulit. Pinjamkan %2$s NB, akan kukembalikan.', 'Times are hard. Lend me %2$s NB, I will repay it.',
                    'Terima kasih. Akan kukembalikan, janji.', 'Thank you. I will pay it back, I promise.'),
    'undangan': ('Undangan Pesta Keluarga', 'Family Feast Invitation', 'Keluarga kami berpesta. Datanglah, Tuan, kami senang bila hadir.', 'Our family is holding a feast. Please come, we would be glad.',
                 'Besok ada perayaan di rumah kami. Tuan kami undang.', 'There is a celebration at our house tomorrow. You are invited.',
                 'Terima kasih sudah hadir.', 'Thank you for coming.'),
}
for jid, (n_id, n_en, t0_id, t0_en, t1_id, t1_en, r_id, r_en) in JENIS.items():
    k(P + f'tolong.{jid}.nama', n_id, n_en)
    k(P + f'tolong.{jid}.teks.0', t0_id, t0_en)
    k(P + f'tolong.{jid}.teks.1', t1_id, t1_en)
    k(P + f'tolong.{jid}.imbalan', r_id, r_en)

# ------------------------------------------------------------------------------------------------ test commands, story effects, characters
fam(P + 'uji.', {
    'tanpa_warga': ('Tidak ada warga dalam 8 blok.', 'No resident within 8 blocks.'),
    'baris': ('%1$s [%2$s]: %3$s', '%1$s [%2$s]: %3$s'),
    'info': ('%1$s #%2$s · umur %3$s · %4$s · watak %5$s · kesan %6$s (%7$s)', '%1$s #%2$s · age %3$s · %4$s · temperament %5$s · opinion %6$s (%7$s)'),
    'info_kabar': ('kabar didengar %1$s · permintaan %2$s · petunjuk: %3$s', 'rumours %1$s · requests %2$s · hint: %3$s'),
    'info_jalan': ('sasaran jalan: %s', 'direction targets: %s'),
    'tolong_baris': ('#%1$s %2$s · %3$s · status %4$s · langkah %5$s · batas hari %6$s · tempat %7$s',
                     '#%1$s %2$s · %3$s · state %4$s · step %5$s · deadline day %6$s · place %7$s'),
    'tolong_dibuat': ('Permintaan #%1$s (%2$s) dibuat untuk %3$s.', 'Request #%1$s (%2$s) made for %3$s.'),
    'tolong_gagal': ('Permintaan %s tidak bisa dibuat sekarang.', 'Request %s cannot be made now.'),
    'tolong_selesai': ('%s permintaan diselesaikan.', '%s requests completed.'),
})
fam(P + 'tokoh.', {
    'laporan': ('Tokoh bernama: %1$s terdaftar, %2$s sudah menjadi warga; ibu kota pertama: %3$s', 'Named characters: %1$s registered, %2$s cast as residents; first capital: %3$s'),
    'baris': ('%1$s -> warga #%2$s di tempat %3$s · umur %4$s · slot %5$s · %6$s', '%1$s -> resident #%2$s in place %3$s · age %4$s · slot %5$s · %6$s'),
    'belum': ('%1$s: belum dipilih (tempat: %2$s)', '%1$s: not cast yet (place: %2$s)'),
    'wafat': ('%1$s: sudah wafat (warga #%2$s)', '%1$s: has died (resident #%2$s)'),
    'hewan': ('%s: hewan, tidak dipilih dari warga', '%s: an animal, not cast from residents'),
})
fam(P + 'cerita.', {
    'harga': ('Peristiwa kota', 'Town event'),
    'calon_muncul': ('Seorang anak berakar roh, %s, terlihat di sekitar Kantor Rekrutmen.', 'A child with a spirit root, %s, was seen near the Recruitment Office.'),
    'nama_jalan': ('Sebuah jalan di %1$s kini dinamai menurut namamu: Jalan %2$s.', 'A street in %1$s is now named after you: %2$s Street.'),
    'resep': ('Kau mempelajari resep %s.', 'You learned the recipe for %s.'),
    'jabatan': ('Kau kini menjabat sebagai %1$s di %2$s.', 'You now hold the office of %1$s in %2$s.'),
})


# ------------------------------------------------------------------------------------------------ checks and writing
def fmt_count(s):
    return sorted(re.findall(r'%(?:\d+\$)?s', s))


def check():
    err = []
    for key, (idn, en) in L.items():
        if fmt_count(idn) != fmt_count(en):
            err.append(f'{key}: format arguments differ')
        for txt in (idn, en):
            if not txt.strip():
                err.append(f'{key}: empty')
            if re.search(r'%(?![\ds%])', txt.replace('%%', '').replace('%s', '')):
                err.append(f'{key}: stray %')
    for fn in sorted(glob.glob(os.path.join(LANG, '*.id_id.json'))):
        if os.path.basename(fn).startswith(FRAG + '.'):
            continue
        other = json.load(open(fn, encoding='utf-8'))
        dup = set(L) & set(other)
        if dup:
            err.append(f'{os.path.basename(fn)} already has {sorted(dup)[:5]}')
    # every static key of the code is here; every dynamic family has at least one variant
    keys_code, prefixes = set(), set()
    for src in glob.glob(os.path.join(REPO, 'src/main/java/dev/kultivator/dunia/kota42/bicara/*.java')):
        text = open(src, encoding='utf-8').read()
        for m in re.finditer(r'"(kultivator\.kota42\.bicara\.[a-z0-9_.]*)"', text):
            (prefixes if m.group(1).endswith('.') else keys_code).add(m.group(1))
    for key in sorted(keys_code):
        if key not in L:
            err.append(f'code key missing: {key}')
    for pre in sorted(prefixes):
        if not any(key.startswith(pre) for key in L):
            err.append(f'code family missing: {pre}*')
    return err


def main():
    err = check()
    if err:
        for e in err:
            print('ERROR', e)
        raise SystemExit(f'{len(err)} errors; nothing written')
    for suffix, idx in (('id_id', 0), ('en_us', 1)):
        out = {key: val[idx] for key, val in sorted(L.items())}
        with open(os.path.join(LANG, f'{FRAG}.{suffix}.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(f'{len(L)} keys written to {FRAG}')


if __name__ == '__main__':
    main()

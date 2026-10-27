# Checklist uji Fase 42 — Kota yang Hidup (final F-END)

Ditulis F42-2, dirapikan F-END setelah gelombang detail D-41-42 (build B3 hijau). Sumber: DESIGN 42.17 (`docs/rancangan/42-kota-hidup.md`).
Belum ada yang diuji di game. Perintah uji di bawah
`/kultivasi uji kota_hidup ...` (op 2; sub: warga, ragam, sebaran, cakap, bicara, tolong, tokoh, gosip, jalan, ekonomi, acara, hewan, tumbuh,
sekte, kompat, anggaran); perintah pemain `/kultivasi kotaku`. **Selalu dunia baru.** Config bawaan: seluruh bagian
`kota*` aktif. Pada tiap uji periksa: tidak ada error di log, tidak ada kunci bahasa mentah (id_id dan en_us), tidak ada
blok berubah saat runtime selain lewat WorldBreaker/BuildPlacer, tidak ada petir/cuaca/waktu/tick-rate yang berubah.

## Sudah terbukti di luar game (F42-0, F42-1, F42-2)
- [x] Build B2 hijau (compileJava, compileClientJava, jar); mergeLang tanpa kunci ganda
- [x] Generator: cerita_isi_kota42 (40 tokoh, 106 dialog, 18 quest, 40 kabar, 12 kisah, 16 tolong, 24 perkara, 4 wayang,
  1819 kunci), gen_kota_hidup_ui (112 ikon), gen_kota_hidup_hewan (8 mesh, 417 kotak, 27 tekstur), gen_kota_hidup_suara
  (65 klip sintetis, 11 melodi), gen_kota_hidup_fx, gen_kompat_bioma (53 tag)
- [x] cek_tempat 0 galat (105 set), cek_sosok 0/0, cek_cakap OK, cek_kompat_mod 0
- [x] Harness: run_38 (TplCheck 8 folder 0 masalah, WgCheck38 157 struktur / 105 set ALL OK, PLAN OK, TEMA PLAN OK), run_all
  (1779 berkas 0 masalah), run_fin (2 istana pada 4 seed), run_kompat (KOMPAT OK)

## Sudah terbukti di luar game (F-END)
- [x] Build B3 hijau; mergeLang tanpa kunci ganda; generator ulang tanpa perubahan berkas (cakap + golden 49/49 OK, kota, aset, kompat_bioma 53 tag)
- [x] 105 klip suara Jepang `kota42.*` dibuat dan terdaftar (`VoiceEvents.KOTA42_SIAP = true`); 60 entri suara sintetis digabung ke `sounds.json`
- [x] Disambungkan: `TujuanKota.perkara`/`kejarCopet`, `Investasi.catatKeran` (bunga pinjaman, upeti), `kelompokPeluang`, `Titik.alat()`, `KerjaPeran` 8 peran baru

## Celah yang diketahui (untuk penguji dan gelombang berikut)
- Belum tersambung: Upacara menyewa pemusik Suona (`Rombongan.pemusikSewaan`), `LembarUpacara.daftarAksi(AksiRombongan)`, cap lambang asli di
  `KalenderKota.suasana`, narasi wayang di chat, `Kebiasaan.faktorTawar` (tawar-menawar toko pemain), `Dagang.catatLaba`/`RumahUang` untuk ke_bank
  (`TokoPemain.keBank` pada `LembarToko`), batas mingguan `LembarBuku` memakai `TokoPemain.batasHarian`.
- Dialog 42 belum dibuka pada 34 peran dengan layar KotaNet sendiri (`NpcRole.tab >= 0`); perlu `Kota.bukaLama(player, npc)` + baris Jasa.
- Tampilan musim dingin `<peran>.dingin` belum dipasang server (MusimKota); klip 443 'tiru' dan bangun unta butuh dukungan pengendali.
- Klien: tanda "Dibangun dengan bantuan", prasasti Tugu Kenangan, asap blokade, teks papan Kavling Kota belum ada; kafilah dan pelancong belum
  berhenti saat `Blokade.aktif`; tenda pengungsi belum terikat tepat ke kasur; kavling hanya maju saat ada pemain dekat.
- 13 peran baru: PENGAMEN, AKROBAT, PENARI, PEMUSIK, PENCOPET adalah badan peristiwa (tanpa `KerjaPeran`); 8 peran lain kini punya pekerjaan.
- Interior ibu kota lama dan kota bertema tetap interior 38 (titik dan pajangan saja). Lembar milikku, rumah, toko_pemain, lapak_sewa dan
  investasi belum punya sub Diri > Harta dan terbuka di Di Sini.
- Kampung dan kota-34 belum disimulasikan; lapisan peta panas 42.4A.8 belum ada. Populasi 200 hari ramai (3-6 lahir, 2-4 nikah, 2-4 wafat per hari
  di ibu kota): periksa apakah terlalu sibuk untuk Upacara.
- Tidak ada penjalan `giling=1` untuk keledai penggiling; barongsai: jangan munculkan badan penari pada klip 378/379.
- Baris sela opsional di tengah kerangka percakapan bisa meninggalkan spasi ganda di gelembung ucapan (perlu normalizer di tampilan).

**Uji terima (harus terlihat oleh penguji):**
1. Dunia baru, ibu kota: `uji kota_hidup warga daftar` → 650 warga bernama dengan rumah tangga bermarga sama, umur sesuai peran,
   2-4 hubungan; papan nama tubuh = nama warga; anak berskala anak, tetua berpostur tua.
2. Klik kanan **WARGA kota 34, PENJAGA, petani kampung, kuli**: semua membuka dialog warga dengan baris yang cocok jam, cuaca
   (uji hujan alami atau `bicara uji`), suasana; peran lama 34 tetap membuka lapak lamanya lewat tablet Jasa.
3. Perkenalkan diri → besok warga itu menyapa dengan nama; selamatkan dari api → baris kenangan; beri teh ke pecinta teh (+8) dan
   ke pemabuk (−5).
4. `warga tahun 20` di kampung: pernikahan terjadwal (arak-arakan tandu, tiga sembah, jamuan), kelahiran (telur merah, pesta
   sebulan, *zhuazhou* dengan bayi merangkak memilih benda), wafat (rumah duka putih, usungan 8 pemikul, nisan bernama di makam);
   Pohon Keluarga 3 generasi benar.
5. `warga tahun 40`: anak yang dikenal pemain dewasa, mengambil slot kerja orang tua atau magang; cucu mewarisi ½ kesan
   (`kota42_cucu`).
6. Rumah sesak → pasangan muda pindah ke kota lain: tubuh pelancong dengan buntelan di jalan; di kota tujuan mereka mengenali
   pemain.
7. Gosip: tolong warga di pasar → 2 hari kemudian Tiga Bibi Sumur membicarakannya; `[Tanya siapa yang bilang]` menelusuri rantai;
   satu lompatan "dibumbui".
8. Tanya Jalan: menunjuk (klip `tunjuk`) ke Rumah Uang dengan arah dan patokan; `[Tandai]` memberi panah kompas; anak menjawab
   "tanya Bibi Wang".
9. Kebiasaan: pemabuk di kedai arak malam lalu tidur di bangku; dua kakek main catur dengan penonton; ibu-ibu di sumur; kakek
   menggantung sangkar; tai chi pagi berbaris tersinkron.
10. Suasana: festival → wajah senang; Gelombang Binatang → takut dan masuk rumah; pemakaman → keluarga sedih berbaju putih.
11. Rumah: beli rumah sedang (harga sesuai faktor), taruh dan bongkar blok di dalamnya **tanpa** tagihan dan tanpa perbaikan 38.R;
    tukang tidak masuk; kunci rumah teleport dengan kanal 3 s; sewa habis → segel 14 hari → isi wadah ke Barang Sitaan, furnitur
    pemain tetap (cetak biru menerima).
12. Toko: rak dagang menampilkan barang dan harga; pegawai warga di kasir; pembeli bertubuh datang, melihat rak, membayar,
    pergi membawa barang; batas harian dan pajak 5 % di laporan.
13. Harga: jual 64 barang sejenis → harga turun (banjir pasar); serangan → senjata/obat naik; gugur → pangan turun; tooltip sebab
    benar di semua toko 34/38/42; beli-kembali ≤ 0,5 harga jual.
14. Investasi: tanam di bakmi, `ekonomi investasi minggu 4` → laba bervariasi dalam batas; pemilik wafat → ahli waris lanjut atau
    saham dibayar.
15. Gudang + kontrak: kafilah gerobak terlihat berangkat dari gudang, tiba, bayaran masuk, Nama Dagang naik; laba ikut batas Dagang.
16. Jabatan: naik ke Hakim Pembantu, sidang perkara dengan saksi dan bukti gosip; suap → Infami +2 dan audit; Wali Kota mengubah
    pajak → suasana dan Kas berubah.
17. Pasar Hantu: sandi dari kedai teh menunjuk lokasi yang benar malam itu (01:00-05:00), lapak bertopeng, barang curian pemain
    bisa ditebus, razia membuat lapak lenyap.
18. Rombongan: akrobat (piramid 3 tubuh), barongsai, Lao Shuo bercerita dengan balok kayu, Ah Qin bermain erhu (melodi
    terdengar), penjual obat; rombongan berpindah ke kota lain sesuai gosip.
19. Pagi: ayam berkokok, pasar pagi dengan tenda, anak berbaris di belakang guru dan menghafal (suara); ronda malam memukul gong
    sesuai geng dan berseru (suara), pemain ikut satu geng.
20. Peristiwa: dicopet di kerumunan → kejar 30 s → tangkap; perkelahian dilerai; gerobak terguling diangkat; api kecil menjalar
    lalu dipadamkan rantai ember — **tidak ada blok berubah** (cek `uji tempat cetak periksa` 0 perbedaan).
21. Hewan: kucing tidur di atap, anjing menggonggong malam, ayam kabur, merpati terbang serempak, kuda di tambat, unta di oasis;
    `uji kota_hidup hewan semua` menampilkan semua model dengan animasi.
22. Musim (`acara musim … hari_raya`): Tahun Baru (kuplet, 福 terbalik, petasan, angpao anak), Qingming (sapu makam), Pertengahan
    Musim Gugur (menatap bulan), Laba (bubur dibagikan), warung sup musim dingin.
23. Kantor Rekrutmen: pelamar dari warga berakar, uji akar dengan batu bercahaya, uang restu, adegan pamit memeluk orang tua; calon
    masuk daftar calon aula dengan nama dan akar yang sama.
24. Pakta: rebut dari sekte NPC lewat tanding utusan; panji lambang sekte pemain di gerbang; Panggilan Bela Kota dengan gelombang
    nyata; murid `JAGA_KOTA` berdiri di gerbang dan ikut ronda; cabang Toko Pil dengan rak/kasir aktif.
25. Kota tumbuh: `tumbuh makmur 70` + `bahan isi` → kavling dibangun 5 tahap oleh regu tukang dengan perancah; blok hanya lewat
    `WorldBreaker.submitRestore` (log alasan "tumbuh"); blok pemain di kavling tidak pernah ditimpa; peresmian barongsai; keluarga
    baru masuk; 38.R kemudian merawat bangunan itu.
26. Bangun kembali: kerusakan nyata > 200 blok (pemain membongkar) → proyek, tenda pengungsi, donasi, tukang pendatang, tugu
    kenangan; serangan tanpa kerusakan nyata **tidak** membuat proyek perbaikan.
27. Distrik malam: lentera merah, kedai arak dengan suit jari, rumah judi Guanpu (EV tertulis jujur), teater bayang dengan siluet
    bergerak dan suara.
28. Lentera kota menyala 18:00 bergelombang dari gerbang, asap cerobong pada jam makan, panji berubah saat pernikahan (囍) dan
    pemakaman (奠).
29. Semua layar 42.11 pada skala GUI 2/3/4 tanpa tumpang tindih dan bisa dipakai dengan keyboard saja.
    (D-42U) Jabatan, Perkara, Kantor Rekrutmen, Pakta, Cabang, Upacara, Rombongan dan Kavling kini halaman Gulungan sendiri
    (`client/dunia/kota42/ui/layar/Layar*`, kit tunggal, teks 1,0): pada kotak sempit (< 320) isinya satu kolom yang bergulir
    (Atas/Bawah/PgUp/PgDn/Home/End, roda), Tab melewati setiap baris dan tombol, kolom amplop/tip/Beri tetap terlihat di bawah.
    Uji: buka tiap lembar lewat dialog (baris Jasa) atau `/kultivasi kotaku ...` pada skala 2, 3, 4 dan dengan hanya keyboard.
30. `uji kota_hidup anggaran`: rata-rata < 0,5 ms/tick semua 42; entitas 42 ≤ 96; Hari Warga ibu kota < 1 ms.
31. Semua sakelar `aktif = false`: perilaku berhenti, blok/item/entitas tetap terdaftar, perilaku 38 kembali persis.
32. **Mesin percakapan**: dua warga berwatak berbeda (penakut vs sombong) menjawab topik sama dengan register, kata dan gestur
    berbeda; `cakap uji SAPA 200` menghasilkan ≥ 150 kalimat berbeda tanpa pengulangan berturut; semua teks berasal dari kunci
    fragmen lang (tidak ada teks buatan model bahasa).
33. Emosi: hina warga → wajah marah dan nada ketus, meluruh dalam ±2 jam dunia; hadiah → senang; serangan → takut.
34. Pengetahuan: warga saksi copet menceritakannya; warga yang hanya mendengar menyebut sumbernya ("kata Bibi Zhang"); fakta yang
    tidak diulang terlupa setelah beberapa hari (`cakap info`).
35. Kilas balik: janji `[Aku akan membawa obat]` → hari berikut warga menagih dengan ungkapan waktu yang benar ("kemarin"); menepati →
    terima kasih + kesan; ingkar → kecewa + gosip.
36. Nada: sopan/akrab/merayu/mengancam/menyuap/bercanda masing-masing berhasil dan gagal sesuai sifat; ancaman yang disaksikan di
    ibu kota menambah poin kejahatan; suap ke warga ber-H tinggi ditolak dan digosipkan.
37. Obrolan NPC-NPC: gelembung bergantian dengan gestur; gosip benar-benar berpindah (`cakap info` pendengar), debat menurunkan
    hubungan, merayu menaikkan; menguping memberi catatan kabar; menyela obrolan memulai sesi dengan topiknya.
38. Dialek: warga Kota Pelabuhan memakai sapaan pelaut, Kota Gunung "Dermawan"; register kuno saat pemain ≥ 3 ranah di atas.
39. Uji emas `cakap golden`: ≥ 40 berkas lulus; mengulang dengan benih sama menghasilkan keluaran identik.
40. **Sebaran**: `sebaran peta` di ibu kota jam 09, 12, 20, 23 menunjukkan pasar/rumah makan/kedai/rumah sebagai pusat sesuai jam
    sementara distrik lain tetap berpenghuni; `ragam laporan 7` memenuhi semua sasaran 42.4A.8 (≤ 35 % tubuh di satu zona, Gini
    ≤ 0,35, zona kosong ≤ 10 %).
41. Pemain berdiri di satu sudut kota: tubuh tersebar ke semua zona yang terlihat (tidak menumpuk di dekat pemain); tidak ada dua
    tubuh tak berinteraksi berjarak < 1,5 blok.
42. **Ragam**: satu warga diikuti 3 hari (`ragam <warga>`): ≥ 5 kegiatan berbeda per hari, rencana hari ke-2 berbeda dari hari ke-1,
    selingan (seka keringat, menoleh, melirik pemain) terlihat, satu belokan (jajan/elus kucing/tonton pengamen), keluarga jalan sore.
43. **Kompat**: dunia baru dengan paket mod pengguna: `kompat periksa` kosong di 3 ibu kota, 3 kota tema, 5 kota 34, 10 kampung;
    kavling dekat struktur asing ditandai `TIDAK_LAYAK` dan tidak dibangun.

**Daftar centang (untuk FASE-42.md):**
- [ ] Warga tersimpan: identitas, nama generasi, akar, watak, kebiasaan; slot `PendudukGen` + slot tambahan
- [ ] Siklus hidup: menikah, lahir, anak angkat, sakit, wafat, pensiun, warisan; arsip dan nisan
- [ ] Hubungan 6 jenis + perilaku terlihat; keluarga terlihat (gandeng cucu, makan bersama)
- [ ] Pekerjaan, magang, ganti kerja; pindah antar-kota dengan tubuh pelancong
- [ ] Anak berbakat → Kantor Rekrutmen / HariGerbang / AnakBerbakat dari satu sumber
- [ ] Ingatan per pemain + kesan + kenangan kuat + warisan kesan
- [ ] Suasana 7 kode wajah; akibat di harga, dialog, pindah
- [ ] 32 kebiasaan sebagai penimpa jadwal data
- [ ] Dialog untuk semua orang sipil; Tanya Jalan; Kabar; Tolong 16 jenis; Beri dan pinjaman
- [ ] Mesin percakapan tanpa LLM: HEXACO, PAD + penilaian, fakta + kurva lupa + keyakinan, topik utilitas, FSM + kilas balik + janji,
      6 nada, tata bahasa bertipe (register, dialek, tik, anti-ulang) ≥ 900 bingkai / ≥ 2.400 terminal, 8 jenis obrolan NPC-NPC
      yang mengubah keadaan, uji emas ≥ 40, `cek_cakap.py` bersih
- [ ] Sebaran: zona + kapasitas + target per jam, Penyeimbang + reservasi + luapan, pemilih tubuh proporsional, metrik lulus
- [ ] Ragam: ≥ 10 kegiatan per peran umum, rencana harian, selingan, belokan, kelompok, irama mingguan/musim
- [ ] Kompat mod dunia 42.12.1A: tanpa struktur/placement baru, keep-out dihormati, periksa struktur asing sebelum tiap tahap
- [ ] Gosip lahir dari peristiwa nyata, menyebar lewat hubungan dan titik kumpul, dibumbui, ditelusuri
- [ ] Rumah klaim pemain (tanpa perbaikan/tagihan di dalam), kunci rumah, segel, cetak biru menerima
- [ ] Toko pemain: blok rak/kasir/papan, pegawai, pembeli bertubuh, batas keran
- [ ] `HargaKota` dipakai semua penjual; 12 kategori; lumbung penstabil
- [ ] Investasi, gudang, kontrak (batas Dagang bersama), pajak, 5 jabatan, perkara, suap/audit
- [ ] Pasar Hantu berpindah tiap malam; barang curian; razia
- [ ] 6 rombongan; rutinitas pagi; ronda 5 geng; 6 peristiwa acak tanpa blok; 7 hewan; 4 musim + hari raya; 3 upacara
- [ ] Kantor Rekrutmen, pakta + Panggilan Bela Kota, murid JAGA/KERJA_KOTA, cabang 3 jenis
- [ ] 24 kit interior, titik baru, kavling, makam, distrik malam; TplCheck/cek_tempat bersih; harness hijau
- [ ] Kavling 5 tahap lewat `WorldBreaker.submitRestore` saja; tidak menimpa blok pemain; `Cetak.terima` saat selesai
- [ ] Bangun kembali hanya untuk kerusakan nyata; pengungsi; tugu kenangan klien
- [ ] Lentera, asap, panji, petasan (tanpa `FireworkRocketEntity`), jelaga (K7), melodi prosedural, wayang
- [ ] 17 entitas, 4 blok, ±20 item, 13 peran, klip 350-449, prop 170-239, busana/rupa baru
- [ ] 105 klip suara Jepang (81 baris + 24 kata seru), bab Kitab Panduan 23, 37 advancement, fragmen lang identik
- [ ] Sibuk di semua peristiwa; tidak ada blok runtime selain `WorldBreaker`; tidak ada LightningBolt/cuaca/waktu/tick-rate

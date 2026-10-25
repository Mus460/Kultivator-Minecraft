# Checklist uji Fase 41 — Gabung Sekte (draf F41-1)

Ditulis F41-1 setelah gelombang 41-G1 (build B1 hijau). Sumber: DESIGN 41.16 (`docs/rancangan/41-gabung-sekte.md`).
Belum ada yang diuji di game; F-END merapikan setelah gelombang detail D-41-42. Perintah uji di bawah
`/kultivasi uji gabung ...` (op 2); perintah pemain di bawah `/kultivasi gabung ...`. **Selalu dunia baru.** Config bawaan:
`gabungSekte.aktif` true, `sebaran` seimbang, `tubuhMaks` 24. Panduan: bab 22. Bantuan: topik "Gabung Sekte", "Kelas Sekte",
"Corak Sekte".

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (id_id dan en_us), tidak ada blok berubah saat
runtime selain lewat WorldBreaker/BuildPlacer, tidak ada tokoh yang mati, tidak ada barang atau PK ganda.

## Sudah terbukti di luar game (F41-1)
- [x] `seimbang`: sekte t8/t7 == `LangitNpcPlan.of(seed)` pada 6 seed (harness SebaranCek)
- [x] TplCheck `kompleks_sekte` (50) dan `sekte` (3) bersih; cek_tempat 0 galat; jumlah structure set tetap 105
- [x] 29 advancement diberikan dari kode; semua sub `uji gabung` terdaftar

## Celah yang diketahui di B1 (untuk gelombang detail)
- Ujian Murid Dalam dan Ujian Tetua belum ada pelaksananya: kenaikan ke Dalam/Tetua tertahan di baris "ujian". Uji Ulang
  (Luar) dan syarat Inti (turnamen 3 besar / misi A) sudah tersambung.
- Dialog aturan `gabung_aturan_<aliran>` belum dibuka dari mana pun (sumpah membacakan 3 aturan lewat chat).
- Penanda peta `gabung/sekteku` belum diterbitkan (perlu penanda per pemain).

## Uji terima
- [ ] 1. Dunia baru, `uji gabung daftar`: 8 sekte dengan kelas `seimbang` (satu t8, satu t7); `uji gabung sebaran` membuktikan dua
  teratas = dua sekte berIstana Awan (`uji langit_sekte npc daftar`).
- [ ] 2. Kedai Teh memberi kabar penerimaan; Direktori menampilkan sekte dengan "?" lalu lengkap setelah kunjungan; Hari Gerbang ibu
  kota: perekrut punya tablet Jasa `[Lamar]`.
- [ ] 3. Lamaran dengan centang syarat benar (uji dengan Usia Tulang lewat → tombol Token muncul); status Calon + Tanda Calon.
- [ ] 4. Hari Ujian: penjaga berseru (suara), calon NPC anak-anak di tangga, tangga ilusi digambar, Tekanan Gunung naik saat diam dan
  turun saat jongkok; jatuh terduduk di 100.
- [ ] 5. Setiap corak (`uji gabung ujian <s> tahap 2 corak <id>` ×10): boneka menyala, biksu perunggu (membunuh sapi = gagal), binatang
  ditaklukkan tanpa mati, labirin tak terlihat dengan kabut dan trigram, pilar Qi jarum, kuali pil, batu pikul lambat, kolam
  darah menguras HP + Infami +10, cawan racun + penawar, ujian senyap dengan kerucut pandang penjaga.
- [ ] 6. Wawancara memakai layar dialog 38 dengan 3 soal aliran; putusan stempel; Calon Istimewa → 2-3 tetua berebut menawar.
- [ ] 7. Upacara sumpah: berlutut, dupa, kowtow, tetua membacakan 3 aturan bersuara, lencana dan jubah; jubah terlihat di atas zirah
  dengan warna lambang dan tepi pangkat; pemain lain melihatnya.
- [ ] 8. Pelajaran Shifu mingguan menaikkan Penguasaan Jurus; Shifu melindungi saat HP < 20 % di kompleks.
- [ ] 9. Tugas pikul air (klip + ember), sapu, kebun, kipas tungku memberi PK; Papan Misi berisi jenis 38 + jenis gabung; PK naik,
  **tidak** ada jalan menukar PK ↔ NB.
- [ ] 10. Paviliun Jasa: harga = harga Contribution/TechniqueGrade × pangkat × corak; terkunci menurut kelas dan pangkat; barang
  berkomponen `asal_sekte` ditolak toko kota 3 hari.
- [ ] 11. Gua Kecil: `CaveAbode` diset (Jimat Pulang pulang ke sana), faktor meditasi ×1.20 tampil di layar kultivasi; sewa lewat →
  pindah ke Bilik.
- [ ] 12. Promosi Luar → Dalam → Inti dengan upacara, kilat jubah, lonceng; kursi penuh → tantang pemegang kursi (duel).
- [ ] 13. Turnamen Murid: bracket dengan roster 39F, Duel Pedang dengan penonton; juara mendapat gelar.
- [ ] 14. `uji gabung perang <lawan>`: Panggilan Perang (suara), misi Bela Gerbang dengan regu ber-lambang lawan, skor naik; akhir
  perang mengubah tier (`kabar.gabung.naik_kelas`), visual kelas bertambah panji.
- [ ] 15. Serbuan kompleks live: pertanda, gelombang, tidak ada blok berubah, tidak ada tokoh mati.
- [ ] 16. Pelanggaran A1 (pukul murid NPC) disaksikan → teguran; ≥ 35 → Gua Renungan dengan pintu terkunci; kabur 3× = Membelot.
- [ ] 17. Pamit dengan Restu (surat) → Restu Mendirikan → aula baru dengan standing +40; Diusir → lencana disita, adegan; Membelot →
  poster buronan, regu pemburu dengan dialog Menyerah/Lawan/Lari, kalah = ditangkap (tidak mati), Warisan dihapus.
- [ ] 18. Kembali bergabung: PAMIT → pangkat −1, PK beku kembali; PEMBELOT ditolak sampai suksesi.
- [ ] 19. Anggota menaruh prasasti → aula dorman + dialog R2; Inti + Restu Gunung → Cabang (nama "· Cabang", upeti di Buku Kas 38).
- [ ] 20. Tetua Tamu: pemilik aula level 3 lulus Ujian Tamu; menyatakan perang ke sekte itu = Lepas Tamu.
- [ ] 21. Suksesi: Pemilihan Pewaris (duel + suara), Jubah dan Mangkuk bab 1-3, Ketua baru; layar Politik berubah; Serap → kompleks
  berganti lambang aula pemain, pool aula bertambah harian.
- [ ] 22. Dua pemain di satu sekte: Papan Jasa, misi kelompok, rebut kursi lewat duel lei tai, PvP di luar lei tai = pelanggaran.
- [ ] 23. Semua layar 41.10 pada skala GUI 2/3/4 tanpa tumpang tindih, bisa dengan keyboard saja.
- [ ] 24. Anggaran: `uji gabung anggaran` < 0.3 ms per sekte per tugas harian, entitas 41 ≤ 24; ujian ditunda saat pemain
  tribulasi (Sibuk).
- [ ] 25. `gabungSekte.aktif = false`: semua perilaku berhenti, item/entitas tetap terdaftar, aula pemain tetap normal.

## Daftar centang
- [ ] Kelas 1-5 dari tier; sebaran lima preset; `tingkatMaks` membatasi tumbuh
- [ ] Corak dari kata A konsisten dengan nama; Teknik Warisan = spec nyata
- [ ] Syarat masuk per kelas (ranah, akar, Usia Tulang, standing, Infami, kejahatan) di satu `SyaratMasuk`
- [ ] Lima pintu masuk; Token dari reruntuhan/lelang/quest/sahabat
- [ ] Tiga tahap ujian, sepuluh ujian corak, wawancara aliran, putusan
- [ ] Upacara sumpah, lencana, jubah, bekal; Shifu dipilih, ikatan, pelajaran, perlindungan, melampaui
- [ ] Pangkat 0-8 + Tamu, kursi, tantangan, promosi, penurunan
- [ ] PK pribadi dari semua sumber; tidak ada tukar NB/batu
- [ ] Papan misi bersama, tugas harian, Paviliun Jasa, kediaman, kolam, jatah, kuota alam, turnamen murid
- [ ] Perang NPC dengan misi, serbuan kompleks, naik/turun kelas, Merosot/Bangkit, peringkat
- [ ] Aturan per aliran, Catatan Pelanggaran, denda, Gua Renungan, sidang
- [ ] Pamit/Restu, Pamit tanpa izin, Diusir, Membelot + pemburu, aturan teknik dan murid, kembali bergabung
- [ ] Veto pendirian, Cabang, Tetua Tamu, veto perang, suksesi, serap/lebur, murid titipan
- [ ] Multipemain: kursi, Papan Jasa, kelompok, PvP lei tai, suara politik, sinkron jubah
- [ ] Worldgen: lot `paviliun_jasa`, titik baru di kompleks dan gerbang, TplCheck/cek_tempat bersih, harness hijau
- [ ] Klip 300-319, prop 150-156, rupa per corak, 26 suara, bab panduan 22, 29 advancement
- [ ] Sibuk di semua peristiwa; tidak ada blok runtime; tidak ada LightningBolt/cuaca/waktu

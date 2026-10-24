# mega-38KS (wave W1, capital Kota Besar) — checkpoint

Scope: jadwal-38-39-40.md W1 row 38KS + sec 5 note; design 38.B (minus 38.B.7 NPC content), 38.B.4-6, 38.T.14.3 row KS.
Owns: `dunia/tempat/kotabesar/**`, `client/dunia/tempat/kotabesar/**`, `dunia/kota/Towns.java`,
`tools/gen_tempat_kota_besar.py`, `structure/kota_besar/**`, `worldgen/**/kota_besar/**`, `resourcepacks/dunia_kotabesar`,
`lang-src/tempat_kota_besar.*`, advancements `tempat/{ibu_kota,warga_terhormat,pahlawan_kota,buronan,alkemis_juara,
pertahanan,teleport}`. No Gradle. New worlds only. STATUS: DONE (session 4).

## Checklist
- [x] Study: tempat_lib.py API, nbt/structgen, Towns, KotaData, KotaAnchors, DuniaAnchors, LembarKunci
- [x] tools/gen_tempat_kota_besar.py (+ tools/kota_besar_kit.py detail kit, Kanvas 144x144 cut into lots by potong()):
      [x] framework, JSON writers (pools+gaya pools, processors lapuk/utuh/gaya_*, 11 structures, sets in pack, tags)
      [x] walls: tembok/lurus, lurus_jaga, menara, sudut, gerbang_agung, gerbang_samping (margin 2, band 8, rotations)
      [x] pusat (alun_agung w/ kota/situs+alun+zona anchors, genderang, lonceng, penerimaan, yamen+6 cells, taman, pos)
      [x] istana [x] cendekia [x] awan (shared builders p3: siheyuan, rumah_toko, rumah_susun; K.tempel rotates)
      [x] gilda (p5) [x] pasar (p6, fix_p6 applied; generated 13:08)
      [x] hunian_barat (p7: pool kota_besar/hunian/rumah a-f whole-lot variants via lot_sendiri, bengkel, alun_hunian,
          rumah_makan_kecil; scratchpad ks/splice.py p7.py p8.py ... rebuilds the region before the JSON marker)
      [x] gerbang (p8.py + p8b.py: alun_gerbang, 2 inns, teratai, kedai_teh_agung, jalan_lentera x2); 2.08 MB total
      [x] arena (p9: arena_agung a-d 90x90 tribunes/160 penonton/lei tai/juri/ruang juara, wisma_delegasi,
          lapangan_latihan, 3 pool lots); 2.23 MB
      [x] kumuh (p10: gubuk/gubuk_blok shack rows, km_road, kuil_tikus, pasar_loak+tukang, sarang_tikus pawnshop over the
          Pasar Gelap cellar y0..3 (kota/gelap, kota/gerbang_gelap), warung_jalan, dapur_umum); 2.49 MB
      [x] luar (p11: gerbang_luar, tugu_jarak+zona, paviliun_perpisahan, kandang_kuda 4 horses, pos_kafilah, perkemahan,
          kios_jalan+tukang, ladang, kebun_dewa_jalan)
      Parts are written in scratchpad ks/ (p0_head, p0_wall, p1.., p_main) and cat'ed into the repo file; if scratchpad
      is gone, edit the repo file directly (district builders go before the "# ===== JSON" marker, add to BUILDERS).
      Preview: scratchpad ks/preview_kb.py <out> [distrik] -> top/oblique PNGs (Read them).
      Size so far: 4 districts + walls = 0.99 MB gzip (budget 3 MB; ~215 KB per district).
- [x] run generator (102 templates, 97 pools, 2.60 MB gzip) + cek_tempat.py 0 errors; TplCheck kota_besar files=102 problems=0
- [x] Towns.java: kota/kota_besar + kota/kota_tema (planned(), situsRencana, jenis, safeRadius, within/inTown by kind)
- [x] KotaBesarConfig (+terapkan -> RencanaKotaBesar.setAturan/setJarak)
- [x] KotaBesar.init wiring (pack, config, anchors handler, payload, services, calendar, commands, phased tick, adv cache)
- [x] KotaBesarNames, KotaBesarKota (plan geometry, lokal/dunia frame), KotaBesarData (SavedData kultivator:kota_besar)
- [x] KotaBesarAnchors (KotaHidup per town, gates made on load, layanan bodies via HidupApi.tubuh, daftar() forwarders,
      LapakSekte/LelangAgung forwarded, titikSel/dari(anchor,putar,...) rotate canonical offsets)
- [x] GerbangKotaEntity (synced buka/lebar/tinggi/roboh/retak, oriented thin box, ketuk, pukul for beast tide;
      also reused as cell door lebar 1 tinggi 2)
- [x] JamMalam (curfew 22000/1000, drum beats, bell, knock 2 NB/free Terhormat, wall-crossing crime) + KotaBesarPayload
      (needs Hukum.kejahatan/buronan/LOMPAT_TEMBOK/POIN_LOMPAT_TEMBOK, GelombangBinatang.tutup/serbu/gerbangRoboh)
- [x] Hukum (crime hooks AFTER_DAMAGE/AFTER_DEATH/UseBlock peti_milik/TEMPAT_RUSAK, palace-at-night, wild duel, shadow/
      halt/hunt+KAPTEN, fine, cells with GerbangKotaEntity NAMA_SEL doors, escape, Yamen screen + jasa) + KotaBesarApi real
      + KotaBesarTampil (sheet header, speaker card, wanted posters + hero board payload, arrival pictures)
      (session 4: gen roles fixed MANDOR_TUKANG->TUKANG, PANITIA_TURNAMEN->PENGUJI, PEMILIK_WARUNG->KOKI; regenerate!)
- [x] Istana (audience + Perintah Istana via acara sheet, never-scheduled calendar entry "istana"), Teleport (station +
      Sky Dock + crouch-on-disc + ship routes kota_besar_<i>; kota_tema/* dest), Kuil (site x1.2, bell bow, dupa payload),
      Perpustakaan (lecture), MenaraPengamat (deck anchor kotabesar/pengamat); BalaiTabib = 38EK Jasa (no class)
- [x] KotaBesarKalender (Acara iface, pure jadwal w/ shifts, running state in KotaBesarData "jalan", acara screen +
      jasa, AcaraPertahanan/AcaraLelang adapters, gerhana) [x] GelombangBinatang [x] FestivalLentera [x] PasarMalam
      [x] PertemuanSekte [x] PertemuanPil [x] HariGerbang (+lapak_rekrut screen) [x] TurnamenAgung (+diLapangan, panji)
      (all events live in package kotabesar, not acara/: they need package-private helpers)
- [x] KotaBesarCommands (uji kota_besar cari/distrik/lot/jam_malam/hukum/sel/acara/alur/teleport/istana) + /kultivasi denda
- [x] PetaKota hub screen (peta_kota) + guide jasa (PENJAGA_GERBANG, kantor_pos)
- [x] Self-review compile pass: scratch javac 25 (MC runtime jdk.compiler, cp scratchpad ks/cp_src.txt, -sourcepath,
      out ks/jc) on kotabesar/*.java + Towns: 0 errors (no Gradle)
- [x] Wanted + Hero boards are server-side papan_hidup (38UI PapanHidup.pasang/setBuronan/setLembar), refreshed 600 t
- [x] Client (uses 38MD HiasanDunia groups "kb:<town>:festival|kembang|pasar|panji", "kb:dupa:x:z"; own HUD + qi
      particles + EpicScreen.flash + CloudWipe): KotaBesarClient, KotaBesarFx, KotaBesarHud (scratch javac 0 errors)
- [x] lang tempat_kota_besar 420 keys (generator scratchpad ks/lang_kb.py; identical keys, no dups, all literal keys covered)
- [x] advancements tempat/{ibu_kota,warga_terhormat,pahlawan_kota,buronan,alkemis_juara,pertahanan,teleport}
- [x] Final self-check: no client refs in server code, no setBlock/LightningBolt/weather/time, ItemStacks made on use, all
      new files LF (Towns.java LF in git); regenerated 102 NBT after role fix: TplCheck 102/0, cek_tempat 0 errors

## Java decisions (session 2)
- Capital town id = site id of kota/kota_besar published by pusat's kota/situs = DuniaSites.idOf(OVERWORLD,
  (chunkX*16+8, 0, chunkZ*16+8) of the Kandidat centre, "kota/kota_besar") -> KotaBesarKota.situs(k). Towns.townOf maps
  every anchor of a kultivator:kota_besar/* structure to it (kota_tema/* likewise with kind kota/kota_tema).
- 38EK already serves jasa (tabib/kuil incense/perpustakaan), penginapan, rumah_makan, bank, lelang, judi, toko, gilda via
  CeritaApi.daftarJasa by NpcRole; 38KS only spawns those service bodies from kotabesar/layanan (HidupApi.tubuh) and
  registers its own jasa entries: PENJAGA_FORMASI->teleport, NAKHODA->dermaga, HAKIM/KAPTEN_PENJAGA->yamen,
  MENTERI/PANGERAN_KOTA/PENJAGA_TEKA/PENGUJI->acara, peta_kota hub.
- Gate opening at night: right-click the GerbangKotaEntity (ketuk) -> 2 NB / free for Terhormat / refused when wanted.
- Money: EkonomiApi.bayar/beri (NB), band = Barang.pita(player); owner for diplomacy = player UUID.

## Anchor kinds written by the templates (handlers: KotaBesarAnchors dispatches "kotabesar/*")
kota/situs (situs=kota/kota_besar tier 5 protect 120), kota/alun (128), kota/papan, kota/penjaga, kota/teh,
kota/kursi_pengembara, kota/pil, kota/pusaka, kota/gelap, kota/gerbang_gelap (module K handles all kota/*);
kotabesar/zona (distrik, protect 100; 38W), kotabesar/gerbang (lebar, tinggi, nama), kotabesar/sel (pts: 6 x [in x,y,z,
door x,y,z] rel), kotabesar/genderang, kotabesar/lonceng, kotabesar/panggung (acara=festival|hari_gerbang|pil|lelang|
turnamen; pts), kotabesar/layanan (layanan, peran), kotabesar/cakram, kotabesar/dermaga (kelas), kotabesar/rak_senjata
(kelas; 38EK), kotabesar/lapak_sekte (nomor; 38EK), kotabesar/tukang (38PR).

## Langkah berikutnya
Nothing left for W1. D-KS (W3): live test of events/law in game (F1 build first), tune bodies per lot, board offsets
(Yamen wanted board at denda desk -2x; hero board 4 south of the festival stage), a dedicated palace screen if 38UI adds one.
Scratch compile: java -m jdk.compiler/com.sun.tools.javac.Main -cp $(cat ks/cp_src.txt) -sourcepath "src/main/java;src/client/java".

## Butuh dari modul lain
- 38EK / 38PR / 38W / 38TK: the anchor module "kotabesar" has ONE handler (DuniaAnchors keeps one per module). Register
  per-what handlers through `KotaBesarAnchors.daftar(what, AnchorHandler)` (38KS), never
  `DuniaAnchors.register("kotabesar", ...)` (that would replace the capital's handler).
- 38W: cek_tempat grid check only sees lots whose dx/dz are multiples of 48; capital lots are at -72/-24/24 (district
  cell 144 centred), so the hole check skips them (no error). Optional: accept the -72 grid too.

- 38EK: (a) shops of a capital should refuse while `KotaBesarApi.tokoTutup(server, town)` (lost defence, 1 day);
  (b) inn: when a player sleeps in an inn bed of a capital without a room, call
  `KotaBesarApi.kejahatan(p, town, "tidur_gratis", 2)` (Penginapan.dipesan is package-private); (c) alchemist tier points:
  KotaBesarData player tag `poin_alkemis` (+2/+1 per Pill Gathering) is kept for GildaUjian to read if wanted.
- 38ST: kabar templates used by 38KS (unknown ones fall back safely): kota_besar.{buronan, pertahanan_menang, kota_terluka,
  festival, juara_pil, pertemuan_sekte, hari_gerbang, juara_turnamen, tamu_istana}; service Sumber lines
  `kultivator.kota_besar.<layanan>.inspired` (38.B.3: rumah_uang, judi_batu, gilda_alkemis, gelombang_binatang ...)
  are 38ST's (38KS wrote only kota.inspired, istana.inspired, festival.source).
- 38UI: palace audience/orders reuse the `acara` sheet (teka question + jadwal rows); a dedicated screen would be nicer.
- 38W/F1: GuideChapters references adv:tempat/teleport -> 38KS created advancement tempat/teleport.

## Suara (38ST owns gen_voices): lines needed = 38.B.8 table (30 keys kotabesar.*), use as written.

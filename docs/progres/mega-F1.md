# F1 finish of wave W1 (38/39/40) — checkpoint

Schedule: docs/progres/jadwal-38-39-40.md section 4 (F1). This file is the F1 record (schedule name jadwal-F1.md).

## Checklist
- [x] 1. Read W1 module reports (all 16 DONE; 38DS item D sub-items all [x])
- [x] 2. Wire F1 hand-offs (unowned files):
  - [x] a desa/Pledge.onPlaced -> Tribute.vetoIkrar after the "taken" check (38DS)
  - [x] b tools/gen_tempat_gua.py processor gua/lapuk output_state "Name" -> "id" (regenerated lapuk.json)
  - [x] c dunia/langit/SkyEvents: ECLIPSE_DROP -> LangitItems.eclipseDrop(slayer) (38GU)
  - [x] d client/dunia/tempat/TempatClient.init -> GuaClient.init() (after PerbaikanClient)
  - [x] e client/dunia/kota/TownPages: rumour coins = KedaiTeh.RUMOUR_KINDS (3x3), by = coinY + 3*13 + 2, label +13
  - [x] f client/dunia/peta/PetaScreen.icon: atlas by index (>= DuniaMarkers.ATLAS2_FIRST -> markers_tempat.png)
  - [x] g client/sosok/gerak/GerakPengendali: plays SosokRender.Keadaan.klipKlien/varianKlien when no server clip,
        own start time via bendera/benderaMulai (40R request)
  - [x] h,i already done by 40R (hashMasukan has gaya.luka; HumanNpcRenderer drops "*" headwear via headwearMilikSosok)
  - [x] j KotaDuel.accepts refuses ANAK; role-aware KotaNames.person in Pengembara (2), Saingan (2), PapanMisi (WARGA),
        Istana (envoy.role()), TurnamenAgung (JUARA_SEKTE)
  - [x] k duel/clash/WeaponClass: #kultivator:senjata_gada -> GADA (before the #axes check); DuniaCommands.CHIPS: ekonomi
        simulasi/judi/lapak [hari]/kafilah [permintaan]/kas/kurs/stok ulang/lelang, tempat_gua cari/ujian/penjaga/tikus/
        status (the wrong old chip "gua ujian ..." replaced)
  - [x] l sekte/mandiri/Perencana.cocok: BELAJAR also accepts acts 53 (bag slip) and 56 (listening) in harusBatal and
        milikku (39B blocker; Kegiatan.BELAJAR act stays 13)
  - [x] m sect/event/Tournament.candidates skips child disciples (SosokApi.usia == Sosok.ANAK)
- [x] 3. Generators re-run (jalan, kota_besar, kampung, kompleks, gua, dunia_kota, tempat_models/gear/textures/ui,
      dunia_peta_textures, perancah, ekonomi, sosok_bagian/resep/cadangan/prop/pratinjau): 84 files changed = lapuk.json
      + 83 sosok textures stale on disk; a second sosok run is byte-identical. Warnings only (blockstate-file props,
      kota_besar/tembok/sudut "16 helper blocks outside the piece")
- [x] 4. cek_tempat: 418 tpl, 35 tata_letak, 57 sets, 0 errors; cek_sosok: 0 errors 0 warnings; galeri.png read
- [x] 5. Build B1 green: attempt 1 (pre-wiring) 2m43s, attempt 2 (all wiring) 1m19s; no javac errors; mergeLang no dups
- [x] 6. Harness: run_38.sh TplCheck jalan 32/pos 12/kota_besar 102/kampung 172/kompleks 47/gua 50/situs 3 = 0
      problems; WgCheck38 109 structures, 57 sets, ALL OK; PlanCheck38 PLAN OK (3 seeds, 8 gates, 2 palaces).
      run_all.sh TplCheck 788 files 0 problems; its WgCheck now uses WgCheck38 (WgCheckL does not know tata_letak/jalan,
      which base data now holds) with all packs: ALL OK. langitG run_fin.sh (WgCheck step also switched to WgCheck38):
      TplCheck 7/0, ALL OK, PlanCheckL 2 sky palaces on seeds 0, 12345, -987654321, 42
- [x] 7. Hard rules: block writers only WorldBreaker + BuildPlacer; no LightningBolt spawn / weather / daytime / tick
      rate (only instanceof checks); no client class in src/main; no static ItemStacks in new code; 322 tracked files,
      0 EOL changes; build-log.txt deleted, no javac.*.args

## Status
DONE. B1 green. Nothing committed (orchestrator decides).

## For W2 (next wave must know)
- 38NL-2: PendudukGen.kompleks should use KompleksApi.kompleksSitus(site).entry().band() (40/80/120); PertemuanPil
  names rival alchemists from the rivals' look but bodies keep their own look (sex may differ).
- Tournament/Bracket/Rivals/HundredDynasties (no W2 owner; D-pass or F2): Bracket.Entrant should carry
  sosokData/gayaData and spawn with Disciples.spawnUnowned(..., Sosok.unpack(sosok), gaya, ...) to keep rival faces.
- 38TK/38TV: covering hats via SosokGear.daftarTopi/daftarHeadwear; capital anchors only through KotaBesarAnchors.daftar;
  prop models items/fx/tempat[/tema]/<PropId>.json, anchor (8,8,8).
- 39E: diary texts for 39A/39B keys (kultivator.sekte.mandiri.harian.*), voices in mega-39B.md, guide bab 21
  (c21.tembus/c21.abadi -> sekte/mandiri_tembus|mandiri_abadi), MandiriApi.teksKabar for feed lines.
- 39C: C39.B4 needs list is Tumbuh.butuh(DiscipleEntity|DiscipleData).
- Open for the D-pass (W3): 38EK shops closed while KotaBesarApi.tokoTutup; inn "tidur_gratis" crime
  (Penginapan.dipesan package-private); BukuKas.bukaDiJangkar at the Record Office desk; 38ST rumour templates
  kota_besar.*, per-service Sumber lines, 30 kotabesar voice lines (F2 VOICEVOX); callers of 38MD HiasanDunia/
  RakSenjata/KaitModel readers (38KS/38DS/38EK); notice boards (PapanHidup.pasang) by 38KS/38EK/38ST; 40K recipe rows
  murid.bayangan/murid.klon (fall back to class); 38GU giok_warisan compass model, Ascend tribulation name, Panggung
  Bintang wide camera; kota_besar/tembok/sudut helper blocks outside piece; kota_besar kumuh/luar tata_letak without
  lots; F3 DESIGN note: marker icons 32-60 live in markers_tempat.png.
- Harness: run_all.sh and langitG/run_fin.sh now need final/wg/out38 (compiled by run_38.sh): run run_38.sh first.
- tools/keluaran/ (preview sheets, untracked) is generator output, not shipped.

## Open problems
None.

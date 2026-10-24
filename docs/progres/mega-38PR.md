# mega-38PR (wave W1) — Perbaikan dan ganti rugi (DESIGN 38.R + 38.T.14.3 row PR)

Owns: `dunia/tempat/perbaikan/**`, `client/dunia/tempat/perbaikan/**`, `kehancuran/{WorldBreaker,ProtectedZones}.java`,
`cultivation/CaveAbode.java`, `desa/Desa.java`, `lang-src/tempat_perbaikan.*`, advancements `tempat/{jujur,
tukang_sukarela,ditakuti}`. No Gradle. New worlds only.

## Checklist
- [x] Read code: stubs, WorldBreaker, ProtectedZones, CaveAbode, Desa, TempatApi/Zones/Events, LembarKunci, HidupApi, TemaApi
- [x] WorldBreaker: RestoreRequest + submitRestore (own queue, budget setRestoreBudget) + addChangeListener (classic batch + bulk chunk)
- [x] ProtectedZones: addClaimProvider + claimed (halls cached 200 t); CaveAbode line; Desa plaque claim + public loyaltyAt
- [x] PerbaikanConfig, NilaiBlok, JenisTempat (profiles incl. tema 38.T.11), TempatRawat, Gugus, PerbaikanData (SavedData tempat_rusak)
- [x] KlaimPemain (ProtectedZones.claimed + SectBuild.insideAnySite)
- [x] Cetak / CetakStore / Rekam (discovery via chunk starts/refs, record queue, region files LRU 6)
- [x] Banding (diff rules 6-9), PindaiTempat (scanner 4096/tick + priority boxes), LogAtribusi, Gugus
- [x] PekerjaanPerbaikan (temukan, report, rate, batches via submitRestore, failures, blocked containers)
- [x] Tukang (bodies, clips, props, foreman), Bantu, Penagihan (calc/merge/bayar/tawar/tolak/rela/donasi), Penagih (visits), Takut, Sitaan
- [x] PerbaikanLayar (tagihan sheet biasa/takut/donasi + ops incl. antar), PerancahNet payload
- [x] PerbaikanApi real (+faktorHarga, ditakuti, diskonPersembahan, poinBantu), Perbaikan init wiring (events, ticks, lifecycle)
- [x] PerbaikanCommands (uji tempat rusak/perbaiki/tagihan/takut/cetak/sitaan/gugus + kota_besar alias + player sitaan [ambil])
- [x] Tema row: factors/strength/collectors in JenisTempat, iblis Blood Price x2 + TemaApi.tolakTagihanIblis, canal boxes via TempatApi.titik(kanal)
- [x] Client Perancah (scaffold quads) + PerbaikanClient
  - [x] tools/gen_tempat_perancah.py -> textures/dunia/tempat/perancah.png (64x64 atlas, 16 cells, previewed)
  - [x] client/dunia/tempat/perbaikan/{Perancah,PerancahSink}.java + PerbaikanClient wiring (receiver, tick, COLLECT_SUBMITS, disconnect)
  - [x] lang perancah.* keys (5 board texts; now 136 keys id/en identical)
- [x] lang tempat_perbaikan (131 keys, id/en identical, generator scratchpad pr/lang_perbaikan.py), advancements tempat/{jujur,tukang_sukarela,ditakuti} (parent dunia/kota_found)
- [x] Self-review: javac (scratchpad pr/jc.sh, JDK 25 vs F0 build classes + MC jars) 33 files 0 errors after fixes
      (Perbaikan: ServerEntityEvents is event.lifecycle.v1; PerbaikanLayar: Items.WOOL/BED.pick(DyeColor));
      old files LF as in HEAD; 11 new CRLF files of this package normalised to LF; no client refs in server;
      no setBlock/LightningBolt/weather; no static ItemStacks; lang 136 keys, no duplicates across fragments

## Langkah berikutnya
DONE (re-verified in a resumed session: javac 33 files 0 errors, lang 136/136). Wave finish (F1) runs Gradle. Client: Perancah.java (putlog scaffold: standards, ledgers, rails, putlogs,
decks, braces, ladder, flapping mat/tarp, notice + progress ribbon, crew pennants, night lantern, dust/soot/sawdust,
boards; rise/fall 30 t; EffectQuality tiers + distance LOD + quad caps 500/3000/6000; shadow pass skipped),
PerancahSink.java, PerbaikanClient.java; atlas textures/dunia/tempat/perancah.png from tools/gen_tempat_perancah.py.

## Butuh dari modul lain
(none)

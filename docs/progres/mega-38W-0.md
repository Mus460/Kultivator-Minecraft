# mega-38W-0 (wave W0, 38 core skeleton) — checkpoint

Scope: jadwal-38-39-40.md W0 row 38W-0 + sec 5 note; design 38.E.0 step 1, 38.E.3 contracts, 38.B.2.2-6, 38.C.6, 38.D,
38.T.14.3 row W. Stub list + future owners: `docs/progres/kota-W0.md`. No Gradle run. No RencanaLama (new worlds only).

## Checklist
- [x] Study patterns (DuniaContent, DuniaConfig, DuniaPacks, placements, entity reg, Kultivator.id, tools/nbt.py)
- [x] tools/nbt.py Template.titik -> kultivator_titik (+ sisi -> kultivator_sisi); old templates byte-identical (tested)
- [x] tools/tempat_lib.py frozen API (Lot, PALET, GAYA, processor_gaya exact-state rules, set_* builders, cek_lot; self-tested)
- [x] tools/cek_tempat.py (templates, tata_letak grid/pools, salts, 38 placements, tema sides, budgets; runs clean)
- [x] worldgen: TempatTypes, TataLetakStructure (full), RencanaPlacement, CincinPlacement (full), JalanPlacement/JalanStructure
      (codec final, no edges yet)
- [x] core records: Kandidat, Distrik, Putusan, PutusanTanah (full 38.B.2.4 + Sumber), Rencana (+simpul/zonaAman/
      semuaDistrik/kandidat/putusan defaults), RencanaRegistry, LotPick, jalan/SimpulJalan, jalan/RencanaJalan (stub)
- [x] TempatInfo, PotonganInfo, Titik, TitikTemplat (full NBT reader), TempatEvents (5 events), TempatConfig (+JalanConfig),
      TempatZones (spatial hash, providers), TempatKeepOut (full logic, mixin is W1), TempatApi (signatures, groundSpot real)
- [x] RencanaKotaBesar (district grid/zones/verdict final; hitung() empty until W1), TempatCommands (uji tempat rencana|zona),
      TempatSpine, Tempat (all init lines live; uses ui.TempatLayar, hidup.Hidup, tema.KotaTema)
- [x] entity stubs + registries: gua (6), kotabesar (gerbang_kota), ekonomi (gerobak), jalan (rakit)
- [x] package inits + APIs: Jalan, KotaBesar(+Api), Ekonomi(+Api, JenisMisi, daftarJenisMisi), Gua(+Api), Cerita(+CeritaApi,
      Syarat.daftar, Akibat.daftar, QuestTracker.daftarTujuan), Perbaikan(+Api, Tagihan), Kampung(+Api), Kompleks(+Api)
- [x] DuniaContent + DuniaContentClient one line each (LF kept)
- [x] client TempatClient + stubs: model/TempatModelClient (Noop renderers for 9 types), hidup/HidupClient,
      kotabesar/KotaBesarClient, cerita/CeritaClient, perbaikan/PerbaikanClient, jalan/JalanClient
- [x] resourcepacks/dunia_jalan/pack.mcmeta; tags structure_set/tempat_berpenghuni + structure/boleh_di_tempat (empty)
- [x] lang tempat_inti (id_id/en_us identical keys; no duplicates across lang-src)
- [x] kota-W0.md stub list + owners
- [x] javac check (JDK 25 jdk.compiler from the MC runtime, not Gradle; output in scratchpad w0/jout): 70 files, 0 errors
      (only the vanilla ExclusionZone deprecation warning, same as JauhDariPusatPlacement); all new files LF

## Langkah berikutnya
Done. W1 (38W): RencanaKotaBesar.hitung (needs T12 GerbangPlan API), RencanaJalan edges + JalanStructure pieces,
ChunkGeneratorTempatMixin + json line, TempatApi place index, TempatNames, markers/guide/chips, run_38 harness.

## Butuh dari modul lain
- 38GU / 38KS / 38EK (W1): add `entity.kultivator.<id>` names to their lang fragments for penjaga_kristal,
  boneka_penjaga, tikus_roh, sangkar, lempeng_glif, pintu_tebing (tempat_gua), gerbang_kota (tempat_kota_besar),
  gerobak (tempat_ekonomi). Not added by 38W-0 so each key lives in exactly one fragment.
- 38MD (W1): replace the NoopRenderer lines in `client/dunia/tempat/model/TempatModelClient.java` with real renderers.
- Orchestrator/38UI (W1): `kultivator:papan_hidup` (38.O.2, content 38UI, model 38MD) has no registered entity type
  yet; suggest 38UI registers it under `dunia/tempat/ui/` in W1.
- Every module adding an inhabited 38 structure set (kampung, kompleks, kota_besar, pos, kota_tema) must add the set
  to `data/kultivator/tags/worldgen/structure_set/tempat_berpenghuni.json` (38W owns the file; ask via kota-W.md).
- F0: no extra action; builds should pick up `Tempat.init()` / `TempatClient.init()` (both depend on sibling W0
  classes TempatLayar, Hidup, KotaTema, UiTempatClient, TemaClient, which exist on disk).

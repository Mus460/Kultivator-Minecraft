# kota-W0 — 38W-0 skeleton: stub files and their owners (W1+)

Landed by 38W-0 (wave W0). Every line of `Tempat.init()` / `TempatClient.init()` is live; owners fill their own init
class and never edit `Tempat` / `TempatClient` (only 38W does). Signatures below are frozen contracts (38.E.3, 38.T.14.4);
later changes are additive only. Progress of this module: `docs/progres/mega-38W-0.md`.

## Server `src/main/java/dev/kultivator/dunia/tempat/`

| File | Owner W1+ | State in W0 |
|---|---|---|
| `Tempat.java` (entry, all init lines) | 38W | final |
| `TempatSpine.java` | 38W | final (types, config, zones, titik reader, kota_besar plan, pack `dunia_jalan`, uji tempat) |
| `TempatApi.java` (C38.W1) | 38W | signatures final; `groundSpot` real (rule 12), `viewing` -> `TempatLayar.sedangMelihat`, rest neutral |
| `TempatInfo`, `PotonganInfo`, `Titik` (records) | 38W | final |
| `TempatEvents.java` (TEMPAT_RUSAK, TAGIHAN, ACARA, KEJAHATAN, QUEST + fire*) | 38W | final |
| `TempatConfig.java` (`tempat` + nested `JalanConfig` = `jalan`) | 38W | final (38.D.9 keys) |
| `TempatZones.java` (ProtectedZones + safeGround providers, `daftar/hapus` runtime boxes, `inside`) | 38W | real |
| `TempatKeepOut.java` (`tolak(seed, structure, chunk)`) | 38W | real; mixin `ChunkGeneratorTempatMixin` + json line = 38W W1 |
| `TempatCommands.java` (`uji tempat rencana|zona`) | 38W | W1 adds cek/kepadatan/ukur/lihat |
| `Rencana.java` (C38.W2 + `simpul`, `zonaAman`, `semuaDistrik`, `kandidat`, `putusan` defaults) | 38W | final |
| `RencanaRegistry.java` (`register/get/ids/semuaZona/semuaZonaAman/semuaSimpul`, `KOSONG`) | 38W | final |
| `Kandidat`, `Distrik`, `Putusan` (records) | 38W | final |
| `PutusanTanah.java` (38.B.2.4 verdict, `Aturan`, `Sumber.of(context|ServerLevel)`, cache 64) | 38W | real |
| `RencanaKotaBesar.java` (11 districts, zones, verdict) | 38W | geometry final; `hitung(seed)` empty until W1 (needs T12 gate list) |
| `LotPick.java` (hashed weighted lot pick) | 38W | real |
| `TitikTemplat.java` (reads `kultivator_titik` / `kultivator_sisi` from NBT resources, transform) | 38W | real |
| `worldgen/TempatTypes.java` (tata_letak, jalan; cincin_tempat, rencana_tempat, jalan) | 38W | real, always registered |
| `worldgen/TataLetakStructure.java` (codec rencana/distrik/lot/gaya; lots as JIGSAW pieces) | 38W | real |
| `worldgen/RencanaPlacement.java` (`rencana`, `distrik`) | 38W | real |
| `worldgen/CincinPlacement.java` (`min_chunks`, `cincin`, `jarak_tempat` vs set tag `#kultivator:tempat_berpenghuni`) | 38W | real |
| `jalan/Jalan.java`, `JalanEntities` (`rakit`), `RakitEntity`, `SimpulJalan` | 38W | entity minimal |
| `jalan/RencanaJalan.java` (`lewat`, `sisiDi`, `sisiSel`, `Sisi`) | 38W | no edges until W1 |
| `jalan/JalanPlacement.java`, `jalan/JalanStructure.java` (codec ubin/jembatan/tangga) | 38W | codec final, no pieces until W1 |
| `kotabesar/KotaBesar.java`, `KotaBesarApi` (C38.KS1), `KotaBesarEntities` (`gerbang_kota`), `GerbangKotaEntity` | 38KS | neutral / minimal |
| `ekonomi/Ekonomi.java`, `EkonomiApi` (C38.EK1 + `daftarJenisMisi`, `jenisMisi`), `JenisMisi`, `EkonomiEntities` (`gerobak`), `GerobakEntity` | 38EK | bayar/beri/harga real (NilaiBatu/Harga); kas/kasKota/lencana neutral |
| `gua/Gua.java`, `GuaApi` (C38.GU1), `GuaEntities` (6 types), `PenjagaKristalEntity`, `BonekaPenjagaEntity`, `TikusRohEntity`, `SangkarEntity`, `LempengGlifEntity`, `PintuTebingEntity` | 38GU | attributes only / minimal |
| `cerita/Cerita.java`, `CeritaApi` (C38.ST1), `Syarat` (`daftar`, `cek`), `Akibat` (`daftar`, `jalankan`), `QuestTracker` (`daftarTujuan`, `cocok`, `peristiwa`) | 38ST | registries live, state neutral |
| `perbaikan/Perbaikan.java`, `PerbaikanApi` (C38.PR1), `Tagihan` | 38PR | neutral |
| `kampung/Kampung.java`, `KampungApi` (C38.DS1) | 38DS | neutral |
| `kompleks/Kompleks.java`, `KompleksApi` (C38.DS1) | 38DS | neutral |

Not created by 38W-0 (other W0 owners): `ui/**` (38UI-0: `TempatLayar.init()` is called from `Tempat.init()`),
`hidup/**` (38NL-0: `Hidup.init()` called), `tema/**` (38TK-0: `KotaTema.init()` called).
Not created at all yet: `TempatNames` (38W W1), `RencanaLama` (dropped, new worlds only), `PropId` (it is 40's
`dev.kultivator.sosok.PropId`).

## Client `src/client/java/dev/kultivator/client/dunia/tempat/`

| File | Owner W1+ | State |
|---|---|---|
| `TempatClient.java` (entry: UiTempatClient, TempatModelClient, HidupClient, KotaBesarClient, CeritaClient, PerbaikanClient, JalanClient, TemaClient) | 38W | final |
| `model/TempatModelClient.java` | 38MD | NoopRenderer for all 9 W0 entity types (replace each line with the real renderer) |
| `hidup/HidupClient.java` | 38NL (W1/W2) | empty init |
| `kotabesar/KotaBesarClient.java` | 38KS | empty init |
| `cerita/CeritaClient.java` | 38ST | empty init |
| `perbaikan/PerbaikanClient.java` | 38PR | empty init |
| `jalan/JalanClient.java` | 38W | empty init |

## Old files touched (one line each, LF kept)
`dunia/DuniaContent.java` (`Tempat.init()`), `client/dunia/DuniaContentClient.java` (`TempatClient.init()`).

## Tools and data
- `tools/nbt.py`: `Template.titik(jenis, x, y, z, hadap, data)` -> root key `kultivator_titik` (list of
  `{jenis, pos:[I x3], hadap, data:{str:str}}`), `Template.sisi(n, e, s, w)` -> `kultivator_sisi` `{n,e,s,w}`; both written
  only when used (old templates byte-identical, tested). Max 96 titik per template.
- `tools/tempat_lib.py` frozen API: `LOT 48`, `LANTAI_Y 4`, `TEPI 3`, `MAKS_TITIK 96`, `MAKS_JANGKAR 6`, `KODE_SISI`,
  `ALAT`, `JENIS_TITIK`, `TITIK_KOTAK`, `titik_sah`; `PALET` (kota_besar, kampung, klan, kompleks_lurus/netral/iblis, gua,
  jalan); `GAYA` (kota_besar dasar/salju/gurun/sakura, kampung 7, klan 4) + `ganti_kayu`; `Lot(folder, nama, tinggi,
  lebar, dalam, palet, seed)` with `fondasi`, `jalan_tepi`, `kotak`, `jalur`, `titik`, `jangkar`, `simpan`;
  `elemen`, `tulis_pool`, `tulis_pool_gaya`, `processor_gaya` (exact-state rules keep properties), `processor_utuh`,
  `tulis_processor`, `semua_state`, `struktur_tata_letak`, `set_rencana`, `set_cincin`, `set_jalan`, `tulis_wg`,
  `putar_sisi`, `sisi_cocok`, `cek_lot`, `cek_semua`.
- `tools/cek_tempat.py`: templates, tata_letak pools/grid/anchors/spawns, salts (unique + 38 ranges), 38 placements,
  tema side codes, gzip budgets. Clean on the current repo.
- Tags: `data/kultivator/tags/worldgen/structure_set/tempat_berpenghuni.json` (structure-SET tag used by
  `cincin_tempat.jarak_tempat`; every inhabited 38 set must be added), `tags/worldgen/structure/boleh_di_tempat.json`.
- Pack: `resourcepacks/dunia_jalan/pack.mcmeta` (`kultivator.dunia.pack.jalan`).
- Lang: `lang-src/tempat_inti.{id_id,en_us}.json` (pack name, `entity.kultivator.rakit`, `kultivator.jalan.inspired`,
  `uji tempat` texts).

## Placement JSON (for generators)
- `kultivator:rencana_tempat`: `{salt, spacing=256, separation=0, rencana, distrik}`.
- `kultivator:cincin_tempat`: `{salt, spacing, separation, min_chunks=6, cincin:[{sampai_chunk, frekuensi}], jarak_tempat=8}`.
- `kultivator:jalan`: `{salt}` (spacing forced 1).
- `kultivator:tata_letak` structure: vanilla settings + `rencana`, `distrik`, `lot:[{dx, dz, dy=0, pool, putar=0}]`,
  `gaya:[{biomes, akhiran}]`. Lot floor (template y 4) replaces the ground surface block (piece y = dasarY - 5 + dy).

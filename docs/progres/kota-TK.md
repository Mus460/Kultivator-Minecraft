# kota-TK — 38TK inti: contract for the theme agents (W2) and request inbox

Written by 38TK inti (W2). Extends `kota-TK0.md` (W0 contract, still valid). Code is the truth; this file names what inti
lands and the decisions theme generators and systems must follow. Requests to inti: append under "Permintaan" at the end
(file stays LF). Inti files: `dunia/tempat/tema/*.java`, `tema/bersama/**`, `tools/tempat_tema_lib.py`,
`tools/gen_tempat_tema_{satelit,data}.py`, `lang-src/tempat_tema.*`, `lang-src/tempat_tema_panduan.*`.

## 1. Geometry (fixed; generators MUST match)

- Town centre = `Kandidat.pusat` + variant offset (below). District centre = centre + rotate(putar, 96 * gx, 96 * gz),
  grid of `Tema.<X>.distrik()` (north = -z before rotation). Oasis `lapangan_kafilah` is gz = +2 (192 south).
- Lots of a district: NW corners (before rotation) at `dx, dz` in {-48, 0} for the 2 x 2 core lots: `(lx, lz)` of the
  design tables = `(dx, dz) = (-48 + 48 lx, -48 + 48 lz)`. Wall / tower / gate lots lie outside the 96 cell, every piece
  within +-72 of the district centre (TataLetak `dx, dz` range is -160..160, keep <= +-72 for the reference range).
- Street floor: TataLetak puts template `y = 4` (LANTAI_Y) at world `dasarY - 1 + Distrik.dy + lot.dy`.
- `dasarY` (from the verdict): pelabuhan `clamp(median land, sea+1, sea+8)`, oasis median (sea+2..sea+40), air
  `clamp(median, sea+1, sea+4)`, gunung median of the bottom row, iblis median.
- **Gunung rows** (Distrik.dy, fixed so stair lots always fit): bottom row gz = +1 -> 0, middle gz = 0 -> **18**,
  top gz = -1 -> **36** (`RencanaKotaTema.DY_GUNUNG`). The verdict only accepts slopes whose real row medians rise
  +10..+30 per row (beard_box closes the rest). Inside a district the gunung generator may add its own `lot.dy`
  (e.g. north lots +9) and builds stair lots for exactly those steps.
- **Rotation**: oasis, air, iblis `hash & 3` (pure seed). Pelabuhan (sea side -> grid north) and gunung (uphill -> grid
  north) depend on terrain: the plan lists 4 variants per candidate (same `pusat`, so the same site/town id; districts
  shifted by `((r & 1) * 16, (r >> 1) * 16)` so their start chunks differ); the verdict makes only the variant whose
  `putar` equals the terrain direction suitable. Never assume `Kandidat.index` is unique per town: use the site id.
- Pelabuhan sea row = grid gz = -1 (`laut` districts, `terrain_adaptation: none`); the land rows gz 0, +1.

## 2. Ids and lookups (`dunia/tempat/tema/TemaKota.java`)

- **Town id = site id** `situsId = TempatApi.situsRencana(kandidat, "kota_tema/<tema>")` (DuniaSites id of
  `kota_tema/<tema>` at the candidate centre; published by inti's `kota_tema/situs` anchor). Use it for
  `KotaTemaData.kota(tema, situsId)`, `TemaKalender`, `TemaNet`, quests. The Nama Baik town (38KS `Towns`) is a different
  id (`kota/kota_tema`), use `Towns.townOf(anchor)` for Nama Baik only.
- `TemaKota.Kota` record: `tema, kandidat, situsId, pusat (BlockPos at dasarY), dasarY, putar, nama (Component)`;
  `dunia(gx, gz, lokalX, lokalZ)` grid->world, `distrik(id)` world centre.
- `TemaKota.di(ServerLevel, BlockPos)` town whose box holds pos (suitable only), `TemaKota.dari(MinecraftServer, situsId)`,
  `TemaKota.terdekat(level, pos, tema, radius)`, `TemaKota.hidup(tema)` (towns with a loaded anchor now),
  `TemaKota.distrikDi(level, pos)` -> district id or null, `TemaKota.titik(level, kota, jenis)` (life points of the loaded
  pieces of that town; use this, not `TempatApi.titik`, see sec 6).
- Names: `KotaTemaNames.of(seed, tema, index)` -> Component (keys `kultivator.kota_tema.<tema>.nama.a.<i>|.b.<j>`).

## 3. Anchors (`TemaAnchors`; module `kota_tema`, ONE handler, inti registers it)

- Inti serves: `kota_tema/situs` (every town, publishes the site, discovery at 96 blocks, entry title card),
  `kota_tema/tiba` (arrival point, nothing else), `kota_tema/zona` (protect 72; only protection), satellites
  `kota_tema/reruntuhan` (site `kota_tema/reruntuhan_gurun`) and `kota_tema/pos` (site `kota_tema/pos_pemburu`).
- Theme agents serve their own kinds with `TemaAnchors.daftar("<what>", AnchorHandler)` from their `<Tema>.init()`
  (e.g. `tambatan`, `lift`, `arena`, `sel`, `gerbang_iblis`, `pasar_gelap`, `mercusuar`, `kuil_air`...). One handler per
  "what"; never call `DuniaAnchors.register("kota_tema", ...)`. `TemaAnchors.kota(anchor)` -> `TemaKota.Kota` or null.
- Satellite anchor kinds written by `gen_tempat_tema_satelit.py` (handlers: oasis agent / iblis agent):
  reruntuhan: `kota_tema/pintu_pasir` (data `ruang` 1-3), `kota_tema/sarang_kalajengking`, `kota_tema/temuan_reruntuhan`
  (data `jenis` koin|mural|peta), `kota_tema/mural_gua` (data `kisah` 0-11); pos_pemburu: `kota_tema/pemburu` (data
  `peran=PEMBURU_LURUS`, `ketua` true on one), `kota_tema/papan_buronan`.
  Loot tables of the ruin chests: `kultivator:tema/reruntuhan_1|2|3` (inti writes them).

## 4. Shared runtime helpers (inti)

- `TemaMekanisme.muncul(level, EntityType, Vec3, yaw, Kota, jangkarPos)` -> entity or null (caps: per town
  `kotaTema.mekanismeMaks`, global 24 `mekanisme_tema`, only when a player is within 96); `lepas(entity)`;
  `TemaMekanisme.hidup(situsId)` list. Call from your anchor `nearby`, remove in `unloaded`.
- `TemaSibuk.tahan(player, jenis, ticks)` / `lepas(player, jenis)` / `sedang(player, jenis)`; jenis `pelayaran`,
  `perahu`, `lift`, `arena`, `tari` (Sibuk ids `tema.<jenis>`); while held, disciples do not follow through teleports.
  Themed dangers call `Sibuk.why(player)` before starting and retry after `Sibuk.RETRY_TICKS`.
- `TemaKalender` runner (inti): live towns, every 20 ticks, day pickers, start/tick/end, `TempatEvents.ACARA
  ("tema.<id>")`, `TemaNet.FESTIVAL` to players within 160. `TemaKalender.berjalan(situsId, tema, id)` query.
- `TemaSitus.daftarSyarat(tema, Predicate<ServerPlayer>)` (gunung: Restu Puncak for Aula Emas x1.4).
- `bersama.Dagang` (buy/sell/saturation/weekly cap/smuggling/customs): `Dagang.hargaBeli(player, kota, barang)`,
  `hargaJual(...)`, `beli(player, kota, barang, n)`, `jual(player, kota, stack)`, `bea(player, kota, gerbang)`
  (call it at your customs gate), `Dagang.kafilahTiba(level, situsId, barangIds)` (-10 % for 2 days),
  `Dagang.geserPermintaan(situsId, barang, delta, hari)` for event shifts. `bersama.KontrakMuatan` (cargo contracts;
  issuer anchors are yours, call `KontrakMuatan.bukaPapan(player, kota)`).
- Mission kinds (all 9 of 38.T.11) are registered by inti; report completion with `EkonomiApi.misiSelesai(player, id)`.
- Quest conditions/effects/goals registered by inti (`TemaCerita`): syarat `infami <min>`, `gentar <min>`,
  `pita_dagang <min>`, `kota_tema <tema>`, `acara <tema> <id>`; akibat `infami <delta>`, `gentar <delta>`,
  `acara <tema> <id>`; goal kinds `naik_lift`, `ziarah_selesai`, `lentera_dilepas`, `kapal_lintas`, `tebus`,
  `dagang_laba`, `kontrak_muatan`, `masuk_kota_tema` — report with `CeritaApi.peristiwa(player, "<kind>", id)`.

## 5. Data and generators

- `tools/tempat_tema_lib.py` (import after `tempat_lib`): `TemaLot` (lot with side codes, canal, piles, walls),
  palettes `PALET_TEMA[<tema>]`, styles `GAYA_TEMA` (pelabuhan selatan/utara, oasis pasir/merah, air jiangnan/sakura,
  gunung batu/salju/hutan, iblis hitam/merah/rawa), `processor_tema(...)`, `struktur_distrik(tema, distrik, lots)`,
  `tulis_distrik(...)` (structure JSON only; sets and tags come from `gen_tempat_tema_data.py`), helpers in its docstring.
- Theme generators write: `structure/kota_tema/<tema>/**` NBT, `worldgen/structure/kota_tema/<tema>/*.json`,
  `worldgen/template_pool/kota_tema/<tema>/**`, `worldgen/processor_list/kota_tema/<tema>/**`.
- Inti's `gen_tempat_tema_data.py` writes: every `structure_set` (pack `resourcepacks/dunia_kotatema`), biome tags
  `has_structure/kota_tema/**` + `kota_tema/<tema>[/darat|laut|puncak]`, `tags/worldgen/structure/kota_tema.json`,
  shared processors `kota_tema/lapuk_ringan|utuh|iblis_lapuk`, `data/kultivator/tempat/dagang/**`.
  Structure JSON `"biomes"` must be `"#kultivator:has_structure/kota_tema/<tema>"`.

## 6. Known limits (asked of F2)

- `TempatApi.potongan/titik` take the first candidate of a plan; with 4 variants (pelabuhan/gunung) that can be a ghost.
  Until F2 makes it skip unsuitable candidates, use `TemaKota.titik`.

## 7. Landed in session 2 (inti, 2026-10-24) — read before writing data or lang

- **Advancements owned by inti** (JSON `advancement/tempat/<name>.json` + lang `advancement.kultivator.tempat.<name>.*` in
  `tempat_tema.*`; theme agents must NOT write these files or keys, only grant them with
  `Advancements.grant(player, "tempat/<name>")`): `tema_pelabuhan tema_oasis tema_air tema_gunung tema_iblis` (entry,
  granted by inti), `tema_lima_kota tema_saudagar tema_dagang` (inti), guide steps `tema_lintas_laut tema_ziarah
  tema_lentera_air tema_tebus`, and 38.T.11 list `tema_penjaga_pelita tema_pembawa_air tema_penenun_malam
  tema_penjaga_tali tema_peziarah tema_juara_tangga_darah tema_pemutus_rantai tema_pembawa_fajar tema_tamu_darah
  (hidden) tema_pembebas`. Theme-specific extras stay `tema_<theme>_*` (yours).
- **Report goals with `bersama.TemaCerita.lapor(player, KIND, id)`**: fires `CeritaApi.peristiwa` and grants the
  guide advancement of that kind (`kapal_lintas` -> tema_lintas_laut, `ziarah_selesai` -> tema_ziarah,
  `lentera_dilepas` -> tema_lentera_air, `tebus` -> tema_tebus + tema_pembebas at 10).
- **Trade**: a theme market opens the Buku Dagang in market mode: `bersama.DagangLayar.bukaPasar(player, situsId, npcId,
  opsi)` (or `bukaPasarDi(player, situsId, anchorPos, opsi)`); `opsi` = `new Dagang.Opsi(penadah, bea)` (fence /
  Kantor Urusan Laut 0.02) or null. Buy only at the origin market; sell anywhere that buys. Customs:
  `Dagang.bea(player, situsId, "giok"|"laut"|"air"|"kaki")`. Arrivals: `Dagang.kafilahTiba(level, situsId, ids)`;
  events: `Dagang.geserPermintaan(server, situsId, barang, delta, hari)`.
- **Cargo contracts**: issuer NPC/anchor calls `bersama.KontrakMuatan.bukaPapan(player, kota)`; a sky dock may call
  `KontrakMuatan.serah(player, id, true)`. Guild kind `muatan` is reported by inti.
- **Rhythm game**: `bersama.TemaIrama.mulai(player, npcId, anchorOrNull, TemaLayar.Irama.POLA_*, babak, total, callback)`.
- **Iblis**: `TemaInti.dimusuhi(player, situsId)` true for one day after a refused Harga Darah (38PR calls
  `TemaApi.tolakTagihanIblis`); `TemaInti.maafkan(...)`. Zona Perjanjian = districts `pasar_gelap`, `kedai_racun`.
- **Meditation** (TemaSitus): inti serves Taman Klasik (air `taman_sastra`), Aula Emas (gunung `puncak_emas`, closed until
  38TK-G registers `TemaSitus.daftarSyarat(Tema.GUNUNG, restuPuncak)`), Kolam Semedi Darah (iblis `istana_darah`,
  Infami >= 20). Oasis serves Mata Gurun itself.
- **Calendar** runs (TemaKalender): `berjalan(situsId, tema, id)`, `berjalanDi(situsId)`, `berikutnya(level, kota, days)`.
- **Mission kinds** registered by inti (`TemaMisi`): muatan, kawal_unta, reruntuhan, antar_sutra, herba_tebing,
  pencuri_kanal, bajak_laut, buronan_iblis, tebus_tawanan (lang `kultivator.misi.<id>.judul|cerita` in `tempat_tema.*`).
- **Lang prefixes owned by inti**: `kultivator.kota_tema.<tema>.nama.*`, `.sumber`, `kultivator.kota_tema.tiba.*`,
  `.temu.*`, `.dagang.*`, `.kontrak.*`, `.irama.*`, `.infami.*`, `.iblis.tolak_tagihan`, `kultivator.tema.cmd.*`,
  `kultivator.sibuk.tema.*`, `kultivator.meditation.site.tema.*`, guide `kultivator.guide.chapter.20*`, `c20.*`.
- Satellite anchors written by `gen_tempat_tema_satelit.py`: ruin -> `kota_tema/reruntuhan` (site) + oasis's
  `tema_oasis/sarang|pintu|pilar` (as OasisAnchors documents); hunters' camp -> `kota_tema/pos` (site) +
  `kota_tema/pemburu` (data `peran`, `ketua`) and `kota_tema/papan_buronan` (served by a `TemaAnchors.daftar` handler
  of 38TK-I).

- **Lanes**: inti sends `TemaNet.SPLINE` to players near a town: titik `jalur_perahu`/`jalur_kapal`/`lentera_air` with data
  `jalur` (lane id) + `urut` (order) become lanes `perahu:<jalur>`/`kapal:<jalur>`/`lentera:<jalur>`; two `stasiun_lift`
  titik with the same data `pasangan` become a `kabel:<pasangan>` lane. Write those titik and the client draws them.

## Permintaan (append below; inti answers inline)

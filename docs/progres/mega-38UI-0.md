# mega-38UI-0 (wave W0, skeleton) — progress

Scope (jadwal-38-39-40.md W0 row + sec 5 note): `dunia/tempat/ui/{TempatLayar,TempatLayarPayload,TempatAksiPayload,
LembarKunci}.java`, `client/tempat/ui/UiTempatClient.java` (stub registry). Nothing else. No Gradle. STATUS: DONE.

## Checklist

- [x] Read 38.U, 38.E.3 C38.UI1, 38.M, 38.B.4-6, 38.R.4-5, 38.T.13.3, KotaNet/Kota.onAction/KotaClient/HubNav patterns
- [x] TempatLayarPayload (S2C kultivator:tempat_layar; + boolean buka for refresh, empty layar = close)
- [x] TempatAksiPayload (C2S kultivator:tempat_aksi, op <= 256)
- [x] TempatLayar (init, buka, bukaDi, segarkan, bukaDari, tutup, daftar/daftarHub, Penangan, Pembangun,
      session + reach 8 + rate limit 10/s + read-only hub, sheet budget 32 KB warn / 512 KB refuse)
- [x] LembarKunci (header/entry keys, values, ops + helpers teks/barang/op/urai/arg*, 22 core screens of 38.U.3 as
      nested classes with schema javadoc, Ikon slot layout of ikon.png + pangkat/bintang/distrik/layanan helpers)
- [x] UiTempatClient (factory registry, receiver: close/refresh/open, hub child via HubNav.openChild, kirim, ditutup)
- [x] Verified against mcsrc 26.3: CompoundTag.sizeInBytes/contains/store/read(+ops), ItemStack.CODEC/EMPTY,
      HolderLookup.Provider.createSerializationContext, NbtOps.INSTANCE, ComponentSerialization.CODEC,
      Entity.distanceToSqr(Entity|Vec3)/isRemoved/isAlive/isSpectator, ServerPlayer.level(), Level.dimension(),
      LevelAccessor.getGameTime, ServerLevel.getEntity(int), Vec3.atCenterOf, BlockPos.immutable, Gui.screen/setScreen;
      Fabric calls copied from KotaNet/KotaClient/Seragam (PayloadTypeRegistry, Server/ClientPlayNetworking,
      Server/ClientPlayConnectionEvents.DISCONNECT, ServerLifecycleEvents.SERVER_STOPPED)
- [x] Final review + hand-offs (new files LF, no client class in main, no lang keys added: reuses
      `kultivator.kota.aksi.jauh`)

## Files

- src/main/java/dev/kultivator/dunia/tempat/ui/TempatLayarPayload.java
- src/main/java/dev/kultivator/dunia/tempat/ui/TempatAksiPayload.java
- src/main/java/dev/kultivator/dunia/tempat/ui/TempatLayar.java
- src/main/java/dev/kultivator/dunia/tempat/ui/LembarKunci.java
- src/client/java/dev/kultivator/client/tempat/ui/UiTempatClient.java

## API summary (for W1 modules)

Server: `TempatLayar.daftar(layar, Penangan)` (C38.UI1), `daftar(layar, Penangan, Pembangun)` (op `lihat` opens,
auto refresh after each action; handler then must not refresh itself), `daftarHub(layar, Penangan, Pembangun)` (hub,
npcId -1, read-only ops only), `buka(player, layar, npcId, lembar)` (C38.UI1), `bukaDi(player, layar, BlockPos,
lembar)` (service anchor, reach 8 from block), `segarkan(...)` (viewers only), `bukaDari`, `tutup`, `sedangMelihat`,
`layarTerbuka`, `npcTerbuka`, `idSah`. Client: `UiTempatClient.daftar(layar, Pembuat)`, screens implement
`UiTempatClient.LayarTempat` (layar, npcId, perbarui(lembar, buka)), send with `kirim`, call `ditutup` in `removed()`,
hub cards call `lihatDariHub(layar, HubNav.X)`.

## Langkah berikutnya

None for W0. W1 38UI: kit `client/tempat/ui/kit/**`, screens `client/tempat/ui/layar/**` registered via
`UiTempatClient.daftar`, `tools/gen_tempat_ui.py` drawing `ikon.png` exactly in `LembarKunci.Ikon` slot order,
`lang-src/tempat_ui.*`, hub cards (HubCards.register + lihatDariHub), `uji ui layar|skala`, kit API doc in kota-UI.md.

## Butuh dari modul lain

1. 38W-0 (owns Tempat.java / TempatClient.java): `Tempat.init()` must call
   `dev.kultivator.dunia.tempat.ui.TempatLayar.init();` and `TempatClient.init()` must call
   `dev.kultivator.client.tempat.ui.UiTempatClient.init();` (F0: verify; payloads are unregistered otherwise).
2. 38TK-0 (TemaLayar): `TemaLayar.daftar` should delegate to `TempatLayar.daftar(layar, penangan[, pembangun])`;
   tema sheets reuse LembarKunci common keys; 38TV-U client screens register via `UiTempatClient.daftar` and implement
   `UiTempatClient.LayarTempat`. Theme screen ids must match `[a-z0-9_]{1,64}`.
3. W1 gameplay modules (KS, EK, DS, GU, PR, ST, W): build sheets only with `LembarKunci` constants; key additions are
   requested in `docs/progres/kota-UI.md`. Builders for `buku_kas`, `peta_kota`, `jurnal` register with `daftarHub`.

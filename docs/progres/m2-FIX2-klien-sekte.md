# m2-FIX2-klien-sekte (fixer "klien-sekte")

Audits: aura-fx, sekte-klien, goals-codex #3 (DesaClient stale card).

## aura-fx
- [x] 1 old ClientLevel kept after disconnect: package-private forget() in the 10 per-world fx classes + EliteFx.forget(), all called from FxDispatcher.forgetLevel(), hooked on DISCONNECT (inside client.execute) in EliteClient; AuraClient nulls lastLevel in its DISCONNECT; DampakDisplays.clear() nulls owner
- [x] 2 SpecFx.play: followUntil/accents joined the existing try, layersOf/start loop wrapped (RuntimeException, LOGGER.warn)
- [x] 3 display id pools: `<=` wrap in PhantomDisplays and DampakDisplays. RealmScenery, TowerScenery, FarRelic are other fixers' files: needs

## sekte-klien
- [x] 1 IdentitasPage.kebijakan: "module off" line when !canSend(MandiriPayloads.Aksi.TYPE); lang key ...papan.tampil.kebijakan.mati in sekte_mandiri_cerita (id + en)
- [x] 2 Founding.Pending carries dim, BuildPreview tag gets "dim"; BuildClient.previewActive() is false in another dimension (hologram, HUD, lihat all follow)
- [x] 3 Keys.send drops (and logs) an Action whose text is longer than 64 chars instead of disconnecting

## goals-codex
- [x] 3 HallDesaPage: "Ambil Surat"/Pelindung send an OP_REFRESH right after the action (card no longer stale for 4 s). DesaClient.data() stays an unused spine stub (server CH_DESA push left as is)

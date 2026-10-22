# sekte:S progress (Seragam, Lambang & Bendera, DESIGN 36.2.8 + 36.4 Modul S)

Run 1 started 2026-10-22.

## Checklist
- [x] 1 Read shared API (SekteContent/Ids/Config/Commands/Bus/Api, Lambang, LambangService, TemplateExtensions, TemplateSpec,
      DiscipleRobeLayer, FormationDisc, HubCards, templates) and mcsrc APIs (DynamicTexture, NativeImage, blit, light)
- [x] 2a PanjiKomponen (sx_seragam_panji T1 gate pair, sx_seragam_panji_formasi T4 four poles), cells per template chosen
      and verified with harness scratchpad/seragamS (FindS, DumpS ext: no COLLIDE/AIROVER, 14/28 ops, anchors ok)
- [x] 2b Server: SeragamConfig, LambangData, SeragamData, SeragamPayloads (LambangSync, LambangSet, open), Seragam
      (init + LambangService impl + sync + NPC standards), SeragamCommands, advancements seragam_lambang/_bendera
- [x] 3 Textures: tools/gen_sekte_seragam_textures.py -> textures/sekte/seragam/ (lambang_atlas shapes+symbols grey,
      robe cuts), checked with Read
- [x] 4 Client: LambangCache, LambangAtlas (DynamicTexture, max 4 halls + preview), SeragamQuads, BenderaRenderer,
      LambangFormasiRenderer, LambangDesignerScreen (layout checked for 320x240), SeragamOwners, JubahLook, SeragamClient
      (receivers, COLLECT_SUBMITS, hub card sekte_seragam_lambang)
- [x] 5 DiscipleRobeLayer edit (colors/trim per rank, robe cut, emblem sheet)
- [x] 6 Lang lang-src/sekte_seragam.{id_id,en_us}.json (150 keys, identical, no duplicates; generator scratchpad/seragamS/lang.py)
- [x] 7 Wire lines in SekteContent / SekteContentClient
- [x] 8 javac self-check (no Gradle): all S files + DiscipleRobeLayer 0 errors (scratchpad/seragamS/log4.txt); all LF;
      cloth/emblem/robe composites checked with harness AtlasCheck -> scratchpad/seragamS/cloth_preview.png (Read)

## Decisions
- Robe layer gets owner by matching render-state position to a client cache of nearby disciples (DiscipleEntity.ownerId()
  is synced); render state has no entity ref and DiscipleRenderer is not S's file.
- Per hall client textures: atlas 128x64 (cloth left, bare emblem right) + robe sheet 64x64; max 4 halls (LRU).
- NPC sects: S reads SekteApi.diplomasi() sects()/emblem()/gate() itself and syncs "npc:<id>" standards (drawn pole+cloth,
  positions found by a read-only heightmap scan near the gate); D needs no S import.
- Banner = vertical hanging streamer (fan) from a client-drawn arm at the gilt cap, facing inward (gate: toward the
  other gate pole; formation: toward the lodestone 'formation' anchor; arena: toward 'arena').

## Files so far
main sekte/seragam/: PanjiKomponen, SeragamConfig, SeragamData, LambangData, SeragamPayloads, Seragam, SeragamCommands;
data advancement/sekte/seragam_{lambang,bendera}.json; tools/gen_sekte_seragam_textures.py -> textures/sekte/seragam/
{lambang_atlas,kain_tenun,bendera_hiasan,decal_cincin}.png, textures/entity/robe_base_{pendek,tempur}.png (checked).
client sekte/seragam/: LambangCache, LambangAtlas.

## Status
DONE (run 1). LambangAtlas paints into int[] (paint()) so the harness can check it; bake budget 2/tick.

## Open problems / for others
- Finish step: verify poles in game with uji sekte audit/pamer (dry-run harness clean on all 9 templates).
- Module T: make panji_arena_n the gilt-cap cell of a pole (S draws the cloth there, facing anchor 'arena').
- Module G (not S): src/client/.../sekte/tangga/TanggaClient.java:56 and TanggaIlusiRenderer.java:344,392 fail javac
  (seen when compiling SekteContentClient); left to G.
- Lead: guide c16.bendera text could mention the designer card (Hub > Sekte > Lambang & Seragam).

## Next step
None.

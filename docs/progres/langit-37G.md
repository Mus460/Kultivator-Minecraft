# langit:37G progress (DESIGN 37 Sekte Langit, module 37G gameplay/events/NPC/UI)

Contract: docs/rancangan/37-sekte-langit.md 37.7 + 37.9.5; audit-integrasi.md section 4. No Gradle.
Status: DONE (run 1). javac helper: scratchpad/langitG/jc.sh <out> files... (javac 25, never Gradle): all 37G files clean.

## Checklist
- [x] 0 Read design, audit sec 4, core APIs (37B core: LangitApi/SkyHallView/LangitIds/LangitConfig "sekteLangit"/
      LangitPayloads/LangitEvents/LangitKeys)
- [x] 1 main/LangitData (SavedData kultivator:sekte_langit) + main/LangitSyncServer (Sync, dirty(UUID))
- [x] 2 main/LangitSites (3 sites + drain factor) + main/LangitSaksi (FX_SENGSARA_BAWAH, witness +2%, strike factors)
- [x] 3 mixin/ServerLevelPrecipitationMixin + main/LangitCuaca + kultivator.mixins.json (1 entry)
- [x] 4 main/LangitSerbu + musuh/SectRaids SpawnPointProvider + repelBonus (CRLF kept)
- [x] 5 main/LangitPeristiwa (daily roll, Sibuk, tamu, pesta, scenes 198-199) / LangitBajak (+Longbo, beats 193-196) /
      LangitUtusan (Perintah Istana Langit, beat 197, Tantang via HeavenlyWrath.challenge) / LangitSuaraServer / LangitItems;
      KapalBajakEntity.berthAt/leave (LF), HeavenlyWrath.canChallenge + addWinListener (CRLF), Harga.BASE
      cangkang_kura_langit 1200, DuniaMarkers sekte/istana_langit, DuniaApi.addSafeGround for sky volumes
- [x] 6 npc/LangitNpcPlan, LangitSektePlacement, LangitNpcPack, LangitNpcAnchors, JaringNpc; tools/gen_sekte_langit.py ->
      7 nbt, 4 pools, structure, 2 tags, pack resourcepacks/sekte_langit (2 sets); harness scratchpad/langitG/wg/run.sh:
      TplCheck 7 files 0 problems, WgCheckL ALL OK, PlanCheckL (2 palaces/seed, placement only on the planned chunk)
- [x] 7 main/LangitMurid (26-27), main/LangitTangga (path(UUID) + arrival provider; LangitArrival merged here)
- [x] 8 15 advancements (data/kultivator/advancement/sekte_langit), GuideChapters ch17 (COUNT 17, shown after save 8,
      11 steps appended, jaring + sembilan optional), client ui/LangitUiClient (hub card sekte_langit -> Perguruan,
      scene recipes) + ui/LangitScreen (diorama, 3 tabs, seals, invite box, ? seal); LangitClient + LangitContent lines
- [x] 9 sekte/langit/LangitTests + main/LangitTestHooks (all 37.7.9 subs + tamu), CheatHelp group sekte_langit;
      player /kultivasi langit [feri|utusan <jawab>]; main/LangitUi + LangitUiPayloads
- [x] 10 lang-src/sekte_langit.* (225 keys) + sekte_langit_panduan.* (26 keys): parity ok, no clashes, every used key exists
- [x] 11 item model/texture (cuboid shell, 32 px atlas, checked), line endings (61 files ok), final javac

## Decisions
- Sky site factor stays INSIDE Meditation cap 3.5 (it is a site); benefit outside the cap = vein drain 0.9 / 0.85 (T5+).
- Scene beats 193-199 (W's unused 190s; 190-192 left free).
- GerbangPlan tiers are 1-5: NPC sky palaces go to the 2 strongest gate sects (design's tier>=7 impossible).
- Palace elder is a greeter (not the war master); war is still won at the ground gate (module D's KetuaSekte).
- Hub card merged into LangitUiClient (no separate LangitHub class).

## Requests (needsFromOthers)
- 37B: fire LangitEvents.founded/tier/ascended/removed/changed (37G grants rancang/tapak_awan/naik/sembilan_langit,
  festivals, sync); HallBridge arrival provider should return null for standing sky halls (LangitTangga.arrival wins).
- 37B anchors map should expose arrival_kaki, tangga_kaki/puncak, dermaga_*, darat_*, feri_atas, meditasi_*, jaring_*,
  gerbang_langit (optional kura_kura for Longbo's landing).
- 36 G: TanggaService has no path hook; LangitTangga.path(UUID) is ready to be called (36 file not edited).
- Voice pass: 6 new Japanese lines (sekte_langit.*) listed in the final report; code falls back to existing lines.

## Next step
None (module finished). Lead: Gradle compileJava compileClientJava jar once after 37B/37M/37V.

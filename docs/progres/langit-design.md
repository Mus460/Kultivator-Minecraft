# langit:design progress (DESIGN 37 "Sekte Langit", doc docs/rancangan/37-sekte-langit.md)

Run 1 (2026-10-22). STATUS: DONE. READ-ONLY except this file and docs/rancangan/37-sekte-langit.md. No Gradle.

## Checklist
- [x] 1 Read sect/hall + sect/build (blueprints T1-T9, BuildPlacer, Validator, Classifier, Scheduler, Site, EngineApi, ProtectedZones)
- [x] 2 Read DESIGN 20/20b/20c/21 + 36 summary, formasi/**, Meditation/SpiritSites/SpiritVeins
- [x] 3 Read flying sword, dunia/kapal ships, Film Kosmik surga, rival NPC sects, voices, hub, Kitab Panduan, EffectQuality
- [x] 4 Verify world height limits (mcsrc) + tools/nbt.py, tools/structgen.py worldgen pipeline
- [x] 5 Verify Sumber (lore)
- [x] 6 Write doc sections 37.0-37.4
- [x] 7 Write doc sections 37.5-37.11 (modules, tests)
- [x] 8 Self check (LF, 0 CR, 1590 lines; verified FormasiB, SectSpawnMixin, SiteHologram, mixins json, COLLECT_SUBMITS,
      Tribulation.addStrikeFactorProvider, HeavenlyWrath.start pkg-private, NpcRole.UTUSAN, tickPrecipitation, weather renderer)

## Facts collected (short)
- 3 Explore surveys done (formasi/qi/flight/kosmik; worldgen/diplomasi/hub/guide/voice/raids; DESIGN 20/21/24/36).
- Height: overworld -64..319 (DimensionTypes.java:47), cloud attr 192.33 (EnvironmentAttributes CLOUD_HEIGHT).
- Territory |dy|<=Halls.TERRITORY_HEIGHT 32; SpiritSites CAP 4.0 MOUNTAIN y>=120; Meditation refuses not-on-ground.
- Tickets: DuniaTickets.touch (TYPE timeout 100, MAX_LIVE 4, radius<=1). No slow-fall formation exists.
- Rival sects = sekte/diplomasi (GerbangPlan, GerbangSektePlacement, AnggotaSekte anchors sektenpc/*).
- SectRaids.spawnPoint uses dryLand heightmap -> lands under island (needs change).
- HangingIsland(x,z,r,Lv) exists; T9 t9_islands. Waterfall = only ticked fluid.

- Sources verified: Liezi Tang Wen (5 mts, 15 turtles, Longbo, Daiyu/Yuanjiao sank) wiki Guixu/Fanghu/Ao; Huainanzi Kunlun
  Liangfeng/Xuanpu/Upper heaven (Religions 14(11):1366); JttW ch4 South Heaven Gate + register (journeytothewestresearch);
  Shushan 1932 Huanzhulouzhu Emei sword-flying (wiki); Zhu Xian Qingyun leading orthodox faction (wiki); Martial Peak
  Lingxiao Pavilion name only. NOT verified (do not cite): Lingxiao Hall in JttW, Zhu Xian Rainbow Bridge/Tongtian, "Lingyun".
- Design decisions: sky family templates 11-19 (SectTemplates.SKY_FIRST), stele ascends to island throne (Tahta Prasasti)
  via relocation; sky site in TETHERED mode before ascent (steleOk = ground hall); ground site -> Kaki Gunung.
  Validator.sky; BuildPlacer allow stele.below() for sky; new pkg sekte/langit + client/sekte/langit; tests via
  DuniaCommands.test("langit_sekte"); config SekteConfig.section("langit").
## Next step
None (design finished). Implementation modules 37B (first: core API), 37M, 37V, 37G per 37.9; progress files docs/progres/langit-<B|M|V|G>.md.

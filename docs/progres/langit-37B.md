# langit:37B progress (DESIGN 37 Sekte Langit, module 37B "Bangun")

Design: docs/rancangan/37-sekte-langit.md (37.4, 37.9.1, 37.9.2). Audit hand-over: docs/progres/audit-integrasi.md sec 4.
No Gradle. LF files (Kultivator.java check). Config section name `sekteLangit` (audit 4.1), not `langit`.

## Checklist
- [x] 1 Core API: sekte/langit/LangitContent; sekte/langit/api/{LangitApi, SkyHallView, LangitIds, LangitKeys,
      LangitConfig (section "sekteLangit"), LangitPayloads (Sync/Fx/Saw + onSaw listeners), LangitEvents};
      sect/build/api/FootMode; Kultivator.java 1 line; TemplateExtensions 0 = ground only, SKY_ALL = -1;
      SectTemplates SKY_FIRST/LAST/SKY_IDS/isSky/validAny/brother/rank. Stubs: flow/LangitApiImpl, flow/LangitBangun
- [x] 2 Templates (11-19 -> AwanTemplates.build), TemplateSpec (sky, kaki, skyFloor), EngineApi sky methods,
      SiteView sky/tethered/skyFloorY + STATUS_AWAIT_ASCENT
- [x] 3 awan/AwanTabel, AwanShape, AwanLayout, AwanPalette, PulauAwan, AwanTerrain (layout harness lay/run.sh)
- [x] 4 awan parts all written (Tahta, PagarTepi, JembatanGiok, TanggaAwan, KristalGantung, RonggaAkar, DermagaAwan,
      GerbangAwan, GerbangSembilanLangit, PulauSatelit, AwanHias, AwanPapan) + AwanTemplates A1-A9.
      Dry harness dry/run.sh: ops/units A1 33k/12k .. A9 113k/44k, no outside cells.
- [x] 5 PlanAudit sky rules (Ground record: only the stair foot is ground; AwanPalette.banned; SKY_ANCHORS tahta,
      arrival, tangga_kaki/puncak, feri_atas/bawah; nothing under skyFloor outside the foot; airterjun falls).
      Audit harness scratchpad/l37b/audit (AuditRun 11 19): all sky templates 0 problems except 1 stair shape on A7-A9
- [x] 6 SitePlan / Validator.skyWorld+skyTerrain / Snapshot.sky / Previews sky jobs
- [x] 7 BuildEngine (skies map, foundSky A/B, ascend + rekey, releaseSky, skyUntouched, footAction, hasFoot,
      retether, skyInfos, sky upgrade previews) + BuildPlacer + Site + Showcase + CellIndex.rekey + Scheduler
- [x] 8 Founding (Pending.sky, previewSky, offering on confirm/refund, height buttons) + client TemplateScreen
      Darat/Awan tabs (glaze coins = height on a sky preview) + SiteHologram drawSky (foot box, tether, throne y);
      BuildInfo catalog sky_on/sky_gate/sky_max_b/sky_t/sky_terr + stats 11-19; SectBuild ops langit, langit_tinggi
- [x] 9 LangitAngkat (HallBridge registers it before Relocation), LangitSesaji, LangitPlan, LangitRancang (gate of
      37.3.3 + /kultivasi aula langit node + uji sekte langit), Upgrade family/realm rule, SectBuild rank fixes,
      SiteStatus status 11 key, HallBridge arrival defers to 37G for sky halls, darat_<i> anchors (aw_t4_pagar)
- [x] 10 RuangUdara + spawn shadow (BreakRefund.spawnBlocked, called by SectSpawnMixin); LangitApiImpl empty when off
- [x] 11 lang blueprint_awan (161 keys, tools/gen_blueprint_awan_lang.py, identical keys, no clashes)
- [x] 12 line endings (Sink/PlanAudit/Previews/Site restored to CRLF as in HEAD) + full javac main+client: 0 errors

## Next step
Done. Final report only. Lead: Gradle compile+jar once (never run here).

## Open problems / notes
- Design footprints (= land sibling) cannot hold satellites for small templates: sky footprints are derived from the
  island layout (AwanLayout) instead; territory = max(sibling R_t, layout reach + 4). Documented in AwanTabel.
- Anchors with a direction suffix (dermaga_<i>_<p|s|b>_<d>, airterjun_<i>_<d>) are template-local in the plan;
  LangitApiImpl.anchors() must rewrite the suffix to world compass by site rotation (37M/37V read world).
- Glow lichen/vines are banned in builds (21.5): underside lit by froglights/glow berries instead.
- Polished andesite wall does not exist in 26.3: balustrade = diorite wall + quartz pillar posts.
- A1 has no separate main hall (aula gilded at T6) and no pagoda; asrama moved to T2 ring (T1 core small).
- Parts read ctx.terrain(); sky plans get a synthetic terrain (island floor where an island is planned, the real
  snapshot under the stair foot) so Hall/Paving/StairFlight foundations never reach down to the ground.
- Satellite fixes after the audit: walk lantern r-4, pines only on the island, kebun lanterns on the right, petak grid
  centred (1x4/2x3/2x4/3x4) with gardener k after bed 2k-1; bridge landing keeps the rail columns off island ground;
  root strands max 7 (skyFloor = floor - 9 holds); mist-fall ferns get a moss footing.
- Known: A7-A9 one asrama eave stair touches the Cloud Stair roof (vanilla would make it inner_left). Ground templates'
  audit "anchors" findings (tower/library distance) are pre-existing, not 37B.

# sekte:G — Ujian Tangga Seribu (DESIGN 36.2.4, 36.4 Modul G)

Status: DONE (run 1)

## Decisions (keep!)
- Candidates.java (G-owned) gets a hook block: interface `StairTrial` + `setStairTrial`, public facade
  (hall spot, untested candidate bodies, payShard, revealClimb, busy) — old code never imports sekte.tangga.
- startTest: when trial set && SekteApi.tangga().hasStair(owner) -> G climbs ALL untested candidates present
  (one amethyst shard each, same as old cost); verdict formula untouched. accept(): busy ids refused; after
  recruit -> trial.accepted(...) (Bond +5 Puncak, Ingatan, honored). extra.tangga {l,v,d} written into rec data
  at reveal (survives into the disciple).
- hasStair = component sx_tangga_batu DONE (EngineApi.components, cached 100 t) or template 1 with tier>=2.
- Geometry JalurTangga (shared, pure): origin = anchor tangga_proyektor (fallback arrival/stele), F = stele->origin
  horizontal; lead-in 3 b along F then conical helix centre O+10F, r 7->4.5, 2.25 turns, 24 high; 6 lanes.
- Timeline 1500 t: gather 100, climb 1040 (verdict L = 300*tiers + rand(0..99) + Tekad, cap 1000), finale 60,
  announce 300. Illusions at 300/600/900 kneel 40 t. v: 3 L==1000, 2 L<100&&Tekad>=70, 1 Tekad<30, else 0.
- Harness: scratchpad/tanggaG/h (run.sh <t> gsearch|plan, JOPTS -Dfront=10) dry-runs templates with my extension.

## Checklist
- [x] 1 Read core + Candidates + build API + render pattern (SigilRenderer) + SekteScenes/SektePoses
- [x] 2 BatuHatiKomponen.java: T2 (±2,9) T3 (±2,10) court; T4 (±3,23) T7 (±3,44) T8 (±3,52) T9 (±3,59) natural+1x1 TerrainZone+plinth;
      T5 (±3,26) approach; T6 (±3,33) tier 4 (forecourt paved at T4). Harness plan1.txt: 0 overlaps (only T9 finale petals
      skip a stela cell; T5 t4_walls CUT kept), 0 unknown blocks, anchors ok
- [x] 3 Server: JalurTangga, TanggaConfig, TanggaPayloads, Tangga, Pendakian, UjianHati, TanggaCommands (javac 0 errors,
      scratchpad/tanggaG/jc.sh + log1.txt) Tangga, TanggaConfig, JalurTangga, Pendakian, UjianHati, TanggaPayloads, TanggaCommands
- [x] 4 Candidates.java hook (LF): StairTrial iface, Climber record, setStairTrial, hallLevel/hallStele/recruitPaused,
      climbers(owner, first, max), payShard, revealClimb; busy checks in startTest/accept/dismiss/pause/infoTags
- [ ] 5 Client: TanggaClient, TanggaIlusiRenderer, labels, pose 21, scene 130
- [x] 6 Textures: tools/gen_sekte_tangga_assets.py -> textures/sekte/tangga/{step,riser,mist,peak,face,ring}.png
      (checked with Read via scratchpad/tanggaG/preview.py -> tex_preview.png)
- [x] 7 Advancement sekte/tangga_selesai.json; lang-src/sekte_tangga.{id_id,en_us}.json 68 keys identical (generator
      scratchpad/tanggaG/gen_lang.py), no dupes, all code keys present
- [x] 8 Wire: SekteContent + SekteContentClient lines (LF kept)
- [x] 9 javac 0 errors (log3.txt) incl. NoAI pin + crash-recovery mark (extra.tangga naik/ng/na, ENTITY_LOAD recover)
- [x] 10 Self-review pass (timeline, abort/settle, expiry, NoAI restore, LF everywhere) + final report

## Next step
None (job finished). Finish step: Gradle compile + jar; in game `uji sekte audit 2..9`, `uji sekte pamer <t> tingkat 2/4`,
`uji perguruan tangga info|proyeksi|mulai 6`.

## Open problems / deviations (in report)
- T2/T3 have no band outside the gate: stelae stand in the court behind it; projector anchor at z1.
- T6 component is tier 4 (forecourt paved at T4), others tier 2. No 3x3 inlay (axis paving owned by t1_axis).
- Test arg order `langkah <0-1000> <nama>` (name is greedy).
- Proposed voice lines: disciple.<v>.climb_peak, disciple.<v>.climb_illusion (see report).

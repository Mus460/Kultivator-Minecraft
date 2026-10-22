# sekte:W progress (Modul W Kebun Obat & Tambang Roh, DESIGN 36.2.10 + 36.4 W)

Run 1 started 2026-10-22. Run 2 (resume) 2026-10-22: client work.

## Checklist
- [x] 1 Study core (sekte/*, api, TemplateExtensions/TemplateSpec, Duties, Storehouse, Halls, HerbKind, FormModels, SektePoses, SekteScenes, HubCards)
- [x] 1b Layout harness in scratchpad kebunW/ (run.sh, src/{DumpW,SoftW,ViewW,PickW2,UsedW}.java): free-cell picks per template
- [x] 2 KebunKomponen (sx_kebun_petak T2-T9 tier 3, sx_kebun_tambang T4-T9 tier 4): PlanAudit with ext == baseline (0 new violations), UsedW clashes 0 on all 8
- [x] 3 Server: KebunConfig, TabelHasil, PetakHerba, Pertumbuhan, TambangRoh, GiliranTambang, Kebun (init+service+tick+useBlock+info), KebunCommands, KebunPayloads, KebunItems (inti_roh_gunung) - javac clean
- [x] 4 Wired SekteContent W line; Duties.lumbungCount (read-only) added; advancements kebun_panen/kebun_tambang (granted in Pertumbuhan/GiliranTambang)
- [x] 5 Client client/sekte/kebun: KebunClient (receivers, hub card sekte_kebun_tambang, scenes 190/191, pick sounds, gourd drops), KebunScreen (2 pages, picker), TanamanRenderer (pieces per stage, glow/dust), Kuas, KebunFx, KebunPoses (15/16 + props); wired SekteContentClient line
- [x] 6a Assets via tools/gen_sekte_kebun_assets.py: 22 pieces (pick, gourd, tunas, muda, <herb>_kuncup/_mekar x9), 15 textures item/fx/sekte_kebun_*, textures/sekte/kebun/{glow,debu,sinar}.png; previewed (scratchpad kebunW/preview.py)
- [x] 6b inti_roh_gunung item json/model/texture (in generator)
- [x] 7 Lang lang-src/sekte_kebun.{id_id,en_us}.json, 146 identical keys (generator tools/gen_sekte_kebun_lang.py); no duplicate with other fragments; every key used in code present
- [x] 8 javac 25 self-check (scratchpad kebunW/jcW.sh, -sourcepath): W server+client 0 errors; SekteContent+SekteContentClient 0 errors; all W files LF; server package has no client refs

## Layout decisions (template-local; level)
T2 plots: flush 4 in a row at pondside x-14..-11 z-7 (no free 5x5 anywhere). T3: flush 2x2 x13..17 z-16..-12 (stair feet).
T4: plots 3x2 raised x20..26 z-4..0 (hero pine lawn); mine 5x4 x-20..-16 z10..13 facing S.
T5: plots 3x2 x14..20 z-17..-13; mine x-18..-14 z-17..-14 S. T6: plots 4x2 x30..38 z-47..-43 (fitted zone); mine x39..43 z-49..-46 S.
T7: plots 4x2 x19..27 z-38..-34 (fitted); mine x29..33 z-38..-35 S. T8: plots 2x6 x57..61 z-76..-64 (zone rear); mine x58..62 z-62..-59 S.
T9: plots 6x2 x63..75 z-97..-93 (fitted); mine x76..80 z-100..-97 S.

## Next step
DONE. For the finish step: Gradle compileJava/compileClientJava/jar; `uji sekte audit 2..9`, `uji sekte pamer <n> tingkat 4`
to see beds/mine in game; check the herb pieces sit on the soil (TanamanRenderer y offset 0.5) and the pick/gourd prop angles.

## Open problems
- Miners' bodies are hidden only by the enclosed cavity (tambang_dalam_2..4); no client hide hook exists without editing
  the lead's DiscipleRenderer, so slot 1 (adit) stays visible on purpose.

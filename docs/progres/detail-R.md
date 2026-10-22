# detail:R — Reruntuhan, Penjara, Menara detail pass

Found on start: tools/gen_dunia_reruntuhan.py already had (uncommitted, from an earlier cut-off run) the dressing
helpers, biome palettes penjara_gurun / menara_sakura / menara_sabana, and gua variants b (gerbang_b, kamar_*_b,
akhir_b). Not yet wired into build()/pools, prison/tower not yet varied, nothing regenerated.

## Checklist
- [x] A1 gua variants wired into build() + pools (gerbang/gerbang_b, kamar_*/kamar_*_b weight 5+5, akhir/akhir_b); anchor data `look` (0 batu,1 pustaka,2 kuil,3 taman)
- [x] A2 prison: PRISON_FAMS {'' , _gurun}; kuil/sumur/aula(+_gurun); structure penjara_iblis_gurun (badlands) in the same set + tag; paifang, lions, braziers, bells, talisman banners, candles, bones, camp
- [x] A3 tower: TOWER_FAMS menara_seratus/_sakura/_sabana (own structures, one set, tag menara); tier 1 forecourt (COURT 12: paifang, pools+railings, toro, censer, lions, trees, banners), hall dressing; storeys: railing, eave lanterns/bells, banners; lobby banners/toro/censer
- [x] A4 regenerated (82 templates, read-back 0, structgen valid), harness run_r.sh ALL OK, pool/template/jigsaw refs cross-checked (scratchpad r_refs.py)
- [x] B  voices: gua.ancestor_trial (RoomRun attempt start, inner, 10 min cd; HallRun unproven), menara.shadow_self (CerminPuzzle mirror, TowerRun floor 100), penjara.free_at_last (KingBrain rise end), penjara.resealed (CoreRun.resealed, inner, speaker lang seal_voice), menara.next_floor (TowerRun.cleared, inner, speaker tower_voice). Pending lang keys in scratchpad r_lang_add.json
- [x] C  nameplates+titles: new client RTitles.java; Rig/Golem/Demon/Shadow/Soul renderers override shouldShowName/getNameTag/extractNameTags (scoreText = title, 'Murka' when enraged); Rig idle breathing. Behaviour: Prisons.eclipse(level); LesserBrain eclipse restless (leash x1.5, pace 1.45, melee 14, FLAG_ENRAGED, growl); HallRun realm greeting (senior/peer/junior) + idle musings (3 per tier, 60 s per player)
- [x] D  achievements ruin_kuno (HallRun), heart_mirror (CerminPuzzle), tower_50 (TowerRun), hong_xin (ShrineRun.pull), eclipse_seal (CoreRun.reinforce under eclipse) + JSONs; tests: uji penjara cari gurun, uji penjara gerhana <bool> (Prisons.testEclipse), uji menara cari sakura|sabana; help topics extended; lang merged (374 keys both)
- [x] E  RFx.ROOM_AIR (28): RoomRun pulses every 2.5 s near players (look from anchor data); RFxClient Air list (max 8, 3 s), 2/5/10 per s: library gold dust, shrine incense from corner braziers, garden petals/leaves

- [x] F  SoulRenderer talk gesture (ITEM arm pose alternating + nod via state while glow>0)
- [x] G  javac (runtime epsilon) of all changed R files against cp.win: clean (output scratchpad/rjc)

- [x] H  guardian regalia in tools/gen_dunia_reruntuhan_textures.py (quadruped saddle/collar/bell/cuffs; textures gd_saddle_red, gd_saddle_violet, gd_ribbon, gd_bell); regenerated, 0 model errors (max 15 elements)

## Next step
Done. Final report.

## Left / notes
- Not run in game (Gradle forbidden here): nameplate heights on the big rigs, regalia placement, forecourt look.
- Pagoda cloud ring still not sky-projected beyond 256 b (wave-1 leftover, unchanged).
- Kitab Panduan (module T / GuideChapters): c14.penjara checks only adv prison_sealed; its text also names Perkuat Segel and DESIGN allows prison_slain. c13.gua text says hills and mountains only (Gua Kuno is badlands / deep dark).

## Open problems
-

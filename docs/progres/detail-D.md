# detail:D progress (module D: Dunia Bawah 63, Laut Tanpa Batas 64)

Found on disk from an earlier interrupted run (no progress file existed, ~07:00): server edits in Dimensi, DimensiActions,
DimensiCommands, DimensiNet, DimensiNpc, TandaEntity, bawah/{ArwahEntity,BawahAnchors,BawahPeople,Courts,GhostQuests,
SoulJourney}, laut/{Kepiting,Laut,LautAnchors,LautNpcEntity,SeaGate,SeaPeople,UlarLaut,Yaksha}; new DimensiChatter,
DimensiSky, DimensiVoice; client DimensiState, render/{Arwah,Headwear,HumanNpc,LautNpc,Tanda,Yaksha}Renderer.
Content: Bai/Hei Wuchang, fisherman (nelayan), NPC chatter/talk/bow/sit poses, name titles, duel clash fx, sky reactions,
Meng Po mastery refund, court NB rewards, Mirror deeds, Dragon King armoury, 4 island kinds, new advancements.
Being verified and completed, not rewritten.

## Checklist
- [x] 1 Verify APIs of the found code (Voice, VoiceEvents, Mastery, Comprehension, ArtifactItem, DuniaSites, DuelRules,
      Kehancuran.selfStatus, MC ArmPose/render state/PoseStack/Items.IRON_CHAIN): all exist
- [x] 2 Worldgen (tools/gen_dunia_dimensi.py, regenerated, TplCheck 52 files 0 problems, WgCheck ALL OK, jigsaw check 0):
      teras (wuchang anchors, lions, censers, banners), naihe (river candles, willows, lions, censers), Meng Po (kitchen,
      bowls, banners, queue ropes), 10 courts (ledgers, banners, mat, lions, censer, ridge pearl), desa jalan_2,
      rumah_4 (ancestral hall), rumah_5 (paper shop), kuil_pagoda, kuil_rawa (own structure, swamp biomes), makam_2;
      islands split into laut/pulau_{herba,pertapa,pasar,karang} (2 forms each, *_2), tags laut/pulau_<kind>,
      satelit_nelayan + satelit_kapal_karam, dermaga nelayan nook + lions, loot pulau/kapal_karam,
      10 new advancements (also written by the generator). Harness: scratchpad/final/wg/run_d.sh
- [x] 2b Textures: headwear bands 12 paper / 13 ink, skins wuchang_putih/hitam (ArwahRenderer uses them)
- [x] 4 Client: DimensiScreen (fisher, wuchang, armoury page, deeds + kehancuran lines, 2-column strips), DimensiFx
      (clash sparks, ghost fade, monster down, eclipse motes, spirit tide, meteor streaks); SeaPeople.buyArm realm check
- [x] 5 Lang: +250 keys in lang-src/dunia_dimensi.{id_id,en_us}.json (chatter 13 roles x 8, titles, fisher, wuchang,
      armoury, deeds, sky, uji, voice captions, 10 advancements, wuchang Sumber); script scratchpad/dD/lang_d.py
- [x] 6 Voice: DimensiVoice uses bawah.*/laut.* (in sounds.json) with fallbacks; heaven.langit.* to players below/at sea.
      NOTE VoiceEvents does not register the phase-34 groups yet (not D's file): fallbacks play until it does
- [x] 6b javac check (Java 25 jdk.compiler, no Gradle): scratchpad/dD/jc.sh, main set without client classes: 0 errors
- [x] 7 Help topics (kultivator.screen.help.dunia_bawah / laut_tanpa_batas) extended in both languages; Panduan c14.*
      (module T's file) checked: still accurate; uji: bawah wuchang|celoteh|langit|bentrok, laut raja|nelayan,
      laut pulau cari <jenis> now per kind, bawah kuil cari covers kuil_rawa
- [x] 7b Extra: sea horizon haze band (DimensiHud, HUD gradients from pitch/FOV, off on Minimal/underwater); night
      routine (fisherman and merchant sit at night); armoury refuses below palaceMinRealm
- [x] 8 Self review: javac clean (main without client classes), line endings unchanged, ItemStacks only at runtime,
      generator deterministic (md5 identical on rerun), TplCheck/WgCheck/jigsaw check clean

## Next step
Done. Finish agent: Gradle build, in-game checks (see open problems).

## Open problems
- VoiceEvents (voice module) does not register bawah.* / laut.* / heaven.langit.* yet: DimensiVoice plays fallbacks.
- In-game checks still needed: worldgen pack in old worlds, Dermaga look, head pieces/hats on the NPCs, new templates,
  turtle island budget, the horizon haze band alignment.
- DESIGN.md (Dermaga Tiba, 3 paid errands, island split) is not a module-D file: left for the finish agent.
- Soul see-through for other players needs another module's AvatarRenderer mixins: not done.

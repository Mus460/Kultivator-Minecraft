# Progres detail:K (Kota & NPC detail pass, DESIGN 34.5.1-34.5.4)

Run 1 (2026-10-22). No earlier progress file.

## Checklist
- [x] 0 orient (harness: scratchpad/final/wg/run_k.sh = TplCheck + WgCheck for kota)
- [x] 1 structures: an interrupted earlier run had rewritten tools/gen_dunia_kota.py (4 biome looks sedang/salju/sabana/
      sakura, gerbang_2, jalan_lurus_4/5, jalan_silang_2, jembatan_2, tembok_ujung_3, *_2 buildings, rumah_halaman,
      rumah_toko, hias_*; curled eaves, chiwen, dougong, furnished rooms) but its regeneration was cut off at sabana.
      This run: fixed clipped roofs/trees (jalan sy 10, silang 12, jembatan 14, rumah_toko 19, abadi_rumah +2, tower in
      tembok_ujung), beam_band axis only on logs/pillars (gold_block[axis] bug), pond clay only inside; regenerated all
      222 templates; TplCheck 0 problems, WgCheck ALL OK (4 town structures in one set).
- [x] 2 lang: move scratch lang_kota.py to tools/gen_dunia_kota_lang.py (verified: reproduces repo JSON byte-identical,
      614 keys); all new keys go there, then run it
- [x] 3 KotaVoice.java (new kota.* keys, fallback when VoiceEvents lacks them; note: VoiceEvents does NOT list
      kota.* yet, the orchestrator/voice owner must add them) used in BalaiLelang, KedaiTeh, KotaAbadi, PasarGelap,
      Pengembara, Saingan, Kota.interact (scorn, guard halt, tea welcome, peach keeper, lecture)
- [x] 4 KultivatorNpc (FLAG_TALK/BOW/GAZE/WORK, DATA_LINE say(), belowNameDisplay realm line, arm(), Duelist),
      NpcGear.java (weapons), KotaDuel.java (profile/accepts/ended/challenge/maybeOffer). Was: FLAG_TALK/FLAG_BOW, synced speech line (DATA_LINE), weapons per role (no attribute modifiers),
      draw/sheathe, implements duel api Duelist (profile for PENGEMBARA/SAINGAN/PENJAGA)
- [x] 5 NpcLife.java (+ hooks: NpcAi.check, Kota.init, KotaAbadi lecture bubbles): daily routines, NPC-NPC chatter, sky-event (DuniaEvents.SKY_EVENT) and realm reactions, bows
- [x] 6 KotaDuel (new, + MeetingPage 'Duel Pedang' button, Pengembara verb duel, the only file touching duel session API): meeting "Duel Pedang", npcOffer, rewards on end
- [x] 7 leftovers (Pengembara knownOf/teach TEACH_POINTS=10 via Mastery.add TEACH; KotaAbadi.peachConversion
      +3 conv; PasarGelap view 'eclipse' + TownPages notice; KotaCommands.findTown tag #kultivator:kota; old-world note: TODO in report): discussion +1 mastery option, peach +3% Esensi Abadi, eclipse surcharge notice in Gelap tab,
      kota cari finds all 4 looks (tag), old-world pack note
- [x] 8 client (KotaGear.java 21 outfits x solid/cloth layers, KotaGearLayer.java, NpcPoses.java body language as
      CastPoses.Snapshot, NpcRenderer extract/bubble, KotaClient register, KotaFx body particles, texture
      tools/gen_dunia_kota_gear.py -> textures/dunia/kota/entity/perlengkapan.png; FLAG_LEADER set in Saingan). Was: KotaGear model layers + texture (tools/gen_dunia_kota_gear.py), KotaGearLayer, NpcModel idle/talk/bow
      poses, PoseRenderData.extract in NpcRenderer, title line + speech bubble text
- [x] 9 lang (tools/gen_dunia_kota_lang.py now 837 keys; regenerated JSON), advancements wanderer_duel,
      wanderer_discussion, immortal_peach, immortal_duel (json), help texts extended. Was: achievements (new adv json), uji commands for new pieces, CheatHelp chips?(not mine) , help texts
- [x] 10 visuals (KotaFx.body: meditation qi, smith sparks, tea steam, bow glint): chatter/bow particles, guard torch?, within budgets
- [x] 11 self-review: javac (scratchpad/k/jc.sh) clean for all K files (probe confirmed errors elsewhere are reported;
      the only errors are in client/duel/fx/DuelCamera.java, not K). NpcGear strips ATTACK_RANGE/PIERCING/KINETIC/WEAPON.
      Old-world pack: verified BuiltinModPackSource.shouldAddAutomatically()=true + MinecraftServer auto-adds (no action).

## Next step
Done. Report written. Not done: Gradle build/playtest (finish agent); Panduan texts (T) and CheatHelp chips (not K files).

## Open problems
- VoiceEvents.java (voice owner) does not list kota.* keys: KotaVoice falls back to old lines until it does.
- KotaDuel 'beaten' = health share reached yield line + 0.08 (onDuelEnd(false) mixes loss/draw/cancel).
- Panduan (dunia_panduan, module T): c13.kota names only plains/forest/savanna; c13.pengembara lacks Duel Pedang and the
  +10 mastery reward; c14.kota_abadi lacks the peach's +3 % Esensi Abadi.
- CheatHelp chips for new uji nodes (kota barisan/obrol/gerak/langit, kota cari <tampilan>, pengembara duel) not added.

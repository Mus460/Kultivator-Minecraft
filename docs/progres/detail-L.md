# detail:L — Langit & Cuaca detail pass (DESIGN 34.5.8-34.5.10)

Run 1 (07:00-07:12, no progress file) left most work on disk; run 2 (14:20) reconstructed it from git diff.

## Done (run 1, verified on disk)
- [x] Meteor Agung: GreatMeteors.java, MeteorRohEntity great flag, LangitData greatSeed, KIND_GREAT_INCOMING/IMPACT, Inti Meteor Agung item (600 Qi + Space Dao 1)
- [x] Eclipse-touched spoils: Kristal Gerhana item (35 %, Shadow Dao 0.3, 5/day), LangitFeats eclipse_hunter
- [x] Treasure guardians (wave-1 leftover 2, own part): TreasureGuardians.java (biome kinds, name plates, leash, seal, sigils)
- [x] Achievements: great_meteor, dew_full, purple_dawn, eclipse_hunter, sky_all, treasure_guardian, treasure_heaven
- [x] Uji: langit agung|gerhana_mob|catatan, harta_lahir penjaga, cuaca ramalan
- [x] Client: Tiangou eclipse phases, radiant/fireballs, sky ripple, biome mist tint, light shafts, purple Qi stream, guardian sigils, crater glow (LangitRenderer/State/Particles/Sky/Sink/Hud), LangitAmbience sounds
- [x] Textures: crater_decal, light_shaft, moon_shadow, qi_wisp, rune_ring + item icons (gen_dunia_langit_textures.py)
- [x] Help topics + lang (166 keys, parity ok)

## Run 2 checklist
- [x] Compile check with scratchpad/langit_jc/check2.sh (javac only): clean except the 2 known harness-only EntityRenderers access errors
- [x] Voice: Langit.heaven sends heaven.langit.* as a VoicePayload when VoiceEvents lacks them (Langit.java)
- [x] Inspect textures; Kristal Gerhana icon redrawn as a 3-shard cluster (gen_dunia_langit_textures.py, item/kristal_gerhana.png)
- [x] Realm-band lines at sky event start (SkyEvents.begin, kultivator.langit.sense.<kind>.<1-4>) and treasure-above-realm warning (TreasureBirths, kultivator.langit.harta.above)
- [x] Reviewed client renderer budgets (every layer through EpicBudget.takeQuads/takePillar, tier tables)
- [x] Final audit: lang parity 179/179, advancement + item keys present, LF kept, 3 Sumber lines kept, javac clean

## Next step
DONE. Only items for other owners remain (see Open problems).

## Open problems
- VoiceEvents (not owned) registers no dunia keys; exists() is false for heaven.langit.*.
- R stone construct / B young Jiao guardians need R/B to listen to TREASURE_BORN.
- Twin Moons x1.5 only on the per-second site rate (Comprehension is not owned).
- Kitab Panduan c13.harta (module T) does not say guardians seal the lotus; c13.langit does not mention Meteor Agung / Kristal Gerhana.

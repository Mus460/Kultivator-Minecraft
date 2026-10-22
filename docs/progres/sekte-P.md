# sekte:P — Kepribadian & Kesetiaan (DESIGN 36.2.1, 36.4 Modul P)

Status: DONE (run 1). Own javac harness: scratchpad/sekteP/jc.sh (copy of lead's, own args file).

## Decisions (report these)
- Loyalty = round(0.55*K + 0.45*(30 + 0.7*Bond)) (+15 Setia): Bond starts at 0, so the literal 0.55K+0.45Bond made
  every new disciple Gelisah (38). Sealed (SoulSeal) and heir: loyalty floor 30 (never defy, never leave).
- Master cannot hurt own disciple (isInvulnerableTo) -> DIPUKUL detected with Fabric AttackEntityCallback (not in duel).
- Berani retreat -0.05 impossible (hook only raises); Penakut +0.10 -> 0.35.
- MEREKRUT (D) never makes a disciple leave at once (rule 36.0.4): 20% of Gelisah: K-12, memory, sect saved as `bujuk`
  (10 days) -> later departure becomes BERKHIANAT to that sect.
- Leaving frees the slot by removing the entry from the master's list (CultivationData extra "disciples"/"list").
- Rebel duel winner = higher health share when the session is decided (no public winner accessor; no T dependency).

## Checklist
- [x] Read core + APIs (DESIGN 36, sekte/**, DiscipleHooks, Disciples, DiscipleScreen, HubCards, duel session, KotaFx quads)
- [x] Server (javac clean): sekte/kepribadian/{KepribadianConfig, Hati, Watak, Kepuasan, Kesetiaan, KepergianMurid,
      YangPergi, KepribadianPayloads, KepribadianCommands, Kepribadian}.java
- [x] Wired: SekteContent.java line `dev.kultivator.sekte.kepribadian.Kepribadian.init();`
- [x] tools/gen_sekte_kepribadian.py (run): lang-src/sekte_kepribadian.{id_id,en_us}.json (209 keys, identical, no clash),
      textures/sekte/kepribadian/{gelembung,sifat}.png, textures/item/fx/sekte_kepribadian_kain.png (checked with Read),
      items/fx + models/item/fx/sekte_kepribadian_buntalan.json, data/kultivator/advancement/sekte/kepribadian_{sifat,puas,rukun}.json
- [x] Fixes: no PENDING removal while iterating; defection deferred to the departure tick (Fase.SEGERA)
- [x] Client (javac clean): client/sekte/kepribadian/{KepribadianClient, HatiTab, GelembungPikiran, SuasanaScreen}.java;
      poses 19/22, hub card sekte_kepribadian_suasana (MURID), scenes 100-102
- [x] Wired: SekteContent + SekteContentClient (one line each)
- [x] javac 25 compile check (no Gradle): all P files + SekteContent/SekteContentClient, 0 errors
- [x] Self-review (CME fix, deferred defection, rebel flow, sulk/restore, slot freeing)
- [x] Final report

## Notes for others / finish step
- X (C36.P3/X4): the gone list is hall section `sekte_kepribadian` -> `pergi` (entries soul, name, cara, hari, sekte,
  tablet); fire MISSION_RETURNED with storyArgs[0] = "pulang:<soul>" and P calls Disciples.arrive.
- G: P mends a flaw when G writes an Ingatan memory `tangga.*` after a re-trial with resolve() == 3 (once per 30 days).
- E: TetuaService.officeFactor(owner, TETUA_HUKUM) multiplies P's daily rises (clamped 1.0-1.5).
- New voice lines proposed in the report (sulk, rebel, submit, leave_night).

## Next step
None (job finished).

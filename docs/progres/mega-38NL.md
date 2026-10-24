# mega-38NL (wave W1, 38NL NPC life) — checkpoint

Scope (jadwal W1 row 38NL + sec 5 note): 38.N minus 40.7.4 transfers + 38.T.14.3 row NL (minus klip/prop = 40).
Owns: `dunia/kota/{NpcRole,NpcAi,NpcLife}.java`, `dunia/tempat/hidup/**`, `client/dunia/tempat/hidup/**`,
`lang-src/tempat_hidup.*`, data `data/kultivator/tempat/jadwal/**` (optional schedule overrides).
Must NOT touch: `KultivatorNpc` (40I), `NpcPoses`/`NpcRenderer` (40G/40R), `Tempat.java`, other modules' files.
W0 done by 38NL-0 (NpcRole 102 roles, Penduduk, HidupApi, Hidup, lang roles) — not redone.

## Checklist
- [x] Read APIs: KultivatorNpc (40I fields/goals), NpcAi, NpcLife, SosokApi, GerakId, PropId, TempatApi/Titik/TitikTemplat,
      TempatConfig, TempatCommands, DuniaBudget, Towns/KotaData, kota-W0.md
- [x] HidupConfig (section `tempatHidup`, 38.N.8 + body caps per kind)
- [x] Kegiatan record, KerjaPeran (work of all 102 roles), JadwalData (reload `tempat/jadwal/<jenis>.json`),
      Jadwal (38.N.3 table, week/season, weather, sky, events incl. `tonton:x,z`); Penduduk.tamu()/TAMU + slot javadoc
- [x] PendudukGen (role mixes: capital 650, towns 40, villages 6/18/36, clan 30, compounds 40/80/120, posts, 5 themes;
      households, guests) + PendudukData (SavedData luka/mati->newcomer/kenal/tanda) + TempatHuni (titik cache, slots, goals, locks)
- [x] TubuhPenduduk (goal walk L0 nav/L1 move, arrival clip+props, sela, vendor calls, chats w/ gestures, bargaining,
      chase, paired sparring, player reactions, crime, danger flee/fight) + Pemanggil (tick, places, bodies, caps,
      weather, far crowd + stands) + PendudukSnapshot payload
- [x] NpcAi (HoldGoal off, HomeGoal -> TubuhPenduduk.mauJalan/mulaiJalan/terusJalan) + NpcLife.tick -> TubuhPenduduk.tik
- [x] Saksi (data witnesses 38.N.6) + HidupApi (penduduk real, pendudukDari, saksi -> data, realmFor pkg) + Hidup.init
      (config, JadwalData, payload, Pemanggil, commands, ACARA/KEJAHATAN/damage/death events)
- [x] Client: HidupClient (receiver, COLLECT_SUBMITS, tick, disconnect), ImpostorRenderer (far crowd smoothing + stands,
      quality caps 0/32/48 & 0/64/160, fade 120-128, behind-camera skip), BonekaImpostor (6/8/12 boxes, region A UVs)
- [x] HidupCommands (`uji hidup tempat|jadwal|jam|klip <id>|klip semua|prop|impostor|saksi|bahaya|cuaca`), previews in Pemanggil
- [x] Lang tempat_hidup: +170 keys (kegiatan 58, kelompok 10, seru 20 roles x3, sapa/marah/takut/buronan/pencuri/lari,
      tawar, uji 18); 257 keys identical id/en, LF, no collision (kultivator.hidup.* only here)
- [x] Self-review: javac (MC 26.3 + Fabric cp, --release 25, scratchpad nl38/jc.sh) 0 errors on all 22 owned files;
      grep: no client refs in server, no setBlock/LightningBolt/weather/time/tick-rate, no static ItemStack; all LF;
      lang 257/257 identical, no collisions, every literal + dynamic key (kegiatan/seru/kelompok/variants) present;
      HidupCommands kegiatan names now translatableWithFallback (data-defined ids)
- [x] Final report

## Langkah berikutnya
DONE. No data/kultivator/tempat/jadwal/*.json shipped (overrides optional; built-in Jadwal tables are used).

## Catatan / masalah terbuka
- Design: Penduduk rumah/kasur/kerja are SLOTS (titik owner = hash(pos) mod n), stable while chunks load.
- KultivatorNpc untouched: resident ids live in `npc.memory()` (`hd_situs`, `hd_id`); NpcAi HomeGoal/HoldGoal route
  residents to TubuhPenduduk.

## Butuh dari modul lain

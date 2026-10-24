# mega-40I (W1, DESIGN 40 module 40I: identitas & data server) - SELESAI

Scope (jadwal W1 row 40I, sec 5 note, design 40.2, 40.4 server data, 40.6.2 server config, 40.6.4 server commands,
40.7.5). Owns: `Kultivator.java`, `disciple/{DiscipleEntity,Disciples,DiscipleNames,DuelRules}.java`,
`voice/VoiceDisciples.java`, `dunia/kota/{KultivatorNpc,KotaNames}.java`, new `sosok/**` (server), lang `sosok.*`,
`tools/gen_lang_sosok.py`. Not touched: NpcRole, NpcAi, NpcLife, client files, DiscipleCultivation, DuniaCommands. No Gradle.

## Checklist
- [x] Read W0 sosok/* + owned old files. EOL: ALL owned old files are LF in HEAD and tree (checked with
      `tr -cd '\r' | wc -c`; `grep -c $'\r'` lies in this shell). Everything kept/written LF.
- [x] SosokConfig (DuniaConfig section "sosok"; rasioPerempuan .5, anak .10, dewasa .08, 48/160 days, skalaTubuh,
      variasiTinggi; `skala(sosok)` used by SosokApi.terapkanSkala)
- [x] SosokGen: K/U policy of every look key (40.4.1 A-D 124 keys incl. liar.0-7, murid.0-5 + murid.calon/saingan/
      bayangan/klon, jalan.*, kelompok.* + kelompok.SIPIL fallback); untukNpc/untukRupa/kelamin/usia/dariKebijakan
      (sex bits look>>>24&0x7F < p*1.28, age bits look>>>2&0x7F, build/seed from Mth.murmurHash3Mixer(look)),
      untukMurid (config shares), dariTubuhLama(name,skin,slim) deterministic, rupaNpc() gallery list
- [x] DiscipleNames: 72 names, pools 36/36 (FEMALE_OLD + 36-55 P, 56-71 L), pick(r,taken,kelamin), kelamin(i),
      kelaminKunci(key); old pick(r,taken) kept (Candidates) - the body then takes the name's sex
- [x] KotaNames: person(look,perempuan), person(look,role), plate via SosokGen.kelamin; person(look) = C50 rule
- [x] DiscipleEntity: implements SosokApi.Pemegang; DATA_SOSOK/GAYA/GERAK/GERAK_T0/PROP/LOOK_AT; getters syncedSosok(),
      syncedGaya(), syncedGerak(), syncedGerakT0(), syncedProp(), syncedLookAt(); setSosokData keeps DATA_SLIM =
      lenganRamping and DATA_SKIN = benih%9; NBT "sosok"/"gaya" (body, tablet, departure; revival clears wound/outfit);
      setupNew(master,data,name,Sosok), setupBody(...,Sosok|null,gaya); tick: SosokApi.tik + SosokUsia.periksa
- [x] KultivatorNpc: implements Pemegang; same 6 DATA_* + DATA_RUPA (String); synced*(), syncedRupa(), rupaKunci();
      setup derives Sosok from role (or rupa key set before), forces voice to sex, SCALE; female() from Sosok;
      setRupaData re-derives unless an explicit setSosok happened (sosokTetap); bicaraSendiri = FLAG_TALK; tick tik
- [x] Disciples: recruit = SosokGen.untukMurid + gendered name; spawnUnowned overload (Sosok, gaya); PHANTOM copies the
      nearest real disciple with the same skin/slim; spawnClone copies source Sosok+gaya
- [x] DuelRules.begin refuses a child (usia ANAK); VoiceDisciples female()/voiceOf from Sosok, SosokApi.bicara(40 t)
- [x] SosokUsia: every 20 t auto wound (HP<35% pincang, DEVIATING habis qi), berkabung during ACT_MOURN, shown traits
      from Hati.traits; once a minute ageing by world-clock day (extra.sosok.lahir/muda): child->youth at 48 days any
      realm, youth->adult at 160 days only <= Qi Refining; never sets old; only takes back values it set itself
- [x] GaleriUji + SosokCommands (`/kultivasi uji penampilan`): galeri peran [1-8] (16 looks x L/P), murid, jalan, usia
      <rupa>, dao, fisik (RIVAL test disciples with physique), acak [n<=32], klip <id|nama> [r], klip semua (24 bodies
      parade 160 clips), klip kelompok tempat|sikap|murid|fungsi|aktivitas (fungsi/aktivitas on test disciples),
      jalan (15 gait profiles walking 12 blocks), wajah (moods, drained face, eyes closed, greeting), pegas (4 runners),
      murid acak|kelamin|usia|jalan|luka|suasana|busana|hari, pratinjau_benih, info, hapus. Cap 64 bodies, 600 s life.
- [x] Sosok.init() (config, commands, gallery driver, stop reset) + Kultivator.java line after Disciples.init()
- [x] lang sosok.{id_id,en_us}.json: 466 identical keys from `python -I tools/gen_lang_sosok.py`; no duplicates in lang-src
- [x] Self-review: scratch javac (Java 25 runtime jdk.compiler, cp = final/wg/cp.win with B0 classes) of all 40I files:
      clean. Scratch test `scratchpad/w1_40I/t/Cek40I.java`: weights sum 128 for 160 rows, all 102 NpcRole have rows,
      warga P .500 A .148 M .204 D .453 T .196, penjaga P .157, fixed-sex rows hold, names 36/36: ALL OK.
      mcsrc verified: Mth.murmurHash3Mixer, getOverworldClockTime, CompoundTag.contains/getLongOr, Entity.entityTags,
      ServerLevel.getAllEntities, Mob.getJumpControl/setNoAi, ClickEvent.SuggestCommand, HoverEvent.ShowText,
      ProjectileUtil.getEntityHitResult, CommandSourceStack.getPlayer, Mth.DEG_TO_RAD/RAD_TO_DEG/atan2.
      No client refs in server code, no static ItemStacks, all files LF.

## Contract notes for 40K/40G/40R/38/39
- Bodies: read `SosokApi.Pemegang` (sosokData, gayaData, gerakData, gerakT0, propData, tatapData, rupaData) or the
  synced*() getters. NPC look key = `npc.rupaKunci()` (role id when DATA_RUPA empty).
- Disciple role keys for 40K resep: `murid.0-5`, `murid.calon`, `murid.saingan`, `murid.bayangan`, `murid.klon`
  (SosokGen.MURID_*), `jalan.<id>`, `kelompok.<GROUP>`, `kelompok.SIPIL`. Client may call `SosokGen.untukRupa(kunci,
  null, look)` for D/Liar/Utusan/Penonton bodies (same policy table as the server).
- Look-key names: `kultivator.sosok.rupa.<key>` for non-role keys; roles keep `kultivator.kota.peran.<id>`.

## Langkah berikutnya
None for W1. D-40 (W3) may tune policy weights; F1 compiles.

## Butuh dari modul lain
- F1/T12 owners (Tournament/Bracket/Rivals/HundredDynasties, read-only now): `Bracket.Entrant` carries skin/slim only;
  rival copies of real disciples get a deterministic Sosok from name+skin+slim (sex right, face not the original).
  To show the exact face, carry `disciple.sosokData()`/`gayaData()` in Entrant and call the new
  `Disciples.spawnUnowned(level,pos,data,name,skin,slim,Sosok.unpack(sosok),gaya,role,anchor)`. Tournament entrant
  selection should skip child disciples (`SosokApi.usia(d) == Sosok.ANAK`), since DuelRules.begin now refuses them.
- 38NL/KotaDuel owner: KotaDuel.accepts should refuse `SosokApi.usia(npc) == Sosok.ANAK` (DuelRules covers registered
  duels only).
- 38NL (Pengembara, Saingan, PapanMisi callers of `KotaNames.person(look)`): use `KotaNames.person(look, role)` so the
  given name always matches the NPC's sex (person(look) uses the 50 % rule; mismatch only for C<p> roles with p != 50).
- 40K: recipe rows for `murid.calon/saingan/bayangan/klon` and `kelompok.SIPIL` (names above).
- 40R: `galeri fisik` / `klip kelompok fungsi|aktivitas` use RIVAL-role test disciples (role RIVAL may be dressed as
  `murid.saingan`; fine for face/physique checks).
- F1: nothing to wire; `Sosok.init()` is in Kultivator.java. Run `python -I tools/gen_lang_sosok.py` only if
  GerakId/PropId names change.

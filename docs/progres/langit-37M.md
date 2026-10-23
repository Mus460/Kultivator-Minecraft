# langit:37M progress (DESIGN 37 "Sekte Langit", module 37M Gerak: movement, access, fall protection)

Doc: docs/rancangan/37-sekte-langit.md (37.5, 37.9.3) + docs/progres/audit-integrasi.md section 4. No Gradle.

## Checklist
- [x] 0 Survey existing code (formasi, kapal, disciple, Sibuk, tickets, FlightLease, entity/renderer registration)
- [x] 1 LangitJatuh + TerjunAwan (+ LangitGerak helpers): sekte/langit/gerak/{LangitGerak,LangitJatuh,TerjunAwan}.java
- [x] 2 formasi/catalog/CatalogLangit + TransportNetwork (AWAN/AWAN_RANGE/AWAN_COST/linked/awanSite) + Limits
      (AWAN_SPEC, AWAN_NODES) + FormasiB 1 line + Formasi.useUnregistered + CoreTicker.outpostHere + gerak/PindahAwan
- [x] 3 formasi/effect/b/JaringAwanEffect (lease, blocker: tier 5, 4 rim poles jaring_0..3)
- [x] 4a Feri server: gerak/{FeriAwanEntity,FeriRute,FeriPanggil,LangitGerakEntities}.java
- [x] 4b Feri client: client/sekte/langit/gerak/{LangitGerakClient,FeriAwanRenderer,GerakQuads} +
      tools/gen_sekte_langit_gerak_tex.py -> textures/sekte/langit/gerak/{feri_awan,awan_puff,cahaya,cincin}.png (checked)
- [x] 5 gerak/LangitDermaga + KapalRohEntity (sailSkyHall, skyDock/skyStage/skyY session fields, tickSkyLeg, cruise
      floor, yaw hold on the last metre, clearSkyLeg in endVoyage)
- [x] 6a gerak/PinMurid (crash-safe pin mark in DiscipleData.extra.sekte_langit) + gerak/PatroliPedang (GUARD_HALL,
      Inti+, Golden Core+, walk to panggung_pedang, lift/out/loop/back/down, lands on raid/duel/hit/teleport)
- [x] 6b gerak/NaikTanggaGoal (new disciple at the foot climbs the real helix: slab scan; hop fallback) +
      gerak/LangitTanggaJejak (seribu_anak_tangga)
- [x] 7 LangitGerak init (+ENTITY_LOAD recover, follow veto) + LangitContent line replaced + LangitGerakClient
- [x] 8 lang-src/sekte_langit_gerak.{en_us,id_id}.json (53 keys, identical, no duplicates) from
      tools/gen_sekte_langit_gerak_lang.py; + gerak/GerakUji (movement half of uji langit_sekte for 37G)
- [x] 9 Self-check: javac 25 clean (scratchpad/langit37m/jc.sh, main without client cp + client); all touched files LF;
      no client class in server code; no ItemStack created; no block writes; final review pass done.

## Dependencies / notes
- Depends on 37B core (sekte/langit/api/*: LangitApi, SkyHallView, LangitIds, LangitConfig, LangitPayloads, LangitKeys;
  sekte/langit/LangitContent). Coded exactly against contract 37.9.1.

## Survey facts
- awan_transfer ground node is outside territory: needs the L5 "outpost" path generalised -> small edits in
  formasi/Formasi.java (useUnregistered: accept awan_transfer when TransportNetwork.awanSite) and
  formasi/core/CoreTicker.java (outpostHere accepts awan). Neither file is owned by another 37 module; documented.
- Formasi.init runs on both sides -> CatalogLangit/JaringAwanEffect must stay common-only.
- startRiding refuses vehicles whose type !canSerialize -> feri type NOT .noSave(); shouldBeSaved()=false instead.
- Disciple activity 25 is not spot-holding; patrol only picks GUARD_HALL disciples (no post/follow goal fights the walk).
- Anchors are id -> BlockPos only: dock ids expected `dermaga_<i>_<p|s|b>_<n|e|s|w>` (fallback `dermaga_<i>`).
- Ship (KapalRohEntity) climbs over cliffs via look-ahead; sky dock = waypoint outside + axis approach.

## Contracts given to others
- LangitJatuh.lastSafe(UUID), catchNow(LivingEntity), protects(hall, entity), addGuestRule(BiPredicate<UUID,Entity>)
  (37G: allied NPC visitors protected while jaring runs).
- JaringAwanEffect.active(owner, gameTime) (37G sync FLAG_NET).
- 37B: aw_t5_jaring should emit the jaring_awan pattern at its eye (FormationRegistry.byId("jaring_awan").pattern()
  .emit(rot)) like aw_t1_formasi_awan does with awan_transfer; rim pole anchors jaring_0..3 (quartz pillar/end rod
  within 3x7x3 of the anchor).
- Advancements granted (37G writes JSON): sekte_langit/tertangkap, terjun_awan, pindah_awan, feri, seribu_anak_tangga.

- 37G: /kultivasi langit feri -> FeriPanggil.callNear(ServerPlayer); missions -> FeriAwanEntity.of(owner).board(entity)
  or FeriAwanEntity.call(level, owner, up).

- 37G LangitTests: GerakUji.jatuh/jatuhMurid/feri/formasi/patroli/tangga return the lines to print.
- 37V: LangitClient must call dev.kultivator.client.sekte.langit.gerak.LangitGerakClient.init() (its comment line).

## Open notes for the leader / other modules
- Edited outside the 37.9.6 table (needed, no other 37 module owns them): formasi/Formasi.java (useUnregistered accepts
  the awan_transfer ground node like an L5 outpost; L5 outpost count/message now only for L5) and
  formasi/core/CoreTicker.java (outpostHere: any awan_transfer within AWAN_RANGE runs outside the territory).
- jaring_awan is Family.PELINDUNG: it uses one of the 2 PELINDUNG cores per owner and an active slot.
- "Pijakan awan" collider (37.0 item 3) is not in the 37M file list; not built here.
- No new Japanese voice lines; sounds.json / gen_voices.py untouched.

## Status
DONE (run 1). Next step: none; Gradle build by the leader.

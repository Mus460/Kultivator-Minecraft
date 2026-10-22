# detail:T (Kapal & Peta detail pass) - progress

Run 2 (earlier run left no progress file; state reconstructed from git diff + untracked files).

## Done (run 1, found on disk)
- [x] KapalStyle liveries (5) + State codec/save/keel tooltip + helm OP_STYLE/OP_RENAME server side (KapalStyle.java, KapalItems, KapalRohEntity, LunasItem)
- [x] KapalVoice (voice.kapal.* with fallbacks) hooked: launch, cloud leap, arrive, hull broken, raider "sky_pirates"
- [x] KapalWeather: module L weather/sky -> fuel, speed, eclipse pirates x2, twin moons leap x0.75, meteor net
- [x] KapalBajakEntity raider + pirates flow (raider alongside, band boards when low, spoils by tier) (Voyages.spoils)
- [x] KapalTests: gaya, nama, rampasan, meteor, info
- [x] KapalModel parser: hidden runs, pennants, rigging, oars, formation, window lights, model by name (refs KapalMesh - MISSING)

## Plan (run 2)
- [x] 1 lang keys (lang-src/dunia_kapal.*: style/weather/name/spoils/meteor/info/tooltip/voice.crew/hud/helm/moments/raider entity)
- [x] 2 advancements data/kultivator/advancement/dunia/ship_bahtera|ship_leap|ship_pirates.json (+ lang)
- [x] 3 gen_dunia_kapal.py: pennants, rigging, oars, formation, window lights, cabin/pagoda furniture, runners, finials, perahu_bajak (weathered); regenerated (layout constants unchanged; runs 100/396/735, raider 106). No 'h' runs emitted.
- [x] 4 client: KapalMesh ref replaced by new KapalLivery (state swap cache); new KapalDraw (shared drawing: livery, pennants, rigging ribbons, qi oars, formation disc, night windows, raider look); KapalRenderer thin + nameplate; QuadBuf.ribbon/flatTurned; textures rope/oar/formation/sail_bajak (gen_dunia_kapal_textures.py)
- [x] 5 KapalBajakRenderer registered in KapalClient; EV_METEOR/EV_SPOILS/EV_PIRATES banners+bursts; KapalFx raider wake/embers/soot, formation dust, damaged-hull smoke; KapalHud weather line
- [x] 6 HelmScreen (H 236): livery swatches w/ lock tooltips, name EditBox + Namai, weather + name in header
- [x] 7 Peta.java: site_found voice on SITE_DISCOVERED (rate-limited), adv dunia/sites_15 + dunia/map_exchange (json + lang dunia_peta); KapalTests 'suara <baris>'
- [x] 8 GuideChapters step c14.bajak (adv ship_pirates); dunia_panduan c14.bajak + pelayaran text; help kapal_terbang extended (livery/name/weather/raider/spoils)
- [x] 9 javac 25 type-check (scratchpad/detailT/jc/check.sh, MC runtime javac): only the 3 known EntityRenderers.register access-widener errors (build-time AW). Lang keys verified id+en. Also: ship name on the map marker (KapalRohEntity.mapEntry + PetaScreen).

## Next step
done (report)

## Left / for others
- VoiceEvents does not register kapal.* / peta.site_found yet -> fallbacks play (KapalVoice.pick); the orchestrator/finish agent must add the group.
- DESIGN 34.5.15 still says kapal_kursi and 60/150/300 runs; real: seats on root, runs 100/396/735 (core 84/327/626), raider 106 (core 93).
- Shared compile (finish agent); R/D lines in DuniaContent(Client); other modules' help topics and c13/c14 advancements.

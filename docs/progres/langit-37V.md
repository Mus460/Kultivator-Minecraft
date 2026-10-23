# langit:37V progress (DESIGN 37 module 37V "Visual": sky-sect atmosphere, FX, sounds; client only)

Design: docs/rancangan/37-sekte-langit.md 37.6 + 37.9.4. No Gradle. LF for new files; KultivatorClient.java is CRLF.
STATUS: DONE (run 1). javac type-check clean for every 37V file (scratchpad l37v/check.sh; remaining errors are in
37M LangitGerak / 37G LangitSites, not 37V).

## Checklist
- [x] 0 Survey (LangitRenderer/LangitSink, KosmikTypes, EpicBudget, ShaderCompat, FxBudget, SektePoses/DutyPoses,
      FlyingSwordLayer, mixins json, kosmik/surga textures) + MC APIs verified in mcsrc (WeatherEffectRenderer
      .extractRenderState :68, ClientLevel.tickWeatherEffects :362, EnvironmentAttributes SUN/STAR_ANGLE, SKY_COLOR,
      STAR_BRIGHTNESS, SoundEvents, ParticleTypes, ParticleStatus, Vec3i.distToCenterSqr, Mth)
- [x] 1 LangitSkyState (only reader of LangitPayloads records), AwanSink, LangitBudget (37.6.7 table), LangitFrame,
      LangitClientConfig (config/kultivator-langit-client.json, 9 switches)
- [x] 2 LautAwanRenderer (log-polar, world UV, daily wind, 32/144/384 quads)
- [x] 3 AirTerjunKabut (camera-facing ribbons, mist billows, qi-mist motes)
- [x] 4 KubahFormasi (rim band, latitude dome, closed-gate wall, Cloud Net lattice + 4 pillars)
- [x] 5 CahayaFajar (gold tint + additive beams), HaloPelangi (antisolar glory, Saw(SAW_HALO) once per morning),
      SungaiBintang (great-circle Milky Way turning with STAR_ANGLE + twinkling stars)
- [x] 6 KawananBangau (V flocks, Lissajous loop, 2-frame sprite), KelopakJatuh, LenteraLangit (stateless, seeded)
- [x] 7 LangitFxClient (ops 0-7 + test ops 100 halo / 101 festival) incl. tether pillar, Nine Heavens rings, vortex+bolts
- [x] 8 Mixins WeatherEffectRendererMixin, ClientLevelWeatherMixin, LangitDutyPosesMixin + 3 entries in
      kultivator.client.mixins.json
- [x] 9 LangitSuara (ambient ≤ 4 / 5 s, one-off fx sounds, vanilla categories)
- [x] 10 tools/gen_langit_tex.py + 9 PNGs in textures/sekte/langit (halo, bangau, sungai_bintang, lentera, pilar,
      pusaran, kilat, jaring, pedang; all checked with Read)
- [x] 11 LangitPoses (25 sword patrol, 26 rim guard, 27 watching) + SwordLayer (natal_sword model + sword-light)
- [x] 12 LangitVisuals (COLLECT_SUBMITS + tick), LangitClient (init, weatherSheltered, openSettings), LangitVisualScreen
      (scroll + living painting + 9 jade-coin switches), KultivatorClient 1 line (CRLF kept), lang
      sekte_langit_visual.{id_id,en_us} (20 keys, identical)
- [x] 13 Self check: javac clean, LF on all new files, CRLF kept on KultivatorClient, no server->client reference,
      no LightningBolt/weather/time change, no block change, no ItemStack in static init, render types lazy (KosmikTypes)

## Next step
None. Final Gradle build by the lead.

## Open problems / hand-over
- SektePoses.register only accepts 13-24 (throws for 25-27) and DutyPoses only blends while still: 25-27 poses go
  through client mixin LangitDutyPosesMixin (RETURN of DutyPoses.apply); sword via Fabric layer callback (raw cast,
  PlayerModel generic bound). No DESIGN 36 file edited.
- 37G: add a "Pemandangan" button in LangitScreen -> LangitClient.openSettings(this); register LangitPayloads.onSaw for
  SAW_HALO (grant sekte_langit/cahaya_buddha); tests send Fx(LangitFxClient.OP_TEST_HALO=100 / OP_TEST_FESTIVAL=101);
  FX_SENGSARA_BAWAH: one Fx per strike, a = tribulation position (vortex refreshes within 24 blocks, lives 20 s).
- Sync anchor ids read by 37V: airterjun_<i>_<n|e|s|w>, jaring_*, dermaga_*, meditasi_*, gerbang_langit, tahta, feri_atas.
- FX_PILAR: a = ground stele, b = throne (hall tahta/origin within 3 blocks); kept alive while the synced hall is tethered,
  else 30 s after the last packet. FX_TANGKAP: a = where caught, b = safe spot. FX_PINDAH: a, b = both ends.
- python edits must use encoding='utf-8' (cp1252 default truncated LangitPoses once; rewritten).

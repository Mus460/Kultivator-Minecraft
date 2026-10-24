# mega-38TV-F — Kota Tema: efek klien + TemaClient + TemaHub (W2)

Scope (jadwal row 38TV-F, 38.T.9.4): `client/dunia/tempat/tema/{TemaClient,TemaHub}.java`, `client/dunia/tempat/tema/fx/**`,
new `tools/gen_tempat_tema_fx.py`, `textures/dunia/tempat/tema/**`, lang `tempat_tema_fx.*`. No Gradle. LF for new files.
Must NOT touch: `tema/model/**` (38TV-M), `client/tempat/ui/**` (38TV-U / kit), any server file.

## Checklist
- [x] Study: TemaNet keys, TemaMekanismeEntity/TemaAnimasi, entity classes (ApiSuar, KeretaGantung, PerahuNaga, ...),
      HubCards, HiasanDunia/HiasQuads pattern, KosmikTypes, LevelRenderEvents, HUD pattern
- [x] tools/gen_tempat_tema_fx.py -> textures/dunia/tempat/tema/{fx,awan,kabut,pasir,dinding_pasir,ombak,pendayung,mural}.png
      (deterministic; previews tools/keluaran/tempat_tema_fx/*, all read and checked)
- [x] fx/FxQuads (quad sink), fx/FxTekstur, fx/Bingkai (frame state), fx/TemaJalur (lanes), fx/TemaFx (driver; refs
      TemaHud, TemaFxPerintah, TemaFestival + all effects with gambar()/tick()/aktif()/bersihkan())
- [x] fx/TemaHud (storm tint+grain, mist veil, banner, captions), fx/TemaFxPerintah (/temafx), fx/TemaFestival (router)
- [x] TemaFx.terima dispatcher (BADAI/FESTIVAL/SPLINE/CAHAYA apa=budha|suar|awan|kabut|riak)
- [x] fx/TemaSuara (loop bed + one-shot), fx/Bentuk (shell/sheet shapes), FxQuads.pitaU, Bingkai.sekarang()
- [x] BadaiPasirFx (shell rings + horizon wall + particles + wind loop; HUD tint in TemaHud; goyang() jitter)
- [x] LenteraAirFx (spline drift, 0/40/120; mulai/akhir/lepas(x,y,z,milik))
- [x] CahayaBudhaFx (rings 1/3/5 + head silhouette)
- [x] LautAwanFx (1/2/2 layers; suasana() shared), KabutGunungFx (mist shell; kuat() for HUD)
- [x] SinarSuarFx (2 rotating additive beams / point light on Minimal; flare; CAHAYA suar scene)
- [x] RiakDarahFx (0/1/3 rings; pesta()), OmbakFx (0/1/1 wave surface; surf bed)
- [x] TaliGantungRenderer (wheel pairs BESAR / SPLINE kabel / stub; katrol 4 ropes; consts TROLI 3.25, ALUR_RODA 0.5, SISI 4.5)
- [x] MuralBillboard (2-frame; titik list optional; niche 12 player murals; caption()), PendayungImpostor (20 paddlers +
      drummer + helmsman, LOD, drum sound; consts DUDUK 0.45, LAJUR 0.55, rows ±1.5..6.5)
- [x] TemaClient receiver -> TemaFx.terima, LRU terakhir, inits TemaFx/Model/Ui/Hub
- [x] TemaHub (HubCards DUNIA kota_tema -> lihatDariHub(tema_dagang))
- [x] lang tempat_tema_fx.{id_id,en_us} identical keys (48: hub card, 14 acara + umum, hud, perintah)
- [x] Final review: APIs checked in mcsrc (sounds, AABB, FluidState, Heightmap, Language.has, CameraRenderState,
      pipelines no-cull/additive depth), unused imports 0, client-only package, LF. javac not possible locally (only
      JDK 21; classes are v69) -> F2 Gradle compiles.

## Langkah berikutnya
DONE. Nothing left in scope. Re-check (retry session): F1 Gradle BUILD SUCCESSFUL compiled fx/** with no errors;
lang 48/48 identical keys; 8 textures on disk; TempatClient.init -> TemaClient.init wired. No further work.
Design notes: world pass = LevelRenderEvents.COLLECT_SUBMITS (HiasanDunia pattern), types KosmikTypes.glow/add/cutout,
HUD = HudElementRegistry after MISC_OVERLAYS; entity-driven fx scan getEntitiesOfClass every 20 ticks.

## Keputusan sesi 2 (payload -> efek)
- BADAI {mulai,x,z,r,tick=storm start,panjang,kuat}: omen = tick-1200..tick (horizon wall moving 0.3 b/t, placed at
  min(dist, 0.85*renderDist) scaled = same angle), storm = shells pasir r 8/12/16/24 + roof cap + streak quads
  (takeQuads, 0/400/900 per s), HUD tint #C8A060, wind loop ELYTRA_FLYING (relative), compass jitter value exported.
- FESTIVAL lentera_air {mulai,tick,akhir} -> LenteraAirFx spawns on lanes kind lentera (fallback perahu); extra optional
  acara lentera_lepas {x,y,z,harapan?}; mural {gua,pemain[{nama,ikon}], optional titik[{no,x,y,z,yaw}]};
  mural_bergerak {gua=7, optional x,y,z,yaw}; perahu_naga -> rowers race tempo; pesta_darah -> faster ripples.
- CAHAYA apa budha|suar|awan|kabut|riak {x,y,z,tick,panjang,nyala, optional r}. awan/kabut: drawn when pagi or rain.
- Scans: ApiSuarEntity (keadaan MENYALA; lens phase (game%240000+pt)*3deg like ApiSuarRenderer), KeretaGantung +
  Katrol (+RodaKatrol) ropes; cable ends from SPLINE kind "kabel" else estimated from cabin samples (nilai, pos);
  PerahuNagaEntity rowers (forward = (-sin yaw, cos yaw)); OmbakFx when riding PerahuNelayanEntity and petir > 0.3.
- TemaHub: HubCards DUNIA "kota_tema" Glyphs.JALAN -> UiTempatClient.lihatDariHub(TemaLayar.Dagang.LAYAR, DUNIA).

## Butuh dari modul lain
- 38TK-G: CAHAYA apa=awan/kabut on entering Kota Gunung {x,y,z,r}; apa=budha {x,y,z pandang,tick,panjang}; optional
  SPLINE kind "kabel" (station/tower/station nodes at rope height).
- 38TK-P: ApiSuar keadaan MENYALA 12500-23500 + storm, nilai = strength; CAHAYA apa=suar {x,y,z,tick,panjang} scene.
- 38TK-A: FESTIVAL lentera_air {mulai,tick,akhir}; acara lentera_lepas {x,y,z,harapan(owner only)}; SPLINE kind lentera;
  PerahuNaga keadaan LOMBA + nilai = stroke rate.
- 38TK-O (Mural.java): add titik [{no,x,y,z,yaw}] to acara=mural, x,y,z,yaw to mural_bergerak.
- 38TK-I: CAHAYA apa=riak {x,y glass block,z,r,kuat}; FESTIVAL pesta_darah.
- 38TK inti: FESTIVAL acara ids as in lang acara.* (dewi_laut, kapal_datang, kafilah_agung, tari_putar, lentera_air,
  perahu_naga, ziarah, lonceng_awan, cahaya_budha, lelang_gelap, pesta_darah, pasar_malam).
- 38TV-M: rope/crew anchors = TaliGantungRenderer.TROLI 3.25 / ALUR_RODA 0.5 / SISI 4.5 / MATA_KATROL 1.2;
  PendayungImpostor DUDUK 0.45, LAJUR 0.55, rows z +-1.5..6.5, helmsman z -8, forward (-sin yaw, cos yaw).
- Compass/quest pointer owners: may read public BadaiPasirFx.goyang(partial) (+-20 deg storm jitter).

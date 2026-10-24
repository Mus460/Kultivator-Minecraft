# mega-40R (W1, DESIGN 40 module 40R: renderer integration, settings, client test commands)

Scope (jadwal sec 3 W1 row 40R, sec 5 note; design 40.2.3 client, 40.6.1-40.6.4 client, 40.7.1-40.7.5):
new `client/sosok/{SosokClient, SosokRender, SosokDetail, SosokKlienPerintah}.java`, lang `sosok_klien.*`;
owned old files: KultivatorClient, DiscipleRenderer, DiscipleRenderState, NpcRenderer, KotaGearLayer,
HumanNpcRenderer, ArwahRenderer, LautNpcRenderer, RogueRenderer, EnvoyRenderer, PenontonRenderer, SettingsRows.
All owned files are LF except KultivatorClient.java (CRLF in HEAD, kept CRLF). No Gradle. 40K/40G/40I run in parallel (their classes not on disk at start).

## Checklist
- [x] Read owned files + PoseRenderData/FabricRenderState pattern, AuraSettings/EffectQuality, EpicClientCommands;
      mcsrc: ModelPart (createPartLookup, visit, visible not reset by resetPose), LivingEntityRenderer.submit
      (setupAnim deferred to feature render), setupRotations, RenderTypes.lines + FishingHook line vertex,
      NativeImage.writeToFile(Path), DynamicTexture.getPixels, TextureManager.getTexture/release,
      Minecraft.crosshairPickEntity/gameDirectory/getFrameTimeNs, PoseStack.rotateDegrees
- [x] SosokDetail.java (setting otomatis/tinggi/sedang/rendah + wajah, Tingkat budgets, Lod w/ 2-block hysteresis,
      nearest-N thresholds from previous frame, compose budget/queue, 5-s stats, lod/bingkai toggles)
- [x] SosokJembatan.java: ONLY file naming 40K/40G classes (guarded calls + Gerbang LOD/shadow wrapper for layers)
- [x] SosokRender.java (RupaMasukan, Keadaan per entity reused, ekstrak/selesai/mintaKlip, atlas choice + fallback,
      dariLama policy table 40.4.1 D, kelasCadangan, angkat/baring (tidur 90deg), labelLod, Bingkai outline layer)
- [x] SosokClient.java (layers, GerakKlien, 200-tick rapikan, DISCONNECT -> lepasSemua, /sosok registration)
- [x] SosokKlienPerintah.java (/sosok info|lod|dump [benih <sosok> <rupa>]|muat_ulang|bingkai|detail|wajah)
- [x] DiscipleRenderer (sosok mesh + vanilla fallback, robe TanpaSosok, layers, ekstrak/selesai, cutout, angkat/baring)
      + DiscipleRenderState (int sosok, gaya)
- [x] NpcRenderer (look key DATA_RUPA or role id, sekte uniform, NpcPoses.snapshot call removed -> Gerak) +
      KotaGearLayer (hair parts hidden per SosokJembatan.sembunyi, no gear at SILUET/JAUH)
- [x] HumanNpcRenderer (hooks rupa/kulitSendiri/lookSosok/slimLama, client Sosok via dariLama, BOW/TALK -> clips
      32/38 via mintaKlip, sosok mesh + vanilla, lift/tilt + angkat/baring) ; Arwah (9 keys, 5 own skins, role clips
      masak/menulis+palu_hakim/sikap_tombak/main_lentera/lipat_tangan) ; Laut (6 keys, Raja Naga own skin, clips)
      Yaksha untouched (no rupa -> old path)
- [x] RogueRenderer (liar.<sect>, slim=woman, sosok.skala draw scale) ; EnvoyRenderer (utusan_langit, man,
      translucent kept, PoseRenderData.extract added for Gerak/cast) ; PenontonRenderer (fallback atlas only, cached
      per spectator, Sosok mesh, child scale; old skins if no fallback)
- [x] SettingsRows (heading kultivator.sosok.setelan.judul + cycle "Detail animasi" w/ tooltip + coin "Ekspresi wajah")
      + KultivatorClient line SosokClient.init() after DiscipleClient.init()
- [x] lang sosok_klien.{id_id,en_us}.json: 32 identical keys (setelan.*, klien.*), no clash with other fragments
- [x] Aligned with 40G progress contract: sleep turn = GerakApplier.putarTidur (bridge), SosokPropLayer(parent)
- [x] Scratch javac (MC runtime java 25, cp.win = MC 26.3 + Fabric API + B0 classes) of all 17 40R files + stubs of
      the assumed 40K/40G APIs: 0 errors (script scratchpad/r40/jc.sh, list r40/list.txt, stubs r40/stubs)
- [x] LF/CRLF check: all LF, KultivatorClient CRLF kept (151 -> 153 CR); 40K/40G Java not on disk yet for kulit/busana
      (40G partial) -> assumptions documented below; F1 fixes one-liners in SosokJembatan only

## Langkah berikutnya
DONE for W1. F1: compile; if a 40K/40G signature differs, edit only SosokJembatan (each call is one line).

## Butuh dari modul lain
- 40K (or F1 in SosokJembatan): `SosokLapisan.register()`, `SosokLapisan.TUBUH` / `TUBUH_RAMPING` (ModelLayerLocation
  of kultivator:sosok_tubuh(_ramping)); `new BusanaLayer(parent, context)`, `new WajahLayer(parent, context)`
  (RenderLayer<DiscipleRenderState, PlayerModel>); `RupaResolver.dari(Sosok, Gaya, SosokRender.RupaMasukan)` ->
  `SosokRupa`; `KulitCache.atlas(SosokRupa)` -> @Nullable Identifier, `KulitCache.lepasSemua()` (+ forget resep/parts),
  `KulitCache.jumlah()`; `KulitCadangan.untuk(int kelamin, int usia, String kelas)` -> @Nullable Identifier;
  `KulitPenyusun.susun(SosokRupa)` -> NativeImage (caller closes); `SosokGear.SEMBUNYI.get(kind)` -> String[].
- 40K: layer data = `SosokRender.keadaan(state)` (sosok, gaya, atlas, lod, kulit, tembus, kotaGearKind, headwearKind,
  rupa()). If 40K's layers read `BusanaLayer.TAMPIL` instead, F1 sets it at the end of `SosokRender.ekstrak` via a
  SosokJembatan line. BusanaLayer draws its cube outlines while `SosokDetail.bingkai()`.
- 40K resep rows needed for the keys 40R sends: murid.0-5, murid.calon, murid.saingan, d.meng_po, d.hakim,
  d.raja_yama, d.arwah, d.penjaga_laut, d.pertapa_laut, d.dewa_penglai, d.nelayan, d.pedagang, liar.0-7,
  utusan_langit, NpcRole ids / kelompok.*; fallback classes sipil/kultivator/penjaga/rahib/bangsawan/iblis.
- 40G: GerakPengendali plays `SosokRender.keadaan(state).klipKlien/varianKlien` as layer 6 when the body has no
  server clip (D people: hormat 32, bicara_tangan 38, masak, menulis, palu_hakim, sikap_tombak, main_lentera,
  sikap_lipat_tangan, teropong_laut, lambai, seru_dagang). Read LOD/springs/face via `SosokDetail.lod/pegas/wajahHidup`.
  40.2.3 part proportions from `SosokRender.sosok(state)` (D, rogue, envoy Sosok are client-made). NpcRenderer no longer
  calls `NpcPoses.snapshot` (Gerak builds the idle POSE).

## Sesi 2 (rekonsiliasi dengan 40K/40G yang sudah di disk) - SELESAI
- [x] SosokJembatan: GerakApplier.angkat/putarTidur take AvatarRenderState (instanceof guard; old stub took
      LivingEntityRenderState), evaluasi(.., kunciRupa) overload, muatUlangGerak (GerakKlien.muatUlang + re-enable),
      jumlahKeadaanGerak/jumlahKlip, gerakRusak(), headwearMilikSosok(kind) (SosokGear.sembunyiHeadwear == {"*"})
- [x] SosokRender.selesai passes kunciRupa only for non-Pemegang bodies (D, liar, utusan, penonton); disciples/NPCs
      null so 40G keeps its own stance keys (calon, saingan_murid, murid.N); hashMasukan mixes gaya.luka() (40K ask)
- [x] HumanNpcRenderer: on a Sosok skin, Headwear GHOST_HAIR/GHOST_EYES (KIND and GLOW) set to NONE after ekstrak (40K ask)
- [x] /sosok info: line info.katalog (clips, live states) + info.gerak_rusak; muat_ulang also resets 40G;
      lang sosok_klien 34 identical keys
- [x] 40K field use checked (Keadaan.atlas/atlasSendiri()/lod/pegas/tembus/rupa(), RupaMasukan accessors): all match
- [x] javac against REAL 40K/40G sources (scratchpad/r40b/jc.sh, sourcepath + -Xprefer:newer): 0 errors; all LF,
      KultivatorClient CRLF (git ls-files --eol w/crlf)

## Celah tersisa (bukan milik 40R)
- 40G GerakPengendali never reads SosokRender.Keadaan.klipKlien/varianKlien: the D-people client clips (hormat 32,
  bicara_tangan 38, masak, menulis, palu_hakim, sikap_tombak, main_lentera, sikap_lipat_tangan, teropong_laut, lambai,
  seru_dagang) that HumanNpc/Arwah/Laut renderers request via SosokRender.mintaKlip are not played. Fix (40G/F1, one
  line after `int g = p != null ? p.gerakData() : 0;` in GerakPengendali.evaluasi): `if (g == 0) { SosokRender.Keadaan
  sk = SosokRender.keadaan(st); if (sk != null && sk.klipKlien != 0) g = GerakId.pack(sk.klipKlien, sk.varianKlien); }`
  (ideally with its own start time like `bendera`, so the loop does not restart each frame).

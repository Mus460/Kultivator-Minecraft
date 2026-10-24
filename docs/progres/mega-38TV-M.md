# mega-38TV-M — Kota Tema: model 3D, tekstur, item (W2)

Scope (jadwal W2 row 38TV-M, sec 5 note; design 38.T.9.1-38.T.9.3, 38.T.8.1 props 41-62, 38.T.10.1 item models):
owns `client/dunia/tempat/tema/model/**` (+`TemaModelClient`), `tools/gen_tempat_tema_{models,textures}.py`, item
models/textures for props 41-62 (`fx/tempat/tema/**`), `dagang/**`, `tema/**` items, `textures/entity/tempat/tema/**`,
lang `tempat_tema_model.*`. Must NOT touch: fx (38TV-F), ui (38TV-U), any server file, 38MD files (read/extend only).
No Gradle. New files LF.

## Design decisions
- Entity textures = stacks of horizontal material bands, each band periodic over the full width (textures REPEAT,
  AbstractTexture default), so boxes may overflow horizontally; rule per box: depth + height <= band height.
  Decal bands (ship eye, relief, runes) are painted at the exact uv of their plate box.
- Base `TemaBendaRenderer` (extends 38MD `BendaRenderer`): reads TemaAnimasi (varian, keadaan, umur, nilai), LOD
  (> 64 blocks no small animation, > 128 not drawn, ships 192), optional emissive pass (eyes render type).
- Creatures (unta, kalajengking, kera) use MobRenderer + `PancarLayer` (38MD).
- `sel_tawanan` reuses 38MD `SangkarRenderer` mesh layer (imported, not edited).
- Item icons 16x16 flat (vanilla density) for tema/dagang; cuboid 3D models for props 41-62, dagang/peti and the two
  head items (kerudung_pasir, topeng_tanpa_wajah: "head" display fits the worn head).

## Checklist
- [x] Study: schedule, kota-TK0, 38.T.8/9/10, TemaEntities + all 33 entity classes, BendaRenderer/Gerobak/Sangkar/
      TikusRoh/PancarLayer, gen_tempat_models.py, SosokGear, PropId/PropKatalog paths, sampler REPEAT, CustomHeadLayer
- [x] tools/gen_tempat_tema_textures.py: all entity band textures + glow (item icons live in gen_tempat_tema_models.py)
- [x] tools/gen_tempat_tema_models.py: props 41-62 (validated, iso previews read), 35 materials, 3D dagang/peti +
      head items, 58 flat icons (tema 41 + dagang 17; ids parsed from TemaItems/DagangItems), icon sheets read
- [x] model/TemaBendaRenderer base (+ helpers, LOD)
- [x] Pelabuhan: JungRenderer (dagang+bajak), PerahuNelayan, ApiSuar, DerekDermaga, KapalGalangan (5 stages, decal
      band 11) incl. textures (previews read), registered in TemaModelClient
- [x] base TemaMakhlukRenderer (MobRenderer + TemaAnimasi state, LOD, scale, PancarLayer)
- [x] Oasis: UntaKafilah (scale 0.56, pace, kneel), KalajengkingRaja (2 glow layers), TimbaSumur, TemuanPasir,
      PintuPasir, PilarRune, BatuUkurAir: renderers + textures (previews read) + registered
- [x] Air: PerahuKanal (4 variants), PerahuNaga (4 teams, 20 impostor paddlers + steersman), AlatTenun: + textures,
      registered
- [x] Gunung: KeretaGantung, Katrol, RodaKatrol, BatuRuntuh, HerbaTebing, Palang, TaliTurun, Jerat, KeraGunung:
      renderers + textures (previews read) + registered
- [x] Iblis: GerbangTengkorak, PintuArena, SelTawanan (imports 38MD sangkar layer+texture), SegelPintu, TongMinyak,
      TuasGerbang; Bersama: Tandu, PintuAir: renderers + textures (previews read) + registered
- [x] All 33 TemaEntities types registered (33 pasang); every referenced entity texture exists (script check)
- [x] TemaModelClient: pasang(type, provider) for all 33 + safety net Noop+warn
- [x] TemaModelStat (1 s render counters, hooked in both base renderers' extract + glow submit)
- [x] TemaModelPerintah client command `/temamodel info|cek` (registered in TemaModelClient.init, `terdaftar()`)
- [x] lang tempat_tema_model.{id_id,en_us} 6 identical keys, no duplicates (only user: /temamodel; item/entity/prop names live in
      tempat_tema (38TK) and sosok (40I) and are all present)
- [x] Review: scratch javac (scratchpad tvm/jc.sh, JDK25 vs MC 26.3 jars + F1 build classes): 37 files, 0 errors;
      band rule (tvm/band.py, ceil(h)+ceil(d) <= band): 685 boxes, 0 over, 35 non-literal skipped; all entity
      texture refs exist; no CRLF in owned files; no server file imports client model code

## Langkah berikutnya
DONE. Finish agent: Gradle compile + mergeLang (new fragment tempat_tema_model).

## Masalah terbuka
- kereta_gantung: only a local 6-block rope slice is drawn; the full span catenary between towers needs station
  positions on the client (38TK-G / 38TV-F), not available to the entity renderer.

## Butuh dari modul lain
-

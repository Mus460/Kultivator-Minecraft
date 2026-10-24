# mega-D-KS (wave W3, detail pass of 38KS: capital NBT/interiors/events) — checkpoint

Scope: jadwal W3 "D-KS" = files of 38KS (see mega-38KS.md): `dunia/tempat/kotabesar/**`,
`client/dunia/tempat/kotabesar/**`, `dunia/kota/Towns.java`, `tools/gen_tempat_kota_besar.py`, `tools/kota_besar_kit.py`,
`structure/kota_besar/**`, `worldgen/**/kota_besar/**`, `resourcepacks/dunia_kotabesar`, `lang-src/tempat_kota_besar.*`,
advancements `tempat/{ibu_kota,warga_terhormat,pahlawan_kota,buronan,alkemis_juara,pertahanan,teleport}`.
No Gradle. New worlds only. Scratch tools: scratchpad ks/ (preview_kb.py, lang_kb.py, cp_src.txt).

## Checklist
- [x] 0. Survey (scratch tools: scratchpad dks/kosong2.py = empty-room finder per building on canvases,
      dks/irisan.py <distrik> <y> [x0 x1 z0 z1] = ASCII slice of assembled lots, dks/patch.py = anchor inserts)
- [x] 1. Generator: partial run (`gen_tempat_kota_besar.py <distrik>`) writes NBT only, never JSON (a partial run had
      wiped the other districts' lots in worldgen/structure JSON; full run restores). Wall pieces taller (+3) so tower
      and watch-post roof tops are no longer clipped (old warning "helper blocks outside the piece")
- [x] 2. Kit: interior dressing system (`K.dandan(p, box, f, gaya, lorong, simpan, pilar, karpet)`, clusters k_*,
      wall dressing, inner columns, aisle runner; never touches titik/anchors/stage boxes/doorways)
- [x] 3. Apply dandan per building (all 11 districts): gilda, istana, cendekia (+pagoda 7 storeys via
      K.pagoda(isi=...)), awan (+dock tower storeys), pasar, pusat (kantor_pos, yamen aisles, bell shrine), hunian_barat
      (rumah_makan_kecil), gerbang (Awan Singgah common room), arena (wisma_delegasi), kumuh (dapur_umum), luar
      (pos_kafilah office+warehouse), shared siheyuan/rumah_toko/rumah_susun (rumah_kecil was already full).
      Empty indoor share now 13-42 % (rest = sealed attics + kept aisles/stages); floor plans dks/denah.py PNG read.
- [x] 4. Full generator run: 102 tpl, 97 pools, 2.630 MB (< 3.0), no clipped helpers; cek_tempat 0 kota_besar errors
- [x] 4b. TplCheck kota_besar via harness out38 (after F2): files=102 problems=0
- [x] 5. Java/events detail pass (survey done: palace screen = 38UI Acara.ACARA_ISTANA, already used; scratch
      dks/jangkar.py <distrik> [filter] lists anchors in the canonical frame)
  - [x] 5a. (KotaBesarTampil.poster; spots checked clear on assembled pusat) Boards: Yamen wanted board was at desk dx-2 (in the walkway, facing away) -> north wall of the hall
        (desk dz-9, faces south); hero board was 4 S of the festival anchor (blocks the causeway) -> north end of the
        causeway (dz-9, faces south)
  - [x] 5b. (KotaBesarAnchors.lepasJauh, LEPAS_JARAK 80) Body cap starvation: bodies stay while chunks are loaded, so walking one way filled tubuhMaks and later
        districts got no service NPC -> release far bodies (no player within 80) when the cap is hit
  - [x] 5c. More lines: palace lore 6->12 (Istana mod 12), festival laughs 3->6 (lang 430 keys, identical)
  - [x] 5d. Scratch javac (dks/jc.sh, Java 25, F2 classes) on kotabesar/*.java: 0 errors, 43 classes

## Langkah berikutnya
D-KS done. Left for F3: Gradle build, in-game check of the two moved boards and of service bodies across districts.

## Butuh dari modul lain

## Open problems
- Not run in game (no Gradle/runClient for W3 agents): board facing assumes BendaRenderer front = entity yaw
  direction (180 - yaw like mobs).

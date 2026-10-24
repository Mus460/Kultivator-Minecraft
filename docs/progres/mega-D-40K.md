# mega-D-40K (W3 detail pass, module 40K: kulit & busana)

Owns: `client/sosok/kulit/**`, `client/sosok/busana/**`, `tools/sosok_*`, `tools/gen_sosok_*`, `tools/cek_sosok.py`,
assets `textures/sosok/**`, `sosok/resep.json`, `{models/item,items,textures/item}/fx/sosok/**`. No Gradle.

## Checklist
- [x] Read design 40.3/40.4/40.6.5 + generators; previews peran_03, contoh, rambut_P read: cloth flat, black hair flat
- [x] Gap check: all 102 NpcRole ids + d.* (non-own-skin) + liar/murid/jalan/penonton/utusan have resep rows (none missing;
      d.kepala_lembu/muka_kuda/wuchang/hantu_lapar/raja_naga use own skins, no row needed); no two role rows share
      (gaya, kain, topi, prop, tambahan, buzi, kepala)
- [x] A. tools/gen_sosok_bagian_busana.py `drape()` over all 22 styles x 2 widths (dyed KAIN1/KAIN2 only, fixed colours
      untouched): light falloff per part, fold lines deepened + lit ridge beside them, rounder silhouette edges, weave per
      cloth class (BROKAT lozenge lattice, KASAR weft rows + slubs, SUTRA sheen line)
- [x] B. cube polish in `paint_cloth`: pleat ridges (dark line + lit ridge), top fold, height gradient, 2-row hem on long
      skirts + dotted TEPI stitch row above hems, sleeve mouth (dark KAIN2 interior + rim) on lengan_lebar_manset,
      KAIN2 lining on the body side of jubah_luar, pleats on side skirts and sleeve pockets
- [x] C. tools/gen_sosok_bagian_rambut.py: broken shine band (RAMBUT_KILAP) on painted hair back/sides (`shine_ring`)
      and a quarter down every hair cube (not beards/brows)
- [x] Regenerated: gen_sosok_bagian (352 sheets), gen_sosok_cadangan (48), gen_sosok_pratinjau (all previews)
- [x] cek_sosok: 351 sheets, 56 cubes, 143 look rows, 0 errors 0 warnings
- [x] javac k40/jc.sh 0 errors (no Java changed); parity k40/parity 300/300 identical (Java vs Python atlases)
- [x] Final report

## Pratinjau dibaca (Read)
- contoh.png (3 runs): cloth now shows weave/folds, hem stitch dots and pleat ridges on skirts; nothing too dark
- busana_2 crop 2x: torso lighter at the top, darker hems, weft rows visible; first try (deltas 5) was invisible
- peran_01, peran_05 crop: brocade dots on PEJABAT, coarse weave on PENDEK/PETANI, child clothes slubs; OK
- galeri.png: 128 people, nothing broken
- rambut_L crop, rambut_P: shine band visible on black and brown hair backs after tone 190/184

## Keputusan
- The tone ramp is gentle (1 STEP = 24 tone ~ 5 % sRGB), so drape deltas are 15-30 tone, cube pleats -22/+14.
- Only sheets changed (Python generators + PNGs); no Java, resep.json or palette change, so parity holds by design.

## Langkah berikutnya
SELESAI. F3: no generator run needed (assets regenerated on disk); optional `python -I tools/cek_sosok.py`.

## Butuh dari modul lain
- none

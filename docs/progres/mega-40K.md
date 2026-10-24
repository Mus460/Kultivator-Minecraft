# mega-40K (W1, DESIGN 40 module 40K: kulit & busana, klien + generator)

Scope: jadwal W1 row 40K + sec 5 note; design 40.3, 40.4 (resep data), 40.4.5 SosokGear, props 63-88 (PropId).
Owns: new `client/sosok/kulit/**`, `client/sosok/busana/**`, `tools/sosok_*`, `tools/gen_sosok_*`, `tools/cek_sosok.py`,
assets `textures/sosok/**`, `sosok/resep.json`, `{models/item,items,textures/item}/fx/sosok/**`.
Must NOT touch: KotaGear*, renderers, perlengkapan.png, any old file. No Gradle.

## Checklist
- [x] Read facts: sosok/* (40I-0), NpcRole (106 roles), KotaGear kinds 0-20 + part names, Headwear 0-16, CultivatorModels,
      LambangCache/JubahLook/SeragamData, Dao/Realm/PowerIds, png.py (writer only), KotaGearLayer submit pattern,
      mcsrc CubeListBuilder.addBox(...,Set<Direction>), ModelPart.Cube UV; design UV tables C/D checked: no overlap
- [x] tools/sosok_lib.py (SplitMix64, FNV, png read/write, Lembar/Badan painters, UV tables, cek_uv, tangga, susun, figure)
- [x] tools/gen_sosok_bagian.py (+ _rambut.py, _busana.py): 352 sheets (kulit 16, wajah, rambut 17 styles + cubes,
      busana 22 styles x2 widths + tepi1/2, buzi, tambahan, kepala, kaki, cubes, luka); scratch dbg compose read: OK
- [x] tools/gen_sosok_resep.py -> assets/kultivator/sosok/resep.json (weights, colours, roles 40.4.1 A-D, pangkat, jalan)
      + tools/sosok_rupa.py (Python RupaResolver) + tools/sosok_iso.py (iso render)
- [x] tools/gen_sosok_cadangan.py -> textures/sosok/cadangan/ (48 = 2 kelamin x 4 usia x 6 kelas)
- [x] tools/gen_sosok_pratinjau.py ran -> tools/keluaran/sosok/ (rambut_L/P/A, busana_1-4, peran_01-12, galeri, contoh, wajah)
- [x] Read each preview + log below (session 2); sosok_iso.py top crop fixed (canvas +5 px rows), previews regenerated
- [x] tools/gen_sosok_prop.py: 26 props 63-88 (max 18 elements), 44 textures 32px, reuses gen_tempat_models helpers;
      iso sheets prop_1/prop_2.png via tempat_iso.py
- [x] tools/cek_sosok.py: 351 sheets, 56 cubes, 143 rows, 0 errors 0 warnings (resep K/U now follow SosokGen.java)
- [x] Java kulit: SplitMix64, Palet, SosokRupa, Resep, RupaResolver (port 1:1), BagianCache, KulitPenyusun, KulitCache,
      KulitCadangan, LambangSalin; busana/BusanaKatalog + SosokGear written
- [x] Scratch harness k40/jc.sh (javac 0 errors) + k40/parity (gen_cases.py + Parity.java): 300/300 looks identical
      (cache key + atlas FNV); hooks Resep.pasangUntukUji / BagianCache.isiUntukUji (package-private, harness only)
- [x] Java busana: BusanaKatalog, RambutBagian, BusanaBagian, SosokTubuhModel, SosokBusanaModel, WajahModel, SosokLapisan,
      BusanaLayer, WajahLayer, SosokGear (compiled with javac against cp.win)
- [x] Self-review: all files LF, client-only, no ItemStacks, MC API checked in mcsrc + javac vs real 26.3 jars;
      no per-frame allocation in SosokBusanaModel; parity 600/600 after last change

## Keputusan (kontrak 40K)
- Integer-only colour math (tangga with LIN/SRGB tables stored in resep.json "tabel"), integer alpha blend, integer
  noise hash -> Java and Python atlases pixel-identical without float pow differences.
- Layer = (sheet, src rect, dest xy, tetap sheet); eye/brow/mouth sheets packed 128x8 (16 states, state i at x 8i);
  face strips B1/B2 recomposed by clipping all layers to head front (8,8)-(16,16).
- Per-group RNG: SplitMix64(benih*0x100000001B3 ^ fnv32(group)); weighted pick (nextLong>>>11)/2^53*total.
- (diganti) Layers read 40R's SosokRender.Keadaan + 40G's GerakData (no 40K render-state key needed).

## Pratinjau dibaca (40.3.11) - semua dibuka dengan Read
- wajah.png: dibaca, baik (16 mata + 16 mulut per wajah terbaca; kilat ambar Api, batuk darah merah)
- contoh.png: dibaca, baik (5 contoh 40.4.4: murid P Es, Tetua Api guan giok, penjaga, anak, nenek)
- galeri.png: dibaca, baik (128 sosok beda kulit/rambut/busana)
- rambut_L/P/A.png: dibaca, diperbaiki: puncak kepala terpotong di iso (sanggul/guan/caping) -> kanvas digeser 5 px
- busana_1-4.png: dibaca, baik (22 gaya; kerah silang kanan-atas-kiri benar; LAUT rompi bergaris; KAFAN kebalikan)
- peran_01-12.png: dibaca, baik (143 baris; seragam per peran terbaca dari 1x; tidak ada slot kulit salah)
- contoh.png + rambut_P.png (ulang): dibaca, diperbaiki: iso slim arm pivot y 2 (+0.5 px), belt hangings and sleeve
  pockets flattened like BusanaBagian (jade pendant now shows), cepol/sanggul visible above head
- prop_1/prop_2.png: dibaca, baik (26 prop; jimat bertulisan, guqin 7 senar, luopan trigram, tasbih 12 manik)

## Langkah berikutnya
SELESAI (W1). Finish agent: no generator run needed (assets on disk); optional re-check `python -I tools/cek_sosok.py`.
Harness: scratchpad/k40/jc.sh (javac), scratchpad/k40/parity/{gen_cases.py N, run.sh} (Java vs Python atlases).
Tools: gen_sosok_pratinjau.py --paket <packed> "<rupa>" mirrors `/sosok dump benih` (realm 1) pixel for pixel.

## Butuh dari modul lain
- 40R SosokRender.hashMasukan: mix gaya.luka() (luka 1 paints the leg bandage; kunciAtlas only covers luka 2).
- 40R HumanNpcRenderer headwear layer: skip Headwear kinds with SosokGear.sembunyiHeadwear(kind) == {"*"}
  (GHOST_HAIR, GHOST_EYES) on Sosok bodies (40.4.5); hats covering the head come from SosokGear.menutup().
- 40G PropKatalog: 63-88 models exist at kultivator:fx/sosok/<PropId.nama> (= PropId.jalurModel).
- 38NL (W2) impostor: KulitCadangan.untuk(kelamin, usia, kelas) with kelas of KulitCadangan.KELAS.
- 38MD/38TV: register covering hats with SosokGear.daftarTopi(kind, menutup, parts...) / daftarHeadwear.

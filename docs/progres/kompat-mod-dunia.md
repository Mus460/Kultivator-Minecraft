# Kompat mod dunia (Terralith, Tectonic, Structory, D&T, Lithostitched, Amplified Nether, Nullscape)

Checkpoint (agent kompat-mod-dunia). Baca dulu sebelum melanjutkan.

- [x] 1. Inventaris structure set Kultivator + mod + vanilla
- [x] 2. Cek tabrakan salt/spacing (`tools/cek_kompat_mod.py`): 0 tabrakan
- [x] 3. Perbaikan keep-out dua arah (`TempatKeepOut.saring` + `ChunkGeneratorTempatMixin` WrapOperation `Structure.generate`)
- [x] 4. Jalan tidak memotong struktur asing (`StructureStartJalanMixin`)
- [x] 5. Tinggi Tectonic: istana langit diangkat (tag `#kultivator:melayang`); Nether/End: tidak ada struktur kita
- [x] 6. Build (`compileJava compileClientJava jar`)
- [x] 7. Harness: run_38 / run_all / langitG run_fin OK, cek_tempat 0 error, KompatCheck (scratchpad `kompat/wg`) OK
- [x] 8. Dokumen + PANDUAN 69.6

## Inventaris (mod terpasang di `%APPDATA%/.minecraft/versions/SODIUM/mods`)

| Mod | Structure set | Catatan |
|---|---|---|
| Dungeons and Taverns 6.0.2 (`nova_structures`) | 35 (33 random_spread, 2 concentric_rings) | kedai spacing 50/30, sumur 32/8, gua kecil 16/6, shrine size 20 maxd 128, desa 51/16; 11 underground_structures, 7 underground_decoration (sebagian di Nether/End) |
| Structory 1.3.18 | 5 | 11 struktur memakai step `raw_generation` (reruntuhan, kapel, firetower, kemah) |
| Structory Towers 1.0.19 | 5 (overworld, Nether, End) | menara size 6, maxd 64 |
| Terralith 2.6.5 | 8 | menara penyihir step `fluid_springs`; "underground" ber-step surface_structures; benteng desa |
| Nullscape 2.0.1 | 4 (End) | menimpa `minecraft:end_cities` |
| Tectonic 3.0.31 | 0 | medan; config pengguna `max_y 320`, `min_y -64`, `vertical_scale 1.125` |
| Amplified Nether 1.3.0, Lithostitched 2.0.6, Yeetus Experimentus | 0 | Lithostitched hanya `@Inject` di `StructureStart.placeInChunk` (cocok dengan WrapOperation kita) |
| Vanilla 26.3 | 20 | `StructureSets.java` |

Kultivator: 105 set; 29 memakai grid vanilla (random_spread, jauh_dari_pusat, cincin_tempat), 76 dari rencana
(rencana_tempat, gerbang_sekte, sekte_langit, jalan, dimension_origin) sehingga salt tidak menentukan posisi.
Struktur kita hanya di overworld dan dimensi sendiri (bioma `kultivator:*`, tidak ada set mod lain di sana).

## Temuan

1. Tabrakan salt/spacing: **tidak ada** (tidak ada set dengan spacing+separation+spread+salt sama).
2. Keep-out lama hanya menolak step `surface_structures` dan hanya mengecek titik tengah chunk start: Structory
   (`raw_generation`) dan menara penyihir Terralith (`fluid_springs`) lolos ke ibu kota, potongan jigsaw asing
   (maxd 80-128) bisa masuk dari luar.
3. Kampung, kota lama, gerbang sekte, penjara, menara seratus lantai, gua, situs dan pos jalan tidak punya keep-out:
   desa vanilla, kedai D&T, reruntuhan Structory, menara Structory Towers bisa menembus mereka (harness: desa salju
   vanilla di atas desa klan, portal rusak di tengah desa).
4. Jalan tidak mengecek struktur asing.
5. Istana Awan (absolut y 204-228, inti 48x52x48) di atas gerbang gunung: dengan Tectonic puncak bisa menembus.
6. Peta `mulai` rencana (kota besar, kota tema, kompleks, pos pemburu) memakai `Map.copyOf` dengan kunci chunk
   `(x<<32)^z` (hash x^z): probing linear sangat panjang. `CincinPlacement` memanggilnya ribuan kali per kandidat.

## Perbaikan

- `TempatKeepOut.permukaan(step)`: semua step kecuali underground_structures/underground_decoration/strongholds dihitung
  permukaan (keep-out rencana sebelum start).
- `TempatKeepOut.saring` (setelah `Structure.generate`, lewat `@WrapOperation` di `tryGenerateStructure`):
  - struktur `#kultivator:melayang` diangkat sampai dasar 16 blok di atas tanah tertinggi di bawah potongannya
    (dibatasi `maxY` dunia);
  - start mod lain/vanilla (bukan stronghold, bukan `#kultivator:boleh_di_tempat`) ditolak bila potongannya + 12 blok
    (6 vertikal) menyentuh kotak keep-out rencana yang lahir (overworld, step permukaan), atau potongan start Kultivator
    mana pun dalam 8 chunk. Start Kultivator diprediksi persis seperti `createStructures` (uji placement, pilihan
    berbobot, `tolak`, generate, cache per seed); urutan chunk tidak berpengaruh. "Kultivator didahulukan": tempat kita
    tidak pernah ditolak, struktur lain hanya ditolak di jejak kita.
  - `createStructures` HEAD/RETURN menyerahkan `ChunkGeneratorStructureState` lewat ThreadLocal.
- `StructureStartJalanMixin`: potongan `kultivator:jalan` yang bagiannya di chunk menyentuh potongan struktur asing tidak
  dipasang (jalan mengalah; semua start sudah diketahui di tahap features).
- Peta `mulai` rencana jadi `Long2ObjectOpenHashMap` (fastutil, hash dicampur): waktu `saring` turun dari rata-rata
  28 ms ke ~7 ms (median 0,3 ms) di harness.
- `tools/cek_kompat_mod.py`: baca jar mod, laporkan tabrakan grid, struktur melayang tanpa tag, step non-surface.

## Verifikasi

- Build `compileJava compileClientJava jar`: BUILD SUCCESSFUL.
- `final/wg/run_38.sh`: tpl 0 problem, WgCheck38 ALL OK, PLAN OK, TEMA PLAN OK, cek_tempat 0 error.
  `run_all.sh` ALL OK, `langitG/wg/run_fin.sh` OK. `tools/cek_kompat_mod.py`: 0 error.
- `kompat/wg/run_kompat.sh` (KompatCheck: vanilla + data kita + Structory + Structory Towers + D&T [+ Terralith],
  overworld nyata, jalankan logika `createStructures` + `saring` tanpa mixin, lalu cek tumpang potongan): KOMPAT OK di
  4 wilayah (seed 12345 sekitar ibu kota calon, 3000/3000, desa salju; seed -987654321 dusun + Terralith). Contoh
  penolakan: `minecraft:village_snowy` di atas `kampung/klan_salju`, `minecraft:ruined_portal` di desa.

## Sisa / batasan

- Tidak bisa diuji di harness: Tectonic (medan dari kode Java), jalan (tahap features), mixin itu sendiri (target
  diverifikasi dengan javap terhadap jar game 26.3). Perlu dunia baru untuk melihat hasil.
- Beard (tanah yang diratakan) potongan jalan yang dilewati masih terhitung; paling 1-2 blok di tepi struktur asing.
- Stronghold tidak pernah ditolak; gua kita bisa bersinggungan dengan stronghold.
- Struktur asing besar (mineshaft, ancient city) memicu prediksi area luas; biaya sekali per area (cache), median 0,3 ms.

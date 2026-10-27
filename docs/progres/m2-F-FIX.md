# m2-F-FIX: penutup perbaikan audit

Build: `compileJava compileClientJava jar` BUILD SUCCESSFUL. mergeLang bersih. `tools/cek_sosok.py` 0 error, `tools/cek_tempat.py` 0 error. Jar: `$TEMP/kultivator-F-FIX.jar`. Tidak di-commit.

Rincian per temuan ada di `m2-FIX-*.md`. Di sini hanya butir "needs" yang diterapkan dan yang dilewati.

## Needs yang diterapkan
- murid-inti #1 (Soul Flight meninggalkan makam): `Makam.tick` kini juga melewati badan yang `departed()`.
- `Disciples.arrive` null pada jiwa yang dilupakan (loop tanpa akhir): `Disciples.forgotten(server, tag)` dipakai untuk mengakhiri catatan di `Crossing.processHome`, `RealmRun` (2 tempat), `Kapal.landPending`, `KapalRohEntity.landDisciples`, `YangPergi.bringBack`.
- `BadaiPasir:299` dan `Mural:394`: sentinel `Long.MIN_VALUE` menjadi `Long.MIN_VALUE / 2`.
- DISCONNECT di `client/**` (opsional): 48 file dibungkus `client.execute(...)` lewat skrip, akhiran baris dipertahankan. `KotaHidupClient` dilewati (peta `ConcurrentHashMap`, aman).

## Dilewati
- sekte gabung #3 (murid titipan): fitur belum dibangun (KolamRoh x1.15, kebal kompleks, jalan SEKTE_TAMU, turnamen). Butuh fitur terpisah.
- sekte-hidup #4 (`langit.feriMisi` tak terbaca): fitur belum dibangun.
- sekte-hidup #9 (`bolehPergi`): pergi bukan mati; keputusan aturan ada pada pemilik.
- sekte-aula #8 (`vassalVillages=false` membekukan penjaga): perilaku flag memang begitu.
- kota42 #10 (`GOSIP_LAHIR`): event ekstensi publik. #8 empat field config tanpa konsumen: ditandai reserved.
- npc-sosok #8/#9: sudah dikerjakan di kota42 (Perkara, PeristiwaJalan).
- inti-ui 4c sebagian (peta statis tidak dibersihkan): salah, `clearAll()` sudah jalan di SERVER_STOPPED.

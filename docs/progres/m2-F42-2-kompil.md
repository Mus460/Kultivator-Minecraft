# m2-F42-2-kompil — tree compile lagi setelah 42-G2 dihentikan

## Checklist
- [x] Build awal: 47 error compileJava (TokohKota, Rombongan, DistrikMalam, Guanpu, TeaterBayang, Upacara.init/klik/pukul,
      LembarGuanpu/Kantor/Pakta/Cabang/Upacara, LembarKunci.TOKOH, TanyaJalan distSqr, LembarKavling put)
- [x] Stub server + 3 perbaikan tipe (lihat bawah)
- [x] compileJava + compileClientJava + jar BUILD SUCCESSFUL (build ke-2)
- [x] Catat stub di m2-42B, m2-42D, m2-42D-P, m2-42U-2 ("Butuh dari modul lain")
- [x] Jar disalin ke $TEMP/kultivator-F42-2-kompil.jar, build-log.txt dihapus

## Stub (semua bertanda `// stub F42-2-kompil` / javadoc "stub F42-2-kompil")
- 42B: `dunia/kota42/bicara/TokohKota.java` baru — init/muat/harian/tick/lupakanSemua/tandaiTubuh no-op,
  nama = WargaApi.nama, gelar null, laporan 1 baris.
- 42D: `dunia/kota42/acara/Rombongan.java` (diKota null, sedangPentas false, uji literal "rombongan"),
  `DistrikMalam.java` (init no-op, ujiMalam literal "malam"), `Guanpu.java` (buka no-op, uji literal "guanpu"),
  `TeaterBayang.java` (sedangPentas false, irama no-op, uji literal "wayang").
- 42D-P: `acara/Upacara.java` + `init()` no-op, `klik(...)`/`pukul(...)` false.
- 42U-2: `dunia/kota42/ui/Lembar{Guanpu,Kantor,Pakta,Cabang,Upacara}.java` baru, `init()` kosong.

## Perbaikan nyata (bukan stub)
- `ui/LembarKotaDasar.java:408` `LembarKunci.TOKOH` -> `LembarKunci.TOKOH_ID` (nilai sama "tokoh").
- `ui/LembarKavling.java:253` `tag.put(KotaLembar.Kavling.SILUET, s)` (map cache SILUET menutupi kunci).
- `bicara/TanyaJalan.java:483` `long d2` -> `double d2`.

Tidak ada registry/payload baru atau dihapus; tidak ada file 42-G2 lain disentuh.

## Langkah berikutnya
Selesai. 42-G2 dilanjutkan nanti (setelah desain 43) mengganti stub di atas.

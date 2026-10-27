# m2-D-42EF — detail pass 42E (sekte di kota) + 42F (kota tumbuh / bangun kembali), wave D-41-42

Owns: kota42/sekte/**, kota42/tumbuh/**, lang kota_hidup_sekte + kota_hidup_tumbuh, old kotabesar/{GelombangBinatang,HariGerbang},
kampung/AnakBerbakat, MandiriApi, perbaikan/{PerbaikanApi,PekerjaanPerbaikan,Cetak}. No Gradle, no commit. Not owned: perbaikan/Tukang.
Checked with plain javac 25 (tumbuh/** + sekte/**, -Xlint:all: 0 errors, only a pre-existing this-escape warning) and a
scratch assert test (CekPapan: lot post geometry, migrant dress roles) in the scratchpad.

## Done
- [x] 1 migrant builders as bodies in the neighbour town's dress: tumbuh/TukangPendatang.java (2 bodies by day when the project
      is > 500 blocks; role = JenisTempat.tukang of the nearest other town, Devil City stays home; walk in from that side,
      plank/hammer/plaster clips; arrival line; the x1.33 stays in BangunKembali.pengali)
- [x] 2 KERJA_KOTA disciples as builders: TugasKotaMurid post `bangun` (tugaskan, pembangun(), pos/pose/gerakkan, diary
      `kerja_bangun`, home after 2 quiet days) + tumbuh/Pembangun.java (lokasi, titikKerja, faktorMurid = 1 + n/3, max 3)
      wired into PerbaikanApi.setPengali (KotaTumbuh)
- [x] 3 tent beds by household: one tent per refugee house (2-8), one mat per resident (max 3), cache only once residents read
- [x] 4 war refugees + blockade: tumbuh/Blokade.java (KotaBus.SERBUAN: blockade 2 days while raid, +0 won / +3 lost; production
      x0.5 and no cart in BahanKota; no new plot starts; houses by the wall -> tents; no repair job/bill); KotaTumbuhData.BLOKADE;
      `uji kota_hidup tumbuh blokade [hari]`
- [x] 5 Mandor Gong Shu dialog already opens the plot sheet (`buka_layanan kavling`, 42B-C data; LembarKavling registered);
      plus tumbuh/PapanKavling.java: right-click the lot's board post (empty hand) opens the sheet at the lot
- [x] 6 audit 42.17 #23-26 in code: pamit (peluk + bundle), Batu Uji, restu, pakta rebut/upeti/Bela Kota, JAGA_KOTA, cabang,
      growth only via WorldBreaker (reason tumbuh), rebuild only from TEMPAT_RUSAK (attributed damage), no block in Blokade
- [x] 7 lang: kota_hidup_tumbuh 114 keys, kota_hidup_sekte 221 keys, identical id/en, no duplicates (checked)
- [x] 8 tumbuh/Peresmian.java: opening ceremony on KotaBus.KAVLING (stage 5): lion + 2 dancers inside + 2 drummers, officer with the
      big scissors and red ribbon, speech, cut (paying player gets the scissors, Nama Baik +2); thanksgiving incense after a rebuild
      (BangunKembali.selesai): officer + 2 craftsmen `sujud_dupa`, smoke, bell. Only while a player is near; no blocks.
- [x] 9 TuguKenangan.tick: newest memorial is read in chat when a player stands at the square (until a client inscription exists)

## Open / for others
- Client: "Dibangun dengan bantuan <pemain>" sign and a drawn Tugu inscription (no S2C payload of TuguKenangan exists; KotaPayloads
  is 42A0), blockade smoke, the "Kavling Kota" billboard text (Kavling payload has no sign name).
- Caravans/PerjalananWarga/KafilahJalan should stop while `Blokade.aktif(situs)` (not my files).
- Exact refugee-to-tent bed binding needs a TempatHuni hook (beds are keyed by a position hash); shortcut comment in
  BangunKembali.kasurTenda.
- Plots still only advance while visited (planning reads the loaded world). Nothing tested in game; Gradle not run.

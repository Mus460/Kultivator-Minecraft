# m2-ANIM murid-hidup (fixer)

Slice: sekte/mandiri, disciple breakthrough/cultivation, Candidates, PilihShifu, hubungan/Upacara, pill feeding, claim flag.

- [x] BERMAIN: act 18 (tea pose) retired for it (`Kegiatan.BERMAIN` act 0); `PelaksanaDasar.Bermain` plays MAIN_CUJU + ball prop (dry) / MAIN_KEJAR as base clips every 80 ticks, faces the partner, `SosokApi.henti` at the end.
- [x] Breakthrough: `DiscipleBreakthrough.succeed` plays TEROBOSAN_BERHASIL, `fail` plays TEROBOSAN_GAGAL (held clip, given 100 ticks). Mandiri `TerobosanSendiri` runs the same `DiscipleBreakthrough` (start/succeed/fail), so it is covered; no change there.
- [x] SUJUD_GURU on acceptance: `Candidates.accept` (faces the master, clip 110 ticks, Bowing hold raised 30 -> 110, xRot hack removed).
- [x] PilihShifu: the player takes a teacher -> `GabungPayloads.kirimKlip(player, KOWTOW)` (a player can only play KLIP_PEMAIN; KOWTOW 302 is the player form of SUJUD_GURU).
- [x] MAKAN_PIL: `DiscipleCultivation.feed` (player feeds) and `feedSelf` (disciple's own bag).
- [ ] PASANG_BENDERA: skipped, no sect claim/territory code exists (the hall stele is placed by the player, who has no Sosok body). See the report.
- [x] Sumpah saudara / restu Dao: the seats before the stele and facing already existed (`GiliranSosial.seatsAtStele` + look control); `Upacara.finale` at completeOath/completeWedding: face each other, HORMAT, then BERSULANG (396), held still 100 ticks.

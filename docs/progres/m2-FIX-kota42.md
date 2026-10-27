# m2-FIX-kota42 (fixer kota42)

Reports: audit-kota42.md (12), audit-npc-sosok.md (Perkara #8, PeristiwaJalan #9), audit-inti-ui.md (KotaAcaraItems lang).

- [x] K1 11 dead advancements granted at the real event: sapa (WargaApi.peristiwaPemain, 5 residents remember the player), gosip (BicaraWarga.tanyaSumber reaching the witness), tolong (PermintaanWarga.selesai, 10th done, counter `s` in p_<uuid>), rumah (RumahKota.beli), janji (Cakap.tepati), lidah_perak (KeadaanPercakapan.pilih, 4 different successful tones, mask in KotaBicara.Data "nada"), nguping (ObrolanWarga.dengarAkhir), dua_keluarga + lao_geng (AkibatKota TempatEvents.QUEST finished), jalan_namaku (AkibatKota.namaJalan), bangun_kembali (BangunKembali.selesai, donors)
- [x] K2 donor share capped at what the donor gave (Investasi.sahamKavling 6-arg, PekerjaanTumbuh passes `given`)
- [x] K3 PeristiwaJalan fire: chain members who do not scrub are released (= npc-sosok #9)
- [x] K4 `/kultivasi kotaku acara` registered in KotaLayar.perintah (opens Buku Kota tab acara)
- [x] K5 loot tables rak_dagang, meja_kasir, papan_nama_toko
- [x] K6 Perkara.tubuhSaksi: no second body of the same witness (= npc-sosok #8)
- [x] K7 lang item/entity names in kota_hidup_acara.{id_id,en_us}.json (= inti-ui KotaAcaraItems)
- [x] K8 dead config: antiUlangJam wired into AntiUlang; ulangHari, copetAmbilBarang, rombonganJumlah, serbuanRusakNyata marked reserved (wiring = new features; defaults already match behavior)
- [x] K9 KotaUji help chips for the non-existent `visual` sub removed
- [ ] K10 GOSIP_LAHIR no listener: skipped (public extension hook, no defect)
- [x] K11 client town fx gated to the overworld (Bingkai.tik, SuasanaFx.kumpul); DISCONNECT in SuasanaFx and UjaranClient via client.execute
- [x] K12 Upacara test command: dewasa.isEmpty() guard in the birth branch

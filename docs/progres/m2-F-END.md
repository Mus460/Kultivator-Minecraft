# m2-F-END — final finish of 41 + 42 (build B3)

Scope: jadwal-41-42.md section 4 "F-END". No commit, no jar copy. Status: DONE.

## Checklist
- [x] B3a: first Gradle run GREEN (5m28, no compile errors from the whole D wave, mergeLang ok)
- [x] 1. D-wave hand-offs wired (list below); the rest listed under "Not wired"
- [x] 1b. Generators re-run (gen_gabung_cerita, gen_tempat_kompleks, gen_dunia_kota, gen_kota_hidup_cakap + --golden-tulis, gen_kota_hidup_assets, gen_kompat_bioma 53 tags): resource tree md5 identical before/after (idempotent)
- [x] 2. Voices: 133 tuples (28 gabung + 105 kota42) from m2-voices.md parsed into tools/gen_voices.py (KV41_42); gen_voices run: 133 new .ogg, no old clip or lang key changed (voice.* lang +266 keys, id/en identical); 60 synth entries merged into sounds.json; GABUNG_SIAP and KOTA42_SIAP = true; every VoiceEvents key has a sounds.json event
- [x] 3. cek_tempat 0 errors (105 sets, 1365 templates), cek_sosok 0/0, cek_cakap OK (49 golden), cek_kompat_mod 0; B3b GREEN (5m54, after wiring); harness run_38 / run_all / run_fin / run_kompat all OK
- [x] 4. Docs: DESIGN.md ## 41. and ## 42. (LF); PANDUAN.md ch 75 + 76 (CRLF; 74 was taken by design 43) + TOC + gabung skala line; README two bullets (CRLF); CREDITS.md; FASE-41/42.md final
- [x] 5. Hard-rule greps clean, EOL checked (two CRLF progress docs restored), stray logs/ moved to scratchpad, jadwal checkpoint updated

## Wired in this finish
Titik.alat() reads data "alat" (42W kit alias); Perkara.putuskan -> TujuanKota.perkara; Copet.tangkap -> TujuanKota.kejarCopet;
Pinjaman interest + PaktaLindung.ambilUpeti -> Investasi.catatKeran; KegiatanKelompok uses KotaConfig.warga().kelompokPeluang;
TurnamenAgung champion -> TokenNaik.beriUndangan; SyaratMasuk skips the DIUSIR wait when CeritaGabung.sudahTebus;
KerjaPeran +8 entries for resident 42 roles (PENJAGA_MALAM, MAK_COMBLANG, PENDONGENG, PENJUAL_OBAT, GURU_SEKOLAH, PENGURUS_DUKA,
PENJAGA_GUDANG, PIALANG). Already wired earlier: TitikSiar nama_jalan. Voices and sounds.json as above.

## Not wired (next wave)
- 42: Rombongan.pemusikSewaan in Upacara; LembarUpacara.daftarAksi(AksiRombongan); Kebiasaan.faktorTawar (no haggling hook in TokoPemain);
  LembarToko KE_BANK -> TokoPemain.keBank; LembarBuku cap via batasHarian; Kota.bukaLama for the 34 NpcRole.tab>=0 roles; MusimKota .dingin look;
  clip 443 tiru; Blokade.aktif stop for caravans/travellers; refugee-tent bed binding; client signs/Tugu inscription/blockade smoke; Diri > Harta sub.
- 41: gantiKetua/lukai on succession and lost war; jubah_mangkuk.restu / rahasiaBocor / pemburuSampai readers; KLIP_PEMAIN > 13 and player chore clips;
  absorbed-sect emblem redirect, branch decal, succession camera (client); duplicate `gabung naik` registration (works, last wins).
- Not played in game: nothing was run in game (only Gradle compile, python generators/checkers, worldgen harness).

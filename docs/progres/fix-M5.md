# fix:M5 Hub, Ensiklopedia, HUD dan Istilah

Rencana: docs/progres/audit-integrasi.md bagian M5. Detail bukti: audit-ux-laporan.md (U1-U21).
Gradle TIDAK dijalankan (finish agent build sekali). Akhir baris: lihat Masalah terbuka (pertahankan apa adanya).
Kunci lang BARU hanya di lang-src/ensiklopedia_sambung.{id_id,en_us}.json.

## Checklist
- [x] 1. T18 kartu bencana = TI Puncak (HubCardsPage)
- [x] 2. T9 SEKTE sub-koin Aula/Perguruan (subNames/showSub), sekte_* otomatis hallLock, gulir cadangan,
       kolom adaptif, kontrak HubCards, ikon beda
- [x] 3. S21 kartu DUNIA "Saingan" (KotaClient + SainganScreen baru); sesi 2: ikut kontrak M2 nyata
       (KotaNet.SainganAsk, groups/name/style/color/grudge/beaten/wins/losses/known/x/z, on, ambush/pressure, pending)
- [x] 4. S11 topik Ensiklopedia baru (duel_pedang, sekte_hidup, diplomasi_sekte, kebun_tambang, turnamen_sekte,
       glosarium, suara, tombol, slot sekte_langit) + fragmen ensiklopedia_sambung.*
- [x] 5. S11 tautan "?" di PetaScreen, MenaraScreen, DimensiScreen, HelmScreen, DuelScreen
- [x] 6. S11 help.turnamen (90 dtk), help.misi (sectevent); help.calon (hall=M6) dan help.serbuan (musuh=M2) -> needs
- [x] 7. T8 help.sects ditulis ulang (Prasasti Aula; /sekte = Saudara Sekte)
- [x] 8. S29 peta di QUICK_REACH; teks parry/duel_camera
- [x] 9. S30 label "HUD Kultivator", TribulationHud selalu tampil, HUD baru patuh hudVisible()
- [x] 10. S31/S32/S33 istilah hub, dunia_naga ...; R22 nama hub; R24 help.commands; R25 "?" Pengaturan;
        R26 sounds.json langit.*; R27 Prasasti Aula Sekte/Ketenaran
- [x] 11. Self-check: kunci lang identik, line endings, compile-review

## Berkas disentuh
- client/screen/HubCardsPage.java (peakLock, sub-koin SEKTE, withHallLock, glyphOf, kolom adaptif + gulir)
- client/hub/HubCards.java (kontrak)
- client/screen/ui/KeyHints.java (peta di QUICK_REACH, OWN_REACH parry/duel_camera, SealCorner)
- PetaScreen, MenaraScreen, DimensiScreen, HelmScreen (SealCorner), DuelScreen (HelpSeal widget)
- client/dunia/kota/SainganScreen.java (BARU), KotaClient.java (kartu saingan, rute SCREEN 3)
- HUD: TribulationHud (selalu tampil), DimensiHud (luka), KapalHud, NagaHud (stamina), LangitHud (chip), AlarmHud
  (hud||dangerAlert), LaporanCard
- SettingsPage HELP = visuals, momen, suara, hud, tombol (R25); HelpPage TOPICS + 10 topik baru
- Kunci baru yang DIPAKAI tapi belum ditulis: kultivator.hub.sekte.sub.{aula,perguruan}, kultivator.hub.sekte.perguruan.empty,
  kultivator.uxpages.key.unbound_{parry,duel_camera}, kultivator.hub.card.saingan(.tip), kultivator.saingan.ui.*,
  kultivator.screen.help.{duel_pedang,sekte_hidup,turnamen_sekte,diplomasi_sekte,kebun_tambang,suara,hud,tombol,glosarium}

## Langkah berikutnya
SELESAI (sesi 2). Berkas tambahan sesi 2: lang-src/ensiklopedia_sambung.{id_id,en_us}.json (48 kunci, identik),
SainganScreen (kontrak M2 nyata), KeyHints javadoc, sounds.json (-2 entri voice.langit.*), ogg voice/world/langit/* dihapus,
tools/gen_voices.py + tools/voice_manifest.json (-2 baris). Self-check: kunci unik dan identik di seluruh lang-src (15082),
format %% benar, akhir baris sama dengan HEAD, API (CompoundTag getStringOr/getIntOr/getBooleanOr/getListOrEmpty) di mcsrc.
Tidak ada angka gameplay yang diubah oleh M5 (tidak perlu catatan Perubahan keseimbangan).

## Masalah terbuka
- CRLF hanya di lang-src/core.*.json dan hud.id_id.json; berkas lain milik M5 LF.
- help.calon (hall.* = M6) dan help.serbuan (musuh.* = M2) bukan fragmen M5 -> needsFromOthers.
- needsFromOthers: M6 VoiceEvents hapus grup LANGIT + subtitles voice.langit.*; M6 hall.ui.kantor/jabatan/dpower/sekte_misi
  'hub'; M3 kota.tab.titah + 'Abadi Sejati' dunia_kota; pemilik duel_inti/duel_ui 'Hub ›' -> 'Gulungan ›'; M1 'menu utama';
  37 menulis kultivator.screen.help.sekte_langit(.title).

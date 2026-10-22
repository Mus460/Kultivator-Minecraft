# audit:ux progres (read-only audit, laporan: docs/progres/audit-ux-laporan.md)

- [x] hub/keys/akses cepat (HubCardsPage, HubCards, QuickWheel, Keys, KeyHints)
- [x] lang parity/dup/arg (OK: 14753 kunci, 0 dup, 0 beda arg), literal keys OK
- [x] guide checks (semua adv ada + ada pemberi), GuideTracker
- [x] help topics (HelpPage TOPICS vs lang; Duel & Sekte 36 tanpa topik)
- [x] README/PANDUAN angka Kitab Panduan
- [ ] settings/HUD/notif/voices
- [ ] terminologi lanjut (realm names, Rangkai Segel, sekte vs aula)
- [ ] sekte 36/37 integrasi
- [ ] tulis laporan

Temuan sementara (catatan):
F1 hub SEKTE 17 kartu meluap di 320x240 (HubCardsPage.layoutCards 240-256; kontrak HubCards "maks 1 kartu/modul")
F2 Ensiklopedia tanpa topik duel/sekte36; layar baru tanpa "?" (Peta, Duel, Turnamen, sekte screens)
F3 README 14 bab/133 langkah, PANDUAN 124 langkah; kode 16 bab/153 langkah
F4 GuideTracker seal bab: baris 190 chapter+1 vs 150 chapter+offset+1
F5 tracker hilang setelah Jalan 15 selesai walau Kitab Panduan bab 13-16 belum; visible() 86
F6 "sects" help: "Dirikan sekte dengan /sekte create" vs hub "Dirikan sekte" = Prasasti; "Prasasti Sekte" vs item "Prasasti Aula Sekte"
F7 "Hub ›" (duel) vs "Gulungan ›"
F8 tombol [[key:peta]] -> "tombol tidak diikat"
F9 Tangga card kirim perintah chat & tutup hub
F10 README Cincin Segel (harus Rangkai Segel), config list kurang dunia/duel/sekte

Next: settings/HUD/voices, terminologi realm, sekte 37

# m2-AU-inti: perbaikan audit ulang GUI 43, irisan "inti" (host & navigasi)

Sumber temuan: `gui-audit-ulang-inti.md` (14 temuan). Berkas irisan: `screen/CultivationScreen`, `screen/Page`, `hub/**`,
`Keys.java`, `KultivatorClient`, `lang-src/{hub,gui43}.*.json`. Tanpa Gradle.

## Daftar periksa
- [x] 1. Tablet Di Sini = lompatan ke rumah (`DiSini.rumah()`, `CultivationScreen.tampilkan` mengalihkan DI_SINI ke rumah); `angka(n)` di halaman yang memegang konteks (`memegangKonteks`), jadi penerus angka halaman gabungan hidup
- [x] 2. `LembarHub.Halaman.onHide` kembali lebih awal selama host memegang konteks; `DiSini.lepas` memanggil `HubNav.tutupKonteks(null)` bila host masih memegang
- [x] 3. Judul kepala Di Sini tanpa ulangan (sudah dikerjakan irisan disini #14)
- [x] 4. Lebar rel bernama = nama bagian terpanjang (tebal) + 24, maks 100 (dihitung di `init`)
- [x] 5. Konteks yang tidak tampil tetap di-tick (`CultivationScreen.tick`)
- [x] 6. Konteks bertahan saat Gulungan diganti cincin/obrolan (`HubNav.menungguKembali`); KeyBindsScreen: perlu SettingsRows (lihat Butuh)
- [x] 7. M membuka Murid › Murid lalu bertanya ke server
- [x] 8. F1 = segel "?", Shift+←/→ = chip Sumber (`gantiSumber`); petunjuk kaki dan tip Sumber id/en menyebutnya
- [x] 9. Varian warna latar (`warnaLangit`): Gaya halaman tampil, senja Titah Langit, langit abadi Keabadian
- [x] 10. Denyut tablet Di Sini saat Gulungan dibuka (sudah dikerjakan irisan disini #15)
- [x] 11. `HubPages.rumah` satu-satunya pencarian rumah (mount `daftarLembar` dulu, lalu `daftarRumah`, urutan sama); `LembarHub.rumah` tinggal sampai UiTempatClient hanya memakai HubPages (lihat Butuh)
- [x] 12. Kode mati dibuang: HubNav.openHall/openTitah/openHub(int)/openHub(int,int)/SUB_ENSIKLOPEDIA (+impor TitahSettings; openSettings langsung), CultivationScreen(int)/showSection, Urutan.POHON/LAINNYA/bagianDari/urutan, Tujuan.of(String), HubPages.ada/versi, Page.subNames/showSub(int)/urai indeks/hoverable. SUB_KITAB, HubNav.adaKonteks, lihatDariHub sudah dibuang irisan disini
- [x] 13. Kunci lang tak terpakai dibuang (56 `kultivator.hub.*` + `kultivator.gui43.host.lainnya`, id_id dan en_us)

## Butuh irisan lain
- `tempat/ui/UiTempatClient` (`rumah`, `terima`): buang cabang `LembarHub.rumah`, pakai hanya `HubPages.rumah` (sub = r.sub() atau tab); lalu `LembarHub.rumah` boleh dihapus.
- `hall/TreasuryPage.sembunyikan`: sekarang berlebih (pengaman ada di `LembarHub.Halaman.onHide`), boleh diganti `p.onHide()`.
- `screen/SettingsRows.openKeyBinds`: panggil `HubNav.kembaliSetelah(KeyBindsScreen.class)` sebelum `setScreen`, agar konteks Di Sini bertahan.

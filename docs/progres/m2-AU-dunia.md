# m2-AU-dunia — perbaikan audit ulang GUI 43, irisan "dunia"

Sumber: side-session-notes `gui-audit-ulang-dunia.md` (commit 1e305fa0). Fixer irisan dunia (Diri, Teknik, Dunia,
Keabadian). Tanpa Gradle, tanpa commit. 42-G2 dan `*.deactivation` tidak disentuh.

## Daftar centang
- [x] B-1 KehancuranClient.openConfirm: sumpah diri (arg 0) mendarat di sub "diri", lepas murid di "murid"
- [x] M-1 DuniaClient: hapus version(int)/version()/requestAll/requestAbadi/open/openAbadi + VERSIONS; javadoc
- [x] M-2 Codex.open/kehancuran, CodexGrid.cardSize, TeknikSession.globalCooldownLeft, DuelClient.localMode/sinceResult
      (+ medan lastResultAt yang jadi tulis-saja)
- [x] B-2 tooBig: AbadiClient.mulai(gulir, lebar, channel) menaruh baris `sect_fix_feat.too_big` di awal gulir Alam Abadi
      (CH_ABADI), Purba/Bencana (CH_PURBA), Batin/Ciptaan (CH_BATIN); PusakaPage menaruhnya di awal kolom info (CH_TEMPA)
- [x] G-3 gulir teks: JalanPage.Gulir -> screen/ui/GulirTeks (+ mode isi per bingkai mulai/tambah/jarak); AbadiClient.Baris
      dihapus (5 halaman Keabadian), MenaraScreen memakai GulirTeks (kepala, teks, bilah kemajuan Bilah.gambar bertanda
      sepersepuluh); HelpPage, GuideScreen ikut ganti tipe (hanya nama kelas)
  - needs: pindah sekte/wilayah/GulirIsi ke screen/ui menyentuh impor berkas sekte (AulaPage, IdentitasPage, TreasuryPage,
    PaviliunScreen, BalaiTetuaScreen, Ikhtisar). Gandanya sudah hilang (GulirKolom .deactivation oleh fixer sekte).
- [x] K-2 Bilah: bilah dendam SainganScreen dan kemajuan lantai MenaraScreen lewat Bilah.gambar
- [x] K-3 nama koin: AlamAbadiPage tanpa shortName (bawaan Page = judul "Alam Abadi"/"Immortal Realm"); kunci
      `spine9.tab.alam.short` dibuang id+en
- [x] G-1 Lelang kota K ganda: TownPages.Lelang dihapus, KotaJembatan tanpa sub Lelang (subDari menggeser tab sesudah
      TAB_LELANG; hanya Juru Lelang yang dibuka di TAB_LELANG dan ia ke KotaLelang). Kunci mati dibuang id+en:
      gui43.m15.lelang.{ke_juru,memimpin}, kota.lelang.{berjalan,dipamerkan,harga,kosong,langkah,memimpin,penawar,
      tip_tawar,titip}, kota.tab.lelang.short
- [x] G-2 Teh/Gelap/Misi/Pertemuan K: tidak dijembatani; dicatat sebagai K7 di 43.17 (keputusan pengguna)
- [ ] K-1 Kunci Naik Keabadian › Lelang: needs (LayarLelang final, di irisan disini; atau overload HubPages.daftar, irisan inti)
- [x] M-4 kunci lang mati (dicek: tidak ada literal penuh dan tidak ada awalan dinamis di src), 60 dibuang id+en:
      abadi.lelang.page.{consign,consign_line,consign_none,empty,leading,open,present,price,running},
      abadi.lelang.{step,step_tip}, abadi.page.status_title, codex.launcher.{kh.tip,kh_off,kh_on,open,open.tip,ring.tip,title},
      codex.ring.{clear.tip,clear_confirm}, codex.tab.{help,kehancuran,kodeks,ring}(.short), kapal.hud.{raider,weather},
      kapal.helm.dest.route.tip, musuh.ui.{encounter,encounters,no_encounters}, peta.{hint,title,penunjuk.inspired},
      purba.ui.sep, reruntuhan.jimat.ui.hold, reruntuhan.ujian.ui.title_n, screen.tab.techniques(.short),
      spine8.dunia.{header,key,title}, spine8.tab.{alam,desa,musuh}(.short), spine9.abadi.title,
      spine9.tab.{bencana,bencana.short,lelang.short}, goals.page.section.{daily,steps,trials}.tip,
      uxpages.musuh.to_defence.title
  - masih dipakai (tidak dibuang): abadi.lelang.consign, abadi.page.conv, codex.launcher.{counter,keys,mastery,power,
    selected}, peta.peta.source, spine9.tab.purba(.short); hub.card.* dan hub.lock.* sudah dibuang fixer inti
  - peta.card.sail.tip dipulihkan: tip tombol Berlayar di kartu penanda Peta (kapten)
  - abadi.lelang.source DIBIARKAN: needs baris sumber di LayarLelang (irisan disini; kontrak lembar tidak punya baris kaki)
- [x] L-1 teks menunjuk GUI lama (id+en): tablet Lelang/Batin/Ciptaan/Alam, layar Keabadian/Dunia, "Duel Pedang" sebagai
      halaman -> Gulungan › Keabadian › Lelang Abadi/Batin/Ciptaan/Alam Abadi, Gulungan › Dunia › Alam,
      Gulungan › Dunia › Duel (› Catatan, › Tantang › Duel Tonton); juga tablet Pusaka (-> Diri › Pusaka) dan tablet Bencana
      (-> Keabadian › Purba › Bencana Bintang). duel.cmd.screen: nilai saja, kunci tetap
- [ ] G-4 kit 38 di LayarBukuDagang/LayarJurnal/LayarPetaKota: needs irisan kit (LayarTempatDasar masih memberi Lukis kit 38)
- [ ] K-4 Bilah.grafik7 di Dagang/BukuKas: needs yang sama (grafik7 memakai Lukis screen/ui; lembar masih Lukis kit 38)
- [ ] K-5 PetaPainter.mini: dilewati; pemanggil pertamanya LayarTeleport/LayarBukuKas ada di irisan disini dan masih kit 38
      (M12/P6 menandainya opsional; disini #13 memegangnya)

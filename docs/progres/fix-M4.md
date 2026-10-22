# fix:M4 Kekuatan & Kultivasi Selaras - progres

Rencana: docs/progres/audit-integrasi.md bagian M4. Persetujuan angka: "Ubah angkanya" (semua disetujui).
Tidak menjalankan Gradle. Hanya berkas milik M4.

## Checklist
- [x] 1 T5 markRealmFoe (TechniquePower TAG_DUNIA_FOE) di konstruktor KultivatorNpc, KepitingBatu, UlarLaut, Yaksha, NagaLiar, RCreature; TreasureGuardians sudah TAG_ELITE (tidak diubah). TechniqueCommands baris info baru.
- [x] 2 T6: Meditation (G=sectGathering di luar min, clamp 1..1.5; pesan capTold `kekuatan.meditation.cap`), InfoPart.meditationLimit -> formasi.med (didaftarkan ZoneBridge), kunci formasi.medcap(.tip) untuk FieldPanel (needsFromOthers)
- [x] 3 S6: ProtectiveFormations.addProtectionProvider/insideProtection (insideActiveFormation TIDAK diubah: trib/push tetap), ZoneBridge.shelters (PELINDUNG/GABUNGAN running, CoreTicker.covers), CaveAbode 2x insideProtection
- [x] 4 S8+R3: EpicHooks.playerCalamity; Tribulation ritualBegin/inRitual/ritualTelegraph/ritualEnd/ritualDrop + strike(p,share,n) (HuFa.beforeStrike, rings, graze heal 6%) + Listener.onRitual; StarCalamity omen/pre/strike/pass/fail/abort + LAST_STRIKE 1.5. Klien: SceneCatalogSect.calamity sudah menerima subjek pemain (tidak perlu ubah SceneCatalogPower). needsFromOthers: HuFaTribulation override onRitual + join saat inRitual
- [x] 5 S10: EpicBudget.beginFrame idempoten per frame (Minecraft.getFrameTimeNs), takeQuads/takeLarge memanggilnya; DuelFxSettings.tier()=EpicBudget.tierOrMinimal (stepDown/fps dihapus, tick no-op), quadCap<=EpicBudget.quadCap; DuelGeo.take ambil dari EpicBudget.takeQuads
- [x] 6 S9/S31/S33: help kehancuran bertingkat (id+en, angka bawaan config), kehancuran_diri/semua + teknik.check.pav Balai->Aula, aura Pembangunan Fondasi / Dewa Abadi, tempa Dewa Abadi + tribulasi, arts nama+desc+lore Kesengsaraan->Tribulasi (Sumber utuh), teknik Cincin->Rangkai Segel (en Seal Set)
- [x] 7 R5 (Sparks RINGAN 0.5, DuelFxSettings baca DuelSettings); R9 DuelRuleSet.defaults baca DuelConfig (radius+waktu); R7 kunci kekuatan.guide.ia8 (M1 merujuk)
- [x] 8 Angka: S7 (legacyLayer 0.25); S3 Breakthrough AWAKENING min NORMAL; S4 XpSources.combatScale clamp(xpReq/4500,1,8) di onKill + milestone(player, share, what) + konstanta 0.03/0.05/0.04/0.10/0.05 + kunci kekuatan.milestone.*; R3 (di butir 4)
- [x] 9 Fragmen kekuatan_selaras.* (11 kunci, id+en identik). PANDUAN milik M1: teks catatan di bawah -> needsFromOthers
- [x] 10 Swa-periksa: grep uji terima = 0, kunci identik semua fragmen M4, akhir baris sama dengan HEAD (Meditation/ProtectiveFormations/teknik CRLF), API MC: Minecraft.getFrameTimeNs (mcsrc :1543)

## Langkah berikut
SELESAI. Tidak menjalankan Gradle.

## Catatan PANDUAN "Perubahan keseimbangan" (untuk M1)
- Meditasi: situs x formasi Qi x batu roh tetap dibatasi x3,5; medan Formasi Sekte (G) kini dihitung di luar batas, maks x1,5 (total maks x5,25). Pesan "batas penyerapan" muncul sekali per sesi.
- Kebangkitan selalu minimal Normal (dulu 50% Retak).
- XP bunuh monster biasa x clamp(xpRequired/4500, 1, 8). Tonggak Dunia (bagian bar): menara per 10 lantai 3%, reruntuhan 5%, pengadilan 4%, penjara disegel 10%, Darah Jiao 5%.
- Teknik vs musuh ujian: D legacy + 0,25 per lapis Kenaikan Abadi (IA T9 = setengah ranah lebih kuat). Musuh Dunia (rival Kota, Kepiting Batu, Ular Laut, Yaksha, Naga Liar, makhluk Reruntuhan) kini musuh ujian (D x1,5, bukan P).
- Bencana Bintang: sambaran terakhir x1,5 (dulu x2); cincin petir (NS+, kembar IA+), sambaran yang diserempet 40% dan memulihkan 6% darah; Hu Fa ikut (setelah HuFaTribulation memakai onRitual).
- Gua Kediaman diakui di dalam Formasi Sekte keluarga Pelindung/Gabungan yang berdaya.
- Percikan duel Ringan = 0,5; batas waktu dan radius duel bawaan dari kultivator-duel.json.

## Masalah terbuka (needsFromOthers)
- HuFaTribulation: override Listener.onRitual (begin -> open(p,t); end -> close(p, success?SUCCESS:FAILED, false)); join() terima Tribulation.inRitual(cultivator).
- FieldPanel: baris dari formasi.med + kunci kekuatan.formasi.medcap(.tip); formasiui.field.gather.tip menyebut batas tumpukan tidak lagi membatasi meditasi.
- M2/M3 (dunia): panggil XpSources.milestone di menara/reruntuhan/pengadilan/penjara; DarahJiaoItem.XP -> milestone(MILESTONE_JIAO_BLOOD).
- M1: Kitab IA T8 rujuk kultivator.kekuatan.guide.ia8; guide.c8.seat.body (x3,5 + G x1,5); catatan PANDUAN di atas.
- DESIGN 22.5 (legacy D + 0,25 layer), 24.5 (G di luar cap), 26.5.2 (akhir x1,5).

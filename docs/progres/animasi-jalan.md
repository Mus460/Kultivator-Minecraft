# Animasi jalan & lari (pemain + NPC)

Rencana: kelas bersama gerak/Langkah.java (Keadaan per tubuh: lari, belok, maju/samping, aksel, mendarat; terapkan() pada PlayerModel).
Pemain: AvatarRendererMixin ekstrak -> Langkah.KUNCI; PlayerModelMixin -> Langkah.pemain(). NPC: GerakKeadaan.langkah, GerakData.langkah, GerakApplier.apply.
ProfilJalan: fase kontinu (k.fasa) + campur jalan->lari halus; profil TAKUT baru untuk LARI_TAKUT.
Setelan di SosokDetail: sosok_jalan (cantik/vanilla), sosok_jalan_kuat (50..150).

- [x] Langkah.java (bersama)
- [x] pemain: mixin ekstrak + terapkan + yield (PAL/BC, EMF, meditasi, pose, pedang terbang, renang, elytra, jongkok, naik)
- [x] NPC: ProfilJalan fase + blend + TAKUT; GerakPengendali/Keadaan/Data/Applier
- [x] setelan SosokDetail + SettingsRows + lang id/en
- [x] PANDUAN 72 (CRLF)
- [x] build compileJava compileClientJava jar (BUILD SUCCESSFUL)

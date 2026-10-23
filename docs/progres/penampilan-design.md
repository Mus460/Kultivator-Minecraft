# Progres: desain 40 Penampilan dan Animasi

File target: docs/rancangan/40-penampilan-dan-animasi.md (LF). Read-only selain dua file ini.
Draf bagian di scratchpad: C:\Users\Musyaffa\AppData\Local\Temp\claude\C--Users-Musyaffa-Downloads-butterbot-fabric\8457f796-cad8-4166-ba65-e21aab96119d\scratchpad\p40\*.md (rakit dengan cat).

- [x] Baca renderer murid + layer + pose (DiscipleRenderer/Look/RobeLayer/HeldItem, DutyPoses, SektePoses, LangitDutyPosesMixin, PoseApplier, PoseLibrary 18 kanal, PlayerModelMixin TAIL, MeditationPose, CultivatorModels)
- [x] Baca NPC kota (KultivatorNpc look/voice/female=voice>=3, NpcRole 15, NpcGear, NpcRenderer, NpcPoses, KotaGear 21 jenis, KotaGearLayer) + HumanNpcRenderer (dimensi), RogueRenderer, EnvoyRenderer, PenontonRenderer
- [x] Baca duel 35 (DuelPose 27, DuelPoses, PalBridge refleksi), seragam 36 (JubahLook, LambangCache/LambangAtlas DynamicTexture, SeragamData CUT_*), Watak/Sifat 12, PowerIds physique 7 = pupil ganda
- [x] Baca 38 N (klip 1-78, DATA_ANIM...), O (NpcFaceLayer, perlengkapan), T.8 (klip 79-107, prop 41-62), E.1/E.2 pemilik; 39 BELUM ADA (mandiri-design belum mulai)
- [x] Verifikasi API mcsrc: DynamicTexture(Supplier,NativeImage)/upload/getPixels, TextureManager.register/release, NativeImage.setPixel ARGB, ModelPart x/y/zScale + resetPose memuat skala, PartPose 9 nilai, Attributes.SCALE syncable (ada di createLivingAttributes), LivingEntityRenderer memakai state.scale, AvatarRenderState.show*
- [x] p1: 40.0 lingkup, 40.1 fakta, 40.2 identitas (selesai)
- [x] p2: 40.3 penyusun kulit + busana + rambut (UV dipak dgn scratchpad/tools40/pack.py)
- [x] p3: 40.4 tabel rupa (K 15, 38.N 49+2, 38.T 39, D, liar, penonton, murid pangkat, jalan 16)
- [x] p4: 40.5 Gerak (arsitektur, kanal 24, format klip, prioritas, sumber, gaya jalan, prop 63-87, pegas, tatap/wajah, katalog 1-107 perubahan, 108-121 sikap, 122-171 murid)
- [x] p5: 40.6 performa/Sodium-Iris/setting/uji + 40.7 modul 4 + kontrak + 38/39 + daftar uji
- [x] 39 muncul di tengah kerja: diselaraskan (10 jalan 39 = bit 1-10, ACT 40-56 -> klip 40.5.13 172-176, JELMAAN, bug SektePoses.register 13-24)
- [x] Rakit ke docs/rancangan/40-penampilan-dan-animasi.md (LF, 1663 baris)
- [x] Ringkasan balasan

Keputusan: atlas sosok 128x128 per penampilan (kulit 64x64 di kiri atas + busana/rambut/wajah); klip = id kanonik 1-107 sama dgn 38 + 120+ murid; Snapshot lewat PoseApplier + GerakApplier tambahan di PlayerModelMixin; Attributes.SCALE utk tinggi/anak.
STATUS: SELESAI 2026-10-23. Langkah berikut: tidak ada (revisi: sunting p*.md di scratchpad lalu rakit ulang, atau sunting dokumen langsung).

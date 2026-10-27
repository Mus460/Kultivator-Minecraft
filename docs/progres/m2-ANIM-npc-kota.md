# m2-ANIM npc-kota (fixer)

Slice: sosok/SosokApi, client GerakPengendali, dunia/tempat/hidup, dunia/kota42/**, data JSON tempat/**.
Rule: every NPC activity is really animated (clip + prop + at the spot, facing it).

- [x] B1 MEMANDANG (164) on non-disciple NPCs: SosokApi.gerak now plays TATAP_LANGIT (variant 0) for any body that is not a DISCIPLE-role disciple; every server caller (HidupApi.klip, TubuhPenduduk.main, TempatDialog and BicaraWarga gestures) goes through it, the client needs no change (it never receives 164 from an NPC). periksa_kerja -> sikap_tangan_belakang, periksa_hasil -> lihat_barang, periksa_gerbang -> sikap_tombak (JSON).
- [x] Wrong-clip rows: koki potong_talenan (new clip 460 potong_talenan + pisau_ganda), koki uleni_adonan (new 461 uleni_adonan), tukang menggergaji (new 462 gergaji_kayu + papan_kayu as the blade), tukang mengukir (palu_lunas, palu_dempul + pahat), jemur_herba (jemur_ikan + herba). 450-459 were all taken by 42S, so the new clips use the reserved 460-462 (KotaGerakId, KotaPoses, TubuhPenduduk.KLIP_FOKUS).
- [x] Perkelahian guard LERAI 389: already in code (Perkelahian.java:237), skipped.
- [x] Fire chain front man SIRAM_API 391: already in code (PeristiwaJalan, front of the chain every 40 ticks), skipped.
- [x] Unused design-42 clips pikul_tandu, sembah_nikah, bersulang, tunggang_kuda, tabur_uang_kertas, ratap_duka, merangkak, pilih_benda, gendong_bayi, bagi_telur, huaquan_a/b, potong_pita, pikul_tempayan, dorong_sangkar_ayam: all wired already (Upacara F42-2, DistrikMalam, Peresmian, RutinitasPagi, PeristiwaJalan). Skipped.
- [x] gosok_dinding 414: after a fire is put out the owner and up to two of the bucket chain scrub the wall for a minute (PeristiwaJalan.padam, tasks survive akhiri).
- [x] bersaksi 417 (witness body), sodor_amplop 419 (the briber's body at the hall when the envelope is offered), palu_putusan 418 (the hall magistrate on the verdict): kota42/ekonomi/Perkara.java.
- [x] tiru 443: apprentices play it one hour in three beside the master (Pekerjaan.magang); mengigau 444: the sick resident lying in bed (TubuhPenduduk dalam branch, also in KLIP_DIAM); pincang 446: the walk of a PULIH resident (TempatHuni).
- [x] 5 troupe roles KerjaPeran fallback: PENGAMEN erhu / seruling, AKROBAT plates / hoop, PENARI yangge with fan / sash, PENDONGENG n/a, PEMUSIK gong / small drum / suona, PENCOPET loiter against a wall.
- [x] Upacara 42 (nikah / makam / lahir / zhuazhou): no longer a stub, every participant has a clip + prop (comblang, procession, musicians, parasol, children, bride, groom, parents, guests, pelayat, pengurus, penabur, ibu, ayah, bayi merangkak). Verified only.

Needs (outside the slice): a real saw prop and a kitchen knife prop (new item models, now stand-ins papan_kayu / pisau_ganda).

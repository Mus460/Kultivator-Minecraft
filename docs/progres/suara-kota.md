# suara-kota: riuh kota + obrolan warga + 159 suara MEGA

Rencana (client-only, tanpa payload baru):
- Riuh kota: loop per suasana (pasar, kedai, pelabuhan, jalan, kuil), dipilih dari peran KultivatorNpc di dekat pemain
  (NpcRole tersinkron), volume = jumlah warga dekat; malam/dalam ruang lebih pelan. SoundSource.AMBIENT.
- Obrolan: klien membaca DATA_LINE (kunci kultivator.hidup.obrol./kultivator.kota.obrol.) -> gumam VOICEVOX
  sesuai Sosok (pria/wanita/tua/anak). SoundSource.VOICE.
- Setelan di VoiceSettings (EffectQuality.get/put): riuh_kota %, obrolan_warga %.
- gen_voices.py: write() lewati berkas yang sudah ada (tidak menimpa klip lama); pack() aman bila voice_dl hilang.

## Checklist
- [x] gen_voices.py aman (skip existing, skip missing packs)
- [x] gen_voices run 1: 359 klip baru (159 baris MEGA), tak ada entri hilang (dicek vs backup)
- [x] gumam: 66 klip (pria 14, wanita 14, kakek 13, nenek 12, anak 13), run 2 ok
- [x] ambience: 5 loop 48 s (pasar, kedai, pelabuhan, jalan, kuil) dari gen_riuh_kota.py (tulis berblok: libsndfile stack overflow)
- [x] kode: sound/SuaraKota, VoiceEvents.GUMAM, hidup/RiuhKota + ObrolanSuara (HidupClient), VoiceSettings rows, ImpostorRenderer.dengar
- [x] lang id/en: lang-src/suara_kota.*.json
- [x] CREDITS.md (VOICEVOX baru + Riuh kota), PANDUAN.md bab 73, credits.8 + CREDIT_LINES 9
- [x] build sukses 16:29 (termasuk konstanta Pemanggil orkestrator); tidak ada javac.*.args/build-log.txt

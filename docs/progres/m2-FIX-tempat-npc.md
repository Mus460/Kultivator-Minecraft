# m2-FIX tempat-npc

Fixer "tempat-npc" for audit-kota-tempat.md and audit-npc-sosok.md (not Perkara / PeristiwaJalan).

## kota-tempat
- [x] 1 NPC cannot board boat: `startRiding(.., true, false)` in Perahu (post rower, tour guide) and TikusAir (thief)
- [x] 2 koki/tukang clip names: `kota42_` prefix (potong_talenan, uleni_adonan, gergaji_kayu)
- [x] 3 AirCommands tur/sewa: test money taken back when the call returns false

## npc-sosok
- [x] 1 DISCONNECT handlers wrapped in `client.execute` (AnimClient, GerakKlien, VoiceClient, MusuhClient, EliteClient, KotaClient, HidupClient, JalanClient, KotaBesarClient, HiasanDunia, PamerModel, RakSenjata, PerbaikanClient, TemaFx, TemaClient, TeksDunia, UiTempatClient)
- [x] 2 sekali dialog ending on a node with no choices: once-flag set in `TempatDialog.masuk`
- [x] 3 GerakId.durasiSekali: 350-469 delegates to KotaGerakId.durasiSekali (no Mandiri/Gabung table exists, nothing else to do)
- [x] 4 rep farm: mei_sapa `arwah` once-flag, lao_shuo per-story tip flag
- [x] 5 Voice.heavenNear: key starting `demon.` goes out as KIND_DEMON (no `heaven.` prefix)
- [x] 6 dialog gestures: `v_tatap_*` replaced by clip `tatap_langit` (3 files)
- [x] 7 NpcLife: partner gets `chat_by`; `freePartner` clears busy when the initiator is gone or no longer chatting with it
- [-] 8 Perkara: not in my files (kota42/ekonomi)
- [-] 9 PeristiwaJalan: excluded

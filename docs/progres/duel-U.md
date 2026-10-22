# Progres duel:U (UI, suara, integrasi) — rancangan 35

Status: SELESAI (run 3, 14:18-). Run 2 (08:11) hanya menulis checklist.

## Checklist
- [x] sisi utama ditinjau: sound/DuelSounds, duel/compat/DuelCompat, CombatRollCompat, duel/voice/DuelVoices (API voice dicek)
- [x] klien inti ditinjau: DuelBus, DuelClock, DuelClient (kartu hub lewat HubCards.register, tanpa edit HubCardsPage), DuelInput (perbaikan parriesBlades field), DuelKeys
- [x] compat/BetterCombatBridge, mixin/duel/DuelInputMixin ditinjau (startAttack()Z, continueAttack(Z)V, startUseItem()V cocok mcsrc)
- [x] sound/DuelSoundPlayer, sound/DuelCrowd, ui/DuelSettings ditinjau (SoundEvents/Holder dicek)
- [x] DuelVoices.npcLine: kota.rival_claim / kota.wanderer_dao / kota.immortal_scorn bila ada, cadangan boss.taunt/elder.advice/rogue.defeat dengan caption kultivator.duel.ui.voice.*
- [x] tekstur gui/duel/*.png (4) ; advancement duel_*.json (10)
- [x] ui/DuelHud (bar postur, kunci, rantai, hitung mundur, 危/決, keluar gelanggang, proyeksi bar di atas kepala; DuelClient memanggil clash(p, predicted))
- [x] ui/ChallengeOfferToast (digambar DuelHud; rulesLine/stakeLine dipakai layar)
- [x] ui/DuelScrollScreen (dasar gulungan baru), ui/DuelScreen, ui/ChallengeScreen, ui/DuelResultCard; DuelSettings + helper cycle/toggle
- [x] lang-src/duel_ui.{id_id,en_us}.json (305 kunci identik; generator scratchpad/gen_duel_ui_lang.py; cek kunci statis OK)
- [x] edit KultivatorClient, kultivator.client.mixins.json, SettingsRows (sudah dari run 1, dicek)
- [x] kartu hub: HubCards.register di DuelClient (HubCardsPage tidak perlu diedit)
- [x] GuideChapters (8 langkah sudah dari run 1)
- [x] sounds.json: 16 peristiwa duel.* ditambahkan dalam satu edit (dibaca ulang tepat sebelumnya)
- [x] pemeriksaan kompilasi: javac 25 (runtime .minecraft) atas semua file U + SettingsRows + GuideChapters dengan stub S/A yang belum ada (scratchpad/duelU_check): 0 galat. Perbaikan: DuelHud.clash statis -> onClash (bentrok dengan Listener.clash)

## Langkah berikut
Tidak ada. Gradle dijalankan agen finish.

## Masalah terbuka
- duel.DuelCameraMixin (V) kini ada; ia butuh fx/DuelCamera (V) yang belum ada saat dicek.
- U bergantung pada kontrak S/A yang belum ada di disk: DuelSession.a()/b()/ring(), DuelRing.center()/radius(), DuelHooks.onRoll, Fighters.get/Fighter.kiai, DuelistRegistry.profile, client.duel.anim.DuelAnim.init.

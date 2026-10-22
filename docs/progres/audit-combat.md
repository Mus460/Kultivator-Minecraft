# audit:combat (read-only audit)
- [x] Peta paket & DESIGN 0/22/23/36/37 (sebagian; 36 sekte dibaca bagian combat)
- [x] Teknik (169) + seal/wheel + biaya/cooldown (sebaran realm dihitung: QR 2, FE 5, IA T8 0, T6 1)
- [x] Ultimate + film (film hanya Dewa/Abadi; Tian tanpa film = sesuai desain) + lockout 400
- [x] Kehancuran / Hancur Total / proteksi (zone providers dicek) / Titah gate (cek duel ok)
- [x] Duel pedang settings (DuelSettings vs DuelFxSettings duplikat)
- [x] Elite / purba (intake clamp ok)
- [x] bencana bintang (tanpa beat sinematik, tanpa Hu Fa) / murka (Panglima posture realm 0)
- [x] heart demon (canFace kurang Purba/DuelRules; Purba.busy kurang HeartDemons)
- [x] Aura + EffectQuality/EpicBudget/setting (3 penjaga FPS, quad duel terpisah)
- [x] Lang-src terminologi (aura Pembentukan Fondasi/Abadi Sejati; kesengsaraan vs tribulasi; 169 vs 168)
- [x] Tulis laporan -> audit-combat-laporan.md (0 KRITIS, 2 TINGGI, 5 SEDANG, 6 RENDAH)
Temuan sementara:
1. legacyD abaikan layer IA (TechniquePower.java:414-417) -> 9 layer IA datar vs trial mob
2. DuelFxSettings sparks Ringan 0.5 vs DuelSettings.Sparks RINGAN 0.4 (DuelSettings.java:78-90)
3. aura nama realm salah (aura.id_id:8, aura_high.id_id:11) vs core.id_id:4,10
4. kesengsaraan vs tribulasi (arts_lightning 57,67; arts_sword 26)
5. KetuaSekte duel DuelRules (SEDANG DIBANGUN) - Duel Pedang membungkus DuelRules, jadi clash tetap
6. TINGGI: musuh dunia (KultivatorNpc, KepitingBatu, NagaLiar, RCreature) tanpa tag trial-mob -> P penuh (TI ~400x) vs elite/rogue legacy -> one-shot vs 10 hit
7. StarCalamity tanpa EpicHooks beat (hanya trampler k=7), tanpa HuFa/LightningRings (StarCalamity.java:400-420)
8. Posture.realm Panglima/Purba = 0 (MobGuard.java:70-80)
9. guide 169 vs codex x/168 (guide.id_id:71, TechniqueSpec.java:542)
10. IA Tahap 8: 0 teknik, Tahap 6: 1
Next: SELESAI.

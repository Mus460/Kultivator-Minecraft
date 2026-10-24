# perbaikan-palsu (38PR false repairs + 38NL livelier taverns/markets)

## A - false repairs
- [x] read perbaikan module, find root causes
- [x] LogAtribusi.cari: no event -> null (was fallback "monster"); fire now -> api; monster event -> serangan while raid
- [x] PindaiTempat.banding: unattributed change -> Cetak.terima (blueprint adopts), counter diterima
- [x] mixins: SectExplosionMixin -> PerbaikanApi.ledakan; TempatUbahBlokMixin (ServerLevel.gameEvent, mob/projectile); TempatApiMixin (FireBlock.checkBurnOut)
- [x] Banding: soul fire / fire on infiniburn never damage
- [x] tukang role work: carving (PAHAT) + LIHAT_BARANG, no plaster; lift periksa_roda no hammer
- [x] repair quests/help-wanted/kabar only from clusters -> fixed by attribution (no tukang anchors exist)
- [x] diagnostic `uji tempat cetak periksa` + lang keys
- [x] compile A (BUILD SUCCESSFUL)

## B - livelier taverns/markets
- [x] read hidup module
- [x] Jadwal.ramai overlay (applied in TempatHuni.kegiatan, no event, healthy, not guest): market 7-11, eating house 12/18-19, tea/story/stroll 13-16, dinner shopping 17, tavern 20-22, late drinkers 22
- [x] goals @kedai (seats near teko / kuali with 4+ seats), @depan_lapak:<n>, @jalan:<n> in Kegiatan + TempatHuni.tujuan
- [x] pilih overflow: free standing spot round the seats (TempatHuni.berdiri) instead of one queue block
- [x] walking: goal number changes every 20 min; door spawn when a player within 40 (was 20)
- [x] tukang role carving; lang kegiatan.jalan_jalan
- [x] compile B

## Finish
- [x] PANDUAN note (69.3)
- [x] build ok (BUILD SUCCESSFUL), cleanup

## Notes

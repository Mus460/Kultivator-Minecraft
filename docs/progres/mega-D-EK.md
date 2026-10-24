# mega-D-EK — detail pass economy (wave W3, 38.M.6)

Owns (from 38EK): `dunia/kota/{BalaiLelang,PapanMisi}.java`, `alchemy/AlchemyFurnace.java`, `tempa/Forging.java`,
`dunia/tempat/ekonomi/**`, `lang-src/tempat_ekonomi.*`, `tools/gen_tempat_ekonomi.py`, economy data
(`tempat/permintaan/{kota_kota,kota_kota_besar,pos_dagang}.json`, `tempat/lelang/agung.json`, advancements tempat/*).
No Gradle (F3 only). Read-only: Harga, NilaiBatu, Toko, Titipan, everything else.

## Checklist
- [x] 0 read 38.M + 38EK report + F2 notes; survey simulation code (EkonomiCommands simulasi/judi/lapak/kafilah)
- [x] 1a mission income audit (scratchpad dek/model.py, mirrors MisiGilda.papan/imbalan, PapanMisi.reward, Lencana):
      old `simulasi` (3 missions at own rank + 1/2 bounty) puts band 1 at ~25 NB (< 30 = 40-25 %) -> FAIL; real player
      model (capital E-S 8 + town E-C 8 + post E-D 5 boards, best eligible offers up to Lencana cap, S needs 7 stars,
      2 town bounties/day) gives all bands inside 38.M.1 +-25 % for 7/30/60 days -> numbers stay, sim was wrong.
      Capital board alone starves bands 0-1 (~1 E offer a day): low bands must use town/post boards (clerk hint).
- [x] 2a Lencana.bintangDari/misiPerHariBintang (pure), MisiGilda ukuranPapan/jenisPapan/bobot/pilihJenis/pangkatTawaran
      (papan uses them), PapanMisi.reward/pangkat/REWARD_BAND_CAP public; EkonomiCommands.simulasi rewritten (3 real
      boards, Lencana cap/S gate, 2 bounties, inn/meals/teleport/repair, lines sumber + belanja/upgrade-days); lang +9
      (model2.py: bands 0-8 all OK at 7/30/100 days, upgrade 0.5-3.2 days)
- [x] 2b judi: JudiBatu.simulasi returns {blind, emas, biru, abu}; analytic gold-only 1.007 <= 1.02 OK; line judi_warna (+1 key)
- [x] 2c lapak/kafilah constants match 38.M.4.1/4.2 (checked, unchanged)
- [x] 3a demand tables rewritten with valued items only (vanilla = 0 NB, were dead rows): tools/gen_tempat_ekonomi_data.py
      -> kota_kota 69, kota_kota_besar 82, pos_dagang 62 rows; demonic wares 0 (refused) in righteous towns; ids checked
- [x] 2d BUG preview != payout: gilda board showed imbalan(seed id) but paid imbalan(seed id^uuid) -> MisiGilda.imbalanTawaran
      used by board row (GildaKultivator) and terima; PapanMisi board showed rewardOf(town^day^i) but paid player.getRandom()
      -> reward stored in misi at accept and paid as shown
- [x] 3b story variants 3 -> 5 per 38EK kind (MisiGilda.CERITA_VARIAN, +24 keys x2; Gu Lianshan = treasury, Qian Duoduo = Nyonya
      of Rumah Uang, Feng Ao)
- [x] 3c lelang/agung.json 26 -> 39 lots (rings, talismans, teas, heart pill, qiang, lonceng grade 4; all Harga BASE ids)
- [x] 2e EXPLOIT Lelang Agung/Gelap consignments: base <= 1.5 x value and NPC limit <= 1.5 x base -> ~1.7 x value; now
      LelangAgung.TITIP_PLAFON 1.0 / plafonTitip(stack) = Lot.ceiling in BalaiLelang (NPC raise only <= ceiling), warning
      lelang.titip.plafon (+1 key). Town profile already safe (0.6-1.0 x value, half mean base).
- [x] 2f EXPLOIT PasarBinatang core buy-back 0.5 x raw value vs shop price x scale (scale 0.25 min -> profit): now x min(1, skala)
- [x] 2g checked: judi pays from paid price (EV kept at any scale); Rumah Uang 180/90 unscaled both ways; lapak loop after
      3 days ~+10 % on capped volume (design-sanctioned, left)
- [x] 2h EXPLOIT Gadai: 40 % loan on raw value vs shop price x scale -> Gadai.nilaiGadai (x min(1, skala)) for loan, stored
      value, redeem and screen value
- [x] 2i `uji ekonomi simulasi semua <hari>` (bands 0-8 + summary line, +1 key); simulasi(source, pita, hari, harian)
- [x] 3d old 38EK hand-offs verified applied: CHIPS (DuniaCommands), WeaponClass #senjata_gada -> GADA, KotaBesarAnchors
      lapak/lelang anchors; ledger reachable through MENTERI Jasa (bukaDiJangkar unused, harmless)
- [x] 4 self-review: javac (scratchpad ek/jc.sh: ekonomi/** + BalaiLelang + PapanMisi vs B2 classes) 0 errors; PapanMisi
      class made public (EkonomiCommands uses reward/pangkat); 47 files all LF (HEAD had no CR either); lang 615/615 keys
      identical, no cross-fragment dups, no identical id/en strings; no static ItemStacks added; no client refs

## Langkah berikutnya
DONE. F3: Gradle build B3 compiles these files (javac-checked already); optional in-game `/kultivasi uji ekonomi simulasi
semua 30` and `judi 100000` (expect 9/9 and best <= 1.02, gold-only ~1.007).

## Butuh dari modul lain
- D-W (DuniaCommands owner, optional): add chip `ekonomi simulasi semua <hari>` to CHIPS.
- F3: nothing else; regenerate nothing (data written directly; generator tools/gen_tempat_ekonomi_data.py reproduces
  the three demand tables: `python tools/gen_tempat_ekonomi_data.py .`).

## Masalah terbuka
- Design property, not changed: bands 2-3 main weapon costs ~0.6-0.8 day of net income (38.M.1 prices vs targets);
  bands 6-8 income identical (mission band cap 6, M3). Capital board alone gives bands 0-1 ~1 offer a day (low bands use
  town/post boards; the simulation reads all three).
- Lapak loop after the 3-day asal_toko lock gives ~+10 % at max discount on capped volume (design-sanctioned).

# mega-D-TK2 (wave W3 detail pass: oasis + iblis) — checkpoint

Scope: jadwal-38-39-40.md W3 row D-TK2 (= files of 38TK-O and 38TK-I). Design 38.T.4, 38.T.7, 38.T.15.
Owns: `dunia/tempat/tema/{oasis,iblis}/**`, `tools/gen_tempat_tema_{oasis,iblis,iblis_cerita}.py` (+ new
`tools/gen_tempat_tema_oasis_cerita.py`, `tools/hias_tema_oi.py`), `structure/kota_tema/{oasis,iblis}/**`,
`worldgen/**/kota_tema/{oasis,iblis}*`, data `tempat/{dialog,quest,kabar}/tema_{oasis,iblis}*`,
`{permintaan,jadwal}/kota_tema_{oasis,iblis}*`, `tempat/lelang/gelap.json`, loot `tema/{oasis,iblis}/**`,
adv `tempat/tema_{oasis,iblis}_*`, lang `tempat_tema_{oasis,iblis}.*`. No Gradle (F3 builds).
`tools/hias_tema.py` is D-TK1's: only imported; my styles live in `hias_tema_oi.py` (registers into H.GAYA at import).

## Survey (dens.py, interior furniture per covered floor cell)
Oasis thin: pandai_kuningan .05, penenun .06, barak .10, rumah_makan .12, karavanserai x4 .13-.15, asrama .16,
bazar_barat a/b .20, bea .22, penginapan .38. Size 1,070,073 / 1.2 MB.
Iblis thin: kedai_racun timur/barat .05, sarang_kontrak .05, balai_lelang .07, barak_istana .08, ruang_penjaga .09,
rumah_iblis x2 .10, kedai_teh_asap .11, gudang_minyak .18, pos_penegak .18, istana x2 .26-.30, perbendaharaan .26.
Size 989,241 / 1.2 MB. Oasis story writer still lives in scratchpad oasis/ (not in repo).

## Checklist
- [x] Read schedule, W2 progress (38TK-O/I), F2 notes, design 38.T.4/38.T.7/38.T.15
- [x] D1 `tools/hias_tema_oi.py` (done: 19 styles into H.GAYA/H.RUG, `tengah()` islands (13 kinds), `kamar()`): desert styles (gurun_inap, gurun_teh, gurun_dapur, gurun_gudang, kuningan, tenun,
      barak, kuil_air) + iblis styles (iblis_kedai, iblis_gudang, penjara, barak_iblis, kontrak, lelang, istana_darah)
- [x] D2 oasis: registry ruang()/hias() (import alias HO; H is canvas height) dressed in simpan(); asrama cells+refectory,
      barak, bazar storerooms (fixed: old fill compared get()[0][0]=='m', doors stood in partitions -> door x-1), penenun,
      pandai kuningan (+pill room), penginapan 10 rooms + upper halls, rumah makan, bea, karavanserai 8 zones.
      48 tpl valid, lot rules pass, 1,076,419 B (+6 KB). Density thin lots .05-.15 -> .13-.55
- [x] D3 iblis: registry in simpan_distrik(); rumah_batu every storey (all demon houses incl. kumuh rumah_utang), pos
      penegak, istana banquet/meditation wings + throne aisles, barak istana 2 floors (+islands), ruang penjaga hall/bunks,
      lelang foyer, sarang kontrak hall (+islands), gudang minyak walls only (quest barrels in the middle), kedai cawan
      racun hall + 8 rooms + upper lounge, kedai teh asap. 44 tpl valid, lot rules pass, gzip 996,022 (+7 KB)
- [x] D4 oasis story writer -> repo `tools/gen_tempat_tema_oasis_cerita.py` (merged by scratch dtk2/gabung_oasis.py,
      byte-identical 57 files + lang first). Then: KENANG() helper = branch-gated choices ("!bendera" works, engine
      Syarat.cek); post-chain lines in shui_lian_pulih (4), sha_wuyan_selesai (3), bulat_khan_sapa (4), ajun_sapa (3),
      mo_hua_sapa (3), gulnar_sapa (1); rumours 21 -> 33 (+8 umum storm/crescent/inn/smith, +4 quest_ done).
      Lang 594 -> 678. Scratch cek_data only flags '!bendera' (checker limit, not a bug)
- [x] D5 iblis cerita: kenang() helper; route-aware lines (iblis_rute per player) in ye_sapa (2), han_sapa (3), tugang_sapa
      (2), tugang_penguasa (2, incl. A-Fu), baishe_sapa (3), mowen_sapa (2); rumours 26 -> 37 (+10 umum = first background
      lines of the demon city, + quest_tema_iblis_topeng). Writer OK, lang 728 keys, id=en
- [x] D6 Java: 38TK-O/38TK-I/F2 left lists hold no item inside tema/oasis or tema/iblis (all four open items belong to
      inti/38EK/38KS, see Butuh); no Java changed in W3
- [x] D7 TplCheck (scratch final/wg) oasis 48/0, iblis 44/0; cek_tempat 717 tpl 0 errors (kota_tema 5,718,323 < 6.2 MB);
      both writers green (oasis Java-key + clash check, iblis full validator); lang id=en (oasis 678, iblis 728); all LF;
      no fire/lava/weather in new code; no new voice lines

## Langkah berikutnya
DONE. F3: no extra generator run needed (outputs on disk); mergeLang + Gradle. Scratchpad oasis/ writer is superseded by
tools/gen_tempat_tema_oasis_cerita.py.

## Butuh dari modul lain
- 38TK inti (TemaSitus): kolam_semedi_darah is served for istana_darah; the pool lot is in menara_terlarang (1,1).
- 38EK: base price for `tema/kontrak_kepala` (~180 NB), else LelangAgung skips the gelap rows naming it.
- 38KS/F3: JamMalam may call BadaiPasir.gerbangTutup(townId) to keep the Gerbang Giok shut in a storm.
- inti: Dagang.bea(.., "giok") waives for any Surat Jalan item; oasis passes expire (Bea.suratSah).

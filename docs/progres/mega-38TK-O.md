# mega-38TK-O (wave W2, Kota Oasis Gurun 38.T.4) — checkpoint

Scope: jadwal-38-39-40.md W2 row 38TK-O + sec 5 note; design 38.T.4 (+38.T.2.4, 38.T.9.2-3, 38.T.10-13 oasis rows).
Owns: `dunia/tempat/tema/oasis/**`, `tools/gen_tempat_tema_oasis.py`, `structure/kota_tema/oasis/**`,
`worldgen/{structure,template_pool,processor_list}/kota_tema/oasis/**`, `data/kultivator/tempat/{dialog,quest,kabar}/tema_oasis*.json`,
`tempat/{permintaan,jadwal}/kota_tema_oasis*.json`, `loot_table/tema/oasis/**`, `advancement/tempat/tema_oasis_*.json`,
`lang-src/tempat_tema_oasis.*`. No Gradle. Structure sets / shared tags / pack = 38TK inti (`gen_tempat_tema_data.py`).
Contract of inti: `docs/progres/kota-TK.md` (town id = site id; TemaKota; TemaAnchors.daftar; TemaMekanisme; TemaSibuk).

## Keputusan
- Town anchors: own module `tema_oasis/<what>` (DuniaAnchors: one handler per module, like pelabuhan's `pelabuhan/*`).
- Ruin satellite anchors (inti's generator): `kota_tema/{pintu_pasir,sarang_kalajengking,temuan_reruntuhan,mural_gua}`
  served by my `Reruntuhan` via `TemaAnchors.daftar`. Pillars = 4 bodies around the nest (no own anchor).
- Town registry `OasisKota` keyed by site id from `TemaKota.di` (contract sec 2); Nama Baik id via `Towns.townOf`.
- Storm Sibuk: `registerEnemy(tema_oasis_badai)` only outside walls during storm proper.
- Gate closure during storm: `BadaiPasir.gerbangTutup(id)` public; hook in JamMalam is F2's (needs list).
- Mata Gurun: own SpiritSites provider in KuilAir.
- Tari Putar uses inti's `tema_irama` screen; result dispatch = request to inti (`TariPutar.hasil(player, skor)`).
- Structure JSON biomes `#kultivator:has_structure/kota_tema/oasis` (contract sec 5); gaya `merah` via ttl.GAYA_TEMA.
- Named characters tagged by `OasisTokoh` (memory `tokoh` + plate).

## Checklist
- [x] Study APIs (session 1+2)
- [x] OasisKota, OasisAnchors, OasisTokoh, OasisLayar, BadaiPasir, KuilAir, Mural (Mural: fix situs = OasisKota.di)
- [x] Entities data classes: TemuanPasir, BatuUkurAir, TimbaSumur (behaviour done), PilarRune, PintuPasir, Unta, Kalajengking (data only)
- [x] OasisNames, Tawar (+bazar guest stalls), Bea, KafilahUnta (+sewa, Kafilah Agung, quest caravan), TariPutar, Reruntuhan
- [x] Entity behaviour: unta (sewa/ride/kneel/dash/pulang), kalajengking (capit/sengat/gali), pintu, pilar
- [x] OasisCerita (syarat/akibat/goal + Jasa buttons), OasisCommands (`uji tema unta`), Oasis.init wiring + ticks
- [x] javac check (JDK 25 of .minecraft runtime, out to scratchpad/oasis/jc): oasis package 0 errors (session 2)
- [x] Session 3 Java fixes: Reruntuhan.ambil reports guild mission `reruntuhan` (EkonomiApi.misiSelesai); OasisCerita.airPulih
      grants inti's `tempat/tema_pembawa_air` (nobody did); Mural.baca fires `oasis_mural N` on EVERY read (stage 3 no dead
      end); new akibat `oasis_paham N`; caravan flag `oasis.kafilah_hilang` (KafilahUnta) + goal `oasis_kafilah akhir`;
      NEW UntaLari.java (Si Belang runaway camel: akibat `oasis_unta_kabur`, event `oasis_unta pulang`), wired in Oasis;
      javac oasis pkg (-sourcepath src/main/java, cp build/classes + final/wg/cp.win) 0 errors
- [x] Data quests (scratchpad oasis/data_quest.py -> 8 files): tema_oasis_mata_air (antre,kafilah,cari_ajun,lempeng,mural,formasi,xie,duel,pertapa,pulih), side
      tema_oasis_{unta_hilang,mural_pesanan,permadani_ratu,kurma_emas}, perintah_{reruntuhan,kafilah,buru}
- [x] NEW tokoh gulnar (OasisTokoh, tagged on tenun lot in OasisAnchors.layanan)
- [x] Dialogs: scratchpad oasis/dlg_lib.py + dlg_a.py (Shui Lian/Sha/Bulat, 15) + dlg_b.py (Ajun/Mo Hua/Xie/Gulnar,
      18) DONE (each writes JSON + lang_<x>.json); dlg_c.py roles (prio 5 < tokoh sapa 10, 9) DONE; cek_data.py: 42 dialogs/8 quests 0 errors
- [x] (data_misc.py, lang_misc.json) kabar tema_oasis.json, permintaan kota_tema_oasis.json, jadwal kota_tema_oasis.json, loot tema/oasis/raja_kalajengking,
      advancements tema_oasis_kalajengking + _penari + _si_belang (granted in UntaLari); loot uses 26.3 `modifier`
- [x] Lang tempat_tema_oasis 594 keys id=en (parts: lang_java.py + dlg_a/b/c + data_misc -> lang_build.py <repo>; checks clashes,
      every literal Java key, 12 murals, dynamic families, % escapes)
- [x] Generator gen_tempat_tema_oasis.py written (2475 lines, 10 districts, walls, gate; output on disk 00:57)
- [x] Generator re-run: deterministic (md5 same), structgen 48 valid, lot rules pass, 1.07 MB (< 1.2 MB theme budget);
      run_38.sh TplCheck kota_tema 259/0, WgCheck38 157 ALL OK; copy+gen_tempat_tema_data: 105 sets ALL OK (session 3)
- [x] Review fixes: TariPutar now starts via inti TemaIrama.mulai (callback -> hasil; before, the score never came back),
      Nama Baik +1 on kota.town (was site id); Bea.dekat uses inti Dagang.bea(p, situs, "giok") (all goods, seizure, town
      chest) instead of its own crate duty; javac 0 errors
- [x] Mural commission quest: new syarat `oasis_mural_dipesan` (Mural.sudahPesan) as stage goal (no dead end)
- [x] Final review: no client imports / block changes / weather; ItemStacks only at runtime; all LF; javac 0 errors
DONE for W2 (session 3). Data sources in scratchpad oasis/: data_quest.py, dlg_lib.py, dlg_a/b/c.py, data_misc.py,
lang_java.py, lang_build.py, cek_data.py (run each with `python -I <file> <repo>`; lang_java.py takes no arg).

## Langkah berikutnya
None in scope. F2: run gen_tempat_tema_oasis.py, then gen_tempat_tema_data.py, then the Gradle build.

## Butuh dari modul lain
- F2/inti: re-run gen_tempat_tema_data.py after this generator (oasis sets + #kota_tema / tempat_dirawat tags; until then
  cek_tempat shows 20 oasis tag errors; verified on a copy: 105 sets, WgCheck38 ALL OK).
- F2: cek_tempat FOLDERS kota_tema 5.5 MB < 5.69 MB actual (5 x 1.2 MB theme budget); oasis 1.07 MB is within its own.
- 38ST/F2: LoreIndeks entry `kota_tema.oasis` = Entri(3, 0, true, "") + site kind mapping; keys are in tempat_tema_oasis.
- F2/38KS: JamMalam may call public BadaiPasir.gerbangTutup(townId) to keep the Gerbang Giok shut in a storm.
- inti: Dagang.bea(.., "giok") waives for ANY Surat Jalan Gurun item; oasis passes expire (Bea.suratSah).

# mega-D-39F (W3 detail pass, DESIGN 39.F feud scenes)

Owns the 39F files only (see mega-39F.md): `sekte/mandiri/seteru/**`, `client/sekte/mandiri/seteru/**`,
`sekte/diplomasi/DiplomasiMandiri.java`, `lang-src/sekte_mandiri_seteru.*`, `tools/gen_mandiri_seteru.py`, its
textures/advancements/kabar json. No Gradle. New files LF.

Findings at start: clips KLIP_CURI/KLIP_LINDUNGI/KLIP_RAHASIA and props gulungan_curian/tali_merah were registered on
the client but never staged by the server; every quarrel used the same 3 voice captions whatever the cause.

## Checklist
- [x] Naskah.java (new): caption keys naskah.<sebab>.<0-2>.a|b|c, naskah.rendah.<0-3>, families (4 variants):
      tegang palak lindungi curi duel.tantang/terima/menang/kalah tetua_tantang tawuran sergap patroli_guru maaf
      sumpah dendam lerai ganti_rugi ketahuan; two-voice damai/saingan/kekasih.<0-3>.a|b. Voice.say(body, event, caption)
- [x] Adegan: prolog per cause (gulungan KLIP_CURI, perundung/desa KLIP_PALAK at nearest townsperson + ours
      KLIP_LINDUNGI, hinaan KLIP_TERTAWA + cawan, else glare), new saingan/dendam/rahasia(+ketahuan elder) scenes,
      lerai start+bow, sumpah winner turns away, damai two lines
- [x] Hooks: Tangga.akhiri SAINGAN/DENDAM scenes, AsmaraLintas.rahasia scene (meeting + caught), DuelSeteru/Tawuran/
      Patroli lines via Naskah
- [x] SeteruIds KLIP_MELIRIK 268 .. KLIP_TERTAWA 273 (KLIP_LAST 273); SeteruPoses.suasana() builds them
- [x] Lang: scratchpad/d39f/lang.py (= seteru39f/lang.py + naskah block + 4 kabar) -> 639 identical keys, cek_lang OK,
      every Naskah family used in code exists (checked by script)
- [x] Rumours: town-bound when the feud has a town (Kabar uses f.situs), 4 new templates pembela/gulungan/sumpah/saingan
      (json + SeteruIds.KABAR_* + Tangga.mulai/campur/akhiri)
- [x] SeteruScreen reviewed (3 tabs, scaling, keyboard already complete); props preview re-read: no change needed
- [x] Final javac 25 scratch compile of all 31 files (server + client, scratchpad/d39f/files.txt): 0 errors;
      cek_lang 639 keys OK; cek_keys missing 0; every touched file LF
- [x] Final report

## Langkah berikutnya
Done. F3: Gradle build B3 (mergeLang picks up the 194 new keys). Lang source of truth: scratchpad/d39f/lang.py
(run from repo root: python <scratch>/d39f/lang.py .).

## Butuh dari modul lain
- F3: build; no new voice keys (Naskah reuses the 18 seteru events of 39.F.7 with new caption keys, so the mega-voices
  list needs only those 18, already in VoiceEvents).
(carried from 39F) D-39: MandiriApi.lukai wound writer; Halls owner: CandidateSpec name override; D-ST/F3: 18 seteru
voice lines (VoiceEvents lists exist, clips not generated); optional Glyphs crossed swords, SAHABAT_LUAR tokoh API.

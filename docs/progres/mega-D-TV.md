# mega-D-TV — detail pass Kota Tema: model, fx, layar (W3)

Scope (jadwal W3 D-TV = files of 38TV-M + 38TV-F + 38TV-U): `client/dunia/tempat/tema/**` (model/, fx/, TemaClient,
TemaHub), `client/tempat/ui/tema/**`, `tools/gen_tempat_tema_{models,textures,fx,ui}.py`, `textures/entity/tempat/tema/**`,
`textures/dunia/tempat/tema/**`, `textures/gui/tempat/tema/**`, item models/textures props 41-62 + `dagang/**` + `tema/**`,
lang `tempat_tema_{model,fx,ui}.*`. Must NOT touch: kit `client/tempat/ui/kit/**`, server files (38TK), 38MD files.
No Gradle (F3 builds). New files LF.

## Checklist
- [x] 0 Read progress of 38TV-M/F/U, schedule, F2 notes (F2: Buku Dagang market block; optional CAHAYA uji, SPLINE kabel)
- [x] 1 Contract audit: TemaNet senders vs TemaFx.terima OK (KAPAL_JADWAL data only); KeretaGantung sends SPLINE kind
      `tali` (not `kabel`) -> step 3. Sheet keys: only unread key was Dagang laba.total -> Laba tab shows lifetime profit +
      meter to Saudagar Lima Kota (Dagang.LABA_SAUDAGAR), 3 keys
- [x] 2 Buku Dagang market tab `pasar` (first tab, only with sheet block pasar): awning + plaque, segments Beli/Jual/Segel,
      rows + detail (preview, amount, ops beli:ID:N / jual:ID:N / segel:ID, Enter acts), keeps selection by id; 36 lang keys
      (dagang.pasar.*, dagang.tab.pasar; fragment 510 identical). scratch javac tema ui 0 errors (scratchpad dtv/jc.sh)
- [x] 3 TaliGantungRenderer: full-span rope from server lane kind `tali` (smoothed, lowered into trolley groove, haul
      rope beside/below within 96 b, both run onto the bull-wheel rims); cabins on it skip pairing/stub. javac fx 0 errors
- [x] 4 Visual: contact sheet of 13 small entity textures read (scratchpad dtv/sheet1.png): banded, textured, decals OK;
      no rework (band/UV rule makes blind edits risky without in-game preview)
- [x] 5 Screens polish: market tab empty states per segment, tooltips, locked reasons, Enter acts, denied sound; Laba tab
      lifetime profit. Other 19 screens unchanged (38TV-U already covers empty/limit states)
- [x] 6 Final review: LF (0 CR) in touched files, tempat_tema_ui 513 keys identical, client-only edits, only kit/own
      APIs used (javac 0 errors tempat/ui/tema + dunia/tempat/tema)

## Langkah berikutnya
DONE. F3: Gradle compile + mergeLang.

## Masalah terbuka
- Not tested in game (no runClient): market tab layout at GUI scale 2/3/4 computed only.
- Jual button total is an upper estimate (client lacks jenuhPerUnit); server pays the exact sum.
- KeretaGantungRenderer's 6-block trolley chain slice still drawn under the full rope (overlaps, looks like the grip).

## Butuh dari modul lain
-

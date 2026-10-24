# mega-D-UI (wave W3, detail pass of 38UI) — progress

Scope (jadwal W3: D-UI = files of 38UI-0 + 38UI): kit `client/tempat/ui/kit/**`, core screens `client/tempat/ui/layar/**`,
`client/tempat/ui/{UiTempatClient,UiHub,TeksDunia,PapanHidupTeksRenderer}.java`, server `dunia/tempat/ui/**`,
`tools/gen_tempat_ui.py` -> `textures/gui/tempat/*.png` (not tema/), `lang-src/tempat_ui.*`, kota-UI.md.
Must NOT touch `client/tempat/ui/tema/**` (D-TV), LayarBukuDagang (D-TV). No Gradle. Focus: GUI scale 2/3/4 (incl.
1366x768 -> scale 4 = 341x192), keyboard only, polish, close earlier 'left' items.

## Checklist
- [x] 1. Read base LayarTempatDasar + Lukis/TabLentera/DaftarGulir/TombolAksi (layout math at 341x192, 455x256, 683x384)
- [x] 2. Review every core screen (critical case SEDANG, content height 126-138 at 1280x720/1366x768 scale 4)
- [x] 3. Fix found problems (list below with files)
- [x] 4. Texture/generator polish (Read previews), lang additions (both fragments, identical keys)
- [x] 5. Final review: all owned files LF, no client refs in dunia/tempat/ui, 76 MC imports exist in mcsrc, ItemStacks
      only in methods (UjiUi), kota-UI.md section 8 added

## Found problems / fixes ([ ] open, [x] fixed)
- [x] K1 TabLentera min lantern 18 px: 8-9 tabs overflow the footer at content 126 (toko 9 tabs) -> min 14
- [x] K2 Lukis.bungkus unbounded: story/notes overlap buttons -> bounded wrap (max height, "..", full tooltip)
- [x] K3 GelembungDialog: lines past the bubble were lost -> shrink to 0.85/0.75, then pages (Space/click next)
- [x] K4 KotakCari: '/' that opens search was typed into it -> swallow first '/'
- [x] K5 KeadaanKosong text unbounded -> bounded lines
- [x] S1 Dialog: choices cut at small heights (3rd hidden) -> reserve choice space, 2 columns when many
- [x] S2 Toko detail: preview size adaptive, services row collision guard (SEMPIT)
- [x] S4 GildaMisi detail story overlaps rewards/buttons -> bounded
- [x] S5 Bank simpan overflows at 126 -> compact layout
- [x] S6 BukuKas rencana: escort/load chips overflow under the button -> toggle lists with scroll
- [x] S7 Tagihan: rincian loop off by ~10 px, kalimat unbounded -> fit rows + "dan N lainnya" line
- [x] S8 PetaKota outer districts never wrap -> wrap rows
- [x] S9 PasarBinatang / S10 Yamen / S11 Acara (ringkasan, teka) / S12 Jurnal (detail, alur) -> bounded text
- [x] S13 Gilda nilai fixed offsets overflow at 106 -> adaptive; toko Enter = beli

- [x] S14 LayarAcara palace mode: Istana (38KS) sends acara=istana without status -> was drawn as "belum dimulai" +
      "belum ada skor"; now audience hall (portrait under canopy, catatan), teka titled audience/orders, jadwal = orders;
      head row only with status; bare calendar shows its catatan. LembarKunci.Acara.ACARA_ISTANA + javadoc.
- [x] UjiUi samples: `uji ui layar dialog panjang` (long line, 6 choices), `uji ui layar acara istana`
- [x] Lang regenerated: 754 keys (716 + 38), both fragments identical keys, old keys unchanged (checked vs build lang)

## Step 4 plan (session 3)
- [x] P1 stempel: square seal fret band + inner frame + chipped corners + mottled field; round seal beaded ring (gen_tempat_ui.py)
- [x] P2 kertas fibres, kayu sheen + gold flecks (seamless 16 px); regenerated, previews Read
- [x] P3 lang: 754/754 identical keys; all 370 static/ternary/tab keys exist (scratchpad/ui38/keys.py, cek_args.py ok)

## Langkah berikutnya
All S-fixes done (also bounded: Bank wesel/gadai, Dialog quest rows, Lelang titip, Peta, Toko kutip, Acara teka answers).
DONE (session 3). Nothing left; F3 compiles (Java edits of session 2 not yet compiled by any finish agent).
Lang: new keys go to scratchpad/ui38/lang4.py (gen_lang.py now reads lang1-4); regenerate with
`python <scratchpad>/ui38/gen_lang.py <repo>` (writes both tempat_ui fragments). Keys so far: dialog.lanjut, gilda.syarat_lagi, bank.saldo_kurang_n, kas.tanpa_murid/tanpa_muatan/maks_pilih,
tagihan.lainnya/rincian_judul.
Also done: GildaMisi ujian compact + overflow fix (fee line overlapped button), Gilda ujian same.

## Butuh dari modul lain

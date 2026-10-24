# mega-D-39 (W3 detail pass: 39A-39E stories/diary/planner tuning; files of 39A-39E)

Scope: jadwal W3 row D-39. Owns files of 39A (sekte/mandiri root + api, lang sekte_mandiri_inti), 39B (tumbuh/**,
DiscipleCultivation CRLF, DiscipleBreakthrough, SectRanks, KontribusiMurid, PaviliunMandiri, lang tumbuh), 39C (jalan/**,
lang jalan, kabar/mandiri.json), 39D (kerja/**, Duties, Keepers, NatalTreasure, bridges, lang kerja), 39E (cerita/**,
client/sekte/mandiri/** minus seteru, DiscipleScreen, gen_mandiri_{tekstur,prop,lang}.py, lang cerita).
NOT: GuideChapters (D-W), gen_voices/VoiceEvents (D-ST), seteru/** (D-39F). No Gradle.

## Checklist
- [x] Audit diary coverage (scratchpad d39/varian.py): thin = inti harian 65 bare keys, jalan harian 56 bare, kisah 124 bare,
      kerja 1-2 variants (natal/formasi/...), tumbuh/cerita 3. Tool d39/tambah.py (adds .N, copies bare key to .0)
- [x] inti harian: 65 bases x 4 variants (d39/v_inti1-3.py)
- [x] jalan harian: 56 bases x 4 (d39/v_jalan1-2.py); kisah: 124 bases x 3 (d39/v_kisah1-2.py)
- [x] Halaman.baris(line, soul, hari, tahap): scroll lines pick key variants (PilihVarian seed); chat keeps the bare key
- [x] F2 left: MandiriApi.lukai(data, jenis, now, sampai, sebab) (heavier wound kept, end may grow); Kepulangan uses it
- [x] MandiriPoses offsets via grip preview (scratchpad d39/grip: jc.sh run + grip.py, models seat legs): kuali dz -8,
      meja -7, dupa -8 (x2) beyond crossed feet; Hu Fa sword rx -60 upright (was piercing floor); terbang/pouch OK
- [x] 10 tablets at 240 px: DiscipleScreen math gives tabletH 18 (=MIN), last bottom 219 <= paperBottom 229 -> fits
- [x] Planner review vs 39.4.3 (weights/traits/temps match); fix: Skor ISTIRAHAT/MEDITASI/MERENUNG fall back to the
      spot inside the hall when no seat or walk budget full (was idle KOSONG up to an hour; acceptance 39.13.1 #1)
- [x] kerja harian: every base >= 4 (natal perak/emas/abadi 3), KabarKerja.VARIAN updated (d39/v_kerja.py)
- [x] cerita (tools/gen_mandiri_lang.py, new lagi() section): 39B cores + tokoh 3->5, renung 4->6, tutup.<cita> 3->4
      (+162 keys, regenerated, d39/chkph.py placeholders OK)
- [x] Tokoh arcs reviewed (BusurTokoh/Saingan/Asmara follow 39.8.3); kabar keys covered (F2 static scan 0 missing)
- [x] Self-review: d39/tc.sh javac of all sekte/mandiri (no seteru) + client mandiri + owned old files: 0 errors;
      lang id/en identical, 0 duplicate keys, placeholders equal (only old non-39 positional pairs differ); all edited files LF

## Langkah berikutnya
DONE. Gradle not run (F3). Not done: in-game look at props/clips; optional KebijakanAula.larang(owner, sekteId) for 39F.

## Butuh dari modul lain
- D-39F: call `MandiriApi.lukai(data, JenisLuka.TERLUKA|BERAT, now, sampai, "seteru")` for home duel losers (39F request).
- F3: build; lang-src regenerated already (gen_mandiri_lang.py deterministic, +162 keys); no new voice lines.

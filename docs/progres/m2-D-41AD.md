# m2-D-41AD — detail pass of 41A (aturan/Balai Hukum/keluar/pangkat), 41D (politik) and 41D-P (perang/pemburu)

Scope: jadwal-41-42.md row D-41AD. No Gradle, no commit. Files of 41A/41D/41D-P only (+ the three audit Halls findings
and the 41D "needs old D/musuh files" items). Status: DONE (javac clean, nothing run in game).

## Checklist
- [x] Read jadwal, m2-41A, m2-41D, m2-41D-P, m2-F41-1 (needsFromOthers)
- [x] Audit finding 1 (Halls.relocateHall skipped sectHall + Alam Abadi checks): both checks added (LF kept)
- [x] Audit finding 2 (wake of a dormant hall vs the founding veto): verified OK, the `Keluar` SteleMoveListener takes over
      every wake while `vetoAlasan != null` (no code change needed)
- [x] Audit finding 3: `Halls.accept(master, id)` added, `Tetua` auto-accept uses it (unused imports removed)
- [x] 41D leftover: allied guards 2 -> 4 for a branch (`sekte/diplomasi/Perang.java` spawnGuards + allyDefense x2 for the mother)
- [x] 41D leftover: +10 war score when a member's hall repels the raid of its sect's war enemy (`Perang.onResult`)
- [ ] absorbed-sect emblem redirect / branch decal = client LambangCache (41V) -> handed to D-41V (see Butuh)
- [x] 41A leftover: Gua Renungan meditation x0.8 (new additive hook `Meditation.addGainFactor`, CRLF kept; BalaiHukum registers)
- [x] unblessed teaching -> PemburuSekte.tambahRegu (`Keluar.ajarWarisan`)
- [x] rumours: 41W covers diterima/naik/keluar/kelas/perang/ketua/bangkit via the bus; the four 41D ones without a template
      (merosot, bangkit, peringkat, serap) got `tempat/kabar/gabung_politik.json` (12 templates, 3 variants each), args
      now `sekte:<id>`; lang in sekte_gabung_politik (generator scratchpad dd41ad/gen_kabar.py)
- [x] Promotion trial runners: new `sekte/gabung/UjianPangkat.java` (Dalam = Sword Duel vs a roster Murid Dalam / examiner body
      via `Kursi.tandingTokoh`; Tetua = corak test again at 0.85 via `UjianCorak`); `Promosi.ajukan` (CRLF kept) starts them when
      the trial is the only missing line; lang keys in sekte_gabung (+ ui texts no longer promise Alam Dewa Hampa floor 5)
- [x] Balance of PK sinks reviewed (numbers only, no code change): promotion 60/300/1200/4000/3000/8000/15000 x class kf x
      skalaBiaya (ceremony refund on logout is a straight refund); Balai Hukum fine 20 x class (20-100), Gua Renungan
      4 s per point (max 1200 s) and `tebus` 5 PK per 10 s (so <= 600 PK, a typical 35-point record = 70 PK); Cabang tithe
      10 % <= 50 x class per day; mission/offering income is capped per group (`tambahPkBatas`) and scaled by `skalaPk`.
      Sinks are bounded and below the first-rank incomes; tune only through `skalaBiaya`/`skalaPk` (config).
- [x] Sidang/rules scenes: already named (Tetua Hukum via `TokohSekte.jabatan`), nothing thin enough to rewrite blind
- [x] Lang parity (id/en identical keys, no duplicates over 43882 keys), EOL kept per file (CRLF: Meditation, Promosi,
      BalaiHukum), hard-rule grep clean (no block change / weather / time / client class / static ItemStack in new code)
- [x] javac 25 (runtime JDK, -Xlint:all, against build/classes + sourcepath) of all 11 touched Java files: exit 0, no error
- [x] Final "what was deepened" list (below)

## What was deepened
- Halls: relocateHall obeys sectHall + Alam Abadi like founding; `Halls.accept` for elder auto-accept (3 audit findings closed)
- Branch halls get 4 allied guards (mother sect first) and the mother counts double in the raid defence score
- A hall that repels the raid of its sect's war enemy adds +10 to the sect's war score
- Gua Renungan meditation x0.8 through the new `Meditation.addGainFactor` hook
- Unblessed Warisan lessons send +1 hunter (`PemburuSekte.tambahRegu`)
- Murid Dalam duel trial and Ujian Tetua (corak at 0.85) now exist (`UjianPangkat`), so ranks Dalam and Tetua are reachable
- Rumours for merosot / bangkit / peringkat / serap (12 templates, `gabung_politik.json`) with `sekte:<id>` names

## Langkah berikutnya
None for this label. F-END: compile (Gradle), run `gen_kompat_bioma` not needed (no generator touched).

## Butuh dari modul lain
- D-41V: absorbed-sect emblem redirect and branch decal (client LambangCache), succession ceremony camera/music
- D-41C: nothing new; D-42V: voice keys `gabung.hukum_*` still not added (captions only)
- F-END compile risk: `Promosi`/`BalaiHukum`/`Meditation` are CRLF (kept); `Tetua` lost two unused imports

## Open problems
- Ujian Murid Dalam has only the duel (the Alam Dewa Hampa floor-5 alternative is not built; texts updated accordingly)
- Pamit/Hukum voice lines, A3 garden theft and Paviliun theft >= 300 PK detection remain open (41C screens)
- Nothing was run in game; only javac type-check

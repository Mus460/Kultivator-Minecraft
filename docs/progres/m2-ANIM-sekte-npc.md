# m2-ANIM sekte-npc (fixer): NPC sect bodies really animated

Rule: every NPC-sect body shows a body clip + fitting prop, stands at and faces its spot.
Paths: sekte/diplomasi (not Utusan), sekte/gabung. Checklist (updated after every item).

- [x] Tubuh helpers (hadap, menuju, kerja by slot)
- [x] AnggotaSekte gate members: job clip per slot (sword drill, meditation, sweeping, yoke) + banner-bearer guard, face anchor yaw
- [x] AnggotaSekte.interact: HORMAT / ANCAM gesture
- [x] KetuaSekte gate master: DUDUK_KURSI on throne, faces anchor yaw (re-seat after a duel)
- [x] KetuaSekte hall-summoned master: SIKAP_LIPAT_TANGAN, faces the player
- [x] Perang allied guards: BERDIRI_JAGA + spear (first guard per sect keeps banner), face outward, re-assert while idle, stop at duel
- [x] Suksesi master during the succession ceremony: base stance
- [x] PemburuSekte hunters in the dialogue stage: base stance, released when the choice is made
- [x] HariUjian gate guard (compound): BERDIRI_JAGA + TOMBAK
- [x] UpacaraSumpah oath line + summoned leader: base stance
- [x] UjianHati temporary elder: base stance
- [x] Pelajaran temporary Shifu: MENGAJAR + KEBUT

Skipped (checked, with reason):
- Utusan.java: not in this slice (needs, see report).
- TokohAnchors / HidupAnggota / Pelajaran with an anchored Shifu: bodies already play the schedule's `jam` clip (REAL).
- Kursi seat duel, CeritaGabung stand-in (wakil), MisiPerang champion/captives, TurnamenMurid bouts, UjianDasar/corak bodies: duel starts at once or an event clip already plays (REAL).
- NamaSekte raiders: combat bodies.
- sekte/mandiri/seteru/TubuhSeteru (uses DiplomasiMandiri.tubuh): other slice.
Nothing compiled (no Gradle by rule); finish agent builds.
- [x] KetuaSekte duel start: SosokApi.henti so the master rises from the throne (re-verified pass: all APIs/imports exist)

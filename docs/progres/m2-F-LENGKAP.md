# m2-F-LENGKAP (finish pass over the five LENGKAP builders)

Build: `gradlew compileJava compileClientJava jar` BUILD SUCCESSFUL (7m 5s), mergeLang clean, `cek_sosok` 0 errors, `cek_tempat` 0 errors. Jar copied to `%TEMP%/kultivator-F-LENGKAP.jar`. Not run in game.

## Needs applied
- `sekte/mandiri/jalan/Tahap.java` SEKTE_TAMU: `sekteSekutu` is also true when the sect is the soul's master sect (`Politik.titipan`), so a ward's trip home is low risk and never a failed trip.
- `client/dunia/kota/TownPages.Misi`: board entries with `pk > 0` list "Poin Kontribusi sekte +N" (existing key `kultivator.kota.misi.hadiah_pk`) in the reward lines.
- `disciple/DiscipleEntity.startRiding`: allowed only when the vehicle is a `FeriAwanEntity`; boats, carts and mounts stay refused. The ferry's follower/rider boarding now really seats disciples.
- Docs: DESIGN.md (feriMisi built), 39-murid-mandiri.md (teleport mode built), PANDUAN.md (uji sekte titik, Titik Formasi build, uji ui skala, Tempa/Cincin pan, Murid Titipan, promotion, Paham aturan, Papan Misi PK, feri misi/teleport, ownership guards).

## Left
- `Tujuan.sekteDikenal` (needs item 2 of gabung) left as is: no soul at hand there; the sect is already met via `MuridTitipan` marking it met, only a war/hostile stance would still hide it.
- Titik Formasi plots for templates 3-4 (and a full set for 5-6) need an art pass on `templates/T3..T6`.
- Capital reach beyond 4000 blocks (39.6.1), ferry return leg, horizontal panning below 292 GUI px, optional `Jelmaan.hapus` public.
- K7 (town merge) and bolehPergi untouched, as instructed.

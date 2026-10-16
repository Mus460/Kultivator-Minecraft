# Kultivator — mod kultivasi xianxia untuk Minecraft 26.3 (Fabric)

**Bahasa Indonesia** · [English below](#english)

Mod Fabric berdiri sendiri di mana **pemain** berkultivasi seperti di novel xianxia: memurnikan qi, menembus ranah dari
Manusia Fana sampai Dewa Abadi, bertahan dari Tribulasi Langit, memilih Jalan Dao, menguasai teknik, terbang di atas
pedang, meracik pil, menghadapi Iblis Hati, dan mendirikan sekte.

**Aturan keras:** efek dan kekuatan mod ini tidak pernah membakar atau merusak blok apa pun. Petir tribulasi hanya
partikel dan suara (tanpa entity petir), cuaca tidak diubah, dan tribulasi tidak pernah membunuh — kalau gagal, pemain
terluka (Penyimpangan Qi).

## Status pengembangan

| Fase | Isi | Status |
|------|-----|--------|
| 0 | Proyek, data pemain (tahan respawn/keluar-masuk/pindah dimensi), perintah uji | ✅ |
| 1 | Ranah & tahap, leher botol + Qi Tertimbun, terobosan + tribulasi, meditasi, pil vanilla, bar Qi, layar info | ✅ |
| 2 | Akar spiritual, Jalan Dao, teknik, kitab teknik | ✅ |
| 3 | Iblis Hati, binatang roh, formasi pelindung, sekte | ⏳ |
| 4 | Visual mewah, alkimia, event akhir Dewa Abadi | ⏳ |

## Perintah

`/cultivate` atau `/kultivasi` — `info`, `meditate`, `breakthrough`, `hardcore on|off`, `root test`,
`dao choose <dao>`, `visual <bagian> on|off`. Perintah uji (op): `/kultivasi debug set realm <ranah> [tahap] [tingkat]`,
`set qi|xp|storedqi <jumlah>`, `set root <mutu> <elemen...>`, `set dao <dao>`, `set comprehension <nilai>`,
`set quality <kualitas>`, `addxp <jumlah>`, `fill`, `learn|forget <teknik>|all`, `deviation clear`, `toxicity clear`,
`reset`, `show <pemain>`.

## Tombol bawaan

`G` teknik terpilih · `;` meditasi · `'` layar kultivasi · `,` `.` ganti teknik · `\` pedang terbang · lompat di udara =
Langkah Awan. Semua bisa diganti di Options → Controls → Kultivator.

## Build

Java 25. `gradlew.bat compileJava compileClientJava jar --no-daemon` → `build/libs/kultivator-<versi>.jar`.
Teks ditulis per modul di `lang-src/*.en_us.json` dan `lang-src/*.id_id.json`; task `mergeLang` menggabungkannya dan
gagal kalau ada kunci yang hanya ada di satu bahasa. Rancangan lengkap: [docs/DESIGN.md](docs/DESIGN.md).

---

## English

A standalone Fabric mod for Minecraft 26.3 in which **you** cultivate like in a xianxia novel: refine qi, break through
realms from Mortal to True Immortal, survive heavenly tribulations, walk a Dao, master techniques, fly on your sword,
refine pills, face your heart demon and found a sect. Nothing in this mod ever burns or breaks a block; tribulation
lightning is particles and sound only, the weather is never changed, and a failed tribulation injures you (Qi
Deviation) instead of killing you. See [docs/DESIGN.md](docs/DESIGN.md) for every rule and number.

License: MIT.

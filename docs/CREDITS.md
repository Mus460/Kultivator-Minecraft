# Kredit / Credits

Semua kode, model dan tekstur Kultivator dibuat sendiri. Aset pihak ketiga di bawah ini gratis dan dipakai sesuai lisensinya.
All Kultivator code, models and textures are our own. The third-party assets below are free and used under their licences.

## Suara bicara (bahasa Jepang) / Japanese voices

Dibuat dengan VOICEVOX (https://voicevox.hiroshiba.jp/), VOICEVOX CORE 0.17.0 dan model suara VOICEVOX VVM 0.16.4.
Teks kalimatnya ditulis untuk Kultivator; efek gema, paduan suara dan suara iblis diolah dengan `tools/gen_voices.py`.
Generated with VOICEVOX (VOICEVOX CORE 0.17.0, VOICEVOX VVM 0.16.4). The lines are written for Kultivator and
processed (echo, choir, demon voice) by `tools/gen_voices.py`.

- VOICEVOX:青山龍星 — Suara Langit / voice of Heaven
- VOICEVOX:剣崎雌雄 — Iblis Hati / Heart Demon
- VOICEVOX:麒ヶ島宗麟 — Makhluk Purba / Ancient Ones
- VOICEVOX:ちび式じい — Pertapa Tua / Old Hermit
- VOICEVOX:†聖騎士 紅桜† — Kultivator Jahat / Rogue Cultivators
- VOICEVOX:白上虎太郎, VOICEVOX:玄野武宏, VOICEVOX:雀松朱司 — murid laki-laki / male disciples
- VOICEVOX:四国めたん, VOICEVOX:春日部つむぎ, VOICEVOX:小夜/SAYO — murid perempuan / female disciples
- VOICEVOX:ずんだもん, VOICEVOX:雨晴はう, VOICEVOX:もち子さん — obrolan warga dan riuh kota (bersama suara di atas) /
  townsfolk chatter and town crowd walla (together with the voices above)

Ketentuan tiap karakter: https://github.com/VOICEVOX/voicevox_vvm (TERMS). Kultivator adalah proyek pribadi non-komersial.
Character terms: see the VOICEVOX VVM TERMS. Kultivator is a personal, non-commercial project.

### Gabung Sekte dan Kota yang Hidup / Joining a Sect and Living Towns (DESIGN 41, 42)

133 baris suara bernama (28 klip di `sounds/voice/world/gabung/`, 105 klip di `sounds/voice/world/kota42/`) dibuat dengan VOICEVOX
oleh `tools/gen_voices.py` (daftar `KV41_42`, kalimat sumber di `docs/progres/m2-voices.md`). Tidak ada suara baru: hanya karakter
yang sudah tercantum di atas, yaitu VOICEVOX:青山龍星 (Langit), VOICEVOX:ちび式じい (tetua sekte, hakim, pendongeng, penjaga malam),
VOICEVOX:麒ヶ島宗麟 (ketua lama, wali kota), VOICEVOX:剣崎雌雄 (penjaga Pasar Hantu), VOICEVOX:白上虎太郎, VOICEVOX:玄野武宏,
VOICEVOX:雀松朱司, VOICEVOX:四国めたん, VOICEVOX:春日部つむぎ dan VOICEVOX:小夜/SAYO (warga, murid, pedagang, tukang).
133 named voice lines (28 clips in `sounds/voice/world/gabung/`, 105 in `sounds/voice/world/kota42/`) were made with VOICEVOX by
`tools/gen_voices.py` (list `KV41_42`, source lines in `docs/progres/m2-voices.md`) using only the characters already credited
above (青山龍星, ちび式じい, 麒ヶ島宗麟, 剣崎雌雄, 白上虎太郎, 玄野武宏, 雀松朱司, 四国めたん, 春日部つむぎ, 小夜/SAYO).

### Alat musik dan suara pesta kota / Town instruments and festival sounds (DESIGN 42)

Erhu, suona, pipa, dizi, gong, kentongan, genderang kecil, balok kayu, petasan, peluit merpati dan dua loop latar
(`ambient/kota/malam_meriah`, `ambient/kota/upacara`) disintesis sendiri sepenuhnya dari rumus oleh
`tools/gen_kota_hidup_suara.py` (tanpa sampel rekaman dan tanpa VOICEVOX; kedua loop latar juga mencampur loop kota yang sudah ada).
Erhu, suona, pipa, dizi, gongs, kentongan, small drum, wood blocks, firecrackers, the pigeon whistle and the two ambience loops
(`ambient/kota/malam_meriah`, `ambient/kota/upacara`) are synthesised from maths alone by `tools/gen_kota_hidup_suara.py` (no
recorded samples, no VOICEVOX; the two loops also mix the existing town loops). Every other texture, model and animation of
designs 41 and 42 is our own, made by the generators in `tools/`.

## Suara tanpa kata / Non-verbal vocal clips

- "Voice Clip Pack - Male Adventurer RPG" oleh/by Brandon Song (wolfwoot) — CC0 —
  https://opengameart.org/content/voice-clip-pack-male-adventurer-rpg (teriakan, rintihan murid laki-laki)
- "Female RPG Character voice pack" oleh/by RiddlesYouThis — CC-BY 3.0 (https://creativecommons.org/licenses/by/3.0/) —
  https://opengameart.org/content/female-rpg-character-voice-pack (oww, gah; diolah/processed)
- "Demon voice" oleh/by Tim Rockk — CC0 — https://opengameart.org/content/demon-voice (tawa, geraman, jeritan iblis; diolah/processed)

## Riuh kota / Town crowd ambience

Loop keramaian `sounds/ambient/kota/{pasar,kedai,pelabuhan,jalan,kuil}.ogg` dicampur oleh `tools/gen_riuh_kota.py`:
gumaman orang banyak dari VOICEVOX (karakter di atas), lonceng kuil, mokugyo dan dasar ombak disintesis sendiri, ditambah:
The crowd loops are mixed by `tools/gen_riuh_kota.py` from VOICEVOX walla (characters above), our own synthesised temple
bell, wooden fish and surf bed, plus:

- Kenney (www.kenney.nl) "RPG Audio" — CC0 1.0 — https://kenney.nl/assets/rpg-audio (handleCoins, footstep00-09, creak1-3,
  cloth1-4, metalPot1-3; langkah, koin, derit gerobak / footsteps, coins, cart creaks)
- Kenney "Impact Sounds" 1.0 — CC0 1.0 — https://kenney.nl/assets/impact-sounds (footstep_concrete/wood, impactGlass_light,
  impactPlate_light, impactPlank_medium, impactWood_light/heavy, impactBell_heavy; cangkir, peti, lonceng kecil / cups,
  crates, small bells)
- "Solo Seagull Sound Effects" oleh/by Rango Mango — CC0 — https://opengameart.org/content/solo-seagull-sound-effects
  (Seagull Ambient 1-7, Seagull Big Ambient 2; camar pelabuhan / harbour gulls)
- "water waves" oleh/by transitking (dikirim/submitted by qubodup) — CC0 — https://opengameart.org/content/water-waves
  (wave_01-04; ombak pelabuhan / harbour waves)

Gumaman obrolan warga (`sounds/voice/gumam/`) dibuat dengan VOICEVOX oleh `tools/gen_voices.py` (bagian GUMAM).
Townsfolk chatter murmurs (`sounds/voice/gumam/`) are VOICEVOX lines made by `tools/gen_voices.py` (GUMAM section).

## Partikel dan efek suara film / Film particles and sound effects (Kenney, CC0)

Particle sprites and sound effects: Kenney (www.kenney.nl), Particle Pack 1.1, Impact Sounds 1.0 and Sci-Fi Sounds 1.0,
licensed CC0 1.0 (https://creativecommons.org/publicdomain/zero/1.0/; credit not required, given with thanks). Every
other texture, model and sound selection of Kultivator is our own. The sprites were downscaled to 32-64 px and turned
into white tintable sprites by `tools/gen_film_assets.py`; the sounds are copied unchanged.
Sprite partikel dan efek suara: Kenney (www.kenney.nl), lisensi CC0 1.0 (kredit tidak wajib, diberikan dengan terima
kasih). Semua tekstur, model dan pilihan suara Kultivator lainnya buatan sendiri.

Berkas yang dipakai / Files used:

- Particle Pack 1.1 (`PNG (Transparent)`): spark_01, spark_02, spark_04, spark_05; flare_01, light_01, light_02, light_03;
  star_01, star_04, star_06, star_08; smoke_01, smoke_04, smoke_07, smoke_10; flame_01, flame_03, flame_05, fire_01;
  magic_01-04; slash_01-04; trace_01, trace_03, trace_05; twirl_01-03; circle_01, circle_03, circle_05; muzzle_01,
  muzzle_03, muzzle_05; dirt_01-03; symbol_01, symbol_02; scratch_01; scorch_01-03; window_02; Rotated/trace_02_rotated
  (→ `textures/particle/film_*`, `textures/misc/film/`)
- Sci-Fi Sounds 1.0: spaceEngineLarge_000-001, forceField_000-002, spaceEngineLow_000, engineCircular_000,
  thrusterFire_000-001, laserLarge_000-002, laserRetro_000, explosionCrunch_000-002, impactMetal_000-002,
  lowFrequency_explosion_000-001 (→ `sounds/film/`)
- Impact Sounds 1.0: impactBell_heavy_000-002, impactGlass_heavy_000-002, impactPunch_heavy_000-002,
  impactPlate_heavy_000-001, impactSoft_heavy_000-001, impactMining_000 (→ `sounds/film/`)

Tekstur film buatan sendiri / Our own film textures (procedural, same script): grain_0..3, anamorphic, leak_0..2, haze,
star_river, nebula_0..2, palace_strip, sun_corona, moon_disc.

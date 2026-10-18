# Kultivator — design and code contract

A standalone Fabric mod for Minecraft **26.3** (Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25) in which the
**player** cultivates like in a xianxia novel. Mod id `kultivator`, package `dev.kultivator`. All player-facing text is
translated in **both** `en_us` and `id_id` (see "Language files").

This document is the contract every contributor (human or agent) follows. Read it fully before writing code.

---------------------------------------------------------------------------------------------------------------------

## 0. Hard rules (never break these)

> **User decision (phase 6, 2026-10-17):** the ONLY exception to rule 1 below is the opt-in **Mode Kehancuran**
> (destruction mode) for very powerful disciples (DESIGN 20). It is OFF by default, switched on explicitly by the
> player/server, never touches blocks near a Sect Hall or the master, and is capped in radius and blocks per tick.
> **User decision (phase 6b, 2026-10-17):** the second exception is **Pembangunan Sekte** (DESIGN 21): the sect complex
> the player chose builds itself from deposited resources, placing blocks and levelling terrain ONLY inside the
> footprint the player previewed and confirmed; displaced natural blocks are credited to the sect storehouse.
> Everything else in the mod stays fully block-safe.
> **User decision (phase 6c, 2026-10-17):** "the world blocks must really get destroyed too, not only big effects" ->
> chosen option "Saklar gampang": Mode Kehancuran also applies to the PLAYER's own techniques (DESIGN 22). It stays OFF
> by default so a base is never wrecked by accident, but switching it on is ONE click in the Kitab Teknik (or
> `/kultivasi kehancuran nyala|mati`): no oath screen for the player's own switch, just a one-line red chat warning with
> the undo command. While it is on, every damaging technique really carves craters, trenches and split terrain that grow
> with the realm (kehancuran/WorldBreaker stays the only block-changing code: caps, MSPT pause, undo log, safe zones around
> Sect Halls, players and spawn). On a dedicated server the switch needs the server permission of 20.3.21.

1. **No block is ever burned, broken, placed, replaced or changed by any effect, power, technique, event, entity or
   formation of this mod.** Formations, alchemy furnaces and spiritual sites only *read* blocks. Concretely:
   - never call `level.setBlock`, `destroyBlock`, `removeBlock`, never place fire, never use `Explosion`/`explode`,
   - **never create a `LightningBolt` entity** (even "visual only" ones power lightning rods and de-oxidise copper),
   - never use vanilla projectiles that hit blocks (fireballs, wither skulls, wind charges, tnt, ender pearls),
   - never use the mob effects `WEAVING` (places cobwebs), `OOZING`, `INFESTED`, `WIND_CHARGED`,
   - never change weather or time,
   - never spawn creepers, ghasts, blazes, withers, ravagers, endermen or silverfish; zombies/husks/drowned that we
     spawn get `setCanBreakDoors(false)`,
   - fire only on **entities** (`igniteForSeconds` / `setRemainingFireTicks`) — that never ignites blocks.
2. **Techniques only hit monsters** (see `Targeting.isHostileTo`), never players (except the two players of an
   accepted sparring duel), pets, villagers or passive animals.
3. **Tribulation damage is a % of max health, unavoidable** (custom damage type that bypasses armor, enchantments,
   effects, resistance, shields) **and never lethal**: the hit is clamped so the player keeps at least 1 health.
   A strike that *would* have killed (raw damage ≥ health + absorption) makes the tribulation **fail** → the player is
   injured with a **Qi Deviation**, never killed. Optional Hardcore mode: failing drops one realm instead.
4. **Effects are spawned on the client** from `FxPayload`s or synced visual state, scaled by the client's
   **Effect Quality** (Minimal / Normal / Maximal). The server never spams `sendParticles` loops; sounds are played on the
   server with `level.playSound(null, ...)`.
5. Player data lives in the Fabric attachment `kultivator:cultivation` (persistent, `copyOnDeath`) so it survives
   respawn, relog and dimension change. Spirit veins / formations / sects / alchemy pots live in `SavedData`.
6. Build only with `compileJava`, `compileClientJava`, `jar` (`--no-daemon`). **Never** run tests, `runClient` or
   `gradlew build`. Feature agents do **not** run Gradle at all; a single compile step runs after them.

---------------------------------------------------------------------------------------------------------------------

## 1. Realms, stages, XP

`Realm` enum (ordinal order matters, it is saved):

| # | id | Indonesian | English | layers | base XP |
|---|----|------------|---------|--------|---------|
| 0 | mortal | Manusia Fana | Mortal | 1 (1 stage only) | 60 |
| 1 | qi_refining | Pemurnian Qi | Qi Refining | 1 | 120 |
| 2 | foundation | Pembangunan Fondasi | Foundation Establishment | 1 | 450 |
| 3 | golden_core | Inti Emas | Golden Core | 1 | 1 500 |
| 4 | nascent_soul | Jiwa Baru Lahir | Nascent Soul | 1 | 4 500 |
| 5 | deity_transformation | Transformasi Dewa | Deity Transformation | 1 | 12 000 |
| 6 | dao_union | Penyatuan Dao | Dao Union | 1 | 30 000 |
| 7 | immortal_ascension | Kenaikan Abadi | Immortal Ascension | **9** (Tahap 1–9) | 60 000 |
| 8 | true_immortal | Dewa Abadi | True Immortal | 1 | 250 000 |

Every realm except Mortal has 4 stages: 0 Awal/Early, 1 Menengah/Middle, 2 Akhir/Late, 3 Puncak/Peak.
Kenaikan Abadi has 9 layers, each with the 4 stages. A position is a `Rank(realm, layer, stage)`; `Rank.step()` is
its linear index (0 = Mortal … 64 = True Immortal Peak).

**XP needed to fill the bar** at a rank: `base(realm) × (1 + 0.35 × stage) × (1 + 0.30 × layer)` (`Rank.xpRequired()`).

* Below Peak: when the bar is full the player advances one stage automatically ("minor breakthrough": sound + small
  FX + chat line), leftover XP carries over (loop, so a big gain can climb several stages).
* **Bottleneck at Peak** (and at Mortal): the bar stops at full. Every further XP goes to **Qi Tertimbun / Stored Qi**
  (`storedQi`, never lost, uncapped). After a successful breakthrough the stored Qi flows into the new rank's bar
  (normal stage-up rules apply) until it is used up or the next bottleneck is reached.
* True Immortal Peak is the end; XP then only adds Stored Qi (shown as "Qi Abadi").

**XP sources** (all multiplied by `Progression.xpMultiplier(player)` = root grade × spiritual site (meditation only) ×
breakthrough-quality bonus × server config `xpMultiplier`; deviation halves XP):
* Combat (killing): hostile (`Enemy`) mob → `maxHealth × 0.5`; boss (Wither, Ender Dragon, Warden, Elder Guardian)
  → `maxHealth × 2`; other mobs → 0. Heart Demon gives its own reward. Dao Protector monsters (§3) give half (they
  are drawn by the heavens, not hunted).
* Mining (player breaks, no Silk Touch on the tool): coal 1, copper 1, nether quartz 1, nether gold 1, iron 2,
  redstone 2, lapis 3, gold 3, diamond 10, emerald 12, amethyst cluster 2. Deepslate variants +50%. Ancient debris
  gives nothing (it drops itself, so it could be placed and mined again forever).
* Meditation: per second `0.6 + 0.25 × realm` × multipliers × spirit vein density (see §4).
* Pills (vanilla in phase 1, own pills in phases 2/4), reduced by pill toxicity (§9).
* Enlightenment moments, Heart Demon victory, tribulation success bonuses.

Commands always show XP as integers; data keeps doubles.

## 2. Stats from cultivation

`Stats.apply(ServerPlayer)` (called on join, respawn, rank change, quality change, Dao change) sets **permanent**
attribute modifiers with fixed ids (`kultivator:realm_*`), recomputed with `addOrReplacePermanentModifier`:
* `MAX_HEALTH` +`realm × 4 + stage + layer × 2` (True Immortal +60) × quality factor.
* `ATTACK_DAMAGE` +`realm × 0.6 + stage × 0.15 + layer × 0.3`.
* `MOVEMENT_SPEED` multiplied base +`min(0.25, realm × 0.025)`.
* `ARMOR` +`realm` (Body Dao +50%), `SAFE_FALL_DISTANCE` +`realm × 1.5`, `BLOCK_BREAK_SPEED` +`realm × 0.05` multiplied.
* Quality factor from all recorded breakthrough qualities (Cracked 0.9, Normal 1.0, Perfect 1.15, Heavenly 1.3 —
  averaged).
Max Qi = `100 + realm × 120 + stage × 30 + layer × 60` × quality factor × root grade max-qi factor.
Qi regen per second = `max × 0.004` (×6 while meditating, ×1.5 in a spiritual site, ×0 during deviation).

## 3. Breakthrough and Heavenly Tribulation

Triggered with `/cultivate breakthrough`, the info-screen button, or the meditate key while meditating at a full Peak
bar (a hint is shown). Requirements: full bar at Peak (or Mortal), not already in a breakthrough, not in deviation,
spiritual root tested (for Mortal → Qi Refining). Moving more than 6 blocks from the start point cancels (counts as fail
only after the first strike). Logging out during a trial or after the first strike is a failure too, applied when the
player is back; damage interrupts the Qi Refining gathering but never the awakening.

* **Mortal → Qi Refining** "Awakening": needs a tested root. Always succeeds; 6 s cinematic of qi gathering.
* **Qi Refining → Foundation**: chance roll, no tribulation. `chance = 0.75 + root bonus + pill bonus + site bonus +
  storedQi bonus (min(0.1, storedQi / xpRequired × 0.1))` capped 0.98.
* **Foundation → Golden Core and every higher step** (including each Immortal Ascension layer step and → True
  Immortal): **Heavenly Tribulation**.

**Tribulation** (`Tribulation` class, one active instance per player, ticked on the server):
1. **Omen** (pertanda langit) 12 s: chat/title text, dark clouds (client FX) gathering 18–24 blocks above the player,
   rumbling thunder **sounds** at low volume. **No weather change.**
2. **Strikes**: count `= 3 + 2 × (realmIndex − 3)` for Golden Core → Dao Union (3,5,7,9 for target realms GC, NS, DT,
   DU), Immortal Ascension layer L target: `9 + L`, True Immortal: 18. Interval 70 ticks (3.5 s). Each strike:
   client FX bolt from the sky to the player (particles only) + thunder sound + `TRIBULATION` damage
   `= maxHealth × pct`, `pct = 0.12 + 0.01 × targetRealm` (+0.005 × layer), last strike × 2. Pills/formations can
   reduce pct (multiplier, min 0.5×). If `pct×maxHealth ≥ health + absorption` the strike *would* kill → the
   tribulation **fails** right there (deal damage clamped to leave 1 HP).
3. **Variants** by dimension: Overworld (white/violet lightning, dark clouds), **Nether** "Tribulasi Api Neraka"
   (columns of flame/soul-fire particles, blaze/ghast *sounds*, sets the **player** on fire 2 s — entity only),
   **End** "Tribulasi Kehampaan" (end-rod/portal/dragon-breath particles, dragon growl sounds, short levitation-free
   void pulse). Other dimensions use Overworld style.
4. **Dao Protector event** ("Pelindung Dao", config `daoProtectorEvent`, default on): during strikes, waves of hostile
   mobs attracted by the phenomenon spawn on valid ground 10–16 blocks away (no block changes, never inside blocks):
   Overworld skeleton/spider/zombie(no door breaking)/stray/husk (vindicator from Deity Transformation; no witches:
   splash potions are vanilla projectiles),
   Nether wither skeleton/magma cube/hoglin (no piglin brutes: they open doors), End endermite/phantom. Skeletons get
   swords instead of bows, nobody picks up loot, zombies call no reinforcements, raiders never join raids, and
   protectors only hurt players. Waves every 3 strikes, size
   `1 + targetRealm/2`. They carry tag `kultivator.dao_protector`; leftovers vanish with a poof when it ends. Allies,
   pets and sect members nearby help ("Dao protectors").
5. **Result**: success → rank advances to the new realm's Early stage, `BreakthroughQuality` rolled, Stored Qi flows in,
   full heal, big FX + sound, server-wide chat announcement for Golden Core and above.
   Failure → **Qi Deviation** (Penyimpangan Qi): lose 30% of the bar (Stored Qi untouched), deviation for
   `2400 + 600 × realm` ticks (no qi regen, XP halved, Weakness I, Slowness I, occasional nausea, health clamped
   damage `25%` of max — never lethal), FX of black-red qi. Hardcore (per player opt-in `/cultivate hardcore on`,
   or server config `hardcoreForced`): instead drop **one realm** (to Peak of the previous realm with an empty bar;
   Mortal floor).
   Death during a tribulation = failure (applied after respawn).

**BreakthroughQuality** (`CRACKED, NORMAL, PERFECT, HEAVENLY`, saved per realm reached): score
`= random(0..1) × 0.5 + lowestHealthFraction × 0.3 + rootGrade × 0.05 + pills/site/formation bonuses`.
`<0.35` Cracked (Retak), `<0.7` Normal (Biasa), `<0.9` Perfect (Sempurna), else Heavenly (Surgawi). Non-tribulation
breakthroughs use `random × 0.7 + bonuses`. Quality affects §2 factors and Dao comprehension (+0/+2/+5/+10 %).

## 4. Meditation

Toggle with the meditate key (default `;`) or `/cultivate meditate`. Needs: on the ground, not swimming/flying/riding,
not in tribulation. Moving (> 0.5 block from the start), taking damage, attacking, or using an item ends it.
* Pose: cross-legged on the ground (client mixin: lowered body, legs crossed, hands on knees in a mudra).
* FX: qi motes spiralling into the dantian, colour of the root's main element; quality-scaled.
* Gains every second: Qi regen ×6, XP (§1), small Dao comprehension for Life/Space/etc. per site.
* **Spiritual sites** (checked when meditation starts and every 5 s, multiplicative, capped ×4):
  Mountain peak (`y ≥ 120` in the Overworld with sky access) ×1.5 · Cherry grove biome ×1.4 · Active beacon within 8
  blocks ×1.5 · ≥ 4 amethyst blocks/clusters within 4 blocks ×1.3 · The End dimension ×1.6 · Full moon at night with
  sky access ×1.5. Shown on the HUD while meditating.
* **Spirit veins** (`SpiritVeins` SavedData per dimension, key = chunk pos >> 2, i.e. 4×4-chunk regions):
  density 0..1 (default 1). Meditation drains `0.0015/s × (1 + realm × 0.25)`; regenerates `0.0002/s` lazily from the
  stored game time. XP × max(0.15, density). HUD/info shows "Kepadatan Qi / Qi density".
* **Enlightenment moment** (Momen Pencerahan): each 60 s of meditation, chance `0.04 × site multiplier` (+0.02 with
  Dao chosen); 10 s golden FX, XP burst `= 25% of xpRequired` (to stored qi if bottlenecked), Dao comprehension +3,
  achievement.
* **Qi gathering formations** (built by the player out of vanilla blocks, detected relative to the block the player
  sits on, re-checked every 5 s, read-only):
  * Small "Formasi Pengumpul Qi Kecil": 4 Amethyst Blocks at (±3,0,0),(0,0,±3) on the floor level → ×1.25.
  * Great "Formasi Pengumpul Qi Agung": the small one + 4 Amethyst Blocks at (±3,0,±3) + the player sits on a Gold
    Block → ×1.6.
  * Heavenly "Formasi Bintang Surgawi": the great one + 4 End Rods standing on the cardinal amethyst blocks + a
    Beacon or Lodestone at (0,−1,0) under the gold block… (the player sits on gold) → ×2.2.
  Active formations show qi lines from the pillars to the player (client FX).

## 5. Qi bar (HUD)

Bottom-left corner: a round **dantian orb** (qi fill rises inside a circle, colour of the root element) wrapped by a
**gold ring** that fills clockwise with cultivation progress; next to it the realm + stage name in realm colour, Qi
numbers, "+N Qi Tertimbun" when stored qi > 0, deviation timer in red, the selected technique as a small **seal/talisman
tile** with its Qi cost and a radial cooldown sweep, and while meditating the site multiplier and qi density.
Hidden in F1 / F3. Qi is spent by active techniques (§8).

## 5b. GUI style — must be its own look, NOT like Butter Bot (user request)

Butter Bot uses vanilla-looking rectangular buttons (`McButton`/`McStyle`), top tabs and a dark list panel. Kultivator
must look and be laid out **differently**; never copy Butter Bot's screen classes, button class or layout.
Theme "Gulungan Giok" (jade scroll), everything drawn by our own widget classes in `client/screen/ui/`:
* Panel: an unrolled ancient scroll — dark ink-blue backdrop, a parchment/jade inner panel with a thin gold double
  border, ornamental corner knots, faint cloud pattern lines, scroll rods at top and bottom.
* Navigation: **vertical jade tablets on the left edge** (tall narrow tiles with an icon glyph + short name, the
  active one glows and slides out a little) — no top tab row.
* Buttons: **talisman buttons** (paper-yellow tile with a red seal stamp square at the left and ink text; hover makes
  the seal glow, press shifts it 1 px) for actions; **round jade-coin buttons** for small toggles (+/-, on/off).
* Status tab: a big **dantian circle** in the centre — concentric rings for cultivation progress (gold), Qi (blue)
  and stored qi (violet), realm name in calligraphy-like bold text inside; stats listed on a side "bamboo slip" column.
* Root & Dao tab: the **Bagua octagon** of the 8 Daos around a yin-yang centre (click a trigram to read/choose), root
  elements as five-element pentagram nodes lit by the player's root.
* Techniques tab: a grid of **seal cards** (square, element-coloured frame, lock overlay when not learned, cooldown
  sweep), selected card has a pulsing gold frame.
* Settings tab: effect quality as three stacked talismans (Minimal / Normal / Maksimal), cosmetics as jade-coin toggles.
* Animation: open with the scroll unrolling (0.25 s), tablet hover glow, soft ease on every bar. Sounds: UI clicks use
  amethyst chime / bamboo-like note-block sounds instead of the vanilla button click.
Screens must fit 320×240 GUI-scaled windows (scale down the circle and wrap cards when small).

## 6. Spiritual roots (phase 2)

`Element`: METAL Logam, WOOD Kayu, WATER Air, FIRE Api, EARTH Tanah, and mutant LIGHTNING Petir, ICE Es,
WIND Angin, SPACE Ruang. `RootGrade`: 0 TRASH Sampah (×0.6), 1 LOW Rendah (×0.85), 2 MID Menengah (×1.0),
3 HIGH Tinggi (×1.3), 4 HEAVENLY Surgawi (×1.7) — XP/qi multiplier. Rolled **once** at the **root testing ceremony**
(info screen button or `/cultivate root test`, Mortal only, 8 s cinematic: pillars of each element light up in turn,
then the result) and **saved** (`rootTested`, `rootElements` bitmask, `rootGrade`) — never recomputed.
Roll: 1–5 elements (weights 5:10%, 4:25%, 3:30%, 2:25%, 1:10%), 6% chance one element is a mutant; grade weights
TRASH 15, LOW 30, MID 30, HIGH 18, HEAVENLY 7; fewer elements shift grade up by `(3 − count) × 0.5` steps (rounded
with random). **Pil Pembersih Sumsum / Marrow Cleansing Pill** raises the grade one step (max Heavenly), 8 s painful
FX (black impurities), Nausea + Hunger; 25% chance to also remove one extra element when there are ≥ 3.
Element affinities: METAL→Sword, FIRE→Fire, WATER/ICE→Ice, LIGHTNING→Lightning, EARTH→Body, WOOD→Life,
WIND→Shadow/Wind, SPACE→Space. Affinity = ×1.5 Dao comprehension gain.

## 7. Dao paths (phase 2)

Chosen once when the player is at Golden Core or higher (prompt after the Golden Core breakthrough; info screen /
`/cultivate dao choose <dao>`): SWORD Pedang, FIRE Api, ICE Es, LIGHTNING Petir, BODY Tubuh, LIFE Kehidupan,
SHADOW Bayangan/Angin, SPACE Ruang. Admin reset via debug command only.
**Dao comprehension** 0–100 (double, per Dao array) grows from: using the Dao's techniques (+0.15 per cast), Dao actions
(Sword kills with a sword +0.05, burning mobs for Fire, killing in snowy/frozen biomes for Ice, fighting in
thunderstorms for Lightning, taking damage for Body, healing for Life, sneaking kills / night kills for Shadow,
ender pearls / the End for Space), enlightenment, books, breakthrough quality. Levels: 0 Pemula/Novice, 10 Kecil/Minor,
30 Besar/Major, 60 Sempurna/Perfect, 90 Wilayah/Domain. Technique damage × `(1 + comprehension / 100)`.

**Technique books use vanilla enchanted books**: sneak + use an enchanted book. Each stored enchantment is mapped
(sharpness/sweeping_edge→Sword, fire_aspect/flame→Fire, frost_walker→Ice, channeling→Lightning,
protection/thorns→Body, mending→Life, swift_sneak/feather_falling→Shadow, infinity/loyalty→Space,
smite→Buddha Palm). If it teaches a technique the player can learn now, it is learned and the book is consumed; if the
technique is known or it is a Dao book of the player's Dao, the book gives +5 comprehension; otherwise "you cannot
comprehend this". Unknown enchantments give 30 XP.

## 8. Techniques (phase 2)

`Technique` enum (ids are saved): metadata `dao` (null = universal), `minRealm`, `qiCost`, `cooldownTicks`,
`unlock` (REALM auto when realm reached, DAO auto on choosing the Dao, BOOK from an enchanted book after comprehension
threshold). Cast with the cast key (`G`) on the selected technique; `,`/`.` cycle the selection; the flying sword also
has its own toggle key (`\`). Damage base `D = 4 + 2.5 × realm + 0.5 × stage` × comprehension factor. Every technique
hits only targets accepted by `Targeting` and never touches blocks (block raycasts are read-only `clip` to stop beams).

| id | name id/en | dao | realm | qi | cd s | effect |
|----|-----------|-----|-------|----|------|--------|
| cloud_step | Langkah Awan / Cloud Step | – | QR | 6 | 0.5 | extra air jumps (QR 1, FE 2, GC+ 3) + no fall damage after |
| qi_palm | Telapak Qi / Qi Palm | – | QR | 10 | 1.5 | qi blast 12 blocks forward, D |
| flying_sword | Pedang Terbang / Flying Sword | – | FE | 2/s | 2 | player really flies standing on a sword (needs any sword in inventory) |
| divine_sense | Indra Ilahi / Divine Sense | – | NS | 30 | 20 | Glowing on monsters in 48 blocks for 12 s + ore pings (radius 12) shown 10 s |
| buddha_palm | Telapak Buddha / Buddha Palm | – | GC | 45 | 12 | giant golden palm falls on the looked-at spot (≤ 24), 5-block AoE 2.5 D, knock-down |
| dharma_form | Wujud Dharma / Dharma Form | – | DT | 80 | 60 | 30 s: scale ×2 (needs head room), +Strength II, Resistance I, reach +2 |
| dao_domain | Wilayah Dao / Dao Domain | – | DU | 120 | 90 | 20 s, radius 10 following the player, Dao-flavoured effect on monsters each second |
| sword_qi | Tebasan Qi Pedang / Sword Qi Slash | SWORD | GC | 20 | 3 | crescent wave 16 blocks, pierces, 1.5 D |
| thousand_swords | Seribu Pedang / Thousand Swords | SWORD | NS+book | 90 | 40 | swords rain on every monster within 16 for 4 s |
| flame_palm | Telapak Api / Flame Palm | FIRE | GC | 20 | 3 | 7-block cone, D, ignite 6 s |
| phoenix_flame | Api Phoenix / Phoenix Flame | FIRE | NS+book | 80 | 35 | fire phoenix flies forward 24 blocks, burns all monsters it passes, 2 D |
| frost_lotus | Lotus Es / Frost Lotus | ICE | GC | 20 | 4 | 6-block nova, D, Slowness IV + freeze 6 s |
| glacier_prison | Penjara Gletser / Glacier Prison | ICE | NS+book | 70 | 30 | target + monsters within 5 frozen in place 8 s, 1.5 D |
| thunder_palm | Telapak Petir / Thunder Palm | LIGHTNING | GC | 25 | 4 | particle bolt on the target, chains to 4 more, D |
| heavenly_thunder | Petir Surgawi / Heavenly Thunder | LIGHTNING | NS+book | 90 | 40 | 8 particle bolts on monsters within 16, 2 D each |
| iron_body | Tubuh Besi / Iron Body | BODY | GC | 25 | 30 | 15 s Resistance II + knockback immunity + reflect 30% melee |
| mountain_stomp | Hentakan Gunung / Mountain Stomp | BODY | NS+book | 60 | 20 | 7-block shockwave, 2 D, launches monsters up |
| spring_of_life | Mata Air Kehidupan / Spring of Life | LIFE | GC | 30 | 20 | heals self + allies/pets in 8 by 40% max, cures poison/wither |
| life_drain | Serap Kehidupan / Life Drain | LIFE | NS+book | 60 | 25 | drains monsters within 8 (D each), heals 50% of dealt |
| shadow_step | Langkah Bayangan / Shadow Step | SHADOW | GC | 20 | 6 | teleport behind the looked-at monster (≤ 20) safely, next hit ×2 |
| gale | Badai Angin / Gale | SHADOW | NS+book | 50 | 20 | push monsters in 10 away hard + Invisibility & Speed II 6 s |
| space_blink | Kedipan Ruang / Space Blink | SPACE | GC | 25 | 5 | teleport to the looked-at spot (≤ 24) if safe |
| space_prison | Penjara Ruang / Space Prison | SPACE | NS+book | 80 | 35 | monsters within 12 locked in place 6 s and pulled together, 1.5 D |

Dao Domain effect per Dao each second (monsters inside): Sword 0.5 D cut, Fire ignite + 0.3 D, Ice Slowness III +
freeze, Lightning one particle bolt 0.8 D on a random target, Body caster Resistance II, Life caster + allies regen II
and monsters Wither I, Shadow monsters Blindness + caster Invisibility, Space monsters Slowness V + pulled 0.3/s;
without Dao: 0.4 D qi pressure.
**Flying sword**: `abilities.mayfly/flying` on (survival/adventure only), flying speed `0.05 + 0.01 × realm`,
drains 2 qi/s (−0.2 per realm, min 0.5); out of qi, damage, or toggle → land with Slow Falling 6 s. The sword item is
the best sword in the inventory and is rendered under the feet (phase 4 visuals; phase 2 shows particles). Saved flag
is cleared on join; abilities are only restored when we set them. Creative/spectator are never touched.

## 9. Pills and alchemy

Phase 1 — **vanilla items as pills** (eaten normally, or sneak + use for non-food items; a mixin on
`LivingEntity.completeUsingItem` reports finished eating):
golden carrot 8 XP · glow berries 2 XP + 5 qi · honey bottle: −20% deviation time (once per deviation) · golden apple 30 XP + 30 qi ·
enchanted golden apple 250 XP + full qi + tribulation pct ×0.85 for the next tribulation · (sneak-use, consumed)
glistering melon slice 20 qi · amethyst shard 15 qi · echo shard 60 XP + Indra hint · dragon's breath 400 XP ·
nether star 1500 XP + breakthrough chance +10% next time · experience bottle (sneak-use) 15 XP.
**Pill toxicity** `toxicity` 0–100: each pill adds its toxin (2–40), decays 1 per 30 s; XP from pills ×
`(1 − toxicity/120)`; above 80 → Nausea + Poison I for 5 s per pill.

Phase 4 — **Alchemy**: a Cauldron (any) standing on a lit Campfire or Soul Campfire is an alchemy furnace (read-only
detection). Right-click it with an ingredient to add one item to its pot (stored in `AlchemyPots` SavedData,
max 8 items, shown in the action bar); sneak + empty-hand right-click starts refining (10 s FX); a recipe table maps
an unordered multiset of items to a pill; success chance `0.6 + alchemyLevel × 0.04 + soul fire 0.1 + Fire Dao 0.1`,
quality grade (Low/Mid/High/Perfect) from a roll + level; failure gives smoke and nothing. The pot is dropped as items
if the furnace disappears; results and spilled ingredients pop out beside the cauldron, never into it (a lava cauldron
would burn them). Bar-scaled XP pills are capped (Qi Gathering 15% of the bar, at most 450 XP; Soul Nurturing 30%, at
most 3600 XP; both before quality), and outside Qi Refining Foundation pills raise the next breakthrough bonus to at
most +0.15. Own pill items (data component `kultivator:pill_quality`, 0–3, multiplies effect
0.8/1.0/1.25/1.6): Qi Gathering Pill, Qi Recovery Pill, Foundation Establishment Pill, Golden Core Pill, Marrow
Cleansing Pill (phase 2 adds it with a crafting recipe), Heart Calming Pill, Healing Pill, Tribulation Warding Pill,
Soul Nurturing Pill, Spirit Beast Pill (phase 3 adds it with a crafting recipe), Immortal Pill.

## 10. Trials, beasts, formations, sects (phase 3)

* **Heart Demon** (Iblis Hati, entity `kultivator:heart_demon`): a dark copy of the player (player model with the
  owner's skin, dark tint, red glowing eyes). Appears as a trial at the breakthroughs into Nascent Soul, Dao Union and
  True Immortal (before the tribulation omen), or rarely when `heartDemonPressure` (from failed breakthroughs, pill
  toxicity, many kills) exceeds 100. Fights only its owner, ignores and is ignored/immune to everything else, never
  explodes, never drops items or XP, not saved to disk, despawns if the owner leaves/dies or after 120 s. Health
  `owner max health × 1.2`, damage `owner attack`. Defeat → "Hati Dao Teguh" (+0.1 quality score, +XP, pressure 0,
  achievement); owner drops below 20% health or timeout → trial lost → Qi Deviation (tribulation does not start).
  Taunt lines (translated) in chat every ~15 s.
* **Spirit beasts**: a tamed wolf (owner = player) or a fox that trusts the player, fed a **Spirit Beast Pill**, awakens
  (attachment `kultivator:spirit_beast` on the animal: tier 0–3, xp). It gains XP from kills near its owner and pills;
  tiers: Wolf → Serigala Roh / Spirit Wolf → Serigala Petir Bulan / Moon Thunder Wolf → Serigala Langit / Heavenly Wolf;
  Fox → Rubah Roh / Spirit Fox → Rubah Ekor Tiga / Three-Tailed Fox → Rubah Ekor Sembilan / Nine-Tailed Fox.
  Each tier: permanent modifiers (health +10/+20/+40, damage +2/+4/+8, speed +10%/20%/30%, scale 1.0/1.1/1.2), aura
  FX, evolution cinematic. Spirit foxes follow their owner (navigation each second) and attack what attacks the owner.
* **Protective formation** (Formasi Pelindung): Lodestone with 4 Crying Obsidian at (±4,0,0),(0,0,±4) on the same level.
  Built formations start **inactive** (default off); the owner sneak-uses the lodestone with an Amethyst Shard to
  register/activate it (each shard = 10 min of power, max 2 h), sneak-uses with empty hand to switch it off. While
  powered and loaded: hostile mobs inside radius 16 are pushed out and take 1 damage per second; shimmering dome FX.
  Data in `Formations` SavedData; server config `protectiveFormations` (true) can disable the feature.
* **Sects** (multiplayer; `Sects` SavedData on the overworld): `/sect create <name>`, `invite <player>`,
  `join <name>`, `leave`, `kick <player>`, `info`, `list`, `chat <msg>`, `spar <player>`, `spar accept`; alias `/sekte`.
  The founder is Sect Master (Ketua Sekte), members are disciples (Murid). Meditating within 16 blocks of a sect mate:
  XP ×1.15. **Sparring** (Latih Tanding): both agree, 120 s, only their damage to each other is allowed through
  techniques; damage that would bring either below 10% max health ends the duel instead (never lethal); winner +XP and
  Dao comprehension, announcement to the sect.

## 11. Visuals (phase 4 polishes; phase 1/2 provide basic aura)

Synced `VisualState` (attachment `kultivator:visual`, synced to everyone) drives client-only rendering:
aura per realm (colour from `Realm.color`, density by stage), robe (Jubah: translucent robe texture overlay on the
player model, colour by Dao), sword on the back (`backSword` item id), Dao circle (rotating rune ring under the feet
while meditating / casting / domain), glowing eyes (emissive layer, Nascent Soul and above), flying sword under the
feet, Dharma form golden outline. Each cosmetic can be switched off per player (`/cultivate visual <part> on|off`, and
the info screen). **Effect Quality** Minimal/Normal/Maximal is a client setting (info screen, saved in
`config/kultivator-client.json`): particle counts ×0.25 / ×1 / ×2 and Maximal adds extra flourishes.

## 12. Achievements, info screen, commands

* **Advancements** tab "Kultivasi" (`data/kultivator/advancement/...`, root `kultivator:root` with `minecraft:impossible`
  criteria, granted from code with `Advancements.grant(player, "name")`). Each feature adds its own advancement files.
* **Info screen** (key `'`): tabs Status (rank, bars, stats, stored qi, deviation, toxicity), Root & Dao, Techniques
  (select, see costs/locks), Settings (effect quality, HUD on/off, cosmetics), Help. Buttons: meditate, breakthrough,
  root test (Mortal, untested), choose Dao (eligible).
* **Commands** `/cultivate` with alias `/kultivasi`:
  `info`, `meditate`, `breakthrough`, `hardcore on|off`, `root test`, `dao choose <dao>`, `technique select <id>`,
  `visual <part> on|off`, and op-only (`LEVEL_GAMEMASTERS`) `debug`:
  `set realm <realm> [stage] [layer]`, `set qi <amount>`, `set xp <amount>`, `set storedqi <amount>`,
  `set root <grade> <elements...>`, `set dao <dao>`, `set comprehension <value>`, `learn <technique>|all`,
  `breakthrough force`, `tribulation test`, `heartdemon summon`, `deviation clear`, `reset`, `show` — all with
  `[player]` target where it makes sense.

---------------------------------------------------------------------------------------------------------------------

## 13. Code architecture and contracts

```
src/main/java/dev/kultivator/
  Kultivator.java                 entrypoint; registers spine + calls every module's init()
  KultivatorConfig.java           server config (config/kultivator.json)
  data/Realm.java Rank.java Element.java RootGrade.java Dao.java Technique.java BreakthroughQuality.java
  data/CultivationData.java       ALL per-player persistent fields (+ NBT read/write with defaults)
  data/VisualState.java           synced record for other clients' rendering
  data/ModAttachments.java        CULTIVATION (player), VISUAL (player, synced); the animal attachment
                                  kultivator:spirit_beast is registered by beast/BeastData.TYPE (phase 3), not here
  data/Cultivators.java           get(player) / dirty(player) / refreshVisual(player) / sync helpers
  network/Payloads.java           SyncSelf (S2C), Action (C2S), Fx (S2C), OrePings (S2C), Notify (S2C)
  network/Net.java                registration, sending, Action dispatch → module handlers
  network/Actions.java            action ids (constants) for every phase
  fx/FxType.java                  every effect id (enum, ordinal sent)
  fx/Fx.java                      Fx.send(level, pos, type, args...) to players within 96 blocks
  command/CultivateCommand.java   /cultivate + /kultivasi root, info + debug; calls Module.addCommands(root, debug)
  util/Msg.java Targeting.java Advancements.java Text.java
  cultivation/ ... technique/ ... (feature modules, see ownership table)
src/client/java/dev/kultivator/client/
  KultivatorClient.java           client entrypoint; receivers; calls client module inits
  ClientCultivation.java          last SyncSelf snapshot (static) + helpers
  EffectQuality.java              client setting
  Keys.java                       all key mappings → Action payloads
  fx/FxDispatcher.java            FxPayload → handler classes per feature
  ... (feature client modules)
```

**Module convention.** Every feature module is a final class with `public static void init()` (register its events,
called once from `Kultivator.onInitialize`), optionally `public static void tick(ServerPlayer player,
CultivationData data)` (called every server tick for each player by `Kultivator`'s player tick loop — keep it cheap,
use `player.tickCount % N`), optionally `addCommands(LiteralArgumentBuilder<CommandSourceStack> root,
LiteralArgumentBuilder<CommandSourceStack> debug)` and `handleAction(ServerPlayer, Payloads.Action)`. The spine
already calls all of them (stubs exist); feature authors fill in the bodies and may add private classes in their own
package. **Never edit a file owned by another module**; if you need something from another module, call its public
static API listed in this document, or (if missing) add a TODO note to your final report.

**CultivationData mutation.** Mutate fields through `CultivationData` setters/helpers, then call
`Cultivators.dirty(player)` (the spine syncs dirty players to their own client at most every 5 ticks and refreshes the
synced `VisualState` when visual fields change). XP must always be added through `Progression.addXp(player, amount,
XpSource)` so bottleneck/stored-qi/stage-ups/multipliers are applied in one place. Qi is spent with
`Qi.trySpend(player, amount)` and restored with `Qi.restore(player, amount)`.

**Effects.** Server: `Fx.send(ServerLevel, Vec3 pos, FxType type, int a, int b, float f)` (or `Fx.sendToTarget`).
Client: `FxDispatcher` switch calls the owning client class (e.g. `TribulationFx.play(type, pos, a, b, f)`). Continuous
effects (aura, meditation motes, domain dome, formation lines, beast aura) are driven on the client each tick from the
synced `VisualState` / `ClientCultivation`, never from server packets. Always multiply particle counts with
`EffectQuality.scale(n)` and check `EffectQuality.maximal()` for extras.

**Language files.** Do **not** edit `assets/kultivator/lang/*.json` directly. Each module writes its own fragments
`lang-src/<module>.en_us.json` and `lang-src/<module>.id_id.json` (flat JSON objects, UTF-8). The Gradle task
`mergeLang` merges `lang-src/*.<code>.json` into the generated `en_us.json` / `id_id.json` and **fails the build if a key
exists in one language but not the other or is defined twice**. Key prefixes: `kultivator.<module>.*`, items
`item.kultivator.*`, entities `entity.kultivator.*`, keys `key.kultivator.*`, advancements
`advancement.kultivator.<name>.title/.description`, damage `death.attack.kultivator_<type>` (+ `.player`).
Indonesian text should sound natural and use the xianxia terms from this document.

**Minecraft 26.3 API notes** (verify everything against the decompiled sources in
`C:\Users\Musyaffa\Downloads\butterbot-fabric\reference\mcsrc` and the Fabric API jars in
`%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api\`; Butter Bot in
`C:\Users\Musyaffa\Downloads\butterbot-fabric\src` is a proven example — read-only, never modify it):
* `Identifier` (not ResourceLocation), `Identifier.fromNamespaceAndPath`, `Kultivator.id(path)`.
* GUI: `GuiGraphicsExtractor g` with `g.fill`, `g.text(font, text, x, y, color, shadow)`, `g.item`, `g.pose()` (2D
  matrix: `pushMatrix/translate/scale/popMatrix`); HUD via `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
  id, element)` implementing `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`. Screens: see Butter Bot
  `client/screen/*` (`minecraft.gui.setScreen`, `minecraft.gui.screen()`). The F1 "hide GUI" flag is
  `minecraft.gui.hud.isHidden()` (`options.hideGui` does not exist in 26.3).
* Entities we spawn that must never come back from disk (Dao Protectors, Heart Demon) are refused with
  `ServerEntityEvents.ALLOW_LOAD` (reason `LOAD`) instead of being discarded in `ENTITY_LOAD`.
* Keys: `KeyMappingHelper.registerKeyMapping(new KeyMapping("key.kultivator.x", InputConstants.Type.KEYBOARD, key,
  CATEGORY))`, `KeyMapping.Category.register(Kultivator.id("main"))`.
* Commands: op check `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`.
* Messages: `player.sendSystemMessage(Component)`, `player.sendOverlayMessage(Component)` (action bar).
* Damage: `entity.hurtServer(ServerLevel, DamageSource, float)`; custom damage types via data JSON +
  `level.damageSources().source(ResourceKey<DamageType>)`-style lookups (check `DamageSources`).
* Velocity change on a player must set `player.syncVelocity = true` (or `hurtMarked`-equivalent) to reach the client.
* Attributes: `Attributes.MAX_HEALTH, ATTACK_DAMAGE, MOVEMENT_SPEED, ARMOR, SAFE_FALL_DISTANCE, BLOCK_BREAK_SPEED, SCALE,
  ENTITY_INTERACTION_RANGE, BLOCK_INTERACTION_RANGE, KNOCKBACK_RESISTANCE, FLYING_SPEED…`; `new AttributeModifier(id,
  amount, Operation.ADD_VALUE)`; `instance.addOrReplacePermanentModifier`, `addOrUpdateTransientModifier`,
  `removeModifier(id)`.
* SavedData: `new SavedDataType<>(Kultivator.id("x"), X::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE)`,
  `level.getDataStorage().computeIfAbsent(TYPE)`, `server.getDataStorage()` for global data, `setDirty()`.
* CompoundTag getters return Optional; use `getIntOr`, `getDoubleOr`, `getStringOr`, `getListOrEmpty`,
  `getCompoundOrEmpty`, `getBooleanOr`.
* Rendering: entity render states (`AvatarRenderState` for players), `SubmitNodeCollector`, layers via
  `LivingEntityRenderLayerRegistrationCallback`; render states implement Fabric's `FabricRenderState`
  (`setData/getData` with `RenderStateDataKey`). Player renderer is `AvatarRenderer`.

## 14. File ownership by phase

Phase 0 (spine, done first): everything in §13 spine list, `build.gradle`, `fabric.mod.json`, mixin configs,
root advancement, `lang-src/core.*`.

Phase 1:
* **P1-progression**: `cultivation/Progression.java`, `cultivation/Qi.java`, `cultivation/Stats.java`,
  `cultivation/XpSources.java` (combat + mining hooks), `lang-src/progression.*`, advancements `realm_*`, `stage_up`.
* **P1-breakthrough**: `cultivation/Breakthrough.java`, `cultivation/Tribulation.java`,
  `cultivation/DaoProtectors.java`, `cultivation/Deviation.java`, damage type JSONs + tags,
  `client/fx/TribulationFx.java`, `client/fx/BreakthroughFx.java`, `lang-src/breakthrough.*`, advancements
  `awakening`, `first_breakthrough`, `first_tribulation`, `perfect_breakthrough`, `heavenly_breakthrough`,
  `qi_deviation`, `nether_tribulation`, `end_tribulation`, `true_immortal_tribulation`.
* **P1-meditation**: `cultivation/Meditation.java`, `cultivation/SpiritSites.java`, `cultivation/SpiritVeins.java`,
  `cultivation/QiFormations.java`, `cultivation/Enlightenment.java`, client `client/fx/MeditationFx.java`,
  `client/render/MeditationPose.java` + mixins `client/mixin/AvatarRendererMixin.java`,
  `client/mixin/PlayerModelMixin.java` (registered in `kultivator.client.mixins.json`, which the spine creates empty),
  `lang-src/meditation.*`, advancements `first_meditation`, `enlightenment`, `qi_formation*`, `spiritual_site`,
  `depleted_vein`.
* **P1-pills**: `cultivation/VanillaPills.java`, `cultivation/PillToxicity.java`, mixin
  `mixin/LivingEntityMixin.java` (registered in `kultivator.mixins.json`, which the spine creates empty),
  `lang-src/pills.*`, advancements `first_pill`, `pill_poisoned`, `forbidden_pill`.
* **P1-hud-screen**: `client/QiHud.java`, `client/screen/CultivationScreen.java` (+ helpers in `client/screen/`),
  `client/fx/AuraFx.java` (basic per-realm aura), `lang-src/hud.*`, `lang-src/screen.*`.

Phase 2: P2-roots (`cultivation/SpiritRoots.java`, ceremony FX, Marrow pill item + recipe), P2-dao
(`dao/DaoPaths.java`, `dao/Comprehension.java`, `dao/TechniqueBooks.java`), P2-techniques-a (universal techniques +
flying sword), P2-techniques-b (Dao techniques), each with their own client FX classes and lang fragments.
Phase 2 contracts (see §15).
Phase 3: P3-heart-demon (entity + renderer), P3-beasts, P3-formations, P3-sects.
Phase 4: P4-visuals (render layers, robe/eyes/back sword/Dao circle textures, flying sword render), P4-alchemy (items,
pots, recipes, textures), P4-final (True Immortal event, remaining achievements, README).

## 15. Phase 2 contracts

Spine additions made before phase 2 starts:
* `registry/ModItems.register(name, factory, properties)` registers an item and puts it in the "Kultivator" creative
  tab (`ModItems.TAB`). Item model files: `assets/kultivator/items/<name>.json` + `models/item/<name>.json` +
  `textures/item/<name>.png` (generate textures with `tools/png.py`, 16×16, readable pixel art).
* `technique/UniversalTechniques`, `technique/DaoTechniques`, `dao/Comprehension`, `dao/TechniqueBooks`,
  `client/fx/DaoTechniqueFx` stubs with the signatures below.

**Casting flow (P2-techniques-a owns `Techniques`, `UniversalTechniques`, `FlyingSword`, `TechniqueFx`):**
`Techniques.handleAction` → SELECT_TECHNIQUE / SELECT_NEXT / SELECT_PREVIOUS cycle through learned techniques that
can be cast (Cloud Step is passive: never selected); CAST → `Techniques.tryCast(player, technique)`:
known, realm ≥ minRealm, Dao technique only with that Dao chosen, not deviating, not breaking through, cooldown over,
enough qi (Qi.trySpend after a successful cast), casting stops meditation. It calls
`UniversalTechniques.cast(player, data, technique)` or `DaoTechniques.cast(player, data, technique)`; each returns
true when the technique really happened (false = nothing to hit / no safe spot → no qi or cooldown spent, the
implementation tells the player why). After success: spend qi, set the cooldown, `Comprehension.onTechniqueUsed`.
Shared helpers in `Techniques` (public static, used by both technique modules):
`double power(CultivationData data)` = D of section 8 (`4 + 2.5 × realm + 0.5 × stage` × `(1 + comprehension/100)`),
`boolean hurt(ServerPlayer caster, LivingEntity target, float amount)` (damage type `kultivator:technique`, attacker =
caster so kills count for XP and loot; returns true when it hit; counts `data.addTechniqueKill()` on kills),
`void fx(ServerPlayer caster, Technique technique, int sub, Vec3 pos, Vec3 target, float scale)` (sends FxType.TECHNIQUE
with a = technique ordinal).
The Dao Domain (universal, P2-techniques-a) calls `DaoTechniques.domainPulse(player, data, dao, insideHostiles)` once per
second when a Dao is chosen; without a Dao it applies the qi pressure itself.
Client: `TechniqueFx.play` handles universal technique ordinals and forwards Dao technique ordinals to
`DaoTechniqueFx.play(fx)` (P2-techniques-b); continuous effects (domain dome, flying sword trail, dharma glow) in
`TechniqueFx.tick` from the synced VisualState flags.

**Dao (P2-dao owns `DaoPaths`, `Comprehension`, `TechniqueBooks`, `DaoFx`):** `DaoPaths.choose` (Golden Core+, none chosen,
grants the DAO techniques through `Techniques.refreshUnlocks`, FX DAO_CHOSEN, advancement); after a player reaches
Golden Core without a Dao it sends `OpenScreen(CHOOSE_DAO)` once per session plus a chat hint.
`Comprehension.add(player, dao, amount)` applies the ×1.5 root affinity; `Comprehension.onTechniqueUsed(player,
technique)`; the Dao action hooks of section 7 live in `Comprehension.init()`.
`TechniqueBooks.init()` handles sneak + use of a vanilla enchanted book (stored enchantments component).

**Roots (P2-roots owns `SpiritRoots`, `RootFx`, the Marrow Cleansing Pill):** keeps `SpiritRoots.roll/describe` and
replaces the instant test with the ceremony; the pill is an item `kultivator:marrow_cleansing_pill` with a shapeless
recipe (glistering melon slice + ghast tear + golden apple + amethyst shard + bone meal).

## 16. Phase 3 contracts

Spine additions made before phase 3 starts: `Kultivator.onInitialize` calls `trial.HeartDemons.init()`,
`beast.SpiritBeasts.init()`, `formation.ProtectiveFormations.init()`, `sect.Sects.init()` (before `ModItems.init()`), the
per-player tick calls `HeartDemons.tick`, `SpiritBeasts.tick`; `KultivatorClient` calls `client.trial.TrialClient.init()`
(entity renderer registration) and `client.beast.BeastClient.init()`.

* **P3-heart-demon** owns `trial/*`, `client/trial/*`, `client/fx/TrialFx.java`, entity `kultivator:heart_demon`
  (registered in its own `trial/TrialEntities` with `FabricDefaultAttributeRegistry`), `lang-src/trial.*`. It implements
  `Breakthrough.TrialProvider` (needsTrial for targets Nascent Soul, Dao Union and True Immortal when the trial of that
  realm is not passed yet and `KultivatorConfig.heartDemonTrials`) and registers it with `Breakthrough.setTrialProvider`
  in `init()`; it reports with `Breakthrough.onTrialFinished(player, passed)`. Pressure-triggered demons (pressure > 100)
  are a separate, non-breakthrough trial (lost = `Deviation.apply(player, 0.6)`). The entity carries `NoGrief.TAG`, is
  excluded from other players' techniques with `Targeting.addExclusion`, never explodes, drops nothing, gives no vanilla
  XP and is never saved (`shouldBeSaved`/equivalent false, discarded when its owner is gone).
* **P3-beasts** owns `beast/*`, `client/beast/*`, `client/fx/BeastFx.java`, the item `kultivator:spirit_beast_pill`
  (+ shapeless recipe: bone + glow berries + amethyst shard + golden carrot), `lang-src/beasts.*`. Data in a persistent
  attachment `kultivator:spirit_beast` on the animal (synced to all players for the aura): owner UUID, tier 0–3, xp.
* **P3-formations** owns `formation/*`, `client/fx/FormationFx.java`, `lang-src/formations.*`; SavedData per dimension.
* **P3-sects** owns `sect/*`, `client/fx/SectFx.java`, `lang-src/sects.*`; global SavedData on the overworld; commands
  `/sect` and `/sekte`; sets `Targeting.sparring`; may change the single XP line in `Meditation` to multiply by
  `Sects.meditationBonus(player)` (1.15 near a sect mate, else 1.0).

## 17. Phase 4 contracts

Spine additions made before phase 4 starts: `Kultivator.onInitialize` calls `alchemy.Alchemy.init()` and
`ascension.Ascension.init()` (before `ModItems.init()`), the per-player tick calls `Ascension.tick`; `KultivatorClient`
calls `client.render.CultivatorRender.init()`.

* **P4-visuals** owns `client/render/*` except `MeditationPose.java` (new: `CultivatorRender.init()` registering player
  render layers through `LivingEntityRenderLayerRegistrationCallback` for the player renderer), `client/fx/AuraFx.java`
  (takes it over from phase 1), generated textures under `assets/kultivator/textures/entity/` and `textures/misc/`,
  `lang-src/visuals.*`. Layers, all driven by the synced `VisualState` (and its SHOW_* cosmetic flags) and scaled by
  `EffectQuality`: robe (Jubah: an inflated player-model overlay with a generated robe texture tinted by the Dao colour,
  realm trims), sword on the back (`swordItem`, rendered as an item diagonally across the back, hidden while flying or
  holding that sword), glowing eyes (emissive eyes layer, Nascent Soul+, colour by Dao/realm), Dao circle (a rotating
  rune ring quad under the feet while meditating, casting a domain or breaking through; translucent emissive, quality
  decides segment count), the flying sword drawn as the real sword item lying flat under the feet while FLYING_SWORD,
  Dharma form golden outline glow, and a richer per-realm aura (AuraFx). First-person view stays clean.
* **P4-alchemy** owns `alchemy/*`, `client/fx/AlchemyFx.java`, the pill items it adds (items/models/textures), the data
  component `kultivator:pill_quality` (registered in `alchemy/AlchemyComponents`), `lang-src/alchemy.*`. Furnace = any
  cauldron on a lit campfire / soul campfire (read-only check); pots in SavedData; recipe table in code; quality grades;
  alchemy XP in `CultivationData.alchemyXp`; pill effects per DESIGN 9.
* **P4-final** owns `ascension/*`, `client/fx/AscensionFx.java`, `lang-src/ascension.*`, the final advancements, the
  user guide `docs/PANDUAN.md` (Indonesian) and README updates. When a player reaches True Immortal and has not ascended
  yet, the ascension event runs (heavens open, golden pillar, celestial sounds, server-wide announcement, rainbow aura via
  `data.setAscended(true)` and the ASCENDED visual flag, an immortal gift: immunity to Qi Deviation and slow constant
  regeneration).

## 18. Phase 5 — Disciples (Murid), requested by the user after phase 4 started

"Bots that can be taught or become cultivators too", built entirely inside Kultivator (no dependency on or code from the
Butter Bot mod; Butter Bot stays a separate mod).

* **Entity `kultivator:disciple`** (PathfinderMob, humanoid 0.6×1.8, player model): owner UUID, name (random
  xianxia-style name from a translated list, renameable with a name tag), skin = one of the default player skins (wide or
  slim) chosen at recruitment, synced visual data (realm, stage, dao, root colour, flags: meditating, breaking through,
  fighting). Persistent with the world (ValueOutput/ValueInput), never despawns, `NoGrief.TAG`, never breaks/places blocks,
  opens no doors, picks up nothing, cannot be leashed/ridden. Max 5 disciples per player (count stored on the player in
  `CultivationData.extra()`).
* **Recruitment:** item *Gulungan Penerimaan Murid* (recruitment_scroll; shapeless: paper + gold ingot + amethyst shard +
  ink sac). Use it → a disciple appears next to the player (FX, sounds) as a Mortal with a rolled root
  (`SpiritRoots.roll`), saved once.
* **Disciple cultivation** (`disciple/DiscipleCultivation`): its own realm/layer/stage/xp/storedQi/qi/root/dao/
  comprehension/learned techniques/qualities in the entity's saved data, using the same `Rank` math and bottleneck rules
  (with a disciple XP multiplier 0.6 so it is slower than the master). XP from monster kills it takes part in, from
  meditating (idle, sits cross-legged near the master or at its post), pills the master gives (right-click with a vanilla
  pill item or a kultivator pill: same effect table, scaled for the disciple), and 10% of the master's meditation XP when
  meditating within 8 blocks of the master. At a full bottleneck the master starts its breakthrough from the disciple
  screen: awakening needs nothing, Qi Refining → Foundation is a chance roll, Golden Core+ is a mini tribulation
  (particles + sounds, strikes = % of its max health, never lethal; failure = 3 minutes of weakness and −30% bar).
  Dao chosen by the master in the disciple screen when it reaches Golden Core.
* **Teaching:** the master teaches any technique the master knows and the disciple qualifies for (realm, Dao) — costs the
  master 20% of max qi; the disciple can then use it. Disciples use a simplified cast of each technique
  (`disciple/DiscipleTechniques`): same visuals (FxType.TECHNIQUE), damage `(3 + 2 × realm + 0.5 × stage)`, only on
  targets `Targeting.isHostileTo(target, master)` accepts, never the master's allies; flying sword/dharma/domain/divine
  sense are not usable by disciples (they show as "cannot be taught").
* **Orders (modes):** follow (default; teleports near when > 24 blocks), stay & guard a spot (fights monsters within 12),
  meditate at a spot (cultivates faster: ×1.5, does not fight unless hit), assist (aggressively attacks what the master
  attacks or what attacks the master). Via the disciple screen (right-click the disciple) or `/kultivasi murid ...`.
* **Combat:** melee with its own attack damage (scaled by realm), casts taught techniques with its own qi pool and
  cooldowns, retreats to the master below 25% health. Regenerates slowly.
* **Death:** drops *Papan Jiwa Murid* (disciple_tablet) holding all its data in the item's custom data; the owner uses
  it to bring the disciple back at full health (others cannot). The tablet is immune to fire/lava/explosions/cactus.
* **Screen (client):** jade-scroll style like the cultivation screen (DESIGN 5b): tabs Status (rank, bars, root, dao,
  stats), Teknik (teach from the master's known techniques; locked ones explain why), Perintah (modes), Pil (give the pill
  in hand), Terobosan button. Sends `Payloads.Action` with new action ids (DISCIPLE_*) carrying the disciple entity id.
* **Commands:** `/kultivasi murid list|panggil|ikut|tinggal|meditasi|bantu` (nearest/looked-at disciple), debug
  `murid set realm ...`, `murid xp <n>`, `murid summon`. `murid lupakan <n> [konfirmasi]` frees the place of a living
  entry whose body is lost; souls that fell while the master was offline and forgotten souls are kept in the overworld
  SavedData `kultivator:disciple_records` (`disciple/DiscipleRecords.java`).
* **Visuals:** renderer with the player model, its skin, aura per realm (reuse AuraFx style), cross-legged pose while
  meditating, robe tint by Dao, name tag "<name> · <realm>".
* **Lang:** `lang-src/disciples.*` (id_id + en_us), names lists in both languages.

### 18.1 Phase 5 code contract (three modules)

Spine (done): `Actions.DISCIPLE_*` (arg = disciple entity id), `Payloads.DiscipleInfo(entityId, CompoundTag)`,
`Payloads.OpenScreen.DISCIPLE` (arg = entity id), `Net` routes DISCIPLE_* to `disciple.Disciples.handleAction`,
`Kultivator` calls `Disciples.init()`, `CultivateCommand` calls `Disciples.addCommands`, `KultivatorClient` calls
`client.disciple.DiscipleClient.init()`, routes `DiscipleInfo` to `DiscipleClient.onInfo` and `OpenScreen.DISCIPLE` to
`DiscipleClient.openScreen`.

* **P5-disciple-core** owns `disciple/Disciples.java`, `disciple/DiscipleEntity.java`, `disciple/DiscipleEntities.java`
  (entity type registration + attributes), `disciple/DiscipleAi*.java` (goals, modes), `disciple/DiscipleItems.java`
  (recruitment scroll, soul tablet), `disciple/DiscipleNames.java`, items' JSON/textures, recipe, `lang-src/disciples.*`
  (core texts), advancements first_disciple, disciple_revived, five_disciples.
  `DiscipleEntity` API used by the others: `DiscipleData data()`, `@Nullable ServerPlayer owner()`,
  `UUID ownerId()`, `Mode mode()` (`enum Mode { FOLLOW, STAY, MEDITATE, ASSIST }`), `boolean isMeditatingNow()`,
  `void refreshSynced()` (copies realm/layer/stage/dao/root colour/flags from `data()` into synced entity data and
  re-applies attribute bonuses from `DiscipleCultivation.maxHealthBonus/attackBonus/speedBonus`),
  `int skinIndex()`, `boolean slimSkin()`; synced getters for the client: `syncedRealm()`, `syncedLayer()`,
  `syncedStage()`, `syncedDao()` (-1 none), `syncedRootColor()`, `syncedFlags()` (bits: 1 meditating, 2 breaking
  through, 4 fighting, 8 deviating), `ownerName()`. Saving: `data().save()` under key "kultivator_disciple".
  Each server tick the entity calls `DiscipleCultivation.tick(this)`; when it fights it asks
  `DiscipleCultivation.tryCastInCombat(this, target)` every 20 ticks before melee; on kills it calls
  `DiscipleCultivation.onKill(this, victim)`. `Disciples.handleAction` validates ownership and distance (≤ 16 blocks)
  and calls `DiscipleCultivation.teach / feed / startBreakthrough / chooseDao` and the mode setter, then sends
  `Payloads.DiscipleInfo(id, DiscipleCultivation.infoFor(entity, master))` to the master (also on DISCIPLE_OPEN followed
  by `OpenScreen(DISCIPLE, id)`). Right-clicking your own disciple with an empty hand sends the same (opens the screen);
  with a pill it feeds; with a name tag vanilla renaming works.
* **P5-disciple-cultivation** owns `disciple/DiscipleData.java` (all cultivation fields, `CompoundTag save()`,
  `static DiscipleData load(CompoundTag)`, `static DiscipleData fresh(RandomSource)` rolling the root), 
  `disciple/DiscipleCultivation.java` (`tick`, `addXp(entity, amount, XpSource)`, `onKill`, `tryCastInCombat`,
  `feed(entity, master, stack)` → boolean, `teach(entity, master, technique)` → boolean,
  `startBreakthrough(entity, master)` → boolean, `chooseDao(entity, master, dao)` → boolean,
  `CompoundTag infoFor(entity, master)` (data + live values + which master techniques are teachable and why not),
  `double maxHealthBonus(DiscipleData)`, `attackBonus`, `speedBonus`), `disciple/DiscipleTechniques.java`
  (`static boolean teachable(Technique)`, `static boolean cast(DiscipleEntity, Technique, LivingEntity target)`),
  `disciple/DiscipleBreakthrough.java` (mini tribulation), `lang-src/disciplecult.*`, advancements disciple_awakened,
  disciple_golden_core, disciple_taught.
* **P5-disciple-client** owns `client/disciple/*` (`DiscipleClient`: renderer registration via `EntityRenderers`, the
  screen, a cache of the last `DiscipleInfo`; renderer + render state using the player model and the default skin
  `skinIndex/slimSkin`, the meditation cross-legged pose (reuse `client/render/MeditationPose` math), a simple robe tint
  layer, glowing name line "<name> · <rank>"), `client/fx/DiscipleFx.java` (aura per realm for disciples, breakthrough
  visuals for `FxType.TECHNIQUE`-independent disciple effects sent with `FxType.BURST/RING/PILLAR`), `lang-src/discipleui.*`.
  The screen reuses the jade-scroll widgets in `client/screen/ui/*` (read-only use) and follows DESIGN 5b.


## 19. Phase 7 (postponed by the user; was planned as phase 6): "Dunia yang Hidup" (treasures, demonic beasts, guidance)

> **User rule (2026-10-17): every idea must come from real cultivation comics/stories.** Before this phase is built,
> each feature below gets a verified "Sumber / Source" (work + concept) and anything without a real source is dropped.

The user asked us to enrich the features and to add or perfect whatever is still missing. Today the mod has a deep cultivation spine. Its gaps:

- The world gives the player little reason to go anywhere. Every spirit vein is the same, structures hold nothing for cultivators, and alchemy runs on sugar and berries.
- Combat stops mattering from Golden Core up, because vanilla mobs die in one hit and flat kill XP is either meaningless or farmable.
- New players get no guidance after the root test.
- Picking a technique and preparing for a tribulation are blind.
- The longest realm, Immortal Ascension, unlocks nothing.

Phase 6 fixes these with an item economy, elite monsters, a guided path and UX polish. It changes no blocks and uses no world generation.

Phase 6 starts after phase 4 is committed. Its file ownership does not overlap phase 5 (disciples), which may still be in progress. Phase 6 never touches `disciple/*`, `client/disciple/*` or `lang-src/disciple*`.

### 19.0 Principles for this phase

* **Block safety (DESIGN 0) holds without exception.**
  - Drops (stones, herbs) appear only *after* a player broke the block themselves, as item entities through `Block.popResource`.
  - Structure treasure is added only to built-in chest loot tables through `LootTableEvents.MODIFY` (`source.isBuiltin()`).
  - Elites are vanilla mobs that vanilla already spawned naturally. The mod upgrades them; it never spawns new world mobs, apart from the existing Dao Protector waves.
  - Elite skills, talismans and techniques touch entities only: effects, knockback, particle "bolts" and entity fire. Never a `LightningBolt` and never a projectile.
  - Formation blueprints and Divine Sense pings only read blocks.
* **Performance (a laptop that also runs Valorant).**
  - Everything is event-driven or runs every 10–100 ticks, with bounded scans.
  - Vein grades come from an O(1) seeded hash. Nothing ticks per region.
  - Continuous visuals are drawn on the client within 32 blocks and scaled by Effect Quality. The new **Otomatis** quality drops effects when FPS falls.
* **Singleplayer first, LAN bonus.** Every feature works alone. Co-op parts (Pengawal Dao, tradeable talismans and stones) add value on LAN.
* **Constants and translation.** Every number below is a `public static final` constant in its class, so screens and forecasts read the real value. Every player-facing text is in `id_id` + `en_us`. All GUI follows DESIGN 5b (jade scroll).

### 19.1 Spine additions (orchestrator, before the four modules start)

1. **`network/Actions.java`.** Append these ids:
   - `CONDENSE_QI = 21` (arg = count 1–9) → `harta.SpiritStones.handleAction`
   - `REFORGE = 22` → `cultivation.Reforge.handleAction`
   - `STAR_CONDENSE = 23` → `cultivation.ImmortalPerks.handleAction`
   - `GUIDE_HIDE = 24` (arg 1 hide, 0 show) → `goals.Goals.handleAction`

   Route all four in `Net.handle`.
2. **`data/CultivationData`.** Add a session-only `CompoundTag sessionExtra` with accessor `sessionExtra()`:
   - not saved
   - cleared in `resetSession()`
   - written in `saveForClient()` under `"session_extra"` and read in `loadFromServer()`

   Modules write HUD values here (keys in 19.3.5) and then call `Cultivators.dirty`. In `Kultivator.onJoin`, write `sessionExtra.cfg = {hd: heartDemonTrials, dp: daoProtectorEvent, guide: guideRewards, daily: dailyTasks, tele: tribulationTelegraph}`.
3. **`util/Advancements`.** Add `addListener(BiConsumer<ServerPlayer, String>)`. Listeners are called only when `award` really completed the advancement.
4. **`cultivation/Breakthrough`.** Add `interface QualityBonus { double bonus(ServerPlayer p, CultivationData d, Rank target); }` and `addQualityBonusProvider(QualityBonus)`.
   - The sum of all providers, clamped to −0.10…+0.35, is added to the quality score of tribulation and non-tribulation rolls.
   - The awakening is excluded.
5. **`cultivation/Tribulation`.** Add the following hooks:
   - `interface StrikeFactor { double factor(ServerPlayer p, Rank target, int strikeIndex, boolean last); }` with `addStrikeFactorProvider`. Providers are multiplied into the raw strike damage.
     - Providers × pills × config are floored at 0.5×.
     - The would-kill check uses the final damage.
   - `interface Listener { default void onOmen(ServerPlayer p, Rank t){} default void onStrike(ServerPlayer p, Rank t, int index, int total){} default void onEnd(ServerPlayer p, Rank t, boolean success, boolean test){} }` with `addListener`.
6. **`KultivatorConfig`.** New fields:
   - `eliteMonsters = true`
   - `eliteChanceScale = 1.0`
   - `structureLoot = true`
   - `spiritStoneMining = true`
   - `herbHarvest = true`
   - `guideRewards = true`
   - `dailyTasks = true`
   - `tribulationTelegraph = true`
7. **`fx/FxType`.** Append these types and their routes in `FxDispatcher`:

   | FxType | Routed to |
   |---|---|
   | `ELITE` | `client/fx/EliteFx` |
   | `TRIBULATION_RING`, `DAO_GUARDIAN` | `TribulationFx` |
   | `TALISMAN` | `client/fx/TalismanFx` |
   | `TREASURE` | `client/fx/HartaFx` |

   `FxDispatcher.tick` also calls `EliteFx.tick`, `TalismanFx.tick` and `client.goals.FormationBlueprint.tick`.
8. **Entry points.**
   - `Kultivator.onInitialize` (before `ModItems.init()`) calls `harta.Harta.init()`, `elite.EliteMonsters.init()`, `talisman.Talismans.init()`, `cultivation.ImmortalPerks.init()`, `cultivation.Reforge.init()` and `goals.Goals.init()`.
   - The per-player tick calls `ImmortalPerks.tick`, `Reforge.tick`, `Talismans.tick` and `Goals.tick`.
   - `CultivateCommand` calls `addCommands` of `Harta`, `EliteMonsters`, `Talismans`, `ImmortalPerks`, `Reforge` and `Goals`.
   - `KultivatorClient` calls `client.harta.HartaClient.init()`, `client.elite.EliteClient.init()`, `client.talisman.TalismanClient.init()` and `client.goals.GoalsClient.init()`. `GoalsClient` registers its own HUD elements.
9. **Mixin stubs.**
   - Empty `mixin/MobFinalizeSpawnMixin` (target `Mob`), listed in `kultivator.mixins.json`.
   - Empty `client/mixin/MouseHandlerMixin` (target `MouseHandler`), listed in `kultivator.client.mixins.json`.
10. **`CultivationScreen` tabs.** Add `TAB_PATH = 5` (stub page `PathPage`, owned by P6-jalan) and `TAB_ALCHEMY = 6` (stub page `AlchemyBookPage`, owned by P6-harta). Existing tab constants keep their numbers. P6-jalan may change the display order of the tablets.
11. **Stub classes with the exact public signatures of 19.3.4.** Bodies are trivial; the owning module replaces them. Every module then compiles against the others regardless of order.

### 19.2 The items (biggest value first)

#### 19.2.1 Binatang Iblis: elite demonic beasts (new feature, P6-pertempuran)

**Why.** From Golden Core on, vanilla monsters are neither a challenge nor progress. Elites give every realm something to hunt, and they feed the new loot.

**Hook.** `MobFinalizeSpawnMixin`, at the TAIL of `Mob.finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason, SpawnGroupData)`:

- `EntitySpawnReason.SPAWNER` → tag `kultivator.spawner`.
- `NATURAL` → elite roll.
- Every other reason, including `TRIAL_SPAWNER`, `STRUCTURE`, `PATROL`, `EVENT` and `COMMAND`, is untouched.

**Eligible types (allowlist).** Zombie, husk, skeleton, stray, bogged, spider, wither skeleton, hoglin. Never creeper, enderman, drowned (tridents), slime, magma cube, phantom, witch, blaze, ghast, piglins, raiders or silverfish.

**Roll.** The nearest non-spectator player within 48 blocks must be Qi Refining or higher. Mortals never meet elites.

| Factor | Value |
|---|---|
| Base chance | `0.01 + 0.005 × realm` of that player |
| Full-moon night in the Overworld | ×2 |
| Spawn in a Great Spirit Vein region (19.2.5) | ×1.5 |
| Config | × `eliteChanceScale` |
| Cap | 7% |

**Limits.** These checks run after the random roll succeeds, so they are cheap:

- At most 3 elites within 64 blocks of that player.
- No other elite within 24 blocks.
- 60 s cooldown per player after an elite appeared near them.
- No elite if 10 or more `Enemy` mobs are within 16 blocks of the spawn point (stops dark-room farms).

**Tiers.**

| Tier | Indonesian / English | Share | Needs nearest player |
|---|---|---|---|
| 1 | Siluman / Wild Demon Beast | 65% | Qi Refining+ |
| 2 | Binatang Iblis / Demonic Beast | 28% | Foundation+ |
| 3 | Raja Siluman / Demon Beast King | 7% | Golden Core+, at most one within 128 blocks |

A tier whose requirement fails falls back to the next lower tier.

**Stats.** R = the nearest player's realm ordinal at spawn. All are permanent modifiers with ids `kultivator:elite_*`:

| Attribute | Formula / value |
|---|---|
| MAX_HEALTH | × `tierMul × (1 + 0.35R)`, tierMul = 1.5 / 2.5 / 4.0, then healed to full |
| ATTACK_DAMAGE | + `1.5 × tier + 0.8R` |
| ARMOR | + `R + 2 × tier` |
| KNOCKBACK_RESISTANCE | 0.3 / 0.5 / 0.8 |
| SCALE | 1.10 / 1.20 / 1.35 |
| MOVEMENT_SPEED | +5% / 10% / 15% |

Example: a Golden Core tier-3 zombie has 164 HP. A True Immortal tier-3 zombie has 304 HP.

**Element.**
- 75%: the mob's natural element (`elite.MobElements`, see 19.2.13).
- 25%: a random one of the nine.

**Skills (entity-only).**

*Tier 1: passive on hit*

| Element | Effect on hit |
|---|---|
| Fire | Entity ignite 3 s |
| Ice / Water | Slowness II 3 s |
| Wood | Poison I 4 s |
| Metal | Weakness I 4 s |
| Earth | Extra knockback 0.6 |
| Lightning | 20% chance of a 2-damage particle bolt |
| Wind | Permanent +30% speed |
| Space | 15% chance to land behind the target (`Targeting.safeSpot`) |

*Tier 2: adds one telegraphed active every 8–10 s against its current target*

| Elements | Skill | Effect |
|---|---|---|
| Fire / Lightning | **Tanda Langit** | Ring of radius 1.5 under the target. After 1.5 s, `6 + 3 × tier + 0.5R` damage if the target is still inside. |
| Ice / Water | **Napas Es** | 6-block cone: Slowness IV 4 s + 3 damage |
| Earth / Metal | **Hentakan Bumi** | Players within 4 launched 0.6 up + `5 + 2 × tier` damage |
| Wind / Space | **Kedipan** | Lands behind the target (safe spot) |
| Wood | **Duri Racun** | Poison II 5 s within 5 |

*Tier 3:* the natural element's active plus one random other. It also gets a `ServerBossEvent` ("Raja Siluman · Api" / "Demon Beast King · Fire") shown to players within 24 blocks, updated every 10 ticks.

**Affix.** Tier 2+ rolls one:
- **Kulit Besi**: technique damage taken ×0.65, applied in `Techniques.hurt`.
- **Penyerap Qi**: each hit drains 8 qi from a player.
- **Gesit**: +30% speed.
- **Pulih Cepat**: regenerates 1 HP/s.

**Hardening** (shared with `DaoProtectors.harden`, made public):
- `setCanPickUpLoot(false)`.
- Zombies `setCanBreakDoors(false)`.
- Skeleton, stray and bogged swap their bow for an iron sword (arrows break pots, chorus and dripstone).
- Tags `NoGrief.TAG` + `kultivator.elite`.
- **No custom name**, so elites still despawn like normal mobs.
- Data in the synced, persistent attachment `kultivator:elite {tier, element, realm, affix}` (copy the `BeastData` sync pattern).
- Converted mobs lose elite status.

**Rewards.** Only when the killer resolves to a player: direct, technique, projectile owner, or the player's spirit beast or tamed wolf. Fall or lava deaths without credit give nothing extra.

- **XP:** `max(combatXp × (3 / 5 / 8), killerBar × (0.01 / 0.025 / 0.05) × clamp(1 + 0.5 × (R − killerRealm), 0.25, 2.0))`.
  - Then the normal multipliers and Karma Darah (19.2.15) apply.
  - Example: a tier-3 kill at Immortal Ascension L1 ≈ 5% of the bar.
- **Comprehension:** +0.3 / +0.6 / +1.5 to the killer's chosen Dao (`Comprehension.grant`).
- **Drops:**
  - Always 1 beast core of grade = tier and the elite's element (`BeastCores.drop`).
  - Tier 2+: 25% chance of a herb (`SpiritHerbs.randomFor(element, …)`, age 50–400).
  - Tier 3: 1–3 Low spirit stones and a 20% ancient technique scroll (`RelicLoot.randomScroll`).

**Dao Protector scaling.**
- From target Nascent Soul, each protector wave includes 1 elite.
  - Tier 1 for targets NS and DT, tier 2 for DU and IA, tier 3 for TI.
  - Realm = target − 2.
- Protector health ×`(1 + 0.3 × max(0, targetRealm − 3))`.
- Elite protectors keep the protector rules (half XP, vanish at the end) but do drop their core.

**Client.** `EliteFx` draws an aura of element-coloured motes on elites within 32 blocks (tier 1: 4 per second, tier 2: 8, tier 3: 16, × quality; at most 12 elites per frame). `FxType.ELITE` (a = skill id, b = entity id, f = radius) draws the telegraph rings.

**Safety and performance.** No block is touched, and everything listed under Hardening stays in force. Cost: one random roll per natural spawn near a cultivator, bounded AABB counts after a success, and the elite skill tick every 10 ticks over a tracked UUID set. The set is rebuilt on `ENTITY_LOAD` and cleaned on death or unload.

#### 19.2.2 Batu Roh: spirit stones (new feature, P6-harta)

**Items.**

| Item id | Indonesian / English |
|---|---|
| `spirit_stone_low` | Batu Roh Rendah / Low Spirit Stone |
| `spirit_stone_mid` | Batu Roh Menengah / Mid Spirit Stone |
| `spirit_stone_high` | Batu Roh Tinggi / High Spirit Stone |
| `spirit_stone_supreme` | Batu Roh Agung / Supreme Spirit Stone |

Shapeless recipes turn 9 of one grade into 1 of the next, and 1 back into 9 (six JSON files).

**Sources.**

1. **Mining.** Own `PlayerBlockBreakEvents.AFTER` listener; Qi Refining+, survival, not a FakePlayer, no Silk Touch, config `spiritStoneMining`.
   - Chance = `min(0.5, 0.015 × XpSources.miningXp(state)) × clamp(SpiritVeins.density(level, pos), 0.5, 2.0)`.
   - Examples: coal 1.5%, iron 3%, gold 4.5%, diamond 15% (deepslate 22.5%), emerald 18%.
   - Drops 1 Low (diamond and emerald 1–2) via `popResource`. Fortune does nothing. Ancient debris gives nothing.
2. **Structure loot.** See 19.2.14.
3. **Wandering trader.** Data only.
   - `data/kultivator/villager_trade/spirit_stones.json`: 2 emeralds → 3 Low, max 4 uses.
   - Added to `data/minecraft/tags/villager_trade/wandering_trader/uncommon.json` with `"replace": false`.
   - Format: `VillagerTrade.CODEC` (`wants`, `gives`, …); verify against the vanilla JSON in the 26.3 jar.
4. **Padatkan Qi / Condense Qi.** Action `CONDENSE_QI`, a Status-page talisman, or `/kultivasi qi padatkan <n>`.
   - Requires meditating with a full bar at a bottleneck (Stored Qi or Qi Abadi > 0).
   - 1 Low costs `max(100, 5% of the current rank's xpRequired)` Stored Qi; up to 9 per press.
   - 2 s crystal effect (`FxType.TREASURE`). Stones go to the inventory, or drop at the feet.

**Uses.**

1. **Meditation fuel.** Stones in the offhand while meditating. Applied by P6-kultivasi in `Meditation`.

   | Grade | One consumed every | XP factor |
   |---|---|---|
   | Low | 30 s | ×1.5 |
   | Mid | 150 s | ×2.0 |
   | High | 300 s | ×2.5 |
   | Supreme | 600 s | ×3.0 |

   - While fuelled, vein drain is ×0.5.
   - Fuel counts inside the site × formation × fuel cap of ×5.
   - **Fuel is inactive and nothing is consumed while the bar is full at a bottleneck** (including True Immortal Peak). This makes a Stored Qi → stones → Stored Qi loop impossible. The HUD shows "Batu roh diam".
2. **Instant qi.** Sneak-use when not meditating, no toxicity, 1 s cooldown:
   - Low +30 qi, Mid +200, High +900.
   - Supreme: full qi + qi regen ×3 for 60 s.
3. **Protective formation fuel**, next to amethyst shards (10 min each, cap 2 h as before).
   - Low +20 min, Mid +3 h, High +24 h, Supreme sets the formation to the stone cap.
   - Stone power may reach a cap of 48 h (`MAX_POWER_STONES = 3,456,000` ticks).
4. **Alchemy catalyst.** See 19.2.9.
5. **Currency** for talismans (19.2.8), the guide and daily rewards (19.2.3), and trading between LAN friends.

**Advancements:** `first_spirit_stone`, `supreme_spirit_stone`, `qi_condensed`.

**Safety.** Drops only after the player broke the ore. No block is changed.

#### 19.2.3 Jalan Kultivasi tablet: Langkah Pertama, Ujian Fondasi, Tugas Harian (new feature, P6-jalan)

**Why.** A player who never reads PANDUAN.md has no idea what to do after the root test. The mid game has no direction beyond "fill the bar", and short sessions between Valorant matches need goals.

The **"Jalan / Path"** tablet has three sections, switched with jade coins: **Langkah · Ujian · Harian**.

**(a) Langkah Pertama / First Steps.** 16 steps in 4 chapters, stored as a bitmask in `extra.goals.guide`.

- Steps complete when their advancement is granted (`Advancements.addListener`).
- **Backfill on join:** each step's advancement is checked with `getOrStartProgress(holder).isDone()`, so veterans are never told to "test your root".
- Each step shows a "how" line using live key names (`KeyMapping.getTranslatedKeyMessage()`).

| Chapter | Steps (advancement) |
|---|---|
| 1 Manusia Fana | test your root (`root_tested`) · meditate (`first_meditation`) · fill the bar at Mortal (bar full, shows "Bar 34/60") · awaken (`awakening`) |
| 2 Pemurnian Qi | cast Qi Palm (`first_technique`) · take a pill (`first_pill`) · sit at a spiritual site or build the Small Qi Formation (`spiritual_site` or `qi_formation_small`) · break through to Foundation (`first_breakthrough`) |
| 3 Fondasi | fly on your sword (`flying_sword`) · refine a pill (`first_alchemy`) · find a spirit stone (`first_spirit_stone`) · survive the Golden Core tribulation (`first_tribulation`) |
| 4 Inti Emas | choose a Dao (`dao_chosen`) · comprehend a book or scroll (`first_book` or `first_ancient_scroll`) · slay a demon beast (`first_elite`) · awaken a spirit beast or build a protective formation (`spirit_beast` or `formation_built`) |

- **Chapter rewards** (config `guideRewards`, into the inventory or dropped at the feet):

  | Chapter | Reward |
  |---|---|
  | 1 | 2 Qi Gathering Pills (Mid) |
  | 2 | 1 Healing + 1 Qi Recovery Pill |
  | 3 | 1 Tribulation Warding Pill + 4 Low stones |
  | 4 | 1 Mid stone + an enchanted book mapped to the chosen Dao (DESIGN 7) |

- **GuideTracker HUD:** a jade slip at the top left, about 120 GUI px wide, scaled down on small screens, avoiding the boss bar and effect icons.
  - Shows the chapter seal, the step, 1–2 "how" lines and progress.
  - Shrinks to a 12 px seal after 20 s without change; expands for 8 s on a change or when the info key is pressed.
  - Plays an amethyst chime and a gold flash when a step completes.
  - The **"Sembunyikan panduan"** coin sends `GUIDE_HIDE`. The tracker hides itself after chapter 4.

**(b) Ujian Fondasi / Foundation Trials.** Three optional trials for every major breakthrough from Golden Core up.

- A set opens when the player enters the realm or layer below its target. Progress is a snapshot of counters taken at that moment; "ever" items use lifetime flags.
- Counters come from `extra.combat` (P6-pertempuran) and the module's own `extra.goals.c`:
  - Meditation seconds by site, formation and dimension, read every 20 ticks while meditating.
  - Spiritual-site types seen (`SpiritSites.current(player).sites()`).
  - Dimensions visited.
  - Ores mined.

| Target | Trial 1 | Trial 2 | Trial 3 |
|---|---|---|---|
| Golden Core | 20 technique kills | 20 min meditating at a site ×1.5+ | set foot in the Nether |
| Nascent Soul | 3 elites | refine 3 pills | comprehension 10 |
| Deity Transformation | ever killed a Wither or Elder Guardian | 10 min in a Great+ qi formation | awakened spirit beast or 1 heart demon defeated |
| Dao Union | comprehension 45 | 3 tier-2+ elites | 10 min meditating in the End |
| IA L1 | ever killed the Ender Dragon | 5 tier-2+ elites | 15 min at a site ×2.0+ |
| IA L2 | comprehension 60 | 30 min in a Heavenly formation | 1 Raja Siluman |
| IA L3 | ever killed a Warden | 3 enlightenments | refine 5 pills |
| IA L4 | `nether_tribulation` or `end_tribulation` | 10 tier-2+ elites | 20 min in a Great Spirit Vein |
| IA L5 | 2 boss insights | comprehension 75 | 2 Raja Siluman |
| IA L6 | 5 enlightenments | 30 min meditating in the End | 15 tier-2+ elites |
| IA L7 | comprehension 90 | 3 Raja Siluman | refine 8 pills |
| IA L8 | 4 boss insights | 45 min in a Heavenly formation | 20 tier-2+ elites |
| IA L9 | 6 spiritual-site types seen | 8 enlightenments | 5 Raja Siluman |
| True Immortal | 6 site types seen | 4 boss insights | comprehension 100 |

- **Rewards** (registered by Goals as Breakthrough/Tribulation providers for exactly that target):
  - +0.07 quality score per completed trial.
  - All three: a further +0.05, plus strikes ×0.9 (**"Fondasi Kokoh"**).
- Nothing is mandatory and there is no penalty. Advancement `foundation_trials` for the first full set.

**(c) Tugas Harian / Daily Tasks** (config `dailyTasks`).

- **Refresh:** 3 tasks per in-game day (overworld `gameTime / 24000`). A refresh also needs at least 18,000 ticks played since the last one, so sleeping through nights cannot farm them.
- **Pool.** Tasks whose feature is unavailable are never rolled.
  - Kill `8 + 2 × realm` monsters.
  - Slay 1 elite (Qi Refining+).
  - Meditate 8 min without a break.
  - Meditate 5 min at a site ×2+.
  - Kill 5 monsters with techniques.
  - Refine 2 pills (after `first_alchemy`).
  - Mine 6 gold, diamond or emerald ores without Silk Touch.
  - Harvest 2 spirit herbs (`extra.harta.herbs_found`).
  - Kill a monster during a thunderstorm (`extra.combat.thunder_kills`).
- **Rewards:**
  - Each task: 4% of the current bar via `Progression.grantXp(…, XpSource.TRIAL)`.
  - All three: 1 Low stone.
  - Streak: +10% per consecutive day, up to +50%. A missed day resets it.
- 3 small dots beside the realm name on the HUD. Advancement `daily_streak_5`.

**Safety and performance.** Pure player data. Checks run every 100 ticks, plus one kill listener and one block-break listener. No world changes.

#### 19.2.4 Roda Segel and Segel Cepat: seal wheel and quick seals (UX, P6-jalan)

- **Opening the wheel.** Hold the cast key (G) for 180 ms or more to open **"Roda Segel"** around the crosshair.
  - Radius 46 GUI px, up to 12 seals from `Techniques.castableTechniques(data)`.
  - Each seal: glyph, element-coloured frame, scaled qi cost (`Techniques.qiCost`), radial cooldown sweep, greyed when unaffordable.
  - The centre shows the hovered name, cost, cooldown and one description line.
- **Input while open.** `MouseHandlerMixin` cancels `turnPlayer` and feeds the mouse deltas to the selector; WASD keeps working. The mouse wheel steps around the ring (an `onScroll` HEAD cancel). Verify the field names in mcsrc.
- **Release.** Sends the existing `SELECT_TECHNIQUE` (text = id). With the setting **"Lepas = rapal"** (default off) it sends the existing `CAST` with text = id instead.
  - A tap under 180 ms casts on release as today.
  - **"Tahan G untuk roda"** (default on) can be switched off for zero cast delay.
  - A separate **"Roda Segel"** key is unbound by default.
- **Segel Cepat 1–4.** Four new keys, unbound by default.
  - While the wheel is open, hovering a seal and pressing 1–4 pins it to that slot (saved in `kultivator-client.json`). The key then sends `CAST` with text = id.
  - When any slot is bound, the HUD shows four 10 px tiles with their own cooldown sweeps.
- **Carousel.** `,` and `.` show a 1.2 s carousel: the previous and next seals as 60%-alpha ghost tiles.

**Safety and performance.** Client-only. The server still runs every check in `tryCast` (no new server action is needed).

#### 19.2.5 Urat Roh and Kultivasi Tertutup: living spirit veins, meditation rework, cave abode (refinement, P6-kultivasi)

**Why.** Every region is the same, so where you meditate barely matters. Flat meditation XP is meaningless from Deity Transformation up. Formations become a net loss on drained veins at high realms.

**Vein grades.** A seeded hash of `(level.getSeed(), dimension id, regionX, regionZ)` gives u in [0,1):

| u range | Grade | Indonesian / English | Cap |
|---|---|---|---|
| < 0.02 | 2 | Urat Roh Agung / Great Spirit Vein | 2.0 |
| < 0.10 | 1 | Urat Roh Kaya / Rich Spirit Vein | 1.4 |
| < 0.18 | −1 | Urat Roh Tipis / Thin Spirit Vein | 0.6 |
| otherwise | 0 | normal | 1.0 |

- Untouched regions sit at their cap and nothing is stored.
- Regen: `0.0004 × cap` per second toward the cap (empty → full in about 42 min).
- Saved entries are clamped to [0, cap]. Old saves stay valid.
- New public API: `SpiritVeins.grade(level, pos)` and `cap(level, pos)`. `density()` now returns 0…2.0.

**Meditation formula** (in `Meditation.second`):

```
base   = max(0.6 + 0.25 × realm, Rank(realm, layer, 0).xpRequired() / 3000)
xp/s   = base × min(5.0, site × formation × fuel) × max(0.15, density)
         × Sects.meditationBonus × (1 + 0.05 × seclusionStacks)
drain/s = 0.0015 × (1 + 0.10 × realm) × fuel 0.5 × formation (Great 0.6, Heavenly 0.35) × abode 0.5
```

- **Base rates:** unchanged up to Nascent Soul (1.6/s); Deity Transformation 4.0, Dao Union 10, IA L1 20, IA L9 68, True Immortal 83.3.
- **Pace:** a plain Early stage takes about 50 min; fully optimised, about 5–10 min.
- **Formation floor:** while a Great (Heavenly) qi formation is active at the seat, drain never takes density below 0.30 (0.45).
- **Enlightenment:**
  - Burst 25% → **20%** of the bar.
  - Chance per minute = `(0.04 × site + 0.02 with a Dao) × 1.5 after 30 min of seclusion × Enlightenment.boostChance factor + 0.02 (IA perk L8)`, capped at 0.5.
- `Meditation.percent` shows values above 100% ("Kepadatan Qi 180% · Urat Roh Agung").

**Kompas Urat Roh / Spirit Vein Compass.**
- Shapeless recipe: compass + amethyst shard + gold ingot + glow berries.
- Use (5 qi, 60 s cooldown): spiral search over hash values only (no chunk loading), up to 32 regions (about 2,048 blocks), for the nearest Great vein, else the nearest Rich.
- Writes `minecraft:lodestone_tracker {target: region centre, tracked: false}`. The item definition copies the vanilla compass `range_dispatch` with target `lodestone`, so the needle points with zero client code.
- Tooltip: grade and distance.

**Kultivasi Tertutup / Closed-door seclusion.**
- Unbroken meditation earns +1 stack every 300 s, up to 5 (+25% XP). After 1,800 s, enlightenment chance ×1.5.
- When meditation ends after at least 60 s, a summary line: "Meditasi 32 menit: +4.210 kultivasi · 2 pencerahan · kepadatan 180% → 95%" (or "+… Qi Tertimbun").
- Advancement `seclusion_hour` (60 min unbroken).

**Gua Kediaman / Cave Abode.**
- `/kultivasi gua tetapkan` while meditating on a seat with qi formation tier 1+ inside an active protective formation (`ProtectiveFormations.insideActiveFormation`).
- One per player, saved in `extra.kultivasi.abode`. `gua lihat` shows it; `gua hapus` removes it.
- Within 6 blocks of the seat:
  - vein drain ×0.5
  - enlightenment chance +0.01
  - seclusion stacks survive a break of 10 s or less
  - the Return Talisman's target (19.2.8)
- Advancements `cave_abode`, `great_vein`.

**Stone fuel.** `SpiritStones.isStone/grade` on the offhand stack, consumed at the start of each period, inactive at a full bottleneck (19.2.2).

**HUD data** in `sessionExtra`: `vein_grade`, `seclusion`, `fuel`, `fuel_end`, `fuel_idle`, `abode`.

**Safety and performance.** Data only, O(1) per second; the compass does at most about 4,000 hash evaluations per use.

#### 19.2.6 Inti Binatang: beast cores (new feature, uses in P6-harta, drops from P6-pertempuran)

**Items.**
- `beast_core_low / _mid / _high / _king`: **Inti Binatang Rendah / Menengah / Tinggi / Raja**.
- Component `kultivator:core_element` (Element id). Display name, for example "Inti Binatang Api · Menengah" / "Fire Beast Core · Mid".
- Tint: the item definition uses `"tints":[{"type":"minecraft:custom_model_data","index":0,"default":…}]` and the stack carries `minecraft:custom_model_data {colors:[element colour]}`. One texture per grade.

**Drops** (decided by P6-pertempuran):
- Elites: always, grade = tier.
- Bosses (Wither, Warden, Ender Dragon, Elder Guardian): 1 Raja core of a random element, only for the first kill of that boss type per player per in-game day.
- Normal `Enemy` kills by Qi Refining+ players: 1.5% × Karma factor for a Low core of the mob's element.
- Never from protectors (except elite protectors), spawner mobs, heart demons, or mobs without an element.

**Uses (P6-harta).**

1. **Absorb (sneak-use).** XP via `Progression.grantXp(…, XpSource.PILL) × PillToxicity.effectiveness`:

   | Grade | XP | Toxin | Heart demon pressure | Comprehension (element affine to chosen Dao) |
   |---|---|---|---|---|
   | Low | min(3% of bar, 150) | 6 | +4 | +0.5 |
   | Mid | min(6%, 900) | 10 | +6 | +1 |
   | High | min(10%, 5,000) | 16 | +10 | +2 |
   | Raja | min(15%, 25,000) | 28 | +20 | +4 |

   - +50% XP when the element is in the player's root.
   - Element → Dao affinity per DESIGN 6 (Metal→Sword, Fire→Fire, Water/Ice→Ice, Lightning→Lightning, Earth→Body, Wood→Life, Wind→Shadow, Space→Space).
2. **Feed your spirit beast** (own `UseEntityCallback`, calls the existing `SpiritBeasts.addXp`).
   - +60 / 150 / 400 / 1,000 beast XP; ×1.5 for a matching element (wolf: Lightning/Metal, fox: Fire/Wood).
   - Own 10 s cooldown per beast, separate from the 30 s pill cooldown.
3. **Alchemy catalyst or required core.** See 19.2.9.
4. **Talisman catalyst.** See 19.2.8.

**Advancements:** `first_beast_core`, `king_core`.

#### 19.2.7 Tanaman Roh: spirit herbs with age (new feature, P6-harta)

Own `PlayerBlockBreakEvents.AFTER` listener; Qi Refining+, survival, not a FakePlayer, config `herbHarvest`.

**Infusion test.** A plant is "spirit-infused" when `hash(seed, pos, herbKind, overworldDay) < p × mods`.
- Deterministic per position and day, so a bone-meal farm gets one roll per spot per day. Divine Sense can show the same result.
- mods: Wood root or Life Dao ×2; × `min(2.0, SpiritSites.current(player).multiplier())`. The site value is only read when the hash is below `4p`, so it is rare.

| Item | Indonesian / English | Plant and condition | p |
|---|---|---|---|
| `spirit_grass` | Rumput Roh / Spirit Grass | short/tall grass, fern, large fern, Overworld | 1.5% |
| `blood_ginseng` | Ginseng Darah / Blood Ginseng | mature carrot/potato/beetroot; ×2 in jungle, taiga, old growth or cherry | 1% |
| `snow_lotus` | Teratai Salju / Snow Lotus | any small flower or age-3 sweet berry bush where `Biome#coldEnoughToSnow` | 4% |
| `hellfire_lotus` | Teratai Api Neraka / Hellfire Lotus | mature nether wart, crimson/warped fungus or roots, Nether | 3% |
| `moonlight_orchid` | Anggrek Cahaya Bulan / Moonlight Orchid | any small flower at night with sky access; full moon ×3 | 2% |
| `thunder_grass` | Rumput Petir / Thunder Grass | any grass or flower during a thunderstorm with sky access | 5% |
| `void_lily` | Lili Kehampaan / Void Lily | chorus flower or plant, End | 4% |
| `purple_lingzhi` | Lingzhi Ungu / Purple Lingzhi | red/brown mushroom in mushroom fields, the Deep Dark, or below y 0 | 3% |

**Harvest.**
- The herb pops beside the plant (`popResource`) with component `kultivator:herb_age` (years).
- Age = `round((10 + r × 90) × clamp(density, 0.5, 2.0) × min(2.0, site))`, with r from the same hash. A 1% chance multiplies it ×10.
- Tooltip: "Umur: 340 tahun". At 1,000 years or more the name is gold ("… Seribu Tahun") and a single server-wide line is shown.
- Each find drains 0.02 vein density and counts `extra.harta.herbs_found`.
- Herbs are **never plantable** (no custom crop blocks).

**Uses.** Auxiliary herb and new recipes, see 19.2.9.

**Divine Sense refinement.** Infused plants within the existing radius 12 are pinged in green (`0x6CFF8A`) through the existing `OrePings` payload. It already carries a colour per position, so no payload change is needed.

**Advancements:** `first_herb`, `thousand_year_herb`.

#### 19.2.8 Jimat: talismans (new feature, P6-kultivasi)

**Blank.** **Kertas Jimat Kosong / Blank Talisman**: shapeless 3 paper + ink sac + gold nugget → 3.

**Inscribing.**
- Blank in the main hand, catalyst in the offhand, sneak-use: a 40-tick channel (brush effect); needs Qi Refining+.
- At the end it costs the qi below and 1 catalyst.
- Result: a typed talisman with component `kultivator:talisman {power, inscriber}`, where power = the inscriber's realm ordinal (1–8).
- A **beast core of the matching element** may replace the catalyst: power +1 (+2 for High or Raja), cap 9.
- **Talisman skill** (`extra.talisman.xp`):
  - +4 XP per success, +1 per failure.
  - Level = `min(10, floor(sqrt(xp / 8)))`.
  - Success chance = `0.60 + 0.035 × level` (max 0.95).
  - A failure burns the blank and the catalyst into ash particles (items only).

**Activation.** Anyone can use a talisman, even a Mortal: a 10-tick use, consumed, 1 s shared cooldown. They stack to 16. Every offensive talisman goes through `Targeting` and `Techniques.hurt` (monsters only).

| Item | Indonesian / English | Catalyst | Inscribe qi | Effect |
|---|---|---|---|---|
| `thunder_talisman` | Jimat Petir / Thunder | copper ingot (Lightning core) | 20 | Particle bolt on the looked-at monster within 24: `4 + 2 × power`; chains to 1 more within 5 at 50% |
| `holy_flame_talisman` | Jimat Api Suci / Holy Flame | blaze powder (Fire core) | 20 | 6-block 60° cone: entity ignite 5 s + `3 + 1.5 × power` |
| `golden_shield_talisman` | Jimat Perisai Emas / Golden Shield | gold ingot (Earth core) | 25 | Absorption amplifier `min(4, power / 2)` for 60 s |
| `sealing_talisman` | Jimat Penyegel / Sealing | chain (Ice core) | 20 | Looked-at monster within 16: Slowness VII + Weakness I for `3 + 0.5 × power` s (bosses ×0.25) |
| `return_talisman` | Jimat Pulang / Return | ender pearl (consumed as ink, never thrown) | 40 | 5 s channel (cancelled by damage or moving more than 1 block). Target, in order: your Cave Abode in this dimension → your respawn point (`ServerPlayer.RespawnConfig`) in this dimension → world spawn if in the Overworld → otherwise refused. Uses `Targeting.safeSpot` + `setPortalCooldown`; refused without a safe spot. 10 min cooldown. Refused during a breakthrough, trial, duel, or within 10 s of being hurt. |
| `tribulation_ward_talisman` | Jimat Penangkal Petir / Tribulation Ward | lightning rod item | 35 | Only during your own tribulation strikes: next strike ×0.5 (a `StrikeFactor` provider). At most 2 per tribulation; the count resets in `onOmen`. |
| `purifying_talisman` | Jimat Pembersih / Purifying | milk bucket (bucket returned) | 15 | Clears Poison, Wither, Nausea, Blindness, Darkness, Slowness, Weakness; toxicity −`(5 + 1.5 × power)` |
| `heavens_eye_talisman` | Jimat Mata Langit / Heaven's Eye | glow ink sac | 15 | Glowing on monsters within `16 + 2 × power` for 10 s |

- Balance: a Thunder Talisman at Golden Core power (10 damage, 1 chain) stays below Thunder Palm.
- Client: `FxType.TALISMAN` (a = type) draws the paper burning into glyphs.
- **Advancements:** `first_talisman`, `talisman_master` (level 10).

**Safety.** Items, particles and entity effects only. The Return talisman never uses or creates a portal and stays in the same dimension.

#### 19.2.9 Alkimia Lanjutan and Kitab Alkimia: deeper alchemy and a recipe tablet (refinement, P6-harta)

**Pot rules.**
- `AlchemyPots.CAPACITY` 8 → 10. Stones, herbs and cores are accepted ingredients.
- Matching:
  1. Remove up to 2 spirit stones (catalysts).
  2. Remove 1 beast core (catalyst), unless the recipe requires a core.
  3. The rest must equal a recipe's ingredients **plus at most one extra herb** (the auxiliary herb, the oldest one not needed by the recipe).
- Effects:

  | Extra in the pot | Effect |
  |---|---|
  | Each Low stone | success +0.05 |
  | Each Mid+ stone | success +0.10, quality roll +0.1 |
  | Auxiliary herb | quality roll + `min(0.30, age / 3333)` (100 years +0.03, 1,000 years +0.30) |
  | Catalyst core Low / Mid / High / Raja | success +0.05 / +0.08 / +0.12 / +0.18; quality floor – / Mid / High / Perfect |

  Success is still capped by `MAX_CHANCE` (0.95).
- Alchemy-refined Marrow Cleansing Pills now carry `pill_quality` (needed for 19.2.15). Crafting-table marrow pills count as Mid.

**New pills** (`PillKind` + items):

| Item id | Indonesian / English | Recipe | Effect | Toxin |
|---|---|---|---|---|
| `dao_insight_pill` | Pil Wawasan Dao / Dao Insight Pill | Moonlight Orchid + Purple Lingzhi + Echo Shard → 1 | +3 × quality comprehension to the chosen Dao, never above 60. At most 5 per realm (`extra.harta.insight_pills`). Refused (pill kept) without a Dao. | 18 |
| `toxin_purging_pill` | Pil Penawar Racun / Toxin Purging Pill | Snow Lotus + Honey Bottle + Spirit Grass → 2 | Toxicity −35 × quality | 0 |
| `enlightenment_pill` | Pil Pencerahan / Enlightenment Pill | Moonlight Orchid + Glow Berries + Amethyst Shard → 2 | `Enlightenment.boostChance(player, 2.0, 12000)` (×2 chance for 10 min) | 10 |
| `bone_tempering_pill` | Pil Penempa Tulang / Bone Tempering Pill | Bone Block + Iron Ingot + Golden Carrot + **Earth core Mid+** (required) → 2 | +1 max health permanently (Perfect +2), cap +10 total, modifier `kultivator:bone_tempering`, re-applied on join and respawn by Harta | 12 |

**Alternative recipes** (same outputs, so every herb has a use):
- Ginseng Darah + Golden Carrot + Glistering Melon → 3 Healing.
- Teratai Api Neraka + Gold Ingot + Golden Apple → 2 Golden Core.
- Rumput Petir + Copper Ingot + Phantom Membrane + Iron Ingot → 2 Tribulation Warding.
- Lili Kehampaan + Ghast Tear + Echo Shard + Chorus Fruit → 3 Soul Nurturing.

**Kitab Alkimia tablet** (`AlchemyBookPage`):
- A card per `AlchemyRecipes.all()` entry: output icon × count, ingredient icons × n, colour band, alchemy XP.
- A check mark and "Bisa diracik 2×" when the inventory holds all the ingredients.
- Header: alchemy level, XP to the next level, success chance with campfire / soul campfire / Fire Dao (`AlchemyFurnace.successChance`).
- Footer: what catalysts and auxiliary herbs do.

**Tooltips** (`PillTooltips`): ingredient items show "[Shift] Bahan alkimia"; holding Shift lists the pills they make.

#### 19.2.10 Tribulasi Hidup: lightning rings and Pengawal Dao (refinement, P6-pertempuran)

**Lingkar Petir / telegraphed strikes.** From target Deity Transformation up; config `tribulationTelegraph`.

- **Telegraph:** 30 ticks before each strike, the ring centre is captured at the player's feet. `FxType.TRIBULATION_RING` draws a glowing sigil ring of radius 1.5 (a = variant, b = strike number).
- **Impact:** if the player is horizontally more than 1.5 blocks from the centre, the strike only **grazes** for 40% damage. Otherwise it hits in full. The would-kill check uses the final damage, so never-lethal stays intact.
- **Twin strikes (target IA and TI):** every 4th strike has twin rings, the second 3 blocks away in a random direction. Standing in either ring takes the full hit.
- **"Menantang Langit" (Defy the Heavens):** a quality provider.
  - +0.12 when every telegraphed strike was taken in full.
  - Otherwise −0.02 per graze (floor −0.10).
  - Dodging keeps `lowestHealth` higher, so the player makes a real choice.
  - Advancement `defy_heaven`.
- The 6-block cancel radius is unchanged; a 2-block sidestep is enough.

**Pengawal Dao / Dao Guardian** (co-op; distinct from the monster "Pelindung Dao" waves).

- **Joining:**
  - When the omen starts (target Golden Core+, not a debug test), online sect mates in the same dimension get a chat line with the target realm, distance and direction, and a clickable **[Kawal]** that runs `/kultivasi kawal <player>` (registers from up to 64 blocks).
  - Any player within 24 blocks when strike 1 lands is registered automatically.
- **Losing the role:** more than 40 blocks away for 10 s, dying, or starting your own breakthrough.
- **During strikes:**
  - Each guardian within 16 blocks multiplies strike damage by 0.95; all guardians together floor at 0.85. This is a `StrikeFactor` provider.
  - Quality +0.04 per guardian present at the end (max +0.08).
  - Protector waves get +1 mob per guardian (max +3), and each new protector picks its first target at random among the cultivator and the guardians.
  - The cultivator's HUD shows "Dikawal: 2". Gold qi threads are drawn by `FxType.DAO_GUARDIAN`, once per strike.
- **Rewards on success:**
  - Each guardian gets 10% of their own bar (`XpSource.SECT`; Stored Qi if bottlenecked) and +0.5 comprehension of their Dao (×3 for the Lightning Dao).
  - Failure: half of that.
  - Once per guardian–cultivator pair per 20 min (`extra.combat.guard_last`).
  - Advancements `dao_guardian`, and `great_guardian` after 5 guards.
- Strikes still hit only the cultivator.

**Session sync for the HUD** (`sessionExtra.trib`): `phase` (0 none, 1 omen, 2 strikes, 3 settling), `done`, `total`, `next` (game time), `pct` (the next strike's effective share, last ×2 included), `cx`, `cz`, `rx`, `rz`, `rend`, `tx`, `tz` (twin ring), `guards`. Marked dirty at the omen, at each telegraph and at each strike, so about one sync every 1.5–3.5 s.

#### 19.2.11 Prakiraan Terobosan and HUD Tribulasi: breakthrough forecast and live tribulation HUD (UX, P6-jalan)

**Persiapan panel.** On the Status page (fold-out) and in the breakthrough talisman's tooltip. It only uses the real helpers, never copies of the formulas:

- **Foundation step:** `Breakthrough.chance(data, site)` with a breakdown (base 75% + root + pill + stored qi), plus a tip about sites.
- **Tribulation steps:**
  - Strikes from `Tribulation.strikeCount`.
  - Per-strike damage `strikePct × nextTribulationFactor × max health` in hearts, last ×2, plus the total and the healing needed.
  - Golden Core: 3 × 15%, last 30% → 60%, no healing needed.
  - Dao Union: 9 × 18%, last 36% → 180%, "heal about one full bar during it".
  - The trial bonus from 19.2.3 is included.
  - Whether a heart demon comes first (`cfg.hd`, target NS/DU/TI, trial not passed): 120 s, 1.2× your HP.
  - Protector waves (`cfg.dp`; strikes/3 waves of `1 + target/2` mobs).
  - Ring telegraphs from Deity Transformation.
  - The dimension variant (Nether: bring Fire Resistance).
- **Checklist:**
  - ✓ health ≥ 90%
  - ✓ healing items in the inventory (golden apples, enchanted golden apples, Healing Pills: counted)
  - ✓ Warding taken (factor < 1)
  - ✓ allies within 16 (visible players and tamed animals)
  - ✓ toxicity < 80
  - ✓ not deviating
  - Hardcore is shown in red.

**Tribulation HUD.** Below the boss bar, from `sessionExtra.trib`:

- Bolt pips "Petir 4/9" and a ring emptying toward the next strike.
- "Berikutnya −5.4 ❤":
  - white normally
  - yellow when the next strike is more than 50% of current health
  - flashing red with a heartbeat sound and "Petir berikutnya akan menggagalkan — pulihkan dirimu!" when `pct × max ≥ health + absorption`
- "Keluar dari lingkar!" while a ring is active.
- A small top-down distance ring against the 6-block limit (amber above 4.5, red above 5.5, with an arrow back to the centre).
- The guardian count.

#### 19.2.12 Lapisan Kenaikan Abadi and uses for Qi Tertimbun: layer perks, reforging, stars (new feature, P6-kultivasi)

**Layer perks.** A perk is active when realm = IA with layer ≥ n − 1, or realm = True Immortal. Attribute perks are added in `Stats.apply` with ids `kultivator:perk_*`.

| Layer | Perk (Indonesian / English) | Effect |
|---|---|---|
| L1 | Napas Abadi / Immortal Breath | OXYGEN_BONUS +3, BURNING_TIME −50% (ADD_MULTIPLIED_BASE −0.5) |
| L2 | Langkah Ringan / Light Step | STEP_HEIGHT +0.5, WATER_MOVEMENT_EFFICIENCY +0.5 |
| L3 | Kulit Giok / Jade Skin | ARMOR_TOUGHNESS +4, KNOCKBACK_RESISTANCE +0.25 |
| L4 | Lautan Qi / Qi Sea | max qi ×1.15 (`Qi.max`) |
| L5 | Tangan Langit / Heaven's Reach | ENTITY_INTERACTION_RANGE +1.5, BLOCK_INTERACTION_RANGE +1.0 |
| L6 | Arus Abadi / Eternal Flow | qi regen ×1.25 (`Qi.regenPerSecond`) |
| L7 | Jiwa Tak Hancur / Unbroken Soul | see below |
| L8 | Pikiran Jernih / Clear Mind | enlightenment +0.02 per minute; heal 0.5 HP/s while meditating |
| L9 | Gerbang Surgawi / Heavenly Gate | the True Immortal tribulation's strikes ×0.85 (`StrikeFactor`) |

- **L7 details:** `ServerLivingEntityEvents.ALLOW_DEATH`, once per 24,000 game ticks (`extra.kultivasi.unbroken_at`).
  - The player is left at 1 HP, gets Absorption (amplifier ≈ 50% of max health / 4) for 5 s and Resistance III for 3 s, and loses all current qi.
  - Golden shatter effect. Advancement `unbroken_soul`.
  - Not triggered by sources tagged `BYPASSES_INVULNERABILITY` (`/kill`, void).
- On reaching a new perk, `ImmortalPerks.tick` (every 20 ticks, compares `extra.kultivasi.perk_count`) shows a title and chat line "Anugerah Kenaikan: Napas Abadi — …" and calls `Stats.apply`.
- **Health curve:** layer coefficient 2 → **4**; True Immortal flat bonus 60 → **40**. IA L9 Peak: +63; True Immortal: +72–75.

**Tempa Ulang Fondasi / Reforge Foundation.** Action `REFORGE`, a Status-page talisman, or `/kultivasi fondasi tempa`.
- Requires meditating with qi formation tier 2+, not deviating and not breaking through.
- Target: the lowest recorded quality among reached realms (earliest realm on ties).
- 30 s ritual (`BREAKTHROUGH_START` effects); cancelled at no cost if meditation stops.
- At the end, pays `3 × Rank(realm, 0, 3).xpRequired()` from Stored Qi (Golden Core 9,225; Dao Union 184,500; IA 369,000).
- Success: Cracked→Normal 100%, Normal→Perfect 60%, Perfect→Heavenly 25%. A failure refunds half.
- Then `Stats.apply` + `Qi.clamp`. Advancement `foundation_reforged`.

**Refill after a failure** (`Deviation.tick`, when a deviation ends). If at a bottleneck with the bar not full and Stored Qi > 0, the bar refills by `min(missing, storedQi / 2)`, consuming 2 Stored Qi per XP. Message: "Qi Tertimbun mengalir kembali…".

**Bintang Abadi / Immortal Stars.** Ascended, True Immortal Peak. Action `STAR_CONDENSE` or `/kultivasi bintang padatkan`.
- Star n (1–18) costs `100,000 × 1.15^(n−1)` Qi Abadi (about 7.6 M in total).
- Each star: +1.5% technique damage (`Techniques.power` × `ImmortalPerks.starDamageFactor`), +2 max HP, +1% max qi.
- Shown orbiting the dantian on the Status page and as a count on the HUD.
- Advancements `immortal_star`, `eighteen_stars`.

#### 19.2.13 Lima Unsur: the five elements in combat (refinement, P6-pertempuran)

**Technique element.** The chosen Dao's element (Sword = Metal, Fire, Ice, Lightning, Body = Earth, Life = Wood, Shadow = Wind, Space). Without a Dao, the root's main element. Without a root, none.

**Target element.**
- An elite's element, or `MobElements.of(entity)`:
  - Earth: zombie, husk, zombie villager, slime, silverfish, ravager
  - Water: drowned, guardian
  - Metal: skeleton, pillager, vindicator, piglin brute
  - Ice: stray
  - Wood: bogged, spider, cave spider, witch, creaking
  - Fire: wither skeleton, blaze, magma cube, ghast, hoglin, zoglin
  - Lightning: creeper
  - Space: enderman, endermite, shulker
  - Wind: phantom, vex, evoker, breeze
- Bosses, players and unknown mobs are neutral (none).

**Families.** Water = {Water, Ice}; Metal = {Metal, Lightning}; Wood = {Wood, Wind}; Fire; Earth.
- Overcoming cycle: Wood > Earth > Water > Fire > Metal > Wood.
- Space is neutral and deals +5% to everything.

**Factor** (in `Techniques.hurt`, non-player targets only, so sparring and tribulations are unaffected):

| Relation | Factor |
|---|---|
| Your element overcomes the target's | ×1.3, with element sparks (`FxType.BURST` in your element colour, at most once per target per 10 ticks) |
| The target's element overcomes yours | ×0.8 |
| Same family | ×0.9 |
| Otherwise | ×1.0 |
| Technique element in your root (resonance) | additional ×1.1 |

**UI.** The Techniques page tooltip shows "Unggul melawan: monster Api, Logam" from `ElementRelations.strongAgainst`.

#### 19.2.14 Pusaka Kuno: ancient relics in structure chests and treasure sense (new feature, P6-harta)

`LootTableEvents.MODIFY`, built-in chest tables only (`source.isBuiltin()`), config `structureLoot`. One extra pool per table:

| Table (`minecraft:chests/…`) | Added |
|---|---|
| `simple_dungeon`, `abandoned_mineshaft` | 30%: 1–4 Low stones |
| `desert_pyramid`, `jungle_temple`, `stronghold_corridor`, `stronghold_crossing` | 2–6 Low + 15%: 1 Mid |
| `stronghold_library` | 2–6 Low + 15%: 1 Mid; **25% scroll: any of the 9 book techniques** |
| `igloo_chest` | 30% scroll Glacier Prison; 30% Snow Lotus (age 50–300) |
| `desert_pyramid` (extra) | 10% scroll Phoenix Flame or Mountain Stomp |
| `jungle_temple` (extra) | 15% scroll Life Drain or Gale |
| `woodland_mansion` | 15% scroll Buddha Palm |
| `underwater_ruin_big` | 8% scroll Glacier Prison |
| `nether_bridge` | 20% Hellfire Lotus (age 50–300) |
| `buried_treasure` | 10% Moonlight Orchid (age 500–1,500) |
| `ancient_city` | 1–3 Mid + 10% High; 12% scroll Space Prison, Gale or Life Drain; 15% High core (Space) |
| `bastion_treasure` | 1–3 Mid + 10% High; 20% scroll Phoenix Flame or Mountain Stomp; 5% Raja core (Fire) |
| `end_city_treasure` | 1–3 Mid + 10% High; 15% scroll Space Prison, Heavenly Thunder or Thousand Swords |
| `trial_chambers/reward_ominous_*` | 8% 1 High stone; 10% scroll Thousand Swords or Heavenly Thunder |

**Gulungan Teknik Kuno / Ancient Technique Scroll** (`ancient_technique_scroll`, component `kultivator:scroll_technique`).
- Sneak-use calls `TechniqueBooks.learnFromScroll(player, technique)`: the same realm and Dao rules as books, but a comprehension threshold of **20** instead of 30.
- If the technique is already known, or belongs to your own Dao but is not learnable yet: +6 comprehension, up to a ceiling of 60 (outside the daily book cap).
- Another Dao's scroll: "you cannot comprehend this", and the scroll is kept. Otherwise it is consumed.
- Advancement `first_ancient_scroll`.

**Divine Sense treasure sense.** Unlooted containers (block entities with a pending loot table) within 24 blocks, in loaded chunks only, are pinged in gold through `OrePings` (total cap `OrePings.MAX` = 256).

**Safety.** Loot tables only; no structure or block changes. The scan iterates block-entity maps of at most 2 chunks around the player.

#### 19.2.15 Keseimbangan: closing exploits and balancing (balance/fix, split between P6-pertempuran and P6-harta)

**Karma Darah (P6-pertempuran, `XpSources`).**
- Saturation S in `extra.combat.karma`, decaying 1 per 100 ticks (computed lazily).
- Each `Enemy` kill: +1 (slimes and magma cubes +0.3, elites +3).
- Kill XP and normal-core chance × `1 / (1 + max(0, S − 25) / 25)`.
  - Normal fighting (2–6 kills per minute) never passes 25.
  - A 30-kills-per-minute farm reaches ×0.12 within 10 min.
- An action-bar hint at most once per minute when the factor is below 0.6: "Karma darah menumpuk: kultivasi dari membunuh ×0.4".
- Mobs tagged `kultivator.spawner` give ×0.25 XP and no cores or herbs. Trial spawners are unaffected.
- Bosses: full XP once per boss type per player per in-game day, 25% after that.

**Wawasan Pertempuran / Battle Insight.** The first kill ever of each boss type (Wither, Warden, Ender Dragon, Elder Guardian):
- +40% of the current bar (`XpSource.TRIAL`) and +5 comprehension.
- Bit in `extra.combat.boss_ever`.
- Advancements `boss_insight` and `all_boss_insights`.

**Dao technique damage (P6-pertempuran, `DaoTechniques`).** Target 5–7 D per target; the Golden Core techniques are unchanged.

| Technique | Change |
|---|---|
| Heavenly Thunder | at most 3 bolts per target |
| Thousand Swords | 0.6 D → 0.4 D per sword |
| Phoenix Flame | 2 D → 3 D + entity ignite 8 s |
| Mountain Stomp | 2 D → 3.5 D + Slowness IV 3 s |
| Glacier Prison | 1.5 D → 2.5 D |
| Space Prison | 1.5 D → 2.5 D |
| Life Drain | 1 D → 2 D per target, heals 40% of the damage dealt |

**Qi cost scaling.**
- `Techniques.qiCost(technique, data) = technique.qiCost() × (1 + 0.08 × max(0, realm − minRealm))`, used by `tryCast`, the HUD and the Techniques page.
- Examples: Sword Qi at True Immortal costs 28; Qi Palm costs 15.6.
- Flying-sword drain is unchanged.

**Enchanted books (P6-harta, `TechniqueBooks`).**
- Insight = `1 + enchantment level` (Sharpness I = 2, V = 6), only up to comprehension 30.
- At 30 or above, the book gives the 30-XP plain reward instead.
- At most 4 insight books and 6 plain-XP books per in-game day (`extra.harta.book_*`). Over the limit, the book is kept: "Kata-katanya kabur; pikiranmu sudah penuh hari ini."
- Learning techniques is never limited.

**Toxicity (P6-harta, `PillToxicity`).**
- Effectiveness = `clamp(1 − (tox / 100)^1.6, 0, 1)` (25 → 0.89, 50 → 0.67, 80 → 0.30, 100 → 0).
- Above 90: each pill has a 25% chance of **"Pil Membalik"** = `Deviation.apply(player, 0.3)` (never lethal; ascended players are immune).
- Decay: 1 per 30 s, or 1 per 10 s while meditating.

**Immortal Pill.** XP = 1 bar × pill quality only (`XpSource.EXACT` × 0.8–1.6) instead of also × root × breakthrough quality (up to about 3.5 bars today).

**Marrow Cleansing Pill (P6-harta).**

| Step | Chance | Requirement |
|---|---|---|
| Trash → Low | 100% | – |
| Low → Mid | 85% | – |
| Mid → High | 55% | Foundation+ |
| High → Heavenly | 30% | Golden Core+ and a pill of High or Perfect quality |

- Chance × 0.8 / 1.0 / 1.25 / 1.6 by pill quality, capped at 95%.
- Failure: the pill is used, toxin 15, Nausea, grey smoke, "Sumsum menolak".
- Toxin 20 for High and above.
- Requirement messages appear before the pill is consumed. Players who already have Heavenly keep it.

#### 19.2.16 HUD and comfort: timers, alerts, auto quality, formation blueprints (UX, P6-jalan)

**HUD additions (`QiHud`).**

| Feature | Detail |
|---|---|
| Flying-sword gauge | `qi / max(0.01, FlyingSword.drainPerSecond − Qi.regenPerSecond)` as "≈42 s"; amber below 15 s; red below 10 s with chimes at 10/5/3 s; "Turun!" below 2 s |
| Dharma Form / Dao Domain | gold countdown arcs from `sessionExtra.dharma_end` / `domain_end` |
| Enlightenment timer | "Pencerahan 0:07" |
| Cloud Step pips | 1–3 cloud pips while airborne (`CloudStep.maxJumps`, jumps counted on the client) |
| Cooldown ping | optional chime and seal flash when a cooldown of 10 s or more ends (setting "Pengingat cooldown") |
| Toxicity meter | shown from 40 instead of only above 80 |
| Vein line | density as % of normal + grade name; seclusion "Tertutup +15%"; stone icon + fuel timer (or "diam"); "+N Qi Tertimbun" kept |
| Stars | count when > 0 |
| Daily dots | the 3 daily-task dots |

New lines are droppable first on narrow screens.

**Meditation danger alert** (client, setting on by default).
- Every 10 ticks while meditating, checks a 12-block box for visible `Enemy` mobs, excluding the heart demon and the player's own pets.
- First sighting: a quiet bell, a red vignette on the screen edge facing the mob, and "Bahaya: Zombie 7 blok".
- Re-pings every 5 s while a mob is within 6 blocks.

**Effect quality "Otomatis".**
- Averages `minecraft.getFps()` over 5 s.
- Below 40 fps → Minimal; after 10 s above 70 fps → Normal. Never Maximal. Hysteresis prevents flicker.
- **"Efek pemain lain":** Penuh / Ringan (×0.25, no continuous aura) / Mati, applied to other players' auras in `AuraFx` through `EffectQuality.othersScale()`.

**Settings page.** Becomes scrollable to hold the new toggles (wheel, release-cast, cooldown ping, danger alert, guide, auto quality, others' effects). It still fits 320×240.

**Cetak Biru Formasi / Formation blueprints** (client, block reads only).
- **Trigger:**
  - Looking within 8 blocks at a Gold Block (qi seat) or a Lodestone (protective) while holding Amethyst Block, End Rod, Gold Block, Beacon, Lodestone or Crying Obsidian, or while sneaking.
  - The blueprint stays for 30 s after the last trigger.
- **Slots:** qi tiers from `QiFormations.cardinalOffsets()` / `cornerOffsets()` (end rods at y+1, beacon or lodestone at y−1); protective pillars from `FormationPattern.pillars()`.
- **Markers:**
  - green outline: correct block
  - pulsing gold cube: empty slot
  - red cross: wrong block
- **Label above the anchor:** "Formasi Pengumpul Qi Kecil 3/4 · ×1.25 → Agung 5/9", or for a protective formation "Jongkok + pakai Pecahan Kecubung untuk membangunkan".
- **Rendering:** `fabric-rendering-v1` world render event if its 26.3 API is verified; otherwise 1 dust particle per slot every 10 ticks (× quality).
- At most 17 `getBlockState` reads every 10 ticks, only while triggered.

### 19.3 Modules, file ownership and contracts

Four agents work in parallel after 19.1. **Never edit a file owned by another module.** Everything a module needs from another goes through the contracts in 19.3.4–19.3.6. A missing need goes into the final report as a TODO.

#### 19.3.1 P6-harta (items, economy, alchemy): items 2, 6 (uses), 7, 9, 14, 15 (books, toxicity, marrow, Immortal Pill)

**Owns (new):**
- `harta/*`:
  - `Harta` (init, commands, actions, join/respawn re-apply of bone tempering)
  - `SpiritStones`, `SpiritHerbs`, `BeastCores`, `RelicLoot`
  - item classes
  - `HartaComponents` (`herb_age`, `core_element`, `scroll_technique`)
- `client/harta/*`, `client/fx/HartaFx.java`, `client/screen/AlchemyBookPage.java`
- Its items' assets plus `tools/gen_harta_textures.py`
- Recipes for the stones
- `data/kultivator/villager_trade/*` and the wandering-trader tag file
- `lang-src/harta.*`
- Advancements: `first_spirit_stone`, `supreme_spirit_stone`, `qi_condensed`, `first_herb`, `thousand_year_herb`, `first_beast_core`, `king_core`, `first_ancient_scroll`

**May edit:** `alchemy/*`, `item/MarrowCleansing.java`, `item/MarrowCleansingPillItem.java`, `cultivation/PillToxicity.java`, `cultivation/VanillaPills.java`, `dao/TechniqueBooks.java`, `technique/DivineSense.java`, `formation/ProtectiveFormations.java`, `formation/Formation.java`, `client/PillTooltips.java`, `client/BookTooltips.java`, `client/fx/AlchemyFx.java`, `client/fx/OrePingFx.java`.

**Help topics:** `stones`, `herbs`, `cores`, `relics`.

#### 19.3.2 P6-pertempuran (combat and tribulation): items 1, 6 (drops), 10 (server), 13, 15 (karma, boss insight, Dao techniques, qi cost)

**Owns (new):**
- `elite/*`: `EliteMonsters`, `EliteData` attachment, `EliteSkills`, `MobElements`
- `data/ElementRelations.java`
- `mixin/MobFinalizeSpawnMixin.java` (body)
- `client/elite/*`, `client/fx/EliteFx.java`
- `lang-src/combat.*`
- Advancements: `first_elite`, `beast_king_slain`, `elite_hunter` (50 elites), `boss_insight`, `all_boss_insights`, `defy_heaven`, `dao_guardian`, `great_guardian`

**May edit:** `cultivation/XpSources.java`, `cultivation/DaoProtectors.java`, `cultivation/Tribulation.java`, `cultivation/Breakthrough.java`, `technique/Techniques.java`, `technique/DaoTechniques.java`, `technique/DaoTechniqueState.java`, `technique/CloudStep.java`, `technique/DharmaForm.java`, `technique/DaoDomain.java`, `client/fx/TribulationFx.java`.

**Commands:** `/kultivasi kawal <player>`; debug `elite summon <tier> [element]` (zombie or skeleton only, hardened) and `karma show|reset`.

**Help topics:** `elites`, `elements`, `karma`, `guardians`.

#### 19.3.3 P6-kultivasi (meditation, veins, perks, talismans): items 5, 8, 12

**Owns (new):**
- `cultivation/ImmortalPerks.java`, `cultivation/Reforge.java`, `cultivation/VeinCompassItem.java`
- `talisman/*`, `client/talisman/*`, `client/fx/TalismanFx.java`
- Its items' assets plus `tools/gen_kultivasi_textures.py`
- Recipes for the blank talisman and the compass
- `lang-src/kultivasi.*` and `lang-src/talismans.*`
- Advancements: `great_vein`, `cave_abode`, `seclusion_hour`, `foundation_reforged`, `unbroken_soul`, `immortal_star`, `eighteen_stars`, `first_talisman`, `talisman_master`

**May edit:** `cultivation/Meditation.java`, `cultivation/SpiritVeins.java`, `cultivation/SpiritSites.java`, `cultivation/Enlightenment.java`, `cultivation/QiFormations.java`, `cultivation/Stats.java`, `cultivation/Qi.java`, `cultivation/Deviation.java`, `client/fx/MeditationFx.java`.

**Commands:** `/kultivasi gua tetapkan|lihat|hapus`, `fondasi tempa`, `bintang padatkan`; debug `vein grade`, `perks show`, `stars set <n>`.

**Help topics:** `veins`, `talismans`, `immortal_perks`.

#### 19.3.4 P6-jalan (goals and client UX): items 3, 4, 11, 16 (+ the HUD side of 5, 10, 12)

**Owns (new):**
- `goals/*`: `Goals`, `Guide`, `RealmTrials`, `DailyTasks`, `GoalCounters`
- `client/goals/*`: `GoalsClient`, `GuideTracker`, `SealWheel`, `TribulationHud`, `MeditationAlert`, `FormationBlueprint`
- `client/mixin/MouseHandlerMixin.java` (body), `client/screen/PathPage.java`
- `lang-src/goals.*`
- Advancements: `guide_complete`, `foundation_trials`, `daily_streak_5`

**May edit:** `client/QiHud.java`, `client/Keys.java`, `client/EffectQuality.java`, `client/screen/CultivationScreen.java`, `StatusPage.java`, `TechniquesPage.java`, `SettingsPage.java`, `HelpPage.java`, `client/screen/ui/*`, `client/fx/AuraFx.java`, `lang-src/screen.*`, `lang-src/hud.*`.

- `HelpPage.TOPICS` gains the 13 new topics plus its own `path` and `seal_wheel`.
- Each module defines `kultivator.screen.help.<topic>.title` and `kultivator.screen.help.<topic>` in its own fragment.

**Keys:** `key.kultivator.seal_wheel`, `key.kultivator.quick_1` … `quick_4`, all unbound.

**Commands:** `/kultivasi jalan` (opens the tab); debug `goals reset` and `daily reroll`.

#### 19.3.5 Public API contracts (stubbed by the spine, filled by the owner)

| Owner | Signature |
|---|---|
| harta | `SpiritStones.isStone(ItemStack)`, `int grade(ItemStack)` (0–3, −1), `ItemStack stack(int grade, int count)`, `double condenseCost(CultivationData)` |
| harta | `BeastCores.create(Element, int grade)`, `void drop(ServerLevel, Vec3, Element, int grade)` |
| harta | `SpiritHerbs.randomFor(@Nullable Element, RandomSource, int minAge, int maxAge)` |
| harta | `RelicLoot.randomScroll(RandomSource)` |
| pertempuran | `EliteMonsters.isElite(Entity)`, `int tier(Entity)` (0 = none), `@Nullable Element element(Entity)` |
| pertempuran | `MobElements.of(Entity)` |
| pertempuran | `ElementRelations.factor(@Nullable Element attacker, @Nullable Element target)`, `List<Element> strongAgainst(Element)`, `@Nullable Element techniqueElement(CultivationData)` |
| pertempuran | `Techniques.qiCost(Technique, CultivationData)`, `CloudStep.maxJumps(CultivationData)` |
| kultivasi | `SpiritVeins.grade(ServerLevel, BlockPos)`, `double cap(ServerLevel, BlockPos)`; `density()` now 0–2.0 |
| kultivasi | `QiFormations.cardinalOffsets()`, `cornerOffsets()` (copies) |
| kultivasi | `Enlightenment.boostChance(ServerPlayer, double factor, int ticks)` |
| kultivasi | `ImmortalPerks.starDamageFactor(CultivationData)`, `int stars(CultivationData)`, `double nextStarCost(CultivationData)` |
| kultivasi | `Reforge.cost(CultivationData)`, `@Nullable Realm target(CultivationData)` |
| existing, read-only | `Techniques.hurt / power / castableTechniques`, `Targeting.*`, `Progression.grantXp`, `Comprehension.grant`, `SpiritBeasts.addXp / data`, `SpiritSites.current`, `Meditation.isMeditating`, `Tribulation.strikeCount / strikePct`, `Breakthrough.chance`, `FlyingSword.drainPerSecond`, `Qi.*`, `XpSources.miningXp`, `ProtectiveFormations.insideActiveFormation`, `FormationPattern.pillars`, `Alchemy.level`, `AlchemyFurnace.successChance`, `AlchemyRecipes.all()` (the `Recipe` record keeps its accessors) |

#### 19.3.6 Data keys

**Saved, in `extra()`:** each module writes only its own sub-compound; others may read it.

- `extra.harta`: `herbs_found`, `condensed`, `insight_pills{realmId:int}`, `bone_hp`, `book_day`, `book_insight`, `book_plain`
- `extra.combat`: `karma`, `karma_t`, `kills`, `elite1` (tier ≥ 1 kills), `elite2` (tier ≥ 2), `elite3` (tier 3), `thunder_kills`, `boss_ever` (bits: 1 Wither, 2 Warden, 4 Dragon, 8 Elder Guardian), `boss_day{type:long}`, `guards`, `guard_last{uuid:long}`
- `extra.kultivasi`: `abode{dim, x, y, z}`, `stars`, `perk_count`, `unbroken_at`, `reforges`
- `extra.talisman`: `xp`
- `extra.goals`: owned by P6-jalan (guide bits, hidden flag, rewarded chapters, trial snapshot, daily state, counters `c{…}`)

**Session, in `sessionExtra`:**

- spine: `cfg{hd, dp, guide, daily, tele}`
- pertempuran: `trib{…}` (19.2.10), `dharma_end`, `domain_end`
- kultivasi: `vein_grade`, `seclusion`, `fuel`, `fuel_end`, `fuel_idle`, `abode`, `reforge_end`

**Advancement names used across modules (guide steps):** `first_spirit_stone` and `first_ancient_scroll` (harta), `first_elite` (pertempuran), plus the existing names listed in 19.2.3.

### 19.4 Order of work and deliverables

1. **P6-spine** (orchestrator, 19.1).
2. The four modules in parallel. No Gradle; each module also returns a player-facing summary and a test checklist.
3. A single compile: `compileJava`, `compileClientJava`, `jar --no-daemon` (never tests, `runClient` or `build`), then a fix pass.
4. **P6-docs:** PANDUAN sections for every item, `docs/uji/FASE-6.md` built from the module checklists, README.

If a module runs long, these sub-parts may slip to a Phase 6b without breaking contracts:

| Module | May slip |
|---|---|
| harta | wandering-trader trade, ingredient Shift tooltips |
| pertempuran | twin rings |
| kultivasi | Immortal Stars |
| jalan | world-render blueprints (keep the particle fallback), carousel, others'-effects scale |

### 19.5 Rejected and deferred proposals

* **Metode Kultivasi (gongfa manuals):** deferred to Phase 7. Effort L; its hooks sit in XP, stats, qi, tribulation and toxicity files owned by three Phase 6 modules. Manuals can later reuse the Phase 6 loot pools.
* **Pusaka/Fabao (refined, blood-bound artifacts):** deferred to Phase 7. Effort L; item attribute modifiers stack with vanilla enchantments, which needs its own balance pass.
* **Alam Rahasia inside vanilla structures:** deferred to Phase 7. It should reuse the elite and protector spawners once they are proven in play.
* **Secret-realm dimension:** rejected. World generation and chunk-generation spikes on the Valorant laptop; the structure-based version is preferred.
* **Kultivator Sesat ambushes:** deferred until phase 5 lands. Needs the player-model NPC renderer and AI the disciples are building.
* **Teleport formations:** deferred. Needs a formation-module refactor and safe cross-dimension entity teleport; the Return Talisman and the flying sword cover travel for now.
* **Concealment and vein-nurturing formations:** deferred. Vein protection now comes from qi formations, the floor and the abode; concealment needs per-second mob target clearing.
* **Heavenly treasure falls:** deferred to Phase 7, together with the secret-realm event spawner.
* **Spirit mounts (Kuda Awan / Qilin):** deferred. Lower value; the flying sword is the travel fantasy.
* **Epithets and Dao Name, realm nameplates, inspect card:** deferred to a social phase. They need a `VisualState` codec change and render hooks in P4-visuals territory; low singleplayer value.
* **Heaven Ranking / Daftar Langit with NPC rivals and rated duels:** deferred to Phase 7, after the Jalan tablet proves out.
* **Sect ranks, contribution, missions, hall, treasury, sect screen, raids, identity, qi resonance:** deferred as one "Sekte Hidup" phase after disciples exist. The user mostly plays alone.
* **Disciples × sects spec and disciple expeditions:** deferred to phase 5 follow-ups. Note for phase 5: disciples near a tribulation should count as Pengawal Dao for protector targeting only, never for strike reduction.
* **Sharing pills and channelling qi to allies:** deferred to the social phase (LAN-only value).
* **Jade-slip technique teaching:** rejected for now. It undercuts book rarity; ancient scrolls cover technique acquisition.
* **Jade notification slips and message log:** deferred. Converting about 40 `Msg.chat` sites across many modules needs a single coordinated pass, not parallel modules.
* **Help → full Kitab codex rewrite with unlock dots:** deferred. Phase 6 only adds topics.
* **HUD layout editor (anchor, scale, drag):** deferred. Auto quality and HUD tidying come first.
* **Pendamping tablet (beasts, formations, sect):** deferred to the social phase.
* **Alchemy furnace look-at card:** deferred. Needs a pot request payload; the recipe tablet comes first.
* **Nine Heavens tribulation:** deferred. Immortal Stars give the endgame first.
* **Explorer's codex (Catatan Penjelajah):** deferred until the new content has settled.
* **Qi Sense server neighbour-scan compass:** merged into the vein compass and the HUD vein line instead of a 40-tick server poll.


## 20. Phase 6 — Sekte Abadi & Murid OP (user priority, 2026-10-17)

User decisions: focus on the sect and on each disciple's abilities/effects; disciples are obtained through the **Sect
Hall (Aula Sekte) recruitment** (the recruitment scroll stops being craftable, it stays for creative/debug); disciples
level up endlessly and may surpass their master (True Immortal and then unlimited **Bintang Abadi** star tiers),
becoming more and more overpowered; at the top they may even destroy the world, but real block destruction happens
only in the opt-in **Mode Kehancuran** (default OFF). The full specification is written after phase 5 lands (built on
the real disciple code) and appended here.

### 20.0 Test commands for disciples (user request, 2026-10-17)

`command/TestCommands` gets `/kultivasi uji murid <name|semua> maks | ranah <realm> [stage] [layer] | tur [seconds] |
teknik | bintang <n>` (op only). `<name>` suggests the names of the player's own loaded disciples (custom name);
`semua` applies to all of them. `maks` = True Immortal Peak + heavenly root + every teachable technique + full qi and
health; `tur` cycles the same realm list as the player tour on that disciple and restores its saved data afterwards;
`bintang <n>` (after phase 6) sets the Bintang Abadi star tier. Implemented by the orchestrator right after phase 5.

### 20.1 Principles for this phase

* **Source rule (user, 2026-10-17): every idea comes from a real cultivation story.** Each feature has a **Sumber / Source** line that names the work(s) and the exact concept it adapts. Only concepts from the researchers' verified list are used, and the source lines use the verifiers' corrections. Numbers, recipes, UI and safety rules are game design and are never presented as canon. Anything without a verifiable source was dropped or redesigned (see 20.6).
* **Block safety.** DESIGN 0 applies everywhere except Mode Kehancuran (20.3.21).
  - Only `kehancuran/WorldBreaker` may call `setBlock`/`removeBlock`.
  - The Sect Hall stele is placed and broken only by players, through vanilla placement and mining.
  - Duties change no crops or blocks. Duels, phantoms, rivals, clones, auras and ultimates act on entities only. Fire is entity fire only. Lightning is particle bolts only.
* **Targeting.** Disciple techniques, auras and ultimates hit only what `DiscipleTechniques.mayHit` accepts. The only exception is the two partners of a duel registered in `DuelRules` (tournament, Hundred Dynasties War, Void Realm, Tanding dengan Guru).
  - Disciples still never fight `DiscipleEntity.untouchable` mobs: creeper, ghast, blaze, breeze, ravager, silverfish, Wither and heart-bound creaking.
  - Every reward below that names one of these mobs credits the **master's** kill, never a disciple's.
* **Performance (a laptop that also runs Valorant).**
  - Per hall at most: 6 candidate entities (8 on a Hundred Dynasties War day, the one documented exception), 2 duellists plus at most 1 rival or phantom, 2 clones per disciple.
  - Disciples on expeditions or soul flight are pure data, with zero ticking.
  - Hall logic runs every 20 ticks. The building scan is spread over 9 ticks every 30 s.
  - Duties, expeditions and away timers are computed lazily from game time.
  - Kehancuran stays inside the block budget of 20.3.21.
  - Continuous visuals are client-side within 32 blocks and scaled by Effect Quality.
* **Phase 7 items do not exist yet** (spirit stones, beast cores, elites, the player's Immortal Stars). Phase 6 uses vanilla items and the existing Kultivator pills. The player's star count is read from `extra.kultivasi.stars` (0 until phase 7 exists).
* **Singleplayer first.** A hall belongs to one player. On LAN, members of the same `/sect` can open it read-only and donate to its pool.
* **Constants and text.** Every number below is a `public static final` in its class, so screens show the real values. All player-facing text is in `id_id` and `en_us`. All GUI follows DESIGN 5b (jade scroll), apart from the deliberate blood-red variant of the Kehancuran warning (20.3.21).
* **Time.** "Day index" `d = floor(overworld dayTime / 24000)`. Sunrise is `dayTime % 24000` crossing 0 and noon is crossing 6000. The hall tick checks this once per second.

### 20.2 Spine additions (orchestrator, after phase 5 is committed, before the four modules start)

1. **`network/Actions`.** Add these ids (21–24 stay reserved for phase 7):
   - `HALL = 30` → `sect.hall.Halls.handleAction`
   - `SECT_EVENT = 31` → `sect.event.SectEvents.handleAction`
   - `DISCIPLE_POWER = 32` → `disciple.power.DisciplePowers.handleAction`
   - `DISCIPLE_PATH = 33` → `disciple.path.DisciplePaths.handleAction`
   - `KEHANCURAN = 34` → `kehancuran.Kehancuran.handleAction`

   For disciple actions `arg` is the entity id. For hall actions `arg` is the sub-op. `text` carries `"op[:param]"`. Route all five in `Net`.
2. **`network/Payloads`.**
   - New `HallInfo(CompoundTag)` S2C.
   - `OpenScreen.HALL = 3` (arg 0).
   - `OpenScreen.KEHANCURAN_CONFIRM = 4` (arg 0 = the player's oath, otherwise the disciple entity id).
   - Client routes go to `client.hall.HallClient.onInfo/openScreen` and `client.kehancuran.KehancuranClient.openConfirm`.
3. **`fx/FxType`.** Append these and add their routes in `FxDispatcher`:

   | FxType | Routed to |
   |---|---|
   | `HALL` | `client/hall/HallFx` |
   | `SECT_EVENT` | `client/sectevent/SectEventFx` |
   | `DISCIPLE_POWER` | `client/disciple/power/PowerFx` |
   | `STAR` | `client/disciple/path/PathFx` |
   | `KEHANCURAN` | `client/kehancuran/KehancuranFx` |

   `FxDispatcher.tick` also calls `PowerFx.tick` and `PathFx.tick`.
4. **`DiscipleData`.** Add `CompoundTag extra()`, saved under `"extra"`, copied in `copy()`, empty by default. Each module writes only its own sub-compound (20.4.6). Make `DiscipleCultivation.distribute(DiscipleData, double)` public so XP can be added to data whose entity is not loaded.
5. **`DiscipleEntity`.**
   - **Roles.** `enum Role { DISCIPLE, CANDIDATE, RIVAL, PHANTOM, CLONE }` with `role()` and `setRole(Role, @Nullable BlockPos anchor)`.
     - Only DISCIPLE is saved, listed in `Disciples`, counted toward the cap, or able to drop a tablet. The other roles are refused in `ServerEntityEvents.ALLOW_LOAD`.
     - CANDIDATE: no owner, invulnerable (except `/kill`), excluded from Targeting (`Targeting.addExclusion`) and from `mayAttack` in both directions. AI is a stroll within 10 blocks of the anchor plus looking at players. Right-click calls `Candidates.onInteract` (A).
     - RIVAL and PHANTOM: `NoGrief.TAG`. They fight only their `DuelRules` partner.
     - CLONE: allied with its source's master. Fights monsters, follows its source, not saved.
   - **Activity.** `activity()` and `setActivity(int id, @Nullable BlockPos spot)`.
     - Ids: 0 none, 1 water, 2 garden, 3 kitchen, 4 farm, 5 formation guard, 6 tower, 7 void body, 8 spectator, 9 duellist.
     - While the id is 1–8 the disciple holds the spot like STAY and fights only when hit (5 guards the spot).
   - **New order.** `Mode.GUARD_HALL`: patrols within 32 blocks of its hall stele and fights hostiles inside the territory.
   - **New synced fields** for the client: role (byte), activity (byte), sect rank (byte), stars (int), talent (int: physique 4 bits, soul colour 3 bits, special 2 bits), power flags (int: bits 0–15 module C, 16–31 module D). Setter: `setSyncedPower(...)`.
6. **`disciple/DiscipleHooks`** (new, spine-owned; providers registered in each module's `init()`). The spine wires each call into phase-5 code; the call site is in brackets:

   | Hook | Call site | Combine rule |
   |---|---|---|
   | `potentialFactor(DiscipleData)` | `xpMultiplier` | replaces the root-grade factor (default: root multiplier) |
   | `rankFactor(DiscipleData)` | `xpMultiplier` | replaces `XP_FACTOR` (default 0.6) |
   | `xpFactor(@Nullable DiscipleEntity, DiscipleData, XpSource)` | `xpMultiplier` | product |
   | `masterShareFactor(DiscipleData)`, `masterShareAnyRange(DiscipleData)` | master-meditation share | product; any true |
   | `stats(DiscipleEntity, StatSink)` | `refreshSynced` | applied as permanent modifiers, removed when absent |
   | `damageFactor(DiscipleEntity, LivingEntity target, boolean technique)` | `DiscipleTechniques` hurt, `doHurtTarget` | product |
   | `hurtFactor(DiscipleEntity, DamageSource)` | start of `hurtServer` | product |
   | `onHurt(DiscipleEntity, DamageSource, float)` | after damage | – |
   | `onDeath(DiscipleEntity, DamageSource) → boolean` | before `die`/tablet | first true = handled (cancelled or soul flight) |
   | `combatCast(DiscipleEntity, LivingEntity) → boolean` | start of `tryCastInCombat` | first true wins |
   | `tick20(DiscipleEntity)` | every 20 ticks | – |
   | `onKill(DiscipleEntity, LivingEntity)` | `onKill` | – |
   | `strikeFactor(e, Rank target, int index)` | `DiscipleBreakthrough` strikes | product, floored at 0.5 |
   | `qualityBonus(e, Rank target)` | quality roll | sum, −0.10…+0.40 |
   | `qualityFloor(e, Rank target) → @Nullable BreakthroughQuality` | after the roll | highest wins |
   | `foundationChance(e)` | `chance` | sum |
   | `breakthroughBlocker(e) → @Nullable String` | `breakthroughBlocker` | first non-null |
   | `breakthroughListener (onStart, onStrike, onEnd(success, kind))` | `DiscipleBreakthrough` | – |
   | `teachBlocker(e, master, technique)` | `teachBlocker` | first non-null |
   | `teachCostFactor(e, master, technique)` | `teachCost` | product |
   | `daoBypass(DiscipleData) → boolean` | `ready`, `teachBlocker` Dao check | any true |
   | `onTaught(e, master, technique)` | after `teach` | – |
   | `feedHandler(e, master, stack) → boolean` | start of `feed` | first true consumes |
   | `onFed(e, master, stack)` | after a successful `feed` | – |
   | `modeBlocker(e, Mode) → @Nullable String` | mode setter | first non-null |
   | `retreatFraction(e)` | `RetreatGoal` (default 0.25) | max |
   | `regenFactor(e)` | regeneration | product |
   | `info(e, master, CompoundTag)` | end of `infoFor` | – |
   | `cap(ServerPlayer) → int` | every disciple-count check | replaces `MAX_DISCIPLES` (default 5) |
7. **`Disciples` (phase-5 core).**
   - `CompoundTag depart(DiscipleEntity, String reason)`: removes the entity silently and marks the list entry `away`. Away disciples still count toward the cap.
   - `@Nullable DiscipleEntity arrive(ServerLevel, Vec3, CompoundTag, UUID owner)`: restores the disciple and clears the away mark.
   - `DiscipleEntity createCandidate(ServerLevel, Vec3, DiscipleData, Component name, int skin, boolean slim, BlockPos anchor)`.
   - `boolean recruitCandidate(ServerPlayer, DiscipleEntity candidate)`: switches the candidate to role DISCIPLE in place, sets the owner, adds the list entry, and reuses the welcome messages and FX of `recruit`.
   - Delete `data/kultivator/recipe/recruitment_scroll.json`. The scroll stays in the creative tab and its tooltip says "Hanya kreatif / Creative only".
8. **`disciple/DuelRules`** (new, spine-owned).
   - API: `begin(LivingEntity a, LivingEntity b, int maxTicks, Listener)`, `dueling(Entity, Entity)`, `end(...)`.
   - Consulted first in `DiscipleEntity.mayAttack`, `isInvulnerableTo` and `DiscipleTechniques.mayHit`, and at the top of `Targeting.isHostileTo` so a master's techniques reach his duel partner.
   - Damage between partners is clamped to keep at least 10% max health. A clamped hit ends the duel. Players use the `ServerLivingEntityEvents.ALLOW_DAMAGE` pattern from `Sparring`.
   - `Listener.onEnd(winner, loser, Reason { DEFEAT, TIMEOUT, LEFT_RING, CANCELLED })`. On TIMEOUT the higher health fraction wins.
9. **`cultivation/DaoProtectors`.** Additive `int spawnWaveFor(ServerLevel, LivingEntity target, @Nullable ServerPlayer owner, Rank target, int size, double healthFactor)`. It uses the same hardening, tags and cleanup, with `target` as the first target.
10. **`cultivation/Progression`.** `addMultiplierProvider(ToDoubleFunction<ServerPlayer>)`, multiplied into `xpMultiplier` (used by D's Kebanggaan Guru).
11. **`KultivatorConfig`.** New fields (clamped on load):
    - `sectHall = true`, `recruitmentIntervalDays = 3` (1–15), `sectEvents = true`, `tournamentIntervalDays = 7` (3–30), `forbiddenGroundIntervalDays = 15` (5–60)
    - `modeKehancuran = false`, `kehancuranMaxRadius = 12` (4–24), `kehancuranBlocksPerTick = 48` (8–256), `kehancuranMaxBlocksPerCast = 4096` (256–16384), `kehancuranDropsPct = 0` (0–10), `kehancuranHallSafeRadius = 64` (48–256), `kehancuranPlayerSafeRadius = 24` (16–128), `kehancuranMsptPause = 45` (30–50), `kehancuranWarningSeconds = 30` (10–60), `kehancuranUndoMinutes = 10` (0–30)

    `KultivatorConfig.save()` must exist so `/kultivasi kehancuran izin` can write the file.
12. **Entry points.**
    - `Kultivator.onInitialize` (before `ModItems.init()`) calls `sect.hall.Halls.init()`, `sect.event.SectEvents.init()`, `disciple.power.DisciplePowers.init()`, `disciple.path.DisciplePaths.init()` and `kehancuran.Kehancuran.init()`.
    - `CultivateCommand` calls each module's `addCommands`.
    - `KultivatorClient` calls `client.hall.HallClient.init()`, `client.sectevent.SectEventClient.init()`, `client.disciple.power.PowerClient.init()`, `client.disciple.path.PathClient.init()` and `client.kehancuran.KehancuranClient.init()`.
13. **Client stubs.**
    - `client/hall/HallScreen` with tablet slots and stub pages:
      - `AulaPage`, `CalonPage`, `JenjangPage`, `TreasuryPage`, `LogPage` (A)
      - `DutyPage`, `MissionPage`, `ArenaPage` (B)
      - `RedSealPage` (D, shown only when `HallInfo.cfg.kehancuran` is true)
    - `DiscipleScreen` gets `TAB_TALENT = 4` (stub `TalentTab`, C), `TAB_TREASURE = 5` (stub `TreasureTab`, C) and `TAB_PATH = 6` (stub `PathTab`, D). Tablets shrink to fit 7 inside 240 px.
14. **Stub classes** with the exact public signatures of 20.4.5, so every module compiles against the others in any order.

### 20.3 The features

#### 20.3.1 Prasasti Aula Sekte: the hall, its level and its territory (module A)

**Sumber / Source.**
- *Battle Through the Heavens*: the Xiao clan's Dou Qi testing stone monument reads each youth's talent in public. The stele is that testing stone.
- *A Record of a Mortal's Journey to Immortality*, ch. 153: Yue Lu Hall is an abode on Great Shaman's Mountain, covered by a spell formation, that stores magic tools, pill recipes and technique manuals and supplies materials. This gives the hall and its pavilion.
- *I Shall Seal the Heavens*, ch. 36–38: Meng Hao studies a whole season in the Magic Pavilion. This is the pavilion requirement.
- *Return of the Mount Hua Sect*: a ruined, unknown sect regains its fame through victories, and applicants then return. This is renown.
- *Perfect World*, ch. 551/558: Shi Hao vows to rebuild the ruined Heaven Mending Pavilion. This is "rebuild to level up".

**Block.** `sect_hall_stele`, *Prasasti Aula Sekte / Sect Hall Stele*.
- Shaped recipe: `G B G / A L A / G B G` (G gold ingot, B bookshelf, A amethyst block, L lodestone).
- Hardness 3.5, any pickaxe, blast resistance 1200, push reaction BLOCK, light 7, drops itself.
- Model: a jade tablet with a gold cap on a stone foot.

**Registration** (`setPlacedBy`, server side).
- If the placer has no hall, a hall is founded:
  - `HallData` stores owner, dimension, position, `founded_day`, name "Sekte <player>" (renamable on the Aula page, validated with `Sect.validName`).
  - Effects: pillar plus bell chime, chat line "Aula Sekte berdiri", advancement `sect_gate_open` ("Gerbang Sekte Terbuka").
- If the placer already has a hall, the new stele is inert. Sneak-using it with an empty hand offers "Pindahkan Aula ke sini?" (move the hall; cooldown 1 in-game day). Renown, pool, storehouse, candidates and away teams move with it.
- A stele within 128 blocks of another player's hall is inert, with a message.
- If the owner is in a `/sect`, the screen shows the sect name and sect mates get read-only view plus donations.

**Removal.**
- Whenever the block is gone (mined, or noticed missing by the 30 s check), the hall becomes **dormant**. Nothing is deleted: candidates leave, duties pause, and away teams return beside the master instead of at the hall.
- Placing a stele again (anywhere) reactivates the hall with all its data. Renown can only be earned, so re-placing gains nothing.

**Tingkat Aula / Hall level 1–9** = `min(renownLevel, buildingCap)`.

| Level | Renown needed | Candidates per Penerimaan | Disciple cap | Territory radius | Exchange price | Duty posts | Missions per day |
|---|---|---|---|---|---|---|---|
| 1 | 0 | 2 | 5 | 24 | ×1.00 | 1 | 3 |
| 2 | 100 | 2 | 6 | 28 | ×1.00 | 1 | 3 |
| 3 | 300 | 3 | 6 | 32 | ×0.95 | 2 | 3 |
| 4 | 700 | 3 | 7 | 36 | ×0.95 | 2 | 4 |
| 5 | 1,500 | 3 | 7 | 40 | ×0.90 | 2 | 4 |
| 6 | 3,000 | 4 | 8 | 44 | ×0.90 | 3 | 4 |
| 7 | 6,000 | 4 | 8 | 48 | ×0.85 | 3 | 4 |
| 8 | 12,000 | 4 | 9 | 52 | ×0.85 | 3 | 5 |
| 9 | 25,000 | 5 | 9 | 56 | ×0.80 | 4 | 5 |

Formulas: candidates `2 + floor(L/3)`, cap `5 + floor(L/2)`, radius `20 + 4L`, price `1 − 0.05 × floor((L−1)/2)`, posts `1 + floor(L/3)`. Without a hall the cap stays 5.

**Building caps.** Read-only. Scanned in a box of ±16 blocks horizontally and y −2…+6 around the stele (the tower box is ±32), spread one y-slab per tick over 9 ticks, every 30 s while the hall chunk is loaded and whenever the screen opens.

| Building (source) | Requirement | Allows up to |
|---|---|---|
| (stele only) | – | Level 2 |
| **Paviliun Kitab** (Yue Lu Hall, Magic Pavilion) | ≥ 10 bookshelves or chiseled bookshelves + 1 lectern within 16 | Level 4 |
| **Formasi Agung** (Yue Lu Hall's covering formation) | an active, registered Protective Formation with its lodestone within 24 | Level 6 |
| **Aula Latihan** (Mount Hua's drill ground) | ≥ 4 target blocks within 16 | Level 8 |
| **Menara Langit Membara** (20.3.15) | ≥ 3 tower rooms within 32 | Level 9 |

**Ketenaran Sekte / renown** (never decreases; awards come only from the owner's own disciples):

| Event | Renown |
|---|---|
| Disciple breakthrough | 10 × target realm ordinal |
| Disciple calamity passed | 100 |
| Every 9th disciple star | 50 |
| Tournament 1st / 2nd / 3rd | 200 / 100 / 50 (Turnamen Elit ×2.5) |
| Expedition success | 10 × tier |
| Forbidden ground survivor | 30 each |
| Boss killed with a disciple dealing ≥ 20% of the damage | 100 (once per boss type per day) |
| Lost art restored | 500 |

At level 5 the server announces "Sekte <nama> telah kembali!". The Mount Hua line is credited in its tooltip.

**Territory.** Horizontal distance ≤ radius, |dy| ≤ 32, same dimension. Used by candidates, duties, events, elder auras and GUARD_HALL.

**Hall screen** (owner right-clicks the stele: `HallInfo` + `OpenScreen.HALL`). Vertical tablets:

| Tablet | Content |
|---|---|
| Aula | name, level and renown bar, building checklist with hints, cap, schedule of the next events, away teams |
| Calon | candidates (20.3.3) |
| Jenjang | ranks (20.3.6) |
| Perbendaharaan | contribution points (20.3.7) |
| Tugas | duties (B) |
| Misi | expeditions (B) |
| Arena | events (B) |
| Catatan | last 50 log lines stamped with day index |
| Segel Merah | Mode Kehancuran (D), only when the server allows it |

It fits 320×240; the tablets scroll when fewer than 9 fit.

**Performance.** One hall map lookup per second, one spread scan every 30 s, no per-block ticking.

#### 20.3.2 Formasi Agung Sekte: the grand protective formation (module A, edits `formation/*`)

**Sumber / Source.**
- *A Record of a Mortal's Journey to Immortality*, ch. 153: Yue Lu Hall is entirely covered by a spell formation.
- *Renegade Immortal*, ch. 216: Wang Lin lays hidden restrictions so no one can enter while he cultivates. This gives stacked restriction layers.
- *Soul Land*: the Seven Treasure Glazed Tile Sect is guarded by two Titled Douluo protectors, Sword Douluo and Bone Douluo. This is the Pelindung post (20.3.6).

A Protective Formation (DESIGN 10) of the hall owner, with its lodestone within 24 blocks of an active stele, becomes **Formasi Agung Sekte**.
- **Radius:** `min(32, 16 + 2 × level)`.
- **Penjaga Formasi:** a Foundation+ disciple in STAY or MEDITATE within 3 blocks of the lodestone (A sets activity 5) keeps it powered with no amethyst drain while it is there. A shows "Dijaga: <nama>".
- **Lapisan Larangan / restriction layers:** `1 + (Tetua and Tetua Agung inside the territory) + (Dalam+ Penjaga Formasi)`, at most 4.

| Layer | Effect on monsters inside |
|---|---|
| 1 | Push out + 1 damage/s (unchanged) |
| 2 | + Slowness II |
| 3 | + Slowness V for 3 s when crossing the edge |
| 4 | Damage 2/s; monsters inside are outlined for the owner (client FX through `FormationFx`, no Glowing effect) |

- Each Pelindung Sekte inside the territory multiplies formation damage by 1.5 (max 2).

**Performance.** Unchanged: one scan per second per formation.

#### 20.3.3 Hari Penerimaan Murid: candidates arrive, are tested and accepted (module A)

**Sumber / Source.**
- *A Record of a Mortal's Journey to Immortality*, ch. 1–5: the Seven Mysteries Sect holds its test once every 5 years. The climb has three stages: bamboo forest, crumbling slope and the rope up Bone Refining Cliff. Han Li misses the noon deadline, but his determination earns him Unofficial Disciple status, retested after half a year.
- *Renegade Immortal*, ch. 3–29: Wang Lin fails the spirit test and enters only as an honorary (grey-robed) disciple. "Murid Tercatat" is the mod's name for that rank.
- *Battle Through the Heavens*: Jia Nan Academy sends tutors to test and recruit youths, and the testing stone reads Dou Zhi Li in 1–9 Duan.
- *Tales of Demons and Gods*, ch. 13–14: the seven soul-realm colours, red lowest, azure and indigo near-legendary.
- *Soul Land*: Spirit Hall awakens village children every year on schedule, and Shrek accepts "only monsters".
- *I Shall Seal the Heavens*, ch. 19: youths with latent talent are taken in as servants.
- *Return of the Mount Hua Sect*: talented youths avoid a fallen sect.

**Schedule.** The arrival day is every `recruitmentIntervalDays` (3) days: `d ≡ founded_day (mod interval)`, plus the first sunrise after founding.
- At sunrise, if the hall is active, its chunk is loaded and the owner is online within 128 blocks, candidates arrive. Otherwise the arrival waits until those hold, but only within the same day; missed days never stack.
- Every 4th arrival day at hall level 3+ is the **Perang Seratus Dinasti** instead (20.3.13).
- **Count:** N = candidates of the level, +1 at renown ≥ 3,000 ("the sect's fame returns"), max 6.
- They step out of a travellers' mist puff on safe ground 8–14 blocks from the stele (`Targeting.safeSpot`, never in blocks or fluids; fewer if no spot is found). A distant bell sounds, the owner gets a chat line and a toast.
- Candidates stay until the next sunrise. They sit and doze at night, then bow and vanish.

**Data first.** Each candidate is a record in `HallData`:
- `DiscipleData.fresh`, name, skin, slim
- age 12–16 (cosmetic, plus Calon Jenius)
- **Tekad** (determination) 0–100, rolled uniform 20–100 and always visible
- talents, physique and special type (rolled now, hidden until tested)
- tested flag, expiry tick

The entity (role CANDIDATE, nametag "Calon · ???") is only a view of the record. It is re-created when the hall chunk loads with the owner within 64 blocks, and never saved.

**Rolling** (module A writes, module C applies effects):
- Root: `SpiritRoots.roll`.
- Renown gate: a HIGH root while renown < 300, or a HEAVENLY root while renown < 1,000, is re-rolled down one grade. The card notes "Calon berbakat menghindari sekte yang belum dikenal".
- 'Hanya Monster' toggle (jade coin on the Calon tablet): only candidates with root ≥ HIGH or a physique are generated, and N − 1 of them (min 1).

**Ujian Masuk / the entrance test.** Talisman "Uji (1 Pecahan Kecubung)" on the card, or right-click the candidate holding an amethyst shard. It costs one shard and plays a 6 s cinematic:
1. **0–3 s:** three tiers of particles climb beside the candidate (bamboo green, stone grey, cliff gold; Fanren's three stages). Tiers climbed:
   - +1 if root grade ≥ LOW
   - +1 if Tekad ≥ 40
   - +1 if `grade × 20 + Tekad ≥ 110`
2. **3–6 s:** the stele raises a light pillar of **Duan** segments 1–9 (BTTH), one chime per segment, coloured with the candidate's **soul colour** (TDG, 20.3.5).
   - Duan = `clamp(1 + 2 × grade + floor((5 − elementCount)/2) + (physique ? 1 : 0), 1, 9)`.

The card then reveals root, colour, talents, physique and special type.

**Verdict.**

| Verdict | Condition | Can join as |
|---|---|---|
| **Lulus sebelum tengah hari** | all 3 tiers | Murid Luar, free |
| **Murid Tercatat** | otherwise, Tekad ≥ 70 (the Han Li / Wang Lin path) | Tercatat, free; a TRASH/LOW root also gets the talent Hati Gigih |
| **Ditolak** | anything else (one of 4 polite lines) | Tercatat as servant ("Jalur Pelayan", ISSTH), 100 PK from the pool |

Untested candidates can be accepted blind; everything is revealed on joining.

**Acceptance.** Talisman "Terima": cap check, then `Disciples.recruitCandidate` at the candidate's spot.
- Effects: the existing recruit FX plus a jade ring and a bow.
- Starts as a Mortal (the phase-5 awakening flow) at the rank of its verdict.
- Advancements `first_recruit_hall`, and `determined_disciple` for a Tercatat.
- "Undang pergi" dismisses a candidate.

**Calon Jenius** (TDG Genius class): age ≤ 14 and colour ≥ Sian. Shown as a flag; adds +5% to its physique roll.

**UI (Calon tablet).** One scroll card per candidate: face, name, age, Tekad bar, verdict seal, talisman buttons, a countdown "Pergi saat fajar 0:14:20", and "Penerimaan berikutnya: hari 12".

**Performance.** At most 6 candidate entities (8 on a war day), each with a stroll goal ticked every 10 ticks and look-at AI. Tokens, envoys and invitations never spawn past the cap: an extra record waits and gets its body when a slot frees.

#### 20.3.4 Token Kenaikan Abadi and the Calon Istimewa (A rolls, C applies)

**Token Kenaikan Abadi / Immortal Ascension Token** (`immortal_ascension_token`, not craftable).

**Sumber / Source.** *A Record of a Mortal's Journey to Immortality*: the Immortal Ascension Token lets whoever holds it enter a sect regardless of spiritual root, and entitles the bearer to one Foundation Establishment Pill.

- **Loot:** `LootTableEvents.MODIFY`, built-in tables only, 6% in `chests/stronghold_library` and `chests/ancient_city`, 4% in `chests/trial_chambers/reward_ominous`.
- **Use:** right-click the active stele. One candidate arrives at once: root ≥ MID, Tekad ≥ 60, already tested.
- **Recruiting it:** joins as Murid Luar whatever the verdict, and the master receives one Normal-quality Foundation Establishment Pill.
- Advancement `immortal_token`.

**Calon Istimewa** (at most one per arrival; flags in `extra.talent.special`):

| Type | Chance | Sumber / Source | Effect (module C) |
|---|---|---|---|
| **Pengemis di Gerbang** (Beggar at the Gate) | 2% per arrival, once per hall | *Return of the Mount Hua Sect*: the Plum Blossom Sword Saint is reborn as a ragged beggar boy, joins as a lowly third-generation disciple and is secretly the greatest swordsman. | Card shows TRASH / Merah. On reaching Foundation, a reveal cinematic: root HEAVENLY, colour Nila, +30 comprehension when the Sword Dao is chosen (+10 for any other Dao), and the innate Plum Blossom passive of 20.3.8. |
| **Murid Regresor** (Regressor) | 3% | *Volcanic Age*: a mediocre Mount Hua elder is sent back to his youth on his deathbed with his memories. *Absolute Sword Sense*: Soh Woonhwi regresses ten years. | Remembers a previous-life realm R (Golden Core–Dao Union, shown "???" until tested). Until R: XP ×1.6, every breakthrough at least Perfect (`qualityFloor`), strikes ×0.8. |
| **Dantian Tersumbat** (Blocked Dantian) | 4% | *Stellar Transformations*: Qin Yu's dantian cannot store energy, so he trains the body and becomes mighty. | Cannot learn qi techniques (teach blocker with the reason). Melee ×1.6, max-health bonus ×1.5, its breakthrough failures cause no Qi Deviation, kill XP ×1.4, star-qi meditation ×1.5, Undying Codex free. |

#### 20.3.5 Warna Alam Jiwa, Bakat and Fisik Langka: soul colour, talents, physiques (A rolls, C applies)

**Warna Alam Jiwa / soul colour.**

**Sumber / Source.** *Tales of Demons and Gods*, ch. 13–14:
- Seven colours: red, orange, yellow, green, cyan, azure, indigo.
- Teacher Shen Xiu claims a red realm caps soul force at 600 unless fortuitous circumstances occur.
- Nie Li insists no soul realm is crippled.

Derived from the root grade (existing disciples: from their grade, Yellow for MID). It replaces the root XP multiplier through `potentialFactor`:

| Colour | Root grade | XP factor |
|---|---|---|
| Merah / Red | TRASH | 0.6 |
| Jingga / Orange | LOW | 0.85 |
| Kuning / Yellow | MID (60%) | 1.0 |
| Hijau / Green | MID (40%) | 1.1 |
| Sian / Cyan | HIGH (65%) | 1.3 |
| Biru Langit / Azure | HIGH (35%) | 1.5 |
| Nila / Indigo | HEAVENLY | 1.7 |

- **Red cap:** a Merah disciple cannot break through out of Golden Core Peak (`breakthroughBlocker`: "Alam jiwa merah menahan…"). XP beyond the full bar goes to Stored Qi at 50%.
- **Good fortune** lifts the colour one step:
  - a Marrow Cleansing Pill fed through the existing feed rules (a new grade takes that grade's base colour), or
  - "Keberuntungan": 1% per minute while meditating within 8 blocks of the meditating master.

  The colour tints the aura and the name line.

**Bakat / talents.** Rolled at creation: 60% none, 32% one, 8% two. Shown as seal tiles on the Bakat tab.

| Talent (weight) | Sumber / Source | Effect |
|---|---|---|
| **Hati Gigih** / Persevering Heart (26, TRASH/LOW roots only) | *A Record of a Mortal's Journey*: Han Li rises through persistence on a poor pseudo root. | Meditation and duty XP ×1.3, Foundation chance +0.08, strikes ×0.9 |
| **Darah Dewa Kuno** / Ancient God Blood (2) | *Renegade Immortal*: Wang Lin inherits the Ancient God Tu Si; Ancient Gods grow by stars, and Tu Si met his first trial at eight stars. | Max-health bonus ×1.5, melee ×1.3, star health ×1.5, first calamity at star 8 |
| **Bakat Pil** / Pill Gift (22) | *I Shall Seal the Heavens*: Meng Hao's gift for alchemy makes him the Pill Demon's disciple. | Fed pills ×1.3, pill cooldown −30%; when Herba Fondasi is refined (20.3.11), 25% chance of +1 quality step |
| **Jari Penempa Roh** / Spirit Enhancer (22) | *A Will Eternal*: Bai Xiaochun's gift for spirit enhancement. | Natal enhancement +15% success, +1 max fold |
| **Takut Mati** / Fear of Death (16) | *A Will Eternal*: Bai Xiaochun's overwhelming fear of death. | Retreats at 40% HP (`retreatFraction`), regeneration ×2 while retreating, Undying Codex points ×2 |
| **Indra Pedang** / Sword Sense (12) | *Absolute Sword Sense*: Soh Woonhwi hears weapons and reads their wielders' styles. | Against monsters holding a sword: 30% dodge of their melee, ×1.5 comprehension from killing them; after 20 such kills, +10% sword damage (melee and Sword techniques) |

**Fisik Langka / rare physique** (one at most).
- Chance: 6% base, +1% per hall level above 1, +5% for Calon Jenius, 30% with a HEAVENLY root, 50% for the Hundred Dynasties champion.
- Weights in brackets. Effects (module C), all monsters-only.

| Physique (weight) | Sumber / Source | Effect |
|---|---|---|
| **Tubuh Racun Malapetaka** / Woeful Poison Body (14) | *Battle Through the Heavens*: the Little Fairy Doctor's Woeful Poison Body and Seven-Colored Poison Manual; it grows out of control until Xiao Yan's Poison Pill gives her a normal life. | Immune to Poison and Wither; melee adds Poison II 4 s. From Foundation: aura 0.3 U/s within 4 (one query per second). Toxic meter +1 per minute of fighting; at 100, "Racun Mengamuk" for 60 s (aura ×2, ignores Follow, still monsters only). A Healing Pill (the Poison Pill stand-in) resets the meter. From Deity Transformation: "Kabut Racun Tujuh Warna", a 6-block cloud for 8 s (Poison III + Wither II), cooldown 45 s. |
| **Darah Naga Purba Taixu** / Taixu Ancient Dragon (12) | *Battle Through the Heavens*: Zi Yan, a young Taixu Ancient Dragon in human form, devours pills and takes dragon form. | Pill effects ×1.5, pill cooldown ×0.5. From Nascent Soul: "Wujud Naga" for 20 s (scale ×1.4 if there is head room, +50% damage, knockback immune, flame-breath cone of 6 blocks at 1.2 U/s with entity fire 3 s), cooldown 120 s. |
| **Garis Darah Dou Di** / Dou Di Bloodline (10) | *Battle Through the Heavens*: the Xiao clan's ancient Dou Di bloodline, awakened later by Xiao Yan. | +0.12 quality score on every breakthrough. At Dao Union, a one-time awakening cinematic: +15% to all stats and +1 stage. |
| **Tubuh Emas Tulang Angkuh** / Proud Golden Body (14) | *Martial Peak*: Yang Kai's Proud Golden Body grows stronger through the beatings he endures. | Every 10 damage taken = 1 Darah Emas stack (max 40, −1 per 30 s); each stack +1.5% damage and +0.5% damage reduction. Every 500 damage survived: +0.5 max HP for good (max +30). |
| **Pembawa Simbol Melahap** / Devouring Ancestral Symbol (8) | *Martial Universe*: Lin Dong carries the Devouring Ancestral Symbol. | Kills give +25% XP and heal 5% of the victim's max health. Unlocks Pusaran Melahap (20.3.21). |
| **Tulang Mahatinggi** / Supreme Bone (10) | *Perfect World*: Shi Hao's innate Supreme Bone with bone-text techniques; ch. 1996, after losing bones he forms an even stronger bone text. | From Foundation, one innate "Teknik Pusaka" for its main root element: METAL sword_qi, WOOD life_drain, WATER/ICE frost_lotus, FIRE flame_palm, LIGHTNING thunder_palm, EARTH mountain_stomp, WIND gale, SPACE space_prison. Power ×1.5, no slot, any Dao, cannot be taught. Kill XP ×1.25. At star 9 the bone "sheds": power ×2.25. |
| **Pupil Ganda** / Double Pupils (10) | *Perfect World*: Shi Yi's Double Pupils fire swords and beams of light. | 25% dodge against monster melee, technique damage ×1.2. Every 10 s, monsters within 16 get an eye-glyph marker for the master only (client FX). |
| **Nadi Dewa Sesat** / Heretic God Veins (8) | *Against the Gods*, ch. 45–47: Yun Che's veins look absolutely ordinary until all 54 entrances open; ch. 46: seven gates in the core of the veins, each opened gate raising the Heretic God Arts one realm, classed Berserk, and opening one too early can kill. | Shows as TRASH / Merah (red cap exempt). At the Golden Core breakthrough, a reveal cinematic: colour Nila with a special factor of 2.0. **Seven gates** open at Golden Core, Nascent Soul, Deity Transformation, Dao Union, Immortal Ascension layer 5, True Immortal and star 9. Names: Jiwa Sesat, Hati Membara, Api Penyucian, Langit Bergemuruh, Raja Neraka, Gerbang Keenam, Gerbang Ketujuh. Each gate +15% damage. "Ledakan Gerbang" (automatic below 50% HP in a boss fight, or ordered): 20 s at damage ×(1 + 0.25 × gates), then +30% damage taken and Weakness for 30 s, cooldown 5 min. Gate 7 unlocks Murka Raja Neraka (20.3.21). |
| **Alam Jiwa Tanpa Atribut** / Attributeless Soul Realm (14) | *Tales of Demons and Gods*, ch. 14: Nie Li's attributeless red realm suits the Heavenly God technique, slow at first, highest potential, able to wield techniques of every attribute. | XP ×0.8 before Golden Core, ×1.4 from Dao Union. Learns and casts techniques of every Dao (`daoBypass`). |

#### 20.3.6 Jenjang Sekte: ranks and promotions (module A)

**Sumber / Source.**
- *Renegade Immortal*: honorary disciples are a lower rank in grey.
- *A Record of a Mortal's Journey*: an unofficial disciple is retested after half a year. Ch. 871: the grand elder title "rightfully belongs to the strongest cultivator", offered to Han Li at mid Nascent Soul.
- *I Shall Seal the Heavens*, ch. 27–31: Outer and Inner Sects, with a special promotion training that admits one Outer disciple.
- *Battle Through the Heavens*: Outer and Inner Academy, and Grand Elder Su Qian presiding over the Inner Academy.
- *Martial Peak*: Yang Kai is a lowly Trial Disciple and floor sweeper.
- *Return of the Mount Hua Sect*: generations of disciples, instructors and elders, with the reborn junior Chung Myung drilling his seniors.
- *Soul Land*: two Titled Douluo protectors guard the Seven Treasure Glazed Tile Sect.

Rank is stored in `extra.sect.rank`.
- Shown as a nametag prefix "[Dalam] Lin Feng · Inti Emas", a robe trim colour (C) and a rank tile on the Status and Bakat tabs.
- Technique slots limit only *new* teaching; techniques already known are kept.

| # | Rank | Requirements to be promoted into it | Slots | XP factor (`rankFactor`) | Trim | Perks |
|---|---|---|---|---|---|---|
| 0 | **Murid Tercatat** (Registered) | joins via determination or the servant path | 2 | 0.55 | grey | orders only FOLLOW / MEDITATE plus duties (`modeBlocker`); ×1.2 duty output |
| 1 | **Murid Luar** (Outer) | Qi Refining, 30 PK, and ≥ half a recruitment cycle (36,000 ticks by default) since joining ("Uji Ulang", Fanren's retest) | 3 | 0.60 | white | all orders, mission tiers 1–2 |
| 2 | **Murid Dalam** (Inner) | Foundation, 150 PK, and a passed **Latihan Khusus** (20.3.15) | 5 | 0.70 | blue | missions up to tier 5, Void Realm, tower |
| 3 | **Murid Inti** (Core) | Golden Core, 600 PK, and a top-3 tournament finish or Void Realm floor 10 | all | 0.80 | jade | missions of every tier, can lead (+0.05 success) |
| 4 | **Tetua** (Elder; max 1, or 2 at hall level 5+) | Nascent Soul, 1,500 PK | all | 0.90 | violet | aura: disciples meditating within 16 get ×1.15 XP; teaches juniors techniques it knows, paid from 30% of its own qi (Mount Hua) |
| 5 | **Tetua Agung** (Grand Elder; max 1) | Nascent Soul Middle, the highest `DisciplePaths.power` among the master's disciples, and appointed by the master | all | 1.00 | white-gold | aura ×1.2 across the territory, +1 Formasi Agung layer |

**Designations** (independent of rank):
- **Pewaris** (heir, 20.3.20).
- **Pelindung Sekte** (Protector):
  - Max 1, or 2 at hall level 7+. Needs Dao Union or star ≥ 1.
  - Order GUARD_HALL. Regeneration II while in the territory. Formasi Agung damage ×1.5.

**Jenjang tablet.**
- A six-storey pagoda drawing with disciple seals on each floor. Clicking a seal opens a slip with the requirement checklist and the "Naikkan" / "Turunkan" talismans.
- Promotion costs the disciple's own PK. It plays a 3 s robe flash in the new trim colour, a bell, and a sect chat line.
- Demotion is free and refunds nothing; the floor is Tercatat.
- Advancements `first_promotion`, `grand_elder`.

**Migration** (phase-5 disciples with no `extra.sect`): Luar below Foundation, Dalam at Foundation–Golden Core, Inti from Nascent Soul. PK 0.

#### 20.3.7 Poin Kontribusi, Kantor Misi and Paviliun Yue Lu (module A)

**Sumber / Source.**
- *A Will Eternal*, ch. 9 and 27: Outer disciples bring spirit plants to the Missions Office, an elder grades them, and merit points are paid by quality. Merit buys scripture sermons, the Magical Arts Pavilion and the 10,000 Medicines Pavilion.
- *Martial Peak*: the Contribution Hall trades contribution points for herbs, pills and recipes.
- *A Record of a Mortal's Journey*, ch. 153: Yue Lu Hall stores manuals and pill recipes and supplies materials.
- *Battle Through the Heavens*: Fire Energy pays for tower training time (used in 20.3.15).

**PK** (integers) exist in two places: the hall pool `pool` (the master's) and each disciple's `extra.sect.pk`.

**Earning.**
- **Pool, Kantor Misi:** the master offers stacks from the inventory, and an elder line grades them ("Tetua menilai: kualitas tinggi"). Capped at 300 PK per in-game day from offerings.

  | Item | PK each |
  |---|---|
  | Glow berries | 0.5 |
  | Amethyst shard, golden carrot, gold ingot | 1 |
  | Glistering melon slice | 2 |
  | Emerald | 6 |
  | Diamond | 8 |
  | Echo shard | 15 |
  | Kultivator pill, quality 0 / 1 / 2 / 3 | 5 / 10 / 20 / 40 |
- **Pool, other:** expedition and event shares (B).
- **Disciple, kills:** `floor(victim max health × 0.05)`, minimum 1 for an `Enemy`, capped at 120 per disciple per in-game day.
- **Disciple, other:** missions, events and duties (B).
- **Anugerah Ketua:** moves pool PK to a disciple 1:1 (Martial Peak: disciples receive contribution points from the sect).

**Paviliun Yue Lu** (spends pool PK; prices × the level factor; "needs" = minimum hall level):

| Item | Price | Needs |
|---|---|---|
| Pil Penyembuh (Healing Pill) | 40 | – |
| Pil Pengumpul Qi (Qi Gathering Pill) | 60 | – |
| 8 amethyst shards | 40 | – |
| Undangan Khusus (one extra candidate now, max 1 per day) | 150 | – |
| Ceramah Kitab (+5 comprehension for one disciple; AWE scripture sermons) | 150 | – |
| Pil Pembangunan Fondasi (Normal) | 250 | – |
| Reset the envoy cooldown | 300 | – |
| Gulungan Teknik (an enchanted book mapped to a chosen book technique, DESIGN 7) | 400 | Level 3 |
| Pil Penangkal Tribulasi (Tribulation Warding) | 600 | Level 3 |
| Pil Inti Emas (Golden Core Pill) | 800 | Level 4 |
| Pil Pembersih Sumsum (Marrow Cleansing) | 900 | Level 6 |
| Manual Tubuh Abadi (Undying Codex for one disciple, 20.3.17) | 1,200 | Level 3 |

Pills are created as the existing alchemy items with `kultivator:pill_quality` 1 (read-only use of the alchemy registry).

**UI (Perbendaharaan tablet).** Left: Kantor Misi. The master's accepted stacks appear as seal cards showing their PK value; one click offers the stack (C2S with the slot index; no container menu). A daily cap bar shows 0/300. Right: Paviliun seal cards with price and lock reason.

#### 20.3.8 Seni Sekte yang Hilang: the lost sect arts (A items and restoration, C effects)

**Sumber / Source.**
- *Return of the Mount Hua Sect*: Chung Myung recovers Mount Hua's lost Twenty-Four Plum Blossom Sword (sword qi blooms as plum blossoms) and the Purple Mist Divine Art.
- *Legend of the Northern Blade*: Jin Mu-won learns the disbanded Northern Heavenly Sect's arts from fragmentary writings his father left.
- *Volcanic Age*: the regressed elder uses techniques from the sect's hidden records.

**Item.** `lost_scripture_page`, *Lembar Kitab yang Hilang*, with component `kultivator:scripture_page {set 1–3, piece 1–5}`.

**Loot** (built-in tables only): `stronghold_library` 25%, `woodland_mansion` 20%, `ancient_city` 15%, `trial_chambers/reward_ominous` 10%, `desert_pyramid` and `jungle_temple` 8%.

**Restoration.** Five different pieces of one set, with a Paviliun Kitab built: Perbendaharaan → "Pulihkan Seni". Pages consumed, +500 renown, advancement `lost_art_restored`. The art is hall-wide for disciples of rank Dalam and above:
- **Pedang Dua Puluh Empat Bunga Plum:** every 4th melee or Sword-technique hit sets off a petal storm (r 4, 1.2 U, `cherry_leaves` particles). At rank Inti, every 24th hit is "Mekar Penuh" (24 petals, r 6, 2 U).
- **Seni Ilahi Kabut Ungu:** qi regeneration ×1.3, max qi +20%, purple mist while meditating.
- **Warisan Sekte Langit Utara:** +20% damage at night and in snowy biomes.

#### 20.3.9 Tugas Sekte and Lumbung Sekte: duties and the storehouse (module B)

**Sumber / Source.**
- *Renegade Immortal*: Wang Lin's chore of at least ten vats of water a day.
- *A Record of a Mortal's Journey*, ch. 150–151: Han Li takes the unclaimed Hundred Medicine Garden task, run by the Foundation cultivator Ma, and secretly ripens herbs there.
- *A Will Eternal*, ch. 2 and 20–26: Bai Xiaochun starts at the Ovens kitchen, cooking spirit rice in woks engraved with earthflame formations. He steals Spirit Tail Chickens; their burnt tails make the three-coloured flame used for spirit enhancement; he proposes the "chicken thief task force" himself. (Flame colours belong to enhancement, so the kitchen has none.)

**Posts.** `posts(level)` posts sit at fixed spots 5 blocks N/E/S/W of the stele (the first free one). On the Tugas tablet the master assigns a disciple to a duty. It walks to the post (STAY + activity 1–4) and loops a cosmetic pose (C renders: carrying a bucket, kneeling, stirring, scattering feed). No block or crop is touched.

**Output.** Computed from elapsed game time every 60 s while the hall chunk is loaded, and when the hall opens. At most 24,000 ticks are banked per post. Goods go to the 27-slot virtual **Lumbung Sekte** (take-out only, by clicking on the Tugas tablet; overflow is lost with a warning line).

| Duty | Output |
|---|---|
| **Pikul Air** (water) | +0.8 XP per minute (`XpSource.SECT`); "Tubuh Terlatih" +1 permanent max HP per 60 min (max +10); +10 disciple PK per 50 min ("ten vats"); +1 Poin Tempa per minute (C, Undying Codex) |
| **Kebun Seratus Obat** (garden) | 1 ingredient per 4 min (glow berries, golden carrot, glistering melon slice or sweet berries, equal weights); ×1.5 with a Wood root or the Life Dao; ×1.25 at hall level 5+ |
| **Dapur Api** (the Ovens) | 1 `spirit_meal` (*Hidangan Roh*) per 6 min; ×1.5 with a Fire root or Fire Dao |
| **Peternakan Ayam Ekor Roh** (chicken farm) | needs ≥ 4 chickens within 16 of the stele (read-only count at computation time); 1 `spirit_tail_feather` (*Bulu Ekor Roh*) per 10 min |

**Hidangan Roh** (spirit meal).
- Food: nutrition 8, saturation 1.0.
- The player gets +8 XP (`XpSource.PILL`) and +10 qi.
- Fed to a disciple (`feedHandler`): XP ×1.2 for 2 min (`extra.activity.meal_until`).

**Easter egg.** A Takut Mati disciple on any duty has a 5% daily chance to eat up to 3 feathers from the storehouse. Chat "Siapa pencuri ayam roh?!" and hidden advancement `chicken_task_force` ("Satgas Pencuri Ayam").

#### 20.3.10 Ekspedisi: missions, envoys, the Primordial Ark and the Kunpeng bone (module B)

**Sumber / Source.**
- *Battle Through the Heavens*: Xiao Yan trains in the Magical Beast Mountain Range and hunts magic cores.
- *Soul Land*: spirit masters travel into the Star Dou Great Forest and the Hunting Spirit Forest for beasts of a chosen age, usually escorted.
- *Coiling Dragon*, Book 19: in the Planar Battlefield, badges from kills count as merits exchanged at a stele (the mission reward model).
- *I Shall Seal the Heavens*, ch. 19: each April, Outer disciples above the fifth level go out and each brings back two or three talented youths (the envoy).
- *Perfect World*, ch. 258–347: the Kunpeng's primordial bone is split into six parts that give only about a third of the legacy; the true technique lies in the Devil Refinement Cave and needs a higher realm to use fully.
- *Against the Gods*, ch. 454: the Primordial Profound Ark opens for a single day right after the Seven Nation tournament. Its boarding rule here is game design.

**Board** (Misi tablet). `missions(level)` offers rolled at sunrise. T* = `clamp(1 + strongest disciple's realm ordinal, 1, 9)`; offers use tiers T*−1, T*, T*+1, clipped to each type's range.

| Mission | Tiers | Duration | Requirement |
|---|---|---|---|
| Patroli Wilayah | 1–3 | 12,000 ticks | Luar+ |
| Mencari Herba Roh | 1–5 | 12,000 ticks | Luar+ |
| Mengawal Karavan | 2–6 | 24,000 ticks | Luar+; tier ≥ 3 needs Dalam+ |
| Perburuan Binatang Iblis | 3–8 | 24,000 ticks | Dalam+ |
| Alam Rahasia | 6–9 | 48,000 ticks | an Inti+ leader at Nascent Soul+ |

**Departure.**
- 1–3 disciples leave with a cloud-step FX and a pillar.
- `Disciples.depart` stores them, then `Halls.storeAway(..., "expedition", info)`.
- They tick zero while away, the timer runs on game time (offline too), and the disciple list shows "Sedang Ekspedisi · sisa 0,4 hari".

**Resolution** happens when a record is due: on the hall tick, when the hall opens, or when the master joins.
- Member power `m = realm ordinal + stage/4 + layer/9 + stars/9`.
- Party `P = max m + 0.5 × Σ others`.
- Difficulty `d = tier + (tier == 9 ? stars of the strongest member / 9 : 0)`.
- **Success** = `clamp(0.45 + 0.12 × (P − (d − 1)) + 0.05 (if an Inti+ member is present), 0.10, 0.95)`.

| Result | Each member | Hall |
|---|---|---|
| **Success** | `DisciplePaths.grantBarShare`: 15% (tier 1–3), 25% (4–6), 35% (7–9), +10% for Alam Rahasia; 10 × tier PK | pool +5 × tier; renown +10 × tier; loot to the storehouse (below) |
| **Failure** | returns at 30% HP with a 3-minute Qi Deviation and half the XP share; no PK or loot; **never dies** | – |

**Loot by the highest tier:**
- Tier 1–2: 2–6 bones, string or iron ingots.
- Tier 3–4: 2–5 gold, emeralds or lapis.
- Tier 5–6: 1–3 diamonds, 2–4 ender pearls, 1 golden apple.
- Tier 7–8: 1–2 echo shards, 10% netherite scrap, 15% an enchanted book mapped to a book technique.
- Tier 9: as tier 7–8 plus 1% nether star.
- From tier 4: 20% one Qi Gathering Pill.
- Mencari Herba Roh doubles the garden ingredients.

"Panggil Pulang" recalls a team at once with nothing gained.

**Utus Perekrut / envoy.**
- One Luar+ disciple at Qi Refining Middle or higher, away 24,000 ticks.
- Once per 5 in-game days per hall (reset for 300 PK).
- Brings 1 (30%), 2 (55%) or 3 (15%) candidates via `Halls.addCandidates`. Each has 30% for +1 root grade, 50% to share the envoy's main element, and a talent chance of 50% instead of 40%.
- The envoy earns 40 PK and a 10% bar share.

**Tulang Kunpeng.**
- Each Alam Rahasia success has an 8% chance of `kunpeng_bone_fragment` (component `kultivator:kunpeng_piece` 1–6, random piece).
- Six *different* pieces → Misi tablet "Satukan" → pick an Inti+ disciple → `DisciplePowers.teachKunpeng` (20.3.16).

**Bahtera Purba** (ghostly ark).
- For one in-game day after each tournament, a ghostly ark floats over the hall (client FX, no blocks).
- Only that tournament's top 3 may board, once, for 24,000 ticks.
- Guaranteed: 1 mapped enchanted book, 1 Kunpeng piece the hall lacks (random if none is missing), and a 60% bar share.
- Advancement `primordial_ark`.

#### 20.3.11 Tanah Terlarang Berwarna Darah: the Blood-Colored Forbidden Ground (module B)

**Sumber / Source.** *A Record of a Mortal's Journey to Immortality*, ch. 155–160:
- An ancient wind restriction weakens for five days once every five years.
- A second restriction admits only Qi Condensation disciples.
- The ground supplies the main herbs for Foundation pills, fewer than a third return, and Han Li goes in to refine his own pills.

**Schedule.** Every `forbiddenGroundIntervalDays` (15) at hall level 2+. Title "Segel angin melemah!" and crimson wind around the stele for a 1-day window.

**Entry.** Up to 3 disciples at **Qi Refining only**. Others are refused with the tooltip "Pembatasan kedua menolak Fondasi ke atas". They are away 6,000 ticks (the five days).

**Success chance per disciple** = `0.30 + 0.08 × stage + 0.05 × root grade + 0.05 × min(3, attack techniques known) + 0.10 (if not alone)`, at most 0.85.

| Result | Effect |
|---|---|
| **Success** | 2–5 `foundation_herb` (*Herba Fondasi*) to the storehouse, 25% bar share, 40 PK, renown +30 |
| **Failure** | "Terluka Parah": 10-minute Qi Deviation and −30% bar, **never death**. Only when the master has Hardcore on: it returns as its Soul Tablet in the storehouse (revivable as usual). |

**Murnikan Herba** (Tugas tablet): 3 herbs → 1 Foundation Establishment Pill. Quality: Low 30 / Mid 45 / High 20 / Perfect 5. Leftovers kept.

A jade report scroll lists every result. Advancement `forbidden_ground_survivor`.

#### 20.3.12 Duels, Kompetisi Daftar Kuat and Turnamen Elit (module B on `DuelRules`)

**Sumber / Source.**
- *A Will Eternal*: the Outer Sect Chosen battles; the north bank takes most of the top ten, and winners are in line for the Legacy Echelon.
- *Battle Through the Heavens*, ch. 566–573: the Strong Ranking competition runs elimination rounds down to a top ten.
- *I Shall Seal the Heavens*, ch. 27–31: the promotion training ends in a final bout announced by the Grand Elder.
- *Against the Gods*, ch. 434–436 and 1359: the Blue Wind and Seven Nation ranking tournaments against rival nations' geniuses.
- *Soul Land*: the Shrek Seven Monsters win the Continental Elite competition and bring Shrek fame.
- *Return of the Mount Hua Sect*: victories over Zhongnan spread Mount Hua's name.

**Kompetisi Daftar Kuat** (internal).
- **Schedule:** every `tournamentIntervalDays` (7) at noon, or from the Arena tablet (cooldown 3 days).
- **Needs:** ≥ 3 Luar+ disciples in the territory and the master within 32 blocks. Otherwise it waits for the next noon; "Lewati minggu ini" skips it.
- **Bracket:** up to 8 of the master's disciples (strongest first; the master can untick some). Empty slots up to the next power of two are filled with **Murid Tepi Utara** rivals (AWE north bank).
- **Rivals:** role RIVAL, never saved. Name from a 24-name list. Realm rolled at the entrants' median ±1 stage. Melee plus two `RivalSkills`: a 12-block qi line of 1 U every 6 s, and an element burst of r 3 and 1 U every 10 s. A rival is spawned only for its own duel.

**Turnamen Elit** (every 3rd Kompetisi).
- Adds 3 visiting champions of rival sects, ranked at your best entrant −1 stage, ±0 and +1 stage.
- Renown ×2.5. Opens Bahtera Purba.

**Duel rules.**
- One duel at a time inside a particle ring of r 9 at the stele.
- Ends at 10% HP (clamp, never lethal) or after 60 s, when the higher HP fraction wins. Being outside r 11 for 3 s is a loss.
- Taught techniques are allowed; `mayHit` accepts the partner only.
- Underdog: +15% damage per realm below the opponent (max +45%; `damageFactor`).
- Spectators sit (activity 8).
- A vanilla `ServerBossEvent` shows the pairing and both HP bars. One FX per hit.

**Rewards.**

| Place | Reward |
|---|---|
| 1st | 300 PK; title "Nomor Satu Daftar Kuat" for 7 days (gold name, +10% technique damage); 40% bar share. In a Turnamen Elit also the title "Juara Peringkat" and boarding the Ark. |
| 2nd | 150 PK, 25% bar share |
| 3rd | 75 PK, 15% bar share |
| Every entrant | 20 PK, 5% bar share, +2 comprehension, +3 bond per won bout |

**Daftar Kuat:** an all-time top-10 list on bamboo slips in the Arena tablet. Advancement `tournament_champion`.

**Performance.** At most 2 fighting entities; the bracket advances every 3 s.

#### 20.3.13 Perang Seratus Dinasti: the Hundred Dynasties War (module B)

**Sumber / Source.**
- *Martial Universe*: the Hundred Dynasties War brings youths of a hundred dynasties to fight; champion Lin Dong is taken into the Dao Sect.
- *Battle Through the Heavens*: Jia Nan Academy accepts only a region's best youths.

**Arrival.** Replaces every 4th arrival day at hall level 3+. 8 candidates arrive.

**The war.** The master presses "Mulai Perang" on the Calon tablet any time that day. It is a 4 + 2 + 1 single-elimination bracket among candidates (temporary `DuelRules` pairs between CANDIDATEs, 30 s bouts, the 10% HP end, base stats, particle flourishes).

**Champion.** Root +1 grade (max HEAVENLY), a 50% physique roll, Tekad 100. Recruitable free as Murid Luar starting at Qi Refining Early with bond 20. Finalists stay recruitable normally until sunrise. A bamboo-slip bracket is shown. Advancement `hundred_dynasties_champion`.

#### 20.3.14 Alam Dewa Hampa: the Void God Realm, an endless trial tower (module B)

**Sumber / Source.**
- *Perfect World*, ch. 339–345: cultivators fight in spirit in the Void God Realm; injuries carry over to the real body as an aftereffect; the lowest level is the Starting Ground, with a long stone stairway upward. Also the Devil Refinement Cave (ch. 261).
- *Tales of Demons and Gods*, ch. 48: the Heavenly Sacred Border, passed by only three in centuries, marks Glory City's brightest talent.

**Entering.** Arena tablet → pick a disciple of rank Dalam or higher → "Masuk Alam Hampa".
- It sits at the stele; its body gets the translucent "tubuh roh" tint (activity 7, rendered by C).
- A **phantom** (role PHANTOM, dark tint, red eyes) appears 4 blocks away inside an 8-block ring. `DuelRules` pairs them.

**Phantom on floor f.**
- HP `20 × 1.12^f`, damage `3 × 1.10^f`, armor `min(20, f/3)`.
- From floor 10 it casts one `RivalSkill` every 8 s.

**Floor names.**

| Floors | Name |
|---|---|
| 1–9 | Tanah Permulaan |
| 10–19 | Tangga Batu |
| 20–29 | Gerbang Awan |
| 30–39 | Istana Hampa |
| 40–49 | Puncak Hampa |
| 50 | Batas Suci |
| 51–59 | Di Atas Batas |
| 60 | Gua Pemurnian Iblis |
| 61+ | Hampa Tanpa Dasar N |

**Climbs.** One per disciple per in-game day, up to 5 floors in a row, with a 3 s pause and a 25% heal between floors. One phantom per hall at a time; climbs queue.

**Rewards.**
- First clear of floor f: bar share `4% × (1 + f/20)`.
- Every 10th first clear: +2 comprehension.
- Floor 50: permanent title "Jenius Batas Suci" (XP ×1.1). Advancement `batas_suci`.
- Floor 60 first clear: Sayap Kunpeng reaches full power (20.3.16).
- A repeat clear gives 25% of the first-clear share.

**Loss.** The phantom fades and the real body gets "Luka Jiwa" for 5 min: −20% damage and no kill XP. Nobody dies, and the climb ends.

**UI.** A best-floor leaderboard of all disciples on the Arena tablet. Advancement `void_floor_10`.

#### 20.3.15 Latihan Khusus and Menara Langit Membara (module B)

**Latihan Khusus / promotion trial** (Luar → Dalam).

**Sumber / Source.**
- *I Shall Seal the Heavens*, ch. 27–31: a special promotion training admits a single Outer disciple into the Inner Sect.
- *Battle Through the Heavens*: the Inner Academy selection.

- **Start:** A calls `SectEvents.startPromotionTrial(disciple, master)` once the other requirements are met. The disciple must be at the stele.
- **Waves:** three waves at 0, 20 and 40 s from `DaoProtectors.spawnWaveFor(level, disciple, master, Rank(Foundation), size 2/3/4, healthFactor 1.6)`. Overworld types only, the usual hardening.
- **Pass:** the disciple survives 60 s without dropping below 25% HP, and the master deals no damage to the trial mobs ("Guru membantu — ujian batal" fails it).
- **Fail:** the mobs vanish with a poof; retry after 1 in-game day.
- **Result:** `SectRanks.onPromotionTrialResult(entity, passed)`. PK are spent only on a pass.

**Menara Langit Membara / Blazing Sky Qi Refining Tower.**

**Sumber / Source.** *Battle Through the Heavens*, ch. 491, 549, 622:
- Training rooms over the sealed Fallen Heart Flame; Heart Flame grows stronger deeper down, nearer the flame.
- Students pay Fire Energy for time, and newcomers get "grilled".
- A monthly collective training day is free.

**Rooms.** Built by the player and detected read-only: a lit Soul Campfire standing on Crying Obsidian, within 32 blocks of the stele, at most 7 rooms.
- Sorted by **descending** Y: the highest room is floor 1 and the deepest is floor n.
- Cached; re-checked every 5 s only while someone trains.

**Training.** Arena tablet → "Latihan di Menara (lantai n)".
- Requires realm ordinal ≥ n − 1. The disciple walks there and meditates (activity 6).
- Meditation XP ×(1.2 + 0.2n) through an `xpFactor` on MEDITATION.
- Comprehension +0.02n per second.
- Cost: n of the disciple's PK per 1,200 ticks; training stops when PK run out.
- "Hari Latihan Bersama": free on day index `d % 7 == 3`.

**Heart-fire surge.** Every 60 s, chance 4% × n:
- comprehension ≥ 10n: the disciple resists, +2 comprehension and +0.01 to its next breakthrough quality (max +0.1),
- otherwise: 8% of max HP as damage (never below 50%) and thrown out for 30 s.

**FX.** Soul-flame motes around a training disciple (`SectEventFx`, scaled by quality).

#### 20.3.16 Jurus Pamungkas: signature ultimates, Heavenly Flames and Kunpeng Wings (module C)

**General rules.**
- Unlocked at **Nascent Soul** for the matching Dao.
- Cast automatically in combat (`combatCast`) when a boss or ≥ 3 monsters are within 12 blocks, or ordered with the "Jurus Pamungkas" talisman on the Bakat tab.
- Cooldown 120 s unless stated.
- One FxPayload per cast; server damage pulses every 10 ticks, each with a single AABB query.
- Monsters only. Never centred within 4 blocks of the master or a villager. Block raycasts are read-only `clip`.
- **U** = `DiscipleTechniques.power(data)` × every `damageFactor` provider (stars, hexes, folds, flames, titles).

| Dao | Ultimate | Sumber / Source | Effect |
|---|---|---|---|
| Pedang | **Formasi Pedang Geng Agung** | *A Record of a Mortal's Journey*: Han Li's 72 Azure Bamboo Cloudswarm Swords; Great Geng Sword Formation (Da Geng Jian Zhen) | 12 / 24 / 36 / 72 flying-sword particles (Nascent Soul / Deity Transformation / Dao Union / Immortal Ascension+; density by quality) form a 10-block dome net over the target for 6 s, 0.4 U per pulse to monsters inside |
| Petir | **Petir Pengusir Kejahatan** | Same work: Evil-Dispelling Divine Lightning carried by the Golden Thunder Bamboo | Golden particle bolts on up to 6 monsters, 2.5 U each (×1.5 against undead); each chains once to a monster within 6 for 1.25 U |
| Ruang | **Kurungan Larangan** | *Renegade Immortal*, ch. 216: Wang Lin's restrictions | Glyph cube of r 7 on the target; monsters inside get a transient −100% movement modifier and Weakness V for 4 s and take 1 U/s |
| Tubuh | **Tinju Kaisar Abadi** | *A Will Eternal*: the Undying Emperor's Fist, secret art of Undying Bones | One punch of 6 × its attack damage plus a 4-block shockwave of 2 U |
| Api | **Teratai Api Amarah Buddha** | *Battle Through the Heavens*: Xiao Yan's self-made lotus from fusing Heavenly Flames, more explosive with each flame added | 4 s charge, thrown ≤ 24 blocks; particle burst only (no `Explosion`), r 4 (r 6 with ≥ 2 flames), `3 U × max(1, flames)`, entity ignite 6 s; cooldown 45 s |
| Kehidupan | **Kitab Kehancuran Agung** (block-safe form) | *Martial Universe*: the Great Desolation Scripture gathers life force into one body | Drains monsters within 10 for 1.5 U each; heals the disciple 100%, the master and allied disciples within 10 by 30%; +30% damage for 30 s |
| Es, Bayangan | the Dao's book technique, amplified | *No new concept* (existing Glacier Prison / Gale) | +60% damage and ×2 radius |

**Fusi Api Guru-Murid.**

**Sumber / Source.** *Battle Through the Heavens*: the first lotus fused Xiao Yan's flame with his master Yao Lao's Bone Chilling Flame.

During the lotus charge, a Fire-Dao master within 6 blocks may press "Fusi" on the disciple screen: `5 U × max(1, flames)`, r 6, costing the master 30% of max qi.

**Benih Api Surgawi / Heavenly Flame seeds.** Item `heavenly_flame_seed`, component `kultivator:flame_kind`.

**Sumber / Source.** *Battle Through the Heavens*: the ranked Heavenly Flames (Green Lotus Core Flame, Bone Chilling Flame, Sea Heart Flame, Fallen Heart Flame, Pure Lotus Demon Flame), and the Flame Mantra that grows by devouring them.

Drops (an `ItemEntity` at the victim on `AFTER_DEATH`):

| Seed | Source | Chance |
|---|---|---|
| Api Inti Bumi Teratai Hijau | blaze killed by a player | 0.5% |
| Api Dingin Tulang | stray, killed by a player or a disciple | 0.3% |
| Api Hati Lautan | Elder Guardian | 100% on the first kill per player, then 25% |
| Api Jantung Jatuh | Wither killed by a player | 100% on the first kill, then 25% |
| Api Teratai Iblis Murni | Ender Dragon | 100% |

**Absorbing.**
- Who: a Fire-root or Fire-Dao disciple, or any disciple from Nascent Soul.
- The master feeds the seed (`feedHandler`), starting a 20 s ritual.
- Without Hu Fa (20.3.18) it fails 40% of the time: 3-minute Qi Deviation, seed returned.
- Each flame (max 3, +1 per 9 stars): fire technique damage +20%, melee ignites entities for 4 s, immune to fire and lava damage. Shown as coloured flame orbs in its aura.
- Advancements `heavenly_flame`, `buddha_lotus_fusion`.

**Sayap Kunpeng / Kunpeng Wings** (taught by the six fragments, 20.3.10; any Dao).
- Dash 12 blocks plus a wind crescent of 2 U on monsters along the path, cooldown 20 s.
- One third of full power (the six pieces give a third of the legacy) until Void Realm floor 60. Then: dash 24, 6 U.
- Advancement `kunpeng_wings`.

**Formasi Tujuh Peri.**

**Sumber / Source.** *Against the Gods*, ch. 739: the Frozen Cloud Seven Fairies, the sect's elite seven.

When 7 or more Inti+ disciples of one master fight within 16 blocks of each other, each deals +10% damage.

#### 20.3.17 Pusaka Natal, Kitab Abadi Tak Mati and Pelarian Jiwa (module C)

**Pusaka Natal / Natal Treasure and spirit enhancement.**

**Sumber / Source.**
- *A Will Eternal*, ch. 5, 29–38, 637: Bai Xiaochun's spirit enhancement gives silver sheen at one fold and later two- and threefold; elders stop at one or two folds because failure rises; his Eternal Parasol changes at its fourteenth enhancement; the three-coloured Spirit Tail flame is the fuel.
- *A Record of a Mortal's Journey*: the 72 swords are Han Li's life-bound natal treasure.

**Refining.** From Foundation, one virtual treasure per disciple (`extra.power.natal`, not an item).
- Form chosen once: Pedang, Payung, Wajan or Lonceng.
- Costs 1 diamond + 8 Bulu Ekor Roh, with the master inside the territory (Pusaka tab).
- Rendered on its back like the player's back sword (C edits the renderer).

**Enhancement, fold n** (max 18; 19 with Jari Penempa Roh).
- Cost: n feathers + `n × 40` pool PK.
- Success = `0.95 − 0.07 × (n − 1)`, +0.15 with Jari Penempa Roh, +0.05 Fire Dao, +0.05 hall level 5+, minimum 0.10.
- A failure at n ≥ 3 loses one fold (smoke and pop FX, no block).
- Each fold: +5% melee and technique damage.

| Fold | Effect |
|---|---|
| 3 | silver tint |
| 9 | gold tint, and the form ability unlocks |
| 14 | "Payung Abadi": the ability doubles and the treasure orbits the disciple |

Form abilities at fold 9:
- **Pedang:** +10% technique damage.
- **Payung:** every 60 s, the master and disciples within 4 get Absorption II for 10 s.
- **Wajan** (Bai Xiaochun's turtle-shell wok): −20% damage taken.
- **Lonceng:** Slowness II for 3 s on monsters within 6 on each of its casts.

Advancements `natal_fold_9`, `payung_abadi`.

**Kitab Abadi Tak Mati / Undying Codex.**

**Sumber / Source.** *A Will Eternal*: the Undying Live-Forever Codex's levels Undying Skin (Iron, Bronze, Silver, Gold Skin), Undying Tendons, Undying Bones (whose secret art is the Undying Emperor's Fist) and Undying Blood. The Heavenly King strength level is skipped as a simplification.

- **Access:** Manual Tubuh Abadi (20.3.7), or free for Body-Dao and Dantian Tersumbat disciples.
- **Poin Tempa:** 1 per 1% of max HP taken in combat (cap 200 per day; ×2 with Takut Mati), plus 1 per minute of Pikul Air.

| Stage | Points | Effect |
|---|---|---|
| Kulit Perak | 150 | armor +6 |
| Kulit Emas | 500 | armor +12, toughness +4, immune to entity fire, gold glint |
| Urat Abadi | 1,200 | speed +15%; dash to its target (8 blocks, cooldown 6 s) |
| Tulang Abadi | 2,500 | Tinju Kaisar Abadi for any Dao (cooldown 30 s outside the Body ultimate) |
| Darah Abadi 0–100% | 80 per 1% | regeneration +0.05 HP/s per 10%. At 100%, once per in-game day a lethal hit leaves it at 1 HP with Resistance V for 5 s and no tablet (`onDeath`). It then starts "Kitab Hidup Abadi": +1% max HP per star. |

Advancement `undying_blood`.

**Pelarian Jiwa Baru Lahir / Nascent Soul escape.**

**Sumber / Source.** *A Record of a Mortal's Journey*: a Nascent Soul flees as a streak of light when its body is destroyed.

A disciple at Nascent Soul+ that dies while its master's hall is active does not drop a tablet (`onDeath` returns handled):
- A glowing soul streak flies toward the stele (FX).
- `Halls.storeAway(..., "soul_flight")` holds it for 12,000 ticks.
- It re-forms at the stele with −10% bar and full health.

Below Nascent Soul, or with no active hall, the Soul Tablet rule of phase 5 applies.

#### 20.3.18 Ikatan, Segel Jiwa, Hu Fa and Fondasi Dao (module D)

**Ikatan / bond** (`extra.path.bond`, 0–100).

**Sumber / Source.** *Tales of Demons and Gods*: a new disciple forms a Soul Seal with the master for loyalty, and ch. 343 is titled "Taking in a disciple". Ch. 13: Nie Li matches each friend's technique to the attribute of their soul.

Gains:

| Action | Bond |
|---|---|
| Teaching a technique | +5 |
| Feeding a pill | +2 (max 10 per day) |
| Guarding its breakthrough or calamity | +10 |
| Meditating within 8 blocks of the master | +1 per minute (max 20 per day) |
| Each won tournament bout | +3 |
| Each Balas Budi rescue | +5 |

Bond resets on release.

- **Segel Jiwa / Soul Seal** (bond ≥ 50). Item `soul_seal_jade`, *Giok Segel Jiwa* (shapeless: amethyst shard + echo shard + gold ingot + paper). Sneak-use it on the disciple for a 5 s seal-glyph ritual.
  - Max sealed = `3 + floor(hall level / 3)` (3 without a hall).
  - A sealed disciple:
    1. can be recalled to the master from anywhere in the same dimension (Jalan tab, cooldown 60 s, safe spot only),
    2. gets +10% max health, damage and max qi,
    3. when the master drops below 30% HP: every sealed disciple within 48 gets Speed II + Strength I for 10 s and switches to ASSIST until the fight ends.
- **Element-matched teaching:** a technique whose Dao element is in the disciple's root costs 10% of the master's max qi instead of 20% (`teachCostFactor`). Its first 10 casts give double comprehension.

**Hu Fa / guarding the breakthrough.**

**Sumber / Source.**
- *Battle Through the Heavens*: Yao Lao protects and guides Xiao Yan through refining the Green Lotus Core Flame and the Fallen Heart Flame (护法).
- *A Will Eternal*: Foundation is Mortal-Dao (with a pill), Earth-Dao or Heaven-Dao (only by chance; Bai Xiaochun reaches it).
- *I Shall Seal the Heavens*, ch. 76 and 100: Foundation pillars graded by crack count (Flawless, Cracked, Fractured), and the crackless Perfect Foundation, extraordinarily rare; Meng Hao's has ten pillars.

**When it applies.** During any disciple breakthrough, calamity, soul-lamp trial or flame absorption, the master qualifies when within 8 blocks and meditating, or standing still while sneaking. Shown as a seal "Hu Fa aktif".

| Effect | Value |
|---|---|
| Strikes | ×0.8 |
| Quality | +0.1 |
| Foundation chance | +10% |
| Flame absorption | never fails |
| **Tahan Petir** (talisman on the Jalan tab or `/kultivasi murid tahan`) | Redirects the next strike: the master takes `0.5 × that strike's share × his max health` (tribulation damage source, clamped to keep 1 HP), and the disciple's strike is skipped. Max 3 per trial, 15% of max qi each. |

**Dao protectors for disciples** (Golden Core+ targets and calamities).
- Every 2 strikes: `spawnWaveFor(disciple)`, size `1 + target realm / 3`.
- Each protector the **master** kills makes the next strike ×0.92 (min ×0.6) and adds +0.05 quality (max +0.2).
- Other disciples nearby fight the protectors but never reduce strikes (DESIGN 19.5).

**Fondasi Dao / Foundation type.** Rolled on a successful Qi Refining → Foundation (`breakthroughListener.onEnd`, kind GATHERING). Shown as pillar art on the Jalan tab.

| Type | Base chance | Pillars | Effect |
|---|---|---|---|
| Fana (Mortal-Dao) | 60% | 9, fractured | – |
| Bumi (Earth-Dao) | 32% | 9, cracked | XP ×1.1, max HP +10% |
| Langit (Heaven-Dao) | 8% | 9, flawless | XP ×1.25, later strikes ×0.85, golden pillar in the aura |
| Sempurna (Perfect) | 0.01% (game value) | **10**, no cracks | XP ×1.4, first calamity at star 8 |

Odds shifts (taken from Fana):
- Master meditating within 8 during the gathering: +10% Bumi, +5% Langit.
- Master's own Foundation quality Perfect or Heavenly: +3% Langit.
- A Perfect-quality Foundation pill fed beforehand (`onFed` marker): +5% Langit.
- All three together: Sempurna becomes 2%.

Advancements `heaven_dao_foundation`, `perfect_foundation`, `disciple_guardian` ("Pelindung Dao Murid").

#### 20.3.19 Bintang Abadi: endless stars beyond True Immortal (module D)

**Sumber / Source.**
- *Renegade Immortal*, ch. ~1157–1159: Ancient Gods grow by stars and face Three Trials with Seven Calamities (2 + 2 + 3). Most meet the first at six stars, some at seven, very few at eight (Tu Si). Failing means death (here: injury). Wang Lin ends at Heaven Trampling.
- *Stellar Transformations*: stages Nebula, Meteor (1, 3, 9 golden granules), Star Core, Planet, Tribulation, Stellar, and beyond them Dark Star and Black Hole.
- *I Shall Seal the Heavens*, ch. ~1276–1362: Soul Lamps, about one per five Immortal meridians; Meng Hao has 33. Extinguishing a lamp absorbs its clone and greatly raises power. The bonus numbers here are game design.
- *Coiling Dragon*: Linley refines divine sparks into Divine Clones that fight beside him.
- *Battle Through the Heavens*: Dou grades in 1–9 stars.

**Condensing.**
- At True Immortal Peak with a full bar, every further XP goes to Stored Qi (shown as "Qi Bintang").
- Star n costs `cost(n) = min(5.0E9, 500,000 × 1.07^(n−1))`. Star count is an int with no cap.
- With "Otomatis" on (default) a star condenses whenever Stored Qi ≥ cost and no calamity gate blocks it; otherwise use the button.
- FX: silver motes collapse into a star that joins the orbit, plus a chime.

**Calamities.**
- Calamity k (1–7) gates star `s_k = f + 9(k − 1)`, with f = 6 (average quality Normal or lower), 7 (Perfect) or 8 (Heavenly, Sempurna Foundation or Darah Dewa Kuno). For f = 6 that is stars 6, 15, 24, 33, 42, 51, 60.
- Started by the master (Jalan tab); the star's cost is deducted at the start.
- Omen 6 s, then `6 + k` strikes of `(0.12 + 0.01 × trial) × max health`, one every 60 ticks, the last ×2. A would-kill strike means failure.
- Protector waves every 3 strikes: size `2 + k`, health ×(1 + 0.5k). Hu Fa applies.

| Result | Effect |
|---|---|
| **Pass** | The star condenses. Finishing a Trial (k = 2, 4, 7) multiplies all star bonuses by 1.1 (trial factor T, up to ×1.331). |
| **Fail** | "Luka Bencana" (Qi Deviation 12,000 ticks), no star, 50% of the cost refunded to Stored Qi |

After k = 7: title **Penginjak Langit** (Heaven Trampling), white-gold aura, server announcement.

**Per-star bonuses** (via the `stats`, `damageFactor` and `xpFactor` providers):

| Bonus | Formula |
|---|---|
| Max health | +4 × n × T (×1.5 with Darah Dewa Kuno) |
| Attack | +0.6 × n × T |
| Technique damage | ×(1 + 0.04 × n × T) |
| Completed 9-star stages | ×1.15 each to technique and melee damage |
| Max qi | ×(1 + 0.02n) |
| Meditation XP | ×(1 + 0.04n); ×2 under the open night sky (`canSeeSky` + night; Qin Yu absorbs star power) |

Health above the 1,024 attribute cap converts to damage reduction: +0.25% per 4 HP over, max 80%.

**Power curve** (technique damage factor; health for a disciple with a base of about 77 at True Immortal):

| Stars | 9 | 18 | 27 | 36 | 54 | 72 | 100 |
|---|---|---|---|---|---|---|---|
| Technique ×(1 + 0.04nT) × 1.15^stages | 1.56 | 2.37 | 3.33 | 4.80 | 8.36 | 14.8 | 29.4 |
| Max health ≈ | 113 | 156 | 196 | 251 | 338 | 460 | 609 |
| Next star cost | 0.86 M | 1.58 M | 2.90 M | 5.34 M | 18.0 M | 61 M | 406 M |

**Star income.** Every event reward is a share of `barSize`: `xpRequired` below True Immortal Peak, `cost(stars + 1)` after it. The climb therefore stays steady and never stalls, and the daily and weekly limits stop farming:

| Source | Share |
|---|---|
| Expedition success | 15–45% |
| Void Realm first clear of floor f | `4% × (1 + f/20)` |
| Tournament 1st / 2nd / 3rd | 40% / 25% / 15% |
| Primordial Ark | 60% |
| Meditation | as above |

Expect roughly 0.3–1 star per in-game day per active disciple.

**Titles** every 9 stars (Stellar Transformations; nametag "Lin Feng · Planet ★31"):

| Stars | Title | Visual |
|---|---|---|
| ★1–9 | Nebula | silver fragments orbit |
| ★10–18 | Meteor | 1, 3, then 9 gold granules at ★10, 13 and 18 |
| ★19–27 | Inti Bintang | gold core glow in the chest |
| ★28–36 | Planet | **Klon Ilahi** unlocks |
| ★37–45 | Tribulasi | |
| ★46–54 | Gemintang | sun-like aura; 2 clones |
| ★55–63 | Bintang Gelap | |
| ★64–72 | Lubang Hitam | |
| ★73+ | Tak Terbatas I, II, … (one numeral per 9 stars) | new aura hue per tier |

**Klon Ilahi / Divine Clone.**
- A role CLONE copy with 50% max health and attack, techniques at 50% U, lasting 60 s, cooldown 5 min.
- Automatic when a boss is within 24 or the disciple is below 50% HP in combat.
- Not saved, not listed, translucent.

**Lampu Jiwa / Soul Lamps** (after Penginjak Langit).
- One lamp lights per 10 stars, max 33.
- "Padamkan Lampu" is a calamity-engine trial with k = 8 + lamps already out.
- Reward per lamp out: +10% range on ultimates and Jurus Pemusnah, +5% damage.
- The Jalan tab shows a row of lamps.

**Server announcements** at ★36, Penginjak Langit and each 9th lamp.

Advancements `first_disciple_star`, `disciple_calamity`, `heaven_trampling`, `soul_lamp`.

**Performance.** Pure math; one calamity per master at a time. Orbit dots are client particles (9 per ring, 1 ring at Minimal, 3 at Maximal).

#### 20.3.20 Pewaris and the Nine Hexes, Cincin Jiwa Guru, Murid Melampaui Guru (module D)

**Pewaris and Sembilan Segel / the heir and the Nine Hexes.**

**Sumber / Source.**
- *I Shall Seal the Heavens*, ch. 102, 1379, 1534–1556: the League of Demon Sealers passes down one Demon Sealer per generation, and each generation must create a new hex (the Eighth Hex, Meng Hao's Ninth). A Mountain and Sea Lord must master all Nine Hexes.
- *Renegade Immortal*: Wang Lin inherits Tu Si's legacy and memories.
- *Martial Universe*: Lin Dong becomes a senior direct disciple of the Desolate Hall.

**Naming the heir.**
- Requirements: rank Inti+, Nascent Soul+, bond ≥ 80, sealed.
- 60 s ritual: master and disciple meditate within 4 blocks, costing the master 30% of max qi. Jade light links their dantians.

**The heir gets:**
- Every teachable technique the master knows, free.
- Comprehension `max(own, master's × 0.5)` in its own Dao.
- XP factor +0.05.
- Master-meditation share ×2 (20% instead of 10%; the direct disciple).

**Segel Generasi.** The master marks one of his techniques as the Generation Hex: the heir's version deals +50% and shows a seal ring.

**New hexes.** Each new realm the heir reaches, and every 9 stars, it creates a hex: the player picks one of its taught techniques without a hex, which gets +25% damage and the seal FX. Max 9.

**All nine:** title "Penguasa Sembilan Segel", the passive "Tersegel" (every 20 s its next hit pins monsters within 6 with Slowness V + Weakness V for 3 s), and Segel Gunung-Laut (20.3.21).

**Changing heir.** The old heir keeps its techniques but loses every hex (warning dialog). A lineage counter "Generasi ke-N" is shown in the tooltip.

Advancements `heir_named`, `nine_hexes`.

**Cincin Jiwa Guru / the Master's Soul Ring.** Item `master_soul_ring` (shapeless: gold ingot + echo shard + amethyst shard + ender pearl). It is given only to the heir, one per heir, and stays in its tablet if it falls.

**Sumber / Source.**
- *Battle Through the Heavens*: Yao Lao's soul lives in the ring, secretly absorbs Xiao Yan's Dou Qi for three years, then becomes his master, lending soul power or taking over in deadly fights.
- *Coiling Dragon*: Doehring Cowart's soul in the Coiling Dragon Ring teaches Linley and guards him in danger.

**Awakening.** For 72,000 ticks (3 in-game days) the ring takes 50% of the heir's meditation XP. Then it bonds (chat line, FX, advancement `master_in_ring`). After bonding:

| Ability | Effect |
|---|---|
| **Bimbingan Jiwa** | The master-meditation share works at any distance and in any dimension (`masterShareAnyRange`). |
| **Perlindungan Jiwa** | Once per in-game day a lethal blow leaves the heir at 1 HP with Resistance IV for 3 s and an old-sage ghost FX. |
| **Jiwa Guru Turun** | Automatic below 30% HP in combat, or ordered; master must be online in the same dimension; cooldown 10 min. For 20 s the heir uses the master's D (`Techniques.power`) for its techniques and may cast Buddha Palm and a 6-block Dao Domain pulse of the master's Dao on monsters, wrapped in an aura of the master's realm colour. Cost to the master: 40% of max qi up front plus 2 qi/s; Slowness I and no casting meanwhile. |

**Murid Melampaui Guru / the disciple surpasses the master.**

**Sumber / Source.**
- *Battle Through the Heavens*: Xiao Yan outgrows Yao Lao and later rescues him from the Hall of Souls.
- *Martial Peak*: Meng Wu Ya, keeper of the Contribution Hall, knew and healed the trial disciple Yang Kai, who later surpassed him.

**Trigger** (once per disciple):
- `DisciplePaths.power(disciple) = rank step + stars` exceeds the master's `rank step + extra.kultivasi.stars`, or
- the disciple wins **Tanding dengan Guru**: a `DuelRules` duel with the master, both ≥ 80% HP within 16 blocks, 120 s, ending at 10% HP; once per in-game day; both get +1 comprehension and +3 bond.

The trigger plays a golden pillar, a server chat line and advancement `disciple_surpasses_master`.

**Effects.**
- The disciple's XP factor +0.1.
- **Kebanggaan Guru:** the master gets +5% XP per surpassing disciple, max +15% (`Progression.addMultiplierProvider`).
- **Balas Budi:** once per in-game day, when the master is below 25% HP within 48 blocks in the same dimension, the disciple teleports to a safe spot beside him. It gives him Absorption equal to 25% of its own max health (amplifier `min(19, floor(0.25 × maxHP / 4) − 1)`) and Resistance II for 5 s, and taunts monsters within 12 for 8 s.
- **Tetua Kehormatan** (master's toggle): while meditating within 8 blocks of this disciple, the master gets 5% of its meditation XP through `Progression.addXp(…, XpSource.SECT)`.

#### 20.3.21 Jurus Pemusnah and MODE KEHANCURAN (module D; the only block-changing code of the mod)

**Sumber / Source.**
- *A Record of a Mortal's Journey*, ch. 2191/2238: the Xuantian Slaying Spirit Sword, a Xuantian treasure (the highest tier), overwhelmingly powerful and draining Han Li's mana. No "law of destruction" is claimed.
- *Battle Through the Heavens*: the Buddha's Fury Flame Lotus grows far more destructive with each flame fused into it.
- *Martial Universe*: the Great Desolation Scripture desolates a thousand miles around the user and gathers life force into one body; the Great Desolate Imprisoning Heavenly Hand (ch. 701); the Devouring Ancestral Symbol devours energy.
- *Soul Land*: the Clear Sky Sect's Disorderly Cloak Hammer Technique builds force over chained blows, and the Great Sumeru Hammer is its devastating ultimate.
- *Coiling Dragon*: Linley's earth mystery of the Pulse, vibration that shatters rock from within.
- *Against the Gods*, ch. 46: the seventh Heretic God gate; the fifth realm, Hell Monarch, is the last the blood memory names.
- *I Shall Seal the Heavens*, ch. 1556: the Lord of the Mountain and Sea Realm must master all Nine Hexes.
- *Renegade Immortal*: Heaven Trampling, beyond the three steps.

**Jurus Pemusnah / world-destroying ultimates.**
- Available to disciples that passed **calamity 4** (the Second Trial, star ≈ 33).
- One shared cooldown of 10 min per disciple.
- **Normal mode (default):** block-safe. Big FX plus monster damage only. May be cast automatically against a boss within 32, or ordered.
- **With all three locks open (below):** the *ordered* cast also changes blocks. Automatic casts always use the block-safe form.
- R = `kehancuranMaxRadius` (12). s = stars.

| Ultimate | Who | Mode Kehancuran shape | Normal mode (blocks untouched) |
|---|---|---|---|
| **Tebasan Xuantian** | Dao Pedang | trench along the aim line from 3 blocks ahead: 3 wide, 4 deep below the surface, length `min(48, 16 + s/2)` | sword light along the line: 8 U to monsters within 2 of it |
| **Teratai Api Amarah Buddha Agung** | Dao Api with ≥ 2 Heavenly Flames | sphere at impact, r `min(R, 4 + s/9)` | 8 U in r + 2, entity ignite 8 s |
| **Getaran Bumi** | Dao Tubuh | disc 3 deep below the surface, r `min(R + 4, 6 + s/6)`; natural blocks only (`base_stone_overworld`, `base_stone_nether`, `#dirt`, sand, red sand, gravel, end stone) | 6 U, launch 0.8, Slowness IV 4 s |
| **Kitab Kehancuran Agung** | Dao Kehidupan | surface withering in r `min(R + 8, 8 + s/6)`: grass block, podzol, mycelium and rooted dirt → coarse dirt; leaves, grass, ferns, flowers and vines removed; crops and farmland skipped; no holes | the block-safe form of 20.3.16 at 2 U |
| **Tangan Penjara Langit** | Dao Ruang | pit 9×9, depth `min(12, 3 + s/18)` | 8 U and held 5 s inside a 9×9 column |
| **Palu Agung Sumeru** | Dao Es, Petir, Bayangan, or no Dao | 9 build-up blows (a hammer glyph grows), then a sphere of r `min(R, 4 + s/9)` | 15 U in r + 4 |
| **Murka Raja Neraka** | Nadi Dewa Sesat with gate 7 | sphere r `min(R, 4 + s/5)` | 24-block nova of 6 U, crimson-sky FX |
| **Pusaran Melahap** | Pembawa Simbol Melahap | column r 3 from the target surface down 16 | vortex r 8: pull 4 s, 1 U/s |
| **Segel Gunung-Laut** | heir with all 9 hexes | nine spheres of r `min(4, R)` in a 3×3 grid 8 blocks apart | nine seal pulses of 3 U, r 4 |

Entity damage always goes to monsters only (`mayHit`). Players, pets, villagers and disciples are never hurt.

**Who can switch it on: three locks, all required.**
1. **Server / global.** `modeKehancuran` in `config/kultivator.json` (default **false**). Only an op changes it: `/kultivasi kehancuran izin on|off` (`LEVEL_GAMEMASTERS`) writes the file, or the file is edited. In singleplayer this needs cheats or the file.
2. **Per player: "Sumpah Kehancuran".** Opened from the Segel Merah tablet or `/kultivasi kehancuran sumpah`. On LAN each player swears only for their own disciples.
3. **Per disciple: "Lepaskan Segel".** On the Jalan tab, only for eligible disciples.

Locks 2 and 3 each open `OpenScreen.KEHANCURAN_CONFIRM`. Closing any lock is one click and takes effect at once. The player himself never gets destructive techniques.

**Warning UI** (`KehancuranConfirmScreen`, the deliberate blood-red variant of the scroll).
- Crimson parchment, black ink, red seal stamps, a slow heartbeat sound. Title "SEGEL MERAH — MODE KEHANCURAN".
- Lists exactly: shape and maximum radius, maximum blocks per cast, the protected zones, "Tidak ada drop" (or the drop %), the restore window.
- A checkbox talisman "Saya paham blok akan hancur", then a seal that must be **held** for 5 s (oath) or 3 s (disciple). Esc cancels.
- A released disciple shows a red seal on its nametag and red sparks in its aura.

**Ordering a destructive cast.**
1. Jalan tab → "Jurus Pemusnah": the master aims within 10 s (ray ≤ 64 blocks, read-only).
2. Server validation (below), then a preview outline of the shape (client FX).
3. Clickable chat confirm "[Ya, hancurkan]" within 15 s.
4. A dimension-wide warning title and red-sky countdown `kehancuranWarningSeconds` (30): "<murid> akan melepaskan <jurus> di X Y Z". "[Batalkan]" works until zero.
5. The cast. Logged in the Catatan tablet and in the server log as `[Kultivator] Kehancuran: master/disciple shape dim x y z → N blocks`.

**Protected (a cast whose shape touches a zone is refused; positions inside a zone are always skipped):**
- within `kehancuranHallSafeRadius` (64) horizontal blocks of **any** Sect Hall stele, active or not;
- within `kehancuranPlayerSafeRadius` (24) of the master or any player (checked at the start and re-checked for each tick's batch);
- world spawn protection (the server's radius, at least 16);
- within 32 blocks of any villager or iron golem, or of any registered protective formation lodestone;
- in the End, within 160 blocks of (0, 0);
- unloaded chunks (dropped, never force-loaded).

**Never touched:**
- bedrock and anything with hardness < 0;
- **every block with a block entity** (chests, barrels, furnaces, beds, signs, spawners, beacons, lecterns, banners and so on);
- portal blocks and frames, obsidian, crying obsidian, reinforced deepslate, lodestone, respawn anchor, the stele;
- fluids and waterlogged blocks;
- the datapack tag `#kultivator:indestructible`.

**Execution (`WorldBreaker`).**
- One running job per server, at most 2 queued.
- Positions are ordered centre-out in shells for spheres and columns, top-down for trenches, pits and discs, and cut at `kehancuranMaxBlocksPerCast` (4,096), so a capped cast still looks like a crater.
- Each server tick (END_SERVER_TICK) removes at most `kehancuranBlocksPerTick` (48) positions across all jobs with `setBlock(air, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS | UPDATE_SKIP_ON_PLACE)` (2|16|32|512). In 26.3 flag 2 alone still runs the neighbour shape updates, which pop torches and signs, start fluids and drop sand; flag 16 skips them, so there are no cascades. Gravity blocks may float until touched.
- A position next to a fluid or a block entity, or next to an unloaded chunk, is never removed (a later neighbour update could let water or lava in or pop a sign).
- Disciples (the caster too), tamed pets and spirit beasts are a protected zone of 6 blocks, like villagers, so none of them falls into a pit.
- The job pauses while `server.getAverageTickTimeNanos()` > `kehancuranMsptPause` (45 ms).
- No fire, no `Explosion`, no item entities.
- **Drops:** none by default. With `kehancuranDropsPct` 1–10, each removed block has that chance to drop its resources, at most 64 stacks per cast.

**Pulihkan (restore).** A safety rule, not a story feature.
- Each cast keeps its removed states in memory, up to the cast cap.
- For `kehancuranUndoMinutes` (10) the master can press "Pulihkan" (Segel Merah tablet or chat).
- It restores at the same budget and the same flags, only where the position is still air, and only for casts with 0% drops.
- A restore ignores the protected zones (it only puts back what the cast took). It waits for unloaded chunks and for anyone standing in a block, and hands those blocks back to the buffer with the original expiry, so "Pulihkan" can be pressed again. Closing a lock or switching the mode off never stops a running restore.
- The buffer is lost on server restart.

Advancements `destruction_oath`, `world_breaker`.

**Performance.** About 48 block changes per tick at most, light updates included. Zero cost while idle.

#### 20.3.22 Visual summary (client, all scaled by Effect Quality)

| Module | Visuals |
|---|---|
| A | candidate mist arrival; test tiers and Duan pillar in the soul colour; recruit ring; promotion robe flash; hall level-up pillar; lost-art restoration glyphs; Formasi Agung layer tints in `FormationFx` |
| B | departure and return cloud-step; duel ring and per-hit sparks; Void portal swirl and phantom dissolve; crimson wind for the forbidden ground; ghostly ark over the hall; heart-fire motes in the tower; duty puffs |
| C | robe trim by rank; nametag prefix; soul-colour aura tint; candidate, rival and phantom tints; translucent spirit body and clones; duty poses; natal treasure on the back (silver, gold, orbit); flame orbs; Poison Body haze; dragon form; golden-body glint; Double Pupils eye glyphs; gate veins; every ultimate's particles |
| D | star orbit and Stellar stage effects; calamity omen and strikes (reusing the TribulationFx look); soul lamps; seal glyphs; heir dantian link; ring ghost; surpass pillar; Tahan Petir arc; the Kehancuran red sky, shape preview, impact dust and restore shimmer |

### 20.4 Modules, file ownership and contracts

Four agents work in parallel after 20.2. **Never edit a file owned by another module.** Anything missing goes into the final report as a TODO.

#### 20.4.1 P6-aula (module A): 20.3.1–20.3.8 (generation and the hall side)

**Owns (new):**
- `sect/hall/*`: `Halls` (init, tick, actions, commands, info composition), `HallData` (SavedData `kultivator:sect_halls` on the overworld), `HallBlock`, `HallItems` (stele block and item, `immortal_ascension_token`, `lost_scripture_page` + component `kultivator:scripture_page`), `HallLevel`, `Candidates`, `Talents`, `SectRanks`, `Contribution`, `GrandFormation`, `LostArts`, `AwayStore`.
- `client/hall/*`: `HallClient`, `HallScreen`, `AulaPage`, `CalonPage`, `JenjangPage`, `TreasuryPage`, `LogPage`, `CandidateCard`, `HallFx`.
- Assets: block model, blockstate and textures; item textures; `tools/gen_hall_textures.py`; recipe `sect_hall_stele.json`; the block loot table.
- `lang-src/hall.*`.
- Advancements `sect_gate_open`, `first_recruit_hall`, `determined_disciple`, `first_promotion`, `grand_elder`, `hall_level_5`, `hall_level_9`, `lost_art_restored`, `immortal_token`.

**May edit:** `formation/ProtectiveFormations.java`, `formation/Formation.java`, `client/fx/FormationFx.java`, `client/screen/HelpPage.java`. It adds help topics `aula`, `calon`, `jenjang`, `kontribusi`, `tugas`, `misi`, `turnamen`, `alam_hampa`, `bakat`, `pusaka`, `bintang`, `ikatan`, `kehancuran`; each module writes its own topic texts.

**Commands:** `/kultivasi aula info|nama <nama>`; debug `aula renown <n>`, `aula pk <n>`, `aula calon datang [n]`, `aula calon perang` (the 8 war youths now), `aula level`.

**May slip to 6b:** lost arts, token.

#### 20.4.2 P6-kegiatan (module B): 20.3.9–20.3.15

**Owns (new):**
- `sect/event/*`: `SectEvents`, `Duties`, `Storehouse`, `Expeditions`, `Envoys`, `PrimordialArk`, `ForbiddenGround`, `DuelArena`, `Tournament`, `HundredDynasties`, `Rivals`, `RivalSkills`, `VoidRealm`, `PromotionTrial`, `BlazingTower`, `SectEventItems` (`spirit_tail_feather`, `foundation_herb`, `spirit_meal`, `kunpeng_bone_fragment` + component `kultivator:kunpeng_piece`).
- `client/sectevent/*`: `SectEventClient`, `DutyPage`, `MissionPage`, `ArenaPage`, `BracketView`, `SectEventFx`.
- Assets and `tools/gen_sectevent_textures.py`.
- `lang-src/sectevent.*` (rival and rival-sect name lists in both languages).
- Advancements `first_expedition`, `secret_realm`, `primordial_ark`, `forbidden_ground_survivor`, `tournament_champion`, `hundred_dynasties_champion`, `void_floor_10`, `batas_suci`, `chicken_task_force` (hidden).

**May edit:** nothing outside its own files (it uses the spine's `DuelRules` and `spawnWaveFor`).

**Commands:** debug `event turnamen mulai`, `event perang mulai`, `event hampa <floor>`, `event terlarang buka`, `event misi selesai`.

**May slip to 6b:** Menara Langit Membara, Perang Seratus Dinasti.

#### 20.4.3 P6-kekuatan (module C): effects of 20.3.4, 20.3.5 and 20.3.8; 20.3.16, 20.3.17; the disciple visuals

**Owns (new):**
- `disciple/power/*`: `DisciplePowers` (init, provider registration, actions, commands), `TalentEffects`, `Physiques`, `HereticGates`, `SpecialCandidates`, `Ultimates`, `HeavenlyFlames` (+ `heavenly_flame_seed`, component `kultivator:flame_kind`), `NatalTreasure`, `UndyingCodex`, `SoulFlight`, `KunpengWings`, `SectArts`, `SevenFairies`, `SwordSense`.
- `client/disciple/power/*`: `PowerClient`, `TalentTab`, `TreasureTab`, `PowerFx`, `PhysiqueVisuals`.
- Assets and `lang-src/dpower.*`.
- Advancements `rare_physique`, `heretic_gates`, `signature_ultimate`, `heavenly_flame`, `buddha_lotus_fusion`, `natal_fold_9`, `payung_abadi`, `undying_blood`, `kunpeng_wings`, `beggar_revealed`.

**May edit:** `client/disciple/DiscipleRenderer.java`, `DiscipleRenderState.java`, `DiscipleRobeLayer.java`, `DiscipleHeldItemLayer.java`, `DiscipleLook.java`, `StatusTab.java`, `client/fx/DiscipleFx.java`, and `disciple/DiscipleTechniques.java` (only to add `castInnate(entity, technique, target, powerFactor)` and a range factor; targeting rules unchanged).

**Commands:** debug `murid fisik <id>`, `murid bakat <id>`, `murid lipat <n>`, `murid api <kind>`.

**May slip to 6b:** Formasi Tujuh Peri, Indra Pedang.

#### 20.4.4 P6-jalan-abadi (module D): 20.3.18–20.3.21

**Owns (new):**
- `disciple/path/*`: `DisciplePaths`, `Stars`, `StarTitles` (pure functions, readable by C), `Calamity`, `SoulLamps`, `DivineClones`, `FoundationTypes`, `HuFa`, `Bond`, `SoulSeal` (+ `soul_seal_jade` + recipe), `Heir`, `MasterRing` (+ `master_soul_ring` + recipe), `Surpass`, `DestroyerArts`.
- `kehancuran/*`: `Kehancuran`, `WorldBreaker`, `Shapes`, `ProtectedZones`, `KehancuranLog`.
- `client/disciple/path/*`: `PathClient`, `PathTab`, `PathFx`.
- `client/kehancuran/*`: `KehancuranClient`, `RedSealPage`, `KehancuranConfirmScreen`, `KehancuranFx`.
- `data/kultivator/tags/block/indestructible.json`.
- `lang-src/dpath.*`, `lang-src/kehancuran.*`.
- Advancements `first_disciple_star`, `disciple_calamity`, `heaven_trampling`, `soul_lamp`, `heaven_dao_foundation`, `perfect_foundation`, `disciple_guardian`, `soul_seal`, `heir_named`, `nine_hexes`, `master_in_ring`, `disciple_surpasses_master`, `destruction_oath`, `world_breaker`.

**May edit:** nothing outside its own files.

**Commands:** `/kultivasi murid tahan`; `/kultivasi kehancuran sumpah|status|pulihkan`; op `kehancuran izin on|off`; debug `murid bintang set <n>`, `murid bencana mulai`, `murid ikatan <n>`.

**May slip to 6b:** soul lamps, Klon Ilahi.

#### 20.4.5 Public API contracts (stubbed by the spine, filled by the owner)

| Owner | Signature |
|---|---|
| A | `Halls.hallOf(UUID) → @Nullable HallView` (record: pos, dim, level, renown, radius, active, name) |
| A | `int Halls.level(UUID)`, `int Halls.cap(UUID)`, `boolean Halls.inTerritory(UUID, Level, Vec3)` |
| A | `Halls.addRenown(UUID, int, String reasonKey)`, `Halls.addPool(UUID, int)`, `boolean Halls.spendPool(UUID, int)`, `Halls.log(UUID, Component)` |
| A | `CompoundTag Halls.section(UUID, String key)`, `Halls.setDirty()`, `Halls.addInfoProvider(BiConsumer<ServerPlayer, CompoundTag>)` |
| A | `Halls.storeAway(UUID owner, DiscipleEntity, long returnAt, String reason, CompoundTag info)`, `Halls.addReturnHandler(String reason, ReturnHandler)` (handler may change the stored disciple data before arrival and returns the arrival position), `List<AwayView> Halls.away(UUID)` |
| A | `Halls.addCandidates(UUID, List<CandidateSpec>)`, `int Halls.arts(UUID)` (bits 1 Plum, 2 Purple Mist, 4 Northern Heaven) |
| A | `Talents.soulColor(DiscipleData)`, `setSoulColor(DiscipleData, int)`, `talents(DiscipleData)` (bits), `physique(DiscipleData)` (0 = none), `special(DiscipleData)`, `tekad(DiscipleData)` |
| A | `SectRanks.rank(DiscipleData)`, `pk(DiscipleData)`, `addPk(DiscipleData, int, String)`, `onPromotionTrialResult(DiscipleEntity, boolean)`, `isProtector(DiscipleData)` |
| B | `SectEvents.startPromotionTrial(DiscipleEntity, ServerPlayer) → boolean`, `@Nullable String Duties.dutyOf(DiscipleData)`, `int VoidRealm.bestFloor(DiscipleData)`, `boolean VoidRealm.clearedDevilCave(DiscipleData)`, `Tournament.hasTitle(DiscipleData, String)`, `Storehouse.insert(UUID, ItemStack) → ItemStack rest` |
| C | `DisciplePowers.addTempering(DiscipleData, int)`, `int flames(DiscipleData)`, `int gates(DiscipleData)`, `teachKunpeng(DiscipleEntity) → boolean`, `boolean hasUltimate(DiscipleData)` |
| D | `int DisciplePaths.stars(DiscipleData)`, `int power(DiscipleData)`, `double barSize(DiscipleData)`, `double grantBarShare(DiscipleData, double fraction)` (EXACT, works without the entity; the caller calls `refreshSynced` when loaded), `int bond(DiscipleData)`, `addBond(DiscipleData, int, String)`, `boolean isHeir(DiscipleData)`, `int calamitiesPassed(DiscipleData)` |
| D | `StarTitles.title(int stars) → Component` |
| D | `Kehancuran.serverAllows()`, `playerSworn(ServerPlayer)`, `released(DiscipleData)`, `boolean ProtectedZones.blocked(ServerLevel, BlockPos)` |
| spine | `DiscipleHooks.*` (20.2.6), `DuelRules.*`, `Disciples.depart/arrive/createCandidate/recruitCandidate`, `DaoProtectors.spawnWaveFor`, `Progression.addMultiplierProvider` |
| existing, read-only | `DiscipleTechniques.power/mayHit/hostilesAround/cast`, `DiscipleCultivation.distribute/addXp/infoFor`, `Targeting.*`, `Techniques.power`, `Qi.*`, `SpiritRoots.roll`, `Sect`/`Sects` (name, membership), `ProtectiveFormations.insideActiveFormation`, alchemy items and `AlchemyComponents` |

#### 20.4.6 Data keys

**`DiscipleData.extra()`.** Each module writes only its own sub-compound; others may read.

| Sub-compound | Owner | Keys |
|---|---|---|
| `extra.sect` | A | `rank`, `pk`, `pk_day`, `pk_today`, `joined`, `protector` |
| `extra.talent` | A | `tekad`, `tested`, `colour`, `talents`, `physique`, `special {type, realm_r, revealed}`, `duan`, `age` |
| `extra.activity` | B | `duty`, `meal_until`, `void {best, day, devil}`, `titles {id: until}`, `tower`, `injury_until` |
| `extra.power` | C | `natal {form, folds}`, `codex {pts, stage, blood, day_pts, unbroken_day}`, `flames [kind]`, `gates`, `toxic`, `gold_stacks`, `gold_dmg`, `gold_hp`, `kunpeng {has}`, `bone_shed`, `dou_di`, `sword_kills`, `trained_hp` |
| `extra.path` | D | `stars`, `auto`, `calamity`, `trials`, `lamps_out`, `foundation`, `bond`, `bond_day {…}`, `sealed`, `heir {gen, hexes [id], gen_hex}`, `ring {stage, since}`, `surpassed`, `released`, `cd {…}` |

**Player `CultivationData.extra()`:** `hall {moved_day}` (A), `path {heir, generation, surpassers, sworn, ring_given}` (D).

**`HallData` per owner:**
- `core` (A): pos, dim, name, founded_day, renown, pool, pool_day, offered_today, candidates [records], last_arrival, envoy_day, arts, log [50], away [records], dormant.
- `activity` (B): storehouse [27], posts {id: soul}, board [missions], board_day, events {next_tournament, count, forbidden_open_until, hundred_counter}, ranking [10], void_board, ark {until, top3}.
- `kehancuran` (D): log entries. Undo buffers live in memory only.

### 20.5 Order of work and deliverables

1. Phase 5 is committed.
2. **P6-spine** (20.2).
3. Modules A, B, C and D in parallel. No Gradle. Each module returns a player-facing summary and a test checklist.
4. One compile step: `compileJava`, `compileClientJava`, `jar --no-daemon`. Never tests, `runClient` or `build`. Then a fix pass.
5. **P6-docs:** PANDUAN sections (including a clear Mode Kehancuran warning page), `docs/uji/FASE-6.md` from the module checklists, README.

### 20.6 Rejected and deferred ideas

| Idea | Decision | Reason |
|---|---|---|
| Soul Land martial souls, twin souls, aged spirit rings, spirit bones, fusion skills | Deferred to 6b (spirit ring visuals are the first candidate) | A whole second progression track with its own UI. The Wither-based spirit bone drop is impossible, because disciples never fight the Wither. |
| TDG Demon Spirit Fusion (seven slots) | Deferred to 6b | L effort, new tab, overlaps physiques. The attributeless physique keeps its "all Daos" part. |
| Martial Peak Small Universe, Apotheosis Hundred Refinements | Deferred to 6b | Item-sink power tracks that duplicate the star curve and the Undying Codex. |
| Coiling Dragon Dragonblood Warrior | Deferred | Duplicates the Taixu dragon form. |
| Martial Universe Four Halls | Deferred | An assignment layer on top of ranks; low singleplayer value. |
| TDG six apprentice classes | Rejected | Only three of the six classes are canon, and they overlap duties and ranks. |
| Soul Land Sea God Nine Trials and Hell Road | Deferred | The RI calamities already gate the stars. |
| SSS Tianyan assassin raids | Deferred to 6b | Needs assassin AI; the user plays alone. |
| Willow Deity, Fanren puppet guards | Deferred | New render or entity work; overlaps Formasi Agung and Pelindung. |
| Absolute Sword Sense sword memory on vanilla swords | Deferred | Hooks into player combat files owned by others; the Indra Pedang talent is kept. |
| AtG Overlord Pill and Stabilising Jade | Deferred | The alchemy recipes are owned by phase 4/7. |
| AWE "a bank win brings resources" bonus | Rejected | Not in the text. |
| Using AWE flame colours in the kitchen | Rejected | Those colours belong to spirit enhancement. |
| SSS champion admitted directly as Inner; Darah Es; Reincarnation Stone | Rejected | Weak or unconfirmed sources (one viewer review). |
| Pan's Gate pooling 20% of Fire Energy | Rejected | Unconfirmed; there is no automatic sect tax. |
| Martial Peak paying bout winners in contribution points | Rejected | Unconfirmed. |
| Separate currencies Energi Api and Lencana Jasa | Merged | One currency, Poin Kontribusi. |
| "Law of Destruction" (Xuantian), ISSTH "1 in 10,000", "+10% divine sense per lamp" | Not claimed | The values stay as plain game design. |
| Regressor's warning before the master's failing strike, Heart Demon token drop, master's tower-room bonus, Bakat Pil furnace bonus | Dropped | Need edits in player tribulation, trial, meditation or alchemy files owned by other modules. |
| Wilayah Bintang at ★9, ★27 "Kaisar" title ladder, "Suci Agung" | Dropped | Unsourced or invented. The Stellar Transformations ladder is used instead. |
| Gold or emerald recruitment fees | Rejected | Unsourced. Shrek's fee is paid by applicants, and Fanren's acceptance is free. Only the test shard and the 100 PK servant path cost anything. |
| Registered disciples counting as half, trial disciples outside the cap | Rejected | Simpler with one cap rule. |
| Red-colour cap at Foundation | Moved to Golden Core Peak | So the Heretic God Veins reveal can happen. |

## 21. Phase 6b — Pembangunan Sekte (sect construction)

**User decisions (2026-10-17), in order:**
1. "I get missions to gather resources to build the sect. I just deposit them and the sect builds itself slowly. At the start I choose a sect complex template (small, medium, large, very large). The sect has 9 tiers of size."
2. "The size grades are divided into 6 levels."
3. **Latest, overrides the earlier ones:** "Nine template sizes, then — and design the sect beautifully (*yang bagus*)."

**Result:**
- There are **nine templates**. The player chooses one when founding the sect, on the stele.
- Each template has **nine growth tiers**: T1 is the founding compound and T9 is the complete, magnificent sect of that size.
- The sect builds itself, gradually, from materials the player deposits.

---------------------------------------------------------------------------------------------------------------------

**User decision 4 (2026-10-17), binding for the whole flow:** "I want to build just by depositing the materials that are
needed; then the building builds itself automatically once the materials are enough."
- Depositing is the ONLY thing the player has to do. As soon as the next building's (component's) bill is fully covered by
  deposits, it is reserved and starts building **automatically**, with no button per building or tier, no confirmation, no
  timer and no renown gate. The only confirmation is the one-time founding confirm of the footprint (21.12).
- Builders (disciples), Misi Pembangunan and gathering expeditions are OPTIONAL helpers: they only make gathering easier or
  placement faster. With zero disciples and zero missions the sect still builds completely.
- The stele page and the HUD always show clearly what the next building still needs ("Masih kurang: 32 Batu, 12 Kayu...")
  and the deposit button/right-click takes the needed items from the inventory in one go.
- Once funded, a building rises at the pace of 21.15 (a few minutes per building, watchable); `sectBuildSpeed` (config,
  up to 4x) and the test commands (`uji sekte tingkat <n> instan`) make it faster.

### 21.0 Principles

1. **Blocks are placed only inside the confirmed footprint.** This is the second exception to DESIGN 0, and the user asked for it explicitly.
   - Pembangunan Sekte places blocks and levels terrain **only inside the footprint column the player previewed as a hologram and confirmed**. The column is the template-local rectangle of 21.1 times that template's height envelope.
   - `sect/build/engine/BuildPlacer` is the only new code that calls `setBlock`. The other caller in the mod stays `kehancuran/WorldBreaker`.
   - Natural blocks it moves are credited to the **Gudang Bahan Sekte** (21.13). They are never lost.
   - Player-made blocks, block entities and protected blocks are never removed silently.
   - Outside the footprint nothing changes, apart from the vanilla side effects named in the consent text (21.10).
   - Everything else in the mod stays fully block-safe.
2. **Beauty is a primary requirement.** The sect must look like a real classical Chinese sect from donghua and manhua art:
   - red lacquered pillars and teal painted beams;
   - curved dark-tile roofs with upturned eaves, double eaves on important halls;
   - white plaster walls on stone plinths, with gold and jade used sparingly;
   - a paifang gate, then long stone stairs and terraces rising along a symmetric axis toward the main hall;
   - asymmetric water gardens beside the axis: arched bridges, lotus ponds, waterfalls, a pagoda, lanterns, plum and azalea trees, bamboo, rock gardens and incense burners.
   - Larger templates add grandeur: several peaks, hanging islands chained to the ground, cloud-sea terraces and a sky-piercing pagoda.
   - The art rules in 21.5–21.8 are binding and are checked by `uji sekte audit`.
   - **Every tier must already look intentional and pretty, never like a construction site** (21.2).
3. **Just deposit.** Materials are the only gate. There is no renown gate and no timer. The sect builds itself at a slow pace the player can watch.
4. **One component at a time, fully paid.** A component starts only when its whole bill is reserved. Only one component per site is open at a time. The sect therefore never stands half-built waiting for materials.
5. **Laptop first.** The game must stay playable on a weak laptop that also runs Valorant.
   - Placement is strictly budgeted and done in bursts.
   - Chunks are never force-loaded, and there is an MSPT guard.
   - Ops are generated off the main thread.
   - The largest template (161×161) stays buildable over a long time (21.15).
6. **Source rule (20.1).** Every story idea cites a verified work and concept.
   - Real Chinese architecture (Yingzao Fashi, the Forbidden City, Jiangnan gardens and so on) is art direction and is **never presented as novel canon**.
   - Numbers, costs, UI and safety rules are game design.
7. **Singleplayer first.** A site belongs to one player. On LAN, sect mates get a read-only view and may deposit materials.
8. **Constants and text.** Every number is a `public static final` in its class. All text exists in `id_id` and `en_us`. All GUI is the jade scroll of DESIGN 5b.

**Sumber / Source (the phase as a whole):**
- *Return of the Mount Hua Sect*: the fallen, ruined Mount Hua is restored and its fame returns.
- *Perfect World*, ch. 551/558: Shi Hao vows to rebuild the ruined Heaven Mending Pavilion.
- *Martial Peak*: Yang Kai starts as a trial disciple and floor sweeper of High Heaven Pavilion. He founds High Heaven Palace in the Star Boundary's Northern Territory, and it later becomes High Heaven Cave Heaven, ranked first of the 36.
- *Battle Through the Heavens*: Xiao Yan co-founds Pan Men in the Inner Academy, then Xiao Gate in Maple City in the Black-Corner Region, then the Yan Alliance.

---------------------------------------------------------------------------------------------------------------------

### 21.1 The nine templates

**How the axes work:**
- Template-local axes: +z points to the gate (front), −z to the main hall, and +x is east.
- The stele is at (0,0,0). The plaza floor is at stele y − 1.
- Footprints are rectangles that lie deeper behind the stele than in front of it. Every width is odd, so the axis runs through the stele cell.
- The player can rotate the template in 90° steps. There is no mirror option, because a mirror would flip the Bagua trigrams and the plaque text.

**Table A: names and geometry**

| # | id | Nama (id_id) / en_us | Footprint W×D | Local bounds | Envelope above / below plaza | Max relief (P95−P5) | Earthwork cap (cut + fill) | Realm hint (plaque only) |
|---|---|---|---|---|---|---|---|---|
| 1 | `unranked` | **Sekte Tak Berperingkat** / Unranked Sect | 25×29 | x ±12, z −16..12 | +14 / −14 | 10 | 3,625 | Manusia Fana |
| 2 | `third_rate` | **Sekte Kelas Tiga** / Third-Rate Sect | 33×39 | x ±16, z −24..14 | +24 / −14 | 14 | 6,435 | Pemurnian Qi |
| 3 | `second_rate` | **Sekte Kelas Dua** / Second-Rate Sect | 45×51 | x ±22, z −32..18 | +36 / −18 | 18 | 11,475 | Pembangunan Fondasi |
| 4 | `first_rate` | **Sekte Kelas Satu** / First-Rate Sect | 57×63 | x ±28, z −38..24 | +40 / −18 | 22 | 17,955 | Inti Emas |
| 5 | `great` | **Sekte Agung** / Great Sect | 73×81 | x ±36, z −50..30 | +48 / −22 | 28 | 29,565 | Jiwa Baru Lahir |
| 6 | `holy_land` | **Tanah Suci** / Holy Land | 89×97 | x ±44, z −60..36 | +56 / −26 | 36 | 43,165 | Transformasi Dewa |
| 7 | `cave_heaven` | **Gua Surga Tanah Berkah** / Cave Heaven, Blessed Land | 109×117 | x ±54, z −72..44 | +62 / −26 | 44 | 63,765 | Penyatuan Dao |
| 8 | `ancient_lineage` | **Warisan Kuno Abadi** / Ancient Immortal Lineage | 133×141 | x ±66, z −88..52 | +70 / −30 | 52 | 93,765 | Kenaikan Abadi |
| 9 | `heavenly_court` | **Istana Langit** / Heavenly Court | 161×161 | x ±80, z −100..60 | +100 / −30 | 64 | 129,605 | Dewa Abadi |

**Table B: composition and perks**

| # | Archetype (unique per template) | Main hall | Dominant vertical (one only) | Boundary | Tower rooms | Qi seat tier | Gate altars (optional) | Disc: lodestone z / ring r | Territory R_t |
|---|---|---|---|---|---|---|---|---|---|
| 1 | courtyard hermitage (siheyuan) | Aula Pendiri, 3-bay xieshan, rank C | hero plum | bamboo fence → white wall | 3 (cellar) | 1 (the disc) | 0 | 2 / 4 | 24 |
| 2 | two courts climbing a slope | 3-bay xieshan on a +3 terrace | 3-floor square pagoda | white wall + wooden gate | 3 | 1 | 0 | 6 / 4 | 32 |
| 3 | Jiangnan water garden: horse-head gables, moon gates | 5-bay xieshan | 5-floor pagoda behind the pond | stepped white walls | 4 | 1 | 0 | 6 / 5 | 40 |
| 4 | martial mountain gate: long central stair | 5-bay double-eave xieshan on +8 | main hall at the stair head | red palace walls, 4 watchtowers | 4 | 2 | 0 | 6 / 5 | 46 |
| 5 | orthodox great sect: white marble terrace | 5-bay double-eave xieshan | Pagoda Kaca Tujuh Harta (7F) | red walls, 2 front towers | 5 | 2 | 0 | 6 / 5 | 58 |
| 6 | twin peaks: gallery bridge over a gorge | 7-bay double-eave wudian | main hall ↔ Inner hall (diagonal pair) | gate + boundary stones | 6 | 2 | 2 | 6 / 7 | 68 |
| 7 | lake valley: island pavilion, stone boat, grotto hill | 7-bay double-eave wudian on a peak | grotto hill + floating rocks | valley rim, no wall | 6 (in the grotto) | 3 | 4 | 6 / 8 | 80 |
| 8 | ancient clan citadel: moat, spirit way, three halls | 9-bay double-eave wudian | gate tower + Pagoda Kaca Sembilan Harta | dark citadel walls | 7 | 3 | 4 | 6 / 8 | 96 |
| 9 | five peaks above the clouds | 9-bay double-eave wudian on the main peak | summit Pagoda Kaca Sembilan Harta | gate + boundary stones | 7 | 3 | 4 | 6 / 9 | 108 |

**Size rules:**
- `R_t = max(|z_min|, x_half) + 8`. The hall's territory radius becomes `max(20 + 4L, R_t)` (21.17).
- The disc ring never passes through the stele, because the lodestone distance d never equals the ring radius r.

**Size perks** (game design; they use only existing mechanics):
- Tower rooms, Qi seat tier and gate altars as in Table B.
- Disciple cap +1 once T9 is complete, for templates 5–9. This stays inside the 20.1 entity budget.
- +1 candidate per arrival for templates 6–9 at tier ≥ 7. The maximum of 6 from 20.3.3 still holds.
- When T9 is complete for template 5 or higher, a server line: "Sekte ‹nama› (‹templat›) berdiri megah!".
- **Gate formation altars** are optional extra Protective Formations, each registered by the player. If no lodestone is deposited, the altar shows an empty socket and the tier still completes.

**Name decisions (lore pass).**
- Template 9 is renamed from the brief's "Sekte Abadi" to **Istana Langit**. No work has a sect grade called "Immortal Sect", and "Sekte Abadi" stays the phase-6 title.
- Template 8 keeps **Warisan Kuno Abadi** and is mapped to the "ancient Dao lineages" (古老道统) of *Perfect World*.
- Templates 2–4 use the genre convention 三流/二流/一流门派. It is not tied to one work.

**Sumber / Source per template:**
- **1:** *Renegade Immortal*, Er Gen's author's note: the Xuan Dao Sect is "小门小派，本不入流" (a small, unranked sect), and the Heng Yue Sect has "沦入末流" (fallen to the bottom grade). For the founding feel: *BTTH*, Pan Men is founded in the Inner Academy to protect new students.
- **2–4:** wuxia/xianxia genre convention of third-, second- and first-rate sects, for example in the Wuxiaworld translations of *Yuan's Ascension* and *Everlasting* (ch. 73). This is a genre label, not a single work's ladder.
- **5:** *Return of the Mount Hua Sect*: the Nine Great Sects and One Gang, and the Five Great Families. Mount Hua was once a member and was replaced by Hainan. Also *A Record of a Mortal's Journey*: Yue State's Seven Great Sects (越国七大派).
- **6:** *Shrouding the Heavens*: Holy Lands such as the Yaoguang Holy Land. *Against the Gods*, ch. 696: the Four Great Sacred Grounds, guardians of the Profound Sky Continent.
- **7:** *Martial Peak*, ch. 3831: the 36 Cave Heavens and 72 Paradises, the forces that dominate the 3,000 Worlds. Classical scheme of Cave Heavens and Blessed Lands: Sima Chengzhen's *Tiandi gongfu tu* (Tang) and Du Guangting's *Dongtian fudi yuedu mingshan ji* (901).
- **8:** *Perfect World*: the Longevity Clans and ancient Dao lineages among the top forces of the Nine Heavens Ten Earths. *BTTH*: the Eight Ancient Clans. *Shrouding the Heavens*: the ancient Jiang family.
- **9:** *Shrouding the Heavens*: Ye Fan establishes the Heavenly Court. The layout follows the rank-by-height rings of *A Record of a Mortal's Journey*'s Heavenly Star City, where Sage Mountain has 81 layers and higher rank lives higher.

---------------------------------------------------------------------------------------------------------------------

### 21.2 The nine growth tiers (shared by every template)

Each tier unlocks the same hall feature in every template. The construction tier then feeds the hall level (21.17).

| Tier | Name (id_id / en_us) | Always adds | Hall feature it backs | `bflags` set | How it looks when complete |
|---|---|---|---|---|---|
| T1 | **Peletakan Prasasti** / Founding Stele | Stele surround: an octagonal inlay ring at r 2 and 4 low stone lanterns, but no roof over the stele. Also: axis path and paved forecourt; **Aula Pendiri** on the axis; first dorm (from T2 in template 1); wooden inner gate or bamboo gateway; lantern posts; well with water vats (Pikul Air); **hero tree** | – | – | a small, lit compound in nature |
| T2 | **Api Dapur dan Kebun Obat** / Hearth and Herb Garden | Dapur Api kitchen (smoker, open hearth); Hundred Medicine Garden; Spirit-Tail coop; trees | duty stations for real (anchors) | – | a working household: hearth smoke, herb beds, plum trees |
| T3 | **Paviliun Kitab** / Scripture Pavilion | Library with ≥ 10 bookshelves and a lectern within 16 of the stele; covered galleries in templates 3–9; plaza paving completed | same as the scan's Paviliun Kitab | `B_PAVILION` | an enclosed scholarly court |
| T4 | **Tembok dan Air** / Walls and Waters | Alchemy hall (cauldron on a lit soul campfire); pond, lake or basins; boundary: wall + shanmen, or gate + boundary stones (6, 7, 9); the wall band at its final level | – | – | a walled sect with water, the classic first impression |
| T5 | **Formasi Agung** / Grand Formation | Bagua disc: lodestone, crying obsidian at ±4, amethyst at ±3, King Wen trigrams. It is placed **inactive**. Also corner or watch towers, and optional gate altars | Formasi Agung once the owner registers the lodestone with an amethyst shard (DESIGN 10, unchanged) | (`B_FORMATION` stays the real active-formation check) | the court floor becomes a ritual disc |
| T6 | **Aula Utama** / Main Hall | Main hall on its terrace (template 1: the **gilding tier** on its Aula Pendiri: plaque, chiwen, gold pearl, roof beasts, eave lanterns); paifang; bronze dings (one with signal smoke). The Aula Pendiri becomes **Kantor Misi** (plaque swap) | – | – | the full axis: paifang → gate → plaza → terrace → hall |
| T7 | **Lapangan Latihan** / Drill Ground | Drill ground and archery with ≥ 4 target blocks; bell and drum towers (templates 3–9); arena stage (templates 6–9); banners; galleries; Inner dorms | same as the scan's Aula Latihan | `B_DRILL` | disciples' life: drums, bell, targets |
| T8 | **Pagoda dan Leluhur** / Pagoda and Ancestors | Pagoda; ancestral hall with soul lanterns; meditation pavilion with its Qi seat; beast garden; bamboo; extra peaks (templates 6–9) | Qi seat tier 2 or 3 for the master | – | vertical grandeur |
| T9 | **Menara Langit Membara** / Blazing Sky Tower | Tower entrance with 3–7 rooms straight below. The finale: waterfalls, gold pearls, glowing plaques, eave lanterns. Templates 7–9 add the sky elements | same as the scan's Menara (≥ 3 rooms) | `B_TOWER` | the complete, magnificent sect of that size |

**Every tier must look finished.** These are binding rules for the blueprint designer:

1. **A component is one unit: plot terrain + structure + plot landscaping.** That means curb, planting, lights and the paving it reaches. There are no tier-wide earthworks ahead of the buildings; only the axis path may lead.
2. **A terrace belongs to the component of its first building.** For example, template 5's west +3 court comes with the pagoda at T8. An empty "stage" never waits for a later tier.
3. **Paving grows with use.** T1 paves the axis and a forecourt of about 11×9 around the stele and the hall front. The rest is lawn with stepping stones, paved by the tier that reaches it.
4. **The axis never dead-ends in a building lot.** Before T6 the path ends at a **screen-wall rock** (影壁). T6 keeps it as the foot of the terrace stair.
5. **The wall band reaches its final level at T4**: the wall line plus 3 cells inside. Unbuilt land inside stays wild woodland, which reads as the sect's "back mountain".
6. **Frontier rule.** Each component finishes the edges of the land it shaped with a curb, a moss slope with rocks, or a hedge.
   - Plots reserved for later tiers keep their natural terrain. At most, cheap stone paths connect the components.
   - Natural trees in garden and lawn plots are kept and planned around. Those in building plots are credited (21.10).
7. **Each tier ends with a "Hiasan Tingkat" ornament component** that adds lanterns, flowers and banners across existing parts. The whole sect looks upgraded, not just one plot.
8. **Build order inside every building** follows how Chinese timber frames are actually erected:
   1. platform and floor;
   2. all pillars rise together, layer by layer;
   3. beams and brackets;
   4. **the roof, eave ring first and ridge last**;
   5. walls, lattice, interior and lights.

   So a half-built hall reads as an open pavilion, never as scaffolding.
9. **Light swaps only, never hall swaps.** Allowed swaps: bamboo fence → wall (template 1), wooden gateway → stone paifang, lawn → paving, and ornaments added. A built hall is never torn down.

**Sumber / Source.** Hall features: DESIGN 20.3.1.
- *A Record of a Mortal's Journey*, ch. 153: Yue Lu Hall and its covering formation.
- *I Shall Seal the Heavens*, ch. 36–38: the Magic Pavilion.
- *Return of the Mount Hua Sect*: the drill ground.
- *BTTH*, ch. 491/549/622: the Blazing Sky tower.

Duty stations (20.3.9):
- *Renegade Immortal*: the water vats.
- *A Record of a Mortal's Journey*, ch. 150–151: the Hundred Medicine Garden.
- *A Will Eternal*, ch. 2 and 20–26: the Ovens and the Spirit Tail chickens.

Kantor Misi: *A Will Eternal*, ch. 9 and 27, the Missions Office. "The founding hall becomes Kantor Misi" is game design.

---------------------------------------------------------------------------------------------------------------------

### 21.3 Building lists per template and tier

Rank letters refer to 21.5. Every list implies the 21.2 rules: hero tree at T1, polish at each tier's end, frontier finishing.

**Template 1 — Sekte Tak Berperingkat (25×29)**

| Tier | Components |
|---|---|
| T1 | axis + forecourt paving; Aula Pendiri (13×7, rank C, glazed); bamboo fence + wooden gateway; 2 gate lantern posts; 2 water vats; hero plum |
| T2 | west dorm wing; SE Dapur Api; coop under the plum; herb beds; pine |
| T3 | east Scripture wing (14 shelves + lectern) |
| T4 | SW alchemy room; white plaster wall with tile cap replaces the fence; 2 lotus basins |
| T5 | formation disc (r 4) with trigrams |
| T6 | gilding tier: plaque, chiwen, gold pearl, roof beasts, eave lanterns; 1-bay stone paifang replaces the gateway; bronze ding |
| T7 | 4 framed target boards on the court faces of the alchemy room and kitchen; stone lanterns; pagoda-lanterns at the steps; scholar rocks |
| T8 | ancestral altar with soul lanterns in the hall's east bay; bamboo screens; bonsai; red carpet |
| T9 | 3-room Blazing cellar under the hall (spiral stair in the west bay); glowing plaque; palace lanterns; petals |

**Template 2 — Sekte Kelas Tiga (33×39)**

| Tier | Components |
|---|---|
| T1 | front court; Aula Pendiri (rank B); wooden gate + fence; lanterns; well + 2 vats; hero pine |
| T2 | east dorm; kitchen; herb garden; coop; trees |
| T3 | west Scripture Pavilion (16 shelves) |
| T4 | rear terrace +3 with the alchemy hall; small stream and lotus pond with a flat bridge; white wall + wooden gate house |
| T5 | disc (r 4) |
| T6 | main hall on the rear terrace (3-bay xieshan, rank D); red-wood paifang (1 bay, 3 roofs); ding |
| T7 | drill ground (4 targets); bell pavilion; side gallery; banners |
| T8 | 3-floor square pagoda (NW, with the pond in front); small ancestral shrine; bamboo |
| T9 | tower entrance + 3 rooms; gold ornaments; eave lanterns; trees |

**Template 3 — Sekte Kelas Dua (45×51)**

| Tier | Components |
|---|---|
| T1 | plaza; Aula Pendiri; Outer dorm; wooden chuihuamen; well + 4 vats; hero plum |
| T2 | kitchen court (kitchen, coop); herb garden; 6 trees |
| T3 | 2-storey Scripture Pavilion (24 shelves); covered galleries round the plaza |
| T4 | north terrace +3 with the alchemy hall; lotus garden with an arched bridge and waterside pavilion (水榭); stepped white walls with moon gates and leak windows; shanmen |
| T5 | disc (r 5); 2 front corner towers |
| T6 | 5-bay main hall (rank D); stone paifang; dings |
| T7 | drill ground (6 targets); bell and drum towers; linking galleries; rock garden; lantern rows; banners |
| T8 | 5-floor square pagoda behind the pond; ancestral hall; meditation pavilion (Qi seat tier 1); horse-head gables on the dorm and kitchen (light swap) |
| T9 | tower entrance + 4 rooms; finishing |

**Template 4 — Sekte Kelas Satu (57×63)**

| Tier | Components |
|---|---|
| T1 | plaza; Aula Pendiri; Outer dorm; inner gate; well; hero pine |
| T2 | kitchen court; stepped herb garden; 8 trees |
| T3 | 2-storey Scripture Pavilion (32 shelves); galleries |
| T4 | red palace walls, 3-bay shanmen and lions; side terrace +2 with the alchemy hall; lotus garden with a small waterfall off the retaining wall |
| T5 | disc (r 5); 4 xieshan watchtowers |
| T6 | **long central stair** (7 wide, 3 landings) to the main terrace (design +8, range +5..+10); 5-bay double-eave xieshan; paifang; dings |
| T7 | drill ground with weapon racks and banners (8 targets); bell and drum; climbing galleries; Inner dorm |
| T8 | 5-floor pagoda; ancestral hall; octagonal meditation pavilion (Qi seat tier 2); beast garden; bamboo grove |
| T9 | tower entrance + 4 rooms; finishing |

**Template 5 — Sekte Agung (73×81)** (plan and coordinates in 21.4)

| Tier | Components |
|---|---|
| T1 | plaza forecourt and axis; Aula Pendiri (15×7, rank B); Outer dorm (east); wooden chuihuamen; well pavilion + 4 vats; lantern posts; hero plum |
| T2 | walled kitchen court (Dapur Api, well, Spirit-Tail coop); stepped Hundred Medicine Garden; 8 trees and paths |
| T3 | 2-storey Scripture Pavilion (west, 40 shelves); covered galleries framing the plaza; plaza paving completed |
| T4 | red palace walls; 3-bay shanmen and lions; approach path; east +2 terrace with the alchemy hall; lotus pond with moon bridge |
| T5 | Bagua disc (r 5); 2 two-storey front corner towers |
| T6 | three-tier white marble terrace (+2 / +4 / +5) with dragon ramp; 5-bay double-eave xieshan (core 23×11, colonnade); 3-bay stone paifang; 2 bronze dings |
| T7 | archery (4 targets) and drill ground (4 dummies); bell and drum towers; climbing galleries to the east halls; rock garden; lantern rows; banners |
| T8 | Pagoda Kaca Tujuh Harta on the NW +3 court; ancestral hall on the NE +3 court; island meditation pavilion (Qi seat tier 2); zigzag stepping bridge; beast garden; bamboo grove |
| T9 | tower entrance with 5 rooms; waterfall into the pond; gold pearls, glowing plaques, eave lanterns; approach groves |

**Template 6 — Tanah Suci (89×97)** (no perimeter wall)

| Tier | Components |
|---|---|
| T1 | plaza; Aula Pendiri; Outer dorm; inner gate; well; hero pine |
| T2 | kitchen court; stepped herb garden; 12 trees |
| T3 | 2-storey Scripture Pavilion; galleries |
| T4 | gorge stream and lotus lake with bridge; alchemy hall on its terrace; 5-bay shanmen with short wall stubs ending in rock; boundary stones |
| T5 | disc (r 7) + 2 gate altars; 2 watch pavilions |
| T6 | main peak terraces (marble +3 / +6 / +9); 7-bay double-eave wudian; 5-roof stone paifang; dings |
| T7 | drill ground and arena stage (10 targets); bell and drum; galleries; Inner dorm |
| T8 | Inner peak (design +12) behind its own barrier with the Inner Disciples' Hall and dorm; covered gallery bridge over the gorge; Pagoda Kaca Tujuh Harta; ancestral hall; meditation pavilion (tier 2); beast garden; bamboo |
| T9 | tower with 6 rooms; cloud-sea terrace with a cliff pavilion (Hanging Temple style) over the mist valley; waterfall off the Inner peak; lanterns; gold |

**Template 7 — Gua Surga Tanah Berkah (109×117)** (no perimeter wall)

| Tier | Components |
|---|---|
| T1 | plaza; Aula Pendiri; Outer dorm; inner gate; well; hero pine |
| T2 | kitchen court; stepped herb fields; 16 trees |
| T3 | 3-storey Scripture Pavilion; galleries |
| T4 | Blessed Land lake (30–35% of the footprint) with rock banks; alchemy hall; nine-turn bridge to a waterside pavilion; **stone boat**; willows; 5-bay shanmen with wall stubs; boundary stones |
| T5 | disc (r 8) + 4 gate altars; 4 watch pavilions on the valley rim |
| T6 | straight marble bridge across the lake with a mid-lake pavilion; main peak +4 / +8 / +12; 7-bay double-eave wudian; 7-roof paifang |
| T7 | drill ground and arena (12 targets); bell and drum; galleries; 2 Inner dorms |
| T8 | Inner and Elders' peaks (+10); Inner hall; elders' residence; Pagoda Kaca Tujuh Harta; ancestral hall; meditation pavilion (tier 3); beast garden; bamboo; test path |
| T9 | **Cave-Heaven grotto hill** (about 15 high) with a carved cave gate; the 6 tower rooms inside and below it, within 12 horizontally; 3 chained hanging rocks over the lake with a carved turtle head under the largest; stepped cascade; mist |

**Template 8 — Warisan Kuno Abadi (133×141)**

| Tier | Components |
|---|---|
| T1 | plaza; Aula Pendiri; 2 Outer dorms; inner gate; well; hero pine |
| T2 | large kitchen court; herb garden; 20 trees |
| T3 | 3-storey Scripture Pavilion; galleries |
| T4 | dark citadel walls (deepslate bricks, 20% cracked) with a **3-storey gate tower** (城楼) and 2 side gates; moat with 3 bridges; imperial way; Menara Pil (3-floor alchemy tower); imperial-garden lake with bridges |
| T5 | disc (r 8) + 4 gate altars; 4 three-storey corner towers |
| T6 | **spirit way** of stone statues and ancient pines; three-tier marble terrace +3 / +6 / +9; 9-bay double-eave wudian; square pyramidal Central Hall; 7-roof paifang with a glazed screen wall |
| T7 | clan arena; drill ground (12 targets); 2-storey bell and drum towers; galleries; 2 Inner dorms |
| T8 | Grand Ancestral Hall (7-bay, soul lanterns) behind the three halls; Pagoda Kaca Sembilan Harta (9F, Porcelain-Tower style); imperial rockery hill with a summit pavilion; meditation pavilion (tier 3); beast garden; 2 elders' courtyards |
| T9 | tower with 7 rooms; 3 chained relic islands over the rear court; finishing |

**Template 9 — Istana Langit (161×161)** (no perimeter wall)

| Tier | Components |
|---|---|
| T1 | plaza axis and forecourt; Aula Pendiri; 2 Outer dorms; inner gate; well pavilion; hero pine |
| T2 | large kitchen court; stepped herb fields; 24 trees |
| T3 | 3-storey Scripture Pavilion; galleries |
| T4 | valley lake, stream and rock banks; island pavilion, zigzag bridge, stone boat; **Puncak Awan Harum** (pill peak, +8) with the Menara Pil; 5-bay shanmen with wall stubs; boundary stones; 3 arched bridges |
| T5 | disc (r 9) + 4 gate altars; 4 watch pavilions on spurs |
| T6 | sculpted main peak (keeps natural rock) with terraces +3 / +6 / +9 and marble +11 / +13; 9-bay double-eave wudian; grand 7-roof paifang; screen wall; nine-flight stair |
| T7 | arena, drill ground and archery (16 targets); 2-storey bell and drum; galleries; 2 Inner dorms |
| T8 | **Puncak Dalam** and **Puncak Tetua** (+14) with halls and dorms behind barriers; **Puncak Binatang** (+8, beast garden); Grand Ancestral Hall (+13); meditation pavilion (tier 3); 2 covered gallery bridges; bamboo grove and test path |
| T9 | tower with 7 rooms; summit Pagoda Kaca Sembilan Harta (base +18, top about +96); hanging-mountain archipelago on jade chains; arched sky bridge to the pagoda's 4th-floor balcony; round triple-eave **Aula Dao Langit** (Langit blue); peak waterfalls into the lake; valley mist |

**Sumber / Source.**
- **Rank by height**, so terraces and stairs climb toward the main hall:
  - *I Shall Seal the Heavens*, ch. 2–3: the Reliance Sect has four peaks. The Outer Sect lies at the foot between them, servants live halfway up, and the Inner Sect and elders live above, behind defensive spells. This is the source for "Inner Sect on upper terraces behind a barrier".
  - *A Record of a Mortal's Journey*: Heavenly Star City's Sage Mountain, 81 layers, higher rank lives higher.
- **Multi-peak sects:**
  - *I Shall Seal the Heavens*: the four peaks above.
  - *A Will Eternal*: the Spirit Stream Sect's peaks. Fragrant Cloud Peak makes the pills, which gives Puncak Awan Harum. Green Crest Peak is for sword cultivators.
- **Menara Pil:** *BTTH*, the Pill Tower, holy land of alchemists in Zhongzhou. Its shape is taken from the name.
- **Beast garden:** *A Record of a Mortal's Journey*, Spirit Beast Mountain, a Yue sect of beast tamers. Name only.
- **Test path:** *A Record of a Mortal's Journey*, ch. 1–5, the Seven Mysteries climb (20.3.3).
- **Hero plum:** *Return of the Mount Hua Sect*, the plum blossoms.
- **Pagoda names:** *Soul Land*, Ning Rongrong's Seven Treasure Glazed Tile pagoda evolving into the Nine Treasure one. It is a martial soul, not a building; the storey counts are design.
- **Floating islands:** *Liezi*, "Tang Wen", the classical Daoist myth behind Penglai in xianxia. Five drifting mountains, Penglai and Yingzhou among them, are held on the heads of fifteen giant turtles; the carved turtle head nods to this. The chains are the user's request and art direction, **not canon**.

---------------------------------------------------------------------------------------------------------------------

### 21.4 Site plans

**Legend:**

| Symbol | Meaning | Symbol | Meaning |
|---|---|---|---|
| `#` | wall | `W` | corner tower |
| `G` | gate hall (shanmen) | `g` | inner gate (chuihuamen) |
| `P` | paifang | `M` | main hall (lower-eave outline) |
| `E` | Aula Pendiri / Kantor Misi | `K` | Scripture Pavilion |
| `A` | alchemy hall | `D` | dorm |
| `k` | kitchen | `c` | coop |
| `w` | well pavilion or vats | `h` | Hundred Medicine Garden |
| `O` | pagoda | `T` | Blazing Sky Tower entrance |
| `Q` | pavilion | `Z` | ancestral hall |
| `Y` | beast garden | `B` | bell or drum tower |
| `X` | arena stage | `x` | target |
| `S` | stele | `L` | lodestone |
| `C` | crying obsidian | `a` | amethyst block |
| `o` | disc ring | `+` | duty post (kept open) |
| `~` | water | `b` | bridge |
| `v` | waterfall | `t` | trees |
| `@` | hero tree | `i` | bamboo |
| `R` | rockery or screen-wall rock | `I` | hanging island (high above) |
| `H` | hall on a peak | `u` | covered gallery |
| `=` | paving | `:` | raised terrace |
| `"` | terrace edge (retaining wall + balustrade) | `^` | stairs |
| `l` | lantern | `n` | stone lion |
| `d` | bronze ding | `f` | woodland or cliff at the rim |
| `j` | boundary stone pillar | `.` | lawn or garden ground |

**Template 1 — Sekte Tak Berperingkat, at T9 (1 char = 1 block)**

```
         |         |         |     (x = -12 .. 12; rows are z)
  -16  #########################   boundary: T1 bamboo fence -> T4 white plaster wall with tile cap
  -15  #ii.EEEEEEEEEEEEEEEEE.ii#   E Aula Pendiri (T1), eave outline x -8..8 z -16..-6; core 13x7 (pillars x +-3, +-6; z -14..-8)
  -14  #ii.EEEEEEEEEEEEEEEEE.ii#     3-bay [3,6,3] xieshan, rank C (the main hall of this sect, glazed); platform x -7..7 z -15..-7
  -13  #ii.EEEEEEEEEEEEEEEEE.ii#     T9: spiral stair in the west bay -> 3 rooms at y -5/-9/-13 under the hall
  -12  #ii.EEEEEEEEEEEEEEEEE.ii#   i bamboo screens in the north corners (T8)
  -11  #...EEEEEEEEEEEEEEEEE...#
  -10  #...EEEEEEEEEEEEEEEEE...#
   -9  #.R.EEEEEEEEEEEEEEEEE.R.#   R scholar rocks (T7)
   -8  #...EEEEEEEEEEEEEEEEE...#
   -7  #...EEEEEEEEEEEEEEEEE...#
   -6  #...EEEEEE^^^^^EEEEEE...#   ^ steps before the centre bay only; doors recessed 1 behind the front pillars (porch)
   -5  #..t..======+======..R..#   + north duty post stays open paving; w water vats (T1); t pine (T2)
   -4  #.....===w=====w===.....#
   -3  #DDDDD~~=========~~KKKKK#   D dorm wing (T2)      K Scripture wing, 14 shelves + lectern (T3)
   -2  #DDDDD~~==ooCoo==~~KKKKK#   ~ two lotus basins (T4)
   -1  #DDDDD===oo=a=oo===KKKKK#
    0  #DDDDD=+oo==S==oo+=KKKKK#   S stele (0,0,0); + E/W duty posts at x +-5
    1  #DDDDD==o=======o==KKKKK#
    2  #DDDDD==Ca==L==aC==KKKKK#   disc (T5): L (0,2), C at +-4, a at +-3, ring o r 4 (the stele sits inside the ring)
    3  #DDDDD==o=======o==KKKKK#
    4  #DDDDD==oo=====oo==KKKKK#
    5  #DDDDD===oo=a=oo===KKKKK#   a at (0,5) is also the floor of the south duty post
    6  #DDDDDl===ooCoo===lKKKKK#   l stone lanterns (T7)
    7  #hhhhh=============hhh@t#   h herb beds (T2)  @ hero plum (T1)
    8  #AxAAxAA..=====..kkxkkxk#   A alchemy room (T4)   k Dapur Api kitchen (T2)   x framed target boards on their court faces (T7)
    9  #AAAAAAA.l=====l.kkkkkkk#   l gate lantern posts (T1)
   10  #AAAAAAA..=====..kkkkkkk#
   11  #AAAAAAAt.=====.ckkkkkkk#   c coop under the plum (T2)
   12  #########P=====P#########   P gateway in the wall line: T1 wooden gate -> T6 1-bay stone paifang
```

**Template 5 — Sekte Agung, at T9 (1 char = 1 block)**

The levels are fitted to the terrain within these ranges:

| Area | Design level (range) |
|---|---|
| Approach and outer court | −1 (−3..0) |
| Plaza | 0, fixed |
| East and side terraces | +2 (+1..+4) |
| Main terrace | +2 / +4, hall platform +5 |
| Pagoda and ancestral courts | +3 |

```
             |         |         |         |         |         |         |         (x = -36 .. 36; rows are z)
  -50  #########################################################################   north wall (T4); no north corner towers: the pagoda (NW) and the ancestral court (NE) hold the corners
  -49  #""""OOOOOOO"""".......ttt.........ttt.........ttt......""""ZZZZZZZZZZZZ#
  -48  #"::OOOOOOOOO::".......ttt.........ttt.........ttt......":::ZZZZZZZZZZZZ#
  -47  #":OOOOOOOOOOO:".......ttt.........ttt.........ttt......":::ZZZZZZZZZZZZ#
  -46  #"OOOOOOOOOOOOO"."""""""""""""""""""""""""""""""""""""""":::ZZZZZZZZZZZZ#   main terrace (T6): +2 x -19..19 z -46..-19; +4 x -17..17 z -44..-22; hall platform +5 x -15..15 z -42..-24
  -45  #OOOOOOOOOOOOOOO."iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"":::ZZZZZZZZZZZZ#   i bamboo grove behind the main hall (T8)
  -44  #OOOOOOOOOOOOOOO.":""""""""""""""""""""""""""""""""""":"":::ZZZZZZZZZZZZ#
  -43  #OOOOOOOOOOOOOOO.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#   M main hall: core 23x11 (5 bays [4,4,6,4,4], pillars x +-3,+-7,+-11; z -38..-28),
  -42  #OOOOOOOOOOOOOOO.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#     colonnade 3 out, double-eave xieshan; lower eave x -16..16 z -43..-23 (drawn), upper eave 1 inside
  -41  #OOOOOOOOOOOOOOO.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#   O Pagoda Kaca Tujuh Harta (T8), octagonal, centre (-28,-42), floor-1 eaves 15 wide, on a +3 court
  -40  #OOOOOOOOOOOOOOO.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"uu":::ZZZZZZZZZZZZ#   Z ancestral hall (T8) on the NE +3 court, faces west to the axis: core 8x13, eaves x 24..36 z -50..-34
  -39  #OOOOOOOOOOOOOOO.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"uu":::ZZZZZZZZZZZZ#   u climbing galleries joining the east halls to the main terrace (T7)
  -38  #"OOOOOOOOOOOOO".":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"uu":::ZZZZZZZZZZZZ#
  -37  #":OOOOOOOOOOO:".":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#
  -36  #"""OOOOOOOOO""".":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#
  -35  #....OOOOOOO.....":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#
  -34  #ttt..~~b~~......":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":"":::ZZZZZZZZZZZZ#   b zigzag stepping bridge island -> pagoda court (T8)
  -33  #ttt~~~~b~~~~....":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""::::::::::::::"#
  -32  #ttt~~~~b~~~~~...":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""::::::::::::::"#
  -31  #.~~~~QQQQQ~~~~..":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""::::::::::::::"#
  -30  #.~~~QQQQQQQ~~~.v":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""::::::::::::::"#   v waterfall from a notch in the +2 retaining wall into the pond (T9)
  -29  #~~~QQQQQQQQQ~~~v":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""AAAAAAAAAAAAAAA#   Q island meditation pavilion, octagonal 9, Qi seat tier 2 (T8)   A alchemy hall (T4) on the east +2 terrace
  -28  #~~~QQQQQQQQQ~~~.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""AAAAAAAAAAAAAAA#
  -27  #~~~QQQQQQQQQ~~~.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""AAAAAAAAAAAAAAA#
  -26  #~~~QQQQQQQQQ~~~.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM":""AAAAAAAAAAAAAAA#   ~ lotus pond (T4), water surface -1; the pond lies between viewer and pagoda (foreground water)
  -25  #~~~QQQQQQQQQ~~~.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"uu"AAAAAAAAAAAAAAA#
  -24  #~~~~QQQQQQQ~~~~.":"MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"uu"AAAAAAAAAAAAAAA#
  -23  #~~~~~QQQQQ~~~~~.":"MMMMMMMMMMMMMM^^^^^MMMMMMMMMMMMMM"uu"AAAAAAAAAAAAAAA#
  -22  #.~~~~~~b~~~~~~..":""""""""""""""""""""""""""""""""""":""AAAAAAAAAAAAAAA#   b moon bridge to the island (T4)
  -21  #.~~~~~~b~~~~~~.."::::::::::::::::^^^^^::::::::::::::::""AAAAAAAAAAAAAAA#
  -20  #R.~~~~~b~~~~~..."::::::::::::d:::^^^^^:::d::::::::::::""AAAAAAAAAAAAAAA#   ^ dragon-ramp stair +2 -> +4 -> +5;  d bronze incense dings (T6, one signal smoke)
  -19  #...~~~~b~~~~.R..""""""""""""""""""""""""""""""""""""""""AAAAAAAAAAAAAAA#
  -18  #.....~~b~~.............^^^.......RRRRR.......^^^.......""""""""""""""""#   ^ side ascents 0 -> +2 at x +-10..12;  R screen-wall rock: the axis end before T6, stair foot after
  -17  #.......b...............^^^.......RRRRR.......^^^.......................#
  -16  #..........................EEEEEEEEEEEEEEEEEEE.......YYYYYYYYYYYY.......#   E Aula Pendiri (T1) -> Kantor Misi at T6: core 15x7 (3 bays [4,6,4]), eaves x -9..9 z -16..-6
  -15  #..........................EEEEEEEEEEEEEEEEEEE.......YYYYYYYYYYYY.......#
  -14  #..........................EEEEEEEEEEEEEEEEEEE.......YYYYtttYYYYY.......#
  -13  #..........................EEEEEEEEEEEEEEEEEEE.......YYYYtttYYYYY.......#   Y beast garden (T8)
  -12  #hhhh......................EEEEEEEEEEEEEEEEEEE.......YYYYtttYYYYY.#######   h stepped Hundred Medicine Garden (T2)   kitchen court (T2): k Dapur Api, w well, c Spirit-Tail coop
  -11  #hhhh......................EEEEEEEEEEEEEEEEEEE.......YYYYYYYYYYYY.#kkkk##
  -10  #hhhh......................EEEEEEEEEEEEEEEEEEE.......YYYYYYYYYYYY.#kkkk##
   -9  #hhhh......................EEEEEEEEEEEEEEEEEEE....................#kkkk##
   -8  #hhhh..KKKKKKKKKKKKK.......EEEEEEEEEEEEEEEEEEE.......DDDDDDDDDDD..#kkkk##
   -7  #hhhh..KKKKKKKKKKKKK.......EEEEEEEEEEEEEEEEEEE.......DDDDDDDDDDD..#kkkk##
   -6  #hhhh..KKKKKKKKKKKKK..uuu==EEEEEEE^^^^^EEEEEEE==uuu..DDDDDDDDDDD..#kkkk##   K 2-storey Scripture Pavilion, 40 shelves (T3)   D Outer dorm (T1)   u covered galleries (T3)
   -5  #hhhh..KKKKKKKKKKKKK..uuu===========+===========uuu..DDDDDDDDDDD..#....##
   -4  #hhhh..KKKKKKKKKKKKK..uuu=======================uuu..DDDDDDDDDDD..#....##
   -3  #hhhh..KKKKKKKKKKKKK..uuuTTTTT=============wwwwwuuu..DDDDDDDDDDD..#.w..##   T Blazing Sky Tower entrance (T9), 5 rooms straight below   w well pavilion + 4 vats (T1)
   -2  #hhhh..KKKKKKKKKKKKK..uuuTTTTT=============wwwwwuuu..DDDDDDDDDDD..#....##
   -1  #hhhh..KKKKKKKKKKKKK..uuuTTTTT=============wwwwwuuu..DDDDDDDDDDD..#....##
    0  #hhhh..KKKKKKKKKKKKK..uuuTTTTT=+====S====+=wwwwwuuu..DDDDDDDDDDD..#....##   S stele (0,0,0); + duty posts at 5 N/E/S/W (kept open)
    1  #hhhh..KKKKKKKKKKKKK..uuuTTTTT====ooooo====wwwwwuuu..DDDDDDDDDDD..#cccc##
    2  #hhhh..KKKKKKKKKKKKK..uuu========o==C==o========uuu..DDDDDDDDDDD..#cccc##
    3  #hhhh.................==========o===a===o==========...............#cccc##
    4  #hhhh...ttt......ttt..uuu=l====o=========o====l=uuu.........ttt...#cccc##
    5  #hhhh...ttt.R....ttt..uuu======o====+====o======uuu....ttt..ttt...#cccc##
    6  #hhhh...ttt.....Rttt..uuu======oCa==L==aCo======uuu....ttt..ttt...#######   disc (T5): L (0,6), C at +-4, a at +-3, ring o r 5 (never through the stele)
    7  #hhhh................@uuu======o=========o======uuu....ttt..............#   @ hero plum (T1)
    8  #hhhh.................uuu======o=========o======uuu.....................#
    9  #......============...uuu=======o===a===o=======uuu...============......#   archery (T7, 4 targets x at the west end)        drill ground (T7, 4 dummies x)
   10  #......============...uuu========o==C==o========uuu...============......#
   11  #......x===========...uuu=l=======ooooo=======l=uuu...============......#
   12  #......============...uuu=======================uuu...===x===x====......#
   13  #......x===========..............ggggggg..............============......#   g wooden chuihuamen (T1)
   14  WWWWWWW============.BBBBB........ggggggg........BBBBB.============WWWWWWW   B bell tower (T7)                                 B drum tower (T7)
   15  WWWWWWWx===========.BBBBB.........=====.........BBBBB.============WWWWWWW
   16  WWWWWWW============.BBBBB...GGGGGG=====GGGGGG...BBBBB.===x===x====WWWWWWW   G 3-bay shanmen on the red wall line z 20 (T4); W 2-storey corner towers (T5)
   17  WWWWWWWx===========.BBBBB...GGGGGG=====GGGGGG...BBBBB.============WWWWWWW
   18  WWWWWWW============.BBBBB...GGGGGG=====GGGGGG...BBBBB.============WWWWWWW
   19  WWWWWWW============.........GGGGGG=====GGGGGG.........============WWWWWWW
   20  WWWWWWW#####################GGGGGG=====GGGGGG#####################WWWWWWW
   21  ............................GGGGGG=====GGGGGG............................
   22  ................................n.=====.n................................   n stone lions (T4)
   23  ..................................=====..................................
   24  .........ttt.........ttt..........=====..........ttt.........ttt.........
   25  ........ttttt.......ttttt.......l.=====.l.......ttttt.......ttttt........   l lantern posts (T1, more at T7)   t pine and plum groves (T2/T9)
   26  .........ttt.........ttt..........=====..........ttt.........ttt.........
   27  ..................................=====..................................
   28  ................................l.=====.l................................
   29  ............................PPPPPP=====PPPPPP............................   P 3-bay stone paifang (T6), 8 blocks clear of the gate hall
   30  ............................PPPPPP=====PPPPPP............................
```

**Template 5 key coordinates** (template-local; these are the contract for the blueprint):

| Element | Bounds / position |
|---|---|
| Plaza | x −11..11, z −6..12. T1 paves the axis (x −2..2) and the forecourt x −5..5, z −6..2. |
| Disc | L (0,6); C (0,2), (0,10), (±4,6); a (0,3), (0,9), (±3,6); ring r 5 |
| Duty posts | (0,±5), (±5,0): air over walkable floor at stele height |
| Tower | entrance x −11..−7, z −3..1; 5 rooms with campfires at y −6, −10, −14, −18, −22 (deepest is 24 from the stele) |
| Well pavilion | x 7..11, z −3..1 |
| Aula Pendiri | core x −7..7, z −14..−8; platform x −8..8, z −15..−7; eaves x −9..9, z −16..−6 |
| Side ascents | x ±10..12, z −18..−17; screen-wall rock x −2..2, z −18..−17 |
| Main terrace | +2 x −19..19, z −46..−19; +4 x −17..17, z −44..−22; platform +5 x −15..15, z −42..−24 |
| Main hall | core x −11..11, z −38..−28; colonnade x ±14, z −41..−25; lower eave x ±16, z −43..−23; upper eave x ±15, z −42..−24 |
| Dings | (±6, −20) |
| Pond | ellipse centre (−28,−26), rx 7, rz 8, surface −1. Island pavilion: octagon at (−28,−27), 9 wide. Moon bridge x −28, z −22..−17. Stepping bridge x −28, z −34..−32. |
| Waterfall | (−20, −30..−29) |
| Pagoda | court x −35..−21, z −49..−36; centre (−28,−42) |
| East terrace | x 20..35, z −49..−18; alchemy x 21..35, z −29..−19; ancestral eaves x 24..36, z −50..−34 |
| Galleries | plaza x ±12..±14, z −6..12 (open at z 3); climbing x 18..19, z −40..−38 and −25..−23 |
| Flanks | Scripture x −29..−17, z −8..2. Herb garden x −35..−32, z −12..8. Dorm x 17..27, z −8..2. Kitchen court x 30..35, z −12..6. Beast garden x 17..28, z −16..−10. |
| Outer court | archery x −29..−18, z 9..19; drill ground x 18..29, z 9..19; bell x −16..−12 and drum x 12..16 at z 14..18; inner gate x −3..3, z 13..14 |
| Gate and approach | gate hall x −8..8, z 16..21 on the wall line z 20; towers x ±(30..36), z 14..20; lions (±4,22); paifang x −8..8, z 29..30 |

**Template 9 — Istana Langit, at T9 (1 char = 3×3 blocks)**

```
              |         |         |         |         |       (1 char = 3x3 blocks; x = -80 .. 80, z = -100 .. 60)
 -100  ffffffjfffffjfffIfjfffffjfffffjfffffjfffffjfffffjfffff   f woodland and cliffs at the rim; j boundary stone pillars every ~16 blocks (no perimeter wall)
  -97  ffff"""""""""".IIII..:::::::::::".IIIII...........ffff   I hanging-mountain islands (+28..+52) on vertical jade chains to bollards on the peaks below
  -94  ff:::::::::::".IIII..::ttt::::::"IIIIIII....III...ffff
  -91  ff:::::::::::".IIII..:::tt::::::"IIIIIIII...IIII..ffff
  -88  f"::HHHHHHH::"..I....:::OOOOOO::"IIHHHHII...IIII.....f   NW Puncak Dalam (Inner Sect, +14) behind its own formation; O summit Pagoda Kaca Sembilan Harta (+18)
  -85  j"::HHHHHHH::".......:::OOOOOO::IIIHHHHII...IIII.....j   NE big island: round triple-eave Aula Dao Langit (Langit blue) - H inside I; arched sky bridge to pagoda floor 4
  -82  f"::HHHHHHH::bb......:::OOOOOO::"IIHHHHII............f   b covered gallery bridges between the peaks
  -79  f":::::::::::bb......:::OOOOOO::"IIIIIIII............f
  -76  f"::DDDDD::::".......:::OOOOOO::".IIIIII.............f
  -73  f"::DDDDD::::".......:::OOOOOO::"...I................f   D Inner dorms
  -70  j"::DDDDD::::".......:::::::::::".......::::::::::::.j
  -67  f":::::::::::".......:::::^^::::".......::::::::::::.f
  -64  f....................ZZZZZ^ZZZZZ........:::HHHHHH:::.f   Z Grand Ancestral Hall (+13);  NE Puncak Tetua (Elders, +14) with H residence and Q meditation pavilion (Qi tier 3)
  -61  f.....III..~~.:::::::ZZZZZZZZZZZ:::::::":::HHHHHH:::.f
  -58  f.....III..~~.:::::::ZZZZZZZZZZZ:::::::bb::HHHHHH:::.f
  -55  j....IIIII.~~.:::::::::::::::::::::::::bb:::::::::::.j
  -52  f.....III..~~.:::::::::::::::::::::::::":::::::QQQQ:.f
  -49  f..........~~.::::::MMMMMMMMMMMMM::::::":::::::QQQQ:.f   M 9-bay double-eave wudian main hall on the marble terrace +11/+13
  -46  f"::::::::"~~.::::::MMMMMMMMMMMMM::::::":::::::QQQQ:.f
  -43  f"::::::::"~~vv:::::MMMMMMMMMMMMM::::::"::::::::::::.f
  -40  j"::AAAA::"~~vv:::::MMMMMMMMMMMMM::::::""""""""""""".j   W Puncak Awan Harum (+8, the pill peak): A Menara Pil;  ~ stream with waterfalls v into the lake
  -37  f"::AAAA::"~~.::::::MMMMMMMMMMMMM::::::"...""""""""".f
  -34  f"::AAAA::"~~t::::::MMMMMMMMMMMMM::::::tt..:YYYYYYY:.f   E Puncak Binatang (+8): Y beast garden
  -31  f"::AAAA::"~~tt::::::::::::::::::::::::ttt.:YYYYYYY:.f
  -28  f"::AAAA::"~~t:::::::::::^^^:::::::::::tt..:YYYYYYY:.f
  -25  j"::::::::"~~.:::::::::::^^^:::::::::::"...:YYYYYYY:.j
  -22  f"::::::::"~~.:::::::::::^^^:::::::::::"...:YYYYYYY:.f
  -19  f....vv....~~.:::::::::::^^^:::::::::::"...:YYYYYYY:.f
  -16  f....vv....~~."""""tt""EEEEEEEE""t""""""...:::::::::.f   E Aula Pendiri -> Kantor Misi; nine-flight stair ^ up the main peak
  -13  f....~~~~~.........ttt.EEEEEEEE.ttt.............I....f
  -10  j.~~~~~~~~~~~..KKKKKK..EEEEEEEE.kkkkkkDDDDDhhhIIIIhhhj   K Scripture Pavilion, k kitchen court, D Outer dorms, h herb terraces
   -7  f~~~~~~~~~~~~~.KKKKKK=TEEEEEEEEwkkkkkkDDDDDhhhIIIIhhhf
   -4  ff~~~~QQQ~~~~~.KKKKKK=TTT=oo=wwwkkkkkkDDDDDhhhIIIIhhhf   T Blazing Sky Tower entrance (<= 12 from the stele), 7 rooms below; S stele; disc r 9 around L (0,6)
   -1  ff~~~~QQQ~~~~~.KKKKKK=TTToSoowwwkkkkkkDDDDDhhhhhIhhhhf
    2  ff~~~~QQQb~~~~.KKKKKK===o=C==o==kkkkkkDDDDDhhhhhhhhhhf   west Blessed lake: Q island pavilion, b zigzag bridge, stone boat
    5  jf~~~~~~~bbb~~.KKKKKK===oCL=Co==kkkkkkDDDDDhhhhhhhhhhj
    8  f.~~~~~~~~~bb........===o=C==o==kkkkkkDDDDDhhhhhhhhhhf
   11  f....~~~~~...........===oo=oo===...........hhhhhhhhhhf
   14  fiiiiiiiiii..........====ggg====...........DDDDDDDDDDf   i bamboo grove and the candidates' test path (bamboo -> rough slope -> chain ladder)
   17  fiiiiiiiiii..............===...............DDDDDDDDDDf
   20  jiiiiiiiiiiXXXXXXXXX.....===......=========DDDDDDDDDDj   X arena stage (leitai); x targets; B bell and drum towers
   23  fiiiiiiiiiiXXXXXXXXX.....===......=xx=xxx==DDDDDDDDDDf
   26  fiiiiiiiiiiXXXXXXXXXBBBB.===..BBB.=========DDDDDDDDDDf
   29  fiiiiiiiiiiXXXXXXXXXBBBB.===..BBB.=========DDDDDDDDDDf
   32  ff========.XXXXXXXXXBBBB.===..BBB.=========DDDDDDDDDDf
   35  ff========.XXXXXXXXX.....===......=xx=xxx==DDDDDDDDDDf
   38  ff========.XXXXXXXXX..GGGG==GGG...=========DDDDDDDDDDf
   41  ffff======............GGGG==GGG............DDDDDDDffff
   44  ffffffjfffffjfffffj###GGGGGGGGG###ffjfffffjfffffjfffff   G 5-bay shanmen with short red wall stubs # that end in rock; no side gates
   47  .........tt.....tt....GGGGGGGGG.....tt....ttt.........
   50  .........ttt...ttt.......===.......ttt....ttt.........
   53  .....................PPPPP=PPPPP......................   P grand paifang, 5 bays, 7 roofs
   56  .........................===..........................
   59  .........................===..........................
```

The `T` marker on this plan is schematic; the tower entrance centre must stay within 12 blocks of the stele.

**Template 9 levels:**

| Area | Level |
|---|---|
| Approach | −6 → −3 |
| Outer court | −3 |
| Plaza | 0 |
| Main peak | +3 / +6 / +9, marble +11 / +13 |
| Ancestral terrace | +13 |
| Summit | +18 |
| Inner and Elders' peaks | +14 |
| Pill and Beast peaks | +8 |
| Lake surface | −1 |
| Islands | +28..+52 |

The peaks are sculpted `Peak` components that keep natural terrain wherever it is higher than the design (21.8). The valley "cloud sea" is client mist (21.16).

**Compositions of the other templates.** These follow 21.3 and the rules of 21.5–21.8.
- **Template 2:** a front court, then the Aula Pendiri, then a +3 rear court with the main hall, with a pagoda NW behind a small pond.
- **Template 3:** the plaza ringed by galleries; a lotus garden NW with the 5-floor pagoda behind the water; horse-head gables.
- **Template 4:** a 7-wide central stair with 3 landings from the gate to the main hall on its +8 terrace, 4 watchtowers on the red wall, archery W and drill ground E.
- **Template 6:** the template-5 scheme with the Inner peak NW across a gorge stream (covered gallery bridge) and a cloud-sea terrace E.
- **Template 7:** gate → plaza → straight marble bridge across the lake → main peak, with a nine-turn bridge W and the grotto hill beside the plaza.
- **Template 8:** moat → gate tower → spirit way → outer court → plaza → three halls on one three-tier marble terrace, with an imperial garden NW and the 9F pagoda NE.

**Sumber / Source.** The same as 21.3. The layouts are art direction from real architecture (21.8).

---------------------------------------------------------------------------------------------------------------------

### 21.5 Block palette (vanilla 26.3; every name verified in `Blocks.java`)

**26.3 API notes:**
- Dyed blocks are `ColorCollection`s, for example `Blocks.DYED_TERRACOTTA.pick(DyeColor.RED)`, `CONCRETE`, `CONCRETE_STAIRS`, `CONCRETE_SLAB`, `GLAZED_TERRACOTTA`, `CARPET`, `DYED_CANDLE`, `BANNER`.
- Copper blocks are `WeatheringCopperCollection`s, for example `Blocks.CUT_COPPER_STAIRS.waxed().pick(WeatheringCopper.WeatherState.OXIDIZED)`. **Always use the waxed form.** Lightning only rewrites unwaxed copper.
- The iron chain is `IRON_CHAIN`.

**Roof rank hierarchy** (real practice, for example grey tile with a green-glazed border, 灰瓦绿剪边):

| Rank | Buildings | Field | Trim (eave row and hips) | Ridge |
|---|---|---|---|---|
| A | dorm, kitchen, coop, gallery | `DEEPSLATE_TILE_STAIRS` / `_SLAB` / `DEEPSLATE_TILES` | `POLISHED_DEEPSLATE_STAIRS` / `_SLAB` / `_WALL` | `DEEPSLATE_BRICKS`, or a rolled ridge |
| B | gate, Aula Pendiri (templates 2–9), scripture, alchemy, towers | deepslate tiles | **jade**: waxed oxidized `CUT_COPPER_STAIRS` / `_SLAB` | `DEEPSLATE_BRICKS` + jade cap |
| C / D | main hall, template 1's Aula Pendiri, ancestral hall, pagodas, Aula Dao Langit | the player's **glaze** | glaze trim | glaze ridge + gold |

**Glazes.**
- The player chooses a glaze at founding. It can be changed until the first rank C/D roof starts.
- Glazed pagodas always use Giok field and trim with white walls, whatever the choice.

| Role | **Tinta** (ink, default) | **Giok** (jade) | **Langit** (sky, after the Temple of Heaven) |
|---|---|---|---|
| Field | deepslate tiles | waxed oxidized `CUT_COPPER_STAIRS` / `_SLAB` / `CUT_COPPER` | `CONCRETE_STAIRS` / `CONCRETE_SLAB` / `CONCRETE` `.pick(BLUE)` |
| Trim | `CONCRETE_STAIRS` / `_SLAB` `.pick(YELLOW)` (yellow-glazed border, 灰瓦黄剪边) | `CONCRETE_STAIRS` / `_SLAB` `.pick(YELLOW)` | yellow concrete |
| Ridge | `DEEPSLATE_BRICKS` + yellow `CONCRETE_SLAB` cap | waxed oxidized `CUT_COPPER` + yellow `CONCRETE_SLAB` cap | `LAPIS_BLOCK` |

*Note (6b review):* Tinta's rank C/D roofs use the yellow-glazed border, not the plain polished deepslate trim of rank A, so the main hall, the ancestral hall and the pagodas outrank the rank B jade border in the default glaze as well.

**Materials by role:**

| Role | Blocks |
|---|---|
| Lacquer pillars | `POLISHED_CINNABAR` (halls and gates); `POLISHED_CINNABAR_WALL` (pavilions, galleries, small gates: it reads as round, so let FIXUP connect them). Base: `CHISELED_TUFF` on `POLISHED_ANDESITE`. |
| Wood trim and doors | `MANGROVE_PLANKS` / `_STAIRS` / `_SLAB`, `STRIPPED_MANGROVE_WOOD` (sills, lintels), `MANGROVE_DOOR`, `MANGROVE_FENCE` |
| Painted beams | beam band `WARPED_PLANKS` (teal caihua); beam ends `GLAZED_TERRACOTTA.pick(CYAN)` |
| Brackets (dougong) | `WARPED_STAIRS` `HALF=TOP` with the tall side against the wall, plus `DARK_OAK_TRAPDOOR`; caps `CHISELED_SULFUR` (aged gilt in shade, T6+). Warped wood does not burn. |
| Soffit (rank C/D) | `POLISHED_DEEPSLATE_SLAB` top half (dark rafter rhythm, non-flammable) |
| Plaster | `SMOOTH_QUARTZ` for **all** plaster; `CALCITE` only in rocks; plinth `POLISHED_ANDESITE` |
| Palace walls | `POLISHED_CINNABAR` body on a `STONE_BRICKS` footing; `CINNABAR_BRICKS` where texture is wanted; cap = rank B trim. Template 8 citadel: `DEEPSLATE_BRICKS` with 20% `CRACKED_DEEPSLATE_BRICKS`. |
| Lattice | rank D: waxed oxidized `COPPER_TRAPDOOR` (quatrefoil fret); rank B/C: `MANGROVE_TRAPDOOR` (moon hole); garden buildings: `POPLAR_TRAPDOOR` (diamond); leak windows: waxed oxidized `COPPER_BARS` |
| Interior | `POLISHED_DEEPSLATE` floor (the palace "golden bricks"); `CARPET.pick(RED)` runner on the axis |
| Stone terraces | `STONE_BRICKS` (≤ 10% `MOSSY_STONE_BRICKS`, retaining walls only); `CHISELED_TUFF_BRICKS` fret course; `SMOOTH_STONE_SLAB` / `POLISHED_ANDESITE` coping; `STONE_BRICK_STAIRS`; a `CHISELED_STONE_BRICKS` pilaster every 6–8 |
| Marble terraces | deck `SMOOTH_QUARTZ`; face `POLISHED_DIORITE`; balustrade `PALE_OAK_FENCE` rails between `QUARTZ_PILLAR` posts (every 3rd cell) with a `SMOOTH_QUARTZ_SLAB` cap; fret `CHISELED_QUARTZ_BLOCK`; stairs `SMOOTH_QUARTZ_STAIRS`; dragon ramp `CHISELED_QUARTZ_BLOCK` |
| Paving | plaza `POLISHED_ANDESITE` with a `STONE_BRICKS` grid every 4; imperial way `SMOOTH_STONE` with `CHISELED_STONE_BRICKS` every 4th; garden `TUFF_BRICK_SLAB` stepping stones on `MOSS_BLOCK` |
| Gold (≤ 5% of a view) | `GOLD_BLOCK` (pearls and finials, at most 1 per roof); `RAW_GOLD_BLOCK` (hanging fish, rank-D plaque frame); `GLAZED_TERRACOTTA.pick(YELLOW)` (cloud panels); `GILDED_BLACKSTONE` (plaque field); `BELL` |
| Jade | waxed oxidized `CHISELED_COPPER`, `COPPER_GRATE`, `COPPER_CHAIN`, `COPPER_LANTERN`; `PRISMARINE_BRICKS` (disc inlay) |
| Bronze | waxed exposed `CUT_COPPER` + `COPPER_GRATE` (dings, vats' stands) |
| Lights | hanging lantern: `IRON_CHAIN` ×1–3 → `SHROOMLIGHT` → `POLISHED_CINNABAR_SLAB` top-half rim below (palace lantern: 2 shroomlights stacked; **no 2×2×2 cubes**). Post lantern: `MANGROVE_FENCE` ×2 → shroomlight → `POLISHED_CINNABAR_SLAB` bottom cap. Stone lantern: `ANDESITE_WALL` ×2 + `LANTERN` + `DEEPSLATE_TILE_SLAB` hood. Also `SOUL_LANTERN` (ancestral hall, tower), waxed oxidized `COPPER_LANTERN` (gardens), hidden `OCHRE_FROGLIGHT` under `MOSS_CARPET`, `PEARLESCENT_FROGLIGHT` inside ridges and island tops, `SEA_PICKLE` (waterlogged) on pond floors, `END_ROD` (spires), `FIREFLY_BUSH`, red `DYED_CANDLE` (altars) |
| Banners | `BANNER.pick(RED)` with a yellow border on `MANGROVE_FENCE` poles 4–6 high; wall banners on the gate pillars |
| Flora (leaves always `PERSISTENT=true`) | see the tree table in 21.8; lotus = `BIG_DRIPLEAF` + stem (waterlogged) + `LILY_PAD` + `SEAGRASS`; beds of `PEONY`, `LILAC`, `LILY_OF_THE_VALLEY`, `TORCHFLOWER`, `PITCHER_PLANT`, `FERN`; formal beds `MOSS_BLOCK`; hedges `FLOWERING_AZALEA_LEAVES`; bonsai `POTTED_AZALEA`, `POTTED_FLOWERING_AZALEA`, `POTTED_CHERRY_SAPLING`, `POTTED_SPRUCE_SAPLING` |
| Rocks | `CALCITE`, `DIORITE`, `TUFF`, `ANDESITE`, `MOSSY_COBBLESTONE` |
| Water | sealed basins: `STONE_BRICKS` / `MUD_BRICKS` rims, `CLAY` floor |
| Formation disc | `LODESTONE`, `CRYING_OBSIDIAN`, `AMETHYST_BLOCK`. Ring `SMOOTH_QUARTZ` with trigram lines in `POLISHED_BLACKSTONE` (yang = 3 blocks, yin = 1 + gap + 1). Compass points `GILDED_BLACKSTONE`, jade inlay `PRISMARINE_BRICKS`, rim `CHISELED_TUFF_BRICKS`. King Wen (Later Heaven) arrangement in **true compass orientation** whatever the rotation: ☲ S, ☵ N, ☳ E, ☱ W, ☴ SE, ☷ SW, ☶ NE, ☰ NW (matches the GUI Bagua). |
| Blazing tower | `POLISHED_BLACKSTONE_BRICKS`, `CINNABAR_BRICKS` bands, `CHISELED_CINNABAR` panels, `SOUL_LANTERN`. Each room: `CRYING_OBSIDIAN` + lit `SOUL_CAMPFIRE`. |
| Hanging islands | irregular drips of `TUFF` / `ANDESITE` / `STONE` / `CALCITE`; top `GRASS_BLOCK` / `MOSS_BLOCK`; `HANGING_ROOTS`; at most 2 `SPORE_BLOSSOM` per sect; vertical `IRON_CHAIN` or waxed oxidized `COPPER_CHAIN` to `POLISHED_BLACKSTONE` bollards |
| Clouds | **client mist only** (21.16). Carved cloud motifs only as yellow glazed-terracotta panels on gates. |

**Colour budget per view** (checked by the audit; plants excluded):

| Share | Colours |
|---|---|
| about 60% | neutral: stone, roof grey, white |
| about 25% | red |
| about 10% | teal and jade |
| at most 5% | gold |

**Banned in builds:**
- gravity blocks (sand, gravel, concrete powder); fill always uses dirt or stone, even when the credit came from sand;
- vines and glow lichen; sugar cane and kelp;
- fire, lightning rods, redstone;
- carved pumpkins and jack-o'-lanterns (golem patterns);
- spawners; chests (use barrels); entities (no armor stands or item frames).

**Sumber / Source.** Art direction from real architecture (21.8), not novel canon.

---------------------------------------------------------------------------------------------------------------------

### 21.6 Roofs: curved tiered eaves from stairs and slabs

**21.6.1 Profile: the juzhe rule, at most 45°.**

This replaces a gamma curve, which reads as Japanese above 45°. The rule follows the *Yingzao Fashi* (1103) juzhe method and the Qing *Gongbu Gongcheng Zuofa* (1734), whose rise steps from 0.5 to 0.9 per run.

Heights are in half-blocks per column, counted from the eave tip (d = 0) inward:

- **Flare:** `f` columns of +1 (slabs, about 27°). `f` is odd, so stairs start on an even `s`:
  - `f = 1` when R ≤ 4;
  - `f = 3` when R is 5–9;
  - `f = 5` when R ≥ 10.
- **Then +2 per column** (stairs, 45°) up to the ridge.
  - Never +3 on halls.
  - Pavilion and pagoda tops may use +3 on their last 1–2 columns.
- **Skirts and pagoda eaves:** all +1, except +2 on the last column.

**Examples:**

| R | s by column, eave → ridge | Total rise |
|---|---|---|
| 4 | 1, 2, 4, 6, 8 | 4 blocks |
| 6 | 1, 2, 3, 4, 6, 8, 10 | 5 blocks |
| 9 | 1, 2, 3, 4, 6, 8, 10, 12, 14, 16 | 8 blocks |

**Shape formulas:**
- `d = min(dx, dz)` for hip and pyramid roofs. `d = dz` where `dx ≥ gableInset` (the xieshan gable part, or always for a gable roof).
- `i = |dx − dz|`.
- **Corner upturn (qiqiao):** `up(i) = n − i` for `i < n`, with `n = clamp(R/2, 2, 5)`. Surface `= s(min(d, R)) + max(0, up(i) − d)`.
- **Hip line raised:** the `i == 0` columns sit +1 half-block above the field, in trim, and run into the flying tip.

**Roof : body ratio.** The body is measured from the floor to the eave tip; the roof from the eave tip to the ridge cap.

| Building | Roof : body |
|---|---|
| halls | 1.0–1.2 |
| minor buildings | ≥ 0.65 |
| pavilions and pagoda tops | ≥ 1.2 |

**Quadrant height map** for R = 6, n = 3. Rows run from the ridge (top) to the eave (bottom); columns run from the corner (left) to the centre (right). The hip-line +1 is not drawn.

```
hip                                          xieshan (gable from dx >= 3 = overhang + 1)
dz=6:  1  2  3  4  6  8 10 10 10 10          dz=6:  1  2  3 10 10 10 10 10 10 10   <- ridge line
dz=5:  1  2  3  4  6  8  8  8  8  8          dz=5:  1  2  3  8  8  8  8  8  8  8
dz=4:  1  2  3  4  6  6  6  6  6  6          dz=4:  1  2  3  6  6  6  6  6  6  6
dz=3:  1  2  3  4  4  4  4  4  4  4          dz=3:  1  2  3  4  4  4  4  4  4  4
dz=2:  2  3  4  3  3  3  3  3  3  3          dz=2:  2  3  4  3  3  3  3  3  3  3
dz=1:  3  4  3  2  2  2  2  2  2  2          dz=1:  3  4  3  2  2  2  2  2  2  2
dz=0:  4  3  2  1  1  1  1  1  1  1          dz=0:  4  3  2  1  1  1  1  1  1  1   <- the eave edge lifts 1.5 blocks over 3 columns
       ^ corner tip                                 vertical gable triangle stands 1 inside the end pillar line
```

**21.6.2 Turning heights into blocks.** `sOut` is the height of the lowest outward neighbour.

| Condition | Piece |
|---|---|
| `s` odd | bottom slab at `baseY + (s−1)/2` |
| `s` even and `s − sOut ≥ 2` | stair at `baseY + s/2 − 1`: `HALF=BOTTOM`, `FACING` toward the ridge; `SHAPE` set explicitly (outer shapes on hip lines) |
| `s` even otherwise | top slab at `baseY + s/2 − 1` |
| upturn zone (`up(i) − d > 0`) | **slabs only, never stairs** |

**Other rules:**
- **No slab-on-fill stacks.** Each stair sits directly on the stair below it. Under the shell, roof blocks fill down to `floor(sOut/2)`, so no hole shows. The first `overhang` columns stay a one-layer shell (an open soffit).
- **Robustness.** `StairBlock.updateShape` recomputes `SHAPE` whenever a horizontal neighbour changes (snow, a torch). So facings must be chosen so that vanilla's `getStairsShape` produces the designed shape. The audit runs the vanilla shape on every stair and fails on any difference.

**21.6.3 Where the wall line may sit (this fixes the eave slit).**
- The pillar/wall line must fall on a column whose piece fills the bottom half of its cell (a bottom slab or a stair).
- With `f = 3`, overhang 2 gives a bottom slab and overhang 4 gives a stair. Overhang 1 or 3 gives a top slab and a see-through slit.
- **Frieze rule:** every cell from pillar top + 1 up to the roof underside at the wall line is filled. The bottom row is the beam band; the rows above are bracket rows. The audit rejects any air in that band.

**21.6.4 Facings, ridges and ornaments:**

| Element | Correct state |
|---|---|
| Roof stairs | `HALF=BOTTOM`, `FACING` toward the ridge |
| Bracket row (dougong) | `WARPED_STAIRS` `HALF=TOP`, `FACING` **toward the wall**, so the tall side is a corbel against the wall. At each pillar head a 2-high cluster (stair + trapdoor), with a `CHISELED_SULFUR` cap from T6. |
| Chiwen (ridge end) | on top of the last ridge block, a trim stair `HALF=BOTTOM` facing **outward**; one cell further out at the same Y, a top slab. This makes a hook that rises and curls out. |
| Terrace spouts and corbels | `HALF=TOP`, tall side against the wall |
| Main ridge by outer width W | W ≥ 11: ridge block + wall cap. W 7–10: ridge block only. W ≤ 6, galleries and garden buildings: **rolled ridge** (卷棚), where the last 2 columns on each side become slabs meeting in a rounded top. |
| Ridge pearl | gold block at the ridge centre, flanked by two stairs facing it (main hall from T6; every major hall at T9) |
| Hip roof (wudian) | needs W/D ≥ 1.6; otherwise use xieshan |
| Xieshan gable | gable face 1 inside the end pillar line. Field `SMOOTH_QUARTZ` (Jiangnan) or `POLISHED_CINNABAR` (northern official style); bargeboard of trim stairs; **hanging fish** (悬鱼) under the ridge, 1×2 `RAW_GOLD_BLOCK`, on rank C/D |
| Roof beasts (走兽) | rank C/D: an unlit black `DYED_CANDLE` (blue in Langit) on each of the last 3–5 hip blocks before each tip |
| Flying tip | a top slab diagonally outside each corner at `s(0,0) + 1`, over a diagonal corner bracket of upside-down outer stairs. From T4: an `IRON_CHAIN` with a `LANTERN` (the wind bell). |

**21.6.5 Variants:**
- **Double eave.**
  - The lower skirt covers a real **outer colonnade** (副阶周匝), 3 cells outside the core. Its eaves are all +1 except the last; a flashing row of trim slabs runs against the core wall.
  - The visible clerestory is 2–3 rows plus the bracket row, and carries the gilded name plaque.
  - The upper eave tip lands 1–2 blocks inside the lower eave tip horizontally, and at least 3 blocks above the skirt surface.
- **Pyramidal** (pavilion, pagoda top): a square hip converging to an apex finial: `DEEPSLATE_TILE_WALL` ×2, `GOLD_BLOCK`, `IRON_CHAIN` ×2, `END_ROD`.
- **Octagonal:** chamfered distance `d = min(dx, dz, (dx + dz + k)/2)`, with the upturn measured to the nearest of the 8 corner lines.
- **Round** (Aula Dao Langit, template 9): `d = R − √(dx² + dz²)`, triple eave.
- **Fan-shaped** pavilions (扇面亭) use the round rule on a sector.
- **Pagoda floor:** core walls with lattice on 4 sides → skirt eave with corner tips and lanterns → balcony railing on the skirt (`MANGROVE_FENCE`, or jade `COPPER_BARS` on glazed pagodas).

**21.6.6 Front elevation of the Sekte Agung main hall** (stylised; 1 char ≈ 1 block)

```
                   )               (             chiwen curls at both ridge ends (outward hooks)
                   [=======o=======]             main ridge + wall cap; gold pearl o (T6+)
                   /###############\             upper roof: +2 per column (45 deg max), no steeper
                  /#################\            (xieshan: from here up the ends are vertical gable triangles)
                _/###################\_
         ,-'"--'=======================`--"'-,   upper eave: 3 flare slabs; corners lift over n columns + flying tip
               vvvvvvvvvvvvvvvvvvvvvvvvv         bracket band (warped, tall side against the wall; gilded caps at pillar heads)
               ::::::::[ PLAKAT ]:::::::         clerestory 2-3 rows: plaster, black-gold plaque with the sect name sign
       ,-'"--'/////////////////\\\\\\\\\\\\\`--"'-,   lower skirt over the outer colonnade (+1 per column, 27 deg)
             ===============================     lower eave edge (trim slabs)
             vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv     brackets on the colonnade
              |~~~|~~~|~~~~~|~~~|~~~|        beam band: teal warped planks between cinnabar pillars (centre bay wider)
              |+++|+++|DDDDD|+++|+++|        doors/lattice recessed 3 behind the colonnade (deep porch shadow)
              |+++|+++|DDDDD|+++|+++|
            i |___|___|DDDDD|___|___| i      quartz-pillar balustrade posts on the platform
         ===================^^^^^===================   hall platform (+5) with steps the width of the centre bay
       #####################^^^^^#####################   marble terrace +4 (polished diorite face, dragon ramp)
     #######################^^^^^#######################  marble terrace +2
```

**21.6.7 Single-eave section** (3-bay Aula Pendiri: pillar height 5, R = 6, f = 3, overhang 2)

```
      z: -7 -6 -5 -4 -3 -2 -1  0      half section: front eave left, ridge at z=0
  F+12                         n      n  ridge cap wall (only when outer width >= 11)
  F+11                         R      R  ridge block (DEEPSLATE_BRICKS / glaze ridge)
  F+10                         #      #  shared ridge-column block
  F+9                       S         S  roof stair, HALF=BOTTOM, FACING toward the ridge
  F+8                    S
  F+7           b  t                  b/t  bottom / top slab: eave flare, +1/2 block per column
  F+6     b  t  v                     v  bracket row: WARPED_STAIRS HALF=TOP, tall side against the wall
  F+5     s  s  =                     s  soffit POLISHED_DEEPSLATE_SLAB top (rank C+);  =  WARPED_PLANKS beam band
  F+1..4        P                     P  POLISHED_CINNABAR pillar
  F+0           P                        on a CHISELED_TUFF base block
  F-1  ~  _  _  _  _  _  _  _         _  platform, 2 high for halls (STONE_BRICKS + POLISHED_ANDESITE coping)
```

The wall line z −4 sits on a bottom slab (s = 3), so the frieze closes. Body = 6 and roof = 7, a ratio of about 1.15.

**21.6.8 Double-eave section** (template 5+ main hall)

```
  z:   -10 -9 -8 -7 -6 -5 -4 ...  0
 F+19                          R/n  upper ridge, gold pearl at x=0, chiwen at both ends
 F+13..18                S  S ...   upper roof, R about 9, f = 3, overhang 4
 F+12                 S            core line sits on a stair (s = 6)
 F+10/11   b  t  b  t              upper eave flare
 F+11                 v            upper bracket row (warped, against the core wall)
 F+10                 =            upper beam band
 F+9                  Q            clerestory SMOOTH_QUARTZ; name plaque in the centre bay
 F+8            b  b  |            skirt meets the core: flashing row of trim slabs
 F+7      b  t  b  t  |            lower skirt (all +1, 27 deg), eave tip 2 beyond the colonnade
 F+6   b  t  v        |            lower bracket row on the colonnade
 F+5         =        |            lower beam band
 F+0..4      P  . .   W            P outer colonnade;  porch;  W doors/lattice on the core line
```

**Sumber / Source.** Art direction from *Yingzao Fashi* and the Qing *Gongbu Gongcheng Zuofa*, the Forbidden City's roof beasts and dragon-head spouts, and Hui-style horse-head walls. These are not novel canon.

---------------------------------------------------------------------------------------------------------------------

### 21.7 Building bodies, gates, pagodas and stamps

**Proportion rules** (audit constants):

| Rule | Value |
|---|---|
| Bays | Centre bay 6 (5 cells), other bays 4 (3 cells), end bays of 7- and 9-bay halls 3. Small ≤ 3-bay halls of rank A/B may use side bays of 3, which gives 13 wide. Widths stay symmetric about x = 0. 5-bay = pillars at ±3, ±7, ±11. |
| Hall depth | pillar-to-pillar depth ≥ 7; width : depth 1.6–2.2 |
| Pillar height | about 0.9 × centre bay: dorms 4, halls 5–6, main-hall lower storey 6 |
| Overhang | 2 when pillar height ≤ 5, else 4 (parity rule, 21.6.3) |
| Platform | 1 for minor buildings, 2 for halls, 3+ on terraces; steps only as wide as the centre bay |
| Porch | every rank C/D hall has its door and lattice line 1–3 behind the front pillar row, for the deepest shadow |
| Interior | halls of 5+ bays get interior pillar rows (金柱), so an open door never shows an empty box |
| Ornament budget | A: tiles, pillars, plain lattice. B: + brackets, jade trim. C: + painted beams, plaque, soffit. D: + double eave, gold, chiwen, roof beasts, colonnade. |
| Density | Roof outer rectangles ≥ 2 apart, or ≥ 4 when both buildings are taller than 7, unless joined by a gallery. Every gap ≥ 4 gets planting. |

**Façade bay module** (槅扇):

```
         x:  P  .  .  .  .  .  P  .  .  .  P
   F+5       =  =  =  =  =  =  =  =  =  =  =    beam band WARPED_PLANKS (pillar-head cells CHISELED_SULFUR from T6)
   F+4       P  L  L  L  L  L  P  L  L  L  P    L = lattice: an OPEN trapdoor lying on the cell's outer face
   F+3       P  L  L  L  L  L  P  L  L  L  P
   F+2       P  L  L  L  L  L  P  L  L  L  P
   F+1       P  k  D  D  D  k  P  k  k  k  P    D = MANGROVE_DOOR, 3 single leaves side by side (a row of geshan doors)
   F+0       P  m  D  D  D  m  P  m  m  m  P    k = MANGROVE_PLANKS panel;  m = POLISHED_ANDESITE sill
              centre bay (6)    side bay (4)    steps in front of the centre bay only
```

- Doors on disciple routes (duties, library, tower) are placed `OPEN=true` or left out, because disciples cannot open doors.
- Repair treats "planned open, found closed" as fixable at no cost.

**Plaque (匾额):** a `GILDED_BLACKSTONE` frame (with a `RAW_GOLD_BLOCK` border on rank D) around a centred `DARK_OAK_WALL_SIGN`. The text is yellow and glowing, the sign is waxed, and it shows the sect name (updated on rename).

**Pagodas.**
- Floor heights fall as the pagoda rises, and the silhouette tapers convexly. Never use a straight stack.
- Templates 2–4 use square pagodas. The glazed 7- and 9-floor pagodas are octagonal (models: the Porcelain Tower of Nanjing and the Kaifeng Iron Pagoda).
- Every floor has a lantern under every corner tip.
- The dominant pagoda's top is 1.6–2.0 × the main hall's ridge height above the plaza.

**Example: Tujuh Harta, 7 floors, about 42 high:**

| Floor | Core (chamfer) | Overhang | Eave width | Height |
|---|---|---|---|---|
| 1 | 9 (2) | 3 | 15 | 7 (with plinth and porch) |
| 2 | 9 (2) | 2 | 13 | 5 |
| 3 | 7 (1) | 3 | 13 | 5 |
| 4 | 7 (1) | 2 | 11 | 4 |
| 5 | 5 (1) | 3 | 11 | 4 |
| 6 | 5 (1) | 2 | 9 | 4 |
| 7 | 5 (1) | 2, pyramid roof | 9 | 4, + roof 4, + spire 5 |

Pagoda skirts are all +1 slabs, so the odd overhangs leave no slit.

**Paifang** (3 bays, 4 posts, 3 roofs; the 5- and 7-roof variants add small roofs over the posts):

```
 x:  -8 . . . -4 . . . 0 . . . 4 . . . 8
y13   ........o........     o  GOLD_BLOCK pearl on a trim wall stub
y12   ....CrrrrrrrC....     centre ridge (trim stairs back to back), C = chiwen (21.6.4)
y11   ...^SSSSSSSSS^...     S  roof slope, ^ lifted tip slab
y10   ....vvvvvvvvv....     v  brackets
y9    .....#=====#.....     #  centre posts x = +-3,  = upper lintel
y8    .....#gKKKg#.....     K  plaque 3x2, g YELLOW_GLAZED_TERRACOTTA cloud panel
y7    Crrrr#gKKKg#rrrrC     side roofs butt against the centre posts
y6    ^SSSS#=====#SSSS^
y5    .vvvv#.....#vvvv.
y4    .#===#.....#===#.     side posts x = +-7, side lintel
y1-3  .#...#.....#...#.     centre opening 5 wide x 6 high, sides 3 x 4
y0    .B...B.....B...B.     B  post foot clamped by "drum stones" (STONE_BRICK_STAIRS against the post at z = +-1)
```

- Red-wood variant: posts `POLISHED_CINNABAR`, lintels `WARPED_PLANKS`.
- White-stone variant: posts `QUARTZ_PILLAR`, lintels `CHISELED_QUARTZ_BLOCK`, roofs in the glaze.
- A paifang stands **at least 8 blocks** in front of a gate hall. A paifang without roofs is forbidden, because it would read as a torii.

**Stamps** (a small char-grid DSL in Java):
- stone lions, dings, water vats (cauldrons on stone stands), weapon racks, bell frames, drums (barrels), pagoda-lanterns, flag poles, bonsai stands, boundary stones;
- the screen-wall rock; the stone boat (template 7);
- **horse-head gables** (马头墙): the gable rises in 2–3 steps of 2 high × 2 wide, each step capped by back-to-back trim stairs ending in an outward top slab. Used on dorms, kitchens and garden walls in templates 3, 7 and 9.

**Incense ding.** There is **one** signal-smoke ding per sect: a hidden `HAY_BLOCK` under its lit campfire, in front of the main hall. Every other campfire has `SIGNAL_FIRE=false`.

**Stele framing.** No roof over the stele, because it would hide the candidates' Duan light pillar. Instead, a `CHISELED_TUFF_BRICKS` octagon inlay at r 2 and low stone lanterns at (±2, ±2). The stele stays reachable from all 4 sides.

---------------------------------------------------------------------------------------------------------------------

### 21.8 Landscape, composition, sky elements and art QA

**Terrace edges:**

```
        outside             deck (y0)
  y+1          P  f  f  P   P QUARTZ_PILLAR post every 3rd cell + SMOOTH_QUARTZ_SLAB cap;  f PALE_OAK_FENCE
  y0        c  #  =  =  =   c coping: SMOOTH_QUARTZ_SLAB TOP, one cell OUT from the face -> shadow line
  y-1       s  #            s spout under every post: SMOOTH_QUARTZ_STAIRS HALF=TOP, tall side against the wall
  y-2          #            # face: POLISHED_DIORITE (marble) / STONE_BRICKS (stone)
  y-3          k            k fret course: CHISELED_QUARTZ_BLOCK / CHISELED_TUFF_BRICKS
  y-4    a  a  #            a planting strip: AZALEA, FERN, rocks (hides the foot)
```

**Terraces and slopes:**
- At most 4 blocks of exposed retaining wall; above that, step back 2 with a planting strip. Template 8's citadel walls are the deliberate exception.
- Exposed step edges use `MOSS_BLOCK` (green on all faces), with a rock cluster every 4–6 blocks. Never leave a bare dirt face.
- Use 1:2 slopes where space allows; rustic sections use rubble walls of mossy cobblestone, tuff and andesite.

**Water:**
- Vanilla water has **no reflections**, so compose for foreground water: the pond lies between the viewpoint and the pagoda.
- Edges are taihu rocks with holes. Formal basins only get `SMOOTH_STONE_SLAB` curbs.
- The surface sits ½–1 block below the path.
- Plant with seagrass, lily pads, dripleaf lotus and sea pickle glow, with flowering azalea on the banks.
- **No water flows off islands.**

**Trees: 6 seeded archetypes**, never a free L-system:

| Tree | Trunk | Canopy | Ground / use |
|---|---|---|---|
| Huangshan pine | `SPRUCE_LOG` 6–10, leaning 1 per 3 | 2–4 horizontal log arms, each ending in a flat `SPRUCE_LEAVES` pad (5×3 or 7×4, 1 thick) + a crown | the most common tree; on ledges |
| Plum (Mount Hua) | `CHERRY_LOG` 2–3, splitting into 3 crooked branches | sparse `CHERRY_LEAVES` clusters with 30% holes; **dark branches stay visible** (big pink blobs read as sakura) | `PINK_PETALS` r 3 |
| Willow | dark trunk 4–5 | `AZALEA_LEAVES` dome r 3 + strands 2–4 long hanging from every other rim cell | water edges only |
| Ginkgo | straight `POPLAR_LOG` 7–9 | conical `YELLOW_POPLAR_LEAVES` | `LEAF_LITTER` ring |
| Maple | `POPLAR_LOG` 4–5 | `ORANGE_POPLAR_LEAVES` near red buildings; `RED_POPLAR_LEAVES` only against white walls or rocks | upper slopes |
| Bamboo clump | 5–12 stalks in a 3×3–5×5 cluster, heights 6–12 | `STAGE=1`, large leaves on the top 2 | podzol or moss |

**Planting rules:**
- At least 60% green foliage, about 25% plum and ginkgo, at most 15% maple.
- Use spruce and azalea, never biome-tinted dark oak or oak leaves.
- **Every template gets one hero tree at T1.**

**Rocks and paths:**
- Scholar's rocks: vertical stacks of 2–4 with lateral offsets and random voids (thin, perforated, open, wrinkled).
- Formal axis: smooth stone with chiseled stone bricks.
- Garden paths: tuff-brick stepping stones on moss.
- Over water: stepping stones (汀步).

**Composition rules** (checked by the audit at dev time):
1. **Sightline** (rising roofs, after Guo Xi's three distances).
   - Eye point: the paifang centre, 1.6 above the ground.
   - For consecutive buildings on the axis, the angle up to the farther ridge must beat the nearer one by ≥ 1.5 blocks at that distance.
   - If not, raise the farther building's platform or terrace within its range.
2. **One dominant vertical per template**, 1.6–2× the main ridge, placed off-axis between ⅓ and ¾ of the half-width. Template 9's summit pagoda is the exception. Never two equal towers.
3. **Framed views** (Ji Cheng, *Yuanye*: borrowed scenery). Every moon gate and leak window frames the pagoda or a hero tree, checked by a line-of-sight test from the gate centre.
4. **No perimeter wall on templates 6, 7 and 9.** Mountains are the walls in sect art. Use the shanmen with short wall stubs ending in rock, boundary stones (界碑) every ~16 blocks, and forest.
5. **Big-template peaks are sculpted mountains, not stacked fill.**
   - The `Peak` component uses a radial falloff plus seeded noise.
   - Cliff faces are striped in stone, andesite, tuff and calcite, with overhangs and pines on the ledges.
   - Retaining walls go only under building pads and stair flights; climbing galleries (爬山廊) join the levels.
   - Natural terrain higher than the design is kept, and only pads and stairs are carved.
6. **Never repeat an identical building within one view**, except mirrored axis pairs. Mix roof types: gable, xieshan, hip, pyramid, round, rolled ridge. Mix pavilions: square, hexagonal, octagonal, round, fan. Mix bridges: moon arch, nine-turn, covered, stepping stones, straight marble. Mix galleries: straight, climbing, waterside.
7. **Avoid Japanese-isms:** paifang without roofs, pale shoji lattice (`BAMBOO_TRAPDOOR`), all-pink canopies, steep tall roofs, all-red buildings without the teal beams, gravel gardens. Chinese reads as red + teal + gold + white + dark grey.

**Sky elements:**
- **Clouds are client FX** (21.16), not snow or concrete blobs.
- **Floating islands are hanging mountains**:
  - irregular drips 1.5–2.5 × r deep;
  - pines leaning out from the rim, with a ting on top;
  - only vertical chains (iron, or jade copper) to stone bollards, with a carved turtle head under the largest island (Liezi);
  - mist instead of water.
- **Cliff pavilion (template 6):** a deck on cantilevered dark-oak fence-and-log struts from a cliff face, after the Hanging Temple of Mount Heng.
- **Aula Dao Langit (template 9):** round, triple-eave, Langit blue, after the Hall of Prayer for Good Harvests.

**Art QA.** `uji sekte kamera <1-3>` jumps to 3 fixed views per template:
1. the approach eye at the paifang;
2. the garden view across the water toward the dominant vertical;
3. a 45° aerial from the SE.

Review snapshots at T1, T3, T5, T7 and T9, in Tinta and in Giok, by day and at night. At night, windows glow (hidden lights behind the lattice) and lantern rows mark the axis and the eaves.

**Sumber / Source.** Real architecture and art theory, art direction only:
- Guo Xi, *Linquan Gaozhi*; Ji Cheng, *Yuanye*;
- the Porcelain Tower of Nanjing and the Kaifeng Iron Pagoda;
- the Hall of Prayer for Good Harvests; the Summer Palace (marble boat, climbing gallery);
- the Hanging Temple of Mount Heng; the Ming tombs' spirit way; the Forbidden City's three-tier marble terrace and water vats;
- the Huangshan welcoming pine; Hui horse-head walls.

Story tie-ins: *Return of the Mount Hua Sect*'s plum blossoms and the *Liezi* turtles (21.3).

---------------------------------------------------------------------------------------------------------------------

### 21.9 The procedural blueprint engine

**Why procedural Java and not `.nbt`:**
- 81 template-tier combinations come from one component library.
- The terrain fit, rotation and glaze swaps are inputs.
- The intentional build order is generated together with the blocks.
- The bill is computed from the same code.
- `uji sekte audit` can dry-run everything.
- Block references are checked at compile time. Renames such as `chain` → `iron_chain` and the 26.x `ColorCollection` / `WeatheringCopperCollection` moves would silently break NBT data.
- `.nbt` would need 81 hand-built files, cannot fit terrain, and still needs code for order and palette.
- Diagrams, the hologram and the bills all come from the same data.

**Components** (package `sect/build/blueprint`). Every component exposes `bounds()` (for the hologram), `tier`, `id` (stable forever), `cost(ctx)` (from a dry-run count) and `emit(ctx, sink)`.

| Component | Key parameters / notes |
|---|---|
| `TerrainZone` | Polygon or rectangle, design level + allowed range, edge style (RETAINING / SLOPE / WATER), keepNature. Owned by the component whose plot it is. Layered earthwork (21.10). |
| `Peak` | radial falloff + seeded noise, striped cliffs, ledges, keeps higher natural terrain, pads for buildings |
| `Terrace` | rectangle, height, style (STONE / MARBLE), balustrade, stair openings; a lantern every 8; terrace interiors filled solid, never hollow |
| `StairFlight` | start, direction, width, rise, style, centre ramp, landing every 4 |
| `Paving` | area, pattern (PLAZA / AXIS / PATH / STEPPING); paths use bottom slabs |
| `Hall` | bays (bay rule), pillarH, platformH, overhang, roof {GABLE, XIESHAN, HIP, PYRAMID, ROUND} × eaves {1, 2, 3}, colonnade, clerestoryH, façade, sides, rank, interior preset, plaque key |
| `RoofBuilder` | outer rectangle, wall rectangle, baseY, R, profile (21.6), type, gableInset, upturn n, glaze, rank; shared by every roofed component |
| `Pavilion` | size, sides {4, 6, 8, ROUND, FAN}, eaves 1–3, optional `QiSeat` |
| `Pagoda` | base, floors, floor heights, taper, sides, glazed, spire |
| `GateHall`, `GateTower` | bays, depth, straddles a wall; the gate tower (城楼) is template 8's 3-storey version |
| `Paifang` | bays {1, 3, 5}, roofs {1, 3, 5, 7}, material {RED_WOOD, WHITE_STONE}, plaque |
| `InnerGate` | width; the T1 gate with hanging-lantern posts |
| `Wall` | polyline, height, style {RED_PALACE, WHITE_GARDEN, CITADEL}, openings (moon gate Ø5, leak windows), horse-head option |
| `CornerTower` | width, storeys 2–3 |
| `Corridor` | path, width 3, slope (climbing gallery), open side |
| `Pond`, `Stream`, `Waterfall`, `Moat` | shape, depth 2, plants; sealed basins; containment proven at emission |
| `Bridge` | MOON_ARCH / ZIGZAG / COVERED / SKY / STRAIGHT_MARBLE / STEPPING |
| `Tree` | the 6 archetypes of 21.8, seeded; skipped where a natural tree stands within 2 |
| `BambooGrove`, `HerbBeds`, `Coop`, `BeastGarden`, `DrillGround`, `ArcheryRange`, `Arena` | area-based; targets are `TARGET` on stone posts, fence dummies or framed boards |
| `Rock`, `RockHill`, `GrottoHill` | taihu holes; hills carry a ting; the grotto hill has a cave gate and hosts the tower |
| `SpiritWay`, `StoneBoat`, `BoundaryStone`, `ScreenWall` | template-specific set pieces |
| `LanternPost` | STONE / RED_POST / HANGING / PALACE / JADE; spacing per the light rule (21.10) |
| `FormationDisc` | lodestone distance d, ring r (Table B); trigrams in compass orientation |
| `TowerShaft` | rooms 3–7, 5×5 interior, spacing 4, entrance (CELLAR / TING / TOWER / GROTTO), doorless spiral stair, shell first |
| `QiSeat` | tier 1 / 2 / 3 exactly as DESIGN 4 / `QiFormations` |
| `HangingIsland` | centre, r, seed, top preset, vertical tethers |
| `Stamp` | char-grid layers + legend (21.7) |
| `Ornament` | "Hiasan Tingkat n": lanterns, flowers, banners spread over existing parts |

**Interior presets:**

| Preset | Contents |
|---|---|
| main hall | throne dais, gilt screen, incense table with red candles, palace lanterns |
| library | bookshelf walls ≥ the requirement, lecterns, reading tables |
| dorm | kang beds of dark-oak slabs with red carpet (no bed block entities) |
| kitchen | smoker, open campfire hearth, barrel, vats. Never a cauldron on a fire, which would read as an alchemy furnace. |
| alchemy | cauldron on a lit soul campfire under a jade copper-grate hood, brewing stands. It is a working `AlchemyFurnace`, so deconstruction treats an active pot as an obstacle. |
| ancestral | stepped tablet altar, soul lanterns, candles |
| meditation | `QiSeat` |

**Pipeline:**
- `TemplateSpec(id)` holds: footprint, envelope, zones (design level, range, parent, minimum step), the components per tier with their template-local coordinates, anchors, and `planVersion`.
- `SitePlan.compile(spec, rotation, glaze, seed, snapshot)` fits the zone levels, then freezes them at confirmation.
- `emit(component)` streams `BuildOp(localPos, state, phase)`.

**Placement order:**
- **Tiers** ascending. Tier t+1 starts only when every tier-t component is DONE or SKIPPED.
- **Components within a tier:**
  1. the axis path;
  2. components by axis priority (south → north), then side axes from the inside out;
  3. boundary works clockwise from the gate;
  4. gardens and water;
  5. the tier's `Ornament`.

  The player may **Prioritaskan** one component to the front of its tier.
- **Phases within a component:**
  `CLEAR` (plants and snow, no drops) → `SEAL` (fluid-first, 21.10) → `CUT` (layers top-down) → `SHELL` (underground room shells) → `FILL` (layers bottom-up) → `PLINTH` → `FRAME` (pillars all together layer by layer, then beams and brackets) → `ROOF` (ascending s: eave ring first, ridge last) → `WALL` → `INTERIOR` → `ORNAMENT` → `GARDEN` → `WATER` (last) → `TIDY`.
- **Within a layer:** mirror pairs interleaved (`(x,z)` then `(−x,z)`), outward from the axis. Earthwork spirals outward within each layer. A building is therefore symmetric at every moment of its growth.

**Slices and determinism:**
- Ops are a pure function of (template, component, seed, rotation, origin, frozen snapshot, frozen zone levels, glaze, planVersion).
- The engine emits a component **off the server thread** (`Util.backgroundExecutor()`, no world reads) and consumes it in slices of ≤ 4,096 ops along the ordered stream. A slice is a range of (phase, layer), never a spatial tile, so symmetric growth is kept.
- Only the cursor `(tier, componentId, phase, layer)` is saved.
- **Resume is idempotent**: re-emit and skip cells already in their target state.

**Versioning:**
- Component ids are stable. `hash` changes only when a component's blocks change.
- After a mod update with a new `planVersion`, finished components are never rebuilt and unfinished ones use the new blueprint.
- New components in tiers that are already finished are queued as "Tambahan" without lowering the tier. Components that disappeared from the plan stay as ordinary sect cells.

**Transform.** Local → world uses rotate and translate. States go through `state.rotate(Rotation)`. The trigrams and plaque text are emitted in world orientation.

**TIDY:**
- A whitelist only: fences, walls, panes, iron and copper bars, fence gates, grass `SNOWY`.
- Uses `Block.updateFromNeighbourShapes(state, level, pos)` with flags 2|16, over the component box **plus a 1-block halo**, so earlier components' fences reconnect.
- Never stairs (designed shapes stay), and never lanterns or plants (a failed `canSurvive` returns AIR).

**Anchors and design constraints the systems rely on:**

| Anchor or constraint | Why |
|---|---|
| Stele (0,0,0); its cell and the block under it are never edited | 20.1: only players place or break the stele |
| The 4 cells 5 blocks N/E/S/W of the stele stay air over walkable floor, at stele height | 20.3.9 duty posts (rotation-invariant) |
| Standing cells inside a ring 8–14 from the stele within ±3 of stele y | 20.3.3 candidate arrival fallback |
| `post_water`, `post_kitchen`, `post_garden`, `post_farm`, `arrival`, `arena`, `library` (≤ 16), `formation` (≤ 24), `tower` (entrance ≤ 12 horizontally, deepest campfire ≤ 28 below, so within the 32 sphere of `BlazingTower`), `treasury`, `main_hall`, `work_*` | A, B and D integration; builder AI |
| Tower rooms: 2-high clear cells next to each campfire; doorless routes | `Targeting.safeSpot`; disciples cannot open doors |
| Lit soul campfires on crying obsidian only in tower rooms | `BlazingTower` counts any of them as a floor |
| Leaves `PERSISTENT=true`; trees are blocks, never saplings | no decay; trees look grown on day one |
| Block entities only: lectern, bell, banners, campfires, brewing stands, decorative empty barrels, the gate sign | no spawners, no loot, no chests |

**Sumber / Source.** Game design; no canon claimed.

---------------------------------------------------------------------------------------------------------------------

### 21.10 Footprint, terrain and safety (the exact scope of the exception)

**Snapshot and preview scan** (reads only):
- The union square of all 4 rotations is scanned once and cached; rotating only re-projects the plan.
- Terrain heights come from chunk heightmaps (`MOTION_BLOCKING_NO_LEAVES`) plus a short downward probe, at about 520k reads for template 9 (≈ 3 s, spread over ticks).
- Fluids, block entities and protected blocks are found by skipping sections with `LevelChunkSection.maybeHas(...)`, as `HallLevel.scanTower` does.
- Every read goes through `getChunkNow`; a sync chunk load is never called. An unknown neighbour counts as "fluid present".
- **Confirmation requires 100% of the chosen rotation's columns snapshotted.** Columns are captured as their chunks load. The HUD shows "Tapak terpindai 78%" with an arrow toward the missing part.
- Template 9 needs about render distance 8 from the stele, template 8 needs 7 and template 7 needs 6.

**Validation:**

| Check | Rule | Severity | Message (id_id) |
|---|---|---|---|
| Dimension | Overworld only (v1) | red | "Hanya di Dunia Atas" |
| World height | `plazaY − below ≥ minY + 2`; `plazaY + above ≤ maxY − 2` | red | "Turunkan/naikkan Prasasti" |
| Other sites | footprint + 16 must not touch another hall's footprint or a showcase; A's 128-block stele rule | red | "Bertumpuk dengan Sekte ‹nama›" |
| Spawn protection | dedicated servers: no overlap | red | |
| Villages | a villager or iron golem within footprint + 16, or a village piece intersecting touched cells | red | "Ada desa di dalam tapak" |
| Structures | player-relevant structures (village, outpost, temples, igloo, mansion, monument, stronghold, ancient city, trial chambers, ruined portal with a chest) intersecting **touched** cells; looked up per chunk with `StructureManager`; mineshafts ignored | red, names it | |
| Lava | lava **outside** the footprint next to a cut cell; lava inside is converted to fill (amber note) | red | |
| Relief | P95 − P5 of surface height ≤ max relief (Table A) | amber > 75%, red > 100% | "Medan terlalu curam: pilih tempat lebih datar atau templat lebih kecil" |
| Earthwork | cut + fill ≤ cap (always shown: "Galian 12.400 · Timbunan 3.100") | red above | |
| Water | surface water ≤ 30% of columns and ≤ 10% deeper than 6 (it is filled and billed) | amber, red above | |
| Cliffs and voids | columns needing fill > 16: fill is capped 4–6 below the surface, with a 3-block stone cap over caves and ravines | amber > 5%, red > 15% | |
| Protected blocks (below) | inside a component box | red marker | "Pindahkan/hancurkan sendiri bila mau" |
| Containers with items | inside a component box | red; amber if "Kosongkan ke Peti Galian" is authorized | |
| Player-made blocks | inside a component box or earthwork | amber: "N blok buatan pemain dilindungi; bangunan yang menabraknya akan menunggu" | |
| Leafless log clusters, natural bee nests | in a building plot | amber; the player chooses credit or keep | |
| Snapshot | < 100% | confirm disabled | "Tapak belum terpindai penuh" |

**Classification** (reads only; a datapack can extend the tags):
- **Natural** = tag `#kultivator:sect_natural`:
  - `#base_stone_overworld`, `#dirt`, `#sand`, gravel, clay, calcite, tuff, dripstone, cinnabar and sulfur, `#ores` (and deepslate variants), badlands terracotta;
  - snow, ice, packed ice, moss, short and tall grass, fern, dead bush, `#flowers`, sugar cane, cactus, bamboo, kelp, seagrass, mushrooms, pumpkin, melon, sweet-berry bush, mangrove roots, mud;
  - leaves only with `persistent=false`;
  - logs as whole clusters: a log-and-leaf cluster classified at scan time keeps its verdict while its state is unchanged, so clearing the leaves first never turns the trunk into "player-made".
- The builder's **own** cells (21.20 cell index) always count as its own and are never "player-made".
- **Player-made** = everything else: planks, glass, wool, torches, rails, redstone, farmland and crops, dirt paths, persistent leaves, carpets. Hanging entities, armor stands, boats and minecarts count as blocking.
- **Protected, never touched** (tag `#kultivator:sect_protected` + D's `#kultivator:indestructible`):
  - bedrock and anything with hardness < 0;
  - portals, frames and end gateways; spawners, trial spawners, vaults; reinforced deepslate; budding amethyst;
  - beacons, conduits, respawn anchors;
  - the stele and the block under it; the lodestone of any registered formation;
  - every block entity the builder did not place, except containers explicitly emptied.

**Per-position decision**, re-checked at placement and not only at scan:

| Current block | Action |
|---|---|
| Already the target state | skip (0.1 op); its reserved share is refunded |
| Air, short or tall grass, fern, snow layer, dead bush | place; cleared plants drop nothing |
| Flowers | credited as Flora items, then place |
| Natural | **displace**: drops as if mined by the unenchanted netherite tool matching its mineable tag (`Block.getDrops(state, level, pos, null, null, tool)`); routed per "Credit" below; then place or leave air |
| Fluid or waterlogged | sealed first by the fluid-first rule |
| Player-made | **blocked**, unless "Bongkar & simpan" is authorized for this site (singleplayer without LAN, or op): then a silk-touch drop goes to Peti Galian |
| Container with items | blocked, unless "Kosongkan" is authorized (same permission): contents and block go to Peti Galian |
| Protected | blocked; can never be authorized |
| A living entity's box overlaps a solid target, or the block holds up a living entity's feet | defer and retry after 40 ticks. After 10 tries, players within 16 see "Menyingkir dari area kerja"; mobs are simply retried |
| Chunk not loaded | the component is suspended (reservation kept); the next loaded one opens |

**Earthwork.**
- Done in **horizontal layers per zone**: cut top-down, fill bottom-up, spiralling within each layer. Never a column sweep, which could drop a player or disciple 10 blocks. A plateau settles visibly.
- **Perimeter first:** edge columns are finished before interior digging below the outside level. Within 2 blocks of the edge, cuts never go below the outside ground; that cell becomes retaining wall.
- **Keep columns:** columns holding player-made blocks, plus a 2-block margin, get no cut below and no fill above. They are wrapped by a retaining ring, and the components that need them show as blocked.
- **Landscape margin:** a 3–6 block ring inside the edge blends from the zone level to the outside height at ≤ 1:1, using moss, rocks and kept natural trees. Retaining walls go wherever it would be steeper.

**Fluids.** Fluids are sealed first.
- Before any cell inside the footprint becomes air, every fluid cell in or touching the cut volume (waterlogged blocks included) becomes the zone's fill block, set with 2|16|512.
- A fluid outside the footprint, or in an unloaded chunk, means that cell never becomes air; it becomes retaining wall.
- Underground rooms place their 1-block shell (replacing cave air and fluid) before hollowing.
- Water is placed in the WATER phase only, as static sources with flag 512, and only where a containment check proves every non-source cell ends in the plan's own sealed basin.
- Only waterfalls get `scheduleTick`. They start in a notch walled on 3 sides and land in a source pool.
- Waterloggable blocks placed in planned water get `WATERLOGGED=true`.

**Flags.**
- Placement uses `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE` (2|16|512): no neighbour cascades, no `onPlace`, so gravity blocks, observers and redstone never wake up.
- Removal uses 2|16.
- TIDY and waterfalls are the only exceptions.
- Flag 256 is never used on a container that holds items.

**Block-state correctness** (emit every property explicitly):
- Stairs: `FACING`, `HALF`, `SHAPE`.
- Doors and double plants: both halves, same `FACING` / `HINGE`. A refund removes both halves.
- Lanterns: `HANGING` matches the support, and the support goes first.
- Chains: `AXIS=Y`.
- Bamboo: `STAGE=1`, plus `AGE` and `LEAVES`.
- Big dripleaf: `TILT=NONE` + stem, waterlogged, on clay or moss.
- Pink petals: amount and facing, on a sturdy block. Lily pads: only on a source.
- Campfire: `LIT`, `SIGNAL_FIRE` false (except the one ding).
- Bell: `ATTACHMENT` matching its mount.
- Vats: `WATER_CAULDRON` with `LEVEL=3` (water is free).

**Light and mobs.**
- **Light rule:** every top surface, roofs included, keeps block light above 0.
  - Each ridge has a glowing pearl or a froglight inside it; island tops have froglights.
  - Walkable lights at most 12 apart; interiors get a light for every ~7×7 of floor.
  - Terrace interiors are solid.
- **Spawn-proof mixin** (`sectBuildSpawnProof = true`): `NaturalSpawner.isValidSpawnPostitionForType` (the vanilla spelling) returns false for `MobCategory.MONSTER` when the block below is a sect cell.
- Emitters of light ≥ 12 stay at least 4 blocks inside the edge, so outside snow and ice do not melt.

**Fire.**
- Roofs, brackets and soffits are non-flammable (stone, copper, concrete, warped). Lantern rims are cinnabar slabs.
- Flammable trim (mangrove, carpets, leaves) stays at least 2 blocks below roof surfaces.
- Trees stay at least 3 blocks from wooden walls.

**Ambient budget per sect:**
- about 120 block entities;
- at most 12 lit campfires: up to 7 tower, 1–2 alchemy, 1 kitchen, 2 dings;
- at most 3 bells; at most 2 spore blossoms; no chests.

**Credit routing** for displaced blocks. Nothing is lost.
1. With "Pakai hasil galian" on (default), bulk drops (cobblestone, dirt, logs, flowers…) go to the ledger at their deposit value (21.11).
2. Everything else, including ores, gems, raw metals and leftovers, goes to **Peti Galian** as items. Ores are never auto-converted.
3. B's 27-slot `Storehouse` is **never** used, because its overflow is deleted.

**Safety checklist.** This is the whole scope of the DESIGN 0 exception.
1. Only `BuildPlacer` places or removes blocks.
2. It acts only inside the confirmed footprint column, only in loaded chunks (it never force-loads), and only while the hall is active and the stele stands on its anchor cell.
3. It never touches protected blocks, obstacles or block entities it did not place, except as explicitly authorized.
4. No item entities, no fire, no explosions, no lightning, no spreading plants, no gravity blocks, no entities.
5. No living entity is trapped or dropped.
6. Fluids stay contained.
7. Every confirmation is logged to Catatan and the server log: `[Kultivator] Bangun: <owner> <template> T<n> -> N blocks`.
8. The consent text names the only accepted outside effect: natural leaves outside the footprint may decay when a trunk inside it is credited.

**Sumber / Source.** The spawn-proofing image is *A Record of a Mortal's Journey*, ch. 153: Yue Lu Hall is covered by a formation. The rules themselves are game design.

---------------------------------------------------------------------------------------------------------------------

### 21.11 Materials, costs and bills

**Sembilan Bahan (nine materials).**
- Each item belongs to exactly one category.
- The ledger stores centi-units (`long` × 100), up to 1,000,000 units per category.
- **1 unit ≈ one full block of that material** when deposited.

| id | Nama / English | Accepts (units each) | Pays for |
|---|---|---|---|
| `batu` | Batu / Stone | cobblestone, stone, andesite, granite, tuff family, stone bricks (all), smooth stone, basalt, sandstone, end stone: 1; slab ½; stair ¾; wall 1 | plinths, terraces, paving, stairs, retaining walls |
| `tanah` | Tanah / Earth | dirt family, grass block, podzol, mycelium, mud, packed mud, sand, red sand, gravel, soul sand and soil: 1; netherrack ½ | fill and soil (mostly paid by digging credit) |
| `kayu` | Kayu / Timber | log, wood, stem or hyphae: 4; planks 1; bamboo block 2; wooden slab ½; stair ¾; stick or bamboo ¼ | beams, frames, doors, lattice |
| `genteng` | Genteng / Tile | deepslate and blackstone families: 1; clay 1, clay ball ¼; bricks 1, brick ¼; terracotta 1; mud bricks 1 | roof tiles, ridges, interior floors |
| `kapur` | Kapur / Lime & Plaster | calcite, diorite (any), quartz block, white concrete or powder, bone block, snow block: 1; quartz ¼; bone ⅓; bone meal ⅑; snowball ¼ | plaster, marble |
| `pernis` | Pernis & Pewarna / Lacquer & Dye | cinnabar family 1 (slab ½, stair ¾); any dye 1; beetroot 1; red, yellow or blue concrete or powder 1; red terracotta, glazed or wool 1 | lacquer pillars, palace walls, glaze concrete |
| `flora` | Flora / Plants | sapling 1; leaves ¼; flower 1; tall flower 2; lily pad 1; moss block 1; moss carpet ½; azalea 1; dripleaf 1; petals ¼; fern or grass ¼; seagrass ¼; spore blossom 2; firefly bush 1; leaf litter ¼; hanging roots ¼ | trees, beds, hedges, lotus |
| `logam` | Logam & Pelita / Metal & Lamps | iron ingot or raw iron 1; nugget ⅑; iron block 9; chain 1; lantern, soul lantern or copper lantern 1; torch ¼; glowstone 1, dust ¼; shroomlight 1; froglight 1; sea lantern 1; sea pickle ½; candle ½; iron bars ⅜ | chains, lights, fittings |
| `permata` | Emas & Giok / Gold & Jade | gold ingot or raw gold 1; nugget ⅑; gold or raw-gold block 9; copper ingot or raw copper ½; copper block 4½; cut or waxed copper (any) 2; prismarine shard or crystals ¼; prismarine 1; prismarine bricks 2; dark prismarine 2; emerald 2, emerald block 18; lapis ½, lapis block 4½; diamond 4; sulfur family ½ | gilt, jade trims and lattice, Giok and Langit glazes |

**Placed cost, by material and never by role:**

| Placed block | Cost |
|---|---|
| Masonry, stone, deepslate, blackstone, brick, quartz, calcite, cinnabar, concrete; full block or wall | ½ unit of its category (stair ⅜, slab ¼). The sect's artisans stretch bulk materials 2 : 1. |
| Hidden fill (terrace cores, foundations) | Tanah ¼ or Batu ¼ |
| Planks and wood | Kayu ½ (stair ⅜, slab ¼, fence ¼, trapdoor ⅜, door 1 per door, sign ½); tree trunks ½ |
| Leaves | Flora ¼ |
| Flowers, azalea, lily pads | Flora 1 each |
| Lanterns, shroomlight, froglight | Logam 1 (chain and bars ½, candle ½) |
| Waxed copper full block, grate, trapdoor | Permata 2 (stair 1½, slab 1, bars and chain 1) |
| Prismarine bricks | Permata 2 |
| Gold block or raw-gold block | Permata 9 |
| Chiseled sulfur | Permata ½ |
| Lapis block | Permata 4½ |
| Water, air | free |
| Unknown block | refused at startup by the audit |

**Cost invariant** (startup self-check): for Logam & Pelita, Emas & Giok and every exact item, the deposit value of a placed block's drop is ≤ its placed cost. Bulk renewable categories are exempt, because vanilla already makes them infinite.

**Exact items** (deposited 1:1, never converted from units, so vanilla progression holds):

| Tier | Item | Count by template 1 → 9 |
|---|---|---|
| T1 | cauldron (water vats) | 2, 2, 4, 4, 4, 6, 6, 8, 8 |
| T2 | smoker, campfire | 1 each |
| T3 | bookshelf | 14, 16, 24, 32, 40, 48, 56, 64, 72 |
| T3 | lectern | 1 (templates 1–4), 2 (5–9) |
| T4 | cauldron + soul campfire (alchemy furnace) | 1 each (templates 1–5), 2 each (6–9) |
| T4 | brewing stand | 1, 1, 1, 2, 2, 2, 3, 3, 3 |
| T5 | lodestone 1, crying obsidian 4, amethyst block 4 | all templates |
| T5 | optional gate altars: +1 lodestone and +4 crying obsidian each | 2 (template 6), 4 (7–9) |
| T6 | campfire (dings) + 1 hay block (the signal ding) | campfires 1 (templates 1–4), 2 (5–9) |
| T7 | target block | 4, 4, 6, 8, 8, 10, 12, 12, 16 |
| T7 | bell | templates 2–9 |
| T7 | barrel (drum) | templates 3–9 |
| T8 | Qi seat tier 1: 4 amethyst blocks | template 3 |
| T8 | Qi seat tier 2: 8 amethyst blocks | templates 4–6 |
| T8 | Qi seat tier 3: 8 amethyst blocks + 4 end rods + 1 lodestone | templates 7–9 |
| T8 / T9 | end rod per pagoda spire | 1 per pagoda |
| T9 | crying obsidian + soul campfire per tower room | 3, 3, 4, 4, 5, 6, 6, 7, 7 |

**Bills.**
- Tinta palette, units, before digging credit.
- The real numbers come from the dry run: `uji sekte info <n>` prints them, and blueprints are tuned to within ±15% of these targets.
- Giok and Langit move most roof Genteng into Permata or Pernis.

**Whole template (all 9 tiers):**

| Template | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1,120 | 20 | 440 | 510 | 320 | 140 | 110 | 310 | 270 | 3,240 |
| 2 | 2,110 | 320 | 910 | 1,100 | 680 | 240 | 110 | 460 | 420 | 6,350 |
| 3 | 3,600 | 460 | 1,450 | 1,780 | 1,080 | 400 | 220 | 500 | 680 | 10,170 |
| 4 | 5,720 | 980 | 2,180 | 2,640 | 1,200 | 980 | 360 | 730 | 1,060 | 15,850 |
| 5 | 7,250 | 1,590 | 2,820 | 3,590 | 1,280 | 1,490 | 500 | 1,120 | 1,450 | 21,090 |
| 6 | 9,680 | 2,710 | 4,000 | 5,040 | 1,820 | 1,760 | 670 | 1,670 | 2,080 | 29,430 |
| 7 | 12,470 | 4,000 | 4,470 | 5,850 | 1,720 | 2,090 | 1,090 | 2,130 | 2,620 | 36,440 |
| 8 | 19,060 | 4,050 | 6,290 | 8,520 | 2,230 | 3,700 | 1,390 | 2,960 | 3,620 | 51,820 |
| 9 | 22,620 | 10,320 | 6,470 | 9,000 | 2,710 | 3,330 | 2,450 | 3,850 | 4,480 | 65,230 |

**Per tier, total units, every template.** T1–T3 are kept a similar-cost core, so hall level 3 comes at a comparable pace in every template.

| Template | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | T9 | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 650 | 400 | 560 | 530 | 390 | 160 | 90 | 160 | 300 | 3,240 |
| 2 | 800 | 500 | 670 | 1,080 | 810 | 880 | 480 | 720 | 410 | 6,350 |
| 3 | 1,000 | 600 | 900 | 2,970 | 620 | 1,320 | 700 | 1,320 | 740 | 10,170 |
| 4 | 1,150 | 700 | 1,100 | 4,460 | 1,480 | 2,090 | 1,780 | 2,090 | 1,000 | 15,850 |
| 5 | 1,300 | 800 | 1,300 | 4,470 | 3,540 | 3,760 | 2,000 | 2,930 | 990 | 21,090 |
| 6 | 1,400 | 900 | 1,400 | 5,300 | 4,410 | 5,240 | 2,290 | 6,570 | 1,920 | 29,430 |
| 7 | 1,500 | 1,000 | 1,500 | 4,480 | 6,050 | 8,210 | 3,250 | 8,490 | 1,960 | 36,440 |
| 8 | 1,700 | 1,100 | 1,600 | 9,680 | 8,250 | 12,490 | 4,910 | 8,440 | 3,650 | 51,820 |
| 9 | 1,800 | 1,200 | 1,700 | 8,560 | 7,170 | 15,200 | 5,030 | 13,760 | 10,810 | 65,230 |

**Per tier and category, templates 1, 5 and 9.** Templates 2–4 use template 1's or 5's split for each tier, and templates 6–8 use template 5's or 9's, whichever is nearer in size, scaled to their own tier totals.

| Template 1 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 210 | – | 130 | 100 | 50 | 20 | – | 100 | 40 | 650 |
| T2 | 100 | 10 | 70 | 70 | 40 | 20 | 40 | 20 | 30 | 400 |
| T3 | 150 | – | 120 | 130 | 70 | 30 | – | 20 | 40 | 560 |
| T4 | 160 | – | 90 | 110 | 50 | 40 | – | 30 | 50 | 530 |
| T5 | 170 | – | – | 90 | 110 | – | – | 20 | – | 390 |
| T6 | 70 | – | 20 | 20 | 10 | 10 | – | 10 | 20 | 160 |
| T7 | 20 | 10 | – | – | – | – | 20 | 20 | 20 | 90 |
| T8 | 30 | – | 10 | – | – | – | 30 | 40 | 50 | 160 |
| T9 | 220 | – | – | – | – | 10 | – | 50 | 20 | 300 |

| Template 5 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 480 | – | 230 | 250 | 130 | 70 | – | 50 | 90 | 1,300 |
| T2 | 130 | 20 | 160 | 80 | 50 | 30 | 270 | 30 | 30 | 800 |
| T3 | 330 | – | 310 | 320 | 110 | 80 | – | 50 | 100 | 1,300 |
| T4 | 1,820 | 690 | 180 | 640 | 220 | 560 | 50 | 210 | 100 | 4,470 |
| T5 | 1,570 | – | 440 | 740 | 10 | 400 | – | 170 | 210 | 3,540 |
| T6 | 1,370 | 790 | 390 | 420 | 350 | 120 | – | 120 | 200 | 3,760 |
| T7 | 490 | – | 430 | 440 | 120 | 110 | – | 160 | 250 | 2,000 |
| T8 | 660 | 200 | 560 | 670 | 250 | 150 | 120 | 100 | 220 | 2,930 |
| T9 | 490 | 10 | – | – | – | 20 | 20 | 230 | 220 | 990 |

| Template 9 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 610 | – | 360 | 370 | 160 | 80 | – | 80 | 140 | 1,800 |
| T2 | 190 | 30 | 230 | 110 | 50 | 30 | 480 | 40 | 40 | 1,200 |
| T3 | 380 | – | 430 | 520 | 80 | 90 | – | 60 | 140 | 1,700 |
| T4 | 2,930 | 760 | 420 | 1,920 | 190 | 1,370 | 280 | 510 | 180 | 8,560 |
| T5 | 3,050 | – | 940 | 1,570 | 30 | 720 | – | 380 | 480 | 7,170 |
| T6 | 5,310 | 5,360 | 1,050 | 1,160 | 930 | 310 | – | 430 | 650 | 15,200 |
| T7 | 1,500 | – | 820 | 1,000 | 140 | 310 | – | 530 | 730 | 5,030 |
| T8 | 5,370 | 3,950 | 710 | 650 | 820 | 140 | 1,160 | 520 | 440 | 13,760 |
| T9 | 3,560 | 850 | 1,140 | 1,480 | 310 | 320 | 300 | 1,280 | 1,570 | 10,810 |

**Reservation.**
- `bill = Σ cost(target)` over the positions that still need placing.
- For earthwork, `reserve[cat] = max(0, fill[cat] − predictedCutCredit[cat])`. The scan predicts natural blocks 1:1 (stone → Batu, dirt → Tanah, logs → Kayu), and CUT runs before FILL.
- Positions found already correct at placement refund their share.
- The bill line shows the expected digging credit: "Batu 1.570 (galian ±900)". On hillsides, digging usually covers Batu and Tanah.

**Sumber / Source.** *A Record of a Mortal's Journey*, ch. 153: Yue Lu Hall stores and supplies materials. This is the Gudang. Categories, numbers and costs are game design.

---------------------------------------------------------------------------------------------------------------------

### 21.12 Founding flow: choose, preview, confirm

1. **Place the stele** (20.3.1). If the hall has no template:
   - chat shows a clickable "[Pilih Templat Sekte]";
   - the Aula and Bangun tablets show a "Pilih Templat" talisman and a small "Bangun Sendiri" coin. That coin keeps the 20.3.1 scan path, with no template.
2. **Template screen** (`OpenScreen.SECT_TEMPLATE`, jade scroll).
   - **Left:** a 3×3 grid of jade plaques. Each has a numeral seal, the name and an ink top-down sketch drawn at runtime from the component boxes.
   - **Right, for the selected template:**
     - a large diagram with a scrubber of nine jade beads (later tiers fade in) and a skyline strip along the axis;
     - stats: footprint, envelope, component count, the whole bill and exact items, estimated time at the current builder count, perks, and the realm hint;
     - the **glaze** choice: Tinta, Giok or Langit.
   - Diagrams come from the blueprint classes in `src/main`, so there are no image assets to drift.
3. **"Lihat Tapak" (preview)** sends C2S `SECT_BUILD "preview:<t>:<rot>:<glaze>"`. The server compiles the plan on the snapshot (21.10) and replies with S2C `BuildPreview`. The client draws the hologram (21.16) for 120 s. The screen can be closed to walk around, and a HUD panel stays: "Pratinjau Tapak · Sekte Kelas Dua · Gerbang: Selatan · 2 peringatan", with chat buttons [Putar] [Konfirmasi] [Batal].
4. **Rotation.** The gate faces N, E, S or W. The default is the side of the stele where the owner stands.
5. **Validation** (21.10). Confirmation is possible only when nothing is red and the snapshot is 100%.
6. **Consent.** A checkbox talisman sits next to the conflict summary: "Aku paham: di dalam tapak, tanah dan tanaman alami akan diratakan dan dikreditkan ke Gudang Sekte; bangunanku dilindungi; daun alami di luar tapak bisa rontok." Then the player **holds** the jade Konfirmasi seal for 3 s.
7. **On confirm:**
   - the site is written (template, rotation, glaze, seed, plazaY, snapshot, frozen zone levels, frozen scan cap);
   - the **Batu Pertama** ceremony plays: the outline flashes once, the stele chimes, and disciples in the territory gather and bow;
   - T1 is queued, the tier missions appear, and advancement `first_stone` is granted;
   - a log line is written.
8. **Free change.** Until the first block is placed, "Ganti Templat" (any template, rotation or glaze) is free. After that, the options are an upgrade, deconstruction, relocation or abandoning (21.18).

**Sumber / Source.** Founding one's own sect:
- *Martial Peak*: Yang Kai founds High Heaven Palace.
- *BTTH*: Xiao Yan founds Pan Men, then Xiao Gate.

The preview, consent and hold are safety game design, a jade version of the Kehancuran hold.

---------------------------------------------------------------------------------------------------------------------

### 21.13 Deposits and the Gudang Bahan Sekte

**Two stores**, both shown on the Bangun tablet:
- **Gudang Bahan**, the ledger of the 9 categories plus exact items.
- **Peti Galian**, a salvage counter map `item_id → long`. It has no limit and loses nothing, and items can be taken out one stack per click.

Both are separate from B's 27-slot Lumbung Sekte (duty output).

**Depositing:**
- **Bangun tablet → Bahan panel:**
  - Accepted inventory items are listed by category with their unit value. Click deposits a stack (C2S `"deposit:<slot>"`, no container menu); shift-click deposits all of that item.
  - **"Setor yang dibutuhkan"** deposits only what the current and next tier still need.
  - Exact items are deposited the same way.
  - Optional **"Ambil dari Lumbung"** moves accepted items from B's storehouse; it needs a B API (21.24).
- **At the stele:** **sneak + right-click with an accepted item**, through `UseBlockCallback`, deposits the held stack. A plain right-click keeps opening the hall, and sneak with an empty hand keeps A's "move hall", so a held diamond is never swallowed by accident. Actionbar: "+64 Batu · Batu 412 / 1.570 (tingkat ini)".
- Partial deposits are always fine.
- **Deposits give no PK by themselves.** Kantor Misi stays the place for PK offerings; missions pay PK (21.14).

**Break refund** (`sectBuildBreakRefund = true`):
- Trigger: `PlayerBlockBreakEvents.BEFORE` on a cell in the sect cell index (21.20).
- The vanilla break is cancelled. `BuildPlacer` removes the block and the sect blocks that depend on it (lanterns, doors, plants) with 2|16.
- Their placed cost is refunded to the ledger, the "removed by player" bit is set, and a jade puff and actionbar show "+0,5 Batu kembali ke Gudang Sekte".
- This applies in creative too, with no XP.
- Placed blocks can therefore never become items, which keeps the 2 : 1 bulk costs safe.

**Explosions** (`sectBuildExplosionCredit = true`). A mixin on `ServerExplosion.interactWithBlocks` handles sect cells: they are removed with no item drops and their placed cost is credited. Perbaiki can rebuild them.

**Accepted minor leak.** A piston-moved sect block mined elsewhere drops normally. It is effortful, the cost invariant bounds valuable categories, and it is documented.

**Surplus.** Batu, Tanah and Kayu units **beyond the whole template's remaining bill** can be taken out 1:1 as cobblestone, dirt or oak planks, one stack per click. There are no other withdrawals.

**Donors (LAN).**
- Members of the same `/sect` may deposit through the stele or the read-only tablet.
- `donors {uuid: units}` feeds the **Prasasti Penyumbang** list and a thank-you line in sect chat, at most one per player per minute.

**Sumber / Source.**
- *A Record of a Mortal's Journey*, ch. 153: Yue Lu Hall stores and supplies materials.
- *Return of the Mount Hua Sect*: a Xi'an merchant guild starts backing Mount Hua after Chung Myung's group heals Elder Hwang Mun-yak, and Hwang Mun-yak declares himself Mount Hua's sponsor. This gives sect-mate deposits and the donor list.
- Refunds and surplus are game design.

---------------------------------------------------------------------------------------------------------------------

### 21.14 Construction missions, gathering expeditions and builders

**1. Misi Tingkat (tier missions)** are the user's "missions to gather resources".
- Every bill line of the current tier is a standing mission, for example "Kumpulkan 1.570 Batu — Formasi Agung" or "Serahkan 1 Lodestone".
- It completes automatically when the ledger covers the line.
- Reward, once per (template, tier, line): pool PK `min(30, ceil(units × rate))`.
  - Rates: Batu and Tanah 0.02; Kayu 0.03; Genteng, Kapur, Pernis and Flora 0.05; Logam 0.1; Permata 0.25.
  - Exact items pay 5 each (lodestone 20).
- When the current tier is covered, the next tier's lines show as "Persiapan".

**2. Daily offers** (Misi panel, rolled at sunrise).
- Count: `3 + floor((S − 1)/3)` offers, so 3, 4 or 5. They are picked weighted by the current shortfall (or the next tier's shortfall once the current one is covered).
- **Setoran (delivery), 60%:** "Bawa 96 Kayu".
  - Amount: `clamp(round_to_stack(shortfall × U(0.20, 0.35)), 16, 1024)`.
  - Progress counts deposits in that category after accepting, by the owner or sect mates.
  - Reward: pool PK `ceil(amount × 2 × rate)`, at most 60 per offer, plus **Berkah Pengrajin**, +20% of the amount as bonus units.
- **Ekspedisi Berkah, 40%:** a gathering trip in that category with ×1.5 yield today.
- **Daily cap:** 150 pool PK from build missions, separate from the 300 of Kantor Misi.
- When the template is complete: no offers; the panel reads "Sekte telah lengkap".
- **No renown is awarded by 6b.** Renown keeps coming only from disciples (20.3.1).

**3. Ekspedisi Pengumpulan (gathering expeditions).** Always available, pure data.
- **Departure:** choose a category and 1–3 disciples, then `Halls.storeAway(owner, e, returnAt, "build_gather", info)`. The return handler is registered with `Halls.addReturnHandler("build_gather", …)`. B's cloud-step look is reused.

| Category | Who may go | Difficulty | Base yield (short trip) |
|---|---|---|---|
| Batu, Tanah | any rank, including Tercatat | 0 | 48 |
| Kayu | any rank | 0 | 32 |
| Flora | any rank | 0 | 16 |
| Genteng | Luar+ | 1 | 24 |
| Kapur | Luar+ | 1 | 16 |
| Pernis | Luar+ | 1 | 12 |
| Logam | Luar+ at Qi Refining+ | 2 | 8 |
| Permata | Dalam+ | 4 | 3 |

- **Duration:** short 6,000 ticks or long 24,000 ticks. A long trip yields ×4.5.
- **Yield:** `base × (1 + 0.4 × P)`, with P from 20.3.10 (`max m + 0.5 × Σ others`).
- **Success:** `clamp(0.75 + 0.05 × (P − difficulty), 0.30, 0.98)`. On failure the party brings 30% of the yield and returns at 50% HP. Never death, never Qi Deviation.
- **Disciple rewards:** PK 3 (short) or 10 (long) × `(1 + difficulty/4)` via `SectRanks.addPk`; a bar share of 3% or 8% via `DisciplePaths.grantBarShare`.
- **Parties at once:** `1 + floor((S − 1)/3)`. A disciple on a gathering trip holds no duty, builder post or expedition.

**4. Pekerja Bangunan (builders).**
- **Assignment:** up to 4 disciples of any rank, set on the Pekerja panel. They get **activity 10 BUILD** (a spine addition: held like activities 1–8 but with a movable spot; they fight only when hit).
- **Work spot:** from `EngineApi.workSpot(owner)`, a perimeter cell of the open component. It changes every 20 s and with each new component. A disciple whose path fails for 10 s cloud-steps to it.
- **Effect:** +25% rate for each builder within 24 blocks of the open component.
- **Pose:** server-side `swing(MAIN_HAND)` every 15–25 ticks, plus material dust FX.
- **Rewards:** +1 PK per 2,400 active ticks (cap 30 per day) and 0.5 XP per minute (`XpSource.SECT`).
- **Idle builders** (site paused or waiting) meditate at the plaza.

**Sumber / Source.**
- Delivery missions paid in merit: *A Will Eternal*, ch. 9 and 27 (the Missions Office pays merit points for delivered spirit plants); *Martial Peak*, the Contribution Hall.
- Gathering trips: *BTTH*, trips into the Magical Beast Mountain Range; *Soul Land*, escorted trips into the Star Dou Great Forest and the Hunting Spirit Forest.
- Builders: an adaptation of the chore model of *Renegade Immortal* (Wang Lin's water vats) and *A Will Eternal* (the Ovens). No canon about construction work is claimed.

---------------------------------------------------------------------------------------------------------------------

### 21.15 Pacing and performance

```
rate (blocks/s) = (6 + 3 × (S − 1)) × sectBuildSpeed × (1 + 0.25 × activeBuilders) × (bank > 0 ? 2 : 1) × msptFactor
msptFactor: average MSPT < 30 ms → 1 · 30–40 ms → 0.5 · ≥ sectBuildMsptPause (40) → 0 (pause)
acc += rate / 20 per tick; every 4 ticks place min(floor(acc), 4 × sectBuildMaxPerTick) ops, kept inside one 16³ section where possible
skips cost 0.1 op; at most 256 checks per tick; one global budget across all sites (round-robin)
```

**When construction runs:**
- the hall is active and the stele stands on its anchor cell;
- the owner, or on LAN a sect mate, is online in the same dimension within `sectBuildRange` = 160 horizontal blocks of the stele (template 9's farthest corner is 128);
- the target chunk is already loaded.

**Pauses and multipliers:**
- Construction never force-loads chunks.
- It pauses while a Kehancuran job runs.
- An optional client hint (`sectBuildFpsHint`) halves the rate while that player's FPS stays below 30 for 5 s.
- **Banked time:** while a funded component waits only because the owner is away, `bank += elapsed ticks`, up to `sectBuildBankTicks` (24,000, as for duties). Banked ticks double the rate until used up. Nothing is banked while the site waits for materials.
- In singleplayer, alt-tabbing to Valorant pauses the game, so nothing builds and nothing is banked. The help text says so.

**Base rates and time while present:**

| Template | Base rate (blocks/s) | Ops (flat .. hillside) | Minutes at base rate | With 4 builders | + bank |
|---|---|---|---|---|---|
| 1 | 6 | 8k .. 10k | 22 .. 28 | 11 .. 14 | 6 .. 7 |
| 2 | 9 | 16k .. 20k | 30 .. 37 | 15 .. 19 | 8 .. 9 |
| 3 | 12 | 27k .. 34k | 38 .. 47 | 19 .. 24 | 10 .. 12 |
| 4 | 15 | 42k .. 53k | 47 .. 59 | 24 .. 30 | 12 .. 15 |
| 5 | 18 | 60k .. 77k | 56 .. 71 | 28 .. 36 | 14 .. 18 |
| 6 | 21 | 90k .. 115k | 71 .. 91 | 36 .. 46 | 18 .. 23 |
| 7 | 24 | 120k .. 155k | 83 .. 108 | 42 .. 54 | 21 .. 27 |
| 8 | 27 | 160k .. 215k | 99 .. 133 | 50 .. 67 | 25 .. 33 |
| 9 | 30 | 220k .. 290k | 122 .. 161 | 61 .. 81 | 31 .. 40 |

Gathering materials is the real limit. Placement is just slow enough to watch the sect grow.

**Costs:**
- **Idle:** one site check per second.
- **Active:** usually 1–6 `setBlock` per tick on average, a hard average cap of 8, in bursts every 4 ticks. Bursts cut client section rebuilds and light packets 4–5×.
- **Emission:** off-thread.
- **Memory:** one open slice list (< 0.2 MB) plus the cell index (about 0.5 MB worst case for template 9).
- **Break event:** a bitset lookup.
- **Client:** the hologram only during preview (≤ 800 gizmos per tick); one wireframe and label within 64 blocks; sparks ≤ 8/s × Effect Quality; mist within 64 blocks, off at Minimal.

**Sumber / Source.** Game design.

---------------------------------------------------------------------------------------------------------------------

### 21.16 Visuals and sound

All client-side, scaled by Effect Quality, via `FxType.SECT_BUILD`.

**Hologram.**
- Drawn with vanilla `net.minecraft.gizmos.Gizmos` (`line`, `cuboid`, `rect`, text) from `ClientTickEvents.END_CLIENT_TICK`. This must be verified inside the per-tick gizmo collection; the fallback is dust particles every 10 ticks. Fabric `LevelRenderEvents.BeforeGizmos` is the alternative hook.
- What it shows:
  - jade light curtains along the footprint edge, from the ground to +3;
  - gold corner pillars up to the envelope height;
  - the gold axis line;
  - terrace outlines at their fitted heights;
  - component boxes coloured by tier, up to the scrubber value;
  - orange or blue cut and fill ticks per 4×4 cell;
  - a red X on each obstacle (≤ 256), a blue X on water to fill;
  - labels for the main components.

**While building:**
- 3–6 block-particle crumbs and one jade spark per placement, batched ≤ 8 per payload per tick per site.
- The block's own sound at 0.35 volume on every 4th placement, at most 5 per second.
- The open component shows:
  - a faint gold wireframe and a label such as "Paviliun Kitab · 63%";
  - a thin **work mist** ring at the current layer, which also softens the half-built look;
  - gold qi threads from each builder to that layer.

**Ceremonies:**
- **Component done:** cherry-petal burst along its roofline, amethyst chime, actionbar "Paviliun Kitab selesai".
- **Tier done (10 s):**
  - disciples in the territory turn and bow (the recruit bow is reused);
  - 3 bells; a gold light pillar from `main_hall`; lantern orbs rising from finished components; a gold ribbon along the axis;
  - title "Sekte ‹nama› · Tingkat 5 — Formasi Agung";
  - a sect chat line, a toast, a log line, advancements.
- **Template complete:** the grand version of the tier ceremony, plus the server line for template ≥ 5.
- **Blocked ping:** a red X shimmer every 5 s for up to 30 s.

**Mist:**
- **Cloud sea (templates 6–9):** soft white billboard particles at valley level inside the footprint, within 64 blocks.
- **Island mist** replaces water off islands.

**Sumber / Source.** Game design. The petals echo the plum motif of *Return of the Mount Hua Sect*.

---------------------------------------------------------------------------------------------------------------------

### 21.17 Hall level and phase-6 integration (replaces the 20.3.1 building caps)

```
buildingCap(owner) = no template ? scanCap (20.3.1 scan, unchanged)
                                 : max(frozenScanCap, tier)      // frozenScanCap = core.bcap at confirmation
hallLevel = HallLevel.level(renown, buildingCap) = max(1, min(renownLevel, max(CAP_STELE = 2, buildingCap)))
```

**Why not the pure `min(renown, tier)`:** a hall that had already scanned, for example to cap 6, would drop to 2 on confirming a template. With a frozen cap, hall level never drops because of a construction choice.

**Rules:**
- While a template exists, the 30-second building scan is skipped, which also saves CPU.
- The tier counts only while the stele stands on the anchor cell.
- **`bflags` come from the tier:** `B_PAVILION` at T ≥ 3, `B_DRILL` at T ≥ 7, `B_TOWER` at T ≥ 9. `B_FORMATION` stays the real active-formation check. `bflags` feed the "pavilion" info flag and the lost-art restoration (20.3.8).

| Integration | Rule |
|---|---|
| Aula tablet | the building checklist is replaced by `SectBuild.statusLine(owner)` |
| Territory | radius = `max(20 + 4L, R_t)` (Table B); \|dy\| ≤ 32 is unchanged |
| Disciple cap | `+ capBonus` (1 for templates 5–9 at T9) |
| Candidates | +1 per arrival (templates 6–9, tier ≥ 7); arrival spots from `anchor("arrival")` and the kept ring cells |
| Duty posts | B uses `anchor("post_*")` when present, otherwise the 4 cells at 5 blocks (always open) |
| Formation | the disc is placed inactive; the owner registers it with an amethyst shard (DESIGN 10, 20.3.2 unchanged) |
| Tower | `BlazingTower` is unchanged: rooms lie within its 32-block sphere, the highest is floor 1 |
| Kehancuran | `ProtectedZones` also refuses `insideAnySite(level, pos, 16)`; template 9's corners lie beyond the 64-block stele radius |
| Stele moved | With a template, re-placing the stele anywhere other than the anchor cell opens the 6b move dialog (21.18); construction pauses meanwhile. Mining the stele makes the hall dormant and pauses construction; re-placing it on the anchor resumes. |

**Sumber / Source.** As 20.3.1 (Yue Lu Hall, the Magic Pavilion, Mount Hua's drill ground, the Blazing Sky tower).
- *Return of the Mount Hua Sect*: the sect grows in stone while its fame (renown) comes from victories.
- *Perfect World*, ch. 551/558: rebuild to rise.

---------------------------------------------------------------------------------------------------------------------

### 21.18 Template upgrade, repair, deconstruction, relocation, abandoning

**Naik Templat (template upgrade)** to any larger template, on the same stele and rotation.
- The new footprint contains the old one. Validation covers the new ring and any player-made blocks; old sect cells count as the sect's own.
- The archetypes differ, so the new plan builds from its own T1. To keep the sect looking alive:
  - **Old components keep standing and working** until a new component needs their cells.
  - Before such a component opens, the old sect cells in its volume are dismantled in reverse order (ORNAMENT → … → PLINTH), with a 100% refund for every cell still equal to its plan state.
  - Old components that never collide stay as heritage until the player picks "Bongkar sisa lama".
  - Old function buildings (disc, tower rooms, library, targets) are dismantled last. Formation and tower functions may pause during the switch, with a warning; the new lodestone must be registered again.
- **`guaranteeTier`** = the old tier, held until the new tier reaches it, so hall level never drops.
- **Cost** = the new plan's bill. The screen shows "Dipertahankan 12 · Dibongkar (bahan kembali) 31 · Biaya ±18.400".
- Advancement `sect_upgraded`.

**Periksa & Perbaiki (inspect and repair).**
- An audit reads the DONE components.
- Cells that are air where the plan has a block, and are not marked "removed by player", go on the repair list, paid at the normal cost and rate.
- Cells changed to another block are counted as "diubah pemain" and left alone.
- Auto-repair is **off**, so a doorway the player cut is never refilled.

**Bongkar (deconstruct).**
- Reverse order at the normal rate, with a 100% refund for every cell still equal to its plan state.
- **"Bongkar bangunan"** keeps the landform (fill, retaining walls, paving): a ruined terrace site that can be rebuilt.
- **"Bongkar total"** also removes paving and walls.
- The original terrain is not restored on real sites; credits replaced it.
- The tier falls as tiers are removed. At the end the template is cleared and the hall returns to the scan.

**Relocation** extends A's "Pindahkan Aula", with its 1-day cooldown.
- **Pindah & bongkar:** the old site deconstructs with a refund whenever the owner is near it. The new site goes through founding.
- **Pindah & tinggalkan:** the old buildings stay as ordinary blocks. The break refund stops there and the site is released.
- Both give `guaranteeTier` = the old tier for 10 in-game days, or until reached.

**Lepaskan Templat (abandon):** like "Pindah & tinggalkan" without moving. Hall level returns to the scan.

**Sumber / Source.**
- Growing through the grades:
  - *Martial Peak*: High Heaven Pavilion → High Heaven Palace → High Heaven Cave Heaven.
  - *BTTH*: Pan Men → Xiao Gate → Yan Alliance.
- The ruined terrace that can be rebuilt: *Perfect World*, ch. 551/558, the ruined Heaven Mending Pavilion.

---------------------------------------------------------------------------------------------------------------------

### 21.19 Multiplayer (LAN)

- A site belongs to one player.
- **Same-`/sect` members** get the read-only Bangun tablet and can deposit. Their presence counts for building, and they appear on the donor list.
- **Overlap:** footprint + 16, plus A's 128-block stele rule.
- **Removing player-made blocks or emptying containers** is allowed only in singleplayer without LAN, or by an op. Otherwise the footprint could be used to grief a neighbour's house.
- **Breaking a sect block**, by anyone, refunds the site owner's ledger. A rate-limited line "Blok sekte dibongkar oleh ‹pemain›" goes to Catatan.
- The mod is not a claims mod and adds no protection.

**Sumber / Source.** *Return of the Mount Hua Sect*: the merchant guild backs Mount Hua (21.13).

---------------------------------------------------------------------------------------------------------------------

### 21.20 Data storage

The two modules write separate `HallData` sections through `Halls.section(owner, key)`, so the data moves with the hall:

```
build_site (P6b-cetak) {
  v, plan_v, template (0 none, 1..9), rot (0..3), glaze, seed, plaza_y, snap (packed heights + water surface), levels [int],
  state (0 none, 1 active, 2 paused, 3 deconstructing, 4 relocating, 5 upgrading), tier, perfect_tiers (bits),
  done [id], skipped [id], prio, open {id, phase, layer, reserve {cat: long}, exact {item: n}, defer, since},
  suspended [id], wait {id: {reason, count, sample [pos ≤ 32]}}, keep_cols (packed), orphans [id], stats {placed, removed, credited}
}
build_flow (P6b-alur) {
  v, ledger {cat: long centi}, exact {item: n}, salvage {item: long}, frozen_scan_cap, guarantee {tier, until_day},
  bank, last_tick, flags {use_excavation:1, empty_containers:0, remove_player_blocks:0},
  missions {day, offers [...], lines_paid [..], pk_today}, builders [uuid], donors {uuid: long}, awards [..],
  history [{template, from_day, to_day}]
}
```

**Other stores:**
- **Cell index:** SavedData `kultivator:sect_cells` (P6b-cetak). Per dimension and touched chunk section, it holds two 512-byte bitsets (placed-by-builder, removed-by-player) plus the owner. About 0.4–0.6 MB worst case. It answers refunds, spawn-proofing, explosion credit, Kehancuran zones, repair and "own block" checks without regenerating plans.
- **Site index** (in memory, rebuilt on load): `dim → list(owner, footprint box)`.
- **Showcases and test undo:** SavedData `kultivator:sect_showcases`, plus one `kultivator_build_undo_<id>` per showcase or test site (packed positions, state palette, indices; about 1 MB gzip for template 9).
- **Add to 20.4.6:** `HallData.build_site` (6b engine) and `HallData.build_flow` (6b flow).

---------------------------------------------------------------------------------------------------------------------

### 21.21 UI pages (jade scroll, fits 320×240)

**Bangun tablet** (a new hall tablet, placed second after Aula):
- **Left:** a 120×120 site diagram.
  - Components are coloured by state: done in full colour, open with a pulsing gold edge and %, queued as a faint ink outline, blocked with a red seal, future tiers dotted.
  - Hovering shows name, tier, bill and state.
- **Right:**
  - title "Tingkat 4/9 · Tembok dan Air";
  - the **tier ladder drawn as a mountain path of 9 terraces** (deliberately not the Jenjang pagoda);
  - the open component bar;
  - a rate line: "≈ 22 blok/detik · 3 pekerja · ×2 tabungan";
  - a status line: "Berjalan", "Menunggu bahan: Kayu 120", "Jeda: pemilik jauh", "Terhalang: 14 blok", "Menunggu: belum dimuat", "Jeda: server sibuk", …
- **Panels**, chosen from a vertical seal column on the right edge (no top tab row):

| Panel | Contents |
|---|---|
| Ikhtisar | overview |
| Bahan | bamboo-slip bill bars per category for this tier and the next, exact items, the deposit grid, "Setor yang dibutuhkan" |
| Misi | tier missions, daily offers, gathering parties |
| Pekerja | builders |
| Galian | Peti Galian, surplus, the "Pakai hasil galian" coin |
| Templat | upgrade, repair, deconstruct, abandon, glaze, stats, history, the obstacle list with "Periksa ulang", "Lewati bagian ini", "Selesaikan tanpa blok terhalang", "Prioritaskan" |
| Penyumbang | donors (LAN only) |

- Talismans: *Setor dari tas*, *Pratinjau*, *Konfirmasi* (hold). Jade-coin toggles: *Jeda*, *Pakai hasil galian*.

**Template screen** (21.12): a 3×3 plaque grid (44×36 each) and the detail panel. Talismans "Lihat Tapak", "Putar", "Konfirmasi" (hold 3 s). Warnings show as bamboo slips.

**HUD during preview:** a small jade panel with template, gate direction, warnings and snapshot %, plus an arrow to unscanned parts.

**Sumber / Source.** DESIGN 5b.

---------------------------------------------------------------------------------------------------------------------

### 21.22 Commands

**Player** (merged into A's `aula` node):

| Command | Effect |
|---|---|
| `/kultivasi aula bangun` | status: template, tier, open component %, shortfall, state |
| `… templat` | opens the template screen |
| `… pratinjau <1-9> [utara\|timur\|selatan\|barat] [tinta\|giok\|langit]` | preview from chat |
| `… putar` / `konfirmasi` / `batal` | rotate; confirm (clickable "[Ya, mulai bangun]" after the consent line); cancel |
| `… setor [semua]` | deposit the held stack, or every accepted item |
| `… jeda` / `lanjut` | pause or resume |
| `… prioritas <komponen>` | move a component to the front of its tier |
| `… naik <2-9>` | template-upgrade preview |
| `… perbaiki` | audit and repair |
| `… bongkar <bangunan\|total>` | deconstruct (with a confirm) |
| `… lepaskan` | abandon the template (with a confirm) |

**Debug** (op, `/kultivasi debug aula bangun …`):
- `tingkat-set <0-9>`: sets the counter only, to test the hall-level coupling.
- `komponen selesai`: finishes the open component now.
- `bank <ticks>`.
- `rencana ulang`: recompiles the plan to the current `planVersion`.

**Test** (op, `/kultivasi uji sekte …`; every placement goes to an undo log and still passes preview → confirm):

| Command | Effect |
|---|---|
| `uji sekte templat <1-9> [arah] [glasir] [paksa]` | Sets the template on the player's hall at no cost. With no hall, it gives a stele item and the hint "pasang Prasasti, atau pakai `pamer`". `paksa` skips only the relief, earthwork and water checks, **never** protected blocks, containers, villages or other sites. |
| `uji sekte tingkat <0-9> [instan\|cepat]` | Builds every component of tiers ≤ n at no cost. `instan` = 512 ops per tick, pausing above 50 ms MSPT, with one ceremony at the end. `cepat` = ×20 rate with every ceremony. A lower n instantly removes higher tiers. |
| `uji sekte sumber [penuh\|<jumlah>]` | `penuh` = the remaining bill + 10% and all exact items; a number = that many units in every category |
| `uji sekte pamer <1-9> [tingkat <1-9>] [arah]` | Showcase at the looked-at block (ray ≤ 64) or the player's spot: hologram first, then `uji sekte pamer konfirmasi` builds it instantly. Not a hall; at most 2 at once. |
| `uji sekte pamer hapus [semua]` / `pamer daftar` | restores the world exactly from the undo log / lists showcases |
| `uji sekte hologram <1-9> [tingkat]` | layout hologram only |
| `uji sekte bandingkan` | all 9 footprints nested around the player as a hologram |
| `uji sekte info [<1-9>]` | the real plan stats: components per tier, blocks, bill per category, exact items, ops, minutes |
| `uji sekte periksa <1-9> [arah]` | runs the validator here without founding |
| `uji sekte audit [<1-9>]` | **dry run of all 81 (template, tier) emissions** (see below) |
| `uji sekte kamera <1-3>` | the art QA views (21.8) |
| `uji sekte upacara [tingkat]` | plays a tier ceremony |
| `uji sekte kecepatan <1-50>` | session rate multiplier |
| `uji sekte naik <2-9>` / `uji sekte bongkar` | instant upgrade / instant deconstruction |
| `uji sekte reset` | removes every plan block of the site instantly, restores the undo-logged originals, clears the template |
| `uji sekte misi [selesai]` | rerolls today's offers / returns all gathering parties now |
| `uji sekte pekerja` | makes every loaded own disciple a builder |

`uji sekte audit` reports:
- ops outside the footprint or envelope;
- gravity blocks, and dependants with no support;
- water that is not contained;
- dark spawnable cells;
- stair-shape mismatches with vanilla, air in friezes;
- the colour budget, sightline, density and framed-view rules;
- the anchors (posts open, library ≤ 16, formation ≤ 24, tower geometry);
- cost/deposit invariant violations.

This is the robustness net, since no Gradle tests are run.

**Sumber / Source.** The user asked for test commands (the 20.0 style).

---------------------------------------------------------------------------------------------------------------------

### 21.23 Advancements, text, help

| Advancement | Name (id_id) | When |
|---|---|---|
| `first_stone` | Batu Pertama | confirm a template |
| `sect_tier_5` | Formasi Agung Berdiri | T5 complete |
| `sect_complete` | Sekte Lengkap | any template at T9 |
| `glazed_pagoda` | Pagoda Kaca | a glazed pagoda complete |
| `grand_sect_complete` | Sekte Agung Berdiri | template ≥ 5 at T9 |
| `heavenly_court` | Istana Langit | template 9 at T9 |
| `sect_upgraded` | Melampaui Tapak Lama | a template upgrade completes its guarantee tier |
| `four_builders` | Seribu Tangan | 4 builders at once |
| `first_gathering` | Ekspedisi Bahan Pertama | first gathering trip returns |
| `sect_donor` | Penyumbang Sekte | LAN: deposit to another player's sect |

- **Text:**
  - `lang-src/blueprint.*` holds template, tier, component-kind, glaze, validation and blocked messages.
  - `lang-src/sectbuild.*` holds the flow text: category names, statuses, consent, mission lines, UI and help.
  - Both exist in id_id and en_us.
- **Help topic `bangun`:** mentions the alt-tab pause, the footprint exception and the refunds.

---------------------------------------------------------------------------------------------------------------------

### 21.24 Modules, spine additions, file ownership and contracts

Two agents work in parallel after the 6b spine. **They never edit each other's files.** Anything missing goes into the final report as a TODO.

**6b spine** (orchestrator, after phase 6 is committed and before both modules):
1. `sect/build/api/*`, created with stubs and owned by the spine:
   - `Bahan` (enum of the 9 categories);
   - `Bill` (record: `long[] centi`, `Map<Item,Integer> exact`);
   - `MaterialLedger`, `BuildListener`, `EngineApi` (static facade; the engine registers its implementation);
   - `SiteView`, `ComponentView`, `PlanStats`, `AuditReport`;
   - `BuildFxIds` (PLACE, COMPONENT_OPEN, COMPONENT_DONE, TIER_DONE, FIRST_STONE, BLOCKED, DECONSTRUCT).
2. `Actions.SECT_BUILD = 35` routes to `sect.build.SectBuild.handleAction`. Also `OpenScreen.SECT_TEMPLATE = 5`, S2C `Payloads.BuildPreview(CompoundTag)`, and `FxType.SECT_BUILD` routed to `client/sectbuild/BuildFx`.
3. `DiscipleEntity.ACTIVITY_BUILD = 10`: held like 1–8, with `setActivity(10, spot)` callable again; `holdsActivitySpot` covers 10.
4. `HallScreen`: a "Bangun" tablet slot placed second, filled by `client.sectbuild.BuildPage`.
5. Provider hooks in A's `Halls` / `HallLevel`:
   - `addBuildingCapProvider` (≥ 0 skips the scan);
   - `addBuildingFlagsProvider`, `addRadiusProvider`, `addCapBonusProvider`, `addCandidateBonusProvider`;
   - `addArrivalSpotProvider`, `addStatusLineProvider`, `addSteleMoveListener`.

   Also B's `Duties.addPostSpotProvider` and D's `ProtectedZones.addZoneProvider`.
6. `KultivatorConfig` fields (clamped on load):

   | Field | Default (range) |
   |---|---|
   | `sectConstruction` | true |
   | `sectBuildSpeed` | 1.0 (0.25–4) |
   | `sectBuildMaxPerTick` | 8 (1–32) |
   | `sectBuildMsptPause` | 40 (30–50) |
   | `sectBuildRange` | 160 (96–512) |
   | `sectBuildWhileAway` | false |
   | `sectBuildBankTicks` | 24000 (0–72000) |
   | `sectBuildMaxTemplate` | 9 (1–9) |
   | `sectTemplateRealmGate` | false |
   | `sectBuildBreakRefund` | true |
   | `sectBuildExplosionCredit` | true |
   | `sectBuildSpawnProof` | true |
   | `sectBuildFpsHint` | true |

7. `kultivator.mixins.json` lists `SectSpawnMixin` and `SectExplosionMixin`.
8. Entry points:
   - `Kultivator.onInitialize` calls `sect.build.engine.BuildEngine.init()` and `sect.build.SectBuild.init()`;
   - `CultivateCommand` calls `SectBuild.addCommands(root, debug)`;
   - `TestCommands` calls `SectBuild.addTestCommands(uji)`;
   - `KultivatorClient` calls `client.sectbuild.BuildClient.init()`.

**P6b-cetak (blueprint + placement engine).**
- **Owns (new):**
  - `sect/build/blueprint/*`: the components of 21.9, `RoofBuilder`, `Palette`, `Glaze`, `Rank`, `Interiors`, `Stamp`, `Costs` (placed costs), `templates/T1Unranked … T9HeavenlyCourt`, `Templates`.
  - `sect/build/engine/*`: `BuildEngine` (init, tick, `EngineApi` implementation), `SitePlan`, `Snapshot`, `TerraceFitter`, `Validator`, `Scheduler`, `BuildPlacer` (**the only `setBlock` caller of 6b**), `CellIndex`, `Classifier`, `FluidGuard`, `EntityGuard`, `BreakRefund`, `Showcase`, `Undo`, `PlanAudit`, `PreviewData`.
  - `mixin/SectSpawnMixin`, `mixin/SectExplosionMixin`.
  - `data/kultivator/tags/block/sect_natural.json`, `sect_protected.json`.
  - `lang-src/blueprint.*`.
- **May edit:** nothing outside its own files.

**P6b-alur (founding, deposits, missions, UI, integration).**
- **Owns (new):**
  - `sect/build/SectBuild`: init, the public API below, actions, the info provider, all commands of 21.22 (calling `EngineApi`).
  - `sect/build/flow/*`: `Founding`, `Ledger` (implements `MaterialLedger`), `Deposits` (deposit table), `SteleDeposit` (`UseBlockCallback`), `BuildMissions`, `GatherExpeditions`, `Builders`, `Ceremonies` (implements `BuildListener`), `HallBridge` (registers every provider), `Upgrade`, `Relocation`, `FlowData`.
  - `client/sectbuild/*`: `BuildClient`, `BuildPage`, `TemplateScreen`, `SiteDiagram`, `SiteHologram`, `BuildHud`, `BuildFx`, `MistFx`.
  - `lang-src/sectbuild.*`, the advancements of 21.23, help topic text `bangun`.
- **May edit:** nothing outside its own files.

**Contracts** (the spine stubs them; each owner fills them):

```java
// flow implements, engine calls
interface MaterialLedger {
  boolean reserve(UUID owner, Bill bill);                       // all-or-nothing
  void release(UUID owner, Bill unused);                        // refund of an unused reservation
  void credit(UUID owner, Bahan cat, long centi, String reason); // digging, break refund, explosion, dismantling
  void salvage(UUID owner, ItemStack stack);                    // Peti Galian, never lost
  long depositValueCenti(Item item); @Nullable Bahan categoryOf(Item item); // for the audit invariant
}
// flow implements, engine fires
interface BuildListener {
  void onConfirmed(UUID owner, int template); void onComponentStarted(UUID owner, ComponentView c);
  void onComponentDone(UUID owner, ComponentView c); void onTierDone(UUID owner, int template, int tier);
  void onBlocked(UUID owner, ComponentView c, int count, List<BlockPos> sample); void onWaitingFunds(UUID owner, Bill shortfall);
}
// engine implements (static facade in api)
EngineApi.preview(ServerPlayer, int template, int rot, Glaze) → long ticket        // async; PreviewData via previewData(ticket)
EngineApi.previewData(long ticket) → @Nullable PreviewData                          // .toTag() is the BuildPreview payload
EngineApi.confirm(ServerPlayer, long ticket) → @Nullable Component error
EngineApi.template(UUID) / tier(UUID) / site(UUID) / components(UUID) / remainingBill(UUID, int tierOr0)
EngineApi.tierBill(int template, int tier, Glaze) → Bill; stats(int template) → PlanStats; audit(@Nullable Integer) → AuditReport
EngineApi.isSectCell(ServerLevel, BlockPos); insideAnySite(Level, BlockPos, int margin); anchor(UUID, String) → @Nullable BlockPos
EngineApi.workSpot(UUID) → @Nullable BlockPos; setRateMultiplier(UUID, double); setBank(UUID, long)
EngineApi.pause(UUID, boolean); prioritize(UUID, String); skip(UUID, String); finishWithoutBlocked(UUID, String); recheck(UUID)
EngineApi.authorize(UUID, boolean removePlayerBlocks, boolean emptyContainers); repair(UUID); deconstruct(UUID, boolean total)
EngineApi.beginUpgrade(UUID, int newTemplate, long ticket); release(UUID, boolean keepBlocks); relocate(UUID, BlockPos newStele, boolean deconstructOld)
EngineApi.testBuild(ServerPlayer, int tier, int speedMode); showcase(...); showcaseRemove(...); reset(UUID)
```

`BuildPreview` tag keys:
- `t`, `rot`, `glaze`, `fp` (world box), `axis`;
- `comps [{id, kind, tier, box[6], y}]`, `zones [{box, y}]`;
- `cut` (packed signed deltas per 4×4 cell), `obst` (long[] ≤ 256), `warn [{sev, key, args}]`;
- `snap` (%), `bill {cat: centi}`, `exact {item: n}`, `ops`.

**Public API for phase-6 modules** (P6b-alur):

```java
int  SectBuild.buildingCap(UUID owner)        // -1 = no template → A keeps the scan; else max(frozenScanCap, tier)
int  SectBuild.buildingFlags(UUID owner)      // bflags from the tier
int  SectBuild.tier(UUID owner); int SectBuild.templateSize(UUID owner)
int  SectBuild.capBonus(UUID owner); int SectBuild.extraCandidates(UUID owner); int SectBuild.territoryRadius(UUID owner)
@Nullable BlockPos SectBuild.anchor(UUID owner, String id); boolean SectBuild.insideAnySite(Level, BlockPos, int margin)
@Nullable Component SectBuild.statusLine(UUID owner)
```

**Integration TODOs** (small edits by the owners after their pass, unless the spine does them as in step 5):
- **A:**
  - `HallLevel` uses the cap and flags providers and skips the scan when the cap is ≥ 0;
  - radius, cap bonus and candidate bonus;
  - the arrival-spot provider in `Candidates`;
  - the stele-move listener in `HallBlock`;
  - the Aula checklist replaced by the status line.
- **B:**
  - `Duties.postSpot` asks the post provider first;
  - optional `Storehouse.take(UUID, Predicate<ItemStack>, int max)` for "Ambil dari Lumbung".
- **C (optional):** a hammer look for activity 10 in `DiscipleHeldItemLayer`.
- **D:** `ProtectedZones.blocked` consults the zone provider.

---------------------------------------------------------------------------------------------------------------------

### 21.25 Test checklist (for `docs/uji/FASE-6b.md`)

1. **Instant build and undo.**
   - Place a stele, then `uji sekte templat 3`, then `uji sekte tingkat 9 instan`.
   - Expected: built in seconds, the ceremony plays, and hall level = min(renown level, 9).
   - `uji sekte reset` restores the land exactly.
2. **Real flow.**
   - Template screen → pick 2 → Lihat Tapak → walk the hologram → rotate → confirm by holding the seal.
   - `uji sekte sumber 200`, then watch T1 build at 9 blocks/s, mirror pairs growing symmetrically.
   - Sneak + right-click the stele with cobblestone: the ledger grows. A plain right-click opens the hall.
3. **Player-made blocks are protected.**
   - Put planks inside a component box.
   - Expected: red X, "Terhalang", and the queue moves on. Remove them and it resumes. Test "Lewati bagian ini" too.
4. **Fluids.** Found next to a pond: the cut edge becomes retaining wall and no water floods. Basins fill statically.
5. **Refunds.**
   - Break a placed lantern: no item, +1 Logam.
   - TNT a wall: no drops, ledger credited.
   - `perbaiki` refills the air cells and skips a doorway that was cut by hand.
6. **Pause and bank.** Walk 200 blocks away: paused, and the bank grows. Come back: ×2 rate. Alt-tab in singleplayer: nothing builds.
7. **Missions and gathering.**
   - Tier mission lines match the bill.
   - `uji sekte misi`: offers match the shortfall.
   - Send 3 disciples on a Batu trip, then `uji sekte misi selesai`: units, PK and bar share arrive.
8. **Builders.** 2 builders walk to the component, swing, and the rate shows +50%.
9. **Hall integration.**
   - A hall with a scanned cap of 6 confirms a template: level unchanged.
   - `bflags` follow the tier.
   - The disc registers with an amethyst shard.
   - The tower rooms count as floors.
10. **Upgrade.** `uji sekte naik 5` on a finished template 3: the old parts keep working until replaced, and hall level does not drop.
11. **Showcase.** `uji sekte pamer 9` on flat land → confirm → `pamer hapus` restores it.
12. **Audit.** `uji sekte audit` reports zero violations for all 81 emissions; `bandingkan` and `info 9` print.

---------------------------------------------------------------------------------------------------------------------

### 21.26 Rejected and deferred ideas

| Idea | Decision | Reason |
|---|---|---|
| `.nbt` structure files | rejected | no terrain fitting, no point queries, 81 binary files, renames break silently (21.9) |
| Pure `hallLevel = min(renown, tier)` | changed to `max(frozenScanCap, tier)` as the cap | it would drop an already-scanned hall on confirming a template |
| Renown gate per tier | rejected | breaks the user's "just deposit and it builds" loop; renown already caps the level |
| Tier-ceremony and mission renown | rejected | 20.3.1: renown comes only from disciples |
| Mirror option | rejected | flips the Bagua trigrams and the plaque text |
| Nether and End sites | deferred | water oddities, ghast damage; Overworld only in v1 |
| Snow and concrete cloud blocks | rejected | they read as snow drifts; client mist instead |
| Steep gamma-curve roofs | rejected | above 45° they read as Japanese; the juzhe rule instead |
| Red terracotta palace walls, sulfur "gold" trim, diorite balustrades, dark-prismarine third glaze, dark-oak pine leaves | replaced | colour clashes or duplicates (21.5) |
| 2×2×2 palace lanterns | rejected | an orange cube; 1×2 lanterns instead |
| Per-column earthwork sweep | rejected | can drop entities; layered earthwork instead |
| Op-index resume cursor | rejected | breaks after any blueprint change; idempotent (phase, layer) cursor instead |
| Regenerating plans in the break event | rejected | server hitch; cell index instead |
| Inserting excavated valuables into B's Storehouse | rejected | its overflow is deleted; Peti Galian instead |
| Esensi Roh category | dropped | gate items are exact items; spirit stones and beast cores join in phase 7 |
| Refusing any lava or any structure piece in the volume | narrowed | would make templates 7–9 almost impossible to place (mineshafts, caves) |
| A 3×3 player-placed stele pedestal | dropped | does not exist in 20.3.1; the stele surround is a T1 component |
| Disciple cap perk up to +3 | reduced to +1 | the 20.1 per-hall entity budget |
| Full ghost-block preview of the whole sect | deferred | too heavy for the laptop; outline, boxes and diagram instead |
| Hopper or chest deposit block | deferred | a new block entity and automation loops; stele sneak-deposit covers it |
| Auto-repair by default | rejected | would undo the player's own changes |
| Restoring original terrain on real sites | rejected | needs a huge undo store; credits replace it (tests and showcases keep exact undo) |
| Realm-locked template choice | optional config, off | the user wants a free choice at founding |
| Time-locked tiers | rejected | gathering already paces the build |
| Territory protection against players | rejected | not a claims mod |
| Surplus → PK | rejected | would turn digging into a PK farm |
| Building all 9 showcases at once | rejected | about 700k ops; `pamer` (max 2) and `bandingkan` instead |
| *Tales of Demons and Gods* "Heavenly Fate Sect" founding | source dropped | No such sect: "Heavenly Fate" is a realm. Nie Li is promoted to Sect Master of the Divine Feathers Sect (around ch. 442) and co-founds the Demon Alliance; neither is founding a sect. |
| *Perfect World* as the source for Holy Lands or Cave Heaven | source dropped | Holy Lands are not systematic there; 洞天境 is a cultivation realm, not a force |
| *Reverend Insanity* as a source for sects | source dropped | blessed lands and grotto-heavens are personal immortal apertures (term only) |
| *A Record of a Mortal's Journey* "First-Rate Sect" label, yellow maple groves as text | source dropped | they are the "Seven Great Sects"; Yellow Maple Valley gives only the name |
| *Martial Peak* as the source for rate grades | source dropped | its sects rank by territory |
| *BTTH* Misty Cloud "long stairs and summit plaza", "Holy Pill City", testing stone "in a public plaza" | source dropped | unverified; the testing stone stands in the Xiao clan's testing ground |
| *Soul Land* storey counts as canon | not claimed | the pagoda is a martial soul; the 7 and 9 floors are a design reading |
| Floating islands chained to the ground as canon | not claimed | *Liezi* has turtles, not chains; the chains are the user's request and art direction |
| *I Shall Seal the Heavens* Outer and Inner Sects on separate mountains | corrected | the Inner Sect is on the upper terraces behind a barrier |
| *A Will Eternal* Violet Cauldron Peak as the pill peak | corrected | the pill peak is Fragrant Cloud Peak (Puncak Awan Harum) |
| *Return of the Mount Hua Sect* Eunha chapter number | dropped | the concept is kept; no chapter could be verified |

## 22. Phase 6c — Teknik Dewa (god-level techniques)

> **User request (2026-10-17, Indonesian, paraphrased):** "Why only 23 techniques? Make a LOT. Make them overpowered and destructive at god level. The higher the cultivation level, the more OP. More techniques, and stronger effects too."
> **User rule:** every technique idea comes from a real cultivation novel, donghua or manhua.
> **User decision (DESIGN 0, "Saklar gampang"):** real terrain destruction from the player's own techniques exists only through Mode Kehancuran.
> - It is OFF by default and switched on with one click in the Kitab Teknik, or with `/kultivasi kehancuran nyala|mati`.
> - There is **no oath screen** for the player's own switch, only a one-line red chat warning with the undo command.
> - A dedicated server also needs the server permission of 20.3.21.
>
> **Conflict note.** The computed task text said "oath + server permission". DESIGN 0 records the later user decision, and this section follows DESIGN 0.
>
> **How this section was made.** It merges the systems design, three technique lists (Sword/Fire/Ice/Lightning, Body/Life/Shadow/Space, Universal), a lore verification pass and a balance/feasibility critique. Duplicates are merged. Names that failed verification are renamed, reworded as "terinspirasi dari / inspired by", or dropped (22.20).

**Result**

| | Count |
|---|---|
| New techniques | **146**: 145 for the player + 1 disciple-only (`karma_domain`) |
| Legacy techniques (all 23 made stronger, 22.10) | 23 |
| **Total** | **169**; the player can learn **168** |

| Set | Kuning | Misterius | Bumi | Langit | Abadi | Dewa | New |
|---|---|---|---|---|---|---|---|
| Pedang (Sword) | – | 4 | 4 | 4 | 1 | 1 | 14 |
| Api (Fire) | – | 4 | 5 | 4 | 1 | 1 | 15 |
| Es (Ice) | – | 4 | 4 | 4 | 1 | 1 | 14 |
| Petir (Lightning) | – | 5 | 4 | 3 | 1 | 1 | 14 |
| Tubuh (Body) | – | 3 | 5 | 3 | 1 | 1 | 13 |
| Kehidupan (Life) | – | 4 | 4 | 3 | 2 | 1 | 14 |
| Bayangan & Angin (Shadow) | – | 2 | 3 | 3 | 1 | 2 (1 disciple-only) | 11 |
| Ruang (Space) | – | 3 | 4 | 3 | 1 | 2 | 13 |
| Universal | 4 | 7 | 10 | 13 | 3 | 1 | 38 |
| **Total** | **4** | **36** | **43** | **40** | **12** | **11** | **146** |

---------------------------------------------------------------------------------------------------------------------

### 22.1 Principles

1. **Entity-only by default.** DESIGN 0 binds every technique:
   - no block is placed, broken, burned or changed;
   - read-only raycasts only; fire on entities only (`DaoTechniques.ignite`);
   - particle lightning only, never a `LightningBolt`; no `Explosion`, no projectile entities, no weather or time change;
   - none of the forbidden mob effects.

   God-level power shows as:
   - huge client visuals: particles, client-only phantom Display entities, sky tears, camera shake, flashes;
   - huge damage, control and knockback to **monsters only** (`Targeting`).
2. **Real destruction only in Mode Kehancuran** (22.11). `kehancuran/WorldBreaker` stays the only block-removing code, besides the phase-6b `BuildPlacer`. It keeps all caps, the MSPT pause, the undo log and the protected zones.
3. **Higher is far stronger.** Damage follows an exponential power curve P (22.5):
   - Qi Refining equals today's damage;
   - Dewa Abadi is about 165× today's, and stars (disciples now, the player in phase 7) keep it growing.

   Areas, durations, counts, visual size and destruction size also grow with rank. The strongest techniques unlock only at Kenaikan Abadi, Dewa Abadi and the Dewa gate.
4. **Fair in arenas.** Sparring, duels and every phase-6 arena use the old linear power (22.14.5). Bosses, the player's own Heart Demon and Dao Protector waves have caps or rules (22.14).
5. **Sources.** Every technique has a lang line `kultivator.technique.<id>.source` naming the work and the named technique or concept.
   - Unverifiable names are written "Terinspirasi dari … / Inspired by …".
   - Numbers, formulas, UI and safety rules are game design and are never presented as canon.
6. **Data-driven.** A technique is a `TechniqueSpec`: a timeline of primitive steps, FX layers, sounds and an optional destruction shape. Hand-written code exists only for the legacy 23 and 9 specials.
7. **Laptop first** (Valorant runs on the same machine). Hard server and client budgets apply (22.15): one entity query per evaluation, simulated projectiles, client-only displays and a particle token bucket.
8. **Text and look.**
   - All text in `id_id` + `en_us`. The Indonesian name is primary; the English name and the source sit in the tooltip.
   - Every number is a `public static final`, so the codex shows the real values.
   - All GUI is the jade scroll of DESIGN 5b, never Butter Bot's look.
9. **Saved ids never change.** Technique ids are strings in saved data.
   - The 23 enum ids stay exactly as they are.
   - New ids are final from first release.
   - An id whose spec disappears is kept in saved data and ignored.

---------------------------------------------------------------------------------------------------------------------

### 22.2 The framework

#### 22.2.1 Types and files (package `dev.kultivator`; owners in 22.18)

| File | Side | Purpose |
|---|---|---|
| `data/TechniqueSpec.java` + `Builder` | common | the immutable spec (22.2.2) |
| `data/TechniqueRegistry.java` | common | `byId`, `byNet`, `all`, `ofDao`, `ofGrade`, `legacy(Technique)`, `register`, `freeze`, `special(id, handler)` |
| `data/TechniqueGrade.java` | common | `HUANG, XUAN, DI, TIAN, ABADI, DEWA` (22.7) |
| `data/TechniqueType.java` | common | `SERANG, AREA, KENDALI, GERAK, LINDUNG, PULIH, PANGGIL, WILAYAH, UBAH, PASIF, PAMUNGKAS, INDRA` |
| `data/Technique.java` | common | unchanged ids and ordinals; gains `spec()` |
| `technique/spec/dsl/*` | common | **data records only**: `Step`, shapes, payloads, `Cond`, `Hook`, `FxLayer`, `SoundCue`, `DShape`, `Bonus`, `Route`, `Feat` |
| `technique/spec/TechniquePower.java` | common | all math (P, A/D/N, qi, cooldown, caps, `finalDamage`), so the codex shows exactly the server numbers |
| `technique/spec/run/*` | server | `TechniqueRunner`, `CastContext` impl, `Caster`, `StatusTracker`, `Phantoms`, `VirtualProjectiles`, `Fields`, `QiShield`, `Forms`, `LegacyArt`, `Mastery`, `Loadout`, `Unlocks`, `FeatTracker`, `Codex` (actions), `TechniqueTests` |
| `technique/arts/*Arts.java` | common | spec tables: `SwordArts`, `FireArts`, `IceArts`, `LightningArts`, `BodyArts`, `LifeArts`, `ShadowArts`, `SpaceArts`, `UniversalArts`, `ImmortalArts` |
| `technique/special/*` | server | the 9 `SpecialTechnique` handlers (22.2.7) |
| `client/fx/spec/*` | client | template renderers, `PhantomDisplays`, custom renderers, `CameraShake`, `ScreenFlash`, `FxBudget` |
| `client/codex/*` | client | the Kitab Teknik, Cincin Segel, Roda Segel, quick seals, HUD tiles |

#### 22.2.2 `TechniqueSpec` fields

| Field | Meaning |
|---|---|
| `id` | `[a-z0-9_]{1,32}`, saved, never renamed |
| `netId` | network id (22.2.3), never reused |
| `dao` | `@Nullable Dao`; null = universal |
| `grade`, `sub` | grade and Rendah/Menengah/Tinggi (0/1/2) |
| `type` | codex filter, disciple AI role, default teachability (22.7) |
| `minRealm`, `minLayer`, `minStage` | realm gate (layer only for Kenaikan Abadi) |
| `gate` | `NONE`, `DEWA_I`, `DEWA_II` (22.8.8) |
| `dewaStars` | disciple star gate for DEWA specs |
| `prereq` | list of `(id, masteryLevel)` |
| `minComprehension` | Dao comprehension needed (0 = none) |
| `routes` | set of `Route` (22.8) |
| `qi` / `qiPct` / `qiPerSecond` | flat cost; or % of max qi (not reduced by realm gap; mastery may lower it to 50% at most); or drain |
| `cooldownTicks`, `charges` | base cooldown (≥ 10); optional charges with separate recharge |
| `charge`, `channel`, `toggle`, `stages` | flow (22.3.4) |
| `timeline` | `List<Step>` |
| `fx`, `sounds` | per phase (22.4) |
| `destruction` | `List<DShape>` at K = 1 (≤ 5 shapes, one shared block cap), `naturalOnly` |
| `perfection`, `peak` | mastery level 3 and 4 bonuses (22.6) |
| `flags` | `TEACHABLE`, `NO_TEACH`, `CAST_ALWAYS`, `REQUIRES_TARGET`, `PRECISE`, `NO_ECHO`, `FORBIDDEN` (demonic: health price, confirm once), `DISCIPLE_ONLY`, `PAVILION` |
| `special` | `@Nullable` handler id |
| `legacy` | `@Nullable Technique` |

**Lang keys** (derived, never stored):

| Key | id_id | en_us |
|---|---|---|
| `kultivator.technique.<id>` | Indonesian name | English name |
| `.desc` | effect, numbers as coefficients | same |
| `.source` | "Sumber: <work> · <named technique>" | "Source: <work> · <named technique>" |
| `.lore` | optional line | optional line |

Work titles stay in English in both languages.

#### 22.2.3 Registry rules

- **Built once**, from the main entrypoint `Kultivator.onInitialize` only. It runs on both physical sides, so the client knows every spec without a sync. Registering in the client initializer as well would double-register in singleplayer.
- Each `*Arts.register(Sink)` runs in a fixed order, then `freeze()`.
- A duplicate id or netId, or a late register, logs an error and refuses the spec. Startup never crashes.
- **netId blocks.** Each Arts file uses only its own block.

  | Block | Used by |
  |---|---|
  | 0–22 | legacy enum ordinals (`FxType.TECHNIQUE` keeps them) |
  | 100–199 | Universal |
  | 200–299 | Sword |
  | 300–399 | Fire |
  | 400–499 | Ice |
  | 500–599 | Lightning |
  | 600–699 | Body |
  | 700–799 | Life |
  | 800–899 | Shadow/Wind |
  | 900–999 | Space |
  | 1000–1099 | Dao-less Abadi/Dewa ultimates |

- **`uji teknik audit`** checks:
  - unique ids and netIds;
  - all four lang keys in both languages (read from the merged lang in the jar);
  - a non-empty source line;
  - every CAST has an FX layer; every PAMUNGKAS has a SHAKE layer;
  - prerequisites exist;
  - no ECHO on non-instant types (22.3.4).

  A parameter above a hard cap is clamped and reported as a **warning**: visual size may exceed hit size, hit size never exceeds the cap.

#### 22.2.4 The 23 legacy techniques and storage

- `enum Technique` stays with its ids, ordinals and existing cast code. The registry adds one spec per constant with `legacy = X`, `netId = ordinal` and the `LegacyArt` handler, which calls the old code with a `CastContext`. Constants become scaled reads (`ctx.area(BASE, CAP)`, `ctx.count`, `ctx.duration`; 22.10).
- **Migration (spine).** `CultivationData.load` and `DiscipleData.load` stop filtering learned ids, the selection and cooldown keys through `Technique.byId`. They keep every id matching `[a-z0-9_]{1,48}`, up to 512.
  - New methods: `knows(String)`, `learn(String)`, `selectedId()`, `cooldownLeft(String, now)`; the enum overloads delegate.
  - `learnedTechniques()` still returns legacy enums; `learnedSpecs()` returns specs and skips unknown ids.
- **Callers that move to specs:**

  | Area | Files |
  |---|---|
  | Technique core | `Techniques` |
  | Screens and HUD | `TechniquesPage` (becomes a launcher), `SealCard`, `QiHud`, `Glyphs`, `CultivationScreen` |
  | Commands | `CultivateCommand`, `TestCommands` |
  | Dao and books | `DaoPaths`, `TechniqueBooks`, `Comprehension` |
  | Disciples | `DiscipleData`, `DiscipleCultivation`, `DiscipleTechniques`, `TechniqueTab`, `TeachCard` |
  | Phase 6 | `Bond`, `Heir`, `Contribution` |

  All of these already use string ids.

#### 22.2.5 Builder DSL (what the content modules write)

```java
r.add(art("bloodmoon_immortal_slaying_sword", 204, Dao.SWORD)
    .grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
    .route(Route.feat(Feat.killsWith(Dao.SWORD, Cond.FULL_MOON_NIGHT, null, 30))).comprehension(50).flag(TEACHABLE, PAVILION)
    .qi(140).cooldown(40.0)
    .step(at(20).shape(rain(Anchor.AIM, 32).radius(8, 16).impacts(12, 36).over(40).impactRadius(2))
                .hit(damage(0.9)))
    .step(at(70).shape(zone(Anchor.AIM).radius(4)).hit(damage(3.0), mark(Mark.daoVuln(Dao.SWORD, 0.2), 200)))
    .fx(Phase.CAST, cloudDisc(0xB0102A).above(20), giantItem(Items.NETHERITE_SWORD).scale(8).fall(20, 20).minTier(3))
    .fx(Phase.IMPACT, swordShape(0xB0102A).falling(), shake(0.5, 12))
    .sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.4f, 0.5f)
    .kehancuran(DShape.scatter(2), DShape.bowl(4))
    .perfection(Bonus.extraCount(6)).build());
```

- Every builder method maps one-to-one to a primitive (22.3) or a template (22.4). Unknown parameters do not compile.
- `b→c` in the catalogue means base value b at mastery 0 and spec cap c. Growth is automatic through A/D/N (22.5.3). `lin(b, k, c)` (= b + k × tiers above the gate, capped) is used only where a row says "+k/tier".

#### 22.2.6 Cast pipeline (`Techniques.tryCast(player, spec, CastOptions)`)

`CastOptions = {bypassGates, free, forceVisual, showcase, noKehancuran}`. Only `/uji` uses anything other than the defaults.

1. **Refusals, in order.** Each refusal is shown in the action bar and spends nothing.
   1. Dead or spectating. Cloud Step is passive and refused. A mounted Flying Sword dismounts.
   2. `CAST_BLOCKERS`.
   3. The technique is not known.
   4. Realm, layer or stage too low.
   5. Wrong Dao.
   6. Dewa gate or prerequisite missing.
   7. Deviating.
   8. Breaking through (a heart-demon trial is allowed).
   9. Global cooldown: 10 ticks after any cast. A STAGES recast skips it.
   10. Ultimate lockout: 20 s after a PAMUNGKAS.
   11. The technique's own cooldown or charges.
   12. Not enough qi.
   13. Concurrency limits (22.15).
2. `Meditation.stop`.
3. **Snapshot a `CastContext`:** P, mastery level, A/D/N, tier t, eye, look, aim point (read-only clip along a loaded path), locked target, seed, visual tier, Kehancuran flag.
4. **Target precheck.** Attack specs without `CAST_ALWAYS` are refused with a reason when no hostile is possible: none in the first shape, or none within 32 for delayed or travelling shapes. `REQUIRES_TARGET` (TARGET, CHAIN) needs a locked target. `forceVisual` skips the check.
5. **Spend.**
   - Qi, cooldown, global cooldown and lockout.
   - `Comprehension.onTechniqueUsed` grants +0.15 × grade factor (1 / 1.2 / 1.5 / 2 / 2.5 / 3), only when the cast hits a hostile, and at most 3.0 per Dao per in-game day.
   - Advancement checks.
6. `TechniqueRunner.start`. Step 0 runs now; later steps run on the server tick. FX go out as `TechFx` events. Destruction shapes go to `Kehancuran.submitSelf` at IMPACT (22.11).
7. **End.** END FX play and mastery points are granted (22.6).
8. **Abort.** Death, logout or a dimension change ends every cast of that caster at once: fields, phantoms, projectiles and forms.

#### 22.2.7 Special techniques

```java
public interface SpecialTechnique {
    boolean cast(CastContext ctx);                       // true when the technique happened
    default void tick(CastContext ctx) {}
    default void end(CastContext ctx, EndReason reason) {}
    default @Nullable Component blocker(CastContext ctx) { return null; }
    default boolean supportsDisciples() { return false; }
}
```

Specials obey every rule of 22.3.5. A step can call a handler per target as the payload `special(id)`.

| Handler | Spec | Why it needs code |
|---|---|---|
| `earth_travel` | `worldwalking` | hidden ground travel and emergence, loaded path, safe exit checked before spending |
| `flight_form` | `lei_zhenzi_wings` | real flight through the shared **FlightLease** API (owned by `FlyingSword`), so two flight sources never revoke each other's `mayfly` |
| `cloud_travel` | `somersault_cloud` | stepped safe travel, ≤ 128 blocks, loaded chunks only |
| `seventy_two` | `seventy_two_transformations` | form cycling through STAGES; the gnat form gets a head-room check and a safe teleport on revert |
| `fusion_pair` | `netherworld_white_tiger` | master and disciple cast together (bond, disciple qi, shared cooldown) through the spine `DisciplePowers` API |
| `mystery_fusion` | `profound_mystery_fusion` | both Daos' `domainPulse` ×2 plus at most 8 pair rows |
| `echo_wheel` | `six_dao_reincarnation` | replays recent arts; excludes PAMUNGKAS, itself, specials and ECHO; one boss cap; no mastery, no Kehancuran |
| `space_fold` | `spatial_ancestral_symbol` | moves ≤ 32 non-boss monsters to safe spots near the centre, once per cast |
| `time_bank` | `time_spirit_domain` | damage bank per target, released ×1.5 |

The legacy specials keep their code: `LegacyArt`, Flying Sword, Cloud Step, Divine Sense, Dharma Form. Type PASIF is reserved; no 6c spec uses it, and Cloud Step stays the legacy passive.

---------------------------------------------------------------------------------------------------------------------

### 22.3 Effect primitives

A **Step** has:
- `at` (tick offset) and optional `x n/k` (repeat n times every k ticks);
- one shape;
- hostile payloads, plus `SELF` and `ALLY` payloads;
- an optional condition;
- optional `onHit`, `onKill` and `onEnd` sub-steps.

Sizes are base values that grow with A/D/N and mastery up to the spec cap and the hard caps of 22.15.

#### 22.3.1 Shapes

| Shape | Parameters (default → hard cap) | Semantics |
|---|---|---|
| `SELF` | – | no hostiles; self/ally payloads only |
| `STRIKE` | range 4 → 12, arc 120° → 220°, height 2.5 | targets within range of the eye (horizontal), inside the arc around the look, LOS |
| `CONE` | length 7 → 24, half-angle 30° → 60° | apex at the eye; targets within 1.5 blocks need only dot > 0; LOS |
| `BEAM` | length 16 → 64 (Dewa 96), half-width 1 → 4, pierce, channel/pulse | stops at the first block (read-only clip). `ignoreBlocks` only for SPACE or DEWA specs. Beyond 64: a travelling hit in ≤ 16-block segment boxes, ≤ 2 segments per tick, loaded chunks only. Dewa PAMUNGKAS may travel 128 |
| `FRONT` (new) | length 16 → 64 (Dewa 128 travelling), width 1 → 24, height 3 → 12, speed 1–8 b/t | a travelling wall along the look (crescents, waves, falling staffs). Each target is hit once when the front passes. One query every 4 ticks covering the next 4 ticks of travel; stops at a solid wall unless SPACE or DEWA |
| `NOVA` | radius 6 → 32, `inner` | sphere (or shell) at the caster |
| `WAVE` | r0 1, r1 12 → 40, speed 1 b/t (min 0.5), thickness 2, height 3, origin caster/aim | expanding ring front; hit once when \|dist − front\| ≤ thickness/2. One query of r1 every 4 ticks |
| `ZONE` | range 24 → 64, radius 5 → 24 (Dewa 32), `fromSky h`, delay | cylinder at the anchor after the delay; the client animates the fall |
| `PILLAR` | radius 2 → 6, height 20 → 64, delay 10, `onTargets n` (search 16 → 32) | vertical cylinders, one per target with onTargets |
| `RAIN` | radius 12 → 32, impacts 12 → 40, duration, impact radius 2 → 6, fall 6, targeted 0.6 | impacts spread over time; each targets an unhit hostile with probability `targeted` |
| `CHAIN` | range 24 → 48, jump 6 → 12, jumps 4 → 12, delay 3, falloff 0.85 | first hop = locked target, then the nearest unhit hostile |
| `TARGET` | range 24 → 64, splash 0 → 8 (factor 0.5) | locked hostile |
| `ALL` | radius 16 → 32, max 12 → 32 (Dewa 64), `order NEAREST/STRONGEST/WEAKEST/RANDOM`, `los` | every hostile in range, by order, up to max |
| `RECALL` (new) | max 32 | the still-living targets hit earlier in this cast, within 48 (convergence strikes, shatters) |
| `VOLLEY` | count 1 → 48, range 32 → 64, hit radius 1 → 3, pierce, spread FAN/RING/SKY/ORBIT, stagger 2; mode `SIM` or `SEEK` | **SIM**: simulated projectiles (one merged query per cast per tick, one clip per projectile per tick). **SEEK**: targets picked at launch, hits land at the computed arrival tick, the client animates homing. Never entities |
| `DOMAIN` | radius 10 → 32 (Dewa 40), height ≤ 16 when r > 16, duration 200 → 600 (Dewa 900), pulse ≥ 10 | **follows the caster**; one per caster (a new one replaces the old) |
| `FIELD` (new) | as DOMAIN, plus `square`, `walls` (bounce non-bosses back), `emitters n` (visual) | a **placed** timed zone; ≤ 2 per caster (the oldest ends) |
| `AURA` | radius 3 → 10, duration ≤ 600 | a small follow zone in its own slot (one per caster) |
| `PATH` | half-width 1.2 → 3 | the segment a DASH or BLINK just travelled |

**Anchors:** `AIM`, `TARGET`, `CASTER`, `EACH_TARGET(n)`, `DENSEST(n)` (n points with the most hostile neighbours, from one query, O(n²) over ≤ 32).

#### 22.3.2 Payloads on hostiles

| Payload | Semantics |
|---|---|
| `DMG coef [pierce] [vs {UNDEAD, ARTHROPOD, BOSS, NETHER, END, AQUATIC, FIRE_IMMUNE, WET, FROZEN: ×}]` | through `TechniquePower.finalDamage` (22.5.5). `pierce` uses `kultivator:technique_pierce` (armor-bypassing; soul and space arts) |
| `DMG% share (boss share)` | share of max health; counts in boss caps; arena clamp 15% per cast |
| `DOT coef/s, s, element FIRE/POISON/BLEED/SOUL/QI/COLD/LIGHT` | `StatusTracker`, ticked every 20 with the snapshot P; same spec refreshes; ≤ 4 per target. FIRE also calls `ignite` (powder-snow guard). Water does not end a tracked DOT |
| `IGN s` | `DaoTechniques.ignite` (entity fire) |
| `FRZ t` | frozen ticks + Slowness (Frost Lotus pattern) |
| `BIND t ROOT/STUN/SUSPEND/SLEEP` | transient MOVEMENT_SPEED −100%, FLYING_SPEED −100%, JUMP 0, navigation stopped once, creeper fuse held. Velocity is zeroed only when \|v\| > 0.01; sync on apply and release. STUN adds ATTACK_DAMAGE ×0 (`kultivator:stun`). SUSPEND lifts 1.5 blocks (no-gravity + tag `kultivator.suspended`, cleared on load). SLEEP = a STUN that breaks on damage. Bosses: ×0.25 and STUN/SUSPEND/SLEEP become ROOT; Warden, Wither and the Dragon are never suspended; the Dragon is never bound |
| `KNK` / `PULL` / `LCH` / `SLAM` | through the existing `knock`/`launch` rules and the never-moved list (22.3.5) |
| `EFF effect t amp` | whitelist only: SLOWNESS, WEAKNESS, BLINDNESS, DARKNESS, WITHER, POISON, GLOWING (≤ 64 targets), MINING_FATIGUE, LEVITATION (≤ 40t) |
| `MARK kind` | `VULNERABLE +x%` (≤ 3 stacks, from this caster); `DAO_VULN(dao, +x%)`; `WET` (also true in water or rain); `SOUL ×k` (the next hit by this caster); per-viewer highlight |
| `ATTR attribute amount t` | transient modifiers, never saved: ARMOR, ATTACK_DAMAGE, MOVEMENT_SPEED, FLYING_SPEED, JUMP_STRENGTH, FALL_DAMAGE_MULTIPLIER, KNOCKBACK_RESISTANCE |
| `EXEC threshold (boss 0.05)` or `EXEC maxHealthBelow k×P` | lethal damage through the choke point (loot and credit kept; never `discard`). Bosses only under 5% |
| `DRAIN share [ally share, radius] [qi share]` | heals the caster (and allies) from damage dealt; qi ≤ 10% of max per cast |
| `UNTARGET` | `setTarget(null)` and the brain's `ATTACK_TARGET` memory erased (Warden anger documented as a limit) |
| `TAUNT t` | target set to the caster, brain memory included |
| `STRIP_BUFFS` / `STRIP_EFFECT id` | removes the listed beneficial effects from monsters |
| `LINK share t` | damage to one linked monster is also dealt to the others at the share. Based on damage actually dealt after caps; a LINK hit never triggers another LINK; boss share 0.25 |
| `special(id)` | per-target handler |

**Dao defaults** (applied unless a spec overrides them):
- Fire DMG and DOT vs FIRE_IMMUNE ×0.5.
- Lightning vs WET or FROZEN ×1.25.
- Ice specs marked "shatter" deal ×1.5 to a FROZEN target and end the freeze.

#### 22.3.3 Payloads on the caster and allies

| Payload | Semantics |
|---|---|
| `DASH dist [vertical] [i-frames]` | along the look through open, loaded space only; ends at the last `Targeting.safeSpot`; no fall damage for 40t |
| `BLINK range AIM/BEHIND_TARGET/TO_TARGET/BACK` | safe teleport; chains wait ≥ 3t between hops; SPACE specs may skip the clear-path check; never into portals or unloaded chunks |
| `HEAL share [radius] [over t]` | `Targeting.alliesAround` + own disciples; never monsters |
| `CLEANSE` | removes Poison, Wither, Slowness, Weakness, Blindness, Darkness, Nausea, Mining Fatigue |
| `SHLD share t` | **Perisai Qi** pool (≤ 2 × max health). It never absorbs `#kultivator:unblockable` (tribulation, qi_deviation, out_of_world, generic_kill) |
| `BUFF kind value t` | STRENGTH, RESISTANCE (amplifier ≤ 2), SPEED, JUMP, REGENERATION, FIRE_RESISTANCE, WATER_BREATHING, NIGHT_VISION, INVISIBILITY, ABSORPTION, HEALTH_BOOST, REACH, ATTACK_DAMAGE, ATTACK_SPEED (player), EMPOWER (technique damage, optionally per Dao), REFLECT, LIFESTEAL, QI_REGEN, CD_RATE, COST_MOD (qi/cd of a Dao, optionally only inside the caster's own field) |
| `DR share [FRONT/BACK/ALL] [HP-scaled]` / `DODGE chance` | combined under the caps of 22.14.3 |
| `TRANSFORM form` | one form at a time; SCALE ≤ 4 with the Dharma head-room check (and an IN_WALL check on revert); transient `kultivator:form_*` modifiers |
| `SUMMON kind n t` | **virtual phantoms**, never entities: ≤ 8 per cast, 12 per caster, 48 per server; attack interval ≥ 10t (one query per attack); modes follow/anchor/cling; cannot be hurt |
| `RESTORE_QI share` | ≤ 30% of max qi |
| `CD_RESET maxGrade` | never resets PAMUNGKAS |
| `GRANT_HOOK ON_MELEE_HIT/ON_HURT/ON_KILL/ON_TECHNIQUE_HIT payloads t/charges icd` | timed hooks on the caster; internal cooldown ≥ 4t |
| `COUNTER window store cap → release step` | absorb, then release; the release counts against the cast's boss cap |
| `CHEAT_DEATH charges t` | through the spine `DeathGuard` (22.14.6) |
| `PROJECTILE_GUARD discard / discard+return, arc, radius, charges` | hostile projectiles are `discard()`ed, never deflected: a deflected fireball or skull would hit blocks. "Return" answers with a simulated TARGET hit on the shooter |
| `SELF_PENALTY effects t` | applied when the technique ends (Weakness, qi regen ×0, technique damage ×0.7) |
| `FORBIDDEN_COST hp share (current/max) [qi share]` | `setHealth(max(1, …))`, never damage, so it can never kill; refused below 2 HP |
| `COMPREHENSION +x (cap per cast)` | counts towards the daily cap of 22.2.6 |

#### 22.3.4 Flow and meta

- **CHARGE(t).** A fixed wind-up with the caster slowed (−90%).
  - Pressing cast again releases early at proportional power (≥ 50%).
  - Taking ≥ 20% of max health interrupts it: 50% of the qi is refunded and 25% of the cooldown is set.
  - There is no hold-to-charge: holding the key opens the wheel.
- **CHANNEL(t / pulse).** Steps repeat. Moving more than 1.5 blocks, casting anything else, pressing cast again, or a hit of ≥ the `interrupt` share ends it. `qiPerSecond` drains meanwhile.
- **TOGGLE(qi/s).** A sustained stance with no movement limit. Cast again to end it.
- **STAGES(window, [timelines], qi and realm gate per stage).** Recasts within the window advance the stage and skip the global cooldown. The cooldown starts after the last stage or when the window runs out. This replaces every "sneak to switch mode" idea, because Shift means "no destruction" (22.11.2).
- **CHARGES(n, recharge).** The spec can be cast n times before cooling down; each charge recharges separately.
- **CONDITIONS:**
  - `NIGHT`, `DAY`, `DAWN`, `SKY_OPEN`, `FULL_MOON`;
  - `RAINING`, `THUNDERING` (read only);
  - `IN_WATER`, `IN_NETHER`, `IN_END`;
  - `TARGET_BURNING`, `TARGET_FROZEN`, `TARGET_LOW_HP(x)`, `TARGET_BOSS`, `TARGET_UNDEAD`;
  - `CASTER_LOW_HP(x)`, `FULL_QI`, `HOLDING_SWORD`, `NEAR_ANCHOR(r)`, `TRIB_STRIKES(per, cap)`.

  Each gives a multiplier or extra payloads.
- **STACK.** Per recast within a window, or `onKill` within one cast. Stacks are multipliers inside F.
- **RAMP(+x% per pulse).** Damage grows each pulse on a DOMAIN or FIELD.
- **ECHO(factor, delay 10).** The default level-4 bonus: the steps re-run at that factor with no destruction, an afterimage tint and no mastery.
  - ECHO applies only to instant SERANG, AREA, KENDALI and PAMUNGKAS.
  - It is forced off (`NO_ECHO`) for WILAYAH, FIELD specs, UBAH, PANGGIL, LINDUNG, PULIH, GERAK, INDRA and AURA. Those get `Bonus.duration(0.25)` instead.

#### 22.3.5 Safety rules shared by every primitive (binding)

1. **Who is hit.** Player casts: `Targeting.isHostileTo`. Disciple casts: `DiscipleTechniques.mayHit`. Pets, villagers, passive animals and other players are never hit (sparring/duel partners only under 22.14.5).
2. **Calm neutrals are hit only by PRECISE shapes.** This covers `NeutralMob.isAngryAt(caster, level) == false` and any `AbstractPiglin` whose target is not the caster. PRECISE shapes are STRIKE, BEAM, TARGET, the first CHAIN hop, EXEC and `PRECISE`-flagged ALL. A god-level AoE in the Nether never aggroes a whole fortress.
3. **Never moved:** creeper, ravager, Ender Dragon, Wither, Warden, Elder Guardian and players. Nothing is moved within 8 blocks of a portal or gateway. The check is `PortalIndex.near(level, pos, 8)`, run per moved mob: nether portals through the POI manager (`PoiTypes.NETHER_PORTAL`), end portals and gateways through chunk block entities, cached per cast. Never a block-volume scan. Every moved or hit monster gets `NoGrief.TAG` and a portal cooldown.
4. **No knockback by default.** `kultivator:technique` and `kultivator:technique_pierce` are in `#minecraft:no_knockback`. All movement comes from KNK/PULL/LCH/SLAM payloads, which obey rule 3.
5. **Fire and lightning.** Fire is entity fire only (`ignite`); no fire blocks ever. Lightning is particle bolts only; never `LightningBolt`, `Explosion` or projectile entities.
6. **Loaded paths.** Every ray, teleport and stepped travel first walks its path with `level.isLoaded` (`LoadedPath`) and is cut at the first unloaded chunk. No synchronous chunk loads.
7. **Damage choke points.** All damage goes through `Techniques.hurt` (player) or `DiscipleTechniques.hurt` / `PowerCore.hurt` (disciples). That includes LINK, REFLECT, %-max-health, phantoms and DOT. The silverfish rule and 22.5.5 apply there.
8. **Disciples** also skip `DiscipleTechniques.provokesBlockChanges` mobs (creeper, ghast, blaze, breeze, ravager, silverfish, Wither, heart-bound creaking).
9. **No block reads in hot loops.** Shapes use one entity query and at most one clip per beam, dash or projectile tick.
10. **Mob state lives in memory or transient modifiers only.** Never saved tags (except the cleared SUSPEND tag) and never `setNoAi`, so a crash cannot leave a mob frozen.

#### 22.3.6 Notation used in the catalogue

```
CHG t; CHAN t/pulse; TOG qi/s; ST n (window) …; CHARGES n
@tick[x n/k] SHAPE(params) > PAYLOAD + PAYLOAD; SELF: …; ALLY: …; ? COND ×m; onHit/onKill/onEnd …
b→c = base → spec cap (grows with A/D/N); +k/tier = lin(); t = ticks, s = seconds; coefficients × P
qi n · qiPct n% · cd s
```

Payload abbreviations: DMG, DMG%, DOT, IGN, FRZ, BIND, KNK, PULL, LCH, SLAM, EFF, MARK, ATTR, EXEC, DRAIN, UNTARGET, HEAL, SHLD, BUFF, EMP (EMPOWER), DR, CLNS (CLEANSE), CD_RATE, COST_MOD.

---------------------------------------------------------------------------------------------------------------------

### 22.4 FX templates

#### 22.4.1 `TechFx` payload (new, server to client)

`TechFx(int spec, byte phase, double x, y, z, tx, ty, tz, float radius, float length, byte tier, byte flags, short count, int seed, int caster, short duration)`

| Field | Content |
|---|---|
| `tier` | caster grade tier 0–5 \| mastery level << 3 |
| `flags` | 1 Kehancuran · 2 echo · 4 low load (client draws ×0.5, no displays) · 8 boss hit · 16 disciple caster · 32 ally effect · 64 showcase |
| `caster` | entity id; auras and forms attach to it |
| `seed` | every client draws the same random shape |

**Sending.** Sent to players within 96 blocks (160 for PAMUNGKAS and grade ≥ Langit). One event per phase occurrence, never per particle. More than 8 hits in one evaluation become one `MASS_HIT` event. At most 20 events per cast per second. `FxType.TECHNIQUE` stays for legacy sub-effects.

**Phases:**

| Phase | When |
|---|---|
| CAST | step 0 |
| CHARGE | the charge starts |
| TRAVEL | a beam, front, projectile, wave or dash starts |
| IMPACT | a delayed area lands |
| HIT | on one target |
| PULSE | a domain, field, aura, rain or channel tick |
| END | the timeline ended |
| EXTRA0–7 | spec-specific moments |

#### 22.4.2 Particle templates (`client/fx/spec/templates/*`)

- **Common parameters:** `color`, `color2`, `size`, `count` (at Normal, scaled by quality and budget), `ticks`, particle override, `minTier`.
- **Particles verified in 26.3:** END_ROD, SOUL_FIRE_FLAME, FLAME, ELECTRIC_SPARK, SNOWFLAKE, CHERRY_LEAVES, ENCHANT, PORTAL, REVERSE_PORTAL, SONIC_BOOM, SCULK_SOUL, GLOW, DRAGON_BREATH, SQUID_INK, WHITE_ASH, ASH, CRIT, ENCHANTED_HIT, FIREWORK, FLASH, EXPLOSION, EXPLOSION_EMITTER (particles only), GUST, TRAIL, DUST, DUST_COLOR_TRANSITION, DUST_PILLAR, BLOCK, BLOCK_CRUMBLE, TRIAL_OMEN, SHRIEK, TOTEM_OF_UNDYING (limited), SWEEP_ATTACK, SOUL, LARGE_SMOKE, FALLING_DUST.
- **Never used:** ELDER_GUARDIAN, VIBRATION, CAMPFIRE_SIGNAL_SMOKE (it lives about 250 ticks).

| Template | Shape |
|---|---|
| `ARC` | crescent band along a strike arc |
| `CRESCENT_FLY` | a moving crescent (FRONT) |
| `BEAM_CORE` | dense core + spiral sheath |
| `CONE_SPRAY` | particles streaming through a cone |
| `SHELL` | sphere-shell burst |
| `SHOCK_RING` | expanding ground ring with dust; reads the ground block client-side |
| `DOME` | translucent dome motes |
| `COLUMN` | sky pillar with ground splash |
| `BOLT` / `BOLT_STORM` | jagged polylines / many bolts from a cloud disc |
| `CLOUD_DISC` | dark swirling disc overhead |
| `VORTEX` | spiral in or out |
| `RUNE_CIRCLE` | rotating glyph circle |
| `PETALS` | lotus or blossom bloom |
| `PALM` / `FIST` | palm and fist point clouds (≤ 30 blocks) |
| `SWORD_SHAPE` | falling, flying or fanned swords |
| `WINGS` | wing silhouettes behind the caster |
| `BEAST` | point-cloud `DRAGON`, `BIRD`, `KUN`, `TIGER`, `WOLF`, `TORTOISE`, `APE`, `SCORPION` |
| `TRAIL`, `IMPACT`, `CRACKS`, `MIST`, `SHARDS`, `CHAINS`, `MOTES_TO`, `HEAL_RISE`, `SHIELD_SHELL`, `AFTERIMAGE` | as named |
| `SKY_TEAR` | a glowing tear 40–60 blocks up, light falling |
| `METEOR` | a falling flaming sphere |
| `STARFIELD` | stars and constellation lines |

#### 22.4.3 Phantom displays (client-only Display entities)

**Why client-only.** Nothing is saved, nothing needs `ALLOW_LOAD` handling, there is no network traffic per frame, the count follows each viewer's quality, and every client builds the same phantoms from the same event and seed. **The server never spawns a Display entity for a technique.**

**How (`PhantomDisplays`):**
1. Create with `EntityType.ITEM_DISPLAY / BLOCK_DISPLAY / TEXT_DISPLAY.create(clientLevel, EntitySpawnReason.LOAD)`.
2. Use **negative ids** counting down from −2,000,000. Add with `ClientLevel.addEntity`, remove with `removeEntity(id, DISCARDED)`.
3. Setters are public through Fabric's transitive access wideners: `setItemStack`, `setBlockState`, `setText`, `setTransformation`, `setTransformationInterpolationDuration`, `setTransformationInterpolationDelay(0)`, `setBrightnessOverride(new Brightness(15, 15))`, `setBillboardConstraints`, `setViewRange`, `setWidth/setHeight` (set to the visual size so it is not culled).
4. **Never glow outlines.** A visible glowing entity switches on the full-screen outline shader.
5. Removed on lifetime end, disconnect, level change and dimension change. Nothing is created while `level == null`. At most 4 are created per tick. If a client-created display misbehaves, the template falls back to particles.

**Animation.**
- Keyframes `(tick, translation, scale, rotation)`; `spin`, `grow`, `fall(h, t)`, `orbit(r, deg/s)`.
- Fade-out = scale to 0 over the last 6 ticks.
- A phantom can be anchored to the caster (forms, auras, orbiting swords).

| Template | Built from | Example |
|---|---|---|
| `GIANT_ITEM(item, scale ≤ 24)` | ItemDisplay | 20-block swords, maces, a gourd, a staff, a flag, a vase |
| `GIANT_BLOCK(state, xyz ≤ 24)` | BlockDisplay | mountains, ice spikes, a furnace, a seal cube, suns |
| `LASER(glass, width, length ≤ 128)` | stretched BlockDisplay | solid translucent beams and sky seams |
| `PETAL_RING(state, n ≤ 16, r)` | glass slabs opening around a centre | lotuses, accretion disks |
| `GLYPH(text, scale ≤ 40)` | TextDisplay, billboard | 佛 封 定 帝 雷 剑 生死 因果 时 (vanilla unifont fallback) |
| `SWORD_RING(item, n ≤ 24, r)` | orbiting ItemDisplays | sword formations |

#### 22.4.4 Custom renderers (no entities)

| Renderer | Use | Budget |
|---|---|---|
| `AVATAR` | a translucent scaled copy of the caster's model drawn at render time (giant forms, Erlang's form, the ape stand-in, Dharma colossus) | outline particles at Minimal |
| `CLONE` | tinted caster or mob models drawn at render time. **Never** a `RemotePlayer` added to `ClientLevel` (it would leak into `players()`) | Minimal 0 (AFTERIMAGE), Normal 4, Maximal 12 |
| `SWARM` | boids of motes (beetles, butterflies, crows) | 40 / 120 / 240 |
| `SKY_SILHOUETTE` | sky-scale point-cloud beasts (ice phoenix, Kun wings, thunder deities) | – |
| `VIEWER_MARK` | a highlight seen only by the caster (Shadow Devil), not GLOWING | – |

#### 22.4.5 Post effects (client)

| Effect | Implementation | Limits |
|---|---|---|
| `SHAKE(intensity 0–1, t)` | client mixin `GameRendererShakeMixin` injecting at **HEAD or RETURN** of the private `GameRenderer.bobHurt(CameraRenderState, PoseStack)` (it returns early, so not TAIL); noise roll/pitch | ≤ 2.5° × intensity × setting (Penuh 1 / **Ringan 0.4, default** / Mati 0) × vanilla `damageTiltStrength`; full within max(8, effect radius), 0 at 2r; one shake at a time |
| `FLASH(colour, α ≤ 0.35, ≤ 8t)` | HUD fill before chat | × `screenEffectScale`; ≥ 10 ticks apart; setting; hidden in F1 |
| `VIGNETTE(colour, t)` | HUD edge gradient | Langit+ PAMUNGKAS, viewers ≤ 48 |
| `SKY` | CLOUD_DISC + SKY_TEAR + VIGNETTE | Abadi/Dewa ultimates; never a weather change |

Default shake by grade: Kuning 0, Misterius 0.1, Bumi 0.25, Langit 0.45, Abadi 0.7, Dewa 1.0. At most 2 large translucent layers (dome, laser, sky tear) at a time.

#### 22.4.6 Sounds

- Server side, `level.playSound(null, …)`, ≤ 4 cues per phase; volume above 1 widens the range.
- **Verified:** WARDEN_SONIC_BOOM, WARDEN_SONIC_CHARGE, ENDER_DRAGON_GROWL, ENDER_DRAGON_FLAP, TRIDENT_THUNDER, BEACON_POWER_SELECT, GENERIC_EXPLODE (sound only), MACE_SMASH_GROUND_HEAVY, LIGHTNING_BOLT_THUNDER, BELL_RESONATE, TOTEM_USE, BREEZE_WIND_CHARGE_BURST, RESPAWN_ANCHOR_CHARGE, AMETHYST_BLOCK_RESONATE, TRIAL_SPAWNER_OMINOUS_ACTIVATE. WITHER_SPAWN and END_PORTAL_SPAWN only through `playSound`, never `levelEvent`.
- Some fields are `Holder<SoundEvent>`; use both overloads as `DaoTechniques` does.
- Sound names in the catalogue are vanilla `SoundEvents` and must be verified by the content module, falling back to a verified cue.

#### 22.4.7 Visual escalation with cultivation

- Tier = caster grade tier: QR/FE 0, GC/NS 1, DT/DU 2, IA 3, TI 4, ★9+ 5.
- Sizes × (1 + 0.15 × tier); counts × (1 + 0.10 × tier); both inside the budget.
- `minTier` layers: glyph circles from 2, phantom displays from 3, sky effects from 4, the Dewa shimmer from 5.
- Mastery: level 3 adds a gold rim, level 4 an afterimage.
- A Dewa Abadi cast of the same art therefore looks far bigger than at Inti Emas.

---------------------------------------------------------------------------------------------------------------------

### 22.5 Power scaling

#### 22.5.1 Power P (PvE)

```
P_player   = 6.0 × R(rank) × C × Q × S(stars) × techniqueDamageMultiplier
P_disciple = 4.5 × R(rank) × C   (× the existing DiscipleHooks.damageFactor: stars, hexes, folds, flames, titles)
R: ≤ Penyatuan Dao  A[realm] × (1 + 0.12 × stage)   A = {Fana 0.6, QR 1.0, FE 1.7, GC 2.9, NS 5.0, DT 8.5, DU 14.5}
   Kenaikan Abadi   25 × 1.3^layer × (1 + 0.06 × stage)   (layer 0–8 = Tahap 1–9)
   Dewa Abadi       400 × (1 + 0.25 × stage)
C = 1 + comprehension/100 (the spec's Dao; main comprehension for universal)
Q = sqrt(average breakthrough quality factor, DESIGN 2)   (0.95–1.14)
S(n) = (1 + 0.04 n) × 1.15^floor(n/9)   (player n = extra.kultivasi.stars, 0 until phase 7)
legacy (arena, trial mobs):  D_legacy = (4 + 2.5 realm + 0.5 stage) × C ;  U_legacy = (3 + 2 realm + 0.5 stage) × C
```

- R rises at every one of the 65 ranks. A breakthrough multiplies it by 1.25–1.27; the step from Kenaikan Abadi Tahap 9 to Dewa Abadi multiplies it by 1.66.
- `Techniques.power` returns `P_player`. `DiscipleTechniques.power` returns `P_disciple`, which puts disciple ultimates, Jurus Pemusnah, Kunpeng and rival skills on the curve too, with the arena and trial-mob rules.
  - **6c implementation note (deliberate deviation).** Disciple *spec* casts read `P_disciple` (`TechniquePower.power(DiscipleData)` in `DiscipleCaster`). `DiscipleTechniques.power` itself still returns `U_legacy`, because `PowerCore.hurt` and the legacy `DiscipleTechniques.hurt` path pass `DamageContext.legacy` amounts unchanged (no boss cap, trial-mob rule or arena ratio). It switches to `P_disciple` only together with those two choke points building HIT contexts (coef = amount / P_disciple, one ledger per cast), so disciple powers never deal curve damage without the caps.

#### 22.5.2 Example numbers per rank

Columns use the Puncak stage, C = 1.5, Q = 1, mastery 0. "Ultimate" is the best PAMUNGKAS band of the rank's grade (22.5.6). Warden casts = the casts needed against a 500 HP Warden with the best grade at that rank (22.14.2).

| Rank | R Puncak | P | Basic art 1.5P | Ultimate | × today | Boss cap base | Warden casts | K (22.11) | Blocks per cast |
|---|---|---|---|---|---|---|---|---|---|
| Pemurnian Qi | 1.36 | 12 | 18 | – | 1.0 | 0.06 | – | – | 0 |
| Pembangunan Fondasi | 2.31 | 21 | 31 | – | 1.3 | 0.06 | – | – | 0 |
| Inti Emas | 3.94 | 35 | 53 | 142 (4P) | 1.8 | 0.12 | 12 | 0.25 | 128 |
| Jiwa Baru Lahir | 6.80 | 61 | 92 | 245 | 2.6 | 0.15 | 10 | 0.35 | 256 |
| Transformasi Dewa | 11.56 | 104 | 156 | 728 (7P) | 3.9 | 0.18 | 7 | 0.50 | 512 |
| Penyatuan Dao | 19.7 | 177 | 266 | 1,240 | 5.8 | 0.22 | 6 | 0.60 | 1,024 |
| Kenaikan Abadi T1 | 29.5 | 266 | 399 | 3,190 (12P) | 7.7 | 0.26 | 4 | 0.65 | 1,536 |
| Kenaikan Abadi T5 | 84.3 | 758 | 1,140 | 9,100 | 22 | 0.38 | 3 | 0.81 | 2,304 |
| Kenaikan Abadi T9 | 240.6 | 2,165 | 3,250 | 26,000 | 63 | 0.50 | 2 | 0.97 | 3,072 |
| **Dewa Abadi** | 700 | **6,300** | 9,450 | 113,400 (18P); Dewa 28P = 176,400 | **165** | 1.0 | 1 | 1.5 | 4,096 |

- Mastery Puncak doubles damage.
- Stars (disciples now; the player from phase 7) at TI Puncak, C = 2: ★9 ×1.56 → P 13,100; ★36 ×4.27 → 35,800; ★72 ×11.9 → 99,700; ★100 ×23 → 195,000.
- Ordinary 20–40 HP monsters die to any art from Inti Emas on.

#### 22.5.3 Area, duration and count growth

```
gapR = realm ordinal − spec.minRealm ordinal (≥ 0); t = tier index: GC 0, NS 1, DT 2, DU 3, IA L1–3 4, L4–6 5, L7–9 6, TI 7, + floor(stars/9)
A = 1 + 0.10 gapR + (IA ? 0.02 layer : 0) + (TI ? 0.10 + 0.05 stage : 0) + 0.01 stars
D = 1 + 0.06 gapR + 0.005 stars        N = 1 + 0.15 gapR + 0.01 stars
value = min(spec cap, hard cap, base × factor × mastery factor)  (counts rounded; A/D/N never stack on lin())
```

#### 22.5.4 Qi, cooldown and realm gap

- Caster grade tier: QR/FE 0, GC/NS 1, DT/DU 2, IA 3, TI 4, ★9+ 5. `tierGap = caster tier − spec grade tier` (≥ 0).
- **Qi** = `ceil(qi × M_qi × max(0.35, 1 − 0.13 × tierGap))`. `qiPct` specs ignore the gap.
- **Cooldown** = `round(cd × M_cd × max(0.40, 1 − 0.12 × tierGap))`, at least 10 ticks.
- **Qi regeneration (spine change in `Qi`):** the base 0.4% of max per second is multiplied by (1 + 0.1 × grade tier), so Dewa Abadi regenerates ×1.4. This keeps "higher = more OP" true for sustained casting.

#### 22.5.5 Damage resolution (`TechniquePower.finalDamage`, one function)

1. `raw = coef × P × M_dmg × F`. F = falloff × conditions × stacks × marks × versus × empower.
   - **F ≤ ×3 against monsters; ≤ ×1.5 under the arena rule.**
2. Disciple casts: × `DiscipleHooks.damageFactor`.
3. **Arena rule.** If the target is a sparring or `DuelRules` partner, multiply by the attacker's legacy/new power ratio, then apply the existing duel clamps. Techniques hit players for ≤ 15% of max health per hit and per cast. No EXEC and no movement payloads on players; control ×0.3.
4. **Trial-mob rule.** If the target is tagged `kultivator.dao_protector` or `kultivator.trial_guard`, use legacy power × 1.5 instead of P. This keeps Dao Protector waves, star calamities, promotion trials and inheritance guards real fights.
5. **Own Heart Demon:** ≤ 6% of its max health per hit (10% PAMUNGKAS), ≤ 12% per cast. No mastery from it.
6. **Boss cap** (22.14.2), cumulative per cast per boss. ECHO, LINK, REFLECT, COUNTER, DOT and replays count against the cap of the cast that started them.
7. **Ender Dragon.** PRECISE hits go to the head part (`EnderDragon.hurt(level, head, source, amount)`). Area hits go to the body, which vanilla quarters.
8. The choke-point hurt.

#### 22.5.6 Coefficient bands (× P, mastery 0)

| Grade | Single target | Area per target | PAMUNGKAS | DOT/s | Heal | Shield | Control |
|---|---|---|---|---|---|---|---|
| Kuning | 1.0–1.5 | 0.6–0.9 | 2.5 | 0.10–0.15 | 15–25% | 20% | 1–2 s |
| Misterius | 1.5–2.5 | 0.9–1.5 | 4 | 0.15–0.25 | 20–35% | 30% | 2–4 s |
| Bumi | 2.5–4 | 1.5–2.4 | 7 | 0.25–0.4 | 30–40% | 50% | 3–5 s |
| Langit | 4–6 | 2.4–3.6 | 12 | 0.4–0.6 | 35–50% | 70% | 4–6 s |
| Abadi | 6–9 | 3.6–5.4 | 18 | 0.6–0.9 | 40–60% | 100% | 5–7 s |
| Dewa | 9–14 | 5.4–8.4 | 28 | 0.9–1.4 | 50–100% | 150% | 6–8 s |

**Qi and cooldown bands:**

| Grade | Basic qi | Ultimate qi | Basic cd | Area cd | Ultimate cd |
|---|---|---|---|---|---|
| Kuning | 8–20 | 30 | 1–3 s | 6–10 s | 20 s |
| Misterius | 20–45 | 90 | 2–4 s | 8–15 s | 35–45 s |
| Bumi | 50–90 | 160 | 3–5 s | 10–20 s | 45–60 s |
| Langit | 100–180 | 320 | 3–6 s | 12–25 s | 60–90 s |
| Abadi | 180–300 | 520 or qiPct | 4–8 s | 15–30 s | 90–150 s (up to 420 s for formations) |
| Dewa | 250–400 | qiPct 70–90 | 5–10 s | 20–40 s | 300–900 s |

---------------------------------------------------------------------------------------------------------------------

### 22.6 Mastery: Penguasaan Jurus

**Sumber / Source.**
- *Ancient Strengthening Technique*: arts advance through small success, large success and great perfection (ch. 378, 818).
- *The Sovereign's Ascension*: lesser mastery unleashes only part of an art's might (ch. 4).
- Level 4 "Puncak" and all numbers are game design.

| Lv | id_id / en_us | Damage | Area | Duration | Qi | Cooldown | Gate |
|---|---|---|---|---|---|---|---|
| 0 | Pemula / Novice | ×1.00 | ×1.00 | ×1.00 | ×1.00 | ×1.00 | – |
| 1 | Sukses Kecil / Small Success | ×1.15 | ×1.08 | ×1.10 | ×0.95 | ×0.95 | – |
| 2 | Sukses Besar / Large Success | ×1.35 | ×1.16 | ×1.20 | ×0.90 | ×0.90 | – |
| 3 | Kesempurnaan Agung / Great Perfection | ×1.60 | ×1.25 | ×1.30 | ×0.85 | ×0.80 | Dao comprehension ≥ 60; universal: Jiwa Baru Lahir |
| 4 | Puncak Jurus / Peak | ×2.00 | ×1.40 | ×1.50 | ×0.75 | ×0.70 | comprehension ≥ 90; universal: Penyatuan Dao |

**Bonuses.** Level 3 unlocks `perfection`: EXTRA_COUNT, EXTRA_WAVE, AREA, DURATION, PAYLOAD, CHAIN, COOLDOWN or EXTRA_CAST. Level 4 unlocks `peak`: ECHO 0.4 where allowed, otherwise DURATION 0.25.

**Points** (`extra.teknik.m {id: int}`):
- Per cast, scaled by `clamp(base cooldown / 10 s, 0.25, 3)`: +1 for a cast, +1 if it hit a monster, +2 if it killed, +3 if it hit a boss.
- Hit and kill points: ≤ 200 per technique per in-game day. Casts that hit nothing: ≤ 20 per day.
- Enlightenment Moment +10 (selected art). The book of a known art +15 (plus +5 comprehension). A duplicate jade slip +25.
- `/uji` casts and training dummies give nothing.

**Thresholds** (cumulative points for levels 1/2/3/4):

| Grade | Thresholds |
|---|---|
| Kuning | 25 / 80 / 200 / 450 |
| Misterius | 40 / 120 / 300 / 700 |
| Bumi | 60 / 180 / 450 / 1,000 |
| Langit | 90 / 270 / 650 / 1,500 |
| Abadi | 120 / 360 / 900 / 2,000 |
| Dewa | 160 / 480 / 1,200 / 2,700 |

**Feedback.** A jade seal toast ("Telapak Buddha mencapai Sukses Besar!"), a chime, a golden ring FX and a chat line.

**Disciples.** A taught art starts at the master's level − 1; the heir gets the master's level. Disciples earn points at half rate (`DiscipleData.extra.teknik.m`).

---------------------------------------------------------------------------------------------------------------------

### 22.7 Grades and types

**Sumber / Source.** *Battle Through the Heavens* ch. 2: Dou techniques are ranked Huang, Xuan, Di and Tian, each low/middle/high. "Abadi" and "Dewa" above Tian are game design labels.

| Grade | id_id | en_us | Tier | Typical realms | Ribbon |
|---|---|---|---|---|---|
| HUANG | Kuning | Yellow | 0 | QR–FE | ochre |
| XUAN | Misterius | Mystic | 1 | GC–NS | violet |
| DI | Bumi | Earth | 2 | DT–DU | bronze |
| TIAN | Langit | Heaven | 3 | IA (Rendah T1–3, Menengah T4–6, Tinggi T7–9) | azure |
| ABADI | Abadi | Immortal | 4 | Dewa Abadi | white-gold |
| DEWA | Dewa | Divine | 5 | Gerbang Dewa (22.8.8) | rainbow shimmer |

**Types** (codex filter, disciple AI role, default teachability):

| Type | Meaning | Teachable by default |
|---|---|---|
| SERANG | single target | yes |
| AREA | area attack (**a placed FIELD counts as AREA**) | yes |
| KENDALI | control | yes |
| GERAK | movement | yes (dash and blink) |
| LINDUNG | defence | yes (self) |
| PULIH | heal or support | yes |
| PANGGIL | summon | at half count |
| WILAYAH | follow-domain | no |
| UBAH | transformation | no |
| PASIF | passive (reserved) | no |
| PAMUNGKAS | ultimate | yes |
| INDRA | sense or utility | no |

The `TEACHABLE` / `NO_TEACH` flags override the default. The catalogue column "Mrd" is the final word.

---------------------------------------------------------------------------------------------------------------------

### 22.8 Acquisition

A spec has one or more routes. The codex shows them as a checklist. Every spec except Dewa, disciple-only and `fusion_pair` can also be learned from a **jade slip**.

1. **Automatic routes.**
   - `REALM`: universal specs, at the realm.
   - `DAO`: the Dao's first arts, when the Dao is chosen and the realm reached.
   - `COMP n`: Dao comprehension ≥ n and the realm.

   `Techniques.refreshUnlocks` (rank, Dao or comprehension change) learns them, with the existing chat line plus a "Pencerahan Jurus" toast and FX.
2. **Enchanted books** (DESIGN 7, extended). Sneak + use. Each enchantment maps to a **pool**; the book teaches the first learnable spec of the pool in grade order, otherwise it gives insight (+5 comprehension, +15 mastery to a known art). `TechniqueBooks.techniqueFor` becomes `poolFor`, and `BookTooltips` lists the pool with lock reasons.

   | Family | Enchantments |
   |---|---|
   | Sword | sharpness, sweeping_edge, breach |
   | Fire | fire_aspect, flame, fire_protection |
   | Ice | frost_walker, depth_strider, respiration |
   | Lightning | channeling, riptide, quick_charge |
   | Body | protection, thorns, blast_protection, unbreaking, density |
   | Life | mending, luck_of_the_sea, lure |
   | Shadow/Wind | swift_sneak, feather_falling, wind_burst, soul_speed, punch |
   | Space | infinity, loyalty, silk_touch, piercing |
   | Universal | smite, power, efficiency, looting, fortune, bane_of_arthropods, impaling, aqua_affinity, projectile_protection, knockback, multishot, lunge |
   | Forbidden | binding_curse, vanishing_curse |

   A catalogue cell such as `BOOK unbreaking` means the spec sits in that pool. Universal specs whose pool enchantment is not in the family above (unbreaking, piercing, soul_speed, lure, breach) are also added to the Universal pool through that enchantment.
3. **Slip Giok / Jade Slip** (new item `jade_slip`, component `kultivator:technique`).
   - Right-click learns it if every gate allows; otherwise "Isi slip ini terlalu dalam untukmu" plus the missing requirement.
   - Used on your own disciple, it teaches without master qi. A known art gives +25 mastery.
   - **Sumber / Source:** *A Record of a Mortal's Journey*: jade slips store arts, read by spiritual sense, traded and gifted (ch. 1269); Han Li declines an art that contradicts his cultivation (ch. 1782).
4. **Paviliun Yue Lu** (20.3.7). The "Gulungan Teknik" row becomes "Slip Giok Teknik".
   - Sold: every spec flagged `PAVILION` (default: non-Dewa, non-special, non-disciple-only specs with a BOOK, PAV or COMP route).
   - Prices: Kuning 200 PK (hall 1), Misterius 400 (3), Bumi 900 (5), Langit 2,000 (7), Abadi 4,000 (9). × the level factor; at most 2 slips per in-game day.
5. **Ujian Warisan (inheritance trial)**, item `giok_warisan` with component `kultivator:theme` (a Dao or `universal`).
   - **Loot:**

     | Table | Chance |
     |---|---|
     | ancient_city | 6% |
     | end_city_treasure | 8% |
     | bastion_treasure | 6% |
     | stronghold_library | 5% |
     | trial_chambers/reward_ominous | 5% |

   - **First boss kills:** Warden (Shadow), Ender Dragon (Space), Elder Guardian (Ice) at 100%; later kills 15%.
   - **Trial:**
     1. Needs DT+, not deviating, not breaking through.
     2. A 10 s omen (CLOUD_DISC, no weather).
     3. 90 s, 3 waves through `DaoProtectors.spawnWaveFor`. Guards carry `kultivator.trial_guard` (trial-mob rule, **no boss tag**). The final guardian has 4× health and the boss tag.
     4. **Win:** choose 1 of 3 learnable specs of the theme, rolled by realm (DT–DU Bumi; IA Langit 70% / Abadi 30% from T7; TI Abadi 60% / Langit 40%). Advancement `inheritance`.
     5. **Loss** (below 20% health, leaving 24 blocks, or timeout): the jade is kept with a 1-day cooldown. Never lethal.
   - **Sumber / Source:** *Renegade Immortal* (Tu Si's legacy), *Perfect World* (bone-text precious arts), *A Record of a Mortal's Journey* (jade slips). The trial is game design.
6. **Enlightenment.** A 25% chance during an Enlightenment Moment:
   - to comprehend the next COMP spec early, when within 10 points of its threshold;
   - to reveal `xing_secret` at IA T5+ (route ENLIGHT).
7. **Prerequisite chains** (`MASTERY`): `prereq (id, level)`, shown in the codex.
8. **Gerbang Dewa (Dewa gate).**

   | Who | Gate |
   |---|---|
   | Player, gate I | Dewa Abadi Puncak (ascended, DESIGN 17), comprehension ≥ 90 in that Dao (main comprehension for universal), the named prereq at Kesempurnaan Agung |
   | Player, gate II | as gate I, plus two named prereqs at Puncak Jurus |
   | Player, phase 7 | stars ≥ 9 (I) or 18 (II), as an alternative route |
   | Disciples | stars ≥ `dewaStars`; a Dewa art is taught by the master, or **self-comprehended** at each new 9-star title (the master picks one eligible art of the disciple's Dao) |

   **Sumber / Source:** *BTTH* (Xiao Yan outgrows Yao Lao) and *Renegade Immortal* (Ancient God stars), as in 20.3.19/20.

   Every player spec is therefore reachable in 6c, and the counter "Teknik x/168" can be completed.
9. **Feats** (`Route.feat`, stored in `extra.teknik.feat`, read-only conditions). The 8 kinds:

   | Kind | Counts |
   |---|---|
   | `KILLS_WITH(dao/spec/any, cond, mobTag, n)` | kills by technique with a condition |
   | `CASTS_WITH(dao, cond, n)` | casts with a condition |
   | `FIRST_KILL(boss or set, dao, minRank)` | first boss kills |
   | `MEDITATE_AT(cond/biome tag/site, minutes)` | meditation time in a place |
   | `ALCHEMY_COUNT(n)` | successful refinements |
   | `TRIB(quality ≥ q, or lifetime strikes ≥ n)` | tribulation results; `tribStrikesSurvived` is a new spine stat |
   | `HEART_DEMON_WIN(beforeRealm)` | heart-demon victories |
   | `DEVIATION_RECOVERED(minRank)` | recovered qi deviations |

   Conditions: NIGHT, FULL_MOON, THUNDERING, RAINING, biome tag, dimension, HOLDING_SWORD, altitude.
10. **Other routes.**
    - `ITEM heavenly_flame_seed:<kind>`.
    - `SCR n`: n pages of the new **lost scripture set 4 "Kitab Penyegel Iblis"** (5 pages in the existing loot tables; module A's `LostArts`).
    - `INH(theme)`.
    - `ENLIGHT`.
11. **Info.** The status page shows "Teknik 37/168 · Kekuatan 8,4 rb · Penguasaan tertinggi: Kesempurnaan Agung". `/kultivasi teknik info <id>` prints the detail in chat. New help topics: `teknik`, `penguasaan`, `kitab_teknik`, `kehancuran_diri`.

---------------------------------------------------------------------------------------------------------------------

### 22.9 Technique catalogue (146 new)

**How to read the tables.**

| Column | Meaning |
|---|---|
| Net | netId |
| ★ (after the type) | special handler |
| Ranah · Tingkat · Jenis | realm, then grade-sub (R/M/T = Rendah/Menengah/Tinggi), then type |
| Resep | recipe in 22.3.6 notation |
| Visual | templates (22.4), displays (always client-only), SHK = shake, ♪ = sound |
| KH | Kehancuran shapes at K = 1 (22.11): `BOWL r`, `RING ro/ri/d`, `TUNNEL r L`, `FISSURE L×w×d`, `SCATTER r`, `ARC r° w d`, `FAN L° d`, `STRIP r` or `fan L` (foliage only), `TRENCH L×w×d`, `SPHERE r`, `DISC r d [nat]`, `PIT hw/d`, `COLUMN r d`, `NINE r s`, `WITHER r`. "nat" = natural blocks only. "–" = no carving |
| Mrd | Y = disciples can learn it; N = no; Y★n = from disciple star n; Y(½) = half count; "only" = disciple-only; "pair" = fusion skill |
| Sumber | work · named technique (chapter where verified). "Terinspirasi" = no canon claim on the name |

Every spec can also be learned from a jade slip (22.8), except Dewa, disciple-only and `fusion_pair`.

#### 22.9.1 Pedang / Sword (200–213)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 200 | `azure_essence_sword` | **Seni Pedang Esensi Biru** / Azure Essence Sword Art | GC · Misterius-R · SERANG | DAO, COMP 10 | TOG 1 qi/s (start 15); GRANT_HOOK sword melee (icd 10t): FRONT(L 6→12, w1, v2) > DMG .6; t≥4: 3 fronts fanned 20° · cd 6s after end | azure blade glow (DUST #7FD8FF, END_ROD), thin CRESCENT_FLY; ♪ amethyst chime 1.8 (throttled) | – | Y | A Record of a Mortal's Journey · 青元剑诀 Azure Essence Sword Art, Han Li's base sword art (13 layers in the human world; the Great Geng formation needs a high layer) |
| 201 | `sword_shadow_split` | **Bayangan Pedang Membelah Cahaya** / Sword Shadow Splitting Light | GC · Misterius-R · AREA | BOOK sweeping_edge | VOLLEY SEEK n 3→10, FAN 50°, range 20→48, pierce, ≤3 per target > DMG .7 · qi 30 · cd 6s | held-sword GIANT_ITEMs ≤6 flying with TRAIL; ♪ trident throw 1.6 + sweep | – | Y | RMJ · 剑影分光术, a divine ability of the Azure Essence Sword Art: the sword light splits off duplicate sword shadows |
| 202 | `giant_sword_light` | **Cahaya Pedang Raksasa** / Giant Sword Light | NS · Misterius-M · SERANG | BOOK sharpness, COMP 30 | CHG 15t; FRONT(L 10→36, w3, h4, v3) > DMG 3 + BIND root 20t + LCH .4 · qi 70 · cd 18s | held sword as GIANT_ITEM 6→20 b rises point-up, then cleaves; CRACKS; SHK .4; ♪ anvil .5 | FISSURE 16×2×4 | Y | Terinspirasi dari the giant sword light of the Azure Essence Sword Art (RMJ); not a named canon art |
| 203 | `heavenly_wolf_slash` | **Tebasan Serigala Langit** / Heavenly Wolf Slash | NS · Misterius-M · GERAK | BOOK sharpness, COMP 35; PAV | DASH 8→20 (i-frames 6t) + PATH(w2) > DMG 2.2; onEnd CONE(10→18, 30°) > DMG 1.2 + EFF weakness I 80t ("Wild Fang") · qi 60 · cd 14s | silver BEAST WOLF head + claw ARCs; SHK .2; ♪ wolf howl .7 | TRENCH path×2×1 | Y | Against the Gods · Heavenly Wolf Hell God's Tome: Heavenly Wolf Slash (1st style, ch. 148) and Wild Fang (2nd style, ch. 1297) |
| 204 | `bloodmoon_immortal_slaying_sword` | **Pedang Pembunuh Abadi Bulan Darah** / Bloodmoon Immortal Slaying Sword | DT · Bumi-R · AREA | FEAT kills_with(SWORD, full-moon night, 30), COMP 50; PAV | @20 RAIN(aim ≤32, r 8→16, impacts 12→36, 40t, ir 2) > DMG .9; @70 ZONE(r4) > DMG 3 + MARK DAO_VULN(SWORD +20%, 200t) · qi 140 · cd 40s | red CLOUD_DISC "blood moon" 20 b up, SWORD_SHAPE rain + falling GIANT_ITEM swords ≤8; SHK .5; ♪ bell .5 | SCATTER r2 + BOWL 4 | Y | Against the Gods ch. 1266 · Bloodmoon Immortal Slaying Sword: Xisu's Immortal Slaying Sword Formation, which badly injured two great moon gods |
| 205 | `overlord_fury` | **Amarah Sang Penguasa** / Overlord's Fury | DT · Bumi-M · AREA | BOOK sharpness, COMP 45; PAV | BLINK leap AIM ≤16→24 (safe); WAVE(r 6→20, v1.5) > DMG 3.5 centre → 1.5 edge + LCH .6 + ATTR armor −50% 160t · qi 110 · cd 30s | vermilion heavy-sword GIANT_ITEM slam, SHOCK_RING ×2, CRACKS; SHK .5; ♪ mace heavy + explode (sound) .6 | BOWL 6 + RING 12/6/2 | Y | Against the Gods ch. 333 · 霸王怒 Overlord's Fury, from a fragment of the broken Overlord sword |
| 206 | `immortal_tribulation_sword` | **Seni Pedang Kesengsaraan Abadi** / Immortal Tribulation Sword Art | DU · Bumi-T · SERANG | BOOK sharpness, COMP 60; PAV | BEAM(48→64, hw 1.5, pierce) > DMG 4 + DOT qi .25/s 8s · qi 180 · cd 45s | white-gold LASER with dark CLOUD_DISC wisps; SHK .4; ♪ trident thunder | TUNNEL r1 L32 | Y | Perfect World ch. 1786/1973 · Immortal Tribulation Sword Art, one of the three great sword extremes (with the Grass Symbol Sword Art and the Chaos Calming Art) |
| 207 | `grass_symbol_sword` | **Seni Pedang Aksara Rumput** / Grass Symbol Sword Art | DU · Bumi-T · SERANG | FEAT meditate_at(bamboo jungle, 10 min), COMP 70 | ALL(r32, max 5→8, STRONGEST, los, PRECISE) > DMG 2.5 + EFF slowness III 60t · qi 150 · cd 35s | GLYPH 草 (scale 8) writes itself; one thin BEAM_CORE stroke-flash per target; ♪ flute 2 + sweep 1.8 | – | Y | Perfect World ch. 1786 · 草字剑诀 Grass Symbol Sword Art: a single blade of grass that can cut apart sun, moon and stars |
| 208 | `heavenbreaker_stance` | **Sikap Pemecah Langit** / Heavenbreaker Stance | IA T1 · Langit-R · PAMUNGKAS | FEAT first_kill(WARDEN, SWORD), COMP 70 | CHG 40t (early release ≥50%); FRONT(L 40→64, w4, h12, v4) > DMG 8 × charge share + BIND root 40t + SLAM .6 · qi 220 · cd 40s | blade grows into a 30-b GIANT_ITEM above the caster during the charge; SKY_TEAR line; CRACKS; SHK .8; ♪ anvil .4 + warden boom .5 | FISSURE 32×3×6 | Y | Desolate Era · Ji Ning's [Heavenbreaker] stance: the sword is made heavier so its weight drives the strike |
| 209 | `lu_ya_gourd` | **Labu Pemenggal Lu Ya** / Lu Ya's Beheading Gourd | IA T3 · Langit-R · KENDALI | PAV; FEAT kills_with(SWORD, nether, 100) | TARGET(≤48) > BIND stun 60t (boss root 15t); @60 TARGET > DMG 8 + EXEC .35 (boss .05) · qi 180 · cd 45s | small decorated-pot GIANT_ITEM opens, a white BEAM_CORE thread pins the target, a tiny glowing figure arcs over; ♪ bell 1.4 + crit | – | Y | Investiture of the Gods ch. 48 · Lu Ya's gourd: a thread of light and a thing "with eyebrows and eyes" pin Bai Tianjun, "请宝贝转身", and his head falls |
| 210 | `blood_drop_stance` | **Sikap Tetes Darah** / Blood Drop Stance | IA T5 · Langit-M · SERANG | BOOK sharpness, COMP 80; PAV | BLINK BEHIND_TARGET chain 6→12 within 24 (≥3t apart, i-frames during chain); each STRIKE > DMG 3 ? TARGET_LOW_HP(.5) ×2 + EXEC .15 · qi 240 · cd 30s | red droplet TRAIL + AFTERIMAGE; red VIGNETTE pulse for the caster; ♪ riptide 1.6 + crit | – | Y | Desolate Era · the Blood Drop stance of the Brightmoon sword art, the fastest, most savage and most penetrating of its five stances |
| 211 | `astral_sword_intent` | **Niat Pedang Inti: Sikap Bintang** / Quintessence Sword-Intent: Astral | IA T7 · Langit-T · WILAYAH | FEAT meditate_at(open sky at night, 30 min), COMP 90 | DOMAIN(r 16→32, 300t, pulse 20) > DMG 1.2; GRANT_HOOK melee and technique hits: 2 star swords > DMG .8 (≤6 per second) · qi 320 · cd 90s | STARFIELD dome + SWORD_RING ×8 orbiting; ♪ amethyst resonate + beacon ambient | – | N | Desolate Era · Quintessence Sword-Intent, third stance "Astral" |
| 212 | `zhuxian_sword_formation` | **Formasi Pedang Zhuxian** / Zhuxian Immortal-Slaying Sword Formation | TI · Abadi-M · PAMUNGKAS | FEAT first_kill set {WITHER, WARDEN, ELDER_GUARDIAN, ENDER_DRAGON} with SWORD; INH(SWORD) | FIELD square (aim ≤64, side 48→64, walls, 240t, pulse 20) > DMG 2; corner PILLARs (r6) every 40t > DMG 4; corner laws: Luxian STRIP_BUFFS, Xianxian EFF slowness IV, Juexian onEnd EXEC .15; @240 ALL(field, max 32) > DMG 10 · qiPct 50 (min 600) · cd 300s | four GIANT_ITEM swords (scale 24) at the corners, GLYPHs 誅 戮 陷 絕, red-black MIST; SHK 1.0; ♪ bell ×4 .5 + thunder | COLUMN r3 d12 ×4 + TRENCH edges 2×3 | Y★18 | Investiture of the Gods ch. 77–78 · 誅仙陣, Tongtian Jiaozhu's formation with the four swords 誅仙 戮仙 陷仙 絕仙, broken only by the Three Teachings together |
| 213 | `heaven_punishing_sword` | **Seni Pedang Hukuman Langit** / Heaven Punishing Sword Art | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `zhuxian_sword_formation` L3) | @0 ALL(r40, max 9, STRONGEST) > DMG 3; @20 NOVA(r40) > BIND stun 60t + EFF darkness 60t; @40 NOVA(r40) > DMG 5; @60 BEAM travelling 128 (hw 3, ignoreBlocks) > DMG 12 · qiPct 75 · cd 600s | horizon-to-horizon white-gold SKY_TEAR seam, SWORD_SHAPE rain, FLASH; SHK 1.0; ♪ dragon growl .6 + trident thunder + bell | TRENCH 64×3×5 (along the beam) | Y★36 | Against the Gods ch. 2189 · Heaven Punishing Sword Art, tied to Mo E's Heaven Punishing Ancestral Sword (the four-phase structure is game design) |

#### 22.9.2 Api / Fire (300–314)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 300 | `phoenix_fire_line` | **Garis Api Phoenix** / Phoenix Fire Line | GC · Misterius-R · SERANG | DAO | BEAM(14→35, hw .8) > DMG 1.2 + DOT fire .25/s 6s · qi 25 · cd 5s | thin golden flame stream from the mouth (BEAM_CORE gold + FLAME); ♪ blaze shoot | TUNNEL r1 L12 | Y | Soul Land · 凤凰火线, Ma Hongjun's 1st soul skill: a line of flame breathed from the mouth, very hard to put out |
| 301 | `phoenix_bathing_fire` | **Phoenix Mandi Api** / Phoenix Bathing in Fire | GC · Misterius-M · LINDUNG | DAO, COMP 20 | AURA(r 3→7, 400t, pulse 20) > DMG .3 + IGN 3s; SELF: BUFF fire_resistance 400t + EMP(FIRE +30%) 400t; GRANT_HOOK melee > IGN 4s · qi 40 · cd 40s | flame mantle + faint WINGS (BIRD outline); ♪ firecharge | – | Y | Soul Land · 浴火凤凰, Ma Hongjun's 2nd soul skill: flames cover the body and strengthen the first skill |
| 302 | `flame_splitting_tsunami` | **Tsunami Api Pembelah** / Flame Splitting Tsunami | NS · Misterius-M · AREA | BOOK fire_aspect, COMP 30 | CHG 20t; FRONT(L 20→48, w 6→14, h4, v1.5) > DMG 2.5 + KNK 1.2 + DOT fire .3/s 5s · qi 80 · cd 20s | curling crimson wave carrying a black heavy ruler (GIANT_BLOCK black concrete 1×6×1); SHK .4; ♪ blaze ×2 + lava pop | FAN 16 30° d2 | Y | Battle Through the Heavens · 焰分噬浪尺: dou qi gathered into Xiao Yan's Heavy Xuan Ruler and released at once |
| 303 | `phoenix_arrow` | **Panah Phoenix** / Phoenix Arrow | NS · Misterius-T · SERANG | BOOK flame, COMP 30 | VOLLEY SEEK n 1→5, range 48 > DMG 2; onHit NOVA(r2) > DMG 1 + DOT fire .3/s 4s · qi 60 · cd 10s | phoenix-fire arrows with feather TRAIL; ♪ crossbow .6 | SCATTER r1 | Y | Against the Gods · 凤凰箭 Phoenix Arrow, a realm of the World Ode of the Phoenix (凤凰颂世典) |
| 304 | `ten_thousand_crow_vase` | **Guci Sepuluh Ribu Gagak** / Ten Thousand Crow Vase | DT · Bumi-R · AREA | PAV; FEAT kills_with(FIRE, nether, 50) | VOLLEY SEEK n 12→40 over 60t, range 24→40, ≤5 per target > DMG .8 + DOT fire .3/s 5s · qi 120 · cd 30s | SWARM of smoke crows with flame wings pouring from a GIANT_ITEM pot; ♪ phantom flap + blaze | SCATTER r2 | Y | Investiture of the Gods ch. 64 · 萬鴉壺 of Luo Xuan: "万只火鸦飞腾入城，口内喷火" |
| 305 | `heaven_fire_transformation` | **Tiga Transformasi Misterius Api Langit** / Heaven Fire Three Mysterious Transformations | DT · Bumi-M · UBAH | FEAT meditate_at(nether, 10 min), COMP 45; INH(FIRE) | ST 3 (window 600t) TRANSFORM: st1 EMP FIRE +40% + speed I; st2 +90% + fire_resistance + strength I; st3 +150% + slow falling + 1 air jump; the form lasts 600t from the last stage; SELF_PENALTY 400t weakness II + qi regen ×0 · qi 60 per stage · cd 120s after the end | three nested flame layers (orange, blue soul fire, white-gold); ♪ beacon select | – | N | Battle Through the Heavens · 天火三玄变, created by Xiao Xuan and kept by Burning Flame Valley: it bursts the Heavenly Flames, hurts the meridians, then a weak period follows |
| 306 | `phoenix_sky_roaring_strike` | **Pukulan Phoenix Mengaum Langit** / Phoenix Sky-Roaring Strike | DT · Bumi-T · KENDALI | BOOK fire_aspect, COMP 45 | NOVA(r 5→14) > BIND stun 40t; PILLAR onTargets 8→16 (r2, h8, delay 6t) > DMG 2.5 + LCH .8 + DOT fire .4/s 5s · qi 100 · cd 25s | lava geysers 8 b high (COLUMN orange + FLAME, particles only); SHK .4; ♪ lava pop + magma jump | SCATTER r2 | Y | Soul Land · 凤凰啸天击, Ma Hongjun's 4th soul skill: nearby foes are stunned and burned by lava erupting skyward |
| 307 | `nine_dragon_fire_cover` | **Tudung Api Ilahi Sembilan Naga** / Nine Dragon Divine Fire Cover | DU · Bumi-R · AREA | PAV | FIELD(aim ≤32, r 6→16, walls, 90t, pulse 10) > DMG 1.2 + IGN 2s; @90 ZONE(field) > DMG 4 · qi 200 · cd 60s | red-gold DOME, nine BEAST DRAGON ribbons, dragon-head GIANT_ITEMs ≤5; SHK .3; ♪ dragon growl 1.4 at .4 | BOWL 6 (@90) | Y | Investiture of the Gods ch. 13/46 · 九龍神火罩 of Taiyi Zhenren: it traps Shiji and burns Sun Tianjun to ash |
| 308 | `samadhi_true_fire` | **Api Sejati Samadhi** / Samadhi True Fire | DU · Bumi-M · AREA | FEAT kills_with(FIRE, drowned/guardian, 30), COMP 70 | CHAN 60t/5: CONE(12→24, 35°) > DMG .8 + DOT fire .5/s 10s (vs FIRE_IMMUNE ×1, WET ×1.5) + EFF blindness 40t; SUMMON 5 flame carts (anchor pentagon, 200t, attack 20t, range 12) > DMG .6 · qi 180 + 20/s · cd 45s | crimson-violet jet with nose LARGE_SMOKE; blazing minecart GIANT_ITEMs ×5; ♪ fire ambient + blaze | FAN 16 35° d2 | Y | Journey to the West ch. 40–42 · Red Boy's 三昧真火: he beats his nose and breathes it while his five carts blaze; the Dragon Kings' rain only makes it burn hotter (ch. 41) |
| 309 | `eight_trigrams_furnace` | **Tungku Delapan Trigram** / Eight Trigrams Furnace | IA T1 · Langit-R · KENDALI | FEAT alchemy_count 49; PAV | FIELD(aim ≤32, r 16→28, 200t, pulse 20) > DMG 1 (vs UNDEAD ×2); @0–60 PULL .5; @0 BIND root 140t (boss 35t); @200 NOVA(r8) > DMG 4 · qi 280 · cd 90s | bronze furnace GIANT_BLOCK (cauldron ×10) on a 16-b bagua RUNE_CIRCLE with ☰☱☲☳☴☵☶☷ GLYPHs; SHK .3; ♪ blast furnace + bell | PIT 6/3 | Y | Journey to the West ch. 7 · Laozi refines Sun Wukong in the Eight Trigrams Furnace for 49 days (the Fiery Eyes are the separate `fiery_golden_eyes`) |
| 310 | `phoenix_flame_sears_heavens` | **Api Phoenix Membakar Langit** / Phoenix Flame Sears the Heavens | IA T4 · Langit-M · PAMUNGKAS | FEAT first_kill(WITHER, FIRE) | FRONT dive (L48, w3, h4, v3) > DMG 3; onEnd ZONE(r 10→18) > DMG 6 + DOT fire 1/s 8s + IGN 8s · qi 300 · cd 60s | giant fire phoenix (SKY_SILHOUETTE BIRD, 20→40 b wingspan), red VIGNETTE; SHK .7; ♪ dragon flap + large firework blast | BOWL 8 | Y | Against the Gods ch. 440/563 · 凤炎焚天 Phoenix Flame Sears the Heavens, a realm of the World Ode of the Phoenix |
| 311 | `golden_emperor_flame` | **Api Kaisar Emas Pembakar Langit** / Golden Emperor Burning Sky Flame | IA T7 · Langit-T · AREA | FEAT kills_with(FIRE, wither, 3), COMP 85 | FIELD(aim ≤48, r 20→32, 200t, pulse 20) > DMG 1.5 + STRIP_BUFFS (resistance, absorption, strength, regeneration, fire_resistance, speed) · qi 400 · cd 120s | golden-white flame sea with rising embers, gold VIGNETTE; ♪ blaze chorus | – | Y | Battle Through the Heavens · 金帝焚天炎, 4th on the novel's Heavenly Flame Ranking, the Gu clan's inherited flame (Xun'er) |
| 312 | `tongtian_divine_fire_pillars` | **Pilar Api Ilahi Penembus Langit** / Tongtian Divine Fire Pillars | IA T9 · Langit-T · AREA | FEAT trib(IA layer quality ≥ Sempurna) | FIELD octagon (aim ≤48, r 14→24, walls, 120t, 8 emitters, pulse 20): each emitter > DMG 1.2 on one target (≤32 per pulse); @120 NOVA(field) > DMG 6 · qi 450 · cd 150s | 8 magma/shroomlight GIANT_BLOCK pillars 10 b tall with coiling dragon ribbons; SHK .6; ♪ blaze + thunder .5 | COLUMN r2 d8 ×8 | Y | Investiture of the Gods ch. 52 · 通天神火柱: Yunzhongzi raises eight pillars by the trigrams, each looses 49 fire dragons, and Wen Zhong dies |
| 313 | `nine_suns_heavens_fury` | **Murka Langit Sembilan Matahari** / Nine Suns Heaven's Fury | TI · Abadi-R · PAMUNGKAS | FEAT first_kill(ENDER_DRAGON, FIRE) | CHG 60t ("15 breaths" counter); ZONE DENSEST(9) (aim ≤64, search r24) x9/6 (r 8→12) > DMG 6 + DOT fire 2/s 6s; @60 ZONE(r24) > DMG 12 · qiPct 55 (min 700) · cd 360s | METEOR suns (shroomlight/gold GIANT_BLOCK spheres with a corona), white-gold SKY; SHK 1.0; ♪ large blast ×9 + explode (sound) | BOWL 4 ×9 + BOWL 8 | Y★27 | Against the Gods ch. 1007/1197 · Nine Suns Heaven's Fury, 10th realm of the Golden Crow's Record of the Burning World; Huo Poyun charged it for fifteen breaths |
| 314 | `emperor_flame` | **Api Kaisar** / Emperor Flame | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `nine_suns_heavens_fury` L3) | FIELD(aim ≤64, r 32→40, 400t, pulse 20) > DMG 2 + IGN 3s; SELF inside the field: COST_MOD(FIRE qi ×0, cd ×0.4 floor); STACK onKill (max 22); @400 NOVA(r40) > DMG 15 + .5 × stacks · qiPct 75 · cd 900s | white-violet PETAL_RING lotus, 80 b visual (n16), GLYPH 帝; SHK .8; ♪ beacon activate .5 + bell | BOWL 10 | Y★36 | Terinspirasi dari 帝炎 Emperor Flame, the legendary 1st of the 23 Heavenly Flames (Battle Through the Heavens) |

#### 22.9.3 Es / Ice (400–413)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 400 | `ice_empress_pincer` | **Capit Permaisuri Es** / Ice Empress' Pincer | GC · Misterius-R · SERANG | DAO, COMP 10 | STRIKE(r4, 90°) > DMG 1 + EFF slowness III 60t; GRANT_HOOK melee (3 charges, 160t) > DMG 1 + slowness III; the 3rd also FRZ 40t; SELF resistance I 160t · qi 20 · cd 8s | emerald-ice claw gauntlets (DUST #7FFFD4 + SNOWFLAKE); ♪ glass 1.5 | – | Y | The Unrivaled Tang Sect ch. 133 · Ice Empress' Pincer, Huo Yuhao's palm strike |
| 401 | `star_anise_ice_coagulation` | **Pembekuan Es Adas Bintang** / Star Anise Ice Coagulation | GC · Misterius-M · AREA | DAO, COMP 20 | VOLLEY SIM n 6→18, FAN 40°, range 20→32, pierce 1 > DMG .8 + EFF slowness II 60t · qi 30 · cd 6s | eight-pointed snowflake GLYPH ❄, then thin ice-spear GIANT_BLOCKs ≤6; ♪ amethyst chime | – | Y | UTS ch. 467 · Star Anise Ice Coagulation, the ice-forming skill of the Star Anise Mysterious Ice Grass soul bone |
| 402 | `frozen_cloud_art` | **Seni Awan Beku** / Frozen Cloud Arts | NS · Misterius-M · AREA | BOOK frost_walker, COMP 30 | CONE(12→24, 30°) > DMG 1.5 + EFF slowness IV 80t + FRZ 40t; FIELD(r 4→8, 100t, pulse 20) > EFF slowness II · qi 60 · cd 12s | rolling white-blue cloud banks (MIST); ♪ snow golem shoot + powder snow | FAN 12 30° d1 | Y | Against the Gods ch. 361/383 · 冰云诀 Frozen Cloud Arts of the Frozen Cloud Immortal Palace |
| 403 | `tongtian_river_freeze` | **Sungai Tongtian Membeku** / Tongtian River Freeze | NS · Misterius-T · KENDALI | FEAT kills_with(any, drowned/guardian, snowy biome, 30), COMP 35 | WAVE(r1 8→22, v1) > FRZ 80t; @80 RECALL > DMG 2.5 (vs AQUATIC ×1.5) + EFF slowness III 60t (shatter) · qi 80 · cd 25s | flat packed-ice GIANT_BLOCK tiles ≤8 + SNOWFLAKE spreading (never real blocks), crack lines, SHARDS; ♪ glass chorus | – | Y | Journey to the West ch. 47–49 · the Great King of Spiritual Responses freezes the Tongtian River so the monk walks onto ice that breaks |
| 404 | `qianlan_ice_flame` | **Api Es Qianlan** / Qianlan Ice Flame | DT · Bumi-R · SERANG | BOOK frost_walker, COMP 45; PAV | BEAM(24→48, hw 1) > DMG 2.5 + FRZ 60t + DOT cold .4/s 6s; onKill NOVA(r3) > DMG 1 · qi 110 · cd 20s | deep-blue cold flame BEAM_CORE (SOUL_FIRE_FLAME + SNOWFLAKE); ♪ soul escape + glass | TUNNEL r1 L16 | Y | RMJ · 乾蓝冰焰 Qianlan Ice Flame, which Han Li tries to refine (donghua ep. 158) |
| 405 | `ice_snow_emperors_pride` | **Kebanggaan Dua Kaisar Es dan Salju** / Pride of the Ice and Snow Twin Emperors | DT · Bumi-T · PANGGIL | FEAT kills_with(any, stray, frozen biome, 50) | SUMMON 2 (200t): Ice Emperor attacks every 10t (range 12) > DMG .7; Snow Emperor DOMAIN(r 10→18, pulse 20) > DMG .4 + EFF slowness III; @200 NOVA(r 10→18) > DMG 3 + FRZ 60t · qi 140 · cd 50s | BEAST SCORPION silhouette + snow-empress particle figure, blizzard MIST; ♪ snow golem + glass | – | Y(½) | UTS · 冰雪二帝之骄傲 Pride of the Ice and Snow Twin Emperors, Huo Yuhao's 5th soul skill: an Ice Emperor + Snow Emperor fusion ice storm |
| 406 | `cold_ice_formation` | **Formasi Es Dingin** / Cold Ice Formation | DU · Bumi-R · AREA | PAV; BOOK frost_walker, COMP 60 | ZONE(aim ≤32, r 8→18) > DMG 2 + LCH .5 + BIND root 30t; @20 ZONE fromSky 20 (same) > DMG 5 + FRZ 80t · qi 220 · cd 60s | black banner GIANT_ITEM, angled packed/blue-ice GIANT_BLOCK spikes, a packed-ice mountain 12→20 b falling; SHK .7; ♪ glass + anvil | BOWL 8 | Y | Investiture of the Gods ch. 44/46 · 寒冰陣 of Yuan Tianjun: "上有冰山如狼牙，下有冰块如刀剑"; broken by Puxian |
| 407 | `broken_moon_shadow` | **Bulan Patah Menyapu Bayangan** / Broken Moon Brushing Shadow | DU · Bumi-T · GERAK | PAV | CHARGES 3 (window 100t): DASH 12→20; each AFTERIMAGE @20 NOVA(r3) > DMG 1.5 + FRZ 40t; after the 3rd: SELF invisibility 60t + NOVA(r16) > UNTARGET · qi 100 · cd 25s | crescent-moon SHARDS + silver-blue AFTERIMAGE; ♪ amethyst cluster break | – | Y | Against the Gods · 断月拂影 Broken Moon Brushing Shadow, an ice movement art that conceals the aura |
| 408 | `bone_chilling_flame` | **Api Dingin Tulang Roh** / Bone Chilling Flame | IA T1 · Langit-R · AREA | ITEM heavenly_flame_seed:bone_chilling; INH(ICE) | WAVE(r1 20→36, v1.5) > DMG 3 + FRZ 80t; SELF COST_MOD(ICE qi ×.7) 200t · qi 280 · cd 70s | pale cold flame ring with ghost wisps (white SOUL_FIRE_FLAME, SCULK_SOUL); ♪ soul escape | RING 16/6/1 | Y | Battle Through the Heavens · 骨灵冷火 Bone Chilling Flame, Yao Lao's Heavenly Flame |
| 409 | `unparalleled_chill` | **Dingin Tak Tertandingi** / Unparalleled Chill | IA T3 · Langit-R · SERANG | BOOK frost_walker, COMP 80 | VOLLEY SEEK 1 (range 40) > DMG 5; onHit NOVA(r 6→10) > DMG 3 + FRZ 80t (shatter) · qi 260 · cd 40s | a crystal blade bursting into hundreds of SHARDS; SHK .4; ♪ glass | – | Y | UTS ch. 538 · Unparalleled Chill: a blade that shatters and releases trapped extreme cold |
| 410 | `frozen_end_divine_art` | **Seni Ilahi Akhir Beku** / Frozen End Divine Art | IA T5 · Langit-M · WILAYAH | FEAT meditate_at(frozen peaks or ice spikes, 20 min), COMP 80 | DOMAIN(r 24→32, 240t, pulse 20) > DMG 1.5 + ATTR movement −95%; @240 ALL(domain) > EXEC .25 (bosses DMG 10) · qi 380 · cd 120s | snowfield DOME where flakes hang still, an aurora edge; SHK .3; ♪ amethyst resonate .5 | – | N | Against the Gods ch. 383–385 · Frozen End Divine Art, left by Ancestor Mu Bingyun and grasped only by Xia Qingyue (really the Ice Phoenix God Investiture Canon, ch. 1277) |
| 411 | `ice_phoenix_frost_seal` | **Segel Embun Beku Phoenix Es** / Ice Phoenix Frost Seal | IA T9 · Langit-T · PAMUNGKAS | FEAT kills_with(ICE, elder_guardian, 3) | ZONE fromSky 40 (aim ≤64, r 20→24) > DMG 4 + FRZ 120t; @40 RECALL > DMG 6 (shatter) · qi 500 · cd 180s | a 64-b hexagram snowflake GLYPH descending from a CLOUD_DISC; SHK .6; ♪ beacon activate .5 + bell | DISC r10 d2 | Y | Terinspirasi dari the Ice Phoenix God Investiture Canon (Against the Gods); the seal itself is game design |
| 412 | `ice_phoenix_descent` | **Turunnya Phoenix Es** / Ice Phoenix Descent | TI · Abadi-R · PAMUNGKAS | FEAT first_kill(ENDER_DRAGON, ICE) | SUMMON 1 colossal phoenix (240t): 3 dives at DENSEST clusters every 60t = FRONT(L40, w4) > DMG 6 + FRZ 80t; @240 NOVA(r 20→32) > DMG 10; SELF immune to slowness and freezing + EMP(ICE +50%) 240t · qiPct 55 (min 700) · cd 360s | 40-b ice phoenix SKY_SILHOUETTE, SHARDS; SHK .7; ♪ dragon growl 1.6 + glass | BOWL 8 | Y★27 | Terinspirasi dari the Ice Phoenix soul of the Snowflower Ribbon (ch. 243) and the Ice Phoenix God Investiture Canon (Against the Gods) |
| 413 | `freeze_mount_qi` | **Membekukan Gunung Qi** / Freezing Mount Qi | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `ice_phoenix_descent` L3) | DOMAIN(r40, 400t, pulse 40) > DMG 2 + EFF slowness III; @200 ALL(r40, max 64) > FRZ 160t + DMG 8; @400 RECALL > DMG 12 (shatter) · qiPct 75 · cd 900s | whiteout MIST + ice VIGNETTE (96-b visual), ice-cube GIANT_BLOCKs on frozen targets (≤24); SHK .5 | – | Y★36 | Investiture of the Gods ch. 39 · Jiang Ziya calls three days of wind and snow and freezes Mount Qi, trapping the Shang army in ice |

#### 22.9.4 Petir / Lightning (500–513)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 500 | `zidian_whip` | **Cambuk Zidian** / Zidian Purple Lightning Whip | GC · Misterius-R · SERANG | BOOK channeling, COMP 15 | CHAIN(range 10→24, jumps 1→4, jump 6, falloff .45) > DMG 1.2 + BIND stun 30t; first hop also PULL 4 b · qi 25 · cd 6s | violet BOLT whip, ring glint; ♪ lightning impact 2 at .3 | – | Y | Mo Dao Zu Shi · 紫电 Zidian, Yu Ziyuan's ring that becomes a purple-lightning whip, inherited by Jiang Cheng |
| 501 | `lightning_flash_step` | **Kilat: Tiga Ribu Gerak Petir** / Three Thousand Lightning Movement: Lightning Flash | GC · Misterius-M · GERAK | DAO, COMP 10 | CHARGES 2 (80t each): DASH 8→16 + PATH(w1.5) > DMG .8 + BIND stun 10t; t≥2: AFTERIMAGE NOVA(r3) > DMG 1 · qi 20 | purple-white TRAIL + AFTERIMAGE; ♪ riptide 2 | TRENCH path×1×1 | Y | Battle Through the Heavens ch. 394/509 · the Di-class agility technique Three Thousand Lightning Movement, 1st stage "Lightning Flash" |
| 502 | `lightning_mark_armor` | **Tanda, Zirah dan Sayap Petir** / Lightning Mark, Armor and Wings | GC · Misterius-T · LINDUNG | DAO, COMP 20 | SELF EMP +15% + speed I 600t; GRANT_HOOK melee: CHAIN(jumps 1→4) > DMG .5; GRANT_HOOK on_hurt (melee attacker) > DMG .4; t≥2: slow-falling wings · qi 40 · cd 45s | crackling plates (ELECTRIC_SPARK SHIELD_SHELL), WINGS from DT; ♪ beacon select | – | Y (no wings) | Martial God Asura ch. 1210/1667/3254 · Chu Feng's Lightning Mark, Lightning Armor and Lightning Wings |
| 503 | `three_thunder_styles` | **Tiga Gaya Guntur** / Three Thunder Styles | NS · Misterius-M · AREA | BOOK channeling, COMP 30; PAV | ST 3 (window 80t): st1 DASH 6 + PATH > DMG 1.2; st2 CONE(8→14, 35°) > DMG 1.5 + BIND stun 20t; st3 ZONE(aim ≤20, r 4→8) > DMG 2.5 + LCH .6 · qi 25/30/45 · cd 12s after | BOLT arcs growing per style, thunderclap SHOCK_RING; ♪ thunder rising | BOWL 3 (st3) | Y | Martial God Asura ch. 5 · Three Thunder Styles of the Azure Dragon School's founder: "fast as lightning, strong as thunder" |
| 504 | `five_thunder_method` | **Metode Lima Guntur** / Five Thunder Method | NS · Misterius-T · KENDALI | FEAT casts_with(LIGHTNING, raining or thundering, 50) | ST 4 (window 120t): st1 NOVA(r 6→12) > KNK 1; st2 NOVA(r12) > EFF blindness 60t; st3 RAIN(r 12→20, impacts 5→12, 30t) > DMG 1.5 + BIND stun 20t; st4 NOVA(r12) > MARK WET 300t, SELF extinguish · qi 20/20/50/20 · cd 20s | 令 token GLYPH struck once to four times, client rain particles (no weather); SHK .25 | SCATTER r1 (st3) | Y | Journey to the West ch. 45 · the Tiger Strength Immortal strikes his token once for wind, twice for clouds, three times for thunder and lightning, four times for rain; Lord Deng calls it a true 五雷法 |
| 505 | `lightning_emperor_art` | **Seni Pusaka Kaisar Petir** / Lightning Emperor Precious Technique | DT · Bumi-R · SERANG | BOOK channeling, COMP 50; PAV | BEAM(40→64, hw 1, pierce) > DMG 3 + MARK DAO_VULN(LIGHTNING +30%, 200t); t≥4 onHit RAIN(r4, impacts 5, 10t) > DMG 1 · qi 100 · cd 15s | a pointing hand of lightning in the sky (violet PALM) + BEAM_CORE bolt; ♪ lightning impact | TUNNEL r1 L24 | Y | Perfect World ch. 1405 · Lightning Emperor Precious Technique (the finger image and the split are game design) |
| 506 | `golden_light_formation` | **Formasi Cahaya Emas** / Golden Light Formation | DT · Bumi-T · AREA | PAV; FEAT kills_with(LIGHTNING, undead, 100) | FIELD ring (aim ≤32, r 10→18, 160t, 7→21 emitters, pulse 20): ALL(field, max 8→16) > DMG .9 (vs UNDEAD ×1.5) + EFF blindness 40t · qi 150 · cd 45s | mirror GIANT_ITEMs (glass panes on poles, ≤9), golden BEAM_CORE strike lines; ♪ bell + thunder | – | Y | Investiture of the Gods ch. 44/46 · 金光陣 of Jin Guang Shengmu: 21 poles with mirrors; thunder shakes them and golden light fires |
| 507 | `extinction_thundercloud` | **Seni Awan Ungu: Awan Petir Pemusnah** / Purple Cloud Art: Extinction Thundercloud | DU · Bumi-R · AREA | PAV; BOOK channeling, COMP 60 | FIELD(aim ≤48, r 16→32, 200t): RAIN 40 impacts over 200t (ir 2) > DMG 1.5 + BIND stun 10t; @200 ALL(field) > DMG 4 · qi 220 · cd 75s | violet CLOUD_DISC ceiling 24 b up, BOLT_STORM; ♪ thunder loop | SCATTER r1 | Y | Against the Gods · 紫云功 Purple Cloud Art, 8th realm 寂灭雷云 Extinction Thundercloud |
| 508 | `divine_sword_imperial_thunder` | **Seni Sejati Pedang Ilahi Mengendalikan Guntur** / Divine Sword Imperial Thunder Art | DU · Bumi-T · SERANG | FEAT kills_with(LIGHTNING, holding a sword, 200), COMP 70 | CHG 30t; BEAM(32→64, hw 2) > DMG 5 ? !HOLDING_SWORD ×.7; onEnd CHAIN(from the farthest hit, jumps 6, jump 6) > DMG 2 · qi 240 · cd 50s | sky COLUMN of lightning into the raised sword, spectral sword GIANT_ITEM ×12, BOLT branches; SHK .5 | TRENCH 24×3×3 | Y | Jade Dynasty (Zhu Xian) · 神剑御雷真诀 Divine Sword Imperial Thunder Art, the Qingyun Sect's signature spell |
| 509 | `lei_zhenzi_wings` | **Sayap Lei Zhenzi** / Lei Zhenzi's Wind-Thunder Wings | IA T1 · Langit-R · UBAH ★ | FEAT meditate_at(altitude ≥150 while thundering, 5 min); INH(LIGHTNING) | `flight_form`: TRANSFORM 400t with real flight via FlightLease, golden staff (ATTR attack +50%, REACH +2); GRANT_HOOK melee > NOVA(r3) > DMG 1.5; ST recast: DASH 16 + PATH > DMG 2 · qi 250 + 3/s · cd 120s | feathered WINGS with 風 and 雷 GLYPHs, golden staff GIANT_ITEM; ♪ phantom flap + thunder | – | N | Investiture of the Gods ch. 21 · Lei Zhenzi eats two red apricots, grows wings marked "wind" and "thunder", and receives a golden staff from Yunzhongzi |
| 510 | `tribulation_thunder_sword` | **Pedang Petir Kesengsaraan Surgawi** / Heavenly Tribulation Thunder Sword | IA T4 · Langit-M · PAMUNGKAS | FEAT trib(lifetime strikes ≥30) | CHG 40t; FRONT from the sky along the aim (L 48→64, w3, h12, v6) > DMG 6 ? TRIB_STRIKES(+1% each, cap +100%); PILLAR onTargets 5 (r3) > DMG 2 · qi 320 · cd 60s | gathering CLOUD_DISC, then a 60-b violet LASER sword falls; SHK .7; ♪ thunder | FISSURE 32×2×5 | Y | Against the Gods · 天劫雷剑 Heavenly Tribulation Thunder Sword, part of Yun Che's Heavenly Dao Divine Power (天道神力) |
| 511 | `thunderbolt_ancestral_symbol` | **Simbol Leluhur Halilintar** / Thunderbolt Ancestral Symbol | IA T7 · Langit-T · WILAYAH | FEAT kills_with(any, thundering, 300) | DOMAIN(r 24→32, 300t, pulse 10): ALL(max 1, RANDOM) > DMG 1.5; every 60t NOVA(r12) > DMG 2; SELF CD_RATE(LIGHTNING ×2, within the floor) 300t · qi 420 · cd 150s | a radiant violet orb above the caster (amethyst GIANT_BLOCK + glow) with orbiting arcs; ♪ beacon ambient + thunder | – | N | Martial Universe ch. 969/982/1033 · Thunderbolt Ancestral Symbol, one of the eight Ancestral Symbols (the sun image is visual flavour only) |
| 512 | `tribulation_thunder_emperor_array` | **Formasi Kaisar Petir Kesengsaraan** / Heavenly Tribulation Thunder Emperor Array | TI · Abadi-M · PAMUNGKAS | FEAT trib(reach TI with quality ≥ Sempurna) | FIELD(aim ≤64, r 24→32, 200t) x9/22: ALL(field, max 32) > DMG 2 + DMG% 8% (boss 3%); the 9th ×2 · qiPct 55 (min 700) · cd 420s | 64-b RUNE_CIRCLE on the ground mirrored in the sky, BOLT_STORM; SHK .5 per strike, 1.0 on the last; ♪ thunder | SCATTER r3 ×9 | Y★27 | Against the Gods · 天劫雷帝阵 Heavenly Tribulation Thunder Emperor Array, part of the Heavenly Dao Divine Power (mirrors DESIGN 3's %-of-max-health tribulation) |
| 513 | `nine_heavens_thunder_venerable` | **Tianzun Guntur Sembilan Langit** / Nine Heavens Thunder Venerable | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `tribulation_thunder_emperor_array` L3) | TRANSFORM 400t (SCALE ×1.5 with room); SUMMON 12 thunder deities (follow, attack every 10t, range 24→40) > DMG 2; every 80t NOVA(r40) > DMG 4 + BIND stun 40t; @400 ALL(r40, max 64) > DMG 8 · qiPct 75 · cd 900s | 24 silhouettes in the clouds (12 phantoms drawn twice, SKY_SILHOUETTE), third-eye BEAM_CORE, dark SKY; SHK .6 | SCATTER r4 ×6 (DENSEST) | Y★36 | Investiture of the Gods ch. 99 · Wen Zhong is invested as 九天應元雷聲普化天尊, leading the 24 deities of the Thunder Department |

#### 22.9.5 Tubuh / Body (600–612)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 600 | `octane_blast` | **Ledakan Delapan Kutub** / Octane Blast | GC · Misterius-R · SERANG | DAO | x8/2 STRIKE(r 4.5, 35°, ≤3 targets) > DMG .15; the 8th > DMG .9 + KNK 1; if all 8 hit one target: NOVA(r 3→8) > DMG .5 (≈2.5 on one target) · qi 25 · cd 6s | eight gold shock rings from the fist (SWEEP_ATTACK + DUST #FFD54A), final FLASH + EXPLOSION + CRIT; SHK .1; ♪ mace air .8→1.6, mace heavy on the 8th | SPHERE 2 (final target) | Y | Battle Through the Heavens · 八极崩 Octane Blast, a high Xuan-class Dou technique from Yao Lao; mastered, it hides eight layers of force |
| 601 | `worldwalking` | **Langkah Penjelajah Bumi** / Worldwalking | NS · Misterius-M · GERAK ★ | DAO | `earth_travel`: hidden ground travel 16→48 toward the aim in 8t (safe exit checked before spending, else refused); SELF invisibility on the way + no fall damage 40t; at the exit PILLAR(r 3→6, h6) > DMG 1.5 + LCH 1 + EFF slowness II 40t · qi 40 · cd 10s | dust furrow racing along the ground (FALLING_DUST of the ground block, read client-side), pointed-dripstone GIANT_ITEMs ≤6 bursting up at the exit; SHK .1; ♪ warden dig → warden emerge | TRENCH path×2×2 nat (from 3 b ahead) | N | Coiling Dragon · Worldwalking, an earth profound mystery used for stealth and escape; Investiture of the Gods · Tu Xingsun's earth-travel art (地行术) |
| 602 | `throbbing_pulse` | **Denyut Nadi Dunia** / Throbbing Pulse of the World | NS · Misterius-T · AREA | COMP 30; BOOK blast_protection | x3/10 WAVE(r1 6 / 10 / 14 → 36, v1.5) > DMG .6 pierce + LCH .5 + EFF slowness III 60t; the 3rd ring on a target hit by both earlier rings: + DMG .6 pierce ("shattered from within"); SELF SHLD .15 100t · qi 70 · cd 22s | SHOCK_RING ×3 with BLOCK_CRUMBLE of the ground block, cracked-stone-brick slab GIANT_BLOCKs riding the ring ≤8; SHK .2 ×3; ♪ warden step .5 ×3 + mace ground | DISC r8 d2 nat | Y | Coiling Dragon (Books 15–16) · Throbbing Pulse of the World, one of the six profound mysteries of the Laws of the Earth; it defends against and launches spiritual attacks |
| 603 | `disorderly_cloak_hammer` | **Palu Jubah Angin Kacau** / Disorderly Cloak Hammer Technique | DT · Bumi-R · AREA | BOOK density; PAV | CHAN 54t/6 (interrupt .15): x9/6 ZONE(aim ≤20, r3) > DMG .2 × (1 + .15k) (k = blow 0–7); the 9th ZONE(r 6→14) > DMG 2.4 + LCH .9 · qi 120 · cd 35s | phantom mace GIANT_ITEM growing 4→10 b and swinging in arcs, dust SHOCK_RING per blow; the 9th adds FLASH + EXPLOSION_EMITTER + SONIC_BOOM; SHK .1 per blow, .4 on the 9th; ♪ anvil .6→1.4, 9th mace heavy + explode (sound) | SPHERE 4 (9th blow, 1 below the surface) | Y | Soul Land · 乱披风锤法 Disorderly Cloak Hammer Technique of the Clear Sky Sect, whose force builds over chained blows |
| 604 | `bright_king_art` | **Seni Raja Cerah** / Bright King Art | DT · Bumi-M · LINDUNG | COMP 30; BOOK protection | ST 2 (window 400t): st1 SELF 400t DR .35→.6 + BUFF attack_damage +40% (knockback immune) + COUNTER(400t, store .25 of damage taken, cap 4); st2 or end: release NOVA(r 6→14) > DMG 2 × stored (≤4) + KNK 1.5 · qi 100 · cd 60s | gold skin sheen (gold SHIELD_SHELL), a turning ring of gold glyph motes at the chest (ENCHANT + DUST #FFD54A), release FLASH + END_ROD SHELL; SHK .25 on release; ♪ beacon select, bell resonate on release | DISC r4 d1 nat (release) | Y | A Record of a Mortal's Journey · 明王诀 Bright King Art, a Buddhist body art from the Feng family's secret cave, derived from the body-refining base of the Three Brahmic Saint Art; later one of the three arts fused into the Brahmic Saint True Devil Art |
| 605 | `great_way_of_buddha` | **Jalan Agung Buddha** / Great Way of the Buddha | DT · Bumi-T · PULIH | COMP 60 | SELF HEAL .3→.5 over 600t; GRANT_HOOK on_hurt ≥10% max health > HEAL .1 (icd 40t, 600t); onEnd CLNS + BUFF health_boost I 6000t (recasts inside it stack to III) · qi 110 · cd 90s | gold-white halo behind the head (END_ROD ring), HEAL_RISE motes, a soft stupa outline (DUST) on cast; ♪ beacon activate + amethyst chime | – | Y | Against the Gods ch. 147/287/786 · 大道浮屠诀 Great Way of the Buddha, Yun Che's body art of astonishing regeneration; brutal tempering leaves his body reborn |
| 606 | `dragonblood_warrior` | **Wujud Prajurit Darah Naga** / Dragonblood Warrior Transformation | DU · Bumi-R · UBAH | FEAT first_kill(ENDER_DRAGON, any); INH(BODY) | TRANSFORM 600t (SCALE ×1.3 with room; form armor +10, attack +6, speed I); @0 NOVA(r 5→12) tail sweep > DMG 2 + KNK 1.5; GRANT_HOOK melee (icd 10t) > CONE(4, 40°) > DMG .8 (claw) · qi 180 · cd 120s | dark dragon-scale overlay, horn and tail GIANT_ITEMs riding the caster (≤3), DRAGON_BREATH motes; SHK .25; ♪ dragon growl .8 + dragon flap | RING 7/4/1 (tail sweep) | N | Coiling Dragon · Linley Baruch awakens his clan's Dragonblood Warrior bloodline transformation |
| 607 | `six_paths_fist` | **Tinju Enam Jalan Reinkarnasi** / Six Paths Reincarnation Fist | DU · Bumi-T · SERANG | BOOK unbreaking; PAV | x6/4 BEAM(24→48, hw 1.5, pierce) > DMG .6; onKill NOVA(r4) > DMG .8 ("Reinkarnasi", chains, ≤8 bursts) · qi 160 · cd 45s | six phantom fists (stained-glass GIANT_BLOCKs: gold, white, azure, crimson, ash, violet) sliding forward, a six-spoked wheel GLYPH behind the caster (DUST + END_ROD), SOUL bursts; SHK .25, .4 on the sixth; ♪ warden boom 1.6→.8 | TRENCH 24×3×3 (from 3 b ahead) | Y | Shrouded the Heavens · 六道轮回拳 Six Paths Reincarnation Fist of the Holy Physique lineage, created by its founder and passed on through the "Old Madman" (老疯子) |
| 608 | `eighth_hex_body` | **Segel Kedelapan: Segel Tubuh** / Eighth Hex: Bodily Cultivation Hexing | IA T1 · Langit-R · KENDALI | BOOK thorns, COMP 60; SCR 3 | NOVA(r 16→32) > DMG 1.2 pierce + ATTR attack −60% 200t + ATTR armor −100% 200t + ATTR movement −50% 200t + MARK VULNERABLE +40% 200t (bosses: half values); SELF BUFF attack_damage +30% 200t · qi 240 · cd 60s | a red 封 GLYPH (scale 12) lying on the ground slams down, a red glyph swirl on each hexed monster (DUST #D02020 + ENCHANT); SHK .3; ♪ bell .6 + anvil | – | N (heir hexes) | I Shall Seal the Heavens ch. 954 · the Eighth Hex, Bodily Cultivation Hexing, a hex laid on the body itself |
| 609 | `undying_emperor_fist` | **Tinju Kaisar Abadi** / Undying Emperor's Fist | IA T4 · Langit-M · PAMUNGKAS | COMP 70; PAV | STRIKE(r 6, 60°) > DMG 7 + KNK 3; FRONT shock (L 20→48, w6, h4, v4) > DMG 3 + KNK 2; x3/6 RECALL (the struck target) > DMG .6 · qi 320 · cd 60s | a giant gold fist (gold-block GIANT_BLOCK knuckles, scale 3→12) sliding down the shock line, a faint emperor silhouette (AVATAR outline) behind the caster; SHK .7; ♪ mace heavy + warden boom + goat ram | TRENCH 32×5×3 (along the shock line) | N (disciple ultimate) | A Will Eternal ch. 671/696/892 · Undying Emperor's Fist, secret art of the Undying Bones level of the Undying Live-Forever Codex |
| 610 | `golden_ape_roar` | **Kera Raksasa Emas: Raungan Vajra** / Golden Giant Ape and Vajra Roar | IA T7 · Langit-T · UBAH | FEAT first_kill(WARDEN, BODY, IA T7); INH(BODY) | ST 2 (window 500t): st1 TRANSFORM 500t (SCALE ×2.5 with room, else a 12-b AVATAR mirrors the swings) + CONE(16→24, 60°) "Vajra Roar" > DMG 3 + EFF weakness III 100t + EFF slowness IV 100t + KNK 2; GRANT_HOOK melee (icd 10t) > NOVA(r4) > DMG 2; st2 "Hantaman Gunung": BLINK leap AIM ≤24 + NOVA(r 10→20) > DMG 5 + LCH 1.2, ends the form · qi 420 · cd 150s | golden fur motes (DUST gold), the roar as a SONIC_BOOM ring + SHRIEK + GUST cone, a gold-tinted AVATAR as the ape stand-in; SHK .45 roar, .7 slam; ♪ ravager roar .5 + warden roar, mace heavy ×2 on the slam | BOWL 8 (slam) | N | A Record of a Mortal's Journey · Han Li's golden giant ape form and its 金刚吼 Vajra Roar, from the 惊蛰十二变 transformations with Mountain Giant Ape true blood (the mountain slam is game design) |
| 611 | `cosmic_giant_form` | **Wujud Kosmik Langit-Bumi** / Cosmic Heaven-and-Earth Form | TI · Abadi-R · PAMUNGKAS | COMP 85, FEAT trib(lifetime strikes ≥60); INH(BODY) | SUMMON 1 colossal avatar (follow, 400t; the caster keeps his own size, unlike the universal `fa_tian_xiang_di`): every 50t ZONE DENSEST(1) (search r48, r 8→14) > DMG 4 + LCH 1; GRANT_HOOK melee (icd 10t) > avatar fist on the target > DMG 3; SELF DR .5 400t; @400 NOVA(r 24→32) > DMG 10 · qiPct 55 (min 700) · cd 360s | a colossal translucent gold AVATAR of the caster (α .35, 40→64 b; outline particles at Minimal), a cloud belt (MIST ring r20) at the waist, dust SHOCK_RING r16 per stomp; SHK .7 per stomp, 1.0 at the end; ♪ warden step .3 + mace heavy | DISC r6 d2 nat ×8 (stomps) | N | Journey to the West ch. 6 · Erlang Shen grows to a ten-thousand-zhang body against the Great Sage, who matches him |
| 612 | `ancient_god_trample` | **Injakan Dewa Kuno** / Ancient God's Trample | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `cosmic_giant_form` L3) | TRANSFORM 300t "Ancient God body" (SCALE ×1.5 with room; DR .3); x9/20 ZONE(8 b further along the look each step, r 8→12) > DMG 3 + SLAM .6 (nine footprints); @180 the 9th: NOVA(r 32→40) > DMG 16 + LCH 1 + BIND stun 120t · qiPct 75 · cd 600s | nine silver star marks between the caster's brows lighting one per step, a star-glyph disc of nine DUST stars under each footprint (STARFIELD), a dark-gold SKY_TEAR on the 9th; SHK 1.0; ♪ warden step .3 ×9 + beacon deactivate + dragon growl .5 | NINE 3 8 (footprints) + BOWL 10 | Y★28 | Terinspirasi dari the Ancient God body of Renegade Immortal, whose stars between the brows mark its might (ch. 921); the trample is game design |

#### 22.9.6 Kehidupan / Life (700–713)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 700 | `eternal_spring_art` | **Seni Musim Semi Abadi** / Eternal Spring Art | GC · Misterius-R · PULIH | DAO | AURA(r 6→10, 240t, pulse 20); ALLY+SELF: HEAL .25 over 240t; SELF BUFF QI_REGEN ×1.5 240t · qi 30 · cd 20s | green motes (HEAL_RISE + a DUST #6CFF8A ground ring), GLOW fireflies at night; ♪ amethyst chime 1.6 | – | Y | A Record of a Mortal's Journey · 长春功 Eternal Spring Art, the wood-attribute foundation art Han Li practises first |
| 701 | `blue_silver_entangle` | **Belitan Rumput Perak Biru** / Blue Silver Entangle | GC · Misterius-M · KENDALI | BOOK lure, COMP 10 | ZONE(aim ≤20, r 4→10) > BIND root 80→120t (boss 30t) + DMG .6 + EFF poison I 80t · qi 25 · cd 8s | blue-silver vine GIANT_ITEMs (twisting vines, scale 2, ≤8) coiling the targets, DUST #7FD8FF + small END_ROD; ♪ creaking sway + amethyst cluster | – | Y | Soul Land · 蓝银缠绕 Entangle, the first spirit ability of Tang San's Blue Silver Grass (a ten-year ring) |
| 702 | `earth_vitality` | **Vitalitas Bumi** / Vitality of the Earth | NS · Misterius-M · PULIH | DAO | NOVA(r 10→24); ALLY+SELF: HEAL .25→.35 + SHLD .15 400t (×2 on natural ground, one read-only check per ally) + BUFF resistance I 200t · qi 70 · cd 40s | green-gold light COLUMNs rising from the ground (HEAL_RISE + DUST #B8E04A), soil motes (FALLING_DUST of the ground block); ♪ amethyst resonate + beacon select | – | Y | Coiling Dragon (Book 15) · Vitality, one of the six profound mysteries of the Laws of the Earth |
| 703 | `blue_silver_parasite` | **Parasit Perak Biru** / Blue Silver Parasite | NS · Misterius-T · AREA | COMP 30 | ALL(r16, max 6→16, NEAREST) > DOT poison .2/s 8s + DRAIN .6 (allies within 8 share it); onKill NOVA(r3) > BIND root 40t · qi 60 · cd 20s | blue-silver vine GIANT_ITEMs climbing the victims (≤6), green life threads to the caster (MOTES_TO); ♪ creaking twitch + soft villager cure 1.8 | WITHER 2 ×≤6 (death spots) | Y | Soul Land · 寄生 Parasitic, the second spirit ability of Blue Silver Grass (a hundred-year ring) |
| 704 | `clear_heart_melody` | **Melodi Hati Jernih** / Clear-Heart Melody | DT · Bumi-R · PULIH | BOOK mending; PAV | @0 NOVA(r 16→32) > DMG .8 (vs UNDEAD ×2) ? TARGET_UNDEAD + EFF slowness IV 120t + EFF weakness III 120t; AURA(r 6→10, 400t, pulse 40); ALLY+SELF: CLNS + BUFF resistance I · qi 120 · cd 35s | five white-blue sound rings (SHOCK_RING + END_ROD), a ♫ GLYPH; ♪ harp chords + bell resonate | – | Y | Mo Dao Zu Shi · 清心音 Clear-Heart Melody of the Gusu Lan Sect, the calming melody that Jin Guangyao learned and twisted into 乱魄抄 |
| 705 | `seven_treasure_pagoda` | **Pagoda Kaca Tujuh Harta** / Seven Treasure Glazed Tile Pagoda | DT · Bumi-M · PULIH | PAV; BOOK luck_of_the_sea | AURA(r 6→10, 600t, pulse 20); ALLY+SELF, one tier per second: 1 EMP +25%, 2 speed II, 3 QI_REGEN ×2, 4 resistance I, 5 jump II, 6 regeneration I; @120 tier 7 "Tujuh Harta": every bonus ×1.5 · qi 130 · cd 90s | a seven-tier pagoda of stained-glass and glazed-terracotta GIANT_BLOCKs (≤7) shrinking upward and turning above the caster, rainbow DUST rings; ♪ beacon activate + amethyst chime rising per tier | – | Y | Soul Land · Ning Rongrong's Seven Treasure Glazed Tile Pagoda, a support martial soul whose fixed abilities boost allies (the seven values are game design) |
| 706 | `gold_devouring_beetles` | **Kawanan Kumbang Pemakan Emas** / Gold-Devouring Beetle Swarm | DU · Bumi-R · AREA | PAV; FEAT kills_with(LIFE, arthropods, 100) | ALL(r24, max 12→32) > DOT bleed .35/s 8s (pierce) + ATTR armor −30% 160t; STACK onKill +10% (max 10); SELF DRAIN .1 · qi 180 · cd 60s | SWARM of gold and silver beetle motes (40 / 120 / 240) clinging to the victims; ♪ bee loop aggressive .6 | WITHER 2 ×≤8 (kill spots) | Y | A Record of a Mortal's Journey · 噬金虫 Gold-Devouring Beetles, the swarm Han Li breeds, which later brings forth an insect king |
| 707 | `willow_god_branches` | **Cabang dan Daun Emas Dewa Dedalu** / Willow God's Branches and Golden Leaves | DU · Bumi-M · KENDALI | COMP 60; PAV | ALL(r 20→32, max 10→24) > BIND root 60t (boss 15t) + DMG 1.2; VOLLEY SEEK 24 (≤12 targets, ≤2 each) > DMG .9 (vs BOSS ×.6); SELF ? CASTER_LOW_HP(.5): HEAL .3 · qi 200 · cd 50s | a phantom willow (dark-oak GIANT_BLOCK trunk 2×10×2 + leaf crown, ≤4), branch whips (green TRAIL), golden leaves (FALLING_DUST gold block) flying to the targets; SHK .1; ♪ creaking sway + phantom flap + amethyst chime | WITHER 6 (around the caster) | Y | Perfect World · the Willow God (柳神) of Stone Village, Shi Hao's guardian, whose charred trunk sprouts anew |
| 708 | `blood_transforming_formation` | **Formasi Pelebur Darah** / Blood-Transforming Formation | IA T1 · Langit-R · AREA | PAV; FEAT kills_with(LIFE, night, 200) | FIELD(aim ≤32, r 14→28, h12, 300t, pulse 20) > DMG .5 + DMG% 1% (boss .5%) + EFF wither II 60t; SELF DRAIN .25 · qi 280 · cd 80s | a black-sand vortex (FALLING_DUST black concrete powder + SQUID_INK) turning to blood water on the victims (DUST #8A0010), a pulsing DOME edge, three blood-banner GIANT_ITEMs at the triangle points; SHK .1; ♪ warden heartbeat loop + shrieker .5 | WITHER 8 (centre) | Y | Investiture of the Gods ch. 44/46 · 化血陣 of Sun Tianjun (孫良), one of the Ten Absolute Formations: its black sand turns victims to blood water |
| 709 | `blood_clones` | **Klon Darah** / Blood Clones | IA T4 · Langit-M · PAMUNGKAS | FEAT first_kill(WITHER, LIFE, IA T4) | SUMMON 2→4 blood clones (cling, 300t, attack every 20t, range 16) > DMG .8; @300 the clones converge: ZONE DENSEST(1) (r 6→10) > DMG 3 per clone + SELF HEAL .1 per clone · qi 320 · cd 90s | translucent dark-red CLONEs of the caster dashing between targets (Minimal: AFTERIMAGE), red DUST bursts on the convergence; SHK .45; ♪ warden heartbeat + illusioner mirror move | BOWL 6 (convergence) | N | I Shall Seal the Heavens (Book 4) · Meng Hao's Blood Clones, cored on the meat jelly's skin and indestructible while he lives |
| 710 | `nezha_lotus_body` | **Tubuh Teratai Nezha** / Nezha's Lotus Incarnation | IA T7 · Langit-T · LINDUNG | COMP 85; INH(LIFE) | SELF 800t: DR .3 + CLNS + CHEAT_DEATH 1 (a lethal hit becomes a full HEAL + BUFF resistance III 60t + NOVA(r 10→20) > DMG 4, then the window ends) · qi 400 · cd 600s | a pink-white lotus PETAL_RING (n8, scale 6) under the feet, CHERRY_LEAVES aura; on rebirth TOTEM_OF_UNDYING (limited) + a PETALS storm; SHK .3 on rebirth; ♪ totem use + bell resonate | – | N | Investiture of the Gods ch. 14 · after Nezha's death, Taiyi Zhenren rebuilds his body from lotus roots and leaves (莲花化身) |
| 711 | `guanyin_sweet_dew` | **Embun Manis Guanyin** / Guanyin's Willow Sprig and Sweet Dew | TI · Abadi-R · PULIH | COMP 80; INH(LIFE) | FIELD(at the caster, r 24→32, h16, 200t, pulse 20); ALLY+SELF (players, pets, disciples, villagers, golems): HEAL .5 over 200t + CLNS; onEnd BUFF regeneration III 200t; hostiles ? TARGET_UNDEAD: DMG .8 per pulse; SELF RESTORE_QI .3 · qi 300 · cd 240s | a white vase (decorated-pot GIANT_ITEM, scale 8) tilting 20 b above with a willow-sprig GIANT_ITEM, glittering dew (GLOW + END_ROD + FALLING_DUST white), particles only, never a weather change; ♪ bell resonate + amethyst resonate + bubbles | – | N | Journey to the West ch. 26 · Guanyin revives Zhenyuan's felled Ginseng Fruit Tree with the sweet dew of her vase, sprinkled with her willow sprig |
| 712 | `life_death_domain` | **Wilayah Hidup dan Mati** / Life and Death Domain | TI · Abadi-M · PAMUNGKAS | COMP 90; FEAT deviation_recovered(IA) | DOMAIN(r 20→32, h16, 400t, pulse 20) > DMG .6 + DMG% 2% (boss 1%) + EXEC .1 (boss .05); ALLY+SELF: HEAL .6 over 400t; SELF DRAIN .2; @400 NOVA(r32) > DMG 12 · qiPct 55 (min 700) · cd 360s | a DOME split in two, a grey ash half (ASH + SCULK_SOUL) and a green-gold half, a river of SOUL + END_ROD between them, 生 and 死 GLYPHs; SHK .7 at the end; ♪ warden heartbeat + beacon ambient | WITHER 10 | Y★27 | Renegade Immortal · Wang Lin's Life and Death domain |
| 713 | `bodhisattva_tree` | **Pohon Kuno Bodhisattva: Seratus Kehidupan** / Bodhisattva Ancient Tree: Hundred Lives | Dewa · Dewa-M · PAMUNGKAS | GATE II (prereq `life_death_domain` L3; Puncak: `guanyin_sweet_dew` L4, `nezha_lotus_body` L4) | FIELD(at the caster, r 24→40, h16, 600t, pulse 20); ALLY+SELF: HEAL .9 over 600t + BUFF QI_REGEN ×3; SELF COMPREHENSION +.05/s (≤3 per cast) + CHEAT_DEATH 1 (1-HP floor once); every 100t NOVA(r40) > DMG 4 + EFF slowness III 100t; @600 NOVA(r40) > DMG 14 · qiPct 80 · cd 900s | a 30-b tree (jungle-log GIANT_BLOCK trunk 3×24×3 + azalea-leaf crown pieces, ≤6), glowing fruit motes (GLOW), life MIST, green-gold SKY; SHK .5 per pulse, 1.0 at the end; ♪ beacon activate + conduit ambient + bell | – | Y★37 | Battle Through the Heavens · the Bodhisattva Ancient Tree: Xiao Yan seeks its Bodhisattva Heart (ch. 828) and later sits inside it in "Hundred Lives Reincarnation" (ch. 1426) |

#### 22.9.7 Bayangan & Angin / Shadow & Wind (800–810)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 800 | `shadow_devil_fusion` | **Penyatuan Roh Iblis Bayangan** / Shadow Devil Demon Spirit Fusion | GC · Misterius-R · INDRA | BOOK swift_sneak, COMP 10 | SELF BUFF invisibility 400→600t; ALL(r32, max 32) > UNTARGET + MARK SOUL ×2 (the next hit) + caster-only highlight 400t · qi 25 · cd 30s | the caster's shadow detaches as a scouting blob (DUST #201030 + SQUID_INK), violet eye glyphs on marked monsters (VIEWER_MARK, never GLOWING); ♪ illusioner prepare blindness + phantom ambient .6 | – | N | Tales of Demons and Gods ch. 40–56 · Nie Li's Shadow Devil demon spirit, a trace-concealing scout; fusing with it needs Silver rank |
| 801 | `samadhi_wind` | **Angin Ilahi Samadhi** / Samadhi Divine Wind | NS · Misterius-M · KENDALI | DAO | CHAN 60t/10: CONE(14→24, 35°) > DMG .3 + EFF blindness 120t + EFF slowness II 60t + KNK .5 · qi 50 · cd 18s | yellow sand-wind CONE_SPRAY (FALLING_DUST sand, GUST, DUST #E8C46A); SHK .1; ♪ breeze whirl loop + wind charge burst | STRIP fan 20 | Y | Journey to the West ch. 21 · 三昧神风 Samadhi Divine Wind of the Yellow Wind Great King, which blinds Sun Wukong until Lingji Bodhisattva subdues him |
| 802 | `star_god_broken_shadow` | **Bayang Pecah Dewa Bintang** / Star God's Broken Shadow | DT · Bumi-R · GERAK | PAV; BOOK feather_falling | ST 2 (window 40t): st1 BLINK TO_TARGET chain 5→10 within 20 (≥3t apart, safe spots, i-frames during the chain), each STRIKE > DMG 1.8; each AFTERIMAGE @10 NOVA(r2) > DMG .6; st2 BLINK BACK to the start point · qi 90 · cd 25s | silver-blue AFTERIMAGE and CLONE, END_ROD star sparks, broken starlight TRAIL lines between the hops; ♪ enderman teleport 1.8 + amethyst hit | – | N | Against the Gods · 星神碎影 Star God's Broken Shadow, the Star God Realm's movement divine art used by Jasmine, which splits the user into afterimages |
| 803 | `wraith_butterflies` | **Kupu-kupu Arwah Perak** / Silver Wraith Butterflies | DT · Bumi-M · AREA | COMP 60; BOOK soul_speed | ALL(r24, max 8→20) > DOT soul .35/s 10s; SELF PROJECTILE_GUARD discard (12 charges, r3, 200t) · qi 100 · cd 35s | SWARM of silver butterflies (END_ROD + WHITE_ASH; 40 / 120 / 240), 12 of them orbiting the caster, a red drizzle at Maximal; ♪ phantom flap 2.0 (soft) | – | Y | Heaven Official's Blessing · Hua Cheng's silver wraith butterflies (死灵蝶), heralds of his presence that swarm his enemies |
| 804 | `banana_leaf_fan` | **Kipas Daun Pisang** / Banana Leaf Fan | DU · Bumi-R · AREA | BOOK wind_burst; INH(SHADOW) | ST 2 (window 60t): st1 FRONT(L 32→64, w 8→24, h6, v4) > DMG 2.2 + KNK 4 (never-moved list applies; fall damage happens normally); st2 CONE(24, 60°) > PULL 2 + DMG 1 · qi 200 · cd 45s | a giant leaf fan (big-dripleaf GIANT_ITEM, scale 6) swung, a wall of wind (GUST every 4 b, white CRESCENT_FLY streaks); SHK .25; ♪ breeze wind-charge burst .5 ×2 | STRIP fan 48 | Y | Journey to the West ch. 59–61 · Princess Iron Fan's banana-leaf fan blows Sun Wukong tens of thousands of li away; Lingji's Wind-Settling Pill lets him withstand it |
| 805 | `wind_roaring_formation` | **Formasi Angin Menderu** / Wind-Roaring Formation | IA T1 · Langit-R · AREA | BOOK punch; PAV | FIELD(aim ≤32, r 14→28, h14, 300t, pulse 10) > DMG .3 + IGN 1s; every 40t > KNK .4 (sideways spin) + LCH .2 + EFF blindness 40t; @300 ALL(field, max 32) > DMG 3 ("ten thousand blades") · qi 280 · cd 80s | four banner GIANT_ITEMs, a 14-b cyclone (VORTEX + GUST + FLAME), SWEEP_ATTACK blades; SHK .2; ♪ breeze whirl + fire ambient + elytra loop | STRIP 10 | Y | Investiture of the Gods ch. 44/46 · 風吼陣 of Dong Tianjun, one of the Ten Absolute Formations: wind and fire with ten thousand blades; Cihang breaks it with the Wind-Fixing Pearl |
| 806 | `soul_falling_formation` | **Formasi Jiwa Jatuh** / Soul-Falling Formation | IA T4 · Langit-M · KENDALI | PAV; FEAT kills_with(SHADOW, night, 300) | FIELD(aim ≤32, r 16→28, h12, 240t, pulse 40) > DOT soul .5/s 4s + BIND stun 30t (chance .25 per pulse); @240 ALL(field, max 32) > EXEC .15 (boss .05) · qi 340 · cd 100s | white paper banner GIANT_ITEMs with talisman seals (≤5), a 落魂 GLYPH, a small straw figure (hay GIANT_BLOCK) at the centre, SOUL particles drawn up out of the monsters; ♪ shrieker 1.6 (soft) | – | Y | Investiture of the Gods ch. 44/48 · 落魂陣 of Yao Bin (姚斌): a white paper banner with talisman seals, "白旛展动，魄消魂散", plus a straw-figure soul ritual |
| 807 | `billion_soul_flag` | **Panji Semiliar Jiwa** / One-Billion-Soul Flag | IA T7 · Langit-T · PAMUNGKAS | FEAT kills_with(SHADOW, any, 1000) | FIELD(aim ≤24, r 20→32, h16, 400t, pulse 20) > DMG .5 pierce; STACK onKill +3% (max 50); @400 NOVA(r 20→32) > DMG 4 + .15 × stacks · qi 420 · cd 150s | a waving black flag GIANT_ITEM 9 b tall, screaming soul streams (SOUL + SCULK_SOUL) circling it; SHK .45, .8 on the release; ♪ warden roar 1.5 + shrieker | SPHERE 6 (at the flag, on the release) | N | Renegade Immortal · the Soul Refining Sect's billion-soul flag, with the souls of 12 Soul Transformation ancestors sealed inside |
| 808 | `stygian_tiger_seal` | **Segel Harimau Alam Baka** / Stygian Tiger Seal | TI · Abadi-R · PANGGIL | FEAT first_kill(WARDEN, SHADOW, TI); INH(SHADOW) | FORBIDDEN_COST hp .1 (current); SUMMON 8 fierce corpses (follow, 500t, attack every 20t, range 16) > DMG 1.5; every 100t NOVA(r24) > EFF weakness II 100t + EFF slowness II 100t (terror) · qiPct 50 (min 600) · cd 300s | a floating black-iron seal (heavy-core GIANT_ITEM, scale 3), dark-tinted CLONEs of the monster types killed nearby in the last 60 s as the corpses (Normal 4, Maximal 8, the rest AFTERIMAGE), black-red resentful MIST, a flute-note TRAIL; SHK .4; ♪ warden roar .6 + flute + shrieker | WITHER 8 | N | Mo Dao Zu Shi · 阴虎符 Stygian Tiger Seal, which Wei Wuxian forges from the iron sword in the Tortoise of Carnage's cave and uses to command fierce corpses with his flute Chenqing |
| 809 | `kunpeng_art` | **Teknik Pusaka Kunpeng** / Kunpeng Precious Art | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `wind_roaring_formation` L3) | CHG 20t; FRONT travelling 128 (w 12→24, h8, v4, ignoreBlocks) > DMG 16 + KNK 2; SELF DASH 32 behind it (i-frames 10t); onEnd WAVE(origin the front's end, r1 8→24) > DMG 6 · qiPct 75 · cd 600s | Kun wings (WINGS planes 16×6 + a 60-b KUN SKY_SILHOUETTE), the crescent as three stretched white-glass LASERs, GUST every 16 b, a cloud wake; SHK 1.0; ♪ dragon flap .5 + breeze burst + warden boom | TRENCH 64×3×2 (from 6 b ahead) | N | Perfect World ch. 347 on · Shi Hao's Kun Peng precious technique (鲲鹏宝术); the 128-block wind crescent is game design |
| 810 | `karma_domain` | **Wilayah Karma** / Karma Domain | murid ★46 · Dewa-M · WILAYAH | DISCIPLE ★46 only (taught or self-comprehended; `DISCIPLE_ONLY`, outside the player's 168) | DOMAIN(r 16→32, h16, 400t, pulse 20) > LINK .4 400t (boss .25); ALLY (the master, disciples, pets): BUFF REFLECT ×3 against monsters inside · qiPct 70 (the disciple's own) · cd 300s | a dome of turning red-gold threads (CHAINS + DUST), a 因果 GLYPH above the disciple; ♪ bell resonate 1.4 + amethyst resonate | – | only | Renegade Immortal · Wang Lin's Karma domain |

#### 22.9.8 Ruang / Space (900–912)

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 900 | `space_blade` | **Bilah Ruang** / Space Blade | GC · Misterius-R · SERANG | DAO, COMP 10 | BEAM(20→48, hw .6, pierce) > DMG 1.5 pierce · qi 22 · cd 4s | a thin black crack LASER (black glass) with white END_ROD edges, REVERSE_PORTAL; ♪ amethyst break 1.8 + enderman teleport 2 | TRENCH 16×1×2 | Y | Martial Peak ch. 1139 · Space Blade, Yang Kai's self-created space art: tiny space cracks pushed forward like a wind blade |
| 901 | `immortal_binding_rope` | **Tali Pengikat Abadi** / Immortal-Binding Rope | GC · Misterius-M · KENDALI | BOOK loyalty | ST 2 (window 60t): st1 TARGET(≤20→40) + CHAIN 2→6 within 4 > BIND root 80t (boss 30t) + DMG .5; st2 PULL 6 b toward the caster · qi 28 · cd 10s | rope segments (lead GIANT_ITEMs ≤3) coiling the targets, a gold DUST line; ♪ armor equip chain | – | Y | Investiture of the Gods ch. 53–54 · 捆仙绳 Immortal-Binding Rope of Ju Liusun, stolen by Tu Xingsun |
| 902 | `qiankun_ring` | **Gelang Qiankun** / Qiankun Ring | NS · Misterius-T · SERANG | COMP 30; BOOK infinity | CHAIN(range 24, jumps 4→12, jump 8, falloff .85) > DMG 1.8 + KNK .8; the ring returns to the caster · qi 50 · cd 12s | a spinning golden ring of 16 DUST points with FIREWORK sparks and a TRAIL; ♪ trident throw 1.4 + bell on hits | – | Y | Investiture of the Gods ch. 12 · Nezha's golden 乾坤圈 Qiankun Ring, which kills the Dragon King's yaksha and third son |
| 903 | `gravitational_space` | **Ruang Gravitasi** / Gravitational Space | DT · Bumi-R · KENDALI | COMP 30; BOOK silk_touch | DOMAIN(r 10→24, h12, 240→360t, pulse 20) > DMG .3 + EFF slowness IV 40t + ATTR jump −100% 40t + ATTR flying_speed −100% 40t + ATTR fall_damage ×2 40t + SLAM .3 · qi 110 · cd 40s | an inward-falling distortion (REVERSE_PORTAL + dark-purple DUST raining down), ground CRACKS; SHK .1; ♪ beacon ambient .5 + anvil land .5 | DISC r6 d1 nat (outside the caster safe radius) | Y | Coiling Dragon (Books 15–16) · Gravitational Space, a high profound mystery of the Laws of the Earth that Linley struggles to fuse |
| 904 | `moon_blade` | **Bilah Rembulan** / Moon Blade | DT · Bumi-M · AREA | COMP 60; PAV | 3 FRONTs fanned −15° / 0° / +15° (L 32→64, w4, h3, v4, ignoreBlocks) > DMG 2 pierce (≤2 fronts per target) · qi 100 · cd 18s | three thin white-glass crescents (CRESCENT_FLY + LASER planes) with END_ROD arcs and a black REVERSE_PORTAL core; ♪ amethyst resonate 1.6 + illusioner cast | TRENCH 24×1×2 ×3 | Y | Martial Peak · the Moon Blade, Yang Kai's improved Space Blade |
| 905 | `purple_gold_gourd` | **Labu Merah Ungu-Emas** / Purple-Gold Red Gourd | DU · Bumi-R · SERANG | BOOK piercing; PAV | TARGET(≤24) "the name is called" (shown 20t); @20 TARGET > EXEC maxHealthBelow 10×P (non-boss), else DMG 4 (boss DMG 3 + EFF slowness IV 80t) · qi 160 · cd 30s | a red gourd silhouette (DUST red + gold) opening above the caster, the target's name as a small TEXT GLYPH, its colours spiralling into the mouth (VORTEX + MOTES_TO); ♪ chorus fruit .6 + bottle fill dragon breath + shulker close | – | N | Journey to the West ch. 32–35 · 紫金红葫芦, the Golden and Silver Horn Kings' red gourd, sucks in whoever answers when it calls their name; Wukong later traps Silver Horn in it |
| 906 | `heaven_turning_seal` | **Segel Pembalik Langit** / Heaven-Turning Seal | DU · Bumi-T · AREA | PAV; FEAT kills_with(SPACE, end, 100) | ZONE fromSky 30 (aim ≤40, r 7→16, delay 20t) > DMG 3.5 + BIND root 40t (boss 10t); @20 WAVE(origin aim, r1 r+4, v2) > DMG 1 + KNK 1 · qi 220 · cd 50s | a gilded-stone seal cube GIANT_BLOCK (8→16 b) with a 番天 GLYPH on its face falling from +30, FLASH + dust SHOCK_RING; SHK .45; ♪ anvil .4 + mace heavy + explode (sound) | PIT 5/4 | Y | Investiture of the Gods ch. 46 · Guang Chengzi's 番天印 Heaven-Turning Seal smashes 19 mirrors, then strikes Jin Guang Shengmu's crown and kills her |
| 907 | `spatial_ancestral_symbol` | **Simbol Leluhur Ruang** / Spatial Ancestral Symbol | IA T1 · Langit-R · KENDALI ★ | COMP 70; INH(SPACE) | `space_fold`: FIELD(aim ≤32, r 16→28, h12, 200t, pulse 20) > DMG .5 pierce; @0 moves ≤32 non-boss monsters to safe spots within 3 of the centre (once); ST 8 (window 200t): free BLINK AIM inside the field (≥20t apart) · qi 280 · cd 90s | a folding cube grid (REVERSE_PORTAL + purple DUST lines), a purple 空 GLYPH; SHK .2; ♪ portal trigger (short) + respawn anchor charge | SPHERE 6 (centre, when the fold closes) | N | Martial Universe ch. 1209 · the Spatial Ancestral Symbol, one of the eight Ancestral Symbols, held by Zhou Tong |
| 908 | `yellow_brow_bag` | **Kantong Benih Alis Kuning** / Yellow Brows' Seed Bag and Golden Cymbals | IA T4 · Langit-M · KENDALI | COMP 75; PAV | @0–60 ALL(aim ≤24, r 20→32, max 32) > PULL 1 toward the bag (non-boss); @60 BIND suspend 100t at the mouth + DOT qi .5/s 5s (bosses: BIND root 25t under the cymbals + the same DOT); @160 RECALL > KNK 2 + DMG 3 · qi 350 · cd 110s | a cloth bag (bundle GIANT_ITEM, scale 10) with a suction VORTEX, gold cymbal discs (flat gold GIANT_BLOCKs ≤2) closing over bosses; SHK .2; ♪ bundle insert + bell resonate | – | N | Journey to the West ch. 65–66 · the Yellow-Browed King, Maitreya's page boy, traps Wukong under stolen golden cymbals and swallows the heavenly helpers in his cloth bag until Maitreya subdues him |
| 909 | `heavenly_restriction` | **Larangan Langit Berlapis** / Layered Heavenly Restriction | IA T7 · Langit-T · PAMUNGKAS | COMP 85; FEAT casts_with(SPACE, end, 100) | FIELD square (aim ≤32, half-side 10→24, walls, h12, 160t, pulse 20) > DMG .6 + ATTR movement −100% 40t + EFF weakness III 40t; RAMP +15% per pulse (max 8); @160 ALL(field, max 32) > DMG 6 (the cube collapses) · qi 450 · cd 150s | rune walls of purple glyphs (vertical RUNE_CIRCLEs + DUST #9040FF), one layer closing each second (SHELL), purple restriction-flag GIANT_ITEMs at the corners (≤4); SHK .1 per layer, .7 on the collapse; ♪ respawn anchor charge + beacon select | PIT 6/4 (collapse) | N | Terinspirasi dari Wang Lin's restrictions (禁制) and his Restriction Flag (Renegade Immortal ch. 207, 233, 291); the layered cube and its name are game design |
| 910 | `void_mirror` | **Cermin Hampa** / Void Mirror | TI · Abadi-R · PAMUNGKAS | FEAT first_kill(ENDER_DRAGON, SPACE); INH(SPACE) | ST 2 (window 200t): st1 SELF 200t DR .5 FRONT + PROJECTILE_GUARD discard+return (arc 120°, r4) + COUNTER(200t, store frontal melee ×2, cap 6); st2 or @200: BEAM(64, hw 3, pierce, ignoreBlocks) > DMG 10 + the COUNTER release · qiPct 50 (min 600) · cd 300s | an 8-b tinted-glass mirror GIANT_BLOCK with a copper rim and a swirling void face (PORTAL) before the caster, the beam as a black-glass LASER (128 b visual); SHK .7; ♪ portal ambient + warden boom | TRENCH 48×3×3 (from 6 b ahead) | N | Shrouded the Heavens · 虚空镜 Void Mirror, the Void Great Emperor's imperial weapon, kept by the Ji family |
| 911 | `time_spirit_domain` | **Wilayah Roh Waktu** / Time Spirit Domain | Dewa · Dewa-R · PAMUNGKAS ★ | GATE I (prereq `void_mirror` L3) | `time_bank`: DOMAIN(r 24→32, h16, 160t, pulse 20) > BIND stun 160t ("time stops"; boss: root 40t + EFF slowness IV 160t); damage dealt to stopped targets is banked and released ×1.5 at @160; SELF CD_RATE ×2 160t (within the floor, never PAMUNGKAS); @160 ALL(domain, max 64) > DMG 10 + the bank · qiPct 75 · cd 600s | a 时 clock-face GLYPH (scale 24) over the domain, grey-blue VIGNETTE, dust hanging still in the air at Maximal, FLASH + SONIC_BOOM at the resume; SHK 1.0 at the resume; ♪ note-block tick-tock + warden boom at the resume | SCATTER r2 ×≤12 (stopped monsters that die at the resume) | N | A Record of a Mortal's Journey · 时间灵域 Time Spirit Domain, gained at the Golden Immortal stage; Han Li later becomes the Time Dao Ancestor |
| 912 | `stellar_black_hole` | **Lubang Hitam** / Black Hole | Dewa · Dewa-M · PAMUNGKAS | GATE II (prereq `void_mirror` L3; Puncak: `gravitational_space` L4, `time_spirit_domain` L4) | FIELD(aim ≤48, r 32→40, h16, 120t, pulse 10) > PULL .35 (never-moved list; the Dragon is only damaged) + DMG .5 (×5 within 3 of the core); @120 NOVA(r16) > DMG 18; @120 ALL(field, max 64) > DMG 4 · qiPct 90 · cd 900s | a black core (black-concrete GIANT_BLOCK) inside a rotating orange-white accretion PETAL_RING (n16), REVERSE_PORTAL + an orange-white VORTEX, dark SKY; SHK .5, 1.0 at the collapse; ♪ warden sonic charge, then warden boom + explode (sound) + very low end-portal spawn (playSound only) | SPHERE 10 (core, at the collapse) | Y★64 | Stellar Transformations · Black Hole, the stage after Dark Star; these post-Star stages were self-evolved and are not recorded in the original art |

#### 22.9.9 Universal (100–133, 1000–1003)

Dao-less specs (`dao = null`):
- C uses the main comprehension (22.5.1). Mastery level 3 needs Jiwa Baru Lahir and level 4 needs Penyatuan Dao (22.6).
- Kuning to Langit live in `UniversalArts` (block 100–199). The four Abadi and Dewa ultimates live in `ImmortalArts`, the Dao-less ultimate block 1000–1099 (22.2.3). Both are content module seni-5.
- Rows are ordered by realm. Book pools follow 22.8.2.
- `FORBIDDEN` rows pay with `FORBIDDEN_COST`: `setHealth(max(1, …))`, refused below 2 HP, confirmed once. They can never kill.

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Buka | Resep · qi · cd | Visual | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 100 | `ghost_shadow_track` | **Langkah Bayangan Hantu** / Ghost Shadow Perplexing Track | FE · Kuning-R · GERAK | BOOK soul_speed; PAV | CHARGES 2→4 (60t each): DASH 4→8 along the held movement keys (else the look), ground slide, i-frames 6t; NOVA(r 6→10) at the start point > UNTARGET; SELF: BUFF speed II 40t; t≥3: onEnd AFTERIMAGE NOVA(r2) > DMG .6 · qi 10 | grey-violet slide streak (SOUL + low LARGE_SMOKE), AFTERIMAGE copy (CLONE ×1, Maximal ×3); ♪ phantom flap 1.6 at .4 | – | Y | Soul Land · 鬼影迷踪 Ghost Shadow Perplexing Track, the Tang Sect speed art: in a fight the user seems to glide over the ground |
| 101 | `mysterious_jade_hand` | **Tangan Giok Misterius** / Mysterious Jade Hand | FE · Kuning-M · LINDUNG | BOOK unbreaking; PAV | SELF 30→50t: PROJECTILE_GUARD discard+return (front 120°, r3, charges 2→5; return TARGET > DMG 1) + DR .5 FRONT; GRANT_HOOK on_hurt (melee attacker in front, icd 4t) > DMG 1.2; CLNS · qi 12 · cd 6s | jade-green glowing hands (DUST #5FD39A + GLOW), CRIT sparks on each catch; ♪ amethyst 1.2 + soft anvil | – | Y | Soul Land · 玄玉手 Mysterious Jade Hand, the Tang Sect palm art: jade-hard hands that block poison and catch hidden weapons |
| 102 | `purple_demon_eye` | **Mata Iblis Ungu** / Purple Demon Eye | FE · Kuning-M · SERANG | BOOK piercing; PAV | BEAM(16→28, hw .6, pierce 3) > DMG 1.2 pierce + EFF slowness II 40t + EFF weakness I 40t; ? DAWN with SKY_OPEN ×1.5 · qi 10 · cd 3s | eyes flash violet (DUST #9B30FF), thin violet BEAM_CORE with PORTAL motes; ♪ evoker cast 1.5 + amethyst | – | Y | Soul Land · 紫极魔瞳 Purple Demon Eyes, the Tang Sect eye art trained facing the rising sun; at depth it releases a strong spiritual attack |
| 103 | `pear_blossom_needles` | **Jarum Hujan Bunga Pir** / Violent Rain Pear Blossom Needles | FE · Kuning-T · AREA | BOOK multishot; PAV | VOLLEY SIM n 9→27, FAN 25°, range 16→32, ≤4 per target > DMG .3 pierce · qi 15 · cd 6s | silver needle TRAILs with CHERRY_LEAVES; ♪ crossbow shoot ×3 at 1.8 | – | Y | Soul Land · 暴雨梨花针 Violent Rain Pear Blossom Needles, the Tang Sect hidden weapon that fires twenty-seven needles in a row |
| 104 | `sleep_insects` | **Serangga Tidur** / Sleep-Inducing Bugs | GC · Misterius-R · KENDALI | BOOK lure | CONE(8→16, 30°) > BIND sleep 60→100t (boss root 20t) + EFF blindness 40t + MARK SOUL ×2 (the caster's next hit) · qi 30 · cd 14s | drowsy gold motes drifting onto the targets (SWARM 40, GLOW + DUST #FFE680), a small "Zzz" GLYPH; ♪ bee loop .3 + cat purr | – | Y | Journey to the West ch. 5, 25, 71 · 瞌睡蟲 sleep-inducing bugs, which Wukong flicks onto guards and demons |
| 105 | `immobilization_spell` | **Mantra Pembeku Tubuh** / Immobilization Spell | GC · Misterius-M · KENDALI | BOOK binding_curse | REQUIRES_TARGET; TARGET(≤24) > DMG 1.5 + BIND stun 60→100t (boss root 15t); ALL(r4 at the target, max 0 +1/tier ≤4, PRECISE) > BIND stun 60→100t · qi 25 · cd 10s | a golden 定 GLYPH (scale 2) over each frozen head, CHAINS sparkle; ♪ chain + amethyst 1.6 | – | Y | Journey to the West ch. 5, 97 · 定身法 the immobilization magic; in ch. 5 Wukong fixes the Seven Fairies in the Peach Garden |
| 106 | `emperor_seal_jue` | **Seni Segel Kaisar** / Emperor Seal Jue | GC · Misterius-M · AREA | BOOK knockback; PAV | ST 4 (window 80t; realm gate st2 NS, st3 DT, st4 DU): st1 **Segel Pembuka Gunung** ZONE fromSky 12 (aim ≤24, r 3→6) > DMG 1.5 + BIND stun 30t; st2 WAVE(r1 8→16, v1.5) > DMG 1.2 + KNK 1.2; st3 PILLAR onTargets 6→10 (r2, delay 10t) > LCH .8, then @+30 SLAM .6 + DMG 1.5; st4 ZONE(aim ≤24, r 8→14) > DMG 2.5 + BIND stun 40t · qi 25/35/50/90 · cd 18s after | a falling stone-brick seal GIANT_BLOCK (3→6 b, gold rim), a blue SHOCK_RING splash, BLOCK_CRUMBLE at the pillars, a gold 帝 GLYPH at the finale; SHK .1→.4; ♪ anvil, splash, mace heavy, bell | PIT 2/2 (st1) · DISC r4 d2 (st3) · BOWL 6 (st4) | Y (st1–3) | Battle Through the Heavens · 帝印决 Emperor Seal Jue, the seal art Xun'er leaves Xiao Yan; its first seal is 开山印 Open Mountain Seal (donghua ep. 48). Stages 2–4 are game design and carry no canon names |
| 107 | `fiery_golden_eyes` | **Mata Api Pupil Emas** / Fiery Eyes Golden Pupils | NS · Misterius-M · INDRA | BOOK fortune | ALL(r 24→32, max 32) > STRIP_EFFECT invisibility + MARK VULNERABLE +20% (+5%/tier, ≤ +40%) 400t (per-viewer highlight through walls); SELF: BUFF night_vision 400t; ? Divine Sense active: r ×1.25 (within the cap) · qi 45 · cd 45s | gold-red eye glint (DUST #FFB000), an expanding gold SHOCK_RING scan, VIEWER_MARK reticles on marked monsters; ♪ beacon select 1.6 | – | N | Journey to the West ch. 7 · 火眼金睛: forty-nine days in Laozi's Eight Trigrams Furnace give Wukong fiery eyes and golden pupils that see through every disguise (the furnace itself is Fire's `eight_trigrams_furnace`) |
| 108 | `finger_of_death` | **Jari Kematian** / Finger of Death | NS · Misterius-T · SERANG | BOOK breach | REQUIRES_TARGET; TARGET(≤24→32) > DMG 2.2 pierce + EXEC .30 (boss .05); onKill SELF: RESTORE_QI 8% · qi 35 · cd 6s | a grey-black finger-shaped qi spear (BEAM_CORE of SQUID_INK + SOUL), a SCULK_SOUL skull wisp on a kill; ♪ wither shoot 1.6 at .4 | – | Y | Renegade Immortal ch. 543, 734 · Finger of Death, the demonic killing spell devised by Situ Nan and used by Wang Lin |
| 109 | `body_beyond_body` | **Tubuh di Luar Tubuh** / Body Beyond Body | NS · Misterius-T · PANGGIL | PAV | SUMMON clones n 3→8 (cling, one per monster within 12, 200→300t, attack every 20t) > DMG .25 · qi 60 · cd 40s | gold motes blown from the mouth, a gold LARGE_SMOKE "Berubah!" puff per clone, mini gold-fur CLONE copies at scale .4 (beyond the CLONE budget: AFTERIMAGE particles); ♪ fox squeak 2.0 | – | N | Journey to the West ch. 2 · 身外身法 Body Beyond Body: Wukong plucks hairs, chews them and shouts "Change!", and they become little monkeys that swarm the Demon of Havoc |
| 110 | `netherworld_white_tiger` | **Harimau Putih Alam Baka** / Netherworld White Tiger | NS · Misterius-T · AREA ★ | REALM + bond ≥ 50 with an own disciple of another Dao within 8 (pair) | `fusion_pair`: CHG 20t (both glow); FRONT(L 24→48, w 2→4, h3, v3) > DMG 2 + 2 U (U = the disciple's P_disciple, through `DisciplePowers`) + KNK 1 + the disciple Dao's domainPulse; ×1.5 with the heir; the disciple pays 30% of its qi and shares the cooldown · qi 70 · cd 40s | a charging BEAST TIGER in white and violet-black (the two Dao colours), an aura TRAIL bridge between master and disciple; SHK .3; ♪ ravager roar 1.4 + amethyst duet | TRENCH 16×2×2 | pair | Soul Land · 幽冥白虎 Netherworld White Tiger, the martial soul fusion skill of Dai Mubai and Zhu Zhuqing |
| 111 | `somersault_cloud` | **Awan Jungkir Balik** / Cloud Somersault | DT · Bumi-R · GERAK ★ | REALM | `cloud_travel`: stepped safe travel 64→128 along the look in 40t (rises to surface +3, stops at walls and unloaded chunks), i-frames while riding, no fall damage 100t; refused before spending when no safe exit exists · qi 60 · cd 30s | a golden cloud puff under the feet (white DUST + gold END_ROD), white TRAIL, a 360° flip pose on the client; ♪ elytra + phantom swoop | – | N | Journey to the West ch. 2 · 觔斗雲 cloud somersault: Subhuti teaches it, 108,000 li in a single somersault |
| 112 | `qilin_sacred_palace` | **Istana Suci Qilin** / Qilin Sacred Palace | DT · Bumi-R · LINDUNG | PAV; BOOK projectile_protection | FIELD(cast spot, r 4→8, outward walls, 120→200t, pulse 20) > DMG .5 + KNK 1.2 (wall contacts only, ≤1 per 20t); ALLY inside (caster, players, pets, disciples, villagers): DR .6 ALL + PROJECTILE_GUARD discard (hostile projectiles crossing the wall) · qi 120 · cd 60s | four gold pillar GIANT_BLOCKs, a crimson-gold roof RUNE_CIRCLE, gold DOME; SHK .2; ♪ ravager roar 1.3 + bell | – | Y (shields the master) | Against the Gods ch. 2008 · Qilin Sacred Palace, the Qilin God's defence, named with the Black Tortoise's Xuanji Tower as one of the two greatest defences |
| 113 | `seventy_two_transformations` | **Tujuh Puluh Dua Perubahan** / Seventy-Two Earthly Transformations | DT · Bumi-M · UBAH ★ | FEAT meditate_at(mountain peak site, 10 min) | `seventy_two`: ST 5 (window 100t) picks the form; TRANSFORM 1200→1800t, TOG 3 qi/s (−.3/tier, ≥1). **Harimau**: SCALE 1.1, speed II, strength I, GRANT_HOOK melee > DMG .6. **Elang**: SCALE .5, slow falling; recast airborne looking down = dive STRIKE(r3) > DMG 2.5. **Ikan**: water_breathing, speed II in water. **Serangga**: SCALE .25 (head-room check, safe teleport on revert), NOVA(r16) > UNTARGET at the change. **Kuil**: own movement −100%, RESISTANCE III, REGENERATION II, GRANT_HOOK on_hurt > DMG .4 · qi 50 per change · cd 10s after the form ends | a gold smoke puff and a 变 GLYPH on each change, form-coloured aura motes; ♪ illusioner mirror move | DISC r3 d1 (hawk dive only) | N | Journey to the West ch. 2, 6 · 地煞七十二变 the seventy-two earthly transformations Subhuti teaches; in ch. 6 Wukong uses them against Erlang Shen, last as a temple |
| 114 | `three_heads_six_arms` | **Tiga Kepala Enam Lengan** / Three Heads, Six Arms | DT · Bumi-M · UBAH | REALM | TRANSFORM 400→600t: GRANT_HOOK melee (icd 4t) > 2 phantom-arm hits on the target and the nearest other monster within 4 > DMG .6; BUFF attack_speed +50%; DR .5 BACK; CD_RATE ×1.25 (inside the floor) · qi 100 · cd 60s | AVATAR layer: two translucent extra heads and four arms holding ghost copies of the main-hand item; ♪ bell + illusioner cast | – | N | Journey to the West ch. 4 · 三头六臂 Wukong's three-headed, six-armed war form; Desolate Era · Ji Ning manifests three heads and six arms |
| 115 | `blood_death_world` | **Dunia Kematian Darah** / Blood Death World | DT · Bumi-T · WILAYAH | REALM | FORBIDDEN; FORBIDDEN_COST hp 20% max; DOMAIN(r 10→18, 160→240t, pulse 20) > DOT bleed .35/s (refreshed) + EFF blindness 30t + EFF slowness II 30t + DRAIN .3; every 20t ALL(domain, max 1, WEAKEST) > DMG 1.2 + EXEC .10 ("Blood Finger") · qi 90 · cd 75s | a crimson sky DOME inside the radius, falling blood drips (DUST #8A0303 + FALLING_DUST), a red RUNE_CIRCLE, red VIGNETTE for the caster; SHK .25; ♪ warden heartbeat | WITHER r10 | N | I Shall Seal the Heavens ch. 203, 447 · Meng Hao's killing moves Blood Finger and Blood Death World; in ch. 447 it is a blinding blood field |
| 116 | `bloodshadow_evasion` | **Pelarian Bayangan Darah** / Bloodshadow Evasion | DU · Bumi-R · GERAK | BOOK vanishing_curse | FORBIDDEN, CAST_ALWAYS; FORBIDDEN_COST hp 30% max (castable at 0 qi); NOVA(r16) at the start > UNTARGET; BLINK BACK 32→64 to the safest loaded spot (no monster within 8); SELF: DR .8 ALL 60t + no fall damage 100t; SELF_PENALTY 600t weakness I · qi 0 · cd 120s | the body bursts into a crimson streak (red DUST TRAIL arcing over the sky), AFTERIMAGE at the start; ♪ warden heartbeat + elytra | – | N | A Record of a Mortal's Journey · 血影遁 Bloodshadow Evasion: Han Li ignites his blood essence to flee in an instant (no distance claimed) |
| 117 | `moon_star_restoration` | **Bulan Ditarik Bintang Kembali** / Moon Star Restoration | DU · Bumi-M · LINDUNG | FEAT heart_demon_win(before DU) | FORBIDDEN; FORBIDDEN_COST hp 15% current ("the lifespan price"); COUNTER 40→60t: incoming damage is cancelled and stored (never `#kultivator:unblockable`; cap 2 × max HP; releases early at 50% max HP stored) → NOVA(r 6→14) > DMG 2 + stored × 2 + KNK 1.5 (counts against the boss cap) · qi 120 · cd 45s | a silver crescent behind the caster, END_ROD star motes drawn inward (MOTES_TO), then an outward STARFIELD SHELL; SHK .3; ♪ respawn anchor charge + deplete | DISC r4 d2 | N | Against the Gods · 月挽星回 Moon Star Restoration, a skill of the Heretic God Art's 4th realm "Rumbling Heaven": it instantly rebounds any attack at a heavy lifespan cost |
| 118 | `ji_realm` | **Alam Ji** / Ji Realm | DU · Bumi-T · AREA | FEAT first_kill(WARDEN, any, DU) | CONE(16→24, 30°) > DMG 2.4 pierce + EXEC .30 (boss .05) + EXEC maxHealthBelow 2×P (non-boss) + EFF slowness IV 60t; SELF_PENALTY 300t qi regen ×0 (the art wounds the caster's soul) · qi 160 · cd 60s | a desaturated cone (WHITE_ASH + SCULK_SOUL), white soul wisps torn out and flying to the caster (MOTES_TO); SHK .3; ♪ warden sonic charge 1.3 | – | N | Renegade Immortal ch. 243, 268 · Wang Lin's Ji Realm: each use wounds his Nascent Soul (ch. 243), and it kills (ch. 268) |
| 119 | `profound_mystery_fusion` | **Fusi Misteri Mendalam** / Fusion of Profound Mysteries | DU · Bumi-T · AREA ★ | COMP 60 (main Dao) + any second Dao ≥ 30 | `mystery_fusion`: VOLLEY SEEK 1 orb (≤32) → NOVA(r 6→12) > DMG 2.2 + both Daos' domainPulse ×2 + the pair row. 8 rows: Pedang+Petir CHAIN(jumps 4) > DMG 1 · Api+Es ×1.5 + EFF slowness III 60t · Tubuh+Ruang PULL .6 + DOT qi .3/s 3s · Kehidupan+Bayangan DRAIN .3 · Es+Ruang BIND root 80t · Pedang+Ruang FRONT(L16, w1) > DMG 1 · Api+Petir IGN 4s + BIND stun 20t · Tubuh+Kehidupan ALLY HEAL 20% (r8). Other pairs: r ×1.5 · qi 160 · cd 30s | an orb wound as a double helix in both Dao colours (TRAIL ×2), a two-colour SHELL burst; SHK .3; ♪ both Daos' cast cues | SPHERE 3 | N | Coiling Dragon Book 15 ch. 42, Book 21 ch. 10 · Linley fuses profound mysteries: the Throbbing Pulse with Gravitational Space and the Essence of the Earth, later his earth, fire, water and wind mysteries |
| 120 | `buddha_wrath_tang_lotus` | **Teratai Tang Murka Buddha** / Buddha's Fury Tang Lotus | DU · Bumi-T · AREA | PAV | VOLLEY SEEK 1 lotus (thrown ≤24) → @+10 NOVA(r 10→16) x3/4 (108 blades drawn, ≤3 hits per target) > DMG .7 + EFF poison II 100t; core NOVA(r 4→6) > DMG 2.5 · qi 140 · cd 40s | a jade-gold PETAL_RING lotus (n8) opens and radiates 108 silver blade streaks (SHARDS + TRAIL, count by quality); SHK .4; ♪ glass + bell | SPHERE 3 (only within 3 of the ground) | Y | Soul Land · 佛怒唐莲 Buddha's Fury Tang Lotus, the mechanism hidden weapon Tang San built, the hardest Tang Sect mechanism to make (not the disciples' BTTH flame lotus Teratai Api Amarah Buddha) |
| 121 | `wind_thunder_wings` | **Sayap Angin-Petir Han Li** / Han Li's Wind-Thunder Wings | IA T1 · Langit-R · GERAK | FEAT kills_with(any, thundering, breeze, 1) | CHARGES 2 (160t each): DASH 24→40 in 2t (i-frames) + PATH(w2.5) > DMG 3 + BIND stun 20t; onEnd CHAIN(from the last hit, jumps 3→6, jump 6) > DMG 1.5 · qi 110 per dash | silver-blue WINGS for 20t, an ELECTRIC_SPARK + END_ROD arc line, BOLT flickers; SHK .3; ♪ thunder (sound only) + phantom flap | TRENCH path×1×1 | Y | A Record of a Mortal's Journey · 风雷翅 Wind-Thunder Wings, the Thunder Roc wings Han Li tricked Feng Xi out of (not Lightning's `lei_zhenzi_wings`) |
| 122 | `proud_golden_body` | **Tubuh Emas Pantang Tunduk** / Proud Golden Body | IA T1 · Langit-R · LINDUNG | REALM | SELF 500t: DR HP-scaled .2 → .6 as health falls (inside the 22.14.3 caps); EMP +0% → +100% as health falls (inside F); GRANT_HOOK on_hurt (melee attacker, icd 10t) > DOT fire .4/s 3s + IGN 3s ("golden blood") · qi 150 · cd 75s | gilded skin (gold SHIELD_SHELL, full bright), dripping gold DUST; ♪ low anvil + beacon | – | Y | Martial Peak · 傲骨金身 Proud Golden Body, the body art Yang Kai gains from the black book (the low-health scaling is terinspirasi) |
| 123 | `fa_tian_xiang_di` | **Rupa Langit Wujud Bumi** / Modeling Heaven on Earth | IA T1 · Langit-R · UBAH | REALM | TRANSFORM 600→900t, TOG 5 qi/s: SCALE ×3 (×4 from TI) with head room, else ×2 as Dharma Form; BUFF strength III + REACH +6; GRANT_HOOK melee (icd 10t) > STRIKE(r6, 140°) > DMG 2; recast in the form (≥60t apart): stomp NOVA(r 6→12) > DMG 3 + LCH .6 · qi 250 + 5/s · cd 150s | AVATAR: a translucent gold giant 10→24 b with a cloud belt, SHOCK_RING dust at each stomp; SHK .6 per stomp; ♪ ravager roar .5 + mace heavy | DISC r4 d2 nat (each stomp) | N | Journey to the West ch. 3, 6 · 法天象地 Modeling Heaven on Earth, Wukong's sky-reaching giant body. The Body Dao's `cosmic_giant_form` (TI) is the avatar-only version; this one grows the caster |
| 124 | `burning_heart` | **Hati Membara** / Burning Heart | IA T1 · Langit-R · UBAH | FEAT deviation_recovered(DU) | FORBIDDEN; FORBIDDEN_COST hp 35% current + qi 20%; SELF 300t: EMP +100% (all technique damage, inside F ≤ ×3) + COST_MOD qi ×.5 + CD_RATE ×1.4 (inside the ×0.4 floor); while active the caster's KH shapes get +1 radius (within the cap); SELF_PENALTY 1200t weakness II + qi regen ×0 + technique damage ×0.7 · cd 240s | light-red qi flames (DUST #FF7070 + FLAME), the realm aura recoloured light red; ♪ warden heartbeat | – (+1 radius to own KH while active) | N | Against the Gods · Burning Heart, the Heretic God Art's 2nd realm, which turns the profound energy light red (the multiplier is game design) |
| 125 | `life_death_hex` | **Segel Hidup-Mati** / Sixth Hex: Life-Death Hexing | IA T2 · Langit-R · AREA | SCR 2 | FIELD(aim ≤32, r 10→20, 200t, pulse 20) > DOT soul .4/s (refreshed) + EFF slowness II 40t; onKill (hexed) SELF: HEAL 3% + CHAIN(r4, jumps 2) > DMG 1; @200 ALL(field) > EXEC .25 (boss DMG 4) · qi 260 · cd 75s | an ash-grey VORTEX on the ground mirrored in the sky (two rotating light-grey glass GIANT_BLOCKs, minTier 3), GLYPH 生死; SHK .4; ♪ beacon deactivate + soul escape | WITHER r10 | Y | I Shall Seal the Heavens ch. 882 · the Sixth Demon Sealing Hex, Life-Death Hexing (the vortex and the end execution are game design) |
| 126 | `ruyi_staff` | **Pukulan Tongkat Ruyi** / Ruyi Jingu Staff Smash | IA T2 · Langit-R · PAMUNGKAS | FEAT first_kill(ELDER_GUARDIAN, any, IA) | CHG 20t (the staff grows); FRONT falling along the aim from 3 ahead (L 40→64, w3, h12, v8) > DMG 9 + LCH .8; t≥7: 2 more fronts fanned ±20° > DMG 4.5 · qi 300 · cd 60s | a black LASER staff with two gold GIANT_BLOCK hoops grows from 1 to 64 b (visual up to 120 b), then topples along the line; CRACKS + dust; SHK .9; ♪ low anvil + mace heavy + distant thunder | TRENCH 48×3×4 | Y | Journey to the West ch. 3 · 如意金箍棒 Ruyi Jingu Bang of 13,500 catties from the Dragon King's treasury, which grows or shrinks at will (the extra staffs are game design) |
| 127 | `three_life_saving_hairs` | **Tiga Helai Rambut Penyelamat** / Three Life-Saving Hairs | IA T3 · Langit-R · LINDUNG | REALM | SELF: CHEAT_DEATH 1 (2 from IA T6, 3 at TI) 24000t through `DeathGuard` (22.14.6). A lethal hit → health 30%, CLNS, DR .8 40t, BLINK BACK 8 (safe), NOVA(r6) > KNK 1.5. Never against `#kultivator:unblockable`; shares the 10-minute technique cheat-death lockout · qi 300 · cd 600s | three golden hairs orbiting the head (gold END_ROD); on trigger a hair bursts into a gold monkey puff (gold DUST + limited TOTEM_OF_UNDYING); ♪ totem 1.2 | – | N | Journey to the West ch. 15, 75 · 三根救命的毫毛: Guanyin gives Wukong three life-saving hairs (ch. 15); he uses them inside the yin-yang vase (ch. 75) |
| 128 | `dragon_soul_domain` | **Wilayah Jiwa Naga** / Dragon Soul Domain | IA T3 · Langit-R · WILAYAH | FEAT first_kill(ENDER_DRAGON, any) | DOMAIN(r 12→24, 200→300t, pulse 20) > UNTARGET + DOT soul .4/s + EFF slowness III 40t + KNK .4 (away from the caster); every 60t BIND stun 20t (boss root 5t) · qi 240 · cd 90s | a serpentine DRAGON_BREATH ring, three BEAST DRAGON ribbons circling, violet MIST; ♪ dragon growl at .3 | – | N | Against the Gods ch. 316, 1064, 1099 · Dragon Soul Domain, a Dragon God mental domain that briefly stuns foes or makes them lose track |
| 129 | `myriad_cauldron` | **Kuali Induk Qi Segala Benda** / Myriad Things Mother Qi Cauldron | IA T4 · Langit-M · KENDALI | FEAT alchemy_count 81 | ZONE fromSky 24 (aim ≤40, r 8→16, delay 20t) > DMG 5 + PULL .5; @20 FIELD(same spot, walls, 80t, pulse 20) > BIND root 80t (boss 20t) + DOT qi .5/s; onKill SELF: RESTORE_QI 3% (≤15% per cast) · qi 280 · cd 75s | a bronze-green cauldron GIANT_BLOCK (8→16 b) dropping from the clouds, green-white mother-qi MIST pouring out; SHK .7 on landing; ♪ low bell + lava pop + mace heavy | PIT 6/4 | Y | Shrouded the Heavens · 万物母气鼎 Myriad Things Mother Qi Cauldron, Ye Fan's cauldron forged around the mother-qi root, with the green copper at its core |
| 130 | `xing_secret` | **Rahasia Xing** / The Xing Secret | IA T5 · Langit-M · GERAK | ENLIGHT (IA T5+) | SELF 200→300t: BUFF speed IV + attack_speed +100% + CD_RATE ×2 (inside the ×0.4 floor, never PAMUNGKAS); AURA(r 6→10, same duration, pulse 20) > EFF slowness IV 40t; every 60t BIND stun 10t · qi 200 · cd 90s | a clock-rune RUNE_CIRCLE under the feet, REVERSE_PORTAL trail, AFTERIMAGE; ♪ beacon select + ticking note-block hats that slow down | – | N | Shrouded the Heavens · 行字秘 the Xing Secret, the speed secret of the Nine Secrets (the effects are terinspirasi) |
| 131 | `karmic_hex` | **Segel Karma** / Seventh Hex: Karmic Hexing | IA T6 · Langit-M · KENDALI | SCR 3 | REQUIRES_TARGET; TARGET(≤32) > DMG 4 pierce; TARGET + ALL(r24 around it, max 12→24) > LINK .5 240t (boss share .25); when the target dies, the threads jump once to the linked monster with the most health · qi 300 · cd 60s | red-gold CHAINS from the target to each linked monster (client-refreshed), a red 因果 GLYPH on the target; ♪ chain + chime | – | Y | I Shall Seal the Heavens ch. 751, 858 · the Seventh Demon Sealing Hex, Karmic Hexing, which sees and hexes karma threads |
| 132 | `six_dao_reincarnation` | **Roda Enam Jalan Reinkarnasi** / Six Dao Reincarnations Heavenly Art | IA T7 · Langit-T · PAMUNGKAS ★ | REALM (needs 30 known arts) | `echo_wheel`: replays the last 6 distinct arts cast in the past 120s (no PAMUNGKAS, itself, specials or ECHO) at the current aim, one every 6t, at ×.6 (+.05/tier above IA T7, ≤ ×.8), free; then NOVA(r 10→20) > DMG 4. One boss cap, no mastery, no Kehancuran · qi 380 · cd 180s | a 20-b six-spoked wheel of light behind the caster (vertical RUNE_CIRCLE in six colours), each spoke flashing as its art replays; SHK .6; ♪ bell ×6 | – | N | Perfect World ch. 1091, 1120 · Six Dao Reincarnations Heavenly Art: introduced in ch. 1091, it merges Shi Hao's six precious techniques into one display in ch. 1120 |
| 133 | `heavenly_fate_finger` | **Jari Takdir Surgawi** / Heavenly Fate Finger | IA T9 · Langit-T · PAMUNGKAS | REALM; MASTERY `divine_sense` L3 | REQUIRES_TARGET; CHG 20t; TARGET(≤64, LOS or a Divine Sense glow) > DMG 12 pierce + BIND root 60t (boss 15t); onHit NOVA(r6) > DMG 3 · qi 320 · cd 90s | a colossal finger of light from a CLOUD_DISC (white stained-glass LASER, 40 b), a RUNE_CIRCLE under the target, FLASH; SHK 1.0; ♪ low beacon + bell | COLUMN r2 d16 | N | Renegade Immortal ch. 1254 · Heavenly Fate Finger, the All-Seer's spell, which Wang Lin uses after briefly fusing with the All-Seer's soul |
| 1000 | `ninth_hex` | **Segel Kesembilan** / The Ninth Hex | TI · Abadi-R · PAMUNGKAS | SCR 5; MASTERY `karmic_hex` L3 | CHAN 160t/20: DOMAIN(r 24→32, h16, pulse 20) > BIND stun 40t (boss root 10t, ≤60t per boss) + PROJECTILE_GUARD discard (whole domain); onEnd ALL(domain, max 32, STRONGEST) > DMG 9 + EFF slowness IV 100t · qiPct 50 (min 600) + 5/s · cd 300s | a darkened DOME with seal gridlines, 封 GLYPHs (scale 20) ringing the caster (Normal 4, Maximal 9), particles near frozen monsters stop moving, FLASH on release; SHK .4 → 1.0 on release; ♪ bell ×9 at .5 | DISC r12 d2 | N | I Shall Seal the Heavens ch. 1379, 1465, 1534, 1540 · the Ninth Hex, Meng Hao's own and the last of the nine Demon Sealing Hexes (the time-sealing effect is game design; the heir's Segel Gunung-Laut stays separate) |
| 1001 | `five_elements_mountain` | **Gunung Lima Unsur** / Five Elements Mountain | TI · Abadi-M · PAMUNGKAS | REALM; MASTERY `buddha_palm` L3 | ZONE fromSky 64 (aim ≤64, r 12→24, delay 40t) > DMG 14 (vs UNDEAD ×1.5) + SLAM .6; @40 PILLAR ×5 (ring r8 around the impact, r4, h20) > BIND root 120t (boss 30t) + DOT qi .8/s 6s · qiPct 55 (min 700) · cd 300s | a gold palm (3 GIANT_BLOCKs, Maximal 12) growing as it falls from a parted CLOUD_DISC; five mountain GIANT_BLOCKs (stone, gold, wood, prismarine, magma, 6×14×6) rise and sink after 120t; the 唵嘛呢叭咪吽 talisman GLYPH; SHK 1.0; ♪ bell ×3 at .5 + mace heavy | DISC r10 d3 + COLUMN r2 d8 ×5 | Y★27 | Journey to the West ch. 7 · 五行山: Wukong cannot leap out of the Buddha's palm, which becomes the Five Elements Mountain, sealed with the six-syllable talisman |
| 1002 | `tai_chi_diagram` | **Diagram Taiji** / Tai Chi Diagram | TI · Abadi-T · PAMUNGKAS | INH(universal); FEAT trib(average breakthrough quality ≥ Sempurna) | CHG 30t; FRONT pressed down from 30 b up along the aim (L 48→64, w9, h12, v8) > DMG 12 (vs UNDEAD ×1.5) + EXEC .40 (boss .05) · qiPct 55 (min 700) · cd 300s | a black-white yin-yang RUNE_CIRCLE unrolling into a bridge of light (white and black LASER bands, visual up to 176 b), WHITE_ASH rising from the targets; SHK .8; ♪ beacon activate + end portal frame | TRENCH 64×9×1 | Y★27 | Investiture of the Gods ch. 61 · 太极图 Tai Chi Diagram: "太极图殷洪绝命", Yin Hong is turned to ashes inside it |
| 1003 | `heaven_trampling` | **Langkah Penginjak Langit** / Heaven Trampling | Dewa · Dewa-R · PAMUNGKAS | GATE I (prereq `five_elements_mountain` L3) | DASH 24 vertical on nine light steps (DR .8 + i-frames 30t, no fall damage 200t); @30 ZONE(aim ≤48, r 24→32) > DMG 14 + LCH 1; @40 x3/10 WAVE(origin aim, r1 32→40, v4) > DMG 4 · qiPct 80 · cd 600s | nine step rings (SHOCK_RING) stacked in the air, a 48-b white-gold SHELL dome, SKY (CLOUD_DISC + SKY_TEAR), FLASH; SHK 1.0; ♪ warden sonic boom .5 + thunder ×3 | SPHERE 6 + DISC r16 d2 nat | Y★60 | Renegade Immortal · Heaven Trampling, the step beyond the three steps by which Wang Lin transcends (20.3.19 names the disciples' Penginjak Langit title after it); the nine steps of light are game design |

#### 22.9.10 Catalogue checks

**Totals per set and grade** (equal to the Result table at the top of 22):

| Set | Section | netId block | Kuning | Misterius | Bumi | Langit | Abadi | Dewa | New |
|---|---|---|---|---|---|---|---|---|---|
| Pedang (Sword) | 22.9.1 | 200–213 | – | 4 | 4 | 4 | 1 | 1 | 14 |
| Api (Fire) | 22.9.2 | 300–314 | – | 4 | 5 | 4 | 1 | 1 | 15 |
| Es (Ice) | 22.9.3 | 400–413 | – | 4 | 4 | 4 | 1 | 1 | 14 |
| Petir (Lightning) | 22.9.4 | 500–513 | – | 5 | 4 | 3 | 1 | 1 | 14 |
| Tubuh (Body) | 22.9.5 | 600–699 | – | 3 | 5 | 3 | 1 | 1 | 13 |
| Kehidupan (Life) | 22.9.6 | 700–799 | – | 4 | 4 | 3 | 2 | 1 | 14 |
| Bayangan & Angin (Shadow) | 22.9.7 | 800–899 | – | 2 | 3 | 3 | 1 | 2 (1 disciple-only) | 11 |
| Ruang (Space) | 22.9.8 | 900–999 | – | 3 | 4 | 3 | 1 | 2 | 13 |
| Universal | 22.9.9 | 100–133 + 1000–1003 | 4 | 7 | 10 | 13 | 3 | 1 | 38 |
| **Total** | | | **4** | **36** | **43** | **40** | **12** | **11** | **146** |

**Totals.**
- 146 new specs = 145 for the player + 1 disciple-only (`karma_domain`).
- With the 23 legacy specs that makes 169. The player can learn 168, so the counter reads "Teknik x/168".
- Jade slips: every new spec except the 11 Dewa specs (`karma_domain` included) and `netherworld_white_tiger` (`fusion_pair`). That leaves 134 new specs that can come from a slip.

**Universal set.**

| Check | Result |
|---|---|
| By realm | FE 4 · GC 3 · NS 4 · DT 5 · DU 5 · IA 13 (T1 4, T2 2, T3 2, T4 1, T5 1, T6 1, T7 1, T9 1) · TI 3 · Dewa gate 1 |
| By type | SERANG 2 · AREA 7 · KENDALI 4 · GERAK 5 · LINDUNG 5 · PANGGIL 1 · WILAYAH 2 · UBAH 4 · INDRA 1 · PAMUNGKAS 7 (= 38) |
| Grade rule | Kuning QR–FE, Misterius GC–NS, Bumi DT–DU, Langit IA with sub by layer (R T1–3, M T4–6, T T7–9), Abadi TI, Dewa gate. Every row follows it |
| Kuning | The only Kuning specs in 6c are these four universal FE arts. They carve nothing, because there is no destruction below Inti Emas |
| Specials | 5 of the 9 handlers in 22.2.7: `fusion_pair` (110), `cloud_travel` (111), `seventy_two` (113), `mystery_fusion` (119), `echo_wheel` (132) |
| FORBIDDEN | 115, 116, 117, 124. Each pays with `FORBIDDEN_COST` (never lethal, refused below 2 HP) and is confirmed once |
| Mrd | Y 16 · Y★ 3 (1001 and 1002 at ★27, 1003 at ★60) · pair 1 · N 18 |
| Audit (22.2.3) | Every row has a source and a CAST FX layer, and every PAMUNGKAS row has a SHK layer. ECHO is off for the GERAK, LINDUNG, UBAH, WILAYAH, PANGGIL and INDRA rows and for the FIELD rows 112, 125 and 129, which get `Bonus.duration(0.25)` instead. Hit sizes stay inside 22.3.1. Visuals may be larger: the staff of 126 shows 120 b and the band of 1002 shows 176 b, while each hits at most 64 |
| Budgets (22.15) | VOLLEY ≤ 27 per cast. The Tang Lotus is a 3-hit NOVA that draws 108 blades. Clones are ≤ 8 per cast. ALL is ≤ 32. Displays per cast at Normal are ≤ 8: the palm of 1001 uses 3 blocks plus 5 mountains, and 1000 draws 4 of its 9 glyphs. No server Display entity, no real projectile, no converted mob |

**Source and balance corrections applied to the Universal list.**
- **Renamed** (lore verdicts):
  - martial_soul_fusion → `netherworld_white_tiger`;
  - kylin_sacred_hall → `qilin_sacred_palace` (ch. 2008);
  - moon_reversing_stars → `moon_star_restoration` (月挽星回, realm "Rumbling Heaven");
  - thunderstorm_wings → `wind_thunder_wings` (风雷翅; the "four wings" and ch. 1955 are dropped);
  - heavenly_finger → `heavenly_fate_finger` (ch. 1254);
  - seal_the_heavens_hex → `ninth_hex`.
- **Reworded or trimmed:**

  | id | Change |
  |---|---|
  | `emperor_seal_jue` | only 帝印决 and 开山印 are claimed |
  | `sleep_insects` | ch. 5, 25, 71 |
  | `immobilization_spell` | ch. 5, 97 |
  | `fa_tian_xiang_di` | ch. 3, 6 |
  | `three_life_saving_hairs` | ch. 15, 75 |
  | `ji_realm` | ch. 243, 268; no "instant kill of equals" |
  | `dragon_soul_domain` | stun and lose track, not nightmares |
  | `tai_chi_diagram` | Yin Hong, ch. 61 |
  | `life_death_hex` | ch. 882 |
  | `bloodshadow_evasion` | no distance |
  | `burning_heart` | no 3–4× |
  | `xing_secret` | no "domain of time" |
  | `star_god_broken_shadow` | the Evil Soul claim is dropped (spec moved to Shadow) |
  | `myriad_cauldron` | the green copper is credited loosely |
  | `proud_golden_body` | the low-health scaling is terinspirasi |

- **Dropped:** `demon_fate_hex`. Canon has no tenth hex.
- **Moved out of Universal** (critic A2):

  | Destination | Specs |
  |---|---|
  | Shadow | `star_god_broken_shadow`, `billion_soul_flag`, `stygian_tiger_seal` (virtual corpses, no CONVERT) |
  | Body | `six_paths_fist`, `great_way_of_buddha` |
  | Life | `gold_devouring_beetles` |
  | Space | `fantian_seal` as `heaven_turning_seal`, `black_hole` as `stellar_black_hole`, `restriction_flag` as `heavenly_restriction` |
  | Sword | `zhuxian_formation` as `zhuxian_sword_formation` |

  Universal keeps `ghost_shadow_track`, `mysterious_jade_hand`, `somersault_cloud`, `xing_secret`, `body_beyond_body`, `life_death_hex`, `karmic_hex`, `five_elements_mountain` and `immobilization_spell`.
- **Balance fixes applied:**

  | Topic | Fix |
  |---|---|
  | Kuning | four arts moved to Kuning at FE with weak numbers (needles 9→27, ≤ 4 per target) |
  | Travel | `somersault_cloud` ≤ 128 |
  | Projectiles | PROJECTILE_GUARD discards instead of reflecting (101, 112, 1000) |
  | `myriad_cauldron` | RESTORE_QI instead of permanent qi |
  | `dragon_soul_domain` | its "flee" became UNTARGET + KNK |
  | `xing_secret` | its attack-speed debuff became a periodic STUN |
  | `burning_heart` | stays inside F ≤ ×3 |
  | Hairs | they use the shared DeathGuard |
  | Star gate | the ★9 gate of `heaven_trampling` became GATE I (the phase-7 star route remains, 22.8.8) |

---------------------------------------------------------------------------------------------------------------------

### 22.10 The 23 legacy techniques made stronger

**Rules for all 23.**
- **Saved data is untouched.** The enum ids, ordinals, `learned`, `selected`, the cooldown keys and `extra.techniques.granted_flight` (Flying Sword) stay exactly as saved. The only change is the id filter of 22.2.4. Mastery starts at 0 (no entry in `extra.teknik.m`).
- **Spec.** Each constant gets one spec: `legacy = X`, `netId = ordinal`, the grade and type below, the old qi and cooldown as base values (realm gap and mastery lower them, 22.5.4), and the `LegacyArt` handler. The old cast code stays and is called with a `CastContext`.
- **Damage.** The coefficient below × P replaces × D. At Pemurnian Qi P ≈ D, so the early game is unchanged. At Dewa Abadi Puncak the same Telapak Qi hits about 165× harder.
- **Scaled reads.** The old constants become `ctx.area(BASE, CAP)`, `ctx.duration(BASE, CAP)` and `ctx.count(BASE, CAP)` (A/D/N × mastery, 22.5.3). Where a row says `lin(b, k, c)`, the value is b + k per tier index t above the gate, capped at c, and A/D/N do not apply on top.
- **Shared rules replace the old local checks (22.3.5).**
  - The `portalNear` block-box scans become `PortalIndex.near`, checked per moved mob.
  - The 3-tick velocity locks become BIND, which uses attribute modifiers.
  - Knockback comes only from KNK/LCH/PULL/SLAM, because technique damage is now in `#minecraft:no_knockback`.
- **FX.** The old `FxType.TECHNIQUE` sub-effects keep playing. New layers come as `TechFx` with spec = ordinal, gated by visual tier (22.4.7).
- **Sources.** The 23 were made before the source rule. Their new `.source` lines therefore say "Terinspirasi dari", except Indra Ilahi, whose concept is verified.
- **ECHO.** Peak (level 4) is ECHO 0.4 only where 22.3.4 allows it. The other rows name their own level 4 bonus.

Columns follow 22.9 (Net, id, name, realm · grade · type, recipe, visual, KH, Mrd, source). The extra column "L3 / L4" gives the mastery level 3 `perfection` and level 4 `peak` bonuses (22.6).

| Net | id | Nama / English | Ranah · Tingkat · Jenis | Resep baru · qi · cd | L3 / L4 | Visual (tier) | KH | Mrd | Sumber |
|---|---|---|---|---|---|---|---|---|---|
| 0 | `cloud_step` | **Langkah Awan** / Cloud Step | QR · Kuning-R · PASIF (legacy passive, not castable) | air jumps lin: QR 1, FE 2, GC 3, DT 4, IA 5, TI 6; 6 qi per jump (gap-reduced, TI 3); no fall damage until the next landing (as today) · cd 0.5s | +1 jump / landing from ≥ 4 blocks: NOVA(r3) > DMG .3 + KNK .4 (icd 20t) | cloud puff (0); cloud-ring disc under each step (2); golden cloud wake (4) | – | N | Terinspirasi dari cloud travel in Journey to the West (ch. 2, Patriarch Subodhi teaches Sun Wukong to ride the clouds) |
| 1 | `qi_palm` | **Telapak Qi** / Qi Palm | QR · Kuning-R · SERANG | BEAM(12→24, hw 1.2→2) > DMG 1.0 + KNK .9 · qi 10 · cd 1.5s | 3 palms fanned ±12° (side palms ×.6) / ECHO .4 | white-jade palm stream (0); 4-b PALM print at the end (2); translucent giant palm phantom sliding along the beam (3) | BOWL 3 (end point) | Y | Terinspirasi dari the qi palm strikes of Dou Qi cultivators (Battle Through the Heavens) |
| 2 | `flying_sword` | **Pedang Terbang** / Flying Sword | FE · Kuning-T · GERAK (legacy special) | real flight through **FlightLease** (this technique owns the lease API, 22.2.7); speed 0.05 + 0.01 × realm, + 0.015 per tier above DU (cap 0.18); drain 2 − 0.2 × realm (min 0.5) × IA T1–3 .75 / T4–6 .5 / T7–9 .25 / TI 0 · cd 2s | +25% speed / sprint-flying ram: PATH(w1.5) > DMG .5, once per target per 10t, ≤ 4 per second, nobody moved | the sword under the feet (existing); sword trail (1); a spectral second sword orbiting the rider (3, display) | – | N | Terinspirasi dari sword riding (御剑) by the Qingyun disciples in Jade Dynasty (Zhu Xian) |
| 3 | `divine_sense` | **Indra Ilahi** / Divine Sense | NS · Misterius-M · INDRA | ALL(r 48→96, max 64, no LOS) > EFF glowing 240t→; ore scan r 12 (cap 16) spread over 4 ticks, pings 200t · qi 30 · cd 20s | glow +120t / glowing monsters also MARK VULNERABLE +10% 200t | soul wave ring (0); eye GLYPH 識 over the caster (3); faint sky ring at the radius (4) | – | N | A Record of a Mortal's Journey · spiritual sense (神识), which Han Li sends out to scan and to read jade slips (ch. 1269) |
| 4 | `buddha_palm` | **Telapak Buddha** / Buddha Palm | GC · Misterius-M · AREA | @20 ZONE(aim ≤24→48, r 5→16, fromSky 12→24) > DMG 3.0 (was 2.5 D) + SLAM 1.0 + EFF slowness II 60t · qi 45 · cd 12s | AREA +25% / three palms: EXTRA_CAST 2 ×.6, 10t apart | golden palm (0); 佛 GLYPH over the palm (2); GIANT palm phantom up to 24 b + SHOCK_RING + CRACKS, SHK .35 (3) | DISC r6 d2 (the palm print) | Y | Terinspirasi dari the Buddha's palm that Sun Wukong cannot leap out of (Journey to the West ch. 7); the full art is `five_elements_mountain`, which has this palm as its prereq |
| 5 | `dharma_form` | **Wujud Dharma** / Dharma Form | DT · Bumi-R · UBAH | TRANSFORM 600t→: SCALE DT 2, DU 2.5, IA 3, IA T5+ 3.5, TI 4 (largest that passes the head-room check, min 2; IN_WALL check on revert); strength II (IA III, TI IV), resistance I (TI II), REACH +2 × (scale − 1), EMP +15% · qi 80 · cd 60s | +100t / melee hits release a golden arc STRIKE(r4, 120°) > DMG .8 (icd 10t) | golden aura (0); AVATAR golden colossus outline at scale + 2 (3); Dewa shimmer (5) | – | N | Terinspirasi dari body-and-dharma dual cultivation (法体双修) of the Brahmic Saint True Devil Art (A Record of a Mortal's Journey) |
| 6 | `dao_domain` | **Wilayah Dao** / Dao Domain | DU · Bumi-T · WILAYAH | DOMAIN(r lin(10, 2, 24), 400t + 40t per realm above DU (cap 800), pulse 20, height ≤ 16) > the Dao pulse `DaoTechniques.domainPulse` with coefficients × P (22.10.1) · qi 120 · cd 90s | radius +20% / pulse every 10t | DOME + RUNE_CIRCLE in the Dao colour (2); orbiting SWORD_RING for Sword (3); the Dao GLYPH 剑 炎 冰 雷 体 生 影 空 in the sky (4) | – | N | Terinspirasi dari the domains (领域) of Renegade Immortal, such as Wang Lin's Life-and-Death and Karma domains (ch. 826, 851) |
| 7 | `sword_qi` | **Tebasan Qi Pedang** / Sword Qi Slash | GC · Misterius-R · SERANG | FRONT(L 16→48, w 1→3, v 2) > DMG 1.5; crescents lin: GC 1, DT 2, IA 3, TI 5, fanned 20° · qi 20 · cd 3s | +1 crescent / ECHO .4 | crescent (0); larger ARC (2); 6-b tall CRESCENT_FLY blade wave (3); SWORD_SHAPE glints (4) | TRENCH 16×1×2 per crescent | Y | Terinspirasi dari the sword light of the Azure Essence Sword Art (A Record of a Mortal's Journey) |
| 8 | `thousand_swords` | **Seribu Pedang** / Thousand Swords | NS · Misterius-T · AREA | x16/5 over 80t→160t: ALL(r 16→32, max lin(6, 2, 24), RANDOM) > DMG .6 (each sword falls 4t) · qi 90 · cd 40s | +2 swords per wave / at the end TARGET(the strongest in range) giant sword > DMG 3 | light swords (0); the caster's own sword as falling GIANT_ITEMs ≤ 8 (3); SWORD_RING in the sky (4) | SCATTER r1 ×12 | Y | Terinspirasi dari the sword formations of A Record of a Mortal's Journey (the Great Geng Sword Formation of 20.3.16) |
| 9 | `flame_palm` | **Telapak Api** / Flame Palm | GC · Misterius-R · AREA | CONE(7→20, 30°→45°) > DMG 1.0 + DOT fire .2/s 5s + IGN 6s · qi 20 · cd 3s | IGN +1s, DOT 6s / ECHO .4 (a second cone) | flame spray (0); flame lotus PETALS at the palm (2); taller flame tongues and ember rain (3) | FAN 7 30° d1 | Y | Terinspirasi dari the fire Dou techniques of Battle Through the Heavens |
| 10 | `phoenix_flame` | **Api Phoenix** / Phoenix Flame | NS · Misterius-T · SERANG | FRONT(L 24→64, w 1.8→3, v 1) > DMG 2.0 + IGN 8s; phoenixes lin: NS 1, IA 2, TI 3, fanned 20° · qi 80 · cd 35s | +1 phoenix / ECHO .4 | a vermilion long-tailed BEAST BIRD skimming low with a crimson trail (0); WINGS of 4 + 2t b (2); fading feather rain (4). Kept visibly unlike the five Fire-catalogue phoenix arts (gold, white-gold, sky-scale) | TRENCH 24×2×1 per phoenix | Y | Terinspirasi dari the phoenix fire of the World Ode of the Phoenix (Against the Gods) |
| 11 | `frost_lotus` | **Lotus Es** / Frost Lotus | GC · Misterius-R · KENDALI | NOVA(r 6→18) > DMG 1.0 + EFF slowness IV 120t + FRZ 240t · qi 20 · cd 4s | FRZ +40t / @20 WAVE(r1 = the nova radius, v1.5) ice spikes > DMG .5 (shatter) | frost bloom (0); SNOWFLAKE ring (2); PETAL_RING of 8 pale-blue glass petals (3) | RING 6/4/1 | Y | Terinspirasi dari the Frozen Cloud Arts of the Frozen Cloud Immortal Palace (Against the Gods) |
| 12 | `glacier_prison` | **Penjara Gletser** / Glacier Prison | NS · Misterius-T · KENDALI | TARGET(≤24→48, splash 5→8, splash factor 1) > DMG 1.5 + BIND root 160t (boss 40t) · qi 70 · cd 30s | BIND +40t / at release RECALL > DMG 1.0 (shatter ×1.5) | ice shell (0); packed-ice GIANT_BLOCK cube around each prisoner, ≤ 8 (2); SHARDS at release (3) | – | Y | Terinspirasi dari the ice seals of the Ice Phoenix God Investiture Canon (Against the Gods) |
| 13 | `thunder_palm` | **Telapak Petir** / Thunder Palm | GC · Misterius-R · SERANG | CHAIN(range 24→48, jumps lin(4, 1, 10), jump 6→12, delay 3, falloff 1.0) > DMG 1.0 (WET or FROZEN ×1.25) · qi 25 · cd 4s | +2 hops / ECHO .4 | palm bolt (0); BOLT branches (2); violet afterglow and ELECTRIC_SPARK motes (3) | SCATTER r1 (each hop) | Y | Terinspirasi dari Chu Feng's lightning arts (Martial God Asura) |
| 14 | `heavenly_thunder` | **Petir Surgawi** / Heavenly Thunder | NS · Misterius-T · AREA | RAIN(caster, r 16→32, impacts lin(8, 2, 24), over 40t, ir 1, targeted 1.0) > DMG 2.0 · qi 90 · cd 40s | +4 bolts / @50 TARGET(the strongest) converging bolt > DMG 4 | sky bolts (0); CLOUD_DISC over the caster (2); BOLT_STORM, SHK .25 (3) | SCATTER r2 ×12 | Y | Terinspirasi dari heavenly tribulation lightning (天劫), as in Against the Gods' Heavenly Tribulation Thunder Sword |
| 15 | `iron_body` | **Tubuh Besi** / Iron Body | GC · Misterius-R · LINDUNG | SELF 300t→: BUFF resistance II (IA III, the cap) + ATTR knockback_resistance +1 + REFLECT 30% of melee (TI 50%) · qi 25 · cd 30s | +100t / when it ends NOVA(r4) > DMG 1.0 + KNK .8 | iron sheen (0); golden SHIELD_SHELL (2); iron-plate displays orbiting, ≤ 4 (3) | – | Y | Terinspirasi dari the body tempering of the Undying Live-Forever Codex (A Will Eternal) |
| 16 | `mountain_stomp` | **Hentakan Gunung** / Mountain Stomp | NS · Misterius-T · AREA | NOVA(r 7→20) > DMG 2.0 + LCH .9 + EFF slowness II 80t; aftershocks lin(0, 1 per 3 tiers, 2): WAVE(r1 = r, v1.5) > DMG .8 · qi 60 · cd 20s | +1 aftershock / ECHO .4 | shock ring (0); CRACKS + DUST_PILLAR (2); stone-slab displays riding the ring, ≤ 8 (3); SHK .25 | DISC r7 d2 nat | Y | Terinspirasi dari the earth mystery Throbbing Pulse of the World (Coiling Dragon) |
| 17 | `spring_of_life` | **Mata Air Kehidupan** / Spring of Life | GC · Misterius-R · PULIH | SELF + ALLY r 8→16: HEAL 40% (TI 60%) + CLNS poison, wither + regen I 100t · qi 30 · cd 20s | full CLNS / regen II 200t | rising green motes (0); lotus of light PETALS (2); HEAL_RISE column (3) | – | Y | Terinspirasi dari the Eternal Spring Art (A Record of a Mortal's Journey) |
| 18 | `life_drain` | **Serap Kehidupan** / Life Drain | NS · Misterius-T · AREA | NOVA(r 8→16) > DMG 1.0 + DRAIN .5 (overheal becomes ABSORPTION ≤ 20% of max health) · qi 60 · cd 25s | DRAIN .6 / ECHO .4 plus DRAIN qi .1 | MOTES_TO the caster (0); red-green spiral (2); blood-mist dome (3) | WITHER 8 | Y | Terinspirasi dari the Great Desolation Scripture of Martial Universe, which gathers the life force around into one body (cited in 20.3.21) |
| 19 | `shadow_step` | **Langkah Bayangan** / Shadow Step | GC · Misterius-R · GERAK | BLINK BEHIND_TARGET(≤20→32; creepers refused); SELF next melee hit ×2 (IA ×3) within 100t; from DT ST 2 (window 60t): stage 2 steps behind another monster within 20 · qi 20 · cd 6s | window +20t / @10 AFTERIMAGE NOVA(r2) > DMG .5 | smoke (0); AFTERIMAGE (2); violet shadow wisps (3) | – | Y | Terinspirasi dari the Ghost Shadow Perplexing Track (Soul Land) |
| 20 | `gale` | **Badai Angin** / Gale | NS · Misterius-T · KENDALI | NOVA(r 10→20) > DMG .8 (new) + KNK .9→2.6 by closeness (× A); SELF invisibility + speed II 120t→ · qi 50 · cd 20s | SELF +40t / ECHO .4 (a second gust) | gust (0); VORTEX (2); GUST emitter ring (3) | STRIP 10 | Y | Terinspirasi dari the Yellow Wind Great King's Samadhi Divine Wind (Journey to the West ch. 21) |
| 21 | `space_blink` | **Kedipan Ruang** / Space Blink | GC · Misterius-R · GERAK | BLINK AIM(24→64; safe spot, loaded path, never into a portal) · qi 25 · cd 5s | COOLDOWN −20% / the departure point keeps a rift: @0 NOVA at the origin (r3) > DMG .5 | portal swirl (0); SHARDS at both ends (2); a fading black-glass seam (3) | – | Y | Terinspirasi dari Yang Kai's space principles (Martial Peak) |
| 22 | `space_prison` | **Penjara Ruang** / Space Prison | NS · Misterius-T · KENDALI | NOVA(r 12→24) > DMG 1.5 + BIND root 120t + PULL .3 b/t to the centre (never-moved list; anchored in place near portals) · qi 80 · cd 35s | BIND +20t / at release RECALL > DMG 1.5 (implosion) | violet lattice (0); black-glass GIANT_BLOCK cube (2; one large translucent layer); SHARDS implosion (3) | PIT 3/3 (at release) | Y | Terinspirasi dari Wang Lin's restrictions (禁制) in Renegade Immortal |

Grades of the 23: Kuning 3 (cloud_step, qi_palm, flying_sword), Misterius 18, Bumi 2 (dharma_form, dao_domain).

#### 22.10.1 Dao Domain pulses (coefficients × P, once per pulse)

| Dao | Today (× D) | 6c |
|---|---|---|
| none | DMG .4 + slowness I | unchanged, × P |
| Sword | DMG .5 | DMG .5; SWORD_RING orbit |
| Fire | IGN 3s + DMG .3 | IGN 3s + DMG .3 + DOT fire .4/s 3s (refreshed) |
| Ice | slowness III + FRZ | slowness III + FRZ; from DU every 4th pulse FRZ 80t |
| Lightning | 1 bolt DMG .8 on a random target | (1 + floor(t/2)) bolts (cap 4) × DMG .8, each on a different random target |
| Body | SELF resistance II | SELF resistance II + DMG .4 (quake) |
| Life | ALLY regen II; monsters wither I | plus DMG .3 + DRAIN 1.0 to the caster |
| Shadow | blindness; SELF invisibility | plus DMG .4 (cuts from behind) |
| Space | slowness V + pull .3 | plus DMG .3 (shear); pull through PULL with the never-moved list |

`profound_mystery_fusion` (special `mystery_fusion`) calls the same `domainPulse` for two Daos at ×2.

#### 22.10.2 Disciples' copies of the 23

`DiscipleTechniques` keeps its own legacy code and constants: Heavenly Thunder 4 bolts, Thousand Swords 3 waves × 4, the 80-tick prisons. It changes in three ways:
- it reads P_disciple (22.5.1);
- its scaled reads go through the `CastContext` that `DiscipleCaster` builds (A/D/N from the disciple's rank and stars, disciple mastery);
- the L3/L4 bonuses above apply at the disciple's mastery level.

The five non-teachable legacy techniques stay non-teachable: cloud_step, flying_sword, divine_sense, dharma_form, dao_domain.

---------------------------------------------------------------------------------------------------------------------

### 22.11 Mode Kehancuran for the player's own techniques

**Sumber / Source.** The block-changing exception and its safety are DESIGN 0 (6c "Saklar gampang") and 20.3.21. The sizes, zones and numbers are game design.

#### 22.11.1 The switch ("Saklar gampang")

- **State.** `extra.path.breaker` (boolean, saved), written only by `Kehancuran`. The default is off. A new world and a new player start off.
- **One click.**
  - On: the red seal on the Kehancuran page of the Kitab Teknik (22.12.6) sends `Actions.KEHANCURAN "self_on"`, or the player types `/kultivasi kehancuran nyala`.
  - Off: `"self_off"` / `/kultivasi kehancuran mati`.
- **Allowed when** `Kehancuran.selfAllowed(player)` returns null, which means both of these hold:
  1. `kehancuranPlayerTechniques` = true (new config, default true);
  2. the player is the owner of the integrated server (`server.isSingleplayerOwner(player.nameAndId())`, verified in 26.3 mcsrc: singleplayer and the LAN host), **or** `modeKehancuran` = true, the server permission of 20.3.21 (`/kultivasi kehancuran izin on`, op only). On a dedicated server and for LAN guests, the permission is required.

  Otherwise the click is refused with a reason in the action bar and chat (`kultivator.kehancuran_diri.why.*`). The switch can be set at any realm. It carves only from Inti Emas (22.11.2), and the page says so.
- **No oath screen.** No hold, no checkbox, no countdown. Switching on prints exactly one red chat line and plays one low heartbeat:

  > **MODE KEHANCURAN NYALA** — jurusmu kini benar-benar menghancurkan blok dunia. Batalkan dengan **/kultivasi kehancuran pulihkan** (10 menit). [Matikan] [Pulihkan]

  en_us: "DESTRUCTION MODE ON — your techniques now really destroy world blocks. Undo with /kultivasi kehancuran pulihkan (10 minutes). [Turn off] [Restore]".
  - The minutes come from `kehancuranUndoMinutes`.
  - [Matikan] and [Pulihkan] are click events that run the commands.
- **Off is one click and works at once.**
  - This player's queued self jobs are dropped. A running self job stops after the current tick's batch, and its removed blocks stay restorable.
  - Running restores continue.
  - Aqua line: "Mode Kehancuran mati — dunia aman lagi."
- **Losing the permission.**
  - `izin off` keeps today's behaviour of cancelling every job.
  - Every guest's switch then reads as off: the stored flag is kept but ignored, and the HUD tag disappears.
  - The integrated owner's switch is unaffected.
- **20.3.21 text.** Its sentence "The player himself never gets destructive techniques" is superseded by DESIGN 0 (6c) and this section. The three disciple locks of 20.3.21 are unchanged.

#### 22.11.2 Which casts destroy

The Kehancuran flag is decided once, in the snapshot (22.2.6 step 3), and holds for the whole cast. It is true only when all of these hold:
1. The switch is on and still allowed (22.11.1).
2. The caster is a player. Disciples' spec casts never carve. Their only destruction stays the three-lock Jurus Pemusnah of 20.3.21.
3. Realm ≥ Inti Emas (K > 0).
4. The spec has a `destruction` list.
5. **The caster is not sneaking when the cast starts.** Shift makes a block-safe cast of the same technique: same damage, same FX minus the CRACKS layer. This is why no technique uses Shift as an alternate mode; STAGES recasts do that (22.3.4).
6. It is not a copy or a by-product of a cast:
   - an ECHO;
   - a `six_dao_reincarnation` replay;
   - a LINK, REFLECT or COUNTER release;
   - a phantom's attack;
   - a ram, a landing ring or a hook.
7. It is not a test cast. `CastOptions.noKehancuran` is false. `/uji` casts (`showcase` or `forceVisual`) carve only while `uji teknik kehancuran nyala` is on, and still need rules 1–5.
8. The caster is not in a duel or sparring (`DuelRules.inDuel`, `Sparring`) and not breaking through or in a heart-demon trial.

The flag goes out as `TechFx` flag 1, which adds the client's CRACKS layer and `BLOCK_CRUMBLE` at the impact. Shapes are submitted once per cast per shape, at the moment the KH cell names (22.11.4). The 30 s warning, aim and confirm of 20.3.21 are **never** used for the player's own casts.

#### 22.11.3 Size: K and the block cap

| Rank | K | Blocks per cast |
|---|---|---|
| below Inti Emas | 0 (never carves) | 0 |
| Inti Emas | 0.25 | 128 |
| Jiwa Baru Lahir | 0.35 | 256 |
| Transformasi Dewa | 0.50 | 512 |
| Penyatuan Dao | 0.60 | 1,024 |
| Kenaikan Abadi Tahap L | 0.65 + 0.04 × (L − 1) (T9 0.97) | 1,536 + 192 × (L − 1) (T9 3,072) |
| Dewa Abadi, stage s | 1.2 + 0.1 × s (Puncak 1.5) | 4,096 |
| player stars (phase 7) | + 0.02 per star, ≤ 2.0 | the config cap |

- Blocks per cast ≤ `kehancuranMaxBlocksPerCast` (4,096; op range 256–16,384). Raising it raises only the Dewa Abadi and star rows.
- **Scaling.** `dim = round(base × K × M_area)` for every radius, width, length and depth of a shape. M_area is the mastery area factor, 1.00–1.40. Counts (×n) never scale.
- **Minimums** after rounding: 1 for radius, width and depth; 2 for length.
- **Caps:**
  - radius-type dims (r, ro, hw) ≤ `kehancuranMaxRadius` (12; op range 4–24);
  - lengths ≤ 4 × that (48); Dewa PAMUNGKAS lengths ≤ 6 × that (72);
  - depth ≤ 24;
  - a RING centred on the caster keeps ri ≥ `kehancuranCasterSafeRadius` + 1;
  - SCATTER ≤ 12 points per cast (a row with ×n > 12 uses the 12 densest points);
  - NINE spacing ≤ 12.
- **One block cap per cast.** A cast has at most 5 shapes. They share one block cap, split in proportion to each shape's uncut size.
- **Shown in the codex.** `TechniquePower.kScale(data)` and `TechniquePower.blockCap(data)` compute K and the cap, so the codex shows the real sizes.

**Example: `overlord_fury` BOWL 6.**
- At Transformasi Dewa (K 0.5): r 3, about 40 blocks.
- At Dewa Abadi Puncak with Puncak Jurus: 6 × 1.5 × 1.4 = 12.6, capped to r 12, about 2,400 blocks.

#### 22.11.4 Carving shapes (exact geometry)

`Shapes.Kind` keeps ordinals 0–6 (TRENCH, SPHERE, DISC, WITHER, PIT, COLUMN, NINE). Clients use the ordinals for previews, so they are never reordered. New kinds are appended:

| # | Kind |
|---|---|
| 7 | BOWL |
| 8 | RING |
| 9 | TUNNEL |
| 10 | FISSURE |
| 11 | SCATTER |
| 12 | ARC |
| 13 | FAN |
| 14 | STRIP |

`Shapes.Spec` gains two fields at the end, `inner` and `angle`. The seven existing factories keep their signatures (only `DestroyerArts` calls them). New factories: `bowl`, `ring`, `tunnel`, `fissure`, `scatter`, `arc`, `fan`, `strip`, `stripFan`.

**Definitions used below.**
- **Impact block:** the first solid block at or below the anchor, found by a read-only scan of ≤ 8 blocks. If there is none, the column surface.
- **Column surface:** the existing `Shapes.surface`, i.e. `MOTION_BLOCKING_NO_LEAVES` − 1, loaded chunks only.
- Every kind reads blocks read-only, only in loaded chunks, and never loads one.

| KH notation | Kind | Geometry at the anchor | Order |
|---|---|---|---|
| `BOWL r` | 7 | half-ellipsoid under the impact block: horizontal radius r, depth d = ceil(0.6 r); (dx² + dz²)/r² + (dy/d)² ≤ 1 for dy in [−d, 0]. Plus everything above the impact block within a sphere of r/2 (trees and walls in the blast) | centre-out by distance |
| `RING ro/ri/d` | 8 | annulus ri ≤ √(dx² + dz²) ≤ ro, d deep below each column's surface (taken within ±4 of the anchor's y). Centred on the caster unless the step names another anchor | inner edge outward |
| `TUNNEL r L` | 9 | 3D cylinder of radius r along the beam's direction, from the first block the beam hit, length L | near to far |
| `FISSURE L×w×d` | 10 | vertical slab along the horizontal aim, width w (1–3). It starts at the impact point, or 3 blocks beyond the caster safe radius for fronts and beams. Lens profile: depth at fraction f of the length = max(1, round(d × sin(π f))) below each column's surface ("the earth splits") | near to far, then top-down |
| `SCATTER r` | 11 | the runner expands it into BOWL r at the cast's impact points: rain impacts, chain hops, projectile hits, `EACH_TARGET`/`DENSEST` anchors, phantom strikes. ≤ 12 points; the block cap is split evenly | per point, centre-out |
| `ARC r° w d` | 12 | crescent trench in front of the caster: an annular sector with centreline radius r, width w, arc ° centred on the look yaw, d deep below each column's surface; inner edge ≥ caster safe radius + 1 | middle of the arc outward |
| `FAN L° d` | 13 | cone scoop from the caster along the horizontal look: sector length L, half-angle ° (the CONE's own half-angle), d deep below each column's surface, from caster safe radius + 1 | near to far |
| `STRIP r` / `STRIP fan L` | 14 | **foliage only.** Removes blocks in the new block tag `#kultivator:strippable` (leaves, flowers, short and tall grass, ferns, dead bushes, vines, glow lichen, moss carpets) from the column top down to the surface (14-block scan, as WITHER), in a disc r, or in a fan of length L with the spec's half-angle (default 35°). Never logs, soil, saplings, crops or farmland | top-down, centre-out |
| `TRENCH L×w×d` | 0 | existing: along the aim from 3 blocks beyond the caster safe radius (or from the anchor), top-down. `TRENCH path×w×d` follows the segment a DASH or BLINK travelled; "along the beam" follows the beam; "edges" follows the four edges of a square FIELD | top-down |
| `SPHERE r` | 1 | existing, centred on the impact point (air bursts) | centre-out shells |
| `DISC r d [nat]` | 2 | existing: d deep below each column's surface; `nat` = natural blocks only (the 20.3.21 list) | top-down |
| `PIT hw/d` | 4 | existing: square (2 hw + 1)², d deep | top-down |
| `COLUMN r d` | 5 | existing: radius r from the target's surface down d | top-down |
| `NINE r s` | 6 | existing: nine spheres r in a 3×3 grid with spacing s | per sphere |
| `WITHER r` | 3 | existing surface withering: grass block, podzol, mycelium and rooted dirt become coarse dirt; foliage removed; crops and farmland skipped; never a hole | top-down |

**Suffixes in the KH column.**

| Suffix | Meaning |
|---|---|
| `×n` | one copy at each of up to n anchors: corners, emitters, targets, DENSEST points, impacts |
| `(@t)` | at step tick t |
| `(st n)` | at stage n |
| "at release", "on collapse", "(at the end)" | at the step that ends the effect |
| no suffix | at the first IMPACT, or the first hit of the first damaging step |

#### 22.11.5 `Kehancuran.submitSelf` contract

```java
// kehancuran/Kehancuran.java (P6c-mesin; the only entry for player technique destruction)
public static @Nullable Component selfAllowed(ServerPlayer player);   // null = the switch may be on (22.11.1)
public static boolean selfOn(ServerPlayer player);                    // stored flag && selfAllowed == null
public static boolean setSelf(ServerPlayer player, boolean on);       // the one-click switch; prints the red line
public static SelfResult submitSelf(ServerPlayer caster, String specId, List<Shapes.Placed> shapes, int blockCap);
public enum SelfResult { QUEUED, OFF, QUEUE_FULL, ALL_PROTECTED, NOTHING, DEFERRED }
// kehancuran/Shapes.java
public record Placed(Spec spec, BlockPos anchor, Vec3 direction) {}   // direction: aim for trench/fissure/arc/fan, 3D for tunnel
```

**Caller (the runner, at the moment named in 22.11.4):**
1. Reads `TechniquePower.kScale` and `blockCap`.
2. Scales `spec.destruction()` (DShape at K = 1) by K × M_area and clamps it (22.11.3).
3. Resolves anchors: aim point, target positions, front end, beam's first block hit, path segment, DENSEST points, field corners or edges.
4. Calls `submitSelf`.

**`submitSelf` semantics:**
1. It never throws, never spends or refunds anything, and never delays the technique. The damage has already happened.
2. It re-checks `selfOn` and the realm, and returns OFF when either fails.
3. **Building.**
   - At most 2 submits are built per server tick. Others wait one tick in a queue of 8 (DEFERRED), and a submit older than 20 ticks is dropped.
   - Each build calls `Shapes.build` for every placed shape (pure geometry plus read-only surface lookups), merges the results, removes duplicates, keeps each shape's order from 22.11.4 and cuts to `blockCap` in proportion.
   - A build reads at most 4 × blockCap blocks.
4. **Filtering.** Protected-zone and never-touched positions (22.11.6) are dropped once at submit. They are checked again for every batch by `WorldBreaker`, now including the caster column.
5. **Results.**
   - Queue full: QUEUE_FULL plus the action-bar line "Bumi masih runtuh — kehancuran dilewati" (at most once per 5 s).
   - Every position protected: ALL_PROTECTED plus "Zona terlindung — tanpa kehancuran" (throttled the same way).
   - Nothing solid to remove: NOTHING, silently.
   - A self cast is never refused, unlike the disciple orders of 20.3.21.
6. **Otherwise:**
   - one `WorldBreaker.Job` (owner = the caster's UUID, `self = true`, label `self:<specId>`, the caster's UUID for the moving safe column);
   - one undo entry;
   - one `KehancuranLog` line;
   - the KEHANCURAN impact FX;
   - advancement `world_breaker_self` on the first one.

#### 22.11.6 Protected zones and never-touched blocks

Positions inside a zone are skipped, so a cast still carves the rest of its shape.

| Zone | Size |
|---|---|
| any Sect Hall stele, active or not | `kehancuranHallSafeRadius` 64, horizontal |
| any **other** player | `kehancuranPlayerSafeRadius` 24, re-checked every batch |
| **the caster: a small safe column** | the caster is exempt from the 24-block player zone. Instead, a vertical cylinder of `kehancuranCasterSafeRadius` (new, 4; range 3–8), at full world height, around the caster's position at the cast **and** at every batch. The ground under and beside the caster never goes, even while they move |
| respawn points ("home") | `kehancuranHomeSafeRadius` (new, 24; range 0–64) around every known respawn position of every player. SavedData `kultivator_homes` is updated on join, on respawn and when a spawn point is set, through `ServerPlayer.getRespawnConfig()` (verified in 26.3). A LAN host therefore never wrecks an offline guest's bed base |
| world spawn | the server's spawn protection, at least 16 |
| villagers, iron golems, protective formation lodestones | 32 |
| disciples, tamed pets, spirit beasts | 6 |
| the End around (0, 0) | 160 |
| phase-6b sect sites | the `ProtectedZones` zone provider (21.24) |
| unloaded chunks | dropped, never loaded |

**Never touched** (20.3.21, unchanged):
- bedrock and anything with hardness < 0;
- every block with a block entity;
- portal blocks and frames, obsidian, crying obsidian, reinforced deepslate, lodestone, respawn anchor, the stele;
- fluids and waterlogged blocks;
- `#kultivator:indestructible`;
- any position next to a fluid, a block entity or an unloaded chunk.

Shape-specific limits on top of that: STRIP removes only `#kultivator:strippable`; WITHER never makes holes and skips crops and farmland; `nat` shapes remove only natural ground.

#### 22.11.7 Queue, budgets, undo and log

- **Queue.**
  - `WorldBreaker.MAX_JOBS` goes from 3 to 8 total, with one running job at a time (as today).
  - Per owner, self and disciple jobs together: at most 1 running or queued, plus 1 more queued.
- **Rate.**
  - 48 blocks per tick shared by all jobs (`kehancuranBlocksPerTick`).
  - The flags of 20.3.21 (2 | 16 | 32 | 512), so there are no neighbour cascades.
  - Jobs pause while the average tick is above `kehancuranMsptPause` (45 ms).
  - A 4,096-block crater takes about 4.3 s at full rate.
- **Drops.** `kehancuranDropsPct` (0 by default); no item entities at 0%.
- **Undo ("Pulihkan").**
  - Each self cast is one undo entry, kept for `kehancuranUndoMinutes` (10).
  - `/kultivasi kehancuran pulihkan` or the Pulihkan button restores all the player's undoable entries (self and disciple), newest first.
  - Restores run at the same rate and flags, only where the position is still air, and only for 0%-drop casts.
  - The server keeps at most 32 undo entries. The oldest is released first, with a chat note to its owner.
  - The buffer is lost on a server restart. The Kehancuran page states this.
- **Log.** The Catatan tablet and the server log get `[Kultivator] Kehancuran: <player> self:<spec> <dim> x y z → N blocks`.
- **Client FX.**
  - The existing KEHANCURAN dust and impact FX per batch, plus the restore shimmer.
  - The CRACKS layer through `TechFx` flag 1.
  - Nothing is drawn for positions that were skipped.

---------------------------------------------------------------------------------------------------------------------

### 22.12 UI: Kitab Teknik, loadout and keys

All screens use the jade scroll of DESIGN 5b: own widgets in `client/codex/`, talisman buttons, jade coins and vertical tablets. They fit 320×240 GUI px, and the UI sounds are the amethyst and bamboo chimes. Nothing in this section uses Butter Bot's look.

#### 22.12.1 Opening

| Way | Detail |
|---|---|
| key `key.kultivator.codex` | default `-` (minus); free in vanilla, Butter Bot and Iris |
| info screen (key `'`) | the Techniques tablet (`TechniquesPage`) becomes a launcher card "Buka Kitab Teknik" with the counter |
| command | `/kultivasi teknik kitab [id]` |
| server | `Payloads.OpenScreen.CODEX = 6` (5 is phase 6b's `SECT_TEMPLATE`); arg = netId to focus, or −1 |

The scroll unrolls in 0.25 s. **Left tablets:** Kodeks · Cincin Segel · Kehancuran (a red tablet) · Bantuan. There is no passive tab, because type PASIF is reserved (22.2.7).

#### 22.12.2 Kodeks page: filters

Each row is 13–14 GUI px.

| Row | Contents |
|---|---|
| A | ink search field (`InkSearchField`, 22.12.3) · counter "Dikuasai 37/168" · sort talisman (Tingkat / Ranah / Nama / Dao / Penguasaan) |
| B | **Dao** coins (11 px): Semua, Universal and the 8 Dao trigrams · **grade** coins: 6 ribbon colours (Kuning ochre, Misterius violet, Bumi bronze, Langit azure, Abadi white-gold, Dewa rainbow) |
| C | **Ranah** talisman, cycling: Semua / "Bisa kurapal" (castable now) / each realm from Inti Emas to Dewa Abadi / "Gerbang Dewa" · **Jenis** talisman, cycling Semua plus the 11 types (PASIF hidden) · **state** chips: Semua / Dikuasai (learned) / Bisa Dipelajari (every gate met, not learned) / Terkunci (locked) · a "Pamungkas" toggle |

- Filters combine with AND.
- The last filters and sort are remembered client-side in `kultivator-client.json`.
- The disciple-only spec never shows in the player's codex.

**Grid.**
- Seal cards are 28–40 px wide and virtualized: only visible rows are drawn.
- When sorted by grade, group headers show "Tingkat Bumi · 18".
- **A card shows:**
  - the glyph: Dao trigram plus type mark;
  - a grade ribbon corner (Dewa: a slow rainbow shimmer, still under "Kurangi gerak");
  - 4 mastery beads;
  - a lock overlay;
  - a cooldown sweep;
  - the loadout slot number;
  - a small red crack mark when the spec has KH shapes;
  - a "BARU" badge (a client-side "seen" set in `kultivator-client.json`).
- **Hover tooltip:** id_id name, en_us name, grade-sub and type, the Sumber line, scaled qi and cooldown, and the lock reason.

#### 22.12.3 Search

- It matches the id_id name, the en_us name, the source work and named technique (`.source`), the Dao name, the type name and the id.
- Case- and diacritic-insensitive. Tokens are ANDed, with prefix matching. "pedang langit" finds Seni Pedang Hukuman Langit; "against" finds every Against the Gods art.
- It filters live while typing, over ≤ 169 entries. The search text is cached per spec on first open, so typing never allocates per frame.

#### 22.12.4 Detail slip (bamboo slip)

It docks on the right (150 px) when the screen is at least 420 GUI px wide. Otherwise it opens as an overlay on click. It contains:
1. A large glyph, the name (id_id) and the en_us name line.
2. Chips: Dao, grade-sub, type, realm gate (layer for Kenaikan Abadi), "Gerbang Dewa I/II".
3. **"Sumber: <work> · <named technique>"** from `.source`. A "Terinspirasi" line is shown in italic grey ink.
4. The description (`.desc`, coefficients) and the optional `.lore` line.
5. **Live numbers** from `TechniquePower` on the synced data, computed by the same functions as the server:
   - "Kerusakan 2,4× → 20.160";
   - area, duration and count after A/D/N and mastery;
   - qi and cooldown after mastery and realm gap;
   - the boss cap % for this grade at the current rank;
   - while Kehancuran is on and the spec carves: "Kawah: BOWL r 6 · ≤ 512 blok".
   - Under the numbers: "Di arena: kekuatan lama" (22.14.5).
6. **Mastery bar:** level name, points / next threshold, the level 3 and 4 bonus text, the comprehension gate (60 / 90; universal: Jiwa Baru Lahir / Penyatuan Dao), and today's remaining hit and kill points.
7. **Acquisition checklist** (22.8), with ✓ / ✗ per line:
   - realm and Dao;
   - comprehension n;
   - the enchanted books of its pools (enchantment names);
   - jade slip;
   - pavilion grade, hall level and price;
   - inheritance theme;
   - feat progress ("Bunuh 12/30 dengan Pedang di malam bulan purnama");
   - prereq ("Formasi Pedang Zhuxian ≥ Kesempurnaan Agung");
   - Dewa gate lines.
8. **Talismans:**
   - **Pilih** (sends the existing `SELECT_TECHNIQUE` with the id);
   - **Pasang ke Cincin** (slot picker 1–12);
   - **Uji** (op + creative only): sends `Actions.TECHNIQUE "test:<id>"`, which the server re-checks and runs as `uji teknik <id>`.

#### 22.12.5 Cincin Segel (loadout)

- **Layout.** 12 sockets in a ring around a dantian, previewing the wheel. Sockets 1–4 carry a small gold numeral: they are also the quick seals.
- **Editing.**
  - Click a socket to pick from a compact filtered list, or drag & drop from the grid.
  - Right-click empties a socket.
- **"Isi otomatis"** fills the strongest castable spec per role:

  | Role | Slots |
  |---|---|
  | single target | 2 |
  | area | 3 |
  | control | 2 |
  | movement | 1 |
  | defence | 1 |
  | heal | 1 |
  | ultimate | 2 |

  Strongest = highest grade, then mastery, then coefficient.
- **"Kosongkan"** clears the ring.
- **Saved** on the server in `extra.teknik.slots` (12 ids, "" = empty), through `Actions.TECHNIQUE "slot:<0-11>:<id|->"`, `"autofill"` and `"clear"`. The server checks that the id is known and castable.

#### 22.12.6 Kehancuran page (the switch)

- **The switch.** One big **red seal toggle**: "MODE KEHANCURAN: MATI" / "NYALA". It is one click, with no oath, hold or checkbox (DESIGN 0). It sends `Actions.KEHANCURAN "self_on"` or `"self_off"`, and the server answers with the red chat line of 22.11.1.
- **Status lines** from `sessionExtra.teknik.kh`:
  - availability, or why not: not the world owner and no server permission; `kehancuranPlayerTechniques` is off; below Inti Emas ("aktif, tetapi baru mengukir dari Inti Emas");
  - this rank's K, radius cap and blocks per cast;
  - the protected zones, including "Lingkar aman 4 blok di sekitarmu" and "Rumah: 24 blok dari titik bangkit";
  - **"Shift saat merapal = tanpa kehancuran"**;
  - queue state.
- **Pulihkan** button with the remaining undo windows (blocks, mm:ss). It sends `"self_undo"`, the same as `/kultivasi kehancuran pulihkan`.
- **Log:** the last 6 self casts from `KehancuranLog`.
- **Look.** The blood-red scroll variant of 20.3.21, for this page only.

#### 22.12.7 Keys, the seal wheel and quick seals

| Key | Default | Action |
|---|---|---|
| `key.kultivator.cast` | G | tap: cast the selected spec. Hold ≥ 250 ms: open the Roda Segel (while "Tahan G untuk roda" is on) |
| `key.kultivator.previous` / `next` | `,` / `.` | cycle **within the loadout** (all castables while the loadout is empty), with the 1.2 s carousel of 19.2.4 |
| `key.kultivator.codex` | `-` | open the Kitab Teknik |
| `key.kultivator.wheel` | unbound | hold: open the wheel. Once bound, G casts on press again with zero delay |
| `key.kultivator.seal_1` … `seal_4` | unbound | cast loadout slot 1–4 (`CAST` with text = id) |
| `key.kultivator.info` | `'` | info screen (unchanged) |
| `key.kultivator.flying_sword` | `\` | unchanged |

- **Hold threshold.** 19.2.4 said 180 ms. It is raised to 250 ms, so a quick tap is never read as a hold. While the hold option is on, a tap casts on release, so the delay equals the tap's length. With the option off, G casts on press as today.
- **Roda Segel** (pulled in from 19.2.4):
  - shows the 12 loadout slots; while the loadout is empty, the first 12 castables by grade;
  - radius 56 GUI px (46 when the screen is under 260 px tall);
  - each seal shows its glyph, grade ribbon, scaled qi cost, a radial cooldown sweep, and grey when unaffordable;
  - the centre shows the hovered name, qi, cooldown, mastery level and one description line.
- **Wheel input.**
  - `MouseHandlerMixin` cancels `turnPlayer` and feeds the mouse deltas to the selector; scrolling steps around the ring; WASD keeps working.
  - Esc or right-click closes without choosing.
  - Pressing 1–4 while hovering a seal pins it to loadout slot 1–4 (`slot:<i>:<id>`), so quick seals are saved on the server and survive relogs.
- **Release** selects the seal (`SELECT_TECHNIQUE`). With **"Lepas = rapal"** (off by default), release casts it (`CAST`).
- **Server side.** The existing `CAST` and `SELECT_TECHNIQUE` actions now resolve spec ids through the registry. The server still runs every check of 22.2.6.

#### 22.12.8 HUD (`QiHud`)

| Element | Detail |
|---|---|
| seal tile | the selected spec: glyph, grade ribbon, cooldown sweep, 4 mastery beads |
| quick seals | four 10 px tiles with their own sweeps, when any seal key is bound or slots 1–4 are filled |
| Perisai Qi | a white-jade arc around the qi bar (pool / max) |
| lasting effects | countdown arcs for form, domain, aura, toggle and up to 2 fields, in the Dao colour |
| charge (CHG) | a filling bar under the crosshair, "Tekan lagi = lepas" from 50% |
| channel | a ring that empties; it flashes when movement or a hit is about to break it |
| STAGES / CHARGES | stage pips with the window timer; charge pips with the recharge sweep |
| lockouts | "Pamungkas 12s" during the ultimate lockout |
| Kehancuran | a red **"KEHANCURAN"** tag next to the seal tile while the switch is on and allowed |
| toasts | "Jurus baru: …", "Pencerahan Jurus", mastery level-up (jade seal, chime), "Gerbang Dewa terbuka" |

New lines are dropped first on narrow screens. F1 hides all of them.

#### 22.12.9 Settings page additions

| Setting | Values (default in bold) |
|---|---|
| Guncangan layar | Penuh / **Ringan** / Mati |
| Kilatan layar | **nyala** / mati |
| Tampilan raksasa (phantom displays) | **nyala** / mati |
| Efek pemain lain | **Penuh** / Ringan / Mati |
| Tahan G untuk roda | **nyala** / mati |
| Lepas = rapal | nyala / **mati** |
| Kualitas efek "Otomatis" (pulled in from 19.2.16) | recommended in this phase |
| Kurangi gerak | stops the Dewa shimmer and screen flashes |

The page scrolls and still fits 320×240.

#### 22.12.10 Sync and actions

| Item | Detail |
|---|---|
| `Actions.TECHNIQUE = 36` | 35 is phase 6b's `SECT_BUILD`; routed to `technique.spec.run.Codex.handleAction`. Text ops: `slot:<0-11>:<id\|->`, `autofill`, `clear`, `test:<id>` (op + creative) |
| `Actions.KEHANCURAN` | gains `self_on`, `self_off`, `self_undo` |
| `extra.teknik` | rides in the existing `SyncSelf` tag: mastery `m`, `mday`, `slots`, `feat` |
| `sessionExtra.teknik` | session only: global cooldown and lockout ends, charges, the active forms, domains and fields with end ticks, shield pool, and `kh {allowed, why, k, cap, r, queued, undo [[blocks, seconds]]}` |
| client config | `codex_seen`, `codex_sort`, `codex_filters`, `wheel_hold`, `release_cast`, `shake`, `flash`, `phantoms`, `others_fx`, `quality_auto`, `reduce_motion` in `kultivator-client.json` |

---------------------------------------------------------------------------------------------------------------------

### 22.13 Disciples

**Sumber / Source.** Disciple teaching and the 9-star self-comprehension reuse 20.3.18–20.3.20 (*BTTH*: Xiao Yan outgrows Yao Lao; *Renegade Immortal*: Ancient God stars). The rules below are game design.

#### 22.13.1 The Mrd column

| Mrd | Meaning |
|---|---|
| Y | teachable when the disciple meets the realm, Dao (or `DiscipleHooks.daoBypass`) and comprehension gates |
| N | never teachable |
| Y★n | teachable from disciple star n (20.3.19), on top of the realm gate |
| Y(½) | teachable; summons and counts at half (rounded up) |
| only | disciple-only (`DISCIPLE_ONLY`): **`karma_domain`** (Shadow, ★46), never in the player's codex or the 168 counter |
| pair | `netherworld_white_tiger` (special `fusion_pair`): the master casts it with a bonded disciple; it is never in the disciple's own list |

- The catalogue cell is final.
- **Defaults** used by `TechniqueSpec.teachable()` when a row says plain Y on a high grade:
  - Abadi specs need disciple realm Dewa Abadi;
  - Abadi PAMUNGKAS rows carry their own star gate (★18 or ★27 in the tables);
  - Dewa specs need `dewaStars` (default ★36).
- **Never teachable**, whatever the flag:
  - FORBIDDEN specs, and specs with CHEAT_DEATH or TRANSFORM SCALE > 1.5;
  - specials without `supportsDisciples` (worldwalking, the flight and cloud-travel arts, seventy-two transformations, the echo wheel);
  - clone summons (body_beyond_body, blood_clones), because disciples have Klon Ilahi.

#### 22.13.2 Learning

| Way | Rule |
|---|---|
| taught by the master | the master knows it, and the disciple meets the gates. Master qi cost by grade: Kuning 10%, Misterius 15%, Bumi 20%, Langit 30%, Abadi 40%, Dewa 50% of the master's max qi; `teachBlocker` / `teachCostFactor` still apply. Mastery starts at the master's level − 1 |
| jade slip | used on one's own disciple: the same gates, no master qi. A known art gives +25 mastery |
| heir (20.3.20) | gets every teachable spec the master knows, free, at the master's mastery level |
| self-comprehension | at each new 9-star title the master picks one eligible Dewa spec of the disciple's Dao (or universal) with `dewaStars` ≤ stars from a list |
| disciple-only | `karma_domain` is self-comprehended at ★46 by Shadow disciples |

Disciples earn mastery at half rate (`DiscipleData.extra.teknik.m`), with the same daily caps.

#### 22.13.3 Using

- **Combat slots:** `3 + floor(realm / 2) + floor(stars / 9)`, at most 10 (realm ordinal: GC 3, so 4 slots; TI 8, so 7 slots).
  - The master picks them on the disciple's Teknik tab ("Siapkan"). The default is an auto-pick by role.
  - Stored in `DiscipleData.extra.teknik.slots`.
- **`tryCastInCombat`** runs every 20 ticks and scores only the slotted specs:

  | Role | When |
  |---|---|
  | boss-kill (SERANG, PAMUNGKAS) | the highest coefficient × grade when a boss is within 24 |
  | area (AREA, placed FIELD) | ≥ 3 hostiles within the spec's radius of the target |
  | control (KENDALI) | ≥ 2 hostiles attack the master |
  | heal or shield (PULIH, LINDUNG) | the master, an allied disciple or the disciple itself is below 50% |
  | mobility (GERAK) | the target is more than 10 blocks away; only DASH toward the target or BLINK `BEHIND_TARGET` / `TO_TARGET`, never AIM blinks |
  | summon (PANGGIL) | ≥ 2 hostiles |
  | teachable WILAYAH | ≥ 3 hostiles inside its radius |
  | otherwise | the cheapest ready attack |

- PAMUNGKAS only against a boss or ≥ 4 hostiles.
- **Aim.** `DiscipleCaster` sets look = toward the target and aim = the target position, or the densest cluster for ZONE, RAIN and FIELD, so every spec works as for the player. SELF payloads hit the disciple; ALLY payloads include the master.
- **Qi.**
  - 60% of the cost from the disciple's own pool (`QI_FACTOR`);
  - `qiPct` specs take that % of the **disciple's** max qi, and `QI_FACTOR` does not apply;
  - cooldowns are stored by id in `DiscipleData`.

#### 22.13.4 Power and safety

- **Power.** P_disciple (22.5.1) × `DiscipleHooks.damageFactor`.
- **Targets.** `DiscipleTechniques.mayHit` and `provokesBlockChanges` apply, so disciples never fight the Wither, creepers, ghasts, blazes and the rest of 22.3.5 rule 8.
- **Limits.**
  - ≤ 3 active casts per disciple;
  - its phantoms count against its own 12 and the server's 48;
  - PANGGIL at half count.
- **Never Mode Kehancuran** for spec casts (22.11.2).
- **FX.** The same `TechFx` with caster = the disciple's entity id and flag 16. The visual tier comes from the disciple's rank and stars (★9+ = tier 5).
- **Arena.** In the Void Realm, duels and tournaments the arena rule applies (22.14.5).

#### 22.13.5 `netherworld_white_tiger` (special `fusion_pair`)

- **Needs:** a bonded disciple (20.3.18) within 16 blocks, not deviating or breaking through, with ≥ 30% qi. The disciple pays 50% of the cost from its own pool.
- **Cooldown.** One cooldown is shared by the master's spec and the disciple's id key.
- **Damage** uses the master's P. The disciple's `damageFactor` adds at most ×1.25.
- **API.** The spine's `DisciplePowers.pairPartner(ServerPlayer, double range)` and `DisciplePowers.spendPairQi(DiscipleEntity, double share)` (P6c-mesin fills them).

#### 22.13.6 UI

- `client/disciple/TechniqueTab` reuses the codex grid in **teach mode**: the master's known specs, the same filters and search, the lock reasons from `infoFor`, and the "Siapkan" slot picker.
- `infoFor` sends only the master's known specs (id, teachable, reason key), at most 8 KB at 168 specs.

---------------------------------------------------------------------------------------------------------------------

### 22.14 Balance

#### 22.14.1 Ordinary monsters and farming

- **Damage.** With the curve, ordinary 20–40 HP monsters die to any art from Inti Emas on. That is the requested god-level feeling.
- **XP.** Combat XP stays `maxHealth × 0.5`, so more damage gives no more XP.
- **Farming limits** are the daily caps, not damage:
  - comprehension: +0.15 × grade factor per cast that hits a hostile, ≤ 3.0 per Dao per in-game day (22.2.6);
  - mastery: points × `clamp(base cd / 10 s, 0.25, 3)`, ≤ 200 hit and kill points per technique per day, ≤ 20 for casts that hit nothing (22.6).
- **Training dummies** (22.16):
  - undying (refilled, never killed);
  - no mastery, no kill credit, no feats, no comprehension;
  - they show the raw damage before clamps.

#### 22.14.2 Boss caps

- **Bosses:**
  - Warden (500), Wither (300), Ender Dragon (200), Elder Guardian (80);
  - anything in the datapack tag `#kultivator:technique_bosses`;
  - anything with the scoreboard tag `kultivator.boss` (the inheritance trial's final guardian);
  - `kultivator.dummy_boss` (test dummies).
- **Cap.** The damage one cast deals to one boss, cumulative, is at most `min(1.0, base × grade factor) × the boss's max health`.

| Rank | Base |
|---|---|
| below Inti Emas | 0.06 |
| Inti Emas | 0.12 |
| Jiwa Baru Lahir | 0.15 |
| Transformasi Dewa | 0.18 |
| Penyatuan Dao | 0.22 |
| Kenaikan Abadi Tahap L | 0.26 + 0.03 × (L − 1) (T9 0.50) |
| Dewa Abadi, stage s | 0.70 + 0.10 × s (Puncak 1.0) |
| per player star (phase 7) / disciple star | + 0.01 |

**Grade factors:** Kuning 0.5, Misterius 0.7, Bumi 0.85, Langit 1.0, Abadi 1.2, Dewa 1.5. Disciples use their own rank and stars.

- **Resulting Warden casts** with the best grade of each rank: GC 12, NS 10, DT 7, DU 6, IA T1 4, T5 3, T9 2, Dewa Abadi Puncak 1 (22.5.2). This is never weaker than the pre-6c numbers.
- **What counts against the cap.**
  - Every follow-up counts against the cap of the cast that started it: ECHO, LINK, REFLECT, COUNTER, DOT ticks, phantom attacks, domain or field pulses, and each `six_dao_reincarnation` replay (which uses the reincarnation's single cap).
  - Each DOT tick and each pulse is also ≤ 2% of the boss's max health.
- **EXEC** works on bosses only below 5% health. `DMG%` payloads use their boss share, and it still counts in the cap.
- **Control.** BIND ×0.25, and STUN/SUSPEND/SLEEP become ROOT. Warden, Wither and the Dragon are never suspended; the Dragon is never bound.
- **Ender Dragon.**
  - never moved;
  - PRECISE hits go to the head part, area hits to the body (quartered by vanilla);
  - End crystals are never hit, because breaking one explodes.
- **Wither.**
  - Today `Targeting.isHostileTo` excludes it, because every hit arms its block-breaking.
  - The spine `WitherBossMixin` restores the previous `destroyBlocksTick` around `hurtServer` when the source is `kultivator:technique` or `kultivator:technique_pierce` (field verified in 26.3), so a technique never arms the break.
  - The player's techniques may then target it. Disciples still never fight it (20.1).
- **Warden.** UNTARGET and TAUNT erase the brain's `ATTACK_TARGET` memory, but its anger list is vanilla; this is documented as a limit.
- **`techniqueBossCaps`** (config, default true) can switch the caps off on a server.

#### 22.14.3 Damage-reduction and other stacking caps

| Stack | Cap |
|---|---|
| **damage reduction** on the caster from technique sources (DR, DODGE, technique-granted Resistance, Qilin-type ally fields) | combined multiplicatively to ≤ 80% (Dewa Abadi 85%). Heart-demon damage is reduced by ≤ 50%. Sources in `#kultivator:unblockable` (tribulation, qi_deviation, out_of_world, generic_kill) are never reduced or absorbed. Vanilla Resistance from potions and beacons stays outside this cap |
| DODGE | ≤ 50% chance; never against unblockable or heart-demon damage |
| Perisai Qi | pool ≤ 2 × max health; never absorbs unblockable damage |
| REFLECT | ≤ 100% of the hit; reflected damage goes through the choke point and the cap of the cast that granted it |
| cooldown reduction (CD_RATE, COST_MOD cd, mastery, realm gap) | combined floor ×0.4, never below 10 ticks, never on PAMUNGKAS |
| damage multiplier F (marks, EMPOWER, shatter, WET, conditions, stacks) | ≤ ×3 against monsters, ≤ ×1.5 under the arena rule |
| control on one target (BIND, FRZ) | diminishing returns in `StatusTracker`: a second control within 10 s lasts ×0.5, a third ×0.25, then 6 s of immunity. Bosses: ×0.25, then 10 s of immunity |

**Mechanism.** Fabric has no damage-modification event. The spine mixin `LivingEntityDamageMixin` puts a `@ModifyVariable` on the damage amount in `LivingEntity.actuallyHurt`, just before absorption is applied (verify the 26.3 method in mcsrc). It calls `DamageHooks.modify(entity, source, amount)` in this order:
1. unblockable passes untouched;
2. technique DR (capped);
3. Perisai Qi;
4. COUNTER store.

#### 22.14.4 Heart Demon, tribulations, protectors and trials

- **Own Heart Demon.**
  - ≤ 6% of its max health per hit (10% for PAMUNGKAS), ≤ 12% per cast;
  - no mastery and no comprehension from it;
  - the trial stays a real fight of at least 9–17 hits. Others' heart demons stay excluded.
- **Tribulations.**
  - Nothing is castable while breaking through, except against one's own heart demon.
  - Perisai Qi, DR and cheat-death never touch unblockable damage, so no technique trivialises DESIGN 3.
- **Trial-mob rule.** Damage to mobs tagged `kultivator.dao_protector` or `kultivator.trial_guard` uses legacy power × 1.5 instead of P, in all three choke points. This covers:
  - Dao Protector waves;
  - disciple breakthroughs;
  - star calamities (20.3.19);
  - promotion trials;
  - inheritance guards.
- **Inheritance trial** (22.8.5):
  - ordinary guards carry only `kultivator.trial_guard`, with no boss tag and no health factor;
  - the final guardian has 4× health, `kultivator.trial_guard` and `kultivator.boss`, so it gets the trial-mob rule and then the boss cap.

#### 22.14.5 Arenas, sparring and duels (the old linear power)

- **Per target.** When the target is a sparring partner or a `DuelRules` partner of the attacker, the damage is multiplied by the attacker's legacy / new power ratio, in all three choke points:

  | Attacker | Ratio |
  |---|---|
  | player | `D_legacy / P_player` |
  | disciple | `U_legacy / P_disciple` (before `damageFactor`, which applies as today) |

  Then F ≤ ×1.5, then the existing duel clamps.
- **Per cast.** While the caster is in a duel or sparring, every cast uses A = D = N = 1, perfection and peak bonuses are off, and the legacy specs use their pre-6c coefficients (Telapak Buddha 2.5) and skip their new payloads. A duel therefore deals exactly the pre-6c numbers.
- **On players:**
  - ≤ 15% of max health per hit **and** per cast (`DMG%` included);
  - no EXEC, no movement payloads, no SUMMON targeting, control ×0.3;
  - no Kehancuran.
- **Covered:** the tournament, Kompetisi Daftar Kuat, Void Realm phantoms (`DuelRules` bouts), the Hundred Dynasties, Tanding dengan Guru and player sparring. Every phase-6 economy stays as balanced as today, including the Void Realm star income, which is tied to floors.

#### 22.14.6 DeathGuard (spine)

Fabric already has several `ALLOW_DEATH` listeners (Tribulation, disciple hooks), and death prevention is spread over Sparring and `DuelRules`. One spine class `util/DeathGuard` registers the only player `ALLOW_DEATH` listener and asks its guards in a fixed order:

| Order | Stage | Who |
|---|---|---|
| 1 | `DUEL` | sparring and duel clamps (they end the bout instead) |
| 2 | `TRIBULATION` | the existing Tribulation rule, migrated by the spine |
| 3 | `TOTEM` | a Totem of Undying held in either hand always acts first. If Fabric's `ALLOW_DEATH` runs before vanilla's `checkTotemDeathProtection` (verify in 26.3), DeathGuard simply allows the death so the totem fires |
| 4 | `TECHNIQUE` | CHEAT_DEATH charges (`three_life_saving_hairs`, `nezha_lotus_body`), HP floors (`bodhisattva_tree`) |
| 5 | `PERK` | the layer perk Jiwa Tak Hancur of 19.2.12 (phase 7), after techniques, so its daily save is kept |

```java
public final class DeathGuard {
    public enum Stage { DUEL, TRIBULATION, TOTEM, TECHNIQUE, PERK }
    public interface Guard { boolean preventDeath(ServerPlayer player, DamageSource source, float amount); }
    public static void register(Stage stage, Guard guard);
    public static boolean techniqueLocked(ServerPlayer player);          // the shared lockout below
}
```

- **Limits of the TECHNIQUE stage.**
  - It never acts against `#kultivator:unblockable` (tribulation, qi_deviation, out_of_world, generic_kill) or during a duel.
  - One **shared 10-minute lockout** covers every technique cheat-death source.
- **When it acts:**
  - health is set to the payload's value, never above max;
  - its FX, sound and chat line play;
  - the charge is spent.

#### 22.14.7 Server tuning

| Config | Default (range) | Effect |
|---|---|---|
| `techniqueDamageMultiplier` | 1.0 (0.1–10) | a factor in P_player |
| `techniqueBossCaps` | true | the caps of 22.14.2 |
| `techniqueAreaCap` | 32 (8–48) | the radius hard cap; Dewa specs × 1.25 |

---------------------------------------------------------------------------------------------------------------------

### 22.15 Performance budgets

Laptop first: Valorant runs on the same machine. Every limit is a `public static final` or a clamped config value.

#### 22.15.1 Server

| Budget | Value |
|---|---|
| entity queries | 1 per shape evaluation |
| | WAVE and FRONT: 1 per 4 ticks, covering the next 4 ticks of travel |
| | VOLLEY SIM: 1 merged query per cast per tick |
| | VOLLEY SEEK: targets picked at launch, then 1 small validation per landing |
| | RAIN: impacts landing in the same tick share 1 query |
| | CHAIN: 1 per hop |
| | DOMAIN, FIELD and AURA: 1 per pulse (pulse ≥ 10 ticks) |
| | SUMMON: 1 per phantom attack (interval ≥ 10 ticks) |
| query boxes | radius ≤ `techniqueAreaCap` 32 (Dewa 40); DOMAIN/FIELD height ≤ 16 when r > 16; beams past 64 blocks are travelling hits in ≤ 16-block segments, ≤ 2 segments per tick |
| targets per evaluation | 32 (Dewa 64); ALL ≤ 32 (Dewa 64) |
| block reads | ≤ 1 read-only clip per beam, dash or projectile per tick; every ray, teleport and stepped travel walks `LoadedPath` first; `PortalIndex` cached per cast; **never a block-box scan** |
| active casts | ≤ 6 per player (refusal 13 of 22.2.6: "Terlalu banyak jurus aktif"), ≤ 3 per disciple, `techniqueMaxActiveCasts` 64 per server (beyond that the oldest lasting cast ends early) |
| one-of slots per caster | 1 DOMAIN (a new one replaces it), 1 AURA, 1 form (TRANSFORM), 1 TOGGLE, ≤ 2 FIELD (the oldest ends) |
| trackers | DOT/MARK ≤ 4 per target, 512 server-wide (one pass every 20 ticks); BIND ≤ 128 server-wide (release by schedule, velocity zeroed only when \|v\| > 0.01, sync on apply and release); ATTR modifiers transient |
| simulated things | phantoms ≤ 8 per cast, 12 per caster, 48 per server; projectiles ≤ 48 per cast, 256 per server (beyond that a VOLLEY falls back to SEEK) |
| teleport chains | ≥ 3 ticks between hops |
| GLOWING | ≤ 64 targets per cast |
| FX traffic | 1 `TechFx` per phase event; `MASS_HIT` above 8 hits in one evaluation; ≤ 20 events per cast per second; sent within 96 blocks (160 for PAMUNGKAS and grade ≥ Langit) |
| sounds | ≤ 4 cues per phase |
| ore scan (`divine_sense`) | radius ≤ 16, spread over 4 ticks |
| Kehancuran | 48 blocks per tick shared; per-cast caps of 22.11.3; ≤ 8 jobs; ≤ 2 shape builds per tick |
| showcase | one technique at a time; the previous cast's lasting parts end before the next |
| idle cost | zero: the runner, trackers and phantoms tick only while something is active |

**MSPT guard.** While the average tick is above `kehancuranMsptPause` (45 ms):
- RAIN, DOMAIN, FIELD and VOLLEY pulses are thinned ×0.5, and each kept pulse deals ×2, so damage per second is unchanged;
- the low-load flag (`TechFx` flag 4) is set;
- Kehancuran jobs pause (existing);
- the showcase waits.

#### 22.15.2 Client

| Budget | Minimal | Normal | Maximal |
|---|---|---|---|
| particle tokens per tick, all casts (`FxBudget` token bucket, capacity 2 ticks) | 80 | 300 | 800 |
| particles per `TechFx` event | 40 | 300 | 800 |
| phantom displays per cast | 2 (grade ≥ Langit only) | 8 | 24 |
| phantom displays alive | 8 | 32 | 96 |
| displays created per tick | 4 | 4 | 4 |
| CLONE models | 0 (AFTERIMAGE instead) | 4 | 12 |
| SWARM motes | 40 | 120 | 240 |

- **Tokens.**
  - A particle costs max(1, lifetime / 20) tokens, so it is budgeted by particle-seconds, not by count.
  - Layers are marked CORE, DETAIL or EXTRA. When tokens run short, EXTRA is dropped first, then DETAIL is scaled.
  - END_ROD and TOTEM_OF_UNDYING use ≤ 15% of a layer's tokens.
  - Never used: CAMPFIRE_SIGNAL_SMOKE, ELDER_GUARDIAN, VIBRATION.
- **Displays.**
  - Lifetime ≤ 100 ticks; forms, domains, fields and summons ≤ 600 (a Dewa domain ≤ 900).
  - Only within 64 blocks of the viewer; never a glow outline.
  - At most 2 large translucent layers at once (dome, laser, sky tear).
  - The server config `techniqueDisplays = false` makes clients skip displays.
- **Others' casts.** "Efek pemain lain": Penuh ×1, Ringan ×0.25 with no displays, Mati.
- **Low-load flag 4:** ×0.5 and no displays.
- **Post effects.** One shake at a time (the strongest wins). Flashes ≥ 10 ticks apart, alpha ≤ 0.35, × `screenEffectScale`. F1 hides HUD effects.
- **CPU.** A template builds its seeded shape once per event and spawns it over the event's ticks. No per-frame allocation. ≤ 64 template updates per tick; beyond that, the oldest EXTRA layers end.
- **`EffectQuality`.** Gains `AUTO` ("Otomatis", 19.2.16): below 40 fps → Minimal; after 10 s above 70 fps → Normal; never Maximal; hysteresis prevents flicker. It also gains `othersScale()` and `displayAllowance()` hooks for `FxBudget`.

---------------------------------------------------------------------------------------------------------------------

### 22.16 Commands

`/kultivasi` and `/cultivate` are the same root.

#### 22.16.1 Player

| Command | Effect |
|---|---|
| `teknik kitab [id]` | opens the codex (focused on the spec) |
| `teknik info <id>` | in chat: name, Sumber, live numbers, mastery, acquisition checklist |
| `teknik pilih <id>` / `teknik rapal <id>` | select / cast; the existing `technique select\|cast\|list` keep working with every spec id |
| `teknik daftar [dao <dao>\|tingkat <grade>\|jenis <type>]` | known specs with mastery and state |
| `teknik slot <1-12> <id\|kosong>` / `teknik slot isi` / `teknik slot hapus` | loadout, autofill, clear |
| `kehancuran nyala` / `kehancuran mati` | the player's own switch (22.11.1); no oath |
| `kehancuran pulihkan` / `kehancuran status` | restore / status, as today, now listing self casts too |

#### 22.16.2 Test (op, `TestCommands` → `TechniqueTests.addTestCommands(uji)`)

| Command | Effect |
|---|---|
| `uji teknik` | as today, extended: learns **every** player spec (168), resets cooldowns, fills qi |
| `uji teknik semua` | the same as `uji teknik`, plus a printed count per set and grade ("Pedang 14+2 … total 168") |
| `uji teknik <id>` | learns it and **casts it now** with `bypassGates + free + forceVisual`: no realm, Dao, gate, qi or cooldown checks, no mastery, aimed where the player looks. Prints P, the coefficient, area, duration, count, the cap that applied, and the damage dealt per target |
| `uji teknik pamer [detik] [dao <dao>\|tingkat <grade>\|dari <id>]` | **showcase**: casts every technique in turn (catalogue order, legacy first), default 4 s each (min 2, longer when the timeline needs it). Title = the Indonesian name, subtitle = the Sumber line, action bar = "n/169". `forceVisual`, no costs, no mastery, no Kehancuran unless `uji teknik kehancuran nyala`. Stops with `uji teknik pamer stop`, on death, logout or dimension change |
| `uji teknik bebas nyala\|mati` | session bypass of gates, qi and cooldowns for normal casting (to try the wheel and the loadout) |
| `uji teknik penguasaan <id\|semua> <0-4>` | sets mastery points to that level's threshold |
| `uji teknik gerbang` | puts the player at Dewa Abadi Puncak, ascended, comprehension 90 in the main Dao, prereqs at level 4, so Gerbang Dewa I and II open |
| `uji teknik kehancuran nyala\|mati` | lets `uji` and showcase casts carve (the 22.11.1 switch rules still apply) |
| `uji teknik audit` | the registry checks of 22.2.3; prints problems, or "169 spec OK · 168 untuk pemain" |
| `uji teknik murid <name\|semua>` | teaches disciples every spec they qualify for; the existing `uji murid <name> teknik` calls it |
| `uji boneka [n ≤ 6] [bos]` / `uji boneka hapus` | training dummies (below) |

**Training dummies.**
- Husks with NoAI, silent, `setCanBreakDoors(false)`, `NoGrief.TAG` + `kultivator.dummy`, persistence off, refused in `ALLOW_LOAD`.
- Health 1,024, **undying**: they are refilled and an `ALLOW_DEATH` refusal applies to the dummy tag.
- The name tag shows the last raw and final damage ("Kerusakan terakhir: 8.400 → 41").
- `bos` adds `kultivator.dummy_boss`, so the boss caps apply.
- No mastery, kill credit, feats or comprehension.
- Removed after 5 minutes, on `hapus`, or when the owner leaves.

#### 22.16.3 Debug (op, `debug teknik …`)

| Command | Effect |
|---|---|
| `mastery <id\|semua> <0-4>` / `mastery tambah <id> <poin>` | mastery |
| `cooldowns reset` | existing (`debug technique cooldowns reset` keeps working) |
| `lupakan <id\|semua>` | forget specs; `debug learn\|forget` now suggest every spec id |
| `kekuatan` | R, C, Q, S, P, A/D/N, t, tierGap, boss cap per grade, K and block cap |
| `beban` | active casts, phantoms, projectiles, DOT/BIND counts, FX events and particle tokens in the last second, Kehancuran jobs |
| `feat <kind> <n>` | sets a feat counter (route testing) |
| `murid teknik mastery <name> <id\|semua> <0-4>` | disciple mastery |

---------------------------------------------------------------------------------------------------------------------

### 22.17 Advancements and text

| Advancement | Name (id_id) | When |
|---|---|---|
| `technique_50` | Lima Puluh Jurus | know 50 specs |
| `technique_100` | Seratus Jurus | know 100 specs |
| `technique_all` | Kitab Lengkap | know all 168 player specs |
| `great_perfection` | Kesempurnaan Agung | first mastery level 3 |
| `peak_mastery` | Puncak Jurus | first mastery level 4 |
| `heaven_grade` | Jurus Tingkat Langit | first Langit spec learned |
| `immortal_grade` | Jurus Abadi | first Abadi spec learned |
| `god_grade` | Gerbang Dewa | first Dewa spec learned |
| `jade_slip` | Membaca Slip Giok | first spec learned from a jade slip |
| `inheritance` | Pewaris Warisan | win an inheritance trial |
| `world_breaker_self` | Tanganku Membelah Bumi | first crater carved by your own technique |

The existing `first_technique` and `technique_collector` stay. The new ones hang under `first_technique` and use `docs/ADVANCEMENT_TEMPLATE.json`.

**Text (`lang-src`, id_id + en_us, merged by `mergeLang`).**

| File | Contents | Owner |
|---|---|---|
| `teknik.*` | grades, sub-ranks, types, mastery level names, flows (charge, channel, stages, charges), refusals including "Terlalu banyak jurus aktif", routes and feats, Dewa gate lines, toasts, command output, death messages `death.attack.kultivator.technique_pierce` (+ `.player`) | P6c-mesin |
| `arts_legacy.*` | `.source` and `.lore` of the 23 (names and desc stay in their current files) | P6c-mesin |
| `kehancuran_diri.*` | the switch, the red line, reasons, statuses, queue and zone messages, page text, help topic `kehancuran_diri` | P6c-mesin |
| `codex.*` | every Kitab Teknik, Cincin Segel, wheel, HUD and settings string; help topics `teknik`, `penguasaan`, `kitab_teknik` | P6c-kitab |
| `arts_sword`, `arts_fire`, `arts_ice`, `arts_lightning`, `arts_body`, `arts_life` (`.*`) | name, desc, source, lore per spec | P6c-seni-A |
| `sumber.*` | `jade_slip`, `giok_warisan`, trial text, book-pool tooltips, pavilion row | P6c-seni-A |
| `arts_shadow`, `arts_space`, `arts_universal`, `arts_immortal` (`.*`) | name, desc, source, lore per spec | P6c-seni-B |

- **Keys per spec** (22.2.2): `kultivator.technique.<id>`, `.desc`, `.source` (id_id "Sumber: …", en_us "Source: …"; work titles in English in both), and an optional `.lore`. About 1,350 lines for 169 specs.
- **Help topics** (the existing `HelpPage`):

  | Topic | Covers |
  |---|---|
  | `teknik` | grades, types, the cast flow, the boss cap and arena rule in one line each |
  | `penguasaan` | levels, points, the daily caps, the comprehension gates |
  | `kitab_teknik` | filters, search, loadout, the wheel, quick seals |
  | `kehancuran_diri` | a clear red warning: off by default; one click; Shift = no carving; the safe zones (4 blocks around you, 24 around beds and players, 64 around halls); undo within 10 minutes, lost on restart |

- **Number formatting** follows the existing `Msg.number` (Indonesian "8,4 rb", English "8.4k").

---------------------------------------------------------------------------------------------------------------------

### 22.18 Modules, spine additions, file ownership and contracts

Four implementation agents work in parallel after the 6c spine. **They never edit each other's files.** Anything missing goes into the final report as a TODO. After them, the orchestrator runs one compile step (`compileJava`, `compileClientJava`, `jar --no-daemon`; never tests, `runClient` or `build`), then a fix pass. The user then runs `uji teknik audit` and the checklist of 22.19 in game.

#### 22.18.1 6c spine (orchestrator, after 6b is committed, before the four modules)

1. **Data types (complete, owned by the spine):**
   - `data/TechniqueSpec` + `Builder`, `TechniqueRegistry` (with `Sink`), `TechniqueGrade`, `TechniqueType`;
   - `technique/spec/dsl/*`: every record of 22.2.1 with final fields — `Step`, shapes, payloads, `Cond`, `Hook`, `FxLayer`, `SoundCue`, `DShape`, `Bonus`, `Route`, `Feat`;
   - `technique/spec/TechniquePower` written in full: P, A/D/N, t, qi, cooldown, mastery thresholds, boss caps, K and block caps, `finalDamage` with the arena, trial-mob, heart-demon and F rules.
2. **`data/Technique.spec()`** and the id migration in `CultivationData` / `DiscipleData`: `knows(String)`, `learn(String)`, `selectedId()`, `cooldownLeft(String, now)`, `learnedSpecs()`, and the enum overloads delegating (22.2.4).
3. **Registry init** in `Kultivator.onInitialize` only. It calls, in order: `LegacyArts` (inside `TechniqueRegistry`), `UniversalArts`, `SwordArts`, `FireArts`, `IceArts`, `LightningArts`, `BodyArts`, `LifeArts`, `ShadowArts`, `SpaceArts`, `ImmortalArts`, then `freeze()`. Each `*Arts.register(Sink)` is an empty stub, so the content modules only fill their own file.
4. **Network:**
   - `Payloads.TechFx` (22.4.1), routed to the stub `client/fx/spec/SpecFx.play`;
   - `Actions.TECHNIQUE = 36`, routed to the stub `technique.spec.run.Codex.handleAction`;
   - `Payloads.OpenScreen.CODEX = 6`;
   - `Actions.KEHANCURAN` text ops `self_on`, `self_off`, `self_undo`, routed into `Kehancuran.handleAction`.
5. **Data pack:**
   - damage type `kultivator:technique_pierce` (in `#minecraft:bypasses_armor`);
   - both technique damage types in `#minecraft:no_knockback`;
   - damage type tag `#kultivator:unblockable` (tribulation, qi_deviation, out_of_world, generic_kill);
   - entity type tag `#kultivator:technique_bosses` (warden, wither, ender_dragon, elder_guardian);
   - block tag `#kultivator:strippable`.
6. **Mixins:**
   - `WitherBossMixin` and `LivingEntityDamageMixin` (main), written in full;
   - `GameRendererShakeMixin` (HEAD or RETURN of the private `GameRenderer.bobHurt`) and `MouseHandlerMixin` (client), as stubs calling `CameraShake.apply` / `Codex.wheelInput`.
7. **Utilities (full):**
   - `util/DeathGuard` (22.14.6), with Tribulation's listener migrated;
   - `util/PortalIndex` (POI manager for nether portals, chunk block entities for end portals and gateways, cached per cast);
   - `util/LoadedPath`;
   - `util/DamageHooks` (the facade `LivingEntityDamageMixin` calls; P6c-mesin registers DR, shield and counter into it).
8. **Choke points.** `Targeting` changes: the Wither is no longer excluded for players; the calm-neutral rule takes a `precise` flag. `Techniques.hurt`, `DiscipleTechniques.hurt` and `PowerCore.hurt` call `TechniquePower.finalDamage`.
9. **`Qi.regenPerSecond`** × (1 + 0.1 × grade tier) (22.5.4).
10. **Stat** `extra.kultivasi.trib_strikes`, incremented by `Tribulation` per survived strike.
11. **Stubs with final signatures** for every contract in 22.18.3, so the modules compile alone: `TechniqueRunner`, `CastContext`, `CastOptions`, `EndReason`, `SpecialTechnique`, `FlightLease`, `Kehancuran.submitSelf/selfAllowed/selfOn/setSelf`, `DisciplePowers.pairPartner/spendPairQi`, `SpecFx`, `Codex`, `CameraShake`.
12. **`KultivatorConfig` fields** (clamped on load):

    | Field | Default (range) |
    |---|---|
    | `techniqueDamageMultiplier` | 1.0 (0.1–10) |
    | `techniqueAreaCap` | 32 (8–48) |
    | `techniqueBossCaps` | true |
    | `techniqueMaxActiveCasts` | 64 (16–128) |
    | `techniqueDisplays` | true |
    | `kehancuranPlayerTechniques` | true |
    | `kehancuranCasterSafeRadius` | 4 (3–8) |
    | `kehancuranHomeSafeRadius` | 24 (0–64) |

13. **Entry points:**
    - `CultivateCommand` → `technique.spec.run.TechniqueCommands.add(root, debug)`;
    - `TestCommands` → `TechniqueTests.addTestCommands(uji)`;
    - `KultivatorClient` → `client.fx.spec.SpecFx.init()` and `client.codex.CodexClient.init()`.

#### 22.18.2 The four modules

| Module | Owns (new) | May edit (nobody else touches these) |
|---|---|---|
| **P6c-mesin** (framework, primitives, runner, legacy, Kehancuran, disciples, commands) | `technique/spec/run/*`: `TechniqueRunner`, `CastContext` impl, `Caster` (+ `PlayerCaster`, `DiscipleCaster`), `StatusTracker`, `Phantoms`, `VirtualProjectiles`, `Fields`, `QiShield`, `Forms`, `LegacyArt`, `Mastery`, `Loadout`, `Unlocks`, `FeatTracker`, `Codex`, `TechniqueCommands`, `TechniqueTests`, `Dummies`, `Homes` (SavedData `kultivator_homes`); the advancements of 22.17 except `inheritance` and `jade_slip`; `lang-src/teknik.*`, `arts_legacy.*`, `kehancuran_diri.*` | `technique/Techniques`, `UniversalTechniques`, `DaoTechniques`, `DaoTechniqueState`, `DharmaForm`, `DaoDomain`, `DivineSense`, `CloudStep`, `FlyingSword` (+ `FlightLease` impl); `dao/Comprehension`, `dao/DaoPaths`; `kehancuran/Kehancuran`, `Shapes`, `WorldBreaker`, `ProtectedZones`, `KehancuranLog`; `disciple/DiscipleTechniques`, `DiscipleCultivation`, `DiscipleData` (teknik extra), `disciple/path/Heir`, `disciple/power/DisciplePowers` (the pair API only), `disciple/path/Bond` (read helpers only) |
| **P6c-kitab** (all client FX and UI) | `client/fx/spec/*`: `SpecFx`, the particle templates, `PhantomDisplays`, the custom renderers `AVATAR`, `CLONE`, `SWARM`, `SKY_SILHOUETTE`, `VIEWER_MARK`, `CameraShake`, `ScreenFlash`, `FxBudget`; `client/codex/*`: `CodexClient`, `CodexScreen`, `CodexGrid`, `DetailSlip`, `InkSearchField`, `LoadoutPage`, `KehancuranPage`, `SealWheel`, `QuickSeals`, `CodexHud`; the bodies of `GameRendererShakeMixin` and `MouseHandlerMixin`; `lang-src/codex.*` | `client/fx/TechniqueFx`, `DaoTechniqueFx` (legacy tier layers), `EffectQuality` (AUTO, budget hooks), `QiHud`, `Keys`, `client/screen/CultivationScreen`, `TechniquesPage` (becomes a launcher), `SettingsPage`, `HelpPage`, `ui/SealCard`, `ui/Glyphs`, `client/disciple/TechniqueTab`, `TeachCard` |
| **P6c-seni-A** (catalogue A + acquisition) | `technique/arts/SwordArts`, `FireArts`, `IceArts`, `LightningArts`, `BodyArts`, `LifeArts` (84 specs); `technique/special/EarthTravel` (`earth_travel`), `FlightForm` (`flight_form`, through `FlightLease`); `technique/source/*`: `JadeSlips`, `InheritanceTrials`, `TechniqueLoot`, `BookPools`; items `jade_slip` and `giok_warisan` (models, textures through `tools/png.py`); `heavenly_flame_seed` player use; advancements `inheritance` and `jade_slip`; `lang-src/arts_sword`, `arts_fire`, `arts_ice`, `arts_lightning`, `arts_body`, `arts_life`, `sumber` (`.*`) | `dao/TechniqueBooks` (becomes `poolFor`), `client/BookTooltips`, `sect/hall/Contribution` (the pavilion row only), module A's `LostArts` (scripture set 4 "Kitab Penyegel Iblis" only) |
| **P6c-seni-B** (catalogue B) | `technique/arts/ShadowArts`, `SpaceArts`, `UniversalArts`, `ImmortalArts` (62 specs); `technique/special/CloudTravel` (`cloud_travel`), `SeventyTwo` (`seventy_two`), `FusionPair` (`fusion_pair`), `MysteryFusion` (`mystery_fusion`), `EchoWheel` (`echo_wheel`), `SpaceFold` (`space_fold`), `TimeBank` (`time_bank`); `lang-src/arts_shadow`, `arts_space`, `arts_universal`, `arts_immortal` (`.*`) | nothing outside its own files |

**Rules for the two catalogue modules.**
- They write only builder DSL calls and specials. They never write client code and never edit `technique/spec/run/*`.
- A visual no template can draw becomes a TODO for P6c-kitab, never custom client code.
- They use only their own netId blocks (22.2.3). ImmortalArts uses 1000–1099.
- Every spec carries all four lang keys in both languages and a non-empty `.source` that follows 22.20.
- Before handing in, each module checks its file against the audit rules of 22.2.3 by reading it.

#### 22.18.3 Public contracts (spine stubs; the owner fills them)

```java
// spine — data and math (common; the codex calls the same functions as the server)
TechniqueRegistry.byId(String) / byNet(int) / all() / ofDao(@Nullable Dao) / ofGrade(TechniqueGrade) / legacy(Technique)
TechniqueRegistry.special(String handlerId) → @Nullable SpecialTechnique ; playerTotal() → 168
interface TechniqueRegistry.Sink { void add(TechniqueSpec spec); void special(String handlerId, SpecialTechnique handler); }
TechniquePower.power(CultivationData) / power(DiscipleData) / legacyD(CultivationData) / legacyU(DiscipleData)
TechniquePower.factors(CultivationData | DiscipleData, TechniqueSpec) → Factors(A, D, N, t, tierGap, gapR)
TechniquePower.qi(TechniqueSpec, CultivationData, int mastery) → int ; cooldownTicks(TechniqueSpec, CultivationData, int mastery) → int
TechniquePower.masteryLevel(TechniqueGrade, int points) → int ; bossShare(CultivationData | DiscipleData, TechniqueGrade) → double
TechniquePower.kScale(CultivationData) → double ; blockCap(CultivationData) → int
TechniquePower.finalDamage(DamageContext) → float            // 22.5.5, from the three choke points only
record CastOptions(boolean bypassGates, boolean free, boolean forceVisual, boolean showcase, boolean noKehancuran)
interface SpecialTechnique { … 22.2.7 … }   enum EndReason { DONE, DEATH, LOGOUT, DIMENSION, CANCELLED, REPLACED, OVERLOAD }
DeathGuard.register(Stage, Guard) ; PortalIndex.near(ServerLevel, BlockPos, int) ; LoadedPath.clip(Level, Vec3, Vec3)
DamageHooks.register(DamageHooks.Stage, DamageHooks.Modifier)

// P6c-mesin
Techniques.tryCast(ServerPlayer, TechniqueSpec, CastOptions) → boolean ; Techniques.tryCast(ServerPlayer, String id)
TechniqueRunner.start(CastContext) ; TechniqueRunner.abort(Entity caster, EndReason) ; TechniqueRunner.active(Entity) → int
Mastery.level(CultivationData | DiscipleData, String id) / points(…) / add(ServerPlayer, String id, int points, Mastery.Source)
Loadout.slots(CultivationData) → String[12] ; Unlocks.refresh(ServerPlayer) ; Unlocks.checklist(CultivationData, TechniqueSpec) → List<Unlocks.Line>
FeatTracker.progress(CultivationData, Route) → int ; FeatTracker.onTribStrike / onAlchemy / onMeditate (called from existing hooks)
FlightLease.acquire(ServerPlayer, String source) → @Nullable FlightLease ; FlightLease.release() ; FlightLease.holder(ServerPlayer)
Kehancuran.selfAllowed / selfOn / setSelf / submitSelf            // 22.11.5
DiscipleTechniques.trySpec(DiscipleEntity, TechniqueSpec, @Nullable LivingEntity target) → boolean
DisciplePowers.pairPartner(ServerPlayer master, double range) → @Nullable DiscipleEntity ; spendPairQi(DiscipleEntity, double share) → boolean

// P6c-kitab
SpecFx.play(Payloads.TechFx) ; Codex.open(int focusNetId) ; Codex.wheelInput(double dx, double dy) → boolean
CameraShake.add(float intensity, int ticks, Vec3 at, float radius) ; ScreenFlash.add(int rgb, float alpha, int ticks)
FxBudget.take(int tokens, FxBudget.Priority) → int

// P6c-seni-A
JadeSlips.stack(String specId) → ItemStack ; JadeSlips.specOf(ItemStack) → @Nullable String
InheritanceTrials.start(ServerPlayer, String theme) → boolean ; InheritanceTrials.active(ServerPlayer) → boolean
TechniqueBooks.poolFor(Holder<Enchantment>) → List<TechniqueSpec>

// P6c-seni-B: registers only through Sink (specs and the seven specials)
```

#### 22.18.4 Data keys

| Holder | Key | Owner |
|---|---|---|
| player `extra.teknik` | `m {id: int}`, `mday {id: [day, pts, nohit]}`, `slots [12]`, `feat {key: n}`, `cday [day, {dao: gained}]` (comprehension daily cap) | P6c-mesin |
| player `extra.teknik.warisan` | `cd` (inheritance jade cooldown), `won [themes]` | P6c-seni-A |
| player `extra.path.breaker` | bool | P6c-mesin (`Kehancuran`) |
| player `extra.kultivasi.trib_strikes` | int | spine (`Tribulation`) |
| `DiscipleData.extra.teknik` | `m {id: int}`, `slots [ids]` | P6c-mesin |
| SavedData `kultivator_homes` | uuid → [dim, x, y, z] | P6c-mesin |

**Session only:** global cooldown, ultimate lockout, charges, stacks, the Perisai Qi pool, forms, domains, fields, auras, toggles, phantoms, projectiles, DOT/MARK/BIND trackers, flight leases, the DeathGuard lockout and the `uji` bypass.

---------------------------------------------------------------------------------------------------------------------

### 22.19 Test checklist (for `docs/uji/FASE-6c.md`)

> Bahasa Indonesia. Persiapan: dunia uji dengan cheat aktif, mode Kreatif lalu Survival untuk poin yang menyebut Survival. Ubah ranah dengan `/kultivasi uji ranah <ranah> [tahap] [lapis]`. Setiap teknik punya baris **Sumber** di tooltip; poin M memeriksanya.

**A. Simpanan dan registri**
1. Muat simpanan lama (sebelum 6c): 23 teknik yang sudah dipelajari, pilihan dan cooldown tetap sama. Tulis id palsu `contoh_hilang` ke `learned` dengan `/data`, simpan, muat ulang → id itu tetap ada dan diabaikan, tidak ada crash.
2. `/kultivasi uji teknik audit` → "169 spec OK · 168 untuk pemain". Tidak ada peringatan kunci bahasa atau Sumber yang kosong.
3. Mulai singleplayer dua kali berturut-turut → log tidak menulis "duplicate id" (registri hanya dibangun sekali).

**B. Perintah uji**
4. `uji teknik` → chat "168 teknik dipelajari", qi penuh, cooldown nol. `uji teknik semua` menampilkan hitungan per set dan tingkat sesuai tabel Hasil.
5. `uji teknik heaven_punishing_sword` di Pemurnian Qi → teknik langsung dirapal di arah pandang tanpa cek ranah/qi. Chat menulis P, koefisien, area, batas yang berlaku dan kerusakan per target. Penguasaan tidak bertambah.
6. `uji teknik pamer 3` → setiap teknik dirapal bergiliran. Judul = nama Indonesia, subjudul = baris Sumber, bilah aksi "n/169". `uji teknik pamer stop` berhenti. Mati atau keluar dunia juga menghentikannya.
7. `uji teknik bebas nyala` → roda dan cincin bisa dipakai tanpa qi/cooldown. `mati` mengembalikan aturan normal.
8. `uji teknik penguasaan semua 4` → semua manik penguasaan penuh. `uji teknik gerbang` → kartu Dewa tidak terkunci lagi.
9. `uji boneka 3 bos` → tiga boneka. Pukul dengan jurus: papan nama menulis kerusakan mentah → akhir. Boneka tidak pernah mati, tidak memberi penguasaan dan hilang setelah 5 menit atau `uji boneka hapus`.

**C. Kekuatan dan skala**
10. `debug teknik kekuatan` di Pemurnian Qi: P ≈ D lama. Di Dewa Abadi Puncak dengan pemahaman 50: P ≈ 6.300.
11. Rapal Telapak Qi di Inti Emas lalu di Dewa Abadi: jangkauan, ukuran visual dan kerusakan jauh lebih besar. Qi dan cooldown jurus tingkat rendah turun mengikuti selisih ranah (tidak pernah di bawah 10 tick).
12. Regenerasi qi di Dewa Abadi ≈ ×1,4 dibanding Pemurnian Qi.

**D. Bos**
13. Warden asli dengan jurus terbaik tiap ranah: Inti Emas ≈ 12 rapalan, Transformasi Dewa ≈ 7, KA T9 ≈ 2, Dewa Abadi Puncak 1 (Abadi/Dewa).
14. Pukul Wither dengan jurus saat `mobGriefing` aktif → tidak ada blok yang rusak di sekitarnya. Murid tetap tidak melawan Wither.
15. Ender Dragon: BEAM mengenai kepala, NOVA mengenai tubuh. Naga tidak pernah terdorong atau terikat. Kristal End tidak tersentuh.
16. Iblis Hati sendiri: satu rapalan tidak pernah mengambil lebih dari 12% darahnya.

**E. Arena (kekuatan lama)**
17. Sparring, duel turnamen dan satu lantai Alam Dewa Hampa: kerusakan sama seperti sebelum 6c (bandingkan dengan Telapak Buddha 2,5 D). Jurus Dewa ke pemain tidak lebih dari 15% darah per pukulan dan per rapalan. Tidak ada kawah walau Kehancuran nyala.

**F. Penargetan dan keamanan blok (Kehancuran MATI)**
18. Piglin zombi netral tidak tersentuh NOVA, tetapi BEAM mengenainya. Benteng Nether tidak marah semua.
19. Hewan peliharaan, penduduk desa dan pemain lain tidak pernah terkena. Creeper, ravager, Warden, Wither dan naga tidak pernah terdorong. Tidak ada yang terdorong dalam 8 blok dari portal.
20. Dengan saklar MATI, rapal semua jurus api, petir, es dan tanah di dekat: penangkal petir, tembaga, lahan pertanian, telur penyu, salju bubuk, kuali air, rumput. Tidak ada blok berubah. Tidak ada api di blok, tidak ada `LightningBolt`.

**G. Penguasaan dan cara belajar**
21. Poin penguasaan naik (rapalan, kena, bunuh, bos). Toast "… mencapai Sukses Besar!" muncul. Level 3 butuh pemahaman 60, level 4 butuh 90. Bonus level 4 terlihat (gema atau bonus khusus).
22. Spam jurus 1 detik di spawner: poin kena/bunuh berhenti di 200 per hari; pemahaman berhenti di +3,0 per Dao per hari.
23. Jongkok + pakai buku *Sharpness* → teknik pertama yang bisa dipelajari dari kolamnya (urut tingkat). Tooltip buku menulis isi kolam dan alasan terkunci.
24. Slip Giok: klik kanan → belajar, atau "Isi slip ini terlalu dalam untukmu" + syarat yang kurang. Paviliun Yue Lu menjual "Slip Giok Teknik" sesuai tingkat dan level aula, maksimal 2 per hari.
25. Giok Warisan (DT+): pertanda 10 detik, 3 gelombang dalam 90 detik, penjaga biasa bisa dikalahkan, penjaga akhir terkena batas bos. Menang → pilih 1 dari 3. Kalah → giok tetap ada, cooldown 1 hari.
26. Feat: `debug teknik feat <jenis> <n>` lalu cek daftar syarat di detail kitab. Gerbang Dewa I/II terbuka setelah syaratnya terpenuhi.

**H. Kitab Teknik, cincin dan tombol**
27. Tombol `-` membuka kitab. Di 320×240 semua baris filter muat. Filter Dao, Ranah, Tingkat, Jenis dan Dikuasai/Bisa Dipelajari/Terkunci bekerja bersamaan.
28. Cari "pedang langit", "against", "zhuxian" → hasil benar (nama Indonesia, Inggris dan judul karya).
29. Detail: baris **Sumber** tampil ("Terinspirasi" dalam huruf miring), angka langsung sama dengan hasil `uji teknik <id>`, bilah penguasaan dan daftar syarat benar.
30. Cincin Segel: seret 12 teknik, keluar-masuk dunia → tetap tersimpan. "Isi otomatis" mengisi sesuai peran.
31. Ketuk G → rapal. Tahan G ≥ 250 ms → Roda Segel. Lepas → pilih ("Lepas = rapal" mengubahnya jadi rapal). Saat roda terbuka, tekan 1–4 → segel cepat. `,`/`.` hanya berputar di dalam cincin.
32. HUD: Perisai Qi, busur waktu wujud/wilayah/medan, bilah charge "Tekan lagi = lepas", pip tahap dan charge, label merah "KEHANCURAN". F1 menyembunyikan semuanya.

**I. Mode Kehancuran**
33. Singleplayer: satu klik segel merah → satu baris chat merah lengkap dengan perintah `/kultivasi kehancuran pulihkan`. Tidak ada layar sumpah. Label HUD muncul. `/kultivasi kehancuran nyala|mati` juga bekerja.
34. Di bawah Inti Emas: saklar bisa nyala, tetapi tidak ada kawah. Inti Emas: Amarah Sang Penguasa membuat mangkuk kecil. Dewa Abadi Puncak: kawah besar yang tumbuh dari tengah ke luar, dibatasi blok per rapalan di halaman Kehancuran.
35. Jongkok saat merapal → kerusakan tetap, tanpa kawah.
36. Tidak ada blok hilang dalam 4 blok di sekitar pemain (termasuk tepat di bawah kaki), dalam 24 blok dari kasur atau titik bangkit siapa pun, dalam 24 blok dari pemain lain, dan dalam 64 blok dari Prasasti Aula. Peti, tungku, portal, obsidian, bedrock dan air tidak pernah tersentuh.
37. `kehancuran pulihkan` dalam 10 menit → blok kembali di tempat yang masih udara.
38. Tiga rapalan merusak beruntun → yang ketiga menulis "Bumi masih runtuh — kehancuran dilewati". Server tetap 20 TPS (`debug teknik beban`).
39. Matikan saklar saat kawah sedang terbentuk → berhenti saat itu juga. Pemulihan yang sedang berjalan tetap lanjut.
40. LAN: tamu tanpa `izin on` ditolak dengan alasan, sedangkan tuan rumah bisa. Server khusus: tanpa `/kultivasi kehancuran izin on` saklar ditolak. `izin off` mematikan saklar semua tamu.
41. Catatan dan log server menulis `self:<id>` beserta jumlah blok. Pencapaian *Tanganku Membelah Bumi* didapat pada kawah pertama.

**J. 23 teknik lama**
42. Semua id lama tetap bisa dirapal. Langkah Awan: lompatan udara 1/2/3/4/5/6 sesuai ranah. Pedang Terbang tetap terbang. Sayap Lei Zhenzi dan Pedang Terbang tidak saling mencabut terbang.
43. Wilayah Dao: denyut sesuai Dao (misalnya Petir menembakkan lebih banyak sambaran di ranah tinggi). Penjara Ruang dan Badai Angin tetap menolak mendorong di dekat portal.

**K. Murid**
44. Ajari teknik bertanda Y (biaya qi guru sesuai tingkat) dan beri Slip Giok (gratis). Teknik N ditolak dengan alasan. Teknik Y★n terkunci sampai bintang n.
45. Slot tempur murid sesuai rumus. AI memakai area saat ≥ 3 musuh, menyembuhkan saat < 50%, dan pamungkas hanya melawan bos atau ≥ 4 musuh.
46. Murid tidak pernah membuat kawah dan tidak pernah melawan Wither. Harimau Putih Alam Arwah hanya bisa dirapal bersama murid yang terikat dalam 16 blok.

**L. Performa dan efek**
47. Kualitas Minimal: paling banyak 2 tampilan per rapalan (hanya Langit ke atas), anggaran partikel tidak terlampaui. Dengan 10 perapal sekaligus, FPS tetap layak. "Otomatis" turun ke Minimal di bawah 40 fps.
48. Guncangan mengikuti pengaturan (Ringan bawaan) dan *damage tilt* vanilla. Kilatan mengikuti *screen effect scale*. "Efek pemain lain: Mati" menyembunyikan jurus pemain lain.
49. `debug teknik beban` saat `uji teknik pamer` tetap di dalam anggaran 22.15.

**M. Sumber**
50. Arahkan kursor ke 20 kartu acak dari setiap set → setiap kartu punya baris Sumber. Nama yang tidak terverifikasi ditulis "Terinspirasi dari …".

**N. DeathGuard**
51. Pegang Totem lalu terima pukulan mematikan saat Tiga Helai Rambut Penyelamat aktif → totem terpakai lebih dulu, rambut tidak. Tanpa totem → rambut menyelamatkan, lalu kunci 10 menit berlaku untuk semua teknik penyelamat.
52. `/kill`, jatuh ke void dan sambaran kesengsaraan tidak pernah dicegah oleh teknik.

---------------------------------------------------------------------------------------------------------------------

### 22.20 Renamed, reworded and dropped names

These rows apply the lore verification and the balance critique. The id on the right is final.

| Earlier proposal | Now | Change | Why |
|---|---|---|---|
| `giant_sword` Teknik Pedang Raksasa / Giant Sword Technique | `giant_sword_light` **Cahaya Pedang Raksasa** / Giant Sword Light | rename + reword | 巨剑术 was found only in a game description. Now "Terinspirasi dari the giant sword light of the Azure Essence Sword Art" |
| `overlord_wrath` Murka Sang Penguasa / Overlord's Wrath | `overlord_fury` **Amarah Sang Penguasa** / Overlord's Fury | rename | Wuxiaworld name 霸王怒 (ATG ch. 333). It comes from a fragment of the broken Overlord sword, not a soul fragment. "Heaven Smiting Sword" removed (a different weapon) |
| `immortal_calamity_sword` | `immortal_tribulation_sword` **Seni Pedang Kesengsaraan Abadi** / Immortal Tribulation Sword Art | rename | canon name, one of the three great sword extremes (PW ch. 1786, 1973) |
| `grass_character_sword` | `grass_symbol_sword` **Seni Pedang Aksara Rumput** / Grass Symbol Sword Art | rename | 草字剑诀 (PW ch. 1786). The canon image (a blade of grass cuts sun, moon and stars) replaces "cuts time and space" |
| `heavenraker_stance` Sikap Pengoyak Langit | `heavenbreaker_stance` **Sikap Pemecah Langit** / Heavenbreaker Stance | rename | the stance is Heavenbreaker; "Daofather Heavenrake" is unverified and dropped |
| `heaven_slaying_sword_art` Seni Pedang Pembunuh Langit | `heaven_punishing_sword` **Seni Pedang Hukuman Langit** / Heaven Punishing Sword Art | rename | its four branches are unverifiable. The closest canon is ATG ch. 2189 (Mo E's Heaven Punishing Ancestral Sword); the four phases are game design |
| `blood_drop_stance` | unchanged | reword | lineage corrected: a stance of the Brightmoon sword art, not of Quintessence Sword-Intent |
| `zhuxian_sword_formation` Formasi Pedang Pembunuh Abadi | **Formasi Pedang Zhuxian** | Indonesian name + merge | the universal `zhuxian_formation` is merged into it |
| `phoenix_flame_burning_heaven` | `phoenix_flame_sears_heavens` **Api Phoenix Membakar Langit** / Phoenix Flame Sears the Heavens | rename | Wuxiaworld name (ATG ch. 440, 563) |
| `golden_emperor_burning_sky_flame` | `golden_emperor_flame` | rename (shorter id) + reword | "burns even Dou Qi" is unverified and dropped |
| `emperor_flame` | unchanged | reword | "Terinspirasi dari 帝炎, the legendary 1st of the 23 Heavenly Flames". "Devoured the 22 above it", "fusion of 22 flames" and "Xiao Yan's flame" dropped |
| `phoenix_arrow` | unchanged | reword | the shooter in ATG ch. 713 is unverified and left out |
| `eight_trigrams_furnace` | unchanged | reword | the Fiery Eyes part removed; `fiery_golden_eyes` covers it |
| Fire's phoenix arts | unchanged ids | visual rule | five Fire arts plus legacy `phoenix_flame` share the phoenix theme, so their names and visuals must differ clearly (22.10) |
| `ice_emperor_pincer` Capit Kaisar Es | `ice_empress_pincer` **Capit Permaisuri Es** / Ice Empress' Pincer | rename | Wuxiaworld name (UTS ch. 133). "First skill / hardens forearms" is unverified and dropped |
| `ice_snow_twin_emperors` Dua Kaisar Es dan Salju | `ice_snow_emperors_pride` **Kebanggaan Dua Kaisar Es dan Salju** / Pride of the Ice and Snow Twin Emperors | rename | canon name 冰雪二帝之骄傲, Huo Yuhao's 5th soul skill |
| `frost_god_sealing_heaven` Dewa Embun Beku Menyegel Langit | `ice_phoenix_frost_seal` **Segel Embun Beku Phoenix Es** / Ice Phoenix Frost Seal | rename + reword | the name was not found. "Terinspirasi dari the Ice Phoenix God Investiture Canon" (not "Sealing God Canon") |
| `ice_phoenix_descent` | unchanged | reword | the name was not found. "Terinspirasi dari the Ice Phoenix soul and canon"; the canon "Broken Moon Oblivion" (ch. 1061) is noted, not used |
| `bone_chilling_flame` Api Dingin Tulang | **Api Dingin Tulang Roh** | Indonesian name + reword | rank 11 and "freezing a thousand miles" are unverified and dropped |
| `broken_moon_shadow` Bulan Pecah … | **Bulan Patah Menyapu Bayangan** | Indonesian name + reword | the Chinese is 断月拂影 (not 碎月) |
| `thunder_emperor_finger` Jari Kaisar Petir | `lightning_emperor_art` **Seni Pusaka Kaisar Petir** / Lightning Emperor Precious Technique | rename | only the Lightning Emperor Precious Technique is attested (PW ch. 1405). "Thunder Emperor's Finger" and "Thunder Transformed into Stars" dropped; the finger image is game design |
| `thunderbolt_ancestral_symbol` | unchanged | reword | the "sun of lightning" (ch. 972) is visual flavour only |
| `lei_zhenzi_wings` Sayap Angin Guntur Lei Zhenzi | **Sayap Lei Zhenzi** | Indonesian name | kept distinct from Han Li's `wind_thunder_wings` |
| `tathagata_palm` (Body) | merged into `five_elements_mountain` (Universal) | merge | the same concept (JTTW ch. 7) |
| `mysterious_jade_hand` (Body, grab) | the Universal parry version, Kuning at FE | merge + move | the parry version is closer to canon; the Kuning grade needed content |
| `six_paths_fist`, `great_way_of_buddha` (Universal copies) | the Body versions | merge | duplicate ids. `great_way_of_buddha`: the level count (10 vs 12) is disputed and left out. `six_paths_fist`: medium-confidence source, worded as such |
| `bright_king_art` | unchanged | reword | a Buddhist body art from the Feng family secret cave, one of three arts later fused into the Brahmic Saint True Devil Art (not "its base") |
| `golden_ape_roar` | unchanged | reword | the golden giant ape and 金刚吼 come from the 惊蛰十二变 transformations with Mountain Giant Ape true blood, not from the Brahmic Saint True Devil Art |
| `octane_blast` | unchanged | reword | the chapter number ("ch. 18") is unverified and dropped |
| `ancient_god_trample` | unchanged id; **player Dewa** spec (Gerbang Dewa), disciples Y★28 | reword + scope | "Terinspirasi dari the Ancient God stars between the brows (Renegade Immortal ch. 921)". It was disciple-only; now only `karma_domain` is |
| `sixth_hex_life_death` (Life) | merged into `life_death_hex` (Universal) | merge | the same Sixth Hex (ISSTH ch. 882) |
| `clear_heart_melody` | unchanged | reword | 清心音 is confirmed canon, so the "still to be verified" note is removed |
| `gold_devouring_beetles` (Universal copy) | the Life version | merge | duplicate id; chapter numbers are not cited |
| `willow_god_branches`, `life_death_domain`, `karma_domain` | unchanged | reword | chapter numbers unverified; generic description only |
| `ghost_shadow_track` (Shadow copy) | the Universal version, Kuning at FE | merge + move | duplicate id |
| `somersault_cloud` (Shadow copy) | the Universal version, special `cloud_travel` | merge | duplicate id; capped at 128 blocks per cast, loaded chunks only |
| `hair_clones` (Shadow) | dropped | drop | the same as `body_beyond_body`; both used "Tubuh di Luar Tubuh" |
| `seventh_hex_karma` (Shadow) | merged into `karmic_hex` (Universal) | merge | the same Seventh Hex |
| `xing_secret` (Shadow ★18 copy) | the Universal version (IA T5, route ENLIGHT) | merge + reword | "enters the domain of time" is unverified and dropped |
| `star_god_broken_shadow` (Universal copy) | the Shadow version | merge + reword | "Evil Soul state single-target kill" removed (it confused the art with the Heretic God Art) |
| `billion_soul_flag`, `stygian_tiger_seal` (Universal copies) | the Shadow versions (virtual souls and corpses) | merge / drop | the universal Stygian Tiger CONVERT turned real mobs into puppets (arrows, tridents, egg trampling): unsafe |
| `wind_roaring_formation` | unchanged | reword | canon mechanics (wind and fire with ten thousand blades, ch. 44; broken by Cihang's Wind-Fixing Pearl, ch. 46), so no "inspired by" is needed |
| `fixing_spell` (Space) | merged into `immobilization_spell` (Universal) | merge | the same body-fixing spell; chapters 5 and 97 (39 and 88 dropped) |
| `fantian_seal` (Universal) | merged into `heaven_turning_seal` (Space) | merge | both used "Segel Pembalik Langit" |
| `black_hole` (Universal) | merged into `stellar_black_hole` (Space) | merge | the post-Star stages were self-evolved and are not recorded in the original art; worded so |
| `restriction_flag` (Universal) | dropped | drop | duplicate of Wang Lin's restrictions, and it spawned server-side Display entities |
| `heavenly_restriction` | unchanged id and game name | reword | "Thousand-Layer Heavenly Restriction" is not a canon name. "Terinspirasi dari Wang Lin's restrictions (禁制)" |
| `martial_soul_fusion` Jurus Fusi Jiwa Guru-Murid | `netherworld_white_tiger` **Harimau Putih Alam Arwah** / Netherworld White Tiger | rename | canon name 幽冥白虎 (Soul Land); "ep. 37" is unverified and dropped |
| `kylin_sacred_hall` Aula Suci Qilin | `qilin_sacred_palace` **Istana Suci Qilin** / Qilin Sacred Palace | rename | canon: the Qilin God's defence, named with the Black Tortoise's Xuanji Tower as one of the two greatest defences (ATG ch. 2008) |
| `moon_reversing_stars` | `moon_star_restoration` **Bulan dan Bintang Kembali** / Moon Star Restoration | rename | Wuxiaworld name 月挽星回, a skill of the 4th realm "Rumbling Heaven" of the Heretic God Art; it rebounds an attack at a heavy lifespan cost |
| `thunderstorm_wings` Sayap Badai Petir | `wind_thunder_wings` **Sayap Angin-Guntur Roc Petir** / Wind-Thunder Wings | rename | canon 风雷翅, the Thunder Roc wings Han Li tricked Feng Xi out of. "Four wings" and "ch. 1955" dropped |
| `heavenly_finger` Jari Surgawi | `heavenly_fate_finger` **Jari Takdir Langit** / Heavenly Fate Finger | rename | canon: the All-Seer's spell, used after briefly fusing with his soul (RI ch. 1254) |
| `seal_the_heavens_hex` Segel Penyegel Langit | `ninth_hex` **Segel Kesembilan** / Ninth Hex | rename | "Seal the Heavens Hex" is unverified. Meng Hao's own Ninth Hex (ISSTH ch. 1379, 1465, 1534, 1540) |
| `demon_fate_hex` (Tenth Hex) | dropped | drop | no evidence of a tenth hex; the hexes end at the Ninth |
| `emperor_seal_jue` | unchanged | reword | only 帝印决 (left by Xun'er) and its first seal 开山印 are verified; the Sea Flipping and Overturning Land seals are dropped |
| `sleep_insects`, `three_life_saving_hairs`, `fa_tian_xiang_di`, `ji_realm` | unchanged | reword | only verified chapters are cited: ch. 5, 25, 71; ch. 15, 75; ch. 3, 6; ch. 243, 268. "Kills same-stage foes in an instant" dropped |
| `burning_heart` | unchanged | reword | the "3–4×" multiplier is unverified; the art's power is game design |
| `dragon_soul_domain` | unchanged | reword | a mental domain that briefly stuns foes or makes them lose track (ATG ch. 316, 1064, 1099), not nightmares. "Flee 2 s" becomes UNTARGET + KNK |
| `myriad_cauldron` | unchanged | reword | credit for suppressing the Demon Emperor's heart goes to the green copper core, so it is phrased loosely. "+3% max qi per kill" becomes RESTORE_QI within the cast |
| `tai_chi_diagram` | unchanged | reword | it turned Yin Hong to ash (ch. 61), not Ma Yuan |
| `bloodshadow_evasion` | unchanged | reword | "over fifty kilometres" is unverified and dropped |
| `pear_blossom_needles`, `purple_demon_eye` | Kuning at FE | move | the Kuning grade needed content. Needles: ≤ 4 per target at FE, ≤ 8 at GC |
| the 23 legacy techniques | unchanged ids and names | new `.source` | "Terinspirasi dari …" lines (22.10); only Indra Ilahi names a verified concept |

**Infeasible clauses rewritten** (applies across the catalogue).

| Clause | Becomes |
|---|---|
| "monster attack speed −80%", "cooldowns ×3 inside" | periodic STUN + slowness |
| "no healing inside" | dropped |
| "hold to charge" | a fixed CHG; casting again releases early at proportional power |
| sneak as an alternate mode | STAGES recasts |
| deflecting projectiles (`void_mirror`, `mysterious_jade_hand`, `qilin_sacred_palace`, `ninth_hex`, `wraith_butterflies`) | PROJECTILE_GUARD discard (+ a simulated return strike) |
| "client ice sheet" (`tongtian_river_freeze`) | BlockDisplays or particles, never `ClientLevel.setBlock` |
| every server-spawned Display | client-only phantoms |

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
> Everything else in the mod stays fully block-safe.

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

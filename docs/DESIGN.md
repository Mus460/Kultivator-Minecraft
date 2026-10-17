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
  - Per hall at most: 6 candidate entities, 2 duellists plus at most 1 rival or phantom, 2 clones per disciple.
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

**Performance.** At most 6 candidate entities, each with a stroll goal ticked every 10 ticks and look-at AI.

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
- Each server tick (END_SERVER_TICK) removes at most `kehancuranBlocksPerTick` (48) positions across all jobs with `setBlock(air, Block.UPDATE_CLIENTS)`: flag 2, no neighbour updates, no cascades. Gravity blocks may float until touched.
- The job pauses while `server.getAverageTickTimeNanos()` > `kehancuranMsptPause` (45 ms).
- No fire, no `Explosion`, no item entities.
- **Drops:** none by default. With `kehancuranDropsPct` 1–10, each removed block has that chance to drop its resources, at most 64 stacks per cast.

**Pulihkan (restore).** A safety rule, not a story feature.
- Each cast keeps its removed states in memory, up to the cast cap.
- For `kehancuranUndoMinutes` (10) the master can press "Pulihkan" (Segel Merah tablet or chat).
- It restores at the same budget, only where the position is still air, and only for casts with 0% drops.
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

**Commands:** `/kultivasi aula info|nama <nama>`; debug `aula renown <n>`, `aula pk <n>`, `aula calon datang [n]`, `aula level`.

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

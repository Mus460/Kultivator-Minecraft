# Kultivator — design and code contract

A standalone Fabric mod for Minecraft **26.3** (Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25) in which the
**player** cultivates like in a xianxia novel. Mod id `kultivator`, package `dev.kultivator`. All player-facing text is
translated in **both** `en_us` and `id_id` (see "Language files").

This document is the contract every contributor (human or agent) follows. Read it fully before writing code.

---------------------------------------------------------------------------------------------------------------------

## 0. Hard rules (never break these)

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
  `murid set realm ...`, `murid xp <n>`, `murid summon`.
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

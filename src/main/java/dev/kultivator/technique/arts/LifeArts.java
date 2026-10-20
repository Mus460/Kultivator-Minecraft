package dev.kultivator.technique.arts;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec.Flag;
import dev.kultivator.data.TechniqueSpec.Gate;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.Flow;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Payload.AttrKind;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Kehidupan / Life catalogue (DESIGN 22.9.6, net ids 700-713): 14 specs (Misterius 4, Bumi 4, Langit 3, Abadi 2,
 * Dewa 1). Builder DSL only (22.2.5): heals and buffs reach the caster ({@code self}) and allies ({@code ally}; the
 * catalogue's "ALLY+SELF"), never monsters; vines, beetles and clones are client visuals or virtual phantoms, never
 * entities; the dew of Guanyin is particles only, never a weather change. The KH shapes are carved only by
 * {@code Kehancuran.submitSelf} while the player's own switch is on (22.11). The level 3 {@code perfection} bonuses are
 * game-design picks of 22.6; the level 4 {@code peak} is ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise.
 * <p>
 * "ALLY+SELF: HEAL x over t" next to a lasting AURA/FIELD/DOMAIN is written as its own step applied once (with the
 * area's radius), because a lasting shape's own payloads repeat every pulse.
 */
public final class LifeArts {

	/** Life green (the Dao colour), blue silver, gold, blood, ash. */
	private static final int GREEN = 0x6CFF8A;
	private static final int BLUE_SILVER = 0x7FD8FF;
	private static final int LEAF_GOLD = 0xB8E04A;
	private static final int GOLD = 0xFFD54A;
	private static final int BLOOD = 0x8A0010;
	private static final int ASH = 0x808080;
	private static final int LOTUS = 0xFFB0D0;

	/** Seven Treasure Glazed Tile Pagoda: one tier per second (22.9.6), the 7th at tick 120 multiplies every bonus ×1.5. */
	public static final int PAGODA_TICKS = 600;
	public static final int PAGODA_TIER_TICKS = 20;

	public static void register(TechniqueRegistry.Sink sink) {
		ParticleOptions goldDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GOLD_BLOCK.defaultBlockState());
		ParticleOptions blackSand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CONCRETE_POWDER.pick(DyeColor.BLACK).defaultBlockState());
		ParticleOptions whiteDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CONCRETE_POWDER.pick(DyeColor.WHITE).defaultBlockState());
		BlockState pinkGlass = Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState();

		// 700 · Seni Musim Semi Abadi (A Record of a Mortal's Journey)
		sink.add(art("eternal_spring_art", 700, Dao.LIFE)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.PULIH).realm(Realm.GOLDEN_CORE)
				.pose(Pose.MEDITATE_FLOAT)
				.route(Route.dao())
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(30).cooldown(20.0D)
				.step(at(0).shape(aura().radius(6, 10).duration(240).pulse(20)))
				.step(at(0).shape(nova().radius(6, 10))
						.self(heal(0.25D).over(240), buff(BuffKind.QI_REGEN, 1.5D, 240))
						.ally(heal(0.25D).over(240)))
				.fx(Phase.CAST, healRise(GREEN).onCaster().count(24).ticks(30), shockRing(GREEN).size(0.8F).count(24))
				// The GLOW fireflies show at night only (variant NIGHT: skipped while it is bright outside).
				.fx(Phase.PULSE, healRise(GREEN).count(8), shell(0xC8FF80).particle(ParticleTypes.GLOW).size(2.0F).count(4).variant(FxLayer.Variant.NIGHT).extra())
				.fx(Phase.PULSE, petals(0xFFD0E0).particle(ParticleTypes.CHERRY_LEAVES).count(4).ticks(20).detail())
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 701 · Belitan Rumput Perak Biru (Soul Land)
		sink.add(art("blue_silver_entangle", 701, Dao.LIFE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.KENDALI).realm(Realm.GOLDEN_CORE)
				.pose(Pose.KNEEL_STRIKE)
				.route(Route.book("minecraft:lure")).comprehension(10)
				.flag(Flag.TEACHABLE)
				.qi(25).cooldown(8.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(20).radius(4, 10))
						.hit(bind(BindKind.ROOT, 80, 120).boss(30), damage(0.6D), eff(Eff.POISON, 1, 80)))
				// The CAST event runs from the caster to the aim: the grass rises at the aim (TARGET attach).
				.fx(Phase.CAST, shockRing(BLUE_SILVER).size(0.6F).count(20), mist(BLUE_SILVER).onTarget().count(30).ticks(20))
				.fx(Phase.HIT,
						model("blue_silver_grass").scale(1.6F).rise(2.0F, 6.0F).ticks(80).facing(FxLayer.Facing.UPRIGHT),
						vortex(BLUE_SILVER).size(0.6F).count(14).ticks(30),
						shell(0xE8F8FF).particle(ParticleTypes.END_ROD).size(0.5F).count(4).detail())
				.sound(Phase.CAST, SoundEvents.CREAKING_SWAY, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0F, 1.2F)
				// the grass drains the ground it rises from (surface only, never a hole)
				.kehancuran(DShape.wither(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 702 · Vitalitas Bumi (Coiling Dragon)
		sink.add(art("earth_vitality", 702, Dao.LIFE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.PULIH).realm(Realm.NASCENT_SOUL)
				.pose(Pose.KNEEL_STRIKE)
				.route(Route.dao())
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(70).cooldown(40.0D)
				.step(at(0).shape(nova().radius(10, 24))
						.self(heal(0.25D, 0.35D), shield(0.15D, 400), buff(BuffKind.RESISTANCE, 1, 200))
						.ally(heal(0.25D, 0.35D), shield(0.15D, 400), buff(BuffKind.RESISTANCE, 1, 200))
						// ×2 on natural ground (one read-only check per receiver): the heal and shield land twice.
						.whenSelf(Cond.NATURAL_GROUND, heal(0.25D, 0.35D), shield(0.15D, 400)))
				.fx(Phase.CAST,
						column(LEAF_GOLD).size(0.8F).count(40).ticks(30),
						healRise(LEAF_GOLD).count(30).ticks(40),
						shockRing(0x8B6B4A).size(1.5F).count(30).detail())
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.2F)
				.perfection(Bonus.selfPayload(cleanse()))
				.peak(Bonus.duration(0.25D))
				.build());

		// 703 · Parasit Perak Biru (Soul Land)
		sink.add(art("blue_silver_parasite", 703, Dao.LIFE)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.pose(Pose.FINGER_POINT)
				.route(Route.comp(30))
				.flag(Flag.TEACHABLE)
				.qi(60).cooldown(20.0D)
				.step(at(0).shape(all().radius(16).max(6, 16).order(Shape.All.Order.NEAREST))
						.hit(dot(0.2D, 8, DotKind.POISON), drain(0.6D).allies(0.6D, 8))
						.onKill(at(0).shape(nova().radius(3).at(Anchor.HIT)).hit(bind(BindKind.ROOT, 40))))
				.fx(Phase.CAST, shockRing(BLUE_SILVER).size(0.6F).count(20), motesTo(GREEN).onCaster().count(16).detail())
				.fx(Phase.HIT,
						model("blue_silver_grass").scale(1.2F).rise(2.0F, 8.0F).ticks(60).facing(FxLayer.Facing.UPRIGHT),
						motesTo(GREEN).count(12).ticks(30),
						vortex(BLUE_SILVER).size(0.5F).count(10).detail())
				.sound(Phase.CAST, SoundEvents.CREAKING_TWITCH, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.3F, 1.8F)
				// "WITHER 2 ×≤6 (death spots)".
				.kehancuran(DShape.wither(2).times(6))
				.perfection(Bonus.extraCount(4))
				.peak(Bonus.echo(0.4D))
				.build());

		// 704 · Melodi Hati Jernih (Mo Dao Zu Shi)
		sink.add(art("clear_heart_melody", 704, Dao.LIFE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.PULIH).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.FLUTE)
				.route(Route.book("minecraft:mending"), Route.pav())
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(120).cooldown(35.0D)
				.step(at(0).shape(nova().radius(16, 32))
						.hit(eff(Eff.SLOWNESS, 4, 120), eff(Eff.WEAKNESS, 3, 120))
						.when(Cond.TARGET_UNDEAD, 1.0D, damage(0.8D).vs(Payload.Versus.UNDEAD, 2.0D)))
				// The melody lingers: every 40 ticks it cleanses and steadies whoever stands in it.
				.step(at(0).shape(aura().radius(6, 10).duration(400).pulse(40))
						.self(cleanse(), buff(BuffKind.RESISTANCE, 1, 60))
						.ally(cleanse(), buff(BuffKind.RESISTANCE, 1, 60)))
				.fx(Phase.CAST,
						shockRing(0xD0E8FF).particle(ParticleTypes.END_ROD).n(5).size(2.0F).count(40).ticks(30),
						FxLayer.of(FxLayer.Template.GLYPH, 0xD0E8FF).text("♫").scale(3.0F).above(3.0F).ticks(40),
						shockRing(0xD0E8FF).size(1.2F).count(30).detail())
				.fx(Phase.PULSE, shockRing(0xD0E8FF).size(0.5F).count(10))
				.fx(Phase.HIT, shell(0xE8F4FF).particle(ParticleTypes.END_ROD).size(0.6F).count(4))
				.sound(Phase.CAST, SoundEvents.NOTE_BLOCK_HARP, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HARP, 1.0F, 1.26F).delay(3))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HARP, 1.0F, 1.5F).delay(6))
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 0.8F, 1.2F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 705 · Pagoda Kaca Tujuh Harta (Soul Land): one tier per second, the pagoda grows a storey with each.
		var pagoda = art("seven_treasure_pagoda", 705, Dao.LIFE)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.PULIH).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.HOLD_ITEM_UP)
				.route(Route.pav(), Route.book("minecraft:luck_of_the_sea"))
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(130).cooldown(90.0D)
				.step(at(0).shape(aura().radius(6, 10).duration(PAGODA_TICKS).pulse(20)))
				.fx(Phase.CAST, shockRing(0xFFE0F0).size(1.0F).count(30), healRise(0xFFF0C0).onCaster().count(16).detail())
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D));
		Payload.Buff[] tiers = {
				empower(0.25D, 0),
				buff(BuffKind.SPEED, 2, 0),
				buff(BuffKind.QI_REGEN, 2.0D, 0),
				buff(BuffKind.RESISTANCE, 1, 0),
				buff(BuffKind.JUMP, 2, 0),
				buff(BuffKind.REGENERATION, 1, 0)};
		Block[] storeys = {
				Blocks.GLAZED_TERRACOTTA.pick(DyeColor.RED), Blocks.STAINED_GLASS.pick(DyeColor.ORANGE),
				Blocks.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW), Blocks.STAINED_GLASS.pick(DyeColor.LIME),
				Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE), Blocks.STAINED_GLASS.pick(DyeColor.BLUE),
				Blocks.GLAZED_TERRACOTTA.pick(DyeColor.PURPLE)};
		for (int tier = 0; tier < storeys.length; tier++) {
			int start = tier * PAGODA_TIER_TICKS;
			int left = PAGODA_TICKS - start;
			Payload.Boon[] boons = tier < tiers.length
					? new Payload.Boon[]{lasting(tiers[tier], left)}
					: sevenTreasures(tiers, left);
			// The first storey rises with the cast itself (CAST is sent once per cast); later storeys use EXTRA tier.
			Phase phase = tier == 0 ? Phase.CAST : Phase.extra(tier);
			var tierStep = at(start).shape(nova().radius(6, 10)).self(boons).ally(boons);
			pagoda.step(tier == 0 ? tierStep : tierStep.phase(tier));
			float width = 3.0F - 0.38F * tier;
			pagoda.fx(phase,
					// each glazed storey grows onto the pagoda held above the hand (one per second)
					model("pagoda_tier_" + (tier + 1)).scale(width * 0.6F).above(2.6F + 0.6F * tier).onCaster().grow(0.2F, 6.0F).ticks(left)
							.facing(FxLayer.Facing.FACE_FORWARD),
					giantBlock(storeys[tier].defaultBlockState()).blockSize(width, 0.8F, width).above(3.0F + 0.9F * tier - 0.04F * tier * tier)
							.onCaster().spin(2.0F).minTier(1).ticks(left).extra(),
					shockRing(storeys[tier].defaultMapColor().col).size(0.5F).count(16));
			pagoda.sound(phase, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F + 0.16F * tier);
		}
		sink.add(pagoda.build());

		// 706 · Kawanan Kumbang Pemakan Emas (A Record of a Mortal's Journey)
		sink.add(art("gold_devouring_beetles", 706, Dao.LIFE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.pose(Pose.SEAL)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.LIFE, Cond.NONE, List.of("#minecraft:arthropod"), 100)))
				.flag(Flag.TEACHABLE, Flag.PAVILION)
				.stack(Flow.Stack.onKill(0.10D, 10))
				.qi(180).cooldown(60.0D)
				.step(at(0).shape(all().radius(24).max(12, 32))
						.hit(dot(0.35D, 8, DotKind.BLEED).pierce(), attrPct(AttrKind.ARMOR, -0.3D, 160), drain(0.1D)))
				.fx(Phase.CAST, swarm(GOLD).color2(0xD0D8E0).model("gold_beetle").n(8).count(120).ticks(160))
				.fx(Phase.HIT, swarm(GOLD).color2(0xC0C8D0).onTarget().count(20).ticks(160))
				.sound(Phase.CAST, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0F, 0.6F)
				.sound(Phase.HIT, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.4F, 0.7F)
				// "WITHER 2 ×≤8 (kill spots)".
				.kehancuran(DShape.wither(2).times(8))
				.perfection(Bonus.extraCount(6))
				.peak(Bonus.echo(0.4D))
				.build());

		// 707 · Cabang dan Daun Emas Dewa Dedalu (Perfect World)
		sink.add(art("willow_god_branches", 707, Dao.LIFE)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.KENDALI).realm(Realm.DAO_UNION)
				.pose(Pose.SUMMON_RAISE)
				.route(Route.comp(60), Route.pav())
				.flag(Flag.TEACHABLE, Flag.PAVILION)
				.qi(200).cooldown(50.0D)
				.step(at(0).shape(all().radius(20, 32).max(10, 24))
						.hit(bind(BindKind.ROOT, 60).boss(15), damage(1.2D))
						.whenSelf(Cond.casterLowHp(0.5D), heal(0.3D)))
				.step(at(0).shape(volley().count(24).seek().maxTargets(12).perTarget(2).spread(Shape.Volley.Spread.FAN, 120))
						.hit(damage(0.9D).vs(Payload.Versus.BOSS, 0.6D)))
				.fx(Phase.CAST,
						model("willow_god").scale(8.0F).grow(0.1F, 15.0F).ticks(60).facing(FxLayer.Facing.FACE_FORWARD),
						healRise(0x5A8A3A).size(2.0F).count(30).ticks(30),
						shake(0.1F, 6))
				.fx(Phase.HIT, trail(0x5A8A3A).count(16).ticks(6))
				.fx(Phase.TRAVEL, trail(GOLD).particle(goldDust).count(12).ticks(10))
				.sound(Phase.CAST, SoundEvents.CREAKING_SWAY, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 0.8F, 1.0F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F)
				// "WITHER 6 (around the caster)".
				.kehancuran(DShape.wither(6))
				.perfection(Bonus.extraCount(8))
				.peak(Bonus.echo(0.4D))
				.build());

		// 708 · Formasi Pelebur Darah (Investiture of the Gods ch. 44/46)
		sink.add(art("blood_transforming_formation", 708, Dao.LIFE)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.LIFE, Cond.NIGHT, null, 200)))
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.NO_ECHO)
				.qi(280).cooldown(80.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(14, 28).height(12).duration(300).pulse(20))
						.hit(damage(0.5D), damagePct(0.01D, 0.005D), eff(Eff.WITHER, 2, 60), drain(0.25D)))
				.fx(Phase.CAST,
						vortex(0x202020).particle(blackSand).size(2.0F).onTarget().count(60).ticks(300),
						vortex(0x101010).particle(ParticleTypes.SQUID_INK).size(1.5F).onTarget().count(20).ticks(300).detail(),
						dome(BLOOD).onTarget().count(40).ticks(300),
						model("formation_banner_red").n(3).radius(14.0F).scale(3.0F).onTarget().ticks(300).facing(FxLayer.Facing.UPRIGHT),
						swordRing(Items.BANNER.pick(DyeColor.RED), 3, 14.0F).scale(4.0F).onTarget().minTier(1).ticks(300).extra(),
						shake(0.1F, 6))
				.fx(Phase.PULSE, dome(BLOOD).size(1.02F).count(16).ticks(10).detail())
				.fx(Phase.HIT, shell(BLOOD).size(0.8F).count(10))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.0F)
				.sound(Phase.CAST, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F, 0.5F)
				.sound(Phase.PULSE, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F)
				.kehancuran(DShape.wither(8))
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 709 · Klon Darah (I Shall Seal the Heavens)
		sink.add(art("blood_clones", 709, Dao.LIFE)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 4)
				.pose(Pose.SUMMON_RAISE)
				// FIRST_KILL wither with LIFE from IA T4 (layer index 3).
				.route(Route.feat(Feat.firstKill("minecraft:wither", Dao.LIFE, Realm.IMMORTAL_ASCENSION, 3)))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(320).cooldown(90.0D)
				.step(at(0).self(summon(Payload.Summon.CLONE, 2, 4, 300).mode(Payload.SummonMode.CLING).interval(20).range(16)
						// Each clone strikes the nearest hostile within 16 of itself.
						.attack(at(0).shape(all().at(Anchor.SUMMON).radius(16).max(1)).hit(damage(0.8D)))))
				// @300 the clones converge: DMG 3 per clone; SELF HEAL .1 per clone (2→4 clones: .2→.4).
				.step(at(300).shape(zone(Anchor.densest(1)).radius(6, 10)).hit(damage(0.0D).perSummon(3.0D))
						.self(heal(0.2D, 0.4D)).phase(0))
				.fx(Phase.CAST,
						cloneOf(BLOOD).alpha(0.5F).n(4).ticks(16),
						afterimage(BLOOD).count(16).ticks(20).detail())
				.fx(Phase.HIT, shell(BLOOD).size(0.6F).count(8))
				.fx(Phase.EXTRA0, shell(0xB00020).size(1.5F).count(60), impact(BLOOD).count(30).detail(), shake(0.45F, 12))
				.fx(Phase.PHANTOM, arc(BLOOD).count(10).ticks(6), impact(0x8A0010).particle(ParticleTypes.DAMAGE_INDICATOR).onTarget().count(6).ticks(8))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.0F)
				.sound(Phase.CAST, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.7F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F)
				.kehancuran(DShape.bowl(6).at(300))
				.perfection(Bonus.extraCount(1))
				.peak(Bonus.duration(0.25D))
				.build());

		// 710 · Tubuh Teratai Nezha (Investiture of the Gods ch. 14)
		sink.add(art("nezha_lotus_body", 710, Dao.LIFE)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.LINDUNG).realm(Realm.IMMORTAL_ASCENSION, 7)
				.pose(Pose.MEDITATE_FLOAT)
				.route(Route.comp(85), Route.inh(Dao.LIFE.id()))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(400).cooldown(600.0D)
				.step(at(0).self(
						dr(0.3D, Payload.Facing.ALL, 800),
						cleanse(),
						// A lethal hit becomes a full heal, resistance III and a lotus burst; then the window ends.
						cheatDeath(1, 800, 1.0D).onTrigger(at(0).shape(nova().radius(10, 20)).hit(damage(4.0D))
								.self(buff(BuffKind.RESISTANCE, 3, 60)).phase(0))))
				.fx(Phase.CAST,
						model("lotus_pedestal").onCaster().above(-0.1F).scale(2.0F).ticks(800).facing(FxLayer.Facing.FLAT),
						petalRing(pinkGlass, 8, 3.0F).scale(6.0F).onCaster().minTier(1).ticks(600).extra(),
						mist(LOTUS).particle(ParticleTypes.CHERRY_LEAVES).onCaster().count(20).ticks(800),
						shieldShell(LOTUS).count(16).ticks(60).detail())
				.fx(Phase.EXTRA0,
						shell(0xFFE0F0).particle(ParticleTypes.CHERRY_LEAVES).size(1.5F).count(30), model("lotus_pedestal").onCaster().scale(4.0F).grow(1.0F, 8.0F).ticks(30).facing(FxLayer.Facing.FLAT),
						petals(LOTUS).size(3.0F).count(60).ticks(30),
						shake(0.3F, 10))
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 0.8F, 1.4F)
				.sound(Phase.EXTRA0, SoundEvents.TOTEM_USE, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.BELL_RESONATE, 1.2F, 1.0F)
				.perfection(Bonus.durationTicks(200))
				.peak(Bonus.duration(0.25D))
				.build());

		// 711 · Embun Manis Guanyin (Journey to the West ch. 26)
		sink.add(art("guanyin_sweet_dew", 711, Dao.LIFE)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PULIH).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.PRAY)
				.route(Route.comp(80), Route.inh(Dao.LIFE.id()))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(300).cooldown(240.0D)
				// The field: hostiles hurt only when undead, DMG .8 per pulse.
				.step(at(0).shape(field(Anchor.CASTER).radius(24, 32).height(16).duration(200).pulse(20))
						.when(Cond.TARGET_UNDEAD, 1.0D, damage(0.8D)))
				// ALLY+SELF (players, pets, disciples, villagers, golems): HEAL .5 over 200t + CLNS; SELF RESTORE_QI .3.
				.step(at(0).shape(nova().radius(24, 32))
						.self(heal(0.5D).over(200), cleanse(), restoreQi(0.3D))
						.ally(heal(0.5D).over(200).everyone(), cleanse()))
				// onEnd: regeneration III for 10 s.
				.step(at(200).shape(nova().radius(24, 32))
						.self(buff(BuffKind.REGENERATION, 3, 200))
						.ally(buff(BuffKind.REGENERATION, 3, 200)).phase(0))
				.fx(Phase.CAST,
						model("guanyin_vase").above(8.0F).scale(4.0F).swing(20.0F, 40.0F).swingEvery(40).ticks(200).facing(FxLayer.Facing.UPRIGHT),
						model("willow_sprig").above(10.5F).scale(2.0F).swing(30.0F, 20.0F).swingEvery(20).ticks(200).facing(FxLayer.Facing.UPRIGHT),
						mist(0xFFFFFF).particle(ParticleTypes.GLOW).above(16.0F).size(1.5F).count(30).ticks(200))
				.fx(Phase.PULSE,
						mist(0xE8F8FF).particle(ParticleTypes.END_ROD).size(1.5F).count(20),
						mist(0xFFFFFF).particle(whiteDust).size(1.5F).count(20).detail())
				.fx(Phase.EXTRA0, healRise(GREEN).size(2.0F).count(40))
				.fx(Phase.PULSE, mist(0xE8F8FF).particle(ParticleTypes.FALLING_WATER).above(7.0F).count(20).ticks(20))
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 1.0F, 1.2F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0F, 1.2F)
				.sound(Phase.PULSE, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.6F, 1.4F)
				// Phase 10 (DESIGN 28.12): lite row (not an ultimate): a soft white ring and the vase lingering after the field
				.dampak(Dampak.builder().mark(Dampak.Mark.RING_ONLY).climaxAt(200).debris(Dampak.Debris.NONE).ring(Dampak.Ring.GOLD)
						.monument("guanyin_vase", 4.0F, 30, Dampak.MonumentPose.HOVER).build())
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 712 · Wilayah Hidup dan Mati (Renegade Immortal)
		sink.add(art("life_death_domain", 712, Dao.LIFE)
				.grade(TechniqueGrade.ABADI, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.ARMS_SPREAD).climax(Phase.EXTRA0)
				.route(Route.comp(90), Route.feat(Feat.deviationRecovered(Realm.IMMORTAL_ASCENSION)))
				.flag(Flag.TEACHABLE, Flag.NO_ECHO).teachStars(27)
				.qiPct(55, 700).cooldown(360.0D)
				.step(at(0).shape(domain().radius(20, 32).height(16).duration(400).pulse(20))
						.hit(damage(0.6D), damagePct(0.02D, 0.01D), exec(0.1D, 0.05D), drain(0.2D)))
				.step(at(0).shape(nova().radius(20, 32)).self(heal(0.6D).over(400)).ally(heal(0.6D).over(400)))
				.step(at(400).shape(nova().radius(32)).hit(damage(12.0D)).phase(0))
				.fx(Phase.CAST,
						dome(ASH).color2(0x9AE05A).variant(FxLayer.Variant.SPLIT).count(60).ticks(400),
						mist(0x707070).particle(ParticleTypes.ASH).size(1.5F).count(30).ticks(400),
						mist(0x60A0A0).particle(ParticleTypes.SCULK_SOUL).size(1.5F).count(12).ticks(400).detail(),
						trail(0x80C0FF).particle(ParticleTypes.SOUL).count(20).ticks(400).detail(),
						FxLayer.of(FxLayer.Template.GLYPH, 0x9AE05A).color2(0xA0A0A0).text("生死").n(2).radius(8.0F).scale(6.0F).above(8.0F).ticks(400),
						sky(0x404040).minTier(4).ticks(400).extra())
				.fx(Phase.PULSE, mist(0xE8FFE0).particle(ParticleTypes.END_ROD).size(2.0F).count(8))
				.fx(Phase.EXTRA0, shell(0xE0E0E0).size(1.5F).count(80), flash(0xFFFFFF, 0.25F, 6), shake(0.7F, 20))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.0F)
				.sound(Phase.CAST, SoundEvents.BEACON_AMBIENT, 1.2F, 0.8F)
				.sound(Phase.PULSE, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.6F)
				.kehancuran(DShape.wither(10))
				// Phase 10 (DESIGN 28.12): the life-death sigil turns; the land withers to ash; a half-withered lotus stays
				.dampak(Dampak.builder().mark(Dampak.Mark.RING_ONLY).ride(Dampak.Ride.SHORT).climaxAt(400)
						.frames(0xE0E0E0, 0x9AE05A).silence(4).debris(Dampak.Debris.CONVERT).ring(Dampak.Ring.GOLD)
						.sigil(SigilPattern.LIFE_DEATH, 20.0F)
						.monument("tang_lotus", 4.0F, 60, Dampak.MonumentPose.UPRIGHT).build())
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 713 · Pohon Kuno Bodhisattva: Seratus Kehidupan (Battle Through the Heavens)
		sink.add(art("bodhisattva_tree", 713, Dao.LIFE)
				.grade(TechniqueGrade.DEWA, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.MEDITATE_FLOAT).climax(Phase.EXTRA1)
				.gate(Gate.DEWA_II).prereq("life_death_domain", 3).prereq("guanyin_sweet_dew", 4).prereq("nezha_lotus_body", 4)
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO).dewaStars(37).teachStars(37)
				.qiPct(80).cooldown(900.0D)
				.step(at(0).shape(field(Anchor.CASTER).radius(24, 40).height(16).duration(600).pulse(20)))
				.step(at(0).shape(nova().radius(24, 40))
						// "CHEAT_DEATH 1 (1-HP floor once)": healthShare 0 = the DeathGuard HP floor.
						.self(heal(0.9D).over(600), buff(BuffKind.QI_REGEN, 3.0D, 600), comprehensionGain(0.05D, 3.0D), cheatDeath(1, 600, 0.0D))
						.ally(heal(0.9D).over(600), buff(BuffKind.QI_REGEN, 3.0D, 600)))
				.step(at(100).repeat(5, 100).shape(nova().radius(40)).hit(damage(4.0D), eff(Eff.SLOWNESS, 3, 100)).phase(0))
				.step(at(600).shape(nova().radius(40)).hit(damage(14.0D)).phase(1))
				.fx(Phase.CAST,
						model("bodhi_tree").scale(20.0F).grow(0.1F, 40.0F).ticks(600).facing(FxLayer.Facing.FACE_FORWARD),
						mist(0x9AE05A).size(1.5F).count(40).ticks(600),
						mist(0xFFF0A0).particle(ParticleTypes.GLOW).above(20.0F).size(1.5F).count(20).ticks(600).detail(),
						sky(0x9AE05A).minTier(4).ticks(600),
						shake(0.3F, 10))
				.fx(Phase.PULSE, healRise(GREEN).size(2.0F).count(12))
				.fx(Phase.EXTRA0, shockRing(0x9AE05A).size(1.5F).count(60), shake(0.5F, 10))
				.fx(Phase.EXTRA1, shell(0xFFF0A0).size(1.5F).count(120), flash(0xE8FFD0, 0.3F, 6), shake(1.0F, 30))
				.fx(Phase.EXTRA0, impact(0xFFF0A0).particle(ParticleTypes.GLOW).above(20.0F).count(20).ticks(10))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.8F)
				.sound(Phase.CAST, SoundEvents.CONDUIT_AMBIENT, 1.5F, 1.0F)
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 1.0F, 0.8F)
				.sound(Phase.EXTRA0, SoundEvents.BELL_BLOCK, 1.0F, 0.8F)
				.sound(Phase.EXTRA1, SoundEvents.BELL_RESONATE, 2.0F, 0.6F)
				.sound(Phase.EXTRA1, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.6F)
				// the tree draws life from the land around it (first damaging nova), then the @600 burst breaks a ring of natural ground
				.kehancuran(DShape.wither(12), DShape.ring(14, 6, 1).nat().at(600))
				// Phase 10 (DESIGN 28.12): the roots grow one branch per pulse; a bodhi sapling stays
				.dampak(Dampak.builder().mark(Dampak.Mark.PILLAR).ride(Dampak.Ride.FULL, 80, Dampak.Vista.POHON_CAHAYA).climaxAt(600)
						.frames(0xE8FFD0, 0xFFF0A0).silence(4).debris(Dampak.Debris.CONVERT).ring(Dampak.Ring.GOLD).palette(Dampak.Palette.LIFE)
						.sigil(SigilPattern.ROOTS, 24.0F).ignite(Phase.EXTRA0)
						.monument("bodhi_tree", 3.0F, 180, Dampak.MonumentPose.UPRIGHT).build())
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	/** The same buff for {@code ticks} (the pagoda tiers last until the aura ends). */
	private static Payload.Buff lasting(Payload.Buff buff, int ticks) {
		return new Payload.Buff(buff.kind(), buff.value(), buff.value2(), dev.kultivator.technique.spec.dsl.Val.of(ticks), buff.dao(),
				buff.onlyInArea(), buff.hpScaled(), buff.stackMax());
	}

	/** Tier 7 "Tujuh Harta": every bonus of tiers 1-6 ×1.5 (effect levels rounded up, resistance at most III). */
	private static Payload.Boon[] sevenTreasures(Payload.Buff[] tiers, int ticks) {
		Payload.Boon[] result = new Payload.Boon[tiers.length];
		for (int i = 0; i < tiers.length; i++) {
			Payload.Buff buff = tiers[i];
			double value = buff.kind().effect() != null ? Math.ceil(buff.value() * 1.5D) : buff.value() * 1.5D;
			if (buff.kind() == BuffKind.RESISTANCE) {
				value = Math.min(3.0D, value);
			}
			result[i] = lasting(new Payload.Buff(buff.kind(), value, buff.value2(), buff.ticks(), buff.dao(), buff.onlyInArea(),
					buff.hpScaled(), buff.stackMax()), ticks);
		}
		return result;
	}

	private LifeArts() {
	}
}

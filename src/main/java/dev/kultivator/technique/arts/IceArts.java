package dev.kultivator.technique.arts;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Payload.AttrKind;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Payload.SummonMode;
import dev.kultivator.technique.spec.dsl.Payload.Versus;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static dev.kultivator.data.TechniqueSpec.Flag.*;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Es / Ice catalogue (DESIGN 22.9.3, net ids 400-413): 14 arts from Misterius to Dewa, each written exactly as its
 * catalogue row with the Sumber line in the {@code arts_ice} lang fragment. Freezing is the frozen-ticks state plus
 * Slowness on entities only (never a block, never {@code ClientLevel.setBlock}); "shatter" hits deal ×1.5 to a frozen
 * target and end the freeze (22.3.2). Ice sheets, spikes and mountains are client-only displays. Level-3 bonuses are game
 * design; level 4 is the 22.6 default.
 */
public final class IceArts {

	public static final double PEAK_ECHO = 0.4D;
	public static final double PEAK_DURATION = 0.25D;

	/** Snowy biomes (Fabric's conventional tag) and the frozen waters strays spawn in. */
	private static final Cond SNOWY = Cond.biome("#c:is_snowy");
	private static final Cond FROZEN = Cond.any(Cond.biome("#c:is_snowy"), Cond.biome("minecraft:frozen_ocean"),
			Cond.biome("minecraft:deep_frozen_ocean"), Cond.biome("minecraft:frozen_river"));

	public static void register(TechniqueRegistry.Sink sink) {
		// 400 · Capit Permaisuri Es · GC · Misterius-R · SERANG · DAO, COMP 10 · Mrd Y
		sink.add(done(art("ice_empress_pincer", 400, Dao.ICE)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.TWIN_PALM).pose(Phase.IMPACT, Pose.TWIN_PALM)
				.route(Route.dao()).comprehension(10)
				.qi(20).cooldown(8.0D)
				// STRIKE(r4, 90°) > DMG 1 + slowness III 60t; GRANT_HOOK melee (3 charges, 160t) > DMG 1 + slowness III, the 3rd also
				// FRZ 40t; SELF resistance I 160t.
				.step(at(0).shape(strike().range(4.0D).arc(90.0D)).hit(damage(1.0D), eff(Eff.SLOWNESS, 3, 60))
						.self(buff(BuffKind.RESISTANCE, 1, 160),
								hook(Hook.onMelee(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(1.0D), eff(Eff.SLOWNESS, 3, 60)))
										.charges(3).ticks(160)
										.lastCharge(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(1.0D), eff(Eff.SLOWNESS, 3, 60), freeze(40))))))
				// Emerald-ice claw gauntlets.
				.fx(Phase.CAST, arc(0x7FFFD4).count(20), trail(0x7FFFD4).onCaster().count(12).ticks(40),
						trail(0xFFFFFF).particle(ParticleTypes.SNOWFLAKE).onCaster().count(6).detail())
				.fx(Phase.HIT, shards(0x7FFFD4).count(8))
				.fx(Phase.IMPACT, arc(0x7FFFD4).size(1.2F).count(14).ticks(6), shards(0xBFFFF0).count(6).ticks(8).detail())
				.sound(Phase.CAST, SoundEvents.GLASS_BREAK, 0.8F, 1.5F)
				.perfection(Bonus.payload(freeze(20)))));

		// 401 · Pembekuan Es Adas Bintang · GC · Misterius-M · AREA · DAO, COMP 20 · Mrd Y
		sink.add(done(art("star_anise_ice_coagulation", 401, Dao.ICE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.AREA).realm(Realm.GOLDEN_CORE)
				.pose(Pose.PALM_THRUST)
				.route(Route.dao()).comprehension(20)
				.qi(30).cooldown(6.0D)
				.step(at(0).shape(volley().sim().count(6.0D, 18.0D).spread(Shape.Volley.Spread.FAN, 40.0D).range(20.0D, 32.0D).pierce(1))
						.hit(damage(0.8D), eff(Eff.SLOWNESS, 2, 60)))
				// An eight-pointed snowflake glyph, then thin ice spears.
				.fx(Phase.CAST, FxLayer.of(FxLayer.Template.GLYPH, 0xBFEFFF).text("❄").scale(2.0F).minTier(1), shards(0xBFEFFF).count(16))
				.fx(Phase.TRAVEL, model("ice_spear").scale(1.5F).slide(10.0F).ticks(12).facing(FxLayer.Facing.BLADE),
						trail(0xCFF4FF).particle(ParticleTypes.SNOWFLAKE).count(8))
				.fx(Phase.LAND, shards(0xCFF4FF).count(6).ticks(8))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F)
				// a small shatter pit where each ice spear strikes
				.kehancuran(DShape.scatter(1))
				.perfection(Bonus.extraCount(4))));

		// 402 · Seni Awan Beku · NS · Misterius-M · AREA · BOOK frost_walker, COMP 30 · Mrd Y
		sink.add(done(art("frozen_cloud_art", 402, Dao.ICE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.pose(Pose.TWIN_PALM)
				.route(Route.book("minecraft:frost_walker")).comprehension(30)
				.qi(60).cooldown(12.0D)
				.step(at(0).shape(cone().length(12.0D, 24.0D).halfAngle(30.0D)).hit(damage(1.5D), eff(Eff.SLOWNESS, 4, 80), freeze(40)))
				.step(at(0).shape(field(Anchor.AIM).radius(4.0D, 8.0D).duration(100).pulse(20)).hit(eff(Eff.SLOWNESS, 2, 30)))
				// Rolling white-blue cloud banks.
				.fx(Phase.CAST, mist(0xE8F4FF).count(50), coneSpray(0xDDEEFF).particle(ParticleTypes.SNOWFLAKE).count(30))
				.fx(Phase.PULSE, mist(0xE8F4FF).count(16).extra())
				.sound(Phase.CAST, SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.POWDER_SNOW_BREAK, 1.0F, 1.0F)
				.kehancuran(DShape.fan(12, 30, 1))
				.perfection(Bonus.area(0.25D))));

		// 403 · Sungai Tongtian Membeku · NS · Misterius-T · KENDALI · FEAT kills_with(any, drowned/guardian, snowy biome, 30), COMP 35 · Mrd Y
		sink.add(done(art("tongtian_river_freeze", 403, Dao.ICE)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.KENDALI).realm(Realm.NASCENT_SOUL)
				.pose(Pose.KNEEL_STRIKE).pose(Phase.EXTRA0, Pose.SEAL)
				.route(Route.feat(Feat.killsWith(null, SNOWY, List.of("minecraft:drowned", "minecraft:guardian"), 30))).comprehension(35)
				.qi(80).cooldown(25.0D)
				.step(at(0).shape(wave().r1(8.0D, 22.0D).speed(1.0D)).hit(freeze(80)))
				// @80 RECALL > DMG 2.5 (vs AQUATIC ×1.5) + slowness III 60t (shatter).
				.step(at(80).shape(recall()).hit(damage(2.5D).vs(Versus.AQUATIC, 1.5D).shatter(), eff(Eff.SLOWNESS, 3, 60)).phase(0))
				// Flat packed-ice tiles and spreading snowflakes (displays and particles, never real blocks), crack lines, shards.
				.fx(Phase.CAST, mist(0xCFF4FF).particle(ParticleTypes.SNOWFLAKE).count(20))
				.fx(Phase.TRAVEL, giantBlock(Blocks.PACKED_ICE.defaultBlockState()).blockSize(3.0F, 0.1F, 3.0F).n(8).minTier(1),
						shockRing(0xCFF4FF).particle(ParticleTypes.SNOWFLAKE).count(40), cracksAlways(0xBFE8FF))
				.fx(Phase.EXTRA0, shards(0xDFF6FF).count(40))
				.sound(Phase.EXTRA0, SoundEvents.GLASS_BREAK, 1.0F, 0.8F)
				.sound(Phase.EXTRA0, SoundCue.of(SoundEvents.GLASS_BREAK, 1.0F, 1.0F).delay(2))
				.sound(Phase.EXTRA0, SoundCue.of(SoundEvents.GLASS_BREAK, 1.0F, 1.3F).delay(4))
				// the frozen ground shatters in a ring around the caster with the @80 shatter
				.kehancuran(DShape.ring(10, 5, 1))
				.perfection(Bonus.area(0.25D))));

		// 404 · Api Es Qianlan · DT · Bumi-R · SERANG · BOOK frost_walker, COMP 45; PAV · Mrd Y
		sink.add(done(art("qianlan_ice_flame", 404, Dao.ICE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.SERANG).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.PALM_THRUST)
				.route(Route.book("minecraft:frost_walker"), Route.pav()).comprehension(45).flag(PAVILION)
				.qi(110).cooldown(20.0D)
				.step(at(0).shape(beam().length(24.0D, 48.0D).halfWidth(1.0D)).hit(damage(2.5D), freeze(60), dot(0.4D, 6, DotKind.COLD))
						.onKill(at(0).shape(nova().radius(3.0D).at(Anchor.HIT)).hit(damage(1.0D))))
				// A deep-blue cold flame.
				.fx(Phase.CAST, impact(0x2050FF).particle(ParticleTypes.SOUL_FIRE_FLAME).onCaster().above(1.2F).count(10).ticks(6))
				.fx(Phase.TRAVEL, beamCore(0x2050FF).particle(ParticleTypes.SOUL_FIRE_FLAME).count(40),
						beamCore(0xCFF4FF).particle(ParticleTypes.SNOWFLAKE).count(16).detail(), mist(0xBFE8FF).onTarget().count(10).ticks(16).extra())
				.fx(Phase.HIT, shards(0x80B0FF).count(10))
				.sound(Phase.TRAVEL, SoundEvents.SOUL_ESCAPE, 1.2F, 0.8F)
				.sound(Phase.CAST, SoundEvents.GLASS_BREAK, 0.8F, 1.2F)
				.kehancuran(DShape.tunnel(1, 16))
				.perfection(Bonus.area(0.25D))));

		// 405 · Kebanggaan Dua Kaisar Es dan Salju · DT · Bumi-T · PANGGIL · FEAT kills_with(any, stray, frozen biome, 50) · Mrd Y(½)
		sink.add(done(art("ice_snow_emperors_pride", 405, Dao.ICE)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.PANGGIL).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.SUMMON_RAISE)
				.route(Route.feat(Feat.killsWith(null, FROZEN, List.of("minecraft:stray"), 50)))
				.qi(140).cooldown(50.0D)
				// SUMMON 2 (200t): the Ice Emperor attacks every 10t (range 12) > DMG .7 ...
				.step(at(0).self(summon("ice_emperor", 1, 200).interval(10).range(12.0D)
						.attack(at(0).shape(all().at(Anchor.SUMMON).radius(12.0D).max(1.0D)).hit(damage(0.7D)))))
				// ... the Snow Emperor's DOMAIN(r 10→18, pulse 20) > DMG .4 + slowness III.
				.step(at(0).self(summon("snow_emperor", 1, 200)).shape(domain().radius(10.0D, 18.0D).duration(200).pulse(20))
						.hit(damage(0.4D), eff(Eff.SLOWNESS, 3, 30)))
				// @200 NOVA(r 10→18) > DMG 3 + FRZ 60t.
				.step(at(200).shape(nova().radius(10.0D, 18.0D)).hit(damage(3.0D), freeze(60)).phase(0))
				// A scorpion silhouette, a snow empress of particles, a blizzard.
				.fx(Phase.CAST, beast(FxLayer.BeastKind.SCORPION, 0xBFEFFF).count(60), beast(FxLayer.BeastKind.DEITY, 0xF0F8FF).count(60),
						mist(0xE0F0FF).particle(ParticleTypes.SNOWFLAKE).count(50))
				.fx(Phase.PULSE, mist(0xE0F0FF).count(12).extra())
				.fx(Phase.EXTRA0, shards(0xE8F8FF).count(60))
				.fx(Phase.PHANTOM, shards(0xBFEFFF).onTarget().count(10).ticks(6), arc(0xDFF6FF).count(8).ticks(6).detail())
				.sound(Phase.CAST, SoundEvents.SNOW_GOLEM_AMBIENT, 1.2F, 0.6F)
				.sound(Phase.CAST, SoundEvents.GLASS_BREAK, 1.0F, 0.8F)
				// the closing @200 nova shatters the ground in a ring around the caster
				.kehancuran(DShape.ring(12, 5, 1).at(200))
				.perfection(Bonus.durationTicks(60))));

		// 406 · Formasi Es Dingin · DU · Bumi-R · AREA · PAV; BOOK frost_walker, COMP 60 · Mrd Y
		sink.add(done(art("cold_ice_formation", 406, Dao.ICE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.pose(Pose.SEAL).pose(Phase.EXTRA0, Pose.SKY_RAISE)
				.route(Route.pav(), Route.book("minecraft:frost_walker")).comprehension(60).flag(PAVILION)
				.qi(220).cooldown(60.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(32.0D).radius(8.0D, 18.0D)).hit(damage(2.0D), launch(0.5D), bind(BindKind.ROOT, 30)))
				// @20 the same zone falls from the sky (20 b): the ice mountain.
				.step(at(20).shape(zone(Anchor.AIM).range(32.0D).radius(8.0D, 18.0D).fromSky(20.0D)).hit(damage(5.0D), freeze(80)).phase(0))
				// A black banner, angled ice spikes, then a packed-ice mountain (12→20 b) falling.
				.fx(Phase.CAST, model("formation_banner_black").scale(3.0F).ticks(60).facing(FxLayer.Facing.UPRIGHT), shards(0x9FD8FF).count(20))
				.fx(Phase.IMPACT, giantBlock(Blocks.BLUE_ICE.defaultBlockState()).blockSize(0.6F, 4.0F, 0.6F).n(8).minTier(1),
						giantBlock(Blocks.PACKED_ICE.defaultBlockState()).blockSize(0.5F, 3.0F, 0.5F).n(8).minTier(1).extra())
				// the ice mountain falls on the sky zone's pre-impact TRAVEL and stands on its EXTRA0
				.fx(Phase.TRAVEL, model("ice_mountain").scale(12.0F).fall(20.0F, 10.0F).ticks(11).facing(FxLayer.Facing.UPRIGHT), mist(0xDFF6FF).inSky().count(16).ticks(10).detail())
				.fx(Phase.EXTRA0, model("ice_mountain").scale(12.0F).ticks(40).facing(FxLayer.Facing.UPRIGHT),
						shards(0xDFF6FF).count(60), shake(0.7F, 20))
				.sound(Phase.CAST, SoundEvents.GLASS_BREAK, 1.0F, 0.7F)
				.sound(Phase.EXTRA0, SoundEvents.ANVIL_LAND, 1.4F, 0.5F)
				.sound(Phase.EXTRA0, SoundEvents.GLASS_BREAK, 1.4F, 0.6F)
				.kehancuran(DShape.bowl(8))
				.perfection(Bonus.area(0.2D))));

		// 407 · Bulan Patah Menyapu Bayangan · DU · Bumi-T · GERAK · PAV · Mrd Y
		sink.add(done(art("broken_moon_shadow", 407, Dao.ICE)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.GERAK).realm(Realm.DAO_UNION)
				.pose(Pose.DASH_LEAN).pose(Phase.IMPACT, Pose.SWORD_SLASH)
				.route(Route.pav()).flag(PAVILION)
				.qi(100).cooldown(25.0D)
				// "CHARGES 3 (window 100t)": three recasts within the window, written as three stages so the end of the 3rd can act.
				.stages(3, 100)
				// DASH 12→20; each AFTERIMAGE @20 NOVA(r3) > DMG 1.5 + FRZ 40t.
				.step(at(0).stage(1).self(dash(12.0D, 20.0D)))
				.step(at(20).stage(1).shape(nova().radius(3.0D).at(Anchor.AFTERIMAGE)).hit(damage(1.5D), freeze(40)))
				.step(at(0).stage(2).self(dash(12.0D, 20.0D)))
				.step(at(20).stage(2).shape(nova().radius(3.0D).at(Anchor.AFTERIMAGE)).hit(damage(1.5D), freeze(40)))
				.step(at(0).stage(3).self(dash(12.0D, 20.0D)))
				.step(at(20).stage(3).shape(nova().radius(3.0D).at(Anchor.AFTERIMAGE)).hit(damage(1.5D), freeze(40)))
				// After the 3rd: SELF invisibility 60t + NOVA(r16) > UNTARGET.
				.step(at(4).stage(3).phase(0).self(buff(BuffKind.INVISIBILITY, 1, 60)).shape(nova().radius(16.0D)).hit(untarget()))
				// Crescent-moon shards and a silver-blue afterimage.
				.fx(Phase.CAST, shards(0xE0ECFF).count(16))
				.fx(Phase.TRAVEL, afterimage(0xC8DCFF), shards(0xE0ECFF).count(10).detail())
				.fx(Phase.EXTRA0, mist(0xC8DCFF).count(20))
				.fx(Phase.TRAVEL, crescentFly(0xC8DCFF).vertical().count(20).ticks(6))
				.fx(Phase.IMPACT, crescentFly(0xE0ECFF).count(16).ticks(6), shards(0xE0ECFF).count(14).ticks(8))
				.sound(Phase.CAST, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.0F)
				// a shatter pit at each afterimage that bursts on a foe (≤ 3 per cast, one per dash)
				.kehancuran(DShape.scatter(2))
				.perfection(Bonus.cooldown(0.2D))));

		// 408 · Api Dingin Tulang Roh · IA T1 · Langit-R · AREA · ITEM heavenly_flame_seed:bone_chilling; INH(ICE) · Mrd Y
		sink.add(done(art("bone_chilling_flame", 408, Dao.ICE)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.item("heavenly_flame_seed:bone_chilling"), Route.inh("ice"))
				.qi(280).cooldown(70.0D)
				.step(at(0).shape(wave().r1(20.0D, 36.0D).speed(1.5D)).hit(damage(3.0D), freeze(80)).self(costMod(Dao.ICE, 0.7D, 1.0D, 200)))
				// A pale cold flame ring with ghost wisps.
				.fx(Phase.CAST, shell(0xF0F8FF).particle(ParticleTypes.SOUL_FIRE_FLAME).count(20))
				.fx(Phase.TRAVEL, shockRing(0xF0F8FF).particle(ParticleTypes.SOUL_FIRE_FLAME).count(60),
						shockRing(0xE0F0FF).particle(ParticleTypes.SCULK_SOUL).count(20).detail())
				.sound(Phase.CAST, SoundEvents.SOUL_ESCAPE, 1.4F, 0.6F)
				.kehancuran(DShape.ring(16, 6, 1))
				.perfection(Bonus.area(0.2D))));

		// 409 · Dingin Tak Tertandingi · IA T3 · Langit-R · SERANG · BOOK frost_walker, COMP 80 · Mrd Y
		sink.add(done(art("unparalleled_chill", 409, Dao.ICE)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.SERANG).realm(Realm.IMMORTAL_ASCENSION, 3)
				.pose(Pose.SWORD_THRUST)
				.route(Route.book("minecraft:frost_walker")).comprehension(80)
				.qi(260).cooldown(40.0D)
				.step(at(0).shape(volley().seek().count(1.0D).range(40.0D)).hit(damage(5.0D))
						.onHit(at(0).shape(nova().radius(6.0D, 10.0D).at(Anchor.HIT)).hit(damage(3.0D).shatter(), freeze(80))))
				// A crystal blade bursting into hundreds of shards.
				.fx(Phase.CAST, shards(0xDFF6FF).count(12), mist(0xE8F8FF).count(10).detail())
				.fx(Phase.TRAVEL, model("crystal_blade").scale(3.0F).slide(10.0F).ticks(12).facing(FxLayer.Facing.BLADE), trail(0xDFF6FF).count(12))
				.fx(Phase.HIT, shards(0xE8F8FF).count(200), shake(0.4F, 10))
				.sound(Phase.HIT, SoundEvents.GLASS_BREAK, 1.4F, 0.9F)
				// the blade bursts into shards: a shatter crater where it strikes
				.kehancuran(DShape.bowl(5))
				.perfection(Bonus.area(0.25D))));

		// 410 · Seni Ilahi Akhir Beku · IA T5 · Langit-M · WILAYAH · FEAT meditate_at(frozen peaks or ice spikes, 20 min), COMP 80 · Mrd N
		sink.add(done(art("frozen_end_divine_art", 410, Dao.ICE)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.WILAYAH).realm(Realm.IMMORTAL_ASCENSION, 5)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.feat(Feat.meditateAt(Cond.any(Cond.biome("minecraft:frozen_peaks"), Cond.biome("minecraft:ice_spikes")), 20)))
				.comprehension(80).flag(NO_TEACH)
				.qi(380).cooldown(120.0D)
				// DOMAIN(r 24→32, 240t, pulse 20) > DMG 1.5 + movement −95%.
				.step(at(0).shape(domain().radius(24.0D, 32.0D).duration(240).pulse(20)).hit(damage(1.5D), attrPct(AttrKind.MOVEMENT_SPEED, -0.95D, 30)))
				// @240 ALL(domain) > EXEC .25 (bosses DMG 10).
				.step(at(240).shape(all().ofArea().max(32.0D)).hit(exec(0.25D)).when(Cond.TARGET_BOSS, 1.0D, damage(10.0D)).phase(0))
				// Snowflakes hang still inside the dome (a lasting snowflake mist) and an aurora ring glows along its edge.
				.fx(Phase.CAST, dome(0xF0F8FF).count(60), mist(0xFFFFFF).particle(ParticleTypes.SNOWFLAKE).count(60).ticks(240),
						runeCircle(0x80FFD0).color2(0xB080FF).count(60).ticks(240).detail())
				.fx(Phase.PULSE, mist(0xF0F8FF).count(12).extra())
				.fx(Phase.EXTRA0, shards(0xF0F8FF).count(80), shake(0.3F, 14))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.5F)
				// the domain follows the caster: its first pulse shatters a ring of ground around them
				.kehancuran(DShape.ring(14, 6, 1))
				.perfection(Bonus.area(0.2D))));

		// 411 · Segel Embun Beku Phoenix Es · IA T9 · Langit-T · PAMUNGKAS · FEAT kills_with(ICE, elder_guardian, 3) · Mrd Y
		sink.add(done(art("ice_phoenix_frost_seal", 411, Dao.ICE)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 9)
				.pose(Pose.SKY_RAISE).pose(Phase.EXTRA0, Pose.SEAL)
				.route(Route.feat(Feat.killsWith(Dao.ICE, Cond.NONE, List.of("minecraft:elder_guardian"), 3)))
				.qi(500).cooldown(180.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(64.0D).radius(20.0D, 24.0D).fromSky(40.0D)).hit(damage(4.0D), freeze(120)))
				.step(at(40).shape(recall()).hit(damage(6.0D).shatter()).phase(0))
				// A 64-b hexagram snowflake glyph (drawn at the glyph cap, scale 40) descending from a cloud disc.
				.fx(Phase.CAST, cloudDisc(0xCFE8FF).above(40.0F),
						FxLayer.of(FxLayer.Template.GLYPH, 0xDFF6FF).text("❄").scale(40.0F).fall(40.0F, 20.0F))
				.fx(Phase.IMPACT, shards(0xDFF6FF).count(80), shake(0.6F, 18))
				.fx(Phase.EXTRA0, shards(0xFFFFFF).count(100))
				.fx(Phase.TRAVEL, model("ice_phoenix").scale(10.0F).fall(40.0F, 20.0F).ticks(22))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.5F)
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.5F, 1.0F)
				.kehancuran(DShape.disc(10, 2))
				.perfection(Bonus.area(0.2D))));

		// 412 · Turunnya Phoenix Es · TI · Abadi-R · PAMUNGKAS · FEAT first_kill(ENDER_DRAGON, ICE) · Mrd Y★27
		sink.add(done(art("ice_phoenix_descent", 412, Dao.ICE)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SUMMON_RAISE)
				.route(Route.feat(Feat.firstKill("minecraft:ender_dragon", Dao.ICE)))
				.teachStars(27).flag(NO_ECHO)
				.qiPct(55.0D, 700).cooldown(360.0D)
				// SUMMON 1 colossal phoenix (240t); SELF immune to slowness and freezing + EMP(ICE +50%) 240t.
				.step(at(0).self(summon("ice_phoenix", 1, 240).mode(SummonMode.ANCHOR), buff(BuffKind.IMMUNE_SLOW, 1, 240),
						empower(Dao.ICE, 0.5D, 240)))
				// 3 dives at DENSEST clusters every 60t = FRONT(L40, w4) pressed down from the sky onto the densest point > DMG 6 + FRZ 80t.
				.step(at(60).repeat(3, 60).shape(front().length(40.0D).width(4.0D).fromSky(30.0D).from(Anchor.densest(1, 32.0D)))
						.hit(damage(6.0D), freeze(80)))
				// @240 NOVA(r 20→32) > DMG 10.
				.step(at(240).shape(nova().radius(20.0D, 32.0D)).hit(damage(10.0D)).phase(0))
				// A 40-b ice phoenix across the sky; shards.
				.fx(Phase.CAST, skySilhouette(FxLayer.BeastKind.BIRD, 0xCFEFFF).scale(40.0F), beast(FxLayer.BeastKind.BIRD, 0xCFEFFF).size(4.0F).count(60))
				.fx(Phase.TRAVEL, trail(0xDFF6FF).particle(ParticleTypes.SNOWFLAKE).count(20), shards(0xDFF6FF).count(30))
				.fx(Phase.EXTRA0, shards(0xFFFFFF).count(120), shake(0.7F, 24))
				.fx(Phase.TRAVEL, model("ice_phoenix").scale(12.0F).slide(10.0F).ticks(12))
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 1.6F)
				.sound(Phase.CAST, SoundEvents.GLASS_BREAK, 1.2F, 0.8F)
				.kehancuran(DShape.bowl(8))
				// Phase 10 (DESIGN 28.12): a six-fold frost ring under each dive; an ice quill stays planted
				.dampak(Dampak.builder().mark(Dampak.Mark.MULTI).ride(Dampak.Ride.SHORT).climaxAt(240)
						.frames(0xFFFFFF, 0xCFEFFF).silence(3).debris(Dampak.Debris.BURST).ring(Dampak.Ring.FROST).palette(Dampak.Palette.ICE)
						.monument("ice_spear", 8.0F, 60, Dampak.MonumentPose.PLANTED).build())
				.perfection(Bonus.durationTicks(60))));

		// 413 · Membekukan Gunung Qi · Dewa · Dewa-R · PAMUNGKAS · GATE I (prereq ice_phoenix_descent L3) · Mrd Y★36
		sink.add(done(art("freeze_mount_qi", 413, Dao.ICE)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.ARMS_SPREAD).climax(Phase.EXTRA1)
				.gate(TechniqueSpec.Gate.DEWA_I).prereq("ice_phoenix_descent", 3).dewaStars(36).teachStars(36)
				.qiPct(75.0D).cooldown(900.0D)
				.step(at(0).shape(domain().radius(40.0D).duration(400).pulse(40)).hit(damage(2.0D), eff(Eff.SLOWNESS, 3, 50)))
				.step(at(200).shape(all().radius(40.0D).max(64.0D)).hit(freeze(160), damage(8.0D)).phase(0))
				.step(at(400).shape(recall()).hit(damage(12.0D).shatter()).phase(1))
				// A whiteout and an ice vignette (96-b visual); ice cubes on the frozen targets (≤24).
				.fx(Phase.CAST, mist(0xF8FCFF).radius(48.0F).count(120), vignette(0xCFEFFF, 60))
				.fx(Phase.PULSE, mist(0xF8FCFF).count(20).extra())
				.fx(Phase.HIT, giantBlock(Blocks.ICE.defaultBlockState()).scale(2.0F))
				.fx(Phase.EXTRA0, shards(0xDFF6FF).count(80))
				.fx(Phase.EXTRA1, shards(0xFFFFFF).count(160), shake(0.5F, 20))
				// the first pulse cracks the frozen ground around the caster; the @400 shatter breaks a deeper ring
				.kehancuran(DShape.ring(16, 6, 1), DShape.ring(20, 8, 3).at(400))
				// Phase 10 (DESIGN 28.12): an ice ring round the caster, the snowflake reaches each frozen foe, frost spikes burst at the recall
				.dampak(Dampak.builder().mark(Dampak.Mark.RING_ONLY).ride(Dampak.Ride.FULL, 80, Dampak.Vista.GUNUNG_ES).climaxAt(400)
						.frames(Dampak.INV, 0xFFFFFF, 0xCFEFFF).silence(6).debris(Dampak.Debris.BURST).ring(Dampak.Ring.FROST).palette(Dampak.Palette.ICE)
						.sigil(SigilPattern.SNOWFLAKE, 40.0F).ignite(Phase.EXTRA0)
						.monument("ice_lotus", 6.0F, 90, Dampak.MonumentPose.UPRIGHT).build())
				.perfection(Bonus.area(0.2D))));
	}

	/** Builds the spec with the default level-4 bonus of 22.6 when the row names none. */
	private static TechniqueSpec done(TechniqueSpec.Builder builder) {
		TechniqueSpec probe = builder.build();
		if (!probe.peak().isEmpty()) {
			return probe;
		}
		builder.peak(probe.echoAllowed() ? Bonus.echo(PEAK_ECHO) : Bonus.duration(PEAK_DURATION));
		return builder.build();
	}

	private IceArts() {
	}
}

package dev.kultivator.technique.arts;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec.Flag;
import dev.kultivator.data.TechniqueSpec.Gate;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.special.EarthTravel;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Payload.AttrKind;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BlinkMode;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Tubuh / Body catalogue (DESIGN 22.9.5, net ids 600-612): 13 specs (Misterius 3, Bumi 5, Langit 3, Abadi 1, Dewa 1).
 * Builder DSL only (22.2.5); fists, quakes and footprints hurt monsters only, and the KH shapes are carved only by
 * {@code Kehancuran.submitSelf} while the player's own switch is on (22.11). Numbers are the catalogue's
 * ({@code b→c} = base → spec cap). The level 3 {@code perfection} bonuses are game-design picks of 22.6; the level 4
 * {@code peak} is ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise. {@code worldwalking} travels through the
 * {@link EarthTravel} special.
 */
public final class BodyArts {

	/** Body bronze (the Dao colour), gold, earth, stone, dragon scale. */
	private static final int BRONZE = 0xD9A05B;
	private static final int GOLD = 0xFFD54A;
	private static final int EARTH = 0x8B6B4A;
	private static final int STONE = 0x9A8A70;
	private static final int DRAGON = 0x22382A;
	private static final int HEX_RED = 0xD02020;
	private static final int STAR_SILVER = 0xE0E8FF;

	/** {@code "Reinkarnasi"} bursts of six_paths_fist chain through kills, at most this deep (22.9.5: "≤8 bursts"). */
	public static final int REINCARNATION_BURSTS = 8;

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(EarthTravel.ID, new EarthTravel());
		ParticleOptions flash = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF0D0);
		ParticleOptions dragonBreath = PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);

		// 600 · Ledakan Delapan Kutub (Battle Through the Heavens)
		sink.add(art("octane_blast", 600, Dao.BODY)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.PUNCH).pose(Phase.EXTRA0, Pose.PUNCH).pose(Phase.EXTRA1, Pose.PUNCH)
				.route(Route.dao())
				.flag(Flag.TEACHABLE)
				.qi(25).cooldown(6.0D)
				// x8/2: seven light blows, then the 8th.
				.step(at(0).repeat(7, 2).shape(strike().range(4.5D).arc(35).max(3)).hit(damage(0.15D)).phase(0))
				.step(at(14).shape(strike().range(4.5D).arc(35).max(3)).hit(damage(0.9D), knock(1.0D)).phase(1)
						// "if all 8 hit one target": the eight layers burst inside it.
						.onHit(at(0).only(Cond.hitBefore(7)).shape(nova().radius(3, 8).at(Anchor.HIT)).hit(damage(0.5D)).phase(2)))
				.fx(Phase.CAST, fist(GOLD).size(0.6F).count(20).ticks(6), shockRing(GOLD).particle(ParticleTypes.SWEEP_ATTACK).size(0.6F).count(6).detail())
				.fx(Phase.EXTRA0, shockRing(GOLD).particle(ParticleTypes.SWEEP_ATTACK).size(0.6F).count(6), shockRing(GOLD).size(0.5F).count(16).detail())
				.fx(Phase.EXTRA1,
						impact(0xFFF0D0).particle(flash).count(1),
						impact(GOLD).particle(ParticleTypes.EXPLOSION).count(3),
						impact(0xFFFFFF).particle(ParticleTypes.CRIT).count(20).detail(),
						shake(0.1F, 6))
				.fx(Phase.EXTRA1, model("golden_fist").scale(2.0F).grow(0.5F, 3.0F).ticks(10).facing(FxLayer.Facing.FACE_FORWARD))
				// the inner burst after seven landed blows: a golden shell, a flash and eight small rings
				.fx(Phase.EXTRA2, shell(GOLD).radius(3.0F).count(30).ticks(8), flash(0xFFF0D0, 0.15F, 3), shockRing(GOLD).radius(1.5F).n(8).count(24).ticks(8).detail())
				.sound(Phase.CAST, SoundEvents.MACE_SMASH_AIR, 0.6F, 0.8F)
				.sound(Phase.EXTRA0, SoundEvents.MACE_SMASH_AIR, 0.6F, 1.3F)
				.sound(Phase.EXTRA1, SoundEvents.MACE_SMASH_AIR, 0.8F, 1.6F)
				.sound(Phase.EXTRA1, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 1.0F)
				// "SPHERE 2 (final target)": on the struck foe (the first hit of the blows lands on the same target).
				.kehancuran(DShape.sphere(2))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 601 · Langkah Penjelajah Bumi (Coiling Dragon; Investiture of the Gods) — special earth_travel
		sink.add(art("worldwalking", 601, Dao.BODY)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.GERAK).realm(Realm.NASCENT_SOUL)
				.pose(Pose.KNEEL_STRIKE)
				.route(Route.dao())
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.special(EarthTravel.ID)
				.qi(40).cooldown(10.0D)
				.step(at(0).self(buff(BuffKind.INVISIBILITY, 1, EarthTravel.TRAVEL_TICKS + 4), buff(BuffKind.NO_FALL, 1, 40)))
				.step(at(EarthTravel.TRAVEL_TICKS).shape(pillar(Anchor.END).radius(3, 6).height(6).delay(0))
						.hit(damage(1.5D), launch(1.0D), eff(Eff.SLOWNESS, 2, 40)))
				.fx(Phase.CAST, mist(EARTH).size(1.2F).count(30).ticks(10), shockRing(EARTH).size(0.6F).count(16).detail())
				// The furrow: the earth trail plus line cracks that shed FALLING_DUST of the ground block (read client-side).
				.fx(Phase.TRAVEL, trail(EARTH).count(40).ticks(EarthTravel.TRAVEL_TICKS), cracksAlways(0x6B5A44).line().count(30).ticks(20).detail())
				.fx(Phase.IMPACT,
						giantItem(Items.POINTED_DRIPSTONE).scale(3.0F).rise(3, 4).n(6).minTier(1).ticks(30),
						shockRing(EARTH).size(1.2F).count(30),
						column(STONE).size(0.8F).count(24).ticks(8).detail(),
						shake(0.1F, 6))
				.sound(Phase.CAST, SoundEvents.WARDEN_DIG, 1.0F, 1.2F)
				.sound(Phase.TRAVEL, SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 0.7F)
				.sound(Phase.IMPACT, SoundEvents.WARDEN_EMERGE, 0.8F, 1.4F)
				.sound(Phase.IMPACT, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0F, 0.8F)
				.kehancuran(DShape.trenchPath(2, 2).nat().ahead(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 602 · Denyut Nadi Dunia (Coiling Dragon)
		Payload.Hit[] pulse = {damage(0.6D).pierce(), launch(0.5D), eff(Eff.SLOWNESS, 3, 60)};
		sink.add(art("throbbing_pulse", 602, Dao.BODY)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.pose(Pose.STOMP).pose(Phase.TRAVEL, Pose.STOMP)
				.route(Route.comp(30), Route.book("minecraft:blast_protection"))
				.flag(Flag.TEACHABLE)
				.qi(70).cooldown(22.0D)
				.step(at(0).self(shield(0.15D, 100)))
				// x3/10 WAVE(r1 6 / 10 / 14 → 36): two rings, then the third ...
				.step(at(0).repeat(2, 10).shape(wave().r1(6, 36).speed(1.5D).perRepeat(4)).hit(pulse))
				// ... which shatters from within whatever both earlier rings struck.
				.step(at(20).shape(wave().r1(14, 36).speed(1.5D)).hit(pulse).when(Cond.hitBefore(2), 1.0D, damage(0.6D).pierce()))
				.fx(Phase.CAST, shockRing(STONE).size(0.8F).count(20), shieldShell(BRONZE).count(16).ticks(100).detail())
				.fx(Phase.TRAVEL,
						shockRing(STONE).size(1.2F).count(50),
						giantBlock(Blocks.CRACKED_STONE_BRICKS.defaultBlockState()).blockSize(1.5F, 0.5F, 1.5F).n(8).minTier(1).ticks(30),
						shake(0.2F, 8))
				.sound(Phase.CAST, SoundEvents.WARDEN_STEP, 1.0F, 0.5F)
				.sound(Phase.TRAVEL, SoundEvents.WARDEN_STEP, 1.0F, 0.5F)
				.sound(Phase.TRAVEL, SoundEvents.MACE_SMASH_GROUND, 0.8F, 0.9F)
				.kehancuran(DShape.disc(8, 2).nat())
				.perfection(Bonus.extraWave(1))
				.peak(Bonus.echo(0.4D))
				.build());

		// 603 · Palu Jubah Angin Kacau (Soul Land)
		sink.add(art("disorderly_cloak_hammer", 603, Dao.BODY)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.OVERHEAD_SLAM).pose(Phase.IMPACT, Pose.OVERHEAD_SLAM).pose(Phase.EXTRA0, Pose.OVERHEAD_SLAM)
				.route(Route.book("minecraft:density"), Route.pav())
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.NO_ECHO)
				// CHAN 54t/6 (interrupt .15): the channel repeats each step every 6 ticks (x9/6), k = the blow index for the
				// growth; the 9th blow at tick 48 is the big one.
				.channel(54, 6, 0.15D)
				.qi(120).cooldown(35.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(20).radius(3)).hit(damage(0.2D)).grow(0.15D))
				.step(at(48).shape(zone(Anchor.AIM).range(20).radius(6, 14)).hit(damage(2.4D), launch(0.9D)).phase(0))
				.fx(Phase.CAST,
						fist(BRONZE).size(0.8F).count(16).detail())
				.fx(Phase.IMPACT, model("dharma_hammer").scale(4.0F).fall(8.0F, 3.0F).ticks(8).facing(FxLayer.Facing.UPRIGHT), shockRing(STONE).size(0.8F).count(24), cracksAlways(0x5A4A3A).count(10).ticks(16).detail(), shake(0.1F, 4))
				.fx(Phase.EXTRA0,
						impact(0xFFF0D0).particle(flash).count(1),
						impact(0xFFE0A0).particle(ParticleTypes.EXPLOSION_EMITTER).count(1),
						impact(0xFFFFFF).particle(ParticleTypes.SONIC_BOOM).count(3),
						shockRing(STONE).size(2.0F).count(50).detail(),
						shake(0.4F, 12))
				.fx(Phase.EXTRA0, model("dharma_hammer").scale(9.0F).fall(14.0F, 4.0F).ticks(20).facing(FxLayer.Facing.UPRIGHT), flash(0xFFF0D0, 0.2F, 4))
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 0.6F, 0.6F)
				.sound(Phase.IMPACT, SoundEvents.ANVIL_LAND, 0.6F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.9F)
				.sound(Phase.EXTRA0, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.0F)
				// "SPHERE 4 (9th blow, 1 below the surface)".
				.kehancuran(DShape.sphere(4).at(48))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 604 · Seni Raja Cerah (A Record of a Mortal's Journey)
		sink.add(art("bright_king_art", 604, Dao.BODY)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.LINDUNG).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.SEAL)
				.route(Route.comp(30), Route.book("minecraft:protection"))
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(100).cooldown(60.0D)
				// "st2 or end: release NOVA(r 6→14) > DMG 2 × stored (≤4)": the COUNTER releases its store when the 20 s end
				// (the runner has no recast-release of a counter yet, so the "st2" early release is not written; see report).
				.step(at(0).self(
						dr(0.35D, 0.6D, Payload.Facing.ALL, 400),
						buff(BuffKind.ATTACK_DAMAGE, 0.4D, 400),
						buff(BuffKind.KNOCKBACK_RESISTANCE, 1, 400),
						counter(400, 0.25D, 4, Payload.Store.DAMAGE_TAKEN)
								.release(at(0).shape(nova().radius(6, 14)).hit(damage(0.0D).perStored(2.0D), knock(1.5D)).phase(0))))
				.fx(Phase.CAST,
						shieldShell(GOLD).count(30).ticks(400),
						runeCircle(GOLD).particle(ParticleTypes.ENCHANT).vertical().onCaster().above(1.2F).size(0.8F).count(20).ticks(400).detail())
				.fx(Phase.EXTRA0,
						impact(0xFFF0D0).particle(flash).count(1),
						shell(0xFFFFFF).particle(ParticleTypes.END_ROD).size(2.0F).count(30),
						shake(0.25F, 8))
				.fx(Phase.EXTRA6, shockRing(GOLD).onCaster().radius(1.2F).count(12).ticks(6), impact(0xFFF0C0).particle(ParticleTypes.END_ROD).onCaster().count(6).ticks(6))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.BELL_RESONATE, 1.0F, 1.0F)
				.kehancuran(DShape.disc(4, 1).nat().atRelease())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 605 · Jalan Agung Buddha (Against the Gods)
		sink.add(art("great_way_of_buddha", 605, Dao.BODY)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.PULIH).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.PRAY)
				.route(Route.comp(60))
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(110).cooldown(90.0D)
				.step(at(0).self(
						heal(0.3D, 0.5D).over(600),
						hook(Hook.onHurt(at(0).self(heal(0.1D)).phase(1)).minShare(0.1D).icd(40).ticks(600))))
				// "onEnd" of the 600-tick healing.
				.step(at(600).self(cleanse(), buff(BuffKind.HEALTH_BOOST, 1, 6000).stack(3)).phase(0))
				.fx(Phase.CAST,
						runeCircle(0xFFF0C0).particle(ParticleTypes.END_ROD).variant(FxLayer.Variant.VERTICAL).onCaster().above(2.0F).size(0.6F).count(16).ticks(600),
						healRise(0xFFE8A0).onCaster().count(20).ticks(600),
						// A soft stupa outline: a dust shell dome with a slim column as its spire.
						shell(0xFFE8B0).size(2.5F).count(36).ticks(30).detail(),
						column(0xFFE8B0).size(1.5F).count(30).ticks(30).detail())
				.fx(Phase.EXTRA0, healRise(0xFFF0C0).size(1.5F).count(30), shell(GOLD).count(16).detail())
				.fx(Phase.CAST, model("stupa").onCaster().above(2.4F).scale(1.2F).grow(0.2F, 20.0F).ticks(200).facing(FxLayer.Facing.FACE_FORWARD))
				.fx(Phase.EXTRA1, healRise(0xFFF0C0).onCaster().count(16).ticks(16), shell(GOLD).onCaster().radius(1.4F).count(14).ticks(8).detail())
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F)
				.perfection(Bonus.selfPayload(buff(BuffKind.REGENERATION, 1, 200)))
				.peak(Bonus.duration(0.25D))
				.build());

		// 606 · Wujud Prajurit Darah Naga (Coiling Dragon)
		sink.add(art("dragonblood_warrior", 606, Dao.BODY)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.UBAH).realm(Realm.DAO_UNION)
				.pose(Pose.ROAR).pose(Phase.EXTRA0, Pose.SWORD_SLASH)
				.route(Route.feat(Feat.firstKill("minecraft:ender_dragon", null)), Route.inh(Dao.BODY.id()))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(180).cooldown(120.0D)
				.step(at(0).shape(nova().radius(5, 12)).hit(damage(2.0D), knock(1.5D))
						.self(
								transform("dragonblood", 600).scale(1.3D).with(
										buff(BuffKind.ARMOR, 10, 600),
										buff(BuffKind.ATTACK_DAMAGE, 6, 600),
										buff(BuffKind.SPEED, 1, 600)),
								hook(Hook.onMelee(at(0).shape(cone().length(4).halfAngle(40)).hit(damage(0.8D)).phase(0)).icd(10))))
				.fx(Phase.CAST,
						shieldShell(DRAGON).count(30).ticks(600),
						shell(0x7A3AB0).particle(dragonBreath).onCaster().count(16).ticks(600).detail(),
						shockRing(0x3A5A3A).size(1.5F).count(30),
						shake(0.25F, 8))
				.fx(Phase.HIT, arc(DRAGON).count(14).ticks(5))
				// the dragon tail sweeps with each hooked blow
				.fx(Phase.EXTRA0, arc(DRAGON).size(2.0F).count(20).ticks(6), arc(0x7A3AB0).mirrored().count(10).ticks(6).detail())
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 1.0F)
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.0F)
				.kehancuran(DShape.ring(7, 4, 1))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 607 · Tinju Enam Jalan Reinkarnasi (Shrouded the Heavens): six fists, one colour each.
		Step reincarnation = reincarnation(REINCARNATION_BURSTS);
		DyeColor[] fistColors = {DyeColor.YELLOW, DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.RED, DyeColor.GRAY, DyeColor.PURPLE};
		// the six paths: deva, human, animal, asura, hungry ghost, hell (one phantom fist model each)
		String[] fistModels = {"phantom_fist_deva", "phantom_fist_human", "phantom_fist_animal", "phantom_fist_asura", "phantom_fist_ghost", "phantom_fist_hell"};
		var sixPaths = art("six_paths_fist", 607, Dao.BODY)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.SERANG).realm(Realm.DAO_UNION)
				.pose(Pose.PUNCH).pose(Phase.EXTRA0, Pose.PUNCH).pose(Phase.EXTRA1, Pose.PUNCH).pose(Phase.EXTRA2, Pose.PUNCH).pose(Phase.EXTRA3, Pose.PUNCH).pose(Phase.EXTRA4, Pose.PUNCH).pose(Phase.EXTRA5, Pose.PUNCH)
				.route(Route.book("minecraft:unbreaking"), Route.pav())
				.flag(Flag.TEACHABLE, Flag.PAVILION)
				.qi(160).cooldown(45.0D)
				.fx(Phase.CAST,
						runeCircle(0xFFFFFF).particle(ParticleTypes.END_ROD).variant(FxLayer.Variant.VERTICAL).onCaster().above(1.0F).size(2.0F).count(40).ticks(30),
						fist(BRONZE).count(20).ticks(6).detail())
				.fx(Phase.IMPACT, shell(0xA0E0FF).particle(ParticleTypes.SOUL).size(1.5F).count(16))
				.kehancuran(DShape.trench(24, 3, 3).ahead(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D));
		for (int i = 0; i < fistColors.length; i++) {
			// x6/4 BEAM(24→48, hw 1.5, pierce) > DMG .6, written as six steps so each fist keeps its own colour (EXTRA i).
			sixPaths.step(at(i * 4).shape(beam().length(24, 48).halfWidth(1.5D).pierce()).hit(damage(0.6D)).onKill(reincarnation).phase(i));
			int rgb = fistColors[i].getTextureDiffuseColor() & 0xFFFFFF;
			sixPaths.fx(Phase.extra(i),
					model(fistModels[i]).scale(2.0F).slide(6).ticks(12).facing(FxLayer.Facing.FACE_FORWARD),
					beamCore(rgb).size(1.5F).count(40).ticks(6),
					fist(rgb).size(1.2F).count(24).ticks(8).detail(),
					shake(i == fistColors.length - 1 ? 0.4F : 0.25F, 8));
			sixPaths.sound(Phase.extra(i), SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.6F - 0.16F * i);
		}
		sink.add(sixPaths.build());

		// 608 · Segel Kedelapan: Segel Tubuh (I Shall Seal the Heavens ch. 954)
		sink.add(art("eighth_hex_body", 608, Dao.BODY)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Pose.SEAL).pose(Phase.IMPACT, Pose.OVERHEAD_SLAM).bindLook(dev.kultivator.technique.spec.run.BindKind.SEAL)
				.route(Route.book("minecraft:thorns"), Route.scr(3)).comprehension(60)
				.flag(Flag.NO_TEACH)
				.qi(240).cooldown(60.0D)
				.step(at(0).shape(nova().radius(16, 32))
						.hit(damage(1.2D).pierce(),
								attrPct(AttrKind.ATTACK_DAMAGE, -0.6D, 200).bossFactor(0.5D),
								attrPct(AttrKind.ARMOR, -1.0D, 200).bossFactor(0.5D),
								attrPct(AttrKind.MOVEMENT_SPEED, -0.5D, 200).bossFactor(0.5D),
								mark(Mark.vulnerable(0.4D).boss(0.5D), 200))
						.self(buff(BuffKind.ATTACK_DAMAGE, 0.3D, 200)))
				.fx(Phase.CAST,
						FxLayer.of(FxLayer.Template.GLYPH, HEX_RED).text("封").scale(12.0F).fall(10, 6).ticks(30),
						shockRing(HEX_RED).size(2.0F).count(40),
						shake(0.3F, 10))
				.fx(Phase.HIT, vortex(HEX_RED).particle(ParticleTypes.ENCHANT).size(0.8F).count(14).ticks(30), vortex(HEX_RED).size(0.6F).count(10).detail())
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.0F, 0.6F)
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 0.8F, 0.8F)
				// the falling 封 seal slams a ring of ground around the caster
				.kehancuran(DShape.ring(14, 6, 2))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 609 · Tinju Kaisar Abadi (A Will Eternal)
		sink.add(art("undying_emperor_fist", 609, Dao.BODY)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 4)
				.pose(Pose.PUNCH).pose(Phase.EXTRA0, Pose.PUNCH)
				.route(Route.comp(70), Route.pav())
				.flag(Flag.NO_TEACH, Flag.PAVILION)
				.qi(320).cooldown(60.0D)
				.step(at(0).shape(strike().range(6).arc(60)).hit(damage(7.0D), knock(3.0D))
						// "x3/6 RECALL (the struck target)": three aftershocks on what the fist struck.
						.onHit(at(6).repeat(3, 6).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(0.6D)).phase(0)))
				.step(at(0).shape(front().length(20, 48).width(6).height(4).speed(4)).hit(damage(3.0D), knock(2.0D)))
				.fx(Phase.CAST,
						avatar(0xFFE08A).alpha(0.25F).count(30).ticks(30),
						fist(GOLD).size(3.0F).count(40).ticks(8), model("emperor_fist").scale(3.0F).slide(4.0F).ticks(12).facing(FxLayer.Facing.FACE_FORWARD),
						shake(0.7F, 16))
				.fx(Phase.TRAVEL,
						crescentFly(GOLD).vertical().size(3.0F).count(40).ticks(10),
						shockRing(GOLD).size(1.5F).count(40),
						cracks())
				.fx(Phase.EXTRA0, impact(GOLD).particle(ParticleTypes.CRIT).count(12), shockRing(GOLD).size(0.6F).count(12).detail())
				.sound(Phase.CAST, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.8F)
				.sound(Phase.CAST, SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.GOAT_RAM_IMPACT, 1.0F, 0.8F)
				.sound(Phase.EXTRA0, SoundEvents.GOAT_RAM_IMPACT, 0.6F, 1.2F)
				.kehancuran(DShape.trench(32, 5, 3).alongLine())
				.perfection(Bonus.extraWave(1))
				.peak(Bonus.echo(0.4D))
				.build());

		// 610 · Kera Raksasa Emas: Raungan Vajra (A Record of a Mortal's Journey)
		sink.add(art("golden_ape_roar", 610, Dao.BODY)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.UBAH).realm(Realm.IMMORTAL_ASCENSION, 7)
				.pose(Pose.CHEST_BEAT).pose(Phase.IMPACT, Pose.ROAR).pose(Phase.EXTRA0, Pose.OVERHEAD_SLAM)
				// FIRST_KILL warden with BODY from IA T7 (layer index 6).
				.route(Route.feat(Feat.firstKill("minecraft:warden", Dao.BODY, Realm.IMMORTAL_ASCENSION, 6)), Route.inh(Dao.BODY.id()))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.stages(2, 500).qiPerStage(420, 0)
				.qi(420).cooldown(150.0D)
				// st1: the form (SCALE ×2.5 with room; without room the runner keeps the scale and the 12-b AVATAR stands in).
				.step(at(0).stage(1).shape(cone().length(16, 24).halfAngle(60))
						.hit(damage(3.0D), eff(Eff.WEAKNESS, 3, 100), eff(Eff.SLOWNESS, 4, 100), knock(2.0D))
						.self(
								transform("golden_ape", 500).scale(2.5D),
								hook(Hook.onMelee(at(0).shape(nova().radius(4).at(Anchor.HIT)).hit(damage(2.0D))).icd(10))))
				// st2 "Hantaman Gunung": leap, slam, and the form ends.
				.step(at(1).stage(2).self(blink(BlinkMode.AIM, 24).leap().eachHop(
						at(0).shape(nova().radius(10, 20)).hit(damage(5.0D), launch(1.2D)).self(revert()).phase(0))))
				.fx(Phase.CAST,
						avatar(0xFFC040).alpha(0.3F).scale(1.05F).count(40).ticks(500),
						mist(0xFFC040).onCaster().count(24).ticks(100).detail(),
						shockRing(0xFFFFFF).particle(ParticleTypes.SONIC_BOOM).size(1.5F).count(6),
						coneSpray(0xEEF4F0).particle(ParticleTypes.GUST).count(8),
						coneSpray(0xFFE0A0).particle(new ShriekParticleOption(0)).count(4).detail(),
						shake(0.45F, 12))
				.fx(Phase.HIT, shell(0xFFC040).size(0.8F).count(10))
				.fx(Phase.EXTRA0, shockRing(0xFFC040).size(2.5F).count(60), cracks(), impact(0xFFE0A0).particle(ParticleTypes.EXPLOSION).count(3), shake(0.7F, 16))
				.fx(Phase.TRAVEL, trail(0xFFC040).count(24).ticks(10), afterimage(0xFFC040).ticks(10).detail())
				.sound(Phase.CAST, SoundEvents.RAVAGER_ROAR, 1.0F, 0.5F)
				.sound(Phase.CAST, SoundEvents.WARDEN_ROAR, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.8F)
				.sound(Phase.EXTRA0, SoundCue.of(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.6F).delay(4))
				.kehancuran(DShape.bowl(8).stage(2))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 611 · Wujud Kosmik Langit-Bumi (Journey to the West ch. 6)
		sink.add(art("cosmic_giant_form", 611, Dao.BODY)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SUMMON_RAISE).pose(Phase.EXTRA1, Pose.KNEEL_STRIKE).climax(Phase.EXTRA1)
				.route(Route.feat(Feat.tribStrikes(60)), Route.inh(Dao.BODY.id())).comprehension(85)
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qiPct(55, 700).cooldown(360.0D)
				.step(at(0).self(
						// The caster keeps his own size: the colossus is a phantom that stomps every 50 ticks.
						summon("cosmic_avatar", 1, 400).mode(Payload.SummonMode.FOLLOW).interval(50).range(48)
								.attack(at(0).shape(zone(Anchor.densest(1, 48)).radius(8, 14)).hit(damage(4.0D), launch(1.0D)).phase(0)),
						hook(Hook.onMelee(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(3.0D))).ticks(400).icd(10)),
						dr(0.5D, Payload.Facing.ALL, 400)))
				.step(at(400).shape(nova().radius(24, 32)).hit(damage(10.0D)).phase(1))
				.fx(Phase.CAST,
						avatar(GOLD).alpha(0.35F).scale(40.0F).count(60).ticks(400),
						mist(0xFFFFFF).radius(20.0F).above(20.0F).count(60).ticks(400).detail(),
						sky(0xFFE8A0).minTier(4).ticks(400).extra(),
						shake(0.5F, 10))
				.fx(Phase.HIT, fist(GOLD).size(2.0F).count(30).ticks(6))
				.fx(Phase.EXTRA0, shockRing(0xC8B080).radius(16.0F).size(2.0F).count(60), shake(0.7F, 10))
				.fx(Phase.EXTRA1, shell(GOLD).size(1.5F).count(80), flash(0xFFF0C0, 0.3F, 6), shake(1.0F, 24))
				// the avatar warns 10 ticks before each stomp (PHANTOM with FLAG_PRE_IMPACT), then the stomp is its EXTRA0
				.fx(Phase.PHANTOM, shockRing(0xC8B080).onTarget().radius(8.0F).count(24).ticks(10), mist(0xC8B080).onTarget().count(10).ticks(10).detail())
				.sound(Phase.CAST, SoundEvents.WARDEN_STEP, 1.5F, 0.5F)
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_STEP, 1.5F, 0.5F)
				.sound(Phase.EXTRA0, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.7F)
				.sound(Phase.EXTRA1, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.5F)
				.kehancuran(DShape.disc(6, 2).nat().times(8))
				// Phase 10 (DESIGN 28.12): a footprint glyph before each avatar stomp
				.dampak(Dampak.builder().mark(Dampak.Mark.FOOT).ride(Dampak.Ride.SHORT).climaxAt(400)
						.frames(0xFFF0C0, 0xC8A040).silence(3).debris(Dampak.Debris.BURST).ring(Dampak.Ring.GOLD).palette(Dampak.Palette.EARTH)
						.monumentSeconds(60).build())
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 612 · Injakan Dewa Kuno (inspired by Renegade Immortal)
		sink.add(art("ancient_god_trample", 612, Dao.BODY)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.STOMP).pose(Phase.EXTRA0, Pose.STOMP).pose(Phase.EXTRA1, Pose.KNEEL_STRIKE).climax(Phase.EXTRA1)
				.gate(Gate.DEWA_I).prereq("cosmic_giant_form", 3)
				.flag(Flag.TEACHABLE, Flag.NO_ECHO).dewaStars(28).teachStars(28)
				.qiPct(75).cooldown(600.0D)
				.step(at(0).self(transform("ancient_god", 300).scale(1.5D).with(dr(0.3D, Payload.Facing.ALL, 300))))
				// x9/20 from tick 20: eight footprints, each 8 blocks further along the look ...
				.step(at(20).repeat(8, 20).shape(zone(Anchor.CASTER).advance(8).radius(8, 12)).hit(damage(3.0D), slam(0.6D)).phase(0))
				// ... and the 9th at tick 180.
				.step(at(180).shape(nova().radius(32, 40)).hit(damage(16.0D), launch(1.0D), bind(BindKind.STUN, 120)).phase(1))
				.fx(Phase.CAST,
						// Nine silver star marks between the brows; one more lights at each footprint (EXTRA0 below).
						starfield(STAR_SILVER).onCaster().above(1.8F).size(0.3F).n(9).count(9).ticks(300),
						avatar(0xC8A040).alpha(0.3F).count(30).ticks(300).detail(),
						shake(0.4F, 10))
				.fx(Phase.EXTRA0,
						starfield(STAR_SILVER).radius(8.0F).n(9).count(27).ticks(40),
						// the star mark of this footprint lights between the brows
						starfield(STAR_SILVER).onCaster().above(1.8F).size(0.45F).n(1).count(4).ticks(20).detail(),
						shockRing(0xC8A040).size(1.5F).count(40),
						cracks(),
						shake(0.5F, 8))
				.fx(Phase.EXTRA1,
						skyTear(0x8A6A20).minTier(4).ticks(60),
						shell(0xFFD070).size(1.5F).count(100),
						flash(0xFFE8A0, 0.3F, 6),
						shake(1.0F, 30))
				// Phase 30 (DESIGN 30.4.6): the god's foot is drawn by client/anim/model/UltimatesB
				.fx(Phase.EXTRA0, cracksAlways(0x5A4A2A).count(24).ticks(40).detail())
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.2F, 0.5F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_STEP, 1.5F, 0.5F)
				.sound(Phase.EXTRA1, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.8F)
				.sound(Phase.EXTRA1, SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 0.5F)
				.kehancuran(DShape.nine(3, 8), DShape.bowl(10).at(180))
				// Phase 10 (DESIGN 28.12): a golden footprint before each step, a silver line joins them, the god's foot stays pressed in the crater
				.dampak(Dampak.builder().mark(Dampak.Mark.FOOT).ride(Dampak.Ride.FULL, 80, Dampak.Vista.SUNGAI_BINTANG).climaxAt(180)
						.frames(0xFFE8A0, 0xC8C8D8).silence(6).debris(Dampak.Debris.BURST).ring(Dampak.Ring.DUST).palette(Dampak.Palette.EARTH)
						.sigil(SigilPattern.EVENT_CHAIN, 8.0F).ignite(Phase.EXTRA0)
						.monument("god_foot", 10.0F, 120, Dampak.MonumentPose.FLAT).build())
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	/** {@code onKill NOVA(r4) > DMG .8} that chains through further kills, at most {@code depth} bursts deep. */
	private static Step reincarnation(int depth) {
		Step burst = at(0).shape(nova().radius(4).at(Anchor.HIT)).hit(damage(0.8D));
		return depth <= 1 ? burst : burst.onKill(reincarnation(depth - 1));
	}

	private BodyArts() {
	}
}

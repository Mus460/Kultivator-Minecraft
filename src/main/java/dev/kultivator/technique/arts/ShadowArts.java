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
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BlinkMode;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Bayangan & Angin / Shadow & Wind catalogue (DESIGN 22.9.7, net ids 800-810): 11 specs, 10 for the player and the
 * disciple-only {@code karma_domain}. Every spec is builder DSL only (22.2.5); no block is ever touched here, the KH
 * shapes are carved only by {@code Kehancuran.submitSelf} when the player's own switch is on (22.11). Numbers are the
 * catalogue's ({@code b→c} = base → spec cap); the level 3 {@code perfection} bonuses are game design picks of 22.6, the
 * level 4 {@code peak} is ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise.
 */
public final class ShadowArts {

	/** Shadow violet, wraith silver and desert sand. */
	private static final int SHADOW = 0x201030;
	private static final int VIOLET = 0x9B30FF;
	private static final int SILVER = 0xE0E8FF;
	private static final int STAR_BLUE = 0xA8C8FF;
	private static final int SAND = 0xE8C46A;
	private static final int WIND = 0xEEF4F0;
	private static final int SOUL_BLUE = 0x7FE0FF;
	private static final int RESENT = 0x400810;
	private static final int KARMA = 0xE0A030;

	public static void register(TechniqueRegistry.Sink sink) {
		ParticleOptions sandDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

		// 800 · Penyatuan Roh Iblis Bayangan (Tales of Demons and Gods)
		sink.add(art("shadow_devil_fusion", 800, Dao.SHADOW)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.INDRA).realm(Realm.GOLDEN_CORE)
				.route(Route.book("minecraft:swift_sneak")).comprehension(10)
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS)
				.qi(25).cooldown(30.0D)
				.step(at(0).shape(all().radius(32).max(32))
						.hit(untarget(), mark(Mark.soul(2.0D), 400), mark(Mark.highlight(), 400))
						.self(buff(BuffKind.INVISIBILITY, 1, 400, 600)))
				.fx(Phase.CAST,
						mist(SHADOW).particle(ParticleTypes.SQUID_INK).count(40).ticks(30),
						shell(0x3A1458).size(2.0F).count(30).detail(),
						vortex(SHADOW).inward().onCaster().count(24).ticks(20).extra())
				.fx(Phase.HIT, viewerMark(VIOLET).ticks(400))
				.sound(Phase.CAST, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.PHANTOM_AMBIENT, 0.8F, 0.6F)
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 801 · Angin Ilahi Samadhi (Journey to the West ch. 21)
		sink.add(art("samadhi_wind", 801, Dao.SHADOW)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.KENDALI).realm(Realm.NASCENT_SOUL)
				.route(Route.dao())
				.channel(60, 10)
				.qi(50).cooldown(18.0D)
				.step(at(0).shape(cone().length(14, 24).halfAngle(35))
						.hit(damage(0.3D), eff(Eff.BLINDNESS, 1, 120), eff(Eff.SLOWNESS, 2, 60), knock(0.5D)))
				.fx(Phase.CAST,
						coneSpray(SAND).particle(sandDust).count(60).ticks(12),
						coneSpray(0xF0E0B0).particle(ParticleTypes.GUST).count(4).detail(),
						shake(0.1F, 8))
				.fx(Phase.PULSE,
						coneSpray(SAND).particle(sandDust).count(50).ticks(12),
						coneSpray(SAND).count(30).detail())
				.sound(Phase.CAST, SoundEvents.BREEZE_WHIRL, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.0F, 1.0F)
				.sound(Phase.PULSE, SoundEvents.BREEZE_WHIRL, 0.8F, 0.9F)
				.kehancuran(DShape.stripFan(20, 35))
				.perfection(Bonus.payload(eff(Eff.WEAKNESS, 1, 80)))
				.peak(Bonus.duration(0.25D))
				.build());

		// 802 · Bayang Pecah Dewa Bintang (Against the Gods)
		sink.add(art("star_god_broken_shadow", 802, Dao.SHADOW)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.GERAK).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.pav(), Route.book("minecraft:feather_falling"))
				.flag(Flag.NO_TEACH, Flag.PAVILION, Flag.REQUIRES_TARGET)
				.stages(2, 40).qiPerStage(90, 0)
				.qi(90).cooldown(25.0D)
				// row 802: i-frames during the chain (each hop renews 6 ticks; hops are 3 ticks apart)
				.step(at(0).stage(1).self(blink(BlinkMode.TO_TARGET, 20).chain(5, 10, 20).gap(3).iframes(6)
						.eachHop(at(0).shape(strike()).hit(damage(1.8D)))))
				.step(at(10).stage(1).shape(nova().radius(2).at(Anchor.AFTERIMAGE)).hit(damage(0.6D)))
				.step(at(0).stage(2).self(blink(BlinkMode.ORIGIN, 64)).phase(0))
				.fx(Phase.CAST,
						afterimage(STAR_BLUE).count(20),
						cloneOf(STAR_BLUE).ticks(30),
						impact(0xFFFFFF).particle(ParticleTypes.END_ROD).count(16).detail())
				.fx(Phase.TRAVEL,
						trail(STAR_BLUE).particle(ParticleTypes.END_ROD).count(24),
						afterimage(0xC0D8FF).count(12).detail())
				.fx(Phase.HIT, impact(0xE0ECFF).particle(ParticleTypes.END_ROD).count(12))
				.fx(Phase.IMPACT, shell(STAR_BLUE).size(2.0F).count(16))
				.fx(Phase.EXTRA0, trail(STAR_BLUE).particle(ParticleTypes.END_ROD).count(20), afterimage(STAR_BLUE).count(12))
				.sound(Phase.CAST, SoundEvents.ENDERMAN_TELEPORT, 0.9F, 1.8F)
				.sound(Phase.TRAVEL, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.8F)
				.sound(Phase.HIT, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.2F)
				.sound(Phase.EXTRA0, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.6F)
				.perfection(Bonus.chain(2))
				.peak(Bonus.duration(0.25D))
				.build());

		// 803 · Kupu-kupu Arwah Perak (Heaven Official's Blessing)
		sink.add(art("wraith_butterflies", 803, Dao.SHADOW)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.comp(60), Route.book("minecraft:soul_speed"))
				.qi(100).cooldown(35.0D)
				.step(at(0).shape(all().radius(24).max(8, 20))
						.hit(dot(0.35D, 10, DotKind.SOUL))
						.self(projectileGuard(360, 3, 12, 200)))
				.fx(Phase.CAST,
						swarm(SILVER).particle(ParticleTypes.END_ROD).count(120).ticks(60),
						swarm(SILVER).particle(ParticleTypes.WHITE_ASH).onCaster().n(12).ticks(200).detail(),
						mist(0xB01020).count(30).ticks(60).extra())
				.fx(Phase.HIT, impact(SILVER).particle(ParticleTypes.WHITE_ASH).count(10))
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 0.6F, 2.0F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 804 · Kipas Daun Pisang (Journey to the West ch. 59-61)
		sink.add(art("banana_leaf_fan", 804, Dao.SHADOW)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.route(Route.book("minecraft:wind_burst"), Route.inh("shadow"))
				.stages(2, 60).qiPerStage(200, 0)
				.qi(200).cooldown(45.0D)
				.step(at(0).stage(1).shape(front().length(32, 64).width(8, 24).height(6).speed(4))
						.hit(damage(2.2D), knock(4.0D)))
				.step(at(0).stage(2).shape(cone().length(24).halfAngle(60))
						.hit(pull(2.0D), damage(1.0D)).phase(0))
				.fx(Phase.CAST,
						giantItem(Items.BIG_DRIPLEAF).scale(6.0F).swing(120.0F, 8.0F).onCaster().minTier(3),
						arc(WIND).count(40).ticks(8),
						shake(0.25F, 10))
				.fx(Phase.TRAVEL,
						crescentFly(0xFFFFFF).count(60),
						crescentFly(WIND).particle(ParticleTypes.GUST).count(8).detail())
				.fx(Phase.EXTRA0,
						coneSpray(WIND).inward().count(50),
						coneSpray(WIND).particle(ParticleTypes.GUST).inward().count(6).detail())
				.sound(Phase.CAST, SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.2F, 0.5F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.2F, 0.5F).delay(4))
				.sound(Phase.EXTRA0, SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.0F, 0.6F)
				.kehancuran(DShape.stripFan(48).stage(1))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 805 · Formasi Angin Menderu (Investiture of the Gods ch. 44/46)
		sink.add(art("wind_roaring_formation", 805, Dao.SHADOW)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 1)
				.route(Route.book("minecraft:punch"), Route.pav())
				.flag(Flag.PAVILION)
				.qi(280).cooldown(80.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(14, 28).height(14).duration(300).pulse(10))
						.hit(damage(0.3D), ignite(1.0D)))
				.step(at(40).repeat(7, 40).shape(all().ofArea().max(32))
						.hit(knock(0.4D).sideways(), launch(0.2D), eff(Eff.BLINDNESS, 1, 40)))
				.step(at(300).shape(all().ofArea().max(32)).hit(damage(3.0D)).phase(0))
				.fx(Phase.CAST,
						swordRing(Items.BANNER.white(), 4, 14.0F).orbit(14.0F, 0.0F).scale(4.0F).ticks(300),
						vortex(WIND).particle(ParticleTypes.GUST).count(30).ticks(40),
						shake(0.2F, 10))
				.fx(Phase.PULSE,
						vortex(WIND).size(14.0F).count(60).ticks(12),
						vortex(0xFF8030).particle(ParticleTypes.FLAME).size(10.0F).count(30).ticks(12).detail(),
						arc(0xFFFFFF).particle(ParticleTypes.SWEEP_ATTACK).count(8).extra())
				.fx(Phase.EXTRA0,
						arc(0xFFFFFF).particle(ParticleTypes.SWEEP_ATTACK).count(48),
						shell(WIND).particle(ParticleTypes.GUST).count(12).detail(),
						shake(0.3F, 12))
				.sound(Phase.CAST, SoundEvents.BREEZE_WHIRL, 1.4F, 0.7F)
				.sound(Phase.CAST, SoundEvents.FIRE_AMBIENT, 1.4F, 0.8F)
				.sound(Phase.CAST, SoundEvents.ELYTRA_FLYING, 0.8F, 1.0F)
				.sound(Phase.PULSE, SoundEvents.BREEZE_WHIRL, 0.8F, 0.8F)
				.kehancuran(DShape.strip(10))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 806 · Formasi Jiwa Jatuh (Investiture of the Gods ch. 44/48)
		sink.add(art("soul_falling_formation", 806, Dao.SHADOW)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 4)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.SHADOW, Cond.NIGHT, null, 300)))
				.flag(Flag.PAVILION)
				.qi(340).cooldown(100.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(16, 28).height(12).duration(240).pulse(40))
						.hit(dot(0.5D, 4, DotKind.SOUL), chance(0.25D, bind(BindKind.STUN, 30))))
				.step(at(240).shape(all().ofArea().max(32)).hit(exec(0.15D, 0.05D)).phase(0))
				.fx(Phase.CAST,
						swordRing(Items.BANNER.white(), 5, 8.0F).orbit(8.0F, 0.0F).scale(3.0F).ticks(240),
						glyph("落魂").scale(10.0F).above(8.0F).ticks(240),
						giantBlock(Blocks.HAY_BLOCK.defaultBlockState()).blockSize(0.6F, 1.6F, 0.6F).ticks(240).detail(),
						runeCircle(0xF0F0F0).size(16.0F).count(40).ticks(40))
				.fx(Phase.PULSE,
						healRise(SOUL_BLUE).particle(ParticleTypes.SOUL).count(30).ticks(30),
						mist(0xD8D8D8).count(20).detail())
				.fx(Phase.HIT, motesTo(SOUL_BLUE).particle(ParticleTypes.SOUL).count(6))
				.fx(Phase.EXTRA0, healRise(SOUL_BLUE).particle(ParticleTypes.SCULK_SOUL).count(60), shake(0.3F, 10))
				.sound(Phase.CAST, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 1.6F)
				.sound(Phase.EXTRA0, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 1.4F)
				.perfection(Bonus.payload(eff(Eff.DARKNESS, 1, 60)))
				.peak(Bonus.duration(0.25D))
				.build());

		// 807 · Panji Semiliar Jiwa (Renegade Immortal)
		sink.add(art("billion_soul_flag", 807, Dao.SHADOW)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 7)
				.route(Route.feat(Feat.killsWith(Dao.SHADOW, Cond.NONE, null, 1000)))
				.flag(Flag.NO_TEACH)
				.stack(Flow.Stack.onKill(0.03D, 50))
				.qi(420).cooldown(150.0D)
				.step(at(0).shape(field(Anchor.AIM).range(24).radius(20, 32).height(16).duration(400).pulse(20))
						.hit(damage(0.5D).pierce()))
				.step(at(400).shape(nova().radius(20, 32).at(Anchor.AREA)).hit(damage(4.0D).perStack(0.15D)).phase(0))
				.fx(Phase.CAST,
						giantItem(Items.BANNER.black()).scale(9.0F).swing(25.0F, 30.0F).ticks(400),
						vortex(SOUL_BLUE).particle(ParticleTypes.SOUL).count(40).ticks(40),
						shake(0.45F, 16))
				.fx(Phase.PULSE,
						vortex(SOUL_BLUE).particle(ParticleTypes.SOUL).size(6.0F).count(40).ticks(20),
						vortex(0x2A9FB0).particle(ParticleTypes.SCULK_SOUL).size(9.0F).count(30).ticks(20).detail())
				.fx(Phase.HIT, motesTo(SOUL_BLUE).particle(ParticleTypes.SCULK_SOUL).count(6))
				.fx(Phase.EXTRA0,
						shell(SOUL_BLUE).particle(ParticleTypes.SCULK_SOUL).size(20.0F).count(120),
						flash(0x80E0FF, 0.25F, 6),
						shake(0.8F, 24))
				.sound(Phase.CAST, SoundEvents.WARDEN_ROAR, 1.2F, 1.5F)
				.sound(Phase.CAST, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_ROAR, 1.6F, 1.5F)
				.sound(Phase.EXTRA0, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.2F, 0.8F)
				.kehancuran(DShape.sphere(6).at(400))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 808 · Segel Harimau Alam Baka (Mo Dao Zu Shi)
		sink.add(art("stygian_tiger_seal", 808, Dao.SHADOW)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PANGGIL).realm(Realm.TRUE_IMMORTAL)
				.route(Route.feat(Feat.firstKill("minecraft:warden", Dao.SHADOW, Realm.TRUE_IMMORTAL, -1)), Route.inh("shadow"))
				.flag(Flag.NO_TEACH, Flag.FORBIDDEN, Flag.CAST_ALWAYS)
				.qiPct(50, 600).cooldown(300.0D)
				.step(at(0).self(forbiddenCost(0.1D, false, 0.0D),
						summon("fierce_corpse", 8, 500).interval(20).range(16)
								.attack(at(0).shape(all().at(Anchor.SUMMON).radius(16).max(1)).hit(damage(1.5D)))))
				.step(at(100).repeat(5, 100).shape(nova().radius(24))
						.hit(eff(Eff.WEAKNESS, 2, 100), eff(Eff.SLOWNESS, 2, 100)))
				.fx(Phase.CAST,
						giantItem(Items.HEAVY_CORE).scale(3.0F).above(3.0F).spin(4.0F).onCaster().ticks(500),
						cloneOf(0x301018).count(8).ticks(500),
						mist(RESENT).count(50).ticks(60),
						trail(0x802030).count(16).detail(),
						shake(0.4F, 14))
				.fx(Phase.PULSE, mist(RESENT).size(24.0F).count(40).ticks(30), shell(0x601020).size(24.0F).count(30).detail())
				.fx(Phase.HIT, impact(0x601020).particle(ParticleTypes.SQUID_INK).count(8))
				.sound(Phase.CAST, SoundEvents.WARDEN_ROAR, 1.0F, 0.6F)
				.sound(Phase.CAST, SoundEvents.NOTE_BLOCK_FLUTE, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F, 0.9F)
				.sound(Phase.PULSE, SoundEvents.NOTE_BLOCK_FLUTE, 0.8F, 0.7F)
				.kehancuran(DShape.wither(8))
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 809 · Teknik Pusaka Kunpeng (Perfect World ch. 347 on)
		sink.add(art("kunpeng_art", 809, Dao.SHADOW)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.gate(Gate.DEWA_I).prereq("wind_roaring_formation", 3)
				.flag(Flag.NO_TEACH)
				.charge(20)
				.qiPct(75).cooldown(600.0D)
				.step(at(0).shape(front().length(128).width(12, 24).height(8).speed(4).ignoreBlocks().travel())
						.hit(damage(16.0D), knock(2.0D))
						.self(dash(32).iframes(10))
						.onEnd(at(0).shape(wave().from(Anchor.END).r1(8, 24)).hit(damage(6.0D))))
				.fx(Phase.CHARGE,
						wings(0xE8F4FF).size(16.0F).count(80).ticks(20),
						skySilhouette(FxLayer.BeastKind.KUN, 0xD8ECFF).size(60.0F).ticks(40))
				.fx(Phase.CAST,
						wings(0xFFFFFF).size(16.0F).count(60).ticks(20),
						laser(Blocks.STAINED_GLASS.white().defaultBlockState(), 2.0F, 128.0F).ticks(40),
						laser(Blocks.STAINED_GLASS.white().defaultBlockState(), 1.5F, 128.0F).above(3.0F).ticks(40),
						laser(Blocks.STAINED_GLASS.white().defaultBlockState(), 1.0F, 128.0F).above(6.0F).ticks(40).extra(),
						shake(1.0F, 30))
				.fx(Phase.TRAVEL,
						crescentFly(0xFFFFFF).size(24.0F).count(80),
						crescentFly(WIND).particle(ParticleTypes.GUST).count(8).detail(),
						mist(0xF4F4F4).count(40).ticks(40).extra())
				.fx(Phase.IMPACT, shockRing(WIND).size(24.0F).count(80), shake(0.6F, 16))
				.sound(Phase.CHARGE, SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.5F)
				.sound(Phase.CAST, SoundEvents.BREEZE_WIND_CHARGE_BURST, 2.0F, 0.6F)
				.sound(Phase.CAST, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.8F)
				.sound(Phase.IMPACT, SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.6F, 0.5F)
				.kehancuran(DShape.trench(64, 3, 2).ahead(6))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 810 · Wilayah Karma (Renegade Immortal), disciple-only from star 46
		sink.add(art("karma_domain", 810, Dao.SHADOW)
				.grade(TechniqueGrade.DEWA, 1).type(TechniqueType.WILAYAH).realm(Realm.TRUE_IMMORTAL)
				.flag(Flag.DISCIPLE_ONLY, Flag.TEACHABLE)
				.dewaStars(46).teachStars(46)
				.qiPct(70).cooldown(300.0D)
				.step(at(0).shape(domain().radius(16, 32).height(16).duration(400).pulse(20))
						.hit(link(0.4D, 400, 0.25D))
						.ally(buff(BuffKind.REFLECT, 3.0D, 400).insideArea()))
				.fx(Phase.CAST,
						dome(KARMA).count(60).ticks(40),
						chains(0xD04020).count(30).ticks(40).detail(),
						glyph("因果").scale(8.0F).above(4.0F).onCaster().ticks(400))
				.fx(Phase.PULSE, chains(KARMA).count(20).ticks(20), dome(0xD04020).count(20).ticks(20).detail())
				.fx(Phase.HIT, chains(KARMA).count(6))
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 1.2F, 1.4F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	private ShadowArts() {
	}
}

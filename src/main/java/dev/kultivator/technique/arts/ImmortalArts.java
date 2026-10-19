package dev.kultivator.technique.arts;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec.Gate;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.DashDir;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Payload.Facing;
import dev.kultivator.technique.spec.dsl.Payload.Versus;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.kultivator.data.TechniqueSpec.Flag.*;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Dao-less Abadi and Dewa ultimates (DESIGN 22.9.9, net ids 1000-1003 of the block 1000-1099): The Ninth Hex, Five
 * Elements Mountain, Tai Chi Diagram and Heaven Trampling. Builder DSL only (22.2.5); the KH shapes are carved only by
 * {@code Kehancuran.submitSelf} while the player's own switch is on (22.11). Mrd Y★n rows carry TEACHABLE with
 * {@code teachStars(n)} (Dewa rows {@code dewaStars(n)}); the level 3 {@code perfection} is a 22.6 pick, the level 4
 * {@code peak} ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise (a channel, or a step that moves the caster).
 * Phantom displays start at visual tier 3 and sky layers at 4 (22.4.7); every one of these specs is cast at tier 4 or
 * more, so all layers show.
 */
public final class ImmortalArts {

	private static final int GOLD = 0xFFD54A;
	private static final int WHITE = 0xFFFFFF;
	private static final int SEAL = 0x303040;
	private static final int HEAVEN = 0xFFF4D0;
	private static final int DISPLAY_TIER = 3;
	private static final int SKY_TIER = 4;

	public static void register(TechniqueRegistry.Sink sink) {
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState whiteGlass = Blocks.STAINED_GLASS.white().defaultBlockState();
		BlockState blackGlass = Blocks.STAINED_GLASS.black().defaultBlockState();

		// 1000 · Segel Kesembilan (I Shall Seal the Heavens, the Ninth Hex)
		sink.add(art("ninth_hex", 1000, null)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SEAL).pose(Phase.EXTRA0, Pose.OVERHEAD_SLAM).bindLook(dev.kultivator.technique.spec.run.BindKind.SEAL).climax(Phase.EXTRA0)
				.route(Route.scr(5)).prereq("karmic_hex", 3)
				.flag(NO_TEACH)
				.channel(160, 20).qiPct(50.0D, 600).qiPerSecond(5.0D).cooldown(300.0D)
				// TODO(mesin): the per-pulse BIND refreshes the seal (bosses ≤ 60t in total) rather than counting as a new control.
				.step(at(0).shape(domain().radius(24, 32).height(16).duration(160).pulse(20)).hit(bind(BindKind.STUN, 40).boss(10)))
				.step(at(0).self(projectileGuard(360.0D, 32.0D, 999, 160).wholeArea()))
				// The release when the channel ends.
				.step(atEnd().shape(all().radius(24, 32).max(32).order(Shape.All.Order.STRONGEST))
						.hit(damage(9.0D), eff(Eff.SLOWNESS, 4, 100)).phase(0))
				.fx(Phase.CAST, dome(SEAL).count(80).ticks(160),
						glyph("封封封封封封封封").scale(6.0F).n(8).radius(18.0F).ticks(160),
						shake(0.4F, 10))
				.fx(Phase.PULSE, dome(SEAL).count(20).ticks(20).detail())
				.fx(Phase.extra(0), shell(WHITE).count(80).ticks(12), flash(WHITE, 0.3F, 6), shake(1.0F, 20))
				.fx(Phase.EXTRA7, impact(SEAL).particle(ParticleTypes.END_ROD).count(6).ticks(6))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.5F))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.6F).delay(8))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.7F).delay(16))
				.sound(Phase.PULSE, SoundEvents.BELL_BLOCK, 0.5F, 0.8F)
				.sound(Phase.extra(0), SoundEvents.BELL_RESONATE, 1.4F, 0.6F)
				.kehancuran(DShape.disc(12, 2).atRelease())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 1001 · Gunung Lima Unsur (Journey to the West, the Five Elements Mountain)
		sink.add(art("five_elements_mountain", 1001, null)
				.grade(TechniqueGrade.ABADI, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SKY_RAISE).pose(Phase.IMPACT, Pose.OVERHEAD_SLAM).pose(Phase.EXTRA0, Pose.SEAL)
				.route(Route.realm()).prereq("buddha_palm", 3)
				.flag(TEACHABLE).teachStars(27)
				.qiPct(55.0D, 700).cooldown(300.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(64).radius(12, 24).fromSky(64).delay(40))
						.hit(damage(14.0D).vs(Versus.UNDEAD, 1.5D), slam(0.6D)))
				.step(at(40).shape(pillar(Anchor.AIM).ring(5, 8.0D).radius(4).height(20).delay(0))
						.hit(bind(BindKind.ROOT, 120).boss(30), dot(0.8D, 6, DotKind.QI)).phase(0))
				.fx(Phase.CAST, cloudDisc(HEAVEN).above(40.0F).count(80).ticks(40))
				// the Buddha's palm comes down from 64 up on the pre-impact TRAVEL (40 ticks), palm-down, and lands on the IMPACT
				.fx(Phase.TRAVEL, model("buddha_palm").scale(24.0F).fall(64.0F, 40.0F).ticks(46).facing(FxLayer.Facing.FLAT),
						palm(GOLD).inSky().count(60).ticks(40), cloudDisc(HEAVEN).inSky().count(30).ticks(40).detail())
				.fx(Phase.IMPACT, shockRing(GOLD).count(60).ticks(16),
						// The 9th display of the cast: EXTRA, so the Normal budget of 8 (3 palm blocks + 5 mountains) drops it first.
						glyph("唵嘛呢叭咪吽").scale(10.0F).above(14.0F).ticks(120).minTier(1).extra(),
						shake(1.0F, 20))
				// The five mountains rise on the ring (one block per pillar, in order) and sink after 120t.
				// one mountain peak rises at each of the five ring points (one EXTRA0 per point, index/of)
				.fx(Phase.extra(0), column(GOLD).count(30).ticks(20),
						model("mountain_peak").scale(10.0F).rise(14.0F, 10.0F).ticks(120).facing(FxLayer.Facing.UPRIGHT),
						impact(0x8A7A60).particle(ParticleTypes.POOF).count(20).ticks(12).detail())
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.5F))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.55F).delay(12))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 0.5F, 0.6F).delay(24))
				.sound(Phase.IMPACT, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.5F)
				.kehancuran(DShape.disc(10, 3), DShape.column(2, 8).times(5).at(40))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 1002 · Diagram Taiji (Investiture of the Gods, the Tai Chi Diagram)
		sink.add(art("tai_chi_diagram", 1002, null)
				.grade(TechniqueGrade.ABADI, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Phase.CHARGE, Pose.SEAL).pose(Pose.TWIN_PALM)
				.route(Route.inh("universal"), Route.feat(Feat.tribAverage(BreakthroughQuality.PERFECT)))
				.flag(TEACHABLE).teachStars(27)
				.charge(30).qiPct(55.0D, 700).cooldown(300.0D)
				.step(at(0).shape(front().length(48, 64).width(9).height(12).speed(8.0D).skyBand(30.0D))
						.hit(damage(12.0D).vs(Versus.UNDEAD, 1.5D).charged(), exec(0.40D)))
				.fx(Phase.CAST, runeCircle(WHITE).color2(0x101010).radius(4.0F).count(40).ticks(30))
				.fx(Phase.CHARGE, runeCircle(WHITE).color2(0x101010).radius(6.0F).spin(6.0F).count(40).ticks(30))
				// The bands show up to 176 b in the catalogue; one LASER is at most 128, so the client draws two in a row (TODO kitab).
				.fx(Phase.TRAVEL, model("taiji_disc").scale(9.0F).fall(30.0F, 10.0F).ticks(24).facing(FxLayer.Facing.FLAT),
						laser(whiteGlass, 4.5F, 128.0F).ticks(24).minTier(1).extra(),
						laser(blackGlass, 4.5F, 128.0F).delay(2).ticks(24).minTier(1).extra(),
						trail(WHITE).color2(0x101010).count(60).ticks(12), shake(0.8F, 16), cracks())
				.fx(Phase.HIT, healRise(0xF0F0F0).particle(ParticleTypes.WHITE_ASH).count(16).ticks(30))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.8F)
				.sound(Phase.TRAVEL, SoundEvents.END_PORTAL_FRAME_FILL, 1.6F, 0.6F)
				.kehancuran(DShape.trench(64, 9, 1))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 1003 · Langkah Penginjak Langit (Renegade Immortal, Heaven Trampling)
		sink.add(art("heaven_trampling", 1003, null)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.STOMP).pose(Phase.IMPACT, Pose.KNEEL_STRIKE).pose(Phase.EXTRA0, Pose.STOMP).climax(Phase.IMPACT)
				.gate(Gate.DEWA_I).prereq("five_elements_mountain", 3).dewaStars(60)
				.flag(TEACHABLE, NO_ECHO)
				.qiPct(80.0D).cooldown(600.0D)
				// Nine light steps up (3t each), untouchable meanwhile, no fall damage for 200t.
				.step(at(0).self(dash(24).dir(DashDir.UP).ticks(27).iframes(30).noFall(200), dr(0.8D, Facing.ALL, 30)))
				.step(at(30).shape(zone(Anchor.AIM).range(48).radius(24, 32)).hit(damage(14.0D), launch(1.0D)))
				.step(at(40).repeat(3, 10).shape(wave().from(Anchor.AIM).r1(32, 40).speed(4.0D)).hit(damage(4.0D)).phase(0))
				.fx(Phase.CAST, shockRing(HEAVEN).n(9).count(90).ticks(30))
				.fx(Phase.IMPACT, shell(HEAVEN).radius(24.0F).count(120).ticks(20), sky(HEAVEN).ticks(60).minTier(SKY_TIER),
						skyTear(HEAVEN).ticks(60).minTier(SKY_TIER), flash(WHITE, 0.35F, 8), shake(1.0F, 24))
				.fx(Phase.extra(0), shockRing(HEAVEN).count(60).ticks(12))
				.fx(Phase.TRAVEL, shockRing(HEAVEN).n(9).radius(2.0F).count(40).ticks(27), trail(HEAVEN).count(30).ticks(27).detail())
				.fx(Phase.IMPACT, model("god_foot").scale(12.0F).fall(12.0F, 4.0F).ticks(16).facing(FxLayer.Facing.FLAT))
				.sound(Phase.IMPACT, SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 0.8F)
				.sound(Phase.extra(0), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 0.7F)
				.kehancuran(DShape.sphere(6), DShape.disc(16, 2).nat())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	private ImmortalArts() {
	}
}

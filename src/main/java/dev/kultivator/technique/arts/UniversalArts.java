package dev.kultivator.technique.arts;

import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.special.CloudTravel;
import dev.kultivator.technique.special.EchoWheel;
import dev.kultivator.technique.special.FusionPair;
import dev.kultivator.technique.special.MysteryFusion;
import dev.kultivator.technique.special.SeventyTwo;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BlinkMode;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.Center;
import dev.kultivator.technique.spec.dsl.Payload.DashDir;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Payload.Facing;
import dev.kultivator.technique.spec.dsl.Payload.Store;
import dev.kultivator.technique.spec.dsl.Payload.SummonMode;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static dev.kultivator.data.TechniqueSpec.Flag.*;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Universal catalogue, Kuning to Langit (DESIGN 22.9.9, net ids 100-133): 34 Dao-less specs, plus the five specials of
 * the set ({@link FusionPair} for 110, {@link CloudTravel} for 111, {@link SeventyTwo} for 113, {@link MysteryFusion} for
 * 119, {@link EchoWheel} for 132). Builder DSL only (22.2.5); no block is ever touched here: the KH shapes are carved only
 * by {@code Kehancuran.submitSelf} while the player's own switch is on (22.11).
 * <p>
 * Notes on the notation: {@code b→c} rows use the two-number forms (base → spec cap, growth through A/D/N and mastery);
 * {@code +k/tier} rows use {@code Val.lin}. The Mrd column is final: Y rows carry TEACHABLE (Y★n also
 * {@code teachStars(n)}), N rows NO_TEACH. The level 3 {@code perfection} bonuses are game-design picks of 22.6; the level
 * 4 {@code peak} is ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise. Special specs keep only an empty
 * keep-alive step in their timeline (their handler runs everything through {@code CastContext}) and do their own target
 * check, so they carry CAST_ALWAYS. Phantom displays start at visual tier 3, glyph circles at 2 and sky layers at 4
 * (22.4.7) unless the row says otherwise; a particle layer always remains for the lower tiers.
 */
public final class UniversalArts {

	private static final int GOLD = 0xFFD54A;
	private static final int JADE = 0x5FD39A;
	private static final int VIOLET = 0x9B30FF;
	private static final int SILVER = 0xD8D8E8;
	private static final int BLOOD = 0x8A0303;
	private static final int CRIMSON = 0xC00010;
	private static final int WHITE = 0xFFFFFF;
	private static final int ASH = 0xC8C8C8;
	private static final int STONE = 0x9A9A9A;
	private static final int BLUE = 0x4FA8FF;
	private static final int DRAGON = 0x9A50FF;

	/** Phantom displays from visual tier 3, glyph circles from 2 (22.4.7). */
	private static final int DISPLAY_TIER = 3;
	private static final int GLYPH_TIER = 2;
	/** Stages of {@code fa_tian_xiang_di}: the growth plus up to nine stomps inside the form. */
	private static final int FA_TIAN_STAGES = 10;

	/** {@code three_life_saving_hairs}: {@code charges} hairs for 20 minutes; each one blows the attackers back. */
	private static Payload.CheatDeath hairs(int charges) {
		return new Payload.CheatDeath(Val.of(charges), Val.of(24000), 0.30D,
				at(0).shape(nova().radius(6)).hit(knock(1.5D))
						.self(cleanse(), dr(0.8D, Facing.ALL, 40), blink(BlinkMode.BACK, 8)).phase(0));
	}

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(FusionPair.ID, new FusionPair());
		sink.special(CloudTravel.ID, new CloudTravel());
		sink.special(SeventyTwo.ID, new SeventyTwo());
		sink.special(MysteryFusion.ID, new MysteryFusion());
		sink.special(EchoWheel.ID, new EchoWheel());

		BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState jadeGlass = Blocks.STAINED_GLASS.lime().defaultBlockState();
		BlockState greyGlass = Blocks.STAINED_GLASS.lightGray().defaultBlockState();
		BlockState blackGlass = Blocks.STAINED_GLASS.black().defaultBlockState();
		BlockState whiteGlass = Blocks.STAINED_GLASS.white().defaultBlockState();
		BlockState cauldron = Blocks.CAULDRON.defaultBlockState();

		// ============================================================ Kuning, Foundation Establishment

		// 100 · Langkah Bayangan Hantu (Soul Land, Ghost Shadow Perplexing Track)
		sink.add(art("ghost_shadow_track", 100, null)
				.grade(TechniqueGrade.HUANG, 0).type(TechniqueType.GERAK).realm(Realm.FOUNDATION)
				.route(Route.book("minecraft:soul_speed"), Route.pav())
				.flag(TEACHABLE, PAVILION, CAST_ALWAYS)
				.qi(10).charges(2, 4, 60).cooldownTicks(60)
				.step(at(0).shape(nova().radius(6, 10).at(Anchor.ORIGIN)).hit(untarget()))
				.step(at(0).self(dash(4, 8).dir(DashDir.MOVE).iframes(6), buff(BuffKind.SPEED, 2, 40)))
				.step(atEnd().minTier(3).shape(nova().radius(2).at(Anchor.AFTERIMAGE)).hit(damage(0.6D)))
				.fx(Phase.CAST, mist(0x8A7FA8).particle(ParticleTypes.LARGE_SMOKE).count(12).ticks(8),
						afterimage(0x8A7FA8).ticks(16))
				.fx(Phase.TRAVEL, trail(0x8A7FA8).particle(ParticleTypes.SOUL).count(20).ticks(8),
						cloneOf(0x8A7FA8).n(1).ticks(16).detail())
				.fx(Phase.END, impact(0x8A7FA8).particle(ParticleTypes.SOUL).count(10))
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 0.4F, 1.6F)
				.perfection(Bonus.extraCount(1))
				.peak(Bonus.duration(0.25D))
				.build());

		// 101 · Tangan Giok Misterius (Soul Land, Mysterious Jade Hand)
		sink.add(art("mysterious_jade_hand", 101, null)
				.grade(TechniqueGrade.HUANG, 1).type(TechniqueType.LINDUNG).realm(Realm.FOUNDATION)
				.route(Route.book("minecraft:unbreaking"), Route.pav())
				.flag(TEACHABLE, PAVILION, CAST_ALWAYS)
				.qi(12).cooldown(6.0D)
				.step(at(0).self(
						projectileGuard(120.0D, 3.0D, 2, 30).charges(2, 5).ticks(30, 50).returnShot(1.0D),
						new Payload.Dr(Val.of(0.5D), Facing.FRONT, Val.of(30, 50), false, 0.0D),
						hook(Hook.onHurt(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(1.2D))).front().icd(4).ticks(30, 50)),
						cleanse()))
				.fx(Phase.CAST, shieldShell(JADE).particle(ParticleTypes.GLOW).count(16).ticks(30))
				.fx(Phase.HIT, impact(0xE8FFF0).particle(ParticleTypes.CRIT).count(8))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.2F)
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 0.25F, 1.6F)
				.perfection(Bonus.extraCount(2))
				.peak(Bonus.duration(0.25D))
				.build());

		// 102 · Mata Iblis Ungu (Soul Land, Purple Demon Eyes)
		sink.add(art("purple_demon_eye", 102, null)
				.grade(TechniqueGrade.HUANG, 1).type(TechniqueType.SERANG).realm(Realm.FOUNDATION)
				.route(Route.book("minecraft:piercing"), Route.pav())
				.flag(TEACHABLE, PAVILION)
				.qi(10).cooldown(3.0D)
				.step(at(0).shape(beam().length(16, 28).halfWidth(0.6D).pierce(3))
						.hit(damage(1.2D).pierce(), eff(Eff.SLOWNESS, 2, 40), eff(Eff.WEAKNESS, 1, 40))
						.when(Cond.all(Cond.DAWN, Cond.SKY_OPEN), 1.5D))
				.fx(Phase.CAST, impact(VIOLET).onCaster().above(1.6F).count(6).ticks(6))
				.fx(Phase.TRAVEL, beamCore(VIOLET).size(0.6F).count(30).ticks(8),
						beamCore(0x6020C0).particle(ParticleTypes.PORTAL).count(14).ticks(10).detail())
				.fx(Phase.HIT, impact(VIOLET).count(6))
				.sound(Phase.CAST, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 1.5F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 103 · Jarum Hujan Bunga Pir (Soul Land, Violent Rain Pear Blossom Needles)
		sink.add(art("pear_blossom_needles", 103, null)
				.grade(TechniqueGrade.HUANG, 2).type(TechniqueType.AREA).realm(Realm.FOUNDATION)
				.route(Route.book("minecraft:multishot"), Route.pav())
				.flag(TEACHABLE, PAVILION)
				.qi(15).cooldown(6.0D)
				// ≤ 4 needles per target at FE, ≤ 8 from GC (22.20)
				.step(at(0).shape(volley().count(9, 27).spread(Shape.Volley.Spread.FAN, 25.0D).range(16, 32).perTarget(4, 8).sim())
						.hit(damage(0.3D).pierce()))
				.fx(Phase.CAST, impact(SILVER).count(10).ticks(6))
				.fx(Phase.TRAVEL, trail(SILVER).count(27).ticks(10),
						petals(0xFFB7D5).particle(ParticleTypes.CHERRY_LEAVES).count(16).ticks(20).extra())
				.fx(Phase.HIT, impact(SILVER).particle(ParticleTypes.CRIT).count(4))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.8F))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.8F).delay(2))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.8F).delay(4))
				.perfection(Bonus.extraCount(6))
				.peak(Bonus.echo(0.4D))
				.build());

		// ============================================================ Misterius, Golden Core

		// 104 · Serangga Tidur (Journey to the West, sleep-inducing bugs)
		sink.add(art("sleep_insects", 104, null)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.KENDALI).realm(Realm.GOLDEN_CORE)
				.route(Route.book("minecraft:lure"))
				.flag(TEACHABLE)
				.qi(30).cooldown(14.0D)
				.step(at(0).shape(cone().length(8, 16).halfAngle(30))
						.hit(bind(BindKind.SLEEP, 60, 100).boss(20), eff(Eff.BLINDNESS, 1, 40), mark(Mark.soul(2.0D), 200)))
				.fx(Phase.CAST, swarm(0xFFE680).particle(ParticleTypes.GLOW).count(40).ticks(30),
						coneSpray(0xFFE680).count(30).ticks(16))
				.fx(Phase.HIT, impact(0xFFE680).count(6),
						glyph("Zzz").scale(1.5F).above(1.0F).ticks(60).minTier(DISPLAY_TIER))
				.sound(Phase.CAST, SoundEvents.BEE_LOOP, 0.3F, 1.0F)
				.sound(Phase.CAST, SoundEvents.CAT_PURR_BABY, 0.8F, 0.8F)
				.perfection(Bonus.durationTicks(20))
				.peak(Bonus.echo(0.4D))
				.build());

		// 105 · Mantra Pembeku Tubuh (Journey to the West, the immobilization magic)
		sink.add(art("immobilization_spell", 105, null)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.KENDALI).realm(Realm.GOLDEN_CORE)
				.route(Route.book("minecraft:binding_curse"))
				.flag(TEACHABLE, REQUIRES_TARGET)
				.qi(25).cooldown(10.0D)
				.step(at(0).shape(target().range(24)).hit(damage(1.5D)))
				// The target plus 0 (+1 per tier, ≤ 4) others within 4 of it, nearest first: one bind each.
				.step(at(0).shape(all().at(Anchor.TARGET).radius(4).max(Val.lin(1, 1, 5)).precise())
						.hit(bind(BindKind.STUN, 60, 100).boss(15)))
				.fx(Phase.CAST, chains(GOLD).count(24).ticks(10))
				.fx(Phase.HIT, chains(GOLD).count(10).ticks(20).detail(),
						glyph("定").scale(2.0F).above(1.2F).ticks(60).minTier(DISPLAY_TIER))
				.sound(Phase.CAST, SoundEvents.CHAIN_PLACE, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F)
				.perfection(Bonus.durationTicks(20))
				.peak(Bonus.echo(0.4D))
				.build());

		// 106 · Seni Segel Kaisar (Battle Through the Heavens, Emperor Seal Jue; stages 2-4 are game design)
		sink.add(art("emperor_seal_jue", 106, null)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.AREA).realm(Realm.GOLDEN_CORE)
				.route(Route.book("minecraft:knockback"), Route.pav())
				.flag(TEACHABLE, PAVILION)
				.stages(4, 80).stageRealm(2, Realm.NASCENT_SOUL).stageRealm(3, Realm.DEITY_TRANSFORMATION).stageRealm(4, Realm.DAO_UNION)
				// Mrd "Y (st1–3)": a disciple runs seals 1-3 in one cast, each behind its realm gate
				.discipleStages(3)
				.qiPerStage(25, 35, 50, 90).qi(25).cooldown(18.0D)
				// st1 Segel Pembuka Gunung (Open Mountain Seal)
				.step(at(0).stage(1).shape(zone(Anchor.AIM).range(24).radius(3, 6).fromSky(12))
						.hit(damage(1.5D), bind(BindKind.STUN, 30)).phase(0))
				.step(at(0).stage(2).shape(wave().r1(8, 16).speed(1.5D)).hit(damage(1.2D), knock(1.2D)).phase(1))
				.step(at(0).stage(3).shape(pillar(Anchor.AIM).onTargets(6, 10).radius(2).delay(10)).hit(launch(0.8D)).phase(2))
				.step(at(40).stage(3).shape(recall()).hit(slam(0.6D), damage(1.5D)).phase(2))
				.step(at(0).stage(4).shape(zone(Anchor.AIM).range(24).radius(8, 14)).hit(damage(2.5D), bind(BindKind.STUN, 40)).phase(3))
				.fx(Phase.CAST, runeCircle(GOLD).radius(1.2F).count(16).ticks(10))
				.fx(Phase.extra(0), column(STONE).count(24).ticks(10),
						giantBlock(stoneBricks).blockSize(4.0F, 1.0F, 4.0F).fall(12.0F, 8.0F).ticks(30).minTier(DISPLAY_TIER),
						shake(0.1F, 8))
				.fx(Phase.extra(1), shockRing(BLUE).count(40).ticks(16), shake(0.2F, 8))
				.fx(Phase.extra(2), impact(STONE).particle(ParticleTypes.CRIT).count(16).ticks(10), shake(0.3F, 10))
				.fx(Phase.extra(3), shockRing(GOLD).count(48).ticks(16),
						glyph("帝").scale(6.0F).above(4.0F).ticks(40).minTier(DISPLAY_TIER), shake(0.4F, 12))
				.sound(Phase.extra(0), SoundEvents.ANVIL_LAND, 1.0F, 0.6F)
				.sound(Phase.extra(1), SoundEvents.GENERIC_SPLASH, 1.2F, 0.6F)
				.sound(Phase.extra(2), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.8F)
				.sound(Phase.extra(3), SoundEvents.BELL_BLOCK, 1.4F, 0.6F)
				.kehancuran(DShape.pit(2, 2).stage(1), DShape.disc(4, 2).stage(3), DShape.bowl(6).stage(4))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// ============================================================ Misterius, Nascent Soul

		// 107 · Mata Api Pupil Emas (Journey to the West, fiery eyes and golden pupils)
		sink.add(art("fiery_golden_eyes", 107, null)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.INDRA).realm(Realm.NASCENT_SOUL)
				.route(Route.book("minecraft:fortune"))
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(45).cooldown(45.0D)
				.step(at(0).only(Cond.not(Cond.DIVINE_SENSE)).shape(all().radius(24, 32).max(32))
						.hit(strip(MobEffects.INVISIBILITY), mark(Mark.vulnerable(0.2D).perTier(0.05D, 0.4D), 400), mark(Mark.highlight(), 400)))
				// ? Divine Sense active: radius ×1.25 (the 32 hard cap still applies).
				.step(at(0).only(Cond.DIVINE_SENSE).shape(all().radius(30, 32).max(32))
						.hit(strip(MobEffects.INVISIBILITY), mark(Mark.vulnerable(0.2D).perTier(0.05D, 0.4D), 400), mark(Mark.highlight(), 400)))
				.step(at(0).self(buff(BuffKind.NIGHT_VISION, 1, 400)))
				.fx(Phase.CAST, impact(0xFFB000).onCaster().above(1.6F).count(8).ticks(8),
						shockRing(0xFFB000).count(60).ticks(24))
				.fx(Phase.HIT, viewerMark(0xFFB000).ticks(400))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F)
				.perfection(Bonus.durationTicks(100))
				.peak(Bonus.duration(0.25D))
				.build());

		// 108 · Jari Kematian (Renegade Immortal, Finger of Death)
		sink.add(art("finger_of_death", 108, null)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.SERANG).realm(Realm.NASCENT_SOUL)
				.route(Route.book("minecraft:breach"))
				.flag(TEACHABLE, REQUIRES_TARGET)
				.qi(35).cooldown(6.0D)
				.step(at(0).shape(target().range(24, 32)).hit(damage(2.2D).pierce(), exec(0.30D))
						.onKill(at(0).self(restoreQi(0.08D)).phase(0)))
				.fx(Phase.CAST, beamCore(0x2A2A2A).particle(ParticleTypes.SQUID_INK).count(24).ticks(8))
				.fx(Phase.TRAVEL, beamCore(0x404040).particle(ParticleTypes.SOUL).count(16).ticks(8).detail())
				.fx(Phase.HIT, impact(0x555555).particle(ParticleTypes.SOUL).count(8))
				.fx(Phase.extra(0), motesTo(0x9FE8FF).particle(ParticleTypes.SCULK_SOUL).count(12).ticks(20))
				.sound(Phase.CAST, SoundEvents.WITHER_SHOOT, 0.4F, 1.6F)
				.perfection(Bonus.payload(exec(0.35D)))
				.peak(Bonus.echo(0.4D))
				.build());

		// 109 · Tubuh di Luar Tubuh (Journey to the West, Body Beyond Body)
		sink.add(art("body_beyond_body", 109, null)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.PANGGIL).realm(Realm.NASCENT_SOUL)
				.route(Route.pav())
				.flag(NO_TEACH, PAVILION, CAST_ALWAYS)
				.qi(60).cooldown(40.0D)
				.step(at(0).self(summon(Payload.Summon.CLONE, 3, 8, 200).ticks(200, 300).mode(SummonMode.CLING).interval(20).range(12)
						.attack(at(0).shape(nova().radius(1.5D).at(Anchor.SUMMON)).hit(damage(0.25D)))))
				.fx(Phase.CAST, motesTo(GOLD).count(16).ticks(10),
						mist(GOLD).particle(ParticleTypes.LARGE_SMOKE).count(20).ticks(12),
						cloneOf(0xD9A040).scale(0.4F).n(8).ticks(200))
				.fx(Phase.HIT, impact(GOLD).count(4))
				.sound(Phase.CAST, SoundEvents.FOX_AMBIENT, 1.0F, 2.0F)
				.perfection(Bonus.extraCount(2))
				.peak(Bonus.duration(0.25D))
				.build());

		// 110 · Harimau Putih Alam Baka (Soul Land, Netherworld White Tiger) ★ fusion_pair
		sink.add(art("netherworld_white_tiger", 110, null)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.route(Route.realm(), Route.pair(FusionPair.MIN_BOND))
				.special(FusionPair.ID)
				.flag(NO_TEACH, NO_ECHO, CAST_ALWAYS)
				.qi(70).cooldown(40.0D)
				.step(at(FusionPair.KEEP_ALIVE_TICKS))
				.fx(Phase.CAST, shell(0xF4F4FF).color2(0x3A1F5A).onCaster().count(20).ticks(20))
				.fx(Phase.CHARGE, trail(0xF4F4FF).color2(0x3A1F5A).line().count(30).ticks(FusionPair.CHARGE_TICKS),
						shieldShell(0xF4F4FF).color2(0x3A1F5A).count(16).ticks(FusionPair.CHARGE_TICKS))
				.fx(Phase.TRAVEL, beast(FxLayer.BeastKind.TIGER, 0xF4F4FF).color2(0x2A1440).count(80).ticks(16), shake(0.3F, 10))
				.fx(Phase.HIT, impact(0xF4F4FF).color2(0x2A1440).count(8))
				.fx(Phase.extra(0), bolt(0xC8B0FF).count(16).ticks(6))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F)
				.sound(Phase.CHARGE, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F)
				.sound(Phase.TRAVEL, SoundEvents.RAVAGER_ROAR, 1.0F, 1.4F)
				.sound(Phase.extra(0), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.3F)
				.kehancuran(DShape.trench(16, 2, 2))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.special("echo", FusionPair.ECHO_FACTOR))
				.build());

		// ============================================================ Bumi, Deity Transformation

		// 111 · Awan Jungkir Balik (Journey to the West, cloud somersault) ★ cloud_travel
		sink.add(art("somersault_cloud", 111, null)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.GERAK).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.realm())
				.special(CloudTravel.ID)
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(60).cooldown(30.0D)
				.step(at(CloudTravel.KEEP_ALIVE_TICKS))
				.fx(Phase.CAST, mist(WHITE).color2(GOLD).count(24).ticks(12),
						impact(GOLD).particle(ParticleTypes.END_ROD).count(8).ticks(10).detail())
				.fx(Phase.TRAVEL, trail(WHITE).onCaster().count(40).ticks(CloudTravel.RIDE_TICKS),
						mist(WHITE).color2(GOLD).onCaster().count(20).ticks(CloudTravel.RIDE_TICKS).detail())
				.fx(Phase.extra(0), mist(WHITE).color2(GOLD).count(20).ticks(10))
				.sound(Phase.CAST, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.2F)
				.sound(Phase.TRAVEL, SoundEvents.ELYTRA_FLYING, 0.6F, 1.4F)
				.sound(Phase.extra(0), SoundEvents.PHANTOM_FLAP, 0.6F, 1.0F)
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 112 · Istana Suci Qilin (Against the Gods, Qilin Sacred Palace)
		sink.add(art("qilin_sacred_palace", 112, null)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.LINDUNG).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.pav(), Route.book("minecraft:projectile_protection"))
				.flag(TEACHABLE, PAVILION, CAST_ALWAYS)
				.qi(120).cooldown(60.0D)
				// Outward walls keep monsters out; the pulse hits those at the wall. Allies inside: DR .6 (refreshed each pulse).
				.step(at(0).shape(field(Anchor.CASTER).radius(4, 8).duration(120, 200).pulse(20).walls(Shape.Field.Walls.OUT))
						.hit(damage(0.5D), knock(1.2D))
						.ally(dr(0.6D, Facing.ALL, 30)))
				.step(at(0).self(projectileGuard(360.0D, 8.0D, 64, 120).ticks(120, 200).wholeArea()))
				.fx(Phase.CAST, dome(GOLD).count(60).ticks(120),
						runeCircle(0xC0302A).color2(GOLD).above(7.0F).radius(6.0F).count(24).ticks(120).minTier(GLYPH_TIER),
						giantBlock(gold).blockSize(1.0F, 6.0F, 1.0F).n(4).radius(6.0F).ticks(120).minTier(DISPLAY_TIER),
						shake(0.2F, 8))
				.fx(Phase.PULSE, dome(GOLD).count(20).ticks(20).detail())
				.sound(Phase.CAST, SoundEvents.RAVAGER_ROAR, 1.0F, 1.3F)
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.2F, 0.8F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 113 · Tujuh Puluh Dua Perubahan (Journey to the West, seventy-two earthly transformations) ★ seventy_two
		sink.add(art("seventy_two_transformations", 113, null)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.UBAH).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.feat(Feat.meditateAt(Cond.site("mountain"), 10)))
				.special(SeventyTwo.ID)
				.flag(NO_TEACH, CAST_ALWAYS)
				// "qi 50 per change"; the 10 s cooldown after the form ends is kept by the handler.
				.qi(50).cooldownTicks(10)
				.step(at(SeventyTwo.KEEP_ALIVE_TICKS))
				.fx(Phase.CAST, mist(GOLD).particle(ParticleTypes.LARGE_SMOKE).count(24).ticks(12),
						glyph("变").scale(2.0F).above(2.5F).ticks(20).minTier(DISPLAY_TIER))
				.fx(Phase.extra(0), mist(SeventyTwo.Form.HARIMAU.color()).onCaster().count(16).ticks(20))
				.fx(Phase.extra(1), mist(SeventyTwo.Form.ELANG.color()).onCaster().count(16).ticks(20))
				.fx(Phase.extra(2), mist(SeventyTwo.Form.IKAN.color()).onCaster().count(16).ticks(20))
				.fx(Phase.extra(3), mist(SeventyTwo.Form.SERANGGA.color()).onCaster().count(16).ticks(20))
				.fx(Phase.extra(4), mist(SeventyTwo.Form.KUIL.color()).onCaster().count(16).ticks(20))
				.fx(Phase.extra(5), shockRing(SeventyTwo.Form.ELANG.color()).count(30).ticks(10), shake(0.25F, 8))
				.sound(Phase.CAST, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F)
				.sound(Phase.extra(5), SoundEvents.PHANTOM_SWOOP, 1.0F, 0.8F)
				.kehancuran(DShape.disc(3, 1))
				.perfection(Bonus.special("form_drain", 0.5D))
				.peak(Bonus.special("form_qi_free", 0.0D))
				.build());

		// 114 · Tiga Kepala Enam Lengan (Journey to the West ch. 4; Desolate Era)
		sink.add(art("three_heads_six_arms", 114, null)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.UBAH).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.realm())
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(100).cooldown(60.0D)
				.step(at(0).self(transform("three_heads_six_arms", 400, 600).with(
						hook(Hook.onMelee(at(0).shape(all().at(Anchor.HIT).radius(4).max(2)).hit(damage(0.6D))).icd(4)),
						buff(BuffKind.ATTACK_SPEED, 0.5D, 400, 600),
						new Payload.Dr(Val.of(0.5D), Facing.BACK, Val.of(400, 600), false, 0.0D),
						new Payload.Buff(BuffKind.CD_RATE, 1.25D, 0.0D, Val.of(400, 600), null, false, false, 1))))
				.fx(Phase.CAST, shell(GOLD).onCaster().count(24).ticks(12),
						avatar(0xFFE08A).variant(FxLayer.Variant.SPLIT).ticks(400).minTier(DISPLAY_TIER))
				.fx(Phase.HIT, arc(0xFFE08A).count(10).ticks(6))
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.2F, 0.7F)
				.sound(Phase.CAST, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.0F)
				.perfection(Bonus.durationTicks(100))
				.peak(Bonus.duration(0.25D))
				.build());

		// 115 · Dunia Kematian Darah (I Shall Seal the Heavens, Blood Finger and Blood Death World)
		sink.add(art("blood_death_world", 115, null)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.WILAYAH).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.realm())
				.flag(NO_TEACH, FORBIDDEN, CAST_ALWAYS)
				.qi(90).cooldown(75.0D)
				.step(at(0).shape(domain().radius(10, 18).duration(160, 240).pulse(20))
						.hit(dot(0.35D, 2, DotKind.BLEED), eff(Eff.BLINDNESS, 1, 30), eff(Eff.SLOWNESS, 2, 30), drain(0.3D)))
				.step(at(0).self(forbiddenCost(0.20D, true, 0.0D)))
				// "Blood Finger" every 20t on the weakest monster inside.
				.step(at(20).repeat(12, 20).shape(all().ofArea().max(1).order(Shape.All.Order.WEAKEST))
						.hit(damage(1.2D), exec(0.10D)).phase(0))
				.fx(Phase.CAST, dome(BLOOD).count(60).ticks(160),
						runeCircle(0xA00000).radius(8.0F).count(30).ticks(160).minTier(GLYPH_TIER),
						vignette(BLOOD, 40), shake(0.25F, 10))
				.fx(Phase.PULSE, mist(BLOOD).count(30).ticks(20))
				.fx(Phase.extra(0), beamCore(CRIMSON).count(12).ticks(6))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 0.8F)
				.sound(Phase.PULSE, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 0.9F)
				.kehancuran(DShape.wither(10))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// ============================================================ Bumi, Dao Union

		// 116 · Pelarian Bayangan Darah (A Record of a Mortal's Journey, Bloodshadow Evasion)
		sink.add(art("bloodshadow_evasion", 116, null)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.GERAK).realm(Realm.DAO_UNION)
				.route(Route.book("minecraft:vanishing_curse"))
				.flag(NO_TEACH, FORBIDDEN, CAST_ALWAYS)
				.qi(0).cooldown(120.0D)
				.step(at(0).shape(nova().radius(16).at(Anchor.ORIGIN)).hit(untarget()))
				.step(at(0).self(forbiddenCost(0.30D, true, 0.0D), blink(BlinkMode.BACK, 32, 64).safeFrom(8.0D),
						dr(0.8D, Facing.ALL, 60), buff(BuffKind.NO_FALL, 1, 100), selfPenalty(600, 1, false, 1.0D)))
				.fx(Phase.CAST, afterimage(CRIMSON).ticks(20), impact(CRIMSON).count(16).ticks(6))
				.fx(Phase.TRAVEL, trail(CRIMSON).count(40).ticks(12))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.2F)
				.sound(Phase.TRAVEL, SoundEvents.ELYTRA_FLYING, 0.8F, 1.6F)
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 117 · Bulan Ditarik Bintang Kembali (Against the Gods, Moon Star Restoration)
		sink.add(art("moon_star_restoration", 117, null)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.LINDUNG).realm(Realm.DAO_UNION)
				.route(Route.feat(Feat.heartDemonWin(Realm.DAO_UNION)))
				.flag(NO_TEACH, FORBIDDEN, CAST_ALWAYS)
				.qi(120).cooldown(45.0D)
				.step(at(0).self(forbiddenCost(0.15D, false, 0.0D),
						counter(40, 1.0D, 2.0D, Store.CANCEL_ALL).window(40, 60).earlyAt(0.5D)
								.release(at(0).shape(nova().radius(6, 14)).hit(damage(2.0D).perStored(2.0D), knock(1.5D)).phase(0))))
				.fx(Phase.CAST, arc(0xDDE6FF).onCaster().size(2.0F).count(20).ticks(40),
						motesTo(0xE8F0FF).particle(ParticleTypes.END_ROD).count(24).ticks(40))
				.fx(Phase.extra(0), starfield(0xE8F0FF).count(40).ticks(20), shell(0xE8F0FF).count(40).ticks(12), shake(0.3F, 10))
				.sound(Phase.CAST, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.0F)
				.sound(Phase.extra(0), SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.0F, 1.0F)
				.kehancuran(DShape.disc(4, 2).atRelease())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 118 · Alam Ji (Renegade Immortal, Wang Lin's Ji Realm)
		sink.add(art("ji_realm", 118, null)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.route(Route.feat(Feat.firstKill("minecraft:warden", null, Realm.DAO_UNION, -1)))
				.flag(NO_TEACH)
				.qi(160).cooldown(60.0D)
				.step(at(0).shape(cone().length(16, 24).halfAngle(30))
						.hit(damage(2.4D).pierce(), exec(0.30D), execBelow(2.0D), eff(Eff.SLOWNESS, 4, 60))
						.self(selfPenalty(300, 0, true, 1.0D)))
				.fx(Phase.CAST, coneSpray(0xF0F0F0).particle(ParticleTypes.WHITE_ASH).count(60).ticks(16), shake(0.3F, 10))
				.fx(Phase.HIT, motesTo(0xCFEFFF).particle(ParticleTypes.SCULK_SOUL).count(6).ticks(20))
				.sound(Phase.CAST, SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.3F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 119 · Fusi Misteri Mendalam (Coiling Dragon, fused profound mysteries) ★ mystery_fusion
		sink.add(art("profound_mystery_fusion", 119, null)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.comprehension(60).route(Route.comp(60), Route.secondDao((int) MysteryFusion.SECOND_DAO_MIN))
				.special(MysteryFusion.ID)
				.flag(NO_TEACH, NO_ECHO, CAST_ALWAYS)
				.qi(160).cooldown(30.0D)
				.step(at(MysteryFusion.KEEP_ALIVE_TICKS))
				.fx(Phase.CAST, shell(WHITE).onCaster().count(16).ticks(10))
				// TRAVEL / EXTRA0 count = (main Dao + 1) + 16 × (second Dao + 1): the client draws them in Dao colours.
				.fx(Phase.TRAVEL, trail(WHITE).count(30).ticks(16), trail(WHITE).variant(FxLayer.Variant.MIRRORED).count(30).ticks(16))
				.fx(Phase.extra(0), shell(WHITE).count(60).ticks(12), shake(0.3F, 10))
				.fx(Phase.extra(1), impact(WHITE).count(12).ticks(8))
				.sound(Phase.TRAVEL, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F)
				.sound(Phase.extra(0), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.8F)
				.sound(Phase.extra(0), SoundEvents.MACE_SMASH_GROUND, 1.0F, 1.0F)
				.kehancuran(DShape.sphere(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.special("echo", MysteryFusion.ECHO_FACTOR))
				.build());

		// 120 · Teratai Tang Murka Buddha (Soul Land, Buddha's Fury Tang Lotus)
		sink.add(art("buddha_wrath_tang_lotus", 120, null)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.route(Route.pav())
				.flag(TEACHABLE, PAVILION)
				.qi(140).cooldown(40.0D)
				// The thrown lotus (visual travel to the aim, ≤ 24), then the 108-blade bursts at the landing.
				.step(at(0).shape(volley().count(1).range(24).seek()))
				.step(at(10).repeat(3, 4).shape(zone(Anchor.AIM).range(24).radius(10, 16)).hit(damage(0.7D), eff(Eff.POISON, 2, 100)))
				.step(at(10).shape(zone(Anchor.AIM).range(24).radius(4, 6)).hit(damage(2.5D)))
				.fx(Phase.CAST, impact(0x7FE0A0).count(10).ticks(6))
				.fx(Phase.TRAVEL, trail(0x7FE0A0).count(20).ticks(10))
				.fx(Phase.IMPACT, shards(SILVER).count(108).ticks(12), trail(SILVER).count(54).ticks(10).detail(),
						petalRing(jadeGlass, 8, 3.0F).ticks(30).minTier(DISPLAY_TIER), shake(0.4F, 10))
				.sound(Phase.IMPACT, SoundEvents.GLASS_BREAK, 1.2F, 1.0F)
				.sound(Phase.IMPACT, SoundEvents.BELL_BLOCK, 1.0F, 1.2F)
				// SPHERE 3 only when the burst is within 3 blocks of the ground (22.9.9 KH cell; one read-only downward scan)
				.kehancuran(DShape.sphere(3).nearGround(3))
				.perfection(Bonus.extraWave(1))
				.peak(Bonus.echo(0.4D))
				.build());

		// ============================================================ Langit, Immortal Ascension

		// 121 · Sayap Angin-Petir Han Li (A Record of a Mortal's Journey, Wind-Thunder Wings)
		sink.add(art("wind_thunder_wings", 121, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.GERAK).realm(Realm.IMMORTAL_ASCENSION, 1)
				.route(Route.feat(Feat.killsWith(null, Cond.THUNDERING, List.of("minecraft:breeze"), 1)))
				.flag(TEACHABLE, CAST_ALWAYS)
				.qi(110).charges(2, 160).cooldownTicks(160)
				.step(at(0).self(dash(24, 40).ticks(2).iframes(4)))
				.step(at(2).shape(path().halfWidth(2.5D)).hit(damage(3.0D), bind(BindKind.STUN, 20))
						.onEnd(at(0).shape(chain().from(Anchor.HIT).jumps(3, 6).jump(6)).hit(damage(1.5D))))
				.fx(Phase.CAST, wings(0xC0D8FF).count(30).ticks(20), shake(0.3F, 8))
				.fx(Phase.TRAVEL, trail(0xB0C8FF).particle(ParticleTypes.ELECTRIC_SPARK).count(40).ticks(6),
						bolt(0xC8D8FF).count(12).ticks(4).extra())
				.fx(Phase.HIT, bolt(0xC8D8FF).count(8).ticks(4))
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.4F)
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 1.0F, 1.2F)
				.kehancuran(DShape.trenchPath(1, 1))
				.perfection(Bonus.extraCount(1))
				.peak(Bonus.duration(0.25D))
				.build());

		// 122 · Tubuh Emas Pantang Tunduk (Martial Peak, Proud Golden Body)
		sink.add(art("proud_golden_body", 122, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.LINDUNG).realm(Realm.IMMORTAL_ASCENSION, 1)
				.route(Route.realm())
				.flag(TEACHABLE, CAST_ALWAYS)
				.qi(150).cooldown(75.0D)
				.step(at(0).self(dr(0.6D, Facing.ALL, 500).hpScaled(0.2D), empower(1.0D, 500).hpScaled(0.0D),
						hook(Hook.onHurt(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(dot(0.4D, 3, DotKind.FIRE), ignite(3.0D)))
								.icd(10).ticks(500))))
				.fx(Phase.CAST, shieldShell(GOLD).count(30).ticks(500), mist(GOLD).onCaster().count(12).ticks(40))
				.fx(Phase.HIT, impact(GOLD).count(8))
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 0.8F, 0.5F)
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F)
				.perfection(Bonus.durationTicks(100))
				.peak(Bonus.duration(0.25D))
				.build());

		// 123 · Rupa Langit Wujud Bumi (Journey to the West, Modeling Heaven on Earth)
		{
			var builder = art("fa_tian_xiang_di", 123, null)
					.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.UBAH).realm(Realm.IMMORTAL_ASCENSION, 1)
					.route(Route.realm())
					.flag(NO_TEACH, CAST_ALWAYS)
					// ST: stage 1 grows the caster, every later recast inside the form stomps (≥ 60t apart; the form drains 5 qi/s).
					.stages(FA_TIAN_STAGES, 900, 60).qi(250).qiPerSecond(5.0D).cooldown(150.0D)
					// SCALE ×3, rising to ×4 at True Immortal (lin on the tier index); the head-room fallback is ×2 as Dharma Form.
					.step(at(0).stage(1).self(transform("fa_tian_xiang_di", 600, 900).scale(Val.lin(3.0D, 0.34D, 4.0D)).with(
							buff(BuffKind.STRENGTH, 3, 600, 900),
							buff(BuffKind.REACH, 6, 600, 900),
							hook(Hook.onMelee(at(0).shape(strike().range(6).arc(140)).hit(damage(2.0D))).icd(10)))));
			for (int stage = 2; stage <= FA_TIAN_STAGES; stage++) {
				builder.step(at(0).stage(stage).shape(nova().radius(6, 12)).hit(damage(3.0D), launch(0.6D)).phase(0));
			}
			int[] stageQi = new int[FA_TIAN_STAGES];
			stageQi[0] = 250;
			sink.add(builder.qiPerStage(stageQi)
					.fx(Phase.CAST, shell(GOLD).onCaster().count(30).ticks(16),
							avatar(GOLD).scale(3.0F).ticks(600).minTier(DISPLAY_TIER))
					.fx(Phase.extra(0), shockRing(0xC8B08A).count(48).ticks(14), shake(0.6F, 12))
					.sound(Phase.CAST, SoundEvents.RAVAGER_ROAR, 0.5F, 0.6F)
					.sound(Phase.extra(0), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.7F)
					// DISC r4 d2 nat at each stomp (the stomps share the shape's part of the one cap)
					.kehancuran(DShape.disc(4, 2).nat().eachStage())
					.naturalOnly()
					.perfection(Bonus.area(0.25D))
					.peak(Bonus.duration(0.25D))
					.build());
		}

		// 124 · Hati Membara (Against the Gods, Burning Heart)
		sink.add(art("burning_heart", 124, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.UBAH).realm(Realm.IMMORTAL_ASCENSION, 1)
				.route(Route.feat(Feat.deviationRecovered(Realm.DAO_UNION)))
				.flag(NO_TEACH, FORBIDDEN, CAST_ALWAYS)
				.qi(0).cooldown(240.0D)
				.step(at(0).self(forbiddenCost(0.35D, false, 0.20D), empower(1.0D, 300), costMod(null, 0.5D, 1.0D, 300),
						cdRate(null, 1.4D, 300), buff(BuffKind.KH_RADIUS, 1, 300), selfPenalty(1200, 2, true, 0.7D)))
				.fx(Phase.CAST, mist(0xFF7070).particle(ParticleTypes.FLAME).onCaster().count(30).ticks(300),
						shell(0xFF7070).onCaster().count(24).ticks(10))
				.sound(Phase.CAST, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 1.0F)
				.perfection(Bonus.durationTicks(60))
				.peak(Bonus.duration(0.25D))
				.build());

		// 125 · Segel Hidup-Mati (I Shall Seal the Heavens, the Sixth Hex)
		sink.add(art("life_death_hex", 125, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 2)
				.route(Route.scr(2))
				.flag(TEACHABLE)
				.qi(260).cooldown(75.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(10, 20).duration(200).pulse(20))
						.hit(dot(0.4D, 2, DotKind.SOUL), eff(Eff.SLOWNESS, 2, 40))
						.onKill(at(0).self(heal(0.03D)), at(0).shape(chain().from(Anchor.HIT).jump(4).jumps(2)).hit(damage(1.0D)))
						.onEnd(at(0).shape(all().ofArea().max(32)).hit(exec(0.25D)).when(Cond.TARGET_BOSS, 1.0D, damage(4.0D)).phase(0)))
				.fx(Phase.CAST, vortex(ASH).count(60).ticks(200), vortex(ASH).mirrored().inSky().count(40).ticks(200).detail(),
						giantBlock(greyGlass).blockSize(6.0F, 0.2F, 6.0F).spin(2.0F).ticks(200).minTier(3),
						glyph("生死").scale(6.0F).above(8.0F).ticks(60).minTier(DISPLAY_TIER), shake(0.4F, 12))
				.fx(Phase.PULSE, vortex(ASH).count(20).ticks(20).detail())
				.fx(Phase.extra(0), impact(WHITE).particle(ParticleTypes.SOUL).count(16).ticks(10))
				.sound(Phase.CAST, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.SOUL_ESCAPE, 1.0F, 1.0F)
				.kehancuran(DShape.wither(10))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 126 · Pukulan Tongkat Ruyi (Journey to the West, Ruyi Jingu Bang)
		sink.add(art("ruyi_staff", 126, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 2)
				.route(Route.feat(Feat.firstKill("minecraft:elder_guardian", null, Realm.IMMORTAL_ASCENSION, -1)))
				.flag(TEACHABLE)
				.charge(20).qi(300).cooldown(60.0D)
				.step(at(0).shape(front().length(40, 64).width(3).height(12).speed(8.0D).fromSky(12.0D).ahead(3.0D))
						.hit(damage(9.0D).charged(), launch(0.8D)))
				.step(at(0).minTier(7).shape(front().length(40, 64).width(3).height(12).speed(8.0D).fromSky(12.0D).ahead(3.0D).count(2).fan(40.0D))
						.hit(damage(4.5D).charged()))
				.fx(Phase.CAST, impact(GOLD).onCaster().count(12).ticks(10))
				.fx(Phase.CHARGE, laser(blackGlass, 1.2F, 64.0F).grow(0.02F, 20.0F).ticks(30).minTier(DISPLAY_TIER),
						giantBlock(gold).blockSize(2.0F, 0.5F, 2.0F).n(2).ticks(30).minTier(DISPLAY_TIER),
						column(GOLD).count(30).ticks(20))
				.fx(Phase.TRAVEL, laser(blackGlass, 1.2F, 120.0F).topple(8.0F).ticks(20).minTier(DISPLAY_TIER),
						trail(0x202020).count(40).ticks(8), shake(0.9F, 16), cracks())
				.fx(Phase.HIT, impact(STONE).particle(ParticleTypes.CRIT).count(10))
				.sound(Phase.CHARGE, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F)
				.sound(Phase.TRAVEL, SoundEvents.ANVIL_LAND, 1.2F, 0.5F)
				.sound(Phase.TRAVEL, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.6F)
				.sound(Phase.TRAVEL, SoundCue.of(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3F, 0.6F).delay(6))
				.kehancuran(DShape.trench(48, 3, 4))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 127 · Tiga Helai Rambut Penyelamat (Journey to the West, three life-saving hairs)
		sink.add(art("three_life_saving_hairs", 127, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.LINDUNG).realm(Realm.IMMORTAL_ASCENSION, 3)
				.route(Route.realm())
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(300).cooldown(600.0D)
				// 1 charge, 2 from IA T6 (layer index 5), 3 at True Immortal: by rank, not by tier index (T4-T6 share one).
				// A later step of the same spec replaces the earlier charges, so the highest rank that holds wins.
				.step(at(0).self(hairs(1)))
				.step(at(0).only(Cond.rankAtLeast(Realm.IMMORTAL_ASCENSION, 5)).self(hairs(2)))
				.step(at(0).only(Cond.rankAtLeast(Realm.TRUE_IMMORTAL, 0)).self(hairs(3)))
				.fx(Phase.CAST, healRise(GOLD).particle(ParticleTypes.END_ROD).onCaster().count(12).ticks(40))
				.fx(Phase.extra(0), impact(GOLD).particle(ParticleTypes.TOTEM_OF_UNDYING).count(16).ticks(20),
						mist(GOLD).count(20).ticks(16))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F)
				.sound(Phase.extra(0), SoundEvents.TOTEM_USE, 1.0F, 1.2F)
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 128 · Wilayah Jiwa Naga (Against the Gods, Dragon Soul Domain)
		sink.add(art("dragon_soul_domain", 128, null)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.WILAYAH).realm(Realm.IMMORTAL_ASCENSION, 3)
				.route(Route.feat(Feat.firstKill("minecraft:ender_dragon", null)))
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(240).cooldown(90.0D)
				.step(at(0).shape(domain().radius(12, 24).duration(200, 300).pulse(20))
						.hit(untarget(), dot(0.4D, 2, DotKind.SOUL), eff(Eff.SLOWNESS, 3, 40), knock(0.4D)))
				.step(at(60).repeat(5, 60).shape(all().ofArea().max(32)).hit(bind(BindKind.STUN, 20).boss(5)).phase(0))
				.fx(Phase.CAST, shockRing(DRAGON).count(60).ticks(20), mist(0x7A3FBF).count(40).ticks(200),
						beast(FxLayer.BeastKind.DRAGON, DRAGON).n(3).count(60).ticks(200))
				.fx(Phase.PULSE, mist(0x7A3FBF).count(16).ticks(20).detail())
				.fx(Phase.extra(0), shockRing(DRAGON).count(30).ticks(10))
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 0.3F, 1.0F)
				.sound(Phase.extra(0), SoundEvents.ENDER_DRAGON_GROWL, 0.2F, 1.4F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 129 · Kuali Induk Qi Segala Benda (Shrouded the Heavens, Myriad Things Mother Qi Cauldron)
		sink.add(art("myriad_cauldron", 129, null)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 4)
				.route(Route.feat(Feat.alchemyCount(81)))
				.flag(TEACHABLE)
				.qi(280).cooldown(75.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(40).radius(8, 16).fromSky(24).delay(20))
						.hit(damage(5.0D), pull(0.5D).toward(Center.ANCHOR))
						.onKill(at(0).self(restoreQi(0.03D, 0.15D))))
				// The cauldron settles: one root for its whole stay, then the mother-qi field.
				.step(at(20).shape(zone(Anchor.AIM).range(40).radius(8, 16)).hit(bind(BindKind.ROOT, 80).boss(20)).phase(0))
				.step(at(20).shape(field(Anchor.AIM).range(40).radius(8, 16).walls().duration(80).pulse(20))
						.hit(dot(0.5D, 2, DotKind.QI))
						.onKill(at(0).self(restoreQi(0.03D, 0.15D))))
				.fx(Phase.CAST, cloudDisc(0xE0E0E0).above(24.0F).count(40).ticks(24))
				.fx(Phase.IMPACT, giantBlock(cauldron).blockSize(8.0F, 8.0F, 8.0F).fall(24.0F, 20.0F).ticks(100).minTier(DISPLAY_TIER),
						shockRing(0x6FA87A).count(40).ticks(14), shake(0.7F, 14))
				.fx(Phase.extra(0), mist(0xB0F0C0).count(40).ticks(80))
				.fx(Phase.PULSE, mist(0xB0F0C0).count(20).ticks(20).detail())
				.sound(Phase.IMPACT, SoundEvents.BELL_BLOCK, 1.4F, 0.5F)
				.sound(Phase.IMPACT, SoundEvents.LAVA_POP, 1.0F, 0.8F)
				.sound(Phase.IMPACT, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.7F)
				.kehancuran(DShape.pit(6, 4))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 130 · Rahasia Xing (Shrouded the Heavens, the Xing Secret)
		sink.add(art("xing_secret", 130, null)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.GERAK).realm(Realm.IMMORTAL_ASCENSION, 5)
				.route(Route.enlight())
				.flag(NO_TEACH, CAST_ALWAYS)
				.qi(200).cooldown(90.0D)
				.step(at(0).self(buff(BuffKind.SPEED, 4, 200, 300), buff(BuffKind.ATTACK_SPEED, 1.0D, 200, 300),
						new Payload.Buff(BuffKind.CD_RATE, 2.0D, 0.0D, Val.of(200, 300), null, false, false, 1)))
				.step(at(0).shape(aura().radius(6, 10).duration(200, 300).pulse(20)).hit(eff(Eff.SLOWNESS, 4, 40)))
				.step(at(60).repeat(5, 60).shape(all().ofArea().max(32)).hit(bind(BindKind.STUN, 10)).phase(0))
				.fx(Phase.CAST, runeCircle(0xD8E8FF).onCaster().radius(1.5F).count(24).ticks(200).minTier(GLYPH_TIER),
						impact(0xD8E8FF).particle(ParticleTypes.REVERSE_PORTAL).count(16).ticks(10))
				.fx(Phase.PULSE, trail(0x8040FF).particle(ParticleTypes.REVERSE_PORTAL).onCaster().count(12).ticks(20).detail(),
						afterimage(0xD8E8FF).ticks(10).extra())
				.fx(Phase.extra(0), runeCircle(0xD8E8FF).count(16).ticks(10).minTier(GLYPH_TIER))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HAT, 0.8F, 1.6F))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HAT, 0.8F, 1.3F).delay(6))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HAT, 0.8F, 1.0F).delay(14))
				.perfection(Bonus.durationTicks(60))
				.peak(Bonus.duration(0.25D))
				.build());

		// 131 · Segel Karma (I Shall Seal the Heavens, the Seventh Hex)
		sink.add(art("karmic_hex", 131, null)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 6)
				.route(Route.scr(3))
				.flag(TEACHABLE, REQUIRES_TARGET)
				.qi(300).cooldown(60.0D)
				// When the target dies to the hex, the threads jump once to the linked monster with the most health.
				.step(at(0).shape(target().range(32)).hit(damage(4.0D).pierce())
						.onKill(at(0).shape(all().at(Anchor.HIT).radius(24).max(12, 24).order(Shape.All.Order.STRONGEST))
								.hit(link(0.5D, 240, 0.25D)).phase(0)))
				.step(at(0).shape(all().at(Anchor.TARGET).radius(24).max(12, 24)).hit(link(0.5D, 240, 0.25D)))
				.fx(Phase.CAST, chains(0xD03030).color2(GOLD).count(30).ticks(240))
				.fx(Phase.HIT, chains(0xD03030).color2(GOLD).count(8).ticks(20).detail(),
						glyph("因果").scale(2.5F).above(1.5F).ticks(60).minTier(DISPLAY_TIER))
				.fx(Phase.extra(0), chains(0xD03030).color2(GOLD).count(20).ticks(240))
				.sound(Phase.CAST, SoundEvents.CHAIN_PLACE, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F)
				.perfection(Bonus.durationTicks(80))
				.peak(Bonus.echo(0.4D))
				.build());

		// 132 · Roda Enam Jalan Reinkarnasi (Perfect World, Six Dao Reincarnations Heavenly Art) ★ echo_wheel
		sink.add(art("six_dao_reincarnation", 132, null)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 7)
				.route(Route.realm(), Route.known(30))
				.special(EchoWheel.ID)
				.flag(NO_TEACH, NO_ECHO, CAST_ALWAYS)
				.qi(380).cooldown(180.0D)
				.step(at(EchoWheel.KEEP_ALIVE_TICKS))
				.fx(Phase.CAST, runeCircle(WHITE).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(60).ticks(60),
						shake(0.2F, 8))
				.fx(Phase.extra(0), runeCircle(0xFF5050).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(1), runeCircle(0xFFB040).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(2), runeCircle(0xFFF060).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(3), runeCircle(0x60E070).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(4), runeCircle(0x50A0FF).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(5), runeCircle(0xB060FF).variant(FxLayer.Variant.VERTICAL).onCaster().radius(10.0F).count(20).ticks(8))
				.fx(Phase.extra(6), shell(WHITE).count(80).ticks(12), shake(0.6F, 14))
				.sound(Phase.CAST, SoundEvents.BELL_RESONATE, 1.0F, 1.0F)
				.sound(Phase.extra(0), SoundEvents.BELL_BLOCK, 1.0F, 0.6F)
				.sound(Phase.extra(1), SoundEvents.BELL_BLOCK, 1.0F, 0.7F)
				.sound(Phase.extra(2), SoundEvents.BELL_BLOCK, 1.0F, 0.8F)
				.sound(Phase.extra(3), SoundEvents.BELL_BLOCK, 1.0F, 0.9F)
				.sound(Phase.extra(4), SoundEvents.BELL_BLOCK, 1.0F, 1.0F)
				.sound(Phase.extra(5), SoundEvents.BELL_BLOCK, 1.0F, 1.1F)
				.sound(Phase.extra(6), SoundEvents.BELL_RESONATE, 1.4F, 0.8F)
				.perfection(Bonus.extraCount(1))
				.peak(Bonus.special(EchoWheel.PEAK_KEY, EchoWheel.PEAK_NOVA))
				.build());

		// 133 · Jari Takdir Surgawi (Renegade Immortal, Heavenly Fate Finger)
		Step fateHit = at(0).hit(damage(12.0D).pierce().charged(), bind(BindKind.ROOT, 60).boss(15))
				.onHit(at(0).shape(nova().radius(6).at(Anchor.HIT)).hit(damage(3.0D)));
		sink.add(art("heavenly_fate_finger", 133, null)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 9)
				.route(Route.realm()).prereq("divine_sense", 3)
				.flag(NO_TEACH, REQUIRES_TARGET)
				.charge(20).qi(320).cooldown(90.0D)
				// LOS, or none needed while the caster's Divine Sense glow is active.
				.step(fateHit.only(Cond.not(Cond.DIVINE_SENSE)).shape(target().range(64)))
				.step(fateHit.only(Cond.DIVINE_SENSE).shape(target().range(64).noLos()))
				.fx(Phase.CAST, cloudDisc(0xF8F8FF).above(40.0F).count(60).ticks(30))
				.fx(Phase.CHARGE, cloudDisc(0xF8F8FF).above(40.0F).count(40).ticks(20), runeCircle(WHITE).count(20).ticks(20).minTier(GLYPH_TIER))
				.fx(Phase.HIT, laser(whiteGlass, 3.0F, 40.0F).ticks(16).minTier(DISPLAY_TIER), column(WHITE).count(40).ticks(12),
						runeCircle(WHITE).onTarget().radius(4.0F).count(24).ticks(20).minTier(GLYPH_TIER),
						flash(WHITE, 0.3F, 6), shake(1.0F, 16))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.5F)
				.sound(Phase.HIT, SoundEvents.BELL_BLOCK, 1.6F, 0.5F)
				.kehancuran(DShape.column(2, 16))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());
	}

	private UniversalArts() {
	}
}

package dev.kultivator.technique.arts;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec.Flag;
import dev.kultivator.data.TechniqueSpec.Gate;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.special.SpaceFold;
import dev.kultivator.technique.special.TimeBank;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload.AttrKind;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.Center;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Payload.Facing;
import dev.kultivator.technique.spec.dsl.Payload.Store;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Ruang / Space catalogue (DESIGN 22.9.8, net ids 900-912): 13 specs, plus the two specials of this set,
 * {@code space_fold} ({@link SpaceFold}, used by {@code spatial_ancestral_symbol}) and {@code time_bank}
 * ({@link TimeBank}, used by {@code time_spirit_domain}). Builder DSL only (22.2.5); no block is ever touched here, the
 * KH shapes are carved only by {@code Kehancuran.submitSelf} when the player's own switch is on (22.11). Numbers are
 * the catalogue's ({@code b→c} = base → spec cap); the level 3 {@code perfection} bonuses are game design picks of 22.6,
 * the level 4 {@code peak} is ECHO 0.4 where 22.3.4 allows it and DURATION 0.25 otherwise. Follow-up stages of a STAGES
 * spec cost no qi (they finish the same art).
 */
public final class SpaceArts {

	/** Space purple, void black, gold and the white of torn space. */
	private static final int PURPLE = 0x5A3FD0;
	private static final int RUNE = 0x9040FF;
	private static final int VOID = 0x101018;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xC01818;
	private static final int TIME = 0x9AB0C8;
	private static final int ACCRETION = 0xFFA040;

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(SpaceFold.ID, new SpaceFold());
		sink.special(TimeBank.ID, new TimeBank());

		BlockState blackGlass = Blocks.STAINED_GLASS.black().defaultBlockState();
		BlockState whiteGlass = Blocks.STAINED_GLASS.white().defaultBlockState();

		// 900 · Bilah Ruang (Martial Peak ch. 1139)
		sink.add(art("space_blade", 900, Dao.SPACE)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.SWORD_SLASH)
				.route(Route.dao()).comprehension(10)
				.qi(22).cooldown(4.0D)
				.step(at(0).shape(beam().length(20, 48).halfWidth(0.6D).pierce()).hit(damage(1.5D).pierce()))
				.fx(Phase.CAST, impact(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).onCaster().above(1.2F).count(10).ticks(6))
				.fx(Phase.TRAVEL,
						beamCore(VOID).count(40).ticks(10),
						beamCore(0xFFFFFF).particle(ParticleTypes.END_ROD).count(16).detail(),
						laser(blackGlass, 0.3F, 48.0F).ticks(14),
						trail(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).count(16).ticks(10).extra())
				.fx(Phase.HIT, impact(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).count(12))
				.sound(Phase.TRAVEL, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.8F)
				.sound(Phase.CAST, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 2.0F)
				.kehancuran(DShape.trench(16, 1, 2))
				.perfection(Bonus.extraCount(2, 0.6D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 901 · Tali Pengikat Abadi (Investiture of the Gods ch. 53-54)
		sink.add(art("immortal_binding_rope", 901, Dao.SPACE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.KENDALI).realm(Realm.GOLDEN_CORE)
				.pose(Pose.FINGER_POINT).pose(Phase.EXTRA0, Pose.CHARGE_BACK).bindLook(dev.kultivator.technique.spec.run.BindKind.ROPE)
				.route(Route.book("minecraft:loyalty"))
				.flag(Flag.REQUIRES_TARGET)
				.stages(2, 60).qiPerStage(28, 0)
				.qi(28).cooldown(10.0D)
				.step(at(0).stage(1).shape(chain().range(20, 40).jumps(2, 6).jump(4).falloff(1.0D))
						.hit(bind(BindKind.ROOT, 80).boss(30), damage(0.5D)))
				.step(at(0).stage(2).shape(recall()).hit(pull(6.0D).blocks()).phase(0))
				.fx(Phase.CAST,
						chains(GOLD).count(30).ticks(16),
						model("chain_link_gold").onTarget().scale(1.5F).spin(6.0F).ticks(80))
				.fx(Phase.HIT, chains(GOLD).count(12).ticks(80))
				.fx(Phase.EXTRA0, chains(GOLD).count(24).ticks(10))
				.sound(Phase.CAST, SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 0.8F)
				.perfection(Bonus.chain(2))
				.peak(Bonus.echo(0.4D))
				.build());

		// 902 · Gelang Qiankun (Investiture of the Gods ch. 12)
		sink.add(art("qiankun_ring", 902, Dao.SPACE)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.SERANG).realm(Realm.NASCENT_SOUL)
				.pose(Pose.PALM_THRUST)
				.route(Route.comp(30), Route.book("minecraft:infinity"))
				.flag(Flag.REQUIRES_TARGET)
				.qi(50).cooldown(12.0D)
				.step(at(0).shape(chain().range(24).jumps(4, 12).jump(8).falloff(0.85D)).hit(damage(1.8D), knock(0.8D)))
				.fx(Phase.CAST, runeCircle(GOLD).n(16).radius(0.8F).spin(30.0F).onCaster().count(16).ticks(10))
				.fx(Phase.TRAVEL,
						trail(GOLD).count(20),
						runeCircle(GOLD).n(16).radius(0.8F).spin(30.0F).count(16).ticks(6).detail())
				.fx(Phase.HIT, impact(GOLD).particle(ParticleTypes.FIREWORK).count(12))
				.fx(Phase.END, trail(GOLD).count(16))
				.fx(Phase.TRAVEL, model("qiankun_ring").scale(1.5F).slide(4.0F).ticks(6).facing(FxLayer.Facing.FLAT))
				.sound(Phase.CAST, SoundEvents.TRIDENT_THROW, 1.0F, 1.4F)
				.sound(Phase.HIT, SoundEvents.BELL_BLOCK, 0.8F, 1.2F)
				.perfection(Bonus.chain(2))
				.peak(Bonus.echo(0.4D))
				.build());

		// 903 · Ruang Gravitasi (Coiling Dragon, Books 15-16)
		sink.add(art("gravitational_space", 903, Dao.SPACE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.KENDALI).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.comp(30), Route.book("minecraft:silk_touch"))
				.qi(110).cooldown(40.0D)
				.step(at(0).shape(domain().radius(10, 24).height(12).duration(240, 360).pulse(20))
						.hit(damage(0.3D), eff(Eff.SLOWNESS, 4, 40), attrPct(AttrKind.JUMP_STRENGTH, -1.0D, 40),
								attrPct(AttrKind.FLYING_SPEED, -1.0D, 40), attrPct(AttrKind.FALL_DAMAGE_MULTIPLIER, 1.0D, 40),
								slam(0.3D)))
				.fx(Phase.CAST,
						vortex(0x3A1060).inward().particle(ParticleTypes.REVERSE_PORTAL).count(60).ticks(30),
						shake(0.1F, 10),
						cracks())
				.fx(Phase.PULSE,
						mist(0x2A0A50).count(40).ticks(20),
						vortex(0x3A1060).inward().particle(ParticleTypes.REVERSE_PORTAL).count(30).ticks(20).detail())
				.sound(Phase.CAST, SoundEvents.BEACON_AMBIENT, 1.2F, 0.5F)
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 0.8F, 0.5F)
				.kehancuran(DShape.disc(6, 1).nat())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 904 · Bilah Rembulan (Martial Peak)
		sink.add(art("moon_blade", 904, Dao.SPACE)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.SWORD_SLASH)
				.route(Route.comp(60), Route.pav())
				.flag(Flag.PAVILION)
				.qi(100).cooldown(18.0D)
				.step(at(0).shape(front().length(32, 64).width(4).height(3).speed(4).ignoreBlocks().count(3).fan(15).perTarget(2))
						.hit(damage(2.0D).pierce()))
				.fx(Phase.CAST, arc(0xFFFFFF).particle(ParticleTypes.END_ROD).count(30).ticks(8))
				.fx(Phase.TRAVEL,
						crescentFly(0xFFFFFF).count(50),
						crescentFly(VOID).particle(ParticleTypes.REVERSE_PORTAL).count(20).detail(),
						laser(whiteGlass, 0.2F, 4.0F).slide(16.0F).ticks(16))
				.fx(Phase.HIT, impact(0xFFFFFF).particle(ParticleTypes.END_ROD).count(10))
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F)
				.sound(Phase.CAST, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.0F)
				.kehancuran(DShape.trench(24, 1, 2).times(3).each())
				.perfection(Bonus.extraCount(2, 0.6D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 905 · Labu Merah Ungu-Emas (Journey to the West ch. 32-35)
		sink.add(art("purple_gold_gourd", 905, Dao.SPACE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.SERANG).realm(Realm.DAO_UNION)
				.pose(Pose.HOLD_ITEM_UP).pose(Phase.EXTRA0, Pose.HOLD_ITEM_UP)
				.route(Route.book("minecraft:piercing"), Route.pav())
				.flag(Flag.NO_TEACH, Flag.PAVILION, Flag.REQUIRES_TARGET)
				.qi(160).cooldown(30.0D)
				.step(at(0).shape(target().range(24).noLos()).hit(mark(Mark.highlight(), 20)))
				.step(at(20).shape(target().range(24).noLos())
						.hit(execBelow(10.0D))
						.when(Cond.all(Cond.targetTough(10.0D), Cond.not(Cond.TARGET_BOSS)), 1.0D, damage(4.0D))
						.when(Cond.TARGET_BOSS, 1.0D, damage(3.0D), eff(Eff.SLOWNESS, 4, 80))
						.phase(0))
				.fx(Phase.CAST,
						mist(RED).color2(GOLD).above(3.0F).count(40).ticks(20),
						vortex(GOLD).above(3.0F).count(20).ticks(20).detail())
				.fx(Phase.HIT, glyph("名").scale(2.0F).above(1.5F).onTarget().ticks(20).minTier(1))
				.fx(Phase.EXTRA0,
						vortex(RED).inward().count(40).ticks(16),
						motesTo(GOLD).count(30).ticks(16).detail())
				.fx(Phase.CAST, model("zijin_gourd").fromHand().above(1.5F).scale(1.5F).ticks(30).until(Phase.EXTRA0))
				.fx(Phase.EXTRA0, model("zijin_gourd").fromHand().above(1.5F).scale(1.5F).ticks(20), model("gourd_stopper").fromHand().above(2.3F).scale(1.0F).rise(1.5F, 6.0F).ticks(12))
				.sound(Phase.CAST, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.BOTTLE_FILL_DRAGONBREATH, 1.0F, 1.0F)
				.sound(Phase.EXTRA0, SoundCue.of(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F).delay(6))
				.perfection(Bonus.cooldown(0.2D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 906 · Segel Pembalik Langit (Investiture of the Gods ch. 46)
		sink.add(art("heaven_turning_seal", 906, Dao.SPACE)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.pose(Pose.SKY_RAISE).pose(Phase.IMPACT, Pose.OVERHEAD_SLAM)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.SPACE, Cond.IN_END, null, 100)))
				.flag(Flag.PAVILION)
				.qi(220).cooldown(50.0D)
				.step(at(0).shape(zone(Anchor.AIM).range(40).radius(7, 16).fromSky(30).delay(20))
						.hit(damage(3.5D), bind(BindKind.ROOT, 40).boss(10)))
				.step(at(20).shape(wave().from(Anchor.AIM).r1Previous(4.0D).speed(2.0D)).hit(damage(1.0D), knock(1.0D)))
				.fx(Phase.CAST,
						runeCircle(0xC8A050).radius(8.0F).count(40).ticks(20),
						mist(0xC8A050).inSky().count(20).ticks(20).detail())
				// the seal falls on the pre-impact TRAVEL (30 up, 20 ticks) and lands exactly on the IMPACT
				.fx(Phase.TRAVEL, model("fantian_seal").scale(8.0F).fall(30.0F, 20.0F).ticks(24).facing(FxLayer.Facing.FLAT),
						glyph("番天").scale(6.0F).fall(30.0F, 20.0F).ticks(22))
				.fx(Phase.IMPACT,
						flash(0xFFE0A0, 0.3F, 6),
						shockRing(0x8A7A60).radius(16.0F).count(60),
						impact(0xC8A050).count(30).detail(),
						shake(0.45F, 14))
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 1.0F, 0.4F)
				.sound(Phase.IMPACT, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.8F)
				.sound(Phase.IMPACT, SoundEvents.GENERIC_EXPLODE, 1.4F, 0.8F)
				.kehancuran(DShape.pit(5, 4))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 907 · Simbol Leluhur Ruang (Martial Universe ch. 1209), special space_fold
		sink.add(art("spatial_ancestral_symbol", 907, Dao.SPACE)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Pose.SEAL)
				.route(Route.comp(70), Route.inh("space"))
				.special(SpaceFold.ID)
				.flag(Flag.NO_TEACH, Flag.NO_ECHO)
				.stages(SpaceFold.STAGES, SpaceFold.WINDOW).qiPerStage(280, 0, 0, 0, 0, 0, 0, 0)
				.qi(280).cooldown(90.0D)
				.step(at(0).stage(1).shape(field(Anchor.AIM).range(SpaceFold.RANGE).radius(SpaceFold.RADIUS, SpaceFold.RADIUS_CAP)
								.height(SpaceFold.HEIGHT).duration(SpaceFold.DURATION).pulse(20))
						.hit(damage(0.5D).pierce()))
				.fx(Phase.CAST,
						runeCircle(RUNE).variant(FxLayer.Variant.VERTICAL).radius(16.0F).count(60).ticks(40),
						mist(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).count(40).ticks(30),
						glyph("空").scale(12.0F).above(10.0F).ticks(200),
						shake(0.2F, 10))
				.fx(Phase.PULSE, runeCircle(RUNE).radius(16.0F).count(30).ticks(20), mist(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).count(20).detail())
				.fx(Phase.EXTRA0, vortex(PURPLE).inward().particle(ParticleTypes.REVERSE_PORTAL).radius(16.0F).count(60).ticks(16), shake(0.2F, 8))
				.fx(Phase.EXTRA1, trail(PURPLE).particle(ParticleTypes.REVERSE_PORTAL).count(24), afterimage(RUNE).count(12).detail())
				.fx(Phase.CAST, model("space_symbol").above(10.0F).scale(6.0F).ticks(200).facing(FxLayer.Facing.FACE_VIEWER))
				.sound(Phase.CAST, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.PORTAL_TRIGGER, 0.5F, 1.8F)
				.sound(Phase.EXTRA0, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.8F)
				.sound(Phase.EXTRA1, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F)
				.kehancuran(DShape.sphere(6).atRelease())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 908 · Kantong Benih Alis Kuning (Journey to the West ch. 65-66)
		sink.add(art("yellow_brow_bag", 908, Dao.SPACE)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 4)
				.pose(Pose.HOLD_ITEM_UP).castTicks(160)
				.route(Route.comp(75), Route.pav())
				.flag(Flag.NO_TEACH, Flag.PAVILION, Flag.NO_ECHO)
				.qi(350).cooldown(110.0D)
				.step(at(0).shape(all().at(Anchor.AIM).range(24).radius(20, 32).max(32))
						.hit(pull(1.0D).perTick(60).toward(Center.ANCHOR))
						.only(Cond.not(Cond.TARGET_BOSS)))
				.step(at(60).shape(all().at(Anchor.AIM).range(24).radius(20, 32).max(32))
						.hit(bind(BindKind.SUSPEND, 100), dot(0.5D, 5, DotKind.QI)).phase(0))
				.step(at(160).shape(recall()).hit(knock(2.0D), damage(3.0D)).phase(1))
				.fx(Phase.CAST,
						model("seed_bag").above(4.0F).scale(4.0F).grow(0.3F, 10.0F).ticks(160).facing(FxLayer.Facing.FACE_FORWARD),
						vortex(0xC8A060).inward().radius(20.0F).count(60).ticks(60))
				.fx(Phase.EXTRA0,
						model("cymbal").scale(3.0F).fall(6.0F, 10.0F).ticks(100).facing(FxLayer.Facing.FLAT),
						shell(GOLD).count(30).detail(),
						shake(0.2F, 10))
				.fx(Phase.EXTRA1, shell(0xC8A060).radius(8.0F).count(60), shake(0.2F, 10))
				.sound(Phase.CAST, SoundEvents.BUNDLE_INSERT, 1.4F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.BELL_RESONATE, 1.2F, 1.0F)
				.sound(Phase.EXTRA1, SoundEvents.BUNDLE_INSERT, 1.4F, 0.5F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 909 · Larangan Langit Berlapis (inspired by Renegade Immortal)
		sink.add(art("heavenly_restriction", 909, Dao.SPACE)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 7)
				.pose(Pose.SEAL).pose(Phase.EXTRA0, Pose.TWIN_PALM).climax(Phase.EXTRA0)
				.route(Route.comp(85), Route.feat(Feat.castsWith(Dao.SPACE, Cond.IN_END, 100)))
				.flag(Flag.NO_TEACH)
				.qi(450).cooldown(150.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(10, 24).square().walls().height(12).duration(160).pulse(20)
								.ramp(0.15D, 8))
						.hit(damage(0.6D), attrPct(AttrKind.MOVEMENT_SPEED, -1.0D, 40), eff(Eff.WEAKNESS, 3, 40)))
				.step(at(160).shape(all().ofArea().max(32)).hit(damage(6.0D)).phase(0))
				.fx(Phase.CAST,
						runeCircle(RUNE).variant(FxLayer.Variant.VERTICAL).radius(10.0F).count(80).ticks(40),
						swordRing(Items.BANNER.purple(), 4, 14.0F).orbit(14.0F, 0.0F).scale(3.0F).ticks(160),
						shake(0.1F, 8))
				.fx(Phase.PULSE, shell(RUNE).radius(10.0F).count(40).ticks(20))
				.fx(Phase.EXTRA0, shell(RUNE).inward().radius(10.0F).count(120), flash(0xB070FF, 0.25F, 6), shake(0.7F, 20))
				.fx(Phase.CAST, model("restriction_seal").n(4).radius(14.0F).scale(7.0F).ticks(160).facing(FxLayer.Facing.FACE_FORWARD).extra())
				.sound(Phase.CAST, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4F, 0.8F)
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.0F)
				.sound(Phase.PULSE, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 1.2F)
				.sound(Phase.EXTRA0, SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.6F)
				.kehancuran(DShape.pit(6, 4).at(160))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 910 · Cermin Hampa (Shrouded the Heavens)
		sink.add(art("void_mirror", 910, Dao.SPACE)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.HOLD_ITEM_UP).pose(Phase.EXTRA0, Pose.TWIN_PALM)
				.route(Route.feat(Feat.firstKill("minecraft:ender_dragon", Dao.SPACE)), Route.inh("space"))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.stages(2, 200)
				.qiPct(50, 600).cooldown(300.0D)
				.step(at(0).stage(1).self(
						dr(0.5D, Facing.FRONT, 200),
						projectileGuard(120, 4, 32, 200).returnShot(1.5D),
						counter(200, 2.0D, 6, Store.FRONT_MELEE)))
				.step(at(0).stageOrEnd(2).shape(beam().length(64).halfWidth(3).pierce().ignoreBlocks())
						.hit(damage(10.0D).perStored(1.0D)).phase(0))
				.fx(Phase.CAST,
						model("void_mirror").onCaster().above(1.5F).scale(4.0F).ticks(200).until(Phase.EXTRA0).facing(FxLayer.Facing.FACE_FORWARD),
						vortex(0x2A1050).particle(ParticleTypes.PORTAL).onCaster().size(1.5F).count(40).ticks(200),
						shake(0.3F, 10))
				.fx(Phase.EXTRA0,
						laser(blackGlass, 3.0F, 128.0F).ticks(30),
						beamCore(VOID).particle(ParticleTypes.REVERSE_PORTAL).count(80).ticks(16),
						flash(0x2A1050, 0.3F, 6),
						shake(0.7F, 24))
				// every blow and shot the mirror takes ripples its glass
				.fx(Phase.EXTRA6, shockRing(0x2A1050).onCaster().above(1.5F).radius(2.5F).vertical().count(16).ticks(8))
				.fx(Phase.EXTRA7, shockRing(0x2A1050).onCaster().above(1.5F).radius(2.5F).vertical().count(16).ticks(8), impact(VOID).particle(ParticleTypes.REVERSE_PORTAL).count(8).ticks(6))
				.sound(Phase.CAST, SoundEvents.PORTAL_AMBIENT, 1.2F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.8F)
				.sound(Phase.EXTRA0, SoundEvents.PORTAL_AMBIENT, 1.0F, 0.6F)
				.kehancuran(DShape.trench(48, 3, 3).ahead(6).atRelease())
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 911 · Wilayah Roh Waktu (A Record of a Mortal's Journey), special time_bank
		sink.add(art("time_spirit_domain", 911, Dao.SPACE)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.ARMS_SPREAD).bindLook(dev.kultivator.technique.spec.run.BindKind.TIME_STOP)
				.gate(Gate.DEWA_I).prereq("void_mirror", 3)
				.special(TimeBank.ID)
				.flag(Flag.NO_TEACH)
				.qiPct(75).cooldown(600.0D)
				.step(at(0).shape(domain().radius(TimeBank.RADIUS, TimeBank.RADIUS_CAP).height(TimeBank.HEIGHT)
								.duration(TimeBank.DURATION).pulse(TimeBank.PULSE))
						.hit(bind(BindKind.STUN, TimeBank.DURATION).boss(40))
						.when(Cond.TARGET_BOSS, 1.0D, eff(Eff.SLOWNESS, 4, TimeBank.DURATION))
						.only(Cond.not(Cond.hitBefore(1))))
				.step(at(0).self(cdRate(null, 2.0D, TimeBank.DURATION)))
				.step(at(TimeBank.DURATION).shape(all().ofArea().max(64)).hit(damage(10.0D)).phase(0))
				.fx(Phase.CAST,
						glyph("时").scale(24.0F).above(12.0F).ticks(TimeBank.DURATION),
						vignette(0x7088A8, TimeBank.DURATION),
						dome(TIME).count(60).ticks(40),
						mist(0xC8C8C0).count(40).ticks(TimeBank.DURATION).extra())
				.fx(Phase.PULSE, dome(TIME).count(20).ticks(20).detail())
				.fx(Phase.EXTRA0,
						flash(0xE0E8FF, 0.35F, 8),
						shell(0xFFFFFF).particle(ParticleTypes.SONIC_BOOM).radius(8.0F).count(12),
						shell(TIME).radius(24.0F).count(80).detail(),
						shake(1.0F, 30))
				.fx(Phase.CAST, model("time_clock").onCaster().above(6.0F).scale(6.0F).ticks(TimeBank.DURATION).facing(FxLayer.Facing.FACE_VIEWER))
				.fx(Phase.EXTRA1, impact(0xF0D080).particle(ParticleTypes.END_ROD).count(6).ticks(8), shockRing(TIME).radius(1.0F).count(10).ticks(6).detail())
				.sound(Phase.CAST, SoundEvents.NOTE_BLOCK_HAT, 1.0F, 1.6F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.NOTE_BLOCK_HAT, 1.0F, 1.2F).delay(10))
				.sound(Phase.PULSE, SoundEvents.NOTE_BLOCK_HAT, 0.8F, 1.6F)
				.sound(Phase.PULSE, SoundCue.of(SoundEvents.NOTE_BLOCK_HAT, 0.8F, 1.2F).delay(10))
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.0F)
				.kehancuran(DShape.scatter(2).times(12).at(TimeBank.DURATION))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 912 · Lubang Hitam (Stellar Transformations)
		sink.add(art("stellar_black_hole", 912, Dao.SPACE)
				.grade(TechniqueGrade.DEWA, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.CHARGE_OVERHEAD).climax(Phase.EXTRA0)
				.gate(Gate.DEWA_II).prereq("void_mirror", 3).prereq("gravitational_space", 4).prereq("time_spirit_domain", 4)
				.flag(Flag.TEACHABLE)
				.dewaStars(64).teachStars(64)
				.qiPct(90).cooldown(900.0D)
				.step(at(0).shape(field(Anchor.AIM).range(48).radius(32, 40).height(16).duration(120).pulse(10))
						.hit(pull(0.35D).perTick(10).toward(Center.AREA), damage(0.5D)))
				.step(at(0).repeat(12, 10).shape(nova().radius(3).at(Anchor.AREA)).hit(damage(2.0D)))
				.step(at(120).shape(nova().radius(16).at(Anchor.AREA)).hit(damage(18.0D)).phase(0))
				.step(at(120).shape(all().ofArea().max(64)).hit(damage(4.0D)))
				.fx(Phase.CAST,
						model("black_hole_core").scale(4.0F).spin(3.0F).ticks(120),
						model("accretion_segment").n(16).radius(10.0F).scale(2.0F).spin(6.0F).ticks(120).facing(FxLayer.Facing.FLAT),
						petalRing(Blocks.STAINED_GLASS.orange().defaultBlockState(), 16, 10.0F).spin(6.0F).ticks(120).extra(),
						vortex(ACCRETION).inward().radius(32.0F).count(80).ticks(40),
						sky(0x100010).ticks(120),
						shake(0.5F, 20))
				.fx(Phase.PULSE,
						vortex(ACCRETION).inward().radius(24.0F).count(40).ticks(10),
						vortex(0x3A1060).inward().particle(ParticleTypes.REVERSE_PORTAL).radius(16.0F).count(30).ticks(10).detail())
				.fx(Phase.EXTRA0,
						shell(0xFFFFFF).radius(16.0F).count(160),
						flash(0xFFE8C0, 0.35F, 8),
						impact(ACCRETION).particle(ParticleTypes.EXPLOSION_EMITTER).count(3).detail(),
						shake(1.0F, 30))
				.sound(Phase.CAST, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.7F)
				.sound(Phase.EXTRA0, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.END_PORTAL_SPAWN, 1.6F, 0.5F)
				.kehancuran(DShape.sphere(10).at(120))
				.perfection(Bonus.payload(eff(Eff.SLOWNESS, 3, 40)))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	private SpaceArts() {
	}
}

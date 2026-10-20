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
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload.AttrKind;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BlinkMode;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.kultivator.data.TechniqueSpec.Flag.*;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Pedang / Sword catalogue (DESIGN 22.9.1, net ids 200-213): 14 arts from Misterius to Dewa, each written exactly as its
 * catalogue row (realm gate, routes, recipe, qi, cooldown, visual layers, Kehancuran shape at K = 1, disciple column) with
 * the Sumber line in the {@code arts_sword} lang fragment. Builder DSL only (22.2.5); every visual is client-drawn from the
 * registry, every carving goes through {@code Kehancuran.submitSelf} only while the player's own switch is on.
 * The level-3 bonuses are game design; level 4 is the 22.6 default (ECHO 0.4 where allowed, otherwise DURATION +25%).
 */
public final class SwordArts {

	/** The default level-4 bonuses of 22.6. */
	public static final double PEAK_ECHO = 0.4D;
	public static final double PEAK_DURATION = 0.25D;

	/** No wolf howl exists in 26.3: the classic big wolf's growl stands in (a verified cue, 22.4.6). */
	private static final Holder<SoundEvent> WOLF_GROWL = SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound();

	public static void register(TechniqueRegistry.Sink sink) {
		// 200 · Seni Pedang Esensi Biru · GC · Misterius-R · SERANG · DAO, COMP 10 · Mrd Y
		sink.add(done(art("azure_essence_sword", 200, Dao.SWORD)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.SWORD_THRUST).pose(Phase.TRAVEL, Pose.SWORD_SLASH).castTicks(400)
				.route(Route.dao()).comprehension(10)
				.toggle(1.0D, 15).cooldown(6.0D)
				// GRANT_HOOK sword melee (icd 10t): FRONT(L 6→12, w1, v2) > DMG .6 (lasts while the stance is held).
				.step(at(0).self(hook(Hook.onMelee(at(0).shape(front().length(6.0D, 12.0D).width(1.0D).speed(2.0D)).hit(damage(0.6D)))
						.swordOnly().icd(10))))
				// t≥4: 3 fronts fanned 20° (the centre front above plus these two at ±20°).
				.step(at(0).minTier(4).self(hook(Hook.onMelee(at(0).shape(front().length(6.0D, 12.0D).width(1.0D).speed(2.0D).count(2).fan(40.0D))
						.hit(damage(0.6D))).swordOnly().icd(10))))
				// An azure glow along the held blade: the caster's own held sword (FxLayer.HELD) wrapped in the azure trail.
				.fx(Phase.CAST, trail(0x7FD8FF).onCaster().count(16).ticks(400), trail(0xFFFFFF).particle(ParticleTypes.END_ROD).onCaster().count(4).ticks(400).extra(),
						impact(0x7FD8FF).particle(ParticleTypes.END_ROD).onCaster().above(1.2F).count(10).ticks(8))
				.fx(Phase.TRAVEL, crescentFly(0x7FD8FF).size(0.6F).count(14).ticks(8))
				.fx(Phase.TRAVEL, arc(0xBFF0FF).size(1.2F).count(16).ticks(6), impact(0xFFFFFF).particle(ParticleTypes.CRIT).count(6).ticks(6).detail())
				.sound(Phase.TRAVEL, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.8F)
				.perfection(Bonus.extraCount(1))));

		// 201 · Bayangan Pedang Membelah Cahaya · GC · Misterius-R · AREA · BOOK sweeping_edge · Mrd Y
		sink.add(done(art("sword_shadow_split", 201, Dao.SWORD)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.AREA).realm(Realm.GOLDEN_CORE)
				.pose(Pose.SWORD_THRUST)
				.route(Route.book("minecraft:sweeping_edge"))
				.qi(30).cooldown(6.0D)
				.step(at(0).shape(volley().seek().count(3.0D, 10.0D).spread(Shape.Volley.Spread.FAN, 50.0D).range(20.0D, 48.0D)
						.pierce(Shape.Volley.PIERCE_ALL).perTarget(3)).hit(damage(0.7D)))
				.fx(Phase.CAST, swordShape(0xCFE8FF).fanned().n(5).count(24))
				// The flying GIANT_ITEMs are copies of the caster's held sword (FxLayer.HELD; an iron sword when the hand is empty).
				.fx(Phase.TRAVEL, heldItem().scale(2.0F).flying().slide(10.0F).ticks(14), model("light_sword").scale(1.4F).slide(10.0F).ticks(14).facing(FxLayer.Facing.BLADE).extra(), trail(0xCFE8FF).count(10))
				.sound(Phase.CAST, SoundEvents.TRIDENT_THROW, 1.0F, 1.6F)
				.sound(Phase.CAST, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F)
				// a small cut pit where each sword shadow strikes
				.kehancuran(DShape.scatter(1))
				.perfection(Bonus.extraCount(3))));

		// 202 · Cahaya Pedang Raksasa · NS · Misterius-M · SERANG · BOOK sharpness, COMP 30 · Mrd Y
		sink.add(done(art("giant_sword_light", 202, Dao.SWORD)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.SERANG).realm(Realm.NASCENT_SOUL)
				.pose(Phase.CHARGE, Pose.SWORD_SKY).pose(Pose.SWORD_SLASH)
				.route(Route.book("minecraft:sharpness")).comprehension(30)
				.qi(70).cooldown(18.0D).charge(15)
				.step(at(0).shape(front().length(10.0D, 36.0D).width(3.0D).height(4.0D).speed(3.0D))
						.hit(damage(3.0D), bind(BindKind.ROOT, 20), launch(0.4D)))
				// The held sword (6→20 b) rises point-up during the charge, then cleaves (FxLayer.HELD).
				.fx(Phase.CHARGE, model("giant_sword").scale(20.0F).grow(0.3F, 15.0F).above(3.0F).ticks(40).until(Phase.TRAVEL).facing(FxLayer.Facing.UPRIGHT), swordShape(0xE0F0FF).count(10), motesTo(0xE0F0FF).particle(ParticleTypes.END_ROD).onCaster().count(16).ticks(15).detail())
				.fx(Phase.CAST, model("giant_sword").scale(20.0F).above(3.0F).swing(-120.0F, 6.0F).ticks(12).facing(FxLayer.Facing.FACE_FORWARD), flash(0xE0F0FF, 0.15F, 3))
				.fx(Phase.TRAVEL, crescentFly(0xE0F0FF).size(3.0F).count(30), cracks(), shake(0.4F, 12))
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 1.0F, 0.5F)
				.kehancuran(DShape.fissure(16, 2, 4))
				.perfection(Bonus.area(0.25D))));

		// 203 · Tebasan Serigala Langit · NS · Misterius-M · GERAK · BOOK sharpness, COMP 35; PAV · Mrd Y
		sink.add(done(art("heavenly_wolf_slash", 203, Dao.SWORD)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.GERAK).realm(Realm.NASCENT_SOUL)
				.pose(Pose.DASH_LEAN).pose(Phase.END, Pose.SWORD_SLASH).pose(Phase.IMPACT, Pose.SWORD_SLASH)
				.route(Route.book("minecraft:sharpness"), Route.pav()).comprehension(35).flag(PAVILION)
				.qi(60).cooldown(14.0D)
				// DASH 8→20 (i-frames 6t) + PATH(w2) > DMG 2.2; onEnd CONE(10→18, 30°) > DMG 1.2 + weakness I 80t ("Wild Fang").
				.step(at(0).self(dash(8.0D, 20.0D).iframes(6)).shape(path().halfWidth(1.0D)).hit(damage(2.2D))
						.onEnd(at(0).shape(cone().length(10.0D, 18.0D).halfAngle(30.0D)).hit(damage(1.2D), eff(Eff.WEAKNESS, 1, 80))))
				.fx(Phase.CAST, beast(FxLayer.BeastKind.WOLF, 0xD8E0EA).size(1.5F).count(40))
				.fx(Phase.TRAVEL, afterimage(0xD8E0EA), trail(0xD8E0EA).count(12))
				.fx(Phase.END, arc(0xD8E0EA).size(1.5F).above(1.2F).count(24), arc(0xFFFFFF).mirrored().above(1.2F).count(12).extra(), shake(0.2F, 8))
				.fx(Phase.TRAVEL, model("sky_wolf").scale(3.0F).slide(6.0F).ticks(10))
				// the Wild Fang cone at the end: three fang crescents at chest height
				.fx(Phase.IMPACT, crescentFly(0xD8E0EA).n(3).above(1.2F).count(30).ticks(8), impact(0xFFFFFF).particle(ParticleTypes.SWEEP_ATTACK).count(6).ticks(6).detail())
				.sound(Phase.CAST, WOLF_GROWL, 1.0F, 0.7F)
				.kehancuran(DShape.trenchPath(2, 1))
				.perfection(Bonus.area(0.25D))));

		// 204 · Pedang Pembunuh Abadi Bulan Darah · DT · Bumi-R · AREA · FEAT kills_with(SWORD, full-moon night, 30), COMP 50; PAV · Mrd Y
		sink.add(done(art("bloodmoon_immortal_slaying_sword", 204, Dao.SWORD)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.SWORD_SKY).pose(Phase.EXTRA0, Pose.SWORD_THRUST)
				.route(Route.feat(Feat.killsWith(Dao.SWORD, Cond.FULL_MOON_NIGHT, null, 30)), Route.pav()).comprehension(50)
				.flag(TEACHABLE, PAVILION)
				.qi(140).cooldown(40.0D)
				.step(at(0))
				.step(at(20).shape(rain(Anchor.AIM, 32).radius(8, 16).impacts(12, 36).over(40).impactRadius(2)).hit(damage(0.9D)))
				.step(at(70).shape(zone(Anchor.AIM).radius(4)).hit(damage(3.0D), mark(Mark.daoVuln(Dao.SWORD, 0.2D), 200)).phase(0))
				.fx(Phase.CAST, cloudDisc(0xB0102A).onTarget().above(20.0F).count(60).ticks(110), sky(0x8A0010).ticks(110).extra(), vignette(0x6A0010, 30))
				// each sword of the rain falls from 14 up on its own pre-impact TRAVEL and lands with the IMPACT
				.fx(Phase.TRAVEL, model("bloodmoon_sword").scale(4.0F).fall(14.0F, 6.0F).ticks(10).facing(FxLayer.Facing.BLADE), swordShape(0xB0102A).falling().count(4).detail())
				.fx(Phase.IMPACT, impact(0xC0102A).count(12).ticks(8), shockRing(0xB0102A).radius(2.0F).count(14).ticks(8).detail())
				.fx(Phase.EXTRA0, model("bloodmoon_sword").scale(8.0F).fall(20.0F, 6.0F).ticks(30).facing(FxLayer.Facing.BLADE),
						shockRing(0xB0102A).radius(6.0F).count(40).ticks(14), flash(0xC0102A, 0.2F, 4), shake(0.5F, 12))
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.4F, 0.5F)
				.kehancuran(DShape.scatter(2), DShape.bowl(4))
				.perfection(Bonus.extraCount(6))));

		// 205 · Amarah Sang Penguasa · DT · Bumi-M · AREA · BOOK sharpness, COMP 45; PAV · Mrd Y
		sink.add(done(art("overlord_fury", 205, Dao.SWORD)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.DASH_LEAN).pose(Phase.EXTRA0, Pose.KNEEL_STRIKE)
				.route(Route.book("minecraft:sharpness"), Route.pav()).comprehension(45).flag(PAVILION)
				.qi(110).cooldown(30.0D)
				// BLINK leap AIM ≤16→24 (safe), then the slam: WAVE(r 6→20, v1.5) > DMG 3.5 centre → 1.5 edge + LCH .6 + armor −50% 160t.
				.step(at(0).self(blink(BlinkMode.AIM, 16.0D, 24.0D).leap()))
				// the slam lands with the leap (a 9-tick parabola, S16)
				.step(at(10).shape(wave().r1(6.0D, 20.0D).speed(1.5D))
						.hit(damage(3.5D).edge(1.5D), launch(0.6D), attrPct(AttrKind.ARMOR, -0.5D, 160)).phase(0))
				.fx(Phase.CAST, trail(0xE34234).count(12), mist(0x8A2020).onCaster().count(12).ticks(10))
				.fx(Phase.TRAVEL, trail(0xE34234).count(20).ticks(10), afterimage(0xE34234).ticks(10))
				.fx(Phase.EXTRA0, model("giant_sword").scale(10.0F).fall(12.0F, 4.0F).ticks(30).facing(FxLayer.Facing.BLADE),
						shockRing(0xE34234).count(40), shockRing(0xFFB070).delay(6).count(30).detail(), cracksAlways(0x3A1A10).count(30).ticks(40),
						impact(0x8A7A60).particle(ParticleTypes.POOF).count(24).ticks(14).detail(), cracks(), shake(0.5F, 14))
				.sound(Phase.EXTRA0, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.6F)
				.kehancuran(DShape.bowl(6), DShape.ring(12, 6, 2))
				.perfection(Bonus.extraWave(1))));

		// 206 · Seni Pedang Kesengsaraan Abadi · DU · Bumi-T · SERANG · BOOK sharpness, COMP 60; PAV · Mrd Y
		sink.add(done(art("immortal_tribulation_sword", 206, Dao.SWORD)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.SERANG).realm(Realm.DAO_UNION)
				.pose(Pose.SWORD_SKY).pose(Phase.TRAVEL, Pose.SWORD_THRUST)
				.route(Route.book("minecraft:sharpness"), Route.pav()).comprehension(60).flag(PAVILION)
				.qi(180).cooldown(45.0D)
				.step(at(0).shape(beam().length(48.0D, 64.0D).halfWidth(1.5D).pierce()).hit(damage(4.0D), dot(0.25D, 8, DotKind.QI)))
				.fx(Phase.CAST, impact(0xFFF1C0).particle(ParticleTypes.END_ROD).onCaster().above(2.2F).count(12).ticks(8), cloudDisc(0x202030).above(10.0F).count(30).ticks(20).detail())
				.fx(Phase.TRAVEL, beamCore(0xFFF1C0).size(1.5F).count(60), laser(Blocks.STAINED_GLASS.white().defaultBlockState(), 1.5F, 48.0F).minTier(1),
						bolt(0xFFF8E0).count(10).ticks(6).detail(), shake(0.4F, 12))
				.sound(Phase.TRAVEL, SoundEvents.TRIDENT_THUNDER, 1.2F, 1.0F)
				.kehancuran(DShape.tunnel(1, 32))
				.perfection(Bonus.area(0.25D))));

		// 207 · Seni Pedang Aksara Rumput · DU · Bumi-T · SERANG · FEAT meditate_at(bamboo jungle, 10 min), COMP 70 · Mrd Y
		sink.add(done(art("grass_symbol_sword", 207, Dao.SWORD)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.SERANG).realm(Realm.DAO_UNION)
				.pose(Pose.SWORD_THRUST)
				.route(Route.feat(Feat.meditateAt(Cond.biome("minecraft:bamboo_jungle"), 10))).comprehension(70)
				.qi(150).cooldown(35.0D)
				.step(at(0).shape(all().radius(32.0D).max(5.0D, 8.0D).order(Shape.All.Order.STRONGEST).los().precise())
						.hit(damage(2.5D), eff(Eff.SLOWNESS, 3, 60)))
				.fx(Phase.CAST, FxLayer.of(FxLayer.Template.GLYPH, 0x9CE07A).text("草").scale(8.0F).minTier(1), runeCircle(0x9CE07A).count(30))
				.fx(Phase.HIT, beamCore(0xC8FFB0).size(0.3F).count(12).ticks(4))
				.fx(Phase.TRAVEL, model("grass_blade_sword").scale(1.5F).slide(8.0F).ticks(10).facing(FxLayer.Facing.BLADE), trail(0xC8FFB0).count(10).ticks(8))
				.sound(Phase.CAST, SoundEvents.NOTE_BLOCK_FLUTE, 1.0F, 2.0F)
				.sound(Phase.HIT, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.8F)
				.perfection(Bonus.extraCount(2))));

		// 208 · Sikap Pemecah Langit · IA T1 · Langit-R · PAMUNGKAS · FEAT first_kill(WARDEN, SWORD), COMP 70 · Mrd Y
		sink.add(done(art("heavenbreaker_stance", 208, Dao.SWORD)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Phase.CHARGE, Pose.SWORD_SKY).pose(Pose.OVERHEAD_SLAM)
				.route(Route.feat(Feat.firstKill("minecraft:warden", Dao.SWORD))).comprehension(70)
				.qi(220).cooldown(40.0D).charge(40)
				// CHG 40t (early release ≥50%); FRONT(L 40→64, w4, h12, v4) > DMG 8 × charge share + root 40t + SLAM .6.
				.step(at(0).shape(front().length(40.0D, 64.0D).width(4.0D).height(12.0D).speed(4.0D))
						.hit(damage(8.0D).charged(), bind(BindKind.ROOT, 40), slam(0.6D)))
				// The 30-b blade is drawn at the display cap (scale 24).
				.fx(Phase.CHARGE, model("giant_sword").scale(24.0F).grow(0.1F, 40.0F).above(8.0F).ticks(44).until(Phase.TRAVEL).facing(FxLayer.Facing.UPRIGHT), swordShape(0xE8F0FF).count(20))
				.fx(Phase.CAST, skyTear(0xE8F0FF).length(64.0F), crescentFly(0xE8F0FF).vertical().size(4.0F).count(60), model("giant_sword").scale(24.0F).above(8.0F).swing(-120.0F, 6.0F).ticks(14).facing(FxLayer.Facing.FACE_FORWARD))
				.fx(Phase.TRAVEL, cracks(), shake(0.8F, 20))
				.sound(Phase.CAST, SoundEvents.ANVIL_LAND, 1.4F, 0.4F)
				.sound(Phase.CAST, SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 0.5F)
				.kehancuran(DShape.fissure(32, 3, 6))
				.perfection(Bonus.area(0.25D))));

		// 209 · Labu Pemenggal Lu Ya · IA T3 · Langit-R · KENDALI · PAV; FEAT kills_with(SWORD, nether, 100) · Mrd Y
		sink.add(done(art("lu_ya_gourd", 209, Dao.SWORD)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 3)
				.pose(Pose.HOLD_ITEM_UP).pose(Phase.EXTRA0, Pose.FINGER_POINT)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.SWORD, Cond.IN_NETHER, null, 100))).flag(PAVILION, REQUIRES_TARGET)
				.qi(180).cooldown(45.0D)
				.step(at(0).shape(target().range(48.0D)).hit(bind(BindKind.STUN, 60).boss(15)))
				.step(at(60).shape(recall().max(1)).hit(damage(8.0D), exec(0.35D, 0.05D)).phase(0))
				.fx(Phase.CAST, model("lu_ya_gourd").fromHand().above(0.4F).scale(1.2F).ticks(70).until(Phase.EXTRA0), beamCore(0xFFFFFF).size(0.2F).count(20).ticks(60))
				// "A tiny glowing figure arcs over" the pinned target: a small white 仙 rising over it, with the white trail arc.
				.fx(Phase.EXTRA0, trail(0xFFF8E0).count(16).ticks(10), impact(0xFFFFFF).count(20),
						FxLayer.of(FxLayer.Template.GLYPH, 0xFFF8E0).text("仙").scale(1.2F).onTarget().above(1.0F).rise(2.0F, 10.0F).ticks(14))
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.0F, 1.4F)
				.sound(Phase.EXTRA0, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 1.0F)
				.perfection(Bonus.durationTicks(20))));

		// 210 · Sikap Tetes Darah · IA T5 · Langit-M · SERANG · BOOK sharpness, COMP 80; PAV · Mrd Y
		sink.add(done(art("blood_drop_stance", 210, Dao.SWORD)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.SERANG).realm(Realm.IMMORTAL_ASCENSION, 5)
				.pose(Pose.SWORD_SLASH).pose(Phase.IMPACT, Pose.SWORD_SLASH)
				.route(Route.book("minecraft:sharpness"), Route.pav()).comprehension(80).flag(PAVILION, REQUIRES_TARGET)
				.qi(240).cooldown(30.0D)
				// BLINK BEHIND_TARGET chain 6→12 within 24 (≥3t apart); each STRIKE > DMG 3 ? TARGET_LOW_HP(.5) ×2 + EXEC .15.
				// i-frames during the chain: each hop renews 6 ticks (the hops are 3 ticks apart).
				.step(at(0).self(blink(BlinkMode.BEHIND_TARGET, 24.0D).chain(6.0D, 12.0D, 24.0D).gap(3).iframes(6)
						.eachHop(at(0).shape(strike().range(4.0D).at(Anchor.HIT)).hit(damage(3.0D)).when(Cond.targetLowHp(0.5D), 2.0D, exec(0.15D)))))
				.fx(Phase.CAST, vignette(0xA00010, 20))
				.fx(Phase.TRAVEL, trail(0xC0102A).count(12), afterimage(0xC0102A))
				.fx(Phase.HIT, impact(0xC0102A).count(10))
				.fx(Phase.IMPACT, crescentFly(0xC0102A).size(1.4F).count(20).ticks(6), impact(0x8A0010).particle(ParticleTypes.DAMAGE_INDICATOR).count(8).ticks(8).detail())
				.fx(Phase.END, glyph("血").color2(0x600008).scale(1.4F).above(1.6F).ticks(20).extra())
				.sound(Phase.CAST, SoundEvents.TRIDENT_RIPTIDE_1, 1.0F, 1.6F)
				.sound(Phase.HIT, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F)
				// the strikes land at each hop's target (anchor HIT): a small cut pit per struck foe
				.kehancuran(DShape.scatter(1))
				.perfection(Bonus.chain(2))));

		// 211 · Niat Pedang Inti: Sikap Bintang · IA T7 · Langit-T · WILAYAH · FEAT meditate_at(open sky at night, 30 min), COMP 90 · Mrd N
		sink.add(done(art("astral_sword_intent", 211, Dao.SWORD)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.WILAYAH).realm(Realm.IMMORTAL_ASCENSION, 7)
				.pose(Pose.ARMS_SPREAD).climax(Phase.CAST)
				.route(Route.feat(Feat.meditateAt(Cond.all(Cond.SKY_OPEN, Cond.NIGHT), 30))).comprehension(90).flag(NO_TEACH)
				.qi(320).cooldown(90.0D)
				// DOMAIN(r 16→32, 300t, pulse 20) > DMG 1.2; GRANT_HOOK melee and technique hits: 2 star swords > DMG .8 (≤6 per second).
				.step(at(0).shape(domain().radius(16.0D, 32.0D).duration(300).pulse(20)).hit(damage(1.2D))
						.self(hook(Hook.onAnyHit(at(0).repeat(2, 3).shape(target().pick(Shape.Target.Pick.HIT).noLos()).hit(damage(0.8D)))
								.perSecond(3))))
				.fx(Phase.CAST, starfield(0xB8C8FF).count(80), swordRing(Items.DIAMOND_SWORD, 8, 6.0F).onCaster())
				.fx(Phase.PULSE, swordShape(0xB8C8FF).count(8).extra())
				.fx(Phase.HIT, trail(0xE0E8FF).count(6))
				.fx(Phase.TRAVEL, model("star_sword").onTarget().scale(2.0F).fall(12.0F, 6.0F).ticks(9).facing(FxLayer.Facing.BLADE), trail(0xE0E8FF).count(8).ticks(6).detail())
				.sound(Phase.CAST, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.0F)
				.sound(Phase.CAST, SoundEvents.BEACON_AMBIENT, 1.0F, 1.0F)
				// the domain rides the caster: the first star-sword pulse scars a ring of ground around them
				.kehancuran(DShape.ring(12, 5, 1))
				.perfection(Bonus.area(0.25D))));

		// 212 · Formasi Pedang Zhuxian · TI · Abadi-M · PAMUNGKAS · FEAT first_kill set {WITHER, WARDEN, ELDER_GUARDIAN, ENDER_DRAGON} with SWORD; INH(SWORD) · Mrd Y★18
		sink.add(done(art("zhuxian_sword_formation", 212, Dao.SWORD)
				.grade(TechniqueGrade.ABADI, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SWORD_SKY).pose(Phase.EXTRA0, Pose.SWORD_SLASH).climax(Phase.EXTRA0)
				.route(Route.feat(Feat.firstKillAll(Dao.SWORD, "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian",
						"minecraft:ender_dragon")), Route.inh("sword"))
				.teachStars(18)
				.qiPct(50.0D, 600).cooldown(300.0D)
				// FIELD square (aim ≤64, side 48→64, walls, 240t, pulse 20) > DMG 2; the corner laws each pulse: Luxian STRIP_BUFFS,
				// Xianxian slowness IV; Juexian onEnd EXEC .15.
				.step(at(0).shape(field(Anchor.AIM).range(64.0D).radius(24.0D, 32.0D).square().walls().duration(240).pulse(20))
						.hit(damage(2.0D), stripBuffs(), eff(Eff.SLOWNESS, 4, 30))
						.onEnd(at(0).shape(all().ofArea().max(32.0D)).hit(exec(0.15D))))
				// Corner PILLARs (r6) every 40t > DMG 4.
				.step(at(40).repeat(5, 40).shape(pillar(Anchor.CORNERS).radius(6.0D)).hit(damage(4.0D)))
				// @240 ALL(field, max 32) > DMG 10.
				.step(at(240).shape(all().ofArea().max(32.0D)).hit(damage(10.0D)).phase(0))
				// The four swords and the four glyphs 誅 戮 陷 絕 stand at the square field's corners (n = 4 on a ring turned 45°).
				// DESIGN 30.4.2: the swords, plinths, map, seams and the sky sword are UltimatesA props (no model layer here)
				.fx(Phase.CAST, FxLayer.of(FxLayer.Template.GLYPH, 0xB0102A).text("誅戮陷絕").n(4).radius(32.0F).scale(6.0F), mist(0x400010).count(60))
				.fx(Phase.PULSE, mist(0x1A0505).count(20).extra())
				.fx(Phase.IMPACT, column(0xB0102A).count(30), shockRing(0xB0102A).radius(6.0F).count(24).ticks(8).detail())
				.fx(Phase.EXTRA0, swordShape(0xB0102A).falling().count(80), flash(0xB0102A, 0.25F, 6), shake(1.0F, 30))
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.5F, 0.5F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 1.5F, 0.5F).delay(6))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 1.5F, 0.5F).delay(12))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 1.5F, 0.5F).delay(18))
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 1.0F)
				.kehancuran(DShape.column(3, 12).times(4), DShape.trench(48, 2, 3).edges())
				// Phase 10 (DESIGN 28.12): four red corner pillars, the square lights corner by corner, the giant sword falls into the centre
				.dampak(Dampak.builder().mark(Dampak.Mark.MULTI).ride(Dampak.Ride.SHORT).climaxAt(240)
						.frames(Dampak.INV, 0xB0102A, 0x1A0505).silence(3).debris(Dampak.Debris.BURST).ring(Dampak.Ring.DUST).palette(Dampak.Palette.SWORD)
						.sigil(SigilPattern.ZHUXIAN_SQUARE, 34.0F).ignite(Phase.IMPACT)
						.monument("zhuxian_sword", 16.0F, 120, Dampak.MonumentPose.PLANTED).build())
				.perfection(Bonus.duration(0.2D))));

		// 213 · Seni Pedang Hukuman Langit · Dewa · Dewa-R · PAMUNGKAS · GATE I (prereq zhuxian_sword_formation L3) · Mrd Y★36
		sink.add(done(art("heaven_punishing_sword", 213, Dao.SWORD)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SWORD_SKY).pose(Phase.TRAVEL, Pose.SWORD_THRUST).climax(Phase.TRAVEL)
				.gate(TechniqueSpec.Gate.DEWA_I).prereq("zhuxian_sword_formation", 3).dewaStars(36).teachStars(36)
				.qiPct(75.0D).cooldown(600.0D)
				.step(at(0).shape(all().radius(40.0D).max(9.0D).order(Shape.All.Order.STRONGEST)).hit(damage(3.0D)))
				.step(at(20).shape(nova().radius(40.0D)).hit(bind(BindKind.STUN, 60), eff(Eff.DARKNESS, 1, 60)))
				.step(at(40).shape(nova().radius(40.0D)).hit(damage(5.0D)))
				.step(at(60).shape(beam().length(128.0D).halfWidth(3.0D).pierce().ignoreBlocks().travel()).hit(damage(12.0D)))
				.fx(Phase.CAST, sky(0xFFF1C0), skyTear(0xFFF1C0).length(128.0F), swordShape(0xFFF1C0).falling().count(60))
				.fx(Phase.TRAVEL, beamCore(0xFFF1C0).size(3.0F).count(120), flash(0xFFF8E0, 0.35F, 6), shake(1.0F, 30))
				// the stun and darkness novas: a dark ring and falling chains of heaven's law
				.fx(Phase.IMPACT, shockRing(0x302040).count(60).ticks(14), mist(0x101018).count(30).ticks(20), chains(0xFFF1C0).count(16).ticks(10).detail())
				// DESIGN 30.4.2: the gate, the chains and the sword riding the beam are UltimatesA props (no model layer here)
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 0.6F)
				.sound(Phase.TRAVEL, SoundEvents.TRIDENT_THUNDER, 1.5F, 1.0F)
				.sound(Phase.TRAVEL, SoundEvents.BELL_BLOCK, 1.5F, 1.0F)
				.kehancuran(DShape.trench(64, 3, 5).alongBeam())
				// Phase 10 (DESIGN 28.12): the sword is drawn out of the opening Southern Heavenly Gate, rides the beam down and stays planted at its end
				.dampak(Dampak.builder().mark(Dampak.Mark.PILLAR).ride(Dampak.Ride.FULL, 60, Dampak.Vista.GERBANG_LANGIT).climaxAt(60)
						.frames(Dampak.INV, 0xFFF1C0, 0x101018).silence(6).debris(Dampak.Debris.WAKE).ring(Dampak.Ring.DUST).palette(Dampak.Palette.SWORD)
						.sigil(SigilPattern.SWORD_RING, 12.0F).ignite(Phase.IMPACT).trench()
						.monument("heaven_punishing_sword", 12.0F, 180, Dampak.MonumentPose.PLANTED).build())
				.perfection(Bonus.area(0.25D))));
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

	private SwordArts() {
	}
}

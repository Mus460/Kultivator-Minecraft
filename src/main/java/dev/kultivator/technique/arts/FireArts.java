package dev.kultivator.technique.arts;

import dev.kultivator.data.BreakthroughQuality;
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
import dev.kultivator.technique.spec.dsl.Flow;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Payload.BindKind;
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.Center;
import dev.kultivator.technique.spec.dsl.Payload.DotKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Payload.Pattern;
import dev.kultivator.technique.spec.dsl.Payload.SummonMode;
import dev.kultivator.technique.spec.dsl.Payload.Versus;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static dev.kultivator.data.TechniqueSpec.Flag.*;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Api / Fire catalogue (DESIGN 22.9.2, net ids 300-314): 15 arts from Misterius to Dewa, each written exactly as its
 * catalogue row with the Sumber line in the {@code arts_fire} lang fragment. Fire is entity fire only ({@code IGN} and
 * FIRE DOTs, never a fire block); Fire damage keeps the Dao default ×0.5 against fire-immune monsters unless a row says
 * otherwise (Samadhi True Fire). The five phoenix arts and the legacy Phoenix Flame keep clearly different looks (gold
 * stream, flame mantle, feathered arrows, sky-scale phoenix, vermilion skimmer). Level-3 bonuses are game design; level 4
 * is the 22.6 default.
 */
public final class FireArts {

	public static final double PEAK_ECHO = 0.4D;
	public static final double PEAK_DURATION = 0.25D;

	public static void register(TechniqueRegistry.Sink sink) {
		// 300 · Garis Api Phoenix · GC · Misterius-R · SERANG · DAO · Mrd Y
		sink.add(done(art("phoenix_fire_line", 300, Dao.FIRE)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.ROAR)
				.route(Route.dao())
				.qi(25).cooldown(5.0D)
				.step(at(0).shape(beam().length(14.0D, 35.0D).halfWidth(0.8D)).hit(damage(1.2D), dot(0.25D, 6, DotKind.FIRE)))
				// A thin golden flame stream from the mouth.
				.fx(Phase.CAST, impact(0xFFC830).onCaster().above(1.6F).count(8).ticks(6), motesTo(0xFF9020).particle(ParticleTypes.FLAME).onCaster().count(8).ticks(4).detail())
				.fx(Phase.TRAVEL, beamCore(0xFFC830).size(0.6F).count(40), beamCore(0xFF9020).particle(ParticleTypes.FLAME).count(20).detail(),
						impact(0xFF9020).particle(ParticleTypes.FLAME).onTarget().count(10).ticks(8).extra())
				.sound(Phase.TRAVEL, SoundEvents.BLAZE_SHOOT, 1.0F, 1.0F)
				.kehancuran(DShape.tunnel(1, 12))
				.perfection(Bonus.payload(ignite(4.0D)))));

		// 301 · Phoenix Mandi Api · GC · Misterius-M · LINDUNG · DAO, COMP 20 · Mrd Y
		sink.add(done(art("phoenix_bathing_fire", 301, Dao.FIRE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.LINDUNG).realm(Realm.GOLDEN_CORE)
				.pose(Pose.SUMMON_RAISE)
				.route(Route.dao()).comprehension(20)
				.qi(40).cooldown(40.0D)
				// AURA(r 3→7, 400t, pulse 20) > DMG .3 + IGN 3s; SELF fire resistance + EMP(FIRE +30%) 400t; GRANT_HOOK melee > IGN 4s.
				.step(at(0).shape(aura().radius(3.0D, 7.0D).duration(400).pulse(20)).hit(damage(0.3D), ignite(3.0D))
						.self(buff(BuffKind.FIRE_RESISTANCE, 1, 400), empower(Dao.FIRE, 0.3D, 400),
								hook(Hook.onMelee(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(ignite(4.0D))).ticks(400))))
				.fx(Phase.CAST, shieldShell(0xFF7A2A).count(40).ticks(40), wings(0xFFB040).count(30).detail())
				.fx(Phase.PULSE, shieldShell(0xFF7A2A).count(10).extra())
				.fx(Phase.CAST, mist(0xFF7A2A).particle(ParticleTypes.FLAME).onCaster().count(20).ticks(400).detail())
				.sound(Phase.CAST, SoundEvents.FIRECHARGE_USE, 1.0F, 1.0F)
				.perfection(Bonus.durationTicks(100))));

		// 302 · Tsunami Api Pembelah · NS · Misterius-M · AREA · BOOK fire_aspect, COMP 30 · Mrd Y
		sink.add(done(art("flame_splitting_tsunami", 302, Dao.FIRE)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.pose(Pose.SWORD_SLASH)
				.route(Route.book("minecraft:fire_aspect")).comprehension(30)
				.qi(80).cooldown(20.0D).charge(20)
				.step(at(0).shape(front().length(20.0D, 48.0D).width(6.0D, 14.0D).height(4.0D).speed(1.5D))
						.hit(damage(2.5D), knock(1.2D), dot(0.3D, 5, DotKind.FIRE)))
				.fx(Phase.CHARGE, coneSpray(0xC0201A).count(20))
				.fx(Phase.CAST, impact(0xC0201A).count(20))
				// The curling crimson wave carries a black heavy ruler (a 1×6×1 black concrete display).
				.fx(Phase.TRAVEL, crescentFly(0xC0201A).size(3.0F).count(40), crescentFly(0xFF6020).particle(ParticleTypes.FLAME).size(2.5F).count(20).detail(),
						model("heavy_ruler").scale(6.0F).slide(12.0F).ticks(16).facing(FxLayer.Facing.UPRIGHT), shake(0.4F, 14))
				.sound(Phase.CAST, SoundEvents.BLAZE_SHOOT, 1.2F, 0.8F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BLAZE_SHOOT, 1.2F, 0.7F).delay(4))
				.sound(Phase.CAST, SoundEvents.LAVA_POP, 1.0F, 1.0F)
				.kehancuran(DShape.fan(16, 30, 2))
				.perfection(Bonus.area(0.25D))));

		// 303 · Panah Phoenix · NS · Misterius-T · SERANG · BOOK flame, COMP 30 · Mrd Y
		sink.add(done(art("phoenix_arrow", 303, Dao.FIRE)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.SERANG).realm(Realm.NASCENT_SOUL)
				.pose(Pose.PALM_THRUST)
				.route(Route.book("minecraft:flame")).comprehension(30)
				.qi(60).cooldown(10.0D)
				.step(at(0).shape(volley().seek().count(1.0D, 5.0D).range(48.0D)).hit(damage(2.0D))
						.onHit(at(0).shape(nova().radius(2.0D).at(Anchor.HIT)).hit(damage(1.0D), dot(0.3D, 4, DotKind.FIRE))))
				// Phoenix-fire arrows with a feather trail.
				.fx(Phase.CAST, impact(0xFFB040).count(10))
				.fx(Phase.TRAVEL, trail(0xFF8030).count(14), trail(0xFFD060).particle(ParticleTypes.FLAME).count(6).detail())
				.fx(Phase.HIT, impact(0xFF6020).count(16))
				.fx(Phase.TRAVEL, model("phoenix_arrow").scale(1.0F).slide(10.0F).ticks(12).facing(FxLayer.Facing.BLADE))
				.sound(Phase.CAST, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.6F)
				.kehancuran(DShape.scatter(1))
				.perfection(Bonus.extraCount(2))));

		// 304 · Guci Sepuluh Ribu Gagak · DT · Bumi-R · AREA · PAV; FEAT kills_with(FIRE, nether, 50) · Mrd Y
		sink.add(done(art("ten_thousand_crow_vase", 304, Dao.FIRE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.HOLD_ITEM_UP)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.FIRE, Cond.IN_NETHER, null, 50))).flag(PAVILION)
				.qi(120).cooldown(30.0D)
				.step(at(0).shape(volley().seek().count(12.0D, 40.0D).over(60).range(24.0D, 40.0D).perTarget(5))
						.hit(damage(0.8D), dot(0.3D, 5, DotKind.FIRE)))
				// A swarm of smoke crows with flame wings pours from a giant pot.
				.fx(Phase.CAST, model("crow_vase").fromHand().above(0.5F).scale(2.0F).ticks(70), swarm(0x302020).model("fire_crow").n(16).count(60))
				.fx(Phase.TRAVEL, trail(0xFF5020).particle(ParticleTypes.LARGE_SMOKE).count(4).extra())
				.fx(Phase.TRAVEL, model("fire_crow").scale(0.8F).slide(12.0F).ticks(14))
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.BLAZE_SHOOT, 1.0F, 0.9F)
				.kehancuran(DShape.scatter(2))
				.perfection(Bonus.extraCount(8))));

		// 305 · Tiga Transformasi Misterius Api Langit · DT · Bumi-M · UBAH · FEAT meditate_at(nether, 10 min), COMP 45; INH(FIRE) · Mrd N
		sink.add(done(art("heaven_fire_transformation", 305, Dao.FIRE)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.UBAH).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.SUMMON_RAISE)
				.route(Route.feat(Feat.meditateAt(Cond.IN_NETHER, 10)), Route.inh("fire")).comprehension(45).flag(NO_TEACH)
				.stages(3, 600).qi(60).qiPerStage(60, 60, 60).cooldown(120.0D)
				// ST 3 (window 600t): each stage replaces the form (lasting 600t from the last stage); the weak period follows the end.
				.step(at(0).stage(1).phase(0).self(transform("heaven_fire_1", 600).with(empower(Dao.FIRE, 0.4D, 600), buff(BuffKind.SPEED, 1, 600)),
						selfPenalty(400, 2, true, 1.0D)))
				.step(at(0).stage(2).phase(1).self(transform("heaven_fire_2", 600).with(empower(Dao.FIRE, 0.9D, 600), buff(BuffKind.SPEED, 1, 600),
						buff(BuffKind.FIRE_RESISTANCE, 1, 600), buff(BuffKind.STRENGTH, 1, 600))))
				.step(at(0).stage(3).phase(2).self(transform("heaven_fire_3", 600).with(empower(Dao.FIRE, 1.5D, 600), buff(BuffKind.SPEED, 1, 600),
						buff(BuffKind.FIRE_RESISTANCE, 1, 600), buff(BuffKind.STRENGTH, 1, 600), buff(BuffKind.SLOW_FALLING, 1, 600),
						buff(BuffKind.AIR_JUMP, 1, 600))))
				// Three nested flame layers: orange, blue soul fire, white-gold.
				.fx(Phase.CAST, shell(0xFF8030).count(30))
				.fx(Phase.EXTRA0, shieldShell(0xFF8030).count(40).ticks(40))
				.fx(Phase.EXTRA1, shieldShell(0x4FA8FF).particle(ParticleTypes.SOUL_FIRE_FLAME).count(40).ticks(40))
				.fx(Phase.EXTRA2, shieldShell(0xFFF1C0).count(50).ticks(40), flash(0xFFF1C0, 0.2F, 4))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.0F)
				.perfection(Bonus.durationTicks(200))));

		// 306 · Pukulan Phoenix Mengaum Langit · DT · Bumi-T · KENDALI · BOOK fire_aspect, COMP 45 · Mrd Y
		sink.add(done(art("phoenix_sky_roaring_strike", 306, Dao.FIRE)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.KENDALI).realm(Realm.DEITY_TRANSFORMATION)
				.pose(Pose.ROAR)
				.route(Route.book("minecraft:fire_aspect")).comprehension(45)
				.qi(100).cooldown(25.0D)
				.step(at(0).shape(nova().radius(5.0D, 14.0D)).hit(bind(BindKind.STUN, 40)))
				.step(at(0).shape(pillar(Anchor.CASTER).onTargets(8.0D, 16.0D).radius(2.0D).height(8.0D).delay(6))
						.hit(damage(2.5D), launch(0.8D), dot(0.4D, 5, DotKind.FIRE)))
				.fx(Phase.CAST, shockRing(0xFF7A20).count(30))
				// Lava geysers 8 b high, particles only.
				.fx(Phase.IMPACT, column(0xFF7A20).count(30), column(0xFFB040).particle(ParticleTypes.FLAME).count(16).detail(),
						column(0xFF5010).particle(ParticleTypes.LAVA).count(6).extra(), shake(0.4F, 12))
				.fx(Phase.CAST, model("fire_phoenix").onCaster().above(3.0F).scale(6.0F).grow(0.3F, 6.0F).ticks(20))
				.fx(Phase.TRAVEL, column(0xFF7A20).count(8).ticks(6).detail())
				.sound(Phase.IMPACT, SoundEvents.LAVA_POP, 1.2F, 1.0F)
				.sound(Phase.IMPACT, SoundEvents.MAGMA_CUBE_JUMP, 1.0F, 0.8F)
				.kehancuran(DShape.scatter(2))
				.perfection(Bonus.extraCount(4))));

		// 307 · Tudung Api Ilahi Sembilan Naga · DU · Bumi-R · AREA · PAV · Mrd Y
		sink.add(done(art("nine_dragon_fire_cover", 307, Dao.FIRE)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.pav()).flag(PAVILION)
				.qi(200).cooldown(60.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32.0D).radius(6.0D, 16.0D).walls().duration(90).pulse(10))
						.hit(damage(1.2D), ignite(2.0D)))
				.step(at(90).shape(zone(Anchor.AREA).ofArea()).hit(damage(4.0D)))
				.fx(Phase.CAST, dome(0xE03A1A).count(60), beast(FxLayer.BeastKind.DRAGON, 0xFFB020).n(9).count(90),
						model("dragon_head").scale(3.0F).n(5).ticks(90),
						ribbon(0xFFB020).color2(0xE03A1A).model("dragon_segment").n(9).radius(12.0F).ticks(90))
				.fx(Phase.PULSE, dome(0xFF6020).count(12).extra())
				.fx(Phase.IMPACT, shell(0xFFB020).count(40), shake(0.3F, 12))
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 0.4F, 1.4F)
				.kehancuran(DShape.bowl(6).at(90))
				.perfection(Bonus.area(0.2D))));

		// 308 · Api Sejati Samadhi · DU · Bumi-M · AREA · FEAT kills_with(FIRE, drowned/guardian, 30), COMP 70 · Mrd Y
		sink.add(done(art("samadhi_true_fire", 308, Dao.FIRE)
				.grade(TechniqueGrade.DI, 1).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.pose(Pose.SUMMON_RAISE).pose(Phase.PULSE, Pose.TWIN_PALM)
				.route(Route.feat(Feat.killsWith(Dao.FIRE, Cond.NONE, List.of("minecraft:drowned", "minecraft:guardian"), 30))).comprehension(70)
				.qi(180).qiPerSecond(20.0D).cooldown(45.0D).channel(60, 5)
				// CHAN 60t/5: the cone pulses every 5 ticks (vs FIRE_IMMUNE ×1, WET ×1.5: water only feeds it).
				.step(at(0).repeat(12, 5).shape(cone().length(12.0D, 24.0D).halfAngle(35.0D))
						.hit(damage(0.8D).vs(Versus.FIRE_IMMUNE, 1.0D).vs(Versus.WET, 1.5D),
								dot(0.5D, 10, DotKind.FIRE).vs(Versus.FIRE_IMMUNE, 1.0D).vs(Versus.WET, 1.5D), eff(Eff.BLINDNESS, 1, 40)))
				// SUMMON 5 flame carts (anchor pentagon, 200t, attack 20t, range 12) > DMG .6.
				.step(at(0).self(summon("flame_cart", 5, 200).mode(SummonMode.ANCHOR).pattern(Pattern.PENTAGON).interval(20).range(12.0D)
						.attack(at(0).shape(all().at(Anchor.SUMMON).radius(12.0D).max(1.0D)).hit(damage(0.6D)))))
				// A crimson-violet jet with smoke from the nose; blazing minecarts.
				.fx(Phase.CAST, coneSpray(0xC0207A).count(40), coneSpray(0x404040).particle(ParticleTypes.LARGE_SMOKE).size(0.4F).count(6).extra())
				.fx(Phase.PULSE, coneSpray(0xC0207A).count(16))
				// each flame cart (a synced phantom) flares at its victim
				.fx(Phase.PHANTOM, beamCore(0xC0207A).count(10).ticks(6), impact(0xFF6020).particle(ParticleTypes.FLAME).onTarget().count(8).ticks(6))
				.sound(Phase.CAST, SoundEvents.FIRE_AMBIENT, 1.4F, 0.8F)
				.sound(Phase.CAST, SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F)
				.kehancuran(DShape.fan(16, 35, 2))
				.perfection(Bonus.cooldown(0.2D))));

		// 309 · Tungku Delapan Trigram · IA T1 · Langit-R · KENDALI · FEAT alchemy_count 49; PAV · Mrd Y
		sink.add(done(art("eight_trigrams_furnace", 309, Dao.FIRE)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.KENDALI).realm(Realm.IMMORTAL_ASCENSION, 1)
				.pose(Pose.SEAL)
				.route(Route.feat(Feat.alchemyCount(49)), Route.pav()).flag(PAVILION)
				.qi(280).cooldown(90.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32.0D).radius(16.0D, 28.0D).duration(200).pulse(20))
						.hit(damage(1.0D).vs(Versus.UNDEAD, 2.0D)))
				// @0-60 PULL .5 toward the furnace; @0 root 140t (boss 35t).
				.step(at(0).shape(all().ofArea().max(32.0D)).hit(pull(0.5D).toward(Center.AREA).perTick(60), bind(BindKind.ROOT, 140).boss(35)))
				.step(at(200).shape(nova().radius(8.0D).at(Anchor.AREA)).hit(damage(4.0D)).phase(0))
				// The eight trigram glyphs ☰☱☲☳☴☵☶☷ stand around the 16-b circle (GLYPH n = 8 splits the text onto a ring).
				.fx(Phase.CAST, model("bagua_furnace").scale(10.0F).ticks(200).facing(FxLayer.Facing.UPRIGHT), runeCircle(0xC08A3A).count(60),
						FxLayer.of(FxLayer.Template.GLYPH, 0xFFD54A).text("☰☱☲☳☴☵☶☷").n(8).radius(16.0F).scale(3.0F))
				.fx(Phase.PULSE, vortex(0xFF8030).inward().count(20))
				.fx(Phase.EXTRA0, shell(0xFFB040).count(50), shake(0.3F, 12))
				.sound(Phase.CAST, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1.4F, 0.7F)
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.2F, 0.8F)
				.kehancuran(DShape.pit(6, 3))
				.perfection(Bonus.area(0.2D))));

		// 310 · Api Phoenix Membakar Langit · IA T4 · Langit-M · PAMUNGKAS · FEAT first_kill(WITHER, FIRE) · Mrd Y
		sink.add(done(art("phoenix_flame_sears_heavens", 310, Dao.FIRE)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 4)
				.pose(Pose.SUMMON_RAISE).pose(Phase.TRAVEL, Pose.DASH_LEAN)
				.route(Route.feat(Feat.firstKill("minecraft:wither", Dao.FIRE)))
				.qi(300).cooldown(60.0D)
				// FRONT dive (L48, w3, h4, v3) > DMG 3; onEnd ZONE(r 10→18) > DMG 6 + DOT fire 1/s 8s + IGN 8s.
				.step(at(0).shape(front().length(48.0D).width(3.0D).height(4.0D).speed(3.0D).toAim()).hit(damage(3.0D))
						.onEnd(at(0).shape(zone(Anchor.END).radius(10.0D, 18.0D)).hit(damage(6.0D), dot(1.0D, 8, DotKind.FIRE), ignite(8.0D))))
				// A giant sky-scale fire phoenix (20→40 b wingspan) and a red vignette.
				.fx(Phase.CAST, skySilhouette(FxLayer.BeastKind.BIRD, 0xFF5020).scale(20.0F).ticks(40), beast(FxLayer.BeastKind.BIRD, 0xFF7030).size(4.0F).count(60),
						vignette(0xC02010, 30))
				.fx(Phase.TRAVEL, trail(0xFF5020).particle(ParticleTypes.FLAME).count(20))
				.fx(Phase.IMPACT, shell(0xFF6020).count(60), shake(0.7F, 20))
				.fx(Phase.TRAVEL, model("fire_phoenix").scale(8.0F).slide(16.0F).ticks(20))
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_FLAP, 1.4F, 1.0F)
				.sound(Phase.IMPACT, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 0.8F)
				.kehancuran(DShape.bowl(8))
				.perfection(Bonus.area(0.25D))));

		// 311 · Api Kaisar Emas Pembakar Langit · IA T7 · Langit-T · AREA · FEAT kills_with(FIRE, wither, 3), COMP 85 · Mrd Y
		sink.add(done(art("golden_emperor_flame", 311, Dao.FIRE)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 7)
				.pose(Pose.ARMS_SPREAD)
				.route(Route.feat(Feat.killsWith(Dao.FIRE, Cond.NONE, List.of("minecraft:wither"), 3))).comprehension(85)
				.qi(400).cooldown(120.0D)
				// FIELD(aim ≤48, r 20→32, 200t, pulse 20) > DMG 1.5 + STRIP_BUFFS (resistance, absorption, strength, regeneration,
				// fire resistance, speed: the default list).
				.step(at(0).shape(field(Anchor.AIM).range(48.0D).radius(20.0D, 32.0D).duration(200).pulse(20)).hit(damage(1.5D), stripBuffs()))
				.fx(Phase.CAST, mist(0xFFE8A0).particle(ParticleTypes.FLAME).count(80), healRise(0xFFD060).count(30).detail(), vignette(0xFFC000, 40))
				.fx(Phase.PULSE, mist(0xFFE8A0).count(20).extra())
				.fx(Phase.CAST, glyph("帝").color2(0xA06000).scale(8.0F).above(10.0F).ticks(60))
				.sound(Phase.CAST, SoundEvents.BLAZE_AMBIENT, 1.4F, 0.8F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BLAZE_AMBIENT, 1.4F, 1.0F).delay(5))
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BLAZE_AMBIENT, 1.4F, 1.2F).delay(10))
				// the first burning pulse scorches a crater at the field's centre
				.kehancuran(DShape.bowl(8))
				.perfection(Bonus.area(0.2D))));

		// 312 · Pilar Api Ilahi Penembus Langit · IA T9 · Langit-T · AREA · FEAT trib(IA layer quality ≥ Sempurna) · Mrd Y
		sink.add(done(art("tongtian_divine_fire_pillars", 312, Dao.FIRE)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.AREA).realm(Realm.IMMORTAL_ASCENSION, 9)
				.pose(Pose.SKY_RAISE)
				.route(Route.feat(Feat.tribQuality(Realm.IMMORTAL_ASCENSION, -1, BreakthroughQuality.PERFECT)))
				.qi(450).cooldown(150.0D)
				// FIELD octagon (aim ≤48, r 14→24, walls, 120t, 8 emitters, pulse 20).
				.step(at(0).shape(field(Anchor.AIM).range(48.0D).radius(14.0D, 24.0D).form(Shape.Field.Form.OCTAGON).walls().duration(120).pulse(20)
						.emitters(8)))
				// Each emitter > DMG 1.2 on one target per pulse (≤32 per pulse).
				.step(at(20).repeat(5, 20).shape(all().at(Anchor.EMITTERS).radius(24.0D).max(1.0D)).hit(damage(1.2D)))
				// @120 NOVA(field) > DMG 6.
				.step(at(120).shape(nova().ofArea()).hit(damage(6.0D)).phase(0))
				// Eight magma pillars 10 b tall crowned with shroomlight, on the octagon's emitters, with coiling dragon ribbons.
				.fx(Phase.CAST, giantBlock(Blocks.MAGMA_BLOCK.defaultBlockState()).blockSize(1.0F, 10.0F, 1.0F).n(8).radius(24.0F),
						giantBlock(Blocks.SHROOMLIGHT.defaultBlockState()).blockSize(1.4F, 1.4F, 1.4F).above(10.0F).n(8).radius(24.0F).detail(),
						ribbon(0xFF8020).color2(0xFF3010).model("dragon_segment").n(8).radius(24.0F).ticks(120),
						beast(FxLayer.BeastKind.DRAGON, 0xFF8020).n(8).count(40).extra())
				.fx(Phase.PULSE, column(0xFF6010).count(16).extra())
				.fx(Phase.EXTRA0, shell(0xFFB040).count(80), shake(0.6F, 18))
				.fx(Phase.IMPACT, column(0xFF6010).count(20).ticks(8), impact(0xFFB040).particle(ParticleTypes.FLAME).count(10).ticks(6).detail())
				.sound(Phase.CAST, SoundEvents.BLAZE_SHOOT, 1.4F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.5F)
				.kehancuran(DShape.column(2, 8).times(8))
				.perfection(Bonus.area(0.2D))));

		// 313 · Murka Langit Sembilan Matahari · TI · Abadi-R · PAMUNGKAS · FEAT first_kill(ENDER_DRAGON, FIRE) · Mrd Y★27
		sink.add(done(art("nine_suns_heavens_fury", 313, Dao.FIRE)
				.grade(TechniqueGrade.ABADI, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Phase.CHARGE, Pose.CHARGE_OVERHEAD).pose(Pose.TWIN_PALM)
				.route(Route.feat(Feat.firstKill("minecraft:ender_dragon", Dao.FIRE)))
				.teachStars(27)
				.qiPct(55.0D, 700).cooldown(360.0D).charge(60)
				// CHG 60t ("15 breaths"); ZONE DENSEST(9) (aim ≤64, search r24) x9/6 (r 8→12): repetition k lands on dense point k.
				.step(at(0).repeat(9, 6).shape(zone(Anchor.densest(9, 24.0D)).range(64.0D).radius(8.0D, 12.0D).fromSky(40.0D).delay(6))
						.hit(damage(6.0D), dot(2.0D, 6, DotKind.FIRE)))
				.step(at(60).shape(zone(Anchor.AIM).range(64.0D).radius(24.0D)).hit(damage(12.0D)).phase(0))
				// The "15 breaths" (十五息) hang over the caster through the charge; a corona ring flares around each falling sun.
				.fx(Phase.CHARGE, sky(0xFFF0C0), cloudDisc(0xFFD040).above(30.0F), glyph("十五息").onCaster().above(3.0F).scale(2.0F).ticks(60))
				.fx(Phase.CAST, column(0xFFD040).count(30), flash(0xFFF1C0, 0.2F, 4))
				// each sun falls on its own pre-impact TRAVEL (6 ticks) and bursts on the IMPACT
				.fx(Phase.TRAVEL, model("sun").scale(5.0F).fall(40.0F, 6.0F).ticks(7), meteor(0xFFD040).count(30).ticks(6))
				.fx(Phase.IMPACT, shockRing(0xFFF1C0).color2(0xFF8020).size(1.4F).count(36).ticks(10), impact(0xFFD040).count(30).ticks(8),
						column(0xFFB040).particle(ParticleTypes.FLAME).count(16).ticks(10).detail())
				.fx(Phase.EXTRA0, shell(0xFFF1C0).count(100), flash(0xFFF1C0, 0.3F, 6), shake(1.0F, 30))
				.sound(Phase.IMPACT, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 0.7F)
				.sound(Phase.EXTRA0, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F)
				.kehancuran(DShape.bowl(4).times(9), DShape.bowl(8))
				.perfection(Bonus.extraCount(3))));

		// 314 · Api Kaisar · Dewa · Dewa-R · PAMUNGKAS · GATE I (prereq nine_suns_heavens_fury L3) · Mrd Y★36
		sink.add(done(art("emperor_flame", 314, Dao.FIRE)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.pose(Pose.SUMMON_RAISE).climax(Phase.EXTRA0)
				.gate(TechniqueSpec.Gate.DEWA_I).prereq("nine_suns_heavens_fury", 3).dewaStars(36).teachStars(36)
				.qiPct(75.0D).cooldown(900.0D)
				// STACK onKill (max 22): the final nova counts them itself (+.5 each).
				.stack(Flow.Stack.onKill(0.0D, 22))
				// FIELD(aim ≤64, r 32→40, 400t, pulse 20) > DMG 2 + IGN 3s; SELF inside the field: COST_MOD(FIRE qi ×0, cd ×0.4 floor).
				.step(at(0).shape(field(Anchor.AIM).range(64.0D).radius(32.0D, 40.0D).duration(400).pulse(20)).hit(damage(2.0D), ignite(3.0D))
						.self(costMod(Dao.FIRE, 0.0D, 0.4D, 400).insideArea()))
				// @400 NOVA(r40) at the lotus > DMG 15 + .5 × stacks.
				.step(at(400).shape(nova().radius(40.0D).at(Anchor.AREA)).hit(damage(15.0D).perStack(0.5D)).phase(0))
				// A white-violet lotus of 16 glass petals, 80 b across, and the glyph 帝.
				.fx(Phase.CAST, model("lotus_petal_violet").n(16).radius(40.0F).scale(8.0F).ticks(400).facing(FxLayer.Facing.FLAT), petalRing(Blocks.STAINED_GLASS.magenta().defaultBlockState(), 16, 40.0F).extra(),
						FxLayer.of(FxLayer.Template.GLYPH, 0xF0D8FF).text("帝").scale(16.0F).above(12.0F), mist(0xF0D8FF).count(40).detail())
				.fx(Phase.PULSE, petals(0xF0D8FF).count(20).extra())
				.fx(Phase.EXTRA0, shell(0xF0D8FF).count(120), flash(0xF8F0FF, 0.3F, 6), shake(0.8F, 30))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.5F)
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.5F, 1.0F)
				.kehancuran(DShape.bowl(10))
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

	private FireArts() {
	}
}

package dev.kultivator.technique.arts;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec.Flag;
import dev.kultivator.data.TechniqueSpec.Gate;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.special.FlightForm;
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
import dev.kultivator.technique.spec.dsl.Payload.BuffKind;
import dev.kultivator.technique.spec.dsl.Payload.Eff;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Petir / Lightning catalogue (DESIGN 22.9.4, net ids 500-513): 14 specs (Misterius 5, Bumi 4, Langit 3, Abadi 1,
 * Dewa 1). Builder DSL only (22.2.5): lightning is always particle bolts drawn by the client, never a
 * {@code LightningBolt}, never a weather change; the KH shapes are carved only by {@code Kehancuran.submitSelf} while the
 * player's own switch is on (22.11). Numbers are the catalogue's ({@code b→c} = base → spec cap). The level 3
 * {@code perfection} bonuses are game-design picks of 22.6; the level 4 {@code peak} is ECHO 0.4 where 22.3.4 allows it
 * and DURATION 0.25 otherwise. {@code lei_zhenzi_wings} flies through the {@link FlightForm} special.
 */
public final class LightningArts {

	/** Lightning violet (the Dao colour), pale spark, deep storm, gold. */
	private static final int VIOLET = 0xB57CFF;
	private static final int PALE = 0xE8D8FF;
	private static final int SPARK = 0xD8B0FF;
	private static final int DEEP = 0x4A2F90;
	private static final int STORM = 0x2A1A50;
	private static final int GOLD = 0xFFD54A;

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(FlightForm.ID, new FlightForm());
		BlockState violetGlass = Blocks.STAINED_GLASS.pick(DyeColor.PURPLE).defaultBlockState();

		// 500 · Cambuk Zidian (Mo Dao Zu Shi)
		sink.add(art("zidian_whip", 500, Dao.LIGHTNING)
				.grade(TechniqueGrade.XUAN, 0).type(TechniqueType.SERANG).realm(Realm.GOLDEN_CORE)
				.route(Route.book("minecraft:channeling")).comprehension(15)
				.flag(Flag.TEACHABLE, Flag.REQUIRES_TARGET)
				.qi(25).cooldown(6.0D)
				.step(at(0).shape(chain().range(10, 24).jumps(1, 4).jump(6).falloff(0.45D))
						.hit(damage(1.2D), bind(BindKind.STUN, 30)))
				// "first hop also PULL 4 b": the first hop is the locked target.
				.step(at(0).shape(target().range(10, 24)).hit(pull(4.0D).blocks()))
				.fx(Phase.CAST,
						shell(PALE).size(0.6F).count(14).ticks(8),
						impact(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(10).detail())
				.fx(Phase.TRAVEL, bolt(VIOLET).count(30).ticks(6))
				.fx(Phase.HIT,
						bolt(VIOLET).count(24).ticks(6),
						impact(SPARK).particle(ParticleTypes.ELECTRIC_SPARK).count(8).detail())
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 2.0F)
				.sound(Phase.HIT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.2F, 1.8F)
				.perfection(Bonus.chain(2))
				.peak(Bonus.echo(0.4D))
				.build());

		// 501 · Kilat: Tiga Ribu Gerak Petir (Battle Through the Heavens)
		sink.add(art("lightning_flash_step", 501, Dao.LIGHTNING)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.GERAK).realm(Realm.GOLDEN_CORE)
				.route(Route.dao()).comprehension(10)
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.charges(2, 80)
				.qi(20).cooldownTicks(80)
				.step(at(0).shape(path().halfWidth(1.5D)).self(dash(8, 16))
						.hit(damage(0.8D), bind(BindKind.STUN, 10)))
				.step(at(6).minTier(2).shape(nova().radius(3).at(Anchor.AFTERIMAGE)).hit(damage(1.0D)))
				.fx(Phase.CAST,
						afterimage(SPARK).count(16).ticks(12),
						impact(PALE).particle(ParticleTypes.ELECTRIC_SPARK).count(10).detail())
				.fx(Phase.TRAVEL,
						trail(PALE).count(30).ticks(8),
						trail(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(14).detail())
				.fx(Phase.IMPACT, shell(VIOLET).size(1.5F).count(18), afterimage(SPARK).count(8).detail())
				.sound(Phase.CAST, SoundEvents.TRIDENT_RIPTIDE_1, 0.9F, 2.0F)
				.sound(Phase.IMPACT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 1.8F)
				.kehancuran(DShape.trenchPath(1, 1))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 502 · Tanda, Zirah dan Sayap Petir (Martial God Asura)
		sink.add(art("lightning_mark_armor", 502, Dao.LIGHTNING)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.LINDUNG).realm(Realm.GOLDEN_CORE)
				.route(Route.dao()).comprehension(20)
				.flag(Flag.TEACHABLE, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(40).cooldown(45.0D)
				.step(at(0).self(
						empower(0.15D, 600),
						buff(BuffKind.SPEED, 1, 600),
						hook(Hook.onMelee(at(0).shape(chain().from(Anchor.HIT).jumps(1, 4)).hit(damage(0.5D))).ticks(600).icd(10)),
						hook(Hook.onHurt(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(0.4D))).ticks(600).icd(10))))
				// "t≥2: slow-falling wings"; disciples learn it without the wings (Mrd "Y (no wings)").
				.step(at(0).minTier(2).only(Cond.PLAYER_CASTER).self(buff(BuffKind.SLOW_FALLING, 1, 600)))
				.fx(Phase.CAST,
						shieldShell(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(40).ticks(200),
						wings(SPARK).minTier(2).count(40).ticks(600).detail())
				.fx(Phase.HIT, bolt(VIOLET).count(20).ticks(5))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F)
				.sound(Phase.HIT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.2F, 2.0F)
				.perfection(Bonus.chain(2))
				.peak(Bonus.duration(0.25D))
				.build());

		// 503 · Tiga Gaya Guntur (Martial God Asura)
		sink.add(art("three_thunder_styles", 503, Dao.LIGHTNING)
				.grade(TechniqueGrade.XUAN, 1).type(TechniqueType.AREA).realm(Realm.NASCENT_SOUL)
				.route(Route.book("minecraft:channeling"), Route.pav()).comprehension(30)
				.flag(Flag.TEACHABLE, Flag.PAVILION)
				.stages(3, 80).qiPerStage(25, 30, 45)
				.qi(25).cooldown(12.0D)
				// Stage 1 draws the CAST layers; recast stages run one tick in so their own EXTRA layers are sent.
				.step(at(0).stage(1).shape(path()).self(dash(6)).hit(damage(1.2D)))
				.step(at(1).stage(2).shape(cone().length(8, 14).halfAngle(35)).hit(damage(1.5D), bind(BindKind.STUN, 20)).phase(1))
				.step(at(1).stage(3).shape(zone(Anchor.AIM).range(20).radius(4, 8)).hit(damage(2.5D), launch(0.6D)).phase(2))
				.fx(Phase.CAST, bolt(VIOLET).size(0.8F).count(20).ticks(6), trail(PALE).count(24).detail())
				.fx(Phase.TRAVEL, trail(PALE).count(24), bolt(VIOLET).size(0.8F).count(16).detail())
				.fx(Phase.EXTRA1, coneSpray(SPARK).particle(ParticleTypes.ELECTRIC_SPARK).count(40), bolt(VIOLET).size(1.2F).count(24).fanned())
				.fx(Phase.EXTRA2, bolt(PALE).size(1.8F).count(36), shockRing(VIOLET).size(1.5F).count(40), cracks())
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 1.6F)
				.sound(Phase.EXTRA1, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.3F)
				.sound(Phase.EXTRA2, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 1.0F)
				.sound(Phase.EXTRA2, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 0.9F)
				.kehancuran(DShape.bowl(3).stage(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 504 · Metode Lima Guntur (Journey to the West ch. 45)
		sink.add(art("five_thunder_method", 504, Dao.LIGHTNING)
				.grade(TechniqueGrade.XUAN, 2).type(TechniqueType.KENDALI).realm(Realm.NASCENT_SOUL)
				.route(Route.feat(Feat.castsWith(Dao.LIGHTNING, Cond.any(Cond.RAINING, Cond.THUNDERING), 50)))
				.flag(Flag.TEACHABLE)
				.stages(4, 120).qiPerStage(20, 20, 50, 20)
				.qi(20).cooldown(20.0D)
				// Stage 1 (wind) draws the CAST layers; recast stages run one tick in so their own EXTRA layers are sent.
				.step(at(0).stage(1).shape(nova().radius(6, 12)).hit(knock(1.0D)))
				.step(at(1).stage(2).shape(nova().radius(12)).hit(eff(Eff.BLINDNESS, 1, 60)).phase(1))
				// The third strike calls the storm (EXTRA2); its impacts land as PULSE events.
				.step(at(1).stage(3).phase(2))
				.step(at(1).stage(3).shape(rain(Anchor.CASTER, 0).radius(12, 20).impacts(5, 12).over(30))
						.hit(damage(1.5D), bind(BindKind.STUN, 20)))
				.step(at(1).stage(4).shape(nova().radius(12)).hit(mark(Mark.wet(), 300)).self(extinguish()).phase(3))
				.fx(Phase.CAST,
						FxLayer.of(FxLayer.Template.GLYPH, GOLD).text("令").scale(2.5F).above(2.5F).ticks(20),
						vortex(0xEEF4F0).particle(ParticleTypes.GUST).count(6),
						shockRing(0xDDE6EE).count(30).detail())
				.fx(Phase.EXTRA1, cloudDisc(0x5A6A8A).above(12).count(60).ticks(60))
				.fx(Phase.EXTRA2, boltStorm(SPARK).count(40).ticks(30), shake(0.25F, 10))
				.fx(Phase.IMPACT, bolt(PALE).count(20).ticks(5))
				.fx(Phase.EXTRA3, mist(0x9FC8FF).particle(ParticleTypes.FALLING_WATER).count(80).ticks(40), cloudDisc(0x5A6A8A).above(12).count(30).detail())
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 0.6F, 1.8F)
				.sound(Phase.CAST, SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.8F, 1.0F)
				.sound(Phase.EXTRA1, SoundEvents.ELYTRA_FLYING, 0.4F, 0.6F)
				.sound(Phase.EXTRA2, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.1F)
				.sound(Phase.IMPACT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.4F)
				.sound(Phase.EXTRA3, SoundEvents.WEATHER_RAIN, 1.0F, 1.0F)
				.kehancuran(DShape.scatter(1).stage(3))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 505 · Seni Pusaka Kaisar Petir (Perfect World)
		sink.add(art("lightning_emperor_art", 505, Dao.LIGHTNING)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.SERANG).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.book("minecraft:channeling"), Route.pav()).comprehension(50)
				.flag(Flag.TEACHABLE, Flag.PAVILION)
				.qi(100).cooldown(15.0D)
				.step(at(0).shape(beam().length(40, 64).halfWidth(1).pierce())
						.hit(damage(3.0D), mark(Mark.daoVuln(Dao.LIGHTNING, 0.3D), 200))
						.onHit(at(0).minTier(4).shape(rain(Anchor.HIT, 0).radius(4).impacts(5).over(10)).hit(damage(1.0D))))
				.fx(Phase.CAST,
						palm(VIOLET).inSky().size(6.0F).count(80).ticks(20),
						impact(PALE).particle(ParticleTypes.ELECTRIC_SPARK).count(12).detail())
				.fx(Phase.TRAVEL, beamCore(PALE).size(1.0F).count(60).ticks(8), bolt(VIOLET).count(20).detail())
				.fx(Phase.HIT, impact(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(12))
				.fx(Phase.IMPACT, bolt(SPARK).count(14).ticks(4))
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 0.8F)
				.sound(Phase.TRAVEL, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F)
				.sound(Phase.IMPACT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 1.6F)
				.kehancuran(DShape.tunnel(1, 24))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.echo(0.4D))
				.build());

		// 506 · Formasi Cahaya Emas (Investiture of the Gods ch. 44/46)
		sink.add(art("golden_light_formation", 506, Dao.LIGHTNING)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
				.route(Route.pav(), Route.feat(Feat.killsWith(Dao.LIGHTNING, Cond.NONE, List.of("#minecraft:undead"), 100)))
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.NO_ECHO)
				.qi(150).cooldown(45.0D)
				.step(at(0).shape(field(Anchor.AIM).range(32).radius(10, 18).duration(160).pulse(20)
						.form(Shape.Field.Form.RING).emitters(7, 21)))
				// "ALL(field, max 8→16)" every pulse of the 160-tick field.
				.step(at(20).repeat(8, 20).shape(all().ofArea().max(8, 16))
						.hit(damage(0.9D).vs(Payload.Versus.UNDEAD, 1.5D), eff(Eff.BLINDNESS, 1, 40)).phase(0))
				// The CAST event runs from the caster to the aim; the formation stands at the aim (TARGET attach).
				.fx(Phase.CAST,
						swordRing(Items.GLASS_PANE, 9, 10.0F).scale(3.0F).onTarget().minTier(3).ticks(160),
						shockRing(GOLD).size(2.0F).onTarget().count(40),
						column(GOLD).size(0.6F).onTarget().count(30).ticks(160).detail())
				.fx(Phase.EXTRA0, shell(0xFFF0C0).particle(ParticleTypes.END_ROD).count(10).detail())
				.fx(Phase.HIT, beamCore(GOLD).count(24).ticks(5), impact(GOLD).particle(ParticleTypes.ELECTRIC_SPARK).count(8).detail())
				.sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.0F, 1.0F)
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.2F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.6F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 507 · Seni Awan Ungu: Awan Petir Pemusnah (Against the Gods)
		sink.add(art("extinction_thundercloud", 507, Dao.LIGHTNING)
				.grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DAO_UNION)
				.route(Route.pav(), Route.book("minecraft:channeling")).comprehension(60)
				.flag(Flag.TEACHABLE, Flag.PAVILION, Flag.NO_ECHO)
				.qi(220).cooldown(75.0D)
				.step(at(0).shape(field(Anchor.AIM).range(48).radius(16, 32).duration(200)))
				.step(at(0).shape(rain(Anchor.AREA, 0).ofArea().impacts(40).over(200).impactRadius(2))
						.hit(damage(1.5D), bind(BindKind.STUN, 10)))
				.step(at(200).shape(all().ofArea().max(12, 32)).hit(damage(4.0D)).phase(0))
				.fx(Phase.CAST,
						cloudDisc(DEEP).above(24).size(2.0F).onTarget().count(80).ticks(200),
						boltStorm(SPARK).above(24).onTarget().count(40).ticks(200).detail())
				.fx(Phase.IMPACT, bolt(PALE).count(24).ticks(5), impact(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(8).detail())
				.fx(Phase.PULSE, boltStorm(SPARK).above(24).count(12).ticks(10).extra())
				.fx(Phase.EXTRA0, boltStorm(PALE).count(80).ticks(12), shell(VIOLET).size(3.0F).count(40), flash(PALE, 0.2F, 4))
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.8F)
				.sound(Phase.IMPACT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.2F)
				.sound(Phase.PULSE, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.0F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.7F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 0.8F)
				.kehancuran(DShape.scatter(1))
				.perfection(Bonus.extraCount(8))
				.peak(Bonus.duration(0.25D))
				.build());

		// 508 · Seni Sejati Pedang Ilahi Mengendalikan Guntur (Jade Dynasty)
		sink.add(art("divine_sword_imperial_thunder", 508, Dao.LIGHTNING)
				.grade(TechniqueGrade.DI, 2).type(TechniqueType.SERANG).realm(Realm.DAO_UNION)
				.route(Route.feat(Feat.killsWith(Dao.LIGHTNING, Cond.HOLDING_SWORD, null, 200))).comprehension(70)
				.flag(Flag.TEACHABLE)
				.charge(30)
				.qi(240).cooldown(50.0D)
				.step(at(0).shape(beam().length(32, 64).halfWidth(2))
						.hit(damage(5.0D))
						.when(Cond.not(Cond.HOLDING_SWORD), 0.7D)
						.onEnd(at(0).shape(chain().from(Anchor.END).jumps(6).jump(6)).hit(damage(2.0D))))
				.fx(Phase.CHARGE,
						column(SPARK).size(1.2F).count(60).ticks(30),
						swordRing(Items.IRON_SWORD, 12, 2.5F).scale(1.5F).minTier(3).ticks(40))
				.fx(Phase.CAST,
						beamCore(PALE).size(1.6F).count(80).ticks(8),
						bolt(VIOLET).fanned().count(30).ticks(8).detail(),
						shake(0.5F, 12))
				.fx(Phase.TRAVEL, bolt(SPARK).count(24).ticks(6))
				.fx(Phase.HIT, bolt(SPARK).count(20).ticks(5), impact(PALE).particle(ParticleTypes.ELECTRIC_SPARK).count(10).detail())
				.sound(Phase.CHARGE, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F)
				.sound(Phase.CAST, SoundEvents.TRIDENT_THUNDER, 1.2F, 1.0F)
				.sound(Phase.HIT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.4F)
				.kehancuran(DShape.trench(24, 3, 3))
				.perfection(Bonus.chain(3))
				.peak(Bonus.echo(0.4D))
				.build());

		// 509 · Sayap Lei Zhenzi (Investiture of the Gods ch. 21) — special flight_form
		sink.add(art("lei_zhenzi_wings", 509, Dao.LIGHTNING)
				.grade(TechniqueGrade.TIAN, 0).type(TechniqueType.UBAH).realm(Realm.IMMORTAL_ASCENSION, 1)
				.route(Route.feat(Feat.meditateAt(Cond.all(Cond.altitude(150), Cond.THUNDERING), 5)), Route.inh(Dao.LIGHTNING.id()))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.special(FlightForm.ID)
				.stages(2, FlightForm.FORM_TICKS).qiPerStage(250, 0)
				.qi(250).qiPerSecond(FlightForm.QI_PER_SECOND).cooldown(120.0D)
				.step(at(0).stage(1).self(
						transform(FlightForm.FORM, FlightForm.FORM_TICKS).with(
								buff(BuffKind.ATTACK_DAMAGE, 0.5D, FlightForm.FORM_TICKS),
								buff(BuffKind.REACH, 2, FlightForm.FORM_TICKS)),
						hook(Hook.onMelee(at(0).shape(nova().radius(3).at(Anchor.HIT)).hit(damage(1.5D))).icd(10))))
				// The recast dive (FlightForm runs it) one tick in, so its EXTRA0 layers are sent.
				.step(at(1).stage(2).shape(path()).self(dash(16)).hit(damage(2.0D)).phase(0))
				.fx(Phase.CAST,
						wings(0xFFE08A).size(1.6F).count(60).ticks(FlightForm.FORM_TICKS),
						FxLayer.of(FxLayer.Template.GLYPH, 0xEEF4F0).text("風").scale(1.5F).above(1.0F).onCaster().minTier(2).ticks(FlightForm.FORM_TICKS),
						FxLayer.of(FxLayer.Template.GLYPH, VIOLET).text("雷").scale(1.5F).above(1.6F).onCaster().minTier(2).ticks(FlightForm.FORM_TICKS),
						giantItem(Items.BLAZE_ROD).scale(3.0F).onCaster().minTier(3).ticks(FlightForm.FORM_TICKS),
						bolt(SPARK).count(20).ticks(6).detail())
				.fx(Phase.HIT, shell(GOLD).size(1.2F).count(16), bolt(VIOLET).count(10).detail())
				.fx(Phase.EXTRA0, trail(0xFFE08A).count(30).ticks(8), bolt(PALE).count(20).detail())
				.sound(Phase.CAST, SoundEvents.PHANTOM_FLAP, 1.0F, 0.8F)
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.2F)
				.sound(Phase.HIT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.4F)
				.sound(Phase.EXTRA0, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.2F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 510 · Pedang Petir Kesengsaraan Surgawi (Against the Gods)
		sink.add(art("tribulation_thunder_sword", 510, Dao.LIGHTNING)
				.grade(TechniqueGrade.TIAN, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.IMMORTAL_ASCENSION, 4)
				.route(Route.feat(Feat.tribStrikes(30)))
				.flag(Flag.TEACHABLE)
				.charge(40)
				.qi(320).cooldown(60.0D)
				.step(at(0).shape(front().fromSky(40).length(48, 64).width(3).height(12).speed(6))
						.hit(damage(6.0D))
						.when(Cond.tribStrikes(0.01D, 1.0D), 1.0D))
				.step(at(0).shape(pillar(Anchor.AIM).onTargets(5).radius(3)).hit(damage(2.0D)).phase(0))
				.fx(Phase.CHARGE, cloudDisc(STORM).above(30).size(2.0F).count(80).ticks(40), bolt(SPARK).above(30).count(16).detail())
				.fx(Phase.CAST,
						laser(violetGlass, 1.5F, 60.0F).fall(40, 8).minTier(3).ticks(30),
						beamCore(PALE).size(1.5F).count(80).ticks(10),
						shake(0.7F, 16))
				.fx(Phase.TRAVEL, bolt(VIOLET).count(30).ticks(10), cracks())
				.fx(Phase.EXTRA0, column(SPARK).size(1.0F).count(40).ticks(10), bolt(PALE).count(20).detail())
				.sound(Phase.CHARGE, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.4F)
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.8F)
				.sound(Phase.CAST, SoundEvents.TRIDENT_THUNDER, 1.2F, 0.9F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.0F)
				.kehancuran(DShape.fissure(32, 2, 5))
				.perfection(Bonus.extraCount(2))
				.peak(Bonus.echo(0.4D))
				.build());

		// 511 · Simbol Leluhur Halilintar (Martial Universe)
		sink.add(art("thunderbolt_ancestral_symbol", 511, Dao.LIGHTNING)
				.grade(TechniqueGrade.TIAN, 2).type(TechniqueType.WILAYAH).realm(Realm.IMMORTAL_ASCENSION, 7)
				.route(Route.feat(Feat.killsWith(null, Cond.THUNDERING, null, 300)))
				.flag(Flag.NO_TEACH, Flag.CAST_ALWAYS, Flag.NO_ECHO)
				.qi(420).cooldown(150.0D)
				// "CD_RATE(LIGHTNING ×2)": Lightning cooldowns run twice as fast, i.e. cooldown ×0.5 (the ×0.4 floor still holds).
				.step(at(0).shape(domain().radius(24, 32).duration(300).pulse(10)).self(cdRate(Dao.LIGHTNING, 0.5D, 300)))
				// The domain's pulse: one random hostile per pulse.
				.step(at(10).repeat(30, 10).shape(all().ofArea().max(1).order(Shape.All.Order.RANDOM)).hit(damage(1.5D)).phase(1))
				.step(at(60).repeat(5, 60).shape(nova().radius(12)).hit(damage(2.0D)).phase(0))
				.fx(Phase.CAST,
						giantBlock(Blocks.AMETHYST_BLOCK.defaultBlockState()).blockSize(2.0F, 2.0F, 2.0F).above(4.0F).onCaster().spin(3.0F).minTier(3).ticks(300),
						shell(SPARK).particle(ParticleTypes.GLOW).above(5.0F).onCaster().count(20).ticks(300),
						dome(VIOLET).count(40).ticks(300).detail())
				.fx(Phase.EXTRA0, shockRing(VIOLET).size(2.0F).count(40), boltStorm(SPARK).count(24).ticks(8).detail())
				.fx(Phase.HIT, bolt(PALE).count(20).ticks(5))
				.sound(Phase.CAST, SoundEvents.BEACON_AMBIENT, 1.2F, 1.0F)
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.0F)
				.sound(Phase.EXTRA1, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.4F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.2F)
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 512 · Formasi Kaisar Petir Kesengsaraan (Against the Gods)
		sink.add(art("tribulation_thunder_emperor_array", 512, Dao.LIGHTNING)
				.grade(TechniqueGrade.ABADI, 1).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.route(Route.feat(Feat.tribQuality(Realm.TRUE_IMMORTAL, -1, BreakthroughQuality.PERFECT)))
				.flag(Flag.TEACHABLE, Flag.NO_ECHO).teachStars(27)
				.qiPct(55, 700).cooldown(420.0D)
				.step(at(0).shape(field(Anchor.AIM).range(64).radius(24, 32).duration(200)))
				// x9/22: eight strikes, then the 9th at ×2.
				.step(at(22).repeat(8, 22).shape(all().ofArea().max(32)).hit(damage(2.0D), damagePct(0.08D, 0.03D)).phase(0))
				.step(at(198).shape(all().ofArea().max(32)).hit(damage(4.0D), damagePct(0.16D, 0.06D)).phase(1))
				.fx(Phase.CAST,
						runeCircle(VIOLET).size(32.0F).mirrored().onTarget().minTier(2).count(80).ticks(200),
						boltStorm(SPARK).above(30).onTarget().count(40).ticks(200),
						sky(STORM).minTier(4).ticks(200),
						shake(0.3F, 10))
				.fx(Phase.EXTRA0, boltStorm(PALE).count(60).ticks(8), shake(0.5F, 8))
				.fx(Phase.EXTRA1, boltStorm(PALE).size(2.0F).count(120).ticks(16), flash(PALE, 0.3F, 6), shake(1.0F, 24))
				.fx(Phase.HIT, impact(VIOLET).particle(ParticleTypes.ELECTRIC_SPARK).count(10))
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.0F)
				.sound(Phase.EXTRA1, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.5F)
				.sound(Phase.EXTRA1, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F, 0.7F)
				.kehancuran(DShape.scatter(3).times(9))
				.perfection(Bonus.area(0.25D))
				.peak(Bonus.duration(0.25D))
				.build());

		// 513 · Tianzun Guntur Sembilan Langit (Investiture of the Gods ch. 99)
		sink.add(art("nine_heavens_thunder_venerable", 513, Dao.LIGHTNING)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.gate(Gate.DEWA_I).prereq("tribulation_thunder_emperor_array", 3)
				.flag(Flag.TEACHABLE, Flag.NO_ECHO).dewaStars(36).teachStars(36)
				.qiPct(75).cooldown(900.0D)
				.step(at(0).self(
						transform("thunder_venerable", 400).scale(1.5D),
						// 12 as the row says; the runner clamps phantoms to 8 per cast (22.15).
						summon("thunder_deity", 12, 400).mode(Payload.SummonMode.FOLLOW).interval(10).range(24, 40)
								// Each deity strikes the nearest hostile within its range.
								.attack(at(0).shape(all().at(Anchor.SUMMON).radius(24, 40).max(1)).hit(damage(2.0D)))))
				.step(at(80).repeat(4, 80).shape(nova().radius(40)).hit(damage(4.0D), bind(BindKind.STUN, 40)).phase(0))
				.step(at(400).shape(all().radius(40).max(64)).hit(damage(8.0D)).phase(1))
				.fx(Phase.CAST,
						sky(STORM).minTier(4).ticks(400),
						skySilhouette(FxLayer.BeastKind.DEITY, SPARK).n(24).ticks(400),
						avatar(VIOLET).alpha(0.3F).ticks(400).detail(),
						shake(0.6F, 20))
				.fx(Phase.HIT, bolt(PALE).count(16).ticks(5))
				.fx(Phase.EXTRA0, beamCore(0xF0E8FF).size(1.2F).count(40).ticks(8), shockRing(VIOLET).size(3.0F).count(60), shake(0.6F, 12))
				.fx(Phase.EXTRA1, boltStorm(PALE).size(2.5F).count(160).ticks(20), flash(0xF0E8FF, 0.35F, 8), shake(1.0F, 30))
				.sound(Phase.CAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.5F)
				.sound(Phase.CAST, SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 0.8F)
				.sound(Phase.EXTRA0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.8F)
				.sound(Phase.EXTRA1, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.5F)
				.sound(Phase.EXTRA1, SoundCue.of(SoundEvents.TRIDENT_THUNDER, 1.5F, 0.7F).delay(4))
				// "SCATTER r4 ×6 (DENSEST)": the six densest impact points of the cast.
				.kehancuran(DShape.scatter(4).times(6))
				.perfection(Bonus.area(0.2D))
				.peak(Bonus.duration(0.25D))
				.build());
	}

	private LightningArts() {
	}
}

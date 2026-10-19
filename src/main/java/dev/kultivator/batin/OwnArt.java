package dev.kultivator.batin;

import dev.kultivator.data.Dao;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.Codex;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.technique.special.MysteryFusion;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code own_art}, the special of {@code own_art_1..3} (DESIGN 26.7): reads the caster's stored composition
 * ({@code extra.batin.ciptaan[slot]}: Dao A, Dao B, form, name, quality) and builds the form through {@link CastContext} with
 * the same run helpers {@code MysteryFusion} uses, so every 22.3.5 rule, the damage choke points and the boss, trial-mob and
 * {@code abadi_foe} rules apply. Damage = form coefficient × Q (× 1.15 for a pure single-Dao art) × P; the two Daos' domain
 * pulses (22.10.1) burst at the aim at ×{@link #PULSE_FACTOR} (a pure art: one pulse at ×{@link #PURE_PULSE}), plus the
 * {@code MysteryFusion} pair row of the two Daos. Players only (no disciples, no echo).
 * <p>
 * Sumber: as {@link Creation}; the forms and every number are game design.
 */
public final class OwnArt implements SpecialTechnique {

	public static final String ID = "own_art";
	/** The instance {@code CiptaanArts} registers. */
	public static final OwnArt STUB = new OwnArt();

	public static final double PULSE_FACTOR = 1.5D;
	public static final double PURE_PULSE = 2.0D;
	public static final double RANGE = 64.0D;
	public static final double BEAM_LENGTH = 64.0D;
	public static final double BEAM_HALF_WIDTH = 1.5D;
	public static final double NOVA_RADIUS = 12.0D;
	public static final double NOVA_RADIUS_CAP = 20.0D;
	public static final double RAIN_RADIUS = 16.0D;
	public static final int RAIN_IMPACTS = 18;
	public static final int RAIN_TICKS = 60;
	public static final double FRONT_LENGTH = 64.0D;
	public static final double FRONT_WIDTH = 16.0D;
	public static final double FRONT_HEIGHT = 8.0D;
	public static final double FRONT_SPEED = 4.0D;
	public static final double ZONE_RADIUS = 14.0D;
	public static final int ZONE_PULSES = 10;
	public static final int ZONE_EVERY = 16;
	public static final int SEAL_ROOT_TICKS = 60;
	public static final double BURST_RADIUS = 8.0D;
	public static final double BURST_RADIUS_CAP = 12.0D;

	private static final String KEY = "kultivator.batin.art.";

	/** The EXTRA phase of each form's main hit (CiptaanArts draws them): cut, burst, rain, wave, domain, seal. */
	public static final int PHASE_CUT = 2;
	public static final int PHASE_BURST = 3;
	public static final int PHASE_RAIN = 4;
	public static final int PHASE_WAVE = 5;
	public static final int PHASE_DOMAIN = 6;
	public static final int PHASE_SEAL = 7;

	private OwnArt() {
	}

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable(KEY + "player");
		}
		Creation.Art art = Creation.art(player, CiptaanArts.slotOf(ctx.spec().id()));
		if (art == null) {
			return Component.translatable("kultivator.spine9.own_art.empty");
		}
		if (ctx.options().forceVisual()) {
			return null;
		}
		if (art.form() == Creation.Form.SEGEL && (ctx.target() == null || !ctx.target().isAlive())) {
			return Component.translatable(KEY + "need_target");
		}
		if (ctx.hostiles(ctx.caster().position(), RANGE, false).isEmpty()) {
			return Component.translatable(KEY + "no_target");
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		int slot = CiptaanArts.slotOf(ctx.spec().id());
		Creation.Art art = Creation.art(player, slot);
		if (art == null) {
			return false;
		}
		LivingEntity target = ctx.target();
		if (art.form() == Creation.Form.SEGEL && (target == null || !target.isAlive())) {
			return false;
		}
		if (!art.pure()) {
			// a two-Dao art: Dao A's layers with Dao B's rim on every event (FLAG_TWO_DAO)
			ctx.twoDao(art.b().color());
		}
		// The runner applies the run's own factor (echo, replay) inside the choke point.
		double scale = art.q() * (art.pure() ? Creation.PURE_BONUS : 1.0D);
		Vec3 eye = ctx.eye();
		Vec3 point = impactPoint(ctx, art.form(), target);
		ctx.markEnd(point);
		Payload.Hit main = damage(art.form().coef() * scale);
		switch (art.form()) {
			// each form has its own phase (DESIGN 27.5: EXTRA2 cut ... EXTRA7 seal) and so its own layers and pose
			case TEBASAN -> ctx.run(at(0).shape(beam().length(BEAM_LENGTH).halfWidth(BEAM_HALF_WIDTH).pierce().ignoreBlocks()).hit(main).phase(PHASE_CUT));
			case LEDAKAN -> ctx.run(at(0).shape(nova().radius(NOVA_RADIUS, NOVA_RADIUS_CAP).at(Anchor.AIM)).hit(main).phase(PHASE_BURST));
			case HUJAN -> ctx.run(at(0).shape(rain(Anchor.AIM, RANGE).radius(RAIN_RADIUS).impacts(RAIN_IMPACTS).over(RAIN_TICKS).impactRadius(2.5D)
					.targeted(0.7D)).hit(damage(art.form().coef() / RAIN_IMPACTS * scale)).phase(PHASE_RAIN));
			case GELOMBANG -> ctx.run(at(0).shape(front().length(FRONT_LENGTH).width(FRONT_WIDTH).height(FRONT_HEIGHT).speed(FRONT_SPEED).ignoreBlocks())
					.hit(main).phase(PHASE_WAVE));
			case WILAYAH -> ctx.run(at(0).shape(zone(Anchor.AIM).range(RANGE).radius(ZONE_RADIUS)).hit(damage(art.form().coef() / ZONE_PULSES * scale))
					.repeat(ZONE_PULSES, ZONE_EVERY).phase(PHASE_DOMAIN));
			case SEGEL -> {
				ctx.run(at(0).shape(target().range(RANGE)).hit(bind(Payload.BindKind.ROOT, SEAL_ROOT_TICKS)).phase(PHASE_SEAL));
				ctx.schedule(SEAL_ROOT_TICKS, () -> ctx.run(at(0).shape(target().range(RANGE).noLos()).hit(main).phase(PHASE_SEAL)));
			}
		}
		burst(ctx, art, art.form() == Creation.Form.SEGEL ? Anchor.TARGET : Anchor.AIM);
		// Mode Kehancuran (only with the existing switch): the runner carves the spec's bowl at the struck points itself.
		int packed = art.form().ordinal() | (art.a().ordinal() + 1) << 4 | (art.b().ordinal() + 1) << 8;
		float size = switch (art.form()) {
			case TEBASAN -> (float) BEAM_LENGTH;
			case LEDAKAN -> (float) NOVA_RADIUS;
			case HUJAN -> (float) RAIN_RADIUS;
			case GELOMBANG -> (float) FRONT_LENGTH;
			case WILAYAH -> (float) ZONE_RADIUS;
			case SEGEL -> 3.0F;
		};
		Fx.send(ctx.level(), point, eye, FxType.BATIN, BatinFxIds.OWN_ART, packed, size);
		if (!ctx.echo() && Creation.markCast(player, slot)) {
			EpicHooks.extra(ctx.level(), EpicIds.EXTRA_OWN_ART_BORN, player, player, point, slot, art.form().ordinal());
		}
		Msg.actionBar(player, Component.translatable(KEY + "cast", Codex.nameOf(player, ctx.spec()), Math.round(art.q() * 100.0D))
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Where the form lands: the beam's and the wave's far end, the aim for areas, the sealed target. */
	private static Vec3 impactPoint(CastContext ctx, Creation.Form form, @Nullable LivingEntity target) {
		Vec3 eye = ctx.eye();
		Vec3 look = ctx.look();
		return switch (form) {
			case TEBASAN -> eye.add(look.normalize().scale(BEAM_LENGTH));
			case GELOMBANG -> {
				Vec3 flat = new Vec3(look.x, 0.0D, look.z);
				yield ctx.caster().position().add(flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize().scale(FRONT_LENGTH));
			}
			case SEGEL -> target == null ? ctx.aim() : target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
			default -> {
				Vec3 offset = ctx.aim().subtract(eye);
				double length = offset.length();
				yield length > RANGE ? eye.add(offset.scale(RANGE / length)) : ctx.aim();
			}
		};
	}

	/** The Daos' domain pulses and the pair row, in one burst at {@code anchor}. */
	private static void burst(CastContext ctx, Creation.Art art, Anchor anchor) {
		double factor = 1.0D;
		List<MysteryFusion.Pulse> pulses = new ArrayList<>();
		if (art.pure()) {
			pulses.add(MysteryFusion.pulse(art.a(), PURE_PULSE * factor, ctx.tier()));
		} else {
			pulses.add(MysteryFusion.pulse(art.a(), PULSE_FACTOR * factor, ctx.tier()));
			pulses.add(MysteryFusion.pulse(art.b(), PULSE_FACTOR * factor, ctx.tier()));
		}
		MysteryFusion.Pair pair = art.pure() ? MysteryFusion.Pair.OTHER : MysteryFusion.Pair.of(art.a(), art.b());
		double coef = 0.0D;
		double drain = 0.0D;
		int bolts = 0;
		List<Payload.Hit> hits = new ArrayList<>();
		List<Payload.Boon> self = new ArrayList<>();
		List<Payload.Boon> ally = new ArrayList<>();
		for (MysteryFusion.Pulse pulse : pulses) {
			coef += pulse.damage();
			drain += pulse.drainDamage();
			hits.addAll(pulse.hits());
			self.addAll(pulse.self());
			ally.addAll(pulse.ally());
			bolts += pulse.bolts();
		}
		switch (pair) {
			case FIRE_ICE -> {
				coef *= MysteryFusion.FIRE_ICE_FACTOR;
				hits.add(eff(Payload.Eff.SLOWNESS, 3, 60));
			}
			case BODY_SPACE -> {
				hits.add(pull(0.6D).toward(Payload.Center.ANCHOR));
				hits.add(dot(0.3D * factor, 3, Payload.DotKind.QI));
			}
			case LIFE_SHADOW -> drain += MysteryFusion.PAIR_DRAIN * Math.max(coef, 0.1D);
			case ICE_SPACE -> hits.add(bind(Payload.BindKind.ROOT, 80));
			case FIRE_LIGHTNING -> {
				hits.add(ignite(4.0D));
				hits.add(bind(Payload.BindKind.STUN, 20));
			}
			default -> {
			}
		}
		List<Payload.Hit> burstHits = new ArrayList<>();
		if (coef > 0.0D) {
			burstHits.add(damage(coef));
			if (drain > 0.0D) {
				burstHits.add(drain(Math.min(1.0D, drain / coef)));
			}
		}
		burstHits.addAll(hits);
		Shape.All area = all().at(anchor).range(RANGE).radius(BURST_RADIUS, BURST_RADIUS_CAP).max(16);
		if (!burstHits.isEmpty() || !self.isEmpty() || !ally.isEmpty()) {
			Step step = at(0).shape(area).phase(1);
			if (!burstHits.isEmpty()) {
				step = step.hit(burstHits.toArray(new Payload.Hit[0]));
			}
			if (!self.isEmpty()) {
				step = step.self(self.toArray(new Payload.Boon[0]));
			}
			if (!ally.isEmpty()) {
				step = step.ally(ally.toArray(new Payload.Boon[0]));
			}
			ctx.run(step);
		}
		if (bolts > 0) {
			ctx.run(at(0).shape(all().at(anchor).range(RANGE).radius(BURST_RADIUS, BURST_RADIUS_CAP).max(bolts).order(Shape.All.Order.RANDOM))
					.hit(damage(MysteryFusion.BOLT_COEF * PULSE_FACTOR * factor)).phase(1));
		}
		switch (pair) {
			case SWORD_LIGHTNING -> ctx.run(at(0).shape(chain().from(Anchor.END).jumps(4)).hit(damage(1.0D * factor)).phase(1));
			case SWORD_SPACE -> ctx.run(at(0).shape(front().length(16).width(1)).hit(damage(1.0D * factor)).phase(1));
			case BODY_LIFE -> ctx.run(at(0).ally(MysteryFusion.PAIR_HEAL_RADIUS, heal(MysteryFusion.PAIR_HEAL)).phase(1));
			default -> {
			}
		}
	}

	/** The resolved numbers of a creation (for {@code debug ciptaan}). */
	static List<Component> describe(Creation.Art art) {
		List<Component> lines = new ArrayList<>();
		double scale = art.q() * (art.pure() ? Creation.PURE_BONUS : 1.0D);
		lines.add(Component.translatable(KEY + "debug.head", art.slot() + 1, art.name(), art.form().displayName(), Math.round(art.q() * 100.0D),
				art.pure() ? Component.translatable(KEY + "debug.pure") : Component.literal("")));
		lines.add(Component.translatable(KEY + "debug.coef", String.format(java.util.Locale.ROOT, "%.1f", art.form().coef()),
				String.format(java.util.Locale.ROOT, "%.2f", art.form().coef() * scale)));
		Dao a = art.a();
		Dao b = art.b();
		MysteryFusion.Pair pair = art.pure() ? MysteryFusion.Pair.OTHER : MysteryFusion.Pair.of(a, b);
		lines.add(Component.translatable(KEY + "debug.daos", a.displayName(), b.displayName(), pair.name().toLowerCase(java.util.Locale.ROOT),
				String.format(java.util.Locale.ROOT, "%.1f", art.pure() ? PURE_PULSE : PULSE_FACTOR)));
		lines.add(Component.translatable(KEY + "debug.caps"));
		return lines;
	}
}

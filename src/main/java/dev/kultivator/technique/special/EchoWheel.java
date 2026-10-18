package dev.kultivator.technique.special;

import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code echo_wheel}, the special of {@code six_dao_reincarnation} (DESIGN 22.2.7, 22.9.9; Perfect World: the Six Dao
 * Reincarnations Heavenly Art merges Shi Hao's six precious techniques into one display). Replays the last
 * {@link #ARTS} distinct arts the caster cast in the past {@link #WINDOW_SECONDS} s (never a PAMUNGKAS, itself, a special,
 * a toggle or a disciple-only spec; echoes are not in the recent list) at the current aim, one every {@link #SPACING} ticks, at
 * ×{@link #FACTOR_BASE} (+{@link #FACTOR_PER_TIER} per tier above Immortal Ascension Tahap 7, at most ×{@link #FACTOR_CAP}),
 * free; then a NOVA of {@link #NOVA_COEF} × P. {@code CastContext.replay} keeps one boss cap, no mastery and no carving.
 * <p>
 * Contract with the runner: the spec's own timeline holds only an empty keep-alive step at {@link #KEEP_ALIVE_TICKS}.
 * Mastery level 3 replays one more art (the spec's EXTRA_COUNT bonus); level 4 makes the final NOVA ×{@link #PEAK_NOVA}
 * (bonus {@code final_nova}). Both are off in an arena (22.14.5).
 */
public final class EchoWheel implements SpecialTechnique {

	public static final String ID = "echo_wheel";
	public static final int WINDOW_SECONDS = 120;
	public static final int ARTS = 6;
	public static final int SPACING = 6;
	public static final double FACTOR_BASE = 0.6D;
	public static final double FACTOR_PER_TIER = 0.05D;
	public static final double FACTOR_CAP = 0.8D;
	public static final double NOVA_COEF = 4.0D;
	public static final double NOVA_RADIUS = 10.0D;
	public static final double NOVA_RADIUS_CAP = 20.0D;
	public static final double PEAK_NOVA = 1.25D;
	public static final double TARGET_RANGE = 32.0D;
	/** The spokes of the wheel (EXTRA0-5); the final NOVA uses EXTRA6. */
	public static final int SPOKES = 6;
	public static final int KEEP_ALIVE_TICKS = 60;
	public static final String PEAK_KEY = "final_nova";

	private static final String KEY = "kultivator.technique.six_dao_reincarnation.why.";

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		if (!ctx.options().forceVisual() && ctx.hostiles(ctx.caster().position(), TARGET_RANGE, false).isEmpty()) {
			return Component.translatable(KEY + "no_target");
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		List<TechniqueSpec> arts = pick(ctx);
		double factor = ctx.arena() ? FACTOR_BASE : ctx.lin(FACTOR_BASE, FACTOR_PER_TIER, FACTOR_CAP);
		for (int i = 0; i < arts.size(); i++) {
			TechniqueSpec art = arts.get(i);
			int index = i;
			Runnable replay = () -> replay(ctx, art, factor, index);
			if (i == 0) {
				replay.run();
			} else {
				ctx.schedule(i * SPACING, replay);
			}
		}
		Runnable closing = () -> finale(ctx);
		if (arts.isEmpty()) {
			closing.run();
		} else {
			ctx.schedule(arts.size() * SPACING, closing);
		}
		return true;
	}

	/** The arts to replay, newest first: distinct recent casts that are not ultimates, specials, disciple-only or this spec. */
	private static List<TechniqueSpec> pick(CastContext ctx) {
		int count = ARTS;
		if (ctx.mastery() >= 3 && !ctx.arena()) {
			for (Bonus bonus : ctx.spec().perfection()) {
				if (bonus.kind() == Bonus.Kind.EXTRA_COUNT) {
					count += Math.max(0, bonus.count());
				}
			}
		}
		List<TechniqueSpec> arts = new ArrayList<>();
		for (String id : ctx.recentCasts(WINDOW_SECONDS)) {
			if (arts.size() >= count) {
				break;
			}
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null || spec == ctx.spec() || spec.id().equals(ctx.spec().id()) || spec.pamungkas() || spec.special() != null
					|| spec.toggle() != null || spec.has(TechniqueSpec.Flag.DISCIPLE_ONLY) || arts.contains(spec)) {
				continue;
			}
			arts.add(spec);
		}
		return arts;
	}

	private static void replay(CastContext ctx, TechniqueSpec art, double factor, int index) {
		if (ctx.ended()) {
			return;
		}
		Vec3 at = ctx.caster().position();
		Phase spoke = Phase.extra(index % SPOKES);
		ctx.fx(spoke, at, ctx.aim(), (float) NOVA_RADIUS, 0.0F, art.netId(), SPACING);
		ctx.sound(spoke, at);
		ctx.replay(art, factor);
	}

	/** The closing NOVA (EXTRA6). */
	private static void finale(CastContext ctx) {
		if (ctx.ended()) {
			return;
		}
		double coef = NOVA_COEF;
		if (ctx.mastery() >= 4 && !ctx.arena()) {
			for (Bonus bonus : ctx.spec().peak()) {
				if (bonus.kind() == Bonus.Kind.SPECIAL && PEAK_KEY.equals(bonus.key())) {
					coef *= PEAK_NOVA;
				}
			}
		}
		ctx.run(at(0).shape(nova().radius(NOVA_RADIUS, NOVA_RADIUS_CAP)).hit(damage(coef)).phase(6));
	}
}

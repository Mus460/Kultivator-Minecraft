package dev.kultivator.technique.spec.run;

import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.technique.UniversalTechniques;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bridge from the registry to the 23 legacy techniques (DESIGN 22.2.4, 22.10): their specs have no timeline, so a
 * cast runs the old hand-written code with this {@link CastContext}, which supplies P, the scaled reads, the mastery
 * bonuses, the choke point and the Kehancuran anchors. Disciples' copies keep their own legacy code
 * ({@code DiscipleTechniques}), now reading P_disciple and the disciple's {@code DiscipleCaster}.
 */
public final class LegacyArt {

	/** Runs the legacy technique of {@code ctx}'s spec; true when it really happened (false spends nothing). */
	static boolean cast(Caster ctx) {
		TechniqueSpec spec = ctx.spec;
		Technique technique = spec.legacy();
		if (technique == null) {
			return false;
		}
		if (ctx.disciple() != null) {
			return DiscipleTechniques.castLegacy(ctx, technique);
		}
		return technique.dao() == null ? UniversalTechniques.cast(ctx) : DaoTechniques.cast(ctx);
	}

	/** True while a recast of this legacy spec would be its stage 2 (the Shadow Step window is open). */
	static boolean recastable(ServerPlayer player, TechniqueSpec spec) {
		return spec.legacy() == Technique.SHADOW_STEP && DaoTechniques.shadowWindow(player);
	}

	/**
	 * A recast of a legacy spec while its stage-2 window is open (Shadow Step from Deity Transformation): consumed here,
	 * skipping the cooldown. The caller already checked deviation, breakthrough and the active-cast limit. A step that
	 * finds no target is dropped without END or mastery. True when consumed.
	 */
	static boolean recast(ServerPlayer player, TechniqueSpec spec, CastOptions options) {
		if (!recastable(player, spec)) {
			return false;
		}
		PlayerCaster ctx = new PlayerCaster(player, spec, options);
		TechniqueRunner.register(ctx);
		boolean done;
		try {
			done = DaoTechniques.shadowRecast(ctx, player);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: Shadow Step recast failed", exception);
			done = false;
		}
		if (!done) {
			TechniqueRunner.discard(ctx);
			return true;
		}
		TechniqueRunner.start(ctx);
		return true;
	}

	private LegacyArt() {
	}
}

package dev.kultivator.util;

import dev.kultivator.Kultivator;
import dev.kultivator.technique.Techniques;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The damage-modification facade (DESIGN 22.14.3). Fabric has no damage-modification event, so
 * {@code LivingEntityDamageMixin} calls {@link #modify} on the amount in {@code actuallyHurt} after armour and enchantment
 * protection, just before absorption. Order: unblockable damage passes untouched, then the DR modifiers (capped by their
 * owner), Perisai Qi, the COUNTER stores. P6c-mesin registers them; zero cost while none is registered.
 */
public final class DamageHooks {

	/** Modifier stages, run in this order. */
	public enum Stage { DR, SHIELD, COUNTER }

	@FunctionalInterface
	public interface Modifier {
		/** The new amount (≥ 0) for {@code entity} taking {@code amount} of {@code source}. */
		float modify(LivingEntity entity, DamageSource source, float amount);
	}

	/** {@code #kultivator:unblockable}: tribulation, qi_deviation, out_of_world, generic_kill. Never reduced or absorbed. */
	public static final TagKey<DamageType> UNBLOCKABLE = TagKey.create(Registries.DAMAGE_TYPE, Kultivator.id("unblockable"));

	private static final Map<Stage, List<Modifier>> MODIFIERS = new EnumMap<>(Stage.class);
	private static volatile boolean any;

	public static synchronized void register(Stage stage, Modifier modifier) {
		MODIFIERS.computeIfAbsent(stage, key -> new ArrayList<>()).add(modifier);
		any = true;
	}

	/** Called by the mixin for every living entity hit on the server. */
	public static float modify(LivingEntity entity, DamageSource source, float amount) {
		if (!any || !(amount > 0.0F) || entity.level().isClientSide() || source.is(UNBLOCKABLE)) {
			return amount;
		}
		float result = amount;
		for (Stage stage : Stage.values()) {
			List<Modifier> list = MODIFIERS.get(stage);
			if (list == null) {
				continue;
			}
			for (Modifier modifier : list) {
				try {
					float next = modifier.modify(entity, source, result);
					result = Float.isFinite(next) ? Math.max(0.0F, next) : result;
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kultivator damage hook failed", exception);
				}
				if (result <= 0.0F) {
					return 0.0F;
				}
			}
		}
		return result;
	}

	/** {@code kultivator:technique} or {@code kultivator:technique_pierce}. */
	public static boolean isTechnique(DamageSource source) {
		return source.is(Techniques.DAMAGE) || source.is(Techniques.DAMAGE_PIERCE);
	}

	private DamageHooks() {
	}
}

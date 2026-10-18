package dev.kultivator.util;

import dev.kultivator.Kultivator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.Nullable;

/**
 * The damage of phase 7c sect formations (DESIGN 24.2.10, 24.13 item 8): data damage type {@code kultivator:formation}
 * ({@code data/kultivator/damage_type/formation.json}), which bypasses nothing and has no attacker. {@link KillCredit}
 * gives nobody credit for it, so a formation kill pays no cultivation XP, beast cores, stones, renown, feats or mastery.
 */
public final class FormationDamage {

	/** {@code kultivator:formation}. */
	public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("formation"));

	/** A sourceless formation damage source of {@code level}. */
	public static DamageSource source(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TYPE));
	}

	/** True for formation damage. */
	public static boolean is(@Nullable DamageSource source) {
		return source != null && source.is(TYPE);
	}

	private FormationDamage() {
	}
}

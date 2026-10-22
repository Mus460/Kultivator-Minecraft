package dev.kultivator.duel.clash;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.elite.EliteData;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.purba.HeavenEnvoyEntity;
import dev.kultivator.purba.PrimordialBeastEntity;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Posture of mobs in the general layer (DESIGN 35.3.13, {@code MobGuard}): only elites ({@link EliteData}), rogue
 * cultivators, the primordial beasts and Heaven's envoys, rival disciple bodies and profiled NPCs have a bar: max
 * {@code 20 + 0.6 × max health} (at most 400; elites ×1.5). Bosses ({@code #kultivator:technique_bosses},
 * the Wither, the Ender Dragon, the Warden) show it, but a broken posture only staggers them for 10 ticks and a Deathblow
 * is ×1.5 capped at 15% of their max health; it never skips a boss phase and never kills a boss outright.
 */
public final class MobGuard {

	public static final float BASE = 20.0F;
	public static final float PER_HEALTH = 0.6F;
	public static final float CAP = 400.0F;
	public static final float ELITE_FACTOR = 1.5F;
	/** A Deathblow against an ordinary posture mob (rogues, elites) multiplies the weapon damage. */
	public static final float DEATHBLOW_MOB = 2.5F;
	/** Against a boss: this multiplier, at most this share of max health. */
	public static final float DEATHBLOW_BOSS = 1.5F;
	public static final float DEATHBLOW_BOSS_CAP = 0.15F;

	/** True when {@code entity} (not a player) gets a posture bar in the general layer. */
	public static boolean hasPosture(LivingEntity entity) {
		if (entity == null || !(entity instanceof Mob)) {
			return false;
		}
		if (boss(entity)) {
			return true;
		}
		if (entity instanceof RogueCultivatorEntity || entity instanceof PrimordialBeastEntity || entity instanceof HeavenEnvoyEntity) {
			return true;
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.role() == DiscipleEntity.Role.RIVAL || disciple.role() == DiscipleEntity.Role.PHANTOM;
		}
		return EliteData.of(entity) != null || DuelistRegistry.profile(entity) != null;
	}

	/** Bosses: the technique boss tag (Warden, Wither, Ender Dragon, Elder Guardian) and the primordial beasts. */
	public static boolean boss(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		EntityType<?> type = entity.getType();
		return TechniquePower.isBoss(entity) || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON
				|| type == EntityTypes.WARDEN || entity instanceof PrimordialBeastEntity;
	}

	/** The max posture of a posture mob. */
	public static float maxPosture(LivingEntity entity) {
		float max = Math.min(CAP, BASE + PER_HEALTH * entity.getMaxHealth());
		EliteData elite = EliteData.of(entity);
		if (elite != null && elite.tier() > 0) {
			max = Math.min(CAP, max * ELITE_FACTOR);
		}
		return Math.max(BASE, max);
	}

	/** The realm ordinal of a mob for the formulas (elites carry one; others 0). */
	public static int realm(LivingEntity entity) {
		EliteData elite = EliteData.of(entity);
		if (elite != null) {
			return Math.max(0, elite.realm());
		}
		if (entity instanceof RogueCultivatorEntity rogue) {
			return rogue.realm();
		}
		return 0;
	}

	/** How long a broken posture holds this entity. */
	public static int staggerTicks(LivingEntity entity) {
		return boss(entity) ? ClashTiming.BOSS_STAGGER_TICKS : ClashTiming.STAGGER_TICKS;
	}

	private MobGuard() {
	}
}

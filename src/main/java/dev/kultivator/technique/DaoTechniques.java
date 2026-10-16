package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** STUB — owned by P2-techniques-b. The 16 Dao techniques and the Dao flavour of the Dao Domain (DESIGN 8, 15). */
public final class DaoTechniques {

	public static void init() {
	}

	/** Performs {@code technique} (qi and cooldown are handled by Techniques). True when it really happened. */
	public static boolean cast(ServerPlayer player, CultivationData data, Technique technique) {
		return false;
	}

	/** Once per second while the Dao Domain is up: the Dao's effect on the monsters inside (and on the caster). */
	public static void domainPulse(ServerPlayer player, CultivationData data, Dao dao, List<LivingEntity> insideHostiles) {
	}

	/** Every server tick for every player: lasting effects of Dao techniques (iron body, prisons...). */
	public static void tick(ServerPlayer player, CultivationData data) {
	}

	private DaoTechniques() {
	}
}

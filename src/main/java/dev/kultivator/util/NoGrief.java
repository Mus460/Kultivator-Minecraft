package dev.kultivator.util;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Entities the mod brings into the world (Dao Protectors, heart demons...) must never change a block. Vanilla lets
 * mobs trample farmland and crush turtle eggs; the mixins in {@code dev.kultivator.mixin} stop that for any entity
 * carrying {@link #TAG} (or the Dao Protector tag).
 */
public final class NoGrief {

	/** Add this entity tag to every entity the mod spawns. */
	public static final String TAG = "kultivator.no_grief";
	private static final String DAO_PROTECTOR_TAG = "kultivator.dao_protector";

	public static boolean isProtected(@Nullable Entity entity) {
		return entity != null && (entity.entityTags().contains(TAG) || entity.entityTags().contains(DAO_PROTECTOR_TAG));
	}

	private NoGrief() {
	}
}

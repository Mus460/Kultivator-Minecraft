package dev.kultivator.elite;

import dev.kultivator.data.Element;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The natural element of a mob (DESIGN 19.3.12; the mob elements are game design). An elite's own element comes first;
 * bosses, players and every mob not listed are neutral (null). Common code, pure lookup.
 */
public final class MobElements {

	private static final Map<EntityType<?>, Element> BY_TYPE = new HashMap<>();

	static {
		put(Element.EARTH, EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SLIME, EntityTypes.SILVERFISH,
				EntityTypes.RAVAGER);
		put(Element.WATER, EntityTypes.DROWNED, EntityTypes.GUARDIAN);
		put(Element.METAL, EntityTypes.SKELETON, EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.PIGLIN_BRUTE);
		put(Element.ICE, EntityTypes.STRAY);
		put(Element.WOOD, EntityTypes.BOGGED, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.WITCH, EntityTypes.CREAKING);
		put(Element.FIRE, EntityTypes.WITHER_SKELETON, EntityTypes.BLAZE, EntityTypes.MAGMA_CUBE, EntityTypes.GHAST, EntityTypes.HOGLIN,
				EntityTypes.ZOGLIN);
		put(Element.LIGHTNING, EntityTypes.CREEPER);
		put(Element.SPACE, EntityTypes.ENDERMAN, EntityTypes.ENDERMITE, EntityTypes.SHULKER);
		put(Element.WIND, EntityTypes.PHANTOM, EntityTypes.VEX, EntityTypes.EVOKER, EntityTypes.BREEZE);
	}

	private static void put(Element element, EntityType<?>... types) {
		for (EntityType<?> type : types) {
			BY_TYPE.put(type, element);
		}
	}

	/** The element of {@code entity} (an elite's own element first); null for bosses, players and unknown mobs. */
	public static @Nullable Element of(Entity entity) {
		if (entity == null) {
			return null;
		}
		EliteData elite = EliteData.of(entity);
		if (elite != null) {
			return elite.elementValue();
		}
		return BY_TYPE.get(entity.getType());
	}

	/** The element a mob of {@code type} has by nature (never an elite's rolled one); null when neutral. */
	public static @Nullable Element natural(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	private MobElements() {
	}
}

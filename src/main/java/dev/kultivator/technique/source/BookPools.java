package dev.kultivator.technique.source;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.dsl.Route;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enchanted-book pools (DESIGN 22.8.2): each enchantment maps to the specs whose BOOK route names it, in grade order; the
 * book teaches the first learnable one, otherwise it gives insight. The family table says which Dao an enchantment's
 * book belongs to (its insight Dao). Universal specs whose pool enchantment belongs to another family (unbreaking,
 * piercing, soul_speed, lure, breach) sit in that enchantment's pool too, so every family book can carry them.
 * Pools are built lazily from the frozen registry, on both sides.
 */
public final class BookPools {

	/** The families of 22.8.2. */
	public enum Family {
		SWORD(Dao.SWORD, "sharpness", "sweeping_edge", "breach"),
		FIRE(Dao.FIRE, "fire_aspect", "flame", "fire_protection"),
		ICE(Dao.ICE, "frost_walker", "depth_strider", "respiration"),
		LIGHTNING(Dao.LIGHTNING, "channeling", "riptide", "quick_charge"),
		BODY(Dao.BODY, "protection", "thorns", "blast_protection", "unbreaking", "density"),
		LIFE(Dao.LIFE, "mending", "luck_of_the_sea", "lure"),
		SHADOW(Dao.SHADOW, "swift_sneak", "feather_falling", "wind_burst", "soul_speed", "punch"),
		SPACE(Dao.SPACE, "infinity", "loyalty", "silk_touch", "piercing"),
		UNIVERSAL(null, "smite", "power", "efficiency", "looting", "fortune", "bane_of_arthropods", "impaling", "aqua_affinity",
				"projectile_protection", "knockback", "multishot", "lunge"),
		FORBIDDEN(null, "binding_curse", "vanishing_curse");

		private final @Nullable Dao dao;
		private final List<String> enchantments;

		Family(@Nullable Dao dao, String... enchantments) {
			this.dao = dao;
			List<String> ids = new ArrayList<>();
			for (String enchantment : enchantments) {
				ids.add("minecraft:" + enchantment);
			}
			this.enchantments = List.copyOf(ids);
		}

		/** The Dao whose comprehension the family's books deepen (null: universal or forbidden). */
		public @Nullable Dao dao() {
			return dao;
		}

		public List<String> enchantments() {
			return enchantments;
		}
	}

	private static volatile @Nullable Map<String, List<TechniqueSpec>> pools;

	/** The family of an enchantment id ({@code minecraft:sharpness}), or null when it has none. */
	public static @Nullable Family family(String enchantmentId) {
		for (Family family : Family.values()) {
			if (family.enchantments.contains(enchantmentId)) {
				return family;
			}
		}
		return null;
	}

	/** The id of an enchantment holder ({@code minecraft:sharpness}), "" when unregistered. */
	public static String id(Holder<Enchantment> enchantment) {
		return enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("");
	}

	/** The pool of one enchantment, grade order (empty when none). */
	public static List<TechniqueSpec> pool(Holder<Enchantment> enchantment) {
		return pool(id(enchantment));
	}

	/** The pool of one enchantment id, grade order (empty when none). */
	public static List<TechniqueSpec> pool(String enchantmentId) {
		return pools().getOrDefault(enchantmentId, List.of());
	}

	/** Every spec the stored enchantments of a book can teach, grade order, without duplicates. */
	public static List<TechniqueSpec> poolOf(ItemEnchantments stored) {
		Set<TechniqueSpec> all = new LinkedHashSet<>();
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			all.addAll(pool(enchantment));
		}
		List<TechniqueSpec> list = new ArrayList<>(all);
		list.sort(LearnRules.GRADE_ORDER);
		return list;
	}

	/** The families of the stored enchantments of a book (empty when none maps). */
	public static Set<Family> familiesOf(ItemEnchantments stored) {
		EnumSet<Family> set = EnumSet.noneOf(Family.class);
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			Family family = family(id(enchantment));
			if (family != null) {
				set.add(family);
			}
		}
		return set;
	}

	/**
	 * True when a book with these enchantments is a technique book: one of them has a pool. A book whose enchantments
	 * pool nothing stays a plain book (cultivation XP), even when its family is known.
	 */
	public static boolean isTechniqueBook(ItemEnchantments stored) {
		return !poolOf(stored).isEmpty();
	}

	private static Map<String, List<TechniqueSpec>> pools() {
		Map<String, List<TechniqueSpec>> built = pools;
		if (built != null) {
			return built;
		}
		Map<String, List<TechniqueSpec>> map = new HashMap<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (!spec.playerVisible()) {
				continue;
			}
			for (Route route : spec.routes()) {
				if (route.kind() != Route.Kind.BOOK) {
					continue;
				}
				for (String key : route.keys()) {
					String id = key.indexOf(':') < 0 ? "minecraft:" + key : key;
					List<TechniqueSpec> list = map.computeIfAbsent(id, unused -> new ArrayList<>());
					if (!list.contains(spec)) {
						list.add(spec);
					}
				}
			}
		}
		Map<String, List<TechniqueSpec>> frozen = new HashMap<>();
		map.forEach((id, list) -> {
			list.sort(LearnRules.GRADE_ORDER);
			frozen.put(id, List.copyOf(list));
		});
		Map<String, List<TechniqueSpec>> result = Collections.unmodifiableMap(frozen);
		if (TechniqueRegistry.frozen()) {
			pools = result;
		}
		return result;
	}

	private BookPools() {
	}
}

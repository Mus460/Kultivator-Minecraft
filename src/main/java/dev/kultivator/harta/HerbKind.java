package dev.kultivator.harta;

import dev.kultivator.data.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The eight spirit herbs that grow hidden among ordinary plants (DESIGN 19.3.4). {@code p} is the infusion chance per
 * plant and day before the modifiers, {@code weight} the share in {@link SpiritHerbs#randomFor} (game design), the
 * elements the herb suits (elite drops, expeditions). The sources are in {@code item.kultivator.<id>.source}.
 */
public enum HerbKind {
	SPIRIT_GRASS("spirit_grass", 0.015D, 0x8CFFB0, 6, Element.WOOD, Element.WIND),
	NINECURL_GINSENG("ninecurl_ginseng", 0.01D, 0xE0B070, 4, Element.EARTH, Element.WOOD),
	EIGHT_ANGLED_ICE_GRASS("eight_angled_ice_grass", 0.04D, 0xA8F0FF, 3, Element.ICE, Element.WATER),
	PURPLE_FLAME_FLOWER("purple_flame_flower", 0.03D, 0xC060FF, 3, Element.FIRE),
	ILLUSORY_SPIRIT_HERB("illusory_spirit_herb", 0.02D, 0xD8C8FF, 2, Element.SPACE, Element.WIND),
	GOLD_LIGHTNING_BAMBOO("gold_lightning_bamboo", 0.05D, 0xFFD54A, 2, Element.LIGHTNING, Element.METAL),
	GINSENG_FRUIT("ginseng_fruit", 0.02D, 0xFFE8C0, 1, Element.WOOD, Element.EARTH),
	YELLOW_ESSENCE_MUSHROOM("yellow_essence_mushroom", 0.03D, 0xFFD040, 3, Element.EARTH, Element.METAL, Element.WATER);

	private static final HerbKind[] ALL = values();

	private final String id;
	private final double chance;
	private final int color;
	private final int weight;
	private final List<Element> elements;

	HerbKind(String id, double chance, int color, int weight, Element... elements) {
		this.id = id;
		this.chance = chance;
		this.color = color;
		this.weight = weight;
		this.elements = List.of(elements);
	}

	/** Item id path, e.g. {@code spirit_grass}. */
	public String id() {
		return id;
	}

	/** The base infusion chance p per plant and day. */
	public double chance() {
		return chance;
	}

	/** RGB of the herb (effects, Divine Sense, the recipe tablet). */
	public int color() {
		return color;
	}

	/** Weight in random herb rolls. */
	public int weight() {
		return weight;
	}

	/** The elements this herb suits. */
	public List<Element> elements() {
		return elements;
	}

	public boolean suits(@Nullable Element element) {
		return element == null || elements.contains(element);
	}

	/** Null when unknown. */
	public static @Nullable HerbKind byId(String id) {
		for (HerbKind kind : ALL) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return null;
	}
}

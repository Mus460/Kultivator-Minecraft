package dev.kultivator.data;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Elements of a spiritual root. The first five are the classic elements, the rest are rare mutant elements. */
public enum Element {
	METAL("metal", 0xE0E0E8, false),
	WOOD("wood", 0x5CCB4A, false),
	WATER("water", 0x3D8BFF, false),
	FIRE("fire", 0xFF5A2A, false),
	EARTH("earth", 0xC08A3E, false),
	LIGHTNING("lightning", 0xB57CFF, true),
	ICE("ice", 0x9FF3FF, true),
	WIND("wind", 0xB6FFB0, true),
	SPACE("space", 0x5A3FD0, true);

	private final String id;
	private final int color;
	private final boolean mutant;

	Element(String id, int color, boolean mutant) {
		this.id = id;
		this.color = color;
		this.mutant = mutant;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	public boolean mutant() {
		return mutant;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public Component displayName() {
		return Component.translatable("kultivator.element." + id);
	}

	/** Null when unknown. */
	public static Element byId(String id) {
		for (Element element : values()) {
			if (element.id.equals(id)) {
				return element;
			}
		}
		return null;
	}

	/** The elements in a bitmask, in enum order. */
	public static List<Element> fromMask(int mask) {
		List<Element> list = new ArrayList<>();
		for (Element element : values()) {
			if ((mask & element.bit()) != 0) {
				list.add(element);
			}
		}
		return list;
	}

	/** The colour used for a root's qi: its first element, or pale blue-white for no root. */
	public static int mainColor(int mask) {
		List<Element> elements = fromMask(mask);
		return elements.isEmpty() ? 0xCFE8FF : elements.get(0).color();
	}
}

package dev.kultivator.technique;

/**
 * The qi colour of the sword a cultivator flies on, shared by the server (sent as a small index inside technique
 * effects) and the client (flying sword trail from the synced {@code VisualState.swordItem}). Pure, no game state.
 */
public final class SwordColors {

	private static final String[] IDS = {"", "minecraft:netherite_sword", "minecraft:diamond_sword", "minecraft:iron_sword",
			"minecraft:golden_sword", "minecraft:copper_sword", "minecraft:stone_sword", "minecraft:wooden_sword"};
	/** Index 0 is any other sword (or none): pale qi blue. */
	private static final int[] COLORS = {0xCFE8FF, 0x9A7FB8, 0x6FF2E8, 0xE4E8F0, 0xFFD54A, 0xE8855A, 0xA8A8A8, 0xC09A5C};

	/** 0..7: the palette index of a sword item id ({@code "minecraft:iron_sword"}), 0 for unknown ones. */
	public static int index(String itemId) {
		if (itemId == null || itemId.isEmpty()) {
			return 0;
		}
		for (int i = 1; i < IDS.length; i++) {
			if (IDS[i].equals(itemId)) {
				return i;
			}
		}
		return 0;
	}

	/** RGB of a palette index (out of range: the default qi blue). */
	public static int color(int index) {
		return index < 0 || index >= COLORS.length ? COLORS[0] : COLORS[index];
	}

	public static int color(String itemId) {
		return color(index(itemId));
	}

	private SwordColors() {
	}
}

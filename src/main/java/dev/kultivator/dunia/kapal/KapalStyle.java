package dev.kultivator.dunia.kapal;

import java.util.Map;

/**
 * The hull liveries of the flying ships (detail pass of DESIGN 34.5.15): the same hull model drawn with another wood, tile
 * and trim, chosen at the helm ("Gaya Lambung"). A style only swaps block ids of the display list on the client (the
 * properties such as facing, half or axis stay), so every class has every style without new model files; nothing is
 * placed in the world. Some styles are earned: {@link #unlock()} names what the owner needs. Pure data, shared by both
 * sides (the client's mesh baker reads {@link #remap}).
 */
public enum KapalStyle {

	/** Kayu Hitam: the dark oak and slate the shipwrights build with (always open). */
	KAYU_HITAM("kayu_hitam", Unlock.NONE, 0xF3EAD2, 0xFFC66A, Map.of()),
	/** Giok Laut: warped wood and prismarine, a jade hull that glows faintly in moonlight (always open). */
	GIOK_LAUT("giok_laut", Unlock.NONE, 0xD8F4EA, 0x8CFFE0, Map.ofEntries(
			Map.entry("minecraft:dark_oak_planks", "minecraft:warped_planks"),
			Map.entry("minecraft:dark_oak_stairs", "minecraft:warped_stairs"),
			Map.entry("minecraft:dark_oak_slab", "minecraft:warped_slab"),
			Map.entry("minecraft:dark_oak_fence", "minecraft:warped_fence"),
			Map.entry("minecraft:dark_oak_trapdoor", "minecraft:warped_trapdoor"),
			Map.entry("minecraft:dark_oak_log", "minecraft:warped_stem"),
			Map.entry("minecraft:stripped_dark_oak_log", "minecraft:stripped_warped_stem"),
			Map.entry("minecraft:stripped_dark_oak_wood", "minecraft:stripped_warped_hyphae"),
			Map.entry("minecraft:deepslate_tiles", "minecraft:prismarine_bricks"),
			Map.entry("minecraft:deepslate_tile_stairs", "minecraft:prismarine_brick_stairs"),
			Map.entry("minecraft:deepslate_tile_slab", "minecraft:prismarine_brick_slab"),
			Map.entry("minecraft:stripped_crimson_stem", "minecraft:stripped_warped_stem"),
			Map.entry("minecraft:stripped_crimson_hyphae", "minecraft:stripped_warped_hyphae"),
			Map.entry("minecraft:lantern", "minecraft:soul_lantern"))),
	/** Merah Lak: red lacquer mangrove with nether-brick armour (the owner's sect hall at level 3 or more). */
	MERAH_LAK("merah_lak", Unlock.HALL_3, 0xF6DCC8, 0xFFB050, Map.ofEntries(
			Map.entry("minecraft:dark_oak_planks", "minecraft:mangrove_planks"),
			Map.entry("minecraft:dark_oak_stairs", "minecraft:mangrove_stairs"),
			Map.entry("minecraft:dark_oak_slab", "minecraft:mangrove_slab"),
			Map.entry("minecraft:dark_oak_fence", "minecraft:mangrove_fence"),
			Map.entry("minecraft:dark_oak_trapdoor", "minecraft:mangrove_trapdoor"),
			Map.entry("minecraft:dark_oak_log", "minecraft:mangrove_log"),
			Map.entry("minecraft:stripped_dark_oak_log", "minecraft:stripped_mangrove_log"),
			Map.entry("minecraft:stripped_dark_oak_wood", "minecraft:stripped_mangrove_wood"),
			Map.entry("minecraft:deepslate_tiles", "minecraft:red_nether_bricks"),
			Map.entry("minecraft:deepslate_tile_stairs", "minecraft:red_nether_brick_stairs"),
			Map.entry("minecraft:deepslate_tile_slab", "minecraft:red_nether_brick_slab"),
			Map.entry("minecraft:spruce_planks", "minecraft:dark_oak_planks"),
			Map.entry("minecraft:spruce_stairs", "minecraft:dark_oak_stairs"))),
	/** Awan Putih: birch and white quartz, a cloud ship (earned by driving off the sky pirates once). */
	AWAN_PUTIH("awan_putih", Unlock.PIRATES, 0xFFFFFF, 0xCFF0FF, Map.ofEntries(
			Map.entry("minecraft:dark_oak_planks", "minecraft:birch_planks"),
			Map.entry("minecraft:dark_oak_stairs", "minecraft:birch_stairs"),
			Map.entry("minecraft:dark_oak_slab", "minecraft:birch_slab"),
			Map.entry("minecraft:dark_oak_fence", "minecraft:birch_fence"),
			Map.entry("minecraft:dark_oak_trapdoor", "minecraft:birch_trapdoor"),
			Map.entry("minecraft:dark_oak_log", "minecraft:birch_log"),
			Map.entry("minecraft:stripped_dark_oak_log", "minecraft:stripped_birch_log"),
			Map.entry("minecraft:stripped_dark_oak_wood", "minecraft:stripped_birch_wood"),
			Map.entry("minecraft:deepslate_tiles", "minecraft:smooth_quartz"),
			Map.entry("minecraft:deepslate_tile_stairs", "minecraft:smooth_quartz_stairs"),
			Map.entry("minecraft:deepslate_tile_slab", "minecraft:smooth_quartz_slab"),
			Map.entry("minecraft:stripped_crimson_stem", "minecraft:stripped_cherry_log"),
			Map.entry("minecraft:stripped_crimson_hyphae", "minecraft:stripped_cherry_wood"),
			Map.entry("minecraft:mangrove_planks", "minecraft:cherry_planks"))),
	/** Bambu Emas: golden bamboo with gilded rails (earned by one voyage of more than 3 000 blocks). */
	BAMBU_EMAS("bambu_emas", Unlock.VOYAGE, 0xFFF4C8, 0xFFD27A, Map.ofEntries(
			Map.entry("minecraft:dark_oak_planks", "minecraft:bamboo_planks"),
			Map.entry("minecraft:dark_oak_stairs", "minecraft:bamboo_stairs"),
			Map.entry("minecraft:dark_oak_slab", "minecraft:bamboo_slab"),
			Map.entry("minecraft:dark_oak_fence", "minecraft:bamboo_fence"),
			Map.entry("minecraft:dark_oak_trapdoor", "minecraft:bamboo_trapdoor"),
			Map.entry("minecraft:dark_oak_log", "minecraft:bamboo_block"),
			Map.entry("minecraft:stripped_dark_oak_log", "minecraft:stripped_bamboo_block"),
			Map.entry("minecraft:stripped_dark_oak_wood", "minecraft:bamboo_mosaic"),
			Map.entry("minecraft:spruce_planks", "minecraft:bamboo_mosaic"),
			Map.entry("minecraft:spruce_stairs", "minecraft:bamboo_mosaic_stairs"),
			Map.entry("minecraft:deepslate_tiles", "minecraft:mud_bricks"),
			Map.entry("minecraft:deepslate_tile_stairs", "minecraft:mud_brick_stairs"),
			Map.entry("minecraft:deepslate_tile_slab", "minecraft:mud_brick_slab")));

	/** What opens a style for a ship's owner. */
	public enum Unlock {
		NONE, HALL_3, PIRATES, VOYAGE
	}

	private final String id;
	private final Unlock unlock;
	private final int sail;
	private final int lantern;
	private final Map<String, String> remap;

	KapalStyle(String id, Unlock unlock, int sail, int lantern, Map<String, String> remap) {
		this.id = id;
		this.unlock = unlock;
		this.sail = sail;
		this.lantern = lantern;
		this.remap = remap;
	}

	/** The lang suffix ({@code kultivator.kapal.style.<id>}) and the {@code uji kapal gaya} argument. */
	public String id() {
		return id;
	}

	public Unlock unlock() {
		return unlock;
	}

	/** The undyed sail cloth colour (the banner colour is mixed in). */
	public int sail() {
		return sail;
	}

	/** The lantern glow colour. */
	public int lantern() {
		return lantern;
	}

	/** The block id drawn for {@code id} in this style ({@code id} itself when unchanged). */
	public String remap(String blockId) {
		return remap.getOrDefault(blockId, blockId);
	}

	public static KapalStyle byOrdinal(int ordinal) {
		KapalStyle[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))];
	}

	/** The style of a {@code uji kapal gaya} argument, or null. */
	public static KapalStyle byId(String id) {
		for (KapalStyle style : values()) {
			if (style.id.equals(id)) {
				return style;
			}
		}
		return null;
	}
}

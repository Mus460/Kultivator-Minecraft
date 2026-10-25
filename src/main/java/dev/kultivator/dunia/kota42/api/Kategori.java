package dev.kultivator.dunia.kota42.api;

import dev.kultivator.Kultivator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The twelve price categories of the town economy (DESIGN 42.6.3, contract C42.C1). {@code HargaKota.faktor(server, situs,
 * kategori)} returns one multiplier per category and place; the town stock ({@code StokKota}, 0-200, start 100) is kept
 * per category. Ordinals are saved inside one world (12 floats per place cache), so constants are only appended.
 */
public enum Kategori {
	/** Food: buns, rice, fish, dishes (farmers, fishers; every resident eats 0.2 per day). */
	PANGAN,
	/** Medicine: pills, decoctions (alchemists, healers; the sick and attacks consume). */
	OBAT,
	/** Herbs: young herbs (herb pickers; alchemists consume). */
	HERBA,
	/** Weapons: town weapons (smiths; guards and wars consume). */
	SENJATA,
	/** Armour: vanilla armour (smiths; guards consume). */
	ZIRAH,
	/** Talismans (monks, scholars). */
	JIMAT,
	/** Building material: wood, stone, roof tiles (porters, builders; plots and repairs consume). */
	BAHAN,
	/** Cloth and robes (weavers; festivals, funerals, winter consume). */
	KAIN,
	/** Luxury: fine tea, decorative jade, wine (festivals, officials). */
	MEWAH,
	/** Trade goods {@code kultivator:dagang/*} of 38.T (they keep their own saturation; only events and war multiply). */
	DAGANG,
	/** Services: rooms, healers, teleports. */
	JASA,
	/** Buildings: house purchase and rent, plots (follow prosperity and density). */
	BANGUNAN;

	private static final Kategori[] ALL = values();

	/** "pangan", "obat"... (commands, data files, lang suffix). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code kultivator.kota42.kategori.<id>}. */
	public Component nama() {
		return Component.translatable("kultivator.kota42.kategori." + id());
	}

	public static @Nullable Kategori byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Kategori k : ALL) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		return null;
	}

	public static Kategori byOrdinal(int ordinal) {
		return ALL[Math.floorMod(ordinal, ALL.length)];
	}

	/**
	 * The category of an item a shop sells or buys (C42.C1). Mod items by their path ({@code dagang/*} trade goods, pills,
	 * herbs, talismans, cloth, luxury), then by components: food, weapons and tools, equippable armour, building blocks.
	 * Everything else counts as {@link #BAHAN} (raw goods). Never {@link #JASA} or {@link #BANGUNAN}: those price services
	 * and houses, not items.
	 */
	public static Kategori dari(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return BAHAN;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath();
		if (Kultivator.MOD_ID.equals(id.getNamespace())) {
			if (path.startsWith("dagang/") || path.startsWith("dagang_")) {
				return DAGANG;
			}
			if (path.contains("pil") || path.contains("ramuan") || path.contains("obat") || path.contains("salep")) {
				return OBAT;
			}
			if (path.contains("herba") || path.contains("rumput") || path.contains("akar_") || path.contains("bunga")
					|| path.contains("jamur") || path.contains("ginseng") || path.contains("lingzhi")) {
				return HERBA;
			}
			if (path.contains("jimat") || path.contains("talisman") || path.contains("fu_") || path.contains("rajah")) {
				return JIMAT;
			}
			if (path.contains("kain") || path.contains("jubah") || path.contains("sutra") || path.contains("benang")) {
				return KAIN;
			}
			if (path.contains("teh") || path.contains("giok") || path.contains("arak") || path.contains("permata") || path.contains("lukisan")) {
				return MEWAH;
			}
		}
		if (stack.has(DataComponents.FOOD)) {
			return PANGAN;
		}
		if (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL)) {
			return SENJATA;
		}
		if (stack.has(DataComponents.EQUIPPABLE)) {
			return ZIRAH;
		}
		if (path.contains("wool") || path.contains("carpet") || path.contains("string")) {
			return KAIN;
		}
		if (path.contains("emerald") || path.contains("diamond") || path.contains("amethyst") || path.contains("gold_")) {
			return MEWAH;
		}
		return BAHAN;
	}
}

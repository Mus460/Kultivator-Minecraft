package dev.kultivator.talisman;

import dev.kultivator.data.Element;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The eight talismans of DESIGN 19.3.6: item id, test id ({@code uji jimat}), the qi an inscription costs, the beast-core
 * element that may replace the catalyst, the uses of one talisman and the colour of its glyphs. The catalyst items are
 * matched by item id so every copper variant of a chain or lightning rod counts.
 */
public enum TalismanKind {
	THUNDER("thunder_talisman", "petir", 20, Element.LIGHTNING, 1, 0xB57CFF),
	FIRE_CLOUD("fire_cloud_talisman", "awan_api", 20, Element.FIRE, 1, 0xFF6A2A),
	SHIELD("shield_talisman", "perisai", 25, Element.EARTH, 1, 0xFFD54A),
	BINDING("binding_talisman", "pengikat", 20, Element.ICE, 1, 0x9FF3FF),
	RETURN("return_talisman", "pulang", 40, null, 1, 0xFFB43A),
	TRIBULATION_WARD("tribulation_ward_talisman", "penangkal", 35, null, 1, 0x6CB8FF),
	THOUSAND_MILE("thousand_mile_talisman", "seribu_li", 15, Element.WIND, 2, 0xB6FFB0),
	LIMPIDITY("limpidity_talisman", "kejernihan", 30, Element.SPACE, 1, 0xC8B8FF);

	private final String itemId;
	private final String testId;
	private final int inscribeQi;
	private final @Nullable Element core;
	private final int uses;
	private final int color;

	TalismanKind(String itemId, String testId, int inscribeQi, @Nullable Element core, int uses, int color) {
		this.itemId = itemId;
		this.testId = testId;
		this.inscribeQi = inscribeQi;
		this.core = core;
		this.uses = uses;
		this.color = color;
	}

	/** The item id without namespace, e.g. {@code thunder_talisman}. */
	public String itemId() {
		return itemId;
	}

	/** The id of {@code uji jimat <jenis>}, e.g. {@code petir}. */
	public String testId() {
		return testId;
	}

	/** Qi an inscription costs. */
	public int inscribeQi() {
		return inscribeQi;
	}

	/** The element of a beast core that may replace the catalyst, null when no core fits. */
	public @Nullable Element core() {
		return core;
	}

	/** Uses of one talisman (the Thousand Mile Talisman has two). */
	public int uses() {
		return uses;
	}

	public int color() {
		return color;
	}

	public Component displayName() {
		return Component.translatable("item.kultivator." + itemId);
	}

	/** True when {@code stack} is this kind's catalyst item (not a core). */
	public boolean isCatalyst(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return switch (this) {
			case THUNDER -> stack.is(Items.COPPER_INGOT);
			case FIRE_CLOUD -> stack.is(Items.BLAZE_POWDER);
			case SHIELD -> stack.is(Items.GOLD_INGOT);
			case BINDING -> {
				String path = path(stack);
				yield path.equals("iron_chain") || path.endsWith("copper_chain");
			}
			case RETURN -> stack.is(Items.ENDER_PEARL);
			case TRIBULATION_WARD -> path(stack).endsWith("lightning_rod");
			case THOUSAND_MILE -> stack.is(Items.SUGAR);
			case LIMPIDITY -> stack.is(Items.FERMENTED_SPIDER_EYE);
		};
	}

	/** The kind whose catalyst item {@code stack} is, or null. */
	public static @Nullable TalismanKind byCatalyst(ItemStack stack) {
		for (TalismanKind kind : values()) {
			if (kind.isCatalyst(stack)) {
				return kind;
			}
		}
		return null;
	}

	/** The kind a beast core of {@code element} inscribes, or null. */
	public static @Nullable TalismanKind byCore(@Nullable Element element) {
		if (element == null) {
			return null;
		}
		for (TalismanKind kind : values()) {
			if (kind.core == element) {
				return kind;
			}
		}
		return null;
	}

	/** The kind of a test id ({@code petir}) or item id ({@code thunder_talisman}), or null. */
	public static @Nullable TalismanKind byId(String id) {
		String key = id.toLowerCase(Locale.ROOT);
		for (TalismanKind kind : values()) {
			if (kind.testId.equals(key) || kind.itemId.equals(key)) {
				return kind;
			}
		}
		return null;
	}

	private static String path(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}
}

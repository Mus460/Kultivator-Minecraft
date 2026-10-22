package dev.kultivator.sekte.kebun;

import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbItem;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.event.SectEventItems;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the beds take and give, and what each depth of the mine yields (DESIGN 36.2.10). Pure tables; every ItemStack is
 * made on demand (never in a static initializer).
 * <ul>
 * <li>{@link Benih}: the nine seeds a bed takes, the alchemy herbs that already exist (the eight spirit herbs of
 * {@code harta.HerbKind} and the Herba Fondasi of the duties), each with its grade ({@code mutu} 0-3, from the herb's
 * rarity): a bed ripens in {@code 1 + mutu} world days.</li>
 * <li>{@link #roll}: the mine's weighted table for depth levels 1-9: low-grade spirit stones and common ores near the
 * surface, mid and high grade stones, gems and crystals deeper down, from level 7 the rare Inti Roh Gunung and at
 * level 9 a supreme stone now and then.</li>
 * </ul>
 * Sumber: the Hundred Medicine Garden (<i>A Record of a Mortal's Journey</i> ch. 150-151); the jade-bearing mountains of
 * the <i>Shanhaijing</i>; Nüwa's five-coloured stones melted from the earth (<i>Huainanzi</i> ch. 6) for the mountain core.
 */
public final class TabelHasil {

	/** A seed of a bed: a spirit herb kind, or the Herba Fondasi ({@code kind == null}). */
	public enum Benih {
		SPIRIT_GRASS(HerbKind.SPIRIT_GRASS, 0),
		NINECURL_GINSENG(HerbKind.NINECURL_GINSENG, 1),
		EIGHT_ANGLED_ICE_GRASS(HerbKind.EIGHT_ANGLED_ICE_GRASS, 1),
		PURPLE_FLAME_FLOWER(HerbKind.PURPLE_FLAME_FLOWER, 1),
		YELLOW_ESSENCE_MUSHROOM(HerbKind.YELLOW_ESSENCE_MUSHROOM, 1),
		ILLUSORY_SPIRIT_HERB(HerbKind.ILLUSORY_SPIRIT_HERB, 2),
		GOLD_LIGHTNING_BAMBOO(HerbKind.GOLD_LIGHTNING_BAMBOO, 2),
		GINSENG_FRUIT(HerbKind.GINSENG_FRUIT, 3),
		FONDASI(null, 0);

		private static final Benih[] ALL = values();

		private final @Nullable HerbKind kind;
		private final int mutu;

		Benih(@Nullable HerbKind kind, int mutu) {
			this.kind = kind;
			this.mutu = mutu;
		}

		/** The saved id: the herb's item id path ({@code spirit_grass}), {@code foundation_herb} for the Herba Fondasi. */
		public String id() {
			return kind == null ? "foundation_herb" : kind.id();
		}

		public @Nullable HerbKind kind() {
			return kind;
		}

		/** The grade 0-3 (growth takes {@code 1 + mutu} world days). */
		public int mutu() {
			return mutu;
		}

		/** The herb's colour (the client's glow, the screen). */
		public int color() {
			return kind == null ? 0xC8E890 : kind.color();
		}

		/** The herb's name (its item's translation). */
		public Component label() {
			return Component.translatable(item().getDescriptionId());
		}

		public Item item() {
			return kind == null ? SectEventItems.FOUNDATION_HERB : HartaItems.herb(kind);
		}

		/** True when {@code stack} is this seed. */
		public boolean matches(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			if (kind == null) {
				return stack.is(SectEventItems.FOUNDATION_HERB);
			}
			return stack.getItem() instanceof SpiritHerbItem herb && herb.kind() == kind;
		}

		/** One harvested herb aged {@code age} years (the Herba Fondasi carries no age). */
		public ItemStack harvest(int age, int count) {
			if (kind == null) {
				return new ItemStack(SectEventItems.FOUNDATION_HERB, Math.max(1, count));
			}
			ItemStack stack = SpiritHerbs.create(kind, age);
			stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), count)));
			return stack;
		}

		public static @Nullable Benih byId(@Nullable String id) {
			if (id == null || id.isEmpty()) {
				return null;
			}
			for (Benih benih : ALL) {
				if (benih.id().equals(id)) {
					return benih;
				}
			}
			return null;
		}

		/** The seed {@code stack} is, or null. */
		public static @Nullable Benih of(ItemStack stack) {
			for (Benih benih : ALL) {
				if (benih.matches(stack)) {
					return benih;
				}
			}
			return null;
		}

		public static Benih[] all() {
			return ALL.clone();
		}
	}

	/** Seed ages above this count as this much when the harvest's age is worked out (no endless ageing by replanting). */
	public static final int SEED_AGE_CAP = 300;
	/** A harvest adds up to this many years per growth day. */
	public static final int YEARS_PER_DAY = 10;

	/** The age of a harvested herb from a seed of {@code seedAge} that grew {@code days} days. */
	public static int harvestAge(int seedAge, int days, RandomSource random) {
		int base = Math.max(10, Math.min(SEED_AGE_CAP, seedAge));
		return base + random.nextInt(Math.max(1, YEARS_PER_DAY * Math.max(1, days)) + 1);
	}

	// ================================================================== the mine

	/** One line of the mine table: levels {@code from..to}, weight, count range and the stack. */
	private record Line(int from, int to, int weight, int min, int max, Supplier<ItemStack> stack) {
	}

	private static List<Line> lines;

	private static List<Line> lines() {
		if (lines == null) {
			List<Line> list = new ArrayList<>();
			// Near the surface (levels 1-4): low-grade stones and common ores.
			list.add(new Line(1, 4, 40, 1, 2, () -> SpiritStones.stack(SpiritStones.LOW, 1)));
			list.add(new Line(1, 2, 20, 1, 3, () -> new ItemStack(Items.COAL)));
			list.add(new Line(1, 3, 20, 1, 3, () -> new ItemStack(Items.RAW_COPPER)));
			list.add(new Line(1, 6, 15, 1, 2, () -> new ItemStack(Items.RAW_IRON)));
			list.add(new Line(1, 9, 6, 1, 2, () -> new ItemStack(Items.AMETHYST_SHARD)));
			// The middle (levels 3-6): mid-grade stones, gold, lapis, redstone, quartz.
			list.add(new Line(3, 9, 12, 1, 2, () -> SpiritStones.stack(SpiritStones.MID, 1)));
			list.add(new Line(3, 8, 10, 1, 3, () -> new ItemStack(Items.RAW_GOLD)));
			list.add(new Line(3, 6, 10, 2, 4, () -> new ItemStack(Items.LAPIS_LAZULI)));
			list.add(new Line(3, 5, 8, 2, 4, () -> new ItemStack(Items.REDSTONE)));
			list.add(new Line(3, 7, 8, 1, 3, () -> new ItemStack(Items.QUARTZ)));
			list.add(new Line(5, 9, 10, 2, 4, () -> SpiritStones.stack(SpiritStones.LOW, 1)));
			// The deep (levels 5-9): gems, high-grade stones, the mountain core, now and then a supreme stone.
			list.add(new Line(5, 9, 5, 1, 1, () -> new ItemStack(Items.DIAMOND)));
			list.add(new Line(5, 9, 5, 1, 1, () -> new ItemStack(Items.EMERALD)));
			list.add(new Line(7, 9, 14, 1, 2, () -> SpiritStones.stack(SpiritStones.MID, 1)));
			list.add(new Line(7, 9, 7, 1, 1, () -> SpiritStones.stack(SpiritStones.HIGH, 1)));
			list.add(new Line(7, 9, 1, 1, 1, () -> new ItemStack(KebunItems.INTI_ROH_GUNUNG)));
			list.add(new Line(9, 9, 1, 1, 1, () -> new ItemStack(KebunItems.INTI_ROH_GUNUNG)));
			list.add(new Line(9, 9, 4, 1, 1, () -> SpiritStones.stack(SpiritStones.HIGH, 1)));
			list.add(new Line(9, 9, 2, 1, 1, () -> new ItemStack(Items.ECHO_SHARD)));
			list.add(new Line(9, 9, 1, 1, 1, () -> SpiritStones.stack(SpiritStones.SUPREME, 1)));
			lines = List.copyOf(list);
		}
		return lines;
	}

	/** One roll of the table at depth {@code level} (1-9). */
	public static ItemStack roll(int level, RandomSource random) {
		int lv = Math.max(1, Math.min(9, level));
		int total = 0;
		for (Line line : lines()) {
			if (lv >= line.from() && lv <= line.to()) {
				total += line.weight();
			}
		}
		if (total <= 0) {
			return ItemStack.EMPTY;
		}
		int pick = random.nextInt(total);
		for (Line line : lines()) {
			if (lv < line.from() || lv > line.to()) {
				continue;
			}
			pick -= line.weight();
			if (pick < 0) {
				ItemStack stack = line.stack().get();
				int count = line.min() + (line.max() > line.min() ? random.nextInt(line.max() - line.min() + 1) : 0);
				stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), count)));
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * The items depth {@code level} can give, most common first, with their share in percent (the mine page of the
	 * screen): {@code [stack, percent]} pairs made on demand.
	 */
	public static List<Object[]> preview(int level) {
		int lv = Math.max(1, Math.min(9, level));
		int total = 0;
		for (Line line : lines()) {
			if (lv >= line.from() && lv <= line.to()) {
				total += line.weight();
			}
		}
		List<Object[]> out = new ArrayList<>();
		if (total <= 0) {
			return out;
		}
		List<Line> sorted = new ArrayList<>();
		for (Line line : lines()) {
			if (lv >= line.from() && lv <= line.to()) {
				sorted.add(line);
			}
		}
		sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
		for (Line line : sorted) {
			ItemStack stack = line.stack().get();
			stack.setCount(Math.max(1, line.max()));
			out.add(new Object[]{stack, Math.max(1, Math.round(100.0F * line.weight() / total))});
		}
		return out;
	}

	/** Miner slots at depth {@code level}: 2 / 3 / 4 / 6 (levels 1-2 / 3-4 / 5-6 / 7-9). */
	public static int slots(int level) {
		if (level <= 0) {
			return 0;
		}
		if (level <= 2) {
			return 2;
		}
		if (level <= 4) {
			return 3;
		}
		if (level <= 6) {
			return 4;
		}
		return 6;
	}

	/** The building materials of the step to {@code level} (2-9), in whole units: {Kayu, Batu}. */
	public static int[] materials(int level) {
		int lv = Math.max(2, Math.min(9, level));
		return new int[]{16 * lv, 24 * lv};
	}

	private TabelHasil() {
	}
}

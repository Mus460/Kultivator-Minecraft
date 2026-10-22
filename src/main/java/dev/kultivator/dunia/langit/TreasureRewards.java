package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.abadi.AbadiItems;
import dev.kultivator.batin.BatinItems;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.purba.PurbaItems;
import dev.kultivator.technique.source.JadeSlips;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a born treasure holds (DESIGN 34.5.9), by tier and kind. The tier comes from the strongest active player within
 * 1 000 blocks of the birth: T1 Mortal-Foundation, T2 Golden Core-Nascent Soul, T3 Deity Transformation-Dao Union, T4
 * Immortal and above. Every tier has its fixed core and the kind adds its accent:
 * <ul>
 * <li>T1: herbs of 100-300 years and Mid spirit stones;</li>
 * <li>T2: a herb of 500-1 000 years, a High beast core and a jade slip (Huang or Xuan grade);</li>
 * <li>T3: a millennium herb, a King beast core, a heavenly flame seed at 5 % (10 % for {@code api});</li>
 * <li>T4: Batu Asal Abadi, an Immortal essence pearl, a primordial material at 5 % (10 % for {@code pusaka}).</li>
 * </ul>
 * On top, the sky's own gift ({@link #skyGift}). Kinds {@code herba inti giok pusaka api batu}. The claim gives no cultivation XP of its own. Item stacks are made only
 * when a treasure is born or claimed (never in a static initialiser).
 */
public final class TreasureRewards {

	public static final String[] KINDS = {"herba", "inti", "giok", "pusaka", "api", "batu"};
	/** Kind weights (herbs and stones are the common births). */
	private static final int[] KIND_WEIGHTS = {26, 18, 14, 10, 12, 20};

	/** A random kind. */
	public static String randomKind(RandomSource random) {
		int total = 0;
		for (int weight : KIND_WEIGHTS) {
			total += weight;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < KINDS.length; i++) {
			roll -= KIND_WEIGHTS[i];
			if (roll < 0) {
				return KINDS[i];
			}
		}
		return KINDS[0];
	}

	public static boolean validKind(@Nullable String kind) {
		if (kind == null) {
			return false;
		}
		for (String each : KINDS) {
			if (each.equals(kind)) {
				return true;
			}
		}
		return false;
	}

	/** The treasure everyone sees inside the lotus: the main prize of the tier and kind. */
	public static ItemStack display(int tier, String kind, RandomSource random) {
		List<ItemStack> roll = roll(tier, kind, random, true);
		return roll.isEmpty() ? new ItemStack(LangitItems.BATU_METEOR_ROH) : roll.getFirst();
	}

	/** The full reward; the first stack is the main prize. */
	public static List<ItemStack> roll(int tier, String kind, RandomSource random) {
		return roll(tier, kind, random, false);
	}

	private static List<ItemStack> roll(int tier, String kind, RandomSource random, boolean displayOnly) {
		List<ItemStack> out = new ArrayList<>();
		int t = Math.max(1, Math.min(4, tier));
		Element element = kind.equals("api") ? Element.FIRE : Element.values()[random.nextInt(Element.values().length)];
		switch (t) {
			case 1 -> {
				main(out, kind, t, element, random);
				if (displayOnly) {
					return out;
				}
				out.add(SpiritHerbs.randomFor(null, random, 100, 300));
				out.add(SpiritStones.stack(SpiritStones.MID, 1 + random.nextInt(2)));
			}
			case 2 -> {
				main(out, kind, t, element, random);
				if (displayOnly) {
					return out;
				}
				out.add(SpiritHerbs.randomFor(null, random, 500, 1000));
				out.add(BeastCores.create(element, BeastCores.HIGH));
				ItemStack slip = slip(random, TechniqueGrade.XUAN);
				if (!slip.isEmpty()) {
					out.add(slip);
				}
			}
			case 3 -> {
				main(out, kind, t, element, random);
				if (displayOnly) {
					return out;
				}
				out.add(SpiritHerbs.randomFor(null, random, SpiritHerbs.MILLENNIUM, SpiritHerbs.MILLENNIUM));
				out.add(BeastCores.create(element, BeastCores.KING));
				if (random.nextDouble() < (kind.equals("api") ? 0.10D : 0.05D)) {
					out.add(flameSeed(random));
				}
			}
			default -> {
				main(out, kind, t, element, random);
				if (displayOnly) {
					return out;
				}
				out.add(new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE, 2 + random.nextInt(3)));
				out.add(new ItemStack(BatinItems.IMMORTAL_ESSENCE_PEARL));
				if (random.nextDouble() < (kind.equals("pusaka") ? 0.10D : 0.05D)) {
					out.add(new ItemStack(primordial(random)));
				}
			}
		}
		skyGift(out, t, random);
		return out;
	}

	/**
	 * The sky's own gift on top (detail pass): T1 35 % one Embun Roh, T2 35 % one or two Batu Meteor Roh, T3 25 % and T4
	 * 30 % one Inti Meteor Agung. Small next to the tier's core, so the tiers stay where DESIGN 34.5.9 puts them.
	 */
	private static void skyGift(List<ItemStack> out, int tier, RandomSource random) {
		switch (tier) {
			case 1 -> {
				if (random.nextDouble() < 0.35D) {
					out.add(new ItemStack(LangitItems.EMBUN_ROH));
				}
			}
			case 2 -> {
				if (random.nextDouble() < 0.35D) {
					out.add(new ItemStack(LangitItems.BATU_METEOR_ROH, 1 + random.nextInt(2)));
				}
			}
			case 3 -> {
				if (random.nextDouble() < 0.25D) {
					out.add(new ItemStack(LangitItems.INTI_METEOR_AGUNG));
				}
			}
			default -> {
				if (random.nextDouble() < 0.30D) {
					out.add(new ItemStack(LangitItems.INTI_METEOR_AGUNG));
				}
			}
		}
	}

	/** The kind's main prize for the tier (always the first stack). */
	private static void main(List<ItemStack> out, String kind, int tier, Element element, RandomSource random) {
		switch (kind) {
			case "herba" -> {
				int[] ages = {120 + random.nextInt(181), 500 + random.nextInt(501), SpiritHerbs.MILLENNIUM, SpiritHerbs.MILLENNIUM};
				HerbKind herb = tier >= 3 ? HerbKind.GINSENG_FRUIT : null;
				out.add(herb == null ? SpiritHerbs.randomFor(null, random, ages[tier - 1], ages[tier - 1]) : SpiritHerbs.create(herb, ages[tier - 1]));
			}
			case "inti" -> out.add(BeastCores.create(element, Math.min(BeastCores.KING, tier)));
			case "giok" -> {
				TechniqueGrade[] grades = {TechniqueGrade.XUAN, TechniqueGrade.DI, TechniqueGrade.TIAN, TechniqueGrade.TIAN};
				ItemStack slip = slip(random, grades[tier - 1]);
				out.add(slip.isEmpty() ? SpiritStones.stack(Math.min(SpiritStones.HIGH, tier), 1) : slip);
			}
			case "pusaka" -> {
				out.add(new ItemStack(LangitItems.BATU_METEOR_ROH, 2 + tier));
				if (tier >= 4) {
					out.add(SpiritStones.stack(SpiritStones.SUPREME, 1));
				} else if (tier >= 2) {
					out.add(SpiritStones.stack(SpiritStones.HIGH, tier - 1));
				}
			}
			case "api" -> {
				int[] ages = {200 + random.nextInt(101), 600 + random.nextInt(401), SpiritHerbs.MILLENNIUM, SpiritHerbs.MILLENNIUM};
				out.add(SpiritHerbs.create(HerbKind.PURPLE_FLAME_FLOWER, ages[tier - 1]));
				if (tier >= 4 && random.nextDouble() < 0.15D) {
					out.add(flameSeed(random));
				}
			}
			default -> {
				// batu: the stones of the tier (T4: Batu Asal Abadi on top of the tier's core)
				switch (tier) {
					case 1 -> out.add(SpiritStones.stack(SpiritStones.MID, 2 + random.nextInt(2)));
					case 2 -> out.add(SpiritStones.stack(SpiritStones.HIGH, 1));
					case 3 -> out.add(SpiritStones.stack(SpiritStones.HIGH, 2 + random.nextInt(2)));
					default -> out.add(new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE, 3));
				}
			}
		}
	}

	/** A jade slip of a slip-allowed technique of {@code maxGrade} or below (empty before the registry is frozen). */
	private static ItemStack slip(RandomSource random, TechniqueGrade maxGrade) {
		if (!TechniqueRegistry.frozen()) {
			return ItemStack.EMPTY;
		}
		List<TechniqueSpec> specs = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.slipAllowed() && spec.grade().ordinal() <= maxGrade.ordinal()) {
				specs.add(spec);
			}
		}
		if (specs.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return JadeSlips.stack(specs.get(random.nextInt(specs.size())).id());
	}

	/** A heavenly flame seed of a random flame, made like the Lelang Abadi lot (by registry id: the class is package-private). */
	@SuppressWarnings("unchecked")
	private static ItemStack flameSeed(RandomSource random) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id("heavenly_flame_seed"));
		if (item == Items.AIR) {
			return SpiritStones.stack(SpiritStones.HIGH, 1);
		}
		ItemStack seed = new ItemStack(item);
		int kind = random.nextInt(PowerIds.FLAME_IDS.length);
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Kultivator.id("flame_kind"));
		if (type != null) {
			seed.set((DataComponentType<Integer>) type, kind);
			seed.set(DataComponents.ITEM_NAME, Component.translatable("item.kultivator.heavenly_flame_seed." + PowerIds.FLAME_IDS[kind]));
			seed.set(DataComponents.ITEM_MODEL, Kultivator.id("heavenly_flame_seed_" + PowerIds.FLAME_IDS[kind]));
		}
		return seed;
	}

	private static Item primordial(RandomSource random) {
		Item[] all = {PurbaItems.KUN_SCALE, PurbaItems.PENG_FEATHER, PurbaItems.ZHULONG_SCALE, PurbaItems.TAOTIE_FANG};
		return all[random.nextInt(all.length)];
	}

	/** The colour of a tier (pillar crown, lotus glow, texts). */
	public static int tierColor(int tier) {
		return switch (Math.max(1, Math.min(4, tier))) {
			case 1 -> 0x9FE0C0;
			case 2 -> 0x6FB8FF;
			case 3 -> 0xC890FF;
			default -> 0xFFE27A;
		};
	}

	private TreasureRewards() {
	}
}

package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewards of module R in the units of DESIGN 34.1: Nilai Batu (NB, the one table of {@code kota.NilaiBatu}) paid in
 * real spirit stones from the highest grade down, aged herbs of the existing herb family, beast cores of
 * {@code harta/BeastCores}, the existing inheritance jade ({@code InheritanceTrials.jade}) and jade slips of the
 * existing registry. Stacks are made only when a reward is given (never in a static initializer). Everything that does
 * not fit the inventory drops at the player's feet, owned by them.
 */
public final class RRewards {

	/** Nilai Batu of each stone grade (a copy of {@code NilaiBatu.VALUE}, the one table; audit R17). */
	public static final int[] NB = {(int) NilaiBatu.VALUE[0], (int) NilaiBatu.VALUE[1], (int) NilaiBatu.VALUE[2], (int) NilaiBatu.VALUE[3]};

	/** Gives {@code nb} worth of spirit stones (highest grade first); returns the stacks given. */
	public static List<ItemStack> stones(ServerPlayer player, int nb) {
		List<ItemStack> given = new ArrayList<>();
		int left = Math.max(0, nb);
		for (int grade = 3; grade >= 0 && left > 0; grade--) {
			int count = left / NB[grade];
			if (count <= 0) {
				continue;
			}
			left -= count * NB[grade];
			while (count > 0) {
				int stack = Math.min(64, count);
				ItemStack stones = SpiritStones.stack(grade, stack);
				given.add(stones.copy());
				give(player, stones);
				count -= stack;
			}
		}
		return given;
	}

	/** A random herb of age {@code minAge..maxAge} years (rarer kinds for older herbs). */
	public static ItemStack herb(RandomSource random, int minAge, int maxAge) {
		HerbKind[] young = {HerbKind.SPIRIT_GRASS, HerbKind.NINECURL_GINSENG, HerbKind.YELLOW_ESSENCE_MUSHROOM, HerbKind.PURPLE_FLAME_FLOWER};
		HerbKind[] old = {HerbKind.EIGHT_ANGLED_ICE_GRASS, HerbKind.PURPLE_FLAME_FLOWER, HerbKind.GOLD_LIGHTNING_BAMBOO,
				HerbKind.ILLUSORY_SPIRIT_HERB, HerbKind.GINSENG_FRUIT};
		HerbKind kind = maxAge >= 600 ? old[random.nextInt(old.length)] : young[random.nextInt(young.length)];
		Item item = HartaItems.herb(kind);
		ItemStack stack = new ItemStack(item);
		int lo = Math.max(1, minAge);
		int hi = Math.max(lo, maxAge);
		stack.set(HartaComponents.HERB_AGE, Math.min(HartaComponents.MAX_HERB_AGE, lo + random.nextInt(hi - lo + 1)));
		return stack;
	}

	/** A beast core of {@code grade} (1 Low .. 4 King) of a random base element. */
	public static ItemStack core(RandomSource random, int grade) {
		Element[] base = {Element.METAL, Element.WOOD, Element.WATER, Element.FIRE, Element.EARTH};
		return BeastCores.create(base[random.nextInt(base.length)], Math.max(1, Math.min(4, grade)));
	}

	/** The inheritance jade of {@code theme} (InheritanceTrials.THEMES; unknown themes give the universal jade). */
	public static ItemStack jade(String theme) {
		return InheritanceTrials.jade(theme);
	}

	/**
	 * A jade slip of a slip-allowed technique of grade {@code from..to}, of {@code dao} when one exists (the demonic
	 * prison prefers the Shadow Dao); empty while the registry is not built.
	 */
	public static ItemStack slip(RandomSource random, TechniqueGrade from, TechniqueGrade to, @Nullable Dao dao) {
		if (!TechniqueRegistry.frozen()) {
			return ItemStack.EMPTY;
		}
		List<TechniqueSpec> pool = new ArrayList<>();
		List<TechniqueSpec> any = new ArrayList<>();
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			if (grade.ordinal() < from.ordinal() || grade.ordinal() > to.ordinal()) {
				continue;
			}
			for (TechniqueSpec spec : TechniqueRegistry.ofGrade(grade)) {
				if (!spec.slipAllowed()) {
					continue;
				}
				any.add(spec);
				if (dao != null && spec.dao() == dao) {
					pool.add(spec);
				}
			}
		}
		List<TechniqueSpec> use = pool.isEmpty() ? any : pool;
		if (use.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return JadeSlips.stack(use.get(random.nextInt(use.size())).id());
	}

	/** Into the inventory, else dropped at the player's feet (only they can pick it up). */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy) || !copy.isEmpty()) {
			if (!copy.isEmpty()) {
				ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), copy);
				item.setTarget(player.getUUID());
				item.setPickUpDelay(10);
				player.level().addFreshEntity(item);
			}
		}
	}

	/** Gives every stack and tells the player one chat line listing them. */
	public static void giveAll(ServerPlayer player, List<ItemStack> stacks, String titleKey) {
		MutableComponent line = Component.translatable(titleKey).withStyle(ChatFormatting.GOLD);
		boolean first = true;
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			line.append(Component.literal(first ? " " : ", ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(stack.getCount() + "× ").withStyle(ChatFormatting.WHITE)).append(stack.getHoverName());
			first = false;
			give(player, stack);
		}
		Msg.chat(player, line);
	}

	/** A random NB amount in {@code min..max}, scaled. */
	public static int roll(RandomSource random, int min, int max, double scale) {
		int lo = Math.max(0, min);
		int hi = Math.max(lo, max);
		return (int) Math.round((lo + random.nextInt(hi - lo + 1)) * Math.max(0.0D, scale));
	}

	private RRewards() {
	}
}

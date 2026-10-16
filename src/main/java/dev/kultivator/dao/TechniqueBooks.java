package dev.kultivator.dao;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Technique books are vanilla enchanted books (DESIGN 7): sneak + use one to read it. Each stored enchantment maps to a
 * technique; a technique the player can learn now is learned and the book consumed; a known technique, or a book of
 * the player's own Dao that cannot be learned yet, gives +5 comprehension instead; otherwise the player is told exactly
 * what is missing and keeps the book. A book without any mapped enchantment gives 30 cultivation XP.
 */
public final class TechniqueBooks {

	/** Ticks before an enchanted book can be read again. */
	public static final int USE_COOLDOWN = 10;
	/** Comprehension from a book that teaches nothing new. */
	public static final double INSIGHT = 5.0D;
	/** Cultivation XP from a book without a technique. */
	public static final double PLAIN_BOOK_XP = 30.0D;
	/** {@code FxType.DAO_CHOSEN} b value: a technique learned from a book (a = Dao ordinal, -1 universal). */
	public static final int FX_LEARNED = 5;
	/** {@code FxType.DAO_CHOSEN} b value: insight from a book (a = Dao ordinal). */
	public static final int FX_INSIGHT = 6;
	/** Colour of universal techniques in texts and effects. */
	public static final int UNIVERSAL_COLOR = 0xFFD54A;

	/** Enchantment -> the technique its book teaches. */
	private static final Map<ResourceKey<Enchantment>, Technique> MAPPING = new LinkedHashMap<>();

	static {
		MAPPING.put(Enchantments.SHARPNESS, Technique.THOUSAND_SWORDS);
		MAPPING.put(Enchantments.SWEEPING_EDGE, Technique.THOUSAND_SWORDS);
		MAPPING.put(Enchantments.FIRE_ASPECT, Technique.PHOENIX_FLAME);
		MAPPING.put(Enchantments.FLAME, Technique.PHOENIX_FLAME);
		MAPPING.put(Enchantments.FROST_WALKER, Technique.GLACIER_PRISON);
		MAPPING.put(Enchantments.CHANNELING, Technique.HEAVENLY_THUNDER);
		MAPPING.put(Enchantments.PROTECTION, Technique.MOUNTAIN_STOMP);
		MAPPING.put(Enchantments.THORNS, Technique.MOUNTAIN_STOMP);
		MAPPING.put(Enchantments.MENDING, Technique.LIFE_DRAIN);
		MAPPING.put(Enchantments.SWIFT_SNEAK, Technique.GALE);
		MAPPING.put(Enchantments.FEATHER_FALLING, Technique.GALE);
		MAPPING.put(Enchantments.INFINITY, Technique.SPACE_PRISON);
		MAPPING.put(Enchantments.LOYALTY, Technique.SPACE_PRISON);
		MAPPING.put(Enchantments.SMITE, Technique.BUDDHA_PALM);
	}

	public static void init() {
		UseItemCallback.EVENT.register(TechniqueBooks::onUseItem);
	}

	// ================================================================== rules (shared with the client tooltip)

	/** The technique taught by an enchantment's book, or null. */
	public static @Nullable Technique techniqueFor(Holder<Enchantment> enchantment) {
		for (Map.Entry<ResourceKey<Enchantment>, Technique> entry : MAPPING.entrySet()) {
			if (enchantment.is(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** The techniques an enchanted book teaches, in technique order (empty for other items or unmapped books). */
	public static List<Technique> techniquesOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) {
			return List.of();
		}
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		EnumSet<Technique> found = EnumSet.noneOf(Technique.class);
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			Technique technique = techniqueFor(enchantment);
			if (technique != null) {
				found.add(technique);
			}
		}
		return new ArrayList<>(found);
	}

	/** True when the technique could be learned from its book right now. */
	public static boolean canLearn(CultivationData data, Technique technique) {
		if (data.knows(technique) || !data.realm().atLeast(technique.minRealm())) {
			return false;
		}
		Dao dao = technique.dao();
		return dao == null || data.dao() == dao && data.comprehension(dao) + 1.0E-9D >= technique.minComprehension();
	}

	/** The Dao a book of this technique deepens when it teaches nothing new (null: no insight possible). */
	public static @Nullable Dao insightDao(CultivationData data, Technique technique) {
		if (data.knows(technique)) {
			return technique.dao() != null ? technique.dao() : data.dao();
		}
		return technique.dao() != null && technique.dao() == data.dao() ? technique.dao() : null;
	}

	/** What keeps the player from learning the technique, one part per missing requirement (empty when nothing). */
	public static List<Component> missing(CultivationData data, Technique technique) {
		List<Component> parts = new ArrayList<>();
		if (!data.realm().atLeast(technique.minRealm())) {
			parts.add(Component.translatable("kultivator.dao.book.missing.realm",
					technique.minRealm().displayName().copy().withColor(technique.minRealm().color()), data.rank().displayName()));
		}
		Dao dao = technique.dao();
		if (dao != null) {
			Dao chosen = data.dao();
			if (chosen != dao) {
				parts.add(chosen == null
						? Component.translatable("kultivator.dao.book.missing.dao_none", DaoPaths.colored(dao))
						: Component.translatable("kultivator.dao.book.missing.dao_other", DaoPaths.colored(dao), DaoPaths.colored(chosen)));
			} else if (data.comprehension(dao) + 1.0E-9D < technique.minComprehension()) {
				parts.add(Component.translatable("kultivator.dao.book.missing.comprehension", DaoPaths.colored(dao),
						Comprehension.format(technique.minComprehension()), Comprehension.format(data.comprehension(dao))));
			}
		}
		return parts;
	}

	/** The colour of a technique's Dao (gold for universal techniques). */
	public static int color(Technique technique) {
		return technique.dao() == null ? UNIVERSAL_COLOR : technique.dao().color();
	}

	// ================================================================== reading a book

	/**
	 * Sneak + use on both sides. The client answers SUCCESS (arm swing, the use packet is still sent) and changes
	 * nothing; the server reads the book. Never reacts when the player is not sneaking.
	 */
	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (player.isSpectator() || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.ENCHANTED_BOOK) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) {
			return InteractionResult.PASS;
		}
		// The cooldown group comes from the stack, so set it before the stack may become empty.
		player.getCooldowns().addCooldown(stack, USE_COOLDOWN);
		try {
			return read(serverPlayer, stack);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Could not read the technique book {}", stack, exception);
			return InteractionResult.PASS;
		}
	}

	private static InteractionResult read(ServerPlayer player, ItemStack stack) {
		CultivationData data = Cultivators.get(player);
		List<Technique> taught = techniquesOf(stack);
		if (taught.isEmpty()) {
			readPlain(player, stack);
			return InteractionResult.SUCCESS;
		}

		List<Technique> learnable = new ArrayList<>();
		for (Technique technique : taught) {
			if (canLearn(data, technique)) {
				learnable.add(technique);
			}
		}
		if (!learnable.isEmpty()) {
			learn(player, data, stack, learnable);
			return InteractionResult.SUCCESS;
		}

		for (Technique technique : taught) {
			Dao dao = insightDao(data, technique);
			if (dao != null) {
				insight(player, data, stack, technique, dao);
				return InteractionResult.SUCCESS;
			}
		}

		// Nothing to take from it yet: say exactly why and keep the book.
		for (Technique technique : taught) {
			List<Component> missing = missing(data, technique);
			Component name = technique.displayName().copy().withColor(color(technique));
			if (missing.isEmpty() && data.knows(technique)) {
				Msg.chat(player, Component.translatable("kultivator.dao.book.known_no_dao", name).withStyle(ChatFormatting.GRAY));
			} else {
				Msg.chat(player, Component.translatable("kultivator.dao.book.cannot", name, DaoPaths.join(missing))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS,
				0.8F, 0.7F);
		return InteractionResult.PASS;
	}

	/** Learns every technique of the book the player can learn now. */
	private static void learn(ServerPlayer player, CultivationData data, ItemStack stack, List<Technique> techniques) {
		consume(player, stack);
		for (Technique technique : techniques) {
			data.learn(technique);
			Component name = technique.displayName().copy().withColor(color(technique)).withStyle(ChatFormatting.BOLD);
			Msg.chat(player, Component.translatable("kultivator.dao.book.learned", name).withStyle(ChatFormatting.YELLOW));
			Msg.chat(player, technique.description().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		Technique first = techniques.get(0);
		if (data.selected() == null) {
			data.setSelected(first);
			Msg.chat(player, Component.translatable("kultivator.dao.book.selected", first.displayName(),
					Component.keybind("key.kultivator.cast").withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
		} else {
			Msg.chat(player, Component.translatable("kultivator.dao.book.select_hint", Component.keybind("key.kultivator.previous"),
					Component.keybind("key.kultivator.next")).withStyle(ChatFormatting.GRAY));
		}
		Msg.actionBar(player, Component.translatable("kultivator.dao.book.learned_bar", first.displayName()).withColor(color(first)));
		// Lets the techniques module check its own rules (selection, collector advancement); books are never auto-learned.
		Techniques.refreshUnlocks(player);

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, first.dao() == null ? -1 : first.dao().ordinal(), FX_LEARNED, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.1F);
		Advancements.grant(player, "first_book");
		Cultivators.syncNow(player);
	}

	/** Nothing new in the book: +5 comprehension instead. */
	private static void insight(ServerPlayer player, CultivationData data, ItemStack stack, Technique technique, Dao dao) {
		consume(player, stack);
		double gained = Comprehension.grant(player, dao, INSIGHT);
		Component name = technique.displayName().copy().withColor(color(technique));
		String key = data.knows(technique) ? "kultivator.dao.book.insight_known" : "kultivator.dao.book.insight_future";
		Msg.chat(player, Component.translatable(key, name, DaoPaths.colored(dao)).withStyle(ChatFormatting.YELLOW));
		if (gained > 0.0D) {
			Msg.chat(player, Component.translatable("kultivator.dao.book.insight_gain", Comprehension.format(gained), DaoPaths.colored(dao),
					Comprehension.format(data.comprehension(dao))).withStyle(ChatFormatting.GRAY));
		} else {
			// Comprehension is already complete: the words still nourish the cultivation.
			double xp = Progression.grantXp(player, PLAIN_BOOK_XP, XpSource.BOOK);
			Msg.chat(player, Component.translatable("kultivator.dao.book.insight_full", DaoPaths.colored(dao), Msg.number(Math.max(0.0D, xp)))
					.withStyle(ChatFormatting.GRAY));
		}
		if (!data.knows(technique)) {
			List<Component> missing = missing(data, technique);
			if (!missing.isEmpty()) {
				Msg.chat(player, Component.translatable("kultivator.dao.book.still_missing", name, DaoPaths.join(missing))
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, dao.ordinal(), FX_INSIGHT, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.1F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		Advancements.grant(player, "book_insight");
		Cultivators.syncNow(player);
	}

	/** A book without technique: its words still nourish a little cultivation. */
	private static void readPlain(ServerPlayer player, ItemStack stack) {
		consume(player, stack);
		double gained = Progression.grantXp(player, PLAIN_BOOK_XP, XpSource.BOOK);
		MutableComponent message = gained > 0.0D
				? Component.translatable("kultivator.dao.book.plain", Msg.number(Math.max(1.0D, gained)))
				: Component.translatable("kultivator.dao.book.plain_none");
		Msg.chat(player, message.withStyle(ChatFormatting.GRAY));
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		player.level().playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.9F);
		Fx.burst(player.level(), player.position().add(0.0D, 1.2D, 0.0D), 0xCFE8FF, 12, 1.0F);
		Cultivators.syncNow(player);
	}

	private static void consume(ServerPlayer player, ItemStack stack) {
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
	}

	private TechniqueBooks() {
	}
}

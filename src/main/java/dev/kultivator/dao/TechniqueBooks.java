package dev.kultivator.dao;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.Harta;
import dev.kultivator.technique.source.BookPools;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.source.LearnRules;
import dev.kultivator.technique.source.TechniqueLoot;
import dev.kultivator.technique.spec.run.Mastery;
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
import java.util.Set;

/**
 * Technique books are vanilla enchanted books (DESIGN 7, extended by 22.8.2): sneak + use one to read it. Each stored
 * enchantment maps to a pool of specs ({@link BookPools}); the book teaches the first learnable spec of its pools in grade
 * order and is consumed. Otherwise it gives insight: +5 comprehension (the Dao of a known art of the pool, or the book
 * family's Dao when it is the reader's) and +15 mastery to the first known art of the pool. Otherwise the player is told
 * exactly what is missing and keeps the book. A book without any pool or family gives 30 cultivation XP.
 * <p>
 * Phase 7 (DESIGN 19.3.14, game design): only the uncapped insight comprehension is limited, to
 * {@code Harta.BOOK_INSIGHT_DAILY} books per in-game day ({@code extra.harta.book_day}, {@code book_insight}). Over the
 * limit a book that would only give comprehension is kept; learning and book mastery are never limited.
 * <p>
 * {@link #init()} also starts the phase-6c acquisition modules of {@code technique.source} (jade slips, inheritance
 * jades, loot), so their items exist before the creative tab is built.
 */
public final class TechniqueBooks {

	/** Ticks before an enchanted book can be read again. */
	public static final int USE_COOLDOWN = 10;
	/** Comprehension from a book that teaches nothing new. */
	public static final double INSIGHT = 5.0D;
	/** Mastery points a book of a known art gives (22.6). */
	public static final int INSIGHT_MASTERY = 15;
	/** Cultivation XP from a book without a technique. */
	public static final double PLAIN_BOOK_XP = 30.0D;
	/** {@code FxType.DAO_CHOSEN} b value: a technique learned from a book (a = Dao ordinal, -1 universal). */
	public static final int FX_LEARNED = 5;
	/** {@code FxType.DAO_CHOSEN} b value: insight from a book (a = Dao ordinal). */
	public static final int FX_INSIGHT = 6;
	/** Colour of universal techniques in texts and effects. */
	public static final int UNIVERSAL_COLOR = 0xFFD54A;
	/** At most this many locked arts are explained when a book teaches nothing. */
	public static final int EXPLAIN_MAX = 3;

	/** Enchantment -> the legacy technique its book taught before 6c (kept for callers of {@link #techniqueFor}). */
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
		// Phase 6c acquisition (DESIGN 22.8): items and listeners of technique.source, before ModItems.init builds the tab.
		JadeSlips.init();
		InheritanceTrials.init();
		TechniqueLoot.init();
	}

	// ================================================================== rules (shared with the client tooltip)

	/** The legacy technique an enchantment's book taught before 6c, or null (see {@link #poolFor} for the 6c pools). */
	public static @Nullable Technique techniqueFor(Holder<Enchantment> enchantment) {
		for (Map.Entry<ResourceKey<Enchantment>, Technique> entry : MAPPING.entrySet()) {
			if (enchantment.is(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** The book pool of an enchantment (DESIGN 22.8.2): the specs it can teach, in grade order. */
	public static List<TechniqueSpec> poolFor(Holder<Enchantment> enchantment) {
		return BookPools.pool(enchantment);
	}

	/** The legacy techniques among an enchanted book's pools, in technique order (empty for other items). */
	public static List<Technique> techniquesOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) {
			return List.of();
		}
		EnumSet<Technique> found = EnumSet.noneOf(Technique.class);
		for (TechniqueSpec spec : BookPools.poolOf(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
			if (spec.legacy() != null) {
				found.add(spec.legacy());
			}
		}
		return new ArrayList<>(found);
	}

	/** True when the legacy technique could be learned from its book right now. */
	public static boolean canLearn(CultivationData data, Technique technique) {
		return LearnRules.canLearn(data, technique.spec());
	}

	/** The Dao a book of this legacy technique deepens when it teaches nothing new (null: no insight possible). */
	public static @Nullable Dao insightDao(CultivationData data, Technique technique) {
		if (data.knows(technique)) {
			return technique.dao() != null ? technique.dao() : data.dao();
		}
		return technique.dao() != null && technique.dao() == data.dao() ? technique.dao() : null;
	}

	/** What keeps the player from learning the legacy technique (empty when nothing). */
	public static List<Component> missing(CultivationData data, Technique technique) {
		return LearnRules.missing(data, technique.spec());
	}

	/** The colour of a technique's Dao (gold for universal techniques). */
	public static int color(Technique technique) {
		return technique.dao() == null ? UNIVERSAL_COLOR : technique.dao().color();
	}

	/**
	 * What a book would do for the player now: the spec it teaches (null when none), the known art that would get the
	 * insight mastery (null when none) and the insight Dao (null when no comprehension is gained).
	 */
	public record Reading(@Nullable TechniqueSpec teaches, @Nullable TechniqueSpec known, @Nullable Dao insight, List<TechniqueSpec> pool) {
		/** True when reading the book does something (it is then consumed). */
		public boolean useful() {
			return teaches != null || known != null || insight != null;
		}
	}

	/** What reading a book with these stored enchantments does for the player (pure; the tooltip shows it too). */
	public static Reading reading(CultivationData data, ItemEnchantments stored) {
		List<TechniqueSpec> pool = BookPools.poolOf(stored);
		for (TechniqueSpec spec : pool) {
			if (LearnRules.canLearn(data, spec)) {
				return new Reading(spec, null, null, pool);
			}
		}
		TechniqueSpec known = null;
		for (TechniqueSpec spec : pool) {
			if (data.knows(spec.id())) {
				known = spec;
				break;
			}
		}
		Dao insight = null;
		Dao chosen = data.dao();
		if (known != null) {
			insight = known.dao() != null ? (known.dao() == chosen ? chosen : null) : chosen;
		}
		if (insight == null && chosen != null) {
			Set<BookPools.Family> families = BookPools.familiesOf(stored);
			for (BookPools.Family family : families) {
				if (family.dao() == chosen) {
					insight = chosen;
				}
			}
			for (TechniqueSpec spec : pool) {
				if (spec.dao() == chosen) {
					insight = chosen;
				}
			}
		}
		return new Reading(null, known, insight, pool);
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
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!BookPools.isTechniqueBook(stored)) {
			readPlain(player, stack);
			return InteractionResult.SUCCESS;
		}
		Reading reading = reading(data, stored);
		if (reading.teaches() != null) {
			consume(player, stack);
			LearnRules.learn(player, reading.teaches(), "book");
			Advancements.grant(player, "first_book");
			return InteractionResult.SUCCESS;
		}
		if (reading.useful()) {
			// Insight books per day (DESIGN 19.3.14): over the limit the comprehension is withheld; a book with nothing
			// else to give (no mastery for a known art) is kept.
			boolean capped = reading.insight() != null && Harta.bookInsightsToday(player) >= Harta.BOOK_INSIGHT_DAILY;
			if (capped && reading.known() == null) {
				Msg.chat(player, Component.translatable("kultivator.harta.book.capped", Harta.BOOK_INSIGHT_DAILY).withStyle(ChatFormatting.GRAY));
				player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS,
						0.8F, 0.7F);
				return InteractionResult.PASS;
			}
			insight(player, data, stack, reading, capped);
			return InteractionResult.SUCCESS;
		}

		// Nothing to take from it yet: say exactly why and keep the book.
		int explained = 0;
		for (TechniqueSpec spec : reading.pool()) {
			if (explained >= EXPLAIN_MAX) {
				break;
			}
			if (data.knows(spec.id())) {
				continue;
			}
			List<Component> missing = LearnRules.missing(data, spec);
			if (missing.isEmpty()) {
				continue;
			}
			Msg.chat(player, Component.translatable("kultivator.dao.book.cannot", LearnRules.name(spec), DaoPaths.join(missing))
					.withStyle(ChatFormatting.GRAY));
			explained++;
		}
		if (explained == 0) {
			Msg.chat(player, Component.translatable("kultivator.sumber.book.nothing").withStyle(ChatFormatting.GRAY));
		}
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS,
				0.8F, 0.7F);
		return InteractionResult.PASS;
	}

	/**
	 * Nothing new in the book: +5 comprehension and +15 mastery to a known art of the pool instead. {@code capped}: the
	 * daily insight limit is reached, so only the mastery is given.
	 */
	private static void insight(ServerPlayer player, CultivationData data, ItemStack stack, Reading reading, boolean capped) {
		consume(player, stack);
		Dao dao = reading.insight();
		TechniqueSpec known = reading.known();
		if (known != null) {
			Msg.chat(player, Component.translatable("kultivator.sumber.book.insight_known", LearnRules.name(known)).withStyle(ChatFormatting.YELLOW));
			Mastery.add(player, known.id(), INSIGHT_MASTERY, Mastery.Source.BOOK);
			Msg.chat(player, Component.translatable("kultivator.sumber.book.mastery", INSIGHT_MASTERY, known.displayName())
					.withStyle(ChatFormatting.GRAY));
		} else if (dao != null) {
			Msg.chat(player, Component.translatable("kultivator.sumber.book.insight_future", DaoPaths.colored(dao)).withStyle(ChatFormatting.YELLOW));
		}
		if (dao != null && capped) {
			Msg.chat(player, Component.translatable("kultivator.harta.book.capped", Harta.BOOK_INSIGHT_DAILY).withStyle(ChatFormatting.GRAY));
		} else if (dao != null) {
			double gained = Comprehension.grant(player, dao, INSIGHT);
			if (gained > 0.0D) {
				Harta.countBookInsight(player);
				Msg.chat(player, Component.translatable("kultivator.dao.book.insight_gain", Comprehension.format(gained), DaoPaths.colored(dao),
						Comprehension.format(data.comprehension(dao))).withStyle(ChatFormatting.GRAY));
			} else {
				// Comprehension is already complete: the words still nourish the cultivation.
				double xp = Progression.grantXp(player, PLAIN_BOOK_XP, XpSource.BOOK);
				Msg.chat(player, Component.translatable("kultivator.dao.book.insight_full", DaoPaths.colored(dao), Msg.number(Math.max(0.0D, xp)))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		for (TechniqueSpec spec : reading.pool()) {
			if (data.knows(spec.id()) || !LearnRules.daoFits(data, spec)) {
				continue;
			}
			List<Component> missing = LearnRules.missing(data, spec);
			if (!missing.isEmpty()) {
				Msg.chat(player, Component.translatable("kultivator.dao.book.still_missing", LearnRules.name(spec), DaoPaths.join(missing))
						.withStyle(ChatFormatting.DARK_GRAY));
			}
			break;
		}

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, dao == null ? -1 : dao.ordinal(), FX_INSIGHT, 0.0F);
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

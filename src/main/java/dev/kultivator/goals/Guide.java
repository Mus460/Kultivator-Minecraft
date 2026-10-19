package dev.kultivator.goals;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.spec.run.Loadout;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Langkah Pertama / First Steps (DESIGN 19.3.9 a): 20 steps in 5 chapters, a bitmask in {@code extra.goals.guide}.
 * A step completes when its advancement is granted ({@code Advancements.addListener}); on join every step is
 * back-filled from the advancements already done (and from the rank), so veterans are never told to test their root.
 * The two steps without an advancement (the Mortal bar, the Kitab Teknik with 4 seals) are checked every 100 ticks.
 * Chapter rewards (config {@code guideRewards}) are claimed with Action JALAN {@code claim:<chapter>}.
 * <p>
 * Phase 8 (DESIGN 25.8.2) adds chapters 6-10 "Jalan Dunia" with 20 more steps in a separate int {@code extra.goals.guide2}
 * (the type of {@code guide} is unchanged); their claimed bits are bits 5-9 of {@code claimed}. All 20 grant
 * {@code world_path_complete}. They complete only from advancements, back-filled on join.
 * <p>
 * Sumber: Terinspirasi dari Yao Lao's guidance of Xiao Yan (BTTH); chapter rewards Terinspirasi dari BTTH ch. 20
 * (Foundation Elixirs "tailored for you"). The steps are game design.
 */
public final class Guide {

	public static final String KEY = "guide";
	public static final String CLAIMED = "claimed";
	public static final String HIDDEN = "hidden";
	public static final String SEEN = "seen";
	public static final int CHAPTERS = 5;
	public static final int PER_CHAPTER = 4;
	public static final int STEP_COUNT = CHAPTERS * PER_CHAPTER;
	public static final int ALL = (1 << STEP_COUNT) - 1;
	/** Phase 8: chapters 6-10 in {@code guide2}. */
	public static final String KEY2 = "guide2";
	public static final int ALL_CHAPTERS = 10;
	public static final int ALL_STEPS = ALL_CHAPTERS * PER_CHAPTER;
	/** {@code extra.goals.world_title}: the cosmetic title "Penguasa Jalan Dunia" of chapter 10. */
	public static final String WORLD_TITLE = "world_title";
	/** Seals of the Cincin Segel the chapter-5 step asks for. */
	public static final int SEALS_NEEDED = 4;
	/** The power of the chapter-5 Shield Talisman. */
	public static final int SHIELD_POWER = 3;

	/** One step: its id (text keys) and the advancements that complete it (any one; none for the checked steps). */
	public record Step(String id, String... advancements) {
	}

	/** The steps in order: chapter = index / 4. */
	public static final Step[] STEPS = {
			new Step("root", "root_tested"), new Step("meditate", "first_meditation"), new Step("bar"), new Step("awaken", "awakening"),
			new Step("technique", "first_technique"), new Step("pill", "first_pill"), new Step("site", "spiritual_site", "qi_formation_small"),
			new Step("foundation", "first_breakthrough"),
			new Step("sword", "flying_sword"), new Step("alchemy", "first_alchemy"), new Step("stone", "first_spirit_stone"),
			new Step("tribulation", "first_tribulation"),
			new Step("dao", "dao_chosen"), new Step("book", "first_book", "jade_slip", "inheritance"), new Step("elite", "first_elite"),
			new Step("beast", "spirit_beast", "formation_built"),
			new Step("codex"), new Step("sect", "sect_founded", "first_recruit_hall"), new Step("disciple", "first_disciple"),
			new Step("expedition", "first_expedition")
	};
	/** Chapters 6-10 (DESIGN 25.8.2), each step completed by one phase-8 advancement. */
	public static final Step[] STEPS2 = {
			new Step("w_ring", "first_ring"), new Step("w_forge", "first_forge"), new Step("w_gate", "realm_gate"), new Step("w_pledge", "village_pledge"),
			new Step("w_ambush", "ambush_survivor"), new Step("w_raid", "raid_repelled"), new Step("w_natal", "natal_bond"),
			new Step("w_heavenvoid", "heavenvoid_hall"),
			new Step("w_langit", "artifact_langit"), new Step("w_spirit", "spirit_elementary"), new Step("w_crystal", "ancient_god_crystal"),
			new Step("w_patriarch", "demonic_patriarch"),
			new Step("w_refine", "refine_9"), new Step("w_tomb9", "heavenly_tomb_9"), new Step("w_vassals", "three_vassals"),
			new Step("w_ring4", "ring_supreme"),
			new Step("w_spirit3", "spirit_complete"), new Step("w_dewa", "artifact_dewa"), new Step("w_tomb81", "heavenly_tomb_81"),
			new Step("w_loyal", "village_loyal")
	};
	/** The realm each phase-8 chapter is meant for (FE, GC, DT, IA, TI), shown on the tablet. */
	public static final Realm[] CHAPTER_REALM = {Realm.FOUNDATION, Realm.GOLDEN_CORE, Realm.DEITY_TRANSFORMATION, Realm.IMMORTAL_ASCENSION,
			Realm.TRUE_IMMORTAL};
	private static final int BAR = 2;
	private static final int CODEX = 16;

	// ================================================================== pure readers

	public static int bits(CultivationData data) {
		return GoalCounters.view(data).getIntOr(KEY, 0) & ALL;
	}

	/** The phase-8 step bits of chapters 6-10 (bit i = step 20 + i). */
	public static int bits2(CultivationData data) {
		return GoalCounters.view(data).getIntOr(KEY2, 0) & ALL;
	}

	/** The step of any of the 40 indices (0-19 chapters 1-5, 20-39 chapters 6-10). */
	public static Step step(int index) {
		return index < STEP_COUNT ? STEPS[Math.max(0, index)] : STEPS2[Math.min(STEP_COUNT - 1, index - STEP_COUNT)];
	}

	/** The four step bits (0-15) of any chapter 0-9. */
	public static int chapterBits(CultivationData data, int chapter) {
		return chapter < CHAPTERS ? (bits(data) >> (chapter * PER_CHAPTER)) & 0xF : (bits2(data) >> ((chapter - CHAPTERS) * PER_CHAPTER)) & 0xF;
	}

	/** True when step {@code index} (0-39) is done. */
	public static boolean stepDone(CultivationData data, int index) {
		return index < STEP_COUNT ? (bits(data) & (1 << index)) != 0 : (bits2(data) & (1 << (index - STEP_COUNT))) != 0;
	}

	/** Steps done in any chapter 0-9. */
	public static int doneInAny(CultivationData data, int chapter) {
		return Integer.bitCount(chapterBits(data, chapter));
	}

	/** True when every step of chapter 0-9 is done. */
	public static boolean chapterDoneAny(CultivationData data, int chapter) {
		return chapterBits(data, chapter) == 0xF;
	}

	/** The first step left among all 40 (0-39), or -1 when everything is done. */
	public static int currentStepAny(CultivationData data) {
		int first = currentStep(bits(data));
		if (first >= 0) {
			return first;
		}
		int second = currentStep(bits2(data));
		return second < 0 ? -1 : STEP_COUNT + second;
	}

	/** The first chapter 0-9 with a step left (10 when all are done). */
	public static int currentChapterAny(CultivationData data) {
		for (int chapter = 0; chapter < ALL_CHAPTERS; chapter++) {
			if (!chapterDoneAny(data, chapter)) {
				return chapter;
			}
		}
		return ALL_CHAPTERS;
	}

	/** Steps done of all 40. */
	public static int totalDone(CultivationData data) {
		return Integer.bitCount(bits(data)) + Integer.bitCount(bits2(data));
	}

	public static boolean worldTitle(CultivationData data) {
		return GoalCounters.view(data).getBooleanOr(WORLD_TITLE, false);
	}

	public static int claimed(CultivationData data) {
		return GoalCounters.view(data).getIntOr(CLAIMED, 0);
	}

	public static boolean hidden(CultivationData data) {
		return GoalCounters.view(data).getBooleanOr(HIDDEN, false);
	}

	public static boolean seen(CultivationData data) {
		return GoalCounters.view(data).getBooleanOr(SEEN, false);
	}

	/** Bits of the four steps of {@code chapter} (0-4). */
	public static int chapterMask(int chapter) {
		return 0xF << (chapter * PER_CHAPTER);
	}

	public static boolean chapterDone(int bits, int chapter) {
		return (bits & chapterMask(chapter)) == chapterMask(chapter);
	}

	/** Steps done in {@code chapter}. */
	public static int doneIn(int bits, int chapter) {
		return Integer.bitCount(bits & chapterMask(chapter));
	}

	/** The first chapter (0-4) with a step left, or 5 when all are done. */
	public static int currentChapter(int bits) {
		for (int chapter = 0; chapter < CHAPTERS; chapter++) {
			if (!chapterDone(bits, chapter)) {
				return chapter;
			}
		}
		return CHAPTERS;
	}

	/** The first step left (0-19), or -1 when all are done. */
	public static int currentStep(int bits) {
		for (int i = 0; i < STEP_COUNT; i++) {
			if ((bits & (1 << i)) == 0) {
				return i;
			}
		}
		return -1;
	}

	/** Filled seals of the Cincin Segel. */
	public static int seals(CultivationData data) {
		int filled = 0;
		for (String id : Loadout.slots(data)) {
			if (!id.isEmpty()) {
				filled++;
			}
		}
		return filled;
	}

	// ================================================================== server

	/** An advancement was completed: the steps it belongs to. */
	static void onAdvancement(ServerPlayer player, String name) {
		int add = 0;
		for (int i = 0; i < STEPS.length; i++) {
			for (String advancement : STEPS[i].advancements()) {
				if (advancement.equals(name)) {
					add |= 1 << i;
				}
			}
		}
		if (add != 0) {
			mark(player, Cultivators.get(player), add);
		}
		int add2 = 0;
		for (int i = 0; i < STEPS2.length; i++) {
			for (String advancement : STEPS2[i].advancements()) {
				if (advancement.equals(name)) {
					add2 |= 1 << i;
				}
			}
		}
		if (add2 != 0) {
			mark2(player, Cultivators.get(player), add2);
		}
	}

	/** On join: every step whose advancement is already done (or that the rank proves) is ticked. */
	static void backfill(ServerPlayer player, CultivationData data) {
		int add = 0;
		for (int i = 0; i < STEPS.length; i++) {
			for (String advancement : STEPS[i].advancements()) {
				if (RealmTrials.done(player, advancement)) {
					add |= 1 << i;
				}
			}
		}
		Realm realm = data.realm();
		if (data.rootTested()) {
			add |= 1;
		}
		if (realm.atLeast(Realm.QI_REFINING)) {
			add |= 1 << 3;
		}
		if (realm.atLeast(Realm.FOUNDATION)) {
			add |= 1 << 7;
		}
		if (realm.atLeast(Realm.GOLDEN_CORE)) {
			add |= 1 << 11;
		}
		if (data.dao() != null) {
			add |= 1 << 12;
		}
		mark(player, data, add | checked(data));
		int add2 = 0;
		for (int i = 0; i < STEPS2.length; i++) {
			for (String advancement : STEPS2[i].advancements()) {
				if (RealmTrials.done(player, advancement)) {
					add2 |= 1 << i;
				}
			}
		}
		mark2(player, data, add2);
	}

	/** Every 100 ticks: the two steps without an advancement. */
	static void check(ServerPlayer player, CultivationData data) {
		int add = checked(data);
		if ((add & ~bits(data)) != 0) {
			mark(player, data, add);
		}
	}

	private static int checked(CultivationData data) {
		int add = 0;
		if (!data.realm().isMortal() || data.barFull()) {
			add |= 1 << BAR;
		}
		if (seen(data) && seals(data) >= SEALS_NEEDED) {
			add |= 1 << CODEX;
		}
		return add;
	}

	/** Sets step bits; tells about finished chapters and grants {@code guide_complete} after the last step. */
	static void mark(ServerPlayer player, CultivationData data, int add) {
		CompoundTag goals = GoalCounters.goals(data);
		int before = goals.getIntOr(KEY, 0) & ALL;
		int after = (before | add) & ALL;
		if (after == before) {
			return;
		}
		goals.putInt(KEY, after);
		Cultivators.dirty(player);
		// Every chapter finished by this change in one line (a veteran's first join can finish several at once).
		MutableComponent names = Component.empty();
		int finished = 0;
		boolean reward = false;
		for (int chapter = 0; chapter < CHAPTERS; chapter++) {
			if (chapterDone(after, chapter) && !chapterDone(before, chapter)) {
				if (finished > 0) {
					names.append(Component.literal(", "));
				}
				names.append(Component.translatable("kultivator.goals.chapter." + (chapter + 1)));
				finished++;
				reward |= KultivatorConfig.get().guideRewards && (claimed(data) & (1 << chapter)) == 0;
			}
		}
		if (finished > 0) {
			Msg.chat(player, Component.translatable(reward ? "kultivator.goals.guide.chapter_done_reward" : "kultivator.goals.guide.chapter_done",
					names).withStyle(ChatFormatting.GOLD));
		}
		if (after == ALL && before != ALL) {
			Advancements.grant(player, "guide_complete");
		}
	}

	/** Sets phase-8 step bits (chapters 6-10); tells about finished chapters and grants {@code world_path_complete}. */
	static void mark2(ServerPlayer player, CultivationData data, int add) {
		CompoundTag goals = GoalCounters.goals(data);
		int before = goals.getIntOr(KEY2, 0) & ALL;
		int after = (before | add) & ALL;
		if (after == before) {
			return;
		}
		goals.putInt(KEY2, after);
		Cultivators.dirty(player);
		MutableComponent names = Component.empty();
		int finished = 0;
		boolean reward = false;
		for (int chapter = 0; chapter < CHAPTERS; chapter++) {
			if (chapterDone(after, chapter) && !chapterDone(before, chapter)) {
				if (finished > 0) {
					names.append(Component.literal(", "));
				}
				names.append(Component.translatable("kultivator.goals.chapter." + (chapter + CHAPTERS + 1)));
				finished++;
				reward |= KultivatorConfig.get().guideRewards && (claimed(data) & (1 << (chapter + CHAPTERS))) == 0;
			}
		}
		if (finished > 0) {
			Msg.chat(player, Component.translatable(reward ? "kultivator.goals.guide.chapter_done_reward" : "kultivator.goals.guide.chapter_done",
					names).withStyle(ChatFormatting.GOLD));
		}
		if (after == ALL && before != ALL) {
			Advancements.grant(player, "world_path_complete");
		}
	}

	/** Action JALAN {@code claim:<chapter>} (1-10): the chapter's reward into the inventory or at the feet. */
	static void claim(ServerPlayer player, int chapterNumber) {
		int chapter = chapterNumber - 1;
		CultivationData data = Cultivators.get(player);
		if (chapter < 0 || chapter >= ALL_CHAPTERS) {
			return;
		}
		if (!KultivatorConfig.get().guideRewards) {
			Msg.chat(player, Component.translatable("kultivator.goals.guide.rewards_off").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!chapterDoneAny(data, chapter)) {
			Msg.chat(player, Component.translatable("kultivator.goals.guide.not_done").withStyle(ChatFormatting.RED));
			return;
		}
		CompoundTag goals = GoalCounters.goals(data);
		int claimed = goals.getIntOr(CLAIMED, 0);
		if ((claimed & (1 << chapter)) != 0) {
			Msg.chat(player, Component.translatable("kultivator.goals.guide.already").withStyle(ChatFormatting.GRAY));
			return;
		}
		goals.putInt(CLAIMED, claimed | (1 << chapter));
		Cultivators.dirty(player);
		List<ItemStack> stacks = rewards(chapter, data, player.getRandom());
		for (ItemStack stack : stacks) {
			give(player, stack);
		}
		if (chapter == ALL_CHAPTERS - 1) {
			goals.putBoolean(WORLD_TITLE, true);
			Msg.chat(player, Component.translatable("kultivator.jalan8.title_won").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.3F);
		Msg.chat(player, Component.translatable("kultivator.goals.guide.claimed", Component.translatable("kultivator.goals.chapter." + chapterNumber))
				.withStyle(ChatFormatting.GOLD));
	}

	/** The stacks of a chapter's reward (0-4); chapter 4 picks a Kuning slip of the chosen Dao the player lacks. */
	static List<ItemStack> rewards(int chapter, CultivationData data, RandomSource random) {
		List<ItemStack> list = new ArrayList<>();
		switch (chapter) {
			case 0 -> list.add(pill(AlchemyItems.QI_GATHERING_PILL, 2));
			case 1 -> {
				list.add(pill(AlchemyItems.HEALING_PILL, 1));
				list.add(pill(AlchemyItems.QI_RECOVERY_PILL, 1));
			}
			case 2 -> {
				list.add(pill(AlchemyItems.TRIBULATION_WARDING_PILL, 1));
				list.add(SpiritStones.stack(SpiritStones.LOW, 4));
			}
			case 3 -> {
				list.add(SpiritStones.stack(SpiritStones.MID, 1));
				ItemStack slip = slipFor(data, random);
				list.add(slip.isEmpty() ? SpiritStones.stack(SpiritStones.MID, 2) : slip);
			}
			case 4 -> {
				list.add(SpiritStones.stack(SpiritStones.MID, 2));
				ItemStack shield = shieldTalisman();
				list.add(shield.isEmpty() ? SpiritStones.stack(SpiritStones.MID, 1) : shield);
			}
			// Phase 8 (DESIGN 25.8.2): module A items by registry id, a spirit stone while one is not registered.
			case 5 -> {
				list.add(byId("token_darah_api", 1, SpiritStones.stack(SpiritStones.LOW, 2)));
				list.add(SpiritStones.stack(SpiritStones.LOW, 4));
			}
			case 6 -> {
				list.add(SpiritStones.stack(SpiritStones.MID, 2));
				list.add(byId("heavenvoid_fragment", 1, SpiritStones.stack(SpiritStones.MID, 1)));
			}
			case 7 -> {
				list.add(SpiritStones.stack(SpiritStones.HIGH, 1));
				list.add(byId("ancient_god_blood", 1, SpiritStones.stack(SpiritStones.HIGH, 1)));
			}
			case 8 -> {
				list.add(SpiritStones.stack(SpiritStones.SUPREME, 1));
				list.add(byId("tomb_sigil", 1, SpiritStones.stack(SpiritStones.HIGH, 2)));
			}
			default -> list.add(byId("tomb_essence", 3, SpiritStones.stack(SpiritStones.SUPREME, 1)));
		}
		list.removeIf(ItemStack::isEmpty);
		return list;
	}

	/** {@code count} of {@code kultivator:<path>}, or {@code fallback} while that item is not registered. */
	private static ItemStack byId(String path, int count, ItemStack fallback) {
		Item item = BuiltInRegistries.ITEM.getValue(dev.kultivator.Kultivator.id(path));
		return item == Items.AIR ? fallback : new ItemStack(item, count);
	}

	private static ItemStack pill(Item item, int count) {
		return AlchemyComponents.withQuality(new ItemStack(item, count), PillQuality.MID);
	}

	/** A jade slip of a random Kuning (Huang) spec of the chosen Dao that the player does not know yet, or empty. */
	private static ItemStack slipFor(CultivationData data, RandomSource random) {
		Dao dao = data.dao();
		if (dao == null) {
			return ItemStack.EMPTY;
		}
		List<TechniqueSpec> candidates = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.dao() == dao && spec.grade() == TechniqueGrade.HUANG && spec.slipAllowed() && spec.playerVisible() && !data.knows(spec.id())) {
				candidates.add(spec);
			}
		}
		while (!candidates.isEmpty()) {
			ItemStack slip = JadeSlips.stack(candidates.remove(random.nextInt(candidates.size())).id());
			if (!slip.isEmpty()) {
				return slip;
			}
		}
		return ItemStack.EMPTY;
	}

	/** One Shield Talisman of power {@link #SHIELD_POWER} (DESIGN 19.3.9), without an inscriber name. */
	private static ItemStack shieldTalisman() {
		return Talismans.create(TalismanKind.SHIELD, SHIELD_POWER, "");
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			player.level().addFreshEntity(item);
		}
	}

	/** {@code uji jalan bab <n>} (1-10): completes chapters 1..n. */
	static void completeUpTo(ServerPlayer player, int chapters) {
		int add = 0;
		for (int chapter = 0; chapter < Math.min(CHAPTERS, chapters); chapter++) {
			add |= chapterMask(chapter);
		}
		mark(player, Cultivators.get(player), add);
		int add2 = 0;
		for (int chapter = CHAPTERS; chapter < Math.min(ALL_CHAPTERS, chapters); chapter++) {
			add2 |= chapterMask(chapter - CHAPTERS);
		}
		if (add2 != 0) {
			mark2(player, Cultivators.get(player), add2);
		}
	}

	private Guide() {
	}
}

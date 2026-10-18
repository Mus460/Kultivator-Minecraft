package dev.kultivator.harta;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * P7-harta (DESIGN 19.3.2-19.3.4, 19.3.7, 19.3.13, 19.3.14): spirit stones ({@link SpiritStones}), beast core uses
 * ({@link BeastCores}), aged spirit herbs ({@link SpiritHerbs}), deeper alchemy (in {@code alchemy/*}), structure relics
 * ({@link HartaLoot}) and the item balance rules. {@link #init()} runs before {@code ModItems.init()} and never reads
 * {@code TechniqueRegistry} (the jade-slip pools read it inside the loot callback).
 * <p>
 * Saved data in {@code extra.harta} (DESIGN 19.9): {@code herbs_found}, {@code refined}, {@code insight_pills
 * {realmId: int}}, {@code bone_hp}, {@code book_day}, {@code book_insight}, and {@code regen_until} (the Supreme stone).
 */
public final class Harta {

	/** Sub-compound of {@code CultivationData.extra()} owned by this module. */
	public static final String EXTRA = "harta";
	public static final String REFINED = "refined";
	public static final String INSIGHT_PILLS = "insight_pills";
	public static final String BONE_HP = "bone_hp";
	public static final String BOOK_DAY = "book_day";
	public static final String BOOK_INSIGHT = "book_insight";
	/** Insight books (the uncapped +5 comprehension) a player may read per in-game day (DESIGN 19.3.14). */
	public static final int BOOK_INSIGHT_DAILY = 4;
	/** The Bone Tempering Pill's max-health modifier. */
	public static final Identifier BONE_TEMPERING_ID = Kultivator.id("bone_tempering");

	private static final int REGEN_INTERVAL = 20;
	private static final int SCAN_INTERVAL = 100;

	public static void init() {
		HartaComponents.init();
		HartaItems.init();
		SpiritStones.init();
		SpiritHerbs.init();
		BeastCores.init();
		HartaLoot.init();
		Breakthrough.addQualityBonusProvider(new StoneBottleneck());
		DiscipleHooks.addFeedHandler(Harta::refuseNewPills);
		ServerTickEvents.END_SERVER_TICK.register(Harta::tickServer);
		ServerPlayerEvents.JOIN.register(Harta::applyBoneTempering);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyBoneTempering(newPlayer));
		ServerPlayerEvents.LEAVE.register(player -> SpiritHerbs.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SpiritHerbs.clear();
			BeastCores.clear();
		});
	}

	/** {@code /kultivasi resep} (opens the Kitab Alkimia tab, DESIGN 19.2.2) plus the module's player and debug commands. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		HartaCommands.addCommands(root, debug);
	}

	/** {@code uji batu|herba|inti|pil|relik ...} (DESIGN 19.4). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		HartaCommands.addTestCommands(uji);
	}

	/**
	 * The phase-7 stacks of {@code uji kit}: 16 of each stone, every herb at age 100 (four each) and one at 1,200, one
	 * core per grade (random elements) and the four new pills (Perfect).
	 */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		for (int grade = SpiritStones.LOW; grade <= SpiritStones.SUPREME; grade++) {
			list.add(SpiritStones.stack(grade, 16));
		}
		for (HerbKind kind : HerbKind.values()) {
			list.add(SpiritHerbs.create(kind, SpiritHerbs.DEFAULT_AGE).copyWithCount(4));
		}
		list.add(SpiritHerbs.create(HerbKind.GINSENG_FRUIT, 1200));
		RandomSource random = RandomSource.create();
		Element[] elements = Element.values();
		for (int grade = BeastCores.LOW; grade <= BeastCores.KING; grade++) {
			list.add(BeastCores.create(elements[random.nextInt(elements.length)], grade));
		}
		for (PillKind kind : new PillKind[]{PillKind.DAO_INSIGHT, PillKind.TOXIN_PURGING, PillKind.ENLIGHTENMENT, PillKind.BONE_TEMPERING}) {
			list.add(AlchemyComponents.withQuality(new ItemStack(AlchemyItems.get(kind), 4), PillQuality.PERFECT));
		}
		return list;
	}

	/** Contribution points one item of {@code stack} is worth at the Kantor Misi (0 = not a phase-7 item). */
	public static double missionValue(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0D;
		}
		Item item = stack.getItem();
		if (item instanceof SpiritStoneItem stone) {
			return SpiritStones.MISSION_PK[stone.grade()];
		}
		if (item instanceof BeastCoreItem core) {
			return BeastCores.MISSION_PK[core.grade() - 1];
		}
		if (item instanceof SpiritHerbItem) {
			return SpiritHerbs.missionValue(SpiritHerbs.age(stack));
		}
		return 0.0D;
	}

	// ================================================================ extra.harta

	/** The module's live {@code extra.harta} compound (created when missing); call {@code Cultivators.dirty} after changes. */
	public static CompoundTag extra(CultivationData data) {
		CompoundTag own = data.extra().getCompound(EXTRA).orElse(null);
		if (own == null) {
			own = new CompoundTag();
			data.extra().put(EXTRA, own);
		}
		return own;
	}

	/** One successful refining (read by the Foundation Trials and the daily chores). */
	public static void countRefined(ServerPlayer player) {
		CompoundTag own = extra(Cultivators.get(player));
		own.putInt(REFINED, own.getIntOr(REFINED, 0) + 1);
		Cultivators.dirty(player);
	}

	/** Dao Insight Pills taken in {@code realm}. */
	public static int insightPills(CultivationData data, Realm realm) {
		return data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty(INSIGHT_PILLS).getIntOr(realm.id(), 0);
	}

	public static void addInsightPill(CultivationData data, Realm realm) {
		CompoundTag own = extra(data);
		CompoundTag pills = own.getCompoundOrEmpty(INSIGHT_PILLS);
		pills.putInt(realm.id(), pills.getIntOr(realm.id(), 0) + 1);
		own.put(INSIGHT_PILLS, pills);
	}

	/** Max health the Bone Tempering Pills added so far. */
	public static int boneHealth(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getIntOr(BONE_HP, 0);
	}

	public static void setBoneHealth(ServerPlayer player, int hp) {
		extra(Cultivators.get(player)).putInt(BONE_HP, Math.max(0, hp));
		Cultivators.dirty(player);
		applyBoneTempering(player);
	}

	/** Re-applies the permanent {@code kultivator:bone_tempering} modifier (join, respawn, after a pill). */
	public static void applyBoneTempering(ServerPlayer player) {
		AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return;
		}
		int hp = boneHealth(Cultivators.get(player));
		if (hp <= 0) {
			instance.removeModifier(BONE_TEMPERING_ID);
			return;
		}
		AttributeModifier current = instance.getModifier(BONE_TEMPERING_ID);
		if (current == null || Math.abs(current.amount() - hp) > 1.0E-9D) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(BONE_TEMPERING_ID, hp, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Insight books read today (0 when the last one was on another day). */
	public static int bookInsightsToday(ServerPlayer player) {
		CompoundTag own = Cultivators.get(player).extra().getCompoundOrEmpty(EXTRA);
		return own.getLongOr(BOOK_DAY, -1L) == SpiritHerbs.day(player.level()) ? own.getIntOr(BOOK_INSIGHT, 0) : 0;
	}

	/** Counts one insight book for today. */
	public static void countBookInsight(ServerPlayer player) {
		CompoundTag own = extra(Cultivators.get(player));
		long day = SpiritHerbs.day(player.level());
		int today = own.getLongOr(BOOK_DAY, -1L) == day ? own.getIntOr(BOOK_INSIGHT, 0) : 0;
		own.putLong(BOOK_DAY, day);
		own.putInt(BOOK_INSIGHT, today + 1);
		Cultivators.dirty(player);
	}

	// ================================================================ hooks

	/** Disciples cannot digest the four phase-7 pills yet (Phase 7b): refused, and the pill is kept. */
	private static boolean refuseNewPills(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		if (stack.getItem() instanceof PillItem pill && pill.kind().phase7()) {
			Msg.actionBar(master, Component.translatable("kultivator.harta.pill.disciple_refused", stack.getHoverName(), entity.getDisplayName())
					.withStyle(ChatFormatting.YELLOW));
			return true;
		}
		return false;
	}

	private static void tickServer(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % REGEN_INTERVAL != 0) {
			return;
		}
		boolean scan = tick % SCAN_INTERVAL == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isRemoved()) {
				continue;
			}
			try {
				SpiritStones.regenTick(player, Cultivators.get(player));
				if (scan) {
					scanInventory(player);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Harta tick failed for {}", player.getPlainTextName(), exception);
			}
		}
	}

	/** Grants the "found one" advancements for stones, herbs and cores the player holds, however they came. */
	private static void scanInventory(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			if (item instanceof SpiritStoneItem stone) {
				Advancements.grant(player, "first_spirit_stone");
				if (stone.grade() >= SpiritStones.SUPREME) {
					Advancements.grant(player, "supreme_spirit_stone");
				}
			} else if (item instanceof SpiritHerbItem) {
				SpiritHerbs.checkAdvancements(player, stack);
			} else if (item instanceof BeastCoreItem) {
				BeastCores.checkAdvancements(player, stack);
			}
		}
	}

	private Harta() {
	}
}

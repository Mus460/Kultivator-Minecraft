package dev.kultivator.technique.source;

import dev.kultivator.Kultivator;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.sect.hall.HallItems;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where techniques are found (DESIGN 22.8):
 * <ul>
 * <li>Giok Warisan in the built-in chests (ancient city 6%, end city 8%, bastion treasure 6%, stronghold library 5%,
 * ominous trial vault 5%; a random theme) and from first boss kills: Warden (Shadow), Ender Dragon (Space), Elder Guardian
 * (Ice) always on a player's first kill, 15% later;</li>
 * <li>the player's use of a Benih Api Surgawi (sneak + use): it teaches the spec whose ITEM route names its flame
 * ({@code heavenly_flame_seed:bone_chilling});</li>
 * <li>the lost scripture set 4 "Kitab Penyegel Iblis" (pages of {@code lost_scripture_page} set 4): using a page reads
 * every page of the set the player carries and teaches each spec whose SCR route needs at most that many different pages
 * (pages are kept).</li>
 * </ul>
 */
public final class TechniqueLoot {

	public static final float ANCIENT_CITY = 0.06F;
	public static final float END_CITY = 0.08F;
	public static final float BASTION = 0.06F;
	public static final float STRONGHOLD = 0.05F;
	public static final float OMINOUS_VAULT = 0.05F;
	/** Chance of a jade from a boss after the player's first kill of it. */
	public static final double BOSS_LATER = 0.15D;
	/** The lost scripture set of the Sealing Demon Codex (set 4 of {@code kultivator:scripture_page}). */
	public static final int SCRIPTURE_SET = 4;
	public static final int SCRIPTURE_PIECES = 5;
	private static final Identifier FLAME_SEED = Kultivator.id("heavenly_flame_seed");
	private static final Identifier FLAME_KIND = Kultivator.id("flame_kind");
	private static final String ITEM_PREFIX = "heavenly_flame_seed:";
	private static final String PREFIX = "kultivator.sumber.";

	/** Bosses whose first kill gives an inheritance jade of a fixed theme. */
	private static final Map<EntityType<?>, String> BOSS_THEMES = new LinkedHashMap<>();

	static {
		BOSS_THEMES.put(EntityTypes.WARDEN, "shadow");
		BOSS_THEMES.put(EntityTypes.ENDER_DRAGON, "space");
		BOSS_THEMES.put(EntityTypes.ELDER_GUARDIAN, "ice");
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			float chance = chanceFor(key);
			if (chance > 0.0F) {
				addJadePool(builder, chance);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(TechniqueLoot::afterDeath);
		UseItemCallback.EVENT.register(TechniqueLoot::onUseItem);
	}

	// ================================================================== chests

	private static float chanceFor(ResourceKey<LootTable> key) {
		if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
			return ANCIENT_CITY;
		}
		if (key.equals(BuiltInLootTables.END_CITY_TREASURE)) {
			return END_CITY;
		}
		if (key.equals(BuiltInLootTables.BASTION_TREASURE)) {
			return BASTION;
		}
		if (key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
			return STRONGHOLD;
		}
		if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) {
			return OMINOUS_VAULT;
		}
		return 0.0F;
	}

	/** One jade of a random theme (equal weights) with {@code chance}. */
	private static void addJadePool(LootTable.Builder builder, float chance) {
		LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(chance));
		for (String theme : InheritanceTrials.THEMES) {
			pool.add(LootItem.lootTableItem(InheritanceTrials.GIOK_WARISAN)
					.apply(SetComponentsFunction.setComponent(InheritanceTrials.THEME, theme)));
		}
		builder.withPool(pool);
	}

	// ================================================================== bosses

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		String theme = BOSS_THEMES.get(victim.getType());
		if (theme == null || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer player = killer(victim, source);
		if (player == null || player instanceof FakePlayer) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		CompoundTag mine = InheritanceTrials.section(data);
		ListTag first = mine.getListOrEmpty("first").copy();
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		boolean killedBefore = false;
		for (Tag tag : first) {
			killedBefore |= id.equals(tag.asString().orElse(""));
		}
		if (killedBefore && victim.getRandom().nextDouble() >= BOSS_LATER) {
			return;
		}
		if (!killedBefore) {
			first.add(StringTag.valueOf(id));
			mine.put("first", first);
			Cultivators.dirty(player);
		}
		// The dragon dies over the void's edge: its jade appears at the killer's feet.
		boolean atPlayer = victim.getType() == EntityTypes.ENDER_DRAGON && player.level() == level;
		double x = atPlayer ? player.getX() : victim.getX();
		double y = atPlayer ? player.getY() + 0.5D : victim.getY() + victim.getBbHeight() * 0.5D;
		double z = atPlayer ? player.getZ() : victim.getZ();
		ItemEntity item = new ItemEntity(level, x, y, z, InheritanceTrials.jade(theme));
		item.setDefaultPickUpDelay();
		item.setGlowingTag(true);
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		Msg.chat(player, Component.translatable(PREFIX + "trial.dropped", InheritanceTrials.themeName(theme)).withStyle(ChatFormatting.GOLD));
	}

	private static @Nullable ServerPlayer killer(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			return player;
		}
		return victim.getKillCredit() instanceof ServerPlayer player ? player : null;
	}

	// ================================================================== flame seeds and scripture pages

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean seed = isFlameSeed(stack);
		boolean scripture = scriptureSet(stack) == SCRIPTURE_SET;
		if (!seed && !scripture || seed && !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) {
			return InteractionResult.PASS;
		}
		try {
			return seed ? useSeed(serverPlayer, stack) : readScripture(serverPlayer);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: could not use {}", stack, exception);
			return InteractionResult.PASS;
		}
	}

	/** True for a Benih Api Surgawi (module C's {@code heavenly_flame_seed}). */
	public static boolean isFlameSeed(ItemStack stack) {
		return !stack.isEmpty() && FLAME_SEED.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/** The flame id of a seed ({@code bone_chilling}), "" when unknown. */
	public static String flameOf(ItemStack stack) {
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(FLAME_KIND);
		Object value = type == null ? null : stack.get(type);
		int kind = value instanceof Integer number ? number : 0;
		return kind >= 0 && kind < PowerIds.FLAME_IDS.length ? PowerIds.FLAME_IDS[kind] : "";
	}

	/** The specs an ITEM route of this seed's flame teaches (catalogue order). */
	public static List<TechniqueSpec> seedSpecs(ItemStack stack) {
		String key = ITEM_PREFIX + flameOf(stack);
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			for (Route route : spec.routes()) {
				if (route.kind() == Route.Kind.ITEM && key.equals(route.key()) && !list.contains(spec)) {
					list.add(spec);
				}
			}
		}
		return list;
	}

	private static InteractionResult useSeed(ServerPlayer player, ItemStack stack) {
		List<TechniqueSpec> specs = seedSpecs(stack);
		if (specs.isEmpty()) {
			Msg.chat(player, Component.translatable(PREFIX + "seed.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.PASS;
		}
		CultivationData data = Cultivators.get(player);
		for (TechniqueSpec spec : specs) {
			if (LearnRules.canLearn(data, spec)) {
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
				player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
						0.8F, 0.6F);
				LearnRules.learn(player, spec, "seed");
				return InteractionResult.SUCCESS;
			}
		}
		for (TechniqueSpec spec : specs) {
			if (data.knows(spec.id())) {
				Msg.chat(player, Component.translatable(PREFIX + "seed.known", LearnRules.name(spec)).withStyle(ChatFormatting.GRAY));
			} else {
				Msg.chat(player, Component.translatable("kultivator.dao.book.cannot", LearnRules.name(spec),
						DaoPaths.join(LearnRules.missing(data, spec))).withStyle(ChatFormatting.GRAY));
			}
		}
		return InteractionResult.PASS;
	}

	/** The scripture set of a lost page (0 for other items). */
	public static int scriptureSet(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(HallItems.LOST_SCRIPTURE_PAGE)) {
			return 0;
		}
		HallItems.ScripturePage page = stack.get(HallItems.SCRIPTURE_PAGE);
		return page == null ? 0 : page.set();
	}

	/** How many different pages (1-5) of the Sealing Demon Codex the player carries. */
	public static int scripturePages(Player player) {
		int bits = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			HallItems.ScripturePage page = stack.is(HallItems.LOST_SCRIPTURE_PAGE) ? stack.get(HallItems.SCRIPTURE_PAGE) : null;
			if (page != null && page.set() == SCRIPTURE_SET) {
				bits |= 1 << (page.piece() - 1);
			}
		}
		return Integer.bitCount(bits);
	}

	/** The SCR page count a spec needs (0 when it has no SCR route). */
	public static int scriptureNeed(TechniqueSpec spec) {
		int need = 0;
		for (Route route : spec.routes()) {
			if (route.kind() == Route.Kind.SCR) {
				need = need == 0 ? route.n() : Math.min(need, route.n());
			}
		}
		return need;
	}

	private static InteractionResult readScripture(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		int pages = scripturePages(player);
		Msg.chat(player, Component.translatable(PREFIX + "scripture.read", pages, SCRIPTURE_PIECES).withStyle(ChatFormatting.LIGHT_PURPLE));
		boolean learned = false;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			int need = scriptureNeed(spec);
			if (need <= 0 || data.knows(spec.id())) {
				continue;
			}
			if (pages < need) {
				Msg.chat(player, Component.translatable(PREFIX + "scripture.need", LearnRules.name(spec), need).withStyle(ChatFormatting.DARK_GRAY));
				continue;
			}
			List<Component> missing = LearnRules.missing(data, spec);
			if (!missing.isEmpty()) {
				Msg.chat(player, Component.translatable("kultivator.dao.book.cannot", LearnRules.name(spec), DaoPaths.join(missing))
						.withStyle(ChatFormatting.GRAY));
				continue;
			}
			learned |= LearnRules.learn(player, spec, "scripture");
		}
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		return learned ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	private TechniqueLoot() {
	}
}

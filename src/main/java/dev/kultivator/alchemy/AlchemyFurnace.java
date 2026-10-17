package dev.kultivator.alchemy;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Tungku Alkimia (DESIGN section 9): any cauldron standing on a lit campfire or soul campfire. Everything here only
 * READS the two blocks; the cauldron's contents and the fire are never changed. Ingredients, the refining timer and the
 * refiner live in {@link AlchemyPots}.
 * <p>
 * Using the cauldron (main hand only): an ingredient adds one item to the pot, sneak + empty hand starts refining,
 * an empty hand shows the pot only when the off hand is empty too. Every other item (buckets, bottles, potions, dyed
 * armour, banners...) keeps its vanilla cauldron behaviour, and so does anything held in the off hand (its cauldron
 * interaction, or its own use such as food, torches and shields). Results and spilled ingredients are dropped beside
 * the cauldron, never into it (a lava cauldron would burn them).
 */
public final class AlchemyFurnace {

	/** A refining lasts 10 seconds. */
	public static final int REFINE_TICKS = 200;
	/** Pots are processed this often. */
	public static final int PROCESS_INTERVAL = 20;
	public static final double BASE_CHANCE = 0.6D;
	public static final double CHANCE_PER_LEVEL = 0.04D;
	public static final double SOUL_FIRE_CHANCE = 0.1D;
	public static final double FIRE_DAO_CHANCE = 0.1D;
	public static final double MAX_CHANCE = 0.95D;
	/** Quality roll shift per alchemy level (towards Perfect). */
	private static final double QUALITY_PER_LEVEL = 0.03D;
	/** Soul fire raises the quality one grade with this chance. */
	private static final double SOUL_FIRE_UPGRADE = 0.25D;
	/** Alchemy XP for a mixture that matches no recipe. */
	private static final double UNKNOWN_XP = 2.0D;
	/** Share of the recipe's alchemy XP kept when the refining fails. */
	private static final double FAILED_XP_SHARE = 0.25D;
	/** Extra alchemy XP per quality grade above Low (x recipe XP). */
	private static final double QUALITY_XP = 0.2D;

	private static final int POT_COLOR = 0xE8D9A8;
	private static final int BUSY_COLOR = 0xFFB060;
	private static final int FAIL_COLOR = 0xB0A8A0;

	private enum Use { ADD, REFINE, SHOW }

	// ================================================================ detection (read-only)

	/** True when {@code pos} is a cauldron (any content) on a lit campfire or soul campfire. Only reads blocks. */
	public static boolean isFurnace(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractCauldronBlock) && !state.is(BlockTags.CAULDRONS)) {
			return false;
		}
		return CampfireBlock.isLitCampfire(level.getBlockState(pos.below()));
	}

	/** The furnace burns on a soul campfire (better chance and quality). */
	public static boolean soulFire(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(Blocks.SOUL_CAMPFIRE);
	}

	/** True when vanilla (or another mod) lets {@code stack} do something with any kind of cauldron. */
	public static boolean hasCauldronInteraction(ItemStack stack) {
		return CauldronInteractions.EMPTY.get(stack) != CauldronInteraction.DEFAULT
				|| CauldronInteractions.WATER.get(stack) != CauldronInteraction.DEFAULT
				|| CauldronInteractions.LAVA.get(stack) != CauldronInteraction.DEFAULT
				|| CauldronInteractions.POWDER_SNOW.get(stack) != CauldronInteraction.DEFAULT;
	}

	/** Success chance of a refining that matches a recipe. */
	public static double successChance(int alchemyLevel, boolean soul, boolean fireDao) {
		double chance = BASE_CHANCE + alchemyLevel * CHANCE_PER_LEVEL + (soul ? SOUL_FIRE_CHANCE : 0.0D) + (fireDao ? FIRE_DAO_CHANCE : 0.0D);
		return Math.min(MAX_CHANCE, chance);
	}

	/** The top centre of the cauldron, where the effects play. */
	public static Vec3 top(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
	}

	// ================================================================ using the cauldron

	/**
	 * Both sides decide the same way from what they can see (blocks and held items), so the client answers SUCCESS for
	 * exactly the uses the server handles: the use packet is sent once, the arm swings, and neither vanilla's cauldron
	 * use nor the item's own use (eating, placing, the sneak-use pills) runs. Everything else passes untouched.
	 */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!isFurnace(level, pos)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		Use use;
		if (!held.isEmpty()) {
			if (!AlchemyRecipes.isIngredient(held.getItem()) || hasCauldronInteraction(held)) {
				return InteractionResult.PASS;
			}
			use = Use.ADD;
		} else {
			ItemStack offhand = player.getOffhandItem();
			boolean refine = player.isSecondaryUseActive();
			if (!offhand.isEmpty() && (hasCauldronInteraction(offhand) || !refine)) {
				// The off hand keeps its own use (bucket, food, torch, shield...); sneak + empty main hand still refines.
				return InteractionResult.PASS;
			}
			use = refine ? Use.REFINE : Use.SHOW;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		try {
			BlockPos at = pos.immutable();
			switch (use) {
				case ADD -> add(serverPlayer, serverLevel, at, held);
				case REFINE -> refine(serverPlayer, serverLevel, at);
				case SHOW -> show(serverPlayer, serverLevel, at);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Alchemy furnace interaction failed at {}", pos, exception);
		}
		return InteractionResult.SUCCESS;
	}

	private static void add(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
		AlchemyPots pots = AlchemyPots.get(level);
		AlchemyPots.Pot existing = pots.get(pos);
		if (existing != null && existing.refining()) {
			Msg.actionBar(player, busy(level, existing));
			return;
		}
		if (existing != null && existing.isFull()) {
			Msg.actionBar(player, Component.translatable("kultivator.alchemy.pot.full", AlchemyPots.CAPACITY).withColor(BUSY_COLOR));
			return;
		}
		AlchemyPots.Pot pot = existing != null ? existing : pots.getOrCreate(pos);
		Item item = held.getItem();
		Component name = held.getHoverName().copy();
		pot.add(item);
		pots.setDirty();
		held.consume(1, player);

		Vec3 top = top(pos);
		Fx.send(level, top, FxType.ALCHEMY, 0, BuiltInRegistries.ITEM.getId(item), pot.size() / (float) AlchemyPots.CAPACITY);
		BlockState state = level.getBlockState(pos);
		SoundEvent splash = state.is(Blocks.LAVA_CAULDRON) ? SoundEvents.LAVA_POP
				: state.is(Blocks.WATER_CAULDRON) ? SoundEvents.GENERIC_SPLASH : SoundEvents.ITEM_PICKUP;
		sound(level, top, splash, 0.5F, state.is(Blocks.WATER_CAULDRON) ? 1.6F : 0.7F);
		sound(level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.2F + 0.08F * pot.size());

		Msg.actionBar(player, Component.translatable("kultivator.alchemy.pot.added", name, contents(pot), match(pot)).withColor(POT_COLOR));
	}

	private static void refine(ServerPlayer player, ServerLevel level, BlockPos pos) {
		AlchemyPots pots = AlchemyPots.get(level);
		AlchemyPots.Pot pot = pots.get(pos);
		if (pot == null || pot.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.alchemy.pot.nothing").withColor(BUSY_COLOR));
			return;
		}
		if (pot.refining()) {
			Msg.actionBar(player, busy(level, pot));
			return;
		}
		CultivationData data = Cultivators.get(player);
		pot.startRefining(level.getGameTime() + REFINE_TICKS, player.getUUID(), Alchemy.level(data), data.dao() == Dao.FIRE);
		pots.setDirty();

		Vec3 top = top(pos);
		Fx.send(level, top, FxType.ALCHEMY, 1, REFINE_TICKS, (float) steamColor(pot));
		sound(level, top, SoundEvents.FIRECHARGE_USE, 0.5F, 0.8F);
		sound(level, top, SoundEvents.BREWING_STAND_BREW, 0.9F, 0.9F);
		sound(level, top, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8F, 1.0F);
		Msg.actionBar(player, Component.translatable("kultivator.alchemy.refine.start", REFINE_TICKS / 20, contents(pot))
				.withColor(BUSY_COLOR));
	}

	private static void show(ServerPlayer player, ServerLevel level, BlockPos pos) {
		AlchemyPots.Pot pot = AlchemyPots.get(level).get(pos);
		if (pot == null || pot.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.alchemy.pot.empty", AlchemyPots.CAPACITY).withColor(POT_COLOR));
			return;
		}
		if (pot.refining()) {
			Msg.actionBar(player, busy(level, pot));
			return;
		}
		Msg.actionBar(player, Component.translatable("kultivator.alchemy.pot.contents", contents(pot), match(pot)).withColor(POT_COLOR));
	}

	private static MutableComponent busy(ServerLevel level, AlchemyPots.Pot pot) {
		long left = Math.max(0L, pot.refineEnd() - level.getGameTime());
		return Component.translatable("kultivator.alchemy.pot.busy", (left + 19L) / 20L).withColor(BUSY_COLOR);
	}

	/** "(3/8) Glowstone Dust + Sugar + Glow Berries". */
	private static MutableComponent contents(AlchemyPots.Pot pot) {
		return Component.translatable("kultivator.alchemy.pot.list", pot.size(), AlchemyPots.CAPACITY,
				AlchemyRecipes.ingredientList(pot.contents()));
	}

	/** "→ Qi Gathering Pill" or "→ unknown mixture". */
	private static MutableComponent match(AlchemyPots.Pot pot) {
		AlchemyRecipes.Recipe recipe = AlchemyRecipes.match(pot.contents());
		return recipe == null
				? Component.translatable("kultivator.alchemy.pot.unknown").withStyle(ChatFormatting.GRAY)
				: Component.translatable("kultivator.alchemy.pot.match", recipe.displayName()).withColor(recipe.color());
	}

	private static int steamColor(AlchemyPots.Pot pot) {
		AlchemyRecipes.Recipe recipe = AlchemyRecipes.match(pot.contents());
		return recipe == null ? AlchemyRecipes.MURKY_COLOR : recipe.color();
	}

	// ================================================================ processing

	/** Every {@link #PROCESS_INTERVAL} ticks: refining progress, results and furnaces that went out. */
	static void tickServer(MinecraftServer server) {
		if (server.getTickCount() % PROCESS_INTERVAL != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			AlchemyPots pots = AlchemyPots.get(level);
			if (pots.isEmpty()) {
				continue;
			}
			for (AlchemyPots.Pot pot : List.copyOf(pots.all())) {
				try {
					process(level, pots, pot);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Alchemy pot at {} failed to tick", pot.pos(), exception);
				}
			}
		}
	}

	private static void process(ServerLevel level, AlchemyPots pots, AlchemyPots.Pot pot) {
		BlockPos pos = pot.pos();
		if (!level.isLoaded(pos) || !level.isPositionEntityTicking(pos)) {
			// Unloaded furnaces sleep; a refining that ended meanwhile finishes once they are loaded again.
			return;
		}
		if (pot.isEmpty()) {
			pots.remove(pos);
			return;
		}
		if (!isFurnace(level, pos)) {
			spill(level, pots, pot);
			return;
		}
		if (!pot.refining()) {
			return;
		}
		long left = pot.refineEnd() - level.getGameTime();
		if (left > 0L) {
			Vec3 top = top(pos);
			Fx.send(level, top, FxType.ALCHEMY, 1, (int) Math.min(REFINE_TICKS, left), (float) steamColor(pot));
			sound(level, top, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.3F);
			if (level.getRandom().nextBoolean()) {
				sound(level, top, SoundEvents.FIRE_AMBIENT, 0.7F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			} else {
				sound(level, top, SoundEvents.LAVA_POP, 0.4F, 1.2F);
			}
			return;
		}
		finish(level, pots, pot);
	}

	/** The refining is over: a pill (or smoke). */
	private static void finish(ServerLevel level, AlchemyPots pots, AlchemyPots.Pot pot) {
		BlockPos pos = pot.pos();
		Vec3 top = top(pos);
		boolean soul = soulFire(level, pos);
		AlchemyRecipes.Recipe recipe = AlchemyRecipes.match(pot.contents());
		ServerPlayer owner = pot.owner() == null ? null : level.getServer().getPlayerList().getPlayer(pot.owner());
		int alchemyLevel = owner != null ? Alchemy.level(Cultivators.get(owner)) : pot.ownerLevel();
		boolean fireDao = owner != null ? Cultivators.get(owner).dao() == Dao.FIRE : pot.ownerFire();
		pots.remove(pos);

		RandomSource random = level.getRandom();
		if (recipe == null) {
			failure(level, top, owner, "kultivator.alchemy.refine.unknown", UNKNOWN_XP);
			return;
		}
		if (random.nextDouble() >= successChance(alchemyLevel, soul, fireDao)) {
			failure(level, top, owner, "kultivator.alchemy.refine.failed", recipe.xp() * FAILED_XP_SHARE);
			return;
		}
		PillQuality quality = rollQuality(random, alchemyLevel, soul);
		boolean pill = recipe.isPill();
		int count = recipe.count() + (!pill && quality == PillQuality.PERFECT ? 1 : 0);
		ItemStack result = new ItemStack(recipe.outputItem(), count);
		if (pill) {
			AlchemyComponents.withQuality(result, quality);
		}
		Component name = result.getHoverName().copy();
		dropBeside(level, pos, result, owner == null ? null : owner.position());

		Fx.send(level, top, FxType.ALCHEMY, 2, quality.ordinal(), (float) recipe.color());
		sound(level, top, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
		sound(level, top, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.4F + 0.1F * quality.ordinal());
		sound(level, top, SoundEvents.BREWING_STAND_BREW, 0.8F, 1.3F);
		if (quality == PillQuality.PERFECT) {
			sound(level, top, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.5F);
		}
		if (recipe.outputItem() == AlchemyItems.IMMORTAL_PILL) {
			sound(level, top, SoundEvents.BELL_RESONATE, 1.0F, 1.0F);
			sound(level, top, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		}

		if (owner == null) {
			return;
		}
		double xp = recipe.xp() * (1.0D + QUALITY_XP * quality.ordinal());
		CultivationData data = Cultivators.get(owner);
		for (int i = 0; i < count; i++) {
			data.addPillRefined();
		}
		Cultivators.dirty(owner);
		MutableComponent shownQuality = pill ? quality.displayName() : Component.translatable("kultivator.alchemy.quality.na");
		Msg.chat(owner, Component.translatable("kultivator.alchemy.refine.success", count, name, shownQuality, Msg.number(xp))
				.withColor(recipe.color()));
		Alchemy.addXp(owner, xp);
		Advancements.grant(owner, "first_alchemy");
		if (quality == PillQuality.PERFECT) {
			Advancements.grant(owner, "perfect_pill");
		}
		if (recipe.outputItem() == AlchemyItems.IMMORTAL_PILL) {
			Advancements.grant(owner, "immortal_pill");
		}
	}

	private static void failure(ServerLevel level, Vec3 top, @Nullable ServerPlayer owner, String key, double xp) {
		Fx.send(level, top, FxType.ALCHEMY, 3, 0, 1.0F);
		sound(level, top, SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.7F);
		sound(level, top, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6F, 0.6F);
		sound(level, top, SoundEvents.SMOKER_SMOKE, 1.0F, 0.6F);
		if (owner == null) {
			return;
		}
		Msg.chat(owner, Component.translatable(key, Msg.number(Math.max(1.0D, xp))).withColor(FAIL_COLOR));
		Alchemy.addXp(owner, xp);
		Advancements.grant(owner, "failed_refining");
	}

	/** The furnace went out (or the cauldron is gone): the ingredients fall out where it stood. */
	private static void spill(ServerLevel level, AlchemyPots pots, AlchemyPots.Pot pot) {
		BlockPos pos = pot.pos();
		pots.remove(pos);
		Vec3 centre = Vec3.atCenterOf(pos);
		for (ItemStack stack : pot.stacks()) {
			dropBeside(level, pos, stack, null);
		}
		Fx.send(level, top(pos), FxType.ALCHEMY, 3, 1, 0.5F);
		sound(level, centre, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.2F);
		if (pot.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pot.owner());
			if (owner != null && pot.refining()) {
				Msg.chat(owner, Component.translatable("kultivator.alchemy.refine.spilled").withColor(FAIL_COLOR));
			}
		}
	}

	/** Quality from a roll shifted by the alchemy level; soul fire may raise it one grade. */
	private static PillQuality rollQuality(RandomSource random, int alchemyLevel, boolean soul) {
		double roll = random.nextDouble() + alchemyLevel * QUALITY_PER_LEVEL;
		PillQuality quality;
		if (roll < 0.35D) {
			quality = PillQuality.LOW;
		} else if (roll < 0.75D) {
			quality = PillQuality.MID;
		} else if (roll < 0.95D) {
			quality = PillQuality.HIGH;
		} else {
			quality = PillQuality.PERFECT;
		}
		if (soul && random.nextDouble() < SOUL_FIRE_UPGRADE) {
			quality = quality.up();
		}
		return quality;
	}

	/** Drops {@code stack} as an item entity (never a block change), popping up a little. */
	static void spawn(ServerLevel level, Vec3 at, ItemStack stack, double upward) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack);
		RandomSource random = level.getRandom();
		entity.setDeltaMovement((random.nextDouble() - 0.5D) * 0.08D, upward, (random.nextDouble() - 0.5D) * 0.08D);
		entity.setPickUpDelay(10);
		level.addFreshEntity(entity);
	}

	/**
	 * Drops {@code stack} just outside the cauldron's footprint on one side (towards {@code toward} when given, else a
	 * random side), popping up and outwards, so it never lands in the bowl (a lava cauldron would burn it). Never a block
	 * change.
	 */
	private static void dropBeside(ServerLevel level, BlockPos pos, ItemStack stack, @Nullable Vec3 toward) {
		if (stack.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		Direction side = toward == null ? Direction.Plane.HORIZONTAL.getRandomDirection(random)
				: Direction.getApproximateNearest(toward.x - (pos.getX() + 0.5D), 0.0D, toward.z - (pos.getZ() + 0.5D));
		if (side.getAxis().isVertical()) {
			side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		}
		// 0.8 from the centre keeps the item's 0.25-wide box fully outside the cauldron.
		ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D + side.getStepX() * 0.8D, pos.getY() + 1.05D,
				pos.getZ() + 0.5D + side.getStepZ() * 0.8D, stack);
		entity.setDeltaMovement(side.getStepX() * 0.1D + (random.nextDouble() - 0.5D) * 0.04D, 0.2D,
				side.getStepZ() * 0.1D + (random.nextDouble() - 0.5D) * 0.04D);
		entity.setPickUpDelay(10);
		level.addFreshEntity(entity);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
	}

	private AlchemyFurnace() {
	}
}

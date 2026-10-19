package dev.kultivator.disciple;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.item.MarrowCleansingPillItem;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Disciples (Murid, DESIGN 18): recruitment with the scroll, the Soul Tablet at death and the revival, the master's
 * orders (screen actions, right-click, commands), following the master across dimensions, and the list of the master's
 * disciples kept in {@code CultivationData.extra()["disciples"]}.
 * <p>
 * The list holds one entry per soul: {@code id} (soul UUID), {@code name}, {@code life}, {@code dead}, and the last
 * known {@code step} (rank) and {@code mode}. Living entries count towards the limit of {@link #MAX_DISCIPLES}; fallen
 * ones only remember the soul so its tablet can bring it back (a tablet is valid while its {@code life} matches the
 * entry, so a copied tablet cannot raise the same soul twice). A disciple that dies while its master is offline is
 * marked fallen when the master joins ({@link DiscipleRecords}, kept across restarts).
 */
public final class Disciples {

	/** A master guides at most this many living disciples. */
	public static final int MAX_DISCIPLES = 5;
	/** Screen actions and orders reach disciples within this distance. */
	public static final double ACTION_RANGE = 16.0D;
	/** Opening a disciple's scroll and giving it orders work from this far (same dimension, loaded): the Murid key. */
	public static final double VIEW_RANGE = 64.0D;
	/** Sub-key of {@code CultivationData.extra()} owned by this module. */
	public static final String EXTRA = "disciples";
	private static final String LIST = "list";
	/** Fallen entries beyond this many are forgotten, oldest first. */
	private static final int MAX_FALLEN_KEPT = 10;
	private static final int GOLD = 0xFFD54A;
	private static final int SOUL_COLOR = 0x9FF3E6;

	/** Following a master into another dimension is retried this long (ticks): the chunks there may still be loading. */
	private static final int FOLLOW_RETRY_TICKS = 100;
	/** A disciple that hit a monster within this long (ticks) before someone else killed it gets a share of the XP. */
	private static final int KILL_SHARE_MEMORY = 100;
	private static final double KILL_SHARE_RANGE = 16.0D;

	/** Loaded disciples per master UUID (any dimension). */
	private static final Map<UUID, Set<DiscipleEntity>> LOADED = new HashMap<>();
	/** Disciples who must follow their master out of {@code origin}, retried each tick until {@code deadline}. */
	private static final List<FollowRequest> PENDING_FOLLOW = new ArrayList<>();

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);

	/** The souls that were at the master's side in {@code origin} when the master left it. */
	private record FollowRequest(UUID player, ServerLevel origin, Set<UUID> souls, int deadline) {
	}

	public static void init() {
		DiscipleEntities.init();
		DiscipleItems.init();
		DuelRules.init();
		// Candidates, rivals, phantoms and clones never come back from disk (DESIGN 20.2.5).
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof DiscipleEntity disciple) || disciple.role() == DiscipleEntity.Role.DISCIPLE);
		// Nobody's techniques touch them (a registered duel partner still can: DuelRules is asked first).
		Targeting.addExclusion((caster, entity) -> entity instanceof DiscipleEntity disciple && disciple.role() != DiscipleEntity.Role.DISCIPLE);
		UseEntityCallback.EVENT.register(Disciples::onUseEntity);
		ServerEntityEvents.ENTITY_LOAD.register(Disciples::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Disciples::onUnload);
		ServerPlayerEvents.JOIN.register(Disciples::onJoin);
		// Players are never block-safety tagged (an older duel could have saved the tag on the master: portals, plates).
		ServerPlayerEvents.JOIN.register(player -> player.removeTag(NoGrief.TAG));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> newPlayer.removeTag(NoGrief.TAG));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onChangeLevel(player, origin));
		ServerLivingEntityEvents.AFTER_DEATH.register(Disciples::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(Disciples::endTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			PENDING_FOLLOW.clear();
			DiscipleTechniques.clearAll();
		});
	}

	// ================================================================== screen actions

	/**
	 * Every DISCIPLE_* action (arg = the disciple's entity id). DISCIPLE_OPEN with the text {@code "refresh"} (the open
	 * screen asking for fresh details) only sends the details and never reopens a screen the player closed meanwhile.
	 */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String text = action.text() == null ? "" : action.text();
		if (action.action() == Actions.DISCIPLE_OPEN && action.arg() < 0) {
			openFromKey(player, text);
			return;
		}
		boolean refresh = action.action() == Actions.DISCIPLE_OPEN && "refresh".equals(text);
		Entity entity = player.level().getEntity(action.arg());
		if (!(entity instanceof DiscipleEntity disciple) || !disciple.isAlive() || disciple.isReleasing()
				|| disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			if (!refresh) {
				Msg.actionBar(player, Component.translatable("kultivator.disciples.gone").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!disciple.isOwnedBy(player)) {
			if (!refresh) {
				Msg.chat(player, Component.translatable("kultivator.disciples.not_yours", disciple.getDisplayName()).withStyle(ChatFormatting.RED));
			}
			return;
		}
		boolean remote = action.action() == Actions.DISCIPLE_OPEN || action.action() == Actions.DISCIPLE_MODE;
		double range = remote ? VIEW_RANGE : ACTION_RANGE;
		if (player.distanceToSqr(disciple) > range * range) {
			if (!refresh) {
				Msg.chat(player, Component.translatable("kultivator.disciples.too_far", disciple.getDisplayName(), (int) range)
						.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		switch (action.action()) {
			case Actions.DISCIPLE_OPEN -> {
				if (refresh) {
					sendInfo(player, disciple);
				} else {
					openScreen(player, disciple);
				}
				return;
			}
			case Actions.DISCIPLE_MODE -> {
				DiscipleEntity.Mode mode = DiscipleEntity.Mode.byId(text);
				if (mode != null) {
					order(player, disciple, mode);
				}
			}
			case Actions.DISCIPLE_TEACH -> {
				Technique technique = Technique.byId(text);
				if (technique != null) {
					DiscipleCultivation.teach(disciple, player, technique);
				} else {
					// every 6c spec id from the Teknik tab (DESIGN 22.13.2)
					dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(text);
					if (spec != null) {
						DiscipleCultivation.teach(disciple, player, spec);
					}
				}
			}
			case Actions.DISCIPLE_FEED -> DiscipleCultivation.feed(disciple, player, player.getMainHandItem());
			case Actions.DISCIPLE_BREAKTHROUGH -> DiscipleCultivation.startBreakthrough(disciple, player);
			case Actions.DISCIPLE_CHOOSE_DAO -> {
				Dao dao = Dao.byId(text);
				if (dao != null) {
					DiscipleCultivation.chooseDao(disciple, player, dao);
				}
			}
			case Actions.DISCIPLE_RELEASE -> {
				if ("confirm".equals(text)) {
					release(player, disciple);
					return;
				}
			}
			default -> {
				return;
			}
		}
		if (disciple.isAlive() && !disciple.isRemoved()) {
			sendInfo(player, disciple);
		}
	}

	/**
	 * The Murid key and {@code /kultivasi murid buka}: opens the scroll of the own disciple the player looks at (within
	 * 15 degrees), else the nearest one, within {@link #VIEW_RANGE} in the same dimension. {@code next:<id>} opens the
	 * next disciple by name after that one (the key pressed again while a scroll is open); {@code name:<name>} opens the
	 * disciple with that name (spaces may be written as underscores).
	 */
	public static void openFromKey(ServerPlayer player, String text) {
		List<DiscipleEntity> near = new ArrayList<>();
		for (DiscipleEntity disciple : loaded(player.getUUID())) {
			if (disciple.level() == player.level() && disciple.role() == DiscipleEntity.Role.DISCIPLE && !disciple.isReleasing()
					&& player.distanceToSqr(disciple) <= VIEW_RANGE * VIEW_RANGE) {
				near.add(disciple);
			}
		}
		if (near.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.disciples.none_near", (int) VIEW_RANGE).withStyle(ChatFormatting.GRAY));
			return;
		}
		near.sort(java.util.Comparator.comparing((DiscipleEntity d) -> d.getName().getString().toLowerCase(java.util.Locale.ROOT))
				.thenComparingInt(DiscipleEntity::getId));
		DiscipleEntity pick = null;
		if (text.startsWith("next:")) {
			int current;
			try {
				current = Integer.parseInt(text.substring(5));
			} catch (NumberFormatException exception) {
				current = -1;
			}
			int at = -1;
			for (int i = 0; i < near.size(); i++) {
				if (near.get(i).getId() == current) {
					at = i;
				}
			}
			pick = near.get((at + 1) % near.size());
		} else if (text.startsWith("name:")) {
			String wanted = text.substring(5).replace('_', ' ').trim().toLowerCase(java.util.Locale.ROOT);
			for (DiscipleEntity disciple : near) {
				if (disciple.getName().getString().toLowerCase(java.util.Locale.ROOT).equals(wanted)) {
					pick = disciple;
				}
			}
			if (pick == null) {
				Msg.actionBar(player, Component.translatable("kultivator.disciples.none_named", text.substring(5)).withStyle(ChatFormatting.GRAY));
				return;
			}
		} else {
			net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
			net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
			double best = Double.MAX_VALUE;
			for (DiscipleEntity disciple : near) {
				net.minecraft.world.phys.Vec3 to = disciple.getBoundingBox().getCenter().subtract(eye);
				double distance = to.length();
				boolean aimed = distance > 0.0D && to.normalize().dot(look) > 0.966D;
				double score = aimed ? distance - 1000.0D : distance;
				if (score < best) {
					best = score;
					pick = disciple;
				}
			}
		}
		if (pick != null) {
			openScreen(player, pick);
		}
	}

	/** Sends the disciple's details, then asks the client to open its screen. */
	public static void openScreen(ServerPlayer player, DiscipleEntity disciple) {
		sendInfo(player, disciple);
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.DISCIPLE, disciple.getId()));
	}

	/** {@code DiscipleCultivation.infoFor} plus the order, the master's name and the health when it lacks them. */
	public static void sendInfo(ServerPlayer player, DiscipleEntity disciple) {
		CompoundTag info = DiscipleCultivation.infoFor(disciple, player);
		if (info == null) {
			info = new CompoundTag();
		}
		if (!info.contains("mode")) {
			info.putString("mode", disciple.mode().id());
		}
		if (!info.contains("master")) {
			info.putString("master", disciple.ownerName());
		}
		if (!info.contains("health")) {
			info.putFloat("health", disciple.getHealth());
		}
		if (!info.contains("max_health")) {
			info.putFloat("max_health", disciple.getMaxHealth());
		}
		Net.send(player, new Payloads.DiscipleInfo(disciple.getId(), info));
	}

	/** Gives one disciple an order, with its answer in chat. */
	private static void order(ServerPlayer player, DiscipleEntity disciple, DiscipleEntity.Mode mode) {
		String why = DiscipleHooks.modeBlocker(disciple, mode);
		if (why != null) {
			// A phase-6 module refused the order (sect rank, protector post...): its key gets the name and the order.
			Msg.chat(player, Component.translatable(why, disciple.getDisplayName(), mode.displayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		disciple.setMode(mode);
		disciple.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Msg.chat(player, Component.translatable("kultivator.disciples.mode.set", disciple.getDisplayName(), mode.displayName())
				.withStyle(ChatFormatting.AQUA));
		ServerLevel level = player.level();
		level.playSound(null, disciple.getX(), disciple.getY(), disciple.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL,
				0.6F, 1.2F);
	}

	/**
	 * The disciple bows, says farewell and vanishes; its place in the list is freed at once. A running breakthrough ends
	 * first (its clouds and vortex stop for everyone).
	 */
	private static void release(ServerPlayer player, DiscipleEntity disciple) {
		if (disciple.data().breakingThrough() && disciple.level() instanceof ServerLevel level) {
			DiscipleBreakthrough.cancel(disciple, level, null);
		}
		removeEntry(player, disciple.soulId());
		Component name = disciple.getDisplayName();
		disciple.getLookControl().setLookAt(player, 30.0F, 30.0F);
		disciple.startRelease();
		Msg.chat(player, Component.translatable("kultivator.disciples.release.farewell", name).withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.disciples.release.done", name).withStyle(ChatFormatting.DARK_AQUA));
		ServerLevel level = player.level();
		level.playSound(null, disciple.getX(), disciple.getY(), disciple.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.5F, 1.4F);
	}

	// ================================================================== right-click

	/**
	 * Right-clicking your own disciple with an empty hand (or while sneaking) opens its screen, with a pill it swallows
	 * the pill; an item a phase-6 feed handler claims is used on it, sneaking or not; a name tag renames it the vanilla way. Someone else's disciple only tells whose disciple it is (it cannot
	 * be renamed or fed by anyone but its master).
	 */
	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			@Nullable EntityHitResult hitResult) {
		if (!(entity instanceof DiscipleEntity disciple) || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (disciple.role() == DiscipleEntity.Role.CANDIDATE) {
			// The Sect Hall's candidate (DESIGN 20.3.3): testing and acceptance belong to the hall module.
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && disciple.isAlive()) {
				dev.kultivator.sect.hall.Candidates.onInteract(serverPlayer, disciple);
			}
			return InteractionResult.SUCCESS;
		}
		if (disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			// Rivals, phantoms and clones answer nobody (and cannot be renamed).
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean own = disciple.isOwnedBy(player);
		if (own && stack.is(Items.NAME_TAG)) {
			// Only its master may rename it (vanilla name tag handling).
			return InteractionResult.PASS;
		}
		boolean pill = isPill(stack);
		boolean open = own && (stack.isEmpty() || player.isShiftKeyDown() && !pill);
		if (own && !stack.isEmpty() && !pill) {
			// Phase 6: a module may take other items from the master's hand (DiscipleHooks.addFeedHandler). Feed-handler items
			// (Soul Seal Jade, Master's Ring, flame seed, spirit meal) win over the sneak-opens-screen shortcut.
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && DiscipleHooks.hasFeedHandlers()
					&& disciple.isAlive() && !disciple.isReleasing() && DiscipleHooks.feedHandler(disciple, serverPlayer, stack)) {
				disciple.refreshSynced();
				return InteractionResult.SUCCESS;
			}
			if (!open) {
				return InteractionResult.PASS;
			}
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			// The server decides; the client only swings the arm and sends the click.
			return InteractionResult.SUCCESS;
		}
		if (!disciple.isAlive() || disciple.isReleasing()) {
			return InteractionResult.PASS;
		}
		if (!own) {
			String master = disciple.ownerName().isEmpty() ? "?" : disciple.ownerName();
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.disciples.other", disciple.getDisplayName(), master)
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		disciple.getLookControl().setLookAt(serverPlayer, 30.0F, 30.0F);
		if (open) {
			openScreen(serverPlayer, disciple);
		} else {
			DiscipleCultivation.feed(disciple, serverPlayer, stack);
		}
		return InteractionResult.SUCCESS;
	}

	/** Vanilla items that work as pills, refined pills and the Marrow Cleansing Pill. */
	public static boolean isPill(ItemStack stack) {
		return !stack.isEmpty() && (VanillaPills.Pill.of(stack) != null || stack.getItem() instanceof PillItem
				|| stack.getItem() instanceof MarrowCleansingPillItem);
	}

	// ================================================================== recruitment, death, revival

	/** A new disciple at the player's side (the caller consumes the scroll). False with a chat reason when it fails. */
	public static boolean recruit(ServerPlayer player) {
		ServerLevel level = player.level();
		CultivationData cultivation = Cultivators.get(player);
		int alive = aliveCount(cultivation);
		int cap = cap(player);
		if (alive >= cap) {
			Msg.chat(player, Component.translatable("kultivator.disciples.full", alive, cap).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		DiscipleEntity disciple = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (disciple == null) {
			return false;
		}
		Vec3 spot = spotBeside(level, disciple, player);
		if (spot == null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.no_space").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		RandomSource random = player.getRandom();
		DiscipleData data = DiscipleData.fresh(random);
		disciple.setupNew(player, data, DiscipleNames.pick(random, takenNames(cultivation)), random.nextInt(DiscipleEntity.SKIN_COUNT),
				random.nextBoolean());
		place(disciple, spot, player);
		// Listed before it joins the world (joining lists it too; the entry is the same).
		ensureAlive(player, disciple);
		if (!level.addFreshEntity(disciple)) {
			removeEntry(player, disciple.soulId());
			return false;
		}

		welcome(player, level, disciple, spot, alive, cap);
		return true;
	}

	/** The welcome of a new disciple (scroll or Sect Hall): FX and sounds at {@code spot}, chat lines, advancements. */
	private static void welcome(ServerPlayer player, ServerLevel level, DiscipleEntity disciple, Vec3 spot, int aliveBefore, int cap) {
		DiscipleData data = disciple.data();
		int color = data.rootColor();
		Fx.pillar(level, spot, color, 3.5F);
		Fx.burst(level, spot.add(0.0D, 1.0D, 0.0D), color, 40, 1.2F);
		Fx.ring(level, spot.add(0.0D, 0.1D, 0.0D), GOLD, 2.5F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.2F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);

		Component name = disciple.getDisplayName();
		Msg.chat(player, Component.translatable("kultivator.disciples.welcome", name).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.disciples.welcome.root", name,
				SpiritRoots.describe(data.rootElements(), data.rootGrade())).withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.disciples.welcome.hint").withStyle(ChatFormatting.DARK_AQUA));
		Msg.actionBar(player, Component.translatable("kultivator.disciples.count", aliveBefore + 1, cap).withStyle(ChatFormatting.AQUA));
		Advancements.grant(player, "first_disciple");
		if (aliveBefore + 1 >= MAX_DISCIPLES) {
			Advancements.grant(player, "five_disciples");
		}
	}

	/**
	 * Calls the soul of a tablet back next to its master (the caller consumes the tablet). Refused for anyone but the
	 * master, for a tablet whose soul already lives again, and when the master already guides five disciples.
	 */
	public static boolean revive(ServerPlayer player, ItemStack stack) {
		CompoundTag soul = DiscipleItems.soulOf(stack);
		if (soul.isEmpty() || soul.getCompound("disciple").isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.disciples.tablet.empty").withStyle(ChatFormatting.GRAY));
			return false;
		}
		UUID owner = soul.read("owner", UUIDUtil.CODEC).orElse(null);
		if (owner == null || !owner.equals(player.getUUID())) {
			String master = soul.getStringOr("owner_name", "?");
			Msg.chat(player, Component.translatable("kultivator.disciples.revive.not_owner", master).withStyle(ChatFormatting.RED));
			return false;
		}
		UUID soulId = soul.read("soul", UUIDUtil.CODEC).orElse(null);
		int life = soul.getIntOr("life", 0);
		CultivationData cultivation = Cultivators.get(player);
		CompoundTag entry = soulId == null ? null : findEntry(entries(cultivation), soulId);
		if (entry != null && entry.getIntOr("life", 0) != life || soulId != null && findLoaded(player.getUUID(), soulId) != null
				|| soulId != null && DiscipleRecords.get(player.level().getServer()).isForgotten(soulId)) {
			Msg.chat(player, Component.translatable("kultivator.disciples.revive.stale").withStyle(ChatFormatting.GRAY));
			return false;
		}
		int alive = aliveCount(cultivation) - (entry != null && !entry.getBooleanOr("dead", false) ? 1 : 0);
		int cap = cap(player);
		if (alive >= cap) {
			Msg.chat(player, Component.translatable("kultivator.disciples.full", alive, cap).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ServerLevel level = player.level();
		DiscipleEntity disciple = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (disciple == null) {
			return false;
		}
		Vec3 spot = spotBeside(level, disciple, player);
		if (spot == null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.no_space").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		disciple.restoreFromTablet(soul, player);
		place(disciple, spot, player);
		// Kept to undo the listing if the body cannot join the world (the tablet then stays valid).
		CompoundTag before = entry == null ? null : entry.copy();
		ensureAlive(player, disciple);
		if (!level.addFreshEntity(disciple)) {
			removeEntry(player, disciple.soulId());
			if (before != null) {
				entries(cultivation).add(before);
				Cultivators.dirty(player);
			}
			return false;
		}

		int color = disciple.data().rootColor();
		Fx.pillar(level, spot, GOLD, 5.0F);
		Fx.burst(level, spot.add(0.0D, 1.0D, 0.0D), color, 50, 1.4F);
		Fx.ring(level, spot.add(0.0D, 0.1D, 0.0D), SOUL_COLOR, 3.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.1F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		Msg.chat(player, Component.translatable("kultivator.disciples.revive", disciple.getDisplayName()).withStyle(ChatFormatting.GOLD));
		Msg.actionBar(player, Component.translatable("kultivator.disciples.count", alive + 1, cap).withStyle(ChatFormatting.AQUA));
		Advancements.grant(player, "disciple_revived");
		if (alive + 1 >= MAX_DISCIPLES) {
			Advancements.grant(player, "five_disciples");
		}
		return true;
	}

	/**
	 * A disciple died: its Soul Tablet appears where it fell (glowing, invulnerable, never despawning), or in its master's
	 * hands when it fell over the void (with the master offline: where it last stood on safe ground, else at its post, else
	 * at the world spawn), so a tablet always exists; the master is told where, and the soul is marked fallen in the
	 * master's list (right away, or when the master joins).
	 */
	static void onDeath(DiscipleEntity disciple, ServerLevel level) {
		if (disciple.isReleasing() || disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			// Only a real disciple leaves a Soul Tablet (DESIGN 20.2.5).
			return;
		}
		if (disciple.data().breakingThrough()) {
			// Death ends the breakthrough: its clouds or vortex stop and the tablet keeps no running trial.
			DiscipleBreakthrough.cancel(disciple, level, null);
		}
		ItemStack tablet = DiscipleItems.tabletFor(disciple);
		ServerPlayer owner = disciple.owner();
		Vec3 pos = disciple.position();
		Component name = disciple.getDisplayName();
		Fx.pillar(level, pos, SOUL_COLOR, 4.0F);
		Fx.burst(level, pos.add(0.0D, 1.0D, 0.0D), disciple.data().rootColor(), 30, 1.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1.5F, 0.8F);

		// Read-only: nothing below to land on (an End island's edge, the void), so a tablet dropped here would be lost.
		boolean overVoid = pos.y < level.getMinY()
				|| level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z)) <= level.getMinY();
		if (overVoid) {
			if (owner != null) {
				if (!owner.getInventory().add(tablet)) {
					spawnTablet(owner.level(), owner.position().add(0.0D, 0.5D, 0.0D), tablet);
				}
				Msg.chat(owner, Component.translatable("kultivator.disciples.died.carried", name).withStyle(ChatFormatting.RED));
			} else {
				BlockPos safe = disciple.lastSafePos() != null ? disciple.lastSafePos() : disciple.post();
				if (safe != null) {
					spawnTablet(level, Vec3.atBottomCenterOf(safe).add(0.0D, 0.5D, 0.0D), tablet);
				} else {
					GlobalPos spawn = level.getRespawnData().globalPos();
					ServerLevel spawnLevel = level.getServer().getLevel(spawn.dimension());
					spawnTablet(spawnLevel != null ? spawnLevel : level.getServer().overworld(),
							Vec3.atBottomCenterOf(spawn.pos()).add(0.0D, 0.5D, 0.0D), tablet);
				}
			}
		} else {
			spawnTablet(level, pos.add(0.0D, 0.5D, 0.0D), tablet);
			if (owner != null) {
				Msg.chat(owner, Component.translatable("kultivator.disciples.died", name, Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z),
						dimensionName(level.dimension())).withStyle(ChatFormatting.RED));
			}
		}

		if (owner != null) {
			Msg.chat(owner, Component.translatable("kultivator.disciples.died.hint").withStyle(ChatFormatting.GRAY));
			markDead(owner, disciple);
		} else if (disciple.ownerId() != null) {
			// Kept in the world's saved data: the master learns of it when they join, even after a restart.
			DiscipleRecords.get(level.getServer()).addPendingDeath(disciple.ownerId(), disciple.soulId());
		}
	}

	/** "Overworld", "Nether", "End" in the player's language; other dimensions by their id. */
	private static Component dimensionName(ResourceKey<Level> dimension) {
		if (dimension == Level.OVERWORLD) {
			return Component.translatable("kultivator.disciples.dim.overworld");
		}
		if (dimension == Level.NETHER) {
			return Component.translatable("kultivator.disciples.dim.nether");
		}
		if (dimension == Level.END) {
			return Component.translatable("kultivator.disciples.dim.end");
		}
		return Component.literal(dimension.identifier().toString());
	}

	/**
	 * Drops a Soul Tablet: glowing, invulnerable, never despawning, with {@link NoGrief#TAG} (it never travels through a
	 * portal, which could build an exit portal, and never presses a plate or a tripwire).
	 */
	private static void spawnTablet(ServerLevel level, Vec3 at, ItemStack tablet) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, tablet);
		item.setDefaultPickUpDelay();
		item.setUnlimitedLifetime();
		item.setGlowingTag(true);
		item.setPermanentlyInvulnerable(true);
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
	}

	/** A safe spot next to the player for a new or returning disciple: in front of them first, else around them. */
	private static @Nullable Vec3 spotBeside(ServerLevel level, DiscipleEntity disciple, ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() > 1.0E-4D) {
			Vec3 front = player.position().add(flat.normalize().scale(2.0D));
			if (level.isLoaded(BlockPos.containing(front))) {
				Vec3 spot = Targeting.safeSpot(level, disciple, front);
				if (spot != null) {
					return spot;
				}
			}
		}
		return disciple.spotNear(level, player.position());
	}

	/** Puts a not yet added disciple on {@code spot}, facing the player. */
	private static void place(DiscipleEntity disciple, Vec3 spot, ServerPlayer player) {
		double dx = player.getX() - spot.x;
		double dz = player.getZ() - spot.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		disciple.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		disciple.setYHeadRot(yaw);
		disciple.setYBodyRot(yaw);
	}

	// ================================================================== phase 6: away, candidates, other bodies (DESIGN 20.2.7)

	/** The master's disciple limit: {@link #MAX_DISCIPLES} unless a module's cap hook raises it (Sect Hall level). */
	public static int cap(ServerPlayer player) {
		return DiscipleHooks.cap(player);
	}

	/**
	 * Takes a disciple out of the world for an errand (expedition, envoy, soul flight): a running breakthrough ends
	 * without a result, the body is removed silently and the master's list entry is marked {@code away} (away disciples
	 * still count toward the limit). Returns everything {@link #arrive} needs: the Soul Tablet data plus {@code health}
	 * and {@code away_reason}. Only role DISCIPLE can depart (an empty tag otherwise, nothing removed).
	 */
	public static CompoundTag depart(DiscipleEntity disciple, String reason) {
		if (disciple.departed() && disciple.departure() != null) {
			// Already gone (the caller and Halls.storeAway may both call this): the same tag again.
			return disciple.departure().copy();
		}
		if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isRemoved() || !(disciple.level() instanceof ServerLevel level)) {
			return new CompoundTag();
		}
		boolean fled = false;
		if (disciple.data().breakingThrough()) {
			// A tribulation abandoned after its first strike counts as failed (phase 5), also when the disciple is sent away.
			DiscipleBreakthrough.Trial trial = disciple.data().trial();
			fled = trial != null && trial.kind == DiscipleBreakthrough.Kind.TRIBULATION && trial.strikesDone > 0;
			DiscipleBreakthrough.cancel(disciple, level, null);
		}
		CompoundTag tag = disciple.writeTablet();
		if (fled) {
			CompoundTag data = tag.getCompoundOrEmpty("disciple");
			data.putBoolean("trial_fled", true);
			tag.put("disciple", data);
		}
		tag.putFloat("health", disciple.getHealth());
		tag.putString("away_reason", reason == null ? "" : reason);
		UUID owner = disciple.ownerId();
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			ListTag list = entries(Cultivators.get(player));
			CompoundTag entry = findEntry(list, disciple.soulId());
			if (entry == null) {
				entry = new CompoundTag();
				list.add(entry);
			}
			writeEntry(entry, disciple);
			entry.putBoolean("away", true);
			entry.putString("away_reason", reason == null ? "" : reason);
			Cultivators.dirty(player);
		}
		disciple.markDeparted(tag.copy());
		if (owner != null) {
			Set<DiscipleEntity> set = LOADED.get(owner);
			if (set != null) {
				set.remove(disciple);
				if (set.isEmpty()) {
					LOADED.remove(owner);
				}
			}
		}
		disciple.discard();
		return tag;
	}

	/** Notes when a departed disciple is due back (game time), for the master's list ("· 0.4 days left"). */
	public static void setAwayUntil(MinecraftServer server, UUID owner, UUID soul, long returnAt) {
		ServerPlayer player = server == null || owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (player == null || soul == null) {
			return;
		}
		CompoundTag entry = findEntry(entries(Cultivators.get(player)), soul);
		if (entry != null && entry.getBooleanOr("away", false)) {
			entry.putLong("away_until", returnAt);
			Cultivators.dirty(player);
		}
	}

	/**
	 * A departed disciple comes back as a Soul Tablet instead of a body (a Hardcore loss in the forbidden ground): its
	 * list entry stops being away and turns fallen, so it frees its slot and can be revived or forgotten. An offline
	 * master learns of it when they join.
	 */
	public static void markFallen(MinecraftServer server, UUID owner, UUID soul) {
		if (server == null || owner == null || soul == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			DiscipleRecords.get(server).addPendingDeath(owner, soul);
			return;
		}
		ListTag list = entries(Cultivators.get(player));
		CompoundTag entry = findEntry(list, soul);
		if (entry == null) {
			return;
		}
		entry.remove("away");
		entry.remove("away_reason");
		entry.remove("away_until");
		entry.putBoolean("dead", true);
		pruneFallen(list);
		Cultivators.dirty(player);
	}

	/**
	 * Brings a departed disciple back ({@code tag} from {@link #depart}, maybe changed by a return handler) on a safe spot
	 * at or near {@code pos}, and clears its away mark. Null when it cannot come now (chunk not loaded, no room, its body
	 * is already in the world, or its master forgot it); the caller keeps the record and tries again later.
	 */
	public static @Nullable DiscipleEntity arrive(ServerLevel level, Vec3 pos, CompoundTag tag, UUID owner) {
		UUID soul = tag.read("soul", UUIDUtil.CODEC).orElse(null);
		if (soul == null || owner == null || tag.getCompoundOrEmpty("disciple").isEmpty()) {
			return null;
		}
		if (DiscipleRecords.get(level.getServer()).isForgotten(soul) || findLoaded(owner, soul) != null
				|| !level.isLoaded(BlockPos.containing(pos))) {
			return null;
		}
		DiscipleEntity disciple = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (disciple == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, disciple, pos);
		if (spot == null) {
			spot = disciple.spotNear(level, pos);
		}
		if (spot == null) {
			return null;
		}
		disciple.snapTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		String ownerName = player != null ? player.getName().getString() : tag.getStringOr("owner_name", "");
		disciple.restoreFromDeparture(tag, owner, ownerName);
		if (!level.addFreshEntity(disciple)) {
			return null;
		}
		if (player != null) {
			// Joining the world listed it already (onLoad); this also clears the away mark of an older entry.
			ensureAlive(player, disciple);
		}
		return disciple;
	}

	/**
	 * A Sect Hall candidate (role CANDIDATE, no master, untouchable, strolling within 10 blocks of {@code anchor}) at
	 * {@code pos}, already added to the world; never saved. Null when it could not be added.
	 */
	public static @Nullable DiscipleEntity createCandidate(ServerLevel level, Vec3 pos, DiscipleData data, Component name, int skin,
			boolean slim, BlockPos anchor) {
		return spawnUnowned(level, pos, data, name, skin, slim, DiscipleEntity.Role.CANDIDATE, anchor);
	}

	/**
	 * A body without a master for the phase-6 modules: a CANDIDATE, RIVAL or PHANTOM with this data and look, added to
	 * the world (never saved, never listed, no tablet). Null for role DISCIPLE or CLONE, or when it could not be added.
	 */
	public static @Nullable DiscipleEntity spawnUnowned(ServerLevel level, Vec3 pos, DiscipleData data, @Nullable Component name, int skin,
			boolean slim, DiscipleEntity.Role role, @Nullable BlockPos anchor) {
		if (role == DiscipleEntity.Role.DISCIPLE || role == DiscipleEntity.Role.CLONE) {
			return null;
		}
		DiscipleEntity body = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (body == null) {
			return null;
		}
		body.setRole(role, anchor);
		body.bindTo(null, "");
		float yaw = level.getRandom().nextFloat() * 360.0F;
		body.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		body.setYHeadRot(yaw);
		body.setYBodyRot(yaw);
		body.setupBody(data, name, skin, slim);
		return level.addFreshEntity(body) ? body : null;
	}

	/**
	 * A Divine Clone of {@code source} (role CLONE): its master, look and a copy of its data, following the source and
	 * fighting monsters; never saved, never listed. The caller sets its strength (stats hooks) and removes it in time.
	 * Null when it could not be added.
	 */
	public static @Nullable DiscipleEntity spawnClone(DiscipleEntity source, Vec3 pos) {
		if (!(source.level() instanceof ServerLevel level) || source.role() != DiscipleEntity.Role.DISCIPLE) {
			return null;
		}
		DiscipleEntity clone = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (clone == null) {
			return null;
		}
		clone.setRole(DiscipleEntity.Role.CLONE, BlockPos.containing(pos));
		clone.bindTo(source.ownerId(), source.ownerName());
		clone.setCloneSource(source.soulId());
		clone.snapTo(pos.x, pos.y, pos.z, source.getYRot(), 0.0F);
		clone.setupBody(source.data().copy(), source.getCustomName(), source.skinIndex(), source.slimSkin());
		return level.addFreshEntity(clone) ? clone : null;
	}

	/**
	 * Accepts a Sect Hall candidate in place (DESIGN 20.2.7): it becomes {@code player}'s disciple (role DISCIPLE, a new
	 * soul, following), joins the list and is welcomed like a recruit. False with a chat reason when the limit is reached
	 * or it is no candidate any more.
	 */
	public static boolean recruitCandidate(ServerPlayer player, DiscipleEntity candidate) {
		if (candidate == null || candidate.role() != DiscipleEntity.Role.CANDIDATE || !candidate.isAlive() || candidate.isRemoved()
				|| !(candidate.level() instanceof ServerLevel level)) {
			return false;
		}
		CultivationData cultivation = Cultivators.get(player);
		int alive = aliveCount(cultivation);
		int cap = cap(player);
		if (alive >= cap) {
			Msg.chat(player, Component.translatable("kultivator.disciples.full", alive, cap).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		candidate.acceptAsDisciple(player);
		LOADED.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).add(candidate);
		ensureAlive(player, candidate);
		candidate.getLookControl().setLookAt(player, 30.0F, 30.0F);
		welcome(player, level, candidate, candidate.position(), alive, cap);
		return true;
	}

	// ================================================================== events

	private static void onLoad(Entity entity, ServerLevel level) {
		if (entity instanceof DiscipleEntity disciple) {
			UUID owner = disciple.ownerId();
			if (owner == null || disciple.role() != DiscipleEntity.Role.DISCIPLE) {
				return;
			}
			if (!disciple.isReleasing() && DiscipleRecords.get(level.getServer()).isForgotten(disciple.soulId())) {
				// Its master forgot it ("murid lupakan"): it bows once more and leaves.
				disciple.startRelease();
				return;
			}
			LOADED.computeIfAbsent(owner, id -> new HashSet<>()).add(disciple);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null && !disciple.isReleasing()) {
				ensureAlive(player, disciple);
			}
		} else if (entity instanceof ItemEntity item && item.getItem().is(DiscipleItems.DISCIPLE_TABLET)) {
			// A Soul Tablet lying around never despawns, can be seen through walls and never changes a block (portals,
			// pressure plates, tripwires), also when a player dropped it.
			item.setUnlimitedLifetime();
			item.setGlowingTag(true);
			item.addTag(NoGrief.TAG);
		}
	}

	private static void onUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof DiscipleEntity disciple)) {
			return;
		}
		UUID owner = disciple.ownerId();
		if (owner == null) {
			return;
		}
		Set<DiscipleEntity> set = LOADED.get(owner);
		if (set != null) {
			set.remove(disciple);
			if (set.isEmpty()) {
				LOADED.remove(owner);
			}
		}
		if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.departed()) {
			// Not a listed disciple, or it just left with Disciples.depart (its entry is already marked away).
			return;
		}
		if (disciple.isAlive() && !disciple.isReleasing()) {
			// Unloaded with its chunk: remember its name, rank and order for the list.
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				ensureAlive(player, disciple);
			}
		}
	}

	private static void onJoin(ServerPlayer player) {
		List<UUID> fallen = DiscipleRecords.get(player.level().getServer()).takePendingDeaths(player.getUUID());
		if (fallen == null) {
			return;
		}
		CultivationData cultivation = Cultivators.get(player);
		ListTag list = entries(cultivation);
		for (UUID soul : fallen) {
			CompoundTag entry = findEntry(list, soul);
			if (entry != null) {
				entry.remove("away");
				entry.remove("away_reason");
				entry.remove("away_until");
				entry.putBoolean("dead", true);
			}
		}
		pruneFallen(list);
		Cultivators.dirty(player);
	}

	/**
	 * The master left {@code origin}: the disciples at their side there right now (following orders, seen near them in
	 * the last 2 s, not busy) are noted, and follow at the end of this tick or, while the chunks around the master are
	 * still loading, during the next 5 s.
	 */
	private static void onChangeLevel(ServerPlayer player, ServerLevel origin) {
		// Phase 8 (DESIGN 25.2 item 9): secret realm companions go in and out through depart/arrive only.
		if (dev.kultivator.alam.Alam.isRealmLevel(origin) || dev.kultivator.alam.Alam.isRealmLevel(player.level())) {
			return;
		}
		Set<UUID> souls = new HashSet<>();
		for (DiscipleEntity disciple : loaded(player.getUUID())) {
			if (disciple.level() == origin && disciple.mode().follows() && disciple.wasNearOwner() && !disciple.isReleasing()
					&& !disciple.data().breakingThrough() && !disciple.holdsActivitySpot()) {
				souls.add(disciple.soulId());
			}
		}
		if (!souls.isEmpty()) {
			PENDING_FOLLOW.add(new FollowRequest(player.getUUID(), origin, souls, player.level().getServer().getTickCount() + FOLLOW_RETRY_TICKS));
		}
	}

	/** Followers of masters who changed dimension come along (direct teleport, never through a portal), retried a while. */
	private static void endTick(MinecraftServer server) {
		if (PENDING_FOLLOW.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (Iterator<FollowRequest> it = PENDING_FOLLOW.iterator(); it.hasNext(); ) {
			FollowRequest request = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(request.player());
			if (player == null || player.level() == request.origin() || now > request.deadline() || request.souls().isEmpty()) {
				it.remove();
				continue;
			}
			Set<DiscipleEntity> set = LOADED.get(request.player());
			if (player.isSpectator() || set == null) {
				continue;
			}
			for (DiscipleEntity disciple : List.copyOf(set)) {
				if (!request.souls().contains(disciple.soulId()) || disciple.level() != request.origin() || !disciple.isAlive()
						|| disciple.isRemoved() || disciple.isReleasing() || disciple.data().breakingThrough() || disciple.holdsActivitySpot()) {
					continue;
				}
				DiscipleEntity moved = disciple.followAcross(player);
				if (moved != null) {
					request.souls().remove(moved.soulId());
					Msg.actionBar(player, Component.translatable("kultivator.disciples.follow_realm", moved.getDisplayName())
							.withStyle(ChatFormatting.AQUA));
				}
			}
			if (request.souls().isEmpty()) {
				it.remove();
			}
		}
	}

	/**
	 * A monster died: every owned disciple within 16 blocks that hit it during the last 5 s, but did not land the last
	 * blow (that one gets the full reward in {@code killedEntity}), takes half the kill XP (DESIGN 18: "monster kills it
	 * takes part in").
	 */
	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Enemy) || victim instanceof DiscipleEntity || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Entity killer = source.getEntity();
		AABB box = victim.getBoundingBox().inflate(KILL_SHARE_RANGE);
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box, each -> each != killer && each.isAlive()
				&& each.ownerId() != null && !each.isReleasing() && each.role() == DiscipleEntity.Role.DISCIPLE)) {
			if (disciple.distanceToSqr(victim) > KILL_SHARE_RANGE * KILL_SHARE_RANGE || disciple.getLastHurtMob() != victim
					|| disciple.tickCount - disciple.getLastHurtMobTimestamp() >= KILL_SHARE_MEMORY) {
				continue;
			}
			DiscipleCultivation.addXp(disciple, victim.getMaxHealth() * DiscipleCultivation.KILL_XP * 0.5D, XpSource.COMBAT);
		}
	}

	// ================================================================== the master's list

	private static CompoundTag root(CultivationData cultivation) {
		CompoundTag extra = cultivation.extra();
		CompoundTag root = extra.getCompound(EXTRA).orElse(null);
		if (root == null) {
			root = new CompoundTag();
			extra.put(EXTRA, root);
		}
		return root;
	}

	private static ListTag entries(CultivationData cultivation) {
		CompoundTag root = root(cultivation);
		ListTag list = root.getList(LIST).orElse(null);
		if (list == null) {
			list = new ListTag();
			root.put(LIST, list);
		}
		return list;
	}

	private static @Nullable CompoundTag findEntry(ListTag list, UUID soul) {
		String id = soul.toString();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				return entry;
			}
		}
		return null;
	}

	/** Living disciples in the master's list. */
	public static int aliveCount(CultivationData cultivation) {
		int count = 0;
		for (Tag tag : entries(cultivation)) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("dead", false)) {
				count++;
			}
		}
		return count;
	}

	private static void writeEntry(CompoundTag entry, DiscipleEntity disciple) {
		entry.remove("away");
		entry.remove("away_reason");
		entry.remove("away_until");
		entry.putString("id", disciple.soulId().toString());
		entry.putInt("life", disciple.life());
		entry.putBoolean("dead", false);
		entry.putInt("step", disciple.data().rank().step());
		entry.putString("mode", disciple.mode().id());
		entry.store("name", ComponentSerialization.CODEC, disciple.getName());
	}

	/** Lists the disciple as living (adds it when missing) with its current name, rank and order. */
	static void ensureAlive(ServerPlayer player, DiscipleEntity disciple) {
		CultivationData cultivation = Cultivators.get(player);
		ListTag list = entries(cultivation);
		CompoundTag entry = findEntry(list, disciple.soulId());
		if (entry == null) {
			entry = new CompoundTag();
			list.add(entry);
		}
		writeEntry(entry, disciple);
		Cultivators.dirty(player);
	}

	private static void markDead(ServerPlayer player, DiscipleEntity disciple) {
		CultivationData cultivation = Cultivators.get(player);
		ListTag list = entries(cultivation);
		CompoundTag entry = findEntry(list, disciple.soulId());
		if (entry == null) {
			entry = new CompoundTag();
			list.add(entry);
		}
		writeEntry(entry, disciple);
		entry.putBoolean("dead", true);
		pruneFallen(list);
		Cultivators.dirty(player);
	}

	private static void removeEntry(ServerPlayer player, UUID soul) {
		ListTag list = entries(Cultivators.get(player));
		String id = soul.toString();
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				list.remove(i);
			}
		}
		Cultivators.dirty(player);
	}

	/** Forgets the oldest fallen entries beyond {@link #MAX_FALLEN_KEPT}. */
	private static void pruneFallen(ListTag list) {
		int fallen = 0;
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry && entry.getBooleanOr("dead", false)) {
				fallen++;
			}
		}
		for (int i = 0; i < list.size() && fallen > MAX_FALLEN_KEPT; ) {
			if (list.get(i) instanceof CompoundTag entry && entry.getBooleanOr("dead", false)) {
				list.remove(i);
				fallen--;
			} else {
				i++;
			}
		}
	}

	private static List<Component> takenNames(CultivationData cultivation) {
		List<Component> names = new ArrayList<>();
		for (Tag tag : entries(cultivation)) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("dead", false)) {
				entry.read("name", ComponentSerialization.CODEC).ifPresent(names::add);
			}
		}
		return names;
	}

	private static @Nullable DiscipleEntity findLoaded(UUID owner, UUID soul) {
		Set<DiscipleEntity> set = LOADED.get(owner);
		if (set == null) {
			return null;
		}
		for (DiscipleEntity disciple : set) {
			if (!disciple.isRemoved() && disciple.soulId().equals(soul)) {
				return disciple;
			}
		}
		return null;
	}

	/** The master's disciples loaded right now (any dimension). */
	public static List<DiscipleEntity> loaded(UUID owner) {
		Set<DiscipleEntity> set = LOADED.get(owner);
		if (set == null) {
			return List.of();
		}
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity disciple : set) {
			if (!disciple.isRemoved() && disciple.isAlive()) {
				list.add(disciple);
			}
		}
		return list;
	}

	// ================================================================== commands

	/** Adds "murid ..." to /cultivate and the disciple debug commands. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> murid = Commands.literal("murid");
		murid.then(Commands.literal("list").executes(ctx -> list(ctx.getSource().getPlayerOrException())));
		murid.then(Commands.literal("buka").executes(ctx -> {
			openFromKey(ctx.getSource().getPlayerOrException(), "");
			return 1;
		}).then(Commands.argument("nama", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
			openFromKey(ctx.getSource().getPlayerOrException(), "name:" + com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nama"));
			return 1;
		})));
		murid.then(Commands.literal("panggil").executes(ctx -> call(ctx.getSource().getPlayerOrException())));
		murid.then(Commands.literal("ikut").executes(ctx -> orderCommand(ctx.getSource().getPlayerOrException(), DiscipleEntity.Mode.FOLLOW)));
		murid.then(Commands.literal("tinggal").executes(ctx -> orderCommand(ctx.getSource().getPlayerOrException(), DiscipleEntity.Mode.STAY)));
		murid.then(Commands.literal("meditasi").executes(ctx -> orderCommand(ctx.getSource().getPlayerOrException(), DiscipleEntity.Mode.MEDITATE)));
		murid.then(Commands.literal("bantu").executes(ctx -> orderCommand(ctx.getSource().getPlayerOrException(), DiscipleEntity.Mode.ASSIST)));
		murid.then(Commands.literal("lupakan").then(Commands.argument("nomor", IntegerArgumentType.integer(1))
				.executes(ctx -> forget(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "nomor"), false))
				.then(Commands.literal("konfirmasi")
						.executes(ctx -> forget(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "nomor"), true)))));
		root.then(murid);

		LiteralArgumentBuilder<CommandSourceStack> muridDebug = Commands.literal("murid");
		muridDebug.then(Commands.literal("summon").executes(ctx -> recruit(ctx.getSource().getPlayerOrException()) ? 1 : 0));
		muridDebug.then(Commands.literal("xp")
				.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D)).executes(Disciples::debugXp)));
		// "murid set realm ..." (DESIGN 18) and the short "murid realm ...".
		muridDebug.then(Commands.literal("set").then(realmNode()));
		muridDebug.then(realmNode());
		debug.then(muridDebug);
	}

	/** {@code realm <realm> [stage] [layer]} for the nearest or looked-at disciple. */
	private static LiteralArgumentBuilder<CommandSourceStack> realmNode() {
		return Commands.literal("realm")
				.then(Commands.argument("realm", StringArgumentType.word()).suggests(REALMS)
						.executes(ctx -> debugRealm(ctx, 1, 1))
						.then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
								.executes(ctx -> debugRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage"), 1))
								.then(Commands.argument("layer", IntegerArgumentType.integer(1, 9))
										.executes(ctx -> debugRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage"),
												IntegerArgumentType.getInteger(ctx, "layer"))))));
	}

	/** {@code murid list}: number, name, rank, order and where each disciple is (fallen ones too). */
	private static int list(ServerPlayer player) {
		CultivationData cultivation = Cultivators.get(player);
		ListTag list = entries(cultivation);
		if (list.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.disciples.list.empty").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		player.sendSystemMessage(Component.translatable("kultivator.disciples.list.header", aliveCount(cultivation), cap(player))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		boolean missing = false;
		for (int index = 0; index < list.size(); index++) {
			if (!(list.get(index) instanceof CompoundTag entry)) {
				continue;
			}
			UUID soul = parseUuid(entry.getStringOr("id", ""));
			DiscipleEntity loaded = soul == null ? null : findLoaded(player.getUUID(), soul);
			Component name;
			Rank rank;
			Component mode;
			Component where;
			if (loaded != null) {
				name = loaded.getDisplayName();
				rank = loaded.data().rank();
				mode = loaded.mode().displayName();
				where = loaded.level() == player.level()
						? Component.translatable("kultivator.disciples.list.distance", (int) Math.round(Math.sqrt(loaded.distanceToSqr(player))))
						: Component.translatable("kultivator.disciples.list.elsewhere");
			} else {
				name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
				rank = Rank.ofStep(entry.getIntOr("step", 0));
				DiscipleEntity.Mode saved = DiscipleEntity.Mode.byId(entry.getStringOr("mode", ""));
				mode = (saved == null ? DiscipleEntity.Mode.FOLLOW : saved).displayName();
				boolean fallen = entry.getBooleanOr("dead", false);
				boolean away = !fallen && entry.getBooleanOr("away", false);
				if (away && entry.contains("away_until")) {
					// "Sedang Ekspedisi · sisa 0,4 hari" (DESIGN 20.3.10).
					String reason = entry.getStringOr("away_reason", "other");
					long left = Math.max(0L, entry.getLongOr("away_until", 0L) - player.level().getServer().overworld().getGameTime());
					where = Component.translatable("kultivator.spine6.list.away_reason",
							Component.translatableWithFallback("kultivator.hall.away." + reason, reason),
							String.format(java.util.Locale.ROOT, "%.1f", left / 24000.0D));
				} else {
					where = away ? Component.translatable("kultivator.spine6.list.away")
							: Component.translatable(fallen ? "kultivator.disciples.list.fallen" : "kultivator.disciples.list.unloaded");
				}
				missing |= !fallen && !away;
			}
			boolean dead = loaded == null && entry.getBooleanOr("dead", false);
			MutableComponent line = Component.literal((index + 1) + ". ").append(Component.translatable("kultivator.disciples.list.entry", name,
					rank.displayName().withColor(rank.realm().color()), mode, where));
			player.sendSystemMessage(line.withStyle(dead ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
		}
		if (missing) {
			player.sendSystemMessage(Component.translatable("kultivator.disciples.list.forget_hint").withStyle(ChatFormatting.DARK_GRAY,
					ChatFormatting.ITALIC));
		}
		return 1;
	}

	/**
	 * {@code murid lupakan <n> [konfirmasi]}: the escape hatch for a living entry whose body is lost for good (no body is
	 * loaded anywhere). Without {@code konfirmasi} it only warns. The entry is removed, which frees its place; the soul is
	 * remembered as forgotten, so a body of it that loads again takes its leave and an old tablet of it is only an echo.
	 */
	private static int forget(ServerPlayer player, int number, boolean confirmed) {
		ListTag list = entries(Cultivators.get(player));
		CompoundTag entry = number >= 1 && number <= list.size() && list.get(number - 1) instanceof CompoundTag tag ? tag : null;
		if (entry == null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.forget.bad_index", number).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
		if (entry.getBooleanOr("dead", false)) {
			Msg.chat(player, Component.translatable("kultivator.disciples.forget.fallen", name).withStyle(ChatFormatting.GRAY));
			return 0;
		}
		UUID soul = parseUuid(entry.getStringOr("id", ""));
		if (soul != null && findLoaded(player.getUUID(), soul) != null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.forget.loaded", name).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (entry.getBooleanOr("away", false)) {
			// Away on an errand (expedition, soul flight): its body is not lost, it comes back by itself.
			Msg.chat(player, Component.translatable("kultivator.spine6.forget.away", name).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (!confirmed) {
			Msg.chat(player, Component.translatable("kultivator.disciples.forget.warn", name, number).withStyle(ChatFormatting.GOLD));
			return 0;
		}
		list.remove(number - 1);
		Cultivators.dirty(player);
		if (soul != null) {
			DiscipleRecords.get(player.level().getServer()).forget(soul);
		}
		Msg.chat(player, Component.translatable("kultivator.disciples.forget.done", name).withStyle(ChatFormatting.DARK_AQUA));
		return 1;
	}

	/**
	 * {@code murid panggil}: every loaded disciple comes to the master and follows again, from another dimension too
	 * (direct teleport, never through a portal).
	 */
	private static int call(ServerPlayer player) {
		int count = 0;
		for (DiscipleEntity disciple : loaded(player.getUUID())) {
			if (disciple.isReleasing() || disciple.data().breakingThrough() || DuelRules.inDuel(disciple)) {
				continue;
			}
			DiscipleEntity called = disciple;
			if (disciple.level() != player.level()) {
				// Across dimensions only when it really arrives (a guard that cannot come keeps its post).
				called = player.isSpectator() ? null : disciple.followAcross(player);
				if (called == null) {
					continue;
				}
			} else {
				disciple.setTarget(null);
				disciple.teleportNear(player);
			}
			if (!called.mode().follows()) {
				called.setMode(DiscipleEntity.Mode.FOLLOW);
			}
			if (called.holdsActivitySpot()) {
				// Called away from a duty post, the tower or the stands.
				called.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			called.setTarget(null);
			count++;
		}
		if (count == 0) {
			Msg.chat(player, Component.translatable("kultivator.disciples.call.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Msg.chat(player, Component.translatable("kultivator.disciples.call.done", count).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		return count;
	}

	/**
	 * {@code murid ikut|tinggal|meditasi|bantu}: the disciple looked at, else the nearest own disciple within 16 blocks
	 * (DESIGN 18). A guard posted far away is never pulled off its post; {@code murid panggil} calls everyone.
	 */
	private static int orderCommand(ServerPlayer player, DiscipleEntity.Mode mode) {
		DiscipleEntity target = lookedAt(player, disciple -> disciple.isOwnedBy(player) && !disciple.isReleasing()
				&& disciple.role() == DiscipleEntity.Role.DISCIPLE);
		if (target == null) {
			AABB box = player.getBoundingBox().inflate(ACTION_RANGE);
			double bestSq = ACTION_RANGE * ACTION_RANGE;
			for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, box, each -> each.isAlive()
					&& each.isOwnedBy(player) && !each.isReleasing() && !each.data().breakingThrough()
					&& each.role() == DiscipleEntity.Role.DISCIPLE)) {
				double distanceSq = disciple.distanceToSqr(player);
				if (distanceSq <= bestSq) {
					bestSq = distanceSq;
					target = disciple;
				}
			}
		}
		if (target == null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.order.none", (int) ACTION_RANGE).withStyle(ChatFormatting.GRAY));
			return 0;
		}
		order(player, target, mode);
		return 1;
	}

	private static int debugXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity disciple = debugTarget(player);
		if (disciple == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.disciples.debug.no_target"));
			return 0;
		}
		DiscipleCultivation.addXp(disciple, DoubleArgumentType.getDouble(ctx, "amount"), XpSource.EXACT);
		disciple.refreshSynced();
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.disciples.debug.done", disciple.getDisplayName())), false);
		return 1;
	}

	private static int debugRealm(CommandContext<CommandSourceStack> ctx, int stage, int layer) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "realm");
		Realm realm = Realm.byId(id);
		if (realm == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.disciples.debug.unknown", id));
			return 0;
		}
		DiscipleEntity disciple = debugTarget(player);
		if (disciple == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.disciples.debug.no_target"));
			return 0;
		}
		DiscipleData data = disciple.data();
		for (Realm each : Realm.values()) {
			if (each.isMortal()) {
				continue;
			}
			if (each.ordinal() <= realm.ordinal()) {
				// Every realm up to the new one counts as reached (Normal quality unless one is already recorded).
				if (data.quality(each) == null) {
					data.setQuality(each, BreakthroughQuality.NORMAL);
				}
			} else {
				data.setQuality(each, null);
			}
		}
		data.setXp(0.0D);
		// Clamps xp and qi, resets the bottleneck notice, refreshes the synced data and the attributes.
		DiscipleCultivation.setRank(disciple, new Rank(realm, layer - 1, stage - 1));
		data.setQi(DiscipleCultivation.maxQi(data));
		disciple.setHealth(disciple.getMaxHealth());
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.disciples.debug.done", disciple.getDisplayName())), false);
		return 1;
	}

	/** The disciple an operator looks at within 16 blocks, else the nearest one within 16 blocks. */
	private static @Nullable DiscipleEntity debugTarget(ServerPlayer player) {
		DiscipleEntity looked = lookedAt(player, disciple -> true);
		if (looked != null) {
			return looked;
		}
		AABB box = player.getBoundingBox().inflate(ACTION_RANGE);
		DiscipleEntity best = null;
		double bestSq = ACTION_RANGE * ACTION_RANGE;
		for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, box, DiscipleEntity::isAlive)) {
			double distanceSq = disciple.distanceToSqr(player);
			if (distanceSq <= bestSq) {
				bestSq = distanceSq;
				best = disciple;
			}
		}
		return best;
	}

	/** The disciple under the crosshair within 16 blocks (not behind a wall) that passes {@code filter}, or null. */
	private static @Nullable DiscipleEntity lookedAt(ServerPlayer player, Predicate<DiscipleEntity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(ACTION_RANGE));
		BlockHitResult block = Targeting.blockHit(player, ACTION_RANGE);
		double maxSq = block.getType() == HitResult.Type.MISS ? ACTION_RANGE * ACTION_RANGE : block.getLocation().distanceToSqr(eye);
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(ACTION_RANGE)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> entity instanceof DiscipleEntity disciple && disciple.isAlive() && filter.test(disciple), maxSq);
		return hit != null && hit.getEntity() instanceof DiscipleEntity disciple ? disciple : null;
	}

	private static @Nullable UUID parseUuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private Disciples() {
	}
}

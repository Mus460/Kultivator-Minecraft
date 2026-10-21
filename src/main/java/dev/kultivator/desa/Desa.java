package dev.kultivator.desa;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.data.RootGrade;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.HallLevel;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Desa Bawahan, the vassal villages (DESIGN 25.6, owned by P8-desa): the plaque and the pledge ({@link Pledge}),
 * loyalty, tribute ({@link Tribute}), candidates from villages, help requests ({@link Requests}), village raids
 * ({@link VillageRaids}) and the Pelindung Desa ({@link VillageGuards}). All village logic runs for online owners only,
 * every 20 ticks (live parts) and every 100 ticks (schedules), lazily on the overworld day. The progression glue of 25.8
 * lives in {@code goals.*} and {@code alchemy.*}.
 */
public final class Desa {

	/** Gifts give at most this much loyalty per day (DESIGN 25.6.3). */
	public static final int GIFT_MAX_PER_DAY = 5;
	public static final int GIFT_POINTS = 8;
	public static final int CANDIDATE_LOYALTY = 50;
	public static final int CANDIDATE_RESOLVE = 10;
	public static final int CANDIDATE_MIN_TEKAD = 40;
	public static final int TALENT_DAYS = 9;
	/** Kehancuran never carves within this radius of a pledged bell. */
	public static final double ZONE_RADIUS = 48.0D;

	private static final Set<UUID> DIRTY = new HashSet<>();

	// ================================================================== init

	/** Registers the plaque, the vassal data and the hooks; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		DesaItems.init();
		ServerLifecycleEvents.SERVER_STARTED.register(Vassals::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VillageRaids.clear();
			Requests.clearAll();
			VillageGuards.clear();
			DIRTY.clear();
			Vassals.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(Desa::tickServer);
		UseBlockCallback.EVENT.register(Desa::onUseBlock);
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			MinecraftServer server = Vassals.server();
			if (server == null || victim.level().isClientSide()) {
				return;
			}
			Requests.onDeath(server, victim);
			if (victim instanceof DiscipleEntity disciple) {
				VillageGuards.onDiscipleDeath(disciple);
			}
		});
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !entity.entityTags().contains(Requests.QUEST_TAG) || Requests.wanted(entity));
		ServerPlayerEvents.LEAVE.register(player -> VillageGuards.onLeave(player.getUUID()));
		ServerPlayerEvents.JOIN.register(player -> dirty(player.getUUID()));
		ProtectedZones.addZoneProvider(Desa::protects);
		DiscipleHooks.addDeathGuard(VillageGuards::save);
		SectRaids.addListener(new SectRaids.RaidListener() {
			@Override
			public void onEnd(UUID owner, boolean repelled) {
				MinecraftServer server = Vassals.server();
				if (server != null) {
					allLoyalty(server, owner, repelled ? 3 : -10);
				}
			}
		});
		Halls.addInfoProvider((viewer, owner, info) -> info.put("desa", syncTag(owner, viewer.getUUID().equals(owner), true)));
	}

	private static boolean protects(ServerLevel level, BlockPos pos) {
		if (!level.dimension().equals(Level.OVERWORLD)) {
			return false;
		}
		for (BlockPos bell : Vassals.bells()) {
			if (bell.distSqr(pos) <= ZONE_RADIUS * ZONE_RADIUS) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== server tick

	private static void tickServer(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 20 != 3) {
			return;
		}
		VillageRaids.tick(server);
		if (!KultivatorConfig.get().vassalVillages) {
			flushDirty(server);
			return;
		}
		long gameTime = server.overworld().getGameTime();
		boolean schedule = tick % 100 == 3;
		boolean scan = tick % 200 == 3;
		long day = Vassals.day();
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			UUID owner = player.getUUID();
			Vassals.Owner entry = Vassals.peek(owner);
			if (entry == null) {
				continue;
			}
			VillageGuards.tickReturns(owner, player);
			for (Village village : List.copyOf(entry.villages)) {
				VillageGuards.tick(server, owner, village, player);
				if (village.pledged) {
					Requests.tick(server, owner, village, player, gameTime);
				} else {
					trustCheck(server, owner, village, player, day);
				}
			}
			if (schedule) {
				schedule(server, owner, entry, player, day, scan);
			}
		}
		flushDirty(server);
	}

	private static void trustCheck(MinecraftServer server, UUID owner, Village village, ServerPlayer player, long day) {
		if (village.trustTried == day || !Vassals.night() || player.level() != server.overworld() || !active(player)
				|| player.position().distanceToSqr(Vec3.atCenterOf(village.plaque)) > Pledge.TRUST_RANGE * Pledge.TRUST_RANGE
				|| VillageRaids.siegeAt(owner, village.id) != null) {
			return;
		}
		village.trustTried = day;
		Vassals.changed();
		if (server.overworld().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			Pledge.complete(server, owner, village);
			return;
		}
		VillageRaids.start(server, owner, village, VillageRaids.KIND_TRUST);
	}

	/** Every 100 ticks per online owner: hall level-ups, the daily loyalty steps, tribute, requests, raids, scans, candidates. */
	private static void schedule(MinecraftServer server, UUID owner, Vassals.Owner entry, ServerPlayer player, long day, boolean scan) {
		Halls.HallView hall = Halls.hallOf(owner);
		int hallLevel = hall == null ? 0 : hall.level();
		if (entry.hallLevel > 0 && hallLevel > entry.hallLevel) {
			allLoyalty(server, owner, 3);
		}
		if (entry.hallLevel != hallLevel) {
			entry.hallLevel = hallLevel;
			Vassals.changed();
		}
		ServerLevel overworld = server.overworld();
		boolean changed = false;
		for (Village village : List.copyOf(entry.villages)) {
			if (!village.pledged) {
				continue;
			}
			changed |= daily(server, owner, village, hall, day);
			if (!entry.villages.contains(village)) {
				continue;
			}
			changed |= Tribute.process(server, owner, village, day);
			changed |= Requests.schedule(server, owner, village, day, player);
			changed |= VillageRaids.checkRaid(server, owner, village, day, player);
			if (scan && player.level() == overworld && player.position().distanceToSqr(Vec3.atCenterOf(village.bell)) <= 64.0D * 64.0D
					&& overworld.isLoaded(village.bell)) {
				changed |= visit(server, owner, village);
			}
		}
		candidates(server, owner, entry, day);
		if (changed) {
			Vassals.changed();
			dirty(owner);
		}
	}

	/** The day steps since the last pass (at most 9): +2 with a Pelindung Desa, -1 while the hall is dormant. */
	private static boolean daily(MinecraftServer server, UUID owner, Village village, Halls.HallView hall, long day) {
		if (village.lastDaily >= day) {
			return false;
		}
		long days = village.lastDaily < 0L ? 1L : Math.min(9L, day - village.lastDaily);
		village.lastDaily = day;
		int delta = 0;
		if (village.guard != null) {
			delta += 2 * (int) days;
		}
		if (hall == null || !hall.active()) {
			delta -= (int) days;
		}
		if (delta != 0) {
			addLoyalty(server, owner, village, delta);
		}
		return true;
	}

	/** The owner is near the bell: recount villagers and notice a lost (or found again) village. */
	private static boolean visit(MinecraftServer server, UUID owner, Village village) {
		ServerLevel level = server.overworld();
		boolean bell = VillageScan.bellStands(level, village.bell);
		int count = VillageScan.villagersOf(level, village.bell).size();
		boolean changed = count != village.villagers;
		village.villagers = count;
		if (!bell && count == 0 && !village.lost) {
			village.lost = true;
			changed = true;
			Component line = Component.translatable("kultivator.desa.lost", village.name);
			Halls.log(owner, line);
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				Msg.chat(player, line.copy().withStyle(ChatFormatting.RED));
			}
		} else if (village.lost && bell && count > 0) {
			village.lost = false;
			changed = true;
		}
		return changed;
	}

	/** On an arrival day of the hall: one youth per vassal at loyalty 50+, and now and then a talented one at loyalty 100. */
	private static void candidates(MinecraftServer server, UUID owner, Vassals.Owner entry, long day) {
		if (entry.candDay >= day || entry.villages.isEmpty()) {
			return;
		}
		CompoundTag core = HallData.get(server).core(owner);
		if (core == null || core.getLongOr("last_arrival", -1L) != day || core.getBooleanOr("dormant", false)) {
			return;
		}
		entry.candDay = day;
		Vassals.changed();
		if (Candidates.isWarDay(owner)) {
			return;
		}
		int room = HallLevel.MAX_CANDIDATES - core.getListOrEmpty("candidates").size();
		RandomSource random = server.overworld().getRandom();
		List<Halls.CandidateSpec> specs = new ArrayList<>();
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		for (Village village : entry.villages) {
			if (!village.pledged || village.lost || village.loyalty < CANDIDATE_LOYALTY) {
				continue;
			}
			if (room > 0) {
				specs.add(villageYouth(random, village, false));
				room--;
			}
			if (village.loyalty >= 100 && day - village.talentDay >= TALENT_DAYS) {
				village.talentDay = day;
				specs.add(villageYouth(random, village, true));
				if (player != null) {
					Msg.chat(player, Component.translatable("kultivator.desa.talent", village.name).withStyle(ChatFormatting.GOLD));
				}
			}
		}
		if (!specs.isEmpty()) {
			Halls.addCandidates(owner, specs);
		}
	}

	private static Halls.CandidateSpec villageYouth(RandomSource random, Village village, boolean talented) {
		DiscipleData data = DiscipleData.fresh(random);
		if (talented && data.rootGrade().ordinal() < RootGrade.MID.ordinal()) {
			data.setRootGrade(RootGrade.MID);
		}
		// Tekad rolls 20-100; with the +10 of the village the total is never below 40 (DESIGN 25.6.5).
		int tekad = Math.max(CANDIDATE_MIN_TEKAD - CANDIDATE_RESOLVE, 20 + random.nextInt(81));
		return new Halls.CandidateSpec(data, tekad, -1.0D, -1.0D, false, "village", village.name, CANDIDATE_RESOLVE);
	}

	// ================================================================== loyalty

	/** Changes a village's loyalty (0-100): 100 grants {@code village_loyal}, 0 renounces the pledge. */
	static void addLoyalty(MinecraftServer server, UUID owner, Village village, int delta) {
		if (!village.pledged || delta == 0) {
			return;
		}
		int before = village.loyalty;
		village.loyalty = Math.max(0, Math.min(100, before + delta));
		Vassals.changed();
		dirty(owner);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (village.loyalty >= 100 && before < 100 && player != null) {
			Advancements.grant(player, "village_loyal");
		}
		if (village.loyalty <= 0) {
			Pledge.forget(server, owner, village, "kultivator.desa.renounced");
		}
	}

	private static void allLoyalty(MinecraftServer server, UUID owner, int delta) {
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry == null) {
			return;
		}
		for (Village village : List.copyOf(entry.villages)) {
			addLoyalty(server, owner, village, delta);
		}
	}

	// ================================================================== the plaque

	/** Using the plaque: its village card in chat. */
	static void onPlaqueUsed(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Map.Entry<UUID, Village> found = Vassals.byPlaque(pos);
		if (found == null) {
			Msg.chat(player, Component.translatable("kultivator.desa.card.inert").withStyle(ChatFormatting.GRAY));
			return;
		}
		Village village = found.getValue();
		if (!found.getKey().equals(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.desa.card.other", village.name).withStyle(ChatFormatting.GRAY));
			return;
		}
		card(player, village);
	}

	/** The village card lines (plaque use, {@code desa info}). */
	static void card(ServerPlayer player, Village village) {
		long day = Vassals.day();
		Msg.chat(player, Component.translatable("kultivator.desa.card.title", village.name).withStyle(ChatFormatting.GOLD));
		if (!village.pledged) {
			Msg.chat(player, Component.translatable("kultivator.desa.card.waiting", (int) Pledge.TRUST_RANGE).withStyle(ChatFormatting.GRAY));
			return;
		}
		Msg.chat(player, Component.translatable("kultivator.desa.card.line", village.loyalty, village.villagers,
				Math.max(0L, village.nextTribute - day)).withStyle(ChatFormatting.WHITE));
		if (village.lost) {
			Msg.chat(player, Component.translatable("kultivator.desa.card.lost").withStyle(ChatFormatting.RED));
		}
		if (village.hasRequest()) {
			Msg.chat(player, Component.translatable("kultivator.desa.card.request", Component.translatable("kultivator.desa.request." + village.reqType),
					Math.max(0L, village.reqExpires - day)).withStyle(ChatFormatting.YELLOW));
		}
		Msg.chat(player, village.guard == null ? Component.translatable("kultivator.desa.card.no_guard").withStyle(ChatFormatting.GRAY)
				: Component.translatable("kultivator.desa.card.guard", village.guardName).withStyle(ChatFormatting.GRAY));
	}

	/** Sneak-use with an item: quest hand-ins first, then gifts (vanilla skips {@code useItemOn} while sneaking). */
	private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player, Level level, InteractionHand hand,
			net.minecraft.world.phys.BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isSecondaryUseActive() || player.getMainHandItem().isEmpty()
				|| !level.getBlockState(hit.getBlockPos()).is(DesaItems.SECT_PATRON_PLAQUE)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Map.Entry<UUID, Village> found = Vassals.byPlaque(hit.getBlockPos());
		if (found == null || !found.getKey().equals(serverPlayer.getUUID()) || !found.getValue().pledged) {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.desa.gift.not_yours"));
			return InteractionResult.SUCCESS;
		}
		Village village = found.getValue();
		ItemStack held = serverPlayer.getMainHandItem();
		if (Requests.handIn(serverLevel.getServer(), serverPlayer.getUUID(), village, serverPlayer, held)) {
			return InteractionResult.SUCCESS;
		}
		gift(serverLevel.getServer(), serverPlayer, village, held);
		return InteractionResult.SUCCESS;
	}

	/** A gift of food at the plaque: +1 loyalty per 8 food points (Hidangan Roh x4), at most +5 per day. */
	private static void gift(MinecraftServer server, ServerPlayer player, Village village, ItemStack held) {
		int points = giftPoints(held);
		if (points <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.desa.gift.what"));
			return;
		}
		long day = Vassals.day();
		if (village.giftDay != day) {
			village.giftDay = day;
			village.giftToday = 0;
		}
		int room = GIFT_MAX_PER_DAY - village.giftToday;
		if (room <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.desa.gift.full", GIFT_MAX_PER_DAY));
			return;
		}
		int total = points * held.getCount();
		int gain = Math.min(room, total / GIFT_POINTS);
		if (gain <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.desa.gift.more", GIFT_POINTS));
			return;
		}
		int take = Math.min(held.getCount(), (gain * GIFT_POINTS + points - 1) / points);
		held.consume(take, player);
		village.giftToday += gain;
		addLoyalty(server, player.getUUID(), village, gain);
		player.level().playSound(null, village.plaque, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		Msg.actionBar(player, Component.translatable("kultivator.desa.gift.done", village.name, gain, village.loyalty).withStyle(ChatFormatting.GREEN));
	}

	private static int giftPoints(ItemStack stack) {
		if (stack.is(Items.BREAD) || stack.is(Items.BAKED_POTATO)) {
			return foodPoints(stack);
		}
		if (stack.is(Items.CAKE)) {
			return 14;
		}
		if (stack.is(SectEventItems.SPIRIT_MEAL)) {
			return Math.max(6, foodPoints(stack)) * 4;
		}
		return 0;
	}

	/** Food points of one item (its nutrition; cake 14; Hidangan Roh x4), 0 for non-food. */
	static int foodPoints(ItemStack stack) {
		if (stack.is(Items.CAKE)) {
			return 14;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		int nutrition = food == null ? 0 : Math.max(0, food.nutrition());
		return stack.is(SectEventItems.SPIRIT_MEAL) ? Math.max(6, nutrition) * 4 : nutrition;
	}

	/** True when the stack is the mod's pill {@code kultivator:<id>}. */
	static boolean isPill(ItemStack stack, String id) {
		Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return Kultivator.MOD_ID.equals(key.getNamespace()) && id.equals(key.getPath());
	}

	// ================================================================== helpers

	/** True when the player pressed something in the last {@code raidAfkSeconds} (an AFK owner is never attacked live). */
	static boolean active(ServerPlayer player) {
		return Util.getMillis() - player.getLastActionTime() <= KultivatorConfig.get().raidAfkSeconds * 1000L;
	}

	/** {@code count} of a mod item looked up by id (another module's item), or empty when it is not registered. */
	static ItemStack registryStack(String path, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id(path));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	/** Into the inventory, the rest at the feet (block-safe item entity). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	/** Marks the owner's Desa data for a resync (sent within a second when online). */
	static void dirty(UUID owner) {
		if (owner != null) {
			DIRTY.add(owner);
		}
	}

	private static void flushDirty(MinecraftServer server) {
		if (DIRTY.isEmpty()) {
			return;
		}
		for (UUID owner : List.copyOf(DIRTY)) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_DESA, syncTag(owner, true, false));
			}
		}
		DIRTY.clear();
	}

	/**
	 * The data of the Desa tablets (Dunia screen and hall page); {@code own} adds the action rights. {@code withLog} adds
	 * the raid log, which only the hall page draws (the Dunia sync leaves it out to stay small).
	 */
	static CompoundTag syncTag(UUID owner, boolean own, boolean withLog) {
		CompoundTag tag = new CompoundTag();
		MinecraftServer server = Vassals.server();
		KultivatorConfig config = KultivatorConfig.get();
		tag.putBoolean("on", config.vassalVillages);
		tag.putBoolean("own", own);
		Halls.HallView hall = Halls.hallOf(owner);
		int hallLevel = hall == null ? 0 : hall.level();
		tag.putInt("hall_level", hallLevel);
		tag.putInt("max", Pledge.maxVassals(hallLevel));
		tag.putInt("count", Vassals.count(owner));
		tag.putInt("range", config.vassalRange);
		long day = Vassals.day();
		tag.putLong("day", day);
		ListTag list = new ListTag();
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry != null && server != null) {
			for (Village village : entry.villages) {
				CompoundTag v = new CompoundTag();
				v.putInt("id", village.id);
				v.putString("name", village.name);
				v.putBoolean("pledged", village.pledged);
				v.putBoolean("lost", village.lost);
				v.putInt("loyalty", village.loyalty);
				v.putInt("villagers", village.villagers);
				v.putLong("tribute_in", Math.max(0L, village.nextTribute - day));
				v.putInt("owed", Tribute.owedCount(server, village));
				v.putIntArray("bell", new int[]{village.bell.getX(), village.bell.getY(), village.bell.getZ()});
				if (village.hasRequest()) {
					v.putString("req", village.reqType);
					v.putLong("req_in", Math.max(0L, village.reqExpires - day));
					v.putInt("req_progress", village.reqProgress);
					v.putInt("req_need", Village.REQ_HARVEST.equals(village.reqType) ? Requests.HARVEST_FOOD
							: Village.REQ_PLAGUE.equals(village.reqType) ? Requests.PLAGUE_HEALING : 0);
				}
				if (village.guard != null) {
					v.putString("guard", village.guardName);
					v.putBoolean("guard_here", village.guardPresent());
				}
				v.putLong("last_raid", village.lastRaid);
				v.putInt("last_result", village.lastRaidResult);
				v.putBoolean("threat", Vassals.threatened(owner, village));
				v.putBoolean("siege", VillageRaids.siegeAt(owner, village.id) != null);
				list.add(v);
			}
			if (withLog) {
				tag.put("log", entry.log.copy());
			}
		}
		tag.put("villages", list);
		return tag;
	}

	// ================================================================== actions and commands

	/** Action {@code DESA}: text = "op[:param]"; "sync" answers with {@code DuniaSync} channel {@code CH_DESA}. */
	public static void handleAction(ServerPlayer player, String text) {
		if (text == null) {
			return;
		}
		UUID owner = player.getUUID();
		if ("sync".equals(text)) {
			Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_DESA, syncTag(owner, true, false));
			return;
		}
		MinecraftServer server = player.level().getServer();
		String[] parts = text.split(":", 3);
		if (parts.length < 2) {
			return;
		}
		int id;
		try {
			id = Integer.parseInt(parts[1]);
		} catch (NumberFormatException exception) {
			return;
		}
		Village village = Vassals.find(owner, id);
		if (village == null) {
			return;
		}
		switch (parts[0]) {
			case "guard" -> VillageGuards.assign(server, owner, village, player);
			case "release" -> VillageGuards.release(server, owner, village, player);
			case "letter" -> {
				if (village.hasRequest() && !hasLetter(player, village)) {
					give(player, Requests.letter(village));
				}
			}
			case "rename" -> {
				if (parts.length == 3) {
					rename(player, village, parts[2]);
				}
			}
			default -> {
			}
		}
		dirty(owner);
	}

	private static boolean hasLetter(ServerPlayer player, Village village) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(DesaItems.VILLAGE_LETTER) && village.name.equals(stack.getOrDefault(DataComponents.CUSTOM_DATA,
					net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getStringOr("village", ""))) {
				return true;
			}
		}
		return false;
	}

	private static void rename(ServerPlayer player, Village village, String raw) {
		String name = raw.strip().replaceAll("[\\p{Cntrl}§]", "");
		if (name.isEmpty() || name.length() > 24) {
			Msg.chat(player, Component.translatable("kultivator.desa.rename.bad").withStyle(ChatFormatting.YELLOW));
			return;
		}
		String old = village.name;
		village.name = name;
		Vassals.changed();
		Msg.chat(player, Component.translatable("kultivator.desa.rename.done", old, name).withStyle(ChatFormatting.GREEN));
	}

	/** {@code /kultivasi desa ...} and {@code debug desa ...} (DESIGN 25.11). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("desa")
				.executes(ctx -> list(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("info")
						.then(Commands.argument("nama", StringArgumentType.greedyString())
								.suggests((ctx, builder) -> {
									try {
										for (VillageView view : Vassals.views(ctx.getSource().getPlayerOrException().getUUID())) {
											builder.suggest(view.name());
										}
									} catch (Exception ignored) {
										// Not a player: no suggestions.
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Village village = byName(player.getUUID(), StringArgumentType.getString(ctx, "nama"));
									if (village == null) {
										ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.desa.cmd.unknown")));
										return 0;
									}
									card(player, village);
									return 1;
								})))
				.then(Commands.literal("nama")
						.then(Commands.argument("lama", StringArgumentType.word())
								.then(Commands.argument("baru", StringArgumentType.greedyString())
										.executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											Village village = byName(player.getUUID(), StringArgumentType.getString(ctx, "lama"));
											if (village == null) {
												ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.desa.cmd.unknown")));
												return 0;
											}
											rename(player, village, StringArgumentType.getString(ctx, "baru"));
											dirty(player.getUUID());
											return 1;
										})))));
		debug.then(Commands.literal("desa")
				.then(Commands.literal("poi").executes(ctx -> DesaTests.debugPoi(ctx.getSource().getPlayerOrException()))));
	}

	static Village byName(UUID owner, String name) {
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry != null) {
			for (Village village : entry.villages) {
				if (village.name.equalsIgnoreCase(name.strip())) {
					return village;
				}
			}
		}
		return null;
	}

	private static int list(ServerPlayer player) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		int hallLevel = hall == null ? 0 : hall.level();
		Msg.chat(player, Component.translatable("kultivator.desa.cmd.header", Vassals.count(owner), Pledge.maxVassals(hallLevel))
				.withStyle(ChatFormatting.GOLD));
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry == null || entry.villages.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.desa.cmd.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		for (Village village : entry.villages) {
			Component state = village.pledged ? Component.translatable("kultivator.desa.cmd.loyal", village.loyalty, village.villagers)
					: Component.translatable("kultivator.desa.cmd.waiting");
			Msg.chat(player, Component.literal("• " + village.name + " ").withStyle(ChatFormatting.WHITE).append(state.copy().withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" (" + village.bell.getX() + ", " + village.bell.getZ() + ")").withStyle(ChatFormatting.DARK_GRAY)));
		}
		return 1;
	}

	/** {@code /kultivasi uji desa ...} (DESIGN 25.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		DesaTests.register(uji);
	}

	/** The module's items for the "Fase 8" test kit: a plaque and 4 Pil Mata Terang. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		list.add(new ItemStack(DesaItems.SECT_PATRON_PLAQUE_ITEM));
		Item pill = AlchemyItems.get(dev.kultivator.alchemy.PillKind.MATA_TERANG);
		if (pill != null) {
			list.add(new ItemStack(pill, 4));
		}
		return list;
	}

	/** Every vassal record of an owner, in order (tests). */
	static List<Village> villages(UUID owner) {
		Vassals.Owner entry = Vassals.peek(owner);
		return entry == null ? List.of() : List.copyOf(entry.villages);
	}

	private Desa() {
	}
}

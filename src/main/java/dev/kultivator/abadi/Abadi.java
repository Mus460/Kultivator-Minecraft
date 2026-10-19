package dev.kultivator.abadi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.event.VoidRealm;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Kenaikan ke Alam Abadi and the Immortal auction (DESIGN 26.3, 26.8; owned by P9-langit): the ascension ritual
 * ({@link Ascend}), the datapack dimension {@code kultivator:alam_abadi} (floating islands made only by world generation),
 * Batu Asal Abadi and the Esensi Abadi meter ({@link OriginStones}), natives ({@link Natives}), the crossing talisman with the
 * disciple sweep ({@link Crossing}), the soft border and void catch ({@link ImmortalBorder}), the arrival island
 * ({@link ArrivalIsland}), the auction ({@link Auction}) and the rival's pursuit ({@link Pursuit}).
 * <p>
 * Block safety (DESIGN 26.2.1): nothing in this package changes a block. Sumber: RMJ ch. 2451 (ascension through a
 * tribulation, the black hole in the five-coloured cloud), RMJIR ch. 567 and 615 (Immortal Origin Stones); every number is
 * game design.
 */
public final class Abadi {

	/** The Alam Abadi dimension (registered by the datapack files of P9-langit). */
	public static final ResourceKey<Level> IMMORTAL_LEVEL = ResourceKey.create(Registries.DIMENSION, Kultivator.id("alam_abadi"));
	/** Sub-compound of {@code CultivationData.extra()} owned by this module (DESIGN 26.13). */
	public static final String EXTRA_KEY = "abadi";
	/** Hurt within this many ticks blocks the ritual and the talisman. */
	public static final long HURT_LOCK = 200L;

	private static final List<Consumer<ServerPlayer>> ASCEND_LISTENERS = new CopyOnWriteArrayList<>();

	private record Task(long due, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static long ticks;

	// ================================================================== init and entry points

	/** Registers the module's items, hooks and listeners; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		AbadiItems.init();
		Ascend.init();
		Crossing.init();
		ImmortalBorder.init();
		OriginStones.init();
		Natives.init();
		Auction.init();
		Pursuit.init();
		ServerTickEvents.END_SERVER_TICK.register(Abadi::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getServer().overworld().getGameTime());
				Crossing.hurt(player);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> LAST_HURT.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TASKS.clear();
			LAST_HURT.clear();
			ticks = 0L;
		});
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			TASKS.removeIf(task -> {
				if (task.due() <= ticks) {
					due.add(task);
					return true;
				}
				return false;
			});
			for (Task task : due) {
				try {
					task.run().run();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kultivator: an Alam Abadi task failed", exception);
				}
			}
		}
		guarded("arrival", () -> ArrivalIsland.tick(server));
		guarded("ascend", () -> Ascend.tick(server, ticks));
		guarded("crossing", () -> Crossing.tick(server, ticks));
		guarded("border", () -> ImmortalBorder.tick(server, ticks));
		guarded("stones", () -> OriginStones.tick(server, ticks));
		guarded("natives", () -> Natives.tick(server, ticks));
		guarded("auction", () -> Auction.tick(server, ticks));
		guarded("pursuit", () -> Pursuit.tick(server, ticks));
	}

	private static void guarded(String what, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: Alam Abadi step {} failed", what, exception);
		}
	}

	/** Runs {@code task} on the server thread after {@code delay} ticks. */
	static void schedule(int delay, Runnable task) {
		TASKS.add(new Task(ticks + Math.max(1, delay), task));
	}

	/** Action {@code ABADI}: text = "op[:param]" ({@code sync}, {@code naik}, {@code lintas}, {@code konversi:n}, {@code bid:lot:price:pct}, {@code titip}). */
	public static void handleAction(ServerPlayer player, String text) {
		String op = text == null ? "" : text;
		String param = "";
		int colon = op.indexOf(':');
		if (colon >= 0) {
			param = op.substring(colon + 1);
			op = op.substring(0, colon);
		}
		switch (op) {
			case "naik" -> startAscension(player, false);
			case "lintas" -> Crossing.useFromInventory(player);
			case "konversi" -> OriginStones.convert(player, parse(param, 1));
			case "bid" -> {
				String[] parts = param.split(":");
				if (parts.length == 3) {
					Auction.bid(player, parse(parts[0], -1), parse(parts[1], -1), parse(parts[2], 0));
				}
			}
			case "titip" -> Auction.consign(player);
			default -> {
			}
		}
		sync(player);
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	/** Sends the Alam and Lelang tablets' data ({@code DuniaSync.CH_ABADI}). */
	static void sync(ServerPlayer player) {
		Auction.noteViewer(player);
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ABADI, syncTag(player));
	}

	/** The {@code CH_ABADI} data of the Alam and Lelang tablets: {@code alam {...}} and {@code lelang {...}}. */
	static CompoundTag syncTag(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("ascended", ascended(player));
		tag.put("alam", Ascend.pageTag(player));
		tag.put("lelang", Auction.pageTag(player));
		return tag;
	}

	/** {@code /kultivasi abadi naik|lintas|info|ikut <name>|konversi <n>}, {@code lelang info} and {@code debug abadi info}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("abadi")
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("naik").executes(ctx -> startAscension(ctx.getSource().getPlayerOrException(), false) ? 1 : 0))
				.then(Commands.literal("lintas").executes(ctx -> Crossing.useFromInventory(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("ikut").then(Commands.argument("nama", StringArgumentType.word())
						.executes(ctx -> Crossing.join(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama")) ? 1 : 0)))
				.then(Commands.literal("konversi").then(Commands.argument("n", IntegerArgumentType.integer(1, OriginStones.CONVERT_PER_DAY))
						.executes(ctx -> OriginStones.convert(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"))))));
		root.then(Commands.literal("lelang")
				.then(Commands.literal("info").executes(ctx -> Auction.info(ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("abadi")
				.then(Commands.literal("info").executes(ctx -> debugInfo(ctx.getSource().getPlayerOrException()))));
	}

	/** {@code /kultivasi uji abadi ...} (DESIGN 26.3.7) and {@code uji lelang ...}. */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		AbadiTests.add(uji);
	}

	/** The module's part of the "Fase 9" test shulker ({@code uji kit}): 64 Batu, 2 talismans, the ascension offering. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> kit = new ArrayList<>();
		kit.add(new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE, 64));
		kit.add(new ItemStack(AbadiItems.REALM_CROSSING_TALISMAN));
		kit.add(new ItemStack(AbadiItems.REALM_CROSSING_TALISMAN));
		ItemStack essence = Ascend.essenceStack(Ascend.OFFER_ESSENCE);
		if (!essence.isEmpty()) {
			kit.add(essence);
		}
		kit.add(dev.kultivator.harta.SpiritStones.stack(dev.kultivator.harta.SpiritStones.SUPREME, 1));
		return kit;
	}

	private static int info(ServerPlayer player) {
		Msg.chat(player, Component.translatable("kultivator.abadi.info.header").withStyle(ChatFormatting.GOLD));
		if (!ascended(player)) {
			Component why = Ascend.blocker(player, false);
			Msg.chat(player, why == null ? Component.translatable("kultivator.abadi.info.ready").withStyle(ChatFormatting.GREEN)
					: Component.translatable("kultivator.abadi.info.not_yet", why).withStyle(ChatFormatting.GRAY));
			return 1;
		}
		Msg.chat(player, Component.translatable("kultivator.abadi.info.status", OriginStones.conv(player), stonesIn(player),
				OriginStones.minedToday(player), OriginStones.MINE_DAY_CAP, Msg.number(Math.ceil(Crossing.cooldownLeft(player) / 20.0D)))
				.withStyle(ChatFormatting.AQUA));
		if (isImmortalLevel(player.level())) {
			Msg.chat(player, Component.translatable("kultivator.abadi.info.border", Math.round(Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ())),
					KultivatorConfig.get().immortalRealmRadius).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int debugInfo(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Msg.chat(player, Component.literal("Abadi: conv=" + Math.round(conversion(player) * 100.0D) + "%, ascended=" + ascended(player)
				+ ", ritual=" + Ascend.active(player) + ", channel=" + Crossing.channeling(player) + ", arrival=" + ArrivalIsland.arrival(server).toShortString()
				+ (ArrivalIsland.usesFallback(server) ? " (fallback)" : "") + ", natives near=" + Natives.near(player, 48.0D) + "/world " + Natives.alive()
				+ ", border=" + Math.round(Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ())) + "/"
				+ KultivatorConfig.get().immortalRealmRadius + ", R_eff=" + effectiveRealm(player) + ", " + Auction.debugLine()));
		return 1;
	}

	// ================================================================== shared helpers

	/** The player's {@code extra.abadi} compound (created and attached when missing). */
	static CompoundTag extra(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag abadi = extra.getCompound(EXTRA_KEY).orElse(null);
		if (abadi == null) {
			abadi = new CompoundTag();
			extra.put(EXTRA_KEY, abadi);
		}
		return abadi;
	}

	/** A sub-compound of {@code extra.abadi} (created and attached when missing). */
	static CompoundTag section(ServerPlayer player, String key) {
		CompoundTag abadi = extra(player);
		CompoundTag section = abadi.getCompound(key).orElse(null);
		if (section == null) {
			section = new CompoundTag();
			abadi.put(key, section);
		}
		return section;
	}

	/** Overworld game time. */
	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Overworld day index (the same day for every dimension). */
	static long day(MinecraftServer server) {
		return Math.max(0L, server.overworld().getOverworldClockTime() / 24000L);
	}

	/** The time of day 0-23999 of the overworld clock. */
	static long timeOfDay(MinecraftServer server) {
		return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
	}

	static @Nullable ServerLevel immortalLevel(MinecraftServer server) {
		return server.getLevel(IMMORTAL_LEVEL);
	}

	/** Batu Asal Abadi in the player's inventory. */
	static int stonesIn(ServerPlayer player) {
		return player.getInventory().countItem(AbadiItems.IMMORTAL_ORIGIN_STONE);
	}

	/** Takes {@code count} Batu Asal Abadi from the inventory; false (nothing taken) when there are fewer. Creative takes none. */
	static boolean takeStones(ServerPlayer player, int count) {
		if (count <= 0 || player.isCreative()) {
			return count <= 0 || player.isCreative();
		}
		if (stonesIn(player) < count) {
			return false;
		}
		int need = count;
		for (int i = 0; i < player.getInventory().getContainerSize() && need > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(AbadiItems.IMMORTAL_ORIGIN_STONE)) {
				int take = Math.min(need, stack.getCount());
				stack.shrink(take);
				need -= take;
			}
		}
		return need <= 0;
	}

	/** Gives Batu Asal Abadi (inventory, else the Lumbung, else dropped with an owner lock) and grants {@code origin_stone}. */
	static void giveStones(ServerPlayer player, int count) {
		if (count <= 0) {
			return;
		}
		give(player, new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE, count));
		dev.kultivator.util.Advancements.grant(player, "origin_stone");
	}

	/** Gives a stack: the inventory, the rest to the Lumbung when the player has a hall, else dropped at their feet with an owner lock. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack rest = stack.copy();
		player.getInventory().add(rest);
		if (rest.isEmpty()) {
			return;
		}
		if (Halls.hallOf(player.getUUID()) != null) {
			rest = Storehouse.insert(player.getUUID(), rest);
		}
		if (!rest.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), rest);
			item.setTarget(player.getUUID());
			item.setPickUpDelay(10);
			item.addTag(dev.kultivator.util.NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	/** {@code R_eff = 8 + floor(stars / 9)} of the player (DESIGN 26.2.2). */
	static int effectiveRealm(ServerPlayer player) {
		return Realm.TRUE_IMMORTAL.ordinal() + TechniquePower.playerStars(Cultivators.get(player)) / 9;
	}

	/** True when the player was hurt within the last 10 s. */
	static boolean recentlyHurt(ServerPlayer player) {
		Long at = LAST_HURT.get(player.getUUID());
		return at != null && now(player.level().getServer()) - at < HURT_LOCK;
	}

	/**
	 * Why the player is busy for the ascension and the talisman (DESIGN 26.3.2: the 25.3.4 refusals plus phase 9's own), or
	 * null. {@code ignoreRitual}: the ritual itself is not counted (asked from inside it).
	 */
	static @Nullable Component busyReason(ServerPlayer player, boolean ignoreRitual) {
		CultivationData data = Cultivators.get(player);
		if (data.breakingThrough() || Breakthrough.isBusy(player) || Tribulation.isActive(player) || HeartDemons.inTrial(player)
				|| InheritanceTrials.active(player) || DuelRules.inDuel(player) || Sparring.inDuel(player) || VoidRealm.climbing(player.getUUID())
				|| Kehancuran.ordering(player.getUUID()) || FlightLease.holder(player) != null || data.flyingSword() || player.isFallFlying()
				|| player.isPassenger() || Alam.inRun(player) || Alam.isRealmLevel(player.level()) || SectRaids.active(player.getUUID())
				|| dev.kultivator.purba.Purba.inFight(player) || dev.kultivator.purba.Purba.inCalamity(player) || dev.kultivator.purba.Purba.inWrath(player)
				|| dev.kultivator.batin.Batin.inDefence(player) || (!ignoreRitual && Ascend.active(player)) || Crossing.channeling(player)) {
			return Component.translatable("kultivator.abadi.why.busy");
		}
		if (data.deviating()) {
			return Component.translatable("kultivator.abadi.why.deviating");
		}
		return null;
	}

	// ================================================================== public contract (26.15.2)

	/** True for the Alam Abadi dimension. */
	public static boolean isImmortalLevel(@Nullable Level level) {
		return level != null && IMMORTAL_LEVEL.equals(level.dimension());
	}

	/** True once the player passed the ascension ({@code extra.abadi.ascended}). */
	public static boolean ascended(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(EXTRA_KEY).getBooleanOr("ascended", false);
	}

	/** Esensi Abadi conversion 0.0-1.0 ({@code extra.abadi.conv} / 100). */
	public static double conversion(ServerPlayer player) {
		return OriginStones.conv(player) / 100.0D;
	}

	/** True while the player's ascension ritual (omen, strikes, waves, vortex) runs. */
	public static boolean inAscension(ServerPlayer player) {
		return Ascend.active(player);
	}

	/**
	 * Starts the ascension ritual ({@code free}: no gate, offering or cooldown, the test command). True when it started; a
	 * refusal is told to the player.
	 */
	public static boolean startAscension(ServerPlayer player, boolean free) {
		return Ascend.start(player, free, null);
	}

	/** Told once after a successful ascension, after the teleport (P9-batin creates the inner world). */
	public static void addAscendListener(Consumer<ServerPlayer> listener) {
		if (listener != null) {
			ASCEND_LISTENERS.add(listener);
		}
	}

	/** Runs every ascend listener (called by the ritual on success). */
	static void notifyAscended(ServerPlayer player) {
		for (Consumer<ServerPlayer> listener : ASCEND_LISTENERS) {
			try {
				listener.accept(player);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("An ascend listener failed", exception);
			}
		}
	}

	// ================================================================== activity hooks (for the daily chores of the glue)

	/** Entity tag of every Alam Abadi native (vanilla-based natives and the wild rogue cultivators). */
	public static final String NATIVE_TAG = Natives.TAG;
	/** Activity names told to {@link #addActivityListener}. */
	public static final String ACT_NATIVE_KILL = "native_kill";
	public static final String ACT_STONE_MINED = "stone_mined";
	public static final String ACT_AUCTION_BID = "auction_bid";
	public static final String ACT_AUCTION_WIN = "auction_win";
	public static final String ACT_CROSSED = "crossed";

	private static final List<java.util.function.BiConsumer<ServerPlayer, String>> ACTIVITY = new CopyOnWriteArrayList<>();

	/**
	 * Told when a player does something of this module that a chore may count: {@link #ACT_NATIVE_KILL} (a native the player
	 * was credited for), {@link #ACT_STONE_MINED}, {@link #ACT_AUCTION_BID}, {@link #ACT_AUCTION_WIN}, {@link #ACT_CROSSED}.
	 * Register from a module's {@code init()}.
	 */
	public static void addActivityListener(java.util.function.BiConsumer<ServerPlayer, String> listener) {
		if (listener != null) {
			ACTIVITY.add(listener);
		}
	}

	static void activity(ServerPlayer player, String what) {
		for (java.util.function.BiConsumer<ServerPlayer, String> listener : ACTIVITY) {
			try {
				listener.accept(player, what);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("An Alam Abadi activity listener failed", exception);
			}
		}
	}

	/** True while the player bids in an open auction session (or waits for a lot to sell). */
	public static boolean inAuction(ServerPlayer player) {
		return Auction.inAuction(player);
	}

	private Abadi() {
	}
}

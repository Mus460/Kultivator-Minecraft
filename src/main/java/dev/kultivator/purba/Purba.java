package dev.kultivator.purba;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.batin.Batin;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Binatang Purba, Bencana Bintang and Murka Langit (DESIGN 26.4, 26.5; owned by P9-purba): the primordial beasts Kun-Peng,
 * Zhulong and Taotie, the player's Seven Star Calamities that gate the stars, and the Heavenly Wrath envoy waves with the
 * Panglima Langit duel. This class registers the module, runs its one server tick, answers the Keabadian screen's
 * {@code PURBA} actions ({@code DuniaSync} channel {@code CH_PURBA}) and holds the public contract of 26.15.2.
 * <p>
 * Block safety (DESIGN 26.2.1): nothing in this package changes a block; every foe is an entity, every pull a velocity,
 * every fire entity fire; lightning is drawn by the client only; no time or weather change.
 */
public final class Purba {

	/** Tag of the primordial beasts and their relics (also read by {@code EpicHooks} for {@code BOSS_PRIMORDIAL}). */
	public static final String TAG = "kultivator.purba";
	/** The primordial kinds in {@code kind} order (0 Kun-Peng, 1 Zhulong, 2 Taotie); the keys of {@code extra.purba.kills}. */
	public static final List<String> KINDS = List.of("kunpeng", "zhulong", "taotie");
	/** Sub-compound of {@code CultivationData.extra()} owned by this module (DESIGN 26.13). */
	public static final String EXTRA_KEY = "purba";

	// ================================================================== init and entry points

	/** Registers the module's entities, items, gates, guards and listeners; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		PurbaItems.init();
		PurbaEntities.init();
		PurbaGuard.init();
		StarCalamity.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			PrimordialEvents.clear();
			StarCalamity.clear();
			HeavenlyWrath.clear();
			EnvoyAi.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Purba::tick);
		ServerPlayerEvents.JOIN.register(StarCalamity::initFor);
		ServerPlayerEvents.LEAVE.register(player -> {
			StarCalamity.abandon(player);
			HeavenlyWrath.abandon(player);
		});
	}

	private static void tick(MinecraftServer current) {
		long tick = current.getTickCount();
		guarded("primordial", () -> PrimordialEvents.tick(current, tick));
		guarded("calamity", () -> StarCalamity.tick(current));
		guarded("wrath", () -> HeavenlyWrath.tick(current, tick));
		guarded("envoy", () -> EnvoyAi.tick(current, tick));
	}

	private static void guarded(String what, Runnable task) {
		try {
			task.run();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: the purba {} pass failed", what, exception);
		}
	}

	/** Action {@code PURBA}: text = "op[:param]": "sync", "start[:k]" (a star calamity), "segel:0|1" (Segel Penangkal for the next run). */
	public static void handleAction(ServerPlayer player, String text) {
		String raw = text == null ? "" : text;
		int colon = raw.indexOf(':');
		String op = colon < 0 ? raw : raw.substring(0, colon);
		String param = colon < 0 ? "" : raw.substring(colon + 1);
		switch (op) {
			case "start" -> {
				int k = 0;
				try {
					k = param.isEmpty() ? 0 : Integer.parseInt(param);
				} catch (NumberFormatException ignored) {
					k = 0;
				}
				StarCalamity.start(player, Math.max(0, Math.min(StarCalamity.COUNT, k)), false);
				sync(player);
			}
			case "segel" -> {
				section(Cultivators.get(player)).putBoolean("segel", "1".equals(param));
				Cultivators.dirty(player);
				sync(player);
			}
			default -> sync(player);
		}
	}

	/** {@code /kultivasi purba info}, {@code bencana info|mulai [k]} and the debug node {@code debug purba info}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("purba")
				.then(Commands.literal("info").executes(ctx -> purbaInfo(ctx.getSource().getPlayerOrException()))));
		root.then(Commands.literal("bencana")
				.then(Commands.literal("info").executes(ctx -> calamityInfo(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("mulai")
						.executes(ctx -> StarCalamity.start(ctx.getSource().getPlayerOrException(), 0, false) ? 1 : 0)
						.then(Commands.argument("k", IntegerArgumentType.integer(1, StarCalamity.COUNT))
								.executes(ctx -> StarCalamity.start(ctx.getSource().getPlayerOrException(),
										IntegerArgumentType.getInteger(ctx, "k"), false) ? 1 : 0))));
		debug.then(Commands.literal("purba")
				.then(Commands.literal("info").executes(ctx -> debugInfo(ctx.getSource().getPlayerOrException()))));
	}

	/** {@code /kultivasi uji purba|bencana|murka|utusan ...} (DESIGN 26.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		PurbaTests.add(uji);
	}

	/** The module's part of the "Fase 9" test shulker: 3 scrolls, 4 of each material, 8 seal shards, the candle eye. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		for (int kind = 0; kind < KINDS.size(); kind++) {
			list.add(PurbaItems.scroll(kind, 1));
		}
		list.add(new ItemStack(PurbaItems.KUN_SCALE, 4));
		list.add(new ItemStack(PurbaItems.PENG_FEATHER, 4));
		list.add(new ItemStack(PurbaItems.ZHULONG_SCALE, 4));
		list.add(new ItemStack(PurbaItems.TAOTIE_FANG, 4));
		list.add(new ItemStack(PurbaItems.HEAVEN_SEAL_SHARD, 8));
		list.add(new ItemStack(PurbaItems.ZHULONG_CANDLE_EYE));
		return list;
	}

	// ================================================================== public contract (26.15.2)

	/** True while the player takes part in a primordial fight (omen included). */
	public static boolean inFight(ServerPlayer player) {
		PrimordialEvents.Fight fight = PrimordialEvents.current;
		if (fight == null || fight.ended) {
			return false;
		}
		PrimordialEvents.Participant part = fight.parts.get(player.getUUID());
		return part != null && part.counting && !part.out && player.level() == fight.level;
	}

	/** True while the player's star calamity run (omen, strikes, waves) is going on. */
	public static boolean inCalamity(ServerPlayer player) {
		return StarCalamity.running(player);
	}

	/** True while a wrath descent (omen, waves, the Panglima duel) targets the player. */
	public static boolean inWrath(ServerPlayer player) {
		return HeavenlyWrath.active(player);
	}

	/**
	 * Spawns {@code count} Utusan Murka Langit around {@code target} for calamity {@code k} (0 = the ascension: no
	 * calamity health bonus); {@code onCleared} is told once: true when every envoy of the wave died (or none could find
	 * ground for 10 s), false when the wave was cancelled or despawned. Returns the number spawned now (more may queue
	 * behind the 8-alive cap).
	 */
	public static int spawnEnvoyWave(ServerPlayer target, int count, int k, Consumer<Boolean> onCleared) {
		return EnvoyAi.spawnWave(target, count, k, onCleared);
	}

	/** Kills of primordial {@code kind} ({@link #KINDS}) by the player. */
	public static int kills(ServerPlayer player, String kind) {
		return read(Cultivators.get(player)).getCompoundOrEmpty("kills").getIntOr(kind, 0);
	}

	/** How many of the three primordial kinds the player has slain (0-3). */
	public static int distinctKinds(ServerPlayer player) {
		int n = 0;
		for (String kind : KINDS) {
			if (kills(player, kind) > 0) {
				n++;
			}
		}
		return n;
	}

	/** The T factor of the passed Trials, 1.0-1.331 (also registered through {@code TechniquePower.addPlayerStarFactor}). */
	public static double trialFactor(ServerPlayer player) {
		return StarCalamity.trialFactor(Cultivators.get(player));
	}

	// ================================================================== shared helpers

	/** The player's {@code extra.purba} section, created when missing (write paths). */
	static CompoundTag section(CultivationData data) {
		CompoundTag tag = data.extra().getCompound(EXTRA_KEY).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			data.extra().put(EXTRA_KEY, tag);
		}
		return tag;
	}

	/** The player's {@code extra.purba} section for reading only (never creates it). */
	static CompoundTag read(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA_KEY);
	}

	/** The overworld clock (day time with days). */
	static long clock(@Nullable MinecraftServer current) {
		return current == null ? 0L : current.overworld().getOverworldClockTime();
	}

	/** The in-game day number. */
	static long today(@Nullable MinecraftServer current) {
		return Math.floorDiv(clock(current), 24000L);
	}

	/** The overworld game time (timers that must not jump with /time set). */
	static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	/** {@code R_eff = 8 + floor(stars / 9)} (DESIGN 26.2.2). */
	static int realmEff(ServerPlayer player) {
		return 8 + TechniquePower.playerStars(Cultivators.get(player)) / 9;
	}

	/** True while anything else holds the player (the 25.3.4 refusals plus Phase 9's own). */
	static boolean busy(ServerPlayer player) {
		return Breakthrough.isBusy(player) || Tribulation.isActive(player) || Ascension.isActive(player) || Alam.inRun(player)
				|| SectRaids.active(player.getUUID()) || DuelRules.inDuel(player) || Sparring.inDuel(player) || inFight(player)
				|| inCalamity(player) || inWrath(player) || Abadi.inAscension(player) || Batin.inDefence(player);
	}

	/** {@code n} Batu Asal Abadi (P9-langit's item, by registry id; empty while it is not registered). */
	static ItemStack stones(int n) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id("immortal_origin_stone"));
		return item == Items.AIR || n <= 0 ? ItemStack.EMPTY : new ItemStack(item, n);
	}

	/** Into the inventory, the rest dropped at the player's feet for them alone. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		player.getInventory().add(copy);
		if (!copy.isEmpty()) {
			drop(player.level(), player.position(), copy, player.getUUID());
		}
	}

	/** A block-safe item entity (locked to {@code owner} when given). */
	static void drop(ServerLevel level, Vec3 pos, ItemStack stack, @Nullable UUID owner) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, stack);
		item.setDefaultPickUpDelay();
		if (owner != null) {
			item.setTarget(owner);
		}
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
	}

	/** Re-sends the tablet data to every player near {@code pos} (a fight began or ended). */
	static void refreshNear(ServerLevel level, Vec3 pos) {
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(pos) <= 96.0D * 96.0D) {
				sync(player);
			}
		}
	}

	/** Distinct Kunpeng bone pieces (1-6) in the inventory. */
	static int pieces(ServerPlayer player) {
		boolean[] held = new boolean[7];
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			int piece = SectEventItems.pieceOf(inventory.getItem(i));
			if (piece >= 1 && piece <= 6) {
				held[piece] = true;
			}
		}
		int n = 0;
		for (int piece = 1; piece <= 6; piece++) {
			if (held[piece]) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== the tablets' data (CH_PURBA)

	/** The Purba and Bencana tablets' data (≤ 2 KB). */
	static void sync(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag purba = read(data);
		KultivatorConfig config = KultivatorConfig.get();
		CompoundTag tag = new CompoundTag();
		// Purba tablet
		tag.put("kills", purba.getCompoundOrEmpty("kills").copy());
		tag.putBoolean("eye", PrimordialEvents.hasUnique(purba, "candle_eye"));
		tag.putInt("pieces", pieces(player));
		tag.putBoolean("art", data.knows("kunpeng_art"));
		tag.putLong("cd", PrimordialEvents.cooldownLeft(player));
		long day = today(player.level().getServer());
		tag.putInt("rep_n", purba.getLongOr("rep_day", -1L) == day ? purba.getIntOr("rep_n", 0) : 0);
		tag.putInt("rep_cap", PrimordialEvents.DAILY_REPEATS);
		tag.putDouble("chance", PrimordialEvents.NATURAL_CHANCE * config.primordialChanceScale);
		tag.putBoolean("beasts", config.primordialBeasts);
		tag.putBoolean("realm", Abadi.isImmortalLevel(player.level()));
		tag.putLong("luka", PurbaGuard.lukaLeft(player));
		tag.putLong("langit", PurbaGuard.langitLeft(player));
		PrimordialEvents.Fight fight = PrimordialEvents.current;
		if (fight != null && !fight.ended && fight.level == player.level()) {
			CompoundTag live = new CompoundTag();
			live.putInt("kind", fight.kind);
			live.putBoolean("active", fight.active);
			live.putInt("phase", fight.phase());
			live.putBoolean("mine", fight.parts.containsKey(player.getUUID()));
			tag.put("fight", live);
		}
		// Bencana tablet
		CompoundTag calamity = new CompoundTag();
		int passed = 0;
		int skipped = 0;
		int[] gates = new int[StarCalamity.COUNT];
		for (int k = 1; k <= StarCalamity.COUNT; k++) {
			if (StarCalamity.passed(data, k)) {
				passed |= 1 << k;
			}
			if (StarCalamity.skipped(data, k)) {
				skipped |= 1 << k;
			}
			gates[k - 1] = StarCalamity.gateStar(data, k);
		}
		calamity.putInt("passed", passed);
		calamity.putInt("skipped", skipped);
		calamity.putIntArray("gates", gates);
		calamity.putInt("stars", TechniquePower.playerStars(data));
		calamity.putDouble("t", StarCalamity.trialFactor(data));
		calamity.putInt("pending", StarCalamity.pending(data));
		calamity.putInt("open", StarCalamity.nextOpen(data));
		calamity.putInt("skip_first", StarCalamity.firstSkipped(data));
		calamity.putBoolean("enabled", config.starCalamities);
		calamity.putBoolean("peak", data.rank().isMax());
		calamity.putBoolean("segel", purba.getBooleanOr("segel", false));
		calamity.putInt("shards", StarCalamity.shards(player));
		calamity.putDouble("cost", StarCalamity.nextCost(player));
		calamity.putDouble("stored", data.storedQi());
		calamity.putBoolean("title", purba.getBooleanOr("title", false));
		StarCalamity.Run run = StarCalamity.run(player);
		if (run != null) {
			calamity.putInt("run_k", run.k);
			calamity.putInt("run_strikes", run.strikes);
			calamity.putInt("run_total", run.total);
		}
		Component why = StarCalamity.refusal(player, 0);
		calamity.putBoolean("can_start", why == null);
		if (why != null) {
			calamity.store("why", ComponentSerialization.CODEC, why);
		}
		tag.put("calamity", calamity);
		tag.put("wrath", HeavenlyWrath.info(player));
		tag.put("results", purba.getListOrEmpty("results").copy());
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_PURBA, tag);
	}

	// ================================================================== commands

	private static int purbaInfo(ServerPlayer player) {
		if (!KultivatorConfig.get().primordialBeasts) {
			Msg.chat(player, Component.translatable("kultivator.purba.disabled").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		for (int kind = 0; kind < KINDS.size(); kind++) {
			Msg.chat(player, Component.translatable("kultivator.purba.info.kills", PurbaItems.kindName(kind), kills(player, KINDS.get(kind)))
					.withStyle(ChatFormatting.GOLD));
		}
		long cd = PrimordialEvents.cooldownLeft(player);
		Msg.chat(player, Component.translatable("kultivator.purba.info.cooldown", cd <= 0L ? Component.translatable("kultivator.purba.info.ready")
				: Component.literal(Msg.number(Math.ceil(cd / 1200.0D)) + " min")).withStyle(ChatFormatting.AQUA));
		CompoundTag purba = read(Cultivators.get(player));
		long day = today(player.level().getServer());
		int repeats = purba.getLongOr("rep_day", -1L) == day ? purba.getIntOr("rep_n", 0) : 0;
		Msg.chat(player, Component.translatable("kultivator.purba.info.repeats", repeats, PrimordialEvents.DAILY_REPEATS,
				Msg.number(100.0D * PrimordialEvents.NATURAL_CHANCE * KultivatorConfig.get().primordialChanceScale)).withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.purba.info.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return 1;
	}

	private static int calamityInfo(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!KultivatorConfig.get().starCalamities) {
			Msg.chat(player, Component.translatable("kultivator.purba.calamity.disabled").withStyle(ChatFormatting.GRAY));
		}
		int open = StarCalamity.nextOpen(data);
		if (open > 0) {
			Msg.chat(player, Component.translatable("kultivator.purba.calamity.info.next", open, StarCalamity.gateStar(data, open),
					TechniquePower.playerStars(data)).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			Msg.chat(player, Component.translatable("kultivator.purba.calamity.info.all").withStyle(ChatFormatting.GOLD));
		}
		StringBuilder line = new StringBuilder();
		for (int k = 1; k <= StarCalamity.COUNT; k++) {
			line.append(StarCalamity.passed(data, k) ? "◉" : StarCalamity.skipped(data, k) ? "◌" : "○");
		}
		Msg.chat(player, Component.translatable("kultivator.purba.calamity.info.seals", line.toString(),
				String.format(java.util.Locale.ROOT, "%.3f", StarCalamity.trialFactor(data))).withStyle(ChatFormatting.AQUA));
		CompoundTag wrath = HeavenlyWrath.info(player);
		if (wrath.getBooleanOr("eligible", false) && wrath.getBooleanOr("enabled", false)) {
			Msg.chat(player, Component.translatable("kultivator.purba.wrath.info", wrath.getLongOr("next", -1L), wrath.getLongOr("today", 0L))
					.withStyle(ChatFormatting.YELLOW));
		}
		Msg.chat(player, Component.translatable("kultivator.purba.calamity.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return 1;
	}

	private static int debugInfo(ServerPlayer player) {
		PrimordialEvents.Fight fight = PrimordialEvents.current;
		LivingEntity looked = Targeting.lookedAtHostile(player, 64.0D);
		if (looked instanceof PrimordialBeastEntity beast && beast.fight != null) {
			fight = beast.fight;
		}
		if (fight == null || fight.beast == null) {
			Msg.chat(player, Component.translatable("kultivator.purba.debug.none").withStyle(ChatFormatting.RED));
			return 0;
		}
		PrimordialBeastEntity beast = fight.beast;
		long now = fight.now();
		Msg.chat(player, Component.translatable("kultivator.purba.debug.info", PurbaItems.kindName(fight.kind), beast.phase() + 1,
				Msg.number(beast.getHealth()), Msg.number(beast.getMaxHealth()), Msg.number(fight.line()),
				Msg.number(beast.getMaxHealth() * (fight.celah() ? PrimordialEvents.INTAKE_CELAH : PrimordialEvents.INTAKE)),
				Msg.number(fight.windowTaken), fight.celah() ? Math.max(0L, fight.celahUntil - now) / 20L : 0L,
				fight.transitioning() ? "+" : "-", fight.parts.size(), Msg.number(fight.total)).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private Purba() {
	}
}

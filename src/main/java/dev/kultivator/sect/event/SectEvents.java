package dev.kultivator.sect.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sect activities (DESIGN 20.3.9-20.3.15; module B, P6-kegiatan): duties and the storehouse, expeditions, envoys, the
 * Primordial Ark, the Kunpeng bone, the Blood-Colored Forbidden Ground, tournaments, the Hundred Dynasties War, the Void
 * God Realm, the promotion trial and the Blazing Sky tower. This class wires them: events, the hall tick once a second
 * per online master with a hall (sunrise and noon from the overworld clock), the Tugas / Misi / Arena actions, the
 * commands and the {@code "sectevent"} part of every {@code HallInfo}. Nothing here places, breaks or changes a block.
 */
public final class SectEvents {

	/** Hall logic runs this often (ticks). */
	public static final int HALL_INTERVAL = 20;
	/** Continuous effects (crimson wind, the ark) are refreshed this often (ticks). */
	private static final int FX_REFRESH = 200;
	/** Disciples act on the hall's pages within this distance of the stele (or of the master). */
	private static final double ACTION_HALL_RANGE = 96.0D;
	private static final double ACTION_PLAYER_RANGE = 32.0D;

	private static int tickCount;

	public static void init() {
		SectEventItems.init();
		PayloadTypeRegistry.clientboundPlay().register(SectEventPayload.TYPE, SectEventPayload.CODEC);
		Duties.init();
		VoidRealm.init();
		BlazingTower.init();
		Tournament.init();
		RivalSkills.init();
		DiscipleHooks.addTick20(SectEvents::tick20);
		DiscipleHooks.addInfo((entity, master, tag) -> {
			String duty = Duties.dutyOf(entity.data());
			if (duty != null) {
				tag.putString("sectevent_duty", duty);
			}
			tag.putInt("sectevent_void_best", VoidRealm.bestFloor(entity.data()));
		});
		Halls.addInfoProvider((viewer, owner, tag) -> tag.put("sectevent", info(viewer, owner, true)));

		ServerLifecycleEvents.SERVER_STARTED.register(EventUtil::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DuelArena.clear();
			Tournament.clear();
			HundredDynasties.clear();
			VoidRealm.clear();
			BlazingTower.clear();
			PromotionTrial.clear();
			RivalSkills.clear();
			EventUtil.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(SectEvents::tick);
		ServerPlayerEvents.JOIN.register(player -> {
			if (EventUtil.server() != null) {
				AwayTeams.resolveDue(player.getUUID(), Halls.hallOf(player.getUUID()));
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PromotionTrial::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			DuelArena.onDamage(entity, source, taken);
			PromotionTrial.afterDamage(entity, source, taken);
		});
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** Starts the Latihan Khusus (Outer to Inner, DESIGN 20.3.15). False (with a chat reason) when it cannot start now. */
	public static boolean startPromotionTrial(DiscipleEntity disciple, ServerPlayer master) {
		return disciple != null && master != null && PromotionTrial.start(disciple, master);
	}

	/** Clears the envoy's 5-day wait (module A's Paviliun item "reset the envoy cooldown" may call this). */
	public static void resetEnvoyCooldown(UUID owner) {
		Envoys.resetCooldown(owner);
	}

	// ================================================================== ticking

	private static void tick(MinecraftServer server) {
		if (EventUtil.server() == null) {
			EventUtil.setServer(server);
		}
		DuelArena.tick(server);
		Tournament.tick(server);
		HundredDynasties.tick(server);
		VoidRealm.tick(server);
		BlazingTower.tick(server);
		PromotionTrial.tick(server);
		if (++tickCount % HALL_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			Halls.HallView hall = Halls.hallOf(player.getUUID());
			if (hall != null) {
				try {
					tickHall(player, hall);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Sect events failed for a hall", exception);
				}
			}
		}
	}

	/** Once a second per online master with a hall: the clock marks, duties, teams, schedules and lasting effects. */
	private static void tickHall(ServerPlayer player, Halls.HallView hall) {
		UUID owner = player.getUUID();
		CompoundTag section = EventUtil.section(owner);
		long clock = EventUtil.clock();
		long last = section.contains("last_clock") ? section.getLongOr("last_clock", clock) : clock;
		section.putLong("last_clock", clock);
		boolean noon = EventUtil.crossed(last, clock, 6000L);
		boolean loaded = EventUtil.hallLoaded(hall);
		int seconds = tickCount / HALL_INTERVAL;

		Expeditions.ensureBoard(owner, Math.max(1, hall.level()), player.getRandom());
		AwayTeams.resolveDue(owner, hall);
		if (loaded && seconds % (Duties.COMPUTE_INTERVAL / HALL_INTERVAL) == 0) {
			Duties.compute(owner, hall);
		}
		if (KultivatorConfig.get().sectEvents) {
			forbiddenSchedule(owner, hall);
			if (noon && loaded) {
				Tournament.onNoon(owner, hall);
			} else {
				Tournament.nextDay(owner);
			}
		}
		if (loaded && seconds % (FX_REFRESH / HALL_INTERVAL) == 0) {
			ServerLevel level = EventUtil.hallLevel(hall);
			Vec3 center = EventUtil.center(hall);
			if (ForbiddenGround.open(owner)) {
				EventUtil.fx(level, center, SectEventFxIds.CRIMSON_WIND, FX_REFRESH + 40, 8.0F);
			}
			if (PrimordialArk.open(owner)) {
				EventUtil.fx(level, center.add(0.0D, PrimordialArk.HEIGHT, 0.0D), SectEventFxIds.ARK, FX_REFRESH + 40, 0.0F);
			}
		}
		EventUtil.dirty();
	}

	/** Every {@code forbiddenGroundIntervalDays} at hall level 2+ the wind seal weakens for a day. */
	private static void forbiddenSchedule(UUID owner, Halls.HallView hall) {
		CompoundTag events = EventUtil.sub(EventUtil.section(owner), "events");
		long day = EventUtil.day();
		int interval = KultivatorConfig.get().forbiddenGroundIntervalDays;
		if (!events.contains("forbidden_next")) {
			events.putLong("forbidden_next", day + interval);
			return;
		}
		if (hall.level() < ForbiddenGround.MIN_HALL_LEVEL || !hall.active() || day < events.getLongOr("forbidden_next", 0L)) {
			return;
		}
		events.putLong("forbidden_next", day + interval);
		ForbiddenGround.openNow(owner, hall);
	}

	/** Once a second per disciple: leftovers of a trial or a training that a restart ended. */
	private static void tick20(DiscipleEntity entity) {
		PromotionTrial.cleanup(entity);
		CompoundTag extra = entity.data().extra().getCompoundOrEmpty(Duties.EXTRA);
		if (extra.contains("tower") && !BlazingTower.training(entity.soulId())) {
			EventUtil.extra(entity.data()).remove("tower");
		}
	}

	// ================================================================== actions

	/** {@code Actions.SECT_EVENT}: {@code arg} = a disciple's entity id (or 0), {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		if (EventUtil.server() == null) {
			return;
		}
		UUID owner = player.getUUID();
		if (!owner.equals(Halls.viewedOwner(player))) {
			// A sect mate's scroll is read-only: nothing acts from it (and never on the mate's own hall).
			return;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		String text = action.text() == null ? "" : action.text();
		int colon = text.indexOf(':');
		String op = colon < 0 ? text : text.substring(0, colon);
		String param = colon < 0 ? "" : text.substring(colon + 1);
		if (hall == null) {
			if (!"refresh".equals(op)) {
				Msg.chat(player, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
			}
			sendInfo(player);
			return;
		}
		DiscipleEntity disciple = action.arg() == 0 ? null : disciple(player, hall, action.arg());
		switch (op) {
			case "duty" -> {
				if (need(player, disciple) && Duties.valid(param)) {
					Duties.assign(player, disciple, param);
				}
			}
			case "duty_off" -> {
				UUID soul = EventUtil.uuid(param);
				if (disciple != null) {
					Duties.release(owner, disciple.soulId(), player);
				} else if (soul != null) {
					// Not loaded (another dimension): the post is freed by soul ("duty_off:<soul>").
					Duties.release(owner, soul, player);
				} else {
					need(player, null);
				}
			}
			case "take" -> take(player, parse(param, -1));
			case "refine" -> Duties.refine(player);
			case "mission" -> {
				String[] parts = param.split(":", 2);
				List<DiscipleEntity> team = team(player, hall, parts.length > 1 ? parts[1] : "");
				if (team != null) {
					Expeditions.send(player, parse(parts[0], -1), team);
				}
			}
			case "recall" -> AwayTeams.recall(player, parse(param, -1));
			case "envoy" -> {
				if (need(player, disciple) && errandReady(player, disciple)) {
					Envoys.send(player, disciple);
				}
			}
			case "envoy_reset" -> Envoys.reset(player);
			case "forbidden" -> {
				List<DiscipleEntity> team = team(player, hall, param);
				if (team != null) {
					ForbiddenGround.send(player, team);
				}
			}
			case "kunpeng" -> {
				if (need(player, disciple)) {
					Expeditions.unite(player, disciple);
				}
			}
			case "ark" -> {
				if (need(player, disciple) && errandReady(player, disciple)) {
					PrimordialArk.board(player, disciple);
				}
			}
			case "tourney_start" -> Tournament.manual(player);
			case "tourney_skip" -> Tournament.skip(player);
			case "exclude" -> {
				if (need(player, disciple)) {
					Tournament.toggleExcluded(owner, disciple.soulId());
				}
			}
			case "void" -> {
				if (need(player, disciple)) {
					VoidRealm.enter(player, disciple);
				}
			}
			case "tower" -> {
				if (need(player, disciple)) {
					BlazingTower.train(player, disciple, parse(param, 0));
				}
			}
			case "tower_stop" -> {
				if (need(player, disciple)) {
					BlazingTower.stop(owner, disciple.soulId(), true);
				}
			}
			case "war_start" -> HundredDynasties.start(player, false);
			case "war" -> {
				// "war:start" from the Calon tablet (module A).
				if ("start".equals(param)) {
					HundredDynasties.start(player, false);
				}
			}
			case "board" -> Expeditions.ensureBoard(owner, Math.max(1, hall.level()), player.getRandom());
			default -> {
			}
		}
		sendInfo(player);
	}

	/** Takes a storehouse slot into the master's inventory (overflow lands at their feet). */
	private static void take(ServerPlayer player, int slot) {
		ItemStack stack = Storehouse.take(player.getUUID(), slot);
		if (!stack.isEmpty()) {
			EventUtil.give(player, stack);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
		}
	}

	private static boolean need(ServerPlayer player, @Nullable DiscipleEntity disciple) {
		if (disciple == null) {
			Msg.actionBar(player, Component.translatable("kultivator.sectevent.disciple_gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		return true;
	}

	/**
	 * A lone errand (envoy, Ark) needs the same freedom as a team: not breaking through, duelling, on a trial, guarding
	 * the formation or in the Void Realm or tower; otherwise a chat line and false (no escape from a failing tribulation).
	 */
	private static boolean errandReady(ServerPlayer player, DiscipleEntity disciple) {
		int activity = disciple.activity();
		if (!EventUtil.free(disciple) || activity == DiscipleEntity.ACTIVITY_FORMATION_GUARD || activity >= DiscipleEntity.ACTIVITY_VOID_BODY) {
			Msg.chat(player, Component.translatable("kultivator.sectevent.team.busy", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		return true;
	}

	/** The player's own disciple with this entity id, near the hall or the player; null otherwise. */
	private static @Nullable DiscipleEntity disciple(ServerPlayer player, Halls.HallView hall, int entityId) {
		Entity entity = player.level().getEntity(entityId);
		if (!(entity instanceof DiscipleEntity disciple) || !disciple.isOwnedBy(player) || disciple.role() != DiscipleEntity.Role.DISCIPLE
				|| !disciple.isAlive() || disciple.isReleasing()) {
			return null;
		}
		if (EventUtil.nearHall(disciple, hall, ACTION_HALL_RANGE) || disciple.distanceToSqr(player) <= ACTION_PLAYER_RANGE * ACTION_PLAYER_RANGE) {
			return disciple;
		}
		return null;
	}

	/** Disciples from a comma list of entity ids, each free for an errand; null (with a chat line) when one is not. */
	private static @Nullable List<DiscipleEntity> team(ServerPlayer player, Halls.HallView hall, String ids) {
		List<DiscipleEntity> team = new ArrayList<>();
		for (String part : ids.split(",")) {
			int id = parse(part.trim(), 0);
			if (id == 0) {
				continue;
			}
			DiscipleEntity disciple = disciple(player, hall, id);
			if (disciple == null || !EventUtil.free(disciple) || team.contains(disciple)) {
				Msg.chat(player, Component.translatable("kultivator.sectevent.team.bad").withStyle(ChatFormatting.YELLOW));
				return null;
			}
			int activity = disciple.activity();
			if (activity == DiscipleEntity.ACTIVITY_FORMATION_GUARD || activity >= DiscipleEntity.ACTIVITY_VOID_BODY) {
				Msg.chat(player, Component.translatable("kultivator.sectevent.team.busy", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
				return null;
			}
			team.add(disciple);
		}
		if (team.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.sectevent.team.empty").withStyle(ChatFormatting.YELLOW));
			return null;
		}
		return team;
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	// ================================================================== info for the hall screen

	/** Sends this module's part of the hall details on its own (after an action). */
	static void sendInfo(ServerPlayer player) {
		Net.send(player, new SectEventPayload(info(player, player.getUUID(), false)));
	}

	/**
	 * The {@code "sectevent"} tag: roster, posts, storehouse, board, teams, envoy, forbidden ground, the ark, the Kunpeng
	 * pieces, the tournament, the Void Realm, the tower and the war, all of {@code owner}'s hall ({@code player} is the
	 * viewer: a sect mate gets it read-only). {@code opening}: lazy work runs first (DESIGN: duty output and due teams are
	 * computed when the hall opens).
	 */
	static CompoundTag info(ServerPlayer player, UUID owner, boolean opening) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("rev", EventUtil.nextRevision());
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || EventUtil.server() == null) {
			tag.putBoolean("hall", false);
			return tag;
		}
		tag.putBoolean("readonly", !owner.equals(player.getUUID()));
		if (opening) {
			AwayTeams.resolveDue(owner, hall);
			if (EventUtil.hallLoaded(hall)) {
				Duties.compute(owner, hall);
			}
			Expeditions.ensureBoard(owner, Math.max(1, hall.level()), player.getRandom());
		}
		int level = Math.max(1, hall.level());
		tag.putBoolean("hall", true);
		tag.putBoolean("active", hall.active());
		tag.putInt("level", level);
		tag.putLong("day", EventUtil.day());
		tag.putLong("time", EventUtil.gameTime());
		tag.putInt("posts", Duties.posts(level));
		tag.putInt("missions", Expeditions.missionsPerDay(level));

		ListTag roster = new ListTag();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			roster.add(rosterEntry(disciple, hall));
		}
		tag.put("roster", roster);

		CompoundTag posts = EventUtil.section(owner).getCompoundOrEmpty("posts").copy();
		tag.put("post_list", posts);
		tag.put("store", Storehouse.encodeForClient(owner));
		ServerLevel hallLevel = EventUtil.hallLevel(hall);
		tag.putInt("chickens", hallLevel != null && EventUtil.hallLoaded(hall) ? Duties.chickens(hallLevel, hall.pos()) : 0);
		tag.putInt("herbs", Duties.herbs(owner, player));

		tag.put("board", Expeditions.board(owner));
		tag.putInt("strongest", Expeditions.strongestTier(owner));
		tag.put("teams", AwayTeams.forClient(owner));
		tag.putLong("envoy_ready", Envoys.readyDay(owner));
		tag.putInt("kunpeng", Expeditions.pieces(owner, player));

		CompoundTag forbidden = new CompoundTag();
		CompoundTag events = EventUtil.section(owner).getCompoundOrEmpty("events");
		forbidden.putBoolean("open", ForbiddenGround.open(owner));
		forbidden.putLong("until", events.getLongOr("forbidden_until", 0L));
		forbidden.putLong("next", events.getLongOr("forbidden_next", 0L));
		forbidden.putInt("slots", ForbiddenGround.slotsLeft(owner));
		forbidden.put("report", EventUtil.section(owner).getListOrEmpty("report").copy());
		tag.put("forbidden", forbidden);

		CompoundTag ark = PrimordialArk.ark(owner).copy();
		ark.putBoolean("open", PrimordialArk.open(owner));
		tag.put("ark", ark);

		tag.put("tourney", Tournament.forClient(owner));
		tag.put("void", VoidRealm.forClient(owner));
		tag.put("tower", BlazingTower.forClient(owner, hall));
		tag.put("war", HundredDynasties.forClient(owner, hall));
		return tag;
	}

	private static CompoundTag rosterEntry(DiscipleEntity disciple, Halls.HallView hall) {
		DiscipleData data = disciple.data();
		CompoundTag entry = new CompoundTag();
		entry.putInt("id", disciple.getId());
		entry.putString("soul", disciple.soulId().toString());
		entry.store("name", ComponentSerialization.CODEC, disciple.getName());
		entry.putInt("step", data.rank().step());
		entry.putInt("realm", data.realm().ordinal());
		entry.putInt("stage", data.stage());
		entry.putInt("layer", data.layer());
		entry.putInt("rank", SectRanks.rank(data));
		entry.putBoolean("tower_ok", SectRanks.rank(data) >= SectRanks.DALAM);
		entry.putInt("pk", SectRanks.pk(data));
		entry.putInt("stars", DisciplePaths.stars(data));
		entry.putInt("power", DisciplePaths.power(data));
		entry.putInt("grade", data.rootGrade().ordinal());
		entry.putInt("color", data.rootColor());
		entry.putDouble("comp", data.comprehension());
		entry.putInt("act", disciple.activity());
		String duty = Duties.dutyOf(data);
		entry.putString("duty", duty == null ? "" : duty);
		entry.putFloat("hp", disciple.getHealth() / Math.max(1.0F, disciple.getMaxHealth()));
		entry.putBoolean("near", EventUtil.nearHall(disciple, hall, 64.0D));
		entry.putBoolean("terr", Halls.inTerritory(disciple.ownerId() == null ? UUID.randomUUID() : disciple.ownerId(), disciple.level(),
				disciple.position()));
		entry.putBoolean("free", EventUtil.free(disciple));
		entry.putInt("void_best", VoidRealm.bestFloor(data));
		entry.putBoolean("void_today", VoidRealm.blocker(disciple) != null && SectRanks.rank(data) >= VoidRealm.MIN_RANK);
		entry.putBoolean("injured", VoidRealm.injured(data));
		entry.putBoolean("tower", BlazingTower.training(disciple.soulId()));
		entry.putBoolean("qr", data.realm() == Realm.QI_REFINING);
		entry.putDouble("fg", ForbiddenGround.chance(data, false));
		entry.putBoolean("envoy_ok", Envoys.blocker(disciple) == null);
		entry.putBoolean("ark_ok", PrimordialArk.mayBoard(disciple.ownerId() == null ? UUID.randomUUID() : disciple.ownerId(), disciple.soulId()));
		entry.putBoolean("excluded", disciple.ownerId() != null && Tournament.excluded(disciple.ownerId(), disciple.soulId()));
		entry.putBoolean("first", Tournament.hasTitle(data, Tournament.TITLE_FIRST));
		entry.putBoolean("border", Tournament.hasTitle(data, Tournament.TITLE_SACRED_BORDER));
		entry.putInt("trained_hp", Duties.trainedHealth(data));
		entry.putLong("meal", Duties.mealUntil(data));
		return entry;
	}

	// ================================================================== commands

	/** Debug: {@code event turnamen mulai|elit}, {@code event perang mulai}, {@code event hampa <floor>}, {@code event terlarang buka}, {@code event misi selesai}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> event = Commands.literal("event");
		event.then(Commands.literal("turnamen")
				.then(Commands.literal("mulai").executes(ctx -> tourney(ctx.getSource().getPlayerOrException(), false)))
				.then(Commands.literal("elit").executes(ctx -> tourney(ctx.getSource().getPlayerOrException(), true))));
		event.then(Commands.literal("perang").then(Commands.literal("mulai").executes(ctx -> war(ctx.getSource().getPlayerOrException()))));
		event.then(Commands.literal("hampa").then(Commands.argument("floor", IntegerArgumentType.integer(1, 999))
				.executes(ctx -> voidFloor(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "floor")))));
		event.then(Commands.literal("terlarang").then(Commands.literal("buka").executes(ctx -> forbidden(ctx.getSource().getPlayerOrException()))));
		event.then(Commands.literal("misi").then(Commands.literal("selesai").executes(ctx -> finishTeams(ctx.getSource().getPlayerOrException()))));
		debug.then(event);
	}

	private static @Nullable Halls.HallView hallFor(ServerPlayer player) {
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall == null) {
			Msg.chat(player, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
		}
		return hall;
	}

	private static int tourney(ServerPlayer player, boolean elite) {
		Halls.HallView hall = hallFor(player);
		if (hall == null) {
			return 0;
		}
		String why = Tournament.blocker(player.getUUID(), hall, player, true);
		if (why != null) {
			Msg.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (elite) {
			CompoundTag events = Tournament.events(player.getUUID());
			int count = events.getIntOr("count", 0);
			events.putInt("count", count + (Tournament.ELITE_EVERY - 1 - count % Tournament.ELITE_EVERY));
		}
		return Tournament.start(player.getUUID(), hall, player, true) ? 1 : 0;
	}

	private static int war(ServerPlayer player) {
		Halls.HallView hall = hallFor(player);
		if (hall == null) {
			return 0;
		}
		// Debug: the war starts with whatever candidates stand at the hall ("aula calon perang" brings the eight war youths).
		return HundredDynasties.start(player, true) ? 1 : 0;
	}

	private static int voidFloor(ServerPlayer player, int floor) {
		DiscipleEntity disciple = nearest(player);
		if (disciple == null) {
			Msg.chat(player, Component.translatable("kultivator.disciples.debug.no_target").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		VoidRealm.setStart(disciple, floor);
		return VoidRealm.enter(player, disciple) ? 1 : 0;
	}

	private static int forbidden(ServerPlayer player) {
		Halls.HallView hall = hallFor(player);
		if (hall == null) {
			return 0;
		}
		ForbiddenGround.openNow(player.getUUID(), hall);
		return 1;
	}

	private static int finishTeams(ServerPlayer player) {
		int count = AwayTeams.finishAll(player.getUUID());
		AwayTeams.resolveDue(player.getUUID(), Halls.hallOf(player.getUUID()));
		Msg.chat(player, Component.translatable("kultivator.sectevent.debug.teams_done", count).withStyle(ChatFormatting.AQUA));
		return count;
	}

	/** The player's nearest own disciple within 16 blocks (debug commands). */
	private static @Nullable DiscipleEntity nearest(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(16.0D);
		DiscipleEntity best = null;
		double bestSq = 16.0D * 16.0D;
		for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, box, each -> each.isAlive()
				&& each.isOwnedBy(player) && each.role() == DiscipleEntity.Role.DISCIPLE)) {
			double distanceSq = disciple.distanceToSqr(player);
			if (distanceSq <= bestSq) {
				bestSq = distanceSq;
				best = disciple;
			}
		}
		return best;
	}

	private SectEvents() {
	}
}

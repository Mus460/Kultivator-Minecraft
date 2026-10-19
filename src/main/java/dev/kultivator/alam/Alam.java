package dev.kultivator.alam;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.event.VoidRealm;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Alam Rahasia, the secret realms (DESIGN 25.3, owned by P8-alam): the public contract of 25.15, the gate and its omen, the
 * player's realm records ({@code extra.dunia.realm}, {@code crystals}, {@code luka_until}), the Dunia tablet data, the
 * commands and the events. Runs live in {@link RealmRun}, chambers in {@link Chambers}, safety in {@link RealmGuard}.
 * Sumber of the system: RMJ ch. 1129 (periodic opening, map-fragment key), ch. 1126 (maze, illusions, stone gates),
 * ch. 155-160 (level restriction and deadly odds), PW ch. 345 (injuries carry over); the instance, slots, chambers, timers,
 * expel rule and rewards are game design.
 */
public final class Alam {

	/** The realm dimension {@code kultivator:alam_rahasia} (datapack, flat floor from world generation, DESIGN 25.1.1). */
	public static final ResourceKey<Level> REALM_LEVEL = ResourceKey.create(Registries.DIMENSION, Kultivator.id("alam_rahasia"));
	/** The refusal shown by the spine hooks of 25.2 item 12 while in a realm or a run. */
	public static final String REFUSE_KEY = "kultivator.alam.refuse";

	public static final int OMEN_TICKS = 200;
	public static final double MATE_RANGE = 8.0D;
	public static final double DISCIPLE_RANGE = 16.0D;
	public static final int MAX_MATES = 3;
	public static final int MAX_DISCIPLES = 2;
	public static final int CRYSTAL_CAP = 94;
	public static final double CRYSTAL_HEALTH = 0.25D;
	public static final int LUKA_TICKS = 5 * 60 * 20;
	public static final String CRYSTAL_ID = "ancient_god_crystal";

	private static final net.minecraft.resources.Identifier CRYSTAL_MODIFIER = Kultivator.id(CRYSTAL_ID);

	/** Told when a run is cleared (DESIGN 25.15): {@code layer} is the Makam layer (0 for realms 1-3), {@code first} a first clear. */
	@FunctionalInterface
	public interface ClearListener {
		void onClear(ServerPlayer player, String id, int layer, boolean first);
	}

	private static final List<ClearListener> CLEAR_LISTENERS = new CopyOnWriteArrayList<>();
	private static final List<BiConsumer<ServerPlayer, String>> CHAMBER_LISTENERS = new CopyOnWriteArrayList<>();

	/** A gate during its 10 s omen. */
	private record Gate(UUID owner, RealmSpec spec, Vec3 pos, ResourceKey<Level> dim, long end, Set<UUID> accepted, int layer, boolean light) {
	}

	private record Task(long due, Runnable run) {
	}

	private static final Map<UUID, Gate> GATES = new HashMap<>();
	private static final List<Task> TASKS = new ArrayList<>();
	private static final Map<UUID, Long> CLEAR_SIGHT = new HashMap<>();
	private static final Set<UUID> FORCE_LIGHT = new HashSet<>();
	private static final Map<UUID, ServerPlayer> LEAVING = new HashMap<>();
	private static long ticks;

	/** Registers the module's items, events and hooks; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		AlamItems.init();
		AlamEntities.init();
		RealmGuard.init();
		AlamLoot.init();
		ServerTickEvents.END_SERVER_TICK.register(Alam::tick);
		ServerPlayerEvents.JOIN.register(player -> {
			applyCrystals(player);
			schedule(1, () -> rejoinCheck(player));
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			applyCrystals(newPlayer);
			if (!isRealmLevel(newPlayer.level()) && RealmRun.of(newPlayer) == null && ReturnPoint.has(newPlayer)) {
				ReturnPoint.clear(newPlayer);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			GATES.remove(player.getUUID());
			RealmGuard.forget(player.getUUID());
			RealmRun run = RealmRun.of(player);
			if (run != null) {
				LEAVING.put(player.getUUID(), player);
				try {
					run.dropMember(player);
				} finally {
					LEAVING.remove(player.getUUID());
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				GATES.remove(player.getUUID());
				RealmRun run = RealmRun.of(player);
				if (run != null) {
					run.dropMember(player);
				}
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			GATES.remove(player.getUUID());
			RealmRun run = RealmRun.of(player);
			if (run != null && origin == run.level) {
				run.dropMember(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(Alam::shutdown);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RealmRun.clearAll();
			Slots.clear();
			GATES.clear();
			TASKS.clear();
			CLEAR_SIGHT.clear();
			FORCE_LIGHT.clear();
			RealmGuard.clearAll();
		});
	}

	// ================================================================== ticking

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
					Kultivator.LOGGER.error("Kultivator: a secret realm task failed", exception);
				}
			}
		}
		if (!GATES.isEmpty()) {
			tickGates(server);
		}
		RealmRun.tickAll(server);
		if (ticks % 20L == 0L) {
			RealmRun.expireChoices(server);
			RealmRun.processHome(server);
			if (!CLEAR_SIGHT.isEmpty()) {
				long now = server.overworld().getGameTime();
				CLEAR_SIGHT.values().removeIf(until -> until <= now);
			}
		}
		// Mutiara Es Dingin in the off hand: entity fire burns half as long.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int fire = player.getRemainingFireTicks();
			if (fire > 1 && player.getOffhandItem().is(AlamItems.COLD_ICE_PEARL)) {
				player.setRemainingFireTicks(fire - 1);
			}
		}
	}

	/** Runs {@code task} on the server thread after {@code delay} ticks. */
	static void schedule(int delay, Runnable task) {
		TASKS.add(new Task(ticks + Math.max(1, delay), task));
	}

	private static void shutdown(MinecraftServer server) {
		for (RealmRun run : List.copyOf(RealmRun.RUNS)) {
			try {
				ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);
				if (owner != null && !run.light) {
					for (UUID id : List.copyOf(run.disciples)) {
						if (run.level.getEntity(id) instanceof DiscipleEntity disciple && disciple.isAlive()) {
							CompoundTag tag = Disciples.depart(disciple, "alam");
							if (!tag.isEmpty()) {
								RealmRun.pushHome(owner, tag);
							}
						}
					}
				}
				for (UUID id : List.copyOf(run.entities)) {
					net.minecraft.world.entity.Entity entity = run.level.getEntity(id);
					if (entity != null) {
						entity.discard();
					}
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: secret realm shutdown step failed", exception);
			}
		}
	}

	/** On the tick after joining: a survival player in the realm dimension with no live run goes back without penalty. */
	private static void rejoinCheck(ServerPlayer player) {
		if (player.isRemoved() || !isRealmLevel(player.level()) || player.isCreative() || player.isSpectator() || RealmRun.of(player) != null) {
			return;
		}
		ReturnPoint.sendBack(player);
		Msg.chat(player, Component.translatable("kultivator.alam.rejoin").withStyle(ChatFormatting.GRAY));
	}

	/** The player being removed right now ({@code LEAVE}), so a run ending at that moment can still write their data. */
	static @Nullable ServerPlayer leavingPlayer(UUID id) {
		return LEAVING.get(id);
	}

	// ================================================================== the gate (25.3.4 steps 1-2)

	/** Opens realm {@code realmId} for the player ({@code free}: no key, omen or cooldown). True when a run or its omen started. */
	public static boolean enter(ServerPlayer player, String realmId, boolean free) {
		return enter(player, realmId, free, -1);
	}

	/** {@link #enter(ServerPlayer, String, boolean)} with the Makam start layer ({@code -1}: continue after the best). */
	public static boolean enter(ServerPlayer player, String realmId, boolean free, int layer) {
		RealmSpec spec = RealmSpecs.byId(realmId);
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.alam.why.unknown", realmId).withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (dev.kultivator.purba.Purba.inFight(player) || dev.kultivator.purba.Purba.inCalamity(player) || dev.kultivator.purba.Purba.inWrath(player)
				|| dev.kultivator.batin.Batin.inDefence(player) || dev.kultivator.abadi.Abadi.inAscension(player)) {
			// Phase 9 (DESIGN 26.14 item 13): never during a primordial fight, a calamity, a wrath descent, a batin defence or the ascension.
			Msg.chat(player, Component.translatable("kultivator.spine9.refuse.busy").withStyle(ChatFormatting.GRAY));
			return false;
		}
		int startLayer = startLayer(player, spec, layer, free);
		if (startLayer < 0) {
			Msg.chat(player, Component.translatable("kultivator.alam.why.layer", best(player, spec.id()) + 1).withStyle(ChatFormatting.GRAY));
			return false;
		}
		boolean light = useLight(player);
		if (free) {
			if (RealmRun.of(player) != null || isRealmLevel(player.level())) {
				Msg.chat(player, Component.translatable(isRealmLevel(player.level()) ? "kultivator.alam.why.inside" : "kultivator.alam.why.running")
						.withStyle(ChatFormatting.GRAY));
				return false;
			}
			GATES.remove(player.getUUID());
			FORCE_LIGHT.remove(player.getUUID());
			RealmRun run = RealmRun.start(player, spec, List.of(), partyDisciples(player), light, startLayer, true);
			return run != null;
		}
		Component why = blocker(player, spec, light);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.alam.refuse_gate", why).withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getServer().overworld().getGameTime();
		Gate gate = new Gate(player.getUUID(), spec, player.position(), level.dimension(), now + OMEN_TICKS, new HashSet<>(), startLayer, light);
		GATES.put(player.getUUID(), gate);
		Vec3 at = player.position();
		EpicHooks.extra(level, EpicIds.EXTRA_REALM_GATE, player, player, at, spec.ordinal(), 0);
		Fx.send(level, at, at.add(0.0D, 3.0D, 0.0D), FxType.ALAM, AlamFxIds.GATE, spec.ordinal(), OMEN_TICKS / 20.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
		Msg.chat(player, Component.translatable("kultivator.alam.omen", spec.displayName(), OMEN_TICKS / 20).withStyle(ChatFormatting.GOLD));
		Advancements.grant(player, "realm_gate");
		inviteMates(player, spec);
		return true;
	}

	private static void inviteMates(ServerPlayer owner, RealmSpec spec) {
		for (ServerPlayer mate : owner.level().players()) {
			if (mate == owner || RealmRun.of(mate) != null || mate.distanceTo(owner) > MATE_RANGE || !Sects.areSectMates(owner, mate)) {
				continue;
			}
			MutableComponent button = Component.translatable("kultivator.alam.invite.button").withStyle(style -> style
					.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/kultivasi alam ikut")));
			Msg.chat(mate, Component.translatable("kultivator.alam.invite", owner.getDisplayName(), spec.displayName(), button)
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** {@code alam ikut}: joins the omen of a sect mate's gate within 8 blocks. */
	static boolean joinGate(ServerPlayer player) {
		for (Gate gate : GATES.values()) {
			ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(gate.owner());
			if (owner != null && owner != player && owner.level() == player.level() && owner.distanceTo(player) <= MATE_RANGE
					&& Sects.areSectMates(owner, player) && RealmRun.of(player) == null) {
				if (gate.accepted().size() >= MAX_MATES) {
					Msg.chat(player, Component.translatable("kultivator.alam.invite.full").withStyle(ChatFormatting.GRAY));
					return false;
				}
				gate.accepted().add(player.getUUID());
				Msg.chat(player, Component.translatable("kultivator.alam.invite.joined", gate.spec().displayName()).withStyle(ChatFormatting.GREEN));
				Msg.chat(owner, Component.translatable("kultivator.alam.invite.mate", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
				Advancements.grant(player, "realm_gate");
				return true;
			}
		}
		Msg.chat(player, Component.translatable("kultivator.alam.invite.none").withStyle(ChatFormatting.GRAY));
		return false;
	}

	private static void tickGates(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Gate gate : List.copyOf(GATES.values())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(gate.owner());
			if (owner == null || !owner.isAlive() || owner.level().dimension() != gate.dim() || owner.position().distanceTo(gate.pos()) > MATE_RANGE) {
				GATES.remove(gate.owner());
				if (owner != null) {
					Msg.chat(owner, Component.translatable("kultivator.alam.omen_broken").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if ((gate.end() - now) % 40L == 0L) {
				Fx.send(owner.level(), gate.pos(), gate.pos().add(0.0D, 3.0D, 0.0D), FxType.ALAM, AlamFxIds.GATE, gate.spec().ordinal(),
						(gate.end() - now) / 20.0F);
			}
			if (now < gate.end()) {
				continue;
			}
			GATES.remove(gate.owner());
			Component why = blocker(owner, gate.spec(), gate.light());
			if (why != null) {
				Msg.chat(owner, Component.translatable("kultivator.alam.refuse_gate", why).withStyle(ChatFormatting.GRAY));
				continue;
			}
			List<ServerPlayer> mates = new ArrayList<>();
			for (UUID id : gate.accepted()) {
				ServerPlayer mate = server.getPlayerList().getPlayer(id);
				if (mate != null && mate.isAlive() && mate.level() == owner.level() && mate.distanceTo(owner) <= MATE_RANGE && RealmRun.of(mate) == null
						&& !mate.isSpectator() && mates.size() < MAX_MATES) {
					mates.add(mate);
				}
			}
			FORCE_LIGHT.remove(owner.getUUID());
			RealmRun run = RealmRun.start(owner, gate.spec(), mates, partyDisciples(owner), gate.light(), gate.layer(), false);
			if (run != null && gate.spec().endless()) {
				consumeKey(owner, gate.spec());
			}
		}
	}

	/** Up to 2 of the owner's own Murid Dalam+ disciples within 16 blocks, the highest ranks first (DESIGN 25.3.10). */
	private static List<DiscipleEntity> partyDisciples(ServerPlayer owner) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(owner.getUUID())) {
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.isAlive() && !disciple.departed() && disciple.level() == owner.level()
					&& disciple.distanceTo(owner) <= DISCIPLE_RANGE && SectRanks.rank(disciple.data()) >= SectRanks.DALAM
					&& !DuelRules.inDuel(disciple)) {
				list.add(disciple);
			}
		}
		list.sort(Comparator.comparingInt((DiscipleEntity d) -> SectRanks.rank(d.data())).reversed());
		return list.size() > MAX_DISCIPLES ? new ArrayList<>(list.subList(0, MAX_DISCIPLES)) : list;
	}

	/** True when the next run of the player is a projected one (Mode Ringan). */
	static boolean useLight(ServerPlayer player) {
		return !KultivatorConfig.get().secretRealmDimension || player.level().getServer().getLevel(REALM_LEVEL) == null
				|| FORCE_LIGHT.contains(player.getUUID());
	}

	/** The Makam start layer: {@code best + 1}, or a checkpoint {@code 9k + 1} not above it; -1 when the asked layer is not allowed. */
	private static int startLayer(ServerPlayer player, RealmSpec spec, int asked, boolean free) {
		if (!spec.endless()) {
			return 1;
		}
		int next = best(player, spec.id()) + 1;
		if (asked <= 0) {
			return next;
		}
		if (free || asked == next || (asked % 9 == 1 && asked <= next)) {
			return asked;
		}
		return -1;
	}

	/** Why the player cannot open a gate of {@code spec} now, or null (DESIGN 25.3.4 step 1, 25.3.2 restrictions). */
	static @Nullable Component blocker(ServerPlayer player, RealmSpec spec, boolean light) {
		CultivationData data = Cultivators.get(player);
		if (!KultivatorConfig.get().secretRealms) {
			return Component.translatable("kultivator.alam.why.disabled");
		}
		if (RealmRun.of(player) != null) {
			return Component.translatable("kultivator.alam.why.running");
		}
		if (isRealmLevel(player.level())) {
			return Component.translatable("kultivator.alam.why.inside");
		}
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return Component.translatable("kultivator.alam.why.peaceful");
		}
		Realm realm = data.realm();
		if (!realm.atLeast(spec.minRealm())) {
			return Component.translatable("kultivator.alam.why.realm_low", spec.minRealm().displayName());
		}
		if (spec.maxRealm() != null && realm.ordinal() > spec.maxRealm().ordinal()) {
			return Component.translatable("kultivator.alam.why.restriction");
		}
		long left = cooldownLeft(player, spec);
		if (left > 0L) {
			return Component.translatable("kultivator.alam.why.cooldown", Msg.number(Math.ceil(left / 1200.0D)));
		}
		Item key = AlamItems.keyOf(spec.id());
		int have = key == null ? 0 : player.getInventory().countItem(key);
		if (have < spec.keyNeed()) {
			return Component.translatable("kultivator.alam.why.key", key == null ? Component.empty() : new ItemStack(key).getHoverName(), have, spec.keyNeed());
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player) || Tribulation.isActive(player) || HeartDemons.inTrial(player)
				|| InheritanceTrials.active(player) || DuelRules.inDuel(player) || Sparring.inDuel(player) || VoidRealm.climbing(player.getUUID())
				|| Kehancuran.ordering(player.getUUID()) || FlightLease.holder(player) != null || data.flyingSword() || player.isFallFlying()
				|| player.isPassenger() || data.deviating()) {
			return Component.translatable("kultivator.alam.why.busy");
		}
		if (RealmGuard.recentlyHurt(player)) {
			return Component.translatable("kultivator.alam.why.hurt");
		}
		if (light) {
			if (!ProjectedRealm.groundOk(player.level(), player, player.position())) {
				return Component.translatable("kultivator.alam.why.ground");
			}
		} else {
			int allowed = Math.max(1, Math.min(Slots.MAX, KultivatorConfig.get().secretRealmSlots));
			if (Slots.leased() >= allowed) {
				return Component.translatable("kultivator.alam.why.slots");
			}
		}
		return null;
	}

	/** Takes the gate's keys (realms 1-3 on success, realm 4 per descent). */
	static void consumeKey(ServerPlayer player, RealmSpec spec) {
		Item key = AlamItems.keyOf(spec.id());
		if (key == null || player.isCreative()) {
			return;
		}
		int need = spec.keyNeed();
		for (int i = 0; i < player.getInventory().getContainerSize() && need > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(key)) {
				int take = Math.min(need, stack.getCount());
				stack.shrink(take);
				need -= take;
			}
		}
	}

	// ================================================================== records (extra.dunia, 25.14)

	private static CompoundTag record(ServerPlayer player, String realmId) {
		CompoundTag dunia = ReturnPoint.dunia(player);
		CompoundTag realms = dunia.getCompound("realm").orElse(null);
		if (realms == null) {
			realms = new CompoundTag();
			dunia.put("realm", realms);
		}
		CompoundTag record = realms.getCompound(realmId).orElse(null);
		if (record == null) {
			record = new CompoundTag();
			realms.put(realmId, record);
		}
		return record;
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	/** How often the player cleared {@code realmId}. */
	public static int clears(ServerPlayer player, String realmId) {
		return record(player, realmId).getIntOr("clears", 0);
	}

	static void addClear(ServerPlayer player, String realmId) {
		CompoundTag record = record(player, realmId);
		record.putInt("clears", record.getIntOr("clears", 0) + 1);
		Cultivators.dirty(player);
	}

	/** The deepest Makam layer cleared (0 when none). */
	public static int best(ServerPlayer player, String realmId) {
		return record(player, realmId).getIntOr("best", 0);
	}

	static void setBest(ServerPlayer player, String realmId, int layer) {
		record(player, realmId).putInt("best", Math.max(0, layer));
		Cultivators.dirty(player);
		if (layer >= 9) {
			Advancements.grant(player, "heavenly_tomb_9");
		}
		if (layer >= 81) {
			Advancements.grant(player, "heavenly_tomb_81");
		}
	}

	public static long cooldownLeft(ServerPlayer player, RealmSpec spec) {
		return Math.max(0L, record(player, spec.id()).getLongOr("cd", 0L) - now(player));
	}

	static void startCooldown(ServerPlayer player, RealmSpec spec) {
		if (spec.cooldown() > 0L) {
			record(player, spec.id()).putLong("cd", now(player) + spec.cooldown());
			Cultivators.dirty(player);
		}
	}

	static void resetCooldown(ServerPlayer player, RealmSpec spec) {
		record(player, spec.id()).remove("cd");
		Cultivators.dirty(player);
	}

	/** Lifetime crystals absorbed in the Tanah Dewa Kuno (0-94). */
	public static int crystals(ServerPlayer player) {
		return Math.max(0, Math.min(CRYSTAL_CAP, ReturnPoint.dunia(player).getIntOr("crystals", 0)));
	}

	static void setCrystals(ServerPlayer player, int count) {
		ReturnPoint.dunia(player).putInt("crystals", Math.max(0, Math.min(CRYSTAL_CAP, count)));
		Cultivators.dirty(player);
		applyCrystals(player);
	}

	/** One more crystal: +0.25 max HP for good (stable modifier {@code kultivator:ancient_god_crystal}, cap 94 = +23.5). */
	static void addCrystal(ServerPlayer player) {
		int before = crystals(player);
		if (before >= CRYSTAL_CAP) {
			Msg.chat(player, Component.translatable("kultivator.alam.crystal.capped", CRYSTAL_CAP).withStyle(ChatFormatting.GRAY));
			return;
		}
		setCrystals(player, before + 1);
		player.heal((float) CRYSTAL_HEALTH);
		Msg.chat(player, Component.translatable("kultivator.alam.crystal.absorbed", before + 1, CRYSTAL_CAP,
				Msg.number((before + 1) * CRYSTAL_HEALTH)).withStyle(ChatFormatting.AQUA));
		Advancements.grant(player, "ancient_god_crystal");
		if (before + 1 >= 70) {
			Advancements.grant(player, "seventy_crystals");
		}
	}

	/** Re-applies the crystal health modifier (join, respawn, change). */
	static void applyCrystals(ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		int count = crystals(player);
		if (count <= 0) {
			health.removeModifier(CRYSTAL_MODIFIER);
			return;
		}
		double amount = count * CRYSTAL_HEALTH;
		AttributeModifier current = health.getModifier(CRYSTAL_MODIFIER);
		if (current == null || Math.abs(current.amount() - amount) > 1.0E-9D) {
			health.addOrReplacePermanentModifier(new AttributeModifier(CRYSTAL_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Ticks left of Luka Alam (0 when none). */
	public static long lukaLeft(ServerPlayer player) {
		return Math.max(0L, ReturnPoint.dunia(player).getLongOr("luka_until", 0L) - now(player));
	}

	static void giveLuka(ServerPlayer player) {
		ReturnPoint.dunia(player).putLong("luka_until", now(player) + LUKA_TICKS);
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.alam.luka", LUKA_TICKS / 1200).withStyle(ChatFormatting.RED));
	}

	/** Jalan Lava and fire factor: ×0.5 with a Cold Ice Pearl in the off hand, ×0.5 with Fire Resistance (they stack). */
	static double lavaFactor(ServerPlayer player) {
		double factor = 1.0D;
		if (player.getOffhandItem().is(AlamItems.COLD_ICE_PEARL)) {
			factor *= 0.5D;
		}
		if (player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
			factor *= 0.5D;
		}
		return factor;
	}

	// ================================================================== Clear Sight and listeners

	/** Pil Mata Terang: phantom exits are revealed and phantoms outlined for {@code ticks}. */
	public static void grantClearSight(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			return;
		}
		long until = now(player) + ticks;
		CLEAR_SIGHT.merge(player.getUUID(), until, Math::max);
		Msg.actionBar(player, Component.translatable("kultivator.alam.sight", Math.max(1, ticks / 20)).withStyle(ChatFormatting.AQUA));
	}

	/** True while Pil Mata Terang's Clear Sight lasts. */
	public static boolean hasClearSight(ServerPlayer player) {
		Long until = CLEAR_SIGHT.get(player.getUUID());
		return until != null && until > now(player);
	}

	/** Adds a clear listener; register from a module's {@code init()}. */
	public static void addClearListener(ClearListener listener) {
		if (listener != null) {
			CLEAR_LISTENERS.add(listener);
		}
	}

	/** Adds a listener told whenever the player clears one chamber (the glue's daily chore "clear 1 realm chamber"). */
	public static void addChamberListener(BiConsumer<ServerPlayer, String> listener) {
		if (listener != null) {
			CHAMBER_LISTENERS.add(listener);
		}
	}

	/** The registered clear listeners (for the module's own firing code). */
	static List<ClearListener> clearListeners() {
		return CLEAR_LISTENERS;
	}

	static void fireClear(ServerPlayer player, String id, int layer, boolean first) {
		if (layer == 0) {
			if (RealmSpecs.BLOOD_FIRE.equals(id)) {
				Advancements.grant(player, "blood_fire_trial");
			} else if (RealmSpecs.HEAVENVOID.equals(id)) {
				Advancements.grant(player, "heavenvoid_hall");
			}
		}
		for (ClearListener listener : CLEAR_LISTENERS) {
			try {
				listener.onClear(player, id, layer, first);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A secret realm clear listener failed", exception);
			}
		}
	}

	static void chamberCleared(ServerPlayer player, String chamber) {
		for (BiConsumer<ServerPlayer, String> listener : CHAMBER_LISTENERS) {
			try {
				listener.accept(player, chamber);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A secret realm chamber listener failed", exception);
			}
		}
	}

	// ================================================================== public contract (25.15)

	/** True while the player plays a run (dimension or Mode Ringan). */
	public static boolean inRun(ServerPlayer player) {
		return RealmRun.of(player) != null;
	}

	/** True for the realm dimension. */
	public static boolean isRealmLevel(@Nullable Level level) {
		return level != null && REALM_LEVEL.equals(level.dimension());
	}

	/** True when the spine refusals of 25.2 item 12 apply: in the realm dimension or in a run. */
	public static boolean blocked(ServerPlayer player) {
		return isRealmLevel(player.level()) || inRun(player);
	}

	/** The refusal message of 25.2 item 12. */
	public static MutableComponent refusal() {
		return Component.translatable(REFUSE_KEY);
	}

	/** The id of the realm the player is running, or null. */
	public static @Nullable String activeRealm(ServerPlayer player) {
		RealmRun run = RealmRun.of(player);
		return run == null ? null : run.spec.id();
	}

	/** Forces Mode Ringan for the player's next run ({@code uji alam ringan}). */
	static void setForceLight(ServerPlayer player, boolean on) {
		if (on) {
			FORCE_LIGHT.add(player.getUUID());
		} else {
			FORCE_LIGHT.remove(player.getUUID());
		}
	}

	// ================================================================== actions and the Dunia tablet

	/** Action {@code ALAM}: text = "op[:param]": {@code sync}, {@code open:<id>}, {@code pick:<n>}, {@code leave}. */
	public static void handleAction(ServerPlayer player, String text) {
		String op = text == null ? "" : text;
		String param = "";
		int colon = op.indexOf(':');
		if (colon >= 0) {
			param = op.substring(colon + 1);
			op = op.substring(0, colon);
		}
		switch (op) {
			case "open" -> {
				enter(player, param, false);
				sync(player);
			}
			case "pick" -> {
				int index;
				try {
					index = Integer.parseInt(param.trim());
				} catch (NumberFormatException exception) {
					index = 0;
				}
				RealmRun.pick(player, index);
			}
			case "leave" -> leave(player);
			default -> sync(player);
		}
	}

	/** A voluntary exit ({@code alam keluar}): no Luka Alam; the cooldown starts. */
	static boolean leave(ServerPlayer player) {
		RealmRun run = RealmRun.of(player);
		if (run == null) {
			if (GATES.remove(player.getUUID()) != null) {
				Msg.chat(player, Component.translatable("kultivator.alam.omen_broken").withStyle(ChatFormatting.GRAY));
				return true;
			}
			Msg.chat(player, Component.translatable("kultivator.alam.why.not_running").withStyle(ChatFormatting.GRAY));
			return false;
		}
		run.leave(player, false);
		return true;
	}

	/** Sends the Alam tablet's data ({@code DuniaSync} channel {@code CH_ALAM}, key {@code page}). */
	static void sync(ServerPlayer player) {
		CompoundTag page = new CompoundTag();
		boolean light = useLight(player);
		page.putBoolean("light", light);
		page.putBoolean("enabled", KultivatorConfig.get().secretRealms);
		page.putInt("crystals", crystals(player));
		page.putLong("luka", lukaLeft(player));
		String active = activeRealm(player);
		page.putString("active", active == null ? "" : active);
		ListTag realms = new ListTag();
		for (RealmSpec spec : RealmSpecs.ALL) {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", spec.id());
			entry.putInt("ord", spec.ordinal());
			Item key = AlamItems.keyOf(spec.id());
			entry.putInt("have", key == null ? 0 : player.getInventory().countItem(key));
			entry.putInt("need", spec.keyNeed());
			entry.putInt("fragments", player.getInventory().countItem(AlamItems.HEAVENVOID_FRAGMENT));
			entry.putInt("clears", clears(player, spec.id()));
			entry.putInt("best", best(player, spec.id()));
			entry.putLong("cd", cooldownLeft(player, spec));
			Component why = blocker(player, spec, light);
			entry.putBoolean("ready", why == null);
			if (why != null) {
				entry.store("why", ComponentSerialization.CODEC, why);
			}
			realms.add(entry);
		}
		page.put("realms", realms);
		CompoundTag out = new CompoundTag();
		out.put("page", page);
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ALAM, out);
	}

	// ================================================================== commands (25.11)

	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) -> SharedSuggestionProvider.suggest(RealmSpecs.ids(), builder);

	/** {@code /kultivasi alam daftar|masuk <id> [lapisan]|keluar|ikut} and {@code debug alam info} (DESIGN 25.11). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("alam")
				.then(Commands.literal("daftar").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("masuk")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
								.executes(ctx -> enter(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), false) ? 1 : 0)
								.then(Commands.argument("lapisan", IntegerArgumentType.integer(1, 9999))
										.executes(ctx -> enter(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), false,
												IntegerArgumentType.getInteger(ctx, "lapisan")) ? 1 : 0))))
				.then(Commands.literal("keluar").executes(ctx -> leave(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("ikut").executes(ctx -> joinGate(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
		debug.then(Commands.literal("alam")
				.then(Commands.literal("info").executes(ctx -> debugInfo(ctx.getSource().getPlayerOrException()))));
	}

	/** {@code /kultivasi uji alam ...} and {@code uji kristal} (DESIGN 25.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		AlamTests.add(uji);
	}

	private static int list(ServerPlayer player) {
		boolean light = useLight(player);
		Msg.chat(player, Component.translatable("kultivator.alam.list.header", crystals(player), CRYSTAL_CAP).withStyle(ChatFormatting.GOLD));
		for (RealmSpec spec : RealmSpecs.ALL) {
			Component why = blocker(player, spec, light);
			Component status = why == null ? Component.translatable("kultivator.alam.status.ready").withStyle(ChatFormatting.GREEN)
					: why.copy().withStyle(ChatFormatting.GRAY);
			Component record = spec.endless() ? Component.translatable("kultivator.alam.list.best", best(player, spec.id()))
					: Component.translatable("kultivator.alam.list.clears", clears(player, spec.id()));
			Msg.chat(player, Component.translatable("kultivator.alam.list.line", spec.displayName(), Component.literal(spec.id()).withStyle(ChatFormatting.DARK_GRAY),
					record, status));
		}
		if (light) {
			Msg.chat(player, Component.translatable("kultivator.alam.list.light").withStyle(ChatFormatting.AQUA));
		}
		long luka = lukaLeft(player);
		if (luka > 0L) {
			Msg.chat(player, Component.translatable("kultivator.alam.list.luka", Msg.number(Math.ceil(luka / 1200.0D))).withStyle(ChatFormatting.RED));
		}
		return 1;
	}

	private static int debugInfo(ServerPlayer player) {
		Msg.chat(player, Component.literal("Alam: slots " + Slots.leased() + "/" + KultivatorConfig.get().secretRealmSlots + ", runs "
				+ RealmRun.RUNS.size() + ", gates " + GATES.size() + ", light=" + useLight(player) + ", ret=" + ReturnPoint.has(player)
				+ ", crystals=" + crystals(player) + ", luka=" + lukaLeft(player) + ", home=" + RealmRun.homeList(player).size()));
		for (Map.Entry<String, String> entry : RealmRun.summary().entrySet()) {
			Msg.chat(player, Component.literal(" - " + entry.getValue()).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	// ================================================================== the test kit

	/** The module's items for the "Fase 8" test kit (DESIGN 25.11). */
	public static List<ItemStack> kitItems() {
		List<ItemStack> kit = new ArrayList<>();
		kit.add(new ItemStack(AlamItems.HEAVENVOID_FRAGMENT, 3));
		kit.add(new ItemStack(AlamItems.HEAVENVOID_MAP));
		kit.add(new ItemStack(AlamItems.TOKEN_DARAH_API));
		kit.add(new ItemStack(AlamItems.INHERITANCE_CRYSTAL));
		kit.add(new ItemStack(AlamItems.TOMB_SIGIL));
		ItemStack herbs = AlamLoot.herb(90);
		herbs.setCount(4);
		kit.add(herbs);
		kit.add(new ItemStack(AlamItems.SOUL_NURTURING_WOOD, 4));
		kit.add(new ItemStack(AlamItems.ANCIENT_GOD_BLOOD, 4));
		kit.add(new ItemStack(AlamItems.TOMB_ESSENCE, 4));
		kit.add(new ItemStack(AlamItems.COLD_ICE_PEARL));
		kit.add(new ItemStack(AlamItems.HEAVENVOID_CAULDRON));
		return kit;
	}

	private Alam() {
	}
}

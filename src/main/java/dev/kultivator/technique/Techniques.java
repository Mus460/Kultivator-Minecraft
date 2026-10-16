package dev.kultivator.technique;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Learning, selecting and casting techniques (DESIGN sections 8 and 15), plus the helpers both technique modules share:
 * {@link #power}, {@link #hurt}, {@link #fx} and {@link #schedule}. The universal techniques themselves live in
 * {@link UniversalTechniques} and its helper classes, the Dao techniques in {@code DaoTechniques}.
 */
public final class Techniques {

	/** Damage type {@code kultivator:technique} (data/kultivator/damage_type/technique.json). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("technique"));
	/** More actions than this per second from one client are ignored. */
	private static final int MAX_ACTIONS_PER_SECOND = 10;
	/** Known techniques for the {@code technique_collector} advancement. */
	private static final int COLLECTOR_COUNT = 10;

	static final int REFUSE = 0xFF9A7A;
	static final int JADE = 0x8FE3C8;
	static final int GOLD = 0xFFD54A;
	static final int MUTED = 0xA8B5B0;

	/** Something a technique does later (the Buddha Palm landing...). Dropped on death, leaving or dimension change. */
	private record Pending(long due, ResourceKey<Level> dimension, Consumer<ServerPlayer> action) {
	}

	private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
	/** Per player: game time the current one-second window started, actions in it. */
	private static final Map<UUID, long[]> RATE = new HashMap<>();

	private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return SharedSuggestionProvider.suggest(java.util.Arrays.stream(Technique.values()).map(Technique::id), builder);
		}
		return SharedSuggestionProvider.suggest(Cultivators.get(player).learnedTechniques().stream().map(Technique::id), builder);
	};

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				// Every fall-based damage (fall, stalagmite) but the ender pearl's own.
				if (source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.ENDER_PEARL) && CloudStep.fallImmune(player)) {
					return false;
				}
				if (source.is(DamageTypes.IN_WALL) && Cultivators.get(player).dharmaActive()) {
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				FlyingSword.onHurt(player, source, damageTaken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			CloudStep.forget(newPlayer);
			PENDING.remove(newPlayer.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			RATE.clear();
			CloudStep.clearAll();
			FlyingSword.clearAll();
			DharmaForm.clearAll();
			DaoDomain.clearAll();
		});
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		runPending(player);
		CloudStep.tick(player, data);
		FlyingSword.tick(player, data);
		DharmaForm.tick(player, data);
		DaoDomain.tick(player, data);
		if (player.tickCount % 100 == 37 && data.learnedIds().size() >= COLLECTOR_COUNT) {
			Advancements.grant(player, "technique_collector");
		}
	}

	// ================================================================ actions

	/** CAST, SELECT_*, FLYING_SWORD_TOGGLE and CLOUD_STEP actions. */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		if (!allowAction(player)) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		switch (action.action()) {
			case Actions.SELECT_TECHNIQUE -> select(player, action.text());
			case Actions.SELECT_NEXT -> cycle(player, 1);
			case Actions.SELECT_PREVIOUS -> cycle(player, -1);
			case Actions.CAST -> {
				String text = action.text() == null ? "" : action.text().trim();
				if (text.isEmpty()) {
					Technique selected = data.selected();
					if (selected == null) {
						refuse(player, Component.translatable("kultivator.techniques.refuse.none_selected"));
					} else {
						tryCast(player, selected);
					}
				} else {
					Technique technique = Technique.byId(text);
					if (technique == null) {
						refuse(player, Component.translatable("kultivator.techniques.refuse.unknown", text));
					} else {
						tryCast(player, technique);
					}
				}
			}
			case Actions.FLYING_SWORD_TOGGLE -> tryCast(player, Technique.FLYING_SWORD);
			case Actions.CLOUD_STEP -> CloudStep.tryStep(player, data);
			default -> {
			}
		}
	}

	/** The simple spam guard: false for actions beyond {@link #MAX_ACTIONS_PER_SECOND} in the current second. */
	private static boolean allowAction(ServerPlayer player) {
		long now = player.level().getGameTime();
		long[] rate = RATE.computeIfAbsent(player.getUUID(), id -> new long[]{now, 0L});
		if (now - rate[0] >= 20L || now < rate[0]) {
			rate[0] = now;
			rate[1] = 0L;
		}
		rate[1]++;
		return rate[1] <= MAX_ACTIONS_PER_SECOND;
	}

	/**
	 * Casts {@code technique} if every rule allows it (DESIGN section 15): known, realm, Dao, no deviation, no
	 * breakthrough, cooldown over, enough qi. Stops meditation first. On success spends the qi, starts the cooldown and
	 * feeds Dao comprehension. Every refusal is explained in the action bar. True when the technique happened.
	 */
	public static boolean tryCast(ServerPlayer player, Technique technique) {
		CultivationData data = Cultivators.get(player);
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.now"));
		}
		if (technique == Technique.CLOUD_STEP) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
		}
		if (technique == Technique.FLYING_SWORD && data.flyingSword()) {
			// Stepping down is always allowed and costs nothing.
			FlyingSword.dismount(player, data, FlyingSword.Reason.TOGGLE);
			return true;
		}
		Component why = whyNotCastable(data, technique);
		if (why != null) {
			return refuse(player, why);
		}
		if (data.deviating()) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.deviation",
					Component.literal(String.valueOf(Math.max(1, data.deviationTicks() / 20)))));
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player)) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.breakthrough"));
		}
		long now = player.level().getGameTime();
		long left = data.cooldownLeft(technique, now);
		if (left > 0L) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.cooldown", name(technique), seconds(left)));
		}
		int cost = technique.qiCost();
		if (data.qi() + 1.0E-6D < cost) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.qi", name(technique),
					Msg.number(data.qi()), String.valueOf(cost)));
		}
		Meditation.stop(player);
		boolean done = technique.dao() == null
				? UniversalTechniques.cast(player, data, technique)
				: DaoTechniques.cast(player, data, technique);
		if (!done) {
			return false;
		}
		Qi.trySpend(player, cost);
		data.setCooldown(technique, now, technique.cooldownTicks());
		Comprehension.onTechniqueUsed(player, technique);
		Advancements.grant(player, "first_technique");
		Cultivators.dirty(player);
		return true;
	}

	/** Selects a known, castable technique (never the passive Cloud Step). True on success. */
	public static boolean select(ServerPlayer player, String id) {
		CultivationData data = Cultivators.get(player);
		Technique technique = Technique.byId(id == null ? "" : id.trim());
		if (technique == null) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.unknown", String.valueOf(id)));
		}
		if (technique == Technique.CLOUD_STEP) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
		}
		Component why = whyNotCastable(data, technique);
		if (why != null) {
			return refuse(player, why);
		}
		data.setSelected(technique);
		Cultivators.syncNow(player);
		showSelection(player, technique);
		return true;
	}

	/** Moves the selection {@code step} places through the castable techniques (enum order, wrapping around). */
	public static void cycle(ServerPlayer player, int step) {
		CultivationData data = Cultivators.get(player);
		List<Technique> list = castableTechniques(data);
		if (list.isEmpty()) {
			refuse(player, Component.translatable("kultivator.techniques.refuse.none_learned"));
			return;
		}
		int index = list.indexOf(data.selected());
		int next = index < 0 ? (step >= 0 ? 0 : list.size() - 1) : Math.floorMod(index + step, list.size());
		Technique technique = list.get(next);
		data.setSelected(technique);
		Cultivators.syncNow(player);
		showSelection(player, technique);
	}

	private static void showSelection(ServerPlayer player, Technique technique) {
		String key = technique == Technique.FLYING_SWORD ? "kultivator.techniques.selected.flying" : "kultivator.techniques.selected";
		Msg.actionBar(player, Component.translatable(key, name(technique), String.valueOf(technique.qiCost()),
				seconds(technique.cooldownTicks()), Comprehension.format(FlyingSword.drainPerSecond(Cultivators.get(player)))).withColor(JADE));
	}

	/** Known, realm reached, Dao matches, not passive: may be selected and cast. Pure (client screens may use it). */
	public static boolean castable(CultivationData data, Technique technique) {
		return technique != Technique.CLOUD_STEP && whyNotCastable(data, technique) == null;
	}

	/** Every castable technique in enum order. */
	public static List<Technique> castableTechniques(CultivationData data) {
		List<Technique> list = new ArrayList<>();
		for (Technique technique : Technique.values()) {
			if (castable(data, technique)) {
				list.add(technique);
			}
		}
		return list;
	}

	/** Why the technique cannot be used at all (unknown, realm too low, other Dao), or null when it can. */
	private static @Nullable Component whyNotCastable(CultivationData data, Technique technique) {
		if (!data.knows(technique)) {
			return Component.translatable("kultivator.techniques.refuse.not_known", name(technique));
		}
		if (!data.realm().atLeast(technique.minRealm())) {
			return Component.translatable("kultivator.techniques.refuse.realm", name(technique),
					technique.minRealm().displayName().copy().withColor(technique.minRealm().color()));
		}
		Dao dao = technique.dao();
		if (dao != null && dao != data.dao()) {
			return Component.translatable("kultivator.techniques.refuse.dao", name(technique), dao.displayName().copy().withColor(dao.color()));
		}
		return null;
	}

	/** Tells the player why something did not happen (action bar, soft red). Always false, for {@code return refuse(...)}. */
	public static boolean refuse(ServerPlayer player, Component message) {
		Msg.actionBar(player, message.copy().withColor(REFUSE));
		return false;
	}

	static MutableComponent name(Technique technique) {
		return technique.displayName().copy().withColor(GOLD);
	}

	/** Ticks as seconds with one decimal ("1.5"). */
	static String seconds(long ticks) {
		return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
	}

	// ================================================================ learning

	/** Learns every REALM/DAO technique the player now qualifies for (called after rank or Dao changes). Never forgets. */
	public static void refreshUnlocks(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean knewNothing = data.learnedIds().isEmpty();
		Dao dao = data.dao();
		List<Technique> fresh = new ArrayList<>();
		for (Technique technique : Technique.values()) {
			boolean qualifies = switch (technique.unlock()) {
				case REALM -> data.realm().atLeast(technique.minRealm());
				case DAO -> dao != null && technique.dao() == dao && data.realm().atLeast(technique.minRealm());
				case BOOK -> false;
			};
			if (qualifies && data.learn(technique)) {
				fresh.add(technique);
			}
		}
		for (Technique technique : fresh) {
			Msg.chat(player, Component.translatable("kultivator.techniques.learned", name(technique),
					technique.description().copy().withColor(MUTED)).withColor(JADE));
			if (technique == Technique.FLYING_SWORD) {
				Msg.chat(player, Component.translatable("kultivator.techniques.hint.flying_sword",
						Component.keybind("key.kultivator.flying_sword").withStyle(ChatFormatting.YELLOW)).withColor(MUTED));
			}
		}
		if (knewNothing && !fresh.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.techniques.hint.keys",
					Component.keybind("key.kultivator.cast").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.previous").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.next").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.info").withStyle(ChatFormatting.YELLOW)).withColor(MUTED));
		}
		boolean selectionChanged = ensureSelection(data);
		if (data.learnedIds().size() >= COLLECTOR_COUNT) {
			Advancements.grant(player, "technique_collector");
		}
		if (!fresh.isEmpty() || selectionChanged) {
			Cultivators.dirty(player);
		}
	}

	/** Selects the first castable technique when nothing (or something no longer castable) is selected. True if changed. */
	private static boolean ensureSelection(CultivationData data) {
		Technique selected = data.selected();
		if (selected != null && castable(data, selected)) {
			return false;
		}
		List<Technique> list = castableTechniques(data);
		Technique choice = list.isEmpty() ? null : list.get(0);
		if (choice == selected) {
			return false;
		}
		if (choice == null && selected != null && data.knows(selected)) {
			// Keep a known but momentarily unusable selection rather than clearing it.
			return false;
		}
		data.setSelected(choice);
		return true;
	}

	// ================================================================ join / leave / death

	/** Before the session reset on join: take away flight we granted in a previous session, catch up on unlocks. */
	public static void onJoin(ServerPlayer player, CultivationData data) {
		FlyingSword.onJoin(player, data);
		DharmaForm.onJoin(player);
		refreshUnlocks(player);
	}

	public static void onLeave(ServerPlayer player) {
		FlyingSword.onLeave(player);
		DharmaForm.onLeave(player);
		DaoDomain.onLeave(player);
		CloudStep.forget(player);
		PENDING.remove(player.getUUID());
		RATE.remove(player.getUUID());
	}

	private static void onDeath(ServerPlayer player) {
		FlyingSword.onDeath(player);
		DharmaForm.onDeath(player);
		DaoDomain.onDeath(player);
		CloudStep.forget(player);
		PENDING.remove(player.getUUID());
	}

	// ================================================================ shared helpers

	/** D of DESIGN section 8: {@code (4 + 2.5 x realm + 0.5 x stage) x (1 + comprehension / 100)}. */
	public static double power(CultivationData data) {
		Rank rank = data.rank();
		double base = 4.0D + 2.5D * rank.realm().ordinal() + 0.5D * rank.stage();
		return base * (1.0D + data.mainComprehension() / 100.0D);
	}

	/** The {@code kultivator:technique} damage source with {@code caster} as the attacker. */
	public static DamageSource damageSource(ServerLevel level, ServerPlayer caster) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), caster);
	}

	/**
	 * Hurts {@code target} with technique damage from {@code caster} (kill credit, XP and loot go to the caster). Refuses
	 * every target {@link Targeting#isHostileTo} rejects. Technique qi is not slowed by the target's short invulnerability
	 * after a melee hit. True when damage was dealt; a kill is counted in the caster's technique kills.
	 * <p>
	 * Silverfish take attacker-less damage (credited through {@code setLastHurtByPlayer}): a hit with an attacker makes them
	 * call their friends out of infested blocks, which breaks or replaces those blocks.
	 */
	public static boolean hurt(ServerPlayer caster, LivingEntity target, float amount) {
		if (caster == null || target == null || !(amount > 0.0F) || !Float.isFinite(amount)) {
			return false;
		}
		if (target.level() != caster.level() || !Targeting.isHostileTo(target, caster)) {
			return false;
		}
		ServerLevel level = caster.level();
		if (target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		DamageSource source;
		if (target instanceof Silverfish) {
			target.setLastHurtByPlayer(caster, 100);
			source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
		} else {
			source = damageSource(level, caster);
		}
		boolean hit = target.hurtServer(level, source, amount);
		if (hit && target.isDeadOrDying()) {
			Cultivators.get(caster).addTechniqueKill();
			Cultivators.dirty(caster);
		}
		return hit;
	}

	/** Asks nearby clients to draw a technique effect (FxType.TECHNIQUE, a = technique ordinal, b = sub, f = scale). */
	public static void fx(ServerPlayer caster, Technique technique, int sub, Vec3 pos, Vec3 target, float scale) {
		Fx.send(caster.level(), pos, target, FxType.TECHNIQUE, technique.ordinal(), sub, scale);
	}

	/**
	 * Runs {@code action} for the player after {@code delayTicks} (at least 1) during their tick, unless they died, left
	 * or changed dimension meanwhile.
	 */
	public static void schedule(ServerPlayer player, int delayTicks, Consumer<ServerPlayer> action) {
		PENDING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
				.add(new Pending(player.level().getGameTime() + Math.max(1, delayTicks), player.level().dimension(), action));
	}

	private static void runPending(ServerPlayer player) {
		List<Pending> list = PENDING.get(player.getUUID());
		if (list == null) {
			return;
		}
		long now = player.level().getGameTime();
		List<Pending> due = null;
		for (Iterator<Pending> it = list.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			if (pending.due() <= now) {
				it.remove();
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(pending);
			}
		}
		if (list.isEmpty()) {
			PENDING.remove(player.getUUID());
		}
		if (due == null) {
			return;
		}
		for (Pending pending : due) {
			if (player.isAlive() && !player.isDeadOrDying() && pending.dimension().equals(player.level().dimension())) {
				pending.action().accept(player);
			}
		}
	}

	// ================================================================ commands

	/** {@code technique select|list|cast} for players and {@code debug technique cooldowns reset}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("technique")
				.then(Commands.literal("select")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> select(self(ctx), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), self(ctx))))
				.then(Commands.literal("cast")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> {
									ServerPlayer player = self(ctx);
									String id = StringArgumentType.getString(ctx, "id");
									Technique technique = Technique.byId(id);
									if (technique == null) {
										ctx.getSource().sendFailure(Component.translatable("kultivator.techniques.refuse.unknown", id));
										return 0;
									}
									return tryCast(player, technique) ? 1 : 0;
								}))));
		debug.then(Commands.literal("technique")
				.then(Commands.literal("cooldowns")
						.then(Commands.literal("reset").executes(ctx -> {
							ServerPlayer player = self(ctx);
							CultivationData data = Cultivators.get(player);
							long now = player.level().getGameTime();
							for (Technique technique : Technique.values()) {
								data.setCooldown(technique, now, 0);
							}
							Cultivators.syncNow(player);
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.debug.cooldowns_reset")), false);
							return 1;
						}))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** Known techniques with their qi cost, cooldown and state. */
	private static int list(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		List<Technique> known = data.learnedTechniques();
		if (known.isEmpty()) {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.list.empty").withColor(MUTED)), false);
			return 0;
		}
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.list.header",
				String.valueOf(known.size())).withColor(GOLD)), false);
		long now = player.level().getGameTime();
		Technique selected = data.selected();
		String drain = Comprehension.format(FlyingSword.drainPerSecond(data));
		for (Technique technique : known) {
			String key = technique == Technique.FLYING_SWORD ? "kultivator.techniques.list.entry_flying" : "kultivator.techniques.list.entry";
			MutableComponent line = Component.literal(" • ").withColor(MUTED)
					.append(Component.translatable(key, name(technique), String.valueOf(technique.qiCost()),
							seconds(technique.cooldownTicks()), drain).withColor(0xE8E8E8));
			Component state;
			if (technique == Technique.CLOUD_STEP) {
				state = Component.translatable("kultivator.techniques.list.passive").withColor(JADE);
			} else if (!castable(data, technique)) {
				Component why = whyNotCastable(data, technique);
				state = (why == null ? Component.translatable("kultivator.techniques.list.locked") : why.copy()).withColor(REFUSE);
			} else {
				long left = data.cooldownLeft(technique, now);
				state = left > 0L
						? Component.translatable("kultivator.techniques.list.waiting", seconds(left)).withColor(0xFFC27A)
						: Component.translatable("kultivator.techniques.list.ready").withColor(0x7CFFB2);
			}
			line.append(Component.literal("  [").withColor(MUTED)).append(state).append(Component.literal("]").withColor(MUTED));
			if (technique == selected) {
				line.append(Component.literal(" ").append(Component.translatable("kultivator.techniques.list.selected").withColor(GOLD)));
			}
			source.sendSuccess(() -> line, false);
		}
		return known.size();
	}

	private Techniques() {
	}
}

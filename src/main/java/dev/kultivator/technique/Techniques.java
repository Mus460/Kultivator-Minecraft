package dev.kultivator.technique;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.Boons;
import dev.kultivator.technique.spec.run.CastOptions;
import dev.kultivator.technique.spec.run.Forms;
import dev.kultivator.technique.spec.run.Loadout;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.technique.spec.run.TechniqueRunner;
import dev.kultivator.technique.spec.run.TeknikSync;
import dev.kultivator.technique.spec.run.Unlocks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Learning, selecting and casting techniques (DESIGN 8, 15, 22.2.6) and the helpers both technique modules share:
 * {@link #power}, {@link #hurt}, {@link #fx} and {@link #schedule}. Since phase 6c every cast goes through the spec
 * pipeline ({@code TechniqueRunner.cast}); the legacy 23 run their hand-written code through {@code LegacyArt}. The
 * CAST and SELECT actions resolve spec ids through the registry, cycling stays inside the loadout (Cincin Segel).
 */
public final class Techniques {

	/** Damage type {@code kultivator:technique} (data/kultivator/damage_type/technique.json). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("technique"));
	/** Armour-bypassing technique damage {@code kultivator:technique_pierce} (soul and space arts, DESIGN 22.3.2). */
	public static final ResourceKey<DamageType> DAMAGE_PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("technique_pierce"));
	/** Other modules' reasons why the player may not cast right now (null: no objection). */
	private static final List<Function<ServerPlayer, Component>> CAST_BLOCKERS = new ArrayList<>();

	/**
	 * Adds a reason a player may not cast techniques now (module D: the master whose soul descends into the ring,
	 * DESIGN 20.3.20). The first non-null reason refuses the cast; stepping off the flying sword stays allowed.
	 */
	public static void addCastBlocker(Function<ServerPlayer, @Nullable Component> blocker) {
		CAST_BLOCKERS.add(blocker);
	}

	/** More actions than this per second from one client are ignored. */
	private static final int MAX_ACTIONS_PER_SECOND = 10;

	static final int REFUSE = 0xFF9A7A;
	static final int JADE = 0x8FE3C8;
	static final int GOLD = 0xFFD54A;
	static final int MUTED = 0xA8B5B0;

	/** Something a technique does later. Dropped on death, leaving or dimension change. */
	private record Pending(long due, ResourceKey<Level> dimension, Consumer<ServerPlayer> action) {
	}

	private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
	/** Per player: game time the current one-second window started, actions in it. */
	private static final Map<UUID, long[]> RATE = new HashMap<>();
	/** Players whose current Enlightenment Moment was already counted (22.6, 22.8.6). */
	private static final Set<UUID> ENLIGHTENED = new HashSet<>();

	private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return SharedSuggestionProvider.suggest(TechniqueRegistry.all().stream().map(TechniqueSpec::id), builder);
		}
		return SharedSuggestionProvider.suggest(Cultivators.get(player).learnedSpecs().stream().map(TechniqueSpec::id), builder);
	};

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				// Every fall-based damage (fall, stalagmite) but the ender pearl's own.
				if (source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.ENDER_PEARL) && (CloudStep.fallImmune(player) || Boons.noFall(player))) {
					return false;
				}
				if (source.is(DamageTypes.IN_WALL) && (Cultivators.get(player).dharmaActive() || Forms.grown(player))) {
					return false;
				}
			}
			// Dash i-frames never cover unblockable damage (tribulation, qi deviation, the void, /kill).
			return !(Boons.iframes(entity) && !source.is(DamageHooks.UNBLOCKABLE));
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
			ENLIGHTENED.clear();
			CloudStep.clearAll();
			FlyingSword.clearAll();
			DharmaForm.clearAll();
			DaoDomain.clearAll();
		});
		FlyingSword.init();
		// Phase 6c (DESIGN 22.2.6): the runner and its subsystems; the registry is built later and read lazily.
		TechniqueRunner.init();
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		runPending(player);
		CloudStep.tick(player, data);
		FlyingSword.tick(player, data);
		DharmaForm.tick(player, data);
		DaoDomain.tick(player, data);
		if (player.tickCount % 20 == 0) {
			// QI_REGEN boons and SELF_PENALTY adjust the second Qi.tick just regenerated.
			double factor = Boons.qiRegenFactor(player);
			if (Math.abs(factor - 1.0D) > 1.0E-6D && !data.deviating()) {
				double regen = Qi.regenPerSecond(data);
				if (factor > 1.0D) {
					Qi.restore(player, regen * (factor - 1.0D));
				} else {
					data.setQi(Math.max(0.0D, data.qi() - regen * (1.0D - factor)));
				}
			}
		}
		if (data.enlightened()) {
			if (ENLIGHTENED.add(player.getUUID())) {
				dev.kultivator.technique.spec.run.TechniqueCommands.enlightenment(player);
			}
		} else if (!ENLIGHTENED.isEmpty()) {
			ENLIGHTENED.remove(player.getUUID());
		}
		if (player.tickCount % 100 == 37) {
			dev.kultivator.technique.spec.run.TechniqueCommands.checkAdvancements(player);
		}
		if (player.tickCount % 10 == 3) {
			TeknikSync.write(player, data);
		}
	}

	// ================================================================ actions

	/** CAST, SELECT_*, FLYING_SWORD_TOGGLE and CLOUD_STEP actions (spec ids through the registry). */
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
					String selected = data.selectedId();
					if (selected.isEmpty() || TechniqueRegistry.byId(selected) == null) {
						refuse(player, Component.translatable("kultivator.techniques.refuse.none_selected"));
					} else {
						tryCast(player, selected);
					}
				} else {
					tryCast(player, text);
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

	/** Casts a legacy technique through the spec pipeline. True when it happened. */
	public static boolean tryCast(ServerPlayer player, Technique technique) {
		return tryCast(player, technique.spec(), CastOptions.DEFAULT);
	}

	/** Casts a spec (DESIGN 22.2.6): every refusal of step 1 in order, the snapshot, precheck, spending and the start. */
	public static boolean tryCast(ServerPlayer player, TechniqueSpec spec, CastOptions options) {
		return TechniqueRunner.cast(player, spec, options);
	}

	/** Casts the spec with id {@code id} with the default options; unknown ids are refused. */
	public static boolean tryCast(ServerPlayer player, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id == null ? "" : id.trim());
		if (spec == null) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.unknown", String.valueOf(id)));
		}
		return tryCast(player, spec, CastOptions.DEFAULT);
	}

	/** The first other module's reason the player may not cast now, or null. */
	public static @Nullable Component castBlocker(ServerPlayer player) {
		for (Function<ServerPlayer, Component> blocker : CAST_BLOCKERS) {
			Component blocked = blocker.apply(player);
			if (blocked != null) {
				return blocked;
			}
		}
		return null;
	}

	/** Steps down from the Flying Sword (always allowed, costs nothing). */
	public static void dismountSword(ServerPlayer player) {
		FlyingSword.dismount(player, Cultivators.get(player), FlyingSword.Reason.TOGGLE);
	}

	/** Ends a Dharma Form (a technique form takes the one-form slot). */
	public static void endDharma(ServerPlayer player) {
		DharmaForm.end(player, "kultivator.techniques.dharma_form.end");
	}

	/** Ends a technique form (Dharma Form takes the one-form slot). */
	static void endTechniqueForm(ServerPlayer player) {
		dev.kultivator.technique.spec.run.TechniqueCommands.endForm(player);
	}

	/** True when {@code pos} lies inside the player's legacy Dao Domain (IN_AREA, COST_MOD inside the own field). */
	public static boolean inLegacyDomain(ServerPlayer player, Vec3 pos) {
		return DaoDomain.contains(player, pos);
	}

	/** The legacy Dao Domain's end tick and radius (HUD), or {0, 0}. */
	public static double[] legacyDomain(ServerPlayer player) {
		return new double[]{DaoDomain.endTick(player), DaoDomain.radius(player)};
	}

	/** Selects a known, castable spec (never the passive Cloud Step). True on success. */
	public static boolean select(ServerPlayer player, String id) {
		CultivationData data = Cultivators.get(player);
		TechniqueSpec spec = TechniqueRegistry.byId(id == null ? "" : id.trim());
		if (spec == null || !spec.playerVisible()) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.unknown", String.valueOf(id)));
		}
		if (spec.type() == TechniqueType.PASIF) {
			return refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
		}
		Component why = TechniqueRunner.whyNotCastablePublic(data, spec);
		if (why != null) {
			return refuse(player, why);
		}
		data.setSelectedId(spec.id());
		Cultivators.syncNow(player);
		showSelection(player, spec);
		return true;
	}

	/** Moves the selection {@code step} places through the loadout (every castable spec while it is empty), wrapping around. */
	public static void cycle(ServerPlayer player, int step) {
		CultivationData data = Cultivators.get(player);
		List<TechniqueSpec> list = Loadout.cycle(data);
		if (list.isEmpty()) {
			refuse(player, Component.translatable("kultivator.techniques.refuse.none_learned"));
			return;
		}
		int index = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(data.selectedId())) {
				index = i;
				break;
			}
		}
		int next = index < 0 ? (step >= 0 ? 0 : list.size() - 1) : Math.floorMod(index + step, list.size());
		TechniqueSpec spec = list.get(next);
		data.setSelectedId(spec.id());
		Cultivators.syncNow(player);
		showSelection(player, spec);
	}

	private static void showSelection(ServerPlayer player, TechniqueSpec spec) {
		CultivationData data = Cultivators.get(player);
		int mastery = Mastery.level(data, spec.id());
		int qi = TechniquePower.qi(spec, data, mastery, Boons.qiFactor(player, spec));
		int cooldown = TechniquePower.cooldownTicks(spec, data, mastery, Boons.cooldownFactor(player, spec));
		String key = spec.legacy() == Technique.FLYING_SWORD ? "kultivator.techniques.selected.flying" : "kultivator.techniques.selected";
		Msg.actionBar(player, Component.translatable(key, spec.displayName().copy().withColor(GOLD), String.valueOf(qi), seconds(cooldown),
				dev.kultivator.dao.Comprehension.format(FlyingSword.drainPerSecond(data))).withColor(JADE));
	}

	/** Known, rank reached, Dao matches, not passive: may be selected and cast. Pure (client screens may use it). */
	public static boolean castable(CultivationData data, Technique technique) {
		return castable(data, technique.spec());
	}

	/** The spec variant of {@link #castable(CultivationData, Technique)}. Pure. */
	public static boolean castable(CultivationData data, TechniqueSpec spec) {
		return Loadout.castable(data, spec);
	}

	/** Every castable legacy technique in enum order. */
	public static List<Technique> castableTechniques(CultivationData data) {
		List<Technique> list = new ArrayList<>();
		for (Technique technique : Technique.values()) {
			if (castable(data, technique)) {
				list.add(technique);
			}
		}
		return list;
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

	/** Learns every automatic spec the player now qualifies for (rank, Dao, comprehension, feats). Never forgets. */
	public static void refreshUnlocks(ServerPlayer player) {
		Unlocks.refresh(player);
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
		ENLIGHTENED.remove(player.getUUID());
	}

	private static void onDeath(ServerPlayer player) {
		FlyingSword.onDeath(player);
		DharmaForm.onDeath(player);
		DaoDomain.onDeath(player);
		CloudStep.forget(player);
		PENDING.remove(player.getUUID());
	}

	// ================================================================ shared helpers

	/** D of DESIGN section 8 (the pre-6c power; arenas and other modules' legacy numbers). */
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
	 * Hurts {@code target} with a finished amount of technique damage from {@code caster} (other modules' pre-6c numbers;
	 * the boss, arena, trial-mob and heart-demon caps apply through a single-hit ledger). Refuses every target
	 * {@link Targeting#isHostileTo} rejects. True when damage was dealt.
	 */
	public static boolean hurt(ServerPlayer caster, LivingEntity target, float amount) {
		if (caster == null || target == null || !(amount > 0.0F) || !Float.isFinite(amount)) {
			return false;
		}
		if (TechniquePower.inArena(caster, target)) {
			// A duel keeps the pre-6c amount exactly (the duel clamps apply as before).
			return hurt(caster, DamageContext.legacy(caster, target, amount));
		}
		double power = Math.max(1.0E-6D, TechniquePower.power(Cultivators.get(caster)));
		DamageContext context = new DamageContext(DamageContext.Kind.HIT, caster, target, null, amount / power, amount / power, 0.0D, power, 0,
				1.0D, true, false, null);
		return hurt(caster, context);
	}

	/**
	 * The player's spec damage choke point (DESIGN 22.3.5 rule 7): only targets {@link Targeting#isHostileTo} accepts
	 * (calm neutrals only for precise hits), the amount from {@code TechniquePower.finalDamage}, {@code technique_pierce}
	 * for pierce hits, the silverfish rule, {@link NoGrief#TAG} and a portal cooldown on monsters, and precise hits on the
	 * Ender Dragon go to its head. True when damage landed.
	 */
	public static boolean hurt(ServerPlayer caster, DamageContext context) {
		LivingEntity target = context.target();
		if (caster == null || target == null || target.level() != caster.level() || !Targeting.isHostileTo(target, caster, context.precise())) {
			return false;
		}
		float damage = TechniquePower.finalDamage(context);
		if (!(damage > 0.0F) || !Float.isFinite(damage)) {
			return false;
		}
		ServerLevel level = caster.level();
		if (target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		ResourceKey<DamageType> type = context.pierce() ? DAMAGE_PIERCE : DAMAGE;
		DamageSource source;
		if (target instanceof Silverfish) {
			target.setLastHurtByPlayer(caster, 100);
			source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type));
		} else {
			source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), caster);
		}
		if (!(target instanceof ServerPlayer)) {
			target.addTag(NoGrief.TAG);
			target.setPortalCooldown();
		}
		boolean hit;
		if (context.precise() && target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
			hit = dragon.hurt(level, dragon.head, source, damage);
		} else {
			hit = target.hurtServer(level, source, damage);
		}
		if (hit && target.isDeadOrDying() && !dev.kultivator.technique.spec.run.Dummies.isDummy(target)) {
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

	/** {@code technique select|list|cast} for players (every spec id) and {@code debug technique cooldowns reset}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("technique")
				.then(Commands.literal("select")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> select(self(ctx), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), self(ctx))))
				.then(Commands.literal("cast")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> tryCast(self(ctx), StringArgumentType.getString(ctx, "id")) ? 1 : 0))));
		debug.then(Commands.literal("technique")
				.then(Commands.literal("cooldowns")
						.then(Commands.literal("reset").executes(ctx -> {
							ServerPlayer player = self(ctx);
							resetCooldowns(player);
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.debug.cooldowns_reset")), false);
							return 1;
						}))));
	}

	/** Every cooldown of the player, the global cooldown, the lockout and the charges. */
	public static void resetCooldowns(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		long now = player.level().getGameTime();
		for (String id : data.learnedIds()) {
			data.setCooldown(id, now, 0);
		}
		for (Technique technique : Technique.values()) {
			data.setCooldown(technique, now, 0);
		}
		dev.kultivator.technique.spec.run.TechniqueCommands.resetSession(player);
		Cultivators.syncNow(player);
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** Known specs with their qi cost, cooldown and state. */
	private static int list(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		List<TechniqueSpec> known = data.learnedSpecs();
		if (known.isEmpty()) {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.list.empty").withColor(MUTED)), false);
			return 0;
		}
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.techniques.list.header",
				String.valueOf(known.size())).withColor(GOLD)), false);
		long now = player.level().getGameTime();
		String selected = data.selectedId();
		String drain = dev.kultivator.dao.Comprehension.format(FlyingSword.drainPerSecond(data));
		for (TechniqueSpec spec : known) {
			int mastery = Mastery.level(data, spec.id());
			String key = spec.legacy() == Technique.FLYING_SWORD ? "kultivator.techniques.list.entry_flying" : "kultivator.techniques.list.entry";
			MutableComponent line = Component.literal(" • ").withColor(MUTED)
					.append(Component.translatable(key, spec.displayName().copy().withColor(GOLD), String.valueOf(TechniquePower.qi(spec, data, mastery)),
							seconds(TechniquePower.cooldownTicks(spec, data, mastery)), drain).withColor(0xE8E8E8));
			Component state;
			if (spec.type() == TechniqueType.PASIF) {
				state = Component.translatable("kultivator.techniques.list.passive").withColor(JADE);
			} else if (!castable(data, spec)) {
				Component why = TechniqueRunner.whyNotCastablePublic(data, spec);
				state = (why == null ? Component.translatable("kultivator.techniques.list.locked") : why.copy()).withColor(REFUSE);
			} else {
				long left = data.cooldownLeft(spec.id(), now);
				state = left > 0L
						? Component.translatable("kultivator.techniques.list.waiting", seconds(left)).withColor(0xFFC27A)
						: Component.translatable("kultivator.techniques.list.ready").withColor(0x7CFFB2);
			}
			line.append(Component.literal("  [").withColor(MUTED)).append(state).append(Component.literal("]").withColor(MUTED));
			if (spec.id().equals(selected)) {
				line.append(Component.literal(" ").append(Component.translatable("kultivator.techniques.list.selected").withColor(GOLD)));
			}
			source.sendSuccess(() -> line, false);
		}
		return known.size();
	}

	/** Grants {@code first_technique} (kept for other modules that cast techniques on the player's behalf). */
	public static void firstTechnique(ServerPlayer player) {
		Advancements.grant(player, "first_technique");
	}

	private Techniques() {
	}
}

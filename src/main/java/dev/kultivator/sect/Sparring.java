package dev.kultivator.sect;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageEstimate;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Latih Tanding (sparring, DESIGN section 10): a friendly duel between two online players in the same dimension. The
 * challenged player has 60 s to accept; a 3-2-1 countdown follows (no blows land during it), then 120 s of fighting in
 * which their techniques may hit each other ({@link Targeting#sparring}). A blow between the two partners that could
 * leave the victim below 10% health (estimated after armour, Resistance and Protection, see {@link DamageEstimate}) is
 * cancelled and ends the duel with the attacker as winner, so nobody dies; a blow taken on a raised shield goes through
 * and is judged by the health it really left. Harm without an attacker (burning left by a Flame Palm, a fall after a
 * Mountain Stomp, a wall or lava after a push) counts as the partner's for a few seconds after their blow, and any such
 * harm that would leave a duelist below 10% ends the duel instead, also during the short truce after a decisive blow;
 * leftovers are put out when the duel ends. Techniques never throw or drag a player, and a duelist who still falls below
 * the world is put back next to the partner and loses (also during the truce). Damage from anyone else is untouched, and vanilla PvP rules (game rule
 * {@code pvp}, teams) still apply. The duel also ends without a winner on leave, death, dimension change, distance over
 * 48 blocks, a switch to creative/spectator, or timeout. The winner is rewarded only for a real fight: at least 15 s
 * long, the loser starting with at least 80% health and losing at least 40% of it to the winner, and at most once per
 * 10 minutes for the same pair. The fight state is only kept in memory; {@code CultivationData.sparringWith} mirrors it
 * for the clients.
 */
public final class Sparring {

	public static final int ANSWER_TICKS = 60 * 20;
	public static final int COUNTDOWN_TICKS = 60;
	public static final int DUEL_TICKS = 120 * 20;
	public static final double CHALLENGE_RANGE = 32.0D;
	public static final double MAX_DISTANCE = 48.0D;
	/** A blow that would leave the victim below this share of max health ends the duel instead. */
	public static final float DEFEAT_FRACTION = 0.10F;
	/** Winner's XP: this share of the bar of their current rank. */
	public static final double XP_FRACTION = 0.10D;
	/** Winner's comprehension of their chosen Dao (before the root affinity factor). */
	public static final double COMPREHENSION = 1.0D;
	/** After a decisive blow the two cannot hurt each other for a moment, so a follow-up swing cannot kill. */
	private static final int TRUCE_TICKS = 100;
	/** Attacker-less harm (burning, wither, a fall...) this soon after a partner's blow counts as that partner's. */
	private static final int LINGER_TICKS = 200;
	/** The same two players are rewarded at most once per this many ticks (10 minutes). */
	private static final long REWARD_COOLDOWN_TICKS = 20L * 60L * 10L;
	/** A rewarded fight lasts at least this long (15 s after the countdown). */
	private static final long MIN_FIGHT_TICKS = 20L * 15L;
	/** A rewarded loser started the fight with at least this share of their max health... */
	private static final float MIN_START_HEALTH = 0.8F;
	/** ... and lost at least this share of it to the winner. */
	private static final float MIN_DEALT_FRACTION = 0.4F;

	private enum End {
		DEFEAT, TIMEOUT, LEFT, DEATH, DIMENSION, DISTANCE, MODE
	}

	private record Challenge(UUID challenger, UUID target, long expires) {
	}

	private record Truce(UUID other, long until) {
	}

	private static final class Duel {
		final UUID a;
		final UUID b;
		final String aName;
		final String bName;
		final long accepted;
		/** Server tick the fight began; -1 during the countdown. */
		long started = -1L;
		/** Server ticks a partner's blow last landed on A / on B. */
		long hitOnA = Long.MIN_VALUE / 2L;
		long hitOnB = Long.MIN_VALUE / 2L;
		/** Share of max health A / B had when the fight began. */
		float startA = 1.0F;
		float startB = 1.0F;
		/** Health A / B lost to the partner's blows during the fight. */
		float dealtToA;
		float dealtToB;

		Duel(UUID a, String aName, UUID b, String bName, long accepted) {
			this.a = a;
			this.aName = aName;
			this.b = b;
			this.bName = bName;
			this.accepted = accepted;
		}

		boolean fighting() {
			return started >= 0L;
		}

		boolean involves(UUID id) {
			return a.equals(id) || b.equals(id);
		}

		/** Server tick the partner last hurt {@code victim}. */
		long lastHitOn(UUID victim) {
			return a.equals(victim) ? hitOnA : hitOnB;
		}

		void markHit(UUID victim, long now) {
			if (a.equals(victim)) {
				hitOnA = now;
			} else {
				hitOnB = now;
			}
		}

		float startOf(UUID player) {
			return a.equals(player) ? startA : startB;
		}

		float dealtTo(UUID victim) {
			return a.equals(victim) ? dealtToA : dealtToB;
		}

		void addDealt(UUID victim, float amount) {
			if (!(amount > 0.0F) || !Float.isFinite(amount)) {
				return;
			}
			if (a.equals(victim)) {
				dealtToA += amount;
			} else {
				dealtToB += amount;
			}
		}

		UUID partnerOf(UUID player) {
			return a.equals(player) ? b : a;
		}

		String partnerName(UUID player) {
			return a.equals(player) ? bName : aName;
		}
	}

	/** Pending challenges by challenged player (one each; a new one replaces the old). */
	private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
	/** Both players of a duel point to the same duel. */
	private static final Map<UUID, Duel> DUELS = new HashMap<>();
	private static final Map<UUID, Truce> TRUCES = new HashMap<>();
	/** "uuid:uuid" (sorted) -> server tick the pair's last rewarded win. */
	private static final Map<String, Long> LAST_REWARD = new HashMap<>();

	static void init() {
		Targeting.sparring = Sparring::areSparring;
		ServerTickEvents.END_SERVER_TICK.register(Sparring::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Sparring::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Sparring::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerPlayerEvents.LEAVE.register(Sparring::onLeave);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onDimensionChange(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHALLENGES.clear();
			DUELS.clear();
			TRUCES.clear();
			// The server tick count starts at 0 again on the next start.
			LAST_REWARD.clear();
		});
	}

	// ================================================================== queries

	/** True when {@code a} and {@code b} are fighting an accepted duel (after the countdown). */
	public static boolean areSparring(ServerPlayer a, ServerPlayer b) {
		if (a == null || b == null || a == b) {
			return false;
		}
		Duel duel = DUELS.get(a.getUUID());
		return duel != null && duel.fighting() && duel.involves(b.getUUID());
	}

	/** True while the player is in a duel, countdown included. */
	public static boolean inDuel(ServerPlayer player) {
		return DUELS.containsKey(player.getUUID());
	}

	// ================================================================== challenge, accept, decline

	/** {@code /sect spar <player>}. Returns the command result (1 sent, 0 refused). */
	public static int challenge(ServerPlayer challenger, ServerPlayer target) {
		if (challenger == target) {
			return refuse(challenger, Component.translatable("kultivator.sects.spar.self"));
		}
		Component problem = problem(challenger, target);
		if (problem != null) {
			return refuse(challenger, problem);
		}
		MinecraftServer server = challenger.level().getServer();
		long now = server.getTickCount();
		Challenge previous = CHALLENGES.get(target.getUUID());
		if (previous != null && !previous.challenger().equals(challenger.getUUID())) {
			ServerPlayer replaced = online(server, previous.challenger());
			if (replaced != null) {
				Msg.chat(replaced, Component.translatable("kultivator.sects.spar.replaced", target.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
			}
		}
		CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), target.getUUID(), now + ANSWER_TICKS));

		Msg.chat(challenger, Component.translatable("kultivator.sects.spar.sent", target.getDisplayName()).withColor(Sects.ACCENT));
		Msg.chat(target, Component.translatable("kultivator.sects.spar.received", challenger.getDisplayName()).withColor(Sects.ACCENT));
		MutableComponent buttons = Component.literal("   ")
				.append(Sects.button("kultivator.sects.spar.accept_button", "/sect spar accept",
						Component.translatable("kultivator.sects.spar.accept_hover", challenger.getDisplayName()), 0x6CFF8A))
				.append(Component.literal("  "))
				.append(Sects.button("kultivator.sects.spar.decline_button", "/sect spar decline",
						Component.translatable("kultivator.sects.spar.decline_hover"), 0xFF7A6A));
		target.sendSystemMessage(buttons);
		ServerLevel level = target.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.8F, 1.2F);
		return 1;
	}

	/** {@code /sect spar accept}: starts the countdown of the pending challenge. */
	public static int accept(ServerPlayer target) {
		MinecraftServer server = target.level().getServer();
		Challenge challenge = CHALLENGES.remove(target.getUUID());
		ServerPlayer challenger = challenge == null || challenge.expires() <= server.getTickCount() ? null : online(server, challenge.challenger());
		if (challenger == null) {
			return refuse(target, Component.translatable("kultivator.sects.spar.none"));
		}
		Component problem = problem(target, challenger);
		if (problem != null) {
			Component forChallenger = problem(challenger, target);
			refuse(challenger, forChallenger != null ? forChallenger : problem);
			return refuse(target, problem);
		}
		// Nobody else may pull either of them into another duel now.
		CHALLENGES.values().removeIf(each -> each.challenger().equals(target.getUUID()) || each.challenger().equals(challenger.getUUID())
				|| each.target().equals(challenger.getUUID()));
		Duel duel = new Duel(challenger.getUUID(), challenger.getPlainTextName(), target.getUUID(), target.getPlainTextName(),
				server.getTickCount() + 1L);
		DUELS.put(duel.a, duel);
		DUELS.put(duel.b, duel);
		Msg.chat(challenger, Component.translatable("kultivator.sects.spar.accepted", target.getDisplayName()).withColor(Sects.ACCENT));
		Msg.chat(target, Component.translatable("kultivator.sects.spar.you_accepted", challenger.getDisplayName()).withColor(Sects.ACCENT));
		return 1;
	}

	/** {@code /sect spar decline}. */
	public static int decline(ServerPlayer target) {
		MinecraftServer server = target.level().getServer();
		Challenge challenge = CHALLENGES.remove(target.getUUID());
		if (challenge == null || challenge.expires() <= server.getTickCount()) {
			return refuse(target, Component.translatable("kultivator.sects.spar.none"));
		}
		ServerPlayer challenger = online(server, challenge.challenger());
		if (challenger != null) {
			Msg.chat(challenger, Component.translatable("kultivator.sects.spar.declined", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
		Msg.chat(target, Component.translatable("kultivator.sects.spar.you_declined",
				challenger == null ? Component.literal("?") : challenger.getDisplayName()).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** Why {@code self} cannot spar with {@code other} right now, from {@code self}'s point of view; null when they can. */
	private static @Nullable Component problem(ServerPlayer self, ServerPlayer other) {
		if (self.isSpectator() || self.isCreative() || other.isSpectator() || other.isCreative()) {
			return Component.translatable("kultivator.sects.spar.mode");
		}
		if (!self.isAlive() || Cultivators.get(self).breakingThrough() || HeartDemons.inTrial(self)) {
			return Component.translatable("kultivator.sects.spar.unavailable_self");
		}
		if (!other.isAlive() || other.hasDisconnected() || Cultivators.get(other).breakingThrough() || HeartDemons.inTrial(other)) {
			return Component.translatable("kultivator.sects.spar.unavailable", other.getDisplayName());
		}
		if (DUELS.containsKey(self.getUUID())) {
			return Component.translatable("kultivator.sects.spar.busy_self");
		}
		if (dev.kultivator.alam.Alam.blocked(self) || dev.kultivator.alam.Alam.blocked(other)) {
			// Phase 8 (DESIGN 25.2 item 12): no sparring in or out of a secret realm run.
			return dev.kultivator.alam.Alam.refusal();
		}
		if (DUELS.containsKey(other.getUUID())) {
			return Component.translatable("kultivator.sects.spar.busy_other", other.getDisplayName());
		}
		if (self.level() != other.level() || self.distanceToSqr(other) > CHALLENGE_RANGE * CHALLENGE_RANGE) {
			return Component.translatable("kultivator.sects.spar.too_far", other.getDisplayName(), (int) CHALLENGE_RANGE);
		}
		if (!self.level().isPvpAllowed()) {
			return Component.translatable("kultivator.sects.spar.pvp_off");
		}
		return null;
	}

	// ================================================================== ticking

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (!CHALLENGES.isEmpty()) {
			expireChallenges(server, now);
		}
		if (!TRUCES.isEmpty()) {
			TRUCES.values().removeIf(truce -> truce.until() <= now);
			// A launch from before the decisive blow may still carry someone over the edge during the truce.
			for (Map.Entry<UUID, Truce> entry : TRUCES.entrySet()) {
				ServerPlayer victim = online(server, entry.getKey());
				ServerPlayer other = online(server, entry.getValue().other());
				if (victim != null && other != null && victim.level() == other.level() && belowWorld(victim)) {
					rescue(victim, other);
				}
			}
		}
		if (DUELS.isEmpty()) {
			return;
		}
		for (Duel duel : new LinkedHashSet<>(DUELS.values())) {
			if (DUELS.get(duel.a) == duel) {
				tickDuel(server, duel, now);
			}
		}
	}

	private static void expireChallenges(MinecraftServer server, long now) {
		Iterator<Challenge> iterator = CHALLENGES.values().iterator();
		while (iterator.hasNext()) {
			Challenge challenge = iterator.next();
			if (challenge.expires() > now) {
				continue;
			}
			iterator.remove();
			ServerPlayer challenger = online(server, challenge.challenger());
			ServerPlayer target = online(server, challenge.target());
			if (challenger != null) {
				Msg.chat(challenger, Component.translatable("kultivator.sects.spar.expired_challenger",
						target == null ? Component.literal("?") : target.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
			if (target != null) {
				Msg.chat(target, Component.translatable("kultivator.sects.spar.expired_target",
						challenger == null ? Component.literal("?") : challenger.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static void tickDuel(MinecraftServer server, Duel duel, long now) {
		ServerPlayer a = online(server, duel.a);
		ServerPlayer b = online(server, duel.b);
		if (a == null || b == null) {
			end(server, duel, null, End.LEFT, a == null ? duel.aName : duel.bName);
			return;
		}
		if (a.isDeadOrDying() || b.isDeadOrDying()) {
			end(server, duel, null, End.DEATH, a.isDeadOrDying() ? duel.aName : duel.bName);
			return;
		}
		if (a.level() != b.level()) {
			end(server, duel, null, End.DIMENSION, "");
			return;
		}
		if (a.distanceToSqr(b) > MAX_DISTANCE * MAX_DISTANCE) {
			end(server, duel, null, End.DISTANCE, "");
			return;
		}
		if (a.isSpectator() || a.isCreative() || b.isSpectator() || b.isCreative()) {
			end(server, duel, null, End.MODE, "");
			return;
		}
		if (duel.fighting()) {
			// Never lethal: someone knocked off an island or into a deep hole is caught long before the void hurts
			// (void damage starts 64 blocks below the world), put back next to the partner, and loses the duel.
			for (ServerPlayer victim : new ServerPlayer[]{a, b}) {
				if (belowWorld(victim)) {
					ServerPlayer partner = victim == a ? b : a;
					rescue(victim, partner);
					end(server, duel, partner, End.DEFEAT, victim.getPlainTextName());
					return;
				}
			}
		}
		if (!duel.fighting()) {
			long elapsed = now - duel.accepted;
			if (elapsed >= COUNTDOWN_TICKS) {
				begin(duel, a, b, now);
			} else if (elapsed >= 0L && elapsed % 20L == 0L) {
				countdown(a, b, (int) (3L - elapsed / 20L));
			}
			return;
		}
		long left = duel.started + DUEL_TICKS - now;
		if (left <= 0L) {
			end(server, duel, null, End.TIMEOUT, "");
			return;
		}
		if (left % 20L == 0L) {
			String seconds = String.valueOf(left / 20L);
			Msg.actionBar(a, Component.translatable("kultivator.sects.spar.actionbar", b.getDisplayName(), seconds).withColor(Sects.ACCENT));
			Msg.actionBar(b, Component.translatable("kultivator.sects.spar.actionbar", a.getDisplayName(), seconds).withColor(Sects.ACCENT));
		}
	}

	private static void countdown(ServerPlayer a, ServerPlayer b, int number) {
		Component title = Component.literal(String.valueOf(number)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		Msg.title(a, title, Component.translatable("kultivator.sects.spar.countdown", b.getDisplayName()).withColor(Sects.ACCENT), 0, 22, 4);
		Msg.title(b, title, Component.translatable("kultivator.sects.spar.countdown", a.getDisplayName()).withColor(Sects.ACCENT), 0, 22, 4);
		Vec3 mid = mid(a, b);
		a.level().playSound(null, mid.x, mid.y, mid.z, SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, volume(a, b, 0.8F), 0.8F + (3 - number) * 0.2F);
	}

	private static void begin(Duel duel, ServerPlayer a, ServerPlayer b, long now) {
		duel.started = now;
		duel.startA = a.getHealth() / Math.max(1.0F, a.getMaxHealth());
		duel.startB = b.getHealth() / Math.max(1.0F, b.getMaxHealth());
		CultivationData dataA = Cultivators.get(a);
		CultivationData dataB = Cultivators.get(b);
		dataA.setSparringWith(b.getId());
		dataB.setSparringWith(a.getId());
		Cultivators.dirty(a);
		Cultivators.dirty(b);

		Component title = Component.translatable("kultivator.sects.spar.start_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		Msg.title(a, title, Component.translatable("kultivator.sects.spar.start_subtitle", b.getDisplayName()).withColor(Sects.ACCENT), 0, 30, 10);
		Msg.title(b, title, Component.translatable("kultivator.sects.spar.start_subtitle", a.getDisplayName()).withColor(Sects.ACCENT), 0, 30, 10);
		String seconds = String.valueOf(DUEL_TICKS / 20);
		Msg.chat(a, Component.translatable("kultivator.sects.spar.started", b.getDisplayName(), seconds).withColor(Sects.ACCENT));
		Msg.chat(b, Component.translatable("kultivator.sects.spar.started", a.getDisplayName(), seconds).withColor(Sects.ACCENT));

		// Start effect: a = A's colour, f = B's colour (an RGB value fits exactly in a float), target = B.
		ServerLevel level = a.level();
		Fx.send(level, a.position(), b.position(), FxType.SPARRING, colorOf(a), 1, (float) colorOf(b));
		Vec3 mid = mid(a, b);
		float volume = volume(a, b, 1.0F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, volume, 0.7F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, volume, 0.6F);
	}

	// ================================================================== damage

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer victim) || DUELS.isEmpty() && TRUCES.isEmpty()) {
			return true;
		}
		MinecraftServer server = victim.level().getServer();
		long now = server.getTickCount();
		Duel duel = DUELS.get(victim.getUUID());
		ServerPlayer attacker = attackerOf(source);
		// Harm without any attacker: burning, frost, a fall after a launch, a wall, cactus, lava or drowning after a push.
		// The void and /kill are never held back.
		boolean environmental = attacker == null && source.getEntity() == null && source.getDirectEntity() == null
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		boolean credited = false;
		if (environmental) {
			if (duel != null && duel.fighting()) {
				// Shortly after the partner's blow it is the partner's doing, so the 10% rule below covers it.
				attacker = lingeringSource(server, duel, victim, now);
				credited = attacker != null;
				if (attacker == null && amount > 0.0F && nearDefeat(victim, source, amount)) {
					// Still never lethal during a duel: the fight ends instead.
					ServerPlayer partner = online(server, duel.partnerOf(victim.getUUID()));
					if (partner != null) {
						end(server, duel, partner, End.DEFEAT, victim.getPlainTextName());
					} else {
						end(server, duel, null, End.LEFT, duel.partnerName(victim.getUUID()));
					}
					return false;
				}
			} else {
				// A launch or push from before the decisive blow can still land during the truce.
				Truce truce = TRUCES.get(victim.getUUID());
				if (truce != null && truce.until() > now && amount > 0.0F && nearDefeat(victim, source, amount)) {
					return false;
				}
			}
		}
		if (attacker == null || attacker == victim) {
			return true;
		}
		Truce truce = TRUCES.get(victim.getUUID());
		if (truce != null && truce.until() > now && truce.other().equals(attacker.getUUID())) {
			return false;
		}
		if (duel == null || !duel.involves(attacker.getUUID())) {
			return true;
		}
		if (!duel.fighting()) {
			// No blows before the countdown is over.
			return false;
		}
		if (!(amount > 0.0F)) {
			return true;
		}
		// The estimate is an upper bound (blocking and the short invulnerability only lower it), so this is never lethal.
		float health = victim.getHealth() + victim.getAbsorptionAmount();
		float estimate = DamageEstimate.upperBound(victim, source, amount);
		float after = health - estimate;
		// A raised shield may stop the blow: it goes through unless it could kill, and the health left decides afterwards.
		boolean shielded = !credited && victim.isBlocking();
		if (after <= 0.0F || !shielded && after < victim.getMaxHealth() * DEFEAT_FRACTION) {
			duel.addDealt(victim.getUUID(), Math.min(estimate, Math.max(0.0F, health)));
			end(server, duel, attacker, End.DEFEAT, victim.getPlainTextName());
			return false;
		}
		if (!credited) {
			duel.markHit(victim.getUUID(), now);
		}
		return true;
	}

	/** Counts what the partner's blows really took, and ends the duel when a blow left a duelist below 10%. */
	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer victim) || DUELS.isEmpty()) {
			return;
		}
		Duel duel = DUELS.get(victim.getUUID());
		if (duel == null || !duel.fighting()) {
			return;
		}
		ServerPlayer attacker = attackerOf(source);
		if (attacker == null || attacker == victim || !duel.involves(attacker.getUUID())) {
			return;
		}
		duel.addDealt(victim.getUUID(), damageTaken);
		if (victim.isAlive() && victim.getHealth() < victim.getMaxHealth() * DEFEAT_FRACTION) {
			end(victim.level().getServer(), duel, attacker, End.DEFEAT, victim.getPlainTextName());
		}
	}

	/** True when {@code amount} of {@code source} could leave {@code victim} below the defeat share of their max health. */
	private static boolean nearDefeat(ServerPlayer victim, DamageSource source, float amount) {
		float after = victim.getHealth() + victim.getAbsorptionAmount() - DamageEstimate.upperBound(victim, source, amount);
		return after < victim.getMaxHealth() * DEFEAT_FRACTION;
	}

	/** The partner, when they hurt {@code victim} in the last few seconds (so attacker-less harm is theirs); else null. */
	private static @Nullable ServerPlayer lingeringSource(MinecraftServer server, Duel duel, ServerPlayer victim, long now) {
		ServerPlayer partner = online(server, duel.partnerOf(victim.getUUID()));
		if (partner == null) {
			return null;
		}
		boolean recent = now - duel.lastHitOn(victim.getUUID()) <= LINGER_TICKS;
		return recent || victim.getLastHurtByMob() == partner ? partner : null;
	}

	/** Puts out what a duel may leave burning on a duelist (fire, wither, poison, frost); only touches the entity. */
	private static void soothe(ServerPlayer player) {
		player.clearFire();
		player.setTicksFrozen(0);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
	}

	/** The player behind a blow: the attacker itself, a projectile's owner, a technique's caster, or a pet's owner. */
	private static @Nullable ServerPlayer attackerOf(DamageSource source) {
		Entity cause = source.getEntity();
		if (cause instanceof ServerPlayer player) {
			return player;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof ServerPlayer player) {
			return player;
		}
		if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		// A duelist's tamed pet or spirit beast fights for them: its bites count as theirs, so the 10% rule covers them.
		return ownerOf(cause);
	}

	/** The online player owning {@code entity} as a spirit beast or a tamed pet; else null. */
	private static @Nullable ServerPlayer ownerOf(@Nullable Entity entity) {
		if (entity == null || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		BeastData beast = SpiritBeasts.data(entity);
		if (beast != null && beast.owner() != null) {
			return level.getServer().getPlayerList().getPlayer(beast.owner());
		}
		if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		return null;
	}

	// ================================================================== other end paths

	private static void onDeath(ServerPlayer player) {
		Duel duel = DUELS.get(player.getUUID());
		if (duel != null) {
			end(player.level().getServer(), duel, null, End.DEATH, player.getPlainTextName());
		}
	}

	private static void onLeave(ServerPlayer player) {
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		Iterator<Challenge> iterator = CHALLENGES.values().iterator();
		while (iterator.hasNext()) {
			Challenge challenge = iterator.next();
			if (challenge.challenger().equals(id) || challenge.target().equals(id)) {
				iterator.remove();
				ServerPlayer other = online(server, challenge.challenger().equals(id) ? challenge.target() : challenge.challenger());
				if (other != null && other != player) {
					Msg.chat(other, Component.translatable("kultivator.sects.spar.challenge_left", player.getDisplayName())
							.withStyle(ChatFormatting.GRAY));
				}
			}
		}
		Duel duel = DUELS.get(id);
		if (duel != null) {
			end(server, duel, null, End.LEFT, player.getPlainTextName());
		}
		TRUCES.remove(id);
	}

	private static void onDimensionChange(ServerPlayer player) {
		Duel duel = DUELS.get(player.getUUID());
		if (duel != null) {
			end(player.level().getServer(), duel, null, End.DIMENSION, "");
		}
	}

	// ================================================================== ending

	/** Ends the duel in every path: clears the state of both players, then rewards, tells and shows the result. */
	private static void end(MinecraftServer server, Duel duel, @Nullable ServerPlayer winner, End reason, String who) {
		DUELS.remove(duel.a, duel);
		DUELS.remove(duel.b, duel);
		ServerPlayer a = online(server, duel.a);
		ServerPlayer b = online(server, duel.b);
		List<ServerPlayer> present = new ArrayList<>(2);
		for (ServerPlayer player : new ServerPlayer[]{a, b}) {
			if (player == null) {
				continue;
			}
			present.add(player);
			CultivationData data = Cultivators.get(player);
			if (data.sparringWith() != -1) {
				data.setSparringWith(-1);
				Cultivators.dirty(player);
			}
			if (duel.fighting()) {
				// Fire or wither left by the partner's techniques, or a fall already under way, must not finish anyone off.
				soothe(player);
				player.resetFallDistance();
			}
		}
		if (duel.fighting() && a != null && b != null && a.level() == b.level()) {
			// Plain pets keep their owner's fight going after the duel; the loser is left with little health.
			calmPets(a, b);
			calmPets(b, a);
		}

		if (reason == End.DEFEAT && winner != null) {
			ServerPlayer loser = winner == a ? b : a;
			if (loser != null) {
				victory(server, duel, winner, loser);
				return;
			}
		}

		if (reason == End.TIMEOUT && a != null && b != null) {
			Component title = Component.translatable("kultivator.sects.spar.title_draw").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
			Msg.title(a, title, Component.translatable("kultivator.sects.spar.draw_subtitle", b.getDisplayName()), 5, 40, 15);
			Msg.title(b, title, Component.translatable("kultivator.sects.spar.draw_subtitle", a.getDisplayName()), 5, 40, 15);
			Msg.chat(a, Component.translatable("kultivator.sects.spar.draw", b.getDisplayName()).withColor(Sects.ACCENT));
			Msg.chat(b, Component.translatable("kultivator.sects.spar.draw", a.getDisplayName()).withColor(Sects.ACCENT));
		} else {
			Component line = switch (reason) {
				case LEFT -> Component.translatable("kultivator.sects.spar.end.left", who);
				case DEATH -> Component.translatable("kultivator.sects.spar.end.death", who);
				case DIMENSION -> Component.translatable("kultivator.sects.spar.end.dimension");
				case DISTANCE -> Component.translatable("kultivator.sects.spar.end.distance", (int) MAX_DISTANCE);
				case MODE -> Component.translatable("kultivator.sects.spar.end.mode");
				default -> Component.translatable("kultivator.sects.spar.end.other");
			};
			for (ServerPlayer player : present) {
				Msg.chat(player, line.copy().withStyle(ChatFormatting.GRAY));
			}
		}
		if (duel.fighting() && !present.isEmpty()) {
			// End effect without a winner (a = -1) and a soft bell.
			ServerPlayer first = present.get(0);
			ServerPlayer second = present.size() > 1 && present.get(1).level() == first.level() ? present.get(1) : first;
			ServerLevel level = first.level();
			Fx.send(level, first.position(), second.position(), FxType.SPARRING, -1, 0, 0.0F);
			Vec3 mid = mid(first, second);
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, volume(first, second, 0.8F), 1.3F);
		}
	}

	private static void victory(MinecraftServer server, Duel duel, ServerPlayer winner, ServerPlayer loser) {
		long now = server.getTickCount();
		TRUCES.put(winner.getUUID(), new Truce(loser.getUUID(), now + TRUCE_TICKS));
		TRUCES.put(loser.getUUID(), new Truce(winner.getUUID(), now + TRUCE_TICKS));

		// Only a real fight teaches something: long enough, a loser who started healthy and lost much of it to the winner,
		// and not the same pair again within the cooldown.
		String pair = pairKey(winner.getUUID(), loser.getUUID());
		Long last = LAST_REWARD.get(pair);
		boolean paid = duel.fighting() && now - duel.started >= MIN_FIGHT_TICKS
				&& duel.startOf(loser.getUUID()) >= MIN_START_HEALTH
				&& duel.dealtTo(loser.getUUID()) >= loser.getMaxHealth() * MIN_DEALT_FRACTION
				&& (last == null || now - last >= REWARD_COOLDOWN_TICKS);

		Msg.title(winner, Component.translatable("kultivator.sects.spar.title_win").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.sects.spar.win_subtitle", loser.getDisplayName()), 5, 50, 15);
		Msg.title(loser, Component.translatable("kultivator.sects.spar.title_lose").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD),
				Component.translatable("kultivator.sects.spar.lose_subtitle", winner.getDisplayName()), 5, 50, 15);
		Msg.chat(winner, Component.translatable("kultivator.sects.spar.win", loser.getDisplayName()).withColor(0xFFD86A));
		if (paid) {
			LAST_REWARD.put(pair, now);
			CultivationData data = Cultivators.get(winner);
			double xp = data.rank().xpRequired() * XP_FRACTION;
			double shownXp = xp * Progression.xpMultiplier(winner, XpSource.SECT);
			Dao dao = data.dao();
			double insight = dao == null ? 0.0D : Comprehension.gainFor(data, dao, COMPREHENSION);
			Progression.addXp(winner, xp, XpSource.SECT);
			if (dao != null) {
				Comprehension.add(winner, dao, COMPREHENSION);
			}
			Msg.chat(winner, dao == null
					? Component.translatable("kultivator.sects.spar.reward", Msg.number(shownXp)).withColor(0xB8FFB0)
					: Component.translatable("kultivator.sects.spar.reward_dao", Msg.number(shownXp), Comprehension.format(insight),
					dao.displayName()).withColor(0xB8FFB0));
		} else {
			Msg.chat(winner, Component.translatable("kultivator.sects.spar.no_reward").withStyle(ChatFormatting.GRAY));
		}
		Advancements.grant(winner, "spar_won");
		Msg.chat(loser, Component.translatable("kultivator.sects.spar.lose", winner.getDisplayName()).withColor(Sects.ACCENT));

		// Both players' sects hear about it (the duelists already got their own lines).
		SectData sects = SectData.get(server);
		Set<UUID> skip = Set.of(winner.getUUID(), loser.getUUID());
		Component news = Component.translatable("kultivator.sects.spar.announce", winner.getDisplayName(), loser.getDisplayName());
		Sect winnerSect = sects.sectOf(winner.getUUID());
		Sect loserSect = sects.sectOf(loser.getUUID());
		if (winnerSect != null) {
			Sects.tell(server, winnerSect, news, skip);
		}
		if (loserSect != null && loserSect != winnerSect) {
			Sects.tell(server, loserSect, news, skip);
		}

		// End effect: the winner's burst (a = winner colour), target = the loser.
		ServerLevel level = winner.level();
		Fx.send(level, winner.position(), loser.level() == level ? loser.position() : winner.position(), FxType.SPARRING,
				colorOf(winner), 0, 0.0F);
		level.playSound(null, winner.getX(), winner.getY(), winner.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.1F);
		level.playSound(null, winner.getX(), winner.getY(), winner.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/**
	 * After a decisive blow both players' pets (tamed animals, spirit beasts) that are going for the other duelist stop:
	 * their anger and target are dropped, and the owners' last-hurt timestamps stay, so the owner goals do not pick the
	 * same target again.
	 */
	private static void calmPets(ServerPlayer owner, ServerPlayer other) {
		for (Mob mob : owner.level().getEntitiesOfClass(Mob.class, other.getBoundingBox().inflate(MAX_DISTANCE),
				m -> m.getTarget() == other && ownerOf(m) == owner)) {
			if (mob instanceof NeutralMob neutral) {
				neutral.stopBeingAngry();
			}
			mob.setTarget(null);
		}
	}

	// ================================================================== helpers

	/** The same key for the pair in either order. */
	private static String pairKey(UUID first, UUID second) {
		String a = first.toString();
		String b = second.toString();
		return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
	}

	private static int refuse(ServerPlayer player, Component message) {
		Msg.chat(player, message.copy().withStyle(ChatFormatting.RED));
		return 0;
	}

	/** True when the living player has fallen below the bottom of the world (on the way to the void). */
	private static boolean belowWorld(ServerPlayer player) {
		return player.isAlive() && player.getY() < player.level().getMinY();
	}

	/** Puts {@code victim} back next to {@code partner}, motion and fall stopped. Only moves the entity. */
	private static void rescue(ServerPlayer victim, ServerPlayer partner) {
		victim.resetFallDistance();
		victim.setDeltaMovement(Vec3.ZERO);
		victim.teleportTo(partner.level(), partner.getX(), partner.getY(), partner.getZ(), Relative.ROTATION, 0.0F, 0.0F, false);
		victim.resetFallDistance();
	}

	private static @Nullable ServerPlayer online(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		return player == null || player.hasDisconnected() ? null : player;
	}

	/** The colour a duelist shows in the effects: their Dao, else their root, else their realm. */
	private static int colorOf(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao();
		if (dao != null) {
			return dao.color() & 0xFFFFFF;
		}
		if (data.rootTested()) {
			return Element.mainColor(data.rootElements()) & 0xFFFFFF;
		}
		return data.realm().color() & 0xFFFFFF;
	}

	private static Vec3 mid(ServerPlayer a, ServerPlayer b) {
		return a.position().add(b.position()).scale(0.5D).add(0.0D, 1.0D, 0.0D);
	}

	/** Loud enough that both duelists hear a sound played between them. */
	private static float volume(ServerPlayer a, ServerPlayer b, float base) {
		double half = a.level() == b.level() ? Math.sqrt(a.distanceToSqr(b)) * 0.5D : 0.0D;
		return Math.max(base, (float) (half / 16.0D) + 0.4F);
	}

	private Sparring() {
	}
}

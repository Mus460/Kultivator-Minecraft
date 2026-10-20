package dev.kultivator.voice;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.purba.PrimordialBeastEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What disciples say (DESIGN: voices, module V). Every disciple keeps one of six voices for life, picked from its soul id:
 * female for the default characters Alex, Ari, Efe, Makena and Noor (its face), male for Kai, Steve, Sunny and Zuri; the
 * number 1-3 from the soul id. The lines come from the master's once-a-second scan ({@link #scan}) and from the hooks
 * ({@link VoiceHooks}); every line is rare and spaced: one disciple line per master every {@link #MASTER_GAP} ticks, each
 * disciple its own cooldown per line, combat lines at most every {@link #COMBAT_GAP} ticks (8 s).
 */
public final class VoiceDisciples {

	public static final int MASTER_GAP = 60;
	public static final int COMBAT_GAP = 160;
	/** The master counts as "away" after this long beyond 16 blocks (5 minutes). */
	public static final int AWAY_TICKS = 6000;
	private static final double SCAN = 24.0D;
	/** Skin indexes of the default characters with a female face: alex, ari, efe, makena, noor. */
	private static final int FEMALE_SKINS = 1 | 1 << 1 | 1 << 2 | 1 << 4 | 1 << 5;

	private static final class State {
		long lastNear = -1L;
		boolean fighting;
		@Nullable LivingEntity lastTarget;
	}

	private static final Map<DiscipleEntity, State> STATES = new WeakHashMap<>();

	// ================================================================== voice

	/** True when the disciple speaks with a female voice. */
	public static boolean female(DiscipleEntity disciple) {
		int skin = disciple.skinIndex();
		return skin >= 0 && skin < 31 && (FEMALE_SKINS & (1 << skin)) != 0;
	}

	/** "m1".."m3" or "f1".."f3", stable for the soul. */
	public static String voiceOf(DiscipleEntity disciple) {
		UUID soul = disciple.soulId();
		long h = soul.getMostSignificantBits() * 31L + soul.getLeastSignificantBits();
		h ^= h >>> 29;
		int n = 1 + (int) Math.floorMod(h, 3L);
		return (female(disciple) ? "f" : "m") + n;
	}

	/** The disciple says {@code line} (one of {@link VoiceEvents#DISCIPLE}) if its cooldown for that line allows. */
	public static boolean line(@Nullable DiscipleEntity disciple, String line, int cooldown) {
		if (disciple == null || !disciple.isAlive() || disciple.isRemoved() || disciple.role() != DiscipleEntity.Role.DISCIPLE
				|| disciple.isReleasing() || !Voice.free(disciple)) {
			return false;
		}
		UUID master = disciple.ownerId();
		if (master != null && !Voice.isReady(master, "disciple_gap")) {
			return false;
		}
		if (!Voice.ready(disciple.getUUID(), "d:" + line, cooldown)) {
			return false;
		}
		if (master != null) {
			Voice.ready(master, "disciple_gap", MASTER_GAP);
		}
		Voice.say(disciple, "disciple." + voiceOf(disciple) + "." + line, null);
		return true;
	}

	/** A grunt without words: attack, hurt or death. */
	public static void grunt(@Nullable DiscipleEntity disciple, String what) {
		if (disciple == null || disciple.isRemoved()) {
			return;
		}
		boolean female = female(disciple);
		String w = female && "death".equals(what) ? "hurt" : what;
		if (!Voice.ready(disciple.getUUID(), "grunt", "death".equals(what) ? 1 : 40)) {
			return;
		}
		float pitch = 0.94F + (Math.floorMod(disciple.soulId().hashCode(), 13)) * 0.01F;
		Voice.say(disciple, "grunt." + (female ? "female." : "male.") + w, "", pitch);
	}

	// ================================================================== the master's scan (once a second)

	static void scan(ServerPlayer master) {
		if (master.isSpectator()) {
			return;
		}
		ServerLevel level = master.level();
		AABB box = master.getBoundingBox().inflate(SCAN);
		List<DiscipleEntity> near = new ArrayList<>(level.getEntitiesOfClass(DiscipleEntity.class, box,
				d -> d.isAlive() && d.role() == DiscipleEntity.Role.DISCIPLE && d.isOwnedBy(master) && !d.isReleasing()));
		if (near.isEmpty()) {
			return;
		}
		long now = Voice.now();
		Vec3 at = master.position();
		boolean masterLow = master.isAlive() && master.getHealth() < master.getMaxHealth() * 0.30F;
		near.sort(Comparator.comparingDouble(d -> d.distanceToSqr(at)));
		boolean careful = false;
		for (DiscipleEntity d : near) {
			State state = STATES.computeIfAbsent(d, k -> new State());
			double dist2 = d.distanceToSqr(at);
			// greet: the master is back within 8 blocks after five minutes away
			if (dist2 <= 64.0D && (state.lastNear < 0L || now - state.lastNear >= AWAY_TICKS)) {
				state.lastNear = now;
				int choice = Math.floorMod((int) (now / 20L) + d.getId(), 3);
				line(d, choice == 0 ? "greet_alt" : "greet", 1200);
			} else if (dist2 <= 256.0D && state.lastNear >= 0L) {
				state.lastNear = now;
			}
			// careful: the master is badly hurt nearby (one disciple speaks)
			if (masterLow && !careful && dist2 <= 256.0D && Voice.ready(master.getUUID(), "careful", 600)) {
				careful = line(d, "careful", 600);
			}
			combat(d, master, state, now);
			// cultivate: an occasional word while it cultivates beside its master
			if (!state.fighting && d.isMeditatingNow() && dist2 <= 144.0D && d.getRandom().nextInt(120) == 0) {
				line(d, "cultivate", 6000);
			}
		}
	}

	private static void combat(DiscipleEntity d, ServerPlayer master, State state, long now) {
		LivingEntity target = d.getTarget();
		boolean fighting = target != null && target.isAlive();
		float health = d.getHealth() / Math.max(1.0F, d.getMaxHealth());
		if (fighting) {
			state.fighting = true;
			state.lastTarget = target;
			boolean stronger = target instanceof PrimordialBeastEntity || target.getMaxHealth() >= d.getMaxHealth() * 4.0F;
			if (stronger && line(d, "fear", 1200)) {
				return;
			}
			if (health < 0.30F && line(d, "injured", 600)) {
				return;
			}
			if (d.getRandom().nextFloat() < 0.18F && Voice.ready(d.getUUID(), "combat", COMBAT_GAP)) {
				Entity theirs = target instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
				String line = theirs == master ? "protect" : d.getRandom().nextBoolean() ? "attack" : "attack_alt";
				line(d, line, 1);
			}
			return;
		}
		if (state.fighting) {
			state.fighting = false;
			LivingEntity last = state.lastTarget;
			state.lastTarget = null;
			if (last != null && !last.isAlive() && d.getRandom().nextFloat() < 0.65F) {
				Voice.later(20, () -> line(d, "victory", 400));
			} else if (health < 0.50F) {
				line(d, "tired", 2400);
			}
		} else if (health < 0.35F && d.getRandom().nextInt(30) == 0) {
			line(d, "tired", 2400);
		}
	}

	/** A technique of the disciple (its cast path): sometimes it shouts the art's name. */
	static void technique(DiscipleEntity d) {
		if (d.getRandom().nextFloat() < 0.35F && Voice.ready(d.getUUID(), "combat", COMBAT_GAP)) {
			line(d, "technique", 1);
		}
	}

	/** The nearest of the master's disciples within {@code radius} blocks of {@code pos}, or null. */
	static @Nullable DiscipleEntity nearest(ServerPlayer master, Vec3 pos, double radius) {
		ServerLevel level = master.level();
		List<DiscipleEntity> near = level.getEntitiesOfClass(DiscipleEntity.class, new AABB(pos, pos).inflate(radius),
				d -> d.isAlive() && d.role() == DiscipleEntity.Role.DISCIPLE && d.isOwnedBy(master) && !d.isReleasing());
		DiscipleEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (DiscipleEntity d : near) {
			double dist = d.distanceToSqr(pos);
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	/** The master left its disciples behind (another dimension, a long teleport): one that stays calls after it. */
	static void farewell(ServerPlayer master, ServerLevel from, Vec3 pos) {
		List<DiscipleEntity> near = from.getEntitiesOfClass(DiscipleEntity.class, new AABB(pos, pos).inflate(16.0D),
				d -> d.isAlive() && d.role() == DiscipleEntity.Role.DISCIPLE && d.isOwnedBy(master) && !d.isReleasing() && !d.mode().follows());
		for (DiscipleEntity d : near) {
			UUID owner = d.ownerId();
			if (owner != null && Voice.ready(d.getUUID(), "d:farewell", 12000)) {
				Voice.callTo(master, d, "disciple." + voiceOf(d) + ".farewell");
				return;
			}
		}
	}

	static void clear() {
		STATES.clear();
	}

	private VoiceDisciples() {
	}
}

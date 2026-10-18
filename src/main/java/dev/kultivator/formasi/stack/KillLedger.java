package dev.kultivator.formasi.stack;

import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-mob, per-second ledger of all 7c killing damage (DESIGN 24.5): only the two strongest arrays count, the sum is
 * capped at {@code KILL_DPS_CAP = (2.0 + 0.5 × L) × agung} per second and at 25 % of max health per second (elites 15 %,
 * bosses 5 %). Target modifiers apply before the caps (in {@code CoreContext.hurt}).
 */
public final class KillLedger {

	private static final class Entry {
		long second;
		double dealt;
		final Map<Integer, Double> byCore = new LinkedHashMap<>();
	}

	private static final Map<Integer, Entry> LEDGER = new HashMap<>();

	/** The cap per second for {@code target} of {@code owner} at hall level {@code hallLevel}. */
	public static double cap(ServerLevel level, Mob target, UUID owner, int hallLevel) {
		double dps = (Stacking.KILL_DPS_BASE + Stacking.KILL_DPS_PER_LEVEL * Math.max(1, hallLevel)) * agung(level, target.position(), owner);
		double share = TechniquePower.isBoss(target) ? Stacking.KILL_HEALTH_SHARE_BOSS
				: EliteMonsters.isElite(target) ? Stacking.KILL_HEALTH_SHARE_ELITE : Stacking.KILL_HEALTH_SHARE;
		return Math.min(dps, target.getMaxHealth() * share);
	}

	/** {@code min(1.5, damageFactor)} inside an active Formasi Agung of the owner, else 1. */
	private static double agung(ServerLevel level, Vec3 pos, UUID owner) {
		FormationData data = FormationData.get(level);
		if (data.isEmpty()) {
			return 1.0D;
		}
		for (Formation formation : data.all()) {
			if (!formation.active() || !formation.owner().equals(owner)) {
				continue;
			}
			double radius = GrandFormation.radius(formation);
			if (Vec3.atCenterOf(formation.pos()).distanceToSqr(pos) <= radius * radius) {
				GrandFormation.State state = GrandFormation.state(level, formation, level.getServer().getTickCount());
				if (state.grand()) {
					return Math.min(Stacking.KILL_AGUNG_CAP, Math.max(1.0D, state.damageFactor()));
				}
			}
		}
		return 1.0D;
	}

	/** Damage still allowed on {@code target} this second for core {@code coreId}. */
	public static double budget(ServerLevel level, Mob target, UUID owner, int hallLevel, int coreId) {
		Entry entry = entry(level, target);
		if (!entry.byCore.containsKey(coreId) && entry.byCore.size() >= Stacking.KILL_ARRAYS_COUNTED) {
			return 0.0D;
		}
		return Math.max(0.0D, cap(level, target, owner, hallLevel) - entry.dealt);
	}

	/** Records {@code amount} dealt by {@code coreId}. */
	public static void note(ServerLevel level, Mob target, int coreId, double amount) {
		Entry entry = entry(level, target);
		entry.dealt += amount;
		entry.byCore.merge(coreId, amount, Double::sum);
	}

	/** Dealt this second on {@code target} (test command). */
	public static double dealt(ServerLevel level, Mob target) {
		Entry entry = LEDGER.get(target.getId());
		return entry == null || entry.second != level.getGameTime() / 20L ? 0.0D : entry.dealt;
	}

	private static Entry entry(ServerLevel level, Mob target) {
		long second = level.getGameTime() / 20L;
		Entry entry = LEDGER.computeIfAbsent(target.getId(), id -> new Entry());
		if (entry.second != second) {
			entry.second = second;
			entry.dealt = 0.0D;
			entry.byCore.clear();
		}
		return entry;
	}

	/** Drops entries older than a few seconds (called every 20 ticks). */
	public static void prune(long gameTime) {
		long second = gameTime / 20L;
		Iterator<Entry> iterator = LEDGER.values().iterator();
		while (iterator.hasNext()) {
			if (second - iterator.next().second > 3L) {
				iterator.remove();
			}
		}
	}

	public static int size() {
		return LEDGER.size();
	}

	public static void clear() {
		LEDGER.clear();
	}

	private KillLedger() {
	}
}

package dev.kultivator.formasi.effect.b;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The grand combined arrays (DESIGN 24.4.11). A1 Formasi Sepuluh Ribu Abadi is complete when its pattern runs, the
 * hall's Formasi Agung Sekte is active ("gates and households") and its three parts run: Taiji (L1, M2 or the Agung with
 * 3+ layers), Liangyi (a Bumi+ killing, maze or control array) and Sixiang (B5, B6, B7, L5 or M4). While complete every
 * other running core of the hall gets A +0.15 (through {@code Formasi.addPowerBonus}), the stack cap is 2.8 and
 * breakthroughs inside gain +0.04 quality (both through the field). A2 Sepuluh Formasi Mutlak counts N (0-10): built and
 * running Absolute arrays plus the five 6c Absolute techniques known by the master or the altar's keeper; with N ≥ 3 and
 * at least 2 built, every built Absolute array gets A +0.03 × N and +2 targets; at N = 10 intruders inside them lose
 * Regeneration and Absorption and their pulses quicken.
 */
public final class GrandArrayEffect implements FormationEffect {

	public static final String A1 = "ten_thousand_immortals";
	public static final String A2 = "ten_absolute";
	public static final double A1_BONUS = 0.15D;
	public static final double A1_QUALITY = 0.04D;
	public static final double A2_PER_N = 0.03D;
	public static final int A2_TARGETS = 2;
	public static final int A2_MIN_N = 3;
	public static final int A2_MIN_BUILT = 2;
	public static final int A2_FULL = 10;
	/** A cached status older than this (ticks) means the array stopped running. */
	public static final int STALE_TICKS = 200;
	public static final int AGUNG_TAIJI_LAYERS = 3;

	public static final Set<String> ABSOLUTE = Set.of("heaven_absolute", "earth_fierce", "red_sand", "blazing_flame", "red_water");
	public static final List<String> ABSOLUTE_ARTS = List.of("wind_roaring_formation", "cold_ice_formation", "golden_light_formation",
			"blood_transforming_formation", "soul_falling_formation");
	public static final Set<String> TAIJI = Set.of("five_layer_shield", "concealment_detection");
	public static final Set<String> LIANGYI = Set.of("great_geng_sword", "heaven_absolute", "earth_fierce", "red_sand", "nine_bend_yellow_river",
			"blazing_flame", "red_water");
	public static final Set<String> SIXIANG = Set.of("great_spirit_gathering", "golden_lightning_net", "earth_lung_fire", "ancient_transfer",
			"spirit_nurturing");

	/** A1 status of one owner. */
	public record Grand(long tick, boolean agung, boolean taiji, boolean liangyi, boolean sixiang, boolean complete) {
		public int bits() {
			return (agung ? 1 : 0) | (taiji ? 2 : 0) | (liangyi ? 4 : 0) | (sixiang ? 8 : 0) | (complete ? 16 : 0);
		}
	}

	/** A2 status of one owner. */
	public record Absolute(long tick, int n, int built, int arts, boolean active) {
		public static final Absolute NONE = new Absolute(Long.MIN_VALUE / 2, 0, 0, 0, false);
	}

	private static final Map<UUID, Grand> GRAND = new HashMap<>();
	private static final Map<UUID, Absolute> ABSOLUTE_STATE = new HashMap<>();
	private static final Map<UUID, Integer> ARTS_SEEN = new HashMap<>();
	private static final Map<UUID, Integer> PRETEND = new HashMap<>();
	private static long clock;

	private final boolean a1;

	public GrandArrayEffect(boolean a1) {
		this.a1 = a1;
	}

	/** The server tick counter (FormasiB's tick hook). */
	public static void tickClock(long tick) {
		clock = tick;
	}

	@Override
	public void pulse(CoreContext ctx) {
		UUID owner = ctx.owner();
		ServerPlayer online = ctx.ownerOnline();
		Vec3 eye = ctx.centre();
		if (a1) {
			Set<String> running = BUtil.runningSpecs(owner);
			int layers = BUtil.agungLayers(ctx.level().getServer(), owner);
			boolean agung = layers >= 0;
			boolean taiji = containsAny(running, TAIJI) || layers >= AGUNG_TAIJI_LAYERS;
			boolean liangyi = containsAny(running, LIANGYI);
			boolean sixiang = containsAny(running, SIXIANG);
			boolean complete = agung && taiji && liangyi && sixiang;
			Grand before = GRAND.get(owner);
			GRAND.put(owner, new Grand(clock, agung, taiji, liangyi, sixiang, complete));
			if (complete && (before == null || !before.complete() || clock - before.tick() > STALE_TICKS)) {
				ctx.fx(FxB.GRAND, eye, 0, (float) ctx.radius());
				ctx.level().playSound(null, eye.x, eye.y + 1.0D, eye.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.6F);
				if (online != null) {
					Advancements.grant(online, "ten_thousand_immortals");
				}
			}
			return;
		}
		Set<String> running = BUtil.runningSpecs(owner);
		int built = 0;
		for (String id : ABSOLUTE) {
			if (running.contains(id)) {
				built++;
			}
		}
		int arts = artsKnown(ctx.core(), online);
		Integer pretend = PRETEND.get(owner);
		int n = pretend != null ? pretend : Math.min(A2_FULL, built + arts);
		boolean active = pretend != null ? n >= A2_MIN_N : n >= A2_MIN_N && built >= A2_MIN_BUILT;
		Absolute before = ABSOLUTE_STATE.get(owner);
		ABSOLUTE_STATE.put(owner, new Absolute(clock, n, built, arts, active));
		if (active && (before == null || !before.active() || before.n() != n)) {
			ctx.fx(FxB.GRAND, eye, 1, n);
		}
		if (active && n >= A2_FULL && online != null) {
			Advancements.grant(online, "ten_absolute");
		}
	}

	/** The five 6c Absolute techniques known by the master (online) or by the altar's keepers; remembered while offline. */
	private static int artsKnown(CoreView core, ServerPlayer online) {
		int count = 0;
		CultivationData master = online == null ? null : Cultivators.get(online);
		List<DiscipleEntity> keepers = BUtil.keepers(core);
		for (String art : ABSOLUTE_ARTS) {
			boolean known = master != null && master.knows(art);
			for (DiscipleEntity keeper : keepers) {
				known |= keeper.data().knows(art);
			}
			if (known) {
				count++;
			}
		}
		if (online == null) {
			count = Math.max(count, ARTS_SEEN.getOrDefault(core.owner(), 0));
		} else {
			ARTS_SEEN.put(core.owner(), count);
		}
		return count;
	}

	private static boolean containsAny(Set<String> running, Set<String> wanted) {
		for (String id : wanted) {
			if (running.contains(id)) {
				return true;
			}
		}
		return false;
	}

	/** The last A1 status of {@code owner} (null when never computed). */
	public static Grand grand(UUID owner) {
		Grand grand = GRAND.get(owner);
		return grand == null || clock - grand.tick() > STALE_TICKS ? null : grand;
	}

	/** True when {@code owner}'s A1 is complete right now. */
	public static boolean complete(UUID owner) {
		Grand grand = grand(owner);
		return grand != null && grand.complete();
	}

	/** The A2 status of {@code owner} ({@link Absolute#NONE} when its altar does not run). */
	public static Absolute absolute(UUID owner) {
		Absolute state = ABSOLUTE_STATE.get(owner);
		return state == null || clock - state.tick() > STALE_TICKS ? Absolute.NONE : state;
	}

	/** Extra targets of an Absolute array of {@code owner} (A2 active: +2). */
	public static int absoluteTargets(CoreView core) {
		return ABSOLUTE.contains(core.spec()) && absolute(core.owner()).active() ? A2_TARGETS : 0;
	}

	/** True when {@code core} is a built Absolute array of an owner whose A2 reached ten. */
	public static boolean tenAbsolute(CoreView core) {
		Absolute state = absolute(core.owner());
		return ABSOLUTE.contains(core.spec()) && state.active() && state.n() >= A2_FULL;
	}

	/** The amplifier term of {@code target} from A1 and A2 (registered with {@code Formasi.addPowerBonus}). */
	public static double powerBonus(CoreView target) {
		double bonus = 0.0D;
		if (!A1.equals(target.spec()) && complete(target.owner())) {
			bonus += A1_BONUS;
		}
		if (ABSOLUTE.contains(target.spec())) {
			Absolute state = absolute(target.owner());
			if (state.active()) {
				bonus += A2_PER_N * state.n();
			}
		}
		return Math.min(Stacking.A_CAP, bonus);
	}

	/** {@code uji formasi mutlak <n>}: pretends the A2 count (−1 forgets it). */
	public static void pretend(UUID owner, int n) {
		if (n < 0) {
			PRETEND.remove(owner);
		} else {
			PRETEND.put(owner, Math.min(A2_FULL, n));
		}
	}

	public static Integer pretended(UUID owner) {
		return PRETEND.get(owner);
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		if (a1 && complete(ctx.owner())) {
			sink.stackCap(Stacking.FORMATION_STACK_CAP_A1);
			sink.quality(A1_QUALITY);
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return a1 ? !complete(ctx.owner()) : !absolute(ctx.owner()).active();
	}

	@Override
	public void onState(CoreContext ctx, CoreState from, CoreState to) {
		if (!to.running()) {
			if (a1) {
				GRAND.remove(ctx.owner());
			} else {
				ABSOLUTE_STATE.remove(ctx.owner());
			}
		}
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		if (a1) {
			Grand grand = grand(ctx.owner());
			out.putByte("p", (byte) (grand == null ? 0 : grand.bits()));
		} else {
			out.putByte("n", (byte) absolute(ctx.owner()).n());
		}
	}

	static void clearAll() {
		GRAND.clear();
		ABSOLUTE_STATE.clear();
		ARTS_SEEN.clear();
		PRETEND.clear();
	}
}

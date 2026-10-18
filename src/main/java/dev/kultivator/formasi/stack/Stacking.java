package dev.kultivator.formasi.stack;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.FormasiB;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stacking rules and every cap of DESIGN 24.5 and 24.2.9, all {@code public static final}. {@link #fieldAt} is cached per
 * player and disciple for 20 ticks; only running cores whose members include the subject count, and cores of different
 * owners never stack (the subject's own sect wins, else the owner with the most cores there).
 */
public final class Stacking {

	// ---- power (24.2.9)
	public static final double P_CAP = 1.5D;
	public static final double P_SUPPORT_CAP = 1.3D;
	public static final double A_CAP = 0.40D;
	public static final double K_CAP = 0.25D;
	public static final int RADIUS_BONUS_CAP = 4;
	public static final int RADIUS_CAP = 48;

	// ---- gathering
	public static final double GATHER_SECT_CAP = 1.5D;
	public static final double FORMATION_STACK_CAP = 2.6D;
	public static final double FORMATION_STACK_CAP_A1 = 2.8D;
	public static final double DISCIPLE_GATHER_CAP = 1.5D;
	public static final double DRAIN_FLOOR = 0.7D;

	// ---- healing
	public static final int HEAL_AMP_CAP = 1;
	public static final double DISCIPLE_REGEN_CAP = 2.0D;
	public static final double QI_REGEN_CAP = 1.39D;
	public static final double DEVIATION_CAP = 1.33D;

	// ---- refining
	public static final double REFINE_CHANCE_CAP = 0.10D;
	public static final int REFINE_STEP_CAP = 1;
	public static final double REFINE_TIME_FLOOR = 0.7D;
	public static final double INSCRIBE_CAP = 0.065D;

	// ---- tribulation, breakthrough, protection
	public static final double TRIB_FORMATION_FLOOR = 0.70D;
	public static final double PELINDUNG_TRIB = 0.85D;
	public static final double QUALITY_FORMATION_CAP = 0.06D;
	public static final int BARRIER_LAYER_CAP = 6;

	// ---- killing
	public static final double KILL_DPS_BASE = 2.0D;
	public static final double KILL_DPS_PER_LEVEL = 0.5D;
	public static final double KILL_AGUNG_CAP = 1.5D;
	public static final double KILL_HEALTH_SHARE = 0.25D;
	public static final double KILL_HEALTH_SHARE_ELITE = 0.15D;
	public static final double KILL_HEALTH_SHARE_BOSS = 0.05D;
	public static final int KILL_ARRAYS_COUNTED = 2;
	public static final double ELITE_DAMAGE = 0.6D;
	public static final double BOSS_DAMAGE = 0.25D;

	// ---- control
	public static final int ROOT_COOLDOWN_TICKS = 300;
	public static final int ROOT_ELITE_MAX_TICKS = 40;
	public static final int ROOT_BOSS_MAX_TICKS = 20;

	// ---- alarms
	public static final int ALARM_CORE_KIND_TICKS = 300;
	public static final int ALARM_PLAYER_CHAT_TICKS = 100;
	public static final int ALARM_MOB_TICKS = 1200;
	public static final int ALARM_TITLE_TICKS = 1200;

	// ---- transport
	public static final int TRANSPORT_M3_COOLDOWN = 200;
	public static final int TRANSPORT_L5_COOLDOWN = 600;

	/** Field cache lifetime. */
	public static final int CACHE_TICKS = 20;

	private record Cached(long time, String dim, FormationField field) {
	}

	private record PosCached(long time, FormationField field, List<CoreRecord> cores) {
	}

	/** One core's own contribution (for {@code uji formasi tumpuk}). */
	public record Contribution(CoreRecord record, FormationSpec spec, FormationField field) {
	}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();
	private static final Map<String, PosCached> POS_CACHE = new HashMap<>();
	private static int hits;
	private static int misses;

	/** The field at {@code pos} for {@code who} (null: no member, so {@link FormationField#NONE}). */
	public static FormationField fieldAt(ServerLevel level, Vec3 pos, @Nullable Entity who) {
		if (who == null) {
			return FormationField.NONE;
		}
		CoreTicker.ensureServer(level.getServer());
		long now = level.getGameTime();
		String dim = level.dimension().identifier().toString();
		Cached cached = CACHE.get(who.getUUID());
		if (cached != null && cached.dim().equals(dim) && now - cached.time() < CACHE_TICKS && now >= cached.time()) {
			hits++;
			return cached.field();
		}
		misses++;
		FormationField field = compute(level, pos, who, null, null);
		CACHE.put(who.getUUID(), new Cached(now, dim, field));
		return field;
	}

	/** The field at a block without a subject (furnaces): cores of {@code owner} and its sect, or of anyone when null. */
	public static FormationField fieldAtBlock(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
		CoreTicker.ensureServer(level.getServer());
		long now = level.getGameTime();
		String key = level.dimension().identifier() + "|" + pos.asLong() + "|" + owner;
		PosCached cached = POS_CACHE.get(key);
		if (cached == null || now - cached.time() >= CACHE_TICKS || now < cached.time()) {
			List<CoreRecord> used = new ArrayList<>();
			FormationField field = compute(level, Vec3.atCenterOf(pos), null, owner, used);
			cached = new PosCached(now, field, used);
			POS_CACHE.put(key, cached);
		}
		for (CoreRecord record : cached.cores()) {
			record.lastUse = now;
		}
		return cached.field();
	}

	/** Damage still allowed on {@code target} this second by {@code owner}'s killing arrays (the KillLedger cap). */
	public static double killBudget(Mob target, UUID owner, int hallLevel) {
		if (!(target.level() instanceof ServerLevel level)) {
			return 0.0D;
		}
		return Math.max(0.0D, KillLedger.cap(level, target, owner, hallLevel) - KillLedger.dealt(level, target));
	}

	/** Every running core that contributes at {@code pos} for {@code who}, with its own field. */
	public static List<Contribution> contributions(ServerLevel level, Vec3 pos, Entity who) {
		List<Contribution> out = new ArrayList<>();
		MinecraftServer server = level.getServer();
		for (CoreRecord record : CoreTicker.store(level).all()) {
			FormationSpec spec = FormationRegistry.byId(record.spec);
			FormationEffect effect = spec == null ? null : FormationRegistry.effectOf(spec);
			if (effect == null || !record.state.running() || !Ownership.isMember(server, record.owner, who)
					|| !CoreTicker.covers(level, record, spec, pos)) {
				continue;
			}
			Accumulator acc = new Accumulator(record.owner);
			safeField(effect, new CoreContextImpl(level, record, spec, who), acc);
			out.add(new Contribution(record, spec, acc.build()));
		}
		return out;
	}

	private static FormationField compute(ServerLevel level, Vec3 pos, @Nullable Entity who, @Nullable UUID ownerFilter,
			@Nullable List<CoreRecord> used) {
		MinecraftServer server = level.getServer();
		Map<UUID, List<CoreRecord>> groups = new LinkedHashMap<>();
		for (CoreRecord record : CoreTicker.store(level).all()) {
			if (!record.state.running()) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec == null || FormationRegistry.effectOf(spec) == null || !CoreTicker.covers(level, record, spec, pos)) {
				continue;
			}
			if (who != null ? !Ownership.isMember(server, record.owner, who)
					: ownerFilter != null && !Ownership.memberUuid(server, record.owner, ownerFilter)) {
				continue;
			}
			groups.computeIfAbsent(record.owner, key -> new ArrayList<>()).add(record);
		}
		if (groups.isEmpty()) {
			return FormationField.NONE;
		}
		UUID preferred = who instanceof Player player ? player.getUUID() : who instanceof DiscipleEntity disciple ? disciple.ownerId() : ownerFilter;
		List<CoreRecord> chosen = preferred == null ? null : groups.get(preferred);
		UUID chosenOwner = preferred;
		if (chosen == null) {
			for (Map.Entry<UUID, List<CoreRecord>> entry : groups.entrySet()) {
				if (chosen == null || entry.getValue().size() > chosen.size()) {
					chosen = entry.getValue();
					chosenOwner = entry.getKey();
				}
			}
		}
		Accumulator acc = new Accumulator(chosenOwner);
		for (CoreRecord record : chosen) {
			FormationSpec spec = FormationRegistry.byId(record.spec);
			FormationEffect effect = FormationRegistry.effectOf(spec);
			safeField(effect, new CoreContextImpl(level, record, spec, who), acc);
			if (used != null) {
				used.add(record);
			}
		}
		return acc.build();
	}

	private static void safeField(FormationEffect effect, CoreContextImpl ctx, FieldSink sink) {
		try {
			effect.field(ctx, sink);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Formation field of {} failed", ctx.spec().id(), exception);
		}
	}

	/** Cache stats since the last call: {hits, misses}. */
	public static int[] takeStats() {
		int[] stats = {hits, misses};
		hits = 0;
		misses = 0;
		return stats;
	}

	/** Drops stale cache entries (every 20 ticks) and everything on server stop. */
	public static void prune(long gameTime) {
		CACHE.values().removeIf(cached -> gameTime - cached.time() > CACHE_TICKS * 3L || gameTime < cached.time());
		POS_CACHE.values().removeIf(cached -> gameTime - cached.time() > CACHE_TICKS * 3L || gameTime < cached.time());
	}

	/** Forgets cached fields (a core changed state). */
	public static void invalidate() {
		CACHE.clear();
		POS_CACHE.clear();
	}

	/** Combines the contributions with the rules of 24.5 and caps them. */
	private static final class Accumulator implements FieldSink {
		private final @Nullable UUID owner;
		private double gather = 1.0D;
		private double unite = 1.0D;
		private double stackCap = FORMATION_STACK_CAP;
		private double drain = 1.0D;
		private int healAmp = -1;
		private double qiRegen = 1.0D;
		private double deviation = 1.0D;
		private double trib = 1.0D;
		private double refineChance;
		private int refineSteps;
		private double refineTime = 1.0D;
		private double inscribe;
		private double quality;
		private int layers;
		private int conceal;
		private int illusion;

		Accumulator(@Nullable UUID owner) {
			this.owner = owner;
		}

		@Override
		public void gather(double g) {
			gather = Math.max(gather, g);
		}

		@Override
		public void unite(double m) {
			if (m > 0.0D) {
				unite = Math.max(unite, m);
			}
		}

		@Override
		public void drain(double f) {
			drain = Math.min(drain, f);
		}

		@Override
		public void heal(int amp, double qi, double dev) {
			healAmp = Math.max(healAmp, amp);
			qiRegen = Math.max(qiRegen, qi);
			deviation = Math.max(deviation, dev);
		}

		@Override
		public void tribulation(double f) {
			if (f > 0.0D) {
				trib *= f;
			}
		}

		@Override
		public void refine(double chance, int steps, double time, double insc) {
			refineChance = Math.max(refineChance, chance);
			refineSteps = Math.max(refineSteps, steps);
			if (time > 0.0D) {
				refineTime = Math.min(refineTime, time);
			}
			inscribe = Math.max(inscribe, insc);
		}

		@Override
		public void quality(double q) {
			quality += q;
		}

		@Override
		public void stackCap(double cap) {
			stackCap = Math.max(stackCap, cap);
		}

		@Override
		public void layers(int n) {
			layers += Math.max(0, n);
		}

		@Override
		public void concealed(int grade) {
			conceal = Math.max(conceal, grade);
		}

		@Override
		public void illusion(int grade) {
			illusion = Math.max(illusion, grade);
		}

		FormationField build() {
			double cap = stackCap;
			if (owner != null && FormasiB.grandComplete(owner)) {
				cap = Math.max(cap, FORMATION_STACK_CAP_A1);
			}
			return new FormationField(gather, unite, Math.min(FORMATION_STACK_CAP_A1, cap), Math.max(DRAIN_FLOOR, drain),
					Math.min(HEAL_AMP_CAP, healAmp), Math.min(QI_REGEN_CAP, qiRegen), Math.min(DEVIATION_CAP, deviation), trib,
					Math.min(REFINE_CHANCE_CAP, refineChance), Math.min(REFINE_STEP_CAP, refineSteps), Math.max(REFINE_TIME_FLOOR, refineTime),
					Math.min(INSCRIBE_CAP, inscribe), Math.min(QUALITY_FORMATION_CAP, quality), Math.min(BARRIER_LAYER_CAP, layers), conceal,
					illusion);
		}
	}

	private Stacking() {
	}
}

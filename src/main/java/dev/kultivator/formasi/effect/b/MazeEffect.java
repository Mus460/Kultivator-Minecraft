package dev.kultivator.formasi.effect.b;

import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.effect.FormationTargets;
import dev.kultivator.formasi.spec.Params;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M1 Formasi Lima Unsur Terbalik and L2 Formasi Sungai Kuning Sembilan Lekuk (DESIGN 24.4.8). Every pulse, intruders
 * inside are slowed and, once per 40 ticks, walked to a waypoint a quarter turn further around the ring (M1 at r − 2,
 * reversing with a gong every 60 s; L2 alternating r − 3 and r − 8, "no straight path in its nine bends"). After the
 * card's time inside a mob is pushed out once and cannot be steered for 10 s. L2 also strips Strength, Speed, Resistance,
 * Fire Resistance, Invisibility, Regeneration and Absorption and silences elite skills ("immortals become mortals");
 * bosses are only stripped. The maze target gate lives in {@link TargetGateImpl}.
 */
public final class MazeEffect implements FormationEffect {

	public static final int IMMUNE_TICKS = 200;
	public static final int SLOW_TICKS = 40;
	public static final int TRAP_AFTER = 200;
	public static final int TRAP_TICKS = 40;
	public static final int IDLE_AFTER = 1200;
	public static final int SUPPRESS_TICKS = 60;
	public static final int NINE_BENDS_STRAYED = 20;
	public static final double EXPEL_PUSH = 1.2D;
	public static final double MIN_RING = 2.0D;
	public static final List<Holder<MobEffect>> STRIPPED = List.of(MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.RESISTANCE,
			MobEffects.FIRE_RESISTANCE, MobEffects.INVISIBILITY, MobEffects.REGENERATION, MobEffects.ABSORPTION);

	private static final class MobState {
		long entered;
		long lastSteer;
		long seen;
		boolean trapped;
		boolean inner;
	}

	private static final class State {
		long lastIntruder;
		long shiftAt = -1L;
		int dir = 1;
		final Map<UUID, MobState> mobs = new HashMap<>();
		final Map<UUID, Long> immune = new HashMap<>();
	}

	private static final Map<BUtil.CoreKey, State> STATES = new HashMap<>();

	@Override
	public void pulse(CoreContext ctx) {
		State state = STATES.computeIfAbsent(BUtil.CoreKey.of(ctx.core()), key -> new State());
		Params params = ctx.spec().params();
		boolean river = params.get("river") > 0.5D;
		long now = ctx.now();
		Vec3 centre = ctx.centre();
		double radius = ctx.radius();
		long shift = (long) params.get("shift");
		if (shift > 0L) {
			if (state.shiftAt < 0L) {
				state.shiftAt = now + shift;
			} else if (now >= state.shiftAt) {
				state.shiftAt = now + shift;
				state.dir = -state.dir;
				ctx.level().playSound(null, centre.x, centre.y + 1.0D, centre.z, SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS, 0.9F, 0.5F);
				ctx.fx(FxB.SHIFT, centre, state.dir > 0 ? 1 : 0, (float) radius);
			}
		}
		boolean keeper = BUtil.keeperRank(ctx.core(), SectRanks.DALAM);
		int slowAmp = (int) params.get("slow");
		long expel = (long) params.get("expel", 400.0D);
		List<Mob> hostiles = ctx.hostiles(24);
		if (!hostiles.isEmpty()) {
			state.lastIntruder = now;
		}
		for (Mob mob : hostiles) {
			boolean elite = EliteMonsters.isElite(mob);
			boolean boss = BUtil.boss(mob);
			if (river) {
				strip(ctx, mob, elite && !boss);
			}
			if (boss || FormationTargets.immuneToMind(mob) || FormationTargets.resists(mob, ctx.core())) {
				continue;
			}
			Long immuneUntil = state.immune.get(mob.getUUID());
			if (immuneUntil != null) {
				if (now < immuneUntil) {
					continue;
				}
				state.immune.remove(mob.getUUID());
			}
			MobState ms = state.mobs.computeIfAbsent(mob.getUUID(), id -> {
				MobState fresh = new MobState();
				fresh.entered = now;
				fresh.lastSteer = now - 1000L;
				return fresh;
			});
			ms.seen = now;
			if (river || !elite) {
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, slowAmp, false, true));
			}
			if (keeper) {
				if (river) {
					mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SLOW_TICKS, 0, false, true));
				} else if (!ms.trapped && now - ms.entered >= TRAP_AFTER) {
					ms.trapped = true;
					if (ctx.root(mob, TRAP_TICKS)) {
						ctx.fx(FxB.ROOT, mob.position(), 1, mob.getBbHeight());
					}
				}
			}
			if (now - ms.entered >= expel) {
				ctx.push(mob, centre, EXPEL_PUSH);
				state.mobs.remove(mob.getUUID());
				state.immune.put(mob.getUUID(), now + IMMUNE_TICKS);
				if (river) {
					strayed(ctx);
				}
				continue;
			}
			long interval = (long) (elite ? params.get("eliteSteer", 80.0D) : params.get("steer", 40.0D));
			if (now - ms.lastSteer >= interval && FormationTargets.steerable(mob)) {
				ms.lastSteer = now;
				double ring = river ? radius - (ms.inner ? params.get("alt", 8.0D) : params.get("ring", 3.0D)) : radius - params.get("ring", 2.0D);
				ms.inner = !ms.inner;
				ring = Math.max(MIN_RING, ring);
				double angle = Math.atan2(mob.getZ() - centre.z, mob.getX() - centre.x) + state.dir * Math.PI / 2.0D;
				ctx.steer(mob, new Vec3(centre.x + Math.cos(angle) * ring, mob.getY(), centre.z + Math.sin(angle) * ring));
			}
		}
		state.mobs.values().removeIf(ms -> now - ms.seen > 100L);
		if (state.immune.size() > 64) {
			state.immune.values().removeIf(until -> until < now);
		}
	}

	/** L2: removes the listed buffs (petals when anything fell off) and silences elite skills for 3 s. */
	private static void strip(CoreContext ctx, Mob mob, boolean silence) {
		boolean any = false;
		for (Holder<MobEffect> effect : STRIPPED) {
			if (mob.hasEffect(effect)) {
				mob.removeEffect(effect);
				any = true;
			}
		}
		if (silence) {
			EliteMonsters.suppressSkills(mob, SUPPRESS_TICKS);
		}
		if (any) {
			ctx.fx(FxB.STRIP, mob.position(), 0, mob.getBbHeight());
		}
	}

	/** Counts a mob led astray by L2 for the owner ({@code nine_bends} at 20). */
	private static void strayed(CoreContext ctx) {
		ServerPlayer owner = ctx.ownerOnline();
		if (owner != null && BUtil.addCount(owner, "strayed", 1) >= NINE_BENDS_STRAYED) {
			Advancements.grant(owner, "nine_bends");
		}
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		sink.illusion(ctx.spec().grade().number() + (BUtil.keeperRank(ctx.core(), SectRanks.DALAM) ? 1 : 0));
	}

	@Override
	public boolean idle(CoreContext ctx) {
		State state = STATES.get(BUtil.CoreKey.of(ctx.core()));
		return state == null || ctx.now() - state.lastIntruder > IDLE_AFTER;
	}

	@Override
	public void onState(CoreContext ctx, CoreState from, CoreState to) {
		if (!to.running()) {
			STATES.remove(BUtil.CoreKey.of(ctx.core()));
		}
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		State state = STATES.get(BUtil.CoreKey.of(ctx.core()));
		out.putByte("d", (byte) (state == null || state.dir > 0 ? 1 : 0));
	}

	/** Forces an immediate shift of an M1 (the master flag, slip). */
	public static void shiftNow(BUtil.CoreKey key) {
		State state = STATES.get(key);
		if (state != null) {
			state.shiftAt = 0L;
		}
	}

	static void clearAll() {
		STATES.clear();
	}
}

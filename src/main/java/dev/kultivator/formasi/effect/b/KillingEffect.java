package dev.kultivator.formasi.effect.b;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.catalog.CatalogB;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.Params;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The killing arrays of DESIGN 24.4.9: B1 Great Geng Sword (sword strikes and the seventy-two sword sweep), B2 Heaven
 * Absolute (particle bolts from a dark cloud disc, never a LightningBolt), B3 Earth Fierce (fire below, thunder above),
 * L3 Blazing Flame (samadhi, air and stone fire) and L4 Red Water (dissolving red water). Damage goes only through
 * {@code ctx.hurt} (the kill ledger and per-mob caps of 24.5, damage type {@code kultivator:formation}, no credit); with
 * config {@code formationDamage} off they only show their effects. Fire is entity fire only. Dao Protectors are skipped.
 * Thunder sounds play only when a target was hit. A2 adds +2 targets; at ten it strips Regeneration and Absorption and
 * runs every third pulse twice (pulse intervals ×0.75).
 */
public final class KillingEffect implements FormationEffect {

	public static final int IDLE_AFTER = 1200;
	public static final double SWORD_HEIGHT = 6.0D;
	public static final double BOLT_HEIGHT = 24.0D;
	public static final float THUNDER_VOLUME = 0.3F;
	public static final double STATION_REACH = 2.0D;
	public static final double WATER_MOB_FACTOR = 1.5D;
	public static final double ELITE_SHARE = 0.5D;
	public static final double BOSS_SHARE = 0.25D;
	private static final int SWORD_COLOR = 0x7FE0C8;
	private static final int BOLT_COLOR = 0xFFF2C0;

	private static final class State {
		long lastIntruder;
		long lastSweep;
		long lastThunder;
		int pulses;
	}

	private static final Map<BUtil.CoreKey, State> STATES = new HashMap<>();

	@Override
	public void pulse(CoreContext ctx) {
		State state = STATES.computeIfAbsent(BUtil.CoreKey.of(ctx.core()), key -> new State());
		boolean ten = GrandArrayEffect.tenAbsolute(ctx.core());
		int runs = ten && state.pulses % 3 == 2 ? 2 : 1;
		state.pulses++;
		for (int i = 0; i < runs; i++) {
			run(ctx, state, ten);
		}
	}

	private static void run(CoreContext ctx, State state, boolean ten) {
		Params params = ctx.spec().params();
		int kind = params.getInt("kind", CatalogB.KILL_SWORD);
		boolean dalam = BUtil.keeperRank(ctx.core(), SectRanks.DALAM);
		int extra = GrandArrayEffect.absoluteTargets(ctx.core()) + (dalam ? kind == CatalogB.KILL_HEAVEN ? 1 : 2 : 0);
		int count = Math.min(24, params.getInt("targets", 6) + extra);
		List<Mob> targets = new ArrayList<>();
		for (Mob mob : ctx.hostiles(24)) {
			if (!BUtil.protector(mob)) {
				targets.add(mob);
			}
		}
		long now = ctx.now();
		if (!targets.isEmpty()) {
			state.lastIntruder = now;
		} else {
			return;
		}
		boolean damage = KultivatorConfig.get().formationDamage;
		double p = ctx.power();
		List<Mob> chosen = targets.subList(0, Math.min(count, targets.size()));
		boolean anyHit = false;
		switch (kind) {
			case CatalogB.KILL_SWORD -> {
				Vec3 from = ctx.centre().add(0.0D, SWORD_HEIGHT, 0.0D);
				for (Mob mob : chosen) {
					Fx.send(ctx.level(), from, mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D), FxType.FORMASI, FxB.SWORD, SWORD_COLOR, 0.0F);
					hit(ctx, mob, params.get("dmg", 4.0D) * p, damage);
				}
				if (now - state.lastSweep >= (long) params.get("sweepEvery", 100.0D)) {
					state.lastSweep = now;
					sweep(ctx, targets, params, p, damage, Math.min(24, params.getInt("sweepTargets", 8) + extra));
				}
			}
			case CatalogB.KILL_HEAVEN -> {
				for (Mob mob : chosen) {
					ctx.fx(FxB.BOLT, mob.position(), BOLT_COLOR, (float) BOLT_HEIGHT);
					if (hit(ctx, mob, params.get("dmg", 6.0D) * p, damage)) {
						anyHit = true;
					}
					mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, params.getInt("slowTicks", 20), 1, false, true));
					if (!mob.isAlive()) {
						ctx.fx(FxB.ASH, mob.position(), 0, mob.getBbHeight());
					}
				}
			}
			case CatalogB.KILL_EARTH -> {
				for (Mob mob : chosen) {
					mob.igniteForSeconds((float) params.get("fire", 3.0D));
					ctx.fx(FxB.FIRE, mob.position(), 3, mob.getBbHeight());
					hit(ctx, mob, params.get("dmg", 2.0D) * p, damage);
				}
				if (now - state.lastThunder >= (long) params.get("thunderEvery", 60.0D)) {
					state.lastThunder = now;
					for (Mob mob : chosen) {
						if (!mob.isAlive()) {
							continue;
						}
						ctx.fx(FxB.THUNDER, mob.position(), BOLT_COLOR, mob.getBbHeight());
						if (hit(ctx, mob, params.get("thunder", 5.0D) * p, damage)) {
							anyHit = true;
						}
					}
				}
			}
			case CatalogB.KILL_BLAZE -> {
				int[] off = BUtil.rotate(0, 5, ctx.core().rot());
				Vec3 station = Vec3.atCenterOf(ctx.core().eye().offset(off[0], 0, off[1]));
				for (Mob mob : chosen) {
					double dealt = params.get("air", 2.5D);
					if (mob.fireImmune()) {
						dealt += params.get("immune", 2.0D);
					} else {
						mob.igniteForSeconds((float) params.get("samadhi", 6.0D));
					}
					boolean stone = mob.position().distanceToSqr(station) <= STATION_REACH * STATION_REACH || onMagma(ctx.level(), mob);
					if (stone) {
						dealt += params.get("stone", 4.0D);
					}
					ctx.fx(FxB.FIRE, mob.position(), stone ? 2 : mob.fireImmune() ? 1 : 0, mob.getBbHeight());
					hit(ctx, mob, dealt * p, damage);
				}
			}
			case CatalogB.KILL_WATER -> {
				for (Mob mob : chosen) {
					double share = params.get("share", 0.01D) * (BUtil.boss(mob) ? BOSS_SHARE : EliteMonsters.isElite(mob) ? ELITE_SHARE : 1.0D);
					double dealt = params.get("dmg", 3.0D) * p + share * mob.getMaxHealth();
					if (mob instanceof Drowned || mob instanceof Guardian) {
						dealt *= WATER_MOB_FACTOR;
					}
					mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, params.getInt("slowTicks", 40), 1, false, true));
					ctx.fx(FxB.WATER, mob.position(), 0, mob.getBbHeight());
					hit(ctx, mob, dealt, damage);
				}
			}
			default -> {
			}
		}
		if (ten) {
			for (Mob mob : chosen) {
				mob.removeEffect(MobEffects.REGENERATION);
				mob.removeEffect(MobEffects.ABSORPTION);
			}
		}
		if (anyHit) {
			Vec3 at = chosen.get(0).position();
			ctx.level().playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, THUNDER_VOLUME, 1.2F);
		}
	}

	/** "Tujuh Puluh Dua Pedang": the intruder with the most neighbours within the sweep radius, and up to {@code max} around it. */
	private static void sweep(CoreContext ctx, List<Mob> targets, Params params, double p, boolean damage, int max) {
		double reach = params.get("sweepRadius", 4.0D);
		double reachSq = reach * reach;
		Mob best = null;
		int bestCount = -1;
		for (Mob mob : targets) {
			int near = 0;
			for (Mob other : targets) {
				if (other != mob && other.distanceToSqr(mob) <= reachSq) {
					near++;
				}
			}
			if (near > bestCount) {
				bestCount = near;
				best = mob;
			}
		}
		if (best == null) {
			return;
		}
		Vec3 centre = best.position();
		ctx.fx(FxB.SWEEP, centre, 0, (float) reach);
		ctx.level().playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.8F, 0.7F);
		int done = 0;
		for (Mob mob : targets) {
			if (done >= max) {
				break;
			}
			if (mob.distanceToSqr(best) <= reachSq) {
				done++;
				hit(ctx, mob, params.get("sweep", 8.0D) * p, damage);
			}
		}
	}

	private static boolean hit(CoreContext ctx, Mob mob, double amount, boolean damage) {
		return damage && amount > 0.0D && mob.isAlive() && ctx.hurt(mob, (float) amount);
	}

	private static boolean onMagma(ServerLevel level, Mob mob) {
		BlockPos below = mob.blockPosition().below();
		return level.isLoaded(below) && level.getBlockState(below).is(Blocks.MAGMA_BLOCK);
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

	static void clearAll() {
		STATES.clear();
	}
}

package dev.kultivator.formasi.effect.b;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Dao;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.Params;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * B4 Formasi Pasir Merah (DESIGN 24.4.8): an intruder that crosses the edge inward "falls into the pit" (rooted 4 s,
 * elites 2 s, bosses 1 s, once per mob per 15 s; Body or Earth keeper +1 s); inside, blade sand deals {@code 1.5 × P}
 * every pulse and Blindness I every 40 ticks (never on bosses); a Murid Dalam+ keeper adds Slowness I. Damage only through
 * {@code ctx.hurt}; nothing touches a block (no gravity blocks, no red sand).
 */
public final class RedSandEffect implements FormationEffect {

	public static final int BLIND_TICKS = 40;
	public static final int KEEPER_ROOT_BONUS = 20;
	public static final int IDLE_AFTER = 1200;

	private static final class State {
		final Set<UUID> inside = new HashSet<>();
		final Map<UUID, Long> rooted = new HashMap<>();
		long lastIntruder;
		long lastBlind;
	}

	private static final Map<BUtil.CoreKey, State> STATES = new HashMap<>();

	@Override
	public void pulse(CoreContext ctx) {
		State state = STATES.computeIfAbsent(BUtil.CoreKey.of(ctx.core()), key -> new State());
		Params params = ctx.spec().params();
		long now = ctx.now();
		boolean damage = KultivatorConfig.get().formationDamage;
		boolean earthKeeper = BUtil.keeperDao(ctx.core(), Dao.BODY);
		boolean dalam = BUtil.keeperRank(ctx.core(), SectRanks.DALAM);
		boolean blind = now - state.lastBlind >= (long) params.get("blind", 40.0D);
		if (blind) {
			state.lastBlind = now;
		}
		List<Mob> hostiles = ctx.hostiles(24);
		Set<UUID> seenNow = new HashSet<>();
		boolean ten = GrandArrayEffect.tenAbsolute(ctx.core());
		for (Mob mob : hostiles) {
			UUID id = mob.getUUID();
			seenNow.add(id);
			boolean boss = BUtil.boss(mob);
			if (!state.inside.contains(id)) {
				Long last = state.rooted.get(id);
				if (last == null || now - last >= Stacking.ROOT_COOLDOWN_TICKS) {
					int ticks = (int) (boss ? params.get("rootBoss", 20.0D) : EliteMonsters.isElite(mob) ? params.get("rootElite", 40.0D)
							: params.get("root", 80.0D));
					if (earthKeeper) {
						ticks += KEEPER_ROOT_BONUS;
					}
					if (ctx.root(mob, ticks)) {
						state.rooted.put(id, now);
						ctx.fx(FxB.ROOT, mob.position(), 0, mob.getBbHeight());
					}
				}
			}
			if (damage && ctx.hurt(mob, (float) (params.get("sand", 1.5D) * ctx.power()))) {
				ctx.fx(FxB.SAND, mob.position(), 0, mob.getBbHeight());
			}
			if (blind && !boss) {
				mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));
			}
			if (dalam) {
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BLIND_TICKS, 0, false, true));
			}
			if (ten) {
				mob.removeEffect(MobEffects.REGENERATION);
				mob.removeEffect(MobEffects.ABSORPTION);
			}
		}
		if (!hostiles.isEmpty()) {
			state.lastIntruder = now;
		}
		state.inside.clear();
		state.inside.addAll(seenNow);
		if (state.rooted.size() > 64) {
			state.rooted.values().removeIf(time -> now - time > Stacking.ROOT_COOLDOWN_TICKS);
		}
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

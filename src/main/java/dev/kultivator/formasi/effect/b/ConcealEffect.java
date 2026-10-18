package dev.kultivator.formasi.effect.b;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Dao;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.effect.FormationTargets;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * K4 Larangan Penyembunyi and M2 Formasi Penyembunyi dan Pengintai (DESIGN 24.4.7). The real work is the event-driven
 * concealment gate ({@link TargetGateImpl}); this handler adds the field grade (Daya Tembus), clears the target of
 * outside mobs that already hunt someone inside (every 40 ticks, radius + 16 for K4, the member boxes of the territory
 * sweep for M2), and for M2 runs the K1 alarm over the whole territory. The concealed-name list for LAN strangers is
 * written by {@link #concealedFrom}. Drain is always full.
 */
public final class ConcealEffect implements FormationEffect {

	public static final String K4 = "concealment_restriction";
	public static final String M2 = "concealment_detection";
	/** K4: outside mobs within radius + 16 that already target someone inside lose that target. */
	public static final double OUTER = 16.0D;
	/** Shadow keeper affinity: K4 radius +4. */
	public static final double SHADOW_RADIUS = 4.0D;

	private final boolean territory;

	public ConcealEffect(boolean territory) {
		this.territory = territory;
	}

	/** The effective grade for Daya Tembus: K4 Kuning (Misterius with a Murid Dalam+ keeper), M2 Misterius. */
	public static int grade(CoreView core, int base) {
		return K4.equals(core.spec()) && BUtil.keeperRank(core, SectRanks.DALAM) ? base + 1 : base;
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		sink.concealed(grade(ctx.core(), ctx.spec().params().getInt("grade", ctx.spec().grade().number())));
	}

	@Override
	public void pulse(CoreContext ctx) {
		ServerLevel level = ctx.level();
		if (territory) {
			boolean shadow = BUtil.keeperDao(ctx.core(), Dao.SHADOW);
			AlarmEffect.detect(ctx, true, shadow ? AlarmEffect.M2_BAND_SHADOW : AlarmEffect.M2_BAND);
			for (Mob mob : TerritorySweep.sweep(level, ctx.owner())) {
				LivingEntity target = mob.getTarget();
				if (target != null && !Halls.inTerritory(ctx.owner(), level, mob.position()) && Halls.inTerritory(ctx.owner(), level, target.position())
						&& fooled(ctx, mob, target)) {
					ctx.loseTarget(mob);
				}
			}
			return;
		}
		double radius = ctx.radius() + (BUtil.keeperDao(ctx.core(), Dao.SHADOW) ? SHADOW_RADIUS : 0.0D);
		Vec3 centre = ctx.centre();
		double reach = radius + OUTER;
		List<Mob> hunters = level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach, Math.max(8.0D, reach / 2.0D), reach),
				mob -> mob.getTarget() != null && AlarmEffect.intruder(level.getServer(), ctx.owner(), mob));
		int handled = 0;
		for (Mob mob : hunters) {
			if (handled >= TerritorySweep.MAX_PER_QUERY) {
				break;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || flat(mob.position(), centre) <= radius || flat(target.position(), centre) > radius) {
				continue;
			}
			handled++;
			if (fooled(ctx, mob, target)) {
				ctx.loseTarget(mob);
			}
		}
	}

	/** True when {@code mob} cannot see {@code target} through this core. */
	private static boolean fooled(CoreContext ctx, Mob mob, LivingEntity target) {
		return ctx.isMember(target) && !BUtil.recentlyHurtBy(mob, target) && !FormationTargets.immuneToMind(mob)
				&& !FormationTargets.resists(mob, ctx.core()) && !BUtil.boss(mob) && !BUtil.protector(mob);
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		out.putByte("g", (byte) grade(ctx.core(), ctx.spec().params().getInt("grade", 1)));
	}

	/**
	 * The entity ids of players {@code viewer} must not nametag (24.7.4): sect members standing inside a running concealment
	 * field of their own sect, when the viewer is a stranger to that sect. Empty when {@code formationConcealNames} is off.
	 */
	public static int[] concealedFrom(ServerPlayer viewer) {
		if (!KultivatorConfig.get().formationConcealNames || !(viewer.level() instanceof ServerLevel level)) {
			return new int[0];
		}
		int[] out = new int[8];
		int n = 0;
		for (ServerPlayer other : level.players()) {
			if (other == viewer || other.isSpectator()) {
				continue;
			}
			for (CoreView core : Cores.covering(level, other.position(), Family.PENYEMBUNYI)) {
				if (BUtil.member(level.getServer(), core.owner(), other) && !BUtil.member(level.getServer(), core.owner(), viewer)) {
					if (n == out.length) {
						out = java.util.Arrays.copyOf(out, n * 2);
					}
					out[n++] = other.getId();
					break;
				}
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}

	private static double flat(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}

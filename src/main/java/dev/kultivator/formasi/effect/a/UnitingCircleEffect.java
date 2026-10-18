package dev.kultivator.formasi.effect.a;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.Slot;
import dev.kultivator.formasi.stack.FieldSink;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * M5 Formasi Lingkar Penyatu (DESIGN 24.4.2): n = occupied cushions (a meditating member or a meditating disciple of the
 * owner within 1 block of one, one per cushion) plus a keeper at the eye as the nucleus (n ≤ 9). Every seated cultivator
 * gets {@code 1 + 0.03 × n × P}, multiplied into the sect gather factor inside the cap. <i>Terinspirasi dari</i> ISSTH
 * Book 5 ch. 735 (the Demonic Incarnation merges its disciples' cultivation, Meng Hao its nucleus).
 */
public final class UnitingCircleEffect implements FormationEffect {

	public static final double PER_SEAT = 0.03D;
	/** A cultivator within this horizontal distance of a cushion sits on it. */
	public static final double SEAT_RANGE = 1.5D;
	public static final char CUSHION = 's';

	@Override
	public void pulse(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		CoreRecord record = impl.record();
		List<Vec3> cushions = cushions(impl);
		List<Entity> sitters = sitters(impl);
		int n = 0;
		for (Vec3 cushion : cushions) {
			for (Entity sitter : sitters) {
				if (near(sitter.position(), cushion)) {
					sitters.remove(sitter);
					n++;
					break;
				}
			}
		}
		if (record.keepersAtPost > 0) {
			n++;
		}
		record.scratch.putInt("n", Math.min(9, n));
		if (n > 0) {
			ctx.fx(FormasiFxIds.PULSE, ctx.centre(), ctx.spec().family().ordinal(), n);
		}
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		int n = impl.record().scratch.getIntOr("n", 0);
		Entity subject = impl.subject();
		if (n <= 0 || subject == null) {
			return;
		}
		boolean seated = Vec3.atCenterOf(impl.record().eye).distanceToSqr(subject.position()) <= 2.0D * 2.0D;
		for (Vec3 cushion : cushions(impl)) {
			seated |= near(subject.position(), cushion);
		}
		if (seated) {
			sink.unite(1.0D + PER_SEAT * n * ctx.power());
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return ((CoreContextImpl) ctx).record().scratch.getIntOr("n", 0) <= 0;
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		out.putByte("n", (byte) ((CoreContextImpl) ctx).record().scratch.getIntOr("n", 0));
	}

	private static boolean near(Vec3 at, Vec3 cushion) {
		double dx = at.x - cushion.x;
		double dz = at.z - cushion.z;
		return dx * dx + dz * dz <= SEAT_RANGE * SEAT_RANGE && Math.abs(at.y - cushion.y) <= 1.5D;
	}

	private static List<Vec3> cushions(CoreContextImpl ctx) {
		List<Vec3> out = new ArrayList<>();
		BlockPos eye = ctx.record().eye;
		for (Slot slot : ctx.spec().pattern().slots(ctx.record().rot)) {
			if (slot.key() == CUSHION) {
				out.add(Vec3.atBottomCenterOf(eye.offset(slot.dx(), slot.dy(), slot.dz())));
			}
		}
		return out;
	}

	private static List<Entity> sitters(CoreContextImpl ctx) {
		List<Entity> out = new ArrayList<>();
		Vec3 centre = ctx.centre();
		for (ServerPlayer player : ctx.level().players()) {
			if (player.distanceToSqr(centre) <= 64.0D && Meditation.isMeditating(player)
					&& Ownership.isMember(ctx.level().getServer(), ctx.owner(), player)) {
				out.add(player);
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(ctx.owner())) {
			if (disciple.level() == ctx.level() && disciple.distanceToSqr(centre) <= 64.0D && disciple.isMeditatingNow()
					&& disciple.mode() == DiscipleEntity.Mode.MEDITATE) {
				out.add(disciple);
			}
		}
		return out;
	}
}

package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.sect.event.VoidRealm;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sayap Kunpeng, the Kunpeng Wings (DESIGN 20.3.16; Perfect World ch. 258-347: the six parts of the Kunpeng's primordial
 * bone give only about a third of the legacy, the true technique lies in the Devil Refinement Cave). Taught by uniting
 * the six fragments (module B); any Dao. A dash of 12 blocks with a wind crescent of 2 U on the monsters along the path,
 * every 20 s; at full power (Void Realm floor 60 cleared) 24 blocks and 6 U. Dashes go through open space only to spots
 * checked by {@link Targeting#safeSpot} (read-only), never through a portal.
 */
final class KunpengWings {

	public static final double DASH = 12.0D;
	public static final double DASH_FULL = 24.0D;
	public static final double CRESCENT_U = 2.0D;
	public static final double CRESCENT_U_FULL = 6.0D;
	public static final double CRESCENT_WIDTH = 1.5D;
	public static final int COOLDOWN = 400;
	/** Ticks the clients draw the dash over (FX_KUNPENG_DASH). */
	public static final int DASH_TICKS = 4;
	/** Inti (Core) and above may receive the wings. */
	public static final int MIN_RANK = 3;

	static boolean has(DiscipleData data) {
		return PowerCore.tag(data).getCompoundOrEmpty("kunpeng").getBooleanOr("has", false);
	}

	static boolean full(DiscipleData data) {
		return VoidRealm.clearedDevilCave(data);
	}

	/** Teaches the wings (DESIGN 20.4.5 {@code teachKunpeng}); false when it cannot learn them. */
	static boolean teach(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (entity.role() != DiscipleEntity.Role.DISCIPLE || has(data) || SectRanks.rank(data) < MIN_RANK) {
			return false;
		}
		PowerCore.editSub(data, "kunpeng").putBoolean("has", true);
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), entity.position().add(0.0D, 6.0D, 0.0D), PowerIds.FX_KUNPENG, full(data) ? 1 : 0, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 0.7F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		}
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, PowerCore.key("kunpeng.learned", PowerCore.name(entity)).withColor(0x8FD0FF));
			Advancements.grant(master, "kunpeng_wings");
		}
		return true;
	}

	/** In a fight: closes in on a far target with the wings. */
	static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		DiscipleData data = entity.data();
		if (!has(data) || !PowerCore.isDisciple(entity) || !PowerCore.ready(data, "kunpeng", PowerCore.now(entity))) {
			return false;
		}
		double distance = entity.distanceTo(target);
		return distance >= 7.0D && distance <= (full(data) ? DASH_FULL : DASH) + 4.0D && dash(entity, target) == null;
	}

	/** The dash towards {@code target} (or the master's order); null when it flew, else a reason key. */
	static @Nullable String dash(DiscipleEntity entity, @Nullable LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (!has(data)) {
			return "why.kunpeng_none";
		}
		if (master == null || !(entity.level() instanceof ServerLevel level)) {
			return "why.busy";
		}
		long now = level.getGameTime();
		if (!PowerCore.ready(data, "kunpeng", now)) {
			return "why.cooldown";
		}
		if (target == null) {
			target = entity.getTarget();
			if (target == null || !DiscipleTechniques.mayHit(entity, master, target)) {
				java.util.List<LivingEntity> around = PowerCore.hostiles(entity, master, entity.position(), DASH_FULL);
				target = around.isEmpty() ? null : around.get(around.size() - 1);
			}
		}
		if (target == null) {
			return "why.no_target";
		}
		boolean full = full(data);
		double reach = full ? DASH_FULL : DASH;
		Vec3 from = entity.position();
		Vec3 spot = dashSpot(entity, level, target.position(), reach, 1.5D);
		if (spot == null) {
			return "why.no_path";
		}
		moveTo(entity, spot, target.getBoundingBox().getCenter());
		PowerCore.setCooldown(data, "kunpeng", now + COOLDOWN);
		// The wind crescent: every monster close to the path.
		double amount = DiscipleTechniques.power(data) * (full ? CRESCENT_U_FULL : CRESCENT_U);
		Vec3 a = from.add(0.0D, 1.0D, 0.0D);
		Vec3 b = spot.add(0.0D, 1.0D, 0.0D);
		for (LivingEntity victim : PowerCore.hostiles(entity, master, a.add(b).scale(0.5D), a.distanceTo(b) * 0.5D + CRESCENT_WIDTH + 1.0D)) {
			AABB box = victim.getBoundingBox().inflate(CRESCENT_WIDTH);
			if (box.contains(a) || box.clip(a, b).isPresent()) {
				PowerCore.hurt(entity, master, victim, amount, PowerCore.HIT_ULTIMATE);
			}
		}
		PowerCore.fx(level, from, spot, PowerIds.FX_KUNPENG, full ? 1 : 0, 0.0F);
		// the trail and the wings: the clients draw the move over DASH_TICKS (vanilla lerps the body) with the wings spread
		PowerCore.fx(level, from, spot, PowerIds.FX_KUNPENG_DASH, DASH_TICKS, full ? 1.0F : 0.0F);
		DisciplePowers.live(entity).kunpengUntil = now + DASH_TICKS + 6L;
		PowerCore.sound(level, from, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.2F);
		PowerCore.sound(level, spot, SoundEvents.BREEZE_SHOOT, 1.0F, 0.7F);
		return null;
	}

	// ================================================================== moving (read-only checks)

	/** A safe spot up to {@code reach} blocks towards {@code towards}, {@code stopShort} before it, reached through open space. */
	static @Nullable Vec3 dashSpot(DiscipleEntity entity, ServerLevel level, Vec3 towards, double reach, double stopShort) {
		Vec3 from = entity.position();
		Vec3 flat = new Vec3(towards.x - from.x, 0.0D, towards.z - from.z);
		double distance = flat.length();
		if (distance < 2.0D) {
			return null;
		}
		Vec3 dir = flat.scale(1.0D / distance);
		for (double length = Math.min(reach, distance - stopShort); length >= 2.0D; length -= 1.5D) {
			Vec3 candidate = from.add(dir.scale(length));
			if (!level.isLoaded(net.minecraft.core.BlockPos.containing(candidate))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, entity, candidate);
			if (spot != null && Math.abs(spot.y - from.y) <= 3.0D
					&& clear(entity, level, entity.getEyePosition(), spot.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D))) {
				return spot;
			}
		}
		return null;
	}

	private static boolean clear(DiscipleEntity entity, ServerLevel level, Vec3 a, Vec3 b) {
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
	}

	/** Puts the disciple on a checked spot facing {@code look} (no portal travel afterwards). */
	static void moveTo(DiscipleEntity entity, Vec3 spot, Vec3 look) {
		Vec3 delta = look.subtract(spot.add(0.0D, entity.getEyeHeight(), 0.0D));
		float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
		entity.getNavigation().stop();
		entity.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.resetFallDistance();
		entity.setPortalCooldown();
		entity.needsSync = true;
	}

	private KunpengWings() {
	}
}

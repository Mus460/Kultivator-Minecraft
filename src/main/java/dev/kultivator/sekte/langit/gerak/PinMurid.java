package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-driven motion of a disciple (module 37M: the flying sword patrol and the First Climb up the Tangga Awan), the
 * pattern of DESIGN 36's stair climb: while pinned the body has no physics, no gravity and no AI step, and the module
 * sets its position every tick along a path computed once. The body's own flags are kept in a crash-safe mark
 * ({@code DiscipleData.extra().sekte_langit}), so a game that stops mid-flight gives the disciple its gravity and AI
 * back when it loads again, standing on the island (never in the air). No block changes.
 */
final class PinMurid {

	static final String SECTION = "sekte_langit";
	private static final String K_PIN = "pin";
	private static final String K_GRAVITY = "g";
	private static final String K_AI = "ai";

	/** Holds the body for scripted motion (idempotent). */
	static void pin(DiscipleEntity disciple) {
		CompoundTag mark = disciple.data().extraSection(SECTION);
		if (!mark.getBooleanOr(K_PIN, false)) {
			mark.putBoolean(K_PIN, true);
			mark.putBoolean(K_GRAVITY, disciple.isNoGravity());
			mark.putBoolean(K_AI, disciple.isNoAi());
		}
		disciple.stopRiding();
		disciple.getNavigation().stop();
		disciple.setTarget(null);
		disciple.noPhysics = true;
		disciple.setNoGravity(true);
		disciple.setNoAi(true);
		disciple.setDeltaMovement(Vec3.ZERO);
	}

	/** Gives the body its own flags back (idempotent). */
	static void unpin(DiscipleEntity disciple) {
		CompoundTag mark = disciple.data().extra().getCompound(SECTION).orElse(null);
		boolean gravity = false;
		boolean ai = false;
		if (mark != null && mark.getBooleanOr(K_PIN, false)) {
			gravity = mark.getBooleanOr(K_GRAVITY, false);
			ai = mark.getBooleanOr(K_AI, false);
			mark.remove(K_PIN);
			mark.remove(K_GRAVITY);
			mark.remove(K_AI);
		}
		disciple.noPhysics = false;
		disciple.setNoGravity(gravity);
		disciple.setNoAi(ai);
		disciple.setDeltaMovement(Vec3.ZERO);
		disciple.resetFallDistance();
		if (disciple.activity() == dev.kultivator.sekte.langit.api.LangitIds.ACT_TERBANG_PEDANG) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	static boolean pinned(DiscipleEntity disciple) {
		CompoundTag mark = disciple.data().extra().getCompound(SECTION).orElse(null);
		return mark != null && mark.getBooleanOr(K_PIN, false);
	}

	/** One scripted step: the body stands at {@code to}, facing {@code yaw}. */
	static void move(DiscipleEntity disciple, Vec3 to, float yaw) {
		disciple.setPos(to.x, to.y, to.z);
		disciple.setDeltaMovement(Vec3.ZERO);
		disciple.setYRot(yaw);
		disciple.setYHeadRot(yaw);
		disciple.setYBodyRot(yaw);
		disciple.resetFallDistance();
	}

	/**
	 * Puts the body down safely: on a safe spot near {@code preferred} (else the owner's sky {@code arrival}, else where
	 * it is), then unpins it. Never leaves it in the air.
	 */
	static void land(ServerLevel level, DiscipleEntity disciple, @Nullable Vec3 preferred) {
		Vec3 spot = preferred == null ? null : Targeting.safeSpot(level, disciple, preferred);
		if (spot == null) {
			UUID owner = disciple.ownerId();
			BlockPos arrival = owner == null ? null : LangitGerak.anchor(owner, "arrival");
			if (arrival != null && level.isLoaded(arrival)) {
				spot = Targeting.safeSpot(level, disciple, Vec3.atBottomCenterOf(arrival));
			}
		}
		if (spot == null) {
			spot = Targeting.safeSpot(level, disciple, disciple.position());
		}
		unpin(disciple);
		if (spot != null) {
			disciple.teleportTo(spot.x, spot.y, spot.z);
		}
		disciple.resetFallDistance();
	}

	/**
	 * A disciple loaded with the mark but moved by nobody (the game stopped mid-flight or mid-climb): its own flags come
	 * back and it stands on its sky island's {@code arrival} (or the nearest safe spot).
	 */
	static void recover(ServerLevel level, DiscipleEntity disciple) {
		if (!pinned(disciple)) {
			return;
		}
		UUID owner = disciple.ownerId();
		SkyHallView hall = owner == null ? null : LangitGerak.hallIn(level, owner);
		Vec3 home = null;
		if (hall != null && hall.inVolume(disciple.position())) {
			BlockPos arrival = LangitGerak.anchor(owner, "arrival");
			home = arrival == null ? null : Vec3.atBottomCenterOf(arrival);
		}
		land(level, disciple, home);
	}

	private PinMurid() {
	}
}

package dev.kultivator.client.epic.camera;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.VisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The hard gates of every shot (DESIGN 23.7.4). A shot does not start, or aborts at once with an instant snap, on:
 * LETHAL_NEXT or health under the floor (45 % Singkat, 30 % Penuh; the result exemption lets one STATIC failure shot run
 * with RESULT_SAFE), damage the scene did not cause (a hurt within ±2 ticks of a shown strike is the scene's), an open
 * strike telegraph, falling more than 2 b, fluid, lava or fire, air below 60 %, riding, gliding, a flying sword, sleeping,
 * spectating, an open screen, death, an unfocused window. The client re-checks hostiles every 10 ticks in a 16-b box
 * (Penuh: an enemy or a mob targeting the player within 4 b; Singkat: a mob targeting the player within 16 b).
 */
public final class ShotGuard {

	public static final float FLOOR_SINGKAT = 0.45F;
	public static final float FLOOR_PENUH = 0.30F;

	/**
	 * Phase 35 (DESIGN 35.4.8): entity ids the hostile scan skips (the duel module names the pair of a live sword duel, so a
	 * held rogue opponent never aborts the intro or the finisher). Client thread only; never null.
	 */
	public static java.util.function.IntPredicate ignoreHostile = id -> false;

	private static int lastHurtTime;
	private static long lastStrikeTick = -100L;
	private static long ticks;
	private static boolean hostileNear;
	private static String reason = "";

	/** Marks the tick a scene strike was shown (its own damage within ±2 ticks is expected). */
	public static void strikeShown() {
		lastStrikeTick = ticks;
	}

	/** Why the last check failed (for {@code /sinematik info}). */
	public static String reason() {
		return reason;
	}

	/** Once per client tick: tracks hurts and, every 10 ticks while needed, hostiles. Returns false on a foreign hurt. */
	static boolean tick(boolean scan, boolean penuh) {
		ticks++;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return false;
		}
		boolean foreignHurt = false;
		if (player.hurtTime > lastHurtTime && Math.abs(ticks - lastStrikeTick) > 2) {
			foreignHurt = true;
		}
		lastHurtTime = player.hurtTime;
		if (scan && ticks % 10 == 0) {
			hostileNear = hostiles(player, penuh);
		}
		return !foreignHurt;
	}

	/** True when the scan saw a hostile that blocks shots. */
	static boolean hostileNear() {
		return hostileNear;
	}

	/** Runs the scan now (before a shot starts). */
	static void scanNow(boolean penuh) {
		LocalPlayer player = Minecraft.getInstance().player;
		hostileNear = player != null && hostiles(player, penuh);
	}

	private static boolean hostiles(LocalPlayer player, boolean penuh) {
		List<Entity> near = player.level().getEntities(player, new AABB(player.blockPosition()).inflate(16.0D),
				entity -> entity instanceof Mob || entity instanceof Enemy);
		for (Entity entity : near) {
			if (!entity.isAlive() || ignoreHostile.test(entity.getId())) {
				continue;
			}
			double distance = entity.distanceTo(player);
			boolean targeting = entity instanceof Mob mob && mob.getTarget() == player;
			if (penuh) {
				if (distance <= 4.0D && (entity instanceof Enemy || targeting)) {
					return true;
				}
			} else if (targeting && distance <= 16.0D) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The gates that do not depend on the shot: null when a shot may run, otherwise the reason. {@code result} = the
	 * failure result exemption (health floor ignored), {@code lethal} = the cue's LETHAL_NEXT flag.
	 */
	static String blocked(boolean penuh, boolean result, boolean lethal, boolean telegraph) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return reason = "no player";
		}
		if (!minecraft.isWindowActive()) {
			return reason = "window";
		}
		if (minecraft.gui.screen() != null) {
			return reason = "screen";
		}
		if (player.isDeadOrDying() || player.isSpectator() || player.isSleeping()) {
			return reason = "state";
		}
		if (lethal) {
			return reason = "lethal";
		}
		if (telegraph) {
			return reason = "telegraph";
		}
		float floor = penuh ? FLOOR_PENUH : FLOOR_SINGKAT;
		float health = (player.getHealth() + player.getAbsorptionAmount()) / Math.max(1.0F, player.getMaxHealth());
		if (!result && health < floor) {
			return reason = "health";
		}
		if (player.fallDistance > 2.0D || player.isInWater() || player.isInLava() || player.isOnFire() || player.isUnderWater()) {
			return reason = "fall/fluid/fire";
		}
		if (player.getAirSupply() < player.getMaxAirSupply() * 0.6F) {
			return reason = "air";
		}
		if (player.isPassenger() || player.isFallFlying()) {
			return reason = "riding";
		}
		if (ClientCultivation.visual(player).has(VisualState.FLYING_SWORD)) {
			return reason = "sword";
		}
		if (hostileNear) {
			return reason = "hostile";
		}
		return null;
	}

	private ShotGuard() {
	}
}

package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.duel.DuelKeys;
import dev.kultivator.client.epic.camera.CinematicDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The duel camera (DESIGN 35.4.10, contract C35.V4), placed by {@code client/mixin/duel/DuelCameraMixin} right after
 * {@code Camera.alignWithEntity} (the point the 7b camera uses), so the frame and the culling both use it.
 * <ul>
 * <li>{@code BAHU} (a duellist in a live duel): over the right shoulder, eye − view × 3.2 + right × 0.9 + up × 0.35, the
 * camera's yaw turned a quarter of the way toward the opponent (≤ 30°) so both stay in frame, FOV × 1.05, pulled in
 * before any wall ({@code level.clip}). Only the camera moves: the player's rotation and aim (the attack ray from the
 * eye) never change, as with vanilla F5.</li>
 * <li>{@code PENONTON} (an onlooker of a duel within 48 b, or the watcher of a disciples' bout): an orbit round the
 * duellists' midpoint; the mouse turns the orbit, the scroll wheel brings it closer or further (the hotbar does not
 * move meanwhile), it drifts slowly round by itself after 2 s without input (not with "Kurangi gerak"), and any movement
 * key leaves it until the next duel or the next change of the setting. A duellist with PENONTON gets BAHU.</li>
 * </ul>
 * While either runs, the view is switched to third person (so the player's body draws and the first-person hand does
 * not) and switched back afterwards; pressing F5 meanwhile hands the view back to the player. Never during a 7b shot
 * ({@link CinematicDirector#active()}), never in spectator mode, with "Kurangi gerak" BAHU stays (only the drift stops;
 * the shake setting is the shake's own). Lock-on (option, off by default, against NPCs and dummies only, never in PvP):
 * while the parry key or the use key is held in the fight, the player's yaw turns toward the opponent at ≤ 90°/s.
 */
public final class DuelCamera {

	/** One camera pose. */
	public record Pose(Vec3 pos, float yaw, float pitch) {
	}

	private enum Active { NONE, SHOULDER, SPECTATOR }

	private static final float SHOULDER_BACK = 3.2F;
	private static final float SHOULDER_RIGHT = 0.9F;
	private static final float SHOULDER_UP = 0.35F;
	private static final float TOWARD_OPPONENT = 0.25F;
	private static final float MAX_TURN = 30.0F;
	private static final float FOV = 1.05F;
	private static final float LOCK_STEP = 4.5F;
	private static final float ZOOM_MIN = 4.0F;
	private static final float ZOOM_MAX = 18.0F;

	private static Active active = Active.NONE;
	private static int sessionId = -1;
	private static int opponentId = -1;
	/** 0..1 ease of entering (per tick). */
	private static float blend;
	private static float blendOld;
	private static @Nullable CameraType saved;
	private static boolean managing;
	private static int suspendedSession = -1;
	private static @Nullable DuelFxSettings.Camera lastSetting;
	private static float zoom = 9.0F;
	private static int lastSlot = -1;
	private static float drift;
	private static float driftOld;
	private static float lastYaw;
	private static int idleTicks;
	private static boolean initialised;

	/** Registers the scroll-to-zoom check (from {@link DuelFx#init}). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientTickEvents.START_CLIENT_TICK.register(DuelCamera::beforeTick);
	}

	// ================================================================ ticks

	/**
	 * Before the client tick (so before the carried item is sent): while the onlooker orbit runs, a change of the hotbar
	 * slot (the wheel) becomes zoom and the slot is put back.
	 */
	private static void beforeTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || active != Active.SPECTATOR) {
			lastSlot = -1;
			return;
		}
		int slot = player.getInventory().getSelectedSlot();
		if (lastSlot >= 0 && slot != lastSlot && minecraft.gui.screen() == null) {
			int delta = slot - lastSlot;
			if (delta > 4) {
				delta -= 9;
			} else if (delta < -4) {
				delta += 9;
			}
			zoom = Mth.clamp(zoom + delta * 1.5F, ZOOM_MIN, ZOOM_MAX);
			player.getInventory().setSelectedSlot(lastSlot);
			return;
		}
		lastSlot = slot;
	}

	/** Once per client tick (from {@link DuelFx}): picks the mode, manages the view, the drift and the lock-on. */
	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Active next = Active.NONE;
		DuelFx.SessionView chosen = null;
		DuelFxSettings.Camera setting = DuelFxSettings.camera();
		if (setting != lastSetting) {
			lastSetting = setting;
			suspendedSession = -1;
		}
		if (player != null && minecraft.level != null && player.isAlive() && !player.isSpectator()) {
			int self = player.getId();
			DuelFx.SessionView own = null;
			DuelFx.SessionView watched = null;
			double best = 48.0D * 48.0D;
			for (DuelFx.SessionView s : DuelFx.sessions()) {
				if (!s.live()) {
					continue;
				}
				if (s.involves(self)) {
					own = s;
				} else {
					double d = player.position().distanceToSqr(s.center);
					if (d < best || s.pair == DuelCodes.PAIR_TONTON && d < 64.0D * 64.0D) {
						best = d;
						watched = s;
					}
				}
			}
			if (own != null && setting != DuelFxSettings.Camera.MATI && own.id != suspendedSession) {
				next = Active.SHOULDER;
				chosen = own;
			} else if (own == null && watched != null && setting == DuelFxSettings.Camera.PENONTON && watched.id != suspendedSession) {
				next = Active.SPECTATOR;
				chosen = watched;
			}
		}
		if (next == Active.SPECTATOR && moving(minecraft)) {
			// a movement key leaves the onlooker orbit until the next duel or the next change of the setting
			suspendedSession = chosen.id;
			next = Active.NONE;
			chosen = null;
		}
		if (next != active || chosen != null && chosen.id != sessionId) {
			if (active == Active.NONE) {
				blend = 0.0F;
				blendOld = 0.0F;
			}
			active = next;
			sessionId = chosen != null ? chosen.id : -1;
		}
		opponentId = chosen != null && player != null ? chosen.partnerOf(player.getId()) : -1;
		manageView(minecraft);
		blendOld = blend;
		blend = active == Active.NONE ? 0.0F : Math.min(1.0F, blend + 0.1F);
		if (active == Active.SPECTATOR && player != null) {
			tickDrift(player);
		} else {
			drift = 0.0F;
			driftOld = 0.0F;
		}
		if (player != null && active == Active.SHOULDER && chosen != null) {
			lockOn(minecraft, player, chosen);
		}
	}

	/** Third person while the duel camera runs; the player's own view back afterwards; F5 meanwhile ends the management. */
	private static void manageView(Minecraft minecraft) {
		CameraType current = minecraft.options.getCameraType();
		if (active != Active.NONE) {
			if (!managing) {
				saved = current;
				managing = true;
				if (current != CameraType.THIRD_PERSON_BACK) {
					minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				}
			} else if (current != CameraType.THIRD_PERSON_BACK) {
				// the player pressed F5: their view wins for this duel
				managing = false;
				saved = null;
				suspendedSession = sessionId;
				active = Active.NONE;
				sessionId = -1;
			}
		} else if (managing) {
			managing = false;
			if (saved != null && current == CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(saved);
			}
			saved = null;
		}
	}

	private static boolean moving(Minecraft minecraft) {
		if (minecraft.gui.screen() != null) {
			return false;
		}
		return minecraft.options.keyUp.isDown() || minecraft.options.keyDown.isDown() || minecraft.options.keyLeft.isDown()
				|| minecraft.options.keyRight.isDown();
	}

	private static void tickDrift(LocalPlayer player) {
		driftOld = drift;
		float yaw = player.getYRot();
		if (Math.abs(Mth.wrapDegrees(yaw - lastYaw)) > 0.05F) {
			idleTicks = 0;
		} else {
			idleTicks++;
		}
		lastYaw = yaw;
		if (idleTicks > 40 && !EffectQuality.reduceMotion()) {
			drift += 0.15F;
		}
	}

	/** Lock-on toward an NPC or dummy (never a player), ≤ 4.5° a tick, only while parry or use is held in the fight. */
	private static void lockOn(Minecraft minecraft, LocalPlayer player, DuelFx.SessionView s) {
		if (!DuelFxSettings.lockOn() || s.phase != DuelCodes.PHASE_FIGHT || minecraft.gui.screen() != null) {
			return;
		}
		Entity opponent = DuelFx.entity(s.partnerOf(player.getId()));
		if (opponent == null || opponent instanceof Player || !opponent.isAlive()) {
			return;
		}
		boolean held = DuelKeys.parryDown() || minecraft.options.keyUse.isDown();
		if (!held) {
			return;
		}
		Vec3 d = opponent.position().subtract(player.position());
		if (d.horizontalDistanceSqr() < 1.0E-4D) {
			return;
		}
		float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float diff = Mth.wrapDegrees(want - player.getYRot());
		player.setYRot(player.getYRot() + Mth.clamp(diff, -LOCK_STEP, LOCK_STEP));
	}

	// ================================================================ the pose (render thread, from the mixin)

	/** The duel camera's pose this frame, or null (vanilla / the 7b camera decide). */
	public static @Nullable Pose pose(Camera camera, DeltaTracker deltaTracker) {
		if (active == Active.NONE || CinematicDirector.active()) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || camera.entity() != player || !managing) {
			return null;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float ease = ease(Mth.lerp(partial, blendOld, blend));
		return active == Active.SHOULDER ? shoulder(minecraft, player, partial, ease) : spectator(minecraft, player, partial, ease);
	}

	private static Pose shoulder(Minecraft minecraft, LocalPlayer player, float partial, float ease) {
		Vec3 eye = player.getEyePosition(partial);
		float viewYaw = player.getViewYRot(partial);
		float pitch = player.getViewXRot(partial);
		float yaw = viewYaw;
		Entity opponent = opponentId != -1 ? DuelFx.entity(opponentId) : null;
		if (opponent != null) {
			Vec3 d = opponent.getPosition(partial).subtract(player.getPosition(partial));
			if (d.horizontalDistanceSqr() > 0.01D) {
				float toward = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
				yaw = viewYaw + Mth.clamp(Mth.wrapDegrees(toward - viewYaw) * TOWARD_OPPONENT, -MAX_TURN, MAX_TURN) * ease;
			}
		}
		Vec3 look = Vec3.directionFromRotation(pitch, yaw);
		double rad = Math.toRadians(yaw);
		Vec3 right = new Vec3(-Math.cos(rad), 0.0D, -Math.sin(rad));
		Vec3 target = eye.subtract(look.scale(SHOULDER_BACK)).add(right.scale(SHOULDER_RIGHT)).add(0.0D, SHOULDER_UP, 0.0D);
		Vec3 pos = clear(minecraft, player, eye, target);
		return new Pose(eye.lerp(pos, ease), yaw, pitch);
	}

	private static Pose spectator(Minecraft minecraft, LocalPlayer player, float partial, float ease) {
		DuelFx.SessionView s = session();
		Vec3 eye = player.getEyePosition(partial);
		float yaw = player.getViewYRot(partial) + Mth.lerp(partial, driftOld, drift);
		float pitch = Mth.clamp(player.getViewXRot(partial), -15.0F, 70.0F);
		if (s == null) {
			return new Pose(eye, player.getViewYRot(partial), player.getViewXRot(partial));
		}
		Entity a = DuelFx.entity(s.aId);
		Entity b = DuelFx.entity(s.bId);
		Vec3 mid = a != null && b != null ? a.getPosition(partial).lerp(b.getPosition(partial), 0.5D) : s.center;
		Vec3 centre = mid.add(0.0D, 1.1D, 0.0D);
		Vec3 look = Vec3.directionFromRotation(pitch, yaw);
		Vec3 target = centre.subtract(look.scale(zoom));
		Vec3 pos = clear(minecraft, player, centre, target);
		return new Pose(eye.lerp(pos, ease), yaw, pitch);
	}

	/** {@code to}, or the free point just short of the first block between {@code from} and it (read only). */
	private static Vec3 clear(Minecraft minecraft, LocalPlayer player, Vec3 from, Vec3 to) {
		if (minecraft.level == null) {
			return to;
		}
		BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return to;
		}
		Vec3 back = from.subtract(hit.getLocation());
		double length = back.length();
		return length < 0.3D ? from : hit.getLocation().add(back.scale(0.25D / length));
	}

	private static @Nullable DuelFx.SessionView session() {
		for (DuelFx.SessionView s : DuelFx.sessions()) {
			if (s.id == sessionId) {
				return s;
			}
		}
		return null;
	}

	private static float ease(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** The FOV factor of the shoulder camera (1 otherwise). */
	public static float fovScale() {
		if (active != Active.SHOULDER || !managing || CinematicDirector.active()) {
			return 1.0F;
		}
		return 1.0F + (FOV - 1.0F) * ease(blend);
	}

	/** True while the duel camera drives the view. */
	public static boolean running() {
		return active != Active.NONE && managing;
	}

	/** World change, disconnect: the view goes back at once. */
	static void clear() {
		active = Active.NONE;
		sessionId = -1;
		opponentId = -1;
		blend = 0.0F;
		blendOld = 0.0F;
		drift = 0.0F;
		driftOld = 0.0F;
		suspendedSession = -1;
		lastSlot = -1;
		manageView(Minecraft.getInstance());
	}

	private DuelCamera() {
	}
}

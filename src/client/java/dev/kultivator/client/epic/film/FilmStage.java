package dev.kultivator.client.epic.film;

import dev.kultivator.client.epic.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The stage of one running film (DESIGN 30.2.5): where its spaces are. The subject is the caster (the scene's subject,
 * interpolated per frame); its forward is the body yaw at the act start; the aim space faces from the caster to the aim
 * as it stood when the film was armed; props come from the drivers ({@link FilmDirector#track}). Client thread only.
 */
public final class FilmStage {

	final Scene scene;
	final int casterId;
	Vec3 aim;
	/** The aim space's forward (horizontal, unit). */
	Vec3 aimForward;
	/** The subject's forward at the act start (horizontal, unit). */
	Vec3 forward;
	float yaw0;
	final Map<String, FilmDirector.Track> tracks;
	/** The film's aim-space fallbacks of untracked props. */
	Map<String, double[]> fallbacks = Map.of();

	FilmStage(Scene scene, int casterId, Vec3 aim, Vec3 origin, float yaw, Map<String, FilmDirector.Track> tracks) {
		this.scene = scene;
		this.casterId = casterId;
		this.aim = aim;
		this.yaw0 = yaw;
		this.forward = forwardOf(yaw);
		Vec3 flat = new Vec3(aim.x - origin.x, 0.0D, aim.z - origin.z);
		this.aimForward = flat.lengthSqr() < 1.0D ? forward : flat.normalize();
		this.tracks = tracks != null ? tracks : new HashMap<>();
	}

	/** Re-reads the subject's facing at an act start. */
	void actStart() {
		yaw0 = scene.yaw();
		forward = forwardOf(yaw0);
	}

	static Vec3 forwardOf(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
	}

	/** The subject's interpolated feet. */
	Vec3 feet(float partial) {
		return scene.feet(partial);
	}

	/** The subject's eye (feet + 0.9 × height). */
	Vec3 eye(float partial) {
		return scene.feet(partial).add(0.0D, scene.height() * 0.9D, 0.0D);
	}

	/** The subject's look direction (for a subject POV), or its forward. */
	Vec3 look(float partial) {
		Entity entity = scene.subject();
		return entity != null ? entity.getViewVector(partial) : forward;
	}

	/** A live tracked prop, or null. */
	@Nullable FilmDirector.Track track(@Nullable String key) {
		if (key == null) {
			return null;
		}
		FilmDirector.Track track = tracks.get(key);
		return track != null && track.alive() ? track : null;
	}

	/** Resolves a point given in {@code space} to world coordinates at this frame. */
	Vec3 resolve(FilmSpace space, @Nullable String prop, double x, double y, double z, float partial) {
		switch (space) {
			case W -> {
				return new Vec3(x, y, z);
			}
			case S -> {
				return place(feet(partial), forward, x, y, z);
			}
			case P -> {
				FilmDirector.Track track = track(prop);
				if (track != null) {
					return place(track.pos(partial), forwardOf(track.yaw(partial)), x, y, z);
				}
				double[] at = prop == null ? null : fallbacks.get(prop);
				if (at != null) {
					return place(aim, aimForward, at[0] + x, at[1] + y, at[2] + z);
				}
				return place(aim, aimForward, x, y, z);
			}
			case K -> {
				// a direction (x = yaw from forward, y = pitch up) far from the subject's eye
				double yaw = Math.toRadians(yaw0 + x);
				double pitch = Math.toRadians(y);
				Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
				return eye(partial).add(dir.scale(Math.max(16.0D, z)));
			}
			default -> {
				return place(aim, aimForward, x, y, z);
			}
		}
	}

	/** origin + right × x + up × y + forward × z (right = forward turned 90° clockwise seen from above). */
	static Vec3 place(Vec3 origin, Vec3 forward, double x, double y, double z) {
		double rx = -forward.z;
		double rz = forward.x;
		return new Vec3(origin.x + rx * x + forward.x * z, origin.y + y, origin.z + rz * x + forward.z * z);
	}

	/** The render distance reach of the film camera: ≤ 64 b and ≤ render distance × 16 − 24. */
	static double reach() {
		int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
		return Math.max(16.0D, Math.min(64.0D, chunks * 16.0D - 24.0D));
	}
}

package dev.kultivator.client.dampak.cinema;

import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Perjalanan Langit (Phase 10, DESIGN 28.3): the long camera journey of a FULL row, told within the 7b speed caps. Three
 * short world-anchored segments ({@link Shots#skyRide}) joined by masked cuts under the {@link CloudWipe}:
 * <ol>
 * <li>R1 "Naik": from over the shoulder up beside the mark pillar, looking up it; at 0.30 the clouds close in;</li>
 * <li>R2 "Puncak": above the aim in a star-dark sky with the vista ({@link Vistas}) and the technique's object charging;</li>
 * <li>R3 "Turun": behind and above the strike, riding it down with speed lines and an FOV punch, holding up to 20 ticks
 * for a late climax; the climax cuts back to the eye under the impact frame.</li>
 * </ol>
 * Gates (28.3.4): Kamera Penuh, "Perjalanan langit" (Pertama: the first cast of each spec per session; Selalu; Mati),
 * the local player's own cast, calm and safe (no enemy within 24 b, health ≥ 50 %, the 23.7.4 gates through
 * {@link CinematicDirector#shoot}), no server load flag, the ultimate camera gap. A failure falls back to the Singkat
 * crane (2 s looking up at the falling strike, ending on the climax), then to nothing. Every 7b abort applies (a key
 * ends it, Backspace skips, a hurt snaps back); the ride never delays anything on the server. Client thread only.
 */
public final class SkyRide {

	private static final Set<String> RIDDEN = new HashSet<>();
	private static @Nullable Ride current;

	private SkyRide() {
	}

	/** One planned or running ride of the local player's cast. */
	private static final class Ride {
		final Scene scene;
		final TechniqueSpec spec;
		final Dampak d;
		final long key;
		final Vec3 aim;
		final int rgb;
		final int casterId;
		final boolean test;
		boolean full;
		boolean running;
		boolean revealOnClimax;
		boolean cancelled;
		boolean climaxed;

		Ride(Scene scene, TechniqueSpec spec, Dampak d, long key, Vec3 aim, int rgb, int casterId, boolean test) {
			this.scene = scene;
			this.spec = spec;
			this.d = d;
			this.key = key;
			this.aim = aim;
			this.rgb = rgb;
			this.casterId = casterId;
			this.test = test;
		}
	}

	// ================================================================ the TechFx stream

	/** One event of the local player's own Dampak cast (never a disciple's, never an echo). */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Dampak d, Phase phase, Vec3 aim) {
		if (d.ride() == Dampak.Ride.FILM) {
			// Phase 10b (29.4): a Titah decree plays Film Pusaka instead (its own acts, timing and settings)
			dev.kultivator.client.pusakalangit.cinema.PusakaFilm.onTechFx(p, spec, d, phase, aim);
			return;
		}
		long key = ((long) p.caster() << 32) | (spec.netId() & 0xFFFFFFFFL);
		Ride ride = current;
		if (phase == Phase.ABORT || phase == Phase.END) {
			if (ride != null && ride.key == key) {
				abandon(ride);
			}
			return;
		}
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) && phase != Phase.PULSE && phase != Phase.MASS_HIT;
		if (climax) {
			if (ride != null && ride.key == key) {
				onClimax(ride);
			}
			return;
		}
		if (phase != Phase.CAST || d.ride() == Dampak.Ride.NONE) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Scene scene = EpicDirector.dampakScene(p.caster(), true, minecraft.player.position(), p.seed(), Math.max(80, d.climaxAt() + 60));
		if (scene == null || !scene.own) {
			return;
		}
		if (ride != null) {
			abandon(ride);
		}
		Ride fresh = new Ride(scene, spec, d, key, TargetMark.ground(aim), TargetMark.colour(spec, d, p), p.caster(), false);
		current = fresh;
		plan(fresh, p.has(Payloads.TechFx.FLAG_LOW_LOAD));
	}

	/** {@code /sinematik dampak naik <id>}: the ride of a spec alone, its climax {@code rideTicks} from now. Null = started. */
	public static @Nullable String test(TechniqueSpec spec, Dampak d, Vec3 aim) {
		if (CinematicDirector.mode() != EpicSettings.Camera.PENUH) {
			return "camera";
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return "player";
		}
		Scene scene = EpicDirector.testScene();
		if (scene == null) {
			return "scene";
		}
		if (current != null) {
			abandon(current);
		}
		int ticks = Math.max(40, Math.min(Dampak.MAX_RIDE_TICKS, d.rideTicks() > 0 ? d.rideTicks() : 60));
		Ride ride = new Ride(scene, spec, d, -1L, TargetMark.ground(aim), TargetMark.colour(spec, d, null), minecraft.player.getId(), true);
		ride.full = true;
		current = ride;
		startFull(ride, ticks);
		scene.at(ticks, () -> {
			if (current == ride && !ride.cancelled) {
				dev.kultivator.client.dampak.DampakClient.testClimax(spec, d, ride.aim, true);
				onClimax(ride);
			}
		});
		return null;
	}

	/** Ends every ride (skip, stop, dimension change, disconnect). */
	public static void clear() {
		if (current != null) {
			current.cancelled = true;
		}
		current = null;
		dev.kultivator.client.pusakalangit.cinema.PusakaFilm.clear();
		CloudWipe.clear();
	}

	// ================================================================ planning

	private static void plan(Ride ride, boolean lowLoad) {
		Dampak d = ride.d;
		int climaxAt = d.climaxAt();
		EpicSettings.SkyRide setting = EpicSettings.skyRide();
		boolean allowed = d.ride() == Dampak.Ride.FULL && !lowLoad && CinematicDirector.mode() == EpicSettings.Camera.PENUH
				&& (setting == EpicSettings.SkyRide.SELALU || setting == EpicSettings.SkyRide.PERTAMA && !RIDDEN.contains(ride.spec.id()));
		if (allowed) {
			int length = Math.max(40, Math.min(Dampak.MAX_RIDE_TICKS, d.rideTicks() > 0 ? d.rideTicks() : Dampak.MAX_RIDE_TICKS));
			if (climaxAt < length) {
				length = climaxAt >= 40 ? climaxAt : -1;
			}
			if (length > 0) {
				int rideLength = length;
				ride.full = true;
				ride.scene.at(Math.max(0, climaxAt - length), () -> {
					if (alive(ride)) {
						startFull(ride, rideLength);
					}
				});
				return;
			}
		}
		planShort(ride, climaxAt, 0);
	}

	/** The Singkat crane: 2 s looking up at the falling strike, ending on the climax (a reveal after it when player-timed). */
	private static void planShort(Ride ride, int climaxAt, int elapsed) {
		ride.full = false;
		if (CinematicDirector.mode() == EpicSettings.Camera.MATI) {
			current = null;
			return;
		}
		if (climaxAt - elapsed >= 40) {
			ride.scene.at(climaxAt - elapsed - 40, () -> {
				if (alive(ride)) {
					crane(ride, Shots.craneLook(3.0F, 1.6F, 6.0F, 9.0F, 2.0F).lookAt(ride.aim.add(0.0D, 10.0D, 0.0D)));
				}
			});
		} else {
			ride.revealOnClimax = true;
		}
	}

	private static void crane(Ride ride, Shots.Shot shot) {
		EpicDirector.refreshFlags(ride.scene);
		CinematicDirector.shoot(ride.scene, shot, CinematicDirector.KIND_ULTIMATE, ride.scene.priority, true);
	}

	// ================================================================ the full ride

	private static void startFull(Ride ride, int length) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Scene scene = ride.scene;
		if (player == null) {
			abandon(ride);
			return;
		}
		EpicDirector.refreshFlags(scene);
		float health = (player.getHealth() + player.getAbsorptionAmount()) / Math.max(1.0F, player.getMaxHealth());
		boolean safe = scene.has(EpicIds.FLAG_SAFE) && scene.has(EpicIds.FLAG_RESULT_SAFE) && health >= 0.5F
				&& CinematicDirector.mode() == EpicSettings.Camera.PENUH;
		if (!safe) {
			// a failed gate falls back to the Singkat crane, then to nothing
			planShort(ride, length, 0);
			return;
		}
		Vec3 feet = player.position();
		Vec3 eye = player.getEyePosition();
		Vec3 aim = ride.aim;
		Vec3 flat = new Vec3(aim.x - feet.x, 0.0D, aim.z - feet.z);
		double distance = flat.length();
		Vec3 h;
		if (distance < 1.0D) {
			Vec3 look = player.getViewVector(1.0F);
			h = new Vec3(look.x, 0.0D, look.z);
			h = h.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : h.normalize();
		} else {
			h = flat.scale(1.0D / distance);
		}
		Vec3 back = h;
		Vec3 side = new Vec3(-h.z, 0.0D, h.x);
		Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
		int r1 = Math.max(10, Math.round(length * 0.375F));
		int r2 = Math.max(10, Math.round(length * 0.25F));
		int r3 = Math.max(10, length - r1 - r2);

		// R1 Naik: from over the shoulder up beside the pillar, looking up it
		Vec3 r1From = eye.subtract(h.scale(1.6D)).add(side.scale(0.6D)).add(0.0D, 0.3D, 0.0D);
		Vec3 r1To = feet.add(h.scale(Math.min(10.0D, distance * 0.4D))).add(side.scale(4.0D)).add(0.0D, 14.0D, 0.0D);
		Shots.Shot first = Shots.skyRide(r1From, r1To, aim.add(0.0D, 3.0D, 0.0D), aim.add(0.0D, 40.0D, 0.0D), r1);
		if (!CinematicDirector.shoot(scene, first, CinematicDirector.KIND_ULTIMATE, scene.priority, true)) {
			planShort(ride, length, 0);
			return;
		}
		ride.running = true;
		RIDDEN.add(ride.spec.id());
		TargetMark.ridePillar(scene, aim, ride.rgb, length + 10);

		// the clouds close in at 0.30 (we rise into them) and hold until the cut; the sky turns star-dark behind them
		int wipe1 = Math.max(2, Math.round(length * 0.30F));
		scene.at(wipe1 - 2, () -> {
			if (alive(ride)) {
				SkyDirector.grade(SkyDirector.Grade.SK_STARS, 0.9F, 0.65F, 20, scene.priority, scene.pos, true, 1.0F, scene.pos.y);
			}
		});
		scene.at(wipe1, () -> {
			if (alive(ride)) {
				CloudWipe.start(4, Math.max(0, r1 - wipe1 - 4) + 2, 6, 0xF4F6FF);
			}
		});

		// R2 Puncak: above the aim, a slow 15° turn, looking up into the vista
		Vec3 v0 = aim.add(side.scale(6.0D)).add(0.0D, 22.0D, 0.0D);
		double turn = Math.toRadians(15.0D);
		Vec3 turned = side.scale(Math.cos(turn) * 6.0D).add(h.scale(Math.sin(turn) * 6.0D));
		Vec3 v1 = aim.add(turned).add(0.0D, 24.0D, 0.0D);
		Vec3 lookUp55 = h.scale(Math.cos(Math.toRadians(55.0D))).add(up.scale(Math.sin(Math.toRadians(55.0D))));
		Vec3 lookUp40 = h.scale(Math.cos(Math.toRadians(40.0D))).add(up.scale(Math.sin(Math.toRadians(40.0D))));
		Vec3 vistaAt = v0.add(h.scale(Math.cos(Math.toRadians(47.0D)) * 110.0D)).add(0.0D, Math.sin(Math.toRadians(47.0D)) * 110.0D, 0.0D);
		Dampak d = ride.d;
		scene.at(Math.max(0, r1 - 2), () -> {
			if (alive(ride)) {
				Vistas.spawn(scene, d.vista(), ride.spec, vistaAt, v0, r2 + 10, ride.rgb, d.monument(), d.monumentScale(), ride.casterId);
			}
		});
		scene.at(r1, () -> {
			if (!alive(ride) || !CinematicDirector.ownedBy(scene)) {
				abandon(ride);
				return;
			}
			EpicDirector.refreshFlags(scene);
			Shots.Shot second = Shots.skyRide(v0, v1, v0.add(lookUp55.scale(50.0D)), v1.add(lookUp40.scale(50.0D)), r2).cut();
			if (!CinematicDirector.shoot(scene, second, CinematicDirector.KIND_ULTIMATE, scene.priority, true)) {
				abandon(ride);
			}
		});

		// the second wipe hides the cut down to the strike (≥ 500 ms after the first: one luminance event each)
		scene.at(r1 + r2 - 4, () -> {
			if (alive(ride) && CinematicDirector.ownedBy(scene)) {
				CloudWipe.start(4, 2, 6, 0xE8ECFF);
			}
		});

		// R3 Turun: behind and above the falling strike, looking down, descending ≤ 11 b; holds for a late climax
		boolean near = distance <= 40.0D;
		scene.at(r1 + r2, () -> {
			if (!alive(ride) || !CinematicDirector.ownedBy(scene)) {
				abandon(ride);
				return;
			}
			scene.at(0, () -> SkyDirector.release(20, scene.priority));
			if (!near) {
				// the aim is too far to ride down: the ride ends after R2 with the normal blend
				CinematicDirector.skip();
				CloudWipe.release(4);
				current = null;
				return;
			}
			EpicDirector.refreshFlags(scene);
			Vec3 from = aim.subtract(back.scale(6.0D)).add(0.0D, 20.0D, 0.0D);
			Vec3 to = aim.subtract(back.scale(4.0D)).add(0.0D, 9.0D, 0.0D);
			Shots.Shot third = Shots.skyRide(from, to, aim, aim, r3).cut().hold(20);
			if (!CinematicDirector.shoot(scene, third, CinematicDirector.KIND_ULTIMATE, scene.priority, true)) {
				abandon(ride);
				return;
			}
			ScreenFlash.speedLines(ride.rgb, Math.min(20, r3));
			CinematicDirector.snap(-8.0F, 4, r3);
		});
	}

	/** The climax of the ridden cast: the cut back to the eye under the impact frame, or the reveal crane. */
	private static void onClimax(Ride ride) {
		ride.climaxed = true;
		if (ride.full && ride.running) {
			if (CinematicDirector.ownedBy(ride.scene)) {
				CinematicDirector.cutToEye();
			}
			CloudWipe.release(3);
		} else if (ride.revealOnClimax && !ride.cancelled) {
			// a player-timed climax: a crane reveal after it
			crane(ride, Shots.craneLook(2.5F, 1.8F, 7.0F, 12.0F, 2.0F).lookAt(ride.aim.add(0.0D, 2.0D, 0.0D)));
		}
		if (current == ride) {
			current = null;
		}
	}

	private static boolean alive(Ride ride) {
		return current == ride && !ride.cancelled && !ride.climaxed;
	}

	private static void abandon(Ride ride) {
		ride.cancelled = true;
		if (ride.running && CinematicDirector.ownedBy(ride.scene)) {
			CinematicDirector.skip();
		}
		if (ride.running) {
			CloudWipe.release(4);
		}
		if (current == ride) {
			current = null;
		}
	}
}

package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.film.FilmLens;
import dev.kultivator.client.epic.lens.LensLayers;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The intro of a formal duel (DESIGN 35.4.8), started by the INTRO phase. With "Intro duel" on and the camera allowed
 * (Kamera sinematik Penuh / Singkat, the scene CAMERA_OK and SAFE, every hard gate of {@code ShotGuard}), the local
 * duellists and onlookers within 32 b get the shots: a wide frame from the side of the duel line (0-30), over A's
 * shoulder at B (30-55), over B's shoulder at A (55-80), a two-shot pushing in (80-100), then the 10-tick ease back;
 * Singkat keeps only the wide frame and the two-shot. Every shot is world-anchored and validated by the 7b speed caps, is
 * skipped by Backspace or any movement key, and aborts at once on danger. Without the camera: the title card, falling
 * leaves of the biome (cherry petals in a cherry grove, snow in a cold biome, the foliage colour elsewhere), wind
 * streaks crossing the ring and the paper talismans (module U plays the taiko, the wind and the gong). At tick 40 the
 * card "Duel Pedang" names both duellists with their realm and title; at 92 the drawn blades glint at their tips. A short
 * intro (40 ticks, no film) is the card and the leaves. COUNTDOWN stops the leaves and drops the wind to a quarter.
 */
final class DuelIntroFilm {

	private static final int SEAL_RED = 0xC0302A;

	private static final class Run {
		final DuelFx.SessionView session;
		final long start;
		final boolean film;
		@Nullable Scene scene;
		boolean countdown;
		boolean cardShown;
		boolean glinted;
		int shotsDone;
		final RandomSource random;

		Run(DuelFx.SessionView session, long start, boolean film) {
			this.session = session;
			this.start = start;
			this.film = film;
			this.random = RandomSource.create(session.id * 31L + 7L);
		}
	}

	private record Wind(Vec3 from, Vec3 dir, float length, double born, float life) {
	}

	private static @Nullable Run run;
	private static final List<Wind> WIND = new ArrayList<>();

	/** The INTRO phase began ({@code offset} ticks ago, for a late joiner). */
	static void start(DuelFx.SessionView s, int offset) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.player.position().distanceToSqr(s.center) > 64.0D * 64.0D && !s.involves(minecraft.player.getId())) {
			return;
		}
		boolean film = s.has(DuelCodes.SESSION_INTRO_FILM) && DuelFxSettings.intro();
		Run r = new Run(s, DuelFx.ticks() - offset, film);
		r.scene = EpicDirector.duelScene(s.aId, s.bId, s.center, s.id * 7919, 160);
		run = r;
		boolean near = s.involves(minecraft.player.getId()) || minecraft.player.position().distanceToSqr(s.center) <= 32.0D * 32.0D;
		int total = film ? 110 : 40;
		if (near && offset < total - 10) {
			Letterbox.flick(total - offset);
		}
		if (offset > 30) {
			// a late joiner: past the first shots, only the card and the world effects
			r.shotsDone = 4;
		}
	}

	/** COUNTDOWN: the leaves stop, the wind drops to a quarter. */
	static void countdown(DuelFx.SessionView s) {
		if (run != null && run.session == s) {
			run.countdown = true;
		}
	}

	/** FIGHT or later: the intro is over (a running shot eases back by itself). */
	static void stop(DuelFx.SessionView s) {
		if (run != null && run.session == s) {
			run = null;
		}
	}

	static void tick(Minecraft minecraft) {
		if (!WIND.isEmpty()) {
			double now = DuelTime.now();
			WIND.removeIf(w -> now - w.born > w.life);
		}
		Run r = run;
		if (r == null || minecraft.level == null || minecraft.player == null) {
			return;
		}
		long elapsed = DuelFx.ticks() - r.start;
		DuelFx.SessionView s = r.session;
		if (elapsed > (r.film ? 160 : 100) || !s.live()) {
			run = null;
			return;
		}
		boolean near = s.involves(minecraft.player.getId()) || minecraft.player.position().distanceToSqr(s.center) <= 32.0D * 32.0D;
		int cardAt = r.film ? 40 : 2;
		if (!r.cardShown && elapsed >= cardAt && near) {
			r.cardShown = true;
			card(s);
		}
		if (r.film && !r.countdown) {
			shots(minecraft, r, elapsed);
		}
		if (r.film && !r.glinted && elapsed >= 92) {
			r.glinted = true;
			glint(s);
		}
		world(minecraft.level, r, elapsed);
	}

	// ================================================================ the card

	private static void card(DuelFx.SessionView s) {
		Component title = Component.translatable("kultivator.duel.fx.intro.title");
		Component sub = Component.translatable("kultivator.duel.fx.intro.sub", s.aName, s.aRank, s.bName, s.bRank);
		TitleCard.show(TitleCard.full(title, sub, "決", SEAL_RED, 70).safeBeat(true));
	}

	// ================================================================ the shots

	private static void shots(Minecraft minecraft, Run r, long elapsed) {
		Scene scene = r.scene;
		if (scene == null || !scene.own || !DuelFxSettings.intro()) {
			return;
		}
		boolean penuh = CinematicDirector.mode() == EpicSettings.Camera.PENUH;
		if (CinematicDirector.mode() == EpicSettings.Camera.MATI) {
			return;
		}
		DuelFx.SessionView s = r.session;
		Entity a = DuelFx.entity(s.aId);
		Entity b = DuelFx.entity(s.bId);
		Vec3 pa = a != null ? a.position() : s.markA();
		Vec3 pb = b != null ? b.position() : s.markB();
		Vec3 line = new Vec3(pb.x - pa.x, 0.0D, pb.z - pa.z);
		if (line.lengthSqr() < 0.25D) {
			return;
		}
		Vec3 fwd = line.normalize();
		Vec3 mid = pa.lerp(pb, 0.5D);
		if (r.shotsDone == 0 && elapsed >= 0) {
			r.shotsDone = 1;
			// the wide frame from the side of the duel line nearer the viewer
			Vec3 perp = new Vec3(-fwd.z, 0.0D, fwd.x);
			if (minecraft.player.position().subtract(mid).dot(perp) < 0.0D) {
				perp = perp.scale(-1.0D);
			}
			shoot(scene, s, sideShot(scene, mid, perp, 9.0F, 2.2F, 1.5F).lookAt(mid.add(0.0D, 1.2D, 0.0D)));
		} else if (r.shotsDone == 1 && elapsed >= 30) {
			r.shotsDone = 2;
			if (penuh) {
				shoot(scene, s, overShoulder(pa, fwd, a, pb, b, 1.25F));
			}
		} else if (r.shotsDone == 2 && elapsed >= 55) {
			r.shotsDone = 3;
			if (penuh) {
				shoot(scene, s, overShoulder(pb, fwd.scale(-1.0D), b, pa, a, 1.25F));
			}
		} else if (r.shotsDone == 3 && elapsed >= 80) {
			r.shotsDone = 4;
			shoot(scene, s, Shots.twoShot(pb, 6.0F, 4.5F, 1.2F, 1.0F));
		}
	}

	/** A static frame of {@code centre} from direction {@code dir} at {@code r} blocks (the STATIC shot, aimed in world space). */
	static Shots.Shot sideShot(Scene scene, Vec3 centre, Vec3 dir, float r, float h, float seconds) {
		// STATIC places the camera at centre + (-sin a, cos a) · r with a = subject yaw + 90° + side
		double a = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		float side = (float) Mth.wrapDegrees(a - scene.yaw() - 90.0D);
		return Shots.stillAt(centre, r, h, side, seconds);
	}

	/** Over the shoulder of the fighter at {@code from} (facing {@code fwd}) toward the other's eye: a fixed world frame. */
	static Shots.Shot overShoulder(Vec3 from, Vec3 fwd, @Nullable Entity self, Vec3 to, @Nullable Entity other, float seconds) {
		double eye = self != null ? self.getEyeHeight() : 1.62D;
		double otherEye = other != null ? other.getEyeHeight() : 1.62D;
		Vec3 right = new Vec3(-fwd.z, 0.0D, fwd.x);
		Vec3 camera = from.add(0.0D, eye + 0.3D, 0.0D).subtract(fwd.scale(1.6D)).add(right.scale(0.6D));
		return Shots.stillAt(camera, 0.0F, 0.0F, 0.0F, seconds).lookAt(to.add(0.0D, otherEye, 0.0D));
	}

	/** One shot of the duel scene (flags re-read first; the shot's own start never counts as a foreign hurt). */
	static boolean shoot(Scene scene, DuelFx.SessionView s, Shots.Shot shot) {
		EpicDirector.refreshDuelFlags(scene, s.aId, s.bId);
		ShotGuard.strikeShown();
		return CinematicDirector.shoot(scene, shot, CinematicDirector.KIND_EXTRA, 3, true);
	}

	// ================================================================ the world

	private static void glint(DuelFx.SessionView s) {
		for (int id : new int[]{s.aId, s.bId}) {
			DuelLinks.Blade blade = DuelLinks.drawn(id, 1.0F);
			Entity entity = DuelFx.entity(id);
			Vec3 tip = blade != null ? blade.tip() : entity != null ? entity.position().add(0.0D, entity.getBbHeight() * 0.8D, 0.0D) : null;
			if (tip == null) {
				continue;
			}
			MetalFlash.add(tip, 0.3F, 0xFFF4E0, 5);
			if (EffectQuality.maximal() && EpicSettings.lens() == LensLayers.Setting.NYALA && !LensLayers.active()
					&& DuelFx.involved(s.aId, s.bId)) {
				LensLayers.shot(1 << FilmLens.FLARE.ordinal(), 0xFFF0D0, Util.getMillis(), tip);
				MetalFlash.lensUntil(DuelFx.ticks() + 8);
			}
		}
	}

	private static void world(ClientLevel level, Run r, long elapsed) {
		DuelFx.SessionView s = r.session;
		int span = r.film ? 100 : 40;
		if (elapsed > span + 20) {
			return;
		}
		RandomSource random = r.random;
		Vec3 centre = s.center;
		Vec3 a = s.markA();
		Vec3 b = s.markB();
		Vec3 line = new Vec3(b.x - a.x, 0.0D, b.z - a.z);
		Vec3 across = line.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-line.z, 0.0D, line.x).normalize();
		float windShare = r.countdown ? 0.25F : 1.0F;
		// wind streaks crossing the ring: 20 over the first 30 ticks (fewer later, a quarter in the countdown)
		int perTick = elapsed < 30 ? 1 : (elapsed % 3 == 0 ? 1 : 0);
		if (perTick > 0 && random.nextFloat() < windShare && WIND.size() < DuelFxSettings.pick(6, 20, 30)) {
			double off = (random.nextDouble() - 0.5D) * s.radius * 2.0D;
			Vec3 along = new Vec3(across.z, 0.0D, -across.x);
			Vec3 from = centre.add(across.scale(-s.radius - 4.0D)).add(along.scale(off)).add(0.0D, 0.4D + random.nextDouble() * 2.6D, 0.0D);
			WIND.add(new Wind(from, across, 1.5F + random.nextFloat() * 2.5F, DuelTime.now(), 16.0F + random.nextFloat() * 10.0F));
		}
		if (r.countdown) {
			return;
		}
		// falling leaves of the biome
		int leaves = DuelFxSettings.pick(2, 8, 20);
		BlockPos at = BlockPos.containing(centre);
		ParticleOptions leaf = leafOf(level, at);
		for (int i = 0; i < leaves && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double rr = random.nextDouble() * (s.radius + 3.0D);
			double x = centre.x + Math.cos(angle) * rr;
			double z = centre.z + Math.sin(angle) * rr;
			double y = centre.y + 3.0D + random.nextDouble() * 3.0D;
			level.addParticle(leaf, x, y, z, across.x * 0.08D, -0.02D, across.z * 0.08D);
		}
		// the talismans of the ring flutter in the wind
		if (elapsed % 4 == 0 && FxBudget.take(1, FxBudget.Priority.EXTRA) > 0) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 p = centre.add(Math.cos(angle) * s.radius, 1.4D, Math.sin(angle) * s.radius);
			level.addParticle(Qi.petal(0xF8F4EA, 0.15F, 40), p.x, p.y, p.z, across.x * 0.06D, 0.01D, across.z * 0.06D);
		}
	}

	/** Cherry petals in a cherry grove, snow where it snows, else leaves in the biome's foliage colour. */
	static ParticleOptions leafOf(ClientLevel level, BlockPos at) {
		Holder<Biome> biome = level.getBiome(at);
		if (biome.is(Biomes.CHERRY_GROVE)) {
			return ParticleTypes.CHERRY_LEAVES;
		}
		if (biome.value().coldEnoughToSnow(at, level.getSeaLevel())) {
			return ParticleTypes.SNOWFLAKE;
		}
		int colour = BiomeColors.getAverageFoliageColor(level, at);
		return ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | (colour & 0xFFFFFF));
	}

	static void render(DuelGeo frame) {
		if (WIND.isEmpty()) {
			return;
		}
		for (Wind w : WIND) {
			float age = (float) (frame.time - w.born);
			if (age < 0.0F || age > w.life || !frame.take(1)) {
				continue;
			}
			float t = age / w.life;
			float alpha = 0.22F * Mth.sin((float) Math.PI * t);
			Vec3 head = w.from.add(w.dir.scale(age * 0.9D));
			Vec3 tail = head.subtract(w.dir.scale(w.length));
			frame.segment(frame.strip, tail, head, 0.0F, 0.06F, DuelGeo.argb(0.0F, 0xFFFFFF), DuelGeo.argb(alpha, 0xF4F8FF), 0.0F, 1.0F);
		}
	}

	static boolean idle() {
		return WIND.isEmpty();
	}

	static void clear() {
		run = null;
		WIND.clear();
	}

	private DuelIntroFilm() {
	}
}

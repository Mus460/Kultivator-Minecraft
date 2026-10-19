package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RingQuad;
import dev.kultivator.client.fx.quad.StreakQuad;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Movement arts as motion, not camera snaps (polish S16). A FLAG_BLINK TRAVEL (pos = from, to = to) draws, on the arrival
 * side and in view: a light streak from → to in the Dao colour (a zig-zag for lightning steps), three or four frozen
 * afterimages of the caster along the path that fade one after another, a departure burst and an arrival burst (in one's
 * own first person 1.5 blocks in front of the camera, so it is seen), and speed lines over one's own screen. A dash that
 * lasts several ticks also leaves a trail behind the moving caster every tick until it ends.
 */
final class MovementFx {

	/** A dash in progress: a trail behind the caster for its ticks. */
	private static final class Dash {
		final int caster;
		final int color;
		int ticks;
		Vec3 last;

		Dash(int caster, int color, int ticks, Vec3 last) {
			this.caster = caster;
			this.color = color;
			this.ticks = ticks;
			this.last = last;
		}
	}

	private static final List<Dash> DASHES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	static void onBlink(FxEvent ev) {
		Vec3 from = ev.pos;
		Vec3 to = ev.hasTarget ? ev.target : ev.pos;
		double distance = from.distanceTo(to);
		if (distance < 0.5D) {
			return;
		}
		Dao dao = ev.spec.dao();
		int color = dao == null ? 0xFFE7A0 : dao.color();
		int core = Qi.core(color);
		boolean quads = !EffectQuality.minimal() && ev.viewScale > 0.3F;
		Vec3 lift = new Vec3(0.0D, 1.0D, 0.0D);
		if (quads) {
			float zig = dao == Dao.LIGHTNING ? (float) Math.min(1.2D, distance * 0.06D) : 0.0F;
			QuadFx.add(new StreakQuad(from.add(lift), to.add(lift), dao == Dao.LIGHTNING ? 0.3F : 0.55F, color, core, 3.0F, 4.0F, zig, ev.seed, 12)
					.caster(ev.casterId));
			if (dao == Dao.LIGHTNING || dao == Dao.SHADOW || dao == Dao.SPACE) {
				// a second, thinner strand
				QuadFx.add(new StreakQuad(from.add(0.0D, 1.5D, 0.0D), to.add(0.0D, 1.5D, 0.0D), 0.18F, core, 0xFFFFFF, 2.0F, 3.0F, zig * 1.5F, ev.seed + 7, 9)
						.caster(ev.casterId));
			}
			QuadFx.add(new RingQuad(new Vec3(to.x, LayerRun.groundY(to.add(0.0D, 1.0D, 0.0D)) + 0.03D, to.z), 0.3F, 2.6F, color, 0.7F, 12)
					.caster(ev.casterId));
		}
		Entity caster = ev.caster();
		if ((caster instanceof Player || caster instanceof DiscipleEntity) && quads) {
			int copies = EffectQuality.maximal() ? 4 : 3;
			List<Vec3> points = new ArrayList<>(copies);
			for (int i = 0; i < copies; i++) {
				points.add(from.lerp(to, (i + 0.4D) / copies));
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
			AvatarFx.fixed(ev.casterId, points, yaw, color, 110, 14);
		}
		ParticleOptions puff = puffOf(dao, color);
		burst(from.add(lift), puff, 14, 0.18D);
		Vec3 arrival = to.add(lift);
		if (ev.firstPerson()) {
			Minecraft minecraft = Minecraft.getInstance();
			Vec3 eye = minecraft.gameRenderer.mainCamera().position();
			Vec3 look = minecraft.player.getLookAngle();
			arrival = eye.add(look.scale(1.5D)).add(0.0D, -0.3D, 0.0D);
			ScreenFlash.speedLines(Qi.lerp(color, 0xFFFFFF, 0.4F), 8);
			if (!dev.kultivator.client.epic.camera.CinematicDirector.active()) {
				// a FOV kick of 4% at most (none under "Kurangi gerak": the snap checks it)
				dev.kultivator.client.epic.camera.CinematicDirector.snap(4.0F, 2, 3);
			}
		}
		burst(arrival, puff, 18, 0.22D);
		burst(arrival, Qi.ring(color, 0.3F, 1.8F, 8), 1, 0.0D);
		if (ev.duration > 1 && caster != null) {
			DASHES.add(new Dash(ev.casterId, color, Math.min(40, ev.duration), caster.position()));
		}
	}

	private static ParticleOptions puffOf(Dao dao, int color) {
		if (dao == null) {
			return Qi.glint(color, 0.35F, 12);
		}
		return switch (dao) {
			case SHADOW -> Qi.smoke(0x2A1A40, 0.8F, 26);
			case LIGHTNING -> Qi.spark(color, 0.45F, 6);
			case FIRE -> Qi.flame(color, 0.5F, 14);
			case ICE -> Qi.snow(color, 0.25F, 30);
			case LIFE -> Qi.petal(color, 0.3F, 30);
			case BODY -> Qi.smoke(0x8A7A66, 0.9F, 24);
			default -> Qi.mist(color, 0.6F, 16);
		};
	}

	static void tick() {
		if (DASHES.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			DASHES.clear();
			return;
		}
		for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
			Dash dash = it.next();
			Entity entity = level.getEntity(dash.caster);
			if (entity == null || dash.ticks-- <= 0) {
				it.remove();
				continue;
			}
			Vec3 now = entity.position();
			if (now.distanceToSqr(dash.last) > 0.04D) {
				Vec3 a = dash.last.add(0.0D, 1.0D, 0.0D);
				Vec3 b = now.add(0.0D, 1.0D, 0.0D);
				if (!EffectQuality.minimal()) {
					QuadFx.add(new StreakQuad(a, b, 0.4F, dash.color, Qi.core(dash.color), 0.0F, 0.0F, 0.0F, dash.ticks, 6).caster(dash.caster));
				}
				int n = Math.max(1, Math.round(3 * EffectQuality.level().factor()));
				for (int i = 0; i < n; i++) {
					Vec3 p = a.lerp(b, RANDOM.nextDouble()).add((RANDOM.nextDouble() - 0.5D) * 0.6D, (RANDOM.nextDouble() - 0.5D) * 1.2D,
							(RANDOM.nextDouble() - 0.5D) * 0.6D);
					add(Qi.spark(dash.color, 0.4F, 6), p, b.subtract(a).scale(-0.2D));
				}
				dash.last = now;
			}
		}
	}

	static void clear() {
		DASHES.clear();
	}

	private static void burst(Vec3 at, ParticleOptions particle, int count, double speed) {
		int n = Math.max(1, Math.round(count * EffectQuality.level().factor()));
		for (int i = 0; i < n; i++) {
			Vec3 d = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6D, RANDOM.nextGaussian()).normalize();
			add(particle, at.add(d.scale(0.2D)), d.scale(speed));
		}
	}

	private static void add(ParticleOptions particle, Vec3 at, Vec3 velocity) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0) {
			level.addParticle(particle, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private MovementFx() {
	}
}

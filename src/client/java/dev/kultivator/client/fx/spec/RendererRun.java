package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The custom renderers of DESIGN 22.4.4 that are not entities:
 * <ul>
 * <li>AVATAR: hands the giant copy to {@link AvatarLayer} for players; at Minimal, for others' casts at Ringan, in one's
 * own first-person view and for non-player casters it draws an outline of particles instead;</li>
 * <li>CLONE: tinted copies through {@link AvatarLayer} (0 / 4 / 12 by quality); at Minimal afterimage outlines;</li>
 * <li>SWARM: boids of motes (40 / 120 / 240) flocking to the target or around the anchor;</li>
 * <li>SKY_SILHOUETTE: a sky-scale point-cloud beast drifting (or diving to the target);</li>
 * <li>VIEWER_MARK: a turning ring and chevron over the marked monster, drawn only on the caster's own client.</li>
 * </ul>
 */
final class RendererRun extends LayerRun {

	private static final int[] SWARM_MAX = {40, 120, 240};
	private static final int[] CLONE_MAX = {0, 4, 12};

	private final FxLayer.Template template;
	private final ParticleOptions main;
	private final DustParticleOptions alt;
	private float[] px = new float[0];
	private float[] py = new float[0];
	private float[] pz = new float[0];
	private float[] vx = new float[0];
	private float[] vy = new float[0];
	private float[] vz = new float[0];
	private boolean outline;
	private @Nullable Entity marked;
	private @Nullable DustParticleOptions big;

	RendererRun(FxEvent ev, FxLayer layer, int life, int index) {
		super(ev, layer, life, index);
		this.template = layer.template();
		this.alt = new DustParticleOptions(layer.color2() & 0xFFFFFF, dustSize(0.9F));
		this.main = layer.particle() != null ? layer.particle() : new DustParticleOptions(layer.color() & 0xFFFFFF, dustSize(1.1F));
		switch (template) {
			case SWARM -> buildSwarm();
			case AVATAR -> startAvatar();
			case CLONE -> startClones();
			case VIEWER_MARK -> findMarked();
			default -> {
			}
		}
	}

	private void buildSwarm() {
		int max = SWARM_MAX[EffectQuality.level().ordinal()];
		int boids = Math.max(4, Math.min(max, Math.round((layer.count() > 20 ? layer.count() : max) * Math.min(1.0F, ev.viewScale * 1.5F))));
		px = new float[boids];
		py = new float[boids];
		pz = new float[boids];
		vx = new float[boids];
		vy = new float[boids];
		vz = new float[boids];
		Vec3 start = anchor();
		for (int i = 0; i < boids; i++) {
			Vec3 offset = sphere().scale(0.5D + rnd.nextDouble() * 1.5D);
			px[i] = (float) (start.x + offset.x);
			py[i] = (float) (start.y + 1.0D + offset.y);
			pz[i] = (float) (start.z + offset.z);
			vx[i] = (float) (offset.x * 0.05D);
			vy[i] = (float) (offset.y * 0.05D);
			vz[i] = (float) (offset.z * 0.05D);
		}
	}

	/** The giant copy for player casters; others draw a particle outline. */
	private void startAvatar() {
		float scale = layer.scale() > 1.0F ? layer.scale() : 3.0F;
		scale = Math.min(4.0F, scale * Math.min(1.3F, ev.sizeF));
		Entity caster = ev.caster();
		boolean player = caster instanceof Player;
		if (player && !EffectQuality.minimal() && ev.viewScale > 0.3F) {
			AvatarFx.avatar(ev.casterId, scale, layer.color(), layer.alpha() > 0.0F ? Math.round(layer.alpha() * 255.0F) : 110, life);
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean firstPerson = ev.own && minecraft.options.getCameraType().isFirstPerson();
		outline = !player || EffectQuality.minimal() || ev.viewScale <= 0.3F || firstPerson;
	}

	private void startClones() {
		int max = CLONE_MAX[EffectQuality.level().ordinal()];
		int count = Math.min(max, layer.n() > 0 ? layer.n() : 4);
		if (ev.viewScale <= 0.3F) {
			count = 0;
		}
		Entity caster = ev.caster();
		if (caster instanceof Player && count > 0) {
			AvatarFx.clones(ev.casterId, count, layer.radius() > 0.0F ? layer.radius() : 2.0F, layer.color(), life);
		} else {
			outline = true;
		}
	}

	/** The living entity at the event's second point (one small query at the start). */
	private void findMarked() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ev.own || minecraft.level == null) {
			return;
		}
		Vec3 at = ev.target;
		List<Entity> near = minecraft.level.getEntities((Entity) null, new AABB(at, at).inflate(2.0D), entity -> entity instanceof LivingEntity && entity.isAlive());
		double best = Double.MAX_VALUE;
		for (Entity entity : near) {
			double distance = entity.position().distanceToSqr(at);
			if (distance < best) {
				best = distance;
				marked = entity;
			}
		}
	}

	@Override
	boolean update(int age) {
		switch (template) {
			case SWARM -> swarm(age);
			case SKY_SILHOUETTE -> skySilhouette(age);
			case VIEWER_MARK -> {
				return viewerMark(age);
			}
			case AVATAR -> {
				if (outline) {
					avatarOutline(age);
				}
			}
			case CLONE -> {
				if (outline) {
					cloneOutlines(age);
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private void swarm(int age) {
		int boids = px.length;
		if (boids == 0) {
			return;
		}
		Vec3 goal = ev.hasTarget ? ev.target.add(0.0D, 1.0D, 0.0D) : anchor().add(0.0D, 1.5D, 0.0D);
		float orbit = radius(3.0F);
		double cx = 0.0D;
		double cy = 0.0D;
		double cz = 0.0D;
		for (int i = 0; i < boids; i++) {
			cx += px[i];
			cy += py[i];
			cz += pz[i];
		}
		cx /= boids;
		cy /= boids;
		cz /= boids;
		double time = age * 0.12D;
		for (int i = 0; i < boids; i++) {
			double phase = time + i * 0.7D;
			double tx = goal.x + Math.cos(phase) * orbit;
			double ty = goal.y + Math.sin(phase * 1.3D) * orbit * 0.3D;
			double tz = goal.z + Math.sin(phase) * orbit;
			vx[i] += (float) ((tx - px[i]) * 0.02D + (cx - px[i]) * 0.005D + (rnd.nextDouble() - 0.5D) * 0.04D);
			vy[i] += (float) ((ty - py[i]) * 0.02D + (cy - py[i]) * 0.005D + (rnd.nextDouble() - 0.5D) * 0.03D);
			vz[i] += (float) ((tz - pz[i]) * 0.02D + (cz - pz[i]) * 0.005D + (rnd.nextDouble() - 0.5D) * 0.04D);
			float speed = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
			if (speed > 0.6F) {
				vx[i] *= 0.6F / speed;
				vy[i] *= 0.6F / speed;
				vz[i] *= 0.6F / speed;
			}
			px[i] += vx[i];
			py[i] += vy[i];
			pz[i] += vz[i];
			if ((i + age) % 3 == 0) {
				emit(i % 4 == 0 ? alt : main, px[i], py[i], pz[i], 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private void skySilhouette(int age) {
		int n = quota(life);
		FxLayer.BeastKind kind = layer.beast() == null ? FxLayer.BeastKind.BIRD : layer.beast();
		float size = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : layer.scale() > 1.0F ? layer.scale() * ev.sizeF : 20.0F * ev.sizeF;
		size = Math.min(120.0F, size);
		Vec3 start = anchor();
		double progress = (age + 1) / (double) life;
		Vec3 center = ev.hasTarget ? start.add(ev.target.add(0.0D, size * 0.15D, 0.0D).subtract(start).scale(progress * progress))
				: start.add(hfwd.scale(size * 0.5D * progress));
		boolean flat = kind == FxLayer.BeastKind.BIRD || kind == FxLayer.BeastKind.KUN || kind == FxLayer.BeastKind.DRAGON;
		float[] shape = PointClouds.beast(kind);
		int count = shape.length / 2;
		Vec3 side = hfwd.cross(UP).normalize();
		if (big == null) {
			big = new DustParticleOptions(layer.color() & 0xFFFFFF, Math.min(4.0F, 2.5F + size / 40.0F));
		}
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(count);
			double x = shape[p * 2] * size / 2.0D;
			double y = shape[p * 2 + 1] * size / 2.0D;
			Vec3 at = flat ? center.add(side.scale(x)).add(hfwd.scale(y)) : center.add(side.scale(x)).add(0.0D, y, 0.0D);
			emit(i % 9 == 0 ? ParticleTypes.END_ROD : i % 3 == 0 ? alt : big, at, 0.0D, 0.0D, 0.0D);
		}
	}

	private boolean viewerMark(int age) {
		if (!ev.own) {
			return false;
		}
		Vec3 at;
		if (marked != null) {
			if (!marked.isAlive() || marked.isRemoved()) {
				return false;
			}
			at = marked.position().add(0.0D, marked.getBbHeight() + 0.6D, 0.0D);
		} else {
			at = ev.target.add(0.0D, 2.2D, 0.0D);
		}
		if (age % 3 != 0) {
			return true;
		}
		double turn = age * 0.15D;
		for (int i = 0; i < 8; i++) {
			double angle = turn + Math.PI * 2.0D * i / 8.0D;
			emit(main, at.x + Math.cos(angle) * 0.6D, at.y, at.z + Math.sin(angle) * 0.6D, 0.0D, 0.0D, 0.0D);
		}
		emit(alt, at.x, at.y + 0.5D, at.z, 0.0D, -0.02D, 0.0D);
		emit(alt, at.x + 0.2D, at.y + 0.7D, at.z, 0.0D, -0.02D, 0.0D);
		emit(alt, at.x - 0.2D, at.y + 0.7D, at.z, 0.0D, -0.02D, 0.0D);
		return true;
	}

	private void avatarOutline(int age) {
		int n = Math.max(2, quota(life));
		float scale = layer.scale() > 1.0F ? layer.scale() : 3.0F;
		scale = Math.min(4.0F, scale * Math.min(1.3F, ev.sizeF));
		Entity caster = ev.caster();
		Vec3 base = ev.casterPos();
		double yawRad = caster == null ? Math.atan2(-hfwd.x, hfwd.z) : Math.toRadians(caster.getYRot());
		Vec3 side = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
		float[] figure = PointClouds.humanoid();
		int count = figure.length / 2;
		double height = 0.95D * scale;
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(count);
			Vec3 at = base.add(side.scale(figure[p * 2] * height)).add(0.0D, (figure[p * 2 + 1] + 1.0D) * height, 0.0D);
			emit(i % 3 == 0 ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	private void cloneOutlines(int age) {
		int n = Math.max(2, quota(life));
		int copies = Math.max(1, Math.min(3, layer.n() > 0 ? layer.n() : 3));
		float r = layer.radius() > 0.0F ? layer.radius() : 2.0F;
		Vec3 base = ev.casterPos();
		float[] figure = PointClouds.humanoid();
		int count = figure.length / 2;
		double turn = age * 0.03D;
		for (int i = 0; i < n; i++) {
			int copy = i % copies;
			double angle = Math.PI * 2.0D * copy / copies + turn;
			Vec3 center = base.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
			Vec3 side = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
			int p = rnd.nextInt(count);
			Vec3 at = center.add(side.scale(figure[p * 2] * 0.95D)).add(0.0D, (figure[p * 2 + 1] + 1.0D) * 0.95D, 0.0D);
			emit(main, at, 0.0D, 0.0D, 0.0D);
		}
	}
}

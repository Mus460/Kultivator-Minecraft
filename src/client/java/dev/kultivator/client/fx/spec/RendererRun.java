package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.GiantAvatar;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RibbonQuad;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The custom renderers of DESIGN 22.4.4 that are not entities:
 * <ul>
 * <li>AVATAR: up to scale 2 a rim copy through {@link AvatarLayer} (players and disciples); above that the world-level
 * {@link GiantAvatar} standing behind the caster, posed by the cast-pose library (polish S21); a particle outline at
 * Minimal and for far others' casts;</li>
 * <li>CLONE: tinted copies through {@link AvatarLayer} (0 / 4 / 12 by quality) on a ring, or frozen afterimages along the
 * path of a movement event (FLAG_BLINK, S16); at Minimal afterimage outlines;</li>
 * <li>SWARM: boids of motes (40 / 120 / 240) flocking to the target or around the anchor (or the caster when attached);</li>
 * <li>SKY_SILHOUETTE: a sky-scale point-cloud beast drifting (or diving to the target), {@code n} copies, {@code size} as
 * its span;</li>
 * <li>VIEWER_MARK: a turning ring and chevron over the marked monster (v2 target id), drawn only on the caster's own
 * client;</li>
 * <li>RIBBON (polish C4): coiling dragons, whips, flame and blood ribbons as {@link RibbonQuad} geometry, an optional head
 * model riding the head, and the same path in motes when the geometry cap refuses.</li>
 * </ul>
 */
final class RendererRun extends LayerRun {

	private static final int[] SWARM_MAX = {40, 120, 240};
	private static final int[] CLONE_MAX = {0, 4, 12};

	private final FxLayer.Template template;
	private final ParticleOptions main;
	private final ParticleOptions alt;
	private final int color;
	private final int color2;
	private float[] px = new float[0];
	private float[] py = new float[0];
	private float[] pz = new float[0];
	private float[] vx = new float[0];
	private float[] vy = new float[0];
	private float[] vz = new float[0];
	private boolean outline;
	private @Nullable Entity marked;
	private @Nullable ParticleOptions big;
	private final List<RibbonQuad.Path> ribbons = new ArrayList<>();
	private boolean ribbonQuads;
	private @Nullable ModelHandle head;
	private String headModel = "";
	private float headWidth;
	private boolean headTried;
	private @Nullable GiantAvatar giant;

	RendererRun(FxEvent ev, FxLayer layer, int life, int index) {
		this(ev, layer, life, index, 1.0F);
	}

	RendererRun(FxEvent ev, FxLayer layer, int life, int index, float tierScale) {
		super(ev, layer, life, index, tierScale);
		this.template = layer.template();
		this.color = layer.color() & 0xFFFFFF;
		this.color2 = layer.color2() == layer.color() ? Qi.rim(color) : layer.color2() & 0xFFFFFF;
		float mote = moteSize(radius(2.0F), 1.0F);
		this.alt = Qi.mote(Qi.core(color2), color2, Qi.rim(color2), mote * 0.85F, mote * 0.3F, 16, dev.kultivator.fx.QiParticleOptions.GLOW);
		this.main = layer.particle() != null ? layer.particle() : Qi.mote(Qi.core(color), color, color2, mote, mote * 0.35F, 16, dev.kultivator.fx.QiParticleOptions.GLOW);
		switch (template) {
			case SWARM -> buildSwarm();
			case AVATAR -> startAvatar();
			case CLONE -> startClones();
			case VIEWER_MARK -> findMarked();
			case RIBBON -> buildRibbons();
			default -> {
			}
		}
	}

	/** Players and disciples get model copies; everything else draws outlines. */
	private static boolean modelCaster(@Nullable Entity caster) {
		return caster instanceof Player || caster instanceof DiscipleEntity;
	}

	private void buildSwarm() {
		int max = SWARM_MAX[EffectQuality.level().ordinal()];
		int boids = Math.max(4, Math.min(max, Math.round((layer.count() > 20 ? layer.count() : max) * Math.min(1.0F, ev.viewScale * 1.5F))));
		if (layer.n() > 0) {
			boids = Math.max(4, Math.min(max, layer.n() * 8));
		}
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

	/** The rim copy (scale ≤ 2) or the giant figure (above 2) for players and disciples; others draw an outline. */
	private void startAvatar() {
		float scale = layer.scale() > 1.0F ? layer.scale() : 3.0F;
		scale = scale * Math.min(1.3F, ev.sizeF) * Math.max(0.6F, tierScale);
		Entity caster = ev.caster();
		boolean copies = modelCaster(caster) && !EffectQuality.minimal() && ev.viewScale > 0.3F;
		int alpha = layer.alpha() > 0.0F ? Math.round(layer.alpha() * 255.0F) : 110;
		if (layer.variant() == FxLayer.Variant.SPLIT) {
			// a split avatar (three heads six arms) is drawn by the form layer: a thin rim only (plan 6.1)
			if (copies) {
				AvatarFx.avatar(ev.casterId, 1.05F, color, Math.max(40, alpha / 2), life);
			}
			outline = false;
			return;
		}
		if (copies && scale > 2.0F) {
			// a world-level giant behind the caster (drawn in first person too)
			Pose pose = ev.spec.resolvedPose(dev.kultivator.technique.spec.dsl.Phase.CAST);
			if (pose == null || pose == Pose.AUTO || pose == Pose.NONE) {
				pose = Pose.ARMS_SPREAD;
			}
			GiantAvatar figure = new GiantAvatar(ev.casterId, Math.min(40.0F, scale * 1.8F), color, color2, alpha / 255.0F, Math.max(2.0F, scale * 0.45F),
					pose, life);
			if (QuadFx.add(figure.delay(Math.max(0, layer.delay())))) {
				giant = figure;
				AvatarFx.avatar(ev.casterId, 1.05F, color, Math.max(40, alpha / 2), life);
				outline = false;
				return;
			}
		}
		if (copies) {
			AvatarFx.avatar(ev.casterId, Math.min(2.0F, scale), color, alpha, life);
		}
		outline = !copies || ev.firstPerson();
	}

	private void startClones() {
		int max = CLONE_MAX[EffectQuality.level().ordinal()];
		int count = Math.min(max, layer.n() > 0 ? layer.n() : 4);
		if (ev.viewScale <= 0.3F) {
			count = 0;
		}
		Entity caster = ev.caster();
		boolean path = ev.has(Payloads.TechFx.FLAG_BLINK) || attach() == FxLayer.Attach.LINE;
		if (modelCaster(caster) && count > 0) {
			if (path && ev.hasTarget) {
				// frozen afterimages along the movement path, fading one after another (S16)
				List<Vec3> points = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					points.add(ev.pos.lerp(ev.target, (i + 0.5D) / count));
				}
				float yaw = (float) Math.toDegrees(Math.atan2(-(ev.target.x - ev.pos.x), ev.target.z - ev.pos.z));
				AvatarFx.fixed(ev.casterId, points, yaw, color, layer.alpha() > 0.0F ? Math.round(layer.alpha() * 255.0F) : 130, life);
			} else {
				AvatarFx.clones(ev.casterId, count, layer.radius() > 0.0F ? layer.radius() : 2.0F, color, life);
			}
			outline = ev.firstPerson() && !path;
		} else {
			outline = true;
		}
	}

	/** The marked living entity: the v2 target id, else the one nearest the event's second point (one query at the start). */
	private void findMarked() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ev.own || minecraft.level == null) {
			return;
		}
		Entity byId = ev.targetEntity();
		if (byId instanceof LivingEntity && byId.isAlive()) {
			marked = byId;
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

	/** The paths of a RIBBON layer: dragons circling the caster or the area, a whip to the target, a coil up a pillar, a wave. */
	private void buildRibbons() {
		int copies = layer.n() > 0 ? Math.min(9, layer.n()) : 1;
		if (tierScale < 1.0F) {
			copies = Math.max(1, copies / 2);
		}
		float r = radius(3.0F);
		Vec3 center = anchor();
		boolean follow = attach() == FxLayer.Attach.CASTER;
		for (int i = 0; i < copies; i++) {
			double phase = Math.PI * 2.0D * i / copies + ev.seed * 0.001D;
			RibbonQuad.Path path;
			if (attach() == FxLayer.Attach.LINE && ev.hasTarget) {
				Vec3 start = lineStart();
				path = layer.variant() == FxLayer.Variant.FLYING ? RibbonQuad.wave(start, targetPoint(), 0.35D + 0.15D * i, Math.max(4.0F, life * 0.4F))
						: RibbonQuad.whip(start, targetPoint(), 0.8D, Math.max(3.0F, Math.min(8.0F, life * 0.4F)));
			} else if (layer.variant() == FxLayer.Variant.VERTICAL) {
				double height = layer.length() > 0.0F ? layer.length() * ev.sizeF : 12.0D * ev.sizeF;
				path = RibbonQuad.coil(new Vec3(center.x, center.y, center.z), Math.max(1.0D, r * 0.4D), height, 2.5D, 0.12D, phase);
			} else {
				double height = (layer.offsetY() > 0.0F ? 0.0D : 1.5D) + (i % 3) * 0.8D;
				double speed = (layer.motion() == FxLayer.Motion.ORBIT && layer.motionB() != 0.0F ? Math.toRadians(layer.motionB() / 20.0D) : 0.07D)
						* (layer.variant() == FxLayer.Variant.MIRRORED && i % 2 == 1 ? -1.0D : 1.0D);
				Vec3 c = follow ? Vec3.ZERO : center;
				path = RibbonQuad.orbit(c, r * (0.85D + 0.15D * (i % 2)), height, speed, phase, 200.0D, 0.6D + 0.2D * r / 6.0D);
				if (follow) {
					RibbonQuad.Path local = path;
					path = (t, age) -> local.at(t, age).add(ev.casterPos());
				}
			}
			ribbons.add(path);
		}
		float width = (layer.width() > 0.0F ? layer.width() * ev.sizeF : 0.55F * Math.max(1.0F, layer.size()) * Math.min(1.6F, ev.sizeF)) * tierScale;
		boolean scales = layer.beast() == FxLayer.BeastKind.DRAGON || layer.beast() == null;
		if (ev.viewScale > 0.3F && !EffectQuality.minimal()) {
			for (RibbonQuad.Path path : ribbons) {
				RibbonQuad quad = new RibbonQuad(path, 28, width, width * 0.15F, color, color2, scales, life);
				ribbonQuads |= QuadFx.add(quad.caster(ev.casterId).delay(Math.max(0, layer.delay())));
			}
		}
		this.headModel = layer.hasModel() ? layer.model() : scales && AnimBridge.modelKnown("dragon_head") ? "dragon_head" : "";
		this.headWidth = width;
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
			case RIBBON -> ribbon(age);
			default -> {
				return false;
			}
		}
		return true;
	}

	/**
	 * An event of the same cast moves the giant figure (S21, cosmic_giant_form): a hit swings a punch, a pre-impact warning
	 * walks it to the stomp point and lifts a leg, the second extra (the climax) kneels and slams, impacts and pulses stomp.
	 */
	void accent(FxEvent other) {
		if (giant == null) {
			return;
		}
		dev.kultivator.technique.spec.dsl.Phase phase = other.phase;
		if (phase == dev.kultivator.technique.spec.dsl.Phase.HIT || phase == dev.kultivator.technique.spec.dsl.Phase.MASS_HIT) {
			giant.play(Pose.PUNCH, 8);
		} else if (other.has(Payloads.TechFx.FLAG_PRE_IMPACT)) {
			Vec3 to = other.hasTarget ? other.target : other.pos;
			giant.walkTo(to.subtract(hfwd.scale(2.0D)), Math.max(4, other.duration - 2));
			giant.play(Pose.STOMP, Math.max(8, other.duration));
		} else if (phase.extraIndex() == 1) {
			giant.play(Pose.KNEEL_STRIKE, 14);
		} else {
			giant.play(Pose.STOMP, 12);
		}
	}

	@Override
	void finish(int ticks) {
		super.finish(ticks);
		AnimBridge.end(head, ticks);
		head = null;
	}

	@Override
	void end() {
		if (head != null && AnimBridge.alive(head)) {
			AnimBridge.end(head, 0);
		}
	}

	private void ribbon(int age) {
		if (ribbons.isEmpty()) {
			return;
		}
		if (!headTried) {
			// the dragon head prop rides the first body from the first tick after the delay
			headTried = true;
			if (!headModel.isEmpty() && ribbonQuads && ev.displaysAllowed && AnimBridge.modelKnown(headModel)) {
				head = AnimBridge.model(headModel, ribbons.get(0).at(0.0D, age), Math.max(1.0F, headWidth * 2.5F), 0.0F, 0.0F, 0.0F, life, List.of(), -1,
						Vec3.ZERO, false, true, ev.casterId, layer.priority());
			}
		}
		if (head != null) {
			RibbonQuad.Path first = ribbons.get(0);
			Vec3 at = first.at(0.0D, age + 1);
			Vec3 behind = first.at(0.05D, age + 1);
			Vec3 d = at.subtract(behind);
			AnimBridge.moveTo(head, at, 1);
			AnimBridge.rotateTo(head, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))),
					0.0F, 1);
		}
		int n = quota(life);
		if (ribbonQuads) {
			// embers and scale glints shed from the bodies
			for (int i = 0; i < Math.max(1, n / 3); i++) {
				RibbonQuad.Path path = ribbons.get(rnd.nextInt(ribbons.size()));
				Vec3 at = path.at(rnd.nextDouble(), age);
				emit(i % 2 == 0 ? main : alt, at, (rnd.nextDouble() - 0.5D) * 0.05D, -0.02D, (rnd.nextDouble() - 0.5D) * 0.05D);
			}
			return;
		}
		// Minimal / refused: the bodies in motes
		for (int i = 0; i < Math.max(2, n); i++) {
			RibbonQuad.Path path = ribbons.get(i % ribbons.size());
			double t = rnd.nextDouble();
			emit(t < 0.1D ? alt : main, path.at(t, age), 0.0D, 0.0D, 0.0D);
		}
	}

	private void swarm(int age) {
		int boids = px.length;
		if (boids == 0) {
			return;
		}
		Vec3 goal;
		if (ev.hasTarget && attach() != FxLayer.Attach.CASTER) {
			goal = targetPoint();
		} else if (attach() == FxLayer.Attach.CASTER) {
			goal = ev.casterPos().add(0.0D, 1.2D + layer.offsetY(), 0.0D);
		} else {
			goal = anchor().add(0.0D, 1.5D, 0.0D);
		}
		float orbit = radius(attach() == FxLayer.Attach.CASTER ? 1.6F : 3.0F);
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
				// gold / silver flicker of insect wings
				emit(i % 4 == 0 ? alt : main, px[i], py[i], pz[i], vx[i] * 0.3D, vy[i] * 0.3D, vz[i] * 0.3D);
			}
		}
	}

	private void skySilhouette(int age) {
		int n = quota(life);
		FxLayer.BeastKind kind = layer.beast() == null ? FxLayer.BeastKind.BIRD : layer.beast();
		float size;
		if (layer.radius() > 0.0F) {
			size = layer.radius() * ev.sizeF;
		} else if (layer.scale() > 1.0F) {
			size = layer.scale() * ev.sizeF;
		} else if (layer.size() > 1.0F) {
			// size() as the span in blocks (S14)
			size = layer.size() * ev.sizeF;
		} else {
			size = 20.0F * ev.sizeF;
		}
		size = Math.min(120.0F, size) * tierScale;
		int copies = layer.n() > 1 ? Math.min(5, layer.n()) : 1;
		Vec3 start = anchor();
		double progress = (age + 1) / (double) life;
		boolean flat = kind == FxLayer.BeastKind.BIRD || kind == FxLayer.BeastKind.KUN || kind == FxLayer.BeastKind.DRAGON;
		float[] shape = PointClouds.beast(kind);
		int count = shape.length / 2;
		Vec3 side = hfwd.cross(UP).normalize();
		if (big == null) {
			big = Qi.mist(color, Math.min(4.0F, 1.2F + size / 25.0F), 26);
		}
		for (int i = 0; i < n; i++) {
			int copy = i % copies;
			Vec3 lane = copies == 1 ? Vec3.ZERO : side.scale((copy - (copies - 1) / 2.0D) * size * 0.8D);
			Vec3 center = ev.hasTarget ? start.add(lane).add(ev.target.add(0.0D, size * 0.15D, 0.0D).subtract(start).scale(progress * progress))
					: start.add(lane).add(hfwd.scale(size * 0.5D * progress));
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
		if (ev.firstPerson()) {
			// one's own first-person view: a denser outline around and behind (S3: at least 40 a tick at Normal)
			n = Math.max(n, Math.round(40 * EffectQuality.level().factor()));
		}
		float scale = layer.scale() > 1.0F ? layer.scale() : 3.0F;
		scale = Math.min(6.0F, scale * Math.min(1.3F, ev.sizeF));
		Vec3 base = ev.casterPos();
		double yawRad = Math.toRadians(ev.casterYaw());
		Vec3 side = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
		Vec3 back = new Vec3(Math.sin(yawRad), 0.0D, -Math.cos(yawRad));
		if (scale > 2.0F) {
			base = base.add(back.scale(scale * 0.45D));
		}
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
		boolean path = ev.has(Payloads.TechFx.FLAG_BLINK) && ev.hasTarget;
		Vec3 base = ev.casterPos();
		float[] figure = PointClouds.humanoid();
		int count = figure.length / 2;
		double turn = age * 0.03D;
		for (int i = 0; i < n; i++) {
			int copy = i % copies;
			Vec3 center;
			Vec3 side;
			if (path) {
				center = ev.pos.lerp(ev.target, (copy + 0.5D) / copies);
				side = right;
			} else {
				double angle = Math.PI * 2.0D * copy / copies + turn;
				center = base.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
				side = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
			}
			int p = rnd.nextInt(count);
			Vec3 at = center.add(side.scale(figure[p * 2] * 0.95D)).add(0.0D, (figure[p * 2 + 1] + 1.0D) * 0.95D, 0.0D);
			emit(main, at, 0.0D, 0.0D, 0.0D);
		}
	}
}

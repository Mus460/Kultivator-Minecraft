package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.beast.BeastData;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.SegmentChain;
import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaLiarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Draws a Jiao (DESIGN 34.5.11) from our cuboid models: the head with its hinged jaw and glowing eyes, two whiskers of
 * five strands that trail and flutter, a body of 8 / 14 / 20 segments laid along the chain of {@link NagaVisuals}
 * (thinning toward the tail, every third with a dorsal sail, a slow undulation on top of the flight path), two pairs of
 * paddling claws, the tail fin; a tamed one carries its pearl under the chin, a Horned Jiao (tier 4) antlers, a True
 * Dragon (5) a mane, Yinglong (6) two feathered wings that beat. The breath is a cone of additive quads (24 / 64 / 128
 * by quality, from the 7b budget), the tail strike a glowing ring, the Selendang's binding three gold loops.
 * Level of detail: within 32 blocks everything, within 96 no whiskers nor claws and the chain halves itself, beyond
 * 192 only the head and every other segment. Nothing here touches the world.
 */
public class NagaRenderer extends EntityRenderer<NagaLiarEntity, NagaRenderer.State> {

	private static final RenderType GLOW = RenderTypes.lightning();
	/** The model scale of the head (a 20 px head = 2.75 blocks). */
	private static final float HEAD_SCALE = 2.2F;
	/** Body width from the neck to the tail, and the (constant) length scale that keeps the segments overlapping. */
	private static final float BODY_FROM = 2.0F;
	private static final float BODY_TO = 0.85F;
	private static final float BODY_LENGTH = 1.55F;
	private static final int[] CONE_RINGS = {3, 6, 8};
	private static final int[] CONE_SIDES = {8, 10, 16};

	/** What one frame needs. */
	public static class State extends EntityRenderState {
		public int entityId;
		public NagaKind kind = NagaKind.AIR;
		public int tier = 3;
		public int flags;
		public int skill;
		public float skillTicks;
		public boolean tamed;
		public float yaw;
		public float pitch;
		public float jaw;
		public float partial;
		public Vec3 headPos = Vec3.ZERO;
		public int count;
		/** The body's length scale when fewer segments than the chain holds are drawn (LOD keeps it closed). */
		public float stretch = 1.0F;
		public final Vec3[] segPos = new Vec3[24];
		public final float[] segYaw = new float[24];
		public final float[] segPitch = new float[24];
		public @Nullable Vec3 aim;
		public @Nullable Vec3 tailPoint;
	}

	public NagaRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
		// a resource reload rebuilds the renderers: the resolved model states are stale now
		NagaModels.clear();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(NagaLiarEntity entity, float partialTicks) {
		// the body trails some 20 blocks behind the head
		return entity.getBoundingBox().inflate(24.0D);
	}

	@Override
	public void extractRenderState(NagaLiarEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.entityId = entity.getId();
		state.kind = entity.kind();
		state.tier = entity.tier();
		state.flags = entity.flags();
		state.skill = entity.skill();
		state.skillTicks = entity.skillTicks() + partialTicks;
		state.tamed = entity.isTamed();
		state.partial = partialTicks;
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		state.pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
		state.headPos = entity.getPosition(partialTicks).add(0.0D, 0.6D, 0.0D);
		NagaVisuals.Visual visual = NagaVisuals.of(entity);
		state.jaw = visual == null ? 0.05F : visual.jaw(partialTicks);
		state.count = 0;
		if (visual != null) {
			SegmentChain chain = visual.chain();
			// the capacity already follows EffectQuality (8 / 14 / 20); here only the distance thins the body out
			int capacity = chain.capacity();
			int drawn = chain.segments() <= 0 ? 0 : capacity;
			double distanceSq = state.distanceToCameraSq;
			if (distanceSq > 192.0D * 192.0D) {
				drawn = Math.min(drawn, 6);
			} else if (distanceSq > 96.0D * 96.0D) {
				drawn = Math.min(drawn, Math.max(6, capacity / 2));
			} else if (distanceSq > 32.0D * 32.0D) {
				drawn = Math.min(drawn, Math.max(6, capacity * 3 / 4));
			}
			int n = Math.min(state.segPos.length, Math.max(0, drawn));
			for (int i = 0; i < n; i++) {
				int index = n <= 1 ? 0 : Math.round(i * (capacity - 1) / (float) (n - 1));
				SegmentChain.Pose pose = chain.pose(index, partialTicks);
				state.segPos[i] = pose.pos();
				state.segYaw[i] = pose.yaw();
				state.segPitch[i] = pose.pitch();
			}
			state.count = n;
			state.stretch = n > 1 ? Math.max(1.0F, (capacity - 1) / (float) (n - 1)) : 1.0F;
		}
		Entity aim = entity.aimId() >= 0 ? entity.level().getEntity(entity.aimId()) : null;
		state.aim = aim == null ? null : aim.getBoundingBox().getCenter();
		state.tailPoint = state.count > 0 ? state.segPos[state.count - 1] : null;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		boolean near = state.distanceToCameraSq < 32.0D * 32.0D;
		boolean mid = state.distanceToCameraSq < 96.0D * 96.0D;
		String kind = state.kind.id();
		float age = state.ageInTicks;
		boolean lemah = (state.flags & NagaLiarEntity.FLAG_LEMAH) != 0;
		boolean resting = (state.flags & NagaLiarEntity.FLAG_RESTING) != 0;
		boolean enraged = (state.flags & (NagaLiarEntity.FLAG_ENRAGED | NagaLiarEntity.FLAG_FRENZY)) != 0;
		// ------------------------------------------------------------ the body (world positions, relative to the head entity)
		Vec3 origin = new Vec3(state.x, state.y, state.z);
		int n = state.count;
		// a resting Jiao breathes slowly, an enraged one writhes; Lemah barely moves
		float sway = lemah ? 0.4F : resting ? 0.35F : enraged ? 1.35F : 1.0F;
		float pace = resting ? 0.07F : enraged ? 0.26F : 0.18F;
		// Ekor Sabet: the tail rears up over its telegraph, then lashes round
		boolean tailSkill = state.skill == NagaLiarEntity.SKILL_TAIL;
		float tailRaise = 0.0F;
		float tailLash = 0.0F;
		if (tailSkill) {
			float s = state.skillTicks;
			if (s < NagaLiarEntity.TAIL_TELEGRAPH) {
				tailRaise = s / NagaLiarEntity.TAIL_TELEGRAPH;
			} else {
				float k = Mth.clamp((s - NagaLiarEntity.TAIL_TELEGRAPH) / 6.0F, 0.0F, 1.0F);
				tailRaise = 1.0F - k;
				tailLash = Mth.sin(k * Mth.PI);
			}
		}
		for (int i = 1; i < n; i++) {
			float t = i / (float) Math.max(1, n - 1);
			float width = Mth.lerp(t, BODY_FROM, BODY_TO);
			Vec3 at = state.segPos[i].subtract(origin);
			float undulate = Mth.sin(age * pace - i * 0.7F) * 7.0F * sway * (0.3F + t);
			float tailK = Mth.clamp((t - 0.55F) / 0.45F, 0.0F, 1.0F);
			undulate += tailLash * tailK * 55.0F;
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.segYaw[i] + undulate));
			poseStack.rotate(Axis.XP.rotationDegrees(-state.segPitch[i] - tailRaise * tailK * 38.0F));
			poseStack.rotate(Axis.ZP.rotationDegrees(Mth.sin(age * pace * 0.6F - i * 0.5F) * 6.0F * sway));
			poseStack.scale(width, width, BODY_LENGTH * state.stretch);
			NagaModels.submit((i % 3 == 1 ? "sail_" : "body_") + kind, poseStack, collector, light);
			poseStack.popPose();
			if (mid && (i == 2 || i == Math.max(3, Math.round(n * 0.62F)))) {
				claws(state, poseStack, collector, light, at, i, width, age, kind);
			}
			if (state.tier >= 5 && i <= 3) {
				onSegment(state, poseStack, collector, light, at, i, width, "mane_" + kind, 0.0F, 0.55F, 0.0F, 1.0F);
			}
			if (state.tier >= BeastData.TOP_TIER && i == 2) {
				wings(state, poseStack, collector, light, at, i, width, age, kind);
			}
		}
		if (n >= 2) {
			int last = n - 1;
			Vec3 at = state.segPos[last].subtract(origin);
			onSegment(state, poseStack, collector, light, at, last, BODY_TO * 1.6F, "tail_" + kind, 0.0F, 0.0F, 0.35F,
					BODY_TO * 1.6F);
		}
		// ------------------------------------------------------------ the head
		Vec3 head = state.headPos.subtract(origin);
		poseStack.pushPose();
		poseStack.translate(head.x, head.y, head.z);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yaw));
		poseStack.rotate(Axis.XP.rotationDegrees(-state.pitch));
		poseStack.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
		NagaModels.submit("head_" + kind, poseStack, collector, light);
		// the jaw, hinged under the back of the skull
		poseStack.pushPose();
		poseStack.translate(0.0F, -1.5F / 16.0F, 1.0F / 16.0F);
		poseStack.rotate(Axis.XP.rotationDegrees(state.jaw * 34.0F));
		NagaModels.submit("jaw_" + kind, poseStack, collector, light);
		poseStack.popPose();
		if (state.tier >= 4) {
			// a Horned Jiao's antlers; from the True Dragon on, the great gilded antlers
			boolean great = state.tier >= 5;
			for (int side = -1; side <= 1; side += 2) {
				poseStack.pushPose();
				poseStack.translate(side * 3.4F / 16.0F, 4.6F / 16.0F, 5.5F / 16.0F);
				float hs = great ? 0.72F : 0.8F;
				poseStack.scale(hs, hs, hs);
				NagaModels.submit(great ? side < 0 ? "horn_great_l" : "horn_great_r" : side < 0 ? "horn_l" : "horn_r", poseStack, collector, light);
				poseStack.popPose();
			}
		}
		if (enraged) {
			// Amukan Naga / Gila Darah: the eyes burn, a flicker of flame trails from them
			poseStack.pushPose();
			float flicker = 1.0F + 0.04F * Mth.sin(age * 1.7F);
			poseStack.scale(flicker, flicker, flicker);
			NagaModels.submit("eyes_rage", poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
			poseStack.popPose();
		}
		if (near || mid && !EffectQuality.minimal()) {
			whiskers(state, poseStack, collector, near, age, kind);
		}
		if (state.tamed) {
			// the dragon pearl it carries under its chin
			poseStack.pushPose();
			poseStack.translate(Mth.sin(age * 0.07F) * 0.05F, -6.5F / 16.0F + Mth.sin(age * 0.11F) * 0.03F, -7.0F / 16.0F);
			poseStack.rotate(Axis.YP.rotationDegrees(age * 3.0F));
			poseStack.scale(0.32F, 0.32F, 0.32F);
			NagaModels.submit("pearl", poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
			poseStack.popPose();
		}
		poseStack.popPose();
		// ------------------------------------------------------------ skills, drawn as additive light
		if (mid) {
			effects(state, poseStack, collector, origin, age);
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** A model on a body segment at an offset (model pixels of the segment) with its own scale. */
	private static void onSegment(State state, PoseStack poseStack, SubmitNodeCollector collector, int light, Vec3 at, int i, float width,
			String model, float ox, float oy, float oz, float scale) {
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.segYaw[i]));
		poseStack.rotate(Axis.XP.rotationDegrees(-state.segPitch[i]));
		poseStack.translate(ox * width, oy * width, oz * width);
		poseStack.scale(scale, scale, scale);
		NagaModels.submit(model, poseStack, collector, light);
		poseStack.popPose();
	}

	/** Two claws under segment {@code i}, paddling through the air. */
	private static void claws(State state, PoseStack poseStack, SubmitNodeCollector collector, int light, Vec3 at, int i, float width, float age,
			String kind) {
		for (int side = -1; side <= 1; side += 2) {
			float paddle = Mth.sin(age * 0.2F + i + (side > 0 ? Mth.PI : 0.0F)) * 22.0F;
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.segYaw[i]));
			poseStack.rotate(Axis.XP.rotationDegrees(-state.segPitch[i]));
			poseStack.translate(side * 5.2F / 16.0F * width, -3.0F / 16.0F * width, 0.0F);
			poseStack.rotate(Axis.XP.rotationDegrees(paddle));
			float s = width * 0.75F;
			poseStack.scale(s, s, s);
			NagaModels.submit((side < 0 ? "claw_l_" : "claw_r_") + kind, poseStack, collector, light);
			poseStack.popPose();
		}
	}

	/** Yinglong's wings on segment {@code i}: a slow, mighty beat. */
	private static void wings(State state, PoseStack poseStack, SubmitNodeCollector collector, int light, Vec3 at, int i, float width, float age,
			String kind) {
		float beat = Mth.sin(age * 0.12F) * 28.0F;
		for (int side = -1; side <= 1; side += 2) {
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.segYaw[i]));
			poseStack.rotate(Axis.XP.rotationDegrees(-state.segPitch[i]));
			poseStack.translate(side * 4.5F / 16.0F * width, 4.0F / 16.0F * width, 0.0F);
			poseStack.rotate(Axis.ZP.rotationDegrees(side * (beat - 8.0F)));
			float s = width * 1.25F;
			poseStack.scale(s, s, s);
			NagaModels.submit((side < 0 ? "wing_l_" : "wing_r_") + kind, poseStack, collector, light);
			poseStack.popPose();
		}
	}

	/** Two whiskers from the snout, each a chain of five strands that trail and flutter (2 strands at Minimal). */
	private static void whiskers(State state, PoseStack poseStack, SubmitNodeCollector collector, boolean near, float age, String kind) {
		int strands = EffectQuality.minimal() ? 2 : near ? 5 : 3;
		for (int side = -1; side <= 1; side += 2) {
			poseStack.pushPose();
			poseStack.translate(side * 3.8F / 16.0F, 0.6F / 16.0F, -6.6F / 16.0F);
			poseStack.rotate(Axis.YP.rotationDegrees(side * -28.0F));
			for (int k = 0; k < strands; k++) {
				float wave = Mth.sin(age * 0.3F - k * 0.9F + side) * (10.0F + 6.0F * k);
				poseStack.rotate(Axis.YP.rotationDegrees(wave * 0.6F * side));
				poseStack.rotate(Axis.XP.rotationDegrees(-6.0F + wave * 0.4F));
				poseStack.pushPose();
				poseStack.scale(0.5F, 0.5F, 0.9F);
				NagaModels.submit("whisker_" + kind, poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
				poseStack.popPose();
				// next strand starts at the end of this one (5 px × 0.9 / 16, scaled with the head)
				poseStack.translate(0.0F, 0.0F, 5.0F * 0.9F / 16.0F);
			}
			poseStack.popPose();
		}
	}

	// ================================================================== additive effects

	private static void effects(State state, PoseStack poseStack, SubmitNodeCollector collector, Vec3 origin, float age) {
		int quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		int glow = state.kind.glow;
		if (state.skill == NagaLiarEntity.SKILL_BREATH) {
			Vec3 mouth = mouth(state).subtract(origin);
			Vec3 dir = state.aim != null ? state.aim.subtract(mouth(state)) : forward(state.yaw, state.pitch);
			dir = dir.lengthSqr() < 1.0E-4D ? forward(state.yaw, state.pitch) : dir.normalize();
			float t = state.skillTicks;
			if (t < NagaLiarEntity.BREATH_TELEGRAPH) {
				float grow = t / NagaLiarEntity.BREATH_TELEGRAPH;
				orb(poseStack, collector, mouth, 0.3F + 0.9F * grow, ARGB.color(Math.round(90 + 120 * grow), glow));
			} else if (t < NagaLiarEntity.BREATH_TELEGRAPH + NagaLiarEntity.BREATH_ACTIVE) {
				float life = (t - NagaLiarEntity.BREATH_TELEGRAPH) / NagaLiarEntity.BREATH_ACTIVE;
				int rings = CONE_RINGS[quality];
				int sides = CONE_SIDES[quality];
				if (NagaVisuals.takeQuads(rings * sides)) {
					cone(poseStack, collector, mouth, dir, rings, sides, age, glow, 1.0F - life * 0.6F);
				}
				orb(poseStack, collector, mouth, 0.9F, ARGB.color(200, 0xFFFFFF));
			}
		} else if (state.skill == NagaLiarEntity.SKILL_TAIL && state.tailPoint != null && state.skillTicks < NagaLiarEntity.TAIL_TELEGRAPH + 6) {
			float t = Mth.clamp(state.skillTicks / NagaLiarEntity.TAIL_TELEGRAPH, 0.0F, 1.0F);
			int sides = quality == 0 ? 16 : 32;
			if (NagaVisuals.takeQuads(sides)) {
				ring(poseStack, collector, state.tailPoint.subtract(origin), 6.0F * t, 0.35F + 0.4F * t, sides,
						ARGB.color(Math.round(80 + 150 * t), glow));
			}
		} else if (state.skill == NagaLiarEntity.SKILL_ROAR && state.skillTicks >= NagaLiarEntity.ROAR_TELEGRAPH) {
			float t = Mth.clamp((state.skillTicks - NagaLiarEntity.ROAR_TELEGRAPH) / 10.0F, 0.0F, 1.0F);
			if (NagaVisuals.takeQuads(32)) {
				ring(poseStack, collector, mouth(state).subtract(origin), 2.0F + 16.0F * t, 0.6F, 32, ARGB.color(Math.round(180 * (1.0F - t)), glow));
			}
		}
		Vec3 headAt = state.headPos.subtract(origin);
		if ((state.flags & (NagaLiarEntity.FLAG_ENRAGED | NagaLiarEntity.FLAG_FRENZY)) != 0 && NagaVisuals.takeQuads(3)) {
			// the heat of its fury around the head
			float pulse = 0.5F + 0.5F * Mth.sin(age * 0.5F);
			orb(poseStack, collector, headAt, 1.5F + 0.4F * pulse, ARGB.color(Math.round(50 + 60 * pulse), 0xFF3A20));
		}
		if (state.skill == NagaLiarEntity.SKILL_SPECIAL && state.skillTicks < NagaLiarEntity.SPECIAL_TELEGRAPH) {
			// the kind skill gathers: light at the mouth; the thunder Jiao crackles along its whole body
			float t = state.skillTicks / NagaLiarEntity.SPECIAL_TELEGRAPH;
			if (NagaVisuals.takeQuads(3)) {
				orb(poseStack, collector, mouth(state).subtract(origin), 0.5F + 1.1F * t, ARGB.color(Math.round(90 + 140 * t), glow));
			}
			if (state.kind == NagaKind.PETIR && state.count > 4) {
				arcs(poseStack, collector, state, origin, age, quality, glow);
			}
		}
		if ((state.flags & NagaLiarEntity.FLAG_GUARD) != 0 && NagaVisuals.takeQuads(32)) {
			// Penjaga Raga: a slow gold halo over the guardian's head
			ring(poseStack, collector, headAt.add(0.0D, 2.4D, 0.0D), 1.1F + 0.1F * Mth.sin(age * 0.1F), 0.15F, 16,
					ARGB.color(150, 0xFFE08A));
		}
		if ((state.flags & NagaLiarEntity.FLAG_CONTEST) != 0 && NagaVisuals.takeQuads(48)) {
			// the Selendang's three gold loops tighten around the head
			Vec3 head = state.headPos.subtract(origin);
			for (int k = 0; k < 3; k++) {
				float r = 1.9F + 0.25F * Mth.sin(age * 0.25F + k * 2.1F);
				ring(poseStack, collector, head.add(0.0D, -0.4D + 0.45D * k, 0.0D), r, 0.12F, 16, ARGB.color(170, 0xFFD27A));
			}
		}
	}

	/**
	 * Hujan Petir Langit gathering: three jagged arcs leap between body segments two apart, re-drawn every other tick
	 * (2 quads per sub-segment, 4 sub-segments an arc; 1 arc at Minimal).
	 */
	private static void arcs(PoseStack poseStack, SubmitNodeCollector collector, State state, Vec3 origin, float age, int quality, int rgb) {
		int count = quality == 0 ? 1 : 3;
		int pieces = 4;
		if (!NagaVisuals.takeQuads(count * pieces * 2)) {
			return;
		}
		RandomSource random = RandomSource.create(state.entityId * 31L + (long) Math.floor(age / 2.0F));
		Vec3[][] paths = new Vec3[count][pieces + 1];
		for (int k = 0; k < count; k++) {
			int a = 1 + random.nextInt(Math.max(1, state.count - 3));
			int b = Math.min(state.count - 1, a + 2);
			Vec3 from = state.segPos[a].subtract(origin).add(0.0D, 0.9D, 0.0D);
			Vec3 to = state.segPos[b].subtract(origin).add(0.0D, 0.9D, 0.0D);
			for (int i = 0; i <= pieces; i++) {
				double t = i / (double) pieces;
				double jitter = i == 0 || i == pieces ? 0.0D : 0.7D;
				paths[k][i] = from.add(to.subtract(from).scale(t)).add((random.nextFloat() - 0.5F) * jitter * 2.0D,
						(random.nextFloat() - 0.2F) * jitter * 2.0D, (random.nextFloat() - 0.5F) * jitter * 2.0D);
			}
		}
		int color = ARGB.color(220, rgb);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (Vec3[] path : paths) {
				for (int i = 0; i < pieces; i++) {
					Vec3 a = path[i];
					Vec3 b = path[i + 1];
					Vec3 dir = b.subtract(a);
					if (dir.lengthSqr() < 1.0E-6D) {
						continue;
					}
					Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
					side = side.lengthSqr() < 1.0E-6D ? new Vec3(0.12D, 0.0D, 0.0D) : side.normalize().scale(0.12D);
					Vec3 lift = new Vec3(0.0D, 0.12D, 0.0D);
					vertex(pose, buffer, a.subtract(side), color);
					vertex(pose, buffer, a.add(side), color);
					vertex(pose, buffer, b.add(side), color);
					vertex(pose, buffer, b.subtract(side), color);
					vertex(pose, buffer, a.subtract(lift), color);
					vertex(pose, buffer, a.add(lift), color);
					vertex(pose, buffer, b.add(lift), color);
					vertex(pose, buffer, b.subtract(lift), color);
				}
			}
		});
	}

	private static Vec3 mouth(State state) {
		return state.headPos.add(forward(state.yaw, state.pitch).scale(1.8D)).add(0.0D, -0.05D, 0.0D);
	}

	private static Vec3 forward(float yawDeg, float pitchDeg) {
		float yaw = yawDeg * Mth.DEG_TO_RAD;
		float pitch = pitchDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
	}

	/** The breath cone: {@code rings} bands of {@code sides} quads from the mouth outward, turning, fading at the far end. */
	private static void cone(PoseStack poseStack, SubmitNodeCollector collector, Vec3 mouth, Vec3 dir, int rings, int sides, float age, int rgb,
			float strength) {
		Vec3 up = Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = dir.cross(up).normalize();
		Vec3 upv = right.cross(dir).normalize();
		double length = NagaLiarEntity.BREATH_LENGTH;
		double spread = 0.46D;
		poseStack.pushPose();
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (int r = 0; r < rings; r++) {
				double a0 = (r / (double) rings);
				double a1 = ((r + 1) / (double) rings);
				double d0 = length * a0;
				double d1 = length * a1;
				double w0 = 0.25D + d0 * spread;
				double w1 = 0.25D + d1 * spread;
				int alpha0 = Math.round(strength * 150.0F * (1.0F - (float) a0 * 0.8F));
				int alpha1 = Math.round(strength * 150.0F * (1.0F - (float) a1 * 0.8F));
				for (int s = 0; s < sides; s++) {
					double t0 = (s / (double) sides) * Math.PI * 2.0D + age * 0.25D + r * 0.4D;
					double t1 = ((s + 1) / (double) sides) * Math.PI * 2.0D + age * 0.25D + r * 0.4D;
					Vec3 p00 = mouth.add(dir.scale(d0)).add(right.scale(Math.cos(t0) * w0)).add(upv.scale(Math.sin(t0) * w0));
					Vec3 p01 = mouth.add(dir.scale(d0)).add(right.scale(Math.cos(t1) * w0)).add(upv.scale(Math.sin(t1) * w0));
					Vec3 p11 = mouth.add(dir.scale(d1)).add(right.scale(Math.cos(t1) * w1)).add(upv.scale(Math.sin(t1) * w1));
					Vec3 p10 = mouth.add(dir.scale(d1)).add(right.scale(Math.cos(t0) * w1)).add(upv.scale(Math.sin(t0) * w1));
					int c0 = ARGB.color(Math.max(0, alpha0), rgb);
					int c1 = ARGB.color(Math.max(0, alpha1), rgb);
					vertex(pose, buffer, p00, c0);
					vertex(pose, buffer, p01, c0);
					vertex(pose, buffer, p11, c1);
					vertex(pose, buffer, p10, c1);
				}
			}
		});
		poseStack.popPose();
	}

	/** A horizontal ring band at {@code center} (radius {@code radius}, band {@code band}). */
	static void ring(PoseStack poseStack, SubmitNodeCollector collector, Vec3 center, float radius, float band, int sides, int color) {
		if (radius <= 0.01F) {
			return;
		}
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			float inner = Math.max(0.0F, radius - band * 0.5F);
			float outer = radius + band * 0.5F;
			int clear = color & 0x00FFFFFF;
			for (int s = 0; s < sides; s++) {
				double t0 = s / (double) sides * Math.PI * 2.0D;
				double t1 = (s + 1) / (double) sides * Math.PI * 2.0D;
				vertex(pose, buffer, center.add(Math.cos(t0) * inner, 0.0D, Math.sin(t0) * inner), clear);
				vertex(pose, buffer, center.add(Math.cos(t1) * inner, 0.0D, Math.sin(t1) * inner), clear);
				vertex(pose, buffer, center.add(Math.cos(t1) * radius, 0.0D, Math.sin(t1) * radius), color);
				vertex(pose, buffer, center.add(Math.cos(t0) * radius, 0.0D, Math.sin(t0) * radius), color);
				vertex(pose, buffer, center.add(Math.cos(t0) * radius, 0.0D, Math.sin(t0) * radius), color);
				vertex(pose, buffer, center.add(Math.cos(t1) * radius, 0.0D, Math.sin(t1) * radius), color);
				vertex(pose, buffer, center.add(Math.cos(t1) * outer, 0.0D, Math.sin(t1) * outer), clear);
				vertex(pose, buffer, center.add(Math.cos(t0) * outer, 0.0D, Math.sin(t0) * outer), clear);
			}
		});
	}

	/** A glowing orb (three crossed quads) at {@code center}. */
	static void orb(PoseStack poseStack, SubmitNodeCollector collector, Vec3 center, float radius, int color) {
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			float r = radius;
			Vector3f[] axes = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
			for (int a = 0; a < 3; a++) {
				Vector3f u = axes[a];
				Vector3f v = axes[(a + 1) % 3];
				vertex(pose, buffer, center.add(-u.x * r - v.x * r, -u.y * r - v.y * r, -u.z * r - v.z * r), color);
				vertex(pose, buffer, center.add(u.x * r - v.x * r, u.y * r - v.y * r, u.z * r - v.z * r), color);
				vertex(pose, buffer, center.add(u.x * r + v.x * r, u.y * r + v.y * r, u.z * r + v.z * r), color);
				vertex(pose, buffer, center.add(-u.x * r + v.x * r, -u.y * r + v.y * r, -u.z * r + v.z * r), color);
			}
		});
	}

	static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color) {
		buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
	}
}

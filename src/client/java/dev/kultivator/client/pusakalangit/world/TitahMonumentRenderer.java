package dev.kultivator.client.pusakalangit.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.pusakalangit.TitahMonumentEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a planted decree weapon, {@code kultivator:titah_monument} (DESIGN 29.6.1, 29.8; contract C10b.4): the entity's
 * {@code kultivator:fx/<model>} item model at its own scale, stood on the client's ground at the entity's column (so it
 * stays planted while the crater forms under it). Swords stand point-first, a quarter of the blade in the ground, leaning
 * {@code tilt}° towards the caster; the Sea-Stabilising Pillar stands upright, sunk 25 %. It is lit full bright while hot
 * (heat = 1 − age / 1200, from the entity) and then by the world's light.
 * <p>
 * States: 0 settling and 1 standing (still; the light breathes), 2 pulling (40 ticks: it rises 1.5 b with growing shakes,
 * brightening, then a white-gold column lifts it; {@link MonumentNotes} carries it on after the entity is removed), 3
 * leaving (rises and shrinks away over 40 ticks). Extras by tier (never in the shadow pass): Normal a glow on the ground
 * at its foot, Maximal also a light strip along the blade or glows on the pillar's hoops. The entity renders in Iris's
 * shadow pass like any entity. The item stack is made lazily ({@link ModelFx#stack}), never in the constructor.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): the scale clamp is 1100 (a tier-9 sword is about 960 b long at Kekuatan ×1), the
 * culling box follows the scaled model and the monument draws up to {@code max(160, 1.5 × length)} b from the camera
 * (its own test, not the entity's 160 b). Beyond render distance, where its chunk section is not drawn, {@link FarRelic}
 * shows it instead (an impostor or a silhouette from the server's {@code TitahFar} list), and this renderer steps aside
 * for it.
 */
public class TitahMonumentRenderer extends EntityRenderer<TitahMonumentEntity, TitahMonumentRenderer.State> {

	private static final String FALLBACK = "heaven_splitting_sword";
	/** The tip of our sword models below their grip anchor, at scale 1 (blocks). */
	private static final float SWORD_TIP = 1.47F;
	/** The pillar's half length at scale 1 (its anchor is its middle). */
	private static final float PILLAR_HALF = 1.5F;
	private static final int PULL_TICKS = 40;
	/** The largest scale drawn (Tingkat Titah, 29.16.10; the server clamps to the same 1100). */
	static final float MAX_SCALE = 1100.0F;

	private static @Nullable RenderType groundGlow;

	private final ItemModelResolver itemModelResolver;

	/** The render state: the model, its pose and the extras' strength. */
	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public String model = FALLBACK;
		public boolean sword = true;
		public float scale = 12.0F;
		public float yaw;
		public float tilt;
		public int stateId;
		public float stateAge;
		public float heat;
		/** Where the ground is relative to the entity position (the client heightmap). */
		public float groundOffset;
		/** The model's lift above its planted pose (pull, leave), its shakes and its size factor. */
		public float lift;
		public float shakeX;
		public float shakeZ;
		public float shrink = 1.0F;
		/** 0..1 of the light extras (heat, breathing, a pull's brightening). */
		public float glow;
		/** 0..1 of the pull's light column. */
		public float column;
		public int tier;
		public float camX;
		public float camY;
		public float camZ;
	}

	public TitahMonumentRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/** The model's length in blocks at scale {@code s}: about 1.87 × s for a sword, 3 × s for the pillar (29.16.1). */
	static float length(boolean sword, float s) {
		return (sword ? 1.87F : 2.0F * PILLAR_HALF) * s;
	}

	/**
	 * Its own distance test (29.16.10): up to {@code max(160, 1.5 × length)} b instead of the entity's fixed 160 b, then the
	 * frustum with the scaled culling box. Beyond render distance {@link FarRelic} draws it when it knows it.
	 */
	@Override
	public boolean shouldRender(TitahMonumentEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		float s = Mth.clamp(Float.isFinite(entity.scale()) ? entity.scale() : 12.0F, 0.5F, MAX_SCALE);
		String id = entity.modelId();
		double range = Math.max(TitahMonumentEntity.RENDER_RANGE, 1.5D * length(id != null && id.endsWith("_sword"), s));
		if (entity.distanceToSqr(camX, camY, camZ) > range * range) {
			return false;
		}
		double dx = entity.getX() - camX;
		double dz = entity.getZ() - camZ;
		double near = TitahWorld.viewRange() - 16.0D;
		if (dx * dx + dz * dz > near * near && FarRelic.drawsAt(entity.getX(), entity.getZ())) {
			return false;
		}
		AABB box = getBoundingBoxForCulling(entity, partialTicks).inflate(0.5D);
		return box.hasNaN() || culler.isVisible(box);
	}

	@Override
	protected AABB getBoundingBoxForCulling(TitahMonumentEntity entity, float partialTicks) {
		float s = Mth.clamp(Float.isFinite(entity.scale()) ? entity.scale() : 12.0F, 0.5F, MAX_SCALE);
		double w = Math.max(1.5D, s * 0.7D);
		double up = s * 3.2D + (entity.state() >= MonumentNotes.STATE_PULLING ? 48.0D : 0.0D);
		double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		return new AABB(x - w, y - s * 1.6D - 2.0D, z - w, x + w, y + up, z + w);
	}

	/** The owner's name shows while the crosshair is on the monument (the look-at tooltip, 29.6.1). */
	@Override
	protected boolean shouldShowName(TitahMonumentEntity entity, double distanceToCameraSq) {
		String owner = entity.ownerName();
		return owner != null && !owner.isEmpty() && entity == this.entityRenderDispatcher.crosshairPickEntity;
	}

	@Override
	protected @Nullable Component getNameTag(TitahMonumentEntity entity) {
		String owner = entity.ownerName();
		return owner == null || owner.isEmpty() ? null : Component.literal("敕 " + owner).withColor(0xFFE8A0);
	}

	@Override
	public void extractRenderState(TitahMonumentEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		String id = entity.modelId();
		if (id == null || !ModelFx.known(id)) {
			id = FALLBACK;
		}
		state.model = id;
		state.sword = id.endsWith("_sword");
		// made on first use: item components are not bound yet when entity renderers are created at resource reload
		itemModelResolver.updateForNonLiving(state.item, ModelFx.stack(id), ItemDisplayContext.FIXED, entity);
		float s = Mth.clamp(Float.isFinite(entity.scale()) ? entity.scale() : 12.0F, 0.5F, MAX_SCALE);
		state.scale = s;
		state.yaw = entity.getYRot();
		state.tilt = Mth.clamp(Float.isFinite(entity.tilt()) ? entity.tilt() : 0.0F, -30.0F, 30.0F);
		state.stateId = entity.state();
		state.stateAge = Math.max(0.0F, entity.stateAge(partialTicks));
		state.heat = Mth.clamp(entity.heat(partialTicks), 0.0F, 1.0F);
		state.tier = DampakWorldBudget.tier();
		// the ground at the entity's column on this client (the monument stays planted while the crater forms)
		state.groundOffset = 0.0F;
		Level level = entity.level();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(state.x), Mth.floor(state.z));
		if (top > level.getMinY()) {
			state.groundOffset = (float) Mth.clamp(top - state.y, -s * 1.5D, 6.0D);
		}
		pose(state);
		// full bright while hot (and while it is pulled or leaves), then the world's light
		int boost = state.stateId >= MonumentNotes.STATE_PULLING ? 15 : Math.round(15.0F * Math.min(1.0F, state.heat * 1.6F));
		int block = LightCoordsUtil.block(state.lightCoords);
		int sky = LightCoordsUtil.sky(state.lightCoords);
		state.lightCoords = LightCoordsUtil.pack(Math.max(block, boost), Math.max(sky, boost));
		Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
		state.camX = (float) (camera.x - state.x);
		state.camY = (float) (camera.y - state.y);
		state.camZ = (float) (camera.z - state.z);
		// tell the per-tick side (motes, pull dust, the carry-away) where it stands
		double groundY = state.y + state.groundOffset;
		Vec3 ground = new Vec3(state.x, groundY, state.z);
		Vec3 anchor = new Vec3(state.x + state.shakeX, groundY + state.lift + anchorHeight(state), state.z + state.shakeZ);
		MonumentNotes.note(entity.getId(), anchor, ground, id, s, state.yaw, state.sword, state.stateId, state.stateAge, state.heat);
	}

	/** The anchor's height above the ground in the planted pose. */
	private static float anchorHeight(State state) {
		return state.sword ? SWORD_TIP * state.scale * 0.75F : PILLAR_HALF * state.scale * 0.5F;
	}

	/** The pose of the state: lift, shakes, size and the light extras. */
	private static void pose(State state) {
		state.lift = 0.0F;
		state.shakeX = 0.0F;
		state.shakeZ = 0.0F;
		state.shrink = 1.0F;
		state.column = 0.0F;
		float breathe = 0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.08F);
		state.glow = Math.min(1.0F, state.heat * breathe);
		switch (state.stateId) {
			case 2 -> {
				float t = Mth.clamp(state.stateAge / PULL_TICKS, 0.0F, 1.0F);
				if (t < 0.75F) {
					float u = t / 0.75F;
					state.lift = 1.5F * u * u * (3.0F - 2.0F * u);
					float amp = (0.03F + 0.12F * u) * Math.max(1.0F, state.scale * 0.08F);
					state.shakeX = amp * Mth.sin(state.ageInTicks * 2.3F);
					state.shakeZ = amp * Mth.cos(state.ageInTicks * 1.7F);
					state.glow = Math.max(state.glow, 0.4F + 0.6F * u);
				} else {
					float v = (t - 0.75F) / 0.25F;
					state.lift = 1.5F + 30.0F * v * v;
					state.glow = 1.0F;
					state.column = v;
				}
			}
			case 3 -> {
				float t = Mth.clamp(state.stateAge / PULL_TICKS, 0.0F, 1.0F);
				state.lift = 24.0F * t * t;
				state.shrink = t > 0.75F ? Math.max(0.01F, 1.0F - (t - 0.75F) / 0.25F) : 1.0F;
				state.glow = 0.8F;
				state.column = 0.5F * (1.0F - t);
			}
			default -> {
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		float s = state.scale;
		poseStack.pushPose();
		poseStack.translate(state.shakeX, state.groundOffset + state.lift, state.shakeZ);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		if (state.sword) {
			// leaning towards the caster (its front), a quarter of the blade in the ground, point down
			poseStack.rotate(Axis.XP.rotationDegrees(-state.tilt));
			poseStack.translate(0.0F, SWORD_TIP * s * 0.75F, 0.0F);
			poseStack.rotate(Axis.ZP.rotationDegrees(180.0F));
		} else {
			poseStack.translate(0.0F, PILLAR_HALF * s * 0.5F, 0.0F);
		}
		float k = s * state.shrink;
		poseStack.scale(k, k, k);
		state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		if (state.tier >= 1 && !ShaderCompat.shadowPass()) {
			submitExtras(state, poseStack, submitNodeCollector);
		}
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	// ================================================================== extras: ground glow, blade strip / hoop glows, the pull's column

	private void submitExtras(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		float glow = state.glow;
		if (glow <= 0.03F && state.column <= 0.0F) {
			return;
		}
		boolean warm = !state.sword || "zhuxian_sword".equals(state.model);
		int rgb = warm ? TitahWorld.EMBER : TitahWorld.WHITE_GOLD;
		float s = state.scale;
		float ground = state.groundOffset + 0.06F;
		Quads soft = new Quads();
		Quads strips = new Quads();
		// a hot glow round the foot, breathing
		float r = s * 0.35F * (0.6F + 0.4F * glow);
		soft.flat(0.0F, ground, 0.0F, r, TitahWorld.argb(0.65F * glow, rgb));
		if (state.tier >= 2) {
			float base = state.groundOffset + state.lift;
			if (state.sword) {
				// a light strip up the exposed blade and over the hilt
				float top = base + SWORD_TIP * s * 0.75F + 0.4F * s;
				strips.beam(state.shakeX, base, state.shakeZ, state.shakeX, top, state.shakeZ, Math.max(0.4F, s * 0.12F), TitahWorld.argb(0.45F * glow, rgb),
						TitahWorld.argb(0.0F, rgb), state);
			} else {
				// the gold hoops glowing at both ends of the visible pillar
				float centre = base + PILLAR_HALF * s * 0.5F;
				soft.billboard(state.shakeX, centre + PILLAR_HALF * s * 0.92F, state.shakeZ, s * 0.45F, TitahWorld.argb(0.6F * glow, TitahWorld.GOLD), state);
				soft.billboard(state.shakeX, centre + PILLAR_HALF * s * 0.77F, state.shakeZ, s * 0.3F, TitahWorld.argb(0.4F * glow, TitahWorld.GOLD), state);
			}
		}
		if (state.column > 0.0F) {
			// the white-gold column that carries the weapon up and away
			float a = Math.min(1.0F, 0.3F + state.column);
			strips.beam(0.0F, state.groundOffset, 0.0F, 0.0F, state.groundOffset + 96.0F, 0.0F, Math.max(1.0F, s * 0.35F), TitahWorld.argb(0.85F * a,
					TitahWorld.WHITE_GOLD), TitahWorld.argb(0.0F, TitahWorld.WHITE_GOLD), state);
		}
		if (groundGlow == null) {
			groundGlow = RenderTypes.entityTranslucentEmissive(SigilRenderer.GROUND_GLOW);
		}
		soft.submit(collector, poseStack, groundGlow);
		strips.submit(collector, poseStack, EpicRenderTypes.GLOW);
	}

	/** A few full-bright quads in the entity's local space (camera-relative to the entity), written once per frame. */
	private static final class Quads {
		private float[] data = new float[8 * 16];
		private int[] colors = new int[16];
		private int count;

		void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
			if (count >= colors.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
				colors = java.util.Arrays.copyOf(colors, colors.length * 2);
			}
			int o = count * 8;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			data[o + 5] = nx;
			data[o + 6] = ny;
			data[o + 7] = nz;
			colors[count] = argb;
			count++;
		}

		/** A horizontal square of half size {@code half} (uv over the whole sheet), facing up. */
		void flat(float x, float y, float z, float half, int color) {
			vertex(x - half, y, z - half, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x - half, y, z + half, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x + half, y, z + half, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x + half, y, z - half, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}

		/** A square facing the camera (the camera's offset from the entity is in the state). */
		void billboard(float x, float y, float z, float half, int color, State state) {
			float vx = x - state.camX;
			float vz = z - state.camZ;
			float len = (float) Math.sqrt(vx * vx + vz * vz);
			float sx = len < 1.0E-4F ? half : -vz / len * half;
			float sz = len < 1.0E-4F ? 0.0F : vx / len * half;
			vertex(x - sx, y - half, z - sz, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x + sx, y - half, z + sz, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x + sx, y + half, z + sz, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			vertex(x - sx, y + half, z - sz, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		}

		/** A strip from a to b turning about its own axis to face the camera; v runs across it. */
		void beam(float ax, float ay, float az, float bx, float by, float bz, float width, int ca, int cb, State state) {
			float dx = bx - ax;
			float dy = by - ay;
			float dz = bz - az;
			float mx = (ax + bx) * 0.5F - state.camX;
			float my = (ay + by) * 0.5F - state.camY;
			float mz = (az + bz) * 0.5F - state.camZ;
			float sx = dy * mz - dz * my;
			float sy = dz * mx - dx * mz;
			float sz = dx * my - dy * mx;
			float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (len < 1.0E-5F) {
				return;
			}
			float h = width * 0.5F / len;
			sx *= h;
			sy *= h;
			sz *= h;
			vertex(ax - sx, ay - sy, az - sz, 0.0F, 0.0F, ca, 0.0F, 1.0F, 0.0F);
			vertex(ax + sx, ay + sy, az + sz, 0.0F, 1.0F, ca, 0.0F, 1.0F, 0.0F);
			vertex(bx + sx, by + sy, bz + sz, 1.0F, 1.0F, cb, 0.0F, 1.0F, 0.0F);
			vertex(bx - sx, by - sy, bz - sz, 1.0F, 0.0F, cb, 0.0F, 1.0F, 0.0F);
		}

		void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type) {
			if (count < 4) {
				return;
			}
			float[] d = java.util.Arrays.copyOf(data, count * 8);
			int[] c = java.util.Arrays.copyOf(colors, count);
			int n = count / 4 * 4;
			collector.submitCustomGeometry(poseStack, type, (PoseStack.Pose pose, VertexConsumer buffer) -> {
				for (int i = 0; i < n; i++) {
					int o = i * 8;
					buffer.addVertex(pose, d[o], d[o + 1], d[o + 2])
							.setColor(c[i])
							.setUv(d[o + 3], d[o + 4])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(LightCoordsUtil.FULL_BRIGHT)
							.setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
				}
			});
		}
	}
}

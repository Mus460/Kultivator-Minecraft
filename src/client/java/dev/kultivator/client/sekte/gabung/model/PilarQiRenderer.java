package dev.kultivator.client.sekte.gabung.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.sekte.gabung.masuk.PilarQiEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code kultivator:pilar_qi} (DESIGN 41.11.3): one of the five carved stone pillars of "Lima Pilar Qi". A two-step
 * plinth, a fluted shaft in two drums with bronze corner ribs and two bands of cloud relief, four bronze-framed plaques
 * carved with the element's seal glyph (Logam 金, Kayu 木, Air 水, Api 火, Tanah 土, inlaid in the element colour), a
 * capital with a bronze lip, a four-pronged cradle and above it the element crystal floating in a ring of runes.
 * <p>
 * The element's own sheet ({@code textures/entity/gabung/pilar_qi_<unsur>.png}) carries the glyph and crystal colour;
 * the glow sheet of the same element lights the glyph strokes, the crystal and the rune rings through the vanilla
 * {@code eyes} type, tinted with {@link PilarQiEntity#warna()}. The light follows {@link PilarQiEntity#nyala()} (eased on
 * the client) and the phase: idle a faint breath; held ({@link PilarQiEntity#FASE_AKTIF}) a quick pulse, the crystal
 * spins faster and rises; passed ({@link PilarQiEntity#FASE_LULUS}) a steady full light, the rings wide; failed
 * ({@link PilarQiEntity#FASE_GAGAL}) a dim, flickering, reddened light and the crystal sinks into its cradle. Minimal:
 * the crystal and rings hold still.
 */
public class PilarQiRenderer extends EntityRenderer<PilarQiEntity, PilarQiRenderer.State> {

	static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pilar_qi"), "main");
	private static final RenderType[] TYPE = new RenderType[PilarQiEntity.UNSUR_JUMLAH];
	private static final RenderType[] GLOW = new RenderType[PilarQiEntity.UNSUR_JUMLAH];

	static {
		for (int u = 0; u < PilarQiEntity.UNSUR_JUMLAH; u++) {
			TYPE[u] = RenderTypes.entityCutout(Kultivator.id("textures/entity/gabung/pilar_qi_" + u + ".png"));
			GLOW[u] = RenderTypes.eyes(Kultivator.id("textures/entity/gabung/pilar_qi_" + u + "_pancar.png"));
		}
	}

	public static class State extends GabungGambar.State {
		int unsur;
		int fase;
		float nyala;
		int warna = 0xFFFFFF;
	}

	/** The light shown per pillar, eased toward the synced value. */
	private final Map<PilarQiEntity, float[]> tampil = new WeakHashMap<>();
	private final Model model;

	public PilarQiRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER));
		this.shadowRadius = 0.6F;
	}

	static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PilarQiRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PilarQiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.waktu = entity.tickCount + partialTicks;
		state.rinci = state.distanceToCameraSq < GabungGambar.RINCI_SQ && !EffectQuality.minimal();
		state.unsur = Math.floorMod(entity.unsur(), PilarQiEntity.UNSUR_JUMLAH);
		state.fase = entity.fase();
		state.warna = entity.warna();
		float[] t = tampil.computeIfAbsent(entity, e -> new float[] {e.nyala(), state.waktu});
		float dt = Mth.clamp(state.waktu - t[1], 0.0F, 10.0F);
		t[0] += (entity.nyala() - t[0]) * (1.0F - (float) Math.pow(0.85D, dt));
		t[1] = state.waktu;
		state.nyala = t[0];
	}

	@Override
	protected AABB getBoundingBoxForCulling(PilarQiEntity entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(0.6D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			GabungGambar.ruangModel(poseStack, state.yaw);
			collector.submitModel(model, state, poseStack, TYPE[state.unsur], state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			float kuat = kuat(state);
			if (kuat > 0.02F) {
				int warna = state.fase == PilarQiEntity.FASE_GAGAL ? ARGB.srgbLerp(0.55F, state.warna | 0xFF000000, 0xFFA03020) : state.warna;
				collector.order(1).submitModel(model, state, poseStack, GLOW[state.unsur], state.lightCoords, OverlayTexture.NO_OVERLAY,
						ARGB.color(Math.round(255.0F * Math.min(1.0F, kuat)), warna & 0xFFFFFF), null, state.outlineColor);
			}
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** The glow strength 0-1 of a phase and light. */
	static float kuat(State s) {
		float t = s.waktu;
		return switch (s.fase) {
			case PilarQiEntity.FASE_AKTIF -> (0.35F + 0.65F * s.nyala) * (0.8F + 0.2F * Mth.sin(t * 0.9F));
			case PilarQiEntity.FASE_LULUS -> 0.85F + 0.15F * Mth.sin(t * 0.12F);
			case PilarQiEntity.FASE_GAGAL -> 0.25F * (0.6F + 0.4F * Mth.sin(t * 2.7F) * Mth.sin(t * 0.83F));
			default -> 0.12F + 0.08F * Mth.sin(t * 0.08F) + 0.5F * s.nyala;
		};
	}

	// ================================================================== mesh

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("alas_bawah", GabungGambar.band(0).addBox(-7.0F, 21.0F, -7.0F, 14.0F, 3.0F, 14.0F), PartPose.ZERO);
		root.addOrReplaceChild("alas_atas", GabungGambar.band(0).addBox(-6.0F, 19.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.ZERO);
		root.addOrReplaceChild("tiang_bawah", GabungGambar.band(1).addBox(-4.5F, 2.0F, -4.5F, 9.0F, 17.0F, 9.0F), PartPose.ZERO);
		root.addOrReplaceChild("tiang_atas", GabungGambar.band(1).addBox(-4.5F, -15.0F, -4.5F, 9.0F, 17.0F, 9.0F), PartPose.ZERO);
		root.addOrReplaceChild("relief_bawah", GabungGambar.band(2).addBox(-5.0F, 13.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.ZERO);
		root.addOrReplaceChild("relief_atas", GabungGambar.band(2).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			float x = (i % 2 == 0 ? -1 : 1) * 4.6F;
			float z = (i < 2 ? -1 : 1) * 4.6F;
			root.addOrReplaceChild("rusuk_" + i, GabungGambar.band(4).addBox(x - 0.7F, -15.0F, z - 0.7F, 1.4F, 17.0F, 1.4F)
					.addBox(x - 0.7F, 2.0F, z - 0.7F, 1.4F, 17.0F, 1.4F), PartPose.ZERO);
			root.addOrReplaceChild("plakat_" + i, GabungGambar.band(3).addBox(-3.0F, -4.0F, -5.6F, 6.0F, 8.0F, 1.0F),
					PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, i * Mth.HALF_PI, 0.0F));
			root.addOrReplaceChild("prong_" + i, GabungGambar.band(4).addBox(x * 0.55F - 0.5F, -23.0F, z * 0.55F - 0.5F, 1.0F, 3.0F, 1.0F),
					PartPose.ZERO);
			root.addOrReplaceChild("aura_" + i, GabungGambar.band(6).addBox(-7.2F, -0.2F, 7.05F, 14.4F, 0.4F, 0.6F),
					PartPose.offsetAndRotation(0.0F, 20.9F, 0.0F, 0.0F, i * Mth.HALF_PI, 0.0F));
		}
		root.addOrReplaceChild("kepala_tiang", GabungGambar.band(0).addBox(-6.0F, -17.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.ZERO);
		root.addOrReplaceChild("bibir", GabungGambar.band(4).addBox(-7.0F, -19.0F, -7.0F, 14.0F, 2.0F, 14.0F), PartPose.ZERO);
		root.addOrReplaceChild("mangkuk", GabungGambar.band(4).addBox(-3.0F, -21.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
		PartDefinition bola = root.addOrReplaceChild("bola", GabungGambar.band(5).addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, -25.5F, 0.0F));
		bola.addOrReplaceChild("inti", GabungGambar.band(5).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				PartPose.rotation(Mth.PI / 4.0F, 0.0F, Mth.PI / 4.0F));
		PartDefinition cincin = root.addOrReplaceChild("cincin", GabungGambar.band(6), PartPose.offset(0.0F, -25.5F, 0.0F));
		for (int i = 0; i < 4; i++) {
			cincin.addOrReplaceChild("r" + i, GabungGambar.band(6).addBox(-4.0F, -0.25F, 4.0F, 8.0F, 0.5F, 0.8F),
					PartPose.rotation(0.0F, i * Mth.HALF_PI, 0.0F));
		}
		PartDefinition cincin2 = root.addOrReplaceChild("cincin_luar", GabungGambar.band(6), PartPose.offset(0.0F, -25.5F, 0.0F));
		for (int i = 0; i < 4; i++) {
			cincin2.addOrReplaceChild("r" + i, GabungGambar.band(6).addBox(-5.5F, -0.2F, 5.5F, 11.0F, 0.4F, 0.6F),
					PartPose.rotation(0.0F, i * Mth.HALF_PI + Mth.PI / 4.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 64, 224);
	}

	// ================================================================== model

	static final class Model extends EntityModel<State> {

		private final ModelPart bola;
		private final ModelPart cincin;
		private final ModelPart cincinLuar;

		Model(ModelPart root) {
			super(root);
			this.bola = root.getChild("bola");
			this.cincin = root.getChild("cincin");
			this.cincinLuar = root.getChild("cincin_luar");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.rinci ? state.waktu : 0.0F;
			float laju = switch (state.fase) {
				case PilarQiEntity.FASE_AKTIF -> 0.12F;
				case PilarQiEntity.FASE_LULUS -> 0.06F;
				case PilarQiEntity.FASE_GAGAL -> 0.008F;
				default -> 0.025F;
			};
			float naik = switch (state.fase) {
				case PilarQiEntity.FASE_AKTIF -> -1.4F - state.nyala * 1.2F;
				case PilarQiEntity.FASE_LULUS -> -2.2F;
				case PilarQiEntity.FASE_GAGAL -> 2.6F;
				default -> 0.0F;
			};
			bola.y += naik + Mth.sin(t * 0.07F) * 0.6F;
			bola.yRot = t * laju;
			bola.xRot = Mth.sin(t * 0.03F) * 0.3F;
			cincin.y = bola.y;
			cincinLuar.y = bola.y;
			cincin.yRot = -t * laju * 1.4F;
			cincin.xRot = 0.28F + Mth.sin(t * 0.05F) * 0.08F;
			cincinLuar.yRot = t * laju * 0.8F;
			cincinLuar.zRot = -0.22F;
			float lebar = state.fase == PilarQiEntity.FASE_LULUS ? 1.25F : state.fase == PilarQiEntity.FASE_GAGAL ? 0.6F : 1.0F;
			cincin.xScale = cincin.zScale = lebar;
			cincinLuar.xScale = cincinLuar.zScale = lebar * (state.fase == PilarQiEntity.FASE_AKTIF ? 0.9F + 0.15F * Mth.sin(t * 0.4F) : 1.0F);
		}
	}
}

package dev.kultivator.client.sekte.gabung.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.sekte.gabung.masuk.CawanUjianEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:cawan_ujian} (DESIGN 41.11.3): one bronze jue of "Tiga Cawan" on its lacquer tray: three splayed
 * legs, a waisted body cast with a taotie band, a flared rim with two capped posts, a side handle, and per cup
 * ({@link CawanUjianEntity#nomor()}) its own detail so the three read apart: cup 0 has its lid laid on the tray beside
 * it, cup 1 its lid leaning against the body, cup 2 a long pouring spout and no lid.
 * <p>
 * Two more passes on the same atlas: the liquid surface tinted with {@link CawanUjianEntity#warnaIsi()} (a slow ripple),
 * shown while full or neutralised (paler), and three wisps of steam in the liquid's colour turning and breathing above
 * the rim (full: thick; neutralised: one pale wisp; drunk: none). Minimal: no steam. Texture
 * {@code textures/entity/gabung/cawan_ujian.png} (64 x 160: bronze with taotie, dark patina, liquid, steam, lacquer).
 */
public class CawanUjianRenderer extends EntityRenderer<CawanUjianEntity, CawanUjianRenderer.State> {

	static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("cawan_ujian"), "main");
	static final ModelLayerLocation LAYER_ISI = new ModelLayerLocation(Kultivator.id("cawan_ujian"), "isi");
	static final ModelLayerLocation LAYER_UAP = new ModelLayerLocation(Kultivator.id("cawan_ujian"), "uap");
	private static final Identifier TEXTURE = Kultivator.id("textures/entity/gabung/cawan_ujian.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType TEMBUS = RenderTypes.entityTranslucent(TEXTURE);

	public static class State extends GabungGambar.State {
		int nomor;
		int fase;
		int warna = 0x6A8A3A;
	}

	private final Model model;
	private final Model isi;
	private final Model uap;

	public CawanUjianRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER), 0);
		this.isi = new Model(context.bakeLayer(LAYER_ISI), 1);
		this.uap = new Model(context.bakeLayer(LAYER_UAP), 2);
		this.shadowRadius = 0.3F;
	}

	static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, CawanUjianRenderer::mesh);
		ModelLayerRegistry.registerModelLayer(LAYER_ISI, CawanUjianRenderer::meshIsi);
		ModelLayerRegistry.registerModelLayer(LAYER_UAP, CawanUjianRenderer::meshUap);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CawanUjianEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.waktu = entity.tickCount + partialTicks + entity.nomor() * 37.0F;
		state.rinci = state.distanceToCameraSq < GabungGambar.RINCI_SQ && !EffectQuality.minimal();
		state.nomor = Mth.clamp(entity.nomor(), 0, 2);
		state.fase = entity.fase();
		state.warna = entity.warnaIsi() & 0xFFFFFF;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			GabungGambar.ruangModel(poseStack, state.yaw);
			collector.submitModel(model, state, poseStack, TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			boolean tawar = state.fase == CawanUjianEntity.FASE_TAWAR;
			if (state.fase == CawanUjianEntity.FASE_PENUH || tawar) {
				int warna = tawar ? ARGB.srgbLerp(0.6F, state.warna | 0xFF000000, 0xFFE8F0E8) : state.warna | 0xFF000000;
				collector.order(1).submitModel(isi, state, poseStack, TEMBUS, state.lightCoords, OverlayTexture.NO_OVERLAY,
						ARGB.color(tawar ? 170 : 230, warna & 0xFFFFFF), null, state.outlineColor);
				if (state.rinci) {
					int uapWarna = ARGB.srgbLerp(0.55F, state.warna | 0xFF000000, 0xFFFFFFFF);
					int alpha = tawar ? 70 : 150 + Math.round(40.0F * Mth.sin(state.waktu * 0.11F));
					collector.order(2).submitModel(uap, state, poseStack, TEMBUS, state.lightCoords, OverlayTexture.NO_OVERLAY,
							ARGB.color(alpha, tawar ? 0xF0F4F0 : uapWarna & 0xFFFFFF), null, state.outlineColor);
				}
			}
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== mesh

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("nampan", GabungGambar.band(4).addBox(-5.0F, 23.0F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.ZERO);
		for (int i = 0; i < 3; i++) {
			float a = i * Mth.TWO_PI / 3.0F + Mth.PI;
			root.addOrReplaceChild("kaki_" + i, GabungGambar.band(1).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 2.4F, 0.8F),
					PartPose.offsetAndRotation(Mth.sin(a) * 1.6F, 20.7F, Mth.cos(a) * 1.6F, Mth.cos(a) * 0.3F, 0.0F, -Mth.sin(a) * 0.3F));
		}
		root.addOrReplaceChild("dasar", GabungGambar.band(1).addBox(-2.0F, 20.2F, -2.0F, 4.0F, 0.8F, 4.0F), PartPose.ZERO);
		PartDefinition badan = root.addOrReplaceChild("badan", GabungGambar.band(0).addBox(-2.5F, 0.0F, -1.8F, 5.0F, 4.4F, 3.6F)
				.addBox(-1.8F, 0.0F, -2.5F, 3.6F, 4.4F, 5.0F), PartPose.offset(0.0F, 16.0F, 0.0F));
		badan.addOrReplaceChild("serong", GabungGambar.band(0).addBox(-1.7F, 0.0F, -1.7F, 3.4F, 4.4F, 3.4F),
				PartPose.rotation(0.0F, Mth.PI / 4.0F, 0.0F));
		badan.addOrReplaceChild("pinggang", GabungGambar.band(1).addBox(-2.7F, 3.2F, -2.0F, 5.4F, 0.6F, 4.0F)
				.addBox(-2.0F, 3.2F, -2.7F, 4.0F, 0.6F, 5.4F), PartPose.ZERO);
		root.addOrReplaceChild("bibir", GabungGambar.band(0).addBox(-3.0F, 15.3F, -2.2F, 6.0F, 0.8F, 4.4F)
				.addBox(-2.2F, 15.3F, -3.0F, 4.4F, 0.8F, 6.0F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			root.addOrReplaceChild("tiang_" + (side < 0 ? "a" : "b"), GabungGambar.band(1).addBox(-0.35F, -2.0F, -0.35F, 0.7F, 2.0F, 0.7F)
					.addBox(-0.65F, -2.7F, -0.65F, 1.3F, 0.7F, 1.3F), PartPose.offset(side * 1.3F, 15.3F, -1.5F));
		}
		root.addOrReplaceChild("pegangan", GabungGambar.band(1).addBox(0.0F, 0.0F, -0.9F, 0.8F, 3.2F, 0.6F)
				.addBox(0.0F, 0.0F, 0.3F, 0.8F, 3.2F, 0.6F).addBox(0.6F, 0.4F, -0.9F, 0.6F, 2.4F, 1.8F), PartPose.offset(2.4F, 16.6F, 0.0F));
		// cup 0: lid on the tray; cup 1: lid leaning on the body; cup 2: a long spout
		root.addOrReplaceChild("tutup_baring", GabungGambar.band(0).addBox(-1.8F, -0.6F, -1.8F, 3.6F, 0.6F, 3.6F)
				.addBox(-0.5F, -1.2F, -0.5F, 1.0F, 0.6F, 1.0F), PartPose.offset(-6.4F, 23.0F, 2.4F));
		root.addOrReplaceChild("tutup_sandar", GabungGambar.band(0).addBox(-1.8F, -0.3F, -1.8F, 3.6F, 0.6F, 3.6F)
				.addBox(-0.5F, -0.9F, -0.5F, 1.0F, 0.6F, 1.0F), PartPose.offsetAndRotation(-3.6F, 20.8F, 0.0F, 0.0F, 0.0F, 1.15F));
		root.addOrReplaceChild("paruh", GabungGambar.band(0).addBox(-0.6F, -0.5F, -3.6F, 1.2F, 0.8F, 3.6F)
				.addBox(-0.4F, -0.7F, -4.2F, 0.8F, 0.5F, 0.8F), PartPose.offsetAndRotation(0.0F, 16.0F, -2.6F, -0.22F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 160);
	}

	static LayerDefinition meshIsi() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("isi", GabungGambar.band(2).addBox(-2.1F, 0.0F, -1.5F, 4.2F, 0.2F, 3.0F)
				.addBox(-1.5F, 0.0F, -2.1F, 3.0F, 0.2F, 4.2F), PartPose.offset(0.0F, 16.25F, 0.0F));
		return LayerDefinition.create(mesh, 64, 160);
	}

	static LayerDefinition meshUap() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int i = 0; i < 3; i++) {
			root.addOrReplaceChild("uap_" + i, GabungGambar.band(3).texOffs(6, 96).addBox(-2.0F, -7.0F, 0.0F, 4.0F, 7.0F, 0.0F),
					PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, i * Mth.PI / 3.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 64, 160);
	}

	// ================================================================== model

	static final class Model extends EntityModel<State> {

		private final int jenis;
		private final ModelPart[] uap = new ModelPart[3];
		private ModelPart tutupBaring;
		private ModelPart tutupSandar;
		private ModelPart paruh;
		private ModelPart isi;

		/** jenis 0 = the cup, 1 = the liquid, 2 = the steam. */
		Model(ModelPart root, int jenis) {
			super(root);
			this.jenis = jenis;
			if (jenis == 0) {
				tutupBaring = root.getChild("tutup_baring");
				tutupSandar = root.getChild("tutup_sandar");
				paruh = root.getChild("paruh");
			} else if (jenis == 1) {
				isi = root.getChild("isi");
			} else {
				for (int i = 0; i < 3; i++) {
					uap[i] = root.getChild("uap_" + i);
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.waktu;
			if (jenis == 0) {
				tutupBaring.visible = state.nomor == 0;
				tutupSandar.visible = state.nomor == 1;
				paruh.visible = state.nomor == 2;
			} else if (jenis == 1) {
				// a slow ripple: the surface breathes a hair up and down
				isi.y += state.rinci ? Mth.sin(t * 0.13F) * 0.05F : 0.0F;
				isi.yRot = t * 0.01F;
			} else {
				boolean tawar = state.fase == CawanUjianEntity.FASE_TAWAR;
				for (int i = 0; i < 3; i++) {
					ModelPart w = uap[i];
					w.visible = !tawar || i == 0;
					float p = t * 0.05F + i * 2.1F;
					w.yRot += t * 0.02F;
					w.zRot = Mth.sin(p) * 0.12F;
					float s = 0.8F + 0.25F * Mth.sin(p * 1.3F);
					w.xScale = s;
					w.yScale = (tawar ? 0.6F : 1.0F) * (0.85F + 0.2F * Mth.sin(p * 0.7F + 1.0F));
					w.y -= (t * 0.04F + i * 0.33F) % 1.0F * 0.6F;
				}
			}
		}
	}
}

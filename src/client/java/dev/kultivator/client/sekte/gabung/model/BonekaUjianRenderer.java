package dev.kultivator.client.sekte.gabung.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.sekte.gabung.masuk.BonekaUjianEntity;
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
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code kultivator:boneka_ujian} (DESIGN 41.11.3): the straw training dummy of "Seratus Tebasan". A cross-footed base
 * of old dark wood, a post through a bound straw torso (three rope rings), a crossbar with straw arm bundles tied at the
 * ends and iron nails at the joint, a burlap sack head daubed with an ink face, its neck tied and a straw tuft on top, and
 * a yellow command talisman pinned to the chest. A second pass draws the cloth band in the sect's ink
 * ({@link BonekaUjianEntity#warna()}): a sash across the chest, a headband with two tails.
 * <p>
 * While {@link BonekaUjianEntity#aktif()} the talisman glyph burns gold and the eyes glow (glow sheet, vanilla
 * {@code eyes} type), and a ring of light on the ground shrinks over the 2 s window ({@link BonekaUjianEntity#nyalaSampai()}).
 * Each hit ({@link BonekaUjianEntity#kena()} changing) rocks the whole dummy on its feet, snaps the head and kicks the
 * arm bundles, damped over 12 ticks. Minimal: no ring, no sway. Texture {@code textures/entity/gabung/boneka_ujian.png}
 * (64 x 256, bands: wood, straw, rope, sack, talisman, cloth, glow, iron).
 */
public class BonekaUjianRenderer extends EntityRenderer<BonekaUjianEntity, BonekaUjianRenderer.State> {

	static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("boneka_ujian"), "main");
	static final ModelLayerLocation LAYER_KAIN = new ModelLayerLocation(Kultivator.id("boneka_ujian"), "kain");
	private static final Identifier TEXTURE = Kultivator.id("textures/entity/gabung/boneka_ujian.png");
	private static final Identifier PANCAR = Kultivator.id("textures/entity/gabung/boneka_ujian_pancar.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(PANCAR);
	/** Ticks a hit keeps the dummy rocking. */
	private static final float GOYANG_TICKS = 12.0F;
	/** The goyang pivot: the dummy rocks about its feet. */
	private static final float PIVOT_Y = 22.0F;

	public static class State extends GabungGambar.State {
		boolean aktif;
		/** Remaining part of the lit window, 1 -> 0. */
		float sisa;
		/** Hit wobble, 1 -> 0. */
		float goyang;
		/** Which side the last hit came from (+1 / -1), for the direction of the rock. */
		float arah = 1.0F;
		int warna = 0xC8A060;
	}

	/** Client-side hit tracking: last seen hit counter and the client tick it changed. */
	private final Map<BonekaUjianEntity, float[]> hit = new WeakHashMap<>();
	private final Model model;
	private final Model kain;

	public BonekaUjianRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER), true);
		this.kain = new Model(context.bakeLayer(LAYER_KAIN), false);
		this.shadowRadius = 0.45F;
	}

	static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, BonekaUjianRenderer::mesh);
		ModelLayerRegistry.registerModelLayer(LAYER_KAIN, BonekaUjianRenderer::meshKain);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BonekaUjianEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.waktu = entity.tickCount + partialTicks;
		state.rinci = state.distanceToCameraSq < GabungGambar.RINCI_SQ && !EffectQuality.minimal();
		state.aktif = entity.aktif();
		state.warna = entity.warna();
		long now = entity.level().getGameTime();
		long until = entity.nyalaSampai();
		state.sisa = state.aktif && until > now ? Mth.clamp((until - now - partialTicks) / (float) BonekaUjianEntity.NYALA_TICKS, 0.0F, 1.0F) : 0.0F;
		float[] h = hit.get(entity);
		int kena = entity.kena();
		if (h == null) {
			h = new float[] {kena, -100.0F, 1.0F};
			hit.put(entity, h);
		} else if ((int) h[0] != kena) {
			h[0] = kena;
			h[1] = state.waktu;
			h[2] = (kena & 1) == 0 ? 1.0F : -1.0F;
		}
		state.goyang = GabungGambar.redam(state.waktu - h[1], GOYANG_TICKS);
		state.arah = h[2];
	}

	@Override
	protected AABB getBoundingBoxForCulling(BonekaUjianEntity entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(0.8D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			GabungGambar.ruangModel(poseStack, state.yaw);
			collector.submitModel(model, state, poseStack, TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			collector.submitModel(kain, state, poseStack, TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.opaque(state.warna), null,
					state.outlineColor);
			if (state.aktif) {
				float denyut = 0.75F + 0.25F * Mth.sin(state.waktu * 0.6F);
				int alpha = Math.round(255.0F * Mth.clamp(denyut * (0.4F + 0.6F * state.sisa), 0.0F, 1.0F));
				collector.order(1).submitModel(model, state, poseStack, GLOW, state.lightCoords, OverlayTexture.NO_OVERLAY,
						ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
			}
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== mesh

	/** Adds a part to the goyang pivot with y given in absolute model space (24 = ground). */
	private static PartDefinition di(PartDefinition goyang, String name, int band, float x, float y, float z, float w, float h, float d) {
		return goyang.addOrReplaceChild(name, GabungGambar.band(band).addBox(x, y - PIVOT_Y, z, w, h, d), PartPose.ZERO);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the cross feet stay on the ground
		root.addOrReplaceChild("kaki_a", GabungGambar.band(0).addBox(-7.0F, 22.0F, -1.0F, 14.0F, 2.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("kaki_b", GabungGambar.band(0).addBox(-1.0F, 22.0F, -7.0F, 2.0F, 2.0F, 14.0F), PartPose.ZERO);
		root.addOrReplaceChild("pasak", GabungGambar.band(7).addBox(-1.5F, 21.5F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
		// the ring of light (glow band only: transparent in the base sheet)
		PartDefinition cincin = root.addOrReplaceChild("cincin", GabungGambar.band(6), PartPose.offset(0.0F, 23.85F, 0.0F));
		for (int i = 0; i < 8; i++) {
			cincin.addOrReplaceChild("r" + i, GabungGambar.band(6).addBox(-3.2F, -0.1F, 8.6F, 6.4F, 0.2F, 1.2F),
					PartPose.rotation(0.0F, i * Mth.PI / 4.0F, 0.0F));
		}
		PartDefinition goyang = root.addOrReplaceChild("goyang", GabungGambar.band(0), PartPose.offset(0.0F, PIVOT_Y, 0.0F));
		di(goyang, "tiang", 0, -1.0F, 1.0F, -1.0F, 2.0F, 21.0F, 2.0F);
		di(goyang, "badan", 1, -4.0F, 3.0F, -2.5F, 8.0F, 10.0F, 5.0F);
		di(goyang, "badan_bawah", 1, -3.0F, 13.0F, -2.0F, 6.0F, 3.0F, 4.0F);
		for (int i = 0; i < 3; i++) {
			di(goyang, "ikat_" + i, 2, -4.3F, 4.0F + i * 3.6F, -2.8F, 8.6F, 1.0F, 5.6F);
		}
		di(goyang, "palang", 0, -8.0F, 4.0F, -1.0F, 16.0F, 2.0F, 2.0F);
		di(goyang, "paku", 7, -1.3F, 4.3F, -1.4F, 2.6F, 1.4F, 0.4F);
		di(goyang, "jimat", 4, -2.0F, 5.4F, -3.3F, 4.0F, 6.0F, 1.0F);
		di(goyang, "leher", 2, -1.8F, 1.6F, -1.8F, 3.6F, 1.2F, 3.6F);
		PartDefinition kepala = goyang.addOrReplaceChild("kepala", GabungGambar.band(3), PartPose.offset(0.0F, 2.0F - PIVOT_Y, 0.0F));
		kepala.addOrReplaceChild("karung", GabungGambar.band(3).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("rumbai", GabungGambar.band(1).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 2.0F, 2.0F)
				.addBox(-1.8F, -7.2F, -0.4F, 0.8F, 1.4F, 0.8F).addBox(1.0F, -7.4F, 0.2F, 0.8F, 1.6F, 0.8F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "kanan" : "kiri";
			PartDefinition lengan = goyang.addOrReplaceChild("lengan_" + s, GabungGambar.band(1),
					PartPose.offset(side * 7.0F, 5.5F - PIVOT_Y, 0.0F));
			lengan.addOrReplaceChild("ikat", GabungGambar.band(2).addBox(-1.7F, -0.6F, -1.7F, 3.4F, 1.2F, 3.4F), PartPose.ZERO);
			lengan.addOrReplaceChild("jerami", GabungGambar.band(1).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F)
					.addBox(-1.0F, 7.0F, -1.0F, 2.0F, 1.5F, 2.0F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 256);
	}

	/** The sect's cloth: sash across the chest, headband and its two tails (band 5, greyscale, tinted). */
	static LayerDefinition meshKain() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition goyang = root.addOrReplaceChild("goyang", GabungGambar.band(5), PartPose.offset(0.0F, PIVOT_Y, 0.0F));
		goyang.addOrReplaceChild("selempang", GabungGambar.band(5).addBox(-5.4F, -0.9F, -3.0F, 10.8F, 1.8F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 8.0F - PIVOT_Y, 0.0F, 0.0F, 0.0F, 0.62F));
		goyang.addOrReplaceChild("sabuk", GabungGambar.band(5).addBox(-4.4F, 0.0F, -2.9F, 8.8F, 1.4F, 5.8F),
				PartPose.offset(0.0F, 12.2F - PIVOT_Y, 0.0F));
		PartDefinition kepala = goyang.addOrReplaceChild("kepala", GabungGambar.band(5), PartPose.offset(0.0F, 2.0F - PIVOT_Y, 0.0F));
		kepala.addOrReplaceChild("ikat_kepala", GabungGambar.band(5).addBox(-3.3F, -4.6F, -3.3F, 6.6F, 1.2F, 6.6F), PartPose.ZERO);
		kepala.addOrReplaceChild("ekor_a", GabungGambar.band(5).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.5F, 0.4F),
				PartPose.offsetAndRotation(-0.8F, -4.2F, 3.3F, 0.35F, 0.0F, 0.12F));
		kepala.addOrReplaceChild("ekor_b", GabungGambar.band(5).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.0F, 0.4F),
				PartPose.offsetAndRotation(0.8F, -4.2F, 3.3F, 0.45F, 0.0F, -0.15F));
		return LayerDefinition.create(mesh, 64, 256);
	}

	// ================================================================== model

	static final class Model extends EntityModel<State> {

		private final ModelPart goyang;
		private final ModelPart kepala;
		private final ModelPart lenganKanan;
		private final ModelPart lenganKiri;
		private final ModelPart cincin;
		private final ModelPart ekorA;
		private final ModelPart ekorB;

		Model(ModelPart root, boolean utama) {
			super(root);
			this.goyang = root.getChild("goyang");
			this.kepala = goyang.getChild("kepala");
			this.lenganKanan = utama ? goyang.getChild("lengan_kanan") : null;
			this.lenganKiri = utama ? goyang.getChild("lengan_kiri") : null;
			this.cincin = utama ? root.getChild("cincin") : null;
			this.ekorA = utama ? null : kepala.getChild("ekor_a");
			this.ekorB = utama ? null : kepala.getChild("ekor_b");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float g = state.goyang;
			float t = state.waktu;
			// a hit rocks the dummy on its feet (two quick swings), the head snaps a beat later
			float rock = g * Mth.sin(t * 1.9F) * 0.22F * state.arah;
			float idle = state.rinci ? Mth.sin(t * 0.05F) * 0.012F : 0.0F;
			goyang.zRot = rock + idle;
			goyang.xRot = g * Mth.sin(t * 1.4F + 0.6F) * 0.08F;
			kepala.zRot = g * Mth.sin(t * 2.3F - 0.8F) * 0.35F * state.arah;
			kepala.xRot = g * 0.18F;
			if (lenganKanan != null) {
				float swing = g * Mth.sin(t * 2.0F) * 0.6F;
				float sway = state.rinci ? Mth.sin(t * 0.07F) * 0.05F : 0.0F;
				lenganKanan.zRot = swing + sway;
				lenganKiri.zRot = -swing - sway;
				lenganKanan.xRot = g * 0.25F;
				lenganKiri.xRot = -g * 0.25F;
				cincin.visible = state.aktif && state.sisa > 0.0F && state.rinci;
				float s = 0.35F + 0.65F * state.sisa;
				cincin.xScale = s;
				cincin.zScale = s;
				cincin.yRot = t * 0.05F;
			} else {
				float flutter = state.rinci ? Mth.sin(t * 0.25F) * 0.15F : 0.0F;
				ekorA.xRot = 0.35F + flutter + g * 0.6F;
				ekorB.xRot = 0.45F - flutter * 0.8F + g * 0.5F;
			}
		}
	}
}

package dev.kultivator.client.sekte.turnamen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.dimensi.render.Headwear;
import dev.kultivator.client.render.CultivatorModels;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.sekte.turnamen.Penonton;
import dev.kultivator.sosok.Sosok;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a visiting spectator of the sect tournament (DESIGN 36.2.3): a default player skin (nine characters, wide or slim
 * arms), the cultivator robe tinted with its sect's field colour and trimmed with its border colour (envoys) or in plain
 * homespun (villagers), and its head piece ({@link PenontonModels}). Light by design (36.3.9): beyond 32 blocks it stands
 * still (no cheer, no breathing), beyond 64 it is not drawn at all; no item in the hands, no name tag unless looked at.
 * <p>
 * DESIGN 40.6.1 (module 40R): a spectator never gets an atlas of its own. It wears one of the ready class fallback
 * atlases of Sosok (40K, {@code textures/sosok/cadangan/<kelamin>_<usia>_<kelas>.png}; envoys {@code kultivator},
 * villagers {@code sipil}) on the Sosok mesh, chosen once per spectator from a client-made Sosok
 * ({@code penonton.utusan} / {@code penonton.warga}: old slim arms = woman, villagers of every age, children drawn
 * small), so a stand of 160 spectators never queues a composition; the robe in the sect's colours and the head piece stay
 * on top, the cheer stays the old one. Without the fallback atlases the old default skins return.
 */
public class PenontonRenderer extends LivingEntityRenderer<Penonton, PenontonRenderState, PlayerModel> {

	private static final float PLAYER_SCALE = 0.9375F;
	private static final double ANIMATE_DISTANCE_SQ = 32.0D * 32.0D;
	private static final double DRAW_DISTANCE_SQ = 64.0D * 64.0D;
	private static final String[] CHARACTERS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};
	private static final Identifier[] WIDE = new Identifier[CHARACTERS.length];
	private static final Identifier[] SLIM = new Identifier[CHARACTERS.length];
	/** Per entity id: the cheer counter last seen and the age when the cheer began. */
	private static final Map<Integer, float[]> CHEERS = new HashMap<>();
	/** True when the state wears a Sosok fallback atlas (the 128 × 128 mesh draws it). */
	private static final RenderStateDataKey<Boolean> SOSOK = RenderStateDataKey.create(() -> "kultivator:penonton_sosok");
	/** The Sosok height of a spectator (the server never sets its scale attribute). */
	private static final RenderStateDataKey<Float> SKALA = RenderStateDataKey.create(() -> "kultivator:penonton_skala");
	/** Per entity id: the fallback look, computed once from the spectator's fixed fields. */
	private static final Map<Integer, Rupa> RUPA = new HashMap<>();

	/** One spectator's fallback look and the fields it was made from. */
	private record Rupa(int skin, boolean slimOld, int kind, @Nullable Identifier texture, boolean slim, float skala) {
	}

	static {
		for (int i = 0; i < CHARACTERS.length; i++) {
			WIDE[i] = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/wide/" + CHARACTERS[i])).texturePath();
			SLIM[i] = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/slim/" + CHARACTERS[i])).texturePath();
		}
	}

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;
	/** The Sosok mesh (128 × 128) for the fallback atlases. */
	private final PlayerModel sosokWide;
	private final PlayerModel sosokSlim;

	public PenontonRenderer(EntityRendererProvider.Context context) {
		super(context, new PenontonModels.Body(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PenontonModels.Body(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.sosokWide = new PenontonModels.Body(SosokJembatan.tubuh(context, false), false);
		this.sosokSlim = new PenontonModels.Body(SosokJembatan.tubuh(context, true), true);
		this.addLayer(new RobeLayer(this, context));
		this.addLayer(new HatLayer(this, context));
	}

	@Override
	public PenontonRenderState createRenderState() {
		return new PenontonRenderState();
	}

	@Override
	public boolean shouldRender(Penonton entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return entity.distanceToSqr(camX, camY, camZ) <= DRAW_DISTANCE_SQ && super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	public void extractRenderState(Penonton entity, PenontonRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.kind = entity.kind();
		state.slim = entity.slim();
		state.hat = entity.hat();
		state.robe = entity.robe();
		state.trim = entity.trim();
		int skin = Math.max(0, Math.min(CHARACTERS.length - 1, entity.skin()));
		state.texture = state.slim ? SLIM[skin] : WIDE[skin];
		Rupa rupa = rupa(entity, skin);
		FabricRenderState data = (FabricRenderState) state;
		if (rupa.texture() != null) {
			state.texture = rupa.texture();
			state.slim = rupa.slim();
			data.setData(SOSOK, Boolean.TRUE);
			data.setData(SKALA, rupa.skala());
		} else {
			data.setData(SOSOK, Boolean.FALSE);
			data.setData(SKALA, 1.0F);
		}
		state.id = entity.getId();
		state.showCape = false;
		state.animate = state.distanceToCameraSq <= ANIMATE_DISTANCE_SQ;
		state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		float age = entity.tickCount + partialTicks;
		float[] seen = CHEERS.get(entity.getId());
		int count = entity.cheers();
		if (seen == null) {
			if (CHEERS.size() > 64) {
				CHEERS.clear();
			}
			seen = new float[]{count, -1000.0F};
			CHEERS.put(entity.getId(), seen);
		} else if ((int) seen[0] != count) {
			seen[0] = count;
			seen[1] = age;
		}
		float since = age - seen[1];
		state.cheer = since >= 0.0F && since <= PenontonModels.CHEER_TICKS ? since : -1.0F;
	}

	/**
	 * The fallback look of a spectator (DESIGN 40.6.1: never an atlas of its own): its client-made Sosok, the class
	 * fallback atlas of its sex, age and class, slim arms by its sex, its height. Made once per spectator; the cache
	 * forgets everything past 256 entries (a stand holds at most 160).
	 */
	private static Rupa rupa(Penonton entity, int skin) {
		Rupa rupa = RUPA.get(entity.getId());
		if (rupa != null && rupa.skin() == skin && rupa.slimOld() == entity.slim() && rupa.kind() == entity.kind()) {
			return rupa;
		}
		boolean envoy = entity.kind() == Penonton.KIND_ENVOY;
		String key = envoy ? "penonton.utusan" : "penonton.warga";
		Sosok sosok = SosokRender.dariLama(key, skin * 2 + (entity.slim() ? 1 : 0), entity.slim() ? 1 : 0, entity.getUUID());
		Identifier texture = SosokRender.cadangan(sosok.kelamin(), sosok.usia(), envoy ? "kultivator" : "sipil");
		rupa = new Rupa(skin, entity.slim(), entity.kind(), texture, sosok.lenganRamping(), sosok.skala());
		if (RUPA.size() > 256) {
			RUPA.clear();
		}
		RUPA.put(entity.getId(), rupa);
		return rupa;
	}

	@Override
	public void submit(PenontonRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.level.CameraRenderState camera) {
		if (((FabricRenderState) state).getDataOrDefault(SOSOK, Boolean.FALSE)) {
			this.model = state.slim ? this.sosokSlim : this.sosokWide;
		} else {
			this.model = state.slim ? this.slimModel : this.wideModel;
		}
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected void scale(PenontonRenderState state, PoseStack poseStack) {
		float scale = PLAYER_SCALE * ((FabricRenderState) state).getDataOrDefault(SKALA, 1.0F);
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(PenontonRenderState state) {
		return state.texture;
	}

	/** The robe: the cultivator robe layers in the spectator's colours (trim only for envoys). */
	private static final class RobeLayer extends RenderLayer<PenontonRenderState, PlayerModel> {

		private static final Identifier BASE = Kultivator.id("textures/entity/robe_base.png");
		private static final Identifier TRIM = Kultivator.id("textures/entity/robe_rank_trim.png");
		private static final RenderType BASE_TYPE = RenderTypes.entityTranslucent(BASE);
		private static final RenderType TRIM_TYPE = RenderTypes.entityTranslucent(TRIM);

		private final PenontonModels.Robe wide;
		private final PenontonModels.Robe slim;

		RobeLayer(RenderLayerParent<PenontonRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
			super(parent);
			this.wide = new PenontonModels.Robe(context.bakeLayer(CultivatorModels.ROBE), false);
			this.slim = new PenontonModels.Robe(context.bakeLayer(CultivatorModels.ROBE_SLIM), true);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, PenontonRenderState state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			PenontonModels.Robe robe = state.slim ? slim : wide;
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			collector.submitModel(robe, state, poseStack, BASE_TYPE, light, overlay, ARGB.opaque(state.robe), null, state.outlineColor);
			if (state.kind == Penonton.KIND_ENVOY) {
				collector.order(1).submitModel(robe, state, poseStack, TRIM_TYPE, light, overlay, ARGB.opaque(state.trim), null,
						state.outlineColor);
			}
		}
	}

	/** The head piece: the caping (villagers, when they wear one) or the guan (envoys). */
	private static final class HatLayer extends RenderLayer<PenontonRenderState, PlayerModel> {

		private static final RenderType TYPE = RenderTypes.entityCutout(Headwear.TEXTURE);

		private final PenontonModels.Piece caping;
		private final PenontonModels.Piece guan;

		HatLayer(RenderLayerParent<PenontonRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
			super(parent);
			this.caping = new PenontonModels.Piece(context.bakeLayer(PenontonModels.CAPING));
			this.guan = new PenontonModels.Piece(context.bakeLayer(PenontonModels.GUAN));
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, PenontonRenderState state, float yRot, float xRot) {
			if (state.isInvisible || !state.hat && state.kind != Penonton.KIND_ENVOY) {
				return;
			}
			PenontonModels.Piece piece = state.kind == Penonton.KIND_ENVOY ? guan : caping;
			collector.submitModel(piece, state, poseStack, TYPE, light, LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null,
					state.outlineColor);
		}
	}
}

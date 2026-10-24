package dev.kultivator.client.dunia.kota;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the {@link KotaGear} outfit of a {@code kultivator_npc}: the fixed materials, then the cloth tinted for this NPC.
 * The gear models are PlayerModels, so the walk, the attack swing, the meditation seat and the cast, duel and body-language
 * poses move them with the body. The sheathed sword's hilt hides while the blade is in hand. Dropped beyond 32 blocks
 * (16 on Minimal effects), like the robe (DESIGN 34.0 rule V3); a ghost's gear is see-through.
 * <p>
 * Penampilan dan Animasi (DESIGN 40.4.5, module 40R): on a Sosok body the gear's own hair pieces (buns, pins, knots,
 * guan, beards, brows of the old outfits; table {@code SosokGear.SEMBUNYI} of 40K, which 38MD extends for its bands) are
 * hidden, because Sosok draws that hair itself in the body's colours; hats, helmets, belts and tools stay. The gear is
 * not drawn at the Siluet and Jauh levels of detail (the atlas already paints the outfit's cloth).
 */
public class KotaGearLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	public static final RenderStateDataKey<Integer> KIND = RenderStateDataKey.create(() -> "kultivator:kota_gear");
	public static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "kultivator:kota_gear_tint");
	private static final double FAR_SQ = 32.0D * 32.0D;

	private static final RenderType SOLID = RenderTypes.entityCutout(KotaGear.TEXTURE);
	private static final RenderType SEE_THROUGH = RenderTypes.entityTranslucent(KotaGear.TEXTURE);

	private final GearModel[] solid = new GearModel[KotaGear.KINDS];
	private final GearModel[] cloth = new GearModel[KotaGear.KINDS];

	public KotaGearLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		for (int i = 1; i < KotaGear.KINDS; i++) {
			String[] hidden = SosokJembatan.sembunyi(i);
			solid[i] = new GearModel(context.bakeLayer(KotaGear.solid(i)), hidden);
			cloth[i] = new GearModel(context.bakeLayer(KotaGear.cloth(i)), hidden);
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		double limit = EffectQuality.minimal() ? FAR_SQ / 4.0D : FAR_SQ;
		if (state.distanceToCameraSq > limit) {
			return;
		}
		if (SosokRender.aktif(state)) {
			SosokDetail.Lod lod = SosokDetail.lod(state);
			if (lod == SosokDetail.Lod.SILUET || lod == SosokDetail.Lod.JAUH) {
				return;
			}
		}
		FabricRenderState data = (FabricRenderState) state;
		int kind = data.getDataOrDefault(KIND, KotaGear.NONE);
		if (kind <= KotaGear.NONE || kind >= KotaGear.KINDS || solid[kind] == null) {
			return;
		}
		int flags = data.getDataOrDefault(NpcRenderer.NPC_FLAGS, 0);
		boolean ghost = (flags & KultivatorNpc.FLAG_GHOST) != 0;
		RenderType type = ghost ? SEE_THROUGH : SOLID;
		int alpha = ghost ? 150 : 255;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		collector.order(3).submitModel(solid[kind], state, poseStack, type, lightCoords, overlay, ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
		int tint = data.getDataOrDefault(TINT, 0xFFD0D0D0);
		collector.order(3).submitModel(cloth[kind], state, poseStack, type, lightCoords, overlay, ARGB.color(alpha, tint & 0xFFFFFF), null,
				state.outlineColor);
	}

	/**
	 * An outfit on the player skeleton; its sword hilts hide while the right hand holds a blade, and its hair pieces hide
	 * on a Sosok body (DESIGN 40.4.5). {@code visible} is not part of the reset pose, so both are written every frame.
	 */
	static final class GearModel extends PlayerModel {

		private final List<ModelPart> hilts = new ArrayList<>();
		private final List<ModelPart> hair = new ArrayList<>();

		GearModel(ModelPart root, String[] hiddenOnSosok) {
			super(root, false);
			java.util.function.Function<String, ModelPart> parts = root.createPartLookup();
			for (String name : hiddenOnSosok) {
				ModelPart part = parts.apply(name);
				if (part != null && part != root && !hair.contains(part)) {
					hair.add(part);
				}
			}
			// the hip sword and the back sword are both "scabbard" on the body; their hilts are children named HILT
			if (this.body.hasChild("scabbard")) {
				ModelPart sheath = this.body.getChild("scabbard");
				if (sheath.hasChild(KotaGear.HILT)) {
					hilts.add(sheath.getChild(KotaGear.HILT));
				}
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			boolean drawn = !state.rightHandItemState.isEmpty();
			for (ModelPart hilt : hilts) {
				hilt.visible = !drawn;
			}
			boolean sosok = SosokRender.aktif(state);
			for (ModelPart piece : hair) {
				piece.visible = !sosok;
			}
		}
	}
}

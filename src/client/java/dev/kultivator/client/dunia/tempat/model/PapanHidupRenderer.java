package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.function.ToIntFunction;

/**
 * Papan Hidup ({@code kultivator:papan_hidup}, DESIGN 38.O.2): the living notice board of a town (rumours, wanted
 * posters, guild missions). A dark-wood frame 32 × 20 on two posts, a small tiled roof 36 × 2 × 6 with upturned ends, and
 * up to six paper sheets pinned on its face. Within 32 blocks the sheets flutter (a sine on each sheet's lower edge); a
 * new sheet is "pasted" (scale 0 → 1 over 6 ticks). The entity type and the board's content belong to 38UI: it
 * registers this renderer for its type with readers of the sheet count and of the ticks since the last change, and draws
 * the sheet texts in {@link #submitIsi} (world {@code Font}, 38.U) by subclassing. Texture
 * {@code textures/entity/tempat/papan_hidup.png} (64 × 96: old wood, written paper, roof tiles).
 */
public class PapanHidupRenderer<T extends Entity> extends BendaRenderer<T, PapanHidupRenderer.State, PapanHidupRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("papan_hidup"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/papan_hidup.png");
	public static final int MAX_SHEETS = 6;

	private final ToIntFunction<T> sheets;
	private final ToIntFunction<T> sinceChange;

	public static class State extends BendaRenderer.State {
		public int sheets;
		public float paste = 1.0F;
		public boolean near;
	}

	/** A board with four sheets and no paste animation. */
	public PapanHidupRenderer(EntityRendererProvider.Context context) {
		this(context, e -> 4, e -> Integer.MAX_VALUE);
	}

	/** A board whose sheet count (0-6) and ticks since its last change come from the entity (38UI). */
	public PapanHidupRenderer(EntityRendererProvider.Context context, ToIntFunction<T> sheets, ToIntFunction<T> sinceChange) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, null, 0.3F);
		this.sheets = sheets;
		this.sinceChange = sinceChange;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PapanHidupRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(T entity) {
		return 1.4D;
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.sheets = Mth.clamp(sheets.applyAsInt(entity), 0, MAX_SHEETS);
		int since = sinceChange.applyAsInt(entity);
		state.paste = since >= 6 ? 1.0F : Mth.clamp((since + partialTicks) / 6.0F, 0.0F, 1.0F);
		state.near = state.distanceToCameraSq < 32.0D * 32.0D;
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		submitIsi(state, poseStack, collector);
	}

	/** The sheet texts (model space, front = -z, board face at z = -1.2); drawn by 38UI's subclass. */
	protected void submitIsi(State state, PoseStack poseStack, SubmitNodeCollector collector) {
	}

	/** Sheet positions on the board face: x, y (model px, top-left), width, height. */
	static final float[][] SHEET = {
			{-14.0F, -12.0F, 7.0F, 9.0F}, {-6.0F, -13.0F, 6.0F, 8.0F}, {1.0F, -12.5F, 7.0F, 9.0F}, {9.0F, -12.0F, 5.0F, 7.0F},
			{-12.0F, -2.5F, 8.0F, 6.0F}, {3.0F, -3.0F, 8.0F, 6.5F}};

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition board = root.addOrReplaceChild("board", CubeListBuilder.create(), PartPose.offset(0.0F, 8.0F, 0.0F));
		board.addOrReplaceChild("panel", band(0).addBox(-16.0F, -14.0F, -1.0F, 16.0F, 18.0F, 2.0F).addBox(0.0F, -14.0F, -1.0F, 16.0F, 18.0F, 2.0F),
				PartPose.ZERO);
		board.addOrReplaceChild("frame", band(0).addBox(-17.0F, -15.0F, -1.6F, 17.0F, 1.5F, 3.2F).addBox(0.0F, -15.0F, -1.6F, 17.0F, 1.5F, 3.2F)
				.addBox(-17.0F, 4.0F, -1.6F, 17.0F, 1.5F, 3.2F).addBox(0.0F, 4.0F, -1.6F, 17.0F, 1.5F, 3.2F), PartPose.ZERO);
		board.addOrReplaceChild("posts", band(0).addBox(-18.0F, -14.0F, -1.5F, 2.0F, 30.0F, 2.0F).addBox(16.0F, -14.0F, -1.5F, 2.0F, 30.0F, 2.0F),
				PartPose.ZERO);
		board.addOrReplaceChild("roof", band(2).addBox(-18.0F, -18.0F, -3.0F, 18.0F, 2.0F, 6.0F).addBox(0.0F, -18.0F, -3.0F, 18.0F, 2.0F, 6.0F)
				.addBox(-4.0F, -19.5F, -2.0F, 8.0F, 1.5F, 4.0F), PartPose.ZERO);
		board.addOrReplaceChild("roof_ends", band(2).addBox(-20.0F, -19.0F, -3.0F, 2.5F, 1.5F, 6.0F).addBox(17.5F, -19.0F, -3.0F, 2.5F, 1.5F, 6.0F),
				PartPose.ZERO);
		for (int i = 0; i < MAX_SHEETS; i++) {
			float[] s = SHEET[i];
			// pivot on the sheet's top edge so it flutters from its pin and pastes from the top
			PartDefinition sheet = board.addOrReplaceChild("sheet_" + i, CubeListBuilder.create().texOffs(i * 4, 32 + (i % 3) * 3)
					.addBox(-s[2] / 2.0F, 0.0F, -0.1F, s[2], s[3], 0.1F), PartPose.offset(s[0] + s[2] / 2.0F, s[1], -1.15F));
			sheet.addOrReplaceChild("pin", band(2).addBox(-0.4F, -0.2F, -0.5F, 0.8F, 0.8F, 0.5F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 96);
	}

	/** The board's model: sheet count, flutter and paste. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart[] sheets = new ModelPart[MAX_SHEETS];

		public Model(ModelPart root) {
			super(root);
			ModelPart board = root.getChild("board");
			for (int i = 0; i < MAX_SHEETS; i++) {
				sheets[i] = board.getChild("sheet_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			for (int i = 0; i < MAX_SHEETS; i++) {
				ModelPart sheet = sheets[i];
				sheet.visible = i < state.sheets;
				if (state.near) {
					sheet.xRot = (Mth.sin(state.ageInTicks * 0.21F + i * 1.7F) * 0.5F + 0.5F) * -0.12F;
				}
				if (i == state.sheets - 1 && state.paste < 1.0F) {
					float k = Math.max(0.01F, state.paste);
					sheet.xScale = k;
					sheet.yScale = k;
				}
			}
		}
	}
}

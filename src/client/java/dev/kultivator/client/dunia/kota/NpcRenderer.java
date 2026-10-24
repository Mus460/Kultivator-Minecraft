package dev.kultivator.client.dunia.kota;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.RupaKunci;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a {@code kultivator_npc} with the disciple's player model (DESIGN 34.5.1, built like {@code RogueRenderer}): one
 * of the default skins by look seed, the disciple robe tinted by role (or the rival group's colour), its held item, the
 * cross-legged pose while seated (teahouse wanderers), a soft see-through look for ghosts. The robe is dropped beyond
 * 32 blocks (16 on Minimal effects); the name plate (role title and name) shows within 16 blocks, the realm and Dao
 * line under it within 10. Immortals wear the white-gold robe and a faint luminous tint.
 * <p>
 * Detail pass: the role's {@link KotaGear} outfit ({@link KotaGearLayer}), the cast and duel poses of the 6c engine and
 * DESIGN 35 ({@code PoseRenderData.extract}, the duel module's obligation of 35.7.6), otherwise the role's body language
 * ({@link NpcPoses}), and the speech bubble of what it says (within 12 blocks, above the plate).
 * <p>
 * Penampilan dan Animasi (DESIGN 40.7.2, module 40R): the NPC is a Sosok like a disciple. Its look key is the one the
 * server set ({@code DATA_RUPA}: 38's sect members, test galleries) or its role's id ({@code juru_lelang}, ...; 40K falls
 * back to the schedule group's row); a {@code sekte=} part brings that sect's uniform. The atlas, the busana and hair
 * cubes, the face and the clip props replace the old default skin and robe (the robe stays for the old-skin fallback
 * only); the KotaGear outfit stays, minus the hair pieces Sosok now draws ({@link KotaGearLayer}). The role's body
 * language is Gerak's (40G maps the flags and roles to clips), after the cast and duel extract.
 */
public class NpcRenderer extends LivingEntityRenderer<KultivatorNpc, DiscipleRenderState, PlayerModel> {

	static final RenderStateDataKey<Integer> NPC_FLAGS = RenderStateDataKey.create(() -> "kultivator:kota_npc_flags");
	static final RenderStateDataKey<Integer> NPC_ROLE = RenderStateDataKey.create(() -> "kultivator:kota_npc_role");
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;
	private static final double BUBBLE_DISTANCE_SQ = 12.0D * 12.0D;
	static final RenderStateDataKey<Component> NPC_LINE = RenderStateDataKey.create(() -> "kultivator:kota_npc_line");
	static final RenderStateDataKey<Vec3> NPC_LINE_AT = RenderStateDataKey.create(() -> "kultivator:kota_npc_line_at");

	/** {@code NpcRole.id()} by ordinal (no lower-casing per frame). */
	private static String[] roleIds;

	/** The Sosok body (128 × 128 atlas mesh). */
	private final PlayerModel sosokWide;
	private final PlayerModel sosokSlim;
	/** The old player-skin body (last fallback when no atlas exists). */
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public NpcRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(SosokJembatan.tubuh(context, false), false), 0.5F);
		this.sosokWide = this.model;
		this.sosokSlim = new PlayerModel(SosokJembatan.tubuh(context, true), true);
		this.wideModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new SosokRender.TanpaSosok(this, new CulledRobe(this, new DiscipleRobeLayer(this, context))));
		SosokRender.pasangLapisan(this::addLayer, this, context);
		this.addLayer(new KotaGearLayer(this, context));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(KultivatorNpc entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		int look = entity.look();
		boolean slim = entity.female() || Math.floorMod(look >>> 13, 4) == 0;
		PlayerSkin skin = DiscipleLook.skinByIndex(Math.floorMod(look, 9), slim);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		NpcRole role = entity.role();
		// the robe's gold trims and emblem follow the realm (Mortal townsfolk wear a plain Qi Refining robe)
		state.realm = Mth.clamp(Math.max(1, entity.realm()), 1, 8);
		state.layer = 0;
		state.stage = entity.stage();
		state.dao = entity.dao() == null ? -1 : entity.dao().ordinal();
		state.rootColor = entity.robe();
		state.flags = 0;
		state.robeColor = entity.robe();
		state.name = entity.plate();
		// RIVAL: plain gold trims from Golden Core and the emblem from Nascent Soul (no sect-rank trims)
		state.role = DiscipleEntity.Role.RIVAL.ordinal();
		if (entity.hasFlag(KultivatorNpc.FLAG_GHOST)) {
			state.role = DiscipleEntity.Role.PHANTOM.ordinal();
		}
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		float seated = entity.hasFlag(KultivatorNpc.FLAG_SITTING) ? 1.0F : 0.0F;
		state.seated = seated;
		if (seated > 0.0F) {
			state.walkAnimationSpeed = 0.0F;
		}
		state.natalItem.clear();
		FabricRenderState data = (FabricRenderState) state;
		data.setData(MeditationPose.STRENGTH, seated);
		data.setData(NPC_FLAGS, entity.flags());
		data.setData(NPC_ROLE, role.ordinal());
		int gear = KotaGear.kindOf(entity);
		data.setData(KotaGearLayer.KIND, gear);
		data.setData(KotaGearLayer.TINT, KotaGear.tint(gear, entity.robe()));
		// DESIGN 40: the Sosok of this NPC (look key, sect uniform, atlas, LOD, slim arms, sleep)
		RupaKunci rupa = SosokRender.rupaDari(entity);
		String key = rupa.kosong() ? roleId(role) : SosokRender.teksRupaDari(entity, rupa);
		LambangCache.Entry uniform = rupa.sekte() == null ? null : LambangCache.get(rupa.sekte());
		SosokRender.ekstrak(entity, state, partialTicks, new SosokRender.RupaMasukan(key, Math.max(0, entity.realm()),
				rupa.dao() >= 0 ? rupa.dao() : state.dao, Math.max(0, rupa.pangkat()), 0, uniform, gear, 0,
				entity.hasFlag(KultivatorNpc.FLAG_GHOST)));
		// a cast or duel pose wins (6c, DESIGN 35); otherwise Gerak (the role's body language, clips, walk, face)
		PoseRenderData.extract(entity, state, partialTicks);
		SosokRender.selesai(entity, state, partialTicks);
		SosokRender.labelLod(entity, state, partialTicks);
		Component line = entity.line();
		// always written: a render state may be reused from the last frame
		boolean speaking = !line.getString().isEmpty() && state.distanceToCameraSq <= BUBBLE_DISTANCE_SQ && !state.isInvisible;
		data.setData(NPC_LINE, speaking ? line : Component.empty());
		if (speaking) {
			Vec3 at = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
			if (at != null) {
				data.setData(NPC_LINE_AT, at);
			}
		}
	}

	private static String roleId(NpcRole role) {
		String[] ids = roleIds;
		if (ids == null) {
			NpcRole[] roles = NpcRole.values();
			ids = new String[roles.length];
			for (int i = 0; i < roles.length; i++) {
				ids[i] = roles[i].id();
			}
			roleIds = ids;
		}
		return ids[role.ordinal()];
	}

	@Override
	protected void submitNameDisplay(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submitNameDisplay(state, poseStack, collector, camera);
		FabricRenderState data = (FabricRenderState) state;
		Component line = data.getData(NPC_LINE);
		Vec3 at = data.getData(NPC_LINE_AT);
		if (line == null || at == null || line.getString().isEmpty()) {
			return;
		}
		// the speech bubble: one line above the plate (and the realm line), in warm italics between quotes
		int rows = (state.nameTag != null ? 1 : 0) + (state.scoreText != null ? 1 : 0);
		poseStack.pushPose();
		poseStack.translate(0.0F, rows * 9.0F * 1.15F * 0.025F + 0.08F, 0.0F);
		Component bubble = Component.literal("“").append(line).append("”").withStyle(style -> style.withItalic(true).withColor(0xFFF2D6));
		collector.submitNameTag(poseStack, at, 0, bubble, !state.isDiscrete, state.lightCoords, camera);
		poseStack.popPose();
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int role = ((FabricRenderState) state).getDataOrDefault(NPC_ROLE, 0);
		int flags = ((FabricRenderState) state).getDataOrDefault(NPC_FLAGS, 0);
		if ((flags & KultivatorNpc.FLAG_GHOST) != 0) {
			return ARGB.color(150, 210, 230, 255);
		}
		if (NpcRole.byOrdinal(role).immortal()) {
			return ARGB.color(255, 255, 250, 238);
		}
		return -1;
	}

	@Override
	protected @Nullable RenderType getRenderType(DiscipleRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		int flags = ((FabricRenderState) state).getDataOrDefault(NPC_FLAGS, 0);
		if (isBodyVisible && (flags & KultivatorNpc.FLAG_GHOST) != 0) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		// DESIGN 40.6.3: vanilla entity cutout for a Sosok body
		if (isBodyVisible && !forceTransparent && SosokRender.aktif(state)) {
			return RenderTypes.entityCutout(this.getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected void scale(DiscipleRenderState state, PoseStack poseStack) {
		// the body size is the synced Attributes.SCALE (children 0.66, 40I); this is the player proportion on top
		float scale = PLAYER_SCALE * SosokRender.skalaGambar(state);
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		MeditationPose.lower(state, poseStack);
		dev.kultivator.client.anim.PoseApplier.lift(state, poseStack);
		SosokRender.angkat(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		SosokRender.baring(state, poseStack);
		dev.kultivator.client.anim.PoseApplier.tilt(state, poseStack);
	}

	@Override
	protected boolean shouldShowName(KultivatorNpc entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(KultivatorNpc entity) {
		return entity.plate();
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (SosokRender.aktif(state)) {
			this.model = state.slim ? sosokSlim : sosokWide;
		} else {
			this.model = state.slim ? slimModel : wideModel;
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** The robe only within 32 blocks (16 on Minimal effects): the laptop's frame time (DESIGN 34.0 rule V3). */
	private static final class CulledRobe extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private final DiscipleRobeLayer robe;

		CulledRobe(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, DiscipleRobeLayer robe) {
			super(parent);
			this.robe = robe;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			double limit = EffectQuality.minimal() ? ROBE_DISTANCE_SQ / 4.0D : ROBE_DISTANCE_SQ;
			if (state.distanceToCameraSq <= limit) {
				robe.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
			}
		}
	}
}

package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.disciple.power.DutyPoses;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.Sosok;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.ChatFormatting;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The shared renderer of module D's people (built like {@code musuh/RogueRenderer}, DESIGN 34.4 "reuse the disciple
 * model"): the disciple render state on the player model (with the sect's duty poses, {@link DutyPoses.Model}), a
 * default skin or one of D's own skins, the disciple robe in the role's colour (dropped beyond 32 b, 16 b on Minimal),
 * the role's {@link Headwear} piece, anything held, a scale and a tint per role; ghosts are drawn see-through.
 * <p>
 * Life on the model, all through vanilla render-state fields (so the body and its robe always move together): a
 * talking NPC ({@link DimensiNpc#FLAG_TALK}) lifts its hand in rhythm and nods; a bowing one
 * ({@link DimensiNpc#FLAG_BOW}) bends low; a meditating one ({@link DimensiNpc#FLAG_SIT}) sinks into the lotus seat,
 * blended in and out over 10 ticks; the subclasses add each role's own idle ({@link #pose}). The ghosts hover and sway.
 * The name plate within 16 b is the role's name and title ({@code kultivator.dimensi.gelar.*}).
 * <p>
 * Penampilan dan Animasi (DESIGN 40.2.2, 40.4.1 D, module 40R): a subclass that names a look key ({@link #rupa}) makes the
 * body a Sosok made on the client from its old fields ({@code SosokRender.dariLama}: fixed sex for Meng Po, King Yama,
 * the judges and the Dragon King, the role's age mix): its own atlas, busana, face and props, slim arms by its sex, and
 * its height from the Sosok unless the role is larger than life. The iconic own skins (Ox-Head, Horse-Face, the two
 * Impermanences, the hungry ghosts, the Dragon King) keep their PNG and mesh ({@link #kulitSendiri}); Gerak animates
 * them all. Talking and bowing become clips 38 {@code bicara_tangan} and 32 {@code hormat} (wanfu for women), the roles'
 * work becomes their clips; bodies without a look key (Yaksha) keep the old gestures.
 */
public abstract class HumanNpcRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, DiscipleRenderState, PlayerModel> {

	protected static final RenderStateDataKey<Float> SCALE = RenderStateDataKey.create(() -> "kultivator:dimensi_scale");
	protected static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "kultivator:dimensi_tint");
	/** Hover height of a ghost (blocks, before the scale): it floats and sways. */
	protected static final RenderStateDataKey<Float> HOVER = RenderStateDataKey.create(() -> "kultivator:dimensi_hover");
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;
	private static final float SIT_BLEND = 10.0F;
	/** Per entity id: {target 0/1, the age it changed} of the lotus seat. */
	private static final Map<Integer, float[]> SEATS = new HashMap<>();

	/** The old player-skin body (own skins, Yaksha, the last fallback). */
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;
	/** The Sosok body (128 × 128 atlas mesh). */
	private final PlayerModel sosokWide;
	private final PlayerModel sosokSlim;

	protected HumanNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new DutyPoses.Model(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new DutyPoses.Model(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.sosokWide = new DutyPoses.Model(SosokJembatan.tubuh(context, false), false);
		this.sosokSlim = new DutyPoses.Model(SosokJembatan.tubuh(context, true), true);
		this.addLayer(new SosokRender.TanpaSosok(this, new CulledRobe(this, new DiscipleRobeLayer(this, context))));
		SosokRender.pasangLapisan(this::addLayer, this, context);
		this.addLayer(new HeadwearLayer(this, context));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	/** The role's look: skin, robe, role (ghosts: {@code PHANTOM}), headwear, tint, scale. */
	protected abstract void look(T entity, DiscipleRenderState state);

	/** The role's own idle and gesture on top of the shared ones (arm poses, duty poses, hover). Default: none. */
	protected void pose(T entity, DiscipleRenderState state, boolean talking, boolean still) {
	}

	/** The role's Sosok look key ({@code d.meng_po}, ... of 40.4.1 D), or null: the body stays as before (Yaksha). */
	protected @Nullable String rupa(T entity) {
		return null;
	}

	/** True for an iconic own skin: Gerak animates it, but it keeps its PNG and mesh (no atlas, no busana or face). */
	protected boolean kulitSendiri(T entity) {
		return false;
	}

	/** The seed of the client-made Sosok (with the UUID): the role's look and variant fields. */
	protected int lookSosok(T entity) {
		return entity.getId();
	}

	/** 1/0 when the old skin's slim arms meant a woman/man for this role, -1 when they meant nothing (the policy decides). */
	protected int slimLama(T entity) {
		return -1;
	}

	/** True when Gerak animates this body (the shared and role gestures then ask clips instead of arm poses). */
	protected static boolean sosokGerak(DiscipleRenderState state) {
		return SosokRender.gerak(state);
	}

	/** Asks Gerak for a client clip on this body (talking, bowing, the role's work; 40G plays it as layer 6). */
	protected static void klip(DiscipleRenderState state, int klip) {
		SosokRender.mintaKlip(state, klip, 0);
	}

	@Override
	public void extractRenderState(T entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.layer = 0;
		state.stage = 0;
		state.dao = -1;
		state.rootColor = 0xFFFFFF;
		state.flags = 0;
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		state.seated = 0.0F;
		state.natalItem.clear();
		state.name = entity.getName();
		state.role = DiscipleEntity.Role.RIVAL.ordinal();
		FabricRenderState data = (FabricRenderState) state;
		data.setData(MeditationPose.STRENGTH, 0.0F);
		data.setData(SCALE, 1.0F);
		data.setData(TINT, -1);
		data.setData(HOVER, 0.0F);
		data.setData(HeadwearLayer.KIND, Headwear.NONE);
		data.setData(HeadwearLayer.GLOW, Headwear.NONE);
		skin(state, entity.getId() & 7, false);
		look(entity, state);
		sosok(entity, state, partialTicks);
		if (entity instanceof DimensiNpc npc) {
			gestures(entity, npc, state);
		}
		SosokRender.selesai(entity, state, partialTicks);
		SosokRender.labelLod(entity, state, partialTicks);
	}

	/**
	 * DESIGN 40.2.2: the client-made Sosok of a role with a look key. The draw scale is the Sosok's height (the server
	 * never sets the scale attribute of D people), except for the larger-than-life roles whose scale is above 1 (King
	 * Yama, the judges, the guards, the immortals); own skins keep the role's scale.
	 */
	private void sosok(T entity, DiscipleRenderState state, float partialTicks) {
		String key = rupa(entity);
		if (key == null) {
			return;
		}
		FabricRenderState data = (FabricRenderState) state;
		boolean own = kulitSendiri(entity);
		Sosok sosok = SosokRender.dariLama(entity, key, lookSosok(entity), slimLama(entity));
		float roleScale = data.getDataOrDefault(SCALE, 1.0F);
		float scale = !own && roleScale <= 1.0F ? sosok.skala() : roleScale;
		int headwear = data.getDataOrDefault(HeadwearLayer.KIND, Headwear.NONE);
		SosokRender.ekstrak(entity, state, partialTicks, new SosokRender.RupaMasukan(key, state.realm, -1, 0, 0, null, 0, headwear,
				PhysiqueVisuals.translucent(state)), sosok, Gaya.NETRAL, !own, scale);
		if (SosokRender.aktif(state)) {
			// 40.4.5: the ghost hair and eyes are painted on the Sosok atlas, the old pieces would double them
			if (SosokJembatan.headwearMilikSosok(headwear)) {
				data.setData(HeadwearLayer.KIND, Headwear.NONE);
			}
			if (SosokJembatan.headwearMilikSosok(data.getDataOrDefault(HeadwearLayer.GLOW, Headwear.NONE))) {
				data.setData(HeadwearLayer.GLOW, Headwear.NONE);
			}
		}
	}

	/** The shared pose hints of {@link DimensiNpc}: the lotus seat, the bow, the talking hand and nod. */
	private void gestures(T entity, DimensiNpc npc, DiscipleRenderState state) {
		float age = state.ageInTicks;
		float seat = seat(state.id, npc.hasFlag(DimensiNpc.FLAG_SIT), age);
		if (seat > 0.0F) {
			state.seated = seat;
			state.walkAnimationSpeed = Mth.lerp(seat, state.walkAnimationSpeed, 0.0F);
			((FabricRenderState) state).setData(MeditationPose.STRENGTH, seat);
		}
		boolean talking = npc.hasFlag(DimensiNpc.FLAG_TALK);
		boolean still = state.walkAnimationSpeed <= 0.1F && seat < 0.5F;
		if (sosokGerak(state)) {
			// DESIGN 40.7.2: the bow and the talking hand are Gerak clips (talking also works while walking: upper body only)
			if (npc.hasFlag(DimensiNpc.FLAG_BOW) && seat < 0.5F) {
				klip(state, GerakId.HORMAT);
			} else if (talking && seat < 0.5F) {
				klip(state, GerakId.BICARA_TANGAN);
			}
		} else if (npc.hasFlag(DimensiNpc.FLAG_BOW) && seat < 0.5F) {
			state.isCrouching = true;
		} else if (talking && still) {
			// a hand lifted in rhythm while it speaks (the free hand, or the one holding the brush or lantern)
			boolean up = Mth.sin(age * 0.22F + npc.getId()) > -0.2F;
			if (state.leftHandItemState.isEmpty()) {
				state.leftArmPose = up ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.EMPTY;
			} else {
				state.rightArmPose = up ? HumanoidModel.ArmPose.BLOCK : state.rightArmPose;
			}
			state.xRot += Mth.sin(age * 0.45F) * 6.0F;
		}
		pose(entity, state, talking, still);
	}

	/** The blended lotus seat 0..1 of entity {@code id} (in over {@link #SIT_BLEND} ticks, out as fast). */
	private static float seat(int id, boolean on, float now) {
		float[] b = SEATS.get(id);
		if (b == null) {
			if (!on) {
				return 0.0F;
			}
			b = new float[] {1.0F, now - SIT_BLEND};
			SEATS.put(id, b);
		} else if ((b[0] > 0.5F) != on) {
			float current = value(b, now);
			b[0] = on ? 1.0F : 0.0F;
			b[1] = now - (on ? current : 1.0F - current) * SIT_BLEND;
		}
		if (SEATS.size() > 256) {
			SEATS.clear();
		}
		return value(b, now);
	}

	private static float value(float[] b, float now) {
		float t = Mth.clamp((now - b[1]) / SIT_BLEND, 0.0F, 1.0F);
		float v = b[0] > 0.5F ? t : 1.0F - t;
		return v * v * (3.0F - 2.0F * v);
	}

	/** One of the nine default characters. */
	protected static void skin(DiscipleRenderState state, int index, boolean slim) {
		PlayerSkin skin = DiscipleLook.skinByIndex(index, slim);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
	}

	/** One of D's own wide-arm skins ({@code textures/dunia/dimensi/entity/<name>.png}). */
	protected static void ownSkin(DiscipleRenderState state, Identifier texture) {
		state.texture = texture;
		state.slim = false;
	}

	protected static void set(DiscipleRenderState state, int robe, int realm, int headwear, float scale, int tint) {
		state.robeColor = robe;
		state.realm = realm;
		FabricRenderState data = (FabricRenderState) state;
		data.setData(HeadwearLayer.KIND, headwear);
		data.setData(SCALE, scale);
		data.setData(TINT, tint);
	}

	/** A ghost hovers {@code height} blocks up, bobbing with its id's phase. */
	protected static void hover(DiscipleRenderState state, float height, int id) {
		float bob = Mth.sin(state.ageInTicks * 0.09F + id * 0.7F) * 0.06F;
		((FabricRenderState) state).setData(HOVER, height + bob);
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int tint = ((FabricRenderState) state).getDataOrDefault(TINT, -1);
		return tint == -1 && PhysiqueVisuals.translucent(state) ? PhysiqueVisuals.modelTint(state, -1) : tint;
	}

	@Override
	protected @Nullable RenderType getRenderType(DiscipleRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (isBodyVisible && PhysiqueVisuals.translucent(state)) {
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
		SosokRender.Keadaan sosok = SosokRender.keadaan(state);
		float role = sosok != null ? sosok.skalaGambar : ((FabricRenderState) state).getDataOrDefault(SCALE, 1.0F);
		float scale = PLAYER_SCALE * role;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		MeditationPose.lower(state, poseStack);
		float hover = ((FabricRenderState) state).getDataOrDefault(HOVER, 0.0F);
		if (hover != 0.0F) {
			poseStack.translate(0.0F, hover, 0.0F);
		}
		dev.kultivator.client.anim.PoseApplier.lift(state, poseStack);
		SosokRender.angkat(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		SosokRender.baring(state, poseStack);
		dev.kultivator.client.anim.PoseApplier.tilt(state, poseStack);
	}

	@Override
	protected boolean shouldShowName(T entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && !entity.isInvisible();
	}

	@Override
	protected Component getNameTag(T entity) {
		Component title = title(entity);
		if (title == null) {
			return entity.getName();
		}
		MutableComponent plate = entity.getName().copy();
		return plate.append(Component.translatable("kultivator.dimensi.gelar.pisah", title).withStyle(ChatFormatting.GRAY));
	}

	/** The role's title on the name plate (null: the name alone). */
	protected @Nullable Component title(T entity) {
		return null;
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

	/** A see-through ghost tint {@code (alpha, rgb)}. */
	protected static int ghostTint(int alpha, int rgb) {
		return ARGB.color(alpha, rgb);
	}

	/** The robe only within 32 blocks (16 on Minimal effects). */
	private static final class CulledRobe extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private final DiscipleRobeLayer robe;

		CulledRobe(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, DiscipleRobeLayer robe) {
			super(parent);
			this.robe = robe;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			double limit = EffectQuality.minimal() ? ROBE_DISTANCE_SQ / 4.0D : ROBE_DISTANCE_SQ;
			if (state.distanceToCameraSq <= limit) {
				robe.submit(poseStack, collector, lightCoords, state, yRot, xRot);
			}
		}
	}
}

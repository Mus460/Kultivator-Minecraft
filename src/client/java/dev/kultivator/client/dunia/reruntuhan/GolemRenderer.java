package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.gua.BonekaBatuEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws the Boneka Batu Penjaga (DESIGN 34.5.5) from block models posed on one hitbox (rule W2): legs, chest, head,
 * shoulder boulders, arms and fists of the ruin's stone (Kecil mossy stone, Agung tuff, Kuno blackstone and gilded
 * blackstone), glowing eyes and the 守 rune on its chest. 12 blocks at Maximal, 10 at Normal, 6 at Minimal or beyond
 * 32 blocks (the 6-12 of the design). It walks with a heavy gait, swings both fists, rises from the floor, raises its
 * arms for Hantaman Bumi and slams them down, spins with its arms out for Pusaran Batu and spreads them for Cincin
 * Kerikil; the telegraph makes the eyes and rune burn orange. Entity models only: it never touches a block.
 */
public class GolemRenderer extends EntityRenderer<BonekaBatuEntity, GolemRenderer.State> {

	private static final BlockState[][] LOOK = {
			{Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState()},
			{Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.CHISELED_TUFF.defaultBlockState(), Blocks.CHISELED_TUFF_BRICKS.defaultBlockState()},
			{Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState(),
					Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()}};
	private static final int[] EYES = {0x9FE0C0, 0xFFD54A, 0xC89BFF};

	public static final class State extends EntityRenderState {
		int kind;
		float bodyYaw;
		float headYaw;
		float limb;
		float limbAmount;
		float swing;
		int skill;
		float skillAge;
		int flags;
		boolean hurt;
		float death;
		final BlockModelRenderState body = new BlockModelRenderState();
		final BlockModelRenderState heavy = new BlockModelRenderState();
		final BlockModelRenderState head = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public GolemRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 1.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(BonekaBatuEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(1.5D);
	}

	@Override
	public void extractRenderState(BonekaBatuEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = Mth.clamp(entity.kind(), 0, 2);
		state.bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		state.headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - state.bodyYaw), -45.0F, 45.0F);
		state.limb = entity.walkAnimation.position(partialTicks);
		state.limbAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
		state.swing = entity.getSwingAnimation(partialTicks);
		state.skill = entity.skill();
		state.skillAge = entity.skillAge() + partialTicks;
		state.flags = entity.flags();
		state.hurt = entity.hurtTime > 0;
		state.death = entity.deathTime > 0 ? (entity.deathTime + partialTicks) / 20.0F : 0.0F;
		BlockState[] look = LOOK[state.kind];
		blocks.update(state.body, look[0], DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		blocks.update(state.heavy, look[1], DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		blocks.update(state.head, look[2], DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
	}

	@Override
	protected boolean shouldShowName(BonekaBatuEntity entity, double distanceToCameraSq) {
		return entity.deathTime == 0 && distanceToCameraSq < RTitles.GUARD_SQ;
	}

	@Override
	protected Component getNameTag(BonekaBatuEntity entity) {
		return RTitles.name(entity.getDisplayName(), RTitles.GOLEM[Mth.clamp(entity.kind(), 0, 2)]);
	}

	@Override
	protected void extractNameTags(BonekaBatuEntity entity, State state, float partialTicks) {
		super.extractNameTags(entity, state, partialTicks);
		state.scoreText = state.nameTag == null ? null : RTitles.golem(entity.kind(), entity.flags());
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = s.lightCoords;
		int overlay = OverlayTexture.pack(0.0F, s.hurt);
		int budget = EffectQuality.minimal() || s.distanceToCameraSq > 32.0D * 32.0D ? 6 : EffectQuality.maximal() ? 12 : 10;
		boolean telegraph = (s.flags & RCreature.FLAG_TELEGRAPH) != 0;
		float scale = 1.0F + 0.15F * s.kind;
		pose.pushPose();
		pose.rotate(Axis.YP.rotationDegrees(-s.bodyYaw));
		pose.scale(scale, scale, scale);
		// rising out of the floor (1.5 s) and the collapse of death
		if ((s.flags & RCreature.FLAG_RISING) != 0 || s.skill == BonekaBatuEntity.RISE) {
			float t = Mth.clamp(s.skillAge / 30.0F, 0.0F, 1.0F);
			pose.translate(0.0F, -(1.0F - t) * (1.0F - t) * 3.0F, 0.0F);
			pose.rotate(Axis.XP.rotationDegrees((1.0F - t) * 25.0F));
		}
		if (s.death > 0.0F) {
			float d = Math.min(1.0F, s.death);
			pose.translate(0.0F, -d * 1.2F, 0.0F);
			pose.rotate(Axis.XP.rotationDegrees(d * 40.0F));
		}
		// gait
		float walk = Mth.cos(s.limb * 0.45F) * 0.7F * s.limbAmount;
		float bounce = Math.abs(Mth.sin(s.limb * 0.45F)) * 0.06F * s.limbAmount;
		// arms (pitch: negative = forward / up), from the walk, the punch and the skills
		float armL = -walk * 0.8F;
		float armR = walk * 0.8F;
		float rollL = 0.08F;
		float rollR = -0.08F;
		float lean = 0.0F;
		float punch = Mth.sin(s.swing * Mth.PI);
		armL -= punch * 1.6F;
		armR -= punch * 1.6F;
		switch (s.skill) {
			case BonekaBatuEntity.SLAM -> {
				float raise = Mth.clamp(s.skillAge / 22.0F, 0.0F, 1.0F);
				float hit = Mth.clamp((s.skillAge - 22.0F) / 3.0F, 0.0F, 1.0F);
				float up = -2.9F * raise + 3.6F * hit;
				armL = up;
				armR = up;
				lean = -0.15F * raise + 0.45F * hit;
			}
			case BonekaBatuEntity.SPIN -> {
				rollL = 1.45F;
				rollR = -1.45F;
				armL = 0.0F;
				armR = 0.0F;
			}
			case BonekaBatuEntity.PEBBLES -> {
				float spread = Mth.clamp(s.skillAge / 18.0F, 0.0F, 1.0F);
				float thrust = Mth.clamp((s.skillAge - 18.0F) / 3.0F, 0.0F, 1.0F);
				rollL = 0.08F + 1.1F * spread;
				rollR = -0.08F - 1.1F * spread;
				armL = -0.6F * thrust;
				armR = -0.6F * thrust;
			}
			default -> {
			}
		}
		pose.translate(0.0F, bounce, 0.0F);
		// legs
		leg(s, pose, collector, -0.38F, walk, budget, light, overlay);
		leg(s, pose, collector, 0.38F, -walk, budget, light, overlay);
		// torso (the chest leans with a slam)
		pose.pushPose();
		pose.translate(0.0F, 1.3F, 0.0F);
		pose.rotate(Axis.XP.rotation(lean));
		RDraw.centred(pose, collector, s.body, 0.0F, 0.0F, 0.0F, 1.5F, 1.25F, 0.95F, light, overlay);
		// head (turns toward its target)
		pose.pushPose();
		pose.translate(0.0F, 1.25F, 0.05F);
		pose.rotate(Axis.YP.rotationDegrees(-s.headYaw));
		RDraw.centred(pose, collector, s.head, 0.0F, 0.0F, 0.0F, 0.62F, 0.56F, 0.62F, light, overlay);
		pose.popPose();
		// shoulders and arms
		if (budget >= 10) {
			RDraw.centred(pose, collector, s.heavy, -0.92F, 0.85F, 0.0F, 0.6F, 0.55F, 0.62F, light, overlay);
			RDraw.centred(pose, collector, s.heavy, 0.92F, 0.85F, 0.0F, 0.6F, 0.55F, 0.62F, light, overlay);
		}
		arm(s, pose, collector, -1.0F, armL, rollL, budget, light, overlay);
		arm(s, pose, collector, 1.0F, armR, rollR, budget, light, overlay);
		// eyes and the chest rune: a carved glyph glowing in the ruin's colour, orange while a skill is being called
		int eye = telegraph ? 0xFF8A3A : EYES[s.kind];
		float burn = telegraph ? 0.95F : 0.6F + 0.2F * Mth.sin(s.ageInTicks * 0.1F);
		if (s.distanceToCameraSq < 48.0D * 48.0D && s.death <= 0.0F) {
			pose.pushPose();
			pose.translate(0.0F, 1.25F, 0.05F);
			pose.rotate(Axis.YP.rotationDegrees(-s.headYaw));
			RSink glow = new RSink(RTex.GLOW, 12);
			Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
			Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
			for (int side = -1; side <= 1; side += 2) {
				eyeQuad(glow, side * 0.14F, 0.33F, 0.33F, 0.1F, RSink.argb(burn, eye));
				eyeQuad(glow, side * 0.14F, 0.33F, 0.33F, 0.22F, RSink.argb(burn * 0.4F, eye));
			}
			glow.submit(collector, pose);
			pose.popPose();
			RDraw.text(pose, collector, Component.literal("守"), 0.0F, 0.7F, 0.49F, RDraw.facing(0.0F, 1.0F), 0.6F,
					ARGB.color(Math.round(255 * burn), RDraw.lighten(eye, 0.2F)), false);
		}
		pose.popPose();
		pose.popPose();
		super.submit(s, pose, collector, camera);
	}

	/** A small square facing the golem's front (+z) at (x, y, z) of half size {@code half}. */
	private static void eyeQuad(RSink sink, float x, float y, float z, float half, int color) {
		sink.vertex(x - half, y - half, z, 0.0F, 1.0F, color);
		sink.vertex(x + half, y - half, z, 1.0F, 1.0F, color);
		sink.vertex(x + half, y + half, z, 1.0F, 0.0F, color);
		sink.vertex(x - half, y + half, z, 0.0F, 0.0F, color);
	}

	private static void leg(State s, PoseStack pose, SubmitNodeCollector collector, float x, float swing, int budget, int light, int overlay) {
		pose.pushPose();
		pose.translate(x, 1.3F, 0.0F);
		pose.rotate(Axis.XP.rotation(swing));
		if (budget >= 12) {
			RDraw.centred(pose, collector, s.heavy, 0.0F, -0.7F, 0.0F, 0.58F, 0.7F, 0.62F, light, overlay);
			RDraw.centred(pose, collector, s.body, 0.0F, -1.32F, 0.06F, 0.62F, 0.64F, 0.74F, light, overlay);
		} else {
			RDraw.centred(pose, collector, s.body, 0.0F, -1.32F, 0.03F, 0.6F, 1.32F, 0.66F, light, overlay);
		}
		pose.popPose();
	}

	private static void arm(State s, PoseStack pose, SubmitNodeCollector collector, float side, float pitch, float roll, int budget, int light,
							int overlay) {
		pose.pushPose();
		pose.translate(side * 0.98F, 1.05F, 0.0F);
		pose.rotate(Axis.XP.rotation(pitch));
		pose.rotate(Axis.ZP.rotation(side < 0 ? -roll : roll));
		if (budget >= 10) {
			RDraw.centred(pose, collector, s.body, side * 0.05F, -0.85F, 0.0F, 0.46F, 0.85F, 0.5F, light, overlay);
			RDraw.centred(pose, collector, s.heavy, side * 0.08F, -1.55F, 0.0F, 0.66F, 0.7F, 0.68F, light, overlay);
		} else {
			RDraw.centred(pose, collector, s.heavy, side * 0.06F, -1.5F, 0.0F, 0.58F, 1.5F, 0.6F, light, overlay);
		}
		pose.popPose();
	}
}

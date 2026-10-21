package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

/**
 * Draws a {@link ColliderEntity} (DESIGN 34.5.5-34.5.7, rule W2; the doors are entities, never blocks):
 * <ul>
 * <li>a Gerbang Segel: two panels of the ruin's carved stone (bronze with gold bands for the tower), tiled a block at a
 * time so the texture stays crisp, the seal sigil and its glyph across the seam; opening, the glyph flares and fades
 * and the panels slide apart into the walls over 1.2 s ({@link ColliderEntity#OPEN_TICKS}), eased;</li>
 * <li>a phantom wall of the Labirin Ilusi: the maze's own stone; once revealed (Pil Mata Terang, the hint) a cold
 * shimmering shell; dispelled, it sinks away in half a second.</li>
 * </ul>
 * The sigil and glyph drop beyond 48 blocks (rule V3).
 */
public class ColliderRenderer extends EntityRenderer<ColliderEntity, ColliderRenderer.State> {

	private static final double DETAIL_SQ = 48.0D * 48.0D;
	private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();

	public static final class State extends EntityRenderState {
		float hx;
		float h;
		float hz;
		int flags;
		int glyph;
		float slide;
		float since;
		final BlockModelRenderState look = new BlockModelRenderState();
		final BlockModelRenderState trim = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public ColliderRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(ColliderEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(3.0D, 1.0D, 3.0D);
	}

	@Override
	public void extractRenderState(ColliderEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Vector3fc half = entity.half();
		state.hx = half.x();
		state.h = half.y();
		state.hz = half.z();
		state.flags = entity.flags();
		state.glyph = entity.glyph();
		blocks.update(state.look, entity.look(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		blocks.update(state.trim, GOLD, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		if (entity.isOpen()) {
			long opened = entity.openedAt();
			float now = Minecraft.getInstance().level == null ? 0.0F : Minecraft.getInstance().level.getGameTime() + partialTicks;
			state.since = opened <= 0L ? 999.0F : Math.max(0.0F, now - opened);
			float t = Mth.clamp(state.since / ColliderEntity.OPEN_TICKS, 0.0F, 1.0F);
			// ease in-out: a heavy door starts and stops slowly
			state.slide = t * t * (3.0F - 2.0F * t);
		} else {
			state.slide = 0.0F;
			state.since = -1.0F;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if ((state.flags & ColliderEntity.FLAG_PHANTOM) != 0) {
			phantom(state, poseStack, collector, camera.orientation);
		} else {
			door(state, poseStack, collector);
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== doors

	private void door(State s, PoseStack pose, SubmitNodeCollector collector) {
		if (s.since > ColliderEntity.OPEN_TICKS + 40.0F) {
			// fully open for two seconds: the panels sit inside the walls, nothing to draw
			return;
		}
		boolean alongX = s.hx >= s.hz;
		float half = alongX ? s.hx : s.hz;
		float thick = Math.max(0.2F, (alongX ? s.hz : s.hx) * 2.0F * 0.85F);
		float height = s.h;
		int light = s.lightCoords;
		boolean tower = (s.flags & ColliderEntity.FLAG_TOWER) != 0;
		float shift = s.slide * half;
		for (int side = -1; side <= 1; side += 2) {
			// a panel covers 0..half along its side, moved outward by the slide
			for (float u = 0.0F; u < half - 0.001F; u += 1.0F) {
				float cell = Math.min(1.0F, half - u);
				float a0 = side < 0 ? -(u + cell) - shift : u + shift;
				for (float y = 0.0F; y < height - 0.001F; y += 1.0F) {
					float cy = Math.min(1.0F, height - y);
					if (alongX) {
						RDraw.box(pose, collector, s.look, a0, y, -thick / 2.0F, cell, cy, thick, light, OverlayTexture.NO_OVERLAY);
					} else {
						RDraw.box(pose, collector, s.look, -thick / 2.0F, y, a0, thick, cy, cell, light, OverlayTexture.NO_OVERLAY);
					}
				}
			}
			if (tower) {
				// gold bands across the bronze leaves and a gold edge on the seam
				for (float y : new float[]{0.6F, height * 0.5F, height - 0.7F}) {
					float a0 = side < 0 ? -half - shift : shift;
					if (alongX) {
						RDraw.box(pose, collector, s.trim, a0, y, -thick / 2.0F - 0.02F, half, 0.12F, thick + 0.04F, light, OverlayTexture.NO_OVERLAY);
					} else {
						RDraw.box(pose, collector, s.trim, -thick / 2.0F - 0.02F, y, a0, thick + 0.04F, 0.12F, half, light, OverlayTexture.NO_OVERLAY);
					}
				}
			}
		}
		if (s.distanceToCameraSq > DETAIL_SQ) {
			return;
		}
		// the seal across the seam: sigil and glyph on both faces, flaring and fading as the door opens
		float fade = s.since < 0.0F ? 1.0F : 1.0F - Mth.clamp(s.since / (ColliderEntity.OPEN_TICKS * 0.6F), 0.0F, 1.0F);
		float flare = s.since < 0.0F ? 0.0F : Mth.clamp(1.0F - Math.abs(s.since - 4.0F) / 6.0F, 0.0F, 1.0F);
		if (fade <= 0.01F && flare <= 0.01F) {
			return;
		}
		int rgb = tower ? 0xFFD54A : s.glyph >= Glyphs.DOOR_PHASE && s.glyph < Glyphs.DOOR_TOWER ? phaseRgb(s.glyph - Glyphs.DOOR_PHASE) : 0xFFC86A;
		float cy = height * 0.55F;
		float r = Math.min(half, height * 0.5F) * 0.85F;
		float pulse = 0.75F + 0.25F * Mth.sin(s.ageInTicks * 0.08F);
		for (int f = -1; f <= 1; f += 2) {
			float nx = alongX ? 0.0F : f;
			float nz = alongX ? f : 0.0F;
			float off = thick / 2.0F + 0.02F;
			Quaternionf rot = RDraw.facing(nx, nz);
			// one sink per face: the collector reads the vertices later, with the pose of this submit
			RSink sigil = new RSink(RTex.SIGIL, 8);
			RSink glow = new RSink(RTex.GLOW, 8);
			pose.pushPose();
			pose.translate(nx * off, cy, nz * off);
			pose.rotate(rot);
			quadXY(sigil, r, RSink.argb((0.45F * pulse) * fade + flare * 0.9F, rgb), s.ageInTicks * 0.01F * f);
			quadXY(glow, r * (1.1F + flare), RSink.argb(0.25F * fade + 0.6F * flare, rgb), 0.0F);
			sigil.submit(collector, pose);
			glow.submit(collector, pose);
			pose.popPose();
			if (fade > 0.05F) {
				int a = Math.round(255 * Math.min(1.0F, fade * 1.2F));
				RDraw.text(pose, collector, Component.literal(Glyphs.door(s.glyph)), nx * (off + 0.01F), cy, nz * (off + 0.01F), rot, r * 0.9F,
						ARGB.color(a, RDraw.lighten(rgb, 0.25F)), false);
			}
		}
	}

	private static int phaseRgb(int phase) {
		return switch (phase) {
			case 0 -> 0x6ADB6A;
			case 1 -> 0xFF6A3A;
			case 2 -> 0xD8B060;
			case 3 -> 0xE8ECF4;
			default -> 0x4F9BFF;
		};
	}

	/** A square of half size {@code r} in the xy plane, turned by {@code spin} about z. */
	private static void quadXY(RSink sink, float r, int color, float spin) {
		float c = Mth.cos(spin) * r;
		float sn = Mth.sin(spin) * r;
		sink.vertex(-c + sn, -sn - c, 0.0F, 0.0F, 1.0F, color);
		sink.vertex(c + sn, sn - c, 0.0F, 1.0F, 1.0F, color);
		sink.vertex(c - sn, sn + c, 0.0F, 1.0F, 0.0F, color);
		sink.vertex(-c - sn, -sn + c, 0.0F, 0.0F, 0.0F, color);
	}

	// ================================================================== phantom walls

	private void phantom(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face) {
		float sink = 1.0F;
		if (s.since >= 0.0F) {
			sink = 1.0F - Mth.clamp(s.since / 10.0F, 0.0F, 1.0F);
			if (sink <= 0.0F) {
				return;
			}
		}
		boolean revealed = (s.flags & ColliderEntity.FLAG_REVEALED) != 0;
		int light = s.lightCoords;
		float height = s.h * sink;
		float w = s.hx * 2.0F;
		float d = s.hz * 2.0F;
		for (float y = 0.0F; y < height - 0.001F; y += 1.0F) {
			float cy = Math.min(1.0F, height - y);
			RDraw.box(pose, collector, s.look, -w / 2.0F, y, -d / 2.0F, w, cy, d, light, OverlayTexture.NO_OVERLAY);
		}
		if (!revealed || s.distanceToCameraSq > DETAIL_SQ) {
			return;
		}
		// the cold shimmer of an illusion: four faces of light just outside the stone, flickering
		RSink shell = new RSink(RTex.CURTAIN, 32);
		float flick = 0.5F + 0.3F * Mth.sin(s.ageInTicks * 0.31F) + 0.2F * Mth.sin(s.ageInTicks * 0.77F + 1.3F);
		int color = RSink.argb(0.35F * flick * sink, 0x9FE8FF);
		float hx = w / 2.0F + 0.02F;
		float hz = d / 2.0F + 0.02F;
		face(shell, -hx, -hz, hx, -hz, height, color);
		face(shell, hx, -hz, hx, hz, height, color);
		face(shell, hx, hz, -hx, hz, height, color);
		face(shell, -hx, hz, -hx, -hz, height, color);
		shell.submit(collector, pose);
		if (!EffectQuality.minimal()) {
			RDraw.text(pose, collector, Component.literal("幻"), 0.0F, height + 0.35F, 0.0F, face, 0.45F, ARGB.color(Math.round(200 * flick), 0x9FE8FF), false);
		}
	}

	/** A vertical face from (x0, z0) to (x1, z1), both sides. */
	private static void face(RSink sink, float x0, float z0, float x1, float z1, float height, int color) {
		sink.vertex(x0, 0.0F, z0, 0.0F, 1.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x1, 0.0F, z1, 1.0F, 1.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x1, height, z1, 1.0F, 0.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x0, height, z0, 0.0F, 0.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x0, height, z0, 0.0F, 0.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x1, height, z1, 1.0F, 0.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x1, 0.0F, z1, 1.0F, 1.0F, color, LightCoordsUtil.FULL_BRIGHT);
		sink.vertex(x0, 0.0F, z0, 0.0F, 1.0F, color, LightCoordsUtil.FULL_BRIGHT);
	}
}

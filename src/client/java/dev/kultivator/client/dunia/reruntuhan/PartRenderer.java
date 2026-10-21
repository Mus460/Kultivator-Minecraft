package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws every {@link TekaPartEntity} by its role (DESIGN 34.5.5-34.5.7, rule W2: a part is an entity drawn as block
 * models, glyph text and glow quads; it never places a block):
 * <ul>
 * <li>PLATE: a carved disc on its floor pad with its trigram inlaid, dark, lit (a column of its colour), hinted or red;</li>
 * <li>LAMP: a soul lantern with the phase glyph of its element above it; lit, a flame of the element's colour;</li>
 * <li>RING: the ring of eight constellation stones round the puzzle's centre, turned smoothly in 4-tick steps (the
 * Beidou's handle star is gold), the ring line on the floor, and (ring 0) the star shining on the ceiling;</li>
 * <li>PAN: a copper pan hanging from the balance beam on its chains; pan 0 also draws the beam, tilted by the two
 * weights, and both show "now / asked";</li>
 * <li>STELE: the carved glyphs of the riddle on the stele's face; TALISMAN: a fluttering seal paper (torn when pulled);
 * CORE: the three seal sigils turning round the Jantung Segel, red and cracked when broken; PILLAR: the 封 band of a seal
 * pillar, lit gold with a beam to the ceiling; GATE / EXIT / PAD: the band gates' curtains, the way home and the pads
 * of a cleared floor; MIRROR: the shimmer of the Cermin Hati.</li>
 * </ul>
 * Text and glow drop beyond 48 blocks, everything but the core shape beyond 96 (rule V3).
 */
public class PartRenderer extends EntityRenderer<TekaPartEntity, PartRenderer.State> {

	private static final double DETAIL_SQ = 48.0D * 48.0D;
	private static final double FAR_SQ = 96.0D * 96.0D;
	private static final String[] STELE_GLYPHS = {"八卦", "五行", "北斗", "幻陣", "心鏡", "衡器"};
	private static final int[] RING_RGB = {0xBFE8FF, 0xD8B0FF, 0xFFE6A0};
	private static final BlockState[] RING_STONES = {Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(),
			Blocks.CALCITE.defaultBlockState()};

	/** The shown turn of each ring handle (client side, eased toward the synced turn over 4 ticks). */
	private static final Map<Integer, float[]> TURNS = new HashMap<>();

	public static final class State extends EntityRenderState {
		int id;
		int role;
		int part;
		int arg;
		int arg2;
		int rgb;
		float yaw;
		float cx;
		float cy;
		float cz;
		float camX;
		float camY;
		float camZ;
		float turn;
		int otherArg = -1;
		float near;
		int unlocked = 1;
		final BlockModelRenderState a = new BlockModelRenderState();
		final BlockModelRenderState b = new BlockModelRenderState();
		final BlockModelRenderState c = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public PartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(TekaPartEntity entity, float partialTicks) {
		return switch (entity.role()) {
			case TekaPartEntity.RING -> entity.getBoundingBox().inflate(8.0D, 7.0D, 8.0D);
			case TekaPartEntity.PAN -> entity.getBoundingBox().inflate(5.0D, 5.0D, 5.0D);
			case TekaPartEntity.GATE, TekaPartEntity.EXIT, TekaPartEntity.MIRROR, TekaPartEntity.PILLAR -> entity.getBoundingBox().inflate(3.0D, 8.0D, 3.0D);
			case TekaPartEntity.CORE -> entity.getBoundingBox().inflate(7.0D, 10.0D, 7.0D);
			default -> entity.getBoundingBox().inflate(1.5D);
		};
	}

	@Override
	public void extractRenderState(TekaPartEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.id = entity.getId();
		state.role = entity.role();
		state.part = entity.state();
		state.arg = entity.arg();
		state.arg2 = entity.arg2();
		state.rgb = entity.color();
		state.yaw = entity.getYRot();
		Vec3 centre = entity.center();
		state.cx = (float) (centre.x - entity.getX());
		state.cy = (float) (centre.y - entity.getY());
		state.cz = (float) (centre.z - entity.getZ());
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 cam = minecraft.gameRenderer.mainCamera().position();
		state.camX = (float) (cam.x - state.x);
		state.camY = (float) (cam.y - state.y);
		state.camZ = (float) (cam.z - state.z);
		state.near = minecraft.player == null ? 99.0F : (float) Math.sqrt(minecraft.player.distanceToSqr(entity));
		state.unlocked = LobbyState.unlocked;
		switch (state.role) {
			case TekaPartEntity.PLATE -> {
				blocks.update(state.a, Blocks.POLISHED_ANDESITE.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				blocks.update(state.b, Blocks.GOLD_BLOCK.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
			}
			case TekaPartEntity.LAMP -> blocks.update(state.a, Blocks.SOUL_LANTERN.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
			case TekaPartEntity.RING -> {
				int k = Mth.clamp((state.arg2 >> 8) & 0xFF, 0, 2);
				blocks.update(state.a, RING_STONES[k], DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				blocks.update(state.b, Blocks.GOLD_BLOCK.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				blocks.update(state.c, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				state.turn = shownTurn(entity, partialTicks);
			}
			case TekaPartEntity.PAN -> {
				blocks.update(state.a, Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				blocks.update(state.b, Blocks.IRON_CHAIN.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				blocks.update(state.c, Blocks.GOLD_BLOCK.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				state.otherArg = otherPan(entity);
			}
			case TekaPartEntity.CORE -> blocks.update(state.b, Blocks.IRON_CHAIN.defaultBlockState(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
			default -> {
			}
		}
	}

	/** The ring's turn as shown: eased from the last shown turn to the synced one over 4 ticks (the short way round). */
	private static float shownTurn(TekaPartEntity entity, float partialTicks) {
		float now = entity.tickCount + partialTicks;
		float target = entity.arg();
		float[] t = TURNS.get(entity.getId());
		if (t == null) {
			t = new float[]{target, target, now - 10.0F};
			if (TURNS.size() > 64) {
				TURNS.clear();
			}
			TURNS.put(entity.getId(), t);
		}
		float shown = t[0] + (t[1] - t[0]) * Mth.clamp((now - t[2]) / 4.0F, 0.0F, 1.0F);
		if (Math.floorMod(Math.round(t[1]), 8) != Math.floorMod(Math.round(target), 8)) {
			float delta = target - Math.floorMod(Math.round(t[1]), 8);
			while (delta > 4.0F) {
				delta -= 8.0F;
			}
			while (delta < -4.0F) {
				delta += 8.0F;
			}
			t[0] = shown;
			t[1] = Math.round(t[1]) + delta;
			t[2] = now;
		}
		return shown;
	}

	/** The weight on the other pan of the same balance, or -1 when it is not loaded here. */
	private static int otherPan(TekaPartEntity pan) {
		Vec3 centre = pan.center();
		Vec3 mirror = centre.add(centre.subtract(pan.position()));
		for (TekaPartEntity other : pan.level().getEntitiesOfClass(TekaPartEntity.class, new AABB(mirror, mirror).inflate(1.5D),
				e -> e != pan && e.role() == TekaPartEntity.PAN)) {
			return other.arg();
		}
		return -1;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.distanceToCameraSq > FAR_SQ * 1.6D) {
			return;
		}
		boolean detail = state.distanceToCameraSq <= DETAIL_SQ;
		Quaternionf face = camera.orientation;
		switch (state.role) {
			case TekaPartEntity.PLATE -> plate(state, poseStack, collector, detail);
			case TekaPartEntity.LAMP -> lamp(state, poseStack, collector, face, detail);
			case TekaPartEntity.RING -> ring(state, poseStack, collector, face, detail);
			case TekaPartEntity.PAN -> pan(state, poseStack, collector, face, detail);
			case TekaPartEntity.STELE -> stele(state, poseStack, collector, face, detail);
			case TekaPartEntity.TALISMAN -> talisman(state, poseStack, collector, face, detail);
			case TekaPartEntity.CORE -> core(state, poseStack, collector, face, detail);
			case TekaPartEntity.PILLAR -> pillar(state, poseStack, collector, face, detail);
			case TekaPartEntity.GATE -> gate(state, poseStack, collector, face, detail);
			case TekaPartEntity.EXIT -> exit(state, poseStack, collector, face, detail);
			case TekaPartEntity.PAD -> pad(state, poseStack, collector, face, detail);
			case TekaPartEntity.MIRROR -> mirror(state, poseStack, collector, detail);
			default -> {
			}
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== helpers

	private static float pulse(State s, float speed) {
		return 0.5F + 0.5F * Mth.sin(s.ageInTicks * speed);
	}

	private static Vector3f right(Quaternionf face) {
		return face.transform(new Vector3f(1.0F, 0.0F, 0.0F));
	}

	private static Vector3f up(Quaternionf face) {
		return face.transform(new Vector3f(0.0F, 1.0F, 0.0F));
	}

	/** The unit horizontal vector from the part toward its puzzle's centre (or the facing of its yaw when they meet). */
	private static float[] towardCentre(State s) {
		float len = Mth.sqrt(s.cx * s.cx + s.cz * s.cz);
		if (len < 0.05F) {
			float r = s.yaw * Mth.DEG_TO_RAD;
			return new float[]{-Mth.sin(r), Mth.cos(r)};
		}
		return new float[]{s.cx / len, s.cz / len};
	}

	/** A clickable part the player stands next to shows a small pulsing glow (the "you can use this" cue). */
	private static void cue(State s, RSink glow, float y, Vector3f right, Vector3f up, int rgb) {
		if (s.near < 5.0F) {
			float k = (5.0F - s.near) / 5.0F;
			glow.billboard(0.0F, y + 0.1F * Mth.sin(s.ageInTicks * 0.2F), 0.0F, right, up, 0.18F + 0.06F * pulse(s, 0.3F), RSink.argb(0.55F * k, rgb));
		}
	}

	// ================================================================== the roles

	private void plate(State s, PoseStack pose, SubmitNodeCollector collector, boolean detail) {
		int light = s.lightCoords;
		RDraw.centred(pose, collector, s.a, 0.0F, 0.0F, 0.0F, 0.86F, 0.08F, 0.86F, light, OverlayTexture.NO_OVERLAY);
		if (detail) {
			// the gold rim
			RDraw.centred(pose, collector, s.b, 0.0F, 0.0F, -0.43F, 0.9F, 0.1F, 0.05F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.b, 0.0F, 0.0F, 0.43F, 0.9F, 0.1F, 0.05F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.b, -0.43F, 0.0F, 0.0F, 0.05F, 0.1F, 0.82F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.b, 0.43F, 0.0F, 0.0F, 0.05F, 0.1F, 0.82F, light, OverlayTexture.NO_OVERLAY);
		}
		int glyph = Math.floorMod(s.arg, 8);
		int rgb = Glyphs.TRIGRAM_RGB[glyph];
		int color;
		float glow;
		switch (s.part) {
			case TekaPartEntity.LIT -> {
				color = RDraw.lighten(rgb, 0.35F);
				glow = 0.9F;
			}
			case TekaPartEntity.HINT -> {
				color = RDraw.lighten(rgb, 0.2F * pulse(s, 0.25F));
				glow = 0.25F + 0.45F * pulse(s, 0.25F);
			}
			case TekaPartEntity.WRONG -> {
				color = 0xFF3A2A;
				glow = 0.8F;
			}
			default -> {
				color = RDraw.mix(rgb, 0x202020, 0.55F);
				glow = 0.0F;
			}
		}
		// the glyph text is drawn face up toward the puzzle centre's opposite side, so it reads from inside the octagon
		float[] in = towardCentre(s);
		float yaw = (float) Math.atan2(-in[0], in[1]);
		RDraw.text(pose, collector, Component.literal(Glyphs.TRIGRAMS[glyph]), 0.0F, 0.085F, 0.0F, RDraw.flat(yaw), 0.62F, ARGB.opaque(color), false);
		if (glow > 0.0F && detail) {
			RSink sigil = new RSink(RTex.SIGIL, 8);
			sigil.flat(0.0F, 0.1F, 0.0F, 0.62F, s.ageInTicks * 0.02F, RSink.argb(glow * 0.8F, color));
			sigil.submit(collector, pose);
			if (s.part == TekaPartEntity.LIT) {
				RSink strip = new RSink(RTex.STRIP, 8);
				strip.beam(0.0F, 0.1F, 0.0F, 0.0F, 2.4F, 0.0F, s.camX, s.camY, s.camZ, 0.5F, RSink.argb(0.55F, color), RSink.argb(0.0F, color));
				strip.submit(collector, pose);
			}
		}
	}

	private void lamp(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		int light = s.lightCoords;
		RDraw.centred(pose, collector, s.a, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, s.part == TekaPartEntity.LIT ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY);
		if (!detail) {
			return;
		}
		int rgb = s.rgb;
		int phase = Math.floorMod(s.arg, Glyphs.PHASE.length);
		boolean lit = s.part == TekaPartEntity.LIT;
		boolean hint = s.part == TekaPartEntity.HINT;
		int color = lit ? RDraw.lighten(rgb, 0.3F) : hint ? RDraw.lighten(rgb, 0.3F * pulse(s, 0.3F)) : RDraw.mix(rgb, 0x303030, 0.45F);
		float bob = 0.05F * Mth.sin(s.ageInTicks * 0.08F + s.id);
		RDraw.text(pose, collector, Component.literal(Glyphs.PHASE[phase]), 0.0F, 1.25F + bob, 0.0F, face, 0.42F, ARGB.opaque(color), false);
		Vector3f right = right(face);
		Vector3f up = up(face);
		RSink glow = new RSink(RTex.GLOW, 16);
		if (lit) {
			float flick = 0.85F + 0.15F * Mth.sin(s.ageInTicks * 0.9F) * Mth.sin(s.ageInTicks * 0.37F + 1.3F);
			glow.billboard(0.0F, 0.45F, 0.0F, right, up, 0.55F * flick, RSink.argb(0.75F, rgb));
			glow.billboard(0.0F, 0.45F, 0.0F, right, up, 0.25F * flick, RSink.argb(0.9F, RDraw.lighten(rgb, 0.6F)));
			glow.billboard(0.0F, 1.25F + bob, 0.0F, right, up, 0.45F, RSink.argb(0.35F, rgb));
		} else if (hint) {
			glow.billboard(0.0F, 1.25F + bob, 0.0F, right, up, 0.4F + 0.15F * pulse(s, 0.3F), RSink.argb(0.45F, rgb));
		} else {
			glow.billboard(0.0F, 0.42F, 0.0F, right, up, 0.12F, RSink.argb(0.35F, rgb));
			cue(s, glow, 1.65F, right, up, rgb);
		}
		glow.submit(collector, pose);
	}

	/** The template direction at {@code phi} turns (45° each; 0 = north, clockwise) turned into the world by {@code quarter}. */
	private static float[] dir(float phi, int quarter) {
		double a = phi * Math.PI / 4.0D;
		float dx = (float) Math.sin(a);
		float dz = (float) -Math.cos(a);
		return switch (quarter & 3) {
			case 1 -> new float[]{-dz, dx};
			case 2 -> new float[]{-dx, -dz};
			case 3 -> new float[]{dz, -dx};
			default -> new float[]{dx, dz};
		};
	}

	private void ring(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		int light = s.lightCoords;
		int k = Mth.clamp((s.arg2 >> 8) & 0xFF, 0, 2);
		int target = s.arg2 & 0xFF;
		int radius = Math.max(1, (s.arg2 >> 16) & 0xFF);
		int quarter = (s.arg2 >> 24) & 0x3;
		boolean aligned = s.part == TekaPartEntity.LIT;
		boolean hint = s.part == TekaPartEntity.HINT;
		int rgb = aligned ? 0xFFD54A : RING_RGB[k];
		// the handle: a carved stone knob on the ring's south point
		RDraw.centred(pose, collector, s.c, 0.0F, 0.0F, 0.0F, 0.5F, 0.32F, 0.5F, light, OverlayTexture.NO_OVERLAY);
		Vector3f right = right(face);
		Vector3f up = up(face);
		RSink glow = new RSink(RTex.GLOW, 64);
		RSink line = new RSink(RTex.RING, 8);
		int stones = EffectQuality.minimal() || !detail ? 4 : 8;
		for (int i = 0; i < 8; i++) {
			boolean marker = i == 0;
			if (!marker && stones == 4 && i % 2 == 1) {
				continue;
			}
			float[] d = dir(s.turn + i, quarter);
			float x = s.cx + d[0] * radius;
			float z = s.cz + d[1] * radius;
			float bob = 0.06F * Mth.sin(s.ageInTicks * 0.07F + i * 0.8F + k);
			float size = marker ? 0.46F : 0.36F;
			RDraw.centred(pose, collector, marker ? s.b : s.a, x, s.cy + 0.22F + bob, z, size, size, size, marker ? 0xF000F0 : light,
					OverlayTexture.NO_OVERLAY);
			if (detail) {
				float star = marker ? 0.32F : 0.12F;
				glow.billboard(x, s.cy + 0.22F + size + 0.1F + bob, z, right, up, star, RSink.argb(marker ? 0.9F : 0.6F, marker ? 0xFFF2C0 : rgb));
			}
		}
		if (detail) {
			line.flat(s.cx, s.cy + 0.04F, s.cz, radius + 0.35F, s.ageInTicks * (k % 2 == 0 ? 0.004F : -0.004F),
					RSink.argb(aligned ? 0.7F : 0.35F, rgb));
			if (hint) {
				glow.billboard(0.0F, 0.6F, 0.0F, right, up, 0.35F + 0.2F * pulse(s, 0.35F), RSink.argb(0.6F, 0x9FE8FF));
				RDraw.text(pose, collector, Component.literal("↻"), 0.0F, 0.95F, 0.0F, face, 0.4F, 0xFF9FE8FF, false);
			} else {
				cue(s, glow, 0.85F, right, up, rgb);
			}
			if (k == 0) {
				// the star on the ceiling the handle star must point to
				float[] d = dir(target, quarter);
				float x = s.cx + d[0] * 6.4F;
				float z = s.cz + d[1] * 6.4F;
				float y = s.cy + 6.6F;
				float tw = 0.75F + 0.25F * Mth.sin(s.ageInTicks * 0.21F) * Mth.sin(s.ageInTicks * 0.13F);
				glow.billboard(x, y, z, right, up, 0.9F * tw, RSink.argb(0.55F, 0xFFF2C0));
				glow.billboard(x, y, z, right, up, 0.35F * tw, RSink.argb(0.95F, 0xFFFFFF));
				RDraw.text(pose, collector, Component.literal(Glyphs.STAR), x, y, z, face, 0.7F, 0xFFFFF2C0, false);
			}
		}
		glow.submit(collector, pose);
		line.submit(collector, pose);
	}

	private void pan(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		int light = s.lightCoords;
		int asked = s.arg2 & 0xFF;
		int index = (s.arg2 >> 8) & 0xFF;
		int mine = s.arg;
		int other = s.otherArg < 0 ? mine : s.otherArg;
		int left = index == 0 ? mine : other;
		int rightW = index == 0 ? other : mine;
		// the heavier side goes down: 4 degrees per weight, at most 14
		float tilt = Mth.clamp((rightW - left) * 4.0F, -14.0F, 14.0F);
		float[] in = towardCentre(s);
		float dist = Mth.sqrt(s.cx * s.cx + s.cz * s.cz);
		float beamY = 4.25F;
		// this pan's end of the beam: toward the centre is "inside"; pan 0 is the left (negative) end
		float sideSign = index == 0 ? -1.0F : 1.0F;
		float drop = Mth.sin(tilt * Mth.DEG_TO_RAD) * dist * sideSign;
		float endY = beamY - drop;
		float panY = Mth.clamp(endY - 3.4F, 0.15F, 1.4F);
		RDraw.centred(pose, collector, s.a, 0.0F, panY, 0.0F, 2.6F, 0.12F, 2.6F, light, OverlayTexture.NO_OVERLAY);
		if (detail) {
			RDraw.centred(pose, collector, s.c, 0.0F, panY + 0.12F, -1.28F, 2.64F, 0.08F, 0.06F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.c, 0.0F, panY + 0.12F, 1.28F, 2.64F, 0.08F, 0.06F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.c, -1.28F, panY + 0.12F, 0.0F, 0.06F, 0.08F, 2.6F, light, OverlayTexture.NO_OVERLAY);
			RDraw.centred(pose, collector, s.c, 1.28F, panY + 0.12F, 0.0F, 0.06F, 0.08F, 2.6F, light, OverlayTexture.NO_OVERLAY);
			// four chains converging from the pan's corners to the beam end above it
			for (int c = 0; c < 4; c++) {
				float ox = (c % 2 == 0 ? -1.0F : 1.0F) * 1.05F;
				float oz = (c < 2 ? -1.0F : 1.0F) * 1.05F;
				chain(pose, collector, s.b, ox, panY + 0.14F, oz, 0.0F, endY - 0.1F, 0.0F, light);
			}
		}
		if (index == 0 && detail) {
			// the beam from this pan's end, over the fulcrum, to the other pan's end
			pose.pushPose();
			pose.translate(s.cx, s.cy + beamY, s.cz);
			float yaw = (float) Math.atan2(-in[0], in[1]);
			pose.rotate(Axis.YP.rotation(-yaw));
			pose.rotate(Axis.XP.rotationDegrees(tilt));
			RDraw.box(pose, collector, s.a, -0.12F, -0.1F, -dist - 0.2F, 0.24F, 0.2F, dist * 2.0F + 0.4F, light, OverlayTexture.NO_OVERLAY);
			RDraw.box(pose, collector, s.c, -0.16F, -0.14F, -0.16F, 0.32F, 0.28F, 0.32F, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		if (detail) {
			boolean ok = mine == asked;
			int color = ok ? 0xFF8CFF8C : 0xFFFFD27A;
			RDraw.text(pose, collector, Component.literal(mine + " / " + asked), 0.0F, panY + 1.1F, 0.0F, face, 0.36F, color, false);
			if (s.part == TekaPartEntity.LIT || s.part == TekaPartEntity.HINT) {
				RSink ringQ = new RSink(RTex.RING, 8);
				float a = s.part == TekaPartEntity.LIT ? 0.7F : 0.35F * pulse(s, 0.25F);
				ringQ.flat(0.0F, panY + 0.14F, 0.0F, 1.7F, s.ageInTicks * 0.01F, RSink.argb(a, s.part == TekaPartEntity.LIT ? 0xFFD54A : 0x9FE8FF));
				ringQ.submit(collector, pose);
			}
		}
	}

	/** A chain of links from one point to another (iron chain block models along the segment). */
	private static void chain(PoseStack pose, SubmitNodeCollector collector, BlockModelRenderState link, float x0, float y0, float z0, float x1, float y1,
							  float z1, int light) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.05F) {
			return;
		}
		pose.pushPose();
		pose.translate(x0, y0, z0);
		// turn +y onto the segment, then tile the links a block at a time (a stretched chain smears its texture)
		Vector3f dirV = new Vector3f(dx / len, dy / len, dz / len);
		pose.rotate(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dirV));
		int links = Math.max(1, Math.round(len));
		float each = len / links;
		for (int i = 0; i < links; i++) {
			RDraw.box(pose, collector, link, -0.5F, i * each, -0.5F, 1.0F, each, 1.0F, light, OverlayTexture.NO_OVERLAY);
		}
		pose.popPose();
	}

	private void stele(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		if (!detail) {
			return;
		}
		String glyphs;
		if (s.arg >= 100) {
			glyphs = "祖訓";
		} else {
			glyphs = STELE_GLYPHS[Mth.clamp(s.arg, 0, STELE_GLYPHS.length - 1)];
		}
		float[] n = towardCentre(s);
		Quaternionf rot = RDraw.facing(n[0], n[1]);
		float glow = s.near < 7.0F ? 0.6F + 0.4F * pulse(s, 0.12F) : 0.45F;
		int gold = ARGB.color(255, Math.round(255 * glow), Math.round(210 * glow), Math.round(110 * glow));
		float fx = n[0] * 0.53F;
		float fz = n[1] * 0.53F;
		int count = glyphs.codePointCount(0, glyphs.length());
		int i = 0;
		for (int off = 0; off < glyphs.length(); ) {
			int cp = glyphs.codePointAt(off);
			off += Character.charCount(cp);
			float y = 2.35F - i * 0.85F + (count == 1 ? -0.4F : 0.0F);
			RDraw.text(pose, collector, Component.literal(new String(Character.toChars(cp))), fx, y, fz, rot, 0.7F, gold, false);
			i++;
		}
		Vector3f right = right(face);
		Vector3f up = up(face);
		RSink g = new RSink(RTex.GLOW, 8);
		g.billboard(fx * 1.2F, 2.0F, fz * 1.2F, right, up, 0.9F, RSink.argb(0.18F * glow, 0xFFD27A));
		cue(s, g, 3.9F, right, up, 0xFFE6A0);
		g.submit(collector, pose);
	}

	private void talisman(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		boolean torn = s.part == TekaPartEntity.LIT;
		float yawRad = s.yaw * Mth.DEG_TO_RAD;
		float nx = -Mth.sin(yawRad);
		float nz = Mth.cos(yawRad);
		pose.pushPose();
		pose.rotate(RDraw.facing(nx, nz));
		float w = 0.21F;
		float top = 1.3F;
		float mid = top - 0.5F;
		RSink paper = new RSink(RTex.PAPER, 32);
		int light = s.lightCoords;
		int white = 0xFFFFFFFF;
		if (torn) {
			// a ragged stub left on the pillar
			quad(paper, -w, top - 0.24F, w, top, 0.0F, 0.0F, 0.25F, white, light, 0.0F);
		} else {
			quad(paper, -w, mid, w, top, 0.0F, 0.0F, 0.5F, white, light, 0.0F);
			// the lower half flutters about the midline (two slow waves and a gust)
			float t = s.ageInTicks + s.id * 7.0F;
			float swing = 12.0F * Mth.sin(t * 0.16F) + 6.0F * Mth.sin(t * 0.43F + 1.1F) * Mth.sin(t * 0.05F);
			float a = swing * Mth.DEG_TO_RAD;
			float depth = Mth.sin(a) * 0.5F;
			float drop = Mth.cos(a) * 0.5F;
			quadBent(paper, -w, mid, w, mid - drop, depth, light);
		}
		paper.submit(collector, pose);
		if (detail && !torn) {
			RSink g = new RSink(RTex.GLOW, 8);
			g.billboard(0.0F, mid, -0.05F, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 0.6F,
					RSink.argb(0.15F + 0.1F * pulse(s, 0.1F), 0xFFD27A));
			g.submit(collector, pose);
		}
		pose.popPose();
		if (detail && !torn && s.near < 5.0F) {
			RSink g = new RSink(RTex.GLOW, 4);
			g.billboard(nx * 0.1F, 2.2F, nz * 0.1F, right(face), up(face), 0.14F + 0.05F * pulse(s, 0.3F), RSink.argb(0.6F * (5.0F - s.near) / 5.0F, 0xFF6A3A));
			g.submit(collector, pose);
		}
	}

	/** A flat paper quad in the xy plane at depth {@code z}, both faces; v from {@code v0} to {@code v1}. */
	private static void quad(RSink sink, float x0, float y0, float x1, float y1, float z, float v0, float v1, int color, int light, float unused) {
		sink.vertex(x0, y0, z, 0.0F, v1, color, light);
		sink.vertex(x1, y0, z, 1.0F, v1, color, light);
		sink.vertex(x1, y1, z, 1.0F, v0, color, light);
		sink.vertex(x0, y1, z, 0.0F, v0, color, light);
		sink.vertex(x0, y1, z, 0.0F, v0, color, light);
		sink.vertex(x1, y1, z, 1.0F, v0, color, light);
		sink.vertex(x1, y0, z, 1.0F, v1, color, light);
		sink.vertex(x0, y0, z, 0.0F, v1, color, light);
	}

	/** The lower half of the paper: from the midline (depth 0) down to {@code yb} at depth {@code zb}, both faces. */
	private static void quadBent(RSink sink, float x0, float ym, float x1, float yb, float zb, int light) {
		int c = 0xFFFFFFFF;
		sink.vertex(x0, yb, zb, 0.0F, 1.0F, c, light);
		sink.vertex(x1, yb, zb, 1.0F, 1.0F, c, light);
		sink.vertex(x1, ym, 0.0F, 1.0F, 0.5F, c, light);
		sink.vertex(x0, ym, 0.0F, 0.0F, 0.5F, c, light);
		sink.vertex(x0, ym, 0.0F, 0.0F, 0.5F, c, light);
		sink.vertex(x1, ym, 0.0F, 1.0F, 0.5F, c, light);
		sink.vertex(x1, yb, zb, 1.0F, 1.0F, c, light);
		sink.vertex(x0, yb, zb, 0.0F, 1.0F, c, light);
	}

	private void core(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		boolean broken = s.part == TekaPartEntity.WRONG;
		float integrity = Mth.clamp(s.arg / 100.0F, 0.0F, 1.0F);
		int rgb = broken ? 0xFF2A1A : RDraw.mix(0xFF3A2A, 0xFFD54A, integrity);
		RSink sigil = new RSink(RTex.SIGIL, 32);
		float t = s.ageInTicks;
		int rings = detail ? 3 : 1;
		for (int i = 0; i < rings; i++) {
			float y = 1.2F + i * 1.4F;
			float r = 1.5F - i * 0.2F;
			float wobble = broken ? 0.25F * Mth.sin(t * 0.3F + i) : 0.0F;
			float alpha = broken ? 0.35F + 0.25F * pulse(s, 0.4F) : 0.35F + 0.5F * integrity;
			sigil.flat(0.0F, y + wobble * 0.2F, 0.0F, r + wobble, (i % 2 == 0 ? 1 : -1) * t * (0.01F + 0.01F * i), RSink.argb(alpha, rgb));
		}
		sigil.submit(collector, pose);
		if (!detail) {
			return;
		}
		// six chains binding the core to the hall's ring, swaying; when the seal is broken they hang snapped from above
		int chains = EffectQuality.minimal() ? 3 : 6;
		for (int i = 0; i < chains; i++) {
			double a = i * Math.PI * 2.0D / chains + 0.3D;
			float sway = 0.15F * Mth.sin(t * 0.05F + i * 1.7F);
			float ox = (float) Math.cos(a) * 6.0F;
			float oz = (float) Math.sin(a) * 6.0F;
			if (broken) {
				chain(pose, collector, s.b, ox, 9.5F, oz, ox * 0.92F + sway, 5.5F + Mth.sin(t * 0.09F + i) * 0.2F, oz * 0.92F, s.lightCoords);
			} else {
				chain(pose, collector, s.b, (float) Math.cos(a) * 0.6F, 4.4F, (float) Math.sin(a) * 0.6F, ox + sway, 9.5F, oz - sway, s.lightCoords);
			}
		}
		if (broken) {
			RSink crack = new RSink(RTex.CRACK, 8);
			crack.flat(0.0F, 0.03F, 0.0F, 3.4F, 0.6F, RSink.argb(0.55F + 0.3F * pulse(s, 0.25F), 0xFF2010));
			crack.submit(collector, pose);
		}
		RSink strip = new RSink(RTex.STRIP, 8);
		strip.beam(0.0F, 0.0F, 0.0F, 0.0F, 6.0F, 0.0F, s.camX, s.camY, s.camZ, 1.6F, RSink.argb(0.25F + 0.15F * pulse(s, 0.08F), rgb), RSink.argb(0.0F, rgb));
		strip.submit(collector, pose);
		if (s.near < 18.0F) {
			Component line = broken ? Component.translatable("kultivator.reruntuhan.penjara.core.label_broken")
					: Component.translatable("kultivator.reruntuhan.penjara.core.label", s.arg);
			RDraw.board(pose, collector, line, 0.0F, 7.2F, 0.0F, face, 0.36F, ARGB.opaque(RDraw.lighten(rgb, 0.3F)), 0x66000000);
			RSink g = new RSink(RTex.GLOW, 4);
			cue(s, g, 3.2F, right(face), up(face), rgb);
			g.submit(collector, pose);
		}
	}

	private void pillar(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		boolean lit = s.part == TekaPartEntity.LIT;
		boolean locked = s.part == TekaPartEntity.LOCKED;
		float p = pulse(s, 0.2F);
		int rgb = lit ? 0xFFD54A : locked ? 0x6A6A6A : RDraw.mix(0xFF9A3A, 0xFFD54A, p);
		if (detail) {
			// 封 on the four faces of the pillar, at chest height
			for (int f = 0; f < 4; f++) {
				float nx = f == 0 ? 1.0F : f == 1 ? -1.0F : 0.0F;
				float nz = f == 2 ? 1.0F : f == 3 ? -1.0F : 0.0F;
				RDraw.text(pose, collector, Component.literal(Glyphs.SEAL), nx * 0.52F, 2.4F, nz * 0.52F, RDraw.facing(nx, nz), 0.55F,
						ARGB.opaque(lit ? 0xFFF2C0 : rgb), false);
			}
		}
		if (locked) {
			return;
		}
		RSink band = new RSink(RTex.STRIP, 64);
		band.band(0.0F, 1.7F, 3.1F, 0.0F, 0.85F, detail ? 12 : 6, RSink.argb(lit ? 0.7F : 0.25F + 0.3F * p, rgb), RSink.argb(0.0F, rgb));
		if (lit) {
			band.beam(0.0F, 10.0F, 0.0F, 0.0F, 15.5F, 0.0F, s.camX, s.camY, s.camZ, 0.8F, RSink.argb(0.6F, 0xFFE6A0), RSink.argb(0.1F, 0xFFE6A0));
		}
		band.submit(collector, pose);
		if (!lit && detail) {
			RSink g = new RSink(RTex.GLOW, 4);
			cue(s, g, 3.6F, right(face), up(face), 0xFFD54A);
			g.submit(collector, pose);
		}
	}

	private void gate(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		int band = Mth.clamp(s.arg, 1, 10);
		boolean open = band <= s.unlocked;
		int rgb = LobbyState.bandRgb(band);
		float[] n = towardCentre(s);
		// the curtain hangs across the gate: its plane is perpendicular to the way into the hall
		float tx = -n[1];
		float tz = n[0];
		RSink curtain = new RSink(RTex.CURTAIN, 32);
		float alpha = open ? 0.45F + 0.15F * pulse(s, 0.1F) : 0.12F;
		int layers = detail && !EffectQuality.minimal() ? 3 : 1;
		for (int l = 0; l < layers; l++) {
			float sway = 0.06F * Mth.sin(s.ageInTicks * 0.05F + l * 2.1F);
			float w = 3.4F - l * 0.4F;
			float off = (l - 1) * 0.12F;
			curtain.strip(n[0] * off + tx * sway, 0.0F, n[1] * off + tz * sway, n[0] * off - tx * sway, 6.6F, n[1] * off - tz * sway, tx, 0.0F, tz, w, w,
					RSink.argb(alpha, open ? rgb : 0x8A8A8A), RSink.argb(alpha * 0.2F, open ? rgb : 0x8A8A8A));
		}
		curtain.submit(collector, pose);
		if (!detail) {
			return;
		}
		RSink glow = new RSink(RTex.GLOW, 8);
		glow.flat(0.0F, 0.03F, 0.0F, 1.6F, 0.0F, RSink.argb(open ? 0.45F : 0.1F, rgb));
		glow.submit(collector, pose);
		if (s.distanceToCameraSq < 24.0D * 24.0D) {
			Component label = Component.translatable("kultivator.reruntuhan.menara.gate.label", LobbyState.NUMERALS[band], band * 10 - 9, band * 10);
			RDraw.board(pose, collector, label, 0.0F, 9.4F, 0.0F, face, 0.42F, open ? ARGB.opaque(rgb) : 0xFF8A8A8A, 0x66000000);
			if (!open) {
				RDraw.text(pose, collector, Component.literal(Glyphs.SEAL), 0.0F, 3.3F, 0.0F, RDraw.facing(n[0], n[1]), 1.4F, 0xC0A01A14, false);
			}
		}
	}

	private void exit(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		RSink curtain = new RSink(RTex.CURTAIN, 16);
		float[] n = towardCentre(s);
		float tx = -n[1];
		float tz = n[0];
		curtain.strip(0.0F, 0.0F, 0.0F, 0.0F, 3.2F, 0.0F, tx, 0.0F, tz, 1.8F, 1.8F, RSink.argb(0.4F + 0.1F * pulse(s, 0.12F), 0x9FE8FF),
				RSink.argb(0.0F, 0x9FE8FF));
		curtain.submit(collector, pose);
		RSink sigil = new RSink(RTex.SIGIL, 8);
		sigil.flat(0.0F, 0.03F, 0.0F, 1.1F, s.ageInTicks * 0.015F, RSink.argb(0.55F, 0x9FE8FF));
		sigil.submit(collector, pose);
		if (detail && s.distanceToCameraSq < 24.0D * 24.0D) {
			RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.exit.label"), 0.0F, 3.8F, 0.0F, face, 0.4F, 0xFF9FE8FF,
					0x66000000);
		}
	}

	private void pad(State s, PoseStack pose, SubmitNodeCollector collector, Quaternionf face, boolean detail) {
		boolean up = s.arg == 1;
		int rgb = up ? 0xFFD54A : 0x9FE8FF;
		RSink sigil = new RSink(RTex.SIGIL, 8);
		sigil.flat(0.0F, 0.04F, 0.0F, 1.15F, s.ageInTicks * (up ? 0.03F : -0.02F), RSink.argb(0.7F, rgb));
		sigil.submit(collector, pose);
		RSink strip = new RSink(RTex.STRIP, 64);
		strip.band(0.0F, 0.0F, 1.4F + 0.2F * pulse(s, 0.15F), 0.0F, 0.95F, detail ? 12 : 6, RSink.argb(0.45F, rgb), RSink.argb(0.0F, rgb));
		strip.submit(collector, pose);
		if (detail) {
			Component label = Component.translatable(up ? "kultivator.reruntuhan.menara.pad.up" : "kultivator.reruntuhan.menara.pad.out");
			RDraw.board(pose, collector, label, 0.0F, 2.0F + 0.05F * Mth.sin(s.ageInTicks * 0.1F), 0.0F, face, 0.36F, ARGB.opaque(rgb), 0x66000000);
		}
	}

	private void mirror(State s, PoseStack pose, SubmitNodeCollector collector, boolean detail) {
		boolean lit = s.part == TekaPartEntity.LIT;
		float[] n = towardCentre(s);
		float tx = -n[1];
		float tz = n[0];
		RSink sheen = new RSink(RTex.CURTAIN, 32);
		int layers = detail ? 3 : 1;
		for (int l = 0; l < layers; l++) {
			float drift = 0.25F * Mth.sin(s.ageInTicks * 0.03F + l * 2.3F);
			float alpha = (lit ? 0.35F : 0.14F) * (1.0F - l * 0.25F);
			float off = -0.46F + l * 0.03F;
			sheen.strip(n[0] * off + tx * drift, 1.0F, n[1] * off + tz * drift, n[0] * off + tx * drift, 5.0F, n[1] * off + tz * drift, tx, 0.0F, tz, 4.8F,
					4.8F, RSink.argb(alpha, lit ? 0xB08AFF : 0xD8D8F0), RSink.argb(alpha * 0.6F, lit ? 0x6A4AA0 : 0xB0B0D0));
		}
		sheen.submit(collector, pose);
	}
}

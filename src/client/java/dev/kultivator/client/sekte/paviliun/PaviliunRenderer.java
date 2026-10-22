package dev.kultivator.client.sekte.paviliun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.form.FormModels;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Module A in the world (DESIGN 36.2.5 "Visual", budgets 36.3.9), from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS},
 * camera-relative, vanilla render types only (never in Iris's shadow pass, never a post chain; the "heavy" look is
 * layered translucent quads with soft glow textures):
 * <ul>
 * <li>the names over the tombstones: the eight nearest within eight blocks, fading out toward eight, gilt for the
 * enshrined, with the realm under the name;</li>
 * <li>the spirit tablets on the altar table (or before the cemetery stele on templates 8-9): up to twelve lacquered
 * tablets with gold inscriptions in two tiers, turned toward the kneeling place, each with a faint breathing glow
 * (Normal and up);</li>
 * <li>incense smoke from the censer while a rite burns: up to 24 soft wisps (12 on Normal, none on Minimal) curling up in a
 * slow helix and fading, three ember points on top (every quality);</li>
 * <li>at night a warm glow round the cemetery's lanterns (Normal and up, within 48 blocks) and a pale soul light over an
 * enshrined grave (Maksimal);</li>
 * <li>the open sutra floating before each seated reader of the pavilion (the scroll prop is hidden while a body sits),
 * at most eight within 24 blocks, and the soft ring of light at a running Qingming.</li>
 * </ul>
 * Every quad is taken from the shared 7b frame budget ({@code EpicBudget.takeQuads}); if the budget is short the smoke and
 * glows go first. Idle cost: one check of {@link PaviliunWorld#any()} and of the nearby readers every frame.
 */
final class PaviliunRenderer {

	static final Identifier SMOKE = Kultivator.id("textures/sekte/paviliun/smoke.png");
	static final Identifier GLOW = Kultivator.id("textures/sekte/paviliun/glow.png");
	static final Identifier TABLET = Kultivator.id("textures/sekte/paviliun/tablet.png");

	private static final RenderType SMOKE_TYPE = RenderTypes.entityTranslucentEmissive(SMOKE);
	private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucentEmissive(GLOW);
	private static final RenderType TABLET_TYPE = RenderTypes.entityTranslucent(TABLET);
	private static final PaviliunSink SMOKES = new PaviliunSink(SMOKE_TYPE);
	private static final PaviliunSink GLOWS = new PaviliunSink(GLOW_TYPE);
	private static final PaviliunSink TABLETS = new PaviliunSink(TABLET_TYPE);

	static final double LABEL_RANGE = 8.0D;
	static final int LABEL_MAX = 8;
	static final double READER_RANGE = 24.0D;
	static final double NIGHT_RANGE = 48.0D;

	private static double camX;
	private static double camY;
	private static double camZ;
	private static final Vector3f RIGHT = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static final Vector3f FORWARD = new Vector3f();

	/** The {@code COLLECT_SUBMITS} listener. */
	static void collect(LevelRenderContext context) {
		if (ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			Vec3 pos = camera.pos;
			camX = pos.x;
			camY = pos.y;
			camZ = pos.z;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, RIGHT);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, UP);
			camera.orientation.transform(0.0F, 0.0F, -1.0F, FORWARD);
			float partial = context.levelState().worldPartialTicks;
			float now = level.getGameTime() + partial;
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			EffectQuality.Level quality = EffectQuality.level();
			SMOKES.reset();
			GLOWS.reset();
			TABLETS.reset();
			boolean began = false;
			List<DiscipleEntity> readers = readers(level, minecraft.player.position());
			if (!PaviliunWorld.any() && readers.isEmpty()) {
				return;
			}
			if (!EpicDirector.anyGeometry()) {
				EpicBudget.beginFrame();
			}
			for (DiscipleEntity reader : readers) {
				scroll(level, reader, partial, now, quality, poseStack, collector);
			}
			tablets(level, now, quality);
			smoke(level, now, quality);
			night(level, now, quality);
			rites(now, quality);
			if (EpicBudget.takeQuads(TABLETS.quads() + GLOWS.quads() + SMOKES.quads())) {
				GLOWS.submit(collector, poseStack);
				TABLETS.submit(collector, poseStack);
				SMOKES.submit(collector, poseStack);
			} else if (EpicBudget.takeQuads(TABLETS.quads())) {
				// short of budget: the tablets stay, the smoke and glows wait
				TABLETS.submit(collector, poseStack);
			}
			labels(level, now, poseStack, collector, camera);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Paviliun render failed", exception);
			SMOKES.reset();
			GLOWS.reset();
			TABLETS.reset();
		}
	}

	// ================================================================== readers

	private static List<DiscipleEntity> readers(ClientLevel level, Vec3 around) {
		List<DiscipleEntity> list = new ArrayList<>();
		AABB box = new AABB(around, around).inflate(READER_RANGE);
		for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof DiscipleEntity d && d.syncedActivity() == SekteIds.ACT_STUDY)) {
			if (e instanceof DiscipleEntity d && list.size() < 8) {
				list.add(d);
			}
		}
		return list;
	}

	/** The open sutra floating before a seated reader, rising and settling with its breath. */
	private static void scroll(ClientLevel level, DiscipleEntity reader, float partial, float now, EffectQuality.Level quality, PoseStack poseStack,
			SubmitNodeCollector collector) {
		Vec3 at = reader.getPosition(partial);
		float yaw = Mth.rotLerp(partial, reader.yBodyRotO, reader.yBodyRot);
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		float bob = Mth.sin(now * 0.08F + reader.getId()) * 0.04F;
		double x = at.x + fx * 0.55D - camX;
		double y = at.y + 0.62D + bob - camY;
		double z = at.z + fz * 0.55D - camZ;
		BlockPos lightAt = BlockPos.containing(at.x, at.y + 0.5D, at.z);
		int light = LightCoordsUtil.getLightCoords(level, lightAt);
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.rotateDegrees(Axis.YP, -yaw + 180.0F);
		poseStack.rotateDegrees(Axis.XP, -35.0F);
		poseStack.scale(0.55F, 0.55F, 0.55F);
		poseStack.translate(0.0F, 0.5F, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		FormModels.submitPiece("sekte_paviliun_scroll", poseStack, collector, light, 1.0F);
		poseStack.popPose();
		if (quality != EffectQuality.Level.MINIMAL) {
			float breath = 0.5F + 0.5F * Mth.sin(now * 0.1F + reader.getId());
			billboard(GLOWS, at.x + fx * 0.55D, at.y + 0.72D + bob, at.z + fz * 0.55D, 0.45F + 0.05F * breath,
					color(0.18F + 0.1F * breath, 0xFFE9A8));
		}
	}

	// ================================================================== the altar

	private static void tablets(ClientLevel level, float now, EffectQuality.Level quality) {
		BlockPos table = PaviliunWorld.table;
		int n = Math.min(12, PaviliunWorld.TABLETS.size());
		if (table == null || n == 0 || dist2(table) > 64.0D * 64.0D) {
			return;
		}
		double baseY = table.getY() + top(level, table);
		// the row runs across the direction to the kneeling place
		double dx = 0.0D;
		double dz = 1.0D;
		BlockPos kneel = PaviliunWorld.kneel;
		if (kneel != null && !kneel.equals(table)) {
			dx = kneel.getX() - table.getX();
			dz = kneel.getZ() - table.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			dx /= len;
			dz /= len;
		}
		double ax = -dz;
		double az = dx;
		double span = PaviliunWorld.tableIsAltar ? 2.3D : 0.85D;
		int perRow = n <= 6 ? n : (n + 1) / 2;
		float w = (float) Math.min(0.2D, span / perRow * 0.8D);
		float h = w * 2.3F;
		int light = LightCoordsUtil.getLightCoords(level, table.above());
		for (int i = 0; i < n; i++) {
			int row = i < perRow ? 0 : 1;
			int inRow = row == 0 ? i : i - perRow;
			int count = row == 0 ? perRow : n - perRow;
			double off = count == 1 ? 0.0D : (inRow / (double) (count - 1) - 0.5D) * span * 0.85D;
			// the back tier stands higher and further back
			double back = row == 1 ? -0.22D : 0.05D;
			double cx = table.getX() + 0.5D + ax * off + dx * back;
			double cz = table.getZ() + 0.5D + az * off + dz * back;
			double cy = baseY + (row == 1 ? 0.12D : 0.0D);
			float x0 = (float) (cx - ax * w * 0.5D - camX);
			float z0 = (float) (cz - az * w * 0.5D - camZ);
			float x1 = (float) (cx + ax * w * 0.5D - camX);
			float z1 = (float) (cz + az * w * 0.5D - camZ);
			float y0 = (float) (cy - camY);
			float y1 = (float) (cy + h - camY);
			int c = 0xFFFFFFFF;
			float nx = (float) dx;
			float nz = (float) dz;
			// both faces (the type culls none, but the back gets its own normal for the light)
			TABLETS.vertex(x0, y0, z0, 0.0F, 1.0F, c, light, nx, 0.0F, nz);
			TABLETS.vertex(x1, y0, z1, 1.0F, 1.0F, c, light, nx, 0.0F, nz);
			TABLETS.vertex(x1, y1, z1, 1.0F, 0.0F, c, light, nx, 0.0F, nz);
			TABLETS.vertex(x0, y1, z0, 0.0F, 0.0F, c, light, nx, 0.0F, nz);
			if (quality != EffectQuality.Level.MINIMAL) {
				float breath = 0.5F + 0.5F * Mth.sin(now * 0.05F + i * 1.3F);
				billboard(GLOWS, cx, cy + h * 0.55D, cz, w * 1.6F, color(0.12F + 0.08F * breath, 0xFFD27A));
			}
		}
	}

	/** The incense smoke and the ember points of the censer. */
	private static void smoke(ClientLevel level, float now, EffectQuality.Level quality) {
		BlockPos censer = PaviliunWorld.smoke;
		if (censer == null || dist2(censer) > 64.0D * 64.0D) {
			return;
		}
		double baseY = censer.getY() + Math.max(0.6D, top(level, censer));
		double cx = censer.getX() + 0.5D;
		double cz = censer.getZ() + 0.5D;
		for (int i = 0; i < 3; i++) {
			double a = i * 2.1D + now * 0.01D;
			float flicker = 0.7F + 0.3F * Mth.sin(now * 0.6F + i * 2.0F);
			billboard(GLOWS, cx + Math.cos(a) * 0.12D, baseY + 0.12D + i * 0.02D, cz + Math.sin(a) * 0.12D, 0.06F, color(0.9F * flicker, 0xFF8A3A));
			billboard(GLOWS, cx + Math.cos(a) * 0.12D, baseY + 0.12D + i * 0.02D, cz + Math.sin(a) * 0.12D, 0.16F, color(0.25F * flicker, 0xFFB060));
		}
		int wisps = quality == EffectQuality.Level.MAXIMAL ? 24 : quality == EffectQuality.Level.NORMAL ? 12 : 0;
		float fade = (float) Mth.clamp((PaviliunWorld.smokeUntil - now) / 100.0D, 0.0D, 1.0D);
		for (int i = 0; i < wisps; i++) {
			// each wisp loops over 120 ticks: rises 2.4 blocks, swells, curls and fades
			float life = ((now + i * (120.0F / wisps)) % 120.0F) / 120.0F;
			double rise = life * 2.4D;
			double angle = life * 5.0D + i * 0.7D;
			double radius = 0.06D + life * 0.32D;
			double drift = Mth.sin(now * 0.013F) * 0.25D * life;
			float size = 0.12F + life * 0.55F;
			float alpha = (float) (Math.sin(Math.PI * life) * 0.42D) * fade;
			billboard(SMOKES, cx + Math.cos(angle) * radius + drift, baseY + 0.25D + rise, cz + Math.sin(angle) * radius, size,
					color(alpha, 0xE8E4DC));
		}
	}

	/** The lanterns' glow and the soul lights of the enshrined at night. */
	private static void night(ClientLevel level, float now, EffectQuality.Level quality) {
		if (quality == EffectQuality.Level.MINIMAL) {
			return;
		}
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		if (time < 12500L || time > 23500L) {
			return;
		}
		for (BlockPos lantern : PaviliunWorld.LANTERNS) {
			if (dist2(lantern) > NIGHT_RANGE * NIGHT_RANGE) {
				continue;
			}
			float breath = 0.5F + 0.5F * Mth.sin(now * 0.07F + lantern.getX());
			billboard(GLOWS, lantern.getX() + 0.5D, lantern.getY() + 0.45D, lantern.getZ() + 0.5D, 0.9F + 0.1F * breath, color(0.22F + 0.06F * breath, 0xFFB060));
			billboard(GLOWS, lantern.getX() + 0.5D, lantern.getY() + 0.45D, lantern.getZ() + 0.5D, 0.35F, color(0.35F, 0xFFE0A0));
		}
		if (quality != EffectQuality.Level.MAXIMAL) {
			return;
		}
		for (PaviliunWorld.Grave g : PaviliunWorld.GRAVES) {
			if (!g.enshrined() || dist2(g.pos()) > NIGHT_RANGE * NIGHT_RANGE) {
				continue;
			}
			float hover = Mth.sin(now * 0.05F + g.pos().getZ()) * 0.12F;
			billboard(GLOWS, g.pos().getX() + 0.5D, g.pos().getY() + 2.4D + hover, g.pos().getZ() + 0.5D, 0.22F, color(0.5F, 0xB8E4FF));
			billboard(GLOWS, g.pos().getX() + 0.5D, g.pos().getY() + 2.4D + hover, g.pos().getZ() + 0.5D, 0.6F, color(0.14F, 0x8CC8FF));
		}
	}

	/** The soft ring of light at a running Qingming. */
	private static void rites(float now, EffectQuality.Level quality) {
		if (quality == EffectQuality.Level.MINIMAL) {
			return;
		}
		for (PaviliunWorld.Rite r : PaviliunWorld.RITES) {
			if (r.kind() != 1 || r.start() < 0L || dist2(r.pos()) > 64.0D * 64.0D) {
				continue;
			}
			float t = Mth.clamp((now - r.start()) / 60.0F, 0.0F, 1.0F) * Mth.clamp((r.ends() - now) / 60.0F, 0.0F, 1.0F);
			float breath = 0.5F + 0.5F * Mth.sin(now * 0.05F);
			ground(GLOWS, r.pos().getX() + 0.5D, r.pos().getY() + 0.03D, r.pos().getZ() + 0.5D, 4.5F + 0.3F * breath, color(0.16F * t, 0xCFF5D8));
		}
	}

	// ================================================================== labels

	private static void labels(ClientLevel level, float now, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (PaviliunWorld.GRAVES.isEmpty()) {
			return;
		}
		List<PaviliunWorld.Grave> near = new ArrayList<>();
		for (PaviliunWorld.Grave g : PaviliunWorld.GRAVES) {
			if (dist2(g.pos()) <= LABEL_RANGE * LABEL_RANGE) {
				near.add(g);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		near.sort(Comparator.comparingDouble(g -> dist2(g.pos())));
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < near.size() && i < LABEL_MAX; i++) {
			PaviliunWorld.Grave g = near.get(i);
			double d = Math.sqrt(dist2(g.pos()));
			float a = (float) Mth.clamp((LABEL_RANGE - d) / 3.0D, 0.0D, 1.0D);
			if (a <= 0.05F) {
				continue;
			}
			double dx = g.pos().getX() + 0.5D - camX;
			double dy = g.pos().getY() + 2.05D - camY;
			double dz = g.pos().getZ() + 0.5D - camZ;
			int nameColor = g.enshrined() ? 0xFFE2A0 : 0xE8E4DC;
			text(font, poseStack, collector, camera, g.name(), dx, dy, dz, 0.022F, alpha(a) << 24 | nameColor);
			Component realm = Rank.ofStep(g.step()).displayName();
			text(font, poseStack, collector, camera, realm, dx, dy - 0.24D, dz, 0.014F, alpha(a * 0.85F) << 24 | 0xB8C4BC);
		}
	}

	private static void text(Font font, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Component text, double dx,
			double dy, double dz, float scale, int argb) {
		FormattedCharSequence seq = text.getVisualOrderText();
		float width = font.width(seq);
		poseStack.pushPose();
		poseStack.translate(dx, dy, dz);
		poseStack.rotate(camera.orientation);
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -width / 2.0F, 0.0F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, argb, 0x50000000, 0);
		poseStack.popPose();
	}

	// ================================================================== helpers

	/** The top of the block's collision at {@code pos} (0 when it has none): tablets stand on a slab, smoke leaves a pot. */
	private static double top(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() ? 0.0D : shape.max(Direction.Axis.Y);
	}

	private static double dist2(BlockPos p) {
		double dx = p.getX() + 0.5D - camX;
		double dy = p.getY() + 0.5D - camY;
		double dz = p.getZ() + 0.5D - camZ;
		return dx * dx + dy * dy + dz * dz;
	}

	private static void billboard(PaviliunSink sink, double wx, double wy, double wz, float half, int color) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		float rx = RIGHT.x * half;
		float ry = RIGHT.y * half;
		float rz = RIGHT.z * half;
		float ux = UP.x * half;
		float uy = UP.y * half;
		float uz = UP.z * half;
		sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
	}

	private static void ground(PaviliunSink sink, double wx, double wy, double wz, float half, int color) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		sink.vertex(x - half, y, z - half, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x - half, y, z + half, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + half, y, z + half, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + half, y, z - half, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
	}

	static int alpha(float a) {
		return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
	}

	private static int color(float a, int rgb) {
		return alpha(a) << 24 | (rgb & 0xFFFFFF);
	}

	static void clear() {
		SMOKES.reset();
		GLOWS.reset();
		TABLETS.reset();
	}

	private PaviliunRenderer() {
	}
}

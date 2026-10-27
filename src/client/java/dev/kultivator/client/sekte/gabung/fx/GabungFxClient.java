package dev.kultivator.client.sekte.gabung.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.sekte.gabung.GabungTitik;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client entry of 41V's effects (DESIGN 41.4.6, 41.5.4, 41.12.3), called once from {@code GabungClient.init}:
 * <ul>
 * <li>{@link VisualKelas}: the class of a known NPC sect made visible at its gate (extra standards 4/6/8 with the sect's
 * emblem through the 36S banner renderer, spirit lanterns along the approach, a thin formation dome, circling cranes, a
 * pillar of light at dusk, a crown of golden cloud), LOD 48/128, Minimal = still banners only;</li>
 * <li>{@link TanggaIlusiGabung}: the illusory stair of light over the real test stair during stage 1 (120/400/1000
 * steps by {@code EffectQuality}, within 96 blocks), mist at its foot, the heart demon's face and a gust at each
 * illusion, the stair reddening with Mountain Pressure;</li>
 * <li>{@link UjianFx}: the stage 2 effects of the ten style tests, read from the test HUD bytes.</li>
 * </ul>
 * One {@code COLLECT_SUBMITS} listener draws all three into shared camera-relative quad sinks of vanilla render types
 * ({@code entityTranslucentEmissive}: translucent, full bright, no depth write), never in Iris's shadow pass, no post
 * chain; every group takes its quads from the shared 7b frame budget ({@code EpicBudget.takeQuads}). Idle cost: a few
 * booleans. The four client switches of 41.13.1 live here ({@link #saklar}); {@code JubahSekteLayer} reads the same keys.
 * <p>
 * Textures: {@code textures/sekte/gabung_fx/fx.png} (atlas of 4 x 4 cells, {@code tools/gen_gabung_fx.py}; cell ids
 * {@code SEL_*}), the crane sprite of 37 ({@code sekte/langit/bangau.png}) and the stair treads, risers and heart-demon
 * face of 36G ({@code sekte/tangga/*}), all read only.
 */
public final class GabungFxClient {

	// ================================================================== client switches (41.13.1)

	/** The member robe drawn over chest armour (read by {@code JubahSekteLayer}). */
	public static final String JUBAH_DI_ATAS_ZIRAH = "gabung.jubahDiAtasZirah";
	/** The class visuals at the NPC sect gates ({@link VisualKelas}). */
	public static final String VISUAL_KELAS = "gabung.visualKelas";
	/** The illusion stair and the style test effects ({@link TanggaIlusiGabung}, {@link UjianFx}). */
	public static final String TANGGA_ILUSI = "gabung.tanggaIlusi";
	/** The rank line above member names (read by {@code JubahSekteLayer}). */
	public static final String NAMETAG_PANGKAT = "gabung.nametagPangkat";
	/** The four switches in settings order. */
	public static final List<String> SAKLAR = List.of(JUBAH_DI_ATAS_ZIRAH, VISUAL_KELAS, TANGGA_ILUSI, NAMETAG_PANGKAT);
	/** Their lang ids under {@link #LANG_SETELAN} (same order). */
	public static final List<String> SAKLAR_LANG = List.of("jubah_di_atas_zirah", "visual_kelas", "tangga_ilusi", "nametag_pangkat");
	/** Lang prefix of the settings rows (fragment {@code sekte_gabung_panduan}). */
	public static final String LANG_SETELAN = "kultivator.gabung.setelan.";

	/** A client switch (default on), stored in {@code kultivator-client.json} beside the other client settings. */
	public static boolean saklar(String kunci) {
		JsonElement e = EffectQuality.get(kunci);
		try {
			return e == null || !e.isJsonPrimitive() || e.getAsBoolean();
		} catch (RuntimeException ignored) {
			return true;
		}
	}

	/** Sets a client switch and saves the file. */
	public static void setSaklar(String kunci, boolean on) {
		EffectQuality.put(kunci, new JsonPrimitive(on));
		if (!on && kunci.equals(VISUAL_KELAS)) {
			VisualKelas.lepasSemua();
		}
	}

	// ================================================================== atlas

	static final Identifier FX = Kultivator.id("textures/sekte/gabung_fx/fx.png");
	static final Identifier BANGAU = Kultivator.id("textures/sekte/langit/bangau.png");
	static final Identifier STEP = Kultivator.id("textures/sekte/tangga/step.png");
	static final Identifier RISER = Kultivator.id("textures/sekte/tangga/riser.png");
	static final Identifier FACE = Kultivator.id("textures/sekte/tangga/face.png");

	/** Cells of {@code fx.png} (column + 4 x row), see {@code tools/gen_gabung_fx.py}. */
	static final int SEL_GLOW = 0, SEL_LENTERA = 1, SEL_KISI = 2, SEL_AWAN = 3;
	static final int SEL_TEBASAN = 4, SEL_KABUT = 5, SEL_PERCIK = 6, SEL_CINCIN = 7;
	static final int SEL_SEGEL = 8, SEL_RETAK = 9, SEL_DAUN = 10, SEL_TETES = 11;
	static final int SEL_ASAP = 12, SEL_MATA = 13, SEL_API = 14, SEL_GUNUNG = 15;

	static float u0(int sel) {
		return (sel & 3) * 0.25F;
	}

	static float v0(int sel) {
		return (sel >> 2) * 0.25F;
	}

	// ================================================================== sinks

	/** Sprites of the atlas (lanterns, clouds, sparks, seals, glyphs). */
	static final Sink SPRITE = new Sink(() -> RenderTypes.entityTranslucentEmissive(FX));
	/** Soft things drawn first (mist, the dome lattice). */
	static final Sink LUNAK = new Sink(() -> RenderTypes.entityTranslucentEmissive(FX));
	static final Sink BURUNG = new Sink(() -> RenderTypes.entityTranslucentEmissive(BANGAU));
	static final Sink TAPAK = new Sink(() -> RenderTypes.entityTranslucentEmissive(STEP));
	static final Sink TEGAK = new Sink(() -> RenderTypes.entityTranslucentEmissive(RISER));
	static final Sink WAJAH = new Sink(() -> RenderTypes.entityTranslucentEmissive(FACE));
	/** Light strips (rails, qi streams, trigram bars, gusts). */
	static final Sink CAHAYA = new Sink(() -> EpicRenderTypes.GLOW);

	private static final Sink[] URUTAN = {LUNAK, TEGAK, TAPAK, BURUNG, CAHAYA, SPRITE, WAJAH};

	// ================================================================== frame state shared by the three

	static double camX;
	static double camY;
	static double camZ;
	static final Vector3f RIGHT = new Vector3f();
	static final Vector3f UP = new Vector3f();
	static final Vector3f FORWARD = new Vector3f();
	/** Client ticks + partial of this frame. */
	static double now;
	static float partial;

	private static final int PILAR_MAKS = 4;
	private static final double[] PILAR = new double[PILAR_MAKS * 4];
	private static int pilar;
	private static long ticks;
	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(GabungFxClient::collect);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		// where the halls stand: the dome over the aula and the crown over the puncak (GabungTitik, D-41V)
		ClientPlayNetworking.registerGlobalReceiver(GabungTitik.TitikKelas.TYPE, (payload, context) -> {
			for (GabungTitik.Entri e : payload.entri()) {
				VisualKelas.titik(e.sekte(), e.aula(), e.puncak());
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		try {
			VisualKelas.tick(level, ticks);
			TanggaIlusiGabung.tick(minecraft.player, ticks);
			UjianFx.tick(level, minecraft.player, ticks);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Gabung fx tick failed", exception);
		}
	}

	private static void clear() {
		VisualKelas.clear();
		TanggaIlusiGabung.clear();
		UjianFx.clear();
	}

	/** Client ticks since start (the animation clock). */
	static long ticks() {
		return ticks;
	}

	private static void collect(LevelRenderContext context) {
		if (ShaderCompat.shadowPass() || !(VisualKelas.any() || TanggaIlusiGabung.any() || UjianFx.any())) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
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
			partial = context.levelState().worldPartialTicks;
			now = ticks + partial;
			for (Sink sink : URUTAN) {
				sink.reset();
			}
			pilar = 0;
			VisualKelas.draw(level);
			TanggaIlusiGabung.draw(level);
			UjianFx.draw(level);
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (Sink sink : URUTAN) {
				sink.submit(collector, poseStack);
			}
			for (int i = 0; i < pilar; i++) {
				poseStack.pushPose();
				// submitBeaconBeam adds translate(0.5, 0, 0.5) itself
				poseStack.translate(PILAR[i * 4] - camX - 0.5D, PILAR[i * 4 + 1] - camY, PILAR[i * 4 + 2] - camZ - 0.5D);
				BeaconRenderer.submitBeaconBeam(poseStack, collector, EpicRenderTypes.BEACON_BEAM, 1.0F, (float) now, 0, 64,
						(int) PILAR[i * 4 + 3], 0.08F, 0.3F);
				poseStack.popPose();
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Gabung fx render failed", exception);
			for (Sink sink : URUTAN) {
				sink.reset();
			}
		}
	}

	/** Queues a beacon-style light pillar this frame (the caller took {@code EpicBudget.takePillar}). */
	static void pilar(double x, double y, double z, int argb) {
		if (pilar >= PILAR_MAKS) {
			return;
		}
		PILAR[pilar * 4] = x;
		PILAR[pilar * 4 + 1] = y;
		PILAR[pilar * 4 + 2] = z;
		PILAR[pilar * 4 + 3] = argb;
		pilar++;
	}

	static double jarak(double x, double y, double z) {
		double dx = x - camX;
		double dy = y - camY;
		double dz = z - camZ;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// ================================================================== quad helpers (world coordinates in, camera-relative out)

	/** A camera-facing square of half size {@code half} showing atlas cell {@code sel}, turned by {@code roll} radians. */
	static void sprite(Sink sink, double wx, double wy, double wz, float half, int argb, int sel, float roll) {
		float u0 = u0(sel);
		float v0 = v0(sel);
		billboard(sink, wx, wy, wz, half, half, argb, u0, v0, u0 + 0.25F, v0 + 0.25F, roll);
	}

	/** A camera-facing rectangle ({@code hw} x {@code hh} half sizes) with its own uv window and roll. */
	static void billboard(Sink sink, double wx, double wy, double wz, float hw, float hh, int argb, float u0, float v0, float u1, float v1,
			float roll) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		float c = (float) Math.cos(roll);
		float s = (float) Math.sin(roll);
		// rotated right and up axes
		float rx = (RIGHT.x * c + UP.x * s) * hw;
		float ry = (RIGHT.y * c + UP.y * s) * hw;
		float rz = (RIGHT.z * c + UP.z * s) * hw;
		float ux = (UP.x * c - RIGHT.x * s) * hh;
		float uy = (UP.y * c - RIGHT.y * s) * hh;
		float uz = (UP.z * c - RIGHT.z * s) * hh;
		float nx = -FORWARD.x;
		float ny = -FORWARD.y;
		float nz = -FORWARD.z;
		sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, u0, v1, argb, nx, ny, nz);
		sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, u1, v1, argb, nx, ny, nz);
		sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, u1, v0, argb, nx, ny, nz);
		sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, u0, v0, argb, nx, ny, nz);
	}

	/** A camera-facing sprite that stays upright (turns only about the vertical axis): lanterns, banners of light. */
	static void tegak(Sink sink, double wx, double wy, double wz, float hw, float hh, int argb, int sel) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		float fx = (float) (wx - camX);
		float fz = (float) (wz - camZ);
		float len = (float) Math.sqrt(fx * fx + fz * fz);
		float sx;
		float sz;
		if (len < 1.0E-4F) {
			sx = RIGHT.x;
			sz = RIGHT.z;
		} else {
			sx = -fz / len;
			sz = fx / len;
		}
		float u0 = u0(sel);
		float v0 = v0(sel);
		float u1 = u0 + 0.25F;
		float v1 = v0 + 0.25F;
		float nx = len < 1.0E-4F ? 0.0F : -fx / len;
		float nz = len < 1.0E-4F ? 1.0F : -fz / len;
		sink.vertex(x - sx * hw, y - hh, z - sz * hw, u0, v1, argb, nx, 0.0F, nz);
		sink.vertex(x + sx * hw, y - hh, z + sz * hw, u1, v1, argb, nx, 0.0F, nz);
		sink.vertex(x + sx * hw, y + hh, z + sz * hw, u1, v0, argb, nx, 0.0F, nz);
		sink.vertex(x - sx * hw, y + hh, z - sz * hw, u0, v0, argb, nx, 0.0F, nz);
	}

	/** A flat square on the ground plane (y up), turned by {@code angle}, showing cell {@code sel}. */
	static void datar(Sink sink, double wx, double wy, double wz, float half, int argb, int sel, double angle) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		float c = (float) Math.cos(angle) * half;
		float s = (float) Math.sin(angle) * half;
		float u0 = u0(sel);
		float v0 = v0(sel);
		float u1 = u0 + 0.25F;
		float v1 = v0 + 0.25F;
		sink.vertex(x - c + s, y, z - s - c, u0, v0, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex(x - c - s, y, z - s + c, u0, v1, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + c - s, y, z + s + c, u1, v1, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + c + s, y, z + s - c, u1, v0, argb, 0.0F, 1.0F, 0.0F);
	}

	/** A strip between two world points that turns about its axis to face the camera ({@code width} wide). */
	static void beam(Sink sink, double ax, double ay, double az, double bx, double by, double bz, float width, int ca, int cb) {
		float x0 = (float) (ax - camX);
		float y0 = (float) (ay - camY);
		float z0 = (float) (az - camZ);
		float x1 = (float) (bx - camX);
		float y1 = (float) (by - camY);
		float z1 = (float) (bz - camZ);
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float mx = (x0 + x1) * 0.5F;
		float my = (y0 + y1) * 0.5F;
		float mz = (z0 + z1) * 0.5F;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0E-5F) {
			sx = RIGHT.x;
			sy = RIGHT.y;
			sz = RIGHT.z;
			length = 1.0F;
		}
		float h = width * 0.5F / length;
		sx *= h;
		sy *= h;
		sz *= h;
		sink.vertex(x0 - sx, y0 - sy, z0 - sz, 0.0F, 0.0F, ca, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x0 + sx, y0 + sy, z0 + sz, 0.0F, 1.0F, ca, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x1 + sx, y1 + sy, z1 + sz, 1.0F, 1.0F, cb, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x1 - sx, y1 - sy, z1 - sz, 1.0F, 0.0F, cb, -FORWARD.x, -FORWARD.y, -FORWARD.z);
	}

	/**
	 * A trigram (DESIGN 41.5.4 Labirin Batu: 0-7 = Qian, Dui, Li, Zhen, Xun, Kan, Gen, Kun) as three camera-facing bars
	 * of light, top line first, a broken line split in two; {@code size} is the glyph's half height.
	 */
	static void trigram(double wx, double wy, double wz, int n, float size, int argb) {
		if (n < 0 || n > 7) {
			return;
		}
		int pola = TRIGRAM[n];
		float bar = size * 0.32F;
		for (int line = 0; line < 3; line++) {
			// bit 2 = top line, 1 = solid
			boolean solid = (pola >> (2 - line) & 1) == 1;
			double y = wy + size - line * size;
			double lx = RIGHT.x * size;
			double ly = RIGHT.y * size;
			double lz = RIGHT.z * size;
			if (solid) {
				beam(CAHAYA, wx - lx, y - ly, wz - lz, wx + lx, y + ly, wz + lz, bar, argb, argb);
			} else {
				double gx = RIGHT.x * size * 0.22D;
				double gy = RIGHT.y * size * 0.22D;
				double gz = RIGHT.z * size * 0.22D;
				beam(CAHAYA, wx - lx, y - ly, wz - lz, wx - gx, y - gy, wz - gz, bar, argb, argb);
				beam(CAHAYA, wx + gx, y + gy, wz + gz, wx + lx, y + ly, wz + lz, bar, argb, argb);
			}
		}
	}

	/** Line patterns of the eight trigrams, top line as bit 2 (1 = solid): ☰ ☱ ☲ ☳ ☴ ☵ ☶ ☷. */
	private static final int[] TRIGRAM = {0b111, 0b011, 0b101, 0b001, 0b110, 0b010, 0b100, 0b000};

	static int argb(float alpha, int rgb) {
		return Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | (rgb & 0xFFFFFF);
	}

	static float smooth(float t) {
		float v = Math.max(0.0F, Math.min(1.0F, t));
		return v * v * (3.0F - 2.0F * v);
	}

	static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return (r << 16) | (g << 8) | bl;
	}

	/** Effect quality now: 0 Minimal, 1 Normal, 2 Maksimal. */
	static int mutu() {
		return EffectQuality.level().ordinal();
	}

	// ================================================================== the sink

	/**
	 * Camera-relative vertices of one render type for one frame, submitted once through a reused writer (the pattern of
	 * 36G's {@code TanggaSink}, 41V's own copy). The render type is created on first submit (never in a static
	 * initialiser); entity vertex format with uv, overlay, full-bright light and a normal.
	 */
	static final class Sink {

		private static final int STRIDE = 8;
		private final Supplier<RenderType> factory;
		private RenderType type;
		private float[] data = new float[STRIDE * 512];
		private int[] colors = new int[512];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(Supplier<RenderType> factory) {
			this.factory = factory;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[size];
				System.arraycopy(colors, 0, moreColors, 0, colors.length);
				colors = moreColors;
			}
			int o = count * STRIDE;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			data[o + 5] = nx;
			data[o + 6] = ny;
			data[o + 7] = nz;
			colors[count] = argb;
			count++;
		}

		void reset() {
			count = 0;
		}

		void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
			if (count < 4) {
				return;
			}
			if (type == null) {
				type = factory.get();
			}
			collector.submitCustomGeometry(poseStack, type, writer);
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int vertices = count / 4 * 4;
			for (int i = 0; i < vertices; i++) {
				int o = i * STRIDE;
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
						.setColor(colors[i])
						.setUv(data[o + 3], data[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
			}
		}
	}

	private GabungFxClient() {
	}
}

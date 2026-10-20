package dev.kultivator.client.epic.cosmic;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Langit Kosmik (DESIGN 30.2.8): the "space" of the journey up the beam, shown only in the protagonist's own
 * {@code cosmic()} film shots (never to bystanders: it is started only by the local film). A star river, nebulae, a
 * celestial sun and moon, heavenly palace silhouettes on a cloud bank and, at Maximal without a shader pack, the vanilla
 * end-portal starfield as a far backing; built from seeded directions and drawn camera-relative at the sky-projection
 * radius from {@code EpicRenderer} on {@code COLLECT_SUBMITS}.
 * <p>
 * Depth: by default every layer is depth tested and fades below 20° elevation (the hills stay in front). Only while the
 * shot guarantees an open sky (the camera looks up 45° or more and stands at least 4 b above the highest of 9 heightmap
 * samples in the view cone within 48 b; re-checked every 4 ticks) the layers switch to the see-through text pipeline and
 * may cover clouds and fog, which is what makes it read as space. Budgets 30.2.8 / 30.2.13; under a shader pack one large
 * layer (the star river) and 0.6 × the stars. Setting "Langit kosmik" off → never shown. Client thread only.
 */
public final class CosmicSky {

	/** The palette of the nebulae: violet-teal, ember-gold, frost-blue. */
	public enum Palette {
		VIOLET(0xB57CFF, 0x5FE0D0), EMBER(0xFF8A3A, 0xFFD54A), FROST(0x9FD8FF, 0xE8F4FF), RED(0xFF3A4A, 0x8A2AFF), JADE(0x5FD6A0, 0xFFE8A0);

		final int a;
		final int b;

		Palette(int a, int b) {
			this.a = a;
			this.b = b;
		}
	}

	private static int left;
	private static int life;
	private static Palette palette = Palette.VIOLET;
	private static long seed;
	private static float yaw;
	private static boolean sun = true;
	private static boolean moon = true;
	private static boolean openSky;
	private static int checkIn;
	private static long ticks;

	/**
	 * Shows the backdrop for {@code ticks} around the view yaw {@code centerYaw} (degrees), in {@code colours}, with or
	 * without the sun and moon. Refused with "Langit kosmik" off.
	 */
	public static boolean show(int ticks, Palette colours, long cosmicSeed, float centerYaw, boolean withSun, boolean withMoon) {
		if (!EpicSettings.cosmic()) {
			return false;
		}
		left = Math.max(1, Math.min(20 * 30, ticks));
		life = left;
		palette = colours;
		seed = cosmicSeed;
		yaw = centerYaw;
		sun = withSun;
		moon = withMoon;
		checkIn = 0;
		CosmicLayers.build(seed);
		return true;
	}

	public static boolean active() {
		return left > 0;
	}

	public static void hide(int fadeTicks) {
		if (left > fadeTicks) {
			left = Math.max(1, fadeTicks);
			life = Math.max(life, left);
		}
	}

	public static void clear() {
		left = 0;
	}

	/** Once per client tick. */
	public static void tick() {
		ticks++;
		if (left <= 0) {
			return;
		}
		left--;
		if (--checkIn <= 0) {
			checkIn = 4;
			openSky = openSky();
		}
	}

	/** 0..1 of the fades in (10 ticks) and out (12 ticks). */
	static float fade() {
		float in = Math.min(1.0F, (life - left) / 10.0F);
		float out = Math.min(1.0F, left / 12.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	static Palette palette() {
		return palette;
	}

	static float yaw() {
		return yaw;
	}

	static boolean sun() {
		return sun;
	}

	static boolean moon() {
		return moon;
	}

	static boolean seeThrough() {
		return openSky;
	}

	static long ticks() {
		return ticks;
	}

	/** 0 Minimal, 1 Normal, 2 Maximal (the frame guard first lowers these layers); −1 under a shader pack. */
	static int tier() {
		return ShaderCompat.packOn() ? -1 : EpicBudget.tierOrMinimal();
	}

	/** The open-sky test: looking up ≥ 45° and ≥ 4 b above the highest of 9 samples within 48 b ahead. */
	private static boolean openSky() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return false;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (camera.xRot() > -45.0F) {
			return false;
		}
		Vec3 pos = camera.position();
		double yawRad = Math.toRadians(camera.yRot());
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);
		int highest = level.getMinY();
		for (int i = 0; i < 9; i++) {
			double along = 4.0D + (i / 3) * 20.0D;
			double side = ((i % 3) - 1) * along * 0.5D;
			int x = Mth.floor(pos.x + fx * along - fz * side);
			int z = Mth.floor(pos.z + fz * along + fx * side);
			highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
		}
		highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z)));
		return pos.y >= highest + 4.0D;
	}

	/** Draws the layers (from {@code EpicRenderer.collect}). */
	public static void render(EpicRenderer.Frame frame) {
		if (left <= 0) {
			return;
		}
		CosmicLayers.render(frame);
	}

	private CosmicSky() {
	}
}

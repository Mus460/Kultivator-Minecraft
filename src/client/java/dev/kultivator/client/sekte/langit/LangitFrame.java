package dev.kultivator.client.sekte.langit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The shared numbers of one frame of the sky-sect visuals (DESIGN 37.6): camera position and view axes, the partial
 * tick, the world clock, the sun's direction (the vanilla sky's own: up turned by the {@code SUN_ANGLE} environment
 * attribute about X, then −90° about Y), how much day, dusk and night it is, the rain and thunder, the sky colour, and
 * the hall the camera is at. One mutable instance, filled by {@link LangitVisuals#collect} before any layer draws.
 */
final class LangitFrame {

	static final LangitFrame INSTANCE = new LangitFrame();

	double camX;
	double camY;
	double camZ;
	float partial;
	/** Game ticks (wrapped at 240 000) plus the partial tick: animation time. */
	float ticks;
	/** Seconds of animation time. */
	float seconds;
	/** The overworld clock in the day, 0-24 000. */
	long dayTime;
	/** The day number of the overworld clock. */
	long day;

	float rightX;
	float rightY;
	float rightZ;
	float upX;
	float upY;
	float upZ;
	float fwdX;
	float fwdY;
	float fwdZ;

	float sunX;
	float sunY;
	float sunZ;
	/** 1 at noon, 0 at night (from the sun's height). */
	float daylight;
	/** 1 around sunrise and sunset (23 000-1 000, 11 500-13 000), 0 otherwise. */
	float golden;
	/** 1 deep in the night. */
	float night;
	float rain;
	float thunder;
	/** The vanilla star brightness (0-1) and the star sphere's turn (radians). */
	float starBrightness;
	float starAngle;
	/** Where the fog starts (blocks): sky layers stay inside it. */
	float fogStart;
	/** The vanilla sky colour this frame (RGB). */
	int skyRgb;
	/** Quality tier in effect for the sky layers: −1 nothing, 0 Minimal, 1 Normal, 2 Maximal. */
	int tier;
	/** Tier for particle-like layers (vanilla "Partikel" Minimal forces 0). */
	int particleTier;
	boolean pack;

	LangitSkyState.Hall hall;
	/** Camera-relative position of the hall's origin. */
	float ox;
	float oy;
	float oz;

	private final Vector3f tmp = new Vector3f();

	void setup(Minecraft minecraft, ClientLevel level, CameraRenderState camera, float partialTicks) {
		camX = camera.pos.x;
		camY = camera.pos.y;
		camZ = camera.pos.z;
		partial = partialTicks;
		ticks = (float) (level.getGameTime() % 240000L) + partialTicks;
		seconds = ticks / 20.0F;
		long clock = level.getOverworldClockTime();
		dayTime = Math.floorMod(clock, 24000L);
		day = Math.floorDiv(clock, 24000L);
		camera.orientation.transform(1.0F, 0.0F, 0.0F, tmp);
		rightX = tmp.x;
		rightY = tmp.y;
		rightZ = tmp.z;
		camera.orientation.transform(0.0F, 1.0F, 0.0F, tmp);
		upX = tmp.x;
		upY = tmp.y;
		upZ = tmp.z;
		camera.orientation.transform(0.0F, 0.0F, -1.0F, tmp);
		fwdX = tmp.x;
		fwdY = tmp.y;
		fwdZ = tmp.z;
		float angle = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * Mth.DEG_TO_RAD;
		sunX = -Mth.sin(angle);
		sunY = Mth.cos(angle);
		sunZ = 0.0F;
		daylight = Mth.clamp(sunY * 2.5F + 0.35F, 0.0F, 1.0F);
		night = Mth.clamp(-sunY * 3.0F - 0.1F, 0.0F, 1.0F);
		golden = goldenOf(dayTime);
		rain = level.getRainLevel(partialTicks);
		thunder = level.getThunderLevel(partialTicks);
		starBrightness = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTicks);
		starAngle = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.STAR_ANGLE, partialTicks) * Mth.DEG_TO_RAD;
		float fog = camera.fogData.renderDistanceStart;
		if (!(fog > 8.0F)) {
			fog = minecraft.options.getEffectiveRenderDistance() * 16.0F * 0.9F;
		}
		fogStart = fog;
		Vector3fc sky = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);
		skyRgb = rgb(sky.x(), sky.y(), sky.z());
		tier = LangitBudget.tier();
		particleTier = LangitBudget.particleTier(minecraft, tier);
		pack = dev.kultivator.client.epic.ShaderCompat.packOn();
	}

	void at(LangitSkyState.Hall hall) {
		this.hall = hall;
		ox = (float) (hall.origin.getX() + 0.5D - camX);
		oy = (float) (hall.origin.getY() + 0.5D - camY);
		oz = (float) (hall.origin.getZ() + 0.5D - camZ);
	}

	float rx(double x) {
		return (float) (x - camX);
	}

	float ry(double y) {
		return (float) (y - camY);
	}

	float rz(double z) {
		return (float) (z - camZ);
	}

	/** Golden hour weight: 23 000-1 000 (dawn, peaking at 0) and 11 500-13 000 (dusk, peaking at 12 250). */
	static float goldenOf(long time) {
		float t = time;
		if (t >= 23000.0F || t <= 1000.0F) {
			float d = t >= 23000.0F ? 24000.0F - t : t;
			return 1.0F - Mth.clamp(d / 1000.0F, 0.0F, 1.0F);
		}
		if (t >= 11500.0F && t <= 13000.0F) {
			return 1.0F - Mth.clamp(Math.abs(t - 12250.0F) / 750.0F, 0.0F, 1.0F);
		}
		return 0.0F;
	}

	/** True at dawn (23 000-1 000), as opposed to dusk. */
	boolean dawn() {
		return dayTime >= 23000L || dayTime <= 1000L;
	}

	private static int rgb(float r, float g, float b) {
		return Mth.clamp(Math.round(r * 255.0F), 0, 255) << 16 | Mth.clamp(Math.round(g * 255.0F), 0, 255) << 8
				| Mth.clamp(Math.round(b * 255.0F), 0, 255);
	}

	private LangitFrame() {
	}
}

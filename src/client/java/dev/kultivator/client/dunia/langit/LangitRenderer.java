package dev.kultivator.client.dunia.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.SkyProjection;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.langit.LangitFxPayload;
import dev.kultivator.dunia.langit.TreasureRewards;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

/**
 * The world geometry of module L (DESIGN 34.5.8-34.5.10), drawn from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}
 * after the 7b {@code EpicRenderer} (never in a shader pack's shadow pass), camera-relative, through the 7b frame budget
 * ({@code EpicBudget.takeQuads}, {@code takePillar}; × 0.6 under a shader pack): layered translucent emissive quads only
 * (rule V2: no post chain, no core shader, Sodium and Iris safe). Idle cost: a handful of booleans.
 * <ul>
 * <li>Gerhana Darah: a crimson disc over the vanilla moon (its direction from the {@code MOON_ANGLE} environment attribute,
 * the vanilla sky's own rotation) with three pulsing halos; Bulan Kembar: a pale silver second moon on the mirrored arc;
 * 1 + 3 and 1 + 2 quads, sky-projected (DESIGN 23.10.3), dimmed by rain like the vanilla moon.</li>
 * <li>Hujan Meteor Roh: the streaks of {@link LangitSky}, three strip segments and a glowing head each (streaks ≤ 600 /
 * 1 500 / 3 000 quads by the 7b cap).</li>
 * <li>Pilar Pelangi: a white core, a soft glow and seven helical colour ribbons that slowly turn, a crown halo and a base
 * glow (120 / 320 / 640 quads by tier); beyond 0.85 × the fog start a sky-projected copy so it is seen from far.</li>
 * <li>Kabut Qi: ground-hugging mist planes around the player (8 / 24 / 48), faded near and far, dimmed by night.</li>
 * <li>the impact rings, flashes, glow columns and crater glows of meteors and claims; the red glow of eclipse-touched
 * monsters; the Meteor Agung crossing the sky beyond its tracking range and its minute-long crater.</li>
 * <li>detail pass: Tiangou's bite sliding over the moon before and after the totality, the frost ring of the real moon
 * under Bulan Kembar, the shower's radiant and the bursting fireballs, the pillar's waning flicker and its ripple at the
 * top of the sky, the birth's shock ring, the Fajar Ungu band and streamers in the east, sunbeams through dense mist, the
 * seal sigils under treasure guardians, and the mist coloured by the local biome.</li>
 * </ul>
 */
final class LangitRenderer {

	private static final LangitSink STREAK = new LangitSink(LangitSink.STREAK);
	private static final LangitSink HEAD = new LangitSink(LangitSink.HEAD);
	private static final LangitSink MOON_BLOOD = new LangitSink(LangitSink.MOON_BLOOD);
	private static final LangitSink MOON_TWIN = new LangitSink(LangitSink.MOON_TWIN);
	private static final LangitSink HALO = new LangitSink(LangitSink.HALO);
	private static final LangitSink PILLAR = new LangitSink(LangitSink.PILLAR);
	private static final LangitSink CROWN = new LangitSink(LangitSink.CROWN);
	private static final LangitSink MIST = new LangitSink(LangitSink.MIST);
	private static final LangitSink GLOW = new LangitSink(LangitSink.GLOW);
	private static final LangitSink RING = new LangitSink(LangitSink.RING);
	private static final LangitSink MOON_SHADOW = new LangitSink(LangitSink.MOON_SHADOW);
	private static final LangitSink CRATER = new LangitSink(LangitSink.CRATER);
	private static final LangitSink RUNE = new LangitSink(LangitSink.RUNE);
	private static final LangitSink SHAFT = new LangitSink(LangitSink.SHAFT);
	private static final LangitSink WISP = new LangitSink(LangitSink.WISP);
	private static final LangitSink[] ORDER = {CRATER, HALO, MOON_SHADOW, MOON_BLOOD, MOON_TWIN, WISP, STREAK, HEAD, PILLAR, CROWN, RING, RUNE,
			SHAFT, MIST, GLOW};

	/** Pillar segment length (blocks) and ribbon count by tier (Minimal / Normal / Maximal). */
	private static final int[] PILLAR_SEGMENT = {32, 16, 8};
	private static final int[] PILLAR_RIBBONS = {7, 7, 14};
	private static final int[] PILLAR_FAR_POINTS = {6, 10, 16};
	private static final int[] MIST_PLANES = {8, 24, 48};
	private static final float MIST_RANGE = 30.0F;
	/** Sunbeams through dense mist and the Fajar Ungu streamers, by tier. */
	private static final int[] SHAFTS = {3, 6, 10};
	private static final int[] WISPS = {3, 6, 10};
	/** A Meteor Agung farther than this is drawn by {@link #effects} (beyond its entity's tracking range). */
	private static final float GREAT_FAR = 176.0F;
	/** The last 90 s of a rainbow pillar flicker as it weakens. */
	private static final float PILLAR_WANING = 1800.0F;

	// ---- the frame
	private static double camX;
	private static double camY;
	private static double camZ;
	private static float partial;
	private static float trueReach;
	private static float radius;
	private static final Vector3f RIGHT = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static final Vector3f FORWARD = new Vector3f();
	private static final float[] A = new float[3];
	private static final float[] B = new float[3];
	private static boolean warned;

	// ---- the mist planes (world positions, recycled)
	private static final int MIST_MAX = 48;
	private static final double[] MIST_X = new double[MIST_MAX];
	private static final double[] MIST_Y = new double[MIST_MAX];
	private static final double[] MIST_Z = new double[MIST_MAX];
	private static final float[] MIST_SIZE = new float[MIST_MAX];
	private static final float[] MIST_PHASE = new float[MIST_MAX];
	private static final boolean[] MIST_LIVE = new boolean[MIST_MAX];

	// ---- the sunbeams (world positions, recycled)
	private static final int SHAFT_MAX = 10;
	private static final double[] SHAFT_X = new double[SHAFT_MAX];
	private static final double[] SHAFT_Y = new double[SHAFT_MAX];
	private static final double[] SHAFT_Z = new double[SHAFT_MAX];
	private static final float[] SHAFT_W = new float[SHAFT_MAX];
	private static final float[] SHAFT_PHASE = new float[SHAFT_MAX];
	private static final boolean[] SHAFT_LIVE = new boolean[SHAFT_MAX];

	/** The {@code COLLECT_SUBMITS} listener. */
	static void collect(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		boolean sky = LangitState.shown() != null;
		boolean anything = sky || !LangitState.BIRTHS.isEmpty() || LangitState.mist > 0.01F || !LangitState.FX.isEmpty()
				|| LangitState.marks.length > 0 || LangitState.guards.length > 0 || LangitState.dawn > 0.01F;
		if (!anything || ShaderCompat.shadowPass()) {
			return;
		}
		if (dev.kultivator.client.epic.kosmik.Kosmik3D.covering()) {
			return;
		}
		long start = System.nanoTime();
		try {
			ensureBudgetFrame();
			CameraRenderState camera = context.levelState().cameraRenderState;
			setup(camera, context.levelState().worldPartialTicks);
			for (LangitSink sink : ORDER) {
				sink.reset();
			}
			float gameTime = (float) (level.getGameTime() % 240000L) + partial;
			boolean overworldSky = level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
			if (sky && overworldSky) {
				moons(minecraft, level);
				streaks(level);
			}
			if (overworldSky && LangitState.dawn > 0.02F) {
				dawnBand(level, gameTime);
			}
			pillars(level, gameTime);
			mist(level, gameTime);
			shafts(minecraft, level, gameTime);
			effects(gameTime);
			marks(level, gameTime);
			guards(level, gameTime);
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (LangitSink sink : ORDER) {
				sink.submit(collector, poseStack);
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Langit visuals skipped a frame", exception);
			}
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	/**
	 * The 7b renderer resets the per-frame budget only while it draws something; when it is idle this frame, L starts the
	 * frame itself (the same condition {@code EpicRenderer.collect} uses), so the budget never runs dry.
	 */
	private static void ensureBudgetFrame() {
		boolean epic = EpicDirector.anyGeometry() || dev.kultivator.client.epic.cosmic.CosmicSky.active()
				|| dev.kultivator.client.epic.lens.HeatHaze.active() || dev.kultivator.client.epic.kosmik.Kosmik3D.wants();
		if (!epic) {
			EpicBudget.beginFrame();
		}
	}

	private static void setup(CameraRenderState camera, float partialTicks) {
		camX = camera.pos.x;
		camY = camera.pos.y;
		camZ = camera.pos.z;
		partial = partialTicks;
		float fog = camera.fogData.renderDistanceStart;
		if (!(fog > 8.0F)) {
			fog = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F * 0.9F;
		}
		trueReach = 0.85F * fog;
		radius = SkyProjection.drawRadius(fog);
		camera.orientation.transform(1.0F, 0.0F, 0.0F, RIGHT);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, UP);
		camera.orientation.transform(0.0F, 0.0F, -1.0F, FORWARD);
	}

	// ================================================================== moons

	private static void moons(Minecraft minecraft, ClientLevel level) {
		SkyKind kind = LangitState.shown();
		if (kind != SkyKind.BLOOD_ECLIPSE && kind != SkyKind.TWIN_MOONS) {
			return;
		}
		float w = LangitState.skyWeight(partial) * (1.0F - level.getRainLevel(partial));
		if (w <= 0.01F) {
			return;
		}
		float angle = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partial) * Mth.DEG_TO_RAD;
		// the vanilla sky: up rotated by the moon angle about X, then the whole sky by -90° about Y
		float dx = -Mth.sin(angle);
		float dy = Mth.cos(angle);
		float dz = 0.0F;
		float time = (float) (level.getGameTime() % 24000L) + partial;
		if (kind == SkyKind.BLOOD_ECLIPSE) {
			float horizon = Mth.clamp((dy + 0.02F) / 0.12F, 0.0F, 1.0F);
			float a = w * horizon;
			if (a > 0.01F && EpicBudget.takeQuads(5)) {
				float pulse = 0.85F + 0.15F * Mth.sin(time * 0.04F);
				// Tiangou swallows the moon: the halos and the red grow with the bite, the totality holds, then it is given back
				float cover = LangitState.eclipseCover(level, partial);
				skyQuad(HALO, dx, dy, dz, 0.46F, argb(0.16F * a * pulse * cover, 0xC8201A));
				skyQuad(HALO, dx, dy, dz, 0.31F, argb(0.26F * a * pulse * cover, 0xE0301E));
				skyQuad(HALO, dx, dy, dz, 0.215F, argb((0.1F + 0.35F * cover) * a, 0xFF4A2A));
				if (cover < 0.999F) {
					// the bite comes in from the east limb and leaves by the west one
					long span = Math.max(1L, LangitState.skyEnd - LangitState.skyStart);
					boolean leaving = (level.getOverworldClockTime() - LangitState.skyStart) * 2L > span;
					float shift = (1.0F - cover) * 0.34F * (leaving ? -1.0F : 1.0F);
					skyQuad(MOON_SHADOW, dx, dy, dz, 0.17F, argb(0.95F * a, 0xFFFFFF), shift);
				}
				skyQuad(MOON_BLOOD, dx, dy, dz, 0.16F, argb(a * cover, 0xFFFFFF));
			}
		} else {
			// the twin on the mirrored arc, tilted south so the two never sit on each other at midnight
			float tx = Mth.sin(angle) * 0.9F;
			float ty = Mth.cos(angle);
			float tz = 0.42F;
			float length = Mth.sqrt(tx * tx + ty * ty + tz * tz);
			tx /= length;
			ty /= length;
			tz /= length;
			float horizon = Mth.clamp((ty + 0.02F) / 0.12F, 0.0F, 1.0F);
			float a = w * horizon;
			if (a > 0.01F && EpicBudget.takeQuads(5)) {
				float shimmer = 0.9F + 0.1F * Mth.sin(time * 0.03F + 1.3F);
				skyQuad(HALO, tx, ty, tz, 0.27F, argb(0.12F * a * shimmer, 0xB8D0FF));
				skyQuad(HALO, tx, ty, tz, 0.17F, argb(0.24F * a, 0xE0ECFF));
				skyQuad(MOON_TWIN, tx, ty, tz, 0.11F, argb(0.95F * a, 0xFFFFFF));
				// the real moon wears a wide frost ring the same night (both moons light the clouds)
				float realHorizon = Mth.clamp((dy + 0.02F) / 0.12F, 0.0F, 1.0F);
				skyQuad(HALO, dx, dy, dz, 0.42F, argb(0.09F * w * realHorizon * shimmer, 0xC8D8FF));
				skyQuad(HALO, dx, dy, dz, 0.24F, argb(0.12F * w * realHorizon, 0xE8F0FF));
			}
		}
	}

	/**
	 * A quad on the sky sphere centred on unit direction (x, y, z), in the plane across it with a sky-fixed basis (the
	 * north-south axis and its cross product), half size {@code half × R}.
	 */
	private static void skyQuad(LangitSink sink, float x, float y, float z, float half, int color) {
		skyQuad(sink, x, y, z, half, color, 0.0F);
	}

	/** {@link #skyQuad(LangitSink, float, float, float, float, int)} moved {@code shift × R} across the north-south axis. */
	private static void skyQuad(LangitSink sink, float x, float y, float z, float half, int color, float shift) {
		// e1: the axis the vanilla moon turns around (north-south), made perpendicular to the direction
		float e1x = 0.0F;
		float e1y = 0.0F;
		float e1z = 1.0F;
		float dot = x * e1x + y * e1y + z * e1z;
		e1x -= dot * x;
		e1y -= dot * y;
		e1z -= dot * z;
		float l1 = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
		if (l1 < 1.0E-4F) {
			e1x = 1.0F;
			e1y = 0.0F;
			e1z = 0.0F;
			l1 = 1.0F;
		}
		e1x /= l1;
		e1y /= l1;
		e1z /= l1;
		float e2x = y * e1z - z * e1y;
		float e2y = z * e1x - x * e1z;
		float e2z = x * e1y - y * e1x;
		float r = radius * 0.98F;
		float cx = x * r + e2x * shift * r;
		float cy = y * r + e2y * shift * r;
		float cz = z * r + e2z * shift * r;
		float h = half * r;
		sink.vertex(cx - e1x * h - e2x * h, cy - e1y * h - e2y * h, cz - e1z * h - e2z * h, 0.0F, 0.0F, color, -x, -y, -z);
		sink.vertex(cx + e1x * h - e2x * h, cy + e1y * h - e2y * h, cz + e1z * h - e2z * h, 1.0F, 0.0F, color, -x, -y, -z);
		sink.vertex(cx + e1x * h + e2x * h, cy + e1y * h + e2y * h, cz + e1z * h + e2z * h, 1.0F, 1.0F, color, -x, -y, -z);
		sink.vertex(cx - e1x * h + e2x * h, cy - e1y * h + e2y * h, cz - e1z * h + e2z * h, 0.0F, 1.0F, color, -x, -y, -z);
	}

	// ================================================================== meteor streaks

	private static void streaks(ClientLevel level) {
		if (LangitSky.STREAKS.isEmpty()) {
			return;
		}
		// rain hides the shower like it hides the stars
		float weight = LangitState.skyWeight(partial) * (1.0F - level.getRainLevel(partial));
		float r = radius * 0.96F;
		// the radiant: the faint star the shower pours from
		LangitSky.radiant(A);
		if (weight > 0.1F && A[1] > 0.05F && EpicBudget.takeQuads(2)) {
			float twinkle = 0.75F + 0.25F * Mth.sin((float) (level.getGameTime() % 2400L) * 0.21F);
			billboard(HEAD, A[0] * r, A[1] * r, A[2] * r, r * 0.022F, argb(0.3F * weight * twinkle, 0xE0F0FF), 0.0F);
			billboard(GLOW, A[0] * r, A[1] * r, A[2] * r, r * 0.06F, argb(0.08F * weight, 0x9FC8FF), 0.0F);
		}
		for (LangitSky.Streak streak : LangitSky.STREAKS) {
			int cost = streak.big ? 10 : 4;
			if (!EpicBudget.takeQuads(cost)) {
				return;
			}
			float age = streak.age + partial;
			float head = streak.speed * age;
			float tail = Math.max(0.0F, head - streak.length);
			float fade = streak.fade(partial) * weight;
			streak.at(head, A);
			float elevation = Mth.clamp((A[1] - 0.06F) / 0.15F, 0.0F, 1.0F);
			fade *= elevation;
			if (fade <= 0.01F) {
				continue;
			}
			int body = argb(fade, streak.color);
			int dim = argb(fade * 0.25F, streak.color);
			float width = streak.width * r;
			// three segments from the tail (u 0) to the head (u 1), widening towards the head
			float prevX = 0.0F;
			float prevY = 0.0F;
			float prevZ = 0.0F;
			for (int i = 0; i <= 3; i++) {
				float f = i / 3.0F;
				streak.at(tail + (head - tail) * f, B);
				float x = B[0] * r;
				float y = B[1] * r;
				float z = B[2] * r;
				if (i > 0) {
					float f0 = (i - 1) / 3.0F;
					segment(STREAK, prevX, prevY, prevZ, x, y, z, width * (0.3F + 0.7F * f0), width * (0.3F + 0.7F * f),
							lerpArgb(dim, body, f0), lerpArgb(dim, body, f), f0, f);
				}
				prevX = x;
				prevY = y;
				prevZ = z;
			}
			billboard(HEAD, A[0] * r, A[1] * r, A[2] * r, width * (streak.big ? 5.0F : 3.0F), argb(Math.min(1.0F, fade * 1.2F), 0xFFFFFF), 0.0F);
			if (streak.big) {
				// the fireball: a wide soft wake, a second green-gold head glow and a lingering train
				streak.at(tail, B);
				segment(STREAK, B[0] * r, B[1] * r, B[2] * r, A[0] * r, A[1] * r, A[2] * r, width * 1.2F, width * 3.2F,
						argb(fade * 0.1F, 0x9FFFC8), argb(fade * 0.45F, 0xE0FFE8), 0.0F, 1.0F);
				billboard(HEAD, A[0] * r, A[1] * r, A[2] * r, width * 9.0F, argb(fade * 0.35F, 0xA8FFC0), 0.7F);
				float trainTail = Math.max(0.0F, tail - streak.length * 0.8F);
				streak.at(trainTail, B);
				float bx = B[0] * r;
				float by = B[1] * r;
				float bz = B[2] * r;
				streak.at(tail, A);
				segment(STREAK, bx, by, bz, A[0] * r, A[1] * r, A[2] * r, width * 0.6F, width * 1.4F,
						argb(0.0F, 0x88C8B0), argb(fade * 0.18F, 0xB0E8D0), 0.0F, 0.4F);
				billboard(HEAD, bx, by, bz, width * 2.0F, argb(fade * 0.1F, 0xB0E8D0), 0.0F);
				// the terminal burst: the fireball flares and breaks apart over the last fifth of its path
				float lifeT = (streak.age + partial) / streak.life;
				if (lifeT > 0.8F) {
					float f = (lifeT - 0.8F) / 0.2F;
					float flare = Mth.sin(f * Mth.PI) * weight * elevation;
					streak.at(head, A);
					billboard(HEAD, A[0] * r, A[1] * r, A[2] * r, width * (8.0F + 22.0F * f), argb(0.75F * flare, 0xF0FFF4), f * 1.2F);
					billboard(GLOW, A[0] * r, A[1] * r, A[2] * r, width * (14.0F + 30.0F * f), argb(0.3F * flare, 0xA8FFC8), 0.0F);
				}
			}
		}
	}

	// ================================================================== the rainbow pillar

	private static void pillars(ClientLevel level, float gameTime) {
		if (LangitState.BIRTHS.isEmpty()) {
			return;
		}
		int tier = EpicBudget.tierOrMinimal();
		for (LangitState.BirthView view : LangitState.BIRTHS.values()) {
			float a = view.alpha;
			float burst = 0.0F;
			if (!view.live()) {
				float e = Math.max(0.0F, (view.ending - partial) / 40.0F);
				burst = 1.0F - e;
				a *= e;
			}
			float left = view.ends - level.getGameTime() - partial;
			if (view.live() && left < PILLAR_WANING) {
				// a weakening birth: the light falters more and more as the end comes
				float waning = 1.0F - Mth.clamp(left / PILLAR_WANING, 0.0F, 1.0F);
				a *= 1.0F - 0.45F * waning * (0.5F + 0.5F * Mth.sin(gameTime * 0.9F + view.seed));
			}
			if (a <= 0.01F) {
				continue;
			}
			double bx = view.x + 0.5D;
			double by = view.y;
			double bz = view.z + 0.5D;
			double dx = bx - camX;
			double dz = bz - camZ;
			float distance = (float) Math.sqrt(dx * dx + dz * dz);
			float height = Math.max(32.0F, Math.min(320.0F, level.getMaxY() - (float) by));
			float near = trueReach;
			if (distance <= near * 0.95F) {
				float blend = distance > near * 0.8F ? (0.95F - distance / near) / 0.15F : 1.0F;
				pillarNear(view, bx, by, bz, height, a * blend, burst, gameTime, tier);
			}
			if (distance >= near * 0.8F) {
				float blend = Mth.clamp((distance / near - 0.8F) / 0.15F, 0.0F, 1.0F);
				pillarFar(view, bx, by, bz, height, a * blend, gameTime, tier);
			}
		}
	}

	private static void pillarNear(LangitState.BirthView view, double bx, double by, double bz, float height, float a, float burst,
								   float gameTime, int tier) {
		int segment = PILLAR_SEGMENT[tier];
		int segments = Math.max(2, Math.round(height / segment));
		int ribbons = PILLAR_RIBBONS[tier];
		boolean full = EpicBudget.takePillar() && EpicBudget.takeQuads(segments * (2 + ribbons) + 8);
		if (!full && !EpicBudget.takeQuads(segments * 2 + 2)) {
			return;
		}
		float seconds = gameTime / 20.0F;
		int tierRgb = TreasureRewards.tierColor(view.tier);
		float x = (float) (bx - camX);
		float y0 = (float) (by - camY);
		float z = (float) (bz - camZ);
		float coreWidth = (1.1F + 0.18F * Mth.sin(seconds * 2.3F)) * (1.0F + burst * 2.5F);
		float glowWidth = 5.6F * (1.0F + burst);
		for (int i = 0; i < segments; i++) {
			float h0 = height * i / segments;
			float h1 = height * (i + 1) / segments;
			float fade0 = (float) Math.sqrt(1.0F - h0 / height);
			float fade1 = (float) Math.sqrt(Math.max(0.0F, 1.0F - h1 / height));
			float v0 = h0 / 16.0F - seconds * 0.8F;
			float v1 = h1 / 16.0F - seconds * 0.8F;
			strip(PILLAR, x, y0 + h0, z, x, y0 + h1, z, glowWidth, glowWidth * 1.1F, argb(0.2F * a * fade0, tierRgb), argb(0.2F * a * fade1, tierRgb),
					v0 * 0.5F, v1 * 0.5F);
			strip(PILLAR, x, y0 + h0, z, x, y0 + h1, z, coreWidth, coreWidth * 0.9F, argb(0.92F * a * fade0, 0xFFFFFF),
					argb(0.92F * a * fade1, 0xFFFFFF), v0, v1);
		}
		if (!full) {
			return;
		}
		// seven helical colour ribbons turning slowly (Maximal: a second, counter-turning inner set)
		for (int k = 0; k < ribbons; k++) {
			boolean inner = k >= 7;
			int rgb = LangitParticles.RAINBOW[k % 7];
			float base = Mth.TWO_PI * (k % 7) / 7.0F + (inner ? 0.45F : 0.0F);
			float spin = (inner ? -0.9F : 0.6F) * seconds;
			float ring = inner ? 0.7F : 1.3F + 0.12F * Mth.sin(seconds * 1.1F + k);
			float width = inner ? 0.45F : 0.75F;
			for (int i = 0; i < segments; i++) {
				float h0 = height * i / segments;
				float h1 = height * (i + 1) / segments;
				float p0 = base + spin + h0 * 0.03F * (inner ? -1.0F : 1.0F);
				float p1 = base + spin + h1 * 0.03F * (inner ? -1.0F : 1.0F);
				float r0 = ring * (1.0F + burst * 3.0F) * (1.0F - 0.35F * h0 / height);
				float r1 = ring * (1.0F + burst * 3.0F) * (1.0F - 0.35F * h1 / height);
				float c0 = Mth.cos(p0);
				float s0 = Mth.sin(p0);
				float c1 = Mth.cos(p1);
				float s1 = Mth.sin(p1);
				float alpha0 = 0.62F * a * (1.0F - 0.7F * h0 / height);
				float alpha1 = 0.62F * a * (1.0F - 0.7F * h1 / height);
				float hw = width * 0.5F;
				PILLAR.quad(x + c0 * r0 - s0 * hw, y0 + h0, z + s0 * r0 + c0 * hw,
						x + c0 * r0 + s0 * hw, y0 + h0, z + s0 * r0 - c0 * hw,
						x + c1 * r1 + s1 * hw, y0 + h1, z + s1 * r1 - c1 * hw,
						x + c1 * r1 - s1 * hw, y0 + h1, z + s1 * r1 + c1 * hw,
						0.0F, h0 / 16.0F - seconds, 1.0F, h1 / 16.0F - seconds, argb(alpha0, rgb), argb(alpha1, rgb));
			}
		}
		// the crown halo, turning, and the base glow
		float crownY = y0 + 18.0F + 1.5F * Mth.sin(seconds * 0.7F);
		flat(CROWN, x, crownY, z, 5.5F * (1.0F + burst * 2.0F), seconds * 0.4F, argb(0.72F * a, tierRgb));
		flat(CROWN, x, crownY + 0.4F, z, 3.6F, -seconds * 0.7F, argb(0.5F * a, 0xFFFFFF));
		billboard(CROWN, x, crownY, z, 3.0F, argb(0.32F * a, tierRgb), seconds * 0.3F);
		flat(GLOW, x, y0 + 0.06F, z, 3.4F, 0.0F, argb(0.6F * a, tierRgb));
		// where the light meets the top of the sky: a wide slow ripple, seen from below like a halo in the clouds
		float top = y0 + height;
		flat(CROWN, x, top, z, 16.0F + 3.0F * Mth.sin(seconds * 0.35F), seconds * 0.08F, argb(0.22F * a, tierRgb));
		flat(RING, x, top - 2.0F, z, 26.0F + 4.0F * Mth.sin(seconds * 0.27F + 1.0F), 0.0F, argb(0.14F * a, 0xFFFFFF));
		flat(CROWN, x, y0 + 0.08F, z, 2.6F, -seconds * 0.5F, argb(0.55F * a, 0xFFFFFF));
		billboard(GLOW, x, y0 + 1.2F, z, 2.2F + burst * 6.0F, argb((0.35F + burst * 0.6F) * a, 0xFFFFFF), 0.0F);
	}

	/** Beyond the true reach: the pillar projected onto the sky sphere, a thin white core in a rainbow glow. */
	private static void pillarFar(LangitState.BirthView view, double bx, double by, double bz, float height, float a, float gameTime, int tier) {
		int points = PILLAR_FAR_POINTS[tier];
		if (!EpicBudget.takeQuads(points * 2 + 1)) {
			return;
		}
		float seconds = gameTime / 20.0F;
		float r = radius * 0.94F;
		float core = r * 0.006F;
		float glow = r * 0.026F;
		float prevX = 0.0F;
		float prevY = 0.0F;
		float prevZ = 0.0F;
		float prevAlpha = 0.0F;
		int prevRgb = 0;
		for (int i = 0; i <= points; i++) {
			float h = height * i / points;
			float px = (float) (bx - camX);
			float py = (float) (by + h - camY);
			float pz = (float) (bz - camZ);
			float length = Mth.sqrt(px * px + py * py + pz * pz);
			if (length < 1.0E-3F) {
				return;
			}
			float k = r / length;
			float x = px * k;
			float y = py * k;
			float z = pz * k;
			// the far beam shows down to the horizon (it is light, not ground), fading below it
			float elevation = (float) Math.toDegrees(Math.asin(Mth.clamp(py / length, -1.0F, 1.0F)));
			float alpha = a * Mth.clamp((elevation + 1.0F) / 4.0F, 0.0F, 1.0F) * (1.0F - 0.6F * i / points);
			int rgb = LangitParticles.RAINBOW[Math.floorMod(i + (int) (seconds * 2.0F), 7)];
			if (i > 0 && (alpha > 0.01F || prevAlpha > 0.01F)) {
				float v0 = (i - 1) * 0.5F - seconds * 0.4F;
				float v1 = i * 0.5F - seconds * 0.4F;
				strip(PILLAR, prevX, prevY, prevZ, x, y, z, glow, glow, argb(0.35F * prevAlpha, prevRgb), argb(0.35F * alpha, rgb), v0, v1);
				strip(PILLAR, prevX, prevY, prevZ, x, y, z, core, core * 0.8F, argb(0.95F * prevAlpha, 0xFFFFFF), argb(0.95F * alpha, 0xFFFFFF),
						v0, v1);
			}
			if (i == Math.max(1, points / 8)) {
				billboard(CROWN, x, y, z, glow * 2.4F, argb(0.5F * alpha, TreasureRewards.tierColor(view.tier)), seconds * 0.4F);
			}
			prevX = x;
			prevY = y;
			prevZ = z;
			prevAlpha = alpha;
			prevRgb = rgb;
		}
	}

	// ================================================================== the Qi mist

	private static void mist(ClientLevel level, float gameTime) {
		float mist = LangitState.mist(partial);
		int count = MIST_PLANES[EpicBudget.tierOrMinimal()];
		if (mist <= 0.01F) {
			for (int i = 0; i < MIST_MAX; i++) {
				MIST_LIVE[i] = false;
			}
			return;
		}
		if (!EpicBudget.takeQuads(count)) {
			return;
		}
		RandomSource random = level.getRandom();
		float light = 0.3F + 0.7F * LangitState.daylight();
		int rgb = lerpRgb(LangitState.mistRgb, LangitParticles.DAWN_RGB, LangitState.dawn);
		rgb = lerpRgb(0x2A3236, rgb, light);
		float seconds = gameTime / 20.0F;
		for (int i = 0; i < count; i++) {
			if (!MIST_LIVE[i] || dist2(MIST_X[i], MIST_Y[i], MIST_Z[i]) > (MIST_RANGE + 6.0F) * (MIST_RANGE + 6.0F)) {
				// a new plane ahead of the camera more often than behind it
				double angle = Math.atan2(FORWARD.z, FORWARD.x) + (random.nextDouble() - 0.5D) * (random.nextInt(3) == 0 ? Math.PI * 2.0D : Math.PI * 0.9D);
				double reach = 4.0D + random.nextDouble() * (MIST_RANGE - 4.0D);
				double x = camX + Math.cos(angle) * reach;
				double z = camZ + Math.sin(angle) * reach;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
				if (ground <= level.getMinY() || Math.abs(ground - camY) > 24.0D) {
					MIST_LIVE[i] = false;
					continue;
				}
				MIST_X[i] = x;
				MIST_Y[i] = ground + 0.1D + random.nextDouble() * 1.2D;
				MIST_Z[i] = z;
				MIST_SIZE[i] = 4.5F + random.nextFloat() * 4.5F;
				MIST_PHASE[i] = random.nextFloat() * Mth.TWO_PI;
				MIST_LIVE[i] = true;
			}
			double wx = MIST_X[i] + Mth.sin(seconds * 0.05F + MIST_PHASE[i]) * 1.5D;
			double wz = MIST_Z[i] + Mth.cos(seconds * 0.04F + MIST_PHASE[i]) * 1.5D;
			float d = (float) Math.sqrt(dist2(wx, MIST_Y[i], wz));
			float fade = Mth.clamp((d - 2.5F) / 4.0F, 0.0F, 1.0F) * Mth.clamp((MIST_RANGE - d) / 8.0F, 0.0F, 1.0F);
			float breathe = 0.8F + 0.2F * Mth.sin(seconds * 0.3F + MIST_PHASE[i] * 3.0F);
			float alpha = 0.34F * mist * fade * breathe;
			if (alpha <= 0.01F) {
				continue;
			}
			float x = (float) (wx - camX);
			float y = (float) (MIST_Y[i] - camY);
			float z = (float) (wz - camZ);
			float size = MIST_SIZE[i];
			if (i % 3 == 2) {
				flat(MIST, x, y + 0.2F, z, size, MIST_PHASE[i] + seconds * 0.02F, argb(alpha * 0.8F, rgb));
			} else {
				upright(MIST, x, y, z, size, size * 0.5F, argb(alpha, rgb), MIST_PHASE[i]);
			}
		}
	}

	// ================================================================== one-off effects

	private static void effects(float gameTime) {
		for (LangitState.Fx fx : LangitState.FX) {
			if (fx.kind == LangitFxPayload.KIND_GREAT_INCOMING) {
				// seen from far: its own distance rules
				greatIncoming(fx);
				continue;
			}
			float t = fx.t(partial);
			float x = (float) (fx.x - camX);
			float y = (float) (fx.y - camY);
			float z = (float) (fx.z - camZ);
			if (x * x + y * y + z * z > 160.0F * 160.0F) {
				continue;
			}
			if (fx.kind == LangitFxPayload.KIND_IMPACT) {
				if (!EpicBudget.takeQuads(29)) {
					return;
				}
				// the crater glow stays the whole 20 s and fades over the last quarter
				float decal = t < 0.75F ? 1.0F : 1.0F - (t - 0.75F) / 0.25F;
				flat(CRATER, x, y + 0.03F, z, 3.2F, (fx.seed & 1023) * 0.0061F, argb(0.85F * decal, 0xFFFFFF));
				float ageTicks = fx.age + partial;
				float ringT = Mth.clamp(ageTicks / 20.0F, 0.0F, 1.0F);
				if (ringT < 1.0F) {
					ring(RING, x, y + 0.08F, z, 0.5F + 7.5F * ringT, 1.6F * (1.0F - ringT) + 0.3F, 20, argb(0.85F * (1.0F - ringT), 0x9FE8FF));
				}
				if (ageTicks < 10.0F) {
					float flash = 1.0F - ageTicks / 10.0F;
					billboard(GLOW, x, y + 1.0F, z, 2.0F + 5.0F * flash, argb(0.9F * flash, 0xE8FAFF), 0.0F);
				}
				float column = 1.0F - t;
				strip(PILLAR, x, y, z, x, y + 7.0F * column + 1.0F, z, 0.9F * column, 0.1F, argb(0.6F * column, 0x8FE0FF), argb(0.0F, 0x8FE0FF),
						gameTime / 40.0F, gameTime / 40.0F + 0.5F);
				flat(GLOW, x, y + 0.05F, z, 1.8F, 0.0F, argb(0.5F * column, 0x8FE0FF));
			} else if (fx.kind == LangitFxPayload.KIND_CLAIM) {
				if (!EpicBudget.takeQuads(48)) {
					return;
				}
				int rgb = TreasureRewards.tierColor(fx.a);
				float fade = 1.0F - t;
				ring(RING, x, y + 0.1F, z, 0.8F + 10.0F * t, 1.8F * fade + 0.2F, 20, argb(0.9F * fade, rgb));
				ring(RING, x, y + 0.15F, z, 0.5F + 6.0F * Math.min(1.0F, t * 1.6F), 1.0F * fade + 0.2F, 16, argb(0.8F * fade, 0xFFFFFF));
				strip(PILLAR, x, y, z, x, y + 34.0F * (0.4F + t), z, 3.4F * fade, 0.4F * fade, argb(0.85F * fade, 0xFFFFFF), argb(0.0F, rgb), 0.0F, 2.0F);
				billboard(GLOW, x, y + 1.2F, z, 3.0F + 7.0F * fade, argb(0.8F * fade * fade, 0xFFF8E0), 0.0F);
			} else if (fx.kind == LangitFxPayload.KIND_GREAT_IMPACT) {
				if (!EpicBudget.takeQuads(48)) {
					return;
				}
				greatImpact(fx, x, y, z, t, gameTime);
			} else if (fx.kind == LangitState.FX_BIRTH_WAVE) {
				if (!EpicBudget.takeQuads(30)) {
					return;
				}
				int rgb = TreasureRewards.tierColor(fx.a);
				float fade = 1.0F - t;
				ring(RING, x, y + 0.1F, z, 1.0F + 26.0F * t, 2.5F * fade + 0.4F, 28, argb(0.7F * fade, rgb));
				flat(RUNE, x, y + 0.06F, z, 3.0F + 5.0F * t, gameTime * 0.03F, argb(0.8F * fade, rgb));
			}
		}
	}

	/**
	 * A Meteor Agung landing (a minute): the shock ring and the flash, a white column that sinks, eight ejecta rays over the
	 * ground, and the crater glow whose cracks pulse slowly until it fades.
	 */
	private static void greatImpact(LangitState.Fx fx, float x, float y, float z, float t, float gameTime) {
		float ageTicks = fx.age + partial;
		float decal = t < 0.8F ? 1.0F : 1.0F - (t - 0.8F) / 0.2F;
		float pulse = 0.7F + 0.3F * Mth.sin(gameTime * 0.08F + fx.seed);
		flat(CRATER, x, y + 0.03F, z, 7.0F, (fx.seed & 1023) * 0.0061F, argb(0.92F * decal, 0xFFFFFF));
		flat(GLOW, x, y + 0.05F, z, 5.5F, 0.0F, argb(0.28F * pulse * decal, 0x8FE0FF));
		float ringT = Mth.clamp(ageTicks / 30.0F, 0.0F, 1.0F);
		if (ringT < 1.0F) {
			ring(RING, x, y + 0.1F, z, 0.8F + 16.0F * ringT, 3.0F * (1.0F - ringT) + 0.4F, 24, argb(0.9F * (1.0F - ringT), 0xBFF0FF));
		}
		if (ageTicks < 14.0F) {
			float flash = 1.0F - ageTicks / 14.0F;
			billboard(GLOW, x, y + 1.5F, z, 4.0F + 10.0F * flash, argb(0.95F * flash, 0xF0FCFF), 0.0F);
		}
		float column = Mth.clamp(1.0F - ageTicks / 300.0F, 0.0F, 1.0F);
		if (column > 0.0F) {
			strip(PILLAR, x, y, z, x, y + 22.0F * column + 2.0F, z, 2.2F * column, 0.2F, argb(0.7F * column, 0xBFF0FF), argb(0.0F, 0x8FE0FF),
					gameTime / 30.0F, gameTime / 30.0F + 1.0F);
		}
		float rays = Mth.clamp(1.0F - ageTicks / 40.0F, 0.0F, 1.0F);
		if (rays > 0.0F) {
			for (int i = 0; i < 8; i++) {
				float angle = Mth.TWO_PI * i / 8.0F + (fx.seed & 63) * 0.05F;
				float reach = 4.0F + 10.0F * (1.0F - rays);
				segment(STREAK, x, y + 0.15F, z, x + Mth.cos(angle) * reach, y + 0.15F, z + Mth.sin(angle) * reach, 1.4F * rays, 0.2F,
						argb(0.0F, 0x8FE0FF), argb(0.6F * rays, 0xE8FAFF), 0.0F, 1.0F);
			}
		}
	}

	/**
	 * A Meteor Agung beyond its entity's tracking range: its head and tail on the path the server sent, in the world when
	 * within the true reach, else projected onto the sky sphere (DESIGN 23.10.3) so it crosses the sky from afar.
	 */
	private static void greatIncoming(LangitState.Fx fx) {
		float age = fx.age + partial;
		float progress = Mth.clamp(age / Math.max(1, fx.fall), 0.0F, 1.0F);
		if (progress >= 1.0F) {
			return;
		}
		float k = 1.0F - (float) Math.pow(progress, 1.7D);
		double hx = fx.x + fx.ox * k;
		double hy = fx.y + 0.6D + fx.oy * k;
		double hz = fx.z + fx.oz * k;
		float hdx = (float) (hx - camX);
		float hdy = (float) (hy - camY);
		float hdz = (float) (hz - camZ);
		float horizontal = Mth.sqrt(hdx * hdx + hdz * hdz);
		if (horizontal < GREAT_FAR || !EpicBudget.takeQuads(5)) {
			return;
		}
		float length = Mth.sqrt(fx.ox * fx.ox + fx.oy * fx.oy + fx.oz * fx.oz);
		float ux = length < 1.0E-3F ? 0.0F : fx.ox / length;
		float uy = length < 1.0E-3F ? 1.0F : fx.oy / length;
		float uz = length < 1.0E-3F ? 0.0F : fx.oz / length;
		float tail = 36.0F;
		float tdx = hdx + ux * tail;
		float tdy = hdy + uy * tail;
		float tdz = hdz + uz * tail;
		float distance = Mth.sqrt(hdx * hdx + hdy * hdy + hdz * hdz);
		float scale = 1.0F;
		if (distance > trueReach) {
			// onto the sky sphere: both ends towards the camera, the widths with them
			float r = radius * 0.95F;
			scale = r / distance;
			float tl = Math.max(1.0E-3F, Mth.sqrt(tdx * tdx + tdy * tdy + tdz * tdz));
			float tk = r / tl;
			hdx *= scale;
			hdy *= scale;
			hdz *= scale;
			tdx *= tk;
			tdy *= tk;
			tdz *= tk;
		}
		float fade = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
		segment(STREAK, tdx, tdy, tdz, hdx, hdy, hdz, 1.2F * scale, 7.0F * scale, argb(0.0F, 0x7FD8FF), argb(0.9F * fade, 0xE8FAFF), 0.0F, 1.0F);
		segment(STREAK, tdx, tdy, tdz, hdx, hdy, hdz, 3.0F * scale, 14.0F * scale, argb(0.0F, 0x5FB8FF), argb(0.3F * fade, 0x9FE0FF), 0.0F, 1.0F);
		billboard(HEAD, hdx, hdy, hdz, 5.0F * scale, argb(fade, 0xFFFFFF), age * 0.05F);
		billboard(GLOW, hdx, hdy, hdz, 14.0F * scale, argb(0.35F * fade, 0x8FD8FF), 0.0F);
	}

	// ================================================================== Fajar Ungu and the sunbeams

	/**
	 * Fajar Ungu (Liexian Zhuan: the purple Qi comes from the east): a violet glow along the eastern horizon and slow
	 * streamers of Qi rising from it towards the zenith (3 / 6 / 10), sky-projected; dimmed by rain.
	 */
	private static void dawnBand(ClientLevel level, float gameTime) {
		float d = LangitState.dawn * (1.0F - level.getRainLevel(partial));
		if (d <= 0.02F) {
			return;
		}
		int wisps = WISPS[EpicBudget.tierOrMinimal()];
		if (!EpicBudget.takeQuads(wisps + 6)) {
			return;
		}
		float seconds = gameTime / 20.0F;
		float r = radius * 0.95F;
		// the band: six soft wisps along the eastern horizon (Minecraft's east is +x), north-east to south-east
		for (int i = 0; i < 6; i++) {
			float az = -0.75F + 1.5F * i / 5.0F;
			float el = 0.05F + 0.03F * Mth.sin(seconds * 0.2F + i * 1.7F);
			float breathe = 0.8F + 0.2F * Mth.sin(seconds * 0.5F + i);
			skyQuad(WISP, Mth.cos(el) * Mth.cos(az), Mth.sin(el), Mth.cos(el) * Mth.sin(az), 0.2F, argb(0.38F * d * breathe,
					i % 2 == 0 ? 0xC8A0F0 : 0xE0B8F8));
		}
		// the streamers: from the eastern horizon up along great circles, each on its own slow cycle
		for (int k = 0; k < wisps; k++) {
			float hash = ((k * 0x9E3779B9) >>> 8 & 1023) / 1023.0F;
			float phase = (seconds * 0.025F + k * 0.37F + hash) % 1.0F;
			float az = -0.6F + hash * 1.2F;
			float el0 = 0.04F + phase * 0.9F;
			float el1 = el0 + 0.22F;
			float az1 = az * (1.0F - 0.35F * phase);
			float ax = Mth.cos(el0) * Mth.cos(az) * r;
			float ay = Mth.sin(el0) * r;
			float azz = Mth.cos(el0) * Mth.sin(az) * r;
			float bx = Mth.cos(el1) * Mth.cos(az1) * r;
			float by = Mth.sin(el1) * r;
			float bz = Mth.cos(el1) * Mth.sin(az1) * r;
			float alpha = Mth.sin(phase * Mth.PI) * d * 0.42F;
			segment(WISP, ax, ay, azz, bx, by, bz, r * 0.03F, r * 0.05F, argb(alpha, 0xB890F0), argb(alpha * 0.7F, 0xE8C8FF), 0.0F, 1.0F);
		}
	}

	/**
	 * Sunbeams through dense Qi mist by day (3 / 6 / 10): tall soft strips leaning towards the sun from the ground around
	 * the player, faded near and far, breathing slowly. Only while the sun is up and the mist is thick.
	 */
	private static void shafts(Minecraft minecraft, ClientLevel level, float gameTime) {
		float mist = LangitState.mist(partial);
		float day = LangitState.daylight();
		int count = SHAFTS[EpicBudget.tierOrMinimal()];
		if (mist < 0.55F || day < 0.5F || level.getRainLevel(partial) > 0.2F) {
			for (int i = 0; i < SHAFT_MAX; i++) {
				SHAFT_LIVE[i] = false;
			}
			return;
		}
		float angle = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial) * Mth.DEG_TO_RAD;
		float sx = -Mth.sin(angle);
		float sy = Mth.cos(angle);
		if (sy < 0.2F || !EpicBudget.takeQuads(count)) {
			return;
		}
		RandomSource random = level.getRandom();
		float seconds = gameTime / 20.0F;
		int rgb = lerpRgb(0xFFF2D8, LangitState.mistRgb, 0.35F);
		float strength = (mist - 0.55F) / 0.45F * day;
		for (int i = 0; i < count; i++) {
			if (!SHAFT_LIVE[i] || dist2(SHAFT_X[i], SHAFT_Y[i], SHAFT_Z[i]) > 28.0D * 28.0D) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double reach = 6.0D + random.nextDouble() * 18.0D;
				double x = camX + Math.cos(a) * reach;
				double z = camZ + Math.sin(a) * reach;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
				if (ground <= level.getMinY() || Math.abs(ground - camY) > 20.0D) {
					SHAFT_LIVE[i] = false;
					continue;
				}
				SHAFT_X[i] = x;
				SHAFT_Y[i] = ground;
				SHAFT_Z[i] = z;
				SHAFT_W[i] = 2.0F + random.nextFloat() * 2.5F;
				SHAFT_PHASE[i] = random.nextFloat() * Mth.TWO_PI;
				SHAFT_LIVE[i] = true;
			}
			float x = (float) (SHAFT_X[i] - camX);
			float y = (float) (SHAFT_Y[i] - camY);
			float z = (float) (SHAFT_Z[i] - camZ);
			float d = Mth.sqrt(x * x + y * y + z * z);
			float fade = Mth.clamp((d - 3.0F) / 5.0F, 0.0F, 1.0F) * Mth.clamp((28.0F - d) / 8.0F, 0.0F, 1.0F);
			float breathe = 0.6F + 0.4F * Mth.sin(seconds * 0.4F + SHAFT_PHASE[i]);
			float alpha = 0.14F * strength * fade * breathe;
			if (alpha <= 0.01F) {
				continue;
			}
			float length = 34.0F;
			strip(SHAFT, x, y, z, x + sx * length, y + sy * length, z, SHAFT_W[i], SHAFT_W[i] * 1.6F, argb(alpha, rgb), argb(0.0F, rgb),
					seconds * 0.05F + SHAFT_PHASE[i], seconds * 0.05F + SHAFT_PHASE[i] + 2.0F);
		}
	}

	// ================================================================== the treasure guardians

	/** A turning seal sigil and a soft glow under each treasure guardian near the player (3 quads each). */
	private static void guards(ClientLevel level, float gameTime) {
		int[] ids = LangitState.guards;
		if (ids.length == 0) {
			return;
		}
		int rgb = TreasureRewards.tierColor(LangitState.guardsTier);
		float seconds = gameTime / 20.0F;
		for (int id : ids) {
			Entity entity = level.getEntity(id);
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			double ex = Mth.lerp(partial, entity.xo, entity.getX());
			double ey = Mth.lerp(partial, entity.yo, entity.getY());
			double ez = Mth.lerp(partial, entity.zo, entity.getZ());
			if (dist2(ex, ey, ez) > 64.0D * 64.0D || !EpicBudget.takeQuads(3)) {
				continue;
			}
			float x = (float) (ex - camX);
			float y = (float) (ey - camY);
			float z = (float) (ez - camZ);
			float pulse = 0.75F + 0.25F * Mth.sin(seconds * 2.4F + id);
			float size = Math.max(1.1F, entity.getBbWidth() * 1.4F);
			flat(RUNE, x, y + 0.04F, z, size, seconds * 0.8F + id, argb(0.8F * pulse, rgb));
			flat(GLOW, x, y + 0.05F, z, size * 1.3F, 0.0F, argb(0.3F * pulse, rgb));
			billboard(GLOW, x, y + entity.getBbHeight() * 0.5F, z, entity.getBbHeight() * 0.6F, argb(0.1F * pulse, rgb), 0.0F);
		}
	}

	private static void marks(ClientLevel level, float gameTime) {
		int[] ids = LangitState.marks;
		if (ids.length == 0) {
			return;
		}
		float seconds = gameTime / 20.0F;
		for (int id : ids) {
			Entity entity = level.getEntity(id);
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			double ex = Mth.lerp(partial, entity.xo, entity.getX());
			double ey = Mth.lerp(partial, entity.yo, entity.getY());
			double ez = Mth.lerp(partial, entity.zo, entity.getZ());
			if (dist2(ex, ey, ez) > 48.0D * 48.0D || !EpicBudget.takeQuads(2)) {
				continue;
			}
			float x = (float) (ex - camX);
			float y = (float) (ey - camY);
			float z = (float) (ez - camZ);
			float pulse = 0.75F + 0.25F * Mth.sin(seconds * 3.0F + id);
			float width = Math.max(0.8F, entity.getBbWidth() * 1.2F);
			billboard(GLOW, x, y + entity.getBbHeight() * 0.55F, z, width, argb(0.3F * pulse, 0xFF2A1A), 0.0F);
			flat(GLOW, x, y + 0.05F, z, width * 1.4F, 0.0F, argb(0.45F * pulse, 0xC01810));
		}
	}

	// ================================================================== primitives (camera-relative)

	/** A camera-facing strip between two points; u along (u0 → u1), v across. */
	static void segment(LangitSink sink, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int ca, int cb,
						float u0, float u1) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float sx = dy * az - dz * ay;
		float sy = dz * ax - dx * az;
		float sz = dx * ay - dy * ax;
		float length = Mth.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0E-5F) {
			sx = RIGHT.x;
			sy = RIGHT.y;
			sz = RIGHT.z;
		} else {
			sx /= length;
			sy /= length;
			sz /= length;
		}
		float ha = wa * 0.5F;
		float hb = wb * 0.5F;
		sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, u0, 0.0F, ca, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, u1, 0.0F, cb, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, u1, 1.0F, cb, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, u0, 1.0F, ca, FORWARD.x, FORWARD.y, FORWARD.z);
	}

	/** A camera-facing strip whose texture runs u across (0 → 1) and v along ({@code v0} → {@code v1}): the pillar bands. */
	static void strip(LangitSink sink, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int ca, int cb,
					  float v0, float v1) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float sx = dy * az - dz * ay;
		float sy = dz * ax - dx * az;
		float sz = dx * ay - dy * ax;
		float length = Mth.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0E-5F) {
			sx = RIGHT.x;
			sy = RIGHT.y;
			sz = RIGHT.z;
		} else {
			sx /= length;
			sy /= length;
			sz /= length;
		}
		float ha = wa * 0.5F;
		float hb = wb * 0.5F;
		sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, 0.0F, v0, ca, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, 1.0F, v0, ca, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, 1.0F, v1, cb, FORWARD.x, FORWARD.y, FORWARD.z);
		sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, 0.0F, v1, cb, FORWARD.x, FORWARD.y, FORWARD.z);
	}

	/** A camera-facing square, turned by {@code angle} radians in the view plane. */
	static void billboard(LangitSink sink, float x, float y, float z, float half, int color, float angle) {
		float c = Mth.cos(angle) * half;
		float s = Mth.sin(angle) * half;
		// the corners: (±1, ±1) rotated, in the camera's right/up plane
		float ax = RIGHT.x * c + UP.x * s;
		float ay = RIGHT.y * c + UP.y * s;
		float az = RIGHT.z * c + UP.z * s;
		float bx = -RIGHT.x * s + UP.x * c;
		float by = -RIGHT.y * s + UP.y * c;
		float bz = -RIGHT.z * s + UP.z * c;
		sink.vertex(x - ax - bx, y - ay - by, z - az - bz, 0.0F, 1.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + ax - bx, y + ay - by, z + az - bz, 1.0F, 1.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + ax + bx, y + ay + by, z + az + bz, 1.0F, 0.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x - ax + bx, y - ay + by, z - az + bz, 0.0F, 0.0F, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
	}

	/** A horizontal square turned by {@code angle} radians, normal up. */
	static void flat(LangitSink sink, float x, float y, float z, float half, float angle, int color) {
		float c = Mth.cos(angle) * half;
		float s = Mth.sin(angle) * half;
		sink.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
	}

	/** A vertical quad standing on (x, y, z), turned about Y to face the camera: ground mist. */
	static void upright(LangitSink sink, float x, float y, float z, float halfWidth, float height, int color, float phase) {
		float hx = x;
		float hz = z;
		float length = Mth.sqrt(hx * hx + hz * hz);
		float sx;
		float sz;
		if (length < 1.0E-3F) {
			sx = RIGHT.x;
			sz = RIGHT.z;
		} else {
			sx = -hz / length;
			sz = hx / length;
		}
		// mirrored on every other plane so the puffs do not repeat
		float u0 = ((int) (phase * 10.0F) & 1) == 0 ? 0.0F : 1.0F;
		float u1 = 1.0F - u0;
		sink.vertex(x - sx * halfWidth, y, z - sz * halfWidth, u0, 1.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x + sx * halfWidth, y, z + sz * halfWidth, u1, 1.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x + sx * halfWidth, y + height, z + sz * halfWidth, u1, 0.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x - sx * halfWidth, y + height, z - sz * halfWidth, u0, 0.0F, color, 0.0F, 0.0F, 1.0F);
	}

	/** A flat ring (annulus) of {@code segments} quads; the ring texture runs u around and v across. */
	static void ring(LangitSink sink, float x, float y, float z, float r, float width, int segments, int color) {
		float r0 = Math.max(0.0F, r - width * 0.5F);
		float r1 = r + width * 0.5F;
		for (int i = 0; i < segments; i++) {
			float a0 = Mth.TWO_PI * i / segments;
			float a1 = Mth.TWO_PI * (i + 1) / segments;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			sink.vertex(x + c0 * r0, y, z + s0 * r0, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c0 * r1, y, z + s0 * r1, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c1 * r1, y, z + s1 * r1, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c1 * r0, y, z + s1 * r0, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}
	}

	// ================================================================== helpers

	private static double dist2(double x, double y, double z) {
		double dx = x - camX;
		double dy = y - camY;
		double dz = z - camZ;
		return dx * dx + dy * dy + dz * dz;
	}

	/** An ARGB colour from an alpha 0..1 and an RGB. */
	static int argb(float alpha, int rgb) {
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
		return a << 24 | (rgb & 0xFFFFFF);
	}

	static int lerpRgb(int a, int b, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
		int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
		int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
		return r << 16 | g << 8 | bl;
	}

	/** ARGB interpolation (alpha too). */
	private static int lerpArgb(int a, int b, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		int alpha = Math.round(((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * k);
		return alpha << 24 | lerpRgb(a, b, k);
	}

	/** Whether the full look is on (not Minimal) for callers that pick details. */
	static boolean rich() {
		return !EffectQuality.minimal();
	}

	private LangitRenderer() {
	}
}

package dev.kultivator.client.epic.cosmic;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The geometry of Langit Kosmik (DESIGN 30.2.8): seeded sky directions turned into camera-relative quads at the
 * sky-projection radius. Two sinks per texture: depth tested ({@code entityTranslucentEmissive}) and see-through
 * ({@code RenderTypes.textSeeThrough}: the {@code TEXT_SEE_THROUGH} pipeline, translucent blend, no depth state) for the
 * open-sky shots; plus the vanilla {@code endPortal()} starfield as the far backing. Directions are built once per show
 * (no per-frame allocation). Every texture is ours except {@code cosmic_star} and {@code flare_core} (Kenney CC0).
 */
final class CosmicLayers {

	private static final int RIVER = 0;
	private static final int NEBULA_0 = 1;
	private static final int PALACE = 4;
	private static final int SUN = 5;
	private static final int MOON = 6;
	private static final int STAR = 7;
	private static final int FLARE = 8;
	private static final Identifier[] TEXTURES = {
			Kultivator.id("textures/misc/film/star_river.png"), Kultivator.id("textures/misc/film/nebula_0.png"),
			Kultivator.id("textures/misc/film/nebula_1.png"), Kultivator.id("textures/misc/film/nebula_2.png"),
			Kultivator.id("textures/misc/film/palace_strip.png"), Kultivator.id("textures/misc/film/sun_corona.png"),
			Kultivator.id("textures/misc/film/moon_disc.png"), Kultivator.id("textures/misc/film/cosmic_star.png"),
			Kultivator.id("textures/misc/film/flare_core.png")};
	private static final EpicRenderer.Sink[] DEPTH = new EpicRenderer.Sink[TEXTURES.length];
	private static final EpicRenderer.Sink[] SEE = new EpicRenderer.Sink[TEXTURES.length];
	private static EpicRenderer.Sink backing;

	private static final int MAX_STARS = 60;
	private static final float[] STAR_DIR = new float[MAX_STARS * 3];
	private static final float[] STAR_SIZE = new float[MAX_STARS];
	private static final float[] STAR_PHASE = new float[MAX_STARS];
	private static int nebulaA;
	private static int nebulaB;
	private static final float[] D = new float[3];
	private static final float[] E = new float[3];
	private static final float[] R = new float[3];
	private static final float[] U = new float[3];

	private static void sinks() {
		if (backing != null) {
			return;
		}
		for (int i = 0; i < TEXTURES.length; i++) {
			RenderType depth = RenderTypes.entityTranslucentEmissive(TEXTURES[i]);
			DEPTH[i] = new EpicRenderer.Sink(depth, true);
			SEE[i] = new EpicRenderer.Sink(RenderTypes.textSeeThrough(TEXTURES[i]), true);
		}
		backing = new EpicRenderer.Sink(RenderTypes.endPortal(), false);
	}

	/** Seeds the star field and the nebula choice of one show. */
	static void build(long seed) {
		long mix = seed * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
		for (int i = 0; i < MAX_STARS; i++) {
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			float yaw = ((mix >>> 33) & 0xFFFF) / 65535.0F * 300.0F - 150.0F;
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			float elevation = 22.0F + (float) Math.sqrt(((mix >>> 33) & 0xFFFF) / 65535.0F) * 66.0F;
			dir(yaw, elevation, D);
			STAR_DIR[i * 3] = D[0];
			STAR_DIR[i * 3 + 1] = D[1];
			STAR_DIR[i * 3 + 2] = D[2];
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			float k = ((mix >>> 33) & 0xFFFF) / 65535.0F;
			STAR_SIZE[i] = 0.010F + k * k * 0.022F;
			STAR_PHASE[i] = ((mix >>> 17) & 0xFFFF) / 65535.0F * 6.2831855F;
		}
		nebulaA = (int) ((seed >>> 3) & 0x7FFFFFFF) % 3;
		nebulaB = (nebulaA + 1) % 3;
	}

	/** A world direction from yaw (relative to the show's centre yaw, degrees) and elevation (degrees). */
	private static void dir(float yawRel, float elevation, float[] out) {
		double yaw = Math.toRadians(CosmicSky.yaw() + yawRel);
		double e = Math.toRadians(elevation);
		out[0] = (float) (-Math.sin(yaw) * Math.cos(e));
		out[1] = (float) Math.sin(e);
		out[2] = (float) (Math.cos(yaw) * Math.cos(e));
	}

	private static int argb(float alpha, int rgb) {
		return (Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24) | (rgb & 0xFFFFFF);
	}

	/** Below 20° elevation the layers fade (to 0 at 15°), as the vistas of 28.3.3. */
	private static float elevationAlpha(float y) {
		double elevation = Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, y))));
		return (float) Math.max(0.0D, Math.min(1.0D, (elevation - 15.0D) / 5.0D));
	}

	static void render(EpicRenderer.Frame frame) {
		sinks();
		boolean see = CosmicSky.seeThrough();
		EpicRenderer.Sink[] sinks = see ? SEE : DEPTH;
		for (EpicRenderer.Sink sink : sinks) {
			sink.reset();
		}
		backing.reset();
		int tier = CosmicSky.tier();
		boolean pack = tier < 0;
		int level = Math.max(0, tier);
		float fade = CosmicSky.fade();
		float radius = frame.drawRadius * 0.92F;
		CosmicSky.Palette palette = CosmicSky.palette();
		float time = (float) (CosmicSky.ticks() + frame.partial);

		// the deep starfield backing (Maximal, vanilla only)
		if (level >= 2 && !pack && !see) {
			float h = frame.drawRadius * 0.97F;
			float s = frame.drawRadius * 1.25F;
			backing.vertex(-s, h, -s, 0.0F, 0.0F, -1, 0.0F, -1.0F, 0.0F);
			backing.vertex(s, h, -s, 0.0F, 0.0F, -1, 0.0F, -1.0F, 0.0F);
			backing.vertex(s, h, s, 0.0F, 0.0F, -1, 0.0F, -1.0F, 0.0F);
			backing.vertex(-s, h, s, 0.0F, 0.0F, -1, 0.0F, -1.0F, 0.0F);
		}
		// Sungai Bintang: a band arching across the sky (every tier)
		river(sinks[RIVER], radius, fade, palette);
		// Awan Nebula: 1 (Normal) or 2 (Maximal) huge soft quads turning 1°/s
		if (level >= 1 && !pack) {
			quad(sinks[NEBULA_0 + nebulaA], 28.0F, 62.0F, radius * 0.97F, radius * 0.75F, time * 0.05F, argb(0.55F * fade, palette.a), see);
			if (level >= 2) {
				quad(sinks[NEBULA_0 + nebulaB], -34.0F, 50.0F, radius * 0.98F, radius * 0.6F, -time * 0.04F, argb(0.45F * fade, palette.b), see);
			}
		}
		// Matahari dan Bulan Langit
		boolean both = level >= 2;
		if (level >= 1 && CosmicSky.sun()) {
			quad(sinks[SUN], 24.0F, 36.0F, radius * 0.9F, radius * 0.22F, 0.0F, argb(0.9F * fade, 0xFFF2C0), see);
			quad(sinks[SUN], 24.0F, 36.0F, radius * 0.9F, radius * 0.4F, time * 0.01F, argb(0.35F * fade, palette.b), see);
			quad(sinks[FLARE], 24.0F, 36.0F, radius * 0.89F, radius * 0.3F, 0.0F, argb(0.6F * fade, 0xFFFFFF), see);
		}
		if (level >= 1 && CosmicSky.moon() && (both || !CosmicSky.sun())) {
			quad(sinks[MOON], -42.0F, 46.0F, radius * 0.9F, radius * 0.1F, 0.0F, argb(0.85F * fade, 0xE8EEFF), see);
		}
		// Istana Langit: palace silhouettes on a cloud bank (Maximal, not under a pack)
		if (level >= 2 && !pack) {
			palace(sinks[PALACE], radius * 0.95F, fade);
		}
		// stars with a slow twinkle: 20 / 40 / 60 (× 0.6 under a pack)
		int stars = level >= 2 ? 60 : level == 1 ? 40 : 20;
		if (pack) {
			stars = Math.round(stars * 0.6F);
		}
		for (int i = 0; i < stars; i++) {
			float twinkle = 0.6F + 0.4F * (float) Math.sin(time * 0.15F + STAR_PHASE[i]);
			float x = STAR_DIR[i * 3] * radius;
			float y = STAR_DIR[i * 3 + 1] * radius;
			float z = STAR_DIR[i * 3 + 2] * radius;
			float a = fade * twinkle * (see ? 1.0F : elevationAlpha(STAR_DIR[i * 3 + 1]));
			frame.billboard(sinks[STAR], x, y, z, STAR_SIZE[i] * radius, argb(a, i % 5 == 0 ? palette.b : 0xFFFFFF));
		}
		int quads = 0;
		for (EpicRenderer.Sink sink : sinks) {
			quads += sink.vertices() / 4;
		}
		if (!EpicBudget.takeQuads(Math.min(quads, 64))) {
			return;
		}
		backing.doubleSided();
		backing.submit(frame.collector);
		for (EpicRenderer.Sink sink : sinks) {
			if (see) {
				sink.doubleSided();
			}
			sink.submit(frame.collector);
		}
	}

	/** A camera-facing square of half size {@code half} in direction (yaw, elevation), turned by {@code angle} radians. */
	private static void quad(EpicRenderer.Sink sink, float yawRel, float elevation, float distance, float half, float angle, int color, boolean see) {
		dir(yawRel, elevation, D);
		float cx = D[0] * distance;
		float cy = D[1] * distance;
		float cz = D[2] * distance;
		// right = normalize(D × up), up' = right × D
		float rx = -D[2];
		float rz = D[0];
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0E-4F) {
			rx = 1.0F;
			rz = 0.0F;
			rl = 1.0F;
		}
		rx /= rl;
		rz /= rl;
		float ux = -rz * D[1];
		float uy = rz * D[0] - rx * D[2];
		float uz = rx * D[1];
		float c = (float) Math.cos(angle);
		float s = (float) Math.sin(angle);
		R[0] = (rx * c + ux * s) * half;
		R[1] = (uy * s) * half;
		R[2] = (rz * c + uz * s) * half;
		U[0] = (ux * c - rx * s) * half;
		U[1] = (uy * c) * half;
		U[2] = (uz * c - rz * s) * half;
		int alpha = (color >>> 24) & 0xFF;
		float elevationFade = see ? 1.0F : elevationAlpha(D[1]);
		int faded = (Math.round(alpha * elevationFade) << 24) | (color & 0xFFFFFF);
		sink.vertex(cx - R[0] - U[0], cy - R[1] - U[1], cz - R[2] - U[2], 0.0F, 1.0F, faded, -D[0], -D[1], -D[2]);
		sink.vertex(cx + R[0] - U[0], cy + R[1] - U[1], cz + R[2] - U[2], 1.0F, 1.0F, faded, -D[0], -D[1], -D[2]);
		sink.vertex(cx + R[0] + U[0], cy + R[1] + U[1], cz + R[2] + U[2], 1.0F, 0.0F, faded, -D[0], -D[1], -D[2]);
		sink.vertex(cx - R[0] + U[0], cy - R[1] + U[1], cz - R[2] + U[2], 0.0F, 0.0F, faded, -D[0], -D[1], -D[2]);
	}

	/** The star river: 12 segments arching from −90° to +90° yaw, three texture repeats, 16° wide. */
	private static void river(EpicRenderer.Sink sink, float radius, float fade, CosmicSky.Palette palette) {
		int segments = 12;
		float width = (float) Math.toRadians(16.0D) * radius * 0.5F;
		for (int i = 0; i < segments; i++) {
			float a0 = -90.0F + 180.0F * i / segments;
			float a1 = -90.0F + 180.0F * (i + 1) / segments;
			float e0 = 58.0F + 22.0F * (float) Math.cos(Math.toRadians(a0));
			float e1 = 58.0F + 22.0F * (float) Math.cos(Math.toRadians(a1));
			dir(a0, e0, D);
			dir(a1, e1, E);
			// across = normalize(tangent × centre)
			float tx = E[0] - D[0];
			float ty = E[1] - D[1];
			float tz = E[2] - D[2];
			float ax = ty * D[2] - tz * D[1];
			float ay = tz * D[0] - tx * D[2];
			float az = tx * D[1] - ty * D[0];
			float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
			if (al < 1.0E-5F) {
				continue;
			}
			ax = ax / al * width;
			ay = ay / al * width;
			az = az / al * width;
			float u0 = (i % 4) / 4.0F;
			float u1 = u0 + 0.25F;
			int c0 = argb(0.8F * fade * elevationAlpha(D[1]), mixColor(0xFFFFFF, palette.a, 0.25F));
			int c1 = argb(0.8F * fade * elevationAlpha(E[1]), mixColor(0xFFFFFF, palette.a, 0.25F));
			float x0 = D[0] * radius;
			float y0 = D[1] * radius;
			float z0 = D[2] * radius;
			float x1 = E[0] * radius;
			float y1 = E[1] * radius;
			float z1 = E[2] * radius;
			sink.vertex(x0 - ax, y0 - ay, z0 - az, u0, 1.0F, c0, -D[0], -D[1], -D[2]);
			sink.vertex(x1 - ax, y1 - ay, z1 - az, u1, 1.0F, c1, -E[0], -E[1], -E[2]);
			sink.vertex(x1 + ax, y1 + ay, z1 + az, u1, 0.0F, c1, -E[0], -E[1], -E[2]);
			sink.vertex(x0 + ax, y0 + ay, z0 + az, u0, 0.0F, c0, -D[0], -D[1], -D[2]);
		}
	}

	/** The palace strip: 16 segments from −70° to +70° yaw between 17° and 31° elevation, two texture repeats. */
	private static void palace(EpicRenderer.Sink sink, float radius, float fade) {
		int segments = 16;
		for (int i = 0; i < segments; i++) {
			float a0 = -70.0F + 140.0F * i / segments;
			float a1 = -70.0F + 140.0F * (i + 1) / segments;
			float u0 = (i % 8) / 8.0F;
			float u1 = u0 + 0.125F;
			int color = argb(0.95F * fade, 0xFFFFFF);
			dir(a0, 17.0F, D);
			dir(a1, 17.0F, E);
			float bx0 = D[0] * radius;
			float by0 = D[1] * radius;
			float bz0 = D[2] * radius;
			float bx1 = E[0] * radius;
			float by1 = E[1] * radius;
			float bz1 = E[2] * radius;
			dir(a0, 31.0F, D);
			dir(a1, 31.0F, E);
			sink.vertex(bx0, by0, bz0, u0, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(bx1, by1, bz1, u1, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(E[0] * radius, E[1] * radius, E[2] * radius, u1, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(D[0] * radius, D[1] * radius, D[2] * radius, u0, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}
	}

	private static int mixColor(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private CosmicLayers() {
	}
}

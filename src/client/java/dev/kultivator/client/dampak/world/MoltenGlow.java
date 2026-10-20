package dev.kultivator.client.dampak.world;

import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kawah Lebur without Mode Kehancuran (DESIGN 28.8.3): a crater-shaped glowing decal ({@code ground_glow.png} in the
 * palette's glow colour, ≤ 64 quads in cells following the client heightmap, plus an optional {@code crack_decal.png}
 * layer) that cools over 20-40 s (colour ramps to dark, alpha to nothing), with 1-2 palette particles per tick for the
 * first 10 s. Real {@code SURFACE} batches get the same glow on the converted blocks' tops for 10 s. Visual only: nothing
 * is read from or sent to the server, no block is touched.
 */
final class MoltenGlow {

	private static final int MAX_DECALS = 24;
	private static final int MAX_SURFACES = 8;
	private static final int SURFACE_LIFE = 200;
	private static final int PARTICLE_TICKS = 200;
	private static final int REFRESH = 20;
	private static final double VIEW = 96.0D;

	private static final List<Decal> DECALS = new ArrayList<>();
	private static final List<Surface> SURFACES = new ArrayList<>();

	private static final class Decal {
		final Vec3 centre;
		final float radius;
		final int rgb;
		final String palette;
		final boolean crack;
		final double born;
		final int life;
		final int n;
		final float cell;
		final float[] heights;
		int refreshIn;

		Decal(Vec3 centre, float radius, int rgb, String palette, boolean crack, double born, int life) {
			this.centre = centre;
			this.radius = radius;
			this.rgb = rgb;
			this.palette = palette;
			this.crack = crack;
			this.born = born;
			this.life = life;
			this.n = Math.max(2, Math.min(8, (int) Math.ceil(radius * 2.0F)));
			this.cell = radius * 2.0F / n;
			this.heights = new float[n * n];
		}
	}

	private static final class Surface {
		final long[] positions;
		final int rgb;
		final double born;

		Surface(long[] positions, int rgb, double born) {
			this.positions = positions;
			this.rgb = rgb;
			this.born = born;
		}
	}

	// ================================================================== API

	/** A cooling glow decal of {@code radius} at {@code centre} (the impact), in the palette's colour. */
	static void decal(ClientLevel level, Vec3 centre, float radius, String palette, int fallbackRgb, boolean crack) {
		if (!finite(centre) || !(radius > 0.5F)) {
			return;
		}
		radius = Math.min(24.0F, radius);
		if (DECALS.size() >= MAX_DECALS) {
			DECALS.remove(0);
		}
		int life = 400 + DampakParticles.random().nextInt(401);
		Decal decal = new Decal(centre, radius, paletteColor(palette, fallbackRgb), palette, crack, DampakWorld.clock(0.0F), life);
		refresh(level, decal);
		DECALS.add(decal);
		DampakWorld.touchGeometry();
	}

	/** The glow on the tops of the blocks a real surface pass converted (10 s). */
	static void surface(long[] positions, String palette, int fallbackRgb) {
		if (positions.length == 0) {
			return;
		}
		if (SURFACES.size() >= MAX_SURFACES) {
			SURFACES.remove(0);
		}
		SURFACES.add(new Surface(positions, paletteColor(palette, fallbackRgb), DampakWorld.clock(0.0F)));
		DampakWorld.touchGeometry();
	}

	static boolean active() {
		return !DECALS.isEmpty() || !SURFACES.isEmpty();
	}

	static int count() {
		return DECALS.size() + SURFACES.size();
	}

	static void clear() {
		DECALS.clear();
		SURFACES.clear();
	}

	// ================================================================== tick

	static void tick(ClientLevel level, double clock) {
		for (Iterator<Decal> it = DECALS.iterator(); it.hasNext(); ) {
			Decal decal = it.next();
			double age = clock - decal.born;
			if (age >= decal.life) {
				it.remove();
				continue;
			}
			if (--decal.refreshIn <= 0) {
				refresh(level, decal);
			}
			if (age < PARTICLE_TICKS) {
				particles(decal);
			}
		}
		SURFACES.removeIf(surface -> clock - surface.born >= SURFACE_LIFE);
	}

	private static void refresh(ClientLevel level, Decal decal) {
		decal.refreshIn = REFRESH;
		double x0 = decal.centre.x - decal.radius;
		double z0 = decal.centre.z - decal.radius;
		for (int i = 0; i < decal.n; i++) {
			for (int j = 0; j < decal.n; j++) {
				double x = x0 + (i + 0.5D) * decal.cell;
				double z = z0 + (j + 0.5D) * decal.cell;
				decal.heights[i * decal.n + j] = (float) (DampakDisplays.ground(level, x, z, decal.centre.y) + SigilRenderer.LIFT);
			}
		}
	}

	private static void particles(Decal decal) {
		RandomSource random = DampakParticles.random();
		int n = DampakParticles.grant(1 + random.nextInt(2));
		ParticleOptions particle = paletteParticle(decal.palette);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = decal.radius * Math.sqrt(random.nextDouble()) * 0.9D;
			int ci = (int) Math.max(0, Math.min(decal.n - 1, (Math.cos(angle) * r + decal.radius) / decal.cell));
			int cj = (int) Math.max(0, Math.min(decal.n - 1, (Math.sin(angle) * r + decal.radius) / decal.cell));
			double y = decal.heights[ci * decal.n + cj] + 0.1D;
			DampakParticles.one(particle, decal.centre.x + Math.cos(angle) * r, y, decal.centre.z + Math.sin(angle) * r, 0.0D, 0.02D, 0.0D);
		}
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		for (int d = 0; d < DECALS.size(); d++) {
			Decal decal = DECALS.get(d);
			if (frame.dist2(decal.centre.x, decal.centre.y, decal.centre.z) > VIEW * VIEW) {
				continue;
			}
			float t = (float) ((frame.clock - decal.born) / decal.life);
			if (t >= 1.0F) {
				continue;
			}
			// hot for the first quarter, then the colour cools to a dark ember and the alpha falls away
			float cool = t < 0.25F ? 0.0F : (t - 0.25F) / 0.75F;
			float alpha = (t < 0.02F ? t / 0.02F : 1.0F) * (1.0F - cool * cool) * 0.85F;
			int rgb = ARGB.srgbLerp(Math.min(1.0F, cool * 1.4F), 0xFF000000 | decal.rgb, 0xFF000000 | darker(decal.rgb)) & 0xFFFFFF;
			if (alpha <= 0.02F) {
				continue;
			}
			int cells = decal.n * decal.n;
			if (!EpicBudget.takeQuads(decal.crack ? cells * 2 : cells)) {
				continue;
			}
			int color = ARGB.color(Math.round(alpha * 255.0F), rgb);
			int crackColor = ARGB.color(Math.round(Math.min(1.0F, alpha * 1.2F) * 255.0F), lighter(rgb));
			float size = decal.radius * 2.0F;
			double x0 = decal.centre.x - decal.radius;
			double z0 = decal.centre.z - decal.radius;
			float limit = (decal.radius + decal.cell * 0.7F) * (decal.radius + decal.cell * 0.7F);
			for (int i = 0; i < decal.n; i++) {
				for (int j = 0; j < decal.n; j++) {
					double cx = x0 + (i + 0.5D) * decal.cell - decal.centre.x;
					double cz = z0 + (j + 0.5D) * decal.cell - decal.centre.z;
					if (cx * cx + cz * cz > limit) {
						continue;
					}
					float ax = frame.rx(x0 + i * decal.cell);
					float az = frame.rz(z0 + j * decal.cell);
					float bx = ax + decal.cell;
					float bz = az + decal.cell;
					float y = frame.ry(decal.heights[i * decal.n + j]);
					float u0 = i * decal.cell / size;
					float u1 = (i + 1) * decal.cell / size;
					float v0 = j * decal.cell / size;
					float v1 = (j + 1) * decal.cell / size;
					frame.cell(frame.ground, ax, az, bx, bz, y, u0, v0, u1, v1, color);
					if (decal.crack) {
						frame.cell(frame.crack, ax, az, bx, bz, y + 0.005F, u0, v0, u1, v1, crackColor);
					}
				}
			}
		}
		for (int s = 0; s < SURFACES.size(); s++) {
			Surface surface = SURFACES.get(s);
			float t = (float) ((frame.clock - surface.born) / SURFACE_LIFE);
			if (t >= 1.0F) {
				continue;
			}
			float alpha = 0.8F * (1.0F - t * t);
			int color = ARGB.color(Math.round(alpha * 255.0F), surface.rgb);
			int n = surface.positions.length;
			if (!EpicBudget.takeQuads(n)) {
				continue;
			}
			for (int i = 0; i < n; i++) {
				BlockPos pos = BlockPos.of(surface.positions[i]);
				if (frame.dist2(pos.getX(), pos.getY(), pos.getZ()) > VIEW * VIEW) {
					continue;
				}
				float ax = frame.rx(pos.getX() - 0.1D);
				float az = frame.rz(pos.getZ() - 0.1D);
				float y = frame.ry(pos.getY() + 1.0D + SigilRenderer.LIFT);
				frame.cell(frame.ground, ax, az, ax + 1.2F, az + 1.2F, y, 0.0F, 0.0F, 1.0F, 1.0F, color);
			}
		}
	}

	// ================================================================== palettes

	/** The glow colour of a palette ({@code Dampak.Palette} name), or {@code fallback}. */
	static int paletteColor(@Nullable String palette, int fallback) {
		if (palette == null) {
			return fallback & 0xFFFFFF;
		}
		return switch (palette) {
			case "FIRE" -> 0xFF6A1A;
			case "ICE" -> 0x9FE8FF;
			case "LIGHTNING" -> 0xB57CFF;
			case "SWORD" -> 0xE8F0FF;
			case "SPACE" -> 0x7A4CFF;
			case "EARTH" -> 0xFFB040;
			case "LIFE" -> 0x7CFF8A;
			case "SHADOW" -> 0x5FD8E8;
			case "TAIJI" -> 0xF4F4F4;
			default -> fallback & 0xFFFFFF;
		};
	}

	/** The lingering particle of a palette (lava drip, snowflake, spark, end rod, portal, dust, spores, soul). */
	static ParticleOptions paletteParticle(@Nullable String palette) {
		if (palette == null) {
			return ParticleTypes.END_ROD;
		}
		return switch (palette) {
			case "FIRE" -> ParticleTypes.DRIPPING_LAVA;
			case "ICE" -> ParticleTypes.SNOWFLAKE;
			case "LIGHTNING" -> ParticleTypes.ELECTRIC_SPARK;
			case "SPACE" -> ParticleTypes.REVERSE_PORTAL;
			case "EARTH" -> ParticleTypes.WHITE_ASH;
			case "LIFE" -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case "SHADOW" -> ParticleTypes.SOUL;
			default -> ParticleTypes.END_ROD;
		};
	}

	private static int darker(int rgb) {
		int r = (rgb >> 16 & 0xFF) / 5 + 0x18;
		int g = (rgb >> 8 & 0xFF) / 8 + 0x08;
		int b = (rgb & 0xFF) / 8 + 0x04;
		return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
	}

	private static int lighter(int rgb) {
		int r = (rgb >> 16 & 0xFF) + 255 >> 1;
		int g = (rgb >> 8 & 0xFF) + 255 >> 1;
		int b = (rgb & 0xFF) + 255 >> 1;
		return r << 16 | g << 8 | b;
	}

	private static boolean finite(Vec3 v) {
		return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	private MoltenGlow() {
	}
}

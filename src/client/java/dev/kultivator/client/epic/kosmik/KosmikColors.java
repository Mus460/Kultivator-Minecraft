package dev.kultivator.client.epic.kosmik;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The home colours of the stages (DESIGN 32.2, contract C32.2): the caster's surroundings as the planet should show them.
 * Sampled once at film arm (or preview) by {@link #sample}: the average of 9 {@link BiomeColors} samples (the caster's
 * column and 8 on a 48-b ring), {@code snowy} = the caster's biome is cold enough to snow there, {@code dry} = it has no
 * precipitation. Outside the overworld the default colours are returned with {@code overworld = false}.
 *
 * @param grass   average grass colour (RGB)
 * @param foliage average foliage colour (RGB)
 * @param water   average water colour (RGB)
 */
public record KosmikColors(int grass, int foliage, int water, boolean snowy, boolean dry, boolean overworld) {

	/** Plains-like defaults (the Nether, the End, no level). */
	public static final KosmikColors DEFAULT = new KosmikColors(0x79C05A, 0x59AE30, 0x3F76E4, false, false, false);

	private static final int RING = 48;

	/** Samples the colours around {@code pos}; never throws (the defaults on any failure). */
	public static KosmikColors sample(ClientLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return DEFAULT;
		}
		try {
			if (!Level.OVERWORLD.equals(level.dimension())) {
				return DEFAULT;
			}
			long gr = 0L;
			long gg = 0L;
			long gb = 0L;
			long fr = 0L;
			long fg = 0L;
			long fb = 0L;
			long wr = 0L;
			long wg = 0L;
			long wb = 0L;
			int n = 0;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int i = 0; i < 9; i++) {
				if (i == 0) {
					at.set(pos);
				} else {
					double a = Math.PI * 2.0D * (i - 1) / 8.0D;
					at.set(pos.getX() + (int) Math.round(Math.cos(a) * RING), pos.getY(), pos.getZ() + (int) Math.round(Math.sin(a) * RING));
				}
				if (!level.hasChunkAt(at)) {
					continue;
				}
				int grass = BiomeColors.getAverageGrassColor(level, at);
				int foliage = BiomeColors.getAverageFoliageColor(level, at);
				int water = BiomeColors.getAverageWaterColor(level, at);
				gr += grass >> 16 & 0xFF;
				gg += grass >> 8 & 0xFF;
				gb += grass & 0xFF;
				fr += foliage >> 16 & 0xFF;
				fg += foliage >> 8 & 0xFF;
				fb += foliage & 0xFF;
				wr += water >> 16 & 0xFF;
				wg += water >> 8 & 0xFF;
				wb += water & 0xFF;
				n++;
			}
			if (n == 0) {
				return DEFAULT;
			}
			int grass = rgb(gr / n, gg / n, gb / n);
			int foliage = rgb(fr / n, fg / n, fb / n);
			int water = rgb(wr / n, wg / n, wb / n);
			Holder<Biome> biome = level.getBiome(pos);
			boolean snowy = biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
			boolean dry = !biome.value().hasPrecipitation();
			return new KosmikColors(grass, foliage, water, snowy, dry, true);
		} catch (RuntimeException exception) {
			return DEFAULT;
		}
	}

	/** A hash of the colours (the planet bake re-runs only when it changed). */
	public int hash() {
		int h = grass;
		h = h * 31 + foliage;
		h = h * 31 + water;
		h = h * 31 + (snowy ? 1 : 0);
		h = h * 31 + (dry ? 1 : 0);
		return h * 31 + (overworld ? 1 : 0);
	}

	/**
	 * The home latitude of the planet in degrees north (32.2): snowy 60, dry 22, jungle-like (warm, saturated foliage) 6,
	 * otherwise 40.
	 */
	public float homeLatitude() {
		if (snowy) {
			return 60.0F;
		}
		if (dry) {
			return 22.0F;
		}
		int r = foliage >> 16 & 0xFF;
		int g = foliage >> 8 & 0xFF;
		int b = foliage & 0xFF;
		// jungle foliage is 30BB0B; plains 77AB2F and forest 59AE30 must not match
		boolean jungle = g >= 175 && r <= 72 && b <= 32;
		return jungle ? 6.0F : 40.0F;
	}

	private static int rgb(long r, long g, long b) {
		return (int) ((Math.max(0L, Math.min(255L, r)) << 16) | (Math.max(0L, Math.min(255L, g)) << 8) | Math.max(0L, Math.min(255L, b)));
	}
}

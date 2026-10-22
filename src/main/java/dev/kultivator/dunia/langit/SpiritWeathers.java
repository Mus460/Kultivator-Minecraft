package dev.kultivator.dunia.langit;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuaca Roh (DESIGN 34.5.10): the spirit weather of the Overworld and Alam Abadi. The world is split into square cells of
 * {@code cellSize} blocks; a cell's weather is a pure function of the world seed, the cell and the 5-minute period
 * ({@link #PERIOD} game ticks), biased by the biome at the cell centre (sampled from the noise only: no chunk is loaded):
 * Cerah 70 %, Kabut Qi Tipis 20 %, Kabut Qi Pekat 7 %, Fajar Ungu 3 % (only at dawn, 23 000-1 000 of the clock; outside
 * dawn that roll is clear). Swamps, forests, taiga, jungles, cherry groves and mountains are mistier, deserts, badlands
 * and savannas clearer; Alam Abadi is misty.
 * <p>
 * Independent of vanilla weather, which is never read or changed here (W3). Players are synced the 3 × 3 cells around
 * them when their cell or a kind in that view changes; {@code DuniaEvents.WEATHER_CHANGED} fires once per changed cell
 * that a player can see. {@code uji cuaca} overrides one cell for some minutes (memory only). From Nascent Soul on a
 * player's divine sense foretells a mist that comes or goes 30 s before the change ({@link #sense}).
 */
public final class SpiritWeathers {

	/** A cell's weather lasts this many game ticks (5 minutes). */
	public static final int PERIOD = 6000;
	public static final double THIN = 0.20D;
	public static final double DENSE = 0.07D;
	public static final double DAWN = 0.03D;
	public static final int DAWN_FROM = 23000;
	public static final int DAWN_TO = 1000;
	/** The divine sense of {@link #sense} speaks this many ticks before the change (30 s). */
	public static final int SENSE_TICKS = 600;
	private static final int BIAS_CACHE = 4096;
	private static final int KNOWN_CACHE = 2048;

	/** A test override of one cell. */
	private record Forced(SpiritWeather weather, long until) {
	}

	private static final Map<ResourceKey<Level>, Map<Long, Forced>> OVERRIDES = new HashMap<>();
	/** Biome bias of a cell centre (multiplies the mist chances), per dimension, LRU. */
	private static final Map<ResourceKey<Level>, Map<Long, Float>> BIAS = new HashMap<>();
	/** The last kind seen for a cell in some player's view (for {@code WEATHER_CHANGED}), per dimension, LRU. */
	private static final Map<ResourceKey<Level>, Map<Long, Integer>> KNOWN = new HashMap<>();
	/** What each player was sent: dimension hash, cell size, cell x, cell z, then the nine kinds. */
	private static final Map<UUID, int[]> SENT = new HashMap<>();

	// ================================================================== queries

	public static boolean eligible(Level level) {
		return SkyEvents.eligible(level);
	}

	/** True when the weather runs at all in {@code level} (switch, master switch, dimension). */
	static boolean on(Level level) {
		return LangitConfig.get().weather && DuniaSpine.enabled() && eligible(level);
	}

	public static int cellSize() {
		return LangitConfig.get().cellSize;
	}

	public static int cellOf(int block) {
		return Math.floorDiv(block, cellSize());
	}

	/** The cell id ({@code cx << 32 | cz}) for {@code DuniaEvents.WEATHER_CHANGED}. */
	public static long cellId(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	/** The spirit weather at {@code pos} now. */
	public static SpiritWeather at(ServerLevel level, BlockPos pos) {
		if (!on(level)) {
			return SpiritWeather.CLEAR;
		}
		return cell(level, cellOf(pos.getX()), cellOf(pos.getZ()));
	}

	/** The spirit weather of cell (cx, cz) now (CLEAR where the weather is off). */
	public static SpiritWeather cell(ServerLevel level, int cx, int cz) {
		if (!on(level)) {
			return SpiritWeather.CLEAR;
		}
		long now = level.getGameTime();
		Map<Long, Forced> overrides = OVERRIDES.get(level.dimension());
		if (overrides != null) {
			Forced override = overrides.get(cellId(cx, cz));
			if (override != null) {
				if (now < override.until()) {
					return override.weather();
				}
				overrides.remove(cellId(cx, cz));
			}
		}
		return natural(level, cx, cz, Math.floorDiv(now, PERIOD), dawn(level));
	}

	/** The weather of cell (cx, cz) in {@code period} without test overrides ({@code dawn}: whether that time is dawn). */
	static SpiritWeather natural(ServerLevel level, int cx, int cz, long period, boolean dawn) {
		double roll = hash01(level.getSeed(), cx, cz, period);
		double bias = bias(level, cx, cz);
		double dense = DENSE * bias;
		double thin = THIN * bias;
		if (roll < dense) {
			return SpiritWeather.DENSE_MIST;
		}
		if (roll < dense + thin) {
			return SpiritWeather.THIN_MIST;
		}
		if (roll < dense + thin + DAWN && dawn) {
			return SpiritWeather.PURPLE_DAWN;
		}
		return SpiritWeather.CLEAR;
	}

	/** True at dawn (23 000-1 000 of the overworld clock). */
	public static boolean dawn(Level level) {
		return dawnAt(level.getOverworldClockTime());
	}

	/** True when overworld clock {@code clock} is dawn. */
	static boolean dawnAt(long clock) {
		int tod = (int) Math.floorMod(clock, (long) SkyEvents.DAY);
		return tod >= DAWN_FROM || tod < DAWN_TO;
	}

	/**
	 * The weather cell (cx, cz) will have in the next period (the deterministic roll of the next 5 minutes; test overrides
	 * are not foreseen). The Indra Rohani of {@link #sense} and {@code uji cuaca ramalan} read it.
	 */
	static SpiritWeather forecast(ServerLevel level, int cx, int cz) {
		if (!on(level)) {
			return SpiritWeather.CLEAR;
		}
		long next = Math.floorDiv(level.getGameTime(), PERIOD) + 1L;
		return natural(level, cx, cz, next, dawnAt(level.getOverworldClockTime() + ticksToNextPeriod(level)));
	}

	/** True while a test override holds cell (cx, cz). */
	static boolean overridden(ServerLevel level, int cx, int cz) {
		Map<Long, Forced> overrides = OVERRIDES.get(level.dimension());
		Forced forced = overrides == null ? null : overrides.get(cellId(cx, cz));
		return forced != null && level.getGameTime() < forced.until();
	}

	/** Game ticks until the weather of every cell rolls again. */
	public static long ticksToNextPeriod(Level level) {
		return PERIOD - Math.floorMod(level.getGameTime(), (long) PERIOD);
	}

	/** {@code uji cuaca}: sets the cell around {@code pos} to {@code weather} for {@code minutes}. */
	static void override(ServerLevel level, BlockPos pos, SpiritWeather weather, int minutes) {
		long id = cellId(cellOf(pos.getX()), cellOf(pos.getZ()));
		OVERRIDES.computeIfAbsent(level.dimension(), k -> new HashMap<>())
				.put(id, new Forced(weather, level.getGameTime() + Math.max(1, minutes) * 1200L));
	}

	/** The site multiplier of a weather kind (1 for clear). */
	public static double factor(SpiritWeather weather) {
		LangitConfig config = LangitConfig.get();
		return switch (weather) {
			case THIN_MIST -> config.thinFactor;
			case DENSE_MIST -> config.denseFactor;
			case PURPLE_DAWN -> config.dawnFactor;
			case CLEAR -> 1.0D;
		};
	}

	// ================================================================== bias

	/**
	 * The biome bias of the cell centre: the noise biome at y 64 (no chunk is touched), 1.6 misty, 0.3 dry, 0.8 oceans,
	 * 1.4 in Alam Abadi, else 1. Cached per cell.
	 */
	static double bias(ServerLevel level, int cx, int cz) {
		Map<Long, Float> cache = BIAS.computeIfAbsent(level.dimension(), k -> lru(BIAS_CACHE));
		long id = cellId(cx, cz);
		Float known = cache.get(id);
		if (known != null) {
			return known;
		}
		float value;
		if (!level.dimension().equals(Level.OVERWORLD)) {
			value = 1.4F;
		} else {
			int size = cellSize();
			int x = cx * size + size / 2;
			int z = cz * size + size / 2;
			Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z));
			value = biasOf(biome);
		}
		cache.put(id, value);
		return value;
	}

	private static float biasOf(Holder<Biome> biome) {
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.CHERRY_GROVE) || biome.is(Biomes.PALE_GARDEN)
				|| biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)
				|| biome.is(BiomeTags.IS_MOUNTAIN)) {
			return 1.6F;
		}
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
			return 0.3F;
		}
		if (biome.is(BiomeTags.IS_OCEAN)) {
			return 0.8F;
		}
		return 1.0F;
	}

	// ================================================================== sync

	/** Every 40 ticks from {@link Langit}: each player's 3 × 3 view, sent only when it changed. */
	static void tick(MinecraftServer server) {
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			ServerLevel level = player.level();
			if (!on(level)) {
				int[] sent = SENT.get(player.getUUID());
				if (sent != null && sent[1] != 0) {
					SENT.put(player.getUUID(), new int[]{0, 0, 0, 0});
					Net.send(player, LangitCuacaPayload.clear());
				}
				continue;
			}
			int size = cellSize();
			int cx = cellOf(player.getBlockX());
			int cz = cellOf(player.getBlockZ());
			int[] now = new int[13];
			now[0] = level.dimension().identifier().hashCode();
			now[1] = size;
			now[2] = cx;
			now[3] = cz;
			byte[] kinds = new byte[9];
			Map<Long, Integer> known = KNOWN.computeIfAbsent(level.dimension(), k -> lru(KNOWN_CACHE));
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					SpiritWeather weather = cell(level, cx + dx, cz + dz);
					int i = (dz + 1) * 3 + dx + 1;
					kinds[i] = (byte) weather.ordinal();
					now[4 + i] = weather.ordinal();
					long id = cellId(cx + dx, cz + dz);
					Integer before = known.put(id, weather.ordinal());
					if (before != null && before != weather.ordinal()) {
						DuniaEvents.fireWeatherChanged(level, id, weather);
					}
				}
			}
			int[] sent = SENT.get(player.getUUID());
			if (sent == null || !Arrays.equals(sent, now)) {
				SENT.put(player.getUUID(), now);
				Net.send(player, new LangitCuacaPayload(size, cx, cz, kinds));
			}
			long left = ticksToNextPeriod(level);
			if (left <= SENSE_TICKS && left > SENSE_TICKS - 40 && !player.isSpectator()) {
				sense(player, level, cx, cz);
			}
		}
	}

	/**
	 * Indra Rohani (detail pass): from Nascent Soul on a cultivator's divine sense feels the spirit weather turn about
	 * 30 seconds before it does, when a mist comes or goes in their cell (one action bar line; test overrides are skipped).
	 */
	private static void sense(ServerPlayer player, ServerLevel level, int cx, int cz) {
		if (!Cultivators.get(player).realm().atLeast(Realm.NASCENT_SOUL) || overridden(level, cx, cz)) {
			return;
		}
		SpiritWeather current = cell(level, cx, cz);
		SpiritWeather next = forecast(level, cx, cz);
		if (next == current || (!next.misty() && !current.misty()) || !Langit.once(player, "indra", 400)) {
			return;
		}
		int seconds = (int) (ticksToNextPeriod(level) / 20L);
		Component line = next.misty()
				? Component.translatable("kultivator.langit.indra.coming", LangitCommands.weatherName(next), seconds)
				: Component.translatable("kultivator.langit.indra.leaving", LangitCommands.weatherName(current), seconds);
		Msg.actionBar(player, line.copy().withColor(0xC8B8FF));
	}

	/** Forces a resend to {@code player} on the next pass (join, dimension change, an override). */
	static void resend(@Nullable ServerPlayer player) {
		if (player != null) {
			SENT.remove(player.getUUID());
		}
	}

	static void resendAll() {
		SENT.clear();
	}

	static void forget(ServerPlayer player) {
		SENT.remove(player.getUUID());
	}

	static void clear() {
		OVERRIDES.clear();
		BIAS.clear();
		KNOWN.clear();
		SENT.clear();
	}

	// ================================================================== helpers

	/** A deterministic 0..1 roll of (seed, cell, period). */
	static double hash01(long seed, int cx, int cz, long period) {
		long h = seed ^ 0x6A09E667F3BCC909L;
		h = mix(h + cx * 0x9E3779B97F4A7C15L);
		h = mix(h + cz * 0xC2B2AE3D27D4EB4FL);
		h = mix(h + period * 0x165667B19E3779F9L);
		return (h >>> 11) * 0x1.0p-53;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static <V> Map<Long, V> lru(int max) {
		return new LinkedHashMap<>(64, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
				return size() > max;
			}
		};
	}

	private SpiritWeathers() {
	}
}

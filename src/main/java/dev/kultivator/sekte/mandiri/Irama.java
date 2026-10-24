package dev.kultivator.sekte.mandiri;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The rhythm of a sect day (DESIGN 39.4.4): the world hour, the hour curve {@code jam(k, h)} of every activity, the moon
 * (full moon = qi tide, new moon = temptation), the weather, the sect's rest day and the hall's events (a tournament,
 * a raid). Pure arithmetic over the clock plus a few cheap world reads; nothing is stored. The hour is
 * {@code (dayTime / 1000 + 6) mod 24} like 38.N, so hour 6 is sunrise and 18 sunset.
 * <p>
 * The table of 39.4.4: 05-06 morning salute (contemplation in rows, meditation), 06-12 morning cultivation (meditation,
 * forms, seclusion, departures), 12-13 lunch, 13-18 work and study, 18-19 supper and tea, 19-22 free time (friends,
 * sparring, forms, contemplation), 22-05 night (meditation from Golden Core, sleep for Mortal to Foundation, the gate
 * watch for guardians). Every value is 0..2.
 */
public final class Irama {

	/** The ticks of one world hour. */
	public static final int JAM_TICK = Kegiatan.JAM;
	/** Morning salute hour (05:00). */
	public static final int JAM_SALAM = 5;
	/** The sect's rest day: world day {@code n mod 7 == 6}. */
	public static final int HARI_ISTIRAHAT = 6;

	/** The time of day 0..23999 of the overworld clock. */
	public static long waktu(Level level) {
		return Math.floorMod(level.getOverworldClockTime(), 24000L);
	}

	/** The world hour 0..23 ({@code 0} = midnight, 6 = sunrise). */
	public static int jam(Level level) {
		return (int) Math.floorMod(waktu(level) / 1000L + 6L, 24L);
	}

	/** The world day number (overworld clock / 24000, like {@code Ingatan.today}). */
	public static long hari(Level level) {
		return Math.max(0L, Math.floorDiv(level.getOverworldClockTime(), 24000L));
	}

	/** Minutes past the hour 0..59 (for the salute's ten minutes). */
	public static int menit(Level level) {
		return (int) (Math.floorMod(waktu(level), 1000L) * 60L / 1000L);
	}

	/** The moon phase index 0..7 at {@code pos} (0 full, 4 new); the day number mod 8 where the dimension has no moon. */
	public static int bulan(ServerLevel level, @Nullable BlockPos pos) {
		try {
			MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos == null ? BlockPos.ZERO : pos);
			if (phase != null) {
				return phase.index();
			}
		} catch (RuntimeException ignored) {
			// fall through to the plain calendar
		}
		return (int) Math.floorMod(hari(level), 8L);
	}

	public static boolean purnama(ServerLevel level, @Nullable BlockPos pos) {
		return bulan(level, pos) == MoonPhase.FULL_MOON.index();
	}

	public static boolean bulanBaru(ServerLevel level, @Nullable BlockPos pos) {
		return bulan(level, pos) == MoonPhase.NEW_MOON.index();
	}

	/** True for the night hours 22-04. */
	public static boolean malam(int jam) {
		return jam >= 22 || jam < 5;
	}

	public static boolean hariIstirahat(long hari) {
		return Math.floorMod(hari, 7L) == HARI_ISTIRAHAT;
	}

	/** True when it rains at {@code pos} (not snow, not under a roof check: the weather of the place). */
	public static boolean hujan(ServerLevel level, BlockPos pos) {
		return level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
	}

	/** True when it snows at {@code pos} (cold biome in a storm): snow play for the young. */
	public static boolean salju(ServerLevel level, BlockPos pos) {
		return level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW;
	}

	// ================================================================== the hour curve jam(k, h)

	/**
	 * The hour curve of activity {@code k} at hour {@code h} (39.4.4) for a disciple of realm {@code realm} (ordinal: 0
	 * Mortal ... 3 Golden Core) who is a gate guardian or not.
	 */
	public static double kurva(Kegiatan k, int h, int realm, boolean penjaga) {
		boolean muda = realm < 3;
		if (malam(h)) {
			return switch (k) {
				case MEDITASI -> muda ? 0.4D : 1.8D;
				case TUTUP_DIRI -> 1.0D;
				case ISTIRAHAT -> muda ? 2.0D : 0.9D;
				case JAGA_GERBANG -> penjaga ? 1.6D : 0.3D;
				case JAGA_FORMASI, JAGA_TEROBOSAN, TEROBOSAN -> 1.0D;
				case MERENUNG -> h >= 22 ? 0.6D : 0.3D;
				case SERAP_BATU -> 0.8D;
				default -> 0.2D;
			};
		}
		if (h == 5) {
			return switch (k) {
				case MERENUNG -> 2.0D;
				case MEDITASI -> 1.6D;
				case TANDING -> 0.2D;
				case ISTIRAHAT -> 0.6D;
				case MAKAN, SOSIAL, BERMAIN -> 0.4D;
				default -> 0.7D;
			};
		}
		if (h < 12) {
			return switch (k) {
				case MEDITASI -> 1.8D;
				case LATIH_JURUS -> 1.6D;
				case TUTUP_DIRI -> 1.5D;
				case BERKEMAS, BERANGKAT, PERJALANAN -> 1.8D;
				case SERAP_BATU -> 1.3D;
				case ISTIRAHAT -> 0.3D;
				case MAKAN -> h == 6 ? 1.2D : 0.3D;
				case SOSIAL, BERMAIN -> 0.6D;
				default -> 1.0D;
			};
		}
		if (h == 12) {
			return switch (k) {
				case MAKAN -> 2.0D;
				case SOSIAL -> 1.6D;
				case LATIH_JURUS, TANDING -> 0.4D;
				case TUTUP_DIRI -> 0.5D;
				default -> 0.8D;
			};
		}
		if (h < 18) {
			return switch (k) {
				case KEBUN, TAMBANG, POS_TUGAS, MERAMU, JIMAT, BELAJAR, MENGAJAR -> 1.7D;
				case JAGA_FORMASI -> 1.3D;
				case TUTUP_DIRI -> 0.4D;
				case ISTIRAHAT -> 0.5D;
				case MAKAN -> 0.3D;
				default -> 1.0D;
			};
		}
		if (h == 18) {
			return switch (k) {
				case MAKAN -> 2.0D;
				case SOSIAL -> 1.8D;
				case KEBUN, TAMBANG, POS_TUGAS -> 0.4D;
				default -> 0.8D;
			};
		}
		// 19-22 free time
		return switch (k) {
			case SOSIAL, BERMAIN -> 1.8D;
			case TANDING -> 1.6D;
			case LATIH_JURUS, MERENUNG -> 1.5D;
			case KEBUN, TAMBANG, POS_TUGAS, MERAMU, JIMAT -> 0.4D;
			case BERKEMAS, BERANGKAT, PERJALANAN -> 0.3D;
			case MAKAN -> 0.4D;
			default -> 1.0D;
		};
	}

	/** The day factor: moon, rest day, weather (39.4.4); 1 when nothing applies. */
	public static double hariIni(Kegiatan k, int h, long hari, int bulan, boolean hujan, boolean salju, int realm) {
		double f = 1.0D;
		if (bulan == 0 && malam(h) && k == Kegiatan.MEDITASI) {
			f *= 1.4D;
		}
		if (bulan == 4 && malam(h) && k.perjalanan()) {
			f *= 0.3D;
		}
		if (hariIstirahat(hari)) {
			switch (k) {
				case KEBUN, TAMBANG, POS_TUGAS, MERAMU, JIMAT -> f *= 0.3D;
				case SOSIAL, BERMAIN -> f *= 1.5D;
				case BERKEMAS, BERANGKAT -> f *= 1.5D;
				default -> {
				}
			}
		}
		if (hujan) {
			switch (k) {
				case LATIH_JURUS, TANDING, KEBUN -> f *= 0.5D;
				case SOSIAL -> f *= 1.5D;
				case BERMAIN -> f *= 0.3D;
				default -> {
				}
			}
		}
		if (k == Kegiatan.BERMAIN) {
			// Snow play is for the young (Mortal to Foundation); festival and harvest play needs nothing.
			f *= salju && realm < 3 ? 2.0D : 0.6D;
		}
		return f;
	}

	/** True while the hall's tournament runs (everyone watches or trains) or a raid is on (only the watch counts). */
	public static boolean turnamen(UUID owner) {
		try {
			return SekteApi.turnamen().running(owner);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** True while a raid threatens the hall (omen or live): every non-guard activity is cancelled. */
	public static boolean serbuan(UUID owner) {
		try {
			return dev.kultivator.musuh.SectRaids.active(owner);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** The hall stele (for the moon and weather reads), or null. */
	public static @Nullable BlockPos pusat(UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall == null ? null : hall.pos();
	}

	private Irama() {
	}
}

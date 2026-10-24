package dev.kultivator.sekte.mandiri.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Extra journey destinations from other modules (DESIGN 39.6.1, 39.10.3): 37 cloud docks, 38 districts, 38GU caves,
 * 38DS sect compounds. Registered with {@code MandiriApi.addTujuanProvider}; asked only when a disciple plans a journey
 * (at most once per hour per hall, never per tick), so a provider may read its saved data but must not scan the world.
 * Journeys stay in the hall's own dimension. Server side only.
 */
@FunctionalInterface
public interface TujuanProvider {

	/**
	 * One destination.
	 *
	 * @param id     stable id ({@code <module>:<what>:<n>}), used in {@code larang} and the travel record
	 * @param jenis  the journey kind it serves
	 * @param pos    the arrival point
	 * @param tier   danger tier (0 safe)
	 * @param nama   the shown name (a translation key or a literal name)
	 * @param town   packed town centre for town-like places ({@code BlockPos.asLong}), or 0
	 * @param situs  the {@code DuniaSites}/{@code TempatApi} site id, or null
	 */
	record Tujuan(String id, JenisJalan jenis, BlockPos pos, int tier, String nama, long town, @Nullable String situs) {
		public Tujuan {
			id = id == null ? "" : id;
			jenis = jenis == null ? JenisJalan.BELANJA : jenis;
			pos = pos == null ? BlockPos.ZERO : pos.immutable();
			tier = Math.max(0, tier);
			nama = nama == null ? "" : nama;
		}
	}

	/** Destinations of kind {@code jenis} within {@code radius} blocks of the hall at {@code aula}. */
	List<Tujuan> tujuan(ServerLevel level, UUID owner, BlockPos aula, JenisJalan jenis, int radius);
}

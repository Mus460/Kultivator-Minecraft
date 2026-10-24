package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bargaining at a stall (DESIGN 39.6.5, {@code ACT_TAWAR} 6 s on the town body): the asked price times the character's
 * factor. The cunning press hard (x0.85-0.92), merchants know the trade (x0.90), the humble ask politely (x0.97), the
 * brave pay what is fair (x1.0), the timid give in (x1.03) and the proud do not haggle at all (x1.05); a master whose
 * Good Name in that town reaches Warga gets x0.95 on top. A cunning disciple offends the keeper one time in ten: that
 * stall will not sell to it again today (a funny diary line).
 */
public final class Tawar {

	/**
	 * The outcome.
	 *
	 * @param awal       the asked price per unit
	 * @param akhir      the agreed price per unit
	 * @param tersinggung the keeper refused (nothing bought)
	 * @param gaya       how it went: {@code licik}, {@code pedagang}, {@code sopan}, {@code adil}, {@code pasrah}, {@code gengsi}
	 */
	public record Hasil(long awal, long akhir, boolean tersinggung, String gaya) {
	}

	/** Bargains for one unit asked at {@code awal} NB in {@code town}; {@code sifat} is the bitmask of {@link Watak}. */
	public static Hasil tawar(MinecraftServer server, int sifat, @Nullable JalanHidup jalan, UUID owner, long town, long awal,
			RandomSource random) {
		double f;
		String gaya;
		if (Watak.punya(sifat, Sifat.LICIK)) {
			if (random.nextFloat() < 0.10F) {
				return new Hasil(awal, awal, true, "licik");
			}
			f = 0.85D + random.nextDouble() * 0.07D;
			gaya = "licik";
		} else if (jalan == JalanHidup.PEDAGANG) {
			f = 0.90D;
			gaya = "pedagang";
		} else if (Watak.punya(sifat, Sifat.SOMBONG)) {
			f = 1.05D;
			gaya = "gengsi";
		} else if (Watak.punya(sifat, Sifat.RENDAH_HATI)) {
			f = 0.97D;
			gaya = "sopan";
		} else if (Watak.punya(sifat, Sifat.PENAKUT)) {
			f = 1.03D;
			gaya = "pasrah";
		} else {
			f = 1.0D;
			gaya = "adil";
		}
		if (owner != null && town != 0L && KotaData.get(server).reputation(town, owner) >= KotaBesarApi.NB_WARGA) {
			f *= 0.95D;
		}
		long akhir = Math.max(1L, Math.round(awal * f));
		return new Hasil(awal, akhir, false, gaya);
	}

	private Tawar() {
	}
}

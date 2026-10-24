package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The capital public face (contract C38.KS1, owner 38KS): Nama Baik ranks (38.B.5.1, on the town module's
 * {@code KotaData.reputation}), the crime record and wanted state ({@link Hukum}, 38.B.5.2) and the running calendar event
 * ({@link KotaBesarKalender}, 38.B.6). Server thread only. Answers are neutral (no rank effects, no crime, no event) for
 * a place that is not a capital or while {@code kotaBesar.aktif} is off.
 */
public final class KotaBesarApi {

	/** Nama Baik bounds of the ranks (38.B.5.1): <= -30 Musuh Kota, < 0 Orang Asing, < 20 Tamu, < 50 Warga, < 80 Terhormat. */
	public static final int NB_MUSUH = -30;
	public static final int NB_WARGA = 20;
	public static final int NB_TERHORMAT = 50;
	public static final int NB_PAHLAWAN = 80;

	/** Rank {@link LembarKunci#PANGKAT_MUSUH} .. {@link LembarKunci#PANGKAT_PAHLAWAN} of Nama Baik {@code nb}. */
	public static int pangkat(int nb) {
		if (nb <= NB_MUSUH) {
			return LembarKunci.PANGKAT_MUSUH;
		}
		if (nb < 0) {
			return LembarKunci.PANGKAT_ASING;
		}
		if (nb < NB_WARGA) {
			return LembarKunci.PANGKAT_TAMU;
		}
		if (nb < NB_TERHORMAT) {
			return LembarKunci.PANGKAT_WARGA;
		}
		if (nb < NB_PAHLAWAN) {
			return LembarKunci.PANGKAT_TERHORMAT;
		}
		return LembarKunci.PANGKAT_PAHLAWAN;
	}

	/** The rank id for lang {@code kultivator.kota_besar.pangkat.<id>}. */
	public static String pangkatId(int pangkat) {
		return switch (pangkat) {
			case LembarKunci.PANGKAT_MUSUH -> "musuh";
			case LembarKunci.PANGKAT_ASING -> "asing";
			case LembarKunci.PANGKAT_WARGA -> "warga";
			case LembarKunci.PANGKAT_TERHORMAT -> "terhormat";
			case LembarKunci.PANGKAT_PAHLAWAN -> "pahlawan";
			default -> "tamu";
		};
	}

	/**
	 * Nama Baik rank of {@code player} in town {@code town}: 0 Musuh Kota .. 5 Pahlawan Kota (38.B.5.1). Any town gives
	 * the rank of its Nama Baik; only a capital attaches the 38 effects to it.
	 */
	public static int pangkatNamaBaik(ServerPlayer player, long town) {
		if (town == 0L) {
			return LembarKunci.PANGKAT_TAMU;
		}
		MinecraftServer server = player.level().getServer();
		return pangkat(KotaData.get(server).reputation(town, player.getUUID()));
	}

	/** Records a crime of {@code jenis} worth {@code poin} crime points (38.B.5.2); counted as witnessed. */
	public static void kejahatan(ServerPlayer player, long town, String jenis, int poin) {
		Hukum.kejahatan(player, town, jenis, poin, player.blockPosition(), false);
	}

	/** True when {@code player} is wanted (25+ points) in capital {@code town}. */
	public static boolean buronan(ServerPlayer player, long town) {
		return Hukum.buronan(player, town);
	}

	/** Crime points of {@code player} in capital {@code town} (0-200). */
	public static int poinKejahatan(ServerPlayer player, long town) {
		return town == 0L ? 0 : Hukum.poin(player.level().getServer(), town, player.getUUID());
	}

	/** The running capital event of place {@code situsId} ({@code LembarKunci.Acara.ACARA_*}), or null. */
	public static @Nullable String acaraAktif(long situsId) {
		return KotaBesarKalender.aktif(situsId);
	}

	/** True while the 38 shops of capital {@code town} stay shut for a day after a lost beast-tide defence (38.B.6.3). */
	public static boolean tokoTutup(MinecraftServer server, long town) {
		return town != 0L && GelombangBinatang.tokoTutup(server, town);
	}

	/** True when {@code player} holds the capital title {@code gelar} ({@code tamu_istana}, {@code juara_pil}, ...). */
	public static boolean punyaGelar(ServerPlayer player, String gelar) {
		return KotaBesarData.get(player.level().getServer()).punyaGelar(player.getUUID(), gelar);
	}

	private KotaBesarApi() {
	}
}

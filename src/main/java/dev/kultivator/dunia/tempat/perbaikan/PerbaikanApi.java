package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.Kota;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Repair and compensation public face (contract C38.PR1, owner 38PR, DESIGN 38.R). Server thread only; every answer is
 * neutral (0 / false / null / 1.0) while {@code perbaikan.aktif} is off or before the server started.
 * <ul>
 *   <li>{@link #takut}: the fear meter of a place (38.R.5). Owners of dialogue, shops, quests and the tea house read it:
 *       20+ stuttering lines, a 10 % "offering" discount ({@link #diskonPersembahan}), quests politely declined, no
 *       rumours shared; 60+ deep bows and guards that look away (crimes still recorded).</li>
 *   <li>{@link #adaKerusakan}, {@link #tagihanTerbuka}: damage waiting for repair, the player's open bill.</li>
 *   <li>{@link #faktorHarga}: 1.10 for 7 days after a refused bill at that place (38.R.4), for the shop owners.</li>
 *   <li>{@link #ditakuti}: true when the place is too afraid of this player to bill him (strength gap, 38.R.5).</li>
 * </ul>
 */
public final class PerbaikanApi {

	private static @Nullable MinecraftServer server() {
		return Perbaikan.server();
	}

	/** Fear meter 0..100 of place {@code situsId} toward overwhelmingly strong visitors (38.R.5). */
	public static int takut(long situsId) {
		MinecraftServer s = server();
		if (s == null || !PerbaikanConfig.get().aktif || !PerbaikanConfig.get().takutAktif) {
			return 0;
		}
		return Takut.nilai(s, situsId);
	}

	/** True while place {@code situsId} has unrepaired recorded damage. */
	public static boolean adaKerusakan(long situsId) {
		MinecraftServer s = server();
		if (s == null || !PerbaikanConfig.get().aktif) {
			return false;
		}
		for (Gugus g : PerbaikanData.get(s).gugus.values()) {
			if (g.situsId == situsId && !g.tuntas()) {
				return true;
			}
		}
		return false;
	}

	/** The open bill of {@code player} (waiting for the collector or shown and not answered), or null. */
	public static @Nullable Tagihan tagihanTerbuka(ServerPlayer player) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return null;
		}
		PerbaikanData.TagihanData bill = Penagihan.terbuka(player.level().getServer(), player.getUUID());
		return bill == null ? null : bill.snapshot();
	}

	/** Price factor for {@code player} at place {@code situsId}: 1.10 for 7 world days after he refused a bill there. */
	public static double faktorHarga(ServerPlayer player, long situsId) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return 1.0D;
		}
		MinecraftServer s = player.level().getServer();
		PerbaikanData.PemainTempat pt = PerbaikanData.get(s).pemainAda(player.getUUID(), situsId);
		return pt != null && pt.hargaNaikSampai >= Kota.day(s) ? 1.10D : 1.0D;
	}

	/** True when place {@code situsId} is too afraid of {@code player} to bill him (38.R.5). */
	public static boolean ditakuti(ServerPlayer player, long situsId) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return false;
		}
		TempatRawat t = Rekam.bySitus(situsId);
		return t != null && Takut.berlaku(player, t);
	}

	/** The "offering" discount a frightened place gives {@code player} (0.10 at fear 20+ when he is feared, else 0). */
	public static double diskonPersembahan(ServerPlayer player, long situsId) {
		return takut(situsId) >= Takut.AMBANG_ANAK && ditakuti(player, situsId) ? 0.10D : 0.0D;
	}

	/** Help points of {@code player} at place {@code situsId} (38.R.6; 100 = "Sahabat"). */
	public static int poinBantu(ServerPlayer player, long situsId) {
		if (player == null) {
			return 0;
		}
		PerbaikanData.PemainTempat pt = PerbaikanData.get(player.level().getServer()).pemainAda(player.getUUID(), situsId);
		return pt == null ? 0 : pt.poinBantu;
	}

	private PerbaikanApi() {
	}
}

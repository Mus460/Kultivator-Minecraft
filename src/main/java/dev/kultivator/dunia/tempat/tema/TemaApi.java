package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Contract C38.T1 (DESIGN 38.T.14.4): what the core 38 modules ask of the themed towns. Every method is static, has a
 * safe default and never throws, so callers (38PR billing, 38EK caravans and auctions, 38W zones, 38KS teleports) compile
 * and run from wave W0 on. The real answers come from 38TK inti, which installs one {@link Penyedia} with
 * {@link #pasang(Penyedia)} from {@code KotaTema.init()}; nobody else calls {@code pasang}.
 * <p>
 * Defaults (no provider, or {@code kotaTema.aktif} false): no theme anywhere, caravans look like {@code GEROBAK}, demand
 * factor 1.0, Infami and Gentar 0, never inside a Zona Perjanjian, refusing an iblis bill does nothing, no caravan body.
 */
public final class TemaApi {

	/** Caravan look: an ox cart (the 38EK default). */
	public static final String RUPA_GEROBAK = "GEROBAK";
	/** Caravan look: a camel string (a route that touches an oasis town). */
	public static final String RUPA_UNTA = "UNTA";
	/** Caravan look: a merchant junk (harbour to harbour). */
	public static final String RUPA_KAPAL = "KAPAL";

	/**
	 * The implementation 38TK inti installs. Each method mirrors the static one of the same name; the facade catches
	 * exceptions and falls back to the default, so a provider bug never breaks a core module.
	 */
	public interface Penyedia {

		@Nullable String temaDi(ServerLevel level, BlockPos pos);

		String rupaKafilah(long situsAsal, long situsTujuan);

		double permintaan(long situsId, Item item);

		int infami(ServerPlayer player);

		void tambahInfami(ServerPlayer player, int delta, String alasan);

		int gentar(ServerPlayer player, long situsId);

		boolean zonaPerjanjian(ServerLevel level, BlockPos pos);

		void tolakTagihanIblis(ServerPlayer player, long situsId);

		@Nullable Entity tubuhKafilah(ServerLevel level, String rupa, Vec3 pos, float yaw);
	}

	private static volatile @Nullable Penyedia penyedia;

	/** Installs the 38TK inti implementation (once, from {@code KotaTema.init()}; a second call replaces it). */
	public static void pasang(Penyedia baru) {
		penyedia = baru;
	}

	/** True when a provider is installed and themed-town behaviour is switched on. */
	public static boolean aktif() {
		return penyedia != null && TemaConfig.inti().aktif;
	}

	/**
	 * The theme id ({@code pelabuhan}, {@code oasis}, {@code air}, {@code gunung}, {@code iblis}) of the themed town whose
	 * zone holds {@code pos}, or null. Satellites ({@code reruntuhan_gurun}, {@code pos_pemburu}) are not towns: null.
	 */
	public static @Nullable String temaDi(ServerLevel level, BlockPos pos) {
		Penyedia p = penyedia;
		if (p == null || level == null || pos == null || !TemaConfig.inti().aktif) {
			return null;
		}
		try {
			return p.temaDi(level, pos);
		} catch (RuntimeException exception) {
			gagal("temaDi", exception);
			return null;
		}
	}

	/**
	 * The look of a caravan between two sites (38EK {@code KafilahSekte}/{@code KafilahJalan}): {@link #RUPA_UNTA},
	 * {@link #RUPA_KAPAL} or {@link #RUPA_GEROBAK} (default).
	 */
	public static String rupaKafilah(long situsAsal, long situsTujuan) {
		Penyedia p = penyedia;
		if (p == null || !TemaConfig.inti().aktif) {
			return RUPA_GEROBAK;
		}
		try {
			String rupa = p.rupaKafilah(situsAsal, situsTujuan);
			return rupa == null || rupa.isEmpty() ? RUPA_GEROBAK : rupa;
		} catch (RuntimeException exception) {
			gagal("rupaKafilah", exception);
			return RUPA_GEROBAK;
		}
	}

	/**
	 * The sale-demand factor of {@code item} at site {@code situsId} (38.T.10.1 column, at most 1.35; a producer 0.8-1.0;
	 * 0 = "not bought here"). Default 1.0 for every item and site, and while {@code kotaTema.dagang} is off.
	 */
	public static double permintaan(long situsId, Item item) {
		Penyedia p = penyedia;
		if (p == null || item == null || !TemaConfig.inti().aktif || !TemaConfig.inti().dagang) {
			return 1.0D;
		}
		try {
			double factor = p.permintaan(situsId, item);
			return Double.isFinite(factor) ? Math.max(0.0D, Math.min(1.35D, factor)) : 1.0D;
		} catch (RuntimeException exception) {
			gagal("permintaan", exception);
			return 1.0D;
		}
	}

	/** The player's global Infami 0-100 (38.T.7.8 butir 4); 0 while {@code temaIblis.infami} is off. */
	public static int infami(ServerPlayer player) {
		Penyedia p = penyedia;
		if (p == null || player == null || !TemaConfig.inti().aktif || !TemaConfig.iblis().infami) {
			return 0;
		}
		try {
			return Math.max(0, Math.min(100, p.infami(player)));
		} catch (RuntimeException exception) {
			gagal("infami", exception);
			return 0;
		}
	}

	/**
	 * Changes the player's Infami by {@code delta} (clamped to 0-100 by the provider). {@code alasan} is a short key
	 * for the log and the hub card ({@code tebus}, {@code kontrak}, {@code gulungan}, {@code buronan}, ...).
	 */
	public static void tambahInfami(ServerPlayer player, int delta, String alasan) {
		Penyedia p = penyedia;
		if (p == null || player == null || delta == 0 || !TemaConfig.inti().aktif || !TemaConfig.iblis().infami) {
			return;
		}
		try {
			p.tambahInfami(player, delta, alasan == null ? "" : alasan);
		} catch (RuntimeException exception) {
			gagal("tambahInfami", exception);
		}
	}

	/** The player's Gentar 0-100 in the Kota Iblis {@code situsId} (38.T.7.8 butir 3); 0 elsewhere. */
	public static int gentar(ServerPlayer player, long situsId) {
		Penyedia p = penyedia;
		if (p == null || player == null || !TemaConfig.inti().aktif) {
			return 0;
		}
		try {
			return Math.max(0, Math.min(100, p.gentar(player, situsId)));
		} catch (RuntimeException exception) {
			gagal("gentar", exception);
			return 0;
		}
	}

	/** True inside a Kota Iblis Zona Perjanjian box (the only safe ground there, 38.T.7.8 butir 1). */
	public static boolean zonaPerjanjian(ServerLevel level, BlockPos pos) {
		Penyedia p = penyedia;
		if (p == null || level == null || pos == null || !TemaConfig.inti().aktif) {
			return false;
		}
		try {
			return p.zonaPerjanjian(level, pos);
		} catch (RuntimeException exception) {
			gagal("zonaPerjanjian", exception);
			return false;
		}
	}

	/**
	 * 38PR calls this when a player refuses a Harga Darah bill (billing style {@code iblis}): the enforcers turn hostile
	 * for one world day in that town and Gentar drops 10 (never Nama Baik).
	 */
	public static void tolakTagihanIblis(ServerPlayer player, long situsId) {
		Penyedia p = penyedia;
		if (p == null || player == null || !TemaConfig.inti().aktif) {
			return;
		}
		try {
			p.tolakTagihanIblis(player, situsId);
		} catch (RuntimeException exception) {
			gagal("tolakTagihanIblis", exception);
		}
	}

	/**
	 * 38EK calls this to show a themed caravan body near a player: {@code rupa} {@link #RUPA_UNTA} makes the camel string
	 * (lead camel returned, the others follow it), {@link #RUPA_KAPAL} a junk at the nearest harbour lane. Null = "draw
	 * your own cart" (the default, and for {@link #RUPA_GEROBAK}). The bodies are {@code noSave} and count toward
	 * {@code mekanisme_tema}.
	 */
	public static @Nullable Entity tubuhKafilah(ServerLevel level, String rupa, Vec3 pos, float yaw) {
		Penyedia p = penyedia;
		if (p == null || level == null || pos == null || rupa == null || RUPA_GEROBAK.equals(rupa) || !TemaConfig.inti().aktif) {
			return null;
		}
		try {
			return p.tubuhKafilah(level, rupa, pos, yaw);
		} catch (RuntimeException exception) {
			gagal("tubuhKafilah", exception);
			return null;
		}
	}

	private static void gagal(String what, RuntimeException exception) {
		Kultivator.LOGGER.warn("TemaApi.{} failed, using the default", what, exception);
	}

	private TemaApi() {
	}
}

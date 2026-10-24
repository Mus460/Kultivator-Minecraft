package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The eleven kinds of journey (DESIGN 39.6.1). A journey is pure data in {@code Halls.storeAway} (away reason
 * {@link MandiriIds#AWAY_JALAN}) plus a mirror in {@code MandiriData.jalan.<soul>}; stages are resolved lazily when due
 * (module 39C). Pure data, safe on both sides; never reorder.
 */
public enum JenisJalan {
	/** A town, a Grand City (38), the capital night market, a road post: buy the shopping list. */
	BELANJA("belanja", IzinKegiatan.BELANJA, 1, 3, 8),
	/** The town Mission Board or the Cultivator Guild (38). Risk = the mission rank. */
	MISI_GILDA("misi_gilda", IzinKegiatan.MISI_GILDA, -1, 24, 48),
	/** Sell the disciple's own work in town or at the Sect Stall (38); joined with BELANJA. */
	JUAL("jual", IzinKegiatan.BELANJA, 1, 3, 8),
	/** Plains and forests 600-2000 blocks out, a Beast Den (38GU). Risk = the beast tier. */
	BERBURU("berburu", IzinKegiatan.BERBURU, -1, 12, 24),
	/** Ruins and caves of phase 34. Risk = the site tier. */
	RERUNTUHAN("reruntuhan", IzinKegiatan.RERUNTUHAN, -1, 24, 48),
	/** Hermit caves, spirit veins and springs (38GU), spirit sites: far seclusion. */
	GUA_PERTAPA("gua_pertapa", IzinKegiatan.GUA, 1, 48, 120),
	/** A known projection realm. Risk = the realm tier. */
	ALAM_RAHASIA("alam_rahasia", IzinKegiatan.ALAM_RAHASIA, -1, 48, 72),
	/** Roads of 38 or the wilds, meeting wanderers. */
	BERKELANA("berkelana", IzinKegiatan.BERKELANA, 1, 24, 72),
	/** A village within 2000 blocks. */
	DESA("desa", IzinKegiatan.DESA_SEKTE, 1, 24, 24),
	/** An NPC sect already met, or its compound (38DS). Risk 1 for an ally, 3 otherwise. */
	SEKTE_TAMU("sekte_tamu", IzinKegiatan.DESA_SEKTE, 1, 24, 48),
	/** A temple, a road shrine or the own ancestors' graves. */
	ZIARAH("ziarah", IzinKegiatan.BERKELANA, 0, 12, 24);

	private final String id;
	private final IzinKegiatan izin;
	private final int risikoDasar;
	private final int lamaJamMin;
	private final int lamaJamMaks;

	JenisJalan(String id, IzinKegiatan izin, int risikoDasar, int lamaJamMin, int lamaJamMaks) {
		this.id = id;
		this.izin = izin;
		this.risikoDasar = risikoDasar;
		this.lamaJamMin = lamaJamMin;
		this.lamaJamMaks = lamaJamMaks;
	}

	public String id() {
		return id;
	}

	/** The permission group that allows it. */
	public IzinKegiatan izin() {
		return izin;
	}

	/** Base risk tier, or -1 when it comes from the destination (mission rank, beast/site/realm tier). */
	public int risikoDasar() {
		return risikoDasar;
	}

	/** Time at the destination without travel (world hours, 1 hour = 1000 ticks), lower bound. */
	public int lamaJamMin() {
		return lamaJamMin;
	}

	public int lamaJamMaks() {
		return lamaJamMaks;
	}

	/** {@code kultivator.sekte.mandiri.inti.jenis_jalan.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.jenis_jalan." + id;
	}

	public static @Nullable JenisJalan byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (JenisJalan jenis : values()) {
			if (jenis.id.equals(id)) {
				return jenis;
			}
		}
		return null;
	}
}

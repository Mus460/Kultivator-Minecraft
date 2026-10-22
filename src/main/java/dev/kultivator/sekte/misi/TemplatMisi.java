package dev.kultivator.sekte.misi;

import dev.kultivator.sect.event.Expeditions;
import org.jetbrains.annotations.Nullable;

/**
 * The twelve story mission templates of module X (DESIGN 36.2.7). Each has a tier range, a length in days, an expedition
 * loot row it shares ({@code Expeditions.lootTable}), three stages of a {@link Jenis} and a few flags. The id is saved in
 * the hall section and in every report scroll and is never renamed; the story texts live under
 * {@code kultivator.sekte.misi.t.<id>.*} (name, desc, three stages each with an {@code ok} and a {@code gagal} branch).
 * <p>
 * Sumber: <i>I Shall Seal the Heavens</i> ch. 19 (the sect's outer disciples go out and come back with two or three
 * talented youths, already the source of {@code Envoys}); the expeditions into the Magical Beast Mountain Range of
 * <i>Battle Through the Heavens</i> (already the source of {@code Expeditions}).
 */
public enum TemplatMisi {
	/** Kawal Kafilah: escort a merchant caravan through a pass. */
	KAWAL("kawal", 1, 6, 1, Expeditions.LOOT_CARAVAN, Jenis.JELAJAH, Jenis.TEMPUR, Jenis.DAMAI, 0, "護", 0xC9A227),
	/** Buru Binatang Iblis: hunt a demonic beast that preys on travellers. */
	BURU("buru", 2, 9, 2, Expeditions.LOOT_HUNT, Jenis.JELAJAH, Jenis.TEMPUR, Jenis.TEMPUR, 0, "獵", 0xB0413E),
	/** Selidiki Reruntuhan: an ancient cave abode opened by a landslide. */
	RERUNTUHAN("reruntuhan", 3, 9, 2, Expeditions.LOOT_SECRET, Jenis.JELAJAH, Jenis.LICIK, Jenis.TEMPUR, Flags.MAP, "墟", 0x8E7CC3),
	/** Utusan ke Sekte: carry the master's letter to an NPC sect (module D) or a rival group. */
	UTUSAN("utusan", 1, 9, 2, Expeditions.LOOT_PATROL, Jenis.JELAJAH, Jenis.DAMAI, Jenis.DAMAI, Flags.SECT, "使", 0x3D85C6),
	/** Cari Herba Langka: a thousand-year herb guarded by its beast. */
	HERBA("herba", 1, 8, 1, Expeditions.LOOT_HERBS, Jenis.JELAJAH, Jenis.JELAJAH, Jenis.TEMPUR, 0, "草", 0x6AA84F),
	/** Tolong Desa: a vassal village (or a village under the mountain) asks for help. */
	DESA("desa", 1, 5, 1, Expeditions.LOOT_PATROL, Jenis.JELAJAH, Jenis.TEMPUR, Jenis.DAMAI, Flags.VILLAGE, "村", 0xE69138),
	/** Rebut Pusaka: a treasure is born and every sect races for it. */
	PUSAKA("pusaka", 4, 9, 3, Expeditions.LOOT_SECRET, Jenis.LICIK, Jenis.TEMPUR, Jenis.TEMPUR, Flags.MAP | Flags.FOE_SECT, "寶", 0xF1C232),
	/** Cari Murid yang Mengembara: look for a disciple who left the sect (module P's list, contract C36.X4). */
	CARI("cari", 1, 9, 2, Expeditions.LOOT_PATROL, Jenis.JELAJAH, Jenis.DAMAI, Jenis.DAMAI, Flags.SEARCH, "尋", 0x76A5AF),
	/** Jaga Perbatasan: hold the border of the territory against raiders. */
	PERBATASAN("perbatasan", 1, 7, 2, Expeditions.LOOT_PATROL, Jenis.TEMPUR, Jenis.JELAJAH, Jenis.TEMPUR, Flags.FOE_SECT, "境", 0x999999),
	/** Lelang Kota: bid at a city auction with the hall's contribution points. */
	LELANG("lelang", 2, 9, 1, Expeditions.LOOT_SECRET, Jenis.DAMAI, Jenis.LICIK, Jenis.DAMAI, Flags.AUCTION, "市", 0xD5A6BD),
	/** Kabar Angin: chase a rumour of a treasure in the wilds. */
	KABAR("kabar", 1, 9, 1, Expeditions.LOOT_PATROL, Jenis.JELAJAH, Jenis.LICIK, Jenis.JELAJAH, Flags.MAP, "聞", 0x93C47D),
	/** Penyamaran: slip into a hostile sect in disguise and learn its plans. */
	SAMAR("samar", 3, 9, 3, Expeditions.LOOT_SECRET, Jenis.LICIK, Jenis.LICIK, Jenis.TEMPUR, Flags.FOE_SECT | Flags.SPY, "影", 0x674EA7);

	/** The kind of a stage: what it tests and which stance fits it. */
	public enum Jenis {
		TEMPUR("tempur", 0xC0392B, "戰"),
		LICIK("licik", 0x8E44AD, "謀"),
		DAMAI("damai", 0x2E86C1, "和"),
		JELAJAH("jelajah", 0x27AE60, "行");

		private final String id;
		private final int color;
		private final String glyph;

		Jenis(String id, int color, String glyph) {
			this.id = id;
			this.color = color;
			this.glyph = glyph;
		}

		public String id() {
			return id;
		}

		public int color() {
			return color;
		}

		public String glyph() {
			return glyph;
		}

		public String key() {
			return "kultivator.sekte.misi.jenis." + id;
		}

		public static Jenis byId(@Nullable String id) {
			for (Jenis jenis : values()) {
				if (jenis.id.equals(id)) {
					return jenis;
				}
			}
			return JELAJAH;
		}
	}

	/**
	 * The stance the master orders (DESIGN 36.2.7: Damai / Tegas / Licik). A stance fits one stage kind (+0.08) and clashes
	 * with another (-0.05); {@link Jenis#JELAJAH} is neutral to all three.
	 */
	public enum Pendekatan {
		DAMAI("damai", Jenis.DAMAI, Jenis.TEMPUR),
		TEGAS("tegas", Jenis.TEMPUR, Jenis.LICIK),
		LICIK("licik", Jenis.LICIK, Jenis.DAMAI);

		private final String id;
		private final Jenis fits;
		private final Jenis clashes;

		Pendekatan(String id, Jenis fits, Jenis clashes) {
			this.id = id;
			this.fits = fits;
			this.clashes = clashes;
		}

		public String id() {
			return id;
		}

		public Jenis fits() {
			return fits;
		}

		public Jenis clashes() {
			return clashes;
		}

		public String key() {
			return "kultivator.sekte.misi.sikap." + id;
		}

		public static Pendekatan byId(@Nullable String id) {
			for (Pendekatan p : values()) {
				if (p.id.equals(id)) {
					return p;
				}
			}
			return DAMAI;
		}
	}

	/** Template flags. */
	public static final class Flags {
		/** A legendary end may reveal a treasure birth ({@code DuniaApi.treasures()}): the "treasure map" of the report. */
		public static final int MAP = 1;
		/** Needs an NPC sect (module D) or a Dunia rival group to visit; the standing changes with the result. */
		public static final int SECT = 1 << 1;
		/** A vassal village is named; youths come home twice as often. */
		public static final int VILLAGE = 1 << 2;
		/** Looks for a departed disciple (contract C36.X4). */
		public static final int SEARCH = 1 << 3;
		/** The foe of the fights is a rival sect when one exists. */
		public static final int FOE_SECT = 1 << 4;
		/** Costs hall contribution points up front (half back on failure). */
		public static final int AUCTION = 1 << 5;
		/** Infiltration: success costs the target sect's standing nothing, failure costs it a lot. */
		public static final int SPY = 1 << 6;

		private Flags() {
		}
	}

	private final String id;
	private final int minTier;
	private final int maxTier;
	private final int days;
	private final int lootType;
	private final Jenis[] stages;
	private final int flags;
	private final String glyph;
	private final int color;

	TemplatMisi(String id, int minTier, int maxTier, int days, int lootType, Jenis s1, Jenis s2, Jenis s3, int flags, String glyph, int color) {
		this.id = id;
		this.minTier = minTier;
		this.maxTier = maxTier;
		this.days = days;
		this.lootType = lootType;
		this.stages = new Jenis[]{s1, s2, s3};
		this.flags = flags;
		this.glyph = glyph;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public int minTier() {
		return minTier;
	}

	public int maxTier() {
		return maxTier;
	}

	/** Length in in-game days before the config cap. */
	public int days() {
		return days;
	}

	public int lootType() {
		return lootType;
	}

	/** The kind of stage {@code i} (0-2). */
	public Jenis stage(int i) {
		return stages[Math.max(0, Math.min(2, i))];
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public String glyph() {
		return glyph;
	}

	public int color() {
		return color;
	}

	/** {@code kultivator.sekte.misi.t.<id>}: the name; {@code .desc}, {@code .s<1-3>.ok|gagal} follow it. */
	public String key() {
		return "kultivator.sekte.misi.t." + id;
	}

	/** The mission's length in ticks with the config cap. */
	public long ticks() {
		return 24000L * Math.max(1, Math.min(MisiConfig.get().durasiMaksHari, days));
	}

	public static @Nullable TemplatMisi byId(@Nullable String id) {
		if (id != null) {
			for (TemplatMisi t : values()) {
				if (t.id.equals(id)) {
					return t;
				}
			}
		}
		return null;
	}
}

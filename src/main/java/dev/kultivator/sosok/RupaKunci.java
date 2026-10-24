package dev.kultivator.sosok;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * A look key (DESIGN 40.4, contract C40.I1 {@code SosokApi.setRupa}): which row of the look tables dresses a body, plus
 * optional sect, Dao and rank overrides. Text form {@code kunci[|sekte=<LambangCache key>][|dao=<n>][|pangkat=<n>]},
 * synced as a String on {@code KultivatorNpc} ({@code DATA_RUPA}) and read by the client's recipe resolver (40K).
 * <p>
 * Keys ({@link #kunci}): {@code NpcRole.id()} for town and DESIGN 38 roles ({@code juru_lelang}, {@code menteri}, ...;
 * a 38 role whose constant does not exist yet already has a row), {@code d.<kind>} for Dimension D people,
 * {@code liar.<0-7>} for Rogue Cultivators, {@code utusan_langit}, {@code penonton.<kind>}, {@code murid.<rank 0-5>} and
 * {@code jalan.<path>} for disciples, {@code ketua_sekte} for NPC sect masters, {@code kelompok.<GROUP>} for a schedule
 * group fallback ({@code kelompok.PEKERJA}; case is kept). The recipe file stores rows as {@code peran.<key>}. The
 * sect value is a {@code LambangCache} key: {@code npc:<sect id>} for NPC sects ({@code SekteNpc.id}: {@code s0},
 * {@code s1}, ...; DESIGN 38DS) or {@code u:<owner uuid>} for a player's sect.
 * <p>
 * Examples: {@code murid.2|sekte=npc:s3} (an inner disciple of NPC sect {@code s3}),
 * {@code ketua_sekte|sekte=npc:s5|dao=4}, {@code juru_lelang}.
 */
public record RupaKunci(String kunci, @Nullable String sekte, int dao, int pangkat) {

	/** Longest text form accepted (the synced String stays small). */
	public static final int PANJANG_MAKS = 96;
	/** "Not set" for {@link #dao} and {@link #pangkat}. */
	public static final int TIDAK_ADA = -1;
	/** Highest disciple rank (Tetua Agung). */
	public static final int PANGKAT_MAKS = 5;
	/** The empty key: the body's own role decides the look. */
	public static final RupaKunci KOSONG = new RupaKunci("", null, TIDAK_ADA, TIDAK_ADA);

	public RupaKunci {
		kunci = bersih(kunci, false);
		sekte = sekte == null ? null : bersih(sekte, true);
		if (sekte != null && sekte.isEmpty()) {
			sekte = null;
		}
		dao = dao < 0 ? TIDAK_ADA : Math.min(dao, 255);
		pangkat = pangkat < 0 ? TIDAK_ADA : Math.min(pangkat, PANGKAT_MAKS);
	}

	/** A plain key without overrides. */
	public static RupaKunci dari(String kunci) {
		return new RupaKunci(kunci, null, TIDAK_ADA, TIDAK_ADA);
	}

	/** The look of a disciple rank 0-5 ({@code murid.<rank>}), optionally in a sect's uniform. */
	public static RupaKunci murid(int pangkat, @Nullable String sekte) {
		int p = Math.max(0, Math.min(PANGKAT_MAKS, pangkat));
		return new RupaKunci("murid." + p, sekte, TIDAK_ADA, p);
	}

	/** The look of an NPC sect member: {@code murid.<rank>|sekte=npc:<sect>} (DESIGN 38DS). */
	public static RupaKunci muridSekteNpc(int pangkat, String sekteNpc) {
		return murid(pangkat, "npc:" + sekteNpc);
	}

	/** The look of an NPC sect master: {@code ketua_sekte|sekte=npc:<sect>}. */
	public static RupaKunci ketuaSekteNpc(String sekteNpc) {
		return new RupaKunci("ketua_sekte", "npc:" + sekteNpc, TIDAK_ADA, PANGKAT_MAKS);
	}

	/**
	 * Parses the text form. Unknown parts are ignored, numbers that do not parse leave the field unset, and null, empty
	 * or over-long text gives {@link #KOSONG}.
	 */
	public static RupaKunci parse(@Nullable String text) {
		if (text == null || text.isBlank() || text.length() > PANJANG_MAKS) {
			return KOSONG;
		}
		String[] parts = text.split("\\|");
		String sekte = null;
		int dao = TIDAK_ADA;
		int pangkat = TIDAK_ADA;
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i];
			int eq = part.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String name = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			String value = part.substring(eq + 1).trim();
			switch (name) {
				case "sekte" -> sekte = value;
				case "dao" -> dao = angka(value);
				case "pangkat" -> pangkat = angka(value);
				default -> {
				}
			}
		}
		return new RupaKunci(parts[0], sekte, dao, pangkat);
	}

	/** The text form ({@link #parse} reads it back unchanged); empty for {@link #KOSONG}. */
	public String format() {
		if (kunci.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(kunci);
		if (sekte != null) {
			out.append("|sekte=").append(sekte);
		}
		if (dao != TIDAK_ADA) {
			out.append("|dao=").append(dao);
		}
		if (pangkat != TIDAK_ADA) {
			out.append("|pangkat=").append(pangkat);
		}
		String s = out.toString();
		return s.length() <= PANJANG_MAKS ? s : kunci;
	}

	public boolean kosong() {
		return kunci.isEmpty();
	}

	/** The recipe row name of the key ({@code peran.<kunci>}), or null when empty. */
	public @Nullable String barisResep() {
		return kunci.isEmpty() ? null : "peran." + kunci;
	}

	/** The part of the key before the first dot ({@code murid}, {@code d}, {@code liar}, ...; the key itself if none). */
	public String awalan() {
		int dot = kunci.indexOf('.');
		return dot < 0 ? kunci : kunci.substring(0, dot);
	}

	public RupaKunci withSekte(@Nullable String sekte) {
		return new RupaKunci(kunci, sekte, dao, pangkat);
	}

	public RupaKunci withDao(int dao) {
		return new RupaKunci(kunci, sekte, dao, pangkat);
	}

	public RupaKunci withPangkat(int pangkat) {
		return new RupaKunci(kunci, sekte, dao, pangkat);
	}

	@Override
	public String toString() {
		return format();
	}

	private static int angka(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return TIDAK_ADA;
		}
	}

	/**
	 * Keeps only {@code [A-Za-z0-9_.]} (plus {@code :} and {@code -} in a sect value, for {@code npc:<id>} and owner
	 * UUIDs), so a key can never break the {@code |} and {@code =} syntax. Case is kept ({@code kelompok.PEKERJA}).
	 */
	private static String bersih(@Nullable String raw, boolean sekte) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim();
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length() && out.length() < PANJANG_MAKS; i++) {
			char c = s.charAt(i);
			boolean huruf = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
			if (huruf || c == '_' || c == '.' || sekte && (c == ':' || c == '-')) {
				out.append(c);
			}
		}
		return out.toString();
	}
}

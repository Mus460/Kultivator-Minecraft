package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota42.warga.Hubungan;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Utility-based topic choice (DESIGN 42.5A.5; Dave Mark's curve-per-consideration scoring): up to 24 candidates from the
 * speaker's salient facts, needs, context and curiosity about the listener, each scored
 * <pre>U = 0.35 sal + 0.20 relLawan + 0.15 cocokEmosi + 0.10 konteks + 0.10 minat + 0.10 kebutuhan - 0.30 sudahDibahas - tabu</pre>
 * plus the trait multipliers of the topic's groups and the follow-up bonus of the previous topic's {@code lanjut}; then one
 * is drawn by a softmax with temperature {@code 0.25 + O/400} from the seeded generator. Weights come from
 * {@code topik.json bobot}, so balance is data.
 */
public final class PerencanaTopik {

	/** Candidates considered per turn. */
	public static final int KANDIDAT_MAKS = 24;
	/** Salient facts taken per turn. */
	public static final int FAKTA_MAKS = 8;

	/**
	 * One scored candidate.
	 *
	 * @param topik  topic id
	 * @param fakta  the fact behind it, null for context topics
	 * @param u      utility
	 * @param bagian each consideration (test command {@code cakap info} prints them)
	 */
	public record Kandidat(String topik, @Nullable Fakta fakta, double u, Map<String, Double> bagian) {
	}

	/**
	 * What the planner knows about the listener side of this turn.
	 *
	 * @param riwayat     history with the listening player (null for NPC-NPC)
	 * @param ingatan     the speaker's memory of the player (null for NPC-NPC)
	 * @param lawan       the NPC listener (null for a player)
	 * @param sejakPemain ticks since anyone discussed a topic with this player, by topic (empty for NPC-NPC)
	 * @param lanjut      topics that get the follow-up bonus (previous topic's {@code lanjut})
	 * @param dibahas     topics already touched this conversation
	 * @param rahasia     secrets unlocked (a good tone this conversation)
	 */
	public record Pendengar(@Nullable Pengetahuan.Riwayat riwayat, @Nullable Ingatan ingatan, @Nullable Warga lawan,
			Map<String, Long> sejakPemain, Collection<String> lanjut, Collection<String> dibahas, boolean rahasia) {
	}

	/** Scores every candidate of the speaker's snapshot {@code s}; sorted by utility, best first. */
	public static List<Kandidat> kandidat(Topik.Situasi s, Pengetahuan pk, Pendengar d) {
		Topik.Tabel tabel = CakapReload.topik();
		Map<String, Kandidat> out = new LinkedHashMap<>();
		Warga w = s.pembicara();
		double rel = relLawan(s, d);
		// salient facts (8 best)
		List<Fakta> facts = new ArrayList<>(pk.ingat(w, s.hari()));
		facts.sort((x, y) -> Double.compare(menonjol(y, s, d), menonjol(x, s, d)));
		for (int i = 0; i < facts.size() && i < FAKTA_MAKS; i++) {
			Fakta f = facts.get(i);
			Predikat p = Predikat.dari(f.predikat());
			if (p.rahasia() && rel < 0.7D && !d.rahasia()) {
				continue;
			}
			Topik.Data t = tabel.topik().get(p.topik());
			double sal = Math.min(1.0D, menonjol(f, s, d) / 3.0D);
			Kandidat k = skor(p.topik(), f, t, sal, 0.0D, s, d, rel, tabel);
			if (k != null) {
				out.merge(k.topik, k, (a, b) -> a.u >= b.u ? a : b);
			}
		}
		// needs: sickness, debt to or from the player
		if (w.kesehatan < 40) {
			tambah(out, skor("kesehatan", null, tabel.topik().get("kesehatan"), 0.4D, 1.0D, s, d, rel, tabel));
		}
		if (d.ingatan() != null && d.ingatan().utang != 0) {
			tambah(out, skor("uang", null, tabel.topik().get("uang"), 0.3D, 1.0D, s, d, rel, tabel));
		}
		// context topics (no fact needed) whose condition holds
		for (Topik.Data t : tabel.topik().values()) {
			if (out.size() >= KANDIDAT_MAKS) {
				break;
			}
			if (out.containsKey(t.id()) || t.golongan().contains("fakta") || t.golongan().contains("ucap")) {
				continue;
			}
			if (t.golongan().contains("tanya") && (d.lawan() != null || s.sifat().o() < 0)) {
				continue;
			}
			tambah(out, skor(t.id(), null, t, 0.0D, 0.0D, s, d, rel, tabel));
		}
		List<Kandidat> list = new ArrayList<>(out.values());
		list.sort((x, y) -> Double.compare(y.u, x.u));
		return list.size() > KANDIDAT_MAKS ? List.copyOf(list.subList(0, KANDIDAT_MAKS)) : List.copyOf(list);
	}

	private static void tambah(Map<String, Kandidat> out, @Nullable Kandidat k) {
		if (k != null) {
			out.merge(k.topik, k, (a, b) -> a.u >= b.u ? a : b);
		}
	}

	/** Salience of a fact for this listener (42.5A.4: relevance 2 for the listener / its family, self 2). */
	static double menonjol(Fakta f, Topik.Situasi s, Pendengar d) {
		Warga w = s.pembicara();
		double relevansi = 1.0D;
		if (d.lawan() != null && (Pengetahuan.keluarga(d.lawan(), f.subjek()) || Pengetahuan.keluarga(d.lawan(), f.objek()))) {
			relevansi = 2.0D;
		} else if (d.ingatan() != null && (f.subjek().jenis() == Fakta.Jenis.PEMAIN && f.subjek().id() == d.ingatan().pemain)) {
			relevansi = 2.0D;
		} else if (f.subjek().jenis() == Fakta.Jenis.WARGA && Hubungan.kekuatan(w, dariId(f.subjek())) != 0) {
			relevansi = 1.5D;
		}
		boolean diri = f.subjek().jenis() == Fakta.Jenis.WARGA && f.subjek().id() == w.id;
		return Lupa.menonjol(f, s.hari(), relevansi, diri);
	}

	private static Warga dariId(Fakta.Rujuk r) {
		Warga o = dev.kultivator.dunia.kota42.api.WargaApi.byId((int) r.id());
		return o == null ? new Warga(Integer.MAX_VALUE) : o;
	}

	/** {@code relLawan}: impression (player) or bond strength (warga) mapped to 0-1. */
	static double relLawan(Topik.Situasi s, Pendengar d) {
		if (d.lawan() != null) {
			return (Hubungan.kekuatan(s.pembicara(), d.lawan()) + 100) / 200.0D;
		}
		return (s.nilaiKesan() + 100) / 200.0D;
	}

	/** One candidate's utility; null when taboo or the topic's condition fails. */
	static @Nullable Kandidat skor(String id, @Nullable Fakta f, @Nullable Topik.Data t, double sal, double kebutuhan, Topik.Situasi s,
			Pendengar d, double rel, Topik.Tabel tabel) {
		Topik.Situasi sf = f == null ? s : s.denganFakta(f.predikat());
		int match = t == null ? 0 : Topik.cocok(t.syarat(), sf);
		if (match < 0) {
			return null;
		}
		if (t != null && tabu(t, f, s, d, rel)) {
			return null;
		}
		Map<String, Double> parts = new LinkedHashMap<>();
		int valensi = f != null ? f.valensi() : t == null ? 0 : t.valensi();
		double cocokEmosi = 1.0D - Math.min(1.0D, Math.abs(valensi / 3.0D - s.p()) / 2.0D);
		double konteks = t == null || t.syarat().isEmpty() ? 0.5D : match > 0 ? 1.0D : 0.5D;
		double minat = t != null && minat(t, s) ? 1.0D : 0.0D;
		double dibahas = sudahDibahas(id, s, d);
		parts.put("sal", sal);
		parts.put("relLawan", rel);
		parts.put("cocokEmosi", cocokEmosi);
		parts.put("konteks", konteks);
		parts.put("minat", minat);
		parts.put("kebutuhan", kebutuhan);
		parts.put("sudahDibahas", dibahas);
		double u = tabel.w("sal", 0.35D) * sal + tabel.w("relLawan", 0.20D) * rel + tabel.w("cocokEmosi", 0.15D) * cocokEmosi
				+ tabel.w("konteks", 0.10D) * konteks + tabel.w("minat", 0.10D) * minat + tabel.w("kebutuhan", 0.10D) * kebutuhan
				+ tabel.w("sudahDibahas", -0.30D) * dibahas;
		if (t != null) {
			Kepribadian.Sifat k = s.sifat();
			double trait = 0.0D;
			if (t.golongan().contains("sosial")) {
				trait += 0.1D * k.x() / 100.0D;
			}
			if (t.golongan().contains("konflik")) {
				trait -= 0.2D * k.a() / 100.0D;
			}
			if (t.golongan().contains("khawatir")) {
				trait += 0.2D * k.e() / 100.0D;
			}
			if (t.golongan().contains("gosip")) {
				trait -= 0.3D * k.h() / 100.0D;
			}
			if (t.golongan().contains("tanya")) {
				trait += 0.2D * k.o() / 100.0D;
			}
			parts.put("sifat", trait);
			u += trait;
		}
		if (d.lanjut().contains(id)) {
			double bonus = tabel.w("lanjut", 0.25D);
			parts.put("lanjut", bonus);
			u += bonus;
		}
		return new Kandidat(id, f, u, java.util.Collections.unmodifiableMap(parts));
	}

	/** {@code sudahDibahas}: {@code 0.9^hours} with this listener (<= 24 h), at least 0.5 when anyone told this player within 1 hour. */
	static double sudahDibahas(String topik, Topik.Situasi s, Pendengar d) {
		double v = 0.0D;
		if (d.dibahas().contains(topik)) {
			v = 1.0D;
		}
		if (d.riwayat() != null) {
			long ticks = d.riwayat().sejakTopik(topik, s.now());
			if (ticks <= 24L * Emosi.JAM) {
				v = Math.max(v, Math.pow(0.9D, ticks / (double) Emosi.JAM));
			}
		}
		Long sejak = d.sejakPemain().get(topik);
		if (sejak != null && sejak <= Emosi.JAM) {
			v = Math.max(v, 0.5D);
		}
		return v;
	}

	/** Taboos of 42.5A.5: mourning at a wedding/festival, gossip while working (C > 40), secrets to strangers, griping about officials when afraid. */
	static boolean tabu(Topik.Data t, @Nullable Fakta f, Topik.Situasi s, Pendengar d, double rel) {
		for (String id : t.tabu()) {
			boolean hit = switch (id) {
				case "duka_di_pesta" -> s.acara().stream().anyMatch(a -> a.contains("nikah") || a.contains("festival") || a.contains("pesta"));
				case "gosip_saat_kerja" -> s.bekerja() && s.sifat().c() > 40;
				case "rahasia_ke_asing" -> (s.kesan().equals("asing") || rel < 0.5D) && !d.rahasia();
				case "jelek_pejabat_takut" -> s.label().equals("cemas") || s.label().equals("takut");
				default -> false;
			};
			if (hit) {
				return true;
			}
		}
		return false;
	}

	private static boolean minat(Topik.Data t, Topik.Situasi s) {
		for (String m : t.minat()) {
			String key = m.toLowerCase(java.util.Locale.ROOT);
			if (key.equals(s.kelompok()) || key.equals(s.peran()) || s.kebiasaan().contains(key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Softmax choice with temperature {@code tau_dasar + O / tau_O} (42.5A.5); null for an empty list.
	 */
	public static @Nullable Kandidat pilih(List<Kandidat> list, Kepribadian.Sifat sifat, SplittableRandom r) {
		if (list.isEmpty()) {
			return null;
		}
		Topik.Tabel tabel = CakapReload.topik();
		double tau = Math.max(0.05D, tabel.w("tau_dasar", 0.25D) + sifat.o() / tabel.w("tau_O", 400.0D));
		double max = list.get(0).u;
		for (Kandidat k : list) {
			max = Math.max(max, k.u);
		}
		double[] w = new double[list.size()];
		double total = 0.0D;
		for (int i = 0; i < w.length; i++) {
			w[i] = Math.exp((list.get(i).u - max) / tau);
			total += w[i];
		}
		double roll = r.nextDouble() * total;
		for (int i = 0; i < w.length; i++) {
			roll -= w[i];
			if (roll <= 0.0D) {
				return list.get(i);
			}
		}
		return list.get(list.size() - 1);
	}

	private PerencanaTopik() {
	}
}

package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emotion as a point in Pleasure-Arousal-Dominance space (DESIGN 42.5A.3; Mehrabian and Russell 1974, Mehrabian 1996).
 * The resting point (temperament) comes from the six traits and the long mood of 42.4.11; events push the state away
 * ({@link Penilaian}) and it relaxes back with a half-life of a few world hours, computed lazily
 * ({@code v(t) = v0 + (v - v0) * 2^(-dt / t_half)}): nothing ticks, the state is only read when someone talks.
 * The eight octants name the feeling ({@link Label}); the label picks the face code, a gesture, the tone of answers and
 * the intensity word.
 */
public final class Emosi {

	/** Below this distance from the origin on every axis the label is {@code netral}. */
	public static final float NETRAL = 0.08F;
	/** Ticks of one world hour. */
	public static final long JAM = 1000L;

	/**
	 * One appraisal row of {@code emosi.json} ({@code peristiwa}).
	 *
	 * @param id         event kind
	 * @param p          pleasure push
	 * @param a          arousal push
	 * @param d          dominance push
	 * @param dRendah    dominance push used while the current D is below 0 ({@code dihina}: shame instead of anger), or null
	 * @param berarah    the feeling points at the cause (anger <i>at</i> X)
	 * @param efek       temporary trait shifts while the push lasts ({@code mabuk}: C -30)
	 * @param fakta      predicate recorded about the cause, or null
	 */
	public record Peristiwa(String id, float p, float a, float d, @Nullable Float dRendah, boolean berarah, Map<String, Integer> efek,
			@Nullable String fakta) {
	}

	/**
	 * One octant label.
	 *
	 * @param id     label id ({@code gembira puas kagum lega marah cemas jemu sedih})
	 * @param sp     sign of P (+1/-1)
	 * @param sa     sign of A
	 * @param sd     sign of D
	 * @param wajah  face code id ({@code KodeWajah})
	 * @param gestur gesture name (clip of {@code GerakId}/{@code KotaGerakId})
	 */
	public record Label(String id, int sp, int sa, int sd, String wajah, String gestur) {
		public static final Label NETRAL = new Label("netral", 0, 0, 0, "netral", "bicara_tangan");
	}

	/** One intensity threshold: the word {@code kultivator.cakap.lex.<kunci>} from distance {@code min}. */
	public record Intensitas(float min, String kunci) {
	}

	/** The table of {@code emosi.json}. */
	public record Tabel(Map<Character, Float> tp, Map<Character, Float> ta, Map<Character, Float> td, float bagi, float jepit,
			float suasanaBagi, float paruhJam, float paruhC, float paruhE, int simpanHari, Map<String, Peristiwa> peristiwa,
			List<Label> label, List<Intensitas> intensitas) {

		public static final Tabel KOSONG = new Tabel(Map.of('X', 0.21F, 'A', 0.59F, 'S', 0.19F), Map.of('O', 0.15F, 'A', 0.30F, 'S', -0.57F),
				Map.of('O', 0.25F, 'C', 0.17F, 'X', 0.60F, 'A', -0.32F), 100.0F, 0.6F, 250.0F, 2.0F, 200.0F, -300.0F, 2, Map.of(), List.of(),
				List.of(new Intensitas(0.0F, "agak"), new Intensitas(0.35F, "sangat"), new Intensitas(0.7F, "bukan_main")));

		static Tabel parse(JsonObject json) {
			JsonObject temp = CakapReload.objek(json, "temperamen");
			JsonObject paruh = CakapReload.objek(json, "paruh");
			Map<String, Peristiwa> events = new LinkedHashMap<>();
			for (JsonObject o : CakapReload.objekList(json, "peristiwa")) {
				String id = CakapReload.teks(o, "id", "");
				if (id.isEmpty()) {
					continue;
				}
				Map<String, Integer> efek = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> e : CakapReload.objek(o, "efek").entrySet()) {
					if (e.getValue().isJsonPrimitive()) {
						efek.put(e.getKey(), e.getValue().getAsInt());
					}
				}
				Float rendah = o.has("d_bila_rendah") && !o.get("d_bila_rendah").isJsonNull() ? o.get("d_bila_rendah").getAsFloat() : null;
				events.put(id, new Peristiwa(id, CakapReload.pecahan(o, "p", 0.0F), CakapReload.pecahan(o, "a", 0.0F),
						CakapReload.pecahan(o, "d", 0.0F), rendah, CakapReload.benar(o, "berarah", false), Map.copyOf(efek),
						CakapReload.teksAtauNull(o, "fakta")));
			}
			List<Label> labels = new ArrayList<>();
			for (JsonObject o : CakapReload.objekList(json, "label")) {
				labels.add(new Label(CakapReload.teks(o, "id", "netral"), (int) Math.signum(CakapReload.pecahan(o, "p", 0)),
						(int) Math.signum(CakapReload.pecahan(o, "a", 0)), (int) Math.signum(CakapReload.pecahan(o, "d", 0)),
						CakapReload.teks(o, "wajah", "netral"), CakapReload.teks(o, "gestur", "bicara_tangan")));
			}
			List<Intensitas> steps = new ArrayList<>();
			for (JsonObject o : CakapReload.objekList(json, "intensitas")) {
				steps.add(new Intensitas(CakapReload.pecahan(o, "min", 0.0F), CakapReload.teks(o, "kunci", "agak")));
			}
			steps.sort((x, y) -> Float.compare(x.min, y.min));
			return new Tabel(koef(CakapReload.objek(temp, "p"), KOSONG.tp), koef(CakapReload.objek(temp, "a"), KOSONG.ta),
					koef(CakapReload.objek(temp, "d"), KOSONG.td), CakapReload.pecahan(temp, "bagi", 100.0F),
					CakapReload.pecahan(temp, "jepit", 0.6F), CakapReload.pecahan(temp, "suasana_bagi", 250.0F),
					CakapReload.pecahan(paruh, "jam", 2.0F), CakapReload.pecahan(paruh, "C", 200.0F), CakapReload.pecahan(paruh, "E", -300.0F),
					CakapReload.angka(json, "simpan_hari", 2), Map.copyOf(events), List.copyOf(labels),
					steps.isEmpty() ? KOSONG.intensitas : List.copyOf(steps));
		}

		private static Map<Character, Float> koef(JsonObject o, Map<Character, Float> cadangan) {
			if (o.isEmpty()) {
				return cadangan;
			}
			Map<Character, Float> out = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (!e.getKey().isEmpty() && e.getValue().isJsonPrimitive()) {
					out.put(e.getKey().charAt(0), e.getValue().getAsFloat());
				}
			}
			return out;
		}
	}

	// ================================================================== state math

	/** The resting point (P0, A0, D0) of a personality and a long mood -100..100 (42.5A.3 formulas). */
	public static float[] dasar(Kepribadian.Sifat s, int suasana) {
		Tabel t = CakapReload.emosi();
		float p = sum(t.tp, s) / t.bagi;
		float a = sum(t.ta, s) / t.bagi;
		float d = sum(t.td, s) / t.bagi;
		p = jepit(p, t.jepit) + suasana / t.suasanaBagi;
		return new float[] {jepit(p, 1.0F), jepit(a, t.jepit), jepit(d, t.jepit)};
	}

	private static float sum(Map<Character, Float> koef, Kepribadian.Sifat s) {
		float v = 0.0F;
		for (Map.Entry<Character, Float> e : koef.entrySet()) {
			v += e.getValue() * s.get(e.getKey());
		}
		return v;
	}

	/** Half-life in ticks: {@code jam * (1 + C/200 - E/300)} world hours, at least a quarter hour. */
	public static long paruh(Kepribadian.Sifat s) {
		Tabel t = CakapReload.emosi();
		double jam = t.paruhJam * (1.0D + s.c() / t.paruhC + s.e() / t.paruhE);
		return Math.max(JAM / 4L, Math.round(jam * JAM));
	}

	/** The current PAD of a stored state (lazy relaxation toward the resting point). */
	public static float[] kini(Pengetahuan.Entri e, Kepribadian.Sifat s, int suasana, long now) {
		float[] v0 = dasar(s, suasana);
		if (!e.adaPad) {
			return v0;
		}
		double k = Math.pow(2.0D, -Math.max(0L, now - e.padTick) / (double) paruh(s));
		return new float[] {(float) (v0[0] + (e.p - v0[0]) * k), (float) (v0[1] + (e.a - v0[1]) * k), (float) (v0[2] + (e.d - v0[2]) * k)};
	}

	/**
	 * Applies a push: amplitude {@code intensitas * (1 + E/100)}; the dominance of a {@link Peristiwa#dRendah} row flips while
	 * D is negative. Stores the new state at {@code now}.
	 */
	public static void dorong(Pengetahuan.Entri e, Kepribadian.Sifat s, int suasana, float dp, float da, float dd, @Nullable Float dRendah,
			float intensitas, long now) {
		float[] cur = kini(e, s, suasana, now);
		float amp = Math.max(0.0F, intensitas) * (1.0F + s.e() / 100.0F);
		float pushD = dRendah != null && cur[2] < 0.0F ? dRendah : dd;
		e.p = jepit(cur[0] + dp * amp, 1.0F);
		e.a = jepit(cur[1] + da * amp, 1.0F);
		e.d = jepit(cur[2] + pushD * amp, 1.0F);
		e.padTick = now;
		e.adaPad = true;
	}

	/** The octant label of a PAD point ({@link Label#NETRAL} near the origin or when the table has no labels). */
	public static Label label(float[] pad) {
		if (Math.abs(pad[0]) < NETRAL && Math.abs(pad[1]) < NETRAL && Math.abs(pad[2]) < NETRAL) {
			return Label.NETRAL;
		}
		int sp = pad[0] >= 0 ? 1 : -1;
		int sa = pad[1] >= 0 ? 1 : -1;
		int sd = pad[2] >= 0 ? 1 : -1;
		for (Label l : CakapReload.emosi().label) {
			if (l.sp == sp && l.sa == sa && l.sd == sd) {
				return l;
			}
		}
		return Label.NETRAL;
	}

	/** The intensity word id ({@code agak/sangat/bukan_main}) of a PAD point: its largest axis. */
	public static String intensitas(float[] pad) {
		float m = Math.max(Math.abs(pad[0]), Math.max(Math.abs(pad[1]), Math.abs(pad[2])));
		String kunci = "agak";
		for (Intensitas i : CakapReload.emosi().intensitas) {
			if (m >= i.min) {
				kunci = i.kunci;
			}
		}
		return kunci;
	}

	static float jepit(float v, float m) {
		return Math.max(-m, Math.min(m, v));
	}

	private Emosi() {
	}
}

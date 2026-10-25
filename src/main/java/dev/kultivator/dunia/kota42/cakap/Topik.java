package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.KodeWajah;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conversation topics of {@code percakapan/topik.json} (DESIGN 42.5A.5) and the shared {@code syarat} matcher of every
 * 42K data file ({@link #cocok}): a condition object holds context axes (role, group, life stage, sex, temperament,
 * habits, traits, hour, weather, season, event, face, emotion label, impression, known name, memories, bonds, place,
 * dialect, register, fact, realm gap); present keys must all hold (AND), values inside one array are OR.
 * The context itself is a {@link Situasi}: everything a speaker "feels" right now, built once per turn.
 */
public final class Topik {

	/**
	 * One topic.
	 *
	 * @param id       topic id
	 * @param golongan trait multiplier groups ({@code sosial konflik khawatir gosip tanya})
	 * @param valensi  emotional colour -3..3 (feeds {@code cocokEmosi})
	 * @param syarat   context condition ({@link #cocok})
	 * @param minat    kelompok/peran/kebiasaan ids that find it interesting
	 * @param lanjut   follow-up topics (+{@code lanjut} weight next turn)
	 * @param tabu     taboo ids ({@code duka_di_pesta gosip_saat_kerja rahasia_ke_asing jelek_pejabat_takut})
	 * @param niat     intents allowed on this topic
	 */
	public record Data(String id, Set<String> golongan, int valensi, JsonObject syarat, List<String> minat, List<String> lanjut,
			List<String> tabu, List<String> niat) {
	}

	/** The table of {@code topik.json}: utility weights and topics in file order. */
	public record Tabel(Map<String, Double> bobot, Map<String, Data> topik) {
		public static final Tabel KOSONG = new Tabel(Map.of(), Map.of());

		public double w(String key, double cadangan) {
			return bobot.getOrDefault(key, cadangan);
		}

		static Tabel parse(JsonObject json) {
			Map<String, Double> w = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : CakapReload.objek(json, "bobot").entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					w.put(e.getKey(), e.getValue().getAsDouble());
				}
			}
			Map<String, Data> list = new LinkedHashMap<>();
			for (JsonObject o : CakapReload.objekList(json, "topik")) {
				String id = CakapReload.teks(o, "id", "");
				if (!id.isEmpty()) {
					list.put(id, new Data(id, new LinkedHashSet<>(CakapReload.teksList(o, "golongan")), CakapReload.angka(o, "valensi", 0),
							CakapReload.objek(o, "syarat"), CakapReload.teksList(o, "minat"), CakapReload.teksList(o, "lanjut"),
							CakapReload.teksList(o, "tabu"), CakapReload.teksList(o, "niat")));
				}
			}
			return new Tabel(Map.copyOf(w), java.util.Collections.unmodifiableMap(list));
		}
	}

	// ================================================================== the context

	/**
	 * What a speaker feels and sees when it speaks (one snapshot per turn).
	 *
	 * @param pembicara   the speaking warga
	 * @param sifat       its personality
	 * @param pad         current PAD
	 * @param label       emotion label id
	 * @param wajah       face code id
	 * @param peran       role id (lower case)
	 * @param kelompok    schedule group id
	 * @param tahap       life stage ({@code anak remaja dewasa tua})
	 * @param kelamin     {@code l}/{@code p}
	 * @param watak       temperament id
	 * @param kebiasaan   habit ids
	 * @param jam         hour bucket ({@code subuh pagi siang sore malam larut})
	 * @param cuaca       weather id
	 * @param musim       season id
	 * @param acara       active event ids at the place
	 * @param kesan       impression bucket toward the listener player ({@code musuh asing kenal akrab sahabat}), "" for NPC-NPC
	 * @param nilaiKesan  raw impression -100..100 (0 without a player)
	 * @param tahuNama    knows the listener's name
	 * @param kenangan    strong memories of the listener (Kenangan ids)
	 * @param hubungan    bond kinds the speaker has (and toward the NPC listener: {@code ke_<jenis>})
	 * @param tempat      place kind tail ({@code kota_besar}, {@code tema_air}, {@code kampung}...)
	 * @param dialek      dialect id
	 * @param register    {@code sopan biasa kasar kuno}
	 * @param ranahSelisih listener realm minus speaker realm (0 for NPC-NPC)
	 * @param fakta       predicate id of the fact in focus, null for none
	 * @param bekerja     at its own work spot by schedule now
	 * @param situs       site id
	 * @param hari        world day
	 * @param now         game time
	 */
	public record Situasi(Warga pembicara, Kepribadian.Sifat sifat, float[] pad, String label, String wajah, String peran, String kelompok,
			String tahap, String kelamin, String watak, List<String> kebiasaan, String jam, String cuaca, String musim, Set<String> acara,
			String kesan, int nilaiKesan, boolean tahuNama, Set<String> kenangan, Set<String> hubungan, String tempat, String dialek,
			String register, int ranahSelisih, @Nullable String fakta, boolean bekerja, long situs, long hari, long now) {

		/** The same snapshot focused on another fact. */
		public Situasi denganFakta(@Nullable String predikat) {
			return new Situasi(pembicara, sifat, pad, label, wajah, peran, kelompok, tahap, kelamin, watak, kebiasaan, jam, cuaca, musim, acara,
					kesan, nilaiKesan, tahuNama, kenangan, hubungan, tempat, dialek, register, ranahSelisih, predikat, bekerja, situs, hari, now);
		}

		public float p() {
			return pad[0];
		}

		public float a() {
			return pad[1];
		}

		public float d() {
			return pad[2];
		}

		/**
		 * Builds the snapshot of {@code w} speaking now at {@code situs} to a player ({@code pendengar}) or another warga
		 * ({@code lawan}); both null for a monologue.
		 */
		public static Situasi bangun(MinecraftServer server, Warga w, @Nullable ServerPlayer pendengar, @Nullable Warga lawan, long situs,
				@Nullable String fakta) {
			Pengetahuan data = Pengetahuan.get(server);
			Pengetahuan.Entri e = data.entri(w);
			Kepribadian.Sifat sifat = data.sifat(w);
			long now = server.overworld().getGameTime();
			long hari = KotaHidup.hari(server);
			KodeWajah kode = WargaApi.kodeWajah(w, now);
			float[] pad = Emosi.kini(e, sifat, w.suasana, now);
			if (kode == KodeWajah.MABUK) {
				sifat = sifat.geser('C', -30);
			}
			Emosi.Label label = Emosi.label(pad);
			NpcRole role = w.peranNpc();
			WargaInfo.Tahap tahap = WargaInfo.Tahap.dari(WargaApi.umur(w));
			List<String> habits = new ArrayList<>(2);
			for (int idx : new int[] {w.kebiasaan1, w.kebiasaan2}) {
				String id = Kepribadian.kebiasaan(idx);
				if (!id.isEmpty()) {
					habits.add(id);
				}
			}
			long tod = Kota.timeOfDay(server);
			int hour = (int) ((tod / 1000L + 6L) % 24L);
			ServerLevel level = server.overworld();
			Set<String> acara = new LinkedHashSet<>();
			String fest = KotaBesarApi.acaraAktif(situs);
			if (fest != null) {
				acara.add(fest.replace('.', '_'));
			}
			SkyKind sky = DuniaApi.sky().active(level);
			String cuaca = level.isThundering() ? "badai" : level.isRaining() ? "hujan" : "cerah";
			if (sky != null) {
				acara.add("langit_" + sky.id());
				String id = sky.id();
				if (id.contains("eclipse")) {
					cuaca = "gerhana";
				} else if (id.contains("meteor")) {
					cuaca = "meteor";
				} else if (id.contains("mist") || id.contains("kabut") || id.contains("fog")) {
					cuaca = "kabut_qi";
				}
			}
			String[] seasons = {"semi", "panas", "gugur", "dingin"};
			String musim = seasons[(int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L)];
			String kesan = "";
			int nilai = 0;
			boolean nama = false;
			Set<String> kenangan = new LinkedHashSet<>();
			int ranah = 0;
			if (pendengar != null) {
				Ingatan ing = WargaApi.ingatan(w, pendengar.getUUID());
				nilai = ing.kesan;
				nama = ing.nama;
				kesan = ing.kenal <= 0 ? "asing" : nilai <= -40 ? "musuh" : nilai >= 60 ? "sahabat" : nilai >= 30 ? "akrab" : "kenal";
				for (int i = 0; i < Ingatan.PERISTIWA; i++) {
					Kenangan k = ing.kenangan(i);
					if (k != null) {
						kenangan.add(k.name());
					}
				}
				ranah = Cultivators.get(pendengar).realm().ordinal() - w.ranah;
			} else if (lawan != null) {
				ranah = lawan.ranah - w.ranah;
			}
			Set<String> bonds = new LinkedHashSet<>();
			for (long b : w.hubungan) {
				JenisHubungan j = JenisHubungan.jenis(b);
				if (j != null) {
					bonds.add(j.id());
					if (lawan != null && JenisHubungan.idLain(b) == lawan.id) {
						bonds.add("ke_" + j.id());
					}
				}
			}
			if (w.pasangan != Warga.TANPA) {
				bonds.add("pasangan");
			}
			if (w.anak.length > 0) {
				bonds.add("anak");
			}
			if (lawan != null && (lawan.id == w.ayah || lawan.id == w.ibu || lawan.ayah == w.id || lawan.ibu == w.id || lawan.id == w.pasangan
					|| (w.rumahTangga != 0 && lawan.rumahTangga == w.rumahTangga))) {
				bonds.add("ke_keluarga");
			}
			String tempat = Topik.tempat(server, situs);
			String dialek = Topik.dialek(tempat);
			Situasi draft = new Situasi(w, sifat, pad, label.id(), kode.id(), role.id(), role.kelompok.id(), tahapId(tahap),
					w.pria() ? "l" : "p", Kepribadian.watak(w), List.copyOf(habits), jamId(hour), cuaca, musim, Set.copyOf(acara), kesan, nilai, nama,
					Set.copyOf(kenangan), Set.copyOf(bonds), tempat, dialek, "biasa", ranah, fakta, false, situs, hari, now);
			return draft.denganRegister(Topik.register(draft, lawan));
		}

		private Situasi denganRegister(String reg) {
			return new Situasi(pembicara, sifat, pad, label, wajah, peran, kelompok, tahap, kelamin, watak, kebiasaan, jam, cuaca, musim, acara,
					kesan, nilaiKesan, tahuNama, kenangan, hubungan, tempat, dialek, reg, ranahSelisih, fakta, bekerja, situs, hari, now);
		}
	}

	/** The four syarat stages of a life stage ({@code anak remaja dewasa tua}). */
	public static String tahapId(WargaInfo.Tahap t) {
		return switch (t) {
			case BAYI, ANAK -> "anak";
			case REMAJA -> "remaja";
			case MUDA, DEWASA -> "dewasa";
			default -> "tua";
		};
	}

	/** Hour buckets of 42.5.2. */
	public static String jamId(int hour) {
		if (hour >= 4 && hour < 6) {
			return "subuh";
		}
		if (hour >= 6 && hour < 11) {
			return "pagi";
		}
		if (hour >= 11 && hour < 15) {
			return "siang";
		}
		if (hour >= 15 && hour < 18) {
			return "sore";
		}
		return hour >= 18 && hour < 22 ? "malam" : "larut";
	}

	/** The place kind tail of a site ({@code kota_besar}, {@code tema_pelabuhan}...), "" when unknown. */
	public static String tempat(MinecraftServer server, long situs) {
		DuniaSites.Site site = situs == 0L ? null : DuniaSites.byId(server, situs);
		if (site == null) {
			return "";
		}
		String kind = site.kind();
		int slash = kind.lastIndexOf('/');
		return slash >= 0 ? kind.substring(slash + 1) : kind;
	}

	/** The dialect of a place kind from {@code dialek.json} ({@code tempat} map, else {@code bawaan}). */
	public static String dialek(String tempat) {
		JsonObject json = CakapReload.mentah(CakapData.DIALEK);
		String bawaan = CakapReload.teks(json, "bawaan", "kampung");
		if (!KotaConfig.bicara().dialek) {
			return bawaan;
		}
		for (Map.Entry<String, JsonElement> e : CakapReload.objek(json, "tempat").entrySet()) {
			if (!tempat.isEmpty() && tempat.contains(e.getKey()) && e.getValue().isJsonPrimitive()) {
				return e.getValue().getAsString();
			}
		}
		return bawaan;
	}

	/**
	 * Register (42.5A.8): {@code kuno} for cultivators, monks and anyone facing a listener >= 3 realms up; {@code kasar}
	 * toward enemies, from drunks at night, in the demon city and from the very disagreeable; {@code sopan} from traders,
	 * the conscientious, to strangers and to officials; else {@code biasa}.
	 */
	static String register(Situasi s, @Nullable Warga lawan) {
		boolean kuno = KotaConfig.bicara().registerKuno;
		if (kuno && (s.kelompok.equals("kultivator") || s.kelompok.equals("rahib") || s.ranahSelisih >= 3)) {
			return "kuno";
		}
		if (s.kesan.equals("musuh") || s.hubungan.contains("ke_musuh") || s.wajah.equals("mabuk") || s.dialek.equals("iblis")
				|| s.sifat.a() < -40) {
			return "kasar";
		}
		if (s.kelompok.equals("pedagang") || s.kesan.equals("asing") || s.sifat.c() > 40
				|| (lawan != null && lawan.peranNpc().kelompok == NpcRole.Kelompok.PEJABAT)) {
			return "sopan";
		}
		return "biasa";
	}

	// ================================================================== the matcher

	/**
	 * Evaluates a {@code syarat} object: -1 when any present key fails, else the number of keys that matched (specificity
	 * bonus of 42.5.2). Unknown keys are ignored (other files add their own, e.g. chat steps).
	 */
	public static int cocok(@Nullable JsonObject syarat, Situasi s) {
		if (syarat == null || syarat.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (Map.Entry<String, JsonElement> e : syarat.entrySet()) {
			String key = e.getKey();
			if (key.startsWith("_")) {
				continue;
			}
			JsonElement v = e.getValue();
			Boolean ok = switch (key) {
				case "peran" -> ada(v, s.peran);
				case "kelompok" -> ada(v, s.kelompok);
				case "tahap" -> ada(v, s.tahap);
				case "kelamin" -> ada(v, s.kelamin);
				case "watak" -> ada(v, s.watak);
				case "kebiasaan" -> adaSalah(v, s.kebiasaan);
				case "sifat" -> sifat(v, s.sifat);
				case "jam" -> ada(v, s.jam);
				case "cuaca" -> ada(v, s.cuaca);
				case "musim" -> ada(v, s.musim);
				case "acara" -> adaSalah(v, s.acara);
				case "wajah" -> ada(v, s.wajah);
				case "label_emosi" -> ada(v, s.label);
				case "kesan" -> ada(v, s.kesan);
				case "tahu_nama" -> v.isJsonPrimitive() && v.getAsBoolean() == s.tahuNama;
				case "kenangan" -> adaSalah(v, s.kenangan);
				case "hubungan" -> adaSalah(v, s.hubungan);
				case "tempat" -> ada(v, s.tempat);
				case "dialek" -> ada(v, s.dialek);
				case "register" -> ada(v, s.register);
				case "fakta" -> s.fakta != null && ada(v, s.fakta);
				case "ranah_selisih" -> v.isJsonPrimitive() && banding(v.getAsString(), s.ranahSelisih);
				default -> null;
			};
			if (ok == null) {
				continue;
			}
			if (!ok) {
				return -1;
			}
			n++;
		}
		return n;
	}

	/** {@code ">60"}, {@code "<-40"}, {@code ">=3"}, {@code "<=0"}, {@code "=2"} or a bare number (equality). */
	public static boolean banding(String expr, double value) {
		String t = expr.trim();
		try {
			if (t.startsWith(">=")) {
				return value >= Double.parseDouble(t.substring(2));
			}
			if (t.startsWith("<=")) {
				return value <= Double.parseDouble(t.substring(2));
			}
			if (t.startsWith(">")) {
				return value > Double.parseDouble(t.substring(1));
			}
			if (t.startsWith("<")) {
				return value < Double.parseDouble(t.substring(1));
			}
			if (t.startsWith("=")) {
				return value == Double.parseDouble(t.substring(1));
			}
			return value == Double.parseDouble(t);
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private static boolean sifat(JsonElement v, Kepribadian.Sifat s) {
		if (!v.isJsonObject()) {
			return true;
		}
		for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
			if (e.getKey().isEmpty() || !e.getValue().isJsonPrimitive() || !banding(e.getValue().getAsString(), s.get(e.getKey().charAt(0)))) {
				return false;
			}
		}
		return true;
	}

	/** True when {@code value} equals the primitive or is one of the array (case-insensitive). */
	static boolean ada(JsonElement v, String value) {
		if (v.isJsonArray()) {
			for (JsonElement e : v.getAsJsonArray()) {
				if (e.isJsonPrimitive() && e.getAsString().equalsIgnoreCase(value)) {
					return true;
				}
			}
			return v.getAsJsonArray().isEmpty();
		}
		return v.isJsonPrimitive() && v.getAsString().equalsIgnoreCase(value);
	}

	private static boolean adaSalah(JsonElement v, java.util.Collection<String> values) {
		if (v.isJsonArray() && v.getAsJsonArray().isEmpty()) {
			return true;
		}
		for (String value : values) {
			if (ada(v, value)) {
				return true;
			}
		}
		return false;
	}

	private Topik() {
	}
}

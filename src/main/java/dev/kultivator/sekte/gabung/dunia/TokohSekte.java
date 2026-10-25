package dev.kultivator.sekte.gabung.dunia;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.diplomasi.GeneratorNama;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The named elders and master of every NPC sect (DESIGN 41.6.12, contract C41.W1, owner 41W), SavedData
 * {@code kultivator:gabung_tokoh}. Seniors and rivals are NOT here: they are the 39F roster
 * ({@code sekte.mandiri.seteru.Roster}, read through its public readers; never import that package's own
 * {@code TokohSekte} next to this class, use its full name).
 * <ul>
 *   <li><b>Elders</b> (id {@code <s>#t<n>}): the class's elder seats (3-7, {@link KelasSekte#kursiTetua}) with an office of
 *   {@code sekte.api.Jabatan} plus 41's own office {@link #PENERIMAAN}. Seat order is {@link #URUTAN}: Agung, Hukum, Luar,
 *   Ajar (Paviliun Jasa), Obat, Pusaka, Penerimaan; a small sect leaves the last offices to an elder who also holds them
 *   ({@link #rangkap}: admissions to the Outer Elder, the treasury to the Grand Elder, medicine to the Pavilion Elder).
 *   Name {@code GeneratorNama.member(lambangSeed, 100 + n)} (unique in the sect) with the office title ("Tetua Hukum Qin
 *   Zhenyuan"), an old body (50-90), realm {@link KelasSekte#ranahTetua} of the sect's tier now, Dao (the corak Dao 60 %),
 *   two {@link Sifat} of different axes (the first by office, the second by the sect's path), attitude per player UUID,
 *   wounds and a {@link Status}.</li>
 *   <li><b>Master</b> (id {@code <s>#k}): {@code GerbangPlan.Entry.master()} (fixed name), realm
 *   {@link KelasSekte#ranahKetua}; replaced only through {@link #gantiKetua} (succession 41.8.5, a lost war of 41D: name
 *   {@code GeneratorNama.member(seed, 200 + generation)}) or set aside by a player master ({@link #pensiunkanKetua}).</li>
 * </ul>
 * Named characters never die (41.0 butir 5): beaten = wounded ({@link #lukai}), absent until the day noted. Everything is
 * built lazily on first read of a sect, never on a tick. The same SavedData also remembers where the gate titik of 41
 * ({@code sektenpc/*}, {@link TitikGabung}) stood once their chunk was loaded. Server thread only.
 */
public final class TokohSekte {

	/** 41's own office id (not in {@code Jabatan}): the admissions elder of the interview (41.5.4 stage 3). */
	public static final String PENERIMAAN = "penerimaan";
	/** Office id of the master (schedules, titles, relations). */
	public static final String KETUA = "ketua";
	/** Attitude bounds (same scale as the 39F roster). */
	public static final int SIKAP_MIN = -100;
	public static final int SIKAP_MAKS = 100;
	/** Players remembered per character (the oldest attitude is dropped beyond this). */
	public static final int SIKAP_INGAT = 32;
	/** Offices in seat order; seat {@code n} holds {@code URUTAN[n]}, the 7th seat exists only in an Abadi sect. */
	public static final List<String> URUTAN = List.of("agung", "hukum", "luar", "ajar", "obat", "pusaka", PENERIMAAN);
	/** Given names read as female (the {@link GeneratorNama#NAMA} list has no grammatical sex). */
	private static final Set<String> NAMA_PEREMPUAN = Set.of("Mingyue", "Feixue", "Qingxuan", "Huaiyu", "Xuanji", "Lingfeng");

	/** Life state of a named character. */
	public enum Status {
		/** In office. */
		AKTIF,
		/** Retired (the old master after a succession: "Leluhur Hidup"). */
		PENSIUN,
		/** The seat went to a player; the character lives on without office. */
		DIGANTI_PEMAIN;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static Status byId(String id) {
			for (Status s : values()) {
				if (s.id().equals(id)) {
					return s;
				}
			}
			return AKTIF;
		}
	}

	/**
	 * One named elder or master. {@code jabatan} null for the master and for the admissions office (then
	 * {@code penerimaan} is true; an Outer Elder who also does admissions has both). {@code lukaSampai} is the world day the
	 * character is back from a wound (0 = well).
	 */
	public record Tokoh(String id, String sekte, String nama, @Nullable Jabatan jabatan, boolean penerimaan, boolean ketua,
			Realm ranah, Dao dao, List<Sifat> sifat, boolean perempuan, int usia, Status status, long lukaSampai) {

		public Tokoh {
			sifat = sifat == null ? List.of() : List.copyOf(sifat);
		}

		/** True on world day {@code hari} when the character is wounded (no body, no lessons, no duel). */
		public boolean terluka(long hari) {
			return lukaSampai > hari;
		}

		/** The office id ({@code agung} .. {@code pusaka}, {@link #PENERIMAAN}, {@link #KETUA}). */
		public String kantor() {
			return ketua ? KETUA : jabatan != null ? jabatan.id() : PENERIMAAN;
		}

		/** True when the character has trait {@code s}. */
		public boolean punya(Sifat s) {
			return sifat.contains(s);
		}

		/** "Tetua Hukum Qin Zhenyuan" / "Ketua Mo Tianhe". */
		public MutableComponent namaGelar() {
			return Component.translatable("kultivator.gabung.tokoh.gelar." + kantor(), nama);
		}

		/** The motto line of the office and the sect's path (shown on cards and in the elder's first words). */
		public String kunciSemboyan(int aliran) {
			return "kultivator.gabung.tokoh.semboyan." + kantor() + "." + Math.max(0, Math.min(2, aliran));
		}

		/** The temper line of the first trait ("keras pada aturan, lembut pada murid"). */
		public String kunciWatak() {
			return "kultivator.gabung.tokoh.watak." + (sifat.isEmpty() ? Sifat.TENANG : sifat.get(0)).id();
		}

		/** The teacher's promise of the Shifu offer (41.5.7), by the character's Dao. */
		public String kunciJanji() {
			return "kultivator.gabung.tokoh.janji." + dao.id();
		}
	}

	// ================================================================== ids (pure)

	/** Id of elder seat {@code n} (0-based) of {@code sekte}: {@code s3#t2}. */
	public static String idTetua(String sekte, int n) {
		return sekte + "#t" + n;
	}

	/** Id of the master of {@code sekte}: {@code s3#k}. */
	public static String idKetua(String sekte) {
		return sekte + "#k";
	}

	/** True for an id of this class (elder or master), false for 39F roster ids ({@code s3#4}) and anything else. */
	public static boolean idSah(@Nullable String id) {
		if (id == null) {
			return false;
		}
		int hash = id.indexOf('#');
		return hash > 1 && id.charAt(0) == 's' && hash + 1 < id.length() && (id.charAt(hash + 1) == 't' || id.charAt(hash + 1) == 'k');
	}

	/** The sect id of a tokoh id ({@code s3#t2} -&gt; {@code s3}), or null. */
	public static @Nullable String sekteDari(@Nullable String id) {
		int hash = id == null ? -1 : id.indexOf('#');
		return hash > 0 ? id.substring(0, hash) : null;
	}

	/**
	 * The office that does the work of {@code kantor} when the sect has no seat for it, or null for the first three
	 * (always seated): admissions -&gt; Outer Elder, treasury -&gt; Grand Elder, medicine -&gt; Pavilion Elder, pavilion -&gt;
	 * Grand Elder.
	 */
	public static @Nullable String rangkap(String kantor) {
		return switch (kantor) {
			case PENERIMAAN -> "luar";
			case "pusaka", "ajar" -> "agung";
			case "obat" -> "ajar";
			default -> null;
		};
	}

	// ================================================================== reads (contract C41.W1)

	/** The elders of {@code sekte} in seat order (only the seats of its class now; retired and replaced ones left out). */
	public static List<Tokoh> tetua(MinecraftServer server, String sekte) {
		Data data = Data.get(server);
		List<Catatan> list = data.pastikan(server, sekte);
		int kursi = kursi(sekte);
		List<Tokoh> out = new ArrayList<>();
		for (int i = 0; i < list.size() && i < kursi; i++) {
			Catatan c = list.get(i);
			if (c.status == Status.AKTIF) {
				out.add(c.rekam(sekte));
			}
		}
		return out;
	}

	/** Every elder ever seated in {@code sekte}, retired and replaced ones too (tests, the politics screen). */
	public static List<Tokoh> semuaTetua(MinecraftServer server, String sekte) {
		List<Tokoh> out = new ArrayList<>();
		for (Catatan c : Data.get(server).pastikan(server, sekte)) {
			out.add(c.rekam(sekte));
		}
		return out;
	}

	/**
	 * The elder doing the work of {@code jabatan} in {@code sekte}: its own seat holder, else the elder who holds it too
	 * ({@link #rangkap}); null when nobody active is left (unknown sect, every seat taken by players).
	 */
	public static @Nullable Tokoh jabatan(MinecraftServer server, String sekte, Jabatan jabatan) {
		return jabatan == null ? null : kantor(server, sekte, jabatan.id());
	}

	/** The admissions elder ({@link #PENERIMAAN}) of {@code sekte}: its own seat in an Abadi sect, else the Outer Elder. */
	public static @Nullable Tokoh penerimaan(MinecraftServer server, String sekte) {
		return kantor(server, sekte, PENERIMAAN);
	}

	/** The character doing office {@code kantor} ({@code agung} .. {@link #PENERIMAAN}, {@link #KETUA}), or null. */
	public static @Nullable Tokoh kantor(MinecraftServer server, String sekte, String kantor) {
		if (server == null || sekte == null || kantor == null) {
			return null;
		}
		if (KETUA.equals(kantor)) {
			return ketua(server, sekte);
		}
		List<Tokoh> seated = tetua(server, sekte);
		String want = kantor;
		for (int guard = 0; want != null && guard < 4; guard++) {
			for (Tokoh t : seated) {
				if (t.kantor().equals(want)) {
					return t;
				}
			}
			want = rangkap(want);
		}
		return seated.isEmpty() ? null : seated.get(0);
	}

	/** The NPC master of {@code sekte}, or null (unknown sect, or a player is master). */
	public static @Nullable Tokoh ketua(MinecraftServer server, String sekte) {
		Data data = Data.get(server);
		data.pastikan(server, sekte);
		Catatan c = data.ketua.get(sekte);
		return c == null || c.status != Status.AKTIF ? null : c.rekam(sekte);
	}

	/** The character with {@code id} ({@code s3#t2}, {@code s3#k}), or null. */
	public static @Nullable Tokoh tokoh(MinecraftServer server, String id) {
		Catatan c = catatan(server, id);
		return c == null ? null : c.rekam(sekteDari(id));
	}

	/** "Tetua Hukum Qin Zhenyuan" for a tokoh id, else null (roster ids are named by the 39F roster). */
	public static @Nullable MutableComponent nama(MinecraftServer server, @Nullable String id) {
		Tokoh t = id == null || !idSah(id) ? null : tokoh(server, id);
		return t == null ? null : t.namaGelar();
	}

	/** The character's attitude toward player {@code pemain} (0 when never met). */
	public static int sikap(MinecraftServer server, String id, UUID pemain) {
		Catatan c = catatan(server, id);
		return c == null || pemain == null ? 0 : c.sikap.getOrDefault(pemain, 0);
	}

	/** Changes the character's attitude toward {@code pemain} by {@code delta} (clamped); returns the new value. */
	public static int ubahSikap(MinecraftServer server, String id, UUID pemain, int delta) {
		Catatan c = catatan(server, id);
		if (c == null || pemain == null) {
			return Math.max(SIKAP_MIN, Math.min(SIKAP_MAKS, delta));
		}
		int now = Math.max(SIKAP_MIN, Math.min(SIKAP_MAKS, c.sikap.getOrDefault(pemain, 0) + delta));
		c.sikap.remove(pemain);
		c.sikap.put(pemain, now);
		while (c.sikap.size() > SIKAP_INGAT) {
			c.sikap.remove(c.sikap.keySet().iterator().next());
		}
		Data.get(server).setDirty();
		return now;
	}

	/** The look seed of the character's body (same face, sex and voice every time it is spawned). */
	public static int look(MinecraftServer server, String id) {
		Catatan c = catatan(server, id);
		return c == null ? id.hashCode() : c.look;
	}

	// ================================================================== changes (41B-41D call these)

	/** Wounds the character until world day {@code sampai} (a lost duel, a war): no body, no lessons until then. */
	public static void lukai(MinecraftServer server, String id, long sampai) {
		Catatan c = catatan(server, id);
		if (c != null && sampai > c.lukaSampai) {
			c.lukaSampai = sampai;
			Data.get(server).setDirty();
		}
	}

	/** Sets the character's life state (a player won the seat: {@link Status#DIGANTI_PEMAIN}; seat back: AKTIF). */
	public static void setStatus(MinecraftServer server, String id, Status status) {
		Catatan c = catatan(server, id);
		if (c != null && status != null && c.status != status) {
			c.status = status;
			Data.get(server).setDirty();
		}
	}

	/** A player became master: the NPC master retires as "Leluhur Hidup" (41.8.5); returns the retired id or null. */
	public static @Nullable String pensiunkanKetua(MinecraftServer server, String sekte) {
		Data data = Data.get(server);
		data.pastikan(server, sekte);
		Catatan c = data.ketua.get(sekte);
		if (c == null || c.status != Status.AKTIF) {
			return null;
		}
		c.status = Status.PENSIUN;
		data.setDirty();
		return c.id;
	}

	/**
	 * A new NPC master for {@code sekte} (succession won by an NPC, a lost war of 41D, the player master stepping down): the
	 * old one retires, the new one gets the next generation's name. Returns the new master.
	 */
	public static @Nullable Tokoh gantiKetua(MinecraftServer server, String sekte) {
		Data data = Data.get(server);
		data.pastikan(server, sekte);
		Catatan lama = data.ketua.get(sekte);
		GerbangPlan.Entry e = entry(server, sekte);
		if (e == null) {
			return null;
		}
		int generasi = lama == null ? 1 : lama.generasi + 1;
		if (lama != null) {
			data.pensiun.computeIfAbsent(sekte, k -> new ArrayList<>()).add(lama);
			lama.status = Status.PENSIUN;
		}
		Catatan baru = Catatan.ketua(e, generasi, data.namaDipakai(sekte));
		data.ketua.put(sekte, baru);
		data.setDirty();
		return baru.rekam(sekte);
	}

	/** The retired masters of {@code sekte} ("Leluhur Hidup"), oldest first. */
	public static List<Tokoh> leluhur(MinecraftServer server, String sekte) {
		List<Tokoh> out = new ArrayList<>();
		for (Catatan c : Data.get(server).pensiun.getOrDefault(sekte, List.of())) {
			out.add(c.rekam(sekte));
		}
		return out;
	}

	// ================================================================== gate titik store (TitikGabung)

	/** The remembered position of gate titik {@code nama} ({@code sektenpc/loket}) of {@code sekte}, or null. */
	static @Nullable BlockPos titikGerbang(MinecraftServer server, String sekte, String nama) {
		Map<String, Long> map = Data.get(server).gerbang.get(sekte);
		Long packed = map == null ? null : map.get(nama);
		return packed == null ? null : BlockPos.of(packed);
	}

	/** Remembers gate titik {@code nama} of {@code sekte} at {@code pos}; true when it was new or moved. */
	static boolean simpanTitikGerbang(MinecraftServer server, String sekte, String nama, BlockPos pos) {
		Data data = Data.get(server);
		Map<String, Long> map = data.gerbang.computeIfAbsent(sekte, k -> new LinkedHashMap<>());
		Long old = map.put(nama, pos.asLong());
		if (old == null || old != pos.asLong()) {
			data.setDirty();
			return true;
		}
		return false;
	}

	/** Every remembered gate titik of {@code sekte} (name -&gt; position), for {@code uji gabung titik}. */
	static Map<String, BlockPos> semuaTitikGerbang(MinecraftServer server, String sekte) {
		Map<String, BlockPos> out = new LinkedHashMap<>();
		Data.get(server).gerbang.getOrDefault(sekte, Map.of()).forEach((k, v) -> out.put(k, BlockPos.of(v)));
		return out;
	}

	// ================================================================== internals

	private static int kursi(String sekte) {
		return KelasSekte.dariTier(Math.max(1, SekteApi.diplomasi().tier(sekte))).kursiTetua();
	}

	private static @Nullable GerbangPlan.Entry entry(MinecraftServer server, String sekte) {
		return server == null || sekte == null ? null : GerbangPlan.of(server.overworld().getSeed()).entry(sekte);
	}

	private static @Nullable Catatan catatan(MinecraftServer server, @Nullable String id) {
		String sekte = sekteDari(id);
		if (server == null || sekte == null || !idSah(id)) {
			return null;
		}
		Data data = Data.get(server);
		List<Catatan> list = data.pastikan(server, sekte);
		Catatan k = data.ketua.get(sekte);
		if (k != null && k.id.equals(id)) {
			return k;
		}
		for (Catatan c : list) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		for (Catatan c : data.pensiun.getOrDefault(sekte, List.of())) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return null;
	}

	/** splitmix64 finaliser: every derived trait is a pure function of (seed, sect, seat). */
	static long campur(long a, long b) {
		long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + 0x632BE59BD9B4E019L;
		h ^= h >>> 30;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 27;
		h *= 0x94D049BB133111EBL;
		return h ^ h >>> 31;
	}

	/** One stored character (mutable; read out as a {@link Tokoh}). */
	static final class Catatan {
		final String id;
		final String kantor;
		final String nama;
		final boolean perempuan;
		final int usia;
		final Dao dao;
		final List<Sifat> sifat;
		final int look;
		final int generasi;
		Status status = Status.AKTIF;
		long lukaSampai;
		final Map<UUID, Integer> sikap = new LinkedHashMap<>();

		Catatan(String id, String kantor, String nama, boolean perempuan, int usia, Dao dao, List<Sifat> sifat, int look, int generasi) {
			this.id = id;
			this.kantor = kantor;
			this.nama = nama;
			this.perempuan = perempuan;
			this.usia = usia;
			this.dao = dao;
			this.sifat = List.copyOf(sifat);
			this.look = look;
			this.generasi = generasi;
		}

		Tokoh rekam(@Nullable String sekte) {
			String s = sekte == null ? "" : sekte;
			int tier = Math.max(1, SekteApi.diplomasi().tier(s));
			boolean ketua = KETUA.equals(kantor);
			Realm ranah = ketua ? KelasSekte.ranahKetua(tier) : KelasSekte.ranahTetua(tier);
			Jabatan j = Jabatan.byId(kantor);
			boolean terima = PENERIMAAN.equals(kantor) || "luar".equals(kantor) && kursi(s) <= URUTAN.indexOf(PENERIMAAN);
			return new Tokoh(id, s, nama, j, terima, ketua, ranah, dao, sifat, perempuan, usia, status, lukaSampai);
		}

		/** Elder seat {@code n} of plan entry {@code e}, avoiding names already used in the sect. */
		static Catatan tetua(GerbangPlan.Entry e, int n, Set<String> dipakai) {
			String kantor = URUTAN.get(Math.min(n, URUTAN.size() - 1));
			String nama = GeneratorNama.member(e.lambangSeed(), 100 + n);
			for (int k = 1; dipakai.contains(nama) && k < 64; k++) {
				nama = GeneratorNama.member(e.lambangSeed(), 100 + n + 16 * k);
			}
			long h = campur(e.lambangSeed(), 1000L + n);
			boolean perempuan = NAMA_PEREMPUAN.contains(nama.substring(nama.indexOf(' ') + 1));
			int usia = 50 + (int) Math.floorMod(h >>> 8, 41L);
			Corak corak = Corak.of(e.kataA());
			Dao dao = Math.floorMod(h >>> 16, 100L) < 60 ? corak.daoUtama() : Dao.values()[(int) Math.floorMod(h >>> 24, (long) Dao.COUNT)];
			return new Catatan(TokohSekte.idTetua(e.id(), n), kantor, nama, perempuan, usia, dao, sifat(kantor, e.aliran(), h), (int) (h >>> 32),
					0);
		}

		/** The master of plan entry {@code e}: generation 0 is the plan's own master, later ones get new names. */
		static Catatan ketua(GerbangPlan.Entry e, int generasi, Set<String> dipakai) {
			String nama;
			if (generasi <= 0) {
				nama = GeneratorNama.MARGA[Math.floorMod(e.ketuaMarga(), GeneratorNama.MARGA.length)] + " "
						+ GeneratorNama.NAMA[Math.floorMod(e.ketuaNama(), GeneratorNama.NAMA.length)];
			} else {
				nama = GeneratorNama.member(e.lambangSeed(), 200 + generasi);
				for (int k = 1; dipakai.contains(nama) && k < 64; k++) {
					nama = GeneratorNama.member(e.lambangSeed(), 200 + generasi + 16 * k);
				}
			}
			long h = campur(e.lambangSeed(), 2000L + generasi);
			boolean perempuan = NAMA_PEREMPUAN.contains(nama.substring(nama.indexOf(' ') + 1));
			int usia = generasi <= 0 ? 90 + (int) Math.floorMod(h >>> 8, 71L) : 60 + (int) Math.floorMod(h >>> 8, 41L);
			Dao dao = Corak.of(e.kataA()).daoUtama();
			return new Catatan(TokohSekte.idKetua(e.id()), KETUA, nama, perempuan, usia, dao, sifat(KETUA, e.aliran(), h), (int) (h >>> 32),
					generasi);
		}

		/**
		 * Two traits of different axes: the first fits the office (the Discipline Elder is strict and loyal, the Pavilion
		 * Elder diligent or proud, the Medicine Elder kind or idle), the second leans on the sect's path (demonic: cunning,
		 * vengeful, proud, ambitious; righteous: loyal, brave, humble, kind).
		 */
		static List<Sifat> sifat(String kantor, int aliran, long h) {
			Sifat[] pertama = switch (kantor) {
				case "agung" -> new Sifat[]{Sifat.TENANG, Sifat.RENDAH_HATI};
				case "hukum" -> new Sifat[]{Sifat.SETIA, Sifat.TENANG};
				case "luar" -> new Sifat[]{Sifat.BERANI, Sifat.AMBISIUS};
				case "ajar" -> new Sifat[]{Sifat.RAJIN, Sifat.SOMBONG};
				case "obat" -> new Sifat[]{Sifat.PENYAYANG, Sifat.MALAS};
				case "pusaka" -> new Sifat[]{Sifat.LICIK, Sifat.RAJIN};
				case PENERIMAAN -> new Sifat[]{Sifat.PENYAYANG, Sifat.TENANG};
				default -> new Sifat[]{Sifat.TENANG, Sifat.AMBISIUS};
			};
			Sifat a = pertama[(int) Math.floorMod(h, 2L)];
			Sifat[] jalan = switch (aliran) {
				case 0 -> new Sifat[]{Sifat.SETIA, Sifat.BERANI, Sifat.RENDAH_HATI, Sifat.PENYAYANG, Sifat.RAJIN, Sifat.TENANG};
				case 2 -> new Sifat[]{Sifat.LICIK, Sifat.PENDENDAM, Sifat.SOMBONG, Sifat.AMBISIUS, Sifat.BERANI, Sifat.MALAS};
				default -> new Sifat[]{Sifat.AMBISIUS, Sifat.TENANG, Sifat.RAJIN, Sifat.LICIK, Sifat.SOMBONG, Sifat.SETIA};
			};
			int start = (int) Math.floorMod(h >>> 40, (long) jalan.length);
			for (int i = 0; i < jalan.length; i++) {
				Sifat b = jalan[(start + i) % jalan.length];
				if (b.axis() != a.axis()) {
					return List.of(a, b);
				}
			}
			return List.of(a);
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putString("id", id);
			t.putString("kantor", kantor);
			t.putString("nama", nama);
			t.putBoolean("p", perempuan);
			t.putInt("usia", usia);
			t.putString("dao", dao.id());
			ListTag s = new ListTag();
			for (Sifat f : sifat) {
				s.add(StringTag.valueOf(f.id()));
			}
			t.put("sifat", s);
			t.putInt("look", look);
			t.putInt("gen", generasi);
			t.putString("status", status.id());
			t.putLong("luka", lukaSampai);
			CompoundTag sk = new CompoundTag();
			sikap.forEach((uuid, v) -> sk.putInt(uuid.toString(), v));
			t.put("sikap", sk);
			return t;
		}

		static @Nullable Catatan muat(CompoundTag t) {
			String id = t.getStringOr("id", "");
			if (!idSah(id)) {
				return null;
			}
			List<Sifat> sifat = new ArrayList<>();
			for (int i = 0; i < t.getListOrEmpty("sifat").size(); i++) {
				Sifat f = Sifat.byId(t.getListOrEmpty("sifat").getStringOr(i, ""));
				if (f != null) {
					sifat.add(f);
				}
			}
			Dao dao = Dao.byId(t.getStringOr("dao", "sword"));
			Catatan c = new Catatan(id, t.getStringOr("kantor", "agung"), t.getStringOr("nama", "?"), t.getBooleanOr("p", false),
					t.getIntOr("usia", 60), dao == null ? Dao.SWORD : dao, sifat, t.getIntOr("look", id.hashCode()), t.getIntOr("gen", 0));
			c.status = Status.byId(t.getStringOr("status", "aktif"));
			c.lukaSampai = t.getLongOr("luka", 0L);
			CompoundTag sk = t.getCompoundOrEmpty("sikap");
			for (String key : sk.keySet()) {
				try {
					c.sikap.put(UUID.fromString(key), sk.getIntOr(key, 0));
				} catch (IllegalArgumentException ignored) {
					// a broken key is dropped
				}
			}
			return c;
		}
	}

	/** The SavedData {@code kultivator:gabung_tokoh}: elders per sect, masters, retired masters, gate titik. */
	static final class Data extends SavedData {

		static final String ID = "gabung_tokoh";
		static final Codec<Data> CODEC = CompoundTag.CODEC.xmap(Data::fromTag, Data::toTag);
		static final SavedDataType<Data> TYPE = new SavedDataType<>(Kultivator.id(ID), Data::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		final Map<String, List<Catatan>> tetua = new LinkedHashMap<>();
		final Map<String, Catatan> ketua = new LinkedHashMap<>();
		final Map<String, List<Catatan>> pensiun = new LinkedHashMap<>();
		final Map<String, Map<String, Long>> gerbang = new LinkedHashMap<>();

		Data() {
		}

		static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}

		/** The elders of {@code sekte}, seated up to the class's seats (grown lazily as the sect grows; never shrunk). */
		List<Catatan> pastikan(MinecraftServer server, String sekte) {
			if (sekte == null || sekte.isEmpty()) {
				return new ArrayList<>();
			}
			List<Catatan> list = tetua.computeIfAbsent(sekte, k -> new ArrayList<>());
			GerbangPlan.Entry e = entry(server, sekte);
			if (e == null) {
				return list;
			}
			if (!ketua.containsKey(sekte)) {
				ketua.put(sekte, Catatan.ketua(e, 0, Set.of()));
				setDirty();
			}
			int want = Math.min(URUTAN.size(), Math.max(3, kursi(sekte)));
			while (list.size() < want) {
				list.add(Catatan.tetua(e, list.size(), namaDipakai(sekte)));
				setDirty();
			}
			return list;
		}

		Set<String> namaDipakai(String sekte) {
			Set<String> out = new HashSet<>();
			for (Catatan c : tetua.getOrDefault(sekte, List.of())) {
				out.add(c.nama);
			}
			Catatan k = ketua.get(sekte);
			if (k != null) {
				out.add(k.nama);
			}
			for (Catatan c : pensiun.getOrDefault(sekte, List.of())) {
				out.add(c.nama);
			}
			return out;
		}

		private static Data fromTag(CompoundTag tag) {
			Data d = new Data();
			CompoundTag t = tag.getCompoundOrEmpty("tetua");
			for (String sekte : t.keySet()) {
				d.tetua.put(sekte, daftar(t.getListOrEmpty(sekte)));
			}
			CompoundTag k = tag.getCompoundOrEmpty("ketua");
			for (String sekte : k.keySet()) {
				Catatan c = Catatan.muat(k.getCompoundOrEmpty(sekte));
				if (c != null) {
					d.ketua.put(sekte, c);
				}
			}
			CompoundTag p = tag.getCompoundOrEmpty("pensiun");
			for (String sekte : p.keySet()) {
				d.pensiun.put(sekte, daftar(p.getListOrEmpty(sekte)));
			}
			CompoundTag g = tag.getCompoundOrEmpty("gerbang");
			for (String sekte : g.keySet()) {
				CompoundTag one = g.getCompoundOrEmpty(sekte);
				Map<String, Long> map = new LinkedHashMap<>();
				for (String nama : one.keySet()) {
					map.put(nama, one.getLongOr(nama, 0L));
				}
				d.gerbang.put(sekte, map);
			}
			return d;
		}

		private static List<Catatan> daftar(ListTag list) {
			List<Catatan> out = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				Catatan c = Catatan.muat(list.getCompoundOrEmpty(i));
				if (c != null) {
					out.add(c);
				}
			}
			return out;
		}

		private CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			CompoundTag t = new CompoundTag();
			tetua.forEach((sekte, list) -> t.put(sekte, simpanDaftar(list)));
			tag.put("tetua", t);
			CompoundTag k = new CompoundTag();
			ketua.forEach((sekte, c) -> k.put(sekte, c.simpan()));
			tag.put("ketua", k);
			CompoundTag p = new CompoundTag();
			pensiun.forEach((sekte, list) -> p.put(sekte, simpanDaftar(list)));
			tag.put("pensiun", p);
			CompoundTag g = new CompoundTag();
			gerbang.forEach((sekte, map) -> {
				CompoundTag one = new CompoundTag();
				map.forEach(one::putLong);
				g.put(sekte, one);
			});
			tag.put("gerbang", g);
			return tag;
		}

		private static ListTag simpanDaftar(List<Catatan> list) {
			ListTag out = new ListTag();
			for (Catatan c : list) {
				out.add(c.simpan());
			}
			return out;
		}
	}

	/** World day helper for callers of {@link #lukai} ("wounded for {@code hari} days from today"). */
	public static long sampai(MinecraftServer server, int hari) {
		return Kota.day(server) + Math.max(0, hari);
	}

	/** Lower-case office id of a {@link Jabatan} (the schedule and lang keys use it). */
	public static String kantorDari(Jabatan j) {
		return j == null ? PENERIMAAN : j.id().toLowerCase(Locale.ROOT);
	}

	private TokohSekte() {
	}
}

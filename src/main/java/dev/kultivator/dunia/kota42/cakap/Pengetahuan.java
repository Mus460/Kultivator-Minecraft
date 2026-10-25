package dev.kultivator.dunia.kota42.cakap;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SavedData {@code kultivator:kota_cakap} in the overworld (DESIGN 42.5A.12, owner 42K): what the conversation engine
 * remembers per townsperson. New worlds only, no migration.
 * <ul>
 * <li>{@code K} six trait bytes (a cache: {@link Kepribadian#bangkit} gives the same person back);</li>
 * <li>{@code E} PAD state + tick, kept only while it differs from the resting point ({@code emosi.json simpan_hari});</li>
 * <li>{@code F} remembered facts (at most {@code kotaBicara.faktaMaks}), forgetting curve of {@link Lupa};</li>
 * <li>{@code R} conversation history per player (6 talks, 3 promises, at most 6 players), {@code U} anti-repeat ring of 64;</li>
 * <li>per player an anti-repeat ring of 256 ({@code P}); per place the next gossip id ({@code G}).</li>
 * </ul>
 * The gossip table itself lives in 42A's {@code WargaData.Situs.gosip} ({@link GosipWarga} format), so one place's people
 * and rumours are dropped together. Iteration orders are sorted ({@link TreeMap}) for determinism (42.5A.12).
 */
public final class Pengetahuan extends SavedData {

	public static final Codec<Pengetahuan> CODEC = CompoundTag.CODEC.xmap(Pengetahuan::fromTag, Pengetahuan::toTag);
	public static final SavedDataType<Pengetahuan> TYPE = new SavedDataType<>(Kultivator.id("kota_cakap"), Pengetahuan::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Conversations kept per (warga, player). */
	public static final int CATATAN_MAKS = 6;
	/** Open promises per (warga, player). */
	public static final int JANJI_MAKS = 3;
	/** Players with a history per warga. */
	public static final int PEMAIN_MAKS = 6;
	/** Anti-repeat ring sizes (warga, player). */
	public static final int CINCIN_WARGA = 64;
	public static final int CINCIN_PEMAIN = 256;

	// ================================================================== parts

	/** A fixed ring of recently used keys (hashes) with the world hour they were used: anti-repeat memory (42.5A.8). */
	public static final class Cincin {
		final short[] kunci;
		final int[] jam;
		int pos;
		int isi;

		public Cincin(int ukuran) {
			kunci = new short[ukuran];
			jam = new int[ukuran];
		}

		/** Records that {@code kunci} (a lang key or rule id) was said in world hour {@code jamKini}. */
		public void catat(String kunci, int jamKini) {
			this.kunci[pos] = hash(kunci);
			jam[pos] = jamKini;
			pos = (pos + 1) % this.kunci.length;
			isi = Math.min(isi + 1, this.kunci.length);
		}

		/** World hours since {@code kunci} was last recorded, or {@link Integer#MAX_VALUE} when it is not in the ring. */
		public int jamSejak(String kunci, int jamKini) {
			short h = hash(kunci);
			int best = Integer.MAX_VALUE;
			for (int i = 0; i < isi; i++) {
				if (this.kunci[i] == h) {
					best = Math.min(best, Math.max(0, jamKini - jam[i]));
				}
			}
			return best;
		}

		public int ukuran() {
			return kunci.length;
		}

		/** 16-bit hash of a key (stable: {@code String.hashCode} folded). */
		public static short hash(String s) {
			int h = s == null ? 0 : s.hashCode();
			return (short) (h ^ (h >>> 16));
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			int[] packed = new int[isi];
			for (int i = 0; i < isi; i++) {
				packed[i] = (kunci[i] & 0xFFFF) << 16 | (jam[i] & 0xFFFF);
			}
			t.putIntArray("k", packed);
			int[] jamPenuh = Arrays.copyOf(jam, isi);
			t.putIntArray("j", jamPenuh);
			t.putInt("p", pos);
			return t;
		}

		static Cincin muat(CompoundTag t, int ukuran) {
			Cincin c = new Cincin(ukuran);
			int[] packed = t.getIntArray("k").orElse(new int[0]);
			int[] jamPenuh = t.getIntArray("j").orElse(new int[0]);
			c.isi = Math.min(Math.min(packed.length, jamPenuh.length), ukuran);
			for (int i = 0; i < c.isi; i++) {
				c.kunci[i] = (short) (packed[i] >>> 16);
				c.jam[i] = jamPenuh[i];
			}
			c.pos = Math.floorMod(t.getIntOr("p", 0), ukuran);
			return c;
		}
	}

	/** Status of a player's promise. */
	public enum StatusJanji {
		BELUM, DITEPATI, INGKAR
	}

	/** A player's promise to a warga (42.5A.6). */
	public static final class Janji {
		public final String predikat;
		public final String apa;
		public final long dibuat;
		public long batas;
		public StatusJanji status = StatusJanji.BELUM;
		/** True once the warga brought it up after the deadline (a callback is made once per state change). */
		public boolean ditagih;

		public Janji(String predikat, String apa, long dibuat, long batas) {
			this.predikat = predikat;
			this.apa = apa == null ? "" : apa;
			this.dibuat = dibuat;
			this.batas = batas;
		}

		public boolean terbuka() {
			return status == StatusJanji.BELUM;
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putString("p", predikat);
			if (!apa.isEmpty()) {
				t.putString("a", apa);
			}
			t.putLong("d", dibuat);
			t.putLong("b", batas);
			t.putByte("s", (byte) status.ordinal());
			if (ditagih) {
				t.putBoolean("t", true);
			}
			return t;
		}

		static Janji muat(CompoundTag t) {
			Janji j = new Janji(t.getStringOr("p", "MEMBAWA_BARANG"), t.getStringOr("a", ""), t.getLongOr("d", 0L), t.getLongOr("b", 0L));
			int s = t.getByteOr("s", (byte) 0);
			j.status = StatusJanji.values()[Math.max(0, Math.min(StatusJanji.values().length - 1, s))];
			j.ditagih = t.getBooleanOr("t", false);
			return j;
		}
	}

	/**
	 * One finished conversation (42.5A.6 history).
	 *
	 * @param hari  world day
	 * @param tick  game time it ended
	 * @param topik up to three topics touched
	 * @param niat  the player's last intent id
	 * @param nada  the player's last tone id
	 * @param hasil -1 bad, 0 plain, 1 good
	 */
	public record Catatan(long hari, long tick, List<String> topik, String niat, String nada, int hasil) {
		public Catatan {
			topik = List.copyOf(topik.size() > 3 ? topik.subList(topik.size() - 3, topik.size()) : topik);
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putLong("h", hari);
			t.putLong("t", tick);
			ListTag list = new ListTag();
			for (String s : topik) {
				list.add(StringTag.valueOf(s));
			}
			t.put("k", list);
			t.putString("n", niat);
			t.putString("a", nada);
			t.putByte("r", (byte) hasil);
			return t;
		}

		static Catatan muat(CompoundTag t) {
			ListTag list = t.getListOrEmpty("k");
			List<String> topik = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				topik.add(list.getStringOr(i, ""));
			}
			return new Catatan(t.getLongOr("h", 0L), t.getLongOr("t", 0L), topik, t.getStringOr("n", ""), t.getStringOr("a", ""),
					t.getByteOr("r", (byte) 0));
		}
	}

	/** History of one warga with one player. */
	public static final class Riwayat {
		public final int pemain;
		/** World day / tick of the last meeting (-1 = never talked). */
		public long temuHari = -1L;
		public long temuTick;
		public final List<Catatan> catatan = new ArrayList<>();
		public final List<Janji> janji = new ArrayList<>();
		/** Tone use today: day and counts per {@link Nada} ordinal (repeat factor 0.7^n of 42.5A.7). */
		public long nadaHari = -1L;
		public final byte[] nadaUlang = new byte[Nada.values().length];
		/** Personal price factor delta of a trader toward this player (42.5A.7 "harga"), and the world day it ends. */
		public float harga;
		public long hargaSampai = -1L;

		public Riwayat(int pemain) {
			this.pemain = pemain;
		}

		/** The personal price factor delta valid on {@code hari} (0 when expired). */
		public float harga(long hari) {
			return hari <= hargaSampai ? harga : 0.0F;
		}

		/** Adds to the personal price factor (kept 3 days, clamped -0.2..+0.2). */
		public void ubahHarga(float delta, long hari) {
			harga = Math.max(-0.2F, Math.min(0.2F, harga(hari) + delta));
			hargaSampai = hari + 3L;
		}

		/** Times {@code nada} was used on this warga today. */
		public int ulang(Nada nada, long hari) {
			return nadaHari == hari ? nadaUlang[nada.ordinal()] : 0;
		}

		public void pakaiNada(Nada nada, long hari) {
			if (nadaHari != hari) {
				Arrays.fill(nadaUlang, (byte) 0);
				nadaHari = hari;
			}
			nadaUlang[nada.ordinal()] = (byte) Math.min(100, nadaUlang[nada.ordinal()] + 1);
		}

		public void tambah(Catatan c) {
			catatan.add(c);
			while (catatan.size() > CATATAN_MAKS) {
				catatan.remove(0);
			}
		}

		/** Adds a promise; the oldest closed one gives way, then the oldest open one. */
		public void janji(Janji j) {
			janji.add(j);
			while (janji.size() > JANJI_MAKS) {
				Janji drop = janji.get(0);
				for (Janji x : janji) {
					if (!x.terbuka()) {
						drop = x;
						break;
					}
				}
				janji.remove(drop);
			}
		}

		/** Ticks since {@code topik} was last discussed with this player, {@link Long#MAX_VALUE} when never. */
		public long sejakTopik(String topik, long now) {
			long best = Long.MAX_VALUE;
			for (Catatan c : catatan) {
				if (c.topik().contains(topik)) {
					best = Math.min(best, Math.max(0L, now - c.tick()));
				}
			}
			return best;
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putInt("i", pemain);
			t.putLong("h", temuHari);
			t.putLong("t", temuTick);
			ListTag c = new ListTag();
			for (Catatan x : catatan) {
				c.add(x.simpan());
			}
			t.put("c", c);
			ListTag j = new ListTag();
			for (Janji x : janji) {
				j.add(x.simpan());
			}
			t.put("j", j);
			if (hargaSampai >= 0L) {
				t.putFloat("hg", harga);
				t.putLong("hs", hargaSampai);
			}
			if (nadaHari >= 0L) {
				t.putLong("nh", nadaHari);
				t.putByteArray("nu", nadaUlang.clone());
			}
			return t;
		}

		static Riwayat muat(CompoundTag t) {
			Riwayat r = new Riwayat(t.getIntOr("i", -1));
			r.temuHari = t.getLongOr("h", -1L);
			r.temuTick = t.getLongOr("t", 0L);
			ListTag c = t.getListOrEmpty("c");
			for (int i = 0; i < c.size(); i++) {
				r.catatan.add(Catatan.muat(c.getCompoundOrEmpty(i)));
			}
			ListTag j = t.getListOrEmpty("j");
			for (int i = 0; i < j.size(); i++) {
				r.janji.add(Janji.muat(j.getCompoundOrEmpty(i)));
			}
			r.harga = t.getFloatOr("hg", 0.0F);
			r.hargaSampai = t.getLongOr("hs", -1L);
			r.nadaHari = t.getLongOr("nh", -1L);
			byte[] nu = t.getByteArray("nu").orElse(new byte[0]);
			System.arraycopy(nu, 0, r.nadaUlang, 0, Math.min(nu.length, r.nadaUlang.length));
			return r;
		}
	}

	/** Everything stored about one warga. Fields are engine-internal (package); readers are public. */
	public static final class Entri {
		public final int warga;
		@Nullable int[] sifat;
		boolean adaPad;
		float p;
		float a;
		float d;
		long padTick;
		/** Kind/id of the cause of the last directed push ({@link Fakta.Jenis} ordinal, -1 = none). */
		int sasaranJenis = -1;
		long sasaranId;
		/** Temporary trait shifts of the last push ({@code emosi.json efek}) and the tick they end. */
		final Map<String, Integer> efek = new TreeMap<>();
		long efekSampai;
		final List<Fakta> fakta = new ArrayList<>();
		final Map<Integer, Riwayat> riwayat = new TreeMap<>();
		final Cincin ulang = new Cincin(CINCIN_WARGA);

		Entri(int warga) {
			this.warga = warga;
		}

		public List<Fakta> fakta() {
			return List.copyOf(fakta);
		}

		public boolean adaPad() {
			return adaPad;
		}

		public long padTick() {
			return padTick;
		}

		/** The cause the current feeling points at, or {@link Fakta.Rujuk#TIADA}. */
		public Fakta.Rujuk sasaran() {
			return sasaranJenis < 0 ? Fakta.Rujuk.TIADA : new Fakta.Rujuk(Fakta.Jenis.values()[sasaranJenis], sasaranId, "");
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putInt("i", warga);
			if (sifat != null) {
				byte[] k = new byte[6];
				for (int i = 0; i < 6; i++) {
					k[i] = (byte) sifat[i];
				}
				t.putByteArray("K", k);
			}
			if (adaPad) {
				CompoundTag e = new CompoundTag();
				e.putFloat("p", p);
				e.putFloat("a", a);
				e.putFloat("d", d);
				e.putLong("t", padTick);
				if (sasaranJenis >= 0) {
					e.putByte("sj", (byte) sasaranJenis);
					e.putLong("si", sasaranId);
				}
				if (!efek.isEmpty()) {
					CompoundTag ef = new CompoundTag();
					efek.forEach(ef::putInt);
					e.put("ef", ef);
					e.putLong("es", efekSampai);
				}
				t.put("E", e);
			}
			if (!fakta.isEmpty()) {
				ListTag f = new ListTag();
				for (Fakta x : fakta) {
					f.add(simpanFakta(x));
				}
				t.put("F", f);
			}
			if (!riwayat.isEmpty()) {
				ListTag r = new ListTag();
				for (Riwayat x : riwayat.values()) {
					r.add(x.simpan());
				}
				t.put("R", r);
			}
			if (ulang.isi > 0) {
				t.put("U", ulang.simpan());
			}
			return t;
		}

		static Entri muat(CompoundTag t) {
			Entri e = new Entri(t.getIntOr("i", 0));
			byte[] k = t.getByteArray("K").orElse(null);
			if (k != null && k.length == 6) {
				e.sifat = new int[6];
				for (int i = 0; i < 6; i++) {
					e.sifat[i] = k[i];
				}
			}
			if (t.contains("E")) {
				CompoundTag pad = t.getCompoundOrEmpty("E");
				e.adaPad = true;
				e.p = pad.getFloatOr("p", 0.0F);
				e.a = pad.getFloatOr("a", 0.0F);
				e.d = pad.getFloatOr("d", 0.0F);
				e.padTick = pad.getLongOr("t", 0L);
				e.sasaranJenis = pad.getByteOr("sj", (byte) -1);
				e.sasaranId = pad.getLongOr("si", 0L);
				CompoundTag ef = pad.getCompoundOrEmpty("ef");
				for (String key : ef.keySet()) {
					e.efek.put(key, ef.getIntOr(key, 0));
				}
				e.efekSampai = pad.getLongOr("es", 0L);
			}
			ListTag f = t.getListOrEmpty("F");
			for (int i = 0; i < f.size(); i++) {
				e.fakta.add(muatFakta(f.getCompoundOrEmpty(i)));
			}
			ListTag r = t.getListOrEmpty("R");
			for (int i = 0; i < r.size(); i++) {
				Riwayat x = Riwayat.muat(r.getCompoundOrEmpty(i));
				e.riwayat.put(x.pemain, x);
			}
			if (t.contains("U")) {
				Cincin c = Cincin.muat(t.getCompoundOrEmpty("U"), CINCIN_WARGA);
				System.arraycopy(c.kunci, 0, e.ulang.kunci, 0, CINCIN_WARGA);
				System.arraycopy(c.jam, 0, e.ulang.jam, 0, CINCIN_WARGA);
				e.ulang.pos = c.pos;
				e.ulang.isi = c.isi;
			}
			return e;
		}
	}

	// ================================================================== store

	private final Map<Integer, Entri> entri = new TreeMap<>();
	private final Map<Integer, Cincin> cincinPemain = new TreeMap<>();
	private final Map<Long, Integer> gosipNext = new TreeMap<>();
	/** Overheard facts per player index (42.5A.9 "Menguping": Buku Kota tab Kabar), newest last. */
	private final Map<Integer, List<Fakta>> catatan = new TreeMap<>();
	/** Transient: player index -> topic -> tick it was last discussed with that player by anyone (42.5A.5, 1 hour). */
	private final Map<Integer, Map<String, Long>> topikPemain = new HashMap<>();
	private @Nullable MinecraftServer server;

	public Pengetahuan() {
	}

	/** The world's store (overworld storage), created on first use. */
	public static Pengetahuan get(MinecraftServer server) {
		Pengetahuan data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		data.server = server;
		return data;
	}

	/** The entry of a warga, created on first use. */
	public Entri entri(Warga w) {
		return entri.computeIfAbsent(w.id, Entri::new);
	}

	public @Nullable Entri peek(int wargaId) {
		return entri.get(wargaId);
	}

	/** Drops everything about a warga (death and archive are 42A's; called from the WAFAT listener). */
	public void lupakan(int wargaId) {
		if (entri.remove(wargaId) != null) {
			setDirty();
		}
	}

	/** Number of stored entries (test command, budget line). */
	public int jumlah() {
		return entri.size();
	}

	public void ubah() {
		setDirty();
	}

	// ------------------------------------------------------------------ personality and feeling

	/** The personality of {@code w} (born on first use from temperament, habits, parents; 42.5A.2). */
	public Kepribadian.Sifat sifat(Warga w) {
		return sifat(w, 0);
	}

	private Kepribadian.Sifat sifat(Warga w, int depth) {
		Entri e = entri(w);
		if (e.sifat == null) {
			Kepribadian.Sifat ayah = null;
			Kepribadian.Sifat ibu = null;
			if (depth < 4) {
				Warga a = w.ayah == Warga.TANPA ? null : WargaApi.byId(w.ayah);
				Warga b = w.ibu == Warga.TANPA ? null : WargaApi.byId(w.ibu);
				if (a != null && b != null) {
					ayah = sifat(a, depth + 1);
					ibu = sifat(b, depth + 1);
				}
			}
			long seed = server == null ? 0L : server.overworld().getSeed();
			e.sifat = Kepribadian.bangkit(w, CakapReload.kepribadian(), seed, ayah, ibu).larik();
			setDirty();
		}
		Kepribadian.Sifat s = Kepribadian.Sifat.dari(e.sifat);
		long now = server == null ? 0L : server.overworld().getGameTime();
		if (!e.efek.isEmpty() && now < e.efekSampai) {
			for (Map.Entry<String, Integer> x : e.efek.entrySet()) {
				if (!x.getKey().isEmpty()) {
					s = s.geser(x.getKey().charAt(0), x.getValue());
				}
			}
		}
		return s;
	}

	/** Current PAD of {@code w}. */
	public float[] pad(Warga w, long now) {
		return Emosi.kini(entri(w), sifat(w), w.suasana, now);
	}

	/**
	 * Applies an appraised push (42.5A.3) and remembers the cause for directed feelings and the temporary trait effects.
	 */
	public void dorong(Warga w, Emosi.Peristiwa ev, float intensitas, Fakta.Rujuk sasaran, long now) {
		Entri e = entri(w);
		Emosi.dorong(e, sifat(w), w.suasana, ev.p(), ev.a(), ev.d(), ev.dRendah(), intensitas, now);
		if (ev.berarah() && sasaran != null && sasaran.jenis() != Fakta.Jenis.TIADA) {
			e.sasaranJenis = sasaran.jenis().ordinal();
			e.sasaranId = sasaran.id();
		}
		if (!ev.efek().isEmpty()) {
			e.efek.clear();
			e.efek.putAll(ev.efek());
			e.efekSampai = now + Emosi.paruh(Kepribadian.Sifat.dari(e.sifat)) * 2L;
		}
		setDirty();
	}

	// ------------------------------------------------------------------ facts

	/** Facts of {@code w} still recalled on {@code hari} (R >= 0.1), in storage order. */
	public List<Fakta> ingat(Warga w, long hari) {
		Entri e = entri.get(w.id);
		if (e == null) {
			return List.of();
		}
		List<Fakta> out = new ArrayList<>();
		for (Fakta f : e.fakta) {
			if (Lupa.ingat(f, hari) >= Lupa.AMBANG || dilindungi(w, f)) {
				out.add(f);
			}
		}
		return out;
	}

	/** The copy of the same fact {@code w} holds (same gossip, else same predicate, subject and object), or null. */
	public @Nullable Fakta sama(Warga w, Fakta f) {
		Entri e = entri.get(w.id);
		if (e == null) {
			return null;
		}
		for (Fakta x : e.fakta) {
			if (identik(x, f)) {
				return x;
			}
		}
		return null;
	}

	static boolean identik(Fakta x, Fakta f) {
		if (x.gosipId() >= 0 && f.gosipId() >= 0) {
			return x.gosipId() == f.gosipId();
		}
		return x.predikat().equals(f.predikat()) && x.subjek().equals(f.subjek()) && x.objek().equals(f.objek());
	}

	/**
	 * Stores the holder's copy {@code f} (already carrying its own confidence, source and strength). Contradictions: an
	 * existing copy with higher confidence wins and is only refreshed; otherwise it is replaced. Over the cap the least
	 * memorable unprotected fact is forgotten. Returns the copy now stored.
	 */
	public Fakta simpan(Warga w, Fakta f, long hari) {
		Entri e = entri(w);
		for (int i = 0; i < e.fakta.size(); i++) {
			Fakta x = e.fakta.get(i);
			if (identik(x, f)) {
				Fakta keep = x.yakin() > f.yakin() || (x.asal().sumber() == Fakta.Sumber.SAKSI && f.asal().sumber() != Fakta.Sumber.SAKSI)
						? Lupa.diingat(x, hari) : f;
				e.fakta.set(i, keep);
				setDirty();
				return keep;
			}
		}
		e.fakta.add(f);
		int cap = Math.max(4, KotaConfig.bicara().faktaMaks);
		while (e.fakta.size() > cap) {
			int drop = -1;
			double worst = Double.MAX_VALUE;
			for (int i = 0; i < e.fakta.size(); i++) {
				Fakta x = e.fakta.get(i);
				double score = Lupa.ingat(x, hari) * (1.0D + Math.abs(x.valensi())) + (dilindungi(w, x) ? 10.0D : 0.0D);
				if (score < worst) {
					worst = score;
					drop = i;
				}
			}
			e.fakta.remove(drop);
		}
		setDirty();
		return f;
	}

	/** Replaces a stored copy (after a telling: {@link Lupa#diceritakan}). */
	public void ganti(Warga w, Fakta lama, Fakta baru) {
		Entri e = entri.get(w.id);
		if (e != null) {
			int i = e.fakta.indexOf(lama);
			if (i >= 0) {
				e.fakta.set(i, baru);
				setDirty();
			}
		}
	}

	/**
	 * Trust of a holder in a source (42.5A.4): {@code 50 + bond/2 + H_teller/4} for a townsperson, impression for a
	 * player, 100 for a witness or oneself, 70 for the notice board.
	 */
	public int kepercayaan(Warga penerima, Fakta.Asal asal) {
		return switch (asal.sumber()) {
			case SAKSI, DIRI -> 100;
			case PAPAN -> 70;
			case PEMAIN -> {
				int kesan = 0;
				for (var i : penerima.ingatan) {
					if (i.pemain == asal.dari()) {
						kesan = i.kesan;
					}
				}
				yield Math.max(5, Math.min(100, 50 + kesan / 2));
			}
			case DENGAR -> {
				long bond = penerima.hubunganDengan(asal.dari());
				int strength = bond == 0L ? 0 : JenisHubungan.kekuatan(bond);
				Warga teller = WargaApi.byId(asal.dari());
				int h = teller == null ? 0 : sifat(teller).h();
				yield Math.max(5, Math.min(100, 50 + strength / 2 + h / 4));
			}
		};
	}

	/** True when a fact is never forgotten by {@code w}: a family matter of a family predicate, or a strong memory. */
	public static boolean dilindungi(Warga w, Fakta f) {
		if (Math.abs(f.valensi()) >= 3) {
			return true;
		}
		Predikat p = Predikat.dari(f.predikat());
		return p.keluarga() && (keluarga(w, f.subjek()) || keluarga(w, f.objek()));
	}

	/** True when {@code r} is {@code w} or a member of its family (parents, spouse, children, household). */
	public static boolean keluarga(Warga w, Fakta.Rujuk r) {
		if (r.jenis() != Fakta.Jenis.WARGA) {
			return false;
		}
		int id = (int) r.id();
		if (id == w.id || id == w.ayah || id == w.ibu || id == w.pasangan) {
			return true;
		}
		for (int c : w.anak) {
			if (c == id) {
				return true;
			}
		}
		Warga o = WargaApi.byId(id);
		return o != null && w.rumahTangga != 0 && o.rumahTangga == w.rumahTangga;
	}

	/**
	 * The daily pass of one warga (42.5A.4, run with the place's Hari Warga): drops facts with R &lt; 0.1 that are not
	 * protected, a PAD state older than {@code simpan_hari}, ended trait effects and long-closed promises.
	 */
	public void harian(Warga w, long hari, long now) {
		Entri e = entri.get(w.id);
		if (e == null) {
			return;
		}
		boolean changed = e.fakta.removeIf(f -> Lupa.ingat(f, hari) < Lupa.AMBANG && !dilindungi(w, f));
		if (e.adaPad && now - e.padTick > CakapReload.emosi().simpanHari() * 24000L) {
			e.adaPad = false;
			e.sasaranJenis = -1;
			changed = true;
		}
		if (!e.efek.isEmpty() && now >= e.efekSampai) {
			e.efek.clear();
			changed = true;
		}
		for (Riwayat r : e.riwayat.values()) {
			changed |= r.janji.removeIf(j -> !j.terbuka() && hari - j.batas > 28L);
		}
		if (changed) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------ history and anti-repeat

	/** The history of {@code w} with player index {@code pemain}, created on first use (6 players kept). */
	public Riwayat riwayat(Warga w, int pemain) {
		Entri e = entri(w);
		Riwayat r = e.riwayat.get(pemain);
		if (r == null) {
			while (e.riwayat.size() >= PEMAIN_MAKS) {
				int oldest = -1;
				long day = Long.MAX_VALUE;
				for (Riwayat x : e.riwayat.values()) {
					if (x.temuHari < day) {
						day = x.temuHari;
						oldest = x.pemain;
					}
				}
				e.riwayat.remove(oldest);
			}
			r = new Riwayat(pemain);
			e.riwayat.put(pemain, r);
			setDirty();
		}
		return r;
	}

	public @Nullable Riwayat peekRiwayat(Warga w, int pemain) {
		Entri e = entri.get(w.id);
		return e == null ? null : e.riwayat.get(pemain);
	}

	/** The anti-repeat ring of a speaker (64 keys). */
	public Cincin cincinWarga(Warga w) {
		return entri(w).ulang;
	}

	/** The anti-repeat ring of a listening player (256 keys). */
	public Cincin cincinPemain(int pemain) {
		return cincinPemain.computeIfAbsent(pemain, i -> new Cincin(CINCIN_PEMAIN));
	}

	/** Records a topic discussed with a player by anyone (42.5A.5 {@code sudahDibahas}, 1 hour, not saved). */
	public void topikDibahas(int pemain, String topik, long now) {
		topikPemain.computeIfAbsent(pemain, i -> new HashMap<>()).put(topik, now);
	}

	/** Ticks since anyone discussed {@code topik} with the player, {@link Long#MAX_VALUE} when never. */
	public long sejakTopikPemain(int pemain, String topik, long now) {
		Map<String, Long> m = topikPemain.get(pemain);
		Long t = m == null ? null : m.get(topik);
		return t == null ? Long.MAX_VALUE : Math.max(0L, now - t);
	}

	/** Notes kept per player. */
	public static final int CATATAN_PEMAIN_MAKS = 32;

	/** A player overheard {@code f} (a copy as {@link Fakta.Sumber#DENGAR}); the same fact is only refreshed. */
	public void catat(int pemain, Fakta f, long hari) {
		if (pemain < 0) {
			return;
		}
		List<Fakta> list = catatan.computeIfAbsent(pemain, i -> new ArrayList<>());
		list.removeIf(x -> identik(x, f));
		list.add(Lupa.diingat(f, hari));
		while (list.size() > CATATAN_PEMAIN_MAKS) {
			list.remove(0);
		}
		setDirty();
	}

	/** The notes of a player index, oldest first (42U Buku Kota tab Kabar reads them). */
	public List<Fakta> catatan(int pemain) {
		List<Fakta> list = catatan.get(pemain);
		return list == null ? List.of() : List.copyOf(list);
	}

	/** The next gossip id of a place. */
	int gosipBaru(long situs) {
		int id = gosipNext.getOrDefault(situs, 1);
		gosipNext.put(situs, id + 1);
		setDirty();
		return id;
	}

	// ================================================================== fact codec (shared with GosipWarga)

	public static CompoundTag simpanRujuk(Fakta.Rujuk r) {
		CompoundTag t = new CompoundTag();
		t.putByte("j", (byte) r.jenis().ordinal());
		if (r.id() != 0L) {
			t.putLong("i", r.id());
		}
		if (!r.kunci().isEmpty()) {
			t.putString("k", r.kunci());
		}
		return t;
	}

	public static Fakta.Rujuk muatRujuk(CompoundTag t) {
		int j = t.getByteOr("j", (byte) 0);
		Fakta.Jenis[] all = Fakta.Jenis.values();
		Fakta.Jenis jenis = j >= 0 && j < all.length ? all[j] : Fakta.Jenis.TIADA;
		return jenis == Fakta.Jenis.TIADA ? Fakta.Rujuk.TIADA : new Fakta.Rujuk(jenis, t.getLongOr("i", 0L), t.getStringOr("k", ""));
	}

	/** The saved form of a fact (field letters of {@link Fakta}). */
	public static CompoundTag simpanFakta(Fakta f) {
		CompoundTag t = new CompoundTag();
		t.putString("p", f.predikat());
		t.put("s", simpanRujuk(f.subjek()));
		if (f.objek().jenis() != Fakta.Jenis.TIADA) {
			t.put("o", simpanRujuk(f.objek()));
		}
		if (f.nilai() != 0) {
			t.putInt("n", f.nilai());
		}
		t.putByte("b", (byte) f.kebenaran().ordinal());
		t.putByte("y", (byte) f.yakin());
		t.putByte("a", (byte) f.asal().sumber().ordinal());
		if (f.asal().dari() >= 0) {
			t.putInt("d", f.asal().dari());
		}
		t.putLong("hk", f.hariKejadian());
		t.putLong("ht", f.hariTahu());
		t.putFloat("k", f.kekuatan());
		if (f.kaliDiceritakan() != 0) {
			t.putByte("c", (byte) Math.min(127, f.kaliDiceritakan()));
		}
		if (f.valensi() != 0) {
			t.putByte("v", (byte) f.valensi());
		}
		if (f.gosipId() >= 0) {
			t.putInt("g", f.gosipId());
		}
		return t;
	}

	public static Fakta muatFakta(CompoundTag t) {
		Fakta.Kebenaran[] kb = Fakta.Kebenaran.values();
		Fakta.Sumber[] sb = Fakta.Sumber.values();
		int b = t.getByteOr("b", (byte) 0);
		int a = t.getByteOr("a", (byte) 0);
		return new Fakta(t.getStringOr("p", "OPINI"), muatRujuk(t.getCompoundOrEmpty("s")),
				t.contains("o") ? muatRujuk(t.getCompoundOrEmpty("o")) : Fakta.Rujuk.TIADA, t.getIntOr("n", 0),
				kb[Math.max(0, Math.min(kb.length - 1, b))], t.getByteOr("y", (byte) 50),
				new Fakta.Asal(sb[Math.max(0, Math.min(sb.length - 1, a))], t.getIntOr("d", -1)), t.getLongOr("hk", 0L),
				t.getLongOr("ht", 0L), t.getFloatOr("k", Fakta.KEKUATAN_DENGAR), t.getByteOr("c", (byte) 0), t.getByteOr("v", (byte) 0),
				t.getIntOr("g", -1));
	}

	// ================================================================== codec

	private static Pengetahuan fromTag(CompoundTag tag) {
		Pengetahuan data = new Pengetahuan();
		ListTag w = tag.getListOrEmpty("w");
		for (int i = 0; i < w.size(); i++) {
			Entri e = Entri.muat(w.getCompoundOrEmpty(i));
			if (e.warga > 0) {
				data.entri.put(e.warga, e);
			}
		}
		ListTag p = tag.getListOrEmpty("P");
		for (int i = 0; i < p.size(); i++) {
			CompoundTag c = p.getCompoundOrEmpty(i);
			data.cincinPemain.put(c.getIntOr("i", -1), Cincin.muat(c, CINCIN_PEMAIN));
		}
		ListTag notes = tag.getListOrEmpty("C");
		for (int i = 0; i < notes.size(); i++) {
			CompoundTag c = notes.getCompoundOrEmpty(i);
			List<Fakta> list = new ArrayList<>();
			ListTag f = c.getListOrEmpty("f");
			for (int k = 0; k < f.size(); k++) {
				list.add(muatFakta(f.getCompoundOrEmpty(k)));
			}
			data.catatan.put(c.getIntOr("i", -1), list);
		}
		CompoundTag g = tag.getCompoundOrEmpty("G");
		for (String key : g.keySet()) {
			try {
				data.gosipNext.put(Long.parseLong(key), g.getIntOr(key, 1));
			} catch (NumberFormatException ignored) {
				// a broken key only resets that place's counter
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag w = new ListTag();
		for (Entri e : entri.values()) {
			w.add(e.simpan());
		}
		tag.put("w", w);
		ListTag p = new ListTag();
		for (Map.Entry<Integer, Cincin> e : cincinPemain.entrySet()) {
			CompoundTag c = e.getValue().simpan();
			c.putInt("i", e.getKey());
			p.add(c);
		}
		tag.put("P", p);
		ListTag notes = new ListTag();
		for (Map.Entry<Integer, List<Fakta>> e : catatan.entrySet()) {
			CompoundTag c = new CompoundTag();
			c.putInt("i", e.getKey());
			ListTag f = new ListTag();
			for (Fakta x : e.getValue()) {
				f.add(simpanFakta(x));
			}
			c.put("f", f);
			notes.add(c);
		}
		tag.put("C", notes);
		CompoundTag g = new CompoundTag();
		gosipNext.forEach((k, v) -> g.putInt(Long.toString(k), v));
		tag.put("G", g);
		return tag;
	}
}

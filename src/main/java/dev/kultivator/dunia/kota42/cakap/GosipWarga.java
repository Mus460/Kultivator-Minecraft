package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Town gossip (DESIGN 42.5.4, stored as facts of 42.5A.4): born from real events, spread on Hari Warga and in NPC-NPC chats,
 * embellished per hop, cooling by 10 heat per day; at most 64 live gossips per place (coldest dropped), each townsperson
 * knows at most 12 ids. {@code KabarTempat.pemicu} also lights a gossip at that place ({@link #dariKabar}, one source).
 * <p>
 * Storage: the table is 42A's {@code WargaData.Situs.gosip} (one compound per {@link Gosip}, keys {@code i t s a l b h f d});
 * who knows what is {@code Warga.gosip} (ids) plus the holder's own {@link Fakta} copy (truth, confidence, teller) in
 * {@link Pengetahuan}. The chain "who told you?" ({@link #rantai}) follows the {@link Fakta.Asal#dari} of each copy.
 */
public final class GosipWarga {

	/** Live gossips per place. */
	public static final int MAKS_TEMPAT = 64;
	/** Gossip ids one townsperson keeps. */
	public static final int MAKS_WARGA = 12;
	/** Heat lost per day (42.5.4). */
	public static final int DINGIN_HARIAN = 10;
	/** Base chance of a hop (x heat/100 x multipliers). */
	public static final double PELUANG_SEBAR = 0.25D;
	/** Chance a hop embellishes (42.5.4 "Bumbu"), and the subject swap chance of a sly teller. */
	public static final double PELUANG_BUMBU = 0.15D;
	public static final double PELUANG_TUKAR_LICIK = 0.30D;
	/** Daily chance a sly townsperson invents a false rumour about an enemy. */
	public static final double PELUANG_PALSU = 0.03D;

	/** Warga predicates a liar may invent about an enemy (42.5.4 "PALSU"). */
	private static final List<String> FITNAH = List.of("BERUTANG", "MABUK", "KALAH_JUDI", "BERKELAHI", "USAHA_BANGKRUT");

	/**
	 * One gossip of the per-place table (42.5.4 "Rekam").
	 *
	 * @param id       id unique within the place
	 * @param templat  template / predicate id (grammar rule group {@code gosip.<templat>})
	 * @param subjek   up to two subjects
	 * @param asal     site id where it was born
	 * @param lahir    world day of birth
	 * @param benar    truth of the original
	 * @param panas    heat 0-100
	 * @param isian    fixed arguments (lexicon words, numbers as strings, item ids)
	 */
	public record Gosip(int id, String templat, List<Fakta.Rujuk> subjek, long asal, long lahir, Fakta.Kebenaran benar,
			int panas, List<String> isian) {
		public Gosip {
			subjek = List.copyOf(subjek.size() > 2 ? subjek.subList(0, 2) : subjek);
			panas = Math.max(0, Math.min(100, panas));
			isian = List.copyOf(isian);
		}

		public Fakta.Rujuk subjek(int i) {
			return i < subjek.size() ? subjek.get(i) : Fakta.Rujuk.TIADA;
		}

		CompoundTag simpan(long hariDingin) {
			CompoundTag t = new CompoundTag();
			t.putInt("i", id);
			t.putString("t", templat);
			ListTag s = new ListTag();
			for (Fakta.Rujuk r : subjek) {
				s.add(Pengetahuan.simpanRujuk(r));
			}
			t.put("s", s);
			t.putLong("a", asal);
			t.putLong("l", lahir);
			t.putByte("b", (byte) benar.ordinal());
			t.putByte("h", (byte) panas);
			if (!isian.isEmpty()) {
				ListTag f = new ListTag();
				for (String x : isian) {
					f.add(StringTag.valueOf(x));
				}
				t.put("f", f);
			}
			t.putLong("d", hariDingin);
			return t;
		}

		static Gosip muat(CompoundTag t) {
			ListTag s = t.getListOrEmpty("s");
			List<Fakta.Rujuk> subjek = new ArrayList<>();
			for (int i = 0; i < s.size(); i++) {
				subjek.add(Pengetahuan.muatRujuk(s.getCompoundOrEmpty(i)));
			}
			ListTag f = t.getListOrEmpty("f");
			List<String> isian = new ArrayList<>();
			for (int i = 0; i < f.size(); i++) {
				isian.add(f.getStringOr(i, ""));
			}
			Fakta.Kebenaran[] kb = Fakta.Kebenaran.values();
			int b = t.getByteOr("b", (byte) 0);
			return new Gosip(t.getIntOr("i", 0), t.getStringOr("t", ""), subjek, t.getLongOr("a", 0L), t.getLongOr("l", 0L),
					kb[Math.max(0, Math.min(kb.length - 1, b))], t.getByteOr("h", (byte) 0), isian);
		}

		/** The shared fact of this gossip (the original, before any holder's own copy). */
		Fakta fakta(int nilai, Fakta.Asal asal, int yakin, float kekuatan, long hariTahu) {
			Predikat p = Predikat.dari(templat);
			return new Fakta(templat, subjek(0), subjek(1), nilai, benar, yakin, asal, lahir, hariTahu, kekuatan, 0, p.valensi(), id);
		}
	}

	// ================================================================== birth

	/**
	 * Gives birth to a true gossip at a place: the witnesses there (bodies in view, else the place's townsfolk by schedule)
	 * learn it as {@link Fakta.Sumber#SAKSI}. Overworld only; other levels are ignored.
	 *
	 * @param level   the level of the place
	 * @param situs   site id of the place
	 * @param templat template id (predicate of {@code percakapan/predikat.json})
	 * @param subjek  up to two subjects (more are ignored)
	 * @return the new gossip id, or -1 when nothing was born (engine off, unknown place, stub)
	 */
	public static int lahir(ServerLevel level, long situs, String templat, Fakta.Rujuk... subjek) {
		return lahir(level, situs, templat, 0, Fakta.Kebenaran.BENAR, List.of(), null, subjek);
	}

	/**
	 * The full form of {@link #lahir}: a number argument, the truth, fixed arguments, and an optional single teller (a liar
	 * inventing a rumour: only {@code pencipta} knows it, as its own word).
	 */
	public static int lahir(ServerLevel level, long situs, String templat, int nilai, Fakta.Kebenaran benar, List<String> isian,
			@Nullable Warga pencipta, Fakta.Rujuk... subjek) {
		MinecraftServer server = level.getServer();
		if (!aktif() || !KotaHidup.duniaKota(level) || situs == 0L || templat == null || templat.isEmpty()) {
			return -1;
		}
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null || s.warga.isEmpty()) {
			return -1;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		int id = pk.gosipBaru(situs);
		Predikat p = Predikat.dari(templat);
		List<Fakta.Rujuk> subj = new ArrayList<>(Arrays.asList(subjek == null ? new Fakta.Rujuk[0] : subjek));
		subj.removeIf(r -> r == null || r.jenis() == Fakta.Jenis.TIADA);
		Gosip g = new Gosip(id, templat, subj, situs, hari, benar, 60 + 10 * Math.abs(p.valensi()), isian);
		tulis(s, g, hari);
		trim(s);
		SplittableRandom r = Cakap.acak(server.overworld().getSeed(), id, (int) situs, hari, 0, "gosip_lahir");
		if (pencipta != null) {
			beriTahu(pk, data, pencipta, g, nilai, Fakta.Asal.DIRI, 100, hari);
		} else {
			for (Warga w : saksi(level, s, g, r)) {
				boolean diri = g.subjek.stream().anyMatch(x -> x.jenis() == Fakta.Jenis.WARGA && x.id() == w.id);
				beriTahu(pk, data, w, g, nilai, diri ? Fakta.Asal.DIRI : Fakta.Asal.SAKSI, 100, hari);
			}
		}
		data.ubah();
		KotaBus.GOSIP_LAHIR.invoker().onGosipLahir(server, situs, id, templat);
		return id;
	}

	/**
	 * {@code KabarTempat.pemicu} hook (42.5.4 "satu sumber"): a 38 rumour of a place also lights a gossip there. The trigger
	 * maps to a predicate ({@code harta_lahir} -> {@code HARTA_LAHIR}, {@code langit_*} -> {@code ACARA}, {@code sekte_*}
	 * -> {@code SEKTE_PERANG}, {@code tempat_rusak}/{@code kejahatan} -> {@code BAHAYA}, {@code acara_*} -> {@code ACARA},
	 * others -> {@code TEMPAT_ADA}); world-wide rumours (site 0) are ignored here.
	 */
	public static void dariKabar(ServerLevel level, long situs, String pemicu, String... isian) {
		if (situs == 0L || pemicu == null || !aktif()) {
			return;
		}
		String predikat;
		if (pemicu.startsWith("harta_")) {
			predikat = "HARTA_LAHIR";
		} else if (pemicu.startsWith("sekte_")) {
			predikat = "SEKTE_PERANG";
		} else if (pemicu.equals("tempat_rusak") || pemicu.equals("kejahatan") || pemicu.equals("segel_pecah")
				|| pemicu.equals("binatang_evolusi")) {
			predikat = "BAHAYA";
		} else if (pemicu.startsWith("langit_") || pemicu.startsWith("acara_")) {
			predikat = "ACARA";
		} else if (pemicu.equals("umum")) {
			return;
		} else {
			predikat = "TEMPAT_ADA";
		}
		List<String> args = new ArrayList<>();
		args.add(pemicu.toLowerCase(Locale.ROOT));
		if (isian != null) {
			args.addAll(Arrays.asList(isian));
		}
		lahir(level, situs, predikat, 0, Fakta.Kebenaran.BENAR, args, null, Fakta.Rujuk.tempat(situs), Fakta.Rujuk.kata(args.get(0)));
	}

	/** Witnesses: resident bodies within 16 blocks of a subject player online here, else up to 4 seeded residents. */
	private static List<Warga> saksi(ServerLevel level, WargaData.Situs s, Gosip g, SplittableRandom r) {
		List<Warga> out = new ArrayList<>();
		WargaData data = WargaData.get(level.getServer());
		for (Fakta.Rujuk ref : g.subjek) {
			if (ref.jenis() == Fakta.Jenis.PEMAIN) {
				UUID uuid = data.pemain((int) ref.id());
				ServerPlayer player = uuid == null ? null : level.getServer().getPlayerList().getPlayer(uuid);
				if (player != null && player.level() == level) {
					for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class,
							player.getBoundingBox().inflate(HidupApi.SAKSI_JARAK), n -> n.isAlive())) {
						Warga w = WargaApi.dari(npc);
						if (w != null && w.hidup() && !out.contains(w)) {
							out.add(w);
						}
					}
				}
			} else if (ref.jenis() == Fakta.Jenis.WARGA) {
				Warga w = WargaApi.byId((int) ref.id());
				if (w != null && w.hidup() && !out.contains(w)) {
					out.add(w);
				}
			}
		}
		if (out.size() < 2) {
			List<Warga> pool = new ArrayList<>();
			for (Warga w : s.warga) {
				if (w.status == Warga.Status.HIDUP && !out.contains(w) && WargaApi.umur(w) >= 6) {
					pool.add(w);
				}
			}
			int want = Math.min(4, pool.size());
			for (int i = 0; i < want; i++) {
				out.add(pool.remove(r.nextInt(pool.size())));
			}
		}
		return out;
	}

	/** {@code w} learns gossip {@code g} with source {@code asal}; returns the stored copy. */
	private static Fakta beriTahu(Pengetahuan pk, WargaData data, Warga w, Gosip g, int nilai, Fakta.Asal asal, int yakin, long hari) {
		Predikat p = Predikat.dari(g.templat);
		float s = Lupa.kekuatanAwal(asal, p.valensi(), p, pk.sifat(w).c());
		Fakta copy = pk.simpan(w, g.fakta(nilai, asal, yakin, s, hari), hari);
		kenal(w, g.id);
		return copy;
	}

	/** Adds a gossip id to what {@code w} knows (12 kept, the oldest dropped). */
	static void kenal(Warga w, int id) {
		for (int x : w.gosip) {
			if (x == id) {
				return;
			}
		}
		int[] next = Arrays.copyOf(w.gosip, w.gosip.length + 1);
		next[w.gosip.length] = id;
		if (next.length > MAKS_WARGA) {
			next = Arrays.copyOfRange(next, next.length - MAKS_WARGA, next.length);
		}
		w.gosip = next;
	}

	static boolean tahu(Warga w, int id) {
		for (int x : w.gosip) {
			if (x == id) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== table

	/** The live gossips of a place, table order. */
	public static List<Gosip> daftar(MinecraftServer server, long situs) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		List<Gosip> out = new ArrayList<>();
		if (s != null) {
			for (int i = 0; i < s.gosip.size(); i++) {
				out.add(Gosip.muat(s.gosip.getCompoundOrEmpty(i)));
			}
		}
		return out;
	}

	public static @Nullable Gosip cari(MinecraftServer server, long situs, int id) {
		for (Gosip g : daftar(server, situs)) {
			if (g.id == id) {
				return g;
			}
		}
		return null;
	}

	private static void tulis(WargaData.Situs s, Gosip g, long hariDingin) {
		for (int i = 0; i < s.gosip.size(); i++) {
			if (s.gosip.getCompoundOrEmpty(i).getIntOr("i", -1) == g.id) {
				s.gosip.set(i, g.simpan(hariDingin));
				return;
			}
		}
		s.gosip.add(g.simpan(hariDingin));
	}

	/** Keeps the table under the cap ({@code min(64, kotaWarga.gosipMaks)}), dropping the coldest. */
	private static void trim(WargaData.Situs s) {
		int cap = Math.max(4, Math.min(MAKS_TEMPAT, KotaConfig.warga().gosipMaks));
		while (s.gosip.size() > cap) {
			int coldest = 0;
			int heat = Integer.MAX_VALUE;
			for (int i = 0; i < s.gosip.size(); i++) {
				int h = s.gosip.getCompoundOrEmpty(i).getByteOr("h", (byte) 0);
				if (h < heat) {
					heat = h;
					coldest = i;
				}
			}
			s.gosip.remove(coldest);
		}
	}

	// ================================================================== spreading

	/**
	 * One telling between two townsfolk ({@code dari} -> {@code ke}), at titik {@code titik} ({@code sumur}, {@code kedai},
	 * {@code pasar}, "" unknown): every gossip {@code dari} knows and {@code ke} does not hops with chance
	 * {@code 0.25 x heat/100 x multipliers} (42.5.4). Each hop may embellish (15 %: number blown up or truth BUMBU; a sly
	 * teller swaps subjects 30 %), the receiver's confidence is its trust in the teller times the teller's confidence less
	 * 15 % per hop, and a gossip about a player moves the receiver's impression by +-3 once. Returns the number of hops.
	 */
	public static int sebar(MinecraftServer server, long situs, Warga dari, Warga ke, String titik, SplittableRandom r) {
		if (!aktif() || dari == ke || dari.gosip.length == 0) {
			return 0;
		}
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null) {
			return 0;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		double m = pengali(pk, dari, ke, titik);
		int n = 0;
		for (int id : dari.gosip.clone()) {
			if (tahu(ke, id)) {
				continue;
			}
			Gosip g = cari(server, situs, id);
			if (g == null || r.nextDouble() >= PELUANG_SEBAR * g.panas / 100.0D * m) {
				continue;
			}
			if (ceritakan(pk, data, dari, ke, g, r, hari) != null) {
				n++;
			}
		}
		if (n > 0) {
			data.ubah();
		}
		return n;
	}

	/**
	 * {@code dari} tells {@code ke} gossip {@code g} now (chats call this directly for the step's fact). Returns the
	 * receiver's new copy, or null when {@code dari} no longer remembers it.
	 */
	static @Nullable Fakta ceritakan(Pengetahuan pk, WargaData data, Warga dari, Warga ke, Gosip g, SplittableRandom r, long hari) {
		Fakta milik = null;
		for (Fakta f : pk.ingat(dari, hari)) {
			if (f.gosipId() == g.id) {
				milik = f;
				break;
			}
		}
		if (milik == null) {
			return null;
		}
		pk.ganti(dari, milik, Lupa.diceritakan(milik, hari));
		Fakta copy = turunan(pk, dari, ke, milik, r, hari);
		Fakta stored = pk.simpan(ke, copy, hari);
		kenal(ke, g.id);
		kesanDariGosip(data, ke, stored, dari, hari);
		return stored;
	}

	/** The receiver's copy of a teller's fact: confidence, source, strength and the embellishment of one hop. */
	static Fakta turunan(Pengetahuan pk, Warga dari, Warga ke, Fakta milik, SplittableRandom r, long hari) {
		Kepribadian.Sifat sd = pk.sifat(dari);
		Fakta.Rujuk subjek = milik.subjek();
		Fakta.Rujuk objek = milik.objek();
		int nilai = milik.nilai();
		Fakta.Kebenaran benar = milik.kebenaran();
		double bumbu = PELUANG_BUMBU + (sd.h() < -30 ? (-sd.h() - 30) / 300.0D : 0.0D);
		if (r.nextDouble() < bumbu) {
			if (nilai > 0) {
				nilai = Math.max(nilai + 1, (int) Math.round(nilai * (1.5D + r.nextDouble())));
			}
			if (Kepribadian.watak(dari).equals("licik") && objek.jenis() != Fakta.Jenis.TIADA && r.nextDouble() < PELUANG_TUKAR_LICIK) {
				Fakta.Rujuk t = subjek;
				subjek = objek;
				objek = t;
			}
			if (benar == Fakta.Kebenaran.BENAR) {
				benar = Fakta.Kebenaran.BUMBU;
			}
		}
		Fakta.Asal asal = Fakta.Asal.dengar(dari.id);
		int trust = pk.kepercayaan(ke, asal);
		int yakin = (int) Math.round(trust * milik.yakin() / 100.0D * 0.85D);
		Predikat p = Predikat.dari(milik.predikat());
		float s = Lupa.kekuatanAwal(asal, milik.valensi(), p, pk.sifat(ke).c());
		return new Fakta(milik.predikat(), subjek, objek, nilai, benar, yakin, asal, milik.hariKejadian(), hari, s, 0, milik.valensi(),
				milik.gosipId());
	}

	/** A gossip about a player changes the receiver's impression by +-3 (once per gossip, 42.5.4 "Memudar"). */
	static void kesanDariGosip(WargaData data, Warga ke, Fakta f, Warga dari, long hari) {
		for (Fakta.Rujuk ref : List.of(f.subjek(), f.objek())) {
			if (ref.jenis() == Fakta.Jenis.PEMAIN && f.valensi() != 0) {
				UUID uuid = data.pemain((int) ref.id());
				if (uuid != null) {
					Ingatan told = dari.ingatanDari((int) ref.id());
					Ingatan.dengarGosip(data, ke, uuid, f.valensi() > 0, told != null && told.nama);
				}
				return;
			}
		}
	}

	/** The hop multiplier of 42.5.4: bond, place and habits. */
	static double pengali(Pengetahuan pk, Warga dari, Warga ke, String titik) {
		// 42A-2 habits: the well gossip and the peeper tell x3
		double m = dev.kultivator.dunia.kota42.warga.Kebiasaan.faktorGosip(dari);
		long bond = dari.hubunganDengan(ke.id);
		JenisHubungan j = bond == 0L ? null : JenisHubungan.jenis(bond);
		if (dari.pasangan == ke.id) {
			m *= 2.0D;
		} else if (j != null && j.id().equals("sahabat")) {
			m *= 2.0D;
		} else if (Pengetahuan.keluarga(dari, Fakta.Rujuk.warga(ke.id))) {
			m *= 1.5D;
		}
		m *= switch (titik == null ? "" : titik) {
			case "sumur" -> 3.0D;
			case "kedai", "kedai_teh", "minum_teh" -> 2.0D;
			case "pasar", "hari_pasar" -> 1.5D;
			default -> 1.0D;
		};
		if (Kepribadian.punya(dari, "gosip_sumur") || Kepribadian.punya(dari, "pengintip")) {
			m *= 2.0D;
		}
		if (Kepribadian.watak(ke).equals("curiga")) {
			m *= 0.5D;
		}
		return m;
	}

	// ================================================================== daily pass

	/**
	 * Hari Warga of one place: every gossip cools by 10 per day passed (cold ones leave the table and the townsfolk's id
	 * lists; the facts stay as plain memories), then every resident tells its bonds (off-screen spread, effects only), and a
	 * sly resident may invent a false rumour about an enemy (3 %).
	 */
	public static void harian(ServerLevel level, long situs, long hari) {
		MinecraftServer server = level.getServer();
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null || !aktif()) {
			return;
		}
		List<Integer> mati = new ArrayList<>();
		for (int i = s.gosip.size() - 1; i >= 0; i--) {
			CompoundTag t = s.gosip.getCompoundOrEmpty(i);
			long last = t.getLongOr("d", hari);
			if (hari <= last) {
				continue;
			}
			int heat = t.getByteOr("h", (byte) 0) - DINGIN_HARIAN * (int) Math.min(10L, hari - last);
			if (heat <= 0) {
				mati.add(t.getIntOr("i", -1));
				s.gosip.remove(i);
			} else {
				t.putByte("h", (byte) heat);
				t.putLong("d", hari);
			}
		}
		if (!mati.isEmpty()) {
			for (Warga w : s.warga) {
				if (w.gosip.length > 0) {
					w.gosip = Arrays.stream(w.gosip).filter(id -> !mati.contains(id)).toArray();
				}
			}
		}
		long seed = server.overworld().getSeed();
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			SplittableRandom r = Cakap.acak(seed, w.id, 0, hari, 0, "gosip_harian");
			if (w.gosip.length > 0) {
				for (long bond : w.hubungan) {
					Warga other = WargaApi.byId(JenisHubungan.idLain(bond));
					if (other != null && other.status == Warga.Status.HIDUP && data.situsDari(other.id) == situs) {
						sebar(server, situs, w, other, "", r);
					}
				}
				Warga spouse = w.pasangan == Warga.TANPA ? null : WargaApi.byId(w.pasangan);
				if (spouse != null && spouse.status == Warga.Status.HIDUP) {
					sebar(server, situs, w, spouse, "", r);
				}
				if (Kepribadian.punya(w, "gosip_sumur") && s.warga.size() > 1) {
					Warga at = s.warga.get(r.nextInt(s.warga.size()));
					sebar(server, situs, w, at, "sumur", r);
				}
			}
			if (Kepribadian.watak(w).equals("licik") && r.nextDouble() < PELUANG_PALSU) {
				for (long bond : w.hubungan) {
					JenisHubungan j = JenisHubungan.jenis(bond);
					if (j != null && j.id().equals("musuh")) {
						String fitnah = FITNAH.get(r.nextInt(FITNAH.size()));
						lahir(level, situs, fitnah, 0, Fakta.Kebenaran.PALSU, List.of(), w, Fakta.Rujuk.warga(JenisHubungan.idLain(bond)));
						break;
					}
				}
			}
		}
		data.ubah();
	}

	// ================================================================== reading

	/**
	 * The chain of tellers of a gossip as {@code holder} heard it (42.5.4 "Tanya siapa yang bilang"): warga ids from the
	 * holder back to the first who saw it (or a player index, negative = -1 - index), at most 16 links.
	 */
	public static List<Integer> rantai(MinecraftServer server, Warga holder, int gosipId) {
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		List<Integer> out = new ArrayList<>();
		Warga cur = holder;
		for (int guard = 0; guard < 16 && cur != null; guard++) {
			Fakta mine = null;
			for (Fakta f : pk.ingat(cur, hari)) {
				if (f.gosipId() == gosipId) {
					mine = f;
					break;
				}
			}
			if (mine == null) {
				break;
			}
			Fakta.Asal a = mine.asal();
			if (a.sumber() == Fakta.Sumber.DENGAR) {
				if (out.contains(a.dari())) {
					break;
				}
				out.add(a.dari());
				cur = WargaApi.byId(a.dari());
			} else {
				if (a.sumber() == Fakta.Sumber.PEMAIN) {
					out.add(-1 - a.dari());
				}
				break;
			}
		}
		return out;
	}

	/** True while gossip runs: the speech section and residents are on. */
	static boolean aktif() {
		return KotaConfig.bicara().aktif && WargaApi.aktif();
	}

	private GosipWarga() {
	}
}

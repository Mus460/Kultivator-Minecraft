package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.event.Expeditions;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.GildaMurid;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.PertemuanProvider;
import dev.kultivator.sekte.mandiri.api.TujuanProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The stage engine of a journey (DESIGN 39.6.2, 39.6.9): the timeline built once at the gate ({@link #susun}) and the
 * lazy resolution of every stage when it falls due ({@link #majukan}), always with the seed
 * {@code benih ^ index}. Stage kinds: {@code jalan} (a road segment of at most 500 blocks out), {@code tiba_kota},
 * {@code kunjungan} (one stop of the town visit), {@code menginap}, {@code tujuan} (the hunt, the ruins, the cave, the
 * guild errand ...), {@code pulang} (a road segment home) and {@code gerbang} (the hall gate).
 * <p>
 * Speeds in blocks per world second, rests included: on foot 3.0, on the flying sword 10 (Golden Core and above), the
 * Imperial road x1.15, a Thousand-Li Talisman x1/0.7; the capital's cloud journey (38) takes one world hour for
 * {@code ceil(6 + d/150)} NB. Rain makes a stage 10 % longer, a thunderstorm makes the party shelter an hour.
 * <p>
 * Each stage first asks the encounter providers of other modules (contract C39.C3, 39F's meetings with other sects; at
 * most one answered per journey), then resolves its own table. Nothing here touches the world; results go into the
 * journey's {@code hasil} and are applied by {@link Kepulangan}; diary entries and news are written at once.
 */
final class Tahap {

	static final String JALAN = "jalan";
	static final String TIBA_KOTA = "tiba_kota";
	static final String KUNJUNGAN = "kunjungan";
	static final String MENGINAP = "menginap";
	static final String TUJUAN = "tujuan";
	static final String PULANG = "pulang";
	static final String GERBANG = "gerbang";
	/** Held captive at a place (39.5.9) until the master comes or three days pass. */
	static final String TAWANAN = "tawanan";

	/** Longest road segment (blocks). */
	static final int SEGMEN = 500;
	/** Blocks per tick on foot, on the flying sword. */
	static final double LAJU_KAKI = 3.0D / 20.0D;
	static final double LAJU_PEDANG = 10.0D / 20.0D;
	static final int JAM = 1000;

	/** What a stage resolution knows. */
	record Konteks(MinecraftServer server, ServerLevel level, Rencana r, int index, CompoundTag t, RandomSource rnd, long hari, int jam) {

		UUID pemimpin() {
			return r.soul();
		}

		List<UUID> semua() {
			return r.rombongan();
		}

		CompoundTag anggota(UUID soul) {
			return r.anggota(soul);
		}

		CompoundTag pimpinan() {
			return r.anggota(r.soul());
		}

		int sifat() {
			return pimpinan().getIntOr("sifat", 0);
		}

		int band() {
			return pimpinan().getIntOr("band", 0);
		}

		BlockPos pos() {
			return BlockPos.of(t.getLongOr("pos", 0L));
		}

		long mulai() {
			return t.getLongOr("mulai", 0L);
		}

		long sampai() {
			return t.getLongOr("sampai", 0L);
		}

		double jamLama() {
			return Math.max(0L, sampai() - mulai()) / (double) JAM;
		}

		String namaPemimpin() {
			return pimpinan().getStringOr("nama", r.nama());
		}

		/** Name arguments of the town of this journey (two keys) or two empty strings. */
		List<String> kota() {
			return r.town() != 0L ? JalanIds.kota(r.town()) : List.of("", "");
		}
	}

	// ================================================================== the timeline

	/**
	 * Builds the plan of a journey for {@code party} (leader first) to {@code tujuan}, departing now; the leader's record
	 * (returned) carries the stages. Members get their own records from {@link Perjalanan#berangkat}.
	 */
	static Rencana susun(ServerLevel level, UUID owner, List<DiscipleEntity> party, JenisJalan jenis, TujuanProvider.Tujuan tujuan, long now) {
		DiscipleEntity leader = party.get(0);
		UUID soul = leader.soulId();
		CompoundTag tag = new CompoundTag();
		long seed = soul.getMostSignificantBits() ^ Long.rotateLeft(soul.getLeastSignificantBits(), 17) ^ now * 0x9E3779B97F4A7C15L;
		tag.putString(Rencana.ID, Long.toHexString(soul.getMostSignificantBits() ^ soul.getLeastSignificantBits() ^ now));
		tag.putString(Rencana.OWNER, owner.toString());
		tag.putString(Rencana.SOUL, soul.toString());
		tag.putString(Rencana.NAMA, leader.getName().getString());
		tag.putString(Rencana.JENIS, jenis.id());
		tag.put(Rencana.TUJUAN, Rencana.tujuanTag(tujuan));
		tag.putString(Rencana.DIM, level.dimension().identifier().toString());
		tag.putLong(Rencana.AULA, leader.blockPosition().asLong());
		tag.putLong(Rencana.BERANGKAT, now);
		tag.putLong(Rencana.BENIH, seed);
		ListTag rombongan = new ListTag();
		CompoundTag anggota = new CompoundTag();
		for (DiscipleEntity d : party) {
			rombongan.add(StringTag.valueOf(d.soulId().toString()));
			anggota.put(d.soulId().toString(), Watak.anggota(d));
		}
		tag.put(Rencana.ROMBONGAN, rombongan);
		tag.put(Rencana.ANGGOTA, anggota);
		Rencana r = new Rencana(tag);
		RandomSource rnd = RandomSource.create(seed);

		// way and speed
		BlockPos from = leader.blockPosition();
		BlockPos to = tujuan.pos();
		double d = Math.max(32.0D, Tujuan.jarak(from, to));
		String moda = Rencana.KAKI;
		double laju = LAJU_KAKI;
		boolean semuaInti = true;
		for (DiscipleEntity m : party) {
			semuaInti &= Tas.band(m.data()) >= 3;
		}
		if (semuaInti && !MandiriApi.kebijakan(leader).dilarang("pedang")) {
			moda = Rencana.PEDANG;
			laju = LAJU_PEDANG;
		}
		int sifat = r.anggota(soul).getIntOr("sifat", 0);
		long awan = (long) Math.ceil(dev.kultivator.sekte.mandiri.MandiriConfig.get().harga.awanDasar
				+ d / Math.max(1, dev.kultivator.sekte.mandiri.MandiriConfig.get().harga.awanPer));
		boolean manja = Watak.punya(sifat, Sifat.MALAS) || Watak.punya(sifat, Sifat.SOMBONG);
		if (manja && !Watak.punya(sifat, Sifat.LICIK) && tujuan.town() != 0L
				&& dev.kultivator.dunia.kota.Towns.isKotaBesar(level.getServer(), tujuan.town()) && r.sisaUang(soul) >= awan * 2L) {
			moda = Rencana.AWAN;
			r.ubahUang(soul, -awan);
		}
		tag.putString(Rencana.MODA, moda);
		if (r.anggota(soul).getBooleanOr("jimatLi", false)) {
			laju /= 0.7D;
		}
		if (level.isRainingAt(from.above(2))) {
			laju /= 1.10D;
		}

		ListTag stages = r.tahap();
		long t = now;
		// out
		t = jalan(level, stages, JALAN, from, to, d, laju, moda, t);
		// destination
		boolean kota = tujuan.town() != 0L || jenis == JenisJalan.BELANJA || jenis == JenisJalan.JUAL;
		if (kota) {
			stages.add(stage(TIBA_KOTA, t, t + 300L, KontrakKota.tiba(level, tujuan.town() != 0L ? tujuan.town() : to.asLong()), 0));
			t += 300L;
			int jamTiba = jamPada(level, t);
			for (CompoundTag stop : KunjunganKota.susun(level, r, t, jamTiba, rnd)) {
				stages.add(stop);
				t = stop.getLongOr("sampai", t);
			}
			if (jenis == JenisJalan.MISI_GILDA) {
				t = tujuanStages(stages, jenis, to, t, rnd);
			}
			int jamAkhir = jamPada(level, t);
			if (jamAkhir >= 18 || jamAkhir < 5) {
				long morning = jamKe(level, t, 6);
				stages.add(stage(MENGINAP, t, morning, to, 0));
				t = morning;
			}
		} else {
			t = tujuanStages(stages, jenis, to, t, rnd);
		}
		// home
		t = jalan(level, stages, PULANG, to, from, d, laju, moda, t);
		stages.add(stage(GERBANG, t, t + 40L, from, 0));
		t += 40L;
		r.setPulang(t);
		r.setBerikut(0);
		return r;
	}

	/** Appends the road home from {@code from} starting at {@code t} (after captivity, a ransom, a talisman) and the gate. */
	static void tambahPulang(ServerLevel level, Rencana r, BlockPos from, long t) {
		BlockPos home = r.aula();
		double d = Math.max(32.0D, Tujuan.jarak(from, home));
		String moda = r.moda();
		double laju = Rencana.PEDANG.equals(moda) ? LAJU_PEDANG : LAJU_KAKI;
		ListTag stages = r.tahap();
		t = jalan(level, stages, PULANG, from, home, d, laju, moda, t);
		stages.add(stage(GERBANG, t, t + 40L, home, 0));
		r.setPulang(t + 40L);
	}

	/** Road segments from {@code a} to {@code b}: at most 500 blocks each, the road class at the middle of each. */
	private static long jalan(ServerLevel level, ListTag stages, String kind, BlockPos a, BlockPos b, double d, double laju, String moda,
			long t) {
		if (Rencana.AWAN.equals(moda)) {
			stages.add(stage(kind, t, t + JAM, b, 0));
			return t + JAM;
		}
		int n = Math.max(1, (int) Math.ceil(d / SEGMEN));
		for (int i = 0; i < n; i++) {
			double f0 = (double) i / n;
			double f1 = (double) (i + 1) / n;
			int mx = (int) Mth.lerp((f0 + f1) / 2.0D, a.getX(), b.getX());
			int mz = (int) Mth.lerp((f0 + f1) / 2.0D, a.getZ(), b.getZ());
			int kelas = Rencana.PEDANG.equals(moda) ? KontrakKota.LIAR : KontrakKota.kelasJalan(level, mx, mz);
			double v = laju * (Rencana.PEDANG.equals(moda) ? 1.0D : KontrakKota.lajuJalan(kelas));
			long len = Math.max(40L, Math.round(d / n / v));
			int ex = (int) Mth.lerp(f1, a.getX(), b.getX());
			int ez = (int) Mth.lerp(f1, a.getZ(), b.getZ());
			CompoundTag s = stage(kind, t, t + len, new BlockPos(ex, b.getY(), ez), kelas);
			s.putLong("dari", new BlockPos((int) Mth.lerp(f0, a.getX(), b.getX()), a.getY(), (int) Mth.lerp(f0, a.getZ(), b.getZ())).asLong());
			s.putInt("n", i);
			stages.add(s);
			t += len;
		}
		return t;
	}

	/** The 2-4 stages at a destination: {@code lamaJam} hours of the journey kind split by the seed. */
	private static long tujuanStages(ListTag stages, JenisJalan jenis, BlockPos at, long t, RandomSource rnd) {
		int hours = jenis.lamaJamMin() + (jenis.lamaJamMaks() > jenis.lamaJamMin() ? rnd.nextInt(jenis.lamaJamMaks() - jenis.lamaJamMin() + 1) : 0);
		int parts = 2 + rnd.nextInt(3);
		long each = Math.max(JAM, (long) hours * JAM / parts);
		for (int i = 0; i < parts; i++) {
			CompoundTag s = stage(TUJUAN, t, t + each, at, 0);
			s.putInt("n", i);
			s.putBoolean("akhir", i == parts - 1);
			stages.add(s);
			t += each;
		}
		return t;
	}

	static CompoundTag stage(String kind, long mulai, long sampai, BlockPos pos, int kelas) {
		CompoundTag s = new CompoundTag();
		s.putString("jenis", kind);
		s.putLong("mulai", mulai);
		s.putLong("sampai", sampai);
		s.putLong("pos", pos.asLong());
		s.putInt("kelas", kelas);
		return s;
	}

	// ================================================================== clock of future times

	/** The world hour at game time {@code t} (the clock offset of now applied). */
	static int jamPada(Level level, long t) {
		long clock = t + (level.getOverworldClockTime() - level.getGameTime());
		return (int) Math.floorMod(Math.floorMod(clock, 24000L) / 1000L + 6L, 24L);
	}

	/** The world day at game time {@code t}. */
	static long hariPada(Level level, long t) {
		long clock = t + (level.getOverworldClockTime() - level.getGameTime());
		return Math.max(0L, Math.floorDiv(clock, 24000L));
	}

	/** The first game time at or after {@code t} whose world hour is {@code jam}. */
	static long jamKe(Level level, long t, int jam) {
		int now = jamPada(level, t);
		int ahead = Math.floorMod(jam - now, 24);
		if (ahead == 0) {
			ahead = 24;
		}
		long clock = t + (level.getOverworldClockTime() - level.getGameTime());
		long intoHour = Math.floorMod(clock, 1000L);
		return t + (long) ahead * JAM - intoHour;
	}

	// ================================================================== resolution

	/** Resolves every stage of {@code r} that is due at {@code now}; true when something changed. */
	static boolean majukan(MinecraftServer server, Rencana r, long now) {
		ServerLevel level = level(server, r);
		if (level == null) {
			return false;
		}
		boolean changed = false;
		int guard = 0;
		while (!r.selesai() && guard++ < 64) {
			int i = r.berikut();
			CompoundTag t = r.tahap(i);
			if (t.getLongOr("sampai", Long.MAX_VALUE) > now) {
				break;
			}
			r.setBerikut(i + 1);
			try {
				selesaikan(server, level, r, i, t);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Mandiri journey {} stage {} failed", r.id(), i, exception);
			}
			changed = true;
		}
		return changed;
	}

	static @Nullable ServerLevel level(MinecraftServer server, Rencana r) {
		Identifier id = Identifier.tryParse(r.dim());
		return id == null ? server.overworld() : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	private static void selesaikan(MinecraftServer server, ServerLevel level, Rencana r, int i, CompoundTag t) {
		long time = t.getLongOr("sampai", level.getGameTime());
		Konteks k = new Konteks(server, level, r, i, t, RandomSource.create(r.benih() ^ (i + 1L) * 0xD1B54A32D192ED03L),
				hariPada(level, time), jamPada(level, time));
		String kind = t.getStringOr("jenis", JALAN);
		switch (kind) {
			case JALAN, PULANG -> {
				r.tambahJam(k.jamLama() * 0.8D);
				if (!pertemuan(k, JALAN, null)) {
					PeristiwaJalan.selesaikan(k);
				}
			}
			case TIBA_KOTA -> {
				r.tambahJam(k.jamLama() * 0.8D);
				List<String> args = new ArrayList<>(k.kota());
				args.add(k.namaPemimpin());
				r.kisah(JalanIds.k("kisah.tiba_kota." + k.rnd().nextInt(4)), args);
				// hurt on the road: the healer of the town first (39.6.6 Balai Tabib)
				if (luka(r) && !adaStop(r, KunjunganKota.Stop.TABIB.id)) {
					CompoundTag healer = stage(KUNJUNGAN, k.sampai(), k.sampai() + 1400L, r.tujuan().pos(), 0);
					healer.putString("stop", KunjunganKota.Stop.TABIB.id);
					r.sisip(k.index(), healer);
					Kepulangan.aturPulang(server, r);
				}
			}
			case KUNJUNGAN -> {
				r.tambahJam(k.jamLama() * 0.6D);
				String stop = t.getStringOr("stop", "");
				if (!pertemuan(k, KunjunganKota.jenisPertemuan(stop), r.tujuan().situs())) {
					KunjunganKota.selesaikan(k, stop);
				}
			}
			case MENGINAP -> {
				r.tambahJam(k.jamLama() * 0.5D);
				KunjunganKota.menginap(k);
			}
			case TUJUAN -> {
				if (!pertemuan(k, jenisPertemuanTujuan(r.jenis()), r.tujuan().situs())) {
					tujuan(k);
				}
			}
			case TAWANAN -> Tawanan.lolos(k);
			case GERBANG -> r.kisah(JalanIds.k("kisah.gerbang." + (r.sukses() ? "baik" : "lelah") + "." + k.rnd().nextInt(3)),
					List.of(k.namaPemimpin()));
			default -> {
			}
		}
		simpanCermin(server, r);
	}

	private static boolean luka(Rencana r) {
		for (UUID soul : r.rombongan()) {
			if (r.luka(soul) == JenisLuka.BERAT || r.luka(soul) == JenisLuka.TERLUKA) {
				return true;
			}
		}
		return false;
	}

	private static boolean adaStop(Rencana r, String stop) {
		for (int i = 0; i < r.jumlahTahap(); i++) {
			if (stop.equals(r.tahap(i).getStringOr("stop", ""))) {
				return true;
			}
		}
		return false;
	}

	/** The C39.C3 stage name of a destination stage. */
	static String jenisPertemuanTujuan(JenisJalan jenis) {
		return switch (jenis) {
			case BERBURU -> "berburu";
			case RERUNTUHAN -> "reruntuhan";
			case ALAM_RAHASIA, GUA_PERTAPA -> "alam";
			case DESA -> "desa";
			case SEKTE_TAMU -> "gerbang_sekte";
			case MISI_GILDA -> "gilda";
			default -> JALAN;
		};
	}

	// ================================================================== C39.C3

	/** Asks the encounter providers (39F) for this stage; true when one answered (its result replaces the stage's table). */
	static boolean pertemuan(Konteks k, String tahap, @Nullable String situs) {
		Rencana r = k.r();
		if (r.pertemuan() >= 1) {
			return false;
		}
		List<PertemuanProvider> providers = MandiriApi.pertemuanProviders();
		if (providers.isEmpty()) {
			return false;
		}
		boolean terlihat = Jelmaan.dekatPemain(k.level(), k.pos(), 128.0D) || Jelmaan.tampil(r.soul());
		PertemuanProvider.Pertemuan p = new PertemuanProvider.Pertemuan(k.server(), r.owner(), r.soul(), r.rombongan(), r.jenis(), tahap, situs,
				k.pos(), k.hari(), r.benih() ^ k.index(), terlihat);
		for (PertemuanProvider provider : providers) {
			PertemuanProvider.HasilPertemuan h;
			try {
				h = provider.coba(p);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("A Mandiri encounter provider failed", exception);
				continue;
			}
			if (h == null) {
				continue;
			}
			r.tambahPertemuan();
			if (!h.kunciKisah().isEmpty()) {
				r.kisah(h.kunciKisah(), h.arg());
			}
			if (h.luka() != JenisLuka.TIDAK) {
				if (h.luka() == JenisLuka.TERTAWAN) {
					Tawanan.tangkap(k, "seteru", k.pos());
				} else {
					r.lukai(r.soul(), h.luka());
				}
			}
			if (h.deltaDompet() != 0L) {
				if (h.deltaDompet() > 0L) {
					r.tambahPendapatan(r.soul(), h.deltaDompet());
				} else {
					r.ubahUang(r.soul(), -Math.min(r.sisaUang(r.soul()), -h.deltaDompet()));
				}
			}
			if (h.deltaTahapTick() != 0) {
				r.geser(k.index() + 1 < r.jumlahTahap() ? k.index() + 1 : k.index(), h.deltaTahapTick());
				Kepulangan.aturPulang(k.server(), r);
			}
			for (String kind : h.kenangan()) {
				r.kenangan(kind);
			}
			if (h.entri() != null) {
				MandiriApi.catat(r.owner(), r.soul(), h.entri());
			}
			return true;
		}
		return false;
	}

	// ================================================================== destination stages (39.6.9)

	/** {@code clamp(0.45 + 0.12 (P - (tier - 1)) + 0.05 Inti+ + 0.03 per close friend + talisman, 0.10, 0.95)}. */
	static double peluang(Rencana r, int tier, double jimat) {
		int sahabat = Math.max(0, r.rombongan().size() - 1);
		double p = 0.45D + 0.12D * (r.kekuatan() - (tier - 1)) + (r.adaInti() ? 0.05D : 0.0D) + 0.03D * sahabat + jimat;
		return Mth.clamp(p, 0.10D, 0.95D);
	}

	/** The tier of the destination as the party meets it (the kind's own risk or the site's). */
	static int tier(Rencana r) {
		int base = r.jenis().risikoDasar();
		return Math.max(0, base >= 0 ? Math.max(base, r.jenis() == JenisJalan.MISI_GILDA ? 1 : 0) : r.tujuan().tier());
	}

	private static void tujuan(Konteks k) {
		Rencana r = k.r();
		JenisJalan jenis = r.jenis();
		boolean akhir = k.t().getBooleanOr("akhir", false);
		int tier = tier(r);
		double jimat = k.pimpinan().getBooleanOr("jimatPerisai", false) ? 0.02D : 0.0D;
		double chance = peluang(r, tier, jimat);
		boolean ok = k.rnd().nextDouble() < chance;
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		args.add(String.valueOf(Math.round(chance * 100.0D)));
		double kFaktor = switch (jenis) {
			case GUA_PERTAPA -> 2.0D * faktorSitus(r);
			case ALAM_RAHASIA -> 1.2D;
			default -> 0.8D;
		};
		r.tambahJam(k.jamLama() * kFaktor);
		String base = "kisah." + jenis.id() + "." + k.t().getIntOr("n", 0) % 3 + "." + (ok ? "ok" : "gagal");
		switch (jenis) {
			case BERBURU -> {
				if (ok) {
					int core = Math.max(1, Math.min(4, tier));
					r.barang(r.soul(), "kultivator:beast_core@" + core, 1, DaftarBelanja.KE_TAS, 0L, false);
					args.add(JalanIds.kunciBarang("kultivator:beast_core@" + core));
					r.kenangan("mandiri.buru");
					if (akhir) {
						Rampasan.ambil(k, Expeditions.LOOT_HUNT, tier, 2);
					}
				}
			}
			case RERUNTUHAN -> {
				if (ok && (akhir || k.rnd().nextFloat() < 0.4F)) {
					Rampasan.ambil(k, Expeditions.LOOT_SECRET, tier, akhir ? 2 : 1);
				}
			}
			case ALAM_RAHASIA -> {
				if (ok && akhir) {
					Rampasan.ambil(k, Expeditions.LOOT_SECRET, tier + 1, 2);
					r.barang(r.soul(), "kultivator:heavenvoid_fragment", 1, DaftarBelanja.KE_TAS, 0L, false);
				}
			}
			case GUA_PERTAPA -> {
				// seclusion far away: the K above is the reward; a calm cave also lowers temptation
				tambahIblis(r, r.soul(), ok ? -2 : 0);
			}
			case DESA -> {
				if (ok) {
					r.kenangan("mandiri.desa");
					tambahAngka(r, "menolong", r.soul(), 1);
					if (akhir && k.rnd().nextFloat() < 0.03F) {
						r.hasil().putBoolean("calon", true);
						args.add("calon");
					}
					if (akhir) {
						Rampasan.ambil(k, Expeditions.LOOT_HERBS, Math.max(1, tier), 1);
					}
				}
			}
			case SEKTE_TAMU -> {
				String sect = Tujuan.sekteDi(r.tujuan().pos());
				args.add(namaSekte(sect));
				if (sect != null) {
					if (akhir) {
						r.hasil().putString("sekte", sect);
						r.hasil().putBoolean("sekteSekutu", SekteApi.diplomasi().stance(r.owner(), sect) == Sikap.SEKUTU);
					}
				}
				if (ok) {
					r.kenangan("mandiri.sekte_tamu");
				}
			}
			case MISI_GILDA -> {
				GildaMurid.MisiMurid misi = misi(r);
				args.add(misi != null ? misi.kunciJudul() : JalanIds.k("misi.umum"));
				if (misi != null) {
					if (akhir) {
						GildaMurid gilda = MandiriApi.gilda();
						long pay = gilda.upah(misi, ok);
						if (pay > 0L) {
							r.tambahPendapatan(r.soul(), pay);
							args.add(String.valueOf(pay));
						}
						if (ok) {
							r.hasil().putInt("misiSukses", 1);
							r.kenangan("mandiri.gilda");
						}
					}
				}
			}
			case ZIARAH -> {
				tambahIblis(r, r.soul(), akhir ? -5 : -1);
				r.kenangan("mandiri.ziarah");
				ok = true;
			}
			case BERKELANA -> {
				if (ok) {
					tambahPaham(r, r.soul(), 0.5D);
				}
			}
			default -> {
			}
		}
		r.kisah(JalanIds.k(base), args);
		if (ok) {
			if (akhir || k.rnd().nextFloat() < 0.35F) {
				Kesempatan.coba(k, jenis == JenisJalan.ALAM_RAHASIA ? 0.20D : 0.10D);
			}
		} else if (jenis != JenisJalan.ZIARAH && jenis != JenisJalan.GUA_PERTAPA) {
			gagal(k, jenis == JenisJalan.SEKTE_TAMU && !r.hasil().getBooleanOr("sekteSekutu", false), "tujuan");
			if (akhir) {
				r.setSukses(false);
			}
		}
	}

	/** A sect's name as a stored argument: its lang key when it has one, else its text; an unknown sect's placeholder. */
	static String namaSekte(@Nullable String sect) {
		net.minecraft.network.chat.Component name = sect == null ? null : SekteApi.diplomasi().name(sect);
		if (name == null) {
			return JalanIds.k("sekte_asing");
		}
		if (name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr && tr.getArgs().length == 0) {
			return tr.getKey();
		}
		String text = name.getString();
		return text.isBlank() ? JalanIds.k("sekte_asing") : text;
	}

	/** The guild errand this journey took (the first fitting one of the town's board), or null. */
	static @Nullable GildaMurid.MisiMurid misi(Rencana r) {
		int pangkat = Math.min(r.anggota(r.soul()).getIntOr("lencana", 0), pangkatMaks(r.anggota(r.soul()).getIntOr("jenjang", 0)));
		List<GildaMurid.MisiMurid> board;
		try {
			board = MandiriApi.gilda().papan(r.town() != 0L ? r.town() : r.tujuan().pos().asLong(), pangkat);
		} catch (RuntimeException e) {
			return null;
		}
		if (board.isEmpty()) {
			return null;
		}
		return board.get(Math.floorMod((int) r.benih(), board.size()));
	}

	/** Highest guild rank by sect rank (39.6.8): Outer C, Inner B, Core A, Elder S; registered disciples E. */
	static int pangkatMaks(int sectRank) {
		return switch (sectRank) {
			case 0 -> 0;
			case 1 -> 2;
			case 2 -> 3;
			case 3 -> 4;
			default -> 5;
		};
	}

	/** The cave's factor: 1.0, a spirit vein 1.3, a spring 1.2, the thunder peak 1.15 (38GU's sites). */
	static double faktorSitus(Rencana r) {
		String kind = Tujuan.jenisSitus(r.tujuan());
		if (kind == null) {
			return 1.0D;
		}
		return switch (kind) {
			case "gua/urat_roh" -> 1.3D;
			case "situs/mata_air_roh" -> 1.2D;
			case "situs/puncak_petir" -> 1.15D;
			default -> 1.0D;
		};
	}

	// ================================================================== failure (39.5.9)

	/**
	 * A failed stage: 70 % wounded, 25 % badly wounded, 5 % captured where {@code bolehTawan} (bandits, rival sects, a raid).
	 * A Shield Talisman turns a bad wound into a light one once; NEKAT never dies here ({@code bolehGugur} is not used by
	 * 39C).
	 */
	static void gagal(Konteks k, boolean bolehTawan, String sebab) {
		Rencana r = k.r();
		float roll = k.rnd().nextFloat();
		JenisLuka luka = roll < 0.70F ? JenisLuka.TERLUKA : roll < 0.95F || !bolehTawan ? JenisLuka.BERAT : JenisLuka.TERTAWAN;
		if (luka == JenisLuka.TERTAWAN) {
			Tawanan.tangkap(k, sebab, k.pos());
			return;
		}
		if (luka == JenisLuka.BERAT && k.pimpinan().getBooleanOr("jimatPerisai", false) && !r.hasil().getBooleanOr("perisaiDipakai", false)) {
			r.hasil().putBoolean("perisaiDipakai", true);
			luka = JenisLuka.TERLUKA;
			r.kisah(JalanIds.k("kisah.perisai"), List.of(k.namaPemimpin()));
		}
		for (UUID soul : r.rombongan()) {
			if (soul.equals(r.soul()) || k.rnd().nextFloat() < 0.5F) {
				r.lukai(soul, luka);
			}
		}
	}

	// ================================================================== shared result helpers

	static void tambahIblis(Rencana r, UUID soul, int delta) {
		tambahAngka(r, "iblis", soul, delta);
	}

	static void tambahPaham(Rencana r, UUID soul, double delta) {
		CompoundTag u = Rencana.child(r.hasil(), "paham");
		u.putDouble(soul.toString(), u.getDoubleOr(soul.toString(), 0.0D) + delta);
	}

	static void tambahAngka(Rencana r, String key, UUID soul, int delta) {
		CompoundTag u = Rencana.child(r.hasil(), key);
		u.putInt(soul.toString(), u.getIntOr(soul.toString(), 0) + delta);
	}

	/** Writes a diary entry of the stage's time for {@code soul}. */
	static void catat(Konteks k, UUID soul, String jenis, String kunci, List<String> args, EntriHarian.Suasana suasana, boolean penting) {
		MandiriApi.catat(k.r().owner(), soul, new EntriHarian(k.hari(), k.jam(), jenis, kunci, args, suasana, penting));
	}

	/** Writes the board's mirror {@code MandiriData.jalan.<soul>} (summary only). */
	static void simpanCermin(MinecraftServer server, Rencana r) {
		CompoundTag mirror = new CompoundTag();
		mirror.putString("id", r.id());
		mirror.putString("jenis", r.jenis().id());
		mirror.putString("tujuan", r.tujuan().id());
		mirror.putLong("town", r.town());
		mirror.putLong("berangkat", r.berangkat());
		mirror.putLong("pulang", r.pulang());
		mirror.putInt("tahap", r.berikut());
		mirror.putInt("dari", r.jumlahTahap());
		mirror.putBoolean("tertawan", r.tertawan());
		ListTag party = new ListTag();
		for (UUID soul : r.rombongan()) {
			party.add(StringTag.valueOf(soul.toString()));
		}
		mirror.put("rombongan", party);
		mirror.put("kisah", r.hasil().getListOrEmpty("tahap").copy());
		CompoundTag hall = MandiriApi.aula(server, r.owner(), MandiriIds.A_JALAN);
		for (UUID soul : r.rombongan()) {
			hall.put(soul.toString(), mirror.copy());
		}
		MandiriApi.tandai(server);
	}

	/** Drops the mirror of the party. */
	static void hapusCermin(MinecraftServer server, UUID owner, List<UUID> souls) {
		CompoundTag hall = MandiriApi.aula(server, owner, MandiriIds.A_JALAN);
		for (UUID soul : souls) {
			hall.remove(soul.toString());
		}
		MandiriApi.tandai(server);
	}

	/** The live {@code core.away} record of {@code soul} (reason {@code mandiri_jalan}), or null. */
	static @Nullable CompoundTag rekamAway(UUID owner, UUID soul) {
		ListTag away = Halls.section(owner, "core").getListOrEmpty("away");
		for (Tag tag : away) {
			if (tag instanceof CompoundTag rec && MandiriIds.AWAY_JALAN.equals(rec.getStringOr("reason", ""))
					&& soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				return rec;
			}
		}
		return null;
	}

	/** True for a path whose trips are long (the planner already weighs them; used for the visible packing time). */
	static boolean petualang(@Nullable JalanHidup jalan) {
		return jalan == JalanHidup.PETUALANG || jalan == JalanHidup.PEMBURU;
	}

	/** Caution factor of road events: careful parties meet half the trouble, the reckless half again as much. */
	static double faktorHati(Kehatian k) {
		return switch (k) {
			case AMAN -> 0.6D;
			case WAJAR -> 1.0D;
			case BERANI -> 1.2D;
			case NEKAT -> 1.5D;
		};
	}

	private Tahap() {
	}
}

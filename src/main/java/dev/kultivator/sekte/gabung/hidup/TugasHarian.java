package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The daily duties of DESIGN 41.6.4: eight chores at real titik of the own compound, each a small hands-on task with no
 * block change (the bucket, the broom, the fan are clips and props of the player, the plots and troughs are particles
 * over existing titik). A Pelayan or Tercatat owes one a day (a missed day is noted by the Outer Elder: violation +1);
 * everyone may do up to {@link #PER_HARI} a day, each chore once. A chore pays its PK x the class factor {@code kf}
 * (x1.5 for the sect's own style), Teacher bond +1 and +1 attitude of the elder whose office it serves.
 * <ul>
 * <li>{@code pikul_air}: 8 trips with the yoke bucket (clip {@code pikul_air} 306, prop 152) from {@code kerja:sumur} to
 * {@code kerja:tong} (Wang Lin carried water for years).</li>
 * <li>{@code sapu}: sweep the 6 leaf heaps around {@code kerja:sapu} within 90 s.</li>
 * <li>{@code kebun}: water 4 herb plots at {@code kerja:cangkul} (crouch 3 s at each).</li>
 * <li>{@code tungku}: keep the furnace at {@code kerja:kuali} alive for 60 s: each time the fire sinks (a puff of smoke),
 * fan it (crouch within 1.5 s); 12 of 20 (clip {@code kipas_tungku} 307, prop 154).</li>
 * <li>{@code kitab}: copy five characters of the Thousand Character Classic at {@code meja_tulis} in the order shown.</li>
 * <li>{@code kandang}: feed 3 animals of the pen ({@code kandang}); without animals, fill 3 troughs.</li>
 * <li>{@code pagi}: morning drill at {@code latihan}, 06:00-08:00: horse stance, crane leaps, the turning step.</li>
 * <li>{@code jaga}: night watch at {@code jaga}, 18:00-24:00: 120 s at the post; a scout may come, beat it.</li>
 * </ul>
 * Sessions are runtime state (one per player); progress is per world day in {@code Keanggotaan.tugasHari/tugasJumlah} and
 * the day's done set in {@code bagian("hidup").tugas_selesai}.
 */
public final class TugasHarian {

	/** One chore: id, base PK, the titik it needs, the styles that pay x1.5, the elder office it pleases, time window. */
	public enum Tugas {
		PIKUL_AIR("pikul_air", 8, TitikGabung.KERJA_SUMUR, null, null, Jabatan.TETUA_LUAR, -1, -1, 300, true, Pangkat.PELAYAN),
		SAPU("sapu", 5, "kerja:sapu", null, null, Jabatan.TETUA_LUAR, -1, -1, 90, true, Pangkat.PELAYAN),
		KEBUN("kebun", 8, "kerja:cangkul", Corak.ALKIMIA, null, Jabatan.TETUA_OBAT, -1, -1, 120, true, Pangkat.PELAYAN),
		TUNGKU("tungku", 10, "kerja:kuali", Corak.ALKIMIA, Corak.DARAH, Jabatan.TETUA_OBAT, -1, -1, 75, true, Pangkat.PELAYAN),
		KITAB("kitab", 10, "meja_tulis", Corak.FORMASI, null, Jabatan.TETUA_AJAR, -1, -1, 60, true, Pangkat.PELAYAN),
		KANDANG("kandang", 8, TitikGabung.KANDANG, Corak.BINATANG, null, Jabatan.TETUA_PUSAKA, -1, -1, 120, true, Pangkat.PELAYAN),
		PAGI("pagi", 6, "latihan", Corak.PEDANG, Corak.TUBUH, Jabatan.TETUA_AGUNG, 0, 2000, 90, true, Pangkat.PELAYAN),
		JAGA("jaga", 12, "jaga", Corak.BAYANGAN, null, Jabatan.TETUA_HUKUM, 12000, 18000, 240, true, Pangkat.PELAYAN),
		/** 41.6.2 "Jaga gerbang": a 5-minute shift at the compound gate, 10-30 PK by class (+ foes beaten), once a day. */
		GERBANG("gerbang", 10, TitikGabung.GERBANG_KOMPLEKS, null, null, Jabatan.TETUA_LUAR, -1, -1, 360, false, Pangkat.TERCATAT),
		/** 41.6.2 "Mengajar": an elder lectures the disciples in the main hall for 120 s, 30 PK x kf, once a day. */
		MENGAJAR("mengajar", 30, TitikGabung.AULA, null, null, Jabatan.TETUA_AJAR, -1, -1, 180, false, Pangkat.TETUA);

		private final String id;
		private final int pk;
		private final String titik;
		private final @Nullable Corak corak;
		private final @Nullable Corak corak2;
		private final Jabatan jabatan;
		private final int jamMulai;
		private final int jamAkhir;
		private final int detikMaks;
		private final boolean harian;
		private final Pangkat pangkatMin;

		Tugas(String id, int pk, String titik, @Nullable Corak corak, @Nullable Corak corak2, Jabatan jabatan, int jamMulai, int jamAkhir,
				int detikMaks, boolean harian, Pangkat pangkatMin) {
			this.id = id;
			this.pk = pk;
			this.titik = titik;
			this.corak = corak;
			this.corak2 = corak2;
			this.jabatan = jabatan;
			this.jamMulai = jamMulai;
			this.jamAkhir = jamAkhir;
			this.detikMaks = detikMaks;
			this.harian = harian;
			this.pangkatMin = pangkatMin;
		}

		/** True for the eight daily chores (the 3-a-day limit, the servant's duty); false for the gate shift and the lecture. */
		public boolean harian() {
			return harian;
		}

		public Pangkat pangkatMin() {
			return pangkatMin;
		}

		public String id() {
			return id;
		}

		/** Base PK before {@code kf} and the style bonus. */
		public int pk() {
			return pk;
		}

		public String titik() {
			return titik;
		}

		public boolean corakKhas(Corak c) {
			return c != null && (c == corak || c == corak2);
		}

		/** True at time of day {@code jam} (0 = 06:00); always true without a window. */
		public boolean jamBoleh(long jam) {
			return jamMulai < 0 || jam >= jamMulai && jam < jamAkhir;
		}

		public MutableComponent nama() {
			return Component.translatable("kultivator.gabung.tugas." + id);
		}

		public MutableComponent teks() {
			return Component.translatable("kultivator.gabung.tugas." + id + ".teks");
		}

		public static @Nullable Tugas byId(String id) {
			for (Tugas t : values()) {
				if (t.id.equals(id)) {
					return t;
				}
			}
			return null;
		}
	}

	/** Chores per day (41.6.2 "3/hari"). */
	public static final int PER_HARI = 3;
	public static final double PENGALI_CORAK = 1.5D;
	static final String K_SELESAI = "tugas_selesai";
	static final String K_SELESAI_HARI = "tugas_selesai_hari";
	static final String K_LALAI = "tugas_lalai_hari";
	/** The Thousand Character Classic (6th century, public domain): the copying pool of {@code kitab}. */
	static final String AKSARA = "天地玄黃宇宙洪荒日月盈昃辰宿列張寒來暑往秋收冬藏";
	static final int KITAB_PANJANG = 5;
	static final int TUNGKU_ISYARAT = 20;
	static final int TUNGKU_LULUS = 12;
	static final int PIKUL_KALI = 8;
	static final int SAPU_TITIK = 6;
	static final int KEBUN_PETAK = 4;
	static final int KANDANG_JUMLAH = 3;
	static final int JAGA_DETIK = 120;
	static final int JAGA_BONUS = 4;
	static final int GERBANG_DETIK = 300;
	static final int GERBANG_PER_MUSUH = 5;
	static final int GERBANG_MUSUH_MAKS = 20;
	static final int MENGAJAR_DETIK = 120;
	static final int MENGAJAR_BARIS = 6;

	/** A running chore (runtime). */
	static final class Sesi {
		final Tugas tugas;
		final String sekte;
		final long mulai;
		final BlockPos pusat;
		final List<BlockPos> titik = new ArrayList<>();
		final Set<Integer> selesai = new HashSet<>();
		final Set<UUID> diberi = new HashSet<>();
		int langkah;
		int hitung;
		int detik;
		int salah;
		boolean bawa;
		boolean jongkokLalu;
		boolean darat = true;
		float yawLalu;
		float putar;
		long isyarat = -1L;
		boolean isyaratKena;
		@Nullable UUID pengintai;
		boolean pengintaiKalah;
		int pengintaiDetik = -1;
		String urutan = "";
		String acak = "";

		Sesi(Tugas tugas, String sekte, long mulai, BlockPos pusat) {
			this.tugas = tugas;
			this.sekte = sekte;
			this.mulai = mulai;
			this.pusat = pusat;
		}
	}

	private static final Map<UUID, Sesi> SESI = new ConcurrentHashMap<>();
	/** Titik cache: "situs|jenis" -> (tick, list). */
	private static final Map<String, Object[]> CACHE = new HashMap<>();

	static void reset() {
		SESI.clear();
		CACHE.clear();
	}

	static boolean sibuk(ServerPlayer player) {
		return SESI.containsKey(player.getUUID());
	}

	// ================================================================== titik of the own compound

	/** The compound site the player stands in, when it is {@code sekte}'s; else -1. */
	static long situs(ServerPlayer player, String sekte) {
		TempatInfo info = TempatApi.tempatDi(player.level(), player.blockPosition());
		if (info == null) {
			return -1L;
		}
		String s = KompleksApi.sekteDi(info.situsId());
		return sekte.equals(s) ? info.situsId() : -1L;
	}

	/** Every titik of kind {@code jenis} of compound site {@code situs} (loaded pieces), cached 10 s. */
	static List<Titik> semuaTitik(ServerLevel level, long situs, String jenis) {
		String key = situs + "|" + jenis;
		Object[] hit = CACHE.get(key);
		long now = level.getGameTime();
		if (hit != null && now - (long) hit[0] < 200L) {
			@SuppressWarnings("unchecked")
			List<Titik> list = (List<Titik>) hit[1];
			return list;
		}
		List<Titik> list = TempatApi.titik(level, situs, jenis);
		CACHE.put(key, new Object[]{now, list});
		return list;
	}

	/** The nearest titik {@code jenis} of the own compound within {@code max} blocks of the player, or null. */
	public static @Nullable BlockPos titikDekat(ServerPlayer player, String sekte, String jenis, double max) {
		long situs = situs(player, sekte);
		if (situs < 0L) {
			return null;
		}
		BlockPos best = null;
		double bestSq = max * max;
		for (Titik t : semuaTitik(player.level(), situs, jenis)) {
			double sq = t.pos().distToCenterSqr(player.position());
			if (sq <= bestSq) {
				bestSq = sq;
				best = t.pos();
			}
		}
		return best;
	}

	/** The titik of a chore nearest the player inside the compound (any distance), else the 41 anchor ladder; null if none. */
	static @Nullable BlockPos titikTugas(ServerPlayer player, String sekte, String jenis) {
		BlockPos near = titikDekat(player, sekte, jenis, 160.0D);
		if (near != null) {
			return near;
		}
		// the 41 anchors and the compound's own (gerbang, aula ...) through the anchor ladder; life points have none
		boolean titikHidup = !TitikGabung.TITIK_BARU.contains(jenis)
				&& (jenis.startsWith("kerja:") || "meja_tulis".equals(jenis) || "latihan".equals(jenis) || "jaga".equals(jenis));
		return titikHidup ? null : TitikGabung.kompleks(sekte, jenis);
	}

	// ================================================================== day bookkeeping

	/** Chores {@code k} did on day {@code hari}. */
	public static int hariIni(Keanggotaan k, long hari) {
		return k.tugasHari == hari ? k.tugasJumlah : 0;
	}

	/** True when {@code tugas} was already done on {@code hari}. */
	public static boolean sudah(Keanggotaan k, Tugas tugas, long hari) {
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (b.getLongOr(K_SELESAI_HARI, -1L) != hari) {
			return false;
		}
		ListTag list = b.getListOrEmpty(K_SELESAI);
		for (int i = 0; i < list.size(); i++) {
			if (tugas.id.equals(list.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	/** True when {@code k} still owes today's duty (Pelayan/Tercatat with none done). */
	public static boolean wajib(Keanggotaan k, long hari) {
		return (k.pangkat == Pangkat.PELAYAN || k.pangkat == Pangkat.TERCATAT) && hariIni(k, hari) == 0;
	}

	/** Lazy upkeep: a servant or registered disciple who skipped a whole day of duty gets one violation point. */
	static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (k.pangkat != Pangkat.PELAYAN && k.pangkat != Pangkat.TERCATAT || k.hariMasuk < 0L || hari - k.hariMasuk < 2L) {
			return;
		}
		long last = Math.max(k.tugasHari, k.hariMasuk);
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (hari - last >= 2L && b.getLongOr(K_LALAI, -1L) != hari) {
			b.putLong(K_LALAI, hari);
			k.pelanggaran = Math.min(dev.kultivator.sekte.gabung.Aturan.MAKS, k.pelanggaran + 1);
			k.catat(hari, "kultivator.gabung.riwayat.tugas_lalai", Long.toString(hari - last - 1L));
			Msg.chat(player, Component.translatable("kultivator.gabung.tugas.lalai").withStyle(ChatFormatting.RED));
		}
	}

	// ================================================================== start

	/** {@code tugas:ID}: starts a chore. Null on success, else the reason. */
	public static @Nullable Component mulai(ServerPlayer player, String id) {
		Tugas tugas = Tugas.byId(id);
		if (tugas == null) {
			return Component.translatable("kultivator.gabung.tugas.alasan.tidak_ada");
		}
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		if (SESI.containsKey(player.getUUID())) {
			return Component.translatable("kultivator.gabung.tugas.alasan.sedang");
		}
		if (sudah(k, tugas, hari)) {
			return Component.translatable("kultivator.gabung.tugas.alasan.sudah");
		}
		if (tugas.harian && hariIni(k, hari) >= PER_HARI) {
			return Component.translatable("kultivator.gabung.tugas.alasan.harian", PER_HARI);
		}
		if (!k.pangkat.minimal(tugas.pangkatMin)) {
			return Component.translatable("kultivator.gabung.tugas.alasan.pangkat", tugas.pangkatMin.nama());
		}
		if (!tugas.jamBoleh(Kota.timeOfDay(server))) {
			return Component.translatable("kultivator.gabung.tugas.alasan.jam." + tugas.id);
		}
		if (situs(player, sekte) < 0L) {
			return Component.translatable("kultivator.gabung.tugas.alasan.luar_kompleks");
		}
		BlockPos pusat = titikTugas(player, sekte, tugas.titik);
		if (pusat == null) {
			return Component.translatable("kultivator.gabung.tugas.alasan.titik");
		}
		if (player.distanceToSqr(Vec3.atCenterOf(pusat)) > 48.0D * 48.0D) {
			return Component.translatable("kultivator.gabung.tugas.alasan.jauh", JenisMisiGabung.arah(player, posMisi(pusat)));
		}
		Sesi s = new Sesi(tugas, sekte, player.level().getGameTime(), pusat);
		RandomSource random = RandomSource.create(player.getUUID().getMostSignificantBits() ^ hari * 31L ^ tugas.ordinal());
		siapkan(player, s, random);
		SESI.put(player.getUUID(), s);
		Msg.title(player, tugas.nama().withStyle(ChatFormatting.GOLD), Component.translatable("kultivator.gabung.tugas." + tugas.id + ".mulai"),
				5, 50, 10);
		Msg.chat(player, tugas.teks().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.4F, 1.4F);
		return null;
	}

	private static CompoundTag posMisi(BlockPos pos) {
		CompoundTag t = new CompoundTag();
		t.putInt(JenisMisiGabung.X, pos.getX());
		t.putInt(JenisMisiGabung.Z, pos.getZ());
		return t;
	}

	/** Lays out the points of a chore around its titik. */
	private static void siapkan(ServerPlayer player, Sesi s, RandomSource random) {
		ServerLevel level = player.level();
		switch (s.tugas) {
			case PIKUL_AIR -> {
				BlockPos tong = titikDekat(player, s.sekte, TitikGabung.KERJA_TONG, 96.0D);
				if (tong == null || tong.distSqr(s.pusat) < 36.0D) {
					BlockPos alt = TempatApi.groundSpot(level, s.pusat.getX() + 12, s.pusat.getZ(), s.pusat.getY());
					tong = alt != null ? alt : s.pusat.offset(12, 0, 0);
				}
				s.titik.add(s.pusat);
				s.titik.add(tong);
			}
			case SAPU -> lingkar(level, s, SAPU_TITIK, 4, 9, random);
			case KEBUN -> {
				long situs = situs(player, s.sekte);
				for (Titik t : semuaTitik(level, situs, "kerja:cangkul")) {
					if (s.titik.size() < KEBUN_PETAK && t.pos().distSqr(s.pusat) <= 24.0D * 24.0D) {
						s.titik.add(t.pos());
					}
				}
				if (s.titik.size() < KEBUN_PETAK) {
					lingkar(level, s, KEBUN_PETAK - s.titik.size(), 2, 5, random);
				}
			}
			case KANDANG -> {
				AABB box = new AABB(s.pusat).inflate(16.0D);
				if (level.getEntitiesOfClass(Animal.class, box, Entity::isAlive).isEmpty()) {
					lingkar(level, s, KANDANG_JUMLAH, 2, 4, random);
				}
			}
			case KITAB -> {
				StringBuilder order = new StringBuilder();
				List<Character> pool = new ArrayList<>();
				for (char c : AKSARA.toCharArray()) {
					pool.add(c);
				}
				for (int i = 0; i < KITAB_PANJANG; i++) {
					order.append(pool.remove(random.nextInt(pool.size())));
				}
				s.urutan = order.toString();
				List<Character> mix = new ArrayList<>();
				for (char c : s.urutan.toCharArray()) {
					mix.add(c);
				}
				StringBuilder shuffled = new StringBuilder();
				while (!mix.isEmpty()) {
					shuffled.append(mix.remove(random.nextInt(mix.size())));
				}
				if (shuffled.toString().equals(s.urutan)) {
					shuffled.reverse();
				}
				s.acak = shuffled.toString();
				MutableComponent shown = Component.empty();
				for (int i = 0; i < s.urutan.length(); i++) {
					shown.append(Component.literal((i > 0 ? " · " : "") + s.urutan.charAt(i)));
				}
				Msg.title(player, shown.withStyle(ChatFormatting.WHITE), Component.translatable("kultivator.gabung.tugas.kitab.ingat"), 5, 80, 10);
				dev.kultivator.voice.Voice.later(100, () -> tombolKitab(player, s));
			}
			case JAGA -> s.pengintaiDetik = random.nextFloat() < 0.6F ? 30 + random.nextInt(60) : -1;
			case MENGAJAR -> {
				long situs = situs(player, s.sekte);
				if (situs >= 0L) {
					// the compound's disciples gather round the lecturer for the length of the lecture (38NL "tonton")
					HidupApi.timpaJadwal(situs, "tonton:" + s.pusat.getX() + "," + s.pusat.getZ(), player.level().getGameTime() + MENGAJAR_DETIK * 20L + 200L);
				}
			}
			case PAGI -> s.yawLalu = player.getYRot();
			default -> {
			}
		}
	}

	/** Points on a ring around the titik, on the ground. */
	private static void lingkar(ServerLevel level, Sesi s, int n, int rMin, int rMax, RandomSource random) {
		double start = random.nextDouble() * Math.PI * 2.0D;
		for (int i = 0; i < n; i++) {
			double a = start + i * Math.PI * 2.0D / n + (random.nextDouble() - 0.5D) * 0.5D;
			double r = rMin + random.nextDouble() * (rMax - rMin);
			int x = Mth.floor(s.pusat.getX() + 0.5D + Math.cos(a) * r);
			int z = Mth.floor(s.pusat.getZ() + 0.5D + Math.sin(a) * r);
			BlockPos ground = TempatApi.groundSpot(level, x, z, s.pusat.getY());
			s.titik.add(ground != null ? ground : new BlockPos(x, s.pusat.getY(), z));
		}
	}

	private static void tombolKitab(ServerPlayer player, Sesi s) {
		if (SESI.get(player.getUUID()) != s) {
			return;
		}
		MutableComponent line = Component.translatable("kultivator.gabung.tugas.kitab.pilih").withStyle(ChatFormatting.GRAY).append(" ");
		for (int i = 0; i < s.acak.length(); i++) {
			char c = s.acak.charAt(i);
			int n = i;
			line.append(Component.literal("[" + c + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung tugas pilih " + n)))).append(" ");
		}
		Msg.chat(player, line);
	}

	/** {@code /kultivasi gabung tugas pilih <n>}: one character of the copying puzzle. */
	public static void pilih(ServerPlayer player, int n) {
		Sesi s = SESI.get(player.getUUID());
		if (s == null || s.tugas != Tugas.KITAB || n < 0 || n >= s.acak.length() || s.langkah >= s.urutan.length()) {
			return;
		}
		char want = s.urutan.charAt(s.langkah);
		ServerLevel level = player.level();
		if (s.acak.charAt(n) == want) {
			s.langkah++;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.0F + s.langkah * 0.08F);
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.kitab.benar", s.urutan.substring(0, s.langkah), s.urutan.length())
					.withStyle(ChatFormatting.GREEN));
			if (s.langkah >= s.urutan.length()) {
				selesai(player, s);
			}
		} else {
			s.salah++;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
			if (s.salah >= 2) {
				berhenti(player, "kultivator.gabung.tugas.kitab.gagal");
			} else {
				Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.kitab.salah").withStyle(ChatFormatting.RED));
			}
		}
	}

	/** Ends the running chore without pay (left the place, out of time, gave up). */
	public static void berhenti(ServerPlayer player, String alasanKey) {
		Sesi s = SESI.remove(player.getUUID());
		if (s == null) {
			return;
		}
		lepasPose(player, s);
		Msg.chat(player, Component.translatable("kultivator.gabung.tugas.berhenti", s.tugas.nama(), Component.translatable(alasanKey))
				.withStyle(ChatFormatting.RED));
	}

	private static void lepasPose(ServerPlayer player, Sesi s) {
		if (s.tugas == Tugas.PIKUL_AIR || s.tugas == Tugas.TUNGKU) {
			GabungPayloads.kirimKlip(player, 0, 0);
		}
		if (s.tugas == Tugas.PIKUL_AIR) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
	}

	// ================================================================== running (every 2 ticks for players with a session)

	static void tick(MinecraftServer server, long tick) {
		if (SESI.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Sesi> e : List.copyOf(SESI.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Sesi s = e.getValue();
			if (player == null || !player.isAlive()) {
				SESI.remove(e.getKey());
				continue;
			}
			if (tick - s.mulai > s.tugas.detikMaks * 20L) {
				berhenti(player, "kultivator.gabung.tugas.henti.waktu");
				continue;
			}
			if (player.distanceToSqr(Vec3.atCenterOf(s.pusat)) > 40.0D * 40.0D) {
				berhenti(player, "kultivator.gabung.tugas.henti.pergi");
				continue;
			}
			boolean detik = (tick - s.mulai) % 20L == 0L;
			switch (s.tugas) {
				case PIKUL_AIR -> pikul(player, s);
				case SAPU -> sapu(player, s, tick);
				case KEBUN -> jongkokTitik(player, s, tick, 30, ParticleTypes.FALLING_WATER, ParticleTypes.SPLASH);
				case TUNGKU -> tungku(player, s, tick);
				case KANDANG -> {
					if (!s.titik.isEmpty()) {
						jongkokTitik(player, s, tick, 20, ParticleTypes.COMPOSTER, ParticleTypes.HAPPY_VILLAGER);
					}
				}
				case PAGI -> pagi(player, s, tick);
				case JAGA -> {
					if (detik) {
						jaga(player, s);
					}
				}
				case GERBANG -> {
					if (detik) {
						gerbang(player, s);
					}
				}
				case MENGAJAR -> {
					if (detik) {
						mengajar(player, s);
					}
				}
				default -> {
				}
			}
		}
	}

	private static void pikul(ServerPlayer player, Sesi s) {
		BlockPos target = s.titik.get(s.bawa ? 1 : 0);
		ServerLevel level = player.level();
		if ((player.level().getGameTime() & 7L) == 0L) {
			level.sendParticles(player, ParticleTypes.DRIPPING_WATER, true, false, target.getX() + 0.5D, target.getY() + 1.6D, target.getZ() + 0.5D,
					4, 0.2D, 0.3D, 0.2D, 0.0D);
		}
		if (player.distanceToSqr(Vec3.atBottomCenterOf(target)) > 2.6D * 2.6D) {
			return;
		}
		if (!s.bawa) {
			s.bawa = true;
			GabungPayloads.kirimKlip(player, GabungGerakId.PIKUL_AIR, GabungGerakId.PROP_EMBER_PIKUL);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 60, 0, false, false, true));
			level.playSound(null, target, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 1.0F);
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.pikul_air.isi", s.hitung, PIKUL_KALI).withStyle(ChatFormatting.AQUA));
		} else {
			s.bawa = false;
			s.hitung++;
			GabungPayloads.kirimKlip(player, 0, 0);
			player.removeEffect(MobEffects.SLOWNESS);
			level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, 12, 0.3D, 0.2D, 0.3D, 0.1D);
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.pikul_air.tuang", s.hitung, PIKUL_KALI).withStyle(ChatFormatting.AQUA));
			if (s.hitung >= PIKUL_KALI) {
				selesai(player, s);
			}
		}
	}

	private static void sapu(ServerPlayer player, Sesi s, long tick) {
		ServerLevel level = player.level();
		if (s.langkah >= s.titik.size()) {
			return;
		}
		BlockPos p = s.titik.get(s.langkah);
		if ((tick & 7L) == 0L) {
			level.sendParticles(player, ParticleTypes.COMPOSTER, true, false, p.getX() + 0.5D, p.getY() + 0.2D, p.getZ() + 0.5D, 6, 0.4D, 0.05D, 0.4D,
					0.0D);
		}
		if (player.distanceToSqr(Vec3.atBottomCenterOf(p)) <= 1.7D * 1.7D) {
			level.playSound(null, p, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.9F, 1.0F);
			level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5D, p.getY() + 0.2D, p.getZ() + 0.5D, 6, 0.4D, 0.1D, 0.4D, 0.01D);
			s.langkah++;
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.sapu.maju", s.langkah, s.titik.size()).withStyle(ChatFormatting.GREEN));
			if (s.langkah >= s.titik.size()) {
				selesai(player, s);
			}
		}
	}

	/** Crouch {@code ticks} at each unfinished point (watering plots, filling troughs). */
	private static void jongkokTitik(ServerPlayer player, Sesi s, long tick, int ticks, net.minecraft.core.particles.SimpleParticleType tanda,
			net.minecraft.core.particles.SimpleParticleType kerja) {
		ServerLevel level = player.level();
		int near = -1;
		for (int i = 0; i < s.titik.size(); i++) {
			BlockPos p = s.titik.get(i);
			if (s.selesai.contains(i)) {
				continue;
			}
			if ((tick & 15L) == 0L) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, p.getX() + 0.5D, p.getY() + 0.4D, p.getZ() + 0.5D, 2, 0.3D, 0.1D,
						0.3D, 0.0D);
			}
			if (near < 0 && player.distanceToSqr(Vec3.atBottomCenterOf(p)) <= 2.2D * 2.2D) {
				near = i;
			}
		}
		if (near < 0 || !player.isShiftKeyDown()) {
			s.hitung = 0;
			return;
		}
		BlockPos p = s.titik.get(near);
		s.hitung += 2;
		if ((tick & 3L) == 0L) {
			level.sendParticles(tanda, p.getX() + 0.5D, p.getY() + 0.8D, p.getZ() + 0.5D, 3, 0.3D, 0.2D, 0.3D, 0.0D);
		}
		if (s.hitung < ticks) {
			return;
		}
		s.hitung = 0;
		s.selesai.add(near);
		level.sendParticles(kerja, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 10, 0.4D, 0.2D, 0.4D, 0.05D);
		level.playSound(null, p, s.tugas == Tugas.KEBUN ? SoundEvents.WET_GRASS_BREAK : SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
		Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas." + s.tugas.id + ".maju", s.selesai.size(), s.titik.size())
				.withStyle(ChatFormatting.GREEN));
		if (s.selesai.size() >= s.titik.size()) {
			selesai(player, s);
		}
	}

	private static void tungku(ServerPlayer player, Sesi s, long tick) {
		ServerLevel level = player.level();
		long t = tick - s.mulai;
		if (t == 0L || s.isyarat < 0L && s.langkah == 0) {
			GabungPayloads.kirimKlip(player, GabungGerakId.KIPAS_TUNGKU, GabungGerakId.PROP_KIPAS_BAMBU);
		}
		if (player.distanceToSqr(Vec3.atCenterOf(s.pusat)) > 4.0D * 4.0D) {
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.tungku.dekat").withStyle(ChatFormatting.YELLOW));
			return;
		}
		boolean jongkok = player.isShiftKeyDown();
		boolean tekan = jongkok && !s.jongkokLalu;
		s.jongkokLalu = jongkok;
		double fx = s.pusat.getX() + 0.5D;
		double fy = s.pusat.getY() + 1.0D;
		double fz = s.pusat.getZ() + 0.5D;
		if (s.isyarat >= 0L) {
			if (tekan && !s.isyaratKena) {
				s.isyaratKena = true;
				s.hitung++;
				level.sendParticles(ParticleTypes.FLAME, fx, fy, fz, 14, 0.25D, 0.3D, 0.25D, 0.04D);
				level.playSound(null, s.pusat, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.4F);
			}
			if (tick - s.isyarat > 30L) {
				if (!s.isyaratKena) {
					level.sendParticles(ParticleTypes.SMOKE, fx, fy, fz, 10, 0.2D, 0.3D, 0.2D, 0.02D);
				}
				s.isyarat = -1L;
				Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.tungku.skor", s.hitung, s.langkah, TUNGKU_ISYARAT)
						.withStyle(s.isyaratKena ? ChatFormatting.GOLD : ChatFormatting.GRAY));
				if (s.langkah >= TUNGKU_ISYARAT) {
					if (s.hitung >= TUNGKU_LULUS) {
						selesai(player, s);
					} else {
						berhenti(player, "kultivator.gabung.tugas.tungku.padam");
					}
				}
			}
			return;
		}
		if ((tick & 3L) == 0L) {
			level.sendParticles(ParticleTypes.FLAME, fx, fy, fz, 2, 0.15D, 0.1D, 0.15D, 0.01D);
		}
		// a cue every 3 s after a 2 s warm-up: the fire sinks, a thread of smoke, a crackle
		if (t >= 40L && (t - 40L) % 60L == 0L && s.langkah < TUNGKU_ISYARAT) {
			s.langkah++;
			s.isyarat = tick;
			s.isyaratKena = false;
			level.sendParticles(ParticleTypes.SMOKE, fx, fy + 0.3D, fz, 6, 0.1D, 0.4D, 0.1D, 0.0D);
			level.playSound(null, s.pusat, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 0.8F);
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.tungku.kipas").withStyle(ChatFormatting.RED));
		}
	}

	private static void pagi(ServerPlayer player, Sesi s, long tick) {
		if (player.distanceToSqr(Vec3.atCenterOf(s.pusat)) > 7.0D * 7.0D) {
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.pagi.dekat").withStyle(ChatFormatting.YELLOW));
			return;
		}
		ServerLevel level = player.level();
		if (s.detik == 0) {
			s.detik = 1;
			gerakan(player, s, 0);
		}
		switch (s.langkah) {
			case 0 -> {
				// horse stance: crouch 4 s without moving
				boolean diam = player.getDeltaMovement().horizontalDistanceSqr() < 0.0025D;
				if (player.isShiftKeyDown() && diam) {
					s.hitung += 2;
					if (s.hitung >= 80) {
						s.hitung = 0;
						s.langkah = 1;
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, 0.8F, 1.2F);
						gerakan(player, s, 1);
					}
				} else {
					s.hitung = 0;
				}
			}
			case 1 -> {
				// crane leaps: three jumps (left the ground going up, landed again)
				boolean darat = player.onGround();
				if (s.darat && !darat && player.getDeltaMovement().y > 0.1D) {
					s.hitung++;
					level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 4, 0.2D, 0.0D, 0.2D, 0.01D);
					Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.pagi.lompat", s.hitung, 3).withStyle(ChatFormatting.GREEN));
					if (s.hitung >= 3) {
						s.hitung = 0;
						s.langkah = 2;
						s.putar = 0.0F;
						s.yawLalu = player.getYRot();
						s.isyarat = tick;
						gerakan(player, s, 2);
					}
				}
				s.darat = darat;
			}
			case 2 -> {
				// the turning step: a full turn within 5 s
				float yaw = player.getYRot();
				s.putar += Math.abs(Mth.wrapDegrees(yaw - s.yawLalu));
				s.yawLalu = yaw;
				if (tick - s.isyarat > 100L) {
					s.putar = 0.0F;
					s.isyarat = tick;
				}
				if (s.putar >= 360.0F) {
					level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 10, 0.6D, 0.4D, 0.6D, 0.02D);
					selesai(player, s);
				}
			}
			default -> {
			}
		}
	}

	private static void gerakan(ServerPlayer player, Sesi s, int n) {
		Msg.title(player, Component.empty(), Component.translatable("kultivator.gabung.tugas.pagi.gerak." + n).withStyle(ChatFormatting.GOLD), 3, 40, 5);
		// the eldest senior (or any disciple of the ground) shows the move
		ServerLevel level = player.level();
		AABB box = new AABB(s.pusat).inflate(12.0D);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, box, Entity::isAlive)) {
			HidupApi.klip(npc, GabungGerakId.SALAM_SEKTE, 0, 40);
			HidupApi.tatap(npc, player);
			break;
		}
	}

	private static void jaga(ServerPlayer player, Sesi s) {
		ServerLevel level = player.level();
		boolean dekat = player.distanceToSqr(Vec3.atCenterOf(s.pusat)) <= 4.5D * 4.5D;
		if (dekat) {
			s.detik++;
		}
		if (s.pengintaiDetik >= 0 && s.detik == s.pengintaiDetik && s.pengintai == null) {
			Vec3 at = Vec3.atCenterOf(s.pusat).add(level.getRandom().nextBoolean() ? 18.0D : -18.0D, 0.0D, level.getRandom().nextBoolean() ? 14.0D : -14.0D);
			Mob scout = RogueCultivators.spawn(level, at, 1, Math.max(0, Barang.pita(player)), RogueCultivators.VARIANT_ROGUE,
					RogueCultivators.ROLE_QUEST, null);
			if (scout != null) {
				s.pengintai = scout.getUUID();
				scout.setTarget(player);
				Msg.chat(player, Component.translatable("kultivator.gabung.tugas.jaga.pengintai").withStyle(ChatFormatting.RED));
				level.playSound(null, s.pusat, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
		}
		if (s.detik % 10 == 0 || !dekat) {
			Msg.actionBar(player, dekat
					? Component.translatable("kultivator.gabung.tugas.jaga.waktu", s.detik, JAGA_DETIK).withStyle(ChatFormatting.AQUA)
					: Component.translatable("kultivator.gabung.tugas.jaga.dekat").withStyle(ChatFormatting.YELLOW));
		}
		boolean pengintaiAda = s.pengintai != null && !s.pengintaiKalah && level.getEntity(s.pengintai) instanceof LivingEntity scout && scout.isAlive();
		if (s.detik >= JAGA_DETIK && !pengintaiAda) {
			selesai(player, s);
		}
	}

	/** The 5-minute gate shift: stay within 6 blocks of the compound gate; foes beaten nearby pay extra. */
	private static void gerbang(ServerPlayer player, Sesi s) {
		boolean dekat = player.distanceToSqr(Vec3.atCenterOf(s.pusat)) <= 6.0D * 6.0D;
		if (dekat) {
			s.detik++;
		}
		if (s.detik % 30 == 0 || !dekat) {
			Msg.actionBar(player, dekat
					? Component.translatable("kultivator.gabung.tugas.gerbang.waktu", s.detik, GERBANG_DETIK, s.hitung).withStyle(ChatFormatting.AQUA)
					: Component.translatable("kultivator.gabung.tugas.gerbang.dekat").withStyle(ChatFormatting.YELLOW));
		}
		if (s.detik >= GERBANG_DETIK) {
			selesai(player, s);
		}
	}

	/** The elder's lecture: 120 s at the hall, one maxim every 20 s for everyone within 16 blocks. */
	private static void mengajar(ServerPlayer player, Sesi s) {
		if (player.distanceToSqr(Vec3.atCenterOf(s.pusat)) > 6.0D * 6.0D) {
			Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.mengajar.dekat").withStyle(ChatFormatting.YELLOW));
			return;
		}
		s.detik++;
		if (s.detik % 20 == 1) {
			int line = Math.min(MENGAJAR_BARIS - 1, s.detik / 20);
			Component maxim = Component.translatable("kultivator.gabung.tugas.mengajar.baris." + line).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
			ServerLevel level = player.level();
			for (ServerPlayer near : level.players()) {
				if (near.distanceToSqr(player) <= 16.0D * 16.0D) {
					Msg.chat(near, Component.empty().append(player.getDisplayName()).append(": ").append(maxim));
				}
			}
			level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.8D, player.getZ(), 20, 1.5D, 0.6D, 1.5D, 0.5D);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 0.9F);
		}
		if (s.detik >= MENGAJAR_DETIK) {
			selesai(player, s);
		}
	}

	/** The death event: a beaten night-watch scout; a foe beaten near a gate shift. */
	static void onMati(LivingEntity victim) {
		for (Sesi s : SESI.values()) {
			if (victim.getUUID().equals(s.pengintai)) {
				s.pengintaiKalah = true;
			}
			if (s.tugas == Tugas.GERBANG && victim instanceof net.minecraft.world.entity.monster.Enemy
					&& victim.distanceToSqr(Vec3.atCenterOf(s.pusat)) <= 32.0D * 32.0D) {
				s.hitung++;
			}
		}
	}

	/** {@code UseEntityCallback}: feeding an animal of the pen during {@code kandang}. */
	static void beriMakan(ServerPlayer player, Entity entity) {
		Sesi s = SESI.get(player.getUUID());
		if (s == null || s.tugas != Tugas.KANDANG || !s.titik.isEmpty() || !(entity instanceof Animal animal)
				|| animal.distanceToSqr(Vec3.atCenterOf(s.pusat)) > 16.0D * 16.0D || !s.diberi.add(animal.getUUID())) {
			return;
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
		level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7F, 1.0F);
		Msg.actionBar(player, Component.translatable("kultivator.gabung.tugas.kandang.maju", s.diberi.size(), KANDANG_JUMLAH).withStyle(ChatFormatting.GREEN));
		if (s.diberi.size() >= KANDANG_JUMLAH) {
			selesai(player, s);
		}
	}

	// ================================================================== reward

	private static void selesai(ServerPlayer player, Sesi s) {
		SESI.remove(player.getUUID());
		lepasPose(player, s);
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.anggotaDi(s.sekte)) {
			return;
		}
		long hari = Kota.day(server);
		if (s.tugas.harian) {
			if (k.tugasHari != hari) {
				k.tugasHari = hari;
				k.tugasJumlah = 0;
			}
			k.tugasJumlah++;
			k.tugasTotal++;
		}
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (b.getLongOr(K_SELESAI_HARI, -1L) != hari) {
			b.putLong(K_SELESAI_HARI, hari);
			b.put(K_SELESAI, new ListTag());
		}
		ListTag done = b.getListOrEmpty(K_SELESAI);
		done.add(StringTag.valueOf(s.tugas.id));
		b.put(K_SELESAI, done);
		boolean khas = s.tugas.corakKhas(GabungApi.corak(s.sekte));
		int pk = pkTugas(s.tugas, s.sekte, khas);
		if (s.tugas == Tugas.JAGA && s.pengintaiKalah) {
			pk += JAGA_BONUS;
		}
		if (s.tugas == Tugas.GERBANG) {
			pk += Math.min(GERBANG_MUSUH_MAKS, s.hitung * GERBANG_PER_MUSUH);
		}
		if (!k.shifu.isEmpty()) {
			k.ikatanShifu = Math.min(100, k.ikatanShifu + 1);
		}
		k.catat(hari, "kultivator.gabung.riwayat.tugas", s.tugas.id, Integer.toString(pk));
		data.setDirty();
		GabungApi.tambahPk(player, pk, "gabung.tugas");
		TokohSekte.Tokoh tetua = TokohSekte.jabatan(server, s.sekte, s.tugas.jabatan);
		if (tetua != null) {
			TokohSekte.ubahSikap(server, tetua.id(), player.getUUID(), 1);
		}
		if (s.tugas.harian && k.tugasTotal >= 10) {
			Advancements.grant(player, "sekte/gabung_tugas");
		}
		if (s.tugas.harian) {
			CeritaApi.peristiwa(player, "tanda", "gabung_tugas");
		}
		CeritaApi.peristiwa(player, "tanda", "gabung_tugas_" + s.tugas.id);
		// the chore's own little story starts at its first completion (data/kultivator/tempat/quest/gabung_tugas_*.json)
		String quest = questTugas(s.tugas);
		if (quest != null) {
			CeritaApi.mulaiQuest(player, quest, Math.max(0L, situs(player, s.sekte)));
		}
		Msg.chat(player, (s.tugas.harian ? Component.translatable("kultivator.gabung.tugas.selesai", s.tugas.nama(), pk, k.tugasJumlah, PER_HARI)
				: Component.translatable("kultivator.gabung.tugas.selesai_lain", s.tugas.nama(), pk)).withStyle(ChatFormatting.GOLD));
		if (khas) {
			Msg.chat(player, Component.translatable("kultivator.gabung.tugas.khas", GabungApi.corak(s.sekte).nama()).withStyle(ChatFormatting.DARK_AQUA));
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.4D, 0.5D, 0.4D, 0.0D);
	}

	/** The PK of {@code t} at {@code sekte}: base x kf (x1.5 own style); the gate shift 10-30 by class instead of kf. */
	static int pkTugas(Tugas t, String sekte, boolean khas) {
		if (t == Tugas.GERBANG) {
			return t.pk + 5 * (GabungApi.kelas(sekte) - 1);
		}
		return Math.max(1, (int) Math.round(t.pk * GabungApi.kelasSekte(sekte).kf() * (khas ? PENGALI_CORAK : 1.0D)));
	}

	/** The duty quest a chore opens ({@code gabung_tugas_*}), or null. */
	static @Nullable String questTugas(Tugas t) {
		return switch (t) {
			case PIKUL_AIR -> "gabung_tugas_air";
			case TUNGKU -> "gabung_tugas_tungku";
			case KITAB -> "gabung_tugas_kitab";
			case KANDANG -> "gabung_tugas_kandang";
			case PAGI -> "gabung_tugas_pagi";
			case JAGA -> "gabung_tugas_jaga";
			default -> null;
		};
	}

	/** The servant's first-days quest: from the oath (Pelayan/Tercatat) or the first day without a duty done. */
	public static final String QUEST_PERTAMA = "gabung_tugas_pertama";

	// ================================================================== sheet and chat

	/** Fills {@code tugas} of sheet {@code gabung_papan} (tab Tugas, {@code GabungLembar.Papan}). */
	public static void isiLembar(ServerPlayer player, String sekte, CompoundTag lembar) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		long hari = Kota.day(server);
		Corak corak = GabungApi.corak(sekte);
		ListTag list = new ListTag();
		for (Tugas t : Tugas.values()) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, t.id);
			LembarKunci.teks(e, LembarKunci.NAMA, t.nama());
			LembarKunci.teks(e, LembarKunci.TEKS, t.teks());
			boolean khas = t.corakKhas(corak);
			e.putInt(LembarKunci.PK, pkTugas(t, sekte, khas));
			boolean done = sudah(k, t, hari);
			e.putInt("kemajuan", done ? 1 : 0);
			e.putInt("maks", 1);
			e.putBoolean(GabungLembar.Papan.SELESAI, done);
			e.putBoolean(GabungLembar.Papan.CORAK_BONUS, khas);
			BlockPos at = titikTugas(player, sekte, t.titik);
			if (at != null) {
				e.putInt(LembarKunci.X, at.getX());
				e.putInt(LembarKunci.Z, at.getZ());
			}
			if (!done && !k.pangkat.minimal(t.pangkatMin)) {
				e.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.tugas.alasan.pangkat", t.pangkatMin.nama()));
			} else if (!done && t.harian && hariIni(k, hari) >= PER_HARI) {
				e.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.tugas.alasan.harian", PER_HARI));
			} else if (!done && !t.jamBoleh(Kota.timeOfDay(server))) {
				e.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.tugas.alasan.jam." + t.id));
			}
			list.add(e);
		}
		lembar.put(GabungLembar.Papan.TUGAS, list);
	}

	/** {@code /kultivasi gabung tugas}: the duties in chat with start buttons. */
	public static void tulisChat(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hidup.bukan_anggota").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = player.level().getServer();
		long hari = Kota.day(server);
		Msg.chat(player, Component.translatable("kultivator.gabung.tugas.chat.judul", hariIni(k, hari), PER_HARI)
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (wajib(k, hari)) {
			Msg.chat(player, Component.translatable("kultivator.gabung.tugas.chat.wajib").withStyle(ChatFormatting.RED));
		}
		Corak corak = GabungApi.corak(sekte);
		for (Tugas t : Tugas.values()) {
			boolean done = sudah(k, t, hari);
			MutableComponent line = t.nama().withStyle(done ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
			if (t.corakKhas(corak)) {
				line.append(Component.literal(" ✦").withStyle(ChatFormatting.DARK_AQUA));
			}
			line.append(" ");
			if (done) {
				line.append(Component.translatable("kultivator.gabung.tugas.chat.sudah").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				line.append(Component.translatable("kultivator.gabung.tugas.chat.mulai").withStyle(st -> st.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung tugas " + t.id))));
			}
			Msg.chat(player, line);
		}
	}

	private TugasHarian() {
	}
}

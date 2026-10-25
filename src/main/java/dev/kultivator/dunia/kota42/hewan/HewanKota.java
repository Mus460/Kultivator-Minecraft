package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.acara.KotaAcaraData;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ekonomi.TokoPemain;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.tema.PutusanTema;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Town animals (DESIGN 42.7.5, contract C42.D1) and the runtime of 42D-H: cats, dogs, chickens, pigeon flocks and tied
 * horses, camels and donkeys, all {@code noSave}, made only from titik inside the place box and only while a player is
 * within {@value #MUNCUL} blocks (the {@code Pemanggil} pattern: it runs over {@link Pemanggil#semua()}, the inhabited
 * places with a player near, and their cached titik), taken back when every player is {@value #LEPAS} blocks away.
 * <p>
 * <b>Candidates</b> per place (rebuilt when the place's titik count changes):
 * <ul>
 *   <li>{@code kucing_tidur} (roofs, sills, wall tops): cats; in the capital the one nearest the market stalls is
 *       <b>Lao Hua</b>. Villages without cat titik use house doors.</li>
 *   <li>{@code jaga} (gates): the shaggy guard dog; {@code pintu} (doors) and the cook's {@code kerja:kuali}: street dogs;
 *       plus every player's guard dog of this place ({@link #beriTulang}).</li>
 *   <li>{@code kandang} (data {@code hewan}, {@code jumlah}): a pen of chickens (one rooster, hens, the rest chicks at a
 *       hen's heels), horses, camels, donkeys, a dog or a dovecote flock.</li>
 *   <li>{@code tambat}: horses; camels in the Kota Oasis and desert posts (biome tag {@code kota_tema/oasis}); donkeys in
 *       villages and on the mountain (with pack bags at trading posts, walking the mill round when the titik has
 *       {@code giling=1}).</li>
 *   <li>{@code dupa} (temple fronts) and {@code sumur} (well squares): pigeon flocks (never in the Kota Iblis or villages).</li>
 * </ul>
 * Each cycle (every {@value #SIKLUS} ticks) the candidates within reach of a player are made nearest first, at most
 * {@value #BARU_PER_SIKLUS} per place per cycle, within the per-kind limit of the place kind, {@code kotaHewan.perTempat}
 * per place, {@value #MAKS_DUNIA} animals in the world (42.13.3) and the shared {@code kota42} entity budget. Kind switches
 * {@code kucing/anjing/ayam/merpati/ternak} gate each group. Variants are a pure function of the titik (every visit shows
 * the same black cat on the same roof). Overworld only; never in sect compounds.
 * <p>
 * Contracts for the other 42 modules: {@link #panggil} (C42.D1), {@link #laoHua}, {@link #dijaga} (guard dogs keep
 * night thieves off a player's shop), {@link #ayamLepas} + {@link #addPenangkapAyam} (loose-chicken event, 42D-P),
 * {@link #kudaPengantin} (the groom's horse, 42D-P), {@link #merpatiTerbang} (bell, firecrackers, 42D), {@link #hewanDi}.
 * State: the guard dogs in {@code KotaAcaraData.situs("hewan", situs)["jaga"][uuid]} ({@code x y z} post, {@code v} coat,
 * {@code t} {@code rumah|toko}).
 */
public final class HewanKota {

	/** Players within this many blocks of a titik bring its animal. */
	public static final double MUNCUL = 64.0D;
	/** Animals farther than this from every player are taken back. */
	public static final double LEPAS = 88.0D;
	/** 42.13.3: at most 48 animals in the world (half the {@code kota42} budget). */
	public static final int MAKS_DUNIA = 48;
	static final int SIKLUS = 20;
	static final int BARU_PER_SIKLUS = 4;
	/** Event animals (loose chickens, the groom's horse) are taken back after this long at the latest. */
	static final long ACARA_MAKS = 20L * 60L * 20L;

	public static final String KUCING = "kucing";
	public static final String LAO_HUA = "lao_hua";
	public static final String ANJING = "anjing";
	public static final String ANJING_JAGA = "anjing_jaga";
	public static final String AYAM = "ayam";
	public static final String KUDA = "kuda";
	public static final String UNTA = "unta";
	public static final String KELEDAI = "keledai";
	public static final String MERPATI = "merpati";

	/** Kind groups (limits and config switches). */
	static final String G_KUCING = "kucing";
	static final String G_ANJING = "anjing";
	static final String G_AYAM = "ayam";
	static final String G_TERNAK = "ternak";
	static final String G_MERPATI = "merpati";

	/** One possible animal of a place: plan key, kind, block, facing yaw, coat, kind data, chicken pen key. */
	record Calon(String kunci, String jenis, BlockPos pos, float yaw, int varian, int data, @Nullable UUID tuan, String kandang) {

		String kelompok() {
			return switch (jenis) {
				case KUCING, LAO_HUA -> G_KUCING;
				case ANJING, ANJING_JAGA -> G_ANJING;
				case AYAM -> G_AYAM;
				case MERPATI -> G_MERPATI;
				default -> G_TERNAK;
			};
		}
	}

	/** Runtime state of one active place. */
	static final class Tempat {
		final long situs;
		final String jenis;
		int titikN = -1;
		int jagaVersi = -1;
		List<Calon> calon = List.of();
		final Map<String, Entity> hidup = new LinkedHashMap<>();
		/** Landing spots of the flocks of this place (pigeons glide between them). */
		List<Vec3> tempatMerpati = List.of();

		Tempat(long situs, String jenis) {
			this.situs = situs;
			this.jenis = jenis;
		}
	}

	private static final Map<Long, Tempat> TEMPAT = new HashMap<>();
	/** Event and test animals: entity -> game time it is taken back. */
	private static final Map<Entity, Long> ACARA = new LinkedHashMap<>();
	/** Test lineup animals: entity -> state cycle length (number of states). */
	private static final Map<Entity, Integer> UJI = new LinkedHashMap<>();
	/** Test lineup animals: entity -> game time they are taken back. */
	private static final Map<Entity, Long> ACARA_UJI = new HashMap<>();
	private static final List<BiConsumer<ServerPlayer, AyamKotaEntity>> PENANGKAP = new CopyOnWriteArrayList<>();
	/** Bumped on every guard-dog change, so the places rebuild their candidates. */
	private static int jagaVersi;
	private static boolean initialised;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(HewanKota::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
		HewanUji.init();
	}

	private static void lupakan() {
		TEMPAT.clear();
		ACARA.clear();
		UJI.clear();
		ACARA_UJI.clear();
	}

	private static boolean aktif() {
		return KotaConfig.hewan().aktif;
	}

	// ================================================================== the tick

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 5 == 0) {
			tickAcara(server);
		}
		if (t % SIKLUS != 0) {
			return;
		}
		if (!aktif()) {
			if (!TEMPAT.isEmpty()) {
				for (Tempat tp : TEMPAT.values()) {
					lepasSemua(tp);
				}
				TEMPAT.clear();
			}
			return;
		}
		Set<Long> aktifSekarang = new HashSet<>();
		for (TempatHuni huni : Pemanggil.semua()) {
			ServerLevel level = server.getLevel(huni.dimensi);
			if (level == null || !KotaHidup.duniaKota(level) || !bolehTempat(huni.jenis)) {
				continue;
			}
			aktifSekarang.add(huni.situsId);
			Tempat tp = TEMPAT.computeIfAbsent(huni.situsId, k -> new Tempat(huni.situsId, huni.jenis));
			try {
				siklus(level, huni, tp);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Town animals of place {} failed", huni.situsId, exception);
			}
		}
		for (Iterator<Tempat> it = TEMPAT.values().iterator(); it.hasNext(); ) {
			Tempat tp = it.next();
			if (!aktifSekarang.contains(tp.situs)) {
				lepasSemua(tp);
				it.remove();
			}
		}
	}

	/** Places that keep town animals: towns, the capital, themed cities, villages and road posts (not sect compounds). */
	static boolean bolehTempat(String jenis) {
		return jenis.startsWith("kota/") || jenis.startsWith("kota_tema/") || jenis.startsWith("kampung/") || jenis.startsWith("pos/");
	}

	private static void siklus(ServerLevel level, TempatHuni huni, Tempat tp) {
		if (tp.titikN != huni.jumlahTitik() || tp.jagaVersi != jagaVersi) {
			tp.titikN = huni.jumlahTitik();
			tp.jagaVersi = jagaVersi;
			tp.calon = rencana(level, huni);
			tp.tempatMerpati = new ArrayList<>();
			for (Calon c : tp.calon) {
				if (MERPATI.equals(c.jenis())) {
					tp.tempatMerpati.add(Vec3.atBottomCenterOf(c.pos()));
				}
			}
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				players.add(p);
			}
		}
		// take back the far ones and the dead
		for (Iterator<Map.Entry<String, Entity>> it = tp.hidup.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Entity> e = it.next();
			Entity a = e.getValue();
			if (a.isRemoved()) {
				it.remove();
				continue;
			}
			if (jarakPemain(players, a.position()) > LEPAS) {
				a.discard();
				it.remove();
			}
		}
		if (players.isEmpty()) {
			return;
		}
		KotaConfig.Hewan cfg = KotaConfig.hewan();
		int cap = cfg.perTempat;
		Map<String, Integer> batas = batas(tp.jenis);
		Map<String, Integer> ada = new HashMap<>();
		for (Map.Entry<String, Entity> e : tp.hidup.entrySet()) {
			Calon c = cari(tp, e.getKey());
			if (c != null) {
				ada.merge(c.kelompok(), 1, Integer::sum);
			}
		}
		List<Calon> dekat = new ArrayList<>();
		Map<Calon, Double> jarak = new HashMap<>();
		for (Calon c : tp.calon) {
			if (tp.hidup.containsKey(c.kunci()) || !nyala(cfg, c.kelompok())) {
				continue;
			}
			double d = jarakPemain(players, Vec3.atBottomCenterOf(c.pos()));
			if (d <= MUNCUL) {
				dekat.add(c);
				jarak.put(c, d);
			}
		}
		// the named cat and the players' guard dogs first, then nearest first
		dekat.sort(Comparator.<Calon>comparingInt(c -> LAO_HUA.equals(c.jenis()) || ANJING_JAGA.equals(c.jenis()) ? 0 : 1)
				.thenComparingDouble(jarak::get));
		int baru = 0;
		for (Calon c : dekat) {
			if (baru >= BARU_PER_SIKLUS || tp.hidup.size() >= cap || jumlahDunia() >= MAKS_DUNIA
					|| !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				break;
			}
			boolean khusus = LAO_HUA.equals(c.jenis()) || ANJING_JAGA.equals(c.jenis());
			if (!khusus && ada.getOrDefault(c.kelompok(), 0) >= batas.getOrDefault(c.kelompok(), 0)) {
				continue;
			}
			Entity e = muncul(level, c, tp);
			if (e != null) {
				tp.hidup.put(c.kunci(), e);
				ada.merge(c.kelompok(), 1, Integer::sum);
				baru++;
			}
		}
		hubungkanAnakAyam(tp);
	}

	private static @Nullable Calon cari(Tempat tp, String kunci) {
		for (Calon c : tp.calon) {
			if (c.kunci().equals(kunci)) {
				return c;
			}
		}
		return null;
	}

	private static boolean nyala(KotaConfig.Hewan cfg, String kelompok) {
		return switch (kelompok) {
			case G_KUCING -> cfg.kucing;
			case G_ANJING -> cfg.anjing;
			case G_AYAM -> cfg.ayam;
			case G_MERPATI -> cfg.merpati;
			default -> cfg.ternak;
		};
	}

	/** Live animals of a kind group per place kind (cats, dogs, chickens, tethered, flocks). */
	static Map<String, Integer> batas(String jenis) {
		Map<String, Integer> m = new HashMap<>();
		if (jenis.equals("kota/kota_besar")) {
			m.put(G_KUCING, 6);
			m.put(G_ANJING, 4);
			m.put(G_AYAM, 6);
			m.put(G_TERNAK, 5);
			m.put(G_MERPATI, 2);
		} else if (jenis.equals("kota_tema/pelabuhan")) {
			m.put(G_KUCING, 7);
			m.put(G_ANJING, 2);
			m.put(G_AYAM, 0);
			m.put(G_TERNAK, 3);
			m.put(G_MERPATI, 1);
		} else if (jenis.equals("kota_tema/iblis")) {
			m.put(G_KUCING, 4);
			m.put(G_ANJING, 3);
			m.put(G_AYAM, 0);
			m.put(G_TERNAK, 2);
			m.put(G_MERPATI, 0);
		} else if (jenis.startsWith("kota_tema/")) {
			m.put(G_KUCING, 4);
			m.put(G_ANJING, 2);
			m.put(G_AYAM, 3);
			m.put(G_TERNAK, 4);
			m.put(G_MERPATI, 1);
		} else if (jenis.startsWith("kota/")) {
			m.put(G_KUCING, 4);
			m.put(G_ANJING, 3);
			m.put(G_AYAM, 6);
			m.put(G_TERNAK, 3);
			m.put(G_MERPATI, 1);
		} else if (jenis.startsWith("kampung/")) {
			m.put(G_KUCING, 2);
			m.put(G_ANJING, 2);
			m.put(G_AYAM, 12);
			m.put(G_TERNAK, 3);
			m.put(G_MERPATI, 1);
		} else {
			m.put(G_KUCING, 1);
			m.put(G_ANJING, 1);
			m.put(G_AYAM, 6);
			m.put(G_TERNAK, 4);
			m.put(G_MERPATI, 0);
		}
		return m;
	}

	private static double jarakPemain(List<ServerPlayer> players, Vec3 at) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			best = Math.min(best, p.position().distanceToSqr(at));
		}
		return Math.sqrt(best);
	}

	/** Live town animals in the world (plan, event and test animals). */
	public static int jumlahDunia() {
		int n = ACARA.size() + UJI.size();
		for (Tempat tp : TEMPAT.values()) {
			n += tp.hidup.size();
		}
		return n;
	}

	private static void lepasSemua(Tempat tp) {
		for (Entity e : tp.hidup.values()) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
		tp.hidup.clear();
	}

	// ================================================================== candidates

	/** Every possible animal of a place, from its titik (deterministic). */
	static List<Calon> rencana(ServerLevel level, TempatHuni huni) {
		List<Calon> out = new ArrayList<>();
		String jenis = huni.jenis;
		long situs = huni.situsId;
		boolean iblis = jenis.equals("kota_tema/iblis");
		boolean kampung = jenis.startsWith("kampung/");
		// cats
		List<Titik> kucing = huni.titik(KotaTitik.KUCING_TIDUR);
		if (kucing.isEmpty() && (kampung || jenis.startsWith("pos/"))) {
			kucing = huni.titik("pintu");
		}
		Titik lao = jenis.equals("kota/kota_besar") ? laoHuaTitik(kucing, huni.titik("lapak")) : null;
		for (Titik t : kucing) {
			if (t == lao) {
				out.add(new Calon(kunci(LAO_HUA, t.pos(), 0), LAO_HUA, t.pos(), t.hadap().toYRot(), KucingKotaEntity.VARIAN_ORANYE, 0, null, ""));
				continue;
			}
			int v = iblis ? KucingKotaEntity.VARIAN_HITAM : acak(situs, t.pos(), 1, KucingKotaEntity.VARIAN_JUMLAH);
			out.add(new Calon(kunci(KUCING, t.pos(), 0), KUCING, t.pos(), t.hadap().toYRot(), v, 0, null, ""));
		}
		// dogs: the gate guard, then doors and the cook's yard
		for (Titik t : huni.titik("jaga")) {
			if (acak(situs, t.pos(), 2, 3) == 0) {
				out.add(new Calon(kunci(ANJING, t.pos(), 0), ANJING, t.pos(), t.hadap().toYRot(), AnjingKotaEntity.VARIAN_PENJAGA, 0, null, ""));
			}
		}
		List<Titik> pintu = new ArrayList<>(huni.titik("pintu"));
		pintu.addAll(huni.titik("kerja:kuali"));
		for (Titik t : pintu) {
			if (acak(situs, t.pos(), 3, 4) != 0) {
				continue;
			}
			int v = iblis ? AnjingKotaEntity.VARIAN_HITAM : acak(situs, t.pos(), 4, 3);
			out.add(new Calon(kunci(ANJING, t.pos(), 1), ANJING, t.pos(), t.hadap().toYRot(), v, 0, null, ""));
		}
		jagaCalon(level.getServer(), situs, out);
		// pens
		for (Titik t : huni.titik(KotaTitik.KANDANG)) {
			kandang(level, situs, t, out);
		}
		// hitching rails
		boolean gurun = jenis.equals("kota_tema/oasis");
		for (Titik t : huni.titik(KotaTitik.TAMBAT)) {
			boolean desertPost = !gurun && jenis.startsWith("pos/") && level.getBiome(t.pos()).is(PutusanTema.OASIS);
			String hewan;
			int roll = acak(situs, t.pos(), 5, 4);
			if (gurun || desertPost) {
				hewan = roll == 0 ? KUDA : UNTA;
			} else if (kampung || jenis.equals("kota_tema/gunung")) {
				hewan = roll == 0 ? KUDA : KELEDAI;
			} else if (jenis.startsWith("pos/")) {
				hewan = roll == 3 ? KELEDAI : KUDA;
			} else {
				hewan = KUDA;
			}
			int data = 0;
			if (KELEDAI.equals(hewan)) {
				data |= t.angka("giling", 0) == 1 ? 2 : 0;
				data |= jenis.startsWith("pos/") || jenis.startsWith("kota") ? 1 : 0;
			}
			out.add(new Calon(kunci(hewan, t.pos(), 0), hewan, t.pos(), t.hadap().toYRot(), varian(hewan, situs, t.pos()), data, null, ""));
		}
		// pigeon flocks on temple fronts and well squares
		if (!iblis && !kampung && !jenis.startsWith("pos/")) {
			List<Titik> spots = new ArrayList<>(huni.titik("dupa"));
			spots.addAll(huni.titik(KotaTitik.SUMUR));
			List<BlockPos> pakai = new ArrayList<>();
			for (Titik t : spots) {
				boolean jauh = true;
				for (BlockPos p : pakai) {
					if (p.distSqr(t.pos()) < 20.0D * 20.0D) {
						jauh = false;
						break;
					}
				}
				if (jauh) {
					pakai.add(t.pos());
					out.add(new Calon(kunci(MERPATI, t.pos(), 0), MERPATI, t.pos(), t.hadap().toYRot(),
							acak(situs, t.pos(), 6, KawananMerpatiEntity.VARIAN_JUMLAH), 0, null, ""));
				}
			}
		}
		return out;
	}

	/** The pen's animals: chickens as a flock (rooster, hens, chicks), else {@code jumlah} of its kind. */
	private static void kandang(ServerLevel level, long situs, Titik t, List<Calon> out) {
		String hewan = t.data().getOrDefault("hewan", "ayam_kota");
		int n = Math.clamp(t.angka("jumlah", 2), 1, 8);
		String pen = kunci("kandang", t.pos(), 0);
		float yaw = t.hadap().toYRot();
		for (int i = 0; i < n; i++) {
			// spread the pen's animals over its floor (pens are at least 6 x 6)
			BlockPos p = t.pos().offset((i % 3) - 1, 0, (i / 3) - 1);
			switch (hewan) {
				case "ayam_kota" -> {
					int v = i == 0 ? AyamKotaEntity.VARIAN_JAGO : i <= Math.max(1, n / 3) ? AyamKotaEntity.VARIAN_BETINA : AyamKotaEntity.VARIAN_ANAK;
					out.add(new Calon(kunci(AYAM, t.pos(), i), AYAM, p, yaw, v, i, null, pen));
				}
				case "kuda_tambat" -> out.add(new Calon(kunci(KUDA, t.pos(), i), KUDA, p, yaw, varian(KUDA, situs, p), 0, null, ""));
				case "unta" -> out.add(new Calon(kunci(UNTA, t.pos(), i), UNTA, p, yaw, varian(UNTA, situs, p), 0, null, ""));
				case "keledai" -> out.add(new Calon(kunci(KELEDAI, t.pos(), i), KELEDAI, p, yaw, varian(KELEDAI, situs, p), 0, null, ""));
				case "anjing_kota" -> out.add(new Calon(kunci(ANJING, t.pos(), 10 + i), ANJING, p, yaw, acak(situs, p, 4, 3), 0, null, ""));
				case "merpati" -> {
					if (i == 0) {
						out.add(new Calon(kunci(MERPATI, t.pos(), 0), MERPATI, t.pos(), yaw, acak(situs, t.pos(), 6, KawananMerpatiEntity.VARIAN_JUMLAH),
								0, null, ""));
					}
				}
				default -> {
				}
			}
		}
	}

	private static int varian(String hewan, long situs, BlockPos pos) {
		return switch (hewan) {
			case KUDA -> acak(situs, pos, 7, KudaTambatEntity.VARIAN_JUMLAH);
			case UNTA -> acak(situs, pos, 8, UntaTambatEntity.VARIAN_JUMLAH);
			case KELEDAI -> acak(situs, pos, 9, KeledaiKotaEntity.VARIAN_JUMLAH);
			default -> 0;
		};
	}

	/** Lao Hua's roof: the cat titik nearest any market stall of the capital. */
	private static @Nullable Titik laoHuaTitik(List<Titik> kucing, List<Titik> lapak) {
		Titik best = null;
		double bestD = Double.MAX_VALUE;
		for (Titik k : kucing) {
			for (Titik l : lapak) {
				double d = k.pos().distSqr(l.pos());
				if (d < bestD || d == bestD && best != null && k.pos().asLong() < best.pos().asLong()) {
					bestD = d;
					best = k;
				}
			}
		}
		return best != null ? best : kucing.isEmpty() ? null : kucing.getFirst();
	}

	static String kunci(String jenis, BlockPos pos, int i) {
		return jenis + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "#" + i;
	}

	/** A deterministic pick 0..n-1 from the place, the block and a salt. */
	static int acak(long situs, BlockPos pos, int garam, int n) {
		long h = situs * 0x9E3779B97F4A7C15L ^ pos.asLong() * 0xBF58476D1CE4E5B9L ^ garam * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 32;
		return (int) Math.floorMod(h, (long) Math.max(1, n));
	}

	// ================================================================== spawning

	private static @Nullable Entity muncul(ServerLevel level, Calon c, Tempat tp) {
		EntityType<? extends Entity> type = switch (c.jenis()) {
			case KUCING, LAO_HUA -> HewanEntities.KUCING_KOTA;
			case ANJING, ANJING_JAGA -> HewanEntities.ANJING_KOTA;
			case AYAM -> HewanEntities.AYAM_KOTA;
			case KUDA -> HewanEntities.KUDA_TAMBAT;
			case UNTA -> HewanEntities.UNTA_TAMBAT;
			case KELEDAI -> HewanEntities.KELEDAI_KOTA;
			case MERPATI -> HewanEntities.KAWANAN_MERPATI;
			default -> null;
		};
		if (type == null) {
			return null;
		}
		Entity e = buat(level, type, Vec3.atBottomCenterOf(c.pos()), c.yaw());
		if (e == null) {
			return null;
		}
		if (e instanceof HewanKotaEntity h) {
			h.ikat(tp.situs, c.pos().asLong());
			h.setKunci(c.kunci());
			h.setVarian(c.varian());
		}
		switch (e) {
			case KucingKotaEntity k -> {
				if (LAO_HUA.equals(c.jenis())) {
					k.jadikanLaoHua();
				}
				// cats are found asleep on their roofs
				k.setKeadaan(KucingKotaEntity.KEADAAN_TIDUR);
			}
			case AnjingKotaEntity a -> {
				if (c.tuan() != null) {
					a.setTuan(c.tuan());
					a.setCustomName(Component.translatable("kultivator.kota42.hewan.nama.anjing_" + c.varian()));
				}
			}
			case KeledaiKotaEntity d -> {
				d.setMuatan((c.data() & 1) != 0);
				d.setGiling((c.data() & 2) != 0);
			}
			case KawananMerpatiEntity m -> {
				m.ikat(tp.situs, c.pos().asLong());
				m.setVarian(c.varian());
				m.setJumlah(KawananMerpatiEntity.JUMLAH_MIN + acak(tp.situs, c.pos(), 10, KawananMerpatiEntity.JUMLAH_MAKS - KawananMerpatiEntity.JUMLAH_MIN + 1));
				m.setBenih((int) (c.pos().asLong() ^ tp.situs));
				m.setTempat(Vec3.atBottomCenterOf(c.pos()), tp.tempatMerpati);
			}
			default -> {
			}
		}
		if (!level.addFreshEntity(e)) {
			return null;
		}
		DuniaBudget.track(KotaAcaraEntities.ANGGARAN, e);
		return e;
	}

	/** Creates an animal of {@code type} at {@code pos} (not yet added). */
	private static <T extends Entity> @Nullable T buat(ServerLevel level, EntityType<T> type, Vec3 pos, float yaw) {
		T e;
		try {
			e = type.create(level, EntitySpawnReason.EVENT);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Town animal {} could not be made", type, exception);
			return null;
		}
		if (e == null) {
			return null;
		}
		e.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		e.setYHeadRot(yaw);
		return e;
	}

	/** Links each chick to a hen of its pen (after both are made). */
	private static void hubungkanAnakAyam(Tempat tp) {
		Map<String, AyamKotaEntity> induk = new HashMap<>();
		for (Entity e : tp.hidup.values()) {
			if (e instanceof AyamKotaEntity a && a.varian() == AyamKotaEntity.VARIAN_BETINA) {
				Calon c = cari(tp, a.kunci());
				if (c != null) {
					induk.putIfAbsent(c.kandang(), a);
				}
			}
		}
		for (Entity e : tp.hidup.values()) {
			if (e instanceof AyamKotaEntity a && a.varian() == AyamKotaEntity.VARIAN_ANAK) {
				Calon c = cari(tp, a.kunci());
				if (c != null) {
					a.setInduk(induk.get(c.kandang()));
				}
			}
		}
	}

	// ================================================================== event and test animals

	private static void tickAcara(MinecraftServer server) {
		if (ACARA.isEmpty() && UJI.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<Entity, Long>> it = ACARA.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Entity, Long> e = it.next();
			Entity a = e.getKey();
			boolean jauh = a.level() instanceof ServerLevel l && l.getNearestPlayer(a, 128.0D) == null;
			if (a.isRemoved() || now >= e.getValue() || jauh) {
				if (!a.isRemoved()) {
					a.discard();
				}
				it.remove();
			}
		}
		for (Iterator<Map.Entry<Entity, Integer>> it = UJI.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Entity, Integer> e = it.next();
			Entity a = e.getKey();
			Long sampai = ACARA_UJI.get(a);
			if (a.isRemoved() || sampai == null || now >= sampai) {
				if (!a.isRemoved()) {
					a.discard();
				}
				ACARA_UJI.remove(a);
				it.remove();
				continue;
			}
			if (a instanceof HewanKotaEntity h && Math.floorMod(now, 60L) == 0L) {
				// the lineup shows every state for 3 s in turn
				h.setKeadaan((h.keadaan() + 1) % Math.max(1, e.getValue()));
			}
		}
	}

	/** Adds a test lineup animal for {@code ticks} cycling {@code states} states (HewanUji). */
	static void uji(Entity e, int states, long ticks) {
		UJI.put(e, states);
		ACARA_UJI.put(e, e.level().getGameTime() + ticks);
		DuniaBudget.track(KotaAcaraEntities.ANGGARAN, e);
	}

	/** Makes a test animal at {@code pos} (HewanUji); added to the level, not yet registered. */
	static <T extends Entity> @Nullable T buatUji(ServerLevel level, EntityType<T> type, Vec3 pos, float yaw) {
		T e = buat(level, type, pos, yaw);
		if (e == null || !level.addFreshEntity(e)) {
			return null;
		}
		return e;
	}

	/** Live animals per active place (HewanUji {@code daftar}). */
	static List<String> ringkas() {
		List<String> out = new ArrayList<>();
		for (Tempat tp : TEMPAT.values()) {
			Map<String, Integer> m = new HashMap<>();
			for (Map.Entry<String, Entity> e : tp.hidup.entrySet()) {
				Calon c = cari(tp, e.getKey());
				m.merge(c == null ? "?" : c.jenis(), 1, Integer::sum);
			}
			out.add(tp.situs + " " + tp.jenis + " " + tp.hidup.size() + "/" + tp.calon.size() + " " + m);
		}
		return out;
	}

	// ================================================================== contracts (C42.D1 and friends)

	/**
	 * Spawns the animal that belongs to {@code titik} (kind by titik kind and its data) now, whatever the player distance,
	 * within the world caps; null when none may come. The animal is bound to the place at the titik and taken back like
	 * every other animal of that place.
	 */
	public static @Nullable Entity panggil(ServerLevel level, Titik titik) {
		if (!aktif() || !KotaHidup.duniaKota(level) || jumlahDunia() >= MAKS_DUNIA || !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
			return null;
		}
		TempatInfo info = TempatApi.tempatDi(level, titik.pos());
		if (info == null || !bolehTempat(info.jenis())) {
			return null;
		}
		Tempat tp = TEMPAT.computeIfAbsent(info.situsId(), k -> new Tempat(info.situsId(), info.jenis()));
		TempatHuni huni = Pemanggil.huni(info.situsId());
		if (huni != null && (tp.titikN != huni.jumlahTitik() || tp.calon.isEmpty())) {
			tp.titikN = huni.jumlahTitik();
			tp.jagaVersi = jagaVersi;
			tp.calon = rencana(level, huni);
		}
		for (Calon c : tp.calon) {
			if (c.pos().closerThan(titik.pos(), 2.0D) && !tp.hidup.containsKey(c.kunci())) {
				Entity e = muncul(level, c, tp);
				if (e != null) {
					tp.hidup.put(c.kunci(), e);
				}
				return e;
			}
		}
		return null;
	}

	/** Lao Hua if he is about in place {@code situs} now, else null (42B's {@code kucing_hilang}, Lao Hua's lines). */
	public static @Nullable KucingKotaEntity laoHua(long situs) {
		Tempat tp = TEMPAT.get(situs);
		if (tp == null) {
			return null;
		}
		for (Entity e : tp.hidup.values()) {
			if (e instanceof KucingKotaEntity k && k.laoHua() && !k.isRemoved()) {
				return k;
			}
		}
		return null;
	}

	/** Live animals of place {@code situs} (copy). */
	public static List<Entity> hewanDi(long situs) {
		Tempat tp = TEMPAT.get(situs);
		return tp == null ? List.of() : List.copyOf(tp.hidup.values());
	}

	/** Sends every landed pigeon flock of {@code situs} up (temple bell, firecrackers, a crowd's roar). */
	public static void merpatiTerbang(ServerLevel level, long situs) {
		for (Entity e : hewanDi(situs)) {
			if (e instanceof KawananMerpatiEntity m) {
				m.kagetkan(m.position().add(level.getRandom().nextDouble() - 0.5D, 0.0D, level.getRandom().nextDouble() - 0.5D));
			}
		}
	}

	/**
	 * The loose-chicken street event (42.7.4, 42D-P): {@code n} chickens burst out at {@code pos} and scatter; a player
	 * catches one with a click ({@link #addPenangkapAyam} hears it). They are taken back after {@code detik} seconds or when
	 * nobody is within 128 blocks. Returns the chickens made (fewer under the caps).
	 */
	public static List<AyamKotaEntity> ayamLepas(ServerLevel level, long situs, BlockPos pos, int n, int detik) {
		List<AyamKotaEntity> out = new ArrayList<>();
		if (!aktif() || !KotaConfig.hewan().ayam || !KotaHidup.duniaKota(level)) {
			return out;
		}
		long now = level.getGameTime();
		for (int i = 0; i < n; i++) {
			if (jumlahDunia() >= MAKS_DUNIA || !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				break;
			}
			Vec3 at = Vec3.atBottomCenterOf(pos).add(level.getRandom().nextDouble() * 2.0D - 1.0D, 0.0D, level.getRandom().nextDouble() * 2.0D - 1.0D);
			AyamKotaEntity a = buat(level, HewanEntities.AYAM_KOTA, at, level.getRandom().nextFloat() * 360.0F);
			if (a == null) {
				break;
			}
			a.ikat(situs, pos.asLong());
			a.setVarian(i == 0 ? AyamKotaEntity.VARIAN_JAGO : AyamKotaEntity.VARIAN_BETINA);
			a.setLepas(true);
			if (!level.addFreshEntity(a)) {
				break;
			}
			DuniaBudget.track(KotaAcaraEntities.ANGGARAN, a);
			ACARA.put(a, now + Math.max(20, detik) * 20L);
			a.pose(AyamKotaEntity.KEADAAN_KABUR, 30);
			out.add(a);
		}
		if (!out.isEmpty()) {
			level.playSound(null, pos, SoundEvents.CHICKEN_SOUNDS.get(net.minecraft.world.entity.animal.chicken.ChickenSoundVariants.SoundSet.CLASSIC)
					.adultSounds().hurtSound().value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
		}
		return out;
	}

	/** Listens to caught loose chickens (42D-P counts them toward the "catch 3" goal and pays the reward). */
	public static void addPenangkapAyam(BiConsumer<ServerPlayer, AyamKotaEntity> penangkap) {
		if (penangkap != null) {
			PENANGKAP.add(penangkap);
		}
	}

	/** A player caught a loose chicken: feathers fly, the chicken goes back to its pen (removed), listeners hear it. */
	public static void ayamDitangkap(ServerPlayer player, AyamKotaEntity ayam) {
		if (ayam.isRemoved() || !ayam.lepas()) {
			return;
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, ayam.getX(), ayam.getY() + 0.4D, ayam.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.02D);
			level.playSound(null, ayam.blockPosition(), SoundEvents.CHICKEN_SOUNDS.get(net.minecraft.world.entity.animal.chicken.ChickenSoundVariants.SoundSet.CLASSIC)
					.adultSounds().ambientSound().value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
		ayam.setLepas(false);
		ACARA.remove(ayam);
		ayam.discard();
		if (PENANGKAP.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.ayam.tangkap"));
		}
		for (BiConsumer<ServerPlayer, AyamKotaEntity> p : PENANGKAP) {
			try {
				p.accept(player, ayam);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Loose chicken listener failed", exception);
			}
		}
	}

	/**
	 * The groom's horse of a wedding procession (42.7.7, 42D-P): a dressed horse at {@code pos}, untied (the event leads it
	 * and seats the groom), taken back after 20 minutes at the latest or when nobody is near; the caller discards it when
	 * the procession ends. Null under the caps.
	 */
	public static @Nullable KudaTambatEntity kudaPengantin(ServerLevel level, long situs, BlockPos pos, float yaw) {
		if (!aktif() || !KotaHidup.duniaKota(level) || jumlahDunia() >= MAKS_DUNIA || !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
			return null;
		}
		KudaTambatEntity kuda = buat(level, HewanEntities.KUDA_TAMBAT, Vec3.atBottomCenterOf(pos), yaw);
		if (kuda == null) {
			return null;
		}
		kuda.ikat(situs, pos.asLong());
		kuda.setVarian(acak(situs, pos, 11, 2) == 0 ? KudaTambatEntity.VARIAN_PUTIH : KudaTambatEntity.VARIAN_COKLAT);
		kuda.setHias(true);
		kuda.setDituntun(true);
		if (!level.addFreshEntity(kuda)) {
			return null;
		}
		DuniaBudget.track(KotaAcaraEntities.ANGGARAN, kuda);
		ACARA.put(kuda, level.getGameTime() + ACARA_MAKS);
		return kuda;
	}

	// ================================================================== guard dogs

	/**
	 * A player gave a dog a bone. With a house (owned or rented) or a shop in the dog's place, the dog becomes the guard of
	 * that house's door (else the shop's front) from now on: it is made there whenever a player is near, keeps 4 blocks of
	 * it, never barks at its master, and {@link #dijaga} tells the shop day that night thieves stay away. A player has one
	 * guard dog per place; a second bone to another dog only gets a wagging tail. Without a house or shop here the dog
	 * follows the giver for one game hour. Returns the chat line, or null when the bone is refused (never: a dog always
	 * takes a bone).
	 */
	public static @Nullable Component beriTulang(ServerPlayer player, AnjingKotaEntity anjing) {
		MinecraftServer server = player.level().getServer();
		long situs = anjing.situsId();
		if (situs == 0L) {
			TempatInfo info = TempatApi.tempatDi(player.level(), anjing.blockPosition());
			situs = info == null ? 0L : info.situsId();
		}
		Component nama = Component.translatable("kultivator.kota42.hewan.nama.anjing_" + anjing.varian());
		if (player.getUUID().equals(anjing.tuan())) {
			anjing.ikuti(player, 200);
			return Component.translatable("kultivator.kota42.hewan.anjing.tuan", nama);
		}
		BlockPos pos = null;
		String apa = "";
		if (situs != 0L) {
			try {
				RumahKota.Info rumah = RumahKota.milikDiTempat(server, situs, player.getUUID());
				if (rumah != null) {
					pos = rumah.pintu().relative(rumah.hadap());
					apa = "rumah";
				} else {
					for (TokoPemain.Toko toko : TokoPemain.milik(server, player.getUUID())) {
						if (toko.situs() == situs) {
							pos = toko.pos();
							apa = "toko";
							break;
						}
					}
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Guard dog: house/shop lookup failed", exception);
			}
		}
		if (pos == null) {
			anjing.ikuti(player, KucingKotaEntity.IKUT_IKAN);
			return Component.translatable("kultivator.kota42.hewan.anjing.ikut", Component.translatable("kultivator.kota42.hewan.anjing"));
		}
		KotaAcaraData data = KotaAcaraData.get(server);
		CompoundTag jaga = data.situs(KotaAcaraData.HEWAN, situs).getCompoundOrEmpty("jaga");
		String key = player.getUUID().toString();
		if (jaga.contains(key)) {
			anjing.ikuti(player, 200);
			int v = jaga.getCompoundOrEmpty(key).getIntOr("v", 0);
			return Component.translatable("kultivator.kota42.hewan.anjing.sudah",
					Component.translatable("kultivator.kota42.hewan.nama.anjing_" + v));
		}
		CompoundTag c = new CompoundTag();
		c.putInt("x", pos.getX());
		c.putInt("y", pos.getY());
		c.putInt("z", pos.getZ());
		c.putInt("v", anjing.varian());
		c.putString("t", apa);
		jaga.put(key, c);
		data.situs(KotaAcaraData.HEWAN, situs).put("jaga", jaga);
		data.ubah();
		// this very dog walks to its post as the guard; the street candidate it came from fills again later
		Tempat tp = TEMPAT.get(situs);
		if (tp != null) {
			tp.hidup.values().remove(anjing);
			tp.hidup.put(kunci(ANJING_JAGA, pos, 0) + key, anjing);
		}
		anjing.setTuan(player.getUUID());
		anjing.setKunci(kunci(ANJING_JAGA, pos, 0) + key);
		anjing.pindahRumah(pos);
		anjing.setCustomName(nama);
		anjing.berhentiIkut();
		jagaVersi++;
		Component tempat;
		try {
			tempat = PajakKota.nama(server, situs);
		} catch (RuntimeException exception) {
			tempat = Component.literal("?");
		}
		return Component.translatable("toko".equals(apa) ? "kultivator.kota42.hewan.anjing.jaga_toko" : "kultivator.kota42.hewan.anjing.jaga_rumah",
				nama, tempat);
	}

	/** True when {@code pemilik} has a guard dog in place {@code situs} (night thieves keep off his shop there). */
	public static boolean dijaga(MinecraftServer server, long situs, UUID pemilik) {
		if (server == null || pemilik == null || situs == 0L) {
			return false;
		}
		return KotaAcaraData.get(server).situs(KotaAcaraData.HEWAN, situs).getCompoundOrEmpty("jaga").contains(pemilik.toString());
	}

	/** Releases {@code pemilik}'s guard dog of {@code situs} (house sold, test reset). */
	public static void lepasJaga(MinecraftServer server, long situs, UUID pemilik) {
		KotaAcaraData data = KotaAcaraData.get(server);
		CompoundTag jaga = data.situs(KotaAcaraData.HEWAN, situs).getCompoundOrEmpty("jaga");
		if (jaga.remove(pemilik.toString()) != null) {
			data.situs(KotaAcaraData.HEWAN, situs).put("jaga", jaga);
			data.ubah();
			jagaVersi++;
		}
	}

	private static void jagaCalon(MinecraftServer server, long situs, List<Calon> out) {
		CompoundTag jaga = KotaAcaraData.get(server).situs(KotaAcaraData.HEWAN, situs).getCompoundOrEmpty("jaga");
		for (String key : jaga.keySet()) {
			UUID owner;
			try {
				owner = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			CompoundTag c = jaga.getCompoundOrEmpty(key);
			BlockPos pos = new BlockPos(c.getIntOr("x", 0), c.getIntOr("y", 0), c.getIntOr("z", 0));
			int v = Math.clamp(c.getIntOr("v", 0), 0, AnjingKotaEntity.VARIAN_JUMLAH - 1);
			out.add(new Calon(kunci(ANJING_JAGA, pos, 0) + key, ANJING_JAGA, pos, 0.0F, v, 0, owner, ""));
		}
	}

	private HewanKota() {
	}
}

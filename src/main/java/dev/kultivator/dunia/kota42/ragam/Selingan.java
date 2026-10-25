package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Kepribadian;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.hidup.TubuhPenduduk;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Micro interludes (DESIGN 42.4A.5): while a body is at its goal, every {@code kotaWarga.selinganMin..selinganMaks} seconds
 * (8-30, a pace of its own per resident) it slips in a 1-3 s interlude that fits the activity and is never the one before:
 * looking round, a stretch, wiping the sweat (heat, heavy work), checking the goods, glancing at a passing player, a word to a
 * neighbour, scratching the head, a yawn, setting the hat straight, a drink from the jug, a whistled tune (the 73 murmur),
 * dusting off, counting coins, waving at a child. Weights follow the traits (E: looks round more, X: talks more) and the
 * state (after two hours of work: sweat and stretch x2). Registered through {@code TubuhPenduduk.addSelingan}; it returns
 * true only on the pass it plays, so the role's own once clips (the vendor's call, the gavel) keep their turns.
 * Timers live in the body's memory ({@code rg_*} keys); counts per place and day for 42S's metrics ({@link #statistik}).
 */
public final class Selingan {

	private static final String T = "rg_sel_t";
	private static final String ID = "rg_sel_id";
	private static final String TATAP = "rg_sel_tatap";
	private static final String KERJA0 = "rg_kerja0";
	/** Two world hours of work make a body tired (sweat and stretch x2). */
	private static final long LELAH = 2000L;

	/** The last interlude of one resident: what and how often today. */
	public record Terakhir(KumpulanKegiatan.SelinganDef def, long hari, int kali) {
	}

	/**
	 * One place's interludes on one day.
	 *
	 * @param putar      interludes played
	 * @param detikTubuh body-seconds at a goal (one per pass)
	 * @param berturut   the same interlude twice in a row (target 0)
	 */
	public record Statistik(long putar, long detikTubuh, int berturut) {
		public static final Statistik KOSONG = new Statistik(0L, 0L, 0);

		/** Interludes per real minute per body (target 2-7). */
		public double perMenit() {
			return detikTubuh <= 0L ? 0.0D : putar * 60.0D / detikTubuh;
		}
	}

	/** Per place: {@code {hari, putar, detik, berturut}}. */
	private static final Map<Long, long[]> STAT = new HashMap<>();
	private static final Map<Long, Map<Integer, Terakhir>> TERAKHIR = new HashMap<>();

	/** The day's interlude counts of place {@code situs} (empty for another day). */
	public static Statistik statistik(long situs, long hari) {
		long[] s = STAT.get(situs);
		return s == null || s[0] != hari ? Statistik.KOSONG : new Statistik(s[1], s[2], (int) s[3]);
	}

	/** The last interlude of resident {@code slot} of place {@code situs}, or null. */
	public static @Nullable Terakhir terakhir(long situs, int slot) {
		Map<Integer, Terakhir> per = TERAKHIR.get(situs);
		return per == null ? null : per.get(slot);
	}

	private static long[] stat(long situs, long hari) {
		long[] s = STAT.computeIfAbsent(situs, k -> new long[]{hari, 0L, 0L, 0L});
		if (s[0] != hari) {
			s[0] = hari;
			s[1] = 0L;
			s[2] = 0L;
			s[3] = 0L;
		}
		return s;
	}

	/** {@link TubuhPenduduk.PenyediaSelingan}: true on the pass it played an interlude. */
	public static boolean selingan(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, Kegiatan k, long now) {
		if (!KotaConfig.warga().sebaranAktif) {
			return false;
		}
		CompoundTag m = npc.memory();
		long waktu = level.getServer().overworld().getOverworldClockTime();
		long hari = Math.floorDiv(waktu, 24000L);
		long[] stat = stat(huni.situsId, hari);
		stat[2]++;
		if (m.contains(TATAP) && now >= m.getLongOr(TATAP, 0L)) {
			HidupApi.tatap(npc, null);
			m.remove(TATAP);
		}
		int jam = Jadwal.saat(p, huni.jenis, waktu).jam();
		boolean kerja = RencanaHarian.blok(k, jam) == KumpulanKegiatan.Blok.KERJA;
		if (kerja) {
			if (!m.contains(KERJA0)) {
				m.putLong(KERJA0, now);
			}
		} else {
			m.remove(KERJA0);
		}
		SplittableRandom r = new SplittableRandom(p.benih() ^ now * 0x9E3779B97F4A7C15L);
		long next = m.getLongOr(T, 0L);
		if (next == 0L || next > now + 20L * 200L) {
			m.putLong(T, now + jeda(p, r));
			return false;
		}
		if (now < next) {
			return false;
		}
		m.putLong(T, now + jeda(p, r));
		RencanaHarian.Rencana rencana = RencanaHarian.rencanaHemat(huni, p, hari, now);
		RencanaHarian.Langkah step = rencana == null ? null : rencana.pada(RencanaHarian.menit(waktu));
		KumpulanKegiatan.SelinganDef def = undi(level, huni, npc, p, step, kerja && now - m.getLongOr(KERJA0, now) >= LELAH, hari,
				m.getStringOr(ID, ""), r);
		if (def == null) {
			return false;
		}
		if (!mainkan(level, npc, def, now, r)) {
			return false;
		}
		if (def.id().equals(m.getStringOr(ID, ""))) {
			stat[3]++;
		}
		m.putString(ID, def.id());
		stat[1]++;
		MetrikRagam.selingan(huni.situsId, p.id(), def.id());
		Map<Integer, Terakhir> per = TERAKHIR.computeIfAbsent(huni.situsId, s -> new HashMap<>());
		Terakhir lalu = per.get(p.id());
		per.put(p.id(), new Terakhir(def, hari, lalu != null && lalu.hari() == hari ? lalu.kali() + 1 : 1));
		return true;
	}

	/** Ticks until the next interlude: the resident's own pace (9-28 s within the config range) +-20 %. */
	private static long jeda(Penduduk p, SplittableRandom r) {
		int min = Math.max(9, KotaConfig.warga().selinganMin);
		int max = Math.max(min, Math.min(28, KotaConfig.warga().selinganMaks));
		double dasar = min + Math.floorMod(p.benih() >>> 11, (long) (max - min + 1));
		return Math.round(dasar * (0.8D + r.nextDouble() * 0.4D) * 20.0D);
	}

	private static KumpulanKegiatan.@Nullable SelinganDef undi(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p,
			RencanaHarian.@Nullable Langkah step, boolean lelah, long hari, String lalu, SplittableRandom r) {
		KumpulanKegiatan.Data d = KumpulanKegiatan.data();
		if (d.selingan().isEmpty()) {
			return null;
		}
		KumpulanKegiatan.Aktivitas a = step != null ? step.aktivitas() : null;
		List<String> boleh = a != null ? a.selingan() : List.of();
		boolean berat = a != null && a.ada("berat");
		boolean panas = IramaMingguan.musim(hari) == Jadwal.PANAS || berat;
		var w = WargaApi.warga(huni.situsId, p.id());
		Kepribadian.Sifat sifat = RencanaHarian.sifat(w, p);
		List<KumpulanKegiatan.SelinganDef> calon = new ArrayList<>();
		List<Double> bobot = new ArrayList<>();
		double total = 0.0D;
		for (KumpulanKegiatan.SelinganDef s : d.selingan()) {
			if (s.id().equals(lalu) || !boleh.isEmpty() && !boleh.contains(s.id()) || s.tanda().contains("berat") && !berat && !panas) {
				continue;
			}
			double b = s.bobot();
			for (Map.Entry<Character, Double> e : s.sifat().entrySet()) {
				b *= Math.max(0.2D, 1.0D + e.getValue() * sifat.get(e.getKey()) / 100.0D);
			}
			if (lelah) {
				b *= s.lelah();
			}
			if (panas) {
				b *= s.panas();
			}
			if (b <= 0.0D || !ada(level, npc, s.perlu())) {
				continue;
			}
			calon.add(s);
			bobot.add(b);
			total += b;
		}
		if (calon.isEmpty()) {
			return null;
		}
		double x = r.nextDouble() * total;
		for (int i = 0; i < calon.size(); i++) {
			x -= bobot.get(i);
			if (x < 0.0D) {
				return calon.get(i);
			}
		}
		return calon.getLast();
	}

	/** True when what an interlude needs is near: a player (12 blocks), a neighbour body (5), a child body (8). */
	private static boolean ada(ServerLevel level, KultivatorNpc npc, String perlu) {
		return switch (perlu) {
			case "pemain" -> level.getNearestPlayer(npc, 12.0D) != null;
			case "tetangga" -> dekat(level, npc, 5.0D, false) != null;
			case "anak" -> dekat(level, npc, 8.0D, true) != null;
			default -> true;
		};
	}

	/** The nearest other resident body (a child when {@code anak}) within {@code r} blocks. */
	private static @Nullable KultivatorNpc dekat(ServerLevel level, KultivatorNpc npc, double r, boolean anak) {
		KultivatorNpc best = null;
		double bestD = r * r;
		for (KultivatorNpc o : level.getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(r),
				e -> e != npc && e.isAlive() && TubuhPenduduk.adalah(e))) {
			if (anak && o.role().kelompok != NpcRole.Kelompok.ANAK) {
				continue;
			}
			double dd = o.distanceToSqr(npc);
			if (dd < bestD) {
				bestD = dd;
				best = o;
			}
		}
		return best;
	}

	private static boolean mainkan(ServerLevel level, KultivatorNpc npc, KumpulanKegiatan.SelinganDef s, long now, SplittableRandom r) {
		CompoundTag m = npc.memory();
		switch (s.aksi()) {
			case "tatap" -> {
				Player player = level.getNearestPlayer(npc, 12.0D);
				if (player == null) {
					return false;
				}
				HidupApi.tatap(npc, player);
				m.putLong(TATAP, now + s.tick());
				return true;
			}
			case "sapa" -> {
				KultivatorNpc other = dekat(level, npc, 8.0D, s.perlu().equals("anak"));
				if (other == null) {
					return false;
				}
				HidupApi.tatap(npc, other);
				m.putLong(TATAP, now + s.tick());
				SosokApi.gerak(npc, s.klip(), s.varian(), s.tick(), s.kanan(), s.kiri(), 0);
				// the other nods (a child waves back)
				SosokApi.gerak(other, other.role().kelompok == NpcRole.Kelompok.ANAK ? GerakId.LAMBAI : GerakId.ANGGUK_DENGAR, 0, s.tick(), 0, 0, 0);
				return true;
			}
			case "siul" -> {
				SosokApi.gerak(npc, s.klip(), s.varian(), s.tick(), 0, 0, 0);
				npc.say(Component.translatable("kultivator.hidup.obrol.rg_siul." + r.nextInt(4)), s.tick() + 20);
				return true;
			}
			default -> {
				if (s.klip() == 0) {
					return false;
				}
				SosokApi.gerak(npc, s.klip(), s.varian(), s.tick(), s.kanan(), s.kiri(), 0);
				return true;
			}
		}
	}

	private Selingan() {
	}
}

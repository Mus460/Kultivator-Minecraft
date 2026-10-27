package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarKalender;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The town calendar (DESIGN 42.7, contract C42.D1) and the hub of 42D Kehidupan &amp; Acara.
 * <ul>
 * <li><b>Calendar.</b> Stored entries ({@link #daftar}: ceremonies of 42D-P, anything another module books) live in
 * {@code KotaAcaraData.bagian("kalender")} per place; computed entries come from {@link Sumber sources} (the six troupes,
 * festivals, the night watch, the morning market, the shadow theatre, and whatever 42E/42F register). {@link #acara}
 * merges both for a range of days, {@link #lembarAcara} writes the {@code kota_buku} Acara rows (42U-1) and the capital's
 * existing {@code acara} screen lists them too ({@link KotaBesarKalender#addSumberJadwal}).</li>
 * <li><b>Clock.</b> {@link #jam}, {@link #menit}, {@link #hari}, {@link #musim}, {@link #hariMusim}: one reading of the
 * overworld clock for every 42D system (tick 0 = 06:00, day {@code clock / 24000}, season {@code (day / 24) % 4}).</li>
 * <li><b>Atmosphere.</b> {@code kultivator:kota_suasana} (42.13.2) to every player within 160 blocks of a place, when it
 * changes and every 30 s: event bits {@code BIT_*}, prosperity (42F), mood (42A), season and festival ({@link MusimKota}),
 * protector (42E), lantern mode {@code LENTERA_*} and the houses marked by ceremonies ({@link #tandaiRumah}).</li>
 * <li><b>Town buffs</b> ({@link Berkah}): <i>Terhibur</i> (+0.01 comprehension a minute for 10 min after a show),
 * <i>Napas Pagi</i> (Qi regeneration +5 % for 10 min, once a day, morning tai chi), <i>Hangat</i> (no powder-snow freezing
 * after Nenek Tang's soup). Stored in the player's {@code extra.kota42_acara}.</li>
 * <li><b>Named characters' days</b> ({@link JadwalTokoh}, 42.10 "42D jadwal tokoh"): a resident whose {@code tokoh} flag
 * 42B set lives the character's own round (the three well aunties, the two chess grandfathers, the drunken poet...).</li>
 * </ul>
 * Every system checks {@code kotaAcara.aktif} and its own switch at run time.
 */
public final class KalenderKota {

	// ================================================================== atmosphere bits (kota_suasana.acara)

	/** A festival of {@link MusimKota} runs today. */
	public static final int BIT_HARI_RAYA = 1;
	/** A wedding today. */
	public static final int BIT_NIKAH = 1 << 1;
	/** A funeral today (white lanterns). */
	public static final int BIT_MAKAM = 1 << 2;
	/** A beast tide or a raid on the town (42E). */
	public static final int BIT_SERANGAN = 1 << 3;
	/** The night market / night district is open (18:00-02:00). */
	public static final int BIT_DISTRIK_MALAM = 1 << 4;
	/** The Ghost Market of 42C-2 is open tonight. */
	public static final int BIT_PASAR_HANTU = 1 << 5;
	/** Rain over the place. */
	public static final int BIT_HUJAN = 1 << 6;
	/** A travelling troupe is in town. */
	public static final int BIT_ROMBONGAN = 1 << 7;
	/** A troupe show is playing now. */
	public static final int BIT_PERTUNJUKAN = 1 << 8;
	/** The night watch walks (19:00-05:00). */
	public static final int BIT_RONDA = 1 << 9;
	/** The morning market (06:00-08:00). */
	public static final int BIT_PASAR_PAGI = 1 << 10;
	/** Morning tai chi in the square (05:00-06:30). */
	public static final int BIT_TAI_CHI = 1 << 11;
	/** School is in (07:00-10:00). */
	public static final int BIT_SEKOLAH = 1 << 12;
	/** A shadow play is on. */
	public static final int BIT_WAYANG = 1 << 13;
	/** Winter soup stall of Nenek Tang open (winter). */
	public static final int BIT_SUP = 1 << 14;
	/** New Year street (couplets, firecrackers): the whole first day of spring. */
	public static final int BIT_TAHUN_BARU = 1 << 15;

	/** Lantern modes of {@code kota_suasana.lentera}. */
	public static final int LENTERA_MATI = 0;
	public static final int LENTERA_MALAM = 1;
	public static final int LENTERA_PESTA = 2;
	public static final int LENTERA_DUKA = 3;

	/** Range of the atmosphere payload (42.13.2: entering within 160 blocks). */
	public static final int JARAK_SUASANA = 160;

	// ================================================================== calendar

	/**
	 * One calendar entry: kind ({@code "nikah"}, {@code "makam"}, {@code "rombongan"}, {@code "hari_raya"}...), world day,
	 * start hour 0-23, length in hours, the residents involved and free data.
	 */
	public record Acara(String jenis, long hari, int jam, int lamaJam, int[] warga, CompoundTag data) {
	}

	/**
	 * A merged calendar row: {@code id} unique per place and day ({@code "k:12"} stored, {@code "rb:bulan_sabit"} computed),
	 * kind ({@code KotaLembar.Buku.AC_*}), display name, day, start, length and the spot (null = the place itself).
	 */
	public record Entri(String id, String jenis, Component nama, long hari, int jam, int menit, int lamaJam, @Nullable BlockPos pos) {
	}

	/** A computed source of calendar rows for one place over days {@code dari..sampai} (inclusive). */
	@FunctionalInterface
	public interface Sumber {
		void isi(MinecraftServer server, long situs, long dari, long sampai, List<Entri> out);
	}

	/** Stored entries kept per place (oldest dropped). */
	public static final int SIMPAN_MAKS = 64;

	private static final List<Sumber> SUMBER = new CopyOnWriteArrayList<>();
	/** Houses marked for the atmosphere: place -> house slot -> {kind, until day}. */
	private static final Map<Long, Map<Integer, long[]>> RUMAH = new ConcurrentHashMap<>();
	/** Ceremonies seen on the bus today: place -> {bits, day}. */
	private static final Map<Long, long[]> UPACARA_HARI = new ConcurrentHashMap<>();
	/** Last atmosphere sent: player -> hash. */
	private static final Map<UUID, Integer> TERKIRIM = new ConcurrentHashMap<>();

	private static boolean initialised;

	/** From {@code KotaAcara.init}: every 42D system, the calendar, the atmosphere, the buffs, the tests. Safe twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(KalenderKota::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUMAH.clear();
			UPACARA_HARI.clear();
			TERKIRIM.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> TERKIRIM.remove(player.getUUID()));
		KotaBus.UPACARA.register((server, situs, jenis, id, fase) -> {
			long day = hari(server);
			long[] now = UPACARA_HARI.computeIfAbsent(situs, k -> new long[] {0L, day});
			if (now[1] != day) {
				now[0] = 0L;
				now[1] = day;
			}
			now[0] |= jenis == JenisUpacara.MAKAM ? BIT_MAKAM : jenis == JenisUpacara.NIKAH ? BIT_NIKAH : 0;
		});
		Berkah.init();
		WargaApi.daftarPenimpa(new JadwalTokoh());
		KotaBesarKalender.addSumberJadwal(KalenderKota::jadwalIbuKota);
		MusimKota.init();
		RutinitasPagi.init();
		Ronda.init();
		Rombongan.init();
		DistrikMalam.init();
		KotaUji.daftar("acara", KalenderKota::uji);
	}

	/** Registers a computed calendar source (42E open trial day, 42F inaugurations...). */
	public static void daftarSumber(Sumber sumber) {
		if (sumber != null) {
			SUMBER.add(sumber);
		}
	}

	/**
	 * C42.D1: adds {@code acara} to place {@code situs}; returns its id, or -1 when refused: switch off, a past day, the
	 * same kind for the same first resident already on that day, or two other entries already overlapping its hours.
	 */
	public static int daftar(long situs, Acara acara) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || acara == null || situs == 0L || !KotaConfig.acara().aktif || acara.jenis() == null || acara.jenis().isEmpty()) {
			return -1;
		}
		long today = hari(server);
		if (acara.hari() < today) {
			return -1;
		}
		KotaAcaraData data = KotaAcaraData.get(server);
		CompoundTag town = data.situs(KotaAcaraData.KALENDER, situs);
		ListTag list = town.getListOrEmpty("a");
		ListTag kept = new ListTag();
		int overlap = 0;
		int first = acara.warga() == null || acara.warga().length == 0 ? -1 : acara.warga()[0];
		for (Tag t : list) {
			if (!(t instanceof CompoundTag e)) {
				continue;
			}
			long day = e.getLongOr("h", 0L);
			if (day < today - 2L) {
				continue;
			}
			kept.add(e);
			if (day != acara.hari()) {
				continue;
			}
			int[] w = e.getIntArray("w").orElse(new int[0]);
			if (e.getStringOr("j", "").equals(acara.jenis()) && first >= 0 && w.length > 0 && w[0] == first) {
				return -1;
			}
			int a0 = e.getIntOr("m", 0);
			int a1 = a0 + Math.max(1, e.getIntOr("l", 1));
			int b0 = acara.jam();
			int b1 = b0 + Math.max(1, acara.lamaJam());
			if (a0 < b1 && b0 < a1) {
				overlap++;
			}
		}
		if (overlap >= 2) {
			return -1;
		}
		while (kept.size() >= SIMPAN_MAKS) {
			kept.remove(0);
		}
		int id = town.getIntOr("n", 0) + 1;
		town.putInt("n", id);
		CompoundTag e = new CompoundTag();
		e.putInt("i", id);
		e.putString("j", acara.jenis());
		e.putLong("h", acara.hari());
		e.putInt("m", Math.floorMod(acara.jam(), 24));
		e.putInt("l", Math.max(1, acara.lamaJam()));
		e.put("w", new IntArrayTag(acara.warga() == null ? new int[0] : acara.warga()));
		if (acara.data() != null && !acara.data().isEmpty()) {
			e.put("d", acara.data().copy());
		}
		kept.add(e);
		town.put("a", kept);
		data.ubah();
		return id;
	}

	/** Removes stored entry {@code id} of {@code situs}; false when unknown. */
	public static boolean batal(long situs, int id) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return false;
		}
		KotaAcaraData data = KotaAcaraData.get(server);
		CompoundTag town = data.situs(KotaAcaraData.KALENDER, situs);
		ListTag list = town.getListOrEmpty("a");
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag e && e.getIntOr("i", -1) == id) {
				list.remove(i);
				town.put("a", list);
				data.ubah();
				return true;
			}
		}
		return false;
	}

	/** The stored entries of {@code situs} on days {@code dari..sampai}, with their ids (id -> entry). */
	public static Map<Integer, Acara> simpanan(MinecraftServer server, long situs, long dari, long sampai) {
		Map<Integer, Acara> out = new java.util.LinkedHashMap<>();
		CompoundTag town = KotaAcaraData.get(server).situs(KotaAcaraData.KALENDER, situs);
		for (Tag t : town.getListOrEmpty("a")) {
			if (t instanceof CompoundTag e) {
				long day = e.getLongOr("h", 0L);
				if (day >= dari && day <= sampai) {
					out.put(e.getIntOr("i", 0), new Acara(e.getStringOr("j", ""), day, e.getIntOr("m", 0), e.getIntOr("l", 1),
							e.getIntArray("w").orElse(new int[0]), e.getCompoundOrEmpty("d")));
				}
			}
		}
		return out;
	}

	/** Every calendar row of {@code situs} on days {@code dari..sampai}, sorted by day and time. */
	public static List<Entri> acara(MinecraftServer server, long situs, long dari, long sampai) {
		List<Entri> out = new ArrayList<>();
		for (Map.Entry<Integer, Acara> e : simpanan(server, situs, dari, sampai).entrySet()) {
			Acara a = e.getValue();
			out.add(new Entri("k:" + e.getKey(), a.jenis(), namaJenis(a.jenis(), a), a.hari(), a.jam(), 0, a.lamaJam(), null));
		}
		bawaan(server, situs, dari, sampai, out);
		for (Sumber s : SUMBER) {
			try {
				s.isi(server, situs, dari, sampai, out);
			} catch (RuntimeException ignored) {
				// a broken source never breaks the calendar
			}
		}
		out.sort(Comparator.comparingLong(Entri::hari).thenComparingInt(Entri::jam).thenComparingInt(Entri::menit));
		return out;
	}

	/** The daily rows every inhabited town has: tai chi, the morning market, school, the night watch. */
	private static void bawaan(MinecraftServer server, long situs, long dari, long sampai, List<Entri> out) {
		KotaConfig.Acara c = KotaConfig.acara();
		String kind = PajakKota.jenisTempat(server, situs);
		boolean town = !PajakKota.JT_POS.equals(kind);
		for (long d = dari; d <= sampai; d++) {
			if (c.rutinitasPagi && town) {
				out.add(new Entri("pagi:" + d, KotaLembar.Buku.AC_PASAR_PAGI, Component.translatable("kultivator.kota42.acara.kalender.pasar_pagi"),
						d, 6, 0, 2, null));
			}
			if (c.ronda && town) {
				out.add(new Entri("ronda:" + d, KotaLembar.Buku.AC_RONDA, Component.translatable("kultivator.kota42.acara.kalender.ronda"),
						d, 19, 0, 10, null));
			}
		}
	}

	private static Component namaJenis(String jenis, Acara a) {
		return Component.translatable("kultivator.kota42.acara.kalender." + jenis);
	}

	/**
	 * The {@code kota_buku} Acara rows (KotaLembar.Buku {@code acara}) of {@code situs} for {@code n} days from today:
	 * id, jenis, nama, hari, jam, menit, tempat, x, z, sisa_tick (today only).
	 */
	public static ListTag lembarAcara(MinecraftServer server, long situs, int n) {
		long today = hari(server);
		ListTag list = new ListTag();
		long now = Kota.timeOfDay(server);
		for (Entri e : acara(server, situs, today, today + Math.max(1, n) - 1)) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, e.id());
			row.putString(LembarKunci.JENIS, e.jenis());
			LembarKunci.teks(row, LembarKunci.NAMA, e.nama());
			row.putLong(LembarKunci.HARI, e.hari());
			row.putInt(KotaLembar.JAM, e.jam());
			row.putInt(KotaLembar.MENIT, e.menit());
			LembarKunci.teks(row, LembarKunci.TEMPAT, PajakKota.nama(server, situs));
			if (e.pos() != null) {
				row.putInt(LembarKunci.X, e.pos().getX());
				row.putInt(LembarKunci.Z, e.pos().getZ());
			}
			if (e.hari() == today) {
				long start = Math.floorMod((e.jam() - 6) * 1000L + e.menit() * 1000L / 60L, 24000L);
				long left = start - now;
				if (left > 0L) {
					row.putLong(LembarKunci.SISA_TICK, left);
				}
			}
			list.add(row);
		}
		return list;
	}

	/** The next {@code n} rows from now (Buku Kota Ringkas: acara [teks, hari, jam, menit, tempat, x, z]). */
	public static ListTag lembarRingkas(MinecraftServer server, long situs, int n) {
		long today = hari(server);
		int jam = jam(server);
		ListTag list = new ListTag();
		for (Entri e : acara(server, situs, today, today + 6)) {
			if (e.hari() == today && e.jam() + e.lamaJam() <= jam) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.TEKS, e.nama());
			row.putLong(LembarKunci.HARI, e.hari());
			row.putInt(KotaLembar.JAM, e.jam());
			row.putInt(KotaLembar.MENIT, e.menit());
			LembarKunci.teks(row, LembarKunci.TEMPAT, PajakKota.nama(server, situs));
			if (e.pos() != null) {
				row.putInt(LembarKunci.X, e.pos().getX());
				row.putInt(LembarKunci.Z, e.pos().getZ());
			}
			list.add(row);
			if (list.size() >= n) {
				break;
			}
		}
		return list;
	}

	/** The capital calendar's extra rows (the 38 {@code acara} screen lists 42's troupes, festivals and ceremonies too). */
	private static void jadwalIbuKota(MinecraftServer server, long town, long day, int hari, List<CompoundTag> out) {
		if (!KotaConfig.acara().aktif) {
			return;
		}
		for (Entri e : acara(server, town, day, day + hari - 1)) {
			if (KotaLembar.Buku.AC_RONDA.equals(e.jenis()) || KotaLembar.Buku.AC_PASAR_PAGI.equals(e.jenis())) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, e.nama());
			row.putInt(LembarKunci.HARI, (int) e.hari());
			LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.kota42.acara.kalender.jam",
					jamTeks(e.jam(), e.menit()), jamTeks(e.jam() + e.lamaJam(), e.menit())));
			out.add(row);
		}
	}

	/** "08.30". */
	public static String jamTeks(int jam, int menit) {
		return String.format(java.util.Locale.ROOT, "%02d.%02d", Math.floorMod(jam, 24), Math.floorMod(menit, 60));
	}

	// ================================================================== clock

	/** Hour 0-23 of the overworld clock (tick 0 = 06:00). */
	public static int jam(MinecraftServer server) {
		return (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
	}

	/** Minute 0-59. */
	public static int menit(MinecraftServer server) {
		return (int) (Kota.timeOfDay(server) % 1000L * 60L / 1000L);
	}

	/** Hour with fraction (5.5 = 05:30). */
	public static double jamDesimal(MinecraftServer server) {
		long t = Kota.timeOfDay(server);
		return (t / 1000L + 6L) % 24L + (t % 1000L) / 1000.0D;
	}

	/** True when the clock is in {@code [dari, sampai)} hours, wrapping over midnight when {@code sampai < dari}. */
	public static boolean antara(double jam, double dari, double sampai) {
		return dari <= sampai ? jam >= dari && jam < sampai : jam >= dari || jam < sampai;
	}

	/** World day ({@code Kota.day}). */
	public static long hari(MinecraftServer server) {
		return Kota.day(server);
	}

	/** Season 0-3 ({@code Jadwal.SEMI..DINGIN}) of world day {@code hari}. */
	public static int musim(long hari) {
		return (int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L);
	}

	/** Day 0-23 inside its season. */
	public static int hariMusim(long hari) {
		return (int) Math.floorMod(hari, (long) Jadwal.HARI_MUSIM);
	}

	/** Game ticks from now until clock hour {@code jam} today (0 when past). */
	public static long tickSampai(MinecraftServer server, double jam) {
		double now = jamDesimal(server);
		return now >= jam ? 0L : (long) ((jam - now) * 1000.0D);
	}

	// ================================================================== places

	/** The inhabited overworld places with a player near now (38's active places). */
	public static List<TempatHuni> aktif(ServerLevel level) {
		List<TempatHuni> out = new ArrayList<>();
		if (!KotaHidup.duniaKota(level)) {
			return out;
		}
		for (TempatHuni huni : Pemanggil.semua()) {
			if (huni.dimensi == level.dimension()) {
				out.add(huni);
			}
		}
		return out;
	}

	/** True for a town-like place (not a road post or a sect compound). */
	public static boolean kota(TempatHuni huni) {
		return huni.jenis.startsWith("kota/") || huni.jenis.startsWith("kota_tema/") || huni.jenis.startsWith("kampung/");
	}

	/** True for the capital. */
	public static boolean ibuKota(TempatHuni huni) {
		return "kota/kota_besar".equals(huni.jenis);
	}

	/** True when a non-spectator player is within {@code r} blocks of {@code pos}. */
	public static boolean adaPemain(ServerLevel level, BlockPos pos, double r) {
		return level.getNearestPlayer(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, r, false) != null;
	}

	/** The square of a place: its stage titik, then a training ground, then a chat corner, else the centre. */
	public static BlockPos alun(TempatHuni huni) {
		for (String type : new String[] {"panggung", "alun", "latihan", "obrol"}) {
			List<dev.kultivator.dunia.tempat.Titik> list = huni.titik(type);
			if (!list.isEmpty()) {
				return list.get((int) Math.floorMod(huni.situsId, (long) list.size())).pos();
			}
		}
		return huni.pusat();
	}

	/** The players within {@link KotaConfig.Acara#jarakTampil} of {@code pos}. */
	public static List<ServerPlayer> penonton(ServerLevel level, BlockPos pos) {
		double r = KotaConfig.acara().jarakTampil;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().distSqr(pos) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** Marks house slot {@code rumah} of {@code situs} for the atmosphere (wedding 囍, funeral 奠) until world day {@code sampai}. */
	public static void tandaiRumah(long situs, int rumah, byte jenis, long sampai) {
		if (rumah < 0) {
			return;
		}
		RUMAH.computeIfAbsent(situs, k -> new ConcurrentHashMap<>()).put(rumah, new long[] {jenis, sampai});
	}

	// ================================================================== tick: atmosphere

	private static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (t % 20L == 7L) {
			Berkah.tick(server);
		}
		if (t % 100L != 31L || !KotaConfig.acara().aktif) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			TempatInfo info = KotaHidupCommands.tempatPemain(player);
			if (info == null) {
				TERKIRIM.remove(player.getUUID());
				continue;
			}
			KotaPayloads.Suasana p = suasana(level, info.situsId());
			int hash = p.hashCode() ^ (int) (t / 600L);
			Integer old = TERKIRIM.get(player.getUUID());
			if (old == null || old != hash) {
				TERKIRIM.put(player.getUUID(), hash);
				KotaPayloads.kirim(player, p);
			}
		}
	}

	/** The atmosphere of place {@code situs} now. */
	public static KotaPayloads.Suasana suasana(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		long day = hari(server);
		double jam = jamDesimal(server);
		int bits = bitAcara(level, situs);
		int kemakmuran = Math.max(0, Math.min(100, Kemakmuran.nilai(situs)));
		int mood = Math.max(-100, Math.min(100, WargaApi.suasanaTempat(situs)));
		int musim = musim(day);
		int raya = KotaConfig.acara().musim ? MusimKota.hariRaya(day) : 0;
		UUID pelindung = PaktaLindung.pelindung(situs);
		// the packed emblem of the protecting sect (the same value the branch sign keeps), -1 without a protector
		int lambang = pelindung == null ? -1 : dev.kultivator.sekte.seragam.LambangData.get(pelindung).lambang() & 0x7FFFFFFF;
		int lentera;
		if ((bits & BIT_MAKAM) != 0) {
			lentera = LENTERA_DUKA;
		} else if (raya != 0 && antara(jam, 17.0D, 24.0D) || (bits & BIT_DISTRIK_MALAM) != 0) {
			lentera = LENTERA_PESTA;
		} else if (antara(jam, 18.0D, 6.0D)) {
			lentera = LENTERA_MALAM;
		} else {
			lentera = LENTERA_MATI;
		}
		List<KotaPayloads.EntriRumah> rumah = new ArrayList<>();
		Map<Integer, long[]> marks = RUMAH.get(situs);
		if (marks != null) {
			marks.entrySet().removeIf(e -> e.getValue()[1] < day);
			for (Map.Entry<Integer, long[]> e : marks.entrySet()) {
				if (rumah.size() >= KotaPayloads.RUMAH_KHUSUS_MAKS) {
					break;
				}
				rumah.add(new KotaPayloads.EntriRumah(e.getKey(), (byte) e.getValue()[0]));
			}
		}
		return new KotaPayloads.Suasana(situs, bits, kemakmuran, mood, musim, raya, lambang, lentera, List.copyOf(rumah));
	}

	/** The event bits of place {@code situs} now ({@code BIT_*}). */
	public static int bitAcara(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		KotaConfig.Acara c = KotaConfig.acara();
		long day = hari(server);
		double jam = jamDesimal(server);
		int bits = 0;
		int raya = c.musim ? MusimKota.hariRaya(day) : 0;
		if (raya != 0) {
			bits |= BIT_HARI_RAYA;
			if (raya == MusimKota.HR_TAHUN_BARU && c.tahunBaru) {
				bits |= BIT_TAHUN_BARU;
			}
		}
		long[] up = UPACARA_HARI.get(situs);
		if (up != null && up[1] == day) {
			bits |= (int) up[0];
		}
		if (dev.kultivator.dunia.kota42.ekonomi.HargaKota.perang(server, situs)) {
			bits |= BIT_SERANGAN;
		}
		if (c.distrikMalam && antara(jam, 18.0D, 2.0D)) {
			bits |= BIT_DISTRIK_MALAM;
		}
		if (level.isRaining()) {
			bits |= BIT_HUJAN;
		}
		if (c.rombongan && Rombongan.diKota(server, situs) != null) {
			bits |= BIT_ROMBONGAN;
			if (Rombongan.sedangPentas(situs)) {
				bits |= BIT_PERTUNJUKAN;
			}
		}
		if (c.ronda && antara(jam, 19.0D, 5.0D)) {
			bits |= BIT_RONDA;
		}
		if (c.rutinitasPagi) {
			if (antara(jam, 6.0D, 8.0D)) {
				bits |= BIT_PASAR_PAGI;
			}
			if (antara(jam, 5.0D, 6.5D)) {
				bits |= BIT_TAI_CHI;
			}
			if (antara(jam, 7.0D, 10.0D)) {
				bits |= BIT_SEKOLAH;
			}
		}
		if (TeaterBayang.sedangPentas(situs)) {
			bits |= BIT_WAYANG;
		}
		if (c.musim && musim(day) == Jadwal.DINGIN) {
			bits |= BIT_SUP;
		}
		return bits;
	}

	// ================================================================== buffs

	/**
	 * The town buffs of 42D, kept in the player's {@code extra.kota42_acara} as game-time ends: {@link #TERHIBUR},
	 * {@link #NAPAS_PAGI} (with the once-a-day mark), {@link #HANGAT}; plus the troupes watched (advancement
	 * {@code kota42_rombongan}).
	 */
	public static final class Berkah {
		public static final String TERHIBUR = "terhibur";
		public static final String NAPAS_PAGI = "napas_pagi";
		public static final String HANGAT = "hangat";
		/** 10 minutes. */
		public static final int SEPULUH_MENIT = 12000;
		/** Napas Pagi: Qi regeneration x1.05. */
		public static final double NAPAS_PAGI_KALI = 1.05D;
		/** Terhibur: comprehension a minute. */
		public static final double TERHIBUR_PER_MENIT = 0.01D;
		private static final String KUNCI = "kota42_acara";

		private static void init() {
			Qi.addRegenFactor(player -> aktif(player, NAPAS_PAGI) ? NAPAS_PAGI_KALI : 1.0D);
		}

		private static CompoundTag tag(ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			CompoundTag extra = data.extra();
			CompoundTag own = extra.getCompoundOrEmpty(KUNCI);
			if (!extra.contains(KUNCI)) {
				extra.put(KUNCI, own);
			}
			return own;
		}

		/** Gives {@code buff} for {@code ticks} from now (longer of old and new). */
		public static void beri(ServerPlayer player, String buff, int ticks) {
			CompoundTag t = tag(player);
			long end = player.level().getGameTime() + ticks;
			if (t.getLongOr(buff, 0L) < end) {
				t.putLong(buff, end);
				Cultivators.dirty(player);
			}
		}

		/** True while {@code buff} lasts. */
		public static boolean aktif(ServerPlayer player, String buff) {
			return tag(player).getLongOr(buff, 0L) > player.level().getGameTime();
		}

		/** Game ticks left of {@code buff} (0 = none). */
		public static long sisa(ServerPlayer player, String buff) {
			return Math.max(0L, tag(player).getLongOr(buff, 0L) - player.level().getGameTime());
		}

		/** True (and marked) the first time today for {@code kunci}: once-a-day rewards. */
		public static boolean sekaliSehari(ServerPlayer player, String kunci) {
			CompoundTag t = tag(player);
			long day = hari(player.level().getServer());
			if (t.getLongOr("hari_" + kunci, Long.MIN_VALUE) == day) {
				return false;
			}
			t.putLong("hari_" + kunci, day);
			Cultivators.dirty(player);
			return true;
		}

		/** How many times {@code kunci} was counted today by {@link #tambahHari} (0 on a new day). */
		public static int hariHitung(ServerPlayer player, String kunci) {
			CompoundTag t = tag(player);
			return t.getLongOr("cd_" + kunci, Long.MIN_VALUE) == hari(player.level().getServer()) ? t.getIntOr("c_" + kunci, 0) : 0;
		}

		/** Counts {@code n} more of {@code kunci} today (the daily limit of dice throws...); returns the new count. */
		public static int tambahHari(ServerPlayer player, String kunci, int n) {
			CompoundTag t = tag(player);
			long day = hari(player.level().getServer());
			int now = (t.getLongOr("cd_" + kunci, Long.MIN_VALUE) == day ? t.getIntOr("c_" + kunci, 0) : 0) + n;
			t.putLong("cd_" + kunci, day);
			t.putInt("c_" + kunci, now);
			Cultivators.dirty(player);
			return now;
		}

		/** True when {@code kunci} was marked today by {@link #sekaliSehari} (read only). */
		public static boolean hariIni(ServerPlayer player, String kunci) {
			return tag(player).getLongOr("hari_" + kunci, Long.MIN_VALUE) == hari(player.level().getServer());
		}

		/** A counter per player and {@code kunci} (0, 1, 2 ...: the n-th lore page, the next song...); returns the old value. */
		public static int urutan(ServerPlayer player, String kunci) {
			CompoundTag t = tag(player);
			int n = t.getIntOr("n_" + kunci, 0);
			t.putInt("n_" + kunci, n + 1);
			Cultivators.dirty(player);
			return n;
		}

		/** Marks troupe bit {@code bit} as watched; returns the full mask. */
		public static int tontonRombongan(ServerPlayer player, int bit) {
			CompoundTag t = tag(player);
			int mask = t.getIntOr("rb", 0) | 1 << bit;
			t.putInt("rb", mask);
			Cultivators.dirty(player);
			return mask;
		}

		/** Every second for every player: comprehension of Terhibur each minute, no freezing while Hangat. */
		private static void tick(MinecraftServer server) {
			long now = server.overworld().getGameTime();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				CultivationData data = Cultivators.get(player);
				CompoundTag t = data.extra().getCompoundOrEmpty(KUNCI);
				if (t.isEmpty()) {
					continue;
				}
				if (t.getLongOr(HANGAT, 0L) > now && player.getTicksFrozen() > 0) {
					player.setTicksFrozen(0);
				}
				if (t.getLongOr(TERHIBUR, 0L) > now && now % 1200L < 20L) {
					Dao dao = data.dao();
					if (dao != null) {
						Comprehension.add(player, dao, TERHIBUR_PER_MENIT);
					}
				}
			}
		}

		/** Starts Terhibur with its message. */
		public static void terhibur(ServerPlayer player) {
			beri(player, TERHIBUR, SEPULUH_MENIT);
			Msg.actionBar(player, Component.translatable("kultivator.kota42.acara.berkah.terhibur").withStyle(ChatFormatting.GOLD));
		}

		private Berkah() {
		}
	}

	// ================================================================== named characters' rounds (42.10)

	/**
	 * The own round of a resident 42B made a named character ({@code Warga.tokoh}); order 150 (after ceremonies, before
	 * habits). Characters run by their 42D system as a body of their own (troupes, the watchmen, the teacher) are left
	 * to that system here unless they live in the place as residents.
	 */
	static final class JadwalTokoh implements PenimpaJadwalKota {

		@Override
		public int urutan() {
			return 150;
		}

		@Override
		public @Nullable Kegiatan timpa(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
			if (w.tokoh == null || w.tokoh.isEmpty() || !KotaConfig.acara().aktif) {
				return null;
			}
			MinecraftServer server = KotaHidup.server();
			if (server == null) {
				return null;
			}
			Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
			double h = s.jam() + s.menit() / 60.0D;
			int musim = s.musim();
			boolean hujan = huni.cuaca().hujan();
			return switch (w.tokoh) {
				case "bibi_zhang", "bibi_li", "bibi_wang" -> {
					// the three well aunties: washing and gossip at the well in the morning and late afternoon
					if (hujan || !(antara(h, 7.0D, 10.5D) || antara(h, 15.5D, 18.0D))) {
						yield null;
					}
					String to = ada(huni, "sumur") ? "sumur" : "obrol";
					int klip = switch (w.tokoh) {
						case "bibi_zhang" -> GerakId.BASUH_PAKAIAN;
						case "bibi_li" -> KotaGerakId.INTIP;
						default -> GerakId.BICARA_TANGAN;
					};
					yield Kegiatan.di("tk_sumur", to, (s.menit() / 10) % 3 == 0 ? GerakId.TERTAWA : klip, 0);
				}
				case "kakek_xiang", "kakek_che" -> {
					// chess under the huai tree; alone when the other one is gone (42.10)
					if (hujan || !(antara(h, 9.0D, 12.0D) || antara(h, 14.0D, 18.0D))) {
						yield null;
					}
					String to = ada(huni, "catur") ? "catur" : "kursi";
					boolean sendiri = !temanCaturHidup(huni.situsId, w.tokoh);
					int klip = sendiri ? KotaGerakId.TONTON_CATUR : (s.menit() / 15) % 2 == 0 ? KotaGerakId.TONTON_CATUR : GerakId.DUDUK_KURSI;
					yield Kegiatan.di(sendiri ? "tk_catur_sendiri" : "tk_catur", to, klip, 0);
				}
				case "li_zuixian" -> {
					// the drunken poet: the tavern from dusk till late, reciting; asleep on a bench at noon
					if (antara(h, 18.0D, 2.0D)) {
						int klip = (s.menit() / 12) % 3 == 0 ? KotaGerakId.DEKLAMASI : KotaGerakId.MINUM_ARAK;
						yield new Kegiatan("tk_penyair", Kegiatan.KEDAI, klip, 0, KotaGerakId.PROP_CAWAN_ARAK, KotaGerakId.PROP_GUCI_ARAK, 0, 0, false);
					}
					if (antara(h, 12.0D, 15.0D)) {
						yield Kegiatan.di("tk_tidur_bangku", "kursi", KotaGerakId.TIDUR_BANGKU, 0);
					}
					yield null;
				}
				case "bibi_mei" -> {
					// the matchmaker makes her rounds of the houses with her red almanac
					if (!antara(h, 9.0D, 12.0D) && !antara(h, 14.0D, 17.0D)) {
						yield null;
					}
					yield Kegiatan.di("tk_comblang", Kegiatan.JALAN + (s.jam() * 3 + s.menit() / 20), GerakId.BICARA_TANGAN, KotaGerakId.PROP_ALMANAK_MERAH);
				}
				case "nenek_tang" -> {
					// the winter soup stall (42.7.6); the rest of the year she tends her stove at home
					if (musim == Jadwal.DINGIN && KotaConfig.acara().musim && antara(h, 10.0D, 20.0D)) {
						yield new Kegiatan("tk_sup", Kegiatan.KEDAI, KotaGerakId.SAJIKAN_TEH_BESAR, 0, KotaGerakId.PROP_KENDI_SUP,
								PropId.TIDAK_ADA, 0, 0, false);
					}
					yield null;
				}
				case "paman_bai" -> antara(h, 6.0D, 11.0D) && ada(huni, "dupa")
						? Kegiatan.di("tk_dupa", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : null;
				case "shi_xiaoqian" -> {
					// the pickpocket boss loiters in the market crowd by day, watching purses
					if (!antara(h, 9.0D, 17.0D)) {
						yield null;
					}
					yield Kegiatan.di("tk_intai", ada(huni, "lapak") ? "lapak" : "obrol", KotaGerakId.INTIP, 0);
				}
				case "lentera_hijau" -> {
					// the Ghost Market's host is never seen by day
					if (antara(h, 23.0D, 4.0D)) {
						yield Kegiatan.di("tk_lentera_hijau", ada(huni, "pasar_hantu_1") ? "pasar_hantu_1" : "obrol",
								GerakId.BERDIRI_JAGA, PropId.LENTERA_TANGAN);
					}
					yield Kegiatan.dalam("tk_tak_terlihat");
				}
				case "gong_shu" -> antara(h, 7.0D, 17.0D) && ada(huni, "kavling")
						? Kegiatan.di("tk_mandor", "kavling", (s.menit() / 20) % 2 == 0 ? KotaGerakId.PASANG_PATOK : GerakId.BICARA_TANGAN,
						KotaGerakId.PROP_PAPAN_PATOK) : null;
				case "shen_wan" -> antara(h, 9.0D, 17.0D)
						? Kegiatan.di("tk_pialang", Kegiatan.KERJA, KotaGerakId.HITUNG_KOIN, PropId.TIDAK_ADA) : null;
				case "qiu_panitera" -> antara(h, 8.0D, 16.0D)
						? Kegiatan.di("tk_panitera", ada(huni, "sidang") ? "sidang" : Kegiatan.KERJA, GerakId.MEMBACA, PropId.KITAB) : null;
				case "fang_gudang" -> antara(h, 8.0D, 18.0D)
						? Kegiatan.di("tk_gudang", Kegiatan.KERJA, KotaGerakId.MENIMBANG, KotaGerakId.PROP_DACING) : null;
				case "gao_senior" -> antara(h, 9.0D, 17.0D) && ada(huni, "rekrut")
						? Kegiatan.di("tk_rekrut", "rekrut", GerakId.SIKAP_LIPAT_TANGAN, 0) : null;
				case "xiao_yu" -> {
					// the girl of the slums watches her mother paint sugar, then plays tag
					if (antara(h, 10.0D, 12.0D)) {
						yield Kegiatan.di("tk_tonton_gula", ada(huni, "lapak") ? "lapak" : "obrol", KotaGerakId.KAGUM, 0);
					}
					yield antara(h, 15.0D, 18.0D) ? Kegiatan.di("tk_main", "obrol", GerakId.MAIN_KEJAR, 0) : null;
				}
				case "zhang_shan", "li_ying" -> antara(h, 18.0D, 19.0D) && !hujan && ada(huni, "sumur")
						// the secret meeting at the well at dusk ("Dua Keluarga di Jalan Sumur")
						? Kegiatan.di("tk_sumur_rahasia", "sumur", KotaGerakId.INTIP, 0) : null;
				case "ah_qin" -> antara(h, 9.0D, 12.0D) || antara(h, 16.0D, 19.0D)
						? Kegiatan.di("tk_erhu", ada(huni, "jembatan") ? "jembatan" : ada(huni, "gerbang") ? "gerbang" : "obrol",
						KotaGerakId.MAIN_ERHU, KotaGerakId.PROP_ERHU) : null;
				case "lao_shuo" -> antara(h, 10.0D, 11.0D) || antara(h, 15.0D, 16.0D) || antara(h, 20.0D, 21.0D)
						? new Kegiatan("tk_dongeng", Kegiatan.KEDAI, KotaGerakId.DONGENG, 0, KotaGerakId.PROP_BALOK_KAYU, PropId.KIPAS_LIPAT, 0, 0, false)
						: null;
				case "lao_geng", "xiao_bang" -> Ronda.kegiatanTokoh(huni, w, s);
				case "guru_zhou" -> RutinitasPagi.kegiatanGuru(huni, w, s);
				default -> null;
			};
		}

		private static boolean ada(TempatHuni huni, String type) {
			return !huni.titik(type).isEmpty();
		}

		private static boolean temanCaturHidup(long situs, String self) {
			String other = "kakek_xiang".equals(self) ? "kakek_che" : "kakek_xiang";
			for (Warga each : WargaApi.wargaTempat(situs)) {
				if (other.equals(each.tokoh)) {
					return each.hidup();
				}
			}
			return false;
		}
	}

	// ================================================================== test commands (uji kota_hidup acara ...)

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("acara")
				.then(Commands.literal("kalender").executes(KalenderKota::ujiKalender))
				.then(Commands.literal("suasana").executes(KalenderKota::ujiSuasana))
				.then(Rombongan.uji())
				.then(RutinitasPagi.uji())
				.then(Ronda.uji())
				.then(MusimKota.uji())
				.then(DistrikMalam.ujiMalam())
				.then(TeaterBayang.uji())
				.then(Guanpu.uji())
				.then(Commands.literal("berkah")
						.then(Commands.argument("jenis", StringArgumentType.word())
								.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
										List.of(Berkah.TERHIBUR, Berkah.NAPAS_PAGI, Berkah.HANGAT), b))
								.then(Commands.argument("detik", IntegerArgumentType.integer(1, 3600))
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											Berkah.beri(p, StringArgumentType.getString(ctx, "jenis"), IntegerArgumentType.getInteger(ctx, "detik") * 20);
											ctx.getSource().sendSuccess(() -> Component.literal("berkah " + StringArgumentType.getString(ctx, "jenis")), false);
											return 1;
										}))));
	}

	/** The place of the command's player, or null with a failure message. */
	static @Nullable TempatInfo tempatUji(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo info = KotaHidupCommands.tempatPemain(player);
		if (info == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tanpa_tempat"));
		}
		return info;
	}

	private static int ujiKalender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		long today = hari(server);
		List<Entri> list = acara(server, info.situsId(), today, today + 13);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.kalender", PajakKota.nama(server, info.situsId()),
				list.size()).withStyle(ChatFormatting.GOLD), false);
		for (Entri e : list) {
			if (KotaLembar.Buku.AC_RONDA.equals(e.jenis()) || KotaLembar.Buku.AC_PASAR_PAGI.equals(e.jenis())) {
				continue;
			}
			ctx.getSource().sendSuccess(() -> Component.literal("  " + e.hari() + " " + jamTeks(e.jam(), e.menit()) + " ")
					.append(e.nama()).append(Component.literal(" [" + e.id() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
		}
		return list.size();
	}

	private static int ujiSuasana(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		KotaPayloads.Suasana s = suasana(ctx.getSource().getServer().overworld(), info.situsId());
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"suasana: bit=%s makmur=%d mood=%d musim=%d raya=%d lambang=%d lentera=%d rumah=%d",
				Integer.toBinaryString(s.acara()), s.kemakmuran(), s.suasana(), s.musim(), s.hariRaya(), s.lambang(), s.lentera(),
				s.rumah().size())), false);
		return 1;
	}

	/** Life stage of a resident (shortcut for the 42D systems). */
	static WargaInfo.Tahap tahap(Warga w) {
		return WargaInfo.Tahap.dari(WargaApi.umur(w));
	}

	/** The place a body belongs to (its town id). */
	static long situsDari(KultivatorNpc npc) {
		return npc.town();
	}

	/** Count of every entry stored (anggaran). */
	static int jumlahSimpanan(MinecraftServer server) {
		int n = 0;
		CompoundTag part = KotaAcaraData.get(server).bagian(KotaAcaraData.KALENDER);
		for (String key : part.keySet()) {
			n += part.getCompoundOrEmpty(key).getListOrEmpty("a").size();
		}
		return n;
	}

	static Map<String, Integer> ringkasTitik(TempatHuni huni) {
		return new HashMap<>(huni.ringkasTitik());
	}

	private KalenderKota() {
	}
}

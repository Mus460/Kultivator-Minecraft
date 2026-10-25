package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.langit.SkyEvents;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Sibuk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The capital's calendar (DESIGN 38.B.6). World day {@code d = overworld clock / 24000}; at most one great event a day per
 * capital, in the priority Defence > Tournament > Grand Auction > Pill Gathering > Gate Opening Day > Lantern Festival; a
 * great event that loses its day moves to the next one (twice at most). The Night Market (every evening) and the Sect
 * Council ({@code d % 30 == 15}) are small events that run beside them. The Grand Auction itself is run by 38EK's
 * {@code LelangAgung} on its fixed day ({@code d % 7 == 5}); the calendar only keeps its slot.
 * <p>
 * An event runs <b>live</b> when a player stands within {@link KotaBesarKota#HIDUP} blocks of the centre when it starts;
 * otherwise it runs as data (a short outcome told as a rumour). Every live start asks {@link Sibuk#why} only for the
 * players it would pull into a scene (the events themselves never move a player). Schedule overrides for the townsfolk go
 * through {@link TempatEvents#fireAcara} (38NL listens).
 */
public final class KotaBesarKalender {

	/** One calendar event. */
	interface Acara {
		String id();

		/** Higher wins the day; small events (0) never compete. */
		int prioritas();

		/** True when {@code day} is a natural day of the event for capital {@code town}. */
		boolean hariAsli(MinecraftServer server, long town, long day);

		long mulaiJam();

		long akhirJam();

		/** Config switch of the event. */
		default boolean nyala() {
			return true;
		}

		void mulai(ServerLevel level, Kandidat k, long town, boolean hidup);

		/** Every 20 ticks while running live. */
		default void tick(ServerLevel level, Kandidat k, long town, long now) {
		}

		void akhir(ServerLevel level, Kandidat k, long town, boolean hidup);

		/** Adds the event's own keys to the {@code acara} sheet. */
		default void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		}

		/** An op of the {@code acara} screen for this event. */
		default void aksi(ServerPlayer player, long town, @Nullable KultivatorNpc npc, String[] op) {
		}
	}

	private static final Map<String, Acara> ACARA = new LinkedHashMap<>();

	/**
	 * A source of extra rows for the capital's calendar list (DESIGN 42.7: 42D's {@code KalenderKota} adds the troupes,
	 * festivals and ceremonies of the town): rows with {@link LembarKunci#NAMA}, {@link LembarKunci#HARI} and
	 * {@link LembarKunci#CATATAN}, for {@code hari} days from {@code day}.
	 */
	@FunctionalInterface
	public interface SumberJadwal {
		void isi(MinecraftServer server, long town, long day, int hari, List<CompoundTag> out);
	}

	private static final List<SumberJadwal> SUMBER_JADWAL = new CopyOnWriteArrayList<>();

	/** Registers a source of extra calendar rows (42D). */
	public static void addSumberJadwal(SumberJadwal sumber) {
		if (sumber != null) {
			SUMBER_JADWAL.add(sumber);
		}
	}

	static void init() {
		daftar(new AcaraPertahanan());
		daftar(new TurnamenAgung());
		daftar(new AcaraLelang());
		daftar(new PertemuanPil());
		daftar(new HariGerbang());
		daftar(new FestivalLentera());
		daftar(new PasarMalam());
		daftar(new PertemuanSekte());
		daftar(new Istana());
		TempatLayar.daftar(LembarKunci.Acara.LAYAR, KotaBesarKalender::aksiLayar, KotaBesarKalender::bangun);
		CeritaApi.daftarJasa(KotaBesarKalender::jasa);
	}

	private static void daftar(Acara a) {
		ACARA.put(a.id(), a);
	}

	static @Nullable Acara acara(String id) {
		return ACARA.get(id);
	}

	static List<String> ids() {
		return List.copyOf(ACARA.keySet());
	}

	// ================================================================== schedule (pure: same answer on every call)

	/** The great event of {@code day} for capital {@code town} (after moves), or null. */
	static @Nullable String jadwal(MinecraftServer server, long town, long day) {
		List<String> pending = new ArrayList<>();
		List<String> pendingAge = new ArrayList<>();
		String winner = null;
		for (long d = day - 6; d <= day; d++) {
			List<String> cand = new ArrayList<>();
			for (Acara a : ACARA.values()) {
				if (a.prioritas() > 0 && a.nyala() && a.hariAsli(server, town, d)) {
					cand.add(a.id());
				}
			}
			for (int i = 0; i < pending.size(); i++) {
				if (!cand.contains(pending.get(i))) {
					cand.add(pending.get(i));
				}
			}
			winner = null;
			int best = -1;
			for (String id : cand) {
				int p = AcaraLelang.ID.equals(id) && LelangHari.lelang(d) ? 1000 : ACARA.get(id).prioritas();
				if (p > best) {
					best = p;
					winner = id;
				}
			}
			List<String> nextPending = new ArrayList<>();
			List<String> nextAge = new ArrayList<>();
			for (String id : cand) {
				if (id.equals(winner) || AcaraLelang.ID.equals(id)) {
					continue;
				}
				int idx = pending.indexOf(id);
				int age = idx < 0 ? 0 : Integer.parseInt(pendingAge.get(idx));
				if (age < 2) {
					nextPending.add(id);
					nextAge.add(Integer.toString(age + 1));
				}
			}
			pending = nextPending;
			pendingAge = nextAge;
		}
		return winner;
	}

	/** The fixed Grand Auction day of 38EK. */
	static final class LelangHari {
		static boolean lelang(long day) {
			return Math.floorMod(day, 7L) == 5L;
		}

		private LelangHari() {
		}
	}

	/** Every small event that runs on {@code day}. */
	static List<Acara> kecil(MinecraftServer server, long town, long day) {
		List<Acara> out = new ArrayList<>();
		for (Acara a : ACARA.values()) {
			if (a.prioritas() == 0 && a.nyala() && a.hariAsli(server, town, day)) {
				out.add(a);
			}
		}
		return out;
	}

	// ================================================================== running state (KotaBesarData)

	/** The running great event of place {@code situsId}, else a running small one, or null. */
	static @Nullable String aktif(long situsId) {
		MinecraftServer server = KotaBesar.server();
		if (server == null || !KotaBesarConfig.get().acara || !KotaBesarConfig.hidup()) {
			return null;
		}
		CompoundTag run = KotaBesarData.get(server).bagian(situsId, "jalan");
		String best = null;
		int bestP = -1;
		for (String id : run.keySet()) {
			Acara a = ACARA.get(id);
			if (a != null && a.prioritas() > bestP) {
				bestP = a.prioritas();
				best = id;
			}
		}
		return best;
	}

	static boolean berjalan(MinecraftServer server, long town, String id) {
		return KotaBesarData.get(server).bagian(town, "jalan").contains(id);
	}

	static boolean hidup(MinecraftServer server, long town, String id) {
		return KotaBesarData.get(server).bagian(town, "jalan").getCompoundOrEmpty(id).getBooleanOr("hidup", false);
	}

	/** Every 20 ticks: starts and ends the events of every known capital. */
	static void tick(MinecraftServer server) {
		if (!KotaBesarConfig.get().acara || !KotaBesarConfig.hidup()) {
			return;
		}
		ServerLevel level = server.overworld();
		long day = Kota.day(server);
		long t = Kota.timeOfDay(server);
		long now = level.getGameTime();
		for (long town : Towns.known(server)) {
			if (!Towns.isKotaBesar(server, town)) {
				continue;
			}
			Kandidat k = KotaBesarKota.dariSitus(level, town);
			if (k == null) {
				continue;
			}
			KotaBesarData data = KotaBesarData.get(server);
			CompoundTag run = data.bagian(town, "jalan");
			// end what is over
			for (String id : List.copyOf(run.keySet())) {
				Acara a = ACARA.get(id);
				CompoundTag st = run.getCompoundOrEmpty(id);
				boolean over = a == null || now >= st.getLongOr("sampai", 0L);
				if (over) {
					run.remove(id);
					data.putBagian(town, "jalan", run);
					if (a != null) {
						a.akhir(level, k, town, st.getBooleanOr("hidup", false));
						TempatEvents.fireAcara(level, town, id, false);
					}
				} else if (a != null && st.getBooleanOr("hidup", false)) {
					a.tick(level, k, town, now);
				}
			}
			// start what is due
			List<Acara> due = new ArrayList<>(kecil(server, town, day));
			String besar = jadwal(server, town, day);
			if (besar != null && !AcaraLelang.ID.equals(besar)) {
				due.add(ACARA.get(besar));
			}
			for (Acara a : due) {
				if (a == null || run.contains(a.id()) || t < a.mulaiJam() || t >= Math.min(24000L, a.akhirJam())) {
					continue;
				}
				String doneKey = "selesai." + a.id();
				if (data.getLong(town, doneKey, Long.MIN_VALUE) == day) {
					continue;
				}
				data.putLong(town, doneKey, day);
				boolean live = adaPemain(level, k);
				CompoundTag st = new CompoundTag();
				st.putLong("hari", day);
				st.putLong("mulai", now);
				st.putLong("sampai", now + (a.akhirJam() - t));
				st.putBoolean("hidup", live);
				run.put(a.id(), st);
				data.putBagian(town, "jalan", run);
				TempatEvents.fireAcara(level, town, a.id(), true);
				a.mulai(level, k, town, live);
			}
		}
	}

	static boolean adaPemain(ServerLevel level, Kandidat k) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				return true;
			}
		}
		return false;
	}

	/** Starts event {@code id} now (command); false when unknown. */
	static boolean paksaMulai(ServerLevel level, Kandidat k, String id) {
		Acara a = ACARA.get(id);
		if (a == null) {
			return false;
		}
		long town = KotaBesarKota.situs(k);
		KotaBesarData data = KotaBesarData.get(level.getServer());
		CompoundTag run = data.bagian(town, "jalan");
		if (run.contains(id)) {
			return true;
		}
		CompoundTag st = new CompoundTag();
		st.putLong("hari", Kota.day(level.getServer()));
		st.putLong("mulai", level.getGameTime());
		st.putLong("sampai", level.getGameTime() + (a.akhirJam() - a.mulaiJam()));
		st.putBoolean("hidup", true);
		st.putBoolean("paksa", true);
		run.put(id, st);
		data.putBagian(town, "jalan", run);
		TempatEvents.fireAcara(level, town, id, true);
		a.mulai(level, k, town, true);
		return true;
	}

	/** Ends event {@code id} now (command); false when it is not running. */
	static boolean paksaAkhir(ServerLevel level, Kandidat k, String id) {
		long town = KotaBesarKota.situs(k);
		KotaBesarData data = KotaBesarData.get(level.getServer());
		CompoundTag run = data.bagian(town, "jalan");
		if (!run.contains(id)) {
			return false;
		}
		boolean live = run.getCompoundOrEmpty(id).getBooleanOr("hidup", false);
		run.remove(id);
		data.putBagian(town, "jalan", run);
		Acara a = ACARA.get(id);
		if (a != null) {
			a.akhir(level, k, town, live);
		}
		TempatEvents.fireAcara(level, town, id, false);
		return true;
	}

	/** The running count of beast tides (gate in turn). */
	static int pertahananKali(MinecraftServer server, long town) {
		return (int) KotaBesarData.get(server).getLong(town, "pertahanan_kali", 0L);
	}

	/** A player came near: the pictures of the running live events (festival sky, banners). */
	static void kirimKeadaan(ServerLevel level, ServerPlayer player, Kandidat k, long town) {
		if (berjalan(level.getServer(), town, LembarKunci.Acara.ACARA_FESTIVAL)) {
			KotaBesarPayload.kirim(player, "festival", FestivalLentera.data(level, k, town, true));
		}
		if (berjalan(level.getServer(), town, LembarKunci.Acara.ACARA_TURNAMEN)) {
			KotaBesarPayload.kirim(player, "panji", TurnamenAgung.panji(level, k, town, true));
		}
		if (berjalan(level.getServer(), town, LembarKunci.Acara.ACARA_PASAR_MALAM)) {
			KotaBesarPayload.kirim(player, "pasar_malam", PasarMalam.data(level, k, town, true));
		}
	}

	// ================================================================== the "acara" screen

	/** Which event an NPC serves: memory {@code kb_acara}, else its service lot, else the running great event. */
	static @Nullable String acaraNpc(MinecraftServer server, @Nullable KultivatorNpc npc, long town) {
		if (npc != null) {
			String own = npc.memory().getStringOr("kb_acara", "");
			if (!own.isEmpty()) {
				return own;
			}
			String lot = npc.memory().getStringOr("kotabesar_layanan", "");
			switch (lot) {
				case "turnamen", "ruang_juara" -> {
					return LembarKunci.Acara.ACARA_TURNAMEN;
				}
				case "ujian_alkemis", "gilda_alkemis" -> {
					return LembarKunci.Acara.ACARA_PERTEMUAN_PIL;
				}
				case "hari_gerbang" -> {
					return LembarKunci.Acara.ACARA_HARI_GERBANG;
				}
				case "pertemuan_sekte" -> {
					return LembarKunci.Acara.ACARA_PERTEMUAN_SEKTE;
				}
				case "audiensi", "perintah_istana" -> {
					return Istana.ID;
				}
				default -> {
				}
			}
		}
		return aktif(town);
	}

	private static long townDari(ServerPlayer p, int npcId) {
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc && Hukum.ibuKota(p.level().getServer(), npc.town())) {
			return npc.town();
		}
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		return k == null ? 0L : KotaBesarKota.situs(k);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		long town = townDari(p, npcId);
		if (town == 0L) {
			return null;
		}
		MinecraftServer server = p.level().getServer();
		KultivatorNpc npc = npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc n ? n : null;
		String id = acaraNpc(server, npc, town);
		CompoundTag tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_KAIN, null);
		if (npc != null) {
			tag.put(LembarKunci.NPC, KotaBesarTampil.kartu(npc));
		}
		long day = Kota.day(server);
		long t = Kota.timeOfDay(server);
		tag.put(LembarKunci.Acara.JADWAL, jadwalDaftar(server, town, day));
		if (id == null) {
			LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.acara.kalender"));
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.tidak_ada"));
			return tag;
		}
		Acara a = ACARA.get(id);
		if (a == null) {
			return tag;
		}
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.acara." + id));
		tag.putString(LembarKunci.Acara.ACARA_ID, id);
		if (a.prioritas() < 0) {
			a.lembar(p, town, npc, tag);
			return tag;
		}
		tag.putString(LembarKunci.JENIS, id);
		boolean running = berjalan(server, town, id);
		long sisa;
		String status;
		if (running) {
			status = LembarKunci.Lelang.S_BERJALAN;
			sisa = Math.floorMod(a.akhirJam() - t, 24000L);
		} else if (id.equals(jadwal(server, town, day)) && t < a.mulaiJam() || a.prioritas() == 0 && a.hariAsli(server, town, day) && t < a.mulaiJam()) {
			status = LembarKunci.Lelang.S_BELUM;
			sisa = a.mulaiJam() - t;
		} else {
			status = LembarKunci.Lelang.S_SELESAI;
			sisa = berikut(server, town, id, day, t);
		}
		tag.putString(LembarKunci.STATUS, status);
		if (sisa > 0L) {
			tag.putLong(LembarKunci.SISA_TICK, sisa);
		}
		LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota_besar.acara." + id + ".jam", jam(a.mulaiJam()), jam(a.akhirJam())));
		String ringkas = KotaBesarData.get(server).getString(town, "ringkasan." + id);
		if (!ringkas.isEmpty() && !running) {
			LembarKunci.teks(tag, LembarKunci.Acara.RINGKASAN, Component.translatable(ringkas));
		}
		a.lembar(p, town, npc, tag);
		return tag;
	}

	private static long berikut(MinecraftServer server, long town, String id, long day, long t) {
		Acara a = ACARA.get(id);
		for (int i = 1; i <= 40; i++) {
			long d = day + i;
			boolean on = a.prioritas() == 0 ? a.hariAsli(server, town, d) : id.equals(jadwal(server, town, d));
			if (on) {
				return i * 24000L + a.mulaiJam() - t;
			}
		}
		return 0L;
	}

	private static ListTag jadwalDaftar(MinecraftServer server, long town, long day) {
		ListTag list = new ListTag();
		for (int i = 0; i < 14 && list.size() < 10; i++) {
			long d = day + i;
			String id = jadwal(server, town, d);
			if (id != null) {
				Acara a = ACARA.get(id);
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.acara." + id));
				row.putInt(LembarKunci.HARI, (int) d);
				LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.jam", jam(a.mulaiJam()), jam(a.akhirJam())));
				list.add(row);
			}
			if (Math.floorMod(d, 30L) == 15L) {
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.acara." + LembarKunci.Acara.ACARA_PERTEMUAN_SEKTE));
				row.putInt(LembarKunci.HARI, (int) d);
				LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.jam", jam(6000L), jam(9000L)));
				list.add(row);
			}
		}
		if (!SUMBER_JADWAL.isEmpty()) {
			List<CompoundTag> rows = new ArrayList<>();
			for (Tag each : list) {
				if (each instanceof CompoundTag row) {
					rows.add(row);
				}
			}
			for (SumberJadwal sumber : SUMBER_JADWAL) {
				try {
					sumber.isi(server, town, day, 14, rows);
				} catch (RuntimeException ignored) {
					// a broken source never breaks the calendar
				}
			}
			rows.sort(java.util.Comparator.comparingInt(row -> row.getIntOr(LembarKunci.HARI, 0)));
			list = new ListTag();
			for (CompoundTag row : rows) {
				if (list.size() >= 24) {
					break;
				}
				list.add(row);
			}
		}
		return list;
	}

	/** "08.00" of a time of day (0 = 06.00). */
	static String jam(long time) {
		long t = Math.floorMod(time, 24000L);
		long hours = (t / 1000L + 6L) % 24L;
		long minutes = (t % 1000L) * 60L / 1000L;
		return String.format(java.util.Locale.ROOT, "%02d.%02d", hours, minutes);
	}

	private static void aksiLayar(ServerPlayer p, int npcId, String op) {
		long town = townDari(p, npcId);
		if (town == 0L) {
			return;
		}
		KultivatorNpc npc = npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc n ? n : null;
		String id = acaraNpc(p.level().getServer(), npc, town);
		Acara a = id == null ? null : ACARA.get(id);
		if (a != null) {
			a.aksi(p, town, npc, LembarKunci.urai(op));
		}
	}

	/** Refreshes the open {@code acara} screen of a player (after an action or a score change). */
	static void segarkan(ServerPlayer p) {
		if (!TempatLayar.sedangMelihat(p, LembarKunci.Acara.LAYAR)) {
			return;
		}
		int npcId = TempatLayar.npcTerbuka(p);
		CompoundTag tag = bangun(p, npcId);
		if (tag != null) {
			TempatLayar.segarkan(p, LembarKunci.Acara.LAYAR, npcId, tag);
		}
	}

	/** Refreshes with a result line. */
	static void segarkan(ServerPlayer p, Component pesan, boolean ok) {
		if (!TempatLayar.sedangMelihat(p, LembarKunci.Acara.LAYAR)) {
			return;
		}
		int npcId = TempatLayar.npcTerbuka(p);
		CompoundTag tag = bangun(p, npcId);
		if (tag != null) {
			KotaBesarTampil.pesan(tag, pesan, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
			TempatLayar.segarkan(p, LembarKunci.Acara.LAYAR, npcId, tag);
		}
	}

	/** The "Acara" entry of the dialog's services for the event NPCs of a capital. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer p, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (!KotaBesarConfig.get().acara || !Hukum.ibuKota(p.level().getServer(), npc.town())) {
			return out;
		}
		NpcRole r = npc.role();
		boolean melayani = r == NpcRole.PENJAGA_TEKA || r == NpcRole.PENGUJI || r == NpcRole.MENTERI && "pertemuan_sekte".equals(
				npc.memory().getStringOr("kotabesar_layanan", "")) || !npc.memory().getStringOr("kb_acara", "").isEmpty();
		if (!melayani) {
			return out;
		}
		String id = acaraNpc(p.level().getServer(), npc, npc.town());
		Component nama = Component.translatable(id == null ? "kultivator.kota_besar.acara.kalender" : "kultivator.kota_besar.acara." + id);
		out.add(new TempatDialog.JasaEntri(LembarKunci.Acara.LAYAR, nama, LembarKunci.Ikon.LONCENG,
				Component.translatable("kultivator.kota_besar.jasa.acara.catatan"), false, null));
		if (LembarKunci.Acara.ACARA_HARI_GERBANG.equals(id)) {
			out.add(new TempatDialog.JasaEntri(LembarKunci.LapakRekrut.LAYAR, Component.translatable("kultivator.kota_besar.jasa.rekrut"),
					LembarKunci.Ikon.LAPAK_SEKTE, Component.translatable("kultivator.kota_besar.jasa.rekrut.catatan"),
					!berjalan(p.level().getServer(), npc.town(), id), Component.translatable("kultivator.kota_besar.acara.belum_buka")));
		}
		return out;
	}

	// ================================================================== the two thin adapters

	/** The beast tide in the calendar (the live part is {@link GelombangBinatang}). */
	static final class AcaraPertahanan implements Acara {
		@Override
		public String id() {
			return LembarKunci.Acara.ACARA_PERTAHANAN;
		}

		@Override
		public int prioritas() {
			return 6;
		}

		@Override
		public boolean nyala() {
			return KotaBesarConfig.get().gelombangBinatang;
		}

		/** Every 20 +- 4 days by the city's own hash, or the night after a Blood Eclipse (one in two). */
		@Override
		public boolean hariAsli(MinecraftServer server, long town, long day) {
			if (day == KotaBesarData.get(server).getLong(town, "gerhana_pasang", Long.MIN_VALUE)) {
				return true;
			}
			long t = Math.floorMod(town * 0x9E3779B97F4A7C15L >>> 40, 24L) + 8L;
			int n = 0;
			while (t < day && n < 10000) {
				t += 16L + Math.floorMod((town ^ (n * 0xC2B2AE3D27D4EB4FL)) >>> 37, 9L);
				n++;
			}
			return t == day;
		}

		@Override
		public long mulaiJam() {
			return 13000L;
		}

		@Override
		public long akhirJam() {
			return 18000L;
		}

		@Override
		public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
			MinecraftServer server = level.getServer();
			int kali = pertahananKali(server, town);
			KotaBesarData.get(server).putLong(town, "pertahanan_kali", kali + 1L);
			GelombangBinatang.mulai(level, k, town, hidup, kali);
		}

		@Override
		public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
			GelombangBinatang.hentikan(level, town, false);
			String hasil = KotaBesarData.get(level.getServer()).getString(town, "pertahanan_hasil");
			KotaBesarData.get(level.getServer()).putString(town, "ringkasan." + id(),
					"kultivator.kota_besar.acara.ringkasan.pertahanan." + (hasil.isEmpty() ? "kalah" : hasil));
		}

		@Override
		public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.gelombang_binatang.catatan"));
		}
	}

	/** The Grand Auction's slot (38EK runs it; the screen points at the auction house). */
	static final class AcaraLelang implements Acara {
		static final String ID = LembarKunci.Acara.ACARA_LELANG_AGUNG;

		@Override
		public String id() {
			return ID;
		}

		@Override
		public int prioritas() {
			return 4;
		}

		@Override
		public boolean hariAsli(MinecraftServer server, long town, long day) {
			return LelangHari.lelang(day);
		}

		@Override
		public long mulaiJam() {
			return 12000L;
		}

		@Override
		public long akhirJam() {
			return 15000L;
		}

		@Override
		public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		}

		@Override
		public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		}

		@Override
		public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.lelang_agung.catatan"));
		}
	}

	/** Called from the sky: a Blood Eclipse night makes the next night a tide night for one capital in two. */
	static void gerhana(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!SkyEvents.is(level, SkyKind.BLOOD_ECLIPSE)) {
			return;
		}
		long day = Kota.day(server);
		KotaBesarData data = KotaBesarData.get(server);
		for (long town : Towns.known(server)) {
			if (!Towns.isKotaBesar(server, town) || data.getLong(town, "gerhana_hari", Long.MIN_VALUE) == day) {
				continue;
			}
			data.putLong(town, "gerhana_hari", day);
			if (Math.floorMod((town ^ day) >>> 21, 2L) == 0L) {
				data.putLong(town, "gerhana_pasang", day + 1L);
			}
		}
	}

	/** Nama Baik of a player in a town (shortcut for the events). */
	static int nb(ServerPlayer p, long town) {
		return KotaData.get(p.level().getServer()).reputation(town, p.getUUID());
	}

	private KotaBesarKalender() {
	}
}

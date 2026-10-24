package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.Surpass;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.HubunganService;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.MisiHasil;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.TurnamenHasil;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.turnamen.Elo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Long-term goals (Cita-cita, DESIGN 39.3.4) and the events that change paths and goals (39.3.5). A disciple keeps at
 * most three active goals in {@code extra.mandiri.cita} ({@code {jenis, target, mulai, prog, prio, asal}}) and the last
 * eight finished ones in {@code citaSelesai}. Every kind has a pure progress function over data that already exists
 * (realm steps, Dao comprehension, learned techniques, sword tier, tournament titles, ties, the master surpassed, sect
 * rank, purse, the statistics other 39 modules record); only {@code prog} is new. The daily step refreshes progress,
 * finishes what was reached (a memory, contentment, the {@code CITA_SELESAI} event, a diary page), drops what failed and
 * fills the list again from the path.
 * <p>
 * The 36 events (tournament ended, a friend buried, a lover found, a mission home, a technique studied, the ancestor
 * rite) reach loaded disciples at once and unloaded ones through the hall record's queue ({@code MandiriData.antre}).
 */
public final class CitaCita {

	public static final int AKTIF_MAKS = 3;
	public static final int SELESAI_MAKS = 8;

	/** The goals a path raises, strongest first (39.3.1, column "Cita-cita khas"). */
	static JenisCita[] khas(JalanHidup jalan) {
		if (jalan == null) {
			return new JenisCita[]{JenisCita.RANAH};
		}
		return switch (jalan) {
			case PEDANG -> new JenisCita[]{JenisCita.JUARA, JenisCita.TEKNIK, JenisCita.PUSAKA, JenisCita.DENDAM};
			case ALKEMIS -> new JenisCita[]{JenisCita.KARYA, JenisCita.RANAH, JenisCita.KEKAYAAN};
			case FORMASI -> new JenisCita[]{JenisCita.KARYA, JenisCita.TEKNIK, JenisCita.RANAH};
			case PERTAPA -> new JenisCita[]{JenisCita.RANAH, JenisCita.DAO, JenisCita.GURU};
			case PETUALANG -> new JenisCita[]{JenisCita.RAHASIA, JenisCita.RANAH, JenisCita.PUSAKA};
			case PEMBURU -> new JenisCita[]{JenisCita.BINATANG, JenisCita.PUSAKA, JenisCita.KEKAYAAN};
			case PEDAGANG -> new JenisCita[]{JenisCita.KEKAYAAN, JenisCita.JUARA, JenisCita.JODOH};
			case CENDEKIA -> new JenisCita[]{JenisCita.TEKNIK, JenisCita.DAO, JenisCita.JENJANG};
			case PENJAGA -> new JenisCita[]{JenisCita.MENOLONG, JenisCita.JENJANG, JenisCita.RANAH};
			case IBLIS -> new JenisCita[]{JenisCita.DENDAM, JenisCita.RANAH};
		};
	}

	/** Memory weight of a reached goal (lasting 9 for the champion and the lover). */
	static int bobot(JenisCita jenis) {
		return switch (jenis) {
			case RANAH, PUSAKA, RAHASIA -> 6;
			case DAO, JENJANG, BINATANG, MENOLONG, KARYA -> 5;
			case TEKNIK, KEKAYAAN -> 4;
			case DENDAM -> 7;
			case JUARA, JODOH, GURU -> 9;
		};
	}

	// ================================================================== reading

	public record Cita(JenisCita jenis, String target, long mulai, int prog, int prio, String asal) {

		CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("jenis", jenis.id());
			tag.putString("target", target);
			tag.putLong("mulai", mulai);
			tag.putInt("prog", prog);
			tag.putInt("prio", prio);
			tag.putString("asal", asal);
			return tag;
		}

		static @Nullable Cita of(CompoundTag tag) {
			JenisCita jenis = JenisCita.byId(tag.getStringOr("jenis", ""));
			return jenis == null ? null : new Cita(jenis, tag.getStringOr("target", ""), tag.getLongOr("mulai", 0L),
					tag.getIntOr("prog", 0), Math.max(1, Math.min(3, tag.getIntOr("prio", 3))), tag.getStringOr("asal", ""));
		}
	}

	public static List<Cita> aktif(DiscipleData data) {
		ListTag list = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		List<Cita> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			Cita c = Cita.of(list.getCompoundOrEmpty(i));
			if (c != null) {
				out.add(c);
			}
		}
		return out;
	}

	private static void simpan(DiscipleData data, List<Cita> list) {
		list.sort((a, b) -> Integer.compare(a.prio(), b.prio()));
		ListTag tag = new ListTag();
		int prio = 1;
		for (Cita c : list) {
			if (tag.size() >= AKTIF_MAKS) {
				break;
			}
			tag.add(new Cita(c.jenis(), c.target(), c.mulai(), c.prog(), prio++, c.asal()).toTag());
		}
		MandiriApi.bagianTulis(data).put(MandiriIds.K_CITA, tag);
	}

	private static void arsip(DiscipleData data, Cita c, long hari, String hasil) {
		CompoundTag section = MandiriApi.bagianTulis(data);
		ListTag list = section.getListOrEmpty(MandiriIds.K_CITA_SELESAI).copy();
		CompoundTag done = new CompoundTag();
		done.putString("jenis", c.jenis().id());
		done.putString("target", c.target());
		done.putLong("hari", hari);
		done.putString("hasil", hasil);
		list.add(done);
		while (list.size() > SELESAI_MAKS) {
			list.remove(0);
		}
		section.put(MandiriIds.K_CITA_SELESAI, list);
	}

	// ================================================================== progress (pure)

	/** Progress 0..100 of a goal (100 = reached). */
	static int kemajuan(DiscipleEntity murid, Cita c) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		int target = angka(c.target(), 1);
		return switch (c.jenis()) {
			case RANAH -> persen(data.rank().step(), target);
			case DAO -> data.dao() == null ? 0 : persen((int) data.comprehension(), target);
			case TEKNIK -> persen(data.learnedIds().size(), target);
			case PUSAKA -> persen(MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_PERLENGKAPAN).getIntOr("pedang", 0), target);
			case JUARA -> Elo.titles(data).size() >= target ? 100
					: Math.max(0, Math.min(95, (Elo.rating(data) - Elo.START) / 4 + Elo.wins(data)));
			case DENDAM -> {
				UUID foe = uuid(c.target());
				if (foe == null || owner == null) {
					yield 0;
				}
				Ikatan tie = SekteApi.hubungan().kind(owner, soul, foe);
				if (tie == Ikatan.DENDAM) {
					yield Math.max(0, Math.min(90, 50 + SekteApi.hubungan().affinity(owner, soul, foe) / 2));
				}
				// A foe outside the hall (a named rival, a feud sect's disciple): 39C/39F end it with selesaikan().
				yield tie == Ikatan.NONE ? c.prog() : 100;
			}
			case JODOH -> {
				if (owner == null) {
					yield 0;
				}
				HubunganService r = SekteApi.hubungan();
				if (!r.with(owner, soul, Ikatan.KEKASIH).isEmpty() || !r.with(owner, soul, Ikatan.PASANGAN_DAO).isEmpty()) {
					yield 100;
				}
				yield Math.max(0, Math.min(90, r.socialBalance(owner, soul)));
			}
			case GURU -> Surpass.surpassed(data) ? 100 : persen(data.rank().step(), Rank.MAX_STEP);
			case JENJANG -> SectRanks.rank(data) >= target ? 100 : persen(SectRanks.rank(data) * 100 + Math.min(99, SectRanks.pk(data) / 10), target * 100);
			case KEKAYAAN -> persen((int) Math.min(Integer.MAX_VALUE, MandiriApi.dompet(data) + MandiriApi.statistik(data, MandiriIds.S_HASIL_NB)), target);
			case BINATANG -> persen((int) MandiriApi.statistik(data, MandiriIds.S_BINATANG), target);
			case RAHASIA -> persen((int) MandiriApi.statistik(data, MandiriIds.S_RAHASIA), target);
			case MENOLONG -> persen((int) MandiriApi.statistik(data, MandiriIds.S_MENOLONG), target);
			case KARYA -> persen((int) MandiriApi.statistik(data, MandiriIds.S_KARYA), target);
		};
	}

	private static int persen(int value, int target) {
		if (target <= 0) {
			return 100;
		}
		return Math.max(0, Math.min(100, (int) Math.floor(100.0D * value / target)));
	}

	private static int angka(String text, int fallback) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	// ================================================================== new goals

	/** A fresh goal of {@code jenis} for this disciple now, or null when it makes no sense (no Dao, no enemy...). */
	static @Nullable Cita buat(DiscipleEntity murid, JenisCita jenis, int prio, long hari, String asal) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		String target = switch (jenis) {
			case RANAH -> {
				Rank rank = data.rank();
				if (rank.isMax()) {
					yield null;
				}
				// The first stage of the next realm (or the next layer of Immortal Ascension).
				int step = rank.step() + 1;
				while (step <= Rank.MAX_STEP && Rank.ofStep(step).realm() == rank.realm()
						&& !(rank.realm() == Realm.IMMORTAL_ASCENSION && Rank.ofStep(step).layer() != rank.layer())) {
					step++;
				}
				yield String.valueOf(Math.min(Rank.MAX_STEP, step));
			}
			case DAO -> data.dao() == null || data.comprehension() >= 100.0D ? null
					: String.valueOf(Math.min(100, ((int) data.comprehension() / 25 + 1) * 25));
			case TEKNIK -> String.valueOf(data.learnedIds().size() + 2);
			case PUSAKA -> {
				int tier = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_PERLENGKAPAN).getIntOr("pedang", 0);
				yield tier >= 5 ? null : String.valueOf(tier + 1);
			}
			case JUARA -> SekteContent.on(SekteIds.T) ? String.valueOf(Elo.titles(data).size() + 1) : null;
			case DENDAM -> {
				if (owner == null) {
					yield null;
				}
				List<UUID> foes = SekteApi.hubungan().with(owner, soul, Ikatan.DENDAM);
				yield foes.isEmpty() ? null : foes.get(0).toString();
			}
			case JODOH -> {
				if (owner == null) {
					yield null;
				}
				HubunganService r = SekteApi.hubungan();
				yield !r.with(owner, soul, Ikatan.KEKASIH).isEmpty() || !r.with(owner, soul, Ikatan.PASANGAN_DAO).isEmpty() ? null : "1";
			}
			case GURU -> owner == null || Surpass.surpassed(data) ? null : "1";
			case JENJANG -> SectRanks.rank(data) >= 5 ? null : String.valueOf(SectRanks.rank(data) + 1);
			case KEKAYAAN -> String.valueOf(Math.max(100L, (MandiriApi.dompet(data) + MandiriApi.statistik(data, MandiriIds.S_HASIL_NB)) * 2L
					+ 100L * (data.realm().ordinal() + 1L)));
			case BINATANG -> String.valueOf(Math.min(9L, MandiriApi.statistik(data, MandiriIds.S_BINATANG) + 1L));
			case RAHASIA -> data.realm().ordinal() < Realm.FOUNDATION.ordinal() ? null
					: String.valueOf(Math.min(9L, MandiriApi.statistik(data, MandiriIds.S_RAHASIA) + 1L));
			case MENOLONG -> String.valueOf(MandiriApi.statistik(data, MandiriIds.S_MENOLONG) + 5L);
			case KARYA -> String.valueOf(Math.min(6L, MandiriApi.statistik(data, MandiriIds.S_KARYA) + 1L));
		};
		if (target == null) {
			return null;
		}
		Cita c = new Cita(jenis, target, hari, 0, prio, asal == null ? "" : asal);
		return new Cita(jenis, target, hari, kemajuan(murid, c), prio, c.asal());
	}

	/**
	 * Adds a goal from an event or another module (a duel lost to a rival: DENDAM prio 1). An existing goal of the same
	 * kind and target only moves up in priority. Returns false when it makes no sense now.
	 */
	public static boolean tambah(DiscipleEntity murid, JenisCita jenis, @Nullable String target, int prio, String asal) {
		long hari = Irama.hari(murid.level());
		List<Cita> list = new ArrayList<>(aktif(murid.data()));
		Cita fresh;
		if (target != null && !target.isEmpty()) {
			Cita c = new Cita(jenis, target, hari, 0, prio, asal == null ? "" : asal);
			fresh = new Cita(jenis, target, hari, kemajuan(murid, c), prio, c.asal());
		} else {
			fresh = buat(murid, jenis, prio, hari, asal);
		}
		if (fresh == null) {
			return false;
		}
		for (int i = 0; i < list.size(); i++) {
			Cita old = list.get(i);
			if (old.jenis() == jenis && (target == null || old.target().equals(fresh.target()))) {
				list.set(i, new Cita(old.jenis(), old.target(), old.mulai(), old.prog(), Math.min(old.prio(), prio), old.asal()));
				geser(list, list.get(i));
				simpan(murid.data(), list);
				return true;
			}
		}
		// A new priority-1 goal pushes the others down; a full list drops the weakest that can be dropped.
		list.add(fresh);
		geser(list, fresh);
		while (list.size() > AKTIF_MAKS) {
			Cita weakest = null;
			for (Cita c : list) {
				if (c != fresh && (weakest == null || c.prio() >= weakest.prio())) {
					weakest = c;
				}
			}
			list.remove(weakest == null ? list.get(list.size() - 1) : weakest);
		}
		simpan(murid.data(), list);
		return true;
	}

	private static void geser(List<Cita> list, Cita top) {
		if (top.prio() != 1) {
			return;
		}
		for (int i = 0; i < list.size(); i++) {
			Cita c = list.get(i);
			if (c != top && c.prio() <= 1) {
				list.set(i, new Cita(c.jenis(), c.target(), c.mulai(), c.prog(), 2, c.asal()));
			}
		}
	}

	/** The master's [Dukung]: this goal becomes priority 1. */
	public static void dukung(DiscipleEntity murid, JenisCita jenis) {
		List<Cita> list = new ArrayList<>(aktif(murid.data()));
		for (int i = 0; i < list.size(); i++) {
			Cita c = list.get(i);
			if (c.jenis() == jenis) {
				Cita top = new Cita(c.jenis(), c.target(), c.mulai(), c.prog(), 1, c.asal().isEmpty() ? "guru" : c.asal());
				list.set(i, top);
				geser(list, top);
				simpan(murid.data(), list);
				return;
			}
		}
	}

	/** The master's [Batalkan] (never for RANAH and GURU). */
	public static boolean batalkan(DiscipleEntity murid, JenisCita jenis) {
		if (!jenis.bisaDibatalkan()) {
			return false;
		}
		List<Cita> list = new ArrayList<>(aktif(murid.data()));
		long hari = Irama.hari(murid.level());
		for (Cita c : list) {
			if (c.jenis() == jenis) {
				list.remove(c);
				arsip(murid.data(), c, hari, "batal");
				simpan(murid.data(), list);
				return true;
			}
		}
		return false;
	}

	// ================================================================== the daily step

	/** Refreshes, finishes, drops and refills the goals of a loaded disciple (once per world day). */
	public static void perbarui(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		ServerLevel level = (ServerLevel) murid.level();
		MinecraftServer server = level.getServer();
		long hari = Irama.hari(level);
		List<Cita> list = new ArrayList<>();
		for (Cita c : aktif(data)) {
			int prog = kemajuan(murid, c);
			Cita now = new Cita(c.jenis(), c.target(), c.mulai(), prog, c.prio(), c.asal());
			if (prog >= 100) {
				capai(murid, now, server, hari);
				if (c.jenis() == JenisCita.RANAH) {
					// RANAH never ends: the target rises.
					Cita next = buat(murid, JenisCita.RANAH, c.prio(), hari, c.asal());
					if (next != null) {
						list.add(next);
					}
				}
				continue;
			}
			if (gagal(murid, now, hari)) {
				continue;
			}
			list.add(now);
		}
		// Refill from the path (the path's first goal leads unless an event goal holds priority 1).
		JalanHidup jalan = MandiriApi.jalan(data);
		boolean ranah = false;
		for (Cita c : list) {
			ranah |= c.jenis() == JenisCita.RANAH;
		}
		for (JenisCita jenis : khas(jalan)) {
			if (list.size() >= AKTIF_MAKS) {
				break;
			}
			if (ada(list, jenis)) {
				continue;
			}
			Cita fresh = buat(murid, jenis, list.isEmpty() ? 1 : list.size() + 1, hari, "jalan." + (jalan == null ? "" : jalan.id()));
			if (fresh != null) {
				list.add(fresh);
				ranah |= jenis == JenisCita.RANAH;
			}
		}
		if (!ranah && list.size() < AKTIF_MAKS) {
			Cita fresh = buat(murid, JenisCita.RANAH, list.size() + 1, hari, "ranah");
			if (fresh != null) {
				list.add(fresh);
			}
		}
		simpan(data, list);
	}

	private static boolean ada(List<Cita> list, JenisCita jenis) {
		for (Cita c : list) {
			if (c.jenis() == jenis) {
				return true;
			}
		}
		return false;
	}

	/** A reached goal: memory, contentment, event, diary, news; ambition calms; a loyal rich disciple gives half away. */
	private static void capai(DiscipleEntity murid, Cita c, MinecraftServer server, long hari) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		arsip(data, c, hari, "capai");
		Ingatan.remember(server, data, "mandiri.cita." + c.jenis().id(), null, bobot(c.jenis()),
				"kultivator.sekte.mandiri.inti.ingatan.cita." + c.jenis().id(), "");
		SekteBus.honored(server, owner, soul, "mandiri_cita", c.jenis() == JenisCita.RANAH ? 5 : 3);
		Dorongan.tambah(data, Dorong.AMBISI, -20);
		MandiriBus.citaSelesai(server, owner, soul, c.jenis(), true);
		String nama = murid.getName().getString();
		MandiriApi.catat(owner, soul, new EntriHarian(hari, Irama.jam(murid.level()), "cita",
				"kultivator.sekte.mandiri.inti.harian.cita." + c.jenis().id(), List.of(nama), EntriHarian.Suasana.BANGGA, true));
		MandiriApi.kabar(server, owner, soul, "kultivator.sekte.mandiri.inti.kabar.cita", true, nama, c.jenis().kunciNama());
		if (c.jenis() == JenisCita.KEKAYAAN && SekteApi.hati().has(data, Sifat.SETIA) && owner != null) {
			long half = MandiriApi.dompet(data) / 2L;
			long taken = -MandiriApi.ubahDompet(data, -half, "sumbang");
			if (taken >= 2L) {
				Halls.addPool(owner, (int) Math.min(Integer.MAX_VALUE, taken / 2L));
			}
		}
	}

	/** Failed or stale goals leave (DENDAM after 60 days: forgiven by the caring, else temptation +10). */
	private static boolean gagal(DiscipleEntity murid, Cita c, long hari) {
		DiscipleData data = murid.data();
		long umur = hari - c.mulai();
		boolean drop = false;
		String hasil = "gagal";
		if (c.jenis() == JenisCita.DENDAM && umur >= 60L) {
			drop = true;
			if (SekteApi.hati().has(data, Sifat.PENYAYANG)) {
				hasil = "maaf";
			} else {
				JalanIblis.ubah(murid, 10, "dendam_gagal");
			}
		} else if (c.jenis() == JenisCita.TEKNIK && umur >= 30L && c.prog() < 50) {
			drop = true;
		} else if (c.jenis() == JenisCita.DAO && murid.data().dao() == null) {
			drop = true;
		} else if (umur >= 120L && c.jenis() != JenisCita.RANAH && c.jenis() != JenisCita.GURU && c.prio() > 1) {
			drop = true;
		}
		if (drop) {
			arsip(data, c, hari, hasil);
			MandiriBus.citaSelesai(((ServerLevel) murid.level()).getServer(), murid.ownerId(), murid.soulId(), c.jenis(), false);
		}
		return drop;
	}

	// ================================================================== events (39.3.5)

	static void init() {
		SekteBus.TOURNAMENT_ENDED.register(CitaCita::onTournament);
		SekteBus.DISCIPLE_BURIED.register((server, owner, soul, name) -> onBuried(server, owner, soul));
		SekteBus.RELATION_CHANGED.register(CitaCita::onRelation);
		SekteBus.MISSION_RETURNED.register(CitaCita::onMission);
		SekteBus.DISCIPLE_STUDIED.register((server, owner, soul, techniqueId, learned) -> {
			if (learned) {
				kirim(server, owner, soul, peristiwa("belajar", null));
			}
		});
	}

	private static CompoundTag peristiwa(String jenis, @Nullable String arg) {
		CompoundTag tag = new CompoundTag();
		tag.putString("jenis", jenis);
		if (arg != null) {
			tag.putString("arg", arg);
		}
		return tag;
	}

	/** Applies to the loaded body now, or queues for the next load. */
	private static void kirim(MinecraftServer server, UUID owner, UUID soul, CompoundTag event) {
		if (server == null || owner == null || soul == null || !MandiriApi.aktif()) {
			return;
		}
		DiscipleEntity loaded = Perencana.cari(owner, soul);
		if (loaded != null) {
			terapkan(loaded, event);
		} else {
			MandiriData.get(server).antre(owner, soul, event);
		}
	}

	/** Applies the queued events of a disciple that just loaded. */
	static void terapkanAntre(DiscipleEntity murid) {
		MinecraftServer server = ((ServerLevel) murid.level()).getServer();
		UUID owner = murid.ownerId();
		if (owner == null) {
			return;
		}
		for (CompoundTag event : MandiriData.get(server).ambilAntre(owner, murid.soulId())) {
			terapkan(murid, event);
		}
	}

	private static void terapkan(DiscipleEntity murid, CompoundTag event) {
		DiscipleData data = murid.data();
		String arg = event.getStringOr("arg", "");
		switch (event.getStringOr("jenis", "")) {
			case "final_kalah" -> {
				tambah(murid, JenisCita.JUARA, null, 1, "final_kalah");
				PilihJalan.bonus(data, JalanHidup.PEDANG, 0.5D);
			}
			case "duka" -> {
				tambah(murid, JenisCita.RANAH, null, 1, "duka");
				if (SekteApi.hati().has(data, Sifat.PENDENDAM)) {
					JalanIblis.ubah(murid, 5, "duka");
				}
			}
			case "kekasih" -> {
				// A lover found: the JODOH goal finishes at the next daily step; journeys for two come more often.
				Dorongan.tambah(data, Dorong.SOSIAL, -20);
			}
			case "misi_sukses" -> {
				PilihJalan.bonus(data, JalanHidup.PETUALANG, 0.4D);
				tambah(murid, JenisCita.RAHASIA, null, 2, "misi");
			}
			case "belajar" -> PilihJalan.bonus(data, JalanHidup.CENDEKIA, 0.1D);
			case "dendam" -> tambah(murid, JenisCita.DENDAM, arg.isEmpty() ? null : arg, 1, "kalah");
			default -> {
			}
		}
	}

	private static void onTournament(MinecraftServer server, UUID owner, TurnamenHasil result) {
		if (result.placings().size() >= 2) {
			// The runner-up of the final.
			kirim(server, owner, result.placings().get(1), peristiwa("final_kalah", null));
		}
	}

	private static void onBuried(MinecraftServer server, UUID owner, UUID dead) {
		HubunganService r = SekteApi.hubungan();
		for (DiscipleEntity d : Perencana.dimuat(owner)) {
			Ikatan tie = r.kind(owner, d.soulId(), dead);
			if (tie == Ikatan.SAHABAT || tie == Ikatan.SAUDARA_ANGKAT || tie == Ikatan.KEKASIH || tie == Ikatan.PASANGAN_DAO) {
				terapkan(d, peristiwa("duka", dead.toString()));
			}
		}
	}

	private static void onRelation(MinecraftServer server, UUID owner, UUID a, UUID b, Ikatan before, Ikatan now) {
		if (now == Ikatan.KEKASIH || now == Ikatan.PASANGAN_DAO) {
			kirim(server, owner, a, peristiwa("kekasih", b.toString()));
			kirim(server, owner, b, peristiwa("kekasih", a.toString()));
		}
	}

	private static void onMission(MinecraftServer server, UUID owner, MisiHasil result) {
		if (result.success() >= 2) {
			for (UUID soul : result.team()) {
				kirim(server, owner, soul, peristiwa("misi_sukses", result.missionId()));
			}
		}
	}

	/**
	 * Ends an active goal now (39C/39F: the rival beaten on the road, the feud foe defeated in an honour duel); the
	 * memory, contentment, event and diary of a reached goal follow. Returns false when no such goal is active.
	 */
	public static boolean selesaikan(DiscipleEntity murid, JenisCita jenis, @Nullable String target) {
		List<Cita> list = new ArrayList<>(aktif(murid.data()));
		for (Cita c : list) {
			if (c.jenis() == jenis && (target == null || c.target().equals(target))) {
				list.remove(c);
				simpan(murid.data(), list);
				capai(murid, new Cita(c.jenis(), c.target(), c.mulai(), 100, c.prio(), c.asal()),
						((ServerLevel) murid.level()).getServer(), Irama.hari(murid.level()));
				return true;
			}
		}
		return false;
	}

	/** 39C/39F: the disciple lost to a named rival or a feud foe: a DENDAM goal (applied at once or at the next load). */
	public static void dendam(MinecraftServer server, UUID owner, UUID soul, String target) {
		kirim(server, owner, soul, peristiwa("dendam", target));
	}

	private CitaCita() {
	}
}

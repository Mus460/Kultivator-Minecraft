package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.Irama;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.TujuanProvider;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Module 39C, Perjalanan &amp; Kota (DESIGN 39.6): the entry of package {@code sekte.mandiri.jalan}, called by
 * {@code sekte.mandiri.Mandiri}. It installs everything the other classes of the package need:
 * <ul>
 *   <li>the executors {@code PERJALANAN}, {@code TIBA}, {@code BERKEMAS}, {@code BERANGKAT} (C39.C1, {@link PelaksanaJalan});</li>
 *   <li>the default market and guild ({@code MandiriApi.setPasar}/{@code setGilda}, C39.K1/K4), the purse book
 *       ({@code setKas}), {@code setPergi} and the volunteers of 38EK ({@code setRelawan}, C39.K5);</li>
 *   <li>the {@code Halls} return handler of reason {@code mandiri_jalan} ({@link Kepulangan}) and the sword damage hook
 *       ({@link Perlengkapan});</li>
 *   <li>the town body (C39.C2, {@link Jelmaan}): its two goals on every disciple body and the master's right-click in a
 *       {@code UseEntityCallback} phase before the default one, because {@code Disciples.onUseEntity} answers every
 *       non-DISCIPLE body with SUCCESS;</li>
 *   <li>the server tick: the lazy stage engine with C39.C3 ({@link Tahap#majukan}, every journey once a second, spread
 *       over the second), the town bodies every two seconds, the sword departures every tick, the daily clean-up;</li>
 *   <li>each disciple's tick20: the purse owner for the money book, the morning shopping list and robe pride, and the
 *       arrival scene of a disciple whose record came home;</li>
 *   <li>the commands ({@link JalanCommands}).</li>
 * </ul>
 * A journey's authoritative record lives in {@link JalanData}; a party member's record only points at its leader
 * ({@code ikut}), so {@link #rencana} always answers with the leader's plan.
 * <p>
 * {@link #registerContent} runs always (K1: 39C owns no payload, data component or entity type of its own; the town body
 * is the disciple entity with {@code Role.JELMAAN} and {@code kultivator:beli_murid} is 39D's component); {@link #init}
 * runs only while {@code umum.mandiri} and {@code mandiri.aktif} are on. Both are safe to call twice.
 */
public final class Perjalanan {

	/** At most this many sword departures of one hall in the air at once (39.6.2). */
	static final int TERBANG_MAKS = 2;

	private static boolean content;
	private static boolean initialised;
	private static @Nullable MinecraftServer server;

	/** Sword departures in the air: soul -> body. */
	private static final Map<UUID, DiscipleEntity> TERBANG = new HashMap<>();
	/** Leaders of the journeys still to look at in this second (the stage engine is spread over 20 ticks). */
	private static final List<UUID> ANTRE = new ArrayList<>();
	private static long hariBersih = -1L;
	/** Souls whose body ticked since the last daily clean-up (scenes of the others are forgotten). */
	private static final Set<UUID> TERLIHAT = new HashSet<>();

	/** Registry content of 39C (always, both physical sides): nothing of its own (see the class comment). */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
	}

	/** True once {@link #init} ran, that is while the module is on (the stage engine of {@link Jelmaan} ticks from here). */
	static boolean berjalan() {
		return initialised;
	}

	/** Runtime behaviour of 39C (server listeners, ticks, executors, commands); only while the module is on. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		PelaksanaJalan.daftar();
		MandiriApi.setPasar(PasarBawaan.INSTANCE);
		MandiriApi.setGilda(GildaBawaan.INSTANCE);
		MandiriApi.setKas(Dompet.KAS);
		MandiriApi.setPergi(Perjalanan::pergi);
		MandiriApi.setRelawan(Perjalanan::relawan);
		Perlengkapan.init();
		Halls.addReturnHandler(MandiriIds.AWAY_JALAN, Kepulangan::onReturn);
		DiscipleEntity.addGoalInstaller((entity, goals) -> {
			goals.addGoal(3, new JelmaanJalanGoal(entity));
			goals.addGoal(4, new JelmaanPoseGoal(entity));
		});
		Identifier early = Kultivator.id("mandiri_jelmaan");
		UseEntityCallback.EVENT.register(early, Jelmaan::pakai);
		UseEntityCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		DiscipleHooks.addTick20(Perjalanan::tick20);
		ServerLifecycleEvents.SERVER_STARTED.register(Perjalanan::mulai);
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> kosongkan());
		ServerTickEvents.END_SERVER_TICK.register(Perjalanan::tick);
		JalanCommands.init();
	}

	// ================================================================== server lifecycle

	private static void mulai(MinecraftServer started) {
		server = started;
		PasarBawaan.server(started);
		GildaBawaan.server(started);
		Dompet.server(started);
	}

	private static void kosongkan() {
		Jelmaan.kosongkan();
		PelaksanaJalan.kosongkan();
		TERBANG.clear();
		ANTRE.clear();
		TERLIHAT.clear();
		hariBersih = -1L;
		PasarBawaan.server(null);
		GildaBawaan.server(null);
		Dompet.server(null);
		server = null;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	// ================================================================== the journey of a soul

	/**
	 * The live plan of the journey {@code soul} is on (its leader's plan for a party member), or null when it is home.
	 * Changes to the plan are saved with {@link JalanData#setDirty}.
	 */
	public static @Nullable Rencana rencana(MinecraftServer srv, UUID soul) {
		if (srv == null || soul == null) {
			return null;
		}
		JalanData data = JalanData.get(srv);
		CompoundTag own = data.perjalanan(soul);
		if (own == null) {
			return null;
		}
		String lead = own.getStringOr(Rencana.IKUT, "");
		if (lead.isEmpty()) {
			return new Rencana(own);
		}
		UUID leader = Rencana.uuid(lead);
		CompoundTag leaderTag = leader == null ? null : data.perjalanan(leader);
		return leaderTag == null ? null : new Rencana(leaderTag);
	}

	/** {@code MandiriApi.setPergi}: away on a DESIGN 39 journey (planned, on the road, or home but not yet arrived). */
	private static boolean pergi(UUID owner, UUID soul) {
		MinecraftServer srv = server;
		return srv != null && (JalanData.get(srv).perjalanan(soul) != null || Tahap.rekamAway(owner, soul) != null);
	}

	/**
	 * The party leaves the world (DESIGN 39.6.2 stage 2): the plan is built once, the leader's record carries it, every
	 * member gets a pointer record; then each body goes into {@code AwayStore} with the planned return. A member whose
	 * body cannot leave stays home (dropped from the party); a leader that cannot leave cancels the journey.
	 */
	static void berangkat(ServerLevel level, List<DiscipleEntity> party, JenisJalan jenis, TujuanProvider.Tujuan tujuan) {
		if (party.isEmpty() || party.get(0).ownerId() == null) {
			return;
		}
		MinecraftServer srv = level.getServer();
		DiscipleEntity leader = party.get(0);
		UUID owner = leader.ownerId();
		long now = level.getGameTime();
		JalanData data = JalanData.get(srv);
		Rencana r = Tahap.susun(level, owner, party, jenis, tujuan, now);
		List<String> args = new ArrayList<>();
		args.add(leader.getName().getString());
		String kunci;
		if (r.town() != 0L) {
			args.addAll(JalanIds.kota(r.town()));
			kunci = "kisah.berangkat.kota." + r.moda();
		} else {
			args.add(jenis.kunciNama());
			kunci = "kisah.berangkat." + r.moda();
		}
		r.kisah(JalanIds.k(kunci), args);
		data.simpan(leader.soulId(), r.tag());
		CompoundTag info = new CompoundTag();
		info.putString("jalan", r.id());
		info.putString("jenis", jenis.id());
		info.putString("tujuan", tujuan.id());
		info.putLong("town", r.town());
		// the leader first: without it nobody goes
		Halls.storeAway(owner, leader, r.pulang(), MandiriIds.AWAY_JALAN, info);
		if (Tahap.rekamAway(owner, leader.soulId()) == null) {
			data.hapus(leader.soulId());
			return;
		}
		ListTag rombongan = r.tag().getListOrEmpty(Rencana.ROMBONGAN).copy();
		for (int i = 1; i < party.size(); i++) {
			DiscipleEntity member = party.get(i);
			CompoundTag pointer = new CompoundTag();
			pointer.putString(Rencana.ID, r.id());
			pointer.putString(Rencana.OWNER, owner.toString());
			pointer.putString(Rencana.SOUL, member.soulId().toString());
			pointer.putString(Rencana.NAMA, member.getName().getString());
			pointer.putString(Rencana.IKUT, leader.soulId().toString());
			pointer.putLong(Rencana.BERANGKAT, now); // else the orphan sweep (bersih) can never age this pointer out
			data.simpan(member.soulId(), pointer);
			Halls.storeAway(owner, member, r.pulang(), MandiriIds.AWAY_JALAN, info);
			if (Tahap.rekamAway(owner, member.soulId()) == null) {
				data.hapus(member.soulId());
				for (int j = rombongan.size() - 1; j >= 0; j--) {
					if (member.soulId().toString().equals(rombongan.getStringOr(j, ""))) {
						rombongan.remove(j);
					}
				}
			}
		}
		r.tag().put(Rencana.ROMBONGAN, rombongan);
		data.setDirty();
		Tahap.simpanCermin(srv, r);
		// diary, news, the master's notice
		long hari = Tahap.hariPada(level, now);
		int jam = Tahap.jamPada(level, now);
		List<UUID> souls = r.rombongan();
		for (UUID soul : souls) {
			String nama = r.anggota(soul).getStringOr("nama", r.nama());
			List<String> entri = new ArrayList<>(args);
			entri.set(0, nama);
			MandiriApi.catat(owner, soul, new EntriHarian(hari, jam, "jalan", JalanIds.k("harian." + kunci.substring(6)), entri,
					souls.size() > 1 ? EntriHarian.Suasana.SENANG : EntriHarian.Suasana.TENANG, false));
		}
		MandiriBus.jalanBerangkat(srv, owner, leader.soulId(), jenis, tujuan, souls);
		MutableComponent where = r.town() != 0L ? JalanIds.namaKota(r.town()) : Component.translatable(jenis.kunciNama());
		MutableComponent line = JalanIds.t(souls.size() > 1 ? "pesan.berangkat.rombongan" : "pesan.berangkat",
				leader.getName().copy().withColor(JalanIds.WARNA_NAMA), where, souls.size() - 1, menit(r.pulang() - now));
		MandiriApi.beritahu(srv, owner, leader.soulId(), Kebijakan.Pemberitahuan.PULANG, line.withColor(JalanIds.WARNA_GIOK), false);
	}

	/** Real minutes of {@code ticks} (at least 1). */
	static long menit(long ticks) {
		return Math.max(1L, Math.round(ticks / 1200.0D));
	}

	/**
	 * Moves the timeline so that stage {@code index} starts now (test {@code percepat}, the master's "go on"): every
	 * unresolved stage from {@code index} and the return move earlier; stages before it become due and resolve at once.
	 */
	static void majukanKe(MinecraftServer srv, Rencana r, int index, long now) {
		int n = r.jumlahTahap();
		if (n == 0) {
			return;
		}
		int i = Math.max(r.berikut(), Math.min(index, n - 1));
		long delta = r.tahap(i).getLongOr("mulai", now) - now;
		if (delta > 0L) {
			ListTag stages = r.tahap();
			for (int k = r.berikut(); k < stages.size(); k++) {
				CompoundTag t = stages.getCompoundOrEmpty(k);
				t.putLong("mulai", Math.max(now - 1L, t.getLongOr("mulai", now) - delta));
				t.putLong("sampai", Math.max(now, t.getLongOr("sampai", now) - delta));
			}
			r.setPulang(Math.max(now, r.pulang() - delta));
		}
		Tahap.majukan(srv, r, now);
		Kepulangan.aturPulang(srv, r);
		Tahap.simpanCermin(srv, r);
		JalanData.get(srv).setDirty();
	}

	// ================================================================== sword departures (39.6.2, at most two per hall)

	/** True while fewer than {@link #TERBANG_MAKS} disciples of {@code owner}'s hall rise on their swords. */
	static boolean bolehTerbang(UUID owner) {
		int n = 0;
		for (DiscipleEntity e : TERBANG.values()) {
			if (owner != null && owner.equals(e.ownerId())) {
				n++;
			}
		}
		return n < TERBANG_MAKS;
	}

	static void terbangMulai(DiscipleEntity murid) {
		TERBANG.put(murid.soulId(), murid);
	}

	// ================================================================== the server tick

	private static void tick(MinecraftServer srv) {
		if (server == null) {
			server = srv;
		}
		// sword departures: one position per tick
		for (Iterator<Map.Entry<UUID, DiscipleEntity>> it = TERBANG.entrySet().iterator(); it.hasNext(); ) {
			DiscipleEntity e = it.next().getValue();
			boolean still;
			try {
				still = !e.isRemoved() && PelaksanaJalan.terbangTick(e);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri sword departure failed", exception);
				still = false;
			}
			if (!still) {
				if (!e.isRemoved()) {
					e.setNoGravity(false);
				}
				it.remove();
			}
		}
		long now = srv.overworld().getGameTime();
		int slot = (int) Math.floorMod(now, 20L);
		JalanData data = JalanData.get(srv);
		if (slot == 0) {
			ANTRE.clear();
			for (UUID soul : data.jiwaAktif()) {
				CompoundTag tag = data.perjalanan(soul);
				if (tag != null && tag.getStringOr(Rencana.IKUT, "").isEmpty()) {
					ANTRE.add(soul);
				}
			}
		}
		// the stage engine: this tick's share of the leaders
		int share = Math.max(1, (ANTRE.size() + 19) / 20);
		for (int i = 0; i < share && !ANTRE.isEmpty(); i++) {
			UUID soul = ANTRE.remove(ANTRE.size() - 1);
			Rencana r = rencana(srv, soul);
			if (r == null) {
				continue;
			}
			try {
				if (Tahap.majukan(srv, r, now)) {
					data.setDirty();
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Mandiri journey of {} failed to advance", soul, exception);
			}
		}
		if (now % 40L == 7L) {
			try {
				Jelmaan.perbarui(srv);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri town bodies failed to update", exception);
			}
		}
		if (now % 1200L == 13L) {
			bersih(srv, data, now);
		}
	}

	/** Once a minute: orphaned journeys; once a world day: the shared stock, the purse-owner map, stale scenes. */
	private static void bersih(MinecraftServer srv, JalanData data, long now) {
		for (UUID soul : data.jiwaAktif()) {
			CompoundTag tag = data.perjalanan(soul);
			if (tag == null) {
				continue;
			}
			UUID owner = Rencana.uuid(tag.getStringOr(Rencana.OWNER, ""));
			// a record none of whose bodies is away or waiting to arrive (the hall was removed, the disciple released); a
			// leader's record lives while any member of its party is still out
			if (owner == null || !hidup(data, owner, soul, tag) && now - tag.getLongOr(Rencana.BERANGKAT, now) > 200L) {
				data.hapus(soul);
				Jelmaan.hapus(soul);
				if (owner != null) {
					Tahap.hapusCermin(srv, owner, List.of(soul));
				}
			}
		}
		long hari = Kota.day(srv);
		if (hari != hariBersih) {
			hariBersih = hari;
			data.bersihkanStok(hari);
			Dompet.lupakanSemua();
			Set<UUID> seen = new HashSet<>(TERLIHAT);
			TERLIHAT.clear();
			PelaksanaJalan.bersihkan(seen::contains);
		}
	}

	private static boolean hidup(JalanData data, UUID owner, UUID soul, CompoundTag tag) {
		if (Tahap.rekamAway(owner, soul) != null || data.adaTiba(soul)) {
			return true;
		}
		if (!tag.getStringOr(Rencana.IKUT, "").isEmpty()) {
			return false;
		}
		for (UUID member : new Rencana(tag).rombongan()) {
			if (Tahap.rekamAway(owner, member) != null) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== each disciple's tick20

	private static void tick20(DiscipleEntity murid) {
		if (murid.role() != DiscipleEntity.Role.DISCIPLE || murid.ownerId() == null || !(murid.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer srv = level.getServer();
		long hari = Kota.day(srv);
		TERLIHAT.add(murid.soulId());
		Dompet.ingat(murid, hari);
		CompoundTag own = murid.data().extraSection(JalanIds.EXTRA);
		if (own.getLongOr("hariJalan", -1L) != hari && Irama.jam(level) >= 6) {
			own.putLong("hariJalan", hari);
			try {
				DaftarBelanja.pagi(murid, hari);
				Perlengkapan.hariBaru(murid, hari);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri morning list of {} failed", murid.getName().getString(), exception);
			}
		}
		JalanData data = JalanData.get(srv);
		CompoundTag arrival = data.tiba(murid.soulId());
		if (arrival == null) {
			return;
		}
		Jelmaan.hapus(murid.soulId());
		if (!arrival.getBooleanOr("adegan", false)) {
			Kepulangan.tiba(murid, arrival);
		} else if (MandiriApi.kegiatan(murid) != Kegiatan.TIBA && level.getGameTime() - arrival.getLongOr("adeganT", 0L) > 100L) {
			// the scene was lost (a restart, an order before it began): deliver at once
			Kepulangan.antar(murid, arrival, true);
			Kepulangan.selesai(murid, arrival);
		}
	}

	// ================================================================== C39.K5 volunteers

	/**
	 * Disciples of {@code owner} willing to keep a stall ({@code lapak}) or guard a caravan ({@code kafilah}), most eager
	 * first: free, allowed to shop and travel, the merchant path first for a stall, the sword and guard paths and the
	 * strong for a caravan; the lazy last, the timid never for a caravan.
	 */
	private static List<UUID> relawan(UUID owner, String jenis) {
		List<UUID> out = new ArrayList<>();
		if (owner == null) {
			return out;
		}
		boolean kafilah = "kafilah".equals(jenis);
		List<double[]> scored = new ArrayList<>();
		List<DiscipleEntity> loaded = Perencana.dimuat(owner);
		for (int i = 0; i < loaded.size(); i++) {
			DiscipleEntity d = loaded.get(i);
			if (!Rombongan.bebas(d)) {
				continue;
			}
			Kebijakan k = MandiriApi.kebijakan(d);
			if (!k.boleh(IzinKegiatan.BELANJA) || kafilah && !k.boleh(IzinKegiatan.BERKELANA)) {
				continue;
			}
			int sifat = Watak.sifat(d.data());
			if (kafilah && Watak.punya(sifat, Sifat.PENAKUT)) {
				continue;
			}
			JalanHidup jalan = MandiriApi.jalan(d.data());
			double s = 1.0D;
			if (kafilah) {
				s += jalan == JalanHidup.PENJAGA || jalan == JalanHidup.PEDANG ? 3.0D : jalan == JalanHidup.PETUALANG ? 2.0D : 0.0D;
				s += Watak.kekuatan(d.data());
				s += Watak.punya(sifat, Sifat.BERANI) ? 1.0D : 0.0D;
			} else {
				s += jalan == JalanHidup.PEDAGANG ? 4.0D : jalan == JalanHidup.ALKEMIS ? 1.0D : 0.0D;
				s += Watak.punya(sifat, Sifat.LICIK) ? 1.0D : 0.0D;
				s += Watak.punya(sifat, Sifat.RAJIN) ? 0.5D : 0.0D;
			}
			s -= Watak.punya(sifat, Sifat.MALAS) ? 1.5D : 0.0D;
			scored.add(new double[]{i, s});
		}
		scored.sort((a, b) -> Double.compare(b[1], a[1]));
		for (double[] one : scored) {
			out.add(loaded.get((int) one[0]).soulId());
		}
		return out;
	}

	private Perjalanan() {
	}
}

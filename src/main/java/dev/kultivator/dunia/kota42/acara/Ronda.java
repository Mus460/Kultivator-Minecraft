package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The night watch of every town (DESIGN 42.7.3): two watchmen per town, themed city and capital (one in a village), one
 * carrying the hand gong (prop 190), the other the bamboo clapper (prop 191), both a lantern, walking the town's
 * {@code ronda_<n>} titik (42W) in a loop half a round apart, else the main street nodes around the square. The watch has
 * five geng: 19:00, 21:00, 23:00, 01:00, 03:00 (tick 13000/15000/17000/19000/21000). At each start the gong strikes the
 * geng's number, the clapper rolls, and the watchman calls (voice {@code kota42.ronda_geng<n>}, the call heard up to 64
 * blocks). Between the calls they knock on doors and walk on.
 * <p>
 * Watchmen are residents when the place has them (the characters {@code lao_geng} and {@code xiao_bang} placed by 42B, or
 * residents of role {@code PENJAGA_MALAM}, moved by the schedule overriders here), else watchmen of their own walking while
 * a player is near (in the first capital they are Lao Geng and his apprentice Xiao Bang).
 * <p>
 * <b>Walking with the watch</b>: a player who stays within 12 blocks of a watchman for a whole geng (85 % of it) earns
 * Nama Baik +1 and 3 NB from the town treasury (less when it is poor), advancement {@code kota42_ronda} and the quest step
 * {@code ronda_geng}; {@link #ikut} (42B's dialog {@code [Ikut ronda]}) also shows the rhythm HUD so the player can strike
 * the geng with the watchman ({@code kota_irama}, kind {@code J_RONDA}; on time: +1 Nama Baik once a night).
 * {@link #ajar} runs the teaching rhythm of "Lentera Terakhir Lao Geng" (kind {@code J_AJAR}). {@link #sedangRonda} lets
 * the pickpocket chase of 42D-P triple the catch chance.
 */
public final class Ronda {

	/** Geng start ticks (time of day). */
	public static final long[] GENG_TICK = {13000L, 15000L, 17000L, 19000L, 21000L};
	/** Patrol pace (blocks per tick). */
	public static final double LAJU = 0.03D;
	/** A player counts as walking with the watch within this. */
	public static final double JARAK_IKUT = 12.0D;
	/** The call is heard this far. */
	public static final double JARAK_SERU = 64.0D;
	/** Nama Baik and NB for a full geng. */
	public static final int NAMA_BAIK = 1;
	public static final long UPAH_NB = 3L;
	/** Share of a geng's samples needed. */
	public static final double SYARAT_IKUT = 0.85D;

	private static final class Jaga {
		final long situs;
		final List<Vec3> rute = new ArrayList<>();
		double[] panjang = new double[0];
		final long hari;
		/** Staff watchmen (index = watchman slot). */
		final Map<Integer, KultivatorNpc> staf = new HashMap<>();
		int gengTerakhir = -1;

		Jaga(long situs, long hari) {
			this.situs = situs;
			this.hari = hari;
		}

		double total() {
			return panjang.length == 0 ? 0.0D : panjang[panjang.length - 1];
		}

		Vec3 titik(double s) {
			if (rute.size() < 2) {
				return rute.isEmpty() ? Vec3.ZERO : rute.getFirst();
			}
			double t = total();
			double x = Math.floorMod((long) (s * 1000.0D), (long) Math.max(1.0D, t * 1000.0D)) / 1000.0D;
			int seg = 1;
			while (seg < panjang.length - 1 && panjang[seg] < x) {
				seg++;
			}
			double len = Math.max(1.0E-4D, panjang[seg] - panjang[seg - 1]);
			return rute.get(seg - 1).lerp(rute.get(seg), Mth.clamp((x - panjang[seg - 1]) / len, 0.0D, 1.0D));
		}
	}

	/** A strike or call scheduled for a game tick. */
	private record Pukulan(long tick, long situs, int watchman, int jenis, int geng) {
	}

	private static final int P_GONG = 0;
	private static final int P_KENTONGAN = 1;
	private static final int P_SERU = 2;

	private static final Map<Long, Jaga> JAGA = new HashMap<>();
	private static final List<Pukulan> ANTRE = new ArrayList<>();
	/** Players walking with the watch this geng: player -> {situs, samples near, samples, geng, day}. */
	private static final Map<UUID, long[]> IKUT = new HashMap<>();
	/** Players who asked to join ({@link #ikut}): player -> place. */
	private static final Map<UUID, Long> DAFTAR = new HashMap<>();
	/** Rhythm rounds: player -> {kind, pattern, hits, start tick, round, rounds, misses}. */
	private static final Map<UUID, int[]> IRAMA = new HashMap<>();

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		WargaApi.daftarPenimpa(new Penimpa());
		ServerTickEvents.END_SERVER_TICK.register(Ronda::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Jaga j : JAGA.values()) {
				for (KultivatorNpc npc : j.staf.values()) {
					npc.vanish();
				}
			}
			JAGA.clear();
			ANTRE.clear();
			IKUT.clear();
			DAFTAR.clear();
			IRAMA.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			IKUT.remove(player.getUUID());
			DAFTAR.remove(player.getUUID());
			IRAMA.remove(player.getUUID());
		});
		KotaPayloads.setPenerimaIrama(Ronda::irama);
	}

	// ================================================================== route and watchmen

	/** How many watchmen walk in this place (village one, else two). */
	static int jumlahPenjaga(TempatHuni huni) {
		return huni.jenis.startsWith("kampung/") ? 1 : 2;
	}

	private static Jaga jaga(TempatHuni huni, long day) {
		Jaga j = JAGA.get(huni.situsId);
		if (j != null && j.hari == day && j.rute.size() >= 2) {
			return j;
		}
		Jaga baru = new Jaga(huni.situsId, day);
		if (j != null) {
			baru.staf.putAll(j.staf);
			baru.gengTerakhir = j.gengTerakhir;
		}
		List<Titik> nodes = new ArrayList<>();
		for (String type : huni.ringkasTitik().keySet()) {
			if (type.startsWith(KotaTitik.RONDA)) {
				nodes.addAll(huni.titik(type));
			}
		}
		BlockPos alun = KalenderKota.alun(huni);
		if (nodes.size() >= 3) {
			nodes.sort(Comparator.comparingInt(t -> KotaTitik.nomor(t.jenis())));
		} else {
			nodes = new ArrayList<>(huni.titik("jalan"));
			nodes.sort(Comparator.comparingDouble(t -> t.pos().distSqr(alun)));
			if (nodes.size() > 14) {
				nodes = new ArrayList<>(nodes.subList(0, 14));
			}
			nodes.sort(Comparator.comparingDouble(t -> Math.atan2(t.pos().getZ() - alun.getZ(), t.pos().getX() - alun.getX())));
		}
		if (nodes.size() >= 3) {
			for (Titik t : nodes) {
				baru.rute.add(Vec3.atBottomCenterOf(t.pos()));
			}
		} else {
			int r = huni.jenis.startsWith("kampung/") ? 12 : 20;
			for (int i = 0; i < 10; i++) {
				double a = Math.PI * 2.0D * i / 10.0D;
				baru.rute.add(Vec3.atBottomCenterOf(alun.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r))));
			}
		}
		baru.rute.add(baru.rute.getFirst());
		baru.panjang = new double[baru.rute.size()];
		for (int i = 1; i < baru.rute.size(); i++) {
			baru.panjang[i] = baru.panjang[i - 1] + baru.rute.get(i - 1).distanceTo(baru.rute.get(i));
		}
		JAGA.put(huni.situsId, baru);
		return baru;
	}

	/** Route distance walked tonight by watchman {@code k} (pure function of the clock). */
	private static double jarakMalam(MinecraftServer server, Jaga j, int k) {
		long t = Math.floorMod(dev.kultivator.dunia.kota.Kota.timeOfDay(server) - GENG_TICK[0], 24000L);
		return t * LAJU + k * j.total() / 2.0D;
	}

	/** True between 19:00 and 05:00. */
	static boolean malam(MinecraftServer server) {
		return KalenderKota.antara(KalenderKota.jamDesimal(server), 19.0D, 5.0D);
	}

	/** The residents walking the watch in this place, in watchman order (characters first). */
	static List<Warga> penjagaWarga(TempatHuni huni) {
		List<Warga> out = new ArrayList<>();
		for (String tokoh : new String[] {"lao_geng", "xiao_bang"}) {
			for (Warga w : WargaApi.wargaTempat(huni.situsId)) {
				if (tokoh.equals(w.tokoh) && w.hidup() && w.slot >= 0 && WargaApi.umur(w) < 90) {
					out.add(w);
				}
			}
		}
		for (Warga w : WargaApi.wargaTempat(huni.situsId)) {
			if (out.size() >= jumlahPenjaga(huni)) {
				break;
			}
			if (w.hidup() && w.slot >= 0 && w.tokoh.isEmpty() && w.peranNpc() == NpcRole.PENJAGA_MALAM) {
				out.add(w);
			}
		}
		return out.size() > jumlahPenjaga(huni) ? out.subList(0, jumlahPenjaga(huni)) : out;
	}

	/** The night activity of resident watchman {@code w} ({@code KalenderKota.JadwalTokoh} and the role overrider). */
	static @Nullable Kegiatan kegiatanTokoh(TempatHuni huni, Warga w, Jadwal.Saat s) {
		KotaConfig.Acara c = KotaConfig.acara();
		MinecraftServer server = KotaHidup.server();
		if (!c.aktif || !c.ronda || server == null || !KalenderKota.kota(huni)) {
			return null;
		}
		double h = s.jam() + s.menit() / 60.0D;
		if (KalenderKota.antara(h, 18.0D, 19.0D) && penjagaWarga(huni).indexOf(w) == 0) {
			// the head watchman walks the round with his pole and lights the street lanterns, the hour before the first watch
			Jaga j = jaga(huni, KalenderKota.hari(server));
			Vec3 at = j.titik((h - 18.0D) * j.total());
			return Kegiatan.di("ronda_lentera", Kegiatan.XZ + (int) Math.floor(at.x) + "," + (int) Math.floor(at.z), KotaGerakId.NYALAKAN_LENTERA, 0).berjalan(GerakId.JALAN_SANTAI);
		}
		if (KalenderKota.antara(h, 19.0D, 5.0D)) {
			List<Warga> list = penjagaWarga(huni);
			int k = list.indexOf(w);
			if (k < 0) {
				return null;
			}
			Jaga j = jaga(huni, KalenderKota.hari(server));
			Vec3 at = j.titik(jarakMalam(server, j, k) + 4.0D);
			boolean gong = k == 0;
			return new Kegiatan("ronda", Kegiatan.XZ + (int) Math.floor(at.x) + "," + (int) Math.floor(at.z), GerakId.BERDIRI_JAGA, 0,
					gong ? KotaGerakId.PROP_GONG_TANGAN : KotaGerakId.PROP_KENTONGAN, PropId.LENTERA_TANGAN, 0, GerakId.JALAN_SANTAI, false);
		}
		if ("lao_geng".equals(w.tokoh) && h >= 6.0D && h < 13.0D) {
			return Kegiatan.dalam("ronda_tidur");
		}
		if ("xiao_bang".equals(w.tokoh) && h >= 15.0D && h < 17.0D) {
			// the apprentice practises the patterns in the yard
			return Kegiatan.di("ronda_latihan", Kegiatan.RUMAH, KotaGerakId.PUKUL_KENTONGAN, KotaGerakId.PROP_KENTONGAN);
		}
		return null;
	}

	/** Order 130: residents of role {@code PENJAGA_MALAM} without a character walk the watch at night. */
	static final class Penimpa implements PenimpaJadwalKota {
		@Override
		public int urutan() {
			return 130;
		}

		@Override
		public @Nullable Kegiatan timpa(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
			if (!w.tokoh.isEmpty() || w.peranNpc() != NpcRole.PENJAGA_MALAM) {
				return null;
			}
			return kegiatanTokoh(huni, w, Jadwal.saat(p, huni.jenis, waktu));
		}
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		// scheduled strikes and calls
		if (!ANTRE.isEmpty()) {
			Iterator<Pukulan> it = ANTRE.iterator();
			while (it.hasNext()) {
				Pukulan p = it.next();
				if (p.tick() <= now) {
					it.remove();
					jalankan(level, p);
				}
			}
		}
		if (server.getTickCount() % 20L != 17L) {
			return;
		}
		KotaConfig.Acara c = KotaConfig.acara();
		Set<Long> live = new HashSet<>();
		boolean night = malam(server);
		long day = KalenderKota.hari(server);
		if (c.aktif && c.ronda && night) {
			long tod = dev.kultivator.dunia.kota.Kota.timeOfDay(server);
			int geng = geng(tod);
			for (TempatHuni huni : KalenderKota.aktif(level)) {
				if (!KalenderKota.kota(huni)) {
					continue;
				}
				live.add(huni.situsId);
				Jaga j = jaga(huni, day);
				List<LivingWatch> watch = penjaga(level, huni, j);
				if (geng >= 0 && j.gengTerakhir != geng && Math.floorMod(tod - GENG_TICK[geng], 24000L) < 200L) {
					j.gengTerakhir = geng;
					mulaiGeng(level, huni, j, watch, geng, now);
				}
				ikutSampel(level, huni, watch);
			}
		}
		Iterator<Map.Entry<Long, Jaga>> it = JAGA.entrySet().iterator();
		while (it.hasNext()) {
			Jaga j = it.next().getValue();
			if (!live.contains(j.situs)) {
				for (KultivatorNpc npc : j.staf.values()) {
					npc.vanish();
				}
				j.staf.clear();
				if (!night) {
					it.remove();
				}
			}
		}
		if (!night) {
			IKUT.clear();
			DAFTAR.clear();
		}
	}

	/** The geng whose 2000 ticks contain time of day {@code tod}, or -1 (day). */
	static int geng(long tod) {
		for (int i = GENG_TICK.length - 1; i >= 0; i--) {
			long d = Math.floorMod(tod - GENG_TICK[i], 24000L);
			if (d < 2000L) {
				return i;
			}
		}
		return -1;
	}

	/** One watchman body with its slot. */
	private record LivingWatch(KultivatorNpc npc, int slot, boolean gong) {
	}

	/** Resident watchmen's bodies, else watchmen of our own walking the loop while a player is near. */
	private static List<LivingWatch> penjaga(ServerLevel level, TempatHuni huni, Jaga j) {
		List<LivingWatch> out = new ArrayList<>();
		List<Warga> residents = penjagaWarga(huni);
		int need = jumlahPenjaga(huni);
		for (int k = 0; k < need; k++) {
			if (k < residents.size()) {
				KultivatorNpc body = huni.tubuhDari(residents.get(k).slot);
				KultivatorNpc staff = j.staf.remove(k);
				if (staff != null) {
					staff.vanish();
				}
				if (body != null) {
					out.add(new LivingWatch(body, k, k == 0));
				}
				continue;
			}
			Vec3 at = j.titik(jarakMalam(level.getServer(), j, k));
			KultivatorNpc staff = j.staf.get(k);
			if (staff == null || staff.isRemoved()) {
				if (!KalenderKota.adaPemain(level, BlockPos.containing(at), KotaConfig.acara().jarakTampil)) {
					continue;
				}
				BlockPos spot = MusimKota.tanah(level, BlockPos.containing(at));
				staff = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.PENJAGA_MALAM, spot, 0.0F, (int) (huni.situsId * 19L + 4001L + k));
				if (staff == null) {
					continue;
				}
				staff.setFightsMonsters(true);
				namai(level, huni, staff, k);
				j.staf.put(k, staff);
			}
			// walk to the point a few blocks ahead on the loop
			Vec3 to = j.titik(jarakMalam(level.getServer(), j, k) + 3.0D);
			if (staff.position().distanceToSqr(to) > 16.0D * 16.0D) {
				staff.teleportTo(to.x, to.y, to.z);
			} else if (staff.position().distanceToSqr(to) > 1.0D) {
				staff.getNavigation().moveTo(to.x, to.y, to.z, 0.7D);
			}
			if (SosokApi.gerakSekarang(staff) == GerakId.TIDAK_ADA || SosokApi.gerakSekarang(staff) == GerakId.JALAN_SANTAI) {
				SosokApi.gerak(staff, GerakId.JALAN_SANTAI, 0, 0, k == 0 ? KotaGerakId.PROP_GONG_TANGAN : KotaGerakId.PROP_KENTONGAN,
						PropId.LENTERA_TANGAN, 0);
			}
			out.add(new LivingWatch(staff, k, k == 0));
			// now and then a knock on a door along the way
			if (level.getRandom().nextInt(45) == 0) {
				for (Titik door : huni.titik("pintu")) {
					if (door.pos().distSqr(staff.blockPosition()) < 9.0D) {
						HidupApi.klip(staff, KotaGerakId.KETUK_PINTU, 0, 40);
						break;
					}
				}
			}
		}
		return out;
	}

	private static void namai(ServerLevel level, TempatHuni huni, KultivatorNpc npc, int k) {
		long first = CeritaData.get(level.getServer()).firstCapital();
		boolean named = first == huni.situsId || first == 0L && KalenderKota.ibuKota(huni);
		if (!named) {
			return;
		}
		String id = k == 0 ? "lao_geng" : "xiao_bang";
		CeritaApi.tandaiTokoh(npc, id);
		npc.setPlate(Component.translatable("kultivator.kota.papan_nama", Component.translatable("kultivator.kota42.acara.tokoh." + id),
				Component.translatable(k == 0 ? "kultivator.kota42.acara.gelar.penjaga_malam" : "kultivator.kota42.acara.gelar.murid_ronda")));
		if (k == 0) {
			SosokApi.setUsia(npc, dev.kultivator.sosok.Sosok.TUA);
		}
	}

	/** A geng begins: the gong strikes {@code geng+1} times, the clapper rolls, then the call. */
	private static void mulaiGeng(ServerLevel level, TempatHuni huni, Jaga j, List<LivingWatch> watch, int geng, long now) {
		if (watch.isEmpty()) {
			return;
		}
		int n = geng + 1;
		LivingWatch gong = watch.getFirst();
		LivingWatch caller = watch.size() > 1 && geng == 3 ? watch.get(1) : gong;
		for (int i = 0; i < n; i++) {
			ANTRE.add(new Pukulan(now + i * 15L, huni.situsId, gong.npc().getId(), P_GONG, geng));
		}
		LivingWatch clap = watch.size() > 1 ? watch.get(1) : gong;
		for (int i = 0; i < 6; i++) {
			ANTRE.add(new Pukulan(now + n * 15L + 10L + i * 4L, huni.situsId, clap.npc().getId(), P_KENTONGAN, geng));
		}
		ANTRE.add(new Pukulan(now + n * 15L + 45L, huni.situsId, caller.npc().getId(), P_SERU, geng));
		// the rhythm HUD for the players who joined
		for (Map.Entry<UUID, Long> e : DAFTAR.entrySet()) {
			if (e.getValue() == huni.situsId && level.getServer().getPlayerList().getPlayer(e.getKey()) instanceof ServerPlayer p) {
				int pattern = (1 << Math.min(5, n)) - 1;
				mulaiIrama(p, KotaLembar.HudIrama.J_RONDA, pattern, 15, 1, 1, now);
			}
		}
	}

	private static void jalankan(ServerLevel level, Pukulan p) {
		if (!(level.getEntity(p.watchman()) instanceof KultivatorNpc npc) || npc.isRemoved()) {
			return;
		}
		switch (p.jenis()) {
			case P_GONG -> {
				HidupApi.klip(npc, KotaGerakId.PUKUL_GONG, KotaGerakId.PROP_GONG_TANGAN, 12);
				level.playSound(null, npc.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 3.0F, 0.5F);
			}
			case P_KENTONGAN -> {
				HidupApi.klip(npc, KotaGerakId.PUKUL_KENTONGAN, KotaGerakId.PROP_KENTONGAN, 6);
				level.playSound(null, npc.blockPosition(), SoundEvents.BAMBOO_WOOD_HIT, SoundSource.AMBIENT, 2.0F, 1.6F);
			}
			default -> {
				HidupApi.klip(npc, KotaGerakId.SERU_JAGA, PropId.LENTERA_TANGAN, 50);
				Component call = Component.translatable("kultivator.kota42.acara.ronda.seru." + (p.geng() + 1));
				npc.say(call, 100);
				String key = "kota42.ronda_geng" + (p.geng() + 1);
				if (VoiceEvents.exists(key)) {
					Voice.say(npc, key, null);
				}
				for (ServerPlayer pl : level.players()) {
					if (!pl.isSpectator() && pl.distanceToSqr(npc) <= JARAK_SERU * JARAK_SERU) {
						Msg.actionBar(pl, Component.translatable("kultivator.kota42.acara.ronda.dengar", p.geng() + 1, call).withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
	}

	// ================================================================== walking with the watch

	private static void ikutSampel(ServerLevel level, TempatHuni huni, List<LivingWatch> watch) {
		MinecraftServer server = level.getServer();
		long tod = dev.kultivator.dunia.kota.Kota.timeOfDay(server);
		int geng = geng(tod);
		long day = KalenderKota.hari(server);
		if (geng < 0) {
			return;
		}
		BlockPos centre = KalenderKota.alun(huni);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.blockPosition().distSqr(centre) > 160.0D * 160.0D) {
				continue;
			}
			boolean near = false;
			for (LivingWatch w : watch) {
				if (p.distanceToSqr(w.npc()) <= JARAK_IKUT * JARAK_IKUT) {
					near = true;
					break;
				}
			}
			long[] st = IKUT.get(p.getUUID());
			if (st == null || st[3] != geng || st[4] != day || st[0] != huni.situsId) {
				if (st != null && st[0] == huni.situsId) {
					selesaiGeng(p, huni, st);
				}
				if (!near) {
					IKUT.remove(p.getUUID());
					continue;
				}
				// a geng counts only when joined at its start (within the first fifth)
				long into = Math.floorMod(tod - GENG_TICK[geng], 24000L);
				if (into > 400L) {
					continue;
				}
				st = new long[] {huni.situsId, 0L, 0L, geng, day};
				IKUT.put(p.getUUID(), st);
				Msg.actionBar(p, Component.translatable("kultivator.kota42.acara.ronda.mulai_ikut", geng + 1).withStyle(ChatFormatting.YELLOW));
			}
			st[2]++;
			if (near) {
				st[1]++;
			}
			// the geng ends: 2000 ticks = 100 samples
			if (Math.floorMod(tod - GENG_TICK[geng], 24000L) >= 1980L) {
				selesaiGeng(p, huni, st);
				IKUT.remove(p.getUUID());
			}
		}
	}

	private static void selesaiGeng(ServerPlayer p, TempatHuni huni, long[] st) {
		if (st[2] < 80L || st[1] < st[2] * SYARAT_IKUT) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		KotaData.get(server).addReputation(huni.situsId, p.getUUID(), NAMA_BAIK);
		long paid = KasKota.ambil(server, huni.situsId, UPAH_NB);
		if (paid > 0L) {
			EkonomiApi.beri(p, paid, "ronda");
		}
		Msg.chat(p, Component.translatable(paid > 0L ? "kultivator.kota42.acara.ronda.upah" : "kultivator.kota42.acara.ronda.upah_kosong",
				st[3] + 1, NAMA_BAIK, paid).withStyle(ChatFormatting.GOLD));
		Advancements.grant(p, "tempat/kota42_ronda");
		dev.kultivator.dunia.kota42.bicara.TujuanKota.ronda(p, huni.situsId, (int) st[3] + 1);
	}

	/**
	 * 42B's dialog {@code [Ikut ronda]} at a watchman: the player joins the watch of the watchman's place tonight (the
	 * rhythm HUD at each geng). Returns null, or the reason it was refused (lang key): daytime, busy elsewhere.
	 */
	public static @Nullable String ikut(ServerPlayer player, KultivatorNpc watchman) {
		MinecraftServer server = player.level().getServer();
		if (!KotaConfig.acara().aktif || !KotaConfig.acara().ronda) {
			return "kultivator.kota42.acara.ronda.mati";
		}
		if (!malam(server)) {
			return "kultivator.kota42.acara.ronda.siang";
		}
		String busy = Sibuk.why(player);
		if (busy != null) {
			return busy;
		}
		DAFTAR.put(player.getUUID(), watchman.town());
		Msg.chat(player, Component.translatable("kultivator.kota42.acara.ronda.ikut").withStyle(ChatFormatting.YELLOW));
		return null;
	}

	/** True while {@code player} walks with a watch tonight (42D-P: pickpockets are caught three times as often). */
	public static boolean sedangRonda(ServerPlayer player) {
		return DAFTAR.containsKey(player.getUUID()) || IKUT.containsKey(player.getUUID());
	}

	/** "Lentera Terakhir Lao Geng" stage 2: Lao Geng teaches Xiao Bang the five patterns with the player (rhythm HUD). */
	public static void ajar(ServerPlayer player) {
		mulaiIrama(player, KotaLembar.HudIrama.J_AJAR, 0b1, 20, 1, 5, player.level().getGameTime());
	}

	// ================================================================== rhythm HUD (kota_irama)

	/** Starts a rhythm round for {@code player}: pattern bits over 5 beats, {@code tpb} ticks a beat. */
	static void mulaiIrama(ServerPlayer player, int jenis, int pattern, int tpb, int round, int rounds, long start) {
		IRAMA.put(player.getUUID(), new int[] {jenis, pattern, 0, (int) (start & 0x7fffffffL), round, rounds, 0, tpb});
		KotaPayloads.kirim(player, new KotaPayloads.Irama(KotaLembar.HudIrama.data(jenis, 0, pattern, tpb, round, rounds, 0, 0,
				(int) (start & 0xFFL))));
	}

	/** Hides the rhythm HUD. */
	static void hentiIrama(ServerPlayer player) {
		IRAMA.remove(player.getUUID());
		KotaPayloads.kirim(player, new KotaPayloads.Irama(KotaLembar.HudIrama.data(KotaLembar.HudIrama.J_KOSONG)));
	}

	/** The single rhythm receiver of 42 (C2S beats): night watch, teaching, and the shadow play's gong. */
	private static void irama(ServerPlayer player, KotaPayloads.Irama payload) {
		byte[] d = payload.data();
		int jenis = KotaLembar.HudIrama.baca(d, 0);
		if (jenis == KotaLembar.HudIrama.J_WAYANG) {
			TeaterBayang.irama(player, d);
			return;
		}
		int[] st = IRAMA.get(player.getUUID());
		if (st == null || st[0] != jenis) {
			return;
		}
		int beat = KotaLembar.HudIrama.baca(d, 1);
		int offset = KotaLembar.HudIrama.baca(d, 2) - 128;
		if (beat < 0 || beat >= KotaLembar.HudIrama.KETUKAN) {
			return;
		}
		boolean wanted = (st[1] >> beat & 1) != 0;
		if (wanted && Math.abs(offset) <= 4) {
			st[2] |= 1 << beat;
		} else {
			st[6]++;
		}
		if ((st[2] & st[1]) == st[1]) {
			// the round is struck right
			if (st[4] < st[5]) {
				int next = (1 << Math.min(5, st[4] + 1)) - 1;
				mulaiIrama(player, jenis, next, st[7], st[4] + 1, st[5], player.level().getGameTime());
				return;
			}
			hentiIrama(player);
			if (jenis == KotaLembar.HudIrama.J_RONDA) {
				if (KalenderKota.Berkah.sekaliSehari(player, "ronda_irama")) {
					Long situs = DAFTAR.get(player.getUUID());
					if (situs != null) {
						KotaData.get(player.level().getServer()).addReputation(situs, player.getUUID(), 1);
					}
					Msg.actionBar(player, Component.translatable("kultivator.kota42.acara.ronda.irama_pas").withStyle(ChatFormatting.GOLD));
				}
			} else if (jenis == KotaLembar.HudIrama.J_AJAR) {
				Msg.chat(player, Component.translatable("kultivator.kota42.acara.ronda.ajar_selesai").withStyle(ChatFormatting.GOLD));
				CeritaApi.peristiwa(player, "ronda_ajar", "xiao_bang");
			}
		} else if (st[6] > 6) {
			hentiIrama(player);
			Msg.actionBar(player, Component.translatable("kultivator.kota42.acara.ronda.irama_gagal").withStyle(ChatFormatting.RED));
		} else {
			KotaPayloads.kirim(player, new KotaPayloads.Irama(KotaLembar.HudIrama.data(jenis, beat, st[1], st[7], st[4], st[5], st[2],
					st[6], st[3] & 0xFF)));
		}
	}

	// ================================================================== test

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("ronda").executes(ctx -> ujiRonda(ctx, -1))
				.then(Commands.literal("geng").then(Commands.argument("n", IntegerArgumentType.integer(1, 5))
						.executes(ctx -> ujiRonda(ctx, IntegerArgumentType.getInteger(ctx, "n") - 1))));
	}

	private static int ujiRonda(CommandContext<CommandSourceStack> ctx, int geng) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		TempatHuni huni = Pemanggil.huni(info.situsId());
		if (huni == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tidak_aktif"));
			return 0;
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		Jaga j = jaga(huni, KalenderKota.hari(ctx.getSource().getServer()));
		List<LivingWatch> watch = new ArrayList<>();
		// a test geng calls even by day: watchmen of our own appear at the route
		for (int k = 0; k < jumlahPenjaga(huni); k++) {
			KultivatorNpc staff = j.staf.get(k);
			if (staff == null || staff.isRemoved()) {
				Vec3 at = j.titik(k * j.total() / 2.0D);
				staff = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.PENJAGA_MALAM, MusimKota.tanah(level, BlockPos.containing(at)), 0.0F,
						(int) (huni.situsId * 19L + 4001L + k));
				if (staff != null) {
					namai(level, huni, staff, k);
					j.staf.put(k, staff);
				}
			}
			if (staff != null) {
				watch.add(new LivingWatch(staff, k, k == 0));
			}
		}
		int n = geng >= 0 ? geng : Math.max(0, geng(dev.kultivator.dunia.kota.Kota.timeOfDay(ctx.getSource().getServer())));
		mulaiGeng(level, huni, j, watch, n, level.getGameTime());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.ronda", n + 1, watch.size(), j.rute.size() - 1,
				String.format(java.util.Locale.ROOT, "%.0f", j.total())), false);
		return 1;
	}

	private Ronda() {
	}
}

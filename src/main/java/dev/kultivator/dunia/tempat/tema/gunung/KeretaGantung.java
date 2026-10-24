package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Kereta Gantung Tali Besi of every Kota Gunung (DESIGN 38.T.6.4): one wooden cabin for 6 riding a two-span line from
 * the lower station (kaki_gerbang) over the guide wheel of the Tiang Tengah (aula_herba) to the upper station
 * (puncak_emas). The cabin is a {@code noSave} entity ({@link KeretaGantungEntity}); its position is a pure function of the
 * trip time (smoothstep over the whole line, parabolic sag per span; no path finding, 38.T.2.7); the server moves it every
 * tick, the client interpolates and draws the rope from the line points ({@link TemaNet#SPLINE} lane kind {@code tali}).
 * <p>
 * Service 05:00-18:00 (time of day 23000..12000), one way 40 s (60 s in rain), a trip leaves 5 s after the first rider
 * boards; fare 1 NB up, free down, free for life for the Penjaga Tali Awan; thunder stops the cabin where it is until it
 * passes. Riders are held in {@code tema.lift} ({@link TemaSibuk}) and can never fall out: leaving mid-line puts them
 * back, except during a Lift Macet stop (quest stage 1, test command), when the keeper lowers a rope
 * ({@link TaliTurunEntity}). Riders sit on the cabin (vanilla passengers) when the entity type accepts passengers;
 * otherwise they are carried (moved with the cabin every tick).
 */
public final class KeretaGantung {

	public static final int PERJALANAN = 800;
	public static final int PERJALANAN_HUJAN = 1200;
	public static final int TUNGGU_PINTU = 100;
	public static final int MACET_TICK = 400;
	public static final long ONGKOS_NAIK = 1L;
	/** Service window (time of day): 05:00 .. 18:00. */
	public static final int BUKA = 23000;
	public static final int TUTUP = 12000;

	public static final int DI_BAWAH = 0;
	public static final int NAIK = 1;
	public static final int DI_ATAS = 2;
	public static final int TURUN = 3;

	/** One line (one town). */
	static final class Jalur {
		final long situsId;
		final TemaKota.Kota kota;
		final Vec3 p0;
		final Vec3 p1;
		final Vec3 p2;
		final double l1;
		final double l2;
		@Nullable KeretaGantungEntity kabin;
		@Nullable TaliTurunEntity tali;
		int fase = DI_BAWAH;
		int jalan;            // ticks of travel done (frozen while stopped)
		int pintu;            // door timer at a station
		int macetSisa;        // ticks left of a Lift Macet stop
		boolean macetTrip;    // this trip will stop mid-line
		boolean macetSelesai; // the stop of this trip is over
		@Nullable UUID macetPemain;
		final Set<UUID> bawa = new LinkedHashSet<>();
		final Set<UUID> penumpang = new LinkedHashSet<>();

		Jalur(TemaKota.Kota kota) {
			this.situsId = kota.situsId();
			this.kota = kota;
			this.p0 = GunungTata.dunia(kota, GunungTata.KABIN_BAWAH);
			this.p1 = GunungTata.dunia(kota, GunungTata.KABIN_TIANG);
			this.p2 = GunungTata.dunia(kota, GunungTata.KABIN_ATAS);
			this.l1 = p0.distanceTo(p1);
			this.l2 = p1.distanceTo(p2);
		}

		double panjang() {
			return l1 + l2;
		}

		/** Position at distance s (0 .. panjang) from the lower station. */
		Vec3 di(double s) {
			s = Mth.clamp(s, 0.0D, panjang());
			if (s <= l1) {
				double u = l1 <= 0.0D ? 0.0D : s / l1;
				Vec3 p = p0.lerp(p1, u);
				return p.subtract(0.0D, GunungTata.SAG_PENDEK * 4.0D * u * (1.0D - u), 0.0D);
			}
			double u = l2 <= 0.0D ? 1.0D : (s - l1) / l2;
			Vec3 p = p1.lerp(p2, u);
			return p.subtract(0.0D, GunungTata.SAG_PANJANG * 4.0D * u * (1.0D - u), 0.0D);
		}

		float arah(double s) {
			Vec3 a = di(Math.max(0.0D, s - 0.5D));
			Vec3 b = di(Math.min(panjang(), s + 0.5D));
			return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90.0F;
		}

		/** Progress 0..1 from the lower station for travel tick {@code t} of {@code total}. */
		double maju(int t, int total) {
			double u = Mth.clamp(t / (double) total, 0.0D, 1.0D);
			double e = u * u * (3.0D - 2.0D * u);
			return fase == TURUN ? 1.0D - e : e;
		}
	}

	private static final Map<Long, Jalur> JALUR = new HashMap<>();
	/** "uuid|situs" pairs that got the rope lane this session. */
	private static final Set<String> TALI_DIKIRIM = new HashSet<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(KeretaGantung::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	static void reset() {
		JALUR.clear();
		TALI_DIKIRIM.clear();
	}

	// ================================================================================================ anchors

	/** A station anchor with players near: the line exists, its cabin is made, the rope lane is sent once. */
	static void dekatStasiun(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TemaConfig.gunung().keretaGantung) {
			return;
		}
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		if (kota == null) {
			return;
		}
		Jalur j = JALUR.computeIfAbsent(kota.situsId(), id -> new Jalur(kota));
		if (j.kabin == null || j.kabin.isRemoved()) {
			buatKabin(level, j, anchor);
		}
		for (ServerPlayer p : within) {
			if (TALI_DIKIRIM.add(p.getUUID() + "|" + kota.situsId())) {
				kirimTali(p, j);
			}
		}
	}

	static void stasiunLepas(ServerLevel level, DuniaAnchorEntity anchor) {
		// the cabin is kept while the other station or the riders are loaded; TemaMekanisme removes it when nobody is near
	}

	private static void buatKabin(ServerLevel level, Jalur j, DuniaAnchorEntity anchor) {
		boolean atas = j.fase == DI_ATAS || j.fase == NAIK;
		if (j.fase == NAIK || j.fase == TURUN) {
			j.fase = atas ? DI_ATAS : DI_BAWAH;      // a cabin re-made mid-line waits at the nearer station
		}
		Vec3 at = atas ? j.p2 : j.p0;
		KeretaGantungEntity e = TemaMekanisme.muncul(level, TemaEntities.KERETA_GANTUNG, at, j.arah(atas ? j.panjang() : 0.0D), j.kota,
				anchor.blockPosition());
		if (e != null) {
			e.setNilai(atas ? 1.0F : 0.0F);
			e.setKeadaan(KeretaGantungEntity.KEADAAN_STASIUN);
			j.kabin = e;
			j.pintu = 0;
		}
	}

	/** The rope polyline for the client renderer (TemaNet SPLINE, one lane {@code tali_gantung} of kind {@code tali}). */
	private static void kirimTali(ServerPlayer player, Jalur j) {
		CompoundTag data = new CompoundTag();
		ListTag lanes = new ListTag();
		CompoundTag lane = new CompoundTag();
		lane.putString("id", "tali_gantung");
		lane.putString("kind", "tali");
		int n = 24;
		int[] pts = new int[(n + 1) * 3];
		for (int i = 0; i <= n; i++) {
			Vec3 p = j.di(j.panjang() * i / n).add(0.0D, GunungTata.TALI_DI_ATAS, 0.0D);
			pts[i * 3] = Mth.floor(p.x);
			pts[i * 3 + 1] = Mth.floor(p.y);
			pts[i * 3 + 2] = Mth.floor(p.z);
		}
		lane.put("p", new IntArrayTag(pts));
		lanes.add(lane);
		data.put("jalur", lanes);
		TemaNet.kirim(player, TemaNet.SPLINE, j.situsId, data);
	}

	// ================================================================================================ riders

	/** A player clicks the cabin (or the lift screen's "naik"): boards when the cabin waits at a station. */
	static boolean naik(ServerPlayer player, KeretaGantungEntity kabin) {
		Jalur j = jalurDari(kabin);
		if (j == null) {
			return false;
		}
		if (j.fase != DI_BAWAH && j.fase != DI_ATAS) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.lift.berjalan").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		if (!bukaJam(level)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.lift.tutup").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (j.penumpang.contains(player.getUUID())) {
			return true;
		}
		if (j.penumpang.size() >= 6) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.lift.penuh").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		boolean keAtas = j.fase == DI_BAWAH;
		if (keAtas && !GunungData.liftGratis(player) && !EkonomiApi.bayar(player, ONGKOS_NAIK, "kereta_gantung")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.lift.uang").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.startRiding(kabin)) {
			j.bawa.add(player.getUUID());
		}
		j.penumpang.add(player.getUUID());
		j.pintu = Math.min(j.pintu, TUNGGU_PINTU / 2);
		TemaSibuk.tahan(player, TemaSibuk.LIFT, PERJALANAN_HUJAN + MACET_TICK + 400);
		Ziarah.batal(player, Ziarah.BATAL_KERETA);
		level.playSound(null, kabin.getX(), kabin.getY(), kabin.getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.9F);
		Msg.actionBar(player, Component.translatable(keAtas ? "kultivator.kota_tema.gunung.lift.naik" : "kultivator.kota_tema.gunung.lift.turun")
				.withStyle(ChatFormatting.GOLD));
		CompoundTag pd = GunungData.pemain(player);
		if (keAtas && !pd.getBooleanOr("lm", false) && !QuestTracker.aktif(player, GunungCerita.QUEST_TALI)
				&& QuestTracker.tahap(player, GunungCerita.QUEST_TALI) <= 0 && j.macetPemain == null) {
			j.macetTrip = true;
			j.macetPemain = player.getUUID();
			pd.putBoolean("lm", true);
			GunungData.ubah(player.level().getServer());
		}
		return true;
	}

	private static @Nullable Jalur jalurDari(KeretaGantungEntity kabin) {
		for (Jalur j : JALUR.values()) {
			if (j.kabin == kabin) {
				return j;
			}
		}
		return null;
	}

	/** Where a rider gets off: the platform beside the cabin at a station, else the cabin itself. */
	static Vec3 turunDi(KeretaGantungEntity kabin) {
		Jalur j = jalurDari(kabin);
		if (j == null) {
			return kabin.position();
		}
		if (j.fase == DI_BAWAH) {
			return GunungTata.dunia(j.kota, "kaki_gerbang", 24.5D, 19.0D, 28.5D);
		}
		if (j.fase == DI_ATAS) {
			return GunungTata.dunia(j.kota, "puncak_emas", 24.5D, 13.0D, 87.5D);
		}
		return kabin.position();
	}

	private static boolean bukaJam(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= BUKA || t < TUTUP;
	}

	// ================================================================================================ the tick

	private static void tick(MinecraftServer server) {
		if (JALUR.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		boolean langkah = server.getTickCount() % 20 == 3;
		for (Iterator<Jalur> it = JALUR.values().iterator(); it.hasNext(); ) {
			Jalur j = it.next();
			if (j.kabin == null || j.kabin.isRemoved()) {
				if (langkah && !TemaMekanisme.pemainDekat(level, j.p0, 160) && !TemaMekanisme.pemainDekat(level, j.p2, 160)) {
					lepasSemua(level, j);
					it.remove();
				}
				continue;
			}
			bersihkan(level, j);
			switch (j.fase) {
				case DI_BAWAH, DI_ATAS -> diStasiun(level, j);
				default -> berjalan(level, j);
			}
			bawaPenumpang(level, j);
		}
	}

	private static void bersihkan(ServerLevel level, Jalur j) {
		for (Iterator<UUID> it = j.penumpang.iterator(); it.hasNext(); ) {
			UUID id = it.next();
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null || p.level() != level || p.isSpectator()) {
				it.remove();
				j.bawa.remove(id);
				continue;
			}
			boolean naik = p.getVehicle() == j.kabin || j.bawa.contains(id);
			if (!naik) {
				if (j.fase == NAIK || j.fase == TURUN) {
					if (j.macetSisa > 0) {
						// leaving during a Lift Macet stop: allowed, the keeper's rope and a slow fall
						it.remove();
						TemaSibuk.lepas(p, TemaSibuk.LIFT);
						GunungCerita.keluarSaatMacet(p);
					} else if (p.position().distanceToSqr(j.kabin.position()) < 36.0D) {
						if (!p.startRiding(j.kabin)) {
							j.bawa.add(id);
						}
						Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.lift.terkunci").withStyle(ChatFormatting.GRAY));
					} else {
						it.remove();
						TemaSibuk.lepas(p, TemaSibuk.LIFT);
					}
				} else {
					it.remove();
					TemaSibuk.lepas(p, TemaSibuk.LIFT);
				}
			}
		}
	}

	private static void diStasiun(ServerLevel level, Jalur j) {
		KeretaGantungEntity kabin = j.kabin;
		kabin.setKeadaan(KeretaGantungEntity.KEADAAN_STASIUN);
		putarRoda(j, 0);
		if (j.penumpang.isEmpty()) {
			j.pintu = 0;
			return;
		}
		if (level.isThundering() || !bukaJam(level)) {
			if (level.getServer().getTickCount() % 100 == 0) {
				for (UUID id : j.penumpang) {
					ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
					if (p != null) {
						Msg.actionBar(p, Component.translatable(level.isThundering() ? "kultivator.kota_tema.gunung.lift.petir"
								: "kultivator.kota_tema.gunung.lift.tutup").withStyle(ChatFormatting.YELLOW));
					}
				}
			}
			return;
		}
		j.pintu++;
		if (j.pintu == TUNGGU_PINTU - 10) {
			kabin.setKeadaan(KeretaGantungEntity.KEADAAN_PINTU);
		}
		if (j.pintu >= TUNGGU_PINTU) {
			j.fase = j.fase == DI_BAWAH ? NAIK : TURUN;
			j.jalan = 0;
			j.pintu = 0;
			j.macetSelesai = false;
			kabin.setKeadaan(KeretaGantungEntity.KEADAAN_JALAN);
			level.playSound(null, kabin.getX(), kabin.getY(), kabin.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
			for (Entity w : GunungAnchors.dimuat(GunungAnchors.RODA)) {
				level.playSound(null, w.getX(), w.getY(), w.getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.5F);
			}
		}
	}

	private static void berjalan(ServerLevel level, Jalur j) {
		KeretaGantungEntity kabin = j.kabin;
		int total = level.isRaining() ? PERJALANAN_HUJAN : PERJALANAN;
		if (j.macetSisa > 0) {
			j.macetSisa--;
			kabin.setKeadaan(KeretaGantungEntity.KEADAAN_MACET);
			putarRoda(j, 0);
			if (j.macetSisa == 0) {
				akhirMacet(level, j);
			}
			return;
		}
		if (level.isThundering()) {
			kabin.setKeadaan(KeretaGantungEntity.KEADAAN_MACET);
			putarRoda(j, 0);
			return;
		}
		kabin.setKeadaan(KeretaGantungEntity.KEADAAN_JALAN);
		j.jalan++;
		double u = j.maju(j.jalan, total);
		double s = u * j.panjang();
		Vec3 at = j.di(s);
		kabin.pindah(at, j.arah(s));
		kabin.setNilai((float) u);
		putarRoda(j, j.fase == NAIK ? 1 : -1);
		// the Lift Macet stop in the middle of the long span (going up only)
		if (j.macetTrip && !j.macetSelesai && j.fase == NAIK && s >= j.l1 + j.l2 * 0.5D) {
			mulaiMacet(level, j);
			return;
		}
		if (j.jalan >= total) {
			tiba(level, j);
		}
	}

	private static void putarRoda(Jalur j, int arah) {
		for (DuniaAnchorEntity a : GunungAnchors.dimuat(GunungAnchors.RODA)) {
			if (!a.data().getStringOr("katrol", "").isEmpty()) {
				continue;
			}
			TemaKota.Kota kota = GunungAnchors.kota(a);
			if (kota == null || kota.situsId() != j.situsId) {
				continue;
			}
			if (GunungAnchors.tubuh(a) instanceof RodaKatrolEntity roda) {
				roda.setKeadaan(arah == 0 ? RodaKatrolEntity.KEADAAN_DIAM : RodaKatrolEntity.KEADAAN_PUTAR);
				roda.setNilai(arah);
			}
		}
	}

	private static void tiba(ServerLevel level, Jalur j) {
		boolean atas = j.fase == NAIK;
		j.fase = atas ? DI_ATAS : DI_BAWAH;
		j.jalan = 0;
		j.pintu = 0;
		j.macetTrip = false;
		j.macetPemain = null;
		KeretaGantungEntity kabin = j.kabin;
		kabin.pindah(atas ? j.p2 : j.p0, j.arah(atas ? j.panjang() : 0.0D));
		kabin.setNilai(atas ? 1.0F : 0.0F);
		kabin.setKeadaan(KeretaGantungEntity.KEADAAN_PINTU);
		level.playSound(null, kabin.getX(), kabin.getY(), kabin.getZ(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.9F);
		Vec3 keluar = turunDi(kabin);
		for (UUID id : List.copyOf(j.penumpang)) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			if (p.getVehicle() == kabin) {
				p.stopRiding();
			}
			p.teleportTo(keluar.x, keluar.y, keluar.z);
			p.resetFallDistance();
			TemaSibuk.lepas(p, TemaSibuk.LIFT);
			dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.lapor(p, dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.NAIK_LIFT, "gantung:" + GunungData.TEMA);
			CeritaApi.peristiwa(p, "tanda", atas ? "gunung_tiba_puncak" : "gunung_tiba_kaki");
			Msg.actionBar(p, Component.translatable(atas ? "kultivator.kota_tema.gunung.lift.tiba_atas" : "kultivator.kota_tema.gunung.lift.tiba_bawah")
					.withStyle(ChatFormatting.GOLD));
		}
		j.penumpang.clear();
		j.bawa.clear();
	}

	private static void mulaiMacet(ServerLevel level, Jalur j) {
		j.macetSisa = MACET_TICK;
		j.macetSelesai = true;
		KeretaGantungEntity kabin = j.kabin;
		kabin.setKeadaan(KeretaGantungEntity.KEADAAN_MACET);
		level.playSound(null, kabin.getX(), kabin.getY(), kabin.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
		// the keeper lowers a rope from the cabin to the ground
		double tanah = kabin.getY();
		for (int dy = 1; dy < 64; dy++) {
			if (!level.getBlockState(kabin.blockPosition().below(dy)).isAir()) {
				tanah = kabin.getY() - dy + 1;
				break;
			}
		}
		TaliTurunEntity tali = TemaMekanisme.muncul(level, TemaEntities.TALI_TURUN, kabin.position(), kabin.getYRot(), j.kota, null);
		if (tali != null) {
			tali.setNilai((float) Math.max(1.0D, kabin.getY() - tanah));
			tali.setKeadaan(TaliTurunEntity.KEADAAN_TURUN);
			j.tali = tali;
		}
		for (UUID id : j.penumpang) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			Msg.title(p, Component.translatable("kultivator.kota_tema.gunung.lift.macet").withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.kota_tema.gunung.lift.macet.sub"), 10, 60, 20);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.lift.macet.tie_suo").withStyle(ChatFormatting.YELLOW));
			if (id.equals(j.macetPemain)) {
				GunungCerita.liftMacet(p, j.situsId);
			}
		}
	}

	private static void akhirMacet(ServerLevel level, Jalur j) {
		if (j.tali != null) {
			j.tali.setKeadaan(TaliTurunEntity.KEADAAN_DITARIK);
			TemaMekanisme.lepas(j.tali);
			j.tali = null;
		}
		for (UUID id : j.penumpang) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.lift.lanjut").withStyle(ChatFormatting.GREEN));
			}
		}
	}

	/** Carried riders (the type refused vanilla passengers): moved with the cabin every tick, camera untouched. */
	private static void bawaPenumpang(ServerLevel level, Jalur j) {
		if (j.bawa.isEmpty() || j.kabin == null) {
			return;
		}
		int i = 0;
		for (UUID id : j.bawa) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			Vec3 seat = j.kabin.position().add(KeretaGantungEntity.kursiOffset(i++, j.kabin.getYRot()));
			if (p.position().distanceToSqr(seat) > 0.02D) {
				p.connection.teleport(new PositionMoveRotation(seat, Vec3.ZERO, 0.0F, 0.0F), EnumSet.of(Relative.Y_ROT, Relative.X_ROT));
			}
			p.resetFallDistance();
		}
	}

	private static void lepasSemua(ServerLevel level, Jalur j) {
		for (UUID id : j.penumpang) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				TemaSibuk.lepas(p, TemaSibuk.LIFT);
			}
		}
		if (j.tali != null) {
			TemaMekanisme.lepas(j.tali);
		}
	}

	// ================================================================================================ control & info

	/** Forces a Lift Macet stop on the next upward trip of town {@code situsId} (test command, calendar event). */
	public static boolean macetkan(long situsId) {
		Jalur j = JALUR.get(situsId);
		if (j == null) {
			return false;
		}
		j.macetTrip = true;
		j.macetSelesai = false;
		if ((j.fase == NAIK || j.fase == TURUN) && j.macetSisa == 0 && j.kabin != null && j.kabin.level() instanceof ServerLevel level) {
			mulaiMacet(level, j);
		}
		return true;
	}

	/** Sends the cabin of town {@code situsId} to the other station now (test command). */
	public static boolean jalankan(long situsId) {
		Jalur j = JALUR.get(situsId);
		if (j == null || j.kabin == null) {
			return false;
		}
		if (j.fase == DI_BAWAH || j.fase == DI_ATAS) {
			j.pintu = TUNGGU_PINTU;
			if (j.penumpang.isEmpty() && j.kabin.level() instanceof ServerLevel level) {
				j.fase = j.fase == DI_BAWAH ? NAIK : TURUN;
				j.jalan = 0;
				level.playSound(null, j.kabin.getX(), j.kabin.getY(), j.kabin.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
			}
		}
		return true;
	}

	/** A snapshot for the lift screen and the test command. */
	public record Info(int fase, float kabin, float tiang, boolean macet, int penumpang, int kursi, boolean ada) {
	}

	public static Info info(long situsId) {
		Jalur j = JALUR.get(situsId);
		if (j == null) {
			return new Info(DI_BAWAH, 0.0F, 0.0F, false, 0, 6, false);
		}
		float tiang = (float) (j.l1 / Math.max(1.0D, j.panjang()));
		float pos = j.kabin == null ? 0.0F : j.kabin.nilai();
		return new Info(j.fase, pos, tiang, j.macetSisa > 0, j.penumpang.size(), 6, j.kabin != null && !j.kabin.isRemoved());
	}

	/** The line of a town (null before a station anchor loaded). */
	static @Nullable Jalur jalur(long situsId) {
		return JALUR.get(situsId);
	}

	/** True when time of day is inside the service window. */
	public static boolean buka(ServerLevel level) {
		return bukaJam(level);
	}

	private KeretaGantung() {
	}
}

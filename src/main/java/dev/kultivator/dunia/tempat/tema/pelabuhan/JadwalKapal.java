package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The scheduled merchant junks of the Dermaga Agung (DESIGN 38.T.3.4) and the Kapal Lintas Laut at its own quay.
 * <p>
 * <b>Schedule (a pure function of the overworld clock, so a restart in the middle of a mooring finds the ship at its
 * berth again).</b> Each of the three moorings {@code n = 0..2} runs a 3-day cycle shifted by {@code n} days:
 * {@code u = floorMod(t - 2000 - 24000 n, 72000)}, cycle number {@code c = floorDiv(...)}. The ship appears 160 blocks
 * out at sea at {@code u = d} ({@code d} = 0..1999 by hash, so between 2000 and 4000 on its arrival day), sails in along
 * the mooring's keel line for 90 s ({@link #LAYAR_MASUK} ticks, slowing down), furls its battened sails panel by panel
 * (40 ticks), drops anchor (30) and lowers the gangway; it stays moored until day 2 at 14000, then weighs anchor (30),
 * unfurls (40), backs out 40 blocks stern first (400), turns about (200) and sails away, fading from 120 blocks
 * ({@link KapalDagangEntity#KEADAAN_MEMUDAR}, 1000 ticks). Without a player within {@link #JARAK} blocks of the mooring
 * no body exists: the cycle runs as data only (the cargo of the cycle is still sold out and renewed).
 * <p>
 * <b>Storms</b> ({@code isThundering}, read only): a ship that has not started its approach waits at sea until the storm
 * ends and then arrives; a moored ship does not leave while it thunders. A lost pirate raid cancels the next cycle of
 * mooring 0 ({@link #batalkan}).
 * <p>
 * <b>Bodies.</b> A ship is a {@code noSave} {@link KapalDagangEntity} made when the mooring anchor's handler sees a player
 * and driven every tick from here (positions computed from the clock, {@code pindah}); it is NOT kept by
 * {@code TemaMekanisme} (whose keeper drops bodies more than 128 blocks from every player, which a ship far out at sea
 * is), but it counts toward {@code DuniaBudget} kind {@code mekanisme_tema}. While moored a NAKHODA_LAUT body stands on
 * the pier at the gangway and sells the cargo ({@code tema_kapal}); the treadwheel cranes run their 200-tick cycle and
 * porters carry crates. The live map pointer {@code tema/kapal_dagang} shows a moored ship until it sails.
 */
public final class JadwalKapal {

	/** Merchant moorings 0..2; the Kapal Lintas Laut quay is mooring {@value #LINTAS}. */
	public static final int DAGANG = 3;
	public static final int LINTAS = 3;
	public static final long SIKLUS = 72000L;
	public static final long DATANG = 2000L;
	public static final int ACAK_DATANG = 2000;
	public static final int LAYAR_MASUK = 1800;
	public static final int GULUNG = 40;
	public static final int JANGKAR = 30;
	/** Departure: day 2 at 14000 (u = 2 x 24000 + 14000 - 2000). */
	public static final long BERANGKAT_U = 60000L;
	public static final int MUNDUR = 400;
	public static final int PUTAR = 200;
	public static final int PERGI = 1000;
	public static final double JARAK_LAUT = 160.0D;
	public static final double JARAK_MUNDUR = 40.0D;
	public static final double JARAK_HILANG = 200.0D;
	/** A ship body exists only while a player is within this many blocks of its mooring. */
	public static final double JARAK = 160.0D;
	/** The Kapal Lintas Laut leaves at 12000 and is back at 18000. */
	public static final long LINTAS_BERANGKAT = 12000L;
	public static final long LINTAS_KEMBALI = 18000L;

	/** Origins of a merchant junk (lang {@code kultivator.kota_tema.pelabuhan.asal.<id>}). */
	public static final List<String> ASAL = List.of("selatan", "mutiara", "barat", "kota");

	/** The phases of one cycle. */
	public enum Fase {
		KOSONG, DATANG, GULUNG, JANGKAR_TURUN, TAMBAT, JANGKAR_NAIK, KEMBANG, MUNDUR, PUTAR, PERGI
	}

	/**
	 * Where the ship is: {@code jarak} blocks out from the berth along the keel line (toward the sea), {@code samping}
	 * blocks sideways, the keel turned {@code putar} degrees from "bow to the land", rudder {@code kemudi} -1..1.
	 */
	public record Pose(Fase fase, int keadaan, double jarak, double samping, float putar, float kemudi) {
		static final Pose KOSONG = new Pose(Fase.KOSONG, 0, 0.0D, 0.0D, 0.0F, 0.0F);
	}

	/** One live ship body. */
	static final class Kapal {
		final long situs;
		final int nomor;
		final BlockPos jangkar;
		final float yaw;
		final double airY;
		final DuniaAnchorEntity anchor;
		@Nullable KapalDagangEntity body;
		@Nullable KultivatorNpc nakhoda;
		long siklus = Long.MIN_VALUE;
		Fase fase = Fase.KOSONG;
		/** Arrival / departure starts moved by storms (absolute clock ticks; memory only). */
		long datangMulai = Long.MIN_VALUE;
		long pergiMulai = Long.MIN_VALUE;
		long liveSite;

		Kapal(long situs, int nomor, DuniaAnchorEntity anchor, double airY) {
			this.situs = situs;
			this.nomor = nomor;
			this.anchor = anchor;
			this.jangkar = anchor.blockPosition().immutable();
			this.yaw = anchor.getYRot();
			this.airY = airY;
		}

		/** Unit vector "bow to the land" (the anchor faces the quay). */
		Vec3 maju() {
			double r = Math.toRadians(yaw);
			return new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		}

		/** Unit vector toward the pier the gangway lands on (template -x of a mooring). */
		Vec3 dermaga() {
			double r = Math.toRadians(yaw);
			return new Vec3(-Math.cos(r), 0.0D, -Math.sin(r));
		}

		Vec3 pusat() {
			return new Vec3(jangkar.getX() + 0.5D, airY, jangkar.getZ() + 0.5D);
		}
	}

	private static final Map<Long, Kapal> HIDUP = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(JadwalKapal::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HIDUP.clear());
		// forced only (the clock runs the real schedule): uji tema acara pelabuhan kapal_datang|kapal_pergi, quest effect "acara"
		TemaKalender.daftar(PelabuhanKota.TEMA, "kapal_datang", 2000, 4000, (level, hari, situs, pusat) -> false, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				paksaDatang(level, situsId, Math.max(0, nomorHidup(situsId, false)));
			}
		});
		TemaKalender.daftar(PelabuhanKota.TEMA, "kapal_pergi", 14000, 15700, (level, hari, situs, pusat) -> false, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				int n = nomorHidup(situsId, true);
				if (n >= 0) {
					paksaPergi(level, situsId, n);
				}
			}
		});
	}

	// ================================================================== schedule (pure)

	/** The overworld clock. */
	static long waktu(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	/** Position of clock {@code t} in the cycle of mooring {@code n}. */
	public static long u(long t, int n) {
		return Math.floorMod(t - DATANG - 24000L * n, SIKLUS);
	}

	/** Cycle number of clock {@code t} for mooring {@code n}. */
	public static long siklus(long t, int n) {
		return Math.floorDiv(t - DATANG - 24000L * n, SIKLUS);
	}

	/** Clock tick the cycle {@code c} of mooring {@code n} begins (its u = 0). */
	public static long awalSiklus(long c, int n) {
		return c * SIKLUS + DATANG + 24000L * n;
	}

	/** The arrival delay 0..1999 of one cycle. */
	public static int tunda(long situs, int n, long c) {
		return (int) Math.floorMod(PelabuhanKota.hash(situs, c * 4L + n, 380401L), (long) ACAK_DATANG);
	}

	/** The origin id of one cycle. */
	public static String asal(long situs, int n, long c) {
		return ASAL.get((int) Math.floorMod(PelabuhanKota.hash(situs, c * 4L + n, 380402L), (long) ASAL.size()));
	}

	/** Pose at clock {@code t} given the (storm-moved) arrival and departure starts. */
	static Pose pose(long t, long datang, long pergi) {
		long a = t - datang;
		if (a < 0L) {
			return Pose.KOSONG;
		}
		if (a < LAYAR_MASUK) {
			double p = a / (double) LAYAR_MASUK;
			double s = JARAK_LAUT * (1.0D - easeOutCubic(p));
			// a gentle S-curve on the approach, straight for the last 30 blocks
			double side = s > 30.0D ? 5.0D * Math.sin((s - 30.0D) / (JARAK_LAUT - 30.0D) * Math.PI) : 0.0D;
			float rudder = (float) (s > 30.0D ? 0.6D * Math.cos((s - 30.0D) / (JARAK_LAUT - 30.0D) * Math.PI) : 0.0D);
			return new Pose(Fase.DATANG, KapalDagangEntity.KEADAAN_BERLAYAR, s, side, 0.0F, rudder);
		}
		a -= LAYAR_MASUK;
		long b = t - pergi;
		if (b < 0L) {
			if (a < GULUNG) {
				return new Pose(Fase.GULUNG, KapalDagangEntity.KEADAAN_GULUNG_LAYAR, 0.0D, 0.0D, 0.0F, 0.0F);
			}
			if (a < GULUNG + JANGKAR) {
				return new Pose(Fase.JANGKAR_TURUN, KapalDagangEntity.KEADAAN_JANGKAR_TURUN, 0.0D, 0.0D, 0.0F, 0.0F);
			}
			return new Pose(Fase.TAMBAT, KapalDagangEntity.KEADAAN_TITIAN_TURUN, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		if (b < JANGKAR) {
			return new Pose(Fase.JANGKAR_NAIK, KapalDagangEntity.KEADAAN_JANGKAR_NAIK, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		b -= JANGKAR;
		if (b < GULUNG) {
			return new Pose(Fase.KEMBANG, KapalDagangEntity.KEADAAN_KEMBANG_LAYAR, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		b -= GULUNG;
		if (b < MUNDUR) {
			double s = JARAK_MUNDUR * easeInOut(b / (double) MUNDUR);
			return new Pose(Fase.MUNDUR, KapalDagangEntity.KEADAAN_BERLAYAR, s, 0.0D, 0.0F, 0.0F);
		}
		b -= MUNDUR;
		if (b < PUTAR) {
			double q = easeInOut(b / (double) PUTAR);
			return new Pose(Fase.PUTAR, KapalDagangEntity.KEADAAN_BERLAYAR, JARAK_MUNDUR + 6.0D * q, 8.0D * Math.sin(q * Math.PI), (float) (180.0D * q), 1.0F);
		}
		b -= PUTAR;
		if (b < PERGI) {
			double q = b / (double) PERGI;
			double s = JARAK_MUNDUR + 6.0D + (JARAK_HILANG - JARAK_MUNDUR - 6.0D) * (q * q * 0.4D + q * 0.6D);
			return new Pose(Fase.PERGI, KapalDagangEntity.KEADAAN_MEMUDAR, s, 0.0D, 180.0F, 0.0F);
		}
		return Pose.KOSONG;
	}

	/** The pose of the Kapal Lintas Laut quay at time of day {@code jam}. */
	static Pose poseLintas(long jam, boolean badai) {
		if (badai || jam < LINTAS_BERANGKAT || jam >= LINTAS_KEMBALI) {
			return new Pose(Fase.TAMBAT, KapalDagangEntity.KEADAAN_TITIAN_TURUN, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		long a = jam - LINTAS_BERANGKAT;
		if (a < JANGKAR) {
			return new Pose(Fase.JANGKAR_NAIK, KapalDagangEntity.KEADAAN_JANGKAR_NAIK, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		if (a < JANGKAR + GULUNG) {
			return new Pose(Fase.KEMBANG, KapalDagangEntity.KEADAAN_KEMBANG_LAYAR, 0.0D, 0.0D, 0.0F, 0.0F);
		}
		a -= JANGKAR + GULUNG;
		if (a < 300L) {
			// drifts ten blocks bow first into the morning fog of the quay mouth, then the crossing is under way
			return new Pose(Fase.PERGI, KapalDagangEntity.KEADAAN_BERLAYAR, -10.0D * easeInOut(a / 300.0D), 0.0D, 0.0F, 0.0F);
		}
		return Pose.KOSONG;
	}

	private static double easeOutCubic(double p) {
		double q = 1.0D - Math.max(0.0D, Math.min(1.0D, p));
		return 1.0D - q * q * q;
	}

	private static double easeInOut(double p) {
		double q = Math.max(0.0D, Math.min(1.0D, p));
		return q * q * (3.0D - 2.0D * q);
	}

	/** True when the next cycle of mooring {@code n} was cancelled (a lost pirate raid). */
	static boolean batal(MinecraftServer server, long situs, int n, long c) {
		return PelabuhanKota.kota(server, situs).getLongOr("batal_" + n, Long.MIN_VALUE) == c;
	}

	/** Cancels the next cycle of mooring 0 (38.T.3.4: a lost raid, one merchant ship does not come). */
	static void batalkan(ServerLevel level, long situs) {
		long t = waktu(level.getServer());
		long c = siklus(t, 0) + 1L;
		PelabuhanKota.kota(level.getServer(), situs).putLong("batal_0", c);
		PelabuhanKota.ubah(level.getServer());
	}

	/** Moorings 0..2: the cycle state as data (for the screen, hub and commands): {@code datang|tambat|berangkat|kosong}. */
	public static String keadaanData(MinecraftServer server, long situs, int n) {
		long t = waktu(server);
		long c = siklus(t, n);
		if (batal(server, situs, n, c)) {
			return "kosong";
		}
		long u = u(t, n);
		int d = tunda(situs, n, c);
		if (u < d) {
			return "kosong";
		}
		if (u < d + LAYAR_MASUK) {
			return "datang";
		}
		if (u < BERANGKAT_U) {
			return "tambat";
		}
		return u < BERANGKAT_U + JANGKAR + GULUNG + MUNDUR + PUTAR + PERGI ? "berangkat" : "kosong";
	}

	/** World day the next ship of mooring {@code n} arrives (today when it is still to come today). */
	public static long hariDatang(MinecraftServer server, long situs, int n) {
		long t = waktu(server);
		long c = siklus(t, n);
		long start = awalSiklus(c, n) + tunda(situs, n, c);
		if (t > start) {
			c++;
			start = awalSiklus(c, n) + tunda(situs, n, c);
		}
		return Math.floorDiv(start, 24000L);
	}

	/** Clock tick the moored ship of mooring {@code n} leaves (its current cycle). */
	public static long tickBerangkat(MinecraftServer server, int n) {
		long t = waktu(server);
		return awalSiklus(siklus(t, n), n) + BERANGKAT_U;
	}

	// ================================================================== bodies

	/** From the {@code pelabuhan/tambatan} anchor while a player is near (every 20 ticks). */
	static void tambatanDekat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota) {
		if (!PelabuhanKota.aktif()) {
			return;
		}
		int nomor = PelabuhanAnchors.angka(anchor, "nomor", 0);
		if (nomor < LINTAS && !PelabuhanKota.cfg().kapalDagang) {
			return;
		}
		Kapal kapal = HIDUP.get(kunci(anchor));
		if (kapal == null) {
			Double air = permukaanAir(level, anchor.blockPosition());
			if (air == null) {
				return;
			}
			kapal = new Kapal(kota.situs, nomor, anchor, air);
			HIDUP.put(kunci(anchor), kapal);
		}
		perbarui(level, kapal, waktu(level.getServer()));
	}

	/** The anchor unloaded: its ship and captain go. */
	static void tambatanLepas(DuniaAnchorEntity anchor) {
		Kapal kapal = HIDUP.remove(kunci(anchor));
		if (kapal != null) {
			lepasTubuh(kapal);
		}
	}

	private static long kunci(DuniaAnchorEntity anchor) {
		return anchor.getUUID().getLeastSignificantBits() ^ anchor.blockPosition().asLong();
	}

	/** The water surface (top of the highest water block) at most 24 blocks under {@code at}, or null when dry. */
	static @Nullable Double permukaanAir(ServerLevel level, BlockPos at) {
		BlockPos.MutableBlockPos p = at.mutable();
		for (int i = 0; i < 24; i++) {
			FluidState fluid = level.getFluidState(p);
			if (!fluid.isEmpty()) {
				return (double) p.getY() + fluid.getHeight(level, p);
			}
			p.move(0, -1, 0);
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (HIDUP.isEmpty()) {
			return;
		}
		long t = waktu(server);
		for (Iterator<Kapal> it = HIDUP.values().iterator(); it.hasNext(); ) {
			Kapal kapal = it.next();
			if (kapal.anchor.isRemoved() || !(kapal.anchor.level() instanceof ServerLevel level)) {
				lepasTubuh(kapal);
				it.remove();
				continue;
			}
			try {
				gerak(level, kapal, t);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Harbour ship {} failed", kapal.nomor, exception);
				lepasTubuh(kapal);
				it.remove();
			}
		}
	}

	/** Every 20 ticks from the anchor: cycle bookkeeping, storms, bodies (spawn/remove), captain, cranes. */
	private static void perbarui(ServerLevel level, Kapal kapal, long t) {
		MinecraftServer server = level.getServer();
		boolean badai = PelabuhanKota.badai(level);
		Pose pose;
		if (kapal.nomor >= LINTAS) {
			pose = PelabuhanKota.cfg().lintasLaut ? poseLintas(PelabuhanKota.jam(level), badai) : Pose.KOSONG;
		} else {
			long c = siklus(t, kapal.nomor);
			if (c != kapal.siklus) {
				kapal.siklus = c;
				kapal.datangMulai = awalSiklus(c, kapal.nomor) + tunda(kapal.situs, kapal.nomor, c);
				kapal.pergiMulai = awalSiklus(c, kapal.nomor) + BERANGKAT_U;
			}
			if (batal(server, kapal.situs, kapal.nomor, c)) {
				pose = Pose.KOSONG;
			} else {
				// storms: the approach waits, the departure waits (never the world weather changed)
				if (badai && t >= kapal.datangMulai && kapal.fase == Fase.KOSONG && t < kapal.pergiMulai - 6000L) {
					kapal.datangMulai = t + 20L;
				}
				if (badai && t >= kapal.pergiMulai && t < kapal.pergiMulai + JANGKAR && kapal.fase == Fase.TAMBAT) {
					kapal.pergiMulai = t + 20L;
				}
				pose = kapal.datangMulai >= kapal.pergiMulai - 6000L ? Pose.KOSONG : pose(t, kapal.datangMulai, kapal.pergiMulai);
			}
		}
		boolean dekat = TemaMekanisme.pemainDekat(level, kapal.pusat(), JARAK);
		if (pose.fase() == Fase.KOSONG || !dekat) {
			if (kapal.fase != Fase.KOSONG && pose.fase() == Fase.KOSONG) {
				pergiSelesai(level, kapal);
			}
			kapal.fase = pose.fase();
			lepasTubuh(kapal);
			return;
		}
		if (kapal.body == null || kapal.body.isRemoved()) {
			kapal.body = buat(level, kapal);
			if (kapal.body == null) {
				return;
			}
		}
		Fase before = kapal.fase;
		kapal.fase = pose.fase();
		if (before != kapal.fase) {
			berganti(level, kapal, before, kapal.fase);
		}
		if (kapal.fase == Fase.TAMBAT) {
			nakhoda(level, kapal);
		} else if (kapal.nakhoda != null) {
			kapal.nakhoda.vanish();
			kapal.nakhoda = null;
		}
	}

	/** Every tick: the body follows the clock. */
	private static void gerak(ServerLevel level, Kapal kapal, long t) {
		KapalDagangEntity body = kapal.body;
		if (body == null || body.isRemoved()) {
			return;
		}
		Pose pose = kapal.nomor >= LINTAS ? poseLintas(PelabuhanKota.jam(level), PelabuhanKota.badai(level))
				: pose(t, kapal.datangMulai, kapal.pergiMulai);
		if (pose.fase() == Fase.KOSONG) {
			return;
		}
		Vec3 maju = kapal.maju();
		Vec3 side = kapal.dermaga();
		Vec3 pos = kapal.pusat().subtract(maju.scale(pose.jarak())).add(side.scale(pose.samping()));
		// a little heave so a moored ship is never perfectly still on the server box (the model adds the roll)
		float yaw = kapal.yaw + pose.putar();
		body.setKeadaan(pose.keadaan());
		body.setNilai(pose.kemudi());
		body.pindah(pos, yaw);
	}

	private static @Nullable KapalDagangEntity buat(ServerLevel level, Kapal kapal) {
		if (!DuniaBudget.mayAdd(level, TemaMekanisme.JENIS)) {
			return null;
		}
		KapalDagangEntity body = TemaEntities.KAPAL_DAGANG.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			return null;
		}
		Vec3 at = kapal.pusat();
		body.snapTo(at.x, at.y, at.z, kapal.yaw, 0.0F);
		body.ikat(kapal.situs, kapal.jangkar.asLong());
		body.tambatan = kapal.nomor;
		body.setVarian(kapal.nomor >= LINTAS ? KapalDagangEntity.VARIAN_JUNG_LAUT : KapalDagangEntity.VARIAN_TIKAR);
		gerak(level, kapal, waktu(level.getServer()));
		if (!level.addFreshEntity(body)) {
			return null;
		}
		DuniaBudget.track(TemaMekanisme.JENIS, body);
		return body;
	}

	private static void lepasTubuh(Kapal kapal) {
		if (kapal.body != null && !kapal.body.isRemoved()) {
			kapal.body.discard();
		}
		kapal.body = null;
		if (kapal.nakhoda != null && !kapal.nakhoda.isRemoved()) {
			kapal.nakhoda.vanish();
		}
		kapal.nakhoda = null;
	}

	/** Phase changes: voices, the live pointer, the schedule packet, rumours. */
	private static void berganti(ServerLevel level, Kapal kapal, Fase from, Fase to) {
		if (kapal.nomor >= LINTAS) {
			return;
		}
		if (to == Fase.DATANG && from == Fase.KOSONG) {
			kirimJadwal(level, kapal.situs, kapal.pusat());
		}
		if (to == Fase.TAMBAT) {
			long until = kapal.pergiMulai;
			DuniaSites.Site live = DuniaSites.publishLive(level, "tema/kapal_dagang", kapal.jangkar, 1, until);
			kapal.liveSite = live.id();
			if (from == Fase.JANGKAR_TURUN || from == Fase.GULUNG) {
				seru(level, kapal, "tema.pelabuhan.ship_arrive");
				// one template per origin: the listener's client translates it (a server-side getString would be the key)
				CeritaApi.kabar(level, kapal.situs, "tema_pelabuhan_kapal_tiba_" + asal(kapal.situs, kapal.nomor, kapal.siklus));
				TempatEvents.fireAcara(level, kapal.situs, "tema.kapal_datang", true);
			}
		}
		if (to == Fase.JANGKAR_NAIK) {
			seru(level, kapal, "tema.pelabuhan.ship_depart");
			if (kapal.liveSite != 0L) {
				DuniaSites.endLive(kapal.liveSite);
				kapal.liveSite = 0L;
			}
			TempatEvents.fireAcara(level, kapal.situs, "tema.kapal_pergi", true);
		}
	}

	private static void pergiSelesai(ServerLevel level, Kapal kapal) {
		if (kapal.liveSite != 0L) {
			DuniaSites.endLive(kapal.liveSite);
			kapal.liveSite = 0L;
		}
	}

	/** A sailor's call from the pier (or the captain). */
	private static void seru(ServerLevel level, Kapal kapal, String voice) {
		if (!VoiceEvents.exists(voice)) {
			return;
		}
		if (kapal.nakhoda != null && !kapal.nakhoda.isRemoved()) {
			Voice.say(kapal.nakhoda, voice, null);
		} else if (kapal.body != null) {
			Voice.say(kapal.body, voice, null);
		}
	}

	/** The captain on the pier at the gangway while moored (NAKHODA_LAUT, sells the cargo). */
	private static void nakhoda(ServerLevel level, Kapal kapal) {
		if (kapal.nakhoda != null && !kapal.nakhoda.isRemoved()) {
			return;
		}
		Vec3 spot = kapal.pusat().add(kapal.dermaga().scale(4.0D));
		BlockPos stand = BlockPos.containing(spot.x, kapal.jangkar.getY() + 1, spot.z);
		KultivatorNpc npc = HidupApi.tubuh(level, kapal.situs, NpcRole.NAKHODA_LAUT, stand, kapal.yaw + 90.0F);
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("hold", true);
		npc.memory().putInt(TokoPelabuhan.KAPAL_NPC, kapal.nomor);
		npc.memory().putLong(TokoPelabuhan.SITUS_NPC, kapal.situs);
		kapal.nakhoda = npc;
	}

	/** True while mooring {@code nomor} of town {@code situs} has a ship moored with the gangway down (live or data). */
	public static boolean tertambat(MinecraftServer server, long situs, int nomor) {
		if (nomor >= LINTAS) {
			return true;
		}
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs && k.nomor == nomor && k.anchor.isAlive()) {
				return k.fase == Fase.TAMBAT;
			}
		}
		return "tambat".equals(keadaanData(server, situs, nomor));
	}

	/** True when any merchant mooring of {@code situs} has a ship tied up now (cranes, porters, the market mood). */
	public static boolean adaTambat(MinecraftServer server, long situs, int... moorings) {
		for (int n : moorings) {
			if (n >= 0 && n < DAGANG && tertambat(server, situs, n)) {
				return true;
			}
		}
		return false;
	}

	/** A click on a moored hull: the cargo screen of that ship through its captain. */
	static void klikKapal(ServerPlayer player, KapalDagangEntity ship) {
		for (Kapal k : HIDUP.values()) {
			if (k.body == ship) {
				if (k.nomor >= LINTAS) {
					KultivatorNpc gao = PelabuhanAnchors.tokohDekat(player, PelabuhanTokoh.GAO_FAN, 24.0D);
					if (gao != null && gao.distanceToSqr(player) <= 64.0D) {
						TokoPelabuhan.bukaLintas(player, gao);
					} else {
						Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.kapal.cari_gao").withStyle(ChatFormatting.GRAY));
					}
					return;
				}
				if (k.nakhoda != null && k.nakhoda.distanceToSqr(player) <= 64.0D) {
					TokoPelabuhan.bukaKapal(player, k.nakhoda);
				} else {
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.kapal.cari_nakhoda").withStyle(ChatFormatting.GRAY));
				}
				return;
			}
		}
	}

	/** The live ship bodies of a town (cranes and porters look at them). */
	static List<KapalDagangEntity> kapalHidup(long situs) {
		List<KapalDagangEntity> out = new ArrayList<>();
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs && k.body != null && !k.body.isRemoved()) {
				out.add(k.body);
			}
		}
		return out;
	}

	/** The schedule of a town as the {@code KAPAL_JADWAL} packet to players within 160 blocks. */
	static void kirimJadwal(ServerLevel level, long situs, Vec3 at) {
		MinecraftServer server = level.getServer();
		ListTag list = new ListTag();
		long t = waktu(server);
		for (int n = 0; n < DAGANG; n++) {
			long c = siklus(t, n);
			CompoundTag e = new CompoundTag();
			e.putInt("tambatan", n);
			e.putString("asal", "kultivator.kota_tema.pelabuhan.asal." + asal(situs, n, c));
			e.putLong("datang", awalSiklus(c, n) + tunda(situs, n, c));
			e.putLong("pergi", awalSiklus(c, n) + BERANGKAT_U);
			e.putInt("keadaan", switch (keadaanData(server, situs, n)) {
				case "datang" -> 1;
				case "tambat" -> 2;
				case "berangkat" -> 3;
				default -> 0;
			});
			list.add(e);
		}
		CompoundTag data = new CompoundTag();
		data.put("kapal", list);
		TemaNet.kirimSekitar(level, at, 160.0D, TemaNet.KAPAL_JADWAL, situs, data);
	}

	/**
	 * Forces a merchant junk to come in to mooring {@code nomor} now (calendar event {@code kapal_datang}, forced by
	 * {@code uji tema acara pelabuhan kapal_datang} or a quest): it appears 160 blocks out and moors for a day. False
	 * when the mooring is not live (no player near it).
	 */
	static boolean paksaDatang(ServerLevel level, long situs, int nomor) {
		long t = waktu(level.getServer());
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs && k.nomor == nomor) {
				if (k.siklus == Long.MIN_VALUE) {
					k.siklus = siklus(t, nomor);
				}
				lepasTubuh(k);
				k.fase = Fase.KOSONG;
				k.datangMulai = t;
				k.pergiMulai = t + LAYAR_MASUK + 24000L;
				return true;
			}
		}
		return false;
	}

	/** Forces the moored junk of mooring {@code nomor} to weigh anchor now (event {@code kapal_pergi}). */
	static boolean paksaPergi(ServerLevel level, long situs, int nomor) {
		long t = waktu(level.getServer());
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs && k.nomor == nomor && k.fase == Fase.TAMBAT) {
				k.pergiMulai = t;
				return true;
			}
		}
		return false;
	}

	/** The first live merchant mooring of a town that has a ship tied up (else the first live one), or -1. */
	static int nomorHidup(long situs, boolean tambat) {
		int best = -1;
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs && k.nomor < LINTAS) {
				if (!tambat || k.fase == Fase.TAMBAT) {
					if (best < 0 || k.nomor < best) {
						best = k.nomor;
					}
				}
			}
		}
		return best;
	}

	/** Tests: drops the storm/cancel overrides of a town so the clock alone decides again. */
	static void ulang(long situs) {
		for (Kapal k : HIDUP.values()) {
			if (k.situs == situs) {
				k.siklus = Long.MIN_VALUE;
			}
		}
	}

	/** Tests: how many ship bodies live now. */
	static int jumlahTubuh() {
		int n = 0;
		for (Kapal k : HIDUP.values()) {
			if (k.body != null && !k.body.isRemoved()) {
				n++;
			}
		}
		return n;
	}

	private JadwalKapal() {
	}
}

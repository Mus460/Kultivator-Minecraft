package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The canal boats of the Kota Air (DESIGN 38.T.5.2, 38.T.5.4), all {@link PerahuKanalEntity} bodies made under inti's
 * caps ({@code TemaMekanisme}) only while a player is near, moved by the server one step per tick:
 * <ul>
 * <li><b>Perahu Pos</b>: one boat per town on the {@link JalurKanal#POS} loop, a pure function of the game time
 * ({@link #PERIODE_POS} ticks per round, {@link #DIAM_HALTE} ticks at each of the five stops), a PENDAYUNG body rowing
 * at the stern ({@code dayung_yuloh}). Right-click to board for {@link #HARGA_POS} NB; ride as passenger, get off at any
 * stop (sneak); {@code TemaSibuk} {@code perahu} while aboard.</li>
 * <li><b>Tur Kanal</b> ({@link #HARGA_TUR} NB, from Bibi Chuan): a decorated boat with Bibi Chuan rowing and singing,
 * {@link JalurKanal#TUR} in {@link #LAMA_TUR} ticks; she tells about six places as they pass. The first tour: +1
 * comprehension, flag {@code tema.air.tur}, advancement {@code tempat/tema_air_tur}.</li>
 * <li><b>Sewa</b> ({@link #HARGA_SEWA} NB per world hour, from Bibi Chuan): the player rows the boat himself (forward /
 * back / left / right of the client input), {@link #PERCEPATAN} per tick up to {@link #LAJU_MAKS}, only on water; left
 * alone for {@link #DITINGGAL} ticks it goes back to its mooring (removed).</li>
 * <li><b>Perahu-lapak</b>: 06-10 the stall boats lie at the {@code tambatan kelas=lapak} points of the Pasar Air Pagi
 * (the four nearest a player), the Tikus Air's favourite prey.</li>
 * </ul>
 */
public final class Perahu {

	public static final long PERIODE_POS = 7200L;
	public static final int DIAM_HALTE = 100;
	public static final long HARGA_POS = 1L;
	public static final long HARGA_TUR = 3L;
	public static final long HARGA_SEWA = 2L;
	public static final int LAMA_TUR = 7200;
	public static final int SEWA_JAM = 1000;
	public static final int DITINGGAL = 2400;
	public static final double PERCEPATAN = 0.08D;
	public static final double LAJU_MAKS = 0.32D;
	public static final double HAMBAT = 0.86D;
	private static final double JARAK_MUNCUL = 96.0D;
	private static final double JARAK_LEPAS = 128.0D;
	private static final int LAPAK_MAKS = 4;
	private static final int KLIP_ULANG = 160;

	/** The stops of the Perahu Pos loop in route order (arc lengths filled on first use). */
	private static double[] halteS;

	/** Live Perahu Pos of a town. */
	private static final class Pos {
		@Nullable PerahuKanalEntity perahu;
		@Nullable KultivatorNpc pendayung;
		long klip;
	}

	/** A running tour. */
	private static final class Tur {
		final long situs;
		final PerahuKanalEntity perahu;
		final UUID pemain;
		@Nullable KultivatorNpc pemandu;
		@Nullable BlockPos pulang;
		final long mulai;
		final java.util.Set<String> diceritakan = new java.util.HashSet<>();
		long lagu;

		Tur(long situs, PerahuKanalEntity perahu, UUID pemain, long mulai) {
			this.situs = situs;
			this.perahu = perahu;
			this.pemain = pemain;
			this.mulai = mulai;
		}
	}

	/** A hired boat. */
	private static final class Sewa {
		final long situs;
		final PerahuKanalEntity perahu;
		final UUID pemain;
		long dibayarSampai;
		long terakhirDinaiki;
		double laju;

		Sewa(long situs, PerahuKanalEntity perahu, UUID pemain, long now) {
			this.situs = situs;
			this.perahu = perahu;
			this.pemain = pemain;
			this.dibayarSampai = now + SEWA_JAM;
			this.terakhirDinaiki = now;
		}
	}

	private static final Map<Long, Pos> POS = new HashMap<>();
	private static final Map<UUID, Tur> TUR = new HashMap<>();
	private static final Map<UUID, Sewa> SEWA = new HashMap<>();
	private static final Map<Long, Map<BlockPos, PerahuKanalEntity>> LAPAK = new HashMap<>();

	static void reset() {
		POS.clear();
		TUR.clear();
		SEWA.clear();
		LAPAK.clear();
	}

	// ================================================================== tick

	/** Every server tick (from {@link Air}). */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!AirKota.aktif()) {
			return;
		}
		long now = level.getGameTime();
		for (AirKota.Hidup kota : AirKota.semua()) {
			if (AirKota.config().perahuPos) {
				pos(level, kota, now);
			}
			if (now % 40L == (kota.situs & 31L)) {
				lapak(level, kota);
			}
		}
		tur(level, now);
		sewa(level, now);
	}

	// ================================================================== Perahu Pos

	private static double[] halte() {
		if (halteS == null) {
			List<Double> list = new ArrayList<>();
			List<JalurKanal.Titik> pts = JalurKanal.LINTAS_POS.titik();
			for (int i = 0; i < pts.size() - 1; i++) {
				if (!pts.get(i).halte().isEmpty()) {
					list.add(JalurKanal.LINTAS_POS.jarakTitik(i));
				}
			}
			halteS = list.stream().mapToDouble(Double::doubleValue).toArray();
		}
		return halteS;
	}

	/** {arc length, 1 when standing at a stop} of the Perahu Pos at loop time t. */
	static double[] posPada(long t) {
		double[] stops = halte();
		double len = JalurKanal.LINTAS_POS.panjang();
		double moving = PERIODE_POS - (double) stops.length * DIAM_HALTE;
		double v = len / moving;
		double time = Math.floorMod(t, PERIODE_POS);
		double s = 0.0D;
		for (int i = 0; i < stops.length; i++) {
			double travel = (stops[i] - s) / v;
			if (time < travel) {
				return new double[] {s + time * v, 0.0D};
			}
			time -= travel;
			s = stops[i];
			if (time < DIAM_HALTE) {
				return new double[] {s, 1.0D};
			}
			time -= DIAM_HALTE;
		}
		return new double[] {Math.min(len, s + time * v), 0.0D};
	}

	private static void pos(ServerLevel level, AirKota.Hidup kota, long now) {
		Pos line = POS.computeIfAbsent(kota.situs, k -> new Pos());
		double[] at = posPada(now + (kota.situs & 0xFFFL));
		Vec3 where = JalurKanal.dunia(kota, JalurKanal.LINTAS_POS, at[0], 0.0D);
		float yaw = JalurKanal.yaw(kota, JalurKanal.LINTAS_POS, at[0]);
		PerahuKanalEntity boat = line.perahu;
		if (boat != null && boat.isRemoved()) {
			boat = null;
			line.perahu = null;
		}
		if (boat == null) {
			if (now % 20L != 0L || !TemaMekanisme.pemainDekat(level, where, JARAK_MUNCUL)) {
				return;
			}
			boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_KANAL, where, yaw, kota.kota, null);
			if (boat == null) {
				return;
			}
			boat.setVarian(PerahuKanalEntity.VARIAN_BIASA);
			line.perahu = boat;
		}
		if (now % 40L == 0L && !TemaMekanisme.pemainDekat(level, boat.position(), JARAK_LEPAS) && !adaPemain(boat)) {
			lepasPos(line);
			return;
		}
		boat.pindah(where, yaw);
		boat.setKeadaan(at[1] > 0.5D ? PerahuKanalEntity.KEADAAN_TAMBAT : PerahuKanalEntity.KEADAAN_JALAN);
		boat.setNilai(at[1] > 0.5D ? 0.0F : 0.6F);
		// the rower
		KultivatorNpc rower = line.pendayung;
		if (rower == null || rower.isRemoved() || rower.getVehicle() != boat) {
			if (rower != null && !rower.isRemoved()) {
				rower.vanish();
			}
			line.pendayung = null;
			if (now % 20L == 0L && boat.getPassengers().isEmpty()) {
				KultivatorNpc npc = HidupApi.tubuh(level, kota.situs, NpcRole.PENDAYUNG, BlockPos.containing(where), yaw);
				if (npc != null) {
					npc.memory().putBoolean("hold", true);
					if (npc.startRiding(boat)) {
						line.pendayung = npc;
					} else {
						npc.vanish();
					}
				}
			}
		} else if (at[1] < 0.5D && now - line.klip >= KLIP_ULANG) {
			line.klip = now;
			HidupApi.klip(rower, GerakId.id("dayung_yuloh"), PropId.id("dayung_yuloh"), KLIP_ULANG);
		}
		// passengers: the hold of the busy state, a word at each stop
		for (Entity e : boat.getPassengers()) {
			if (e instanceof ServerPlayer p) {
				TemaSibuk.tahan(p, TemaSibuk.PERAHU, 60);
				if (at[1] > 0.5D && Math.floorMod(now + (kota.situs & 0xFFFL), PERIODE_POS) % 20L == 0L) {
					String halte = JalurKanal.LINTAS_POS.halteDekat(at[0], 1.0D);
					if (!halte.isEmpty()) {
						Msg.actionBar(p, Component.translatable("kultivator.kota_tema.air.pos.halte",
								Component.translatable("kultivator.kota_tema.air.halte." + halte)).withStyle(ChatFormatting.AQUA));
					}
				}
			}
		}
	}

	private static boolean adaPemain(Entity boat) {
		for (Entity e : boat.getPassengers()) {
			if (e instanceof ServerPlayer) {
				return true;
			}
		}
		return false;
	}

	private static void lepasPos(Pos line) {
		if (line.pendayung != null && !line.pendayung.isRemoved()) {
			line.pendayung.vanish();
		}
		line.pendayung = null;
		TemaMekanisme.lepas(line.perahu);
		line.perahu = null;
	}

	// ================================================================== clicks

	/** A right-click on a canal boat; true when handled. */
	static boolean klik(PerahuKanalEntity boat, ServerPlayer player) {
		if (player.getVehicle() == boat) {
			return false;
		}
		for (Pos line : POS.values()) {
			if (line.perahu == boat) {
				return naikPos(boat, player);
			}
		}
		Sewa hired = SEWA.get(player.getUUID());
		if (hired != null && hired.perahu == boat) {
			if (boat.getPassengers().isEmpty() && player.startRiding(boat)) {
				hired.terakhirDinaiki = player.level().getGameTime();
				return true;
			}
			return false;
		}
		if (boat.varian() == PerahuKanalEntity.VARIAN_LAPAK) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.lapak.sapa").withStyle(ChatFormatting.GOLD));
			return true;
		}
		return false;
	}

	private static boolean naikPos(PerahuKanalEntity boat, ServerPlayer player) {
		if (boat.getPassengers().size() >= 3) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.pos.penuh").withStyle(ChatFormatting.RED));
			return true;
		}
		String why = dev.kultivator.util.Sibuk.why(player);
		if (why != null) {
			Msg.actionBar(player, dev.kultivator.util.Sibuk.text(why).withStyle(ChatFormatting.RED));
			return true;
		}
		if (!NilaiBatu.pay(player, HARGA_POS)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(HARGA_POS)).withStyle(ChatFormatting.RED));
			return true;
		}
		if (player.startRiding(boat)) {
			TemaSibuk.tahan(player, TemaSibuk.PERAHU, 60);
			player.level().playSound(null, boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.8F, 1.0F);
			Msg.chat(player, Component.translatable("kultivator.kota_tema.air.pos.naik").withStyle(ChatFormatting.AQUA));
			AirCerita.peristiwa(player, "air_perahu", "pos");
		} else {
			NilaiBatu.give(player, HARGA_POS);
		}
		return true;
	}

	// ================================================================== the tour

	/** Starts the Tur Kanal for a player (from Bibi Chuan's dialog); the message says why not. */
	public static boolean mulaiTur(ServerPlayer player, @Nullable KultivatorNpc pemandu) {
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (TUR.containsKey(player.getUUID()) || SEWA.containsKey(player.getUUID())) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.tur.sudah").withStyle(ChatFormatting.RED));
			return false;
		}
		String why = dev.kultivator.util.Sibuk.why(player);
		if (why != null) {
			Msg.actionBar(player, dev.kultivator.util.Sibuk.text(why).withStyle(ChatFormatting.RED));
			return false;
		}
		Vec3 start = JalurKanal.dunia(kota, JalurKanal.LINTAS_TUR, 0.0D, 0.0D);
		if (!TemaMekanisme.boleh(level, kota.situs, start)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.perahu.habis").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!NilaiBatu.pay(player, HARGA_TUR)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(HARGA_TUR)).withStyle(ChatFormatting.RED));
			return false;
		}
		PerahuKanalEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_KANAL, start,
				JalurKanal.yaw(kota, JalurKanal.LINTAS_TUR, 0.0D), kota.kota, null);
		if (boat == null) {
			NilaiBatu.give(player, HARGA_TUR);
			return false;
		}
		boat.setVarian(PerahuKanalEntity.VARIAN_WISATA);
		boat.setKeadaan(PerahuKanalEntity.KEADAAN_JALAN);
		Tur tour = new Tur(kota.situs, boat, player.getUUID(), level.getGameTime());
		if (pemandu != null && !pemandu.isRemoved()) {
			tour.pulang = pemandu.blockPosition();
			if (pemandu.startRiding(boat)) {
				tour.pemandu = pemandu;
			}
		}
		player.teleportTo(start.x, start.y + 0.6D, start.z);
		if (!player.startRiding(boat)) {
			TemaMekanisme.lepas(boat);
			NilaiBatu.give(player, HARGA_TUR);
			return false;
		}
		TUR.put(player.getUUID(), tour);
		TemaSibuk.tahan(player, TemaSibuk.PERAHU, LAMA_TUR + 200);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.tur.mulai").withStyle(ChatFormatting.AQUA));
		if (tour.pemandu != null) {
			tour.pemandu.say(Component.translatable("kultivator.kota_tema.air.tur.sapa"), 80);
		}
		return true;
	}

	private static void tur(ServerLevel level, long now) {
		for (Iterator<Map.Entry<UUID, Tur>> it = TUR.entrySet().iterator(); it.hasNext(); ) {
			Tur tour = it.next().getValue();
			AirKota.Hidup kota = AirKota.cari(tour.situs);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(tour.pemain);
			long age = now - tour.mulai;
			double len = JalurKanal.LINTAS_TUR.panjang();
			double s = len * Math.min(1.0D, age / (double) LAMA_TUR);
			boolean gone = kota == null || tour.perahu.isRemoved() || player == null || player.getVehicle() != tour.perahu && age > 40L;
			if (gone || age >= LAMA_TUR) {
				akhiriTur(level, tour, player, !gone);
				it.remove();
				continue;
			}
			tour.perahu.pindah(JalurKanal.dunia(kota, JalurKanal.LINTAS_TUR, s, 0.0D), JalurKanal.yaw(kota, JalurKanal.LINTAS_TUR, s));
			tour.perahu.setNilai(0.5F);
			TemaSibuk.tahan(player, TemaSibuk.PERAHU, 60);
			String place = JalurKanal.LINTAS_TUR.halteDekat(s, 10.0D);
			if (!place.isEmpty() && !"akhir".equals(place) && tour.diceritakan.add(place)) {
				Component line = Component.translatable("kultivator.kota_tema.air.tur.tempat." + place);
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.tur.pemandu", AirTokoh.nama(AirTokoh.BIBI_CHUAN), line)
						.withStyle(ChatFormatting.GRAY));
				if (tour.pemandu != null && !tour.pemandu.isRemoved()) {
					tour.pemandu.say(Component.translatable("kultivator.kota_tema.air.tur.tempat." + place + ".singkat"), 100);
				}
				AirCerita.turLewat(player, place, level, tour.pemandu);
			} else if (now - tour.lagu >= 900L && tour.pemandu != null && !tour.pemandu.isRemoved()) {
				tour.lagu = now;
				int n = 1 + (int) Math.floorMod(now / 900L, 6L);
				tour.pemandu.say(Component.translatable("kultivator.kota_tema.air.lagu." + n), 120);
				HidupApi.klip(tour.pemandu, GerakId.id("nyanyi_perahu"), 0, 120);
			} else if (tour.pemandu != null && !tour.pemandu.isRemoved() && now % KLIP_ULANG == 0L) {
				HidupApi.klip(tour.pemandu, GerakId.id("dayung_yuloh"), PropId.id("dayung_yuloh"), KLIP_ULANG);
			}
		}
	}

	private static void akhiriTur(ServerLevel level, Tur tour, @Nullable ServerPlayer player, boolean selesai) {
		if (tour.pemandu != null && !tour.pemandu.isRemoved()) {
			tour.pemandu.stopRiding();
			if (tour.pulang != null) {
				tour.pemandu.teleportTo(tour.pulang.getX() + 0.5D, tour.pulang.getY(), tour.pulang.getZ() + 0.5D);
			}
		}
		if (player != null) {
			if (player.getVehicle() == tour.perahu) {
				player.stopRiding();
			}
			TemaSibuk.lepas(player, TemaSibuk.PERAHU);
			if (selesai) {
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.tur.selesai").withStyle(ChatFormatting.AQUA));
				CompoundTag me = AirKota.pemain(level.getServer(), player.getUUID());
				if (!me.getBooleanOr("tur", false)) {
					me.putBoolean("tur", true);
					AirKota.ubah(level.getServer());
					CultivationData data = Cultivators.get(player);
					Dao dao = data.dao() != null ? data.dao() : Dao.LIFE;
					double got = Comprehension.grant(player, dao, 1.0D);
					Msg.chat(player, Component.translatable("kultivator.kota_tema.air.tur.paham", Msg.number(got)).withStyle(ChatFormatting.GOLD));
					dev.kultivator.dunia.tempat.cerita.CeritaApi.setBendera(player, "tema.air.tur", true);
					Advancements.grant(player, "tempat/tema_air_tur");
				}
				AirCerita.peristiwa(player, "air_tur", "selesai");
			}
		}
		Vec3 end = tour.perahu.position();
		TemaMekanisme.lepas(tour.perahu);
		if (player != null && selesai) {
			player.teleportTo(end.x, AirKota.cari(tour.situs) != null ? AirKota.cari(tour.situs).lantai() + 0.1D : end.y + 1.0D, end.z);
		}
	}

	// ================================================================== hire

	/** Hires a boat for a player at the nearest hire mooring (from Bibi Chuan's dialog). */
	public static boolean sewa(ServerPlayer player) {
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (TUR.containsKey(player.getUUID()) || SEWA.containsKey(player.getUUID())) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.sewa.sudah").withStyle(ChatFormatting.RED));
			return false;
		}
		Titik best = null;
		double bestD = 64.0D * 64.0D;
		for (Titik t : AirKota.titik(level, kota, "tambatan")) {
			if (!"sewa".equals(t.data().getOrDefault("kelas", ""))) {
				continue;
			}
			double d = t.pos().distSqr(player.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		Vec3 at = best != null ? new Vec3(best.pos().getX() + 0.5D, kota.permukaan(), best.pos().getZ() + 0.5D) : null;
		if (at == null || !air(level, kota, at)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.sewa.jauh").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!TemaMekanisme.boleh(level, kota.situs, at)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.perahu.habis").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!NilaiBatu.pay(player, HARGA_SEWA)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(HARGA_SEWA)).withStyle(ChatFormatting.RED));
			return false;
		}
		PerahuKanalEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_KANAL, at, best.hadap().toYRot(), kota.kota, best.pos());
		if (boat == null) {
			NilaiBatu.give(player, HARGA_SEWA);
			return false;
		}
		boat.setVarian(PerahuKanalEntity.VARIAN_BIASA);
		boat.setKeadaan(PerahuKanalEntity.KEADAAN_TAMBAT);
		Sewa hire = new Sewa(kota.situs, boat, player.getUUID(), level.getGameTime());
		SEWA.put(player.getUUID(), hire);
		player.teleportTo(at.x, at.y + 0.6D, at.z);
		player.startRiding(boat);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.sewa.mulai", NilaiBatu.text(HARGA_SEWA)).withStyle(ChatFormatting.AQUA));
		AirCerita.peristiwa(player, "air_perahu", "sewa");
		return true;
	}

	private static void sewa(ServerLevel level, long now) {
		for (Iterator<Map.Entry<UUID, Sewa>> it = SEWA.entrySet().iterator(); it.hasNext(); ) {
			Sewa hire = it.next().getValue();
			AirKota.Hidup kota = AirKota.cari(hire.situs);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(hire.pemain);
			if (kota == null || hire.perahu.isRemoved()) {
				it.remove();
				continue;
			}
			boolean riding = player != null && player.getVehicle() == hire.perahu;
			if (!riding) {
				hire.laju = 0.0D;
				hire.perahu.setKeadaan(PerahuKanalEntity.KEADAAN_TAMBAT);
				hire.perahu.setNilai(0.0F);
				if (player != null) {
					TemaSibuk.lepas(player, TemaSibuk.PERAHU);
				}
				if (now - hire.terakhirDinaiki >= DITINGGAL) {
					if (player != null) {
						Msg.chat(player, Component.translatable("kultivator.kota_tema.air.sewa.kembali").withStyle(ChatFormatting.GRAY));
					}
					TemaMekanisme.lepas(hire.perahu);
					it.remove();
				}
				continue;
			}
			hire.terakhirDinaiki = now;
			TemaSibuk.tahan(player, TemaSibuk.PERAHU, 60);
			if (now >= hire.dibayarSampai) {
				if (NilaiBatu.pay(player, HARGA_SEWA)) {
					hire.dibayarSampai = now + SEWA_JAM;
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.sewa.bayar", NilaiBatu.text(HARGA_SEWA)).withStyle(ChatFormatting.GRAY));
				} else {
					Msg.chat(player, Component.translatable("kultivator.kota_tema.air.sewa.habis").withStyle(ChatFormatting.RED));
					player.stopRiding();
					TemaSibuk.lepas(player, TemaSibuk.PERAHU);
					TemaMekanisme.lepas(hire.perahu);
					it.remove();
					continue;
				}
			}
			dayung(level, kota, hire, player.getLastClientInput());
		}
	}

	/** The player rows: steer with left/right, pull with forward, brake with back; only on canal water. */
	private static void dayung(ServerLevel level, AirKota.Hidup kota, Sewa hire, Input input) {
		PerahuKanalEntity boat = hire.perahu;
		float yaw = boat.getYRot();
		if (input.left()) {
			yaw -= 3.5F;
		}
		if (input.right()) {
			yaw += 3.5F;
		}
		if (input.forward()) {
			hire.laju = Math.min(LAJU_MAKS, hire.laju + PERCEPATAN * 0.5D);
		} else if (input.backward()) {
			hire.laju = Math.max(-LAJU_MAKS * 0.4D, hire.laju - PERCEPATAN * 0.5D);
		} else {
			hire.laju *= HAMBAT;
		}
		double rad = Math.toRadians(yaw);
		Vec3 dir = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		Vec3 next = new Vec3(boat.getX() + dir.x * hire.laju, kota.permukaan(), boat.getZ() + dir.z * hire.laju);
		Vec3 bow = next.add(dir.scale(Math.signum(hire.laju) * 2.6D));
		if (Math.abs(hire.laju) > 1.0E-3D && (!air(level, kota, next) || !air(level, kota, bow))) {
			hire.laju = 0.0D;
			next = new Vec3(boat.getX(), kota.permukaan(), boat.getZ());
			if (level.getGameTime() % 20L == 0L) {
				level.playSound(null, boat.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.5F, 0.8F);
			}
		}
		boat.pindah(next, yaw);
		boolean moving = Math.abs(hire.laju) > 0.02D;
		boat.setKeadaan(moving ? PerahuKanalEntity.KEADAAN_JALAN : PerahuKanalEntity.KEADAAN_TAMBAT);
		boat.setNilai((float) Math.min(1.0D, Math.abs(hire.laju) / LAJU_MAKS));
		if (moving && level.getGameTime() % 30L == 0L) {
			level.playSound(null, boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.6F, 1.0F);
		}
	}

	/** True when the column under {@code at} is open canal water (water top block, air above it). */
	static boolean air(ServerLevel level, AirKota.Hidup kota, Vec3 at) {
		BlockPos top = BlockPos.containing(at.x, kota.lantai() - 2, at.z);
		return level.getFluidState(top).is(FluidTags.WATER) && level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty();
	}

	// ================================================================== stall boats

	private static void lapak(ServerLevel level, AirKota.Hidup kota) {
		Map<BlockPos, PerahuKanalEntity> live = LAPAK.computeIfAbsent(kota.situs, k -> new HashMap<>());
		live.values().removeIf(Entity::isRemoved);
		int jam = AirKota.jamHari(level);
		boolean pasar = jam >= 6 && jam < 10 || TikusAir.berjalan(kota.situs);
		if (!pasar) {
			for (PerahuKanalEntity e : live.values()) {
				if (!TikusAir.sasaran(e)) {
					TemaMekanisme.lepas(e);
				}
			}
			live.values().removeIf(Entity::isRemoved);
			return;
		}
		List<Titik> moorings = new ArrayList<>();
		for (Titik t : AirKota.titik(level, kota, "tambatan")) {
			if ("lapak".equals(t.data().getOrDefault("kelas", ""))) {
				moorings.add(t);
			}
		}
		List<ServerPlayer> players = level.players();
		moorings.sort((a, b) -> Double.compare(jarakPemain(a.pos(), players), jarakPemain(b.pos(), players)));
		int n = 0;
		for (Titik t : moorings) {
			if (n >= LAPAK_MAKS || jarakPemain(t.pos(), players) > 64.0D * 64.0D) {
				break;
			}
			n++;
			if (live.containsKey(t.pos())) {
				continue;
			}
			Vec3 at = new Vec3(t.pos().getX() + 0.5D, kota.permukaan(), t.pos().getZ() + 0.5D);
			PerahuKanalEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_KANAL, at, t.hadap().toYRot(), kota.kota, t.pos());
			if (boat != null) {
				boat.setVarian(PerahuKanalEntity.VARIAN_LAPAK);
				boat.setKeadaan(PerahuKanalEntity.KEADAAN_TAMBAT);
				live.put(t.pos(), boat);
			}
		}
		// boats now beyond the four nearest go home
		for (Iterator<Map.Entry<BlockPos, PerahuKanalEntity>> it = live.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, PerahuKanalEntity> e = it.next();
			if (jarakPemain(e.getKey(), players) > 80.0D * 80.0D && !TikusAir.sasaran(e.getValue())) {
				TemaMekanisme.lepas(e.getValue());
				it.remove();
			}
		}
	}

	/** The live stall boats of a town (the Tikus Air pick one). */
	static List<PerahuKanalEntity> lapakHidup(long situs) {
		Map<BlockPos, PerahuKanalEntity> live = LAPAK.get(situs);
		if (live == null) {
			return List.of();
		}
		List<PerahuKanalEntity> out = new ArrayList<>();
		for (PerahuKanalEntity e : live.values()) {
			if (!e.isRemoved()) {
				out.add(e);
			}
		}
		return out;
	}

	private static double jarakPemain(BlockPos pos, List<ServerPlayer> players) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			best = Math.min(best, p.blockPosition().distSqr(pos));
		}
		return best;
	}

	/** True while the player is on one of this theme's boats (tour, hire, Perahu Pos). */
	public static boolean diPerahu(ServerPlayer player) {
		return player.getVehicle() instanceof PerahuKanalEntity || player.getVehicle() instanceof PerahuNagaEntity;
	}

	/** A player logged out: their tour or hired boat ends. */
	static void keluar(ServerPlayer player) {
		Tur tour = TUR.remove(player.getUUID());
		if (tour != null && player.level() instanceof ServerLevel level) {
			akhiriTur(level, tour, null, false);
		}
		Sewa hire = SEWA.remove(player.getUUID());
		if (hire != null) {
			TemaMekanisme.lepas(hire.perahu);
		}
	}

	/** The anchor of Bibi Chuan (the guide body of the tour); null when not loaded. */
	static @Nullable DuniaAnchorEntity sewaAnchor(AirKota.Hidup kota) {
		return kota.satu(AirAnchors.SEWA_PERAHU);
	}

	private Perahu() {
	}
}

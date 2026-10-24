package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.tema.TemaAnchors;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.bersama.PintuAirEntity;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The anchors {@code kota_tema/<what>} of {@code tools/gen_tempat_tema_air.py} served by this theme (registered with
 * {@code TemaAnchors.daftar}; {@code situs}, {@code tiba}, {@code zona} are 38TK inti's, {@code kota/*} and
 * {@code kotabesar/*} their owners'). Template data values are strings; every anchor also carries {@code tema=air} and its
 * city-local position {@code ux, uz, uy}.
 * <pre>
 * taman         Taman Sang Sastrawan: Wen Shi (tema_bait), site "Taman Klasik" x1.2 ({@link PasanganBait})
 * sutra         Rumah Sutra Su: Nyonya Su Jin (tema_sutra), Su Jin'er at the red loom, the six looms (alat_tenun points)
 * teh_awan      Paviliun Teh Awan: Lu Yuqing (tema_teh)
 * pintu_air     the Pintu Air leaf (entity pintu_air, its own 05:00 / 21:00 schedule) and the gate customs
 * lukisan       the famous view of the twin bridges: a small title card "Kenangan Kampung Halaman" once per player
 * kuil_air      the shrine of the Water God by the lake (the prayer lanterns of the Arwah Kanal)
 * panggung_air  the stage on the lake: the lantern-night singer and musicians ({@link LenteraAir})
 * perahu_naga   the dragon-boat house: two idle dragon boats at their moorings ({@link LombaPerahuNaga})
 * halte         the central Perahu Pos stop (ticket office); the boat itself runs from {@link Perahu}
 * sewa_perahu   the boat hire: Bibi Chuan (hire, Tur Kanal)
 * </pre>
 * Bodies and mechanisms are {@code noSave}, made when a player is near (inti's caps, {@code TemaMekanisme}) and removed
 * when the anchor unloads.
 */
public final class AirAnchors implements AnchorHandler {

	public static final String TAMAN = "taman";
	public static final String SUTRA = "sutra";
	public static final String TEH_AWAN = "teh_awan";
	public static final String PINTU_AIR = "pintu_air";
	public static final String LUKISAN = "lukisan";
	public static final String KUIL_AIR = "kuil_air";
	public static final String PANGGUNG_AIR = "panggung_air";
	public static final String PERAHU_NAGA = "perahu_naga";
	public static final String HALTE = "halte";
	public static final String SEWA_PERAHU = "sewa_perahu";
	public static final List<String> SEMUA = List.of(TAMAN, SUTRA, TEH_AWAN, PINTU_AIR, LUKISAN, KUIL_AIR, PANGGUNG_AIR, PERAHU_NAGA,
			HALTE, SEWA_PERAHU);

	/** Looms are made only when a player stands this close to the silk house. */
	private static final double JARAK_TENUN = 40.0D;
	private static final double JARAK_LUKISAN = 3.5D;
	/** Customs reach of the Pintu Air and the repeat time of the duty for one player. */
	private static final double JARAK_BEA = 9.0D;
	private static final long BEA_ULANG = 6000L;

	static final AirAnchors HANDLER = new AirAnchors();

	/** Bodies made per anchor: anchor uuid -> key -> entity. */
	private final Map<UUID, Map<String, Entity>> buatan = new HashMap<>();
	/** Towns whose canal splines a player already got this session. */
	private final Set<String> splineDikirim = new HashSet<>();
	/** Last customs check per player. */
	private final Map<UUID, Long> bea = new HashMap<>();

	static void init() {
		for (String what : SEMUA) {
			TemaAnchors.daftar(what, HANDLER);
		}
	}

	static void reset() {
		HANDLER.buatan.clear();
		HANDLER.splineDikirim.clear();
		HANDLER.bea.clear();
	}

	@Override
	public int radius() {
		return 96;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		AirKota.daftar(level, anchor);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!AirKota.aktif()) {
			return;
		}
		AirKota.Hidup kota = AirKota.daftar(level, anchor);
		if (kota == null) {
			return;
		}
		kirimSpline(kota, within);
		String what = anchor.what();
		try {
			switch (what) {
				case TAMAN -> tokoh(level, kota, anchor, AirTokoh.WEN_SHI, within, 48.0D);
				case SUTRA -> {
					tokoh(level, kota, anchor, AirTokoh.SU_JIN, within, 48.0D);
					RumahSutra.dekat(level, kota, anchor, within);
				}
				case TEH_AWAN -> tokoh(level, kota, anchor, AirTokoh.LU_YUQING, within, 48.0D);
				case SEWA_PERAHU -> tokoh(level, kota, anchor, AirTokoh.BIBI_CHUAN, within, 48.0D);
				case PINTU_AIR -> pintuAir(level, kota, anchor, within);
				case LUKISAN -> lukisan(level, anchor, within);
				case PANGGUNG_AIR -> LenteraAir.panggungDekat(level, kota, anchor, within);
				case PERAHU_NAGA -> LombaPerahuNaga.rumahDekat(level, kota, anchor, within);
				case KUIL_AIR, HALTE -> {
				}
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kota Air anchor {} failed", what, exception);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		AirKota.lupakan(anchor);
		Map<String, Entity> made = buatan.remove(anchor.getUUID());
		if (made != null) {
			for (Entity e : made.values()) {
				buang(e);
			}
		}
		if (SUTRA.equals(anchor.what())) {
			RumahSutra.lepas(anchor);
		}
	}

	// ================================================================== bodies

	static void buang(@Nullable Entity e) {
		if (e == null || e.isRemoved()) {
			return;
		}
		if (e instanceof KultivatorNpc npc) {
			npc.vanish();
		} else {
			TemaMekanisme.lepas(e);
		}
	}

	/** The body {@code key} this anchor made, or null. */
	@Nullable Entity buatan(DuniaAnchorEntity anchor, String key) {
		Map<String, Entity> map = buatan.get(anchor.getUUID());
		Entity e = map == null ? null : map.get(key);
		return e == null || e.isRemoved() ? null : e;
	}

	void simpan(DuniaAnchorEntity anchor, String key, Entity e) {
		buatan.computeIfAbsent(anchor.getUUID(), k -> new HashMap<>()).put(key, e);
	}

	/** The named character of a place (spawned once while a player is within {@code r}). */
	void tokoh(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, String id, List<ServerPlayer> within, double r) {
		if (buatan(anchor, id) != null || within.isEmpty() || within.getFirst().distanceToSqr(anchor) > r * r) {
			return;
		}
		KultivatorNpc npc = AirTokoh.panggil(level, kota.situs, id, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			if (kota.town != 0L) {
				npc.setTown(kota.town);
			}
			simpan(anchor, id, npc);
		}
	}

	/** A themed mechanism for this anchor under key {@code key} (once; inti's caps). */
	<T extends TemaMekanismeEntity> @Nullable T mekanisme(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, String key,
			EntityType<T> type, Vec3 pos, float yaw, BlockPos ikat) {
		Entity old = buatan(anchor, key);
		if (old != null) {
			@SuppressWarnings("unchecked")
			T same = (T) old;
			return same;
		}
		T e = TemaMekanisme.muncul(level, type, pos, yaw, kota.kota, ikat);
		if (e != null) {
			simpan(anchor, key, e);
		}
		return e;
	}

	/** Removes one body of an anchor. */
	void hapus(DuniaAnchorEntity anchor, String key) {
		Map<String, Entity> map = buatan.get(anchor.getUUID());
		if (map != null) {
			buang(map.remove(key));
		}
	}

	// ================================================================== Pintu Air

	/**
	 * The water-gate leaf (its 05:00 / 21:00 schedule is the entity's own, 38TK inti) and the Pintu Air customs: a player
	 * passing within {@link #JARAK_BEA} blocks of the gate pays inti's duty {@code Dagang.bea(.., "air")} once per
	 * {@link #BEA_ULANG} ticks (smuggled goods are seized there).
	 */
	private void pintuAir(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		PintuAirEntity pintu = mekanisme(level, kota, anchor, PINTU_AIR, TemaEntities.PINTU_AIR, anchor.position(), anchor.getYRot(),
				anchor.blockPosition());
		if (pintu != null) {
			pintu.setVarian(PintuAirEntity.VARIAN_KANAL);
		}
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > JARAK_BEA * JARAK_BEA) {
				continue;
			}
			Long last = bea.get(player.getUUID());
			if (last != null && now - last < BEA_ULANG) {
				continue;
			}
			bea.put(player.getUUID(), now);
			dev.kultivator.dunia.tempat.tema.bersama.Dagang.HasilBea r = dev.kultivator.dunia.tempat.tema.bersama.Dagang.bea(player, kota.situs, "air");
			if (r.disita() > 0 || r.bea() > 0L) {
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.bea", r.disita(), dev.kultivator.dunia.kota.NilaiBatu.text(r.bea()))
						.withStyle(r.disita() > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
			}
		}
	}

	/** True while the Pintu Air of the town stands open (boats of the Perahu Pos and the race use the Kanal Utama anyway). */
	public static boolean pintuTerbuka(ServerLevel level) {
		int jam = AirKota.jamHari(level);
		return jam >= 5 && jam < 21;
	}

	// ================================================================== the famous view

	private void lukisan(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > JARAK_LUKISAN * JARAK_LUKISAN) {
				continue;
			}
			var me = AirKota.pemain(level.getServer(), player.getUUID());
			if (me.getBooleanOr("lukisan", false)) {
				continue;
			}
			me.putBoolean("lukisan", true);
			AirKota.ubah(level.getServer());
			Msg.title(player, Component.translatable("kultivator.kota_tema.air.lukisan.judul").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.kota_tema.air.lukisan.sub").withStyle(ChatFormatting.GRAY), 20, 70, 30);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
			AirCerita.peristiwa(player, "air_lukisan", "jembatan_kembar");
		}
	}

	// ================================================================== client splines

	private void kirimSpline(AirKota.Hidup kota, List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			String key = player.getUUID() + "/" + kota.situs;
			if (splineDikirim.add(key)) {
				TemaNet.kirim(player, TemaNet.SPLINE, kota.situs, JalurKanal.spline(kota));
			}
		}
	}

	/** A player left: their splines are sent again on the next visit. */
	static void lupakanPemain(UUID id) {
		String prefix = id + "/";
		HANDLER.splineDikirim.removeIf(k -> k.startsWith(prefix));
	}

	// ================================================================== helpers

	/** An int value of the anchor data (template strings). */
	static int angka(DuniaAnchorEntity anchor, String key, int fallback) {
		String raw = anchor.data().getStringOr(key, "");
		if (raw.isEmpty()) {
			return anchor.data().getIntOr(key, fallback);
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** The life points of a kind within {@code r} of {@code pos} (loaded pieces only). */
	static List<Titik> titikDekat(ServerLevel level, AirKota.Hidup kota, String jenis, BlockPos pos, double r) {
		List<Titik> out = new ArrayList<>();
		for (Titik t : AirKota.titik(level, kota, jenis)) {
			if (t.pos().distSqr(pos) <= r * r) {
				out.add(t);
			}
		}
		return out;
	}

	static double jarakTenun() {
		return JARAK_TENUN;
	}

	private AirAnchors() {
	}
}

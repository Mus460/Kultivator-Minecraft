package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.laut.SeaGate;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kapal Lintas Laut (DESIGN 38.T.3.4, 38.T.11 row "Laut Tanpa Batas"): Kapten Gao Fan's junk leaves the Pos Kapal Lintas
 * Laut once a world day at 12000 for the Laut Tanpa Batas.
 * <ul>
 * <li><b>Ticket</b> ({@code tema_kapal} tab {@code lintas}, op {@code lintas:MURID}): band 2+ (Fondasi), not wanted in
 * this town, one crossing per player per world day; fare {@code 40 x (1 + band/4)} NB for the player and the same for
 * each of up to two disciples who come along. The title "Penjaga Pelita" halves it; stage 5 of
 * {@code tema_pelabuhan_pelita} sails free (flag {@code pelabuhan_lintas_gratis}, used once). A ticket bought after
 * the day's ship left is for the next one; a passenger who is not at the quay when the ship leaves keeps the ticket for
 * the next day.</li>
 * <li><b>Departure</b> (from the {@code pelabuhan/lintas_laut} anchor): at 12000 every ticket holder within
 * {@value #JARAK_DERMAGA} blocks of the quay boards. A storm ({@code isThundering}, read only) delays the ship until it
 * clears, the same day until 18000. Each passenger is held in {@code tema.pelayaran} ({@link TemaSibuk}), gets the
 * scene {@code tema_pelabuhan_lintas} (one short camera shot toward the junk sailing into the fog plus a title card,
 * skippable and only when safe: the 7b rules of {@code AdeganPayload}) and after {@value #ADEGAN} ticks
 * {@code SeaGate.enter(player, true)} (the "free" crossing: no realm gate and no pearl, the ship is the way). The chosen
 * disciples (nearest following ones, at most 2) are sent along to the dock; the others stay behind (a follow veto
 * while the hold lasts). The way home is the existing Gerbang Pulang ({@code extra.laut.ret} = this quay).</li>
 * </ul>
 */
public final class LintasLaut {

	public static final long HARGA_DASAR = 40L;
	public static final int PITA_MIN = 2;
	public static final int MURID_MAKS = 2;
	public static final int ADEGAN = 120;
	public static final double JARAK_DERMAGA = 32.0D;
	public static final String GRATIS = "pelabuhan_lintas_gratis";

	/** One passenger on the way (memory; a reload simply lets the player sail the next day). */
	private record Penumpang(UUID pemain, long mulai, int murid, long situs) {
	}

	private static final Map<UUID, Penumpang> JALAN = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(LintasLaut::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JALAN.clear());
		Disciples.addFollowVeto((player, origin, destination) ->
				TemaSibuk.sedang(player, TemaSibuk.PELAYARAN) ? "kultivator.kota_tema.pelabuhan.lintas.murid_tinggal" : null);
	}

	// ================================================================== fare and ticket

	/** The fare for one head for this player (title and quest discounts applied). */
	public static long ongkos(ServerPlayer player) {
		int band = Barang.pita(player);
		long fare = HARGA_DASAR * (4L + band) / 4L;
		if (CeritaApi.bendera(player, "gelar.penjaga_pelita")) {
			fare = (fare + 1L) / 2L;
		}
		return fare;
	}

	/** Why the player cannot buy a ticket here now, or null. */
	public static @Nullable Component alasan(ServerPlayer player, long situs) {
		if (!PelabuhanKota.cfg().lintasLaut || !PelabuhanKota.aktif()) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.lintas.tutup");
		}
		if (Barang.pita(player) < PITA_MIN) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.pita", PITA_MIN);
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		if (KotaBesarApi.buronan(player, kota != null ? kota.town : situs)) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.lintas.buronan");
		}
		if (punyaTiket(player, situs)) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.lintas.sudah", jamBerangkat());
		}
		CompoundTag p = PelabuhanKota.pemain(player.level().getServer(), player.getUUID());
		if (p.getLongOr("lintas_terakhir", Long.MIN_VALUE) == PelabuhanKota.hari(player.level())) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.lintas.sekali");
		}
		return null;
	}

	/** True when the player holds a ticket of this town. */
	public static boolean punyaTiket(ServerPlayer player, long situs) {
		CompoundTag t = PelabuhanKota.pemain(player.level().getServer(), player.getUUID()).getCompoundOrEmpty("tiket_lintas");
		return t.getLongOr("situs", 0L) == situs && t.contains("hari");
	}

	/** "12.00". */
	static Component jamBerangkat() {
		return Component.literal("12.00");
	}

	/** The disciples near the player that would come along (following ones within 16 blocks, nearest first). */
	static List<DiscipleEntity> muridIkut(ServerPlayer player) {
		List<DiscipleEntity> out = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			if (d.level() == player.level() && d.isAlive() && d.mode().follows() && d.distanceToSqr(player) <= 16.0D * 16.0D) {
				out.add(d);
			}
		}
		out.sort(Comparator.comparingDouble(d -> d.distanceToSqr(player)));
		return out;
	}

	/** Buys the ticket for the player and {@code murid} disciples; the result line. */
	static Component pesan(ServerPlayer player, long situs, int murid) {
		Component why = alasan(player, situs);
		if (why != null) {
			return why;
		}
		int n = Math.max(0, Math.min(MURID_MAKS, murid));
		n = Math.min(n, muridIkut(player).size());
		MinecraftServer server = player.level().getServer();
		boolean gratis = CeritaApi.bendera(player, GRATIS);
		long total = gratis ? 0L : ongkos(player) * (1L + n);
		if (!EkonomiApi.bayar(player, total, "pelabuhan.lintas")) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang");
		}
		if (gratis) {
			CeritaApi.setBendera(player, GRATIS, false);
		}
		EkonomiApi.kasKota(situs, total / 5L);
		long today = PelabuhanKota.hari(player.level());
		long day = PelabuhanKota.jam(player.level()) >= JadwalKapal.LINTAS_BERANGKAT && sudahBerangkat(server, situs, today) ? today + 1L : today;
		CompoundTag t = new CompoundTag();
		t.putLong("situs", situs);
		t.putLong("hari", day);
		t.putInt("murid", n);
		PelabuhanKota.pemain(server, player.getUUID()).put("tiket_lintas", t);
		PelabuhanKota.ubah(server);
		return Component.translatable(day == today ? "kultivator.kota_tema.pelabuhan.lintas.tiket" : "kultivator.kota_tema.pelabuhan.lintas.tiket_besok",
				jamBerangkat(), NilaiBatu.text(total));
	}

	/** The {@code lintas} compound of the sheet. */
	static CompoundTag lembar(ServerPlayer player, long situs) {
		CompoundTag c = new CompoundTag();
		boolean gratis = CeritaApi.bendera(player, GRATIS);
		c.putLong(dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal.ONGKOS, gratis ? 0L : ongkos(player));
		Component why = alasan(player, situs);
		c.putBoolean(LembarKunci.BISA, why == null);
		if (why != null) {
			LembarKunci.teks(c, LembarKunci.ALASAN, why);
		}
		LembarKunci.teks(c, dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal.BERANGKAT, jamBerangkat());
		c.putInt(dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal.MURID, Math.min(MURID_MAKS, muridIkut(player).size()));
		c.putBoolean(dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal.BADAI, PelabuhanKota.badai(player.level()));
		if (gratis) {
			LembarKunci.teks(c, LembarKunci.CATATAN, Component.translatable("kultivator.kota_tema.pelabuhan.lintas.gratis"));
		}
		return c;
	}

	// ================================================================== departure

	private static boolean sudahBerangkat(MinecraftServer server, long situs, long day) {
		return PelabuhanKota.kota(server, situs).getLongOr("lintas_hari", Long.MIN_VALUE) >= day;
	}

	/** From the {@code pelabuhan/lintas_laut} anchor (every 20 ticks while a player is near). */
	static void dermagaDekat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, List<ServerPlayer> within) {
		if (!PelabuhanKota.cfg().lintasLaut) {
			return;
		}
		MinecraftServer server = level.getServer();
		long jam = PelabuhanKota.jam(level);
		long today = PelabuhanKota.hari(level);
		if (jam < JadwalKapal.LINTAS_BERANGKAT || jam >= JadwalKapal.LINTAS_KEMBALI || sudahBerangkat(server, kota.situs, today)
				|| PelabuhanKota.badai(level)) {
			return;
		}
		berangkat(level, anchor, kota, within, today);
	}

	/** Boards every ticket holder near the quay (also forced by {@code uji tema kapal lintas}). */
	static int berangkat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, List<ServerPlayer> within, long today) {
		MinecraftServer server = level.getServer();
		PelabuhanKota.kota(server, kota.situs).putLong("lintas_hari", today);
		PelabuhanKota.ubah(server);
		KultivatorNpc gao = null;
		int boarded = 0;
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > JARAK_DERMAGA * JARAK_DERMAGA || !punyaTiket(player, kota.situs)) {
				continue;
			}
			CompoundTag p = PelabuhanKota.pemain(server, player.getUUID());
			CompoundTag t = p.getCompoundOrEmpty("tiket_lintas");
			if (t.getLongOr("hari", today) > today) {
				continue;
			}
			p.remove("tiket_lintas");
			p.putLong("lintas_terakhir", today);
			mulai(player, kota.situs, t.getIntOr("murid", 0), anchor.position());
			if (gao == null) {
				gao = PelabuhanAnchors.tokohDekat(player, PelabuhanTokoh.GAO_FAN, 40.0D);
			}
			boarded++;
		}
		// passengers who missed the ship keep the ticket for tomorrow
		for (ServerPlayer other : level.players()) {
			CompoundTag p = PelabuhanKota.pemain(server, other.getUUID());
			CompoundTag t = p.getCompoundOrEmpty("tiket_lintas");
			if (t.getLongOr("situs", 0L) == kota.situs && t.getLongOr("hari", today) <= today && !JALAN.containsKey(other.getUUID())) {
				t.putLong("hari", today + 1L);
			}
		}
		if (gao != null && VoiceEvents.exists("tema.pelabuhan.sea_crossing")) {
			Voice.say(gao, "tema.pelabuhan.sea_crossing", null);
		}
		PelabuhanKota.ubah(server);
		return boarded;
	}

	/** Starts one passenger's crossing: the hold, the scene, the fog. */
	static void mulai(ServerPlayer player, long situs, int murid, Vec3 kapal) {
		TemaSibuk.tahan(player, TemaSibuk.PELAYARAN, ADEGAN + 80);
		JALAN.put(player.getUUID(), new Penumpang(player.getUUID(), player.level().getGameTime(), murid, situs));
		AdeganPayload.kirim(player, "tema_pelabuhan_lintas", null, kapal.add(0.0D, 4.0D, 0.0D), 0);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ADEGAN, 2, false, false, false));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.0F, 0.8F);
		Msg.title(player, Component.translatable("kultivator.kota_tema.pelabuhan.lintas.judul").withColor(0x9FD8F0),
				Component.translatable("kultivator.kota_tema.pelabuhan.lintas.sub").withColor(0xD8ECF4), 10, 80, 30);
	}

	private static void tick(MinecraftServer server) {
		if (JALAN.isEmpty()) {
			return;
		}
		for (Iterator<Penumpang> it = JALAN.values().iterator(); it.hasNext(); ) {
			Penumpang p = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(p.pemain());
			if (player == null) {
				it.remove();
				continue;
			}
			long age = player.level().getGameTime() - p.mulai();
			if (age == ADEGAN / 2) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
			}
			if (age < ADEGAN) {
				continue;
			}
			it.remove();
			seberang(player, p);
		}
	}

	private static void seberang(ServerPlayer player, Penumpang p) {
		List<DiscipleEntity> along = muridIkut(player);
		int n = Math.min(p.murid(), along.size());
		boolean ok;
		try {
			ok = SeaGate.enter(player, true);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kapal Lintas Laut crossing failed", exception);
			ok = false;
		}
		MinecraftServer server = player.level().getServer();
		ServerPlayer now = server.getPlayerList().getPlayer(p.pemain());
		TemaSibuk.lepas(now != null ? now : player, TemaSibuk.PELAYARAN);
		if (!ok || now == null) {
			Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.lintas.gagal").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel sea = DimensiLevels.laut(server);
		if (sea != null) {
			for (int i = 0; i < n; i++) {
				Vec3 at = DimensiLevels.LAUT_ARRIVAL.add((i == 0 ? -2.0D : 2.0D), 1.0D, 1.5D);
				DimensiLevels.sendEntity(along.get(i), sea, at);
			}
		}
		// inti's goal kind kapal_lintas also grants tempat/tema_lintas_laut (TemaCerita)
		CeritaApi.peristiwa(now, "kapal_lintas", "laut");
	}

	private LintasLaut() {
	}
}

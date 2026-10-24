package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The capital's curfew (DESIGN 38.B.4.1): at 16000 (22.00) the night drum on the Drum Tower beats twelve times and the gates
 * swing shut over the next six seconds; at 1000 the morning bell rings three times and they open. A player left outside
 * knocks on the gate ({@link #ketuk}): the gate guard opens it for ten seconds for 2 NB, free for an Honoured citizen
 * (Nama Baik 50+), never for a wanted one or an Enemy of the City. Crossing the wall band anywhere else while the gates are
 * shut, and being seen by a guard, is the crime "lompat_tembok" (3 points, {@link Hukum}).
 * <p>
 * Runs from {@code KotaBesar}'s tick every 20 ticks; sounds play only for capitals with a player within
 * {@link KotaBesarKota#HIDUP} blocks. No block, time or weather is ever touched (the gates are entities).
 */
public final class JamMalam {

	/** 22.00 on the tick-0 = 06.00 clock that NPC schedules and {@link KotaBesarKalender#jam} use (F3: was 22000 = 04.00). */
	public static final long GENDERANG = 16000L;
	/** The gates are shut from here (the drum's six seconds later). */
	public static final long TUTUP = 16120L;
	public static final long BUKA = 1000L;
	/** A knock keeps the gate open this long. */
	public static final long KETUK_TICK = 200L;
	public static final long BIAYA_KETUK = 2L;
	/** Distance from an open gate within which a wall crossing is "through the gate". */
	private static final double LEWAT_GERBANG = 9.0D;

	private record Dentum(ServerLevel level, BlockPos pos, int sisa, long next, boolean genderang) {
	}

	private static final List<Dentum> DENTUM = new ArrayList<>();
	private static final Map<Long, Long> HARI_GENDERANG = new HashMap<>();
	private static final Map<Long, Long> HARI_LONCENG = new HashMap<>();
	/** Last known side of the wall per player (true = inside the walled core). */
	private static final Map<UUID, Boolean> SISI = new HashMap<>();
	/** Game time until which a player's wall crossing is not a crime (teleports, cells, gate passages). */
	private static final Map<UUID, Long> ABAIKAN = new HashMap<>();

	static void lupakan() {
		DENTUM.clear();
		HARI_GENDERANG.clear();
		HARI_LONCENG.clear();
		SISI.clear();
		ABAIKAN.clear();
	}

	/** Time of day in the overworld (0..23999). */
	static long jam(MinecraftServer server) {
		return Kota.timeOfDay(server);
	}

	/** True while the curfew holds the gates shut (16120 .. 1000, 22.07 to 07.00). */
	public static boolean malam(MinecraftServer server) {
		long t = jam(server);
		return t >= TUTUP || t < BUKA;
	}

	/** Whether gate {@code g} of town {@code town} should stand open now. */
	static boolean harusBuka(ServerLevel level, long town, GerbangKotaEntity g) {
		MinecraftServer server = level.getServer();
		if (g.roboh()) {
			return true;
		}
		if (GelombangBinatang.tutup(town)) {
			return g.ditahanBuka(level.getGameTime()) && !GelombangBinatang.serbu(town);
		}
		if (!KotaBesarConfig.get().jamMalam || !KotaBesarConfig.hidup()) {
			return true;
		}
		if (g.ditahanBuka(level.getGameTime())) {
			return true;
		}
		return !malam(server);
	}

	/** Lets {@code player} cross the wall without a crime for {@code ticks} (teleport arrival, cell, a knock). */
	public static void abaikan(ServerPlayer player, long ticks) {
		long until = player.level().getGameTime() + ticks;
		ABAIKAN.merge(player.getUUID(), until, Math::max);
	}

	// ================================================================== tick (every 20 ticks)

	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		long day = Kota.day(server);
		long t = jam(server);
		for (GerbangKotaEntity g : KotaBesarAnchors.gerbang()) {
			if (!(g.level() instanceof ServerLevel level)) {
				continue;
			}
			boolean want = harusBuka(level, g.town(), g);
			if (g.buka() != want && !g.roboh()) {
				g.setBuka(want);
			}
		}
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			if (level == null || kota.kandidat == null) {
				continue;
			}
			boolean dekat = adaPemain(level, kota.kandidat);
			if (t >= GENDERANG && HARI_GENDERANG.getOrDefault(kota.town, -1L) != day) {
				HARI_GENDERANG.put(kota.town, day);
				if (dekat && KotaBesarConfig.get().jamMalam && KotaBesarConfig.hidup()) {
					genderang(level, kota, now);
				}
			}
			if (t >= BUKA && t < GENDERANG && HARI_LONCENG.getOrDefault(kota.town, -1L) != day) {
				HARI_LONCENG.put(kota.town, day);
				if (dekat && KotaBesarConfig.hidup()) {
					lonceng(level, kota, now);
				}
			}
			if (dekat) {
				periksaTembok(level, kota, now);
			}
		}
	}

	/** Every server tick: the drum and bell beats (spaced 10 / 30 ticks). */
	static void ketukan(MinecraftServer server) {
		if (DENTUM.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<Dentum> next = new ArrayList<>();
		for (Iterator<Dentum> it = DENTUM.iterator(); it.hasNext(); ) {
			Dentum d = it.next();
			if (now < d.next()) {
				continue;
			}
			it.remove();
			BlockPos p = d.pos();
			if (d.genderang()) {
				d.level().playSound(null, p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D, SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
						SoundSource.BLOCKS, 4.0F, d.sisa() % 3 == 0 ? 0.5F : 0.6F);
			} else {
				d.level().playSound(null, p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS,
						4.0F, 0.7F);
			}
			if (d.sisa() > 1) {
				next.add(new Dentum(d.level(), p, d.sisa() - 1, now + (d.genderang() ? 10L : 30L), d.genderang()));
			}
		}
		DENTUM.addAll(next);
	}

	private static void genderang(ServerLevel level, KotaBesarAnchors.KotaHidup kota, long now) {
		BlockPos at = kota.genderang != null ? kota.genderang : KotaBesarKota.pusatDistrik(level, kota.kandidat, "pusat");
		DENTUM.add(new Dentum(level, at, 12, now, true));
		for (GerbangKotaEntity g : kota.gerbang) {
			KultivatorNpc guard = penjagaDekat(level, g.blockPosition(), 24.0D);
			if (guard != null) {
				guard.say(Component.translatable("kultivator.kota_besar.jam_malam.seru"), 80);
				Voice.say(guard, "kotabesar.curfew", null);
			}
		}
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(kota.kandidat, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				p.sendOverlayMessage(Component.translatable("kultivator.kota_besar.jam_malam.genderang").withStyle(ChatFormatting.GOLD));
			}
		}
		TempatEvents.fireAcara(level, kota.town, "jam_malam", true);
	}

	private static void lonceng(ServerLevel level, KotaBesarAnchors.KotaHidup kota, long now) {
		BlockPos at = kota.lonceng != null ? kota.lonceng : KotaBesarKota.pusatDistrik(level, kota.kandidat, "pusat");
		DENTUM.add(new Dentum(level, at, 3, now, false));
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(kota.kandidat, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				p.sendOverlayMessage(Component.translatable("kultivator.kota_besar.jam_malam.pagi").withStyle(ChatFormatting.YELLOW));
			}
		}
		TempatEvents.fireAcara(level, kota.town, "jam_malam", false);
		TempatEvents.fireAcara(level, kota.town, "lonceng_pagi", true);
	}

	private static boolean adaPemain(ServerLevel level, Kandidat k) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				return true;
			}
		}
		return false;
	}

	/** The nearest live guard (gate guard, town guard, captain) within {@code r} blocks, or null. */
	static @Nullable KultivatorNpc penjagaDekat(ServerLevel level, BlockPos at, double r) {
		KultivatorNpc best = null;
		double bestSq = r * r;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at).inflate(r),
				n -> n.isAlive() && (n.role() == NpcRole.PENJAGA_GERBANG || n.role() == NpcRole.PENJAGA || n.role() == NpcRole.KAPTEN_PENJAGA))) {
			double d = npc.distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
			if (d < bestSq) {
				bestSq = d;
				best = npc;
			}
		}
		return best;
	}

	// ================================================================== wall crossings

	private static void periksaTembok(ServerLevel level, KotaBesarAnchors.KotaHidup kota, long now) {
		Kandidat k = kota.kandidat;
		boolean semuaTutup = malam(level.getServer()) || GelombangBinatang.tutup(kota.town);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || KotaBesarKota.jarak(k, p.blockPosition()) > KotaBesarKota.TEMBOK_LUAR + 80) {
				continue;
			}
			if (KotaBesarKota.diTembok(k, p.blockPosition())) {
				continue;
			}
			boolean dalam = KotaBesarKota.dalamTembok(k, p.blockPosition());
			Boolean lalu = SISI.put(p.getUUID(), dalam);
			if (lalu == null || lalu == dalam || !semuaTutup || !KotaBesarConfig.get().hukum) {
				continue;
			}
			if (now < ABAIKAN.getOrDefault(p.getUUID(), 0L) || lewatGerbang(kota, p)) {
				continue;
			}
			Hukum.kejahatan(p, kota.town, Hukum.LOMPAT_TEMBOK, Hukum.POIN_LOMPAT_TEMBOK, p.blockPosition(), true);
		}
	}

	private static boolean lewatGerbang(KotaBesarAnchors.KotaHidup kota, ServerPlayer p) {
		for (GerbangKotaEntity g : kota.gerbang) {
			if (g.buka() && g.distanceTo(p) <= LEWAT_GERBANG + g.lebar()) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== knocking

	/** A player knocks on a shut gate (right click on the leaves). */
	static void ketuk(ServerLevel level, ServerPlayer player, GerbangKotaEntity g) {
		long town = g.town();
		if (g.buka()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.gerbang.sudah_buka"));
			return;
		}
		if (GelombangBinatang.tutup(town)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.gerbang.pertahanan").withStyle(ChatFormatting.RED));
			return;
		}
		KultivatorNpc guard = penjagaDekat(level, g.blockPosition(), 32.0D);
		if (Hukum.buronan(player, town)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.gerbang.tolak_buronan").withStyle(ChatFormatting.RED));
			if (guard != null) {
				Voice.say(guard, "kotabesar.wanted", null);
			}
			return;
		}
		int pangkat = KotaBesarApi.pangkatNamaBaik(player, town);
		if (pangkat <= LembarKunci.PANGKAT_MUSUH) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.gerbang.tolak_musuh").withStyle(ChatFormatting.RED));
			return;
		}
		boolean gratis = pangkat >= LembarKunci.PANGKAT_TERHORMAT;
		if (!gratis && !EkonomiApi.bayar(player, BIAYA_KETUK, "kotabesar.gerbang")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.gerbang.kurang", BIAYA_KETUK).withStyle(ChatFormatting.RED));
			return;
		}
		g.bukaSampai(level.getGameTime() + KETUK_TICK);
		abaikan(player, KETUK_TICK + 100L);
		player.sendSystemMessage(Component.translatable(gratis ? "kultivator.kota_besar.gerbang.dibuka_gratis"
				: "kultivator.kota_besar.gerbang.dibuka", BIAYA_KETUK).withStyle(ChatFormatting.GOLD));
		if (guard != null) {
			guard.say(Component.translatable("kultivator.kota_besar.gerbang.penjaga"), 60);
			Voice.say(guard, "kotabesar.gate_welcome", null);
		}
	}

	private JamMalam() {
	}
}

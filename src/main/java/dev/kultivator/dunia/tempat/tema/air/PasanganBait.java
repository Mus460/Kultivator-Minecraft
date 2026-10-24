package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pasangan Bait in the Taman Sang Sastrawan (DESIGN 38.T.5.4), screen {@code tema_bait} of the scholar Wen Shi (keys
 * {@code TemaLayar.Bait}): the first line of one of {@link #JUMLAH} couplets written for this game
 * ({@code kultivator.kota_tema.air.bait.<n>.a}, the matching second line {@code .b}), three second lines to choose from
 * (the right one and two of other couplets). Three right in a row: +1 comprehension of the player's Dao, at most once a
 * world day (the screen stays playable afterwards, without reward). While quest {@code tema_air_bait_tua} waits at stage
 * {@code bait}, Wen Shi's own lost last couplet ({@code bait.tua}) comes up first.
 * <p>
 * The garden is also an extra meditation site "Taman Klasik" x{@link #PENGALI_TAMAN} inside {@code SpiritSites.CAP} for
 * a player in district {@code taman_sastra}.
 */
public final class PasanganBait {

	public static final int JUMLAH = 48;
	public static final int BERUNTUN = 3;
	public static final double PAHAM = 1.0D;
	public static final double PENGALI_TAMAN = 1.2D;
	public static final String QUEST_TUA = "tema_air_bait_tua";
	public static final String TAHAP_TUA = "bait";

	/** One player's open round: couplet number (0 = Wen Shi's lost one), the three ids shown, the right id. */
	private record Soal(int no, List<String> pilihan, String benar) {
	}

	private static final Map<UUID, Soal> SOAL = new HashMap<>();
	private static final Map<UUID, Integer> BENAR = new HashMap<>();

	static void init() {
		TemaLayar.daftar(TemaLayar.Bait.LAYAR, PasanganBait::bangun, PasanganBait::aksi);
		SpiritSites.addSiteProvider(PasanganBait::situs);
	}

	static void reset() {
		SOAL.clear();
		BENAR.clear();
	}

	static void keluar(UUID id) {
		SOAL.remove(id);
		BENAR.remove(id);
	}

	private static SpiritSites.@Nullable ExtraSite situs(ServerPlayer player) {
		if (!AirKota.aktif() || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		AirKota.Hidup kota = AirKota.terdekat(level, player.position(), AirKota.JANGKAU_KOTA);
		if (kota == null || !"taman_sastra".equals(kota.distrik(player.blockPosition()))) {
			return null;
		}
		return new SpiritSites.ExtraSite("taman_klasik", PENGALI_TAMAN, null, Component.translatable("kultivator.kota_tema.air.situs.taman_klasik"));
	}

	// ================================================================== rounds

	private static Soal soalBaru(ServerPlayer player) {
		long seed = player.getUUID().getLeastSignificantBits() ^ player.level().getGameTime() * 0x9E3779B97F4A7C15L;
		java.util.Random random = new java.util.Random(seed);
		boolean tua = QuestTracker.aktif(player, QUEST_TUA) && TAHAP_TUA.equals(QuestTracker.simpul(player, QUEST_TUA));
		int no = tua ? 0 : 1 + random.nextInt(JUMLAH);
		String right = tua ? "tua" : Integer.toString(no);
		List<String> ids = new ArrayList<>();
		ids.add(right);
		while (ids.size() < 3) {
			String other = Integer.toString(1 + random.nextInt(JUMLAH));
			if (!ids.contains(other)) {
				ids.add(other);
			}
		}
		java.util.Collections.shuffle(ids, random);
		return new Soal(no, List.copyOf(ids), right);
	}

	private static String kunci(int no) {
		return "kultivator.kota_tema.air.bait." + (no == 0 ? "tua" : Integer.toString(no));
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = AirLayar.npc(player, npcId);
		if (npc == null || !AirTokoh.adalah(npc, AirTokoh.WEN_SHI)) {
			return null;
		}
		Soal soal = SOAL.computeIfAbsent(player.getUUID(), k -> soalBaru(player));
		CompoundTag tag = AirLayar.kepala(player, "kultivator.kota_tema.air.bait.judul", LembarKunci.GAYA_KERTAS, npc);
		LembarKunci.teks(tag, TemaLayar.Bait.BAIT, Component.translatable(kunci(soal.no) + ".a"));
		ListTag pilihan = new ListTag();
		for (String id : soal.pilihan) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, id);
			LembarKunci.teks(e, LembarKunci.TEKS, Component.translatable("kultivator.kota_tema.air.bait." + id + ".b"));
			pilihan.add(e);
		}
		tag.put(TemaLayar.Bait.PILIHAN, pilihan);
		tag.putInt(TemaLayar.Bait.BENAR, BENAR.getOrDefault(player.getUUID(), 0));
		tag.putInt(LembarKunci.MAKS, BERUNTUN);
		MinecraftServer server = player.level().getServer();
		tag.putBoolean(TemaLayar.Bait.HARI_INI, AirKota.pemain(server, player.getUUID()).getLongOr("bait_hari", -1L) == AirKota.hari(server.overworld()));
		return tag;
	}

	static void aksi(ServerPlayer player, int npcId, String op) {
		if (!TemaLayar.Bait.OP_PILIH.equals(LembarKunci.verb(op))) {
			return;
		}
		Soal soal = SOAL.get(player.getUUID());
		if (soal == null) {
			return;
		}
		String pick = LembarKunci.arg(LembarKunci.urai(op), 1);
		if (!soal.pilihan.contains(pick)) {
			return;
		}
		KultivatorNpc npc = AirLayar.npc(player, npcId);
		SOAL.put(player.getUUID(), soalBaru(player));
		if (!pick.equals(soal.benar)) {
			BENAR.put(player.getUUID(), 0);
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.bait.salah",
					Component.translatable(kunci(soal.no) + ".b")));
			if (npc != null) {
				npc.say(Component.translatable("kultivator.kota_tema.air.bait.wen_salah"), 60);
			}
			return;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
		if (soal.no == 0) {
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.bait.tua_ketemu"), true);
			AirCerita.peristiwa(player, "air_bait_tua", "ketemu");
			if (npc != null) {
				npc.say(Component.translatable("kultivator.kota_tema.air.bait.wen_tua"), 120);
			}
			return;
		}
		int streak = BENAR.getOrDefault(player.getUUID(), 0) + 1;
		if (streak < BERUNTUN) {
			BENAR.put(player.getUUID(), streak);
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.bait.benar", streak, BERUNTUN), true);
			return;
		}
		BENAR.put(player.getUUID(), 0);
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		long hari = AirKota.hari(server.overworld());
		AirCerita.peristiwa(player, "air_bait", "tiga");
		if (me.getLongOr("bait_hari", -1L) == hari) {
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.bait.tiga_lagi"), true);
			return;
		}
		me.putLong("bait_hari", hari);
		AirKota.ubah(server);
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao() != null ? data.dao() : Dao.LIFE;
		double got = Comprehension.grant(player, dao, PAHAM);
		AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.bait.tiga", Msg.number(got)), true);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.bait.tiga", Msg.number(got)).withStyle(ChatFormatting.GOLD));
	}

	private PasanganBait() {
	}
}

package dev.kultivator.sekte.gabung;

import dev.kultivator.duel.DuelIds;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.masuk.UjianCorak;
import dev.kultivator.sekte.gabung.politik.Kursi;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The two promotion trials that had no runner (D-41AD, 41.6.1): Murid Dalam and Tetua.
 * <ul>
 *   <li><b>Ujian Murid Dalam</b>: a friendly Sword Duel against an Inner Disciple of the sect (a roster member with role
 *       {@code dalam}, else a temporary examiner body), 3 minutes, stop at 10 %. Won, or the time runs out, or the examiner
 *       is down to half: passed.</li>
 *   <li><b>Ujian Tetua</b>: the sect's corak test again ({@link UjianCorak}) with the elder's bar of
 *       {@link #AMBANG_TETUA} instead of the 0.5 of the Luar re-test.</li>
 * </ul>
 * Both end in {@link Promosi#lulus}; [Ajukan Kenaikan] in the own grounds starts them while the trial is the only missing
 * requirement. No block changes, nobody dies.
 */
final class UjianPangkat {

	static final int DUEL_TICKS = 20 * 180;
	static final double AMBANG_TETUA = 0.85D;
	private static final String KEY = "kultivator.gabung.naik.";

	/** True when this class runs the trial of {@code tujuan}. */
	static boolean punya(Pangkat tujuan) {
		return tujuan == Pangkat.DALAM || tujuan == Pangkat.TETUA;
	}

	/** Starts the trial; false (with a chat line) when it cannot start now. */
	static boolean mulai(ServerPlayer player, String sekte, Pangkat tujuan) {
		String sibuk = Sibuk.why(player);
		if (sibuk != null) {
			Msg.chat(player, Sibuk.text(sibuk).copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		return tujuan == Pangkat.DALAM ? duelDalam(player, sekte) : ujianTetua(player, sekte);
	}

	private static boolean duelDalam(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		long hari = GabungApi.hari(server);
		List<Roster.Tokoh> calon = new ArrayList<>();
		for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
			if (t.aktif() && !t.luka(hari) && "dalam".equals(t.peran())) {
				calon.add(t);
			}
		}
		String id = sekte + "#penguji";
		Component nama = Component.translatable(KEY + "penguji_dalam");
		int step = DiplomasiGabung.langkahAnggota(server, sekte, 2);
		if (!calon.isEmpty()) {
			Roster.Tokoh t = calon.get(player.level().getRandom().nextInt(calon.size()));
			id = t.id();
			nama = t.nama();
			step = Kursi.langkahTokoh(server, sekte, id);
		}
		Component nm = nama;
		Component why = Kursi.tandingTokoh(player, sekte, id, nama, step, DUEL_TICKS,
				(p, menang, alasan, lawan) -> hasilDuel(p, menang, alasan, lawan));
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		Msg.chat(player, Component.translatable(KEY + "ujian_dalam.mulai", nm, DUEL_TICKS / 20).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static void hasilDuel(ServerPlayer player, boolean menang, int alasan, @Nullable LivingEntity lawan) {
		boolean separuh = lawan != null && lawan.getHealth() <= lawan.getMaxHealth() * 0.5F;
		if (menang || alasan == DuelIds.RES_WAKTU || separuh) {
			Promosi.lulus(player, Pangkat.DALAM);
		} else {
			Msg.chat(player, Component.translatable(KEY + "ujian_dalam.gagal").withStyle(ChatFormatting.RED));
		}
	}

	private static boolean ujianTetua(ServerPlayer player, String sekte) {
		Component gagal = UjianCorak.mulai(player, sekte, null, (p, hasil) -> {
			GabungApi.lepasSibuk(p, UjianCorak.SIBUK);
			if (!hasil.gagal() && hasil.skor() >= AMBANG_TETUA) {
				Promosi.lulus(p, Pangkat.TETUA);
			} else {
				Msg.chat(p, Component.translatable(KEY + "ujian_tetua.gagal", (int) Math.round(hasil.skor() * 100.0D),
						(int) Math.round(AMBANG_TETUA * 100.0D)).withStyle(ChatFormatting.RED));
			}
		});
		if (gagal != null) {
			Msg.chat(player, gagal.copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		Msg.chat(player, Component.translatable(KEY + "ujian_tetua.mulai", (int) Math.round(AMBANG_TETUA * 100.0D)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private UjianPangkat() {
	}
}

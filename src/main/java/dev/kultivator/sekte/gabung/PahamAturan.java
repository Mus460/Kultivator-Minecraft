package dev.kultivator.sekte.gabung;

import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * What reading the sect rules at the gate is worth (DESIGN 41.6.11 reads them at the oath, the design says nothing more, so
 * the simplest reward in line with the Balai Hukum is used). The story flag {@value #BENDERA} is set by the
 * {@code gabung_aturan_<aliran>} dialogs. When the player is accepted, the flag is turned into a stamp on the new membership
 * ({@code bagian("aturan").pahamSampai}, the world day it ends) and the flag is consumed, so a later membership has to read the
 * rules again. For {@link #HARI} world days the member "knew the rules": every offence adds half its points to the violation
 * record (at least 1; A2, the killing of a fellow member, is never halved). The Kartu Anggota shows the days left.
 */
public final class PahamAturan {

	/** Story flag set by the rules dialogs ({@code bendera gabung.aturan_dibaca}). */
	public static final String BENDERA = "gabung.aturan_dibaca";
	/** World days after joining during which the member counts as having known the rules. */
	public static final int HARI = 7;
	private static final String BAGIAN = "aturan";
	private static final String SAMPAI = "pahamSampai";

	/** Called by {@code GabungApi.terima} once the record is a member's: stamps the week and consumes the flag. */
	public static void saatMasuk(ServerPlayer player, Keanggotaan k) {
		MinecraftServer server = player.level().getServer();
		UUID uuid = player.getUUID();
		CompoundTag a = k.bagian(BAGIAN);
		a.remove(SAMPAI);
		CeritaData cerita = CeritaData.get(server);
		if (cerita.flag(uuid, BENDERA)) {
			a.putLong(SAMPAI, GabungApi.hari(server) + HARI);
			cerita.setFlag(uuid, BENDERA, false);
			Msg.chat(player, Component.translatable("kultivator.gabung.aturan.paham.mulai", HARI).withStyle(ChatFormatting.GREEN));
		}
	}

	/** World days of the knowing-the-rules week still to run (0 when it never began or is over). */
	public static int sisaHari(Keanggotaan k, long hari) {
		return (int) Math.max(0L, k.bagian(BAGIAN).getLongOr(SAMPAI, 0L) - hari);
	}

	/** The violation points an offence of {@code poin} really adds to the member's record. */
	public static int poin(Keanggotaan k, long hari, boolean pembunuhan, int poin) {
		return poin > 0 && !pembunuhan && sisaHari(k, hari) > 0 ? Math.max(1, poin / 2) : poin;
	}

	private PahamAturan() {
	}
}

package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * The tier exams shared by the craft guilds of the capital (DESIGN 38.M.2.1, 38.U.3.9 {@code gilda_alkemis} /
 * {@code gilda_tempa}): tier 1-9, an exam per tier with a checklist of conditions, a fee of {@code 5 × n²} NB (a sink into
 * the town treasury), once a world day. The guild's own class says what each condition is; this helper keeps the tier in
 * {@link EkonomiData}, builds the {@code ujian} block and runs the common part of the exam.
 */
final class GildaUjian {

	static final int TINGKAT_MAKS = 9;

	/** One condition of an exam: its line and whether the player meets it now. */
	record Syarat(Component teks, boolean ok) {
	}

	private GildaUjian() {
	}

	static int tingkat(ServerPlayer player, String kunci) {
		return Math.max(0, Math.min(TINGKAT_MAKS, EkonomiData.get(player.level().getServer()).pemain(player.getUUID()).getIntOr(kunci, 0)));
	}

	static void setTingkat(ServerPlayer player, String kunci, int tingkat) {
		EkonomiData data = EkonomiData.get(player.level().getServer());
		data.pemain(player.getUUID()).putInt(kunci, Math.max(0, Math.min(TINGKAT_MAKS, tingkat)));
		data.touch();
	}

	static long biaya(int tingkat) {
		return 5L * tingkat * tingkat;
	}

	/** True when the player has not sat an exam of this guild today. */
	static boolean bolehHariIni(ServerPlayer player, String kunci) {
		MinecraftServer server = player.level().getServer();
		return EkonomiData.get(server).harian(player.getUUID(), Barang.hari(server), "ujian_" + kunci) == 0;
	}

	/** The {@code ujian} block: next tier, the guild's conditions plus "once a day" and the fee. */
	static CompoundTag lembar(ServerPlayer player, String kunci, List<Syarat> syarat) {
		CompoundTag tag = new CompoundTag();
		int next = tingkat(player, kunci) + 1;
		if (next > TINGKAT_MAKS) {
			tag.putBoolean(LembarKunci.BISA, false);
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.gilda_kerajinan.tamat"));
			return tag;
		}
		tag.putInt(LembarKunci.TARGET, next);
		ListTag rows = new ListTag();
		boolean all = true;
		for (Syarat each : syarat) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.TEKS, each.teks());
			row.putBoolean(LembarKunci.OK, each.ok());
			rows.add(row);
			all &= each.ok();
		}
		boolean today = bolehHariIni(player, kunci);
		CompoundTag row = new CompoundTag();
		LembarKunci.teks(row, LembarKunci.TEKS, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.sekali"));
		row.putBoolean(LembarKunci.OK, today);
		rows.add(row);
		tag.put(LembarKunci.SYARAT, rows);
		long fee = biaya(next);
		tag.putLong(LembarKunci.BIAYA, fee);
		boolean bisa = all && today && NilaiBatu.total(player) >= fee;
		tag.putBoolean(LembarKunci.BISA, bisa);
		if (!bisa) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable(!all ? "kultivator.tempat_ekonomi.gilda_kerajinan.belum"
					: !today ? "kultivator.tempat_ekonomi.gilda_kerajinan.besok" : "kultivator.tempat_ekonomi.gilda_kerajinan.biaya"));
		}
		return tag;
	}

	/**
	 * Sits the exam: every condition met, the fee paid into the town treasury (and the sect ledger), the tier raised.
	 * {@code namaKunci} is the lang key of the guild's tier titles ({@code <namaKunci>.<n>}).
	 */
	static boolean ujian(ServerPlayer player, KultivatorNpc npc, String kunci, List<Syarat> syarat, String namaKunci, String advancement) {
		CompoundTag check = lembar(player, kunci, syarat);
		if (!check.getBooleanOr(LembarKunci.BISA, false)) {
			Lembar.pesan(player, LembarKunci.teks(check, LembarKunci.ALASAN), false);
			return false;
		}
		int next = tingkat(player, kunci) + 1;
		long fee = biaya(next);
		if (!NilaiBatu.pay(player, fee)) {
			Lembar.kurangUang(player, fee);
			return false;
		}
		MinecraftServer server = player.level().getServer();
		KasKota.tambah(server, npc.anchorSite() != 0L ? npc.anchorSite() : npc.town(), fee);
		if (Halls.hallOf(player.getUUID()) != null) {
			BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_UJIAN, 0, -fee, "kultivator.tempat_ekonomi.kas.catatan.ujian_kerajinan",
					next);
		}
		EkonomiData.get(server).tambahHarian(player.getUUID(), Barang.hari(server), "ujian_" + kunci, 1);
		setTingkat(player, kunci, next);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.gilda_kerajinan.lulus", Component.translatable(namaKunci + "." + next), next)
				.withStyle(ChatFormatting.GOLD));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.gilda_kerajinan.lulus.singkat", next), true);
		if (next >= 5) {
			Advancements.grant(player, advancement);
		}
		return true;
	}
}

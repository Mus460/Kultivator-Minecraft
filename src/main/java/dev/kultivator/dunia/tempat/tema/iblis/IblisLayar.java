package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sheet helpers of the five Kota Iblis screens (DESIGN 38.T.13.3; keys of {@code TemaLayar} and {@code LembarKunci},
 * drawn by 38TV-U on the "Pasar Giok" kit): the common header (title, style, money, stones, band, the town and its
 * Gentar/Infami line, the speaker card) and the one-line result kept per player for the next rebuild.
 */
final class IblisLayar {

	private static final Map<UUID, Object[]> PESAN = new HashMap<>();

	private IblisLayar() {
	}

	/** A new sheet with the common header; {@code npc} null = an anchor or entity screen without a speaker. */
	static CompoundTag kepala(ServerPlayer player, String judulKunci, String gaya, @Nullable KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(judulKunci));
		tag.putString(LembarKunci.GAYA, gaya);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		TemaKota.Kota kota = IblisKota.di(player);
		if (kota != null) {
			tag.putLong(LembarKunci.SITUS, kota.situsId());
			LembarKunci.teks(tag, LembarKunci.TEMPAT, kota.nama(player.level().getSeed()));
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota_tema.iblis.layar.meter",
					IblisMeter.gentar(player, kota.situsId()), IblisMeter.infami(player)));
		}
		if (npc != null) {
			CompoundTag card = new CompoundTag();
			LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
			String tokoh = TokohIblis.dari(npc);
			TokohIblis.Data data = TokohIblis.byId(tokoh);
			LembarKunci.teks(card, LembarKunci.GELAR, data != null ? Component.translatable(data.kunciGelar()) : Component.translatable(npc.role().titleKey()));
			card.putString(LembarKunci.PERAN, npc.role().name());
			card.putInt(LembarKunci.POTRET, npc.getId());
			if (!tokoh.isEmpty()) {
				card.putString(LembarKunci.TOKOH_ID, tokoh);
			}
			tag.put(LembarKunci.NPC, card);
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, (Component) pesan[0]);
			tag.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return tag;
	}

	/** Keeps the result line for the next sheet and shows it on the action bar. */
	static void pesan(ServerPlayer player, Component teks, boolean ok) {
		PESAN.put(player.getUUID(), new Object[] {teks, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA});
		Msg.actionBar(player, teks.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	static void peringatan(ServerPlayer player, Component teks) {
		PESAN.put(player.getUUID(), new Object[] {teks, LembarKunci.PESAN_PERINGATAN});
		Msg.actionBar(player, teks.copy().withStyle(ChatFormatting.YELLOW));
	}

	/** Locks the whole screen with a reason. */
	static void kunci(CompoundTag tag, Component alasan, @Nullable Component syarat) {
		CompoundTag kunci = new CompoundTag();
		LembarKunci.teks(kunci, LembarKunci.ALASAN, alasan);
		if (syarat != null) {
			LembarKunci.teks(kunci, LembarKunci.SYARAT, syarat);
		}
		tag.put(LembarKunci.KUNCI_LAYAR, kunci);
	}

	/** An empty-state card of the main list. */
	static void kosong(CompoundTag tag, Component teks) {
		CompoundTag k = new CompoundTag();
		LembarKunci.teks(k, LembarKunci.TEKS, teks);
		tag.put(LembarKunci.KOSONG, k);
	}

	/** A long argument of an op, or {@code fallback}. */
	static long angka(String[] parts, int i, long fallback) {
		if (i >= parts.length) {
			return fallback;
		}
		try {
			return Long.parseLong(parts[i].trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	static void lupakan(UUID id) {
		PESAN.remove(id);
	}
}

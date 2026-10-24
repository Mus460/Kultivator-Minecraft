package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sheet helpers of the three Kota Air screens built by 38TK-A ({@code tema_sutra} {@link RumahSutra}, {@code tema_teh}
 * {@link PaviliunTeh}, {@code tema_bait} {@link PasanganBait}; keys of {@code TemaLayar} and {@code LembarKunci}, drawn by
 * 38TV-U on the "Pasar Giok" kit): the common header (title, style, money, stones, band, Nama Baik, speaker card, place)
 * and the one-line result kept per player for the next rebuild.
 */
final class AirLayar {

	private static final Map<UUID, Object[]> PESAN = new HashMap<>();

	/** A new sheet with the common header; {@code npc} null = an anchor screen without a speaker. */
	static CompoundTag kepala(ServerPlayer player, String judulKunci, String gaya, @Nullable KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(judulKunci));
		tag.putString(LembarKunci.GAYA, gaya);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		AirKota.Hidup kota = AirKota.di(player);
		if (kota != null) {
			tag.putLong(LembarKunci.SITUS, kota.situs);
			LembarKunci.teks(tag, LembarKunci.TEMPAT, kota.kota.nama(player.level().getServer().overworld().getSeed()));
			tag.putInt(LembarKunci.NAMA_BAIK, AirKota.namaBaik(player, kota));
		}
		if (npc != null) {
			CompoundTag card = new CompoundTag();
			LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
			LembarKunci.teks(card, LembarKunci.GELAR, Component.translatable(npc.role().titleKey()));
			card.putString(LembarKunci.PERAN, npc.role().name());
			card.putInt(LembarKunci.POTRET, npc.getId());
			String tokoh = AirTokoh.dari(npc);
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

	/** The KultivatorNpc of a screen, or null. */
	static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId) {
		if (npcId < 0) {
			return null;
		}
		Entity e = player.level().getEntity(npcId);
		return e instanceof KultivatorNpc npc && !npc.isRemoved() ? npc : null;
	}

	/** Keeps the result line for the next sheet and shows it on the action bar. */
	static void pesan(ServerPlayer player, Component teks, boolean ok) {
		PESAN.put(player.getUUID(), new Object[] {teks, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA});
		Msg.actionBar(player, teks.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** A warning line (yellow). */
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

	static void lupakan(UUID id) {
		PESAN.remove(id);
	}

	private AirLayar() {
	}
}

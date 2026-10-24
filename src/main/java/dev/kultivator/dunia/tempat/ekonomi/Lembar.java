package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sheet helpers shared by every 38EK screen (keys of {@link LembarKunci}): the header (title, style, district, the
 * player's money and stones, band), the speaker card, the one-line result message and the NPC check of a builder
 * ("does this NPC serve this screen?"). Results of an action are kept per player for the next rebuild, so the screen
 * shows "Dibeli: Jian Roh Menengah" in its own message line instead of the chat.
 */
final class Lembar {

	private static final java.util.Map<java.util.UUID, Object[]> PESAN = new java.util.HashMap<>();

	/** The NPC of {@code npcId} when it has one of {@code peran}; null otherwise (the builder then refuses). */
	static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId, Set<NpcRole> peran) {
		if (npcId < 0) {
			return null;
		}
		Entity entity = player.level().getEntity(npcId);
		return entity instanceof KultivatorNpc npc && peran.contains(npc.role()) ? npc : null;
	}

	/** A new sheet with the common header. */
	static CompoundTag kepala(ServerPlayer player, String judulKunci, String gaya, @Nullable String distrik, @Nullable KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(judulKunci));
		tag.putString(LembarKunci.GAYA, gaya);
		if (distrik != null) {
			tag.putString(LembarKunci.DISTRIK, distrik);
		}
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		if (npc != null) {
			CompoundTag card = new CompoundTag();
			LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
			LembarKunci.teks(card, LembarKunci.GELAR, Component.translatable(npc.role().titleKey()));
			card.putString(LembarKunci.PERAN, npc.role().name());
			card.putInt(LembarKunci.POTRET, npc.getId());
			tag.put(LembarKunci.NPC, card);
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, (Component) pesan[0]);
			tag.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return tag;
	}

	/** Remembers the result line for the next sheet and shows it on the action bar too. */
	static void pesan(ServerPlayer player, Component teks, boolean ok) {
		PESAN.put(player.getUUID(), new Object[] {teks, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA});
		Msg.actionBar(player, teks.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** A warning line (yellow). */
	static void peringatan(ServerPlayer player, Component teks) {
		PESAN.put(player.getUUID(), new Object[] {teks, LembarKunci.PESAN_PERINGATAN});
		Msg.actionBar(player, teks.copy().withStyle(ChatFormatting.YELLOW));
	}

	/** "Not enough stones: 12 NB" (the town line). */
	static void kurangUang(ServerPlayer player, long harga) {
		pesan(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(harga)), false);
	}

	/** An empty-state block for the main list. */
	static void kosong(CompoundTag tag, String kunci) {
		CompoundTag kosong = new CompoundTag();
		LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(kunci));
		tag.put(LembarKunci.KOSONG, kosong);
	}

	/** Locks the whole screen with a reason and a requirement. */
	static void kunci(CompoundTag tag, Component alasan, @Nullable Component syarat) {
		CompoundTag kunci = new CompoundTag();
		LembarKunci.teks(kunci, LembarKunci.ALASAN, alasan);
		if (syarat != null) {
			LembarKunci.teks(kunci, LembarKunci.SYARAT, syarat);
		}
		tag.put(LembarKunci.KUNCI_LAYAR, kunci);
	}

	/** Forgets a player's pending message (logout). */
	static void lupakan(java.util.UUID id) {
		PESAN.remove(id);
	}

	/** Card state for a price: ok, not enough money, or locked. */
	static String keadaan(ServerPlayer player, long harga, boolean terkunci) {
		if (terkunci) {
			return LembarKunci.K_TERKUNCI;
		}
		return NilaiBatu.total(player) < harga ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
	}

	private Lembar() {
	}
}

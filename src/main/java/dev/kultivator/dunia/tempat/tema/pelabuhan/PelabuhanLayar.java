package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * Sheet helpers of the four harbour screens built by 38TK-P ({@code tema_kapal} {@link TokoPelabuhan},
 * {@code tema_galangan} {@link GalanganLayar}, {@code tema_dokumen} and {@code tema_racik} {@link TekaPelabuhan}; keys of
 * {@code TemaLayar} and {@code LembarKunci}, drawn by 38TV-U on the "Pasar Giok" kit): the common header (title, style,
 * place, money, stones, band, Nama Baik, speaker card) and the one-line result kept per player for the next rebuild.
 */
final class PelabuhanLayar {

	private static final Map<UUID, Object[]> PESAN = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PESAN.remove(handler.getPlayer().getUUID()));
	}

	/** A new sheet with the common header; {@code npc} null = an anchor or entity screen without a speaker. */
	static CompoundTag kepala(ServerPlayer player, String judulKunci, String gaya, @Nullable KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(judulKunci));
		tag.putString(LembarKunci.GAYA, gaya);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
		if (kota != null) {
			tag.putLong(LembarKunci.SITUS, kota.situs);
			LembarKunci.teks(tag, LembarKunci.TEMPAT, kota.nama(player.level().getServer()));
		}
		if (npc != null) {
			CompoundTag card = new CompoundTag();
			LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
			String tokoh = PelabuhanTokoh.dari(npc);
			PelabuhanTokoh.Data data = PelabuhanTokoh.byId(tokoh);
			LembarKunci.teks(card, LembarKunci.GELAR, data != null ? Component.translatable(data.kunciGelar()) : Component.translatable(npc.role().titleKey()));
			card.putString(LembarKunci.PERAN, npc.role().name());
			card.putInt(LembarKunci.POTRET, npc.getId());
			if (!tokoh.isEmpty()) {
				card.putString(LembarKunci.TOKOH_ID, tokoh);
			}
			tag.put(LembarKunci.NPC, card);
			long town = kota != null ? kota.town : npc.town();
			if (town != 0L) {
				tag.putInt(LembarKunci.NAMA_BAIK, KotaData.get(player.level().getServer()).reputation(town, player.getUUID()));
			}
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, (Component) pesan[0]);
			tag.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return tag;
	}

	/** The speaker of a screen session: the NPC with id {@code npcId} within reach, or null. */
	static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId) {
		if (npcId < 0) {
			return null;
		}
		Entity e = player.level().getEntity(npcId);
		return e instanceof KultivatorNpc npc && npc.isAlive() && npc.distanceToSqr(player) <= 12.0D * 12.0D ? npc : null;
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

	private PelabuhanLayar() {
	}
}

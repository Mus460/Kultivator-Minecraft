package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gentar and Infami of the Kota Iblis (DESIGN 38.T.7.8 butir 3-4). Both meters live in {@link KotaTemaData} (inti
 * stores and clamps; only inti and 38TK-I write them, 38.T contract). Infami always goes through
 * {@link TemaApi#tambahInfami} (the hub line and the log are inti's).
 * <ul>
 * <li>Gentar 0-100 per player per Kota Iblis: + arena wins, + Jam Berburu wins, + contracts, + chain chapters; -10 for a
 * refused blood bill (inti); -1 per 3 world days ({@link #susut}, applied when the player is next seen in town).</li>
 * <li>Thresholds: 30 fewer challengers and a market discount, 60 audience with the ruler and a free gate, 80 the title
 * "Yang Ditakuti di &lt;kota&gt;" and the enforcers bow ({@code hormat}).</li>
 * </ul>
 */
public final class IblisMeter {

	public static final int GENTAR_SEDIKIT = 30;
	public static final int GENTAR_AUDIENSI = 60;
	public static final int GENTAR_DITAKUTI = 80;
	/** World days per point of Gentar decay. */
	public static final int SUSUT_HARI = 3;

	private IblisMeter() {
	}

	public static int gentar(ServerPlayer player, long situsId) {
		return KotaTemaData.get(player.level().getServer()).gentar(player.getUUID(), situsId);
	}

	/** Adds Gentar in one town (after the pending decay) and tells the crossed thresholds. */
	public static int tambahGentar(ServerPlayer player, long situsId, int delta, String alasan) {
		MinecraftServer server = player.level().getServer();
		susut(player, situsId);
		KotaTemaData data = KotaTemaData.get(server);
		int before = data.gentar(player.getUUID(), situsId);
		int after = data.setGentar(player.getUUID(), situsId, before + delta);
		if (after == before) {
			return after;
		}
		Component line = Component.translatable(after > before ? "kultivator.kota_tema.iblis.gentar.naik" : "kultivator.kota_tema.iblis.gentar.turun",
				after - before, after, Component.translatable("kultivator.kota_tema.iblis.alasan." + alasan));
		player.sendSystemMessage(line.copy().withStyle(after > before ? ChatFormatting.DARK_RED : ChatFormatting.GRAY));
		for (int ambang : new int[] {GENTAR_SEDIKIT, GENTAR_AUDIENSI, GENTAR_DITAKUTI}) {
			if (before < ambang && after >= ambang) {
				player.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.gentar.ambang." + ambang).withStyle(ChatFormatting.RED));
			}
		}
		if (after >= GENTAR_DITAKUTI) {
			CompoundTag p = IblisData.pemainKota(server, situsId, player.getUUID());
			if (!p.getBooleanOr("ditakuti", false)) {
				p.putBoolean("ditakuti", true);
				IblisData.ubah(server);
				Advancements.grant(player, "tempat/tema_iblis_ditakuti");
			}
		}
		return after;
	}

	/** Applies the pending decay (1 per {@value #SUSUT_HARI} days since the last one). */
	public static void susut(ServerPlayer player, long situsId) {
		MinecraftServer server = player.level().getServer();
		long hari = IblisKota.hari(player.level());
		CompoundTag p = IblisData.pemainKota(server, situsId, player.getUUID());
		long last = p.getLongOr("gentar_hari", -1L);
		if (last < 0L) {
			p.putLong("gentar_hari", hari);
			IblisData.ubah(server);
			return;
		}
		long steps = (hari - last) / SUSUT_HARI;
		if (steps <= 0L) {
			return;
		}
		KotaTemaData data = KotaTemaData.get(server);
		data.setGentar(player.getUUID(), situsId, data.gentar(player.getUUID(), situsId) - (int) Math.min(100L, steps));
		p.putLong("gentar_hari", last + steps * SUSUT_HARI);
		IblisData.ubah(server);
	}

	public static int infami(ServerPlayer player) {
		return TemaApi.infami(player);
	}

	public static void tambahInfami(ServerPlayer player, int delta, String alasan) {
		TemaApi.tambahInfami(player, delta, alasan);
	}

	/** Gentar in the town the player stands in (else the nearest within 600), 0 when none. */
	public static int gentarDiSini(ServerPlayer player) {
		TemaKota.Kota kota = IblisKota.di(player);
		if (kota == null) {
			kota = IblisKota.terdekat(player.level(), player.blockPosition(), 600);
		}
		return kota == null ? 0 : gentar(player, kota.situsId());
	}
}

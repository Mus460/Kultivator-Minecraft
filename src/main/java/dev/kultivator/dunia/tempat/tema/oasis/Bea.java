package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.PetiDagangItem;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The customs desk of the Gerbang Giok (DESIGN 38.T.4.5, 38.T.10; anchor {@code tema_oasis/bea}): a player who comes
 * through the gate carrying trade goods of 38.T.10 (loose or in sealed crates) passes 38TK inti's customs
 * {@code Dagang.bea(player, situs, "giok")} once per town per world day: 3 % of the goods' base value into the town chest,
 * smuggled goods seized, waived by a Surat Jalan Gurun. Without the money the clerk only notes it (no debt, nothing taken).
 * The Surat Jalan itself is issued here ({@link #beriSurat}: 30 world days from the Tuan Benteng's audience, for ever
 * after the chain's ending; validity in the player's oasis record, {@link #suratSah}).
 * <p>
 * The desk is served by a desert guard body (role {@code PENGAWAL_GURUN}) made by the anchor, who reacts to the outcome;
 * the check runs by proximity (within {@link #JARAK} blocks of the desk) so a player never needs to talk to pass.
 */
public final class Bea {

	public static final int JARAK = 6;
	/** Days a Surat Jalan Gurun stays valid from the Tuan Benteng's audience. */
	public static final int SURAT_HARI = 30;

	private static final Map<UUID, KultivatorNpc> PENJAGA = new HashMap<>();

	static void reset() {
		PENJAGA.clear();
	}

	// ================================================================== Surat Jalan Gurun

	/** Gives the pass, valid {@link #SURAT_HARI} days ({@code selamanya}: for ever, the chain's reward). */
	public static void beriSurat(ServerPlayer player, boolean selamanya) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		long sampai = selamanya ? Long.MAX_VALUE : OasisKota.hari(level) + SURAT_HARI;
		if (me.getLongOr("surat_sampai", 0L) < sampai) {
			me.putLong("surat_sampai", sampai);
			OasisKota.ubah(server);
		}
		if (player.getInventory().countItem(TemaItems.SURAT_JALAN_GURUN) == 0) {
			ItemStack stack = new ItemStack(TemaItems.SURAT_JALAN_GURUN);
			OasisKota.beri(player, stack);
		}
		Msg.chat(player, Component.translatable(selamanya ? "kultivator.kota_tema.oasis.surat.selamanya" : "kultivator.kota_tema.oasis.surat.dapat",
				SURAT_HARI).withStyle(ChatFormatting.GOLD));
	}

	/** True while the player carries a valid Surat Jalan Gurun. */
	public static boolean suratSah(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || player.getInventory().countItem(TemaItems.SURAT_JALAN_GURUN) == 0) {
			return false;
		}
		return OasisKota.pemain(level.getServer(), player.getUUID()).getLongOr("surat_sampai", 0L) >= OasisKota.hari(level);
	}

	// ================================================================== the desk

	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		long situs = OasisAnchors.situsOf(level, anchor);
		KultivatorNpc guard = PENJAGA.get(anchor.getUUID());
		if (guard == null || guard.isRemoved() || !guard.isAlive()) {
			guard = HidupApi.tubuh(level, situs, NpcRole.PENGAWAL_GURUN, anchor.blockPosition(), anchor.getYRot());
			if (guard != null) {
				guard.memory().putBoolean("hold", true);
				SosokApi.gerak(guard, GerakId.SIKAP_TOMBAK, 0, 0, PropId.TOMBAK, 0, 0);
				PENJAGA.put(anchor.getUUID(), guard);
			}
		}
		MinecraftServer server = level.getServer();
		long hari = OasisKota.hari(level);
		for (ServerPlayer p : within) {
			if (p.isCreative() || p.isSpectator() || p.distanceToSqr(anchor) > (double) JARAK * JARAK) {
				continue;
			}
			if (!bawaDagang(p)) {
				continue;
			}
			CompoundTag me = OasisKota.pemain(server, p.getUUID());
			String key = "bea_" + situs;
			if (me.getLongOr(key, Long.MIN_VALUE) == hari) {
				continue;
			}
			// one customs check a day at this gate: 38TK inti's Dagang.bea (3 % duty on every trade good, smuggled goods
			// seized, the duty into the town chest, a Desert Pass waives the duty); the guard reacts to the outcome
			me.putLong(key, hari);
			OasisKota.ubah(server);
			Dagang.HasilBea hasil = Dagang.bea(p, situs, "giok");
			if (hasil.disita() > 0) {
				if (guard != null) {
					SosokApi.gerak(guard, GerakId.MARAH_TUNJUK, 0, 30);
					guard.say(Component.translatable("kultivator.kota_tema.oasis.bea.tegur"), 70);
				}
			} else if (hasil.bea() > 0L) {
				level.playSound(null, anchor.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.6F, 1.4F);
				if (guard != null) {
					guard.say(Component.translatable("kultivator.kota_tema.oasis.bea.terima"), 60);
				}
				QuestTracker.peristiwa(p, "oasis_bea", "bayar");
			} else if (hasil.bebas() && suratSah(p)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.bea.bebas").withStyle(ChatFormatting.GREEN));
				if (guard != null) {
					SosokApi.gerak(guard, GerakId.HORMAT, 0, 30);
				}
			} else if (guard != null) {
				// could not pay: the clerk notes it and lets him pass (no debt, 38.T.10)
				SosokApi.gerak(guard, GerakId.BERDEBAT, 0, 30);
			}
		}
	}

	/** True when the player carries a trade good of 38.T.10 (loose or in a sealed crate). */
	static boolean bawaDagang(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && (DagangItems.barang(stack.getItem()) != null || stack.get(PetiDagangItem.ISI) != null)) {
				return true;
			}
		}
		return false;
	}

	static void lepas(DuniaAnchorEntity anchor) {
		KultivatorNpc guard = PENJAGA.remove(anchor.getUUID());
		if (guard != null && !guard.isRemoved()) {
			guard.vanish();
		}
	}

	private Bea() {
	}
}

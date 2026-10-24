package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.util.Msg;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seized goods (DESIGN 38.R.3 rule 4): a block a player placed inside a cared-for place and the crew had to take away
 * is not lost: one of its item goes to the place's store in the placer's name (or "unknown" when nobody was seen). The
 * owner gets it back for free from the collector or the place's office within 14 world days; after that it is sold and
 * the money goes to the place's purse ({@code EkonomiApi.kasKota}). Nothing disappears silently.
 */
public final class Sitaan {

	/** World days a seized item waits for its owner. */
	public static final int HARI_SIMPAN = 14;
	/** Seized stacks one place keeps at most (the oldest are sold first). */
	public static final int MAKS = 256;

	/** Adds one item of {@code found}'s block for {@code pemilik} (null: unknown placer). */
	static void tambah(ServerLevel level, TempatRawat t, BlockState found, @Nullable UUID pemilik) {
		Item item = found.getBlock().asItem();
		if (item == Items.AIR) {
			return;
		}
		MinecraftServer server = level.getServer();
		PerbaikanData data = PerbaikanData.get(server);
		List<PerbaikanData.SitaanItem> list = data.sitaan.computeIfAbsent(t.situsId, k -> new ArrayList<>());
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		long day = Kota.day(server);
		for (int i = 0; i < list.size(); i++) {
			PerbaikanData.SitaanItem e = list.get(i);
			if (e.item().equals(id) && java.util.Objects.equals(e.pemilik(), pemilik) && e.jumlah() < item.getDefaultMaxStackSize()) {
				list.set(i, new PerbaikanData.SitaanItem(id, e.jumlah() + 1, pemilik, day));
				data.kotor();
				return;
			}
		}
		list.add(new PerbaikanData.SitaanItem(id, 1, pemilik, day));
		while (list.size() > MAKS) {
			jual(server, t.situsId, list.remove(0));
		}
		data.kotor();
	}

	/** The seized items of {@code owner} at place {@code situsId}. */
	static List<PerbaikanData.SitaanItem> milik(MinecraftServer server, long situsId, UUID owner) {
		List<PerbaikanData.SitaanItem> out = new ArrayList<>();
		for (PerbaikanData.SitaanItem e : PerbaikanData.get(server).sitaan.getOrDefault(situsId, List.of())) {
			if (owner.equals(e.pemilik())) {
				out.add(e);
			}
		}
		return out;
	}

	/** Gives every seized item of {@code player} at place {@code situsId} back (inventory, else dropped at his feet). */
	static int kembalikan(ServerPlayer player, long situsId) {
		MinecraftServer server = player.level().getServer();
		PerbaikanData data = PerbaikanData.get(server);
		List<PerbaikanData.SitaanItem> list = data.sitaan.get(situsId);
		if (list == null || list.isEmpty()) {
			return 0;
		}
		int given = 0;
		for (Iterator<PerbaikanData.SitaanItem> it = list.iterator(); it.hasNext(); ) {
			PerbaikanData.SitaanItem e = it.next();
			if (!player.getUUID().equals(e.pemilik())) {
				continue;
			}
			Identifier id = Identifier.tryParse(e.item());
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			it.remove();
			if (item == Items.AIR) {
				continue;
			}
			player.getInventory().placeItemBackInInventory(new ItemStack(item, e.jumlah()), Prediction.SERVER_ONLY);
			given += e.jumlah();
		}
		if (given > 0) {
			data.kotor();
			Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.sitaan.kembali", given).withColor(0xC9E7A8));
		}
		return given;
	}

	/** Daily: items older than {@link #HARI_SIMPAN} days are sold to the place's purse. */
	static void harian(MinecraftServer server) {
		long day = Kota.day(server);
		PerbaikanData data = PerbaikanData.get(server);
		boolean changed = false;
		for (Map.Entry<Long, List<PerbaikanData.SitaanItem>> entry : data.sitaan.entrySet()) {
			for (Iterator<PerbaikanData.SitaanItem> it = entry.getValue().iterator(); it.hasNext(); ) {
				PerbaikanData.SitaanItem e = it.next();
				if (day - e.hari() > HARI_SIMPAN) {
					it.remove();
					jual(server, entry.getKey(), e);
					changed = true;
				}
			}
		}
		data.sitaan.values().removeIf(List::isEmpty);
		if (changed) {
			data.kotor();
		}
	}

	private static void jual(MinecraftServer server, long situsId, PerbaikanData.SitaanItem e) {
		Identifier id = Identifier.tryParse(e.item());
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		if (item == Items.AIR) {
			return;
		}
		long worth = EkonomiApi.harga(new ItemStack(item, e.jumlah()));
		if (worth > 0L) {
			EkonomiApi.kasKota(situsId, Math.max(1L, worth / 3L));
		}
	}

	private Sitaan() {
	}
}

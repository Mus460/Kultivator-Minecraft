package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.PasarGelap;
import dev.kultivator.dunia.kota42.ekonomi.PasarHantu;
import dev.kultivator.dunia.kota42.ekonomi.LapakHantuEntity;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.PasarHantu.*;

/**
 * {@code pasar_hantu} (DESIGN 42.11.10, keys {@link KotaLembar.PasarHantu}) from 42C-2's {@code PasarHantu}: tonight's
 * Ghost Market at a masked seller's stall — the spot and the minutes to dawn, the mask (−10 %, safe from a raid), the raid
 * tension, whether the player came by the password; tab Lapak (tonight's wares, each sold once per player), tab Curian
 * (stolen goods on the table, the player's own marked, redeem at 1.2 x value) and tab Penadah (what the fence pays for
 * the bag, +1 infamy each). Opened by right-clicking a {@code lapak_hantu} ({@code PasarHantu.klik}); drawn on the client
 * as the Ghost Market mode of the black-market screen {@code LayarPasarGelap} (dark cloth, green accents).
 */
public final class LembarPasarHantu {

	private static final String K = LembarKotaDasar.K + "pasar_hantu.";
	/** Night ticks of a market (01:00-05:00). */
	private static final long MALAM = PasarHantu.TUTUP - PasarHantu.BUKA;

	static void init() {
		LembarKotaDasar.daftar(LAYAR, LembarPasarHantu::bangun);
		LembarKotaDasar.bawaan(LAYAR, OP_BELI, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				PasarHantu.beli(p, situs, LembarKunci.argInt(op, 1, -1)), Component.translatable(K + "beli_ok")));
		LembarKotaDasar.bawaan(LAYAR, OP_TEBUS, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				PasarHantu.tebus(p, situs, LembarKunci.argInt(op, 1, -1)), Component.translatable(K + "tebus_ok")));
		LembarKotaDasar.bawaan(LAYAR, OP_JUAL, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				PasarHantu.jual(p, situs, LembarKunci.argInt(op, 1, -1)), null));
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		Entity e = npcId >= 0 ? player.level().getEntity(npcId) : null;
		int varian = e instanceof LapakHantuEntity stall ? stall.varian() : 0;
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_KAIN);
		tag.putBoolean(KotaLembar.GELAP, true);
		LembarKotaDasar.kepala(tag, player, situs);
		if (situs == 0L || !PasarHantu.aktif() || !PasarHantu.adaPasar(server, situs)) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "tiada"));
		}
		long hari = Kota.day(server);
		long waktu = Kota.timeOfDay(server);
		boolean malam = PasarHantu.malam(server);
		int n = PasarHantu.lokasi(situs, hari);
		boolean topeng = PasarHantu.bertopeng(player);

		CompoundTag pasar = new CompoundTag();
		LembarKunci.teks(pasar, LOKASI, n < 0 ? Component.translatable(K + "lokasi_tiada") : PasarHantu.namaLokasi(PasarHantu.jenisLokasi(n)));
		pasar.putLong(BUBAR_TICK, malam ? Math.max(0L, PasarHantu.TUTUP - waktu) : 0L);
		pasar.putBoolean(TOPENG, topeng);
		pasar.putFloat(DISKON, topeng ? (float) PasarHantu.DISKON_TOPENG : 0.0F);
		// raid tension: a quiet night stays low; on a raid night it climbs toward dawn (the guards gather)
		float jalan = malam ? Math.min(1.0F, (waktu - PasarHantu.BUKA) / (float) MALAM) : 0.0F;
		pasar.putInt(RAZIA, PasarHantu.razia(situs, hari) ? Math.round(35 + 60 * jalan) : Math.round(5 + 10 * jalan));
		pasar.putBoolean(SANDI, PasarHantu.punyaSandi(player, situs));
		tag.put(PASAR, pasar);
		if (topeng) {
			tag.putFloat(DISKON, (float) PasarHantu.DISKON_TOPENG);
		}

		CompoundTag penjual = new CompoundTag();
		LembarKunci.teks(penjual, LembarKunci.NAMA, Component.translatable(K + "penjual." + Math.floorMod(varian, LapakHantuEntity.VARIAN_JUMLAH)));
		LembarKunci.teks(penjual, BISIK, Component.translatable(K + "bisik." + Math.floorMod(situs ^ hari ^ varian, 6L)));
		penjual.putInt(LembarKunci.POTRET, -1);
		tag.put(PENJUAL, penjual);

		List<CompoundTag> lapak = new ArrayList<>();
		for (PasarGelap.Offer o : PasarHantu.dagangan(player, situs)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(o.index()));
			LembarKunci.teks(t, LembarKunci.NAMA, o.stack().getHoverName());
			LembarKunci.barang(t, o.stack(), server.registryAccess());
			t.putLong(LembarKunci.HARGA, o.price());
			t.putInt(LembarKunci.STOK, o.stack().getCount());
			boolean terjual = PasarHantu.terjual(player, situs, o.index());
			t.putString(LembarKunci.KEADAAN, !malam ? LembarKunci.K_TUTUP : terjual ? LembarKunci.K_HABIS : LembarKunci.K_OK);
			t.putBoolean(LembarKunci.LANGKA, o.price() >= 100L);
			t.putInt(LAPAK, o.index() % 4);
			lapak.add(t);
		}
		tag.put(LembarKunci.DAFTAR, LembarKotaDasar.daftar(lapak));

		List<CompoundTag> curian = new ArrayList<>();
		for (PasarHantu.Curian c : PasarHantu.curian(server, situs)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(c.indeks()));
			LembarKunci.teks(t, LembarKunci.NAMA, c.barang().getHoverName());
			LembarKunci.barang(t, c.barang(), server.registryAccess());
			t.putLong(TEBUS, PasarHantu.hargaTebus(c.barang()));
			t.putLong(LembarKunci.HARI, c.hari());
			t.putBoolean(MILIKMU, c.pemilik().equals(player.getUUID()));
			curian.add(t);
		}
		// the player's own stolen goods first
		curian.sort((a, b) -> Boolean.compare(b.getBooleanOr(MILIKMU, false), a.getBooleanOr(MILIKMU, false)));
		tag.put(CURIAN, LembarKotaDasar.daftar(curian));

		List<CompoundTag> penadah = new ArrayList<>();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			long harga = PasarHantu.hargaPenadah(server, situs, stack);
			if (harga <= 0L) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putInt(LembarKunci.SLOT, slot);
			t.putString(LembarKunci.ID, Integer.toString(slot));
			LembarKunci.teks(t, LembarKunci.NAMA, stack.getHoverName());
			LembarKunci.barang(t, stack, server.registryAccess());
			t.putLong(LembarKunci.HARGA, harga);
			t.putInt(LembarKunci.JUMLAH, stack.getCount());
			t.putInt(INFAMI, 1);
			penadah.add(t);
		}
		tag.put(PENADAH, LembarKotaDasar.daftar(penadah));
		if (!malam) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(K + "fajar"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		return tag;
	}

	private LembarPasarHantu() {
	}
}

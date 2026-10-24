package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Pawn at the Rumah Uang (DESIGN 38.M.2.2): one ware in hand (the 38 sell filter) is held for a loan of 40 % of its value;
 * it is redeemed within 7 world days for 46 % (the 15 % interest is a sink); an unredeemed pledge is sold by the house
 * (gone). At most {@link #MAKS} pledges at once. A ware a 38 shop sold in the last 3 days is refused (no quick loop).
 */
public final class Gadai {

	public static final double PINJAM = 0.40D;
	public static final double TEBUS = 0.46D;
	public static final int HARI = 7;
	public static final int MAKS = 6;
	private static final String KUNCI = "gadai";

	/** The pledges of the player (expired ones are sold first). */
	static ListTag daftar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		ListTag list = data.daftar(player.getUUID(), KUNCI);
		long hari = Barang.hari(server);
		boolean changed = false;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (hari - entry.getLongOr("hari", hari) > HARI) {
				list.remove(i);
				changed = true;
				ItemStack lost = Barang.muat(entry.get("barang"), player.registryAccess());
				Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.bank.gadai.hangus", lost.getHoverName()).withStyle(ChatFormatting.GRAY));
			}
		}
		if (changed) {
			data.touch();
		}
		return list;
	}

	/**
	 * The pledge value of {@code stack}: its town value, times the price scale when that is below 1 (38.M.5: with a 0.5
	 * scale on both configs the shops sold at a quarter of the value and a 40 % loan on an unredeemed pledge paid more).
	 */
	static long nilaiGadai(ItemStack stack) {
		return Math.max(1L, Math.round(EkonomiApi.harga(stack) * stack.getCount() * Math.min(1.0D, Barang.skala())));
	}

	static long pinjaman(ItemStack stack) {
		return Math.max(1L, (long) Math.floor(nilaiGadai(stack) * PINJAM));
	}

	static long tebusan(long nilai) {
		return Math.max(1L, (long) Math.ceil(nilai * TEBUS));
	}

	/** Pledges the main-hand stack. */
	static void gadai(ServerPlayer player) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		MinecraftServer server = player.level().getServer();
		long hari = Barang.hari(server);
		if (!Barang.bolehDijual(hand) || Barang.baruDibeli(hand, hari)) {
			Lembar.pesan(player, Component.translatable(Barang.berisi(hand) ? "kultivator.ekonomi.cincin_berisi"
					: "kultivator.tempat_ekonomi.bank.gadai.tolak"), false);
			return;
		}
		ListTag list = daftar(player);
		if (list.size() >= MAKS) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.gadai.penuh", MAKS), false);
			return;
		}
		long nilai = nilaiGadai(hand);
		long loan = pinjaman(hand);
		EkonomiData data = EkonomiData.get(server);
		CompoundTag entry = new CompoundTag();
		entry.putLong("id", data.serial("gadai"));
		entry.putLong("hari", hari);
		entry.putLong("nilai", nilai);
		entry.putLong("pinjam", loan);
		entry.put("barang", Barang.simpan(hand.copy(), player.registryAccess()));
		Component name = hand.getHoverName();
		hand.setCount(0);
		list.add(entry);
		data.touch();
		NilaiBatu.give(player, loan);
		RumahUang.riwayat(player, loan, "kultivator.tempat_ekonomi.bank.riwayat.gadai", name.getString());
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.gadai.diterima", name, NilaiBatu.text(loan), HARI), true);
	}

	/** Redeems pledge {@code id}. */
	static void tebus(ServerPlayer player, long id) {
		ListTag list = daftar(player);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (entry.getLongOr("id", -1L) != id) {
				continue;
			}
			long price = tebusan(entry.getLongOr("nilai", 0L));
			if (!NilaiBatu.pay(player, price)) {
				Lembar.kurangUang(player, price);
				return;
			}
			ItemStack back = Barang.muat(entry.get("barang"), player.registryAccess());
			list.remove(i);
			EkonomiData.get(player.level().getServer()).touch();
			KasKota.tambah(player.level().getServer(), RumahUang.situsTerakhir(player), price - entry.getLongOr("pinjam", 0L));
			Barang.beri(player, back);
			RumahUang.riwayat(player, -price, "kultivator.tempat_ekonomi.bank.riwayat.tebus", back.getHoverName().getString());
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.gadai.ditebus", back.getHoverName(), NilaiBatu.text(price)), true);
			return;
		}
	}

	/** The {@code gadai} block of the bank sheet. */
	static CompoundTag lembar(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat(LembarKunci.Bank.PINJAM, (float) PINJAM);
		tag.putFloat(LembarKunci.Bank.TEBUS, (float) TEBUS);
		tag.putInt(LembarKunci.HARI, HARI);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (Barang.bolehDijual(hand) && !Barang.baruDibeli(hand, Barang.hari(player.level().getServer()))) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand, player.registryAccess());
			tangan.putLong(LembarKunci.NILAI, nilaiGadai(hand));
			tangan.putLong(LembarKunci.Bank.PINJAMAN, pinjaman(hand));
			tag.put(LembarKunci.TANGAN, tangan);
		}
		ListTag rows = new ListTag();
		long hari = Barang.hari(player.level().getServer());
		for (net.minecraft.nbt.Tag each : daftar(player)) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			ItemStack stack = Barang.muat(entry.get("barang"), player.registryAccess());
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Long.toString(entry.getLongOr("id", 0L)));
			LembarKunci.barang(row, stack, player.registryAccess());
			row.putLong(LembarKunci.Bank.PINJAMAN, entry.getLongOr("pinjam", 0L));
			row.putLong(LembarKunci.Bank.TEBUS_NB, tebusan(entry.getLongOr("nilai", 0L)));
			row.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, HARI - (hari - entry.getLongOr("hari", hari))));
			rows.add(row);
		}
		tag.put(LembarKunci.DAFTAR, rows);
		return tag;
	}

	private Gadai() {
	}
}

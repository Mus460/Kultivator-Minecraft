package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Brankas of the Rumah Uang (DESIGN 38.M.2.2): a free vault of 9 slots (18 for a known citizen, Nama Baik rank Warga or
 * better at the capital), one per player and shared by every Rumah Uang. Whole stacks are kept in SavedData; a filled
 * container (ring, pouch, shulker box, bundle) is refused so nothing can hide inside a vault slot.
 */
public final class Brankas {

	public static final int SLOT = 9;
	public static final int SLOT_WARGA = 18;
	private static final String KUNCI = "brankas";

	static int kapasitas(ServerPlayer player) {
		return RumahUang.wargaDikenal(player) ? SLOT_WARGA : SLOT;
	}

	private static CompoundTag isi(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).bagian(player.getUUID(), KUNCI);
	}

	/** Stores the main-hand stack in the first free slot. */
	static void simpan(ServerPlayer player) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (hand.isEmpty()) {
			return;
		}
		if (Barang.berisi(hand)) {
			Lembar.pesan(player, Component.translatable("kultivator.ekonomi.cincin_berisi"), false);
			return;
		}
		CompoundTag isi = isi(player);
		int cap = kapasitas(player);
		for (int slot = 0; slot < cap; slot++) {
			String key = Integer.toString(slot);
			if (!isi.contains(key)) {
				Component name = hand.getHoverName();
				isi.put(key, Barang.simpan(hand.copy(), player.registryAccess()));
				hand.setCount(0);
				EkonomiData.get(player.level().getServer()).touch();
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.brankas.simpan", name, slot + 1), true);
				return;
			}
		}
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.brankas.penuh", cap), false);
	}

	/** Takes slot {@code slot} back. */
	static void ambil(ServerPlayer player, int slot) {
		CompoundTag isi = isi(player);
		String key = Integer.toString(slot);
		if (slot < 0 || !isi.contains(key)) {
			return;
		}
		ItemStack stack = Barang.muat(isi.get(key), player.registryAccess());
		isi.remove(key);
		EkonomiData.get(player.level().getServer()).touch();
		Barang.beri(player, stack);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.brankas.ambil", stack.getHoverName()), true);
	}

	/** The {@code brankas} block of the bank sheet (slots beyond the capacity are still listed so nothing is lost). */
	static CompoundTag lembar(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(LembarKunci.SLOT, kapasitas(player));
		ListTag rows = new ListTag();
		CompoundTag isi = isi(player);
		for (int slot = 0; slot < SLOT_WARGA; slot++) {
			String key = Integer.toString(slot);
			if (!isi.contains(key)) {
				continue;
			}
			ItemStack stack = Barang.muat(isi.get(key), player.registryAccess());
			CompoundTag row = new CompoundTag();
			row.putInt(LembarKunci.SLOT, slot);
			LembarKunci.barang(row, stack, player.registryAccess());
			rows.add(row);
		}
		tag.put(LembarKunci.DAFTAR, rows);
		return tag;
	}

	private Brankas() {
	}
}

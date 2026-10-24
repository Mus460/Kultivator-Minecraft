package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.sect.event.Expeditions;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Loot of a journey (DESIGN 39.6.9): the expedition loot table ({@code Expeditions.lootTable}, public and shared) of the
 * matching kind (a hunt, a secret realm or ruins, herbs from a village), at most {@code rampasanMinggu} stacks per hall
 * and 7 days. A stage only books a draw (kind, tier, seed); the stacks are made at the gate when the disciple is home
 * (39.6.11 rule 2), the first ones into its bag and the rest to the Lumbung.
 */
final class Rampasan {

	/** Books up to {@code maks} loot stacks of {@code type} and {@code tier} for the leader. */
	static void ambil(Tahap.Konteks k, int type, int tier, int maks) {
		Rencana r = k.r();
		JalanData data = JalanData.get(k.server());
		int used = data.rampasanMinggu(r.owner(), k.hari());
		int room = Math.max(0, MandiriConfig.get().rampasanMinggu - used);
		int n = Math.min(maks, room);
		if (n <= 0) {
			r.kisah(JalanIds.k("kisah.rampasan.penuh"), List.of(k.namaPemimpin()));
			return;
		}
		data.tambahRampasan(r.owner(), k.hari(), n);
		ListTag list = r.hasil().getList("rampasan").orElse(null);
		if (list == null) {
			list = new ListTag();
			r.hasil().put("rampasan", list);
		}
		CompoundTag one = new CompoundTag();
		one.putInt("type", type);
		one.putInt("tier", Math.max(1, Math.min(9, tier)));
		one.putInt("n", n);
		one.putLong("seed", r.benih() ^ (k.index() + 7L) * 0x632BE59BD9B4E019L);
		list.add(one);
		r.kisah(JalanIds.k("kisah.rampasan." + type), List.of(k.namaPemimpin(), String.valueOf(n)));
	}

	/** Makes the booked loot (at most the booked count of each draw); empty when none. */
	static List<ItemStack> buat(Rencana r) {
		List<ItemStack> out = new ArrayList<>();
		ListTag list = r.hasil().getListOrEmpty("rampasan");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			List<ItemStack> drawn = Expeditions.lootTable(one.getIntOr("type", Expeditions.LOOT_PATROL), one.getIntOr("tier", 1),
					RandomSource.create(one.getLongOr("seed", 0L)));
			int n = one.getIntOr("n", 0);
			for (ItemStack stack : drawn) {
				if (n-- <= 0) {
					break;
				}
				if (!stack.isEmpty()) {
					out.add(stack);
				}
			}
		}
		return out;
	}

	private Rampasan() {
	}
}

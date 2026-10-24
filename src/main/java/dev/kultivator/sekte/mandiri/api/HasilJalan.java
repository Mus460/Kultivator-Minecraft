package dev.kultivator.sekte.mandiri.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of one finished journey (DESIGN 39.6.2, 39.6.9), carried by {@code MandiriBus.JALAN_TIBA} and kept in
 * {@code MandiriData.pending.<soul>} until the body arrives (applied once, keyed by {@link #idPerjalanan}). Goods are
 * item ids and counts, never {@code ItemStack}s: stacks are created lazily at arrival (39.6.11 rule 2). Pure data, safe
 * on both sides.
 *
 * @param idPerjalanan idempotency key ({@code soul ^ departure time}, hex)
 * @param jenis        kind of journey
 * @param tujuan       destination id (town key, site id or provider id)
 * @param tahap        one story key per stage ({@code kultivator.sekte.mandiri.kisah.<jenis>.<tahap>.<hasil>.<n>}), the
 *                     "Gulungan Perjalanan"
 * @param barang       goods bought or looted (to the bag, the Lumbung or a gift)
 * @param uang         purse change in NB (spent negative, earned positive), already clamped
 * @param luka         how the disciple comes home
 * @param kenangan     memory kinds to write ({@code mandiri.kota}, {@code mandiri.tertawan} ...)
 * @param jamKultivasi K-weighted hours of the trip ({@code K 0.8} per road hour, 2.0 x site factor in a cave ...)
 * @param pk           personal contribution points earned (missions)
 * @param sukses       the last stage succeeded
 */
public record HasilJalan(String idPerjalanan, JenisJalan jenis, String tujuan, List<String> tahap, List<Barang> barang,
						 long uang, JenisLuka luka, List<String> kenangan, double jamKultivasi, int pk, boolean sukses) {

	/** One kind of goods: an item id ({@code kultivator:healing_pill}) and a count; {@code tujuan} bag/lumbung/hadiah:<soul>. */
	public record Barang(String itemId, int n, String tujuan) {
		public Barang {
			itemId = itemId == null ? "" : itemId;
			n = Math.max(0, n);
			tujuan = tujuan == null ? "tas" : tujuan;
		}
	}

	public HasilJalan {
		idPerjalanan = idPerjalanan == null ? "" : idPerjalanan;
		jenis = jenis == null ? JenisJalan.BELANJA : jenis;
		tujuan = tujuan == null ? "" : tujuan;
		tahap = tahap == null ? List.of() : List.copyOf(tahap);
		barang = barang == null ? List.of() : List.copyOf(barang);
		luka = luka == null ? JenisLuka.TIDAK : luka;
		kenangan = kenangan == null ? List.of() : List.copyOf(kenangan);
		jamKultivasi = Math.max(0.0D, jamKultivasi);
		pk = Math.max(0, pk);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", idPerjalanan);
		tag.putString("jenis", jenis.id());
		tag.putString("tujuan", tujuan);
		tag.put("tahap", strings(tahap));
		ListTag goods = new ListTag();
		for (Barang each : barang) {
			CompoundTag one = new CompoundTag();
			one.putString("item", each.itemId());
			one.putInt("n", each.n());
			one.putString("ke", each.tujuan());
			goods.add(one);
		}
		tag.put("barang", goods);
		tag.putLong("uang", uang);
		tag.putString("luka", luka.id());
		tag.put("kenangan", strings(kenangan));
		tag.putDouble("jam", jamKultivasi);
		tag.putInt("pk", pk);
		tag.putBoolean("sukses", sukses);
		return tag;
	}

	public static HasilJalan fromTag(CompoundTag tag) {
		ListTag goods = tag.getListOrEmpty("barang");
		List<Barang> barang = new ArrayList<>(goods.size());
		for (int i = 0; i < goods.size(); i++) {
			CompoundTag one = goods.getCompoundOrEmpty(i);
			barang.add(new Barang(one.getStringOr("item", ""), one.getIntOr("n", 0), one.getStringOr("ke", "tas")));
		}
		JenisJalan jenis = JenisJalan.byId(tag.getStringOr("jenis", ""));
		return new HasilJalan(tag.getStringOr("id", ""), jenis == null ? JenisJalan.BELANJA : jenis, tag.getStringOr("tujuan", ""),
				readStrings(tag.getListOrEmpty("tahap")), barang, tag.getLongOr("uang", 0L), JenisLuka.byId(tag.getStringOr("luka", "")),
				readStrings(tag.getListOrEmpty("kenangan")), tag.getDoubleOr("jam", 0.0D), tag.getIntOr("pk", 0),
				tag.getBooleanOr("sukses", false));
	}

	private static ListTag strings(List<String> values) {
		ListTag list = new ListTag();
		for (String value : values) {
			list.add(StringTag.valueOf(value == null ? "" : value));
		}
		return list;
	}

	private static List<String> readStrings(ListTag list) {
		List<String> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getStringOr(i, ""));
		}
		return out;
	}
}

package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code tagihan} sheet (DESIGN 38.U.3.7, keys of {@link LembarKunci.Tagihan}) and its answers. Three variants:
 * <ul>
 *   <li>{@code biasa}: the bill lines (block group x count, NB), the 30 % wage, the total and what paying or refusing
 *       means here; ops {@code bayar}, {@code tawar}, {@code tolak};</li>
 *   <li>{@code takut}: no numbers, a kneeling portrait, the place's fear meter; ops {@code bayar_rela}, {@code pergi};</li>
 *   <li>{@code donasi} (monster and raid damage, opened at the crew's foreman or any craftsman): the job's progress,
 *       the materials still missing with how many the player carries; ops {@code donasi:NB}, {@code antar},
 *       {@code bantu}.</li>
 * </ul>
 */
public final class PerbaikanLayar {

	private static final String L = "kultivator.tempat_perbaikan.";
	/** Lines of the collector per variant (lang {@code kalimat.<varian>.<n>}). */
	private static final int KALIMAT_BIASA = 5;
	private static final int KALIMAT_TAKUT = 4;
	private static final int KALIMAT_DONASI = 4;
	private static final int KALIMAT_IBLIS = 3;
	/** Donation steps offered on the sheet. */
	public static final long[] DONASI = {5L, 20L, 81L};

	static void init() {
		TempatLayar.daftar(LembarKunci.Tagihan.LAYAR, PerbaikanLayar::aksi, PerbaikanLayar::bangun);
	}

	// ================================================================== building

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		MinecraftServer server = player.level().getServer();
		PerbaikanData data = PerbaikanData.get(server);
		long billId = Penagih.tagihanDari(player, npcId);
		Entity entity = player.level().getEntity(npcId);
		if (billId != 0L) {
			PerbaikanData.TagihanData bill = data.tagihan.get(billId);
			TempatRawat t = bill == null ? null : data.tempat.get(bill.kunci);
			if (bill != null && t != null && entity instanceof KultivatorNpc npc) {
				return lembarTagihan(player, t, bill, npc, Penagih.takut(player), null);
			}
			return null;
		}
		if (entity instanceof KultivatorNpc npc) {
			Gugus g = Tukang.gugusDari(server, npc);
			TempatRawat t = g == null ? null : data.tempat.get(g.kunci);
			if (g != null && t != null) {
				return lembarDonasi(player, t, g, npc, null);
			}
		}
		return null;
	}

	/** The bill sheet ({@code biasa}, or {@code takut} when the place is afraid of {@code player}). */
	static CompoundTag lembarTagihan(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill, KultivatorNpc npc, boolean takut,
			@Nullable Component pesan) {
		CompoundTag sheet = kepala(player, t, npc, takut);
		LembarKunci.teks(sheet, LembarKunci.JUDUL, Component.translatable(L + "layar.judul"));
		sheet.putString(LembarKunci.Tagihan.TAGIHAN_ID, Long.toString(bill.id));
		LembarKunci.teks(sheet, LembarKunci.Tagihan.DARI, Component.translatable(L + "layar.dari", Component.translatable(npc.role().titleKey()), npc.plate()));
		boolean iblis = JenisTempat.GAYA_IBLIS.equals(bill.gaya);
		if (takut) {
			sheet.putString(LembarKunci.Tagihan.VARIAN, LembarKunci.Tagihan.V_TAKUT);
			LembarKunci.teks(sheet, LembarKunci.Tagihan.KALIMAT, Component.translatable(L + "kalimat.takut." + Math.floorMod(bill.id, KALIMAT_TAKUT)));
			sheet.putInt(LembarKunci.Tagihan.TAKUT_METER, Takut.nilai(player.level().getServer(), t.situsId));
			LembarKunci.teks(sheet, LembarKunci.SUBJUDUL, Component.translatable(L + "layar.sub_takut"));
		} else {
			sheet.putString(LembarKunci.Tagihan.VARIAN, LembarKunci.Tagihan.V_BIASA);
			String line = iblis ? "kalimat.iblis." + Math.floorMod(bill.id, KALIMAT_IBLIS) : "kalimat.biasa." + Math.floorMod(bill.id, KALIMAT_BIASA);
			LembarKunci.teks(sheet, LembarKunci.Tagihan.KALIMAT, Component.translatable(L + line));
			if (iblis) {
				LembarKunci.teks(sheet, LembarKunci.SUBJUDUL, Component.translatable(L + "layar.sub_iblis"));
			}
			HolderLookup.Provider registries = player.level().registryAccess();
			ListTag lines = new ListTag();
			for (Map.Entry<String, Double> e : bill.rincian.entrySet()) {
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable(L + "kelompok." + e.getKey()));
				ItemStack icon = ikon(e.getKey());
				if (!icon.isEmpty()) {
					LembarKunci.barang(row, icon, registries);
				}
				row.putInt(LembarKunci.JUMLAH, bill.jumlahBlok.getOrDefault(e.getKey(), 0));
				row.putFloat(LembarKunci.Tagihan.SUBTOTAL, (float) (Math.round(e.getValue() * bill.faktor * 10.0D) / 10.0D));
				lines.add(row);
				if (lines.size() >= LembarKunci.DAFTAR_MAKS) {
					break;
				}
			}
			sheet.put(LembarKunci.Tagihan.RINCIAN, lines);
			sheet.putFloat(LembarKunci.Tagihan.UPAH, (float) (Math.round(Penagihan.upah(bill) * 10.0D) / 10.0D));
			sheet.putInt(LembarKunci.Tagihan.UPAH_PERSEN, (int) Math.round((Penagihan.UPAH - 1.0D) * 100.0D));
			sheet.putLong(LembarKunci.Tagihan.TOTAL, bill.jumlah);
			sheet.putBoolean(LembarKunci.Tagihan.SUDAH_TAWAR, bill.sudahTawar);
			LembarKunci.teks(sheet, LembarKunci.Tagihan.AKIBAT_BAYAR, Component.translatable(L + (iblis ? "akibat.bayar_iblis" : "akibat.bayar"),
					trimNumber(PerbaikanConfig.get().pengaliSewa)));
			LembarKunci.teks(sheet, LembarKunci.Tagihan.AKIBAT_TOLAK, akibatTolak(t, bill));
		}
		if (pesan != null) {
			LembarKunci.teks(sheet, LembarKunci.PESAN, pesan);
			sheet.putString(LembarKunci.PESAN_JENIS, LembarKunci.PESAN_PERINGATAN);
		}
		return sheet;
	}

	/** The help sheet of a monster- or raid-damaged job (any player may give). */
	static CompoundTag lembarDonasi(ServerPlayer player, TempatRawat t, Gugus g, KultivatorNpc npc, @Nullable Component pesan) {
		CompoundTag sheet = kepala(player, t, npc, false);
		LembarKunci.teks(sheet, LembarKunci.JUDUL, Component.translatable(L + "layar.judul_bantuan"));
		sheet.putString(LembarKunci.Tagihan.TAGIHAN_ID, "g" + g.id);
		sheet.putString(LembarKunci.Tagihan.VARIAN, LembarKunci.Tagihan.V_DONASI);
		LembarKunci.teks(sheet, LembarKunci.Tagihan.DARI, Component.translatable(L + "layar.dari_mandor", Component.translatable(npc.role().titleKey()),
				npc.plate()));
		LembarKunci.teks(sheet, LembarKunci.Tagihan.KALIMAT, Component.translatable(L + "kalimat.donasi." + Math.floorMod(g.id, KALIMAT_DONASI),
				Component.translatable(L + "sebab." + g.sebabUtama())));
		CompoundTag donasi = new CompoundTag();
		donasi.putFloat(LembarKunci.KEMAJUAN, g.persen());
		ListTag need = new ListTag();
		HolderLookup.Provider registries = player.level().registryAccess();
		for (Map.Entry<Item, Integer> e : bahan(g).entrySet()) {
			CompoundTag row = new CompoundTag();
			ItemStack stack = new ItemStack(e.getKey());
			LembarKunci.barang(row, stack, registries);
			row.putInt(LembarKunci.JUMLAH, e.getValue());
			row.putInt(LembarKunci.Tagihan.ADA, player.getInventory().countItem(e.getKey()));
			need.add(row);
			if (need.size() >= 6) {
				break;
			}
		}
		donasi.put(LembarKunci.Tagihan.PERLU, need);
		donasi.putLong(LembarKunci.Tagihan.MIN, Penagihan.MINIMUM);
		donasi.putBoolean(LembarKunci.Tagihan.BANTU, true);
		sheet.put(LembarKunci.Tagihan.DONASI, donasi);
		LembarKunci.teks(sheet, LembarKunci.Tagihan.AKIBAT_BAYAR, Component.translatable(L + "akibat.donasi"));
		if (pesan != null) {
			LembarKunci.teks(sheet, LembarKunci.PESAN, pesan);
			sheet.putString(LembarKunci.PESAN_JENIS, LembarKunci.PESAN_OK);
		}
		return sheet;
	}

	private static CompoundTag kepala(ServerPlayer player, TempatRawat t, KultivatorNpc npc, boolean takut) {
		CompoundTag sheet = new CompoundTag();
		sheet.putString(LembarKunci.GAYA, LembarKunci.GAYA_KERTAS);
		sheet.putLong(LembarKunci.SITUS, t.situsId);
		LembarKunci.teks(sheet, LembarKunci.TEMPAT, Penagihan.namaTempat(t));
		sheet.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		if (!JenisTempat.GAYA_IBLIS.equals(t.profil().gaya())) {
			sheet.putInt(LembarKunci.NAMA_BAIK, KotaData.get(player.level().getServer()).reputation(t.situsId, player.getUUID()));
		}
		CompoundTag card = new CompoundTag();
		LembarKunci.teks(card, LembarKunci.NAMA, npc.plate());
		LembarKunci.teks(card, LembarKunci.GELAR, Component.translatable(npc.role().titleKey()));
		card.putString(LembarKunci.PERAN, npc.role().name());
		card.putInt(LembarKunci.POTRET, npc.getId());
		card.putBoolean(LembarKunci.TAKUT, takut);
		sheet.put(LembarKunci.NPC, card);
		return sheet;
	}

	private static Component akibatTolak(TempatRawat t, PerbaikanData.TagihanData bill) {
		if (JenisTempat.GAYA_IBLIS.equals(bill.gaya)) {
			return Component.translatable(L + "akibat.tolak_iblis");
		}
		int loss = (int) Math.min(30L, (bill.jumlah + 4L) / 5L);
		if (t.profil().ibuKota()) {
			return Component.translatable(L + "akibat.tolak_ibu_kota", loss, Math.max(1L, bill.jumlah / 10L));
		}
		if (JenisTempat.GAYA_SEKTE.equals(bill.gaya)) {
			return Component.translatable(L + "akibat.tolak_sekte", loss);
		}
		if (t.jenis.startsWith("kampung")) {
			return Component.translatable(L + "akibat.tolak_kampung", loss);
		}
		return Component.translatable(L + "akibat.tolak", loss);
	}

	private static String trimNumber(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** The icon item of a bill line group. */
	private static ItemStack ikon(String group) {
		Item item = switch (group) {
			case NilaiBlok.TANAH -> Items.DIRT;
			case NilaiBlok.BATU -> Items.COBBLESTONE;
			case NilaiBlok.KAYU -> Items.OAK_PLANKS;
			case NilaiBlok.KAIN -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
			case NilaiBlok.KACA -> Items.GLASS_PANE;
			case NilaiBlok.BATA -> Items.BRICKS;
			case NilaiBlok.HIAS -> Items.CHISELED_QUARTZ_BLOCK;
			case NilaiBlok.PINTU -> Items.OAK_DOOR;
			case NilaiBlok.LOGAM -> Items.LANTERN;
			case NilaiBlok.RANJANG -> Items.BED.pick(net.minecraft.world.item.DyeColor.RED);
			case NilaiBlok.LONCENG -> Items.BELL;
			case NilaiBlok.TANAMAN -> Items.WHEAT_SEEDS;
			case "buang" -> Items.IRON_SHOVEL;
			default -> Items.STONE;
		};
		return new ItemStack(item);
	}

	/** The materials the job still needs (open operations by item), most first. */
	private static Map<Item, Integer> bahan(Gugus g) {
		Map<Item, Integer> count = new LinkedHashMap<>();
		for (Gugus.Op op : g.ops) {
			if (op.status != Gugus.MENUNGGU || op.target.isAir()) {
				continue;
			}
			Item item = op.target.getBlock().asItem();
			if (item != Items.AIR) {
				count.merge(item, 1, Integer::sum);
			}
		}
		List<Map.Entry<Item, Integer>> list = new ArrayList<>(count.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		Map<Item, Integer> sorted = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : list) {
			sorted.put(e.getKey(), e.getValue());
		}
		return sorted;
	}

	// ================================================================== answers

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		MinecraftServer server = player.level().getServer();
		PerbaikanData data = PerbaikanData.get(server);
		Entity entity = player.level().getEntity(npcId);
		if (!(entity instanceof KultivatorNpc npc)) {
			return;
		}
		long billId = Penagih.tagihanDari(player, npcId);
		if (billId != 0L) {
			PerbaikanData.TagihanData bill = data.tagihan.get(billId);
			TempatRawat t = bill == null ? null : data.tempat.get(bill.kunci);
			if (bill == null || t == null) {
				TempatLayar.tutup(player);
				return;
			}
			switch (verb) {
				case LembarKunci.Tagihan.OP_BAYAR -> {
					Penagihan.bayar(player, t, bill);
					if (bill.status == PerbaikanData.T_LUNAS) {
						Penagih.selesai(player, true);
						TempatLayar.tutup(player);
					}
				}
				case LembarKunci.Tagihan.OP_TAWAR -> {
					long before = bill.jumlah;
					Penagihan.tawar(player, t, bill);
					Component msg = Component.translatable(L + (bill.jumlah < before ? "tawar.turun" : bill.jumlah > before ? "tawar.naik" : "tawar.sudah"),
							bill.jumlah);
					TempatLayar.segarkan(player, LembarKunci.Tagihan.LAYAR, npcId, lembarTagihan(player, t, bill, npc, false, msg));
				}
				case LembarKunci.Tagihan.OP_TOLAK -> {
					Penagihan.tolak(player, t, bill);
					Penagih.selesai(player, false);
					TempatLayar.tutup(player);
				}
				case LembarKunci.Tagihan.OP_BAYAR_RELA -> {
					Penagihan.bayarRela(player, t);
					Penagih.selesai(player, true);
					TempatLayar.tutup(player);
				}
				case LembarKunci.Tagihan.OP_PERGI -> TempatLayar.tutup(player);
				default -> {
				}
			}
			return;
		}
		Gugus g = Tukang.gugusDari(server, npc);
		TempatRawat t = g == null ? null : data.tempat.get(g.kunci);
		if (g == null || t == null) {
			TempatLayar.tutup(player);
			return;
		}
		switch (verb) {
			case LembarKunci.Tagihan.OP_DONASI -> {
				long nb = Math.max(Penagihan.MINIMUM, LembarKunci.argLong(parts, 1, Penagihan.MINIMUM));
				Penagihan.donasi(player, t, Math.min(nb, 100_000L));
				TempatLayar.segarkan(player, LembarKunci.Tagihan.LAYAR, npcId, lembarDonasi(player, t, g, npc,
						Component.translatable(L + "donasi.terima", nb)));
			}
			case LembarKunci.Tagihan.OP_ANTAR -> {
				int given = antar(player, t, g);
				Component msg = given > 0 ? Component.translatable(L + "antar.ok", given) : Component.translatable(L + "antar.tidak_ada");
				TempatLayar.segarkan(player, LembarKunci.Tagihan.LAYAR, npcId, lembarDonasi(player, t, g, npc, msg));
			}
			case LembarKunci.Tagihan.OP_BANTU -> {
				if (Bantu.mulai(player, t, g)) {
					TempatLayar.tutup(player);
				}
			}
			case LembarKunci.Tagihan.OP_PERGI -> TempatLayar.tutup(player);
			default -> {
			}
		}
	}

	/**
	 * Delivering materials (38.R.6): every carried block item that an open operation of the job needs is taken, one per
	 * operation, and that operation counts as worked off. Nama Baik +1 per 32 items, a help point per 8.
	 */
	static int antar(ServerPlayer player, TempatRawat t, Gugus g) {
		int given = 0;
		for (Gugus.Op op : g.ops) {
			if (op.status != Gugus.MENUNGGU || op.target.isAir()) {
				continue;
			}
			Block block = op.target.getBlock();
			Item item = block.asItem();
			if (item == Items.AIR || !(item instanceof BlockItem)) {
				continue;
			}
			int slot = slotOf(player, item);
			if (slot < 0) {
				continue;
			}
			player.getInventory().removeItem(slot, 1);
			op.status = Gugus.SIAP;
			given++;
		}
		if (given <= 0) {
			return 0;
		}
		PerbaikanData data = PerbaikanData.get(player.level().getServer());
		PerbaikanData.PemainTempat pt = data.pemain(player.getUUID(), t.situsId);
		pt.sisaAntar += given;
		int rep = pt.sisaAntar / 32;
		pt.sisaAntar %= 32;
		if (rep > 0) {
			Penagihan.namaBaik(player, t.situsId, rep);
		}
		data.kotor();
		Bantu.tambahPoin(player, t.situsId, Math.max(1, given / 8));
		Msg.actionBar(player, Component.translatable(L + "antar.ok", given).withStyle(ChatFormatting.GREEN));
		return given;
	}

	private static int slotOf(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && stack.is(item)) {
				return i;
			}
		}
		return -1;
	}

	private PerbaikanLayar() {
	}
}

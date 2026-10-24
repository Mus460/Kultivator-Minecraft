package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Forge orders of Empu Ou Ye (DESIGN 38.M.2.1): the player picks a form and a grade (≤ 4), hands over the very materials
 * the forge's recipe asks for ({@link Forging#orderMaterials}, taken when ordering) and pays 0.15 × {@code
 * Harga.ARTIFACT[grade]}; the piece is ready after 1 / 2 / 3 / 3 / 4 world days (grade 0-4), made as at the guild's heat 3
 * ({@link Forging#buatPesanan}), kept in {@link EkonomiData} across restarts (38.E.5 test 9) and fetched at any Forge Guild
 * smith. At most two orders per player. Band gates 0 / 1 / 2 / 4 / 5 for grade 0-4 (38.M.1).
 */
public final class PesananTempa {

	public static final double ONGKOS = 0.15D;
	public static final int[] HARI_JADI = {1, 2, 3, 3, 4};
	public static final int[] PITA_MIN = {0, 1, 2, 4, 5};
	public static final int MAKS = 2;
	private static final String KUNCI = "pesanan_tempa";
	/** The last quote a player asked for (form, grade), shown on the next sheet. */
	private static final Map<UUID, String[]> KUTIPAN = new HashMap<>();

	static void lupakan(UUID id) {
		KUTIPAN.remove(id);
	}

	static long ongkos(int mutu) {
		return Barang.harga(Math.round(Harga.ARTIFACT[Math.max(0, Math.min(Forging.ORDER_MAX_GRADE, mutu))] * ONGKOS), 1.0D);
	}

	static ListTag pesanan(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).daftar(player.getUUID(), KUNCI);
	}

	private static int punya(ServerPlayer player, Forging.OrderMaterial bahan) {
		int n = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && bahan.test().test(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void ambilBahan(ServerPlayer player, Forging.OrderMaterial bahan) {
		int left = bahan.count();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && bahan.test().test(stack)) {
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
		inventory.setChanged();
	}

	/** Why an order of {@code mutu} cannot be placed now (null when it can, materials aside). */
	private static @Nullable Component alasan(ServerPlayer player, int mutu) {
		if (mutu < 0 || mutu > Forging.ORDER_MAX_GRADE) {
			return Component.translatable("kultivator.tempat_ekonomi.pesan.mutu_maks", Forging.ORDER_MAX_GRADE);
		}
		if (Barang.pita(player) < PITA_MIN[mutu]) {
			return Component.translatable("kultivator.tempat_ekonomi.butuh_ranah",
					Component.translatable("kultivator.realm." + dev.kultivator.data.Realm.values()[PITA_MIN[mutu]].id()));
		}
		if (pesanan(player).size() >= MAKS) {
			return Component.translatable("kultivator.tempat_ekonomi.pesan.penuh", MAKS);
		}
		return null;
	}

	/** The {@code pesan} block of the weapon-shop sheet. */
	static CompoundTag lembar(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		ListTag bentuk = new ListTag();
		int[] icons = {LembarKunci.Ikon.JIAN, LembarKunci.Ikon.KIPAS_BESI, LembarKunci.Ikon.GADA, LembarKunci.Ikon.LONCENG};
		for (int i = 0; i < ArtifactItem.FORMS.size(); i++) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, ArtifactItem.FORMS.get(i));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempa.form." + ArtifactItem.FORMS.get(i)));
			row.putInt(LembarKunci.IKON, icons[i]);
			bentuk.add(row);
		}
		tag.put(LembarKunci.TokoSenjata.BENTUK, bentuk);
		tag.putInt(LembarKunci.TokoSenjata.MUTU_MAKS, Forging.ORDER_MAX_GRADE);
		tag.putInt(LembarKunci.TokoSenjata.PESANAN_MAKS, MAKS);
		String[] quote = KUTIPAN.get(player.getUUID());
		if (quote != null) {
			tag.put(LembarKunci.TokoSenjata.KUTIPAN, kutipan(player, quote[0], Integer.parseInt(quote[1])));
		}
		tag.put(LembarKunci.TokoSenjata.PESANAN, daftarLembar(player));
		return tag;
	}

	/** The running orders (also the Forge Guild screen's {@code pesanan}). */
	static ListTag daftarLembar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long now = Barang.waktu(server);
		ListTag rows = new ListTag();
		for (net.minecraft.nbt.Tag each : pesanan(player)) {
			if (!(each instanceof CompoundTag order)) {
				continue;
			}
			String form = order.getStringOr("bentuk", ArtifactItem.PEDANG);
			int mutu = order.getIntOr("mutu", 0);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Long.toString(order.getLongOr("id", 0L)));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.pesan.nama",
					Component.translatable("kultivator.tempa.form." + form), Component.translatable("kultivator.tempa.grade." + mutu)));
			LembarKunci.barang(row, ArtifactItem.create(form, mutu, 0, 0), player.registryAccess());
			row.putInt(LembarKunci.MUTU, mutu);
			long left = Math.max(0L, order.getLongOr("siap", 0L) - now);
			row.putLong(LembarKunci.SISA_TICK, left);
			row.putBoolean(LembarKunci.TokoSenjata.SIAP, left <= 0L);
			rows.add(row);
		}
		return rows;
	}

	private static CompoundTag kutipan(ServerPlayer player, String form, int mutu) {
		CompoundTag tag = new CompoundTag();
		tag.putString(LembarKunci.ID, form);
		tag.putInt(LembarKunci.MUTU, mutu);
		ListTag bahan = new ListTag();
		boolean semua = true;
		for (Forging.OrderMaterial material : Forging.orderMaterials(form, mutu)) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable(material.nameKey()));
			LembarKunci.barang(row, material.icon().get(), player.registryAccess());
			row.putInt(LembarKunci.TokoSenjata.PERLU, material.count());
			int have = punya(player, material);
			row.putInt(LembarKunci.TokoSenjata.PUNYA, have);
			semua &= have >= material.count();
			bahan.add(row);
		}
		tag.put(LembarKunci.TokoSenjata.BAHAN, bahan);
		long ongkos = ongkos(mutu);
		tag.putLong(LembarKunci.TokoSenjata.ONGKOS, ongkos);
		tag.putInt(LembarKunci.TokoSenjata.HARI_JADI, HARI_JADI[Math.max(0, Math.min(4, mutu))]);
		Component why = alasan(player, mutu);
		if (why == null && !semua) {
			why = Component.translatable("kultivator.tempat_ekonomi.pesan.bahan_kurang");
		}
		if (why == null && NilaiBatu.total(player) < ongkos) {
			why = Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(ongkos));
		}
		tag.putBoolean(LembarKunci.BISA, why == null);
		if (why != null) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, why);
		}
		return tag;
	}

	static void aksi(ServerPlayer player, KultivatorNpc npc, String[] parts) {
		switch (parts[0]) {
			case LembarKunci.TokoSenjata.OP_KUTIP -> {
				String form = LembarKunci.arg(parts, 1);
				int mutu = LembarKunci.argInt(parts, 2, 0);
				if (ArtifactItem.FORMS.contains(form) && mutu >= 0 && mutu <= Forging.ORDER_MAX_GRADE) {
					KUTIPAN.put(player.getUUID(), new String[] {form, Integer.toString(mutu)});
				}
			}
			case LembarKunci.TokoSenjata.OP_PESAN -> pesan(player, npc, LembarKunci.arg(parts, 1), LembarKunci.argInt(parts, 2, -1));
			case LembarKunci.TokoSenjata.OP_AMBIL -> ambil(player, npc, LembarKunci.argLong(parts, 1, -1L));
			default -> {
			}
		}
	}

	private static void pesan(ServerPlayer player, KultivatorNpc npc, String form, int mutu) {
		if (!ArtifactItem.FORMS.contains(form)) {
			return;
		}
		Component why = alasan(player, mutu);
		if (why != null) {
			Lembar.pesan(player, why, false);
			return;
		}
		List<Forging.OrderMaterial> materials = Forging.orderMaterials(form, mutu);
		for (Forging.OrderMaterial material : materials) {
			if (punya(player, material) < material.count()) {
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pesan.bahan_kurang"), false);
				return;
			}
		}
		long ongkos = ongkos(mutu);
		if (!NilaiBatu.pay(player, ongkos)) {
			Lembar.kurangUang(player, ongkos);
			return;
		}
		for (Forging.OrderMaterial material : materials) {
			ambilBahan(player, material);
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag order = new CompoundTag();
		order.putLong("id", data.serial("pesanan_tempa"));
		order.putString("bentuk", form);
		order.putInt("mutu", mutu);
		order.putLong("siap", Barang.waktu(server) + HARI_JADI[mutu] * 24000L);
		order.putString("empu", npc.getDisplayName().getString());
		pesanan(player).add(order);
		data.touch();
		KUTIPAN.remove(player.getUUID());
		SosokApi.gerak(npc, GerakId.PALU_LANDASAN, 0, 60, PropId.PALU, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5F, 1.3F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pesan.diterima", Component.translatable("kultivator.tempa.form." + form),
				Component.translatable("kultivator.tempa.grade." + mutu), HARI_JADI[mutu]), true);
	}

	private static void ambil(ServerPlayer player, KultivatorNpc npc, long id) {
		MinecraftServer server = player.level().getServer();
		ListTag list = pesanan(player);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag order = list.getCompoundOrEmpty(i);
			if (order.getLongOr("id", -1L) != id) {
				continue;
			}
			if (order.getLongOr("siap", Long.MAX_VALUE) > Barang.waktu(server)) {
				Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.pesan.belum"));
				return;
			}
			RandomSource random = RandomSource.create(id * 0x632BE59BD9B4E019L ^ server.overworld().getSeed());
			ItemStack piece = Forging.buatPesanan((ServerLevel) player.level(), order.getStringOr("bentuk", ArtifactItem.PEDANG), order.getIntOr("mutu", 0),
					null, order.getStringOr("empu", "Empu"), random);
			list.remove(i);
			EkonomiData.get(server).touch();
			Barang.beri(player, piece);
			player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.7F, 1.2F);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pesan.diambil", piece.getHoverName()), true);
			return;
		}
	}

	/** {@code uji ekonomi pesanan selesai}: every order of the player is ready now. */
	public static int selesaikan(ServerPlayer player) {
		ListTag list = pesanan(player);
		long now = Barang.waktu(player.level().getServer());
		for (net.minecraft.nbt.Tag each : list) {
			if (each instanceof CompoundTag order) {
				order.putLong("siap", now);
			}
		}
		EkonomiData.get(player.level().getServer()).touch();
		return list.size();
	}

	private PesananTempa() {
	}
}

package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The weapon shops (DESIGN 38.M.2.1, screen 38.U.3.2): Paviliun Seribu Bilah ({@link NpcRole#PEDAGANG_SENJATA}), the Forge
 * Guild's smiths ({@link NpcRole#EMPU}, with the forge-order tab of {@link PesananTempa}) and the town weapon stalls. A day's
 * stock is seeded by (site, day): city weapons of every shape and grade (2-4 iron, 1-3 low, 1-2 mid, 0-1 high spirit), 3-5
 * forged swords grade 0-2 on the rack (5 % a day a rare one grade higher), chain and iron sets, diamond pieces and the four
 * guild robes; stock is shared by every buyer of that day. Price = base × town scale × (1 − discount), forged rack pieces
 * {@code Harga.ARTIFACT[grade] × 1.2}. Band gates 0 / 2 / 3 / 4 for the four weapon grades, 1 / 1 / 3 for chain / iron /
 * diamond. Services: repair (2 % of the value per 10 % durability) and appraisal (3 free a day). Every ware sold here is
 * marked {@code asal_toko} (no resale to a 38 shop within 3 days).
 */
public final class TokoSenjata {

	public static final double HARGA_PUSAKA = 1.2D;
	public static final double PERBAIKI_PER_10 = 0.02D;
	public static final int NILAI_GRATIS = 3;
	static final Set<NpcRole> PERAN = Set.of(NpcRole.PEDAGANG_SENJATA, NpcRole.EMPU, NpcRole.PANDAI_PUSAKA);

	/** One ware of the day. */
	record Barisan(String id, String tab, Supplier<ItemStack> buat, long harga, int stok, int pitaMin, boolean langka, int mutu) {
	}

	static void init() {
		TempatLayar.daftar(LembarKunci.TokoSenjata.LAYAR, TokoSenjata::aksi, TokoSenjata::bangun);
	}

	static long situs(@Nullable KultivatorNpc npc) {
		return npc == null ? 0L : npc.anchorSite() != 0L ? npc.anchorSite() : npc.town();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Stock

	/** Today's wares of the shop at {@code situs} (the same list for every viewer of the day). */
	static List<Barisan> stok(ServerLevel level, long situs, double diskon) {
		long hari = Barang.hari(level.getServer());
		RandomSource random = RandomSource.create(situs * 0x2545F4914F6CDD1DL + hari * 1_000_003L);
		List<Barisan> list = new ArrayList<>();
		double faktor = 1.0D - diskon;
		int[][] stokMutu = {{2, 4}, {1, 3}, {1, 2}, {0, 1}};
		for (SenjataKota.Bentuk bentuk : SenjataKota.Bentuk.values()) {
			for (SenjataKota.Mutu mutu : SenjataKota.Mutu.values()) {
				int[] range = stokMutu[mutu.ordinal()];
				int stok = range[0] + random.nextInt(range[1] - range[0] + 1);
				long harga = Barang.harga(bentuk.harga(mutu), faktor);
				list.add(new Barisan("s." + bentuk.id + "." + mutu.id, bentuk.id, () -> SenjataKota.stack(bentuk, mutu), harga, stok, mutu.pitaMin,
						false, mutu.ordinal()));
			}
		}
		int pusaka = 3 + random.nextInt(3);
		for (int i = 0; i < pusaka; i++) {
			long seed = random.nextLong();
			int grade = random.nextInt(3);
			boolean langka = random.nextFloat() < 0.05F;
			if (langka) {
				grade++;
			}
			int g = grade;
			long harga = Barang.harga(Math.round(Harga.ARTIFACT[g] * HARGA_PUSAKA), faktor);
			list.add(new Barisan("p." + i, LembarKunci.TokoSenjata.TAB_JIAN,
					() -> Forging.buatStok(level, ArtifactItem.PEDANG, g, RandomSource.create(seed), "Gilda Pandai Pusaka"), harga, 1,
					Math.max(0, g + 1), langka, g));
		}
		list.add(zirah("z.rantai", () -> new ItemStack(Items.CHAINMAIL_CHESTPLATE), 40L, 1, faktor, 1 + random.nextInt(2)));
		list.add(zirah("z.besi", () -> new ItemStack(Items.IRON_CHESTPLATE), 60L, 1, faktor, 1 + random.nextInt(2)));
		list.add(zirah("z.intan_helm", () -> new ItemStack(Items.DIAMOND_HELMET), 180L, 3, faktor, random.nextInt(2)));
		list.add(zirah("z.intan_dada", () -> new ItemStack(Items.DIAMOND_CHESTPLATE), 180L, 3, faktor, random.nextInt(2)));
		list.add(zirah("z.intan_celana", () -> new ItemStack(Items.DIAMOND_LEGGINGS), 180L, 3, faktor, random.nextInt(2)));
		list.add(zirah("z.intan_sepatu", () -> new ItemStack(Items.DIAMOND_BOOTS), 180L, 3, faktor, random.nextInt(2)));
		String[] warna = {"merah", "biru", "hijau", "emas"};
		List<Item> jubah = EkonomiItems.jubah();
		for (int i = 0; i < 4; i++) {
			Item item = jubah.get(i);
			list.add(zirah("z.jubah_" + warna[i], () -> new ItemStack(item), 30L, 0, faktor, 3));
		}
		return list;
	}

	private static Barisan zirah(String id, Supplier<ItemStack> buat, long base, int pita, double faktor, int stok) {
		return new Barisan(id, LembarKunci.TokoSenjata.TAB_ZIRAH, buat, Barang.harga(base, faktor), stok, pita, false, -1);
	}

	/** The pieces a set id hands out (a chain or iron set is four pieces). */
	private static List<ItemStack> potongan(Barisan barisan) {
		return switch (barisan.id) {
			case "z.rantai" -> List.of(new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
					new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS));
			case "z.besi" -> List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS),
					new ItemStack(Items.IRON_BOOTS));
			default -> List.of(barisan.buat.get());
		};
	}

	private static CompoundTag terjual(MinecraftServer server, long situs) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situs);
		CompoundTag sold = site.getCompoundOrEmpty("senjata_terjual");
		long hari = Barang.hari(server);
		if (sold.getLongOr("hari", Long.MIN_VALUE) != hari) {
			sold = new CompoundTag();
			sold.putLong("hari", hari);
			site.put("senjata_terjual", sold);
			data.touch();
		}
		return sold;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Stats and comparison

	/** Total attack of a held weapon (base 1 + modifiers; forged swords by their power). */
	static double serangan(ItemStack stack) {
		if (stack.isEmpty()) {
			return 1.0D;
		}
		if (ArtifactItem.is(stack)) {
			return ArtifactItem.PEDANG.equals(ArtifactItem.form(ArtifactItem.data(stack))) ? ArtifactItem.swordDamage(ArtifactItem.power(stack)) : 1.0D;
		}
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1.0D,
				EquipmentSlot.MAINHAND);
	}

	static double kecepatan(ItemStack stack) {
		if (ArtifactItem.is(stack)) {
			return ArtifactItem.SWORD_SPEED;
		}
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_SPEED, 4.0D,
				EquipmentSlot.MAINHAND);
	}

	private static ListTag stat(ItemStack stack, ItemStack hand) {
		ListTag stats = new ListTag();
		boolean banding = !hand.isEmpty() && WeaponClass.of(hand) != WeaponClass.TANGAN_KOSONG;
		boolean senjata = stack.getItem() instanceof SenjataKota.SenjataItem || ArtifactItem.is(stack);
		if (senjata) {
			stats.add(baris(LembarKunci.TokoSenjata.STAT_SERANGAN, serangan(stack), banding ? serangan(stack) - serangan(hand) : Double.NaN));
			stats.add(baris(LembarKunci.TokoSenjata.STAT_KECEPATAN, kecepatan(stack), banding ? kecepatan(stack) - kecepatan(hand) : Double.NaN));
			SenjataKota.Jenis jenis = SenjataKota.dari(stack);
			if (jenis != null && jenis.bentuk().jangkauan > 0.0D) {
				stats.add(baris(LembarKunci.TokoSenjata.STAT_JANGKAUAN, jenis.bentuk().jangkauan, Double.NaN));
			}
		} else {
			double armor = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0D,
					EquipmentSlot.CHEST) + stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR,
					0.0D, EquipmentSlot.HEAD) + stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(
					Attributes.ARMOR, 0.0D, EquipmentSlot.LEGS) + stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
					.compute(Attributes.ARMOR, 0.0D, EquipmentSlot.FEET);
			if (armor > 0.0D) {
				stats.add(baris(LembarKunci.TokoSenjata.STAT_ZIRAH, armor, Double.NaN));
			}
		}
		if (stack.getMaxDamage() > 0) {
			stats.add(baris(LembarKunci.TokoSenjata.STAT_DAYA_TAHAN, stack.getMaxDamage(), Double.NaN));
		}
		return stats;
	}

	private static CompoundTag baris(String kunci, double nilai, double banding) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.TokoSenjata.STAT_KUNCI, kunci);
		row.putFloat(LembarKunci.NILAI, (float) nilai);
		if (!Double.isNaN(banding)) {
			row.putFloat(LembarKunci.TokoSenjata.BANDING, (float) banding);
		}
		return row;
	}

	private static String kelasDuel(ItemStack stack) {
		WeaponClass kelas = WeaponClass.of(stack);
		return switch (kelas) {
			case PEDANG, PEDANG_BERAT -> "PEDANG";
			case TOMBAK -> "TOMBAK";
			case KAPAK -> "KAPAK";
			case GADA -> "GADA";
			default -> "";
		};
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		ServerLevel level = (ServerLevel) player.level();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.toko.judul", LembarKunci.GAYA_KAYU, npc.role() == NpcRole.EMPU
				? LembarKunci.DISTRIK_GILDA : LembarKunci.DISTRIK_PASAR, npc);
		if (!EkonomiConfig.get().toko) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		long situs = situs(npc);
		double diskon = Barang.diskon(player, npc.town(), npc.role() == NpcRole.EMPU);
		tag.putFloat(LembarKunci.DISKON, (float) diskon);
		int pita = Barang.pita(player);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		CompoundTag sold = terjual(level.getServer(), situs);
		ListTag daftar = new ListTag();
		java.util.Set<String> tabsWithGoods = new java.util.HashSet<>();
		for (Barisan barisan : stok(level, situs, diskon)) {
			ItemStack stack = barisan.buat.get();
			if (stack.isEmpty()) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, barisan.id);
			row.putString(LembarKunci.TokoSenjata.TAB_ENTRI, barisan.tab);
			LembarKunci.barang(row, stack, player.registryAccess());
			row.putLong(LembarKunci.HARGA, barisan.harga);
			int left = Math.max(0, barisan.stok - sold.getIntOr(barisan.id, 0));
			row.putInt(LembarKunci.STOK, left);
			row.putInt(LembarKunci.MUTU, barisan.mutu);
			if (barisan.langka) {
				row.putBoolean(LembarKunci.LANGKA, true);
			}
			String kelas = kelasDuel(stack);
			if (!kelas.isEmpty()) {
				row.putString(LembarKunci.TokoSenjata.KELAS_DUEL, kelas);
			}
			row.put(LembarKunci.TokoSenjata.STAT, stat(stack, hand));
			if (left <= 0) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
			} else if (pita < barisan.pitaMin) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.butuh_ranah",
						Component.translatable("kultivator.realm." + dev.kultivator.data.Realm.values()[Math.min(8, barisan.pitaMin)].id())));
			} else {
				row.putString(LembarKunci.KEADAAN, Lembar.keadaan(player, barisan.harga, false));
			}
			tabsWithGoods.add(barisan.tab);
			daftar.add(row);
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		ListTag hidden = new ListTag();
		for (String tab : LembarKunci.TokoSenjata.TABS) {
			boolean pesanTab = LembarKunci.TokoSenjata.TAB_PESAN.equals(tab);
			if (pesanTab ? npc.role() != NpcRole.EMPU : !tabsWithGoods.contains(tab)) {
				hidden.add(net.minecraft.nbt.StringTag.valueOf(tab));
			}
		}
		tag.put(LembarKunci.TAB_SEMBUNYI, hidden);
		if (!hand.isEmpty()) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand, player.registryAccess());
			tag.put(LembarKunci.TANGAN, tangan);
		}
		CompoundTag layanan = new CompoundTag();
		long repair = hargaPerbaikan(hand);
		if (repair > 0L) {
			layanan.putLong(LembarKunci.TokoSenjata.PERBAIKI, repair);
		}
		layanan.putInt(LembarKunci.TokoSenjata.NILAI_SISA, nilaiSisa(player));
		tag.put(LembarKunci.TokoSenjata.LAYANAN, layanan);
		if (npc.role() == NpcRole.EMPU) {
			tag.put(LembarKunci.TokoSenjata.PESAN_TEMPA, PesananTempa.lembar(player));
		}
		return tag;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !EkonomiConfig.get().toko) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case LembarKunci.TokoSenjata.OP_BELI -> beli(player, npc, LembarKunci.arg(parts, 1),
					Math.max(1, Math.min(16, LembarKunci.argInt(parts, 2, 1))));
			case LembarKunci.TokoSenjata.OP_PERBAIKI -> perbaiki(player, npc);
			case LembarKunci.TokoSenjata.OP_NILAI -> nilai(player);
			case LembarKunci.TokoSenjata.OP_KUTIP, LembarKunci.TokoSenjata.OP_PESAN, LembarKunci.TokoSenjata.OP_AMBIL -> {
				if (npc.role() == NpcRole.EMPU) {
					PesananTempa.aksi(player, npc, parts);
				}
			}
			default -> {
			}
		}
	}

	/** Buys {@code n} of ware {@code id} ({@code s.<bentuk>.<mutu>}, {@code p.<slot>}, {@code z.<zirah>}). */
	private static void beli(ServerPlayer player, KultivatorNpc npc, String id, int n) {
		ServerLevel level = (ServerLevel) player.level();
		long situs = situs(npc);
		double diskon = Barang.diskon(player, npc.town(), npc.role() == NpcRole.EMPU);
		Barisan found = null;
		for (Barisan barisan : stok(level, situs, diskon)) {
			if (barisan.id.equals(id)) {
				found = barisan;
			}
		}
		if (found == null) {
			return;
		}
		CompoundTag sold = terjual(level.getServer(), situs);
		int left = Math.max(0, found.stok - sold.getIntOr(found.id, 0));
		int count = Math.min(n, left);
		if (count <= 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.habis"), false);
			return;
		}
		if (Barang.pita(player) < found.pitaMin) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.terkunci"), false);
			return;
		}
		long total = found.harga * count;
		if (!NilaiBatu.pay(player, total)) {
			Lembar.kurangUang(player, total);
			return;
		}
		sold.putInt(found.id, sold.getIntOr(found.id, 0) + count);
		EkonomiData.get(level.getServer()).touch();
		long hari = Barang.hari(level.getServer());
		Component name = Component.empty();
		for (int i = 0; i < count; i++) {
			for (ItemStack piece : potongan(found)) {
				name = piece.getHoverName();
				Barang.beri(player, Barang.tandaiAsal(piece, hari));
			}
		}
		SosokApi.gerak(npc, GerakId.SERU_DAGANG, 0, 30, PropId.KANTONG_UANG, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.6F, 1.1F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", name, NilaiBatu.text(total)), true);
	}

	/** Repair price of the held item: 2 % of its value per 10 % durability missing (0 = nothing to repair). */
	static long hargaPerbaikan(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamaged() || stack.getMaxDamage() <= 0) {
			return 0L;
		}
		long value = Math.max(EkonomiApi.harga(stack), 10L);
		double tenths = 10.0D * stack.getDamageValue() / stack.getMaxDamage();
		return Math.max(1L, Math.round(value * PERBAIKI_PER_10 * tenths * Barang.skala()));
	}

	private static void perbaiki(ServerPlayer player, KultivatorNpc npc) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		long price = hargaPerbaikan(hand);
		if (price <= 0L) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.tak_perlu_perbaiki"), false);
			return;
		}
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		hand.setDamageValue(0);
		SosokApi.gerak(npc, GerakId.PALU_LANDASAN, 0, 60, PropId.PALU, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.0F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.diperbaiki", hand.getHoverName(), NilaiBatu.text(price)), true);
	}

	static int nilaiSisa(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return Math.max(0, NILAI_GRATIS - EkonomiData.get(server).harian(player.getUUID(), Barang.hari(server), "nilai"));
	}

	/** Appraisal: the hidden sub-quality, refinement and power of a forged artifact, or a city weapon's stats. */
	static void nilai(ServerPlayer player) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		MinecraftServer server = player.level().getServer();
		if (nilaiSisa(player) <= 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.nilai_habis", NILAI_GRATIS), false);
			return;
		}
		if (ArtifactItem.is(hand)) {
			CompoundTag data = ArtifactItem.data(hand);
			EkonomiData.get(server).tambahHarian(player.getUUID(), Barang.hari(server), "nilai", 1);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.nilai.pusaka", hand.getHoverName(),
					Component.translatable("kultivator.tempat_ekonomi.toko.sub." + ArtifactItem.sub(data)), ArtifactItem.refine(data),
					dev.kultivator.harta.HartaText.decimal(ArtifactItem.power(hand)), NilaiBatu.text(EkonomiApi.harga(hand))), true);
			return;
		}
		long value = EkonomiApi.harga(hand);
		if (value <= 0L) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.nilai.tak_bernilai"), false);
			return;
		}
		EkonomiData.get(server).tambahHarian(player.getUUID(), Barang.hari(server), "nilai", 1);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.nilai.barang", hand.getHoverName(), NilaiBatu.text(value)), true);
	}

	private TokoSenjata() {
	}
}

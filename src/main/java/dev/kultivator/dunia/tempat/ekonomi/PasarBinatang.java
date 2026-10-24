package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pasar Binatang Roh, the capital's spirit beast market (DESIGN 38.B.3 {@code pasar_binatang}, 38.M.2.2, screen
 * {@code pasar_binatang} of 38.U.3.9). Pens: a spirit wolf cub (120 NB) or a spirit fox cub (160 NB), already awakened as
 * the buyer's spirit beast (tier 0), one cub per player every 3 world days, from Foundation on. Offerings: the Spirit Beast
 * Pill at {@code Harga.value × 1.2}, 2 a day per market. Trainer: {@code 15 × tier} NB (tier 0 counts as 1) for each of the
 * player's awakened beasts within 24 blocks, once per beast and day, worth {@code 50 × (tier + 1)} spirit XP. Cores: the
 * market sells two beast cores a day at their value and buys the player's cores at half their value (38.M.5: nothing
 * bought back above 0.5 × price). Clerk: {@link NpcRole#PEDAGANG_BINATANG}.
 */
public final class PasarBinatang {

	public static final long HARGA_SERIGALA = 120L;
	public static final long HARGA_RUBAH = 160L;
	public static final int HARI_ANAK = 3;
	public static final int PITA_ANAK = 2;
	public static final double SESAJI_FAKTOR = 1.2D;
	public static final int SESAJI_STOK = 2;
	public static final long LATIH_PER_TIER = 15L;
	public static final double LATIH_XP = 50.0D;
	public static final double BELI_KEMBALI = 0.5D;
	static final Set<NpcRole> PERAN = Set.of(NpcRole.PEDAGANG_BINATANG);

	static void init() {
		TempatLayar.daftar(LembarKunci.PasarBinatang.LAYAR, PasarBinatang::aksi, PasarBinatang::bangun);
	}

	private static long situs(KultivatorNpc npc) {
		return npc.anchorSite() != 0L ? npc.anchorSite() : npc.town();
	}

	private static CompoundTag hariIni(MinecraftServer server, long situs) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situs);
		CompoundTag day = site.getCompoundOrEmpty("binatang_hari");
		long hari = Barang.hari(server);
		if (day.getLongOr("hari", Long.MIN_VALUE) != hari) {
			day = new CompoundTag();
			day.putLong("hari", hari);
			site.put("binatang_hari", day);
			data.touch();
		}
		return day;
	}

	/** Days until the player may buy the next cub (0 = now). */
	static int sisaHariAnak(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long last = EkonomiData.get(server).pemain(player.getUUID()).getLongOr("binatang_anak", Long.MIN_VALUE / 2);
		return (int) Math.max(0L, last + HARI_ANAK - Barang.hari(server));
	}

	/** The two cores of the day at site {@code situs}. */
	private static List<ItemStack> intiHariIni(MinecraftServer server, long situs) {
		RandomSource random = RandomSource.create(situs * 0x9E37L + Barang.hari(server) * 131L);
		List<ItemStack> out = new ArrayList<>();
		Element[] all = Element.values();
		for (int i = 0; i < 2; i++) {
			out.add(BeastCores.create(all[random.nextInt(all.length)], 1 + random.nextInt(i == 0 ? 2 : 3)));
		}
		return out;
	}

	private static List<Mob> binatang(ServerPlayer player) {
		List<Mob> out = new ArrayList<>();
		for (Mob beast : SpiritBeasts.loadedBeasts(player.getUUID())) {
			if (beast.level() == player.level() && beast.distanceToSqr(player) <= 24.0D * 24.0D && out.size() < 8) {
				out.add(beast);
			}
		}
		return out;
	}

	private static long hargaLatih(int tier) {
		return Barang.harga(LATIH_PER_TIER * Math.max(1, tier), 1.0D);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.pasar_binatang.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_PASAR, npc);
		if (!EkonomiConfig.get().toko) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		double diskon = Barang.diskon(player, npc.town(), false);
		int band = Barang.pita(player);
		int wait = sisaHariAnak(player);
		ListTag pens = new ListTag();
		for (int i = 0; i < 2; i++) {
			boolean wolf = i == 0;
			long price = Barang.harga(wolf ? HARGA_SERIGALA : HARGA_RUBAH, 1.0D - diskon);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, wolf ? "serigala" : "rubah");
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang." + (wolf ? "serigala" : "rubah")));
			row.putString(LembarKunci.ENTITAS, BuiltInRegistries.ENTITY_TYPE.getKey(wolf ? EntityTypes.WOLF : EntityTypes.FOX).toString());
			row.putLong(LembarKunci.HARGA, price);
			row.putInt(LembarKunci.STOK, wait == 0 ? 1 : 0);
			row.putInt(LembarKunci.SISA_HARI, wait);
			if (band < PITA_ANAK) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.pita"));
			} else if (wait > 0) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_BATAS);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.tunggu", wait));
			} else {
				row.putString(LembarKunci.KEADAAN, Lembar.keadaan(player, price, false));
			}
			pens.add(row);
		}
		tag.put(LembarKunci.PasarBinatang.KANDANG, pens);
		long situs = situs(npc);
		CompoundTag day = hariIni(server, situs);
		ListTag offerings = new ListTag();
		ItemStack pill = Harga.item("spirit_beast_pill");
		if (!pill.isEmpty()) {
			long price = Barang.harga(Math.round(EkonomiApi.harga(pill) * SESAJI_FAKTOR), 1.0D - diskon);
			int left = Math.max(0, SESAJI_STOK - day.getIntOr("sesaji", 0));
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "sesaji");
			LembarKunci.barang(row, pill, player.registryAccess());
			row.putLong(LembarKunci.HARGA, price);
			row.putInt(LembarKunci.STOK, left);
			row.putString(LembarKunci.KEADAAN, left <= 0 ? LembarKunci.K_HABIS : Lembar.keadaan(player, price, false));
			offerings.add(row);
		}
		tag.put(LembarKunci.PasarBinatang.SESAJI, offerings);
		ListTag beasts = new ListTag();
		CompoundTag trained = day.getCompoundOrEmpty("dilatih");
		for (Mob beast : binatang(player)) {
			BeastData data = SpiritBeasts.data(beast);
			if (data == null) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, beast.getUUID().toString());
			LembarKunci.teks(row, LembarKunci.NAMA, beast.getDisplayName());
			row.putInt(LembarKunci.PasarBinatang.TIER, data.tier());
			row.putLong(LembarKunci.HARGA, hargaLatih(data.tier()));
			row.putBoolean(LembarKunci.PasarBinatang.SUDAH, trained.getBooleanOr(beast.getUUID().toString(), false));
			beasts.add(row);
		}
		tag.put(LembarKunci.PasarBinatang.BINATANG, beasts);
		ListTag cores = new ListTag();
		List<ItemStack> forSale = intiHariIni(server, situs);
		for (int i = 0; i < forSale.size(); i++) {
			ItemStack core = forSale.get(i);
			long price = Barang.harga(EkonomiApi.harga(core), 1.0D - diskon);
			boolean sold = day.getBooleanOr("inti." + i, false);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "beli." + i);
			LembarKunci.barang(row, core, player.registryAccess());
			row.putLong(LembarKunci.HARGA, price);
			row.putBoolean(LembarKunci.PasarBinatang.JUAL, false);
			row.putString(LembarKunci.KEADAAN, sold ? LembarKunci.K_HABIS : Lembar.keadaan(player, price, false));
			cores.add(row);
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize() && cores.size() < 14; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!BeastCores.isCore(stack)) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "jual." + slot);
			LembarKunci.barang(row, stack, player.registryAccess());
			row.putLong(LembarKunci.HARGA, hargaJual(stack));
			row.putBoolean(LembarKunci.PasarBinatang.JUAL, true);
			row.putString(LembarKunci.KEADAAN, Barang.baruDibeli(stack, Barang.hari(server)) ? LembarKunci.K_DITOLAK : LembarKunci.K_OK);
			cores.add(row);
		}
		tag.put(LembarKunci.PasarBinatang.INTI, cores);
		return tag;
	}

	/**
	 * What the trader pays for a core: half its value, and never more than half of what he sells it for when the price
	 * scale is turned below 1 (38.M.5: no buy-back above 0.5 of the sale price; a 0.5 scale made buy-then-sell print stones).
	 */
	private static long hargaJual(ItemStack core) {
		return Math.max(1L, (long) Math.floor(EkonomiApi.harga(core) * BELI_KEMBALI * Math.min(1.0D, Barang.skala())));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !EkonomiConfig.get().toko) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		String id = LembarKunci.arg(parts, 1);
		switch (parts[0]) {
			case LembarKunci.PasarBinatang.OP_BELI -> {
				if ("serigala".equals(id) || "rubah".equals(id)) {
					anak(player, npc, "serigala".equals(id));
				} else if ("sesaji".equals(id)) {
					sesaji(player, npc);
				} else if (id.startsWith("beli.")) {
					beliInti(player, npc, LembarKunci.argInt(id.split("\\."), 1, -1));
				}
			}
			case LembarKunci.PasarBinatang.OP_LATIH -> latih(player, npc, id);
			case LembarKunci.PasarBinatang.OP_JUAL -> {
				if (id.startsWith("jual.")) {
					jualInti(player, LembarKunci.argInt(id.split("\\."), 1, -1));
				}
			}
			default -> {
			}
		}
	}

	private static void anak(ServerPlayer player, KultivatorNpc npc, boolean wolf) {
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		if (Barang.pita(player) < PITA_ANAK) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.pita"), false);
			return;
		}
		int wait = sisaHariAnak(player);
		if (wait > 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.tunggu", wait), false);
			return;
		}
		long price = Barang.harga(wolf ? HARGA_SERIGALA : HARGA_RUBAH, 1.0D - Barang.diskon(player, npc.town(), false));
		if (NilaiBatu.total(player) < price) {
			Lembar.kurangUang(player, price);
			return;
		}
		Mob cub = wolf ? EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT) : EntityTypes.FOX.create(level, EntitySpawnReason.EVENT);
		if (cub == null) {
			return;
		}
		BlockPos at = npc.blockPosition().relative(npc.getDirection(), 2);
		cub.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
		if (cub instanceof Wolf pup) {
			pup.setBaby(true);
			pup.tame(player);
		} else if (cub instanceof Fox kit) {
			kit.setBaby(true);
		}
		if (!level.addFreshEntity(cub)) {
			return;
		}
		if (!SpiritBeasts.awakenFor(player, cub)) {
			cub.discard();
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.gagal"), false);
			return;
		}
		NilaiBatu.pay(player, price);
		EkonomiData data = EkonomiData.get(server);
		data.pemain(player.getUUID()).putLong("binatang_anak", Barang.hari(server));
		data.touch();
		cub.setPersistenceRequired();
		SosokApi.gerak(npc, GerakId.SERU_DAGANG, 0, 40);
		level.playSound(null, at, wolf ? SoundEvents.WOLF_AMBIENT_BABY.value() : SoundEvents.FOX_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.6F);
		Advancements.grant(player, "tempat/anak_binatang");
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.dibeli." + (wolf ? "serigala" : "rubah"),
				NilaiBatu.text(price)), true);
	}

	private static void sesaji(ServerPlayer player, KultivatorNpc npc) {
		MinecraftServer server = player.level().getServer();
		CompoundTag day = hariIni(server, situs(npc));
		if (day.getIntOr("sesaji", 0) >= SESAJI_STOK) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.habis"), false);
			return;
		}
		ItemStack pill = Harga.item("spirit_beast_pill");
		if (pill.isEmpty()) {
			return;
		}
		long price = Barang.harga(Math.round(EkonomiApi.harga(pill) * SESAJI_FAKTOR), 1.0D - Barang.diskon(player, npc.town(), false));
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		day.putInt("sesaji", day.getIntOr("sesaji", 0) + 1);
		EkonomiData.get(server).touch();
		Barang.beri(player, Barang.tandaiAsal(pill, Barang.hari(server)));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", pill.getHoverName(), NilaiBatu.text(price)), true);
	}

	private static void beliInti(ServerPlayer player, KultivatorNpc npc, int index) {
		MinecraftServer server = player.level().getServer();
		long situs = situs(npc);
		List<ItemStack> forSale = intiHariIni(server, situs);
		CompoundTag day = hariIni(server, situs);
		if (index < 0 || index >= forSale.size() || day.getBooleanOr("inti." + index, false)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.habis"), false);
			return;
		}
		ItemStack core = forSale.get(index);
		long price = Barang.harga(EkonomiApi.harga(core), 1.0D - Barang.diskon(player, npc.town(), false));
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		day.putBoolean("inti." + index, true);
		EkonomiData.get(server).touch();
		Barang.beri(player, Barang.tandaiAsal(core, Barang.hari(server)));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", core.getHoverName(), NilaiBatu.text(price)), true);
	}

	private static void jualInti(ServerPlayer player, int slot) {
		if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		MinecraftServer server = player.level().getServer();
		if (!BeastCores.isCore(stack) || Barang.baruDibeli(stack, Barang.hari(server))) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.asal_toko", Barang.ASAL_HARI), false);
			return;
		}
		long pay = hargaJual(stack);
		Component name = stack.getHoverName();
		stack.shrink(1);
		player.getInventory().setChanged();
		NilaiBatu.give(player, pay);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dijual", name, NilaiBatu.text(pay)), true);
	}

	private static void latih(ServerPlayer player, KultivatorNpc npc, String uuid) {
		UUID id;
		try {
			id = UUID.fromString(uuid);
		} catch (IllegalArgumentException exception) {
			return;
		}
		Mob beast = null;
		for (Mob each : binatang(player)) {
			if (each.getUUID().equals(id)) {
				beast = each;
			}
		}
		BeastData data = beast == null ? null : SpiritBeasts.data(beast);
		if (data == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag day = hariIni(server, situs(npc));
		CompoundTag trained = day.getCompoundOrEmpty("dilatih");
		if (trained.getBooleanOr(uuid, false)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.sudah_dilatih"), false);
			return;
		}
		long price = hargaLatih(data.tier());
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		trained.putBoolean(uuid, true);
		day.put("dilatih", trained);
		EkonomiData.get(server).touch();
		double xp = LATIH_XP * (data.tier() + 1);
		SpiritBeasts.addXp(beast, xp);
		SosokApi.gerak(npc, GerakId.HORMAT, 0, 20);
		player.level().playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.4F, 1.8F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.pasar_binatang.dilatih", beast.getDisplayName(), (int) xp,
				NilaiBatu.text(price)), true);
	}

	private PasarBinatang() {
	}
}

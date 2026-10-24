package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rumah Uang Seribu Tael (DESIGN 38.M.2.2, screen 38.U.3.5): deposit every stone carried (no interest, ever), withdraw at a
 * 1 % fee (min 1 NB, a sink into the town treasury), Surat Wesel ({@link SuratWesel}), pawn ({@link Gadai}), the Batu Asal
 * counter (buy at 180 NB, at most 8 a week and only from Immortal Ascension; sell at 90 NB, at most 8 a day: the gap
 * stops any loop, 38.M.0) and the vault ({@link Brankas}). The deposit lives in {@link EkonomiData} and is the same at
 * every Rumah Uang.
 */
public final class RumahUang {

	public static final double BIAYA_TARIK = 0.01D;
	public static final long ASAL_BELI = 180L;
	public static final long ASAL_JUAL = 90L;
	public static final int ASAL_BELI_MINGGU = 8;
	public static final int ASAL_JUAL_HARI = 8;
	public static final int RIWAYAT_MAKS = 16;

	private static final String SALDO = "bank_saldo";
	private static final String RIWAYAT = "bank_riwayat";
	private static final Set<NpcRole> PERAN = Set.of(NpcRole.BANKIR);
	/** The site of the bank the player used last (fees go to that town's treasury). */
	private static final Map<UUID, Long> SITUS = new HashMap<>();

	static void init() {
		TempatLayar.daftar(LembarKunci.Bank.LAYAR, RumahUang::aksi, RumahUang::bangun);
	}

	static void lupakan(UUID id) {
		SITUS.remove(id);
	}

	static long situsTerakhir(ServerPlayer player) {
		return SITUS.getOrDefault(player.getUUID(), 0L);
	}

	/** Rank Warga or better at the bank's town (18 vault slots). */
	static boolean wargaDikenal(ServerPlayer player) {
		return KotaBesarApi.pangkatNamaBaik(player, situsTerakhir(player)) >= LembarKunci.PANGKAT_WARGA;
	}

	public static long saldo(MinecraftServer server, UUID player) {
		return Math.max(0L, EkonomiData.get(server).pemain(player).getLongOr(SALDO, 0L));
	}

	private static void setSaldo(MinecraftServer server, UUID player, long value) {
		EkonomiData data = EkonomiData.get(server);
		data.pemain(player).putLong(SALDO, Math.max(0L, value));
		data.touch();
	}

	/** Takes {@code nb} from the deposit; false (nothing taken) when it is smaller. */
	static boolean ambilSaldo(ServerPlayer player, long nb) {
		MinecraftServer server = player.level().getServer();
		long have = saldo(server, player.getUUID());
		if (have < nb) {
			return false;
		}
		setSaldo(server, player.getUUID(), have - nb);
		return true;
	}

	/** Adds a history line (signed NB, newest first, {@link #RIWAYAT_MAKS} kept). */
	static void riwayat(ServerPlayer player, long nb, String kunci, Object arg) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		ListTag list = data.daftar(player.getUUID(), RIWAYAT);
		CompoundTag row = new CompoundTag();
		row.putLong(LembarKunci.HARI, Barang.hari(server));
		row.putLong(LembarKunci.NB, nb);
		LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable(kunci, String.valueOf(arg)));
		list.add(0, row);
		while (list.size() > RIWAYAT_MAKS) {
			list.remove(list.size() - 1);
		}
		data.touch();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		SITUS.put(player.getUUID(), npc.anchorSite() != 0L ? npc.anchorSite() : npc.town());
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.bank.judul", LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_PASAR, npc);
		if (!EkonomiConfig.get().bank) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		tag.putLong(LembarKunci.Bank.SALDO, saldo(server, player.getUUID()));
		tag.putFloat(LembarKunci.Bank.BIAYA_TARIK, (float) BIAYA_TARIK);
		tag.put(LembarKunci.RIWAYAT, EkonomiData.get(server).daftar(player.getUUID(), RIWAYAT).copy());
		CompoundTag wesel = new CompoundTag();
		wesel.putFloat(LembarKunci.BIAYA, (float) SuratWesel.BIAYA);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		long note = SuratWesel.nilai(hand);
		if (note > 0L) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.teks(tangan, LembarKunci.NAMA, hand.getHoverName());
			tangan.putLong(LembarKunci.NB, note);
			wesel.put(LembarKunci.TANGAN, tangan);
		}
		tag.put(LembarKunci.Bank.WESEL, wesel);
		tag.put(LembarKunci.Bank.GADAI, Gadai.lembar(player));
		tag.put(LembarKunci.Bank.TUKAR, tukarLembar(player));
		tag.put(LembarKunci.Bank.BRANKAS, Brankas.lembar(player));
		return tag;
	}

	private static CompoundTag tukarLembar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		CompoundTag tag = new CompoundTag();
		tag.putLong(LembarKunci.Bank.BELI, ASAL_BELI);
		tag.putLong(LembarKunci.Bank.JUAL, ASAL_JUAL);
		tag.putInt(LembarKunci.Bank.BELI_SISA, (int) Math.max(0L, ASAL_BELI_MINGGU - data.mingguan(player.getUUID(), hari, "asal_beli")));
		tag.putInt(LembarKunci.Bank.JUAL_SISA, Math.max(0, ASAL_JUAL_HARI - data.harian(player.getUUID(), hari, "asal_jual")));
		boolean boleh = Cultivators.get(player).realm().ordinal() >= Realm.IMMORTAL_ASCENSION.ordinal();
		tag.putBoolean(LembarKunci.Bank.BOLEH_BELI, boleh);
		if (!boleh) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.bank.tukar.ranah"));
		}
		tag.putInt(LembarKunci.Bank.BATU_ASAL, NilaiBatu.origin(player));
		return tag;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		if (!EkonomiConfig.get().bank) {
			return;
		}
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case LembarKunci.Bank.OP_SETOR -> setor(player);
			case LembarKunci.Bank.OP_TARIK -> tarik(player, LembarKunci.argLong(parts, 1, 0L));
			case LembarKunci.Bank.OP_WESEL -> SuratWesel.tulis(player, LembarKunci.argLong(parts, 1, 0L));
			case LembarKunci.Bank.OP_CAIR -> SuratWesel.cair(player, player.getItemInHand(InteractionHand.MAIN_HAND));
			case LembarKunci.Bank.OP_GADAI -> Gadai.gadai(player);
			case LembarKunci.Bank.OP_TEBUS -> Gadai.tebus(player, LembarKunci.argLong(parts, 1, -1L));
			case LembarKunci.Bank.OP_TUKAR_BELI -> tukarBeli(player, LembarKunci.argInt(parts, 1, 1));
			case LembarKunci.Bank.OP_TUKAR_JUAL -> tukarJual(player, LembarKunci.argInt(parts, 1, 1));
			case LembarKunci.Bank.OP_SIMPAN -> Brankas.simpan(player);
			case LembarKunci.Bank.OP_AMBIL -> Brankas.ambil(player, LembarKunci.argInt(parts, 1, -1));
			default -> {
				return;
			}
		}
		SosokApi.gerak(npc, GerakId.HITUNG_SEMPOA, 0, 30, PropId.SEMPOA, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
	}

	/** Every spirit stone carried goes into the deposit at its NB value. */
	private static void setor(ServerPlayer player) {
		long sum = 0L;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			int grade = SpiritStones.grade(stack);
			if (grade >= 0 && grade < NilaiBatu.VALUE.length) {
				sum += NilaiBatu.VALUE[grade] * stack.getCount();
				stack.setCount(0);
			}
		}
		inventory.setChanged();
		if (sum <= 0L) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.setor.kosong"), false);
			return;
		}
		MinecraftServer server = player.level().getServer();
		setSaldo(server, player.getUUID(), saldo(server, player.getUUID()) + sum);
		riwayat(player, sum, "kultivator.tempat_ekonomi.bank.riwayat.setor", sum);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.6F, 1.4F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.setor", NilaiBatu.text(sum)), true);
	}

	private static void tarik(ServerPlayer player, long nb) {
		if (nb <= 0L) {
			return;
		}
		long fee = Math.max(1L, Math.round(nb * BIAYA_TARIK));
		if (!ambilSaldo(player, nb + fee)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.saldo_kurang", NilaiBatu.text(nb + fee)), false);
			return;
		}
		NilaiBatu.give(player, nb);
		KasKota.tambah(player.level().getServer(), situsTerakhir(player), fee);
		riwayat(player, -(nb + fee), "kultivator.tempat_ekonomi.bank.riwayat.tarik", nb);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.tarik", NilaiBatu.text(nb), fee), true);
	}

	private static void tukarBeli(ServerPlayer player, int n) {
		if (n <= 0) {
			return;
		}
		if (Cultivators.get(player).realm().ordinal() < Realm.IMMORTAL_ASCENSION.ordinal()) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.tukar.ranah"), false);
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		int left = (int) Math.max(0L, ASAL_BELI_MINGGU - data.mingguan(player.getUUID(), hari, "asal_beli"));
		int take = Math.min(n, left);
		if (take <= 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.tukar.batas_beli", ASAL_BELI_MINGGU), false);
			return;
		}
		long price = ASAL_BELI * take;
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		data.tambahMingguan(player.getUUID(), hari, "asal_beli", take);
		NilaiBatu.giveOrigin(player, take);
		riwayat(player, -price, "kultivator.tempat_ekonomi.bank.riwayat.asal_beli", take);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.tukar.dibeli", take, NilaiBatu.text(price)), true);
	}

	private static void tukarJual(ServerPlayer player, int n) {
		if (n <= 0) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		int left = Math.max(0, ASAL_JUAL_HARI - data.harian(player.getUUID(), hari, "asal_jual"));
		int take = Math.min(Math.min(n, left), NilaiBatu.origin(player));
		if (take <= 0) {
			Lembar.pesan(player, Component.translatable(left <= 0 ? "kultivator.tempat_ekonomi.bank.tukar.batas_jual"
					: "kultivator.tempat_ekonomi.bank.tukar.tak_punya", ASAL_JUAL_HARI), false);
			return;
		}
		if (!NilaiBatu.payOrigin(player, take)) {
			return;
		}
		data.tambahHarian(player.getUUID(), hari, "asal_jual", take);
		long pay = ASAL_JUAL * take;
		NilaiBatu.give(player, pay);
		riwayat(player, pay, "kultivator.tempat_ekonomi.bank.riwayat.asal_jual", take);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.tukar.dijual", take, NilaiBatu.text(pay)), true);
	}

	private RumahUang() {
	}
}

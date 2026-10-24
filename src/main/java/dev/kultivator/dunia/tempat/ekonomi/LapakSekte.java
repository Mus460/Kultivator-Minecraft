package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lapak Sekte (DESIGN 38.M.4.1): one of the six rented kiosks of the capital's Deret Lapak Sekte, {@code 20 × (band + 1)}
 * NB per 7 days for a hall of level 2 or more, rented at the Record Office. 18 slots filled with the sect's own goods (the
 * 38 sell filter: no stones, rings, filled containers or phase-9 wares, nothing a 38 shop sold in the last 3 days); the
 * price is at most 1.5 × its value. Once a world day every stack sells with a chance by its relative price h = price /
 * value (≤ 1.0: 80 %, ≤ 1.2: 50 %, ≤ 1.5: 20 %), the guard disciple's nature shifting it ±10 % (cunning +, lazy −), up
 * to a daily volume of {@code 40 × (band + 1) × hall level} NB; a 5 % market tax goes to the town treasury; the takings
 * wait in the stall's cash box and the ledger. The guard is the following disciple nearest to the player when renting
 * (sent away with reason {@code lapak} until the lease ends); while a player is within 48 blocks of the kiosk a body with
 * the guard's looks stands there calling out to buyers ({@code kotabesar/lapak_sekte} anchors, forwarded by 38KS).
 */
public final class LapakSekte {

	public static final int SLOT = 18;
	public static final int HARI_SEWA = 7;
	public static final int LEVEL_MIN = 2;
	public static final double HARGA_MAKS = 1.5D;
	public static final double PAJAK = 0.05D;
	public static final int KIOS = 6;
	private static final String KUNCI = "lapak";
	/** Guard bodies standing at a kiosk: anchor entity id → body. */
	private static final Map<Integer, KultivatorNpc> PENJAGA = new HashMap<>();

	static void init() {
		Halls.addReturnHandler(KUNCI, (level, owner, stored, info) -> null);
	}

	static void bersihkan() {
		PENJAGA.values().forEach(KultivatorNpc::vanish);
		PENJAGA.clear();
	}

	static long sewa(int band) {
		return Barang.harga(20L * (band + 1), 1.0D);
	}

	static long volume(int band, int level) {
		return 40L * (band + 1) * Math.max(1, level);
	}

	private static CompoundTag data(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).pemain(player.getUUID()).getCompoundOrEmpty(KUNCI);
	}

	private static boolean aktif(MinecraftServer server, CompoundTag lapak) {
		return !lapak.isEmpty() && lapak.getLongOr("sampai", 0L) >= Barang.hari(server);
	}

	/** The chance in percent that one stack sells today at relative price {@code h}. */
	static int peluang(double h) {
		return h <= 1.0D ? 80 : h <= 1.2D ? 50 : h <= 1.5D ? 20 : 0;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet block (buku_kas.lapak)

	static CompoundTag lembar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = new CompoundTag();
		int level = Halls.level(player.getUUID());
		CompoundTag lapak = data(player);
		boolean ada = aktif(server, lapak);
		tag.putBoolean(LembarKunci.BukuKas.ADA, ada);
		int band = Barang.pita(player);
		tag.putLong(LembarKunci.BukuKas.SEWA, sewa(band));
		tag.putLong(LembarKunci.BukuKas.VOLUME_MAKS, volume(band, level));
		if (!EkonomiConfig.get().lapak) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.tutup"));
			return tag;
		}
		if (level < LEVEL_MIN) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.lapak.level", LEVEL_MIN));
		}
		if (!ada) {
			return tag;
		}
		LembarKunci.teks(tag, LembarKunci.BukuKas.KIOS, Component.translatable("kultivator.tempat_ekonomi.lapak.kios", lapak.getIntOr("nomor", 0) + 1));
		tag.putInt(LembarKunci.BukuKas.SEWA_SISA_HARI, (int) Math.max(0L, lapak.getLongOr("sampai", 0L) - Barang.hari(server)));
		if (!lapak.getStringOr("penjaga_nama", "").isEmpty()) {
			LembarKunci.teks(tag, LembarKunci.BukuKas.PENJAGA, Component.literal(lapak.getStringOr("penjaga_nama", "")));
		}
		tag.putLong(LembarKunci.BukuKas.KAS, lapak.getLongOr("kas", 0L));
		net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
		CompoundTag slots = lapak.getCompoundOrEmpty("isi");
		for (int slot = 0; slot < SLOT; slot++) {
			CompoundTag entry = slots.getCompoundOrEmpty(Integer.toString(slot));
			if (entry.isEmpty()) {
				continue;
			}
			ItemStack stack = Barang.muat(entry.get("barang"), player.registryAccess());
			long value = Math.max(1L, EkonomiApi.harga(stack) * stack.getCount());
			long price = entry.getLongOr("harga", value);
			CompoundTag row = new CompoundTag();
			row.putInt(LembarKunci.SLOT, slot);
			LembarKunci.barang(row, stack, player.registryAccess());
			row.putLong(LembarKunci.HARGA, price);
			row.putLong(LembarKunci.NILAI, value);
			row.putInt(LembarKunci.PELUANG, peluang(price / (double) value));
			rows.add(row);
		}
		tag.put(LembarKunci.DAFTAR, rows);
		return tag;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions (Record Office clerk, or the stall's guard body)

	static void aksi(ServerPlayer player, String verb, String[] parts) {
		if (!EkonomiConfig.get().lapak) {
			return;
		}
		switch (verb) {
			case LembarKunci.BukuKas.OP_LAPAK_SEWA -> sewaLapak(player);
			case LembarKunci.BukuKas.OP_LAPAK_ISI -> isi(player);
			case LembarKunci.BukuKas.OP_LAPAK_HARGA -> harga(player, LembarKunci.argInt(parts, 1, -1), LembarKunci.argLong(parts, 2, -1L));
			case LembarKunci.BukuKas.OP_LAPAK_AMBIL -> ambil(player, LembarKunci.argInt(parts, 1, -1));
			case LembarKunci.BukuKas.OP_LAPAK_KAS -> kas(player);
			default -> {
			}
		}
	}

	/** The capital of the clerk the player talks to (the kiosk row belongs to it); 0 at a hub. */
	private static final Map<UUID, Long> SITUS = new HashMap<>();

	static void ingatSitus(ServerPlayer player, long situs) {
		SITUS.put(player.getUUID(), situs);
	}

	static void lupakan(UUID id) {
		SITUS.remove(id);
	}

	private static void sewaLapak(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		int level = Halls.level(owner);
		if (level < LEVEL_MIN) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.level", LEVEL_MIN), false);
			return;
		}
		long situs = SITUS.getOrDefault(owner, 0L);
		EkonomiData data = EkonomiData.get(server);
		CompoundTag lapak = data.pemain(owner).getCompoundOrEmpty(KUNCI);
		boolean renew = aktif(server, lapak);
		int nomor = renew ? lapak.getIntOr("nomor", 0) : kiosBebas(server, situs);
		if (nomor < 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.penuh"), false);
			return;
		}
		int band = Barang.pita(player);
		long price = sewa(band);
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		long hari = Barang.hari(server);
		lapak.putLong("situs", renew ? lapak.getLongOr("situs", situs) : situs);
		lapak.putInt("nomor", nomor);
		lapak.putLong("sampai", Math.max(hari, lapak.getLongOr("sampai", hari)) + HARI_SEWA);
		lapak.putInt("pita", band);
		if (!renew || lapak.getStringOr("penjaga_nama", "").isEmpty()) {
			pilihPenjaga(player, lapak);
		}
		data.pemain(owner).put(KUNCI, lapak);
		data.touch();
		KasKota.tambah(server, situs, price);
		BukuKas.catat(server, owner, LembarKunci.BukuKas.SUMBER_SEWA, 0, -price, "kultivator.tempat_ekonomi.kas.catatan.sewa", nomor + 1, HARI_SEWA);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.disewa", nomor + 1, HARI_SEWA, NilaiBatu.text(price)), true);
	}

	private static int kiosBebas(MinecraftServer server, long situs) {
		boolean[] taken = new boolean[KIOS];
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		for (UUID id : data.pemainIds()) {
			CompoundTag other = data.pemain(id).getCompoundOrEmpty(KUNCI);
			if (!other.isEmpty() && other.getLongOr("situs", 0L) == situs && other.getLongOr("sampai", 0L) >= hari) {
				int n = other.getIntOr("nomor", -1);
				if (n >= 0 && n < KIOS) {
					taken[n] = true;
				}
			}
		}
		for (int i = 0; i < KIOS; i++) {
			if (!taken[i]) {
				return i;
			}
		}
		return -1;
	}

	/** The following disciple nearest the player keeps the stall (away until the lease ends). */
	private static void pilihPenjaga(ServerPlayer player, CompoundTag lapak) {
		DiscipleEntity best = null;
		double bestSq = Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			double d = disciple.distanceToSqr(player);
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.level() == player.level() && d < bestSq) {
				bestSq = d;
				best = disciple;
			}
		}
		if (best == null) {
			Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.lapak.tanpa_penjaga").withStyle(ChatFormatting.GRAY));
			return;
		}
		lapak.putString("penjaga_nama", best.getDisplayName().getString());
		lapak.putString("penjaga_jiwa", best.soulId().toString());
		lapak.putInt("penjaga_sosok", SosokApi.sosok(best).pack());
		lapak.putInt("penjaga_watak", watak(best));
		long returnAt = Barang.waktu(player.level().getServer()) + HARI_SEWA * 24000L;
		CompoundTag info = new CompoundTag();
		info.putInt("nomor", lapak.getIntOr("nomor", 0));
		Halls.storeAway(player.getUUID(), best, returnAt, KUNCI, info);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.lapak.penjaga", lapak.getStringOr("penjaga_nama", "")).withStyle(ChatFormatting.GOLD));
	}

	/** Nature effect on the sale chance: cunning +10, lazy −10, else 0 (36 P natures through the public API). */
	private static int watak(DiscipleEntity disciple) {
		try {
			List<Sifat> traits = SekteApi.hati().traits(disciple.data());
			if (traits.contains(Sifat.LICIK)) {
				return 10;
			}
			if (traits.contains(Sifat.MALAS)) {
				return -10;
			}
		} catch (RuntimeException ignored) {
			// module P off: no nature effect
		}
		return 0;
	}

	private static void isi(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag lapak = data(player);
		if (!aktif(server, lapak)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.belum_sewa"), false);
			return;
		}
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!Barang.bolehDijual(hand) || Barang.baruDibeli(hand, Barang.hari(server))) {
			Lembar.pesan(player, Component.translatable(Barang.berisi(hand) ? "kultivator.ekonomi.cincin_berisi" : "kultivator.tempat_ekonomi.lapak.tolak"),
					false);
			return;
		}
		CompoundTag slots = lapak.getCompoundOrEmpty("isi");
		for (int slot = 0; slot < SLOT; slot++) {
			String key = Integer.toString(slot);
			if (!slots.contains(key)) {
				CompoundTag entry = new CompoundTag();
				entry.put("barang", Barang.simpan(hand.copy(), player.registryAccess()));
				entry.putLong("harga", Math.max(1L, EkonomiApi.harga(hand) * hand.getCount()));
				slots.put(key, entry);
				lapak.put("isi", slots);
				Component name = hand.getHoverName();
				hand.setCount(0);
				EkonomiData.get(server).touch();
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.diisi", name, slot + 1), true);
				return;
			}
		}
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.slot_penuh", SLOT), false);
	}

	private static void harga(ServerPlayer player, int slot, long nb) {
		CompoundTag lapak = data(player);
		CompoundTag entry = lapak.getCompoundOrEmpty("isi").getCompoundOrEmpty(Integer.toString(slot));
		if (entry.isEmpty() || nb <= 0L) {
			return;
		}
		ItemStack stack = Barang.muat(entry.get("barang"), player.registryAccess());
		long value = Math.max(1L, EkonomiApi.harga(stack) * stack.getCount());
		long capped = Math.min(nb, (long) Math.floor(value * HARGA_MAKS));
		entry.putLong("harga", Math.max(1L, capped));
		EkonomiData.get(player.level().getServer()).touch();
		if (capped < nb) {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.harga_maks", NilaiBatu.text(capped)));
		} else {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.harga", stack.getHoverName(), NilaiBatu.text(capped)), true);
		}
	}

	private static void ambil(ServerPlayer player, int slot) {
		CompoundTag lapak = data(player);
		CompoundTag slots = lapak.getCompoundOrEmpty("isi");
		String key = Integer.toString(slot);
		if (!slots.contains(key)) {
			return;
		}
		ItemStack stack = Barang.muat(slots.getCompoundOrEmpty(key).get("barang"), player.registryAccess());
		slots.remove(key);
		EkonomiData.get(player.level().getServer()).touch();
		Barang.beri(player, stack);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.diambil", stack.getHoverName()), true);
	}

	private static void kas(ServerPlayer player) {
		CompoundTag lapak = data(player);
		long kas = lapak.getLongOr("kas", 0L);
		if (kas <= 0L) {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.kas_kosong"));
			return;
		}
		lapak.putLong("kas", 0L);
		EkonomiData.get(player.level().getServer()).touch();
		NilaiBatu.give(player, kas);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lapak.kas_diambil", NilaiBatu.text(kas)), true);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The daily sale (every stall of the world, owner online or not)

	/** A new world day: every rented stall sells (once per day; a missed day is not repeated). */
	static void hariBaru(MinecraftServer server, long hari) {
		if (!EkonomiConfig.get().lapak) {
			return;
		}
		EkonomiData data = EkonomiData.get(server);
		for (UUID owner : data.pemainIds()) {
			CompoundTag lapak = data.pemain(owner).getCompoundOrEmpty(KUNCI);
			if (lapak.isEmpty() || lapak.getLongOr("dijual", Long.MIN_VALUE) >= hari) {
				continue;
			}
			lapak.putLong("dijual", hari);
			if (lapak.getLongOr("sampai", 0L) < hari - 1L) {
				continue;
			}
			jual(server, owner, lapak, hari);
		}
		data.touch();
	}

	private static void jual(MinecraftServer server, UUID owner, CompoundTag lapak, long hari) {
		RandomSource random = RandomSource.create(owner.getLeastSignificantBits() ^ hari * 0x9E3779B97F4A7C15L);
		long cap = volume(lapak.getIntOr("pita", 0), Halls.level(owner));
		int nature = lapak.getIntOr("penjaga_watak", 0);
		long gross = 0L;
		int sold = 0;
		CompoundTag slots = lapak.getCompoundOrEmpty("isi");
		for (String key : List.copyOf(slots.keySet())) {
			CompoundTag entry = slots.getCompoundOrEmpty(key);
			ItemStack stack = Barang.muat(entry.get("barang"), server.registryAccess());
			if (stack.isEmpty()) {
				slots.remove(key);
				continue;
			}
			long value = Math.max(1L, EkonomiApi.harga(stack) * stack.getCount());
			long price = entry.getLongOr("harga", value);
			int chance = Math.max(0, Math.min(95, peluang(price / (double) value) + (peluang(price / (double) value) > 0 ? nature : 0)));
			if (gross + price > cap || random.nextInt(100) >= chance) {
				continue;
			}
			gross += price;
			sold++;
			slots.remove(key);
		}
		if (sold <= 0) {
			return;
		}
		long tax = Math.max(1L, Math.round(gross * PAJAK));
		long net = gross - tax;
		lapak.putLong("kas", lapak.getLongOr("kas", 0L) + net);
		lapak.putInt("total_terjual", lapak.getIntOr("total_terjual", 0) + sold);
		KasKota.tambah(server, lapak.getLongOr("situs", 0L), tax);
		BukuKas.catat(server, owner, LembarKunci.BukuKas.SUMBER_LAPAK, 0, net, "kultivator.tempat_ekonomi.kas.catatan.lapak", sold, tax);
		ServerPlayer online = server.getPlayerList().getPlayer(owner);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.tempat_ekonomi.lapak.laku", sold, NilaiBatu.text(net)).withStyle(ChatFormatting.GOLD));
			if (lapak.getIntOr("total_terjual", 0) >= 20) {
				Advancements.grant(online, "tempat/lapak_laris");
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The guard body at the kiosk (forwarded from the kotabesar/lapak_sekte anchor)

	/**
	 * {@code kotabesar/lapak_sekte} anchor near players (38KS forwards its {@code nearby}): the renter's guard stands at kiosk
	 * {@code data.nomor} and calls out to buyers. The body opens the owner's ledger (stall tab) for its owner.
	 */
	public static void jangkarDekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!EkonomiConfig.get().lapak) {
			return;
		}
		MinecraftServer server = level.getServer();
		int nomor = anchor.data().getIntOr("nomor", 0);
		long situs = anchor.site();
		UUID renter = null;
		CompoundTag lapak = null;
		EkonomiData data = EkonomiData.get(server);
		for (UUID id : data.pemainIds()) {
			CompoundTag other = data.pemain(id).getCompoundOrEmpty(KUNCI);
			if (aktif(server, other) && other.getIntOr("nomor", -1) == nomor && (situs == 0L || other.getLongOr("situs", 0L) == situs)) {
				renter = id;
				lapak = other;
				break;
			}
		}
		KultivatorNpc body = PENJAGA.get(anchor.getId());
		if (renter == null) {
			if (body != null) {
				body.vanish();
				PENJAGA.remove(anchor.getId());
			}
			return;
		}
		if (body == null || body.isRemoved()) {
			body = HidupApi.tubuh(level, situs, NpcRole.PEDAGANG_KELILING, anchor.blockPosition(), anchor.getYRot());
			if (body == null) {
				return;
			}
			Sosok sosok = Sosok.unpack(lapak.getIntOr("penjaga_sosok", Sosok.KOSONG));
			if (sosok != null) {
				SosokApi.setSosok(body, sosok);
			}
			body.memory().putString("lapak_pemilik", renter.toString());
			body.memory().putBoolean("hold", true);
			body.setCustomName(Component.translatable("kultivator.tempat_ekonomi.lapak.nama_penjaga", lapak.getStringOr("penjaga_nama", "")));
			PENJAGA.put(anchor.getId(), body);
		}
		if (level.getRandom().nextInt(4) == 0) {
			boolean call = level.getRandom().nextBoolean();
			SosokApi.gerak(body, call ? GerakId.SERU_DAGANG : GerakId.HITUNG_SEMPOA, 0, 60, call ? PropId.TIDAK_ADA : PropId.SEMPOA, PropId.TIDAK_ADA,
					PropId.TIDAK_ADA);
		}
	}

	/** The kiosk anchor unloaded: its guard body leaves. */
	public static void jangkarLepas(DuniaAnchorEntity anchor) {
		KultivatorNpc body = PENJAGA.remove(anchor.getId());
		if (body != null) {
			body.vanish();
		}
	}

	/** The stall guard's owner (the ledger opens for him only), or null. */
	static @Nullable UUID pemilikPenjaga(KultivatorNpc npc) {
		String text = npc.memory().getStringOr("lapak_pemilik", "");
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private LapakSekte() {
	}
}

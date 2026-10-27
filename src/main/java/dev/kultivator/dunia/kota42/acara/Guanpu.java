package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.LembarKotaDasar;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dice house (DESIGN 42.9.6, 42.11.10; sheet contract {@link KotaLembar.Guanpu}): a click on a {@link MejaDaduEntity} opens
 * the {@code guanpu} sheet. Three dice in a bamboo cup, two games, every odd written honestly on the sheet:
 * <ul>
 * <li><b>Besar-Kecil</b>: <i>besar</i> (sum 11-17) or <i>kecil</i> (4-10) pays 0.85 : 1 and loses on any triple, 105 of 216
 * throws win, return 0.90 of the stake; a named triple (<i>kembar</i> 1-6) pays 150 : 1 at 1/216, return 0.70. A stake is
 * paid when it is placed ({@code taruh}); {@code lempar} throws the dice for every stake on the table at once. Stakes left on
 * the table when the sheet closes are refunded.</li>
 * <li><b>Guanpu</b> (throw for goods): every throw costs 1/8 of the prize's value and wins it on a sum of 15 or more (20 of
 * 216 = 9.3 %, return 0.74); each prize of the merchant's table can be won twice a day.</li>
 * </ul>
 * 20 throws a player a day on both games together; stakes from 1 NB up to {@code 10 x (band + 1)} NB. Everything is a sink
 * (the house always wins on average) and the same die rolls are shown to every client by the table entity ({@code kocok}).
 * The sheet builder and op handler are registered here as a fallback when no other module has registered the screen
 * ({@code TempatLayar.terdaftar}); {@link #bangun} and {@link #aksi} are public for a builder class of 42U to call.
 */
public final class Guanpu {

	public static final int BATAS_HARI = 20;
	public static final long STAKE_MIN = 1L;
	/** A guanpu throw wins on this sum or more (20 of 216). */
	public static final int MENANG_JIKA = 15;
	/** Prize stock per table and day. */
	public static final int STOK_HADIAH = 2;
	/** Payout of a besar/kecil win (profit per 1 staked) and of a named triple. */
	public static final double BAYAR_BESAR_KECIL = 0.85D;
	public static final long BAYAR_KEMBAR = 150L;
	static final String K = "kultivator.kota42.acara.guanpu.";
	private static final String LAYAR = KotaLembar.Guanpu.LAYAR;

	/** Stakes on the table: player -> bet id -> NB. */
	private static final Map<UUID, Map<String, Long>> TARUHAN = new HashMap<>();
	/** The last 10 throws of a player: {d1, d2, d3, hasil code, menang, nb (net)}. */
	private static final Map<UUID, Deque<long[]>> RIWAYAT = new HashMap<>();
	/** Players whose next sheet answers a throw (cup animation). */
	private static final Map<UUID, long[]> LEMPAR = new HashMap<>();
	/** The last result line of a player: {menang(1/0), nb, hadiah index+1 or 0, text kind} with the sentence in {@link #TEKS}. */
	private static final Map<UUID, Object[]> HASIL = new HashMap<>();
	/** Prizes won today: "situs:meja:day:i" -> count. */
	private static final Map<String, Integer> TERMENANG = new HashMap<>();
	/** The last dice of a player (to keep them on the sheet). */
	private static final Map<UUID, int[]> DADU = new HashMap<>();
	private static boolean initialised;

	/** From {@code DistrikMalam.init}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!TempatLayar.terdaftar(LAYAR)) {
				TempatLayar.daftar(LAYAR, (p, npcId, op) -> aksi(p, npcId, LembarKunci.urai(op)), Guanpu::bangun);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40L == 9L) {
				kembalikanSisa(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			kembalikan(p);
			LEMPAR.remove(p.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TARUHAN.clear();
			RIWAYAT.clear();
			LEMPAR.clear();
			HASIL.clear();
			TERMENANG.clear();
			DADU.clear();
		});
	}

	// ================================================================== opening

	/** Opens the dice sheet of {@code meja} for {@code player}. */
	static void buka(ServerPlayer player, MejaDaduEntity meja) {
		if (!KotaConfig.acara().aktif || !KotaConfig.acara().distrikMalam) {
			Msg.actionBar(player, Component.translatable(K + "tutup").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!TempatLayar.bukaDari(player, LAYAR, meja.getId())) {
			Msg.actionBar(player, Component.translatable(K + "tutup").withStyle(ChatFormatting.GRAY));
			return;
		}
		// the table keeper looks up from the cup
		for (KultivatorNpc npc : meja.level().getEntitiesOfClass(KultivatorNpc.class, meja.getBoundingBox().inflate(4.0D))) {
			if (npc.town() == meja.situsId() || meja.situsId() == 0L) {
				HidupApi.tatap(npc, player);
				break;
			}
		}
	}

	// ================================================================== odds

	/** The sum of a throw. */
	static int jumlah(int[] d) {
		return d[0] + d[1] + d[2];
	}

	static boolean kembar(int[] d) {
		return d[0] == d[1] && d[1] == d[2];
	}

	/** True when {@code bet} wins on the throw {@code d}. */
	static boolean menang(String bet, int[] d) {
		int j = jumlah(d);
		if (bet.startsWith(KotaLembar.Guanpu.BT_KEMBAR + "_")) {
			return kembar(d) && d[0] == bet.charAt(bet.length() - 1) - '0';
		}
		if (kembar(d)) {
			return false;
		}
		return KotaLembar.Guanpu.BT_BESAR.equals(bet) ? j >= 11 && j <= 17 : KotaLembar.Guanpu.BT_KECIL.equals(bet) && j >= 4 && j <= 10;
	}

	/** The return of a stake of {@code nb} when {@code bet} wins (the stake back plus the profit). */
	static long kembali(String bet, long nb) {
		if (bet.startsWith(KotaLembar.Guanpu.BT_KEMBAR + "_")) {
			return nb * (BAYAR_KEMBAR + 1L);
		}
		return nb + (long) Math.floor(nb * BAYAR_BESAR_KECIL);
	}

	/** Throws of 216 that win each bet ("105/216", "1/216"). */
	static String peluang(String bet) {
		return bet.startsWith(KotaLembar.Guanpu.BT_KEMBAR + "_") ? "1/216" : "105/216";
	}

	static String bayarTeks(String bet) {
		return bet.startsWith(KotaLembar.Guanpu.BT_KEMBAR + "_") ? "150 : 1" : "0,85 : 1";
	}

	/** Three dice from a source of randomness. */
	static int[] lempar(RandomSource r) {
		return new int[] {1 + r.nextInt(6), 1 + r.nextInt(6), 1 + r.nextInt(6)};
	}

	/** The expected return of 1 staked on a bet (for the tests and the sheet's fine print). */
	public static double nilaiHarapan(String bet) {
		int menang = 0;
		for (int a = 1; a <= 6; a++) {
			for (int b = 1; b <= 6; b++) {
				for (int c = 1; c <= 6; c++) {
					if (menang(bet, new int[] {a, b, c})) {
						menang++;
					}
				}
			}
		}
		return menang / 216.0D * (kembali(bet, 1000L) / 1000.0D);
	}

	// ================================================================== sheet

	/** The stake limit of a player: 10 x (band + 1). */
	static long maks(ServerPlayer player) {
		return 10L * (Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, 8) + 1L);
	}

	private static List<String> daftarBet() {
		List<String> out = new ArrayList<>();
		out.add(KotaLembar.Guanpu.BT_BESAR);
		out.add(KotaLembar.Guanpu.BT_KECIL);
		for (int i = 1; i <= 6; i++) {
			out.add(KotaLembar.Guanpu.BT_KEMBAR + "_" + i);
		}
		return out;
	}

	/** One prize of the table: a stack and its value in NB. */
	record Hadiah(String id, ItemStack barang, long nilai) {
		long hargaLempar() {
			return Math.max(1L, Math.round(nilai / 8.0D));
		}
	}

	/** The merchant's four prizes of a table today (fixed by place, table and day). */
	static List<Hadiah> hadiah(MinecraftServer server, long situs, int meja) {
		long hari = KalenderKota.hari(server);
		RandomSource r = RandomSource.create(situs * 31L + meja * 7919L + hari * 104729L);
		List<ItemStack> kolam = new ArrayList<>();
		for (String id : new String[] {"mutiara_laut", "giok_barat", "sutra_awan", "lingzhi_gunung", "permadani_gurun", "porselen_hijau", "arak_beras",
				"teh_bi_luo"}) {
			Item item = DagangItems.item(id);
			if (item != null) {
				kolam.add(new ItemStack(item, id.equals("arak_beras") || id.equals("teh_bi_luo") ? 4 : 1));
			}
		}
		kolam.add(new ItemStack(Items.GOLD_INGOT, 2));
		kolam.add(new ItemStack(Items.EMERALD, 3));
		kolam.add(new ItemStack(Items.AMETHYST_SHARD, 6));
		kolam.add(new ItemStack(Items.HONEY_BOTTLE, 2));
		kolam.add(new ItemStack(Items.NAUTILUS_SHELL, 1));
		List<Hadiah> out = new ArrayList<>();
		while (out.size() < 4 && !kolam.isEmpty()) {
			ItemStack s = kolam.remove(r.nextInt(kolam.size()));
			long nilai = EkonomiApi.harga(s);
			if (nilai >= 8L) {
				out.add(new Hadiah(Integer.toString(out.size()), s, nilai));
			}
		}
		return out;
	}

	private static int nomorMeja(net.minecraft.world.entity.Entity e) {
		return e == null ? 0 : (int) Math.floorMod(e.blockPosition().asLong(), 1000L);
	}

	/** Throws a player has left today. */
	static int sisa(ServerPlayer player) {
		return Math.max(0, BATAS_HARI - KalenderKota.Berkah.hariHitung(player, "guanpu"));
	}

	/** Builds the whole sheet for the table {@code npcId} (the table entity id). */
	public static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		MinecraftServer server = player.level().getServer();
		net.minecraft.world.entity.Entity e = player.level().getEntity(npcId);
		if (!(e instanceof MejaDaduEntity meja)) {
			return null;
		}
		long situs = meja.situsId();
		int nomor = nomorMeja(meja);
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(K + "judul"));
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_KAIN);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		if (situs != 0L) {
			tag.putLong(LembarKunci.SITUS, situs);
			LembarKunci.teks(tag, LembarKunci.TEMPAT, PajakKota.nama(server, situs));
		}
		UUID id = player.getUUID();
		CompoundTag m = new CompoundTag();
		LembarKunci.teks(m, LembarKunci.TEMPAT, situs == 0L ? Component.translatable(K + "meja." + meja.varian()) : PajakKota.nama(server, situs));
		m.putInt(KotaLembar.Guanpu.SISA, sisa(player));
		m.putInt(KotaLembar.Guanpu.BATAS_HARI, BATAS_HARI);
		m.putLong(KotaLembar.Guanpu.MIN, STAKE_MIN);
		m.putLong(LembarKunci.MAKS, maks(player));
		tag.put(KotaLembar.Guanpu.MEJA, m);
		int[] dadu = DADU.get(id);
		if (dadu != null) {
			tag.putIntArray(KotaLembar.Guanpu.DADU, dadu);
			tag.putInt(LembarKunci.JUMLAH, jumlah(dadu));
		}
		long[] l = LEMPAR.remove(id);
		if (l != null) {
			tag.putBoolean(KotaLembar.Guanpu.LEMPAR, true);
		}
		Map<String, Long> pasang = TARUHAN.getOrDefault(id, Map.of());
		ListTag bets = new ListTag();
		for (String bet : daftarBet()) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, bet);
			Component nama = bet.startsWith(KotaLembar.Guanpu.BT_KEMBAR + "_")
					? Component.translatable(K + "bet.kembar", bet.substring(bet.length() - 1)) : Component.translatable(K + "bet." + bet);
			LembarKunci.teks(t, LembarKunci.NAMA, nama);
			LembarKunci.teks(t, KotaLembar.Guanpu.BAYAR, Component.literal(bayarTeks(bet)));
			LembarKunci.teks(t, LembarKunci.PELUANG, Component.literal(peluang(bet)));
			boolean dipasang = pasang.containsKey(bet);
			t.putBoolean("pilih", dipasang);
			t.putLong("nb", pasang.getOrDefault(bet, 0L));
			bets.add(t);
		}
		tag.put(KotaLembar.Guanpu.TARUHAN, bets);
		ListTag riwayat = new ListTag();
		Deque<long[]> hist = RIWAYAT.get(id);
		if (hist != null) {
			for (long[] h : hist) {
				CompoundTag t = new CompoundTag();
				t.putIntArray(KotaLembar.Guanpu.DADU, new int[] {(int) h[0], (int) h[1], (int) h[2]});
				t.putInt(LembarKunci.JUMLAH, (int) (h[0] + h[1] + h[2]));
				t.putString(KotaLembar.Guanpu.HASIL, h[3] == 2L ? KotaLembar.Guanpu.BT_KEMBAR : h[3] == 1L ? KotaLembar.Guanpu.BT_BESAR : KotaLembar.Guanpu.BT_KECIL);
				if (h[4] >= 0L) {
					t.putBoolean(KotaLembar.Guanpu.MENANG, h[4] == 1L);
					t.putLong("nb", Math.abs(h[5]));
				}
				riwayat.add(t);
			}
		}
		tag.put(LembarKunci.RIWAYAT, riwayat);
		ListTag guan = new ListTag();
		for (Hadiah h : hadiah(server, situs, nomor)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, h.id());
			LembarKunci.teks(t, LembarKunci.NAMA, h.barang().getHoverName());
			LembarKunci.barang(t, h.barang(), server.registryAccess());
			t.putLong(KotaLembar.Guanpu.HARGA_LEMPAR, h.hargaLempar());
			t.putInt(KotaLembar.Guanpu.MENANG_JIKA, MENANG_JIKA);
			LembarKunci.teks(t, LembarKunci.PELUANG, Component.literal("9,3 %"));
			t.putInt(KotaLembar.Guanpu.SISA, Math.max(0, STOK_HADIAH - TERMENANG.getOrDefault(kunciHadiah(server, situs, nomor, h.id()), 0)));
			guan.add(t);
		}
		tag.put(KotaLembar.Guanpu.GUANPU, guan);
		Object[] hasil = HASIL.get(id);
		if (hasil != null) {
			CompoundTag t = new CompoundTag();
			t.putBoolean(KotaLembar.Guanpu.MENANG, (Boolean) hasil[0]);
			t.putLong("nb", (Long) hasil[1]);
			LembarKunci.teks(t, LembarKunci.TEKS, (Component) hasil[2]);
			if (hasil[3] instanceof ItemStack s && !s.isEmpty()) {
				LembarKunci.barang(t, s, server.registryAccess());
			}
			tag.put(KotaLembar.Guanpu.HASIL, t);
		}
		return tag;
	}

	private static String kunciHadiah(MinecraftServer server, long situs, int meja, String id) {
		return situs + ":" + meja + ":" + KalenderKota.hari(server) + ":" + id;
	}

	// ================================================================== actions

	/** The ops of the sheet: {@code taruh:BT:NB}, {@code lempar}, {@code lempar_guanpu:ID}; {@code op[0]} the verb. */
	public static void aksi(ServerPlayer player, int npcId, String[] op) {
		String verb = op.length == 0 ? "" : op[0];
		if (!(player.level().getEntity(npcId) instanceof MejaDaduEntity meja)) {
			return;
		}
		switch (verb) {
			case KotaLembar.Guanpu.OP_TARUH -> taruh(player, LembarKunci.arg(op, 1), LembarKunci.argLong(op, 2, 0L));
			case KotaLembar.Guanpu.OP_LEMPAR -> lemparMeja(player, meja);
			case KotaLembar.Guanpu.OP_LEMPAR_GUANPU -> lemparHadiah(player, meja, LembarKunci.arg(op, 1));
			default -> {
			}
		}
	}

	private static void pesan(ServerPlayer p, String kunci, String jenis, Object... isian) {
		LembarKotaDasar.pesan(p, Component.translatable(K + kunci, isian), jenis);
	}

	private static void taruh(ServerPlayer p, String bet, long nb) {
		if (!daftarBet().contains(bet)) {
			return;
		}
		if (sisa(p) <= 0) {
			pesan(p, "habis", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		long maks = maks(p);
		Map<String, Long> pasang = TARUHAN.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
		long ada = pasang.getOrDefault(bet, 0L);
		long tambah = Mth.clamp(nb, STAKE_MIN, maks);
		if (ada + tambah > maks) {
			tambah = maks - ada;
		}
		if (tambah <= 0L) {
			pesan(p, "batas", LembarKunci.PESAN_PERINGATAN, maks);
			return;
		}
		if (!EkonomiApi.bayar(p, tambah, "guanpu_taruh")) {
			pesan(p, "kurang", LembarKunci.PESAN_BAHAYA, tambah);
			return;
		}
		pasang.put(bet, ada + tambah);
		pesan(p, "taruh_ok", LembarKunci.PESAN_OK, tambah);
	}

	private static void lemparMeja(ServerPlayer p, MejaDaduEntity meja) {
		if (sisa(p) <= 0) {
			pesan(p, "habis", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		if (meja.keadaan() == MejaDaduEntity.KEADAAN_KOCOK) {
			pesan(p, "tunggu", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		KalenderKota.Berkah.tambahHari(p, "guanpu", 1);
		int[] d = lempar(p.getRandom());
		meja.kocok(d[0], d[1], d[2]);
		DADU.put(p.getUUID(), d);
		LEMPAR.put(p.getUUID(), new long[] {p.level().getGameTime()});
		Map<String, Long> pasang = TARUHAN.remove(p.getUUID());
		long untung = 0L;
		long total = 0L;
		long kembaliNb = 0L;
		boolean adaMenang = false;
		if (pasang != null) {
			for (Map.Entry<String, Long> e : pasang.entrySet()) {
				total += e.getValue();
				if (menang(e.getKey(), d)) {
					adaMenang = true;
					kembaliNb += kembali(e.getKey(), e.getValue());
				}
			}
			untung = kembaliNb - total;
			if (kembaliNb > 0L) {
				EkonomiApi.beri(p, kembaliNb, "guanpu_menang");
			}
		}
		int code = kembar(d) ? 2 : jumlah(d) >= 11 ? 1 : 0;
		Deque<long[]> hist = RIWAYAT.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		hist.addFirst(new long[] {d[0], d[1], d[2], code, pasang == null || pasang.isEmpty() ? -1L : untung > 0L || adaMenang && untung >= 0L ? 1L : 0L, untung});
		while (hist.size() > 10) {
			hist.removeLast();
		}
		Component teks = pasang == null || pasang.isEmpty()
				? Component.translatable(K + "hasil.kosong", jumlah(d))
				: adaMenang ? Component.translatable(K + "hasil.menang", jumlah(d), kembaliNb) : Component.translatable(K + "hasil.kalah", jumlah(d), total);
		if (kembar(d) && pasang != null && !pasang.isEmpty()) {
			teks = Component.translatable(K + "hasil.kembar", d[0], jumlah(d));
		}
		HASIL.put(p.getUUID(), new Object[] {pasang != null && !pasang.isEmpty() && untung >= 0L && adaMenang, Math.abs(untung), teks, ItemStack.EMPTY});
		p.level().playSound(null, meja.blockPosition(), net.minecraft.sounds.SoundEvents.BAMBOO_WOOD_HIT, net.minecraft.sounds.SoundSource.AMBIENT, 1.2F, 1.4F);
		if (kembar(d)) {
			CeritaApi.peristiwa(p, "judi_kembar", Integer.toString(d[0]));
		}
	}

	private static void lemparHadiah(ServerPlayer p, MejaDaduEntity meja, String id) {
		MinecraftServer server = p.level().getServer();
		if (sisa(p) <= 0) {
			pesan(p, "habis", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		if (meja.keadaan() == MejaDaduEntity.KEADAAN_KOCOK) {
			pesan(p, "tunggu", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		Hadiah hadiah = null;
		long situs = meja.situsId();
		int nomor = nomorMeja(meja);
		for (Hadiah h : hadiah(server, situs, nomor)) {
			if (h.id().equals(id)) {
				hadiah = h;
			}
		}
		if (hadiah == null) {
			return;
		}
		String kunci = kunciHadiah(server, situs, nomor, hadiah.id());
		if (TERMENANG.getOrDefault(kunci, 0) >= STOK_HADIAH) {
			pesan(p, "hadiah_habis", LembarKunci.PESAN_PERINGATAN);
			return;
		}
		long harga = hadiah.hargaLempar();
		if (!EkonomiApi.bayar(p, harga, "guanpu_lempar")) {
			pesan(p, "kurang", LembarKunci.PESAN_BAHAYA, harga);
			return;
		}
		KalenderKota.Berkah.tambahHari(p, "guanpu", 1);
		int[] d = lempar(p.getRandom());
		meja.kocok(d[0], d[1], d[2]);
		DADU.put(p.getUUID(), d);
		LEMPAR.put(p.getUUID(), new long[] {p.level().getGameTime()});
		boolean menang = jumlah(d) >= MENANG_JIKA;
		Deque<long[]> hist = RIWAYAT.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		hist.addFirst(new long[] {d[0], d[1], d[2], kembar(d) ? 2 : jumlah(d) >= 11 ? 1 : 0, menang ? 1L : 0L, menang ? hadiah.nilai() - harga : -harga});
		while (hist.size() > 10) {
			hist.removeLast();
		}
		if (menang) {
			TERMENANG.merge(kunci, 1, Integer::sum);
			PeristiwaJalan.beri(p, hadiah.barang().copy());
			HASIL.put(p.getUUID(), new Object[] {true, hadiah.nilai(), Component.translatable(K + "hasil.hadiah", jumlah(d), hadiah.barang().getHoverName()), hadiah.barang().copy()});
			CeritaApi.peristiwa(p, "guanpu_menang", hadiah.id());
		} else {
			HASIL.put(p.getUUID(), new Object[] {false, harga, Component.translatable(K + "hasil.hadiah_kalah", jumlah(d), MENANG_JIKA), ItemStack.EMPTY});
		}
		p.level().playSound(null, meja.blockPosition(), net.minecraft.sounds.SoundEvents.BAMBOO_WOOD_HIT, net.minecraft.sounds.SoundSource.AMBIENT, 1.2F, 1.4F);
	}

	// ================================================================== stakes left on the table

	private static void kembalikan(ServerPlayer p) {
		Map<String, Long> pasang = TARUHAN.remove(p.getUUID());
		if (pasang == null) {
			return;
		}
		long total = 0L;
		for (long v : pasang.values()) {
			total += v;
		}
		if (total > 0L) {
			EkonomiApi.beri(p, total, "guanpu_kembali");
			Msg.actionBar(p, Component.translatable(K + "kembali", total).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Every two seconds: a player with stakes who no longer has the sheet open gets them back. */
	private static void kembalikanSisa(MinecraftServer server) {
		if (TARUHAN.isEmpty()) {
			return;
		}
		Iterator<UUID> it = List.copyOf(TARUHAN.keySet()).iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				TARUHAN.remove(id);
			} else if (!TempatLayar.sedangMelihat(p, LAYAR)) {
				kembalikan(p);
			}
		}
	}

	// ================================================================== test commands (uji kota_hidup acara guanpu ...)

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("guanpu")
				.executes(ctx -> {
					StringBuilder sb = new StringBuilder();
					for (String bet : new String[] {KotaLembar.Guanpu.BT_BESAR, KotaLembar.Guanpu.BT_KECIL, KotaLembar.Guanpu.BT_KEMBAR + "_3"}) {
						sb.append(String.format(java.util.Locale.ROOT, "%s=%.4f ", bet, nilaiHarapan(bet)));
					}
					int menangHadiah = 0;
					for (int a = 1; a <= 6; a++) {
						for (int b = 1; b <= 6; b++) {
							for (int c = 1; c <= 6; c++) {
								if (a + b + c >= MENANG_JIKA) {
									menangHadiah++;
								}
							}
						}
					}
					String teks = sb.toString().trim();
					int w = menangHadiah;
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.guanpu", teks, w + "/216",
							String.format(java.util.Locale.ROOT, "%.4f", w / 216.0D * 8.0D)), false);
					return 1;
				})
				.then(Commands.literal("lempar").then(Commands.argument("n", IntegerArgumentType.integer(1, 100000)).executes(ctx -> {
					int n = IntegerArgumentType.getInteger(ctx, "n");
					RandomSource r = RandomSource.create(n);
					long besar = 0L;
					long kecil = 0L;
					long kembar = 0L;
					for (int i = 0; i < n; i++) {
						int[] d = lempar(r);
						besar += menang(KotaLembar.Guanpu.BT_BESAR, d) ? 1 : 0;
						kecil += menang(KotaLembar.Guanpu.BT_KECIL, d) ? 1 : 0;
						kembar += kembar(d) ? 1 : 0;
					}
					long b = besar;
					long k = kecil;
					long t = kembar;
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.guanpu_lempar", n, b, k, t), false);
					return 1;
				})))
				.then(Commands.literal("sisa").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					int s = sisa(p);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.guanpu_sisa", s, BATAS_HARI), false);
					return s;
				}));
	}

	private Guanpu() {
	}
}

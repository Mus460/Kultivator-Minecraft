package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Repeating trade contracts (DESIGN 42.6.5) taken at a {@link RumahDagang} and filled from the player's warehouses
 * ({@link GudangKota}), plus the Nama Dagang and the city orders.
 * <p>
 * <b>Running</b>: on each run day the caravan leaves by itself when the source warehouse holds the load (it is taken out
 * and travels as a {@link GudangKota} caravan); on arrival the buyer pays units x price less the 3 % caravan fee into the
 * drawer. The premium over the goods' base value counts against the one weekly trade-profit cap of 38.T
 * ({@code Dagang.batas}: {@code batasLabaMingguan x (b+1)}, shared with the market trade; beyond it only the base value
 * is paid). A run without the load on its day, or a load short after bandits, fails: a fine of 15 % of the run's value
 * from the drawer and Nama Dagang -30; an on-time full delivery +10. <b>Nama Dagang</b> 0-1000 gives tiers I-V (every
 * 200); active contracts at most {@code 2 + tier}.
 * <p>
 * <b>City orders</b> (Pesanan Kota): the town asks for materials for its plots and rebuilding (42F registers the needs
 * with {@link #daftarPesanan}); a delivery is paid {@code HargaKota x 1.1} from the Kas Kota (never more than it holds),
 * Nama Baik +1 per 32 items and counts as a town task.
 * <p>
 * State in {@code KotaEkonomiData.bagian("kontrak")["pemain"][uuid]}: {@code nd} Nama Dagang, {@code ok} deliveries made,
 * {@code km}/{@code kp} premium booked this week, {@code n} next id, {@code L} contracts ({@code i} id, {@code b} item,
 * {@code j} units, {@code a}/{@code t} source/destination, {@code ti} days, {@code k} runs done, {@code kk} runs, {@code h}
 * price x100, {@code dn} fine %, {@code tg} tier, {@code nx} next run day, {@code kv} caravan on the road, {@code st} 0
 * active / 1 done / 2 cancelled, {@code ws} week the offer came from, {@code wo} offer id).
 */
public final class KontrakDagang {

	public static final int NAMA_MAKS = 1000;
	public static final int NAMA_TEPAT = 10;
	public static final int NAMA_GAGAL = -30;
	public static final int NAMA_BATAL = -20;
	public static final double ONGKOS = GudangKota.ONGKOS;
	/** Deliveries for the advancement "saudagar kontrak". */
	public static final int SAUDAGAR = 4;

	/** A city order of 42F: id, item, units needed, units delivered so far. */
	public record Pesanan(String id, Item item, int perlu, int terkumpul) {
	}

	/** 42F: the city orders of a place and the hand-in of {@code n} units of order {@code id}. */
	public interface PenyediaPesanan {
		List<Pesanan> pesanan(MinecraftServer server, long situs);

		void terima(MinecraftServer server, long situs, String id, int n);
	}

	private static final List<PenyediaPesanan> PESANAN = new CopyOnWriteArrayList<>();

	static void init() {
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().kontrak;
	}

	/** 42F registers the city orders (plot material, rebuilding). */
	public static void daftarPesanan(PenyediaPesanan penyedia) {
		if (penyedia != null) {
			PESANAN.add(penyedia);
		}
	}

	// ================================================================== ledger

	private static CompoundTag buku(MinecraftServer server, UUID player) {
		CompoundTag all = KotaEkonomiData.get(server).bagian(KotaEkonomiData.KONTRAK).getCompoundOrEmpty("pemain");
		CompoundTag mine = all.getCompoundOrEmpty(player.toString());
		all.put(player.toString(), mine);
		KotaEkonomiData.get(server).bagian(KotaEkonomiData.KONTRAK).put("pemain", all);
		return mine;
	}

	private static ListTag daftar(MinecraftServer server, UUID player) {
		CompoundTag b = buku(server, player);
		ListTag list = b.getListOrEmpty("L");
		b.put("L", list);
		return list;
	}

	private static @Nullable CompoundTag mentah(MinecraftServer server, UUID player, int id) {
		for (Tag tag : daftar(server, player)) {
			if (tag instanceof CompoundTag c && c.getIntOr("i", -1) == id) {
				return c;
			}
		}
		return null;
	}

	/** The player's Nama Dagang 0-1000. */
	public static int namaDagang(MinecraftServer server, UUID player) {
		return Mth.clamp(buku(server, player).getIntOr("nd", 0), 0, NAMA_MAKS);
	}

	/** Trade tier 1-5 (I-V) from the Nama Dagang. */
	public static int tingkat(MinecraftServer server, UUID player) {
		return 1 + Math.min(4, namaDagang(server, player) / 200);
	}

	/** Most contracts the player may run at once. */
	public static int kontrakMaks(MinecraftServer server, UUID player) {
		return 2 + tingkat(server, player);
	}

	private static void ubahNama(MinecraftServer server, UUID player, int delta) {
		CompoundTag b = buku(server, player);
		b.putInt("nd", Mth.clamp(b.getIntOr("nd", 0) + delta, 0, NAMA_MAKS));
		KotaEkonomiData.get(server).ubah();
	}

	/** One contract for the screens. */
	public record Kontrak(int id, String barang, int jumlah, long asal, long tujuan, int tiap, int kali, int kaliMaks, double harga, int denda,
			int tingkat, long berikut, boolean jalan, int keadaan) {

		public @Nullable Item item() {
			Identifier id = Identifier.tryParse(barang);
			return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		}
	}

	private static Kontrak dari(CompoundTag c) {
		return new Kontrak(c.getIntOr("i", 0), c.getStringOr("b", ""), c.getIntOr("j", 0), c.getLongOr("a", 0L), c.getLongOr("t", 0L), c.getIntOr("ti", 7),
				c.getIntOr("k", 0), c.getIntOr("kk", 1), c.getLongOr("h", 0L) / 100.0D, c.getIntOr("dn", RumahDagang.DENDA_PERSEN), c.getIntOr("tg", 1),
				c.getLongOr("nx", 0L), c.getLongOr("kv", 0L) != 0L, c.getIntOr("st", 0));
	}

	/** Every contract of the player (running first). */
	public static List<Kontrak> kontrak(MinecraftServer server, UUID player) {
		List<Kontrak> out = new ArrayList<>();
		for (Tag tag : daftar(server, player)) {
			if (tag instanceof CompoundTag c) {
				out.add(dari(c));
			}
		}
		out.sort((x, y) -> Integer.compare(x.keadaan(), y.keadaan()));
		return out;
	}

	private static int aktifJumlah(MinecraftServer server, UUID player) {
		int n = 0;
		for (Kontrak k : kontrak(server, player)) {
			if (k.keadaan() == 0) {
				n++;
			}
		}
		return n;
	}

	/** Units of the contract's goods in its source warehouse. */
	public static int stok(MinecraftServer server, UUID player, Kontrak k) {
		Item item = k.item();
		return item == null ? 0 : GudangKota.hitung(server, k.asal(), player, s -> s.is(item));
	}

	// ================================================================== taking

	/** Why the player cannot take offer {@code id} of the board at {@code situs}, or null. */
	public static @Nullable Component alasan(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.kontrak.mati");
		}
		RumahDagang.Tawaran t = RumahDagang.tawaran(server, situs, id);
		if (t == null) {
			return Component.translatable("kultivator.kota42.kontrak.tiada");
		}
		if (t.tingkat() > tingkat(server, player.getUUID())) {
			return Component.translatable("kultivator.kota42.kontrak.tingkat_kurang", Component.translatable("kultivator.kota42.kontrak.tingkat." + t.tingkat()));
		}
		if (aktifJumlah(server, player.getUUID()) >= kontrakMaks(server, player.getUUID())) {
			return Component.translatable("kultivator.kota42.kontrak.penuh", kontrakMaks(server, player.getUUID()));
		}
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		for (Tag tag : daftar(server, player.getUUID())) {
			if (tag instanceof CompoundTag c && c.getLongOr("ws", -1L) == week && c.getIntOr("wo", -1) == id && c.getLongOr("t", 0L) == situs) {
				return Component.translatable("kultivator.kota42.kontrak.sudah_ambil");
			}
		}
		if (GudangKota.gudang(server, t.asal(), player.getUUID()) == null) {
			return Component.translatable("kultivator.kota42.kontrak.perlu_gudang", PajakKota.nama(server, t.asal()));
		}
		return null;
	}

	/** Takes offer {@code id} of the board at {@code situs}; the first run is due in 7 days. Null on success or the refusal. */
	public static @Nullable Component ambil(ServerPlayer player, long situs, int id) {
		Component why = alasan(player, situs, id);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		RumahDagang.Tawaran t = RumahDagang.tawaran(server, situs, id);
		CompoundTag b = buku(server, player.getUUID());
		int cid = b.getIntOr("n", 1);
		b.putInt("n", cid + 1);
		CompoundTag c = new CompoundTag();
		c.putInt("i", cid);
		c.putString("b", t.barang());
		c.putInt("j", t.jumlah());
		c.putLong("a", t.asal());
		c.putLong("t", t.tujuan());
		c.putInt("ti", t.tiap());
		c.putInt("kk", t.kali());
		c.putLong("h", Math.round(t.harga() * 100.0D));
		c.putInt("dn", t.denda());
		c.putInt("tg", t.tingkat());
		c.putLong("nx", KotaHidup.hari(server) + t.tiap());
		c.putLong("ws", Math.floorDiv(KotaHidup.hari(server), 7L));
		c.putInt("wo", id);
		daftar(server, player.getUUID()).add(c);
		KotaEkonomiData.get(server).ubah();
		ItemStack letter = new ItemStack(KotaEkonomiItems.SURAT_KONTRAK);
		letter.set(KotaEkonomiItems.KONTRAK, new KotaEkonomiItems.Kontrak(cid, t.nama(server), player.getUUID()));
		Barang.beri(player, letter);
		Msg.chat(player, Component.translatable("kultivator.kota42.kontrak.ambil_ok", t.nama(server), c.getLongOr("nx", 0L)).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Cancels a running contract: Nama Dagang -20 (the run on the road still arrives and is paid). */
	public static @Nullable Component batal(ServerPlayer player, int id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, player.getUUID(), id);
		if (c == null || c.getIntOr("st", 0) != 0) {
			return Component.translatable("kultivator.kota42.kontrak.tiada");
		}
		c.putInt("st", 2);
		ubahNama(server, player.getUUID(), NAMA_BATAL);
		Msg.chat(player, Component.translatable("kultivator.kota42.kontrak.batal_ok", NAMA_BATAL).withStyle(ChatFormatting.YELLOW));
		return null;
	}

	// ================================================================== the road

	/** Daily: due runs leave (or fail), finished contracts are tidied. */
	static void harian(MinecraftServer server, long hari) {
		if (!aktif()) {
			return;
		}
		CompoundTag all = KotaEkonomiData.get(server).bagian(KotaEkonomiData.KONTRAK).getCompoundOrEmpty("pemain");
		for (String key : all.keySet()) {
			UUID player = Investasi.uuid(key);
			if (player == null) {
				continue;
			}
			ListTag list = all.getCompoundOrEmpty(key).getListOrEmpty("L");
			for (int i = list.size() - 1; i >= 0; i--) {
				CompoundTag c = list.getCompoundOrEmpty(i);
				int state = c.getIntOr("st", 0);
				if (state != 0) {
					if (c.getLongOr("kv", 0L) == 0L && hari - c.getLongOr("nx", hari) > 14L) {
						list.remove(i);
					}
					continue;
				}
				if (hari < c.getLongOr("nx", 0L) || c.getLongOr("kv", 0L) != 0L) {
					continue;
				}
				jalankan(server, player, c, hari);
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	private static void jalankan(MinecraftServer server, UUID player, CompoundTag c, long hari) {
		Kontrak k = dari(c);
		Item item = k.item();
		c.putLong("nx", hari + k.tiap());
		c.putInt("k", k.kali() + 1);
		if (k.kali() + 1 >= k.kaliMaks()) {
			c.putInt("st", 1);
		}
		if (item == null || stok(server, player, k) < k.jumlah()) {
			gagal(server, player, k, Component.translatable("kultivator.kota42.kontrak.gagal_stok", k.jumlah(), PajakKota.nama(server, k.asal())));
			return;
		}
		ListTag cargo = new ListTag();
		int left = GudangKota.keluarkan(server, k.asal(), player, s -> s.is(item), k.jumlah());
		while (left > 0) {
			int n = Math.min(left, item.getDefaultMaxStackSize());
			cargo.add(Barang.simpan(new ItemStack(item, n), server.registryAccess()));
			left -= n;
		}
		long fee = Math.max(1L, Math.round(k.jumlah() * k.harga() * ONGKOS));
		long id = GudangKota.berangkat(server, player, k.asal(), k.tujuan(), cargo, k.id(), fee);
		c.putLong("kv", id);
		Investasi.kabarPemain(server, player, Component.translatable("kultivator.kota42.kontrak.berangkat", k.jumlah(), new ItemStack(item).getHoverName(),
				PajakKota.nama(server, k.tujuan()), GudangKota.hari(server, k.asal(), k.tujuan())));
	}

	private static void gagal(MinecraftServer server, UUID player, Kontrak k, Component sebab) {
		long fine = Math.round(k.jumlah() * k.harga() * k.denda() / 100.0D);
		long paid = Investasi.dariLaci(server, player, fine);
		KasKota.pajak(server, k.tujuan(), paid, "denda");
		ubahNama(server, player, NAMA_GAGAL);
		Investasi.kabarPemain(server, player, sebab.copy().append(" ").append(Component.translatable("kultivator.kota42.kontrak.denda", NilaiBatu.text(fine),
				NAMA_GAGAL)));
	}

	/** A contract caravan arrived ({@link GudangKota}): pays the delivered units into the drawer. */
	static void tiba(MinecraftServer server, UUID player, int id, ListTag cargo, boolean dibegal) {
		CompoundTag c = mentah(server, player, id);
		if (c == null) {
			return;
		}
		c.putLong("kv", 0L);
		Kontrak k = dari(c);
		Item item = k.item();
		int units = 0;
		for (Tag t : cargo) {
			ItemStack stack = Barang.muat(t, server.registryAccess());
			if (item != null && stack.is(item)) {
				units += stack.getCount();
			}
		}
		double base = RumahDagang.nilai(k.barang());
		long gross = Math.round(units * k.harga());
		long fee = Math.round(gross * ONGKOS);
		long premium = Math.max(0L, gross - Math.round(units * base));
		long room = Math.max(0L, batasMinggu(server, player) - premiMinggu(server, player));
		long paidPremium = Math.min(premium, room);
		long pay = Math.max(0L, gross - premium + paidPremium - fee);
		catatPremi(server, player, paidPremium);
		Investasi.keLaci(server, player, pay, "");
		Investasi.catatKeran(server, player, Investasi.K_KONTRAK, paidPremium);
		if (units >= k.jumlah()) {
			ubahNama(server, player, NAMA_TEPAT);
			CompoundTag b = buku(server, player);
			b.putInt("ok", b.getIntOr("ok", 0) + 1);
			ServerPlayer online = server.getPlayerList().getPlayer(player);
			if (online != null && b.getIntOr("ok", 0) >= SAUDAGAR) {
				Advancements.grant(online, "tempat/kota42_saudagar_kontrak");
			}
			if (online != null) {
				Jabatan.catatTugas(online, k.tujuan());
			}
			Investasi.kabarPemain(server, player, Component.translatable("kultivator.kota42.kontrak.tiba", units, PajakKota.nama(server, k.tujuan()),
					NilaiBatu.text(pay), NAMA_TEPAT));
		} else {
			gagal(server, player, new Kontrak(k.id(), k.barang(), k.jumlah() - units, k.asal(), k.tujuan(), k.tiap(), k.kali(), k.kaliMaks(), k.harga(),
					k.denda(), k.tingkat(), k.berikut(), false, k.keadaan()), Component.translatable(dibegal ? "kultivator.kota42.kontrak.kurang_begal"
					: "kultivator.kota42.kontrak.kurang", units, k.jumlah()));
			Investasi.kabarPemain(server, player, Component.translatable("kultivator.kota42.kontrak.tiba_sebagian", units, NilaiBatu.text(pay)));
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** The weekly cap of the trade premium (the 38.T trade cap for the player's band, less his market trade profit). */
	private static long batasMinggu(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			return Math.max(0L, Dagang.batas(online) - Math.max(0L, Dagang.labaMinggu(online)));
		}
		return (long) TemaConfig.inti().batasLabaMingguan * (Investasi.pitaTersimpan(server, player) + 1L);
	}

	private static long premiMinggu(MinecraftServer server, UUID player) {
		CompoundTag b = buku(server, player);
		return b.getLongOr("km", -1L) == Math.floorDiv(KotaHidup.hari(server), 7L) ? b.getLongOr("kp", 0L) : 0L;
	}

	private static void catatPremi(MinecraftServer server, UUID player, long nb) {
		CompoundTag b = buku(server, player);
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		if (b.getLongOr("km", -1L) != week) {
			b.putLong("km", week);
			b.putLong("kp", 0L);
		}
		b.putLong("kp", b.getLongOr("kp", 0L) + nb);
	}

	/** Weekly upkeep (nothing yet: weeks reset lazily). */
	static void mingguan(MinecraftServer server, long minggu) {
	}

	// ================================================================== city orders

	/** The city orders of a place (empty while no module registered any). */
	public static List<Pesanan> pesanan(MinecraftServer server, long situs) {
		List<Pesanan> out = new ArrayList<>();
		for (PenyediaPesanan p : PESANAN) {
			out.addAll(p.pesanan(server, situs));
		}
		return out;
	}

	/** Price per unit of a city order: {@code HargaKota x 1.1}. */
	public static long hargaPesanan(MinecraftServer server, long situs, Item item) {
		ItemStack one = new ItemStack(item);
		return Math.max(1L, Math.round(HargaKota.harga(server, situs, one) * 1.1D));
	}

	/** Hands in up to {@code n} units of city order {@code id} from the bag; null on success or the refusal. */
	public static @Nullable Component serahkan(ServerPlayer player, long situs, String id, int n) {
		MinecraftServer server = player.level().getServer();
		Pesanan order = null;
		PenyediaPesanan owner = null;
		for (PenyediaPesanan p : PESANAN) {
			for (Pesanan each : p.pesanan(server, situs)) {
				if (each.id().equals(id)) {
					order = each;
					owner = p;
				}
			}
		}
		if (order == null) {
			return Component.translatable("kultivator.kota42.pesanan.tiada");
		}
		int want = Math.min(n, order.perlu() - order.terkumpul());
		long each = hargaPesanan(server, situs, order.item());
		int afford = (int) Math.min(want, KasKota.saldo(server, situs) / Math.max(1L, each));
		Item item = order.item();
		int have = Dagang.hitung(player, item);
		int give = Math.min(afford, have);
		if (give <= 0) {
			return Component.translatable(have <= 0 ? "kultivator.kota42.pesanan.tanpa_barang" : "kultivator.kota42.pesanan.kas_kosong");
		}
		if (!KasKota.bayar(server, situs, give * each, "pesanan")) {
			return Component.translatable("kultivator.kota42.pesanan.kas_kosong");
		}
		Dagang.ambil(player, item, give);
		owner.terima(server, situs, id, give);
		dev.kultivator.dunia.tempat.ekonomi.EkonomiApi.beri(player, give * each, "kota42.pesanan");
		int rep = give / 32;
		if (rep > 0) {
			KotaData.get(server).addReputation(situs, player.getUUID(), rep);
		}
		Jabatan.catatTugas(player, situs);
		Msg.chat(player, Component.translatable("kultivator.kota42.pesanan.ok", give, new ItemStack(item).getHoverName(), NilaiBatu.text(give * each), rep)
				.withStyle(ChatFormatting.GREEN));
		return null;
	}

	// ================================================================== commands

	/** {@code /kultivasi kotaku kontrak [papan | ambil <id> | batal <id>]}. */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("kontrak").executes(ctx -> daftarCmd(ctx.getSource()))
				.then(Commands.literal("papan").executes(ctx -> papan(ctx.getSource())))
				.then(Commands.literal("ambil").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo here = PajakKota.tempat(player);
					return hasil(ctx, here == null ? Component.translatable("kultivator.kota42.cmd.tanpa_tempat")
							: ambil(player, here.situsId(), IntegerArgumentType.getInteger(ctx, "id")));
				})))
				.then(Commands.literal("batal").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> hasil(ctx, batal(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id"))))));
	}

	private static int hasil(CommandContext<CommandSourceStack> ctx, @Nullable Component why) {
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int daftarCmd(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		UUID id = player.getUUID();
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.kontrak.judul", namaDagang(server, id),
				Component.translatable("kultivator.kota42.kontrak.tingkat." + tingkat(server, id)), aktifJumlah(server, id), kontrakMaks(server, id))
				.withStyle(ChatFormatting.GOLD), false);
		for (Kontrak k : kontrak(server, id)) {
			Item item = k.item();
			int stock = stok(server, id, k);
			MutableComponent line = Component.translatable("kultivator.kota42.kontrak.baris", k.id(), k.jumlah(),
					item == null ? Component.literal(k.barang()) : new ItemStack(item).getHoverName(), PajakKota.nama(server, k.asal()),
					PajakKota.nama(server, k.tujuan()), k.kali(), k.kaliMaks(), stock, String.format(java.util.Locale.ROOT, "%.1f", k.harga()), k.berikut());
			line.withStyle(k.keadaan() != 0 ? ChatFormatting.DARK_GRAY : stock >= k.jumlah() ? ChatFormatting.WHITE : ChatFormatting.RED);
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int papan(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TempatInfo here = PajakKota.tempat(player);
		if (here == null || !RumahDagang.ada(server, here.situsId())) {
			source.sendFailure(Component.translatable("kultivator.kota42.kontrak.tanpa_rumah_dagang"));
			return 0;
		}
		List<RumahDagang.Tawaran> board = RumahDagang.tawaran(server, here.situsId());
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.kontrak.papan", PajakKota.nama(server, here.situsId())).withStyle(ChatFormatting.GOLD),
				false);
		for (RumahDagang.Tawaran t : board) {
			Component why = alasan(player, here.situsId(), t.id());
			MutableComponent line = Component.translatable("kultivator.kota42.kontrak.tawaran", t.nama(server), PajakKota.nama(server, t.asal()), t.tiap(),
					t.kali(), String.format(java.util.Locale.ROOT, "%.1f", t.harga()), t.denda());
			if (why == null) {
				line.append(Component.translatable("kultivator.kota42.kontrak.tombol_ambil").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku kontrak ambil " + t.id()))));
			} else {
				line.append(Component.literal(" (").append(why).append(")").withStyle(ChatFormatting.DARK_GRAY));
			}
			source.sendSuccess(() -> line, false);
		}
		for (Pesanan p : pesanan(server, here.situsId())) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.pesanan.baris", new ItemStack(p.item()).getHoverName(), p.terkumpul(), p.perlu(),
					hargaPesanan(server, here.situsId(), p.item())), false);
		}
		return board.size();
	}

	/** {@code uji kota_hidup ekonomi kontrak [buat | kirim | tiba]}: take the first offer here, send its run now, land every caravan now. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("kontrak").executes(ctx -> daftarCmd(ctx.getSource()))
				.then(Commands.literal("buat").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo here = PajakKota.tempat(player);
					MinecraftServer server = ctx.getSource().getServer();
					List<RumahDagang.Tawaran> board = here == null ? List.of() : RumahDagang.tawaran(server, here.situsId());
					if (board.isEmpty()) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.kontrak.tanpa_rumah_dagang"));
						return 0;
					}
					RumahDagang.Tawaran t = board.get(0);
					if (GudangKota.gudang(server, t.asal(), player.getUUID()) == null) {
						GudangKota.sewaUji(server, t.asal(), player.getUUID());
					}
					Item item = t.item();
					if (item != null) {
						for (int left = t.jumlah(); left > 0; left -= item.getDefaultMaxStackSize()) {
							GudangKota.taruh(server, t.asal(), player.getUUID(), new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())), true);
						}
					}
					return hasil(ctx, ambil(player, here.situsId(), t.id()));
				}))
				.then(Commands.literal("kirim").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					MinecraftServer server = ctx.getSource().getServer();
					for (Tag tag : daftar(server, player.getUUID())) {
						if (tag instanceof CompoundTag c && c.getIntOr("st", 0) == 0 && c.getLongOr("kv", 0L) == 0L) {
							jalankan(server, player.getUUID(), c, KotaHidup.hari(server));
						}
					}
					return daftarCmd(ctx.getSource());
				}))
				.then(Commands.literal("tiba").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					for (Tag tag : KotaEkonomiData.get(server).bagian(KotaEkonomiData.GUDANG).getListOrEmpty("k")) {
						if (tag instanceof CompoundTag c) {
							c.putLong("t0", server.overworld().getGameTime() - c.getLongOr("d", 0L));
						}
					}
					return daftarCmd(ctx.getSource());
				}));
	}

	private KontrakDagang() {
	}
}

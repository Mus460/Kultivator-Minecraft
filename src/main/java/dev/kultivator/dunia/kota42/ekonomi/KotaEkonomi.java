package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.ekonomi.LapakSekte;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Runtime init of 42C Ekonomi Kota (DESIGN 42.6.0-42.6.3) and the town purse reader of contract C42.C1, called once from
 * {@code KotaHidup.init} (the blocks, items and entity types of 42R0 are registered from {@code KotaHidup.registerContent}).
 * Starts {@link HargaKota} (bus listeners), {@link RumahKota} (claim, titik veto, Teh Rumah), {@link KunciRumah},
 * {@link LapakKota} (claim, tent clicks) and {@link TokoPemain}; runs the clocks (every tick the key channels; every 2 s
 * help bodies and stall tents; every 5 s the shops' missed hours; each world hour the town census and the price check;
 * each world day rents, taxes, seals and stall leases); adds two schedule overriders (the granary queue of a Lumbung
 * Penstabil morning, residents shopping before open player shops while a player is near) and the commands
 * {@code kotaku rumah|toko|lapak|pulang} and {@code uji kota_hidup ekonomi rumah|toko|harga|stok|lumbung|simulasi}.
 * 42C-2's parts (investments, warehouses, contracts, tax, offices, Ghost Market) start from {@code PajakKota.init()}.
 * Behaviour checks {@code kotaEkonomi.aktif} at run time.
 */
public final class KotaEkonomi {

	/** Income targets of 38.M.1 per band, NB a world day (mirror of the package-private {@code EkonomiCommands.SASARAN}). */
	static final long[][] SASARAN = {{20, 30}, {40, 70}, {100, 160}, {250, 400}, {600, 900}, {1200, 1800}, {2000, 4000}, {2000, 4000}, {2000, 4000}};

	private static boolean initialised;
	private static long hariTerakhir = Long.MIN_VALUE / 2;
	private static long jamTerakhir = Long.MIN_VALUE / 2;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		HargaKota.init();
		RumahKota.init();
		KunciRumah.init();
		LapakKota.init();
		TokoPemain.init();
		// 42C-2: investments, warehouses, contracts, tax, offices, cases, Ghost Market (their root init)
		PajakKota.init();
		ServerTickEvents.END_SERVER_TICK.register(KotaEkonomi::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			HargaKota.lupakan();
			RumahKota.lupakan();
			KunciRumah.lupakan();
			LapakKota.lupakan();
			TokoPemain.lupakan();
			hariTerakhir = Long.MIN_VALUE / 2;
			jamTerakhir = Long.MIN_VALUE / 2;
		});
		WargaApi.daftarPenimpa(KotaEkonomi::lumbung);
		WargaApi.daftarPenimpa(KotaEkonomi::belanja);
		KotaHidupCommands.daftar("rumah", KotaEkonomi::cmdRumah);
		KotaHidupCommands.daftar("toko", KotaEkonomi::cmdToko);
		KotaHidupCommands.daftar("lapak", KotaEkonomi::cmdLapak);
		KotaHidupCommands.daftar("pulang", () -> Commands.literal("pulang").executes(KotaEkonomi::pulang));
		KotaUji.daftar("ekonomi", KotaEkonomi::uji);
		KotaUji.chips("kota_hidup ekonomi stok <kategori> <n>", "kota_hidup ekonomi lumbung");
	}

	/**
	 * The town purse of a place in NB (38EK {@code KasKota}: sinks and taxes in; wages, granary purchases and plot costs
	 * out; never negative).
	 */
	public static long kasKota(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? 0L : kasKota(server, situs);
	}

	/** The same for a known server (commands, screens). */
	public static long kasKota(MinecraftServer server, long situs) {
		return KasKota.saldo(server, situs);
	}

	// ================================================================== clocks

	private static void tick(MinecraftServer server) {
		KunciRumah.tick(server);
		int t = server.getTickCount();
		if (t % 40 == 7) {
			RumahKota.tick(server);
			LapakKota.tick(server);
		}
		if (t % 100 == 13) {
			TokoPemain.tick(server);
		}
		if (t % 20 != 3 || !KotaConfig.ekonomi().aktif) {
			return;
		}
		long jam = StokKota.jamDunia(server);
		if (jam != jamTerakhir) {
			jamTerakhir = jam;
			for (TempatHuni huni : Pemanggil.semua()) {
				StokKota.sensus(server, huni);
				HargaKota.semua(server, huni.situsId);
			}
		}
		long hari = KotaHidup.hari(server);
		if (hari != hariTerakhir) {
			boolean first = hariTerakhir == Long.MIN_VALUE / 2;
			hariTerakhir = hari;
			if (!first) {
				RumahKota.hariBaru(server, hari);
				LapakKota.hariBaru(server, hari);
			}
		}
	}

	// ================================================================== schedule overriders

	/** Lumbung Penstabil: on the morning the granary opens, a third of the households queue at it with sacks. */
	private static @Nullable Kegiatan lumbung(TempatHuni huni, Warga warga, dev.kultivator.dunia.tempat.hidup.Penduduk p, long waktu, int jam, long now) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || jam < 6 || jam >= 10 || Math.floorMod(warga.id, 3) != 0 || !warga.hidup()
				|| !StokKota.lumbungBuka(server, huni.situsId)) {
			return null;
		}
		String tujuan = huni.titik(KotaTitik.LUMBUNG).isEmpty()
				? huni.titik("gudang").isEmpty() ? Kegiatan.XZ + huni.pusat().getX() + "," + huni.pusat().getZ() : "gudang"
				: KotaTitik.LUMBUNG;
		boolean dapat = jam >= 8;
		return Kegiatan.di("antre_lumbung", tujuan, KotaGerakId.ANTRE, dapat ? KotaGerakId.PROP_KARUNG_BERAS : PropId.TIDAK_ADA);
	}

	/** Embodied shoppers: while a player is near an open player shop, a few residents go and look over its racks. */
	private static @Nullable Kegiatan belanja(TempatHuni huni, Warga warga, dev.kultivator.dunia.tempat.hidup.Penduduk p, long waktu, int jam, long now) {
		if (jam < 8 || jam >= 20 || !warga.hidup()) {
			return null;
		}
		List<BlockPos> depan = TokoPemain.depanToko(huni.situsId);
		if (depan.isEmpty()) {
			return null;
		}
		// about one resident in fourteen, a different set every world hour
		long slot = (long) warga.id * 0x9E3779B1L + Math.floorDiv(waktu, 1000L) * 0x632BE5ABL;
		if (Math.floorMod(slot, 14L) != 0L) {
			return null;
		}
		BlockPos at = depan.get((int) Math.floorMod(slot >>> 8, (long) depan.size()));
		boolean bayar = Math.floorMod(now / 200L + warga.id, 3L) == 0L;
		return Kegiatan.di("belanja", Kegiatan.XZ + at.getX() + "," + at.getZ(), bayar ? KotaGerakId.BERI_KOIN : GerakId.LIHAT_BARANG,
				bayar ? PropId.KANTONG_UANG : PropId.TIDAK_ADA);
	}

	// ================================================================== kotaku rumah

	private static LiteralArgumentBuilder<CommandSourceStack> cmdRumah() {
		return Commands.literal("rumah").executes(KotaEkonomi::rumahDaftar)
				.then(Commands.literal("beli").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.beli(pl, s, id)))))
				.then(Commands.literal("sewa").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.sewa(pl, s, id, 1)))
						.then(Commands.argument("minggu", IntegerArgumentType.integer(1, RumahKota.MINGGU_MAKS))
								.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.sewa(pl, s, id, IntegerArgumentType.getInteger(ctx, "minggu")))))))
				.then(Commands.literal("perpanjang").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("minggu", IntegerArgumentType.integer(1, RumahKota.MINGGU_MAKS))
								.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.perpanjang(pl, s, id, IntegerArgumentType.getInteger(ctx, "minggu")))))))
				.then(Commands.literal("pajak").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.bayarPajak(pl, s, id)))))
				.then(Commands.literal("kunci").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.cetakUlang(pl, s, id, false)))))
				.then(Commands.literal("akta").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.cetakUlang(pl, s, id, true)))))
				.then(Commands.literal("jual").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(KotaEkonomi::rumahJualTanya)
						.then(Commands.literal("ya").executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.jual(pl, s, id))))))
				.then(Commands.literal("sitaan").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo t = tempat(ctx);
					return t != null && RumahKota.ambilSitaan(player, t.situsId()).ok() ? 1 : 0;
				}))
				.then(Commands.literal("pembantu").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("warga", IntegerArgumentType.integer(1))
								.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.pekerjakan(pl, s, id, IntegerArgumentType.getInteger(ctx, "warga")))))))
				.then(Commands.literal("berhentikan").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> rumah(ctx, (pl, s, id) -> RumahKota.berhentikan(pl, s, id)))));
	}

	@FunctionalInterface
	private interface AksiRumah {
		RumahKota.Hasil jalankan(ServerPlayer player, long situs, int id);
	}

	private static int rumah(CommandContext<CommandSourceStack> ctx, AksiRumah aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		long situs = situsRumah(player, id);
		if (situs == 0L) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		return aksi.jalankan(player, situs, id).ok() ? 1 : 0;
	}

	/** The place a house id refers to: the place the player is in, else the place of his own house with that id. */
	private static long situsRumah(ServerPlayer player, int id) {
		TempatInfo t = KotaHidupCommands.tempatPemain(player);
		if (t != null && RumahKota.info(player.level().getServer(), t.situsId(), id) != null) {
			return t.situsId();
		}
		for (RumahKota.Rumah r : RumahKota.milik(player.getUUID())) {
			if (r.id() == id) {
				return r.situs();
			}
		}
		return t == null ? 0L : t.situsId();
	}

	private static int rumahJualTanya(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		long situs = situsRumah(player, id);
		RumahKota.Info info = RumahKota.info(player.level().getServer(), situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.rumah.gagal.bukan_milik"));
			return 0;
		}
		MutableComponent ya = Component.translatable("kultivator.kota42.cmd.ya").withStyle(s -> s.withColor(ChatFormatting.RED).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku rumah jual " + id + " ya")));
		ctx.getSource().sendSuccess(() -> Component.translatable(info.hargaDibayar() > 0L ? "kultivator.kota42.rumah.jual_tanya"
				: "kultivator.kota42.rumah.lepas_tanya", RumahKota.nama(player.level().getServer(), info), NilaiBatu.text(RumahKota.nilaiJual(info)),
				RumahKota.SEGEL_HARI).append(" ").append(ya), false);
		return 1;
	}

	private static int rumahDaftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		CommandSourceStack src = ctx.getSource();
		if (!KotaConfig.ekonomi().aktif || !KotaConfig.ekonomi().rumah) {
			src.sendFailure(Component.translatable("kultivator.kota42.rumah.gagal.mati"));
			return 0;
		}
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.milik_judul", RumahKota.jumlah(server, player.getUUID()),
				KotaConfig.ekonomi().rumahMaks).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (RumahKota.Info info : RumahKota.semua(server)) {
			if (!player.getUUID().equals(info.pemilik())) {
				continue;
			}
			long sampai = RumahKota.ST_SEWA.equals(info.status()) ? info.sewaSampai() : info.segelSampai();
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.milik_baris", RumahKota.nama(server, info),
					Component.translatable("kultivator.kota42.rumah.status." + info.status()), sampai, info.tunggak(),
					info.pintu().getX(), info.pintu().getZ()).withStyle(ChatFormatting.GRAY), false);
		}
		TempatInfo t = KotaHidupCommands.tempatPemain(player);
		if (t == null) {
			return 1;
		}
		List<RumahKota.Info> dijual = RumahKota.dijual(server, t.situsId());
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.dijual_judul", RumahKota.namaTempat(server, t.situsId()), dijual.size())
				.withStyle(ChatFormatting.GOLD), false);
		if (dijual.isEmpty()) {
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.tiada").withStyle(ChatFormatting.GRAY), false);
		}
		for (RumahKota.Info info : dijual) {
			MutableComponent beli = Component.translatable("kultivator.kota42.rumah.cmd.tombol_beli").withStyle(s -> s.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent.SuggestCommand("/kultivasi kotaku rumah beli " + info.id())));
			MutableComponent sewa = Component.translatable("kultivator.kota42.rumah.cmd.tombol_sewa").withStyle(s -> s.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.SuggestCommand("/kultivasi kotaku rumah sewa " + info.id() + " 1")));
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.dijual_baris", info.id(), RumahKota.nama(server, info), info.kasur(),
					info.lebar(), info.panjang(), NilaiBatu.text(RumahKota.harga(server, info)), NilaiBatu.text(RumahKota.sewa(server, info)),
					info.pintu().getX(), info.pintu().getZ()).append(" ").append(beli).append(" ").append(sewa), false);
		}
		int sitaan = RumahKota.jumlahSitaan(server, t.situsId(), player.getUUID());
		if (sitaan > 0) {
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.rumah.cmd.sitaan", sitaan).withStyle(ChatFormatting.YELLOW), false);
		}
		return 1;
	}

	// ================================================================== kotaku toko / lapak / pulang

	private static LiteralArgumentBuilder<CommandSourceStack> cmdToko() {
		return Commands.literal("toko").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.ringkas(pl, toko)))
				.then(Commands.literal("buka").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.buka(pl, toko, true))))
				.then(Commands.literal("tutup").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.buka(pl, toko, false))))
				.then(Commands.literal("laci").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.ambilLaci(pl, toko))))
				.then(Commands.literal("nama").then(Commands.argument("teks", StringArgumentType.greedyString())
						.executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.nama(pl, toko, StringArgumentType.getString(ctx, "teks"))))))
				.then(Commands.literal("pegawai").then(Commands.argument("warga", IntegerArgumentType.integer(1))
						.executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.pekerjakan(pl, toko, IntegerArgumentType.getInteger(ctx, "warga"))))))
				.then(Commands.literal("murid").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.pekerjakanMurid(pl, toko))))
				.then(Commands.literal("berhentikan").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.berhentikan(pl, toko))))
				.then(Commands.literal("harga")
						.then(Commands.literal("pasar").executes(ctx -> rakDitatap(ctx, -1L)))
						.then(Commands.argument("nb", LongArgumentType.longArg(1L)).executes(ctx -> rakDitatap(ctx, LongArgumentType.getLong(ctx, "nb")))));
	}

	@FunctionalInterface
	private interface AksiToko {
		void jalankan(ServerPlayer player, TokoPemain.Toko toko);
	}

	/** Runs a shop action on the shop the player stands in, else his only shop. */
	private static int toko(CommandContext<CommandSourceStack> ctx, AksiToko aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TokoPemain.Toko toko = TokoPemain.di(player.level(), player.blockPosition());
		if (toko == null || !toko.pemilik().equals(player.getUUID())) {
			List<TokoPemain.Toko> mine = TokoPemain.milik(player.level().getServer(), player.getUUID());
			toko = mine.size() == 1 ? mine.get(0) : null;
		}
		if (toko == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.toko.gagal.tanpa_toko"));
			return 0;
		}
		aksi.jalankan(player, toko);
		return 1;
	}

	/** {@code toko harga <nb>|pasar}: sets the price of the shelf the player looks at. */
	private static int rakDitatap(CommandContext<CommandSourceStack> ctx, long nb) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		HitResult hit = player.pick(5.0D, 0.0F, false);
		if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK
				|| !(player.level().getBlockEntity(bh.getBlockPos()) instanceof RakDagangBlockEntity rak)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.toko.gagal.tatap_rak"));
			return 0;
		}
		int slot = RakDagangBlock.slotDari(bh.getBlockPos(), bh);
		if (nb < 0L) {
			TokoPemain.hargaPasar(player, rak, slot);
		} else {
			TokoPemain.harga(player, rak, slot, nb);
		}
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> cmdLapak() {
		return Commands.literal("lapak").executes(KotaEkonomi::lapakDaftar)
				.then(Commands.literal("sewa").then(Commands.argument("no", IntegerArgumentType.integer(0))
						.executes(ctx -> lapak(ctx, 0))
						.then(Commands.argument("minggu", IntegerArgumentType.integer(1, LapakKota.MINGGU_MAKS))
								.executes(ctx -> lapak(ctx, IntegerArgumentType.getInteger(ctx, "minggu"))))))
				.then(Commands.literal("perpanjang").then(Commands.argument("no", IntegerArgumentType.integer(0))
						.then(Commands.argument("minggu", IntegerArgumentType.integer(1, LapakKota.MINGGU_MAKS))
								.executes(ctx -> lapak(ctx, -IntegerArgumentType.getInteger(ctx, "minggu"))))))
				.then(Commands.literal("lepas").then(Commands.argument("no", IntegerArgumentType.integer(0))
						.executes(ctx -> lapak(ctx, Integer.MIN_VALUE))));
	}

	/** {@code minggu} > 0 rent (0 = one week), < 0 extend by -minggu, MIN_VALUE give up. */
	private static int lapak(CommandContext<CommandSourceStack> ctx, int minggu) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		int no = IntegerArgumentType.getInteger(ctx, "no");
		RumahKota.Hasil h;
		if (minggu == Integer.MIN_VALUE) {
			h = LapakKota.lepas(player, t.situsId(), no);
		} else if (minggu < 0) {
			h = LapakKota.perpanjang(player, t.situsId(), no, -minggu);
		} else {
			h = LapakKota.sewa(player, t.situsId(), no, Math.max(1, minggu));
		}
		return h.ok() ? 1 : 0;
	}

	private static int lapakDaftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		TempatInfo t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		List<LapakKota.Lapak> all = LapakKota.daftar(server, t.situsId());
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.lapak.cmd.judul", RumahKota.namaTempat(server, t.situsId()), all.size(),
				NilaiBatu.text(LapakKota.harga(dev.kultivator.dunia.tempat.cerita.Isian.pita(player)))).withStyle(ChatFormatting.GOLD), false);
		if (all.isEmpty()) {
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.lapak.cmd.tiada").withStyle(ChatFormatting.GRAY), false);
		}
		for (LapakKota.Lapak l : all) {
			Component state = !l.disewa() ? Component.translatable("kultivator.kota42.lapak.cmd.bebas").withStyle(s -> s.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent.SuggestCommand("/kultivasi kotaku lapak sewa " + l.no() + " 1")))
					: player.getUUID().equals(l.penyewa()) ? Component.translatable("kultivator.kota42.lapak.cmd.anda", l.sampai())
					: Component.translatable("kultivator.kota42.lapak.cmd.disewa");
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.lapak.cmd.baris", l.no(),
					Component.translatableWithFallback("kultivator.kota_besar.distrik." + l.distrik(), l.distrik()), l.pos().getX(), l.pos().getZ()).append(" ").append(state), false);
		}
		return 1;
	}

	/** {@code kotaku pulang}: the first house key the player carries (same handler as using it). */
	private static int pulang(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			KotaEkonomiItems.Rumah tanda = stack.get(KotaEkonomiItems.RUMAH);
			if (stack.is(KotaEkonomiItems.KUNCI_RUMAH) && tanda != null && player.getUUID().equals(tanda.pemilik())) {
				KunciRumah.mulai(player, stack);
				return 1;
			}
		}
		ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.kunci.gagal.tanpa_kunci"));
		return 0;
	}

	private static @Nullable TempatInfo tempat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo t = KotaHidupCommands.tempatPemain(ctx.getSource().getPlayerOrException());
		if (t == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
		}
		return t;
	}

	// ================================================================== uji kota_hidup ekonomi

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		LiteralArgumentBuilder<CommandSourceStack> harga = Commands.literal("harga").executes(ctx -> ujiHarga(ctx, null));
		LiteralArgumentBuilder<CommandSourceStack> stok = Commands.literal("stok");
		for (Kategori k : Kategori.values()) {
			harga.then(Commands.literal(k.id()).executes(ctx -> ujiHarga(ctx, k)));
			stok.then(Commands.literal(k.id()).then(Commands.argument("n", IntegerArgumentType.integer(-200, 200)).executes(ctx -> {
				TempatInfo t = tempat(ctx);
				if (t == null) {
					return 0;
				}
				StokKota.ubah(ctx.getSource().getServer(), t.situsId(), k, IntegerArgumentType.getInteger(ctx, "n"));
				return ujiHarga(ctx, k);
			})));
		}
		return Commands.literal("ekonomi")
				.then(Commands.literal("rumah")
						.then(Commands.literal("daftar").executes(KotaEkonomi::rumahDaftar))
						.then(Commands.literal("beli").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(KotaEkonomi::ujiBeli)))
						.then(Commands.literal("segel").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> ujiRumah(ctx, false))))
						.then(Commands.literal("lepas").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> ujiRumah(ctx, true)))))
				.then(Commands.literal("toko")
						.then(Commands.literal("laporan").executes(ctx -> toko(ctx, (pl, toko) -> TokoPemain.ringkas(pl, toko))))
						.then(Commands.literal("jual").then(Commands.argument("jam", IntegerArgumentType.integer(1, 24)).executes(KotaEkonomi::ujiJual))))
				.then(harga)
				.then(stok)
				.then(Commands.literal("lumbung").executes(KotaEkonomi::ujiLumbung))
				.then(Commands.literal("simulasi").then(Commands.argument("pita", IntegerArgumentType.integer(0, SASARAN.length - 1))
						.then(Commands.argument("hari", IntegerArgumentType.integer(1, 120)).executes(KotaEkonomi::ujiSimulasi))));
	}

	private static int ujiHarga(CommandContext<CommandSourceStack> ctx, @Nullable Kategori satu) throws CommandSyntaxException {
		TempatInfo t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.judul", RumahKota.namaTempat(server, t.situsId()),
				StokKota.penduduk(server, t.situsId()), kasKota(server, t.situsId())).withStyle(ChatFormatting.GOLD), false);
		for (Kategori k : Kategori.values()) {
			if (satu != null && k != satu) {
				continue;
			}
			HargaKota.Rincian r = HargaKota.rincian(server, t.situsId(), k);
			String panah = r.tren() > 0 ? "↑" : r.tren() < 0 ? "↓" : "·";
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.baris", k.nama(), String.format(Locale.ROOT, "%.2f", r.faktor()), panah,
					r.stok(), HargaKota.sebab(server, t.situsId(), k)).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int ujiBeli(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		long situs = situsRumah(player, id);
		RumahKota.Info info = RumahKota.info(player.level().getServer(), situs, id);
		if (info == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.rumah.gagal.tidak_dijual"));
			return 0;
		}
		// test path: the price is given to the player first, so the normal purchase runs end to end
		dev.kultivator.dunia.tempat.ekonomi.EkonomiApi.beri(player, RumahKota.harga(player.level().getServer(), info), "uji");
		return RumahKota.beli(player, situs, id).ok() ? 1 : 0;
	}

	private static int ujiRumah(CommandContext<CommandSourceStack> ctx, boolean lepas) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		long situs = situsRumah(player, id);
		MinecraftServer server = player.level().getServer();
		if (RumahKota.info(server, situs, id) == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.rumah.gagal.tidak_dijual"));
			return 0;
		}
		if (lepas) {
			RumahKota.lepas(server, situs, id);
		} else {
			RumahKota.segel(server, situs, id, false);
		}
		ctx.getSource().sendSuccess(() -> Component.literal((lepas ? "lepas " : "segel ") + situs + ":" + id), true);
		return 1;
	}

	private static int ujiJual(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		int jam = IntegerArgumentType.getInteger(ctx, "jam");
		CommandSourceStack src = ctx.getSource();
		TokoPemain.Toko toko = TokoPemain.di(player.level(), player.blockPosition());
		if (toko == null) {
			src.sendFailure(Component.translatable("kultivator.kota42.toko.gagal.tanpa_toko"));
			return 0;
		}
		// rewinds the shop's hour mark so the next market run catches up `jam` hours
		net.minecraft.nbt.CompoundTag d = KotaEkonomiData.get(server).situs(KotaEkonomiData.TOKO, toko.situs()).getCompoundOrEmpty("t");
		for (String key : d.keySet()) {
			net.minecraft.nbt.CompoundTag t = d.getCompoundOrEmpty(key);
			if (t.getStringOr("nm", "").equals(toko.nama())) {
				t.putLong("jam", StokKota.jamDunia(server) - jam);
			}
		}
		TokoPemain.tick(server);
		TokoPemain.ringkas(player, toko);
		return 1;
	}

	private static int ujiLumbung(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		StokKota.ubah(server, t.situsId(), Kategori.PANGAN, -200);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.lumbung", StokKota.LUMBUNG_HARI,
				String.format(Locale.ROOT, "%.2f", HargaKota.faktor(server, t.situsId(), Kategori.PANGAN))), true);
		return 1;
	}

	/**
	 * {@code ekonomi simulasi <pita> <hari>} (42.6.0): the mean daily NB a player of band {@code pita} draws from the 42
	 * faucets when he uses all of them (the shop sells at 60 % of its rumah-toko cap and keeps a 25 % margin over what the
	 * goods cost him; the weekly caps of 42.6.0 spread over the days; offices and tribute only from the bands that reach
	 * them), minus the 42C sinks (rent of a rumah toko, shop tax, two helpers). Time model: a player who works all 42
	 * faucets spends about half his day on the 38 sources, so {@code 0.5 x target middle + 42 net} must fall inside the
	 * 38.M.1 target -25 % / +25 %.
	 */
	private static int ujiSimulasi(CommandContext<CommandSourceStack> ctx) {
		int b = IntegerArgumentType.getInteger(ctx, "pita");
		int hari = IntegerArgumentType.getInteger(ctx, "hari");
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		long[] keran = {
				Math.round(cfg.tokoBatas * (b + 1) * 1.5D * 0.6D * 0.25D * hari),   // rumah toko: 60 % of its cap, 25 % margin
				Math.round(60.0D * (b + 1) / 7.0D * hari),                     // investments, total cap a week
				Math.round(20.0D * (b + 1) * 3 * 2 / 7.0D * Math.min(1.0D, b / 3.0D) * hari),   // office wages, rank 3 in 2 places
				Math.round(30.0D * (b + 1) * 2 * 3 / 7.0D * Math.min(1.0D, b / 4.0D) * hari),   // protection tribute, 3 towns
				Math.round(10.0D * (b + 1) / 7.0D * hari),                     // loan interest
				Math.round(3.0D * 4 * (b + 1) * hari)};                        // residents' requests
		String[] nama = {"toko", "investasi", "gaji", "upeti", "bunga", "permintaan"};
		long sewaRumah = Math.round(1800.0D * 1.0D * cfg.sewaPersen / 100.0D / 7.0D * hari);
		long pajakToko = Math.round(cfg.tokoBatas * (b + 1) * 1.5D * 0.6D * hari * LapakSekte.PAJAK);
		long pembantu = Math.round(12.0D / 7.0D * hari) * 2;
		long masuk = 0L;
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.sim_judul", b, hari).withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < keran.length; i++) {
			masuk += keran[i];
			int idx = i;
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.sim_baris", nama[idx], keran[idx], keran[idx] / Math.max(1, hari))
					.withStyle(ChatFormatting.GRAY), false);
		}
		long keluar = sewaRumah + pajakToko + pembantu;
		long bersihHari = (masuk - keluar) / Math.max(1, hari);
		long bawah = SASARAN[b][0];
		long atas = SASARAN[b][1];
		long total = (bawah + atas) / 4L + bersihHari;
		boolean ok = total >= bawah * 3L / 4L && total <= atas * 5L / 4L;
		long masukF = masuk;
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.harga.uji.sim_hasil", masukF, keluar, bersihHari, total, bawah, atas,
				Component.translatable(ok ? "kultivator.kota42.harga.uji.ok" : "kultivator.kota42.harga.uji.lewat"))
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	private KotaEkonomi() {
	}
}

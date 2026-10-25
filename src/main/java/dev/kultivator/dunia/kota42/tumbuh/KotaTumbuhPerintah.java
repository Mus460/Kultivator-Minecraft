package dev.kultivator.dunia.kota42.tumbuh;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomi;
import dev.kultivator.dunia.tempat.PotonganInfo;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The commands of 42F (DESIGN 42.14.2-42.14.3).
 * <ul>
 *   <li>{@code uji kota_hidup tumbuh [info]}, {@code kavling [daftar|tp <n>|pilih <n> <rencana>|mulai <n>]},
 *       {@code tahap [lanjut|semua]}, {@code bahan isi}, {@code makmur <n>}, {@code rusak_proyek};</li>
 *   <li>{@code uji kota_hidup kompat periksa [radius]}: structures of other namespaces touching the plots and lots of the
 *       places near the tester, and the plots marked {@code TIDAK_LAYAK} with their reason (42.12.1A);</li>
 *   <li>{@code /kultivasi kotaku kavling [<n> [sumbang <nb>|rencana <id>]]}, {@code kavling jual|beri <bahan> <n>},
 *       {@code kotaku bangun [sumbang <nb>|alihkan]}: the same actions as the plot screen's buttons.</li>
 * </ul>
 */
final class KotaTumbuhPerintah {

	private static final String K = "kultivator.kota42.tumbuh.";

	// ================================================================== uji kota_hidup tumbuh

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("tumbuh")
				.executes(ctx -> info(ctx))
				.then(Commands.literal("info").executes(KotaTumbuhPerintah::info))
				.then(Commands.literal("kavling").executes(ctx -> daftar(ctx, true))
						.then(Commands.literal("daftar").executes(ctx -> daftar(ctx, true)))
						.then(Commands.literal("tp").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(KotaTumbuhPerintah::tp)))
						.then(Commands.literal("mulai").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(KotaTumbuhPerintah::mulai)))
						.then(Commands.literal("pilih").then(Commands.argument("n", IntegerArgumentType.integer(1))
								.then(Commands.argument("rencana", StringArgumentType.greedyString()).suggests((ctx, b) -> {
									MinecraftServer server = ctx.getSource().getServer();
									return SharedSuggestionProvider.suggest(RencanaTumbuh.semua(server).keySet(), b);
								}).executes(KotaTumbuhPerintah::pilih)))))
				.then(Commands.literal("tahap").executes(ctx -> tahap(ctx, false))
						.then(Commands.literal("lanjut").executes(ctx -> tahap(ctx, false)))
						.then(Commands.literal("semua").executes(ctx -> tahap(ctx, true))))
				.then(Commands.literal("bahan").then(Commands.literal("isi").executes(KotaTumbuhPerintah::bahanIsi)))
				.then(Commands.literal("makmur").then(Commands.argument("n", IntegerArgumentType.integer(-1, 100)).executes(KotaTumbuhPerintah::makmur)))
				.then(Commands.literal("rusak_proyek").executes(KotaTumbuhPerintah::rusakProyek));
	}

	private record Tempat(ServerPlayer player, ServerLevel level, MinecraftServer server, long situs) {
	}

	private static @Nullable Tempat tempat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo t = KotaHidupCommands.tempatPemain(player);
		if (t == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return null;
		}
		return new Tempat(player, player.level(), ctx.getSource().getServer(), t.situsId());
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		CommandSourceStack src = ctx.getSource();
		int[] terms = Kemakmuran.rincian(t.server(), t.situs());
		MutableComponent line = Component.translatable(K + "uji.makmur", Kemakmuran.nilai(t.server(), t.situs()), KotaConfig.tumbuh().ambang,
				Kemakmuran.tren(t.server(), t.situs()));
		for (int i = 0; i < terms.length; i++) {
			line.append(" ").append(Component.translatable(K + "makmur." + Kemakmuran.SUKU[i])).append("=" + terms[i]);
		}
		src.sendSuccess(() -> line.withStyle(ChatFormatting.GOLD), false);
		src.sendSuccess(() -> Component.translatable(K + "uji.bahan", stokTeks(BahanKota.stok(t.server(), t.situs())),
				KotaEkonomi.kasKota(t.server(), t.situs())), false);
		List<String> needs = PekerjaanTumbuh.kebutuhan(t.server(), t.level(), t.situs());
		src.sendSuccess(() -> Component.translatable(K + "uji.butuh", needs.isEmpty() ? "-" : String.join(", ", needs),
				PekerjaanTumbuh.serentak(t.server(), t.situs())), false);
		int[] pr = BangunKembali.kemajuan(t.server(), t.situs());
		src.sendSuccess(() -> Component.translatable(K + "uji.bangun", BangunKembali.aktif(t.server(), t.situs()) ? 1 : 0, pr[0], pr[1],
				TuguKenangan.daftar(t.server(), t.situs()).size()), false);
		return daftar(ctx, false);
	}

	private static Component stokTeks(int[] stok) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < stok.length; i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.translatable(K + "bahan." + BahanKota.JENIS.get(i))).append(" " + stok[i]);
		}
		return out;
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx, boolean uji) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		List<Kavling.Rinci> plots = Kavling.semuaRinci(t.server(), t.situs());
		if (plots.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "kosong").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		for (Kavling.Rinci r : plots) {
			ctx.getSource().sendSuccess(() -> baris(t.server(), r, uji), false);
		}
		return plots.size();
	}

	/** One plot line: "#3 Rumah Toko (gaya ibu kota pasar) · tahap 2/5 dinding · dibangun". */
	static Component baris(MinecraftServer server, Kavling.Rinci r, boolean uji) {
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, r.rencana());
		Component name = plan == null ? Component.literal(r.rencana()) : PekerjaanTumbuh.namaRencana(plan);
		int t = r.info().tahap();
		Component stage = t < 0 ? Component.translatable(K + "tidak_layak")
				: Component.translatable(K + "tahap", t, Kavling.SELESAI, t > 0 ? Component.translatable(K + "tahap." + RencanaTumbuh.NAMA_TAHAP.get(t - 1))
				: Component.translatable(K + "tahap.belum"));
		MutableComponent line = Component.translatable(K + "baris", r.info().id(), name, stage, Component.translatable(K + "keadaan." + r.keadaan()));
		if (!r.alasan().isEmpty()) {
			line.append(" ").append(Component.translatable(K + "alasan." + r.alasan(), r.terhalang()).withStyle(ChatFormatting.YELLOW));
		}
		if (uji) {
			line.append(Component.literal(" @" + r.info().pos().toShortString() + " " + r.info().hadap().getSerializedName()
					+ (r.job() != 0L ? " job=" + r.job() + " " + java.util.Arrays.toString(PerbaikanApi.sisa(r.job())) : "")).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (!r.papanNama().isEmpty()) {
			line.append(" ").append(Component.translatable(K + "papan", r.papanNama()).withStyle(ChatFormatting.AQUA));
		}
		return line;
	}

	private static @Nullable CompoundTag plot(CommandContext<CommandSourceStack> ctx, Tempat t) {
		int n = IntegerArgumentType.getInteger(ctx, "n");
		CompoundTag c = Kavling.mentah(t.server(), t.situs(), n);
		if (c == null) {
			ctx.getSource().sendFailure(Component.translatable(K + "tak_ada"));
		}
		return c;
	}

	private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		CompoundTag c = t == null ? null : plot(ctx, t);
		if (c == null) {
			return 0;
		}
		BoundingBox box = Kavling.kotak(c);
		net.minecraft.core.Direction street = Kavling.info(c).hadap();
		double x = box.getCenter().getX() + 0.5D + street.getStepX() * (box.getXSpan() / 2.0D + 3.0D);
		double z = box.getCenter().getZ() + 0.5D + street.getStepZ() * (box.getZSpan() / 2.0D + 3.0D);
		t.player().teleportTo(x, box.minY() + 1.0D, z);
		return 1;
	}

	private static int mulai(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		CompoundTag c = t == null ? null : plot(ctx, t);
		if (c == null) {
			return 0;
		}
		PekerjaanTumbuh.paksaMulai(t.server(), c);
		ctx.getSource().sendSuccess(() -> baris(t.server(), Kavling.rinci(c), true), false);
		return 1;
	}

	private static int pilih(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		CompoundTag c = t == null ? null : plot(ctx, t);
		if (c == null) {
			return 0;
		}
		Component err = Kavling.pilih(t.server(), t.situs(), c, StringArgumentType.getString(ctx, "rencana").trim());
		if (err != null) {
			ctx.getSource().sendFailure(err);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> baris(t.server(), Kavling.rinci(c), true), false);
		return 1;
	}

	/** Gives the running plot the material of its next stage ({@code semua}: of every stage left) and starts it now. */
	private static int tahap(CommandContext<CommandSourceStack> ctx, boolean semua) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		CompoundTag c = Kavling.berjalan(t.server(), t.situs());
		if (c == null) {
			for (CompoundTag k : Kavling.semua(t.server(), t.situs())) {
				if (k.getIntOr("t", 0) == 0 && Kavling.info(k).layak()) {
					c = k;
					break;
				}
			}
		}
		if (c == null) {
			ctx.getSource().sendFailure(Component.translatable(K + "tak_ada"));
			return 0;
		}
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(t.server(), c.getStringOr("r", ""));
		if (plan == null) {
			ctx.getSource().sendFailure(Component.translatable(K + "rencana_salah", c.getStringOr("r", "")));
			return 0;
		}
		for (int s = c.getIntOr("t", 0) + 1; s <= (semua ? Kavling.SELESAI : c.getIntOr("t", 0) + 1); s++) {
			RencanaTumbuh.Tahap stage = plan.tahap(s);
			if (stage != null) {
				for (int i = 0; i < stage.bahan().length; i++) {
					BahanKota.tambah(t.server(), t.situs(), i, stage.bahan()[i]);
				}
			}
		}
		PekerjaanTumbuh.paksaMulai(t.server(), c);
		CompoundTag done = c;
		ctx.getSource().sendSuccess(() -> baris(t.server(), Kavling.rinci(done), true), false);
		return 1;
	}

	private static int bahanIsi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		int[] full = new int[BahanKota.JENIS.size()];
		java.util.Arrays.fill(full, 500);
		BahanKota.setStok(t.server(), t.situs(), full);
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "uji.bahan", stokTeks(full), KotaEkonomi.kasKota(t.server(), t.situs())), false);
		return 1;
	}

	private static int makmur(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		int n = IntegerArgumentType.getInteger(ctx, "n");
		Kemakmuran.timpa(t.server(), t.situs(), n, 7);
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "uji.makmur", Kemakmuran.nilai(t.server(), t.situs()), KotaConfig.tumbuh().ambang,
				Kemakmuran.tren(t.server(), t.situs())), false);
		return 1;
	}

	private static int rusakProyek(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		int open = PerbaikanApi.rusakTerbuka(t.situs());
		if (open <= 0) {
			ctx.getSource().sendFailure(Component.translatable(K + "uji.tanpa_rusak"));
			return 0;
		}
		BangunKembali.mulai(t.server(), t.level(), t.situs(), open);
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "bangun.mulai", open), false);
		return 1;
	}

	// ================================================================== uji kota_hidup kompat periksa

	static LiteralArgumentBuilder<CommandSourceStack> kompat() {
		return Commands.literal("kompat").then(Commands.literal("periksa").executes(ctx -> periksa(ctx, 256))
				.then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048)).executes(ctx -> periksa(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
	}

	private static int periksa(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		MinecraftServer server = ctx.getSource().getServer();
		int foreign = 0;
		int dead = 0;
		int places = 0;
		for (TempatInfo info : TempatApi.dekat(level, player.blockPosition(), radius, "")) {
			places++;
			long situs = info.situsId();
			for (PotonganInfo p : TempatApi.potongan(level, situs)) {
				String id = PekerjaanTumbuh.asing(level, p.kotak());
				if (id != null) {
					foreign++;
					String where = p.templat() + " @" + p.kotak().getCenter().toShortString();
					ctx.getSource().sendSuccess(() -> Component.translatable(K + "kompat.lot", where, id).withStyle(ChatFormatting.RED), false);
				}
			}
			for (CompoundTag c : Kavling.semua(server, situs)) {
				String why = c.getIntOr("t", 0) < 0 ? c.getStringOr("a", "") : PekerjaanTumbuh.periksaUji(level, c);
				if (why == null || "jauh".equals(why)) {
					continue;
				}
				if (why.startsWith("!") || c.getIntOr("t", 0) < 0) {
					dead++;
				}
				String reason = why.startsWith("!") ? why.substring(1) : why;
				String extra = c.getStringOr("asing", "");
				Component line = Component.translatable(K + "kompat.kavling", situs, c.getIntOr("i", 0), Component.translatable(K + "alasan." + reason, 0),
						extra).withStyle(ChatFormatting.YELLOW);
				ctx.getSource().sendSuccess(() -> line, false);
			}
		}
		int f = foreign;
		int d = dead;
		int n = places;
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "kompat.ringkas", n, f, d)
				.withStyle(f == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return f;
	}

	// ================================================================== /kultivasi kotaku kavling | bangun

	static LiteralArgumentBuilder<CommandSourceStack> kotaku() {
		return Commands.literal("kavling").executes(ctx -> daftarPemain(ctx))
				.then(Commands.literal("jual").then(bahanArg(KotaTumbuhPerintah::jual)))
				.then(Commands.literal("beri").then(bahanArg(KotaTumbuhPerintah::beri)))
				.then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(KotaTumbuhPerintah::rinciPemain)
						.then(Commands.literal("sumbang").then(Commands.argument("nb", LongArgumentType.longArg(1L)).executes(KotaTumbuhPerintah::sumbangNb)))
						.then(Commands.literal("rencana").then(Commands.argument("rencana", StringArgumentType.greedyString()).executes(KotaTumbuhPerintah::rencana))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> bahanArg(
			com.mojang.brigadier.Command<CommandSourceStack> run) {
		return Commands.argument("bahan", StringArgumentType.word()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(BahanKota.JENIS, b))
				.then(Commands.argument("satuan", IntegerArgumentType.integer(1, 999)).executes(run));
	}

	private static int daftarPemain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		if (!KotaConfig.tumbuh().aktif) {
			ctx.getSource().sendFailure(Component.translatable(K + "mati"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "judul", Kemakmuran.nilai(t.server(), t.situs()), KotaConfig.tumbuh().ambang,
				stokTeks(BahanKota.stok(t.server(), t.situs()))).withStyle(ChatFormatting.GOLD), false);
		return daftar(ctx, false);
	}

	private static int rinciPemain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		CompoundTag c = t == null ? null : plot(ctx, t);
		if (c == null) {
			return 0;
		}
		Kavling.Rinci r = Kavling.rinci(c);
		ctx.getSource().sendSuccess(() -> baris(t.server(), r, false), false);
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(t.server(), r.rencana());
		if (plan != null && r.info().layak() && !r.info().selesai()) {
			RencanaTumbuh.Tahap next = plan.tahap(r.info().tahap() + 1);
			if (next != null) {
				int[] stock = BahanKota.stok(t.server(), t.situs());
				MutableComponent need = Component.empty();
				for (int i = 0; i < next.bahan().length; i++) {
					if (next.bahan()[i] > 0) {
						need.append(Component.translatable(K + "perlu", Component.translatable(K + "bahan." + BahanKota.JENIS.get(i)),
								Math.min(stock[i], next.bahan()[i]), next.bahan()[i], BahanKota.tas(t.player(), i))).append(" ");
					}
				}
				ctx.getSource().sendSuccess(() -> need, false);
			}
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "biaya", c.getLongOr("biaya", PekerjaanTumbuh.biaya(t.server(), t.situs(), plan)),
					r.dana().getOrDefault(t.player().getUUID(), 0L)), false);
		}
		for (UUID u : r.dana().keySet()) {
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "dana", r.namaDana().getOrDefault(u, "?"), r.dana().get(u))
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int hasil(CommandContext<CommandSourceStack> ctx, @Nullable Component err, String okKey, Object... args) {
		if (err != null) {
			ctx.getSource().sendFailure(err);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(K + okKey, args).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int sumbangNb(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		long nb = LongArgumentType.getLong(ctx, "nb");
		int n = IntegerArgumentType.getInteger(ctx, "n");
		return hasil(ctx, Kavling.sumbangNb(t.player(), t.situs(), n, nb), "sumbang_ok", nb, n);
	}

	private static int rencana(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "rencana").trim();
		int n = IntegerArgumentType.getInteger(ctx, "n");
		return hasil(ctx, Kavling.pilihRencana(t.player(), t.situs(), n, id), "rencana_ok", n, id);
	}

	private static int perBahan(CommandContext<CommandSourceStack> ctx, String okKey,
			Function<Integer, Function<Integer, Component>> run) throws CommandSyntaxException {
		int i = BahanKota.JENIS.indexOf(StringArgumentType.getString(ctx, "bahan"));
		int n = IntegerArgumentType.getInteger(ctx, "satuan");
		if (i < 0) {
			ctx.getSource().sendFailure(Component.translatable(K + "bahan_salah"));
			return 0;
		}
		return hasil(ctx, run.apply(i).apply(n), okKey, n, Component.translatable(K + "bahan." + BahanKota.JENIS.get(i)));
	}

	private static int jual(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		return t == null ? 0 : perBahan(ctx, "jual_ok", i -> n -> BahanKota.jual(t.player(), t.situs(), i, n));
	}

	private static int beri(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		return t == null ? 0 : perBahan(ctx, "beri_ok", i -> n -> BahanKota.sumbang(t.player(), t.situs(), i, n));
	}

	static LiteralArgumentBuilder<CommandSourceStack> kotakuBangun() {
		return Commands.literal("bangun").executes(KotaTumbuhPerintah::bangunInfo)
				.then(Commands.literal("sumbang").then(Commands.argument("nb", LongArgumentType.longArg(1L)).executes(ctx -> {
					Tempat t = tempat(ctx);
					long nb = LongArgumentType.getLong(ctx, "nb");
					return t == null ? 0 : hasil(ctx, BangunKembali.sumbang(t.player(), t.situs(), nb), "bangun.sumbang_ok", nb);
				})))
				.then(Commands.literal("alihkan").executes(ctx -> {
					Tempat t = tempat(ctx);
					return t == null ? 0 : hasil(ctx, BangunKembali.alihkanKas(t.player(), t.situs()), "bangun.alihkan_ok", BangunKembali.ALIH_NB);
				}));
	}

	private static int bangunInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		if (!BangunKembali.aktif(t.server(), t.situs())) {
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "bangun.tak_ada").withStyle(ChatFormatting.GRAY), false);
		} else {
			int[] pr = BangunKembali.kemajuan(t.server(), t.situs());
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "bangun.status", pr[0] - pr[1], pr[0],
					BangunKembali.pendatang(t.server(), t.situs()) ? Component.translatable(K + "bangun.pendatang") : Component.empty())
					.withStyle(ChatFormatting.GOLD), false);
		}
		for (TuguKenangan.Tugu tugu : TuguKenangan.daftar(t.server(), t.situs())) {
			StringBuilder names = new StringBuilder();
			for (TuguKenangan.Penyumbang p : tugu.penyumbang()) {
				names.append(names.isEmpty() ? "" : ", ").append(p.nama());
			}
			ctx.getSource().sendSuccess(() -> Component.translatable(K + "bangun.tugu", tugu.hari(), tugu.blok(), names.isEmpty() ? "-" : names.toString())
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private KotaTumbuhPerintah() {
	}
}

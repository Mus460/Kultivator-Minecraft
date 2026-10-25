package dev.kultivator.dunia.kota42;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.KodeWajah;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.HariWarga;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.SiklusHidup;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.kota42.warga.WargaGen;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The resident subs of the 42 commands (DESIGN 42.14.2-42.14.3), hung on the roots of {@link KotaUji} and
 * {@link KotaHidupCommands} by 42A:
 * <ul>
 * <li>{@code /kultivasi uji kota_hidup warga info [id|nama]} (the nearest resident body when empty), {@code daftar <situs>},
 * {@code tahun <n>}, {@code hari <n>} (run real Hari Warga passes, every event recorded, world time untouched),
 * {@code nikah <a> <b>}, {@code lahir <a>}, {@code wafat <a>}, {@code pindah <a> <situs>}, {@code sakit <a>},
 * {@code kesan <a> <n>}, {@code kenangan <a> <jenis>}, {@code utang <a> <n>}, {@code suasana <a> <kode>},
 * {@code kebiasaan <a> <id>}, {@code pohon <a>} (three generations up and down in chat), {@code langkah} (the registered
 * Hari Warga steps and the queue);</li>
 * <li>{@code /kultivasi kotaku warga <nama>}: the Kartu Warga in chat (the screen of 42U opens from the dialog).</li>
 * </ul>
 * Residents are named by their global id (shown by {@code info}) or by name inside the tester's place.
 */
public final class KotaUjiWarga {

	private static boolean registered;

	/** From {@code KotaWarga.init}. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		KotaUji.daftar("warga", KotaUjiWarga::uji);
		KotaHidupCommands.daftar("warga", KotaUjiWarga::pemain);
		KotaUji.chips("kota_hidup warga langkah");
	}

	// ================================================================== trees

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("warga")
				.then(Commands.literal("info").executes(ctx -> info(ctx, null))
						.then(Commands.argument("siapa", StringArgumentType.greedyString()).executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "siapa")))))
				.then(Commands.literal("daftar").then(Commands.argument("situs", LongArgumentType.longArg())
						.executes(ctx -> daftar(ctx, LongArgumentType.getLong(ctx, "situs")))))
				.then(Commands.literal("tahun").then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> majukan(ctx, IntegerArgumentType.getInteger(ctx, "n") * KotaConfig.warga().hariPerTahun))))
				.then(Commands.literal("hari").then(Commands.argument("n", IntegerArgumentType.integer(1, 400))
						.executes(ctx -> majukan(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("nikah").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("b", IntegerArgumentType.integer(1))
						.executes(ctx -> nikah(ctx, IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "b"))))))
				.then(satu("lahir", KotaUjiWarga::lahir))
				.then(satu("wafat", KotaUjiWarga::wafat))
				.then(satu("sakit", KotaUjiWarga::sakit))
				.then(satu("pohon", KotaUjiWarga::pohon))
				.then(Commands.literal("pindah").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("situs", LongArgumentType.longArg())
						.executes(ctx -> pindah(ctx, IntegerArgumentType.getInteger(ctx, "a"), LongArgumentType.getLong(ctx, "situs"))))))
				.then(Commands.literal("kesan").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("n", IntegerArgumentType.integer(-100, 100))
						.executes(ctx -> kesan(ctx, IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("utang").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("n", IntegerArgumentType.integer(-100000, 100000))
						.executes(ctx -> utang(ctx, IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("kenangan").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("jenis", StringArgumentType.word())
						.suggests((ctx, b) -> {
							for (Kenangan k : Kenangan.values()) {
								b.suggest(k.name().toLowerCase(Locale.ROOT));
							}
							return b.buildFuture();
						})
						.executes(ctx -> kenangan(ctx, IntegerArgumentType.getInteger(ctx, "a"), StringArgumentType.getString(ctx, "jenis"))))))
				.then(Commands.literal("suasana").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("kode", StringArgumentType.word())
						.suggests((ctx, b) -> {
							for (KodeWajah k : KodeWajah.values()) {
								b.suggest(k.name().toLowerCase(Locale.ROOT));
							}
							return b.buildFuture();
						})
						.executes(ctx -> suasana(ctx, IntegerArgumentType.getInteger(ctx, "a"), StringArgumentType.getString(ctx, "kode"))))))
				.then(Commands.literal("kebiasaan").then(Commands.argument("a", IntegerArgumentType.integer(1)).then(Commands.argument("id", IntegerArgumentType.integer(0, 32))
						.executes(ctx -> kebiasaan(ctx, IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "id"))))))
				.then(Commands.literal("langkah").executes(KotaUjiWarga::langkah));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pemain() {
		return Commands.literal("warga")
				.executes(ctx -> kartuPemain(ctx, null))
				.then(Commands.argument("nama", StringArgumentType.greedyString()).executes(ctx -> kartuPemain(ctx, StringArgumentType.getString(ctx, "nama"))));
	}

	@FunctionalInterface
	private interface Aksi {
		int run(CommandContext<CommandSourceStack> ctx, Warga w) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> satu(String name, Aksi aksi) {
		return Commands.literal(name).then(Commands.argument("a", IntegerArgumentType.integer(1)).executes(ctx -> {
			Warga w = cari(ctx, IntegerArgumentType.getInteger(ctx, "a"));
			return w == null ? 0 : aksi.run(ctx, w);
		}));
	}

	// ================================================================== lookup

	private static @Nullable Warga cari(CommandContext<CommandSourceStack> ctx, int id) {
		Warga w = WargaApi.byId(id);
		if (w == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.tidak_ada", id));
		}
		return w;
	}

	/** By id, by full or given name in the player's place, else the nearest resident body within 8 blocks. */
	private static @Nullable Warga cariSiapa(ServerPlayer player, @Nullable String siapa) {
		if (siapa != null && !siapa.isBlank()) {
			try {
				return WargaApi.byId(Integer.parseInt(siapa.trim()));
			} catch (NumberFormatException ignored) {
				// a name
			}
			TempatInfo tempat = KotaHidupCommands.tempatPemain(player);
			if (tempat == null) {
				return null;
			}
			String q = siapa.trim().toLowerCase(Locale.ROOT);
			for (Warga w : WargaApi.wargaTempat(tempat.situsId())) {
				if (NamaWarga.lengkap(w).toLowerCase(Locale.ROOT).equals(q)) {
					return w;
				}
			}
			for (Warga w : WargaApi.wargaTempat(tempat.situsId())) {
				String full = NamaWarga.lengkap(w).toLowerCase(Locale.ROOT);
				if (full.contains(q) || NamaWarga.diri(w).toLowerCase(Locale.ROOT).equals(q)) {
					return w;
				}
			}
			return null;
		}
		KultivatorNpc best = null;
		double bestSq = 64.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D), n -> n.isAlive())) {
			double d = npc.distanceToSqr(player);
			if (d < bestSq && WargaApi.dari(npc) != null) {
				bestSq = d;
				best = npc;
			}
		}
		return best == null ? null : WargaApi.dari(best);
	}

	private static @Nullable WargaData.Situs situsPemain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = KotaHidupCommands.tempatPemain(player);
		if (tempat == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return null;
		}
		MinecraftServer server = ctx.getSource().getServer();
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(tempat.situsId());
		if (s == null || !s.bangkit()) {
			TempatHuni huni = Pemanggil.huni(tempat.situsId());
			if (huni != null && HariWarga.dihuni(huni) && KotaConfig.warga().aktif) {
				HariWarga.bangkit(server, data, huni, KotaHidup.hari(server));
				s = data.peek(tempat.situsId());
			}
		}
		if (s == null || !s.bangkit()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.status.belum"));
			return null;
		}
		return s;
	}

	// ================================================================== cards

	/** The full card of one resident (42.11.2 in chat). */
	public static List<Component> kartu(Warga w, @Nullable ServerPlayer viewer) {
		List<Component> out = new ArrayList<>();
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return out;
		}
		WargaData data = WargaData.get(server);
		WargaInfo info = WargaApi.info(w);
		out.add(Component.translatable("kultivator.kota42.warga.kartu.judul", NamaWarga.komponen(w), NamaWarga.hanzi(w), w.id)
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		out.add(Component.translatable("kultivator.kota42.warga.kartu.diri", info.umur(), info.tahap().nama(),
				Component.translatable(w.pria() ? "kultivator.kota42.warga.pria" : "kultivator.kota42.warga.wanita"),
				Component.translatable(info.peran().titleKey()), Component.translatable("kultivator.kota42.watak." + Tokoh.WATAK.get(Mth.clamp(w.watak, 0, 7)))));
		out.add(Component.translatable("kultivator.kota42.warga.kartu.badan", info.wajah().nama(), w.suasana, w.kesehatan, w.ahli,
				Component.translatable("kultivator.kota42.warga.akar." + w.akar), Component.translatable("kultivator.kota42.warga.status." + info.status()))
				.withStyle(ChatFormatting.GRAY));
		MutableComponent keluarga = Component.translatable("kultivator.kota42.warga.kartu.keluarga",
				namaOrang(data, w.ayah), namaOrang(data, w.ibu), namaOrang(data, w.pasangan), w.anak.length, w.rumahTangga);
		out.add(keluarga);
		if (w.tunangan != Warga.TANPA) {
			out.add(Component.translatable("kultivator.kota42.warga.kartu.tunangan", namaOrang(data, w.tunangan), w.hariNikah).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (w.hubungan.length > 0) {
			MutableComponent bonds = Component.translatable("kultivator.kota42.warga.kartu.hubungan");
			for (long b : w.hubungan) {
				JenisHubungan j = JenisHubungan.jenis(b);
				if (j != null) {
					bonds.append(Component.literal(" · ")).append(namaOrang(data, JenisHubungan.idLain(b))).append(Component.literal(" "))
							.append(j.nama()).append(Component.literal(" " + JenisHubungan.kekuatan(b)));
				}
			}
			out.add(bonds.withStyle(ChatFormatting.AQUA));
		}
		if (viewer != null) {
			Ingatan i = w.ingatanDari(data.cariPemain(viewer.getUUID()));
			if (i != null) {
				Kenangan last = i.terbaru();
				out.add(Component.translatable("kultivator.kota42.warga.kartu.ingatan", i.kesan, i.kenal, i.utang,
						last == null ? Component.literal("-") : Component.translatable("kultivator.kota42.kenangan." + last.name().toLowerCase(Locale.ROOT)))
						.withStyle(ChatFormatting.GREEN));
			}
		}
		if (w.kebiasaan1 != 0 || w.kebiasaan2 != 0) {
			out.add(Component.translatable("kultivator.kota42.warga.kartu.kebiasaan", w.kebiasaan1, w.kebiasaan2).withStyle(ChatFormatting.DARK_AQUA));
		}
		return out;
	}

	private static Component namaOrang(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return Component.literal("-");
		}
		Warga w = data.byId(id);
		if (w != null) {
			return NamaWarga.komponen(w);
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		if (e != null) {
			Warga ghost = new Warga(e.id());
			ghost.marga = e.marga();
			ghost.nama = e.nama();
			ghost.generasi = e.generasi();
			ghost.kelamin = e.wanita() ? Warga.WANITA : Warga.PRIA;
			return Component.translatable(e.wafat() ? "kultivator.kota42.warga.almarhum" : "kultivator.kota42.warga.jauh", NamaWarga.komponen(ghost));
		}
		return Component.literal("#" + id);
	}

	/** The {@code uji hidup audit warga} line of a resident body, or null when it stands for nobody. */
	public static @Nullable Component barisAudit(KultivatorNpc npc) {
		Warga w = WargaApi.dari(npc);
		if (w == null) {
			return null;
		}
		WargaInfo info = WargaApi.info(w);
		return Component.translatable("kultivator.kota42.warga.audit", NamaWarga.komponen(w), info.umur(), Component.translatable(info.peran().titleKey()),
				info.wajah().nama(), w.suasana, w.kesehatan, w.id);
	}

	// ================================================================== subs

	private static int info(CommandContext<CommandSourceStack> ctx, @Nullable String siapa) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Warga w = cariSiapa(player, siapa);
		if (w == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.tidak_ketemu"));
			return 0;
		}
		for (Component line : kartu(w, player)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int kartuPemain(CommandContext<CommandSourceStack> ctx, @Nullable String nama) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!KotaConfig.warga().aktif) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.mati"));
			return 0;
		}
		Warga w = cariSiapa(player, nama);
		if (w == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.tidak_ketemu"));
			return 0;
		}
		for (Component line : kartu(w, player)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx, long situs) {
		MinecraftServer server = ctx.getSource().getServer();
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (s == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.status.belum"));
			return 0;
		}
		long hari = KotaHidup.hari(server);
		Map<WargaInfo.Tahap, Integer> perTahap = new TreeMap<>();
		Map<Integer, Integer> rumah = new TreeMap<>();
		int berakar = 0;
		int nganggur = 0;
		int pasangan = 0;
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			int u = SiklusHidup.umurPada(w, hari);
			perTahap.merge(WargaInfo.Tahap.dari(u), 1, Integer::sum);
			rumah.merge(w.rumahTangga, 1, Integer::sum);
			berakar += w.akar > 0 ? 1 : 0;
			nganggur += w.slot == Warga.TANPA_SLOT && u >= 16 && u < 65 ? 1 : 0;
			pasangan += w.pasangan != Warga.TANPA && w.pria() ? 1 : 0;
		}
		int households = rumah.size();
		int roots = berakar;
		int idle = nganggur;
		int couples = pasangan;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.daftar", situs, s.stat.getIntOr("pop", s.warga.size()),
				households, couples, idle, roots, s.arsip.size(), s.hariTerakhir).withStyle(ChatFormatting.GOLD), false);
		for (Map.Entry<WargaInfo.Tahap, Integer> e : perTahap.entrySet()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.warga.baris", e.getKey().nama(), e.getValue()), false);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.daftar_peristiwa", s.stat.getIntOr("nLahir", 0),
				s.stat.getIntOr("nNikah", 0), s.stat.getIntOr("nWafat", 0), s.stat.getIntOr("rata", 0)).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int majukan(CommandContext<CommandSourceStack> ctx, int hari) throws CommandSyntaxException {
		WargaData.Situs s = situsPemain(ctx);
		if (s == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		int lahir = s.stat.getIntOr("nLahir", 0);
		int nikah = s.stat.getIntOr("nNikah", 0);
		int wafat = s.stat.getIntOr("nWafat", 0);
		HariWarga.majukan(server, WargaData.get(server), s, hari);
		int dl = s.stat.getIntOr("nLahir", 0) - lahir;
		int dn = s.stat.getIntOr("nNikah", 0) - nikah;
		int dw = s.stat.getIntOr("nWafat", 0) - wafat;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.majukan", hari, dl, dn, dw, s.warga.size())
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int nikah(CommandContext<CommandSourceStack> ctx, int a, int b) {
		Warga x = cari(ctx, a);
		Warga y = cari(ctx, b);
		MinecraftServer server = ctx.getSource().getServer();
		if (x == null || y == null) {
			return 0;
		}
		WargaData data = WargaData.get(server);
		if (x.kelamin == y.kelamin || !x.lajang() && x.tunangan != y.id || !y.lajang() && y.tunangan != x.id) {
			// 42.16 K9: only one man and one woman, both single
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.nikah_tolak"));
			return 0;
		}
		long situs = data.situsDari(x.id);
		boolean ok = SiklusHidup.nikah(server, data, situs, x, y, KotaHidup.hari(server), RandomSource.create(x.id * 31L + y.id));
		ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.kota42.warga.uji.nikah" : "kultivator.kota42.warga.uji.nikah_tolak",
				NamaWarga.komponen(x), NamaWarga.komponen(y)), true);
		return ok ? 1 : 0;
	}

	private static int lahir(CommandContext<CommandSourceStack> ctx, Warga w) {
		MinecraftServer server = ctx.getSource().getServer();
		if (w.pria() || w.status != Warga.Status.HIDUP) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.lahir_tolak", NamaWarga.komponen(w)));
			return 0;
		}
		WargaData data = WargaData.get(server);
		List<Warga> babies = SiklusHidup.lahirkan(server, data, data.situsDari(w.id), w, KotaHidup.hari(server), RandomSource.create(w.id * 977L + KotaHidup.hari(server)));
		for (Warga b : babies) {
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.lahir", NamaWarga.komponen(b), NamaWarga.komponen(w), b.id), true);
		}
		return babies.size();
	}

	private static int wafat(CommandContext<CommandSourceStack> ctx, Warga w) {
		MinecraftServer server = ctx.getSource().getServer();
		WargaData data = WargaData.get(server);
		Component nama = NamaWarga.komponen(w);
		SiklusHidup.wafat(server, data, data.situsDari(w.id), w, ArsipWarga.SEBAB_USIA, KotaHidup.hari(server));
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.wafat", nama), true);
		return 1;
	}

	private static int sakit(CommandContext<CommandSourceStack> ctx, Warga w) {
		boolean sudah = w.kesehatan < 30;
		if (sudah) {
			SiklusHidup.sembuh(w);
		} else {
			SiklusHidup.sakit(w, RandomSource.create(w.id));
		}
		WargaData.get(ctx.getSource().getServer()).ubah();
		ctx.getSource().sendSuccess(() -> Component.translatable(sudah ? "kultivator.kota42.warga.uji.sembuh" : "kultivator.kota42.warga.uji.sakit",
				NamaWarga.komponen(w), w.kesehatan), true);
		return 1;
	}

	private static int pindah(CommandContext<CommandSourceStack> ctx, int a, long situs) {
		Warga w = cari(ctx, a);
		if (w == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		WargaData data = WargaData.get(server);
		long asal = data.situsDari(w.id);
		WargaData.Situs to = data.peek(situs);
		if (to == null || !to.bangkit() || asal == situs) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.pindah_tolak", situs));
			return 0;
		}
		long hari = KotaHidup.hari(server);
		w.slot = Warga.TANPA_SLOT;
		w.datang = hari;
		w.rumahTangga = to.rumahTanggaBaru();
		w.rumah = dev.kultivator.dunia.tempat.hidup.Penduduk.TANPA;
		data.tambah(situs, w);
		KotaBus.PINDAH.invoker().onPindah(server, w, asal, situs);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.pindah", NamaWarga.komponen(w), asal, situs), true);
		return 1;
	}

	private static int kesan(CommandContext<CommandSourceStack> ctx, int a, int n) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Warga w = cari(ctx, a);
		if (w == null) {
			return 0;
		}
		Ingatan i = WargaApi.ingatan(w, player.getUUID());
		i.setKesan(n);
		WargaData.get(ctx.getSource().getServer()).ubah();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.kesan", NamaWarga.komponen(w), i.kesan), true);
		return 1;
	}

	private static int utang(CommandContext<CommandSourceStack> ctx, int a, int n) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Warga w = cari(ctx, a);
		if (w == null) {
			return 0;
		}
		WargaData data = WargaData.get(ctx.getSource().getServer());
		Ingatan.ubahUtang(data, w, player.getUUID(), n);
		int now = WargaApi.ingatan(w, player.getUUID()).utang;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.utang", NamaWarga.komponen(w), now), true);
		return 1;
	}

	private static int kenangan(CommandContext<CommandSourceStack> ctx, int a, String jenis) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Warga w = cari(ctx, a);
		Kenangan k;
		try {
			k = Kenangan.valueOf(jenis.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			k = null;
		}
		if (w == null || k == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.warga.uji.kenangan_tolak", jenis));
			return 0;
		}
		WargaApi.ubahKesan(w, player, k.delta, "uji", k);
		Kenangan done = k;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.kenangan", NamaWarga.komponen(w),
				Component.translatable("kultivator.kota42.kenangan." + done.name().toLowerCase(Locale.ROOT))), true);
		return 1;
	}

	private static int suasana(CommandContext<CommandSourceStack> ctx, int a, String kode) {
		Warga w = cari(ctx, a);
		KodeWajah k = KodeWajah.byId(kode);
		if (w == null) {
			return 0;
		}
		w.suasana = switch (k) {
			case SENANG -> 60;
			case SEDIH -> -55;
			case MARAH -> -70;
			case TAKUT -> -40;
			default -> 0;
		};
		if (k == KodeWajah.SAKIT) {
			SiklusHidup.sakit(w, RandomSource.create(w.id));
		}
		WargaData.get(ctx.getSource().getServer()).ubah();
		TempatHuni huni = Pemanggil.huni(WargaData.get(ctx.getSource().getServer()).situsDari(w.id));
		KultivatorNpc body = huni == null || w.slot == Warga.TANPA_SLOT ? null : huni.tubuhDari(w.slot);
		if (body != null) {
			SosokApi.setSuasana(body, k.sosok, 600);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.suasana", NamaWarga.komponen(w), k.nama()), true);
		return 1;
	}

	private static int kebiasaan(CommandContext<CommandSourceStack> ctx, int a, int id) {
		Warga w = cari(ctx, a);
		if (w == null) {
			return 0;
		}
		if (id == 0) {
			w.kebiasaan1 = 0;
			w.kebiasaan2 = 0;
		} else if (w.kebiasaan1 == 0 || w.kebiasaan1 == id) {
			w.kebiasaan1 = id;
		} else {
			w.kebiasaan2 = id;
		}
		WargaData.get(ctx.getSource().getServer()).ubah();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.kebiasaan", NamaWarga.komponen(w), w.kebiasaan1, w.kebiasaan2), true);
		return 1;
	}

	/** Three generations up and down (42.11.3 in chat): grandparents, parents, the person with spouse and siblings, children, grandchildren. */
	private static int pohon(CommandContext<CommandSourceStack> ctx, Warga w) {
		MinecraftServer server = ctx.getSource().getServer();
		WargaData data = WargaData.get(server);
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.warga.pohon.judul", NamaWarga.komponen(w)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		List<Integer> orangTua = List.of(w.ayah, w.ibu);
		List<Integer> kakekNenek = new ArrayList<>();
		for (int p : orangTua) {
			int[] gp = induk(data, p);
			kakekNenek.add(gp[0]);
			kakekNenek.add(gp[1]);
		}
		baris(src, data, "kakek_nenek", kakekNenek);
		baris(src, data, "orang_tua", orangTua);
		List<Integer> saudara = new ArrayList<>();
		for (int p : orangTua) {
			for (int c : anakDari(data, p)) {
				if (c != w.id && !saudara.contains(c)) {
					saudara.add(c);
				}
			}
		}
		baris(src, data, "diri", List.of(w.id, w.pasangan));
		baris(src, data, "saudara", saudara);
		List<Integer> anak = new ArrayList<>();
		for (int c : w.anak) {
			anak.add(c);
		}
		baris(src, data, "anak", anak);
		List<Integer> cucu = new ArrayList<>();
		for (int c : w.anak) {
			for (int g : anakDari(data, c)) {
				cucu.add(g);
			}
		}
		baris(src, data, "cucu", cucu);
		return 1;
	}

	private static int[] induk(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return new int[]{Warga.TANPA, Warga.TANPA};
		}
		Warga w = data.byId(id);
		if (w != null) {
			return new int[]{w.ayah, w.ibu};
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		return e == null ? new int[]{Warga.TANPA, Warga.TANPA} : new int[]{e.ayah(), e.ibu()};
	}

	private static int[] anakDari(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return new int[0];
		}
		Warga w = data.byId(id);
		if (w != null) {
			return w.anak;
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		return e == null ? new int[0] : e.anak();
	}

	private static void baris(CommandSourceStack src, WargaData data, String kunci, List<Integer> ids) {
		MutableComponent line = Component.translatable("kultivator.kota42.warga.pohon." + kunci).withStyle(ChatFormatting.YELLOW);
		boolean any = false;
		for (int id : ids) {
			if (id != Warga.TANPA) {
				line.append(Component.literal(any ? ", " : " ").withStyle(ChatFormatting.DARK_GRAY)).append(namaOrang(data, id));
				any = true;
			}
		}
		if (!any) {
			line.append(Component.literal(" -").withStyle(ChatFormatting.DARK_GRAY));
		}
		src.sendSuccess(() -> line, false);
	}

	private static int langkah(CommandContext<CommandSourceStack> ctx) {
		List<String> steps = HariWarga.namaLangkah();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.warga.uji.langkah", String.join(", ", steps), HariWarga.antrian(),
				WargaData.get(ctx.getSource().getServer()).jumlah()), false);
		return steps.size();
	}

	private KotaUjiWarga() {
	}
}

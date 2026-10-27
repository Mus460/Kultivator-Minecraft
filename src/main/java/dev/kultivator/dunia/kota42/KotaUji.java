package dev.kultivator.dunia.kota42;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota42.acara.KotaAcaraData;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiData;
import dev.kultivator.dunia.kota42.ragam.RagamData;
import dev.kultivator.dunia.kota42.sekte.KotaSekteData;
import dev.kultivator.dunia.kota42.tumbuh.KotaTumbuhData;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The test tree of Kota yang Hidup (DESIGN 42.14.3): {@code /kultivasi uji kota_hidup ...}, op 2, through
 * {@code DuniaCommands.test}. The root and the data subs live here; every module hangs its own subs ({@code warga},
 * {@code bicara}, {@code cakap}, {@code sebaran}, {@code ragam}, {@code kompat}, {@code gosip}, {@code jalan},
 * {@code tolong}, {@code ekonomi}, {@code acara}, {@code hewan}, {@code sekte}, {@code tumbuh}) with
 * {@link #daftar} from its init, so nobody edits this file for a new sub. The tree is built when Brigadier asks, so later
 * registrations count. Literals registered by a module under the same name as a built-in are merged by Brigadier.
 * <p>
 * Built in: {@code uji kota_hidup} (every chip of 42.14.3, clickable), {@code cek} (self-checks of the frozen formats),
 * {@code warga daftar} (stored residents of the place the tester is in, per life stage), {@code anggaran} (data sizes and
 * the entity cap), {@code reset [warga|ekonomi|acara|sekte|tumbuh|ragam|semua] [situs]}.
 */
public final class KotaUji {

	/** Chips of group {@code kota_hidup} of {@code /kultivasi uji} (42.14.3); a module may append more with {@link #chips}. */
	public static final List<String> CHIPS = new CopyOnWriteArrayList<>(List.of(
			"kota_hidup", "kota_hidup cek", "kota_hidup warga info [npc|nama]", "kota_hidup warga daftar [situs]",
			"kota_hidup warga tahun <n>", "kota_hidup warga hari <n>", "kota_hidup warga nikah <a> <b>", "kota_hidup warga lahir <a>",
			"kota_hidup warga wafat <a>", "kota_hidup warga pindah <a> <situs>", "kota_hidup warga sakit <a>",
			"kota_hidup warga kesan <a> <n>", "kota_hidup warga kenangan <a> <jenis>", "kota_hidup warga utang <a> <n>",
			"kota_hidup warga suasana <a> <kode>", "kota_hidup warga kebiasaan <a> <id>", "kota_hidup warga pohon <a>",
			"kota_hidup bicara uji <topik>", "kota_hidup cakap info <npc>", "kota_hidup cakap fakta <npc>", "kota_hidup cakap emosi <npc> <peristiwa>",
			"kota_hidup cakap uji <niat> [n]", "kota_hidup cakap kombinasi [niat]", "kota_hidup cakap obrol <npcA> <npcB> [jenis]",
			"kota_hidup cakap jejak", "kota_hidup cakap golden [tulis]", "kota_hidup sebaran peta", "kota_hidup sebaran zona",
			"kota_hidup sebaran titik", "kota_hidup sebaran simulasi <hari>", "kota_hidup ragam <warga>", "kota_hidup ragam laporan [hari]",
			"kota_hidup kompat periksa [radius]", "kota_hidup gosip daftar", "kota_hidup gosip buat <templat>", "kota_hidup gosip sebar <hari>",
			"kota_hidup gosip jejak <id>", "kota_hidup jalan <tujuan>", "kota_hidup tolong buat <jenis> [warga]", "kota_hidup tolong selesai",
			"kota_hidup ekonomi rumah [daftar|beli <id>|segel|lepas]", "kota_hidup ekonomi toko [buat|jual <jam>|laporan]",
			"kota_hidup ekonomi harga [kategori]", "kota_hidup ekonomi investasi [minggu <n>]", "kota_hidup ekonomi gudang",
			"kota_hidup ekonomi kontrak [buat|kirim|tiba]", "kota_hidup ekonomi pajak <tarif>", "kota_hidup ekonomi jabatan <pangkat>",
			"kota_hidup ekonomi perkara [buat <templat>|putus <n>]", "kota_hidup ekonomi pasar_hantu [buka|tp|razia]",
			"kota_hidup ekonomi simulasi <pita> <hari>", "kota_hidup acara rombongan [<id> sini]", "kota_hidup acara pagi",
			"kota_hidup acara ronda [geng <n>]", "kota_hidup acara copet", "kota_hidup acara tawuran", "kota_hidup acara gerobak",
			"kota_hidup acara api", "kota_hidup acara ayam", "kota_hidup acara anak_tersesat", "kota_hidup acara nikah", "kota_hidup acara makam",
			"kota_hidup acara lahir", "kota_hidup acara zhuazhou", "kota_hidup acara musim <musim> [hari_raya]", "kota_hidup acara malam",
			"kota_hidup acara wayang <naskah>", "kota_hidup acara guanpu", "kota_hidup hewan [semua|<jenis>]",
			"kota_hidup sekte kantor [buat|pelamar <n>|ujian]", "kota_hidup sekte pakta [buat|serbuan|gagal]", "kota_hidup sekte murid <tugas>",
			"kota_hidup sekte cabang <jenis>", "kota_hidup tumbuh kavling [daftar|tp <n>|pilih <rencana>]", "kota_hidup tumbuh tahap [lanjut|semua]",
			"kota_hidup tumbuh bahan isi", "kota_hidup tumbuh makmur <n>", "kota_hidup tumbuh rusak_proyek", "kota_hidup anggaran",
			"kota_hidup reset [bagian] [situs]"));

	private static final List<String> BAGIAN = List.of("warga", "ekonomi", "acara", "sekte", "tumbuh", "ragam", "semua");
	private static final Map<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> SUBS = new LinkedHashMap<>();
	private static boolean registered;

	/** From {@code KotaHidup.init}. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		DuniaCommands.test("kota_hidup", KotaUji::node);
	}

	/** Adds {@code uji kota_hidup <sub>}; the node's literal should be {@code sub}. */
	public static synchronized void daftar(String sub, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> node) {
		if (sub != null && node != null) {
			SUBS.put(sub + "#" + SUBS.size(), node);
		}
	}

	/** Appends chips (each starting with {@code "kota_hidup "}). */
	public static void chips(String... chips) {
		if (chips != null) {
			for (String chip : chips) {
				if (!CHIPS.contains(chip)) {
					CHIPS.add(chip);
				}
			}
		}
	}

	/** The root node {@code kota_hidup} with the built-in subs and every registered sub. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kota_hidup")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("cek").executes(ctx -> cek(ctx.getSource())))
				.then(Commands.literal("anggaran").executes(ctx -> anggaran(ctx.getSource())))
				.then(Commands.literal("warga").then(Commands.literal("daftar").executes(KotaUji::daftarWarga)));
		LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset").executes(ctx -> reset(ctx, "semua", 0L));
		for (String bagian : BAGIAN) {
			reset.then(Commands.literal(bagian).executes(ctx -> reset(ctx, bagian, 0L))
					.then(Commands.argument("situs", LongArgumentType.longArg())
							.executes(ctx -> reset(ctx, bagian, LongArgumentType.getLong(ctx, "situs")))));
		}
		root.then(reset);
		for (Map.Entry<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> entry : List.copyOf(SUBS.entrySet())) {
			try {
				ArgumentBuilder<CommandSourceStack, ?> sub = entry.getValue().get();
				if (sub != null) {
					root.then(sub);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kota hidup test command {} could not be built", entry.getKey(), exception);
			}
		}
		return root;
	}

	// ================================================================== built-in subs

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.uji.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		MutableComponent line = Component.literal(" ");
		int inLine = 0;
		for (String chip : CHIPS) {
			if (inLine > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			int cut = chip.length();
			for (int i = 0; i < chip.length(); i++) {
				if (chip.charAt(i) == '<' || chip.charAt(i) == '[') {
					cut = i;
					break;
				}
			}
			String command = "/kultivasi uji " + chip.substring(0, cut).trim() + (cut < chip.length() ? " " : "");
			line.append(Component.literal(chip).withStyle(style -> style.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.SuggestCommand(command))));
			if (++inLine >= 4) {
				MutableComponent done = line;
				source.sendSuccess(() -> done, false);
				line = Component.literal(" ");
				inLine = 0;
			}
		}
		if (inLine > 0) {
			MutableComponent done = line;
			source.sendSuccess(() -> done, false);
		}
		return 1;
	}

	/** {@code uji kota_hidup cek}: the frozen packings round-trip (bond long, titik numbers, resident codec). */
	private static int cek(CommandSourceStack source) {
		boolean kemas = JenisHubungan.cekKemas();
		boolean titik = KotaTitik.nomor("nisan_17") == 17 && KotaTitik.adalah("pajangan:guci") && !KotaTitik.adalah("kasur");
		Warga w = new Warga(4242);
		w.kelamin = Warga.WANITA;
		w.lahir = 1234L;
		w.hubungan = new long[]{JenisHubungan.kemas(7, JenisHubungan.SAHABAT, 55, 3L)};
		Warga back = Warga.muat(w.simpan());
		boolean kodek = back.id == 4242 && back.kelamin == Warga.WANITA && back.lahir == 1234L && back.hubungan.length == 1
				&& JenisHubungan.kekuatan(back.hubungan[0]) == 55;
		boolean ok = kemas && titik && kodek;
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.kota42.uji.cek.ok" : "kultivator.kota42.uji.cek.gagal",
				Boolean.toString(kemas), Boolean.toString(titik), Boolean.toString(kodek))
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	/** {@code uji kota_hidup warga daftar}: the stored residents of the tester's place per life stage. */
	private static int daftarWarga(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = KotaHidupCommands.tempatPemain(player);
		if (tempat == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		List<Warga> all = WargaApi.wargaTempat(tempat.situsId());
		Map<WargaInfo.Tahap, Integer> perTahap = new EnumMap<>(WargaInfo.Tahap.class);
		int berakar = 0;
		for (Warga w : all) {
			perTahap.merge(WargaInfo.Tahap.dari(WargaApi.umur(w)), 1, Integer::sum);
			if (w.akar > 0) {
				berakar++;
			}
		}
		int roots = berakar;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.warga.judul", tempat.jenis(), tempat.situsId(), all.size(), roots)
				.withStyle(ChatFormatting.GOLD), false);
		for (WargaInfo.Tahap t : WargaInfo.Tahap.values()) {
			int n = perTahap.getOrDefault(t, 0);
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.warga.baris", t.nama(), n), false);
		}
		return Math.max(1, all.size());
	}

	/** {@code uji kota_hidup anggaran}: data sizes of 42 and the entity cap. */
	private static int anggaran(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		WargaData warga = WargaData.get(server);
		int ekonomi = KotaEkonomiData.get(server).ukuran();
		int acara = KotaAcaraData.get(server).ukuran();
		int sekte = KotaSekteData.get(server).ukuran();
		int tumbuh = KotaTumbuhData.get(server).ukuran();
		int ragam = RagamData.get(server).ukuran();
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.uji.anggaran", warga.jumlah(), warga.semuaSitus().size(),
				ekonomi, acara, sekte, tumbuh, ragam, KotaHidup.ANGGARAN_MAKS), false);
		return 1;
	}

	/** {@code uji kota_hidup reset [bagian] [situs]}: forgets 42 data (one place when {@code situs} != 0). */
	private static int reset(CommandContext<CommandSourceStack> ctx, String bagian, long situs) {
		MinecraftServer server = ctx.getSource().getServer();
		boolean semua = "semua".equals(bagian);
		if (semua || "warga".equals(bagian)) {
			WargaData data = WargaData.get(server);
			if (situs != 0L) {
				data.hapusSitus(situs);
			} else {
				data.hapusSemua();
			}
		}
		if (semua || "ekonomi".equals(bagian)) {
			hapus(KotaEkonomiData.get(server), situs);
		}
		if (semua || "acara".equals(bagian)) {
			hapus(KotaAcaraData.get(server), situs);
		}
		if (semua || "sekte".equals(bagian)) {
			hapus(KotaSekteData.get(server), situs);
		}
		if (semua || "tumbuh".equals(bagian)) {
			hapus(KotaTumbuhData.get(server), situs);
		}
		if (semua || "ragam".equals(bagian)) {
			hapus(RagamData.get(server), situs);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.reset", bagian, situs), true);
		return 1;
	}

	private static void hapus(KotaBagianData data, long situs) {
		if (situs != 0L) {
			data.hapusSitus(situs);
		} else {
			data.hapusSemua();
		}
	}

	private KotaUji() {
	}
}

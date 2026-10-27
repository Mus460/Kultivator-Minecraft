package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Test commands of 42K (DESIGN 42.5A.12, 42.14.3): {@code /kultivasi uji kota_hidup cakap ...} and {@code ... gosip ...}.
 * The townsperson is the stored warga behind the nearest civilian body within 8 blocks (the second nearest for
 * {@code obrol}); the place is the tester's ({@code KotaHidupCommands.tempatPemain}). Output keys
 * {@code kultivator.kota42.cakap.uji.*} (fragment {@code kota_hidup_cakap_mesin}). {@code cakap golden [tulis]} runs the
 * realisation golden files of 42K-T ({@link Realisasi#ujiGolden}).
 */
public final class CakapUji {

	private static final String K = "kultivator.kota42.cakap.uji.";

	static void init() {
		KotaUji.daftar("cakap", CakapUji::cakap);
		KotaUji.daftar("gosip", CakapUji::gosip);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> cakap() {
		return Commands.literal("cakap")
				.then(Commands.literal("info").executes(CakapUji::info))
				.then(Commands.literal("fakta").executes(CakapUji::fakta))
				.then(Commands.literal("emosi").then(Commands.argument("peristiwa", StringArgumentType.word()).executes(CakapUji::emosi)))
				.then(Commands.literal("uji").then(Commands.argument("niat", StringArgumentType.word()).executes(ctx -> uji(ctx, 3))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(ctx -> uji(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("kombinasi").executes(ctx -> kombinasi(ctx, "SAPA"))
						.then(Commands.argument("niat", StringArgumentType.word()).executes(ctx -> kombinasi(ctx, StringArgumentType.getString(ctx, "niat")))))
				.then(Commands.literal("obrol").executes(ctx -> obrol(ctx, null))
						.then(Commands.argument("jenis", StringArgumentType.word()).executes(ctx -> obrol(ctx, StringArgumentType.getString(ctx, "jenis")))))
				.then(Commands.literal("jejak").executes(CakapUji::jejak))
				.then(Commands.literal("golden").executes(ctx -> golden(ctx, false))
						.then(Commands.literal("tulis").executes(ctx -> golden(ctx, true))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> gosip() {
		return Commands.literal("gosip")
				.then(Commands.literal("daftar").executes(CakapUji::gosipDaftar))
				.then(Commands.literal("buat").then(Commands.argument("templat", StringArgumentType.word()).executes(CakapUji::gosipBuat)))
				.then(Commands.literal("sebar").then(Commands.argument("hari", IntegerArgumentType.integer(1, 30)).executes(CakapUji::gosipSebar)))
				.then(Commands.literal("jejak").then(Commands.argument("id", IntegerArgumentType.integer(0)).executes(CakapUji::gosipJejak)));
	}

	// ================================================================== helpers

	private static List<KultivatorNpc> tubuh(ServerPlayer player, int n) {
		List<KultivatorNpc> list = new ArrayList<>(player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D),
				npc -> npc.isAlive() && WargaApi.dari(npc) != null));
		list.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		return list.size() > n ? list.subList(0, n) : list;
	}

	private static @Nullable Warga warga(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<KultivatorNpc> near = tubuh(player, 1);
		if (near.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable(K + "tanpa_warga"));
			return null;
		}
		return WargaApi.dari(near.get(0));
	}

	private static void baris(CommandSourceStack source, Component c) {
		source.sendSuccess(() -> c, false);
	}

	private static long situs(MinecraftServer server, Warga w) {
		return WargaData.get(server).situsDari(w.id);
	}

	// ================================================================== cakap

	/** {@code cakap info}: traits, feeling, register, dialect and the five best topics for the tester. */
	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Pengetahuan pk = Pengetahuan.get(server);
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, situs(server, w), null);
		CommandSourceStack src = ctx.getSource();
		baris(src, Component.translatable(K + "info.judul", WargaApi.nama(w), s.watak(), String.join(",", s.kebiasaan())).withStyle(ChatFormatting.GOLD));
		baris(src, Component.translatable(K + "info.sifat", s.sifat().toString()));
		baris(src, Component.translatable(K + "info.emosi", String.format(Locale.ROOT, "%.2f %.2f %.2f", s.p(), s.a(), s.d()), s.label(),
				Emosi.intensitas(s.pad()), s.wajah()));
		baris(src, Component.translatable(K + "info.gaya", s.register(), s.dialek(), s.kesan(), s.jam(), s.cuaca(), s.musim()));
		int idx = WargaData.get(server).indeksPemain(player.getUUID());
		PerencanaTopik.Pendengar d = new PerencanaTopik.Pendengar(pk.riwayat(w, idx), WargaApi.ingatan(w, player.getUUID()), null, Map.of(),
				List.of(), List.of(), false);
		List<PerencanaTopik.Kandidat> list = PerencanaTopik.kandidat(s, pk, d);
		for (int i = 0; i < list.size() && i < 5; i++) {
			PerencanaTopik.Kandidat k = list.get(i);
			baris(src, Component.translatable(K + "info.topik", k.topik(), String.format(Locale.ROOT, "%.3f", k.u()),
					k.fakta() == null ? "-" : k.fakta().predikat()));
		}
		baris(src, Component.translatable(K + "info.tata", Giliran.adaTataBahasa() ? "ya" : "cadangan"));
		return 1;
	}

	/** {@code cakap fakta}: every remembered fact with recall, confidence, source and gossip id. */
	private static int fakta(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		long hari = KotaHidup.hari(server);
		List<Fakta> list = Cakap.fakta(w);
		baris(ctx.getSource(), Component.translatable(K + "fakta.judul", WargaApi.nama(w), list.size()).withStyle(ChatFormatting.GOLD));
		for (Fakta f : list) {
			baris(ctx.getSource(), Component.translatable(K + "fakta.baris", f.predikat(), rujuk(f.subjek()), rujuk(f.objek()), f.nilai(),
					f.kebenaran().name(), f.yakin(), f.asal().sumber().name() + (f.asal().dari() >= 0 ? ":" + f.asal().dari() : ""),
					String.format(Locale.ROOT, "%.2f", Lupa.ingat(f, hari)), f.gosipId()));
		}
		return Math.max(1, list.size());
	}

	private static String rujuk(Fakta.Rujuk r) {
		return r.jenis() == Fakta.Jenis.TIADA ? "-" : r.jenis().id() + ":" + (r.kunci().isEmpty() ? Long.toString(r.id()) : r.kunci());
	}

	/** {@code cakap emosi <peristiwa>}: applies an appraisal event and shows PAD before and after. */
	private static int emosi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		String jenis = StringArgumentType.getString(ctx, "peristiwa");
		MinecraftServer server = ctx.getSource().getServer();
		long now = server.overworld().getGameTime();
		Pengetahuan pk = Pengetahuan.get(server);
		float[] before = pk.pad(w, now);
		if (Cakap.peristiwaData(jenis) == null) {
			ctx.getSource().sendFailure(Component.translatable(K + "emosi.tanpa", jenis));
			return 0;
		}
		int idx = WargaData.get(server).indeksPemain(ctx.getSource().getPlayerOrException().getUUID());
		Cakap.peristiwa(w, jenis, Fakta.Rujuk.pemain(idx), 1.0F);
		float[] after = pk.pad(w, now);
		baris(ctx.getSource(), Component.translatable(K + "emosi.hasil", jenis, pad(before), Emosi.label(before).id(), pad(after),
				Emosi.label(after).id()));
		return 1;
	}

	private static String pad(float[] v) {
		return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v[0], v[1], v[2]);
	}

	/** {@code cakap uji <niat> [n]}: n utterances of an intent by the nearest townsperson (different seeds). */
	private static int uji(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Niat niat = new Niat(StringArgumentType.getString(ctx, "niat").toUpperCase(Locale.ROOT));
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, situs(server, w), null);
		baris(ctx.getSource(), Component.translatable(K + "uji.judul", WargaApi.nama(w), niat.id(), s.register(), s.dialek()).withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < n; i++) {
			SplittableRandom r = Cakap.acak(server.overworld().getSeed(), w.id, 0, KotaHidup.hari(server), i, "uji");
			Ujaran u = Giliran.ujaran(new Giliran.Permintaan(niat, null, s, null, Map.of(), player, null, r));
			int nomor = i + 1;
			baris(ctx.getSource(), Component.translatable(K + "uji.baris", nomor, u.komponen(), u.akar().kunci()));
		}
		return n;
	}

	/** {@code cakap kombinasi [niat]}: distinct realisations among 2000 seeds (a lower bound of the variety, 42.5A.8). */
	private static int kombinasi(CommandContext<CommandSourceStack> ctx, String niatId) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Niat niat = new Niat(niatId.toUpperCase(Locale.ROOT));
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, situs(server, w), null);
		Set<String> seen = new HashSet<>();
		Set<String> frames = new HashSet<>();
		int runs = 2000;
		for (int i = 0; i < runs; i++) {
			SplittableRandom r = new SplittableRandom(Cakap.benih(server.overworld().getSeed(), w.id, i, 0L, i, "kombinasi"));
			Ujaran u = Giliran.ujaran(new Giliran.Permintaan(niat, null, s, null, Map.of(), player, null, r));
			seen.add(pohon(u.akar()));
			frames.add(u.akar().kunci());
		}
		baris(ctx.getSource(), Component.translatable(K + "kombinasi", niat.id(), runs, seen.size(), frames.size()));
		return seen.size();
	}

	/** A stable text form of an utterance tree (keys and literal leaves). */
	static String pohon(Ujaran.Simpul s) {
		if (s.teks() != null) {
			return "'" + s.teks().getString() + "'";
		}
		StringBuilder b = new StringBuilder(s.kunci());
		if (!s.arg().isEmpty()) {
			b.append('(');
			for (int i = 0; i < s.arg().size(); i++) {
				if (i > 0) {
					b.append(',');
				}
				b.append(pohon(s.arg().get(i)));
			}
			b.append(')');
		}
		return b.toString();
	}

	/** {@code cakap obrol [jenis]}: the two nearest townsfolk chat (text shown here, effects applied). */
	private static int obrol(CommandContext<CommandSourceStack> ctx, @Nullable String jenisId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<KultivatorNpc> near = tubuh(player, 2);
		if (near.size() < 2) {
			ctx.getSource().sendFailure(Component.translatable(K + "tanpa_dua"));
			return 0;
		}
		Warga a = WargaApi.dari(near.get(0));
		Warga b = WargaApi.dari(near.get(1));
		Cakap.JenisObrolan jenis = jenisId == null ? null : Cakap.JenisObrolan.dari(jenisId.toLowerCase(Locale.ROOT));
		if (a == null || b == null || (jenisId != null && jenis == null)) {
			ctx.getSource().sendFailure(Component.translatable(K + "tanpa_dua"));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		Cakap.JenisObrolan run = jenis != null ? jenis
				: ObrolanWarga.pilihJenis(server, Pengetahuan.get(server), a, b, situs(server, a),
						Cakap.acak(server.overworld().getSeed(), a.id, b.id, KotaHidup.hari(server), 0, "uji_obrol"));
		baris(ctx.getSource(), Component.translatable(K + "obrol.judul", WargaApi.nama(a), WargaApi.nama(b), run.id()).withStyle(ChatFormatting.GOLD));
		for (Ujaran u : ObrolanWarga.naskahTeks(server, a, b, run)) {
			baris(ctx.getSource(), Component.translatable(K + "obrol.baris", u.niat().id(), u.komponen()));
		}
		Cakap.obrol(a, b, run);
		return 1;
	}

	/** {@code cakap jejak}: the last 16 NPC-NPC chats (who, kind, steps, shown or not). */
	private static int jejak(CommandContext<CommandSourceStack> ctx) {
		List<String> list = ObrolanWarga.jejak();
		baris(ctx.getSource(), Component.translatable(K + "jejak.judul", list.size()).withStyle(ChatFormatting.GOLD));
		for (String s : list) {
			baris(ctx.getSource(), Component.literal(s));
		}
		return Math.max(1, list.size());
	}

	// ================================================================== gosip

	private static long tempat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var t = KotaHidupCommands.tempatPemain(ctx.getSource().getPlayerOrException());
		if (t == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0L;
		}
		return t.situsId();
	}

	/** {@code gosip daftar}: live gossips of the tester's place (heat, truth, holders). */
	private static int gosipDaftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		long situs = tempat(ctx);
		if (situs == 0L) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		List<GosipWarga.Gosip> list = GosipWarga.daftar(server, situs);
		baris(ctx.getSource(), Component.translatable(K + "gosip.judul", situs, list.size()).withStyle(ChatFormatting.GOLD));
		List<Warga> warga = WargaApi.wargaTempat(situs);
		for (GosipWarga.Gosip g : list) {
			int holders = 0;
			for (Warga w : warga) {
				if (GosipWarga.tahu(w, g.id())) {
					holders++;
				}
			}
			baris(ctx.getSource(), Component.translatable(K + "gosip.baris", g.id(), g.templat(), g.panas(), g.benar().name(), holders,
					KotaHidup.hari(server) - g.lahir()));
		}
		return Math.max(1, list.size());
	}

	/** {@code gosip buat <templat>}: a gossip about the tester born at the place. */
	private static int gosipBuat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		long situs = tempat(ctx);
		if (situs == 0L) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String templat = StringArgumentType.getString(ctx, "templat").toUpperCase(Locale.ROOT);
		int idx = WargaData.get(ctx.getSource().getServer()).indeksPemain(player.getUUID());
		int id = GosipWarga.lahir(ctx.getSource().getServer().overworld(), situs, templat, Fakta.Rujuk.pemain(idx));
		baris(ctx.getSource(), Component.translatable(K + "gosip.buat", templat, id));
		return id >= 0 ? 1 : 0;
	}

	/** {@code gosip sebar <hari>}: runs the daily gossip pass of the place n times (cooling once per real day). */
	private static int gosipSebar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		long situs = tempat(ctx);
		if (situs == 0L) {
			return 0;
		}
		int n = IntegerArgumentType.getInteger(ctx, "hari");
		for (int i = 0; i < n; i++) {
			Lupa.harian(ctx.getSource().getServer().overworld(), situs);
		}
		return gosipDaftar(ctx);
	}

	/** {@code gosip jejak <id>}: who told the nearest townsperson, back to the source. */
	private static int gosipJejak(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Warga w = warga(ctx);
		if (w == null) {
			return 0;
		}
		int id = IntegerArgumentType.getInteger(ctx, "id");
		List<Integer> chain = GosipWarga.rantai(ctx.getSource().getServer(), w, id);
		baris(ctx.getSource(), Component.translatable(K + "gosip.jejak", id, WargaApi.nama(w), chain.size()).withStyle(ChatFormatting.GOLD));
		for (int teller : chain) {
			Warga t = teller >= 0 ? WargaApi.byId(teller) : null;
			baris(ctx.getSource(), t != null ? Component.translatable(K + "gosip.jejak_warga", WargaApi.nama(t))
					: Component.translatable(K + "gosip.jejak_pemain", -1 - teller));
		}
		return 1;
	}

	// ================================================================== golden files

	/**
	 * {@code cakap golden [tulis]}: every realisation golden file ({@code "jenis": "realisasi"} under
	 * {@code percakapan/golden}) against the live grammar, seeds and language tables; a difference prints the step, the key and
	 * both values. {@code tulis} never touches the jar: it writes the freshly computed expectations to
	 * {@code <server dir>/kultivator_golden/<id>.json} for the author to copy into the data folder.
	 */
	private static int golden(CommandContext<CommandSourceStack> ctx, boolean tulis) {
		CommandSourceStack src = ctx.getSource();
		Map<String, String> id = Ujaran.bahasa("id_id");
		Map<String, String> en = Ujaran.bahasa("en_us");
		TataBahasa.Tabel tabel = TataBahasa.tabel();
		int total = 0;
		int lulus = 0;
		int ditulis = 0;
		List<String> beda = new ArrayList<>();
		java.nio.file.Path keluar = src.getServer().getServerDirectory().resolve("kultivator_golden");
		for (Map.Entry<String, JsonObject> e : CakapReload.mentahAwalan("golden/").entrySet()) {
			JsonObject g = e.getValue();
			if (!"realisasi".equals(TataBahasa.teks(g, "jenis", ""))) {
				continue;
			}
			total++;
			JsonObject kerja = g.deepCopy();
			List<String> d = Realisasi.ujiGolden(kerja, tabel, id, en, tulis);
			if (tulis) {
				try {
					java.nio.file.Files.createDirectories(keluar);
					String name = e.getKey().substring(e.getKey().lastIndexOf('/') + 1) + ".json";
					java.nio.file.Files.writeString(keluar.resolve(name), new com.google.gson.GsonBuilder().setPrettyPrinting()
							.disableHtmlEscaping().create().toJson(kerja) + "\n", java.nio.charset.StandardCharsets.UTF_8);
					ditulis++;
				} catch (java.io.IOException exception) {
					beda.add(e.getKey() + ": " + exception.getMessage());
				}
			}
			if (d.isEmpty()) {
				lulus++;
			} else {
				beda.addAll(d);
			}
		}
		if (total == 0) {
			src.sendFailure(Component.translatable(K + "golden.kosong"));
			return 0;
		}
		if (tulis) {
			baris(src, Component.translatable(K + "golden.tulis", ditulis, keluar.toString()).withStyle(ChatFormatting.GOLD));
			return ditulis;
		}
		baris(src, Component.translatable(K + "golden.hasil", lulus, total).withStyle(lulus == total ? ChatFormatting.GREEN : ChatFormatting.RED));
		for (int i = 0; i < beda.size() && i < 12; i++) {
			baris(src, Component.translatable(K + "golden.beda", beda.get(i)));
		}
		return lulus;
	}

	private CakapUji() {
	}
}

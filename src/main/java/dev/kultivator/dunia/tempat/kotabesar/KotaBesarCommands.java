package dev.kultivator.dunia.tempat.kotabesar;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.cerita.AlurTakhta;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The capital's test commands {@code /kultivasi uji kota_besar ...} (op, DESIGN 38.D.10; chips listed by 38W in
 * {@code DuniaCommands.CHIPS}) and the player command {@code /kultivasi denda} (the record at the capital one stands in,
 * read only: fines are paid at the Yamen or to a guard). Every line is translated; numbers are the live formulas.
 */
final class KotaBesarCommands {

	static void init() {
		DuniaCommands.test("kota_besar", KotaBesarCommands::node);
		DuniaCommands.player("denda", () -> Commands.literal("denda").executes(ctx -> denda(ctx.getSource().getPlayerOrException())));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("kota_besar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> cari(ctx.getSource(), -1))
				.then(Commands.literal("cari")
						.executes(ctx -> cari(ctx.getSource(), -1))
						.then(Commands.argument("indeks", IntegerArgumentType.integer(0, 4096))
								.executes(ctx -> cari(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "indeks")))))
				.then(Commands.literal("distrik")
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(RencanaKotaBesar.PETAK.stream().map(RencanaKotaBesar.Petak::id), b))
								.executes(ctx -> distrik(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("lot").executes(ctx -> lot(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("jam_malam")
						.executes(ctx -> jamMalam(ctx.getSource().getPlayerOrException(), ""))
						.then(Commands.literal("tutup").executes(ctx -> jamMalam(ctx.getSource().getPlayerOrException(), "tutup")))
						.then(Commands.literal("buka").executes(ctx -> jamMalam(ctx.getSource().getPlayerOrException(), "buka"))))
				.then(Commands.literal("hukum")
						.executes(ctx -> hukum(ctx.getSource().getPlayerOrException(), -1))
						.then(Commands.argument("poin", IntegerArgumentType.integer(0, KotaBesarData.KEJAHATAN_MAKS))
								.executes(ctx -> hukum(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "poin")))))
				.then(Commands.literal("sel").executes(ctx -> sel(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("acara")
						.executes(ctx -> acaraDaftar(ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("acara", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(alias(), b))
								.executes(ctx -> acara(ctx, "mulai"))
								.then(Commands.literal("mulai").executes(ctx -> acara(ctx, "mulai")))
								.then(Commands.literal("akhir").executes(ctx -> acara(ctx, "akhir")))))
				.then(Commands.literal("alur")
						.executes(ctx -> alur(ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("bab", IntegerArgumentType.integer(1, AlurTakhta.BAB_TERAKHIR))
								.executes(ctx -> alur(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("teleport")
						.then(Commands.argument("tujuan", StringArgumentType.greedyString())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Teleport.JENIS, b))
								.executes(ctx -> teleport(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "tujuan")))))
				.then(Commands.literal("istana").executes(ctx -> istana(ctx.getSource().getPlayerOrException())));
	}

	// ================================================================== helpers

	private static void kirim(CommandSourceStack src, Component line) {
		src.sendSuccess(() -> line, false);
	}

	private static List<String> alias() {
		return List.of("festival", "pil", "lelang", "gerbang", "turnamen", "pertahanan", "pertemuan", "pasar_malam");
	}

	private static @Nullable String idAcara(String alias) {
		return switch (alias) {
			case "festival" -> LembarKunci.Acara.ACARA_FESTIVAL;
			case "pil" -> LembarKunci.Acara.ACARA_PERTEMUAN_PIL;
			case "lelang" -> LembarKunci.Acara.ACARA_LELANG_AGUNG;
			case "gerbang" -> LembarKunci.Acara.ACARA_HARI_GERBANG;
			case "turnamen" -> LembarKunci.Acara.ACARA_TURNAMEN;
			case "pertahanan" -> LembarKunci.Acara.ACARA_PERTAHANAN;
			case "pertemuan" -> LembarKunci.Acara.ACARA_PERTEMUAN_SEKTE;
			case "pasar_malam" -> LembarKunci.Acara.ACARA_PASAR_MALAM;
			default -> KotaBesarKalender.acara(alias) != null ? alias : null;
		};
	}

	/** The capital the player stands in, else the nearest born one; null when there is none near. */
	private static @Nullable Kandidat kota(ServerPlayer p) {
		ServerLevel level = p.level();
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		Kandidat here = KotaBesarKota.di(level, p.blockPosition());
		if (here != null) {
			return here;
		}
		Kandidat best = null;
		double bestSq = Double.MAX_VALUE;
		for (Kandidat k : RencanaKotaBesar.of(level.getSeed()).kandidat()) {
			double d = Math.pow(k.pusatX() - p.getX(), 2) + Math.pow(k.pusatZ() - p.getZ(), 2);
			if (d < bestSq && d < 6000.0D * 6000.0D && KotaBesarKota.lahir(level, k)) {
				bestSq = d;
				best = k;
			}
		}
		return best;
	}

	private static int tanpaKota(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.tanpa_kota").withStyle(ChatFormatting.RED));
		return 0;
	}

	private static void pergi(ServerPlayer p, ServerLevel level, BlockPos at) {
		DuniaTickets.touch(level, ChunkPos.containing(at), 1);
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		JamMalam.abaikan(p, 400L);
		p.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
	}

	// ================================================================== nodes

	private static int cari(CommandSourceStack src, int indeks) throws CommandSyntaxException {
		MinecraftServer server = src.getServer();
		ServerLevel level = server.overworld();
		List<Kandidat> born = new ArrayList<>();
		for (Kandidat k : RencanaKotaBesar.of(level.getSeed()).kandidat()) {
			if (Math.hypot(k.pusatX(), k.pusatZ()) <= 12000.0D && KotaBesarKota.lahir(level, k)) {
				born.add(k);
			}
		}
		if (indeks < 0) {
			kirim(src, Component.translatable("kultivator.kota_besar.uji.daftar", born.size()).withStyle(ChatFormatting.GOLD));
			for (Kandidat k : born) {
				Putusan put = KotaBesarKota.putusan(level, k);
				kirim(src, Component.translatable("kultivator.kota_besar.uji.baris", k.index(), KotaBesarKota.nama(server, k), k.pusatX(), put.dasarY(),
						k.pusatZ(), k.putar() * 90, k.pertama() ? "*" : "", Long.toString(KotaBesarKota.situs(k))));
			}
			if (born.isEmpty()) {
				// Why not: the first ring's verdicts (terrain reason, or the ring rule).
				for (Kandidat k : RencanaKotaBesar.of(level.getSeed()).kandidat()) {
					if (!k.pertama()) {
						break;
					}
					kirim(src, Component.translatable("kultivator.kota_besar.uji.ditolak", k.index(), k.pusatX(), k.pusatZ(),
							KotaBesarKota.putusan(level, k).alasan()).withStyle(ChatFormatting.GRAY));
				}
			}
			return born.size();
		}
		ServerPlayer p = src.getPlayerOrException();
		for (Kandidat k : born) {
			if (k.index() == indeks) {
				BlockPos at = KotaBesarKota.titik(level, k, 0, 20, 1);
				pergi(p, level, at);
				kirim(src, Component.translatable("kultivator.kota_besar.uji.tiba", KotaBesarKota.nama(server, k)).withStyle(ChatFormatting.GREEN));
				return 1;
			}
		}
		kirim(src, Component.translatable("kultivator.kota_besar.uji.indeks_tidak_ada", indeks).withStyle(ChatFormatting.RED));
		return 0;
	}

	private static int distrik(ServerPlayer p, String id) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		BlockPos centre = KotaBesarKota.pusatDistrik(p.level(), k, id);
		pergi(p, p.level(), centre.above());
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.distrik", Component.translatable("kultivator.kota_besar.distrik." + id))
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int lot(ServerPlayer p) {
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		if (k == null) {
			return tanpaKota(p);
		}
		int[] l = KotaBesarKota.lokal(k, p.getBlockX(), p.getBlockZ());
		String d = KotaBesarKota.distrik(k, p.blockPosition());
		int cellX = Math.floorDiv(Math.floorMod(l[0] + 72, 144), 48);
		int cellZ = Math.floorDiv(Math.floorMod(l[1] + 72, 144), 48);
		long town = KotaBesarKota.situs(k);
		int nb = KotaData.get(p.level().getServer()).reputation(town, p.getUUID());
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.lot", KotaBesarKota.nama(p.level().getServer(), k),
				d == null ? "-" : d, l[0], l[1], cellX, cellZ, KotaBesarKota.dalamTembok(k, p.blockPosition()) ? "+" : "-",
				Long.toString(town), nb, Component.translatable("kultivator.kota_besar.pangkat." + KotaBesarApi.pangkatId(KotaBesarApi.pangkat(nb)))));
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		if (kota != null) {
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.hidup", kota.gerbang.size(), kota.sel.size(), kota.layanan.size(),
					kota.panggung.size(), kota.cakram.size(), kota.dermaga.size(), KotaBesarAnchors.tubuhHidup(town)).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int jamMalam(ServerPlayer p, String mode) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		long town = KotaBesarKota.situs(k);
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		int n = 0;
		if (kota != null) {
			for (GerbangKotaEntity g : kota.gerbang) {
				if ("tutup".equals(mode)) {
					g.setBuka(false);
				} else if ("buka".equals(mode)) {
					g.bukaSampai(p.level().getGameTime() + 2400L);
				}
				n++;
			}
		}
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.jam_malam", n, JamMalam.malam(p.level().getServer())
				? Component.translatable("kultivator.kota_besar.uji.malam") : Component.translatable("kultivator.kota_besar.uji.siang"),
				KotaBesarKalender.jam(JamMalam.GENDERANG), KotaBesarKalender.jam(JamMalam.BUKA)).withStyle(ChatFormatting.GOLD));
		return n;
	}

	private static int hukum(ServerPlayer p, int poin) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		long town = KotaBesarKota.situs(k);
		MinecraftServer server = p.level().getServer();
		if (poin >= 0) {
			KotaBesarData.get(server).setKejahatan(town, p.getUUID(), poin);
			KotaBesarTampil.ingatNama(p, town);
			KotaBesarTampil.poster(server, town);
		}
		int now = Hukum.poin(server, town, p.getUUID());
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.hukum", now,
				Component.translatable("kultivator.kota_besar.hukum.status." + Hukum.status(now)), dev.kultivator.dunia.tempat.ekonomi.Barang.nb(Hukum.denda(p, town)),
				Math.min(Hukum.SEL_MAKS, now * Hukum.SEL_PER_POIN) / 20L).withStyle(ChatFormatting.GOLD));
		return now;
	}

	private static int sel(ServerPlayer p) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		long town = KotaBesarKota.situs(k);
		MinecraftServer server = p.level().getServer();
		if (Hukum.poin(server, town, p.getUUID()) <= 0) {
			KotaBesarData.get(server).setKejahatan(town, p.getUUID(), 5);
		}
		boolean ok = Hukum.serahkanDiri(p, town);
		return ok ? 1 : 0;
	}

	private static int acaraDaftar(ServerPlayer p) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		MinecraftServer server = p.level().getServer();
		long town = KotaBesarKota.situs(k);
		long day = dev.kultivator.dunia.kota.Kota.day(server);
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.acara_judul", KotaBesarKota.nama(server, k), day).withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < 14; i++) {
			String id = KotaBesarKalender.jadwal(server, town, day + i);
			if (id != null) {
				KotaBesarKalender.Acara a = KotaBesarKalender.acara(id);
				p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.acara_baris", day + i,
						Component.translatable("kultivator.kota_besar.acara." + id), KotaBesarKalender.jam(a.mulaiJam()), KotaBesarKalender.jam(a.akhirJam())));
			}
		}
		String now = KotaBesarKalender.aktif(town);
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.acara_kini", now == null
				? Component.translatable("kultivator.kota_besar.acara.tidak_ada") : Component.translatable("kultivator.kota_besar.acara." + now)));
		return 1;
	}

	private static int acara(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		String id = idAcara(StringArgumentType.getString(ctx, "acara"));
		if (id == null) {
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.acara_tak_dikenal").withStyle(ChatFormatting.RED));
			return 0;
		}
		boolean ok = "akhir".equals(mode) ? KotaBesarKalender.paksaAkhir(p.level(), k, id) : KotaBesarKalender.paksaMulai(p.level(), k, id);
		p.sendSystemMessage(Component.translatable(ok ? "kultivator.kota_besar.uji.acara_ok." + mode : "kultivator.kota_besar.uji.acara_gagal",
				Component.translatable("kultivator.kota_besar.acara." + id)).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
		return ok ? 1 : 0;
	}

	private static int alur(ServerPlayer p) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		MinecraftServer server = p.level().getServer();
		long town = KotaBesarKota.situs(k);
		String ruler = AlurTakhta.penguasa(server, town);
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.alur", AlurTakhta.bab(server, town), AlurTakhta.BAB_TERAKHIR,
				ruler.isEmpty() ? "-" : ruler, AlurTakhta.sisaHari(server, town)).withStyle(ChatFormatting.GOLD));
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.alur_lompat").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int teleport(ServerPlayer p, String tujuan) {
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.all(p.level().getServer())) {
			boolean match = site.kind().equals(tujuan) || Long.toString(site.id()).equals(tujuan);
			if (!match || site.dim() != p.level().dimension()) {
				continue;
			}
			double d = site.pos().distSqr(p.blockPosition());
			if (d < bestSq) {
				bestSq = d;
				best = site;
			}
		}
		if (best == null) {
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.tujuan_tidak_ada", tujuan).withStyle(ChatFormatting.RED));
			return 0;
		}
		DuniaSites.discover(p, best.id());
		boolean ok = Teleport.langsung(p, best);
		p.sendSystemMessage(Component.translatable(ok ? "kultivator.kota_besar.uji.teleport_ok" : "kultivator.kota_besar.teleport.tak_ada_titik",
				Teleport.namaSitus(p.level().getServer(), best), Teleport.tarifKe(p, best)).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
		return ok ? 1 : 0;
	}

	private static int istana(ServerPlayer p) {
		Kandidat k = kota(p);
		if (k == null) {
			return tanpaKota(p);
		}
		MinecraftServer server = p.level().getServer();
		long town = KotaBesarKota.situs(k);
		KotaData.get(server).setReputation(town, p.getUUID(), Math.max(KotaBesarApi.NB_PAHLAWAN, KotaData.get(server).reputation(town, p.getUUID())));
		KotaBesarData.get(server).beriGelar(p.getUUID(), "tamu_istana");
		BlockPos hall = KotaBesarKota.pusatDistrik(p.level(), k, "istana");
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		if (kota != null && kota.layanan("audiensi") != null) {
			hall = kota.layanan("audiensi").blockPosition().offset(0, 0, 3);
		}
		pergi(p, p.level(), hall);
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.uji.istana", KotaBesarNames.pangeran(server.overworld().getSeed(), k.index()),
				Istana.imbalan(dev.kultivator.dunia.tempat.ekonomi.Barang.pita(p))).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code /kultivasi denda}: the record at the capital the player stands in (read only). */
	private static int denda(ServerPlayer p) {
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		if (k == null) {
			return tanpaKota(p);
		}
		long town = KotaBesarKota.situs(k);
		MinecraftServer server = p.level().getServer();
		int poin = Hukum.poin(server, town, p.getUUID());
		CompoundTag tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_PUSAT);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.yamen.judul"));
		tag.putInt(LembarKunci.Yamen.POIN, poin);
		tag.putString(LembarKunci.STATUS, Hukum.status(poin));
		if (poin > 0) {
			tag.putLong(LembarKunci.Yamen.DENDA, Hukum.denda(p, town));
		}
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.yamen.jarak_jauh"));
		TempatLayar.buka(p, LembarKunci.Yamen.LAYAR, -1, tag);
		return 1;
	}

	private KotaBesarCommands() {
	}
}

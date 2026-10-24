package dev.kultivator.dunia.tempat.tema.iblis;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaCommands;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Commands of the Kota Iblis (DESIGN 38.T.13.2). Test words under {@code /kultivasi uji tema} (op, inti's root through
 * {@link TemaCommands#daftar}); none of them touches a block, the weather or the time:
 * <pre>
 * arena &lt;peringkat&gt;          sets the caller's ladder rank in this town and starts a bout now
 * taruhan                    tonight's twelve bouts of the betting card with odds and the (hidden) winner
 * infami &lt;0-100&gt;             sets the caller's Infami              gentar &lt;0-100&gt;   Gentar in this (or the nearest) town
 * buronan [daftar|buat]      the fugitives of the nearest Kota Iblis / one of them, by the caller, as his bounty
 * pemburu [paksa]            why a righteous squad could or could not come now / a squad now (no day or pressure check)
 * iblis alur &lt;tahap&gt;         jumps the chain tema_iblis_rantai to a stage    iblis rute &lt;lurus|penebusan|darah|-&gt;
 * iblis pesta mulai|akhir    the Blood Feast      iblis serbu [mulai]   siege status / the chapter-5 siege now
 * iblis berburu              an ambush of the hunting hour now     iblis lelang  a black auction now at the hall
 * iblis menara | kontrak | tawanan | kas | kota   status lines       iblis reset   clears this town's story flags
 * </pre>
 * The player's answer to a Jam Berburu toll is {@code /kultivasi tema_iblis bayar|lawan} (no op; the clickable chat
 * line of {@link JamBerburu}).
 */
final class IblisCommands {

	private IblisCommands() {
	}

	static void init() {
		TemaCommands.daftar(IblisData.TEMA, IblisCommands::arena);
		TemaCommands.daftar(IblisData.TEMA, () -> Commands.literal("taruhan").executes(ctx -> taruhan(ctx.getSource())));
		TemaCommands.daftar(IblisData.TEMA, () -> Commands.literal("infami")
				.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(ctx -> infami(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nilai")))));
		TemaCommands.daftar(IblisData.TEMA, () -> Commands.literal("gentar")
				.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(ctx -> gentar(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nilai")))));
		TemaCommands.daftar(IblisData.TEMA, () -> Commands.literal("buronan")
				.executes(ctx -> buronan(ctx.getSource(), false))
				.then(Commands.literal("daftar").executes(ctx -> buronan(ctx.getSource(), false)))
				.then(Commands.literal("buat").executes(ctx -> buronan(ctx.getSource(), true))));
		TemaCommands.daftar(IblisData.TEMA, () -> Commands.literal("pemburu")
				.executes(ctx -> pemburu(ctx.getSource(), false))
				.then(Commands.literal("paksa").executes(ctx -> pemburu(ctx.getSource(), true))));
		TemaCommands.daftar(IblisData.TEMA, IblisCommands::iblis);
		DuniaCommands.player("tema_iblis", () -> Commands.literal("tema_iblis")
				.then(Commands.literal("bayar").executes(ctx -> jawab(ctx.getSource(), true)))
				.then(Commands.literal("lawan").executes(ctx -> jawab(ctx.getSource(), false))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> arena() {
		return Commands.literal("arena")
				.then(Commands.argument("peringkat", IntegerArgumentType.integer(1, ArenaDarah.PERINGKAT_MAKS))
						.executes(ctx -> arenaUji(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "peringkat"))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> iblis() {
		return Commands.literal("iblis")
				.executes(ctx -> kota(ctx.getSource()))
				.then(Commands.literal("alur").then(Commands.argument("tahap", StringArgumentType.word())
						.executes(ctx -> alur(ctx.getSource(), StringArgumentType.getString(ctx, "tahap")))))
				.then(Commands.literal("rute").then(Commands.argument("rute", StringArgumentType.word())
						.executes(ctx -> rute(ctx.getSource(), StringArgumentType.getString(ctx, "rute")))))
				.then(Commands.literal("pesta")
						.then(Commands.literal("mulai").executes(ctx -> pesta(ctx.getSource(), true)))
						.then(Commands.literal("akhir").executes(ctx -> pesta(ctx.getSource(), false))))
				.then(Commands.literal("serbu")
						.executes(ctx -> serbu(ctx.getSource(), false))
						.then(Commands.literal("mulai").executes(ctx -> serbu(ctx.getSource(), true))))
				.then(Commands.literal("berburu").executes(ctx -> berburu(ctx.getSource())))
				.then(Commands.literal("lelang").executes(ctx -> lelang(ctx.getSource())))
				.then(Commands.literal("menara").executes(ctx -> baris(ctx.getSource(), MenaraTerlarang.status(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("kontrak").executes(ctx -> baris(ctx.getSource(), SarangKontrak.status(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("tawanan").executes(ctx -> tawanan(ctx.getSource())))
				.then(Commands.literal("kas").executes(ctx -> kota(ctx.getSource())))
				.then(Commands.literal("kota").executes(ctx -> kota(ctx.getSource())))
				.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())));
	}

	// ================================================================== handlers

	private static TemaKota.Kota kotaAtauGagal(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			kota = IblisKota.terdekat(p.level(), p.blockPosition(), 600);
		}
		if (kota == null) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.iblis.cmd.tidak_ada"));
		}
		return kota;
	}

	private static int arenaUji(CommandSourceStack source, int rank) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.iblis.cmd.di_kota"));
			return 0;
		}
		IblisData.pemainKota(p.level().getServer(), kota.situsId(), p.getUUID()).putInt("peringkat", rank);
		IblisData.ubah(p.level().getServer());
		Component no = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_TANGGA, -1);
		if (no != null) {
			source.sendFailure(no);
			return 0;
		}
		return 1;
	}

	private static int taruhan(CommandSourceStack source) throws CommandSyntaxException {
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		return baris(source, Taruhan.kartu(source.getLevel(), kota.situsId()));
	}

	private static int infami(CommandSourceStack source, int value) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaApi.tambahInfami(p, value - TemaApi.infami(p), "uji");
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.infami", TemaApi.infami(p)), false);
		return 1;
	}

	private static int gentar(CommandSourceStack source, int value) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		KotaTemaData.get(p.level().getServer()).setGentar(p.getUUID(), kota.situsId(), value);
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.gentar", IblisMeter.gentar(p, kota.situsId())), false);
		return 1;
	}

	private static int buronan(CommandSourceStack source, boolean buat) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		if (buat) {
			if (!BuronanIblis.buatUji(p)) {
				source.sendFailure(Component.translatable("kultivator.kota_tema.iblis.cmd.tidak_ada"));
				return 0;
			}
			return 1;
		}
		return baris(source, BuronanIblis.status(p));
	}

	private static int pemburu(CommandSourceStack source, boolean paksa) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		if (paksa) {
			return PemburuLurus.cegat(p, true) ? 1 : 0;
		}
		source.sendSuccess(() -> PemburuLurus.pratinjau(p), false);
		return 1;
	}

	private static int alur(CommandSourceStack source, String tahap) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		boolean ok = QuestTracker.lompat(p, IblisCerita.QUEST, tahap);
		if (!ok) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.iblis.cmd.alur_gagal", tahap));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.alur", tahap), false);
		return 1;
	}

	private static int rute(CommandSourceStack source, String rute) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		IblisData.setRute(p, "-".equals(rute) ? "" : rute);
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.rute", IblisData.rute(p)), false);
		return 1;
	}

	private static int pesta(CommandSourceStack source, boolean on) throws CommandSyntaxException {
		return PestaDarah.uji(source.getPlayerOrException(), on) ? 1 : 0;
	}

	private static int serbu(CommandSourceStack source, boolean mulai) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		if (mulai) {
			return PenyerbuanLurus.panggilBab5(p) ? 1 : 0;
		}
		source.sendSuccess(() -> PenyerbuanLurus.status(p.level(), kota), false);
		return 1;
	}

	private static int berburu(CommandSourceStack source) throws CommandSyntaxException {
		return JamBerburu.uji(source.getPlayerOrException()) ? 1 : 0;
	}

	private static int lelang(CommandSourceStack source) throws CommandSyntaxException {
		if (!LelangGelap.uji(source.getPlayerOrException())) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.iblis.cmd.lelang_gagal"));
			return 0;
		}
		return 1;
	}

	private static int tawanan(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		var list = KandangTawanan.daftar(p.level(), kota);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag c) {
				Component line = Component.translatable("kultivator.kota_tema.iblis.cmd.tawanan", c.getIntOr("id", 0), KandangTawanan.nama(c), KandangTawanan.asal(c));
				source.sendSuccess(() -> line, false);
			}
		}
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.tawanan_harga", KandangTawanan.harga(kota, p.level())), false);
		return 1;
	}

	private static int kota(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		CompoundTag town = IblisData.kota(p.level().getServer(), kota.situsId());
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.kota", kota.nama(p.level().getSeed()), town.getLongOr("kas", 0L),
				town.getStringOr("penguasa", IblisData.PENGUASA_XUE), IblisMeter.gentar(p, kota.situsId()), IblisMeter.infami(p), IblisData.rute(p),
				QuestTracker.simpul(p, IblisCerita.QUEST)).withStyle(ChatFormatting.DARK_RED), false);
		return 1;
	}

	private static int reset(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		TemaKota.Kota kota = kotaAtauGagal(source);
		if (kota == null) {
			return 0;
		}
		CompoundTag town = IblisData.kota(p.level().getServer(), kota.situsId());
		for (String key : List.of("kandang_tutup", "rumah_tebusan", "segel_terbuka", "xue_yao_kalah", "a_fu_bebas", "lao_kuang_pergi", "minyak_sabotase",
				"pasar_naik", "markas_lurus", "kosong_sampai", "pasar_tutup_sampai", "upeti_naik_sampai", "minyak_pecah")) {
			town.remove(key);
		}
		town.putString("penguasa", IblisData.PENGUASA_XUE);
		town.putInt("kitab_stok", 1);
		IblisData.ubah(p.level().getServer());
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.iblis.cmd.reset"), false);
		return 1;
	}

	private static int jawab(CommandSourceStack source, boolean bayar) throws CommandSyntaxException {
		JamBerburu.jawab(source.getPlayerOrException(), bayar);
		return 1;
	}

	private static int baris(CommandSourceStack source, List<Component> lines) {
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return lines.size();
	}
}

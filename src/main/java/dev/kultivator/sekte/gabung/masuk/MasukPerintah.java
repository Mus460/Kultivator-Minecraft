package dev.kultivator.sekte.gabung.masuk;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungUji;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands of module 41B. Player ({@code /kultivasi gabung ...}, 41.13.2; {@code direktori} and {@code info} are 41U-1's
 * screens): {@code lamar <sekte> [gerbang|token|undangan|pelayan|tamu]}, {@code batal}, {@code sumpah [mulai|ya|tidak]},
 * {@code shifu [pilih <n>]}. Test ({@code /kultivasi uji gabung ...}, op 2, 41.13.3): {@code ujian <sekte> [tahap <1-3>]
 * [corak <id>]}, {@code ujian lulus|gagal}, {@code sumpah [<sekte> <pangkat>]}, {@code shifu [pilih <n>]},
 * {@code token [sekte]}, {@code undangan <sekte>}, {@code hari_ujian <sekte>}. Every one runs the same code as its button.
 */
final class MasukPerintah {

	private static final SuggestionProvider<CommandSourceStack> PINTU = (ctx, b) -> {
		List<String> ids = new ArrayList<>();
		for (Pintu p : Pintu.values()) {
			ids.add(p.id());
		}
		return SharedSuggestionProvider.suggest(ids, b);
	};
	private static final SuggestionProvider<CommandSourceStack> CORAK = (ctx, b) -> {
		List<String> ids = new ArrayList<>();
		for (Corak c : Corak.values()) {
			ids.add(c.id());
		}
		return SharedSuggestionProvider.suggest(ids, b);
	};
	private static final SuggestionProvider<CommandSourceStack> PANGKAT = (ctx, b) -> {
		List<String> ids = new ArrayList<>();
		for (Pangkat p : Pangkat.values()) {
			ids.add(p.id());
		}
		return SharedSuggestionProvider.suggest(ids, b);
	};

	static void init() {
		pemain();
		uji();
	}

	// ================================================================== player

	private static void pemain() {
		GabungCommands.daftar("lamar", () -> Commands.literal("lamar")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							Lamaran.buka(p, StringArgumentType.getString(ctx, "sekte"));
							return 1;
						})
						.then(Commands.argument("pintu", StringArgumentType.word()).suggests(PINTU).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							Pintu pintu = Pintu.byId(StringArgumentType.getString(ctx, "pintu"), null);
							if (pintu == null) {
								ctx.getSource().sendFailure(Masuk.t("cmd.pintu_salah"));
								return 0;
							}
							Component why = Lamaran.ajukan(p, StringArgumentType.getString(ctx, "sekte"), pintu);
							if (why != null) {
								ctx.getSource().sendFailure(why);
								return 0;
							}
							return 1;
						}))));
		GabungCommands.daftar("batal", () -> Commands.literal("batal").executes(ctx -> {
			if (!Lamaran.batal(ctx.getSource().getPlayerOrException())) {
				ctx.getSource().sendFailure(Masuk.t("cmd.tak_ada_lamaran"));
				return 0;
			}
			return 1;
		}));
		GabungCommands.daftar("sumpah", () -> Commands.literal("sumpah")
				.executes(MasukPerintah::sumpahMulai)
				.then(Commands.literal("mulai").executes(MasukPerintah::sumpahMulai))
				.then(Commands.literal("ya").executes(ctx -> jawab(ctx, true)))
				.then(Commands.literal("tidak").executes(ctx -> jawab(ctx, false))));
		GabungCommands.daftar("shifu", () -> Commands.literal("shifu")
				.executes(ctx -> shifu(ctx.getSource().getPlayerOrException(), ctx.getSource()))
				.then(Commands.literal("pilih").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> pilihNo(ctx, IntegerArgumentType.getInteger(ctx, "n"))))));
	}

	private static int sumpahMulai(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Component why = UpacaraSumpah.mulai(ctx.getSource().getPlayerOrException());
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int jawab(CommandContext<CommandSourceStack> ctx, boolean ya) throws CommandSyntaxException {
		if (!UpacaraSumpah.jawab(ctx.getSource().getPlayerOrException(), ya)) {
			ctx.getSource().sendFailure(Masuk.t("cmd.tak_ada_sumpah"));
			return 0;
		}
		return 1;
	}

	private static int shifu(ServerPlayer p, CommandSourceStack source) {
		Keanggotaan k = GabungApi.anggota(p);
		if (!k.shifu.isEmpty()) {
			Component nama = dev.kultivator.sekte.gabung.dunia.TokohSekte.nama(source.getServer(), k.shifu);
			source.sendSuccess(() -> Masuk.t("cmd.shifu", nama != null ? nama : Component.literal(k.shifu), k.ikatanShifu)
					.withStyle(ChatFormatting.AQUA), false);
			return 1;
		}
		if (PilihShifu.bukaLagi(p) || PilihShifu.mulai(p)) {
			return 1;
		}
		source.sendFailure(Masuk.t("cmd.shifu_tidak"));
		return 0;
	}

	private static int pilihNo(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ListTag offers = GabungApi.anggota(p).bagian(Putusan.B).getListOrEmpty("shifu_tawaran");
		if (n > offers.size() || !PilihShifu.pilih(p, offers.getCompoundOrEmpty(n - 1).getStringOr("id", ""))) {
			ctx.getSource().sendFailure(Masuk.t("cmd.shifu_tidak"));
			return 0;
		}
		return 1;
	}

	// ================================================================== test

	private static void uji() {
		GabungUji.daftar("ujian", () -> Commands.literal("ujian")
				.then(Commands.literal("lulus").executes(ctx -> paksa(ctx, true)))
				.then(Commands.literal("gagal").executes(ctx -> paksa(ctx, false)))
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> ujian(ctx, 0, null))
						.then(Commands.literal("tahap").then(Commands.argument("tahap", IntegerArgumentType.integer(1, 3))
								.executes(ctx -> ujian(ctx, IntegerArgumentType.getInteger(ctx, "tahap"), null))
								.then(Commands.literal("corak").then(Commands.argument("corak", StringArgumentType.word()).suggests(CORAK)
										.executes(ctx -> ujian(ctx, IntegerArgumentType.getInteger(ctx, "tahap"),
												StringArgumentType.getString(ctx, "corak")))))))
						.then(Commands.literal("corak").then(Commands.argument("corak", StringArgumentType.word()).suggests(CORAK)
								.executes(ctx -> ujian(ctx, 0, StringArgumentType.getString(ctx, "corak")))))));
		GabungUji.daftar("sumpah", () -> Commands.literal("sumpah")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Component why = UpacaraSumpah.mulai(p);
					if (why != null) {
						ctx.getSource().sendFailure(why);
						return 0;
					}
					return 1;
				})
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.then(Commands.argument("pangkat", StringArgumentType.word()).suggests(PANGKAT).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							Pangkat pangkat = Pangkat.byId(StringArgumentType.getString(ctx, "pangkat"));
							Component why = UpacaraSumpah.mulai(p, StringArgumentType.getString(ctx, "sekte"), pangkat, Pintu.GERBANG, 0, false, true);
							if (why != null) {
								ctx.getSource().sendFailure(why);
								return 0;
							}
							return 1;
						}))));
		GabungUji.daftar("shifu", () -> Commands.literal("shifu")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					GabungApi.anggota(p).shifu = "";
					return PilihShifu.mulai(p) ? 1 : 0;
				})
				.then(Commands.literal("pilih").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> pilihNo(ctx, IntegerArgumentType.getInteger(ctx, "n"))))));
		GabungUji.daftar("token", () -> Commands.literal("token")
				.executes(ctx -> {
					TokenNaik.beriToken(ctx.getSource().getPlayerOrException(), List.of("umum"));
					return 1;
				})
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE).executes(ctx -> {
					TokenNaik.beriToken(ctx.getSource().getPlayerOrException(), List.of(StringArgumentType.getString(ctx, "sekte")));
					return 1;
				})));
		GabungUji.daftar("undangan", () -> Commands.literal("undangan")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE).executes(ctx -> {
					TokenNaik.beriUndangan(ctx.getSource().getPlayerOrException(), List.of(StringArgumentType.getString(ctx, "sekte")));
					return 1;
				})));
		GabungUji.daftar("hari_ujian", () -> Commands.literal("hari_ujian")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE).executes(ctx -> {
					String sekte = StringArgumentType.getString(ctx, "sekte");
					var server = ctx.getSource().getServer();
					long next = HariUjian.berikutUntuk(server, sekte);
					boolean buka = HariUjian.bukaSekarang(server, sekte);
					ctx.getSource().sendSuccess(() -> Masuk.t("cmd.hari_ujian", Masuk.nama(sekte), next, buka), false);
					ServerPlayer p = ctx.getSource().getPlayer();
					if (p != null) {
						for (Pintu pintu : Pintu.values()) {
							boolean ok = SyaratMasuk.lolos(SyaratMasuk.periksa(p, sekte, pintu));
							ctx.getSource().sendSuccess(() -> Component.literal("  ").append(pintu.nama()).append(": ")
									.append(Component.literal(ok ? "✓" : "✗").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
						}
					}
					return 1;
				})));
		GabungUji.chips("gabung token [sekte]", "gabung undangan <sekte>", "gabung hari_ujian <sekte>");
	}

	private static int ujian(CommandContext<CommandSourceStack> ctx, int tahap, String corakId) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		String sekte = StringArgumentType.getString(ctx, "sekte");
		Corak corak = corakId == null ? null : Corak.byId(corakId, null);
		if (corakId != null && corak == null) {
			ctx.getSource().sendFailure(Masuk.t("cmd.corak_salah", corakId));
			return 0;
		}
		Component why = HariUjian.mulai(p, sekte, Pintu.GERBANG, true, tahap, corak);
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int paksa(CommandContext<CommandSourceStack> ctx, boolean lulus) throws CommandSyntaxException {
		if (!HariUjian.paksa(ctx.getSource().getPlayerOrException(), lulus)) {
			ctx.getSource().sendFailure(Masuk.t("cmd.tak_ada_ujian"));
			return 0;
		}
		return 1;
	}

	private MasukPerintah() {
	}
}

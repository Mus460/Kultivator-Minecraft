package dev.kultivator.dunia.tempat.gua;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.trial.HeartDemons;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /kultivasi uji tempat_gua ...} (op, DESIGN 38.D.10 "gua"; the literal {@code gua} belongs to module R) and the player command {@code /kultivasi cermin}.
 * <ul>
 *   <li>{@code cari <jenis>}: teleport to the nearest cave or site of that kind (vanilla locate, existing chunks only
 *       generate as the player arrives);</li>
 *   <li>{@code ujian warisan [status|hati|kuat|paham|hadiah|reset]}: the nearest tomb's progress of the caller;</li>
 *   <li>{@code ujian cermin}: a Heart Mirror trial now (no day limit, no realm gate);</li>
 *   <li>{@code ujian pertapa [meditasi|pergi|reset]}: the hermit's incense trial done, the hermit leaves, he is back;</li>
 *   <li>{@code penjaga}: wakes the nearest Crystal Guardians or tomb puppets;</li>
 *   <li>{@code tikus [n]}: releases spirit rats at the caller (quest {@code tikus_dapur} test);</li>
 *   <li>{@code status}: live runs, the place the caller stands in and the five place factors.</li>
 * </ul>
 */
final class GuaCommands {

	static final List<String> JENIS = List.of("gua/gua_pertapa", "gua/makam_warisan_1", "gua/makam_warisan_2", "gua/makam_warisan_3",
			"gua/sarang_binatang", "gua/sarang_iblis", "gua/gua_urat_roh", "gua/cermin_hati", "situs/puncak_petir", "situs/panggung_bintang",
			"situs/mata_air_roh");

	static void init() {
		// "uji gua" is taken by module R (RTests) and ImmortalPerks: 38GU uses "uji tempat_gua" (DESIGN 38.D.10 table name "gua")
		DuniaCommands.test("tempat_gua", GuaCommands::uji);
		DuniaCommands.player("cermin", () -> Commands.literal("cermin")
				.executes(ctx -> CerminHati.mulai(ctx.getSource().getPlayerOrException()) ? 1 : 0));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("tempat_gua")
				.then(Commands.literal("cari").then(Commands.argument("jenis", StringArgumentType.greedyString())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(JENIS, b))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String jenis = StringArgumentType.getString(ctx, "jenis").trim();
							String id = jenis.contains("/") ? jenis : (jenis.startsWith("puncak") || jenis.startsWith("panggung") || jenis.startsWith("mata")
									? "situs/" : "gua/") + jenis;
							return DuniaCommands.findAndTeleport(player, Kultivator.id(id));
						})))
				.then(Commands.literal("ujian")
						.then(Commands.literal("warisan")
								.executes(ctx -> warisan(ctx.getSource(), "status"))
								.then(Commands.argument("tahap", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("status", "hati", "kuat", "paham", "hadiah", "reset"), b))
										.executes(ctx -> warisan(ctx.getSource(), StringArgumentType.getString(ctx, "tahap")))))
						.then(Commands.literal("cermin").executes(ctx -> cermin(ctx.getSource())))
						.then(Commands.literal("pertapa")
								.executes(ctx -> pertapa(ctx.getSource(), "meditasi"))
								.then(Commands.argument("tahap", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("meditasi", "pergi", "reset"), b))
										.executes(ctx -> pertapa(ctx.getSource(), StringArgumentType.getString(ctx, "tahap"))))))
				.then(Commands.literal("penjaga").executes(ctx -> penjaga(ctx.getSource())))
				.then(Commands.literal("tikus").executes(ctx -> tikus(ctx.getSource(), 3))
						.then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6))
								.executes(ctx -> tikus(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
	}

	private static int warisan(CommandSourceStack source, String tahap) {
		ServerPlayer player = source.getPlayer();
		MakamRun run = player == null ? null : GuaRuns.nearest(player, MakamRun.class, 96.0D);
		if (run == null) {
			source.sendFailure(Component.translatable("kultivator.gua.uji.tiada_makam"));
			return 0;
		}
		switch (tahap) {
			case "hati" -> run.tambah(player, GuaData.HATI);
			case "kuat" -> run.tambah(player, GuaData.KUAT);
			case "paham" -> run.tambah(player, GuaData.PAHAM);
			case "hadiah" -> {
				MakamRun.Aula aula = GuaRuns.nearest(player, MakamRun.Aula.class, 128.0D);
				if (aula == null) {
					source.sendFailure(Component.translatable("kultivator.gua.uji.tiada_aula"));
					return 0;
				}
				aula.beri(player);
			}
			case "reset" -> GuaData.get(source.getServer()).resetMakam(run.situs(), player.getUUID());
			default -> {
			}
		}
		run.periksa(player);
		int bits = run.bits(player);
		source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.warisan", run.dao().displayName(), run.tier(), run.quest(),
				run.tahap(player).isEmpty() ? "-" : run.tahap(player), Integer.toBinaryString(bits),
				String.format(java.util.Locale.ROOT, "%.2f", TempatApi.faktor(player, "warisan"))), false);
		return 1;
	}

	private static int cermin(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (!HeartDemons.startVoluntaryTrial(player)) {
			source.sendFailure(Component.translatable("kultivator.gua.cermin.gagal"));
			return 0;
		}
		CerminHati.paksa(player);
		source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.cermin"), false);
		return 1;
	}

	private static int pertapa(CommandSourceStack source, String tahap) {
		ServerPlayer player = source.getPlayer();
		PertapaRun run = player == null ? null : GuaRuns.nearest(player, PertapaRun.class, 64.0D);
		if (run == null) {
			source.sendFailure(Component.translatable("kultivator.gua.uji.tiada_pertapa"));
			return 0;
		}
		switch (tahap) {
			case "pergi" -> run.state().putLong("pergi", run.today());
			case "reset" -> run.state().remove("pergi");
			default -> CeritaApi.peristiwa(player, "ujian", "pertapa:meditasi");
		}
		source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.pertapa", tahap, run.dao().displayName(), run.ranah().displayName(),
				run.pergi()), false);
		return 1;
	}

	private static int penjaga(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		GuaUratRoh urat = GuaRuns.nearest(player, GuaUratRoh.class, 64.0D);
		if (urat != null) {
			urat.uji(player);
			source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.penjaga_kristal"), false);
			return 1;
		}
		MakamRun.Kekuatan room = GuaRuns.nearest(player, MakamRun.Kekuatan.class, 64.0D);
		if (room != null) {
			room.uji(player);
			source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.penjaga_boneka"), false);
			return 1;
		}
		source.sendFailure(Component.translatable("kultivator.gua.uji.tiada_penjaga"));
		return 0;
	}

	private static int tikus(CommandSourceStack source, int n) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		int made = TikusRohEntity.lepas(player.level(), player.position().add(player.getLookAngle().scale(3.0D)), n).size();
		source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.tikus", made), false);
		return made > 0 ? 1 : 0;
	}

	private static int status(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		GuaRun at = GuaRuns.nearest(player, GuaRun.class, 48.0D);
		String where = at == null ? "-" : at.anchor.kind() + " @ " + at.anchor.blockPosition().toShortString() + (at.inside(player) ? " (di dalam)" : "");
		source.sendSuccess(() -> Component.translatable("kultivator.gua.uji.status", GuaRuns.count(), where, GuaConfig.on(), GuaApi.dalamUji(player))
				.withStyle(ChatFormatting.AQUA), false);
		for (String key : TempatApi.FAKTOR) {
			double f = TempatApi.faktor(player, key);
			source.sendSuccess(() -> Component.literal("  " + key + " = " + String.format(java.util.Locale.ROOT, "%.2f", f)).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private GuaCommands() {
	}
}

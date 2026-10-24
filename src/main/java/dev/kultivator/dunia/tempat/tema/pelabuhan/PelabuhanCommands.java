package dev.kultivator.dunia.tempat.tema.pelabuhan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.tema.TemaCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /kultivasi uji tema kapal ...} (DESIGN 38.T.13.2, op only): the harbour's mechanisms and story on demand, only
 * in the harbour the player stands in. World weather and time are never changed.
 * <pre>
 * kapal                      schedule of the three moorings, live bodies, lamp, raids
 * kapal datang [tambatan]    a junk comes in now (default the first live mooring)
 * kapal pergi [tambatan]     the moored junk weighs anchor now
 * kapal lintas               the Kapal Lintas Laut leaves now with every ticket holder on the quay
 * kapal pelita &lt;0-2&gt;         lamp state (0 cracked, 1 new lens, 2 Mutiara Fajar)
 * kapal bajak [gelombang]    a pirate raid now
 * kapal lomba                the regatta (or A-Lan's practice race) starts now
 * kapal galangan             every keel order of the town is finished now
 * kapal heisha               summons Kapten Hiu Hitam on the breakwater (chain bab 6)
 * kapal bab &lt;1-9&gt;            jumps the chain to a stage (1 redup .. 9 upacara)
 * </pre>
 */
final class PelabuhanCommands {

	private static final List<String> TAHAP = List.of("redup", "catatan", "lapor", "lensa", "serbuan", "mutiara", "titipan", "duel", "nasib", "upacara");

	static void init() {
		TemaCommands.daftar(PelabuhanKota.TEMA, PelabuhanCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("kapal")
				.executes(PelabuhanCommands::info)
				.then(Commands.literal("datang").executes(ctx -> datang(ctx, -1))
						.then(Commands.argument("tambatan", IntegerArgumentType.integer(0, 2))
								.executes(ctx -> datang(ctx, IntegerArgumentType.getInteger(ctx, "tambatan")))))
				.then(Commands.literal("pergi").executes(ctx -> pergi(ctx, -1))
						.then(Commands.argument("tambatan", IntegerArgumentType.integer(0, 2))
								.executes(ctx -> pergi(ctx, IntegerArgumentType.getInteger(ctx, "tambatan")))))
				.then(Commands.literal("lintas").executes(PelabuhanCommands::lintas))
				.then(Commands.literal("pelita").then(Commands.argument("n", IntegerArgumentType.integer(0, 2)).executes(ctx -> {
					PelabuhanKota.Hidup kota = kota(ctx);
					Suar.setPelita(ctx.getSource().getServer(), kota.situs, IntegerArgumentType.getInteger(ctx, "n"));
					return ok(ctx, "pelita " + IntegerArgumentType.getInteger(ctx, "n"));
				})))
				.then(Commands.literal("bajak").executes(ctx -> bajak(ctx, 2))
						.then(Commands.argument("gelombang", IntegerArgumentType.integer(1, 5))
								.executes(ctx -> bajak(ctx, IntegerArgumentType.getInteger(ctx, "gelombang")))))
				.then(Commands.literal("lomba").executes(ctx -> {
					PelabuhanKota.Hidup kota = kota(ctx);
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (!HariDewiLaut.pendaftaran(kota.situs)) {
						HariDewiLaut.latihan(p.level(), kota.situs, p);
					}
					return ok(ctx, HariDewiLaut.paksaLomba(p.level(), kota.situs) ? "lomba mulai" : "lomba menunggu peserta (sewa perahu lomba)");
				}))
				.then(Commands.literal("galangan").executes(ctx -> {
					PelabuhanKota.Hidup kota = kota(ctx);
					ListTag list = PelabuhanKota.kota(ctx.getSource().getServer(), kota.situs).getListOrEmpty("pesanan");
					for (Tag t : list) {
						if (t instanceof net.minecraft.nbt.CompoundTag c) {
							c.putLong("mulai", c.getLongOr("mulai", 0L) - 10L);
						}
					}
					PelabuhanKota.ubah(ctx.getSource().getServer());
					return ok(ctx, "pesanan selesai: " + list.size());
				}))
				.then(Commands.literal("heisha").executes(ctx -> {
					PelabuhanCerita.panggilHeiSha(ctx.getSource().getPlayerOrException());
					return ok(ctx, "Hei Sha dipanggil");
				}))
				.then(Commands.literal("bab").then(Commands.argument("n", IntegerArgumentType.integer(1, TAHAP.size())).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					PelabuhanKota.Hidup kota = kota(ctx);
					if (!QuestTracker.aktif(p, PelabuhanCerita.QUEST)) {
						QuestTracker.mulai(p, PelabuhanCerita.QUEST, kota.situs);
					}
					String stage = TAHAP.get(IntegerArgumentType.getInteger(ctx, "n") - 1);
					return ok(ctx, QuestTracker.lompat(p, PelabuhanCerita.QUEST, stage) ? "bab " + stage : "gagal");
				})));
	}

	private static PelabuhanKota.Hidup kota(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
		if (kota == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("kultivator.kota_tema.pelabuhan.uji.bukan")).create();
		}
		return kota;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal("[pelabuhan] " + text).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PelabuhanKota.Hidup kota = kota(ctx);
		MinecraftServer server = ctx.getSource().getServer();
		long t = JadwalKapal.waktu(server);
		StringBuilder b = new StringBuilder();
		b.append("situs ").append(kota.situs).append(kota.kota == null ? " (uji)" : "").append(", hari ").append(Math.floorDiv(t, 24000L))
				.append(", jam ").append(Math.floorMod(t, 24000L)).append('\n');
		for (int n = 0; n < JadwalKapal.DAGANG; n++) {
			long c = JadwalKapal.siklus(t, n);
			b.append("  tambatan ").append(n).append(": ").append(JadwalKapal.keadaanData(server, kota.situs, n)).append(", asal ")
					.append(JadwalKapal.asal(kota.situs, n, c)).append(", datang hari ").append(JadwalKapal.hariDatang(server, kota.situs, n))
					.append(", u=").append(JadwalKapal.u(t, n)).append('\n');
		}
		b.append("  tubuh kapal ").append(JadwalKapal.jumlahTubuh()).append(", pelita ").append(Suar.pelita(server, kota.situs))
				.append(", bajak berikut hari ").append(PelabuhanKota.kota(server, kota.situs).getLongOr("bajak_berikut", -1L))
				.append(SerbuanBajakLaut.berhenti(server, kota.situs) ? " (berhenti)" : "").append(", serbuan aktif ")
				.append(SerbuanBajakLaut.aktif(kota.situs)).append(", festival ").append(HariDewiLaut.berjalan(kota.situs));
		String text = b.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int datang(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		PelabuhanKota.Hidup kota = kota(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		int nomor = n >= 0 ? n : Math.max(0, JadwalKapal.nomorHidup(kota.situs, false));
		return ok(ctx, JadwalKapal.paksaDatang(level, kota.situs, nomor) ? "kapal datang ke tambatan " + nomor : "tambatan " + nomor + " belum hidup (dekati dermaga)");
	}

	private static int pergi(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		PelabuhanKota.Hidup kota = kota(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		int nomor = n >= 0 ? n : JadwalKapal.nomorHidup(kota.situs, true);
		return ok(ctx, nomor >= 0 && JadwalKapal.paksaPergi(level, kota.situs, nomor) ? "kapal berangkat dari tambatan " + nomor : "tidak ada kapal tertambat");
	}

	private static int lintas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PelabuhanKota.Hidup kota = kota(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		List<DuniaAnchorEntity> quay = kota.jangkar(PelabuhanAnchors.LINTAS_LAUT);
		if (quay.isEmpty()) {
			return ok(ctx, "dermaga lintas belum dimuat");
		}
		DuniaAnchorEntity a = quay.getFirst();
		int n = LintasLaut.berangkat(level, a, kota, level.getPlayers(p -> p.distanceToSqr(a) < 48.0D * 48.0D), PelabuhanKota.hari(level));
		return ok(ctx, "penumpang naik: " + n);
	}

	private static int bajak(CommandContext<CommandSourceStack> ctx, int waves) throws CommandSyntaxException {
		PelabuhanKota.Hidup kota = kota(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		return ok(ctx, SerbuanBajakLaut.mulaiSerbuan(level, kota.situs, null, waves, true) ? "serbuan dimulai" : "serbuan tidak bisa dimulai");
	}

	private PelabuhanCommands() {
	}
}

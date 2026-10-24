package dev.kultivator.sekte.mandiri.tumbuh;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.mandiri.MandiriCommands;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The commands of module 39B (DESIGN 39.10.4), hung on {@code MandiriCommands}:
 * <ul>
 *   <li>Player (the chat buttons of the notices): {@code perguruan mandiri tembus jaga|biarkan <jiwa>},
 *   {@code ... dao setujui|biarkan <jiwa>}, {@code ... jenjang setujui|nanti <jiwa>}.</li>
 *   <li>Test (op): {@code uji perguruan mandiri proyeksi <murid>} (the table of 39.5.1 with the real factors),
 *   {@code ... tembus <murid>} (the self breakthrough now, the waiting skipped), {@code ... hati <murid> lulus|gagal} (the
 *   next Heart Trial's result) and {@code ... tumbuh info|ajar|dao|dewa|jenjang|pencerahan|serap|belajar <murid>},
 *   {@code ... tumbuh tutup <murid> <hari>}.</li>
 * </ul>
 */
final class TumbuhCommands {

	private static final String P = KabarTumbuh.P + "cmd.";
	private static boolean installed;

	private static final SuggestionProvider<CommandSourceStack> NAMA = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
				if (e.role() == DiscipleEntity.Role.DISCIPLE) {
					names.add(e.getName().getString().replace(' ', '_'));
				}
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	static void init() {
		if (installed) {
			return;
		}
		installed = true;
		MandiriCommands.pemain("tembus", () -> Commands.literal("tembus")
				.then(Commands.literal("jaga").then(jiwa().executes(ctx -> tembus(ctx, true))))
				.then(Commands.literal("biarkan").then(jiwa().executes(ctx -> tembus(ctx, false)))));
		MandiriCommands.pemain("dao", () -> Commands.literal("dao")
				.then(Commands.literal("setujui").then(jiwa().executes(ctx -> dao(ctx, true))))
				.then(Commands.literal("biarkan").then(jiwa().executes(ctx -> dao(ctx, false)))));
		MandiriCommands.pemain("jenjang", () -> Commands.literal("jenjang")
				.then(Commands.literal("setujui").then(jiwa().executes(ctx -> jenjang(ctx, true))))
				.then(Commands.literal("nanti").then(jiwa().executes(ctx -> jenjang(ctx, false)))));
		MandiriCommands.uji("proyeksi", () -> perMurid("proyeksi", TumbuhCommands::proyeksi));
		MandiriCommands.uji("tembus", () -> perMurid("tembus", (ctx, murid) -> hasil(ctx, TerobosanSendiri.paksa(murid), "tembus")));
		MandiriCommands.uji("hati", () -> Commands.literal("hati").then(murid()
				.then(Commands.literal("lulus").executes(ctx -> hati(ctx, true)))
				.then(Commands.literal("gagal").executes(ctx -> hati(ctx, false)))));
		MandiriCommands.uji("tumbuh", () -> Commands.literal("tumbuh")
				.then(perMurid("info", TumbuhCommands::info))
				.then(perMurid("ajar", (ctx, murid) -> hasil(ctx, AjarSenior.paksa(murid), "ajar")))
				.then(perMurid("dao", (ctx, murid) -> hasil(ctx, DaoSendiri.paksa(murid), "dao")))
				.then(perMurid("dewa", (ctx, murid) -> hasil(ctx, DewaSendiri.paksa(murid), "dewa")))
				.then(perMurid("jenjang", (ctx, murid) -> {
					JenjangSendiri.paksa(murid);
					Component why = SectRanks.promoteBlockerSelf(murid);
					return hasil(ctx, why == null || SectRanks.onlyMasterLinesSelf(murid), "jenjang");
				}))
				.then(perMurid("pencerahan", (ctx, murid) -> hasil(ctx, Pencerahan.coba(murid, true) != null, "pencerahan")))
				.then(perMurid("serap", (ctx, murid) -> hasil(ctx,
						MandiriApi.mulaiKegiatan(murid, Kegiatan.SERAP_BATU, Kegiatan.JAM, null, null), "serap")))
				.then(perMurid("belajar", (ctx, murid) -> hasil(ctx,
						MandiriApi.mulaiKegiatan(murid, Kegiatan.BELAJAR, 2 * Kegiatan.JAM, null, null), "belajar")))
				.then(Commands.literal("tutup").then(murid().then(Commands.argument("hari", IntegerArgumentType.integer(1, 5))
						.executes(ctx -> {
							DiscipleEntity murid = cari(ctx);
							int days = IntegerArgumentType.getInteger(ctx, "hari");
							return murid == null ? 0 : hasil(ctx, MandiriApi.mulaiKegiatan(murid, Kegiatan.TUTUP_DIRI, days * 24000, null, null), "tutup");
						})))));
		MandiriCommands.chips("perguruan mandiri proyeksi", "perguruan mandiri tembus", "perguruan mandiri hati",
				"perguruan mandiri tumbuh info", "perguruan mandiri tumbuh ajar", "perguruan mandiri tumbuh dao",
				"perguruan mandiri tumbuh dewa", "perguruan mandiri tumbuh jenjang", "perguruan mandiri tumbuh pencerahan",
				"perguruan mandiri tumbuh tutup", "perguruan mandiri tumbuh serap", "perguruan mandiri tumbuh belajar");
	}

	// ================================================================== helpers

	private interface Aksi {
		int run(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) throws CommandSyntaxException;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(NAMA);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> jiwa() {
		return Commands.argument("jiwa", StringArgumentType.word());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> perMurid(String literal, Aksi aksi) {
		return Commands.literal(literal).then(murid().executes(ctx -> {
			DiscipleEntity e = cari(ctx);
			return e == null ? 0 : aksi.run(ctx, e);
		}));
	}

	private static @Nullable DiscipleEntity cari(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "murid");
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && e.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				return e;
			}
		}
		ctx.getSource().sendFailure(Component.translatable(P + "tidak_ada", name));
		return null;
	}

	/** The player's loaded disciple with the soul of argument {@code jiwa}, or null (a message is sent). */
	private static @Nullable DiscipleEntity jiwaMilik(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID soul;
		try {
			soul = UUID.fromString(StringArgumentType.getString(ctx, "jiwa"));
		} catch (IllegalArgumentException exception) {
			ctx.getSource().sendFailure(Component.translatable(P + "jiwa_salah"));
			return null;
		}
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.soulId().equals(soul) && e.isOwnedBy(player)) {
				return e;
			}
		}
		ctx.getSource().sendFailure(Component.translatable(P + "jauh"));
		return null;
	}

	private static int hasil(CommandContext<CommandSourceStack> ctx, boolean ok, String what) {
		if (ok) {
			ctx.getSource().sendSuccess(() -> Component.translatable(P + what + ".ok").withStyle(ChatFormatting.GREEN), false);
			return 1;
		}
		ctx.getSource().sendFailure(Component.translatable(P + what + ".gagal"));
		return 0;
	}

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	private static String f1(double v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	private static String f2(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	// ================================================================== player buttons

	private static int tembus(CommandContext<CommandSourceStack> ctx, boolean jaga) throws CommandSyntaxException {
		DiscipleEntity murid = jiwaMilik(ctx);
		if (murid == null) {
			return 0;
		}
		TerobosanSendiri.jawab(murid.soulId(), jaga);
		ctx.getSource().sendSuccess(() -> KabarTumbuh.baris(jaga ? "jawab.jaga" : "jawab.biarkan", jaga ? 0xFFD54A : 0x8FE3C8,
				KabarTumbuh.nama(murid), TerobosanSendiri.TUNGGU_GURU_JAGA_HARI), false);
		return 1;
	}

	private static int dao(CommandContext<CommandSourceStack> ctx, boolean setujui) throws CommandSyntaxException {
		DiscipleEntity murid = jiwaMilik(ctx);
		if (murid == null) {
			return 0;
		}
		ServerPlayer master = ctx.getSource().getPlayerOrException();
		if (setujui) {
			return DaoSendiri.setujui(murid, master) ? 1 : 0;
		}
		DaoSendiri.biarkan(murid);
		ctx.getSource().sendSuccess(() -> KabarTumbuh.baris("jawab.dao_biarkan", 0x8FE3C8, KabarTumbuh.nama(murid)), false);
		return 1;
	}

	private static int jenjang(CommandContext<CommandSourceStack> ctx, boolean setujui) throws CommandSyntaxException {
		DiscipleEntity murid = jiwaMilik(ctx);
		if (murid == null) {
			return 0;
		}
		ServerPlayer master = ctx.getSource().getPlayerOrException();
		if (setujui) {
			return JenjangSendiri.setujui(murid, master) ? 1 : 0;
		}
		JenjangSendiri.nanti(murid);
		ctx.getSource().sendSuccess(() -> KabarTumbuh.baris("jawab.jenjang_nanti", 0xA0A0A0, KabarTumbuh.nama(murid),
				JenjangSendiri.ULANG_HARI), false);
		return 1;
	}

	// ================================================================== test

	private static int hati(CommandContext<CommandSourceStack> ctx, boolean lulus) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		UjianHati.paksa(murid.soulId(), lulus);
		return hasil(ctx, true, lulus ? "hati_lulus" : "hati_gagal");
	}

	/** The projection table of 39.5.1 for this disciple. */
	private static int proyeksi(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		CommandSourceStack src = ctx.getSource();
		Proyeksi.Hasil hasil = Proyeksi.hitung(murid.data());
		baris(src, Component.translatable(P + "proyeksi.judul", KabarTumbuh.nama(murid),
				hasil.jalan() == null ? Component.translatable(P + "belum") : Component.translatable(hasil.jalan().kunciNama()),
				f2(hasil.kBar() * 24.0D), f2(hasil.tempat()), f2(hasil.pengali()), f2(hasil.skala())).withStyle(ChatFormatting.GOLD));
		if (hasil.baris().isEmpty()) {
			baris(src, Component.translatable(P + "proyeksi.sudah").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		baris(src, Component.translatable(P + "proyeksi.kepala").withStyle(ChatFormatting.DARK_GRAY));
		for (Proyeksi.Baris row : hasil.baris()) {
			baris(src, Component.literal(" ").append(row.realm().displayName().copy().withColor(row.realm().color()))
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(f1(row.sendiri())).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" · " + f1(row.asli()) + " · " + f1(row.luarLayar()) + " · " + f1(row.diperintah()))
							.withStyle(ChatFormatting.GRAY)));
		}
		baris(src, Component.translatable(P + "proyeksi.total", f1(hasil.total()), f1(Proyeksi.Hasil.jamMain(hasil.total())),
				f1(hasil.totalAsli()), f1(hasil.totalLuarLayar()), f1(hasil.totalDiperintah())).withStyle(ChatFormatting.YELLOW));
		baris(src, Component.translatable(P + "proyeksi.catatan").withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	/** Everything 39B knows about one disciple. */
	private static int info(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		CommandSourceStack src = ctx.getSource();
		DiscipleData data = murid.data();
		UUID soul = murid.soulId();
		long now = murid.level().getGameTime();
		baris(src, Component.translatable(P + "info.judul", KabarTumbuh.nama(murid), data.rank().displayName()).withStyle(ChatFormatting.GOLD));
		String fase = TerobosanSendiri.fase(soul);
		String why = dev.kultivator.disciple.DiscipleCultivation.breakthroughBlockerSelf(murid, 1.0D);
		baris(src, Component.translatable(P + "info.tembus", fase.isEmpty() ? "-" : fase, TerobosanSendiri.siap(murid) ? "ya" : "tidak",
				why == null ? Component.literal("-") : Component.translatable(why),
				DataTumbuh.tungguHari(data, DataTumbuh.T_TEROBOSAN)).withStyle(ChatFormatting.GRAY));
		UUID guard = HuFaSahabat.menjaga(soul);
		UUID teacher = AjarSenior.guruDari(soul);
		baris(src, Component.translatable(P + "info.kegiatan", MandiriApi.kegiatan(murid) == null ? "-" : MandiriApi.kegiatan(murid).id(),
				murid.activity(), BelajarSendiri.cara(soul).isEmpty() ? "-" : BelajarSendiri.cara(soul),
				guard == null ? "-" : guard.toString().substring(0, 8), teacher == null ? "-" : teacher.toString().substring(0, 8))
				.withStyle(ChatFormatting.GRAY));
		baris(src, Component.translatable(P + "info.tutup", MandiriApi.tutupDiri(data, now) ? "ya" : "tidak",
				f2(LajuMandiri.faktorTutup(data, now)), SerapBatu.biaya(data), MandiriApi.dompet(data)).withStyle(ChatFormatting.GRAY));
		Dao lean = DaoSendiri.pilih(murid);
		MutableComponent daoLine = Component.translatable(P + "info.dao", data.dao() == null ? Component.literal("-") : data.dao().displayName(),
				lean == null ? Component.literal("-") : lean.displayName(), DataTumbuh.tungguHari(data, DataTumbuh.T_DAO),
				DaoSendiri.menunggu(murid) ? "ya" : "tidak");
		baris(src, daoLine.withStyle(ChatFormatting.GRAY));
		if (data.dao() == null && data.realm().atLeast(dev.kultivator.data.Realm.GOLDEN_CORE)) {
			for (Map.Entry<Dao, DaoSendiri.Skor> e : DaoSendiri.skor(murid).entrySet()) {
				DaoSendiri.Skor s = e.getValue();
				if (s.total() > 0.0D) {
					baris(src, Component.literal("   ").append(e.getKey().displayName()).append(Component.literal(" " + f1(s.total())
							+ " (akar " + f1(s.akar()) + ", jalan " + f1(s.jalan()) + ", teknik " + f1(s.teknik()) + ", guru " + f1(s.guru())
							+ ", sahabat " + f1(s.sahabat()) + ")")).withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		}
		Component rankWhy = SectRanks.promoteBlockerSelf(murid);
		baris(src, Component.translatable(P + "info.jenjang", SectRanks.rankName(SectRanks.rank(data)),
				rankWhy == null ? Component.translatable(P + "info.jenjang_siap") : rankWhy,
				DataTumbuh.tungguHari(data, DataTumbuh.T_JENJANG)).withStyle(ChatFormatting.GRAY));
		TechniqueSpec reading = null;
		String tas = dev.kultivator.sekte.paviliun.PaviliunMandiri.bacaanTas(data);
		if (!tas.isEmpty()) {
			reading = dev.kultivator.data.TechniqueRegistry.byId(tas);
		}
		baris(src, Component.translatable(P + "info.belajar", reading == null ? Component.literal("-") : reading.displayName(),
				f2(dev.kultivator.sekte.paviliun.PaviliunMandiri.kemajuanTas(data)), f2(Pencerahan.peluang(data) * 100.0D),
				dev.kultivator.disciple.DiscipleCultivation.dewaPending(data).size()).withStyle(ChatFormatting.GRAY));
		baris(src, Component.translatable(P + "info.statistik", DataTumbuh.statistik(data, DataTumbuh.S_TEROBOSAN),
				DataTumbuh.statistik(data, DataTumbuh.S_TEKNIK), DataTumbuh.statistik(data, DataTumbuh.S_TUTUP),
				DataTumbuh.statistik(data, DataTumbuh.S_BATU), DataTumbuh.statistik(data, DataTumbuh.S_AJAR),
				MandiriApi.iblis(data)).withStyle(ChatFormatting.GRAY));
		List<Tumbuh.Kebutuhan> needs = Tumbuh.butuh(murid);
		MutableComponent needLine = Component.translatable(P + "info.butuh");
		if (needs.isEmpty()) {
			needLine.append(Component.literal(" -"));
		}
		for (Tumbuh.Kebutuhan need : needs) {
			needLine.append(Component.literal(" " + need.item() + " x" + need.jumlah() + " (p" + need.prio() + ")"));
		}
		baris(src, needLine.withStyle(ChatFormatting.DARK_AQUA));
		baris(src, Component.literal(" " + MandiriIds.MODUL + "/39B").withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	private TumbuhCommands() {
	}
}

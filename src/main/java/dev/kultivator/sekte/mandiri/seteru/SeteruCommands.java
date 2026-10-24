package dev.kultivator.sekte.mandiri.seteru;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriCommands;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.PertemuanProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The commands of 39F (DESIGN 39.F.8), hung on {@code MandiriCommands} with the chips of group
 * {@code perguruan mandiri seteru}:
 * <ul>
 * <li>Test (op): {@code uji perguruan mandiri seteru daftar | roster <sekte> | mulai <murid> <sekte> <sebab> |
 * panas <id> <0-100> | naik <id> [cabang] | temui <murid> <tokoh> | duel <id> | tawuran <id> | sumpah <id> |
 * patroli <id> | asmara <murid> <tokoh> | damai <id> | akhir <id> <DAMAI|SAINGAN|DENDAM> | bersih | anggaran}.</li>
 * <li>Player (the chat buttons and the hub card): {@code perguruan mandiri seteru} (the screen),
 * {@code ... aksi <id> <aksi>}, {@code ... usir <id>} (duel the patrol's leader).</li>
 * </ul>
 */
final class SeteruCommands {

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

	private static final SuggestionProvider<CommandSourceStack> SEKTE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SekteApi.diplomasi().sects(), builder);

	private static final SuggestionProvider<CommandSourceStack> SEBAB = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of(SeteruIds.SEBAB), builder);

	private static final SuggestionProvider<CommandSourceStack> CABANG = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of(SeteruIds.CABANG), builder);

	private static final SuggestionProvider<CommandSourceStack> AKSI = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("damaikan", "utus_tetua", "hukum", "tuntut", "dukung", "tantang_tetua", "larang", "biarkan",
					"restui", "pisahkan"), builder);

	private static final SuggestionProvider<CommandSourceStack> ID = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> ids = new ArrayList<>();
		if (player != null) {
			SeteruData.Aula aula = SeteruData.get(player.level().getServer()).peek(player.getUUID());
			if (aula != null) {
				for (Perseteruan f : aula.seteru) {
					ids.add(f.id);
				}
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> TOKOH = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> ids = new ArrayList<>();
		if (player != null) {
			SeteruData data = SeteruData.get(player.level().getServer());
			for (List<TokohSekte> list : data.roster.values()) {
				for (TokohSekte t : list) {
					ids.add(t.id);
				}
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	static void init() {
		if (installed) {
			return;
		}
		installed = true;
		MandiriCommands.uji("seteru", SeteruCommands::uji);
		MandiriCommands.pemain("seteru", SeteruCommands::pemain);
		MandiriCommands.chips("perguruan mandiri seteru daftar", "perguruan mandiri seteru roster", "perguruan mandiri seteru mulai",
				"perguruan mandiri seteru panas", "perguruan mandiri seteru naik", "perguruan mandiri seteru temui", "perguruan mandiri seteru duel",
				"perguruan mandiri seteru tawuran", "perguruan mandiri seteru sumpah", "perguruan mandiri seteru patroli",
				"perguruan mandiri seteru asmara", "perguruan mandiri seteru damai", "perguruan mandiri seteru akhir",
				"perguruan mandiri seteru bersih", "perguruan mandiri seteru anggaran");
	}

	// ================================================================== trees

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("seteru")
				.executes(ctx -> daftar(ctx))
				.then(Commands.literal("daftar").executes(SeteruCommands::daftar))
				.then(Commands.literal("roster").then(sekte().executes(SeteruCommands::roster)))
				.then(Commands.literal("mulai").then(murid().then(sekte().then(Commands.argument("sebab", StringArgumentType.word()).suggests(SEBAB)
						.executes(SeteruCommands::mulai)))))
				.then(Commands.literal("panas").then(id().then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100))
						.executes(SeteruCommands::panas))))
				.then(Commands.literal("naik").then(id().executes(ctx -> naik(ctx, null))
						.then(Commands.argument("cabang", StringArgumentType.word()).suggests(CABANG)
								.executes(ctx -> naik(ctx, StringArgumentType.getString(ctx, "cabang"))))))
				.then(Commands.literal("temui").then(murid().then(tokoh().executes(SeteruCommands::temui))))
				.then(Commands.literal("duel").then(id().executes(ctx -> adegan(ctx, "duel"))))
				.then(Commands.literal("tawuran").then(id().executes(ctx -> adegan(ctx, "tawuran"))))
				.then(Commands.literal("sumpah").then(id().executes(ctx -> adegan(ctx, "sumpah"))))
				.then(Commands.literal("patroli").then(id().executes(SeteruCommands::patroli)))
				.then(Commands.literal("asmara").then(murid().then(tokoh().executes(SeteruCommands::asmara))))
				.then(Commands.literal("damai").then(id().executes(ctx -> akhir(ctx, SeteruIds.DAMAI))))
				.then(Commands.literal("akhir").then(id()
						.then(Commands.literal("DAMAI").executes(ctx -> akhir(ctx, SeteruIds.DAMAI)))
						.then(Commands.literal("SAINGAN").executes(ctx -> akhir(ctx, SeteruIds.SAINGAN)))
						.then(Commands.literal("DENDAM").executes(ctx -> akhir(ctx, SeteruIds.DENDAM)))))
				.then(Commands.literal("bersih").executes(SeteruCommands::bersih))
				.then(Commands.literal("anggaran").executes(SeteruCommands::anggaran));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pemain() {
		return Commands.literal("seteru")
				.executes(ctx -> {
					SeteruPayloads.kirim(ctx.getSource().getPlayerOrException(), true, null);
					return 1;
				})
				.then(Commands.literal("aksi").then(id().then(Commands.argument("aksi", StringArgumentType.word()).suggests(AKSI)
						.executes(SeteruCommands::aksi))))
				.then(Commands.literal("usir").then(id().executes(SeteruCommands::usir)));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(NAMA);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> sekte() {
		return Commands.argument("sekte", StringArgumentType.word()).suggests(SEKTE);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
		return Commands.argument("id", StringArgumentType.word()).suggests(ID);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> tokoh() {
		return Commands.argument("tokoh", StringArgumentType.string()).suggests(TOKOH);
	}

	// ================================================================== lookups

	private static @Nullable DiscipleEntity cariMurid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "murid");
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && e.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				return e;
			}
		}
		ctx.getSource().sendFailure(Teks.t("cmd.tanpa_murid", name));
		return null;
	}

	private static @Nullable Perseteruan cariSeteru(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		Perseteruan f = SeteruData.get(player.level().getServer()).aula(player.getUUID()).byId(id);
		if (f == null) {
			ctx.getSource().sendFailure(Teks.t("cmd.tanpa_seteru", id));
		}
		return f;
	}

	private static @Nullable TokohSekte cariTokoh(CommandContext<CommandSourceStack> ctx, MinecraftServer server) {
		String id = StringArgumentType.getString(ctx, "tokoh");
		int hash = id.indexOf('#');
		if (hash > 0) {
			Roster.anggota(server, id.substring(0, hash));
		}
		TokohSekte t = SeteruData.get(server).tokoh(id);
		if (t == null) {
			ctx.getSource().sendFailure(Teks.t("cmd.tanpa_tokoh", id));
		}
		return t;
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, Component line) {
		ctx.getSource().sendSuccess(() -> line, false);
	}

	// ================================================================== test commands

	private static int daftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		SeteruData.Aula aula = SeteruData.get(server).aula(player.getUUID());
		ok(ctx, Teks.t("cmd.daftar.judul", aula.aktif().size(), SeteruConfig.get().seteruMaksAula,
				Component.literal(SeteruConfig.get().tingkatIntensitas().name())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (aula.seteru.isEmpty()) {
			ok(ctx, Teks.t("cmd.daftar.kosong").withStyle(ChatFormatting.GRAY));
		}
		for (Perseteruan f : aula.seteru) {
			MutableComponent line = Component.literal(" " + f.id + " ").withStyle(ChatFormatting.AQUA);
			line.append(Teks.t("cmd.daftar.baris", f.namaUtama(), Teks.sekte(f.sekte), Teks.t("sebab." + f.sebab),
					Teks.t("tingkat." + Math.max(0, f.tingkat)), f.panas, Teks.t("status." + f.status)).withStyle(Tangga.warna(f.tingkat)));
			if (f.sergapTertunda) {
				line.append(Teks.t("cmd.daftar.sergap").withStyle(ChatFormatting.RED));
			}
			if (!f.adegan.isEmpty()) {
				line.append(Teks.t("cmd.daftar.adegan", f.adegan.getStringOr("jenis", "")).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			ok(ctx, line);
		}
		return 1;
	}

	private static int roster(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		String sekte = StringArgumentType.getString(ctx, "sekte");
		List<TokohSekte> list = Roster.anggota(server, sekte);
		if (list.isEmpty()) {
			ctx.getSource().sendFailure(Teks.t("cmd.tanpa_sekte", sekte));
			return 0;
		}
		long today = Ingatan.today(server);
		int tier = SekteApi.diplomasi().tier(sekte);
		ok(ctx, Teks.t("cmd.roster.judul", Teks.sekte(sekte), tier, list.size()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (TokohSekte t : list) {
			MutableComponent traits = Component.empty();
			for (Sifat s : t.sifat) {
				if (!traits.getSiblings().isEmpty()) {
					traits.append(", ");
				}
				traits.append(Component.translatable(s.key()));
			}
			int best = 0;
			for (Integer v : t.sikap.values()) {
				if (Math.abs(v) > Math.abs(best)) {
					best = v;
				}
			}
			Rank rank = t.rank(today, tier);
			ok(ctx, Component.literal(" " + t.id + " ").withStyle(ChatFormatting.DARK_AQUA)
					.append(Teks.t("cmd.roster.baris", t.namaTampil(), Component.translatable(t.kunciPeran()), rank.displayName(), traits, best,
							Teks.t("status_tokoh." + t.status.toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static int mulai(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		DiscipleEntity murid = cariMurid(ctx);
		if (murid == null) {
			return 0;
		}
		String sekte = StringArgumentType.getString(ctx, "sekte");
		String sebab = StringArgumentType.getString(ctx, "sebab");
		TokohSekte t = Roster.pilih(server, sekte, murid.soulId(), Ingatan.today(server), RandomSource.create(), true);
		if (t == null) {
			ctx.getSource().sendFailure(Teks.t("cmd.tanpa_sekte", sekte));
			return 0;
		}
		int start = switch (sebab) {
			case SeteruIds.SEBAB_GULUNGAN -> 35;
			case SeteruIds.SEBAB_HARTA, SeteruIds.SEBAB_DESA -> 25;
			case SeteruIds.SEBAB_GILDA, SeteruIds.SEBAB_TURNAMEN, SeteruIds.SEBAB_ASMARA -> 20;
			case SeteruIds.SEBAB_PERISTIWA -> 10;
			default -> 15;
		};
		Perseteruan f = Tangga.mulai(server, player.getUUID(), MuridInfo.dari(player.getUUID(), murid.soulId()), t, sebab, SeteruIds.SALAH_MEREKA,
				start, 0L, player.blockPosition());
		if (f == null) {
			ctx.getSource().sendFailure(Teks.t("cmd.penuh"));
			return 0;
		}
		ok(ctx, Teks.t("cmd.mulai", f.id, murid.getName(), t.namaTampil(), Teks.sekte(sekte)).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int panas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		int value = IntegerArgumentType.getInteger(ctx, "nilai");
		Tangga.panas(server, f, value - f.panas, "riwayat.panas_uji");
		ok(ctx, Teks.t("cmd.panas", f.id, f.panas, Teks.t("tingkat." + Math.max(0, f.tingkat))).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int naik(CommandContext<CommandSourceStack> ctx, @Nullable String cabang) throws CommandSyntaxException {
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		f.pos = player.blockPosition().asLong();
		Tangga.naik(ctx.getSource().getServer(), f, cabang);
		ok(ctx, Teks.t("cmd.naik", f.id, Teks.t("tingkat." + Math.max(0, f.tingkat)), f.panas).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int temui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		DiscipleEntity murid = cariMurid(ctx);
		TokohSekte t = cariTokoh(ctx, server);
		if (murid == null || t == null) {
			return 0;
		}
		BlockPos pos = player.blockPosition();
		long today = Ingatan.today(server);
		DiscipleEntity body = TubuhSeteru.munculkan(player.level(), pos.offset(3, 0, 2), t, TubuhSeteru.Peran.TEMU, player.getUUID(), null, 2400);
		Adegan.hadap(murid, body);
		t.dijumpai(today, "");
		MuridInfo info = MuridInfo.dari(player.getUUID(), murid.soulId());
		String tahap = Pertemuan.tahapDi(player.level(), pos);
		PertemuanProvider.Pertemuan p = new PertemuanProvider.Pertemuan(server, player.getUUID(), murid.soulId(), List.of(murid.soulId()),
				JenisJalan.BELANJA, tahap, null, pos, today, server.getTickCount() * 31L + t.slot, true);
		Insiden.Hasil h = Insiden.roll(server, p, info, t.sekte, RandomSource.create());
		if (h != null) {
			Insiden.Hasil forced = new Insiden.Hasil(h.sebab(), h.salah(), h.panas(), t);
			Perseteruan f = Tangga.mulai(server, player.getUUID(), info, forced.tokoh(), forced.sebab(), forced.salah(), forced.panas(), 0L, pos);
			ok(ctx, Teks.t("cmd.temui.insiden", t.namaTampil(), Teks.t("sebab." + h.sebab()), f == null ? "-" : f.id).withStyle(ChatFormatting.RED));
		} else {
			AsmaraLintas.ramah(server, player.getUUID(), info, t, RandomSource.create());
			Adegan.ucap(body, "greet");
			ok(ctx, Teks.t("cmd.temui.ramah", t.namaTampil(), t.sikap(murid.soulId())).withStyle(ChatFormatting.GREEN));
		}
		return 1;
	}

	private static int adegan(CommandContext<CommandSourceStack> ctx, String jenis) throws CommandSyntaxException {
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		f.pos = player.blockPosition().asLong();
		switch (jenis) {
			case "duel" -> {
				DuelSeteru.jadwalkan(server, f);
				DuelSeteru.periksa(server, f);
			}
			case "tawuran" -> {
				Tawuran.jadwalkan(server, f);
				Tawuran.periksa(server, f);
			}
			default -> {
				f.sumpahSampai = Ingatan.today(server) + 10L;
				Adegan.sumpah(server, f);
			}
		}
		SeteruData.get(server).setDirty();
		ok(ctx, Teks.t("cmd.adegan", f.id, Teks.t("adegan." + jenis)).withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	private static int patroli(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
		BlockPos a = player.blockPosition().offset((int) (-Math.sin(yaw) * 12.0D), 0, (int) (Math.cos(yaw) * 12.0D));
		BlockPos b = player.blockPosition().offset((int) (-Math.sin(yaw) * 60.0D), 0, (int) (Math.cos(yaw) * 60.0D));
		Patroli.buatDi(player.level().getServer(), f, a, b);
		Patroli.tampilkan(player.level().getServer());
		ok(ctx, Teks.t("cmd.patroli", f.id, Teks.sekte(f.sekte)).withStyle(ChatFormatting.RED));
		return 1;
	}

	private static int asmara(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		DiscipleEntity murid = cariMurid(ctx);
		TokohSekte t = cariTokoh(ctx, server);
		if (murid == null || t == null) {
			return 0;
		}
		Perseteruan f = AsmaraLintas.mulai(server, player.getUUID(), MuridInfo.dari(player.getUUID(), murid.soulId()), t);
		if (f == null) {
			ctx.getSource().sendFailure(Teks.t("cmd.asmara.sudah"));
			return 0;
		}
		ok(ctx, Teks.t("cmd.asmara", f.id, murid.getName(), t.namaTampil(), Teks.sekte(t.sekte)).withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	private static int akhir(CommandContext<CommandSourceStack> ctx, String status) throws CommandSyntaxException {
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		Tangga.akhiri(ctx.getSource().getServer(), f, status, "akhir.uji");
		ok(ctx, Teks.t("cmd.akhir", f.id, Teks.t("status." + status)).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int bersih(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(player.getUUID());
		for (Perseteruan f : aula.seteru) {
			TubuhSeteru.hilangSeteru(f.id);
		}
		data.aula.remove(player.getUUID());
		data.setDirty();
		ok(ctx, Teks.t("cmd.bersih").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int anggaran(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		SeteruData data = SeteruData.get(server);
		int roster = 0;
		for (List<TokohSekte> list : data.roster.values()) {
			roster += list.size();
		}
		int feuds = 0;
		int scenes = 0;
		for (SeteruData.Aula a : data.aula.values()) {
			feuds += a.aktif().size();
			for (Perseteruan f : a.seteru) {
				if (!f.adegan.isEmpty()) {
					scenes++;
				}
			}
		}
		int r = roster;
		int fe = feuds;
		int sc = scenes;
		ok(ctx, Teks.t("cmd.anggaran", TubuhSeteru.jumlah(), SeteruConfig.get().tubuhMaks, r, fe, sc, DuelSessions.count(),
				String.format(Locale.ROOT, "%.3f", Seteru.msRataRata())).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	// ================================================================== player commands

	private static int aksi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "aksi");
		int aksi = -1;
		for (int i = 0; i < SeteruIds.AKSI.length; i++) {
			if (SeteruIds.AKSI[i].equals(name)) {
				aksi = i;
			}
		}
		if ("restui".equals(name)) {
			aksi = SeteruIds.AKSI_RESTUI;
		} else if ("pisahkan".equals(name)) {
			aksi = SeteruIds.AKSI_PISAHKAN;
		}
		Component why = CampurTangan.jalankan(player.level().getServer(), player, f, aksi);
		if (why != null) {
			ctx.getSource().sendFailure(Teks.t("alasan.gagal", why));
			return 0;
		}
		return 1;
	}

	private static int usir(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Perseteruan f = cariSeteru(ctx);
		if (f == null) {
			return 0;
		}
		Component why = Patroli.usir(player.level().getServer(), f, player);
		if (why != null) {
			ctx.getSource().sendFailure(Teks.t("alasan.gagal", why));
			return 0;
		}
		return 1;
	}

	private SeteruCommands() {
	}
}

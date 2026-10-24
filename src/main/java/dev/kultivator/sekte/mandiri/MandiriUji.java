package dev.kultivator.sekte.mandiri;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.SibukMurid;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 39A's test commands (DESIGN 39.10.4), hung under {@code /kultivasi uji perguruan mandiri} through
 * {@link MandiriCommands#uji}:
 * {@code info <murid>}, {@code skor <murid>}, {@code jalan <murid> <id>}, {@code cita <murid> <jenis> <target|->},
 * {@code dorong <murid> <dorongan> <0-100>}, {@code lompat <hari>}, {@code iblis <murid> <0-100>},
 * {@code kebijakan <mode>}, {@code anggaran}, {@code reset <murid>}, plus the purse and bag of 39.6
 * ({@code dompet <murid> <+-n>}, {@code tas <murid> <item> <n>}). The other rows of the table belong to 39B (proyeksi,
 * tembus, hati), 39C (kirim, percepat, jelmaan) and 39E (harian, tokoh), which add their own sub-trees.
 * <p>
 * A disciple is named by its name with spaces written as underscores, among the player's loaded disciples. Every value
 * goes through the same writers the game uses (thresholds, diaries, notices, the money book), with the cause {@code uji}.
 */
final class MandiriUji {

	private static final String P = "kultivator.sekte.mandiri.inti.uji.";

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

	private static final SuggestionProvider<CommandSourceStack> JALAN = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(JalanHidup.values()).map(JalanHidup::id), builder);
	private static final SuggestionProvider<CommandSourceStack> CITA = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(JenisCita.values()).map(JenisCita::id), builder);
	private static final SuggestionProvider<CommandSourceStack> DORONG = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Dorong.values()).map(Dorong::id), builder);
	private static final SuggestionProvider<CommandSourceStack> MODE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(ModeMandiri.values()).map(ModeMandiri::id), builder);
	private static final SuggestionProvider<CommandSourceStack> TARGET = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("-", "kultivator:jade_sword", "golden_core", "nascent_soul"), builder);

	private static boolean installed;

	static void init() {
		if (installed) {
			return;
		}
		installed = true;
		MandiriCommands.uji("info", () -> perMurid("info", MandiriUji::info));
		MandiriCommands.uji("skor", () -> perMurid("skor", MandiriUji::skor));
		MandiriCommands.uji("jalan", () -> Commands.literal("jalan").then(murid()
				.then(Commands.argument("id", StringArgumentType.word()).suggests(JALAN).executes(MandiriUji::jalan))));
		MandiriCommands.uji("cita", () -> Commands.literal("cita").then(murid()
				.then(Commands.argument("jenis", StringArgumentType.word()).suggests(CITA)
						.executes(ctx -> cita(ctx, null))
						.then(Commands.argument("target", StringArgumentType.greedyString()).suggests(TARGET)
								.executes(ctx -> cita(ctx, StringArgumentType.getString(ctx, "target")))))));
		MandiriCommands.uji("dorong", () -> Commands.literal("dorong").then(murid()
				.then(Commands.argument("dorongan", StringArgumentType.word()).suggests(DORONG)
						.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(MandiriUji::dorong)))));
		MandiriCommands.uji("lompat", () -> Commands.literal("lompat")
				.then(Commands.argument("hari", IntegerArgumentType.integer(1, 120)).executes(MandiriUji::lompat)));
		MandiriCommands.uji("iblis", () -> Commands.literal("iblis").then(murid()
				.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(MandiriUji::iblis))));
		MandiriCommands.uji("kebijakan", () -> Commands.literal("kebijakan")
				.executes(ctx -> kebijakan(ctx, null))
				.then(Commands.argument("mode", StringArgumentType.word()).suggests(MODE)
						.executes(ctx -> kebijakan(ctx, StringArgumentType.getString(ctx, "mode")))));
		MandiriCommands.uji("anggaran", () -> Commands.literal("anggaran").executes(MandiriUji::anggaran));
		MandiriCommands.uji("reset", () -> perMurid("reset", MandiriUji::reset));
		MandiriCommands.uji("dompet", () -> Commands.literal("dompet").then(murid()
				.then(Commands.argument("n", IntegerArgumentType.integer(-100000, 100000)).executes(MandiriUji::dompet))));
		MandiriCommands.uji("tas", () -> Commands.literal("tas").then(murid()
				.executes(MandiriUji::tasLihat)
				.then(Commands.argument("item", IdentifierArgument.id())
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 64)).executes(MandiriUji::tas)))));
		MandiriCommands.chips("perguruan mandiri info", "perguruan mandiri skor", "perguruan mandiri jalan",
				"perguruan mandiri cita", "perguruan mandiri dorong", "perguruan mandiri lompat 7", "perguruan mandiri iblis",
				"perguruan mandiri kebijakan", "perguruan mandiri anggaran", "perguruan mandiri dompet",
				"perguruan mandiri tas", "perguruan mandiri reset");
	}

	// ================================================================== tree helpers

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(NAMA);
	}

	private interface Aksi {
		int run(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) throws CommandSyntaxException;
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

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	private static String f2(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static Component nama(@Nullable JalanHidup jalan) {
		return jalan == null ? Component.translatable(P + "belum") : Component.translatable(jalan.kunciNama()).withColor(jalan.warna() & 0xFFFFFF);
	}

	// ================================================================== info

	private static int info(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		CommandSourceStack src = ctx.getSource();
		DiscipleData data = murid.data();
		ServerLevel level = (ServerLevel) murid.level();
		long now = level.getGameTime();
		long hari = Irama.hari(level);
		CompoundTag section = MandiriApi.bagian(data);
		JalanHidup jalan = MandiriApi.jalan(data);
		baris(src, Component.translatable(P + "info.judul", murid.getName(), data.realm().displayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		long sejak = section.getLongOr(MandiriIds.K_JALAN_HARI, -1L);
		baris(src, Component.translatable(P + "info.jalan", nama(jalan), sejak < 0L ? "-" : String.valueOf(hari - sejak),
				MandiriApi.iblis(data)).withStyle(ChatFormatting.YELLOW));
		JalanHidup asal = JalanHidup.byId(section.getStringOr(MandiriIds.K_JALAN_ASAL, ""));
		if (asal != null) {
			baris(src, Component.translatable(P + "info.jalan_asal", nama(asal)).withStyle(ChatFormatting.DARK_PURPLE));
		}

		// Goals.
		List<CitaCita.Cita> cita = CitaCita.aktif(data);
		if (cita.isEmpty()) {
			baris(src, Component.translatable(P + "info.cita_kosong").withStyle(ChatFormatting.GRAY));
		}
		for (CitaCita.Cita c : cita) {
			baris(src, Component.translatable(P + "info.cita", c.prio(), Component.translatable(c.jenis().kunciNama()),
					c.target().isEmpty() ? "-" : c.target(), c.prog(), c.asal().isEmpty() ? "-" : c.asal())
					.withStyle(ChatFormatting.AQUA));
		}

		// Drives.
		int[] dorong = Dorongan.baca(data);
		MutableComponent drives = Component.translatable(P + "info.dorong").withStyle(ChatFormatting.GRAY);
		for (Dorong d : Dorong.values()) {
			drives.append(Component.literal("  "));
			drives.append(Component.translatable(d.kunciNama()).withColor(d.warna() & 0xFFFFFF));
			drives.append(Component.literal(" " + dorong[d.ordinal()]).withStyle(ChatFormatting.WHITE));
		}
		baris(src, drives);

		// The running activity and what holds the disciple.
		Kegiatan k = MandiriApi.kegiatan(murid);
		CompoundTag rec = section.getCompoundOrEmpty(MandiriIds.K_KEGIATAN);
		if (k != null) {
			long sampai = rec.getLongOr(MandiriIds.KG_SAMPAI, -1L);
			String sisa = sampai < 0L ? "~" : String.valueOf(Math.max(0L, (sampai - now) / 20L));
			String acara = rec.getStringOr(MandiriIds.KG_ACARA, "");
			baris(src, Component.translatable(P + "info.kegiatan", Component.translatable(k.kunciNama()), sisa,
					acara.isEmpty() ? "-" : acara).withStyle(ChatFormatting.GREEN));
		} else {
			baris(src, Component.translatable(P + "info.kegiatan_kosong", murid.mode().name().toLowerCase(Locale.ROOT),
					murid.activity()).withStyle(ChatFormatting.GRAY));
		}
		String why = SibukMurid.why(murid);
		boolean boleh = Perencana.bolehDirencana(murid, now);
		baris(src, Component.translatable(P + "info.boleh",
				Component.translatable(boleh ? P + "ya" : P + "tidak").withStyle(boleh ? ChatFormatting.GREEN : ChatFormatting.RED),
				why == null ? Component.literal("-") : SibukMurid.text(why)).withStyle(ChatFormatting.GRAY));
		long bebas = MandiriApi.bebasSampai(data);
		if (bebas > now) {
			baris(src, Component.translatable(P + "info.bebas", bebas == Long.MAX_VALUE ? "~" : String.valueOf((bebas - now) / 1200L))
					.withStyle(ChatFormatting.GOLD));
		}

		// Effective policy.
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		int izin = KebijakanAula.izinEfektif(murid, kebijakan);
		MutableComponent groups = Component.literal("");
		for (IzinKegiatan g : IzinKegiatan.values()) {
			if ((izin & g.bit()) != 0) {
				if (!groups.getSiblings().isEmpty()) {
					groups.append(Component.literal(", "));
				}
				groups.append(Component.translatable(g.kunciNama()));
			}
		}
		boolean tumpang = section.contains(MandiriIds.K_KEBIJAKAN);
		baris(src, Component.translatable(P + "info.kebijakan", Component.translatable(kebijakan.mode().kunciNama()),
				Component.translatable(kebijakan.risiko().kunciNama()), kebijakan.uangSakuPersen(),
				Component.translatable(tumpang ? P + "tumpang" : P + "ikut_aula")).withStyle(ChatFormatting.YELLOW));
		baris(src, Component.translatable(P + "info.izin", groups).withStyle(ChatFormatting.DARK_AQUA));

		// Purse, bag, the decision queue, the catch-up clock.
		baris(src, Component.translatable(P + "info.dompet", MandiriApi.dompet(data), MandiriApi.dompetMaks(data),
				MandiriApi.tas(data, level.registryAccess()).size(), MandiriIds.TAS_SLOT).withStyle(ChatFormatting.GOLD));
		CompoundTag tunggu = section.getCompoundOrEmpty(MandiriIds.K_TUNGGU);
		MutableComponent queue = Component.literal("");
		for (String key : tunggu.keySet()) {
			if (!queue.getSiblings().isEmpty()) {
				queue.append(Component.literal(", "));
			}
			queue.append(Component.literal(key + " " + tunggu.getLongOr(key, -1L)));
		}
		long terakhir = section.getLongOr(MandiriIds.K_TERAKHIR, 0L);
		baris(src, Component.translatable(P + "info.tunggu", queue.getSiblings().isEmpty() ? Component.literal("-") : queue,
				terakhir <= 0L ? "-" : String.valueOf((now - terakhir) / 20L)).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	// ================================================================== skor

	private static final String[] FAKTOR = {"dasar", "jalan", "jam", "dorong", "sifat", "cita", "baru", "izin", "keadaan", "inersia"};

	private static int skor(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		CommandSourceStack src = ctx.getSource();
		long now = murid.level().getGameTime();
		UUID owner = murid.ownerId();
		Kegiatan running = Perencana.berjalan(murid);
		Skor.Hasil hasil = Skor.nilai(murid, running, owner != null && Perencana.bebasPergi(owner, now));
		baris(src, Component.translatable(P + "skor.judul", murid.getName(), hasil.jam, f2(hasil.suhu),
				running == null ? Component.literal("-") : Component.translatable(running.kunciNama()))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Kegiatan[] top = hasil.teratas(3);
		int rank = 0;
		for (Kegiatan k : top) {
			if (k == null) {
				continue;
			}
			rank++;
			int i = k.ordinal();
			BlockPos at = hasil.tempat[i];
			baris(src, Component.translatable(P + "skor.baris", rank, Component.translatable(k.kunciNama()), f2(hasil.skor[i]),
					at == null ? "-" : at.toShortString()).withStyle(ChatFormatting.YELLOW));
			MutableComponent parts = Component.literal("   ").withStyle(ChatFormatting.GRAY);
			for (int f = 0; f < Skor.F_JUMLAH; f++) {
				if (f > 0) {
					parts.append(Component.literal(f == Skor.F_INERSIA ? " + " : " x ").withStyle(ChatFormatting.DARK_GRAY));
				}
				parts.append(Component.translatable(P + "faktor." + FAKTOR[f]));
				parts.append(Component.literal(" " + f2(hasil.faktor[i][f])).withStyle(ChatFormatting.WHITE));
			}
			baris(src, parts);
		}
		if (rank == 0) {
			baris(src, Component.translatable(P + "skor.kosong").withStyle(ChatFormatting.GRAY));
		}
		// Why the rest scored zero (one line, grouped by cause).
		for (int s = 1; s < Skor.SEBAB.length; s++) {
			MutableComponent list = Component.literal("");
			for (Kegiatan k : Kegiatan.values()) {
				if (k == Kegiatan.KOSONG || hasil.sebab[k.ordinal()] != s) {
					continue;
				}
				if (!list.getSiblings().isEmpty()) {
					list.append(Component.literal(", "));
				}
				list.append(Component.translatable(k.kunciNama()));
			}
			if (!list.getSiblings().isEmpty()) {
				baris(src, Component.translatable(P + "skor.nol", Component.translatable(P + "sebab." + Skor.SEBAB[s]), list)
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		if (hasil.teman != null) {
			DiscipleEntity teman = owner == null ? null : Perencana.cari(owner, hasil.teman);
			baris(src, Component.translatable(P + "skor.teman", teman == null ? Component.literal(hasil.teman.toString().substring(0, 8))
					: teman.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return 1;
	}

	// ================================================================== setters

	private static int jalan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		JalanHidup ke = JalanHidup.byId(StringArgumentType.getString(ctx, "id"));
		if (ke == null) {
			ctx.getSource().sendFailure(Component.translatable(P + "salah", StringArgumentType.getString(ctx, "id")));
			return 0;
		}
		DiscipleData data = murid.data();
		if (ke == JalanHidup.IBLIS) {
			// The overlay only comes through temptation (39.3.3): set it at the threshold so every consequence runs.
			JalanIblis.set(murid, Math.max(JalanIblis.IBLIS, MandiriApi.iblis(data)));
		} else {
			if (MandiriApi.jalan(data) == JalanHidup.IBLIS) {
				MandiriApi.bagianTulis(data).remove(MandiriIds.K_JALAN_ASAL);
				JalanIblis.set(murid, Math.min(MandiriApi.iblis(data), JalanIblis.TERGODA - 1));
			}
			PilihJalan.tetapkan(murid, MandiriApi.jalan(data), ke, "uji");
		}
		Perencana.segera(murid);
		baris(ctx.getSource(), Component.translatable(P + "jalan.ok", murid.getName(), nama(MandiriApi.jalan(data)))
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int cita(CommandContext<CommandSourceStack> ctx, @Nullable String target) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		JenisCita jenis = JenisCita.byId(StringArgumentType.getString(ctx, "jenis"));
		if (jenis == null) {
			ctx.getSource().sendFailure(Component.translatable(P + "salah", StringArgumentType.getString(ctx, "jenis")));
			return 0;
		}
		String t = target == null || target.equals("-") ? null : target;
		boolean ok = CitaCita.tambah(murid, jenis, t, 1, "uji");
		if (!ok) {
			ctx.getSource().sendFailure(Component.translatable(P + "cita.gagal", Component.translatable(jenis.kunciNama())));
			return 0;
		}
		Perencana.segera(murid);
		baris(ctx.getSource(), Component.translatable(P + "cita.ok", murid.getName(), Component.translatable(jenis.kunciNama()),
				t == null ? "-" : t).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int dorong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		Dorong d = Dorong.byId(StringArgumentType.getString(ctx, "dorongan"));
		if (d == null) {
			ctx.getSource().sendFailure(Component.translatable(P + "salah", StringArgumentType.getString(ctx, "dorongan")));
			return 0;
		}
		int nilai = IntegerArgumentType.getInteger(ctx, "nilai");
		Dorongan.tulis(murid.data(), d, nilai);
		Perencana.segera(murid);
		baris(ctx.getSource(), Component.translatable(P + "dorong.ok", murid.getName(), Component.translatable(d.kunciNama()),
				Dorongan.baca(murid.data(), d)).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int iblis(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		if (!JalanIblis.aktif()) {
			ctx.getSource().sendFailure(Component.translatable(P + "iblis.mati"));
			return 0;
		}
		JalanIblis.set(murid, IntegerArgumentType.getInteger(ctx, "nilai"));
		baris(ctx.getSource(), Component.translatable(P + "iblis.ok", murid.getName(), MandiriApi.iblis(murid.data()),
				nama(MandiriApi.jalan(murid.data()))).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int dompet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		long applied = MandiriApi.ubahDompet(murid.data(), IntegerArgumentType.getInteger(ctx, "n"), "uji");
		baris(ctx.getSource(), Component.translatable(P + "dompet.ok", murid.getName(), applied, MandiriApi.dompet(murid.data()),
				MandiriApi.dompetMaks(murid.data())).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int tas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		Identifier id = IdentifierArgument.getId(ctx, "item");
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		if (item.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable(P + "salah", id.toString()));
			return 0;
		}
		ItemStack stack = new ItemStack(item.get(), IntegerArgumentType.getInteger(ctx, "n"));
		boolean ok = MandiriApi.masukTas(murid.data(), murid.level().registryAccess(), stack);
		if (!ok) {
			ctx.getSource().sendFailure(Component.translatable(P + "tas.penuh", murid.getName()));
			return 0;
		}
		return tasLihat(ctx);
	}

	private static int tasLihat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity murid = cari(ctx);
		if (murid == null) {
			return 0;
		}
		List<ItemStack> bag = MandiriApi.tas(murid.data(), murid.level().registryAccess());
		MutableComponent list = Component.literal("");
		for (ItemStack s : bag) {
			if (!list.getSiblings().isEmpty()) {
				list.append(Component.literal(", "));
			}
			list.append(Component.literal(s.getCount() + "x ")).append(s.getHoverName());
		}
		baris(ctx.getSource(), Component.translatable(P + "tas.isi", murid.getName(), bag.size(), MandiriIds.TAS_SLOT,
				bag.isEmpty() ? Component.literal("-") : list).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	// ================================================================== lompat, kebijakan, anggaran, reset

	/** Runs the catch-up for every loaded disciple of the player as if {@code hari} days had passed. */
	private static int lompat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int hari = IntegerArgumentType.getInteger(ctx, "hari");
		if (!MandiriApi.aktif()) {
			ctx.getSource().sendFailure(Component.translatable(P + "mati"));
			return 0;
		}
		int n = 0;
		double xp = 0.0D;
		long nb = 0L;
		for (DiscipleEntity murid : Disciples.loaded(player.getUUID())) {
			if (murid.role() != DiscipleEntity.Role.DISCIPLE || !murid.isAlive()) {
				continue;
			}
			DiscipleData data = murid.data();
			long today = Irama.hari(murid.level());
			long before = MandiriApi.dompet(data);
			// Pocket money and the daily step count from these days back.
			MandiriApi.bagianTulis(data).putLong(MandiriIds.K_SAKU_HARI, today - hari);
			Perencana.batal(murid, true);
			xp += KejarKetinggalan.jalankan(murid, hari * 24000L);
			CitaCita.perbarui(murid);
			nb += MandiriApi.dompet(data) - before;
			Perencana.segera(murid);
			n++;
		}
		int count = n;
		String gain = String.format(Locale.ROOT, "%.0f", xp);
		long money = nb;
		baris(ctx.getSource(), Component.translatable(P + "lompat.ok", hari, count, gain, money).withStyle(ChatFormatting.GREEN));
		return count;
	}

	private static int kebijakan(CommandContext<CommandSourceStack> ctx, @Nullable String id) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Kebijakan aula = MandiriApi.kebijakanAula(player.getUUID());
		if (id != null) {
			ModeMandiri mode = ModeMandiri.byId(id, null);
			if (mode == null) {
				ctx.getSource().sendFailure(Component.translatable(P + "salah", id));
				return 0;
			}
			KebijakanAula.setAula(server, player.getUUID(), aula.withMode(mode), player);
			aula = MandiriApi.kebijakanAula(player.getUUID());
		}
		Kebijakan shown = aula;
		baris(ctx.getSource(), Component.translatable(P + "kebijakan.aula", Component.translatable(shown.mode().kunciNama()),
				Component.translatable(shown.risiko().kunciNama()), shown.uangSakuPersen(), shown.tungguTerobosanHari(),
				shown.tungguDaoHari(), Integer.bitCount(shown.izin())).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** The 39.9 gauge: planner cost, queues, walkers per hall against {@code gerakMaks}, reservations. */
	private static int anggaran(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		MandiriConfig config = MandiriConfig.get();
		long now = player.level().getGameTime();
		baris(ctx.getSource(), Component.translatable(P + "anggaran.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		baris(ctx.getSource(), Component.translatable(P + "anggaran.waktu", f2(Perencana.rataMikro()), Perencana.nilaiTickIni(),
				config.penilaianPerTick).withStyle(Perencana.rataMikro() > 500.0D ? ChatFormatting.RED : ChatFormatting.GREEN));
		baris(ctx.getSource(), Component.translatable(P + "anggaran.antre", Perencana.antreNilai(), Perencana.antreKejar(),
				Perencana.totalNilai(), Perencana.totalKejar()).withStyle(ChatFormatting.YELLOW));
		List<DiscipleEntity> mine = Perencana.dimuat(owner);
		int milik = 0;
		for (DiscipleEntity d : mine) {
			if (Perencana.milikku(d)) {
				milik++;
			}
		}
		baris(ctx.getSource(), Component.translatable(P + "anggaran.aula", Perencana.jumlahDimuat(), mine.size(), milik,
				AnggaranGerak.berjalan(owner, now), config.gerakMaks, TempatCache.jumlahPesanan(owner)).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** Removes {@code extra.mandiri} (and the hall's overlay copy) so the disciple starts DESIGN 39 afresh. */
	private static int reset(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		Perencana.batal(murid, true);
		KebijakanAula.setTumpang(murid, null);
		UUID owner = murid.ownerId();
		if (owner != null) {
			TempatCache.lepas(owner, murid.soulId());
		}
		murid.data().extra().remove(MandiriIds.EXTRA);
		SosokApi.setJalan(murid, null);
		Perencana.segera(murid);
		baris(ctx.getSource(), Component.translatable(P + "reset.ok", murid.getName()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private MandiriUji() {
	}
}

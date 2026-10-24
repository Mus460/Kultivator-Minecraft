package dev.kultivator.sekte.mandiri.kerja;

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
import dev.kultivator.disciple.power.NatalMandiri;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.mandiri.MandiriCommands;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.talisman.Inscribe;
import dev.kultivator.talisman.Talismans;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The test commands of module 39D (DESIGN 39.10.4), hung on {@code MandiriCommands} as
 * {@code uji perguruan mandiri kerja ...}:
 * <ul>
 *   <li>{@code info <murid>}: alchemy level and hours, talisman level and xp, the training-ground record, the craft grade,
 *   the natal treasure (form, folds, the next fold's chance and PK), today's practice points, the running activity.</li>
 *   <li>{@code mulai <murid> <latih|tanding|meramu|jimat|kebun|tambang|pos|formasi|teh>}: starts that activity now through
 *   the planner (the partner of a bout or a tea is the nearest free hall-mate).</li>
 *   <li>{@code bahan <murid> <meramu|jimat|natal>}: puts a test kit into the bag (money book reason {@code uji}).</li>
 *   <li>{@code natal <murid>}: today's natal attempt now.</li>
 *   <li>{@code jimat <murid> <0-10>}, {@code alkimia <murid> <0-10>}: sets a skill level.</li>
 * </ul>
 */
final class KerjaCommands {

	private static final String P = DataKerja.P + "cmd.";
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

	private static final String[] KEGIATAN = {"latih", "tanding", "meramu", "jimat", "kebun", "tambang", "pos", "formasi", "teh"};

	static void init() {
		if (installed) {
			return;
		}
		installed = true;
		MandiriCommands.uji("kerja", () -> Commands.literal("kerja")
				.then(Commands.literal("info").then(murid().executes(ctx -> run(ctx, KerjaCommands::info))))
				.then(Commands.literal("mulai").then(murid().then(Commands.argument("kegiatan", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(KEGIATAN, builder))
						.executes(ctx -> run(ctx, KerjaCommands::mulai)))))
				.then(Commands.literal("bahan").then(murid().then(Commands.argument("jenis", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"meramu", "jimat", "natal"}, builder))
						.executes(ctx -> run(ctx, KerjaCommands::bahan)))))
				.then(Commands.literal("natal").then(murid().executes(ctx -> run(ctx, KerjaCommands::natal))))
				.then(Commands.literal("jimat").then(murid().then(Commands.argument("level", IntegerArgumentType.integer(0, Inscribe.MAX_LEVEL))
						.executes(ctx -> run(ctx, KerjaCommands::jimat)))))
				.then(Commands.literal("alkimia").then(murid().then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
						.executes(ctx -> run(ctx, KerjaCommands::alkimia))))));
		MandiriCommands.chips("perguruan mandiri kerja info", "perguruan mandiri kerja mulai", "perguruan mandiri kerja bahan",
				"perguruan mandiri kerja natal", "perguruan mandiri kerja jimat", "perguruan mandiri kerja alkimia");
	}

	// ================================================================== helpers

	private interface Aksi {
		int run(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) throws CommandSyntaxException;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(NAMA);
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Aksi aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "murid");
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && e.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				return aksi.run(ctx, e);
			}
		}
		ctx.getSource().sendFailure(Component.translatable(P + "tidak_ada", name));
		return 0;
	}

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	// ================================================================== info

	private static int info(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		CommandSourceStack source = ctx.getSource();
		DiscipleData data = murid.data();
		CompoundTag own = DataKerja.baca(data);
		baris(source, Component.translatable(P + "info.judul", murid.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		baris(source, Component.translatable(P + "info.alkimia", DataKerja.levelAlkimia(data),
				MandiriApi.statistik(data, MandiriIds.S_JAM_MERAMU), Math.round(dev.kultivator.alchemy.AlchemyFurnace.successChance(
						DataKerja.levelAlkimia(data), false, data.dao() == dev.kultivator.data.Dao.FIRE) * 100.0D)).withStyle(ChatFormatting.GRAY));
		baris(source, Component.translatable(P + "info.jimat", DataKerja.levelJimat(data), DataKerja.jimatXp(data),
				own.getIntOr(DataKerja.JIMAT_BUAT, 0), Math.round(Inscribe.chance(DataKerja.levelJimat(data)) * 100.0D)).withStyle(ChatFormatting.GRAY));
		baris(source, Component.translatable(P + "info.tanding", DataKerja.elo(data), own.getIntOr(DataKerja.MENANG, 0),
				own.getIntOr(DataKerja.KALAH, 0), own.getIntOr(DataKerja.SERI, 0)).withStyle(ChatFormatting.GRAY));
		CompoundTag latih = own.getCompoundOrEmpty(DataKerja.LATIH);
		long hari = DataKerja.hari(murid.level().getServer());
		int poin = latih.getLongOr("hari", -1L) == hari ? latih.getIntOr("poin", 0) : 0;
		baris(source, Component.translatable(P + "info.latih", poin, LatihJurus.POIN_HARI, MandiriApi.statistik(data, MandiriIds.S_KARYA))
				.withStyle(ChatFormatting.GRAY));
		int form = NatalMandiri.form(data);
		if (form < 0) {
			baris(source, Component.translatable(P + "info.natal_belum").withStyle(ChatFormatting.GRAY));
		} else {
			baris(source, Component.translatable(P + "info.natal", Component.translatable(NatalMandiri.formKey(form)), NatalMandiri.folds(data),
					NatalMandiri.maxFolds(data), Math.round(NatalMandiri.chanceNext(murid) * 100.0D), NatalMandiri.foldPk(data),
					SectRanks.pk(data)).withStyle(ChatFormatting.GRAY));
		}
		Kegiatan running = MandiriApi.kegiatan(murid);
		baris(source, Component.translatable(P + "info.kegiatan", running == null ? Component.translatable(P + "info.tidak_ada")
				: Component.translatable(running.kunciNama()), murid.activity()).withStyle(ChatFormatting.DARK_AQUA));
		return 1;
	}

	// ================================================================== mulai

	private static int mulai(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		String id = StringArgumentType.getString(ctx, "kegiatan").toLowerCase(Locale.ROOT);
		Kegiatan k = switch (id) {
			case "latih" -> Kegiatan.LATIH_JURUS;
			case "tanding" -> Kegiatan.TANDING;
			case "meramu" -> Kegiatan.MERAMU;
			case "jimat" -> Kegiatan.JIMAT;
			case "kebun" -> Kegiatan.KEBUN;
			case "tambang" -> Kegiatan.TAMBANG;
			case "pos" -> Kegiatan.POS_TUGAS;
			case "formasi" -> Kegiatan.JAGA_FORMASI;
			case "teh" -> Kegiatan.SOSIAL;
			default -> null;
		};
		if (k == null) {
			ctx.getSource().sendFailure(Component.translatable(P + "mulai.tidak_dikenal", id));
			return 0;
		}
		UUID teman = null;
		if (k == Kegiatan.TANDING || k == Kegiatan.SOSIAL) {
			DiscipleEntity best = null;
			for (DiscipleEntity other : Disciples.loaded(murid.ownerId())) {
				if (other != murid && other.level() == murid.level() && other.distanceToSqr(murid) < 24.0D * 24.0D && Tanding.bebasDiajak(other)
						&& (best == null || other.distanceToSqr(murid) < best.distanceToSqr(murid))) {
					best = other;
				}
			}
			if (best == null) {
				ctx.getSource().sendFailure(Component.translatable(P + "mulai.tanpa_teman"));
				return 0;
			}
			teman = best.soulId();
		}
		int lama = Math.max(200, k.blokMaks());
		boolean ok = MandiriApi.mulaiKegiatan(murid, k, lama, null, teman);
		if (ok) {
			ctx.getSource().sendSuccess(() -> Component.translatable(P + "mulai.ok", murid.getName(), Component.translatable(k.kunciNama()))
					.withStyle(ChatFormatting.GREEN), false);
			return 1;
		}
		ctx.getSource().sendFailure(Component.translatable(P + "mulai.gagal", murid.getName(), Component.translatable(k.kunciNama())));
		return 0;
	}

	// ================================================================== bahan

	private static int bahan(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		String jenis = StringArgumentType.getString(ctx, "jenis").toLowerCase(Locale.ROOT);
		List<ItemStack> kit = new ArrayList<>();
		switch (jenis) {
			case "meramu" -> {
				kit.add(new ItemStack(Items.GLOWSTONE_DUST, 2));
				kit.add(new ItemStack(Items.SUGAR, 2));
				kit.add(new ItemStack(Items.GLOW_BERRIES, 2));
				kit.add(new ItemStack(Items.GLISTERING_MELON_SLICE, 1));
				kit.add(new ItemStack(Items.GOLDEN_CARROT, 1));
				kit.add(new ItemStack(Items.SWEET_BERRIES, 1));
				kit.add(new ItemStack(Items.KELP, 1));
			}
			case "jimat" -> {
				kit.add(new ItemStack(Talismans.BLANK, 3));
				kit.add(new ItemStack(Items.GOLD_INGOT, 1));
				kit.add(new ItemStack(Items.SUGAR, 1));
				kit.add(new ItemStack(Items.COPPER_INGOT, 1));
				kit.add(new ItemStack(Items.PAPER, 3));
				kit.add(new ItemStack(HartaItems.SPIRIT_GRASS, 1));
				kit.add(new ItemStack(Items.DYE.red(), 1));
			}
			case "natal" -> {
				kit.add(new ItemStack(Items.DIAMOND, 1));
				kit.add(new ItemStack(NatalMandiri.feather(), 16));
				SectRanks.addPk(murid.data(), 400, DataKerja.P + "pk.uji");
			}
			default -> {
				ctx.getSource().sendFailure(Component.translatable(P + "bahan.tidak_dikenal", jenis));
				return 0;
			}
		}
		int masuk = 0;
		for (ItemStack stack : kit) {
			if (MandiriApi.masukTas(murid.data(), murid.level().registryAccess(), stack)) {
				masuk++;
			}
		}
		int total = masuk;
		ctx.getSource().sendSuccess(() -> Component.translatable(P + "bahan.ok", murid.getName(), total, kit.size())
				.withStyle(total == kit.size() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return total > 0 ? 1 : 0;
	}

	// ================================================================== natal, skills

	private static int natal(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		String hasil = NatalSendiri.paksa(murid);
		ctx.getSource().sendSuccess(() -> Component.translatable(P + "natal." + hasil, murid.getName())
				.withStyle("ok".equals(hasil) ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return "ok".equals(hasil) ? 1 : 0;
	}

	private static int jimat(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		int level = IntegerArgumentType.getInteger(ctx, "level");
		DataKerja.tulis(murid.data()).putInt(DataKerja.JIMAT_XP, Inscribe.xpForLevel(level));
		ctx.getSource().sendSuccess(() -> Component.translatable(P + "jimat.ok", murid.getName(), level).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int alkimia(CommandContext<CommandSourceStack> ctx, DiscipleEntity murid) {
		int level = IntegerArgumentType.getInteger(ctx, "level");
		long now = MandiriApi.statistik(murid.data(), MandiriIds.S_JAM_MERAMU);
		MandiriApi.tambahStatistik(murid.data(), MandiriIds.S_JAM_MERAMU, level * 40L - now);
		ctx.getSource().sendSuccess(() -> Component.translatable(P + "alkimia.ok", murid.getName(), level).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}

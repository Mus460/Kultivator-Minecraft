package dev.kultivator.sekte.mandiri.cerita;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sekte.mandiri.Irama;
import dev.kultivator.sekte.mandiri.MandiriCommands;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.HasilJalan;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The commands of module 39E (DESIGN 39.10.4). Player ({@code /kultivasi perguruan mandiri ...}): {@code papan} (the
 * Jalan Hidup Murid board, also the {@code [Lihat]} of every notice) and {@code harian <murid>} (the diary book). Test
 * (op, {@code /kultivasi uji perguruan mandiri ...}): {@code harian <murid> [uji]} (five sample entries of every kind with
 * real names, prices and weather, then the book), {@code tokoh daftar}, {@code tokoh buat <peran> <murid>} (one step of
 * that arc now), {@code pikir <murid> <ikon>} (a bubble), {@code toast <jenis>} (a notice through the real path, Sibuk
 * included) and {@code ringkas} (the "while you were away" summary with three sample lines).
 */
final class CeritaCommands {

	private static final String P = "kultivator.sekte.mandiri.cerita.uji.";

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
	private static final SuggestionProvider<CommandSourceStack> PERAN = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Tokoh.Peran.values()).map(p -> p.name().toLowerCase(Locale.ROOT)), builder);
	private static final SuggestionProvider<CommandSourceStack> JENIS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Kebijakan.Pemberitahuan.values()).map(p -> p.name().toLowerCase(Locale.ROOT)), builder);

	static void init() {
		MandiriCommands.pemain("papan", () -> Commands.literal("papan").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			// "buka": the client opens the board (the chat [Lihat] link and the command), a refresh only updates it
			net.minecraft.nbt.CompoundTag data = PapanMandiri.bangun(player, player.getUUID());
			data.putBoolean("buka", true);
			MandiriPayloads.kirim(player, new MandiriPayloads.Papan(data));
			return 1;
		}));
		MandiriCommands.pemain("harian", () -> Commands.literal("harian").then(murid().executes(ctx -> buka(ctx, false))));
		MandiriCommands.uji("harian", () -> Commands.literal("harian").then(murid().executes(ctx -> buka(ctx, false))
				.then(Commands.literal("uji").executes(ctx -> buka(ctx, true)))));
		MandiriCommands.uji("tokoh", () -> Commands.literal("tokoh")
				.then(Commands.literal("daftar").executes(CeritaCommands::daftar))
				.then(Commands.literal("buat").then(Commands.argument("peran", StringArgumentType.word()).suggests(PERAN)
						.then(murid().executes(CeritaCommands::buat)))));
		MandiriCommands.uji("pikir", () -> Commands.literal("pikir").then(murid()
				.then(Commands.argument("ikon", IntegerArgumentType.integer(0, MandiriPayloads.PIKIR_IKON - 1)).executes(ctx -> {
					DiscipleEntity e = cari(ctx);
					if (e == null) {
						return 0;
					}
					MandiriPayloads.pikir(e, IntegerArgumentType.getInteger(ctx, "ikon"), "kultivator.sekte.mandiri.cerita.pikir.uji", 100);
					return 1;
				}))));
		MandiriCommands.uji("toast", () -> Commands.literal("toast").then(Commands.argument("jenis", StringArgumentType.word()).suggests(JENIS)
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Kebijakan.Pemberitahuan jenis = Kebijakan.Pemberitahuan.TEROBOSAN;
					for (Kebijakan.Pemberitahuan p : Kebijakan.Pemberitahuan.values()) {
						if (p.name().equalsIgnoreCase(StringArgumentType.getString(ctx, "jenis"))) {
							jenis = p;
						}
					}
					Pemberitahuan.kirim(player.level().getServer(), player.getUUID(), jenis,
							Component.translatable(P + "toast." + jenis.name().toLowerCase(Locale.ROOT)), jenis == Kebijakan.Pemberitahuan.TEROBOSAN);
					return 1;
				})));
		MandiriCommands.uji("ringkas", () -> Commands.literal("ringkas").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			MinecraftServer server = player.level().getServer();
			UUID owner = player.getUUID();
			Pemberitahuan.kirimTunda(server, owner, Kebijakan.Pemberitahuan.TEROBOSAN, Component.translatable(P + "ringkas.1"), true);
			Pemberitahuan.kirimTunda(server, owner, Kebijakan.Pemberitahuan.PULANG, Component.translatable(P + "ringkas.2"), false);
			Pemberitahuan.kirimTunda(server, owner, Kebijakan.Pemberitahuan.TEKNIK, Component.translatable(P + "ringkas.3"), false);
			Pemberitahuan.masuk(player);
			return 1;
		}));
		MandiriCommands.chips("perguruan mandiri papan", "perguruan mandiri harian", "perguruan mandiri tokoh daftar",
				"perguruan mandiri toast", "perguruan mandiri ringkas");
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(NAMA);
	}

	private static DiscipleEntity cari(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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

	/** Opens the diary book; {@code contoh}: first five sample entries of every kind. */
	private static int buka(CommandContext<CommandSourceStack> ctx, boolean contoh) throws CommandSyntaxException {
		DiscipleEntity e = cari(ctx);
		if (e == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (contoh) {
			contoh(player, e);
		}
		MandiriPayloads.Harian page = BukuHarian.halaman(player, player.getUUID(), e.soulId());
		if (page != null) {
			page.data().putBoolean("buka", true);
			MandiriPayloads.kirim(player, page);
		}
		return 1;
	}

	/** The five sample entries of DESIGN 39.8.1 (shopping and haggling, a full-moon breakthrough watched, bandits, ...). */
	private static void contoh(ServerPlayer player, DiscipleEntity e) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		UUID soul = e.soulId();
		ServerLevel level = (ServerLevel) e.level();
		long today = Irama.hari(level);
		long town = Towns.near(level, e.blockPosition(), 4000);
		if (town == 0L) {
			town = e.blockPosition().asLong() ^ 0x5DEECE66DL;
		}
		String friend = "@murid:" + Math.floorMod(soul.hashCode() + 7, dev.kultivator.disciple.DiscipleNames.TOTAL);
		String senior = "@murid:" + Math.floorMod(soul.hashCode() + 19, dev.kultivator.disciple.DiscipleNames.TOTAL);
		String merchant = "@kota:" + Math.floorMod(soul.hashCode() * 31 + 5, Integer.MAX_VALUE) + ":l";
		List<EntriHarian> list = List.of(
				new EntriHarian(Math.max(0L, today - 36), 17, "belanja", "contoh.belanja", List.of(DaftarTokoh.argKota(town), merchant,
						"i:kultivator:foundation_pill", "n:72", "n:61", "ctx:hujan:biasa"), EntriHarian.Suasana.SENANG, false),
				new EntriHarian(Math.max(0L, today - 19), 21, "jaga", "contoh.purnama", List.of(senior, "r:golden_core", "ctx:cerah:purnama"),
						EntriHarian.Suasana.BANGGA, true),
				new EntriHarian(Math.max(0L, today - 12), 6, "jalan.begal", "contoh.begal", List.of(friend, "n:3", "ctx:cerah:biasa"),
						EntriHarian.Suasana.SENANG, false),
				new EntriHarian(Math.max(0L, today - 5), 19, "teh", "contoh.teh", List.of(friend, "ctx:salju:biasa"), EntriHarian.Suasana.TENANG,
						false),
				new EntriHarian(today, 9, "tembus", "tembus.berhasil", List.of("r:foundation", "n:2", "ctx:badai:baru"), EntriHarian.Suasana.BANGGA,
						true));
		for (EntriHarian entry : list) {
			MandiriApi.simpanHarian(server, owner, soul, entry);
		}
		ctx(player, Component.translatable(P + "harian.ok", e.getName()));
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<Tokoh> all = DaftarTokoh.semua(player.level().getServer(), player.getUUID());
		ctx(player, Component.translatable(P + "tokoh.judul", all.size(), DaftarTokoh.MAKS).withStyle(ChatFormatting.GOLD));
		for (Tokoh t : all) {
			ctx(player, Component.literal(" • ").append(DaftarTokoh.nama(t).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" · ")).append(Component.translatable(t.peran().kunciNama()))
					.append(Component.translatable(P + "tokoh.baris", t.jumpa(), t.hari(), t.situs())).withStyle(ChatFormatting.GRAY));
		}
		return all.size();
	}

	private static int buat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity e = cari(ctx);
		if (e == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		long town = Towns.near((ServerLevel) e.level(), e.blockPosition(), 4000);
		if (town == 0L) {
			town = e.blockPosition().asLong() ^ 0x5DEECE66DL;
		}
		Tokoh.Peran peran = Tokoh.Peran.byName(StringArgumentType.getString(ctx, "peran"), Tokoh.Peran.PENGEMBARA);
		switch (peran) {
			case PEDAGANG -> BusurTokoh.belanja(server, owner, e.soulId(), town, "kultivator:healing_pill", 2, 18L);
			case PENCURI -> BusurTokoh.kota(server, owner, e.soulId(), town, "copet_lolos");
			case PENGEMBARA -> BusurTokoh.kota(server, owner, e.soulId(), town, "pengembara");
			case AHLI_TUA -> BusurTokoh.kota(server, owner, e.soulId(), town, "ahli_tua");
			case SAINGAN -> BusurSaingan.kota(server, owner, e.soulId(), town, "provokasi:" + (e.getRandom().nextBoolean() ? "menang" : "kalah"));
			case KENALAN_HATI -> BusurAsmara.kota(server, owner, e.soulId(), town, "asmara");
			case KEPALA_DESA -> BusurTokoh.tiba(server, owner, e.soulId(), new HasilJalan("uji", JenisJalan.DESA, "uji_desa", List.of(), List.of(),
					0L, JenisLuka.TIDAK, List.of(), 0.0D, 0, true));
			case SAHABAT_LUAR -> {
				ctx.getSource().sendFailure(Component.translatable(P + "tokoh.milik_39f"));
				return 0;
			}
		}
		ctx(player, Component.translatable(P + "tokoh.ok", Component.translatable(peran.kunciNama()), e.getName()));
		return 1;
	}

	private static void ctx(ServerPlayer player, Component line) {
		player.sendSystemMessage(line);
	}

	private CeritaCommands() {
	}
}

package dev.kultivator.dunia.tempat.tema;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.TitikTemplat;
import dev.kultivator.dunia.tempat.cerita.CeritaReload;
import dev.kultivator.dunia.tempat.cerita.Quest;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.KontrakMuatan;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 38TK inti's words under {@code /kultivasi uji tema} (DESIGN 38.T.13.2; op only, registered with
 * {@link TemaCommands#daftar}): {@code rencana putusan cari distrik lot cek acara dagang kontrak alur model fx kepadatan}.
 * None of them changes a block, the weather or the time; {@code cari}/{@code distrik} teleport the caller like
 * {@code /tp}, {@code model} shows a 30 s line-up of every themed entity (AI off, then removed), {@code fx} sends
 * preview packets of {@code TemaNet} for 20 s.
 */
public final class TemaUji {

	private static final List<String> ARTI_SATELIT = List.of("reruntuhan", "pos_pemburu");
	private static final List<String> FX = List.of("badai", "lentera", "cahaya", "awan", "kabut", "suar", "riak", "ombak");
	/** Ticks of the model line-up and the fx previews. */
	private static final int PAMER_TICK = 600;
	private static final int FX_TICK = 400;
	/** Far candidates listed by {@code rencana} lie within this radius of the caller. */
	private static final int RENCANA_JARAK = 8000;

	private record Jadwal(long tick, Runnable kerja) {
	}

	private static final List<Jadwal> JADWAL = new ArrayList<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaCommands.daftar("inti", TemaUji::rencana);
		TemaCommands.daftar("inti", TemaUji::putusan);
		TemaCommands.daftar("inti", TemaUji::cari);
		TemaCommands.daftar("inti", TemaUji::distrik);
		TemaCommands.daftar("inti", () -> Commands.literal("lot").executes(TemaUji::lot));
		TemaCommands.daftar("inti", () -> Commands.literal("cek").executes(TemaUji::cek));
		TemaCommands.daftar("inti", TemaUji::acara);
		TemaCommands.daftar("inti", TemaUji::dagang);
		TemaCommands.daftar("inti", TemaUji::kontrak);
		TemaCommands.daftar("inti", TemaUji::alur);
		TemaCommands.daftar("inti", () -> Commands.literal("model").executes(TemaUji::model));
		TemaCommands.daftar("inti", TemaUji::fx);
		TemaCommands.daftar("inti", () -> Commands.literal("kepadatan").executes(ctx -> kepadatan(ctx, 4000))
				.then(Commands.argument("radius", IntegerArgumentType.integer(500, 20000)).executes(ctx ->
						kepadatan(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
		ServerTickEvents.END_SERVER_TICK.register(TemaUji::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Jadwal j : List.copyOf(JADWAL)) {
				j.kerja().run();
			}
			JADWAL.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (JADWAL.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<Jadwal> due = new ArrayList<>();
		JADWAL.removeIf(j -> {
			if (j.tick() <= now) {
				due.add(j);
				return true;
			}
			return false;
		});
		for (Jadwal j : due) {
			try {
				j.kerja().run();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Themed test follow-up failed", exception);
			}
		}
	}

	private static void nanti(ServerLevel level, int ticks, Runnable kerja) {
		JADWAL.add(new Jadwal(level.getServer().overworld().getGameTime() + ticks, kerja));
	}

	// ================================================================================================ helpers

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	private static void baris(CommandSourceStack source, String text, ChatFormatting color) {
		source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
	}

	private static CompletableFuture<Suggestions> saranTema(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		List<String> ids = new ArrayList<>();
		for (Tema t : Tema.values()) {
			ids.add(t.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	}

	private static CompletableFuture<Suggestions> saranCari(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		List<String> ids = new ArrayList<>();
		for (Tema t : Tema.values()) {
			ids.add(t.id());
		}
		ids.addAll(ARTI_SATELIT);
		return SharedSuggestionProvider.suggest(ids, builder);
	}

	private static @Nullable Tema tema(CommandContext<CommandSourceStack> ctx, String name) {
		Tema tema = Tema.dari(StringArgumentType.getString(ctx, name));
		if (tema == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.tema_salah", StringArgumentType.getString(ctx, name))
					.withStyle(ChatFormatting.RED));
		}
		return tema;
	}

	/** The town the caller stands in, else the nearest born town of {@code tema} (null = any) within 3000. */
	private static @Nullable TemaKota.Kota kotaSini(ServerPlayer player, @Nullable Tema tema) {
		TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
		if (kota != null && (tema == null || kota.tema() == tema)) {
			return kota;
		}
		return TemaKota.terdekat(player.level(), player.blockPosition(), tema, 3000);
	}

	private static void teleport(ServerPlayer player, int x, int z, int nearY) {
		ServerLevel level = player.level().getServer().overworld();
		level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
		int y = Math.max(nearY, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		player.teleportTo(level, x + 0.5D, y + 1.0D, z + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
	}

	private static long jarak(ServerPlayer player, int x, int z) {
		return Math.round(Math.hypot(player.getX() - x, player.getZ() - z));
	}

	// ================================================================================================ rencana

	private static LiteralArgumentBuilder<CommandSourceStack> rencana() {
		return Commands.literal("rencana")
				.executes(ctx -> rencana(ctx, null))
				.then(Commands.literal("segar").executes(ctx -> {
					RencanaKotaTema.lupakan();
					TemaKota.lupakan();
					baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.rencana.segar").withStyle(ChatFormatting.GREEN));
					return 1;
				}))
				.then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranTema).executes(ctx -> {
					Tema t = tema(ctx, "tema");
					return t == null ? 0 : rencana(ctx, t);
				}));
	}

	private static int rencana(CommandContext<CommandSourceStack> ctx, @Nullable Tema only) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level().getServer().overworld();
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		RencanaKotaTema.Plan plan = RencanaKotaTema.plan(level.getSeed());
		CommandSourceStack source = ctx.getSource();
		for (Tema t : Tema.values()) {
			if (only != null && t != only) {
				continue;
			}
			List<RencanaKotaTema.Calon> list = plan.calon().getOrDefault(t, List.of());
			baris(source, Component.translatable("kultivator.tema.cmd.rencana.judul", t.id(), list.size(),
					TemaConfig.worldgen(t) ? "on" : "off").withStyle(Style(t)));
			RencanaKotaTema.Calon first = null;
			Map<String, Integer> reasons = new java.util.TreeMap<>();
			int ring = 0;
			for (RencanaKotaTema.Calon c : list) {
				if (!c.pertama()) {
					continue;
				}
				ring++;
				Putusan p = RencanaKotaTema.akhirCalon(sumber, c);
				if (p.layak() && first == null) {
					first = c;
				} else if (!p.layak()) {
					reasons.merge(kata(p.alasan()), 1, Integer::sum);
				}
			}
			if (first != null) {
				baris(source, String.format(Locale.ROOT, "  cincin: %d calon, kota pertama #%d di (%d, %d), %d blok dari pusat, %d blok darimu",
						ring, first.index(), first.x(), first.z(), Math.round(Math.hypot(first.x(), first.z())), jarak(player, first.x(), first.z())),
						ChatFormatting.GREEN);
			} else {
				baris(source, "  cincin: " + ring + " calon, TANPA jaminan; alasan " + reasons, ChatFormatting.YELLOW);
			}
			int shown = 0;
			for (RencanaKotaTema.Calon c : list) {
				if (c.pertama() || jarak(player, c.x(), c.z()) > RENCANA_JARAK || shown >= 10) {
					continue;
				}
				shown++;
				Putusan p = RencanaKotaTema.akhirCalon(sumber, c);
				baris(source, String.format(Locale.ROOT, "  sel #%d (%d, %d) %d blok: %s", c.index(), c.x(), c.z(), jarak(player, c.x(), c.z()),
						p.layak() ? "LAYAK, dasarY " + p.dasarY() + ", " + p.alasan() : p.alasan()), p.layak() ? ChatFormatting.AQUA : ChatFormatting.GRAY);
			}
		}
		Kandidat pos = null;
		for (Kandidat k : RencanaPosPemburu.INSTANCE.kandidat(level.getSeed())) {
			if (RencanaPosPemburu.akhir(sumber, k).layak() && (pos == null || jarak(player, k.pusatX(), k.pusatZ()) < jarak(player, pos.pusatX(), pos.pusatZ()))) {
				pos = k;
			}
		}
		if (pos != null && (only == null || only == Tema.IBLIS)) {
			baris(source, String.format(Locale.ROOT, "pos_pemburu terdekat (%d, %d), %d blok", pos.pusatX(), pos.pusatZ(), jarak(player, pos.pusatX(), pos.pusatZ())),
					ChatFormatting.WHITE);
		}
		return 1;
	}

	private static ChatFormatting Style(Tema t) {
		return switch (t) {
			case PELABUHAN -> ChatFormatting.AQUA;
			case OASIS -> ChatFormatting.GOLD;
			case AIR -> ChatFormatting.DARK_AQUA;
			case GUNUNG -> ChatFormatting.GRAY;
			case IBLIS -> ChatFormatting.DARK_RED;
		};
	}

	private static String kata(String alasan) {
		int space = alasan.indexOf(' ');
		return space < 0 ? alasan : alasan.substring(0, space);
	}

	// ================================================================================================ putusan

	private static LiteralArgumentBuilder<CommandSourceStack> putusan() {
		return Commands.literal("putusan").then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranTema).executes(ctx -> {
			Tema t = tema(ctx, "tema");
			if (t == null) {
				return 0;
			}
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			ServerLevel level = player.level().getServer().overworld();
			PutusanTema.Hasil h = PutusanTema.hitung(TempatZones.sumber(level), t, player.getBlockX(), player.getBlockZ());
			CommandSourceStack source = ctx.getSource();
			baris(source, Component.translatable("kultivator.tema.cmd.putusan.judul", t.id(), player.getBlockX(), player.getBlockZ()).withStyle(Style(t)));
			baris(source, "  aturan: " + PutusanTema.aturan().get(t.id()), ChatFormatting.DARK_GRAY);
			baris(source, "  " + (h.layak() ? "LAYAK" : "TIDAK") + ": " + h.alasan(), h.layak() ? ChatFormatting.GREEN : ChatFormatting.RED);
			if (h.layak()) {
				baris(source, String.format(Locale.ROOT, "  dasarY %d, putar %s", h.dasarY(), PutusanTema.arah(h.putar())), ChatFormatting.WHITE);
				if (t == Tema.GUNUNG) {
					baris(source, "  dy baris (atas, tengah, bawah): " + java.util.Arrays.toString(RencanaKotaTema.DY_GUNUNG), ChatFormatting.WHITE);
				}
			}
			if (!h.rinci().isEmpty()) {
				baris(source, "  " + h.rinci(), ChatFormatting.GRAY);
			}
			return h.layak() ? 1 : 0;
		}));
	}

	// ================================================================================================ cari / distrik

	private static LiteralArgumentBuilder<CommandSourceStack> cari() {
		return Commands.literal("cari").then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranCari)
				.executes(ctx -> cari(ctx, -1))
				.then(Commands.argument("indeks", IntegerArgumentType.integer(0)).executes(ctx -> cari(ctx, IntegerArgumentType.getInteger(ctx, "indeks")))));
	}

	private static int cari(CommandContext<CommandSourceStack> ctx, int indeks) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String what = StringArgumentType.getString(ctx, "tema");
		ServerLevel level = player.level().getServer().overworld();
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		if ("reruntuhan".equals(what)) {
			return DuniaCommands.findAndTeleport(player, Kultivator.id("kota_tema/reruntuhan_gurun"));
		}
		if ("pos_pemburu".equals(what)) {
			Kandidat best = null;
			for (Kandidat k : RencanaPosPemburu.INSTANCE.kandidat(level.getSeed())) {
				if (RencanaPosPemburu.akhir(sumber, k).layak() && (best == null || jarak(player, k.pusatX(), k.pusatZ()) < jarak(player, best.pusatX(), best.pusatZ()))) {
					best = k;
				}
			}
			if (best == null) {
				baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.cari.tidak", what).withStyle(ChatFormatting.RED));
				return 0;
			}
			teleport(player, best.pusatX(), best.pusatZ(), 0);
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.cari.ok", what, best.pusatX(), best.pusatZ()).withStyle(ChatFormatting.GREEN));
			return 1;
		}
		Tema t = Tema.dari(what);
		if (t == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.tema_salah", what).withStyle(ChatFormatting.RED));
			return 0;
		}
		TemaKota.Kota kota = null;
		if (indeks >= 0) {
			RencanaKotaTema.Calon c = RencanaKotaTema.plan(level.getSeed()).calon(t, indeks);
			kota = c == null ? null : TemaKota.dari(sumber, c);
		} else {
			List<TemaKota.Kota> near = TemaKota.sekitar(level, player.blockPosition(), t, 30000, 1);
			kota = near.isEmpty() ? null : near.getFirst();
		}
		if (kota == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.cari.tidak", what).withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos at = kota.pusat();
		teleport(player, at.getX(), at.getZ(), at.getY());
		baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.cari.kota", kota.nama(level.getSeed()), at.getX(), at.getZ(),
				PutusanTema.arah(kota.putar())).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> distrik() {
		return Commands.literal("distrik").then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranTema)
				.then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) -> {
					Tema t = Tema.dari(StringArgumentType.getString(ctx, "tema"));
					List<String> ids = new ArrayList<>();
					if (t != null) {
						for (Tema.Distrik d : t.distrik()) {
							ids.add(d.id());
						}
					}
					return SharedSuggestionProvider.suggest(ids, builder);
				}).executes(ctx -> {
					Tema t = tema(ctx, "tema");
					if (t == null) {
						return 0;
					}
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TemaKota.Kota kota = kotaSini(player, t);
					String id = StringArgumentType.getString(ctx, "id");
					BlockPos at = kota == null ? null : kota.distrik(id);
					if (at == null) {
						baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.distrik.tidak", t.id(), id).withStyle(ChatFormatting.RED));
						return 0;
					}
					teleport(player, at.getX(), at.getZ(), at.getY());
					baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.distrik.ok", id, at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.GREEN));
					return 1;
				})));
	}

	// ================================================================================================ lot / cek

	/** One themed piece of a loaded district start. */
	private record Potong(Identifier template, BoundingBox box, net.minecraft.world.level.block.Rotation rotasi, BlockPos asal) {
	}

	private static List<Potong> potongan(ServerLevel level, TemaKota.Kota kota) {
		List<Potong> out = new ArrayList<>();
		for (Distrik d : kota.semuaDistrik()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(d.chunkX(), d.chunkZ());
			if (chunk == null) {
				continue;
			}
			for (StructureStart start : chunk.getAllStarts().values()) {
				if (!start.isValid()) {
					continue;
				}
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
						Identifier template;
						try {
							template = single.getTemplateLocation();
						} catch (RuntimeException e) {
							continue;
						}
						if (Kultivator.MOD_ID.equals(template.getNamespace()) && template.getPath().startsWith("kota_tema/")) {
							out.add(new Potong(template, piece.getBoundingBox(), pool.getRotation(), pool.getPosition()));
						}
					}
				}
			}
		}
		return out;
	}

	/** Side codes of a piece in WORLD directions (n, e, s, w) after its rotation. */
	private static Map<String, String> sisiDunia(MinecraftServer server, Potong p) {
		Map<String, String> raw = TitikTemplat.sisi(server, p.template());
		String[] order = {"n", "e", "s", "w"};
		int turn = switch (p.rotasi()) {
			case NONE -> 0;
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
		};
		Map<String, String> out = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 4; i++) {
			String code = raw.get(order[i]);
			if (code != null) {
				out.put(order[(i + turn) & 3], code);
			}
		}
		return out;
	}

	private static int lot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		TemaKota.Kota kota = TemaKota.di(level, player.blockPosition());
		if (kota == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos at = player.blockPosition();
		for (Potong p : potongan(level, kota)) {
			BoundingBox b = p.box();
			if (at.getX() < b.minX() || at.getX() > b.maxX() || at.getZ() < b.minZ() || at.getZ() > b.maxZ()) {
				continue;
			}
			CommandSourceStack source = ctx.getSource();
			baris(source, Component.translatable("kultivator.tema.cmd.lot.judul", p.template().toString()).withStyle(ChatFormatting.GOLD));
			baris(source, String.format(Locale.ROOT, "  putar %s, asal (%d, %d, %d), dy %d dari dasarY %d, distrik %s", p.rotasi().name(),
					p.asal().getX(), p.asal().getY(), p.asal().getZ(), p.asal().getY() + 4 - (kota.dasarY() - 1), kota.dasarY(),
					TemaKota.distrikDi(kota, at)), ChatFormatting.WHITE);
			baris(source, "  sisi (dunia): " + sisiDunia(level.getServer(), p), ChatFormatting.AQUA);
			Map<String, Integer> count = new java.util.TreeMap<>();
			for (Titik t : TitikTemplat.dunia(level.getServer(), p.template(), p.asal(), p.rotasi())) {
				count.merge(t.jenis(), 1, Integer::sum);
			}
			baris(source, "  titik: " + count, ChatFormatting.GRAY);
			return 1;
		}
		baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.lot.tidak").withStyle(ChatFormatting.YELLOW));
		return 0;
	}

	private static int cek(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		TemaKota.Kota kota = kotaSini(player, null);
		CommandSourceStack source = ctx.getSource();
		if (kota == null) {
			baris(source, Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
			return 0;
		}
		MinecraftServer server = level.getServer();
		baris(source, Component.translatable("kultivator.tema.cmd.cek.judul", kota.nama(level.getSeed()), kota.tema().id()).withStyle(Style(kota.tema())));
		int loaded = 0;
		for (Distrik d : kota.semuaDistrik()) {
			if (level.getChunkSource().getChunkNow(d.chunkX(), d.chunkZ()) != null) {
				loaded++;
			}
		}
		List<Potong> pieces = potongan(level, kota);
		baris(source, String.format(Locale.ROOT, "  distrik dimuat %d/%d, potongan tema %d", loaded, kota.semuaDistrik().size(), pieces.size()),
				ChatFormatting.WHITE);
		int bad = 0;
		int dead = 0;
		int pairs = 0;
		for (Potong a : pieces) {
			Map<String, String> sa = sisiDunia(server, a);
			boolean pool = a.template().getPath().contains("kolam") || a.template().getPath().contains("danau");
			for (Map.Entry<String, String> side : sa.entrySet()) {
				Potong b = tetangga(a, side.getKey(), pieces);
				if (b == null) {
					if ("K".equals(side.getValue()) && !pool) {
						dead++;
					}
					continue;
				}
				String other = sisiDunia(server, b).get(lawan(side.getKey()));
				if (other == null) {
					continue;
				}
				pairs++;
				if (!other.equals(side.getValue())) {
					bad++;
					if (bad <= 6) {
						baris(source, "  sisi beda: " + a.template().getPath() + " " + side.getKey() + "=" + side.getValue() + " vs "
								+ b.template().getPath() + " " + lawan(side.getKey()) + "=" + other, ChatFormatting.RED);
					}
				}
			}
		}
		baris(source, String.format(Locale.ROOT, "  pasangan sisi %d, beda %d, kanal ke tepi tanpa sambungan %d", pairs / 2, bad / 2, dead),
				bad == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
		List<Entity> bodies = TemaMekanisme.hidup(kota.situsId());
		baris(source, String.format(Locale.ROOT, "  mekanisme hidup %d (maks %d), seluruh dunia %d/%d, kota hidup %d", bodies.size(),
				TemaConfig.inti().mekanismeMaks, TemaMekanisme.jumlah(), TemaMekanisme.GLOBAL, TemaKota.hidupIds().size()), ChatFormatting.WHITE);
		List<TemaKalender.Acara> running = TemaKalender.berjalanDi(kota.situsId());
		if (!running.isEmpty()) {
			List<String> ids = new ArrayList<>();
			for (TemaKalender.Acara a : running) {
				ids.add(a.id());
			}
			baris(source, "  acara berjalan: " + ids, ChatFormatting.LIGHT_PURPLE);
		}
		return bad == 0 ? 1 : 0;
	}

	private static String lawan(String side) {
		return switch (side) {
			case "n" -> "s";
			case "s" -> "n";
			case "e" -> "w";
			default -> "e";
		};
	}

	/** The piece touching {@code a} on world side {@code side} with at least 24 blocks of shared edge, or null. */
	private static @Nullable Potong tetangga(Potong a, String side, List<Potong> pieces) {
		BoundingBox x = a.box();
		for (Potong b : pieces) {
			if (b == a) {
				continue;
			}
			BoundingBox y = b.box();
			boolean touch = switch (side) {
				case "n" -> y.maxZ() + 1 == x.minZ() && overlap(x.minX(), x.maxX(), y.minX(), y.maxX()) >= 24;
				case "s" -> y.minZ() - 1 == x.maxZ() && overlap(x.minX(), x.maxX(), y.minX(), y.maxX()) >= 24;
				case "e" -> y.minX() - 1 == x.maxX() && overlap(x.minZ(), x.maxZ(), y.minZ(), y.maxZ()) >= 24;
				default -> y.maxX() + 1 == x.minX() && overlap(x.minZ(), x.maxZ(), y.minZ(), y.maxZ()) >= 24;
			};
			if (touch) {
				return b;
			}
		}
		return null;
	}

	private static int overlap(int a0, int a1, int b0, int b1) {
		return Math.min(a1, b1) - Math.max(a0, b0) + 1;
	}

	// ================================================================================================ acara

	private static LiteralArgumentBuilder<CommandSourceStack> acara() {
		return Commands.literal("acara")
				.executes(ctx -> {
					for (Tema t : Tema.values()) {
						List<String> ids = new ArrayList<>();
						for (TemaKalender.Acara a : TemaKalender.acara(t.id())) {
							ids.add(a.id() + " " + jam(a.dari()) + "-" + jam(a.sampai()));
						}
						baris(ctx.getSource(), t.id() + ": " + (ids.isEmpty() ? "-" : String.join(", ", ids)), Style(t));
					}
					return 1;
				})
				.then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranTema)
						.then(Commands.argument("acara", StringArgumentType.word()).suggests((ctx, builder) -> {
							List<String> ids = new ArrayList<>();
							for (TemaKalender.Acara a : TemaKalender.acara(StringArgumentType.getString(ctx, "tema"))) {
								ids.add(a.id());
							}
							return SharedSuggestionProvider.suggest(ids, builder);
						})
								.executes(ctx -> acara(ctx, true))
								.then(Commands.literal("mulai").executes(ctx -> acara(ctx, true)))
								.then(Commands.literal("akhir").executes(ctx -> acara(ctx, false)))));
	}

	private static String jam(int tick) {
		int h = (tick / 1000 + 6) % 24;
		int m = tick % 1000 * 60 / 1000;
		return String.format(Locale.ROOT, "%02d.%02d", h, m);
	}

	private static int acara(CommandContext<CommandSourceStack> ctx, boolean mulai) throws CommandSyntaxException {
		Tema t = tema(ctx, "tema");
		if (t == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "acara");
		TemaKota.Kota kota = kotaSini(player, t);
		if (kota == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (TemaKalender.cari(t.id(), id) == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.acara.tidak", t.id(), id).withStyle(ChatFormatting.RED));
			return 0;
		}
		boolean ok = TemaKalender.paksa(player.level(), kota.situsId(), t.id(), id, mulai);
		baris(ctx.getSource(), Component.translatable(mulai ? "kultivator.tema.cmd.acara.mulai" : "kultivator.tema.cmd.acara.akhir", id,
				kota.nama(player.level().getSeed())).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
		return ok ? 1 : 0;
	}

	// ================================================================================================ dagang

	private static LiteralArgumentBuilder<CommandSourceStack> dagang() {
		return Commands.literal("dagang")
				.executes(ctx -> dagangHarga(ctx))
				.then(Commands.literal("harga").executes(TemaUji::dagangHarga))
				.then(Commands.literal("batas").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					baris(ctx.getSource(), String.format(Locale.ROOT, "laba minggu ini %d / batas %d NB (pita %d), total seumur hidup %d, 7 hari %s",
							Dagang.labaMinggu(player), Dagang.batas(player), Barang.pita(player), Dagang.labaTotal(player),
							java.util.Arrays.toString(Dagang.labaHarian(player))), Dagang.lewatBatas(player) ? ChatFormatting.RED : ChatFormatting.GREEN);
					return 1;
				}))
				.then(Commands.literal("jenuh").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					long site = Dagang.pasarDi(player);
					if (site == 0L) {
						baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
						return 0;
					}
					MinecraftServer server = player.level().getServer();
					for (DagangItems.Barang b : DagangItems.barang()) {
						double n = Dagang.jenuhUnit(server, site, b.id());
						if (n > 0.0D) {
							baris(ctx.getSource(), String.format(Locale.ROOT, "  %s: %.1f unit, jenuh %.2f", b.id(), n, Dagang.jenuh(server, site, b.id())),
									ChatFormatting.WHITE);
						}
					}
					return 1;
				}))
				.then(Commands.literal("jual").then(Commands.argument("barang", StringArgumentType.word()).suggests((ctx, builder) -> {
					List<String> ids = new ArrayList<>();
					for (DagangItems.Barang b : DagangItems.barang()) {
						ids.add(b.id());
					}
					return SharedSuggestionProvider.suggest(ids, builder);
				}).then(Commands.argument("n", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					DagangItems.Barang b = DagangItems.barang(StringArgumentType.getString(ctx, "barang"));
					long site = Dagang.pasarDi(player);
					if (b == null || site == 0L) {
						baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
						return 0;
					}
					int n = IntegerArgumentType.getInteger(ctx, "n");
					double sum = 0.0D;
					StringBuilder each = new StringBuilder();
					for (int i = 0; i < n; i++) {
						double unit = b.dasar() * Dagang.permintaan(player.level().getServer(), site, TemaInti.pasar(player.level().getServer(), site), b)
								* Dagang.jenuhDari(Dagang.jenuhUnit(player.level().getServer(), site, b.id()) + i);
						sum += unit;
						if (i < 12) {
							each.append(String.format(Locale.ROOT, "%.1f ", unit));
						}
					}
					baris(ctx.getSource(), String.format(Locale.ROOT, "simulasi jual %d %s di sini: %.1f NB (per unit %s)", n, b.id(), sum, each),
							ChatFormatting.AQUA);
					return 1;
				}))))
				.then(Commands.literal("simulasi").then(Commands.argument("hari", IntegerArgumentType.integer(1, 56)).executes(ctx ->
						simulasi(ctx, IntegerArgumentType.getInteger(ctx, "hari")))));
	}

	private static int dagangHarga(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		long site = Dagang.pasarDi(player);
		int pasar = TemaInti.pasar(server, site);
		baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.dagang.judul", site == 0L ? Component.literal("-") : Dagang.namaSitus(server, site))
				.withStyle(ChatFormatting.GOLD));
		String[] cols = {"pelabuhan", "oasis", "air", "gunung", "ibu_kota", "iblis"};
		for (DagangItems.Barang b : DagangItems.barang()) {
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  %-20s %3d ", b.id(), b.dasar()));
			for (int c = 0; c < cols.length; c++) {
				double f = b.di(c);
				line.append(f <= 0.0D ? "   -  " : String.format(Locale.ROOT, "%5.2f ", f));
			}
			if (site != 0L && pasar >= 0) {
				long buy = Dagang.hargaBeli(player, site, b);
				double sell = Dagang.hargaJual(player, site, b, new Dagang.Opsi(pasar == DagangItems.PASAR_IBLIS, 0.0D));
				line.append(String.format(Locale.ROOT, "| beli %s jual %.1f", buy < 0 ? "-" : Long.toString(buy), sell));
			}
			baris(ctx.getSource(), line.toString(), ChatFormatting.WHITE);
		}
		baris(ctx.getSource(), "  kolom: " + String.join(" ", cols), ChatFormatting.DARK_GRAY);
		return 1;
	}

	/**
	 * Simulates an active trader for {@code hari} days on the reference table: each day he buys his daily 16 units of the
	 * best margin good of a market and sells them in its best other market (own saturation counted, one unit recovering
	 * per hour of travel: half a day per trip), the weekly cap of his band applied. Pure arithmetic: no world change.
	 */
	private static int simulasi(CommandContext<CommandSourceStack> ctx, int hari) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int band = Barang.pita(player);
		long cap = (long) TemaConfig.inti().batasLabaMingguan * (band + 1L);
		Map<String, Double> sat = new java.util.HashMap<>();
		long week = 0L;
		long weekProfit = 0L;
		long total = 0L;
		long worstWeek = 0L;
		StringBuilder log = new StringBuilder();
		for (int d = 0; d < hari; d++) {
			if (d % 7 == 0) {
				if (d > 0) {
					log.append(String.format(Locale.ROOT, "minggu %d: %d / %d NB\n", week + 1, weekProfit, cap));
					worstWeek = Math.max(worstWeek, weekProfit);
					week++;
				}
				weekProfit = 0L;
			}
			sat.replaceAll((k, v) -> Math.max(0.0D, v - 12.0D));
			DagangItems.Barang best = null;
			int bestCol = -1;
			double bestGain = 0.0D;
			for (DagangItems.Barang b : DagangItems.barang()) {
				if (b.selundupan()) {
					continue;
				}
				int origin = TemaInti.kolom(b.asal());
				for (int c = 0; c < 6; c++) {
					if (c == origin || b.di(c) <= 0.0D) {
						continue;
					}
					double s = sat.getOrDefault(b.id() + "@" + c, 0.0D);
					double gain = 0.0D;
					for (int i = 0; i < Dagang.STOK_HARIAN; i++) {
						gain += b.dasar() * b.di(c) * Dagang.jenuhDari(s + i) - b.dasar();
					}
					if (gain > bestGain) {
						bestGain = gain;
						best = b;
						bestCol = c;
					}
				}
			}
			if (best == null) {
				continue;
			}
			double s = sat.getOrDefault(best.id() + "@" + bestCol, 0.0D);
			double profit = 0.0D;
			for (int i = 0; i < Dagang.STOK_HARIAN; i++) {
				double unit = weekProfit + profit >= cap ? best.dasar() * Dagang.JUAL_RENDAH : best.dasar() * best.di(bestCol) * Dagang.jenuhDari(s + i);
				profit += unit - best.dasar();
			}
			sat.put(best.id() + "@" + bestCol, s + Dagang.STOK_HARIAN);
			long p = Math.round(profit);
			weekProfit += p;
			total += p;
			if (d < 7) {
				log.append(String.format(Locale.ROOT, "hari %d: %s ke kolom %d, laba %d\n", d + 1, best.id(), bestCol, p));
			}
		}
		worstWeek = Math.max(worstWeek, weekProfit);
		log.append(String.format(Locale.ROOT, "minggu %d: %d / %d NB", week + 1, weekProfit, cap));
		for (String line : log.toString().split("\n")) {
			baris(ctx.getSource(), "  " + line, ChatFormatting.WHITE);
		}
		boolean ok = worstWeek <= cap + best16(cap);
		baris(ctx.getSource(), String.format(Locale.ROOT, "total %d NB dalam %d hari; minggu terbesar %d (batas %d): %s", total, hari, worstWeek, cap,
				ok ? "OK" : "LEWAT"), ok ? ChatFormatting.GREEN : ChatFormatting.RED);
		return ok ? 1 : 0;
	}

	/** Profit of the last unit sold before the cap took hold (the cap is checked per unit, so one unit may cross it). */
	private static long best16(long cap) {
		long most = 0L;
		for (DagangItems.Barang b : DagangItems.barang()) {
			most = Math.max(most, Math.round(b.dasar() * (Dagang.PERMINTAAN_MAKS - 1.0D)));
		}
		return most;
	}

	// ================================================================================================ kontrak

	private static LiteralArgumentBuilder<CommandSourceStack> kontrak() {
		return Commands.literal("kontrak")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ListTag list = KontrakMuatan.aktif(player);
					baris(ctx.getSource(), "kontrak aktif: " + list.size(), ChatFormatting.GOLD);
					for (int i = 0; i < list.size(); i++) {
						CompoundTag c = list.getCompoundOrEmpty(i);
						baris(ctx.getSource(), String.format(Locale.ROOT, "  %s: %d peti %s -> %s, bayar %d, deposit %d", c.getStringOr("id", ""),
								c.getIntOr("peti", 0), c.getStringOr("barang", ""), Dagang.namaSitus(player.level().getServer(), c.getLongOr("tujuan", 0L)).getString(),
								c.getLongOr("bayar", 0L), c.getLongOr("deposit", 0L)), ChatFormatting.WHITE);
					}
					return 1;
				})
				.then(Commands.literal("buat").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TemaKota.Kota kota = kotaSini(player, null);
					if (kota == null || kota.tema() == Tema.IBLIS) {
						baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.bukan_kota").withStyle(ChatFormatting.RED));
						return 0;
					}
					KontrakMuatan.bukaPapan(player, kota);
					return 1;
				}))
				.then(Commands.literal("selesai").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ListTag list = KontrakMuatan.aktif(player);
					if (list.isEmpty()) {
						return 0;
					}
					CompoundTag c = list.getCompoundOrEmpty(0);
					BlockPos to = Dagang.posSitus(player.level().getServer(), c.getLongOr("tujuan", 0L));
					if (to != null) {
						teleport(player, to.getX(), to.getZ(), to.getY());
					}
					baris(ctx.getSource(), KontrakMuatan.serah(player, c.getStringOr("id", ""), KontrakMuatan.lewatLangit(player)).copy()
							.withStyle(ChatFormatting.GREEN));
					return 1;
				}))
				.then(Commands.literal("gagal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ListTag list = KontrakMuatan.aktif(player);
					if (list.isEmpty()) {
						return 0;
					}
					KontrakMuatan.gagal(player, list.getCompoundOrEmpty(0), "kultivator.kota_tema.kontrak.gagal_uji");
					return 1;
				}));
	}

	// ================================================================================================ alur

	private static LiteralArgumentBuilder<CommandSourceStack> alur() {
		return Commands.literal("alur").then(Commands.argument("tema", StringArgumentType.word()).suggests(TemaUji::saranTema)
				.executes(ctx -> alurDaftar(ctx))
				.then(Commands.argument("bab", StringArgumentType.word()).suggests((ctx, builder) -> {
					List<String> ids = new ArrayList<>();
					Quest q = rantai(Tema.dari(StringArgumentType.getString(ctx, "tema")));
					if (q != null) {
						for (int i = 0; i < q.tahap().size(); i++) {
							ids.add(Integer.toString(i + 1));
							ids.add(q.tahap().get(i).id());
						}
						ids.add(Quest.SELESAI);
					}
					return SharedSuggestionProvider.suggest(ids, builder);
				}).executes(TemaUji::alurLompat)));
	}

	/** The main story chain of a theme: the first {@code alur} quest whose id starts with {@code tema_<tema>_}. */
	private static @Nullable Quest rantai(@Nullable Tema tema) {
		if (tema == null) {
			return null;
		}
		Quest any = null;
		for (Quest q : CeritaReload.quests()) {
			if (q.id().startsWith("tema_" + tema.id() + "_")) {
				if (q.alur()) {
					return q;
				}
				if (any == null) {
					any = q;
				}
			}
		}
		return any;
	}

	private static int alurDaftar(CommandContext<CommandSourceStack> ctx) {
		Tema t = tema(ctx, "tema");
		Quest q = rantai(t);
		if (q == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.alur.tidak", t == null ? "?" : t.id()).withStyle(ChatFormatting.RED));
			return 0;
		}
		baris(ctx.getSource(), q.id() + ":", ChatFormatting.GOLD);
		for (int i = 0; i < q.tahap().size(); i++) {
			baris(ctx.getSource(), "  " + (i + 1) + " " + q.tahap().get(i).id(), ChatFormatting.WHITE);
		}
		return 1;
	}

	private static int alurLompat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tema t = tema(ctx, "tema");
		Quest q = rantai(t);
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (q == null || t == null) {
			baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.alur.tidak", t == null ? "?" : t.id()).withStyle(ChatFormatting.RED));
			return 0;
		}
		String bab = StringArgumentType.getString(ctx, "bab");
		String stage = bab;
		try {
			int n = Integer.parseInt(bab);
			if (n >= 1 && n <= q.tahap().size()) {
				stage = q.tahap().get(n - 1).id();
			}
		} catch (NumberFormatException ignored) {
			// a stage id
		}
		if (!QuestTracker.aktif(player, q.id())) {
			TemaKota.Kota kota = kotaSini(player, t);
			QuestTracker.mulai(player, q.id(), kota == null ? 0L : kota.situsId());
		}
		boolean ok = QuestTracker.lompat(player, q.id(), stage);
		baris(ctx.getSource(), Component.translatable(ok ? "kultivator.tema.cmd.alur.ok" : "kultivator.tema.cmd.alur.gagal", q.id(), stage)
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
		return ok ? 1 : 0;
	}

	// ================================================================================================ model

	private static int model(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		double rad = Math.toRadians(player.getYRot());
		Vec3 forward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
		Vec3 base = player.position().add(forward.scale(10.0D));
		List<EntityType<?>> types = TemaEntities.semua();
		List<Entity> made = new ArrayList<>();
		int perRow = 8;
		for (int i = 0; i < types.size(); i++) {
			int row = i / perRow;
			int col = i % perRow;
			Vec3 at = base.add(right.scale((col - (perRow - 1) / 2.0D) * 7.0D)).add(forward.scale(row * 9.0D));
			Entity e;
			try {
				e = types.get(i).create(level, EntitySpawnReason.COMMAND);
			} catch (RuntimeException exception) {
				continue;
			}
			if (e == null) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
			e.snapTo(at.x, y, at.z, player.getYRot() + 180.0F, 0.0F);
			if (e instanceof Mob mob) {
				mob.setNoAi(true);
				mob.setPersistenceRequired();
			}
			if (e instanceof TemaMekanismeEntity m) {
				m.setKeadaan(1);
			}
			if (level.addFreshEntity(e)) {
				made.add(e);
			}
		}
		nanti(level, PAMER_TICK, () -> {
			for (Entity e : made) {
				if (!e.isRemoved()) {
					e.discard();
				}
			}
		});
		baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.model.ok", made.size(), PAMER_TICK / 20).withStyle(ChatFormatting.GREEN));
		return made.size();
	}

	// ================================================================================================ fx

	private static LiteralArgumentBuilder<CommandSourceStack> fx() {
		return Commands.literal("fx").then(Commands.argument("apa", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FX, builder))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					String apa = StringArgumentType.getString(ctx, "apa");
					if (!FX.contains(apa)) {
						baris(ctx.getSource(), "fx: " + FX, ChatFormatting.RED);
						return 0;
					}
					fxKirim(player, apa);
					baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.fx.ok", apa, FX_TICK / 20).withStyle(ChatFormatting.GREEN));
					return 1;
				}));
	}

	private static void fxKirim(ServerPlayer player, String apa) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		double rad = Math.toRadians(player.getYRot());
		int fx = (int) Math.floor(player.getX() - Math.sin(rad) * 24.0D);
		int fz = (int) Math.floor(player.getZ() + Math.cos(rad) * 24.0D);
		long site = 0L;
		CompoundTag data = new CompoundTag();
		switch (apa) {
			case "badai" -> {
				data.putBoolean("mulai", true);
				data.putInt("x", player.getBlockX());
				data.putInt("z", player.getBlockZ());
				data.putInt("r", 600);
				data.putLong("tick", now + 200);
				data.putInt("panjang", FX_TICK);
				data.putFloat("kuat", 1.0F);
				TemaNet.kirim(player, TemaNet.BADAI, site, data);
				nanti(level, FX_TICK + 200, () -> {
					CompoundTag end = new CompoundTag();
					end.putBoolean("mulai", false);
					TemaNet.kirim(player, TemaNet.BADAI, site, end);
				});
			}
			case "lentera" -> {
				CompoundTag lane = new CompoundTag();
				lane.putString("id", "uji");
				lane.putString("kind", "lentera");
				int[] p = new int[18];
				for (int i = 0; i < 6; i++) {
					p[i * 3] = (int) Math.floor(player.getX() - Math.sin(rad) * (6 + i * 8) + Math.cos(rad) * Math.sin(i) * 3);
					p[i * 3 + 1] = player.getBlockY();
					p[i * 3 + 2] = (int) Math.floor(player.getZ() + Math.cos(rad) * (6 + i * 8) + Math.sin(rad) * Math.sin(i) * 3);
				}
				lane.put("p", new IntArrayTag(p));
				ListTag lanes = new ListTag();
				lanes.add(lane);
				CompoundTag spline = new CompoundTag();
				spline.put("jalur", lanes);
				TemaNet.kirim(player, TemaNet.SPLINE, site, spline);
				data.putString("acara", "lentera_air");
				data.putBoolean("mulai", true);
				data.putLong("tick", now);
				data.putLong("akhir", now + FX_TICK);
				TemaNet.kirim(player, TemaNet.FESTIVAL, site, data);
				nanti(level, FX_TICK, () -> {
					CompoundTag end = new CompoundTag();
					end.putString("acara", "lentera_air");
					end.putBoolean("mulai", false);
					TemaNet.kirim(player, TemaNet.FESTIVAL, site, end);
				});
			}
			default -> {
				String kind = "cahaya".equals(apa) ? "budha" : apa;
				data.putString("apa", kind);
				data.putInt("x", fx);
				data.putInt("y", "cahaya".equals(apa) || "awan".equals(apa) ? player.getBlockY() - 6 : player.getBlockY());
				data.putInt("z", fz);
				data.putLong("tick", now);
				data.putInt("panjang", FX_TICK);
				data.putBoolean("nyala", true);
				data.putBoolean("uji", true);
				TemaNet.kirim(player, TemaNet.CAHAYA, site, data);
			}
		}
	}

	// ================================================================================================ kepadatan

	private static int kepadatan(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level().getServer().overworld();
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		Map<Tema, int[]> count = new EnumMap<>(Tema.class);
		for (RencanaKotaTema.Calon c : RencanaKotaTema.plan(level.getSeed()).urutan()) {
			if (jarak(player, c.x(), c.z()) > radius) {
				continue;
			}
			int[] n = count.computeIfAbsent(c.tema(), k -> new int[2]);
			n[0]++;
			if (RencanaKotaTema.akhirCalon(sumber, c).layak()) {
				n[1]++;
			}
		}
		double km2 = Math.PI * radius * (double) radius / 1.0E6D;
		baris(ctx.getSource(), Component.translatable("kultivator.tema.cmd.kepadatan.judul", radius, String.format(Locale.ROOT, "%.1f", km2))
				.withStyle(ChatFormatting.GOLD));
		for (Tema t : Tema.values()) {
			int[] n = count.getOrDefault(t, new int[2]);
			baris(ctx.getSource(), String.format(Locale.ROOT, "  %-9s calon %3d, layak %2d, %.3f per km2", t.id(), n[0], n[1], n[1] / km2), Style(t));
		}
		return 1;
	}

	private TemaUji() {
	}
}

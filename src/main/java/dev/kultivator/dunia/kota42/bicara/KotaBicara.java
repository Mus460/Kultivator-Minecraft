package dev.kultivator.dunia.kota42.bicara;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBagianData;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Niat;
import dev.kultivator.dunia.kota42.warga.HariWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime init of 42B Bicara (DESIGN 42.5, 42.10), called once from {@code KotaHidup.init}: talk with every civilian
 * ({@link BicaraWarga}), asking the way ({@link TanyaJalan}), personal requests ({@link PermintaanWarga}), gifts
 * ({@link Selera}), loans ({@link Pinjaman}), the new quest goal kinds ({@link TujuanKota}) and the named characters of
 * 42.10 ({@link TokohKota}). Behaviour checks {@code kotaBicara.aktif} at run time ({@link KotaConfig#bicara()}), so a
 * reload can switch it off and on; nothing is registered conditionally.
 * <p>
 * Owns the SavedData {@code kultivator:kota_bicara} ({@link Data}, sections {@link Data#TOLONG}, {@link Data#PINJAM},
 * {@link Data#JEJAK}, {@link Data#SEBAR}, {@link Data#TOKOH}) and the data loader of 42B's datapack files (authored by
 * 42B-C; every file is optional, code holds a complete default):
 * <ul>
 * <li>{@code data/kultivator/tempat/tolong/<jenis>.json} — text and tuning of one request kind ({@link PermintaanWarga}
 * javadoc has the format);</li>
 * <li>{@code data/kultivator/tempat/selera.json} — gift tastes ({@link Selera} javadoc);</li>
 * <li>{@code data/kultivator/tempat/tokoh/*.json} — named characters ({@link TokohKota} javadoc).</li>
 * </ul>
 * Test subs under {@code /kultivasi uji kota_hidup}: {@code bicara info|uji <topik>}, {@code jalan <tujuan>},
 * {@code tolong buat <jenis> [warga]|selesai|daftar}, {@code tokoh}.
 */
public final class KotaBicara {

	/** SavedData {@code kultivator:kota_bicara} (overworld). New worlds only: no migration. */
	public static final class Data extends KotaBagianData {

		public static final Codec<Data> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new Data(), tag), Data::tulis);
		public static final SavedDataType<Data> TYPE = new SavedDataType<>(Kultivator.id("kota_bicara"), Data::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		/** Requests per place ({@code kota_<situs>}: {@code L} list, {@code n} next id) and accept counters per player. */
		public static final String TOLONG = "tolong";
		/** Loans between players and residents ({@code L} list, {@code n} next id, weekly interest per player). */
		public static final String PINJAM = "pinjam";
		/** Gossip traces per player ("Tanya siapa yang bilang"). */
		public static final String JEJAK = "jejak";
		/** "Sebarkan kabar baik sekteku" per place and player, with the recruiting boost until a day. */
		public static final String SEBAR = "sebar";
		/** Named characters: tokoh id -> warga id, places already cast. */
		public static final String TOKOH = "tokoh";

		public Data() {
		}

		public static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}

		@Override
		protected CompoundTag tulis() {
			return super.tulis();
		}
	}

	/** Everything one datapack load of 42B produced. */
	record Muatan(Map<String, JsonObject> tolong, JsonObject selera, Map<String, JsonObject> tokoh) {
		static final Muatan KOSONG = new Muatan(Map.of(), new JsonObject(), Map.of());
	}

	private static final FileToIdConverter TOLONG = FileToIdConverter.json("tempat/tolong");
	private static final FileToIdConverter TOKOH = FileToIdConverter.json("tempat/tokoh");
	private static final Identifier SELERA = Kultivator.id("tempat/selera.json");

	private static volatile Muatan muatan = Muatan.KOSONG;
	private static boolean initialised;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Kultivator.id("kota42_bicara"), new Muat());
		BicaraWarga.init();
		PermintaanWarga.init();
		Pinjaman.init();
		TujuanKota.init();
		TokohKota.init();
		HariWarga.addLangkah(HariWarga.URUT_KERJA + 50, "bicara_tokoh", (server, data, s, hari, random) -> TokohKota.harian(server, s, hari));
		HariWarga.addLangkah(HariWarga.URUT_INGATAN + 50, "bicara_tolong", (server, data, s, hari, random) -> PermintaanWarga.harian(server, s, hari));
		HariWarga.addLangkah(HariWarga.URUT_INGATAN + 60, "bicara_pinjam", (server, data, s, hari, random) -> Pinjaman.harian(server, s, hari));
		ServerTickEvents.END_SERVER_TICK.register(KotaBicara::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BicaraWarga.lupakan(handler.getPlayer().getUUID());
			TanyaJalan.lupakan(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BicaraWarga.lupakanSemua();
			TanyaJalan.lupakanSemua();
			PermintaanWarga.lupakanSemua();
			TokohKota.lupakanSemua();
		});
		KotaUji.daftar("bicara", KotaBicara::ujiBicara);
		KotaUji.daftar("jalan", KotaBicara::ujiJalan);
		KotaUji.daftar("tolong", KotaBicara::ujiTolong);
		KotaUji.daftar("tokoh", KotaBicara::ujiTokoh);
		KotaUji.chips("kota_hidup bicara info", "kota_hidup tolong daftar", "kota_hidup tokoh");
	}

	/** True while 42 talk runs ({@code kotaBicara.aktif} and residents on). */
	public static boolean aktif() {
		return KotaConfig.bicara().aktif && WargaApi.aktif();
	}

	static Muatan muatan() {
		return muatan;
	}

	private static void tick(MinecraftServer server) {
		if (!aktif()) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (now % 20L == 0L) {
			BicaraWarga.tick(server);
			TanyaJalan.tick(server, now);
		}
		if (now % 20L == 5L) {
			PermintaanWarga.tick(server, now);
		}
		if (now % 100L == 10L) {
			TokohKota.tick(server);
		}
	}

	// ================================================================== datapack

	private static final class Muat extends SimplePreparableReloadListener<Muatan> {

		@Override
		protected Muatan prepare(ResourceManager manager, ProfilerFiller profiler) {
			Map<String, JsonObject> tolong = baca(manager, TOLONG);
			Map<String, JsonObject> tokoh = baca(manager, TOKOH);
			JsonObject selera = new JsonObject();
			Optional<Resource> res = manager.getResource(SELERA);
			if (res.isPresent()) {
				try (Reader reader = res.get().openAsReader()) {
					JsonElement json = JsonParser.parseReader(reader);
					if (json.isJsonObject()) {
						selera = json.getAsJsonObject();
					}
				} catch (Exception exception) {
					Kultivator.LOGGER.warn("Gift taste file {} could not be read", SELERA, exception);
				}
			}
			return new Muatan(tolong, selera, tokoh);
		}

		private static Map<String, JsonObject> baca(ResourceManager manager, FileToIdConverter conv) {
			Map<String, JsonObject> out = new LinkedHashMap<>();
			List<Map.Entry<Identifier, Resource>> entries = new ArrayList<>(conv.listMatchingResources(manager).entrySet());
			entries.sort(Map.Entry.comparingByKey());
			for (Map.Entry<Identifier, Resource> entry : entries) {
				try (Reader reader = entry.getValue().openAsReader()) {
					JsonElement json = JsonParser.parseReader(reader);
					if (json.isJsonObject()) {
						out.put(conv.fileToId(entry.getKey()).getPath(), json.getAsJsonObject());
					}
				} catch (Exception exception) {
					Kultivator.LOGGER.warn("Town talk file {} could not be read", entry.getKey(), exception);
				}
			}
			return Collections.unmodifiableMap(out);
		}

		@Override
		protected void apply(Muatan loaded, ResourceManager manager, ProfilerFiller profiler) {
			muatan = loaded;
			Selera.muat(loaded.selera());
			TokohKota.muat(loaded.tokoh());
			Kultivator.LOGGER.info("Town talk: {} request files, {} character files, gift tastes {}", loaded.tolong().size(), loaded.tokoh().size(),
					loaded.selera().size() == 0 ? "built-in" : "from data");
		}
	}

	// ================================================================== test subs (42.14.3)

	private static LiteralArgumentBuilder<CommandSourceStack> ujiBicara() {
		return Commands.literal("bicara")
				.then(Commands.literal("info").executes(KotaBicara::bicaraInfo))
				.then(Commands.literal("uji").then(Commands.argument("topik", StringArgumentType.word()).executes(KotaBicara::bicaraUji)));
	}

	private static int bicaraInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = BicaraWarga.terdekat(player, 8.0D);
		Warga w = npc == null ? null : WargaApi.dari(npc);
		if (w == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.bicara.uji.tanpa_warga"));
			return 0;
		}
		for (Component line : BicaraWarga.ringkas(player, npc, w)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int bicaraUji(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = BicaraWarga.terdekat(player, 8.0D);
		Warga w = npc == null ? null : WargaApi.dari(npc);
		if (w == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.bicara.uji.tanpa_warga"));
			return 0;
		}
		String topik = StringArgumentType.getString(ctx, "topik");
		long situs = npc.penduduk() ? npc.situsPenduduk() : npc.town();
		Component line = Cakap.ujaran(w, Niat.BERI_KABAR, new Cakap.Konteks(player, null, topik, null, situs, Map.of())).komponen();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.bicara.uji.baris", WargaApi.nama(w), topik, line), false);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ujiJalan() {
		return Commands.literal("jalan").then(Commands.argument("tujuan", StringArgumentType.greedyString()).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			KultivatorNpc npc = BicaraWarga.terdekat(player, 8.0D);
			if (npc == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.bicara.uji.tanpa_warga"));
				return 0;
			}
			Component answer = TanyaJalan.jawab(npc, StringArgumentType.getString(ctx, "tujuan"));
			ctx.getSource().sendSuccess(() -> answer, false);
			return 1;
		}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ujiTolong() {
		return Commands.literal("tolong")
				.then(Commands.literal("daftar").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					List<Component> lines = PermintaanWarga.daftarUji(player);
					for (Component line : lines) {
						ctx.getSource().sendSuccess(() -> line, false);
					}
					return lines.size();
				}))
				.then(Commands.literal("selesai").executes(ctx -> {
					int n = PermintaanWarga.selesaikanUji(ctx.getSource().getPlayerOrException());
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.bicara.uji.tolong_selesai", n), false);
					return n;
				}))
				.then(Commands.literal("buat").then(Commands.argument("jenis", StringArgumentType.word())
						.executes(ctx -> tolongBuat(ctx, -1))
						.then(Commands.argument("warga", IntegerArgumentType.integer(1))
								.executes(ctx -> tolongBuat(ctx, IntegerArgumentType.getInteger(ctx, "warga"))))));
	}

	private static int tolongBuat(CommandContext<CommandSourceStack> ctx, int wargaId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Warga w;
		if (wargaId > 0) {
			w = WargaApi.byId(wargaId);
		} else {
			KultivatorNpc npc = BicaraWarga.terdekat(player, 8.0D);
			w = npc == null ? null : WargaApi.dari(npc);
		}
		String jenis = StringArgumentType.getString(ctx, "jenis");
		PermintaanWarga.Permintaan p = w == null ? null : PermintaanWarga.paksa(w, jenis);
		if (p == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.bicara.uji.tolong_gagal", jenis));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.bicara.uji.tolong_dibuat", p.id(), jenis, WargaApi.nama(w))
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ujiTokoh() {
		return Commands.literal("tokoh").executes(ctx -> {
			List<Component> lines = TokohKota.laporan(ctx.getSource().getServer());
			for (Component line : lines) {
				ctx.getSource().sendSuccess(() -> line, false);
			}
			return lines.size();
		});
	}

	/** World day helper (KotaHidup day). */
	static long hari(MinecraftServer server) {
		return KotaHidup.hari(server);
	}

	private KotaBicara() {
	}
}

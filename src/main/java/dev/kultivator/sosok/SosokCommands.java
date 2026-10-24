package dev.kultivator.sosok;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * {@code /kultivasi uji penampilan ...} (DESIGN 40.6.4, server half; op 2, registered with
 * {@code DuniaCommands.test("penampilan", ...)}): galleries of every look, random faces, clip parades, the gait walk, the
 * face show, the spring run, changes to the player's own disciple, an exact-seed body for comparing with 40K's Python
 * preview, and a readout of a body's Sosok data. The test bodies are {@link GaleriUji}'s.
 */
public final class SosokCommands {

	/** Looks per role-gallery page (each shown as a man and a woman: 32 bodies, 8 columns by 4 rows). */
	public static final int RUPA_PER_HALAMAN = 16;
	/** {@code acak} at most. */
	public static final int ACAK_MAKS = 32;
	/** {@code klip semua} bodies. */
	public static final int PARADE_BARIS = 24;
	/** Reach of {@code murid}/{@code info} (blocks). */
	public static final double JANGKAU = 8.0D;

	/** The 15 gait profiles of 40.5.6 and the look whose table row walks that way ({@code jalan}). */
	private static final String[][] PROFIL = {
			{"gagah", "pandai_pusaka", "0"}, {"anggun", "penjaga_kebun", "1"}, {"santai", "pemilik_teh", "-1"},
			{"tergesa", "pelayan", "-1"}, {"militer", "penjaga", "-1"}, {"bangsawan", "juru_lelang", "-1"},
			{"pedagang", "pedagang_pil", "-1"}, {"rahib", "rahib", "0"}, {"kuli", "kuli", "-1"},
			{"tua_tongkat", "kepala_desa", "0"}, {"tua", "penceramah", "-1"}, {"anak_lompat", "anak", "-1"},
			{"penari", "penari_gurun", "1"}, {"iblis", "makelar_gelap", "-1"}, {"arwah", "d.arwah", "-1"}};

	/** The four spring test looks ({@code pegas}): long hair and shawl, ponytail, wide sleeves and outer robe, pibo. */
	private static final String[][] PEGAS = {{"abadi", "1"}, {"penjaga", "1"}, {"tetua_sekte", "0"}, {"penari_gurun", "1"}};

	private static final SuggestionProvider<CommandSourceStack> RUPA = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SosokGen.semuaKunci(), builder);
	private static final SuggestionProvider<CommandSourceStack> KLIP = (ctx, builder) -> {
		List<String> names = new ArrayList<>();
		names.add("semua");
		for (int id = 1; id <= GerakId.SOSOK_LAST_DIPAKAI; id++) {
			String nama = GerakId.nama(id);
			if (nama != null) {
				names.add(nama);
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> JALAN = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		ids.add("tidak_ada");
		for (int bit = 1; bit <= SosokJalan.MAKS; bit++) {
			ids.add(SosokJalan.id(bit));
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> USIA = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("anak", "muda", "dewasa", "tua"), builder);
	private static final SuggestionProvider<CommandSourceStack> SUASANA = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("netral", "senang", "sedih", "marah", "takut", "mengantuk", "malu", "bangga"), builder);
	private static final String[] SUASANA_ID = {"netral", "senang", "sedih", "marah", "takut", "mengantuk", "malu", "bangga"};
	private static final String[] USIA_ID = {"anak", "muda", "dewasa", "tua"};

	/** Called once from {@link Sosok#init()}. */
	static void init() {
		DuniaCommands.test("penampilan", SosokCommands::node);
	}

	static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("penampilan")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> bantuan(ctx.getSource()))
				.then(Commands.literal("galeri")
						.then(Commands.literal("peran")
								.executes(ctx -> galeriPeran(ctx, 1))
								.then(Commands.argument("halaman", IntegerArgumentType.integer(1, halamanJumlah()))
										.executes(ctx -> galeriPeran(ctx, IntegerArgumentType.getInteger(ctx, "halaman")))))
						.then(Commands.literal("murid").executes(SosokCommands::galeriMurid))
						.then(Commands.literal("jalan").executes(SosokCommands::galeriJalan))
						.then(Commands.literal("usia")
								.executes(ctx -> galeriUsia(ctx, "warga"))
								.then(Commands.argument("rupa", StringArgumentType.word()).suggests(RUPA)
										.executes(ctx -> galeriUsia(ctx, StringArgumentType.getString(ctx, "rupa")))))
						.then(Commands.literal("dao").executes(SosokCommands::galeriDao))
						.then(Commands.literal("fisik").executes(SosokCommands::galeriFisik)))
				.then(Commands.literal("acak")
						.executes(ctx -> acak(ctx, 16))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, ACAK_MAKS))
								.executes(ctx -> acak(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("klip")
						.then(Commands.literal("semua").executes(SosokCommands::klipSemua))
						.then(Commands.literal("kelompok")
								.then(Commands.argument("kelompok", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("tempat", "sikap", "murid", "fungsi", "aktivitas"), b))
										.executes(ctx -> klipKelompok(ctx, StringArgumentType.getString(ctx, "kelompok")))))
						.then(Commands.argument("klip", StringArgumentType.word()).suggests(KLIP)
								.executes(ctx -> klip(ctx, StringArgumentType.getString(ctx, "klip"), 8))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
										.executes(ctx -> klip(ctx, StringArgumentType.getString(ctx, "klip"), IntegerArgumentType.getInteger(ctx, "radius"))))))
				.then(Commands.literal("jalan").executes(SosokCommands::jalan))
				.then(Commands.literal("wajah").executes(SosokCommands::wajah))
				.then(Commands.literal("pegas").executes(SosokCommands::pegas))
				.then(Commands.literal("murid")
						.then(Commands.literal("acak").executes(ctx -> murid(ctx, "acak", "")))
						.then(Commands.literal("kelamin")
								.then(Commands.argument("nilai", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("l", "p"), b))
										.executes(ctx -> murid(ctx, "kelamin", StringArgumentType.getString(ctx, "nilai")))))
						.then(Commands.literal("usia")
								.then(Commands.argument("nilai", StringArgumentType.word()).suggests(USIA)
										.executes(ctx -> murid(ctx, "usia", StringArgumentType.getString(ctx, "nilai")))))
						.then(Commands.literal("jalan")
								.then(Commands.argument("nilai", StringArgumentType.word()).suggests(JALAN)
										.executes(ctx -> murid(ctx, "jalan", StringArgumentType.getString(ctx, "nilai")))))
						.then(Commands.literal("luka")
								.then(Commands.argument("nilai", IntegerArgumentType.integer(0, Gaya.LUKA_MAKS))
										.executes(ctx -> murid(ctx, "luka", String.valueOf(IntegerArgumentType.getInteger(ctx, "nilai"))))))
						.then(Commands.literal("suasana")
								.then(Commands.argument("nilai", StringArgumentType.word()).suggests(SUASANA)
										.executes(ctx -> murid(ctx, "suasana", StringArgumentType.getString(ctx, "nilai")))))
						.then(Commands.literal("busana")
								.then(Commands.argument("nilai", IntegerArgumentType.integer(0, Gaya.BUSANA_MAKS))
										.executes(ctx -> murid(ctx, "busana", String.valueOf(IntegerArgumentType.getInteger(ctx, "nilai"))))))
						.then(Commands.literal("hari")
								.then(Commands.argument("nilai", IntegerArgumentType.integer(1, 1000))
										.executes(ctx -> murid(ctx, "hari", String.valueOf(IntegerArgumentType.getInteger(ctx, "nilai")))))))
				.then(Commands.literal("pratinjau_benih")
						.then(Commands.argument("benih", IntegerArgumentType.integer(0, Sosok.BENIH_MASK))
								.then(Commands.argument("rupa", StringArgumentType.word()).suggests(RUPA)
										.executes(ctx -> pratinjauBenih(ctx, IntegerArgumentType.getInteger(ctx, "benih"),
												StringArgumentType.getString(ctx, "rupa"))))))
				.then(Commands.literal("info").executes(SosokCommands::info))
				.then(Commands.literal("hapus").executes(ctx -> hapus(ctx.getSource())));
	}

	// ================================================================== help

	private static int bantuan(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.intro").withStyle(ChatFormatting.GRAY), false);
		String[] chips = {"galeri peran", "galeri murid", "galeri jalan", "galeri usia warga", "galeri dao", "galeri fisik", "acak 16",
				"klip semua", "klip kelompok murid", "klip hormat", "jalan", "wajah", "pegas", "murid acak", "murid usia anak",
				"murid hari 48", "pratinjau_benih 2773443 murid.1", "info", "hapus"};
		MutableComponent line = Component.empty();
		for (int i = 0; i < chips.length; i++) {
			String cmd = "/kultivasi uji penampilan " + chips[i];
			line.append(Component.literal("[" + chips[i] + "]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.SuggestCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))));
			line.append(Component.literal(" "));
		}
		source.sendSuccess(() -> line, false);
		return 1;
	}

	// ================================================================== galleries

	private static int halamanJumlah() {
		return Math.max(1, (SosokGen.rupaNpc().size() + RUPA_PER_HALAMAN - 1) / RUPA_PER_HALAMAN);
	}

	/** {@code galeri peran [halaman]}: 16 looks of 40.4.1 A-D per page, each as a man (front row) and a woman. */
	private static int galeriPeran(CommandContext<CommandSourceStack> ctx, int halaman) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), RUPA_PER_HALAMAN * 2)) {
			return 0;
		}
		List<String> semua = SosokGen.rupaNpc();
		int awal = (halaman - 1) * RUPA_PER_HALAMAN;
		int n = 0;
		RandomSource random = player.getRandom();
		for (int i = 0; i < RUPA_PER_HALAMAN && awal + i < semua.size(); i++) {
			String rupa = semua.get(awal + i);
			SosokGen.Kebijakan k = SosokGen.kebijakan(rupa, null);
			int kolom = i % 8;
			int pasangan = i / 8;
			for (int kelamin = 0; kelamin < 2; kelamin++) {
				int jadi = k.kelaminTetap() ? k.kelaminWajib() : kelamin;
				Vec3 at = GaleriUji.grid(player, kolom, 8, pasangan * 2 + kelamin);
				if (GaleriUji.npc(player, at, rupa, jadi, k.usiaUtama(), random.nextInt(), dao(random), -1,
						GaleriUji.label(rupa, jadi), false) != null) {
					n++;
				}
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.peran", halaman, halamanJumlah(), dibuat,
				semua.size()).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code galeri murid}: ranks 0-5, candidate, rival, phantom and clone, each as a man and a woman, a Dao per column. */
	private static int galeriMurid(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		String[] rupa = {"murid.0", "murid.1", "murid.2", "murid.3", "murid.4", "murid.5", SosokGen.MURID_CALON, SosokGen.MURID_SAINGAN,
				SosokGen.MURID_BAYANGAN, SosokGen.MURID_KLON};
		if (player == null || !ruang(ctx.getSource(), rupa.length * 2)) {
			return 0;
		}
		Realm[] realm = {Realm.MORTAL, Realm.QI_REFINING, Realm.FOUNDATION, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL, Realm.DEITY_TRANSFORMATION,
				Realm.MORTAL, Realm.FOUNDATION, Realm.FOUNDATION, Realm.GOLDEN_CORE};
		RandomSource random = player.getRandom();
		int n = 0;
		for (int kolom = 0; kolom < rupa.length; kolom++) {
			Dao dao = Dao.values()[kolom % Dao.COUNT];
			int pangkat = kolom <= RupaKunci.PANGKAT_MAKS ? kolom : RupaKunci.TIDAK_ADA;
			String kunci = new RupaKunci(rupa[kolom], null, dao.ordinal(), pangkat).format();
			for (int kelamin = 0; kelamin < 2; kelamin++) {
				Vec3 at = GaleriUji.grid(player, kolom, rupa.length, kelamin);
				if (GaleriUji.npc(player, at, kunci, kelamin, Sosok.MUDA, random.nextInt(), dao, realm[kolom].ordinal(),
						GaleriUji.label(rupa[kolom], kelamin), false) != null) {
					n++;
				}
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.murid", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code galeri jalan}: the 15 path looks of 40.4.3 plus a pathless inner disciple, as a man and a woman. */
	private static int galeriJalan(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		int kolomJumlah = SosokJalan.MAKS + 1;
		if (player == null || !ruang(ctx.getSource(), kolomJumlah * 2)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int n = 0;
		for (int kolom = 0; kolom < kolomJumlah; kolom++) {
			int bit = kolom;
			String rupa = bit == SosokJalan.TIDAK_ADA ? "murid.2" : SosokJalan.kunciRupa(bit);
			for (int kelamin = 0; kelamin < 2; kelamin++) {
				Vec3 at = GaleriUji.grid(player, kolom, kolomJumlah, kelamin, 1.3D, 2.5D);
				KultivatorNpc npc = GaleriUji.npc(player, at, rupa, kelamin, Sosok.MUDA, random.nextInt(), dao(random),
						Realm.FOUNDATION.ordinal(), GaleriUji.label(rupa, kelamin), false);
				if (npc != null) {
					SosokApi.setGaya(npc, SosokApi.gaya(npc).withJalan(bit));
					n++;
				}
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.jalan", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code galeri usia <rupa>}: one look in the four age groups, men in front, women behind. */
	private static int galeriUsia(CommandContext<CommandSourceStack> ctx, String rupa) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), 8)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int n = 0;
		for (int usia = Sosok.ANAK; usia <= Sosok.TUA; usia++) {
			for (int kelamin = 0; kelamin < 2; kelamin++) {
				Vec3 at = GaleriUji.grid(player, usia, 4, kelamin, 1.8D, 2.5D);
				MutableComponent papan = Component.literal(rupa + " · ").append(Component.translatable(Sosok.kunciUsia(usia)))
						.append(" · ").append(Component.translatable(Sosok.kunciKelamin(kelamin)));
				if (GaleriUji.npc(player, at, rupa, kelamin, usia, random.nextInt(), dao(random), -1, papan, false) != null) {
					n++;
				}
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.usia", rupa, dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code galeri dao}: inner disciples of every Dao at Golden Core (front) and Nascent Soul (behind). */
	private static int galeriDao(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), Dao.COUNT * 2)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		Realm[] ranah = {Realm.GOLDEN_CORE, Realm.NASCENT_SOUL};
		int n = 0;
		for (int kolom = 0; kolom < Dao.COUNT; kolom++) {
			Dao dao = Dao.values()[kolom];
			for (int baris = 0; baris < 2; baris++) {
				int kelamin = (kolom + baris) & 1;
				String kunci = new RupaKunci("murid.2", null, dao.ordinal(), 2).format();
				Vec3 at = GaleriUji.grid(player, kolom, Dao.COUNT, baris);
				MutableComponent papan = dao.displayName().copy().append(" · ").append(ranah[baris].displayName());
				if (GaleriUji.npc(player, at, kunci, kelamin, Sosok.MUDA, random.nextInt(), dao, ranah[baris].ordinal(), papan, false) != null) {
					n++;
				}
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.dao", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code galeri fisik}: the nine rare physiques on test disciples 1.5 blocks away, facing the player. */
	private static int galeriFisik(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		int jumlah = PowerIds.PHYSIQUE_IDS.length - 1;
		if (player == null || !ruang(ctx.getSource(), jumlah)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int n = 0;
		for (int i = 0; i < jumlah; i++) {
			int physique = i + 1;
			Vec3 at = GaleriUji.grid(player, i, jumlah, 0, 1.2D, 1.5D);
			Sosok sosok = SosokGen.untukMurid(random, (i & 1) == 0 ? Sosok.LAKI : Sosok.PEREMPUAN).withUsia(Sosok.MUDA);
			Component papan = Component.translatable("kultivator.dpower.physique." + PowerIds.PHYSIQUE_IDS[physique]);
			if (GaleriUji.murid(player, at, sosok, physique, 0, papan) != null) {
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.galeri.fisik", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code acak [n]}: n bodies of random looks and brand-new seeds (every run gives other faces). */
	private static int acak(CommandContext<CommandSourceStack> ctx, int jumlah) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), jumlah)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		List<String> semua = SosokGen.rupaNpc();
		int kolomJumlah = Math.min(8, jumlah);
		int n = 0;
		for (int i = 0; i < jumlah; i++) {
			String rupa = semua.get(random.nextInt(semua.size()));
			int look = random.nextInt();
			Sosok s = SosokGen.untukRupa(rupa, null, look);
			Vec3 at = GaleriUji.grid(player, i % kolomJumlah, kolomJumlah, i / kolomJumlah);
			MutableComponent papan = GaleriUji.label(rupa, s.kelamin()).append(String.format(Locale.ROOT, " · #%07X", s.benih()));
			if (GaleriUji.npc(player, at, rupa, -1, -1, look, dao(random), -1, papan, false) != null) {
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.acak", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	// ================================================================== clips

	/** {@code klip <id|nama> [radius]}: test bodies and the player's own disciples in range play the clip. */
	private static int klip(CommandContext<CommandSourceStack> ctx, String nama, int radius) {
		ServerPlayer player = pemain(ctx);
		if (player == null) {
			return 0;
		}
		int klip = GerakId.id(nama);
		if (klip == GerakId.TIDAK_ADA) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.klip.tidak_ada", nama));
			return 0;
		}
		int lama = GerakId.durasiSekali(klip, 0) > 0 ? 0 : 200;
		int n = 0;
		AABB box = player.getBoundingBox().inflate(radius);
		for (LivingEntity body : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
				&& (e.entityTags().contains(GaleriUji.TAG) || e instanceof DiscipleEntity d && d.isOwnedBy(player)))) {
			SosokApi.gerak(body, klip, 0, lama);
			n++;
		}
		int dimainkan = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.klip", klip, Component.translatable(GerakId.kunciLang(klip)),
				dimainkan).withStyle(ChatFormatting.GREEN), false);
		return dimainkan;
	}

	/** {@code klip semua}: 24 bodies in a row, each walking the whole catalog in turn (clip length + 1 s). */
	private static int klipSemua(CommandContext<CommandSourceStack> ctx) {
		int[] katalog = IntStream.rangeClosed(1, GerakId.SOSOK_LAST_DIPAKAI).filter(id -> GerakId.ada(id) && !GerakId.fungsi(id)).toArray();
		return paradeNpc(ctx, katalog, PARADE_BARIS, "semua");
	}

	/** {@code klip kelompok <tempat|sikap|murid|fungsi|aktivitas>}: only that part of the catalog. */
	private static int klipKelompok(CommandContext<CommandSourceStack> ctx, String kelompok) {
		ServerPlayer player = pemain(ctx);
		if (player == null) {
			return 0;
		}
		switch (kelompok.toLowerCase(Locale.ROOT)) {
			case "tempat" -> {
				return paradeNpc(ctx, IntStream.rangeClosed(GerakId.TEMPAT_FIRST, GerakId.TEMPAT_LAST).filter(GerakId::ada).toArray(), 16, kelompok);
			}
			case "sikap" -> {
				return paradeNpc(ctx, IntStream.rangeClosed(GerakId.SIKAP_FIRST, GerakId.SIKAP_LAST).filter(GerakId::ada).toArray(), 14, kelompok);
			}
			case "murid" -> {
				return paradeNpc(ctx, IntStream.rangeClosed(GerakId.MURID_FIRST, GerakId.SOSOK_LAST_DIPAKAI)
						.filter(id -> GerakId.ada(id) && !GerakId.fungsi(id)).toArray(), 16, kelompok);
			}
			case "fungsi" -> {
				return aktivitasBaris(ctx, IntStream.rangeClosed(GerakId.FUNGSI_FIRST, GerakId.FUNGSI_LAST)
						.map(GerakId::aktivitasFungsi).toArray(), kelompok);
			}
			case "aktivitas" -> {
				return aktivitasBaris(ctx, IntStream.rangeClosed(SekteIds.ACT_MANDIRI_FIRST, 56).toArray(), kelompok);
			}
			default -> {
				ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.klip.kelompok_tidak_ada", kelompok));
				return 0;
			}
		}
	}

	private static int paradeNpc(CommandContext<CommandSourceStack> ctx, int[] katalog, int baris, String nama) {
		ServerPlayer player = pemain(ctx);
		int jumlah = Math.min(baris, katalog.length);
		if (player == null || !ruang(ctx.getSource(), jumlah)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		List<String> semua = SosokGen.rupaNpc();
		int kolomJumlah = Math.min(12, jumlah);
		int n = 0;
		for (int i = 0; i < jumlah; i++) {
			String rupa = (i & 1) == 0 ? "warga" : semua.get(random.nextInt(semua.size()));
			Vec3 at = GaleriUji.grid(player, i % kolomJumlah, kolomJumlah, i / kolomJumlah, 1.8D, 2.5D);
			KultivatorNpc npc = GaleriUji.npc(player, at, rupa, i & 1, Sosok.DEWASA, random.nextInt(), dao(random), -1,
					Component.literal(rupa), false);
			if (npc != null) {
				GaleriUji.parade(npc, katalog, i, jumlah);
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.klip.parade", nama, katalog.length, dibuat)
				.withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** Test disciples in a row, one per disciple activity (function poses 13-27, DESIGN 39 activities 40-56). */
	private static int aktivitasBaris(CommandContext<CommandSourceStack> ctx, int[] aktivitas, String nama) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), aktivitas.length)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int kolomJumlah = Math.min(9, aktivitas.length);
		int n = 0;
		for (int i = 0; i < aktivitas.length; i++) {
			int act = aktivitas[i];
			int klip = GerakId.dariAktivitas(act);
			String label = klip != GerakId.TIDAK_ADA ? klip + " " + GerakId.namaAtauId(klip) : "ACT " + act;
			Vec3 at = GaleriUji.grid(player, i % kolomJumlah, kolomJumlah, i / kolomJumlah, 1.8D, 2.5D);
			Sosok sosok = SosokGen.untukMurid(random, i & 1).withUsia(Sosok.MUDA);
			if (GaleriUji.murid(player, at, sosok, 0, act, Component.literal(label)) != null) {
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.klip.aktivitas", nama, dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	// ================================================================== gait, face, springs

	/** {@code jalan}: one body per gait profile walking between two marks 12 blocks apart. */
	private static int jalan(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), PROFIL.length)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int n = 0;
		for (int i = 0; i < PROFIL.length; i++) {
			String[] p = PROFIL[i];
			Vec3 a = GaleriUji.grid(player, i, PROFIL.length, 0, 1.5D, 2.0D);
			Vec3 b = GaleriUji.grid(player, i, PROFIL.length, 0, 1.5D, 14.0D);
			int kelamin = Integer.parseInt(p[2]);
			SosokGen.Kebijakan k = SosokGen.kebijakan(p[1], null);
			KultivatorNpc npc = GaleriUji.npc(player, a, p[1], kelamin, k.usiaUtama(), random.nextInt(), dao(random), -1,
					Component.literal(p[0] + " · " + p[1]), true);
			if (npc != null) {
				GaleriUji.jalan(npc, a, b, "tergesa".equals(p[0]) ? 1.25D : "arwah".equals(p[0]) ? 0.55D : 0.8D);
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.jalan", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	/** {@code wajah}: one body 1.5 blocks ahead showing every mood, the drained face, closed eyes and a greeting. */
	private static int wajah(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), 1)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		Vec3 at = GaleriUji.grid(player, 0, 1, 0, 1.0D, 1.5D);
		int kelamin = random.nextInt(2);
		KultivatorNpc npc = GaleriUji.npc(player, at, "warga", kelamin, Sosok.MUDA, random.nextInt(), null, -1,
				GaleriUji.label("warga", kelamin), false);
		if (npc == null) {
			return 0;
		}
		GaleriUji.wajah(npc);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.wajah").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** {@code pegas}: four bodies with long hair, ponytail, wide sleeves and a shawl run round the player and jump. */
	private static int pegas(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), PEGAS.length)) {
			return 0;
		}
		RandomSource random = player.getRandom();
		int n = 0;
		for (int i = 0; i < PEGAS.length; i++) {
			double sudut = i * Math.PI / 2.0D;
			Vec3 at = player.position().add(Math.cos(sudut) * 5.0D, 0.0D, Math.sin(sudut) * 5.0D);
			int kelamin = Integer.parseInt(PEGAS[i][1]);
			KultivatorNpc npc = GaleriUji.npc(player, at, PEGAS[i][0], kelamin, Sosok.MUDA, random.nextInt(), dao(random), -1,
					GaleriUji.label(PEGAS[i][0], kelamin), true);
			if (npc != null) {
				GaleriUji.pegas(npc, sudut);
				n++;
			}
		}
		int dibuat = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.pegas", dibuat).withStyle(ChatFormatting.GREEN), false);
		return dibuat;
	}

	// ================================================================== the player's own disciple

	/** {@code murid <...>}: changes the player's own disciple under the crosshair (within 8 blocks), permanently. */
	private static int murid(CommandContext<CommandSourceStack> ctx, String apa, String nilai) {
		ServerPlayer player = pemain(ctx);
		if (player == null) {
			return 0;
		}
		Entity target = ditatap(player);
		if (!(target instanceof DiscipleEntity murid) || !murid.isOwnedBy(player) || murid.role() != DiscipleEntity.Role.DISCIPLE) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.murid.tidak_ada"));
			return 0;
		}
		Sosok sosok = SosokApi.sosok(murid);
		String v = nilai.toLowerCase(Locale.ROOT);
		switch (apa) {
			case "acak" -> {
				Sosok baru = SosokGen.untukMurid(player.getRandom(), sosok.kelamin()).withUsia(sosok.usia());
				SosokApi.setSosok(murid, baru);
			}
			case "kelamin" -> {
				if (!v.equals("l") && !v.equals("p")) {
					return gagal(ctx, nilai);
				}
				SosokApi.setSosok(murid, sosok.withKelamin(v.equals("p") ? Sosok.PEREMPUAN : Sosok.LAKI));
			}
			case "usia" -> {
				int usia = indeks(USIA_ID, v);
				if (usia < 0) {
					return gagal(ctx, nilai);
				}
				SosokApi.setUsia(murid, usia);
			}
			case "jalan" -> {
				if (!v.equals("tidak_ada") && SosokJalan.bit(v) == SosokJalan.TIDAK_ADA) {
					return gagal(ctx, nilai);
				}
				SosokApi.setJalan(murid, v.equals("tidak_ada") ? null : v);
			}
			case "luka" -> SosokApi.setLuka(murid, Integer.parseInt(v));
			case "suasana" -> {
				int suasana = indeks(SUASANA_ID, v);
				if (suasana < 0) {
					try {
						suasana = Integer.parseInt(v);
					} catch (NumberFormatException exception) {
						return gagal(ctx, nilai);
					}
				}
				SosokApi.setSuasana(murid, suasana, 0);
			}
			case "busana" -> SosokApi.setBusanaKhusus(murid, Integer.parseInt(v));
			case "hari" -> {
				SosokUsia.majukan(murid, Integer.parseInt(v));
				SosokUsia.harian(murid, player.level());
			}
			default -> {
				return gagal(ctx, apa);
			}
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.murid", murid.getDisplayName(), ringkas(murid))
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	// ================================================================== exact seed, info, remove

	/** {@code pratinjau_benih <benih> <rupa>}: one body with exactly this seed (compare with the Python preview). */
	private static int pratinjauBenih(CommandContext<CommandSourceStack> ctx, int benih, String rupa) {
		ServerPlayer player = pemain(ctx);
		if (player == null || !ruang(ctx.getSource(), 1)) {
			return 0;
		}
		Vec3 at = GaleriUji.grid(player, 0, 1, 0, 1.0D, 2.0D);
		int look = player.getRandom().nextInt();
		MutableComponent papan = Component.literal(String.format(Locale.ROOT, "%s · #%07X", rupa, benih));
		KultivatorNpc npc = GaleriUji.npc(player, at, rupa, -1, -1, look, null, -1, papan, false);
		if (npc == null) {
			return 0;
		}
		Sosok sosok = SosokApi.sosok(npc).withBenih(benih);
		SosokApi.setSosok(npc, sosok);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.benih", String.format(Locale.ROOT, "#%07X", benih), rupa,
				ringkas(npc)).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** {@code info}: the Sosok data of the body under the crosshair. */
	private static int info(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = pemain(ctx);
		if (player == null) {
			return 0;
		}
		Entity target = ditatap(player);
		if (!(target instanceof LivingEntity body) || SosokApi.pemegang(body) == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.info.tidak_ada"));
			return 0;
		}
		Sosok s = SosokApi.sosok(body);
		Gaya g = SosokApi.gaya(body);
		int klip = SosokApi.gerakSekarang(body);
		RupaKunci rupa = body instanceof KultivatorNpc npc ? npc.rupaKunci() : SosokApi.rupa(body);
		var scale = body.getAttribute(Attributes.SCALE);
		double skala = scale != null ? scale.getValue() : 1.0D;
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.info.judul", body.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.info.sosok", Component.translatable(Sosok.kunciKelamin(s.kelamin())),
				Component.translatable(Sosok.kunciUsia(s.usia())), s.postur(), String.format(Locale.ROOT, "#%07X", s.benih()),
				String.format(Locale.ROOT, "%.3f", skala)), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.info.gaya",
				g.sifatPertama() == null ? Component.literal("-") : Component.translatable(g.sifatPertama().key()),
				g.sifatKedua() == null ? Component.literal("-") : Component.translatable(g.sifatKedua().key()),
				Component.translatable(SosokJalan.kunciLang(g.jalan())), Component.translatable(Gaya.kunciLuka(g.luka())),
				Component.translatable(Gaya.kunciBusana(g.busanaKhusus())), Component.translatable(Gaya.kunciSuasana(g.suasana()))), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.info.gerak", rupa.kosong() ? "-" : rupa.format(),
				klip == GerakId.TIDAK_ADA ? Component.literal("-") : Component.literal(klip + " ").append(Component.translatable(GerakId.kunciLang(klip))),
				SosokApi.tatapSekarang(body)), false);
		return 1;
	}

	private static int hapus(CommandSourceStack source) {
		int n = GaleriUji.hapus(source.getServer());
		source.sendSuccess(() -> Component.translatable("kultivator.sosok.cmd.hapus", n).withStyle(ChatFormatting.GREEN), false);
		return n;
	}

	// ================================================================== helpers

	private static @Nullable ServerPlayer pemain(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.hanya_pemain"));
		}
		return player;
	}

	/** True when {@code n} more test bodies fit under the cap (a message when not). */
	private static boolean ruang(CommandSourceStack source, int n) {
		if (GaleriUji.muat(n)) {
			return true;
		}
		source.sendFailure(Component.translatable("kultivator.sosok.cmd.penuh", GaleriUji.jumlah(), GaleriUji.MAKS));
		return false;
	}

	private static int gagal(CommandContext<CommandSourceStack> ctx, String nilai) {
		ctx.getSource().sendFailure(Component.translatable("kultivator.sosok.cmd.nilai_salah", nilai));
		return 0;
	}

	private static int indeks(String[] ids, String v) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(v)) {
				return i;
			}
		}
		return -1;
	}

	private static Dao dao(RandomSource random) {
		return Dao.values()[random.nextInt(Dao.COUNT)];
	}

	/** "Perempuan · Muda · postur 3 · Pedang · Senang". */
	private static Component ringkas(LivingEntity body) {
		Sosok s = SosokApi.sosok(body);
		Gaya g = SosokApi.gaya(body);
		return Component.translatable("kultivator.sosok.cmd.ringkas", Component.translatable(Sosok.kunciKelamin(s.kelamin())),
				Component.translatable(Sosok.kunciUsia(s.usia())), s.postur(), Component.translatable(SosokJalan.kunciLang(g.jalan())),
				Component.translatable(Gaya.kunciLuka(g.luka())), Component.translatable(Gaya.kunciBusana(g.busanaKhusus())),
				Component.translatable(Gaya.kunciSuasana(g.suasana())));
	}

	/** The living body under the crosshair within {@link #JANGKAU} blocks, or null. */
	private static @Nullable Entity ditatap(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(JANGKAU));
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(JANGKAU)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> entity instanceof LivingEntity living && living.isAlive() && SosokApi.pemegang(living) != null, JANGKAU * JANGKAU);
		return hit != null ? hit.getEntity() : null;
	}

	private SosokCommands() {
	}
}

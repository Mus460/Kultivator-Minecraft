package dev.kultivator.sekte.gabung.dunia;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungUji;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Module 41W "Dunia &amp; Tokoh" (DESIGN 41.6.12-41.6.13, 41.11.1-41.11.2): the named elders and master of every NPC sect
 * ({@link TokohSekte}, SavedData {@code kultivator:gabung_tokoh}), their bodies at the compound titik
 * ({@link TokohAnchors}), the titik ladder compound -&gt; gate -&gt; town ({@link TitikGabung}), the story chains of a member
 * ({@link CeritaGabung}: conditions, effects and goals of the 38.S story engine) and the world news ({@link KabarGabung}).
 * Entry of the package {@code sekte.gabung.dunia}, called by {@code Gabung.init()} while 41 is on (no registry content).
 * <p>
 * One server tick handler, no per-member tick: every second the titik a member stands at (their own sect's, the
 * infiltrated sect's) become story events, elders' bodies within reach become {@code temu:<rel>}, and an expelled player
 * back at the gate may start Tebus Dosa; every two seconds the elders' bodies are kept ({@link TokohAnchors#rawat}); every
 * ten seconds the loaded gate anchors are read ({@link TitikGabung#pindaiGerbang}). Test subs: {@code uji gabung tokoh
 * <sekte>}, {@code titik <sekte>}, {@code tp <sekte> [titik]}, {@code dua_topeng}, {@code cerita <rantai> [tahap]}.
 * <p>
 * Owners: 41S0 wrote the skeleton (wave 41-G0); 41W fills it; 41W-G regenerates the compound and gate structures with the
 * new lots and titik ({@code tools/gen_tempat_kompleks.py}, {@code tools/gen_sekte_gerbang.py}) and adds the titik to
 * {@code KompleksAnchors.TITIK}. 41 adds no structure type, set or placement.
 */
public final class DuniaGabung {

	/** Compound titik that become {@code gabung_titik <name>} events when a member stands within {@link #TITIK_JARAK}. */
	public static final List<String> AWAS = List.of(TitikGabung.GERBANG_KOMPLEKS, TitikGabung.AULA, TitikGabung.ARENA, TitikGabung.TETUA,
			TitikGabung.DALAM, TitikGabung.PUNCAK, TitikGabung.PASAR, TitikGabung.PAPAN_MISI, TitikGabung.JASA, TitikGabung.HUKUM,
			TitikGabung.ALTAR_SUMPAH, TitikGabung.KANDANG, TitikGabung.UJIAN_BAWAH, TitikGabung.UJIAN_ATAS);
	public static final double TITIK_JARAK = 4.0D;
	/** A member farther than this from their sect's gate column is not checked at all (the compound lies behind it). */
	public static final double KOMPLEKS_JARAK = 512.0D;
	/** The same story event repeats for a player at most this often (ticks). */
	public static final long ULANG = 200L;

	private static final Map<String, Long> TERAKHIR = new HashMap<>();
	private static boolean initialised;
	private static long tick;

	/** Runtime wiring of module 41W. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		CeritaGabung.init();
		KabarGabung.init();
		ServerTickEvents.END_SERVER_TICK.register(DuniaGabung::tik);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> TokohAnchors.lupakanJadwal());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TokohAnchors.bersih();
			TERAKHIR.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			TokohAnchors.lupakan(player.getUUID());
			String prefix = player.getUUID() + "|";
			TERAKHIR.keySet().removeIf(k -> k.startsWith(prefix));
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof KultivatorNpc npc && GabungApi.aktif()) {
				String rel = CeritaGabung.relasiTubuh(sp, npc);
				if (rel != null) {
					CeritaGabung.peristiwa(sp, "temu:" + rel);
				}
			}
			return InteractionResult.PASS;
		});
		// a player master sets the NPC master aside as "Leluhur Hidup" (41.8.5); an NPC successor is 41D's gantiKetua call
		GabungBus.KETUA_BARU.register((server, sekte, pemain, tokoh) -> {
			if (pemain != null) {
				TokohSekte.pensiunkanKetua(server, sekte);
			}
		});
		daftarUji();
	}

	private static void tik(MinecraftServer server) {
		if (!GabungApi.aktif()) {
			return;
		}
		tick++;
		if (tick % 20L == 0L) {
			pindaiTitik(server);
		}
		if (tick % 40L == 10L) {
			TokohAnchors.rawat(server);
		}
		if (tick % 200L == 30L) {
			TitikGabung.pindaiGerbang(server);
		}
	}

	// ================================================================== titik and characters as story events

	private static void pindaiTitik(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GabungData data = GabungData.get(server);
		DiplomasiService d = SekteApi.diplomasi();
		long now = level.getGameTime();
		Map<String, KultivatorNpc> bodies = TokohAnchors.hidup();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Keanggotaan k = data.peek(p.getUUID());
			if (k == null) {
				continue;
			}
			if (k.status.anggota() && dekatGerbang(d, k.sekte, p)) {
				for (String nama : AWAS) {
					BlockPos pos = TitikGabung.kompleks(k.sekte, nama);
					if (pos != null && di(p, pos, TITIK_JARAK)) {
						api(p, "gabung_titik", nama, now);
					}
				}
				for (Map.Entry<String, KultivatorNpc> e : bodies.entrySet()) {
					KultivatorNpc npc = e.getValue();
					if (k.sekte.equals(TokohSekte.sekteDari(e.getKey())) && npc.distanceToSqr(p) <= TITIK_JARAK * TITIK_JARAK) {
						String rel = CeritaGabung.relasiTubuh(p, npc);
						if (rel != null) {
							api(p, "gabung", "temu:" + rel, now);
						}
					}
				}
			}
			if (k.status == StatusAnggota.MENYAMAR && !k.samaranSekte.isEmpty()) {
				BlockPos gate = KompleksApi.spot(k.samaranSekte, TitikGabung.GERBANG_KOMPLEKS);
				BlockPos gateCol = d.gate(k.samaranSekte);
				if (gate != null && di(p, gate, 6.0D) || gateCol != null && di(p, gateCol, 12.0D)) {
					api(p, "gabung_titik", "samaran:gerbang", now);
				}
				BlockPos hall = KompleksApi.spot(k.samaranSekte, TitikGabung.AULA);
				if (hall != null && di(p, hall, 6.0D)) {
					api(p, "gabung_titik", "samaran:aula", now);
				}
			}
			if (!k.status.anggota()) {
				for (Map.Entry<String, Keanggotaan.Riwayat> r : k.riwayatSekte.entrySet()) {
					if (r.getValue().status() != StatusKeluar.DIUSIR) {
						continue;
					}
					BlockPos gate = d.gate(r.getKey());
					if (gate != null && di(p, gate, 24.0D)) {
						CeritaGabung.cobaTebus(p, k, r.getKey());
					}
				}
			}
		}
	}

	private static boolean dekatGerbang(DiplomasiService d, String sekte, ServerPlayer p) {
		BlockPos col = d.gate(sekte) != null ? d.gate(sekte) : d.plannedGate(sekte);
		if (col == null) {
			return false;
		}
		double dx = p.getX() - col.getX();
		double dz = p.getZ() - col.getZ();
		return dx * dx + dz * dz <= KOMPLEKS_JARAK * KOMPLEKS_JARAK;
	}

	private static boolean di(ServerPlayer p, BlockPos pos, double jarak) {
		double dx = p.getX() - (pos.getX() + 0.5D);
		double dz = p.getZ() - (pos.getZ() + 0.5D);
		return dx * dx + dz * dz <= jarak * jarak && Math.abs(p.getY() - pos.getY()) <= 4.0D;
	}

	/** Fires a story event, at most once per {@link #ULANG} per player and event. */
	private static void api(ServerPlayer p, String jenis, String id, long now) {
		String key = p.getUUID() + "|" + jenis + "|" + id;
		Long last = TERAKHIR.get(key);
		if (last != null && now - last < ULANG) {
			return;
		}
		TERAKHIR.put(key, now);
		QuestTracker.peristiwa(p, jenis, id);
	}

	// ================================================================== test subs (41.13.3)

	private static void daftarUji() {
		SuggestionProvider<CommandSourceStack> titik = (ctx, builder) -> {
			List<String> all = new ArrayList<>(AWAS);
			all.addAll(TitikGabung.TITIK_BARU);
			all.addAll(TitikGabung.TITIK_GERBANG);
			return SharedSuggestionProvider.suggest(all, builder);
		};
		SuggestionProvider<CommandSourceStack> rantai = (ctx, builder) -> SharedSuggestionProvider.suggest(CeritaGabung.RANTAI, builder);
		GabungApi.daftarUji("tokoh", () -> Commands.literal("tokoh")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> tokoh(ctx, StringArgumentType.getString(ctx, "sekte")))));
		GabungApi.daftarUji("titik", () -> Commands.literal("titik")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> titik(ctx, StringArgumentType.getString(ctx, "sekte")))));
		GabungApi.daftarUji("tp", () -> Commands.literal("tp")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> tp(ctx, StringArgumentType.getString(ctx, "sekte"), TitikGabung.GERBANG_KOMPLEKS))
						.then(Commands.argument("titik", StringArgumentType.string()).suggests(titik)
								.executes(ctx -> tp(ctx, StringArgumentType.getString(ctx, "sekte"), alias(StringArgumentType.getString(ctx, "titik"), 0)))
								.then(Commands.argument("n", IntegerArgumentType.integer(0, 5))
										.executes(ctx -> tp(ctx, StringArgumentType.getString(ctx, "sekte"),
												alias(StringArgumentType.getString(ctx, "titik"), IntegerArgumentType.getInteger(ctx, "n"))))))));
		GabungApi.daftarUji("dua_topeng", () -> Commands.literal("dua_topeng").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			boolean ok = CeritaGabung.mulai(p, CeritaGabung.DUA_TOPENG) || QuestTracker.lompat(p, CeritaGabung.DUA_TOPENG, "panggilan");
			ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.gabung.dunia.uji.mulai" : "kultivator.gabung.dunia.uji.gagal",
					CeritaGabung.DUA_TOPENG), false);
			return ok ? 1 : 0;
		}));
		GabungApi.daftarUji("cerita", () -> Commands.literal("cerita")
				.then(Commands.argument("rantai", StringArgumentType.word()).suggests(rantai)
						.executes(ctx -> cerita(ctx, StringArgumentType.getString(ctx, "rantai"), null))
						.then(Commands.argument("tahap", StringArgumentType.word())
								.executes(ctx -> cerita(ctx, StringArgumentType.getString(ctx, "rantai"), StringArgumentType.getString(ctx, "tahap"))))));
		GabungUji.chips("gabung cerita <rantai> [tahap]");
	}

	/** {@code ujian} -&gt; the first step, {@code kediaman <n>} -&gt; cave n, {@code renungan} -&gt; Gua Renungan (41.13.3). */
	private static String alias(String nama, int n) {
		return switch (nama) {
			case "ujian" -> TitikGabung.UJIAN_BAWAH;
			case "kediaman" -> TitikGabung.KEDIAMAN + n;
			case "renungan" -> TitikGabung.GUA_RENUNGAN;
			default -> nama;
		};
	}

	private static int tokoh(CommandContext<CommandSourceStack> ctx, String sekte) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		if (SekteApi.diplomasi().tier(sekte) <= 0) {
			source.sendFailure(Component.translatable("kultivator.gabung.dunia.uji.sekte_tidak", sekte));
			return 0;
		}
		UUID tester = source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
		int aliran = Math.max(0, SekteApi.diplomasi().aliran(sekte));
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.dunia.uji.tokoh.judul", namaSekte(sekte),
				GabungApi.kelasSekte(sekte).nama(), GabungApi.corak(sekte).nama()).withStyle(ChatFormatting.GOLD), false);
		long hari = Kota.day(server);
		List<TokohSekte.Tokoh> all = new ArrayList<>();
		TokohSekte.Tokoh ketua = TokohSekte.ketua(server, sekte);
		if (ketua != null) {
			all.add(ketua);
		}
		all.addAll(TokohSekte.semuaTetua(server, sekte));
		for (TokohSekte.Tokoh t : all) {
			MutableComponent sifat = Component.empty();
			for (int i = 0; i < t.sifat().size(); i++) {
				sifat.append(i == 0 ? Component.empty() : Component.literal(", "));
				sifat.append(Component.translatable(t.sifat().get(i).key()));
			}
			int sikap = tester == null ? 0 : TokohSekte.sikap(server, t.id(), tester);
			Component status = Component.translatable("kultivator.gabung.dunia.status." + (t.terluka(hari) ? "luka" : t.status().id()));
			Component line = Component.translatable("kultivator.gabung.dunia.uji.tokoh.baris", t.id(), t.namaGelar(),
					Component.translatable("kultivator.realm." + t.ranah().id()), t.dao().displayName(), sifat, t.usia(), sikap, status,
					TokohAnchors.hidup().containsKey(t.id()) ? Component.translatable("kultivator.gabung.dunia.uji.bertubuh") : Component.empty());
			Component motto = Component.translatable(t.kunciSemboyan(aliran));
			source.sendSuccess(() -> line, false);
			source.sendSuccess(() -> Component.literal("   ").append(motto).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
		}
		for (Roster.Tokoh r : Roster.daftar(server, sekte)) {
			Component line = Component.translatable("kultivator.gabung.dunia.uji.tokoh.roster", r.id(), r.nama(), r.peran(),
					tester == null ? 0 : Roster.sikap(server, r.id(), tester));
			source.sendSuccess(() -> line.copy().withStyle(ChatFormatting.GRAY), false);
		}
		if (tester != null) {
			Keanggotaan k = GabungData.get(server).peek(tester);
			if (k != null && k.anggotaDi(sekte)) {
				source.sendSuccess(() -> Component.translatable("kultivator.gabung.dunia.uji.tokoh.milikmu", CeritaGabung.namaId(server,
						k.shifu.isEmpty() ? null : k.shifu, "shifu"), CeritaGabung.namaId(server, k.kakak.isEmpty() ? null : k.kakak, "kakak"),
						CeritaGabung.namaId(server, k.saingan.isEmpty() ? null : k.saingan, "saingan")).withStyle(ChatFormatting.AQUA), false);
			}
		}
		return 1;
	}

	private static int titik(CommandContext<CommandSourceStack> ctx, String sekte) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		TitikGabung.pindaiGerbang(server);
		Map<String, BlockPos> all = TitikGabung.semua(sekte);
		int known = 0;
		for (BlockPos pos : all.values()) {
			if (pos != null) {
				known++;
			}
		}
		int k = known;
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.dunia.uji.titik.judul", namaSekte(sekte), k, all.size())
				.withStyle(ChatFormatting.GOLD), false);
		MutableComponent line = Component.literal(" ");
		int n = 0;
		for (Map.Entry<String, BlockPos> e : all.entrySet()) {
			BlockPos pos = e.getValue();
			line.append(Component.literal(e.getKey()).withStyle(pos != null ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
			if (pos != null) {
				line.append(Component.literal(" " + pos.getX() + "," + pos.getY() + "," + pos.getZ()).withStyle(ChatFormatting.GRAY));
			}
			line.append(Component.literal("  "));
			if (++n % 4 == 0) {
				MutableComponent done = line;
				source.sendSuccess(() -> done, false);
				line = Component.literal(" ");
			}
		}
		if (n % 4 != 0) {
			MutableComponent done = line;
			source.sendSuccess(() -> done, false);
		}
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> ctx, String sekte, String nama) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		BlockPos pos = TitikGabung.titik(sekte, nama);
		if (pos == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.dunia.uji.tp.tidak", sekte, nama));
			return 0;
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		int y = pos.getY() <= level.getMinY() + 1 ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				pos.getX(), pos.getZ()) : pos.getY();
		p.teleportTo(level, pos.getX() + 0.5D, y + 0.1D, pos.getZ() + 0.5D, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
		p.resetFallDistance();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.dunia.uji.tp.ok", nama, pos.getX(), y, pos.getZ()), false);
		return 1;
	}

	private static int cerita(CommandContext<CommandSourceStack> ctx, String rantai, String tahap) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		String q = rantai.startsWith(CeritaGabung.AWALAN) ? rantai : CeritaGabung.AWALAN + rantai;
		boolean ok;
		if (CeritaGabung.TEBUS_DOSA.equals(q)) {
			Keanggotaan k = GabungApi.anggota(p);
			String s = k.sekte.isEmpty() ? SekteApi.diplomasi().nearest(p.blockPosition(), -1) : k.sekte;
			if (s != null) {
				k.bagian(CeritaGabung.BAGIAN).putString("tebus", s);
				GabungData.get(p.level().getServer()).setDirty();
			}
		}
		if (tahap == null) {
			QuestTracker.lupakan(p, q);
			ok = CeritaGabung.mulai(p, q);
		} else {
			ok = QuestTracker.lompat(p, q, tahap);
		}
		boolean done = ok;
		ctx.getSource().sendSuccess(() -> Component.translatable(done ? "kultivator.gabung.dunia.uji.mulai" : "kultivator.gabung.dunia.uji.gagal", q), false);
		return ok ? 1 : 0;
	}

	private static Component namaSekte(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name != null ? name : Component.literal(sekte);
	}

	private DuniaGabung() {
	}
}
